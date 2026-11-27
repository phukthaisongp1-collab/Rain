package com.rain.browser.downloads

/**
 * Pure retention rules for RAIN-managed offline videos.
 *
 * This policy is intentionally independent from persistence and file deletion.
 * Callers must persist the returned metadata and must separately validate that
 * any deletion target belongs to RAIN's managed video directory.
 */
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

    /**
     * Liked videos never expire automatically. An unlike action starts a fresh
     * retention window from the exact time of that action.
     */
    fun expiresAtMillis(metadata: Metadata): Long? {
        if (metadata.isLiked) return null
        val retentionStart = metadata.unlikedAtMillis ?: metadata.downloadedAtMillis
        return safeAdd(retentionStart, RETENTION_MILLIS)
    }

    fun isExpired(metadata: Metadata, nowMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Current timestamp must not be negative." }
        val expiresAt = expiresAtMillis(metadata) ?: return false
        return nowMillis >= expiresAt
    }

    /**
     * Returns updated metadata for an explicit Like/Unlike user action.
     * Unlike always starts a new 30-day window, even if already unliked.
     */
    fun onLikeChanged(
        metadata: Metadata,
        isLiked: Boolean,
        actionAtMillis: Long
    ): Metadata {
        require(actionAtMillis >= 0L) { "Action timestamp must not be negative." }
        return if (isLiked) {
            metadata.copy(isLiked = true, unlikedAtMillis = null)
        } else {
            metadata.copy(isLiked = false, unlikedAtMillis = actionAtMillis)
        }
    }

    private fun safeAdd(timestamp: Long, duration: Long): Long {
        return if (timestamp > Long.MAX_VALUE - duration) Long.MAX_VALUE
        else timestamp + duration
    }
}
