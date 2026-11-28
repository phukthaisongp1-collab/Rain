package com.rain.browser.library

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.rain.browser.data.SqliteStorage
import com.rain.browser.data.VideoRecord
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import com.rain.browser.downloads.ManagedVideoPathPolicy
import com.rain.browser.downloads.VideoDownloadCoordinator
import com.rain.browser.player.VideoPlayerActivity
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Displays completed local videos and transfers still managed by Android DownloadManager. */
class VideoLibraryActivity : Activity() {
    private lateinit var repository: VideoRepository
    private lateinit var coordinator: VideoDownloadCoordinator
    private lateinit var content: LinearLayout
    private lateinit var searchInput: EditText
    private val worker = Executors.newSingleThreadExecutor()
    private val thumbnailWorker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var latestRecords: List<VideoRecord> = emptyList()
    private var searchQuery: String = ""
    private val thumbnailCache = object : LruCache<String, Bitmap>(MAX_THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val refreshTask = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            worker.execute {
                val initial = runCatching { repository.list() }.getOrDefault(emptyList())
                initial.filter { it.status in ACTIVE_STATUSES && it.downloadId != null }
                    .forEach { record -> runCatching { coordinator.refresh(record.id) } }
                val visible = runCatching {
                    repository.list()
                        .filter { it.status in VISIBLE_STATUSES }
                        .sortedWith(compareByDescending<VideoRecord> {
                            if (it.downloadedAt > 0L) it.downloadedAt else it.createdAt
                        }.thenByDescending { it.createdAt })
                }.getOrDefault(emptyList())
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        latestRecords = visible
                        render()
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

        searchInput = EditText(this).apply {
            hint = "ค้นหาชื่อวิดีโอ"
            setSingleLine(true)
            setTextColor(Color.BLACK)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString().orEmpty()
                    render()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(12), dp(8), dp(12), dp(4)) })

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
        worker.execute { cleanupExpiredVideos() }
        handler.post(refreshTask)
    }

    private fun cleanupExpiredVideos() {
        val now = System.currentTimeMillis()
        val movies = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return
        val managedDirectory = File(movies, "RAIN")
        repository.list().asSequence()
            .filter {
                it.status == VideoStatus.READY &&
                    !it.isLiked &&
                    it.expiresAt != null &&
                    it.expiresAt <= now
            }
            .forEach { record ->
                runCatching {
                    if (record.filePath.isNotBlank()) {
                        val file = File(record.filePath)
                        if (file.exists()) {
                            if (!ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)) {
                                return@forEach
                            }
                            if (!file.delete()) return@forEach
                        }
                    }
                    repository.removeMetadata(record.id)
                }
            }
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val query = searchQuery.trim()
        val records = latestRecords.filter {
            query.isEmpty() || it.title.contains(query, ignoreCase = true)
        }
        if (records.isEmpty()) {
            content.addView(TextView(this).apply {
                text = if (query.isEmpty()) {
                    "ยังไม่มีวิดีโอที่ดาวน์โหลดเสร็จหรือกำลังดาวน์โหลด"
                } else {
                    "ไม่พบวิดีโอที่ตรงกับคำค้นหา"
                }
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }
        records.forEach { record ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            val thumbnail = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(226, 232, 240))
                contentDescription = "ภาพตัวอย่างวิดีโอจากไฟล์ในเครื่อง"
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(72))
            }
            row.addView(thumbnail)
            if (record.status == VideoStatus.READY) loadLocalThumbnail(record, thumbnail)

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            details.addView(TextView(this).apply {
                text = record.title
                textSize = 15f
                setTextColor(Color.BLACK)
                maxLines = 2
            })
            val downloaded = record.bytesDownloaded.coerceAtLeast(0L)
            val total = record.sizeBytes
            val percent = if (record.status == VideoStatus.READY) 100 else if (total > 0L) {
                ((downloaded * 100L) / total).coerceIn(0L, 100L).toInt()
            } else 0
            val status = statusLabel(record.status)
            details.addView(TextView(this).apply {
                text = if (record.status == VideoStatus.READY) {
                    "${formatDuration(record.durationMs)} · ${formatBytes(total)}"
                } else if (total > 0L) {
                    "$status · $percent% · ${formatBytes(downloaded)} / ${formatBytes(total)}"
                } else {
                    "$status · ${formatBytes(downloaded)}"
                }
                textSize = 12f
                setTextColor(Color.DKGRAY)
            })
            if (record.status != VideoStatus.READY) {
                details.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = percent
                    isIndeterminate = total <= 0L && record.status in ACTIVE_STATUSES
                })
            }
            val info = Button(this).apply {
                text = "รายละเอียด"
                textSize = 11f
                setOnClickListener { showDetails(record) }
            }
            row.addView(details)
            row.addView(info)
            if (record.status == VideoStatus.READY) {
                val heart = Button(this).apply {
                    text = if (record.isLiked) "♥" else "♡"
                    textSize = 20f
                    contentDescription = if (record.isLiked) "นำออกจากรายการถูกใจ" else "เพิ่มรายการถูกใจ"
                    setOnClickListener {
                        isEnabled = false
                        worker.execute {
                            val updated = runCatching {
                                if (record.isLiked) repository.unlike(record.id) else repository.like(record.id)
                            }.getOrNull()
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    if (updated == null) isEnabled = true
                                    handler.removeCallbacks(refreshTask)
                                    handler.post(refreshTask)
                                }
                            }
                        }
                    }
                }
                row.addView(heart)
            }
            val deleteButton = Button(this).apply {
                text = "ลบ"
                textSize = 11f
                setOnClickListener { confirmDelete(record) }
            }
            row.addView(deleteButton)
            if (record.status == VideoStatus.READY) {
                row.isClickable = true
                row.setOnClickListener { openReadyVideo(record) }
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

    private fun loadLocalThumbnail(record: VideoRecord, target: ImageView) {
        val file = File(record.filePath)
        if (!file.isFile || file.length() <= 0L) return
        val cacheKey = "${record.id}:${file.length()}:${file.lastModified()}"
        target.tag = cacheKey
        thumbnailCache.get(cacheKey)?.let {
            target.setImageBitmap(it)
            return
        }
        thumbnailWorker.execute {
            val bitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val frame = retriever.getFrameAtTime(
                        1_000_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: retriever.getFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: return@runCatching null
                    val scaled = Bitmap.createScaledBitmap(frame, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true)
                    if (scaled !== frame) frame.recycle()
                    scaled
                } finally {
                    retriever.release()
                }
            }.getOrNull()
            if (bitmap != null && !isFinishing && !isDestroyed) {
                thumbnailCache.put(cacheKey, bitmap)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && target.tag == cacheKey) {
                        target.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun openReadyVideo(record: VideoRecord) {
        val file = File(record.filePath)
        if (!file.isFile || file.length() <= 0L || file.length() != record.sizeBytes) {
            AlertDialog.Builder(this)
                .setTitle("ไฟล์วิดีโอเสียหายหรือไม่สมบูรณ์")
                .setMessage("ไม่สามารถเปิดวิดีโอนี้ได้ เนื่องจากไม่พบไฟล์หรือขนาดไฟล์ไม่ตรงกับข้อมูลที่บันทึกไว้")
                .setPositiveButton("ตกลง", null)
                .show()
            return
        }
        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_PATH, file.absolutePath)
            putExtra(VideoPlayerActivity.EXTRA_TITLE, record.title)
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_ID, record.id)
        })
    }

    private fun confirmDelete(record: VideoRecord) {
        AlertDialog.Builder(this)
            .setTitle("ลบวิดีโอ")
            .setMessage("ต้องการลบ \"${record.title}\" พร้อมไฟล์ที่จัดเก็บใน RAIN ใช่หรือไม่?")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("ลบ") { _, _ -> deleteVideo(record) }
            .show()
    }

    private fun deleteVideo(record: VideoRecord) {
        worker.execute {
            val result = runCatching {
                val current = repository.get(record.id)
                    ?: throw IllegalStateException("ไม่พบรายการวิดีโอ")
                if (current.status in ACTIVE_STATUSES && !coordinator.cancel(current.id)) {
                    throw IllegalStateException("ไม่สามารถยกเลิกการดาวน์โหลดได้")
                }
                val latest = repository.get(record.id)
                    ?: throw IllegalStateException("ไม่พบรายการวิดีโอ")
                if (latest.filePath.isNotBlank()) {
                    val file = File(latest.filePath)
                    if (file.exists()) {
                        val movies = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                            ?: throw IllegalStateException("พื้นที่จัดเก็บวิดีโอไม่พร้อมใช้งาน")
                        check(ManagedVideoPathPolicy.isManagedFile(File(movies, "RAIN"), file)) {
                            "ไฟล์อยู่นอกพื้นที่จัดเก็บของ RAIN จึงไม่ลบไฟล์นี้"
                        }
                        check(file.delete()) { "ไม่สามารถลบไฟล์วิดีโอได้" }
                    }
                }
                check(repository.removeMetadata(latest.id)) { "ไม่สามารถลบข้อมูลวิดีโอได้" }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    result.onSuccess {
                        handler.removeCallbacks(refreshTask)
                        handler.post(refreshTask)
                    }.onFailure { error ->
                        AlertDialog.Builder(this)
                            .setTitle("ลบวิดีโอไม่สำเร็จ")
                            .setMessage(error.message ?: "เกิดข้อผิดพลาดระหว่างลบวิดีโอ")
                            .setPositiveButton("ตกลง", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun showDetails(record: VideoRecord) {
        val domain = runCatching { URI(record.url).host }.getOrNull().orEmpty().ifBlank { "ไม่ทราบโดเมน" }
        val downloadedAt = if (record.downloadedAt > 0L) formatDate(record.downloadedAt) else "ยังไม่เสร็จสิ้น"
        val expiresAt = record.expiresAt?.takeIf { it > 0L }?.let(::formatDate)
            ?: if (record.isLiked) "ไม่หมดอายุ (ถูกใจแล้ว)" else "ยังไม่กำหนด"
        val message = listOf(
            "ชื่อ: ${record.title}",
            "โดเมน: $domain",
            "ขนาด: ${formatBytes(record.sizeBytes)}",
            "ระยะเวลา: ${formatDuration(record.durationMs)}",
            "วันที่ดาวน์โหลด: $downloadedAt",
            "วันหมดอายุ: $expiresAt",
            "สถานะ: ${statusLabel(record.status)}"
        ).joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("รายละเอียดวิดีโอ")
            .setMessage(message)
            .setPositiveButton("ปิด", null)
            .show()
    }

    private fun statusLabel(status: VideoStatus): String = when (status) {
        VideoStatus.READY -> "พร้อมใช้งาน"
        VideoStatus.QUEUED -> "รอเริ่มดาวน์โหลด"
        VideoStatus.DOWNLOADING -> "กำลังดาวน์โหลด"
        VideoStatus.VERIFYING -> "กำลังตรวจสอบไฟล์"
        VideoStatus.FAILED -> "ดาวน์โหลดไม่สำเร็จ"
        VideoStatus.FAILED_CORRUPTED -> "ไฟล์เสียหาย"
        VideoStatus.CANCELED -> "ยกเลิกแล้ว"
    }

    private fun formatDate(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(Date(timestamp))

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        if (bytes < 1_048_576L) return "${bytes / 1024L} KB"
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    }

    private fun showStorageUnavailable() {
        setContentView(TextView(this).apply {
            text = "พื้นที่จัดเก็บวิดีโอของแอปยังไม่พร้อมใช้งาน"
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        worker.shutdownNow()
        thumbnailWorker.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 1_500L
        const val THUMBNAIL_WIDTH = 320
        const val THUMBNAIL_HEIGHT = 180
        const val MAX_THUMBNAIL_CACHE_BYTES = 4 * 1024 * 1024
        val ACTIVE_STATUSES = setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)
        val VISIBLE_STATUSES = ACTIVE_STATUSES + VideoStatus.READY
    }
}
