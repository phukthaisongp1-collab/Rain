package com.rain.browser.downloads

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Credential-free HLS transport. DNS answers are screened before connect and
 * the actual connected socket address is screened again before each request.
 */
object HlsHttpClient {
    data class FetchedResponse(
        val finalUrl: String,
        val statusCode: Int,
        val contentType: String,
        val body: ByteArray
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns { hostname ->
                Dns.SYSTEM.lookup(hostname).also { addresses ->
                    require(addresses.isNotEmpty()) {
                        "ไม่สามารถค้นหา IP ของ HLS Host ได้"
                    }
                    addresses.forEach(::requirePublicAddress)
                }
            })
            .addNetworkInterceptor { chain ->
                val connection = chain.connection()
                    ?: throw IllegalStateException("ไม่พบการเชื่อมต่อเครือข่าย HLS")
                val connectedAddress = connection.socket().inetAddress
                    ?: throw IllegalStateException("ไม่สามารถตรวจสอบ IP ปลายทาง HLS ได้")
                requirePublicAddress(connectedAddress)
                chain.proceed(chain.request())
            }
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun get(
        rawUrl: String,
        maximumBytes: Int,
        accept: String = "*/*"
    ): FetchedResponse {
        require(maximumBytes > 0)
        var currentUrl = HlsPlaylistUrlPolicy.validateHttpUrl(rawUrl)

        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = Request.Builder()
                .url(currentUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Encoding", "identity")
                .header("Connection", "close")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw IllegalArgumentException("เว็บนี้จำกัดการดาวน์โหลด ไม่รองรับ")
                }

                if (response.code in REDIRECT_CODES) {
                    require(redirectCount < MAX_REDIRECTS) {
                        "HLS URL เปลี่ยนเส้นทางเกิน 5 ครั้ง"
                    }
                    val location = response.header("Location")
                        ?: throw IllegalArgumentException("HLS Redirect ไม่มี URL ปลายทาง")
                    val redirected = URI(currentUrl).resolve(URI(location)).toString()
                    val nextUrl = HlsPlaylistUrlPolicy.validateHttpUrl(redirected)
                    require(
                        !currentUrl.startsWith("https://", ignoreCase = true) ||
                            nextUrl.startsWith("https://", ignoreCase = true)
                    ) { "ไม่อนุญาตให้ HLS Redirect ลดระดับจาก HTTPS เป็น HTTP" }
                    currentUrl = nextUrl
                    return@use null
                }

                require(response.code == 200) {
                    "ไม่สามารถดึง HLS resource ได้ (HTTP ${response.code})"
                }
                val body = response.body
                    ?: throw IllegalArgumentException("HLS response ไม่มีข้อมูล")
                val declaredLength = body.contentLength()
                require(declaredLength < 0L || declaredLength <= maximumBytes.toLong()) {
                    "HLS resource มีขนาดเกินขีดจำกัดที่รองรับ"
                }
                val bytes = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(count <= maximumBytes - total) {
                            "HLS resource มีขนาดเกินขีดจำกัดที่รองรับ"
                        }
                        total += count
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                return FetchedResponse(
                    finalUrl = currentUrl,
                    statusCode = response.code,
                    contentType = body.contentType()?.toString().orEmpty(),
                    body = bytes
                )
            }
        }
        throw IllegalArgumentException("ไม่สามารถตรวจสอบ HLS Redirect ได้")
    }

    private fun requirePublicAddress(address: InetAddress) {
        require(isPublicAddress(address)) {
            "HLS Host เชื่อมต่อไปยังเครือข่ายภายในหรือ IP ที่ไม่ปลอดภัย"
        }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false

        val bytes = address.address
        if (address is Inet4Address || bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            val c = bytes[2].toInt() and 0xff
            return when {
                a == 0 || a == 10 || a == 127 || a >= 224 -> false
                a == 100 && b in 64..127 -> false
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && b == 168 -> false
                a == 192 && b == 0 && (c == 0 || c == 2) -> false
                a == 192 && b == 88 && c == 99 -> false
                a == 198 && b in 18..19 -> false
                a == 198 && b == 51 && c == 100 -> false
                a == 203 && b == 0 && c == 113 -> false
                else -> true
            }
        }

        if (address !is Inet6Address || bytes.size != 16) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff
        val fourth = bytes[3].toInt() and 0xff
        val isGlobalUnicast = first and 0xe0 == 0x20
        val isDocumentation = first == 0x20 && second == 0x01 &&
            third == 0x0d && fourth == 0xb8
        val isSpecial2001 = first == 0x20 && second == 0x01 &&
            (third == 0x00 || third == 0x01)
        val isSixToFour = first == 0x20 && second == 0x02
        return isGlobalUnicast && !isDocumentation && !isSpecial2001 && !isSixToFour
    }

    private const val CONNECT_TIMEOUT_SECONDS = 8L
    private const val READ_TIMEOUT_SECONDS = 15L
    private const val CALL_TIMEOUT_SECONDS = 30L
    private const val MAX_REDIRECTS = 5
    private const val USER_AGENT = "RAIN Browser HLS"
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
