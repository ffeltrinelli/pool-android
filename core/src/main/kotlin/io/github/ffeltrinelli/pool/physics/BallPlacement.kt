package io.github.ffeltrinelli.pool.physics

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.rules.PlacementRegion
import kotlin.math.*

/** Queries only: the same geometry validates user previews and computer candidates. */
object BallPlacement {
    private const val CLEARANCE = 0.001f
    fun invalidReason(point: Point, objects: List<Point>, region: PlacementRegion): String? {
        val radius = TableGeometry.BALL_RADIUS + CLEARANCE
        if (!point.x.isFinite() || !point.z.isFinite()) return "Choose a position on the cloth"
        if (abs(point.x) > TableGeometry.HALF_WIDTH - radius || abs(point.z) > TableGeometry.HALF_LENGTH - radius)
            return "Keep the ball inside the table"
        if (region == PlacementRegion.BEHIND_HEAD_STRING && point.z <= TableGeometry.HEAD_STRING)
            return "Place behind the head string"
        if (objects.any { (it - point).length() < radius + TableGeometry.BALL_RADIUS }) return "Too close to another ball"
        if (PocketGeometry.pockets.any { it.containsDrop(point) || (it.mouthCenter - point).length() < radius })
            return "Keep the ball away from pockets"
        if (PocketGeometry.cushions.any { cushion ->
            contains(point, cushion.outline) || cushion.outline.indices.any { i ->
                val a = cushion.outline[i]; val edge = cushion.outline[(i + 1) % cushion.outline.size] - a
                val closest = a + edge * ((point - a).dot(edge) / edge.dot(edge)).coerceIn(0f, 1f)
                (point - closest).length() < radius
            }
        }) return "Too close to a cushion"
        return null
    }
    private fun contains(point: Point, polygon: List<Point>): Boolean {
        var inside = false
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            if ((a.z > point.z) != (b.z > point.z) &&
                point.x < (b.x - a.x) * (point.z - a.z) / (b.z - a.z) + a.x) inside = !inside
        }
        return inside
    }
    fun candidates(objects: List<Point>, region: PlacementRegion): List<Point> {
        val points = buildList {
            add(TableGeometry.CUE_START)
            for (z in -10..10) for (x in -5..5) add(Point(x * 0.10f, z * 0.10f))
        }
        return points.distinct().filter { invalidReason(it, objects, region) == null }
    }
    fun restrictedTargetToSpot(table: TableSnapshot, targets: Set<Int>): Int? {
        val legal = table.balls.filter { it.id in targets }
        if (legal.isEmpty() || legal.any { it.point.z <= TableGeometry.HEAD_STRING }) return null
        return legal.minWith(compareBy<BallSnapshot> { it.point.z }.thenBy { it.id }).id
    }
    fun spot(objects: List<Point>): Point {
        // WPA: as close as possible to the foot spot toward the foot rail, then toward the head.
        val step = 0.0005f
        val points = sequence {
            yield(Point(0f, TableGeometry.FOOT_SPOT))
            for (i in 1..1100) yield(Point(0f, TableGeometry.FOOT_SPOT - i * step))
            for (i in 1..3300) yield(Point(0f, TableGeometry.FOOT_SPOT + i * step))
        }
        return points.first { p -> abs(p.z) < TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS &&
            objects.all { (it - p).length() >= TableGeometry.BALL_RADIUS * 2 + 0.0001f } }
    }
}
