package com.rain.browser.downloads

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Combines a completed HLS transport stream or fragmented MP4 playlist into
 * one MP4 file using Android's platform media stack.
 *
 * This class does not fetch network resources. All inputs must already be
 * verified RAIN-managed files produced by HlsSegmentDownloader.
 */
class HlsMediaRemuxer(
    private val managedDirectory: File
) {
    data class RemuxResult(
        val file: File,
        val durationMs: Long,
        val sizeBytes: Long
    )

    fun remux(
        recordId: String,
        playlist: HlsParsedPlaylist.Media,
        download: HlsSegmentDownloader.DownloadResult
    ): RemuxResult {
        require(recordId.matches(RECORD_ID_PATTERN)) { "Invalid HLS video record ID." }
        require(playlist.endList && playlist.segments.isNotEmpty()) {
            "ไม่รองรับ HLS Live Playlist ที่ไม่มี EXT-X-ENDLIST"
        }
        ensureManagedDirectory()
        require(download.temporaryDirectory.isDirectory &&
            ManagedVideoPathPolicy.isManagedFile(managedDirectory, download.temporaryDirectory)
        ) { "HLS temporary directory is outside RAIN-managed storage." }
        require(download.segmentFiles.size == playlist.segments.size &&
            download.segmentFiles.all(::isManagedNonEmptyFile)
        ) { "HLS media segments are incomplete or outside RAIN storage." }
        require(download.initializationMapFiles.all(::isManagedNonEmptyFile)) {
            "HLS initialization map is missing or outside RAIN storage."
        }

        val mapCount = download.initializationMapFiles.size
        require(mapCount <= 1) {
            "ไม่รองรับ HLS ที่เปลี่ยน Initialization Map ระหว่างวิดีโอ"
        }
        require(
            if (mapCount == 0) playlist.segments.all { it.initializationMap == null }
            else playlist.segments.all { it.initializationMap != null }
        ) { "HLS Playlist มีรูปแบบ segment และ Initialization Map ปะปนกัน" }

        val isFragmentedMp4 = mapCount == 1
        val assembledInput = File(
            download.temporaryDirectory,
            if (isFragmentedMp4) "remux-input.mp4" else "remux-input.ts"
        )
        require(ManagedVideoPathPolicy.isManagedFile(download.temporaryDirectory, assembledInput)) {
            "HLS remux input path is outside the temporary directory."
        }

        val outputPart = File(managedDirectory, "rain-video-$recordId.mp4.part")
        val outputFile = File(managedDirectory, "rain-video-$recordId.mp4")
        require(ManagedVideoPathPolicy.isManagedFile(managedDirectory, outputPart) &&
            ManagedVideoPathPolicy.isManagedFile(managedDirectory, outputFile)
        ) { "HLS MP4 output path is outside RAIN storage." }
        require(!outputFile.exists()) { "HLS MP4 destination already exists." }

        try {
            assembleInput(download, assembledInput, isFragmentedMp4)
            remuxWithPlatformExtractor(assembledInput, outputPart)
            validateOutput(outputPart)
            check(outputPart.renameTo(outputFile)) {
                "Unable to finalize the HLS MP4 output file."
            }
            return RemuxResult(
                file = outputFile,
                durationMs = VideoMediaInspector.inspect(outputFile).durationMs,
                sizeBytes = outputFile.length()
            )
        } catch (error: Exception) {
            if (outputPart.exists() && ManagedVideoPathPolicy.isManagedFile(managedDirectory, outputPart)) {
                outputPart.delete()
            }
            if (outputFile.exists() && ManagedVideoPathPolicy.isManagedFile(managedDirectory, outputFile)) {
                outputFile.delete()
            }
            throw if (error is IllegalArgumentException || error is IllegalStateException) {
                error
            } else {
                IllegalArgumentException(
                    "ไม่สามารถรวม HLS เป็นไฟล์ MP4 ได้: ${error.message.orEmpty()}",
                    error
                )
            }
        } finally {
            if (assembledInput.exists() &&
                ManagedVideoPathPolicy.isManagedFile(download.temporaryDirectory, assembledInput)
            ) {
                assembledInput.delete()
            }
        }
    }

    private fun assembleInput(
        download: HlsSegmentDownloader.DownloadResult,
        destination: File,
        isFragmentedMp4: Boolean
    ) {
        FileOutputStream(destination, false).use { output ->
            if (isFragmentedMp4) {
                FileInputStream(download.initializationMapFiles.single()).use { input ->
                    input.copyTo(output, COPY_BUFFER_BYTES)
                }
            }
            download.segmentFiles.forEach { segment ->
                FileInputStream(segment).use { input ->
                    input.copyTo(output, COPY_BUFFER_BYTES)
                }
            }
            output.fd.sync()
        }
        require(destination.isFile && destination.length() > 0L &&
            ManagedVideoPathPolicy.isManagedFile(download.temporaryDirectory, destination)
        ) { "HLS assembled media input is empty or outside RAIN storage." }
    }

    private fun remuxWithPlatformExtractor(inputFile: File, outputFile: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(inputFile.absolutePath)
            require(extractor.trackCount > 0) {
                "Android ไม่สามารถอ่าน track จาก HLS media ได้"
            }

            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val trackMap = IntArray(extractor.trackCount) { -1 }
            var hasVideoTrack = false

            for (sourceTrack in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(sourceTrack)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                require(mime.startsWith("video/") || mime.startsWith("audio/")) {
                    "ไม่รองรับ HLS track type: $mime"
                }
                if (mime.startsWith("video/")) hasVideoTrack = true
                trackMap[sourceTrack] = try {
                    muxer.addTrack(format)
                } catch (error: Exception) {
                    throw IllegalArgumentException(
                        "ไม่รองรับ codec สำหรับการรวมไฟล์ออฟไลน์: $mime",
                        error
                    )
                }
                extractor.selectTrack(sourceTrack)
            }
            require(hasVideoTrack) { "HLS media ไม่มี video track" }

            try {
                muxer.start()
                muxerStarted = true
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "ไม่รองรับ codec หรือ media format นี้สำหรับ MP4 remux",
                    error
                )
            }

            val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE_BUFFER_BYTES)
            val bufferInfo = MediaCodec.BufferInfo()
            val lastPresentationTimes = LongArray(extractor.trackCount) { -1L }
            var sampleCount = 0L

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                require(sampleSize <= buffer.capacity()) {
                    "HLS มี media sample ใหญ่เกินขีดจำกัดที่รองรับ"
                }
                val sourceTrack = extractor.sampleTrackIndex
                require(sourceTrack in trackMap.indices && trackMap[sourceTrack] >= 0) {
                    "HLS media sample อ้างอิง track ที่ไม่รองรับ"
                }
                val presentationTimeUs = extractor.sampleTime
                require(presentationTimeUs >= 0L &&
                    presentationTimeUs >= lastPresentationTimes[sourceTrack]
                ) { "HLS media timestamp ไม่ต่อเนื่องหรือย้อนกลับ" }

                val sampleFlags = extractor.sampleFlags
                bufferInfo.set(
                    0,
                    sampleSize,
                    presentationTimeUs,
                    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                )
                buffer.position(0)
                buffer.limit(sampleSize)
                muxer.writeSampleData(trackMap[sourceTrack], buffer, bufferInfo)
                lastPresentationTimes[sourceTrack] = presentationTimeUs
                sampleCount++
                require(outputFile.length() <= VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES) {
                    "วิดีโอมีขนาดเกินขีดจำกัด 4 GB"
                }
                extractor.advance()
            }

            require(sampleCount > 0L) { "HLS media ไม่มี sample สำหรับสร้างไฟล์ MP4" }
            muxer.stop()
            muxerStarted = false
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun validateOutput(file: File) {
        require(isManagedNonEmptyFile(file)) { "HLS MP4 output is missing or empty." }
        require(file.length() <= VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES) {
            "วิดีโอมีขนาดเกินขีดจำกัด 4 GB"
        }
        val prefix = FileInputStream(file).use { it.readNBytes(CONTAINER_PREFIX_BYTES) }
        require(VideoContainerValidator.detect(prefix) == VideoContainerValidator.Container.MP4) {
            "HLS remux output does not have a valid MP4 container signature."
        }
        try {
            VideoMediaInspector.inspect(file)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "ไฟล์ MP4 ที่รวมจาก HLS ไม่สมบูรณ์หรือไม่สามารถถอดรหัสภาพได้",
                error
            )
        }
    }

    private fun isManagedNonEmptyFile(file: File): Boolean =
        file.isFile && file.length() > 0L &&
            ManagedVideoPathPolicy.isManagedFile(managedDirectory, file)

    private fun ensureManagedDirectory() {
        if (!managedDirectory.exists()) {
            check(managedDirectory.mkdirs()) { "Unable to create RAIN video storage." }
        }
        check(managedDirectory.isDirectory && managedDirectory.canWrite()) {
            "RAIN video storage is not writable."
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MAX_SAMPLE_BUFFER_BYTES = 16 * 1024 * 1024
        const val CONTAINER_PREFIX_BYTES = 32
        val RECORD_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
