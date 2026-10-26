package io.github.ffeltrinelli.pool.stats

import io.github.ffeltrinelli.pool.model.MatchResult
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import kotlin.math.roundToInt

data class MatchTotals(val completed: Long = 0, val won: Long = 0) {
    val winPercentage: Int get() = if (completed == 0L) 0 else (100.0 * won / completed).roundToInt()
}

/** A presentation snapshot; callers cannot mutate the stored rows. */
class MatchStatistics(rows: Map<OpponentDifficulty, MatchTotals> = emptyMap()) {
    private val rows = rows.toMap()
    operator fun get(difficulty: OpponentDifficulty): MatchTotals = rows[difficulty] ?: MatchTotals()

    fun recorded(difficulty: OpponentDifficulty, result: MatchResult): MatchStatistics {
        val previous = get(difficulty)
        val next = MatchTotals(previous.completed + 1, previous.won + if (result == MatchResult.HUMAN_WINS) 1 else 0)
        return MatchStatistics(rows + (difficulty to next))
    }
}

/** Owns the per-rack recording guard independently of resets and presentation. */
class MatchStatisticsTracker(private val preference: StatisticsPreference) {
    var snapshot = preference.load()
        private set
    private var recorded = false

    fun recordCompletion(result: MatchResult?, difficulty: OpponentDifficulty) {
        if (result == null || recorded) return
        recorded = true
        snapshot = snapshot.recorded(difficulty, result)
        preference.save(snapshot)
    }

    fun beginRack() { recorded = false }

    fun reset() {
        snapshot = MatchStatistics()
        preference.save(snapshot)
    }
}
