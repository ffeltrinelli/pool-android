package io.github.ffeltrinelli.pool.render

import io.github.ffeltrinelli.pool.model.Point
import kotlin.math.max

/** Extend the centre trajectory by its contact clearance, always on the original aiming axis. */
object AimGuideGeometry {
    private const val DASH = 0.025f
    private const val PITCH = 0.043f

    fun segments(origin: Point, cueCenterAtImpact: Point, contactPoint: Point): List<Pair<Point, Point>> = buildList {
        val direction = (cueCenterAtImpact - origin).normalized()
        val clearance = (contactPoint - cueCenterAtImpact).length()
        val end = cueCenterAtImpact + direction * clearance
        val length = (end - origin).length()
        // Work backwards so a gap in the dash pattern can never hide the impact endpoint.
        var finish = length
        while (finish > 0.00001f) {
            val start = max(0f, finish - DASH)
            add((origin + direction * start) to (origin + direction * finish))
            finish -= PITCH
        }
    }
}
