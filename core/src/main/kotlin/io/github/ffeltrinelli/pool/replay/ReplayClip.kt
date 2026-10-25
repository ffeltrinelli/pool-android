package io.github.ffeltrinelli.pool.replay

import com.badlogic.gdx.math.Quaternion
import io.github.ffeltrinelli.pool.audio.GameSound
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.CuePose
import io.github.ffeltrinelli.pool.physics.PoolPhysics

/** Value data only: a clip never owns or restores native physics bodies. */
data class BallPose(val id: Int, val x: Float, val y: Float, val z: Float,
    val qx: Float, val qy: Float, val qz: Float, val qw: Float, val visible: Boolean) {
    val point get() = Point(x, z)
}
data class ReplayFrame(val seconds: Float, val balls: List<BallPose>, val cue: CuePose?)
data class ReplaySound(val sound: GameSound, val volume: Float, val start: Float, val stop: Float)
data class ReplayClip(val frames: List<ReplayFrame>, val sounds: List<ReplaySound>,
    val strikeSeconds: Float, val contactSeconds: Float, val pocketSeconds: Float,
    val timeScale: Float = 1f) {
    val videoSeconds = maxOf(pocketSeconds, sounds.maxOfOrNull { it.stop } ?: pocketSeconds)

    /** Stretch event timing while retaining the original pitch and natural length of each effect. */
    fun slowMotion(): ReplayClip {
        val stretch = 1f / PLAYBACK_SPEED
        return copy(
            frames = frames.map { it.copy(seconds = it.seconds * stretch) },
            sounds = sounds.map {
                val start = it.start * stretch
                it.copy(start = start, stop = start + minOf((it.stop - it.start) * stretch, it.sound.seconds))
            },
            strikeSeconds = strikeSeconds * stretch,
            contactSeconds = contactSeconds * stretch,
            pocketSeconds = pocketSeconds * stretch,
            timeScale = timeScale * stretch
        )
    }

    fun frameAt(seconds: Float): ReplayFrame {
        val time = seconds.coerceIn(0f, pocketSeconds)
        val found = frames.binarySearchBy(time) { it.seconds }
        if (found >= 0) return frames[found]
        val next = (-found - 1).coerceIn(0, frames.lastIndex)
        if (next == 0) return frames.first()
        val a = frames[next - 1]
        val b = frames[next]
        val t = ((time - a.seconds) / (b.seconds - a.seconds)).coerceIn(0f, 1f)
        val balls = a.balls.map { first ->
            val last = b.balls.first { it.id == first.id }
            val q = Quaternion(first.qx, first.qy, first.qz, first.qw)
                .slerp(Quaternion(last.qx, last.qy, last.qz, last.qw), t)
            first.copy(x = first.x + (last.x - first.x) * t, y = first.y + (last.y - first.y) * t,
                z = first.z + (last.z - first.z) * t, qx = q.x, qy = q.y, qz = q.z, qw = q.w)
        }
        val cue = a.cue?.let { first -> b.cue?.let { last ->
            CuePose((first.direction * (1f - t) + last.direction * t).normalized(),
                first.tipGap + (last.tipGap - first.tipGap) * t)
        } ?: first }
        return ReplayFrame(time, balls, cue.takeIf { time < strikeSeconds })
    }
    companion object { const val PLAYBACK_SPEED = 0.75f }

}

fun PoolPhysics.replayBalls() = balls.map { ball ->
    BallPose(ball.id, ball.position.x, ball.position.y, ball.position.z,
        ball.rotation.x, ball.rotation.y, ball.rotation.z, ball.rotation.w, !ball.pocketed)
}

fun qualifiesForReplay(outcome: ShotOutcome): Boolean = with(outcome.decision) {
    foul == null && loss == null && !rerack && (continues || won) && outcome.pocketed.any { it in 1..15 }
}
