package com.rain.browser.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionManagerTest {
    @Test
    fun normalizesSecureOriginAndDefaultPort() {
        assertEquals(
            "https://example.com",
            PermissionManager.normalizeOrigin("HTTPS://Example.COM:443/")
        )
    }

    @Test
    fun preservesNonDefaultPort() {
        assertEquals(
            "https://example.com:8443",
            PermissionManager.normalizeOrigin("https://Example.com:8443")
        )
    }

    @Test
    fun rejectsInsecureOrigin() {
        assertNull(PermissionManager.normalizeOrigin("http://example.com"))
    }

    @Test
    fun rejectsCredentialBearingOrigin() {
        assertNull(PermissionManager.normalizeOrigin("https://user:pass@example.com"))
    }

    @Test
    fun rejectsPathBeyondOrigin() {
        assertNull(PermissionManager.normalizeOrigin("https://example.com/path"))
    }
}
