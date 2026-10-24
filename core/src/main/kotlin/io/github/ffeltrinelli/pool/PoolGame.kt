package io.github.ffeltrinelli.pool

import com.badlogic.gdx.*
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.PlacementRegion
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.render.TablePresentation
import io.github.ffeltrinelli.pool.ui.*
import io.github.ffeltrinelli.pool.view.ViewMode

class PoolGame : ApplicationAdapter() {
    private val session = GameSession()
    private val gesture = CueGesture()
    private lateinit var input: GameInput
    private lateinit var audio: PoolAudio
    private lateinit var physics: PoolPhysics
    private lateinit var renderer: PoolRenderer
    private lateinit var controls: GameControls
    private lateinit var computer: ComputerTurn
    private val physicsEvents = mutableListOf<PhysicsEvent>()
    private var paused = false
    private var skipDelta = true
    private val placement = CueBallPlacement()
    private var activeSeconds = 0f
    private var notice: String? = null
    private var noticeSeconds = 0f
    private var rerackPending = false

    override fun create() {
        audio = PoolAudio(LibGdxSoundPlayer())
        physics = PoolPhysics(physicsEvents::add, audio::queue, audio::queue)
        computer = ComputerTurn(WorkerShotPlanningService(reportFailure = { Gdx.app.error("PoolAI", "Planning failed; using fallback", it) })) {
            Gdx.app.log("PoolAI", "${it.evaluations} evaluations in ${it.elapsedMillis} ms; fallback=${it.usedFallback}; predictedFoul=${it.predictedFoul}; shot=${it.shot}")
        }
        renderer = PoolRenderer()
        resetRackAim()
        controls = GameControls(SavedSettingsPreference(Gdx.app.getPreferences("pool-settings")), object : ControlEffects {
            override fun cancelGesture() = cancelInput()
            override fun pauseGame() { audio.pause(); computer.suspend(); skipDelta = true }
            override fun resumeGame() { audio.resume(); skipDelta = true }
            override fun restartGame() = restart()
            override fun exitGame() { computer.reset(); Gdx.app.exit() }
            override fun selectView(mode: ViewMode) { renderer.views.select(mode) }
        })
        renderer.views.select(controls.viewMode)
        if (paused) controls.pause()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        input = GameInput(controls, session, { renderer.hud.layout }, renderer.views, physics, gesture, ::humanCanShoot, ::strike, placement, ::confirmHumanPlacement)
        Gdx.input.inputProcessor = input
    }

    private fun strike(shot: ShotCommand) {
        val cue = physics.balls.first { it.id == 0 && !it.pocketed }
        if (session.player == Player.HUMAN) renderer.views.update(cue.point, shot.direction, true)
        session.beginShot()
        physics.strike(shot)
    }

    private fun cancelInput() {
        if (::input.isInitialized) input.cancel() else gesture.cancel()
    }
    private fun humanCanShoot() = session.humanTurn && !computer.blocksShot && !rerackPending

    private fun restart() {
        computer.reset()
        physicsEvents.clear()
        audio.clear()
        physics.reset()
        session.restart()
        resetPlacement()
        resetRackAim()
        skipDelta = true
    }

    private fun resetRackAim() {
        cancelInput()
        val rack = TableGeometry.rack().toMap()
        gesture.resetForRack(rack.getValue(0), Point(0f, TableGeometry.FOOT_SPOT))
        renderer.views.reset()
        renderer.views.update(rack.getValue(0), gesture.direction, true)
    }

    override fun render() {
        val delta = if (controls.suspended || skipDelta) 0f else Gdx.graphics.deltaTime.coerceIn(0f, 0.1f)
        if (!controls.suspended) {
            activeSeconds += delta
            noticeSeconds = (noticeSeconds - delta).coerceAtLeast(0f)
            if (noticeSeconds == 0f) {
                notice = null
                if (rerackPending) {
                    rerackPending = false
                    computer.reset(); audio.clear(); physics.reset()
                    session.rerackAfterInsufficientBreak()
                    resetPlacement(); resetRackAim()
                }
            }
            physics.advance(delta)
            processPhysicsEvents()
            if (!rerackPending) computer.advance(session, ::planningSnapshot, delta, controls.difficulty, ::applyPlacement)?.let(::strike)
            audio.flush(delta)
            skipDelta = false
        }
        val table = TablePresentation(
            humanCanAim = humanCanShoot(),
            computerPose = computer.pose,
            holdOverview = !session.humanTurn || computer.blocksShot,
            aidedAim = controls.aidedAim,
            placement = placement.point.takeIf { session.placement != null && session.player == Player.HUMAN },
            placementRegion = session.placement,
            placementValid = placementError() == null,
            activeSeconds = activeSeconds
        )
        val hud = HudPresentation(controls.settingsOpen, computer.thinking, computer.foulPlayer, controls.difficulty, notice, placementError())
        renderer.render(physics, session, gesture, viewDelta = delta, table = table, hudState = hud)
    }

