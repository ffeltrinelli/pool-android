package io.github.ffeltrinelli.pool.stats

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

interface StatisticsPreference {
    fun load(): MatchStatistics
    fun save(statistics: MatchStatistics)
}

class SavedStatisticsPreference(private val preferences: Preferences) : StatisticsPreference {
    override fun load() = MatchStatistics(OpponentDifficulty.entries.associateWith { difficulty ->
        val completed = preferences.getLong("${difficulty.name}-completed", 0).coerceAtLeast(0)
        val won = preferences.getLong("${difficulty.name}-won", 0).coerceIn(0, completed)
        MatchTotals(completed, won)
    })

    override fun save(statistics: MatchStatistics) {
        for (difficulty in OpponentDifficulty.entries) {
            val totals = statistics[difficulty]
            preferences.putLong("${difficulty.name}-completed", totals.completed)
            preferences.putLong("${difficulty.name}-won", totals.won)
        }
        preferences.flush()
    }
}
