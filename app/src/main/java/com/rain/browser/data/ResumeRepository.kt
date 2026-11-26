package com.rain.browser.data

import java.net.URI

data class ResumeEntry(val id: String, val url: String, val title: String, val positionMs: Long, val updatedAt: Long)

class ResumeRepository(private val storage: Storage) {
    fun save(url: String, title: String, positionMs: Long, updatedAt: Long = System.currentTimeMillis()): ResumeEntry? {
        if (!isWebUrl(url) || positionMs < 0) return null
        val entry = ResumeEntry(url, url, title.trim().take(512), positionMs, updatedAt)
        storage.set(TABLE, entry.id, mapOf("url" to entry.url, "title" to entry.title, "position_ms" to entry.positionMs.toString(), "updated_at" to entry.updatedAt.toString()))
        prune()
        return entry
    }

    fun list(): List<ResumeEntry> = storage.query(TABLE, orderBy = "updated_at DESC", limit = MAX_ENTRIES)
        .mapNotNull { row -> runCatching { ResumeEntry(row.getValue("id"), row.getValue("url"), row.getValue("title"), row.getValue("position_ms").toLong(), row.getValue("updated_at").toLong()) }.getOrNull() }

    fun delete(id: String): Boolean = storage.delete(TABLE, id) > 0
    fun clearExpired(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - MAX_AGE_MILLIS
        val expired = storage.query(TABLE).filter { (it["updated_at"]?.toLongOrNull() ?: Long.MAX_VALUE) < cutoff }
        return expired.sumOf { storage.delete(TABLE, it["id"]) }
    }

    private fun prune() {
        storage.query(TABLE, orderBy = "updated_at DESC", limit = MAX_ENTRIES + 1)
            .drop(MAX_ENTRIES).forEach { storage.delete(TABLE, it["id"]) }
    }

    private fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private companion object {
        const val TABLE = "resume"
        const val MAX_ENTRIES = 10
        const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
