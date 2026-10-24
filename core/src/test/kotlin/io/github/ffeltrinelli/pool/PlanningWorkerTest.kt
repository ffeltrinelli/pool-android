package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlanningWorkerTest {
    private val table = TableSnapshot(listOf(BallSnapshot(0, Point(0f, 0.4f)), BallSnapshot(1, Point(0f, 0f))))
    @Test fun `cancelled work cannot leak into a later request and native owner closes on worker`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var workerName = ""
        val planner = object : ComputerShotPlanner {
            override fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult {
                workerName = Thread.currentThread().name
                if (seed == 1L) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                return PlanningResult(ShotCommand(Point(1f, 0f), seed.toFloat() / 10), 1, 0, false)
            }
            override fun close() { assertEquals(workerName, Thread.currentThread().name); closed.countDown() }
        }
        val service = WorkerShotPlanningService(planner)
        try {
            service.start(table, 1)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            service.cancel()
            service.start(table, 2)
            release.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var result: PlanningResult? = null
            while (result == null && System.nanoTime() < deadline) { result = service.poll(); Thread.yield() }
            assertEquals(0.2f, requireNotNull(result).shot.impulse, 0f)
            assertEquals("pool-shot-planner", workerName)
        } finally { release.countDown(); service.close() }
        assertTrue(closed.await(5, TimeUnit.SECONDS))
    }
    @Test fun `planner failure is reported and delivers one fallback`() {
        val planner = object : ComputerShotPlanner {
            override fun plan(table: TableSnapshot, seed: Long, cancelled: () -> Boolean): PlanningResult = error("test failure")
            override fun close() = Unit
        }
        var failure: Throwable? = null
        val service = WorkerShotPlanningService(planner) { failure = it }
        try {
            service.start(table, 1)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var result: PlanningResult? = null
            while (result == null && System.nanoTime() < deadline) { result = service.poll(); Thread.yield() }
            assertTrue(requireNotNull(result).usedFallback)
            assertEquals("test failure", failure!!.message)
            assertNull(service.poll())
        } finally { service.close() }
    }
}
