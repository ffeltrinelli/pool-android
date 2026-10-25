package io.github.ffeltrinelli.pool

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class GameControlsTest {
    private class Preference : SettingsPreference {
        var difficulty = OpponentDifficulty.MEDIUM
        var difficultySaves = 0
        override fun loadDifficulty() = difficulty
        override fun saveDifficulty(difficulty: OpponentDifficulty) { this.difficulty = difficulty; difficultySaves++ }
        var aided = true
        override fun loadAidedAim() = aided
        override fun saveAidedAim(enabled: Boolean) { aided = enabled }
    }
    private class Effects : ControlEffects {
        var exits = 0
        override fun exitGame() { exits++ }
        var cancelled = 0
        var paused = false
        var restarts = 0
        override fun cancelGesture() { cancelled++ }
        override fun pauseGame() { paused = true }
        override fun resumeGame() { paused = false }
        override fun restartGame() { restarts++ }
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

    @Test fun `saved preferences ignore legacy modes and preserve aim and difficulty`() {
        val values = mutableMapOf<String, Any>()
        var flushes = 0
        val preferences = Proxy.newProxyInstance(Preferences::class.java.classLoader, arrayOf(Preferences::class.java)) { proxy, method, args ->
            when (method.name) {
                "getString", "getBoolean" -> {
                    check(args[0] !in listOf("play-mode", "view-mode"))
                    values[args[0]] ?: args[1]
                }
                "putString", "putBoolean" -> { values[args[0] as String] = args[1]; proxy }
                "flush" -> { flushes++; null }
                else -> error("Unexpected preference call ${method.name}")
            }
        } as Preferences
        val store = SavedSettingsPreference(preferences)
        values["view-mode"] = "obsolete"
        assertTrue(store.loadAidedAim())
        store.saveAidedAim(false)
        assertFalse(SavedSettingsPreference(preferences).loadAidedAim())
        for (legacy in listOf("SOLO", "COMPUTER", "obsolete")) {
            values["play-mode"] = legacy
            val controls = GameControls(SavedSettingsPreference(preferences), Effects())
            assertFalse(controls.aidedAim)
            assertEquals(io.github.ffeltrinelli.pool.model.Player.HUMAN, io.github.ffeltrinelli.pool.model.GameSession().player)
        }
        assertEquals(OpponentDifficulty.MEDIUM, store.loadDifficulty())
        values["opponent-difficulty"] = "obsolete"
        assertEquals(OpponentDifficulty.MEDIUM, store.loadDifficulty())
        store.saveDifficulty(OpponentDifficulty.EASY)
        assertEquals(OpponentDifficulty.EASY, SavedSettingsPreference(preferences).loadDifficulty())
        assertFalse(store.loadAidedAim())
        assertEquals(2, flushes)
    }

    @Test fun `aided aim is enabled initially saved independently and retained by restart`() {
        val preference = Preference()
        val effects = Effects()
        val controls = GameControls(preference, effects)
        assertTrue(controls.aidedAim)
        controls.handle(HudAction.SETTINGS)
        controls.handle(HudAction.TOGGLE_AIM)
        assertFalse(controls.aidedAim)
        assertTrue(controls.suspended)
        assertEquals(0, effects.restarts)
        controls.handle(HudAction.RESTART)
        assertFalse(controls.aidedAim)
        val relaunched = GameControls(preference, Effects())
        assertFalse(relaunched.aidedAim)
    }

    @Test fun `difficulty persists without restarting resuming or saving duplicate selections`() {
        val preference = Preference()
        val effects = Effects()
        val controls = GameControls(preference, effects)
        assertEquals(OpponentDifficulty.MEDIUM, controls.difficulty)
        controls.handle(HudAction.SETTINGS)
        controls.selectDifficulty(OpponentDifficulty.MEDIUM)
        assertEquals(0, preference.difficultySaves)
        for (level in OpponentDifficulty.entries) {
            controls.selectDifficulty(level)
            assertEquals(level, controls.difficulty)
            assertTrue(controls.settingsOpen && controls.suspended && effects.paused)
            assertEquals(0, effects.restarts)
            assertEquals(level, GameControls(preference, Effects()).difficulty)
        }
        controls.handle(HudAction.RESTART)
        assertEquals(OpponentDifficulty.HARD, controls.difficulty)
    }

    @Test fun `exit cancels input stops playback and requests one normal application exit`() {
        val effects = Effects()
        val controls = GameControls(Preference(), effects)
        controls.handle(HudAction.SETTINGS)
        controls.handle(HudAction.EXIT)
        assertEquals(1, effects.exits)
        assertTrue(effects.paused)
        assertEquals(2, effects.cancelled)
        assertEquals(0, effects.restarts)
    }

    @Test fun `HUD routes settings results and outside modal taps at multiple sizes`() {
        for ((width, height) in listOf(1080 to 2400, 420 to 933, 540 to 1200)) {
            val layout = HudLayout(width, height)
            fun hit(rect: com.badlogic.gdx.math.Rectangle, open: Boolean = false, finished: Boolean = false) =
                layout.hit(((rect.x + rect.width / 2) * width / 420).toInt(),
                    ((layout.uiHeight - rect.y - rect.height / 2) * height / layout.uiHeight).toInt(), open, finished)
            assertTrue(layout.gear.width >= 48f && layout.gear.height >= 48f)
            assertEquals(HudAction.SETTINGS, hit(layout.gear))
            assertEquals(HudAction.SETTINGS, hit(layout.gear, finished = true))
            assertEquals(HudAction.CLOSE, hit(layout.close, true))
            assertEquals(HudAction.TOGGLE_AIM, hit(layout.aidedAim, true))
            assertEquals(HudAction.EXIT, hit(layout.exit, true))
            assertTrue(layout.aidedAim.y > layout.restart.y && layout.restart.y == layout.exit.y)
            assertTrue(layout.close.x > layout.panel.x + layout.panel.width / 2)
            assertTrue(layout.close.y > layout.aidedAim.y + layout.aidedAim.height)
            for (rect in listOf( layout.close, layout.aidedAim, layout.restart, layout.exit)) {
                assertTrue(rect.width >= 48f && rect.height >= 48f)
                assertTrue(layout.panel.contains(rect))
            }
            assertEquals(HudAction.RESTART, hit(layout.restart, true))
            assertEquals(HudAction.RESTART, hit(layout.playAgain, finished = true))
            assertEquals(HudAction.BLOCK, layout.hit(0, 0, true, false))
            assertNull(layout.hit(0, 0, false, false))
        }
    }
}
