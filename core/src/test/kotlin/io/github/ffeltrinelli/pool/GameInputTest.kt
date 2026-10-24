package io.github.ffeltrinelli.pool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.ui.*
import io.github.ffeltrinelli.pool.view.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class GameInputTest {
    private class Settings : SettingsPreference {
        override fun loadDifficulty() = OpponentDifficulty.MEDIUM
        override fun saveDifficulty(difficulty: OpponentDifficulty) = Unit
        override fun loadViewMode() = ViewMode.TOP
        override fun saveViewMode(mode: ViewMode) = Unit
        override fun loadAidedAim() = true
        override fun saveAidedAim(enabled: Boolean) = Unit
    }

    private class Effects : ControlEffects {
        var cancellations = 0
        var viewChanges = 0
        override fun cancelGesture() { cancellations++ }
        override fun pauseGame() = Unit
        override fun resumeGame() = Unit
        override fun restartGame() = Unit
        override fun selectView(mode: ViewMode) { viewChanges++ }
        override fun exitGame() = Unit
    }

    @Test fun `settings construction reads preferences without triggering game commands`() {
        val effects = Effects()
        val controls = GameControls(Settings(), effects)
        assertEquals(ViewMode.TOP, controls.viewMode)
        assertEquals(0, effects.cancellations)
        assertEquals(0, effects.viewChanges)
        controls.handle(HudAction.PLAYER_VIEW)
        assertEquals(1, effects.viewChanges)
        assertEquals(1, effects.cancellations)
    }

    @Test fun `input routes modal and Back before gameplay and ignores background touches`() = withInput { input, controls, gesture, _, _ ->
        controls.handle(HudAction.SETTINGS)
        assertTrue(input.touchDown(210, 700, 0, Input.Buttons.LEFT))
        assertEquals(CuePhase.IDLE, gesture.phase)
        assertTrue(input.keyDown(Input.Keys.BACK))
        assertFalse(controls.settingsOpen)
        assertFalse(input.keyDown(Input.Keys.BACK))
        controls.pause()
        assertFalse(input.touchDown(210, 700, 0, Input.Buttons.LEFT))
        assertEquals(CuePhase.IDLE, gesture.phase)
    }

    @Test fun `computer turn blocks strokes while keeping settings accessible`() = withInput { input, controls, gesture, session, shots ->
        session.beginShot()
        session.accept(io.github.ffeltrinelli.pool.model.PhysicsEvent.Settled)
        assertFalse(input.touchDown(210, 700, 0, Input.Buttons.LEFT))
        assertTrue(input.touchDown(372, 72, 0, Input.Buttons.LEFT))
        assertTrue(controls.settingsOpen)
        assertEquals(CuePhase.IDLE, gesture.phase)
        assertTrue(shots.isEmpty())
    }

    @Test fun `pointer cancellation resets a pending stroke without firing`() = withInput { input, _, gesture, _, shots ->
        assertTrue(input.touchDown(210, 700, 0, Input.Buttons.LEFT))
        assertEquals(CuePhase.AIMING, gesture.phase)
        input.touchDown(210, 700, 1, Input.Buttons.LEFT)
        input.touchCancelled(210, 700, 1, Input.Buttons.LEFT)
        assertEquals(CuePhase.AIMING, gesture.phase)
        input.cancel()
        assertEquals(CuePhase.IDLE, gesture.phase)
        assertEquals(0.05f, gesture.tipGap, 0f)
        assertTrue(shots.isEmpty())
    }

    @Test fun `forward contact delivers one command through input routing`() = withInput { input, _, gesture, _, shots ->
        input.touchDown(210, 700, 0, Input.Buttons.LEFT)
        input.touchDragged(210, 690, 0)
        input.touchDragged(210, 680, 0)
        input.touchDragged(210, 670, 0)
        assertEquals(CuePhase.FIRED, gesture.phase)
        assertEquals(1, shots.size)
        assertTrue(shots.single().impulse > 0f)
    }

    private fun withInput(check: (GameInput, GameControls, CueGesture, GameSession, List<ShotCommand>) -> Unit) {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val previousInput = Gdx.input
        var timestamp = 0L
        Gdx.input = Proxy.newProxyInstance(Input::class.java.classLoader, arrayOf(Input::class.java)) { _, method, _ ->
            check(method.name == "getCurrentEventTime")
            timestamp += 50_000_000L
            timestamp
        } as Input
        val physics = PoolPhysics()
        try {
            val controls = GameControls(Settings(), Effects())
            val session = GameSession()
            val gesture = CueGesture()
            val shots = mutableListOf<ShotCommand>()
            val views = GameViews().apply { resize(420, 933) }
            val input = GameInput(controls, session, { HudLayout(420, 933) }, views, physics, gesture, { session.humanTurn }, shots::add)
            check(input, controls, gesture, session, shots)
        } finally {
            physics.dispose()
            Gdx.input = previousInput
        }
    }
}
