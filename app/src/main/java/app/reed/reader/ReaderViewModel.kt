package app.reed.reader

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.speech.tts.TextToSpeech
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
import app.reed.listen.ListenSpeeds
import app.reed.listen.ReadAloud
import app.reed.listen.forBook
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

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
    private val readAloud = app.reed.readAloud

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

    /** Reading aloud, when it's this book being read. */
    val listening: StateFlow<ReadAloud.State?> = readAloud.state.forBook(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val listenSpeed: StateFlow<Double> = readAloud.speed

    private val messageChannel = Channel<ReaderMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private var flashJob: Job? = null

    /**
     * The sentence reading aloud stopped at, kept while the reader stays on the page showing it
     * ([listenPage]). Moving to another page forgets it, so the next listen starts there instead.
     */
    private var resumeAt: Locator? = null
    private var listenPage: Locator? = null
    /** Page changes until then come from following the voice, opening the book or relayout, not the reader. */
    private var settleUntil = 0L

    init {
        viewModelScope.launch {
            currentLocator.filterNotNull().debounce(600).collect { library.savePosition(bookId, it) }
        }
        listening.filterNotNull().onEach { resumeAt = it.sentence }.launchIn(viewModelScope)
        // The microphone shouldn't hear the book: a note sheet pauses the voice.
        draft.filterNotNull().onEach { if (listening.value?.playing == true) readAloud.pause() }.launchIn(viewModelScope)
        readAloud.events.onEach(::onReadAloudEvent).launchIn(viewModelScope)
    }

    /** Opens the publication once; survives configuration changes. */
    suspend fun load(): ReaderSession? {
        session?.let { return it }
        val book = library.book(bookId) ?: return null
        val publication = library.open(book) ?: return null
        if (jumpTo == null) resumeAt = book.listenLocator?.toLocator()
        // A sentence read aloud is only kept while its page is the one being read, so it can open there.
        val initial = jumpTo?.toLocator() ?: resumeAt ?: book.lastLocator?.toLocator()
        library.markOpened(bookId)
        jumpNoteId?.let { flash(it, delayMs = 700) }
        return ReaderSession(book, publication, initial).also { session = it }
    }

    val format: BookFormat? get() = session?.book?.format

    fun onLocator(locator: Locator) {
        currentLocator.value = if (locator.title.isNullOrBlank()) locator.copy(title = chapterAt(locator)) else locator
        trackListenPage(locator)
    }

    private fun trackListenPage(page: Locator) {
        if (resumeAt == null) return
        val anchor = listenPage
        if (anchor == null || listening.value?.playing == true || SystemClock.elapsedRealtime() < settleUntil) {
            listenPage = page
        } else if (!page.samePlace(anchor)) {
            resumeAt = null
            listenPage = null
            viewModelScope.launch { library.clearListenPosition(bookId) }
        }
    }

    /** Where reading aloud is, unless the reader has since turned elsewhere. */
    fun listenPositionToShow(): Locator? = listening.value?.sentence?.takeIf { resumeAt != null }

    /** The page is about to move for a reason other than the reader turning it. */
    fun settle(ms: Long = 1200) {
        settleUntil = SystemClock.elapsedRealtime() + ms
    }

    /**
     * Reads aloud from where it last stopped if that's still on screen, otherwise from the top of
     * the page ([pageStart]).
     */
    fun listen(pageStart: suspend () -> Locator?) {
        chromeVisible.value = false
        viewModelScope.launch {
            if (readAloud.isReading(bookId)) {
                if (resumeAt == null) pageStart()?.let { readAloud.go(it) }
                readAloud.play()
            } else {
                readAloud.start(bookId, resumeAt ?: pageStart() ?: currentLocator.value)
            }
        }
    }

    fun pauseListening() = readAloud.pause()

    fun resumeListening() = readAloud.play()

    fun nextSentence() = readAloud.next()

    fun previousSentence() = readAloud.previous()

    fun stopListening() = readAloud.stop()

    fun cycleListenSpeed() {
        val next = ListenSpeeds.firstOrNull { it > listenSpeed.value + 0.01 } ?: ListenSpeeds.first()
        readAloud.setSpeed(next)
    }

    private suspend fun onReadAloudEvent(event: ReadAloud.Event) {
        val message = when (event) {
            is ReadAloud.Event.Finished -> {
                if (event.bookId == bookId) resumeAt = null
                return
            }
            ReadAloud.Event.NoEngine -> ReaderMessage(
                "No text-to-speech engine on this phone",
                action = "Settings",
                onAction = { openSystem(Intent(TTS_SETTINGS)) },
            )
            ReadAloud.Event.Unreadable -> ReaderMessage("This book can't be read aloud")
            is ReadAloud.Event.MissingVoice -> ReaderMessage(
                "No ${event.language} voice installed",
                action = "Install",
                onAction = { openSystem(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) },
            )
            is ReadAloud.Event.FallbackVoice -> ReaderMessage(
                "No ${event.language} voice installed, reading with the default voice",
                action = "Install",
                onAction = { openSystem(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) },
            )
            ReadAloud.Event.NeedsNetwork -> ReaderMessage("This voice needs an internet connection")
            ReadAloud.Event.Failed -> ReaderMessage("Reading aloud stopped")
        }
        messageChannel.send(message)
    }

    private fun openSystem(intent: Intent) {
        runCatching { getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun chapterAt(locator: Locator): String? = session?.publication?.chapterTitle(locator.href)

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
        settle(2000)
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

/** Many books leave locator titles empty; fall back to the table of contents. */
fun Publication.chapterTitle(href: Url): String? {
    val target = href.removeFragment()
    fun search(links: List<Link>): String? {
        for (link in links) {
            if (link.url().removeFragment() == target && !link.title.isNullOrBlank()) return link.title
            search(link.children)?.let { return it }
        }
        return null
    }
    return search(tableOfContents)
        ?: readingOrder.firstOrNull { it.url().removeFragment() == target }?.title
}

private fun String.collapseWhitespace() = replace(Regex("\\s+"), " ")

/** Same page of the visual navigator: positions are only comparable within one layout. */
private fun Locator.samePlace(other: Locator): Boolean {
    val a = locations.progression ?: return false
    val b = other.locations.progression ?: return false
    return href == other.href && abs(a - b) < 1e-6
}

private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

private fun Locator.samePage(other: Locator): Boolean {
    val a = locations.position
    val b = other.locations.position
    return href == other.href && a != null && a == b
}
