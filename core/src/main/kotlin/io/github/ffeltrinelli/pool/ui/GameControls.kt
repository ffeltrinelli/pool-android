package io.github.ffeltrinelli.pool.ui

import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

enum class HudAction { SETTINGS, HELP, STATS, RESET_STATS, CLOSE, RESTART, SHARE_APP, TOGGLE_AIM, TOGGLE_REPLAYS, EXIT, EASY, MEDIUM, HARD, BLOCK }

interface ControlEffects {
    fun cancelGesture()
    fun pauseGame()
    fun resumeGame()
    fun shareApp()
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
    private enum class SettingsPage { CLOSED, MENU, HELP, STATS }
    private var settingsPage = SettingsPage.CLOSED
    val settingsOpen: Boolean get() = settingsPage != SettingsPage.CLOSED
    val helpOpen: Boolean get() = settingsPage == SettingsPage.HELP
    val statsOpen: Boolean get() = settingsPage == SettingsPage.STATS
    val settingsPageOpen: Boolean get() = settingsPage == SettingsPage.MENU
    private var backgrounded = false
    val acceptsInput: Boolean get() = !backgrounded
    val suspended: Boolean get() = backgrounded || settingsOpen
    val acceptsGameplay: Boolean get() = !suspended

    fun handle(action: HudAction) {
        when (action) {
            HudAction.SETTINGS -> {
                if (!settingsOpen) settingsPage = SettingsPage.MENU
                effects.cancelGesture()
                updateSuspension()
            }
            HudAction.HELP -> openSettingsPage(SettingsPage.HELP)
            HudAction.STATS -> openSettingsPage(SettingsPage.STATS)
            HudAction.RESET_STATS -> { if (statsOpen) effects.resetStatistics() }
            HudAction.CLOSE -> {
                effects.cancelGesture()
                settingsPage = when (settingsPage) {
                    SettingsPage.HELP, SettingsPage.STATS -> SettingsPage.MENU
                    else -> SettingsPage.CLOSED
                }
                updateSuspension()
            }
            HudAction.SHARE_APP -> { if (settingsPageOpen && !backgrounded) effects.shareApp() }
            HudAction.RESTART -> {
                effects.cancelGesture()
                effects.restartGame()
                settingsPage = SettingsPage.CLOSED
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

    private fun openSettingsPage(page: SettingsPage) {
        if (!settingsPageOpen) return
        effects.cancelGesture()
        settingsPage = page
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
