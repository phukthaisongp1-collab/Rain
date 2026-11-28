package com.rain.browser.downloads

import java.io.File
import java.security.MessageDigest

/** Streams a local video file into SHA-256 without loading it fully into memory. */
object VideoChecksum {
    fun sha256(file: File): String {
        require(file.isFile && file.length() > 0L) { "Video file is missing or empty." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val BUFFER_SIZE = 64 * 1024
}
