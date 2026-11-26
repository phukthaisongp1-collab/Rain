package com.rain.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewProxyManagerTest {
    @Test
    fun acceptsExplicitHttpHttpsAndSocksProxyEndpoints() {
        assertEquals(
            "http://proxy.example.com:8080",
            WebViewProxyManager.parseProxyEndpoint("http://proxy.example.com:8080")?.proxyUrl
        )
        assertEquals(
            "https://proxy.example.com:443",
            WebViewProxyManager.parseProxyEndpoint("https://proxy.example.com:443")?.proxyUrl
        )
        assertEquals(
            "socks://192.0.2.10:1080",
            WebViewProxyManager.parseProxyEndpoint("socks://192.0.2.10:1080")?.proxyUrl
        )
    }

    @Test
    fun acceptsProxyEndpointWithoutExplicitPort() {
        assertEquals(
            "http://proxy.example.com",
            WebViewProxyManager.parseProxyEndpoint("http://proxy.example.com")?.proxyUrl
        )
    }

    @Test
    fun normalizesProxySchemeCase() {
        assertEquals(
            "https://proxy.example.com:8443",
            WebViewProxyManager.parseProxyEndpoint("HTTPS://proxy.example.com:8443")?.proxyUrl
        )
    }

    @Test
    fun rejectsBlankMalformedAndSchemeLessEndpoints() {
        val invalid = listOf(
            "",
            " ",
            "proxy.example.com:8080",
            "http://",
            "http://proxy host:8080",
            "http://proxy.example.com:invalid"
        )
        invalid.forEach { assertNull(WebViewProxyManager.parseProxyEndpoint(it)) }
    }

    @Test
    fun rejectsUnsupportedProxySchemes() {
        assertNull(WebViewProxyManager.parseProxyEndpoint("ftp://proxy.example.com"))
        assertNull(WebViewProxyManager.parseProxyEndpoint("file:///tmp/proxy"))
        assertNull(WebViewProxyManager.parseProxyEndpoint("javascript:alert(1)"))
    }

    @Test
    fun rejectsCredentialsPathsQueriesAndFragments() {
        val invalid = listOf(
            "http://user:password@proxy.example.com:8080",
            "http://proxy.example.com:8080/path",
            "http://proxy.example.com:8080?token=secret",
            "http://proxy.example.com:8080#fragment"
        )
        invalid.forEach { assertNull(WebViewProxyManager.parseProxyEndpoint(it)) }
    }

    @Test
    fun rejectsInvalidPortsAndWhitespace() {
        val invalid = listOf(
            "http://proxy.example.com:0",
            "http://proxy.example.com:65536",
            "http://proxy.example.com:8080 "
        )
        invalid.forEach { assertNull(WebViewProxyManager.parseProxyEndpoint(it)) }
    }

    @Test
    fun approvedBypassRulesIncludeEachRootAndSubdomainPattern() {
        val rules = WebViewProxyManager.approvedBypassRules()
        val domains = listOf("scb.co.th", "kbank.com", "go.th", "gmail.com", "outlook.com")

        domains.forEach { domain ->
            assertTrue(rules.contains(domain))
            assertTrue(rules.contains("*.$domain"))
        }
        assertEquals(10, rules.size)
    }

    @Test
    fun approvedBypassMatcherIncludesRootAndNestedSubdomains() {
        val hosts = listOf(
            "scb.co.th",
            "mobile.scb.co.th",
            "a.b.scb.co.th",
            "kbank.com",
            "secure.kbank.com",
            "go.th",
            "agency.go.th",
            "gmail.com",
            "mail.google.com",
            "outlook.com",
            "login.outlook.com"
        )
        hosts.forEach { assertTrue("Expected direct bypass for $it", WebViewProxyManager.isApprovedBypassHost(it)) }
    }

    @Test
    fun approvedBypassMatcherRejectsLookalikeAndSuffixAttackHosts() {
        val hosts = listOf(
            "not-scb.co.th",
            "scb.co.th.evil.example",
            "evilscb.co.th",
            "kbank.com.attacker.test",
            "notgo.th",
            "gmail.com.evil.test",
            "outlook.com.attacker.test",
            "example.com"
        )
        hosts.forEach { assertFalse("Unexpected direct bypass for $it", WebViewProxyManager.isApprovedBypassHost(it)) }
    }

    @Test
    fun approvedBypassMatcherIgnoresHostCaseAndTrailingDot() {
        assertTrue(WebViewProxyManager.isApprovedBypassHost("LOGIN.GMAIL.COM."))
    }

    @Test
    fun invalidHostIsNotApprovedForBypass() {
        assertFalse(WebViewProxyManager.isApprovedBypassHost(""))
        assertFalse(WebViewProxyManager.isApprovedBypassHost("   "))
    }

    @Test
    fun parsedEndpointContainsNoCredentials() {
        val endpoint = WebViewProxyManager.parseProxyEndpoint("http://proxy.example.com:8080")
        assertNotNull(endpoint)
        assertEquals("http", endpoint?.scheme)
        assertEquals("proxy.example.com:8080", endpoint?.authority)
    }
}
