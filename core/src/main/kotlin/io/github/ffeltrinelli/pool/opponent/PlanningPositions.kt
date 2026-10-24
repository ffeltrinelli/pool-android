package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*

/** A bounded placement shortlist; shot evaluation still uses the same shared search budget. */
internal object PlanningPositions {
    fun candidates(table: TableSnapshot): List<Point> {
        val region = table.placement ?: return listOf(table.balls.first { it.id == 0 }.point)
        val objects = table.balls.filter { it.id != 0 }.map { it.point }
        val direct = table.balls.filter { it.id in table.rulesContext().targets }.flatMap { ball ->
            PocketGeometry.pockets.map { pocket -> ball.point - (pocket.center - ball.point).normalized() * 0.25f }
        }
        return (direct + BallPlacement.candidates(objects, region)).distinct()
            .filter { BallPlacement.invalidReason(it, objects, region) == null }
            .sortedWith(compareBy<Point> { (it - TableGeometry.CUE_START).length() }.thenBy { it.z }.thenBy { it.x })
            .take(8)
    }
    fun fallback(table: TableSnapshot): PlanningResult {
        val point = candidates(table).first()
        return PlanningResult(ShotCandidates().fallback(table.withCue(point)), 0, 0, true, point)
    }
}
