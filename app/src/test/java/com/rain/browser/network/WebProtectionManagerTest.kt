package com.rain.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebProtectionManagerTest {

    private val now = 1_800_000_000_000L

    private fun evidence(
        category: WebProtectionManager.ThreatCategory = WebProtectionManager.ThreatCategory.PHISHING,
        source: WebProtectionManager.EvidenceSource = WebProtectionManager.EvidenceSource.TRUSTED_THREAT_INTELLIGENCE,
        confidence: Double = 0.99,
        sourceReference: String = "https://security.example/reports/123",
        corroboratingSourceCount: Int = 2,
        observedAt: Long = now - 1_000,
        expiresAt: Long = now + 60_000
    ) = WebProtectionManager.ThreatEvidence(
        category = category,
        source = source,
        sourceName = "Trusted Security Source",
        sourceReference = sourceReference,
        reason = "Verified malicious activity was reported.",
        confidence = confidence,
        observedAtEpochMillis = observedAt,
        expiresAtEpochMillis = expiresAt,
        corroboratingSourceCount = corroboratingSourceCount
    )

    @Test
    fun blocksCrediblePhishingEvidence() {
        val decision = WebProtectionManager.evaluate(listOf(evidence()), now)

        assertEquals(WebProtectionManager.Action.BLOCK, decision.action)
        assertEquals(WebProtectionManager.ThreatCategory.PHISHING, decision.category)
        assertEquals("Trusted Security Source", decision.sourceName)
    }

    @Test
    fun blocksCredibleMalwareEvidence() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(category = WebProtectionManager.ThreatCategory.MALWARE)),
            now
        )

        assertEquals(WebProtectionManager.Action.BLOCK, decision.action)
        assertEquals(WebProtectionManager.ThreatCategory.MALWARE, decision.category)
    }

    @Test
    fun warnsOnScamEvidenceWithoutTwoCorroboratingSources() {
        val decision = WebProtectionManager.evaluate(
            listOf(
                evidence(
                    category = WebProtectionManager.ThreatCategory.SCAM,
                    corroboratingSourceCount = 1
                )
            ),
            now
        )

        assertEquals(WebProtectionManager.Action.WARN, decision.action)
        assertEquals(WebProtectionManager.ThreatCategory.SCAM, decision.category)
    }

    @Test
    fun blocksScamOnlyWhenEvidenceIsStrongAndCorroborated() {
        val decision = WebProtectionManager.evaluate(
            listOf(
                evidence(
                    category = WebProtectionManager.ThreatCategory.SCAM,
                    corroboratingSourceCount = 2
                )
            ),
            now
        )

        assertEquals(WebProtectionManager.Action.BLOCK, decision.action)
    }

    @Test
    fun unverifiedSourceCanWarnButCannotBlock() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(source = WebProtectionManager.EvidenceSource.UNVERIFIED)),
            now
        )

        assertEquals(WebProtectionManager.Action.WARN, decision.action)
    }

    @Test
    fun ordinaryWebsiteWithoutEvidenceIsAllowed() {
        val decision = WebProtectionManager.evaluate(emptyList(), now)

        assertEquals(WebProtectionManager.Action.ALLOW, decision.action)
        assertNull(decision.category)
    }

    @Test
    fun weakEvidenceDoesNotCauseWarningOrBlock() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(confidence = 0.59)),
            now
        )

        assertEquals(WebProtectionManager.Action.ALLOW, decision.action)
    }

    @Test
    fun expiredEvidenceIsIgnored() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(expiresAt = now)),
            now
        )

        assertEquals(WebProtectionManager.Action.ALLOW, decision.action)
    }

    @Test
    fun futureEvidenceIsIgnored() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(observedAt = now + 1)),
            now
        )

        assertEquals(WebProtectionManager.Action.ALLOW, decision.action)
    }

    @Test
    fun missingSourceReferenceCannotCauseHardBlock() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(sourceReference = "")),
            now
        )

        assertEquals(WebProtectionManager.Action.WARN, decision.action)
    }

    @Test
    fun gamblingIsIndependentAndDisabledByDefault() {
        val gambling = evidence(category = WebProtectionManager.ThreatCategory.GAMBLING)

        val disabledDecision = WebProtectionManager.evaluate(listOf(gambling), now)
        val enabledDecision = WebProtectionManager.evaluate(
            listOf(gambling),
            now,
            WebProtectionManager.Settings(blockGamblingSites = true)
        )

        assertEquals(WebProtectionManager.Action.ALLOW, disabledDecision.action)
        assertEquals(WebProtectionManager.Action.BLOCK, enabledDecision.action)
        assertEquals(WebProtectionManager.ThreatCategory.GAMBLING, enabledDecision.category)
    }

    @Test
    fun invalidConfidenceIsIgnored() {
        val decision = WebProtectionManager.evaluate(
            listOf(evidence(confidence = 1.01)),
            now
        )

        assertEquals(WebProtectionManager.Action.ALLOW, decision.action)
    }
}
