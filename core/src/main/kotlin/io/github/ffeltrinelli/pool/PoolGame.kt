package io.github.ffeltrinelli.pool

import com.badlogic.gdx.*
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.ui.*
import io.github.ffeltrinelli.pool.view.ViewMode

class PoolGame : ApplicationAdapter() {
    private val session = GameSession()
    private val gesture = CueGesture()
    private lateinit var audio: PoolAudio
    private lateinit var physics: PoolPhysics
    private lateinit var renderer: PoolRenderer
    private lateinit var controls: GameControls
    private var paused = false
    private var skipDelta = true
    private var gameplayPointer: Int? = null

    override fun create() {
        audio = PoolAudio(LibGdxSoundPlayer())
        physics = PoolPhysics(session::accept, audio::queue, audio::queue)
        renderer = PoolRenderer()
        controls = GameControls(SavedViewPreference(Gdx.app.getPreferences("pool-settings")), object : ControlEffects {
            override fun cancelGesture() = cancelInput()
            override fun pauseGame() { audio.pause(); skipDelta = true }
            override fun resumeGame() { audio.resume(); skipDelta = true }
            override fun restartGame() = restart()
            override fun selectView(mode: ViewMode) { renderer.views.select(mode) }
        })
        if (paused) controls.pause()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = gameInput()
    }

    private fun gameInput() = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
            if (!controls.settingsOpen) return false
            controls.handle(HudAction.CLOSE)
            return true
        }

        override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
            if (pointer != 0 || button != Input.Buttons.LEFT || paused) return false
            val action = renderer.hud.layout.hit(x, y, controls.settingsOpen, session.finished)
            if (action != null) { controls.handle(action); return true }
            if (!controls.acceptsGameplay || !session.canShoot || gameplayPointer != null) return false
            val cue = physics.balls.firstOrNull { it.id == 0 && !it.pocketed } ?: return false
            val point = renderer.views.active.beginInput(x, y, cue.point, gesture.direction)
            gesture.begin(sample(pointer, point), cue.point)
            if (gesture.phase == CuePhase.AIMING) gameplayPointer = pointer
            return true
        }

        override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
            if (pointer != gameplayPointer || !controls.acceptsGameplay || !session.canShoot) return false
            val point = renderer.views.active.moveInput(x, y, gesture.phase, gesture.direction)
            gesture.move(sample(pointer, point))?.let(::strike)
            return true
        }

        override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
            if (pointer == gameplayPointer) cancelInput()
            return true
        }

        override fun touchCancelled(x: Int, y: Int, pointer: Int, button: Int): Boolean {
            if (pointer == gameplayPointer) cancelInput()
            return true
        }
    }

    private fun strike(shot: ShotCommand) {
        val cue = physics.balls.first { it.id == 0 && !it.pocketed }
        renderer.views.update(cue.point, shot.direction, true)
        session.beginShot()
        physics.strike(shot)
    }

    private fun sample(pointer: Int, point: Point) = PointerSample(pointer, point, Gdx.input.currentEventTime / 1e9)
    private fun cancelInput() { gameplayPointer = null; gesture.cancel() }
    private fun restart() {
        audio.clear()
        physics.reset()
        session.restart()
        renderer.views.reset()
        skipDelta = true
    }

    override fun render() {
        val delta = if (controls.suspended || skipDelta) 0f else Gdx.graphics.deltaTime.coerceIn(0f, 0.1f)
        if (!controls.suspended) {
            physics.advance(delta)
            audio.flush(delta)
            skipDelta = false
        }
        renderer.settingsOpen = controls.settingsOpen
        renderer.render(physics, session, gesture, viewDelta = delta)
    }

    override fun resize(width: Int, height: Int) {
        if (::renderer.isInitialized) { cancelInput(); renderer.resize(width, height) }
    }
    override fun pause() { paused = true; cancelInput(); if (::controls.isInitialized) controls.pause() }
    override fun resume() { paused = false; skipDelta = true; if (::controls.isInitialized) controls.resume() }
    override fun dispose() {
        Gdx.input?.inputProcessor = null
        Gdx.input?.setCatchKey(Input.Keys.BACK, false)
        if (::audio.isInitialized) audio.dispose()
        if (::renderer.isInitialized) renderer.dispose()
        if (::physics.isInitialized) physics.dispose()
    }
}
