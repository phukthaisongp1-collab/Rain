package com.rain.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSecurityPolicyTest {
    @Test
    fun sslCertificateErrorsAreAlwaysCancelled() {
        assertEquals(
            BrowserSecurityPolicy.SslErrorAction.CANCEL,
            BrowserSecurityPolicy.sslErrorAction
        )
    }

    @Test
    fun mixedContentAndSafeBrowsingUseSecureDefaults() {
        assertEquals(1, BrowserSecurityPolicy.mixedContentMode)
        assertTrue(BrowserSecurityPolicy.safeBrowsingEnabled)
    }

    @Test
    fun localFileAndContentAccessAreDisabled() {
        assertFalse(BrowserSecurityPolicy.allowFileAccess)
        assertFalse(BrowserSecurityPolicy.allowContentAccess)
        assertFalse(BrowserSecurityPolicy.allowFileAccessFromFileUrls)
        assertFalse(BrowserSecurityPolicy.allowUniversalAccessFromFileUrls)
    }

    @Test
    fun unsolicitedWindowCreationIsDisabled() {
        assertFalse(BrowserSecurityPolicy.javaScriptCanOpenWindowsAutomatically)
        assertFalse(BrowserSecurityPolicy.supportMultipleWindows)
    }
}
