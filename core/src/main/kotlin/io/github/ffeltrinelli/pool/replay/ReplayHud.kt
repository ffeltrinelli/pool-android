package io.github.ffeltrinelli.pool.replay

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.Rectangle
import io.github.ffeltrinelli.pool.ui.*

/** Drawing and hit testing use the same logical rectangles. */
class ReplayHud : AutoCloseable {
    private val sprites = SpriteBatch()
    private val fonts = UiFonts(TextStyle.BUTTON, TextStyle.BODY)
    private val glyph = GlyphLayout()
    private val white = Pixmap(1, 1, Pixmap.Format.RGBA8888).let {
        it.setColor(Color.WHITE); it.fill(); Texture(it).also { _ -> it.dispose() }
    }
    private val shareIcon = Pixmap(48, 48, Pixmap.Format.RGBA8888).let { p ->
        p.setColor(Color.valueOf("f2e5c8"))
        for (offset in -1..1) {
            p.drawLine(12, 24 + offset, 34, 11 + offset)
            p.drawLine(12, 24 + offset, 34, 37 + offset)
        }
        p.fillCircle(12, 24, 5); p.fillCircle(34, 11, 5); p.fillCircle(34, 37, 5)
        Texture(p).also { p.dispose() }
    }
    private val gearIcon = Pixmap(48, 48, Pixmap.Format.RGBA8888).let { p ->
        p.setColor(Color.valueOf("f2e5c8")); p.fillCircle(24, 24, 13)
        for (i in 0..7) {
            val angle = i * kotlin.math.PI / 4
            val x = (24 + 15 * kotlin.math.cos(angle)).toInt()
            val y = (24 + 15 * kotlin.math.sin(angle)).toInt()
            p.fillCircle(x, y, 4)
        }
        p.setBlending(Pixmap.Blending.None); p.setColor(Color.CLEAR); p.fillCircle(24, 24, 6)
        Texture(p).also { p.dispose() }
    }
    private val camera = OrthographicCamera()
    private val skip = Rectangle(24f, 24f, 112f, 48f)
    private val share = Rectangle(348f, 24f, 48f, 48f)
    private val gear = Rectangle(348f, 0f, 48f, 48f)
    private var logicalHeight = 933f

    enum class Action { SKIP, SHARE, SETTINGS, NONE }
    fun hit(x: Int, y: Int, width: Int, height: Int): Action {
        resize(width, height)
        val px = x * 420f / width
        val py = logicalHeight - y * 420f / width
        return when {
            skip.contains(px, py) -> Action.SKIP
            share.contains(px, py) -> Action.SHARE
            gear.contains(px, py) -> Action.SETTINGS
            else -> Action.NONE
        }
    }
    private fun resize(width: Int, height: Int) {
        logicalHeight = 420f * height / width
        gear.y = logicalHeight - 60f
        camera.setToOrtho(false, 420f, logicalHeight)
    }
    fun render(width: Int, height: Int, canShare: Boolean, state: ShareState, progress: Float, holdSecondsRemaining: Int? = null) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        resize(width, height); fonts.resize(width)
        sprites.projectionMatrix = camera.combined
        sprites.begin()
        label("REPLAY", 24f, logicalHeight - 30f)
        box(gear); sprites.color = Color.WHITE; sprites.draw(gearIcon, gear.x + 6f, gear.y + 6f, 36f, 36f)
        box(skip); label(replaySkipLabel(state, holdSecondsRemaining), 45f, 54f)
        if (canShare && state != ShareState.Encoding && state != ShareState.Sharing) {
            box(share); sprites.color = Color.WHITE; sprites.draw(shareIcon, share.x + 6f, share.y + 6f, 36f, 36f)
        }
        when (state) {
            ShareState.Encoding -> label("Creating video: ${(progress * 100).toInt()}%", 24f, 108f)
            is ShareState.Failed -> { label("Export failed. Tap Share to retry.", 24f, 108f) }
            else -> Unit
        }
        sprites.end()
    }
    private fun box(rect: Rectangle) {
        sprites.color = Color.valueOf("163039")
        sprites.draw(white, rect.x, rect.y, rect.width, rect.height)
    }
    private fun label(text: String, x: Float, y: Float) {
        val font = fonts[TextStyle.BODY]
        font.color = Color.valueOf("f2e5c8")
        glyph.setText(font, text); sprites.color = Color.WHITE; font.draw(sprites, glyph, x, y)
    }
    override fun close() { gearIcon.dispose(); shareIcon.dispose(); white.dispose(); fonts.dispose(); sprites.dispose() }
}

/** Export cancellation takes precedence over the paused replay countdown. */
fun replaySkipLabel(state: ShareState, holdSecondsRemaining: Int?): String = when {
    state == ShareState.Encoding -> "Cancel"
    holdSecondsRemaining != null -> "Skip ($holdSecondsRemaining)"
    else -> "Skip"
}
