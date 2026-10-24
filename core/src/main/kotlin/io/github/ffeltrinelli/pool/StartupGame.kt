package io.github.ffeltrinelli.pool

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import io.github.ffeltrinelli.pool.startup.SplashScreen
import io.github.ffeltrinelli.pool.startup.StartupSplash

/** Owns launch presentation; gameplay receives frames and input only after the intro. */
class StartupGame(
    private val gameFactory: () -> ApplicationListener = { PoolGame() },
    private val splashFactory: () -> StartupSplash = { SplashScreen() },
    private val inputProvider: () -> Input = { Gdx.input },
    private val nanoTime: () -> Long = System::nanoTime
) : ApplicationAdapter() {
    private enum class Phase { SHOWING, INITIALIZING, READY, RUNNING, DISPOSED }
    private lateinit var input: Input
    private var phase = Phase.SHOWING
    private var splash: StartupSplash? = null
    private var game: ApplicationListener? = null
    private var gameInput: InputProcessor? = null
    private var width = 1
    private var height = 1
    private var paused = false
    private var visibleNanos = 0L
    private var lastFrame: Long? = null

    override fun create() {
        input = inputProvider()
        splash = splashFactory()
        input.inputProcessor = null
    }

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        splash?.resize(this.width, this.height)
        game?.resize(this.width, this.height)
    }

    override fun render() {
        if (paused || phase == Phase.DISPOSED) return
        if (phase == Phase.RUNNING) {
            game?.render()
            return
        }
        val previousFrame = lastFrame
        val now = nanoTime()
        previousFrame?.let { visibleNanos += (now - it).coerceAtLeast(0L) }
        splash?.render()
        // First-frame font preparation happens before the splash can become visible.
        lastFrame = if (previousFrame == null) nanoTime() else now
        when (phase) {
            Phase.SHOWING -> phase = Phase.INITIALIZING
            Phase.INITIALIZING -> initializeGame()
            Phase.READY -> if (visibleNanos >= MINIMUM_VISIBLE_NANOS) enterGame()
            else -> Unit
        }
    }

    private fun initializeGame() {
        val initializedGame = gameFactory()
        game = initializedGame
        try {
            initializedGame.create()
            initializedGame.resize(width, height)
            gameInput = input.inputProcessor
        } finally {
            input.inputProcessor = null
        }
        phase = Phase.READY
    }

    private fun enterGame() {
        splash?.dispose()
        splash = null
        input.inputProcessor = gameInput
        phase = Phase.RUNNING
    }

    override fun pause() {
        paused = true
        lastFrame = null
        game?.pause()
    }

    override fun resume() {
        paused = false
        lastFrame = null
        game?.resume()
    }

    override fun dispose() {
        if (phase == Phase.DISPOSED) return
        phase = Phase.DISPOSED
        if (::input.isInitialized) input.inputProcessor = null
        try {
            game?.dispose()
        } finally {
            splash?.dispose()
            splash = null
        }
    }

    companion object {
        private const val MINIMUM_VISIBLE_NANOS = 3_000_000_000L
    }
}
