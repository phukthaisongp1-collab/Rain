package com.rain.browser.browser

import android.content.Context
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

interface WebViewCallbacks {
    fun onNavigationRequested(view: WebView, url: String)
    fun onCrossSiteNavigationRequested(view: WebView, url: String) = Unit
    fun onExternalNavigationRequested(view: WebView, url: String) = Unit
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
                allowFileAccess = BrowserSecurityPolicy.allowFileAccess
                allowContentAccess = BrowserSecurityPolicy.allowContentAccess
                allowFileAccessFromFileURLs = BrowserSecurityPolicy.allowFileAccessFromFileUrls
                allowUniversalAccessFromFileURLs = BrowserSecurityPolicy.allowUniversalAccessFromFileUrls
                mixedContentMode = BrowserSecurityPolicy.mixedContentMode
                javaScriptCanOpenWindowsAutomatically = BrowserSecurityPolicy.javaScriptCanOpenWindowsAutomatically
                setSupportMultipleWindows(BrowserSecurityPolicy.supportMultipleWindows)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                useWideViewPort = false
                loadWithOverviewMode = false
                cacheMode = WebSettings.LOAD_DEFAULT
                safeBrowsingEnabled = BrowserSecurityPolicy.safeBrowsingEnabled
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    return when (
                        NavigationGuardPolicy.evaluate(
                            currentUrl = view.url.orEmpty(),
                            destinationUrl = url,
                            isMainFrame = request.isForMainFrame,
                            hasGesture = request.hasGesture()
                        )
                    ) {
                        NavigationGuardPolicy.Decision.ALLOW_WEB -> {
                            if (request.isForMainFrame) callbacks.onNavigationRequested(view, url)
                            false
                        }
                        NavigationGuardPolicy.Decision.CONFIRM_CROSS_SITE ->
                            callbacks.onCrossSiteNavigationRequested(view, url).let { true }
                        NavigationGuardPolicy.Decision.CONFIRM_EXTERNAL ->
                            callbacks.onExternalNavigationRequested(view, url).let { true }
                        NavigationGuardPolicy.Decision.BLOCK -> true
                    }
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
                    when (BrowserSecurityPolicy.sslErrorAction) {
                        BrowserSecurityPolicy.SslErrorAction.CANCEL -> handler.cancel()
                    }
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
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message
                ): Boolean {
                    // Reject popup creation before a secondary WebView can be attached.
                    return false
                }

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
