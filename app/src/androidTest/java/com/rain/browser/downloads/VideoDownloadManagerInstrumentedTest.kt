package com.rain.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Android 16 emulator feasibility tests for the platform DownloadManager.
 *
 * The local server serves synthetic bytes only. These tests do not inspect or download
 * third-party media and do not change RAIN's production download flow.
 */
@RunWith(AndroidJUnit4::class)
class VideoDownloadManagerInstrumentedTest {

    @Test
    fun downloadsSyntheticVideoAndReportsCompletedByteCount() {
        val context = targetContext()
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val payload = syntheticMp4Payload()
        val server = LocalHttpServer(payload)

        var requestId = -1L
        try {
            server.start()
            requestId = enqueue(manager, context, server.url, uniqueFileName())
            val result = awaitStatus(manager, requestId, DownloadManager.STATUS_SUCCESSFUL, 30_000L)

            assertEquals(payload.size.toLong(), result.bytesDownloaded)
            assertEquals(payload.size.toLong(), result.totalBytes)

            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                result.fileName
            )
            assertTrue("DownloadManager did not create the destination file", file.isFile)
            assertEquals(payload.size.toLong(), file.length())
            assertArrayEquals(payload, file.readBytes())
        } finally {
            if (requestId >= 0L) manager.remove(requestId)
            server.close()
        }
    }

    @Test
    fun exposesPartialTransferProgressBeforeCompletion() {
        val context = targetContext()
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
        val server = LocalHttpServer(payload, pauseAfterFirstChunk = true)

        var requestId = -1L
        try {
            server.start()
            requestId = enqueue(manager, context, server.url, uniqueFileName())
            assertTrue(
                "DownloadManager did not receive the first response chunk",
                server.firstChunkSent.await(20, TimeUnit.SECONDS)
            )

            val progress = awaitPartialProgress(manager, requestId, payload.size.toLong(), 15_000L)
            assertTrue(progress.bytesDownloaded > 0L)
            assertTrue(progress.bytesDownloaded < payload.size.toLong())
            assertEquals(payload.size.toLong(), progress.totalBytes)

            server.releaseRemainingBody()
            val completed = awaitStatus(
                manager,
                requestId,
                DownloadManager.STATUS_SUCCESSFUL,
                30_000L
            )
            assertEquals(payload.size.toLong(), completed.bytesDownloaded)
        } finally {
            server.releaseRemainingBody()
            if (requestId >= 0L) manager.remove(requestId)
            server.close()
        }
    }

    @Test
    fun removesAnActiveTransferWhenCanceled() {
        val context = targetContext()
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val server = LocalHttpServer(syntheticMp4Payload(), holdBeforeBody = true)

        var requestId = -1L
        try {
            server.start()
            requestId = enqueue(manager, context, server.url, uniqueFileName())
            assertTrue(
                "DownloadManager did not connect to the controlled local server",
                server.headersSent.await(20, TimeUnit.SECONDS)
            )

            val removedCount = manager.remove(requestId)
            assertEquals(1, removedCount)

            manager.query(DownloadManager.Query().setFilterById(requestId)).use { cursor ->
                assertEquals("Canceled request should be removed from DownloadManager", 0, cursor.count)
            }
        } finally {
            server.releaseRemainingBody()
            if (requestId >= 0L) manager.remove(requestId)
            server.close()
        }
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun enqueue(
        manager: DownloadManager,
        context: Context,
        url: String,
        fileName: String
    ): Long {
        val destination = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        requireNotNull(destination) { "App-specific external Movies directory is unavailable" }
        if (!destination.exists()) {
            check(destination.mkdirs()) { "Unable to create app-specific Movies directory" }
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("RAIN DownloadManager emulator probe")
            .setMimeType("video/mp4")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_MOVIES,
                fileName
            )
        return manager.enqueue(request)
    }

    private fun awaitStatus(
        manager: DownloadManager,
        requestId: Long,
        expectedStatus: Int,
        timeoutMillis: Long
    ): DownloadSnapshot {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var lastSnapshot: DownloadSnapshot? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = query(manager, requestId)
            lastSnapshot = snapshot
            if (snapshot.status == expectedStatus) return snapshot
            if (snapshot.status == DownloadManager.STATUS_FAILED) {
                throw AssertionError("DownloadManager failed with reason " + snapshot.reason)
            }
            SystemClock.sleep(100L)
        }
        throw AssertionError(
            "Timed out waiting for status " + expectedStatus +
                "; last status=" + lastSnapshot?.status +
                "; reason=" + lastSnapshot?.reason
        )
    }

    private fun awaitPartialProgress(
        manager: DownloadManager,
        requestId: Long,
        expectedTotal: Long,
        timeoutMillis: Long
    ): DownloadSnapshot {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var lastSnapshot: DownloadSnapshot? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = query(manager, requestId)
            lastSnapshot = snapshot
            if (snapshot.bytesDownloaded > 0L && snapshot.bytesDownloaded < expectedTotal) {
                return snapshot
            }
            if (snapshot.status == DownloadManager.STATUS_FAILED) {
                throw AssertionError("DownloadManager failed with reason " + snapshot.reason)
            }
            SystemClock.sleep(100L)
        }
        throw AssertionError(
            "Timed out waiting for partial progress; last bytes=" +
                lastSnapshot?.bytesDownloaded + "; status=" + lastSnapshot?.status
        )
    }

    private fun query(manager: DownloadManager, requestId: Long): DownloadSnapshot {
        manager.query(DownloadManager.Query().setFilterById(requestId)).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw AssertionError("DownloadManager request disappeared before completion")
            }
            return DownloadSnapshot(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                bytesDownloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ),
                totalBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                ),
                fileName = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                )
            )
        }
    }

    private data class DownloadSnapshot(
        val status: Int,
        val reason: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val fileName: String
    )

    private fun uniqueFileName(): String = "rain-video-probe-" + UUID.randomUUID() + ".mp4"

    private fun syntheticMp4Payload(): ByteArray = byteArrayOf(
        0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
        'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        0, 0, 0, 0, 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()
    )

    private class LocalHttpServer(
        private val payload: ByteArray,
        private val pauseAfterFirstChunk: Boolean = false,
        private val holdBeforeBody: Boolean = false
    ) : Closeable {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val bodyGate = CountDownLatch(1)
        val headersSent = CountDownLatch(1)
        val firstChunkSent = CountDownLatch(1)
        val url: String
            get() = "http://127.0.0.1:" + serverSocket.localPort + "/synthetic-video.mp4"

        fun start() {
            executor.execute {
                try {
                    serverSocket.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val input = BufferedInputStream(socket.getInputStream())
                        readRequestHeaders(input)

                        val output = BufferedOutputStream(socket.getOutputStream())
                        val header = (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: video/mp4\r\n" +
                                "Content-Length: " + payload.size + "\r\n" +
                                "Accept-Ranges: bytes\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.US_ASCII)
                        output.write(header)
                        output.flush()
                        headersSent.countDown()

                        if (holdBeforeBody) {
                            bodyGate.await(20, TimeUnit.SECONDS)
                        } else if (pauseAfterFirstChunk) {
                            val split = payload.size / 4
                            output.write(payload, 0, split)
                            output.flush()
                            firstChunkSent.countDown()
                            bodyGate.await(20, TimeUnit.SECONDS)
                            output.write(payload, split, payload.size - split)
                            output.flush()
                        } else {
                            firstChunkSent.countDown()
                            output.write(payload)
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                    // A canceled DownloadManager request is expected to close the socket.
                }
            }
        }

        fun releaseRemainingBody() {
            bodyGate.countDown()
        }

        private fun readRequestHeaders(input: BufferedInputStream) {
            var matched = 0
            val terminator = byteArrayOf(13, 10, 13, 10)
            while (matched < terminator.size) {
                val value = input.read()
                if (value < 0) return
                matched = if (value.toByte() == terminator[matched]) matched + 1
                else if (value.toByte() == terminator[0]) 1 else 0
            }
        }

        override fun close() {
            releaseRemainingBody()
            runCatching { serverSocket.close() }
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }
}
