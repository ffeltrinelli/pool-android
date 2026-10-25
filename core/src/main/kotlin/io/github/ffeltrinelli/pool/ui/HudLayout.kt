package io.github.ffeltrinelli.pool.ui

import com.badlogic.gdx.math.Rectangle
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

/** One layout supplies drawing and hit regions, including the modal input barrier. */
class HudLayout(width: Int, height: Int, val placementVisible: Boolean = false, notificationHeight: Float = 13f) {
    val width = width.coerceAtLeast(1)
    private val height = height.coerceAtLeast(1)
    val uiHeight = 420f * this.height / this.width
    val notificationBottom = maxOf(76f, 57f + notificationHeight + 6f)
    val headerHeight = notificationBottom + if (placementVisible) 56f else 0f
    val gameTop = kotlin.math.ceil(headerHeight * this.width / 420f).toInt().coerceAtMost(this.height - 1)
    val gameHeight = this.height - gameTop
    fun gameScreenY(screenY: Int) = screenY - gameTop
    fun containsGame(screenX: Int, screenY: Int) = screenX in 0 until width && screenY in gameTop until height
    fun renderHeight(backBufferHeight: Int) = (backBufferHeight.toLong() * gameHeight / height).toInt().coerceAtLeast(1)
    val gear = Rectangle(348f, uiHeight - 50f, 48f, 48f)
    val panel = Rectangle(30f, uiHeight / 2 - 195f, 360f, 390f)
    val close = Rectangle(336f, panel.y + panel.height - 54f, 48f, 48f)
    val aidedAim = Rectangle(60f, uiHeight / 2 + 55f, 146f, 48f)
    val replays = Rectangle(214f, uiHeight / 2 + 55f, 146f, 48f)
    val difficulty = Rectangle(60f, uiHeight / 2 - 40f, 300f, 48f)
    val difficultyStops = OpponentDifficulty.entries.mapIndexed { index, _ ->
        Rectangle(difficulty.x + index * 100f, difficulty.y, 100f, 48f)
    }
    val restart = Rectangle(60f, uiHeight / 2 - 145f, 146f, 48f)
    val exit = Rectangle(214f, uiHeight / 2 - 145f, 146f, 48f)
    val confirmPlacement = Rectangle(100f, uiHeight - headerHeight + 4f, 220f, 48f)
    fun confirmsPlacement(x: Int, y: Int) = confirmPlacement.contains(x * 420f / width, uiHeight - y * uiHeight / height)
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
            replays.contains(x, y) -> HudAction.TOGGLE_REPLAYS
            aidedAim.contains(x, y) -> HudAction.TOGGLE_AIM
            exit.contains(x, y) -> HudAction.EXIT
            restart.contains(x, y) -> HudAction.RESTART
            close.contains(x, y) -> HudAction.CLOSE
            else -> HudAction.BLOCK
        }
        if (gear.contains(x, y)) return HudAction.SETTINGS
        if (finished) return if (playAgain.contains(x, y)) HudAction.RESTART else HudAction.BLOCK
        return null
    }
}

