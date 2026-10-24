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
        val session = GameSession()
        assertTrue(session.humanTurn)
        shot(session, listOf(1, 8))
        assertEquals(2, session.humanScore)
        assertEquals(Player.COMPUTER, session.player)
        assertFalse(session.humanTurn)
        shot(session)
        assertTrue(session.humanTurn)
    }
    @Test fun `points wait for settling and duplicate or idle events do not score`() {
        val session = GameSession()
        session.accept(PhysicsEvent.Pocketed(1))
        assertEquals(15, session.remaining)
        session.beginShot()
        repeat(2) { session.accept(PhysicsEvent.Pocketed(1)) }
        assertEquals(0, session.humanScore)
        session.accept(PhysicsEvent.Settled)
        session.accept(PhysicsEvent.Settled)
        assertEquals(1, session.humanScore)
        assertEquals(Player.COMPUTER, session.player)
    }
    @Test fun `scratch by either player removes objects without scoring and passes turn`() {
        for (computerFouls in listOf(false, true)) {
            val session = GameSession()
            if (computerFouls) shot(session)
            val shooter = session.player
            shot(session, listOf(1, 2, 0, 0))
            assertEquals(13, session.remaining)
            assertEquals(0, session.humanScore + session.computerScore)
            assertEquals(shooter.opponent, session.player)
            assertEquals(ShotOutcome(shooter, 2, true), session.outcome)
            assertFalse(session.finished)
            shot(session, listOf(3))
            assertEquals(1, session.humanScore + session.computerScore)
        }
    }
    @Test fun `final scratch cancels only that shot and can produce a draw`() {
        val session = GameSession()
        shot(session, (1..15).toList() + 0)
        assertEquals(MatchResult.DRAW, session.result)
        assertTrue(session.finished)
        assertEquals(0, session.humanScore)
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
        val session = GameSession()
        shot(session, listOf(1, 2))
        session.restart()
        assertTrue(session.humanTurn)
        assertEquals(0, session.humanScore)
        assertEquals(15, session.remaining)
        assertNull(session.outcome)
    }
}
