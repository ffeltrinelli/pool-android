package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.RoundState
import io.github.ffeltrinelli.pool.view.ViewMode
import kotlin.math.*

/** One layout supplies drawing and hit regions, including the modal input barrier. */
class HudLayout(width: Int, height: Int) {
    private val width = width.coerceAtLeast(1)
    private val height = height.coerceAtLeast(1)
    val uiHeight = 420f * this.height / this.width
    val gear = Rectangle(348f, uiHeight - 96f, 48f, 48f)
    val panel = Rectangle(30f, uiHeight / 2 - 160f, 360f, 340f)
    val restart = Rectangle(60f, uiHeight / 2 + 60f, 300f, 48f)
    val top = Rectangle(60f, uiHeight / 2 - 40f, 146f, 48f)
    val player = Rectangle(214f, uiHeight / 2 - 40f, 146f, 48f)
    val close = Rectangle(60f, uiHeight / 2 - 125f, 300f, 48f)
    val playAgain = Rectangle(100f, uiHeight / 2 - 85f, 220f, 48f)

    fun hit(screenX: Int, screenY: Int, settingsOpen: Boolean, finished: Boolean): HudAction? {
        val x = screenX * 420f / width
        val y = uiHeight - screenY * uiHeight / height
        if (settingsOpen) return when {
            restart.contains(x, y) -> HudAction.RESTART
            top.contains(x, y) -> HudAction.TOP_VIEW
            player.contains(x, y) -> HudAction.PLAYER_VIEW
            close.contains(x, y) -> HudAction.CLOSE
            else -> HudAction.BLOCK
        }
        if (gear.contains(x, y)) return HudAction.SETTINGS
        if (finished) return if (playAgain.contains(x, y)) HudAction.RESTART else HudAction.BLOCK
        return null
    }
}

class GameHud : Disposable {
    private val sprites = SpriteBatch()
    private val font = BitmapFont()
    private val glyphs = GlyphLayout()
    private val camera = OrthographicCamera()
    private val white = texture(1, 1) { setColor(Color.WHITE); fill() }
    private val gearIcon = texture(64, 64) {
        setColor(Color.valueOf("f2e5c8"))
        fillCircle(32, 32, 20)
        for (tooth in 0..7) {
            val angle = tooth * PI / 4
            fun x(radius: Int, side: Int) = (32 + radius * cos(angle) - side * sin(angle)).toInt()
            fun y(radius: Int, side: Int) = (32 + radius * sin(angle) + side * cos(angle)).toInt()
            fillTriangle(x(16, -5), y(16, -5), x(27, -5), y(27, -5), x(27, 5), y(27, 5))
            fillTriangle(x(16, -5), y(16, -5), x(27, 5), y(27, 5), x(16, 5), y(16, 5))
        }
        setColor(Color.CLEAR)
        setBlending(Pixmap.Blending.None)
        fillCircle(32, 32, 9)
    }
    var layout = HudLayout(420, 933)
        private set

    private fun texture(width: Int, height: Int, paint: Pixmap.() -> Unit): Texture {
        val pixels = Pixmap(width, height, Pixmap.Format.RGBA8888)
        return try { pixels.paint(); Texture(pixels).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) } }
        finally { pixels.dispose() }
    }

    fun resize(width: Int, height: Int) {
        layout = HudLayout(width, height)
        camera.setToOrtho(false, 420f, layout.uiHeight)
    }

    fun render(session: GameSession, settingsOpen: Boolean, mode: ViewMode) {
        val h = layout.uiHeight
        sprites.projectionMatrix = camera.combined
        sprites.begin()
        text("POOL", 28f, h - 34f, 1.9f, "f2e5c8")
        text("SOLO PRACTICE", 29f, h - 57f, 0.65f, "8ba6a8")
        text("%02d / 15 LEFT".format(session.remaining), 29f, h - 86f, 0.85f, "d5e5dd")
        if (session.finished) drawResult(session)
        drawGear()
        if (settingsOpen) drawSettings(mode)
        sprites.end()
    }

    private fun drawGear() {
        rectangle(layout.gear, "32504f")
        sprites.color = Color.WHITE
        sprites.draw(gearIcon, layout.gear.x + 8f, layout.gear.y + 8f, 32f, 32f)
    }

    private fun drawSettings(mode: ViewMode) {
        shade()
        rectangle(layout.panel, "163039")
        text("SETTINGS", 210f, layout.uiHeight / 2 + 140f, 1.35f, "f2e5c8", true)
        button(layout.restart, "Restart")
        text("View mode", 60f, layout.uiHeight / 2 + 30f, 0.92f, "b8ccca")
        button(layout.top, "Top view", mode == ViewMode.TOP)
        button(layout.player, "Player view", mode == ViewMode.PLAYER)
        button(layout.close, "Close")
    }

    private fun drawResult(session: GameSession) {
        val center = layout.uiHeight / 2
        shade()
        rectangle(Rectangle(30f, center - 112f, 360f, 225f), "163039")
        text("PRACTICE COMPLETE", 210f, center + 80f, 0.72f, "9dbbb7", true)
        text(if (session.state == RoundState.SCRATCHED) "Cue ball pocketed" else "Table cleared", 210f, center + 40f, 1.35f, "f2e5c8", true)
        text("${15 - session.remaining} of 15 object balls pocketed", 210f, center + 6f, 0.84f, "b8ccca", true)
        button(layout.playAgain, "PLAY AGAIN")
    }

    private fun shade() {
        sprites.color = Color(0.02f, 0.055f, 0.07f, 0.86f)
        sprites.draw(white, 0f, 0f, 420f, layout.uiHeight)
    }

    private fun rectangle(rect: Rectangle, hex: String) {
        sprites.color = Color.valueOf(hex)
        sprites.draw(white, rect.x, rect.y, rect.width, rect.height)
    }

    private fun button(rect: Rectangle, label: String, selected: Boolean = false) {
        rectangle(rect, if (selected) "527e70" else "32504f")
        if (selected) rectangle(Rectangle(rect.x, rect.y, rect.width, 3f), "d9bd83")
        text(label, rect.x + rect.width / 2, rect.y + rect.height / 2 + 5f, 0.88f, "f2e5c8", true)
    }

    private fun text(value: String, x: Float, y: Float, scale: Float, hex: String, centered: Boolean = false) {
        font.data.setScale(scale)
        font.color = Color.valueOf(hex)
        glyphs.setText(font, value)
        sprites.color = Color.WHITE
        font.draw(sprites, glyphs, if (centered) x - glyphs.width / 2 else x, y)
    }

    override fun dispose() { sprites.dispose(); font.dispose(); white.dispose(); gearIcon.dispose() }
}
