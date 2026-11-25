package com.rain.browser.privacy

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.rain.browser.R
import com.rain.browser.browser.BrowserUrlResolver
import com.rain.browser.browser.SearchEngine
import com.rain.browser.browser.WebViewCallbacks
import com.rain.browser.browser.WebViewFactory
import com.rain.browser.network.NetworkPolicyManager

/**
 * Runs in the dedicated :private process. The data-directory suffix must be
 * configured before this process creates or accesses any WebView instance.
 */
class PrivateBrowsingActivity : Activity(), WebViewCallbacks {
    private lateinit var addressInput: EditText
    private lateinit var webViewContainer: FrameLayout
    private var privateWebView: WebView? = null
    private var isClosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_browsing)

        addressInput = findViewById(R.id.private_address_input)
        webViewContainer = findViewById(R.id.private_webview_container)

        findViewById<Button>(R.id.private_go_button).setOnClickListener { openAddress() }
        findViewById<Button>(R.id.private_close_button).setOnClickListener { closePrivateSession() }
        addressInput.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (submit) {
                openAddress()
                true
            } else {
                false
            }
        }

        privateWebView = WebViewFactory(this).create(this).also { webView ->
            webViewContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            webView.loadDataWithBaseURL(null, PRIVATE_HOME_PAGE, "text/html", "UTF-8", null)
        }
    }

    private fun openAddress() {
        val webView = privateWebView ?: return
        val url = BrowserUrlResolver.resolve(addressInput.text.toString(), SearchEngine.DUCKDUCKGO)
        if (url.isBlank()) return
        if (!NetworkPolicyManager.isAllowedWebUrl(url)) {
            Toast.makeText(this, "Only HTTP and HTTPS websites are allowed", Toast.LENGTH_SHORT).show()
            return
        }
        webView.loadUrl(url)
        addressInput.clearFocus()
    }

    private fun closePrivateSession() {
        if (isClosing) return
        isClosing = true
        val webView = privateWebView
        webView?.stopLoading()
        webView?.clearHistory()
        webView?.clearFormData()
        webView?.clearCache(true)
        WebStorage.getInstance().deleteAllData()

        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            destroyPrivateWebView()
            finish()
        }
    }

    private fun destroyPrivateWebView() {
        val webView = privateWebView ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.webChromeClient = null
        webView.webViewClient = android.webkit.WebViewClient()
        webView.removeAllViews()
        webView.destroy()
        privateWebView = null
    }

    override fun onNavigationRequested(view: WebView, url: String) = Unit

    override fun onPageStarted(view: WebView, url: String) = Unit

    override fun onPageFinished(view: WebView, url: String) = Unit

    override fun onProgressChanged(view: WebView, progress: Int) = Unit

    override fun onTitleChanged(view: WebView, title: String) = Unit

    @Deprecated("Deprecated in Android API; retained for API 29 compatibility.")
    override fun onBackPressed() {
        closePrivateSession()
    }

    override fun onDestroy() {
        if (!isClosing) {
            privateWebView?.stopLoading()
            destroyPrivateWebView()
        }
        super.onDestroy()
    }

    private companion object {
        const val PRIVATE_DATA_DIRECTORY_SUFFIX = "rain_private"
        const val PRIVATE_HOME_PAGE = """
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
                    h1 { color: #38BDF8; }
                </style>
            </head>
            <body><main><h1>RAIN Private</h1><p>Private session is isolated from normal browsing data.</p></main></body>
            </html>
        """
    }
}
