package com.rain.browser.downloads

import com.rain.browser.data.HLS_MEDIA_MIME_TYPE
import com.rain.browser.data.Storage
import com.rain.browser.data.VideoRepository
import com.rain.browser.data.VideoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HlsOfflineRegressionTest {

    @Test
    fun parsesMasterVariantsAndResolvesRelativeUrls() {
        val content = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            video/720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=640000,RESOLUTION=640x360
            video/360/index.m3u8
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse(
            "https://media.example/catalog/master.m3u8",
            content
        ) as HlsParsedPlaylist.Master

        assertEquals(2, parsed.variants.size)
        assertEquals("https://media.example/catalog/video/720/index.m3u8", parsed.variants[0].url)
        assertEquals(1280000L, parsed.variants[0].bandwidth)
        assertEquals(1280, parsed.variants[0].width)
        assertEquals(720, parsed.variants[0].height)
        assertTrue(parsed.variants[0].codecs.contains("mp4a.40.2"))
        assertEquals("https://media.example/catalog/video/360/index.m3u8", parsed.variants[1].url)
    }

    @Test
    fun parsesMediaSequenceAes128AndInitializationMap() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-MEDIA-SEQUENCE:41
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/key.bin",IV=0x0000000000000000000000000000002a
            #EXT-X-MAP:URI="../init/video.mp4"
            #EXTINF:4.0,
            segment-41.m4s
            #EXTINF:5.5,
            segment-42.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse(
            "https://media.example/hls/720/index.m3u8",
            content
        ) as HlsParsedPlaylist.Media

        assertTrue(parsed.endList)
        assertEquals(41L, parsed.mediaSequence)
        assertEquals(2, parsed.segments.size)
        assertEquals(41L, parsed.segments[0].sequence)
        assertEquals(42L, parsed.segments[1].sequence)
        assertEquals(4.0, parsed.segments[0].durationSeconds, 0.0)
        assertEquals(5.5, parsed.segments[1].durationSeconds, 0.0)

        val key = parsed.segments[0].key
        assertNotNull(key)
        assertEquals("AES-128", key!!.method)
        assertEquals("https://media.example/hls/keys/key.bin", key.url)
        assertEquals(16, key.iv!!.size)
        assertEquals(0x2a.toByte(), key.iv!!.last())

        val map = parsed.segments[0].initializationMap
        assertNotNull(map)
        assertEquals("https://media.example/hls/init/video.mp4", map!!.url)
        assertEquals("AES-128", map.key!!.method)
        assertEquals(map, parsed.segments[1].initializationMap)
    }

    @Test
    fun supportsExplicitEncryptionRemovalWithMethodNone() {
        val content = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:4,
            encrypted.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4,
            clear.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse("https://media.example/list.m3u8", content)
            as HlsParsedPlaylist.Media

        assertNotNull(parsed.segments[0].key)
        assertNull(parsed.segments[1].key)
    }

    @Test
    fun rejectsLivePlaylistWithoutEndList() {
        val content = """
            #EXTM3U
            #EXTINF:4,
            segment.ts
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/live.m3u8", content)
        }
        assertTrue(error.message.orEmpty().contains("EXT-X-ENDLIST"))
    }

    @Test
    fun rejectsSampleAesAndNonIdentityKeyFormats() {
        val sampleAes = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="key.bin"
            #EXTINF:4,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val drmKeyFormat = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,KEYFORMAT="com.example.drm",URI="key.bin"
            #EXTINF:4,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/list.m3u8", sampleAes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/list.m3u8", drmKeyFormat)
        }
    }

    @Test
    fun rejectsByteRangesAndInvalidAesIv() {
        val byteRange = """
            #EXTM3U
            #EXTINF:4,
            #EXT-X-BYTERANGE:100@0
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val invalidIv = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x1234
            #EXTINF:4,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/list.m3u8", byteRange)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/list.m3u8", invalidIv)
        }
    }

    @Test
    fun rejectsSeparateAudioRenditionsAndLowLatencyParts() {
        val separateAudio = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1000000
            video.m3u8
        """.trimIndent()
        val lowLatency = """
            #EXTM3U
            #EXT-X-PART:DURATION=0.2,URI="part.m4s"
            #EXTINF:4,
            segment.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/master.m3u8", separateAudio)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/media.m3u8", lowLatency)
        }
    }

    @Test
    fun enforcesMaximumSegmentCount() {
        val content = buildString {
            appendLine("#EXTM3U")
            repeat(HlsPlaylistParser.MAX_SEGMENTS + 1) { index ->
                appendLine("#EXTINF:1,")
                appendLine("segment-$index.ts")
            }
            appendLine("#EXT-X-ENDLIST")
        }

        assertThrows(IllegalArgumentException::class.java) {
            HlsPlaylistParser.parse("https://media.example/list.m3u8", content)
        }
    }

    @Test
    fun rejectsCredentialTokenAndLocalNetworkPlaylistUrls() {
        val rejectedUrls = listOf(
            "https://user:pass@media.example/list.m3u8",
            "https://media.example/list.m3u8?access_token=secret",
            "https://media.example/list.m3u8?X-Amz-Signature=secret",
            "http://localhost/list.m3u8",
            "file:///sdcard/list.m3u8"
        )

        rejectedUrls.forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                HlsPlaylistUrlPolicy.validateHttpUrl(url)
            }
        }
    }

    @Test
    fun detectsPlaylistUrlsInPathAndQueryWithoutGuessingOtherResources() {
        assertTrue(HlsPlaylistUrlPolicy.isCandidate("https://cdn.example/live/INDEX.M3U8"))
        assertTrue(
            HlsPlaylistUrlPolicy.isCandidate(
                "https://cdn.example/proxy?source=https%3A%2F%2Fmedia.example%2Fmaster.m3u8"
            )
        )
        assertFalse(HlsPlaylistUrlPolicy.isCandidate("https://cdn.example/video.mp4"))
        assertFalse(HlsPlaylistUrlPolicy.isCandidate("blob:https://cdn.example/123"))
    }

    @Test
    fun registryDeduplicatesCapsEntriesAndClearsOnPageChange() {
        val registry = HlsPlaylistRegistry(maximumEntries = 2)

        registry.add("https://cdn.example/a.m3u8")
        registry.add("https://cdn.example/a.m3u8")
        registry.add("https://cdn.example/b.m3u8")
        registry.add("https://cdn.example/c.m3u8")

        assertEquals(
            listOf("https://cdn.example/a.m3u8", "https://cdn.example/b.m3u8"),
            registry.snapshot()
        )

        registry.clear()
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun containerValidationRequiresARecognizedSignature() {
        val mp4 = ByteArray(32).also {
            it[4] = 'f'.code.toByte()
            it[5] = 't'.code.toByte()
            it[6] = 'y'.code.toByte()
            it[7] = 'p'.code.toByte()
        }
        val webm = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

        assertEquals(VideoContainerValidator.Container.MP4, VideoContainerValidator.detect(mp4))
        assertEquals(VideoContainerValidator.Container.WEBM, VideoContainerValidator.detect(webm))
        assertEquals(
            VideoContainerValidator.Container.UNKNOWN,
            VideoContainerValidator.detect(byteArrayOf(1, 2, 3))
        )
    }

    @Test
    fun managedPathPolicyRejectsRootTraversalAndExternalFiles() {
        val root = Files.createTempDirectory("rain-managed-video").toFile()
        val nested = root.resolve("hls-record/segment.ts")
        val outside = root.parentFile.resolve("outside-video.ts")
        try {
            assertTrue(ManagedVideoPathPolicy.isManagedFile(root, nested))
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, root))
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, root.resolve("../outside-video.ts")))
            assertFalse(ManagedVideoPathPolicy.isManagedFile(root, outside))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun repositoryCoversHlsProgressReadyLikeResumeAndExpiryLifecycle() {
        val root = Files.createTempDirectory("rain-hls-repository").toFile()
        val repository = VideoRepository(InMemoryStorage(), root)
        try {
            val queued = repository.enqueue(
                url = "https://media.example/master.m3u8",
                title = "Regression HLS",
                mimeType = HLS_MEDIA_MIME_TYPE,
                userConfirmedPermission = true,
                now = 1L
            )
            assertEquals(VideoStatus.QUEUED, queued.status)

            val downloading = repository.startHlsTransfer(queued.id, now = 2L)!!
            assertEquals(VideoStatus.DOWNLOADING, downloading.status)
            assertNull(downloading.downloadId)

            repository.updateHlsProgress(queued.id, 100L, now = 3L)
            val monotonic = repository.updateHlsProgress(queued.id, 50L, now = 4L)!!
            assertEquals(100L, monotonic.bytesDownloaded)

            assertEquals(VideoStatus.VERIFYING, repository.markVerifying(queued.id, now = 5L)!!.status)

            val output = root.resolve("rain-video-${queued.id}.mp4")
            output.writeBytes(ByteArray(32).also {
                it[4] = 'f'.code.toByte()
                it[5] = 't'.code.toByte()
                it[6] = 'y'.code.toByte()
                it[7] = 'p'.code.toByte()
            })
            val ready = repository.markReady(queued.id, output, durationMs = 5_000L, now = 6L)!!
            assertEquals(VideoStatus.READY, ready.status)
            assertEquals(output.canonicalPath, ready.filePath)
            assertEquals(output.length(), ready.sizeBytes)

            val checksum = "a".repeat(64)
            assertEquals(checksum, repository.updateChecksumSha256(queued.id, checksum, now = 7L)!!.checksumSha256)
            assertTrue(repository.like(queued.id, now = 8L)!!.expiresAt == null)
            val unliked = repository.unlike(queued.id, now = 9L)!!
            assertEquals(9L + 30L * 24L * 60L * 60L * 1000L, unliked.expiresAt)

            val resumed = repository.updatePlaybackState(queued.id, 4_000L, now = 10L)!!
            assertEquals(4_000L, resumed.playbackPositionMs)
            assertEquals(0L, repository.markPlaybackCompleted(queued.id, now = 11L)!!.playbackPositionMs)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun repositoryRejectsHlsProgressAboveSharedFourGigabyteLimit() {
        val root = Files.createTempDirectory("rain-hls-cap").toFile()
        val repository = VideoRepository(InMemoryStorage(), root)
        try {
            val record = repository.enqueue(
                url = "https://media.example/master.m3u8",
                title = "Cap regression",
                mimeType = HLS_MEDIA_MIME_TYPE,
                userConfirmedPermission = true,
                now = 1L
            )
            repository.startHlsTransfer(record.id, now = 2L)

            assertNull(
                repository.updateHlsProgress(
                    record.id,
                    VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES + 1L,
                    now = 3L
                )
            )
            assertEquals(
                4L * 1024L * 1024L * 1024L,
                VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun repositoryCancelsAndRetriesHlsWithoutDownloadManagerId() {
        val root = Files.createTempDirectory("rain-hls-retry").toFile()
        val repository = VideoRepository(InMemoryStorage(), root)
        try {
            val record = repository.enqueue(
                url = "https://media.example/master.m3u8",
                title = "Retry regression",
                mimeType = HLS_MEDIA_MIME_TYPE,
                userConfirmedPermission = true,
                now = 1L
            )
            repository.startHlsTransfer(record.id, now = 2L)
            val canceled = repository.markHlsCanceled(record.id, now = 3L)!!
            assertEquals(VideoStatus.CANCELED, canceled.status)
            assertNull(canceled.downloadId)

            val retry = repository.resetForRetry(record.id, now = 4L)!!
            assertEquals(VideoStatus.QUEUED, retry.status)
            assertEquals(0L, retry.bytesDownloaded)
        } finally {
            root.deleteRecursively()
        }
    }

    private class InMemoryStorage : Storage {
        private val tables = mutableMapOf<String, LinkedHashMap<String, Map<String, String>>>()

        override fun get(table: String, id: String): Map<String, String>? =
            tables[table]?.get(id)?.toMap()

        override fun set(table: String, id: String, values: Map<String, String>) {
            tables.getOrPut(table) { linkedMapOf() }[id] = values + ("id" to id)
        }

        override fun query(
            table: String,
            selection: String?,
            selectionArgs: Array<String>,
            orderBy: String?,
            limit: Int?
        ): List<Map<String, String>> = tables[table]?.values?.map { it.toMap() }.orEmpty()

        override fun delete(table: String, id: String?): Int {
            val rows = tables[table] ?: return 0
            return if (id == null) {
                val count = rows.size
                rows.clear()
                count
            } else {
                if (rows.remove(id) != null) 1 else 0
            }
        }
    }
}
