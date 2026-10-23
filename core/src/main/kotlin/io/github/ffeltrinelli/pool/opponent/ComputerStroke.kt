package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*

data class CuePose(val direction: Point, val tipGap: Float)

/** Presentation only. The caller applies the returned shot once, at visible tip contact. */
class ComputerStroke(val shot: ShotCommand) {
    private var elapsed = 0f
    private var delivered = false
    val pose: CuePose get() {
        val gap = when {
            elapsed <= HOLD -> REST
            elapsed <= HOLD + BACK -> REST + (WITHDRAWN - REST) * smooth((elapsed - HOLD) / BACK)
            else -> WITHDRAWN * (1f - smooth((elapsed - HOLD - BACK) / FORWARD))
        }
        return CuePose(shot.direction, gap.coerceAtLeast(0f))
    }
    fun advance(seconds: Float): ShotCommand? {
        elapsed += seconds.coerceAtLeast(0f)
        if (delivered || elapsed + 0.000001f < HOLD + BACK + FORWARD) return null
        delivered = true
        return shot
    }
    private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    companion object {
        const val REST = 0.05f
        const val WITHDRAWN = 0.12f
        const val HOLD = 0.35f
        const val BACK = 0.25f
        const val FORWARD = 0.20f
    }
}
