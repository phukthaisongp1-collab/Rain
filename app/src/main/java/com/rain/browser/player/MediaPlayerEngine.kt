package com.rain.browser.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface

/** Native MediaPlayer engine for local video playback. */
class MediaPlayerEngine(
    surface: Surface,
    filePath: String,
    private val onPrepared: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onFailure: (String) -> Unit
) : PlayerEngine {
    private var mediaPlayer: MediaPlayer? = MediaPlayer().apply {
        setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        setSurface(surface)
        setDataSource(filePath)
        setOnPreparedListener { onPrepared() }
        setOnCompletionListener { onCompleted() }
        setOnErrorListener { _, what, extra ->
            onFailure("MediaPlayer error ($what/$extra)")
            true
        }
        prepareAsync()
    }

    override fun play() {
        mediaPlayer?.let { if (!it.isPlaying) it.start() }
    }

    override fun pause() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            it.seekTo(positionMs.coerceIn(0L, it.duration.toLong()), MediaPlayer.SEEK_CLOSEST)
        }
    }

    override fun setSpeed(speed: Float) {
        mediaPlayer?.let { it.playbackParams = it.playbackParams.setSpeed(speed) }
    }

    override fun position(): Long = runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
    override fun duration(): Long = runCatching { mediaPlayer?.duration?.toLong() ?: 0L }.getOrDefault(0L)
    override fun isPlaying(): Boolean = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    override fun release() {
        mediaPlayer?.runCatching {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            release()
        }
        mediaPlayer = null
    }
}
