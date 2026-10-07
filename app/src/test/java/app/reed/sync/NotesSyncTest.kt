package app.reed.sync

import app.reed.data.Book
import app.reed.data.BookFormat
import app.reed.data.Note
import app.reed.data.NoteKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class NotesSyncTest {
    private val book = Book(
        id = 7, title = "Der Zauberberg", author = "Thomas Mann", format = BookFormat.EPUB,
        filePath = "/data/books/3f2a9c1e-uuid.epub", coverPath = null, language = "de", addedAt = 1_000,
    )
    private fun note(id: Long, progression: Double, text: String, passage: String? = "Ein \"Zitat\"\nmit Zeile", updated: Long = 5_000) = Note(
        id = id, bookId = 7, kind = if (passage == null) NoteKind.PAGE else NoteKind.PASSAGE, locator = """{"href":"ch1.xhtml"}""",
        passage = passage, text = text, chapter = "Vorsatz", progression = progression, page = null, createdAt = 4_000, updatedAt = updated,
    )

    @Test
    fun `a book reads as a reed-notes v1 file`() {
        val j = JSONObject(NotesJson.book(book, listOf(note(2, 0.5, "später"), note(1, 0.1, "Erzähler\tals Figur"))))
        assertEquals("reed-notes", j.getString("format"))
        assertEquals(1, j.getInt("version"))
        val b = j.getJSONArray("books").getJSONObject(0)
        assertEquals("3f2a9c1e-uuid", b.getString("id"))
        assertEquals(false, b.getBoolean("private"))
        val notes = b.getJSONArray("notes")
        assertEquals("1", notes.getJSONObject(0).getString("id"))
        assertEquals("Erzähler\tals Figur", notes.getJSONObject(0).getString("text"))
        assertEquals("Ein \"Zitat\"\nmit Zeile", notes.getJSONObject(0).getString("passage"))
        assertEquals("""{"href":"ch1.xhtml"}""", notes.getJSONObject(0).getString("locator"))
        assertEquals("1970-01-01T00:00:05Z", j.getString("exportedAt"))
    }

    @Test
    fun `page notes and empty books are valid too`() {
        val j = JSONObject(NotesJson.book(book.copy(author = null), listOf(note(3, 0.2, "page", passage = null))))
        val n = j.getJSONArray("books").getJSONObject(0).getJSONArray("notes").getJSONObject(0)
        assertEquals("page", n.getString("kind"))
        assertEquals(true, n.isNull("passage"))
        assertEquals(0, JSONObject(NotesJson.book(book, emptyList())).getJSONArray("books").getJSONObject(0).getJSONArray("notes").length())
    }

    @Test
    fun `the same notes give the same file`() {
        val a = NotesJson.book(book, listOf(note(1, 0.1, "x")))
        assertEquals(a, NotesJson.book(book, listOf(note(1, 0.1, "x"))))
    }

    private class Fake : RemoteFolder {
        val files = mutableMapOf<String, String>()
        val calls = mutableListOf<String>()
        override suspend fun mkdirs(path: String) { calls += "mkdir $path" }
        override suspend fun put(path: String, body: String) { calls += "put $path"; files[path] = body }
        override suspend fun delete(path: String) { calls += "delete $path"; files.remove(path) }
        override suspend fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") }.associate { it.removePrefix("$dir/") to "e" }
        override suspend fun getText(path: String) = files[path]
    }

    @Test
    fun `only changed books are sent and removed books are deleted`() = runBlocking {
        val fake = Fake()
        val up = NotesUpload(fake, "Reed")
        val a = BookFile("a", "1")
        val b = BookFile("b", "1")
        var r = up.run(listOf(a, b), emptyMap())
        assertEquals(2, r.uploaded)
        assertEquals(listOf("mkdir Reed/notes", "put Reed/notes/a.json", "put Reed/notes/b.json"), fake.calls)
        fake.calls.clear()
        r = up.run(listOf(a, BookFile("b", "2")), r.sent)
        assertEquals(listOf("put Reed/notes/b.json"), fake.calls)
        fake.calls.clear()
        r = up.run(listOf(a), r.sent)
        assertEquals(listOf("delete Reed/notes/b.json"), fake.calls)
        assertEquals(setOf("a"), r.sent.keys)
        assertNull(fake.files["Reed/notes/b.json"])
    }

    /** Against a real WebDAV server: REED_TEST_WEBDAV=http://127.0.0.1:8765/ (user u, password testpass). */
    @Test
    fun `sending to a real WebDAV server`() = runBlocking {
        val server = System.getenv("REED_TEST_WEBDAV")
        assumeTrue(server != null)
        val dav = Dav(server!!, "u", "testpass")
        val folder = "reed-e2e-${System.nanoTime()}/Reed Bücher"
        val up = NotesUpload(dav, folder)
        val file = BookFile("3f2a9c1e-uuid", NotesJson.book(book, listOf(note(1, 0.1, "Ü"))))
        var r = up.run(listOf(file), emptyMap())
        assertEquals(1, r.uploaded)
        val got = http.newCall(okhttp3.Request.Builder().url(dav.url(up.path(file.id))).header("Authorization", okhttp3.Credentials.basic("u", "testpass")).build()).execute()
        assertEquals(200, got.code)
        assertEquals(file.json, got.body.string())
        r = up.run(emptyList(), r.sent)
        assertEquals(1, r.removed)
        val gone = http.newCall(okhttp3.Request.Builder().url(dav.url(up.path(file.id))).header("Authorization", okhttp3.Credentials.basic("u", "testpass")).build()).execute()
        assertEquals(404, gone.code)
    }
}

