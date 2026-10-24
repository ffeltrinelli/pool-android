package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.PhysicsEvent
import io.github.ffeltrinelli.pool.model.PocketGeometry
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.physics.TableSnapshot

/** One worker owns this silent world. Only complete, settled simulations produce a score. */
internal class ShotSimulation : AutoCloseable {
    private var settled = false
    private var scratch = false
    private var pocketed = 0
    private val world = PoolPhysics(events = ::accept)

    fun evaluate(table: TableSnapshot, shot: ShotCommand, checkBudget: () -> Unit): ShotEvaluation? {
        world.restore(table)
        settled = false
        scratch = false
        pocketed = 0
        world.strike(shot)
        var steps = 0
        val maximumSteps = (OpponentTuning.SIMULATION_SECONDS / PoolPhysics.STEP).toInt()
        while (!settled && steps++ < maximumSteps) {
            checkBudget()
            world.step()
        }
        if (!settled) return null
        val closest = world.balls.filter { it.id != 0 && !it.pocketed }.minOfOrNull { ball ->
            PocketGeometry.pockets.minOf { (it.mouthCenter - ball.point).length() }
        } ?: 0f
        return ShotEvaluation(pocketed, scratch, closest, shot.impulse)
    }

    private fun accept(event: PhysicsEvent) {
        when (event) {
            PhysicsEvent.Settled -> settled = true
            is PhysicsEvent.Pocketed -> if (event.ballId == 0) scratch = true else pocketed++
        }
    }

    override fun close() = world.dispose()
}
