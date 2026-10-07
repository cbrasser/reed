package app.reed.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** What the "Send notes" screen shows and the worker needs. The app password is never in here. */
data class SyncSettings(
    val connected: Boolean = false,
    val server: String = "",
    val loginName: String = "",
    val userId: String = "",
    val enabled: Boolean = false,
    val folder: String = DEFAULT_FOLDER,
    val includePrivate: Boolean = false,
    /** Also keep the book files themselves on Nextcloud, and fetch books this phone doesn't have. */
    val syncBooks: Boolean = false,
    val bookFileCount: Int = 0,
    val lastSentAt: Long? = null,
    val lastError: String? = null,
    val bookCount: Int = 0,
)

const val DEFAULT_FOLDER = "Reed"

private val Context.syncStore by preferencesDataStore("sync")

class SyncStore(private val context: Context) {
    private object Keys {
        val server = stringPreferencesKey("server")
        val loginName = stringPreferencesKey("loginName")
        val userId = stringPreferencesKey("userId")
        val password = stringPreferencesKey("appPasswordSealed")
        val enabled = booleanPreferencesKey("enabled")
        val folder = stringPreferencesKey("folder")
        val includePrivate = booleanPreferencesKey("includePrivate")
        val lastSentAt = longPreferencesKey("lastSentAt")
        val lastError = stringPreferencesKey("lastError")
        val sent = stringPreferencesKey("sent")
        val sentIds = stringPreferencesKey("sentNoteIds")
        val tombstones = stringPreferencesKey("tombstones")
        val homeEtags = stringPreferencesKey("homeEtags")
        val syncBooks = booleanPreferencesKey("syncBooks")
        val uploaded = stringPreferencesKey("uploadedBooks")
        val goneElsewhere = stringPreferencesKey("goneElsewhere")
    }

    val settings: Flow<SyncSettings> = context.syncStore.data.map { p ->
        SyncSettings(
            connected = p[Keys.password] != null,
            server = p[Keys.server].orEmpty(),
            loginName = p[Keys.loginName].orEmpty(),
            userId = p[Keys.userId].orEmpty(),
            enabled = p[Keys.enabled] ?: false,
            folder = p[Keys.folder] ?: DEFAULT_FOLDER,
            includePrivate = p[Keys.includePrivate] ?: false,
            lastSentAt = p[Keys.lastSentAt],
            lastError = p[Keys.lastError],
            bookCount = p[Keys.sent]?.let { runCatching { JSONObject(it).length() }.getOrNull() } ?: 0,
            syncBooks = p[Keys.syncBooks] ?: false,
            bookFileCount = p[Keys.uploaded]?.let { runCatching { org.json.JSONArray(it).length() }.getOrNull() } ?: 0,
        )
    }

    suspend fun current() = settings.first()

    suspend fun connect(account: Account, userId: String) {
        val sealed = Seal.seal(account.appPassword)
        context.syncStore.edit {
            it[Keys.server] = account.server
            it[Keys.loginName] = account.loginName
            it[Keys.userId] = userId
            it[Keys.password] = sealed
            it[Keys.enabled] = true
            it.remove(Keys.lastError)
            it.remove(Keys.lastSentAt)
            listOf(Keys.sent, Keys.sentIds, Keys.tombstones, Keys.homeEtags, Keys.uploaded, Keys.goneElsewhere).forEach { k -> it.remove(k) }
        }
    }

    /** The signed-in account, with its app password unsealed; null when not connected. */
    suspend fun account(): Account? {
        val p = context.syncStore.data.first()
        val sealed = p[Keys.password] ?: return null
        val password = Seal.open(sealed) ?: return null
        return Account(p[Keys.server].orEmpty(), p[Keys.loginName].orEmpty(), password)
    }

    suspend fun disconnect() {
        context.syncStore.edit { it.clear() }
    }

    suspend fun setEnabled(on: Boolean) = context.syncStore.edit { it[Keys.enabled] = on }

    suspend fun setIncludePrivate(on: Boolean) = context.syncStore.edit { it[Keys.includePrivate] = on }

