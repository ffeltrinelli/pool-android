package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.*
import org.junit.Assert.*
import org.junit.Test

class BreakSettlingTest {
    @Test fun `live OnePlus cloth seam position eventually hands over`() {
        val events = mutableListOf<PhysicsEvent>()
        val physics = PoolPhysics(events::add)
        try {
            physics.restore(TableSnapshot(listOf(
                BallSnapshot(0, Point(0f, 0.58f)),
                BallSnapshot(13, Point(-0.501586676f, -0.933050632f), 0.028491512f,
                    0.235934064f, 0.752796531f, -0.055885263f, 0.611971557f)
            )))
            physics.strike(ShotCommand(Point(0f, -1f), 0.001f))
            repeat(7200) { physics.step() }
            assertEquals("Seam ball: " + physics.balls.last().body.angularVelocity, 1, events.count { it == PhysicsEvent.Settled })
        } finally { physics.dispose() }
    }
    @Test fun `break hands over after balls stop without persistent contact jitter`() {
        for (impulse in listOf(0.3f, 0.65f, 1.15f)) {
            val events = mutableListOf<PhysicsEvent>()
            val physics = PoolPhysics(events::add)
            try {
                physics.strike(ShotCommand(Point(0f, -1f), impulse))
                repeat(7200) { physics.step() }
                assertEquals("Impulse $impulse: " + physics.balls.filter { !it.pocketed }.joinToString { "${it.id}: v=${it.body.linearVelocity}, w=${it.body.angularVelocity}, p=${it.position}" }, 1, events.count { it == PhysicsEvent.Settled })
            } finally { physics.dispose() }
        }
    }
}
