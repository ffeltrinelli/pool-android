package io.github.ffeltrinelli.pool

import com.badlogic.gdx.*
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.render.PoolRenderer

class PoolGame : ApplicationAdapter() {
    private val session = GameSession()
    private val gesture = CueGesture()
    private lateinit var physics: PoolPhysics
    private lateinit var renderer: PoolRenderer
    private var paused = false
    private var skipDelta = true

    override fun create() {
        physics = PoolPhysics(session::accept)
        renderer = PoolRenderer()
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                if (pointer != 0 || button != Input.Buttons.LEFT || paused) return false
                if (renderer.isRestart(x, y, session.finished)) {
                    restart()
                    return true
                }
                if (!session.canShoot) return false
                val cue = physics.balls.firstOrNull { it.id == 0 && !it.pocketed } ?: return false
                gesture.begin(sample(x, y, pointer), cue.point)
                return true
            }
            override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
                if (paused || !session.canShoot) return false
                gesture.move(sample(x, y, pointer))?.let { session.beginShot(); physics.strike(it) }
                return true
            }
            override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                gesture.end(pointer)
                return true
            }
            override fun touchCancelled(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                gesture.end(pointer)
                return true
            }
        }
    }
    private fun sample(x: Int, y: Int, pointer: Int) = PointerSample(pointer, renderer.tablePoint(x, y), Gdx.input.currentEventTime / 1e9)
    private fun restart() { gesture.cancel(); physics.reset(); session.restart(); skipDelta = true }
    override fun render() {
        if (!paused) {
            if (!skipDelta) physics.advance(Gdx.graphics.deltaTime)
            skipDelta = false
        }
        renderer.render(physics, session, gesture)
    }
    override fun resize(width: Int, height: Int) { renderer.resize(width, height) }
    override fun pause() { paused = true; gesture.cancel() }
    override fun resume() { paused = false; skipDelta = true }
    override fun dispose() { Gdx.input.inputProcessor = null; renderer.dispose(); physics.dispose() }
}
