package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.math.Rectangle
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

/** One layout supplies drawing and hit regions, including the modal input barrier. */
class HudLayout(width: Int, height: Int) {
    private val width = width.coerceAtLeast(1)
    private val height = height.coerceAtLeast(1)
    val uiHeight = 420f * this.height / this.width
    val gear = Rectangle(348f, uiHeight - 96f, 48f, 48f)
    val panel = Rectangle(30f, uiHeight / 2 - 237.5f, 360f, 475f)
    val close = Rectangle(336f, panel.y + panel.height - 54f, 48f, 48f)
    val top = Rectangle(60f, uiHeight / 2 + 97.5f, 146f, 48f)
    val player = Rectangle(214f, uiHeight / 2 + 97.5f, 146f, 48f)
    val aidedAim = Rectangle(60f, uiHeight / 2 + 12.5f, 146f, 48f)
    val difficulty = Rectangle(60f, uiHeight / 2 - 82.5f, 300f, 48f)
    val difficultyStops = OpponentDifficulty.entries.mapIndexed { index, _ ->
        Rectangle(difficulty.x + index * 100f, difficulty.y, 100f, 48f)
    }
    val restart = Rectangle(60f, uiHeight / 2 - 187.5f, 146f, 48f)
    val exit = Rectangle(214f, uiHeight / 2 - 187.5f, 146f, 48f)
    val playAgain = Rectangle(100f, uiHeight / 2 - 85f, 220f, 48f)

    fun difficultyAt(screenX: Int): OpponentDifficulty {
        val x = screenX * 420f / width
        val index = ((x - difficulty.x) / 100f).toInt().coerceIn(0, 2)
        return OpponentDifficulty.entries[index]
    }

    fun containsDifficulty(screenX: Int, screenY: Int): Boolean =
        difficulty.contains(screenX * 420f / width, uiHeight - screenY * uiHeight / height)

    fun hit(screenX: Int, screenY: Int, settingsOpen: Boolean, finished: Boolean): HudAction? {
        val x = screenX * 420f / width
        val y = uiHeight - screenY * uiHeight / height
        if (settingsOpen) return when {
            difficulty.contains(x, y) -> when (difficultyAt(screenX)) {
                OpponentDifficulty.EASY -> HudAction.EASY
                OpponentDifficulty.MEDIUM -> HudAction.MEDIUM
                OpponentDifficulty.HARD -> HudAction.HARD
            }
            aidedAim.contains(x, y) -> HudAction.TOGGLE_AIM
            exit.contains(x, y) -> HudAction.EXIT
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

