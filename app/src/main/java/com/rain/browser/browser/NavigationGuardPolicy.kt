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
        "ac.za", "co.za", "gov.za", "net.za", "org.za",
        "co.kr", "go.kr", "ne.kr", "or.kr",
        "com.ar", "com.co", "com.mx", "com.ph", "com.pk", "com.tr", "com.vn",
        "co.id", "go.id", "or.id", "ac.id",
        "co.il", "org.il", "net.il",
        "co.ke", "or.ke", "go.ke", "ac.ke",
        "com.ng", "org.ng", "net.ng",
        "com.eg", "com.sa", "com.ae", "co.ae", "com.qa",
        "github.io", "appspot.com", "blogspot.com", "pages.dev",
        "vercel.app", "netlify.app", "herokuapp.com", "web.app",
        "firebaseapp.com", "wordpress.com"
    )

    private val commonSingleLabelPublicSuffixes = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int",
        "io", "app", "dev", "ai", "me", "info", "biz", "xyz",
        "online", "site", "store", "tech", "cloud", "pro", "name",
        "tv", "cc", "co", "th", "uk", "de", "fr", "jp", "au", "nz",
        "sg", "my", "in", "br", "cn", "hk", "tw", "mx", "ru", "ua",
        "pl", "es", "it", "nl", "be", "ch", "se", "no", "dk", "fi",
        "ca", "us", "id", "kr", "za", "ng", "ke", "eg", "sa", "ae",
        "qa", "pk", "ph", "vn", "tr", "gr", "pt", "il", "ir", "ie",
        "at", "cz", "sk", "hu", "ro", "bg", "hr", "si", "rs", "lt",
        "lv", "ee", "ge", "am", "az", "kz", "uz", "mn", "kh", "la",
        "mm", "bd", "lk", "np", "af", "iq", "jo", "lb", "ps", "cy",
        "mt", "lu", "li", "ad", "mc", "sm", "va", "to", "ws", "fm",
        "gg", "je", "im"
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
            if (isHttpsUrl(currentUrl) && isHttpUrl(destinationUrl) && !hasGesture) {
                return Decision.BLOCK
            }
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
            rawUrl.any { it == '\r' || it == '\n' || it == '\u0000' }
        ) return false

        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (uri.rawUserInfo != null) return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme !in allowedExternalSchemes) return false

        val schemeSpecificPart = uri.rawSchemeSpecificPart
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val normalizedPart = schemeSpecificPart.lowercase(Locale.ROOT)
        if ("%0d" in normalizedPart || "%0a" in normalizedPart) return false

        return when (scheme) {
            "mailto" -> schemeSpecificPart.contains('@') &&
                !schemeSpecificPart.contains('\\')
            "tel", "sms" -> schemeSpecificPart.matches(Regex("^[+0-9().,;*# -]{3,40}$"))
            "geo" -> schemeSpecificPart.matches(
                Regex("^-?\\d{1,2}(\\.\\d+)?,-?\\d{1,3}(\\.\\d+)?$")
            )
            else -> false
        }
    }

    private fun isHttpsUrl(rawUrl: String): Boolean =
        runCatching { URI(rawUrl.trim()).scheme.equals("https", ignoreCase = true) }
            .getOrDefault(false)

    private fun isHttpUrl(rawUrl: String): Boolean =
        runCatching { URI(rawUrl.trim()).scheme.equals("http", ignoreCase = true) }
            .getOrDefault(false)

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
        val labelCount = when {
            lastTwo in multiLabelPublicSuffixes -> 3
            labels.last() in commonSingleLabelPublicSuffixes -> 2
            else -> return host
        }
        return labels.takeLast(labelCount.coerceAtMost(labels.size)).joinToString(".")
    }

    private fun isIpAddress(host: String): Boolean =
        host.contains(':') || host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
}
