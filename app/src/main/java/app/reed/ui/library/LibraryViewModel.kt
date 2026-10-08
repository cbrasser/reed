package app.reed.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.reed.data.BookWithCount
import app.reed.data.ImportFailure
import app.reed.data.ImportResult
import app.reed.data.LibrarySort
import app.reed.reed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val loaded: Boolean = false,
    val books: List<BookWithCount> = emptyList(),
    val readingNow: List<BookWithCount> = emptyList(),
    val privateBooks: List<BookWithCount> = emptyList(),
    val totalVisible: Int = 0,
    val unlocked: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENT,
    val query: String = "",
    val importing: Int = 0,
)

data class LibraryMessage(
    val text: String,
    val action: String? = null,
    val onAction: (() -> Unit)? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val library = app.reed.library
    private val settings = app.reed.settings
    private val lock = app.reed.privacyLock

    private val query = MutableStateFlow("")
    private val importing = MutableStateFlow(0)
    private val messages = Channel<LibraryMessage>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val state: StateFlow<LibraryState> = combine(
        library.books,
        settings.sort,
        query,
        importing,
        lock.unlocked,
    ) { all, sort, q, importingCount, unlocked ->
        val matches = { b: BookWithCount ->
            q.isBlank() || b.title.contains(q, ignoreCase = true) || (b.author?.contains(q, ignoreCase = true) == true)
        }
        val visible = all.filter { !it.isPrivate }
        LibraryState(
            loaded = true,
            books = visible.filter(matches).sortedWith(sort.comparator()),
            readingNow = if (q.isBlank()) {
                visible
                    .filter { it.lastOpenedAt != null && it.progression < 0.99 }
                    .sortedByDescending { it.lastOpenedAt }
                    .take(8)
            } else {
                emptyList()
            },
            privateBooks = if (unlocked) all.filter { it.isPrivate && matches(it) }.sortedWith(sort.comparator()) else emptyList(),
            totalVisible = visible.size,
            unlocked = unlocked,
            sort = sort,
            query = q,
            importing = importingCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(sort: LibrarySort) = viewModelScope.launch { settings.setSort(sort) }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.update { it + uris.size }
            val results = uris.map { uri ->
                library.import(uri).also { importing.update { n -> n - 1 } }
            }
            report(results)
        }
    }

    private suspend fun report(results: List<ImportResult>) {
        val added = results.filterIsInstance<ImportResult.Added>()
        val failed = results.filterIsInstance<ImportResult.Failed>()
        if (added.isNotEmpty()) {
            messages.send(
                LibraryMessage(
                    if (added.size == 1) "Added “${added.single().title}”" else "Added ${added.size} books",
                ),
            )
        }
        failed.forEach { messages.send(LibraryMessage(it.explain())) }
    }

    fun setLanguage(book: BookWithCount, language: String) = viewModelScope.launch {
        library.setLanguage(book.id, language)
    }

    fun setPrivate(book: BookWithCount, isPrivate: Boolean) = viewModelScope.launch {
        library.setPrivate(book.id, isPrivate)
        val text = if (isPrivate) {
            if (lock.unlocked.value) "“${book.title}” is now private" else "“${book.title}” is now private and hidden"
        } else {
            "“${book.title}” is visible again"
        }
        messages.send(
            LibraryMessage(text, action = "Undo", onAction = {
                viewModelScope.launch { library.setPrivate(book.id, !isPrivate) }
            }),
        )
    }

    fun remove(book: BookWithCount) = viewModelScope.launch {
        library.remove(book.id)
        messages.send(LibraryMessage("Removed “${book.title}”"))
    }

    fun lockPrivate() = lock.lock()

    fun say(text: String) = viewModelScope.launch { messages.send(LibraryMessage(text)) }
}

private fun LibrarySort.comparator(): Comparator<BookWithCount> = when (this) {
    LibrarySort.RECENT -> compareByDescending<BookWithCount> { it.lastOpenedAt ?: it.addedAt }
    LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.removeArticle() }
    LibrarySort.AUTHOR -> compareBy<BookWithCount, String>(String.CASE_INSENSITIVE_ORDER) { it.author?.lastName() ?: "￿" }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
}

private val articles = Regex("^(the|a|an|der|die|das|ein|eine)\\s+", RegexOption.IGNORE_CASE)

private fun String.removeArticle() = replace(articles, "")

private fun String.lastName() = split(",").first().trim().substringAfterLast(' ')

private fun ImportResult.Failed.explain(): String = when (reason) {
    ImportFailure.KINDLE_FORMAT -> "“$fileName” is a Kindle file. Convert it to EPUB with Calibre, then add it again."
    ImportFailure.UNSUPPORTED -> "“$fileName” isn't an EPUB or PDF, so Reed can't open it."
    ImportFailure.PROTECTED -> "“$fileName” is DRM-protected. Reed can only open DRM-free books."
    ImportFailure.DAMAGED -> "“$fileName” is incomplete or damaged, maybe an interrupted download. Download it again and add it once more."
    ImportFailure.UNREADABLE -> "Couldn't read “$fileName”. The file may be damaged."
}
