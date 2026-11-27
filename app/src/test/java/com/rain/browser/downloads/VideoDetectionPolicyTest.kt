package com.rain.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDetectionPolicyTest {
    @Test
    fun acceptsOnlyStableUserInitiatedMainFrameDirectVideoWithCorroboratingMetadata() {
        assertEquals(
            VideoDetectionPolicy.Decision.CANDIDATE,
            VideoDetectionPolicy.evaluate(validSignals())
        )
    }

    @Test
    fun rejectsExplicitAdStatusAndAdvertisementMarkers() {
        assertEquals(
            VideoDetectionPolicy.Decision.REJECT_AD,
            VideoDetectionPolicy.evaluate(validSignals().copy(adStatus = true))
        )
        assertEquals(
            VideoDetectionPolicy.Decision.REJECT_AD,
            VideoDetectionPolicy.evaluate(
                validSignals().copy(visibleAdText = listOf("Advertisement", "Skip Ad"))
            )
        )
    }

    @Test
    fun ignoresAutoplayIframeAndUnstableSourceSignals() {
        assertEquals(
            VideoDetectionPolicy.Decision.IGNORE,
            VideoDetectionPolicy.evaluate(validSignals().copy(userInitiated = false))
        )
        assertEquals(
            VideoDetectionPolicy.Decision.IGNORE,
            VideoDetectionPolicy.evaluate(validSignals().copy(mainFrame = false))
        )
        assertEquals(
            VideoDetectionPolicy.Decision.IGNORE,
            VideoDetectionPolicy.evaluate(validSignals().copy(sourceStable = false))
        )
    }

    @Test
    fun failsClosedForBlobAndUnsupportedStreamingMimeTypes() {
        assertEquals(
            VideoDetectionPolicy.Decision.IGNORE,
            VideoDetectionPolicy.evaluate(validSignals().copy(sourceUrl = "blob:https://video.example.test/id"))
        )
        assertEquals(
            VideoDetectionPolicy.Decision.NEED_MORE_EVIDENCE,
            VideoDetectionPolicy.evaluate(validSignals().copy(mimeType = "application/vnd.apple.mpegurl"))
        )
    }

    @Test
    fun requiresExplicitNonAdStateAndFailsClosedWhenUnknown() {
        assertEquals(
            VideoDetectionPolicy.Decision.NEED_MORE_EVIDENCE,
            VideoDetectionPolicy.evaluate(validSignals().copy(adStatus = null))
        )
    }

    @Test
    fun durationAloneCannotEstablishMainContent() {
        assertEquals(
            VideoDetectionPolicy.Decision.NEED_MORE_EVIDENCE,
            VideoDetectionPolicy.evaluate(
                validSignals().copy(title = "", posterUrl = "", playerContext = "")
            )
        )
    }

    private fun validSignals() = VideoDetectionPolicy.Signals(
        pageUrl = "https://video.example.test/watch",
        sourceUrl = "https://cdn.example.test/media/video.mp4",
        mimeType = "video/mp4",
        title = "Main video",
        posterUrl = "https://video.example.test/poster.jpg",
        playerContext = "Main video player",
        durationMs = 120_000L,
        userInitiated = true,
        mainFrame = true,
        sourceStable = true,
        sourceFromVideoElement = true,
        adStatus = false,
        visibleAdText = emptyList()
    )
}
