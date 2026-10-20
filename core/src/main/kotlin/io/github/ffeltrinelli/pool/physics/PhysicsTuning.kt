package io.github.ffeltrinelli.pool.physics

import io.github.ffeltrinelli.pool.model.TableGeometry

/** SI units. Contact resistance, rather than air damping, slows the balls. */
internal object PhysicsTuning {
    const val SOLVER_ITERATIONS = 10
    const val GRAVITY = 9.81f
    const val CLOTH_FRICTION = 0.8f
    const val BALL_FRICTION = 0.25f
    const val BALL_RESTITUTION = 0.94f
    const val CUSHION_FRICTION = 0.16f
    const val CUSHION_RESTITUTION = 0.78f
    const val ROLLING_FRICTION = 0.00035f
    const val SPINNING_FRICTION = 0.001f
    const val LINEAR_SLEEP_SPEED = 0.012f
    const val ANGULAR_SLEEP_SPEED = 0.3f
    const val SETTLED_SECONDS = 0.3f
}

/** Disjoint IDs prevent cloth from being classified as a pocket or cushion. */
internal object CollisionIds {
    val BALLS = 0..15
    val CUSHIONS = -TableGeometry.rails.size..-1
    val POCKETS = 100 until 100 + TableGeometry.pockets.size
    const val CLOTH = 200
}
