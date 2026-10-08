package app.reed.listen

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import app.reed.MainActivity
import app.reed.data.Book
import app.reed.data.Library
import app.reed.data.SettingsStore
import app.reed.reader.ReaderActivity
import app.reed.reader.chapterTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.navigator.media.common.Media3Adapter
import org.readium.navigator.media.common.MediaMetadataFactory
import org.readium.navigator.media.common.MediaMetadataProvider
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.publication.services.content.TextContentTokenizer
import org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer
import org.readium.r2.shared.util.tokenizer.TextUnit
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * Reads one book aloud at a time, sentence by sentence: with a voice Reed downloaded for the
 * book's language ([PiperEngine]) when there is one, otherwise with the phone's speech engine.
 * Lives with the app, not the reader screen, and [ReadAloudService] keeps it going with the
 * screen off. Every sentence reached becomes the book's position.
 */
@OptIn(ExperimentalReadiumApi::class, ExperimentalCoroutinesApi::class)
class ReadAloud(
    private val app: Application,
    private val library: Library,
    private val settings: SettingsStore,
) {
    /** What the reader shows: the sentence being spoken and the word within it, when the engine says. */
    data class State(val bookId: Long, val playing: Boolean, val sentence: Locator, val word: Locator)

    sealed interface Event {
        data class Finished(val bookId: Long) : Event
        data object NoEngine : Event
        data object Unreadable : Event
        data class MissingVoice(val language: String) : Event
        /** No voice for the book's language: Android reads it with the default voice instead. */
        data class FallbackVoice(val language: String) : Event
        /** Nothing on the phone speaks the book's language, but Reed can download [voice]. */
        data class NeedsVoice(val bookId: Long, val voice: CatalogVoice) : Event
        data object NeedsNetwork : Event
        data object Failed : Event
    }

    private class Session(
        val book: Book,
        val navigator: TtsNavigator<*, *, *, *>,
        val setSpeed: (Double) -> Unit,
        val piper: PiperEngine?,
        val publication: Publication,
        val mediaSession: MediaSession,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val bookId: Long get() = book.id
    }

    private val scope = MainScope()

    /** Voices Reed runs itself, downloaded on request. */
    val voices = VoiceStore(app, scope)
    private val starting = Mutex()
    private val current = MutableStateFlow<Session?>(null)
    private var sessionCount = 0

    /** The session shown in the notification and on the lock screen, if any. */
    val mediaSession: StateFlow<MediaSession?> =
        current.map { it?.mediaSession }.stateIn(scope, SharingStarted.Eagerly, null)

    val state: StateFlow<State?> = current.flatMapLatest { session ->
        session?.navigator?.let { navigator ->
            combine(navigator.playback, navigator.location) { playback, location ->
                State(
                    bookId = session.bookId,
                    playing = playback.playWhenReady,
                    sentence = location.utteranceLocator,
                    word = location.tokenLocator ?: location.utteranceLocator,
                )
            }
        } ?: flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val speed: StateFlow<Double> = settings.listenSpeed.stateIn(scope, SharingStarted.Eagerly, 1.0)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    // The speech engine plays in its own process; this keeps Reed awake to hand it the next sentence.
    private val wakeLock = app.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reed:read-aloud")
        .apply { setReferenceCounted(false) }

    init {
        state.map { it?.playing == true }.distinctUntilChanged().onEach { playing ->
            if (playing) wakeLock.acquire(WAKE_TIMEOUT_MS) else if (wakeLock.isHeld) wakeLock.release()
        }.launchIn(scope)
        // Long listens outlast the timeout; every new sentence renews it.
        state.map { it?.takeIf { s -> s.playing }?.sentence }.distinctUntilChanged().onEach {
            if (it != null) wakeLock.acquire(WAKE_TIMEOUT_MS)
        }.launchIn(scope)
    }

    private fun hasEngine(): Boolean =
        app.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()

    fun isReading(bookId: Long): Boolean = current.value?.bookId == bookId

    /** Starts reading [bookId] aloud from [from] (the book's start when null), replacing any other book. */
    suspend fun start(bookId: Long, from: Locator?): Boolean = starting.withLock {
        stop()
        val book = library.book(bookId) ?: return false
        val publication = library.open(book) ?: return fail(Event.Unreadable)
        val start = from?.let { locate(publication, it) }
        val speed = settings.listenSpeed.first()
        val installed = voices.installedFor(book.language)
        val reedVoice = installed?.let { PiperEngine.load(it, PiperPreferences(speed = speed)) }
        // A voice that won't load is no use; removing it lets the reader offer a fresh download.
        if (installed != null && reedVoice == null) voices.delete(installed.voice)
        val engine = if (reedVoice != null) {
            reedNavigator(book, publication, reedVoice, start, speed)
        } else {
            phoneNavigator(book, publication, start, speed)
        }
        if (engine == null) {
            reedVoice?.close()
            publication.close()
            return false
        }
        val (navigator, setSpeed) = engine
        val mediaSession = MediaSession.Builder(app, (navigator as Media3Adapter).asMedia3Player())
            .setId("read-aloud-${sessionCount++}")
            .setSessionActivity(openReader(bookId))
            .build()
        val session = Session(book, navigator, setSpeed, reedVoice, publication, mediaSession)
        current.value = session
        watch(session)
        try {
            app.startService(Intent(app, ReadAloudService::class.java))
        } catch (e: IllegalStateException) {
            Timber.w(e, "Read aloud: couldn't start the service")
            stop()
            return fail(Event.Failed)
        }
        if (from != null && start != null) skipToSentence(navigator, from, start)
        navigator.play()
        true
    }

    private val stopListener = object : TtsNavigator.Listener {
        override fun onStopRequested() = stop()
    }

    private suspend fun reedNavigator(
        book: Book,
        publication: Publication,
        engine: PiperEngine,
        start: Locator?,
        speed: Double,
    ): Pair<TtsNavigator<*, *, *, *>, (Double) -> Unit>? {
        val factory = TtsNavigatorFactory(app, publication, PiperEngineProvider(engine), metadataProvider = metadataFor(book))
            ?: return fail(Event.Unreadable).let { null }
        val navigator = factory.createNavigator(stopListener, start, PiperPreferences(speed = speed)).getOrElse {
            Timber.w("Read aloud: %s", it.message)
            return fail(Event.Unreadable).let { null }
        }
        return navigator to { s: Double -> navigator.submitPreferences(PiperPreferences(speed = s)) }
    }

    /** The phone's speech engine, if it speaks the book's language; otherwise offers Reed's voice. */
    private suspend fun phoneNavigator(
        book: Book,
        publication: Publication,
        start: Locator?,
        speed: Double,
    ): Pair<TtsNavigator<*, *, *, *>, (Double) -> Unit>? {
        val downloadable = catalogVoiceFor(book.language)
        // Android never answers a speech request on a phone without an engine; ask first.
        if (!hasEngine()) {
            return fail(downloadable?.let { Event.NeedsVoice(book.id, it) } ?: Event.NoEngine).let { null }
        }
        val factory = TtsNavigatorFactory(app, publication, metadataProvider = metadataFor(book))
            ?: return fail(Event.Unreadable).let { null }
        val created = withTimeoutOrNull(ENGINE_TIMEOUT_MS) {
            factory.createNavigator(stopListener, start, voicePreferences(book, speed))
        } ?: return fail(downloadable?.let { Event.NeedsVoice(book.id, it) } ?: Event.NoEngine).let { null }
        val navigator = created.getOrElse { error ->
            Timber.w("Read aloud: %s", error.message)
            val event = when {
                error !is TtsNavigatorFactory.Error.EngineInitialization -> Event.Unreadable
                downloadable != null -> Event.NeedsVoice(book.id, downloadable)
                else -> Event.NoEngine
            }
            return fail(event).let { null }
        }
        book.language?.let { language ->
            val wanted = Locale.forLanguageTag(language).iso3()
            if (navigator.voices.none { it.language.locale.iso3() == wanted }) {
                // Android would read it with another language's voice: offer Reed's instead.
                if (downloadable != null) {
                    navigator.close()
                    return fail(Event.NeedsVoice(book.id, downloadable)).let { null }
                }
                _events.tryEmit(Event.FallbackVoice(Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)))
            }
        }
        return navigator to { s: Double -> navigator.submitPreferences(voicePreferences(book, s)) }
    }

    /**
     * Readium starts at the paragraph holding [target]; step through its sentences to the one
     * where [target]'s words begin (a saved sentence, the first words on screen or a selection),
     * or stay at the paragraph's start if they aren't there.
     */
    private suspend fun skipToSentence(navigator: TtsNavigator<*, *, *, *>, target: Locator, paragraph: Locator) {
        val words = target.text.highlight?.collapsed()?.takeIf { it.isNotEmpty() } ?: return
        val opening = words.take(30)
        fun beginsIn(sentence: String): Boolean {
            val s = sentence.collapsed()
            // A position from the page view holds its whole paragraph, which starts with the sentence.
            if (s.contains(opening) || words.startsWith(s)) return true
            // The words start near the end of this sentence and run on into the next.
            return (maxOf(0, s.length - opening.length) until s.length - 2).any { words.startsWith(s.substring(it)) }
        }
        for (step in 0 until MAX_SKIPPED_SENTENCES) {
            val here = navigator.location.value
            if (beginsIn(here.utterance)) return
            if (here.href != target.href || !navigator.hasNextUtterance()) break
            navigator.skipToNextUtterance()
            withTimeoutOrNull(1_000) { navigator.location.first { it != here } } ?: break
        }
        navigator.go(paragraph)
    }

    /**
     * The paragraph holding [from], found by its text. A position from the page view names its
     * paragraph with a CSS path from the WebView, which doesn't always match the document Readium
     * reads aloud from (some books nest paragraphs in links, and the two parsers disagree).
     */
    private suspend fun locate(publication: Publication, from: Locator): Locator {
        val wanted = from.text.highlight?.collapsed()?.take(200)?.takeIf { it.length >= 3 } ?: return from
        fun Content.Element.holdsWanted() = (this as? Content.TextualElement)?.text?.collapsed()?.contains(wanted) == true
        publication.content(from)?.iterator()?.let { here ->
            if (here.hasNext() && here.next().holdsWanted()) return from
        }
        val chapter = publication.content(from.copy(locations = Locator.Locations(), text = Locator.Text()))?.iterator()
            ?: return from
        while (chapter.hasNext()) {
            val element = chapter.next()
            if (element.locator.href != from.href) break
            if (element.holdsWanted()) return element.locator
        }
        return from
    }

    fun play() {
        current.value?.navigator?.play()
    }

    fun pause() {
        current.value?.navigator?.pause()
    }

    fun next() {
        current.value?.navigator?.skipToNextUtterance()
    }

    fun previous() {
        current.value?.navigator?.skipToPreviousUtterance()
    }

    /** Moves reading to the sentence where [locator]'s words begin. */
    suspend fun go(locator: Locator) {
        val session = current.value ?: return
        val navigator = session.navigator
        val wasPlaying = navigator.playback.value.playWhenReady
        // Paused, stepping through sentences only moves a cursor; playing, each step would speak.
        navigator.pause()
        val paragraph = locate(session.publication, locator)
        val before = navigator.location.value
        navigator.go(paragraph)
        withTimeoutOrNull(1_500) { navigator.location.first { it != before } }
        skipToSentence(navigator, locator, paragraph)
        if (wasPlaying) navigator.play()
    }

    fun setSpeed(speed: Double) {
        scope.launch { settings.setListenSpeed(speed) }
    }

    fun stop() {
        val session = current.value ?: return
        current.value = null
        session.scope.cancel()
        session.mediaSession.release()
        session.navigator.close()
        session.publication.close()
    }

    private fun watch(session: Session) {
        val navigator = session.navigator
        navigator.location
            .map { it.utteranceLocator }
            .distinctUntilChanged()
            .onEach { library.saveListenPosition(session.bookId, it) }
            .launchIn(session.scope)
        navigator.playback
            .map { it.state }
            .distinctUntilChanged()
            .onEach { state ->
                when (state) {
                    is TtsNavigator.State.Ended -> {
                        library.clearListenPosition(session.bookId)
                        _events.emit(Event.Finished(session.bookId))
                        stop()
                    }
                    is TtsNavigator.State.Failure -> {
                        Timber.w("Read aloud failed: %s", state.error.message)
                        _events.emit(eventFor(state.error))
                        stop()
                    }
                    else -> Unit
                }
            }
            .launchIn(session.scope)
        settings.listenSpeed
            .distinctUntilChanged()
            .onEach { session.setSpeed(it) }
            .launchIn(session.scope)
        // Reed's voice synthesises the next sentence while this one plays, so there's no pause.
        session.piper?.let { piper ->
            val lookahead = Lookahead(session.publication, session.book.language?.let { Language(it) })
            navigator.location
                .mapLatest { location -> lookahead.after(location.utteranceLocator, location.utterance, count = 3) }
                .distinctUntilChanged()
                .onEach { upcoming -> upcoming.forEach(piper::prefetch) }
                .launchIn(session.scope)
        }
    }

    /** The voice for the book's language as Reed knows it, which can differ from what the file declares. */
    private fun voicePreferences(book: Book, speed: Double) =
        AndroidTtsPreferences(language = book.language?.let { Language(it) }, speed = speed)

    private fun eventFor(error: TtsNavigator.Error): Event = when (val cause = (error as? TtsNavigator.Error.EngineError<*>)?.cause) {
        is AndroidTtsEngine.Error.LanguageMissingData ->
            Event.MissingVoice(Locale.forLanguageTag(cause.language.code).getDisplayLanguage(Locale.ENGLISH))
        AndroidTtsEngine.Error.Network, AndroidTtsEngine.Error.NetworkTimeout -> Event.NeedsNetwork
        null -> Event.Unreadable
        else -> Event.Failed
    }

    private fun fail(event: Event): Boolean {
        _events.tryEmit(event)
        return false
    }

    /** Tapping the notification opens the book, with the library behind it. */
    private fun openReader(bookId: Long): PendingIntent =
        TaskStackBuilder.create(app)
            .addNextIntent(Intent(app, MainActivity::class.java))
            .addNextIntent(ReaderActivity.intent(app, bookId))
            .getPendingIntent(bookId.toInt(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!

    /** Chapter over book title, with the cover. A private book shows neither on the lock screen. */
    private fun metadataFor(book: Book) = MediaMetadataProvider { publication ->
        object : MediaMetadataFactory {
            override suspend fun publicationMetadata(): MediaMetadata = base().build()

            override suspend fun resourceMetadata(index: Int): MediaMetadata {
                val chapter = publication.readingOrder.getOrNull(index)
                    ?.let { publication.chapterTitle(it.url()) }
                    ?.takeUnless { book.isPrivate }
                return base().setTrackNumber(index).apply {
                    if (chapter != null) setTitle(chapter).setArtist(book.title)
                }.build()
            }

            private fun base() = MediaMetadata.Builder().apply {
                if (book.isPrivate) {
                    setTitle("Reading aloud")
                    setArtist("Private book")
                } else {
                    setTitle(book.title)
                    setArtist(book.author)
                    setAlbumTitle(book.title)
                    book.coverPath?.let { setArtworkUri(Uri.fromFile(File(it))) }
                }
            }
        }
    }

    private companion object {
        const val WAKE_TIMEOUT_MS = 10 * 60 * 1000L
        const val ENGINE_TIMEOUT_MS = 15_000L
        // Some books are one paragraph per chapter, so the sentence can be far from its "paragraph".
        const val MAX_SKIPPED_SENTENCES = 5_000
    }
}

/** Engines name languages either way ("de", "deu"); the three-letter code compares them. */
private fun Locale.iso3(): String = runCatching { isO3Language }.getOrDefault(language)

private fun String.collapsed() = replace(Regex("\\s+"), " ").trim()

/** Speeds offered by the reader's speed button, in order. */
val ListenSpeeds = listOf(0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0)

fun Flow<ReadAloud.State?>.forBook(bookId: Long): Flow<ReadAloud.State?> = map { it?.takeIf { s -> s.bookId == bookId } }

/**
 * Walks the book alongside the voice to tell which sentence Readium will ask for next, split the
 * way Readium splits it. Positions only say where a sentence's paragraph is (in some books, just
 * "body"), so it keeps its place and only searches again after a jump.
 */
@OptIn(ExperimentalReadiumApi::class)
private class Lookahead(private val publication: Publication, language: Language?) {
    private val tokenizer = TextContentTokenizer(language, overrideContentLanguage = true) {
        DefaultTextContentTokenizer(TextUnit.Sentence, it)
    }
    private var elements: Content.Iterator? = null
    private val queue = ArrayDeque<String>()

    /** The [count] sentences after [current], which is being read at [at]. */
    suspend fun after(at: Locator, current: String, count: Int): List<String> = withContext(Dispatchers.Default) {
        val wanted = current.collapsed()
        // Usually the sentence being read is the next one in line.
        if (skipTo(wanted, limit = 50)) return@withContext peek(count)
        elements = publication.content(at)?.iterator() ?: return@withContext emptyList()
        queue.clear()
        if (skipTo(wanted, limit = 5_000)) peek(count) else emptyList()
    }

    private suspend fun skipTo(wanted: String, limit: Int): Boolean {
        repeat(limit) {
            val next = poll() ?: return false
            if (next.collapsed() == wanted) return true
        }
        return false
    }

    private suspend fun peek(count: Int): List<String> {
        while (queue.size < count && fill()) Unit
        return queue.take(count)
    }

    private suspend fun poll(): String? {
        if (queue.isEmpty()) fill()
        return queue.removeFirstOrNull()
    }

    /** Adds the next paragraph's sentences; false at the end of the book. */
    private suspend fun fill(): Boolean {
        val iterator = elements ?: return false
        val before = queue.size
        while (queue.size == before && iterator.hasNext()) {
            tokenizer.tokenize(iterator.next()).forEach { element ->
                val texts = (element as? Content.TextElement)?.segments?.map { it.text }
                    ?: listOfNotNull((element as? Content.TextualElement)?.text)
                texts.filterTo(queue) { text -> text.any { it.isLetterOrDigit() } }
            }
        }
        return queue.size > before
    }
}
