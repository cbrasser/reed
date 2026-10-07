package app.reed.sync

import app.reed.data.Book
import app.reed.data.Note
import app.reed.data.NoteKind
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * One book's notes in the "reed-notes" format (version 1) that the home app
 * reads; see home's docs/reed-format.md. Plain Kotlin so it can be tested on
 * the JVM.
 */
object NotesJson {

    /** A book's id that stays the same for as long as the book is in Reed: the name of its stored copy. */
    fun bookId(book: Book): String = File(book.filePath).nameWithoutExtension

    /**
     * The export for one book. The time stamp is the latest note change, not
     * "now", so the same notes always give the same file and unchanged books
     * aren't sent again.
     */
    fun book(book: Book, notes: List<Note>, deleted: Map<String, Long> = emptyMap()): String {
        val changed = maxOf(notes.maxOfOrNull { it.updatedAt } ?: book.addedAt, deleted.values.maxOrNull() ?: 0)
        return buildString {
            append("{\n")
            append("  \"format\": \"reed-notes\",\n")
            append("  \"version\": 1,\n")
            append("  \"exportedAt\": ").append(str(iso(changed))).append(",\n")
            append("  \"books\": [{\n")
            append("    \"id\": ").append(str(bookId(book))).append(",\n")
            append("    \"title\": ").append(str(book.title)).append(",\n")
            append("    \"author\": ").append(str(book.author)).append(",\n")
            append("    \"language\": ").append(str(book.language)).append(",\n")
            append("    \"private\": ").append(book.isPrivate).append(",\n")
            append("    \"notes\": [")
            notes.sortedWith(compareBy({ it.progression }, { it.createdAt })).forEachIndexed { i, n ->
                append(if (i == 0) "\n" else ",\n")
                append("      {")
                append("\"id\": ").append(str(n.id.toString())).append(", ")
                append("\"kind\": ").append(str(if (n.kind == NoteKind.PAGE) "page" else "passage")).append(", ")
                append("\"passage\": ").append(str(n.passage)).append(", ")
                append("\"text\": ").append(str(n.text)).append(", ")
                append("\"chapter\": ").append(str(n.chapter)).append(", ")
                append("\"progression\": ").append(if (n.progression.isFinite()) n.progression else 0.0).append(", ")
                append("\"page\": ").append(n.page?.toString() ?: "null").append(", ")
                append("\"createdAt\": ").append(str(iso(n.createdAt))).append(", ")
                append("\"updatedAt\": ").append(str(iso(n.updatedAt))).append(", ")
                append("\"locator\": ").append(str(n.locator))
                append("}")
            }
            append(if (notes.isEmpty()) "]" else "\n    ]")
            if (deleted.isNotEmpty()) {
                // Notes deleted here after they were sent, so home can delete them too.
                append(",\n    \"deletedNotes\": [")
                deleted.entries.sortedBy { it.key }.forEachIndexed { i, (id, at) ->
                    append(if (i == 0) "\n" else ",\n")
                    append("      {\"id\": ").append(str(id)).append(", \"deletedAt\": ").append(str(iso(at))).append("}")
                }
                append("\n    ]")
            }
            append("\n  }]\n")
            append("}\n")
        }
    }

    fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    /** A JSON string literal, or null. */
    fun str(s: String?): String {
        if (s == null) return "null"
        val b = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when {
                c == '"' -> b.append("\\\"")
                c == '\\' -> b.append("\\\\")
                c == '\n' -> b.append("\\n")
                c == '\r' -> b.append("\\r")
                c == '\t' -> b.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> b.append("\\u%04x".format(c.code))
                else -> b.append(c)
            }
        }
        return b.append('"').toString()
    }
}
