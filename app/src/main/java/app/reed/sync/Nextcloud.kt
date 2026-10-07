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
import okhttp3.RequestBody.Companion.asRequestBody
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
    /** Files directly in a folder: name → ETag. Empty when the folder isn't there. */
    suspend fun list(dir: String): Map<String, String>
    /** A text file, or null when it isn't there. */
    suspend fun getText(path: String): String?
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

    override suspend fun list(dir: String): Map<String, String> = withContext(Dispatchers.IO) {
        val u = url(dir).newBuilder().addPathSegment("").build()
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getetag/></d:prop></d:propfind>"""
            .toRequestBody("application/xml; charset=utf-8".toMediaType())
        client.newCall(request(u).header("Depth", "1").method("PROPFIND", body).build()).execute().use { res ->
            if (res.code == 404) return@withContext emptyMap()
            res.check("Listing $dir")
            parseListing(res.body.string(), u.encodedPath)
        }
    }

    override suspend fun getText(path: String): String? = withContext(Dispatchers.IO) {
        client.newCall(request(url(path)).build()).execute().use { res ->
            if (res.code == 404) return@withContext null
            res.check("Reading $path")
            res.body.string()
        }
    }

    /** Send a book file, streaming it. */
    suspend fun putFile(path: String, file: java.io.File) = withContext(Dispatchers.IO) {
        val type = "application/octet-stream".toMediaType()
        fun call() = client.newCall(request(url(path)).put(file.asRequestBody(type)).build()).execute()
        var res = call()
        if (res.code == 409 || res.code == 404) {
            res.close()
            mkdirs(path.substringBeforeLast('/', ""))
            res = call()
        }
        res.check("Sending $path").close()
    }

    /** Fetch a file to `to`, streaming it. */
    suspend fun download(path: String, to: java.io.File) = withContext(Dispatchers.IO) {
        client.newCall(request(url(path)).build()).execute().use { res ->
            res.check("Fetching $path")
            to.outputStream().use { out -> res.body.byteStream().copyTo(out) }
        }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        client.newCall(request(url(path)).delete().build()).execute().use { res ->
            if (res.code == 404) return@use
            res.check("Removing $path")
        }
    }
}

/** File names and ETags from a PROPFIND (Depth 1) answer; the folder itself and subfolders are left out. */
fun parseListing(xml: String, folderPath: String): Map<String, String> {
    val f = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val doc = f.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
    val dav = "DAV:"
    val out = mutableMapOf<String, String>()
    val responses = doc.getElementsByTagNameNS(dav, "response")
    for (i in 0 until responses.length) {
        val r = responses.item(i) as org.w3c.dom.Element
        val href = r.getElementsByTagNameNS(dav, "href").item(0)?.textContent?.trim() ?: continue
        val path = java.net.URI(if (href.startsWith("http")) href else "http://x$href").path
        if (path.trimEnd('/') == java.net.URI("http://x$folderPath").path.trimEnd('/')) continue
        if (r.getElementsByTagNameNS(dav, "collection").length > 0) continue
        val etag = r.getElementsByTagNameNS(dav, "getetag").item(0)?.textContent?.trim()?.removePrefix("W/")?.trim('"') ?: ""
        out[path.trimEnd('/').substringAfterLast('/')] = etag
    }
    return out
}
