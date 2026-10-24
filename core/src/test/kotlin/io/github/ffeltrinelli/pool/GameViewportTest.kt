package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.ui.HudLayout
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.view.TopView
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.*
import org.junit.Test

class GameViewportTest {
    @Test fun `reserved HUD and game viewport partition the window at normal and high DPI`() {
        for ((width, height) in listOf(420 to 933, 1080 to 2400, 1080 to 2296)) {
            for (placing in listOf(false, true)) {
                val layout = HudLayout(width, height, placing)
                assertEquals(height, layout.gameTop + layout.gameHeight)
                assertFalse(layout.containsGame(width / 2, layout.gameTop - 1))
                assertTrue(layout.containsGame(width / 2, layout.gameTop))
                assertEquals(0, layout.gameScreenY(layout.gameTop))
                assertEquals(layout.gameHeight - 1, layout.gameScreenY(height - 1))
                assertEquals(layout.gameHeight * 2, layout.renderHeight(height * 2))
            }
        }
    }
    @Test fun `placement confirmation stays inside the top strip and clear of notifications`() {
        val layout = HudLayout(420, 933, placementVisible = true)
        val button = layout.confirmPlacement
        assertTrue(button.y >= layout.uiHeight - layout.headerHeight)
        assertTrue(button.y + button.height <= layout.uiHeight - 76f)
        assertTrue(button.height >= 48f)
        assertTrue(layout.confirmsPlacement(210, 104))
        assertFalse(layout.containsGame(210, 104))
        assertEquals(76f, HudLayout(420, 933).headerHeight, 0f)
    }

    @Test fun `top view touches match projected table points below the reserved header`() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val layout = HudLayout(1080, 2296)
        val view = TopView().apply { resize(1080, layout.gameHeight) }
        for (point in listOf(Point(0f, 0.58f),
            Point(-0.4f, -0.8f), Point(0.4f, 0.8f))) {
            val projected = view.camera.project(Vector3(point.x, 0f, point.z),
                0f, 0f, 1080f, layout.gameHeight.toFloat())
            val rawY = (2296 - projected.y).toInt()
            val mapped = view.beginInput(projected.x.toInt(), layout.gameScreenY(rawY), point, point)
            assertEquals(point.x, mapped.x, 0.003f)
            assertEquals(point.z, mapped.z, 0.003f)
        }
    }

}
