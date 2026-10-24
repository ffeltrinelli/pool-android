package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class ComputerLegalContactTest {
    @Test fun `legal contact alternative beats a shot that stops before reaching a cushion`() {
        val balls = listOf(BallSnapshot(0, Point(0f, 0.3f)), BallSnapshot(9, Point(0f, 0f)),
            BallSnapshot(8, Point(0.3f, -0.7f)))
        val table = TableSnapshot(balls, RulesContext(setOf(9, 8), BallGroup.STRIPES))
        val weak = ShotCommand(Point(0f, -1f), 0.08f)
        val simulation = ShotSimulation()
        try {
            assertEquals(FoulReason.NO_RAIL, simulation.evaluate(table, weak) {}?.foul)
            val contact = ShotCandidates().contactAlternatives(table).first {
                simulation.evaluate(table, it) {}?.let { evaluation -> evaluation.foul == null && !evaluation.lost } == true
            }
            val search = ShotSearch(table, weak, simulation, 2, Long.MAX_VALUE, { false })
            search.evaluate(weak)
            search.evaluate(contact)
            assertEquals(contact, search.result().shot)
            assertNull(search.result().predictedFoul)
            assertEquals(2, search.result().evaluations)
        } finally { simulation.close() }
    }

    @Test fun `ordinary planning keeps the settled cue pose and body order`() {
        val cue = BallSnapshot(0, Point(0f, 0.3f), height = 0.0281f, qx = 0f, qy = 1f, qz = 0f, qw = 0f)
        val table = TableSnapshot(listOf(cue, BallSnapshot(9, Point(0f, 0f))))
        assertSame(table, table.withCue(cue.point))
        val placed = table.copy(placement = PlacementRegion.ANYWHERE).withCue(cue.point)
        assertEquals(listOf(9, 0), placed.balls.map { it.id })
        assertEquals(TableGeometry.BALL_RADIUS, placed.balls.last().height, 0f)
    }

    @Test fun `contact alternatives target visible legal balls only`() {
        val table = TableSnapshot(listOf(BallSnapshot(0, Point(0f, 0.3f)),
            BallSnapshot(1, Point(0f, 0.15f)), BallSnapshot(9, Point(0f, 0f)),
            BallSnapshot(10, Point(0.3f, 0.3f))), RulesContext(setOf(1, 9, 10), BallGroup.STRIPES))
        val shots = ShotCandidates().contactAlternatives(table)
        assertEquals(2, shots.size)
        assertTrue(shots.all { it.direction.x > 0.999f && it.impulse <= OpponentTuning.MAX_IMPULSE })
        assertTrue(ShotCandidates().contactAlternatives(table.copy(rules = table.rules!!.copy(breaking = true))).isEmpty())
    }

    @Test fun `hard chooses a legal contact when direct pots are blocked`() {
        val balls = listOf(
            BallSnapshot(0, Point(0f, 0.3f)), BallSnapshot(9, Point(0f, 0f)),
            BallSnapshot(1, Point(-0.075f, 0f)), BallSnapshot(2, Point(0.075f, 0f)),
            BallSnapshot(3, Point(0f, -0.075f)), BallSnapshot(8, Point(0.3f, -0.7f))
        )
        val table = TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(), BallGroup.STRIPES))
        val planner = BulletShotPlanner(maxEvaluations = 16, deadlineMillis = Long.MAX_VALUE)
        val facts = ShotFacts()
        val world = PoolPhysics(events = facts::accept)
        try {
            assertTrue("Fixture must require a contact alternative", ShotCandidates().generate(table).isEmpty())
            val result = planner.plan(table, 17) { false }
            world.restore(table)
            world.strike(ExecutionAccuracy.apply(result.shot, OpponentDifficulty.HARD, 17))
            repeat(3600) { world.step() }
            val decision = EightBallRules.resolve(table.rulesContext(), facts)
            assertNull("shot=${result.shot}, contacts=${facts.firstContacts}, rails=${facts.railBalls}, pockets=${facts.pocketed}", decision.foul)
            assertNull(decision.loss)
        } finally { world.dispose(); planner.close() }
    }
}
