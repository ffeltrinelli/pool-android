package io.github.ffeltrinelli.pool

import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.opponent.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.*

class ExecutionAccuracyTest {
    @Test fun `Hard preserves the exact planned command including float components`() {
        val shot = ShotCommand(Point(0.6f, -0.8f), 0.317f)
        for (seed in 0L..100L) assertSame(shot, ExecutionAccuracy.apply(shot, OpponentDifficulty.HARD, seed))
    }

    @Test fun `Medium retains the accepted seeded casual calculation`() {
        val shot = ShotCommand(Point(0.6f, -0.8f), 0.317f)
        for (seed in 0L..100L) {
            val random = Random(seed)
            val error = (random.nextGaussian() * 0.25).coerceIn(-0.75, 0.75)
            val angle = atan2(shot.direction.x.toDouble(), -shot.direction.z.toDouble()) + Math.toRadians(error)
            val impulse = (shot.impulse * (1 + (random.nextDouble() * 2 - 1) * 0.03)).toFloat()
            val accepted = ShotCommand(Point(sin(angle).toFloat(), -cos(angle).toFloat()), impulse.coerceIn(0.04f, 1.15f))
            assertEquals(accepted, ExecutionAccuracy.apply(shot, OpponentDifficulty.MEDIUM, seed))
        }
    }

    @Test fun `seeded profiles stay symmetric bounded and within legal impulse limits`() {
        for (level in OpponentDifficulty.entries) {
            val angularErrors = mutableListOf<Double>()
            val powerErrors = mutableListOf<Double>()
            val seeds = Random(7319)
            repeat(4096) {
                val seed = seeds.nextLong()
                val shot = ShotCommand(Point(0f, -1f), 0.5f)
                val executed = ExecutionAccuracy.apply(shot, level, seed)
                assertEquals(executed, ExecutionAccuracy.apply(shot, level, seed))
                val angle = Math.toDegrees(atan2(executed.direction.x.toDouble(), -executed.direction.z.toDouble()))
                val power = executed.impulse / shot.impulse - 1.0
                assertTrue(abs(angle) <= level.maxAngleErrorDegrees + 0.00001)
                assertTrue(abs(power) <= level.powerVariation + 0.000001)
                assertEquals(1f, executed.direction.length(), 0.000001f)
                angularErrors += angle
                powerErrors += power
                for (impulse in listOf(0.04f, 1.15f)) {
                    assertTrue(ExecutionAccuracy.apply(shot.copy(impulse = impulse), level, seed).impulse in 0.04f..1.15f)
                }
            }
            assertEquals(0.0, angularErrors.average(), 0.03)
            assertEquals(0.0, powerErrors.average(), 0.003)
            if (level != OpponentDifficulty.HARD) {
                assertTrue(angularErrors.any { it < 0 } && angularErrors.any { it > 0 })
                assertTrue(powerErrors.any { it < 0 } && powerErrors.any { it > 0 })
            }
        }
    }

    @Test fun `search effort and visible thinking retain shared tuning`() {
        assertEquals(5000L, OpponentTuning.DEADLINE_MILLIS)
        assertEquals(64, OpponentTuning.EVALUATIONS)
        assertEquals(1f, OpponentTuning.THINK_SECONDS, 0f)
    }
}
