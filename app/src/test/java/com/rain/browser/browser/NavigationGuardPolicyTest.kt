package com.rain.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuardPolicyTest {
    @Test
    fun allowsSameHostAndSameSiteSubdomainNavigationWithoutPrompt() {
        assertTrue(
            NavigationGuardPolicy.isSameSite(
                "https://www.example.com/page",
                "https://shop.example.com/next"
            )
        )
        assertEquals(
            NavigationGuardPolicy.Decision.ALLOW_WEB,
            NavigationGuardPolicy.evaluate(
                "https://www.example.com",
                "https://shop.example.com/next",
                true,
                false
            )
        )
    }

    @Test
    fun recognizesCommonMultiLabelPublicSuffixes() {
        assertTrue(
            NavigationGuardPolicy.isSameSite(
                "https://www.example.co.uk",
                "https://shop.example.co.uk"
            )
        )
        assertTrue(
            NavigationGuardPolicy.isSameSite(
                "https://www.example.co.th",
                "https://m.example.co.th"
            )
        )
    }

    @Test
    fun requiresConfirmationForCrossSiteMainFrameNavigationWithoutGesture() {
        assertEquals(
            NavigationGuardPolicy.Decision.CONFIRM_CROSS_SITE,
            NavigationGuardPolicy.evaluate(
                "https://trusted.example",
                "https://destination.test/path",
                true,
                false
            )
        )
    }

    @Test
    fun allowsCrossSiteNavigationWithUserGesture() {
        assertEquals(
            NavigationGuardPolicy.Decision.ALLOW_WEB,
            NavigationGuardPolicy.evaluate(
                "https://trusted.example",
                "https://destination.test/path",
                true,
                true
            )
        )
    }

    @Test
    fun neverPromptsForSameSiteRedirects() {
        assertEquals(
            NavigationGuardPolicy.Decision.ALLOW_WEB,
            NavigationGuardPolicy.evaluate(
                "https://a.example.com",
                "https://b.example.com/redirected",
                true,
                false
            )
        )
    }

    @Test
    fun blocksExternalSchemesWithoutUserGesture() {
        assertEquals(
            NavigationGuardPolicy.Decision.BLOCK,
            NavigationGuardPolicy.evaluate(
                "https://example.com",
                "tel:+66123456789",
                true,
                false
            )
        )
    }

    @Test
    fun requiresConfirmationForAllowlistedExternalSchemesWithGesture() {
        assertEquals(
            NavigationGuardPolicy.Decision.CONFIRM_EXTERNAL,
            NavigationGuardPolicy.evaluate(
                "https://example.com",
                "mailto:support@example.com",
                true,
                true
            )
        )
        assertEquals(
            NavigationGuardPolicy.Decision.CONFIRM_EXTERNAL,
            NavigationGuardPolicy.evaluate(
                "https://example.com",
                "tel:+66123456789",
                true,
                true
            )
        )
    }

    @Test
    fun blocksDangerousSchemesEvenWithUserGesture() {
        val urls = listOf(
            "javascript:alert(1)",
            "intent://example.com",
            "file:///etc/passwd",
            "content://provider/item",
            "data:text/html,hello"
        )
        urls.forEach { url ->
            assertEquals(
                url,
                NavigationGuardPolicy.Decision.BLOCK,
                NavigationGuardPolicy.evaluate("https://example.com", url, true, true)
            )
        }
    }

    @Test
    fun blocksExternalSchemesFromSubframes() {
        assertEquals(
            NavigationGuardPolicy.Decision.BLOCK,
            NavigationGuardPolicy.evaluate(
                "https://example.com",
                "mailto:support@example.com",
                false,
                true
            )
        )
    }

    @Test
    fun validatesExternalUrlsAndRejectsMalformedOrUnapprovedSchemes() {
        assertTrue(NavigationGuardPolicy.isAllowedExternalUrl("mailto:help@example.com"))
        assertTrue(NavigationGuardPolicy.isAllowedExternalUrl("tel:+66123456789"))
        assertTrue(NavigationGuardPolicy.isAllowedExternalUrl("geo:13.7563,100.5018"))
        assertFalse(NavigationGuardPolicy.isAllowedExternalUrl("intent://example.com"))
        assertFalse(NavigationGuardPolicy.isAllowedExternalUrl("tel:abc"))
        assertFalse(NavigationGuardPolicy.isAllowedExternalUrl("mailto:"))
        assertFalse(NavigationGuardPolicy.isAllowedExternalUrl("mailto:help@example.com%0d%0aBcc:x"))
    }
}