    suspend fun setSyncBooks(on: Boolean) = context.syncStore.edit { it[Keys.syncBooks] = on }

    // ---- bookkeeping for two-way notes and book files ----

    private suspend fun raw(key: androidx.datastore.preferences.core.Preferences.Key<String>) = context.syncStore.data.first()[key]

    private fun obj(s: String?) = runCatching { JSONObject(s ?: "{}") }.getOrDefault(JSONObject())
    private fun set(s: String?): Set<String> = runCatching {
        val a = org.json.JSONArray(s ?: "[]")
        (0 until a.length()).map { a.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    suspend fun sentIds(): Map<String, Set<String>> {
        val j = obj(raw(Keys.sentIds))
        return j.keys().asSequence().associateWith { k -> j.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }
    }

    suspend fun tombstones(): Map<String, Map<String, Long>> {
        val j = obj(raw(Keys.tombstones))
        return j.keys().asSequence().associateWith { k -> j.getJSONObject(k).let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } }
    }

    suspend fun recordNotes(sentIds: Map<String, Set<String>>, tombstones: Map<String, Map<String, Long>>) = context.syncStore.edit {
        it[Keys.sentIds] = JSONObject(sentIds.mapValues { (_, v) -> org.json.JSONArray(v.toList()) }).toString()
        it[Keys.tombstones] = JSONObject(tombstones.mapValues { (_, v) -> JSONObject(v) }).toString()
    }

    suspend fun homeEtags(): Map<String, String> = obj(raw(Keys.homeEtags)).let { j -> j.keys().asSequence().associateWith { j.getString(it) } }

    suspend fun recordHomeEtag(file: String, etag: String) = context.syncStore.edit {
        it[Keys.homeEtags] = obj(it[Keys.homeEtags]).put(file, etag).toString()
    }

    suspend fun uploaded(): Set<String> = set(raw(Keys.uploaded))
    suspend fun goneElsewhere(): Set<String> = set(raw(Keys.goneElsewhere))

    suspend fun recordBooks(uploaded: Set<String>, goneElsewhere: Set<String>) = context.syncStore.edit {
        it[Keys.uploaded] = org.json.JSONArray(uploaded.toList()).toString()
        it[Keys.goneElsewhere] = org.json.JSONArray(goneElsewhere.toList()).toString()
    }

    /** A new folder starts fresh: everything is sent there again. */
    suspend fun setFolder(folder: String) = context.syncStore.edit {
        val f = folder.trim().trim('/').ifEmpty { DEFAULT_FOLDER }
        if (f != it[Keys.folder]) {
            it[Keys.folder] = f
            listOf(Keys.sent, Keys.sentIds, Keys.tombstones, Keys.homeEtags, Keys.uploaded, Keys.goneElsewhere).forEach { k -> it.remove(k) }
        }
    }

    suspend fun sent(): Map<String, String> {
        val raw = context.syncStore.data.first()[Keys.sent] ?: return emptyMap()
        return runCatching {
            val j = JSONObject(raw)
            j.keys().asSequence().associateWith { j.getString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun recordSent(sent: Map<String, String>, at: Long) = context.syncStore.edit {
        it[Keys.sent] = JSONObject(sent).toString()
        it[Keys.lastSentAt] = at
        it.remove(Keys.lastError)
    }

    suspend fun recordError(message: String?) = context.syncStore.edit {
        if (message == null) it.remove(Keys.lastError) else it[Keys.lastError] = message
    }
}

/** Seals the app password with a key that lives in the Android Keystore and never leaves it. */
private object Seal {
    private const val ALIAS = "reed-sync"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun seal(secret: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val b64 = { b: ByteArray -> Base64.encodeToString(b, Base64.NO_WRAP) }
        return "${b64(c.iv)}:${b64(c.doFinal(secret.toByteArray()))}"
    }

    fun open(sealed: String): String? = runCatching {
        val (iv, data) = sealed.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(data))
    }.getOrNull()
}
