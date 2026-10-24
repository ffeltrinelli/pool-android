package io.github.ffeltrinelli.pool.model

import kotlin.math.*

data class Point(val x: Float, val z: Float) {
    operator fun plus(other: Point) = Point(x + other.x, z + other.z)
    operator fun minus(other: Point) = Point(x - other.x, z - other.z)
    operator fun times(scale: Float) = Point(x * scale, z * scale)
    fun dot(other: Point) = x * other.x + z * other.z
    fun length() = sqrt(dot(this))
    fun normalized(): Point {
        val magnitude = length()
        return if (magnitude > 0.00001f) this * (1f / magnitude) else Point(0f, -1f)
    }
}

object TableGeometry {
    const val HALF_WIDTH = 0.56f
    const val HALF_LENGTH = 1.12f
    const val BALL_RADIUS = 0.028575f
    const val BALL_MASS = 0.17f
    val pockets get() = PocketGeometry.pockets.map { it.center }
    fun rack(): List<Pair<Int, Point>> = buildList {
        add(0 to Point(0f, 0.58f))
        val spacing = BALL_RADIUS * 2f + 0.0008f
        val numbers = listOf(1, 9, 2, 3, 8, 10, 11, 4, 12, 5, 6, 13, 7, 14, 15)
        var index = 0
        for (row in 0..4) for (column in 0..row) {
            add(numbers[index++] to Point((column - row / 2f) * spacing, -0.38f - row * spacing * 0.8660254f))
        }
    }
}
