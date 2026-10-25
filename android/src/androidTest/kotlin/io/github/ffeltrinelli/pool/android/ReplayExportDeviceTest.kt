@file:Suppress("DEPRECATION")
package io.github.ffeltrinelli.pool.android

import android.content.Intent
import android.media.MediaExtractor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import com.badlogic.gdx.Gdx
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.CuePose
import io.github.ffeltrinelli.pool.audio.GameSound
import io.github.ffeltrinelli.pool.replay.*
import io.github.ffeltrinelli.pool.render.PoolRenderer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Exercises production offscreen rendering, EGL, codecs, metadata and URI grants on a real device. */
class ReplayExportDeviceTest {
    @Test fun realShotReplaySettingsSharingAndReturn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AndroidLauncher
        val sharingShown = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        fun read(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let {
            it.isAccessible = true; it.get(owner)
        }
        val lifecycle = ActivityLifecycleMonitorRegistry.getInstance()
        val sharingLifecycle = ActivityLifecycleCallback { changedActivity, stage ->
            if (changedActivity === activity && stage == Stage.PAUSED) {
                val current = read(activity.applicationListener, "game")
                val sharing = current?.let { read(it, "sharing") as AndroidReplaySharing }
                if (sharing?.state == ShareState.Sharing) {
                    val file = activity.cacheDir.resolve("replays").listFiles()!!
                        .filter { it.extension == "mp4" }.maxBy { it.lastModified() }
                    file.copyTo(File(activity.filesDir, "replay-real-shot.mp4"), overwrite = true)
                    sharingShown.countDown()
                }
            }
        }
        instrumentation.runOnMainSync { lifecycle.addLifecycleCallback(sharingLifecycle) }
        Gdx.app.postRunnable(object : Runnable {
            var phase = 0
            var pausedFrames = 0
            var savedTime = 0f
            var liveSnapshot: io.github.ffeltrinelli.pool.physics.TableSnapshot? = null
            var game: io.github.ffeltrinelli.pool.PoolGame? = null
            override fun run() {
                try {
                    if (game == null) game = read(activity.applicationListener, "game") as? io.github.ffeltrinelli.pool.PoolGame
                    val current = game
                    if (current == null) { Gdx.app.postRunnable(this); return }
                    val session = read(current, "session") as GameSession
                    val physics = read(current, "physics") as io.github.ffeltrinelli.pool.physics.PoolPhysics
                    val controls = read(current, "controls") as io.github.ffeltrinelli.pool.ui.GameControls
                    val playback = read(current, "replay") as? ReplayPlayback
                    val sharing = read(current, "sharing") as AndroidReplaySharing
                    if (phase == 4 && sharingShown.count == 0L) phase = 5
                    when (phase) {
                        0 -> {
                            // Isolate this replay fixture from the player's saved toggle without writing preferences.
                            controls.javaClass.getDeclaredField("replays").apply { isAccessible = true }.setBoolean(controls, true)
                            // Fixtures stay in test code; production exposes no setup controls.
                            session.restart()
                            while (session.player != Player.HUMAN) session.restart()
                            session.confirmPlacement(); session.beginShot()
                            session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1,2,3,4))); session.accept(PhysicsEvent.Settled)
                            session.beginShot(); session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1))); session.accept(PhysicsEvent.Settled)
                            physics.reset(listOf(0 to Point(0.2f, 0f), 1 to Point(0.44f, 0f)))
                            val recorder = read(current, "recorder") as ReplayRecorder
                            recorder.clear()
                            recorder.aim(0f, physics.replayBalls(), CuePose(Point(1f, 0f), 0.05f))
                            recorder.aim(0.5f, physics.replayBalls(), CuePose(Point(1f, 0f), 0.12f))
                            current.javaClass.getDeclaredMethod("strike", ShotCommand::class.java).apply { isAccessible = true }
                                .invoke(current, ShotCommand(Point(1f, 0f), 0.12f))
                            phase = 1
                        }
                        1 -> if (playback != null) {
                            assertTrue(playback.clip.sounds.any { it.sound == GameSound.POCKET })
                            assertEquals(1f / ReplayClip.PLAYBACK_SPEED, playback.clip.timeScale, 0.0001f)
                            liveSnapshot = physics.snapshot()
                            savedTime = playback.seconds
                            controls.handle(io.github.ffeltrinelli.pool.ui.HudAction.SETTINGS)
                            phase = 2
                        }
                        2 -> {
                            assertNotNull(playback)
                            assertEquals(savedTime, playback!!.seconds, 0f)
                            assertEquals(liveSnapshot, physics.snapshot())
                            if (++pausedFrames == 1) controls.handle(io.github.ffeltrinelli.pool.ui.HudAction.HELP)
                            assertTrue(controls.helpOpen)
                            if (pausedFrames >= 5) {
                                controls.handle(io.github.ffeltrinelli.pool.ui.HudAction.CLOSE)
                                assertTrue(controls.settingsOpen && controls.suspended)
                                controls.handle(io.github.ffeltrinelli.pool.ui.HudAction.CLOSE)
                                phase = 3
                            }
                        }
                        3 -> if (playback != null && playback.seconds >= playback.clip.pocketSeconds) {
                            savedTime = playback.seconds
                            assertEquals(5, playback.holdSecondsRemaining)
                            val width = Gdx.graphics.width
                            Gdx.input.inputProcessor.touchDown(width * 372 / 420, Gdx.graphics.height - width * 48 / 420, 0, 0)
                            phase = 4
                        }
                        4 -> {
                            assertNotNull(playback)
                            assertEquals(savedTime, playback!!.seconds, 0f)
                            assertEquals(5, playback.holdSecondsRemaining)
                            assertEquals(liveSnapshot, physics.snapshot())
                            if (sharing.state is ShareState.Failed) error((sharing.state as ShareState.Failed).message)
                        }
                        5 -> if (playback == null) { complete.countDown(); return }
                    }
                    Gdx.app.postRunnable(this)
                } catch (error: Throwable) { failure.set(error); sharingShown.countDown(); complete.countDown() }
            }
        })
        try {
            assertTrue("Real shot did not reach sharing", sharingShown.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Replay flow failed", it) }
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.waitForIdle(500, 5000)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(activity.filesDir, "replay-share-sheet.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
            assertTrue("Replay did not resume and finish after chooser dismissal", complete.await(15, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Replay return failed", it) }
        } finally {
            instrumentation.runOnMainSync { lifecycle.removeLifecycleCallback(sharingLifecycle) }
        }
    }

    @Test fun exportWithAudioAndSplash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AndroidLauncher
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val sharing = AndroidReplaySharing(activity)
        Gdx.app.postRunnable(object : Runnable {
            var renderer: PoolRenderer? = null
            var export: ReplayExport? = null
            override fun run() {
                try {
                    if (renderer == null) {
                        renderer = PoolRenderer()
                        val balls = listOf(BallPose(0, 0f, TableGeometry.BALL_RADIUS, 0.3f, 0f, 0f, 0f, 1f, true),
                            BallPose(1, 0f, TableGeometry.BALL_RADIUS, -0.3f, 0f, 0f, 0f, 1f, true))
                        val cue = CuePose(Point(1f, -1f).normalized(), 0.05f)
                        val clip = ReplayClip(listOf(ReplayFrame(0f, balls, cue), ReplayFrame(0.5f, balls, cue.copy(tipGap = 0f)),
                            ReplayFrame(1f, balls.map { it.copy(z = it.z - 0.2f) }, null),
                            ReplayFrame(2f, balls.map { it.copy(visible = it.id != 1) }, null)),
                            listOf(ReplaySound(GameSound.CUE, 0.4f, 0.5f, 0.7f), ReplaySound(GameSound.POCKET, 0.35f, 2f, 2.68f)), 0.5f, 1f, 2f).slowMotion()
                        val cancelled = ReplayExport(sharing, clip)
                        cancelled.advance(renderer!!)
                        sharing.cancel(); cancelled.close()
                        export = ReplayExport(sharing, clip)
                    }
                    export!!.advance(renderer!!)
                    when (val state = sharing.state) {
                        ShareState.Ready -> {
                            val file = activity.cacheDir.resolve("replays").listFiles()!!.filter { it.extension == "mp4" }.maxBy { it.lastModified() }
                            file.copyTo(File(activity.filesDir, "replay-device-test.mp4"), overwrite = true)
                            val extractor = MediaExtractor()
                            try {
                                extractor.setDataSource(file.path)
                                assertEquals(2, extractor.trackCount)
                                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                                assertTrue(formats.any { it.getString("mime") == "video/avc" && it.getInteger("width") == 720 && it.getInteger("height") == 1280 })
                                assertTrue(formats.any { it.getString("mime") == "audio/mp4a-latm" })
                            } finally { extractor.release() }
                            val uri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.replays", file)
                            activity.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) }
                            finish()
                        }
                        is ShareState.Failed -> error(state.message)
                        else -> Gdx.app.postRunnable(this)
                    }
                } catch (error: Throwable) { failure.set(error); finish() }
            }
            fun finish() { export?.close(); renderer?.dispose(); sharing.close(); done.countDown() }
        })
        assertTrue("Replay export timed out", done.await(90, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Device export failed", it) }
        val played = CountDownLatch(1)
        var decoded = false
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: Intent) {
                decoded = intent.getBooleanExtra("success", false); played.countDown()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(activity, receiver,
            android.content.IntentFilter("io.github.ffeltrinelli.pool.REPLAY_TEST_PLAYED"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        try {
            val file = activity.cacheDir.resolve("replays").listFiles()!!.filter { it.extension == "mp4" }.maxBy { it.lastModified() }
            val uri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.replays", file)
            activity.runOnUiThread {
                activity.startActivity(Intent(Intent.ACTION_VIEW, uri)
                    .setComponent(android.content.ComponentName("${activity.packageName}.test", ReplayRecipientActivity::class.java.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            assertTrue("Recipient did not finish playback", played.await(15, TimeUnit.SECONDS))
            assertTrue("Recipient could not decode the shared video", decoded)
        } finally { activity.unregisterReceiver(receiver) }

    }
}
