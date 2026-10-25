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
        assertEquals(2 * TableGeometry.OUTER_HALF_WIDTH + 0.08f, view.camera.viewportWidth, 0.0001f)
        assertEquals(Vector3(0f, -1f, 0f), view.camera.direction)
        val center = view.beginInput(540, 1200, cue, forward)
        assertEquals(0f, center.x, 0.0001f)
        assertEquals(0f, center.z, 0.0001f)
        assertTrue(view.moveInput(540, 1300, CuePhase.AIMING, forward).z > center.z)
    }

    @Test fun `top framing preserves outer rails across aspect ratios and HUD heights`() {
        for ((width, height) in listOf(1080 to 2400, 1080 to 1920, 720 to 1600, 1080 to 1080, 1600 to 1080)) {
            for (placing in listOf(false, true)) {
                val layout = io.github.ffeltrinelli.pool.ui.HudLayout(width, height, placing)
                val view = TopView().apply { resize(width, layout.gameHeight) }
                for (x in listOf(-TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_WIDTH)) {
                    for (z in listOf(-TableGeometry.OUTER_HALF_LENGTH, TableGeometry.OUTER_HALF_LENGTH)) {
                        val projected = Vector3(x, 0f, z).prj(view.camera.combined)
                        assertTrue(abs(projected.x) < 1f && abs(projected.y) < 1f)
                    }
                }
                assertTrue(view.cueLength(Point(0f, 0f), forward) <= CueGeometry.DISPLAY_LENGTH)
            }
        }
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
            assertTrue(view.cueLength(point - direction * 0.279f, direction) in 0.04f..CueGeometry.DISPLAY_LENGTH)
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
        assertTrue("Overview should stay close enough to read the moving balls", overview.y < 2.35f)
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

    @Test fun `closer overview keeps central play visible at every yaw and portrait viewport`() {
        for ((width, height) in listOf(1080 to 2400, 1080 to 2297, 720 to 1600)) {
            for (degrees in 0 until 360 step 15) {
                val angle = Math.toRadians(degrees.toDouble())
                val heading = Point(sin(angle).toFloat(), -cos(angle).toFloat())
                val view = PlayerView().apply { resize(width, height); update(Point(0.5f, 1f), heading, true) }
                view.update(null, heading, false, 0.65f)
                for (x in listOf(-0.30f, 0.30f)) for (z in listOf(-0.55f, 0.55f)) for (y in listOf(0f, 0.06f)) {
                    val ndc = Vector3(x, y, z).prj(view.camera.combined)
                    assertTrue("Central play clipped at $degrees degrees: $ndc", abs(ndc.x) <= 0.961f && ndc.y in -0.921f..0.761f && ndc.z in -1f..1f)
                }
            }
        }
    }

    @Test fun `closer overview intentionally allows outer table edges offscreen`() {
        val view = PlayerView().apply { resize(1080, 2400); update(cue, forward, true) }
        view.update(null, forward, false, 0.65f)
        assertEquals(-sqrt(0.5f), view.camera.direction.y, 0.0001f)
        val railCorner = Vector3(0.73f, 0f, 1.29f).prj(view.camera.combined)
        assertTrue("The closer overview must no longer be constrained by the outer rails", railCorner.x > 1f)
        assertTrue(view.camera.position.y < 2.35f)
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

    @Test fun `rack restart resets overview for human aiming`() {
        val views = GameViews()
        views.resize(1080, 2400)
        views.follow(GameSession().apply { confirmPlacement() })
        views.update(cue, forward, true)
        val aimPosition = views.active.camera.position.cpy()
        views.update(null, forward, false, 0.65f)
        assertTrue(views.active.camera.position.y > aimPosition.y)
        views.reset()
        assertTrue(views.active is PlayerView)
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
            assertEquals(0.050f, input.gesture.tipGap, 0.0001f)
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
        assertEquals(0.050f + 50 * 1.92f / 1080, input.gesture.tipGap, 0.0001f)
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
        assertEquals(0.050f, input.gesture.tipGap, 0.0001f)
        input.move(600, 1425)
        assertEquals(CuePhase.STROKING, input.gesture.phase)
        assertEquals(heading.x, input.gesture.direction.x, 0.0001f)
        assertTrue(input.gesture.tipGap > 0.06f)
    }

    @Test fun `player input retains world speed power independently of overhead zoom`() {
        val player = Input()
        val actual = listOf(20, 40, 60).mapNotNull { player.move(540, 1400 - it) }.single()
        val gesture = CueGesture()
        val start = cue - forward * 0.30f
        gesture.begin(PointerSample(0, start, 0.0), cue)
        val expected = listOf(20, 40, 60).mapIndexedNotNull { index, pixels ->
            val point = start + forward * (pixels * 1.92f / 1080)
            gesture.move(PointerSample(0, point, (index + 1) * 0.05))
        }.single()
        assertEquals(expected.impulse, actual.impulse, 0.0001f)
    }

    @Test fun `automatic camera follows ownership through placement motion results and restart`() {
        val session = GameSession()
        val views = GameViews().apply { resize(1080, 2400) }
        fun follow() = views.follow(session)
        follow()
        assertTrue(views.active is TopView)
        session.confirmPlacement()
        follow()
        assertTrue(views.active is PlayerView)
        session.beginShot()
        follow()
        assertFalse(session.humanTurn)
        assertTrue(views.active is PlayerView)
        session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
        session.accept(PhysicsEvent.Settled)
        follow()
        assertEquals(Player.COMPUTER, session.player)
        assertTrue(views.active is TopView)
        session.beginShot()
        follow()
        assertTrue(views.active is TopView)
        session.accept(PhysicsEvent.Pocketed(8))
        session.accept(PhysicsEvent.Settled)
        follow()
        assertTrue(session.finished)
        assertTrue(views.active is TopView)
        session.restart()
        views.reset()
        follow()
        assertTrue(views.active is TopView)
        session.confirmPlacement()
        follow()
        assertEquals(Player.COMPUTER, session.player)
        assertTrue(views.active is TopView)
        session.restart()
        follow()
        session.confirmPlacement()
        follow()
        assertTrue(views.active is PlayerView)
    }

    @Test fun `human foul placement returns directly to close player aim when confirmed`() {
        val session = GameSession().apply { confirmPlacement() }
        val views = GameViews().apply { resize(1080, 2400); follow(session) }
        views.update(cue, forward, true)
        val close = views.active.camera.position.cpy()
        views.update(null, forward, false, 0.65f)
        session.beginShot()
        session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
        session.accept(PhysicsEvent.Settled)
        session.beginShot()
        session.accept(PhysicsEvent.Pocketed(0))
        session.accept(PhysicsEvent.Settled)
        views.follow(session)
        assertEquals(Player.HUMAN, session.player)
        assertNotNull(session.placement)
        assertTrue(views.active is TopView)
        session.confirmPlacement()
        views.follow(session)
        views.update(cue, forward, true)
        assertTrue(views.active is PlayerView)
        assertEquals(close, views.active.camera.position)
    }

    @Test fun `computer retained turn stays overhead and handoff uses settled human aim`() {
        val session = GameSession().apply { confirmPlacement() }
        val views = GameViews().apply { resize(1080, 2400); follow(session) }
        views.update(cue, forward, true)
        session.beginShot()
        session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
        session.accept(PhysicsEvent.Settled)
        views.follow(session)
        val overhead = views.active
        session.beginShot()
        session.accept(PhysicsEvent.Contacts(setOf(1), emptySet()))
        session.accept(PhysicsEvent.Pocketed(1))
        session.accept(PhysicsEvent.Settled)
        views.follow(session)
        assertEquals(Player.COMPUTER, session.player)
        assertSame(overhead, views.active)
        session.beginShot()
        session.accept(PhysicsEvent.Contacts(setOf(2), setOf(2)))
        session.accept(PhysicsEvent.Settled)
        views.follow(session)
        assertEquals(Player.HUMAN, session.player)
        val settled = Point(0.3f, -0.2f)
        views.update(settled, forward, true)
        assertTrue(views.active is PlayerView)
        assertEquals(settled.x, views.active.camera.position.x, 0.0001f)
        assertEquals(settled.z + 0.95f, views.active.camera.position.z, 0.0001f)
        val paused = views.active.camera.position.cpy()
        repeat(5) {
            views.follow(session)
            views.update(settled, forward, true, 0f)
        }
        assertEquals(paused, views.active.camera.position)
    }

    @Test fun `unclipped cues use seventy centimetres and still clip near rails`() {
        val view = TopView().apply { resize(1080, 2400) }
        assertEquals(0.70f, view.cueLength(Point(0f, 0f), forward), 0.0001f)
        assertTrue(view.cueLength(Point(-0.7f, 0f), Point(1f, 0f)) < 0.70f)
    }
}
