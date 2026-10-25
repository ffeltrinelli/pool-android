package io.github.ffeltrinelli.pool

import com.badlogic.gdx.utils.JsonReader
import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class DirectRequestPlanningTest {
    @Test fun `captured request finds and independently executes its direct rail pot`() {
        val table = capturedTable()
        val candidate = ShotCandidates().generate(table).first { it.targetBallId == 4 }
        val planner = BulletShotPlanner(64, Long.MAX_VALUE)
        val facts = ShotFacts()
        var settled = false
        val world = PoolPhysics(events = { facts.accept(it); if (it == PhysicsEvent.Settled) settled = true })
        try {
            val result = planner.plan(table, 4) { false }
            assertEquals(ShotKind.DIRECT_POT, candidate.kind)
            assertEquals(ShotKind.DIRECT_POT, result.kind)
            assertEquals(4, result.targetBallId)
            assertEquals(0, result.metrics.reboundAttempts)
            assertNull(result.predictedFoul)
            assertTrue(4 in result.predictedPockets)
            world.restore(table)
            world.strike(result.shot)
            repeat(3600) { if (!settled) world.step() }
            val actual = EightBallRules.resolve(table.rulesContext(), facts)
            assertTrue(settled)
            assertEquals(setOf(4), facts.firstContacts)
            assertTrue(4 in facts.pocketed)
            assertNull(actual.foul)
            assertNull(actual.loss)
        } finally { world.dispose(); planner.close() }
    }

    @Test fun `rail routes are mirrored and still reject an object blocking the pocket lane`() {
        val original = capturedTable()
        for (x in listOf(-1f, 1f)) for (z in listOf(-1f, 1f)) {
            val table = original.copy(balls = original.balls.map { it.copy(point = Point(it.point.x * x, it.point.z * z)) })
            val rail = table.balls.first { it.id == 4 }.point
            val candidate = ShotCandidates().generate(table).first { it.targetBallId == 4 }
            val blocker = BallSnapshot(6, rail + Point(0f, -0.09f * z))
            val blocked = table.copy(balls = table.balls + blocker)
            assertTrue(ShotCandidates().generate(blocked).none { it.targetBallId == 4 })
            val predicted = AimPredictor().predict(table.balls.first { it.id == 0 }.point, candidate.shot.direction,
                table.balls.map { AimBall(it.id, it.point) })
            assertEquals(4, predicted.targetBallId)
        }
    }

    @Test fun `pocket crossing uses the ray intersection rather than distance to mouth center`() {
        val pocket = PocketGeometry.pockets.first()
        val ball = capturedTable().balls.first { it.id == 4 }.point
        val distance = DirectPocketRoutes.mouthDistance(ball, Point(0f, -1f), pocket)
        assertNotNull(distance)
        assertTrue(distance!! + 0.005f < (pocket.mouthCenter - ball).length())
        assertNull(DirectPocketRoutes.mouthDistance(ball, Point(0f, 1f), pocket))
        assertNull(DirectPocketRoutes.mouthDistance(Point(0f, 0f), Point(0f, -1f), pocket))
    }

    companion object {
        fun capturedTable(): TableSnapshot {
            val root = DirectRequestPlanningTest::class.java.getResourceAsStream("/opponent/missed-direct-request.json")!!.use { JsonReader().parse(it) }
            val balls = root.get("balls").map { b -> BallSnapshot(b.getInt("id"), Point(b.getFloat("x"), b.getFloat("z")),
                b.getFloat("height"), b.getFloat("qx"), b.getFloat("qy"), b.getFloat("qz"), b.getFloat("qw")) }
            return TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(), BallGroup.SOLIDS))
        }
    }
}
