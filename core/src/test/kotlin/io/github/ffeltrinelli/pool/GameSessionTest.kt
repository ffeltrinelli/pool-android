package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import org.junit.Assert.*
import org.junit.Test

class GameSessionTest {
    @Test fun `shots lock until settled and duplicate pockets count once`() {
        val session = GameSession()
        session.beginShot()
        assertFalse(session.canShoot)
        session.accept(PhysicsEvent.Pocketed(8))
        session.accept(PhysicsEvent.Pocketed(8))
        assertEquals(14, session.remaining)
        session.accept(PhysicsEvent.Settled)
        assertTrue(session.canShoot)
    }
    @Test fun `clear waits for settling and scratch on final shot wins`() {
        val session = GameSession()
        session.beginShot()
        (1..15).forEach { session.accept(PhysicsEvent.Pocketed(it)) }
        assertFalse(session.finished)
        session.accept(PhysicsEvent.Pocketed(0))
        session.accept(PhysicsEvent.Settled)
        assertEquals(RoundState.SCRATCHED, session.state)
    }
    @Test fun `all object balls including eight must be pocketed`() {
        val session = GameSession()
        session.beginShot()
        (1..15).filter { it != 8 }.forEach { session.accept(PhysicsEvent.Pocketed(it)) }
        session.accept(PhysicsEvent.Settled)
        assertEquals(RoundState.READY, session.state)
        session.beginShot()
        session.accept(PhysicsEvent.Pocketed(8))
        session.accept(PhysicsEvent.Settled)
        assertEquals(RoundState.CLEARED, session.state)
    }
    @Test fun `restart clears previous outcome`() {
        val session = GameSession()
        session.beginShot()
        session.accept(PhysicsEvent.Pocketed(0))
        session.accept(PhysicsEvent.Settled)
        session.restart()
        assertEquals(15, session.remaining)
        assertTrue(session.canShoot)
    }
}
