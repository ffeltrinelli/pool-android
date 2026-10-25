package io.github.ffeltrinelli.pool.view

import io.github.ffeltrinelli.pool.replay.ReplayClip

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.model.*
import kotlin.math.*

/** Camera position is a pure function of clip time, shared by playback and export. */
class ReplayCamera {
    val camera = PerspectiveCamera(60f, 420f, 933f)
    fun update(clip: ReplayClip, seconds: Float, width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        val frame = clip.frameAt(seconds)
        val cue = frame.balls.first { it.id == 0 }
        val heading = frame.cue?.direction ?: clip.frameAt(clip.strikeSeconds).cue?.direction
            ?: clip.frames.lastOrNull { it.cue != null }!!.cue!!.direction
        val blendTime = ((seconds - clip.contactSeconds) / (PULLBACK_SECONDS * clip.timeScale)).coerceIn(0f, 1f)
        val blend = blendTime * blendTime * (3f - 2f * blendTime)
        val right = Vector3(-heading.z, 0f, heading.x)
        val closeTarget = Vector3(cue.x, cue.y, cue.z)
        val close = closeTarget.cpy().add(-heading.x * CLOSE_DISTANCE, CLOSE_HEIGHT, -heading.z * CLOSE_DISTANCE).mulAdd(right, LATERAL_OFFSET)
        val forward = Vector3(heading.x, -1f, heading.z).nor()
        val up = right.cpy().crs(forward).nor()
        val vTan = tan(Math.toRadians(30.0)).toFloat()
        val hTan = vTan * width / height
        var distance = 0f
        for (x in listOf(-TableGeometry.OUTER_HALF_WIDTH, TableGeometry.OUTER_HALF_WIDTH))
            for (z in listOf(-TableGeometry.OUTER_HALF_LENGTH, TableGeometry.OUTER_HALF_LENGTH))
                for (y in listOf(-0.15f, 0.08f)) {
                    val offset = Vector3(x, y - TableGeometry.BALL_RADIUS, z)
                    distance = max(distance, abs(offset.dot(right)) / (hTan * 0.92f) - offset.dot(forward))
                    distance = max(distance, abs(offset.dot(up)) / (vTan * 0.92f) - offset.dot(forward))
                }
        val center = Vector3(0f, TableGeometry.BALL_RADIUS, 0f)
        camera.position.set(close).lerp(center.cpy().mulAdd(forward, -distance), blend)
        val target = closeTarget.lerp(center, blend)
        camera.up.set(Vector3.Y)
        camera.lookAt(target)
        // Pull the near plane out with the camera to preserve precision along the rails.
        camera.near = CLOSE_NEAR + (OVERVIEW_NEAR - CLOSE_NEAR) * blend
        camera.far = 20f
        camera.update()
    }
    companion object {
        const val PULLBACK_SECONDS = 0.65f
        private const val CLOSE_NEAR = 0.01f
        private const val OVERVIEW_NEAR = 0.25f
        private const val CLOSE_DISTANCE = 0.32f
        private const val CLOSE_HEIGHT = 0.10f
        private const val LATERAL_OFFSET = 0.045f
    }
}
