package com.rain.browser.downloads

import android.net.Uri
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Keeps only candidate playlist URLs for the current WebView page.
 * No request headers, cookies, or authorization values are retained.
 */
class HlsPlaylistRegistry(
    private val maximumEntries: Int = MAX_ENTRIES
) {
    private val lock = Any()
    private val urls = LinkedHashMap<String, String>()

    init {
        require(maximumEntries in 1..MAX_ENTRIES)
    }

    fun add(rawUrl: String) {
        val normalized = rawUrl.trim()
        if (!HlsPlaylistUrlPolicy.isCandidate(normalized)) return

        synchronized(lock) {
            if (normalized in urls || urls.size >= maximumEntries) return
            urls[normalized] = normalized
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { urls.values.toList() }

    fun clear() {
        synchronized(lock) { urls.clear() }
    }

    companion object {
        const val MAX_ENTRIES = 200
    }
}

object HlsPlaylistUrlPolicy {
    fun resolveHttpUrl(baseUrl: String, reference: String): String {
        val resolved = URI(baseUrl).resolve(URI(reference))
        validateHttpUrl(resolved)
        return resolved.toString()
    }

    fun validateHttpUrl(rawUrl: String): String {
        val uri = URI(rawUrl.trim())
        validateHttpUrl(uri)
        return uri.toString()
    }

    private fun validateHttpUrl(uri: URI) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require((scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            uri.rawFragment == null
        ) { "HLS media URL must be a valid HTTP(S) URL without credentials or fragments." }
        require(!containsSensitiveQueryParameter(uri)) {
            "ไม่รองรับ URL ที่มี Token หรือข้อมูลยืนยันตัวตน"
        }
        val host = uri.host.lowercase(Locale.ROOT)
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local")) {
            "ไม่รองรับแหล่งสื่อจากเครือข่ายภายใน"
        }
    }

    private fun containsSensitiveQueryParameter(uri: URI): Boolean {
        val blocked = setOf(
            "token", "access_token", "auth", "authorization", "key",
            "signature", "sig", "policy", "credential", "session", "cookie"
        )
        return uri.rawQuery.orEmpty().split('&').any { component ->
            val rawName = component.substringBefore('=')
            val decodedName = runCatching {
                java.net.URLDecoder.decode(rawName, java.nio.charset.StandardCharsets.UTF_8.name())
            }.getOrDefault(rawName)
            decodedName.lowercase(Locale.ROOT) in blocked
        }
    }

    fun isCandidate(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return false

        if (uri.path.orEmpty().contains(".m3u8", ignoreCase = true)) return true
        return runCatching {
            uri.queryParameterNames.any { name ->
                uri.getQueryParameters(name).any { value ->
                    value.contains(".m3u8", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }
}
