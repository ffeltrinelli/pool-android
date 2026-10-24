package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.PhysicsEvent
import io.github.ffeltrinelli.pool.rules.*
import io.github.ffeltrinelli.pool.model.PocketGeometry
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.physics.TableSnapshot

/** One worker owns this silent world. Only complete, settled simulations produce a score. */
internal class ShotSimulation : AutoCloseable {
    private var settled = false
    private var facts = ShotFacts()
    private val world = PoolPhysics(events = ::accept)

    fun evaluate(table: TableSnapshot, shot: ShotCommand, checkBudget: () -> Unit): ShotEvaluation? {
        world.restore(table)
        settled = false
        facts = ShotFacts()
        world.strike(shot)
        var steps = 0
        val maximumSteps = (OpponentTuning.SIMULATION_SECONDS / PoolPhysics.STEP).toInt()
        while (!settled && steps++ < maximumSteps) {
            checkBudget()
            world.step()
        }
        if (!settled) return null
        val closest = world.balls.filter { it.id in table.rulesContext().targets && !it.pocketed }.minOfOrNull { ball ->
            PocketGeometry.pockets.minOf { (it.mouthCenter - ball.point).length() }
        } ?: 0f
        val decision = EightBallRules.resolve(table.rulesContext(), facts)
        val targets = table.rulesContext().targets
        return ShotEvaluation(facts.pocketed.count { it in targets }, decision.foul, closest, shot.impulse,
            decision.won, decision.loss != null || decision.rerack)
    }

    private fun accept(event: PhysicsEvent) {
        facts.accept(event)
        if (event == PhysicsEvent.Settled) settled = true
    }

    override fun close() = world.dispose()
}
