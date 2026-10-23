package io.github.ffeltrinelli.pool.opponent

import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.ShotCommand
import java.util.Random
import kotlin.math.*

enum class OpponentDifficulty(
    val label: String,
    val angleDeviationDegrees: Double,
    val maxAngleErrorDegrees: Double,
    val powerVariation: Double
) {
    EASY("Easy", 1.2, 3.6, 0.14),
    MEDIUM("Medium", 0.25, 0.75, 0.03),
    HARD("Hard", 0.0, 0.0, 0.0)
}

/** Execution only: the selected plan and search budget are independent of difficulty. */
object ExecutionAccuracy {
    fun apply(shot: ShotCommand, difficulty: OpponentDifficulty, seed: Long): ShotCommand {
        if (difficulty == OpponentDifficulty.HARD) return shot
        val random = Random(seed)
        val error = (random.nextGaussian() * difficulty.angleDeviationDegrees)
            .coerceIn(-difficulty.maxAngleErrorDegrees, difficulty.maxAngleErrorDegrees)
        val angle = atan2(shot.direction.x.toDouble(), -shot.direction.z.toDouble()) + Math.toRadians(error)
        val impulse = (shot.impulse * (1 + (random.nextDouble() * 2 - 1) * difficulty.powerVariation)).toFloat()
        return ShotCommand(Point(sin(angle).toFloat(), -cos(angle).toFloat()),
            impulse.coerceIn(OpponentTuning.MIN_IMPULSE, OpponentTuning.MAX_IMPULSE))
    }
}
