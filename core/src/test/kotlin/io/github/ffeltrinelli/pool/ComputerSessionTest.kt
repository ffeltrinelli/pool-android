package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import org.junit.Assert.*
import org.junit.Test

class ComputerSessionTest {
    private fun shot(session: GameSession, balls: List<Int> = emptyList()) {
        session.beginShot()
        balls.forEach { session.accept(PhysicsEvent.Pocketed(it)) }
        session.accept(PhysicsEvent.Settled)
    }
    @Test fun `human breaks and turns alternate even on a successful pot`() {
        val s = GameSession()
        assertTrue(s.humanTurn)
        shot(s, listOf(1, 8))
        assertEquals(2, s.humanScore)
        assertEquals(Player.COMPUTER, s.player)
        assertFalse(s.humanTurn)
        shot(s)
        assertTrue(s.humanTurn)
    }
    @Test fun `points wait for settling and duplicate or idle events do not score`() {
        val s = GameSession()
        s.accept(PhysicsEvent.Pocketed(1))
        assertEquals(15, s.remaining)
        s.beginShot()
        repeat(2) { s.accept(PhysicsEvent.Pocketed(1)) }
        assertEquals(0, s.humanScore)
        s.accept(PhysicsEvent.Settled)
        s.accept(PhysicsEvent.Settled)
        assertEquals(1, s.humanScore)
        assertEquals(Player.COMPUTER, s.player)
    }
    @Test fun `scratch by either player removes objects without scoring and passes turn`() {
        for (computerFouls in listOf(false, true)) {
            val s = GameSession()
            if (computerFouls) shot(s)
            val shooter = s.player
            shot(s, listOf(1, 2, 0, 0))
            assertEquals(13, s.remaining)
            assertEquals(0, s.humanScore + s.computerScore)
            assertEquals(shooter.opponent, s.player)
            assertEquals(ShotOutcome(shooter, 2, true), s.outcome)
            assertFalse(s.finished)
            shot(s, listOf(3))
            assertEquals(1, s.humanScore + s.computerScore)
        }
    }
    @Test fun `final scratch cancels only that shot and can produce a draw`() {
        val s = GameSession()
        shot(s, (1..15).toList() + 0)
        assertEquals(MatchResult.DRAW, s.result)
        assertTrue(s.finished)
        assertEquals(0, s.humanScore)
    }
    @Test fun `winner follows scores instead of the player clearing the table`() {
        val humanWins = GameSession()
        shot(humanWins, (1..8).toList())
        shot(humanWins, (9..15).toList())
        assertEquals(MatchResult.HUMAN_WINS, humanWins.result)
        val computerWins = GameSession()
        shot(computerWins, listOf(1))
        shot(computerWins, (2..15).toList())
        assertEquals(MatchResult.COMPUTER_WINS, computerWins.result)
    }
    @Test fun `restart resets scores shooter and outcomes`() {
        val s = GameSession()
        shot(s, listOf(1, 2))
        s.restart()
        assertTrue(s.humanTurn)
        assertEquals(0, s.humanScore)
        assertEquals(15, s.remaining)
        assertNull(s.outcome)
    }
}
