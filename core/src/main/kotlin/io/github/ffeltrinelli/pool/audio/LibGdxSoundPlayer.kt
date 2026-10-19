package io.github.ffeltrinelli.pool.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound

/** Owns preloaded native sound buffers; all calls happen on the game thread. */
class LibGdxSoundPlayer : SoundPlayer {
    private val sounds = mutableMapOf<ImpactSound, Sound>()
    private val failed = mutableSetOf<ImpactSound>()
    private val playbackWarnings = mutableSetOf<ImpactSound>()

    init {
        ImpactSound.entries.forEach { clip ->
            try { sounds[clip] = Gdx.audio.newSound(Gdx.files.internal(clip.path)) }
            catch (failure: RuntimeException) { unavailable(clip, failure) }
        }
    }

    override fun play(sound: ImpactSound, volume: Float): Long {
        if (sound in failed) return -1
        return try {
            val id = sounds[sound]?.play(volume) ?: -1
            if (id < 0 && playbackWarnings.add(sound)) {
                Gdx.app.error("PoolAudio", "Could not start ${sound.path}; gameplay continues")
            }
            id
        }
        catch (failure: RuntimeException) { unavailable(sound, failure); -1 }
    }

    override fun stop(sound: ImpactSound, id: Long) {
        try { sounds[sound]?.stop(id) }
        catch (failure: RuntimeException) { unavailable(sound, failure) }
    }

    private fun unavailable(sound: ImpactSound, failure: RuntimeException) {
        if (failed.add(sound)) Gdx.app.error("PoolAudio", "Sound unavailable: ${sound.path}; gameplay continues", failure)
    }

    override fun dispose() {
        sounds.forEach { (clip, sound) ->
            try { sound.dispose() }
            catch (failure: RuntimeException) { unavailable(clip, failure) }
        }
        sounds.clear()
    }
}
