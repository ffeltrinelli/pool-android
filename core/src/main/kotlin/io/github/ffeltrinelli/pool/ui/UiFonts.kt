package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import kotlin.math.roundToInt

/** Font sizes in the UI's 420-unit coordinate system, before display-density conversion. */
enum class TextStyle(val size: Float, val strokeWidth: Float = 0f) {
    BODY(16.5f), BUTTON(16.2f), CAPTION(15.75f), SMALL(15f),
    SYMBOL(28f, 0.6f), HEADING(22.5f), RESULT(24f), SPLASH_TITLE(34.5f)
}

/** Owns native font generation and display-sized atlases; queries never allocate fonts. */
class UiFonts(private vararg val styles: TextStyle) : Disposable {
    private val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/Lato-Regular.ttf"))
    private var renderWidth = 0
    private var fonts = emptyMap<TextStyle, BitmapFont>()

    fun resize(pixelWidth: Int) {
        val nextWidth = pixelWidth.coerceAtLeast(1)
        if (nextWidth == renderWidth) return
        val pixelsPerUnit = nextWidth / 420f
        val generated = mutableMapOf<Pair<Int, Float>, BitmapFont>()
        val replacement = try {
            styles.associateWith { style ->
                val pixelSize = (style.size * pixelsPerUnit).roundToInt().coerceAtLeast(1)
                val strokeWidth = style.strokeWidth * pixelsPerUnit
                generated.getOrPut(pixelSize to strokeWidth) { generate(pixelSize, pixelsPerUnit, strokeWidth) }
            }
        } catch (failure: Throwable) {
            generated.values.forEach(BitmapFont::dispose)
            throw failure
        }
        fonts.values.toSet().forEach(BitmapFont::dispose)
        fonts = replacement
        renderWidth = nextWidth
    }

    operator fun get(style: TextStyle): BitmapFont = fonts.getValue(style)

    private fun generate(pixelSize: Int, pixelsPerUnit: Float, strokeWidth: Float): BitmapFont {
        val parameters = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = pixelSize
            borderWidth = strokeWidth
            borderColor = Color.WHITE
            minFilter = TextureFilter.Linear
            magFilter = TextureFilter.Linear
        }
        return generator.generateFont(parameters).apply {
            data.setScale(1f / pixelsPerUnit)
            setUseIntegerPositions(false)
        }
    }

    override fun dispose() {
        fonts.values.toSet().forEach(BitmapFont::dispose)
        fonts = emptyMap()
        generator.dispose()
    }
}
