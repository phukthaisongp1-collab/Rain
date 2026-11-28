package com.rain.browser.downloads

import java.net.URI
import java.util.Locale

sealed interface HlsParsedPlaylist {
    data class Master(val variants: List<HlsVariant>) : HlsParsedPlaylist
    data class Media(
        val mediaSequence: Long,
        val segments: List<HlsSegment>,
        val endList: Boolean
    ) : HlsParsedPlaylist
}

data class HlsVariant(
    val url: String,
    val bandwidth: Long,
    val averageBandwidth: Long?,
    val width: Int?,
    val height: Int?,
    val codecs: String
) {
    val label: String
        get() = if (width != null && height != null) {
            "${width}×${height} · ${bandwidth / 1_000} kbps"
        } else {
            "${bandwidth / 1_000} kbps"
        }
}

data class HlsEncryptionKey(
    val method: String,
    val url: String?,
    val iv: ByteArray?
)

data class HlsInitializationMap(
    val url: String,
    val key: HlsEncryptionKey?
)

data class HlsSegment(
    val url: String,
    val durationSeconds: Double,
    val sequence: Long,
    val key: HlsEncryptionKey?,
    val initializationMap: HlsInitializationMap?
)

/**
 * Bounded HLS playlist parser. Network access and destination-IP validation are
 * deliberately handled by the HLS downloader, not by this parser.
 */
object HlsPlaylistParser {
    const val MAX_SEGMENTS = 20_000

    fun parse(baseUrl: String, content: String): HlsParsedPlaylist {
        val normalized = content.removePrefix("\uFEFF")
        require(normalized.startsWith("#EXTM3U")) {
            "URL นี้ไม่ใช่ HLS Playlist ที่ถูกต้อง"
        }
        val lines = normalized.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull() == "#EXTM3U") {
            "รูปแบบ HLS Playlist ไม่ถูกต้อง"
        }

