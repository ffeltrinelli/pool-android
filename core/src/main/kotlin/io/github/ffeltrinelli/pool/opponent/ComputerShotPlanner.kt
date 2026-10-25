package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.FoulReason
import java.util.concurrent.CancellationException

object OpponentTuning {
    const val MAX_IMPULSE = 1.15f
    const val MIN_IMPULSE = 0.04f
    const val CONTACT_IMPULSE = 0.70f
    const val STRONG_CONTACT_IMPULSE = 1.00f
    const val INTENDED_POT_BONUS = 2.0
    const val EVALUATIONS = 256
    const val DEADLINE_MILLIS = 5000L
    const val SIMULATION_SECONDS = 30f
    const val THINK_SECONDS = 1f
    const val FOUL_SECONDS = 3f
}

data class PlanningMetrics(
    val attempts: Int = 0, val rejected: Int = 0, val cacheHits: Int = 0,
    val directAttempts: Int = 0, val reboundAttempts: Int = 0,
    val restoreMillis: Double = 0.0, val simulationMillis: Double = 0.0, val physicsSteps: Long = 0,
    val geometryMillis: Double = 0.0, val setupMillis: Double = 0.0
)

data class PlanningResult(
    val shot: ShotCommand,
    val evaluations: Int,
    val elapsedMillis: Long,
    val usedFallback: Boolean,
    val cuePosition: Point? = null,
    val predictedFoul: FoulReason? = null,
    val predictedPockets: Set<Int> = emptySet(),
    val targetBallId: Int? = null,
    val kind: ShotKind? = null,
    val predictedFirstContacts: Set<Int> = emptySet(),
    val metrics: PlanningMetrics = PlanningMetrics()
)
interface ComputerShotPlanner : AutoCloseable {
    fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult
    override fun close()
}

/** Called exclusively by one worker. All phases share the same request deadline. */
class BulletShotPlanner(
    private val maxEvaluations: Int = OpponentTuning.EVALUATIONS,
    private val deadlineMillis: Long = OpponentTuning.DEADLINE_MILLIS
) : ComputerShotPlanner {
    internal data class Seed(val candidate: ShotCandidate, val cuePosition: Point)
    private var simulation: ShotSimulation? = null

    override fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult {
        val started = System.nanoTime()
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        val world = simulation ?: ShotSimulation().also { simulation = it }
        world.resetMetrics()
        val geometry = ShotCandidates()
        val fallbackPosition = PlanningPositions.fallbackPoint(table)
        val initialTable = table.withCue(fallbackPosition)
        val search = ShotSearch(initialTable, geometry.fallback(initialTable), world, maxEvaluations, deadlineMillis, cancelled, started)
        val setupMillis = (System.nanoTime() - started) / 1_000_000.0
        var geometryNanos = 0L
        val direct = mutableListOf<Seed>()
        val supplemental = mutableListOf<Seed>()
        try {
            val geometryStarted = System.nanoTime()
            val placements: List<PlanningPosition>
            try {
                placements = PlanningPositions.shortlist(table, search::checkBudget)
                for (placement in placements) {
                    search.checkBudget()
                    val positioned = table.withCue(placement.point)
                    val limit = if (table.placement == null) 4 else 1
                    val candidates = if (table.rulesContext().breaking) geometry.generate(positioned, search::checkBudget)
                    else {
                        val pots = geometry.directSeeds(positioned, search::checkBudget)
                        supplemental += pots.supplemental.take(limit).map { Seed(it, placement.point) }
                        placement.directPot?.let { listOf(it) } ?: pots.centered.take(limit)
                    }
                    direct += candidates.map { Seed(it, placement.point) }
                }
            } finally { geometryNanos += System.nanoTime() - geometryStarted }
            val positions = placements.map { it.point }
            if (!table.rulesContext().breaking) search.limitDirectPhase()
            try {
                // Without a clear pot, cover one contact per placement before refining a rebound.
                // Otherwise a pair of contact fallbacks leaves the phase available for direct-pot refinement.
                val contacts = if (table.placement != null && direct.isEmpty()) {
                    positions.mapNotNull { position -> geometry.contactAlternatives(table.withCue(position)).firstOrNull()?.let {
                        Seed(ShotCandidate(it, 0f, kind = ShotKind.CONTACT), position)
                    } }
                } else positions.asSequence().flatMap { position ->
                    geometry.contactAlternatives(table.withCue(position)).asSequence().map {
                        Seed(ShotCandidate(it, 0f, kind = ShotKind.CONTACT), position)
                    }
                }.take(if (table.placement == null) 4 else 2).toList()
                val opening = if (table.placement != null) direct + contacts else direct.take(1) + contacts + direct.drop(1)
                ShotOptimizer.evaluateSeeds(opening, search)
                // Keep established centered pots; otherwise try direct pocket entries before rebounds.
                val refinement = if (search.directPotFound) direct else {
                    ShotOptimizer.evaluateSeeds(supplemental, search)
                    direct + supplemental
                }
                if (refinement.isNotEmpty()) ShotOptimizer.refine(refinement, search, seed)
            } catch (_: ShotSearch.DirectPhaseEnded) { /* Reserve the second phase for rebounds. */ }
            if (!search.directPotFound && !table.rulesContext().breaking && search.remaining > 0) {
                search.startRebounds()
                val reboundStarted = System.nanoTime()
                val rebounds = try {
                    val perPosition = positions.map { position ->
                        ReboundCandidates().generate(table.withCue(position), search::checkBudget).map { Seed(it, position) }
                    }
                    // Every placement gets a chance before a second seed from the same placement.
                    (0 until 8).flatMap { index -> perPosition.mapNotNull { it.getOrNull(index) } }.take(8)
                } finally { geometryNanos += System.nanoTime() - reboundStarted }
                ShotOptimizer.evaluateSeeds(rebounds, search)
                ShotOptimizer.refine(rebounds, search, seed + 1000)
            }
        } catch (_: ShotSearch.DeadlineReached) { /* Preserve completed results. */ }
          catch (_: ShotSearch.AttemptsReached) { /* One cap covers seeds and all optimizers. */ }
        search.checkCancellation()
        val result = search.result()
        return result.copy(metrics = result.metrics.copy(geometryMillis = geometryNanos / 1_000_000.0, setupMillis = setupMillis))
    }

    override fun close() { simulation?.close(); simulation = null }
}
