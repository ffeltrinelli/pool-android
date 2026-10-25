package io.github.ffeltrinelli.pool.lwjgl3

import com.badlogic.gdx.*
import io.github.ffeltrinelli.pool.replay.*
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.backends.lwjgl3.*
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.render.TablePresentation
import io.github.ffeltrinelli.pool.ui.HudPresentation
import io.github.ffeltrinelli.pool.ui.UiFonts
import io.github.ffeltrinelli.pool.ui.TextStyle
import java.io.File
import io.github.ffeltrinelli.pool.view.TopView
import io.github.ffeltrinelli.pool.view.PlayerView
import io.github.ffeltrinelli.pool.startup.SplashScreen

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
        private var session = GameSession()
        private val gesture = CueGesture()
        private var tablePresentation = TablePresentation()
        private var hudPresentation = HudPresentation()
        private var index = 0
        override fun create() {
            renderer = PoolRenderer()
            renderer.resize(1080, 2400)
            physics = PoolPhysics({ session.accept(it) })
            framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 1080, 2400, true)
        }
        override fun render() {
            if (index >= VisualScenarios.all.size) {
                if (System.getenv("POOL_RECORD_POCKETS") == "1") {
                    try { recordPocketMotion() }
                    catch (failure: Throwable) { report.record("pocket-motion-recording", failure.stackTraceToString()) }
                }
                if (System.getenv("POOL_RECORD_PLAYER_VIEW") == "1") {
                    try { recordPlayerMotion() }
                    catch (failure: Throwable) { report.record("player-motion-recording", failure.stackTraceToString()) }
                }
                if (System.getenv("POOL_RECORD_COMPUTER") == "1") {
                    try { recordComputerMotion() }
                    catch (failure: Throwable) { report.record("computer-motion-recording", failure.stackTraceToString()) }
                }
                Gdx.app.exit()
                return
            }
            val scenario = VisualScenarios.all[index++]
            val name = scenario.name
            var bound = false
            try {
                fixture(name)
                framebuffer.begin(); bound = true
                if (name.startsWith("replay-")) {
                    renderReplayFixture(name)
                } else if (name == "gallery-splash") {
                    verifyTypography()
                    val splash = SplashScreen()
                    try { splash.resize(1080, 2400); splash.render(1080) }
                    finally { splash.dispose() }
                } else {
                    renderer.render(physics, session, gesture, 1080, 2400, table = tablePresentation, hudState = hudPresentation)
                }
                if (name != "gallery-splash" && !name.startsWith("replay-")) {
                    val overhead = session.placement != null || session.player == Player.COMPUTER
                    check(if (overhead) renderer.views.active is TopView else renderer.views.active is PlayerView)
                }
                capture(name)
                if (VisualScenarios.named(name).group == ScenarioGroup.CUE_VISIBILITY) verifyCueVisibility(name)
                if (name.startsWith("rolling-markings-")) captureDetail(name)
                if (VisualScenarios.named(name).group == ScenarioGroup.POCKET) capturePocketDetail(name)
                report.record(name, null)
            } catch (failure: Throwable) {
                if (bound) runCatching { capture("$name-failure") }
                report.record(name, failure.stackTraceToString())
            } finally {
                if (bound) framebuffer.end()
                report.write()
            }
        }
        private fun renderReplayFixture(name: String) {
            val direction = if (name == "replay-diagonal" || name == "replay-export") Point(1f, -1f).normalized() else Point(0f, -1f)
            val balls = physics.replayBalls()
            fun frame(time: Float, gap: Float?, z: Float = TableGeometry.CUE_START.z) = ReplayFrame(time,
                balls.map { if (it.id == 0) it.copy(z = z) else it }, gap?.let { CuePose(direction, it) })
            val clip = ReplayClip(listOf(frame(0f, 0.05f), frame(0.5f, 0.12f), frame(1f, 0f),
                frame(1.3f, null, 0.1f), frame(2f, null, 0.1f), frame(3f, null, 0.1f)), emptyList(), 1f, 1.3f, 3f)
            val time = when (name.removePrefix("replay-")) {
                "close" -> 0f
                "withdrawn" -> 0.5f
                "tracking" -> 1.15f
                "transition" -> 1.625f
                "overview", "diagonal" -> 2f
                else -> 3f
            }
            renderer.renderReplay(clip, time, 1080, 2400)
            if (name != "replay-export") {
                val hud = ReplayHud()
                try { hud.render(1080, 2400, true, ShareState.Idle, 0f, if (name == "replay-hold") 5 else null) } finally { hud.close() }
            }
        }

        private fun verifyTypography() {
            val fonts = UiFonts(TextStyle.BODY, TextStyle.SPLASH_TITLE)
            try {
                fonts.resize(420)
                val original = fonts[TextStyle.BODY]
                val logicalHeight = original.data.getGlyph('H').height.toFloat()
                fonts.resize(420)
                check(fonts[TextStyle.BODY] === original) { "Unchanged render width must reuse fonts" }
                for (width in listOf(720, 1080, 1440)) {
                    val oldTexture = fonts[TextStyle.BODY].region.texture
                    fonts.resize(width)
                    check(oldTexture.textureObjectHandle == 0) { "Resize leaked the previous atlas" }
                    for (style in listOf(TextStyle.BODY, TextStyle.SPLASH_TITLE)) {
                        val font = fonts[style]
                        check(abs(font.data.scaleX - 420f / width) < 0.0001f)
                        check(font.region.texture.magFilter == com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)
                    }
                    val body = fonts[TextStyle.BODY]
                    val height = body.data.getGlyph('H').height
                    check(height > logicalHeight) { "High-density glyphs must be rasterized larger" }
                    check(abs(height * body.data.scaleY - logicalHeight) <= 1.5f) {
                        "Font resize changed the logical text size at $width pixels"
                    }
                }
            } finally {
                val texture = fonts[TextStyle.BODY].region.texture
                fonts.dispose()
                check(texture.textureObjectHandle == 0) { "Disposal leaked the font atlas" }
            }
        }

        private fun fixture(name: String) {
            resetFixture()
            when (VisualScenarios.named(name).group) {
                ScenarioGroup.CUE_VISIBILITY -> cueVisibilityFixture(name)
                ScenarioGroup.DIFFICULTY -> difficultyFixture(name)
                ScenarioGroup.COMPUTER -> computerFixture(name)
                ScenarioGroup.AID -> aidFixture(name)
                ScenarioGroup.VIEW -> viewFixture(name)
                ScenarioGroup.POCKET -> pocketFixture(name)
                ScenarioGroup.TABLE -> tableFixture(name)
                ScenarioGroup.RULES -> rulesFixture(name)
                ScenarioGroup.GALLERY -> galleryFixture(name)
                ScenarioGroup.REPLAY -> Unit
            }
            if (session.placement != null && session.player == Player.HUMAN && tablePresentation.placement == null)
                tablePresentation = tablePresentation.copy(humanCanAim = false, placement = TableGeometry.CUE_START,
                    placementRegion = session.placement)
        }

        private fun galleryFixture(name: String) {
            session = GameSession()
            if (name == "gallery-placement" || name == "gallery-splash") return
            session.confirmPlacement()
            gesture.resetForRack(TableGeometry.CUE_START, Point(0f, TableGeometry.FOOT_SPOT))
            if (name == "gallery-top") session = readyMatch(Player.COMPUTER)
            if (name == "gallery-settings") hudPresentation = hudPresentation.copy(settingsOpen = true)
            if (name == "gallery-help") hudPresentation = hudPresentation.copy(settingsOpen = true, helpOpen = true)
            if (name == "gallery-result") {
                finishMatch(Player.HUMAN)
                physics.reset(listOf(0 to Point(0f, 0.58f)) + (9..15).map { id ->
                    id to Point(if (id % 2 == 0) -0.3f else 0.3f, (id - 12) * 0.25f)
                })
                check(session.finished && physics.balls.none { it.id in 1..8 })
            }
            if (name == "gallery-player") {
                check(session.breaking && session.canShoot && physics.balls.size == 16)
                check(gesture.direction == (Point(0f, TableGeometry.FOOT_SPOT) - TableGeometry.CUE_START).normalized())
                check(gesture.tipGap == 0.05f)
            }
        }

        private fun readyMatch(player: Player = Player.HUMAN): GameSession {
            val match = GameSession()
            match.confirmPlacement()
            match.beginShot()
            match.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
            match.accept(PhysicsEvent.Settled)
            if (player == Player.HUMAN) {
                match.beginShot()
                match.accept(PhysicsEvent.Contacts(setOf(1), setOf(1)))
                match.accept(PhysicsEvent.Settled)
            }
            return match
        }

        private fun finishMatch(winner: Player) {
            session = readyMatch(winner)
            session.beginShot()
            session.accept(PhysicsEvent.Contacts(setOf(1), emptySet()))
            (1..7).forEach { session.accept(PhysicsEvent.Pocketed(it)) }
            session.accept(PhysicsEvent.Settled)
            session.beginShot()
            session.accept(PhysicsEvent.Contacts(setOf(8), emptySet()))
            session.accept(PhysicsEvent.Pocketed(8))
            session.accept(PhysicsEvent.Settled)
        }

        private fun resetFixture() {
            physics.reset(TableGeometry.rack(42))
            session = readyMatch()
            gesture.resetForRack(Point(0f, 0.58f), Point(0f, TableGeometry.FOOT_SPOT))
            renderer.views.reset()
            renderer.views.follow(session)
            tablePresentation = TablePresentation()
            hudPresentation = HudPresentation()
        }

        private fun difficultyFixture(name: String) {
            if (name.startsWith("top")) session = readyMatch(Player.COMPUTER)
            hudPresentation = hudPresentation.copy(
                difficulty = OpponentDifficulty.entries.single { name.endsWith(it.name.lowercase()) },
                settingsOpen = true
            )
            check(renderer.hud.layout.restart.y == renderer.hud.layout.exit.y)
        }

        private fun rulesFixture(name: String) {
            when {
                "placement" in name -> {
                    session = GameSession()
                    if ("anywhere" in name) {
                        session.confirmPlacement(); session.beginShot()
                        session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
                        session.accept(PhysicsEvent.Settled)
                        session.beginShot(); session.accept(PhysicsEvent.Pocketed(0)); session.accept(PhysicsEvent.Settled)
                    }
                    val point = if ("invalid" in name) physics.balls.first { it.id == 8 }.point else TableGeometry.CUE_START
                    tablePresentation = tablePresentation.copy(humanCanAim = false, placement = point,
                        placementRegion = session.placement, placementValid = "invalid" !in name, activeSeconds = 1f)
                    hudPresentation = hudPresentation.copy(placementError = "Too close to another ball".takeIf { "invalid" in name })
                    check(session.placement != null && session.player == Player.HUMAN)
                }
                "assigned" in name || "on-eight" in name -> {
                    session.beginShot()
                    session.accept(PhysicsEvent.Contacts(setOf(1), emptySet()))
                    val pots = if ("on-eight" in name) (1..7).toList() else listOf(1)
                    pots.forEach { session.accept(PhysicsEvent.Pocketed(it)) }
                    session.accept(PhysicsEvent.Settled)
                    physics.reset(physics.snapshot().balls.filter { it.id !in pots }.map { it.id to it.point })
                    check(session.group(Player.HUMAN) != null)
                }
                "wrong-first" in name -> {
                    hudPresentation = hudPresentation.copy(notice = "You: Foul - wrong ball hit first")
                }
                "mixed" in name -> {
                    session.beginShot(); session.accept(PhysicsEvent.Contacts(setOf(1), emptySet()))
                    listOf(1, 9).forEach { session.accept(PhysicsEvent.Pocketed(it)) }
                    session.accept(PhysicsEvent.Settled)
                    hudPresentation = hudPresentation.copy(notice = "Solid and stripe pocketed - table stays open")
                    check(session.group(Player.HUMAN) == null && session.humanTurn)
                }
            }
        }

        private fun tableFixture(name: String) {
            when {
                name == "initial-rack" -> {
                    session = GameSession()
                    check(physics.balls.size == 16 && session.state == RoundState.PLACING)
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
                    session = readyMatch(Player.COMPUTER)
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
                name == "scratch-foul" -> {
                    physics.reset(TableGeometry.rack(42))
                    session.beginShot()
                    session.accept(PhysicsEvent.Pocketed(0))
                    session.accept(PhysicsEvent.Settled)
                    check(!session.finished)
                    hudPresentation = hudPresentation.copy(foulPlayer = Player.HUMAN, notice = "You: Foul - cue ball pocketed")
                    tablePresentation = tablePresentation.copy(humanCanAim = false)
                }
                name == "cleared-result" -> {
                    physics.reset(listOf(0 to Point(0f, 0.58f)))
                    finishMatch(Player.HUMAN)
                    check(session.state == RoundState.FINISHED)
                }
            }
        }
        private fun cueVisibilityFixture(name: String) {
            val cue = if (name.endsWith("ball-overlap")) Point(0f, 0.4f) else Point(0.49f, 0.3f)
            val direction = if (name.endsWith("ball-overlap")) Point(0f, -1f) else Point(-1f, 0f)
            val objectBall = if (name.endsWith("ball-overlap")) cue - direction * 0.2f else Point(0f, 0f)
            physics.reset(listOf(0 to cue, 1 to objectBall))
            val start = cue - direction * 0.3f
            gesture.begin(PointerSample(0, start, 0.0), cue)
            if (name.endsWith("withdrawn") || name.endsWith("return")) {
                gesture.move(PointerSample(0, start - direction * 0.02f, 0.05))
                gesture.move(PointerSample(0, start - direction * 0.10f, 0.1))
                if (name.endsWith("return")) gesture.move(PointerSample(0, start + direction * 0.02f, 0.2))
                check(gesture.phase == CuePhase.STROKING)
                val expectedGap = if (name.endsWith("withdrawn")) 0.15f else 0.03f
                check(abs(gesture.tipGap - expectedGap) < 0.0001f)
            }
            if (name.startsWith("top")) {
                session = readyMatch(Player.COMPUTER)
                tablePresentation = tablePresentation.copy(humanCanAim = false,
                    computerPose = CuePose(direction, gesture.tipGap))
            }
            hudPresentation = hudPresentation.copy(settingsOpen = name.endsWith("settings"))
            renderer.views.follow(session)
            renderer.views.update(cue, direction, true)
            check(gesture.direction == direction && session.canShoot)
        }

        private fun verifyCueVisibility(name: String) {
            val cue = physics.balls.first { it.id == 0 }.point
            val distance = when {
                name.endsWith("withdrawn") -> 0.21f
                name.endsWith("ball-overlap") -> 0.2f
                else -> 0.16f
            }
            val probe = cue - gesture.direction * distance
            val screen = renderer.views.active.camera.project(
                Vector3(probe.x, TableGeometry.BALL_RADIUS, probe.z), 0f, 0f, 1080f, renderer.hud.layout.gameHeight.toFloat())
            check(screen.x in 6f..1073f && screen.y in 6f..2393f)
            val withCue = Pixmap.createFromFrameBuffer(screen.x.toInt() - 5, screen.y.toInt() - 5, 11, 11)
            val humanCanAim = tablePresentation.humanCanAim
            val computerPose = tablePresentation.computerPose
            try {
                tablePresentation = tablePresentation.copy(humanCanAim = false)
                tablePresentation = tablePresentation.copy(computerPose = null)
                renderer.render(physics, session, gesture, 1080, 2400, table = tablePresentation, hudState = hudPresentation)
                val withoutCue = Pixmap.createFromFrameBuffer(screen.x.toInt() - 5, screen.y.toInt() - 5, 11, 11)
                try {
                    val changed = (0 until 11).sumOf { x ->
                        (0 until 11).count { y -> withCue.getPixel(x, y) != withoutCue.getPixel(x, y) }
                    }
                    if (name.endsWith("settings")) check(changed == 0) { "Settings must cover the cue ($changed changed pixels)" }
                    else check(changed >= 5) { "Cue hidden at obstacle ($changed changed pixels)" }
                } finally { withoutCue.dispose() }
            } finally {
                withCue.dispose()
                tablePresentation = tablePresentation.copy(humanCanAim = humanCanAim)
                tablePresentation = tablePresentation.copy(computerPose = computerPose)
                renderer.render(physics, session, gesture, 1080, 2400, table = tablePresentation, hudState = hudPresentation)
            }
        }

        private fun computerFixture(name: String) {
            session = readyMatch()
            renderer.views.update(Point(0f, 0.58f), gesture.direction, true)
            fun shot(ids: List<Int>) {
                if (session.placement != null) session.confirmPlacement()
                session.beginShot()
                session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1)))
                ids.forEach { session.accept(PhysicsEvent.Pocketed(it)) }
                session.accept(PhysicsEvent.Settled)
            }
            when {
                name.endsWith("human-win") -> finishMatch(Player.HUMAN)
                name.endsWith("computer-win") -> finishMatch(Player.COMPUTER)
                name.endsWith("foul-human") -> { shot(listOf(0)); hudPresentation = hudPresentation.copy(foulPlayer = Player.HUMAN, notice = "You: Foul - cue ball pocketed") }
                name.endsWith("foul-computer") -> { shot(emptyList()); shot(listOf(0)); hudPresentation = hudPresentation.copy(foulPlayer = Player.COMPUTER, notice = "Computer: Foul - cue ball pocketed") }
                !name.endsWith("human") && !name.endsWith("settings") -> shot(emptyList())
            }
            if (session.finished) physics.reset(listOf(0 to Point(0f, 0.58f)))
            tablePresentation = tablePresentation.copy(humanCanAim = session.humanTurn && hudPresentation.foulPlayer == null)
            tablePresentation = tablePresentation.copy(holdOverview = !tablePresentation.humanCanAim)
            hudPresentation = hudPresentation.copy(computerThinking = name.endsWith("thinking"))
            hudPresentation = hudPresentation.copy(settingsOpen = name.endsWith("settings"))
            if ("cue-" in name) {
                val stroke = ComputerStroke(ShotCommand(Point(0.4f, -1f).normalized(), 0.35f))
                val elapsed = when {
                    name.endsWith("withdrawn") -> 0.6f
                    name.endsWith("contact") -> 0.799f
                    else -> 0f
                }
                check(stroke.advance(elapsed) == null)
                tablePresentation = tablePresentation.copy(computerPose = stroke.pose)
                check(stroke.pose.direction == Point(0.4f, -1f).normalized())
                val expected = if (name.endsWith("withdrawn")) 0.12f else if (name.endsWith("contact")) 0f else 0.05f
                check(abs(stroke.pose.tipGap - expected) < 0.0001f)
            }
        }

        private fun recordComputerMotion() {
            fixture("computer-cue-aim")
            val stroke = ComputerStroke(ShotCommand(Point(0f, -1f), 0.65f))
            val directory = File(report.directory, "computer-motion").apply { mkdirs() }
            for (frame in 0 until 150) {
                val shot = stroke.advance(1f / 60f)
                if (shot != null) { session.beginShot(); physics.strike(shot) }
                tablePresentation = tablePresentation.copy(computerPose = if (session.canShoot) stroke.pose else null)
                if (!session.canShoot) physics.advance(1f / 60f)
                framebuffer.begin()
                try {
                    renderer.render(physics, session, gesture, 1080, 2400, table = tablePresentation, hudState = hudPresentation)
                    val pixels = Pixmap.createFromFrameBuffer(0, 0, 1080, 2400)
                    try { PixmapIO.writePNG(FileHandle(File(directory, "frame-%03d.png".format(frame))), pixels, -1, true) }
                    finally { pixels.dispose() }
                } finally { framebuffer.end() }
            }
        }

        private fun pocketFixture(name: String) {
            session = readyMatch(if (name.startsWith("top")) Player.COMPUTER else Player.HUMAN)
            val pocket = if ("corner" in name) PocketGeometry.pockets[3] else PocketGeometry.pockets[5]
            val cue = pocket.mouthCenter - pocket.outward * 0.24f
            val start = when {
                "shelf" in name -> pocket.mouthCenter - pocket.outward * 0.004f
                "jaw" in name -> pocket.mouth(1f) + pocket.lateral * 0.018f - pocket.outward * 0.12f
                else -> pocket.mouthCenter - pocket.outward * 0.06f
            }
            physics.reset(listOf(0 to cue, 1 to start))
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
            val projected = renderer.views.active.camera.project(Vector3(pocket.center.x, 0f, pocket.center.z), 0f, 0f, 1080f, renderer.hud.layout.gameHeight.toFloat())
            val x = (projected.x.toInt() - 210).coerceIn(0, 660)
            val y = (projected.y.toInt() - 210).coerceIn(0, 1980)
            val pixels = Pixmap.createFromFrameBuffer(x, y, 420, 420)
            try { PixmapIO.writePNG(FileHandle(File(report.directory, "$name-detail.png")), pixels, -1, true) }
            finally { pixels.dispose() }
        }

        private fun aidFixture(name: String) {
            tablePresentation = tablePresentation.copy(aidedAim = !name.endsWith("disabled") && !name.endsWith("settings-off"))
            hudPresentation = hudPresentation.copy(settingsOpen = name.endsWith("settings-off"))
            val cue = Point(0f, 0.58f)
            val radius = TableGeometry.BALL_RADIUS
            var direction = Point(0f, -1f)
            when {
                name.endsWith("glancing") -> physics.reset(listOf(0 to cue, 1 to Point(radius, 0f)))
                name.endsWith("rail") -> physics.reset(listOf(0 to cue))
                name.endsWith("pocket") -> {
                    val pocket = PocketGeometry.pockets.last()
                    direction = (pocket.mouthCenter - cue).normalized()
                    physics.reset(listOf(0 to cue))
                }
                name.endsWith("blocked") -> physics.reset(listOf(0 to cue, 1 to Point(0f, -1.3f)))
                name.endsWith("reset") -> {
                    gesture.begin(PointerSample(0, cue + Point(0.3f, 0f), 0.0), cue)
                    renderer.views.update(cue, gesture.direction, false, 0.65f)
                    gesture.resetForRack(cue, Point(0f, TableGeometry.FOOT_SPOT))
                    renderer.views.reset()
                    check(gesture.direction == direction && gesture.tipGap == 0.05f)
                }
            }
            if (!name.endsWith("reset")) gesture.begin(PointerSample(0, cue - direction * 0.3f, 0.0), cue)
            renderer.views.follow(session)
            renderer.views.update(cue, gesture.direction, true)
            val path = AimPredictor().predict(cue, direction, physics.balls.filter { !it.pocketed }.map { AimBall(it.id, it.point) })
            if (name.endsWith("enabled") || name.endsWith("glancing") || name.endsWith("reset")) check(path.targetBallId == 1)
            if (name.endsWith("rail") || name.endsWith("blocked") || name.endsWith("pocket")) check(path.targetBallId == null)
            if (name.endsWith("rail") || name.endsWith("blocked")) check(abs(path.end.z + TableGeometry.HALF_LENGTH - radius) < 0.0001f)
            check(abs(renderer.views.active.camera.direction.x - direction.x * 0.70710677f) < 0.0001f)
        }

        private fun viewFixture(name: String) {
            if (name == "settings-top") { session = readyMatch(Player.COMPUTER); hudPresentation = hudPresentation.copy(settingsOpen = true); return }
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
            if (name == "settings-player") hudPresentation = hudPresentation.copy(settingsOpen = true)
            if (name.startsWith("player-shot-") || name == "player-return-to-aim") {
                session.beginShot()
                physics.strike(ShotCommand(direction, 0.4f))
                repeat(120) { physics.step() }
                val duration = if (name == "player-shot-pullback") 0.325f else 0.65f
                renderer.views.update(physics.balls.first { it.id == 0 }.point, direction, false, duration)
                check(view.camera.position.y > 1f)
                if (name == "player-return-to-aim") {
                    repeat(6000) { if (!session.canShoot) physics.step() }
                    check(session.state != RoundState.MOVING)
                    session = readyMatch()
                    check(session.humanTurn)
                    renderer.views.update(physics.balls.first { it.id == 0 }.point, direction, true, 0.225f)
                }
            }
            if (name == "player-foul" || name == "player-cleared") {
                session.beginShot()
                if (name == "player-foul") session.accept(PhysicsEvent.Pocketed(0))
                else { finishMatch(Player.HUMAN); return }
                session.accept(PhysicsEvent.Settled)
                if (name == "player-foul") physics.reset(TableGeometry.rack(42))
                else physics.reset(listOf(0 to cue))
                if (name == "player-foul") {
                    check(!session.finished)
                    hudPresentation = hudPresentation.copy(foulPlayer = Player.HUMAN, notice = "You: Foul - cue ball pocketed")
                    tablePresentation = tablePresentation.copy(humanCanAim = false)
                } else check(session.finished)
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
                    renderer.render(physics, session, gesture, 1080, 2400, viewDelta = 1f / 30, table = tablePresentation, hudState = hudPresentation)
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
                    renderer.render(physics, session, gesture, 1080, 2400, table = tablePresentation, hudState = hudPresentation)
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
