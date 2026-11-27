package com.rain.browser.downloads

/** Pure retention rules. Persistence and file deletion are deliberately external. */
object VideoRetentionPolicy {
    const val RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    data class Metadata(
        val downloadedAtMillis: Long,
        val isLiked: Boolean,
        val unlikedAtMillis: Long? = null
    ) {
        init {
            require(downloadedAtMillis >= 0L) { "Download timestamp must not be negative." }
            require(unlikedAtMillis == null || unlikedAtMillis >= 0L) {
                "Unlike timestamp must not be negative."
            }
        }
    }

    fun expiresAtMillis(metadata: Metadata): Long? {
        if (metadata.isLiked) return null
        val start = metadata.unlikedAtMillis ?: metadata.downloadedAtMillis
        return if (start > Long.MAX_VALUE - RETENTION_MILLIS) Long.MAX_VALUE
        else start + RETENTION_MILLIS
    }

    fun isExpired(metadata: Metadata, nowMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Current timestamp must not be negative." }
        return expiresAtMillis(metadata)?.let { nowMillis >= it } ?: false
    }

    /** An explicit Unlike action always starts a fresh 30-day window. */
    fun onLikeChanged(metadata: Metadata, isLiked: Boolean, actionAtMillis: Long): Metadata {
        require(actionAtMillis >= 0L) { "Action timestamp must not be negative." }
        return if (isLiked) metadata.copy(isLiked = true, unlikedAtMillis = null)
        else metadata.copy(isLiked = false, unlikedAtMillis = actionAtMillis)
    }
}
