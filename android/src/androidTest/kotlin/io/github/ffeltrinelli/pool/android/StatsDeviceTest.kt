package io.github.ffeltrinelli.pool.android

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import io.github.ffeltrinelli.pool.PoolGame
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.stats.*
import io.github.ffeltrinelli.pool.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Deterministic completion fixtures stay outside the production launcher and preserve player data. */
class StatsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun liveResultsSurviveActivityRestartAndResetDoesNotRecount() {
        val preferences = instrumentation.targetContext.getSharedPreferences("pool-statistics", 0)
        val original = preferences.all.toMap()
        var activity = launch()
        try {
            onGame(activity) { game ->
                val tracker = read(game, "statistics") as MatchStatisticsTracker
                tracker.reset()
                completeMatch(game, OpponentDifficulty.HARD, MatchResult.HUMAN_WINS)
                completeMatch(game, OpponentDifficulty.MEDIUM, MatchResult.COMPUTER_WINS)
                assertEquals(MatchTotals(1, 1), tracker.snapshot[OpponentDifficulty.HARD])
                assertEquals(MatchTotals(1, 0), tracker.snapshot[OpponentDifficulty.MEDIUM])
                val controls = read(game, "controls") as GameControls
                controls.handle(HudAction.SETTINGS)
                val layout = (read(game, "renderer") as PoolRenderer).hud.layout
                tap(layout, layout.stats)
                assertTrue(controls.statsOpen && controls.suspended)
                tap(layout, layout.difficulty)
                assertEquals(OpponentDifficulty.MEDIUM, controls.difficulty)
                Gdx.input.inputProcessor.keyDown(Input.Keys.BACK)
                assertTrue(controls.settingsPageOpen && controls.suspended)
            }
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            activity = launch()
            onGame(activity) { game ->
                val tracker = read(game, "statistics") as MatchStatisticsTracker
                assertEquals(MatchTotals(1, 1), tracker.snapshot[OpponentDifficulty.HARD])
                assertEquals(MatchTotals(1, 0), tracker.snapshot[OpponentDifficulty.MEDIUM])
                assertEquals(MatchTotals(), tracker.snapshot[OpponentDifficulty.EASY])
                completeMatch(game, OpponentDifficulty.EASY, MatchResult.HUMAN_WINS)
                val controls = read(game, "controls") as GameControls
                controls.handle(HudAction.SETTINGS)
                val layout = (read(game, "renderer") as PoolRenderer).hud.layout
                tap(layout, layout.stats)
                tap(layout, layout.statsReset)
                assertTrue(controls.statsOpen && controls.suspended)
                // Reprocessing the completed session cannot undo Reset.
                processEvents(game, listOf(PhysicsEvent.Settled))
                val reloaded = SavedStatisticsPreference(Gdx.app.getPreferences("pool-statistics")).load()
                for (difficulty in OpponentDifficulty.entries) {
                    assertEquals(MatchTotals(), tracker.snapshot[difficulty])
                    assertEquals(MatchTotals(), reloaded[difficulty])
                }
                tap(layout, layout.close)
                assertTrue(controls.settingsPageOpen && controls.suspended)
                Gdx.input.inputProcessor.keyDown(Input.Keys.BACK)
                assertFalse(controls.suspended)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            val restore = preferences.edit().clear()
            for ((key, value) in original) restore.putLong(key, value as Long)
            check(restore.commit()) { "Could not restore original statistics" }
        }
    }

    private fun launch() = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, AndroidLauncher::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ) as AndroidLauncher

    private fun onGame(activity: AndroidLauncher, check: (PoolGame) -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Gdx.app.postRunnable(object : Runnable {
            override fun run() {
                try {
                    val game = read(activity.applicationListener, "game") as? PoolGame
                    if (game == null || Gdx.input.inputProcessor == null) { Gdx.app.postRunnable(this); return }
                    check(game)
                    done.countDown()
                } catch (error: Throwable) { failure.set(error); done.countDown() }
            }
        })
        assertTrue("Game did not become ready", done.await(20, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Stats device validation failed", it) }
    }

    private fun completeMatch(game: PoolGame, difficulty: OpponentDifficulty, result: MatchResult) {
        val controls = read(game, "controls") as GameControls
        controls.handle(HudAction.RESTART)
        // Use the chosen difficulty for this fixture without overwriting the player's setting.
        controls.javaClass.getDeclaredField("difficulty").apply { isAccessible = true }.set(controls, difficulty)
        val session = read(game, "session") as GameSession
        while (session.player != Player.HUMAN) session.restart()
        session.confirmPlacement()
        session.beginShot()
        session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
        if (result == MatchResult.COMPUTER_WINS) session.accept(PhysicsEvent.Pocketed(1))
        session.accept(PhysicsEvent.Settled)
        session.beginShot()
        processEvents(game, listOf(PhysicsEvent.Pocketed(8), PhysicsEvent.Settled))
        assertEquals(result, session.result)
    }

    private fun processEvents(game: PoolGame, events: List<PhysicsEvent>) {
        @Suppress("UNCHECKED_CAST")
        val queue = read(game, "physicsEvents") as MutableList<PhysicsEvent>
        queue.addAll(events)
        game.javaClass.getDeclaredMethod("processPhysicsEvents").apply { isAccessible = true }.invoke(game)
    }

    private fun tap(layout: HudLayout, rectangle: Rectangle) {
        val x = ((rectangle.x + rectangle.width / 2) * Gdx.graphics.width / 420).toInt()
        val y = ((layout.uiHeight - rectangle.y - rectangle.height / 2) * Gdx.graphics.height / layout.uiHeight).toInt()
        Gdx.input.inputProcessor.touchDown(x, y, 0, Input.Buttons.LEFT)
        Gdx.input.inputProcessor.touchUp(x, y, 0, Input.Buttons.LEFT)
    }

    private fun read(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(owner)
    }
}
