package com.rain.browser.downloads

import org.junit.Assert.*
import org.junit.Test

class VideoRetentionPolicyTest {
    private val downloadedAt = 1_000_000L

    @Test fun expiresThirtyDaysAfterDownloadAtBoundary() {
        val item = VideoRetentionPolicy.Metadata(downloadedAt, false)
        val expiry = downloadedAt + VideoRetentionPolicy.RETENTION_MILLIS
        assertEquals(expiry, VideoRetentionPolicy.expiresAtMillis(item))
        assertFalse(VideoRetentionPolicy.isExpired(item, expiry - 1))
        assertTrue(VideoRetentionPolicy.isExpired(item, expiry))
    }

    @Test fun likedVideoNeverExpiresAutomatically() {
        val item = VideoRetentionPolicy.Metadata(downloadedAt, true)
        assertNull(VideoRetentionPolicy.expiresAtMillis(item))
        assertFalse(VideoRetentionPolicy.isExpired(item, Long.MAX_VALUE))
    }

    @Test fun unlikeStartsFreshThirtyDayWindow() {
        val liked = VideoRetentionPolicy.Metadata(downloadedAt, true)
        val unlikeAt = downloadedAt + 90L * 24L * 60L * 60L * 1000L
        val item = VideoRetentionPolicy.onLikeChanged(liked, false, unlikeAt)
        assertEquals(unlikeAt + VideoRetentionPolicy.RETENTION_MILLIS,
            VideoRetentionPolicy.expiresAtMillis(item))
    }

    @Test fun likingClearsPreviousUnlikeTimestamp() {
        val item = VideoRetentionPolicy.Metadata(downloadedAt, false, downloadedAt + 50)
        val liked = VideoRetentionPolicy.onLikeChanged(item, true, downloadedAt + 100)
        assertTrue(liked.isLiked)
        assertNull(liked.unlikedAtMillis)
        assertNull(VideoRetentionPolicy.expiresAtMillis(liked))
    }

    @Test fun repeatedUnlikeResetsWindowFromLatestAction() {
        val item = VideoRetentionPolicy.Metadata(downloadedAt, false, downloadedAt + 10)
        val unlikeAt = downloadedAt + 20_000
        val updated = VideoRetentionPolicy.onLikeChanged(item, false, unlikeAt)
        assertEquals(unlikeAt + VideoRetentionPolicy.RETENTION_MILLIS,
            VideoRetentionPolicy.expiresAtMillis(updated))
    }

    @Test fun expiryAdditionSaturatesInsteadOfOverflowing() {
        val item = VideoRetentionPolicy.Metadata(Long.MAX_VALUE - 1, false)
        assertEquals(Long.MAX_VALUE, VideoRetentionPolicy.expiresAtMillis(item))
    }
}
