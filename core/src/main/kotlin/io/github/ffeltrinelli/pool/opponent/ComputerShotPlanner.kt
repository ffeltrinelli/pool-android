package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import org.apache.commons.math3.exception.TooManyEvaluationsException
import org.apache.commons.math3.optim.*
import org.apache.commons.math3.optim.nonlinear.scalar.*
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer
import org.apache.commons.math3.random.Well19937c
import java.util.concurrent.CancellationException
import kotlin.math.*

object OpponentTuning {
    const val MAX_IMPULSE = 1.15f
    const val MIN_IMPULSE = 0.04f
    const val EVALUATIONS = 64
    const val DEADLINE_MILLIS = 5000L
    const val SIMULATION_SECONDS = 30f
    const val THINK_SECONDS = 1f
    const val FOUL_SECONDS = 3f
}

data class PlanningResult(val shot: ShotCommand, val evaluations: Int, val elapsedMillis: Long, val usedFallback: Boolean)
interface ComputerShotPlanner : AutoCloseable {
    fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult
    override fun close()
}

/** Called exclusively by one worker. Its Bullet world never emits live session or audio events. */
class BulletShotPlanner(
    private val maxEvaluations: Int = OpponentTuning.EVALUATIONS,
    private val deadlineMillis: Long = OpponentTuning.DEADLINE_MILLIS
) : ComputerShotPlanner {
    private var simulation: PoolPhysics? = null
    private var settled = false
    private var scratch = false
    private var pocketed = 0

    override fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult {
        val started = System.nanoTime()
        val deadline = if (deadlineMillis == Long.MAX_VALUE) Long.MAX_VALUE else started + deadlineMillis * 1_000_000
        fun checkBudget() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            if (System.nanoTime() >= deadline) throw SearchDeadline()
        }
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        val world = simulation ?: PoolPhysics(events = { event ->
            when (event) {
                PhysicsEvent.Settled -> settled = true
                is PhysicsEvent.Pocketed -> if (event.ballId == 0) scratch = true else pocketed++
            }
        }).also { simulation = it }
        val geometry = ShotCandidates()
        val seeds = geometry.generate(table).take(4).map { it.shot }.ifEmpty { listOf(geometry.fallback(table)) }
        var best = geometry.fallback(table)
        var bestScore = Double.NEGATIVE_INFINITY
        var completed = 0
        var attempted = 0
        fun evaluate(shot: ShotCommand): Double {
            checkBudget()
            attempted++
            world.restore(table)
            settled = false; scratch = false; pocketed = 0
            world.strike(shot)
            var steps = 0
            while (!settled && steps++ < (OpponentTuning.SIMULATION_SECONDS / PoolPhysics.STEP).toInt()) {
                checkBudget()
                world.step()
            }
            if (!settled) return -1000.0
            completed++
            val closest = world.balls.filter { it.id != 0 && !it.pocketed }.minOfOrNull { ball ->
                PocketGeometry.pockets.minOf { (it.mouthCenter - ball.point).length() }
            } ?: 0f
            val score = ShotEvaluation(pocketed, scratch, closest, shot.impulse).score
            if (score > bestScore) { bestScore = score; best = shot }
            return score
        }
        try {
            seeds.forEach { if (attempted < maxEvaluations) evaluate(it) }
            for ((index, initial) in seeds.withIndex()) {
                val remainingSeeds = seeds.size - index
                val budget = (maxEvaluations - attempted) / remainingSeeds
                if (budget < 6) break
                val angle = atan2(initial.direction.x.toDouble(), -initial.direction.z.toDouble())
                val optimizer = CMAESOptimizer(16, Double.POSITIVE_INFINITY, true, 0, 10, Well19937c(seed + index), false, null)
                try {
                    optimizer.optimize(MaxEval(budget), GoalType.MAXIMIZE,
                        InitialGuess(doubleArrayOf(angle, initial.impulse.toDouble())),
                        SimpleBounds(doubleArrayOf(angle - 0.14, OpponentTuning.MIN_IMPULSE.toDouble()),
                            doubleArrayOf(angle + 0.14, OpponentTuning.MAX_IMPULSE.toDouble())),
                        CMAESOptimizer.Sigma(doubleArrayOf(0.035, 0.12)), CMAESOptimizer.PopulationSize(6),
                        ObjectiveFunction { point -> evaluate(ShotCommand(Point(sin(point[0]).toFloat(), -cos(point[0]).toFloat()), point[1].toFloat())) })
                } catch (_: TooManyEvaluationsException) { /* Best completed evaluation is retained across starts. */ }
            }
        } catch (_: SearchDeadline) { /* Keep the best complete simulation; partial outcomes are never scored. */ }
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        return PlanningResult(best, completed, (System.nanoTime() - started) / 1_000_000, completed == 0)
    }

    override fun close() { simulation?.dispose(); simulation = null }
    private class SearchDeadline : RuntimeException()
}
