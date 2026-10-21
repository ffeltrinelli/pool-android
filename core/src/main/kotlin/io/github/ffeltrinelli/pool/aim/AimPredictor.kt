package io.github.ffeltrinelli.pool.aim

import io.github.ffeltrinelli.pool.model.*
import kotlin.math.*

data class AimBall(val id: Int, val position: Point)
data class AimPath(val end: Point, val targetBallId: Int?)

/** Direct cue-centre travel, without advancing Bullet or predicting rebounds. */
class AimPredictor {
    fun predict(origin: Point, direction: Point, balls: List<AimBall>): AimPath {
        val forward = direction.normalized()
        val radius = TableGeometry.BALL_RADIUS
        var distance = Float.POSITIVE_INFINITY
        var target: Int? = null
        for (cushion in PocketGeometry.cushions) {
            val polygon = cushion.outline
            for (i in polygon.indices) {
                distance = min(distance, capsuleContact(origin, forward, polygon[i], polygon[(i + 1) % polygon.size], radius))
            }
        }
        for (pocket in PocketGeometry.pockets) {
            distance = min(distance, segmentContact(origin, forward, pocket.mouth(-1f), pocket.mouth(1f)))
        }
        for (ball in balls) {
            if (ball.id == 0) continue
            val contact = circleContact(origin, forward, ball.position, radius * 2f)
            if (contact < distance) {
                distance = contact
                target = ball.id
            }
        }
        // Invalid origins outside the defined playing surface may have no forward intersection.
        if (!distance.isFinite()) distance = 0f
        return AimPath(origin + forward * distance, target)
    }

    private fun circleContact(origin: Point, direction: Point, center: Point, radius: Float): Float {
        val offset = origin - center
        val along = offset.dot(direction)
        val separation = offset.dot(offset) - radius * radius
        if (separation <= 0f) return if (along < 0f) 0f else Float.POSITIVE_INFINITY
        val discriminant = along * along - separation
        if (discriminant < -0.0000001f) return Float.POSITIVE_INFINITY
        val contact = -along - sqrt(discriminant.coerceAtLeast(0f))
        return if (contact >= 0f) contact else Float.POSITIVE_INFINITY
    }

    private fun capsuleContact(origin: Point, direction: Point, a: Point, b: Point, radius: Float): Float {
        val edge = b - a
        val length = edge.length()
        val axis = edge * (1f / length)
        val normal = Point(-axis.z, axis.x)
        val offset = origin - a
        val projection = offset.dot(axis)
        var distance = min(circleContact(origin, direction, a, radius), circleContact(origin, direction, b, radius))
        val approach = direction.dot(normal)
        if (abs(approach) > 0.000001f) {
            for (side in listOf(-1f, 1f)) {
                if (side * approach >= 0f) continue
                val signedDistance = side * offset.dot(normal)
                if (signedDistance in 0f..radius && projection in 0f..length) return 0f
                val travel = (side * radius - offset.dot(normal)) / approach
                val along = projection + travel * direction.dot(axis)
                if (travel >= 0f && along in 0f..length) distance = min(distance, travel)
            }
        }
        return distance
    }

    private fun segmentContact(origin: Point, direction: Point, a: Point, b: Point): Float {
        val edge = b - a
        val cross = direction.x * edge.z - direction.z * edge.x
        if (abs(cross) < 0.000001f) return Float.POSITIVE_INFINITY
        val offset = a - origin
        val travel = (offset.x * edge.z - offset.z * edge.x) / cross
        val fraction = (offset.x * direction.z - offset.z * direction.x) / cross
        return if (travel >= 0f && fraction in 0f..1f) travel else Float.POSITIVE_INFINITY
    }
}
