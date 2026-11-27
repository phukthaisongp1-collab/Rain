package com.rain.browser.browser

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Feasibility probe only: verifies which HTML5 video metadata the Android WebView DOM exposes.
 * It does not decide whether a resource is downloadable or permitted by its source website.
 */
@RunWith(AndroidJUnit4::class)
class Html5VideoDetectionProbeTest {
    @Test
    fun readsSourceAndPlayerMetadataForHtml5VideoElements() {
        val signals = loadSignals(
            """
            <!doctype html>
            <html>
              <head><title>RAIN video probe fixture</title></head>
              <body>
                <video id="primary" controls width="640" height="360"
                    poster="https://media.example.invalid/poster.jpg"
                    src="https://media.example.invalid/primary.mp4"></video>
                <video id="secondary" controls
                    src="https://media.example.invalid/secondary.webm"></video>
              </body>
            </html>
            """.trimIndent()
        )

        assertEquals(2, signals.length())
        val primary = signals.getJSONObject(0)
        assertEquals("https://media.example.invalid/primary.mp4", primary.getString("src"))
        assertEquals("https://media.example.invalid/poster.jpg", primary.getString("poster"))
        assertEquals("640", primary.getString("width"))
        assertEquals("360", primary.getString("height"))
        assertTrue(primary.has("paused"))
        assertTrue(primary.has("readyState"))
        assertTrue(primary.has("networkState"))

        val secondary = signals.getJSONObject(1)
        assertEquals("https://media.example.invalid/secondary.webm", secondary.getString("src"))
        assertEquals("", secondary.getString("poster"))
    }

    @Test
    fun doesNotInventASourceWhenVideoHasNoExposedSource() {
        val signals = loadSignals(
            """
            <!doctype html>
            <html><body><video id="opaque" controls></video></body></html>
            """.trimIndent()
        )

        assertEquals(1, signals.length())
        assertEquals("", signals.getJSONObject(0).getString("src"))
        assertFalse(signals.getJSONObject(0).getBoolean("hasExposedSource"))
    }



    @Test
    fun collectsExplicitAdUiMarkersWhenThePlayerExposesThem() {
        val signals = loadSignals(
            """
            <!doctype html>
            <html>
              <body>
                <div id="player">
                  <div role="status" aria-label="Advertisement">Advertisement</div>
                  <button type="button" aria-label="Skip Ad">Skip Ad</button>
                  <video controls src="https://media.example.invalid/ad.mp4"></video>
                </div>
              </body>
            </html>
            """.trimIndent()
        )

        val indicators = signals.getJSONObject(0).getJSONArray("adIndicators")
        val labels = (0 until indicators.length()).map {
            indicators.getJSONObject(it).getString("label")
        }
        assertTrue(labels.contains("Advertisement"))
        assertTrue(labels.contains("Skip Ad"))
        assertFalse(signals.getJSONObject(0).has("isAd"))
    }

    @Test
    fun webViewClientObservesOneDirectMp4ResourceRequest() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requestObserved = CountDownLatch(1)
        val pageFinished = CountDownLatch(1)
        val webViewRef = AtomicReference<WebView>()
        val html = """
            <!doctype html>
            <html>
              <body>
                <video autoplay muted playsinline preload="auto"
                    src="$SAMPLE_MP4_URL"></video>
              </body>
            </html>
        """.trimIndent()

        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    if (request.url.toString() == SAMPLE_MP4_URL) {
                        requestObserved.countDown()
                    }
                    return null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(
                        "(function () { var video = document.querySelector('video'); " +
                            "if (video) { video.muted = true; video.play().catch(function () {}); } })();",
                        null
                    )
                    pageFinished.countDown()
                }
            }
            webViewRef.set(webView)
            webView.loadDataWithBaseURL(
                "https://fixture.example.invalid/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }

        try {
            assertTrue("WebView fixture did not finish loading", pageFinished.await(10, TimeUnit.SECONDS))
            assertTrue(
                "WebViewClient did not observe the direct MP4 request",
                requestObserved.await(20, TimeUnit.SECONDS)
            )
        } finally {
            instrumentation.runOnMainSync {
                webViewRef.getAndSet(null)?.apply {
                    stopLoading()
                    webChromeClient = null
                    destroy()
                }
            }
        }
    }

    private fun loadSignals(html: String): JSONArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        val webViewRef = AtomicReference<WebView>()

        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(VIDEO_SIGNAL_SCRIPT) { value ->
                        if (result.compareAndSet(null, value)) completed.countDown()
                    }
                }
            }
            webViewRef.set(webView)
            webView.loadDataWithBaseURL(
                "https://fixture.example.invalid/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }

        try {
            assertTrue("WebView fixture did not finish loading", completed.await(10, TimeUnit.SECONDS))
            val encoded = result.get() ?: error("WebView returned no video signal payload")
            val decoded = JSONTokener(encoded).nextValue() as? String
                ?: error("WebView returned an invalid JSON string")
            return JSONArray(decoded)
        } finally {
            instrumentation.runOnMainSync {
                webViewRef.getAndSet(null)?.apply {
                    stopLoading()
                    webChromeClient = null
                    destroy()
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_MP4_URL = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
        val VIDEO_SIGNAL_SCRIPT = """
            (function () {
                return JSON.stringify(Array.from(document.querySelectorAll('video')).map(function (video) {
                    var rect = video.getBoundingClientRect();
                    var style = window.getComputedStyle(video);
                    var source = video.currentSrc || video.src || '';
                    var adIndicators = [];
                    var ancestor = video;
                    for (var depth = 0; ancestor && depth < 4; depth++, ancestor = ancestor.parentElement) {
                        ancestor.querySelectorAll('[aria-label], button, [role="status"], [data-ad]').forEach(function (node) {
                            var label = (node.getAttribute('aria-label') || '').trim();
                            var text = (node.innerText || node.textContent || '').trim();
                            if (/\b(advertisement|sponsored|skip ad|skip advertisement)\b/i.test(label + ' ' + text)) {
                                adIndicators.push({
                                    label: label,
                                    text: text.slice(0, 80),
                                    role: node.getAttribute('role') || ''
                                });
                            }
                        });
                    }
                    return {
                        src: source,
                        hasExposedSource: source.length > 0,
                        poster: video.poster || '',
                        width: String(video.videoWidth || video.getAttribute('width') || ''),
                        height: String(video.videoHeight || video.getAttribute('height') || ''),
                        duration: Number.isFinite(video.duration) ? video.duration : null,
                        paused: video.paused,
                        readyState: video.readyState,
                        networkState: video.networkState,
                        visible: !!(rect.width && rect.height) &&
                            style.display !== 'none' && style.visibility !== 'hidden',
                        adIndicators: adIndicators
                    };
                }));
            })();
        """.trimIndent()
    }
}
