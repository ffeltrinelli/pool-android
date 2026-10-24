package io.github.ffeltrinelli.pool.lwjgl3

import com.badlogic.gdx.utils.JsonWriter
import io.github.ffeltrinelli.pool.audio.GameSound
import io.github.ffeltrinelli.pool.audio.SoundPlayer
import java.io.File

/** Captures production playback decisions without recording unrelated system audio. */
internal class RecordingSoundPlayer : SoundPlayer {
    private data class Voice(val id: Long, val sound: GameSound, val volume: Float, val startFrame: Int, var stopFrame: Int? = null)
    private val voices = mutableListOf<Voice>()
    private var frame = 0

    fun beginFrame(frame: Int) { this.frame = frame }

    override fun play(sound: GameSound, volume: Float): Long {
        val id = voices.size.toLong()
        voices += Voice(id, sound, volume, frame)
        return id
    }

    override fun stop(sound: GameSound, id: Long) {
        voices.firstOrNull { it.id == id && it.sound == sound }?.let {
            if (it.stopFrame == null) it.stopFrame = frame
        }
    }

    fun write(file: File, frames: Int, fps: Int) {
        check(voices.count { it.sound == GameSound.CUE } >= 2) { "Demo must contain human and computer strokes" }
        check(voices.any { it.sound == GameSound.BALL }) { "Demo contains no ball impacts" }
        JsonWriter(file.writer()).use { json ->
            json.setOutputType(com.badlogic.gdx.utils.JsonWriter.OutputType.json)
            json.`object`().set("frames", frames).set("fps", fps).array("voices")
            voices.forEach { voice ->
                json.`object`().set("sound", voice.sound.path).set("volume", voice.volume)
                    .set("startFrame", voice.startFrame).set("stopFrame", voice.stopFrame ?: frames).pop()
            }
            json.pop().pop()
        }
    }

    override fun dispose() = Unit
}
