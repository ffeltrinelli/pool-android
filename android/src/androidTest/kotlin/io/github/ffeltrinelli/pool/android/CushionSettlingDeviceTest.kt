package io.github.ffeltrinelli.pool.android

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.PhysicsEvent
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.ShotCommand
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CushionSettlingDeviceTest {
    @Test fun onePlusResidualCushionMotionSettles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.startActivitySync(Intent(instrumentation.targetContext, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Gdx.app.postRunnable {
            val events = mutableListOf<PhysicsEvent>()
            val physics = PoolPhysics(events::add)
            try {
                physics.reset(listOf(0 to Point(0.22541456f, -0.5845344f), 11 to Point(-0.49207595f, -1.0942622f)))
                physics.strike(ShotCommand(Point(0f, -1f), 0.0001f))
                val ball = physics.balls.first { it.id == 11 }
                ball.body.worldTransform = Matrix4().set(
                    Vector3(-0.49207595f, 0.028532963f, -1.0942622f),
                    Quaternion(-0.539429f, 0.115831055f, 0.58890116f, 0.5905885f))
                ball.body.linearVelocity = Vector3(-0.008917803f, -0.00047525018f, 0.000060406965f)
                ball.body.angularVelocity = Vector3(-0.000084969666f, 0.0016494485f, 0.31223032f)
                repeat(1200) { physics.step() }
                assertEquals(1, events.count { it == PhysicsEvent.Settled })
                assertFalse(ball.pocketed)
                assertFalse(ball.body.isActive)
                val settledPosition = ball.position.cpy()
                repeat(120) { physics.step() }
                assertEquals(settledPosition, ball.position)
                assertEquals(1, events.count { it == PhysicsEvent.Settled })
                physics.strike(ShotCommand(Point(1f, 0f), 0.17f))
                physics.step()
                assertTrue(physics.balls.first { it.id == 0 }.body.isActive)
            } catch (error: Throwable) { failure.set(error) }
            finally { physics.dispose(); done.countDown() }
        }
        assertTrue("Cushion fixture timed out", done.await(15, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Cushion settling fixture failed", it) }
    }
}
