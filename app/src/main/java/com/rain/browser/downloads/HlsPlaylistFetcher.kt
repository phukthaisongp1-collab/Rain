package com.rain.browser.downloads

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Fetches a selected HLS playlist without copying credentials from the WebView.
 * Every request and redirect is routed through HlsHttpClient destination checks.
 */
object HlsPlaylistFetcher {
    data class FetchedPlaylist(
        val finalUrl: String,
        val content: String,
        val contentType: String
    )

    fun fetch(rawUrl: String): FetchedPlaylist {
        val response = HlsHttpClient.get(
            rawUrl = rawUrl,
            maximumBytes = MAX_PLAYLIST_BYTES,
            accept = "application/vnd.apple.mpegurl, application/x-mpegURL, application/dash+xml, text/html, */*"
        )
        val content = response.body.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
        if (!content.startsWith("#EXTM3U")) {
            throw IllegalArgumentException(unsupportedResponseMessage(content, response.contentType))
        }
        return FetchedPlaylist(
            finalUrl = response.finalUrl,
            content = content,
            contentType = response.contentType
        )
    }

    private fun unsupportedResponseMessage(content: String, contentType: String): String {
        val sample = content.take(RESPONSE_CLASSIFICATION_BYTES).lowercase(Locale.ROOT)
        val normalizedType = contentType.lowercase(Locale.ROOT)
        val isHtml = normalizedType.contains("text/html") ||
            sample.trimStart().startsWith("<!doctype html") ||
            sample.trimStart().startsWith("<html")
        val isDash = normalizedType.contains("dash+xml") ||
            MPD_TAG.containsMatchIn(sample)
        if (isDash) return "ไม่รองรับ DASH (MPD) สำหรับการดาวน์โหลดวิดีโอออฟไลน์"
        if (CLOUDFLARE_MARKERS.any { marker -> sample.contains(marker) }) {
            return "เว็บนี้แสดง Cloudflare Challenge หรือหน้าป้องกันอัตโนมัติ RAIN ไม่สามารถข้ามได้"
        }
        if (isHtml && LOGIN_MARKERS.any { marker -> sample.contains(marker) } && PASSWORD_INPUT.containsMatchIn(sample)) {
            return "เว็บนี้ต้องเข้าสู่ระบบ RAIN ไม่รองรับการใช้ Cookie หรือข้อมูลเข้าสู่ระบบจากเว็บไซต์"
        }
        if (isHtml) {
            return "เว็บนี้ส่งหน้า HTML แทน HLS Playlist อาจต้องเข้าสู่ระบบหรือถูกจำกัดการดาวน์โหลด"
        }
        return "URL นี้ไม่ใช่ HLS Playlist ที่ถูกต้อง"
    }

    private const val MAX_PLAYLIST_BYTES = 1_048_576
    private const val RESPONSE_CLASSIFICATION_BYTES = 8_192
    private val MPD_TAG = Regex("<\\s*(?:[a-z0-9_-]+:)?mpd(?:\\s|>)", RegexOption.IGNORE_CASE)
    private val PASSWORD_INPUT = Regex(
        "<input[^>]+type\\s*=\\s*['\\\"]password['\\\"]",
        RegexOption.IGNORE_CASE
    )
    private val CLOUDFLARE_MARKERS = listOf(
        "cloudflare",
        "cf-chl-",
        "challenge-platform",
        "just a moment"
    )
    private val LOGIN_MARKERS = listOf(
        "login",
        "log in",
        "sign in",
        "signin",
        "เข้าสู่ระบบ"
    )
}
