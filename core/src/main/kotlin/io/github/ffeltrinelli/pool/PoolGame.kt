package io.github.ffeltrinelli.pool

import com.badlogic.gdx.*
import io.github.ffeltrinelli.pool.replay.*
import io.github.ffeltrinelli.pool.input.*
import io.github.ffeltrinelli.pool.audio.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.PlacementRegion
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.render.TablePresentation
import io.github.ffeltrinelli.pool.ui.*
import io.github.ffeltrinelli.pool.stats.*

class PoolGame(
    private val session: GameSession = GameSession(),
    private val sharing: ReplaySharing = NoReplaySharing,
    private val appSharing: AppSharing = NoAppSharing,
    private val soundPlayer: () -> SoundPlayer = { LibGdxSoundPlayer() }
) : ApplicationAdapter() {
    private val recorder = ReplayRecorder()
    private var replay: ReplayPlayback? = null
    private var export: ReplayExport? = null
    private var exportError: String? = null
    private lateinit var replayHud: ReplayHud
    private lateinit var replaySound: SoundPlayer
    private var pendingReplayOutcome: ShotOutcome? = null
    private val gesture = CueGesture()
    private lateinit var input: GameInput
    private lateinit var audio: PoolAudio
    private lateinit var physics: PoolPhysics
    private lateinit var renderer: PoolRenderer
    private lateinit var statistics: MatchStatisticsTracker
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
        audio = PoolAudio(ReplayRecordingPlayer(soundPlayer(), recorder))
        replaySound = soundPlayer()
        replayHud = ReplayHud()
        physics = PoolPhysics({ event ->
            recorder.event(event, PoolPhysics.STEP)
            physicsEvents.add(event)
        }, audio::queue, audio::queue, { if (recorder.recording) recorder.step(PoolPhysics.STEP, physics.replayBalls()) })
        computer = ComputerTurn(WorkerShotPlanningService(reportFailure = { Gdx.app.error("PoolAI", "Planning failed; using fallback", it) })) {
            Gdx.app.log("PoolAI", "${it.evaluations} evaluations in ${it.elapsedMillis} ms; fallback=${it.usedFallback}; cue=${it.cuePosition}; kind=${it.kind}; first=${it.predictedFirstContacts}; metrics=${it.metrics}; predictedFoul=${it.predictedFoul}; target=${it.targetBallId}; predictedPockets=${it.predictedPockets}; shot=${it.shot}")
        }
        renderer = PoolRenderer()
        resetRackAim()
        statistics = MatchStatisticsTracker(SavedStatisticsPreference(Gdx.app.getPreferences("pool-statistics")))
        controls = GameControls(SavedSettingsPreference(Gdx.app.getPreferences("pool-settings")), object : ControlEffects {
            override fun cancelGesture() = cancelInput()
            override fun pauseGame() { audio.pause(); replay?.silence(); computer.suspend(); skipDelta = true }
            override fun resumeGame() { audio.resume(); skipDelta = true }
            override fun restartGame() = restart()
            override fun shareApp() = appSharing.share()
            override fun resetStatistics() = statistics.reset()
            override fun exitGame() { computer.reset(); Gdx.app.exit() }
        })
        if (paused) controls.pause()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        input = GameInput(controls, session, { renderer.hud.layout }, renderer.views, physics, gesture, ::humanCanShoot, ::strike, placement, ::replayTouch, ::replayBack, ::confirmHumanPlacement)
        Gdx.input.inputProcessor = input
    }

    private fun strike(shot: ShotCommand) {
        val cue = physics.balls.first { it.id == 0 && !it.pocketed }
        if (session.player == Player.HUMAN) renderer.views.update(cue.point, shot.direction, true)
        if (controls.replays) recorder.strike(physics.replayBalls(), CuePose(shot.direction, 0f))
        session.beginShot()
        physics.strike(shot)
    }

    private fun cancelInput() {
        if (::input.isInitialized) input.cancel() else gesture.cancel()
    }
    private fun humanCanShoot() = replay == null && session.humanTurn && !computer.blocksShot && !rerackPending

    private fun restart() {
        endReplay()
        recorder.clear()
        computer.reset()
        physicsEvents.clear()
        audio.clear()
        physics.reset()
        session.restart()
        statistics.beginRack()
        resetPlacement()
        resetRackAim()
        skipDelta = true
    }

    private fun resetRackAim() {
        cancelInput()
        val rack = TableGeometry.rack().toMap()
        gesture.resetForRack(rack.getValue(0), Point(0f, TableGeometry.FOOT_SPOT))
        renderer.views.reset()
        renderer.views.follow(session)
        renderer.views.update(rack.getValue(0), gesture.direction, true)
    }

    override fun render() {
        val delta = if (controls.suspended || skipDelta) 0f else Gdx.graphics.deltaTime.coerceIn(0f, 0.1f)
        if (replay != null) {
            renderReplay(delta)
            skipDelta = false
            return
        }
        if (!controls.suspended) {
            if (controls.replays) recorder.aim(delta, physics.replayBalls(), currentCuePose())
            activeSeconds += delta
            noticeSeconds = (noticeSeconds - delta).coerceAtLeast(0f)
            if (noticeSeconds == 0f) {
                notice = null
                if (rerackPending) {
                    rerackPending = false
                    computer.reset(); audio.clear(); physics.reset()
                    session.rerackAfterInsufficientBreak()
                    statistics.beginRack()
                    resetPlacement(); resetRackAim()
                }
            }
            physics.advance(delta)
            processPhysicsEvents()
            if (!rerackPending && pendingReplayOutcome == null) computer.advance(session, ::planningSnapshot, delta, controls.difficulty, ::applyPlacement)?.let(::strike)
            audio.flush(delta)
            pendingReplayOutcome?.let { outcome ->
                pendingReplayOutcome = null
                val clip = recorder.finish()
                if (controls.replays && qualifiesForReplay(outcome) && clip != null) {
                    cancelInput(); audio.pause()
                    replay = ReplayPlayback(clip.slowMotion(), replaySound)
                }
            }
            skipDelta = false
        }
        if (replay != null) { renderReplay(0f); return }
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
        val hud = settingsPresentation().copy(
            computerThinking = computer.thinking,
            foulPlayer = computer.foulPlayer,
            notice = notice,
            placementError = placementError()
        )
        renderer.render(physics, session, gesture, viewDelta = delta, table = table, hudState = hud)
    }

    private fun settingsPresentation() = HudPresentation(
        settingsOpen = controls.settingsOpen,
        appSharingAvailable = appSharing.available,
        difficulty = controls.difficulty,
        replays = controls.replays,
        helpOpen = controls.helpOpen,
        statsOpen = controls.statsOpen,
        statistics = statistics.snapshot
    )

    private fun currentCuePose(): CuePose? {
        if (!session.humanTurn) return computer.pose
        return when (gesture.phase) {
            CuePhase.AIMING, CuePhase.STROKING -> CuePose(gesture.direction, gesture.tipGap)
            else -> null
        }
    }

    private fun renderReplay(delta: Float) {
        val playback = replay ?: return
        val state = exportState()
        if (!controls.suspended) {
            when (state) {
                ShareState.Encoding -> try { export?.advance(renderer) }
                    catch (failure: RuntimeException) { failExport(failure) }
                ShareState.Ready -> sharing.share()
                ShareState.Complete -> { sharing.cancel(); export?.close(); export = null }
                else -> Unit
            }
            if (export == null && exportState() !is ShareState.Failed && state != ShareState.Sharing && state !is ShareState.Failed) playback.advance(delta)
        }
        renderer.renderReplay(playback.clip, playback.seconds, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        replayHud.render(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight, sharing.available, exportState(), export?.progress ?: 0f, playback.holdSecondsRemaining)
        if (controls.settingsOpen) renderer.hud.renderSettings(controls.aidedAim,
            settingsPresentation(), Gdx.graphics.backBufferWidth)
        if (playback.finished && !controls.suspended && export == null && exportState() !is ShareState.Failed) endReplay()
    }

    private fun replayTouch(x: Int, y: Int): Boolean {
        val playback = replay ?: return false
        when (replayHud.hit(x, y, Gdx.graphics.width, Gdx.graphics.height)) {
            ReplayHud.Action.SKIP -> {
                if (export != null) { sharing.cancel(); export?.close(); export = null } else endReplay()
            }
            ReplayHud.Action.SHARE -> if (sharing.available && sharing.state != ShareState.Encoding && sharing.state != ShareState.Sharing) {
                playback.silence()
                exportError = null
                export?.close()
                try { export = ReplayExport(sharing, playback.clip) }
                catch (failure: RuntimeException) { failExport(failure) }
            }
            ReplayHud.Action.SETTINGS -> controls.handle(HudAction.SETTINGS)
            ReplayHud.Action.NONE -> Unit
        }
        return true
    }

    private fun exportState(): ShareState = exportError?.let { ShareState.Failed(it) } ?: sharing.state

    private fun failExport(failure: RuntimeException) {
        Gdx.app.error("PoolReplay", "Could not create replay video", failure)
        sharing.cancel(); export?.close(); export = null
        exportError = "Could not create replay video"
    }

    private fun replayBack(): Boolean {
        if (replay == null) return false
        endReplay()
        return true
    }

    private fun endReplay() {
        sharing.cancel(); export?.close(); export = null; exportError = null
        replay?.close(); replay = null
        pendingReplayOutcome = null
        if (::audio.isInitialized && ::controls.isInitialized && !controls.suspended) audio.resume()
        skipDelta = true
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
        renderer.views.follow(session)
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
        renderer.views.follow(session)
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
            pendingReplayOutcome = outcome
            val decision = outcome.decision
            if (session.finished) {
                statistics.recordCompletion(session.result, controls.difficulty)
                continue
            }
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
        endReplay(); sharing.close()
        if (::replayHud.isInitialized) replayHud.close()
        if (::replaySound.isInitialized) replaySound.dispose()
        Gdx.input?.inputProcessor = null
        Gdx.input?.setCatchKey(Input.Keys.BACK, false)
        if (::computer.isInitialized) computer.close()
        if (::audio.isInitialized) audio.dispose()
        if (::renderer.isInitialized) renderer.dispose()
        if (::physics.isInitialized) physics.dispose()
    }
}
