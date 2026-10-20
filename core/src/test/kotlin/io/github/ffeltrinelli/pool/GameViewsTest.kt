package io.github.ffeltrinelli.pool

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.view.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import kotlin.math.*

class GameViewsTest {
    companion object { @JvmStatic @BeforeClass fun natives() { GdxNativesLoader.load() } }
    private val cue = Point(0f, 0.58f)
    private val forward = Point(0f, -1f)

    @Test fun `top view retains overhead framing and touch mapping`() {
        val view = TopView()
        view.resize(1080, 2400)
        assertEquals(1.92f, view.camera.viewportWidth, 0.0001f)
        assertEquals(Vector3(0f, -1f, 0f), view.camera.direction)
        val center = view.beginInput(540, 1200, cue, forward)
        assertEquals(0f, center.x, 0.0001f)
        assertEquals(0f, center.z, 0.0001f)
        assertTrue(view.moveInput(540, 1300, CuePhase.AIMING, forward).z > center.z)
    }

    @Test fun `player camera follows aim at fixed elevation even near every rail`() {
        val view = PlayerView()
        view.resize(1080, 2400)
        for (point in TableGeometry.pockets + cue) for (direction in listOf(forward, Point(1f, 0f), Point(0f, 1f), Point(-1f, 0f))) {
            view.update(point, direction, true)
            val offset = Vector3(point.x, TableGeometry.BALL_RADIUS, point.z).sub(view.camera.position)
            assertEquals(0f, offset.nor().dst(view.camera.direction), 0.0001f)
            assertEquals(0.95f, hypot(view.camera.position.x - point.x, view.camera.position.z - point.z), 0.0001f)
            assertEquals(-sqrt(0.5f), view.camera.direction.y, 0.0001f)
            assertTrue(view.cueLength(point - direction * 0.279f, direction) in 0.04f..0.65f)
        }
    }

    @Test fun `player camera pulls back smoothly holds the shot yaw and returns after settling`() {
        val view = PlayerView()
        view.resize(1080, 2400)
        view.update(cue, forward, true)
        val aimingPosition = view.camera.position.cpy()
        val direction = view.camera.direction.cpy()
        view.update(Point(0.4f, -0.8f), Point(1f, 0f), false, 0f)
        assertEquals(aimingPosition, view.camera.position)
        view.update(Point(0.4f, -0.8f), Point(1f, 0f), false, 0.325f)
        val halfway = view.camera.position.cpy()
        assertTrue(halfway.y > aimingPosition.y)
        view.update(null, Point(-1f, 0f), false, 0.325f)
        val overview = view.camera.position.cpy()
        assertTrue(overview.y > halfway.y)
        assertTrue("Overview should stay close enough to read the moving balls", overview.y < 2.9f)
        assertEquals(direction, view.camera.direction)
        view.update(null, forward, false, 1f)
        assertEquals(overview, view.camera.position)
        val settled = Point(0.4f, -0.8f)
        view.update(settled, forward, true, 0.225f)
        assertTrue(view.camera.position.y < overview.y)
        view.update(settled, forward, true, 0.225f)
        assertEquals(0.4f, view.camera.position.x, 0.0001f)
        assertEquals(settled.z + 0.95f, view.camera.position.z, 0.0001f)
    }

    @Test fun `full table remains inside overview at every yaw and portrait viewport`() {
        for ((width, height) in listOf(1080 to 2400, 1080 to 2297, 720 to 1600)) {
            for (degrees in 0 until 360 step 15) {
                val angle = Math.toRadians(degrees.toDouble())
                val heading = Point(sin(angle).toFloat(), -cos(angle).toFloat())
                val view = PlayerView().apply { resize(width, height); update(Point(0.5f, 1f), heading, true) }
                view.update(null, heading, false, 0.65f)
                for (x in listOf(-0.76f, 0.76f)) for (z in listOf(-1.32f, 1.32f)) for (y in listOf(-0.13f, 0.06f)) {
                    val ndc = Vector3(x, y, z).prj(view.camera.combined)
                    assertTrue("Table clipped at $degrees degrees: $ndc", abs(ndc.x) <= 0.961f && ndc.y in -0.921f..0.761f && ndc.z in -1f..1f)
                }
            }
        }
    }

    @Test fun `camera animation is frame partition independent and freezes with zero delta`() {
        fun view() = PlayerView().apply { resize(1080, 2400); update(cue, forward, true) }
        val single = view()
        val partitioned = view()
        single.update(null, forward, false, 0.325f)
        repeat(39) { partitioned.update(null, forward, false, 1f / 120) }
        assertTrue(single.camera.position.epsilonEquals(partitioned.camera.position, 0.0001f))
        val paused = single.camera.position.cpy()
        repeat(60) { single.update(null, forward, false, 0f) }
        assertEquals(paused, single.camera.position)
        single.resize(720, 1600)
        assertTrue(single.camera.position.epsilonEquals(paused, 0.0001f))
    }

