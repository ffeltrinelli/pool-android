package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class ReboundPlanningTest {
    private val blocked = PlanningBenchmarkTest.layouts().getValue("blocked")

    @Test fun `blocked fallback contacts a stripe after one cushion instead of striking the blocking solid`() {
        val fallback = ShotCandidates().fallback(blocked)
        val balls = blocked.balls.map { AimBall(it.id, it.point) }
        assertNull(AimPredictor().predict(blocked.balls.first { it.id == 0 }.point, fallback.direction, balls).targetBallId)
        assertEquals(9, ReboundGeometry().firstContact(Point(0f, 0.3f), fallback.direction, balls, ShotKind.KICK_CONTACT))
        val simulation = ShotSimulation()
        try {
            val evaluation = simulation.evaluate(blocked, fallback) {}!!
            assertFalse("Fallback must never hit the blocking solid first", 1 in evaluation.firstContacts)
        } finally { simulation.close() }
    }

    @Test fun `assignment by either player reaches the next computer snapshot with stripes only`() {
        fun shoot(session: GameSession, target: Int, pots: List<Int> = emptyList()) {
            if (session.placement != null) session.confirmPlacement()
            session.beginShot()
            session.accept(PhysicsEvent.Contacts(setOf(target), setOf(1, 2, 3, 4)))
            pots.forEach { session.accept(PhysicsEvent.Pocketed(it)) }
            session.accept(PhysicsEvent.Settled)
        }
        for (assigner in Player.entries) {
            val session = GameSession()
            shoot(session, 1) // Legal dry break hands the open table to the computer.
            if (assigner == Player.HUMAN) shoot(session, 1)
            shoot(session, if (assigner == Player.COMPUTER) 9 else 1, listOf(if (assigner == Player.COMPUTER) 9 else 1))
            if (assigner == Player.HUMAN) shoot(session, 2)
            assertEquals(Player.COMPUTER, session.player)
            assertEquals(BallGroup.STRIPES, session.context().group)
            assertFalse(8 in session.context().targets)
            assertTrue(session.context().targets.all { it in 9..15 })
            val table = blocked.copy(balls = blocked.balls.map { if (it.id == 9) it.copy(id = 10) else it }, rules = session.context())
            val rebounds = ReboundCandidates().generate(table)
            assertTrue(rebounds.isNotEmpty())
            assertTrue(rebounds.all { it.targetBallId in 9..15 })
            val planner = BulletShotPlanner(32, Long.MAX_VALUE)
            try {
                val result = planner.plan(table, 17) { false }
                assertEquals(setOf(10), result.predictedFirstContacts)
                assertNull(result.predictedFoul)
            } finally { planner.close() }
            assertTrue(ShotCandidates().generate(table).all { it.targetBallId in 9..15 })
        }
    }

    @Test fun `silent physics preserves rule facts and trajectories while omitting audio work`() {
        val audibleFacts = ShotFacts()
        val silentFacts = ShotFacts()
        val audible = PoolPhysics(events = audibleFacts::accept, impacts = {})
        val silent = PoolPhysics(events = silentFacts::accept)
        try {
            audible.restore(blocked)
            silent.restore(blocked)
            val shot = ShotCommand(Point(0.8f, -0.6f), 0.7f)
            audible.strike(shot); silent.strike(shot)
            repeat(3600) { audible.step(); silent.step() }
            assertEquals(audibleFacts.firstContacts, silentFacts.firstContacts)
            assertEquals(audibleFacts.railBalls, silentFacts.railBalls)
            assertEquals(audibleFacts.pocketed, silentFacts.pocketed)
            audible.balls.zip(silent.balls).forEach { (a, b) -> assertTrue(a.position.dst(b.position) < 0.00001f) }
        } finally { audible.dispose(); silent.dispose() }
    }

    @Test fun `both illegal eight and opponent group are rejected without advancing physics`() {
        for (blocker in listOf(1, 8)) {
            val table = blocked.copy(balls = blocked.balls.filter { it.id != 8 }.map {
                if (it.id == 1) it.copy(id = blocker) else it
            })
            val simulation = ShotSimulation()
            try {
                val shot = ShotCommand(Point(0f, -1f), 0.7f)
                val search = ShotSearch(table, ShotCandidates().fallback(table), simulation, 4, Long.MAX_VALUE, { false })
                assertEquals(ShotSearch.REJECTED_SCORE, search.evaluate(shot), 0.0)
                assertEquals(0, search.result().evaluations)
                assertEquals(0L, search.result().metrics.physicsSteps)
                assertEquals(1, search.result().metrics.rejected)
            } finally { simulation.close() }
        }
    }

    @Test fun `blocked planner finds a legal kick and retains direct pots when available`() {
        val planner = BulletShotPlanner(64, Long.MAX_VALUE)
        val simulation = ShotSimulation()
        try {
            val kick = planner.plan(blocked, 17) { false }
            assertTrue(kick.metrics.reboundAttempts > 0)
            assertEquals(setOf(9), kick.predictedFirstContacts)
            assertNull(kick.predictedFoul)
            assertNull(simulation.evaluate(blocked, kick.shot) {}!!.foul)
            val direct = planner.plan(PlanningBenchmarkTest.layouts().getValue("direct"), 17) { false }
            assertEquals(ShotKind.DIRECT_POT, direct.kind)
            assertEquals(0, direct.metrics.reboundAttempts)
            assertTrue(9 in direct.predictedPockets)
        } finally { simulation.close(); planner.close() }
    }

    @Test fun `mirror faces exclude pocket openings and reflect back into the playing surface`() {
        val geometry = ReboundGeometry()
        assertEquals(6, geometry.faces.size)
        for (face in geometry.faces) {
            val bounce = face.bounce(Point(0f, 0.2f), Point(0.1f, -0.3f)) ?: continue
            val incoming = (bounce - Point(0f, 0.2f)).normalized()
            assertTrue(incoming.dot(face.normal) < 0f)
            assertTrue(face.reflect(incoming).dot(face.normal) > 0f)
            assertTrue(geometry.reachesBounce(Point(0f, 0.2f), bounce, emptyList()))
        }
        assertNull(geometry.faces.first().bounce(Point(0f, 0f), geometry.faces.first().a))
    }

    @Test fun `single cushion bank and kick pot seeds produce intended legal pots in Bullet`() {
        val simulation = ShotSimulation()
        try {
            val found = mutableSetOf<ShotKind>()
            for (table in PlanningBenchmarkTest.layouts().values.filter { it.placement == null }) {
                for (candidate in ReboundCandidates().generate(table).filter { it.kind.isPot && it.kind !in found }) {
                    val search = ShotSearch(table, candidate.shot, simulation, 128, Long.MAX_VALUE, { false })
                    val start = BulletShotPlanner.Seed(candidate, table.balls.first { it.id == 0 }.point)
                    ShotOptimizer.evaluateSeeds(listOf(start), search)
                    ShotOptimizer.refine(listOf(start), search, 17)
                    val result = search.result()
                    if (result.predictedFoul == null && candidate.targetBallId in result.predictedPockets) {
                        val outcome = simulation.evaluate(table, result.shot) {}!!
                        if (candidate.kind == ShotKind.BANK_POT) assertTrue(candidate.targetBallId in outcome.bankedTargets)
                        else assertEquals(1, outcome.cueRailsBeforeContact)
                        found += candidate.kind
                    }
                }
            }
            assertTrue("Successful rebound kinds: $found", ShotKind.BANK_POT in found)
            assertTrue("Successful rebound kinds: $found", ShotKind.KICK_POT in found)
        } finally { simulation.close() }
    }

    @Test fun `refinement cannot replace a successful seed merely for lower impulse`() {
        val table = PlanningBenchmarkTest.layouts().getValue("direct")
        val candidate = ShotCandidates().generate(table).first()
        val simulation = ShotSimulation()
        try {
            val initial = simulation.evaluate(table, candidate.shot) {}!!
            val weaker = listOf(0.95f, 0.9f, 0.8f, 0.7f).map { candidate.shot.copy(impulse = candidate.shot.impulse * it) }.first {
                val outcome = simulation.evaluate(table, it) {}!!
                outcome.foul == null && outcome.pocketedTargets == initial.pocketedTargets &&
                    candidate.targetBallId in outcome.pocketedTargets && outcome.scoreFor(candidate.targetBallId) > initial.scoreFor(candidate.targetBallId)
            }
            val search = ShotSearch(table, candidate.shot, simulation, 2, Long.MAX_VALUE, { false })
            search.evaluate(candidate.shot, targetBallId = candidate.targetBallId)
            search.evaluate(weaker, targetBallId = candidate.targetBallId, origin = TrialOrigin.REFINEMENT)
            assertEquals(candidate.shot, search.result().shot)
        } finally { simulation.close() }
    }

    @Test fun `exact repeated trial reuses simulation but obeys the request attempt limit`() {
        val table = PlanningBenchmarkTest.layouts().getValue("direct")
        val candidate = ShotCandidates().generate(table).first()
        val simulation = ShotSimulation()
        try {
            val search = ShotSearch(table, candidate.shot, simulation, 2, Long.MAX_VALUE, { false })
            val score = search.evaluate(candidate.shot, targetBallId = candidate.targetBallId)
            assertEquals(score, search.evaluate(candidate.shot, targetBallId = candidate.targetBallId), 0.0)
            assertEquals(1, search.result().evaluations)
            assertEquals(1, search.result().metrics.cacheHits)
            try { search.evaluate(candidate.shot); fail("Expected attempt limit") } catch (_: ShotSearch.AttemptsReached) { }
        } finally { simulation.close() }
    }

    @Test fun `direct attempt quota and interrupted rollout retain the completed legal shot`() {
        val table = PlanningBenchmarkTest.layouts().getValue("direct")
        val candidate = ShotCandidates().generate(table).first()
        val simulation = ShotSimulation()
        try {
            val quota = ShotSearch(table, candidate.shot, simulation, 4, Long.MAX_VALUE, { false })
            quota.limitDirectPhase()
            repeat(2) { quota.evaluate(candidate.shot, kind = ShotKind.CONTACT) }
            try { quota.evaluate(candidate.shot); fail("Expected direct quota") } catch (_: ShotSearch.DirectPhaseEnded) { }
            assertEquals(candidate.shot, quota.result().shot)
            quota.startRebounds()
            quota.evaluate(candidate.shot)
            assertEquals(3, quota.result().metrics.attempts)

            var now = 0L
            var ticking = false
            val interrupted = ShotSearch(table, candidate.shot, simulation, 16, 5000, { false }, 0L,
                { if (ticking) now += 500_000_000L; now })
            interrupted.limitDirectPhase()
            interrupted.evaluate(candidate.shot, kind = ShotKind.CONTACT)
            ticking = true
            try {
                interrupted.evaluate(candidate.shot.copy(impulse = candidate.shot.impulse + 0.01f))
                fail("Expected interruption during the next rollout")
            } catch (_: ShotSearch.DirectPhaseEnded) { }
            assertEquals(1, interrupted.result().evaluations)
            assertEquals(candidate.shot, interrupted.result().shot)
        } finally { simulation.close() }
    }

    @Test fun `direct phase time and attempt boundaries preserve completed results`() {
        val table = PlanningBenchmarkTest.layouts().getValue("direct")
        val candidate = ShotCandidates().generate(table).first()
        val simulation = ShotSimulation()
        var now = 0L
        try {
            val search = ShotSearch(table, candidate.shot, simulation, 4, 5000, { false }, 0L, { now })
            search.limitDirectPhase()
            now = 2_500_000_000L
            try { search.checkBudget(); fail("Expected direct phase end") } catch (_: ShotSearch.DirectPhaseEnded) { }
            search.startRebounds()
            search.evaluate(candidate.shot, targetBallId = candidate.targetBallId)
            now = 5_000_000_000L
            try { search.checkBudget(); fail("Expected deadline") } catch (_: ShotSearch.DeadlineReached) { }
            assertEquals(1, search.result().evaluations)
            assertEquals(candidate.shot, search.result().shot)
        } finally { simulation.close() }
    }
}
