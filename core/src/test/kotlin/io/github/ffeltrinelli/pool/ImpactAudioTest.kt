package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.model.*
import org.junit.Assert.*
import org.junit.Test

class ImpactAudioTest {
    private class Player : SoundPlayer {
        data class Played(val sound: ImpactSound, val volume: Float, val id: Long)
        val played = mutableListOf<Played>()
        val stopped = mutableListOf<Long>()
        var disposed = false
        var available = true
        override fun play(sound: ImpactSound, volume: Float): Long {
            if (!available) return -1
            val id = played.size.toLong()
            played.add(Played(sound, volume, id))
            return id
        }
        override fun stop(sound: ImpactSound, id: Long) { stopped.add(id) }
        override fun dispose() { disposed = true }
    }

    @Test fun `power raises volume and is bounded`() {
        val tuning = AudioTuning()
        assertEquals(0f, tuning.volume(0.001f), 0f)
        assertEquals(0f, tuning.volume(Float.NaN), 0f)
        assertTrue(tuning.volume(0.1f) < tuning.volume(0.5f))
        assertEquals(tuning.maximumVolume, tuning.volume(20f), 0f)
    }

    @Test fun `strongest pending sounds survive a busy frame`() {
        val player = Player()
        val audio = PoolAudio(player, AudioTuning(maximumVoices = 2, volumeBudget = 2f))
        audio.queue(Impact(ImpactKind.BALL, 0.1f))
        audio.queue(Impact(ImpactKind.CUSHION, 0.7f))
        audio.queue(Impact(ImpactKind.CUE, 1f))
        audio.flush(0f)
        assertEquals(listOf(ImpactSound.CUE, ImpactSound.CUSHION), player.played.map { it.sound })
        audio.flush(0f)
        assertEquals(2, player.played.size)
    }

    @Test fun `strong hit replaces quietest active voice`() {
        val player = Player()
        val audio = PoolAudio(player, AudioTuning(maximumVoices = 2, volumeBudget = 2f))
        audio.queue(Impact(ImpactKind.BALL, 0.1f)); audio.queue(Impact(ImpactKind.BALL, 0.2f)); audio.flush(0f)
        audio.queue(Impact(ImpactKind.CUSHION, 1f)); audio.flush(0f)
        assertEquals(listOf(1L), player.stopped)
        audio.queue(Impact(ImpactKind.BALL, 0.05f)); audio.flush(0f)
        assertEquals(3, player.played.size)
    }

    @Test fun `voice count never exceeds eight and summed gain stays bounded`() {
        val player = Player()
        val audio = PoolAudio(player)
        repeat(20) { audio.queue(Impact(ImpactKind.BALL, 0.01f)) }
        audio.flush(0f)
        assertEquals(8, player.played.size)
        assertTrue(player.played.sumOf { it.volume.toDouble() } <= 1.00001)
        audio.queue(Impact(ImpactKind.CUSHION, 0.005f)); audio.flush(0f)
        assertEquals(8, player.played.size)
        audio.flush(0.25f)
        assertEquals(8, player.stopped.size)
    }

    @Test fun `strong new hit takes gain budget from quieter active sounds`() {
        val player = Player()
        val audio = PoolAudio(player, AudioTuning(maximumVolume = 0.8f, volumeBudget = 1f))
        audio.queue(Impact(ImpactKind.BALL, 0.4f)); audio.queue(Impact(ImpactKind.BALL, 0.4f)); audio.flush(0f)
        audio.queue(Impact(ImpactKind.CUE, 1.15f)); audio.flush(0f)
        assertEquals(2, player.stopped.size)
        assertEquals(ImpactSound.CUE, player.played.last().sound)
        assertEquals(0.8f, player.played.last().volume, 0f)
    }

    @Test fun `pause discards queued and active sounds and resume never replays them`() {
        val player = Player()
        val audio = PoolAudio(player)
        audio.queue(Impact(ImpactKind.CUE, 0.1f)); audio.flush(0f)
        audio.queue(Impact(ImpactKind.BALL, 0.1f))
        audio.pause()
        audio.queue(Impact(ImpactKind.CUSHION, 0.1f))
        audio.resume(); audio.flush(0f)
        assertEquals(1, player.played.size)
        assertEquals(listOf(0L), player.stopped)
        audio.queue(Impact(ImpactKind.BALL, 0.1f)); audio.flush(0f)
        assertEquals(2, player.played.size)
        audio.dispose()
        assertTrue(player.disposed)
        assertEquals(listOf(0L, 1L), player.stopped)
    }

    @Test fun `restart clears sounds and permits a fresh shot`() {
        val player = Player()
        val audio = PoolAudio(player)
        audio.queue(Impact(ImpactKind.CUE, 0.1f)); audio.flush(0f)
        audio.queue(Impact(ImpactKind.BALL, 0.1f)); audio.clear(); audio.flush(0f)
        assertEquals(1, player.played.size)
        audio.queue(Impact(ImpactKind.CUE, 0.1f)); audio.flush(0f)
        assertEquals(2, player.played.size)
    }

    @Test fun `unavailable audio leaves no active voice`() {
        val player = Player().apply { available = false }
        val audio = PoolAudio(player)
        audio.queue(Impact(ImpactKind.CUE, 1f)); audio.flush(0f); audio.clear()
        assertTrue(player.stopped.isEmpty())
    }
}
