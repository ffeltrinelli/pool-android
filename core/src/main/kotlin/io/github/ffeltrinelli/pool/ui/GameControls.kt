package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.model.PlayMode
import io.github.ffeltrinelli.pool.view.ViewMode

enum class HudAction { SETTINGS, CLOSE, RESTART, TOP_VIEW, PLAYER_VIEW, TOGGLE_AIM, EXIT, SOLO, COMPUTER, BLOCK }

interface SettingsPreference {
    fun loadPlayMode(): PlayMode
    fun savePlayMode(mode: PlayMode)
    fun loadViewMode(): ViewMode
    fun saveViewMode(mode: ViewMode)
    fun loadAidedAim(): Boolean
    fun saveAidedAim(enabled: Boolean)
}

class SavedSettingsPreference(private val preferences: Preferences) : SettingsPreference {
    override fun loadPlayMode() = PlayMode.entries.firstOrNull { it.name == preferences.getString("play-mode", PlayMode.SOLO.name) } ?: PlayMode.SOLO
    override fun savePlayMode(mode: PlayMode) { preferences.putString("play-mode", mode.name); preferences.flush() }
    override fun loadAidedAim() = preferences.getBoolean("aided-aim", true)
    override fun saveAidedAim(enabled: Boolean) { preferences.putBoolean("aided-aim", enabled); preferences.flush() }
    override fun loadViewMode() = ViewMode.entries.firstOrNull { it.name == preferences.getString("view-mode", ViewMode.TOP.name) } ?: ViewMode.TOP
    override fun saveViewMode(mode: ViewMode) { preferences.putString("view-mode", mode.name); preferences.flush() }
}

interface ControlEffects {
    fun cancelGesture()
    fun pauseGame()
    fun resumeGame()
    fun restartGame()
    fun selectPlayMode(mode: PlayMode)
    fun selectView(mode: ViewMode)
    fun exitGame()
}

/** Settings and OS suspension are independent: closing one must not resume the other. */
class GameControls(private val preference: SettingsPreference, private val effects: ControlEffects) {
    var playMode = preference.loadPlayMode()
        private set
    var mode = preference.loadViewMode()
        private set
    var aidedAim = preference.loadAidedAim()
        private set
    var settingsOpen = false
        private set
    private var backgrounded = false
    val suspended: Boolean get() = backgrounded || settingsOpen
    val acceptsGameplay: Boolean get() = !suspended

    init { effects.selectView(mode); effects.selectPlayMode(playMode) }

    fun handle(action: HudAction) {
        when (action) {
            HudAction.SETTINGS -> { settingsOpen = true; effects.cancelGesture(); updateSuspension() }
            HudAction.CLOSE -> { settingsOpen = false; updateSuspension() }
            HudAction.RESTART -> {
                effects.cancelGesture()
                effects.restartGame()
                settingsOpen = false
                updateSuspension()
            }
            HudAction.SOLO -> selectPlay(PlayMode.SOLO)
            HudAction.COMPUTER -> selectPlay(PlayMode.COMPUTER)
            HudAction.TOP_VIEW -> select(ViewMode.TOP)
            HudAction.PLAYER_VIEW -> select(ViewMode.PLAYER)
            HudAction.TOGGLE_AIM -> { aidedAim = !aidedAim; preference.saveAidedAim(aidedAim) }
            HudAction.EXIT -> { effects.cancelGesture(); effects.pauseGame(); effects.exitGame() }
            HudAction.BLOCK -> Unit
        }
    }

    private fun selectPlay(mode: PlayMode) {
        if (playMode == mode) return
        effects.cancelGesture()
        playMode = mode
        preference.savePlayMode(mode)
        effects.selectPlayMode(mode)
    }

    private fun select(mode: ViewMode) {
        if (this.mode == mode) return
        effects.cancelGesture()
        this.mode = mode
        preference.saveViewMode(mode)
        effects.selectView(mode)
    }

    fun pause() { backgrounded = true; effects.cancelGesture(); updateSuspension() }
    fun resume() { backgrounded = false; updateSuspension() }
    private fun updateSuspension() { if (suspended) effects.pauseGame() else effects.resumeGame() }
}
