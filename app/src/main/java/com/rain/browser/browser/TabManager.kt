package com.rain.browser.browser

import android.webkit.WebView

class TabManager {
    private val tabItems = mutableListOf<BrowserTab>()
    private var nextId = 1L
    private var activeTabId: Long? = null

    val tabs: List<BrowserTab>
        get() = tabItems.toList()

    val activeTab: BrowserTab?
        get() = tabItems.firstOrNull { it.id == activeTabId }

    val activeIndex: Int
        get() = tabItems.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

    fun add(webView: WebView): BrowserTab {
        val tab = BrowserTab(id = nextId++, webView = webView)
        tabItems += tab
        activeTabId = tab.id
        return tab
    }

    fun select(tabId: Long): BrowserTab? {
        val tab = tabItems.firstOrNull { it.id == tabId } ?: return null
        activeTabId = tab.id
        return tab
    }

    fun selectAt(index: Int): BrowserTab? {
        val tab = tabItems.getOrNull(index) ?: return null
        activeTabId = tab.id
        return tab
    }

    fun findByWebView(webView: WebView): BrowserTab? =
        tabItems.firstOrNull { it.webView === webView }

    fun close(tabId: Long): BrowserTab? {
        val index = tabItems.indexOfFirst { it.id == tabId }
        if (index < 0) return null
        val removed = tabItems.removeAt(index)
        if (activeTabId == tabId) {
            activeTabId = tabItems.getOrNull(index.coerceAtMost(tabItems.lastIndex))?.id
        }
        return removed
    }

    fun closeActive(): BrowserTab? = activeTab?.let { close(it.id) }

    fun clear(): List<BrowserTab> {
        val removed = tabItems.toList()
        tabItems.clear()
        activeTabId = null
        return removed
    }
}
