package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.view.ViewMode
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import kotlin.math.*

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

    fun render(session: GameSession, mode: ViewMode, aidedAim: Boolean, presentation: HudPresentation) {
        val h = layout.uiHeight
        sprites.projectionMatrix = camera.combined
        sprites.begin()
        text("POOL", 28f, h - 34f, 1.9f, "f2e5c8")
        fun groupLabel(player: Player): String = session.group(player)?.let {
            "${it.label}: ${session.remaining(player)} left"
        } ?: "Unassigned"
        text("You: ${groupLabel(Player.HUMAN)}", 29f, h - 57f, 0.78f, "d5e5dd")
        text("Computer: ${groupLabel(Player.COMPUTER)}", 29f, h - 75f, 0.78f, "d5e5dd")
        val status = when {
            presentation.computerThinking -> "Computer thinking..."
            session.placement != null -> if (session.player == Player.HUMAN) "Place the cue ball" else "Computer placing cue ball"
            session.group(session.player) == null -> "${if (session.player == Player.HUMAN) "Your turn" else "Computer's turn"} - open table"
            session.remaining(session.player) == 0 -> if (session.player == Player.HUMAN) "You're on the 8-ball" else "Computer is on the 8-ball"
            session.player == Player.HUMAN -> "Your turn"
            else -> "Computer's turn"
        }
        text(status, 29f, h - 103f, 0.80f, "d5e5dd")
        if (session.group(session.player) == null && !session.finished)
            text("Open table - groups not assigned", 210f, h - 124f, 0.68f, "b8ccca", true)
        if (presentation.notice != null && !session.finished) {
            font.data.setScale(0.72f)
            font.color = Color.valueOf("f2d295")
            font.draw(sprites, presentation.notice, 30f, h - 146f, 360f, com.badlogic.gdx.utils.Align.center, true)
        }
        if (session.placement != null && session.player == Player.HUMAN) {
            val instruction = if (session.placement == io.github.ffeltrinelli.pool.rules.PlacementRegion.ANYWHERE)
                "Anywhere on the table" else "Behind the head string"
            text(instruction, 210f, 163f, 0.95f, "f2e5c8", true)
            text(presentation.placementError ?: "Tap or drag the highlighted ball", 210f, 137f, 0.78f,
                if (presentation.placementError == null) "b8ccca" else "f29b91", true)
            button(layout.confirmPlacement, "Confirm placement", presentation.placementError == null)
        }
        if (session.finished) drawResult(session)
        drawGear()
        if (presentation.settingsOpen) drawSettings(mode, aidedAim, presentation.difficulty)
        sprites.end()
    }

    private fun drawGear() {
        rectangle(layout.gear, "32504f")
        sprites.color = Color.WHITE
        sprites.draw(gearIcon, layout.gear.x + 8f, layout.gear.y + 8f, 32f, 32f)
    }

    private fun drawSettings(mode: ViewMode, aidedAim: Boolean, difficulty: OpponentDifficulty) {
        shade()
        rectangle(layout.panel, "163039")
        text("SETTINGS", 60f, layout.uiHeight / 2 + 202.5f, 1.35f, "f2e5c8")
        button(layout.close, "X")
        text("View mode", 60f, layout.uiHeight / 2 + 157.5f, 0.92f, "b8ccca")
        button(layout.top, "Top view", mode == ViewMode.TOP)
        button(layout.player, "Player view", mode == ViewMode.PLAYER)
        text("Aided aim", 60f, layout.uiHeight / 2 + 72.5f, 0.92f, "b8ccca")
        button(layout.aidedAim, if (aidedAim) "On" else "Off", aidedAim)
        drawDifficulty(difficulty)
        button(layout.restart, "Restart")
        button(layout.exit, "Exit game")
    }

    private fun drawDifficulty(difficulty: OpponentDifficulty) {
        val track = layout.difficulty
        text("Difficulty: ${difficulty.label}", 60f, track.y + 67f, 0.92f, "b8ccca")
        rectangle(Rectangle(track.x + 50f, track.y + 22f, 200f, 4f), "527e70")
        for ((index, level) in OpponentDifficulty.entries.withIndex()) {
            val stop = layout.difficultyStops[index]
            val center = stop.x + stop.width / 2
            val selected = level == difficulty
            rectangle(Rectangle(center - 5f, stop.y + 18f, 10f, 12f), "8ba6a8")
            if (selected) rectangle(Rectangle(center - 10f, stop.y + 14f, 20f, 20f), "d9bd83")
            text(level.label, center, stop.y - 10f, 0.82f, if (selected) "f2e5c8" else "b8ccca", true)
        }
    }

    private fun drawResult(session: GameSession) {
        val center = layout.uiHeight / 2
        shade()
        rectangle(Rectangle(30f, center - 112f, 360f, 225f), "163039")
        text("MATCH COMPLETE", 210f, center + 80f, 0.72f, "9dbbb7", true)
        val result = when (session.result) {
            MatchResult.HUMAN_WINS -> "You win"
            MatchResult.COMPUTER_WINS -> "Computer wins"
            null -> ""
        }
        text(result, 210f, center + 40f, 1.35f, "f2e5c8", true)
        text(session.outcome?.decision?.loss ?: "Eight legally pocketed", 210f, center + 6f, 0.7f, "b8ccca", true)
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
