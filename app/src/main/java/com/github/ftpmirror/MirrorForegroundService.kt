package com.github.ftpmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MirrorForegroundService : Service() {

    private val channelId = "ftp_mirror_channel"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Starting scheduled sync…", true))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateNotification("Syncing folder pairs…", true)
                val result = SyncEngine(this@MirrorForegroundService).syncAllEnabled()
                val summary =
                    "Done — ↑${result.uploaded}  ↓${result.downloaded}  del ${result.deleted}  fail ${result.failed}"
                updateNotification(summary, false)
            } catch (e: Exception) {
                updateNotification("Sync failed: ${e.message?.take(50) ?: "error"}", false)
            } finally {
                delay(5000)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FTP Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Folder pair sync progress"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun tapIntent(): PendingIntent {
        val i = Intent(this, PinActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(text: String, ongoing: Boolean): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FTP Mirror")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(!ongoing)
            .build()
    }

    private fun updateNotification(text: String, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, buildNotification(text, ongoing))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