    @Test fun `rack restart resets overview without changing the selected view`() {
        val views = GameViews()
        views.resize(1080, 2400)
        views.select(ViewMode.PLAYER)
        views.update(cue, forward, true)
        val aimPosition = views.active.camera.position.cpy()
        views.update(null, forward, false, 0.65f)
        assertTrue(views.active.camera.position.y > aimPosition.y)
        views.reset()
        assertEquals(ViewMode.PLAYER, views.mode)
        assertEquals(aimPosition, views.active.camera.position)
    }

    private class Input(val view: PlayerView = PlayerView(), val gesture: CueGesture = CueGesture()) {
        private var time = 0.0
        private val ball = Point(0f, 0.58f)
        init {
            view.resize(1080, 2400)
            gesture.begin(PointerSample(0, view.beginInput(540, 1400, ball, Point(0f, -1f)), time), ball)
        }
        fun move(x: Int, y: Int, seconds: Double = 0.05): ShotCommand? {
            time += seconds
            val point = view.moveInput(x, y, gesture.phase, gesture.direction)
            val shot = gesture.move(PointerSample(0, point, time))
            view.update(ball, gesture.direction, shot == null)
            return shot
        }
    }

    @Test fun `player touch preserves arbitrary aim and camera updates cannot feed into input`() {
        val view = PlayerView()
        view.resize(1080, 2400)
        val heading = Point(0.6f, -0.8f)
        val first = view.beginInput(100, 1800, cue, heading)
        assertEquals(heading.x, (cue - first).normalized().x, 0.0001f)
        view.update(Point(0.5f, -0.5f), Point(-1f, 0f), true)
        assertEquals(first, view.moveInput(100, 1800, CuePhase.AIMING, heading))
    }

    @Test fun `sideways circling turns player aim without pumping the cue`() {
        val input = Input()
        for (x in 550..800 step 10) {
            assertNull(input.move(x, 1400))
            assertEquals(0.032f, input.gesture.tipGap, 0.0001f)
            assertEquals(CuePhase.AIMING, input.gesture.phase)
        }
        assertTrue(input.gesture.direction.x > 0.8f)
        val camera = input.view.camera.direction
        assertEquals(input.gesture.direction.x, camera.x / cos(PI.toFloat() / 4), 0.001f)
    }

    @Test fun `practice strokes lock direction ignore sideways movement and strike exactly once`() {
        val input = Input()
        input.move(540, 1420)
        input.move(540, 1450)
        assertEquals(CuePhase.STROKING, input.gesture.phase)
        assertEquals(0.032f + 50 * 1.92f / 1080, input.gesture.tipGap, 0.0001f)
        input.move(740, 1450)
        assertEquals(Point(0f, -1f), input.gesture.direction)
        input.move(740, 1420)
        input.move(740, 1470)
        input.move(740, 1400)
        val shot = input.move(740, 1370)
        assertNotNull(shot)
        assertNull(input.move(740, 1300))
    }

    @Test fun `backswing after player circling retains yaw and stable preview`() {
        val input = Input()
        input.move(570, 1400)
        input.move(600, 1400)
        val heading = input.gesture.direction
        input.move(600, 1403)
        assertEquals(0.032f, input.gesture.tipGap, 0.0001f)
        input.move(600, 1425)
        assertEquals(CuePhase.STROKING, input.gesture.phase)
        assertEquals(heading.x, input.gesture.direction.x, 0.0001f)
        assertTrue(input.gesture.tipGap > 0.06f)
    }

    @Test fun `equal forward speeds yield same power across view input mappings`() {
        val player = Input()
        player.move(540, 1390)
        val actual = player.move(540, 1380)!!
        val gesture = CueGesture()
        val top = TopView().apply { resize(1080, 2400) }
        val start = top.beginInput(540, 1600, cue, forward)
        val ball = start + forward * 0.3f
        gesture.begin(PointerSample(0, start, 0.0), ball)
        gesture.move(PointerSample(0, top.moveInput(540, 1590, gesture.phase, gesture.direction), 0.05))
        val expected = gesture.move(PointerSample(0, top.moveInput(540, 1580, gesture.phase, gesture.direction), 0.10))!!
        assertEquals(expected.impulse, actual.impulse, 0.0001f)
    }
}
