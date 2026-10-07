package app.reed.reader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DictationLanguage(val label: String, val tag: String, val spoken: String) {
    EN("EN", "en-US", "English"),
    DE("DE", "de-DE", "German"),
    ;

    companion object {
        fun forBook(language: String?): DictationLanguage =
            if (language?.lowercase()?.startsWith("de") == true) DE else EN
    }
}

enum class DictationError {
    PERMISSION,
    NO_SPEECH,
    NETWORK,
    LANGUAGE,
    UNAVAILABLE,
    BUSY,
    OTHER,
}

/** The intent for an app's own speech screen, used when no recognition service exists. */
fun recognizerScreenIntent(language: DictationLanguage): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.tag)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note")

/**
 * One-tap dictation on top of the platform [SpeechRecognizer]. Streams partial results while
 * listening and hands each final phrase to [onFinal].
 */
class Dictation(private val context: Context) {

    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf<DictationError?>(null)
        private set

    /** 0..1, smoothed microphone level while listening. */
    var level by mutableFloatStateOf(0f)
        private set

    private var recognizer: SpeechRecognizer? = null
    private val watchdog = Handler(Looper.getMainLooper())
    private var ready = false

    /**
     * Installed recognition services that can actually listen. Some apps register a placeholder
     * (FUTO Voice Input's DummyService, filed under the TEST category) that never responds.
     */
    private fun services(): List<ComponentName> = context.packageManager
        .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.GET_RESOLVED_FILTER)
        .filter { it.filter?.hasCategory(Intent.CATEGORY_TEST) != true && it.serviceInfo.exported }
        .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }

    val isAvailable: Boolean get() = services().isNotEmpty()

    /**
     * Apps that offer their own "speak now" screen (RecognizerIntent) but no background
     * service, such as FUTO Voice Input. Used when no recognition service is installed.
     */
    val hasRecognizerScreen: Boolean
        get() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .resolveActivity(context.packageManager) != null

    /** The user's chosen recognizer, or the first installed one when none is chosen. */
    private fun createRecognizer(): SpeechRecognizer {
        val available = services()
        val chosen = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            ?.let(ComponentName::unflattenFromString)
        if (chosen != null && chosen in available) return SpeechRecognizer.createSpeechRecognizer(context)
        return SpeechRecognizer.createSpeechRecognizer(context, available.first())
    }

    /**
     * Starts listening. When the service never gets ready, [onStalled] is called so the caller
     * can fall back to another way of dictating.
     */
    fun start(language: DictationLanguage, onFinal: (String) -> Unit, onStalled: () -> Unit = {}) {
        error = null
        if (!isAvailable) {
            error = DictationError.UNAVAILABLE
            return
        }
        ready = false
        val recognizer = recognizer ?: createRecognizer().also { recognizer = it }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                ready = true
                watchdog.removeCallbacksAndMessages(null)
                listening = true
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                level = level * 0.6f + normalized * 0.4f
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(code: Int) {
                watchdog.removeCallbacksAndMessages(null)
                listening = false
                partial = ""
                level = 0f
                error = when (code) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.PERMISSION
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> DictationError.NO_SPEECH
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                    -> DictationError.NETWORK
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    -> DictationError.LANGUAGE
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> DictationError.BUSY
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> DictationError.OTHER
                }
            }

            override fun onResults(results: Bundle?) {
                watchdog.removeCallbacksAndMessages(null)
                listening = false
                level = 0f
                partial = ""
                results.bestMatch()?.let(onFinal)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults.bestMatch().orEmpty()
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.tag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            },
        )
        listening = true
        watchdog.postDelayed({
            if (!ready) {
                release()
                onStalled()
            }
        }, STALL_MS)
    }

    /**
     * Stops listening; whatever was heard so far is still delivered as a final result.
     * A service that hasn't started, or never answers, is torn down so the button always works.
     */
    fun stop() {
        if (!ready) {
            release()
            return
        }
        recognizer?.stopListening()
        watchdog.postDelayed({ if (listening) release() }, STALL_MS)
    }

    fun clearError() {
        error = null
    }

    fun failStalled() {
        error = DictationError.OTHER
    }

    fun permissionDenied() {
        error = DictationError.PERMISSION
    }

    fun release() {
        watchdog.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        ready = false
        listening = false
        partial = ""
        level = 0f
    }

    private companion object {
        const val STALL_MS = 3_000L
    }
}

private fun Bundle?.bestMatch(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
