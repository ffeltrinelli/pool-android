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
    const val HEAD_STRING = HALF_LENGTH / 2
    const val FOOT_SPOT = -HALF_LENGTH / 2
    val CUE_START = Point(0f, 0.58f)
    fun rack(seed: Long = kotlin.random.Random.nextLong()): List<Pair<Int, Point>> = buildList {
        add(0 to CUE_START)
        val spacing = BALL_RADIUS * 2f + 0.0001f
        val random = kotlin.random.Random(seed)
        val solid = (1..7).random(random)
        val stripe = (9..15).random(random)
        val corners = listOf(solid, stripe).shuffled(random)
        val others = (1..15).filter { it !in listOf(8, solid, stripe) }.shuffled(random).iterator()
        val numbers = (0..14).map { when (it) { 4 -> 8; 10 -> corners[0]; 14 -> corners[1]; else -> others.next() } }
        var index = 0
        for (row in 0..4) for (column in 0..row) {
            add(numbers[index++] to Point((column - row / 2f) * spacing, FOOT_SPOT - row * spacing * 0.8660254f))
        }
    }
}
