package com.rain.browser.data

import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
    private class MemoryStorage : Storage {
        private val tables = mutableMapOf<String, LinkedHashMap<String, Map<String, String>>>()
        override fun get(table: String, id: String): Map<String, String>? = tables[table]?.get(id)?.plus("id" to id)
        override fun set(table: String, id: String, values: Map<String, String>) {
            tables.getOrPut(table) { linkedMapOf() }[id] = values
        }
        override fun query(table: String, selection: String?, selectionArgs: Array<String>, orderBy: String?, limit: Int?): List<Map<String, String>> {
            val rows = tables[table].orEmpty().map { (id, values) -> values + ("id" to id) }
            val sorted = when (orderBy) {
                "created_at DESC" -> rows.sortedByDescending { it["created_at"]?.toLongOrNull() }
                "visited_at DESC" -> rows.sortedByDescending { it["visited_at"]?.toLongOrNull() }
                "updated_at DESC" -> rows.sortedByDescending { it["updated_at"]?.toLongOrNull() }
                else -> rows
            }
            return sorted.take(limit ?: sorted.size)
        }
        override fun delete(table: String, id: String?): Int {
            val values = tables[table] ?: return 0
            if (id == null) { val count = values.size; values.clear(); return count }
            return if (values.remove(id) != null) 1 else 0
        }
    }

    @Test fun bookmarkRejectsUnsafeUrlAndPersistsValidEntry() {
        val repository = BookmarkRepository(MemoryStorage())
        assertThrows(IllegalArgumentException::class.java) { repository.add("javascript:alert(1)", "bad") }
        val item = repository.add("https://example.com", "Example", "Work", 100L)
        assertEquals(item, repository.get(item.id))
    }

    @Test fun bookmarkSearchIsCaseInsensitiveAndFolderScoped() {
        val repository = BookmarkRepository(MemoryStorage())
        repository.add("https://example.com", "Example Site", "Work", 1L)
        repository.add("https://news.example", "News", "Read", 2L)
        assertEquals(1, repository.search("EXAMPLE", "Work").size)
        assertEquals(1, repository.search(folder = "Read").size)
    }

    @Test fun bookmarkDeleteAndClearWork() {
        val repository = BookmarkRepository(MemoryStorage())
        val item = repository.add("https://example.com", "Example")
        assertTrue(repository.delete(item.id))
        repository.add("https://one.example", "One")
        repository.add("https://two.example", "Two")
        assertEquals(2, repository.deleteAll())
    }

    @Test fun historyRejectsNonWebUrl() {
        val repository = HistoryRepository(MemoryStorage())
        assertNull(repository.record("file:///secret", "Secret"))
    }

    @Test fun historySearchAndDeleteWork() {
        val repository = HistoryRepository(MemoryStorage())
        val first = repository.record("https://example.com", "Example", 1L)!!
        repository.record("https://news.example", "News", 2L)
        assertEquals(1, repository.search("EXAMPLE").size)
        assertTrue(repository.delete(first.id))
        assertEquals(1, repository.search().size)
    }

    @Test fun historyClearRemovesAllEntries() {
        val repository = HistoryRepository(MemoryStorage())
        repository.record("https://one.example", "One", 1L)
        repository.record("https://two.example", "Two", 2L)
        assertEquals(2, repository.clear())
    }

    @Test fun historyRetentionRemovesOlderEntries() {
        val repository = HistoryRepository(MemoryStorage())
        repository.record("https://old.example", "Old", 10L)
        repository.record("https://new.example", "New", 20L)
        assertEquals(1, repository.clearOlderThan(15L))
        assertEquals("New", repository.search().single().title)
    }

    @Test fun resumeRejectsUnsafeUrlAndNegativePosition() {
        val repository = ResumeRepository(MemoryStorage())
        assertNull(repository.save("https://example.com", "Example", -1))
        assertNull(repository.save("data:text/html,hi", "Bad", 1))
    }

    @Test fun resumeStoresPositionWithoutFormOrCredentialFields() {
        val repository = ResumeRepository(MemoryStorage())
        val item = repository.save("https://video.example/watch", "Video", 12000L, 50L)!!
        assertEquals(12000L, repository.list().single().positionMs)
        assertFalse(repository.list().single().url.contains("password", true))
        assertEquals(item, repository.list().single())
    }

    @Test fun resumeCleanupRemovesEntriesOlderThanThirtyDays() {
        val repository = ResumeRepository(MemoryStorage())
        val now = 40L * 24 * 60 * 60 * 1000
        repository.save("https://video.example/old", "Old", 100L, now - 31L * 24 * 60 * 60 * 1000)
        repository.save("https://video.example/new", "New", 200L, now)
        assertEquals(1, repository.clearExpired(now))
        assertEquals("New", repository.list().single().title)
    }

    @Test fun settingsUseSafeDefaultsAndPersistChanges() {
        val repository = SettingsRepository(MemoryStorage())
        assertEquals(SettingsRepository.Language.SYSTEM, repository.language)
        assertEquals("DUCKDUCKGO", repository.searchEngine)
        assertEquals(SettingsRepository.DefaultDisplayMode.MOBILE, repository.defaultDisplayMode)
        assertEquals(0, repository.autoClearHistoryDays)
        repository.language = SettingsRepository.Language.THAI
        repository.searchEngine = "GOOGLE"
        repository.defaultDisplayMode = SettingsRepository.DefaultDisplayMode.DESKTOP
        repository.autoClearHistoryDays = 7
        repository.blockGamblingSites = true
        assertEquals(SettingsRepository.Language.THAI, repository.language)
        assertEquals("GOOGLE", repository.searchEngine)
        assertEquals(SettingsRepository.DefaultDisplayMode.DESKTOP, repository.defaultDisplayMode)
        assertEquals(7, repository.autoClearHistoryDays)
        assertTrue(repository.blockGamblingSites)
    }

    @Test fun settingsRejectUnsupportedValues() {
        val repository = SettingsRepository(MemoryStorage())
        assertThrows(IllegalArgumentException::class.java) { repository.searchEngine = "UNKNOWN" }
        assertThrows(IllegalArgumentException::class.java) { repository.autoClearHistoryDays = 14 }
    }
}
