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
        startForeground(notificationId, buildNotification("Starting FTP mirror..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateNotification("Mirroring files to FTP...")
                FtpMirrorService(this@MirrorForegroundService).performMirror()
                updateNotification("Mirror completed")
            } catch (e: Exception) {
                updateNotification("Mirror failed: ${e.message?.take(40) ?: "error"}")
            } finally {
                // Give the user a moment to see the final status
                kotlinx.coroutines.delay(2500)
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FTP Mirror")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
