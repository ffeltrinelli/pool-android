package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.Player
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.TableSnapshot

/** All timing is active game time. Pausing invalidates work and restarts an unfinished cue stroke. */
class ComputerTurn(
    private val planning: ShotPlanningService,
    private val reportPlan: (PlanningResult) -> Unit = {}
) : AutoCloseable {
    private sealed interface State {
        data object Idle : State
        data class Planning(val elapsed: Float) : State
        data class Selected(val shot: ShotCommand, val elapsed: Float) : State
        data class Stroking(val selected: ShotCommand, val animation: ComputerStroke) : State
    }

    private var state: State = State.Idle
    private var seed = 0L
    var foulSeconds = 0f
        private set
    var foulPlayer: Player? = null
        private set
    val pose get() = (state as? State.Stroking)?.animation?.pose
    val thinking get() = state is State.Planning || state is State.Selected
    val blocksShot get() = foulSeconds > 0f

    fun foul(player: Player) {
        reset()
        foulPlayer = player
        foulSeconds = OpponentTuning.FOUL_SECONDS
    }

    fun advance(
        session: GameSession,
        table: () -> TableSnapshot,
        seconds: Float,
        difficulty: OpponentDifficulty = OpponentDifficulty.MEDIUM,
        placeCue: (Point) -> Unit = {}
    ): ShotCommand? {
        val delta = seconds.coerceAtLeast(0f)
        if (blocksShot) {
            advanceFoulNotice(delta)
            return null
        }
        if ((!session.canShoot && session.placement == null) || session.player != Player.COMPUTER) return null
        val current = state
        if (current is State.Stroking) return advanceStroke(current, delta)
        advancePlanning(table, delta, placeCue)
        val selected = state as? State.Selected ?: return null
        if (selected.elapsed >= OpponentTuning.THINK_SECONDS) {
            val executed = ExecutionAccuracy.apply(selected.shot, difficulty, seed)
            state = State.Stroking(selected.shot, ComputerStroke(executed))
        }
        return null
    }

    private fun advanceFoulNotice(delta: Float) {
        foulSeconds = (foulSeconds - delta).coerceAtLeast(0f)
        if (foulSeconds == 0f) foulPlayer = null
    }

    private fun advancePlanning(table: () -> TableSnapshot, delta: Float, placeCue: (Point) -> Unit) {
        if (state == State.Idle) {
            planning.start(table(), ++seed)
            state = State.Planning(0f)
        }
        state = when (val current = state) {
            is State.Planning -> {
                val elapsed = current.elapsed + delta
                val result = planning.poll()
                if (result == null) State.Planning(elapsed)
                else {
                    result.cuePosition?.let(placeCue)
                    reportPlan(result)
                    State.Selected(result.shot, elapsed)
                }
            }
            is State.Selected -> current.copy(elapsed = current.elapsed + delta)
            else -> state
        }
    }

    private fun advanceStroke(current: State.Stroking, delta: Float): ShotCommand? {
        val shot = current.animation.advance(delta)
        if (shot != null) state = State.Idle
        return shot
    }

    fun suspend() {
        planning.cancel()
        state = when (val current = state) {
            is State.Selected -> current.copy(elapsed = 0f)
            is State.Stroking -> State.Selected(current.selected, 0f)
            else -> State.Idle
        }
    }

    fun reset() {
        suspend()
        state = State.Idle
        foulSeconds = 0f
        foulPlayer = null
    }

    override fun close() {
        reset()
        planning.close()
    }
}
