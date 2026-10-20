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
import io.github.ffeltrinelli.pool.view.ViewMode

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
        private val viewScenarios = listOf("settings-top", "player-initial", "player-right", "player-left", "player-reverse",
            "player-withdrawn", "player-return", "settings-player", "player-shot-pullback", "player-shot-overview", "player-return-to-aim", "player-scratch", "player-cleared") +
            (1..6).map { "player-rail-$it" }
        private val pocketScenarios = listOf("top", "player").flatMap { mode ->
            listOf("corner", "side").flatMap { type -> listOf(0, 12, 24, 36, 60).map { "$mode-drop-$type-$it" } +
                listOf("$mode-shelf-$type", "$mode-jaw-$type") }
        }
        private val allScenarios get() = scenarios + viewScenarios + pocketScenarios
        private var index = 0
        override fun create() {
            renderer = PoolRenderer()
            renderer.resize(1080, 2400)
            physics = PoolPhysics(session::accept)
            framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 1080, 2400, true)
        }
        override fun render() {
            if (index >= allScenarios.size) {
                if (System.getenv("POOL_RECORD_POCKETS") == "1") {
                    try { recordPocketMotion() }
                    catch (failure: Throwable) { report.record("pocket-motion-recording", failure.stackTraceToString()) }
                }
                if (System.getenv("POOL_RECORD_PLAYER_VIEW") == "1") {
                    try { recordPlayerMotion() }
                    catch (failure: Throwable) { report.record("player-motion-recording", failure.stackTraceToString()) }
                }
                Gdx.app.exit()
                return
            }
            val name = allScenarios[index++]
            var bound = false
            try {
                fixture(name)
                framebuffer.begin(); bound = true
                renderer.render(physics, session, gesture, 1080, 2400)
                capture(name)
                if (name.startsWith("rolling-markings-")) captureDetail(name)
                if (name in pocketScenarios) capturePocketDetail(name)
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
            renderer.views.select(ViewMode.TOP)
            renderer.views.reset()
            renderer.settingsOpen = false
            if (name in viewScenarios) { viewFixture(name); return }
            if (name in pocketScenarios) { pocketFixture(name); return }
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
                        "cue-stroke-withdrawn" -> 0.150f
                        "cue-stroke-return" -> 0.030f
                        else -> 0.050f
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
        private fun pocketFixture(name: String) {
            val pocket = if ("corner" in name) PocketGeometry.pockets[3] else PocketGeometry.pockets[5]
            val cue = pocket.mouthCenter - pocket.outward * 0.24f
            val start = when {
                "shelf" in name -> pocket.mouthCenter - pocket.outward * 0.004f
                "jaw" in name -> pocket.mouth(1f) + pocket.lateral * 0.018f - pocket.outward * 0.12f
                else -> pocket.mouthCenter - pocket.outward * 0.06f
            }
            physics.reset(listOf(0 to cue, 1 to start))
            renderer.views.select(if (name.startsWith("player")) ViewMode.PLAYER else ViewMode.TOP)
            gesture.begin(PointerSample(0, cue - pocket.outward * 0.3f, 0.0), cue)
            renderer.views.update(cue, pocket.outward, true)
            session.beginShot()
            if ("drop" in name || "jaw" in name) {
                val v = pocket.outward * 0.7f
                val ball = physics.balls[1]
                ball.body.linearVelocity = Vector3(v.x, 0f, v.z)
                ball.body.angularVelocity = Vector3(v.z / TableGeometry.BALL_RADIUS, 0f, -v.x / TableGeometry.BALL_RADIUS)
            }
            val steps = if ("drop" in name) name.substringAfterLast('-').toInt() else if ("jaw" in name) 18 else 120
            repeat(steps) { physics.step() }
            val ball = physics.balls[1]
            if ("shelf" in name) check(!ball.pocketed && abs(ball.position.y - TableGeometry.BALL_RADIUS) < 0.001f)
            if ("drop" in name && steps == 60) check(ball.pocketed && ball.position.y < PocketGeometry.CAPTURE_DEPTH)
            if ("drop" in name && steps == 0) check(!ball.pocketed && ball.position.y > 0f)
            if ("drop" in name && steps == 24) check(!ball.pocketed && ball.position.y < TableGeometry.BALL_RADIUS)
            check(abs(ball.position.x) < 0.8f && abs(ball.position.z) < 1.36f)
        }

        private fun capturePocketDetail(name: String) {
            val pocket = if ("corner" in name) PocketGeometry.pockets[3] else PocketGeometry.pockets[5]
            val projected = renderer.views.active.camera.project(Vector3(pocket.center.x, 0f, pocket.center.z), 0f, 0f, 1080f, 2400f)
            val x = (projected.x.toInt() - 210).coerceIn(0, 660)
            val y = (projected.y.toInt() - 210).coerceIn(0, 1980)
            val pixels = Pixmap.createFromFrameBuffer(x, y, 420, 420)
            try { PixmapIO.writePNG(FileHandle(File(report.directory, "$name-detail.png")), pixels, -1, true) }
            finally { pixels.dispose() }
        }

        private fun viewFixture(name: String) {
            if (name == "settings-top") { renderer.settingsOpen = true; return }
            renderer.views.select(ViewMode.PLAYER)
            val cue = if (name.startsWith("player-rail-")) {
                val pocket = TableGeometry.pockets[name.substringAfterLast('-').toInt() - 1]
                pocket * 0.85f
            } else Point(0f, 0.58f)
            if (name.startsWith("player-rail-")) physics.reset(listOf(0 to cue, 1 to Point(0f, 0f)))
            val direction = when (name) {
                "player-right" -> Point(1f, 0f)
                "player-left" -> Point(-1f, 0f)
                "player-reverse" -> Point(0f, 1f)
                else -> if (name.startsWith("player-rail-")) (Point(0f, 0f) - cue).normalized() else Point(0f, -1f)
            }
            gesture.begin(PointerSample(0, cue - direction * 0.30f, 0.0), cue)
            val view = renderer.views.active
            val start = view.beginInput(540, 1500, cue, direction)
            gesture.cancel()
            gesture.begin(PointerSample(0, start, 0.0), cue)
            if (name == "player-withdrawn" || name == "player-return") {
                gesture.move(PointerSample(0, view.moveInput(540, 1520, gesture.phase, gesture.direction), 0.05))
                gesture.move(PointerSample(0, view.moveInput(540, 1556, gesture.phase, gesture.direction), 0.1))
                if (name == "player-return") gesture.move(PointerSample(0, view.moveInput(540, 1489, gesture.phase, gesture.direction), 0.2))
                check(gesture.phase == CuePhase.STROKING && session.canShoot)
                check(abs(gesture.direction.x - direction.x) < 0.0001f)
                val expected = if (name == "player-withdrawn") 0.050f + 56 * 1.92f / 1080 else 0.050f - 11 * 1.92f / 1080
                check(abs(gesture.tipGap - expected) < 0.0001f)
            }
            renderer.views.update(cue, direction, true)
            if (name == "settings-player") renderer.settingsOpen = true
            if (name.startsWith("player-shot-") || name == "player-return-to-aim") {
                session.beginShot()
                physics.strike(ShotCommand(direction, 0.4f))
                repeat(120) { physics.step() }
                val duration = if (name == "player-shot-pullback") 0.325f else 0.65f
                renderer.views.update(physics.balls.first { it.id == 0 }.point, direction, false, duration)
                check(view.camera.position.y > 1f)
                if (name == "player-return-to-aim") {
                    repeat(6000) { if (!session.canShoot) physics.step() }
                    check(session.canShoot)
                    renderer.views.update(physics.balls.first { it.id == 0 }.point, direction, true, 0.225f)
                }
            }
            if (name == "player-scratch" || name == "player-cleared") {
                session.beginShot()
                if (name == "player-scratch") session.accept(PhysicsEvent.Pocketed(0))
                else (1..15).forEach { session.accept(PhysicsEvent.Pocketed(it)) }
                session.accept(PhysicsEvent.Settled)
                if (name == "player-scratch") physics.reset(TableGeometry.rack().filter { it.first != 0 })
                else physics.reset(listOf(0 to cue))
                check(session.finished)
            }
        }
        /** Opt-in production-renderer frames for a short PR recording; never enables gameplay debug input. */
        private fun recordPlayerMotion() {
            val directory = File(report.directory, "player-motion-frames").apply { mkdirs() }
            fixture("player-initial")
            val view = renderer.views.active
            val cue = physics.balls.first { it.id == 0 }.point
            gesture.cancel()
            gesture.begin(PointerSample(0, view.beginInput(540, 1500, cue, Point(0f, -1f)), 0.0), cue)
            for (frame in 0 until 150) {
                if (session.canShoot && frame > 0) {
                    val x = if (frame < 40) 540 + (60 * kotlin.math.sin(Math.PI * frame / 40)).toInt() else 540
                    val y = when {
                        frame <= 40 -> 1500
                        frame <= 60 -> 1500 + (frame - 40) * 5
                        else -> 1600 - (frame - 60) * 12
                    }
                    val point = view.moveInput(x, y, gesture.phase, gesture.direction)
                    gesture.move(PointerSample(0, point, frame / 30.0))?.let {
                        renderer.views.update(cue, it.direction, true)
                        session.beginShot()
                        physics.strike(it)
                    }
                }
                if (!session.canShoot) repeat(4) { physics.step() }
                framebuffer.begin()
                try {
                    renderer.render(physics, session, gesture, 1080, 2400, viewDelta = 1f / 30)
                    val pixels = Pixmap.createFromFrameBuffer(0, 0, 1080, 2400)
                    try { PixmapIO.writePNG(FileHandle(File(directory, "frame-%03d.png".format(frame))), pixels, -1, true) }
                    finally { pixels.dispose() }
                } finally { framebuffer.end() }
            }
        }
        private fun recordPocketMotion() {
            val directory = File(report.directory, "pocket-motion-frames").apply { mkdirs() }
            for (frame in 0 until 120) {
                val mode = if (frame < 60) "top" else "player"
                if (frame % 60 == 0) fixture("$mode-drop-corner-0")
                if (frame % 60 > 0) repeat(2) { physics.step() }
                framebuffer.begin()
                try {
                    renderer.render(physics, session, gesture, 1080, 2400)
                    val pixels = Pixmap.createFromFrameBuffer(0, 0, 1080, 2400)
                    try { PixmapIO.writePNG(FileHandle(File(directory, "frame-%03d.png".format(frame))), pixels, -1, true) }
                    finally { pixels.dispose() }
                } finally { framebuffer.end() }
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
                (if (File(directory, "$name-detail.png").exists()) "<p><a href=\"$name-detail.png\">Detail crop</a></p>" else "") +
                (if (failure != null) "<pre>${escape(failure)}</pre>" else "") +
                (if (File(directory, "$name-failure.png").exists()) "<a href=\"$name-failure.png\">Last frame at failure</a>" else "") + "</section>" }}
            </html>
        """.trimIndent())
    }
}
