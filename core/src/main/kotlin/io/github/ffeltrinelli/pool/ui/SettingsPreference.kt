package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.view.ViewMode

interface SettingsPreference {
    fun loadDifficulty(): OpponentDifficulty
    fun saveDifficulty(difficulty: OpponentDifficulty)
    fun loadViewMode(): ViewMode
    fun saveViewMode(mode: ViewMode)
    fun loadAidedAim(): Boolean
    fun saveAidedAim(enabled: Boolean)
}

class SavedSettingsPreference(private val preferences: Preferences) : SettingsPreference {
    override fun loadDifficulty(): OpponentDifficulty {
        val saved = preferences.getString("opponent-difficulty", OpponentDifficulty.MEDIUM.name)
        return OpponentDifficulty.entries.firstOrNull { it.name == saved } ?: OpponentDifficulty.MEDIUM
    }
    override fun saveDifficulty(difficulty: OpponentDifficulty) {
        preferences.putString("opponent-difficulty", difficulty.name)
        preferences.flush()
    }
    override fun loadAidedAim() = preferences.getBoolean("aided-aim", true)
    override fun saveAidedAim(enabled: Boolean) {
        preferences.putBoolean("aided-aim", enabled)
        preferences.flush()
    }

    override fun loadViewMode(): ViewMode {
        val saved = preferences.getString("view-mode", ViewMode.TOP.name)
        return ViewMode.entries.firstOrNull { it.name == saved } ?: ViewMode.TOP
    }

    override fun saveViewMode(mode: ViewMode) {
        preferences.putString("view-mode", mode.name)
        preferences.flush()
    }
}
