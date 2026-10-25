package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*

/** A bounded placement shortlist; shot evaluation still uses the same shared search budget. */
internal object PlanningPositions {
    fun candidates(table: TableSnapshot): List<Point> {
        val region = table.placement ?: return listOf(table.balls.first { it.id == 0 }.point)
        val objects = table.balls.filter { it.id != 0 }.map { it.point }
        val targets = table.rulesContext().targets
        val direct = table.balls.filter { it.id in targets }.flatMap { ball ->
            PocketGeometry.pockets.map { pocket ->
                (ball.point - (pocket.center - ball.point).normalized() * 0.25f) to (pocket.mouthCenter - ball.point).length()
            }
        }.sortedBy { it.second }.map { it.first }
        val safe = BallPlacement.candidates(objects, region).sortedBy { (it - TableGeometry.CUE_START).length() }
        // Preserve pot-aligned positions before grid fallbacks; proximity to the break spot is not shot quality.
        val positions = if (table.rulesContext().breaking) safe else direct + safe
        return positions.distinct().filter { BallPlacement.invalidReason(it, objects, region) == null }.take(8)
    }
    fun fallback(table: TableSnapshot): PlanningResult {
        val point = candidates(table).first()
        val positioned = table.withCue(point)
        val geometry = ShotCandidates()
        val shot = geometry.generate(positioned).firstOrNull()?.shot ?: geometry.fallback(positioned)
        return PlanningResult(shot, 0, 0, true, point)
    }
}
