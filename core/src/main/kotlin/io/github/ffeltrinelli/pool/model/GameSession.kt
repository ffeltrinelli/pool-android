package io.github.ffeltrinelli.pool.model

sealed interface PhysicsEvent {
    data class Pocketed(val ballId: Int) : PhysicsEvent
    data object Settled : PhysicsEvent
}

enum class RoundState { READY, MOVING, SCRATCHED, CLEARED }
data class ShotCommand(val direction: Point, val impulse: Float)

class GameSession {
    private val remainingIds = (1..15).toMutableSet()
    private var scratched = false
    var state = RoundState.READY
        private set
    val remaining: Int get() = remainingIds.size
    val canShoot: Boolean get() = state == RoundState.READY
    val finished: Boolean get() = state == RoundState.SCRATCHED || state == RoundState.CLEARED

    fun beginShot() {
        check(canShoot)
        state = RoundState.MOVING
    }

    fun accept(event: PhysicsEvent) {
        when (event) {
            is PhysicsEvent.Pocketed -> {
                if (event.ballId == 0) scratched = true else remainingIds.remove(event.ballId)
            }
            PhysicsEvent.Settled -> state = when {
                scratched -> RoundState.SCRATCHED
                remainingIds.isEmpty() -> RoundState.CLEARED
                else -> RoundState.READY
            }
        }
    }

    fun restart() {
        remainingIds.clear()
        remainingIds.addAll(1..15)
        scratched = false
        state = RoundState.READY
    }
}
