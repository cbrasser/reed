package app.reed.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import timber.log.Timber
import java.io.File
import java.util.UUID

sealed interface ImportResult {
    data class Added(val title: String) : ImportResult
    data class Failed(val fileName: String, val reason: ImportFailure) : ImportResult
}

enum class ImportFailure { KINDLE_FORMAT, UNSUPPORTED, PROTECTED, DAMAGED, UNREADABLE }

/** Everything Reed knows about books and notes, plus Readium plumbing to open them. */
class Library(
    private val context: Context,
    private val db: ReedDatabase,
    /** Called after notes or a book's privacy change, so they can be sent on. */
    private val onNotesChanged: () -> Unit = {},
) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = PdfiumDocumentFactory(context),
        ),
    )

    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

    val books: Flow<List<BookWithCount>> = db.books().observeAll()

    fun observeBook(id: Long): Flow<Book?> = db.books().observe(id)

    suspend fun book(id: Long): Book? = db.books().get(id)

    fun notes(bookId: Long): Flow<List<Note>> = db.notes().observeForBook(bookId)

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val fileName = displayName(uri) ?: "Untitled"
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension in KINDLE_EXTENSIONS) {
            return@withContext ImportResult.Failed(fileName, ImportFailure.KINDLE_FORMAT)
        }

        val id = UUID.randomUUID().toString()
        val staging = File(booksDir, "$id.tmp")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                staging.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            staging.delete()
            return@withContext ImportResult.Failed(fileName, ImportFailure.UNREADABLE)
        }

        val asset = assetRetriever.retrieve(staging).getOrElse {
            Timber.w("Import: couldn't retrieve %s: %s", fileName, it.message)
            val reason = if (staging.looksLikeBook()) ImportFailure.DAMAGED else ImportFailure.UNSUPPORTED
            staging.delete()
            return@withContext ImportResult.Failed(fileName, reason)
        }
        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            Timber.w("Import: couldn't open %s: %s", fileName, it.message)
            val reason = when {
                staging.looksLikeBook() -> ImportFailure.DAMAGED
                it is PublicationOpener.OpenError.FormatNotSupported -> ImportFailure.UNSUPPORTED
                else -> ImportFailure.UNREADABLE
            }
            staging.delete()
            return@withContext ImportResult.Failed(fileName, reason)
        }

        try {
            val format = when {
                publication.conformsTo(Publication.Profile.EPUB) -> BookFormat.EPUB
                publication.conformsTo(Publication.Profile.PDF) -> BookFormat.PDF
                else -> null
            }
            if (format == null) {
                staging.delete()
                return@withContext ImportResult.Failed(fileName, ImportFailure.UNSUPPORTED)
            }
            if (publication.isRestricted) {
                staging.delete()
                return@withContext ImportResult.Failed(fileName, ImportFailure.PROTECTED)
            }

            val target = File(booksDir, "$id.${format.name.lowercase()}")
            staging.renameTo(target)

            val coverPath = publication.cover()?.let { saveCover(id, it) }
            val title = publication.metadata.title?.takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.')
            val author = publication.metadata.authors
                .map { it.name }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { null }

            db.books().insert(
                Book(
                    title = title,
                    author = author,
                    format = format,
                    filePath = target.absolutePath,
                    coverPath = coverPath,
                    language = publication.metadata.languages.firstOrNull(),
                    addedAt = System.currentTimeMillis(),
                ),
            )
            ImportResult.Added(title)
        } finally {
            publication.close()
        }
    }

    /** Opens a publication for reading. The caller owns it and must close it. */
    suspend fun open(book: Book): Publication? = withContext(Dispatchers.IO) {
        val asset = assetRetriever.retrieve(File(book.filePath)).getOrElse {
            Timber.w("Open: couldn't retrieve %s: %s", book.title, it.message)
            return@withContext null
        }
        publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            Timber.w("Open: couldn't open %s: %s", book.title, it.message)
            asset.close()
            null
        }
    }

    suspend fun remove(bookId: Long) = withContext(Dispatchers.IO) {
        val book = db.books().get(bookId) ?: return@withContext
        db.books().delete(book)
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
        onNotesChanged()
    }

    suspend fun setPrivate(bookId: Long, isPrivate: Boolean) {
        db.books().setPrivate(bookId, isPrivate)
        onNotesChanged()
    }

    /** Every book with its notes, for sending them to the user's server. */
    suspend fun everything(): List<Pair<Book, List<Note>>> = withContext(Dispatchers.IO) {
        db.books().all().map { it to db.notes().forBook(it.id) }
    }

    suspend fun markOpened(bookId: Long) = db.books().markOpened(bookId, System.currentTimeMillis())

    suspend fun savePosition(bookId: Long, locator: Locator) {
        db.books().updatePosition(
            id = bookId,
            locator = locator.toJSON().toString(),
            progression = locator.locations.totalProgression ?: 0.0,
        )
    }

    suspend fun note(id: Long): Note? = db.notes().get(id)

    suspend fun addNote(note: Note): Long = db.notes().insert(note).also { onNotesChanged() }

    suspend fun updateNote(note: Note) = db.notes().update(note).also { onNotesChanged() }

    suspend fun deleteNote(note: Note) = db.notes().delete(note).also { onNotesChanged() }

    private fun saveCover(id: String, bitmap: Bitmap): String? = runCatching {
        val file = File(coversDir, "$id.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment

    private companion object {
        val KINDLE_EXTENSIONS = setOf("mobi", "azw", "azw3", "azw4", "kfx", "prc")
    }
}

/**
 * True when the file starts like an EPUB (ZIP) or PDF. Used to tell a damaged or half-downloaded
 * book apart from a file that was never a book.
 */
private fun File.looksLikeBook(): Boolean = runCatching {
    val head = ByteArray(4)
    val read = inputStream().use { it.read(head) }
    read == 4 && (
        (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) ||
            String(head, Charsets.US_ASCII) == "%PDF"
        )
}.getOrDefault(false)

fun Locator.serialize(): String = toJSON().toString()

fun String.toLocator(): Locator? = runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()
