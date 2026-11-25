package com.rain.browser.privacy

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rain.browser.MainActivity
import com.rain.browser.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PrivateBrowsingIsolationTest {
    @Test
    fun privateCookiesAndLocalStorageAreIsolatedAndClearedAfterReopen() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var privateActivityStarted = false

        try {
            val normalProcessName = AtomicReference<String>()
            val normalProcessId = AtomicInteger(-1)
            scenario.onActivity {
                normalProcessName.set(Application.getProcessName())
                normalProcessId.set(android.os.Process.myPid())
            }

            assertEquals(
                "Normal browsing must run in the default application process",
                targetContext.packageName,
                normalProcessName.get()
            )

            val normalBefore = loadNormalPage(scenario, writeTestData = true)
            assertEquals("normal-kept", normalBefore.optString("storage"))
            assertTrue(normalBefore.optString("cookie").contains("rain_normal=kept"))

            launchPrivateActivity()
            privateActivityStarted = true

            val privateStatus = awaitPrivateWebView()
            val privateProcessName = privateStatus.getString("process_name")
            val privateProcessId = privateStatus.getInt("pid")
            assertEquals("${targetContext.packageName}:private", privateProcessName)
            assertTrue("Private browsing must run in a distinct process", privateProcessId > 0 && privateProcessId != normalProcessId.get())

            Log.i(
                TEST_TAG,
                "PROCESS_LIFECYCLE normal=${normalProcessName.get()} pid=${normalProcessId.get()} " +
                    "private=$privateProcessName pid=$privateProcessId " +
                    "SERVICE_WORKER_CLEANUP_FEATURE_SUPPORTED=${privateStatus.getBoolean("service_worker_cleanup_supported")}"
            )

            val initialPrivate = loadPrivatePage(writeTestData = false)
            assertTrue("Private localStorage must start empty", initialPrivate.isNull("storage"))
            assertFalse(
                "Normal cookies must not be visible in the private process",
                initialPrivate.optString("cookie").contains("rain_normal=kept")
            )

            val writtenPrivate = loadPrivatePage(writeTestData = true)
            assertEquals("private-temporary", writtenPrivate.optString("storage"))
            assertTrue(writtenPrivate.optString("cookie").contains("rain_private=temporary"))

            val closeResult = sendPrivateCommand(
                PrivateBrowsingActivity.OP_CLOSE,
                timeoutMs = WAIT_TIMEOUT_MS
            )
            assertNotNull("Private session cleanup did not complete", closeResult)
            assertTrue(closeResult!!.getBoolean("closed"))
            assertFalse(
                "Private cookies must be removed when the session closes",
                closeResult.getString("remaining_cookie").orEmpty().contains("rain_private=temporary")
            )
            privateActivityStarted = false

            launchPrivateActivity()
            privateActivityStarted = true
            val reopenedStatus = awaitPrivateWebView()
            assertEquals("${targetContext.packageName}:private", reopenedStatus.getString("process_name"))

            val reopenedPrivate = loadPrivatePage(writeTestData = false)
            assertTrue("Private localStorage must remain empty after reopening", reopenedPrivate.isNull("storage"))
            assertFalse(
                "Private cookies must remain cleared after reopening",
                reopenedPrivate.optString("cookie").contains("rain_private=temporary")
            )

            val secondClose = sendPrivateCommand(
                PrivateBrowsingActivity.OP_CLOSE,
                timeoutMs = WAIT_TIMEOUT_MS
            )
            assertNotNull("Reopened private session cleanup did not complete", secondClose)
            assertTrue(secondClose!!.getBoolean("closed"))
            privateActivityStarted = false

            val normalAfter = loadNormalPage(scenario, writeTestData = false)
            assertEquals("Normal localStorage must survive private cleanup", "normal-kept", normalAfter.optString("storage"))
            assertTrue(
                "Normal cookies must survive private cleanup",
                normalAfter.optString("cookie").contains("rain_normal=kept")
            )
            assertFalse(
                "Private cookies must not leak into normal browsing",
                normalAfter.optString("cookie").contains("rain_private=temporary")
            )
        } finally {
            if (privateActivityStarted) {
                runCatching {
                    sendPrivateCommand(
                        PrivateBrowsingActivity.OP_CLOSE,
                        timeoutMs = CLEANUP_TIMEOUT_MS
                    )
                }
            }
            scenario.close()
        }
    }

    private fun launchPrivateActivity() {
        val intent = Intent(targetContext, PrivateBrowsingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(intent)
    }

    private fun awaitPrivateWebView(): Bundle {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val status = sendPrivateCommand(
                PrivateBrowsingActivity.OP_STATUS,
                timeoutMs = COMMAND_TIMEOUT_MS
            )
            if (status?.getBoolean("ready") == true) return status
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Private WebView was not initialized before timeout")
    }

    private fun loadPrivatePage(writeTestData: Boolean): JSONObject {
        val result = sendPrivateCommand(
            PrivateBrowsingActivity.OP_LOAD_TEST_PAGE,
            timeoutMs = WAIT_TIMEOUT_MS,
            writeTestData = writeTestData
        )
        assertNotNull("Private WebView did not finish loading the isolation test page", result)
        return JSONObject(result!!.getString("page_data").orEmpty())
    }

    private fun loadNormalPage(
        scenario: ActivityScenario<MainActivity>,
        writeTestData: Boolean
    ): JSONObject {
        val pageLoaded = CountDownLatch(1)
        val evaluationFinished = CountDownLatch(1)
        val pageData = AtomicReference<String?>()

        scenario.onActivity { activity ->
            val container = activity.findViewById<FrameLayout>(R.id.webview_container)
            val webView = container.getChildAt(0) as? WebView
                ?: throw AssertionError("MainActivity does not contain its normal WebView")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.startsWith(TEST_URL)) pageLoaded.countDown()
                }
            }

            val html = if (writeTestData) NORMAL_TEST_PAGE_WITH_DATA else TEST_PAGE_READ_ONLY
            webView.loadDataWithBaseURL(TEST_URL, html, "text/html", "UTF-8", null)
        }

        assertTrue(
            "Normal WebView did not finish loading the isolation test page",
            pageLoaded.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )

        scenario.onActivity { activity ->
            val container = activity.findViewById<FrameLayout>(R.id.webview_container)
            val webView = container.getChildAt(0) as WebView
            webView.evaluateJavascript(TEST_PAGE_DATA_SCRIPT) {
                pageData.set(it)
                evaluationFinished.countDown()
            }
        }

        assertTrue(
            "Normal WebView JavaScript evaluation timed out",
            evaluationFinished.await(COOKIE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
        return JSONObject(pageData.get().orEmpty())
    }

    private fun sendPrivateCommand(
        operation: String,
        cookie: String? = null,
        timeoutMs: Long = COMMAND_TIMEOUT_MS,
        writeTestData: Boolean? = null
    ): Bundle? {
        val latch = CountDownLatch(1)
        val response = AtomicReference<Bundle?>()
        val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                response.set(resultData)
                latch.countDown()
            }
        }

        val intent = Intent(PrivateBrowsingActivity.ACTION_PRIVATE_TEST)
            .setPackage(targetContext.packageName)
            .putExtra(PrivateBrowsingActivity.EXTRA_OPERATION, operation)
            .putExtra(PrivateBrowsingActivity.EXTRA_RESULT_RECEIVER, resultReceiver)
        if (cookie != null) {
            intent.putExtra(PrivateBrowsingActivity.EXTRA_COOKIE, cookie)
        }
        if (writeTestData != null) {
            intent.putExtra(PrivateBrowsingActivity.EXTRA_WRITE_TEST_DATA, writeTestData)
        }
        targetContext.sendBroadcast(intent)

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        return response.get()
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val TEST_TAG = "RAIN-PrivateTest"
        const val TEST_URL = "https://rain-private-isolation.test/"
        const val WAIT_TIMEOUT_MS = 30_000L
        const val CLEANUP_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 1_000L
        const val COOKIE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 100L

        const val TEST_PAGE_DATA_SCRIPT =
            "({storage: localStorage.getItem('rain_isolation'), cookie: document.cookie})"

        const val TEST_PAGE_READ_ONLY = """
            <!doctype html>
            <html><head><meta charset="utf-8"></head><body><p>RAIN isolation test page</p></body></html>
        """

        const val NORMAL_TEST_PAGE_WITH_DATA = """
            <!doctype html>
            <html><head><meta charset="utf-8"></head><body>
            <script>
                localStorage.setItem('rain_isolation', 'normal-kept');
                document.cookie = 'rain_normal=kept; Path=/; Secure; SameSite=Lax';
            </script>
            </body></html>
        """
    }
}
