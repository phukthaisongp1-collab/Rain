package com.rain.browser.library

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.rain.browser.data.SqliteStorage
import com.rain.browser.data.VideoRecord
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import com.rain.browser.downloads.VideoDownloadCoordinator
import java.io.File
import java.util.concurrent.Executors

/** Displays completed local videos and transfers still managed by Android DownloadManager. */
class VideoLibraryActivity : Activity() {
    private lateinit var repository: VideoRepository
    private lateinit var coordinator: VideoDownloadCoordinator
    private lateinit var content: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            worker.execute {
                val initial = runCatching { repository.list() }.getOrDefault(emptyList())
                initial.filter { it.status in ACTIVE_STATUSES && it.downloadId != null }
                    .forEach { record -> runCatching { coordinator.refresh(record.id) } }
                val visible = runCatching { repository.list().filter { it.status in VISIBLE_STATUSES } }
                    .getOrDefault(emptyList())
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        render(visible)
                        handler.postDelayed(this, REFRESH_INTERVAL_MS)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(15, 23, 42)

        val movies = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (movies == null) {
            showStorageUnavailable()
            return
        }
        val managedDirectory = File(movies, "RAIN")
        repository = VideoRepository(SqliteStorage(this), managedDirectory)
        coordinator = VideoDownloadCoordinator(this, repository, managedDirectory)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 32f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(18), 0)
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "คลังวิดีโอ"
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        header.addView(back)
        header.addView(title)
        root.addView(header)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        handler.post(refreshTask)
    }

    private fun render(records: List<VideoRecord>) {
        content.removeAllViews()
        if (records.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "ยังไม่มีวิดีโอที่ดาวน์โหลดเสร็จหรือกำลังดาวน์โหลด"
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }
        records.forEach { record ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(TextView(this).apply {
                text = record.title
                textSize = 16f
                setTextColor(Color.BLACK)
                maxLines = 2
            })
            val downloaded = record.bytesDownloaded.coerceAtLeast(0L)
            val total = record.sizeBytes
            val percent = if (record.status == VideoStatus.READY) 100 else if (total > 0L) {
                ((downloaded * 100L) / total).coerceIn(0L, 100L).toInt()
            } else 0
            val status = when (record.status) {
                VideoStatus.READY -> "พร้อมใช้งานแบบออฟไลน์"
                VideoStatus.QUEUED -> "รอเริ่มดาวน์โหลด"
                VideoStatus.DOWNLOADING -> "กำลังดาวน์โหลด"
                VideoStatus.VERIFYING -> "กำลังตรวจสอบไฟล์"
                else -> ""
            }
            row.addView(TextView(this).apply {
                text = if (total > 0L) "$status · $percent% · ${formatBytes(downloaded)} / ${formatBytes(total)}"
                else "$status · ${formatBytes(downloaded)}"
                textSize = 13f
                setTextColor(Color.DKGRAY)
            })
            if (record.status != VideoStatus.READY) {
                row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = percent
                    isIndeterminate = total <= 0L && record.status in ACTIVE_STATUSES
                })
            }
            content.addView(row)
            content.addView(View(this).apply {
                setBackgroundColor(Color.LTGRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
            })
        }
    }

    private fun showStorageUnavailable() {
        setContentView(TextView(this).apply {
            text = "พื้นที่จัดเก็บวิดีโอของแอปยังไม่พร้อมใช้งาน"
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        })
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1_048_576L) return "${bytes / 1024L} KB"
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        worker.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 1_500L
        val ACTIVE_STATUSES = setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)
        val VISIBLE_STATUSES = ACTIVE_STATUSES + VideoStatus.READY
    }
}
