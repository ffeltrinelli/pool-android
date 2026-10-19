package io.github.ffeltrinelli.pool.input

import io.github.ffeltrinelli.pool.model.*
import kotlin.math.abs

/** Positions use table coordinates; timestamps are monotonic seconds. */
data class PointerSample(val pointer: Int, val position: Point, val seconds: Double)
data class GestureTuning(
    val deadZone: Float = 0.055f,
    val strokeThreshold: Float = 0.014f,
    val contactTravel: Float = 0.032f,
    val sideRatio: Float = 0.30f,
    val velocityWindow: Double = 0.075,
    val impulsePerSpeed: Float = 0.48f,
    val maximumImpulse: Float = 1.15f
)
enum class CuePhase { IDLE, AIMING, STROKING, FIRED }

class CueGesture(private val tuning: GestureTuning = GestureTuning()) {
    var phase = CuePhase.IDLE
        private set
    var direction = Point(0f, -1f)
        private set
    var travel = 0f
        private set
    val tipGap: Float get() = (tuning.contactTravel - travel).coerceAtLeast(0f)
    private var activePointer: Int? = null
    private var origin = Point(0f, 0f)
    private var previous: PointerSample? = null
    private var candidateTravel = 0f
    private var candidateSamples = 0
    private val history = ArrayDeque<PointerSample>()

    fun begin(sample: PointerSample, cueBall: Point) {
        if (activePointer != null || (sample.position - cueBall).length() < tuning.deadZone) return
        activePointer = sample.pointer
        origin = cueBall
        direction = (cueBall - sample.position).normalized()
        previous = sample
        phase = CuePhase.AIMING
        travel = 0f
        candidateTravel = 0f
        candidateSamples = 0
        history.clear()
        history.add(sample)
    }

    fun move(sample: PointerSample): ShotCommand? {
        if (sample.pointer != activePointer || phase == CuePhase.FIRED) return null
        val last = previous ?: return null
        if (sample.seconds <= last.seconds) return null
        val delta = sample.position - last.position
        val forward = delta.dot(direction)
        val sideways = abs(delta.x * direction.z - delta.z * direction.x)
        history.add(sample)
        while (history.size > 2 && history.first().seconds < sample.seconds - tuning.velocityWindow) history.removeFirst()
        if (phase == CuePhase.AIMING) {
            if (forward > 0f && sideways <= forward * tuning.sideRatio) {
                candidateTravel += forward
                candidateSamples++
                if (candidateSamples >= 2 && candidateTravel >= tuning.strokeThreshold) {
                    phase = CuePhase.STROKING
                    travel = candidateTravel
                }
            } else {
                candidateTravel = 0f
                candidateSamples = 0
                if ((origin - sample.position).length() >= tuning.deadZone) {
                    direction = (origin - sample.position).normalized()
                }
            }
        } else if (phase == CuePhase.STROKING) {
            travel = (travel + forward).coerceAtLeast(0f)
        }
        previous = sample
        if (phase != CuePhase.STROKING || travel < tuning.contactTravel || forward <= 0f) return null
        val oldest = history.first()
        val seconds = (sample.seconds - oldest.seconds).coerceAtLeast(0.001)
        val speed = ((sample.position - oldest.position).dot(direction) / seconds).toFloat().coerceAtLeast(0f)
        if (speed <= 0f) return null
        phase = CuePhase.FIRED
        return ShotCommand(direction, (speed * tuning.impulsePerSpeed).coerceAtMost(tuning.maximumImpulse))
    }

    fun end(pointer: Int) {
        if (pointer == activePointer) cancel()
    }

    fun cancel() {
        activePointer = null
        previous = null
        history.clear()
        phase = CuePhase.IDLE
        travel = 0f
        candidateTravel = 0f
        candidateSamples = 0
    }
}
