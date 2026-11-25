package com.rain.browser.privacy

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
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
class PrivateBrowsingActivity : ComponentActivity(), WebViewCallbacks {
    private lateinit var addressInput: EditText
    private lateinit var webViewContainer: FrameLayout
    private var privateWebView: WebView? = null
    private var isClosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closePrivateSession()
            }
        })
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

        val retainedSession = lastCustomNonConfigurationInstance as? RetainedPrivateSession
        if (retainedSession != null) {
            initializePrivateWebView(retainedSession.url)
        } else {
            clearStalePrivateData {
                if (!isFinishing && !isDestroyed) initializePrivateWebView()
            }
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? =
        privateWebView?.let { RetainedPrivateSession(it.url) }

    private fun clearStalePrivateData(onComplete: () -> Unit) {
        clearPrivateBrowsingData(onComplete)
    }

    private fun initializePrivateWebView(initialUrl: String? = null) {
        privateWebView = WebViewFactory(this).create(this).also { webView ->
            webView.clearCache(true)
            webViewContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val safeInitialUrl = initialUrl?.takeIf(NetworkPolicyManager::isAllowedWebUrl)
            if (safeInitialUrl != null) {
                webView.loadUrl(safeInitialUrl)
            } else {
                webView.loadDataWithBaseURL(null, PRIVATE_HOME_PAGE, "text/html", "UTF-8", null)
            }
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
        privateWebView?.apply {
            stopLoading()
            clearHistory()
            clearFormData()
            clearCache(true)
        }
        destroyPrivateWebView()
        clearPrivateData {
            finish()
        }
    }

    private fun clearPrivateData(onComplete: () -> Unit) {
        clearPrivateBrowsingData(onComplete)
    }

    private fun clearPrivateBrowsingData(onComplete: () -> Unit) {
        val webStorage = WebStorage.getInstance()
        val cookieManager = CookieManager.getInstance()

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            val deletionStarted = runCatching {
                WebStorageCompat.deleteBrowsingData(webStorage) {
                    cookieManager.flush()
                    onComplete()
                }
            }.isSuccess
            if (deletionStarted) return
        }

        // Older WebView providers may not support DELETE_BROWSING_DATA.
        // This fallback cannot guarantee Service Worker data removal.
        webStorage.deleteAllData()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            onComplete()
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

    override fun onDestroy() {
        if (!isClosing) {
            isClosing = true
            privateWebView?.apply {
                stopLoading()
                clearHistory()
                clearFormData()
                clearCache(true)
            }
            destroyPrivateWebView()
            if (!isChangingConfigurations) {
                runCatching {
                    clearPrivateBrowsingData {}
                }
            }
        }
        super.onDestroy()
    }

    private data class RetainedPrivateSession(val url: String?)

    private companion object {
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
