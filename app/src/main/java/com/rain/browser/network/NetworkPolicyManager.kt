package com.rain.browser.network

import java.net.URI
import java.util.Locale

object NetworkPolicyManager {
    fun isAllowedWebUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        return (scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null
    }
}
