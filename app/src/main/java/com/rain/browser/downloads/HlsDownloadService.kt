package com.rain.browser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.IBinder
import com.rain.browser.data.HLS_MEDIA_MIME_TYPE
import com.rain.browser.data.SqliteStorage
import com.rain.browser.data.VideoRecord
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import com.rain.browser.library.VideoLibraryActivity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Owns user-authorized HLS transfers independently of an Activity lifecycle.
 * All network access remains in HlsHttpClient and never receives WebView credentials.
 */
class HlsDownloadService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancellations = ConcurrentHashMap<String, HlsDownloadCancellation>()
    private lateinit var repository: VideoRepository
    private lateinit var managedDirectory: File
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        val movies = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: throw IllegalStateException("พื้นที่จัดเก็บวิดีโอของแอปยังไม่พร้อมใช้งาน")
        managedDirectory = File(movies, "RAIN")
        repository = VideoRepository(SqliteStorage(this), managedDirectory)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            FOREGROUND_NOTIFICATION_ID,
            buildProgressNotification(null, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        when (intent?.action) {
            ACTION_CANCEL -> {
                val recordId = intent.getStringExtra(EXTRA_RECORD_ID).orEmpty()
                if (recordId.isNotBlank()) {
                    cancellations[recordId]?.cancel()
                        ?: repository.markHlsCanceled(recordId)
                    publishRecordState(recordId)
                }
                worker.execute { stopWhenIdle(startId) }
            }

            ACTION_CANCEL_ALL -> {
                repository.list()
                    .filter { it.mimeType == HLS_MEDIA_MIME_TYPE && it.status in ACTIVE_STATUSES }
                    .forEach { record ->
                        cancellations[record.id]?.cancel() ?: repository.markHlsCanceled(record.id)
                        publishRecordState(record.id)
                    }
                worker.execute { stopWhenIdle(startId) }
            }

            ACTION_START -> {
                val recordId = intent.getStringExtra(EXTRA_RECORD_ID).orEmpty()
                if (recordId.isNotBlank()) worker.execute {
                    processRecord(recordId)
                    stopWhenIdle(startId)
                }
            }

            ACTION_RECOVER, null -> worker.execute {
                repository.list()
                    .filter {
                        it.mimeType == HLS_MEDIA_MIME_TYPE &&
                            it.status in ACTIVE_STATUSES
                    }
                    .forEach { processRecord(it.id) }
                stopWhenIdle(startId)
            }

            else -> worker.execute { stopWhenIdle(startId) }
        }
        return START_STICKY
    }

    private fun processRecord(recordId: String) {
        val initial = repository.get(recordId) ?: return
        if (initial.mimeType != HLS_MEDIA_MIME_TYPE ||
            initial.status !in setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)
        ) return

        val finalOutput = File(managedDirectory, "rain-video-$recordId.mp4")
        if (initial.status == VideoStatus.VERIFYING && finalOutput.isFile &&
            ManagedVideoPathPolicy.isManagedFile(managedDirectory, finalOutput)
        ) {
            val recovered = runCatching {
                val duration = VideoMediaInspector.inspect(finalOutput).durationMs
                repository.markReady(recordId, finalOutput, duration)
            }.getOrNull()
            if (recovered?.status == VideoStatus.READY) {
                val checksum = runCatching { VideoChecksum.sha256(finalOutput) }.getOrNull()
                if (checksum != null && repository.updateChecksumSha256(recordId, checksum) != null) {
                    publishRecordState(recordId)
                    return
                }
                repository.markReadyCorrupted(recordId, "ไม่สามารถกู้คืน SHA-256 ของไฟล์ HLS ได้")
                if (ManagedVideoPathPolicy.isManagedFile(managedDirectory, finalOutput)) finalOutput.delete()
                publishRecordState(recordId)
                return
            }
            if (repository.get(recordId)?.status == VideoStatus.FAILED_CORRUPTED) {
                if (ManagedVideoPathPolicy.isManagedFile(managedDirectory, finalOutput)) finalOutput.delete()
                publishRecordState(recordId)
                return
            }
            if (finalOutput.exists() &&
                ManagedVideoPathPolicy.isManagedFile(managedDirectory, finalOutput)
            ) {
                repository.markCorrupted(
                    recordId,
                    finalOutput,
                    "ไฟล์ HLS ที่กู้คืนหลัง process หยุดทำงานไม่ผ่านการตรวจสอบ"
                )
                runCatching { finalOutput.delete() }
                publishRecordState(recordId)
                return
            }
        }

        val record = repository.startHlsTransfer(recordId) ?: return
        val cancellation = HlsDownloadCancellation()
        cancellations[recordId] = cancellation
        try {
            publishProgress(record, 0L, 0, 0)
            val fetched = HlsPlaylistFetcher.fetch(record.url)
            val playlist = HlsPlaylistParser.parse(fetched.finalUrl, fetched.content)
            require(playlist is HlsParsedPlaylist.Media) {
                "HLS Master Playlist ต้องเลือกคุณภาพก่อนเริ่มดาวน์โหลด"
            }

            val downloaded = HlsSegmentDownloader(managedDirectory).download(
                recordId = recordId,
                playlist = playlist,
                cancellation = cancellation
            ) { progress ->
                cancellation.throwIfCancelled()
                repository.updateHlsProgress(recordId, progress.bytesDownloaded)
                publishProgress(
                    record,
                    progress.bytesDownloaded,
                    progress.completedSegments,
                    progress.totalSegments
                )
            }
            cancellation.throwIfCancelled()
            repository.markVerifying(recordId)
            publishProgress(record, downloaded.bytesDownloaded, downloaded.totalSegments, downloaded.totalSegments)

            val remuxed = HlsMediaRemuxer(managedDirectory).remux(
                recordId = recordId,
                playlist = playlist,
                download = downloaded
            )
            cancellation.throwIfCancelled()
            val ready = repository.markReady(
                id = recordId,
                file = remuxed.file,
                durationMs = remuxed.durationMs
            ) ?: throw IllegalStateException("ไม่สามารถบันทึกสถานะวิดีโอที่ดาวน์โหลดเสร็จแล้วได้")
            if (ready.status != VideoStatus.READY) {
                throw IllegalStateException(ready.lastError.ifBlank { "ตรวจสอบไฟล์วิดีโอไม่สำเร็จ" })
            }

            val checksum = VideoChecksum.sha256(remuxed.file)
            if (repository.updateChecksumSha256(recordId, checksum) == null) {
                repository.markReadyCorrupted(recordId, "ไม่สามารถบันทึก SHA-256 ของวิดีโอได้")
                throw IllegalStateException("ไม่สามารถบันทึก SHA-256 ของวิดีโอได้")
            }
            publishRecordState(recordId)
        } catch (error: Exception) {
            if (cancellation.isCancelled()) {
                repository.markHlsCanceled(recordId)
            } else {
                repository.markFailed(recordId, safeError(error))
            }
            if (finalOutput.exists() &&
                ManagedVideoPathPolicy.isManagedFile(managedDirectory, finalOutput) &&
                repository.get(recordId)?.status != VideoStatus.READY
            ) {
                runCatching { finalOutput.delete() }
            }
            publishRecordState(recordId)
        } finally {
            cancellations.remove(recordId, cancellation)
        }
    }

    private fun publishProgress(
        record: VideoRecord,
        bytes: Long,
        completedSegments: Int,
        totalSegments: Int
    ) {
        val percent = if (totalSegments > 0) {
            (completedSegments * 100 / totalSegments).coerceIn(0, 100)
        } else 0
        val notification = buildProgressNotification(record.title, percent, bytes, completedSegments, totalSegments)
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(
        title: String?,
        percent: Int? = null,
        bytes: Long = 0L,
        completedSegments: Int = 0,
        totalSegments: Int = 0
    ): Notification {
        val libraryIntent = Intent(this, VideoLibraryActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            FOREGROUND_NOTIFICATION_ID,
            libraryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            this,
            FOREGROUND_NOTIFICATION_ID + 1,
            Intent(this, HlsDownloadService::class.java)
                .setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title?.take(120) ?: "RAIN กำลังดาวน์โหลด HLS")
            .setContentText(
                if (totalSegments > 0) {
                    "$completedSegments/$totalSegments segments · ${formatBytes(bytes)}"
                } else "กำลังเตรียมการดาวน์โหลด HLS"
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "ยกเลิกทั้งหมด",
                    cancelIntent
                ).build()
            )
            .build()
    }

    private fun publishRecordState(recordId: String) {
        val record = repository.get(recordId) ?: return
        if (record.status !in FINAL_STATUSES) return
        val contentIntent = PendingIntent.getActivity(
            this,
            record.id.hashCode(),
            Intent(this, VideoLibraryActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val succeeded = record.status == VideoStatus.READY
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (succeeded) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(
                if (succeeded) "ดาวน์โหลดวิดีโอเสร็จแล้ว"
                else if (record.status == VideoStatus.CANCELED) "ยกเลิกการดาวน์โหลดแล้ว"
                else "ดาวน์โหลดวิดีโอไม่สำเร็จ"
            )
            .setContentText(
                if (succeeded) record.title.take(120)
                else record.lastError.ifBlank { record.title }.take(160)
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { notificationManager.notify(record.id.hashCode(), notification) }
    }

    private fun stopWhenIdle(startId: Int) {
        if (cancellations.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "RAIN HLS downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress and completion of user-authorized HLS downloads"
            }
        )
    }

    private fun safeError(error: Throwable): String {
        val message = error.message ?: error.javaClass.simpleName
        return if (message.contains("401") || message.contains("403")) {
            "เว็บนี้จำกัดการดาวน์โหลด ไม่รองรับ"
        } else message.take(512)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1_048_576L -> "${bytes.coerceAtLeast(0L) / 1024L} KB"
        bytes < 1_073_741_824L -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        else -> String.format(java.util.Locale.ROOT, "%.2f GB", bytes / 1_073_741_824.0)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancellations.values.forEach(HlsDownloadCancellation::cancel)
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "rain_hls_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 3701
        private const val ACTION_START = "com.rain.browser.action.HLS_START"
        private const val ACTION_CANCEL = "com.rain.browser.action.HLS_CANCEL"
        private const val ACTION_CANCEL_ALL = "com.rain.browser.action.HLS_CANCEL_ALL"
        private const val ACTION_RECOVER = "com.rain.browser.action.HLS_RECOVER"
        private const val EXTRA_RECORD_ID = "rain_extra_hls_record_id"
        private val ACTIVE_STATUSES = setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)
        private val FINAL_STATUSES = setOf(VideoStatus.READY, VideoStatus.FAILED, VideoStatus.FAILED_CORRUPTED, VideoStatus.CANCELED)

        fun start(context: Context, recordId: String) {
            val intent = Intent(context, HlsDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RECORD_ID, recordId)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, recordId: String) {
            val intent = Intent(context, HlsDownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_RECORD_ID, recordId)
            context.startForegroundService(intent)
        }

        fun cancelAll(context: Context) {
            context.startForegroundService(
                Intent(context, HlsDownloadService::class.java).setAction(ACTION_CANCEL_ALL)
            )
        }

        fun recover(context: Context) {
            context.startForegroundService(
                Intent(context, HlsDownloadService::class.java).setAction(ACTION_RECOVER)
            )
        }
    }
}
