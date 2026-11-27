package com.rain.browser.downloads

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Verifies a public, directly accessible MP4/WebM response without site cookies
 * or credentials. Redirects and every resolved address are checked natively.
 */
object VideoSourceValidator {
    data class ValidatedSource(
        val finalUrl: String,
        val mimeType: String,
        val contentLength: Long,
        val etag: String,
        val lastModified: String,
        val container: VideoContainerValidator.Container
    )

    fun validate(rawUrl: String, allowLoopbackForTests: Boolean = false): ValidatedSource {
        var current = validatePublicUrl(rawUrl, allowLoopbackForTests)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (current.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Range", "bytes=0-31")
                setRequestProperty("Accept", "video/mp4, video/webm")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode in REDIRECT_CODES) {
                    require(redirectCount < MAX_REDIRECTS) { "Video source exceeded the redirect limit." }
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalArgumentException("Video source redirect has no destination.")
                    current = validatePublicUrl(current.resolve(location).toString(), allowLoopbackForTests)
                    return@repeat
                }

                require(responseCode == HttpURLConnection.HTTP_OK ||
                    responseCode == HttpURLConnection.HTTP_PARTIAL
                ) { "Video source is not publicly accessible (HTTP $responseCode)." }

                if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                    require(CONTENT_RANGE.matches(contentRange)) {
                        "Video source returned an invalid byte range."
                    }
                }

                val mimeType = connection.contentType.orEmpty()
                    .substringBefore(';').trim().lowercase(Locale.ROOT)
                require(mimeType == "video/mp4" || mimeType == "video/webm") {
                    "Only directly accessible MP4 and WebM media are supported."
                }

                val contentLength = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    connection.getHeaderField("Content-Range")
                        .substringAfterLast('/')
                        .toLongOrNull() ?: connection.contentLengthLong
                } else {
                    connection.contentLengthLong
                }
                require(contentLength < 0L || contentLength >= MIN_CONTAINER_BYTES) {
                    "Video source is too small to contain a supported video container."
                }

                val prefix = connection.inputStream.use { it.readNBytes(CONTAINER_PREFIX_BYTES) }
                val container = VideoContainerValidator.detect(prefix)
                val matchesMime = (mimeType == "video/mp4" &&
                    container == VideoContainerValidator.Container.MP4) ||
                    (mimeType == "video/webm" &&
                        container == VideoContainerValidator.Container.WEBM)
                require(matchesMime) { "Video response content does not match its declared container type." }

                return ValidatedSource(
                    finalUrl = current.toString(),
                    mimeType = mimeType,
                    contentLength = contentLength,
                    etag = connection.getHeaderField("ETag").orEmpty().take(MAX_HEADER_LENGTH),
                    lastModified = connection.getHeaderField("Last-Modified").orEmpty().take(MAX_HEADER_LENGTH),
                    container = container
                )
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalArgumentException("Unable to validate the video source redirect chain.")
    }

    private fun validatePublicUrl(rawUrl: String, allowLoopbackForTests: Boolean): URI {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "Only HTTP(S) video sources are supported." }
        require(uri.rawUserInfo == null && uri.rawFragment == null) {
            "Video source URL must not contain embedded credentials or fragments."
        }
        val host = uri.host?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Video source URL has no valid host.")
        require(!containsSensitiveQueryParameter(uri)) {
            "Tokenized or credential-bearing media URLs are not supported."
        }
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") ||
            allowLoopbackForTests && host == "localhost"
        ) { "Local network video sources are not supported." }

        val addresses = InetAddress.getAllByName(host.trim('[', ']'))
        require(addresses.isNotEmpty()) { "Video source host did not resolve." }
        addresses.forEach { address ->
            val testLoopback = allowLoopbackForTests && address.isLoopbackAddress
            require(testLoopback || (
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress
                )
            ) { "Video source resolves to a private or local network address." }
        }
        return uri
    }

    private fun containsSensitiveQueryParameter(uri: URI): Boolean {
        val blocked = setOf(
            "token", "access_token", "auth", "authorization", "key",
            "signature", "sig", "policy", "credential", "session", "cookie"
        )
        return uri.rawQuery.orEmpty().split('&').any { component ->
            component.substringBefore('=').lowercase(Locale.ROOT) in blocked
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 8_000
    private const val READ_TIMEOUT_MILLIS = 8_000
    private const val MAX_REDIRECTS = 5
    private const val CONTAINER_PREFIX_BYTES = 32
    private const val MIN_CONTAINER_BYTES = 8L
    private const val MAX_HEADER_LENGTH = 1_024
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private val CONTENT_RANGE = Regex("bytes 0-[0-9]+/(?:[0-9]+|\\*)", RegexOption.IGNORE_CASE)
}
