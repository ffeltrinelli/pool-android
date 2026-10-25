package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

interface SettingsPreference {
    fun loadReplays(): Boolean = true
    fun saveReplays(enabled: Boolean) {}
    fun loadDifficulty(): OpponentDifficulty
    fun saveDifficulty(difficulty: OpponentDifficulty)
    fun loadAidedAim(): Boolean
    fun saveAidedAim(enabled: Boolean)
}

class SavedSettingsPreference(private val preferences: Preferences) : SettingsPreference {
    override fun loadReplays() = preferences.getBoolean("replays", true)
    override fun saveReplays(enabled: Boolean) {
        preferences.putBoolean("replays", enabled)
        preferences.flush()
    }
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
}
