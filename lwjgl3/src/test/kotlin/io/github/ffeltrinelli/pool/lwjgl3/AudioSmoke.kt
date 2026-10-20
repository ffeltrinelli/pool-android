package io.github.ffeltrinelli.pool.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio
import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.model.*
import java.io.File
import kotlin.math.abs

/** Uses real OpenAL decoding and source allocation, including the null output driver on CI. */
object AudioSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val report = File(args.single()).absoluteFile
        val results = mutableListOf<String>()
        var failure: Throwable? = null
        try {
            Lwjgl3Application(object : ApplicationAdapter() {
                override fun create() {
                    try {
                        val backend = Gdx.audio as OpenALLwjgl3Audio
                        GameSound.entries.forEach { clip ->
                            val decoded = backend.newSound(Gdx.files.internal(clip.path))
                            try {
                                check(decoded.rate == 44100 && decoded.channels == 1) { "Unexpected PCM format: ${clip.path}" }
                                check(abs(decoded.duration() - clip.seconds) < 0.002f) { "Unexpected duration: ${clip.path}" }
                            } finally { decoded.dispose() }
                        }
                        val player = LibGdxSoundPlayer()
                        try {
                            GameSound.entries.forEach { clip ->
                                val id = player.play(clip, 0f)
                                check(id >= 0 && backend.getSourceId(id) > 0) { "No OpenAL source for ${clip.path}; audio device unavailable" }
                                player.stop(clip, id)
                                results.add("PASS ${clip.path}: mono PCM 44100 Hz, ${clip.seconds}s, decoded and allocated OpenAL source")
                            }
                        } finally { player.dispose() }
                        val audio = PoolAudio(LibGdxSoundPlayer())
                        try {
                            ImpactKind.entries.forEach { audio.queue(Impact(it, 0.1f)) }
                            audio.queue(PocketDrop(0, 0))
                            audio.flush(0f)
                            audio.pause(); audio.resume(); audio.flush(0f); audio.clear()
                        } finally { audio.dispose() }
                        results.add("PASS production audio lifecycle")
                    } catch (problem: Throwable) { failure = problem }
                    finally { Gdx.app.exit() }
                }
            }, Lwjgl3ApplicationConfiguration().apply {
                setTitle("Pool audio verification")
                setWindowedMode(64, 64)
                setInitialVisible(false)
                useVsync(false)
            })
        } catch (problem: Throwable) { failure = problem }
        finally {
            report.parentFile.mkdirs()
            val text = results.joinToString("\n") + (failure?.let { "\nFAIL ${it.stackTraceToString()}" } ?: "\nPASS audio smoke suite")
            report.writeText(text)
            println(text)
        }
        failure?.let { throw IllegalStateException("Audio smoke failed. See $report", it) }
    }
}
