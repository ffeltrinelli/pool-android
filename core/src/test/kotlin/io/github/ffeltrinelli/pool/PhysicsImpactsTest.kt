package io.github.ffeltrinelli.pool

import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import org.junit.Assert.*
import org.junit.Test

class PhysicsImpactsTest {
    private fun withPhysics(test: (PoolPhysics, MutableList<Impact>) -> Unit) {
        val events = mutableListOf<Impact>()
        val physics = PoolPhysics(impacts = events::add)
        try { test(physics, events) } finally { physics.dispose() }
    }

    @Test fun `head on collision emits one ball hit with solver impulse`() = withPhysics { p, events ->
        p.reset(listOf(0 to Point(0f, 0.4f), 1 to Point(0f, 0.1f)))
        p.strike(ShotCommand(Point(0f, -1f), 0.17f))
        repeat(50) { p.step() }
        val hit = events.filter { it.kind == ImpactKind.BALL }.single()
        assertTrue(hit.impulse in 0.05f..0.3f)
        assertEquals(1, events.count { it.kind == ImpactKind.CUE })
        assertEquals(0.17f, events.first().impulse, 0f)
    }

    @Test fun `stronger collision has larger solver impulse`() {
        fun hit(power: Float): Float {
            val events = mutableListOf<Impact>()
            val p = PoolPhysics(impacts = events::add)
            try {
                p.reset(listOf(0 to Point(0f, 0.2f), 1 to Point(0f, 0.1f)))
                p.strike(ShotCommand(Point(0f, -1f), power))
                repeat(40) { p.step() }
                return events.first { it.kind == ImpactKind.BALL }.impulse
            } finally { p.dispose() }
        }
        assertTrue(hit(0.3f) > hit(0.1f) * 2)
    }

    @Test fun `cushion can sound again after separation and after restart`() = withPhysics { p, events ->
        fun approach() {
            p.balls.single().body.worldTransform = com.badlogic.gdx.math.Matrix4().setToTranslation(0.45f, TableGeometry.BALL_RADIUS, 0.4f)
            p.balls.single().body.linearVelocity = Vector3.Zero
            p.strike(ShotCommand(Point(1f, 0f), 0.17f))
            repeat(35) { p.step() }
        }
        p.reset(listOf(0 to Point(0.45f, 0.4f)))
        approach(); approach()
        assertEquals(2, events.count { it.kind == ImpactKind.CUSHION })
        p.reset(listOf(0 to Point(0.45f, 0.4f)))
        approach()
        assertEquals(3, events.count { it.kind == ImpactKind.CUSHION })
    }

    @Test fun `stationary rack and pocket drops do not emit impact sounds`() = withPhysics { p, events ->
        repeat(120) { p.step() }
        assertTrue(events.isEmpty())
        p.reset(listOf(0 to Point(TableGeometry.HALF_WIDTH + 0.02f, 0f)))
        p.strike(ShotCommand(Point(1f, 0f), 0.1f))
        repeat(120) { p.step() }
        assertTrue(p.balls.single().pocketed)
        assertEquals(listOf(ImpactKind.CUE), events.map { it.kind })
        p.strike(ShotCommand(Point(1f, 0f), 0.1f))
        assertEquals(1, events.size)
    }

    @Test fun `sustained native contact emits once instead of buzzing`() = withPhysics { p, events ->
        p.reset(listOf(0 to Point(0f, 0.16f), 1 to Point(0f, 0.1f)))
        p.balls.forEach { it.body.restitution = 0f }
        p.balls[1].body.linearFactor = Vector3.Zero
        repeat(120) {
            p.balls[0].body.activate()
            p.balls[0].body.linearVelocity = Vector3(0f, 0f, -0.1f)
            p.step()
        }
        assertEquals(1, events.count { it.kind == ImpactKind.BALL })
    }

    @Test fun `cancelled or zero power stroke makes no cue sound`() = withPhysics { p, events ->
        p.strike(ShotCommand(Point(0f, -1f), 0f))
        p.strike(ShotCommand(Point(0f, 0f), 0.1f))
        assertTrue(events.isEmpty())
    }
}
