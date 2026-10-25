package io.github.ffeltrinelli.pool

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PoolPhysicsTest {
    private fun physics(test: (PoolPhysics) -> Unit) {
        val physics = PoolPhysics()
        try { test(physics) } finally { physics.dispose() }
    }
    @Test fun `head on collision transfers forward momentum`() = physics { p ->
        p.reset(listOf(0 to Point(0f, 0.4f), 1 to Point(0f, 0.1f)))
        p.strike(ShotCommand(Point(0f, -1f), 0.17f))
        repeat(42) { p.step() }
        assertTrue(p.balls[1].body.linearVelocity.z < -0.4f)
        assertTrue(abs(p.balls[0].body.linearVelocity.z) < abs(p.balls[1].body.linearVelocity.z))
    }
    @Test fun `glancing collision deflects object ball`() = physics { p ->
        p.reset(listOf(0 to Point(0f, 0.3f), 1 to Point(0.035f, 0.1f)))
        p.strike(ShotCommand(Point(0f, -1f), 0.17f))
        repeat(40) { p.step() }
        assertTrue(p.balls[1].body.linearVelocity.x > 0.1f)
        assertTrue(p.balls[1].body.linearVelocity.z < -0.1f)
    }
    @Test fun `cushion reverses velocity without tunnelling at maximum power`() = physics { p ->
        p.reset(listOf(0 to Point(0f, 0.4f)))
        p.strike(ShotCommand(Point(1f, 0f), 1.15f))
        var rebounded = false
        repeat(25) {
            p.step()
            if (p.balls[0].body.linearVelocity.x < -0.1f) rebounded = true
            assertTrue(abs(p.balls[0].position.x) <= TableGeometry.HALF_WIDTH)
        }
        assertTrue(rebounded)
        assertFalse(p.balls[0].pocketed)
    }
    @Test fun `maximum power transfers momentum instead of crossing an object ball`() = physics { p ->
        p.reset(listOf(0 to Point(0f, 0.4f), 1 to Point(0f, 0.1f)))
        p.strike(ShotCommand(Point(0f, -1f), 1.15f))
        repeat(10) { p.step() }
        assertTrue(p.balls[1].body.linearVelocity.z < -1f)
        assertTrue(p.balls[0].position.z > p.balls[1].position.z)
    }
    @Test fun `restart after pocketing restores a playable cue ball`() = physics { p ->
        p.reset(listOf(0 to Point(TableGeometry.HALF_WIDTH + 0.02f, 0f)))
        p.strike(ShotCommand(Point(1f, 0f), 0.1f))
        repeat(60) { p.step() }
        assertTrue(p.balls[0].pocketed)
        p.reset()
        assertEquals(16, p.balls.size)
        assertFalse(p.balls.first { it.id == 0 }.pocketed)
        p.strike(ShotCommand(Point(0f, -1f), 0.1f))
        p.step()
        assertTrue(p.balls.first { it.id == 0 }.position.z < 0.58f)
    }
    @Test fun `all six physical pockets capture cue ball`() {
        TableGeometry.pockets.forEach { pocket ->
            val events = mutableListOf<PhysicsEvent>()
            val p = PoolPhysics(events::add)
            try {
                val direction = pocket.normalized()
                p.reset(listOf(0 to (pocket - direction * 0.16f)))
                p.strike(ShotCommand(direction, 0.10f))
                repeat(180) { p.step() }
                assertTrue("Pocket $pocket", p.balls[0].pocketed)
                assertEquals(1, events.count { it == PhysicsEvent.Pocketed(0) })
            } finally { p.dispose() }
        }
    }
    @Test fun `shot eventually settles once`() {
        val events = mutableListOf<PhysicsEvent>()
        val p = PoolPhysics(events::add)
        try {
            p.reset(listOf(0 to Point(0f, 0.4f)))
            p.strike(ShotCommand(Point(0f, -1f), 0.10f))
            repeat(2400) { p.step() }
            assertEquals(1, events.count { it == PhysicsEvent.Settled })
            assertEquals(0f, p.balls[0].body.linearVelocity.len(), 0.001f)
        } finally { p.dispose() }
    }
    @Test fun `oneplus corner cushion residual motion settles once`() {
        val events = mutableListOf<PhysicsEvent>()
        val physics = PoolPhysics(events::add)
        try {
            physics.reset(listOf(0 to Point(0.22541456f, -0.5845344f), 11 to Point(-0.49207595f, -1.0942622f)))
            physics.strike(ShotCommand(Point(0f, -1f), 0.0001f))
            val ball = physics.balls.first { it.id == 11 }
            ball.body.worldTransform = Matrix4().set(
                Vector3(-0.49207595f, 0.028532963f, -1.0942622f),
                Quaternion(-0.539429f, 0.115831055f, 0.58890116f, 0.5905885f))
            ball.body.linearVelocity = Vector3(-0.008917803f, -0.00047525018f, 0.000060406965f)
            ball.body.angularVelocity = Vector3(-0.000084969666f, 0.0016494485f, 0.31223032f)
            repeat(1200) { physics.step() }
            assertEquals("Residual cushion motion must resolve the shot", 1, events.count { it == PhysicsEvent.Settled })
            assertFalse(ball.pocketed)
            assertEquals(0f, ball.body.angularVelocity.len(), 0.001f)
            val settledPosition = ball.position.cpy()
            repeat(120) { physics.step() }
            assertEquals(settledPosition, ball.position)
            assertEquals(1, events.count { it == PhysicsEvent.Settled })
        } finally { physics.dispose() }
    }
    @Test fun `settled bodies wake for another strike and object ball collision`() {
        val physics = PoolPhysics()
        try {
            physics.reset(listOf(0 to Point(0f, 0.4f), 1 to Point(0f, 0.1f)))
            physics.strike(ShotCommand(Point(0f, -1f), 0.0001f))
            repeat(120) { physics.step() }
            assertTrue(physics.balls.none { it.body.isActive })
            physics.strike(ShotCommand(Point(0f, -1f), 0.17f))
            repeat(42) { physics.step() }
            assertTrue(physics.balls[1].body.linearVelocity.z < -0.4f)
        } finally { physics.dispose() }
    }
    @Test fun `repeated reset retains rack without duplicate bodies`() = physics { p ->
        repeat(20) { p.reset(); assertEquals(16, p.balls.size) }
        assertEquals(16, p.balls.map { it.id }.toSet().size)
        for (a in p.balls) for (b in p.balls) if (a.id != b.id) {
            assertTrue(a.position.dst(b.position) >= TableGeometry.BALL_RADIUS * 2f)
        }
    }
    @Test fun `frame partition does not change fixed timestep outcome`() {
        val a = PoolPhysics(); val b = PoolPhysics()
        try {
            a.reset(listOf(0 to Point(0f, 0.4f))); b.reset(listOf(0 to Point(0f, 0.4f)))
            val shot = ShotCommand(Point(0f, -1f), 0.1f)
            a.strike(shot); b.strike(shot)
            repeat(120) { a.advance(PoolPhysics.STEP) }
            repeat(60) { b.advance(PoolPhysics.STEP * 2) }
            assertEquals(a.balls[0].position.z, b.balls[0].position.z, 0.0001f)
        } finally { a.dispose(); b.dispose() }
    }
}
