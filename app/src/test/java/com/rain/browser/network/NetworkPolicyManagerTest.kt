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
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("content://com.example.provider/item"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("data:text/html,hello"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("mailto:test@example.com"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("tel:+66000000000"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("market://details?id=com.example.app"))
    }

    @Test
    fun rejectsMalformedAndCredentialUrls() {
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("not a url"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("https://user:pass@example.com"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl(""))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("https:///missing-host"))
        assertFalse(NetworkPolicyManager.isAllowedWebUrl("https://example.com\\n"))
    }
}
