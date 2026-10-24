package io.github.ffeltrinelli.pool.input

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.PlacementRegion

/** Owns the human preview, never a Bullet body. Confirmation is an explicit command. */
class CueBallPlacement {
    var point = TableGeometry.CUE_START
        private set
    fun begin(point: Point = TableGeometry.CUE_START) { this.point = point }
    fun move(point: Point) { this.point = point }
    fun invalidReason(table: TableSnapshot, region: PlacementRegion) =
        BallPlacement.invalidReason(point, table.balls.filter { it.id != 0 }.map { it.point }, region)
}
