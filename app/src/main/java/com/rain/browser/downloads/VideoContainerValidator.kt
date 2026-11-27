package com.rain.browser.downloads

/**
 * Performs a bounded signature check before a completed transfer can be
 * considered for deeper media validation. This is not a full decoder check.
 */
object VideoContainerValidator {
    enum class Container {
        MP4,
        WEBM,
        UNKNOWN
    }

    fun detect(prefix: ByteArray): Container {
        if (hasWebmSignature(prefix)) return Container.WEBM
        if (hasMp4FtypBox(prefix)) return Container.MP4
        return Container.UNKNOWN
    }

    private fun hasWebmSignature(bytes: ByteArray): Boolean {
        val signature = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        if (bytes.size < signature.size) return false
        return signature.indices.all { index -> bytes[index] == signature[index] }
    }

    private fun hasMp4FtypBox(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
    }
}
