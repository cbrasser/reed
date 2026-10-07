package app.reed.sync

import app.reed.data.Book
import app.reed.data.Note
import org.json.JSONObject
import java.time.Instant

/**
 * The parts of syncing that only decide, so they can be tested on the JVM:
 * applying edits made in the home app, remembering notes deleted here, and
 * which book files go where. See home's docs/reed-format.md.
 */

/** Edits and deletions the home app made to one book's notes (a "reed-notes-edits" file). */
data class HomeEdits(
    val book: String,
    val edits: List<Edit>,
    val deleted: List<Deleted>,
) {
    data class Edit(val id: String, val text: String, val updatedAt: Long)
    data class Deleted(val id: String, val deletedAt: Long)

    companion object {
        fun parse(text: String): HomeEdits? = runCatching {
            val j = JSONObject(text)
            if (j.optString("format") != "reed-notes-edits" || j.optInt("version", 0) > 1) return null
            val notes = j.optJSONArray("notes")
            val gone = j.optJSONArray("deleted")
            HomeEdits(
                book = j.getString("book"),
                edits = (0 until (notes?.length() ?: 0)).map { i ->
                    val n = notes!!.getJSONObject(i)
                    Edit(n.getString("id"), n.getString("text"), Instant.parse(n.getString("updatedAt")).toEpochMilli())
                },
                deleted = (0 until (gone?.length() ?: 0)).map { i ->
                    val d = gone!!.getJSONObject(i)
                    Deleted(d.getString("id"), Instant.parse(d.getString("deletedAt")).toEpochMilli())
                },
            )
        }.getOrNull()
    }
}

/** What to change here: notes to rewrite with home's text, notes to delete. */
data class ApplyPlan(val update: List<Note>, val delete: List<Note>)

/**
 * The later change wins. An applied edit keeps home's time, so Reed's next
 * export carries the same text with the same time and nothing bounces back.
 */
fun planHomeEdits(notes: List<Note>, home: HomeEdits): ApplyPlan {
    val byId = notes.associateBy { it.id.toString() }
    val deletes = home.deleted.mapNotNull { d -> byId[d.id]?.takeIf { d.deletedAt > it.updatedAt } }
    val gone = deletes.map { it.id }.toSet()
    val updates = home.edits.mapNotNull { e ->
        val n = byId[e.id] ?: return@mapNotNull null
        if (n.id in gone || e.updatedAt <= n.updatedAt || e.text.trim() == n.text.trim()) null
        else n.copy(text = e.text.trim(), updatedAt = e.updatedAt)
    }
    return ApplyPlan(updates, deletes)
}

private const val KEEP_TOMBSTONES_MS = 90L * 24 * 60 * 60 * 1000

/**
 * Notes deleted here since the last send become tombstones (note id → when),
 * so home can delete them too. Old tombstones are dropped after 90 days.
 */
fun tombstones(
    sentIds: Map<String, Set<String>>,
    nowIds: Map<String, Set<String>>,
    old: Map<String, Map<String, Long>>,
    now: Long,
): Map<String, Map<String, Long>> {
    val out = mutableMapOf<String, Map<String, Long>>()
    for (book in sentIds.keys + nowIds.keys + old.keys) {
        val current = nowIds[book].orEmpty()
        val merged = old[book].orEmpty().toMutableMap()
        for (id in sentIds[book].orEmpty() - current) merged.putIfAbsent(id, now)
        // A note that is back (re-created with the same id) isn't deleted.
        merged.keys.removeAll(current)
        merged.entries.removeIf { now - it.value > KEEP_TOMBSTONES_MS }
        if (merged.isNotEmpty()) out[book] = merged
    }
    return out
}

/** Book files on the user's server: `<folder>/books/<book id>.<epub|pdf>` with `<book id>.json` beside it. */
data class BookPlan(
    /** Local books to send (file and metadata). */
    val upload: List<Book>,
    /** Book ids whose files to remove there (the book was removed here). */
    val removeRemote: List<String>,
    /** Book ids to fetch (on the server, not here, never sent from here). */
    val download: List<String>,
    /** Book ids removed on another device: kept here, but no longer sent. */
    val goneElsewhere: List<String>,
)

fun planBooks(
    local: List<Book>,
    includePrivate: Boolean,
    remoteNames: Set<String>,
    uploaded: Set<String>,
    goneElsewhere: Set<String>,
): BookPlan {
    val localIds = local.associateBy { NotesJson.bookId(it) }
    val remoteIds = remoteNames.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.toSet()
    val eligible = local.filter { includePrivate || !it.isPrivate }
    return BookPlan(
        upload = eligible.filter { NotesJson.bookId(it) !in uploaded && NotesJson.bookId(it) !in goneElsewhere },
        // Removed here, or made private while private books aren't sent.
        removeRemote = uploaded.filter { it !in localIds || (!includePrivate && localIds[it]!!.isPrivate) }.filter { it in remoteIds },
        download = (remoteIds - localIds.keys - uploaded - goneElsewhere).toList().sorted(),
        goneElsewhere = uploaded.filter { it in localIds && it !in remoteIds && (includePrivate || !localIds[it]!!.isPrivate) },
    )
}

/** The metadata file beside a book on the server. */
fun bookMeta(book: Book): String = buildString {
    append("{\n")
    append("  \"format\": \"reed-book\",\n  \"version\": 1,\n")
    append("  \"id\": ").append(NotesJson.str(NotesJson.bookId(book))).append(",\n")
    append("  \"title\": ").append(NotesJson.str(book.title)).append(",\n")
    append("  \"author\": ").append(NotesJson.str(book.author)).append(",\n")
    append("  \"file\": ").append(NotesJson.str(java.io.File(book.filePath).name)).append(",\n")
    append("  \"private\": ").append(book.isPrivate).append(",\n")
    append("  \"addedAt\": ").append(NotesJson.str(Instant.ofEpochMilli(book.addedAt).toString())).append("\n")
    append("}\n")
}
