package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.BallSnapshot
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import kotlin.math.sqrt

/** Mirror geometry supplies seeds; the production simulator remains the authority on rebounds. */
internal class ReboundCandidates {
    private val geometry = ReboundGeometry()
    private val predictor = AimPredictor()

    fun generate(table: TableSnapshot, checkBudget: () -> Unit = {}): List<ShotCandidate> {
        if (table.rulesContext().breaking) return emptyList()
        val cue = table.balls.first { it.id == 0 }.point
        val balls = table.balls.map { AimBall(it.id, it.point) }
        val banks = mutableListOf<ShotCandidate>()
        val kicks = mutableListOf<ShotCandidate>()
        val targets = table.rulesContext().targets
        for (target in table.balls.filter { it.id in targets }) {
            val obstacles = balls.filter { it.id != target.id }.map { if (it.id == 0) AimBall(99, it.position) else it }
            val layout = TargetLayout(cue, target, balls, obstacles)
            for (face in geometry.faces) {
                checkBudget()
                kickContact(layout, face)?.let(kicks::add)
                for (pocket in PocketGeometry.pockets) {
                    checkBudget()
                    bankPot(layout, face, pocket)?.let(banks::add)
                    kickPot(layout, face, pocket)?.let(kicks::add)
                }
            }
        }
        return shortlist(banks, kicks)
    }

    private data class TargetLayout(val cue: Point, val target: BallSnapshot,
        val balls: List<AimBall>, val obstacles: List<AimBall>)

    private fun kickContact(layout: TargetLayout, face: ReboundGeometry.Face): ShotCandidate? {
        val bounce = face.bounce(layout.cue, layout.target.point) ?: return null
        val incoming = (layout.target.point - bounce).normalized()
        if (!geometry.reachesBounce(layout.cue, bounce, layout.balls) ||
            geometry.afterBounce(bounce, incoming, layout.balls).targetBallId != layout.target.id) return null
        val cost = 4f + (bounce - layout.cue).length() + (layout.target.point - bounce).length()
        return ShotCandidate(ShotCommand((bounce - layout.cue).normalized(), OpponentTuning.CONTACT_IMPULSE),
            cost, layout.target.id, ShotKind.KICK_CONTACT)
    }

    private fun bankPot(layout: TargetLayout, face: ReboundGeometry.Face, pocket: PocketDefinition): ShotCandidate? {
        val bounce = face.bounce(layout.target.point, pocket.center) ?: return null
        val outgoing = (bounce - layout.target.point).normalized()
        val ghost = layout.target.point - outgoing * (2f * TableGeometry.BALL_RADIUS)
        val incoming = (ghost - layout.cue).normalized()
        val cut = incoming.dot(outgoing)
        val after = (pocket.center - bounce).normalized()
        if (cut < 0.15f || predictor.predict(layout.cue, incoming, layout.balls).targetBallId != layout.target.id ||
            !geometry.reachesBounce(layout.target.point, bounce, layout.obstacles) ||
            !reachesPocket(bounce + after * 0.0002f, after, pocket, layout.obstacles)) return null
        val distance = (ghost - layout.cue).length() + (bounce - layout.target.point).length() + (pocket.center - bounce).length()
        return candidate(incoming, distance, cut, layout.target.id, ShotKind.BANK_POT)
    }

    private fun kickPot(layout: TargetLayout, face: ReboundGeometry.Face, pocket: PocketDefinition): ShotCandidate? {
        val outgoing = (pocket.center - layout.target.point).normalized()
        val ghost = layout.target.point - outgoing * (2f * TableGeometry.BALL_RADIUS)
        val bounce = face.bounce(layout.cue, ghost) ?: return null
        val incoming = (ghost - bounce).normalized()
        val cut = incoming.dot(outgoing)
        if (cut < 0.15f || !geometry.reachesBounce(layout.cue, bounce, layout.balls) ||
            geometry.afterBounce(bounce, incoming, layout.balls).targetBallId != layout.target.id ||
            !reachesPocket(layout.target.point, outgoing, pocket, layout.obstacles)) return null
        val distance = (bounce - layout.cue).length() + (ghost - bounce).length() + (pocket.center - layout.target.point).length()
        return candidate((bounce - layout.cue).normalized(), distance, cut, layout.target.id, ShotKind.KICK_POT)
    }

    private fun shortlist(banks: List<ShotCandidate>, kicks: List<ShotCandidate>): List<ShotCandidate> {
        val rankedBanks = banks.sortedBy { it.rankingCost }
        val rankedKicks = kicks.sortedBy { it.rankingCost }
        // Alternate families so a large bank list cannot starve kicks, or vice versa.
        return buildList {
            for (index in 0 until maxOf(rankedBanks.size, rankedKicks.size)) {
                rankedBanks.getOrNull(index)?.let(::add)
                rankedKicks.getOrNull(index)?.let(::add)
            }
        }.distinctBy { it.shot to it.kind }.take(8)
    }

    private fun reachesPocket(origin: Point, direction: Point, pocket: PocketDefinition, balls: List<AimBall>): Boolean {
        val path = predictor.predict(origin, direction, balls)
        return path.targetBallId == null && (path.end - origin).length() + 0.005f >= (pocket.mouthCenter - origin).length()
    }

    private fun candidate(direction: Point, distance: Float, cut: Float, target: Int, kind: ShotKind): ShotCandidate {
        val impulse = (0.22f + 0.20f * sqrt(distance / (cut * cut))).coerceIn(OpponentTuning.MIN_IMPULSE, OpponentTuning.MAX_IMPULSE)
        return ShotCandidate(ShotCommand(direction, impulse), distance + (1f - cut) * 3f, target, kind)
    }
}
