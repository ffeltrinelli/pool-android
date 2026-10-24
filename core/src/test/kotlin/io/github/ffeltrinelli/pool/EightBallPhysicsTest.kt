package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import io.github.ffeltrinelli.pool.opponent.*
import org.junit.Assert.*
import org.junit.Test

class EightBallPhysicsTest {
    @Test fun `native contact facts identify first ball and later cushion independently of frame partition`() {
        fun simulate(partition: Int): ShotFacts {
            val facts = ShotFacts()
            val world = PoolPhysics(events = facts::accept)
            try {
                world.reset(listOf(0 to Point(0f, 0.4f), 1 to Point(0f, 0f)))
                world.strike(ShotCommand(Point(0f, -1f), 0.3f))
                repeat(2400 / partition) { world.advance(PoolPhysics.STEP * partition) }
                assertEquals(setOf(1), facts.firstContacts)
                assertTrue(facts.railAfterContact || facts.pocketed.isNotEmpty())
                return facts
            } finally { world.dispose() }
        }
        val a = simulate(1); val b = simulate(4)
        assertEquals(a.firstContacts, b.firstContacts)
        assertEquals(a.railBalls, b.railBalls)
        assertEquals(a.pocketed, b.pocketed)
    }
    @Test fun `placement and spotting preserve every other ball transform`() {
        val world = PoolPhysics()
        try {
            world.reset(listOf(1 to Point(0f, TableGeometry.FOOT_SPOT), 9 to Point(0.2f, 0f)))
            val before = world.snapshot().balls
            world.placeBall(0, TableGeometry.CUE_START)
            world.spotBall(8)
            assertEquals(before, world.snapshot().balls.filter { it.id in setOf(1, 9) })
            assertTrue(world.balls.first { it.id == 8 }.point.z < TableGeometry.FOOT_SPOT)
            repeat(5) { world.placeBall(0, Point(0.1f, 0.7f)); world.spotBall(8) }
            assertEquals(4, world.balls.size)
            assertEquals(1, world.balls.count { it.id == 0 })
            world.reset()
            assertEquals(16, world.balls.size)
        } finally { world.dispose() }
    }
    @Test fun `off table removal is distinct from pocket and silent`() {
        val events = mutableListOf<PhysicsEvent>()
        val drops = mutableListOf<PocketDrop>()
        val world = PoolPhysics(events::add, pocketDrops = drops::add)
        try {
            world.reset(listOf(8 to Point(0.9f, 0f)))
            world.step()
            assertEquals(listOf(PhysicsEvent.OffTable(8)), events.filterIsInstance<PhysicsEvent.OffTable>())
            assertTrue(events.filterIsInstance<PhysicsEvent.Pocketed>().isEmpty())
            assertTrue(drops.isEmpty())
        } finally { world.dispose() }
    }
    @Test fun `computer targets only own group and searches legal placement within budget`() {
        val snapshot = TableSnapshot(listOf(BallSnapshot(1, Point(0.3f, 0f)), BallSnapshot(9, Point(0f, -0.3f)),
            BallSnapshot(8, Point(-0.3f, 0f))), RulesContext(group = BallGroup.SOLIDS), PlacementRegion.ANYWHERE)
        val planner = BulletShotPlanner(maxEvaluations = 8, deadlineMillis = Long.MAX_VALUE)
        try {
            val result = planner.plan(snapshot, 1) { false }
            assertNotNull(result.cuePosition)
            assertNull(BallPlacement.invalidReason(result.cuePosition!!, snapshot.balls.map { it.point }, PlacementRegion.ANYWHERE))
            assertTrue(result.evaluations in 1..8)
            val table = snapshot.withCue(result.cuePosition!!)
            assertTrue(ShotCandidates().generate(table).isNotEmpty())
            assertEquals(Point(0.3f, 0f), table.balls.first { it.id == 1 }.point)
        } finally { planner.close() }
    }
    @Test fun `open table can legally target eight when one entire group is gone`() {
        val context = RulesContext(remaining = (8..15).toSet())
        assertTrue(context.legalFirst(setOf(8)))
        val facts = ShotFacts().apply {
            accept(PhysicsEvent.Contacts(setOf(8), emptySet()))
            accept(PhysicsEvent.Pocketed(8))
        }
        assertTrue(EightBallRules.resolve(context, facts).won)
    }
}
