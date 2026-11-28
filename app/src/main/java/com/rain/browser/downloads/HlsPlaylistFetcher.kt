package com.rain.browser.downloads

import java.nio.charset.StandardCharsets

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
            accept = "application/vnd.apple.mpegurl, application/x-mpegURL, */*"
        )
        val content = response.body.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
        require(content.startsWith("#EXTM3U")) {
            "URL นี้ไม่ใช่ HLS Playlist ที่ถูกต้อง"
        }
        return FetchedPlaylist(
            finalUrl = response.finalUrl,
            content = content,
            contentType = response.contentType
        )
    }

    private const val MAX_PLAYLIST_BYTES = 1_048_576
}
