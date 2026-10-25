package io.github.ffeltrinelli.pool.ui

import io.github.ffeltrinelli.pool.model.Player
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

data class HudPresentation(
    val settingsOpen: Boolean = false,
    val computerThinking: Boolean = false,
    val foulPlayer: Player? = null,
    val difficulty: OpponentDifficulty = OpponentDifficulty.MEDIUM,
    val notice: String? = null,
    val placementError: String? = null,
    val replays: Boolean = true
)
