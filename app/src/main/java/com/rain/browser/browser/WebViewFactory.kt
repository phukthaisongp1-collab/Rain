package com.rain.browser.browser

import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rain.browser.network.NetworkPolicyManager

interface WebViewCallbacks {
    fun onNavigationRequested(view: WebView, url: String)
    fun onPageStarted(view: WebView, url: String)
    fun onPageFinished(view: WebView, url: String)
    fun onProgressChanged(view: WebView, progress: Int)
    fun onTitleChanged(view: WebView, title: String)
}

class WebViewFactory(private val context: Context) {
    fun create(callbacks: WebViewCallbacks): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                useWideViewPort = false
                loadWithOverviewMode = false
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    if (!NetworkPolicyManager.isAllowedWebUrl(url)) return true
                    if (request.isForMainFrame) callbacks.onNavigationRequested(view, url)
                    return false
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    callbacks.onPageStarted(view, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    callbacks.onPageFinished(view, url)
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError
                ) {
                    handler.cancel()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    callbacks.onProgressChanged(view, newProgress)
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    callbacks.onTitleChanged(view, title.orEmpty())
                }
            }
        }
    }
}
