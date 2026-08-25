package com.github.ftpmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MirrorForegroundService : Service() {

    private val channelId = "ftp_mirror_channel"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Starting FTP mirror…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateNotification("Connecting and uploading files…")
                val result = FtpMirrorService(this@MirrorForegroundService).performMirror()
                val summary = "Done — uploaded ${result.uploaded}, failed ${result.failed}, skipped ${result.skipped}"
                updateNotification(summary)
            } catch (e: Exception) {
                updateNotification("Mirror failed: ${e.message?.take(50) ?: "error"}")
            } finally {
                kotlinx.coroutines.delay(4000)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FTP Mirror",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background FTP file mirroring"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FTP Mirror")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
