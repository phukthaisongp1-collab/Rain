package com.rain.browser.downloads

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Fetches an HLS playlist only after an explicit user selection.
 * It never copies WebView cookies, authorization, or referrer headers.
 */
object HlsPlaylistFetcher {
    data class FetchedPlaylist(
        val finalUrl: String,
        val content: String,
        val contentType: String
    )

    fun fetch(rawUrl: String): FetchedPlaylist {
        val uri = validatePublicUrl(rawUrl)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Connection", "close")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                responseCode == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                throw IllegalArgumentException("เว็บนี้จำกัดการดาวน์โหลด ไม่รองรับ")
            }
            require(responseCode == HttpURLConnection.HTTP_OK) {
                "ไม่สามารถดึง HLS Playlist ได้ (HTTP $responseCode)"
            }

            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0L || declaredLength <= MAX_PLAYLIST_BYTES) {
                "HLS Playlist มีขนาดเกินขีดจำกัดที่รองรับ"
            }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_PLAYLIST_BYTES) {
                        "HLS Playlist มีขนาดเกินขีดจำกัดที่รองรับ"
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val content = bytes.toString(StandardCharsets.UTF_8)
                .removePrefix("\uFEFF")
            require(content.startsWith("#EXTM3U")) {
                "URL นี้ไม่ใช่ HLS Playlist ที่ถูกต้อง"
            }

            return FetchedPlaylist(
                finalUrl = uri.toString(),
                content = content,
                contentType = connection.contentType.orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun validatePublicUrl(rawUrl: String): URI {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "รองรับเฉพาะ URL แบบ HTTP/HTTPS"
        }
        require(uri.rawUserInfo == null && uri.rawFragment == null) {
            "URL ของ HLS ต้องไม่มีข้อมูลรับรองหรือ Fragment"
        }
        val host = uri.host?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("URL ของ HLS ไม่มี Host ที่ถูกต้อง")
        require(!containsSensitiveQueryParameter(uri)) {
            "ไม่รองรับ URL ที่มี Token หรือข้อมูลยืนยันตัวตน"
        }
        require(
            host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local")
        ) {
            "ไม่รองรับแหล่งสื่อจากเครือข่ายภายใน"
        }

        val addresses = InetAddress.getAllByName(host.trim('[', ']'))
        require(addresses.isNotEmpty()) { "ไม่สามารถค้นหา IP ของ HLS Host ได้" }
        addresses.forEach { address ->
            require(
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress
            ) {
                "HLS Host ชี้ไปยังเครือข่ายภายในหรือ IP ที่ไม่ปลอดภัย"
            }
        }
        return uri
    }

    private fun containsSensitiveQueryParameter(uri: URI): Boolean {
        val blocked = setOf(
            "token", "access_token", "auth", "authorization", "key",
            "signature", "sig", "policy", "credential", "session", "cookie"
        )
        return uri.rawQuery.orEmpty().split('&').any { component ->
            val rawName = component.substringBefore('=')
            val decodedName = runCatching {
                URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
            }.getOrDefault(rawName)
            decodedName.lowercase(Locale.ROOT) in blocked
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 8_000
    private const val READ_TIMEOUT_MILLIS = 8_000
    private const val MAX_PLAYLIST_BYTES = 1_048_576
    private const val USER_AGENT = "RAIN Browser HLS"
}
