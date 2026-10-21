package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class AimPredictorTest {
    private val predictor = AimPredictor()
    private val radius = TableGeometry.BALL_RADIUS
    private val origin = Point(0f, 0.5f)
    private val forward = Point(0f, -1f)

    @Test fun `nearest contacted ball wins regardless of ordering and cue ball is excluded`() {
        val path = predictor.predict(origin, forward, listOf(AimBall(0, origin), AimBall(2, Point(0f, -0.5f)), AimBall(1, Point(0f, 0f))))
        assertEquals(1, path.targetBallId)
        assertEquals(radius * 2, path.end.z, 0.00001f)
    }

    @Test fun `glancing and tangent hits account for both radii while misses are ignored`() {
        for (offset in listOf(0f, radius, radius * 2)) {
            val path = predictor.predict(origin, forward, listOf(AimBall(1, Point(offset, 0f))))
            assertEquals(1, path.targetBallId)
            assertEquals(sqrt((4 * radius * radius - offset * offset).coerceAtLeast(0f)), path.end.z, 0.0001f)
        }
        assertNull(predictor.predict(origin, forward, listOf(AimBall(1, Point(radius * 2 + 0.001f, 0f)))).targetBallId)
    }

    @Test fun `rail stops cue centre one radius away and blocks balls beyond it`() {
        val path = predictor.predict(origin, forward, listOf(AimBall(1, Point(0f, -1.3f))))
        assertNull(path.targetBallId)
        assertEquals(-TableGeometry.HALF_LENGTH + radius, path.end.z, 0.00001f)
    }

    @Test fun `all pocket mouths terminate direct paths without extending into interiors`() {
        for (pocket in PocketGeometry.pockets) {
            val start = pocket.mouthCenter - pocket.outward * 0.3f
            val path = predictor.predict(start, pocket.outward, emptyList())
            assertNull(path.targetBallId)
            assertEquals(pocket.mouthCenter.x, path.end.x, 0.0001f)
            assertEquals(pocket.mouthCenter.z, path.end.z, 0.0001f)
        }
    }

    @Test fun `jaw corners obstruct near mouth paths`() {
        val pocket = PocketGeometry.pockets.last()
        val start = pocket.mouthCenter - pocket.outward * 0.3f + pocket.lateral * (pocket.width / 2 - radius / 2)
        val path = predictor.predict(start, pocket.outward, emptyList())
        assertTrue((path.end - start).length() < 0.3f)
        assertNull(path.targetBallId)
    }

    @Test fun `touching a rail or ball behind the cue does not block an away shot`() {
        val cue = Point(0f, -TableGeometry.HALF_LENGTH + radius)
        val away = Point(0f, 1f)
        val path = predictor.predict(cue, away, emptyList())
        assertEquals(TableGeometry.HALF_LENGTH - radius, path.end.z, 0.0001f)
        val behind = AimBall(1, origin - forward * (radius * 2))
        val ballPath = predictor.predict(origin, forward, listOf(behind))
        assertNull(ballPath.targetBallId)
        assertEquals(-TableGeometry.HALF_LENGTH + radius, ballPath.end.z, 0.0001f)
    }

    @Test fun `removing a pocketed target exposes the next live ball`() {
        val live = listOf(AimBall(2, Point(0f, -0.5f)))
        assertEquals(2, predictor.predict(origin, forward, live).targetBallId)
    }
}
