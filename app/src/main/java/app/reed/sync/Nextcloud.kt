package app.reed.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server refused the sign-in (app password revoked, account gone). */
class SignInRejected(message: String) : IOException(message)

/** The server answered, but not with what Reed needed. */
class ServerProblem(message: String) : IOException(message)

data class Account(val server: String, val loginName: String, val appPassword: String)

val http: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** "cloud.example.org/" → "https://cloud.example.org". */
fun normalizeServer(input: String): String {
    var s = input.trim().trimEnd('/')
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
    return s
}

private fun Response.check(what: String): Response {
    if (code == 401) {
        close()
        throw SignInRejected("Nextcloud refused the sign-in. Sign in again.")
    }
    if (!isSuccessful) {
        close()
        throw ServerProblem("$what: the server answered $code.")
    }
    return this
}

/**
 * Nextcloud's Login Flow v2: the user signs in on their server's own page in
 * the browser and approves Reed, which receives an app password it can be
 * revoked by. Reed never sees the user's password.
 */
object LoginFlow {
    data class Start(val loginUrl: String, val pollEndpoint: String, val token: String)

    suspend fun start(server: String): Start = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${normalizeServer(server)}/index.php/login/v2")
            .header("User-Agent", "Reed (Android)")
            .post(FormBody.Builder().build())
            .build()
        http.newCall(req).execute().check("Starting the sign-in").use { res ->
            val j = JSONObject(res.body.string())
            val poll = j.getJSONObject("poll")
            Start(j.getString("login"), poll.getString("endpoint"), poll.getString("token"))
        }
    }

    /** The account once the user has approved in the browser; null while they haven't yet. */
    suspend fun poll(start: Start): Account? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(start.pollEndpoint)
            .post(FormBody.Builder().add("token", start.token).build())
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return@withContext null
            if (!res.isSuccessful) throw ServerProblem("Signing in: the server answered ${res.code}.")
            val j = JSONObject(res.body.string())
            Account(j.getString("server").trimEnd('/'), j.getString("loginName"), j.getString("appPassword"))
        }
    }

    /** The user id the server files live under (it can differ from the login name). */
    suspend fun userId(account: Account): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${account.server}/ocs/v1.php/cloud/user?format=json")
            .header("OCS-APIRequest", "true")
            .header("Authorization", Credentials.basic(account.loginName, account.appPassword))
            .build()
        http.newCall(req).execute().check("Looking up the account").use { res ->
            JSONObject(res.body.string()).getJSONObject("ocs").getJSONObject("data").getString("id")
        }
    }

    /** Revoke the app password on the server (best effort, when disconnecting). */
    suspend fun revoke(account: Account) = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${account.server}/ocs/v2.php/core/apppassword")
                .header("OCS-APIRequest", "true")
                .header("Authorization", Credentials.basic(account.loginName, account.appPassword))
                .delete()
                .build()
            http.newCall(req).execute().close()
        }
    }
}

/** Where book files go. */
interface RemoteFolder {
    suspend fun mkdirs(path: String)
    suspend fun put(path: String, body: String)
    suspend fun delete(path: String)
}

/** The few WebDAV calls Reed needs, inside one folder the user chose. */
class Dav(root: String, user: String, password: String, private val client: OkHttpClient = http) : RemoteFolder {
    private val root: HttpUrl = (if (root.endsWith("/")) root else "$root/").toHttpUrl()
    private val auth = Credentials.basic(user, password)

    companion object {
        /** A Nextcloud account's files: …/remote.php/dav/files/<user id>/. */
        fun nextcloud(account: Account, userId: String) =
            Dav(
                "${account.server}/remote.php/dav/files/".toHttpUrl().newBuilder().addPathSegment(userId).addPathSegment("").build().toString(),
                account.loginName,
                account.appPassword,
            )
    }

    fun url(path: String): HttpUrl = root.newBuilder().apply {
        path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
    }.build()

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("Authorization", auth)

    /** Create a folder and its missing parents. */
    override suspend fun mkdirs(path: String) = withContext(Dispatchers.IO) {
        var sofar = ""
        for (seg in path.split('/').filter { it.isNotEmpty() }) {
            sofar += "$seg/"
            val u = url(sofar).newBuilder().addPathSegment("").build()
            client.newCall(request(u).method("MKCOL", null).build()).execute().use { res ->
                // 201 created, 405 already there.
                if (res.code == 401) throw SignInRejected("Nextcloud refused the sign-in. Sign in again.")
                if (!res.isSuccessful && res.code != 405) throw ServerProblem("Creating the folder $sofar: the server answered ${res.code}.")
            }
        }
    }

    /** Write a file, creating its folder if the server says it's missing. */
    override suspend fun put(path: String, body: String) = withContext(Dispatchers.IO) {
        val type = "application/json; charset=utf-8".toMediaType()
        fun call() = client.newCall(request(url(path)).put(body.toRequestBody(type)).build()).execute()
        var res = call()
        if (res.code == 409 || res.code == 404) {
            res.close()
            mkdirs(path.substringBeforeLast('/', ""))
            res = call()
        }
        res.check("Sending $path").close()
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        client.newCall(request(url(path)).delete().build()).execute().use { res ->
            if (res.code == 404) return@use
            res.check("Removing $path")
        }
    }
}
