package io.github.ffeltrinelli.pool.android

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import io.github.ffeltrinelli.pool.physics.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OpenTablePlanningDeviceTest {
    private fun onePlusOpenTable(): TableSnapshot {
        val balls = listOf(
            BallSnapshot(10, Point(0.0068604187f, -0.5186366f), 0.028574996f, -0.10273863f, 0.09957911f, -0.040309113f, 0.9888903f),
            BallSnapshot(12, Point(-0.450201f, 0.07447143f), 0.028574996f, -0.55535674f, -0.18496154f, 0.7007347f, 0.40784687f),
            BallSnapshot(13, Point(-0.17495333f, -0.45271835f), 0.028574998f, -0.0034795858f, -0.39096883f, 0.55318695f, 0.7356055f),
            BallSnapshot(5, Point(-0.23742782f, -0.54467165f), 0.028574998f, -0.5727197f, -0.43591553f, 0.29318264f, 0.6292962f),
            BallSnapshot(8, Point(-0.11259337f, -0.7063626f), 0.028574996f, -0.177728f, 0.7242016f, -0.66478026f, -0.044854976f),
            BallSnapshot(7, Point(0.059809152f, -0.6469233f), 0.028574998f, -0.56264925f, 0.03048626f, -0.02608651f, 0.82572144f),
            BallSnapshot(15, Point(-0.04806765f, 0.9683315f), 0.028574998f, -0.11233815f, 0.30812848f, 0.83945906f, 0.43329605f),
            BallSnapshot(1, Point(0.2995173f, -1.0914032f), 0.028575001f, 0.67669696f, -0.5330656f, -0.4268952f, -0.27510506f),
            BallSnapshot(2, Point(0.11619073f, -0.71221614f), 0.028574998f, 0.18673158f, -0.68928826f, 0.69330096f, -0.09667889f),
            BallSnapshot(4, Point(0.30504492f, -0.335833f), 0.028574992f, 0.22074564f, 0.27339762f, 0.9279083f, 0.12454407f),
            BallSnapshot(9, Point(-0.19514853f, -1.0910183f), 0.028575f, 0.7665845f, 0.58791894f, -0.24279359f, 0.08803852f),
            BallSnapshot(14, Point(-0.2868526f, -0.5159238f), 0.028574998f, -0.1065786f, 0.23450412f, 0.55598885f, 0.7902691f),
            BallSnapshot(3, Point(0.12227183f, -0.8236052f), 0.028575003f, 0.8768863f, -0.189184f, -0.12591356f, 0.42358664f),
            BallSnapshot(6, Point(0.24405853f, -0.3665885f), 0.028574998f, -0.5534037f, 0.78588086f, 0.12623183f, -0.24535927f),
            BallSnapshot(0, Point(-0.43709293f, -0.7585067f), 0.028575f, 0.9244012f, -0.18995921f, 0.24600938f, -0.22108205f)
        )
        return TableSnapshot(balls, RulesContext(balls.filter { it.id != 0 }.map { it.id }.toSet()))
    }

    @Test fun openTablePrefersItsIntendedPot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AndroidLauncher
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Gdx.app.postRunnable {
            val planner = BulletShotPlanner()
            val facts = ShotFacts()
            val world = PoolPhysics(events = facts::accept)
            try {
                val table = onePlusOpenTable()
                val result = planner.plan(table, 1) { false }
                assertFalse(result.usedFallback)
                assertEquals(3, result.targetBallId)
                assertTrue(result.predictedPockets.contains(3))
                world.restore(table)
                world.strike(ExecutionAccuracy.apply(result.shot, OpponentDifficulty.MEDIUM, 1))
                repeat(3600) { world.step() }
                val decision = EightBallRules.resolve(table.rulesContext(), facts)
                File(activity.filesDir, "open-table-planning.txt").writeText(
                    "$result\nMedium execution: first=${facts.firstContacts}, pockets=${facts.pocketed}, foul=${decision.foul}\n")
                assertNull(decision.foul)
                assertTrue("Medium should pocket the intended ball in this fixture: $result", 3 in facts.pocketed)
            } catch (error: Throwable) { failure.set(error) }
            finally { world.dispose(); planner.close(); done.countDown() }
        }
        assertTrue("Open-table planner timed out", done.await(20, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Open-table planning failed", it) }
    }
}
