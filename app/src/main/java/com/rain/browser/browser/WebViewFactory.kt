package com.rain.browser.browser

import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
    fun onWebPermissionRequested(view: WebView, request: PermissionRequest)
    fun onWebPermissionRequestCanceled(request: PermissionRequest)
    fun onSafeBrowsingThreat(view: WebView, url: String, threatType: Int)
    fun onGeolocationPermissionRequested(
        origin: String,
        callback: GeolocationPermissions.Callback
    )
    fun onDownloadRequested(
        view: WebView,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    )
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

                override fun onSafeBrowsingHit(
                    view: WebView,
                    request: WebResourceRequest,
                    threatType: Int,
                    callback: android.webkit.SafeBrowsingResponse
                ) {
                    callbacks.onSafeBrowsingThreat(view, request.url.toString(), threatType)
                    // Keep the WebView provider's security interstitial in control.
                    // Do not allow navigation through a detected Safe Browsing threat.
                    callback.showInterstitial(true)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    callbacks.onWebPermissionRequested(this@apply, request)
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    callbacks.onWebPermissionRequestCanceled(request)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback
                ) {
                    callbacks.onGeolocationPermissionRequested(origin, callback)
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    callbacks.onProgressChanged(view, newProgress)
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    callbacks.onTitleChanged(view, title.orEmpty())
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                callbacks.onDownloadRequested(
                    this,
                    url,
                    userAgent.orEmpty(),
                    contentDisposition.orEmpty(),
                    mimeType.orEmpty(),
                    contentLength
                )
            }
        }
    }
}
