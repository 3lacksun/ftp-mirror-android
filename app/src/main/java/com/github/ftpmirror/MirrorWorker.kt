package com.github.ftpmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class MirrorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            setForeground(createForegroundInfo("Synchronising assigned endpoints…"))
            val result = SyncEngine(applicationContext).syncAllEnabled()
            Result.success(
                workDataOf(
                    "uploaded" to result.uploaded,
                    "downloaded" to result.downloaded,
                    "deleted" to result.deleted,
                    "skipped" to result.skipped,
                    "failed" to result.failed,
                    "conflicts" to result.conflicts
                )
            )
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
            else Result.failure(workDataOf("error" to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification(text)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "STONE//SYNC background operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Long-running endpoint synchronisation"
            }
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, PinActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("STONE//SYNC")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stone_sync_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "stone_sync_worker"
        private const val NOTIFICATION_ID = 1002
        private const val MAX_RETRY_ATTEMPTS = 3

        fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<MirrorWorker>()
                .setConstraints(constraints)
                .addTag("stone_sync_one_time")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun schedule(context: Context, minutes: Long) {
            val interval = minutes.coerceAtLeast(15L)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MirrorWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag("stone_sync_periodic")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "stone_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("stone_sync")
            WorkManager.getInstance(context).cancelAllWorkByTag("stone_sync_one_time")

            // Clean up pre-rename work identifiers from installed FTP Mirror builds.
            WorkManager.getInstance(context).cancelUniqueWork("ftp_mirror")
            WorkManager.getInstance(context).cancelAllWorkByTag("ftp_mirror_one_time")
        }
    }
}
