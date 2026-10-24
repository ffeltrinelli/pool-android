package io.github.ffeltrinelli.pool.startup

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import kotlin.math.min
import io.github.ffeltrinelli.pool.ui.UiFonts
import io.github.ffeltrinelli.pool.ui.TextStyle

interface StartupSplash : Disposable {
    fun resize(width: Int, height: Int)
    fun render()
}

/** The same source artwork supplies launcher assets and this larger presentation. */
class SplashScreen : StartupSplash {
    private val batch = SpriteBatch()
    private val fonts = UiFonts(TextStyle.SPLASH_TITLE, TextStyle.BODY)
    private val artwork = Texture(Gdx.files.internal("branding/eight-ball.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val layout = GlyphLayout()
    private val width = 420f
    private var height = 933f

    override fun resize(width: Int, height: Int) {
        this.height = 420f * height.coerceAtLeast(1) / width.coerceAtLeast(1)
        batch.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, this.width, this.height)
    }

    override fun render() = render(Gdx.graphics.backBufferWidth)

    fun render(pixelWidth: Int) {
        fonts.resize(pixelWidth)
        Gdx.gl.glClearColor(9f / 255, 19f / 255, 24f / 255, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        val size = min(360f, height * 0.48f)
        val centerY = height * 0.56f
        batch.begin()
        batch.draw(artwork, (width - size) / 2, centerY - size / 2, size, size)
        centeredText("Feltri 8-Ball", centerY - size / 2 - 12f, TextStyle.SPLASH_TITLE)
        centeredText("by Francesco Feltrinelli", centerY - size / 2 - 52f, TextStyle.BODY)
        batch.end()
    }

    private fun centeredText(text: String, y: Float, style: TextStyle) {
        val font = fonts[style]
        font.color = Color.valueOf("f3e9cc")
        layout.setText(font, text)
        font.draw(batch, layout, (width - layout.width) / 2, y)
    }

    override fun dispose() {
        artwork.dispose()
        fonts.dispose()
        batch.dispose()
    }
}
