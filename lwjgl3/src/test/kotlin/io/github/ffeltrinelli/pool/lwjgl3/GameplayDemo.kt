package io.github.ffeltrinelli.pool.lwjgl3

import com.badlogic.gdx.*
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import io.github.ffeltrinelli.pool.PoolGame
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.Player
import io.github.ffeltrinelli.pool.model.RoundState
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** Optional documentation capture using production gameplay, input, rules, planning, and audio policy. */
object GameplayDemo {
    private const val WIDTH = 540
    private const val HEIGHT = 1200
    private const val FPS = 30
    private const val FRAMES = FPS * 30

    @JvmStatic fun main(args: Array<String>) {
        val directory = File(args.single()).absoluteFile.apply { mkdirs() }
        val recording = Recording(directory)
        Lwjgl3Application(recording, Lwjgl3ApplicationConfiguration().apply {
            setTitle("Feltri 8-Ball documentation recording")
            setWindowedMode(WIDTH, HEIGHT)
            setInitialVisible(false)
            setPreferencesConfig(File(directory, "preferences").path, Files.FileType.Absolute)
            setForegroundFPS(FPS)
            useVsync(false)
            disableAudio(true)
        })
        recording.failure?.let { throw IllegalStateException("Gameplay recording failed", it) }
    }

    private class Recording(private val directory: File) : ApplicationAdapter() {
        private val session = GameSession()
        private val sounds = RecordingSoundPlayer()
        private val game = PoolGame(session) { sounds }
        private lateinit var backendGraphics: Graphics
        private lateinit var backendInput: Input
        private lateinit var framebuffer: FrameBuffer
        private lateinit var encoder: Process
        private lateinit var frames: BufferedOutputStream
        private val pixels = ByteArray(WIDTH * HEIGHT * 4)
        private var frame = 0
        private var humanShots = 0
        private var computerShots = 0
        private var gameDisposed = false
        var failure: Throwable? = null
            private set

        override fun create() {
            try {
                installFrameClock()
                preparePreferencesAndFramebuffer()
                startEncoder()
                prepareGame()
            } catch (problem: Throwable) {
                failure = problem
                Gdx.app.exit()
            }
        }

        private fun installFrameClock() {
            backendGraphics = Gdx.graphics
            backendInput = Gdx.input
            Gdx.graphics = object : Graphics by backendGraphics {
                override fun getWidth() = WIDTH
                override fun getHeight() = HEIGHT
                override fun getBackBufferWidth() = WIDTH
                override fun getBackBufferHeight() = HEIGHT
                override fun getDeltaTime() = 1f / FPS
                @Suppress("OVERRIDE_DEPRECATION")
                override fun getRawDeltaTime() = 1f / FPS
            }
            Gdx.input = object : Input by backendInput {
                override fun getCurrentEventTime() = frame * 1_000_000_000L / FPS
            }
        }

        private fun preparePreferencesAndFramebuffer() {
            Gdx.app.getPreferences("pool-settings").apply {
                putString("opponent-difficulty", "MEDIUM")
                putBoolean("aided-aim", true)
                putBoolean("replays", true)
                flush()
            }
            framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, true)
        }

        private fun startEncoder() {
            encoder = ProcessBuilder("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "rawvideo", "-pixel_format", "rgba", "-video_size", "${WIDTH}x$HEIGHT",
                "-framerate", "$FPS", "-i", "pipe:0", "-vf", "vflip", "-an",
                "-c:v", "libx264", "-preset", "fast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-movflags", "+faststart", File(directory, "silent.mp4").path)
                .redirectError(File(directory, "encoder.log")).start()
            frames = BufferedOutputStream(encoder.outputStream)
        }

        private fun prepareGame() {
            game.create()
            game.resize(WIDTH, HEIGHT)
            // Prepare the real placement HUD before routing its confirmation tap.
            framebuffer.begin()
            try { game.render() } finally { framebuffer.end() }
            tap(WIDTH / 2, 134)
            check(session.canShoot && session.breaking)
        }

        override fun render() {
            if (failure != null) return
            try {
                if (frame == FRAMES) {
                    finishRecording()
                    return
                }
                sounds.beginFrame(frame)
                val previous = session.state
                val player = session.player
                drivePointer()
                framebuffer.begin()
                try {
                    game.render()
                    if (previous != RoundState.MOVING && session.state == RoundState.MOVING) {
                        if (player == Player.HUMAN) humanShots++ else computerShots++
                    }
                    val capture = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT)
                    try {
                        if (frame == FPS * 5) PixmapIO.writePNG(Gdx.files.absolute(File(directory, "poster.png").path), capture, -1, true)
                        capture.pixels.get(pixels)
                        frames.write(pixels)
                    } finally { capture.dispose() }
                } finally { framebuffer.end() }
                frame++
            } catch (problem: Throwable) {
                failure = problem
                Gdx.app.exit()
            }
        }

        private fun finishRecording() {
            check(humanShots >= 1 && computerShots >= 1) { "Clip did not show both players: human=$humanShots computer=$computerShots" }
            sounds.beginFrame(frame)
            disposeGame()
            sounds.write(File(directory, "audio-events.json"), FRAMES, FPS)
            frames.close()
            check(encoder.waitFor(10, TimeUnit.SECONDS) && encoder.exitValue() == 0) { "Video encoding failed; see encoder.log" }
            println("Recorded 30s at ${WIDTH}x$HEIGHT/$FPS fps; human shots=$humanShots, computer shots=$computerShots")
            Gdx.app.exit()
        }

        private fun drivePointer() {
            val input = Gdx.input.inputProcessor
            when {
                frame == 30 -> input.touchDown(270, 750, 0, Input.Buttons.LEFT)
                frame in 31..50 -> input.touchDragged(270, 750 + (frame - 30) * 3, 0)
                frame in 51..60 -> input.touchDragged(270, 810 - (frame - 50) * 25, 0)
                frame == 61 -> input.touchUp(270, 535, 0, Input.Buttons.LEFT)
            }
        }

        private fun tap(x: Int, y: Int) {
            Gdx.input.inputProcessor.touchDown(x, y, 0, Input.Buttons.LEFT)
            Gdx.input.inputProcessor.touchUp(x, y, 0, Input.Buttons.LEFT)
        }

        private fun disposeGame() {
            if (gameDisposed) return
            gameDisposed = true
            game.dispose()
        }

        override fun dispose() {
            runCatching { disposeGame() }
            if (::frames.isInitialized) runCatching { frames.close() }
            if (::encoder.isInitialized && encoder.isAlive) encoder.destroyForcibly()
            if (::framebuffer.isInitialized) framebuffer.dispose()
            if (::backendGraphics.isInitialized) Gdx.graphics = backendGraphics
            if (::backendInput.isInitialized) Gdx.input = backendInput
        }
    }
}