    private fun planningSnapshot() = physics.snapshot().copy(rules = session.context(), placement = session.placement)

    private fun placementError() = session.placement?.let { placement.invalidReason(physics.snapshot(), it) }

    private fun confirmHumanPlacement() {
        if (computer.blocksShot || placementError() != null) return
        applyPlacement(placement.point)
    }

    private fun applyPlacement(point: Point) {
        if (session.placement == null) return
        check(BallPlacement.invalidReason(point,
            physics.balls.filter { it.id != 0 && !it.pocketed }.map { it.point }, session.placement!!) == null)
        physics.placeBall(0, point)
        audio.ballReturnedToTable(0)
        if (session.breaking) {
            gesture.resetForRack(point, Point(0f, TableGeometry.FOOT_SPOT))
            if (session.player == Player.HUMAN) renderer.views.reset()
        }
        session.confirmPlacement()
        cancelInput()
        renderer.views.placing = false
        renderer.views.update(point, gesture.direction, session.player == Player.HUMAN)
    }

    private fun resetPlacement() {
        rerackPending = false
        notice = null
        noticeSeconds = 0f
        placement.begin()
    }

    private fun preparePlacement() {
        val region = session.placement ?: return
        cancelInput()
        if (region == PlacementRegion.BEHIND_HEAD_STRING && !session.breaking)
            BallPlacement.restrictedTargetToSpot(physics.snapshot(), session.context().targets)?.let(physics::spotBall)
        val point = physics.balls.firstOrNull { it.id == 0 && !it.pocketed }?.point ?: TableGeometry.CUE_START
        placement.begin(point)
        renderer.views.placing = session.player == Player.HUMAN
    }

    private fun processPhysicsEvents() {
        val events = physicsEvents.toList()
        physicsEvents.clear()
        for (event in events) {
            val wasMoving = session.state == RoundState.MOVING
            val previouslyAssigned = session.group(Player.HUMAN) != null
            session.accept(event)
            if (event != PhysicsEvent.Settled || !wasMoving) continue
            val outcome = session.outcome ?: continue
            val decision = outcome.decision
            if (session.finished) continue
            if (decision.spotEight) { physics.spotBall(8); audio.ballReturnedToTable(8) }
            notice = when {
                decision.rerack -> (decision.foul?.let { "Foul: ${it.message}. " } ?: "") + "Insufficient break - opponent breaks a fresh rack"
                decision.foul != null -> "${outcome.player.label}: Foul - ${decision.foul.message}"
                decision.group != null && !previouslyAssigned -> "You are ${session.group(Player.HUMAN)!!.label.lowercase()}. Computer is ${session.group(Player.COMPUTER)!!.label.lowercase()}."
                session.group(Player.HUMAN) == null && outcome.pocketed.any { it in 1..7 } && outcome.pocketed.any { it in 9..15 } ->
                    "Solid and stripe pocketed - table stays open. ${outcome.player.label} continue${if (outcome.player == Player.COMPUTER) "s" else ""}."
                else -> null
            }
            noticeSeconds = 3f
            if (decision.rerack) { rerackPending = true; computer.foul(outcome.player) }
            else if (decision.foul != null) computer.foul(outcome.player)
            preparePlacement()
        }
    }

    override fun resize(width: Int, height: Int) {
        if (::renderer.isInitialized) { cancelInput(); renderer.resize(width, height) }
    }
    override fun pause() { paused = true; cancelInput(); if (::controls.isInitialized) controls.pause() }
    override fun resume() { paused = false; skipDelta = true; if (::controls.isInitialized) controls.resume() }
    override fun dispose() {
        Gdx.input?.inputProcessor = null
        Gdx.input?.setCatchKey(Input.Keys.BACK, false)
        if (::computer.isInitialized) computer.close()
        if (::audio.isInitialized) audio.dispose()
        if (::renderer.isInitialized) renderer.dispose()
        if (::physics.isInitialized) physics.dispose()
    }
}
