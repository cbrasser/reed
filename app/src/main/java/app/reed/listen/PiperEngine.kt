package app.reed.listen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Try
import timber.log.Timber
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

data class PiperPreferences(
    override val language: Language? = null,
    val speed: Double? = null,
) : TtsEngine.Preferences<PiperPreferences> {
    override fun plus(other: PiperPreferences) =
        PiperPreferences(language = other.language ?: language, speed = other.speed ?: speed)
}

data class PiperSettings(
    override val language: Language?,
    val speed: Double,
) : TtsEngine.Settings {
    // A Piper model speaks one language; content tagged otherwise is read with it anyway.
    override val overrideContentLanguage: Boolean get() = true
}

class PiperVoice(override val language: Language, val name: String) : TtsEngine.Voice

sealed class PiperError(override val message: String) : TtsEngine.Error {
    override val cause: org.readium.r2.shared.util.Error? get() = null
    data object Synthesis : PiperError("The voice couldn't speak this sentence.")
    data object Output : PiperError("Audio output failed.")
}

class PiperPreferencesEditor(override var preferences: PiperPreferences) : PreferencesEditor<PiperPreferences> {
    override fun clear() {
        preferences = PiperPreferences()
    }
}

/**
 * Reads aloud with a downloaded Piper voice, on the phone. Sentences are synthesised on one
 * thread and played on another, so the next one (see [prefetch]) is ready when this one ends.
 */
