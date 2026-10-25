package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import java.util.concurrent.CancellationException

/** Per-request budget and best-result bookkeeping; never reused across table snapshots. */
internal class ShotSearch(
    private val table: TableSnapshot,
    fallback: ShotCommand,
    private val simulation: ShotSimulation,
    private val maxEvaluations: Int,
    deadlineMillis: Long,
    private val cancelled: () -> Boolean,
    private val started: Long = System.nanoTime()
) {
    private val deadline = if (deadlineMillis == Long.MAX_VALUE) Long.MAX_VALUE else started + deadlineMillis * 1_000_000
    private var best = fallback
    private var bestPosition = table.balls.first { it.id == 0 }.point
    private var bestScore = Double.NEGATIVE_INFINITY
    private var bestEvaluation: ShotEvaluation? = null
    private var bestTarget: Int? = null
    private var completed = 0
    private var attempted = 0
    val remaining get() = maxEvaluations - attempted

    fun checkCancellation() {
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
    }

    fun checkBudget() {
        checkCancellation()
        if (System.nanoTime() >= deadline) throw DeadlineReached()
    }

    fun evaluate(shot: ShotCommand, position: io.github.ffeltrinelli.pool.model.Point = bestPosition, targetBallId: Int? = null): Double {
        checkBudget()
        attempted++
        val evaluation = simulation.evaluate(table.withCue(position), shot, ::checkBudget) ?: return -1000000.0
        completed++
        val score = evaluation.scoreFor(targetBallId)
        if (score > bestScore) {
            bestEvaluation = evaluation
            bestTarget = targetBallId
            bestScore = score
            best = shot
            bestPosition = position
        }
        return score
    }

    fun result() = PlanningResult(
        shot = best,
        evaluations = completed,
        elapsedMillis = (System.nanoTime() - started) / 1_000_000,
        usedFallback = completed == 0,
        cuePosition = bestPosition,
        predictedFoul = bestEvaluation?.foul,
        predictedPockets = bestEvaluation?.pocketedTargets.orEmpty(),
        targetBallId = bestTarget
    )

    class DeadlineReached : RuntimeException()
}
