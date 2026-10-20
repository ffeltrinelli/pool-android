package io.github.ffeltrinelli.pool.audio

import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.Impact
import io.github.ffeltrinelli.pool.model.ImpactKind
import io.github.ffeltrinelli.pool.model.PocketDrop
import kotlin.math.sqrt

enum class GameSound(val path: String, val seconds: Float) {
    BALL("audio/ball.wav", 0.24f),
    CUSHION("audio/cushion.wav", 0.24f),
    CUE("audio/cue.wav", 0.20f),
    POCKET("audio/pocket.wav", 0.68f);

    val priority get() = if (this == POCKET) 1 else 0
}

interface SoundPlayer : Disposable {
    /** Returns a playback ID, or -1 when audio is unavailable. */
    fun play(sound: GameSound, volume: Float): Long
    fun stop(sound: GameSound, id: Long)
}

data class AudioTuning(
    val minimumImpulse: Float = Impact.MIN_AUDIBLE_IMPULSE,
    val fullVolumeImpulse: Float = 1.15f,
    val maximumVolume: Float = 0.45f,
    val maximumVoices: Int = 8,
    val volumeBudget: Float = 1f,
    val pocketVolume: Float = 0.35f
) {
    init {
        require(pocketVolume in 0f..1f)
        require(minimumImpulse >= 0f && fullVolumeImpulse > minimumImpulse)
        require(maximumVolume in 0f..1f && maximumVoices > 0 && volumeBudget > 0f)
    }

    fun volume(impulse: Float): Float {
        if (!impulse.isFinite() || impulse < minimumImpulse) return 0f
        return maximumVolume * sqrt(((impulse - minimumImpulse) / (fullVolumeImpulse - minimumImpulse)).coerceIn(0f, 1f))
    }
}

/** Owns the player. Queue during fixed steps, then flush once per rendered frame. */
class PoolAudio(private val player: SoundPlayer, private val tuning: AudioTuning = AudioTuning()) : Disposable {
    private data class Pending(val sound: GameSound, val volume: Float)
    private data class Voice(val sound: GameSound, val id: Long, val volume: Float, var remaining: Float)
    private val pending = mutableListOf<Pending>()
    private val voices = mutableListOf<Voice>()
    private var paused = false
    private val pocketedIds = mutableSetOf<Int>()

    fun queue(impact: Impact) {
        if (paused) return
        val volume = tuning.volume(impact.impulse)
        if (volume <= 0f) return
        val sound = when (impact.kind) {
            ImpactKind.BALL -> GameSound.BALL
            ImpactKind.CUSHION -> GameSound.CUSHION
            ImpactKind.CUE -> GameSound.CUE
        }
        enqueue(Pending(sound, volume))
    }

    fun queue(drop: PocketDrop) {
        if (paused || !pocketedIds.add(drop.ballId)) return
        enqueue(Pending(GameSound.POCKET, tuning.pocketVolume))
    }

    private fun enqueue(sound: Pending) {
        if (sound.volume <= 0f) return
        pending.add(sound)
        pending.sortWith(compareByDescending<Pending> { it.sound.priority }.thenByDescending { it.volume })
        if (pending.size > tuning.maximumVoices) pending.removeAt(pending.lastIndex)
    }

    fun flush(delta: Float) {
        if (paused) return
        voices.forEach { it.remaining -= delta.coerceAtLeast(0f) }
        // Explicit stopping also bounds playback if a backend starts a sample late.
        voices.filter { it.remaining <= 0f }.forEach { player.stop(it.sound, it.id) }
        voices.removeAll { it.remaining <= 0f }
        pending.forEach(::play)
        pending.clear()
    }

    private fun play(hit: Pending) {
        while (voices.isNotEmpty() && (voices.size >= tuning.maximumVoices || totalVolume() + hit.volume > tuning.volumeBudget)) {
            val quietest = voices.minWith(compareBy<Voice> { it.sound.priority }.thenBy { it.volume })
            if (hit.sound.priority < quietest.sound.priority ||
                (hit.sound.priority == quietest.sound.priority && hit.volume <= quietest.volume)) break
            player.stop(quietest.sound, quietest.id)
            voices.remove(quietest)
        }
        if (voices.size >= tuning.maximumVoices) return
        // Bounding summed gain leaves headroom for the bundled clips on a busy break.
        val volume = minOf(hit.volume, (tuning.volumeBudget - totalVolume()))
        if (volume <= 0f) return
        val id = player.play(hit.sound, volume)
        if (id >= 0) voices.add(Voice(hit.sound, id, volume, hit.sound.seconds))
    }

    private fun totalVolume() = voices.sumOf { it.volume.toDouble() }.toFloat()

    fun clear() {
        pocketedIds.clear()
        stopPlayback()
    }

    private fun stopPlayback() {
        pending.clear()
        voices.forEach { player.stop(it.sound, it.id) }
        voices.clear()
    }

    fun pause() { paused = true; stopPlayback() }
    fun resume() { paused = false }
    override fun dispose() { pause(); player.dispose() }
}
