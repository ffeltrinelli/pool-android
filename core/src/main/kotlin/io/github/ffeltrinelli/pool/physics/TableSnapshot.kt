package io.github.ffeltrinelli.pool.physics

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.rules.*

data class BallSnapshot(val id: Int, val point: Point, val height: Float = TableGeometry.BALL_RADIUS,
    val qx: Float = -0.70710677f, val qy: Float = 0f, val qz: Float = 0f, val qw: Float = 0.70710677f)
data class TableSnapshot(val balls: List<BallSnapshot>,
    val rules: RulesContext? = null,
    val placement: PlacementRegion? = null) {
    fun withCue(point: Point) = copy(balls = balls.filter { it.id != 0 } + BallSnapshot(0, point))
    fun rulesContext() = rules ?: RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet())
}
