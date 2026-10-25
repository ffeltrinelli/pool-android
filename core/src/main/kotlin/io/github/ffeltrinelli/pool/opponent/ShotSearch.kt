package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.aim.AimBall
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import java.util.concurrent.CancellationException

internal enum class TrialOrigin { SEED, REFINEMENT }

/** Per-request budget, exact evaluation cache and best completed result. */
internal class ShotSearch(
    private val table: TableSnapshot,
    fallback: ShotCommand,
    private val simulation: ShotSimulation,
    private val maxEvaluations: Int,
    deadlineMillis: Long,
    private val cancelled: () -> Boolean,
    private val started: Long = System.nanoTime(),
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val deadline = if (deadlineMillis == Long.MAX_VALUE) Long.MAX_VALUE else started + deadlineMillis * 1_000_000
    private val directDeadline = if (deadlineMillis == Long.MAX_VALUE) Long.MAX_VALUE else started + deadlineMillis * 500_000
    private val directLimit = maxOf(1, maxEvaluations / 2)
    private var phaseLimited = false
    private var reboundPhase = false
    var directPotFound = false
        private set
    private var best = fallback
    private var bestPosition = table.balls.first { it.id == 0 }.point
    private var bestScore = Double.NEGATIVE_INFINITY
    private var bestEvaluation: ShotEvaluation? = null
    private var bestTarget: Int? = null
    private var bestKind: ShotKind? = null
    private var bestOrigin = TrialOrigin.SEED
    private var completed = 0
    private var attempted = 0
    private var rejected = 0
    private var cached = 0
    private var directAttempts = 0
    private var reboundAttempts = 0
    private data class Trial(val position: Point, val shot: ShotCommand)
    private val evaluations = mutableMapOf<Trial, ShotEvaluation?>()
    private val geometry = ReboundGeometry()
    private val positioned = mutableMapOf<Point, Pair<TableSnapshot, List<AimBall>>>()
    private val context = table.rulesContext()
    private val targets = context.targets
    val remaining get() = maxEvaluations - attempted

    fun limitDirectPhase() { phaseLimited = true }
    fun startRebounds() { phaseLimited = false; reboundPhase = true }

    fun checkCancellation() {
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
    }

    fun checkBudget() {
        checkCancellation()
        val now = nanoTime()
        if (now >= deadline) throw DeadlineReached()
        if (phaseLimited && !directPotFound && now >= directDeadline) throw DirectPhaseEnded()
    }

    fun evaluate(shot: ShotCommand, position: Point = bestPosition, targetBallId: Int? = null,
        kind: ShotKind = ShotKind.DIRECT_POT, origin: TrialOrigin = TrialOrigin.SEED): Double {
        checkBudget()
        if (remaining <= 0) throw AttemptsReached()
        if (phaseLimited && !directPotFound && attempted >= directLimit) throw DirectPhaseEnded()
        attempted++
        if (reboundPhase) reboundAttempts++ else directAttempts++
        val (snapshot, balls) = positioned.getOrPut(position) {
            val snapshot = table.withCue(position)
            snapshot to snapshot.balls.map { AimBall(it.id, it.point) }
        }
        if (!context.breaking) {
            val first = geometry.firstContact(position, shot.direction, balls, kind)
            if (first !in targets || (targetBallId != null && first != targetBallId)) {
                rejected++
                return REJECTED_SCORE
            }
        }
        val trial = Trial(position, shot)
        val evaluation = if (evaluations.containsKey(trial)) {
            cached++
            evaluations[trial]
        } else {
            simulation.evaluate(snapshot, shot, ::checkBudget).also {
                evaluations[trial] = it
                if (it != null) completed++
            }
        } ?: return REJECTED_SCORE
        if (kind == ShotKind.BANK_POT && targetBallId in evaluation.pocketedTargets && targetBallId !in evaluation.bankedTargets)
            return REJECTED_SCORE
        if (kind.isKick && evaluation.firstContacts.isNotEmpty() && evaluation.cueRailsBeforeContact != 1)
            return REJECTED_SCORE
        if (kind == ShotKind.DIRECT_POT && evaluation.foul == null && !evaluation.lost && targetBallId in evaluation.pocketedTargets)
            directPotFound = true
        val score = evaluation.scoreFor(targetBallId.takeIf { kind.isPot })
        if (score > bestScore && !preserveSuccessfulSeed(evaluation, targetBallId, origin)) {
            bestEvaluation = evaluation
            bestTarget = targetBallId
            bestKind = kind
            bestOrigin = origin
            bestScore = score
            best = shot
            bestPosition = position
        }
        return score
    }

    /** A secondary score improvement is not evidence that a proven pot became more robust. */
    private fun preserveSuccessfulSeed(evaluation: ShotEvaluation, target: Int?, origin: TrialOrigin): Boolean {
        val previous = bestEvaluation ?: return false
        return origin == TrialOrigin.REFINEMENT && bestOrigin == TrialOrigin.SEED && target != null && target == bestTarget &&
            previous.foul == null && evaluation.foul == null && !previous.lost && !evaluation.lost &&
            previous.won == evaluation.won && previous.objectBalls == evaluation.objectBalls &&
            target in previous.pocketedTargets && target in evaluation.pocketedTargets
    }

    fun result() = PlanningResult(
        shot = best, evaluations = completed, elapsedMillis = (nanoTime() - started) / 1_000_000,
        usedFallback = bestEvaluation == null, cuePosition = bestPosition, predictedFoul = bestEvaluation?.foul,
        predictedPockets = bestEvaluation?.pocketedTargets.orEmpty(), targetBallId = bestTarget,
        kind = bestKind, predictedFirstContacts = bestEvaluation?.firstContacts.orEmpty(),
        metrics = PlanningMetrics(attempted, rejected, cached, directAttempts, reboundAttempts,
            simulation.restoreNanos / 1_000_000.0, simulation.simulationNanos / 1_000_000.0, simulation.physicsSteps)
    )

    class DeadlineReached : RuntimeException()
    class DirectPhaseEnded : RuntimeException()
    class AttemptsReached : RuntimeException()
    companion object { const val REJECTED_SCORE = -1000000.0 }
}
