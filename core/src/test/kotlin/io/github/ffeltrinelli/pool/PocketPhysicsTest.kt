package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PocketPhysicsTest {
    private fun withPhysics(test: (PoolPhysics, MutableList<PhysicsEvent>, MutableList<PocketDrop>, MutableList<Impact>) -> Unit) {
        val events = mutableListOf<PhysicsEvent>()
        val drops = mutableListOf<PocketDrop>()
        val impacts = mutableListOf<Impact>()
        val physics = PoolPhysics(events::add, impacts::add, drops::add)
        try { test(physics, events, drops, impacts) } finally { physics.dispose() }
    }

    @Test fun `standard mouths shelves and mirrored jaw cuts share six cushion boundaries`() {
        assertEquals(6, PocketGeometry.pockets.size)
        assertEquals(6, PocketGeometry.cushions.size)
        PocketGeometry.pockets.forEach { pocket ->
            assertEquals(pocket.width, (pocket.mouth(1f) - pocket.mouth(-1f)).length(), 0.00001f)
            assertEquals(if (pocket.corner) 0.1143f else 0.127f, pocket.width, 0f)
            val nearest = (pocket.center - pocket.mouthCenter).length() - pocket.radius
            assertEquals(pocket.shelf, nearest, 0.00001f)
            for (side in listOf(-1f, 1f)) {
                assertTrue(PocketGeometry.cushions.any { cushion -> cushion.outline.any { (it - pocket.mouth(side)).length() < 0.00001f } })
                assertTrue(PocketGeometry.cushions.any { cushion -> cushion.outline.any { (it - pocket.jawBack(side)).length() < 0.00001f } })
            }
        }
        val removedArea = PocketGeometry.pockets.sumOf { PocketGeometry.area(it.opening).toDouble() }.toFloat()
        assertEquals(1.46f * 2.58f - removedArea, PocketGeometry.support.sumOf { PocketGeometry.area(it).toDouble() }.toFloat(), 0.0001f)
    }

    @Test fun `soft and angled entries drop physically into all six pockets exactly once`() {
        for (pocket in PocketGeometry.pockets) for (angle in listOf(0f, -0.15f, 0.15f)) withPhysics { p, events, drops, _ ->
            val direction = (pocket.outward + pocket.lateral * angle).normalized()
            val start = pocket.mouthCenter - direction * 0.14f
            p.reset(listOf(0 to start))
            p.strike(ShotCommand(direction, 0.14f))
            var descending = false
            var rotated = false
            val ball = p.balls.single()
            val initial = ball.rotation.cpy()
            repeat(240) {
                p.step()
                if (!ball.pocketed && ball.position.y < 0f) descending = true
                if (abs(ball.rotation.dot(initial)) < 0.99f) rotated = true
            }
            assertTrue("Entry ${pocket.id}, angle $angle at ${ball.position}", ball.pocketed)
            assertTrue(descending)
            assertTrue(rotated)
            assertTrue(ball.position.y < PocketGeometry.CAPTURE_DEPTH)
            assertEquals(listOf(PocketDrop(0, pocket.id)), drops)
            assertEquals(1, events.count { it == PhysicsEvent.Pocketed(0) })
            assertEquals(1, events.count { it == PhysicsEvent.Settled })
        }
    }

    @Test fun `stationary shelf balls stay supported without capture or pocket audio`() {
        for (pocket in PocketGeometry.pockets) withPhysics { p, _, drops, impacts ->
            p.reset(listOf(1 to (pocket.mouthCenter - pocket.outward * 0.004f)))
            repeat(180) { p.step() }
            assertFalse(p.balls.single().pocketed)
            assertEquals(TableGeometry.BALL_RADIUS, p.balls.single().position.y, 0.001f)
            assertTrue(drops.isEmpty())
            assertTrue(impacts.isEmpty())
        }
    }

    @Test fun `jaw misses rebound and do not count as pockets`() {
        for (pocket in PocketGeometry.pockets) withPhysics { p, _, drops, impacts ->
            val start = pocket.mouth(1f) + pocket.lateral * 0.018f - pocket.outward * 0.12f
            p.reset(listOf(0 to start))
            p.strike(ShotCommand(pocket.outward, 0.12f))
            var rebounded = false
            repeat(100) {
                p.step()
                val v = p.balls.single().body.linearVelocity
                if (Point(v.x, v.z).dot(pocket.outward) < -0.03f || abs(Point(v.x, v.z).dot(pocket.lateral)) > 0.15f) rebounded = true
            }
            assertTrue("Jaw ${pocket.id}: ${p.balls.single().position}, ${p.balls.single().body.linearVelocity}, $impacts", rebounded)
            assertFalse(p.balls.single().pocketed)
            assertTrue(drops.isEmpty())
            assertTrue(impacts.any { it.kind == ImpactKind.CUSHION })
        }
    }

    @Test fun `interior drop is silent until confirmed capture and aiming remains locked`() = withPhysics { p, events, drops, impacts ->
        val pocket = PocketGeometry.pockets.last()
        p.reset(listOf(0 to pocket.center))
        p.strike(ShotCommand(pocket.outward, 0.0001f))
        impacts.clear()
        repeat(8) { p.step() }
        assertTrue(p.balls.single().position.y < TableGeometry.BALL_RADIUS)
        assertFalse(p.balls.single().pocketed)
        assertTrue(events.isEmpty())
        assertTrue(drops.isEmpty())
        repeat(100) { p.step() }
        assertEquals(listOf(PocketDrop(0, pocket.id)), drops)
        assertTrue(impacts.isEmpty())
    }

    @Test fun `maximum power is contained by pocket jaws or liner without fallback capture`() {
        for (pocket in PocketGeometry.pockets) withPhysics { p, _, drops, _ ->
            p.reset(listOf(0 to (pocket.mouthCenter - pocket.outward * 0.18f)))
            p.strike(ShotCommand(pocket.outward, 1.15f))
            repeat(240) { p.step() }
            val ball = p.balls.single()
            if (ball.pocketed) assertEquals("${ball.position}", listOf(PocketDrop(0, pocket.id)), drops)
            assertTrue(abs(ball.position.x) < 0.80f && abs(ball.position.z) < 1.36f)
        }
    }

    @Test fun `simultaneous drops preserve scratch precedence and reset removes falling bodies`() = withPhysics { p, _, drops, _ ->
        val session = GameSession()
        val q = PoolPhysics(session::accept, pocketDrops = drops::add)
        try {
            q.reset(listOf(0 to PocketGeometry.pockets[0].center, 1 to PocketGeometry.pockets[1].center))
            session.beginShot()
            (2..15).forEach { session.accept(PhysicsEvent.Pocketed(it)) }
            q.strike(ShotCommand(Point(0f, -1f), 0.0001f))
            repeat(180) { q.step() }
            assertEquals(RoundState.SCRATCHED, session.state)
            assertEquals(setOf(0, 1), drops.map { it.ballId }.toSet())
        } finally { q.dispose() }
        repeat(10) {
            p.reset(listOf(0 to PocketGeometry.pockets[0].center))
            repeat(8) { p.step() }
            assertFalse(p.balls.single().pocketed)
            p.reset()
            repeat(100) { p.step() }
            assertEquals(16, p.balls.count { !it.pocketed })
        }
    }

    @Test fun `flat support seams do not cause jumps or significant sideways deflection`() = withPhysics { p, _, _, _ ->
        p.reset(listOf(0 to Point(0.40f, 0.60f)))
        p.strike(ShotCommand(Point(1f, 0f), 0.10f))
        repeat(100) {
            p.step()
            val ball = p.balls.single()
            assertEquals(TableGeometry.BALL_RADIUS, ball.position.y, 0.001f)
            assertEquals(0.60f, ball.position.z, 0.002f)
        }
    }
    @Test fun `pocket drops agree across render frame partitions`() {
        val first = mutableListOf<PocketDrop>()
        val second = mutableListOf<PocketDrop>()
        val a = PoolPhysics(pocketDrops = first::add)
        val b = PoolPhysics(pocketDrops = second::add)
        try {
            val pocket = PocketGeometry.pockets[3]
            val start = pocket.mouthCenter - pocket.outward * 0.14f
            listOf(a, b).forEach { it.reset(listOf(0 to start)); it.strike(ShotCommand(pocket.outward, 0.14f)) }
            repeat(240) { a.advance(PoolPhysics.STEP) }
            repeat(120) { b.advance(PoolPhysics.STEP * 2f) }
            assertEquals(listOf(PocketDrop(0, 3)), first)
            assertEquals(first, second)
            assertEquals(a.balls.single().position, b.balls.single().position)
            assertEquals(a.balls.single().rotation, b.balls.single().rotation)
        } finally { a.dispose(); b.dispose() }
    }

}
