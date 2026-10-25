package io.github.ffeltrinelli.pool.android

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.CuePose
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.replay.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Exercises oblique full-table views in the same offscreen target used by video export. */
class ReplayDepthDeviceTest {
    @Test fun diagonalExportFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AndroidLauncher
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Gdx.app.postRunnable {
            val renderer = PoolRenderer()
            val sharing = AndroidReplaySharing(activity)
            var export: ReplayExport? = null
            var reference: FrameBuffer? = null
            try {
                val balls = TableGeometry.rack(42).map { (id, p) ->
                    BallPose(id, p.x, TableGeometry.BALL_RADIUS, p.z, 0f, 0f, 0f, 1f, true)
                }
                val cue = CuePose(Point(1f, -1f).normalized(), 0.05f)
                val clip = ReplayClip(listOf(ReplayFrame(0f, balls, cue), ReplayFrame(0.5f, balls, cue.copy(tipGap = 0f)),
                    ReplayFrame(2f, balls, null)), emptyList(), 0.5f, 1f, 2f)
                export = ReplayExport(sharing, clip)
                val target = export.javaClass.getDeclaredField("target").let { it.isAccessible = true; it.get(export) as FrameBuffer }
                val close = capture(renderer, clip, target, 0f)
                try { PixmapIO.writePNG(Gdx.files.absolute(File(activity.filesDir, "replay-close-shadows.png").path), close, -1, true) }
                finally { close.dispose() }
                reference = FrameBuffer(Pixmap.Format.RGBA8888, 720, 1280, true)
                val report = StringBuilder()
                target.begin()
                try {
                    val bits = BufferUtils.newIntBuffer(1)
                    Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_BITS, bits)
                    report.append("Export depth bits: ${bits.get(0)}\n")
                    if (Gdx.gl30 != null || Gdx.graphics.supportsExtension("GL_OES_depth24")) assertTrue(bits.get(0) >= 24)
                } finally { target.end() }
                for (degrees in listOf(45, 135, 225, 315)) {
                    val angle = Math.toRadians(degrees.toDouble())
                    val heading = Point(kotlin.math.sin(angle).toFloat(), -kotlin.math.cos(angle).toFloat())
                    val diagonal = clip.copy(frames = clip.frames.map { frame -> frame.copy(cue = frame.cue?.copy(direction = heading)) })
                    val actual = capture(renderer, diagonal, target)
                    val lowPrecision = capture(renderer, diagonal, reference)
                    try {
                        var mismatches = 0
                        for (y in 0 until 1280) for (x in 0 until 720) {
                            val a = actual.getPixel(x, y)
                            val b = lowPrecision.getPixel(x, y)
                            if (differentColor(a, b)) mismatches++
                        }
                        report.append("$degrees degrees: $mismatches significant depth disagreements\n")
                        PixmapIO.writePNG(Gdx.files.absolute(File(activity.filesDir, "replay-diagonal-$degrees.png").path), actual, -1, true)
                        assertTrue("Depth fighting at $degrees degrees: $mismatches pixels", mismatches < 500)
                    } finally { actual.dispose(); lowPrecision.dispose() }
                }
                File(activity.filesDir, "replay-depth-verification.txt").writeText(report.toString())
            } catch (error: Throwable) { failure.set(error) }
            finally { sharing.cancel(); export?.close(); reference?.dispose(); renderer.dispose(); done.countDown() }
        }
        assertTrue("Depth fixture timed out", done.await(15, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Replay depth fixture failed", it) }
    }
    private fun differentColor(a: Int, b: Int): Boolean {
        for (shift in 8..24 step 8) {
            if (kotlin.math.abs((a ushr shift and 255) - (b ushr shift and 255)) > 16) return true
        }
        return false
    }

    private fun capture(renderer: PoolRenderer, clip: ReplayClip, target: FrameBuffer, seconds: Float = 2f): Pixmap {
        target.begin()
        return try {
            renderer.renderReplay(clip, seconds, 720, 1280)
            Pixmap.createFromFrameBuffer(0, 0, 720, 1280)
        } finally { target.end() }
    }
}
