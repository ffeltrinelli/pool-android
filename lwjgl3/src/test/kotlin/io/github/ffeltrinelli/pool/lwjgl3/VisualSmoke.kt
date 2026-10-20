package io.github.ffeltrinelli.pool.lwjgl3

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.backends.lwjgl3.*
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.render.PoolRenderer
import java.io.File

/** Test-only fixtures render through the same renderer as Android; no production test switches. */
object VisualSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val report = VisualReport(File(args.single()))
        val suite = Suite(report)
        try {
            Lwjgl3Application(suite, Lwjgl3ApplicationConfiguration().apply {
                setTitle("Pool visual verification")
                setWindowedMode(540, 1200)
                setInitialVisible(false)
                disableAudio(true)
                useVsync(false)
                setForegroundFPS(60)
            })
        } catch (failure: Throwable) {
            report.record("graphics-startup", failure.stackTraceToString())
        } finally {
            report.write()
        }
        check(report.failures == 0) { "Visual smoke tests failed. See ${report.directory}/index.html" }
    }

    private class Suite(val report: VisualReport) : ApplicationAdapter() {
        private lateinit var renderer: PoolRenderer
        private lateinit var physics: PoolPhysics
        private lateinit var framebuffer: FrameBuffer
        private val session = GameSession()
        private val gesture = CueGesture()
        private val scenarios = listOf("initial-rack", "cue-and-aim", "cue-stroke-initial", "cue-stroke-withdrawn", "cue-stroke-return") + (1..6).map { "pocket-$it" } + listOf("scratch-result", "cleared-result") + listOf(0, 12, 18, 24, 48).map { "rolling-markings-$it" }
        private var index = 0
        override fun create() {
            renderer = PoolRenderer()
            renderer.resize(1080, 2400)
            physics = PoolPhysics(session::accept)
            framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 1080, 2400, true)
        }
        override fun render() {
            if (index >= scenarios.size) { Gdx.app.exit(); return }
            val name = scenarios[index++]
            var bound = false
            try {
                fixture(name)
                framebuffer.begin(); bound = true
                renderer.render(physics, session, gesture, 1080, 2400)
                capture(name)
                if (name.startsWith("rolling-markings-")) captureDetail(name)
                report.record(name, null)
            } catch (failure: Throwable) {
                if (bound) runCatching { capture("$name-failure") }
                report.record(name, failure.stackTraceToString())
            } finally {
                if (bound) framebuffer.end()
                report.write()
            }
        }
        private fun fixture(name: String) {
            physics.reset(); session.restart(); gesture.cancel()
            when {
                name == "initial-rack" -> {
                    check(physics.balls.size == 16 && session.canShoot)
                }
                name == "cue-and-aim" -> {
                    val cue = physics.balls.first { it.id == 0 }.point
                    gesture.begin(PointerSample(0, cue + Point(0.19f, 0.25f), 0.0), cue)
                    check(gesture.phase == CuePhase.AIMING)
                }
                name.startsWith("cue-stroke-") -> {
                    val cue = physics.balls.first { it.id == 0 }.point
                    val start = cue + Point(0.19f, 0.25f)
                    gesture.begin(PointerSample(0, start, 0.0), cue)
                    val direction = gesture.direction
                    if (name != "cue-stroke-initial") {
                        check(gesture.move(PointerSample(0, start - direction * 0.05f, 0.1)) == null)
                        check(gesture.move(PointerSample(0, start - direction * 0.10f, 0.2)) == null)
                        check(gesture.phase == CuePhase.STROKING)
                        if (name == "cue-stroke-return") {
                            check(gesture.move(PointerSample(0, start + direction * 0.02f, 0.4)) == null)
                        }
                    }
                    val expectedGap = when (name) {
                        "cue-stroke-withdrawn" -> 0.132f
                        "cue-stroke-return" -> 0.012f
                        else -> 0.032f
                    }
                    check(abs(gesture.tipGap - expectedGap) < 0.0001f)
                    check(gesture.direction == direction && session.canShoot)
                }
                name.startsWith("rolling-markings-") -> {
                    val steps = name.substringAfterLast('-').toInt()
                    physics.reset(listOf(1 to Point(-0.16f, 0.35f), 9 to Point(0.16f, 0.35f)))
                    val initial = physics.balls.first().rotation.cpy()
                    session.beginShot()
                    physics.balls.forEach { it.body.applyCentralImpulse(Vector3(0f, 0f, -0.136f)) }
                    repeat(steps) { physics.step() }
                    check(physics.balls.none { it.pocketed })
                    if (steps > 0) {
                        check(physics.balls.all { abs(it.rotation.dot(initial)) < 0.999f })
                    }
                    if (steps == 18) {
                        check(physics.balls.all { abs(Vector3(0f, 0f, 1f).mul(it.rotation).y) < 0.4f })
                    }
                }
                name.startsWith("pocket-") -> {
                    val pocket = TableGeometry.pockets[name.substringAfter('-').toInt() - 1]
                    val direction = pocket.normalized()
                    physics.reset(listOf(0 to Point(0f, 0.58f), 1 to (pocket - direction * 0.11f)))
                    // A fixed 120-step pre-roll makes this independent of rendering frame rate.
                    repeat(120) { physics.step() }
                    check(physics.balls.none { it.pocketed })
                }
                name == "scratch-result" -> {
                    physics.reset(TableGeometry.rack().filter { it.first != 0 })
                    session.beginShot()
                    session.accept(PhysicsEvent.Pocketed(0))
                    session.accept(PhysicsEvent.Settled)
                    check(session.state == RoundState.SCRATCHED)
                }
                name == "cleared-result" -> {
                    physics.reset(listOf(0 to Point(0f, 0.58f)))
                    session.beginShot()
                    (1..15).forEach { session.accept(PhysicsEvent.Pocketed(it)) }
                    session.accept(PhysicsEvent.Settled)
                    check(session.state == RoundState.CLEARED)
                }
            }
        }
        private fun captureDetail(name: String) {
            // A crop of the production framebuffer makes the surface markings easy to inspect.
            val pixels = Pixmap.createFromFrameBuffer(390, 900, 300, 600)
            try { PixmapIO.writePNG(FileHandle(File(report.directory, "$name-detail.png")), pixels, -1, true) }
            finally { pixels.dispose() }
        }
        private fun capture(name: String) {
            val pixels = Pixmap.createFromFrameBuffer(0, 0, 1080, 2400)
            try { PixmapIO.writePNG(FileHandle(File(report.directory, "$name.png")), pixels, -1, true) }
            finally { pixels.dispose() }
        }
        override fun dispose() {
            if (::framebuffer.isInitialized) framebuffer.dispose()
            if (::renderer.isInitialized) renderer.dispose()
            if (::physics.isInitialized) physics.dispose()
        }
    }
}

