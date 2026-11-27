package com.rain.browser.downloads

/** Bounded container signature check; this is not full media integrity validation. */
object VideoContainerValidator {
    enum class Container { MP4, WEBM, UNKNOWN }

    fun detect(prefix: ByteArray): Container = when {
        hasWebmSignature(prefix) -> Container.WEBM
        hasMp4FtypBox(prefix) -> Container.MP4
        else -> Container.UNKNOWN
    }

    private fun hasWebmSignature(bytes: ByteArray): Boolean {
        val signature = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        return bytes.size >= signature.size &&
            signature.indices.all { bytes[it] == signature[it] }
    }

    private fun hasMp4FtypBox(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
}
