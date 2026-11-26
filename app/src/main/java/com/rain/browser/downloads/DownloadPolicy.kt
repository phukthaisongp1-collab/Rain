package com.rain.browser.downloads

import java.net.URI
import java.util.Locale

object DownloadPolicy {
    enum class Action { CONFIRM, BLOCK }
    enum class Risk { NORMAL, UNKNOWN_TYPE, APK_PACKAGE, INSECURE_SOURCE }

    data class Decision(
        val action: Action,
        val risk: Risk,
        val fileName: String,
        val mimeType: String,
        val sourceHost: String?,
        val reason: String,
        val isApk: Boolean
    )

    private val blockedExtensions = setOf(
        "exe", "com", "bat", "cmd", "msi", "sh", "ps1", "vbs", "scr",
        "jar", "dex", "so", "elf", "deb", "rpm"
    )

    private val expectedMimeTypes = mapOf(
        "apk" to setOf("application/vnd.android.package-archive"),
        "pdf" to setOf("application/pdf"),
        "png" to setOf("image/png"),
        "jpg" to setOf("image/jpeg"),
        "jpeg" to setOf("image/jpeg"),
        "gif" to setOf("image/gif"),
        "webp" to setOf("image/webp"),
        "txt" to setOf("text/plain"),
        "csv" to setOf("text/csv", "application/csv"),
        "zip" to setOf("application/zip", "application/x-zip-compressed")
    )

    fun evaluate(url: String, rawFileName: String, rawMimeType: String?): Decision {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        val host = uri?.host?.takeIf { it.isNotBlank() }
        val mimeType = rawMimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        val fileName = sanitizeFileName(rawFileName)
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isApk = extension == "apk"

        fun block(reason: String) = Decision(
            Action.BLOCK, Risk.UNKNOWN_TYPE, fileName, mimeType, host, reason, isApk
        )

        if (uri == null || host == null || uri.rawUserInfo != null ||
            (scheme != "https" && scheme != "http")
        ) {
            return block("The download URL is invalid or uses an unsupported source.")
        }
        if (fileName.isBlank() || fileName == "." || fileName == "..") {
            return block("The server did not provide a safe file name.")
        }
        if (extension in blockedExtensions || mimeType in setOf(
                "application/x-msdownload",
                "application/x-executable",
                "application/x-sh",
                "application/java-archive",
                "application/vnd.microsoft.portable-executable"
            )
        ) {
            return block("This executable or script file type is blocked by RAIN.")
        }
        if (mimeType in setOf("text/html", "application/xhtml+xml") &&
            extension !in setOf("html", "htm")
        ) {
            return block("The file extension does not match the server's HTML content type.")
        }
        if (isApk && mimeType.isNotBlank() &&
            mimeType !in setOf("application/vnd.android.package-archive", "application/octet-stream")
        ) {
            return block("The APK extension conflicts with the reported content type.")
        }

        val expectedTypes = expectedMimeTypes[extension]
        if (!expectedTypes.isNullOrEmpty() && mimeType.isNotBlank() &&
            mimeType != "application/octet-stream" && mimeType !in expectedTypes
        ) {
            return block("The file extension and reported content type do not match.")
        }

        val risk: Risk
        val reason: String
        when {
            isApk -> {
                risk = Risk.APK_PACKAGE
                reason = "APK files can install applications. RAIN will only save this file and will never install it automatically. Verify the publisher before opening it."
            }
            scheme != "https" -> {
                risk = Risk.INSECURE_SOURCE
                reason = "This download uses an unencrypted HTTP connection. Confirm only if you trust the source."
            }
            mimeType.isBlank() || mimeType == "application/octet-stream" ||
                extension.isBlank() || expectedTypes == null -> {
                risk = Risk.UNKNOWN_TYPE
                reason = "RAIN cannot confidently identify this file type. Review the source and file name before downloading."
            }
            else -> {
                risk = Risk.NORMAL
                reason = "Review the source and file details before confirming the download."
            }
        }

        return Decision(Action.CONFIRM, risk, fileName, mimeType, host, reason, isApk)
    }

    fun sanitizeFileName(rawFileName: String): String {
        val leafName = rawFileName.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = leafName
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_")
            .replace(Regex("[<>:\"|?*]"), "_")
            .trim()
        if (cleaned.length <= 120) return cleaned
        val extension = cleaned.substringAfterLast('.', "").takeIf { it.length in 1..12 }
        return if (extension == null) {
            cleaned.take(120)
        } else {
            cleaned.substringBeforeLast('.').take(120 - extension.length - 1) + "." + extension
        }
    }
}
