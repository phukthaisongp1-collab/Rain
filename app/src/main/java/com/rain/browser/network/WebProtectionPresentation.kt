package com.rain.browser.network

object WebProtectionPresentation {
    fun title(decision: WebProtectionManager.Decision): String = when (decision.action) {
        WebProtectionManager.Action.ALLOW -> "Website protection"
        WebProtectionManager.Action.WARN -> "Suspicious website"
        WebProtectionManager.Action.BLOCK -> "Dangerous website blocked"
    }

    fun message(url: String, decision: WebProtectionManager.Decision): String {
        val details = buildList {
            add("Website: $url")
            decision.category?.let { add("Category: ${it.name.lowercase().replace('_', ' ')}") }
            decision.sourceName?.let { add("Source: $it") }
            if (decision.reason.isNotBlank()) add("Reason: ${decision.reason}")
            decision.sourceReference.takeIf { !it.isNullOrBlank() }?.let {
                add("Reference: $it")
            }
        }
        return details.joinToString("\n")
    }

    fun falsePositiveReport(
        url: String,
        decision: WebProtectionManager.Decision?,
        reportedAtEpochMillis: Long
    ): String = buildString {
        appendLine("RAIN Browser false-positive report")
        appendLine("Website: $url")
        appendLine("Reported at: $reportedAtEpochMillis")
        if (decision != null) {
            decision.category?.let { appendLine("Category: ${it.name}") }
            decision.sourceName?.let { appendLine("Detection source: $it") }
            decision.sourceReference?.let { appendLine("Source reference: $it") }
            appendLine("Detection reason: ${decision.reason}")
            appendLine("Decision: ${decision.action}")
        }
        append("The user believes this website was incorrectly warned about or blocked.")
    }
}
