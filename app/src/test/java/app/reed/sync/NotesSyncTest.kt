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
