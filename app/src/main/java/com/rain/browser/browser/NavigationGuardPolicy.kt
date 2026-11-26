package com.rain.browser.browser

import com.rain.browser.network.NetworkPolicyManager
import java.net.URI
import java.util.Locale

/**
 * Applies conservative navigation rules before WebView commits a main-frame URL.
 */
object NavigationGuardPolicy {
    enum class Decision {
        ALLOW_WEB,
        CONFIRM_CROSS_SITE,
        CONFIRM_EXTERNAL,
        BLOCK
    }

    private val allowedExternalSchemes = setOf("mailto", "tel", "sms", "geo")

    // Common multi-label public suffixes. Unknown suffixes use the usual final-two-label rule.
    private val multiLabelPublicSuffixes = setOf(
        "ac.th", "co.th", "go.th", "in.th", "or.th",
        "ac.uk", "co.uk", "gov.uk", "net.uk", "org.uk",
        "com.au", "edu.au", "gov.au", "net.au", "org.au",
        "co.nz", "net.nz", "org.nz",
        "co.jp", "ne.jp", "or.jp",
        "com.br", "com.cn", "com.hk", "com.my", "com.sg", "com.tw",
        "co.in", "firm.in", "net.in", "org.in",
        "github.io", "appspot.com", "blogspot.com", "pages.dev",
        "vercel.app", "netlify.app", "herokuapp.com", "web.app",
        "firebaseapp.com", "wordpress.com"
    )

    fun evaluate(
        currentUrl: String,
        destinationUrl: String,
        isMainFrame: Boolean,
        hasGesture: Boolean
    ): Decision {
        val isWebUrl = NetworkPolicyManager.isAllowedWebUrl(destinationUrl)
        if (!isMainFrame) return if (isWebUrl) Decision.ALLOW_WEB else Decision.BLOCK

        if (isWebUrl) {
            if (currentUrl.isBlank() || hasGesture || isSameSite(currentUrl, destinationUrl)) {
                return Decision.ALLOW_WEB
            }
            return Decision.CONFIRM_CROSS_SITE
        }

        return if (hasGesture && isAllowedExternalUrl(destinationUrl)) {
            Decision.CONFIRM_EXTERNAL
        } else {
            Decision.BLOCK
        }
    }

    fun isSameSite(firstUrl: String, secondUrl: String): Boolean {
        val firstHost = hostOf(firstUrl) ?: return false
        val secondHost = hostOf(secondUrl) ?: return false
        if (firstHost == secondHost) return true
        if (isIpAddress(firstHost) || isIpAddress(secondHost)) return false
        return registrableDomain(firstHost) == registrableDomain(secondHost)
    }

    fun isAllowedExternalUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || rawUrl.length > 2048 ||
            rawUrl.any { it == '\\r' || it == '\\n' || it == '\\u0000' }
        ) return false

        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (uri.rawUserInfo != null) return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme !in allowedExternalSchemes) return false

        val schemeSpecificPart = uri.rawSchemeSpecificPart
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: return false

        return when (scheme) {
            "mailto" -> schemeSpecificPart.contains('@') &&
                !schemeSpecificPart.contains('\\\\')
            "tel", "sms" -> schemeSpecificPart.matches(Regex("^[+0-9().,;*# -]{3,40}$"))
            "geo" -> schemeSpecificPart.matches(
                Regex("^-?\\\\d{1,2}(\\\\.\\\\d+)?,-?\\\\d{1,3}(\\\\.\\\\d+)?$")
            )
            else -> false
        }
    }

    private fun hostOf(rawUrl: String): String? {
        if (!NetworkPolicyManager.isAllowedWebUrl(rawUrl)) return null
        return runCatching { URI(rawUrl.trim()).host?.lowercase(Locale.ROOT)?.trimEnd('.') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun registrableDomain(host: String): String {
        val labels = host.split('.').filter { it.isNotBlank() }
        if (labels.size <= 1) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val labelCount = if (lastTwo in multiLabelPublicSuffixes) 3 else 2
        return labels.takeLast(labelCount.coerceAtMost(labels.size)).joinToString(".")
    }

    private fun isIpAddress(host: String): Boolean =
        host.contains(':') || host.matches(Regex("^\\\\d{1,3}(\\\\.\\\\d{1,3}){3}$"))
}
