package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.rules.*
import org.junit.Assert.*
import org.junit.Test

class GameSessionTest {
    private fun shot(s: GameSession, first: Int = 1, pots: List<Int> = emptyList(), rails: Set<Int> = setOf(1, 2, 3, 4)) {
        if (s.placement != null) s.confirmPlacement()
        s.beginShot()
        s.accept(PhysicsEvent.Contacts(setOf(first), rails))
        pots.forEach { s.accept(PhysicsEvent.Pocketed(it)) }
        s.accept(PhysicsEvent.Settled)
    }
    private fun openTable() = GameSession().apply { shot(this) }

    @Test fun `startup requires placement and break does not assign groups`() {
        val s = GameSession()
        assertEquals(RoundState.PLACING, s.state)
        assertFalse(s.canShoot)
        assertEquals(Player.HUMAN, s.player)
        shot(s, pots = listOf(1))
        assertNull(s.group(Player.HUMAN))
        assertEquals(Player.HUMAN, s.player)
        assertTrue(s.canShoot)
    }
    @Test fun `legal single group pot assigns both groups and keeps turn`() {
        val s = openTable()
        shot(s, 9, listOf(9))
        assertEquals(BallGroup.STRIPES, s.group(Player.COMPUTER))
        assertEquals(BallGroup.SOLIDS, s.group(Player.HUMAN))
        assertEquals(Player.COMPUTER, s.player)
        shot(s, 10, listOf(10))
        assertEquals(5, s.remaining(Player.COMPUTER))
    }
    @Test fun `mixed pots keep open table and turn while fouls never assign`() {
        val s = openTable()
        shot(s, pots = listOf(1, 9))
        assertNull(s.group(s.player))
        assertEquals(Player.COMPUTER, s.player)
        shot(s, 2, listOf(2, 0))
        assertNull(s.group(s.player))
        assertEquals(Player.HUMAN, s.player)
        assertEquals(PlacementRegion.ANYWHERE, s.placement)
        assertEquals(FoulReason.SCRATCH, s.outcome!!.decision.foul)
    }
    @Test fun `opponent only pot stays down but ends turn without foul`() {
        val s = openTable()
        shot(s, pots = listOf(1))
        shot(s, 2, listOf(9))
        assertEquals(Player.HUMAN, s.player)
        assertFalse(s.outcome!!.foul)
        assertEquals(6, s.remaining(Player.HUMAN))
    }
    @Test fun `duplicate and idle events cannot remove balls twice`() {
        val s = openTable()
        s.accept(PhysicsEvent.Pocketed(2))
        s.beginShot()
        s.accept(PhysicsEvent.Contacts(setOf(1), emptySet()))
        repeat(2) { s.accept(PhysicsEvent.Pocketed(1)) }
        assertEquals(15, s.remaining)
        repeat(2) { s.accept(PhysicsEvent.Settled) }
        assertEquals(14, s.remaining)
    }
    @Test fun `eight is evaluated against group before shot and late scratch loses`() {
        for (scratch in listOf(false, true)) {
            val s = openTable()
            shot(s, pots = (1..6).toList())
            if (scratch) shot(s, 7, listOf(7))
            s.beginShot()
            s.accept(PhysicsEvent.Contacts(setOf(if (scratch) 8 else 7), emptySet()))
            s.accept(PhysicsEvent.Pocketed(8))
            assertFalse(s.finished)
            if (scratch) s.accept(PhysicsEvent.Pocketed(0)) else s.accept(PhysicsEvent.Pocketed(7))
            s.accept(PhysicsEvent.Settled)
            assertEquals(MatchResult.HUMAN_WINS, s.result)
        }
    }
    @Test fun `cleared group then legal eight wins and scratch without eight does not lose`() {
        val s = openTable()
        shot(s, pots = (1..7).toList())
        assertEquals(setOf(8), s.context().targets)
        shot(s, 8, listOf(8))
        assertEquals(MatchResult.COMPUTER_WINS, s.result)
        val scratch = openTable()
        shot(scratch, pots = (1..7).toList())
        shot(scratch, 8, listOf(0))
        assertFalse(scratch.finished)
        assertNotNull(scratch.placement)
    }
    @Test fun `break eight is spotted and break fouls grant restricted placement`() {
        val s = GameSession()
        shot(s, pots = listOf(8, 0))
        assertTrue(s.outcome!!.decision.spotEight)
        assertFalse(s.finished)
        assertEquals(15, s.remaining)
        assertEquals(PlacementRegion.BEHIND_HEAD_STRING, s.placement)
    }
    @Test fun `requested racks alternate independently from insufficient break reracks`() {
        val s = GameSession()
        shot(s, rails = emptySet())
        assertTrue(s.outcome!!.decision.rerack)
        s.rerackAfterInsufficientBreak()
        assertEquals(Player.COMPUTER, s.player)
        s.restart()
        assertEquals(Player.COMPUTER, s.player)
        s.restart()
        assertEquals(Player.HUMAN, s.player)
        assertEquals(RoundState.PLACING, s.state)
        assertNull(s.outcome)
        assertNull(s.group(s.player))
    }
}
