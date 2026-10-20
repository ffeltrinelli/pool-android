package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.*
import org.junit.Assert.*
import org.junit.Test

class CueGestureTest {
    private fun sample(x: Float, z: Float, t: Double, pointer: Int = 0) = PointerSample(pointer, Point(x, z), t)
    private fun shoot(interval: Double): ShotCommand {
        val gesture = CueGesture()
        gesture.begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
        assertNull(gesture.move(sample(0f, 0.29f, interval)))
        assertNull(gesture.move(sample(0f, 0.28f, interval * 2)))
        return requireNotNull(gesture.move(sample(0f, 0.26f, interval * 3)))
    }
    @Test fun `forward speed determines power and power is capped`() {
        assertTrue(shoot(0.01).impulse > shoot(0.08).impulse)
        assertEquals(1.15f, shoot(0.001).impulse, 0.001f)
    }
    @Test fun `circling rotates cue without shooting`() {
        val gesture = CueGesture()
        gesture.begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
        for (i in 1..40) {
            val a = i * Math.PI / 80
            assertNull(gesture.move(sample((kotlin.math.sin(a) * 0.3).toFloat(), (kotlin.math.cos(a) * 0.3).toFloat(), i * 0.016)))
        }
        assertEquals(-1f, gesture.direction.x, 0.001f)
        assertEquals(CuePhase.AIMING, gesture.phase)
    }
    @Test fun `one gesture emits exactly one shot`() {
        val gesture = CueGesture()
        gesture.begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
        var shots = 0
        for (i in 1..20) if (gesture.move(sample(0f, 0.3f - i * 0.01f, i * 0.01)) != null) shots++
        assertEquals(1, shots)
    }
    @Test fun `release or cancellation before contact cannot shoot`() {
        val gesture = CueGesture()
        gesture.begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
        gesture.move(sample(0f, 0.29f, 0.02))
        gesture.end(0)
        assertNull(gesture.move(sample(0f, 0.1f, 0.04)))
        gesture.begin(sample(0f, 0.3f, 0.1), Point(0f, 0f))
        gesture.cancel()
        assertNull(gesture.move(sample(0f, 0.1f, 0.2)))
    }
    @Test fun `jitter extra fingers and invalid timestamps do not strike`() {
        val gesture = CueGesture()
        gesture.begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
        gesture.begin(sample(0.3f, 0f, 0.0, 1), Point(0f, 0f))
        assertNull(gesture.move(sample(0f, 0f, 0.1, 1)))
        for (i in 1..100) assertNull(gesture.move(sample(if (i % 2 == 0) 0.001f else -0.001f, if (i % 2 == 0) 0.301f else 0.299f, i * 0.01)))
        assertNull(gesture.move(sample(0f, 0f, 0.0)))
        assertEquals(CuePhase.AIMING, gesture.phase)
    }
    @Test fun `touching cue ball dead zone is ignored`() {
        val gesture = CueGesture()
        gesture.begin(sample(0.001f, 0f, 0.0), Point(0f, 0f))
        assertEquals(CuePhase.IDLE, gesture.phase)
    }
    @Test fun `aim then push works without lifting finger`() {
        val gesture = CueGesture()
        gesture.begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
        gesture.move(sample(0.3f, 0f, 0.1))
        assertNull(gesture.move(sample(0.29f, 0f, 0.12)))
        assertNull(gesture.move(sample(0.28f, 0f, 0.14)))
        val shot = requireNotNull(gesture.move(sample(0.26f, 0f, 0.16)))
        assertEquals(-1f, shot.direction.x, 0.001f)
    }

    private fun aimedGesture() = CueGesture().apply {
        begin(sample(0f, 0.3f, 0.0), Point(0f, 0f))
    }

    @Test fun `small aligned movement previews withdrawal before locking`() {
        val gesture = aimedGesture()
        assertNull(gesture.move(sample(0f, 0.305f, 0.01)))
        assertEquals(0.037f, gesture.tipGap, 0.0001f)
        assertEquals(CuePhase.AIMING, gesture.phase)
        gesture.move(sample(0.03f, 0.30f, 0.02))
        assertEquals(0.032f, gesture.tipGap, 0.0001f)
        assertTrue(gesture.direction.x < 0f)
    }

