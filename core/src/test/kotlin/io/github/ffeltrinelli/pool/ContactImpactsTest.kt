package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.ContactImpacts
import org.junit.Assert.*
import org.junit.Test

class ContactImpactsTest {
    @Test fun `multiple contact points combine once and persistent contacts stay silent`() {
        val events = mutableListOf<Impact>()
        val contacts = ContactImpacts(events::add)
        contacts.contact(0, 1, 0.1f); contacts.contact(1, 0, 0.2f); contacts.finishStep()
        contacts.contact(0, 1, 0.4f); contacts.finishStep()
        assertEquals(1, events.size)
        assertEquals(ImpactKind.BALL, events.single().kind)
        assertEquals(0.3f, events.single().impulse, 0.00001f)
        contacts.finishStep()
        contacts.contact(1, 0, 0.2f); contacts.finishStep()
        assertEquals(2, events.size)
    }

    @Test fun `different cushions have independent contacts and reset forgets previous pairs`() {
        val events = mutableListOf<Impact>()
        val contacts = ContactImpacts(events::add)
        contacts.contact(-1, 0, 0.1f); contacts.finishStep()
        contacts.contact(-2, 0, 0.2f); contacts.finishStep()
        assertEquals(2, events.size)
        assertTrue(events.all { it.kind == ImpactKind.CUSHION })
        contacts.reset()
        contacts.contact(-2, 0, 0.2f); contacts.finishStep()
        assertEquals(3, events.size)
    }

    @Test fun `pocket sensors cloth unknown bodies and negligible impacts are silent`() {
        val events = mutableListOf<Impact>()
        val contacts = ContactImpacts(events::add)
        contacts.contact(100, 0, 2f); contacts.contact(1, 101, 2f)
        contacts.contact(200, 0, 2f); contacts.contact(-99, 1, 2f)
        contacts.contact(0, 1, 0.0001f); contacts.finishStep()
        assertTrue(events.isEmpty())
    }
}
