package com.rain.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoContainerValidatorTest {
    @Test fun detectsWebmEbmlSignature() {
        val bytes = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x00)
        assertEquals(VideoContainerValidator.Container.WEBM, VideoContainerValidator.detect(bytes))
    }

    @Test fun detectsMp4FtypMarker() {
        val bytes = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(),
            'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
        assertEquals(VideoContainerValidator.Container.MP4, VideoContainerValidator.detect(bytes))
    }

    @Test fun rejectsTruncatedAndUnknownSignatures() {
        assertEquals(VideoContainerValidator.Container.UNKNOWN,
            VideoContainerValidator.detect(byteArrayOf(0x1A, 0x45, 0xDF.toByte())))
        assertEquals(VideoContainerValidator.Container.UNKNOWN,
            VideoContainerValidator.detect("not media".toByteArray()))
    }

    @Test fun extensionAloneIsNotEvidence() {
        assertEquals(VideoContainerValidator.Container.UNKNOWN,
            VideoContainerValidator.detect(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
    }
}
