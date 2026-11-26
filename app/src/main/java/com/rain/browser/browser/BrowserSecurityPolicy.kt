package com.rain.browser.browser

import android.webkit.WebSettings

/**
 * Security defaults applied to every RAIN WebView instance.
 *
 * SSL certificate errors are always cancelled; there is intentionally no
 * user-facing proceed or certificate-exception path.
 */
object BrowserSecurityPolicy {
    const val allowFileAccess = false
    const val allowContentAccess = false
    const val allowFileAccessFromFileUrls = false
    const val allowUniversalAccessFromFileUrls = false
    const val mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    const val safeBrowsingEnabled = true
    const val javaScriptCanOpenWindowsAutomatically = false
    const val supportMultipleWindows = false

    enum class SslErrorAction {
        CANCEL
    }

    val sslErrorAction: SslErrorAction = SslErrorAction.CANCEL
}
