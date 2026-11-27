package com.rain.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRetentionPolicyTest {
    private val downloadTime = 1_000_000L

    @Test
    fun unlikedVideoExpiresThirtyDaysAfterDownload() {
        val metadata = VideoRetentionPolicy.Metadata(downloadTime, isLiked = false)
        val expiry = downloadTime + VideoRetentionPolicy.RETENTION_MILLIS

        assertEquals(expiry, VideoRetentionPolicy.expiresAtMillis(metadata))
        assertFalse(VideoRetentionPolicy.isExpired(metadata, expiry - 1L))
        assertTrue(VideoRetentionPolicy.isExpired(metadata, expiry))
    }

    @Test
    fun likedVideoNeverExpiresAutomatically() {
        val metadata = VideoRetentionPolicy.Metadata(downloadTime, isLiked = true)

        assertNull(VideoRetentionPolicy.expiresAtMillis(metadata))
        assertFalse(VideoRetentionPolicy.isExpired(metadata, Long.MAX_VALUE))
    }

    @Test
    fun unlikingStartsFreshThirtyDayWindow() {
        val liked = VideoRetentionPolicy.Metadata(downloadTime, isLiked = true)
        val unlikeTime = downloadTime + 90L * 24L * 60L * 60L * 1000L
        val unliked = VideoRetentionPolicy.onLikeChanged(liked, false, unlikeTime)

        assertEquals(
            unlikeTime + VideoRetentionPolicy.RETENTION_MILLIS,
            VideoRetentionPolicy.expiresAtMillis(unliked)
        )
        assertFalse(VideoRetentionPolicy.isExpired(unliked, unlikeTime))
    }

    @Test
    fun likingClearsPreviousUnlikeTimestamp() {
        val unliked = VideoRetentionPolicy.Metadata(
            downloadedAtMillis = downloadTime,
            isLiked = false,
            unlikedAtMillis = downloadTime + 500L
        )

        val liked = VideoRetentionPolicy.onLikeChanged(unliked, true, downloadTime + 1_000L)

        assertTrue(liked.isLiked)
        assertNull(liked.unlikedAtMillis)
        assertNull(VideoRetentionPolicy.expiresAtMillis(liked))
    }

    @Test
    fun unlikingAgainResetsTheWindowFromLatestAction() {
        val firstUnlike = VideoRetentionPolicy.Metadata(
            downloadedAtMillis = downloadTime,
            isLiked = false,
            unlikedAtMillis = downloadTime + 100L
        )
        val secondUnlikeTime = downloadTime + 10_000L

        val updated = VideoRetentionPolicy.onLikeChanged(firstUnlike, false, secondUnlikeTime)

        assertEquals(
            secondUnlikeTime + VideoRetentionPolicy.RETENTION_MILLIS,
            VideoRetentionPolicy.expiresAtMillis(updated)
        )
    }

    @Test
    fun expiryAdditionSaturatesInsteadOfOverflowing() {
        val metadata = VideoRetentionPolicy.Metadata(
            downloadedAtMillis = Long.MAX_VALUE - 10L,
            isLiked = false
        )

        assertEquals(Long.MAX_VALUE, VideoRetentionPolicy.expiresAtMillis(metadata))
    }
}
