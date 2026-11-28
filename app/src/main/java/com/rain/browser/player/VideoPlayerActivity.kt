package com.rain.browser.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** YouTube-style controls backed exclusively by Android MediaPlayer. */
class VideoPlayerActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var surface: SurfaceView
    private lateinit var root: FrameLayout
    private lateinit var controls: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var total: TextView
    private lateinit var message: TextView
    private lateinit var preview: ImageView
    private lateinit var previewTime: TextView
    private lateinit var audio: AudioManager
    private var engine: PlayerEngine? = null
    private var path = ""
    private var title = "วิดีโอ"
    private var ready = false
    private var seeking = false
    private var controlsShown = true
    private var holding = false
    private var speed = 1f
    private var restoreSpeed = 1f
    private var startX = 0f
    private var startY = 0f
    private var verticalSwipe = false
    private var lastSeekTap = 0L
    private var accumulatedSeek = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var focusGranted = false
    private var receiverRegistered = false
    private val main = Handler(Looper.getMainLooper())
    private val previewExecutor = Executors.newSingleThreadExecutor()
    private val previewToken = AtomicInteger()
    private val previews = object : LruCache<Long, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val hideTask = Runnable { showControls(false) }
    private val messageTask = Runnable { message.visibility = View.GONE }
    private val ticker = object : Runnable {
        override fun run() {
            if (ready && !seeking) {
                val duration = engine?.duration()?.coerceAtLeast(1L) ?: 1L
                val position = engine?.position() ?: 0L
                seek.progress = (position * 1000L / duration).toInt().coerceIn(0, 1000)
                elapsed.text = timeText(position)
                total.text = timeText(duration)
            }
            main.postDelayed(this, 500L)
        }
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseVideo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "วิดีโอ" }
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) {
            Toast.makeText(this, "ไม่พบไฟล์วิดีโอในเครื่อง", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        buildUi()
        surface.holder.addCallback(this)
        requestFocus()
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(noisyReceiver, filter)
        receiverRegistered = true
        main.post(ticker)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(12))
            setBackgroundColor(0x99000000.toInt())
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = button(android.R.drawable.ic_media_previous, "กลับ") { finish() }
        val heading = TextView(this).apply {
            text = title; textSize = 16f; setTextColor(Color.WHITE); maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(heading)
        controls.addView(top)

        val middle = FrameLayout(this)
        val play = button(android.R.drawable.ic_media_play, "เล่นหรือหยุดชั่วคราว") { toggleVideo() }
        play.tag = PLAY_TAG
        middle.addView(play, FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER))
        controls.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))

        val timeline = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        elapsed = timeLabel("00:00")
        total = timeLabel("00:00")
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x88FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) {
                    seeking = true
                    preview.visibility = View.VISIBLE
                    previewTime.visibility = View.VISIBLE
                }
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && ready) {
                        val target = (engine?.duration() ?: 0L) * value / 1000L
                        elapsed.text = timeText(target)
                        previewTime.text = timeText(target)
                        loadPreview(target)
                    }
                }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    engine?.seekTo((engine?.duration() ?: 0L) * (bar?.progress ?: 0) / 1000L)
                    seeking = false
                    preview.visibility = View.GONE
                    previewTime.visibility = View.GONE
                }
            })
        }
        timeline.addView(elapsed)
        timeline.addView(seek)
        timeline.addView(total)
        controls.addView(timeline)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val speedButton = textButton("ความเร็ว 1x") { chooseSpeed() }.apply { tag = SPEED_TAG }
        val pipButton = textButton("หน้าต่างลอย") { enterPip() }
        actions.addView(speedButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(pipButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        controls.addView(actions)
        root.addView(controls, FrameLayout.LayoutParams(-1, -1))

        preview = ImageView(this).apply { visibility = View.GONE; scaleType = ImageView.ScaleType.CENTER_CROP }
        previewTime = timeLabel("").apply { visibility = View.GONE; gravity = Gravity.CENTER; setBackgroundColor(0xCC202020.toInt()) }
        root.addView(preview, FrameLayout.LayoutParams(dp(144), dp(82), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(112) })
        root.addView(previewTime, FrameLayout.LayoutParams(dp(144), dp(24), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(88) })
        message = timeLabel("").apply {
            visibility = View.GONE; textSize = 16f; gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10)); setBackgroundColor(0xCC202020.toInt())
        }
        root.addView(message, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
        attachGestures()
        seek.isEnabled = false
    }

    private fun attachGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!verticalSwipe) showControls(!controlsShown)
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!ready) return true
                val now = System.currentTimeMillis()
                accumulatedSeek = if (now - lastSeekTap <= 700L) accumulatedSeek + 10_000L else 10_000L
                lastSeekTap = now
                val delta = if (e.x < surface.width / 2f) -accumulatedSeek else accumulatedSeek
                val target = ((engine?.position() ?: 0L) + delta).coerceIn(0L, engine?.duration() ?: Long.MAX_VALUE)
                engine?.seekTo(target)
                showMessage(if (delta < 0) "ย้อนกลับ ${accumulatedSeek / 1000}s" else "ไปข้างหน้า ${accumulatedSeek / 1000}s")
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (ready && !holding) {
                    holding = true
                    restoreSpeed = speed
                    runCatching { engine?.setSpeed(2f) }
                    showMessage("⏩ 2x")
                }
            }
        })
        surface.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; verticalSwipe = false }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - startY
                    if (abs(dy) > dp(18) && abs(dy) > abs(event.x - startX)) {
                        verticalSwipe = true
                        val change = (-dy / surface.height.toFloat()).coerceIn(-1f, 1f)
                        if (startX < surface.width / 2f) {
                            val attrs = window.attributes
                            val base = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
                            attrs.screenBrightness = (base + change * 0.7f).coerceIn(0.05f, 1f)
                            window.attributes = attrs
                            showMessage("☀ ${(attrs.screenBrightness * 100).toInt()}%")
                        } else {
                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val value = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + (change * max).toInt()).coerceIn(0, max)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                            showMessage("🔊 ${value * 100 / max}%")
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (holding) {
                        holding = false
                        runCatching { engine?.setSpeed(restoreSpeed) }
                        showMessage("ความเร็ว ${restoreSpeed}x")
                    }
                    if (verticalSwipe) main.postDelayed({ verticalSwipe = false }, 250L)
                }
            }
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (engine != null) return
        engine = MediaPlayerEngine(
            holder.surface, path,
            onPrepared = {
                ready = true
                runOnUiThread {
                    seek.isEnabled = true
                    total.text = timeText(engine?.duration() ?: 0L)
                    updatePlayIcon(true)
                    engine?.play()
                    updatePipActions()
                    scheduleHide()
                }
            },
            onCompleted = {
                runOnUiThread {
                    updatePlayIcon(false)
                    showMessage("เล่นจบแล้ว")
                    showControls(true)
                }
            },
            onFailure = { reason ->
                runOnUiThread {
                    Toast.makeText(this, "เล่นวิดีโอไม่ได้: $reason", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        )
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { engine?.pause() }

    private fun toggleVideo() {
        if (!ready) return
        if (engine?.isPlaying() == true) pauseVideo() else {
            if (!focusGranted) requestFocus()
            if (focusGranted) { engine?.play(); updatePlayIcon(true) }
        }
        updatePipActions()
        scheduleHide()
    }

    private fun pauseVideo() {
        engine?.pause()
        updatePlayIcon(false)
        updatePipActions()
    }

    private fun updatePlayIcon(playing: Boolean) {
        findTagged(root, PLAY_TAG)?.let { (it as ImageButton).setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        ) }
    }

    private fun chooseSpeed() {
        val labels = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
        val values = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        android.app.AlertDialog.Builder(this).setTitle("ความเร็วในการเล่น")
            .setItems(labels) { _, index ->
                runCatching { engine?.setSpeed(values[index]) }
                    .onSuccess {
                        speed = values[index]
                        (findTagged(root, SPEED_TAG) as? TextView)?.text = "ความเร็ว ${labels[index]}"
                        showMessage("ความเร็ว ${labels[index]}")
                    }
                    .onFailure { Toast.makeText(this, "ตั้งค่าความเร็วไม่ได้", Toast.LENGTH_SHORT).show() }
            }.show()
    }

    private fun loadPreview(position: Long) {
        val key = position / 2000L
        previews.get(key)?.let { preview.setImageBitmap(it); return }
        val token = previewToken.incrementAndGet()
        previewExecutor.execute {
            val start = System.nanoTime()
            val bitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    retriever.getFrameAtTime(position * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                        val scaled = Bitmap.createScaledBitmap(frame, 256, 144, true)
                        if (scaled !== frame) frame.recycle()
                        scaled
                    }
                } finally { retriever.release() }
            }.getOrNull()
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            if (bitmap != null && elapsedMs <= 500L && token == previewToken.get()) {
                previews.put(key, bitmap)
                runOnUiThread { if (token == previewToken.get() && seeking) preview.setImageBitmap(bitmap) }
            } else if (elapsedMs > 500L) {
                runOnUiThread { if (token == previewToken.get()) preview.setImageDrawable(null) }
            }
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "อุปกรณ์ไม่รองรับหน้าต่างลอย", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9)).setActions(pipActions()).build())
        }.onFailure { Toast.makeText(this, "เปิดหน้าต่างลอยไม่ได้", Toast.LENGTH_SHORT).show() }
    }

    private fun pipActions(): List<RemoteAction> {
        val playing = engine?.isPlaying() == true
        val action = if (playing) ACTION_PAUSE else ACTION_PLAY
        val icon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(this, 2201, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return listOf(RemoteAction(Icon.createWithResource(this, icon),
            if (playing) "หยุดชั่วคราว" else "เล่น", action, pending))
    }

    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) {
            setPictureInPictureParams(PictureInPictureParams.Builder().setActions(pipActions()).build())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            ACTION_PLAY -> { if (!focusGranted) requestFocus(); if (focusGranted) { engine?.play(); updatePlayIcon(true) } }
            ACTION_PAUSE -> pauseVideo()
        }
        updatePipActions()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 && ready && engine?.isPlaying() == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, config: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(inPip, config)
        controls.visibility = if (inPip) View.GONE else View.VISIBLE
        if (!inPip) scheduleHide()
    }

    private fun requestFocus() {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change <= AudioManager.AUDIOFOCUS_LOSS) pauseVideo()
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setOnAudioFocusChangeListener(listener).build()
        focusRequest = request
        focusGranted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun showControls(show: Boolean) {
        controlsShown = show
        controls.visibility = if (show) View.VISIBLE else View.GONE
        main.removeCallbacks(hideTask)
        if (show) scheduleHide()
    }

    private fun scheduleHide() {
        main.removeCallbacks(hideTask)
        if (engine?.isPlaying() == true && !isInPictureInPictureMode) main.postDelayed(hideTask, 3000L)
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        main.removeCallbacks(messageTask)
        main.postDelayed(messageTask, 900L)
    }

    private fun requestFocusRelease() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
        focusGranted = false
    }

    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun button(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon); contentDescription = description; setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE); setOnClickListener { action() }
    }

    private fun textButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 13f; setTextColor(Color.WHITE)
        setOnClickListener { action() }
    }

    private fun timeLabel(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 12f; setTextColor(Color.WHITE)
    }

    private fun findTagged(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
            findTagged(view.getChildAt(i), tag)?.let { return it }
        }
        return null
    }

    private fun timeText(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        val h = seconds / 3600L
        val m = seconds % 3600L / 60L
        val s = seconds % 60L
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) pauseVideo()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        previewToken.incrementAndGet()
        previewExecutor.shutdownNow()
        engine?.release()
        engine = null
        requestFocusRelease()
        if (receiverRegistered) runCatching { unregisterReceiver(noisyReceiver) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "com.rain.browser.player.FILE_PATH"
        const val EXTRA_TITLE = "com.rain.browser.player.TITLE"
        private const val ACTION_PLAY = "com.rain.browser.player.PIP_PLAY"
        private const val ACTION_PAUSE = "com.rain.browser.player.PIP_PAUSE"
        private const val PLAY_TAG = "rain_player_play"
        private const val SPEED_TAG = "rain_player_speed"
    }
}
