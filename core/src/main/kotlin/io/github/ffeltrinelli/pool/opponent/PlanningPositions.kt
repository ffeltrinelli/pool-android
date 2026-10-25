package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*

/** A placement retains the exact target/pocket seed whose two clear lanes justified it. */
internal data class PlanningPosition(val point: Point, val directPot: ShotCandidate? = null)

internal object PlanningPositions {
    private val cueDistances = listOf(0.25f, 0.12f, 0.08f, 0.45f)
    private const val MAX_POSITIONS = 8

    fun shortlist(table: TableSnapshot, checkBudget: () -> Unit = {}): List<PlanningPosition> {
        if (table.placement == null) return listOf(PlanningPosition(table.balls.first { it.id == 0 }.point))
        if (table.rulesContext().breaking) return safePoints(table).take(MAX_POSITIONS).map { PlanningPosition(it) }
        val aligned = alignedPlacements(table, checkBudget)
        val safe = if (aligned.size < MAX_POSITIONS) safePoints(table).map { PlanningPosition(it) } else emptyList()
        return (aligned + safe).distinctBy { it.point }.take(MAX_POSITIONS)
    }

    private data class PlacementOption(val placement: PlanningPosition, val target: Int, val pocketDistance: Float, val distancePreference: Int)

    private fun alignedPlacements(table: TableSnapshot, checkBudget: () -> Unit): List<PlanningPosition> {
        val region = requireNotNull(table.placement)
        val objects = table.balls.filter { it.id != 0 }.map { it.point }
        val targets = table.rulesContext().targets
        val placements = buildList {
            for (ball in table.balls.filter { it.id in targets }) for (pocket in PocketGeometry.pockets) {
                val outgoing = (pocket.center - ball.point).normalized()
                for ((preference, distance) in cueDistances.withIndex()) {
                    checkBudget()
                    val point = ball.point - outgoing * distance
                    if (BallPlacement.invalidReason(point, objects, region) != null) continue
                    val pot = DirectPotGeometry(table.withCue(point)).candidate(ball.id, pocket)
                    add(PlacementOption(PlanningPosition(point, pot), ball.id, (pocket.mouthCenter - ball.point).length(), preference))
                }
            }
        }.sortedWith(compareBy<PlacementOption> { it.pocketDistance }.thenBy { it.distancePreference })
        val direct = diverseTargets(placements.filter { it.placement.directPot != null })
        // The predictor is conservative around jaws; retain unchecked aligned poses only after clear pots.
        val unchecked = if (direct.size < MAX_POSITIONS)
            diverseTargets(placements.filter { it.placement.directPot == null }) else emptyList()
        return (direct + unchecked).distinctBy { it.point }.take(MAX_POSITIONS)
    }

    private fun diverseTargets(options: List<PlacementOption>): List<PlanningPosition> {
        val perTarget = options.distinctBy { it.placement.point }.groupBy { it.target }.values.toList()
        return (0 until MAX_POSITIONS).flatMap { index -> perTarget.mapNotNull { it.getOrNull(index)?.placement } }.take(MAX_POSITIONS)
    }

    private fun safePoints(table: TableSnapshot): List<Point> {
        val region = requireNotNull(table.placement)
        val objects = table.balls.filter { it.id != 0 }.map { it.point }
        return BallPlacement.candidates(objects, region).sortedBy { (it - TableGeometry.CUE_START).length() }
    }

    fun fallbackPoint(table: TableSnapshot): Point = if (table.placement == null)
        table.balls.first { it.id == 0 }.point else safePoints(table).first()

    fun fallback(table: TableSnapshot): PlanningResult {
        val point = fallbackPoint(table)
        val positioned = table.withCue(point)
        val geometry = ShotCandidates()
        val shot = geometry.generate(positioned).firstOrNull()?.shot ?: geometry.fallback(positioned)
        return PlanningResult(shot, 0, 0, true, point)
    }
}
