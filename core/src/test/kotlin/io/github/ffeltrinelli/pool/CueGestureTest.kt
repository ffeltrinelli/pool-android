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
}
