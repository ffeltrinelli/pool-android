package io.github.ffeltrinelli.pool.render

import io.github.ffeltrinelli.pool.opponent.CuePose

/** Immutable inputs for one frame; rendering does not retain coordination state. */
data class TablePresentation(
    val humanCanAim: Boolean = true,
    val computerPose: CuePose? = null,
    val holdOverview: Boolean = false,
    val aidedAim: Boolean = true
)
