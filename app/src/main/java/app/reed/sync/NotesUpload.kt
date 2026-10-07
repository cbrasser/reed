package app.reed.sync

/** One book ready to send: its id, the file and the file's hash. */
data class BookFile(val id: String, val json: String) {
    val hash: String = NotesJson.hash(json)
}

data class UploadResult(
    /** Book id → hash of the file now on the server. */
    val sent: Map<String, String>,
    val uploaded: Int,
    val removed: Int,
)

/**
 * Sends each book's notes as `<folder>/notes/<book id>.json`. Reed is the
 * only writer of these files: a book is sent when its file differs from what
 * was last sent, and its file is removed when the book is gone from Reed (or
 * is private and private books aren't sent). The home app only reads them.
 */
class NotesUpload(private val dav: RemoteFolder, folder: String) {
    private val dir = "${folder.trim('/')}/notes"

    fun path(bookId: String) = "$dir/$bookId.json"

    suspend fun run(books: List<BookFile>, sent: Map<String, String>): UploadResult {
        val now = sent.toMutableMap()
        var uploaded = 0
        var removed = 0
        val changed = books.filter { sent[it.id] != it.hash }
        if (changed.isNotEmpty() && sent.isEmpty()) dav.mkdirs(dir)
        for (b in changed) {
            dav.put(path(b.id), b.json)
            now[b.id] = b.hash
            uploaded++
        }
        val keep = books.map { it.id }.toSet()
        for (id in sent.keys - keep) {
            dav.delete(path(id))
            now.remove(id)
            removed++
        }
        return UploadResult(now, uploaded, removed)
    }
}
