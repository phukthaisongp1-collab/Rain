package com.rain.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Application-owned persistent storage. Private WebView sessions must never use this store. */
interface Storage {
    fun get(table: String, id: String): Map<String, String>?
    fun set(table: String, id: String, values: Map<String, String>)
    fun query(table: String, selection: String? = null, selectionArgs: Array<String> = emptyArray(), orderBy: String? = null, limit: Int? = null): List<Map<String, String>>
    fun delete(table: String, id: String? = null): Int
}

class SqliteStorage(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), Storage {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, folder TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, visited_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX history_visited_at_idx ON history(visited_at DESC)")
        db.execSQL("CREATE TABLE resume (id TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, position_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE settings (id TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        createVideosTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations must be additive; existing user data is never dropped.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS history_visited_at_idx ON history(visited_at DESC)")
        }
        if (oldVersion < 3) {
            createVideosTable(db)
        }
    }

    private fun createVideosTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS videos (
                id TEXT PRIMARY KEY NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                thumbnail_url TEXT NOT NULL DEFAULT '',
                mime_type TEXT NOT NULL DEFAULT '',
                duration_ms INTEGER NOT NULL DEFAULT 0,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                bytes_downloaded INTEGER NOT NULL DEFAULT 0,
                download_id INTEGER,
                file_path TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL,
                downloaded_at INTEGER NOT NULL DEFAULT 0,
                expires_at INTEGER,
                is_liked INTEGER NOT NULL DEFAULT 0,
                unliked_at INTEGER,
                last_error TEXT NOT NULL DEFAULT '',
                etag TEXT NOT NULL DEFAULT '',
                last_modified TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS videos_status_created_idx ON videos(status, created_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS videos_expiry_idx ON videos(is_liked, expires_at)")
    }

    override fun get(table: String, id: String): Map<String, String>? {
        val safeTable = requireTable(table)
        return readableDatabase.query(safeTable, null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toStringMap() else null }
    }

    override fun set(table: String, id: String, values: Map<String, String>) {
        val safeTable = requireTable(table)
        require(values.keys.all { it.matches(Regex("[a-z_]+")) && it != "id" })
        val content = ContentValues().apply {
            put("id", id)
            values.forEach { (key, value) -> put(key, value) }
        }
        writableDatabase.insertWithOnConflict(safeTable, null, content, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun query(table: String, selection: String?, selectionArgs: Array<String>, orderBy: String?, limit: Int?): List<Map<String, String>> {
        val safeTable = requireTable(table)
        require(limit == null || limit in 1..10000)
        require(orderBy == null || orderBy.matches(Regex("[a-z_]+( ASC| DESC)?(, [a-z_]+( ASC| DESC)?)*", RegexOption.IGNORE_CASE)))
        return readableDatabase.query(safeTable, null, selection, selectionArgs, null, null, orderBy, limit?.toString())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toStringMap()) } }
    }

    override fun delete(table: String, id: String?): Int {
        val safeTable = requireTable(table)
        return if (id == null) writableDatabase.delete(safeTable, null, null)
        else writableDatabase.delete(safeTable, "id = ?", arrayOf(id))
    }

    private fun requireTable(table: String): String {
        require(table in ALLOWED_TABLES) { "Unsupported storage table" }
        return table
    }

    private fun Cursor.toStringMap(): Map<String, String> = buildMap {
        for (index in 0 until columnCount) {
            if (!isNull(index)) put(getColumnName(index), getString(index))
        }
    }

    private companion object {
        const val DATABASE_NAME = "rain_browser.db"
        const val DATABASE_VERSION = 3
        val ALLOWED_TABLES = setOf("bookmarks", "history", "resume", "settings", "videos")
    }
}
