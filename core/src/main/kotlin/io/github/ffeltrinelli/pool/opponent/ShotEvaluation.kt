package io.github.ffeltrinelli.pool.opponent

/** Secondary terms cannot outweigh a single point or the no-scratch preference. */
data class ShotEvaluation(val objectBalls: Int, val scratched: Boolean, val nearestPocketDistance: Float, val impulse: Float) {
    val score: Double get() {
        val awarded = if (scratched) 0 else objectBalls
        val noScratch = if (scratched) 0 else 10
        val proximity = 1.0 - nearestPocketDistance.coerceIn(0f, 3f) / 3.0
        return awarded * 100.0 + noScratch + proximity - impulse * 0.01
    }
}
