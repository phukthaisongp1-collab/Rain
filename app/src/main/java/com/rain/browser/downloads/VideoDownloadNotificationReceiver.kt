package com.rain.browser.downloads

import android.Manifest
import android.content.BroadcastReceiver
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import com.rain.browser.MainActivity
import com.rain.browser.data.SqliteStorage
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import java.io.File

/**
 * Converts completed RAIN-owned DownloadManager requests into one app notification.
 * The receiver is manifest-registered so it can run when the browser process is absent.
 */
class VideoDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0L) return

        val pendingResult = goAsync()
        Thread {
            try {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) return@Thread

                val moviesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: return@Thread
                val managedDirectory = File(moviesDirectory, "RAIN")
                val repository = VideoRepository(SqliteStorage(context), managedDirectory)
                val record = repository.list().firstOrNull { it.downloadId == downloadId }
                    ?: return@Thread
                val coordinator = VideoDownloadCoordinator(context, repository, managedDirectory)
                val snapshot = coordinator.refresh(record.id) ?: return@Thread
                if (snapshot.status !in FINAL_STATUSES) return@Thread

                val refreshed = repository.get(record.id) ?: return@Thread
                val succeeded = refreshed.status == VideoStatus.READY
                val notificationManager =
                    context.getSystemService(NotificationManager::class.java) ?: return@Thread
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "RAIN video downloads",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Completion and failure updates for RAIN video downloads"
                    }
                )

                val openDownloadsIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_DOWNLOADS
                    putExtra("rain_extra_show_video_downloads", true)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val contentIntent = PendingIntent.getActivity(
                    context,
                    refreshed.id.hashCode(),
                    openDownloadsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(
                        if (succeeded) "ดาวน์โหลดวิดีโอเสร็จแล้ว"
                        else "ดาวน์โหลดวิดีโอไม่สำเร็จ"
                    )
                    .setContentText(refreshed.title.take(120))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .build()
                notificationManager.notify(refreshed.id.hashCode(), notification)
            } catch (_: Exception) {
                // Notification failures must not affect the persisted download result.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private companion object {
        const val CHANNEL_ID = "rain_video_download_results"
        const val ACTION_OPEN_DOWNLOADS = "com.rain.browser.action.OPEN_VIDEO_DOWNLOADS"
        val FINAL_STATUSES = setOf(
            VideoStatus.READY,
            VideoStatus.FAILED,
            VideoStatus.FAILED_CORRUPTED
        )
    }
}
