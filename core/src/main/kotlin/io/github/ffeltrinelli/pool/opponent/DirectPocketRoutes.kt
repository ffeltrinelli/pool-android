package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import kotlin.math.abs

/** Pocket-center and table-axis routes; axis routes include pots running along a cushion. */
internal object DirectPocketRoutes {
    private val axes = listOf(Point(-1f, 0f), Point(1f, 0f), Point(0f, -1f), Point(0f, 1f))

    fun directions(origin: Point, pocket: PocketDefinition): List<Point> {
        val centered = (pocket.center - origin).normalized()
        return listOf(centered) + axes.filter { direction ->
            mouthDistance(origin, direction, pocket) != null &&
                (origin + direction * (pocket.center - origin).dot(direction) - pocket.center).length() < pocket.radius
        }.filter { (it - centered).length() > 0.00001f }
    }

    fun mouthDistance(origin: Point, direction: Point, pocket: PocketDefinition): Float? {
        val approach = direction.dot(pocket.outward)
        if (approach <= 0.000001f) return null
        val distance = (pocket.mouthCenter - origin).dot(pocket.outward) / approach
        if (distance <= 0f) return null
        val entry = origin + direction * distance
        return distance.takeIf { abs((entry - pocket.mouthCenter).dot(pocket.lateral)) <= pocket.width / 2f }
    }
}
