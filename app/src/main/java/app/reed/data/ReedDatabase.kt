package app.reed.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT books.*, (SELECT COUNT(*) FROM notes WHERE notes.bookId = books.id) AS noteCount
        FROM books
        """,
    )
    fun observeAll(): Flow<List<BookWithCount>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<Book?>

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("UPDATE books SET lastLocator = :locator, progression = :progression WHERE id = :id")
    suspend fun updatePosition(id: Long, locator: String, progression: Double)

    @Query("UPDATE books SET lastOpenedAt = :at WHERE id = :id")
    suspend fun markOpened(id: Long, at: Long)

    @Query("UPDATE books SET isPrivate = :isPrivate WHERE id = :id")
    suspend fun setPrivate(id: Long, isPrivate: Boolean)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY progression ASC, createdAt ASC")
    fun observeForBook(bookId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}

@Database(entities = [Book::class, Note::class], version = 1, exportSchema = true)
abstract class ReedDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun notes(): NoteDao

    companion object {
        fun create(context: Context): ReedDatabase =
            Room.databaseBuilder(context, ReedDatabase::class.java, "reed.db").build()
    }
}
