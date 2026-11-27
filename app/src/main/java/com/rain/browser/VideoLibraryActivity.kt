package com.rain.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import android.text.format.Formatter
import com.rain.browser.data.SqliteStorage
import com.rain.browser.data.VideoRecord
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import com.rain.browser.downloads.ManagedVideoPathPolicy
import com.rain.browser.downloads.VideoDownloadCoordinator
import java.io.File
import java.text.DateFormat
import java.util.Date

class VideoLibraryActivity : Activity() {
    private lateinit var repository: VideoRepository
    private lateinit var transferCoordinator: VideoDownloadCoordinator
    private lateinit var managedDirectory: File
    private lateinit var listContainer: LinearLayout
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            if (!isFinishing && ::transferCoordinator.isInitialized) {
                refreshLibrary()
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        managedDirectory = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(filesDir, "movies"),
            "RAIN"
        )
        repository = VideoRepository(SqliteStorage(this), managedDirectory)
        transferCoordinator = VideoDownloadCoordinator(this, repository, managedDirectory)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 250, 255))
            setPadding(dp(16), dp(18), dp(16), dp(12))
        }
        val header = TextView(this).apply {
            text = getString(R.string.video_library_title)
            setTextColor(Color.rgb(12, 54, 99))
            textSize = 24f
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(header)

        val description = TextView(this).apply {
            text = getString(R.string.video_library_description)
            setTextColor(Color.rgb(77, 98, 120))
            textSize = 14f
        }
        root.addView(description)

        val refresh = Button(this).apply {
            text = getString(R.string.video_refresh)
            setOnClickListener { refreshLibrary() }
        }
        root.addView(refresh, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scrollView = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        scrollView.addView(listContainer)
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
        refreshLibrary()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            refreshLibrary()
            refreshHandler.removeCallbacks(refreshTask)
            refreshHandler.postDelayed(refreshTask, REFRESH_INTERVAL_MILLIS)
        }
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun refreshLibrary() {
        listContainer.removeAllViews()
        runCatching { transferCoordinator.reconcileAll() }
        val records = runCatching { repository.list() }.getOrElse {
            showMessage(it.message ?: getString(R.string.video_play_failed))
            return
        }

        if (records.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = getString(R.string.video_library_empty)
                setTextColor(Color.DKGRAY)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(36), dp(12), dp(36))
            })
            return
        }

        records.forEach { record ->
            listContainer.addView(createVideoCard(record))
        }
    }

    private fun createVideoCard(record: VideoRecord): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(2).toFloat()
        }
        val title = TextView(this).apply {
            text = record.title
            setTextColor(Color.rgb(18, 44, 72))
            textSize = 17f
        }
        card.addView(title)

        val status = TextView(this).apply {
            text = statusLabel(record.status)
            setTextColor(if (record.status == VideoStatus.READY) {
                Color.rgb(20, 120, 70)
            } else {
                Color.rgb(92, 105, 120)
            })
            textSize = 13f
            setPadding(0, dp(5), 0, dp(3))
        }
        card.addView(status)

        val details = buildString {
            if (record.sizeBytes > 0L) append(Formatter.formatFileSize(this@VideoLibraryActivity, record.sizeBytes))
            if (record.status == VideoStatus.DOWNLOADING) {
                if (isNotEmpty()) append("  •  ")
                val progress = if (record.sizeBytes > 0L) {
                    ((record.bytesDownloaded * 100L) / record.sizeBytes).coerceIn(0L, 100L)
                } else 0L
                append(getString(
                    R.string.video_progress,
                    Formatter.formatFileSize(this@VideoLibraryActivity, record.bytesDownloaded),
                    if (record.sizeBytes > 0L) "$progress%" else getString(R.string.video_progress_unknown)
                ))
            }
            if (record.durationMs > 0L) {
                if (isNotEmpty()) append("  •  ")
                append(formatDuration(record.durationMs))
            }
            if (record.downloadedAt > 0L) {
                if (isNotEmpty()) appendLine()
                append(getString(R.string.video_downloaded_at, formatDate(record.downloadedAt)))
            }
            if (record.isLiked) {
                if (isNotEmpty()) appendLine()
                append(getString(R.string.video_liked_keep))
            } else if (record.expiresAt != null) {
                if (isNotEmpty()) appendLine()
                append(getString(R.string.video_expires_at, formatDate(record.expiresAt)))
            }
            if (record.lastError.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(record.lastError)
            }
        }
        if (details.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = details
                setTextColor(Color.rgb(85, 99, 114))
                textSize = 12f
                setPadding(0, dp(3), 0, dp(7))
            })
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        if (record.status in setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING)) {
            actions.addView(Button(this).apply {
                text = getString(R.string.video_cancel)
                setOnClickListener {
                    if (!transferCoordinator.cancel(record.id)) {
                        showMessage(getString(R.string.video_cancel_failed))
                    }
                    refreshLibrary()
                }
            })
        }

        if (record.status in setOf(VideoStatus.FAILED, VideoStatus.FAILED_CORRUPTED, VideoStatus.CANCELED)) {
            actions.addView(Button(this).apply {
                text = getString(R.string.video_retry)
                setOnClickListener {
                    runCatching { transferCoordinator.retry(record.id) }
                        .onFailure { showMessage(getString(R.string.video_retry_failed)) }
                    refreshLibrary()
                }
            })
        }

        if (record.status == VideoStatus.READY) {
            actions.addView(Button(this).apply {
                text = getString(if (record.isLiked) R.string.video_unlike else R.string.video_like)
                setOnClickListener {
                    repository.setLiked(record.id, !record.isLiked)
                    refreshLibrary()
                }
            })
            actions.addView(Button(this).apply {
                text = getString(R.string.video_play)
                setOnClickListener { playVideo(record) }
            })
        }

        if (record.status !in setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)) {
            actions.addView(Button(this).apply {
                text = getString(R.string.video_delete)
                setOnClickListener { confirmDelete(record) }
            })
        }
        card.addView(actions)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
        card.layoutParams = params
        return card
    }

    private fun playVideo(record: VideoRecord) {
        if (record.status != VideoStatus.READY || record.filePath.isBlank()) {
            showMessage(getString(R.string.video_play_failed))
            return
        }
        val file = File(record.filePath)
        if (!file.isFile || !ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)) {
            showMessage(getString(R.string.video_play_failed))
            refreshLibrary()
            return
        }

        val videoView = VideoView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240)
            )
        }
        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setOnErrorListener { _, _, _ ->
            showMessage(getString(R.string.video_play_failed))
            true
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(record.title)
            .setView(videoView)
            .setPositiveButton(R.string.dialog_ok, null)
            .create()
        dialog.setOnDismissListener { videoView.stopPlayback() }
        dialog.setOnShowListener {
            videoView.setOnPreparedListener { player ->
                player.isLooping = false
                videoView.start()
            }
            videoView.setVideoURI(Uri.fromFile(file))
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun confirmDelete(record: VideoRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.video_delete)
            .setMessage(R.string.video_delete_confirm)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.video_delete) { _, _ -> deleteVideo(record) }
            .show()
    }

    private fun deleteVideo(record: VideoRecord) {
        if (record.status in setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)) {
            showMessage(getString(R.string.video_delete_failed))
            return
        }

        val path = record.filePath
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists()) {
                if (!ManagedVideoPathPolicy.isManagedFile(managedDirectory, file) || !file.delete()) {
                    showMessage(getString(R.string.video_delete_failed))
                    return
                }
            }
        }

        if (!repository.removeMetadata(record.id)) {
            showMessage(getString(R.string.video_delete_failed))
            return
        }
        refreshLibrary()
    }

    private fun statusLabel(status: VideoStatus): String = when (status) {
        VideoStatus.QUEUED -> getString(R.string.video_status_queued)
        VideoStatus.DOWNLOADING -> getString(R.string.video_status_downloading)
        VideoStatus.VERIFYING -> getString(R.string.video_status_verifying)
        VideoStatus.READY -> getString(R.string.video_status_ready)
        VideoStatus.FAILED -> getString(R.string.video_status_failed)
        VideoStatus.FAILED_CORRUPTED -> getString(R.string.video_status_corrupted)
        VideoStatus.CANCELED -> getString(R.string.video_status_canceled)
    }

    private fun formatDate(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 1_000L
    }
}
