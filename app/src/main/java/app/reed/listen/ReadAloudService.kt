package app.reed.listen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.reed.R
import app.reed.reed
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps [ReadAloud] playing with the screen off or the reader closed, with controls in the
 * notification and on the lock screen. Stops itself when reading aloud stops.
 */
@OptIn(UnstableApi::class)
class ReadAloudService : MediaSessionService() {

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.read_aloud_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )
        scope.launch {
            reed.readAloud.mediaSession.collect { session ->
                if (session != null) addSession(session) else stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        reed.readAloud.mediaSession.value

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // A notification left over from a stopped process can start the service with nothing to
        // play. Android still expects it in the foreground, so it shows briefly and stops.
        if (reed.readAloud.mediaSession.value == null) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.read_aloud_channel), NotificationManager.IMPORTANCE_LOW),
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.read_aloud_channel))
                .build()
            startForeground(STALE_NOTIFICATION_ID, notification)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /** Swiping Reed away keeps a book that's playing going, like any audio app; a paused one stops. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (reed.readAloud.state.value?.playing != true) {
            reed.readAloud.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "read-aloud"
        const val STALE_NOTIFICATION_ID = 0x5eef
    }
}