        if (lines.any { it.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) }) {
            return parseMaster(baseUrl, lines)
        }
        return parseMedia(baseUrl, lines)
    }

    private fun parseMaster(baseUrl: String, lines: List<String>): HlsParsedPlaylist.Master {
        val variants = mutableListOf<HlsVariant>()
        var index = 1
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.startsWith("#EXT-X-I-FRAME-STREAM-INF:", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS I-frame playlist")

                line.startsWith("#EXT-X-SESSION-KEY:", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS Session Key หรือ DRM")

                line.startsWith("#EXT-X-CONTENT-STEERING:", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS Content Steering")

                line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    if (attributes["TYPE"].equals("AUDIO", true)) {
                        throw IllegalArgumentException("ไม่รองรับ HLS audio rendition แยก")
                    }
                }

                line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    val bandwidth = attributes["BANDWIDTH"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("HLS variant ไม่มี BANDWIDTH ที่ถูกต้อง")
                    require(bandwidth > 0L) { "HLS variant มี BANDWIDTH ไม่ถูกต้อง" }

                    var uriIndex = index + 1
                    while (uriIndex < lines.size && lines[uriIndex].isBlank()) uriIndex++
                    require(uriIndex < lines.size && !lines[uriIndex].startsWith("#")) {
                        "HLS variant ไม่มี URI ปลายทาง"
                    }
                    val resolution = attributes["RESOLUTION"]?.let(::parseResolution)
                    variants += HlsVariant(
                        url = resolveHttpUrl(baseUrl, lines[uriIndex]),
                        bandwidth = bandwidth,
                        averageBandwidth = attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
                        width = resolution?.first,
                        height = resolution?.second,
                        codecs = attributes["CODECS"].orEmpty()
                    )
                    index = uriIndex
                }
            }
            index++
        }
        require(variants.isNotEmpty()) { "HLS Master Playlist ไม่มี variant ที่รองรับ" }
        return HlsParsedPlaylist.Master(variants)
    }

    private fun parseMedia(baseUrl: String, lines: List<String>): HlsParsedPlaylist.Media {
        val segments = mutableListOf<HlsSegment>()
        var mediaSequence = 0L
        var currentSequence = 0L
        var pendingDuration: Double? = null
        var currentKey: HlsEncryptionKey? = null
        var currentMap: HlsInitializationMap? = null
        var endList = false

        lines.drop(1).forEach { line ->
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) -> {
                    mediaSequence = line.substringAfter(':').toLongOrNull()
                        ?: throw IllegalArgumentException("HLS media sequence ไม่ถูกต้อง")
                    require(mediaSequence >= 0L) { "HLS media sequence ติดลบไม่ได้" }
                    currentSequence = mediaSequence
                }

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    require(pendingDuration == null) { "HLS Playlist มี EXTINF ซ้อนกัน" }
                    val value = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull()
                        ?: throw IllegalArgumentException("HLS segment duration ไม่ถูกต้อง")
                    require(value > 0.0 && value.isFinite()) {
                        "HLS segment duration ต้องมากกว่าศูนย์"
                    }
                    pendingDuration = value
                }

                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    currentKey = parseKey(baseUrl, line.substringAfter(':'))
                }

                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    require(attributes["BYTERANGE"].isNullOrBlank()) {
                        "ไม่รองรับ HLS EXT-X-MAP byte range"
                    }
                    val reference = attributes["URI"]
                        ?: throw IllegalArgumentException("HLS EXT-X-MAP ไม่มี URI")
                    currentMap = HlsInitializationMap(
                        url = resolveHttpUrl(baseUrl, reference),
                        key = currentKey
                    )
                }

                line.equals("#EXT-X-ENDLIST", ignoreCase = true) -> endList = true

                line.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS byte-range segment")

                line.startsWith("#EXT-X-PART", ignoreCase = true) ||
                    line.startsWith("#EXT-X-PRELOAD-HINT", ignoreCase = true) ||
                    line.startsWith("#EXT-X-SKIP", ignoreCase = true) ||
                    line.startsWith("#EXT-X-RENDITION-REPORT", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS Low-Latency หรือ delta playlist")

                line.startsWith("#EXT-X-GAP", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS segment gap")

                line.startsWith("#EXT-X-DEFINE", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS variable substitution")

                line.startsWith("#EXT-X-SESSION-KEY", ignoreCase = true) ->
                    throw IllegalArgumentException("ไม่รองรับ HLS Session Key หรือ DRM")

                line.startsWith("#") -> Unit

                else -> {
                    val duration = pendingDuration
                        ?: throw IllegalArgumentException("HLS segment URI ไม่มี EXTINF กำกับ")
                    require(segments.size < MAX_SEGMENTS) {
                        "HLS Playlist มีจำนวน segment เกินขีดจำกัด 20,000"
                    }
                    segments += HlsSegment(
                        url = resolveHttpUrl(baseUrl, line),
                        durationSeconds = duration,
                        sequence = currentSequence,
                        key = currentKey,
                        initializationMap = currentMap
                    )
                    currentSequence++
                    pendingDuration = null
                }
            }
        }

        require(pendingDuration == null) { "HLS Playlist มี EXTINF ที่ไม่มี segment URI" }
        require(endList) { "ไม่รองรับ HLS Live Playlist ที่ไม่มี EXT-X-ENDLIST" }
        require(segments.isNotEmpty()) { "HLS Media Playlist ไม่มี segment" }
        return HlsParsedPlaylist.Media(mediaSequence, segments, endList)
    }

    private fun parseKey(baseUrl: String, value: String): HlsEncryptionKey? {
        val attributes = parseAttributes(value)
        val method = attributes["METHOD"]?.uppercase(Locale.ROOT)
            ?: throw IllegalArgumentException("HLS encryption key ไม่มี METHOD")
        if (method == "NONE") return null
        if (method.startsWith("SAMPLE-AES")) {
            throw IllegalArgumentException("ไม่รองรับ HLS SAMPLE-AES หรือ DRM")
        }
        require(method == "AES-128") {
            "ไม่รองรับ HLS encryption method: $method"
        }
        val keyFormat = attributes["KEYFORMAT"] ?: "identity"
        require(keyFormat.equals("identity", true)) {
            "ไม่รองรับ HLS DRM หรือ KEYFORMAT: $keyFormat"
        }
        val keyUri = attributes["URI"]
            ?: throw IllegalArgumentException("HLS AES-128 key ไม่มี URI")
        val iv = attributes["IV"]?.let(::parseIv)
        return HlsEncryptionKey(
            method = method,
            url = resolveHttpUrl(baseUrl, keyUri),
            iv = iv
        )
    }

    private fun parseAttributes(value: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var index = 0
        while (index < value.length) {
            while (index < value.length && (value[index] == ',' || value[index].isWhitespace())) index++
            if (index >= value.length) break
            val equalsIndex = value.indexOf('=', index)
            require(equalsIndex > index) { "HLS attribute list ไม่ถูกต้อง" }
            val name = value.substring(index, equalsIndex).trim().uppercase(Locale.ROOT)
            index = equalsIndex + 1
            val attributeValue = if (index < value.length && value[index] == '"') {
                index++
                val endQuote = value.indexOf('"', index)
                require(endQuote >= 0) { "HLS attribute มีเครื่องหมาย quote ไม่ครบ" }
                value.substring(index, endQuote).also { index = endQuote + 1 }
            } else {
                val end = value.indexOf(',', index).let { if (it < 0) value.length else it }
                value.substring(index, end).trim().also { index = end }
            }
            require(name.isNotBlank() && name !in result) { "HLS attribute ซ้ำหรือว่าง" }
            result[name] = attributeValue
            while (index < value.length && value[index] != ',') {
                require(value[index].isWhitespace()) { "HLS attribute delimiter ไม่ถูกต้อง" }
                index++
            }
        }
        return result
    }

    private fun parseResolution(value: String): Pair<Int, Int>? {
        val match = RESOLUTION.matchEntire(value) ?: return null
        val width = match.groupValues[1].toIntOrNull() ?: return null
        val height = match.groupValues[2].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        return width to height
    }

    private fun parseIv(value: String): ByteArray {
        require(IV_PATTERN.matches(value)) { "HLS AES-128 IV ต้องเป็นเลขฐานสิบหก 128 บิต" }
        return value.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun resolveHttpUrl(baseUrl: String, reference: String): String =
        HlsPlaylistUrlPolicy.resolveHttpUrl(baseUrl, reference)

    private val RESOLUTION = Regex("([1-9][0-9]*)x([1-9][0-9]*)", RegexOption.IGNORE_CASE)
    private val IV_PATTERN = Regex("0x[0-9a-fA-F]{32}", RegexOption.IGNORE_CASE)
}
