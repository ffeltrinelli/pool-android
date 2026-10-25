package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in measurements, not timing assertions. Run the same fixtures before and after a change. */
class PlanningBenchmarkTest {
    @Test fun measureFixedAndDeadlineSearches() {
        assumeTrue(java.lang.Boolean.getBoolean("pool.benchmark"))
        val report = File(System.getProperty("pool.benchmarkOutput", "build/reports/planning/benchmark.csv"))
        report.parentFile.mkdirs()
        report.writeText("layout,mode,run,elapsedMs,evaluations,fallback,foul,target,pockets,attempts,rejected,cacheHits,restoreMs,simulationMs,physicsSteps,geometryMs,setupMs,kind\n")
        val requested = System.getProperty("pool.benchmarkLayout", "")
        val selected = layouts().filterKeys { requested.isEmpty() || it == requested }
        require(selected.isNotEmpty()) { "Unknown planning layout: $requested" }
        for ((name, table) in selected) for (mode in listOf("fixed", "deadline")) {
            val planner = if (mode == "fixed") BulletShotPlanner(32, Long.MAX_VALUE) else BulletShotPlanner()
            try {
                repeat(2) { run ->
                    val result = planner.plan(table, 17) { false }
                    report.appendText("$name,$mode,${if (run == 0) "cold" else "warm"},${result.elapsedMillis},${result.evaluations},${result.usedFallback},${result.predictedFoul},${result.targetBallId},${result.predictedPockets.joinToString(";")},${result.metrics.attempts},${result.metrics.rejected},${result.metrics.cacheHits},${result.metrics.restoreMillis},${result.metrics.simulationMillis},${result.metrics.physicsSteps},${result.metrics.geometryMillis},${result.metrics.setupMillis},${result.kind}\n")
                }
            } finally { planner.close() }
        }
    }

    companion object {
        fun layouts(): Map<String, TableSnapshot> {
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
}
