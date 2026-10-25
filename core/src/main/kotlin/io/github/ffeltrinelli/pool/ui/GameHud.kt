package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import kotlin.math.*

class GameHud : Disposable {
    private val sprites = SpriteBatch()
    private val fonts = UiFonts(TextStyle.BODY, TextStyle.BUTTON, TextStyle.CAPTION,
        TextStyle.SMALL, TextStyle.HEADING, TextStyle.RESULT, TextStyle.SYMBOL)
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
    private val helpBall = texture(64, 64) {
        setColor(Color.valueOf("f2e5c8"))
        fillCircle(32, 32, 29)
    }
    var layout = HudLayout(420, 933)
        private set

    private fun texture(width: Int, height: Int, paint: Pixmap.() -> Unit): Texture {
        val pixels = Pixmap(width, height, Pixmap.Format.RGBA8888)
        return try { pixels.paint(); Texture(pixels).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) } }
        finally { pixels.dispose() }
    }

    private var width = 420
    private var height = 933

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        updateLayout(layout.placementVisible)
    }

    private fun updateLayout(placementVisible: Boolean, notificationHeight: Float = 13f) {
        layout = HudLayout(width, height, placementVisible, notificationHeight)
        camera.setToOrtho(false, 420f, layout.uiHeight)
    }

    fun prepare(session: GameSession, presentation: HudPresentation, pixelWidth: Int) {
        fonts.resize(pixelWidth)
        val font = fonts[TextStyle.BODY]
        glyphs.setText(font, notification(session, presentation), Color.WHITE, 362f,
            com.badlogic.gdx.utils.Align.left, true)
        val next = HudLayout(width, height, session.placement != null && session.player == Player.HUMAN, glyphs.height)
        if (next.headerHeight == layout.headerHeight && next.placementVisible == layout.placementVisible) return
        updateLayout(next.placementVisible, glyphs.height)
    }

    fun render(session: GameSession, aidedAim: Boolean, presentation: HudPresentation) {
        sprites.projectionMatrix = camera.combined
        sprites.begin()
        drawHeader(session, presentation)
        if (layout.placementVisible) {
            button(layout.confirmPlacement, "Confirm placement", presentation.placementError == null)
        }
        if (session.finished) drawResult(session)
        drawGear()
        if (presentation.settingsOpen) drawSettings(aidedAim, presentation.difficulty, presentation.replays)
        if (presentation.helpOpen) drawHelp()
        sprites.end()
    }

    fun renderSettings(aidedAim: Boolean, presentation: HudPresentation, pixelWidth: Int) {
        fonts.resize(pixelWidth)
        sprites.projectionMatrix = camera.combined
        sprites.begin()
        drawSettings(aidedAim, presentation.difficulty, presentation.replays)
        if (presentation.helpOpen) drawHelp()
        sprites.end()
    }

    private fun drawHeader(session: GameSession, presentation: HudPresentation) {
        val h = layout.uiHeight
        rectangle(Rectangle(0f, h - layout.headerHeight, 420f, layout.headerHeight), "08161c")
        fun groupLabel(player: Player): String = session.group(player)?.let {
            "${it.label}: ${session.remaining(player)} left"
        } ?: "Unassigned"
        text("You: ${groupLabel(Player.HUMAN)}", 29f, h - 14f, TextStyle.BODY, "d5e5dd")
        text("Computer: ${groupLabel(Player.COMPUTER)}", 29f, h - 34f, TextStyle.BODY, "d5e5dd")
        val font = fonts[TextStyle.BODY]
        font.color = Color.valueOf(if (presentation.placementError != null) "f29b91" else "f2d295")
        font.draw(sprites, notification(session, presentation), 29f, h - 57f, 362f,
            com.badlogic.gdx.utils.Align.left, true)
    }

    private fun notification(session: GameSession, presentation: HudPresentation): String {
        if (presentation.notice != null && !session.finished) return presentation.notice
        return when {
            presentation.computerThinking -> "Computer thinking..."
            session.placement != null -> when {
                session.player != Player.HUMAN -> "Computer placing cue ball"
                presentation.placementError != null -> presentation.placementError
                session.placement == io.github.ffeltrinelli.pool.rules.PlacementRegion.ANYWHERE -> "Tap or drag cue ball anywhere"
                else -> "Tap or drag cue ball behind head string"
            }
            session.group(session.player) == null -> if (session.player == Player.HUMAN) "Your turn" else "Computer's turn"
            session.remaining(session.player) == 0 -> if (session.player == Player.HUMAN) "You're on the 8-ball" else "Computer is on the 8-ball"
            session.player == Player.HUMAN -> "Your turn"
            else -> "Computer's turn"
        }
    }

    private fun drawGear() {
        rectangle(layout.gear, "32504f")
        sprites.color = Color.WHITE
        sprites.draw(gearIcon, layout.gear.x + 8f, layout.gear.y + 8f, 32f, 32f)
    }

    private fun drawSettings(aidedAim: Boolean, difficulty: OpponentDifficulty, replays: Boolean) {
        shade()
        rectangle(layout.panel, "163039")
        text("SETTINGS", 60f, layout.panel.y + layout.panel.height - 35f, TextStyle.HEADING, "f2e5c8")
        symbolButton(layout.info, "?")
        symbolButton(layout.close, "X")
        text("Aided aim", 60f, layout.aidedAim.y + 66f, TextStyle.BODY, "b8ccca")
        button(layout.aidedAim, if (aidedAim) "On" else "Off", aidedAim)
        text("Replays", 214f, layout.replays.y + 66f, TextStyle.BODY, "b8ccca")
        button(layout.replays, if (replays) "On" else "Off", replays)
        drawDifficulty(difficulty)
        button(layout.restart, "Restart")
        button(layout.exit, "Exit game")
    }

    private fun drawHelp() {
        shade()
        val top = layout.helpPanel.y + layout.helpPanel.height
        rectangle(layout.helpPanel, "163039")
        text("HOW TO PLAY", 60f, top - 35f, TextStyle.HEADING, "f2e5c8")
        symbolButton(layout.helpClose, "X")
        helpStep("Aim", "Drag left or right to turn. The dashed guide helps you line up.", top - 94f)
        doubleArrow(72f, top - 126f, 132f, top - 126f)
        helpStep("Pull back", "Drag down to draw the cue back. Keep your finger on the table.", top - 194f)
        arrow(102f, top - 208f, 102f, top - 248f)
        helpStep("Push to strike", "Push up until the cue hits. Faster pushes make stronger shots.", top - 294f)
        arrow(102f, top - 348f, 102f, top - 308f)
        helpStep("Place the cue ball", "When prompted, tap or drag to a valid spot, then Confirm placement.", top - 394f)
        doubleArrow(72f, top - 431f, 132f, top - 431f)
        sprites.color = Color.WHITE
        sprites.draw(helpBall, 93f, top - 440f, 18f, 18f)
        helpStep("Win the rack", "Clear your solids or stripes, then pot the 8. Hit your group first.", top - 494f)
        sprites.color = Color.WHITE
        sprites.draw(helpBall, 84f, top - 546f, 36f, 36f)
        text("8", 102f, top - 521f, TextStyle.HEADING, "163039", true)
        text("Lift to re-aim. Wait for the balls to stop.", 210f, top - 586f, TextStyle.SMALL, "b8ccca", true)
    }

    private fun helpStep(title: String, description: String, y: Float) {
        text(title, 156f, y, TextStyle.BUTTON, "f2e5c8")
        val font = fonts[TextStyle.SMALL]
        font.color = Color.valueOf("b8ccca")
        font.draw(sprites, description, 156f, y - 25f, 208f, com.badlogic.gdx.utils.Align.left, true)
    }

    private fun doubleArrow(x1: Float, y1: Float, x2: Float, y2: Float) {
        arrow(x1, y1, x2, y2)
        arrowHead(x1, y1, atan2(y2 - y1, x2 - x1) + PI.toFloat())
    }

    private fun arrow(x1: Float, y1: Float, x2: Float, y2: Float) {
        line(x1, y1, x2, y2)
        arrowHead(x2, y2, atan2(y2 - y1, x2 - x1))
    }

    private fun arrowHead(x: Float, y: Float, angle: Float) {
        for (side in listOf(-1, 1)) {
            val wing = angle + side * 0.65f
            line(x, y, x - 11f * cos(wing), y - 11f * sin(wing))
        }
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        sprites.color = Color.valueOf("d9bd83")
        sprites.draw(white, x1, y1 - 1.5f, 0f, 1.5f, sqrt(dx * dx + dy * dy), 3f,
            1f, 1f, atan2(dy, dx) * 180f / PI.toFloat(), 0, 0, 1, 1, false, false)
    }

    private fun drawDifficulty(difficulty: OpponentDifficulty) {
        val track = layout.difficulty
        text("Difficulty: ${difficulty.label}", 60f, track.y + 73f, TextStyle.BODY, "b8ccca")
        rectangle(Rectangle(track.x + 50f, track.y + 22f, 200f, 4f), "527e70")
        for ((index, level) in OpponentDifficulty.entries.withIndex()) {
            val stop = layout.difficultyStops[index]
            val center = stop.x + stop.width / 2
            val selected = level == difficulty
            rectangle(Rectangle(center - 5f, stop.y + 18f, 10f, 12f), "8ba6a8")
            if (selected) rectangle(Rectangle(center - 10f, stop.y + 14f, 20f, 20f), "d9bd83")
            text(level.label, center, stop.y - 10f, TextStyle.CAPTION, if (selected) "f2e5c8" else "b8ccca", true)
        }
    }

    private fun drawResult(session: GameSession) {
        val center = layout.uiHeight / 2
        shade()
        rectangle(Rectangle(30f, center - 112f, 360f, 225f), "163039")
        text("MATCH COMPLETE", 210f, center + 80f, TextStyle.SMALL, "9dbbb7", true)
        val result = when (session.result) {
            MatchResult.HUMAN_WINS -> "You win"
            MatchResult.COMPUTER_WINS -> "Computer wins"
            null -> ""
        }
        text(result, 210f, center + 40f, TextStyle.RESULT, "f2e5c8", true)
        val font = fonts[TextStyle.SMALL]
        font.color = Color.valueOf("b8ccca")
        font.draw(sprites, session.outcome?.decision?.loss ?: "Eight legally pocketed", 50f, center + 8f,
            320f, com.badlogic.gdx.utils.Align.center, true)
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

    private fun symbolButton(rect: Rectangle, symbol: String) {
        rectangle(rect, "32504f")
        val font = fonts[TextStyle.SYMBOL]
        font.color = Color.valueOf("f2e5c8")
        glyphs.setText(font, symbol)
        sprites.color = Color.WHITE
        font.draw(sprites, glyphs, rect.x + (rect.width - glyphs.width) / 2,
            rect.y + (rect.height + glyphs.height) / 2)
    }

    private fun button(rect: Rectangle, label: String, selected: Boolean = false) {
        rectangle(rect, if (selected) "527e70" else "32504f")
        if (selected) rectangle(Rectangle(rect.x, rect.y, rect.width, 3f), "d9bd83")
        text(label, rect.x + rect.width / 2, rect.y + rect.height / 2 + 5f, TextStyle.BUTTON, "f2e5c8", true)
    }

    private fun text(value: String, x: Float, y: Float, style: TextStyle, hex: String, centered: Boolean = false) {
        val font = fonts[style]
        font.color = Color.valueOf(hex)
        glyphs.setText(font, value)
        sprites.color = Color.WHITE
        font.draw(sprites, glyphs, if (centered) x - glyphs.width / 2 else x, y)
    }

    override fun dispose() { sprites.dispose(); fonts.dispose(); white.dispose(); gearIcon.dispose(); helpBall.dispose() }
}
