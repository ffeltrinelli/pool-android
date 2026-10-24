package io.github.ffeltrinelli.pool.render

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.*
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.*

/** Builds the visible table from the same pocket boundaries used by Bullet. */
internal object TableModel {
    private val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
    fun create(): Model {
        val builder = ModelBuilder()
        builder.begin()
        val wood = part(builder, "wood", "664533")
        surface(wood, PocketGeometry.wood, PocketGeometry.RAIL_HEIGHT)
        walls(wood, PocketGeometry.rectangle(TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_LENGTH), PocketGeometry.RAIL_HEIGHT, -0.12f)
        val cloth = part(builder, "cloth", "197765")
        surface(cloth, PocketGeometry.cloth, 0f)
        val cushion = part(builder, "cushions", "10564e")
        PocketGeometry.cushions.forEach {
            surface(cushion, listOf(it.outline), PocketGeometry.RAIL_HEIGHT)
            walls(cushion, it.outline, PocketGeometry.RAIL_HEIGHT, 0f)
        }
        val liner = part(builder, "liners", "111a1b")
        PocketGeometry.pockets.forEach { pocket ->
            pocket.lipRings.zipWithNext().forEach { (outer, inner) ->
                for (i in outer.outline.indices) {
                    val j = (i + 1) % outer.outline.size
                    quad(cloth, point(outer.outline[i], outer.height), point(inner.outline[i], inner.height), point(inner.outline[j], inner.height), point(outer.outline[j], outer.height))
                }
            }
            pocket.backLiner.forEach { (a, b) ->
                val topA = pocket.linerTop(a); val topB = pocket.linerTop(b)
                quad(liner, point(topA, PocketGeometry.RAIL_HEIGHT), point(a, PocketGeometry.LINER_SHOULDER), point(b, PocketGeometry.LINER_SHOULDER), point(topB, PocketGeometry.RAIL_HEIGHT))
                val capA = pocket.center + (a - pocket.center).normalized() * pocket.radius
                val capB = pocket.center + (b - pocket.center).normalized() * pocket.radius
                quad(liner, point(topA, PocketGeometry.RAIL_HEIGHT), point(topB, PocketGeometry.RAIL_HEIGHT), point(capB, PocketGeometry.RAIL_HEIGHT), point(capA, PocketGeometry.RAIL_HEIGHT))
            }
            walls(liner, pocket.innerOpening.reversed(), -PocketGeometry.BEVEL, PocketGeometry.LINER_BOTTOM)
            surface(liner, listOf(pocket.opening), -0.19f)
        }
        return builder.end()
    }
    private fun part(builder: ModelBuilder, name: String, hex: String) =
        builder.part(name, GL20.GL_TRIANGLES, attributes, Material(ColorAttribute.createDiffuse(Color.valueOf(hex))))
    private fun point(p: Point, y: Float) = Vector3(p.x, y, p.z)
    private fun surface(mesh: MeshPartBuilder, polygons: List<List<Point>>, y: Float) {
        polygons.forEach { polygon ->
            for (i in 1 until polygon.lastIndex) triangle(mesh, point(polygon[0], y), point(polygon[i + 1], y), point(polygon[i], y))
        }
    }
    private fun walls(mesh: MeshPartBuilder, outline: List<Point>, top: Float, bottom: Float) {
        for (i in outline.indices) {
            val a = outline[i]; val b = outline[(i + 1) % outline.size]
            quad(mesh, point(a, top), point(a, bottom), point(b, bottom), point(b, top))
        }
    }
    private fun quad(mesh: MeshPartBuilder, a: Vector3, b: Vector3, c: Vector3, d: Vector3) {
        triangle(mesh, a, b, c); triangle(mesh, a, c, d)
    }
    private fun triangle(mesh: MeshPartBuilder, a: Vector3, b: Vector3, c: Vector3) {
        val normal = b.cpy().sub(a).crs(c.cpy().sub(a)).nor()
        mesh.triangle(MeshPartBuilder.VertexInfo().setPos(a).setNor(normal),
            MeshPartBuilder.VertexInfo().setPos(b).setNor(normal), MeshPartBuilder.VertexInfo().setPos(c).setNor(normal))
    }
}
