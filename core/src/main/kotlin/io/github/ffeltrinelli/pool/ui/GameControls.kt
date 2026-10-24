package io.github.ffeltrinelli.pool.ui

import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.view.ViewMode

enum class HudAction { SETTINGS, CLOSE, RESTART, TOP_VIEW, PLAYER_VIEW, TOGGLE_AIM, EXIT, EASY, MEDIUM, HARD, BLOCK }

interface ControlEffects {
    fun cancelGesture()
    fun pauseGame()
    fun resumeGame()
    fun restartGame()
    fun selectView(mode: ViewMode)
    fun exitGame()
}

/** Settings and OS suspension are independent: closing one must not resume the other. */
class GameControls(private val preference: SettingsPreference, private val effects: ControlEffects) {
    var difficulty = preference.loadDifficulty()
        private set
    var viewMode = preference.loadViewMode()
        private set
    var aidedAim = preference.loadAidedAim()
        private set
    var settingsOpen = false
        private set
    private var backgrounded = false
    val acceptsInput: Boolean get() = !backgrounded
    val suspended: Boolean get() = backgrounded || settingsOpen
    val acceptsGameplay: Boolean get() = !suspended

    fun handle(action: HudAction) {
        when (action) {
            HudAction.SETTINGS -> { settingsOpen = true; effects.cancelGesture(); updateSuspension() }
            HudAction.CLOSE -> { effects.cancelGesture(); settingsOpen = false; updateSuspension() }
            HudAction.RESTART -> {
                effects.cancelGesture()
                effects.restartGame()
                settingsOpen = false
                updateSuspension()
            }
            HudAction.TOP_VIEW -> selectView(ViewMode.TOP)
            HudAction.PLAYER_VIEW -> selectView(ViewMode.PLAYER)
            HudAction.TOGGLE_AIM -> { aidedAim = !aidedAim; preference.saveAidedAim(aidedAim) }
            HudAction.EXIT -> { effects.cancelGesture(); effects.pauseGame(); effects.exitGame() }
            HudAction.EASY -> selectDifficulty(OpponentDifficulty.EASY)
            HudAction.MEDIUM -> selectDifficulty(OpponentDifficulty.MEDIUM)
            HudAction.HARD -> selectDifficulty(OpponentDifficulty.HARD)
            HudAction.BLOCK -> Unit
        }
    }

    fun selectDifficulty(difficulty: OpponentDifficulty) {
        if (this.difficulty == difficulty) return
        this.difficulty = difficulty
        preference.saveDifficulty(difficulty)
    }

    private fun selectView(mode: ViewMode) {
        if (viewMode == mode) return
        effects.cancelGesture()
        viewMode = mode
        preference.saveViewMode(mode)
        effects.selectView(mode)
    }

    fun pause() { backgrounded = true; effects.cancelGesture(); updateSuspension() }
    fun resume() { backgrounded = false; updateSuspension() }
    private fun updateSuspension() { if (suspended) effects.pauseGame() else effects.resumeGame() }
}
