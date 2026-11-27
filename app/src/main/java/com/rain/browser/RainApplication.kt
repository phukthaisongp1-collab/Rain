package com.rain.browser

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.rain.browser.downloads.VideoRetentionScheduler

/**
 * Initializes WebView storage before any provider or Activity can use WebView
 * in the dedicated private process.
 */
class RainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Application.getProcessName() == packageName
        ) {
            runCatching { VideoRetentionScheduler.ensureScheduled(this) }
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Application.getProcessName() == "$packageName:private"
        ) {
            WebView.setDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX)
        }
    }

    private companion object {
        const val PRIVATE_DATA_DIRECTORY_SUFFIX = "rain_private"
    }
}
