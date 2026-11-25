package com.rain.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.rain.browser.browser.BrowserTab
import com.rain.browser.browser.BrowserUrlResolver
import com.rain.browser.browser.DesktopModeManager
import com.rain.browser.browser.SearchEngine
import com.rain.browser.browser.TabManager
import com.rain.browser.browser.WebViewCallbacks
import com.rain.browser.browser.WebViewFactory

class MainActivity : Activity(), WebViewCallbacks {
    private lateinit var tabManager: TabManager
    private lateinit var desktopModeManager: DesktopModeManager
    private lateinit var webViewFactory: WebViewFactory
    private lateinit var webViewContainer: FrameLayout
    private lateinit var addressInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var tabsButton: Button
    private lateinit var reloadButton: Button
    private lateinit var searchEngineButton: Button

    private var searchEngine = SearchEngine.DUCKDUCKGO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(15, 23, 42)
        setContentView(R.layout.activity_main)

        tabManager = TabManager()
        desktopModeManager = DesktopModeManager(this)
        webViewFactory = WebViewFactory(this)
        bindViews()
        bindActions()
        restoreTabs(savedInstanceState)
    }

    private fun bindViews() {
        webViewContainer = findViewById(R.id.webview_container)
        addressInput = findViewById(R.id.address_input)
        progressBar = findViewById(R.id.page_progress)
        statusText = findViewById(R.id.status_text)
        modeText = findViewById(R.id.mode_text)
        tabsButton = findViewById(R.id.tabs_button)
        reloadButton = findViewById(R.id.reload_button)
        searchEngineButton = findViewById(R.id.search_engine_button)
        updateSearchEngineButton()
    }

    private fun bindActions() {
        findViewById<Button>(R.id.go_button).setOnClickListener { openAddress() }
        findViewById<Button>(R.id.back_button).setOnClickListener {
            tabManager.activeTab?.webView?.let { if (it.canGoBack()) it.goBack() }
            updateBrowserControls()
        }
        findViewById<Button>(R.id.forward_button).setOnClickListener {
            tabManager.activeTab?.webView?.let { if (it.canGoForward()) it.goForward() }
            updateBrowserControls()
        }
        reloadButton.setOnClickListener {
            val webView = tabManager.activeTab?.webView ?: return@setOnClickListener
            if (tabManager.activeTab?.isLoading == true) webView.stopLoading() else webView.reload()
            updateBrowserControls()
        }
        findViewById<Button>(R.id.home_button).setOnClickListener { loadHome() }
        findViewById<Button>(R.id.new_tab_button).setOnClickListener { createTab() }
        tabsButton.setOnClickListener { showTabsDialog() }
        searchEngineButton.setOnClickListener { toggleSearchEngine() }
        findViewById<Button>(R.id.desktop_button).setOnClickListener { toggleDesktopMode() }
        findViewById<Button>(R.id.zoom_out_button).setOnClickListener {
            tabManager.activeTab?.webView?.zoomOut()
        }
        findViewById<Button>(R.id.zoom_in_button).setOnClickListener {
            tabManager.activeTab?.webView?.zoomIn()
        }
        findViewById<Button>(R.id.text_out_button).setOnClickListener { changeTextZoom(-10) }
        findViewById<Button>(R.id.text_in_button).setOnClickListener { changeTextZoom(10) }

        addressInput.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isGo) {
                openAddress()
                true
            } else {
                false
            }
        }
    }

    private fun restoreTabs(savedInstanceState: Bundle?) {
        val savedTabs = savedInstanceState?.getParcelableArrayList<Bundle>(STATE_TABS)
        if (savedTabs.isNullOrEmpty()) {
            createTab()
            return
        }

        savedTabs.forEach { savedTab ->
            val webView = webViewFactory.create(this)
            val tab = tabManager.add(webView)
            tab.title = savedTab.getString(STATE_TITLE).orEmpty().ifBlank { "New Tab" }
            tab.url = savedTab.getString(STATE_URL).orEmpty()
            tab.isDesktopMode = savedTab.getBoolean(STATE_DESKTOP, false)
            val restored = runCatching { webView.restoreState(savedTab) }.getOrNull()
            if (restored == null) {
                if (tab.url.isBlank()) showWelcomePage(webView) else loadUrlInTab(tab, tab.url)
            }
        }
        tabManager.selectAt(savedInstanceState.getInt(STATE_ACTIVE_INDEX, 0))
        showActiveTab()
    }

    private fun createTab() {
        val webView = webViewFactory.create(this)
        val tab = tabManager.add(webView)
        showActiveTab()
        showWelcomePage(tab.webView)
        updateBrowserControls()
    }

    private fun showWelcomePage(webView: WebView) {
        webView.loadDataWithBaseURL(null, WELCOME_PAGE, "text/html", "UTF-8", null)
    }

    private fun loadHome() {
        val tab = tabManager.activeTab ?: return
        tab.url = ""
        tab.title = "New Tab"
        showWelcomePage(tab.webView)
        updateBrowserControls()
    }

    private fun openAddress() {
        val resolvedUrl = BrowserUrlResolver.resolve(addressInput.text.toString(), searchEngine)
        if (resolvedUrl.isBlank()) {
            loadHome()
            return
        }
        val tab = tabManager.activeTab ?: return
        applyDesktopSettings(tab.webView, desktopModeManager.isDesktopFor(resolvedUrl))
        tab.url = resolvedUrl
        tab.webView.loadUrl(resolvedUrl)
        hideKeyboard()
        addressInput.clearFocus()
    }

    private fun loadUrlInTab(tab: BrowserTab, url: String) {
        if (url.isBlank()) {
            showWelcomePage(tab.webView)
            return
        }
        if (!com.rain.browser.network.NetworkPolicyManager.isAllowedWebUrl(url)) {
            showWelcomePage(tab.webView)
            return
        }
        applyDesktopSettings(tab.webView, desktopModeManager.isDesktopFor(url))
        tab.webView.loadUrl(url)
    }

    private fun showActiveTab() {
        val tab = tabManager.activeTab ?: return
        (tab.webView.parent as? android.view.ViewGroup)?.removeView(tab.webView)
        webViewContainer.removeAllViews()
        webViewContainer.addView(
            tab.webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        updateBrowserControls()
    }

    private fun showTabsDialog() {
        val tabs = tabManager.tabs
        if (tabs.isEmpty()) return
        val labels = tabs.mapIndexed { index, tab ->
            val title = tab.title.ifBlank { "New Tab" }
            "${index + 1}. $title"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Open tabs (${tabs.size})")
            .setItems(labels) { _, which ->
                tabs.getOrNull(which)?.let { tabManager.select(it.id) }
                showActiveTab()
            }
            .setPositiveButton("New tab") { _, _ -> createTab() }
            .setNegativeButton("Close current") { _, _ -> closeCurrentTab() }
            .show()
    }

    private fun closeCurrentTab() {
        val removed = tabManager.closeActive() ?: return
        destroyWebView(removed.webView)
        if (tabManager.activeTab == null) {
            createTab()
        } else {
            showActiveTab()
        }
    }

    private fun toggleSearchEngine() {
        searchEngine = if (searchEngine == SearchEngine.DUCKDUCKGO) {
            SearchEngine.GOOGLE
        } else {
            SearchEngine.DUCKDUCKGO
        }
        updateSearchEngineButton()
        Toast.makeText(this, "Search: ${searchEngineLabel()}", Toast.LENGTH_SHORT).show()
    }

    private fun searchEngineLabel(): String =
        if (searchEngine == SearchEngine.DUCKDUCKGO) "DuckDuckGo" else "Google"

    private fun updateSearchEngineButton() {
        searchEngineButton.text = "Search: ${searchEngineLabel()}"
    }

    private fun toggleDesktopMode() {
        val tab = tabManager.activeTab ?: return
        val url = tab.webView.url.orEmpty().ifBlank { tab.url }
        if (!desktopModeManager.setDesktopFor(url, !desktopModeManager.isDesktopFor(url))) {
            Toast.makeText(this, "Open a website before changing display mode", Toast.LENGTH_SHORT).show()
            return
        }
        val enabled = desktopModeManager.isDesktopFor(url)
        applyDesktopSettings(tab.webView, enabled)
        tab.isDesktopMode = enabled
        tab.webView.reload()
        updateBrowserControls()
    }

    private fun applyDesktopSettings(webView: WebView, enabled: Boolean) {
        val defaultAgent = WebSettings.getDefaultUserAgent(this)
        val desktopAgent = defaultAgent
            .replace(Regex("\\(Linux; Android [^)]+\\)"), "(X11; Linux x86_64)")
            .replace("; wv", "")
            .replace(" Mobile", "")
            .replace(" Version/4.0", "")
        webView.settings.apply {
            userAgentString = if (enabled) desktopAgent else defaultAgent
            useWideViewPort = enabled
            loadWithOverviewMode = enabled
        }
    }

    private fun changeTextZoom(delta: Int) {
        val settings = tabManager.activeTab?.webView?.settings ?: return
        settings.textZoom = (settings.textZoom + delta).coerceIn(50, 200)
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(addressInput.windowToken, 0)
    }

    private fun updateBrowserControls() {
        val tab = tabManager.activeTab
        if (tab == null) {
            statusText.text = "No tab"
            modeText.text = "Mobile"
            tabsButton.text = "Tabs (0)"
            progressBar.visibility = View.GONE
            return
        }
        if (!addressInput.hasFocus()) {
            addressInput.setText(tab.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty())
        }
        statusText.text = tab.title.ifBlank { "RAIN Browser" }
        modeText.text = if (tab.isDesktopMode) "Desktop" else "Mobile"
        tabsButton.text = "Tabs (${tabManager.tabs.size})"
        reloadButton.text = if (tab.isLoading) "Stop" else "Reload"
        progressBar.progress = tab.progress
        progressBar.visibility = if (tab.isLoading) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.back_button).isEnabled = tab.webView.canGoBack()
        findViewById<Button>(R.id.forward_button).isEnabled = tab.webView.canGoForward()
    }

    override fun onNavigationRequested(view: WebView, url: String) {
        val tab = tabManager.findByWebView(view) ?: return
        val desktop = desktopModeManager.isDesktopFor(url)
        applyDesktopSettings(view, desktop)
        tab.isDesktopMode = desktop
    }

    override fun onPageStarted(view: WebView, url: String) {
        runOnUiThread {
            val tab = tabManager.findByWebView(view) ?: return@runOnUiThread
            tab.isLoading = true
            tab.progress = 0
            if (url.startsWith("http://") || url.startsWith("https://")) tab.url = url
            tab.isDesktopMode = desktopModeManager.isDesktopFor(url)
            if (tab == tabManager.activeTab) updateBrowserControls()
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        runOnUiThread {
            val tab = tabManager.findByWebView(view) ?: return@runOnUiThread
            tab.isLoading = false
            tab.progress = 100
            if (url.startsWith("http://") || url.startsWith("https://")) {
                tab.url = url
                tab.isDesktopMode = desktopModeManager.isDesktopFor(url)
            } else {
                tab.url = ""
            }
            if (tab == tabManager.activeTab) updateBrowserControls()
        }
    }

    override fun onProgressChanged(view: WebView, progress: Int) {
        runOnUiThread {
            val tab = tabManager.findByWebView(view) ?: return@runOnUiThread
            tab.progress = progress
            tab.isLoading = progress < 100
            if (tab == tabManager.activeTab) updateBrowserControls()
        }
    }

    override fun onTitleChanged(view: WebView, title: String) {
        runOnUiThread {
            val tab = tabManager.findByWebView(view) ?: return@runOnUiThread
            if (title.isNotBlank()) tab.title = title
            if (tab == tabManager.activeTab) updateBrowserControls()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val states = ArrayList<Bundle>()
        tabManager.tabs.forEach { tab ->
            val state = Bundle()
            tab.webView.saveState(state)
            state.putString(STATE_TITLE, tab.title)
            state.putString(STATE_URL, tab.url)
            state.putBoolean(STATE_DESKTOP, tab.isDesktopMode)
            states.add(state)
        }
        outState.putParcelableArrayList(STATE_TABS, states)
        outState.putInt(STATE_ACTIVE_INDEX, tabManager.activeIndex)
        outState.putString(STATE_SEARCH_ENGINE, searchEngine.name)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        searchEngine = runCatching {
            SearchEngine.valueOf(savedInstanceState.getString(STATE_SEARCH_ENGINE).orEmpty())
        }.getOrDefault(SearchEngine.DUCKDUCKGO)
        updateSearchEngineButton()
    }

    override fun onPause() {
        tabManager.tabs.forEach { it.webView.onPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        tabManager.tabs.forEach { it.webView.onResume() }
    }

    override fun onDestroy() {
        tabManager.clear().forEach { destroyWebView(it.webView) }
        super.onDestroy()
    }

    private fun destroyWebView(webView: WebView) {
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = android.webkit.WebViewClient()
        webView.removeAllViews()
        webView.destroy()
    }

    private companion object {
        const val STATE_TABS = "rain_state_tabs"
        const val STATE_ACTIVE_INDEX = "rain_state_active_index"
        const val STATE_SEARCH_ENGINE = "rain_state_search_engine"
        const val STATE_TITLE = "rain_state_title"
        const val STATE_URL = "rain_state_url"
        const val STATE_DESKTOP = "rain_state_desktop"

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
