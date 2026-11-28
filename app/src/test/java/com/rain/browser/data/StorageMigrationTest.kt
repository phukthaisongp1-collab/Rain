package com.rain.browser.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class StorageMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun upgradingVersion2PreservesExistingRowsAndCreatesVideosTable() {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()

        val version2Database = SQLiteDatabase.openOrCreateDatabase(databaseFile.absolutePath, null)
        try {
            version2Database.execSQL(
                "CREATE TABLE bookmarks (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, folder TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
            version2Database.execSQL(
                "CREATE TABLE history (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, visited_at INTEGER NOT NULL)"
            )
            version2Database.execSQL(
                "CREATE INDEX history_visited_at_idx ON history(visited_at DESC)"
            )
            version2Database.execSQL(
                "CREATE TABLE resume (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, position_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL)"
            )
            version2Database.execSQL(
                "CREATE TABLE settings (id TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)"
            )
            version2Database.execSQL(
                "INSERT INTO bookmarks VALUES ('bookmark-1', 'https://example.com', 'Example', 'Work', 101)"
            )
            version2Database.execSQL(
                "INSERT INTO history VALUES ('history-1', 'https://example.org', 'History', 202)"
            )
            version2Database.execSQL(
                "INSERT INTO resume VALUES ('resume-1', 'https://example.net', 'Resume', 303, 404)"
            )
            version2Database.execSQL(
                "INSERT INTO settings VALUES ('setting-1', 'dark')"
            )
            version2Database.version = 2
        } finally {
            version2Database.close()
        }

        val storage = SqliteStorage(context)
        try {
            val bookmarks = storage.query("bookmarks")
            val history = storage.query("history")
            val resume = storage.query("resume")
            val settings = storage.query("settings")

            assertEquals(1, bookmarks.size)
            assertEquals("bookmark-1", bookmarks.single()["id"])
            assertEquals("https://example.com", bookmarks.single()["url"])
            assertEquals("Example", bookmarks.single()["title"])
            assertEquals("Work", bookmarks.single()["folder"])
            assertEquals("101", bookmarks.single()["created_at"])

            assertEquals(1, history.size)
            assertEquals("history-1", history.single()["id"])
            assertEquals("https://example.org", history.single()["url"])
            assertEquals("History", history.single()["title"])
            assertEquals("202", history.single()["visited_at"])

            assertEquals(1, resume.size)
            assertEquals("resume-1", resume.single()["id"])
            assertEquals("https://example.net", resume.single()["url"])
            assertEquals("Resume", resume.single()["title"])
            assertEquals("303", resume.single()["position_ms"])
            assertEquals("404", resume.single()["updated_at"])

            assertEquals(1, settings.size)
            assertEquals("setting-1", settings.single()["id"])
            assertEquals("dark", settings.single()["value"])

            assertTrue("videos table must exist after migration", storage.query("videos").isEmpty())
        } finally {
            storage.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "rain_browser.db"
    }
}
