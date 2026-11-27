package com.rain.browser.downloads

import android.media.MediaMetadataRetriever
import java.io.File

/** Uses Android's media stack to verify that a completed file is actually parseable. */
object VideoMediaInspector {
    data class Metadata(val durationMs: Long)

    fun inspect(file: File): Metadata {
        require(file.isFile && file.length() > 0L) { "Video file is missing or empty." }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()
                ?: throw IllegalArgumentException("Video duration metadata is unavailable.")
            require(duration > 0L) { "Video duration must be greater than zero." }

            val frame = retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: throw IllegalArgumentException("Video does not contain a decodable frame.")
            frame.recycle()
            return Metadata(duration)
        } finally {
            retriever.release()
        }
    }
}
