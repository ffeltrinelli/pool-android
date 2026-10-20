package io.github.ffeltrinelli.pool

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ClothPhysicsTest {
    private fun withPhysics(test: (PoolPhysics, MutableList<PhysicsEvent>, MutableList<Impact>) -> Unit) {
        val events = mutableListOf<PhysicsEvent>()
        val impacts = mutableListOf<Impact>()
        val p = PoolPhysics(events::add, impacts::add)
        try {
            p.reset(listOf(0 to Point(0f, 0.4f)))
            test(p, events, impacts)
        } finally { p.dispose() }
    }

    @Test fun `cloth supports a stationary ball silently without drift`() = withPhysics { p, _, impacts ->
        repeat(1200) { p.step() }
        val ball = p.balls.single()
        assertFalse(ball.pocketed)
        assertEquals(TableGeometry.BALL_RADIUS, ball.position.y, 0.0005f)
        assertEquals(0.4f, ball.position.z, 0.0001f)
        assertEquals(0f, ball.body.linearVelocity.len(), 0.001f)
        assertTrue(impacts.isEmpty())
    }

    @Test fun `central impulse slides then rolls in the travel direction`() = withPhysics { p, _, _ ->
        p.strike(ShotCommand(Point(0f, -1f), 0.17f))
        val ball = p.balls.single()
        p.step()
        val earlySlip = abs(ball.body.linearVelocity.cpy().z - ball.body.angularVelocity.cpy().x * TableGeometry.BALL_RADIUS)
        repeat(35) { p.step() }
        val velocity = ball.body.linearVelocity.cpy()
        val spin = ball.body.angularVelocity.cpy()
        val rollingSlip = abs(velocity.z - spin.x * TableGeometry.BALL_RADIUS)
        assertTrue(earlySlip > 0.7f)
        assertTrue(spin.x < -1f)
        assertTrue(velocity.z < -0.4f)
        assertTrue("Slip $rollingSlip from $earlySlip", rollingSlip < earlySlip * 0.1f)
        assertEquals(velocity.z, spin.x * TableGeometry.BALL_RADIUS, 0.04f)
        assertTrue(abs(ball.rotation.dot(Quaternion(Vector3.X, -90f))) < 0.98f)
    }

    @Test fun `cloth dissipates energy without an artificial rotation driver`() = withPhysics { p, _, _ ->
        val ball = p.balls.single()
        val mass = TableGeometry.BALL_MASS
        val inertia = 0.4f * mass * TableGeometry.BALL_RADIUS * TableGeometry.BALL_RADIUS
        fun energy(): Float {
            val linear = ball.body.linearVelocity.len2()
            val angular = ball.body.angularVelocity.len2()
            return 0.5f * (mass * linear + inertia * angular)
        }
        p.strike(ShotCommand(Point(0f, -1f), 0.17f))
        var previous = energy()
        repeat(100) {
            p.step()
            val current = energy()
            assertTrue("Energy increased: $previous -> $current", current <= previous + 0.0001f)
            previous = current
        }
        assertTrue(previous < 0.06f)
    }

    @Test fun `spin keeps the shot locked until angular movement settles`() = withPhysics { p, events, impacts ->
        p.strike(ShotCommand(Point(0f, -1f), 0.0001f))
        p.balls.single().body.angularVelocity = Vector3(0f, 15f, 0f)
        repeat(60) { p.step() }
        assertTrue(p.balls.single().body.angularVelocity.len() > 0.3f)
        assertFalse(events.contains(PhysicsEvent.Settled))
        repeat(2400) { p.step() }
        assertEquals(1, events.count { it == PhysicsEvent.Settled })
        assertEquals(0f, p.balls.single().body.angularVelocity.len(), 0.001f)
        assertEquals(listOf(ImpactKind.CUE), impacts.map { it.kind })
    }

    @Test fun `restart restores pose and velocities after rolling and spin`() = withPhysics { p, _, _ ->
        repeat(20) {
            p.strike(ShotCommand(Point(0f, -1f), 0.17f))
            repeat(40) { p.step() }
            p.reset(listOf(0 to Point(0f, 0.4f)))
            val ball = p.balls.single()
            assertEquals(1f, abs(ball.rotation.dot(Quaternion(Vector3.X, -90f))), 0.00001f)
            assertEquals(ball.rotation, ball.previousRotation)
            assertEquals(0f, ball.body.angularVelocity.len(), 0f)
            assertEquals(0f, ball.body.linearVelocity.len(), 0f)
            assertEquals(TableGeometry.BALL_RADIUS, ball.position.y, 0f)
            assertFalse(ball.pocketed)
        }
    }
}