class TwoWayTest {
    private val book = Book(
        id = 7, title = "Der Zauberberg", author = "Thomas Mann", format = BookFormat.EPUB,
        filePath = "/data/books/zb-uuid.epub", coverPath = null, language = "de", addedAt = 1_000,
    )
    private fun n(id: Long, text: String, updated: Long) = Note(
        id = id, bookId = 7, kind = NoteKind.PASSAGE, locator = "{}", passage = "p", text = text,
        chapter = null, progression = 0.1, page = null, createdAt = 0, updatedAt = updated,
    )

    @Test
    fun `home's edits apply when they are later, deletions too`() {
        val edits = HomeEdits.parse(
            """{"format":"reed-notes-edits","version":1,"book":"zb-uuid",
               "notes":[{"id":"1","text":"from home ","updatedAt":"1970-01-01T00:00:10Z"},{"id":"2","text":"stale","updatedAt":"1970-01-01T00:00:01Z"}],
               "deleted":[{"id":"3","deletedAt":"1970-01-01T00:00:10Z"},{"id":"4","deletedAt":"1970-01-01T00:00:01Z"}]}""",
        )!!
        val plan = planHomeEdits(listOf(n(1, "old", 5_000), n(2, "newer here", 5_000), n(3, "x", 5_000), n(4, "y", 5_000)), edits)
        assertEquals(listOf("from home"), plan.update.map { it.text })
        assertEquals(10_000L, plan.update.single().updatedAt)
        assertEquals(listOf(3L), plan.delete.map { it.id })
        // Applying the same file again changes nothing.
        assertEquals(0, planHomeEdits(listOf(plan.update.single()), edits).update.size)
        assertNull(HomeEdits.parse("""{"format":"other"}"""))
    }

    @Test
    fun `deleted notes become tombstones that travel in the export`() {
        val gone = tombstones(mapOf("b" to setOf("1", "2")), mapOf("b" to setOf("1")), emptyMap(), 50_000)
        assertEquals(mapOf("b" to mapOf("2" to 50_000L)), gone)
        // Kept with their first time, dropped when old.
        assertEquals(gone, tombstones(mapOf("b" to setOf("1")), mapOf("b" to setOf("1")), gone, 60_000))
        assertEquals(emptyMap<String, Map<String, Long>>(), tombstones(emptyMap(), mapOf("b" to setOf("1")), gone, 50_000 + 91L * 86_400_000))
        val j = JSONObject(NotesJson.book(book, listOf(n(1, "a", 5_000)), gone["b"]!!))
        val d = j.getJSONArray("books").getJSONObject(0).getJSONArray("deletedNotes").getJSONObject(0)
        assertEquals("2", d.getString("id"))
        assertEquals("1970-01-01T00:00:50Z", d.getString("deletedAt"))
    }

    @Test
    fun `book files go up, come down, and removals are respected`() {
        val a = book.copy(id = 1, filePath = "/b/a.epub")
        val p = book.copy(id = 2, filePath = "/b/p.epub", isPrivate = true)
        val first = planBooks(listOf(a, p), includePrivate = false, remoteNames = setOf("x.epub", "x.json"), uploaded = emptySet(), goneElsewhere = emptySet())
        assertEquals(listOf("a"), first.upload.map { NotesJson.bookId(it) })
        assertEquals(listOf("x"), first.download)
        // a was sent; now it's gone from the server: removed on another phone.
        val later = planBooks(listOf(a), false, setOf("x.json"), uploaded = setOf("a", "x"), goneElsewhere = emptySet())
        assertEquals(listOf("a"), later.goneElsewhere)
        assertEquals(emptyList<Book>(), later.upload)
        // Removed here: its copy there goes too.
        val removed = planBooks(emptyList(), false, setOf("a.epub", "a.json"), uploaded = setOf("a"), goneElsewhere = emptySet())
        assertEquals(listOf("a"), removed.removeRemote)
        assertEquals(emptyList<String>(), removed.download)
    }

    @Test
    fun `a WebDAV listing reads as file names and etags`() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/remote.php/dav/files/u/Reed/home/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
            <d:response><d:href>/remote.php/dav/files/u/Reed/home/zb%20uuid.json</d:href><d:propstat><d:prop><d:resourcetype/><d:getetag>"e1"</d:getetag></d:prop></d:propstat></d:response>
            <d:response><d:href>/remote.php/dav/files/u/Reed/home/sub/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
            </d:multistatus>"""
        assertEquals(mapOf("zb uuid.json" to "e1"), parseListing(xml, "/remote.php/dav/files/u/Reed/home/"))
    }

    @Test
    fun `listing, reading and files against a real WebDAV server`() = runBlocking {
        val server = System.getenv("REED_TEST_WEBDAV")
        org.junit.Assume.assumeTrue(server != null)
        val dav = Dav(server!!, "u", "testpass")
        val dir = "reed-e2e-${System.nanoTime()}/Reed/books"
        assertEquals(emptyMap<String, String>(), dav.list(dir))
        val f = java.io.File.createTempFile("book", ".epub").apply { writeBytes(ByteArray(300_000) { (it % 251).toByte() }) }
        dav.putFile("$dir/a.epub", f)
        dav.put("$dir/a.json", "{}")
        assertEquals(setOf("a.epub", "a.json"), dav.list(dir).keys)
        assertEquals("{}", dav.getText("$dir/a.json"))
        assertNull(dav.getText("$dir/missing.json"))
        val back = java.io.File.createTempFile("back", ".epub")
        dav.download("$dir/a.epub", back)
        assertEquals(f.readBytes().toList(), back.readBytes().toList())
    }
}
