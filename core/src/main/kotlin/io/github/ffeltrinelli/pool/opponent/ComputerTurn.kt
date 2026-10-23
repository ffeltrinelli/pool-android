package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.TableSnapshot
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

interface ShotPlanningService : AutoCloseable {
    fun start(table: TableSnapshot, seed: Long)
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
    private var pending: Future<PlanningResult>? = null
    private var fallback: ShotCommand? = null
    override fun start(table: TableSnapshot, seed: Long) {
        cancel()
        fallback = ShotCandidates().fallback(table)
        val token = generation.get()
        pending = executor.submit<PlanningResult> { planner.plan(table, seed) { generation.get() != token } }
    }
    override fun poll(): PlanningResult? {
        val future = pending ?: return null
        if (!future.isDone) return null
        pending = null
        return try { future.get() } catch (failure: ExecutionException) {
            reportFailure(failure.cause ?: failure)
            PlanningResult(requireNotNull(fallback), 0, 0, true)
        } catch (_: CancellationException) { null }
    }
    override fun cancel() {
        generation.incrementAndGet()
        pending?.cancel(false)
        pending = null
    }
    override fun close() {
        cancel()
        executor.submit { planner.close() }
        executor.shutdown()
    }
}

/** All timing is active game time. Pausing invalidates work and restarts an unfinished cue stroke. */
class ComputerTurn(private val planning: ShotPlanningService, private val reportPlan: (PlanningResult) -> Unit = {}) : AutoCloseable {
    private var requested = false
    private var selected: ShotCommand? = null
    private var stroke: ComputerStroke? = null
    private var thinkingSeconds = 0f
    private var seed = 0L
    var foulSeconds = 0f
        private set
    var foulPlayer: Player? = null
        private set
    val pose get() = stroke?.pose
    val thinking get() = requested || selected != null && stroke == null
    val blocksShot get() = foulSeconds > 0f

    fun foul(player: Player) {
        reset()
        foulPlayer = player
        foulSeconds = OpponentTuning.FOUL_SECONDS
    }

    fun advance(session: GameSession, table: () -> TableSnapshot, seconds: Float): ShotCommand? {
        val delta = seconds.coerceAtLeast(0f)
        if (foulSeconds > 0f) {
            foulSeconds = (foulSeconds - delta).coerceAtLeast(0f)
            if (foulSeconds == 0f) foulPlayer = null
            return null
        }
        if (!session.canShoot || session.player != Player.COMPUTER || session.mode != PlayMode.COMPUTER) return null
        if (stroke != null) {
            val shot = stroke!!.advance(delta)
            if (shot != null) { selected = null; stroke = null; thinkingSeconds = 0f }
            return shot
        }
        if (selected == null && !requested) {
            planning.start(table(), ++seed)
            requested = true
        }
        thinkingSeconds += delta
        if (requested) planning.poll()?.let { result ->
            reportPlan(result)
            selected = result.shot
            requested = false
        }
        if (selected != null && thinkingSeconds >= OpponentTuning.THINK_SECONDS) stroke = ComputerStroke(selected!!)
        return null
    }

    fun suspend() {
        planning.cancel()
        requested = false
        stroke = null
        thinkingSeconds = 0f
    }
    fun reset() {
        suspend()
        selected = null
        foulSeconds = 0f
        foulPlayer = null
    }
    override fun close() { reset(); planning.close() }
}
