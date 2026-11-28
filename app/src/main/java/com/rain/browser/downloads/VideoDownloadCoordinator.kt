package com.rain.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.database.Cursor
import com.rain.browser.data.VideoRecord
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Owns RAIN-managed direct MP4/WebM transfers.
 *
 * It deliberately does not reuse WebView cookies or credentials and does not
 * modify the browser's generic file/APK download path.
 */
class VideoDownloadCoordinator(
    context: Context,
    private val repository: VideoRepository,
    private val managedDirectory: File,
    private val allowLoopbackForTests: Boolean = false
) {
    private val appContext = context.applicationContext
    private val downloadManager: DownloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    data class TransferSnapshot(
        val status: VideoStatus,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val reason: String = ""
    )

    @Synchronized
    fun enqueue(record: VideoRecord, userAgent: String = ""): Long {
        require(record.status == VideoStatus.QUEUED) { "Only queued video records can be started." }
        require(record.authorizationConfirmed) { "User authorization confirmation is required before downloading media." }
        val validatedSource = try {
            if (allowLoopbackForTests) null else VideoSourceValidator.validate(record.url)
        } catch (error: Exception) {
            repository.markFailed(record.id, safeError(error))
            throw error
        }
        val sourceAndMime = try {
            validateSource(validatedSource?.finalUrl ?: record.url) to
                (validatedSource?.mimeType ?: normalizeVideoMime(record.mimeType))
        } catch (error: Exception) {
            repository.markFailed(record.id, safeError(error))
            throw error
        }
        val source = sourceAndMime.first
        val mime = sourceAndMime.second
        val preflightSize = validatedSource?.contentLength ?: -1L
        if (preflightSize > MAX_VIDEO_SIZE_BYTES) {
            repository.markFailed(record.id, "วิดีโอมีขนาดเกินขีดจำกัด 4 GB")
            throw IllegalArgumentException("Video exceeds the 4 GB download limit.")
        }
        val extension = if (mime == "video/webm") "webm" else "mp4"
        val fileName = "rain-video-${record.id}.$extension"

        if (!managedDirectory.exists() && !managedDirectory.mkdirs()) {
            repository.markFailed(record.id, "Unable to create RAIN video storage.")
            throw IllegalStateException("Unable to create RAIN video storage.")
        }
        check(managedDirectory.isDirectory) { "RAIN video storage is not a directory." }
        val requiredStorageBytes = if (preflightSize >= 0L) {
            preflightSize + STORAGE_SAFETY_MARGIN_BYTES
        } else {
            UNKNOWN_SIZE_MINIMUM_FREE_BYTES
        }
        if (managedDirectory.usableSpace < requiredStorageBytes) {
            val message = if (preflightSize >= 0L) {
                "พื้นที่จัดเก็บไม่เพียงพอ ต้องมีพื้นที่ว่างมากกว่าขนาดวิดีโออย่างน้อย 100 MiB"
            } else {
                "พื้นที่จัดเก็บไม่เพียงพอ ต้องมีพื้นที่ว่างอย่างน้อย 500 MiB"
            }
            repository.markFailed(record.id, message)
            throw IllegalStateException(message)
        }

        val externalMoviesDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: run {
                repository.markFailed(record.id, "Android DownloadManager requires app-specific external Movies storage.")
                throw IllegalStateException("Android DownloadManager requires app-specific external Movies storage.")
            }
        val expectedManagedDirectory = File(externalMoviesDirectory, "RAIN").canonicalFile
        if (managedDirectory.canonicalFile != expectedManagedDirectory) {
            repository.markFailed(record.id, "Video storage does not match the Android DownloadManager destination.")
            throw IllegalStateException("Video storage does not match the Android DownloadManager destination.")
        }

        val destination = File(managedDirectory, fileName)
        check(ManagedVideoPathPolicy.isManagedFile(managedDirectory, destination)) {
            "Video destination is outside RAIN-managed storage."
        }
        if (repository.setDestination(record.id, destination) == null) {
            repository.markFailed(record.id, "Unable to persist the video destination.")
            throw IllegalStateException("Unable to persist the video destination.")
        }

        val request = DownloadManager.Request(Uri.parse(source.toString())).apply {
            setTitle(record.title.take(120))
            setDescription("RAIN offline video")
            setMimeType(mime)
            // DownloadManager shows transfer progress; RAIN posts the single completion/failure notification.
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setAllowedOverRoaming(false)
            setAllowedOverMetered(true)
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_MOVIES,
                "RAIN/$fileName"
            )
            if (userAgent.isNotBlank() && userAgent.length <= MAX_USER_AGENT_LENGTH &&
                userAgent.none { it == '\r' || it == '\n' }
            ) {
                addRequestHeader("User-Agent", userAgent)
            }
            validatedSource?.etag?.takeIf {
                it.isNotBlank() && it.length <= MAX_VALIDATOR_HEADER_LENGTH &&
                    it.none { character -> character == '\r' || character == '\n' }
            }?.let { addRequestHeader("If-Match", it) }
            if (validatedSource?.etag.isNullOrBlank()) {
                validatedSource?.lastModified?.takeIf {
                    it.isNotBlank() && it.length <= MAX_VALIDATOR_HEADER_LENGTH &&
                        it.none { character -> character == '\r' || character == '\n' }
                }?.let { addRequestHeader("If-Unmodified-Since", it) }
            }
        }

        val requestId = try {
            downloadManager.enqueue(request)
        } catch (error: Exception) {
            repository.markFailed(record.id, safeError(error))
            throw IllegalStateException("Unable to enqueue video transfer.", error)
        }

        val persisted = repository.updateTransfer(
            id = record.id,
            downloadId = requestId,
            bytesDownloaded = 0L,
            totalBytes = validatedSource?.contentLength ?: -1L,
            etag = validatedSource?.etag.orEmpty(),
            lastModified = validatedSource?.lastModified.orEmpty()
        )
        if (persisted == null) {
            downloadManager.remove(requestId)
            repository.markFailed(record.id, "Unable to persist the system download request.")
            throw IllegalStateException("Unable to persist the system download request.")
        }
        return requestId
    }

    @Synchronized
    fun refresh(recordId: String): TransferSnapshot? {
        val record = repository.get(recordId) ?: return null
        val requestId = record.downloadId ?: run {
            if (record.status in ACTIVE_STATUSES) {
                repository.markFailed(recordId, "Video transfer has no persisted system request ID.")
            }
            return null
        }
        val snapshot = query(requestId) ?: run {
            if (record.status in ACTIVE_STATUSES) {
                val staleFile = record.filePath.takeIf { it.isNotBlank() }?.let(::File)
                if (staleFile != null && staleFile.exists() &&
                    ManagedVideoPathPolicy.isManagedFile(managedDirectory, staleFile)
                ) {
                    runCatching { staleFile.delete() }
                }
                repository.markFailed(recordId, "Android DownloadManager no longer has this request.")
            }
            return null
        }

        when (snapshot.status) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED -> {
                val downloadedBytes = snapshot.bytesDownloaded.coerceAtLeast(0L)
                val reportedTotal = snapshot.totalBytes
                if (downloadedBytes > MAX_VIDEO_SIZE_BYTES || reportedTotal > MAX_VIDEO_SIZE_BYTES) {
                    downloadManager.remove(requestId)
                    val partial = record.filePath.takeIf { it.isNotBlank() }?.let(::File)
                    if (partial != null && partial.exists() &&
                        ManagedVideoPathPolicy.isManagedFile(managedDirectory, partial)
                    ) {
                        runCatching { partial.delete() }
                    }
                    repository.markFailed(recordId, "วิดีโอมีขนาดเกินขีดจำกัด 4 GB")
                    return TransferSnapshot(VideoStatus.FAILED, downloadedBytes, reportedTotal,
                        "วิดีโอมีขนาดเกินขีดจำกัด 4 GB")
                }
                repository.updateTransfer(
                    id = recordId,
                    downloadId = requestId,
                    bytesDownloaded = snapshot.bytesDownloaded.coerceAtLeast(0L),
                    totalBytes = snapshot.totalBytes,
                    now = System.currentTimeMillis()
                )
                val waitingForNetwork = snapshot.status == DownloadManager.STATUS_PAUSED &&
                    snapshot.reason in setOf(
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK,
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI
                    )
                return TransferSnapshot(
                    VideoStatus.DOWNLOADING,
                    snapshot.bytesDownloaded,
                    snapshot.totalBytes,
                    if (waitingForNetwork) WAITING_FOR_NETWORK
                    else "Android DownloadManager paused or running (reason: ${snapshot.reason})."
                )
            }

            DownloadManager.STATUS_FAILED -> {
                val insufficientSpace = snapshot.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE
                val failureMessage = if (insufficientSpace) {
                    "พื้นที่จัดเก็บไม่เพียงพอ กรุณาเพิ่มพื้นที่ว่างแล้วลองดาวน์โหลดอีกครั้ง"
                } else {
                    "ดาวน์โหลดไม่สำเร็จ (รหัสข้อผิดพลาด: ${snapshot.reason})"
                }
                if (insufficientSpace) {
                    downloadManager.remove(requestId)
                    deleteManagedPartial(record.filePath)
                }
                repository.markFailed(recordId, failureMessage)
                return TransferSnapshot(
                    VideoStatus.FAILED,
                    snapshot.bytesDownloaded,
                    snapshot.totalBytes,
                    failureMessage
                )
            }

            DownloadManager.STATUS_SUCCESSFUL -> {
                repository.updateTransfer(
                    id = recordId,
                    downloadId = requestId,
                    bytesDownloaded = snapshot.bytesDownloaded.coerceAtLeast(0L),
                    totalBytes = snapshot.totalBytes,
                    now = System.currentTimeMillis()
                )
                repository.markVerifying(recordId)
                val completedFile = File(record.filePath)
                val durationMs = if (allowLoopbackForTests) {
                    0L
                } else {
                    runCatching { VideoMediaInspector.inspect(completedFile).durationMs }
                        .getOrElse { error ->
                            val corrupted = repository.markCorrupted(
                                recordId,
                                completedFile,
                                safeError(error)
                            )
                            return TransferSnapshot(
                                VideoStatus.FAILED_CORRUPTED,
                                snapshot.bytesDownloaded,
                                snapshot.totalBytes,
                                corrupted?.lastError ?: "Video decoder verification failed."
                            )
                        }
                }
                val ready = repository.markReady(
                    id = recordId,
                    file = completedFile,
                    durationMs = durationMs
                )
                if (ready == null) {
                    repository.markFailed(recordId, "Unable to finalize the downloaded video record.")
                    return TransferSnapshot(
                        VideoStatus.FAILED,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                        "Unable to finalize the downloaded video record."
                    )
                }
                if (ready.status != VideoStatus.READY) {
                    return TransferSnapshot(
                        VideoStatus.FAILED_CORRUPTED,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                        ready.lastError
                    )
                }
                val checksum = runCatching { VideoChecksum.sha256(completedFile) }.getOrElse { error ->
                    val corrupted = repository.markReadyCorrupted(
                        recordId,
                        "Unable to calculate video SHA-256: ${safeError(error)}"
                    )
                    return TransferSnapshot(
                        VideoStatus.FAILED_CORRUPTED,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                        corrupted?.lastError ?: "Unable to calculate video SHA-256."
                    )
                }
                if (repository.updateChecksumSha256(recordId, checksum) == null) {
                    val corrupted = repository.markReadyCorrupted(
                        recordId,
                        "Unable to persist video SHA-256."
                    )
                    return TransferSnapshot(
                        VideoStatus.FAILED_CORRUPTED,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                        corrupted?.lastError ?: "Unable to persist video SHA-256."
                    )
                }
                return TransferSnapshot(
                    VideoStatus.READY,
                    snapshot.bytesDownloaded,
                    snapshot.totalBytes
                )
            }

            else -> return TransferSnapshot(
                record.status,
                snapshot.bytesDownloaded,
                snapshot.totalBytes,
                "Unknown DownloadManager status."
            )
        }
    }

    @Synchronized
    fun cancel(recordId: String): Boolean {
        val record = repository.get(recordId) ?: return false
        if (record.status !in ACTIVE_STATUSES) return false
        val requestId = record.downloadId
        if (requestId != null && downloadManager.remove(requestId) == 0) {
            val stillPresent = query(requestId) != null
            if (stillPresent) return false
        }
        return repository.markCanceled(recordId) != null
    }

    @Synchronized
    fun retry(recordId: String, userAgent: String = ""): Long {
        val record = repository.get(recordId) ?: throw IllegalArgumentException("Video record not found.")
        require(record.status in setOf(VideoStatus.FAILED, VideoStatus.FAILED_CORRUPTED, VideoStatus.CANCELED)) {
            "Only failed or canceled videos can be retried."
        }
        if (record.filePath.isNotBlank()) {
            val partial = File(record.filePath)
            if (partial.exists()) {
                check(ManagedVideoPathPolicy.isManagedFile(managedDirectory, partial) && partial.delete()) {
                    "Unable to safely remove the previous partial video file."
                }
            }
        }
        val queued = repository.resetForRetry(recordId)
            ?: throw IllegalStateException("Unable to reset the video record for retry.")
        return enqueue(queued, userAgent)
    }

    /** Reconciles persisted records with Android's durable DownloadManager queue. */
    @Synchronized
    fun reconcileAll(): List<TransferSnapshot> {
        val initialRecords = repository.list()
        val results = mutableListOf<TransferSnapshot>()
        initialRecords.filter { it.status == VideoStatus.READY }.forEach { record ->
            val file = record.filePath.takeIf { it.isNotBlank() }?.let(::File)
            if (file == null || !file.isFile) {
                repository.markMissing(record.id, "ไม่พบไฟล์วิดีโอในพื้นที่จัดเก็บของ RAIN")
            } else if (!ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)) {
                repository.markMissing(record.id, "ตำแหน่งไฟล์วิดีโอไม่อยู่ในพื้นที่จัดเก็บของ RAIN")
            }
        }

        val records = repository.list()
        val knownRequestIds = records.mapNotNull { it.downloadId }.toSet()
        records.filter { it.status in ACTIVE_STATUSES }.forEach { record ->
            val result = if (record.downloadId == null) {
                repository.markFailed(record.id, "Video transfer has no persisted system request ID.")
                TransferSnapshot(VideoStatus.FAILED, record.bytesDownloaded, record.sizeBytes)
            } else {
                refresh(record.id) ?: TransferSnapshot(
                    VideoStatus.FAILED,
                    record.bytesDownloaded,
                    record.sizeBytes,
                    "Unable to reconcile system download."
                )
            }
            results += result
        }
        removeOrphanedManagedRequests(knownRequestIds)
        removeOrphanedManagedFiles()
        return results
    }

    private fun removeOrphanedManagedFiles() {
        val knownPaths = repository.list()
            .mapNotNull { record ->
                record.filePath.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { File(path).canonicalPath }.getOrNull()
                }
            }
            .toSet()
        val children = managedDirectory.listFiles() ?: return
        children.filter { it.isFile }.forEach { candidate ->
            if (!ManagedVideoPathPolicy.isManagedFile(managedDirectory, candidate)) return@forEach
            val canonicalPath = runCatching { candidate.canonicalPath }.getOrNull() ?: return@forEach
            if (canonicalPath in knownPaths) return@forEach
            runCatching { candidate.delete() }
        }
    }

    private fun deleteManagedPartial(path: String) {
        if (path.isBlank()) return
        val partial = runCatching { File(path) }.getOrNull() ?: return
        if (partial.exists() && ManagedVideoPathPolicy.isManagedFile(managedDirectory, partial)) {
            runCatching { partial.delete() }
        }
    }

    /**
     * Removes only app-owned DownloadManager requests whose resolved local file
     * is proven to be inside RAIN storage. Unknown locations are left untouched.
     */
    private fun removeOrphanedManagedRequests(knownRequestIds: Set<Long>) {
        val cursor = runCatching { downloadManager.query(DownloadManager.Query()) }.getOrNull() ?: return
        cursor.use {
            val idColumn = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val fileColumn = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
            val uriColumn = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (idColumn < 0) return
            while (it.moveToNext()) {
                val requestId = it.getLong(idColumn)
                if (requestId in knownRequestIds) continue
                val localPath = if (fileColumn >= 0 && !it.isNull(fileColumn)) {
                    it.getString(fileColumn)
                } else if (uriColumn >= 0 && !it.isNull(uriColumn)) {
                    val localUri = Uri.parse(it.getString(uriColumn))
                    localUri.path.takeIf { localUri.scheme.equals("file", true) }
                } else {
                    null
                } ?: continue
                val candidate = File(localPath)
                if (ManagedVideoPathPolicy.isManagedFile(managedDirectory, candidate)) {
                    downloadManager.remove(requestId)
                }
            }
        }
    }

    private fun query(requestId: Long): DownloadSnapshot? {
        if (requestId < 0L) return null
        val cursor = try {
            downloadManager.query(DownloadManager.Query().setFilterById(requestId))
        } catch (_: Exception) {
            return null
        }
        cursor.use {
            if (!it.moveToFirst()) return null
            return DownloadSnapshot(
                status = it.intOrDefault(DownloadManager.COLUMN_STATUS, -1),
                reason = it.intOrDefault(DownloadManager.COLUMN_REASON, 0),
                bytesDownloaded = it.longOrDefault(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, 0L),
                totalBytes = it.longOrDefault(DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1L)
            )
        }
    }

    private fun validateSource(rawUrl: String): URI {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https" || scheme == "http") { "Only HTTP(S) video sources are supported." }
        val host = uri.host?.lowercase(Locale.ROOT)
        require(!host.isNullOrBlank() && uri.rawUserInfo == null) {
            "Video source URL must have a host and must not contain embedded credentials."
        }
        val localName = host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")
        require(!localName || allowLoopbackForTests && host == "localhost") {
            "Local network video sources are not supported."
        }
        if (host.matches(IPV4_LITERAL) || host.startsWith("[") || host.contains(":")) {
            val address = runCatching { InetAddress.getByName(host.trim('[', ']')) }.getOrNull()
            val testLoopback = allowLoopbackForTests && address?.isLoopbackAddress == true
            require(address != null && (testLoopback || (
                !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress && !address.isSiteLocalAddress &&
                    !address.isMulticastAddress
                ))
            ) { "Private or local IP video sources are not supported." }
        }
        return uri
    }

    private fun normalizeVideoMime(rawMime: String): String {
        val mime = rawMime.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when (mime) {
            "video/webm" -> "video/webm"
            "video/mp4", "application/mp4", "" -> "video/mp4"
            else -> throw IllegalArgumentException("Only direct MP4 and WebM sources are supported.")
        }
    }

    private fun safeError(error: Throwable): String =
        (error.message ?: error.javaClass.simpleName).take(256)

    private fun Cursor.intOrDefault(column: String, fallback: Int): Int =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getInt) ?: fallback

    private fun Cursor.longOrDefault(column: String, fallback: Long): Long =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: fallback

    private data class DownloadSnapshot(
        val status: Int,
        val reason: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    )

    companion object {
        const val MAX_VIDEO_SIZE_BYTES = 4L * 1024L * 1024L * 1024L
        const val STORAGE_SAFETY_MARGIN_BYTES = 100L * 1024L * 1024L
        const val UNKNOWN_SIZE_MINIMUM_FREE_BYTES = 500L * 1024L * 1024L
        private const val WAITING_FOR_NETWORK = "WAITING_FOR_NETWORK"
        const val MAX_USER_AGENT_LENGTH = 512
        const val MAX_VALIDATOR_HEADER_LENGTH = 1_024
        val ACTIVE_STATUSES = setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)
        val IPV4_LITERAL = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
    }
}
