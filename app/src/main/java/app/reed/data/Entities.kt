package app.reed.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookFormat { EPUB, PDF }

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val format: BookFormat,
    /** Absolute path of the copy inside app storage. */
    val filePath: String,
    val coverPath: String?,
    /** BCP 47 language tag from the publication metadata, if any. */
    val language: String?,
    /** Serialized Readium locator of the last reading position. */
    val lastLocator: String? = null,
    /** 0.0 – 1.0 */
    val progression: Double = 0.0,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val isPrivate: Boolean = false,
)

enum class NoteKind { PASSAGE, PAGE }

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: NoteKind,
    /** Serialized Readium locator of the passage or page. */
    val locator: String,
    /** The selected passage, verbatim. Null for page notes. */
    val passage: String?,
    val text: String,
    /** Chapter title at the time the note was taken. */
    val chapter: String?,
    /** 0.0 – 1.0, used for ordering and the location label. */
    val progression: Double,
    /** Page number for PDFs. */
    val page: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A book row plus its note count, for the library. */
data class BookWithCount(
    val id: Long,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String,
    val coverPath: String?,
    val language: String?,
    val lastLocator: String?,
    val progression: Double,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val isPrivate: Boolean,
    val noteCount: Int,
)
