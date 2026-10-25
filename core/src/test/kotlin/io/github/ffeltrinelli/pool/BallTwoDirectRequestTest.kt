package io.github.ffeltrinelli.pool

import com.badlogic.gdx.utils.JsonReader
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class BallTwoDirectRequestTest {
    @Test fun `captured ball two jaw route is tested before rebounds and pots directly`() {
        val table = capturedTable()
        val candidate = ShotCandidates().generate(table).first { it.targetBallId == 2 }
        val planner = BulletShotPlanner(64, Long.MAX_VALUE)
        val simulation = ShotSimulation()
        try {
            val result = planner.plan(table, 11) { false }
            assertEquals(ShotKind.DIRECT_POT, result.kind)
            assertEquals(2, result.targetBallId)
            assertEquals(0, result.metrics.reboundAttempts)
            assertNull(result.predictedFoul)
            val actual = simulation.evaluate(table, result.shot) {}!!
            assertEquals(setOf(2), actual.firstContacts)
            assertTrue(2 in actual.pocketedTargets)
            assertEquals(0, actual.cueRailsBeforeContact)
            assertNull(actual.foul)
            assertFalse(actual.lost)
            assertEquals(ShotKind.DIRECT_POT, candidate.kind)
        } finally { simulation.close(); planner.close() }
    }

    @Test fun `near jaw allowance does not admit a blocked object lane or a distant cushion`() {
        val table = capturedTable()
        val ball = table.balls.first { it.id == 2 }.point
        val pocket = PocketGeometry.pockets[1]
        val outgoing = (pocket.center - ball).normalized()
        val blocked = table.copy(balls = table.balls + BallSnapshot(13, ball + outgoing * 0.21f))
        assertTrue(ShotCandidates().generate(blocked).none { it.targetBallId == 2 })
        val distant = table.copy(balls = listOf(BallSnapshot(0, Point(0.3f, -0.2f)), BallSnapshot(2, Point(0.47f, -0.4f))),
            rules = RulesContext(setOf(2), BallGroup.SOLIDS))
        val distantBall = distant.balls.first { it.id == 2 }.point
        val direction = (pocket.center - distantBall).normalized()
        val ghost = distantBall - direction * (2 * TableGeometry.BALL_RADIUS)
        val rejected = (ghost - distant.balls.first { it.id == 0 }.point).normalized()
        assertTrue(ShotCandidates().generate(distant).none { it.targetBallId == 2 && it.shot.direction == rejected })
    }

    @Test fun `the captured ball two shot has a legal pot without any target cushion contact`() {
        val table = capturedTable()
        val ball = table.balls.first { it.id == 2 }.point
        val outgoing = (PocketGeometry.pockets[1].center - ball).normalized()
        val ghost = ball - outgoing * (2 * TableGeometry.BALL_RADIUS)
        val cue = table.balls.first { it.id == 0 }.point
        val facts = ShotFacts()
        var targetCushions = 0
        val world = PoolPhysics(events = {
            if (it is PhysicsEvent.Contacts && 2 in it.railBalls) targetCushions++
            facts.accept(it)
        })
        try {
            world.restore(table)
            world.strike(ShotCommand((ghost - cue).normalized(), 0.25f))
            repeat(3600) { world.step() }
            val actual = EightBallRules.resolve(table.rulesContext(), facts)
            assertTrue(2 in facts.pocketed)
            assertEquals(0, targetCushions)
            assertEquals(setOf(2), facts.firstContacts)
            assertNull(actual.loss)
            assertNull(actual.foul)
        } finally { world.dispose() }
    }

    companion object {
        fun capturedTable(): TableSnapshot {
            val root = BallTwoDirectRequestTest::class.java.getResourceAsStream("/opponent/ball-two-direct-request.json")!!.use { JsonReader().parse(it) }
            val balls = root.get("balls").map { b -> BallSnapshot(b.getInt("id"), Point(b.getFloat("x"), b.getFloat("z")),
                b.getFloat("height"), b.getFloat("qx"), b.getFloat("qy"), b.getFloat("qz"), b.getFloat("qw")) }
            return TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(), BallGroup.SOLIDS))
        }
    }
}
