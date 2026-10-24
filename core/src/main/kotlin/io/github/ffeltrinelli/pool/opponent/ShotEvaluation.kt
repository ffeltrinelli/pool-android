package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.rules.FoulReason

/** Rack outcomes dominate fouls; legal pots dominate proximity and impulse tie-breakers. */
data class ShotEvaluation(
    val objectBalls: Int,
    val foul: FoulReason?,
    val nearestPocketDistance: Float,
    val impulse: Float,
    val won: Boolean = false,
    val lost: Boolean = false
) {
    val score: Double get() {
        if (won) return 100000.0
        if (lost) return -100000.0
        if (foul != null) return -10000.0
        val proximity = 1.0 - nearestPocketDistance.coerceIn(0f, 3f) / 3.0
        return objectBalls * 100.0 + 10 + proximity - impulse * 0.01
    }
}
