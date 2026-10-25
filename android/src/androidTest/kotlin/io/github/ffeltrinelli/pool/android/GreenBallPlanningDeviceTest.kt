package io.github.ffeltrinelli.pool.android

import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.utils.JsonReader
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GreenBallPlanningDeviceTest {
    @Test fun capturedRequestPotsGreenBallThroughThePocketMouth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.context.assets.open("opponent/green-ball-direct-request.json").use { JsonReader().parse(it) }
        val balls = root.get("balls").map { b -> BallSnapshot(b.getInt("id"), Point(b.getFloat("x"), b.getFloat("z")),
            b.getFloat("height"), b.getFloat("qx"), b.getFloat("qy"), b.getFloat("qz"), b.getFloat("qw")) }
        val table = TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(),
            BallGroup.valueOf(root.getString("group"))))
        val planner = BulletShotPlanner()
        val facts = ShotFacts()
        var cueRailsBeforeContact = 0
        var settled = false
        var targetContact = false
        val targetRailPositions = mutableListOf<Point>()
        val world = PoolPhysics(events = {
            if (it is PhysicsEvent.Contacts) {
                if (6 in it.railBalls) targetContact = true
                if (facts.firstContacts.isEmpty() && it.cueTargets.isEmpty() && 0 in it.railBalls) cueRailsBeforeContact++
            }
            facts.accept(it)
            if (it == PhysicsEvent.Settled) settled = true
        })
        try {
            val result = planner.plan(table, 2) { false }
            world.restore(table)
            world.strike(result.shot)
            repeat(3600) {
                if (!settled) {
                    world.step()
                    if (targetContact) {
                        targetRailPositions += world.balls.first { it.id == 6 }.point
                        targetContact = false
                    }
                }
            }
            val actual = EightBallRules.resolve(table.rulesContext(), facts)
            File(instrumentation.targetContext.filesDir, "green-ball-planning.txt").writeText("$result\nactual=$actual first=${facts.firstContacts} pots=${facts.pocketed} targetRailPositions=$targetRailPositions\n")
            assertEquals("$result", ShotKind.DIRECT_POT, result.kind)
            assertEquals(6, result.targetBallId)
            assertNull(result.predictedFoul)
            assertEquals(0, result.metrics.reboundAttempts)
            assertTrue("Deadline overrun: $result", result.elapsedMillis <= 5100)
            assertTrue(result.targetBallId in result.predictedPockets)
            assertTrue("actual=$actual pockets=${facts.pocketed}", result.targetBallId in facts.pocketed)
            assertNull(actual.foul)
            assertNull(actual.loss)
            assertEquals(0, cueRailsBeforeContact)
            val pocket = PocketGeometry.pockets[1]
            assertTrue("Early target cushion contacts: $targetRailPositions", targetRailPositions.all {
                (it - pocket.mouthCenter).dot(pocket.outward) >= -TableGeometry.BALL_RADIUS
            })
        } finally { world.dispose(); planner.close() }
    }
}
