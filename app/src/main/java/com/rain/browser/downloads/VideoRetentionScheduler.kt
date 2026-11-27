package com.rain.browser.downloads

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Registers one durable daily retention and storage-health task. */
object VideoRetentionScheduler {
    private const val WORK_NAME = "rain-video-retention-and-storage"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<VideoRetentionWorker>(
            24L,
            TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
