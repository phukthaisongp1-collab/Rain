package com.rain.browser.downloads

import java.net.URI
import java.util.Locale

/**
 * Native fail-closed decision policy for minimal, untrusted WebView video signals.
 * A candidate is not permission to download; native source validation is still required.
 */
object VideoDetectionPolicy {
    enum class Decision {
        IGNORE,
        REJECT_AD,
        NEED_MORE_EVIDENCE,
        CANDIDATE
    }

    data class Signals(
        val pageUrl: String,
        val sourceUrl: String,
        val mimeType: String,
        val title: String,
        val posterUrl: String,
        val playerContext: String,
        val durationMs: Long?,
        val userInitiated: Boolean,
        val mainFrame: Boolean,
        val sourceStable: Boolean,
        val sourceFromVideoElement: Boolean,
        val adStatus: Boolean?,
        val visibleAdText: List<String>
    )

    fun evaluate(signals: Signals): Decision {
        if (!signals.mainFrame || !signals.userInitiated ||
            !signals.sourceFromVideoElement || !signals.sourceStable
        ) return Decision.IGNORE

        if (hasAdEvidence(signals)) return Decision.REJECT_AD
        if (signals.adStatus != false) return Decision.NEED_MORE_EVIDENCE

        val page = parseWebUrl(signals.pageUrl) ?: return Decision.IGNORE
        val source = parseWebUrl(signals.sourceUrl) ?: return Decision.IGNORE
        if (page.host.isNullOrBlank() || source.host.isNullOrBlank()) return Decision.IGNORE

        val mime = signals.mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime !in SUPPORTED_MIME_TYPES) return Decision.NEED_MORE_EVIDENCE

        if (signals.durationMs != null && signals.durationMs < 0L) {
            return Decision.NEED_MORE_EVIDENCE
        }

        val hasTitle = signals.title.trim().length >= MIN_TITLE_LENGTH
        val hasPoster = signals.posterUrl.isNotBlank() && parseWebUrl(signals.posterUrl) != null
        val hasPlayerContext = signals.playerContext.trim().length >= MIN_CONTEXT_LENGTH
        val metadataSignals = listOf(hasTitle, hasPoster, hasPlayerContext).count { it }

        // Duration is corroborating metadata only and can never establish main content alone.
        if (metadataSignals < 2) return Decision.NEED_MORE_EVIDENCE
        if (signals.durationMs == null || signals.durationMs == 0L) {
            return Decision.NEED_MORE_EVIDENCE
        }

        return Decision.CANDIDATE
    }

    private fun hasAdEvidence(signals: Signals): Boolean {
        if (signals.adStatus == true) return true
        val text = signals.visibleAdText.joinToString(" ").lowercase(Locale.ROOT)
        val markers = AD_MARKERS.count { marker -> text.contains(marker) }
        return markers >= 1
    }

    private fun parseWebUrl(value: String): URI? = runCatching {
        val uri = URI(value.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if ((scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        ) uri else null
    }.getOrNull()

    private const val MIN_TITLE_LENGTH = 2
    private const val MIN_CONTEXT_LENGTH = 3
    private val SUPPORTED_MIME_TYPES = setOf("video/mp4", "video/webm")
    private val AD_MARKERS = setOf(
        "advertisement",
        "skip ad",
        "sponsored",
        "ad choices",
        "pre-roll",
        "mid-roll"
    )
}
