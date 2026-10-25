package io.github.ffeltrinelli.pool.android

import androidx.test.platform.app.InstrumentationRegistry
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OpponentLegalityDeviceTest {
    @Test fun blockedStripeTargetsUseLegalKicksWithinTheProductionBudget() {
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "opponent-legality.txt")
        report.writeText("")
        val planner = BulletShotPlanner()
        try {
            for (blocker in listOf(1, 8)) {
                val balls = listOf(BallSnapshot(0, Point(0f, 0.3f)), BallSnapshot(9, Point(0f, 0f)),
                    BallSnapshot(blocker, Point(0f, 0.15f))) +
                    if (blocker == 1) listOf(BallSnapshot(8, Point(0.3f, -0.7f))) else emptyList()
                val table = TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet(), BallGroup.STRIPES))
                val result = planner.plan(table, 17) { false }
                val facts = ShotFacts()
                var cueRailsBeforeContact = 0
                var settled = false
                val world = PoolPhysics(events = {
                    if (it is PhysicsEvent.Contacts && facts.firstContacts.isEmpty() && it.cueTargets.isEmpty() && 0 in it.railBalls)
                        cueRailsBeforeContact++
                    facts.accept(it)
                    if (it == PhysicsEvent.Settled) settled = true
                })
                try {
                    world.restore(table)
                    world.strike(result.shot)
                    repeat(3600) { if (!settled) world.step() }
                    val decision = EightBallRules.resolve(table.rulesContext(), facts)
                    report.appendText("blocker=$blocker $result actual=$decision first=${facts.firstContacts} cueRails=$cueRailsBeforeContact\n")
                    assertFalse(result.usedFallback)
                    assertNull(result.predictedFoul)
                    assertEquals(setOf(9), facts.firstContacts)
                    assertEquals(1, cueRailsBeforeContact)
                    assertNull(decision.foul)
                    assertNull(decision.loss)
                    assertTrue("Deadline overrun: $result", result.elapsedMillis <= 5100)
                } finally { world.dispose() }
            }
        } finally { planner.close() }
    }
}
