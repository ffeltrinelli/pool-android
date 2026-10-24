package io.github.ffeltrinelli.pool.model

import kotlin.math.*

data class PocketLipRing(val outline: List<Point>, val height: Float)

/** Dimensions are metres; mouth widths are measured between cushion noses. */
data class PocketDefinition(val id: Int, val corner: Boolean, val mouthCenter: Point, val outward: Point) {
    val width = if (corner) 0.1143f else 0.127f
    val shelf = if (corner) 0.030f else 0.005f
    val cutDegrees = if (corner) 142f else 104f
    val radius = if (corner) 0.055f else 0.061f
    val lateral = Point(outward.z, -outward.x)
    val center = mouthCenter + outward * (shelf + radius)
    val opening = (0 until 48).map { i ->
        val angle = i * (2.0 * PI / 48).toFloat()
        center + Point(cos(angle), sin(angle)) * radius
    }
    val lipRings = (0..3).map { step ->
        val angle = (step * PI / 6.0).toFloat()
        val ringRadius = radius - PocketGeometry.BEVEL * sin(angle)
        PocketLipRing(opening.map { center + (it - center) * (ringRadius / radius) },
            -PocketGeometry.BEVEL * (1f - cos(angle)))
    }
    val innerOpening = lipRings.last().outline
    fun mouth(side: Float) = mouthCenter + lateral * (width * side / 2f)
    fun jawBack(side: Float): Point {
        val taper = tan(Math.toRadians((if (corner) 7.0 else 14.0))).toFloat()
        return mouth(side) + outward * 0.055f - lateral * (side * 0.055f * taper)
    }
    val backLiner = innerOpening.indices.mapNotNull { i ->
        val a = innerOpening[i]; val b = innerOpening[(i + 1) % innerOpening.size]
        if (((a + b) * 0.5f - center).dot(outward) >= -0.00001f) a to b else null
    }
    fun linerTop(point: Point) = center + (point - center).normalized() * (radius - 0.008f)
    fun containsDrop(point: Point) = (point - center).length() <= radius + 0.012f
}

class CushionGeometry(val id: Int, points: List<Point>) {
    val outline: List<Point> = if (points.indices.sumOf { i ->
        val a = points[i]; val b = points[(i + 1) % points.size]
        (a.x * b.z - b.x * a.z).toDouble()
    } >= 0) points else points.reversed()
}

/** Shared planar geometry. Convex subtraction keeps rendering and support boundaries identical. */
object PocketGeometry {
    const val CAPTURE_DEPTH = -0.120f
    const val BEVEL = 0.003f
    const val RAIL_HEIGHT = 0.055f
    const val LINER_SHOULDER = -0.004f
    const val LINER_BOTTOM = -0.18f
    val pockets = buildList {
        for (z in listOf(-1f, 1f)) for (x in listOf(-1f, 1f)) {
            val outward = Point(x, z).normalized()
            val corner = Point(x * TableGeometry.HALF_WIDTH, z * TableGeometry.HALF_LENGTH)
            add(PocketDefinition(size, true, corner - outward * (0.1143f / 2f), outward))
        }
        for (x in listOf(-1f, 1f)) add(PocketDefinition(size, false, Point(x * TableGeometry.HALF_WIDTH, 0f), Point(x, 0f)))
    }
    val cushions: List<CushionGeometry> = buildList {
        val ends = pockets.flatMap { p -> listOf(-1f, 1f).map { side -> p.mouth(side) to p.jawBack(side) } }
        for (z in listOf(-TableGeometry.HALF_LENGTH, TableGeometry.HALF_LENGTH)) {
            val pair = ends.filter { abs(it.first.z - z) < 0.00001f }.sortedBy { it.first.x }
            add(CushionGeometry(size, listOf(pair[0].first, pair[1].first, pair[1].second, pair[0].second)))
        }
        for (x in listOf(-TableGeometry.HALF_WIDTH, TableGeometry.HALF_WIDTH)) {
            val pairs = ends.filter { abs(it.first.x - x) < 0.00001f }.sortedBy { it.first.z }
            for (i in listOf(0, 2)) add(CushionGeometry(size, listOf(pairs[i].first, pairs[i + 1].first, pairs[i + 1].second, pairs[i].second)))
        }
    }
    val support = cutOpenings(listOf(rectangle(TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_LENGTH)))
    val cloth = cutOpenings(listOf(rectangle(0.61f, 1.17f)))
    val wood = cutOpenings(subtract(rectangle(TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_LENGTH), rectangle(0.61f, 1.17f)))

    fun rectangle(x: Float, z: Float) = listOf(Point(-x, -z), Point(x, -z), Point(x, z), Point(-x, z))
    private fun cutOpenings(polygons: List<List<Point>>): List<List<Point>> =
        pockets.fold(polygons) { remaining, pocket -> remaining.flatMap { subtract(it, pocket.opening) } }

    fun subtract(subject: List<Point>, hole: List<Point>): List<List<Point>> {
        var inside = subject
        val outside = mutableListOf<List<Point>>()
        for (i in hole.indices) {
            if (inside.size < 3) break
            val a = hole[i]
            val b = hole[(i + 1) % hole.size]
            val piece = clip(inside, a, b, false)
            if (area(piece) > 0.00000001f) outside.add(piece)
            inside = clip(inside, a, b, true)
        }
        return outside
    }

    private fun clip(polygon: List<Point>, a: Point, b: Point, keepInside: Boolean): List<Point> {
        if (polygon.isEmpty()) return emptyList()
        fun distance(p: Point) = (b.x - a.x) * (p.z - a.z) - (b.z - a.z) * (p.x - a.x)
        val result = mutableListOf<Point>()
        var previous = polygon.last()
        var previousDistance = distance(previous)
        for (current in polygon) {
            val currentDistance = distance(current)
            val previousKept = if (keepInside) previousDistance >= 0f else previousDistance <= 0f
            val currentKept = if (keepInside) currentDistance >= 0f else currentDistance <= 0f
            if (previousKept != currentKept) result.add(previous + (current - previous) * (previousDistance / (previousDistance - currentDistance)))
            if (currentKept) result.add(current)
            previous = current
            previousDistance = currentDistance
        }
        return result
    }
    fun area(polygon: List<Point>): Float = abs(polygon.indices.sumOf { i ->
        val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
        (a.x * b.z - b.x * a.z).toDouble()
    }.toFloat()) / 2f
}
