package app.reed.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.reed.reed
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sending notes to the user's Nextcloud, where the home app picks them up.
 * Runs in the background shortly after notes change, whenever the phone is
 * online; it does nothing unless the user turned it on.
 */
object NotesSync {
    private const val WORK = "reed-notes-sync"

    /** Send soon; a later call within the delay replaces this one, so a burst of edits sends once. */
    fun schedule(context: Context, delaySeconds: Long = 20) {
        val request = OneTimeWorkRequestBuilder<NotesSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun sendNow(context: Context) = schedule(context, 0)

    /** One run: send what changed. Throws on network and sign-in problems. */
    suspend fun run(context: Context): UploadResult? {
        val store = context.reed.sync
        val settings = store.current()
        if (!settings.enabled) return null
        val account = store.account() ?: return null
        val books = context.reed.library.everything()
            .filter { (book, _) -> settings.includePrivate || !book.isPrivate }
            .map { (book, notes) -> BookFile(NotesJson.bookId(book), NotesJson.book(book, notes)) }
        val dav = Dav.nextcloud(account, settings.userId)
        val result = NotesUpload(dav, settings.folder).run(books, store.sent())
        store.recordSent(result.sent, System.currentTimeMillis())
        return result
    }
}

class NotesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = applicationContext.reed.sync
        return try {
            val r = NotesSync.run(applicationContext)
            if (r != null) Timber.i("Notes sent: %d up, %d removed", r.uploaded, r.removed)
            Result.success()
        } catch (e: SignInRejected) {
            store.recordError(e.message)
            Result.failure()
        } catch (e: ServerProblem) {
            store.recordError(e.message)
            Result.retry()
        } catch (e: IOException) {
            store.recordError("Couldn't reach your Nextcloud. Trying again when the connection is back.")
            Result.retry()
        }
    }
}
