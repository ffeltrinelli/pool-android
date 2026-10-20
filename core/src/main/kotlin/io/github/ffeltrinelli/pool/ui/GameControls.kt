package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.view.ViewMode

enum class HudAction { SETTINGS, CLOSE, RESTART, TOP_VIEW, PLAYER_VIEW, BLOCK }

interface ViewPreference {
    fun load(): ViewMode
    fun save(mode: ViewMode)
}

class SavedViewPreference(private val preferences: Preferences) : ViewPreference {
    override fun load() = ViewMode.entries.firstOrNull { it.name == preferences.getString("view-mode", ViewMode.TOP.name) } ?: ViewMode.TOP
    override fun save(mode: ViewMode) { preferences.putString("view-mode", mode.name); preferences.flush() }
}

interface ControlEffects {
    fun cancelGesture()
    fun pauseGame()
    fun resumeGame()
    fun restartGame()
    fun selectView(mode: ViewMode)
}

/** Settings and OS suspension are independent: closing one must not resume the other. */
class GameControls(private val preference: ViewPreference, private val effects: ControlEffects) {
    var mode = preference.load()
        private set
    var settingsOpen = false
        private set
    private var backgrounded = false
    val suspended: Boolean get() = backgrounded || settingsOpen
    val acceptsGameplay: Boolean get() = !suspended

    init { effects.selectView(mode) }

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
            HudAction.TOP_VIEW -> select(ViewMode.TOP)
            HudAction.PLAYER_VIEW -> select(ViewMode.PLAYER)
            HudAction.BLOCK -> Unit
        }
    }

    private fun select(mode: ViewMode) {
        if (this.mode == mode) return
        effects.cancelGesture()
        this.mode = mode
        preference.save(mode)
        effects.selectView(mode)
    }

    fun pause() { backgrounded = true; effects.cancelGesture(); updateSuspension() }
    fun resume() { backgrounded = false; updateSuspension() }
    private fun updateSuspension() { if (suspended) effects.pauseGame() else effects.resumeGame() }
}
