package io.github.ffeltrinelli.pool.model

/** A physical impact, independent of round rules. Impulse is in N·s. */
data class Impact(val kind: ImpactKind, val impulse: Float) {
    companion object { const val MIN_AUDIBLE_IMPULSE = 0.002f }
}
enum class ImpactKind { BALL, CUSHION, CUE }

/** A confirmed physical drop, separate from session events and impact strength. */
data class PocketDrop(val ballId: Int, val pocketId: Int)
