package com.rain.browser.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.rain.browser.R
import com.rain.browser.VideoLibraryActivity
import com.rain.browser.data.SqliteStorage
import com.rain.browser.data.VideoRepository
import java.io.File

/**
 * Deletes only expired, unliked RAIN videos from the managed directory.
 * WorkManager persists this periodic task across process exits and reboots.
 */
class VideoRetentionWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result = runCatching {
        val managedDirectory = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(applicationContext.filesDir, "movies"),
            "RAIN"
        )
        val repository = VideoRepository(SqliteStorage(applicationContext), managedDirectory)
        val expiredRecords = repository.findExpired(System.currentTimeMillis())
        var deletedCount = 0

        expiredRecords.forEach { record ->
            runCatching {
                val path = record.filePath
                if (path.isBlank()) {
                    if (repository.removeMetadata(record.id)) deletedCount++
                    return@runCatching
                }

                val file = File(path)
                if (!file.exists()) {
                    if (repository.removeMetadata(record.id)) deletedCount++
                    return@runCatching
                }

                if (!ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)) {
                    return@runCatching
                }

                if (file.delete() && repository.removeMetadata(record.id)) {
                    deletedCount++
                }
            }
        }

        postStorageWarningIfNeeded()
        Result.success(workDataOf("expired_videos_removed" to deletedCount))
    }.getOrElse {
        Result.retry()
    }

    private fun postStorageWarningIfNeeded() {
        val storageRoot = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val stats = runCatching { StatFs(storageRoot.absolutePath) }.getOrNull() ?: return
        val totalBytes = stats.totalBytes
        val availableBytes = stats.availableBytes
        if (totalBytes <= 0L) return

        val isNearlyFull = availableBytes <= MIN_AVAILABLE_BYTES ||
            availableBytes.toDouble() / totalBytes.toDouble() <= LOW_STORAGE_RATIO
        if (!isNearlyFull) return

        val now = System.currentTimeMillis()
        val preferences = applicationContext.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        val lastWarningAt = preferences.getLong(LAST_WARNING_KEY, 0L)
        if (lastWarningAt > 0L && now >= lastWarningAt &&
            now - lastWarningAt < WARNING_COOLDOWN_MILLIS
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.video_storage_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val openLibrary = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, VideoLibraryActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.video_storage_warning_title))
            .setContentText(applicationContext.getString(R.string.video_storage_warning_message))
            .setContentIntent(openLibrary)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        preferences.edit().putLong(LAST_WARNING_KEY, now).apply()
    }

    private companion object {
        const val MIN_AVAILABLE_BYTES = 500L * 1024L * 1024L
        const val LOW_STORAGE_RATIO = 0.10
        const val WARNING_COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L
        const val CHANNEL_ID = "rain_video_storage"
        const val NOTIFICATION_ID = 4207
        const val STORAGE_PREFS = "rain_video_storage_warning"
        const val LAST_WARNING_KEY = "last_warning_at"
    }
}
