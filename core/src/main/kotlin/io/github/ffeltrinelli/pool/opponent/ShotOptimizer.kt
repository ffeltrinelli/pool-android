package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import org.apache.commons.math3.exception.TooManyEvaluationsException
import org.apache.commons.math3.optim.*
import org.apache.commons.math3.optim.nonlinear.scalar.*
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer
import org.apache.commons.math3.random.Well19937c
import kotlin.math.*

internal object ShotOptimizer {
    fun evaluateSeeds(seeds: List<BulletShotPlanner.Seed>, search: ShotSearch) {
        for (seed in seeds.distinct()) {
            if (search.remaining <= 0) return
            val candidate = seed.candidate
            search.evaluate(candidate.shot, seed.cuePosition, candidate.targetBallId, candidate.kind)
        }
    }

    fun refine(seeds: List<BulletShotPlanner.Seed>, search: ShotSearch, randomSeed: Long) {
        // A CMA-ES start needs its initial evaluation plus a population of six.
        // Shortlist starts instead of leaving a small budget unused across too many seeds.
        val starts = seeds.take(search.remaining / 7)
        for ((index, seed) in starts.withIndex()) {
            search.checkBudget()
            val budget = search.remaining / (starts.size - index)
            if (budget < 7) return
            val candidate = seed.candidate
            val angle = atan2(candidate.shot.direction.x.toDouble(), -candidate.shot.direction.z.toDouble())
            val optimizer = CMAESOptimizer(64, Double.POSITIVE_INFINITY, true, 0, 10, Well19937c(randomSeed + index), false, null)
            try {
                optimizer.optimize(MaxEval(budget), GoalType.MAXIMIZE,
                    InitialGuess(doubleArrayOf(angle, candidate.shot.impulse.toDouble())),
                    SimpleBounds(doubleArrayOf(angle - 0.14, OpponentTuning.MIN_IMPULSE.toDouble()),
                        doubleArrayOf(angle + 0.14, OpponentTuning.MAX_IMPULSE.toDouble())),
                    CMAESOptimizer.Sigma(doubleArrayOf(0.035, 0.12)), CMAESOptimizer.PopulationSize(6),
                    ObjectiveFunction { point ->
                        search.evaluate(ShotCommand(Point(sin(point[0]).toFloat(), -cos(point[0]).toFloat()), point[1].toFloat()),
                            seed.cuePosition, candidate.targetBallId, candidate.kind, TrialOrigin.REFINEMENT)
                    })
            } catch (_: TooManyEvaluationsException) { /* Best completed result belongs to the request. */ }
        }
    }
}
