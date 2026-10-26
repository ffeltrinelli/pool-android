package io.github.ffeltrinelli.pool.ui

import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

enum class HudAction { SETTINGS, HELP, STATS, RESET_STATS, CLOSE, RESTART, TOGGLE_AIM, TOGGLE_REPLAYS, EXIT, EASY, MEDIUM, HARD, BLOCK }

interface ControlEffects {
    fun cancelGesture()
    fun pauseGame()
    fun resumeGame()
    fun restartGame()
    fun resetStatistics()
    fun exitGame()
}

/** Settings and OS suspension are independent: closing one must not resume the other. */
class GameControls(private val preference: SettingsPreference, private val effects: ControlEffects) {
    var difficulty = preference.loadDifficulty()
        private set
    var replays = preference.loadReplays()
        private set
    var aidedAim = preference.loadAidedAim()
        private set
    var settingsOpen = false
        private set
    var helpOpen = false
        private set
    var statsOpen = false
        private set
    val settingsPageOpen: Boolean get() = settingsOpen && !helpOpen && !statsOpen
    private var backgrounded = false
    val acceptsInput: Boolean get() = !backgrounded
    val suspended: Boolean get() = backgrounded || settingsOpen
    val acceptsGameplay: Boolean get() = !suspended

    fun handle(action: HudAction) {
        when (action) {
            HudAction.SETTINGS -> { settingsOpen = true; effects.cancelGesture(); updateSuspension() }
            HudAction.HELP -> { if (settingsPageOpen) { effects.cancelGesture(); helpOpen = true } }
            HudAction.STATS -> { if (settingsPageOpen) { effects.cancelGesture(); statsOpen = true } }
            HudAction.RESET_STATS -> { if (statsOpen) effects.resetStatistics() }
            HudAction.CLOSE -> {
                effects.cancelGesture()
                when {
                    helpOpen -> helpOpen = false
                    statsOpen -> statsOpen = false
                    else -> settingsOpen = false
                }
                updateSuspension()
            }
            HudAction.RESTART -> {
                effects.cancelGesture()
                effects.restartGame()
                helpOpen = false
                statsOpen = false
                settingsOpen = false
                updateSuspension()
            }
            HudAction.TOGGLE_REPLAYS -> { replays = !replays; preference.saveReplays(replays) }
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

    fun pause() { backgrounded = true; effects.cancelGesture(); updateSuspension() }
    fun resume() { backgrounded = false; updateSuspension() }
    private fun updateSuspension() { if (suspended) effects.pauseGame() else effects.resumeGame() }
}
