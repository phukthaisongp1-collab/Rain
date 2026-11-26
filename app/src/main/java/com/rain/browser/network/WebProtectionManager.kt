package com.rain.browser.network

/**
 * Evaluates explicit threat evidence without guessing from a website's appearance or category.
 *
 * Evidence producers must identify the source and provide a reason. Only trusted security
 * providers can cause a hard block; unverified reports can produce a warning but never a block.
 */
object WebProtectionManager {

    enum class ThreatCategory {
        PHISHING,
        MALWARE,
        SCAM,
        GAMBLING
    }

    enum class EvidenceSource {
        ANDROID_SAFE_BROWSING,
        TRUSTED_THREAT_INTELLIGENCE,
        VERIFIED_BEHAVIORAL_SIGNAL,
        USER_REPORT,
        UNVERIFIED
    }

    enum class Action {
        ALLOW,
        WARN,
        BLOCK
    }

    data class ThreatEvidence(
        val category: ThreatCategory,
        val source: EvidenceSource,
        val sourceName: String,
        val sourceReference: String,
        val reason: String,
        val confidence: Double,
        val observedAtEpochMillis: Long,
        val expiresAtEpochMillis: Long,
        val corroboratingSourceCount: Int = 1
    )

    data class Settings(
        val blockGamblingSites: Boolean = false
    )

    data class Decision(
        val action: Action,
        val category: ThreatCategory? = null,
        val reason: String,
        val sourceName: String? = null,
        val sourceReference: String? = null
    )

    private const val WARNING_CONFIDENCE = 0.60
    private const val PHISHING_MALWARE_BLOCK_CONFIDENCE = 0.90
    private const val SCAM_BLOCK_CONFIDENCE = 0.95
    private const val GAMBLING_BLOCK_CONFIDENCE = 0.90

    /**
     * Returns ALLOW when no current, usable evidence exists.
     * A warning is used for suspicious but insufficiently corroborated evidence.
     */
    fun evaluate(
        evidence: Collection<ThreatEvidence>,
        nowEpochMillis: Long,
        settings: Settings = Settings()
    ): Decision {
        val currentEvidence = evidence
            .asSequence()
            .filter { isUsable(it, nowEpochMillis) }
            .filterNot { it.category == ThreatCategory.GAMBLING && !settings.blockGamblingSites }
            .toList()

        if (currentEvidence.isEmpty()) {
            return Decision(
                action = Action.ALLOW,
                reason = "No current threat evidence meets the warning or blocking criteria."
            )
        }

        val blockingEvidence = currentEvidence
            .filter(::isTrustedBlockingSource)
            .filter { canBlock(it) }
            .maxWithOrNull(
                compareBy<ThreatEvidence> { blockPriority(it.category) }
                    .thenBy { it.confidence }
                    .thenBy { it.corroboratingSourceCount }
            )

        if (blockingEvidence != null) {
            return blockingDecision(blockingEvidence)
        }

        val warningEvidence = currentEvidence
            .filter { it.confidence >= WARNING_CONFIDENCE }
            .maxWithOrNull(
                compareBy<ThreatEvidence> { warningPriority(it.category) }
                    .thenBy { it.confidence }
                    .thenBy { it.corroboratingSourceCount }
            )

        if (warningEvidence != null) {
            return Decision(
                action = Action.WARN,
                category = warningEvidence.category,
                reason = warningEvidence.reason,
                sourceName = warningEvidence.sourceName,
                sourceReference = warningEvidence.sourceReference
            )
        }

        return Decision(
            action = Action.ALLOW,
            reason = "Available evidence is below the warning threshold."
        )
    }

    private fun isUsable(evidence: ThreatEvidence, nowEpochMillis: Long): Boolean {
        return evidence.sourceName.isNotBlank() &&
            evidence.reason.isNotBlank() &&
            evidence.confidence in 0.0..1.0 &&
            evidence.observedAtEpochMillis <= nowEpochMillis &&
            evidence.expiresAtEpochMillis > nowEpochMillis &&
            evidence.expiresAtEpochMillis > evidence.observedAtEpochMillis &&
            evidence.corroboratingSourceCount >= 1
    }

    private fun isTrustedBlockingSource(evidence: ThreatEvidence): Boolean {
        return evidence.source == EvidenceSource.ANDROID_SAFE_BROWSING ||
            evidence.source == EvidenceSource.TRUSTED_THREAT_INTELLIGENCE
    }

    private fun canBlock(evidence: ThreatEvidence): Boolean {
        if (evidence.sourceReference.isBlank()) return false

        return when (evidence.category) {
            ThreatCategory.PHISHING,
            ThreatCategory.MALWARE ->
                evidence.confidence >= PHISHING_MALWARE_BLOCK_CONFIDENCE

            ThreatCategory.SCAM ->
                evidence.confidence >= SCAM_BLOCK_CONFIDENCE &&
                    evidence.corroboratingSourceCount >= 2

            ThreatCategory.GAMBLING ->
                evidence.confidence >= GAMBLING_BLOCK_CONFIDENCE
        }
    }

    private fun blockingDecision(evidence: ThreatEvidence): Decision {
        return Decision(
            action = Action.BLOCK,
            category = evidence.category,
            reason = evidence.reason,
            sourceName = evidence.sourceName,
            sourceReference = evidence.sourceReference
        )
    }

    private fun blockPriority(category: ThreatCategory): Int = when (category) {
        ThreatCategory.MALWARE -> 4
        ThreatCategory.PHISHING -> 3
        ThreatCategory.SCAM -> 2
        ThreatCategory.GAMBLING -> 1
    }

    private fun warningPriority(category: ThreatCategory): Int = when (category) {
        ThreatCategory.MALWARE -> 4
        ThreatCategory.PHISHING -> 3
        ThreatCategory.SCAM -> 2
        ThreatCategory.GAMBLING -> 1
    }
}
