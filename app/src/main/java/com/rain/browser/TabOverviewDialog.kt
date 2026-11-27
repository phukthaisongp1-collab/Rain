package com.rain.browser

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rain.browser.browser.BrowserTab
import kotlin.math.abs
import kotlin.math.min

class TabOverviewDialog(
    private val context: Context,
    private val tabsProvider: () -> List<BrowserTab>,
    private val activeTabIdProvider: () -> Long?,
    private val onSelectTab: (BrowserTab) -> Unit,
    private val onCloseTab: (BrowserTab) -> Unit,
    private val onNewTab: () -> Unit
) {
    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(8))
            setBackgroundColor(Color.rgb(242, 243, 245))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val countLabel = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.rgb(32, 33, 36))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        val newTabButton = TextView(context).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = context.getString(R.string.action_new_tab)
            background = rounded(Color.rgb(26, 115, 232), dp(24))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        header.addView(countLabel)
        header.addView(newTabButton)
        root.addView(header)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(8))
        }
        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        scroll.addView(grid, ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        lateinit var refreshCards: () -> Unit
        refreshCards = {
            val tabs = tabsProvider()
            countLabel.text = context.getString(R.string.tabs_count, tabs.size)
            grid.removeAllViews()
            grid.rowCount = (tabs.size + 1) / 2
            tabs.forEachIndexed { index, tab ->
                val card = createCard(tab, tab.id == activeTabIdProvider())
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(index / 2),
                    GridLayout.spec(index % 2, 1f)
                ).apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
                grid.addView(card, params)
                card.setOnClickListener {
                    onSelectTab(tab)
                    dialog.dismiss()
                }
                var downX = 0f
                var downY = 0f
                card.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            val deltaX = event.rawX - downX
                            val deltaY = event.rawY - downY
                            if (abs(deltaX) > dp(64) && abs(deltaX) > abs(deltaY)) {
                                onCloseTab(tab)
                                refreshCards()
                            } else {
                                view.performClick()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> true
                        else -> true
                    }
                }
                card.findViewWithTag<View>("tab_close")?.setOnClickListener {
                    onCloseTab(tab)
                    refreshCards()
                }
            }
        }

        newTabButton.setOnClickListener {
            dialog.dismiss()
            onNewTab()
        }
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            refreshCards()
        }
        dialog.setOnDismissListener { grid.removeAllViews() }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun createCard(tab: BrowserTab, isActive: Boolean): View {
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(
                    dp(if (isActive) 2 else 1),
                    if (isActive) Color.rgb(26, 115, 232) else Color.rgb(218, 220, 224)
                )
            }
            isClickable = true
            isFocusable = true
            contentDescription = tab.title.ifBlank { tab.url }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(232, 234, 237))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(142)
            )
            createThumbnail(tab)?.let { setImageBitmap(it) }
        }
        val title = TextView(context).apply {
            text = tab.title.ifBlank { tab.url.ifBlank { "New Tab" } }
            textSize = 14f
            setTextColor(Color.rgb(32, 33, 36))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(9), dp(8), dp(9), dp(2))
        }
        val url = TextView(context).apply {
            text = tab.url
            textSize = 11f
            setTextColor(Color.rgb(95, 99, 104))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(9), 0, dp(9), dp(9))
        }
        content.addView(preview)
        content.addView(title)
        content.addView(url)
        card.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val close = TextView(context).apply {
            tag = "tab_close"
            text = "×"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(60, 64, 67))
            background = rounded(Color.WHITE, dp(18))
            elevation = dp(3).toFloat()
            contentDescription = context.getString(R.string.dialog_close)
            layoutParams = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.END or Gravity.TOP).apply {
                setMargins(0, dp(7), dp(7), 0)
            }
        }
        card.addView(close)
        return card
    }

    private fun createThumbnail(tab: BrowserTab): Bitmap? = runCatching {
        val source = tab.webView
        val sourceWidth = source.width.takeIf { it > 0 } ?: return@runCatching null
        val sourceHeight = source.height.takeIf { it > 0 } ?: return@runCatching null
        val bitmapWidth = dp(320)
        val bitmapHeight = dp(180)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val scale = min(bitmapWidth.toFloat() / sourceWidth, bitmapHeight.toFloat() / sourceHeight)
        val offsetX = (bitmapWidth - sourceWidth * scale) / 2f
        val offsetY = (bitmapHeight - sourceHeight * scale) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        source.draw(canvas)
        canvas.restore()
        bitmap
    }.getOrNull()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}