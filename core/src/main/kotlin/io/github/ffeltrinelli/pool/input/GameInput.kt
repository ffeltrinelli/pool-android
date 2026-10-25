package io.github.ffeltrinelli.pool.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.Player
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.ui.*
import io.github.ffeltrinelli.pool.view.GameViews

/** Routes modal controls before gameplay and owns pointer cancellation. */
internal class GameInput(
    private val controls: GameControls,
    private val session: GameSession,
    private val layout: () -> HudLayout,
    private val views: GameViews,
    private val physics: PoolPhysics,
    private val gesture: CueGesture,
    private val canShoot: () -> Boolean,
    private val deliverShot: (ShotCommand) -> Unit,
    private val placement: CueBallPlacement = CueBallPlacement(),
    private val modalTouch: (Int, Int) -> Boolean = { _, _ -> false },
    private val modalBack: () -> Boolean = { false },
    private val confirmPlacement: () -> Unit = {}
) : InputAdapter() {
    private val difficultySlider = DifficultySliderInput()
    private var gameplayPointer: Int? = null

    override fun keyDown(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        if (!controls.settingsOpen) return modalBack()
        controls.handle(HudAction.CLOSE)
        return true
    }

    override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        if (!controls.acceptsInput) return false
        if (pointer != 0 || button != Input.Buttons.LEFT) return controls.settingsOpen
        if (!controls.settingsOpen && modalTouch(x, y)) return true
        if (controls.settingsOpen) {
            difficultySlider.begin(layout(), x, y, pointer)
            difficultySlider.selection(layout(), x, pointer)?.let {
                controls.selectDifficulty(it)
                return true
            }
        }
        val action = layout().hit(x, y, controls.settingsOpen, session.finished)
        if (action != null) { controls.handle(action); return true }
        if (controls.acceptsGameplay && session.placement != null && session.player == Player.HUMAN &&
            layout().confirmsPlacement(x, y)) {
            confirmPlacement()
            return true
        }
        if (!layout().containsGame(x, y)) return true
        if (controls.acceptsGameplay && session.placement != null && session.player == Player.HUMAN) {
            placement.move(views.active.beginInput(x, layout().gameScreenY(y), placement.point, gesture.direction))
            gameplayPointer = pointer
            return true
        }
        if (!controls.acceptsGameplay || !canShoot() || gameplayPointer != null) return false
        val cue = physics.balls.firstOrNull { it.id == 0 && !it.pocketed } ?: return false
        val point = views.active.beginInput(x, layout().gameScreenY(y), cue.point, gesture.direction)
        gesture.begin(sample(pointer, point), cue.point)
        if (gesture.phase == CuePhase.AIMING) gameplayPointer = pointer
        return true
    }

    override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
        if (controls.settingsOpen) {
            difficultySlider.selection(layout(), x, pointer)?.let(controls::selectDifficulty)
            return true
        }
        if (pointer != gameplayPointer || !controls.acceptsGameplay) return false
        if (session.placement != null) {
            placement.move(views.active.moveInput(x, layout().gameScreenY(y), CuePhase.AIMING, gesture.direction))
            return true
        }
        if (!canShoot()) return false
        val point = views.active.moveInput(x, layout().gameScreenY(y), gesture.phase, gesture.direction)
        gesture.move(sample(pointer, point))?.let(deliverShot)
        return true
    }

    override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        difficultySlider.end(pointer)
        if (pointer == gameplayPointer) cancel()
        return true
    }

    override fun touchCancelled(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        difficultySlider.end(pointer)
        if (pointer == gameplayPointer) cancel()
        return true
    }
    fun cancel() {
        difficultySlider.cancel()
        gameplayPointer = null
        gesture.cancel()
    }

    private fun sample(pointer: Int, point: Point) =
        PointerSample(pointer, point, Gdx.input.currentEventTime / 1e9)
}
