package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.view.*
import org.junit.Assert.*
import org.junit.Test

class RackAimTest {
    @Test fun `fresh rack clears pending strokes and aligns cue and player camera with apex`() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val rack = TableGeometry.rack().toMap()
        val cue = rack.getValue(0)
        val gesture = CueGesture()
        val views = GameViews()
        views.resize(1080, 2400)
        for (fired in listOf(false, true)) {
            views.follow(GameSession().apply { confirmPlacement() })
            val start = cue + Point(-0.3f, 0f)
            gesture.begin(PointerSample(0, start, 0.0), cue)
            gesture.move(PointerSample(0, start + Point(-0.02f, 0f), 0.05))
            gesture.move(PointerSample(0, start + Point(-0.05f, 0f), 0.1))
            if (fired) gesture.move(PointerSample(0, start + Point(0.1f, 0f), 0.2))
            views.update(cue, gesture.direction, false, 0.65f)
            gesture.resetForRack(cue, Point(0f, TableGeometry.FOOT_SPOT))
            views.reset()
            views.update(cue, gesture.direction, true)
            assertEquals(Point(0f, -1f), gesture.direction)
            assertEquals(CuePhase.IDLE, gesture.phase)
            assertEquals(0.05f, gesture.tipGap, 0.00001f)
            assertNull(gesture.move(PointerSample(0, Point(1f, 1f), 0.3)))
            assertEquals(0f, views.active.camera.direction.x, 0.00001f)
            assertTrue(views.active.camera.direction.z < 0f)
            assertEquals(cue.x, views.active.camera.position.x, 0.00001f)
            gesture.begin(PointerSample(0, cue + Point(0.3f, 0f), 1.0), cue)
            assertEquals(CuePhase.AIMING, gesture.phase)
            val aimed = gesture.direction
            gesture.cancel()
            assertEquals(aimed, gesture.direction)
        }
    }
}
