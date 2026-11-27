package com.rain.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        assertNotNull(runCatching {
            VideoSourceValidator.validate("https://media.example.test/video.mp4?%61ccess_token=secret")
        }.exceptionOrNull())
    }

    @Test
    fun validatesDirectMp4ResponseAndByteRangeWithoutCredentials() {
        val payload = byteArrayOf(
            0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(),
            'y'.code.toByte(), 'p'.code.toByte(), 0, 0, 0, 0
        )
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.execute {
                server.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    readHeaders(input)
                    val output = BufferedOutputStream(socket.getOutputStream())
                    val headers = (
                        "HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: video/mp4\r\n" +
                            "Content-Range: bytes 0-11/12\r\n" +
                            "Content-Length: 12\r\n" +
                            "ETag: \"v1\"\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                    output.write(headers)
                    output.write(payload)
                    output.flush()
                }
            }

            val validated = VideoSourceValidator.validate(
                "http://127.0.0.1:" + server.localPort + "/video.mp4",
                allowLoopbackForTests = true
            )
            assertEquals("video/mp4", validated.mimeType)
            assertEquals(12L, validated.contentLength)
            assertEquals("\\"v1\\"", validated.etag)
            assertEquals(VideoContainerValidator.Container.MP4, validated.container)
        } finally {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun readHeaders(input: BufferedInputStream) {
        var matched = 0
        val terminator = byteArrayOf(13, 10, 13, 10)
        while (matched < terminator.size) {
            val value = input.read()
            if (value < 0) return
            matched = if (value.toByte() == terminator[matched]) matched + 1
            else if (value.toByte() == terminator[0]) 1 else 0
        }
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
