package io.github.ffeltrinelli.pool

import com.badlogic.gdx.*
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
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

    override fun create() {
        audio = PoolAudio(LibGdxSoundPlayer())
        physics = PoolPhysics(physicsEvents::add, audio::queue, audio::queue)
        computer = ComputerTurn(WorkerShotPlanningService(reportFailure = { Gdx.app.error("PoolAI", "Planning failed; using fallback", it) })) {
            Gdx.app.log("PoolAI", "${it.evaluations} evaluations in ${it.elapsedMillis} ms; fallback=${it.usedFallback}")
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
        input = GameInput(controls, session, { renderer.hud.layout }, renderer.views, physics, gesture, ::humanCanShoot, ::strike)
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
    private fun humanCanShoot() = session.humanTurn && !computer.blocksShot

    private fun restart() {
        computer.reset()
        physicsEvents.clear()
        audio.clear()
        physics.reset()
        session.restart()
        resetRackAim()
        skipDelta = true
    }

    private fun resetRackAim() {
        cancelInput()
        val rack = TableGeometry.rack().toMap()
        gesture.resetForRack(rack.getValue(0), rack.getValue(1))
        renderer.views.reset()
        renderer.views.update(rack.getValue(0), gesture.direction, true)
    }

    override fun render() {
        val delta = if (controls.suspended || skipDelta) 0f else Gdx.graphics.deltaTime.coerceIn(0f, 0.1f)
        if (!controls.suspended) {
            physics.advance(delta)
            processPhysicsEvents()
            computer.advance(session, physics::snapshot, delta, controls.difficulty)?.let(::strike)
            audio.flush(delta)
            skipDelta = false
        }
        val table = TablePresentation(
            humanCanAim = humanCanShoot(),
            computerPose = computer.pose,
            holdOverview = !session.humanTurn || computer.blocksShot,
            aidedAim = controls.aidedAim
        )
        val hud = HudPresentation(controls.settingsOpen, computer.thinking, computer.foulPlayer, controls.difficulty)
        renderer.render(physics, session, gesture, viewDelta = delta, table = table, hudState = hud)
    }

    private fun processPhysicsEvents() {
        val events = physicsEvents.toList()
        physicsEvents.clear()
        for (event in events) {
            session.accept(event)
            if (event == PhysicsEvent.Settled && !session.finished) {
                session.outcome?.takeIf { it.foul }?.let { outcome ->
                    physics.replaceCueBall()
                    audio.cueBallReplaced()
                    computer.foul(outcome.player)
                }
            }
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
