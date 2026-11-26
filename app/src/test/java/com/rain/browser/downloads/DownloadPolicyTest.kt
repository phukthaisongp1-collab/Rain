package com.rain.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test
    fun allowsKnownPdfOnlyAfterConfirmation() {
        val decision = DownloadPolicy.evaluate(
            "https://files.example/report.pdf",
            "report.pdf",
            "application/pdf"
        )

        assertEquals(DownloadPolicy.Action.CONFIRM, decision.action)
        assertEquals(DownloadPolicy.Risk.NORMAL, decision.risk)
    }

    @Test
    fun requiresExplicitConfirmationForApkAndNeverMarksItForInstallation() {
        val decision = DownloadPolicy.evaluate(
            "https://files.example/app.apk",
            "app.apk",
            "application/vnd.android.package-archive"
        )

        assertEquals(DownloadPolicy.Action.CONFIRM, decision.action)
        assertEquals(DownloadPolicy.Risk.APK_PACKAGE, decision.risk)
        assertTrue(decision.isApk)
        assertTrue(decision.reason.contains("never install it automatically"))
    }

    @Test
    fun blocksExecutableFiles() {
        val decision = DownloadPolicy.evaluate(
            "https://files.example/setup.exe",
            "setup.exe",
            "application/octet-stream"
        )

        assertEquals(DownloadPolicy.Action.BLOCK, decision.action)
    }

    @Test
    fun blocksMimeTypeMismatch() {
        val decision = DownloadPolicy.evaluate(
            "https://files.example/report.pdf",
            "report.pdf",
            "text/html"
        )

        assertEquals(DownloadPolicy.Action.BLOCK, decision.action)
    }

    @Test
    fun warnsForUnknownFileTypesByRequiringConfirmation() {
        val decision = DownloadPolicy.evaluate(
            "https://files.example/data.unknown",
            "data.unknown",
            "application/octet-stream"
        )

        assertEquals(DownloadPolicy.Action.CONFIRM, decision.action)
        assertEquals(DownloadPolicy.Risk.UNKNOWN_TYPE, decision.risk)
    }

    @Test
    fun flagsUnencryptedHttpSourceForConfirmation() {
        val decision = DownloadPolicy.evaluate(
            "http://files.example/report.pdf",
            "report.pdf",
            "application/pdf"
        )

        assertEquals(DownloadPolicy.Action.CONFIRM, decision.action)
        assertEquals(DownloadPolicy.Risk.INSECURE_SOURCE, decision.risk)
    }

    @Test
    fun rejectsInvalidAndCredentialBearingUrls() {
        assertEquals(
            DownloadPolicy.Action.BLOCK,
            DownloadPolicy.evaluate("javascript:alert(1)", "file.txt", "text/plain").action
        )
        assertEquals(
            DownloadPolicy.Action.BLOCK,
            DownloadPolicy.evaluate(
                "https://user:pass@files.example/file.txt",
                "file.txt",
                "text/plain"
            ).action
        )
    }

    @Test
    fun sanitizesPathTraversalAndControlCharacters() {
        val sanitized = DownloadPolicy.sanitizeFileName("../../safe\u0000name.pdf")

        assertEquals("safe_name.pdf", sanitized)
        assertFalse(sanitized.contains('/'))
    }
}
