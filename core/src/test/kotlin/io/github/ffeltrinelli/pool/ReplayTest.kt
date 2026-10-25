package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.CuePose
import io.github.ffeltrinelli.pool.replay.*
import io.github.ffeltrinelli.pool.view.ReplayCamera
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ReplayTest {
    companion object { @JvmStatic @org.junit.BeforeClass fun natives() { com.badlogic.gdx.utils.GdxNativesLoader.load() } }
    private fun balls(z: Float = 0f, visible: Boolean = true) = listOf(
        BallPose(0, 0f, TableGeometry.BALL_RADIUS, z, 0f, 0f, 0f, 1f, true),
        BallPose(1, 0.2f, TableGeometry.BALL_RADIUS, -0.5f, 0f, 0f, 0f, 1f, visible))
    private val cue = CuePose(Point(0f, -1f), 0.05f)
    private fun clip() = ReplayClip(listOf(ReplayFrame(0f, balls(), cue), ReplayFrame(1f, balls(-0.2f), null),
        ReplayFrame(2f, balls(-0.4f, false), null)),
        listOf(ReplaySound(GameSound.CUE, 0.4f, 0f, 0.2f), ReplaySound(GameSound.POCKET, 0.35f, 2f, 2.68f)), 0f, 1f, 2f)

    @Test fun `slow motion stretches animation camera events and audio starts without stretching samples or hold`() {
        val source = clip()
        val slow = source.slowMotion()
        val stretch = 1f / ReplayClip.PLAYBACK_SPEED
        assertEquals(source.strikeSeconds * stretch, slow.strikeSeconds, 0.0001f)
        assertEquals(source.contactSeconds * stretch, slow.contactSeconds, 0.0001f)
        assertEquals(source.pocketSeconds * stretch, slow.pocketSeconds, 0.0001f)
        assertEquals(source.frameAt(0.5f).balls, slow.frameAt(0.5f * stretch).balls)
        assertEquals(stretch, slow.timeScale, 0.0001f)
        val pocket = slow.sounds.last()
        assertEquals(slow.pocketSeconds, pocket.start, 0.0001f)
        assertEquals(GameSound.POCKET.seconds, pocket.stop - pocket.start, 0.0001f)
        assertEquals(slow.pocketSeconds + GameSound.POCKET.seconds, slow.videoSeconds, 0.0001f)
        val view = ReplayCamera()
        view.update(slow, slow.contactSeconds + ReplayCamera.PULLBACK_SECONDS * stretch / 2f, 720, 1280)
        assertEquals(0.13f, view.camera.near, 0.0001f)
        val playback = ReplayPlayback(slow, Sounds())
        playback.advance(slow.pocketSeconds)
        assertEquals(5, playback.holdSecondsRemaining)
        playback.advance(4.99f)
        assertFalse(playback.finished)
        playback.advance(0.02f)
        assertTrue(playback.finished)
        assertEquals(2f, source.pocketSeconds, 0f)
    }

    @Test fun `only legal scoring outcomes trigger replay for either player`() {
        for (player in Player.entries) {
            fun outcome(d: RuleDecision, ids: Set<Int> = setOf(1)) = ShotOutcome(player, d, ids)
            assertTrue(qualifiesForReplay(outcome(RuleDecision(continues = true))))
            assertTrue(qualifiesForReplay(outcome(RuleDecision(won = true), setOf(8))))
            assertFalse(qualifiesForReplay(outcome(RuleDecision())))
            assertFalse(qualifiesForReplay(outcome(RuleDecision(continues = true, foul = FoulReason.SCRATCH))))
            assertFalse(qualifiesForReplay(outcome(RuleDecision(loss = "Early eight"), setOf(8))))
            assertFalse(qualifiesForReplay(outcome(RuleDecision(continues = true), emptySet())))
        }
    }
    @Test fun `recorder trims aiming and retains first cue contact and last pocket`() {
        val recorder = ReplayRecorder()
        repeat(50) { recorder.aim(0.1f, balls(), cue.copy(tipGap = it * 0.001f)) }
        recorder.strike(balls(), cue)
        recorder.event(PhysicsEvent.Contacts(emptySet(), setOf(0)), 0.1f)
        recorder.step(0.1f, balls(-0.1f))
        recorder.event(PhysicsEvent.Contacts(setOf(1), emptySet()), 0.1f)
        recorder.event(PhysicsEvent.Pocketed(1), 0.1f)
        recorder.step(0.1f, balls(-0.2f, false))
        recorder.event(PhysicsEvent.Pocketed(2), 0.1f)
        recorder.step(0.1f, balls(-0.3f, false))
        val clip = recorder.finish()!!
        assertTrue(clip.strikeSeconds in 2.8f..3.001f)
        assertEquals(clip.strikeSeconds + 0.1f, clip.contactSeconds, 0.0001f)
        assertEquals(clip.strikeSeconds + 0.3f, clip.pocketSeconds, 0.0001f)
        assertTrue(clip.frames.first().cue != null)
        assertNull(recorder.finish())
    }
    @Test fun `playback waits full five seconds and preserves remaining time across suspension`() {
        val player = Sounds()
        val playback = ReplayPlayback(clip(), player)
        playback.advance(2f)
        assertFalse(playback.finished)
        assertTrue(player.played.contains(GameSound.POCKET))
        playback.advance(4.5f)
        playback.silence()
        assertEquals(6.5f, playback.seconds, 0f)
        playback.advance(0f)
        assertFalse(playback.finished)
        playback.advance(0.499f)
        assertFalse(playback.finished)
        playback.advance(0.002f)
        assertTrue(playback.finished)
        playback.close()
    }
    @Test fun `Skip countdown starts only at final frame and shows five through one`() {
        val playback = ReplayPlayback(clip(), Sounds())
        assertNull(playback.holdSecondsRemaining)
        assertEquals("Skip", replaySkipLabel(ShareState.Idle, playback.holdSecondsRemaining))
        playback.advance(1.99f)
        assertNull(playback.holdSecondsRemaining)
        playback.advance(0.01f)
        for (remaining in 5 downTo 1) {
            assertEquals(remaining, playback.holdSecondsRemaining)
            assertEquals("Skip ($remaining)", replaySkipLabel(ShareState.Idle, playback.holdSecondsRemaining))
            assertFalse(playback.finished)
            playback.silence()
            playback.advance(0f)
            assertEquals(remaining, playback.holdSecondsRemaining)
            assertEquals("Cancel", replaySkipLabel(ShareState.Encoding, playback.holdSecondsRemaining))
            playback.advance(1f)
        }
        assertTrue(playback.finished)
        assertNull(playback.holdSecondsRemaining)
        assertEquals(2.68f, playback.clip.videoSeconds, 0.001f)
    }

    @Test fun `interpolation preserves disappearance boundary and clamps final picture`() {
        val clip = clip()
        assertEquals(-0.1f, clip.frameAt(0.5f).balls[0].z, 0.0001f)
        assertTrue(clip.frameAt(1.99f).balls[1].visible)
        assertFalse(clip.frameAt(2f).balls[1].visible)
        assertEquals(clip.frameAt(2f).balls, clip.frameAt(100f).balls)
        assertEquals(2.68f, clip.videoSeconds, 0.001f)
    }
    @Test fun `recorded voices retain gain and explicit stops independent of backend availability`() {
        val recorder = ReplayRecorder()
        recorder.strike(balls(), cue)
        val player = ReplayRecordingPlayer(Sounds(), recorder)
        val id = player.play(GameSound.CUE, 0.25f)
        recorder.step(0.1f, balls())
        player.stop(GameSound.CUE, id)
        recorder.event(PhysicsEvent.Pocketed(1), 0.1f)
        recorder.step(0.1f, balls(visible = false))
        val sound = recorder.finish()!!.sounds.single()
        assertEquals(0.25f, sound.volume, 0f)
        assertEquals(0.1f, sound.stop, 0.0001f)
    }
    @Test fun `slow rendered frame retains its final pocket audio flush`() {
        val recorder = ReplayRecorder()
        recorder.strike(balls(), cue)
        recorder.event(PhysicsEvent.Pocketed(1), 0.01f)
        recorder.step(0.01f, balls(visible = false))
        recorder.step(0.09f, balls(visible = false))
        recorder.sound(1L, GameSound.POCKET, 0.35f)
        val clip = recorder.finish()!!
        assertEquals(GameSound.POCKET, clip.sounds.single().sound)
        assertEquals(0.78f, clip.videoSeconds, 0.001f)
    }

    @Test fun `camera tracks cue until first contact then fits entire table`() {
        val camera = ReplayCamera()
        val clip = clip()
        camera.update(clip, 0f, 720, 1280)
        val start = camera.camera.position.cpy()
        camera.update(clip, 0.5f, 720, 1280)
        assertEquals(-0.1f, camera.camera.position.z - start.z, 0.001f)
        assertEquals(start.y, camera.camera.position.y, 0.001f)
        for ((w,h) in listOf(720 to 1280, 1080 to 2400, 1280 to 720)) {
            camera.update(clip, 2f, w, h)
            for (x in listOf(-TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_WIDTH))
                for (z in listOf(-TableGeometry.OUTER_HALF_LENGTH, TableGeometry.OUTER_HALF_LENGTH)) {
                    assertTrue(camera.camera.frustum.pointInFrustum(x, 0f, z))
                }
        }
    }
    @Test fun `replay near plane preserves cue closeup and depth precision through pullback`() {
        val view = ReplayCamera()
        val source = clip()
        for (degrees in 0 until 360 step 45) {
            val angle = Math.toRadians(degrees.toDouble())
            val heading = Point(kotlin.math.sin(angle).toFloat(), -kotlin.math.cos(angle).toFloat())
            val diagonal = source.copy(frames = source.frames.map { frame ->
                frame.copy(cue = frame.cue?.copy(direction = heading))
            })
            view.update(diagonal, source.contactSeconds, 720, 1280)
            assertEquals(0.01f, view.camera.near, 0.0001f)
            val cue = com.badlogic.gdx.math.Vector3(0f, TableGeometry.BALL_RADIUS, -0.2f)
            assertTrue(view.camera.frustum.sphereInFrustum(cue, TableGeometry.BALL_RADIUS))
            view.update(diagonal, source.contactSeconds + ReplayCamera.PULLBACK_SECONDS / 2f, 720, 1280)
            assertEquals(0.13f, view.camera.near, 0.0001f)
            view.update(diagonal, source.contactSeconds + ReplayCamera.PULLBACK_SECONDS, 720, 1280)
            assertEquals(0.25f, view.camera.near, 0.0001f)
            for (x in listOf(-TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_WIDTH))
                for (z in listOf(-TableGeometry.OUTER_HALF_LENGTH, TableGeometry.OUTER_HALF_LENGTH)) {
                    assertTrue(view.camera.frustum.pointInFrustum(x, 0f, z))
                }
        }
    }

    @Test fun `PCM export has silent two second intro and audible aligned cue`() {
        val assets = sequenceOf(File("../assets"), File("assets")).first { it.isDirectory }
        val pcm = ReplayPcm(clip()) { File(assets, it).readBytes() }
        val intro = ByteBuffer.allocate(ReplayPcm.INTRO_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.write(intro, 0, ReplayPcm.INTRO_SAMPLES)
        assertTrue(intro.array().all { it == 0.toByte() })
        val cueSound = ByteBuffer.allocate(8820 * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.write(cueSound, ReplayPcm.INTRO_SAMPLES, 8820)
        assertTrue(cueSound.array().any { it != 0.toByte() })
    }
    private class Sounds : SoundPlayer {
        val played = mutableListOf<GameSound>()
        override fun play(sound: GameSound, volume: Float): Long { played.add(sound); return played.size.toLong() }
        override fun stop(sound: GameSound, id: Long) = Unit
        override fun dispose() = Unit
    }
}
