package io.github.ffeltrinelli.pool.render

import io.github.ffeltrinelli.pool.opponent.CuePose

/** Immutable inputs for one frame; rendering does not retain coordination state. */
data class TablePresentation(
    val humanCanAim: Boolean = true,
    val computerPose: CuePose? = null,
    val holdOverview: Boolean = false,
    val aidedAim: Boolean = true,
    val placement: io.github.ffeltrinelli.pool.model.Point? = null,
    val placementRegion: io.github.ffeltrinelli.pool.rules.PlacementRegion? = null,
    val placementValid: Boolean = true,
    val activeSeconds: Float = 0f
)
