package com.rain.browser

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rain.browser.network.NetworkPolicyManager

class MainActivity : Activity() {
    private var browserWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(15, 23, 42)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.browser_view)
        browserWebView = webView
        configureWebView(webView)
        webView.loadDataWithBaseURL(null, WELCOME_PAGE, "text/html", "UTF-8", null)
    }

    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = !NetworkPolicyManager.isAllowedWebUrl(request.url.toString())

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                handler.cancel()
            }
        }
    }

    override fun onDestroy() {
        browserWebView?.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        browserWebView = null
        super.onDestroy()
    }

    private companion object {
        const val WELCOME_PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="utf-8">
                <style>
                    :root { color-scheme: dark; }
                    body {
                        margin: 0; min-height: 100vh; display: grid; place-items: center;
                        background: #0F172A; color: #E2E8F0; font-family: sans-serif;
                        text-align: center;
                    }
                    main { padding: 24px; }
                    h1 { color: #38BDF8; letter-spacing: 0.12em; }
                    p { color: #CBD5E1; }
                </style>
            </head>
            <body><main><h1>RAIN</h1><p>Your browsing space is ready.</p></main></body>
            </html>
        """
    }
}
