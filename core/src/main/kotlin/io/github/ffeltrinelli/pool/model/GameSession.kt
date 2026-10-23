package io.github.ffeltrinelli.pool.model

sealed interface PhysicsEvent {
    data class Pocketed(val ballId: Int) : PhysicsEvent
    data object Settled : PhysicsEvent
}

enum class PlayMode { SOLO, COMPUTER }
enum class Player { HUMAN, COMPUTER;
    val opponent get() = if (this == HUMAN) COMPUTER else HUMAN
    val label get() = if (this == HUMAN) "You" else "Computer"
}
enum class MatchResult { HUMAN_WINS, COMPUTER_WINS, DRAW }
enum class RoundState { READY, MOVING, SCRATCHED, CLEARED }
data class ShotCommand(val direction: Point, val impulse: Float)
data class ShotOutcome(val player: Player, val pocketed: Int, val foul: Boolean)

private interface SessionRules {
    val player: Player
    val humanScore: Int get() = 0
    val computerScore: Int get() = 0
    fun finishShot(outcome: ShotOutcome, remaining: Int): RoundState
}

private class SoloRules : SessionRules {
    override val player = Player.HUMAN
    override fun finishShot(outcome: ShotOutcome, remaining: Int) = when {
        outcome.foul -> RoundState.SCRATCHED
        remaining == 0 -> RoundState.CLEARED
        else -> RoundState.READY
    }
}

private class ComputerRules : SessionRules {
    override var player = Player.HUMAN
        private set
    override var humanScore = 0
        private set
    override var computerScore = 0
        private set
    override fun finishShot(outcome: ShotOutcome, remaining: Int): RoundState {
        if (!outcome.foul) {
            if (player == Player.HUMAN) humanScore += outcome.pocketed else computerScore += outcome.pocketed
        }
        if (remaining == 0) return RoundState.CLEARED
        player = player.opponent
        return RoundState.READY
    }
}

/** Scores are committed only at settling, after every pocket event in the shot is known. */
class GameSession(mode: PlayMode = PlayMode.SOLO) {
    var mode = mode
        private set
    private var rules: SessionRules = newRules()
    private val remainingIds = (1..15).toMutableSet()
    private var scratched = false
    private var shotPockets = 0
    var outcome: ShotOutcome? = null
        private set
    var state = RoundState.READY
        private set
    val player get() = rules.player
    val humanScore get() = rules.humanScore
    val computerScore get() = rules.computerScore
    val remaining: Int get() = remainingIds.size
    val canShoot: Boolean get() = state == RoundState.READY
    val humanTurn get() = canShoot && player == Player.HUMAN
    val finished: Boolean get() = state == RoundState.SCRATCHED || state == RoundState.CLEARED
    val result: MatchResult? get() = if (!finished || mode == PlayMode.SOLO) null else when {
        humanScore > computerScore -> MatchResult.HUMAN_WINS
        humanScore < computerScore -> MatchResult.COMPUTER_WINS
        else -> MatchResult.DRAW
    }

    fun beginShot() {
        check(canShoot)
        scratched = false
        shotPockets = 0
        outcome = null
        state = RoundState.MOVING
    }

    fun accept(event: PhysicsEvent) {
        if (state != RoundState.MOVING) return
        when (event) {
            is PhysicsEvent.Pocketed -> {
                if (event.ballId == 0) scratched = true
                else if (remainingIds.remove(event.ballId)) shotPockets++
            }
            PhysicsEvent.Settled -> {
                val completed = ShotOutcome(player, shotPockets, scratched)
                outcome = completed
                state = rules.finishShot(completed, remaining)
            }
        }
    }

    fun restart(mode: PlayMode = this.mode) {
        this.mode = mode
        rules = newRules()
        remainingIds.clear()
        remainingIds.addAll(1..15)
        scratched = false
        shotPockets = 0
        outcome = null
        state = RoundState.READY
    }

    private fun newRules(): SessionRules = when (mode) {
        PlayMode.SOLO -> SoloRules()
        PlayMode.COMPUTER -> ComputerRules()
    }
}
