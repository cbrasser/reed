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

    /** Also look in now and then, for edits made in home and books added elsewhere. */
    fun scheduleRegular(context: Context) {
        val request = androidx.work.PeriodicWorkRequestBuilder<NotesSyncKick>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("$WORK-regular", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * One run: apply what was edited in home, send what changed here (with
     * notes deleted here), then the book files if that's on. Throws on
     * network and sign-in problems.
     */
    suspend fun run(context: Context): UploadResult? {
        val app = context.reed
        val store = app.sync
        val settings = store.current()
        if (!settings.enabled) return null
        val account = store.account() ?: return null
        val dav = Dav.nextcloud(account, settings.userId)
        val folder = settings.folder.trim('/')

        // 1. Edits and deletions made in the home app.
        val homeDir = "$folder/home"
        val seen = store.homeEtags()
        val byId = app.library.everything().associateBy { (book, _) -> NotesJson.bookId(book) }
        for ((name, etag) in dav.list(homeDir)) {
            if (!name.endsWith(".json") || seen[name] == etag) continue
            val edits = dav.getText("$homeDir/$name")?.let(HomeEdits::parse)
            val notes = edits?.let { byId[it.book]?.second }
            if (edits != null && notes != null) {
                val plan = planHomeEdits(notes, edits)
                plan.update.forEach { app.library.applyRemote(update = it) }
                plan.delete.forEach { app.library.applyRemote(delete = it) }
                if (plan.update.isNotEmpty() || plan.delete.isNotEmpty()) Timber.i("From home: %d edited, %d deleted", plan.update.size, plan.delete.size)
            }
            store.recordHomeEtag(name, etag)
        }

        // 2. This phone's notes, with the ones deleted since the last send.
        val everything = app.library.everything()
        val shown = everything.filter { (book, _) -> settings.includePrivate || !book.isPrivate }
        val nowIds = shown.associate { (book, notes) -> NotesJson.bookId(book) to notes.map { it.id.toString() }.toSet() }
        val gone = tombstones(store.sentIds(), nowIds, store.tombstones(), System.currentTimeMillis())
        val files = shown.map { (book, notes) ->
            val id = NotesJson.bookId(book)
            BookFile(id, NotesJson.book(book, notes, gone[id].orEmpty()))
        }
        val result = NotesUpload(dav, folder).run(files, store.sent())
        store.recordSent(result.sent, System.currentTimeMillis())
        store.recordNotes(nowIds, gone.filterKeys { it in nowIds })

        // 3. The books themselves.
        if (settings.syncBooks) syncBooks(context, dav, folder, settings.includePrivate)
        return result
    }

    private suspend fun syncBooks(context: Context, dav: Dav, folder: String, includePrivate: Boolean) {
        val app = context.reed
        val store = app.sync
        val dir = "$folder/books"
        val local = app.library.everything().map { it.first }
        val remote = dav.list(dir)
        val plan = planBooks(local, includePrivate, remote.keys, store.uploaded(), store.goneElsewhere())
        val uploaded = store.uploaded().toMutableSet()
        val goneElsewhere = (store.goneElsewhere() + plan.goneElsewhere).toMutableSet()
        uploaded.removeAll(plan.goneElsewhere.toSet())
        suspend fun save() = store.recordBooks(uploaded, goneElsewhere)

        for (book in plan.upload) {
            val id = NotesJson.bookId(book)
            val file = java.io.File(book.filePath)
            if (!file.exists()) continue
            // The book first, then its card: a card on the server means the book is complete.
            dav.putFile("$dir/${file.name}", file)
            dav.put("$dir/$id.json", bookMeta(book))
            uploaded += id
            save()
        }
        for (id in plan.removeRemote) {
            val name = remote.keys.firstOrNull { it.startsWith("$id.") && !it.endsWith(".json") }
            dav.delete("$dir/$id.json")
            if (name != null) dav.delete("$dir/$name")
            uploaded -= id
            save()
        }
        for (id in plan.download) {
            val meta = dav.getText("$dir/$id.json")?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: continue
            val name = meta.optString("file").takeIf { it.startsWith("$id.") && !it.contains('/') } ?: continue
            val staging = java.io.File(app.library.booksFolder, "$id.tmp")
            try {
                dav.download("$dir/$name", staging)
                val addedAt = runCatching { java.time.Instant.parse(meta.optString("addedAt")).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                val r = app.library.importStaged(staging, name, id, isPrivate = meta.optBoolean("private"), addedAt = addedAt)
                Timber.i("Book from Nextcloud: %s", r)
                // A book this phone can't open isn't fetched again and again.
                if (r is app.reed.data.ImportResult.Failed) goneElsewhere += id else uploaded += id
                save()
            } finally {
                staging.delete()
            }
        }
        save()
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

/** Hourly nudge: queues the one sync job, so two runs never overlap. */
class NotesSyncKick(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        NotesSync.schedule(applicationContext, 0)
        return Result.success()
    }
}
