package com.rain.browser.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyManagerTest {
    @Test
    fun allowsHttpAndHttpsUrls() {
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("https://example.com"))
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("http://example.com/path"))
    }

    @Test
    fun acceptsCaseInsensitiveWebSchemesAndOuterWhitespace() {
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("HTTPS://example.com"))
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("HtTp://example.com/path"))
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("  https://example.com/path  "))
    }

    @Test
    fun rejectsUnsupportedAndExternalSchemes() {
        val urls = listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://com.example.provider/item",
            "data:text/html,hello",
            "intent://example.com",
            "market://details?id=com.example.app",
            "mailto:test@example.com",
            "tel:+66000000000",
            "ftp://example.com/file",
            "ws://example.com/socket",
            "wss://example.com/socket",
            "blob:https://example.com/resource"
        )

        urls.forEach { url ->
            assertFalse("Expected scheme to be blocked: $url", NetworkPolicyManager.isAllowedWebUrl(url))
        }
    }

    @Test
    fun rejectsMalformedAndCredentialUrls() {
        val urls = listOf(
            "not a url",
            "https://user:pass@example.com",
            "https://user%40name@example.com",
            "",
            "https:///missing-host",
            "https://example.com\\n",
            "https://example.com:invalid",
            "https://example.com:65536"
        )

        urls.forEach { url ->
            assertFalse("Expected malformed URL to be blocked: $url", NetworkPolicyManager.isAllowedWebUrl(url))
        }
    }

    @Test
    fun acceptsPortRangeBoundaries() {
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("https://example.com:0"))
        assertTrue(NetworkPolicyManager.isAllowedWebUrl("https://example.com:65535"))
    }

    @Test
    fun rejectsUrlsWithoutAnAllowedHost() {
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("https:"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("//example.com/path"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("http:///path"))
    }
}
