package com.rain.browser.browser

import android.webkit.WebView

data class BrowserTab(
    val id: Long,
    val webView: WebView,
    var title: String = "New Tab",
    var url: String = "",
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var isDesktopMode: Boolean = false
)
