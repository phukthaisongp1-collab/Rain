package com.rain.browser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.LocaleManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.Intent
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.rain.browser.browser.BrowserTab
import com.rain.browser.browser.NavigationGuardPolicy
import com.rain.browser.browser.BrowserUrlResolver
import com.rain.browser.browser.DesktopModeManager
import com.rain.browser.browser.SearchEngine
import com.rain.browser.browser.TabManager
import com.rain.browser.browser.WebViewCallbacks
import com.rain.browser.browser.WebViewFactory
import com.rain.browser.downloads.DownloadPolicy
import com.rain.browser.network.WebProtectionManager
import com.rain.browser.network.WebProtectionPresentation
import com.rain.browser.permissions.PermissionManager
import com.rain.browser.data.BookmarkRepository
import com.rain.browser.data.HistoryRepository
import com.rain.browser.data.SettingsRepository
import com.rain.browser.data.ResumeRepository
import java.util.concurrent.Executors
import com.rain.browser.data.SqliteStorage

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
    private lateinit var permissionManager: PermissionManager
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var resumeRepository: ResumeRepository
    private val storageExecutor = Executors.newSingleThreadExecutor()

    private data class PendingWebPermission(
        val request: PermissionRequest,
        val origin: String,
        val resources: Array<String>,
        val sitePermissions: List<PermissionManager.SitePermission>,
        val androidPermissions: Array<String>
    )

    private data class PendingGeoPermission(
        val origin: String,
        val callback: GeolocationPermissions.Callback
    )

    private var pendingWebPermission: PendingWebPermission? = null
    private var pendingGeoPermission: PendingGeoPermission? = null
    private var lastWebProtectionUrl: String? = null
    private var lastWebProtectionPageUrl: String? = null
    private var lastWebProtectionView: WebView? = null
    private var lastWebProtectionDecision: WebProtectionManager.Decision? = null
    private var searchEngine = SearchEngine.DUCKDUCKGO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(15, 23, 42)
        setContentView(R.layout.activity_main)

        tabManager = TabManager()
        desktopModeManager = DesktopModeManager(this)
        webViewFactory = WebViewFactory(this)
        permissionManager = PermissionManager(this)
        val storage = SqliteStorage(this)
        bookmarkRepository = BookmarkRepository(storage)
        historyRepository = HistoryRepository(storage)
        settingsRepository = SettingsRepository(storage)
        resumeRepository = ResumeRepository(storage)
        searchEngine = runCatching { SearchEngine.valueOf(settingsRepository.searchEngine) }.getOrDefault(SearchEngine.DUCKDUCKGO)
        applyAutomaticHistoryRetention()
        cleanupExpiredResumeEntries()
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
        findViewById<Button>(R.id.private_button).setOnClickListener {
            startActivity(Intent(this, com.rain.browser.privacy.PrivateBrowsingActivity::class.java))
        }
        findViewById<Button>(R.id.protection_button).setOnClickListener { showProtectionDialog() }
        findViewById<Button>(R.id.permissions_button).setOnClickListener { showSitePermissionManager() }
        findViewById<Button>(R.id.bookmarks_button).setOnClickListener { showBookmarksDialog() }
        findViewById<Button>(R.id.history_button).setOnClickListener { showHistoryDialog() }
        findViewById<Button>(R.id.resume_button).setOnClickListener { showResumeDialog() }
        findViewById<Button>(R.id.settings_button).setOnClickListener { showSettingsDialog() }

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
            restorePersistedTabs()
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

    private fun restorePersistedTabs() {
        val entries = resumeRepository.list()
        if (entries.isEmpty()) {
            createTab()
            return
        }
        entries.forEach { entry ->
            val webView = webViewFactory.create(this)
            val tab = tabManager.add(webView)
            tab.title = entry.title.ifBlank { entry.url }
            tab.url = entry.url
            loadUrlInTab(tab, entry.url)
        }
        tabManager.selectAt(0)
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
        val recentItems = resumeRepository.list().take(5).joinToString("") { entry ->
            "<li><a href=\"" + escapeHtml(entry.url) + "\">" +
                escapeHtml(entry.title.ifBlank { entry.url }) + "</a></li>"
        }.ifBlank { "<li>" + getString(R.string.new_tab_no_recent) + "</li>" }
        val bookmarkItems = bookmarkRepository.search().take(5).joinToString("") { entry ->
            "<li><a href=\"" + escapeHtml(entry.url) + "\">" +
                escapeHtml(entry.title.ifBlank { entry.url }) + "</a></li>"
        }.ifBlank { "<li>" + getString(R.string.new_tab_no_bookmarks) + "</li>" }
        val page = WELCOME_PAGE
            .replace("{{READY}}", escapeHtml(getString(R.string.new_tab_ready)))
            .replace("{{RECENT_TITLE}}", getString(R.string.new_tab_recent))
            .replace("{{BOOKMARK_TITLE}}", getString(R.string.new_tab_bookmarks))
            .replace("{{NO_RECENT}}", getString(R.string.new_tab_no_recent))
            .replace("{{NO_BOOKMARKS}}", getString(R.string.new_tab_no_bookmarks))
            .replace("{{RECENT}}", recentItems)
            .replace("{{BOOKMARKS}}", bookmarkItems)
        webView.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

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
        applyDesktopSettings(tab.webView, isDesktopForUrl(resolvedUrl))
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
        applyDesktopSettings(tab.webView, isDesktopForUrl(url))
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
        settingsRepository.searchEngine = searchEngine.name
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
        if (!desktopModeManager.setDesktopFor(url, !isDesktopForUrl(url))) {
            Toast.makeText(this, "Open a website before changing display mode", Toast.LENGTH_SHORT).show()
            return
        }
        val enabled = isDesktopForUrl(url)
        applyDesktopSettings(tab.webView, enabled)
        tab.isDesktopMode = enabled
        tab.webView.reload()
        updateBrowserControls()
    }

    private fun isDesktopForUrl(url: String): Boolean =
        if (desktopModeManager.hasPreferenceFor(url)) desktopModeManager.isDesktopFor(url)
        else settingsRepository.defaultDisplayMode == SettingsRepository.DefaultDisplayMode.DESKTOP

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


    private fun cleanupExpiredResumeEntries() {
        storageExecutor.execute { runCatching { resumeRepository.clearExpired() } }
    }

    private fun saveResumeSession() {
        val snapshots = tabManager.tabs.mapNotNull { tab ->
            val url = tab.webView.url.orEmpty().ifBlank { tab.url }
            if (url.startsWith("https://") || url.startsWith("http://")) Triple(url, tab.title, 0L) else null
        }
        if (snapshots.isNotEmpty()) {
            storageExecutor.execute {
                snapshots.forEach { (url, title, position) ->
                    runCatching { resumeRepository.save(url, title, position) }
                }
            }
        }
    }

    private fun showResumeDialog() {
        val entries = resumeRepository.list()
        val labels = entries.map { it.title.ifBlank { it.url } + "\n" + it.url }.toTypedArray()
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.resume_title)
            .setItems(labels) { _, which -> entries.getOrNull(which)?.let { openSavedUrl(it.url) } }
            .setNegativeButton("Close", null)
        if (entries.isEmpty()) builder.setMessage(R.string.resume_empty)
        builder.show()
    }


    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.settings_language, settingsRepository.language.name),
            getString(R.string.settings_search_engine, settingsRepository.searchEngine),
            getString(R.string.settings_default_display, settingsRepository.defaultDisplayMode.name),
            getString(R.string.settings_history_retention, settingsRepository.autoClearHistoryDays)
        )
        AlertDialog.Builder(this).setTitle(R.string.settings_title).setItems(options) { _, which ->
            when (which) {
                0 -> chooseLanguage()
                1 -> chooseSearchEngine()
                2 -> chooseDefaultDisplayMode()
                3 -> chooseHistoryRetention()
            }
        }.setNegativeButton(R.string.dialog_close, null).show()
    }

    private fun chooseLanguage() {
        val values = SettingsRepository.Language.values()
        AlertDialog.Builder(this).setTitle(R.string.language_title)
            .setItems(arrayOf(getString(R.string.language_system), getString(R.string.language_thai), getString(R.string.language_english))) { _, which ->
                val selected = values[which]
                settingsRepository.language = selected
                val tags = when (selected) {
                    SettingsRepository.Language.SYSTEM -> ""
                    SettingsRepository.Language.THAI -> "th"
                    SettingsRepository.Language.ENGLISH -> "en"
                }
                getSystemService(LocaleManager::class.java).applicationLocales =
                    if (tags.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tags)
                recreate()
            }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun chooseSearchEngine() {
        AlertDialog.Builder(this).setTitle(R.string.search_engine_title)
            .setItems(arrayOf("DuckDuckGo", "Google")) { _, which ->
                settingsRepository.searchEngine = if (which == 0) "DUCKDUCKGO" else "GOOGLE"
                searchEngine = if (which == 0) SearchEngine.DUCKDUCKGO else SearchEngine.GOOGLE
                updateSearchEngineButton()
            }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun chooseDefaultDisplayMode() {
        AlertDialog.Builder(this).setTitle(R.string.display_mode_title)
            .setItems(arrayOf(getString(R.string.mode_mobile), getString(R.string.mode_desktop))) { _, which ->
                settingsRepository.defaultDisplayMode = if (which == 0) {
                    SettingsRepository.DefaultDisplayMode.MOBILE
                } else SettingsRepository.DefaultDisplayMode.DESKTOP
            }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun chooseHistoryRetention() {
        val days = intArrayOf(0, 1, 7, 30, 90)
        val labels = arrayOf(
            getString(R.string.retention_never), getString(R.string.retention_one_day),
            getString(R.string.retention_seven_days), getString(R.string.retention_thirty_days),
            getString(R.string.retention_ninety_days)
        )
        AlertDialog.Builder(this).setTitle(R.string.history_retention_title)
            .setItems(labels) { _, which ->
                settingsRepository.autoClearHistoryDays = days[which]
                applyAutomaticHistoryRetention()
            }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun applyAutomaticHistoryRetention() {
        val days = settingsRepository.autoClearHistoryDays
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days.toLong() * HISTORY_DAY_MILLIS
            historyRepository.clearOlderThan(cutoff)
        }
    }

    private fun showBookmarksDialog(query: String = "") {
        val bookmarks = bookmarkRepository.search(query)
        val labels = bookmarks.map { "${it.title}\n${it.url}" }.toTypedArray()
        val builder = AlertDialog.Builder(this)
            .setTitle(if (query.isBlank()) "Bookmarks" else "Bookmarks: $query")
            .setItems(labels) { _, which ->
                bookmarks.getOrNull(which)?.let { bookmark ->
                    AlertDialog.Builder(this)
                        .setTitle(bookmark.title)
                        .setItems(arrayOf("Open", "Delete")) { _, action ->
                            when (action) {
                                0 -> openSavedUrl(bookmark.url)
                                1 -> {
                                    bookmarkRepository.delete(bookmark.id)
                                    showBookmarksDialog(query)
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setPositiveButton("Add current page") { _, _ -> addCurrentBookmark() }
            .setNeutralButton("Search") { _, _ -> promptDataSearch("Search bookmarks") { showBookmarksDialog(it) } }
            .setNegativeButton("Close", null)
        if (bookmarks.isEmpty()) builder.setMessage("No bookmarks found.")
        builder.show()
    }

    private fun addCurrentBookmark() {
        val tab = tabManager.activeTab ?: return
        val url = tab.webView.url.orEmpty().ifBlank { tab.url }
        if (url.isBlank()) {
            Toast.makeText(this, "Open a website before bookmarking", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { bookmarkRepository.add(url, tab.title) }
            .onSuccess { Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "This page cannot be bookmarked", Toast.LENGTH_LONG).show() }
    }

    private fun showHistoryDialog(query: String = "") {
        val entries = historyRepository.search(query)
        val labels = entries.map { "${it.title.ifBlank { it.url }}\n${it.url}" }.toTypedArray()
        val builder = AlertDialog.Builder(this)
            .setTitle(if (query.isBlank()) "History" else "History: $query")
            .setItems(labels) { _, which ->
                entries.getOrNull(which)?.let { entry ->
                    AlertDialog.Builder(this)
                        .setTitle(entry.title.ifBlank { entry.url })
                        .setItems(arrayOf("Open", "Delete")) { _, action ->
                            when (action) {
                                0 -> openSavedUrl(entry.url)
                                1 -> {
                                    historyRepository.delete(entry.id)
                                    showHistoryDialog(query)
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setPositiveButton("Clear all") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Clear browsing history?")
                    .setMessage("This removes normal browsing history from this device. Private Browsing data is stored separately.")
                    .setPositiveButton("Clear") { _, _ ->
                        historyRepository.clear()
                        Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("Search") { _, _ -> promptDataSearch("Search history") { showHistoryDialog(it) } }
            .setNegativeButton("Close", null)
        if (entries.isEmpty()) builder.setMessage("No browsing history found.")
        builder.show()
    }

    private fun promptDataSearch(title: String, onSearch: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Enter search text"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Search") { _, _ -> onSearch(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSavedUrl(url: String) {
        if (!com.rain.browser.network.NetworkPolicyManager.isAllowedWebUrl(url)) {
            Toast.makeText(this, "This saved address is not allowed", Toast.LENGTH_LONG).show()
            return
        }
        val tab = tabManager.activeTab ?: return
        tab.url = url
        applyDesktopSettings(tab.webView, isDesktopForUrl(url))
        tab.webView.loadUrl(url)
    }

    private fun showProtectionDialog() {
        val activeWebView = tabManager.activeTab?.webView
        val currentUrl = activeWebView?.url.orEmpty()
        val hasMatchingThreat = activeWebView != null &&
            activeWebView === lastWebProtectionView &&
            currentUrl == lastWebProtectionPageUrl
        val reportUrl = if (hasMatchingThreat) lastWebProtectionUrl.orEmpty() else currentUrl
        val decision = if (hasMatchingThreat) {
            lastWebProtectionDecision
        } else {
            null
        } ?: WebProtectionManager.Decision(
            action = WebProtectionManager.Action.ALLOW,
            reason = "Android WebView Safe Browsing is enabled. No external threat-intelligence feed is currently connected."
        )
        val message = if (reportUrl.isBlank()) {
            "Open a website to view its protection status."
        } else {
            WebProtectionPresentation.message(reportUrl, decision) +
                "\n\nFalse-positive reports are shared through Android's share sheet. RAIN does not upload reports automatically."
        }
        AlertDialog.Builder(this)
            .setTitle(WebProtectionPresentation.title(decision))
            .setMessage(message)
            .setPositiveButton(R.string.protection_report) { _, _ ->
                if (reportUrl.isNotBlank()) shareFalsePositiveReport(reportUrl, decision)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun shareFalsePositiveReport(
        url: String,
        decision: WebProtectionManager.Decision?
    ) {
        val safeReportUrl = safeReportOrigin(url)
        val safeDecision = decision?.copy(
            sourceReference = decision.sourceReference?.let(::safeReportOrigin)
        )
        val report = WebProtectionPresentation.falsePositiveReport(
            safeReportUrl,
            safeDecision,
            System.currentTimeMillis()
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "RAIN Browser false-positive report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(shareIntent, "Share false-positive report"))
    }

    private fun safeReportOrigin(rawUrl: String): String {
        val parsed = Uri.parse(rawUrl)
        val host = parsed.host ?: return rawUrl.substringBefore('?').substringBefore('#').take(256)
        val scheme = parsed.scheme?.takeIf { it == "https" || it == "http" } ?: "https"
        val port = parsed.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
        return "$scheme://$host$port"
    }

    private fun showSitePermissionManager() {
        val grants = permissionManager.getSiteGrants()
        val origins = grants.keys.toList()
        val builder = AlertDialog.Builder(this)
            .setTitle("Site permissions")
            .setPositiveButton("App notifications") { _, _ -> showAppNotificationPermission() }
            .setNegativeButton("Close", null)

        if (origins.isEmpty()) {
            builder.setMessage(
                "No site permissions have been granted. Camera, microphone, and location requests require your approval for each HTTPS origin. Website notifications are not supported by the current Android WebView; the separate button manages RAIN app notifications only."
            )
        } else {
            builder.setMessage(
                "Select an origin to review or revoke its saved permissions. Website Notification.requestPermission is not supported by the current Android WebView."
            )
            builder.setItems(origins.map { origin ->
                val labels = grants[origin].orEmpty().map { it.label }.joinToString(", ")
                "$origin\n$labels"
            }.toTypedArray()) { _, which ->
                origins.getOrNull(which)?.let { showSitePermissionActions(it) }
            }
        }
        builder.show()
    }

    private fun showSitePermissionActions(origin: String) {
        val grants = permissionManager.getSiteGrants()[origin].orEmpty().toList()
        if (grants.isEmpty()) {
            showSitePermissionManager()
            return
        }
        val labels = grants.map { "Revoke ${it.label}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(origin)
            .setItems(labels) { _, which ->
                grants.getOrNull(which)?.let { permission ->
                    permissionManager.revoke(origin, permission)
                    recreateTabsAfterPermissionRevocation()
                    Toast.makeText(this, "${permission.label} permission revoked", Toast.LENGTH_SHORT).show()
                    showSitePermissionManager()
                }
            }
            .setNeutralButton("Revoke all") { _, _ ->
                permissionManager.revokeAll(origin)
                recreateTabsAfterPermissionRevocation()
                Toast.makeText(this, "All site permissions revoked", Toast.LENGTH_SHORT).show()
                showSitePermissionManager()
            }
            .setNegativeButton("Close", null)
            .show()
    }


    private fun recreateTabsAfterPermissionRevocation() {
        val activeIndex = tabManager.activeIndex
        val snapshots = tabManager.tabs.map { tab ->
            val state = Bundle()
            runCatching { tab.webView.saveState(state) }
            state.putString(STATE_TITLE, tab.title)
            state.putString(STATE_URL, tab.url)
            state.putBoolean(STATE_DESKTOP, tab.isDesktopMode)
            state
        }
        tabManager.clear().forEach { destroyWebView(it.webView) }
        lastWebProtectionView = null
        lastWebProtectionUrl = null
        lastWebProtectionPageUrl = null
        lastWebProtectionDecision = null
        snapshots.forEach { state ->
            val webView = webViewFactory.create(this)
            val tab = tabManager.add(webView)
            tab.title = state.getString(STATE_TITLE).orEmpty().ifBlank { "New Tab" }
            tab.url = state.getString(STATE_URL).orEmpty()
            tab.isDesktopMode = state.getBoolean(STATE_DESKTOP, false)
            val restored = runCatching { webView.restoreState(state) }.getOrNull()
            if (restored == null) {
                if (tab.url.isBlank()) showWelcomePage(webView) else loadUrlInTab(tab, tab.url)
            } else if (tab.url.isNotBlank()) {
                applyDesktopSettings(webView, isDesktopForUrl(tab.url))
            }
        }
        if (tabManager.tabs.isEmpty()) {
            createTab()
        } else {
            tabManager.selectAt(activeIndex)
            showActiveTab()
        }
    }

    private fun showAppNotificationPermission() {
        val appNotificationsAllowed =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val status = if (appNotificationsAllowed) "allowed" else "not allowed"
        AlertDialog.Builder(this)
            .setTitle("Notification permissions")
            .setMessage(
                "Website Notification.requestPermission is not supported by the current Android WebView, so RAIN cannot grant website notifications per origin. RAIN app notifications are currently $status; this is a separate Android app-level setting."
            )
            .setPositiveButton("Open Android settings") { _, _ -> openAppNotificationSettings() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
    }

    override fun onWebPermissionRequested(view: WebView, request: PermissionRequest) {
        val origin = PermissionManager.normalizeOrigin(request.origin.toString())
        if (origin == null) {
            request.deny()
            return
        }

        val resources = request.resources?.toList().orEmpty()
        if (resources.isEmpty()) {
            request.deny()
            return
        }

        val mapped = resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> PermissionManager.SitePermission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> PermissionManager.SitePermission.MICROPHONE
                else -> null
            }
        }
        if (mapped.size != resources.size) {
            request.deny()
            return
        }

        val androidPermissions = buildList {
            if (PermissionManager.SitePermission.CAMERA in mapped) add(Manifest.permission.CAMERA)
            if (PermissionManager.SitePermission.MICROPHONE in mapped) add(Manifest.permission.RECORD_AUDIO)
        }.distinct().toTypedArray()

        if (pendingWebPermission != null) {
            request.deny()
            return
        }

        if (mapped.all { permissionManager.isGranted(origin, it) } &&
            androidPermissions.all(::isRuntimePermissionGranted)
        ) {
            runCatching { request.grant(resources.toTypedArray()) }
            return
        }

        val labels = mapped.map { it.label }.distinct().joinToString(" and ")
        AlertDialog.Builder(this)
            .setTitle("Website permission")
            .setMessage("$origin requests access to $labels. Allow this origin to use these device features?")
            .setPositiveButton("Allow") { _, _ ->
                pendingWebPermission = PendingWebPermission(
                    request, origin, resources.toTypedArray(), mapped, androidPermissions
                )
                val missing = androidPermissions.filterNot(::isRuntimePermissionGranted).toTypedArray()
                if (missing.isEmpty()) {
                    completeWebPermissionRequest(true)
                } else {
                    requestPermissions(missing, REQUEST_SITE_PERMISSION)
                }
            }
            .setNegativeButton("Deny") { _, _ -> runCatching { request.deny() } }
            .setOnCancelListener { runCatching { request.deny() } }
            .show()
    }

    override fun onWebPermissionRequestCanceled(request: PermissionRequest) {
        if (pendingWebPermission?.request == request) pendingWebPermission = null
    }

    override fun onGeolocationPermissionRequested(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        val normalizedOrigin = PermissionManager.normalizeOrigin(origin)
        if (normalizedOrigin == null) {
            callback.invoke(origin, false, false)
            return
        }

        if (permissionManager.isGranted(
                normalizedOrigin,
                PermissionManager.SitePermission.LOCATION
            ) && hasRuntimeLocationPermission()
        ) {
            callback.invoke(origin, true, false)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Location permission")
            .setMessage("$normalizedOrigin wants to access your device location. Allow this site?")
            .setPositiveButton("Allow") { _, _ ->
                pendingGeoPermission = PendingGeoPermission(origin, callback)
                if (hasRuntimeLocationPermission()) {
                    completeGeoPermissionRequest(true)
                } else {
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        REQUEST_GEOLOCATION
                    )
                }
            }
            .setNegativeButton("Deny") { _, _ -> callback.invoke(origin, false, false) }
            .setOnCancelListener { callback.invoke(origin, false, false) }
            .show()
    }

    private fun completeWebPermissionRequest(granted: Boolean) {
        val pending = pendingWebPermission ?: return
        pendingWebPermission = null
        if (!granted || !pending.androidPermissions.all(::isRuntimePermissionGranted)) {
            runCatching { pending.request.deny() }
            return
        }
        pending.sitePermissions.forEach { permission ->
            permissionManager.grant(pending.origin, permission)
        }
        runCatching { pending.request.grant(pending.resources) }
    }

    private fun completeGeoPermissionRequest(granted: Boolean) {
        val pending = pendingGeoPermission ?: return
        pendingGeoPermission = null
        val allowed = granted && hasRuntimeLocationPermission()
        if (allowed) {
            permissionManager.grant(
                pending.origin,
                PermissionManager.SitePermission.LOCATION
            )
        }
        pending.callback.invoke(pending.origin, allowed, false)
    }

    private fun isRuntimePermissionGranted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasRuntimeLocationPermission(): Boolean =
        isRuntimePermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isRuntimePermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun onDownloadRequested(
        view: WebView,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        runOnUiThread {
            val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val decision = DownloadPolicy.evaluate(url, guessedName, mimeType)
            if (decision.action == DownloadPolicy.Action.BLOCK) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.download_blocked)
                    .setMessage(decision.fileName + "\n\n" + decision.reason)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
                return@runOnUiThread
            }

            val message = buildString {
                appendLine(getString(R.string.download_file, decision.fileName))
                appendLine(getString(R.string.download_source, decision.sourceHost ?: "Unknown"))
                appendLine(getString(R.string.download_type, decision.mimeType.ifBlank { "Unknown" }))
                appendLine(getString(R.string.download_size, if (contentLength > 0) android.text.format.Formatter.formatFileSize(this@MainActivity, contentLength) else "Unknown"))
                appendLine()
                append(decision.reason)
            }
            AlertDialog.Builder(this)
                .setTitle(if (decision.isApk) R.string.download_apk_confirm else R.string.download_confirm)
                .setMessage(message)
                .setPositiveButton(R.string.download_action) { _, _ ->
                    enqueueDownload(decision, url, userAgent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun enqueueDownload(
        decision: DownloadPolicy.Decision,
        url: String,
        userAgent: String
    ) {
        val manager = getSystemService(DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            Toast.makeText(this, R.string.download_service_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        val request = runCatching {
            DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(decision.fileName)
                setDescription(getString(R.string.download_description, decision.sourceHost ?: "website"))
                if (decision.mimeType.isNotBlank()) setMimeType(decision.mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, decision.fileName)
                setAllowedOverRoaming(false)
                if (userAgent.isNotBlank() && userAgent.length <= 512) {
                    addRequestHeader("User-Agent", userAgent)
                }
            }
        }.getOrElse {
            Toast.makeText(this, R.string.download_prepare_failed, Toast.LENGTH_LONG).show()
            return
        }

        runCatching { manager.enqueue(request) }
            .onSuccess {
                Toast.makeText(this, R.string.download_added, Toast.LENGTH_LONG).show()
            }
            .onFailure {
                Toast.makeText(this, getString(R.string.download_start_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_SITE_PERMISSION -> completeWebPermissionRequest(
                permissions.isNotEmpty() && permissions.all(::isRuntimePermissionGranted)
            )
            REQUEST_GEOLOCATION -> completeGeoPermissionRequest(
                permissions.isNotEmpty() && hasRuntimeLocationPermission()
            )
        }
    }

    override fun onSafeBrowsingThreat(view: WebView, url: String, threatType: Int) {
        val category = when (threatType) {
            WebViewClient.SAFE_BROWSING_THREAT_MALWARE ->
                WebProtectionManager.ThreatCategory.MALWARE
            WebViewClient.SAFE_BROWSING_THREAT_PHISHING ->
                WebProtectionManager.ThreatCategory.PHISHING
            else -> null
        }
        val reason = when (threatType) {
            WebViewClient.SAFE_BROWSING_THREAT_MALWARE ->
                "Android Safe Browsing identified a malware threat."
            WebViewClient.SAFE_BROWSING_THREAT_PHISHING ->
                "Android Safe Browsing identified a phishing threat."
            WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE ->
                "Android Safe Browsing identified potentially unwanted software."
            WebViewClient.SAFE_BROWSING_THREAT_BILLING ->
                "Android Safe Browsing identified a billing-related threat."
            else -> "Android Safe Browsing identified a threat (type $threatType)."
        }
        lastWebProtectionUrl = url
        lastWebProtectionPageUrl = view.url.orEmpty()
        lastWebProtectionView = view
        lastWebProtectionDecision = WebProtectionManager.Decision(
            action = WebProtectionManager.Action.BLOCK,
            category = category,
            reason = reason,
            sourceName = "Android WebView Safe Browsing",
            sourceReference = url
        )
    }

    override fun onNavigationRequested(view: WebView, url: String) {
        val tab = tabManager.findByWebView(view) ?: return
        val desktop = isDesktopForUrl(url)
        applyDesktopSettings(view, desktop)
        tab.isDesktopMode = desktop
    }

    override fun onCrossSiteNavigationRequested(view: WebView, url: String) {
        if (tabManager.findByWebView(view) == null ||
            !com.rain.browser.network.NetworkPolicyManager.isAllowedWebUrl(url)
        ) return

        val destinationHost = Uri.parse(url).host ?: "another website"
        AlertDialog.Builder(this)
            .setTitle(R.string.navigation_leave_title)
            .setMessage(getString(R.string.navigation_leave_message, destinationHost))
            .setPositiveButton(R.string.navigation_continue) { _, _ ->
                if (tabManager.findByWebView(view) != null &&
                    com.rain.browser.network.NetworkPolicyManager.isAllowedWebUrl(url)
                ) {
                    view.loadUrl(url)
                }
            }
            .setNegativeButton(R.string.navigation_stay, null)
            .setOnCancelListener { }
            .show()
    }

    override fun onExternalNavigationRequested(view: WebView, url: String) {
        if (tabManager.findByWebView(view) == null ||
            !NavigationGuardPolicy.isAllowedExternalUrl(url)
        ) return

        val uri = Uri.parse(url)
        val scheme = uri.scheme.orEmpty().lowercase()
        val externalIntent = Intent(Intent.ACTION_VIEW, uri)
        AlertDialog.Builder(this)
            .setTitle(R.string.external_open_title)
            .setMessage(getString(R.string.external_open_message, scheme))
            .setPositiveButton(R.string.navigation_open) { _, _ ->
                if (tabManager.findByWebView(view) != null) {
                    runCatching { startActivity(externalIntent) }
                        .onFailure {
                            Toast.makeText(
                                this,
                                getString(R.string.external_no_app),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPageStarted(view: WebView, url: String) {
        runOnUiThread {
            val tab = tabManager.findByWebView(view) ?: return@runOnUiThread
            tab.isLoading = true
            tab.progress = 0
            if (url.startsWith("http://") || url.startsWith("https://")) tab.url = url
            tab.isDesktopMode = isDesktopForUrl(url)
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
                tab.isDesktopMode = isDesktopForUrl(url)
                historyRepository.record(url, tab.title)
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
        saveResumeSession()
        tabManager.tabs.forEach { it.webView.onPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        tabManager.tabs.forEach { it.webView.onResume() }
    }

    override fun onDestroy() {
        tabManager.clear().forEach { destroyWebView(it.webView) }
        storageExecutor.shutdown()
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
        const val REQUEST_SITE_PERMISSION = 4101
        const val REQUEST_GEOLOCATION = 4102
        const val HISTORY_DAY_MILLIS = 24L * 60 * 60 * 1000

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
                    section { width: min(90vw, 520px); margin: 18px auto; text-align: left; }
                    li { margin: 8px 0; overflow-wrap: anywhere; }
                    a { color: #38BDF8; text-decoration: none; }
                </style>
            </head>
            <body><main><h1>RAIN</h1><p>{{READY}}</p>
              <section><h2>{{RECENT_TITLE}}</h2><ul>{{RECENT}}</ul></section>
              <section><h2>{{BOOKMARK_TITLE}}</h2><ul>{{BOOKMARKS}}</ul></section>
            </main></body>
            </html>
        """
    }
}
