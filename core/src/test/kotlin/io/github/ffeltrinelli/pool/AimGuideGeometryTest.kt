package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.render.AimGuideGeometry
import org.junit.Assert.*
import org.junit.Test

class AimGuideGeometryTest {
    private val origin = Point(0f, 0.5f)
    private val direction = Point(0f, -1f)
    private val radius = TableGeometry.BALL_RADIUS
    private val predictor = AimPredictor()

    @Test fun `guide extends straight along original aim for head on glancing and tangent collisions`() {
        for (offset in listOf(0f, radius, radius * 2)) {
            val target = Point(offset, 0f)
            val path = predictor.predict(origin, direction, listOf(AimBall(1, target)))
            assertEquals(radius, (path.contactPoint - target).length(), 0.00001f)
            assertEquals(radius, (path.contactPoint - path.end).length(), 0.00001f)
            val segments = AimGuideGeometry.segments(origin + direction * radius, path.end, path.contactPoint)
            val end = path.end + direction * radius
            assertTrue((end - segments.first().second).length() < 0.00001f)
            for ((a, b) in segments) {
                assertEquals(0f, a.x, 0.00001f)
                assertEquals(0f, b.x, 0.00001f)
            }
        }
    }

    @Test fun `guide reaches cushion face instead of stopping one radius short`() {
        val path = predictor.predict(origin, direction, emptyList())
        assertEquals(-TableGeometry.HALF_LENGTH, path.contactPoint.z, 0.00001f)
        assertEquals(-TableGeometry.HALF_LENGTH + radius, path.end.z, 0.00001f)
    }

    @Test fun `guide reaches jaw surface and pocket mouth without overshooting`() {
        for (pocket in PocketGeometry.pockets) {
            val start = pocket.mouthCenter - pocket.outward * 0.3f
            val path = predictor.predict(start, pocket.outward, emptyList())
            assertTrue((path.contactPoint - pocket.mouthCenter).length() < 0.0001f)
        }
        val pocket = PocketGeometry.pockets.last()
        val start = pocket.mouthCenter - pocket.outward * 0.3f + pocket.lateral * (pocket.width / 2 - radius / 2)
        val path = predictor.predict(start, pocket.outward, emptyList())
        assertEquals(radius, (path.contactPoint - path.end).length(), 0.0001f)
    }

    @Test fun `oblique rail contact never turns the final dash toward the rail normal`() {
        val forward = Point(0.2f, -1f).normalized()
        val path = predictor.predict(origin, forward, emptyList())
        val start = origin + forward * radius
        val segments = AimGuideGeometry.segments(start, path.end, path.contactPoint)
        for ((a, b) in segments) {
            for (point in listOf(a, b)) {
                val delta = point - origin
                assertEquals(0f, delta.x * forward.z - delta.z * forward.x, 0.00001f)
            }
        }
    }

    @Test fun `last dash reaches endpoint for every dash phase including short paths`() {
        for (i in 1..1000) {
            val end = Point(0f, i * 0.001f)
            val segments = AimGuideGeometry.segments(Point(0f, 0f), end, end)
            assertTrue((end - segments.first().second).length() < 0.000001f)
            assertTrue(segments.all { (a, b) -> a.z >= -0.000001f && b.z <= end.z + 0.000001f })
        }
        assertTrue(AimGuideGeometry.segments(origin, origin, origin).isEmpty())
    }
}
