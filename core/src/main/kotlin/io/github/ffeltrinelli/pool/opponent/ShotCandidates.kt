package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import kotlin.math.*

data class ShotCandidate(val shot: ShotCommand, val difficulty: Float)

/** Ghost-ball geometry only seeds the search; Bullet decides whether a pot actually works. */
class ShotCandidates {
    private val predictor = AimPredictor()
    fun generate(table: TableSnapshot): List<ShotCandidate> {
        val cue = table.balls.first { it.id == 0 }.point
        val balls = table.balls.map { AimBall(it.id, it.point) }
        val candidates = mutableListOf<ShotCandidate>()
        for (ball in table.balls.filter { it.id != 0 }) for (pocket in PocketGeometry.pockets) {
            val outgoing = (pocket.center - ball.point).normalized()
            val ghost = ball.point - outgoing * (2 * TableGeometry.BALL_RADIUS)
            if (abs(ghost.x) > TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS ||
                abs(ghost.z) > TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS) continue
            val incoming = (ghost - cue).normalized()
            val cut = incoming.dot(outgoing)
            if (cut < 0.15f) continue
            if (predictor.predict(cue, incoming, balls).targetBallId != ball.id) continue
            val objectPath = predictor.predict(ball.point, outgoing,
                balls.filter { it.id != ball.id }.map { if (it.id == 0) AimBall(99, it.position) else it })
            if ((objectPath.end - ball.point).length() + 0.005f < (pocket.mouthCenter - ball.point).length()) continue
            val cueDistance = (ghost - cue).length()
            val objectDistance = (pocket.center - ball.point).length()
            val impulse = (0.18f + 0.16f * sqrt(cueDistance + objectDistance / (cut * cut))).coerceIn(0.08f, OpponentTuning.MAX_IMPULSE)
            candidates.add(ShotCandidate(ShotCommand(incoming, impulse), cueDistance + objectDistance + (1 - cut) * 3))
        }
        return candidates.sortedBy { it.difficulty }
    }

    fun fallback(table: TableSnapshot): ShotCommand {
        val cue = table.balls.first { it.id == 0 }.point
        val target = table.balls.filter { it.id != 0 }.minByOrNull { (it.point - cue).length() }
        return ShotCommand(target?.let { (it.point - cue).normalized() } ?: Point(0f, -1f), 0.55f)
    }
}
