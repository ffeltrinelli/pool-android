package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class EightBallPlacementSearchTest {
    @Test fun `hard ball in hand finds a winning eight instead of preferring the break spot`() {
        val planner = BulletShotPlanner(maxEvaluations = 64, deadlineMillis = Long.MAX_VALUE)
        try {
            val layouts = listOf(Point(0f, 0f), Point(0.3f, 0.2f), Point(-0.3f, -0.65f), Point(0.42f, 0.85f))
                .map { listOf(BallSnapshot(8, it)) } + listOf(listOf(BallSnapshot(8, Point(0.1f, 0.2f)),
                    BallSnapshot(9, Point(0.35f, 0.2f)), BallSnapshot(10, Point(-0.2f, -0.3f))))
            for (balls in layouts) {
                val point = balls.first { it.id == 8 }.point
                val table = TableSnapshot(balls,
                    RulesContext(balls.map { it.id }.toSet(), BallGroup.SOLIDS), PlacementRegion.ANYWHERE)
                val plan = planner.plan(table, 17) { false }
                val facts = ShotFacts()
                val live = PoolPhysics(events = facts::accept)
                try {
                    live.restore(table.withCue(plan.cuePosition!!))
                    live.strike(ExecutionAccuracy.apply(plan.shot, OpponentDifficulty.HARD, 17))
                    repeat(3600) { live.step() }
                    assertTrue("eight=$point placement=${plan.cuePosition} shot=${plan.shot} pockets=${facts.pocketed} foul=${EightBallRules.resolve(table.rulesContext(), facts).foul}",
                        EightBallRules.resolve(table.rulesContext(), facts).won)
                } finally { live.dispose() }
            }
        } finally { planner.close() }
    }
}
