package com.rain.browser.browser

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

enum class SearchEngine {
    DUCKDUCKGO,
    GOOGLE
}

object BrowserUrlResolver {
    fun resolve(input: String, searchEngine: SearchEngine): String {
        val value = input.trim()
        if (value.isEmpty()) return ""

        val hostWithPort = HOST_PORT_PATTERN.matches(value)
        val explicitScheme = SCHEME_PATTERN.containsMatchIn(value) && !hostWithPort
        val candidate = when {
            explicitScheme -> value
            looksLikeHost(value) -> "https://$value"
            else -> return search(value, searchEngine)
        }

        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return search(value, searchEngine)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val isSafeWebUrl = (scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null

        return if (isSafeWebUrl) uri.toASCIIString() else search(value, searchEngine)
    }

    private fun looksLikeHost(value: String): Boolean {
        if (value.any(Char::isWhitespace)) return false
        val hostPart = value.substringBefore('/').substringBefore('?').substringBefore('#')
        return hostPart.equals("localhost", ignoreCase = true) ||
            hostPart.contains('.') ||
            hostPart.equals("localhost", ignoreCase = true) ||
            HOST_PORT_PATTERN.matches(value) ||
            IPV4_PATTERN.matches(hostPart)
    }

    private fun search(query: String, searchEngine: SearchEngine): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return when (searchEngine) {
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
            SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encoded"
        }
    }

    private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val IPV4_PATTERN = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?$")
    private val HOST_PORT_PATTERN = Regex("^[A-Za-z0-9.-]+:\\d{1,5}(?:/.*)?$")
}
