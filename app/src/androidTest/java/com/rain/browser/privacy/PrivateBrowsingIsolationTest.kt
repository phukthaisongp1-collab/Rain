package com.rain.browser.privacy

import android.app.Application
import android.os.SystemClock
import android.webkit.CookieManager
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rain.browser.MainActivity
import com.rain.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateBrowsingIsolationTest {
    @Test
    fun privateCookiesAreIsolatedAndClearedWithoutAffectingNormalCookies() {
        val normalScenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            normalScenario.onActivity {
                val cookies = CookieManager.getInstance()
                setCookie(cookies, NORMAL_COOKIE)
                assertTrue(cookies.getCookie(TEST_URL).orEmpty().contains("rain_normal=kept"))
            }

            val firstPrivateScenario = ActivityScenario.launch(PrivateBrowsingActivity::class.java)
            try {
                waitForPrivateWebView(firstPrivateScenario)
                firstPrivateScenario.onActivity { activity ->
                    assertEquals(
                        "${activity.packageName}:private",
                        Application.getProcessName()
                    )
                    val cookies = CookieManager.getInstance()
                    assertFalse(cookies.getCookie(TEST_URL).orEmpty().contains("rain_normal=kept"))

                    setCookie(cookies, PRIVATE_COOKIE)
                    assertTrue(cookies.getCookie(TEST_URL).orEmpty().contains("rain_private=temporary"))
                    activity.findViewById<Button>(R.id.private_close_button).performClick()
                }
                waitForDestroyed(firstPrivateScenario)
            } finally {
                firstPrivateScenario.close()
            }

            val secondPrivateScenario = ActivityScenario.launch(PrivateBrowsingActivity::class.java)
            try {
                waitForPrivateWebView(secondPrivateScenario)
                secondPrivateScenario.onActivity { activity ->
                    val privateCookies = CookieManager.getInstance().getCookie(TEST_URL).orEmpty()
                    assertFalse(privateCookies.contains("rain_private=temporary"))
                    assertFalse(privateCookies.contains("rain_normal=kept"))
                    activity.findViewById<Button>(R.id.private_close_button).performClick()
                }
                waitForDestroyed(secondPrivateScenario)
            } finally {
                secondPrivateScenario.close()
            }

            normalScenario.onActivity {
                val normalCookies = CookieManager.getInstance().getCookie(TEST_URL).orEmpty()
                assertTrue(normalCookies.contains("rain_normal=kept"))
                assertFalse(normalCookies.contains("rain_private=temporary"))
            }
        } finally {
            normalScenario.close()
        }
    }

    private fun waitForPrivateWebView(scenario: ActivityScenario<PrivateBrowsingActivity>) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val field = PrivateBrowsingActivity::class.java.getDeclaredField("privateWebView")
                field.isAccessible = true
                ready = field.get(activity) != null
            }
            if (ready) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Private WebView was not initialized before timeout")
    }

    private fun waitForDestroyed(scenario: ActivityScenario<*>) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS
        while (scenario.state != Lifecycle.State.DESTROYED &&
            SystemClock.uptimeMillis() < deadline
        ) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Suppress("DEPRECATION")
    private fun setCookie(cookieManager: CookieManager, value: String) {
        cookieManager.setCookie(TEST_URL, value)
        cookieManager.flush()
    }

    private companion object {
        const val TEST_URL = "https://rain-private-isolation.test/"
        const val NORMAL_COOKIE = "rain_normal=kept; Path=/; Secure"
        const val PRIVATE_COOKIE = "rain_private=temporary; Path=/; Secure"
        const val WAIT_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
