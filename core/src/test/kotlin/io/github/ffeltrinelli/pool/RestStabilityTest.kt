package io.github.ffeltrinelli.pool

import com.badlogic.gdx.math.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.model.TableGeometry
import org.junit.Assert.*
import org.junit.Test

class RestStabilityTest {
    @Test fun `submillimetre contact oscillation can settle despite velocity spikes`() {
        val rest = RestStability()
        repeat(37) { step ->
            val sign = if (step % 2 == 0) 1f else -1f
            rest.beginStep()
            rest.observe(13, Vector3(-0.50158f + sign * 0.0001f, TableGeometry.BALL_RADIUS - 0.00008f, -0.93305f),
                Quaternion(Vector3.Z, sign * 0.2f), 0.014f * 0.014f, 0.45f * 0.45f)
            rest.finishStep(PoolPhysics.STEP)
            if (step < 35) assertFalse(rest.settled)
        }
        assertTrue(rest.settled)
    }
    @Test fun `meaningful continuous spin rolling or falling never count as stable contact`() {
        for (movement in listOf("spin", "roll", "fall", "fast")) {
            val rest = RestStability()
            repeat(120) { step ->
                rest.beginStep()
                rest.observe(1,
                    Vector3(if (movement == "roll") step * PoolPhysics.STEP * 0.02f else 0f,
                        if (movement == "fall") 0.1f else TableGeometry.BALL_RADIUS, 0f),
                    Quaternion(Vector3.Y, if (movement == "spin") step * PoolPhysics.STEP * 0.4f * 57.29578f else 0f),
                    if (movement == "roll") 0.02f * 0.02f else 0f,
                    if (movement == "fast") 1f else 0.4f * 0.4f)
                rest.finishStep(PoolPhysics.STEP)
                assertFalse("$movement at $step", rest.settled)
            }
        }
    }
    @Test fun `restart and changed ball identities require another full quiet interval`() {
        val rest = RestStability()
        fun observe(id: Int) {
            rest.beginStep()
            rest.observe(id, Vector3(0f, TableGeometry.BALL_RADIUS, 0f), Quaternion(), 0f, 0f)
            rest.finishStep(PoolPhysics.STEP)
        }
        repeat(37) { observe(0) }
        assertTrue(rest.settled)
        rest.reset()
        assertFalse(rest.settled)
        repeat(37) { observe(0) }
        observe(1)
        assertFalse(rest.settled)
    }
}
