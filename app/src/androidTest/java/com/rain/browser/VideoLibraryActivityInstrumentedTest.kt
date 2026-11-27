package com.rain.browser

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoLibraryActivityInstrumentedTest {
    @Test
    fun libraryScreenLaunchesAndShowsEmptyState() {
        ActivityScenario.launch(VideoLibraryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val root = content.getChildAt(0) as LinearLayout
                val title = root.getChildAt(0) as TextView
                assertEquals(activity.getString(R.string.video_library_title), title.text.toString())

                val scrollView = root.getChildAt(3) as ScrollView
                val list = scrollView.getChildAt(0) as LinearLayout
                val emptyState = list.getChildAt(0) as TextView
                assertEquals(
                    activity.getString(R.string.video_library_empty),
                    emptyState.text.toString()
                )
            }
        }
    }
}
