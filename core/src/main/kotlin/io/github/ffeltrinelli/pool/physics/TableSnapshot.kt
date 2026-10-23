package io.github.ffeltrinelli.pool.physics

import io.github.ffeltrinelli.pool.model.*
import kotlin.math.*

data class BallSnapshot(val id: Int, val point: Point, val height: Float = TableGeometry.BALL_RADIUS,
    val qx: Float = -0.70710677f, val qy: Float = 0f, val qz: Float = 0f, val qw: Float = 0.70710677f)
data class TableSnapshot(val balls: List<BallSnapshot>)

/** Nearest safe point on a 5 mm lattice, tied by z then x; never moves another ball. */
object CueReplacement {
    private const val GRID = 0.005f
    private const val CLEARANCE = 0.002f
    private val start get() = TableGeometry.rack().first().second
    fun position(objects: List<Point>): Point {
        if (safe(start, objects)) return start
        return sequence {
            val maxX = ((TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS - CLEARANCE) / GRID).toInt()
            val maxZ = ((TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS - CLEARANCE) / GRID).toInt()
            for (z in 7..maxZ) for (x in -maxX..maxX) yield(Point(x * GRID, z * GRID))
        }.filter { safe(it, objects) }.minWithOrNull(compareBy<Point> { (it - start).dot(it - start) }.thenBy { it.z }.thenBy { it.x })
            ?: error("No cue replacement position on starting half")
    }
    private fun safe(point: Point, objects: List<Point>): Boolean {
        val radius = TableGeometry.BALL_RADIUS + CLEARANCE
        if (objects.any { (it - point).length() < radius + TableGeometry.BALL_RADIUS }) return false
        if (PocketGeometry.pockets.any { (it.center - point).length() < it.radius + radius }) return false
        return PocketGeometry.cushions.none { cushion ->
            cushion.outline.indices.any { i ->
                val a = cushion.outline[i]; val b = cushion.outline[(i + 1) % cushion.outline.size]
                val edge = b - a
                val closest = a + edge * ((point - a).dot(edge) / edge.dot(edge)).coerceIn(0f, 1f)
                (point - closest).length() < radius
            }
        }
    }
}
