package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.FoulReason
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
    const val CONTACT_IMPULSE = 0.70f
    const val STRONG_CONTACT_IMPULSE = 1.00f
    const val INTENDED_POT_BONUS = 2.0
    const val EVALUATIONS = 64
    const val DEADLINE_MILLIS = 5000L
    const val SIMULATION_SECONDS = 30f
    const val THINK_SECONDS = 1f
    const val FOUL_SECONDS = 3f
}

data class PlanningResult(
    val shot: ShotCommand,
    val evaluations: Int,
    val elapsedMillis: Long,
    val usedFallback: Boolean,
    val cuePosition: Point? = null,
    val predictedFoul: FoulReason? = null,
    val predictedPockets: Set<Int> = emptySet(),
    val targetBallId: Int? = null
)
interface ComputerShotPlanner : AutoCloseable {
    fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult
    override fun close()
}

/** Called exclusively by one worker. Its Bullet world never emits live session or audio events. */
class BulletShotPlanner(
    private val maxEvaluations: Int = OpponentTuning.EVALUATIONS,
    private val deadlineMillis: Long = OpponentTuning.DEADLINE_MILLIS
) : ComputerShotPlanner {
    private data class Seed(val candidate: ShotCandidate, val cuePosition: Point)
    private var simulation: ShotSimulation? = null

    override fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult {
        val started = System.nanoTime()
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        val world = simulation ?: ShotSimulation().also { simulation = it }
        val geometry = ShotCandidates()
        val positions = PlanningPositions.candidates(table)
        val initialTable = table.withCue(positions.first())
        val search = ShotSearch(initialTable, (geometry.generate(initialTable).firstOrNull()?.shot ?: geometry.fallback(initialTable)), world, maxEvaluations, deadlineMillis, cancelled, started)
        val seeds = if (table.placement == null) {
            geometry.generate(initialTable).take(4).map { Seed(it, positions.first()) }
                .ifEmpty { listOf(Seed(ShotCandidate(geometry.fallback(initialTable), 0f), positions.first())) }
        } else positions.map { position ->
            val positioned = table.withCue(position)
            Seed(geometry.generate(positioned).firstOrNull() ?: ShotCandidate(geometry.fallback(positioned), 0f), position)
        }
        try {
            val openingSeeds = if (table.placement == null) {
                seeds.take(1) + geometry.contactAlternatives(initialTable).map { Seed(ShotCandidate(it, 0f), positions.first()) } + seeds.drop(1)
            } else seeds
            openingSeeds.distinct().forEach { if (search.remaining > 0) search.evaluate(it.candidate.shot, it.cuePosition, it.candidate.targetBallId) }
            for ((index, initial) in seeds.withIndex()) {
                val remainingSeeds = seeds.size - index
                val budget = search.remaining / remainingSeeds
                if (budget < 6) break
                val angle = atan2(initial.candidate.shot.direction.x.toDouble(), -initial.candidate.shot.direction.z.toDouble())
                val optimizer = CMAESOptimizer(16, Double.POSITIVE_INFINITY, true, 0, 10, Well19937c(seed + index), false, null)
                try {
                    optimizer.optimize(MaxEval(budget), GoalType.MAXIMIZE,
                        InitialGuess(doubleArrayOf(angle, initial.candidate.shot.impulse.toDouble())),
                        SimpleBounds(doubleArrayOf(angle - 0.14, OpponentTuning.MIN_IMPULSE.toDouble()),
                            doubleArrayOf(angle + 0.14, OpponentTuning.MAX_IMPULSE.toDouble())),
                        CMAESOptimizer.Sigma(doubleArrayOf(0.035, 0.12)), CMAESOptimizer.PopulationSize(6),
                        ObjectiveFunction { point -> search.evaluate(ShotCommand(Point(sin(point[0]).toFloat(), -cos(point[0]).toFloat()), point[1].toFloat()), initial.cuePosition, initial.candidate.targetBallId) })
                } catch (_: TooManyEvaluationsException) { /* Best completed evaluation is retained across starts. */ }
            }
        } catch (_: ShotSearch.DeadlineReached) { /* Keep the best complete simulation; partial outcomes are never scored. */ }
        search.checkCancellation()
        return search.result()
    }

    override fun close() { simulation?.close(); simulation = null }
}
