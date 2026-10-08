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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.navigator.media.common.MediaMetadataFactory
import org.readium.navigator.media.common.MediaMetadataProvider
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * Reads one book aloud at a time with the phone's speech engine, sentence by sentence.
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
        data object NeedsNetwork : Event
        data object Failed : Event
    }

    private class Session(
        val bookId: Long,
        val navigator: AndroidTtsNavigator,
        val publication: Publication,
        val mediaSession: MediaSession,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private val scope = MainScope()
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
        // Android never answers a speech request on a phone without an engine; ask first.
        if (!hasEngine()) return fail(Event.NoEngine)
        val book = library.book(bookId) ?: return false
        val publication = library.open(book) ?: return fail(Event.Unreadable)
        val factory = TtsNavigatorFactory(app, publication, metadataProvider = metadataFor(book))
        if (factory == null) {
            publication.close()
            return fail(Event.Unreadable)
        }
        val created = withTimeoutOrNull(ENGINE_TIMEOUT_MS) {
            factory.createNavigator(
                listener = object : TtsNavigator.Listener {
                    override fun onStopRequested() = stop()
                },
                initialLocator = from,
                initialPreferences = AndroidTtsPreferences(speed = settings.listenSpeed.first()),
            )
        }
        if (created == null) {
            publication.close()
            return fail(Event.NoEngine)
        }
        val navigator = created.getOrElse { error ->
            Timber.w("Read aloud: %s", error.message)
            publication.close()
            return fail(if (error is TtsNavigatorFactory.Error.EngineInitialization) Event.NoEngine else Event.Unreadable)
        }
        val mediaSession = MediaSession.Builder(app, navigator.asMedia3Player())
            .setId("read-aloud-${sessionCount++}")
            .setSessionActivity(openReader(bookId))
            .build()
        val session = Session(bookId, navigator, publication, mediaSession)
        current.value = session
        watch(session)
        try {
            app.startService(Intent(app, ReadAloudService::class.java))
        } catch (e: IllegalStateException) {
            Timber.w(e, "Read aloud: couldn't start the service")
            stop()
            return fail(Event.Failed)
        }
        navigator.play()
        true
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

    fun go(locator: Locator) {
        current.value?.navigator?.go(locator)
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
            .onEach { navigator.submitPreferences(AndroidTtsPreferences(speed = it)) }
            .launchIn(session.scope)
    }

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
    }
}

/** Speeds offered by the reader's speed button, in order. */
val ListenSpeeds = listOf(0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0)

fun Flow<ReadAloud.State?>.forBook(bookId: Long): Flow<ReadAloud.State?> = map { it?.takeIf { s -> s.bookId == bookId } }
