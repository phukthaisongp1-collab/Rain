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
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.ClipData
import android.speech.RecognizerIntent
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject
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

    private data class HomeShortcut(val name: String, val url: String)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(15, 23, 42)
        setContentView(R.layout.activity_main)
        applyImmersiveFullscreen()

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
            if (tab.url.isBlank()) {
                showWelcomePage(webView)
            } else if (restored == null) {
                loadUrlInTab(tab, tab.url)
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
        tabManager.findByWebView(webView)?.apply {
            url = GOOGLE_HOME_URL
            title = "Google"
        }
        updateBrowserControls()
        webView.loadUrl(GOOGLE_HOME_URL)
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

    private fun openHomeSearch(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a search or website address", Toast.LENGTH_SHORT).show()
            return
        }
        addressInput.setText(query)
        openAddress()
    }

    private fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            startActivityForResult(intent, REQUEST_VOICE_SEARCH)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No speech recognition service is available on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchVisualSearch() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Choose an image"), REQUEST_IMAGE_PICKER)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No image picker is available on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareImageForVisualSearch(imageUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newUri(contentResolver, "Selected image", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Search image with Google Lens or another app"))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No app can receive the selected image", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddShortcutDialog() {
        val nameInput = EditText(this).apply {
            hint = "Website name"
            setSingleLine(true)
        }
        val urlInput = EditText(this).apply {
            hint = "https://example.com"
            setSingleLine(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = (20 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, 8, horizontalPadding, 0)
            addView(nameInput)
            addView(urlInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Add website shortcut")
            .setView(content)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val rawUrl = urlInput.text.toString().trim()
                val resolvedUrl = BrowserUrlResolver.resolveWebsiteShortcut(rawUrl)
                if (name.isBlank() || resolvedUrl == null ||
                    !com.rain.browser.network.NetworkPolicyManager.isAllowedWebUrl(resolvedUrl)
                ) {
                    Toast.makeText(this, "Enter a name and a valid website address", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val shortcuts = readHomeShortcuts().toMutableList()
                if (shortcuts.any { it.url.equals(resolvedUrl, ignoreCase = true) }) {
                    Toast.makeText(this, "This website is already a shortcut", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                shortcuts.add(HomeShortcut(name, resolvedUrl))
                saveHomeShortcuts(shortcuts)
                tabManager.activeTab?.webView?.let(::showWelcomePage)
                Toast.makeText(this, "Shortcut added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readHomeShortcuts(): List<HomeShortcut> {
        val raw = getSharedPreferences(HOME_SHORTCUTS_PREFS, MODE_PRIVATE)
            .getString(HOME_SHORTCUTS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val url = item.optString("url").trim()
                    if (name.isNotBlank() && com.rain.browser.network.NetworkPolicyManager.isAllowedWebUrl(url)) {
                        add(HomeShortcut(name, url))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHomeShortcuts(shortcuts: List<HomeShortcut>) {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            array.put(JSONObject().put("name", shortcut.name).put("url", shortcut.url))
        }
        getSharedPreferences(HOME_SHORTCUTS_PREFS, MODE_PRIVATE)
            .edit()
            .putString(HOME_SHORTCUTS_KEY, array.toString())
            .apply()
    }

    private fun buildHomeShortcutMarkup(): String =
        readHomeShortcuts().joinToString("") { shortcut ->
            "<a class=\"shortcut custom-shortcut\" href=\"" + escapeHtml(shortcut.url) + "\">" +
                "<span class=\"shortcut-icon\">↗</span><span>" +
                escapeHtml(shortcut.name) + "</span></a>"
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_VOICE_SEARCH -> {
                val spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull().orEmpty()
                if (spokenText.isNotBlank()) openHomeSearch(spokenText)
            }
            REQUEST_IMAGE_PICKER -> data.data?.let(::shareImageForVisualSearch)
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
        searchEngineButton.text = getString(R.string.search_button, searchEngineLabel())
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
            findViewById<View>(R.id.top_toolbar).visibility = View.VISIBLE
            findViewById<View>(R.id.address_toolbar).visibility = View.VISIBLE
            findViewById<View>(R.id.actions_toolbar).visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.status_no_tab)
            modeText.text = getString(R.string.mode_mobile)
            tabsButton.text = "Tabs (0)"
            progressBar.visibility = View.GONE
            return
        }

        val currentUrl = tab.webView.url.orEmpty().ifBlank { tab.url }
        val isGoogleHome = BrowserUrlResolver.isGoogleHomePage(currentUrl)
        val toolbarVisibility = if (isGoogleHome) View.GONE else View.VISIBLE
        findViewById<View>(R.id.top_toolbar).visibility = toolbarVisibility
        findViewById<View>(R.id.address_toolbar).visibility = toolbarVisibility
        findViewById<View>(R.id.actions_toolbar).visibility = toolbarVisibility
        statusText.visibility = toolbarVisibility

        if (!addressInput.hasFocus()) {
            addressInput.setText(tab.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty())
        }
        statusText.text = tab.title.ifBlank { getString(R.string.status_ready) }
        modeText.text = if (tab.isDesktopMode) getString(R.string.mode_desktop) else getString(R.string.mode_mobile)
        tabsButton.text = getString(R.string.tabs_count, tabManager.tabs.size)
        reloadButton.text = if (tab.isLoading) getString(R.string.status_stop) else getString(R.string.action_reload)
        progressBar.progress = tab.progress
        progressBar.visibility = if (!isGoogleHome && tab.isLoading) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.back_button).isEnabled = tab.webView.canGoBack()
        findViewById<Button>(R.id.forward_button).isEnabled = tab.webView.canGoForward()
    }


    private fun cleanupExpiredResumeEntries() {
        storageExecutor.execute { runCatching { resumeRepository.clearExpired() } }
    }

    private fun saveResumeSession() {
        val tabs = tabManager.tabs
        val activeTab = tabManager.activeTab
        val baseTimestamp = System.currentTimeMillis()
        val snapshots = tabs.mapIndexedNotNull { index, tab ->
            val url = tab.webView.url.orEmpty().ifBlank { tab.url }
            if (url.startsWith("https://") || url.startsWith("http://")) {
                val priority = if (tab == activeTab) tabs.size + 1L else (tabs.size - index).toLong()
                Triple(url, tab.title, baseTimestamp + priority)
            } else null
        }
        if (snapshots.isNotEmpty()) {
            storageExecutor.execute {
                snapshots.forEach { (url, title, updatedAt) ->
                    runCatching { resumeRepository.save(url, title, 0L, updatedAt) }
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
            reason = getString(R.string.protection_default_reason)
        )
        val message = if (reportUrl.isBlank()) {
            getString(R.string.protection_open_site)
        } else {
            localizedProtectionMessage(reportUrl, decision) +
                "\n\nFalse-positive reports are shared through Android's share sheet. RAIN does not upload reports automatically."
        }
        AlertDialog.Builder(this)
            .setTitle(localizedProtectionTitle(decision))
            .setMessage(message)
            .setPositiveButton(R.string.protection_report) { _, _ ->
                if (reportUrl.isNotBlank()) shareFalsePositiveReport(reportUrl, decision)
            }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun localizedProtectionTitle(decision: WebProtectionManager.Decision): String = when (decision.action) {
        WebProtectionManager.Action.ALLOW -> getString(R.string.protection_allow_title)
        WebProtectionManager.Action.WARN -> getString(R.string.protection_warn_title)
        WebProtectionManager.Action.BLOCK -> getString(R.string.protection_block_title)
    }

    private fun localizedProtectionMessage(url: String, decision: WebProtectionManager.Decision): String = buildList {
        add(getString(R.string.protection_website, url))
        decision.category?.let { add(getString(R.string.protection_category, it.name.lowercase().replace('_', ' '))) }
        decision.sourceName?.let { add(getString(R.string.protection_source, it)) }
        if (decision.reason.isNotBlank()) add(getString(R.string.protection_reason, decision.reason))
        decision.sourceReference?.takeIf { it.isNotBlank() }?.let { add(getString(R.string.protection_reference, it)) }
    }.joinToString("\n")

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
                    .setMessage(decision.fileName + "\n\n" + localizedDownloadReason(decision.reason))
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
                append(localizedDownloadReason(decision.reason))
            }
            AlertDialog.Builder(this)
                .setTitle(if (decision.isApk) R.string.download_apk_confirm else R.string.download_confirm)
                .setMessage(message)
                .setPositiveButton(R.string.download_action) { _, _ ->
                    enqueueDownload(decision, url, userAgent)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun localizedDownloadReason(reason: String): String = when (reason) {
        "The download URL is invalid or uses an unsupported source." -> getString(R.string.download_reason_invalid)
        "The server did not provide a safe file name." -> getString(R.string.download_reason_filename)
        "This executable or script file type is blocked by RAIN." -> getString(R.string.download_reason_executable)
        "The file extension does not match the server's HTML content type." -> getString(R.string.download_reason_html)
        "The APK extension conflicts with the reported content type." -> getString(R.string.download_reason_apk)
        "The file extension and reported content type do not match." -> getString(R.string.download_reason_mismatch)
        "APK files can install applications. RAIN will only save this file and will never install it automatically. Verify the publisher before opening it." -> getString(R.string.download_reason_apk_confirm)
        "This download uses an unencrypted HTTP connection. Confirm only if you trust the source." -> getString(R.string.download_reason_http)
        "RAIN cannot confidently identify this file type. Review the source and file name before downloading." -> getString(R.string.download_reason_unknown)
        "Review the source and file details before confirming the download." -> getString(R.string.download_reason_review)
        else -> reason
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
        if (url.startsWith("rain://", ignoreCase = true)) {
            val currentTab = tabManager.findByWebView(view) ?: return
            if (currentTab.url.isNotBlank()) return
            val uri = Uri.parse(url)
            when (uri.host) {
                "search" -> openHomeSearch(uri.getQueryParameter("q").orEmpty())
                "voice" -> launchVoiceSearch()
                "visual-search" -> launchVisualSearch()
                "add-shortcut" -> showAddShortcutDialog()
            }
            return
        }
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
        applyImmersiveFullscreen()
        tabManager.tabs.forEach { it.webView.onResume() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullscreen()
    }

    private fun applyImmersiveFullscreen() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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
        const val REQUEST_VOICE_SEARCH = 4103
        const val REQUEST_IMAGE_PICKER = 4104
        const val HISTORY_DAY_MILLIS = 24L * 60 * 60 * 1000
        const val GOOGLE_HOME_URL = "https://www.google.com/"
        const val HOME_SHORTCUTS_PREFS = "rain_home_shortcuts"
        const val HOME_SHORTCUTS_KEY = "items"

        const val WELCOME_PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                <meta name="theme-color" content="#d9efff">
                <title>RAIN Browser</title>
                <style>
                    * { box-sizing: border-box; }
                    html, body { margin: 0; min-height: 100%; }
                    body {
                        min-height: 100vh;
                        overflow-x: hidden;
                        color: #092b58;
                        font-family: Roboto, "Arial", sans-serif;
                        background:
                            radial-gradient(ellipse at 50% 28%, rgba(255,255,255,.95) 0, rgba(255,255,255,.42) 32%, transparent 58%),
                            linear-gradient(180deg, #8dcfff 0%, #d8efff 48%, #7bbbe9 75%, #d6f2ff 100%);
                    }
                    .scene { position: relative; min-height: 100vh; padding: 22px 5vw 28px; isolation: isolate; }
                    .scene:before, .scene:after {
                        content: ""; position: absolute; z-index: -1; left: -8%; right: -8%;
                        pointer-events: none;
                    }
                    .scene:before {
                        bottom: 19%; height: 43%;
                        background:
                            linear-gradient(165deg, transparent 0 40%, rgba(15,91,171,.82) 40.2% 100%),
                            linear-gradient(18deg, transparent 0 38%, rgba(48,139,207,.9) 38.2% 100%);
                        clip-path: polygon(0 62%, 8% 49%, 15% 60%, 25% 25%, 34% 56%, 43% 43%, 52% 65%, 63% 29%, 72% 57%, 82% 17%, 91% 53%, 100% 36%, 100% 100%, 0 100%);
                        filter: drop-shadow(0 12px 16px rgba(20,100,170,.16));
                    }
                    .scene:after {
                        bottom: 0; height: 31%;
                        background:
                            linear-gradient(174deg, transparent 0 25%, rgba(255,255,255,.76) 25.5% 31%, transparent 32%),
                            linear-gradient(180deg, rgba(72,169,226,.88), rgba(202,239,255,.95) 62%, rgba(255,255,255,.92));
                        clip-path: polygon(0 24%, 14% 15%, 27% 26%, 41% 12%, 54% 25%, 68% 10%, 82% 22%, 100% 7%, 100% 100%, 0 100%);
                        filter: blur(1px);
                    }
                    .clouds {
                        position: absolute; z-index: -1; inset: 13% -5% 18%;
                        opacity: .88; pointer-events: none;
                        background:
                            radial-gradient(ellipse at 5% 48%, rgba(255,255,255,.98) 0 4%, transparent 13%),
                            radial-gradient(ellipse at 13% 43%, rgba(255,255,255,.96) 0 6%, transparent 16%),
                            radial-gradient(ellipse at 20% 53%, rgba(255,255,255,.88) 0 5%, transparent 15%),
                            radial-gradient(ellipse at 78% 48%, rgba(255,255,255,.94) 0 5%, transparent 15%),
                            radial-gradient(ellipse at 88% 42%, rgba(255,255,255,.98) 0 7%, transparent 17%),
                            radial-gradient(ellipse at 96% 53%, rgba(255,255,255,.88) 0 5%, transparent 15%),
                            radial-gradient(ellipse at 50% 13%, rgba(255,255,255,.8) 0 7%, transparent 24%);
                    }
                    .weather {
                        position: absolute; top: 22px; right: 5vw; display: flex; align-items: center; gap: 10px;
                        padding: 9px 14px; border-radius: 16px; color: #08366e;
                        background: rgba(235,248,255,.62); border: 1px solid rgba(255,255,255,.65);
                        box-shadow: 0 8px 24px rgba(22,94,160,.08); font-size: 13px;
                    }
                    .weather-icon { font-size: 25px; }
                    .weather strong { display: block; font-size: 16px; }
                    main { width: min(100%, 980px); margin: 0 auto; position: relative; z-index: 1; padding-top: clamp(26px, 7vh, 78px); }
                    .brand { display: flex; justify-content: center; margin: 0 auto 24px; }
                    .brand svg { display: block; width: min(72vw, 440px); height: auto; overflow: visible; filter: drop-shadow(0 8px 15px rgba(10,75,140,.12)); }
                    .search {
                        display: flex; align-items: center; gap: 12px; width: min(100%, 820px); height: 62px;
                        margin: 0 auto 28px; padding: 6px 8px 6px 20px; border-radius: 40px;
                        background: rgba(255,255,255,.94); border: 1px solid rgba(255,255,255,.9);
                        box-shadow: 0 10px 30px rgba(18,83,145,.16);
                    }
                    .search-icon { font-size: 25px; color: #164d86; }
                    .search input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: #16395f; font-size: 17px; }
                    .search input::placeholder { color: #647b96; }
                    .mic { display: grid; place-items: center; border: 0; background: transparent; color: #164d86; font-size: 22px; padding: 8px; text-decoration: none; }
                    .search-submit { width: 48px; height: 48px; border: 0; border-radius: 50%; background: #0877df; color: white; font-size: 22px; }
                    .shortcuts { display: flex; justify-content: center; flex-wrap: wrap; gap: 14px; margin: 0 auto 28px; }
                    .shortcut {
                        display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px;
                        width: 112px; min-height: 112px; padding: 12px 8px; border-radius: 22px;
                        color: #082e61; text-decoration: none; font-size: 13px; font-weight: 600;
                        background: rgba(255,255,255,.72); border: 1px solid rgba(255,255,255,.72);
                        box-shadow: 0 8px 22px rgba(20,88,145,.11); backdrop-filter: blur(8px);
                    }
                    .shortcut-icon { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 15px; background: white; font-size: 31px; font-weight: 800; }
                    .google { color: #4285f4; } .youtube { color: #f00; } .facebook { color: #1877f2; } .xbrand { color: #111; } .instagram { color: #d62976; }
                    .add { color: #0a65bd; font-size: 38px; font-weight: 400; }
                    .features {
                        display: grid; grid-template-columns: repeat(5, 1fr); width: min(100%, 920px); margin: 0 auto;
                        padding: 14px 4px; border-radius: 28px; background: rgba(232,247,255,.74);
                        border: 1px solid rgba(255,255,255,.78); box-shadow: 0 10px 28px rgba(17,84,143,.12);
                        backdrop-filter: blur(10px);
                    }
                    .feature { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px; min-height: 72px; padding: 4px 7px; text-align: center; color: #07366e; font-size: 12px; font-weight: 700; }
                    .feature + .feature { border-left: 1px solid rgba(20,82,142,.35); }
                    .feature-icon { display: grid; place-items: center; width: 30px; height: 30px; line-height: 1; }
                    .feature-icon svg { display: block; width: 28px; height: 28px; }
                    @media (max-width: 600px) {
                        .scene { padding: 18px 14px 22px; }
                        .weather { top: 12px; right: 14px; padding: 6px 9px; font-size: 10px; }
                        .weather-icon { font-size: 19px; }
                        .weather strong { font-size: 12px; }
                        main { padding-top: 88px; }
                        .brand { margin-bottom: 18px; }
                        .brand svg { width: min(84vw, 360px); }
                        .search { height: 54px; padding-left: 14px; gap: 7px; margin-bottom: 22px; }
                        .search input { font-size: 14px; }
                        .search-submit { width: 42px; height: 42px; }
                        .shortcuts { gap: 9px; margin-bottom: 20px; }
                        .shortcut { width: calc(33.333% - 8px); min-height: 92px; border-radius: 17px; font-size: 11px; }
                        .shortcut-icon { width: 42px; height: 42px; font-size: 26px; }
                        .features { grid-template-columns: repeat(5, 1fr); border-radius: 18px; padding: 8px 2px; }
                        .feature { font-size: 9px; min-height: 66px; padding: 3px 2px; }
                        .feature-icon { width: 24px; height: 24px; }
                        .feature-icon svg { width: 22px; height: 22px; }
                    }

                    /* Compact layout for phones rotated to landscape. */
                    @media (orientation: landscape) and (max-height: 520px) {
                        .scene { min-height: 100vh; padding: 8px 3vw 12px; }
                        .weather { top: 8px; right: 3vw; gap: 7px; padding: 5px 8px; font-size: 10px; }
                        .weather-icon { font-size: 18px; }
                        .weather strong { font-size: 12px; }
                        main { width: min(100%, 920px); padding-top: 8px; }
                        .brand { margin-bottom: 10px; }
                        .brand svg { width: min(38vw, 250px); max-height: 27vh; }
                        .search { width: min(100%, 760px); height: 46px; margin-bottom: 12px; padding: 4px 7px 4px 14px; gap: 8px; }
                        .search-icon { font-size: 21px; }
                        .search input { font-size: 14px; }
                        .search-submit { width: 36px; height: 36px; }
                        .mic { padding: 5px; font-size: 18px; }
                        .shortcuts { gap: 8px; margin-bottom: 12px; }
                        .shortcut { width: 86px; min-height: 76px; padding: 6px; gap: 4px; border-radius: 14px; font-size: 10px; }
                        .shortcut-icon { width: 34px; height: 34px; border-radius: 10px; font-size: 23px; }
                        .add { font-size: 29px; }
                        .features { width: min(100%, 860px); padding: 5px 3px; border-radius: 16px; }
                        .feature { min-height: 48px; gap: 3px; padding: 2px 4px; font-size: 9px; }
                        .feature-icon { font-size: 18px; }
                    }
                </style>
            </head>
            <body>
                <div class="scene">
                    <div class="clouds"></div>
                    <aside class="weather" aria-label="Weather location">
                        <span class="weather-icon" aria-hidden="true">🌦️</span>
                        <span><strong>Bangkok</strong>Local weather</span>
                    </aside>
                    <main>
                        <div class="brand" aria-label="RAIN Browser">
                            <svg viewBox="0 0 1296 967" role="img" aria-label="RAIN Browser logo">
                                <defs><linearGradient id="rain" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#064da9"/><stop offset=".52" stop-color="#079bdc"/><stop offset="1" stop-color="#4ce5ad"/></linearGradient></defs>
                                <path d="M 1116,274 L 1040,413 L 972,565 L 1040,446 L 1105,306 Z M 866,280 L 806,384 L 729,552 L 667,552 L 665,547 L 731,426 L 765,355 L 798,273 L 734,386 L 660,552 L 557,552 L 556,604 L 559,608 L 726,608 L 736,611 L 748,622 L 754,642 L 751,656 L 598,656 L 572,662 L 546,678 L 533,692 L 522,715 L 519,757 L 535,797 L 562,820 L 595,831 L 672,832 L 694,828 L 724,811 L 730,805 L 740,778 L 599,777 L 578,760 L 574,749 L 575,736 L 581,724 L 600,711 L 715,711 L 734,702 L 751,682 L 754,686 L 754,830 L 809,831 L 807,615 L 797,591 L 785,577 L 765,563 L 737,553 L 735,549 L 799,432 Z M 1038,252 L 972,372 L 892,549 L 888,552 L 853,551 L 857,555 L 858,832 L 913,831 L 913,553 L 895,549 L 970,408 Z M 3,210 L 2,321 L 5,323 L 312,323 L 348,337 L 375,363 L 390,396 L 390,428 L 374,464 L 346,490 L 309,503 L 141,503 L 140,506 L 362,813 L 402,831 L 512,832 L 511,824 L 357,615 L 359,610 L 400,593 L 436,569 L 465,537 L 492,489 L 504,438 L 503,383 L 492,338 L 478,310 L 455,277 L 417,243 L 386,227 L 349,214 L 321,210 Z M 979,190 L 869,399 L 807,542 L 866,438 L 899,371 Z M 1229,121 L 1177,216 L 1113,366 L 1174,254 Z M 1287,107 L 1143,383 L 1140,396 L 1079,525 L 1074,544 L 1067,552 L 1048,553 L 1014,565 L 990,582 L 972,605 L 959,647 L 961,832 L 1016,831 L 1016,651 L 1024,631 L 1042,612 L 1060,608 L 1137,608 L 1155,612 L 1171,625 L 1180,656 L 1180,831 L 1239,830 L 1239,646 L 1229,610 L 1204,577 L 1173,559 L 1146,552 L 1075,552 L 1072,548 L 1141,422 Z M 1162,81 L 1108,181 L 1042,334 L 1106,217 Z M 986,79 L 915,214 L 869,324 L 935,202 Z M 834,58 L 802,113 L 697,320 L 600,539 L 695,360 Z M 1093,46 L 972,283 L 858,541 L 1003,253 L 1073,99 Z M 926,5 L 851,148 L 810,247 L 868,141 L 901,70 Z" fill="url(#rain)" fill-rule="evenodd"/>
                                <path d="M 583,890 L 605,962 L 618,960 L 635,910 L 653,961 L 666,960 L 687,895 L 686,889 L 675,891 L 661,944 L 657,942 L 640,889 L 629,890 L 611,943 L 595,891 Z M 1072,888 L 1052,895 L 1047,889 L 1036,890 L 1038,962 L 1046,962 L 1048,958 L 1048,914 L 1057,904 L 1073,900 Z M 906,890 L 893,903 L 889,913 L 888,931 L 892,945 L 902,957 L 916,963 L 933,963 L 945,958 L 955,947 L 955,940 L 946,940 L 935,950 L 921,952 L 904,943 L 902,932 L 958,930 L 959,916 L 954,903 L 945,893 L 933,888 Z M 902,913 L 913,901 L 930,899 L 943,910 L 945,917 L 906,919 Z M 769,890 L 759,901 L 761,919 L 770,927 L 798,935 L 802,945 L 793,952 L 781,952 L 766,942 L 758,942 L 758,948 L 767,959 L 777,963 L 802,961 L 815,948 L 814,933 L 802,924 L 776,916 L 770,906 L 779,899 L 793,899 L 802,909 L 811,910 L 813,903 L 807,894 L 795,888 Z M 465,889 L 449,901 L 443,914 L 442,932 L 447,945 L 460,960 L 486,963 L 500,957 L 511,945 L 515,933 L 513,911 L 508,901 L 492,889 Z M 472,899 L 490,901 L 501,916 L 499,940 L 485,951 L 472,951 L 458,942 L 457,912 Z M 373,888 L 359,890 L 353,895 L 347,889 L 336,891 L 338,962 L 349,960 L 349,914 L 358,903 L 373,900 Z M 249,870 L 237,866 L 190,866 L 192,962 L 239,962 L 250,958 L 257,949 L 260,931 L 249,911 L 258,898 L 259,887 Z M 202,922 L 209,917 L 236,918 L 246,926 L 248,942 L 233,951 L 206,951 L 202,947 Z M 203,879 L 240,879 L 247,887 L 247,894 L 238,906 L 207,908 L 202,904 Z M 3,503 L 2,830 L 79,831 L 93,825 L 112,809 L 119,786 L 119,503 Z" fill="#123f72" fill-rule="evenodd"/>
                            </svg>
                        </div>
                        <form class="search" action="rain://search" method="get" role="search">
                            <span class="search-icon" aria-hidden="true">⌕</span>
                            <input name="q" type="search" autocomplete="off" placeholder="Search the web..." aria-label="Search the web">
                            <a class="mic" href="rain://voice" aria-label="Voice search">🎙</a>
                            <a class="mic" href="rain://visual-search" aria-label="Search by image">▧</a>
                            <button class="search-submit" type="submit" aria-label="Search">⌕</button>
                        </form>
                        <nav class="shortcuts" aria-label="Favorite websites">
                            <a class="shortcut" href="https://www.google.com/"><span class="shortcut-icon google">G</span><span>Google</span></a>
                            <a class="shortcut" href="https://www.youtube.com/"><span class="shortcut-icon youtube">▶</span><span>YouTube</span></a>
                            <a class="shortcut" href="https://www.facebook.com/"><span class="shortcut-icon facebook">f</span><span>Facebook</span></a>
                            <a class="shortcut" href="https://x.com/"><span class="shortcut-icon xbrand">𝕏</span><span>X</span></a>
                            <a class="shortcut" href="https://www.instagram.com/"><span class="shortcut-icon instagram">◎</span><span>Instagram</span></a>
                            {{CUSTOM_SHORTCUTS}}
                            <a class="shortcut" href="rain://add-shortcut" aria-label="Add shortcut"><span class="shortcut-icon add">+</span><span>Add</span></a>
                        </nav>
                        <section class="features" aria-label="RAIN Browser features">
                            <div class="feature"><span class="feature-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#0756a8" d="M13.4 1.5 4.8 13h5.8l-.8 9.5L19.2 9h-6.1z"/></svg></span><span>Fast<br>Browsing</span></div>
                            <div class="feature"><span class="feature-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#0756a8" d="M12 1.5 21 5v6.2c0 5.4-3.7 9.2-9 11.3-5.3-2.1-9-5.9-9-11.3V5z"/><path fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="m7.5 11.8 3 3 6-6"/></svg></span><span>Secure<br>&amp; Private</span></div>
                            <div class="feature"><span class="feature-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#31a95b" d="M20.8 2.5C11 2.8 5.2 5.5 4.2 11.3c-.6 3.4 1.6 5.7 4.5 5.2 5.5-.9 9.6-6.5 12.1-14z"/><path fill="none" stroke="#fff" stroke-width="1.5" stroke-linecap="round" d="M3 21c3.5-5.3 7.2-8.7 13.4-12.4"/></svg></span><span>Lightweight<br>&amp; Eco-Friendly</span></div>
                            <div class="feature"><span class="feature-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#0756a8" d="M8 2a2 2 0 1 0 0 4H3v5h4a2 2 0 1 1 0 4H3v7h7v-4a2 2 0 1 1 4 0v4h7v-7h-4a2 2 0 1 1 0-4h4V6h-5a2 2 0 1 0-4 0H8z"/></svg></span><span>Extensions<br>Support</span></div>
                            <div class="feature"><span class="feature-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#0756a8" d="m19.4 13.5 2-1.5-2-1.5.2-2.4-2.5-.5-1-2.2-2.2 1L12 4.8l-1.9 1.6-2.5-.5-.8 2.4-2.3 1 1.1 2.2-1 2.2 2.3 1 .8 2.4 2.5-.5 1.9 1.6 1.9-1.6 2.5.5 1-2.2 2.5-.5z"/><circle cx="12" cy="12" r="3" fill="#fff"/></svg></span><span>Customize<br>Your Way</span></div>
                        </section>
                    </main>
                </div>
            </body>
            </html>
        """
    }
}
