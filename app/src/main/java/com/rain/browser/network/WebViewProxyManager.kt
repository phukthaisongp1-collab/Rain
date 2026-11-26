package com.rain.browser.network

import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Optional process-scoped proxy support for an explicitly configured, user-authorized proxy.
 *
 * No proxy is applied automatically. Callers must obtain explicit user consent before calling
 * [applyUserConfiguredProxy]. The bypass list keeps the approved sensitive domains on direct
 * connections while a proxy is active.
 */
object WebViewProxyManager {
    enum class Result {
        APPLIED,
        CLEARED,
        UNSUPPORTED,
        INVALID_CONFIGURATION,
        FAILED
    }

    data class ProxyEndpoint(
        val scheme: String,
        val authority: String
    ) {
        val proxyUrl: String
            get() = "$scheme://$authority"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command ->
        mainHandler.post(command)
        Unit
    }

    private val bypassDomains = listOf(
        "scb.co.th",
        "kbank.com",
        "go.th",
        "gmail.com",
        "outlook.com"
    )

    /**
     * Returns whether the installed Android System WebView provider supports proxy overrides.
     */
    fun isProxyOverrideSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /**
     * Parses a proxy endpoint without resolving its host or making a network request.
     *
     * Supported forms are http://host[:port], https://host[:port], and socks://host[:port].
     * Credentials and URL paths are deliberately rejected because this API does not provide
     * a safe credential-management contract for this application.
     */
    fun parseProxyEndpoint(rawValue: String): ProxyEndpoint? {
        if (rawValue.isBlank() || rawValue != rawValue.trim()) return null

        val uri = runCatching { URI(rawValue) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme !in setOf("http", "https", "socks")) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.rawPath.isNullOrEmpty().not()) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null

        val authority = uri.rawAuthority ?: return null
        if (authority.isBlank() || authority.any(Char::isWhitespace)) return null

        return ProxyEndpoint(scheme = scheme, authority = authority)
    }

    /**
     * The candidate domains are direct-connection bypasses, not destinations routed through
     * a proxy. Each root and its subdomains are represented separately.
     */
    fun approvedBypassRules(): List<String> =
        bypassDomains.flatMap { domain -> listOf(domain, "*.$domain") }

    /**
     * Mirrors the approved bypass-domain policy for deterministic tests and diagnostics.
     */
    fun isApprovedBypassHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (host.isBlank()) return false
        return bypassDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    /**
     * Applies one explicit proxy to WebView requests in the current process.
     *
     * There is intentionally no automatic proxy, public/free proxy, secondary proxy, or direct
     * fallback. Approved bypass domains connect directly. The caller must wait for the callback
     * before loading or reloading a page.
     */
    fun applyUserConfiguredProxy(
        rawProxyEndpoint: String,
        callback: (Result) -> Unit
    ) {
        val endpoint = parseProxyEndpoint(rawProxyEndpoint)
        if (endpoint == null) {
            dispatch(callback, Result.INVALID_CONFIGURATION)
            return
        }
        if (!isProxyOverrideSupported()) {
            dispatch(callback, Result.UNSUPPORTED)
            return
        }

        val configBuilder = ProxyConfig.Builder().addProxyRule(endpoint.proxyUrl)
        approvedBypassRules().forEach(configBuilder::addBypassRule)

        runCatching {
            ProxyController.getInstance().setProxyOverride(
                configBuilder.build(),
                mainExecutor,
                Runnable { callback(Result.APPLIED) }
            )
        }.onFailure { error ->
            val result = if (error is UnsupportedOperationException) {
                Result.UNSUPPORTED
            } else {
                Result.FAILED
            }
            dispatch(callback, result)
        }
    }

    /**
     * Clears RAIN's process-scoped override and returns WebView to the Android system proxy
     * configuration. Wait for the callback before loading or reloading a page.
     */
    fun clearUserConfiguredProxy(callback: (Result) -> Unit) {
        if (!isProxyOverrideSupported()) {
            dispatch(callback, Result.UNSUPPORTED)
            return
        }

        runCatching {
            ProxyController.getInstance().clearProxyOverride(
                mainExecutor,
                Runnable { callback(Result.CLEARED) }
            )
        }.onFailure { error ->
            val result = if (error is UnsupportedOperationException) {
                Result.UNSUPPORTED
            } else {
                Result.FAILED
            }
            dispatch(callback, result)
        }
    }

    private fun dispatch(callback: (Result) -> Unit, result: Result) {
        mainHandler.post { callback(result) }
    }
}
