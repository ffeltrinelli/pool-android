package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import kotlin.math.abs

/** Ball-centre reflection planes derived from the actual straight cushion noses. */
internal class ReboundGeometry {
    data class Face(val a: Point, val b: Point, val normal: Point) {
        fun mirror(point: Point) = point - normal * (2f * (point - a).dot(normal))
        fun reflect(direction: Point) = direction - normal * (2f * direction.dot(normal))
        fun bounce(origin: Point, destination: Point): Point? {
            val direction = mirror(destination) - origin
            val approach = direction.dot(normal)
            if (approach >= -0.000001f) return null
            val fraction = (a - origin).dot(normal) / approach
            if (fraction <= 0f || fraction >= 1f) return null
            val point = origin + direction * fraction
            val edge = b - a
            val along = (point - a).dot(edge) / edge.dot(edge)
            // Rounded cushion ends and jaws are not mirror surfaces.
            val margin = TableGeometry.BALL_RADIUS / edge.length()
            return point.takeIf { along > margin && along < 1f - margin }
        }
    }

    private val predictor = AimPredictor()
    val faces = PocketGeometry.cushions.flatMap { cushion ->
        cushion.outline.indices.mapNotNull { index ->
            val a = cushion.outline[index]
            val b = cushion.outline[(index + 1) % cushion.outline.size]
            val nose = (abs(a.x - b.x) < 0.000001f && abs(abs(a.x) - TableGeometry.HALF_WIDTH) < 0.000001f) ||
                (abs(a.z - b.z) < 0.000001f && abs(abs(a.z) - TableGeometry.HALF_LENGTH) < 0.000001f)
            if (!nose) null else {
                val edge = (b - a).normalized()
                val normal = Point(edge.z, -edge.x)
                Face(a + normal * TableGeometry.BALL_RADIUS, b + normal * TableGeometry.BALL_RADIUS, normal)
            }
        }
    }

    fun reachesBounce(origin: Point, bounce: Point, balls: List<AimBall>): Boolean {
        val path = predictor.predict(origin, (bounce - origin).normalized(), balls)
        return path.targetBallId == null && (path.end - bounce).length() < 0.002f
    }

    fun afterBounce(bounce: Point, direction: Point, balls: List<AimBall>): AimPath =
        predictor.predict(bounce + direction * 0.0002f, direction, balls)

    fun firstContact(cue: Point, direction: Point, balls: List<AimBall>, kind: ShotKind): Int? {
        val path = predictor.predict(cue, direction, balls)
        if (!kind.isKick) return path.targetBallId
        if (path.targetBallId != null) return null
        val face = faces.firstOrNull { face ->
            abs((path.end - face.a).dot(face.normal)) < 0.002f &&
                direction.dot(face.normal) < 0f &&
                (path.end - face.a).dot(face.b - face.a) > 0f &&
                (path.end - face.b).dot(face.a - face.b) > 0f
        } ?: return null
        return afterBounce(path.end, face.reflect(direction).normalized(), balls).targetBallId
    }
}
