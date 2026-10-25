package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import kotlin.math.abs
import kotlin.math.sqrt

internal data class DirectPotSeeds(val centered: List<ShotCandidate>, val offCenter: List<ShotCandidate>)

/** One definition of a clear ghost-ball pot, shared by fixed-cue and placement search. */
internal class DirectPotGeometry(private val table: TableSnapshot) {
    private val predictor = AimPredictor()
    private val cue = table.balls.first { it.id == 0 }.point
    private val balls = table.balls.map { AimBall(it.id, it.point) }
    private val targets = table.rulesContext().targets
    private val obstacles = mutableMapOf<Int, List<AimBall>>()

    fun generate(checkBudget: () -> Unit): DirectPotSeeds {
        val centered = mutableListOf<ShotCandidate>()
        val offCenter = mutableListOf<ShotCandidate>()
        for (ball in table.balls.filter { it.id in targets }) for (pocket in PocketGeometry.pockets) {
            for ((index, outgoing) in DirectPocketRoutes.directions(ball.point, pocket).withIndex()) {
                checkBudget()
                val candidate = candidate(ball.id, pocket, outgoing) ?: continue
                if (index == 0) centered += candidate else offCenter += candidate
            }
        }
        return DirectPotSeeds(centered.sortedBy { it.rankingCost }, offCenter.sortedBy { it.rankingCost })
    }

    fun candidate(target: Int, pocket: PocketDefinition, direction: Point? = null): ShotCandidate? {
        if (target !in targets) return null
        val ball = table.balls.firstOrNull { it.id == target } ?: return null
        val outgoing = direction ?: (pocket.center - ball.point).normalized()
        val mouthDistance = DirectPocketRoutes.mouthDistance(ball.point, outgoing, pocket) ?: return null
        val ghost = ball.point - outgoing * (2 * TableGeometry.BALL_RADIUS)
        if (abs(ghost.x) > TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS ||
            abs(ghost.z) > TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS) return null
        val incoming = (ghost - cue).normalized()
        val cut = incoming.dot(outgoing)
        if (cut < 0.15f || predictor.predict(cue, incoming, balls).targetBallId != ball.id) return null
        val objectObstacles = obstacles.getOrPut(ball.id) {
            balls.filter { it.id != ball.id }.map { if (it.id == 0) AimBall(99, it.position) else it }
        }
        val objectPath = predictor.predict(ball.point, outgoing, objectObstacles)
        if ((objectPath.end - ball.point).length() + 0.005f < mouthDistance) return null
        val cueDistance = (ghost - cue).length()
        val objectDistance = (pocket.center - ball.point).length()
        val impulse = (0.18f + 0.16f * sqrt(cueDistance + objectDistance / (cut * cut))).coerceIn(0.08f, OpponentTuning.MAX_IMPULSE)
        return ShotCandidate(ShotCommand(incoming, impulse), cueDistance + objectDistance + (1 - cut) * 3, ball.id)
    }
}
