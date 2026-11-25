package com.rain.browser.privacy

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PrivateBrowsingIsolationTest {
    @Test
    fun privateCookiesAreIsolatedAndClearedWithoutAffectingNormalCookies() {
        assertEquals(
            "Normal cookie setup must run in the application's default process",
            targetContext.packageName,
            Application.getProcessName()
        )

        // Seed the normal process cookie store without launching MainActivity.
        // Creating MainActivity also creates a WebView, which is not required
        // to verify CookieManager's process-level storage isolation.
        val normalCookies = CookieManager.getInstance()
        setCookie(normalCookies, NORMAL_COOKIE)
        assertTrue(normalCookies.getCookie(TEST_URL).orEmpty().contains("rain_normal=kept"))

        var privateActivityStarted = false
        try {
            launchPrivateActivity()
            privateActivityStarted = true

            val privateStatus = awaitPrivateWebView()
            assertEquals(
                "${targetContext.packageName}:private",
                privateStatus.getString("process_name")
            )

            val initialPrivateCookies = sendPrivateCommand(PrivateBrowsingActivity.OP_GET_COOKIE)
                ?.getString("cookie").orEmpty()
            assertFalse(initialPrivateCookies.contains("rain_normal=kept"))

            val setCookieResult = sendPrivateCommand(
                PrivateBrowsingActivity.OP_SET_COOKIE,
                PRIVATE_COOKIE,
                WAIT_TIMEOUT_MS
            )
            assertNotNull("Private process did not acknowledge cookie creation", setCookieResult)
            assertTrue(
                setCookieResult!!.getString("cookie").orEmpty().contains("rain_private=temporary")
            )

            val closeResult = sendPrivateCommand(
                PrivateBrowsingActivity.OP_CLOSE,
                timeoutMs = WAIT_TIMEOUT_MS
            )
            assertNotNull("Private session cleanup did not complete", closeResult)
            assertTrue(closeResult!!.getBoolean("closed"))
            assertFalse(
                closeResult.getString("remaining_cookie").orEmpty()
                    .contains("rain_private=temporary")
            )
            privateActivityStarted = false

            val remainingNormalCookies = normalCookies.getCookie(TEST_URL).orEmpty()
            assertTrue(remainingNormalCookies.contains("rain_normal=kept"))
            assertFalse(remainingNormalCookies.contains("rain_private=temporary"))
        } finally {
            if (privateActivityStarted) {
                runCatching {
                    sendPrivateCommand(
                        PrivateBrowsingActivity.OP_CLOSE,
                        timeoutMs = CLEANUP_TIMEOUT_MS
                    )
                }
            }
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

    private fun sendPrivateCommand(
        operation: String,
        cookie: String? = null,
        timeoutMs: Long = COMMAND_TIMEOUT_MS
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
        targetContext.sendBroadcast(intent)

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        return response.get()
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setCookie(cookieManager: CookieManager, value: String) {
        val latch = CountDownLatch(1)
        val accepted = AtomicReference<Boolean>()
        cookieManager.setCookie(TEST_URL, value) {
            accepted.set(it)
            latch.countDown()
        }
        assertTrue("Normal cookie write timed out", latch.await(COOKIE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertEquals("Normal cookie was rejected", true, accepted.get())
        cookieManager.flush()
    }

    private companion object {
        const val TEST_URL = "https://rain-private-isolation.test/"
        const val NORMAL_COOKIE = "rain_normal=kept; Path=/; Secure"
        const val PRIVATE_COOKIE = "rain_private=temporary; Path=/; Secure"
        const val WAIT_TIMEOUT_MS = 30_000L
        const val CLEANUP_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 1_000L
        const val COOKIE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
