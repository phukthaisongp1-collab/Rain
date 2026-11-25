package com.rain.browser.privacy

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
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
    private var privateTestReceiverRegistered = false

    private val privateTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isDebuggableBuild()) return
            val resultReceiver = getTestResultReceiver(intent) ?: return

            when (intent.getStringExtra(EXTRA_OPERATION)) {
                OP_STATUS -> {
                    sendTestResult(resultReceiver, Bundle().apply {
                        putBoolean("ready", privateWebView != null)
                        putString("process_name", Application.getProcessName())
                    })
                }

                OP_SET_COOKIE -> {
                    val cookie = intent.getStringExtra(EXTRA_COOKIE).orEmpty()
                    if (cookie.isBlank()) {
                        sendTestError(resultReceiver, "Cookie value is empty")
                    } else {
                        val cookies = CookieManager.getInstance()
                        cookies.setCookie(TEST_URL, cookie)
                        cookies.flush()
                        sendTestResult(resultReceiver, Bundle().apply {
                            putString("cookie", cookies.getCookie(TEST_URL).orEmpty())
                        })
                    }
                }

                OP_GET_COOKIE -> {
                    sendTestResult(resultReceiver, Bundle().apply {
                        putString("cookie", CookieManager.getInstance().getCookie(TEST_URL).orEmpty())
                    })
                }

                OP_CLOSE -> {
                    closePrivateSession { remainingCookie ->
                        sendTestResult(resultReceiver, Bundle().apply {
                            putBoolean("closed", true)
                            putString("remaining_cookie", remainingCookie)
                        })
                    }
                }

                else -> sendTestError(resultReceiver, "Unknown private test operation")
            }
        }
    }

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
        registerPrivateTestReceiver()

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

    private fun registerPrivateTestReceiver() {
        if (!isDebuggableBuild()) return
        val filter = IntentFilter(ACTION_PRIVATE_TEST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(privateTestReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(privateTestReceiver, filter)
        }
        privateTestReceiverRegistered = true
    }

    private fun isDebuggableBuild(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun getTestResultReceiver(intent: Intent): ResultReceiver? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
    }

    private fun sendTestResult(receiver: ResultReceiver, data: Bundle) {
        receiver.send(Activity.RESULT_OK, data)
    }

    private fun sendTestError(receiver: ResultReceiver, message: String) {
        receiver.send(Activity.RESULT_CANCELED, Bundle().apply {
            putString("error", message)
        })
    }

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

    private fun closePrivateSession(onComplete: ((String) -> Unit)? = null) {
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
            val remainingCookie = CookieManager.getInstance().getCookie(TEST_URL).orEmpty()
            finish()
            onComplete?.invoke(remainingCookie)
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
        if (privateTestReceiverRegistered) {
            runCatching { unregisterReceiver(privateTestReceiver) }
            privateTestReceiverRegistered = false
        }
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

    companion object {
        internal const val ACTION_PRIVATE_TEST = "com.rain.browser.action.PRIVATE_TEST"
        internal const val EXTRA_OPERATION = "private_test_operation"
        internal const val EXTRA_COOKIE = "private_test_cookie"
        internal const val EXTRA_RESULT_RECEIVER = "private_test_result_receiver"
        internal const val OP_STATUS = "status"
        internal const val OP_SET_COOKIE = "set_cookie"
        internal const val OP_GET_COOKIE = "get_cookie"
        internal const val OP_CLOSE = "close"

        private const val TEST_URL = "https://rain-private-isolation.test/"

        private const val PRIVATE_HOME_PAGE = """
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
