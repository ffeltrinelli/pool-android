package io.github.ffeltrinelli.pool

import com.badlogic.gdx.Preferences
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.stats.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class MatchStatisticsTest {
    private class Store : StatisticsPreference {
        var saved = MatchStatistics()
        var saves = 0
        override fun load() = saved
        override fun save(statistics: MatchStatistics) { saved = statistics; saves++ }
    }

    @Test fun `counts wins and losses under completion difficulty and saves immediately`() {
        val store = Store()
        val tracker = MatchStatisticsTracker(store)
        tracker.recordCompletion(null, OpponentDifficulty.EASY)
        assertEquals(0, store.saves)
        tracker.recordCompletion(MatchResult.HUMAN_WINS, OpponentDifficulty.HARD)
        tracker.beginRack()
        tracker.recordCompletion(MatchResult.COMPUTER_WINS, OpponentDifficulty.HARD)
        assertEquals(MatchTotals(2, 1), tracker.snapshot[OpponentDifficulty.HARD])
        assertEquals(MatchTotals(), tracker.snapshot[OpponentDifficulty.EASY])
        assertEquals(2, store.saves)
        assertEquals(MatchTotals(2, 1), MatchStatisticsTracker(store).snapshot[OpponentDifficulty.HARD])
    }

    @Test fun `duplicate completion and reset after completion never count the same rack again`() {
        val store = Store()
        val tracker = MatchStatisticsTracker(store)
        tracker.recordCompletion(MatchResult.HUMAN_WINS, OpponentDifficulty.MEDIUM)
        val beforeReset = tracker.snapshot
        repeat(3) { tracker.recordCompletion(MatchResult.HUMAN_WINS, OpponentDifficulty.EASY) }
        assertEquals(1, store.saves)
        tracker.reset()
        tracker.recordCompletion(MatchResult.HUMAN_WINS, OpponentDifficulty.MEDIUM)
        assertEquals(2, store.saves)
        assertEquals(MatchTotals(), MatchStatisticsTracker(store).snapshot[OpponentDifficulty.MEDIUM])
        assertEquals(MatchTotals(1, 1), beforeReset[OpponentDifficulty.MEDIUM])
        tracker.beginRack()
        tracker.recordCompletion(MatchResult.COMPUTER_WINS, OpponentDifficulty.EASY)
        assertEquals(MatchTotals(1, 0), tracker.snapshot[OpponentDifficulty.EASY])
    }

    @Test fun `unfinished restarted and insufficient break racks have no result to record`() {
        val tracker = MatchStatisticsTracker(Store())
        val session = GameSession()
        repeat(2) {
            session.confirmPlacement()
            session.beginShot()
            session.accept(PhysicsEvent.Settled)
            tracker.recordCompletion(session.result, OpponentDifficulty.EASY)
            session.rerackAfterInsufficientBreak()
            tracker.beginRack()
        }
        session.restart()
        tracker.beginRack()
        tracker.recordCompletion(session.result, OpponentDifficulty.HARD)
        for (difficulty in OpponentDifficulty.entries) assertEquals(MatchTotals(), tracker.snapshot[difficulty])
    }

    @Test fun `opponent losing foul is recorded as a human win only after settlement`() {
        val session = GameSession()
        session.confirmPlacement()
        session.beginShot()
        session.accept(PhysicsEvent.Contacts(setOf(1), setOf(1, 2, 3, 4)))
        session.accept(PhysicsEvent.Settled)
        assertEquals(Player.COMPUTER, session.player)
        session.beginShot()
        session.accept(PhysicsEvent.Pocketed(8))
        val tracker = MatchStatisticsTracker(Store())
        tracker.recordCompletion(session.result, OpponentDifficulty.EASY)
        session.accept(PhysicsEvent.Settled)
        tracker.recordCompletion(session.result, OpponentDifficulty.HARD)
        assertEquals(MatchTotals(), tracker.snapshot[OpponentDifficulty.EASY])
        assertEquals(MatchTotals(1, 1), tracker.snapshot[OpponentDifficulty.HARD])
    }

    @Test fun `percentage rounds without decimals and empty rows show zero`() {
        assertEquals(0, MatchTotals().winPercentage)
        assertEquals(33, MatchTotals(3, 1).winPercentage)
        assertEquals(67, MatchTotals(3, 2).winPercentage)
        assertEquals(13, MatchTotals(8, 1).winPercentage)
        assertEquals(100, MatchTotals(7, 7).winPercentage)
        assertEquals(67, MatchTotals(3_000_000_000, 2_000_000_000).winPercentage)
    }

    @Test fun `preferences reload counters for every difficulty and persist reset in one flush`() {
        val values = mutableMapOf<String, Long>()
        var flushes = 0
        val preferences = Proxy.newProxyInstance(Preferences::class.java.classLoader, arrayOf(Preferences::class.java)) { proxy, method, args ->
            when (method.name) {
                "getLong" -> values[args[0]] ?: args[1]
                "putLong" -> { values[args[0] as String] = args[1] as Long; proxy }
                "flush" -> { flushes++; null }
                else -> error("Unexpected preference call ${method.name}")
            }
        } as Preferences
        val store = SavedStatisticsPreference(preferences)
        for (difficulty in OpponentDifficulty.entries) assertEquals(MatchTotals(), store.load()[difficulty])
        val rows = OpponentDifficulty.entries.associateWith { MatchTotals(3_000_000_000, it.ordinal.toLong()) }
        store.save(MatchStatistics(rows))
        for (difficulty in OpponentDifficulty.entries) assertEquals(rows[difficulty], SavedStatisticsPreference(preferences).load()[difficulty])
        assertEquals(1, flushes)
        MatchStatisticsTracker(store).reset()
        for (difficulty in OpponentDifficulty.entries) assertEquals(MatchTotals(), SavedStatisticsPreference(preferences).load()[difficulty])
        assertEquals(2, flushes)
    }
}
