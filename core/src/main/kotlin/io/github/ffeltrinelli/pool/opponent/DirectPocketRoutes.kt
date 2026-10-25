package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import kotlin.math.abs

/** Pocket-center, table-axis and usable-mouth routes, including oblique corner entries. */
internal object DirectPocketRoutes {
    private val axes = listOf(Point(-1f, 0f), Point(1f, 0f), Point(0f, -1f), Point(0f, 1f))
    private val mouthFractions = listOf(0f, -0.5f, 0.5f)

    fun directions(origin: Point, pocket: PocketDefinition): List<Point> = buildList {
        add((pocket.center - origin).normalized())
        val usableHalfWidth = pocket.width / 2f - TableGeometry.BALL_RADIUS
        val mouthDirections = mouthFractions.map { fraction ->
            val entry = pocket.mouthCenter + pocket.lateral * (usableHalfWidth * fraction)
            (entry - origin).normalized()
        }
        for (direction in axes + mouthDirections) {
            if (mouthDistance(origin, direction, pocket) == null || !reachesOpening(origin, direction, pocket)) continue
            if (none { (it - direction).length() <= 0.00001f }) add(direction)
        }
    }

    private fun reachesOpening(origin: Point, direction: Point, pocket: PocketDefinition): Boolean =
        (origin + direction * (pocket.center - origin).dot(direction) - pocket.center).length() < pocket.radius

    fun mouthDistance(origin: Point, direction: Point, pocket: PocketDefinition): Float? {
        val approach = direction.dot(pocket.outward)
        if (approach <= 0.000001f) return null
        val distance = (pocket.mouthCenter - origin).dot(pocket.outward) / approach
        if (distance <= 0f) return null
        val entry = origin + direction * distance
        return distance.takeIf { abs((entry - pocket.mouthCenter).dot(pocket.lateral)) <= pocket.width / 2f }
    }
}
