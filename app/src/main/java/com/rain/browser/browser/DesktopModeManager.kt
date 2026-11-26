package com.rain.browser.browser

import android.content.Context
import java.net.URI
import java.util.Locale

class DesktopModeManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isDesktopFor(url: String): Boolean {
        val host = hostFor(url) ?: return false
        return preferences.getBoolean("$HOST_KEY_PREFIX$host", false)
    }

    fun hasPreferenceFor(url: String): Boolean {
        val host = hostFor(url) ?: return false
        return preferences.contains("$HOST_KEY_PREFIX$host")
    }

    fun setDesktopFor(url: String, enabled: Boolean): Boolean {
        val host = hostFor(url) ?: return false
        preferences.edit().putBoolean("$HOST_KEY_PREFIX$host", enabled).apply()
        return true
    }

    fun hostFor(url: String): String? = runCatching {
        URI(url).host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "rain_browser_preferences"
        const val HOST_KEY_PREFIX = "desktop_mode_"
    }
}
