package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ComputerPhysicsTest {
    @Test fun `settled snapshot preserves rotation and reproduces trajectories within tolerance`() {
        val a = PoolPhysics(); val b = PoolPhysics()
        try {
            a.reset(listOf(0 to Point(0f, 0.4f), 1 to Point(0f, 0.1f)))
            repeat(100) { a.step() }
            val snapshot = a.snapshot()
            b.restore(snapshot)
            snapshot.balls.zip(b.snapshot().balls).forEach { (first, second) ->
                assertEquals(first.point, second.point)
                assertEquals(first.height, second.height, 0.000001f)
                assertEquals(first.qx, second.qx, 0.000001f)
                assertEquals(first.qw, second.qw, 0.000001f)
            }
            val shot = ShotCommand(Point(0f, -1f), 0.2f)
            a.strike(shot); b.strike(shot)
            repeat(300) { a.step(); b.step() }
            for ((first, second) in a.balls.zip(b.balls)) {
                assertTrue("Ball ${first.id}: live=${first.position}, restored=${second.position}, distance=${first.position.dst(second.position)}", first.position.dst(second.position) < 0.01f)
                assertEquals(first.pocketed, second.pocketed)
            }
        } finally { a.dispose(); b.dispose() }
    }
    @Test fun `cue replacement finds nearest clear starting half location and preserves objects`() {
        val p = PoolPhysics()
        try {
            p.reset(listOf(1 to Point(0f, 0.58f), 2 to Point(0.1f, 0.6f)))
            repeat(100) { p.step() }
            val objects = p.snapshot().balls
            p.replaceCueBall()
            assertEquals(objects, p.snapshot().balls.filter { it.id != 0 })
            val cue = p.balls.first { it.id == 0 }
            assertTrue(cue.point.z > 0)
            assertTrue((cue.point - Point(0f, 0.58f)).length() < 0.065f)
            assertTrue(p.balls.filter { it.id != 0 }.all { (it.point - cue.point).length() > TableGeometry.BALL_RADIUS * 2 })
            repeat(10) { p.replaceCueBall() }
            assertEquals(3, p.balls.size)
            assertEquals(1, p.balls.count { it.id == 0 })
        } finally { p.dispose() }
        assertEquals(Point(0f, 0.58f), CueReplacement.position(emptyList()))
    }
    @Test fun `ghost ball candidates include a straight pot and reject an obstructed path`() {
        val generator = ShotCandidates()
        val clear = TableSnapshot(listOf(BallSnapshot(0, Point(0f, 0f)), BallSnapshot(1, Point(0.3f, 0f))))
        assertTrue(generator.generate(clear).any { it.shot.direction.x > 0.999f })
        val blocked = clear.copy(balls = clear.balls + BallSnapshot(2, Point(0.15f, 0f)))
        assertFalse(generator.generate(blocked).any { it.shot.direction.x > 0.999f })
        assertTrue(generator.fallback(blocked).impulse in 0.04f..1.15f)
    }
    @Test fun `seeded optimization is reproducible bounded and uses a separate silent world`() {
        val live = PoolPhysics()
        val planner = BulletShotPlanner(maxEvaluations = 24, deadlineMillis = Long.MAX_VALUE, variation = false)
        try {
            live.reset(listOf(0 to Point(0f, 0f), 1 to Point(0.3f, 0f)))
            val snapshot = live.snapshot()
            val first = planner.plan(snapshot, 123) { false }
            val second = planner.plan(snapshot, 123) { false }
            assertEquals(snapshot, live.snapshot())
            assertEquals(first.shot, second.shot)
            assertTrue(first.evaluations in 7..24)
            assertFalse(first.usedFallback)
            assertTrue(first.shot.impulse in 0.04f..1.15f)
            var pocket = false
            var scratch = false
            val check = PoolPhysics(events = {
                if (it == PhysicsEvent.Pocketed(1)) pocket = true
                if (it == PhysicsEvent.Pocketed(0)) scratch = true
            })
            try {
                check.restore(snapshot); check.strike(first.shot)
                repeat(3600) { check.step() }
                assertTrue("The isolated straight pot must be found", pocket)
                assertFalse("A safe pot should be preferred to scratching", scratch)
            } finally { check.dispose() }
        } finally { planner.close(); live.dispose() }
    }
    @Test fun `scoring cancels foul points and prefers safe shots before proximity`() {
        val scratch = ShotEvaluation(5, true, 0f, 0.04f).score
        val safeMiss = ShotEvaluation(0, false, 3f, 1.15f).score
        val safePot = ShotEvaluation(1, false, 3f, 1.15f).score
        assertTrue(safeMiss > scratch)
        assertTrue(safePot > ShotEvaluation(0, false, 0f, 0.04f).score)
        assertTrue(ShotEvaluation(1, false, 1f, 0.1f).score > ShotEvaluation(1, false, 1f, 1f).score)
    }
    @Test fun `native simulation checks cancellation during physics stepping and remains reusable`() {
        val planner = BulletShotPlanner(deadlineMillis = Long.MAX_VALUE, maxEvaluations = 8)
        val table = TableSnapshot(listOf(BallSnapshot(0, Point(0f, 0f)), BallSnapshot(1, Point(0.3f, 0f))))
        var checks = 0
        try {
            try { planner.plan(table, 1) { ++checks >= 20 }; fail("Expected cancellation during rollout") }
            catch (_: CancellationException) { }
            assertTrue(checks >= 20)
            assertTrue(planner.plan(table, 1) { false }.evaluations in 1..8)
        } finally { planner.close() }
    }
    @Test fun `deadline returns a legal fallback and cancellation never returns a shot`() {
        val planner = BulletShotPlanner(deadlineMillis = 0)
        val table = TableSnapshot(listOf(BallSnapshot(0, Point(0f, 0.4f)), BallSnapshot(1, Point(0f, 0f))))
        try {
            val result = planner.plan(table, 1) { false }
            assertTrue(result.usedFallback)
            assertEquals(0, result.evaluations)
            assertTrue(result.shot.impulse in 0.04f..1.15f)
            try { planner.plan(table, 1) { true }; fail("Expected cancellation") } catch (_: CancellationException) { }
        } finally { planner.close() }
    }
}
