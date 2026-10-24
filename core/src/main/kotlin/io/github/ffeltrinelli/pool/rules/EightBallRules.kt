package io.github.ffeltrinelli.pool.rules

import io.github.ffeltrinelli.pool.model.*

enum class BallGroup(val label: String, val ids: IntRange) {
    SOLIDS("Solids", 1..7), STRIPES("Stripes", 9..15);
    val opposite get() = if (this == SOLIDS) STRIPES else SOLIDS
}
enum class PlacementRegion { ANYWHERE, BEHIND_HEAD_STRING }
enum class FoulReason(val message: String) {
    SCRATCH("Cue ball pocketed"), OFF_TABLE("Ball driven off the table"),
    NO_CONTACT("No object ball hit"), WRONG_FIRST("Wrong ball hit first"),
    NO_RAIL("No cushion reached after contact"), HEAD_STRING("Cue ball must cross the head string first")
}
data class RulesContext(
    val remaining: Set<Int> = (1..15).toSet(),
    val group: BallGroup? = null,
    val breaking: Boolean = false,
    val restricted: Boolean = false
) {
    val targets: Set<Int> get() = group?.let { assigned ->
        remaining.filter { it in assigned.ids }.toSet().ifEmpty { setOf(8) }
    } ?: remaining.filter { it != 8 || canPlayEight }.toSet()
    val canPlayEight get() = group?.let { g -> remaining.none { it in g.ids } }
        ?: BallGroup.entries.any { g -> remaining.none { it in g.ids } }
    fun legalFirst(ids: Set<Int>) = breaking || ids.any { it in targets || (it == 8 && canPlayEight) }
}

/** Facts have no gameplay side effects and are usable by both live and silent worlds. */
class ShotFacts {
    var firstContacts: Set<Int> = emptySet()
        private set
    var firstContactBehind = false
        private set
    var crossedBeforeContact = false
        private set
    private var crossed = false
    val railBalls = mutableSetOf<Int>()
    var railAfterContact = false
        private set
    val pocketed = mutableSetOf<Int>()
    val offTable = mutableSetOf<Int>()
    fun accept(event: PhysicsEvent) {
        when (event) {
            is PhysicsEvent.Contacts -> {
                if (event.crossedHeadString) crossed = true
                if (firstContacts.isEmpty() && event.cueTargets.isNotEmpty()) {
                    firstContacts = event.cueTargets.toSet()
                    firstContactBehind = event.targetsBehindHeadString
                    crossedBeforeContact = crossed
                }
                railBalls.addAll(event.railBalls)
                if (firstContacts.isNotEmpty() && event.railBalls.isNotEmpty()) railAfterContact = true
            }
            is PhysicsEvent.Pocketed -> pocketed.add(event.ballId)
            is PhysicsEvent.OffTable -> offTable.add(event.ballId)
            PhysicsEvent.Settled -> Unit
        }
    }
}
data class RuleDecision(
    val foul: FoulReason? = null,
    val group: BallGroup? = null,
    val continues: Boolean = false,
    val won: Boolean = false,
    val loss: String? = null,
    val rerack: Boolean = false,
    val spotEight: Boolean = false
)

object EightBallRules {
    fun resolve(context: RulesContext, shot: ShotFacts): RuleDecision {
        val objects = shot.pocketed.filter { it != 0 }.toSet()
        val foul = when {
            0 in shot.pocketed -> FoulReason.SCRATCH
            shot.offTable.isNotEmpty() -> FoulReason.OFF_TABLE
            shot.firstContacts.isEmpty() -> FoulReason.NO_CONTACT
            !context.legalFirst(shot.firstContacts) -> FoulReason.WRONG_FIRST
            context.restricted && shot.firstContactBehind && !shot.crossedBeforeContact -> FoulReason.HEAD_STRING
            !context.breaking && objects.isEmpty() && !shot.railAfterContact -> FoulReason.NO_RAIL
            else -> null
        }
        if (context.breaking) return RuleDecision(
            foul = foul,
            continues = foul == null && objects.isNotEmpty(),
            rerack = objects.isEmpty() && shot.railBalls.count { it != 0 } < 4,
            spotEight = 8 in objects || 8 in shot.offTable
        )
        if (8 in shot.offTable) return RuleDecision(foul, loss = "Eight driven off the table")
        if (8 in objects) return when {
            !context.canPlayEight -> RuleDecision(foul, loss = "Eight pocketed before clearing your group")
            foul != null -> RuleDecision(foul, loss = "Eight pocketed with a foul")
            else -> RuleDecision(won = true, group = context.group)
        }
        val pottedGroups = BallGroup.entries.filter { group -> objects.any { it in group.ids } }
        val assigned = context.group ?: pottedGroups.singleOrNull().takeIf { foul == null }
        val continues = foul == null && if (context.group == null) objects.isNotEmpty() else objects.any { it in context.group.ids }
        return RuleDecision(foul, assigned, continues)
    }
}
