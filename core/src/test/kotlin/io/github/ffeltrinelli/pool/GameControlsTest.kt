package io.github.ffeltrinelli.pool

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.ui.*
import io.github.ffeltrinelli.pool.view.ViewMode
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class GameControlsTest {
    private class Preference(var saved: ViewMode = ViewMode.TOP) : ViewPreference {
        override fun load() = saved
        override fun save(mode: ViewMode) { saved = mode }
    }
    private class Effects : ControlEffects {
        var cancelled = 0
        var paused = false
        var restarts = 0
        var selected = ViewMode.TOP
        override fun cancelGesture() { cancelled++ }
        override fun pauseGame() { paused = true }
        override fun resumeGame() { paused = false }
        override fun restartGame() { restarts++ }
        override fun selectView(mode: ViewMode) { selected = mode }
    }

    @Test fun `settings blocks gameplay cancels stroke and pauses until closed`() {
        val effects = Effects()
        val controls = GameControls(Preference(), effects)
        assertTrue(controls.acceptsGameplay)
        controls.handle(HudAction.SETTINGS)
        assertTrue(controls.settingsOpen && controls.suspended && effects.paused)
        assertFalse(controls.acceptsGameplay)
        assertEquals(1, effects.cancelled)
        controls.handle(HudAction.BLOCK)
        assertEquals(0, effects.restarts)
        controls.handle(HudAction.CLOSE)
        assertTrue(controls.acceptsGameplay)
        assertFalse(effects.paused)
    }

    @Test fun `background resume does not dismiss or resume open settings`() {
        val effects = Effects()
        val controls = GameControls(Preference(), effects)
        controls.handle(HudAction.SETTINGS)
        controls.pause()
        controls.resume()
        assertTrue(controls.settingsOpen && effects.paused)
        controls.pause()
        controls.handle(HudAction.CLOSE)
        assertTrue(controls.suspended && effects.paused)
        controls.resume()
        assertFalse(controls.suspended || effects.paused)
    }

    @Test fun `mode selection persists cancels input and leaves rack untouched`() {
        val preference = Preference()
        val effects = Effects()
        val controls = GameControls(preference, effects)
        controls.handle(HudAction.SETTINGS)
        controls.handle(HudAction.PLAYER_VIEW)
        assertEquals(ViewMode.PLAYER, controls.mode)
        assertEquals(ViewMode.PLAYER, effects.selected)
        assertEquals(2, effects.cancelled)
        assertEquals(0, effects.restarts)
        assertTrue(effects.paused)
        val relaunched = GameControls(preference, Effects())
        assertEquals(ViewMode.PLAYER, relaunched.mode)
        controls.handle(HudAction.RESTART)
        assertEquals(1, effects.restarts)
        assertEquals(ViewMode.PLAYER, controls.mode)
        assertFalse(controls.settingsOpen || controls.suspended)
    }

    @Test fun `saved preference defaults to top tolerates unknown values and flushes selection`() {
        val values = mutableMapOf<String, String>()
        var flushes = 0
        val preferences = Proxy.newProxyInstance(Preferences::class.java.classLoader, arrayOf(Preferences::class.java)) { proxy, method, args ->
            when (method.name) {
                "getString" -> values[args[0]] ?: args[1]
                "putString" -> { values[args[0] as String] = args[1] as String; proxy }
                "flush" -> { flushes++; null }
                else -> error("Unexpected preference call ${method.name}")
            }
        } as Preferences
        val store = SavedViewPreference(preferences)
        assertEquals(ViewMode.TOP, store.load())
        values["view-mode"] = "obsolete"
        assertEquals(ViewMode.TOP, store.load())
        store.save(ViewMode.PLAYER)
        assertEquals(ViewMode.PLAYER, SavedViewPreference(preferences).load())
        assertEquals(1, flushes)
    }

    @Test fun `HUD routes gear modes results and outside modal taps at multiple sizes`() {
        for ((width, height) in listOf(1080 to 2400, 420 to 933, 540 to 1200)) {
            val layout = HudLayout(width, height)
            fun hit(rect: com.badlogic.gdx.math.Rectangle, open: Boolean = false, finished: Boolean = false) =
                layout.hit(((rect.x + rect.width / 2) * width / 420).toInt(),
                    ((layout.uiHeight - rect.y - rect.height / 2) * height / layout.uiHeight).toInt(), open, finished)
            assertTrue(layout.gear.width >= 48f && layout.gear.height >= 48f)
            assertEquals(HudAction.SETTINGS, hit(layout.gear))
            assertEquals(HudAction.SETTINGS, hit(layout.gear, finished = true))
            assertEquals(HudAction.TOP_VIEW, hit(layout.top, true))
            assertEquals(HudAction.PLAYER_VIEW, hit(layout.player, true))
            assertEquals(HudAction.CLOSE, hit(layout.close, true))
            assertEquals(HudAction.RESTART, hit(layout.restart, true))
            assertEquals(HudAction.RESTART, hit(layout.playAgain, finished = true))
            assertEquals(HudAction.BLOCK, layout.hit(0, 0, true, false))
            assertNull(layout.hit(0, 0, false, false))
        }
    }
}
