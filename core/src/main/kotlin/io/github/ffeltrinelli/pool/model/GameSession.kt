package io.github.ffeltrinelli.pool.model

import io.github.ffeltrinelli.pool.rules.*

sealed interface PhysicsEvent {
    data class Pocketed(val ballId: Int) : PhysicsEvent
    data class OffTable(val ballId: Int) : PhysicsEvent
    data class Contacts(val cueTargets: Set<Int>, val railBalls: Set<Int>,
        val crossedHeadString: Boolean = false, val targetsBehindHeadString: Boolean = false) : PhysicsEvent
    data object Settled : PhysicsEvent
}
enum class Player { HUMAN, COMPUTER;
    val opponent get() = if (this == HUMAN) COMPUTER else HUMAN
    val label get() = if (this == HUMAN) "You" else "Computer"
}
enum class MatchResult { HUMAN_WINS, COMPUTER_WINS }
enum class RoundState { PLACING, READY, MOVING, FINISHED }
data class ShotCommand(val direction: Point, val impulse: Float)
data class ShotOutcome(val player: Player, val decision: RuleDecision, val pocketed: Set<Int>) {
    val foul get() = decision.foul != null
}

/** Resolves a shot only after settling; presentation and Bullet ownership stay outside rules. */
class GameSession {
    var player = Player.HUMAN
        private set
    private var requestedBreaker = Player.HUMAN
    private val remainingIds = (1..15).toMutableSet()
    private var humanGroup: BallGroup? = null
    private var facts = ShotFacts()
    private var shotContext = RulesContext()
    var breaking = true
        private set
    var placement: PlacementRegion? = PlacementRegion.BEHIND_HEAD_STRING
        private set
    private var restrictedShot = false
    var outcome: ShotOutcome? = null
        private set
    var state = RoundState.PLACING
        private set
    var result: MatchResult? = null
        private set
    val remaining get() = remainingIds.size
    val canShoot get() = state == RoundState.READY
    val humanTurn get() = canShoot && player == Player.HUMAN
    val finished get() = state == RoundState.FINISHED
    fun group(player: Player): BallGroup? = humanGroup?.let { if (player == Player.HUMAN) it else it.opposite }
    fun remaining(player: Player) = group(player)?.let { group -> remainingIds.count { it in group.ids } }
    fun context() = RulesContext(remainingIds.toSet(), group(player), breaking, restrictedShot || (!breaking && placement == PlacementRegion.BEHIND_HEAD_STRING))

    fun confirmPlacement() {
        check(state == RoundState.PLACING)
        restrictedShot = !breaking && placement == PlacementRegion.BEHIND_HEAD_STRING
        placement = null
        state = RoundState.READY
    }
    fun beginShot() {
        check(canShoot)
        facts = ShotFacts()
        shotContext = context()
        outcome = null
        state = RoundState.MOVING
    }
    fun accept(event: PhysicsEvent) {
        if (state != RoundState.MOVING) return
        facts.accept(event)
        if (event == PhysicsEvent.Settled) finishShot()
    }
    private fun finishShot() {
        val decision = EightBallRules.resolve(shotContext, facts)
        remainingIds.removeAll(facts.pocketed + facts.offTable)
        if (decision.spotEight) remainingIds.add(8)
        outcome = ShotOutcome(player, decision, facts.pocketed.toSet())
        if (decision.won || decision.loss != null) {
            val winner = if (decision.won) player else player.opponent
            result = if (winner == Player.HUMAN) MatchResult.HUMAN_WINS else MatchResult.COMPUTER_WINS
            state = RoundState.FINISHED
            return
        }
        decision.group?.let { humanGroup = if (player == Player.HUMAN) it else it.opposite }
        val wasBreak = breaking
        breaking = false
        restrictedShot = false
        if (!decision.continues) player = player.opponent
        placement = if (decision.foul != null) {
            if (wasBreak) PlacementRegion.BEHIND_HEAD_STRING else PlacementRegion.ANYWHERE
        } else null
        state = if (placement == null) RoundState.READY else RoundState.PLACING
    }
    fun restart() {
        requestedBreaker = requestedBreaker.opponent
        freshRack(requestedBreaker)
    }
    fun rerackAfterInsufficientBreak() = freshRack(player)
    private fun freshRack(breaker: Player) {
        player = breaker
        humanGroup = null
        remainingIds.clear(); remainingIds.addAll(1..15)
        facts = ShotFacts()
        outcome = null
        result = null
        breaking = true
        restrictedShot = false
        placement = PlacementRegion.BEHIND_HEAD_STRING
        state = RoundState.PLACING
    }
}
