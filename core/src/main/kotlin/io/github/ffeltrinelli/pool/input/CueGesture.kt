package io.github.ffeltrinelli.pool.input

import io.github.ffeltrinelli.pool.model.*
import kotlin.math.abs
import kotlin.math.sign

/** Positions use table coordinates; timestamps are monotonic seconds. */
data class PointerSample(val pointer: Int, val position: Point, val seconds: Double)
data class GestureTuning(
    val deadZone: Float = 0.055f,
    val strokeThreshold: Float = 0.014f,
    val initialTipGap: Float = 0.032f,
    val maximumTipGap: Float = 0.25f,
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
    var tipGap = tuning.initialTipGap
        private set
    private var activePointer: Int? = null
    private var origin = Point(0f, 0f)
    private var previous: PointerSample? = null
    private var candidateTravel = 0f
    private var candidateSamples = 0
    private val forwardHistory = ArrayDeque<PointerSample>()

    fun begin(sample: PointerSample, cueBall: Point) {
        if (activePointer != null || (sample.position - cueBall).length() < tuning.deadZone) return
        activePointer = sample.pointer
        origin = cueBall
        direction = (cueBall - sample.position).normalized()
        previous = sample
        phase = CuePhase.AIMING
        resetStroke()
    }

    fun move(sample: PointerSample): ShotCommand? {
        if (sample.pointer != activePointer || phase == CuePhase.FIRED) return null
        val last = previous ?: return null
        if (sample.seconds <= last.seconds) return null
        val delta = sample.position - last.position
        val forward = delta.dot(direction)
        val sideways = abs(delta.x * direction.z - delta.z * direction.x)
        if (phase == CuePhase.AIMING && sideways > abs(forward) * tuning.sideRatio) {
            resetStroke()
            if ((origin - sample.position).length() >= tuning.deadZone) {
                direction = (origin - sample.position).normalized()
            }
        } else {
            updateForwardHistory(last, sample, forward)
            tipGap = (tipGap - forward).coerceIn(0f, tuning.maximumTipGap)
            if (phase == CuePhase.AIMING) detectStroke(forward)
        }
        previous = sample
        if (phase != CuePhase.STROKING || tipGap > 0f || forward <= 0f) return null
        val speed = forwardSpeed(sample)
        if (speed <= 0f) return null
        phase = CuePhase.FIRED
        return ShotCommand(direction, (speed * tuning.impulsePerSpeed).coerceAtMost(tuning.maximumImpulse))
    }

    private fun detectStroke(forward: Float) {
        if (forward == 0f) return
        if (sign(forward) != sign(candidateTravel)) {
            candidateTravel = 0f
            candidateSamples = 0
        }
        candidateTravel += forward
        candidateSamples++
        if (candidateSamples >= 2 && abs(candidateTravel) >= tuning.strokeThreshold) phase = CuePhase.STROKING
    }

    private fun updateForwardHistory(last: PointerSample, sample: PointerSample, forward: Float) {
        if (forward <= 0f) {
            forwardHistory.clear()
            return
        }
        if (forwardHistory.isEmpty()) forwardHistory.add(last)
        forwardHistory.add(sample)
        val cutoff = sample.seconds - tuning.velocityWindow
        // Retain one sample before the window so its boundary can be interpolated.
        while (forwardHistory.size > 2 && forwardHistory[1].seconds <= cutoff) forwardHistory.removeFirst()
    }

    private fun forwardSpeed(sample: PointerSample): Float {
        val oldest = forwardHistory.firstOrNull() ?: return 0f
        val start = maxOf(oldest.seconds, sample.seconds - tuning.velocityWindow)
        val next = forwardHistory[1]
        val fraction = ((start - oldest.seconds) / (next.seconds - oldest.seconds)).toFloat()
        val startPosition = oldest.position + (next.position - oldest.position) * fraction
        return ((sample.position - startPosition).dot(direction) / (sample.seconds - start)).toFloat().coerceAtLeast(0f)
    }

    private fun resetStroke() {
        tipGap = tuning.initialTipGap
        candidateTravel = 0f
        candidateSamples = 0
        forwardHistory.clear()
    }

    fun end(pointer: Int) {
        if (pointer == activePointer) cancel()
    }

    fun cancel() {
        activePointer = null
        previous = null
        phase = CuePhase.IDLE
        resetStroke()
    }
}
