package com.rain.browser.permissions

import android.content.Context
import java.net.URI
import java.util.Locale

class PermissionManager(context: Context) {
    enum class SitePermission(val label: String) {
        CAMERA("Camera"),
        MICROPHONE("Microphone"),
        LOCATION("Location")
    }

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isGranted(origin: String, permission: SitePermission): Boolean {
        val normalizedOrigin = normalizeOrigin(origin) ?: return false
        return preferences.getBoolean(permissionKey(normalizedOrigin, permission), false)
    }

    fun grant(origin: String, permission: SitePermission) {
        val normalizedOrigin = normalizeOrigin(origin) ?: return
        preferences.edit().putBoolean(permissionKey(normalizedOrigin, permission), true).apply()
    }

    fun revoke(origin: String, permission: SitePermission) {
        val normalizedOrigin = normalizeOrigin(origin) ?: return
        preferences.edit().remove(permissionKey(normalizedOrigin, permission)).apply()
    }

    fun revokeAll(origin: String) {
        val normalizedOrigin = normalizeOrigin(origin) ?: return
        preferences.edit().apply {
            SitePermission.values().forEach { remove(permissionKey(normalizedOrigin, it)) }
        }.apply()
    }

    fun getSiteGrants(): Map<String, Set<SitePermission>> {
        val result = linkedMapOf<String, MutableSet<SitePermission>>()
        preferences.all.forEach { (key, value) ->
            if (value != true) return@forEach
            val separator = key.lastIndexOf(SEPARATOR)
            if (separator <= 0) return@forEach
            val origin = key.substring(0, separator)
            val permission = runCatching {
                SitePermission.valueOf(key.substring(separator + SEPARATOR.length))
            }.getOrNull() ?: return@forEach
            result.getOrPut(origin) { linkedSetOf() }.add(permission)
        }
        return result.mapValues { it.value.toSet() }.toSortedMap()
    }

    companion object {
        private const val PREFERENCES_NAME = "rain_site_permissions"
        private const val SEPARATOR = "::"

        fun normalizeOrigin(rawOrigin: String): String? {
            val uri = runCatching { URI(rawOrigin.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
            if (scheme != "https" || uri.rawUserInfo != null) return null
            if (uri.query != null || uri.fragment != null) return null
            if (uri.path.isNotEmpty() && uri.path != "/") return null
            val port = if (uri.port == 443 || uri.port == -1) "" else ":${uri.port}"
            return "https://$host$port"
        }

        private fun permissionKey(origin: String, permission: SitePermission): String =
            "$origin$SEPARATOR${permission.name}"
    }
}