private class VisualReport(val directory: File) {
    private val results = linkedMapOf<String, String?>()
    val failures: Int get() = results.values.count { it != null }
    init { directory.mkdirs(); directory.listFiles()?.filter { it.extension == "png" }?.forEach { it.delete() } }
    fun record(name: String, failure: String?) { results[name] = failure }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    fun write() {
        File(directory, "index.html").writeText("""
            <!doctype html><html lang="en"><meta charset="utf-8"><title>Pool visual test report</title>
            <style>body{background:#10252c;color:#eee;font:16px system-ui;max-width:1100px;margin:40px auto;padding:20px}section{display:inline-block;vertical-align:top;width:320px;margin:12px}img{width:100%;border-radius:12px}pre{white-space:pre-wrap;color:#ffb4a5}a{color:#c6e6d6}</style>
            <h1>Pool visual verification</h1><p>1080 × 2400 · LWJGL3 / OpenGL · ${escape(System.getProperty("os.name"))} ${escape(System.getProperty("os.arch"))}</p>
            <p>${results.size - failures} passed · $failures failed. Screenshots require human visual review.</p>
            <p><a href="../../../core/build/reports/tests/test/index.html">JUnit and native Bullet results (local checkout)</a></p>
            ${results.entries.joinToString("\n") { (name, failure) -> "<section><h2>${escape(name)}</h2><p>${if (failure == null) "PASS" else "FAIL"}</p>" +
                (if (File(directory, "$name.png").exists()) "<a href=\"$name.png\"><img src=\"$name.png\" alt=\"${escape(name)}\"></a>" else "") +
                (if (File(directory, "$name-detail.png").exists()) "<p><a href=\"$name-detail.png\">Surface detail crop</a></p>" else "") +
                (if (failure != null) "<pre>${escape(failure)}</pre>" else "") +
                (if (File(directory, "$name-failure.png").exists()) "<a href=\"$name-failure.png\">Last frame at failure</a>" else "") + "</section>" }}
            </html>
        """.trimIndent())
    }
}
