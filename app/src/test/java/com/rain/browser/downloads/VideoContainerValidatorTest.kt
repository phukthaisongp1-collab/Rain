package com.rain.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoContainerValidatorTest {
    @Test
    fun detectsWebmByEbmlSignature() {
        val prefix = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x00)

        assertEquals(VideoContainerValidator.Container.WEBM, VideoContainerValidator.detect(prefix))
    }

    @Test
    fun detectsMp4ByFtypBoxMarker() {
        val prefix = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
        )

        assertEquals(VideoContainerValidator.Container.MP4, VideoContainerValidator.detect(prefix))
    }

    @Test
    fun rejectsUnknownAndTruncatedSignatures() {
        assertEquals(
            VideoContainerValidator.Container.UNKNOWN,
            VideoContainerValidator.detect(byteArrayOf(0x1A, 0x45, 0xDF.toByte()))
        )
        assertEquals(
            VideoContainerValidator.Container.UNKNOWN,
            VideoContainerValidator.detect("not a video".toByteArray())
        )
    }

    @Test
    fun doesNotAcceptMp4ExtensionAsEvidence() {
        val arbitraryBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)

        assertEquals(VideoContainerValidator.Container.UNKNOWN, VideoContainerValidator.detect(arbitraryBytes))
    }
}
