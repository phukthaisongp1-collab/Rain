package com.rain.browser.downloads

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HlsDownloadCancellation {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun isCancelled(): Boolean = cancelled.get()

    internal fun throwIfCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw CancellationException("ยกเลิกการดาวน์โหลด HLS แล้ว")
        }
    }
}

/**
 * Downloads a completed HLS media playlist into RAIN-managed resumable chunks.
 * Playlist URLs are represented in resume metadata only by a SHA-256 fingerprint;
 * credentials and raw source URLs are never written to disk.
 */
class HlsSegmentDownloader(
    private val managedDirectory: File,
    private val parallelism: Int = DEFAULT_PARALLELISM
) {
    data class Progress(
        val completedSegments: Int,
        val totalSegments: Int,
        val bytesDownloaded: Long
    )

    data class DownloadResult(
        val temporaryDirectory: File,
        val segmentFiles: List<File>,
        val initializationMapFiles: List<File>,
        val completedSegments: Int,
        val totalSegments: Int,
        val bytesDownloaded: Long,
        val durationMs: Long
    )

    init {
        require(parallelism in MIN_PARALLELISM..MAX_PARALLELISM) {
            "HLS parallelism must be between 3 and 4."
        }
    }

    fun download(
        recordId: String,
        playlist: HlsParsedPlaylist.Media,
        cancellation: HlsDownloadCancellation = HlsDownloadCancellation(),
        onProgress: (Progress) -> Unit = {}
    ): DownloadResult {
        require(recordId.matches(RECORD_ID_PATTERN)) { "Invalid HLS video record ID." }
        require(playlist.endList && playlist.segments.isNotEmpty()) {
            "ไม่รองรับ HLS Live Playlist ที่ไม่มี EXT-X-ENDLIST"
        }
        require(playlist.segments.size <= HlsPlaylistParser.MAX_SEGMENTS) {
            "HLS Playlist มีจำนวน segment เกินขีดจำกัด 20,000"
        }
        ensureManagedDirectory()
        require(managedDirectory.usableSpace >= VideoDownloadCoordinator.UNKNOWN_SIZE_MINIMUM_FREE_BYTES) {
            "พื้นที่จัดเก็บไม่เพียงพอ ต้องมีพื้นที่ว่างอย่างน้อย 500 MiB"
        }

        val temporaryDirectory = File(managedDirectory, "hls-$recordId")
        if (!temporaryDirectory.exists()) {
            check(temporaryDirectory.mkdir()) { "ไม่สามารถสร้างพื้นที่ชั่วคราวสำหรับ HLS ได้" }
        }
        check(ManagedVideoPathPolicy.isManagedFile(managedDirectory, temporaryDirectory) &&
            temporaryDirectory.isDirectory
        ) { "ตำแหน่งไฟล์ชั่วคราว HLS ไม่ปลอดภัย" }

        val manifestFile = File(temporaryDirectory, RESUME_MANIFEST)
        val properties = loadResumeProperties(manifestFile)
        val fingerprint = playlistFingerprint(playlist)
        if (properties.getProperty("playlist.fingerprint") != fingerprint) {
            clearManagedTemporaryFiles(temporaryDirectory, manifestFile)
            properties.clear()
            properties.setProperty("playlist.fingerprint", fingerprint)
            saveResumeProperties(manifestFile, properties)
        }

        val byteCount = AtomicLong(0L)
        val budgetLock = Any()
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        val completedSegments = AtomicLong(0L)
        val segmentFiles = playlist.segments.indices.map { index ->
            File(temporaryDirectory, segmentFileName(index))
        }

        segmentFiles.forEachIndexed { index, file ->
            cancellation.throwIfCancelled()
            val hash = properties.getProperty(segmentHashProperty(index))
            val savedBytes = properties.getProperty(segmentBytesProperty(index))?.toLongOrNull()
            if (isVerifiedChunk(file, hash) && savedBytes != null && savedBytes > 0L) {
                byteCount.addAndGet(savedBytes)
                completedSegments.incrementAndGet()
            } else {
                file.delete()
                properties.remove(segmentHashProperty(index))
                properties.remove(segmentBytesProperty(index))
            }
        }
        require(byteCount.get() <= VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES) {
            "วิดีโอมีขนาดเกินขีดจำกัด 4 GB"
        }
        onProgress(
            Progress(
                completedSegments = completedSegments.get().toInt(),
                totalSegments = playlist.segments.size,
                bytesDownloaded = byteCount.get()
            )
        )

        val maps = playlist.segments.mapNotNull { it.initializationMap }
            .distinctBy(::mapFingerprint)
        val mapFiles = mutableListOf<File>()
        maps.forEach { map ->
            cancellation.throwIfCancelled()
            val mapId = mapFingerprint(map)
            val mapFile = File(temporaryDirectory, "map-$mapId.bin")
            val hash = properties.getProperty(mapHashProperty(mapId))
            val savedBytes = properties.getProperty(mapBytesProperty(mapId))?.toLongOrNull()
            if (isVerifiedChunk(mapFile, hash) && savedBytes != null && savedBytes > 0L) {
                val updated = byteCount.addAndGet(savedBytes)
                require(updated <= VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES) {
                    "วิดีโอมีขนาดเกินขีดจำกัด 4 GB"
                }
            } else {
                mapFile.delete()
                properties.remove(mapHashProperty(mapId))
                properties.remove(mapBytesProperty(mapId))
                val firstSegment = playlist.segments.first {
                    it.initializationMap?.let(::mapFingerprint) == mapId
                }
                val downloadedBytes = downloadResourceWithRetry(
                    url = map.url,
                    destination = mapFile,
                    encryptionKey = map.key,
                    sequence = firstSegment.sequence,
                    cancellation = cancellation,
                    byteCount = byteCount,
                    budgetLock = budgetLock,
                    keyCache = keyCache,
                    maximumBytes = VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES
                )
                properties.setProperty(mapHashProperty(mapId), sha256(mapFile))
                properties.setProperty(mapBytesProperty(mapId), downloadedBytes.toString())
                saveResumeProperties(manifestFile, properties)
            }
            mapFiles += mapFile
        }

        val executor = Executors.newFixedThreadPool(parallelism)
        val futures = mutableListOf<Future<Unit>>()
        try {
            playlist.segments.forEachIndexed { index, segment ->
                if (isVerifiedChunk(
                        segmentFiles[index],
                        properties.getProperty(segmentHashProperty(index))
                    )
                ) {
                    return@forEachIndexed
                }
                futures += executor.submit(Callable {
                    cancellation.throwIfCancelled()
                    val destination = segmentFiles[index]
                    val downloadedBytes = downloadResourceWithRetry(
                        url = segment.url,
                        destination = destination,
                        encryptionKey = segment.key,
                        sequence = segment.sequence,
                        cancellation = cancellation,
                        byteCount = byteCount,
                        budgetLock = budgetLock,
                        keyCache = keyCache,
                        maximumBytes = VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES
                    )
                    synchronized(properties) {
                        properties.setProperty(segmentHashProperty(index), sha256(destination))
                        properties.setProperty(segmentBytesProperty(index), downloadedBytes.toString())
                        saveResumeProperties(manifestFile, properties)
                    }
                    val completed = completedSegments.incrementAndGet().toInt()
                    onProgress(
                        Progress(
                            completedSegments = completed,
                            totalSegments = playlist.segments.size,
                            bytesDownloaded = byteCount.get()
                        )
                    )
                    Unit
                })
            }

            futures.forEach { future ->
                cancellation.throwIfCancelled()
                future.get()
            }
            cancellation.throwIfCancelled()
        } catch (error: Exception) {
            futures.forEach { it.cancel(true) }
            throw unwrapExecutionError(error)
        } finally {
            executor.shutdownNow()
        }

        require(segmentFiles.all { it.isFile && it.length() > 0L }) {
            "HLS segment download did not produce every media chunk."
        }
        require(byteCount.get() <= VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES) {
            "วิดีโอมีขนาดเกินขีดจำกัด 4 GB"
        }
        onProgress(
            Progress(
                completedSegments = playlist.segments.size,
                totalSegments = playlist.segments.size,
                bytesDownloaded = byteCount.get()
            )
        )
        val durationMs = playlist.segments.sumOf { (it.durationSeconds * 1_000.0).toLong() }
        return DownloadResult(
            temporaryDirectory = temporaryDirectory,
            segmentFiles = segmentFiles,
            initializationMapFiles = mapFiles,
            completedSegments = playlist.segments.size,
            totalSegments = playlist.segments.size,
            bytesDownloaded = byteCount.get(),
            durationMs = durationMs
        )
    }

    fun deleteTemporaryFiles(recordId: String): Boolean {
        if (!recordId.matches(RECORD_ID_PATTERN)) return false
        val directory = File(managedDirectory, "hls-$recordId")
        if (!directory.exists()) return true
        if (!directory.isDirectory ||
            !ManagedVideoPathPolicy.isManagedFile(managedDirectory, directory)
        ) return false
        val files = directory.listFiles() ?: return false
        if (files.any { !it.isFile || !ManagedVideoPathPolicy.isManagedFile(directory, it) }) {
            return false
        }
        val deleted = files.all { it.delete() }
        return deleted && directory.delete()
    }

    private fun downloadResourceWithRetry(
        url: String,
        destination: File,
        encryptionKey: HlsEncryptionKey?,
        sequence: Long,
        cancellation: HlsDownloadCancellation,
        byteCount: AtomicLong,
        budgetLock: Any,
        keyCache: ConcurrentHashMap<String, ByteArray>,
        maximumBytes: Long
    ): Long {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            cancellation.throwIfCancelled()
            val attemptBytes = AtomicLong(0L)
            val encryptedFile = File(destination.parentFile, "\${destination.name}.part")
            try {
                encryptedFile.delete()
                val response = HlsHttpClient.downloadToFile(
                    rawUrl = url,
                    destination = encryptedFile,
                    maximumBytes = maximumBytes
                ) { delta ->
                    cancellation.throwIfCancelled()
                    synchronized(budgetLock) {
                        val next = byteCount.get() + delta
                        require(next <= VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES) {
                            "วิดีโอมีขนาดเกินขีดจำกัด 4 GB"
                        }
                        byteCount.set(next)
                        attemptBytes.addAndGet(delta)
                    }
                }
                require(response.bytesWritten > 0L) { "HLS resource is empty." }
                cancellation.throwIfCancelled()

                if (encryptionKey != null) {
                    val key = resolveKey(encryptionKey, keyCache)
                    val iv = encryptionKey.iv ?: sequenceIv(sequence)
                    decryptAes128(encryptedFile, destination, key, iv)
                    check(encryptedFile.delete()) { "Unable to remove encrypted HLS temporary data." }
                } else {
                    if (destination.exists()) check(destination.delete()) {
                        "Unable to replace an old HLS temporary chunk."
                    }
                    check(encryptedFile.renameTo(destination)) {
                        "Unable to finalize HLS temporary chunk."
                    }
                }
                check(ManagedVideoPathPolicy.isManagedFile(managedDirectory, destination)) {
                    "HLS output escaped RAIN-managed storage."
                }
                return response.bytesWritten
            } catch (error: Exception) {
                synchronized(budgetLock) {
                    byteCount.addAndGet(-attemptBytes.get())
                }
                runCatching { encryptedFile.delete() }
                runCatching { destination.delete() }
                if (error is CancellationException || Thread.currentThread().isInterrupted) throw error
                lastError = error
                if (attempt + 1 < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_BASE_DELAY_MILLIS * (1L shl attempt))
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw CancellationException("ยกเลิกการดาวน์โหลด HLS แล้ว")
                    }
                }
            }
        }
        throw IllegalStateException(
            "ดาวน์โหลด HLS segment ไม่สำเร็จหลังลองครบ 3 ครั้ง: \${lastError?.message.orEmpty()}",
            lastError
        )
    }

    private fun resolveKey(
        encryptionKey: HlsEncryptionKey,
        keyCache: ConcurrentHashMap<String, ByteArray>
    ): ByteArray {
        require(encryptionKey.method == "AES-128") {
            "ไม่รองรับ HLS encryption method: \${encryptionKey.method}"
        }
        val keyUrl = encryptionKey.url
            ?: throw IllegalArgumentException("HLS AES-128 key ไม่มี URL")
        val keyId = sha256(keyUrl.toByteArray(Charsets.UTF_8))
        return keyCache[keyId] ?: synchronized(keyCache) {
            keyCache[keyId] ?: run {
                val bytes = HlsHttpClient.get(keyUrl, AES_KEY_BYTES).body
                require(bytes.size == AES_KEY_BYTES) {
                    "HLS AES-128 key ต้องมีขนาด 16 bytes"
                }
                keyCache[keyId] = bytes
                bytes
            }
        }
    }

    private fun decryptAes128(
        encryptedFile: File,
        destination: File,
        key: ByteArray,
        iv: ByteArray
    ) {
        require(key.size == AES_KEY_BYTES && iv.size == AES_KEY_BYTES) {
            "HLS AES-128 key หรือ IV มีขนาดไม่ถูกต้อง"
        }
        require(encryptedFile.length() > 0L && encryptedFile.length() % AES_KEY_BYTES == 0L) {
            "HLS AES-128 encrypted resource มีขนาดไม่ถูกต้อง"
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        try {
            FileInputStream(encryptedFile).use { input ->
                FileOutputStream(destination, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val decrypted = cipher.update(buffer, 0, count)
                        if (decrypted != null && decrypted.isNotEmpty()) output.write(decrypted)
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes.isNotEmpty()) output.write(finalBytes)
                    output.fd.sync()
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw IllegalArgumentException("HLS AES-128 decryption failed.", error)
        }
    }

    private fun sequenceIv(sequence: Long): ByteArray =
        ByteBuffer.allocate(AES_KEY_BYTES).putLong(0L).putLong(sequence).array()

    private fun ensureManagedDirectory() {
        if (!managedDirectory.exists()) {
            check(managedDirectory.mkdirs()) { "Unable to create RAIN video storage." }
        }
        check(managedDirectory.isDirectory && managedDirectory.canWrite()) {
            "RAIN video storage is not writable."
        }
    }

    private fun loadResumeProperties(file: File): Properties {
        val properties = Properties()
        if (!file.exists()) return properties
        check(ManagedVideoPathPolicy.isManagedFile(File(file.parent), file)) {
            "HLS resume manifest is outside its managed directory."
        }
        FileInputStream(file).use(properties::load)
        return properties
    }

    private fun saveResumeProperties(file: File, properties: Properties) {
        val temporary = File(file.parentFile, "\$RESUME_MANIFEST.tmp")
        FileOutputStream(temporary, false).use { output ->
            properties.store(output, "RAIN HLS resume metadata; no source URLs or credentials")
            output.fd.sync()
        }
        if (file.exists()) check(file.delete()) { "Unable to replace HLS resume metadata." }
        check(temporary.renameTo(file)) { "Unable to save HLS resume metadata." }
    }

    private fun clearManagedTemporaryFiles(directory: File, manifestFile: File) {
        val files = directory.listFiles() ?: throw IllegalStateException("Unable to inspect HLS temporary directory.")
        files.forEach { file ->
            require(file.isFile && ManagedVideoPathPolicy.isManagedFile(directory, file)) {
                "Unexpected path in HLS temporary directory."
            }
            check(file.delete()) { "Unable to clear stale HLS temporary data." }
        }
        if (manifestFile.exists()) manifestFile.delete()
    }

    private fun isVerifiedChunk(file: File, expectedHash: String?): Boolean =
        expectedHash?.matches(SHA256_PATTERN) == true &&
            file.isFile &&
            ManagedVideoPathPolicy.isManagedFile(File(file.parent), file) &&
            runCatching { sha256(file) == expectedHash }.getOrDefault(false)

    private fun playlistFingerprint(playlist: HlsParsedPlaylist.Media): String {
        val digest = MessageDigest.getInstance("SHA-256")
        playlist.segments.forEach { segment ->
            digest.update(segment.url.toByteArray(Charsets.UTF_8))
            digest.update(segment.sequence.toString().toByteArray(Charsets.UTF_8))
            digest.update(segment.durationSeconds.toString().toByteArray(Charsets.UTF_8))
            digest.update(encryptionFingerprint(segment.key).toByteArray(Charsets.UTF_8))
            segment.initializationMap?.let { map ->
                digest.update(map.url.toByteArray(Charsets.UTF_8))
                digest.update(encryptionFingerprint(map.key).toByteArray(Charsets.UTF_8))
            }
        }
        return digest.digest().toHex()
    }

    private fun mapFingerprint(map: HlsInitializationMap): String =
        sha256((map.url + "|" + encryptionFingerprint(map.key)).toByteArray(Charsets.UTF_8))

    private fun encryptionFingerprint(key: HlsEncryptionKey?): String =
        if (key == null) "none" else {
            "\${key.method}|\${key.url.orEmpty()}|\${key.iv?.toHex().orEmpty()}"
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun segmentFileName(index: Int): String = "segment-%05d.bin".format(index)
    private fun segmentHashProperty(index: Int): String = "segment.$index.sha256"
    private fun segmentBytesProperty(index: Int): String = "segment.$index.bytes"
    private fun mapHashProperty(id: String): String = "map.$id.sha256"
    private fun mapBytesProperty(id: String): String = "map.$id.bytes"

    private fun unwrapExecutionError(error: Exception): Exception =
        if (error is java.util.concurrent.ExecutionException) {
            (error.cause as? Exception) ?: error
        } else {
            error
        }

    private companion object {
        const val DEFAULT_PARALLELISM = 4
        const val MIN_PARALLELISM = 3
        const val MAX_PARALLELISM = 4
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MILLIS = 500L
        const val AES_KEY_BYTES = 16
        const val RESUME_MANIFEST = "resume.properties"
        val RECORD_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
    }
}
