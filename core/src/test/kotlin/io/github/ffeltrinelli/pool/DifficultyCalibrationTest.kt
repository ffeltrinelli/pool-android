package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random

/** Opt-in native experiment: each profile executes the same raw plan and the same fixed seed corpus. */
class DifficultyCalibrationTest {
    private data class Layout(val name: String, val balls: List<Pair<Int, Point>>)
    private data class Totals(var shots: Int = 0, var pots: Int = 0, var points: Int = 0, var scratches: Int = 0, var unsettled: Int = 0)

    @Test fun `Easy targets thirty percent pot success while search remains identical`() {
        assumeTrue(java.lang.Boolean.getBoolean("pool.calibrateDifficulty"))
        val layouts = listOf(
            Layout("short-side", listOf(0 to Point(0f, 0f), 1 to Point(0.3f, 0f))),
            Layout("long-side", listOf(0 to Point(-0.4f, 0f), 1 to Point(0.18f, 0f))),
            Layout("short-corner", listOf(0 to Point(0.08f, 0.18f), 1 to Point(0.32f, 0.66f))),
            Layout("long-corner", listOf(0 to Point(-0.36f, -0.62f), 1 to Point(0.24f, 0.48f))),
            Layout("left-cut", listOf(0 to Point(0.32f, 0.58f), 1 to Point(-0.2f, 0.25f))),
            Layout("right-cut", listOf(0 to Point(-0.28f, 0.55f), 1 to Point(0.24f, 0.08f))),
            Layout("crowded-side", listOf(0 to Point(-0.38f, 0.12f), 1 to Point(0.2f, 0f),
                2 to Point(-0.08f, -0.34f), 3 to Point(0.18f, 0.55f))),
            Layout("crowded-corner", listOf(0 to Point(-0.3f, -0.5f), 1 to Point(0.2f, 0.45f),
                2 to Point(0.3f, -0.2f), 3 to Point(-0.25f, 0.35f)))
        )
        val seeds = Random(81273).let { random -> List(64) { random.nextLong() } }
        val totals = OpponentDifficulty.entries.associateWith { Totals() }
        val rows = mutableListOf("layout,difficulty,shots,pot_successes,awarded_points,scratches,unsettled,completed_evaluations")
        val planner = BulletShotPlanner(deadlineMillis = Long.MAX_VALUE)
        val setup = PoolPhysics()
        var pocketed = 0
        var scratch = false
        var settled = false
        val replay = PoolPhysics(events = { event ->
            when (event) {
                is PhysicsEvent.Pocketed -> if (event.ballId == 0) scratch = true else pocketed++
                is PhysicsEvent.Contacts, is PhysicsEvent.OffTable -> Unit
                PhysicsEvent.Settled -> settled = true
            }
        })
        try {
            for ((index, layout) in layouts.withIndex()) {
                setup.reset(layout.balls)
                repeat(100) { setup.step() }
                val snapshot = setup.snapshot()
                val plan = planner.plan(snapshot, 200L + index) { false }
                assertFalse("${layout.name} must have a completed plan", plan.usedFallback)
                assertTrue(plan.evaluations in 1..64)
                for (level in OpponentDifficulty.entries) {
                    val result = Totals()
                    for (seed in seeds) {
                        replay.restore(snapshot)
                        pocketed = 0; scratch = false; settled = false
                        replay.strike(ExecutionAccuracy.apply(plan.shot, level, seed))
                        var steps = 0
                        while (!settled && steps++ < 7200) replay.step()
                        result.shots++
                        if (settled && pocketed > 0) result.pots++
                        if (!settled) result.unsettled++
                        if (scratch) result.scratches++ else if (settled) result.points += pocketed
                    }
                    rows += "${layout.name},$level,${result.shots},${result.pots},${result.points},${result.scratches},${result.unsettled},${plan.evaluations}"
                    totals.getValue(level).apply {
                        shots += result.shots; pots += result.pots; points += result.points; scratches += result.scratches; unsettled += result.unsettled
                    }
                }
            }
        } finally { replay.dispose(); setup.dispose(); planner.close() }
        for ((level, result) in totals) rows += "TOTAL,$level,${result.shots},${result.pots},${result.points},${result.scratches},${result.unsettled},"
        val output = File(System.getProperty("pool.calibrationOutput"))
        output.parentFile.mkdirs()
        output.writeText(rows.joinToString("\n", postfix = "\n"))
        val easy = totals.getValue(OpponentDifficulty.EASY)
        val medium = totals.getValue(OpponentDifficulty.MEDIUM)
        val easySuccessRate = easy.pots.toDouble() / easy.shots
        assertEquals("Easy should remain near 30% on the fixed corpus; see $output", 0.30, easySuccessRate, 0.01)
        val difference = medium.pots.toDouble() / medium.shots - easy.pots.toDouble() / easy.shots
        assertTrue("Easy should reduce aggregate pot success by at least 10 points; actual=$difference; see $output", difference >= 0.10)
    }
}
