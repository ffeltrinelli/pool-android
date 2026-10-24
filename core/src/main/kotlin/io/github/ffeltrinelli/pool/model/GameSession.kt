package io.github.ffeltrinelli.pool.model

sealed interface PhysicsEvent {
    data class Pocketed(val ballId: Int) : PhysicsEvent
    data object Settled : PhysicsEvent
}

enum class Player { HUMAN, COMPUTER;
    val opponent get() = if (this == HUMAN) COMPUTER else HUMAN
    val label get() = if (this == HUMAN) "You" else "Computer"
}
enum class MatchResult { HUMAN_WINS, COMPUTER_WINS, DRAW }
enum class RoundState { READY, MOVING, FINISHED }
data class ShotCommand(val direction: Point, val impulse: Float)
data class ShotOutcome(val player: Player, val pocketed: Int, val foul: Boolean)

/** Scores are committed only at settling, after every pocket event in the shot is known. */
class GameSession {
    var player = Player.HUMAN
        private set
    var humanScore = 0
        private set
    var computerScore = 0
        private set
    private val remainingIds = (1..15).toMutableSet()
    private var scratched = false
    private var shotPockets = 0
    var outcome: ShotOutcome? = null
        private set
    var state = RoundState.READY
        private set
    val remaining: Int get() = remainingIds.size
    val canShoot: Boolean get() = state == RoundState.READY
    val humanTurn get() = canShoot && player == Player.HUMAN
    val finished: Boolean get() = state == RoundState.FINISHED
    val result: MatchResult? get() = if (!finished) null else when {
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
            PhysicsEvent.Settled -> finishShot()
        }
    }

    private fun finishShot() {
        val completed = ShotOutcome(player, shotPockets, scratched)
        outcome = completed
        if (!completed.foul) awardPoints(completed)
        if (remaining == 0) state = RoundState.FINISHED
        else {
            player = player.opponent
            state = RoundState.READY
        }
    }

    private fun awardPoints(completed: ShotOutcome) {
        when (completed.player) {
            Player.HUMAN -> humanScore += completed.pocketed
            Player.COMPUTER -> computerScore += completed.pocketed
        }
    }

    fun restart() {
        player = Player.HUMAN
        humanScore = 0
        computerScore = 0
        remainingIds.clear()
        remainingIds.addAll(1..15)
        scratched = false
        shotPockets = 0
        outcome = null
        state = RoundState.READY
    }
}
