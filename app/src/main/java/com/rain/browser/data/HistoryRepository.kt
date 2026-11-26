package com.rain.browser.data

import java.net.URI

data class HistoryEntry(val id: String, val url: String, val title: String, val visitedAt: Long)

class HistoryRepository(private val storage: Storage) {
    fun record(url: String, title: String, visitedAt: Long = System.currentTimeMillis()): HistoryEntry? {
        if (!isWebUrl(url)) return null
        val id = "$url#$visitedAt"
        val entry = HistoryEntry(id, url, title.trim().take(512), visitedAt)
        storage.set(TABLE, id, mapOf("url" to url, "title" to entry.title, "visited_at" to visitedAt.toString()))
        prune()
        return entry
    }

    fun search(query: String = "", limit: Int = MAX_ENTRIES): List<HistoryEntry> {
        require(limit in 1..MAX_ENTRIES)
        val term = query.trim()
        return storage.query(TABLE, orderBy = "visited_at DESC", limit = MAX_ENTRIES)
            .mapNotNull { row -> runCatching { HistoryEntry(row.getValue("id"), row.getValue("url"), row.getValue("title"), row.getValue("visited_at").toLong()) }.getOrNull() }
            .filter { term.isBlank() || it.title.contains(term, true) || it.url.contains(term, true) }
            .take(limit)
    }

    fun delete(id: String): Boolean = storage.delete(TABLE, id) > 0
    fun clear(): Int = storage.delete(TABLE)

    fun clearOlderThan(cutoffEpochMillis: Long): Int {
        val expired = storage.query(TABLE).filter { (it["visited_at"]?.toLongOrNull() ?: Long.MAX_VALUE) < cutoffEpochMillis }
        return expired.sumOf { storage.delete(TABLE, it["id"]) }
    }

    private fun prune() {
        val overflow = storage.query(TABLE, orderBy = "visited_at DESC", limit = MAX_ENTRIES + 1).drop(MAX_ENTRIES)
        overflow.forEach { storage.delete(TABLE, it["id"]) }
    }

    private fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private companion object {
        const val TABLE = "history"
        const val MAX_ENTRIES = 5000
    }
}
