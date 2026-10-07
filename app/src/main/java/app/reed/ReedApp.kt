package app.reed

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.Context
import app.reed.data.Library
import app.reed.data.ReedDatabase
import app.reed.data.SettingsStore
import app.reed.privacy.PrivacyLock
import timber.log.Timber

class ReedApp : Application() {
    lateinit var library: Library
        private set
    lateinit var settings: SettingsStore
        private set
    val privacyLock = PrivacyLock()

    override fun onCreate() {
        super.onCreate()
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) Timber.plant(Timber.DebugTree())
        library = Library(this, ReedDatabase.create(this))
        settings = SettingsStore(this)
        privacyLock.install()
    }
}

val Context.reed: ReedApp get() = applicationContext as ReedApp
