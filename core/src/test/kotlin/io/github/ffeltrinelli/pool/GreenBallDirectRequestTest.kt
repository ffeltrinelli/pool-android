package io.github.ffeltrinelli.pool

import com.badlogic.gdx.utils.JsonReader
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class GreenBallDirectRequestTest {
    @Test fun `captured green ball gets a direct mouth-entry pot instead of a firm contact`() {
        val table = capturedTable()
        val planner = BulletShotPlanner(64, Long.MAX_VALUE)
        val facts = ShotFacts()
        var settled = false
        var targetContact = false
        val railPositions = mutableListOf<Point>()
        val world = PoolPhysics(events = {
            if (it is PhysicsEvent.Contacts && 6 in it.railBalls) targetContact = true
            facts.accept(it)
            if (it == PhysicsEvent.Settled) settled = true
        })
        try {
            val result = planner.plan(table, 2) { false }
            assertEquals(ShotKind.DIRECT_POT, result.kind)
            assertEquals(6, result.targetBallId)
            assertEquals(0, result.metrics.reboundAttempts)
            assertNull(result.predictedFoul)
            world.restore(table)
            world.strike(result.shot)
            repeat(3600) {
                if (!settled) {
                    world.step()
                    if (targetContact) {
                        railPositions += world.balls.first { it.id == 6 }.point
                        targetContact = false
                    }
                }
            }
            val actual = EightBallRules.resolve(table.rulesContext(), facts)
            assertTrue(settled)
            assertEquals(setOf(6), facts.firstContacts)
            assertTrue(6 in facts.pocketed)
            assertNull(actual.foul)
            assertNull(actual.loss)
            val pocket = PocketGeometry.pockets[1]
            assertTrue("Target bounced before reaching the pocket: $railPositions", railPositions.all {
                (it - pocket.mouthCenter).dot(pocket.outward) >= -TableGeometry.BALL_RADIUS
            })
        } finally { world.dispose(); planner.close() }
    }

    @Test fun `oblique mouth entries work in every corner and still reject a blocked ball lane`() {
        val original = capturedTable()
        for (x in listOf(-1f, 1f)) for (z in listOf(-1f, 1f)) {
            val table = original.copy(balls = original.balls.map { it.copy(point = Point(it.point.x * x, it.point.z * z)) })
            assertTrue(ShotCandidates().generate(table).any { it.targetBallId == 6 })
            val ball = table.balls.first { it.id == 6 }.point
            val entry = Point(PocketGeometry.pockets[1].mouthCenter.x * x, PocketGeometry.pockets[1].mouthCenter.z * z)
            val blocker = BallSnapshot(13, ball + (entry - ball).normalized() * 0.3f)
            assertTrue(ShotCandidates().generate(table.copy(balls = table.balls + blocker)).none { it.targetBallId == 6 })
        }
    }

    companion object {
        fun capturedTable(): TableSnapshot {
            val root = GreenBallDirectRequestTest::class.java.getResourceAsStream("/opponent/green-ball-direct-request.json")!!.use { JsonReader().parse(it) }
            val balls = root.get("balls").map { b -> BallSnapshot(b.getInt("id"), Point(b.getFloat("x"), b.getFloat("z")),
                b.getFloat("height"), b.getFloat("qx"), b.getFloat("qy"), b.getFloat("qz"), b.getFloat("qw")) }
            return TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(), BallGroup.SOLIDS))
        }
    }
}
