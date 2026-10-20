package io.github.ffeltrinelli.pool.physics

import io.github.ffeltrinelli.pool.model.PocketGeometry

/** SI units. Contact resistance, rather than air damping, slows the balls. */
internal object PhysicsTuning {
    const val SOLVER_ITERATIONS = 10
    const val GRAVITY = 9.81f
    const val CLOTH_FRICTION = 0.8f
    const val BALL_FRICTION = 0.25f
    const val BALL_RESTITUTION = 0.94f
    const val CUSHION_FRICTION = 0.16f
    const val CUSHION_RESTITUTION = 0.78f
    const val LINER_FRICTION = 0.35f
    const val LINER_RESTITUTION = 0.15f
    const val ROLLING_FRICTION = 0.00035f
    const val SPINNING_FRICTION = 0.001f
    const val LINEAR_SLEEP_SPEED = 0.012f
    const val ANGULAR_SLEEP_SPEED = 0.3f
    const val SETTLED_SECONDS = 0.3f
}

/** Support and interior contacts must never be classified as cushion impacts. */
internal object CollisionIds {
    val BALLS = 0..15
    val CUSHIONS = -PocketGeometry.cushions.size..-1
    const val INTERIOR = 201
    const val CLOTH = 200
}
