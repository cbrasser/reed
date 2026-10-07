package app.reed

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.Context
import app.reed.data.Library
import app.reed.data.ReedDatabase
import app.reed.data.SettingsStore
import app.reed.privacy.PrivacyLock
import app.reed.sync.NotesSync
import app.reed.sync.SyncStore
import timber.log.Timber

class ReedApp : Application() {
    lateinit var library: Library
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var sync: SyncStore
        private set
    val privacyLock = PrivacyLock()

    override fun onCreate() {
        super.onCreate()
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) Timber.plant(Timber.DebugTree())
        sync = SyncStore(this)
        library = Library(this, ReedDatabase.create(this)) { NotesSync.schedule(this) }
        settings = SettingsStore(this)
        // Catch up on anything that didn't get sent last time (does nothing when sending is off).
        NotesSync.schedule(this, delaySeconds = 5)
        privacyLock.install()
    }
}

val Context.reed: ReedApp get() = applicationContext as ReedApp