    @Test fun `backswing locks aim and repeated practice strokes move both ways`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.31f, 0.01))
        gesture.move(sample(0f, 0.32f, 0.02))
        assertEquals(CuePhase.STROKING, gesture.phase)
        assertEquals(0.052f, gesture.tipGap, 0.0001f)
        assertNull(gesture.move(sample(0.2f, 0.30f, 0.03)))
        assertEquals(Point(0f, -1f), gesture.direction)
        assertEquals(0.032f, gesture.tipGap, 0.0001f)
        assertNull(gesture.move(sample(0.2f, 0.34f, 0.04)))
        assertEquals(0.072f, gesture.tipGap, 0.0001f)
        assertNull(gesture.move(sample(0.2f, 0.30f, 0.05)))
        assertEquals(0.032f, gesture.tipGap, 0.0001f)
        assertNotNull(gesture.move(sample(0.2f, 0.26f, 0.06)))
        assertNull(gesture.move(sample(0.2f, 0.20f, 0.07)))
    }

    @Test fun `forward stroke locks direction through withdrawal and sideways movement`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.29f, 0.01))
        gesture.move(sample(0f, 0.28f, 0.02))
        assertEquals(CuePhase.STROKING, gesture.phase)
        assertNull(gesture.move(sample(0.1f, 0.28f, 0.03)))
        assertEquals(0.012f, gesture.tipGap, 0.0001f)
        assertNull(gesture.move(sample(0.1f, 0.33f, 0.04)))
        assertEquals(0.062f, gesture.tipGap, 0.0001f)
        assertEquals(Point(0f, -1f), gesture.direction)
    }

    @Test fun `withdrawal cap discards excess movement and reverses immediately`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.31f, 0.01))
        gesture.move(sample(0f, 0.7f, 0.02))
        gesture.move(sample(0f, 1f, 0.03))
        assertEquals(0.25f, gesture.tipGap, 0.0001f)
        assertNull(gesture.move(sample(0f, 0.99f, 0.04)))
        assertEquals(0.24f, gesture.tipGap, 0.0001f)
    }

    @Test fun `release cancellation and new touch reset a withdrawn stroke`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.32f, 0.01))
        gesture.move(sample(0f, 0.34f, 0.02))
        gesture.end(1)
        assertEquals(CuePhase.STROKING, gesture.phase)
        gesture.end(0)
        assertEquals(CuePhase.IDLE, gesture.phase)
        assertEquals(0.032f, gesture.tipGap, 0.0001f)
        assertNull(gesture.move(sample(0f, 0.1f, 0.03)))
        gesture.begin(sample(0f, 0.3f, 0.04), Point(0f, 0f))
        gesture.move(sample(0f, 0.32f, 0.05))
        gesture.cancel()
        assertEquals(0.032f, gesture.tipGap, 0.0001f)
        assertEquals(CuePhase.IDLE, gesture.phase)
    }

    private fun shotAfterBackswing(distance: Float, pause: Double = 0.0): ShotCommand {
        val gesture = aimedGesture()
        var z = 0.3f + distance / 2f
        gesture.move(sample(0f, z, 0.01))
        z += distance / 2f
        gesture.move(sample(0f, z, 0.02))
        // A stationary sample separates any pause from the subsequent forward velocity.
        gesture.move(sample(0f, z, 0.02 + pause + 0.001))
        for (i in 1..100) {
            val shot = gesture.move(sample(0f, z - i * 0.01f, 0.02 + pause + 0.001 + i * 0.02))
            if (shot != null) return shot
        }
        error("Stroke did not reach the ball")
    }

    @Test fun `power depends on forward speed rather than backswing distance or pause`() {
        val short = shotAfterBackswing(0.02f)
        val long = shotAfterBackswing(0.12f)
        val paused = shotAfterBackswing(0.12f, 4.0)
        assertEquals(0.24f, short.impulse, 0.0001f)
        assertEquals(short.impulse, long.impulse, 0.0001f)
        assertEquals(short.impulse, paused.impulse, 0.0001f)
    }

    @Test fun `reversal removes backswing velocity from immediate contact power`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.29f, 0.01))
        gesture.move(sample(0f, 0.28f, 0.02))
        gesture.move(sample(0f, 0.29f, 0.03))
        val shot = requireNotNull(gesture.move(sample(0f, 0.26f, 0.09)))
        assertEquals(0.24f, shot.impulse, 0.0001f)
    }

    @Test fun `recent forward window excludes old slow movement`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.32f, 0.01))
        gesture.move(sample(0f, 0.34f, 0.02))
        gesture.move(sample(0f, 0.33f, 1.02))
        gesture.move(sample(0f, 0.32f, 1.04))
        gesture.move(sample(0f, 0.31f, 1.06))
        gesture.move(sample(0f, 0.30f, 1.08))
        gesture.move(sample(0f, 0.29f, 1.10))
        gesture.move(sample(0f, 0.28f, 1.12))
        val shot = requireNotNull(gesture.move(sample(0f, 0.26f, 1.16)))
        assertEquals(0.24f, shot.impulse, 0.0001f)
    }

    @Test fun `stationary pause removes stale forward speed`() {
        val gesture = aimedGesture()
        gesture.move(sample(0f, 0.29f, 0.001))
        gesture.move(sample(0f, 0.28f, 0.002))
        gesture.move(sample(0f, 0.28f, 3.0))
        val shot = requireNotNull(gesture.move(sample(0f, 0.26f, 3.1)))
        assertEquals(0.096f, shot.impulse, 0.0001f)
    }
}
