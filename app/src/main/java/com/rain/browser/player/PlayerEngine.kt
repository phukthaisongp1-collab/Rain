package com.rain.browser.player

interface PlayerEngine {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun position(): Long
    fun duration(): Long
    fun isPlaying(): Boolean
    fun release()
}
