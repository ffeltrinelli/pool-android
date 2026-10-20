package io.github.ffeltrinelli.pool.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import kotlin.math.*

/** Bakes markings in sphere-local coordinates, away from the UV seam and polar singularities. */
internal class BallTextures(font: BitmapFont) : AutoCloseable {
    private val fontData = font.data
    private val atlasData = font.region.texture.textureData
    private val atlas: Pixmap
    private val colors = listOf("f8f7ed", "efbc35", "397dc1", "ce5246", "8e5bb6", "df8737", "379d81", "9c3e4c", "252d36")

    init {
        if (!atlasData.isPrepared) atlasData.prepare()
        atlas = atlasData.consumePixmap()
    }

    fun create(id: Int): Texture {
        val base = Color.rgba8888(Color.valueOf(colors[if (id > 8) id - 8 else id]))
        val ivory = Color.rgba8888(Color.valueOf(colors[0]))
        val stamp = numberStamp(id)
        val surface = Pixmap(512, 256, Pixmap.Format.RGBA8888)
        surface.setBlending(Pixmap.Blending.None)
        try {
            for (v in 0 until surface.height) {
                val latitude = PI * (v + 0.5) / surface.height
                val y = cos(latitude)
                for (u in 0 until surface.width) {
                    // Matches libGDX SphereShapeBuilder's reversed U coordinate.
                    val longitude = 2 * PI * (1 - (u + 0.5) / surface.width)
                    val x = sin(latitude) * cos(longitude)
                    val z = sin(latitude) * sin(longitude)
                    var pixel = if (id > 8 && abs(y) > 0.48) ivory else base
                    if (id > 0 && abs(z) > sqrt(1 - STAMP_RADIUS * STAMP_RADIUS)) {
                        val sx = ((1 + x * sign(z) / STAMP_RADIUS) * stamp.width / 2).toInt().coerceIn(0, stamp.width - 1)
                        val sy = ((1 - y / STAMP_RADIUS) * stamp.height / 2).toInt().coerceIn(0, stamp.height - 1)
                        pixel = stamp.getPixel(sx, sy)
                    }
                    surface.drawPixel(u, v, pixel)
                }
            }
            return Texture(surface, true).apply {
                setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge)
            }
        } finally {
            stamp.dispose()
            surface.dispose()
        }
    }

    private fun numberStamp(id: Int): Pixmap {
        val stamp = Pixmap(128, 128, Pixmap.Format.RGBA8888)
        stamp.setColor(Color.valueOf("fff9e7"))
        stamp.fill()
        if (id == 0) return stamp
        val glyphs = id.toString().map { fontData.getGlyph(it) }
        val scale = 5
        val totalWidth = glyphs.sumOf { it.xadvance } * scale
        var left = (stamp.width - totalWidth) / 2
        for (glyph in glyphs) {
            val top = (stamp.height - glyph.height * scale) / 2
            for (y in 0 until glyph.height * scale) for (x in 0 until glyph.width * scale) {
                val alpha = atlas.getPixel(glyph.srcX + x / scale, glyph.srcY + y / scale) and 255
                stamp.drawPixel(left + glyph.xoffset * scale + x, top + y, (0x172d34 shl 8) or alpha)
            }
            left += glyph.xadvance * scale
        }
        return stamp
    }

    override fun close() {
        if (atlasData.disposePixmap()) atlas.dispose()
    }

    companion object { private const val STAMP_RADIUS = 0.58 }
}
