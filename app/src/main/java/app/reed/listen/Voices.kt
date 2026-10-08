package app.reed.listen

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** A voice Reed can download and run itself: a Piper model packaged by sherpa-onnx. */
data class CatalogVoice(
    val id: String,
    /** ISO 639-1 language code. */
    val language: String,
    val name: String,
    val sizeMb: Int,
) {
    val languageName: String get() = Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$id.tar.bz2"
}

/**
 * One voice per language, the best-sounding Piper voice. Full precision: the fp16 builds are half
 * the size but sherpa-onnx's runtime refuses them, and int8 loses some of the voice.
 */
val VoiceCatalog = listOf(
    CatalogVoice("de_DE-thorsten-high", "de", "Thorsten", 116),
    CatalogVoice("en_GB-cori-high", "en", "Cori", 116),
)

fun catalogVoiceFor(language: String?): CatalogVoice? {
    val code = language?.substringBefore('-')?.lowercase() ?: return null
    return VoiceCatalog.firstOrNull { it.language == code }
}

/** The files of a downloaded voice, ready for sherpa-onnx. */
class InstalledVoice(val voice: CatalogVoice, dir: File) {
    val model: File = dir.listFiles { f -> f.extension == "onnx" }!!.single()
    val tokens = File(dir, "tokens.txt")
    val espeakData = File(dir, "espeak-ng-data")
}

sealed interface VoiceDownload {
    /** Bytes so far out of the total, when the server says how large the voice is. */
    data class Downloading(val fraction: Float?) : VoiceDownload
    data object Unpacking : VoiceDownload
}

/** Voices kept in app storage: which are installed, downloads in progress, and failures. */
class VoiceStore(context: Context, private val scope: CoroutineScope) {

    private val root = File(context.filesDir, "voices").apply { mkdirs() }
    private val cache = context.cacheDir
    private val http = OkHttpClient()

    init {
        // Voices dropped from the catalog, or left half-unpacked, only take up space.
        val known = VoiceCatalog.map { it.id }.toSet()
        root.listFiles()?.filter { it.name !in known }?.forEach { it.deleteRecursively() }
    }

    private val _installed = MutableStateFlow(scanInstalled())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, VoiceDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, VoiceDownload>> = _downloads.asStateFlow()

    private val _failures = MutableSharedFlow<CatalogVoice>(extraBufferCapacity = 4)
    val failures: SharedFlow<CatalogVoice> = _failures.asSharedFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun installedFor(language: String?): InstalledVoice? {
        val voice = catalogVoiceFor(language)?.takeIf { it.id in _installed.value } ?: return null
        return runCatching { InstalledVoice(voice, File(root, voice.id)) }.getOrNull()
    }

    fun download(voice: CatalogVoice) {
        if (voice.id in _installed.value || jobs[voice.id]?.isActive == true) return
        _downloads.update { it + (voice.id to VoiceDownload.Downloading(0f)) }
        jobs[voice.id] = scope.launch {
            try {
                install(voice)
                _installed.update { it + voice.id }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.w(e, "Voice download failed: %s", voice.id)
                    _failures.tryEmit(voice)
                }
            } finally {
                _downloads.update { it - voice.id }
                jobs.remove(voice.id)
            }
        }
    }

    fun cancel(voice: CatalogVoice) {
        jobs[voice.id]?.cancel()
    }

    fun delete(voice: CatalogVoice) {
        cancel(voice)
        File(root, voice.id).deleteRecursively()
        _installed.update { it - voice.id }
    }

    private suspend fun install(voice: CatalogVoice) = withContext(Dispatchers.IO) {
        val archive = File(cache, "voice-${voice.id}.tar.bz2")
        val unpacking = File(root, "${voice.id}.partial")
        try {
            fetch(voice, archive)
            _downloads.update { it + (voice.id to VoiceDownload.Unpacking) }
            unpacking.deleteRecursively()
            unpack(archive, unpacking)
            check(unpacking.listFiles { f -> f.extension == "onnx" }?.size == 1) { "No model in ${voice.id}" }
            val target = File(root, voice.id)
            target.deleteRecursively()
            if (!unpacking.renameTo(target)) throw IOException("Couldn't move ${voice.id} into place")
        } finally {
            archive.delete()
            unpacking.deleteRecursively()
        }
    }

    private suspend fun fetch(voice: CatalogVoice, into: File) {
        http.newCall(Request.Builder().url(voice.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${voice.url}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                into.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var reported = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = total?.let { (done * 100 / it).toInt() } ?: -1
                        if (percent != reported) {
                            reported = percent
                            val fraction = total?.let { done.toFloat() / it }
                            _downloads.update { it + (voice.id to VoiceDownload.Downloading(fraction)) }
                        }
                    }
                }
            }
        }
    }

    /** Unpacks the archive into [dir], dropping its top folder and refusing paths that leave [dir]. */
    private suspend fun unpack(archive: File, dir: File) {
        val base = dir.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = tar.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "").takeIf { it.isNotEmpty() } ?: continue
                val target = File(base, relative).canonicalFile
                if (!target.path.startsWith(base.path + File.separator)) throw IOException("Unsafe path in voice: ${entry.name}")
                if (entry.isDirectory) {
                    target.mkdirs()
                } else if (entry.isFile) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    private fun scanInstalled(): Set<String> =
        VoiceCatalog.map { it.id }.filter { id -> File(root, id).listFiles { f -> f.extension == "onnx" }?.isNotEmpty() == true }.toSet()
}
