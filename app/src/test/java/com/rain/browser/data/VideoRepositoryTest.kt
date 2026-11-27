package com.rain.browser.data

import com.rain.browser.downloads.VideoRetentionPolicy
import com.rain.browser.downloads.VideoRetentionCleanup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VideoRepositoryTest {
    @Test
    fun persistsQueuedTransferReadyAndLikeRetentionState() {
        val storage = FakeStorage()
        val directory = Files.createTempDirectory("rain-video-repository").toFile()
        try {
            val repository = VideoRepository(storage, directory)
            val queued = repository.enqueue(
                url = "https://media.example.test/video.mp4",
                title = " Test video ",
                now = 1_000L
            )
            assertEquals(VideoStatus.QUEUED, queued.status)
            assertEquals("Test video", queued.title)

            val downloading = repository.updateTransfer(
                id = queued.id,
                downloadId = 42L,
                bytesDownloaded = 8L,
                totalBytes = 16L,
                etag = "\"v1\"",
                now = 2_000L
            )
            assertNotNull(downloading)
            assertEquals(VideoStatus.DOWNLOADING, downloading?.status)

            val progressed = repository.updateTransfer(
                id = queued.id,
                downloadId = 42L,
                bytesDownloaded = 12L,
                totalBytes = 16L,
                now = 2_500L
            )
            assertEquals("\"v1\"", progressed?.etag)

            val file = File(directory, "video.mp4")
            file.writeBytes(
                byteArrayOf(
                    0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(),
                    'y'.code.toByte(), 'p'.code.toByte(), 0, 0, 0, 0
                )
            )
            val ready = repository.markReady(queued.id, file, durationMs = 12_000L, now = 3_000L)
            assertNotNull(ready)
            assertEquals(VideoStatus.READY, ready?.status)
            assertEquals(12_000L, ready?.durationMs)
            assertEquals(3_000L + VideoRetentionPolicy.RETENTION_MILLIS, ready?.expiresAt)

            val liked = repository.setLiked(queued.id, true, now = 4_000L)
            assertEquals(true, liked?.isLiked)
            assertNull(liked?.expiresAt)
            assertTrue(repository.findExpired(Long.MAX_VALUE).isEmpty())

            val unliked = repository.setLiked(queued.id, false, now = 5_000L)
            assertEquals(false, unliked?.isLiked)
            assertEquals(5_000L, unliked?.unlikedAt)
            assertEquals(5_000L + VideoRetentionPolicy.RETENTION_MILLIS, unliked?.expiresAt)
            assertEquals(1, repository.list().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun persistsManagedDestinationAndSupportsCancelThenRetry() {
        val storage = FakeStorage()
        val directory = Files.createTempDirectory("rain-video-retry").toFile()
        try {
            val repository = VideoRepository(storage, directory)
            val queued = repository.enqueue("https://media.example.test/video.mp4", "Retry video", now = 1L)
            val destination = File(directory, "retry.mp4")
            assertEquals(destination.canonicalPath, repository.setDestination(queued.id, destination, 2L)?.filePath)

            val downloading = repository.updateTransfer(queued.id, 99L, 12L, 100L, now = 3L)
            assertEquals(VideoStatus.DOWNLOADING, downloading?.status)
            val canceled = repository.markCanceled(queued.id, 4L)
            assertEquals(VideoStatus.CANCELED, canceled?.status)
            assertNull(canceled?.downloadId)

            val retried = repository.resetForRetry(queued.id, 5L)
            assertEquals(VideoStatus.QUEUED, retried?.status)
            assertEquals(0L, retried?.bytesDownloaded)
            assertTrue(retried?.filePath.isNullOrEmpty())
            assertNull(retried?.downloadId)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun refusesDestinationOutsideManagedDirectory() {
        val directory = Files.createTempDirectory("rain-video-root").toFile()
        val outside = Files.createTempDirectory("rain-video-outside-destination").toFile()
        try {
            val repository = VideoRepository(FakeStorage(), directory)
            val queued = repository.enqueue("https://media.example.test/video.mp4", "Video", now = 1L)
            assertNull(repository.setDestination(queued.id, File(outside, "outside.mp4"), 2L))
        } finally {
            directory.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun retentionCleanupDeletesOnlyExpiredUnlikedManagedVideo() {
        val directory = Files.createTempDirectory("rain-retention-cleanup").toFile()
        try {
            val repository = VideoRepository(FakeStorage(), directory)
            val expired = repository.enqueue("https://media.example.test/expired.mp4", "Expired", now = 1_000L)
            repository.updateTransfer(expired.id, 11L, 12L, 12L, now = 1_100L)
            val expiredFile = File(directory, "expired.mp4").apply {
                writeBytes(byteArrayOf(
                    0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(),
                    'y'.code.toByte(), 'p'.code.toByte(), 0, 0, 0, 0
                ))
            }
            repository.markReady(expired.id, expiredFile, now = 1_000L)

            val liked = repository.enqueue("https://media.example.test/liked.mp4", "Liked", now = 1_000L)
            repository.updateTransfer(liked.id, 12L, 12L, 12L, now = 1_100L)
            val likedFile = File(directory, "liked.mp4").apply {
                writeBytes(byteArrayOf(
                    0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(),
                    'y'.code.toByte(), 'p'.code.toByte(), 0, 0, 0, 0
                ))
            }
            repository.markReady(liked.id, likedFile, now = 1_000L)
            repository.setLiked(liked.id, true, now = 2_000L)

            val unrelatedFile = File(directory, "user-file.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val now = 1_000L + VideoRetentionPolicy.RETENTION_MILLIS

            assertEquals(1, VideoRetentionCleanup.removeExpired(repository, directory, now))
            assertFalse(expiredFile.exists())
            assertNull(repository.get(expired.id))
            assertTrue(likedFile.isFile)
            assertNotNull(repository.get(liked.id))
            assertTrue(unrelatedFile.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsafeSourceUrls() {
        val repository = VideoRepository(FakeStorage(), Files.createTempDirectory("rain-video-url").toFile())
        val error = runCatching {
            repository.enqueue("file:///sdcard/private.mp4", "Unsafe", now = 1L)
        }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test
    fun refusesFileOutsideManagedDirectoryAndMarksItCorrupted() {
        val storage = FakeStorage()
        val directory = Files.createTempDirectory("rain-video-managed").toFile()
        val outside = Files.createTempDirectory("rain-video-outside").toFile()
        try {
            val repository = VideoRepository(storage, directory)
            val queued = repository.enqueue("https://media.example.test/video.webm", "Video", now = 1L)
            repository.updateTransfer(queued.id, 7L, 4L, 4L, now = 2L)
            val externalFile = File(outside, "video.webm").apply {
                writeBytes(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
            }

            val result = repository.markReady(queued.id, externalFile, now = 3L)
            assertEquals(VideoStatus.FAILED_CORRUPTED, result?.status)
            assertTrue(result?.filePath.isNullOrEmpty())
        } finally {
            directory.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownContainerBeforeMarkingReady() {
        val storage = FakeStorage()
        val directory = Files.createTempDirectory("rain-video-corrupt").toFile()
        try {
            val repository = VideoRepository(storage, directory)
            val queued = repository.enqueue("https://media.example.test/video.mp4", "Video", now = 1L)
            repository.updateTransfer(queued.id, 8L, 4L, 4L, now = 2L)
            val invalid = File(directory, "invalid.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

            val result = repository.markReady(queued.id, invalid, now = 3L)
            assertEquals(VideoStatus.FAILED_CORRUPTED, result?.status)
            assertEquals(invalid.canonicalPath, result?.filePath)
            assertFalse(result?.status == VideoStatus.READY)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeStorage : Storage {
        private val tables = mutableMapOf<String, MutableMap<String, Map<String, String>>>()

        override fun get(table: String, id: String): Map<String, String>? = tables[table]?.get(id)

        override fun set(table: String, id: String, values: Map<String, String>) {
            tables.getOrPut(table) { linkedMapOf() }[id] = values + ("id" to id)
        }

        override fun query(
            table: String,
            selection: String?,
            selectionArgs: Array<String>,
            orderBy: String?,
            limit: Int?
        ): List<Map<String, String>> = tables[table]?.values?.toList().orEmpty().take(limit ?: Int.MAX_VALUE)

        override fun delete(table: String, id: String?): Int {
            val rows = tables[table] ?: return 0
            if (id == null) {
                val count = rows.size
                rows.clear()
                return count
            }
            return if (rows.remove(id) != null) 1 else 0
        }
    }
}
