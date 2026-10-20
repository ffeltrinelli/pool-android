package io.github.ffeltrinelli.pool.physics

import io.github.ffeltrinelli.pool.model.Impact
import io.github.ffeltrinelli.pool.model.ImpactKind

/** Tracks contact episodes, not Bullet manifolds (which can outlive physical contact). */
internal class ContactImpacts(private val emit: (Impact) -> Unit) {
    private data class PairId(val first: Int, val second: Int)
    private var previous = emptySet<PairId>()
    private val current = mutableMapOf<PairId, Float>()

    fun contact(first: Int, second: Int, impulse: Float) {
        val pair = PairId(minOf(first, second), maxOf(first, second))
        if (pair.second !in CollisionIds.BALLS ||
            (pair.first !in CollisionIds.BALLS && pair.first !in CollisionIds.CUSHIONS)) return
        current[pair] = (current[pair] ?: 0f) + impulse.coerceAtLeast(0f)
    }

    fun finishStep() {
        current.forEach { (pair, impulse) ->
            if (pair !in previous && impulse >= Impact.MIN_AUDIBLE_IMPULSE) {
                emit(Impact(if (pair.first < 0) ImpactKind.CUSHION else ImpactKind.BALL, impulse))
            }
        }
        previous = current.keys.toSet()
        current.clear()
    }

    fun reset() { previous = emptySet(); current.clear() }

}
