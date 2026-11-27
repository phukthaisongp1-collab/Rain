package com.rain.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserUrlResolverTest {
    @Test
    fun keepsValidHttpsUrl() {
        assertEquals(
            "https://example.com/path",
            BrowserUrlResolver.resolve("https://example.com/path", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun upgradesBareDomainToHttps() {
        assertEquals(
            "https://example.com",
            BrowserUrlResolver.resolve("example.com", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun searchesPlainTextWithDuckDuckGoByDefault() {
        assertEquals(
            "https://duckduckgo.com/?q=rain+browser",
            BrowserUrlResolver.resolve("rain browser", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun supportsGoogleSearchSelection() {
        assertEquals(
            "https://www.google.com/search?q=rain",
            BrowserUrlResolver.resolve("rain", SearchEngine.GOOGLE)
        )
    }

    @Test
    fun doesNotPassUnsafeSchemesToWebView() {
        assertEquals(
            "https://duckduckgo.com/?q=javascript%3Aalert%281%29",
            BrowserUrlResolver.resolve("javascript:alert(1)", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun acceptsLocalhostWithPort() {
        assertEquals(
            "https://localhost:8080/path",
            BrowserUrlResolver.resolve("localhost:8080/path", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun rejectsUrlsContainingUserInfo() {
        assertEquals(
            "https://duckduckgo.com/?q=https%3A%2F%2Fuser%3Apass%40example.com",
            BrowserUrlResolver.resolve("https://user:pass@example.com", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun trimsWhitespaceAroundWebsiteAddress() {
        assertEquals(
            "https://example.com/path",
            BrowserUrlResolver.resolve("  example.com/path  ", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertEquals("", BrowserUrlResolver.resolve("   ", SearchEngine.DUCKDUCKGO))
    }

    @Test
    fun preservesHttpUrl() {
        assertEquals(
            "http://example.com/path",
            BrowserUrlResolver.resolve("http://example.com/path", SearchEngine.GOOGLE)
        )
    }

    @Test
    fun encodesReservedCharactersInSearchQuery() {
        assertEquals(
            "https://duckduckgo.com/?q=rain+%26+weather%3F",
            BrowserUrlResolver.resolve("rain & weather?", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun treatsUnsupportedSchemesAsSearchText() {
        assertEquals(
            "https://www.google.com/search?q=ftp%3A%2F%2Fexample.com",
            BrowserUrlResolver.resolve("ftp://example.com", SearchEngine.GOOGLE)
        )
    }
    @Test
    fun resolvesWebsiteShortcutWithoutScheme() {
        assertEquals("https://example.com/path?q=rain", BrowserUrlResolver.resolveWebsiteShortcut("example.com/path?q=rain"))
    }

    @Test
    fun preservesHttpWebsiteShortcut() {
        assertEquals("http://example.com/path", BrowserUrlResolver.resolveWebsiteShortcut("http://example.com/path"))
    }

    @Test
    fun rejectsSearchTextAsWebsiteShortcut() {
        assertEquals(null, BrowserUrlResolver.resolveWebsiteShortcut("rain browser"))
    }

    @Test
    fun rejectsUnsafeWebsiteShortcutSchemes() {
        assertEquals(null, BrowserUrlResolver.resolveWebsiteShortcut("javascript:alert(1)"))
        assertEquals(null, BrowserUrlResolver.resolveWebsiteShortcut("ftp://example.com"))
    }

    @Test
    fun rejectsWebsiteShortcutWithCredentials() {
        assertEquals(null, BrowserUrlResolver.resolveWebsiteShortcut("https://user:pass@example.com"))
    }

    @Test
    fun rejectsBlankWebsiteShortcut() {
        assertEquals(null, BrowserUrlResolver.resolveWebsiteShortcut("   "))
    }
}
