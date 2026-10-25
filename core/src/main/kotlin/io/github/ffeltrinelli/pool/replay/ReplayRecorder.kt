package io.github.ffeltrinelli.pool.replay

import io.github.ffeltrinelli.pool.audio.GameSound
import io.github.ffeltrinelli.pool.audio.SoundPlayer
import io.github.ffeltrinelli.pool.model.PhysicsEvent
import io.github.ffeltrinelli.pool.opponent.CuePose

/** Captures one shot and a bounded aiming lead-in. All calls stay on the game thread. */
class ReplayRecorder {
    private val aiming = ArrayDeque<ReplayFrame>()
    private val frames = mutableListOf<ReplayFrame>()
    private data class Voice(val sound: GameSound, val volume: Float, val start: Float, var stop: Float)
    private val voices = mutableMapOf<Long, Voice>()
    var seconds = 0f
        private set
    private var strike = 0f
    private var contact: Float? = null
    private var pocket: Float? = null
    var recording = false
        private set

    fun aim(delta: Float, balls: List<BallPose>, cue: CuePose?) {
        if (recording) return
        if (cue == null) { aiming.clear(); seconds = 0f; return }
        seconds += delta
        aiming.addLast(ReplayFrame(seconds, balls, cue))
        while (aiming.size > 1 && aiming.elementAt(1).seconds < seconds - 3f) aiming.removeFirst()
    }

    fun strike(balls: List<BallPose>, cue: CuePose) {
        frames.clear(); voices.clear(); contact = null; pocket = null
        val origin = maxOf(aiming.firstOrNull()?.seconds ?: seconds, seconds - 3f)
        frames.addAll(aiming.filter { it.seconds >= origin }.map { it.copy(seconds = it.seconds - origin) })
        strike = seconds - origin
        frames.removeAll { it.seconds >= strike }
        frames.add(ReplayFrame(strike, balls, cue.copy(tipGap = 0f)))
        seconds = strike
        recording = true
        aiming.clear()
    }

    fun step(delta: Float, balls: List<BallPose>) {
        if (!recording) return
        seconds += delta
        frames.add(ReplayFrame(seconds, balls, null))
    }

    fun event(event: PhysicsEvent, stepSeconds: Float) {
        if (!recording) return
        val time = seconds + stepSeconds
        when (event) {
            is PhysicsEvent.Contacts -> if (contact == null && (event.cueTargets.isNotEmpty() || 0 in event.railBalls)) contact = time
            is PhysicsEvent.Pocketed -> if (event.ballId != 0) pocket = time
            else -> Unit
        }
    }

    fun sound(id: Long, sound: GameSound, volume: Float) {
        if (recording) voices[id] = Voice(sound, volume, seconds, seconds + sound.seconds)
    }
    fun stop(id: Long) { voices[id]?.let { it.stop = minOf(it.stop, seconds) } }

    fun finish(): ReplayClip? {
        if (!recording) return null
        recording = false
        val end = pocket
        if (end == null) { clear(); return null }
        // Production flushes audio after up to 100 ms of fixed steps in one rendered frame.
        // Retain that final flush even when the last pocket occurred at the frame's beginning.
        val sounds = voices.values.filter { it.start <= end + 0.1001f }.map {
            ReplaySound(it.sound, it.volume, it.start, it.stop)
        }
        return ReplayClip(frames.toList(), sounds, strike, contact ?: end, end).also { clear() }
    }

    fun clear() {
        aiming.clear(); frames.clear(); voices.clear(); seconds = 0f; recording = false
    }
}

/** Records logical playback even when a device's sound backend is unavailable. */
class ReplayRecordingPlayer(private val delegate: SoundPlayer, private val recorder: ReplayRecorder) : SoundPlayer {
    private var nextId = 0L
    private val nativeIds = mutableMapOf<Long, Long>()
    override fun play(sound: GameSound, volume: Float): Long {
        val id = ++nextId
        nativeIds[id] = delegate.play(sound, volume)
        recorder.sound(id, sound, volume)
        return id
    }
    override fun stop(sound: GameSound, id: Long) {
        recorder.stop(id)
        nativeIds.remove(id)?.takeIf { it >= 0 }?.let { delegate.stop(sound, it) }
    }
    override fun dispose() { nativeIds.clear(); delegate.dispose() }
}
