package com.rain.browser.data

class SettingsRepository(private val storage: Storage) {
    enum class Language { SYSTEM, THAI, ENGLISH }
    enum class DefaultDisplayMode { MOBILE, DESKTOP }

    var language: Language
        get() = enumValue(storage.get(TABLE, KEY_LANGUAGE)?.get("value"), Language.SYSTEM)
        set(value) = put(KEY_LANGUAGE, value.name)

    var searchEngine: String
        get() = storage.get(TABLE, KEY_SEARCH_ENGINE)?.get("value")?.takeIf { it in SUPPORTED_SEARCH_ENGINES } ?: DEFAULT_SEARCH_ENGINE
        set(value) { require(value in SUPPORTED_SEARCH_ENGINES); put(KEY_SEARCH_ENGINE, value) }

    var defaultDisplayMode: DefaultDisplayMode
        get() = enumValue(storage.get(TABLE, KEY_DISPLAY_MODE)?.get("value"), DefaultDisplayMode.MOBILE)
        set(value) = put(KEY_DISPLAY_MODE, value.name)

    var autoClearHistoryDays: Int
        get() = storage.get(TABLE, KEY_HISTORY_RETENTION)?.get("value")?.toIntOrNull()?.takeIf { it in ALLOWED_RETENTION_DAYS } ?: 0
        set(value) { require(value in ALLOWED_RETENTION_DAYS); put(KEY_HISTORY_RETENTION, value.toString()) }

    var blockGamblingSites: Boolean
        get() = storage.get(TABLE, KEY_BLOCK_GAMBLING)?.get("value")?.toBooleanStrictOrNull() ?: false
        set(value) = put(KEY_BLOCK_GAMBLING, value.toString())

    private fun put(key: String, value: String) = storage.set(TABLE, key, mapOf("value" to value))
    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val TABLE = "settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_DISPLAY_MODE = "default_display_mode"
        const val KEY_HISTORY_RETENTION = "auto_clear_history_days"
        const val KEY_BLOCK_GAMBLING = "block_gambling_sites"
        const val DEFAULT_SEARCH_ENGINE = "DUCKDUCKGO"
        val SUPPORTED_SEARCH_ENGINES = setOf("DUCKDUCKGO", "GOOGLE")
        val ALLOWED_RETENTION_DAYS = setOf(0, 1, 7, 30, 90)
    }
}
