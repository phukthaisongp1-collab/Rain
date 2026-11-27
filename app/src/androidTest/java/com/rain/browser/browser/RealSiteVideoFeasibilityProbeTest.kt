package com.rain.browser.browser

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Read-only Day 8 feasibility probe for the three owner-authorized test pages.
 *
 * It does not click controls, start playback, download media, inspect protected responses,
 * or infer download permission. URL query strings and fragments are removed from all reports.
 */
@RunWith(AndroidJUnit4::class)
class RealSiteVideoFeasibilityProbeTest {
    @Test
    fun capturesReadOnlyVideoAndAdSignalsFromApprovedTestPages() {
        val reports = TEST_PAGES.map { pageUrl -> inspectPage(pageUrl) }

        reports.forEach { report ->
            Log.i(TAG, report.toString())
        }

        assertEqualsPageCount(TEST_PAGES.size, reports.size)
        assertTrue("At least one site navigation should complete or return a main-frame error",
            reports.all { it.has("navigation") })
    }

    private fun inspectPage(pageUrl: String): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CountDownLatch(1)
        val result = AtomicReference<JSONObject>()
        val webViewRef = AtomicReference<WebView>()

        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(SNAPSHOT_SCRIPT) { encoded ->
                        if (result.compareAndSet(null, decodeSnapshot(encoded, url))) {
                            completed.countDown()
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame && result.get() == null) {
                        val failure = JSONObject()
                            .put("requestedUrl", safeUrl(pageUrl))
                            .put("navigation", "error")
                            .put("errorCode", error.errorCode)
                            .put("description", error.description?.toString()?.take(160) ?: "")
                        if (result.compareAndSet(null, failure)) completed.countDown()
                    }
                }
            }
            webViewRef.set(webView)
            webView.loadUrl(pageUrl)
        }

        try {
            val finished = completed.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                return JSONObject()
                    .put("requestedUrl", safeUrl(pageUrl))
                    .put("navigation", "timeout")
            }
            return result.get() ?: JSONObject()
                .put("requestedUrl", safeUrl(pageUrl))
                .put("navigation", "empty-result")
        } finally {
            instrumentation.runOnMainSync {
                webViewRef.getAndSet(null)?.apply {
                    stopLoading()
                    webChromeClient = null
                    webViewClient = WebViewClient()
                    destroy()
                }
            }
        }
    }

    private fun decodeSnapshot(encoded: String, requestedUrl: String): JSONObject {
        return runCatching {
            val decoded = JSONTokener(encoded).nextValue() as? String
                ?: error("WebView returned a non-string result")
            JSONObject(decoded).put("requestedUrl", safeUrl(requestedUrl))
        }.getOrElse { error ->
            JSONObject()
                .put("requestedUrl", safeUrl(requestedUrl))
                .put("navigation", "snapshot-error")
                .put("error", error.message.orEmpty().take(160))
        }
    }

    private fun safeUrl(raw: String): String {
        return runCatching {
            android.net.Uri.parse(raw).buildUpon()
                .clearQuery()
                .fragment(null)
                .build()
                .toString()
        }.getOrDefault("")
    }

    private fun assertEqualsPageCount(expected: Int, actual: Int) {
        assertTrue("Expected $expected site reports, got $actual", expected == actual)
    }

    private companion object {
        const val TAG = "RAIN_DAY8_SITE_PROBE"
        const val PAGE_TIMEOUT_SECONDS = 45L

        val TEST_PAGES = listOf(
            "https://www.037-hdd.com/",
            "https://fwip-tv.com/visionquest/",
            "https://www.nunghd4k.com/"
        )

        val SNAPSHOT_SCRIPT = """
            (function () {
                function safeUrl(raw) {
                    try {
                        var value = new URL(raw, location.href);
                        value.search = '';
                        value.hash = '';
                        return value.href;
                    } catch (_) {
                        return '';
                    }
                }
                function visible(node) {
                    var style = getComputedStyle(node);
                    var rect = node.getBoundingClientRect();
                    return rect.width > 0 && rect.height > 0 &&
                        style.display !== 'none' && style.visibility !== 'hidden';
                }
                var videos = Array.from(document.querySelectorAll('video')).map(function (video) {
                    var rawSource = video.currentSrc || video.getAttribute('src') || '';
                    var childSources = Array.from(video.querySelectorAll('source')).map(function (source) {
                        return {
                            src: safeUrl(source.getAttribute('src') || ''),
                            type: (source.getAttribute('type') || '').slice(0, 80)
                        };
                    });
                    return {
                        src: safeUrl(rawSource),
                        sourceCount: childSources.length,
                        sources: childSources,
                        poster: safeUrl(video.getAttribute('poster') || ''),
                        readyState: video.readyState,
                        networkState: video.networkState,
                        durationAvailable: Number.isFinite(video.duration),
                        visible: visible(video),
                        blobSource: rawSource.indexOf('blob:') === 0,
                        hasMediaStreamObject: !!video.srcObject
                    };
                });
                var adMarkers = Array.from(document.querySelectorAll(
                    '[aria-label], [role="status"], [data-ad], button'
                )).filter(visible).map(function (node) {
                    var label = (node.getAttribute('aria-label') || '').trim();
                    var text = (node.innerText || node.textContent || '').trim();
                    var combined = (label + ' ' + text).slice(0, 180);
                    if (!/\b(advertisement|sponsored|skip ad|skip advertisement)\b/i.test(combined)) {
                        return null;
                    }
                    return {
                        label: label.slice(0, 80),
                        text: text.slice(0, 100),
                        role: (node.getAttribute('role') || '').slice(0, 40)
                    };
                }).filter(Boolean).slice(0, 30);
                var iframes = Array.from(document.querySelectorAll('iframe')).map(function (frame) {
                    return {
                        src: safeUrl(frame.getAttribute('src') || ''),
                        title: (frame.getAttribute('title') || '').slice(0, 100)
                    };
                }).slice(0, 30);
                var mediaResourceCount = performance.getEntriesByType('resource').filter(function (entry) {
                    return /\.(mp4|webm|m3u8|mpd)(?:$|[?#])/i.test(entry.name);
                }).length;
                return JSON.stringify({
                    navigation: 'complete',
                    finalUrl: safeUrl(location.href),
                    title: (document.title || '').slice(0, 160),
                    videoCount: videos.length,
                    videos: videos,
                    adMarkerCount: adMarkers.length,
                    adMarkers: adMarkers,
                    iframeCount: iframes.length,
                    iframes: iframes,
                    mediaResourceCount: mediaResourceCount,
                    mediaSourceApiAvailable: typeof MediaSource !== 'undefined'
                });
            })();
        """.trimIndent()
    }
}
