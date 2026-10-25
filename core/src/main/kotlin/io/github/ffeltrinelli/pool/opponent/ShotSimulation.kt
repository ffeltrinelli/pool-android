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
    private val railCounts = IntArray(16)
    private val bankedTargets = mutableSetOf<Int>()
    private var cueRailsBeforeContact = 0
    private var cachedContext: RulesContext? = null
    private var cachedTargets: Set<Int> = emptySet()

    var restoreNanos = 0L
        private set
    var simulationNanos = 0L
        private set
    var physicsSteps = 0L
        private set

    fun resetMetrics() { restoreNanos = 0; simulationNanos = 0; physicsSteps = 0 }

    fun evaluate(table: TableSnapshot, shot: ShotCommand, checkBudget: () -> Unit): ShotEvaluation? {
        val context = table.rulesContext()
        if (context != cachedContext) {
            cachedContext = context
            cachedTargets = context.targets
        }
        val targets = cachedTargets
        val restoreStarted = System.nanoTime()
        world.restore(table)
        restoreNanos += System.nanoTime() - restoreStarted
        val simulationStarted = System.nanoTime()
        settled = false
        facts = ShotFacts()
        railCounts.fill(0)
        bankedTargets.clear()
        cueRailsBeforeContact = 0
        world.strike(shot)
        var steps = 0
        val maximumSteps = (OpponentTuning.SIMULATION_SECONDS / PoolPhysics.STEP).toInt()
        try {
            while (!settled && steps++ < maximumSteps) {
                checkBudget()
                world.step()
                physicsSteps++
            }
        } finally { simulationNanos += System.nanoTime() - simulationStarted }
        if (!settled) return null
        val closest = world.balls.filter { it.id in targets && !it.pocketed }.minOfOrNull { ball ->
            PocketGeometry.pockets.minOf { (it.mouthCenter - ball.point).length() }
        } ?: 0f
        val decision = EightBallRules.resolve(context, facts)
        return ShotEvaluation(facts.pocketed.count { it in targets }, decision.foul, closest, shot.impulse,
            decision.won, decision.loss != null || decision.rerack, facts.pocketed.filter { it in targets }.toSet(), facts.firstContacts, bankedTargets.toSet(), cueRailsBeforeContact)
    }

    private fun accept(event: PhysicsEvent) {
        if (event is PhysicsEvent.Contacts) {
            if (facts.firstContacts.isEmpty() && event.cueTargets.isEmpty() && 0 in event.railBalls) cueRailsBeforeContact++
            event.railBalls.forEach { railCounts[it]++ }
        }
        if (event is PhysicsEvent.Pocketed && event.ballId != 0 && railCounts[event.ballId] == 1) bankedTargets += event.ballId
        facts.accept(event)
        if (event == PhysicsEvent.Settled) settled = true
    }

    override fun close() = world.dispose()
}
