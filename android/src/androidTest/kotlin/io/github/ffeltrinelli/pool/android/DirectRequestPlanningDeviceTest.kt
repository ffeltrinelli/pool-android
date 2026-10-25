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

class DirectRequestPlanningDeviceTest {
    @Test fun capturedRequestUsesTheDirectRailPot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.context.assets.open("opponent/missed-direct-request.json").use { JsonReader().parse(it) }
        val balls = root.get("balls").map { b -> BallSnapshot(b.getInt("id"), Point(b.getFloat("x"), b.getFloat("z")),
            b.getFloat("height"), b.getFloat("qx"), b.getFloat("qy"), b.getFloat("qz"), b.getFloat("qw")) }
        val table = TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(),
            BallGroup.valueOf(root.getString("group"))))
        val planner = BulletShotPlanner()
        val facts = ShotFacts()
        var settled = false
        val world = PoolPhysics(events = { facts.accept(it); if (it == PhysicsEvent.Settled) settled = true })
        try {
            val result = planner.plan(table, 4) { false }
            world.restore(table)
            world.strike(result.shot)
            repeat(3600) { if (!settled) world.step() }
            val actual = EightBallRules.resolve(table.rulesContext(), facts)
            File(instrumentation.targetContext.filesDir, "direct-request-planning.txt").writeText("$result\nactual=$actual first=${facts.firstContacts} pots=${facts.pocketed}\n")
            assertEquals("$result", ShotKind.DIRECT_POT, result.kind)
            assertEquals(4, result.targetBallId)
            assertNull(result.predictedFoul)
            assertEquals(0, result.metrics.reboundAttempts)
            assertTrue("Deadline overrun: $result", result.elapsedMillis <= 5100)
            assertTrue(result.targetBallId in result.predictedPockets)
            assertTrue("actual=$actual pockets=${facts.pocketed}", result.targetBallId in facts.pocketed)
            assertNull(actual.foul)
            assertNull(actual.loss)
        } finally { world.dispose(); planner.close() }
    }
}
