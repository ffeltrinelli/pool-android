package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

interface ShotPlanningService : AutoCloseable {
    fun start(table: TableSnapshot, seed: Long)
    /** Consumes a completed result once; returns null while pending or after cancellation. */
    fun poll(): PlanningResult?
    fun cancel()
    override fun close()
}

/** The worker owns native resources, including their final disposal. No game objects cross threads. */
class WorkerShotPlanningService(
    private val planner: ComputerShotPlanner = BulletShotPlanner(),
    private val reportFailure: (Throwable) -> Unit = {}
) : ShotPlanningService {
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "pool-shot-planner").apply { isDaemon = true } }
    private val generation = AtomicLong()
    private data class Request(val future: Future<PlanningResult>, val fallback: PlanningResult)
    private var pending: Request? = null
    // Retain one immutable request for read-only diagnostics even when replay recording is disabled.
    private var lastRequest: TableSnapshot? = null
    override fun start(table: TableSnapshot, seed: Long) {
        cancel()
        lastRequest = table
        val fallback = PlanningPositions.fallback(table)
        val token = generation.get()
        val future = executor.submit<PlanningResult> { planner.plan(table, seed) { generation.get() != token } }
        pending = Request(future, fallback)
    }
    override fun poll(): PlanningResult? {
        val request = pending ?: return null
        if (!request.future.isDone) return null
        pending = null
        return try { request.future.get() } catch (failure: ExecutionException) {
            reportFailure(failure.cause ?: failure)
            request.fallback
        } catch (_: CancellationException) { null }
    }
    override fun cancel() {
        generation.incrementAndGet()
        pending?.future?.cancel(false)
        pending = null
    }
    override fun close() {
        cancel()
        executor.submit { planner.close() }
        executor.shutdown()
    }
}

