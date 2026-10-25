package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Identical physics work on both revisions isolates speed changes from search-policy changes. */
class PhysicsThroughputBenchmarkTest {
    @Test fun measureIdenticalRollouts() {
        assumeTrue(java.lang.Boolean.getBoolean("pool.benchmark"))
        assumeTrue(System.getProperty("pool.benchmarkLayout", "").isEmpty())
        val report = File(System.getProperty("pool.benchmarkOutput").removeSuffix(".csv") + "-physics.csv")
        report.parentFile.mkdirs()
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
}
