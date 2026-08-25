package com.github.ftpmirror

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager

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
    }
}
