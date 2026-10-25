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
    fun generate(table: TableSnapshot, checkBudget: () -> Unit = {}): List<ShotCandidate> {
        val context = table.rulesContext()
        val targets = context.targets
        val cue = table.balls.first { it.id == 0 }.point
        val balls = table.balls.map { AimBall(it.id, it.point) }
        val candidates = mutableListOf<ShotCandidate>()
        if (context.breaking) return listOf(ShotCandidate(
            ShotCommand((Point(0f, TableGeometry.FOOT_SPOT) - cue).normalized(), 1.10f), 0f, kind = ShotKind.BREAK))
        for (ball in table.balls.filter { it.id in targets }) {
            val obstacles = balls.filter { it.id != ball.id }.map { if (it.id == 0) AimBall(99, it.position) else it }
            for (pocket in PocketGeometry.pockets) {
                checkBudget()
                val outgoing = (pocket.center - ball.point).normalized()
                val ghost = ball.point - outgoing * (2 * TableGeometry.BALL_RADIUS)
                if (abs(ghost.x) > TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS ||
                    abs(ghost.z) > TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS) continue
                val incoming = (ghost - cue).normalized()
                val cut = incoming.dot(outgoing)
                if (cut < 0.15f) continue
                if (predictor.predict(cue, incoming, balls).targetBallId != ball.id) continue
                val objectPath = predictor.predict(ball.point, outgoing, obstacles)
                if ((objectPath.end - ball.point).length() + 0.005f < (pocket.mouthCenter - ball.point).length()) continue
                val cueDistance = (ghost - cue).length()
                val objectDistance = (pocket.center - ball.point).length()
                val impulse = (0.18f + 0.16f * sqrt(cueDistance + objectDistance / (cut * cut))).coerceIn(0.08f, OpponentTuning.MAX_IMPULSE)
                candidates.add(ShotCandidate(ShotCommand(incoming, impulse), cueDistance + objectDistance + (1 - cut) * 3, ball.id))
            }
        }
        return candidates.sortedBy { it.rankingCost }
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
