package com.github.ftpmirror

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class MirrorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, MirrorForegroundService::class.java)
            ContextCompat.startForegroundService(applicationContext, intent)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<MirrorWorker>()
                .addTag("ftp_mirror_one_time")
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
                .addTag("ftp_mirror_periodic")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ftp_mirror",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("ftp_mirror")
            WorkManager.getInstance(context).cancelAllWorkByTag("ftp_mirror_one_time")
        }
    }
}
