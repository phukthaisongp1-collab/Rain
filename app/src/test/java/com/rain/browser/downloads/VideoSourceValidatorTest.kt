package com.rain.browser.downloads

import org.junit.Assert.assertNotNull
import org.junit.Test

class VideoSourceValidatorTest {
    @Test
    fun rejectsLoopbackAndLocalNetworkHosts() {
        assertNotNull(runCatching {
            VideoSourceValidator.validate("http://127.0.0.1/video.mp4")
        }.exceptionOrNull())
        assertNotNull(runCatching {
            VideoSourceValidator.validate("http://localhost/video.mp4")
        }.exceptionOrNull())
    }

    @Test
    fun rejectsCredentialAndTokenBearingUrlsBeforeConnecting() {
        assertNotNull(runCatching {
            VideoSourceValidator.validate("https://user:password@media.example.test/video.mp4")
        }.exceptionOrNull())
        assertNotNull(runCatching {
            VideoSourceValidator.validate("https://media.example.test/video.mp4?access_token=secret")
        }.exceptionOrNull())
        assertNotNull(runCatching {
            VideoSourceValidator.validate("https://media.example.test/video.mp4?signature=secret")
        }.exceptionOrNull())
    }

    @Test
    fun rejectsOpaqueBlobAndUnsupportedSchemes() {
        assertNotNull(runCatching {
            VideoSourceValidator.validate("blob:https://media.example.test/video")
        }.exceptionOrNull())
        assertNotNull(runCatching {
            VideoSourceValidator.validate("file:///sdcard/video.mp4")
        }.exceptionOrNull())
    }
}
