package io.github.ffeltrinelli.pool.replay

import io.github.ffeltrinelli.pool.audio.SoundPlayer

/** Playback owns only its sound voices and clock; the resolved match remains untouched. */
class ReplayPlayback(val clip: ReplayClip, private val player: SoundPlayer) : AutoCloseable {
    private val voices = mutableMapOf<Int, Long>()
    private var nextSound = 0
    var seconds = 0f
        private set
    val holdSecondsRemaining: Int?
        get() = if (seconds < clip.pocketSeconds || finished) null
            else kotlin.math.ceil((clip.pocketSeconds + HOLD_SECONDS - seconds).toDouble()).toInt()
    val finished get() = seconds >= clip.pocketSeconds + HOLD_SECONDS

    fun advance(delta: Float) {
        seconds += delta.coerceAtLeast(0f)
        clip.sounds.forEachIndexed { index, sound ->
            if (index >= nextSound && sound.start <= seconds) {
                if (sound.stop > seconds) voices[index] = player.play(sound.sound, sound.volume)
                nextSound = index + 1
            }
            if (sound.stop <= seconds) voices.remove(index)?.let { player.stop(sound.sound, it) }
        }
    }
    fun silence() {
        voices.forEach { (index, id) -> player.stop(clip.sounds[index].sound, id) }
        voices.clear()
    }
    override fun close() = silence()
    companion object { const val HOLD_SECONDS = 5f }
}
