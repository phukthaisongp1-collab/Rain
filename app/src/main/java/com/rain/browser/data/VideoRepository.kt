package com.rain.browser.data

import com.rain.browser.downloads.ManagedVideoPathPolicy
import com.rain.browser.downloads.VideoContainerValidator
import java.io.File
import java.net.URI
import java.util.UUID

enum class VideoStatus {
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    READY,
    FAILED,
    FAILED_CORRUPTED,
    CANCELED
}

data class VideoRecord(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val bytesDownloaded: Long,
    val downloadId: Long?,
    val filePath: String,
    val status: VideoStatus,
    val downloadedAt: Long,
    val expiresAt: Long?,
    val isLiked: Boolean,
    val unlikedAt: Long?,
    val lastError: String,
    val etag: String,
    val lastModified: String,
    val checksumSha256: String,
    val authorizationConfirmed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Persists RAIN-managed video metadata in the existing application database.
 * File transfer and file deletion remain owned by their dedicated components.
 */
class VideoRepository(
    private val storage: Storage,
    private val managedDirectory: File
) {
    fun enqueue(
        url: String,
        title: String,
        thumbnailUrl: String = "",
        mimeType: String = "",
        userConfirmedPermission: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): VideoRecord {
        require(userConfirmedPermission) { "User authorization confirmation is required before downloading media." }
        require(isSafeWebUrl(url)) { "Video source URL must be a valid HTTP(S) URL." }
        require(thumbnailUrl.isBlank() || isSafeWebUrl(thumbnailUrl)) {
            "Thumbnail URL must be blank or a valid HTTP(S) URL."
        }
        require(now >= 0L) { "Timestamp must not be negative." }
        val safeTitle = title.trim().take(MAX_TITLE_LENGTH).ifBlank { url }
        val record = VideoRecord(
            id = UUID.randomUUID().toString(),
            url = url,
            title = safeTitle,
            thumbnailUrl = thumbnailUrl,
            mimeType = mimeType.trim().take(MAX_MIME_LENGTH),
            durationMs = 0L,
            sizeBytes = 0L,
            bytesDownloaded = 0L,
            downloadId = null,
            filePath = "",
            status = VideoStatus.QUEUED,
            downloadedAt = 0L,
            expiresAt = null,
            isLiked = false,
            unlikedAt = null,
            lastError = "",
            etag = "",
            lastModified = "",
            checksumSha256 = "",
            authorizationConfirmed = true,
            createdAt = now,
            updatedAt = now
        )
        persist(record)
        return record
    }

    fun get(id: String): VideoRecord? = storage.get(TABLE, id)?.toRecord()

    fun list(status: VideoStatus? = null): List<VideoRecord> =
        storage.query(TABLE, orderBy = "created_at DESC")
            .mapNotNull { it.toRecord() }
            .filter { status == null || it.status == status }

    fun updateTransfer(
        id: String,
        downloadId: Long,
        bytesDownloaded: Long,
        totalBytes: Long,
        etag: String = "",
        lastModified: String = "",
        now: Long = System.currentTimeMillis()
    ): VideoRecord? {
        if (downloadId < 0L || bytesDownloaded < 0L || totalBytes < -1L || now < 0L) return null
        val current = get(id) ?: return null
        if (current.status !in setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING)) return null
        val updated = current.copy(
            downloadId = downloadId,
            bytesDownloaded = bytesDownloaded,
            sizeBytes = if (totalBytes >= 0L) totalBytes else current.sizeBytes,
            status = VideoStatus.DOWNLOADING,
            etag = etag.take(MAX_HEADER_LENGTH).ifBlank { current.etag },
            lastModified = lastModified.take(MAX_HEADER_LENGTH).ifBlank { current.lastModified },
            updatedAt = now
        )
        persist(updated)
        return updated
    }

    fun markVerifying(id: String, now: Long = System.currentTimeMillis()): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status != VideoStatus.DOWNLOADING || now < 0L) return null
        return current.copy(status = VideoStatus.VERIFYING, updatedAt = now).also(::persist)
    }

    /**
     * A file becomes READY only after it exists inside RAIN's managed directory
     * and its initial MP4/WebM container signature is recognized.
     */
    fun markReady(
        id: String,
        file: File,
        durationMs: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status !in setOf(VideoStatus.DOWNLOADING, VideoStatus.VERIFYING)) return null
        if (durationMs < 0L || now < 0L) return null
        if (!file.isFile || !ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)) {
            return failCorrupted(current, "Video file is missing or outside RAIN storage.", now, file)
        }

        val prefix = runCatching { file.inputStream().use { it.readNBytes(CONTAINER_PREFIX_BYTES) } }
            .getOrElse { return failCorrupted(current, "Unable to read video container.", now, file) }
        if (VideoContainerValidator.detect(prefix) == VideoContainerValidator.Container.UNKNOWN) {
            return failCorrupted(current, "Unrecognized or corrupted video container.", now, file)
        }

        val updated = current.copy(
            filePath = file.canonicalPath,
            durationMs = durationMs,
            sizeBytes = file.length(),
            bytesDownloaded = file.length(),
            status = VideoStatus.READY,
            downloadedAt = now,
            expiresAt = null,
            lastError = "",
            updatedAt = now
        )
        persist(updated)
        return updated
    }

    fun markCorrupted(
        id: String,
        file: File,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status !in setOf(VideoStatus.DOWNLOADING, VideoStatus.VERIFYING) || now < 0L) {
            return null
        }
        return failCorrupted(current, reason.trim().take(MAX_ERROR_LENGTH), now, file)
    }

    fun markFailed(id: String, reason: String, now: Long = System.currentTimeMillis()): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status == VideoStatus.READY || now < 0L) return null
        return current.copy(
            status = VideoStatus.FAILED,
            lastError = reason.trim().take(MAX_ERROR_LENGTH),
            updatedAt = now
        ).also(::persist)
    }

    fun setDestination(id: String, file: File, now: Long = System.currentTimeMillis()): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status != VideoStatus.QUEUED || now < 0L ||
            !ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)
        ) return null
        return current.copy(
            filePath = file.canonicalPath,
            updatedAt = now
        ).also(::persist)
    }

    fun markCanceled(id: String, now: Long = System.currentTimeMillis()): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status !in setOf(VideoStatus.QUEUED, VideoStatus.DOWNLOADING) || now < 0L) return null
        return current.copy(
            status = VideoStatus.CANCELED,
            downloadId = null,
            lastError = "",
            updatedAt = now
        ).also(::persist)
    }

    fun resetForRetry(id: String, now: Long = System.currentTimeMillis()): VideoRecord? {
        val current = get(id) ?: return null
        if (current.status !in setOf(VideoStatus.FAILED, VideoStatus.FAILED_CORRUPTED, VideoStatus.CANCELED) || now < 0L) return null
        return current.copy(
            bytesDownloaded = 0L,
            downloadId = null,
            filePath = "",
            status = VideoStatus.QUEUED,
            lastError = "",
            etag = "",
            lastModified = "",
            updatedAt = now
        ).also(::persist)
    }

    /** Removes metadata only. The managed file must be deleted by a path-validated owner. */
    fun removeMetadata(id: String): Boolean = storage.delete(TABLE, id) > 0

    private fun failCorrupted(
        current: VideoRecord,
        reason: String,
        now: Long,
        candidateFile: File? = null
    ): VideoRecord {
        val currentFile = current.filePath.takeIf { it.isNotBlank() }?.let(::File)
        val safeManagedPath = sequenceOf(candidateFile, currentFile).filterNotNull().firstNotNullOfOrNull { file ->
            file.takeIf { it.isFile && ManagedVideoPathPolicy.isManagedFile(managedDirectory, it) }
                ?.canonicalPath
        }.orEmpty()
        return current.copy(
            status = VideoStatus.FAILED_CORRUPTED,
            filePath = safeManagedPath,
            lastError = reason,
            updatedAt = now
        ).also(::persist)
    }

    private fun persist(record: VideoRecord) {
        storage.set(TABLE, record.id, mapOf(
            "url" to record.url,
            "title" to record.title,
            "thumbnail_url" to record.thumbnailUrl,
            "mime_type" to record.mimeType,
            "duration_ms" to record.durationMs.toString(),
            "size_bytes" to record.sizeBytes.toString(),
            "bytes_downloaded" to record.bytesDownloaded.toString(),
            "download_id" to record.downloadId?.toString().orEmpty(),
            "file_path" to record.filePath,
            "status" to record.status.name,
            "downloaded_at" to record.downloadedAt.toString(),
            "expires_at" to record.expiresAt?.toString().orEmpty(),
            "is_liked" to if (record.isLiked) "1" else "0",
            "unliked_at" to record.unlikedAt?.toString().orEmpty(),
            "last_error" to record.lastError,
            "etag" to record.etag,
            "last_modified" to record.lastModified,
            "checksum_sha256" to record.checksumSha256.take(64),
            "authorization_confirmed" to if (record.authorizationConfirmed) "1" else "0",
            "created_at" to record.createdAt.toString(),
            "updated_at" to record.updatedAt.toString()
        ))
    }

    private fun Map<String, String>.toRecord(): VideoRecord? = runCatching {
        VideoRecord(
            id = getValue("id"),
            url = getValue("url"),
            title = getValue("title"),
            thumbnailUrl = getValue("thumbnail_url"),
            mimeType = getValue("mime_type"),
            durationMs = getValue("duration_ms").toLong(),
            sizeBytes = getValue("size_bytes").toLong(),
            bytesDownloaded = getValue("bytes_downloaded").toLong(),
            downloadId = getValue("download_id").toLongOrNull(),
            filePath = getValue("file_path"),
            status = VideoStatus.valueOf(getValue("status")),
            downloadedAt = getValue("downloaded_at").toLong(),
            expiresAt = getValue("expires_at").toLongOrNull(),
            isLiked = getValue("is_liked") == "1",
            unlikedAt = getValue("unliked_at").toLongOrNull(),
            lastError = getValue("last_error"),
            etag = getValue("etag"),
            lastModified = getValue("last_modified"),
            checksumSha256 = getValue("checksum_sha256"),
            authorizationConfirmed = getValue("authorization_confirmed") == "1",
            createdAt = getValue("created_at").toLong(),
            updatedAt = getValue("updated_at").toLong()
        )
    }.getOrNull()

    private fun isSafeWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private companion object {
        const val TABLE = "videos"
        const val MAX_TITLE_LENGTH = 512
        const val MAX_MIME_LENGTH = 128
        const val MAX_HEADER_LENGTH = 1024
        const val MAX_ERROR_LENGTH = 512
        const val CONTAINER_PREFIX_BYTES = 32
    }
}
