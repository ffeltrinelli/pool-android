package io.github.ffeltrinelli.pool.ui

import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

/** Captures only a gesture that starts on the modal slider; leaving its bounds clamps the value. */
class DifficultySliderInput {
    private var pointer: Int? = null

    fun begin(layout: HudLayout, x: Int, y: Int, pointer: Int) {
        if (pointer != 0 || this.pointer != null || !layout.containsDifficulty(x, y)) return
        this.pointer = pointer
    }

    fun selection(layout: HudLayout, x: Int, pointer: Int): OpponentDifficulty? =
        if (this.pointer == pointer) layout.difficultyAt(x) else null

    fun end(pointer: Int) { if (this.pointer == pointer) cancel() }
    fun cancel() { pointer = null }
}
