package com.rain.browser.data

import java.util.UUID

data class Bookmark(val id: String, val url: String, val title: String, val folder: String, val createdAt: Long)

class BookmarkRepository(private val storage: Storage) {
    fun add(url: String, title: String, folder: String = DEFAULT_FOLDER, now: Long = System.currentTimeMillis()): Bookmark {
        require(isWebUrl(url))
        val safeTitle = title.trim().take(MAX_TITLE_LENGTH).ifBlank { url }
        val safeFolder = folder.trim().take(MAX_FOLDER_LENGTH).ifBlank { DEFAULT_FOLDER }
        val bookmark = Bookmark(UUID.randomUUID().toString(), url, safeTitle, safeFolder, now)
        storage.set(TABLE, bookmark.id, mapOf("url" to bookmark.url, "title" to bookmark.title, "folder" to bookmark.folder, "created_at" to bookmark.createdAt.toString()))
        return bookmark
    }

    fun get(id: String): Bookmark? = storage.get(TABLE, id)?.toBookmark()

    fun search(query: String = "", folder: String? = null): List<Bookmark> {
        val term = query.trim()
        val rows = storage.query(TABLE, orderBy = "created_at DESC")
        return rows.mapNotNull { it.toBookmark() }.filter {
            (term.isBlank() || it.title.contains(term, true) || it.url.contains(term, true)) &&
                (folder.isNullOrBlank() || it.folder == folder)
        }
    }

    fun delete(id: String): Boolean = storage.delete(TABLE, id) > 0
    fun deleteAll(): Int = storage.delete(TABLE)

    private fun Map<String, String>.toBookmark(): Bookmark? = runCatching {
        Bookmark(getValue("id"), getValue("url"), getValue("title"), getValue("folder"), getValue("created_at").toLong())
    }.getOrNull()

    private fun isWebUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private companion object {
        const val TABLE = "bookmarks"
        const val DEFAULT_FOLDER = "Bookmarks"
        const val MAX_TITLE_LENGTH = 512
        const val MAX_FOLDER_LENGTH = 80
    }
}
