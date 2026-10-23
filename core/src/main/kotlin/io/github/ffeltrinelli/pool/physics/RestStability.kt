package io.github.ffeltrinelli.pool.physics

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.TableGeometry
import kotlin.math.abs
import kotlin.math.acos

/** Recognizes bounded contact oscillation, never a timeout for a genuinely moving or spinning ball. */
internal class RestStability {
    private data class Anchor(val position: Vector3, val rotation: Quaternion)
    private val anchors = mutableMapOf<Int, Anchor>()
    private val observed = mutableSetOf<Int>()
    private var blocked = false
    private var stableSeconds = 0f
    val settled get() = stableSeconds >= PhysicsTuning.SETTLED_SECONDS

    fun beginStep() { observed.clear(); blocked = false }

    fun observe(id: Int, position: Vector3, rotation: Quaternion, linearSpeed2: Float, angularSpeed2: Float) {
        observed.add(id)
        if (abs(position.y - TableGeometry.BALL_RADIUS) > PhysicsTuning.REST_HEIGHT_TOLERANCE ||
            linearSpeed2 > PhysicsTuning.CONTACT_LINEAR_LIMIT * PhysicsTuning.CONTACT_LINEAR_LIMIT ||
            angularSpeed2 > PhysicsTuning.CONTACT_ANGULAR_LIMIT * PhysicsTuning.CONTACT_ANGULAR_LIMIT) {
            blocked = true
            return
        }
        val anchor = anchors.getOrPut(id) { Anchor(position.cpy(), rotation.cpy()) }
        val angle = 2f * acos(abs(anchor.rotation.dot(rotation)).coerceIn(0f, 1f))
        if (anchor.position.dst2(position) > PhysicsTuning.REST_POSITION_TOLERANCE * PhysicsTuning.REST_POSITION_TOLERANCE ||
            angle > PhysicsTuning.REST_ANGLE_TOLERANCE) blocked = true
    }

    fun finishStep(seconds: Float) {
        if (blocked || anchors.keys != observed) reset()
        else stableSeconds += seconds
    }

    fun reset() { anchors.clear(); stableSeconds = 0f }
}
