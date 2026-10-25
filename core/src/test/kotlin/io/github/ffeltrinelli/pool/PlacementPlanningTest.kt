package io.github.ffeltrinelli.pool

import com.badlogic.gdx.utils.JsonReader
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class PlacementPlanningTest {
    @Test fun `ball in hand plans an intended direct pot on the captured post-miss table`() {
        val table = postMissTable()
        val planner = BulletShotPlanner(64, Long.MAX_VALUE)
        try {
            val result = planner.plan(table, 3) { false }
            assertEquals(ShotKind.DIRECT_POT, result.kind)
            assertNull(result.predictedFoul)
            assertTrue(result.targetBallId in result.predictedPockets)
            assertEquals(0, result.metrics.reboundAttempts)
            val facts = ShotFacts()
            var settled = false
            val world = PoolPhysics(events = { facts.accept(it); if (it == PhysicsEvent.Settled) settled = true })
            try {
                world.restore(table.withCue(result.cuePosition!!))
                world.strike(result.shot)
                repeat(3600) { if (!settled) world.step() }
                val actual = EightBallRules.resolve(table.rulesContext(), facts)
                assertTrue("actual=$actual pockets=${facts.pocketed}", result.targetBallId in facts.pocketed)
                assertNull(actual.foul)
                assertNull(actual.loss)
            } finally { world.dispose() }
        } finally { planner.close() }
    }

    @Test fun `placement shortlist rejects blocked pot lanes and covers different legal targets`() {
        val balls = listOf(BallSnapshot(1, Point(0f, 0f)), BallSnapshot(2, Point(0.3f, 0.5f)),
            BallSnapshot(9, Point(-0.075f, 0f)), BallSnapshot(10, Point(0.075f, 0f)),
            BallSnapshot(11, Point(0f, -0.075f)), BallSnapshot(12, Point(0f, 0.075f)),
            BallSnapshot(7, Point(-0.3f, -0.6f)), BallSnapshot(8, Point(0.3f, -0.7f)))
        val table = TableSnapshot(balls, RulesContext(balls.map { it.id }.toSet(), BallGroup.SOLIDS), PlacementRegion.ANYWHERE)
        val positions = PlanningPositions.shortlist(table)
        val intended = positions.mapNotNull { it.directPot?.targetBallId }.toSet()
        assertEquals(setOf(2, 7), intended)
        assertTrue(positions.all { position ->
            val candidate = position.directPot ?: return@all true
            ShotCandidates().generate(table.withCue(position.point)).any { it == candidate }
        })
        assertTrue(positions.all { BallPlacement.invalidReason(it.point, balls.map { it.point }, PlacementRegion.ANYWHERE) == null })
    }

    @Test fun `placement generation observes cancellation before searching its routes`() {
        try {
            PlanningPositions.shortlist(postMissTable()) { throw java.util.concurrent.CancellationException() }
            fail("Expected cancellation while generating placements")
        } catch (_: java.util.concurrent.CancellationException) { }
    }

    companion object {
        fun postMissTable(): TableSnapshot {
            val root = PlacementPlanningTest::class.java.getResourceAsStream("/opponent/ball-in-hand-post-miss.json")!!.use { JsonReader().parse(it) }
            val balls = root.get("balls").map { b -> BallSnapshot(b.getInt("id"), Point(b.getFloat("x"), b.getFloat("z")),
                b.getFloat("height"), b.getFloat("qx"), b.getFloat("qy"), b.getFloat("qz"), b.getFloat("qw")) }
            return TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(), BallGroup.valueOf(root.getString("group"))), PlacementRegion.ANYWHERE)
        }
    }
}
