package app.reed.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.reed.data.Book
import app.reed.data.BookFormat
import app.reed.data.Note
import app.reed.data.NoteKind
import app.reed.data.ReadingSettings
import app.reed.data.serialize
import app.reed.data.toLocator
import app.reed.reed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** A note being written or edited in the note sheet. */
data class NoteDraft(
    val noteId: Long?,
    val kind: NoteKind,
    val locator: Locator,
    val passage: String?,
    val text: String,
    val chapter: String?,
    val progression: Double,
    val page: Int?,
)

enum class ReaderSheet { NONE, NOTES, SETTINGS }

data class ReaderMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

class ReaderSession(val book: Book, val publication: Publication, val initialLocator: Locator?)

@OptIn(FlowPreview::class)
class ReaderViewModel(
    app: Application,
    val bookId: Long,
    private val jumpTo: String?,
    private val jumpNoteId: Long?,
) : AndroidViewModel(app) {

    private val library = app.reed.library
    private val settingsStore = app.reed.settings

    var session: ReaderSession? = null
        private set

    val book: StateFlow<Book?> = library.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notes: StateFlow<List<Note>> = library.notes(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<ReadingSettings> = settingsStore.reading
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingSettings())

    val chromeVisible = MutableStateFlow(false)
    val sheet = MutableStateFlow(ReaderSheet.NONE)
    val draft = MutableStateFlow<NoteDraft?>(null)
    val flashNoteId = MutableStateFlow<Long?>(null)
    val currentLocator = MutableStateFlow<Locator?>(null)

    private val messageChannel = Channel<ReaderMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private var flashJob: Job? = null

    init {
        viewModelScope.launch {
            currentLocator.filterNotNull().debounce(600).collect { library.savePosition(bookId, it) }
        }
    }

    /** Opens the publication once; survives configuration changes. */
    suspend fun load(): ReaderSession? {
        session?.let { return it }
        val book = library.book(bookId) ?: return null
        val publication = library.open(book) ?: return null
        val initial = jumpTo?.toLocator() ?: book.lastLocator?.toLocator()
        library.markOpened(bookId)
        jumpNoteId?.let { flash(it, delayMs = 700) }
        return ReaderSession(book, publication, initial).also { session = it }
    }

    val format: BookFormat? get() = session?.book?.format

    fun onLocator(locator: Locator) {
        currentLocator.value = if (locator.title.isNullOrBlank()) locator.copy(title = chapterAt(locator)) else locator
    }

    /** Many books leave locator titles empty; fall back to the table of contents. */
    private fun chapterAt(locator: Locator): String? {
        val publication = session?.publication ?: return null
        val href = locator.href.removeFragment()
        fun search(links: List<Link>): String? {
            for (link in links) {
                if (link.url().removeFragment() == href && !link.title.isNullOrBlank()) return link.title
                search(link.children)?.let { return it }
            }
            return null
        }
        return search(publication.tableOfContents)
            ?: publication.readingOrder.firstOrNull { it.url().removeFragment() == href }?.title
    }

    fun toggleChrome() {
        chromeVisible.value = !chromeVisible.value
    }

    fun startPassageNote(selection: Locator) {
        val here = currentLocator.value
        val passage = selection.text.highlight?.trim()?.takeIf { it.isNotEmpty() } ?: return
        chromeVisible.value = false
        draft.value = NoteDraft(
            noteId = null,
            kind = NoteKind.PASSAGE,
            locator = selection,
            passage = passage.collapseWhitespace(),
            text = "",
            chapter = selection.title?.takeIf { it.isNotBlank() } ?: here?.title ?: chapterAt(selection),
            progression = selection.locations.totalProgression ?: here?.locations?.totalProgression ?: 0.0,
            page = null,
        )
    }

    fun startPageNote() {
        val here = currentLocator.value ?: return
        val existing = notes.value.firstOrNull { it.kind == NoteKind.PAGE && it.locator.toLocator()?.samePage(here) == true }
        if (existing != null) {
            edit(existing)
            return
        }
        chromeVisible.value = false
        draft.value = NoteDraft(
            noteId = null,
            kind = NoteKind.PAGE,
            locator = here,
            passage = null,
            text = "",
            chapter = here.title,
            progression = here.locations.totalProgression ?: 0.0,
            page = if (format == BookFormat.PDF) here.locations.position else null,
        )
    }

    fun edit(note: Note) {
        val locator = note.locator.toLocator() ?: return
        chromeVisible.value = false
        sheet.value = ReaderSheet.NONE
        draft.value = NoteDraft(
            noteId = note.id,
            kind = note.kind,
            locator = locator,
            passage = note.passage,
            text = note.text,
            chapter = note.chapter,
            progression = note.progression,
            page = note.page,
        )
    }

    fun editById(id: Long) {
        notes.value.firstOrNull { it.id == id }?.let(::edit)
    }

    /** Saves the draft. Blank text on a new note discards it; blank text on an existing note keeps the old text. */
    fun saveDraft(text: String) {
        val current = draft.value ?: return
        draft.value = null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = if (current.noteId == null) {
                library.addNote(
                    Note(
                        bookId = bookId,
                        kind = current.kind,
                        locator = current.locator.serialize(),
                        passage = current.passage,
                        text = trimmed,
                        chapter = current.chapter,
                        progression = current.progression,
                        page = current.page,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                val existing = library.note(current.noteId) ?: return@launch
                if (existing.text != trimmed) library.updateNote(existing.copy(text = trimmed, updatedAt = now))
                existing.id
            }
            flash(id)
        }
    }

    fun deleteDraftNote() {
        val id = draft.value?.noteId ?: return
        draft.value = null
        viewModelScope.launch {
            val note = library.note(id) ?: return@launch
            library.deleteNote(note)
            messageChannel.send(
                ReaderMessage("Note deleted", action = "Undo", onAction = {
                    viewModelScope.launch { library.addNote(note.copy(id = 0)) }
                }),
            )
        }
    }

    fun dismissDraft() {
        draft.value = null
    }

    fun flash(id: Long, delayMs: Long = 0) {
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            flashNoteId.value = id
            delay(1800)
            flashNoteId.value = null
        }
    }

    fun updateSettings(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { settingsStore.updateReading(transform) }
    }

    override fun onCleared() {
        session?.publication?.close()
        session = null
    }

    companion object {
        fun factory(app: Application, bookId: Long, jumpTo: String?, jumpNoteId: Long?): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReaderViewModel(app, bookId, jumpTo, jumpNoteId) } }
    }
}

private fun String.collapseWhitespace() = replace(Regex("\\s+"), " ")

private fun Locator.samePage(other: Locator): Boolean {
    val a = locations.position
    val b = other.locations.position
    return href == other.href && a != null && a == b
}
