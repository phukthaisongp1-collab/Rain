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
    fun rejectsUnsupportedSchemes() {
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("javascript:alert(1)"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("file:///etc/passwd"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("intent://example.com"))
    }

    @Test
    fun rejectsMalformedAndCredentialUrls() {
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("not a url"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("https://user:pass@example.com"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl(""))
    }
}
