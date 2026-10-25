package io.github.ffeltrinelli.pool.android

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Runs on the instrumentation thread, which owns all benchmark native worlds. Opt-in only. */
class PlanningBenchmarkDeviceTest {
    @Test fun measureFixedAndDeadlineSearches() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("poolBenchmark") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AndroidLauncher
        val report = File(activity.filesDir, "planning-benchmark.csv")
        val details = File(activity.filesDir, "planning-benchmark-details.txt")
        report.writeText("layout,mode,run,elapsedMs,evaluations,fallback,predictedFoul,actualFoul,target,pockets\n")
        details.writeText("device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}\n")
        try {
            for ((name, table) in layouts()) for (mode in listOf("fixed", "deadline")) {
                val planner = if (mode == "fixed") BulletShotPlanner(32, Long.MAX_VALUE) else BulletShotPlanner()
                var facts = ShotFacts()
                var settled = false
                val world = PoolPhysics(events = { facts.accept(it); if (it == PhysicsEvent.Settled) settled = true })
                try {
                    repeat(2) { run ->
                        val result = planner.plan(table, 17) { false }
                        // Independently replay exact Hard execution in another native world.
                        world.restore(table.withCue(result.cuePosition!!))
                        world.strike(result.shot)
                        repeat(3600) { if (!settled) world.step() }
                        val actual = EightBallRules.resolve(table.rulesContext(), facts)
                        report.appendText("$name,$mode,${if (run == 0) "cold" else "warm"},${result.elapsedMillis},${result.evaluations},${result.usedFallback},${result.predictedFoul},${actual.foul},${result.targetBallId},${result.predictedPockets.joinToString(";")}\n")
                        details.appendText("$name $mode run=$run $result actual=$actual first=${facts.firstContacts}\n")
                        assertTrue(result.shot.impulse in OpponentTuning.MIN_IMPULSE..OpponentTuning.MAX_IMPULSE)
                        facts = ShotFacts()
                        settled = false
                    }
                } finally { world.dispose(); planner.close() }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun measureIdenticalRollouts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("poolBenchmark") == "true")
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "planning-benchmark-physics.csv")
        report.parentFile?.mkdirs()
        report.writeText("layout,run,rollouts,steps,restoreMs,simulationMs\n")
        val layouts = linkedMapOf(
            "sparse" to listOf(0 to Point(0f, 0.4f), 9 to Point(0f, 0f), 8 to Point(0.3f, -0.7f)),
            "rack" to TableGeometry.rack(17)
        )
        for ((name, layout) in layouts) {
            var settled = false
            val world = PoolPhysics(events = { if (it == PhysicsEvent.Settled) settled = true })
            try {
                world.reset(layout)
                val snapshot = world.snapshot()
                repeat(4) { run ->
                    var restoreNanos = 0L
                    var simulationNanos = 0L
                    var steps = 0
                    repeat(16) {
                        val restoreStarted = System.nanoTime()
                        world.restore(snapshot)
                        restoreNanos += System.nanoTime() - restoreStarted
                        settled = false
                        val simulationStarted = System.nanoTime()
                        world.strike(ShotCommand(Point(0f, -1f), if (name == "rack") 1.1f else 0.7f))
                        repeat(3600) { if (!settled) { world.step(); steps++ } }
                        simulationNanos += System.nanoTime() - simulationStarted
                    }
                    report.appendText("$name,$run,16,$steps,${restoreNanos / 1_000_000.0},${simulationNanos / 1_000_000.0}\n")
                }
            } finally { world.dispose() }
        }
    }

    private fun layouts(): Map<String, TableSnapshot> {
        fun table(vararg balls: Pair<Int, Point>, group: BallGroup? = BallGroup.STRIPES, placement: PlacementRegion? = null): TableSnapshot {
            val snapshots = balls.map { BallSnapshot(it.first, it.second) }
            return TableSnapshot(snapshots, RulesContext(snapshots.filter { it.id != 0 }.map { it.id }.toSet(), group), placement)
        }
        return linkedMapOf(
            "direct" to table(0 to Point(0f, 0f), 9 to Point(0.3f, 0f), 8 to Point(-0.3f, -0.7f)),
            "open" to table(0 to Point(0f, 0.4f), 1 to Point(0.2f, 0f), 9 to Point(-0.2f, -0.4f), 8 to Point(0.35f, -0.7f), group = null),
            "blocked" to table(0 to Point(0f, 0.3f), 9 to Point(0f, 0f), 1 to Point(0f, 0.15f), 8 to Point(0.3f, -0.7f)),
            "bank" to table(0 to Point(0f, 0.6f), 9 to Point(0.1f, 0f), 1 to Point(0.32f, 0f), 8 to Point(-0.3f, -0.7f)),
            "kick" to table(0 to Point(-0.2f, 0.35f), 9 to Point(0.2f, -0.35f), 1 to Point(0f, 0f), 8 to Point(-0.3f, -0.7f)),
            "placement" to table(9 to Point(0.2f, 0.2f), 8 to Point(-0.3f, -0.5f), placement = PlacementRegion.ANYWHERE)
        )
    }
}
