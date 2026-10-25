package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import kotlin.math.*

enum class ShotKind(val isPot: Boolean = false, val isKick: Boolean = false) {
    DIRECT_POT(true), BANK_POT(true), KICK_POT(true, true), CONTACT, KICK_CONTACT(false, true), BREAK
}

data class ShotCandidate(val shot: ShotCommand, val rankingCost: Float, val targetBallId: Int? = null,
    val kind: ShotKind = ShotKind.DIRECT_POT)

/** Ghost-ball geometry only seeds the search; Bullet decides whether a pot actually works. */
class ShotCandidates {
    private val predictor = AimPredictor()
    private val directByPosition = mutableMapOf<TableSnapshot, DirectPotSeeds>()
    fun generate(table: TableSnapshot, checkBudget: () -> Unit = {}): List<ShotCandidate> {
        val cue = table.balls.first { it.id == 0 }.point
        if (table.rulesContext().breaking) return listOf(ShotCandidate(
            ShotCommand((Point(0f, TableGeometry.FOOT_SPOT) - cue).normalized(), 1.10f), 0f, kind = ShotKind.BREAK))
        val pots = directSeeds(table, checkBudget)
        return pots.centered + pots.supplemental
    }

    internal fun directSeeds(table: TableSnapshot, checkBudget: () -> Unit): DirectPotSeeds {
        checkBudget()
        return directByPosition.getOrPut(table) { DirectPotGeometry(table).generate(checkBudget) }
    }

    /** Broaden the search beyond pots: a firm legal contact can satisfy the rail rule. */
    fun contactAlternatives(table: TableSnapshot): List<ShotCommand> {
        if (table.rulesContext().breaking) return emptyList()
        val cue = table.balls.first { it.id == 0 }.point
        val balls = table.balls.map { AimBall(it.id, it.point) }
        val targets = table.rulesContext().targets
        val visible = table.balls.filter { it.id in targets }
            .sortedBy { (it.point - cue).length() }
            .filter { predictor.predict(cue, (it.point - cue).normalized(), balls).targetBallId == it.id }
            .take(2)
        return visible.flatMap { ball ->
            val direction = (ball.point - cue).normalized()
            listOf(OpponentTuning.CONTACT_IMPULSE, OpponentTuning.STRONG_CONTACT_IMPULSE).map { impulse -> ShotCommand(direction, impulse) }
        }
    }

    fun fallback(table: TableSnapshot): ShotCommand {
        val cue = table.balls.first { it.id == 0 }.point
        if (table.rulesContext().breaking) return ShotCommand((Point(0f, TableGeometry.FOOT_SPOT) - cue).normalized(), 1.10f)
        contactAlternatives(table).firstOrNull()?.let { return it }
        ReboundCandidates().generate(table).firstOrNull { it.kind.isKick }?.let { return it.shot }
        // No verified route exists. A bounded miss is preferable to aiming through a known illegal ball.
        val balls = table.balls.map { AimBall(it.id, it.point) }
        val direction = (0 until 32).map { angle ->
            val radians = angle * (2f * PI.toFloat() / 32f)
            Point(sin(radians), cos(radians))
        }.firstOrNull { predictor.predict(cue, it, balls).targetBallId == null } ?: Point(0f, -1f)
        return ShotCommand(direction, OpponentTuning.MIN_IMPULSE)
    }
}
