package io.github.ffeltrinelli.pool.render

import io.github.ffeltrinelli.pool.model.Point
import kotlin.math.max

/** Preserve the centre trajectory, then connect its end to the obstacle's contact surface. */
object AimGuideGeometry {
    private const val DASH = 0.025f
    private const val PITCH = 0.043f

    fun segments(origin: Point, cueCenterAtImpact: Point, contactPoint: Point): List<Pair<Point, Point>> = buildList {
        val length = (cueCenterAtImpact - origin).length()
        val direction = (cueCenterAtImpact - origin).normalized()
        // Work backwards so a gap in the dash pattern can never hide the impact endpoint.
        var finish = length
        while (finish > 0.00001f) {
            val start = max(0f, finish - DASH)
            add((origin + direction * start) to (origin + direction * finish))
            finish -= PITCH
        }
        if ((contactPoint - cueCenterAtImpact).length() > 0.00001f) {
            add(cueCenterAtImpact to contactPoint)
        }
    }
}
