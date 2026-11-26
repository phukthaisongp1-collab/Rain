package com.rain.browser.network

import org.junit.Assert.assertTrue
import org.junit.Test

class WebProtectionPresentationTest {
    @Test
    fun warningMessageShowsCategorySourceAndReason() {
        val decision = WebProtectionManager.Decision(
            action = WebProtectionManager.Action.WARN,
            category = WebProtectionManager.ThreatCategory.SCAM,
            reason = "Corroboration is incomplete.",
            sourceName = "Trusted Report",
            sourceReference = "https://security.example/report/1"
        )

        val message = WebProtectionPresentation.message("https://example.com", decision)

        assertTrue(message.contains("https://example.com"))
        assertTrue(message.contains("scam"))
        assertTrue(message.contains("Trusted Report"))
        assertTrue(message.contains("Corroboration is incomplete."))
        assertTrue(message.contains("https://security.example/report/1"))
    }

    @Test
    fun falsePositiveReportIncludesUrlAndDetectionEvidence() {
        val decision = WebProtectionManager.Decision(
            action = WebProtectionManager.Action.BLOCK,
            category = WebProtectionManager.ThreatCategory.PHISHING,
            reason = "Reported credential theft.",
            sourceName = "Security Feed",
            sourceReference = "https://security.example/report/2"
        )

        val report = WebProtectionPresentation.falsePositiveReport(
            "https://example.com",
            decision,
            1234L
        )

        assertTrue(report.contains("https://example.com"))
        assertTrue(report.contains("Security Feed"))
        assertTrue(report.contains("Reported credential theft."))
        assertTrue(report.contains("BLOCK"))
    }
}
