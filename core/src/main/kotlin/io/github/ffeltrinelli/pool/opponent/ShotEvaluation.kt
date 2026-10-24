package io.github.ffeltrinelli.pool.opponent

/** Rack outcomes dominate fouls; legal pots dominate proximity and impulse tie-breakers. */
data class ShotEvaluation(val objectBalls: Int, val scratched: Boolean, val nearestPocketDistance: Float, val impulse: Float, val won: Boolean = false, val lost: Boolean = false) {
    val score: Double get() {
        if (won) return 100000.0
        if (lost) return -100000.0
        if (scratched) return -10000.0
        val awarded = if (scratched) 0 else objectBalls
        val noScratch = if (scratched) 0 else 10
        val proximity = 1.0 - nearestPocketDistance.coerceIn(0f, 3f) / 3.0
        return awarded * 100.0 + noScratch + proximity - impulse * 0.01
    }
}
