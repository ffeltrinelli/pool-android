package io.github.ffeltrinelli.pool.replay

import io.github.ffeltrinelli.pool.audio.GameSound
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Sample-accurate mix of bundled mono WAV effects; no live audio capture is required. */
class ReplayPcm(private val clip: ReplayClip, load: (String) -> ByteArray) {
    private val samples = GameSound.entries.associateWith { decodeWav(load(it.path)) }
    fun write(target: ByteBuffer, firstSample: Int, count: Int) {
        target.order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) { offset ->
            val sample = firstSample + offset - INTRO_SAMPLES
            var sum = 0f
            if (sample >= 0) clip.sounds.forEach { voice ->
                val start = (voice.start * SAMPLE_RATE).roundToInt()
                val stop = (voice.stop * SAMPLE_RATE).roundToInt()
                val index = sample - start
                val pcm = samples.getValue(voice.sound)
                if (sample < stop && index in pcm.indices) sum += pcm[index] * voice.volume
            }
            target.putShort(sum.roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }
    companion object {
        const val SAMPLE_RATE = 44100
        const val INTRO_SAMPLES = 2 * SAMPLE_RATE
        fun decodeWav(bytes: ByteArray): ShortArray {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bytes.size >= 12 && b.int == 0x46464952) { "Expected RIFF WAV" }
            b.int
            require(b.int == 0x45564157) { "Expected WAVE" }
            var valid = false
            while (b.remaining() >= 8) {
                val kind = b.int
                val length = b.int
                require(length >= 0 && length <= b.remaining()) { "Invalid WAV chunk" }
                val end = b.position() + length
                when (kind) {
                    0x20746d66 -> {
                        require(length >= 16 && b.short.toInt() == 1 && b.short.toInt() == 1 && b.int == SAMPLE_RATE)
                        b.int; b.short
                        require(b.short.toInt() == 16) { "Expected mono 16-bit PCM" }
                        valid = true
                    }
                    0x61746164 -> {
                        require(valid && length % 2 == 0)
                        return ShortArray(length / 2) { b.short }
                    }
                }
                b.position((end + (length % 2)).coerceAtMost(bytes.size))
            }
            error("WAV data missing")
        }
    }
}