@OptIn(ExperimentalReadiumApi::class)
class PiperEngine private constructor(
    private val tts: OfflineTts,
    voice: InstalledVoice,
    initialPreferences: PiperPreferences,
) : TtsEngine<PiperSettings, PiperPreferences, PiperError, PiperVoice> {

    private val main = Handler(Looper.getMainLooper())
    private val synthesis: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "piper-synthesis") }
    private val playback: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "piper-playback") }

    private val language = Language(voice.voice.language)
    override val voices: Set<PiperVoice> = setOf(PiperVoice(language, voice.voice.name))

    private val _settings = MutableStateFlow(settingsFor(initialPreferences))
    override val settings: StateFlow<PiperSettings> = _settings.asStateFlow()

    private var listener: TtsEngine.Listener<PiperError>? = null

    /** Synthesised (or being synthesised) sentences by text and speed: the current one and the next three. */
    private val audio = object : LinkedHashMap<String, Future<FloatArray?>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Future<FloatArray?>>) = size > 6
    }

    private class Request(val id: TtsEngine.RequestId, val key: String) {
        @Volatile var cancelled = false
    }
    private val current = AtomicReference<Request?>(null)

    private val sampleRate = tts.sampleRate()
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    override fun setListener(listener: TtsEngine.Listener<PiperError>?) {
        this.listener = listener
    }

    override fun submitPreferences(preferences: PiperPreferences) {
        val next = settingsFor(preferences)
        if (next.speed != _settings.value.speed) synchronized(audio) { audio.clear() }
        _settings.value = next
    }

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        val request = Request(requestId, key(text))
        current.getAndSet(request)?.cancelled = true
        val samples = synthesise(text)
        playback.execute { play(request, samples) }
    }

    /** Starts synthesising a sentence expected next, so it plays without a pause. */
    fun prefetch(text: String) {
        synthesise(text)
    }

    override fun stop() {
        val request = current.getAndSet(null) ?: return
        request.cancelled = true
        track.pause()
        track.flush()
        main.post { listener?.onInterrupted(request.id) }
    }

    override fun close() {
        stop()
        synchronized(audio) {
            audio.values.forEach { it.cancel(false) }
            audio.clear()
        }
        // Each thread frees what it uses once its current work ends.
        playback.execute { track.release() }
        synthesis.execute { tts.release() }
        playback.shutdown()
        synthesis.shutdown()
    }

    private fun synthesise(text: String): Future<FloatArray?> = synchronized(audio) {
        val key = key(text)
        audio[key]?.let { return it }
        val speed = _settings.value.speed.toFloat()
        val job = synthesis.submit(
            Callable {
                val started = System.nanoTime()
                runCatching { tts.generate(text.trim(), 0, speed).samples }
                    .onSuccess {
                        Timber.d(
                            "Synthesised %d chars in %d ms, %.1f s of audio",
                            text.length, (System.nanoTime() - started) / 1_000_000, it.size / sampleRate.toFloat(),
                        )
                    }
                    .onFailure { Timber.w(it, "Piper couldn't synthesise") }
                    .getOrNull()
            },
        )
        audio[key] = job
        return job
    }

    private fun play(request: Request, job: Future<FloatArray?>) {
        if (request.cancelled) return
        main.post { listener?.onStart(request.id) }
        val samples = try {
            job.get()
        } catch (e: Exception) {
            null
        }
        if (request.cancelled) return
        if (samples == null) {
            synchronized(audio) { audio.remove(request.key) }
            finish(request) { onError(request.id, PiperError.Synthesis) }
            return
        }
        try {
            // A stopped sentence can leave a chunk in the buffer; it mustn't play before this one.
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.flush()
            track.play()
            val start = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            var offset = 0
            while (offset < samples.size && !request.cancelled) {
                val written = track.write(samples, offset, minOf(4096, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw IllegalStateException("AudioTrack write failed: $written")
                offset += written
            }
            // Wait until the last sample has actually been heard.
            val end = start + samples.size
            while (!request.cancelled && (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < end &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING
            ) {
                Thread.sleep(15)
            }
        } catch (e: InterruptedException) {
            return
        } catch (e: Exception) {
            Timber.w(e, "Piper playback failed")
            finish(request) { onError(request.id, PiperError.Output) }
            return
        }
        if (!request.cancelled) finish(request) { onDone(request.id) }
    }

    private fun finish(request: Request, call: TtsEngine.Listener<PiperError>.() -> Unit) {
        current.compareAndSet(request, null)
        main.post { listener?.call() }
    }

    private fun key(text: String) = "${_settings.value.speed}|${text.trim().replace(Regex("\\s+"), " ")}"

    private fun settingsFor(preferences: PiperPreferences) =
        PiperSettings(language = language, speed = preferences.speed ?: 1.0)

    companion object {
        /** Loads the voice (a second or two); null if it can't run on this phone. */
        suspend fun load(voice: InstalledVoice, preferences: PiperPreferences): PiperEngine? = withContext(Dispatchers.IO) {
            try {
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = voice.model.path,
                            tokens = voice.tokens.path,
                            dataDir = voice.espeakData.path,
                        ),
                        numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4),
                        provider = "cpu",
                    ),
                )
                PiperEngine(OfflineTts(null, config), voice, preferences)
            } catch (e: Throwable) {
                // UnsatisfiedLinkError on phones without the 64-bit ARM library, or a broken model.
                Timber.w(e, "Couldn't load voice %s", voice.voice.id)
                null
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
class PiperEngineProvider(private val engine: PiperEngine) :
    TtsEngineProvider<PiperSettings, PiperPreferences, PiperPreferencesEditor, PiperError, PiperVoice> {

    override suspend fun createEngine(publication: Publication, initialPreferences: PiperPreferences): Try<PiperEngine, org.readium.r2.shared.util.Error> {
        engine.submitPreferences(initialPreferences)
        return Try.success(engine)
    }

    override fun createPreferencesEditor(publication: Publication, initialPreferences: PiperPreferences) =
        PiperPreferencesEditor(initialPreferences)

    override fun createEmptyPreferences() = PiperPreferences()

    override fun getPlaybackParameters(settings: PiperSettings) = PlaybackParameters(settings.speed.toFloat())

    override fun updatePlaybackParameters(previousPreferences: PiperPreferences, playbackParameters: PlaybackParameters) =
        previousPreferences.copy(speed = playbackParameters.speed.toDouble())

    override fun mapEngineError(error: PiperError) =
        PlaybackException(error.message, null, PlaybackException.ERROR_CODE_UNSPECIFIED)
}
