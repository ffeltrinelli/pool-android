package io.github.ffeltrinelli.pool.view

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.input.CuePhase
import io.github.ffeltrinelli.pool.model.GameSession
import io.github.ffeltrinelli.pool.model.Player
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.TableGeometry
import io.github.ffeltrinelli.pool.model.CueGeometry
import kotlin.math.*

data class ViewTuning(
    val elevationDegrees: Float = 45f,
    val fieldOfView: Float = 60f,
    val distance: Float = 0.95f,
    val near: Float = 0.01f,
    val overviewNear: Float = 0.25f,
    val far: Float = 20f,
    val aimingRadius: Float = 0.30f,
    val pullbackSeconds: Float = 0.65f,
    val returnSeconds: Float = 0.45f,
    val overviewDistanceScale: Float = 0.80f,
    val overviewHorizontalLimit: Float = 0.96f,
    val overviewTopLimit: Float = 0.76f,
    val overviewBottomLimit: Float = 0.92f
)

/** Each view owns projection and screen-input interpretation; the stroke interpreter is shared. */
interface GameView {
    val camera: Camera
    fun resize(width: Int, height: Int)
    fun update(cueBall: Point?, direction: Point, canAim: Boolean, deltaSeconds: Float = 0f)
    fun beginInput(x: Int, y: Int, cueBall: Point, direction: Point): Point
    fun moveInput(x: Int, y: Int, phase: CuePhase, direction: Point): Point
    fun cueLength(tip: Point, direction: Point): Float
}

private fun gestureWorldWidth(width: Int, height: Int) = max(1.92f, 3.5f * width / height.coerceAtLeast(1))

class TopView : GameView {
    override val camera = OrthographicCamera()
    private var width = 420
    private var height = 933

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        val aspect = this.width.toFloat() / this.height
        val margin = 0.08f
        val fittedWidth = max(2 * TableGeometry.OUTER_HALF_WIDTH + margin,
            (2 * TableGeometry.OUTER_HALF_LENGTH + margin) * aspect)
        camera.viewportWidth = fittedWidth
        camera.viewportHeight = camera.viewportWidth * this.height / this.width
        camera.position.set(0f, 4f, 0f)
        camera.direction.set(0f, -1f, 0f)
        camera.up.set(0f, 0f, -1f)
        camera.near = 0.1f
        camera.far = 10f
        camera.update()
    }

    override fun update(cueBall: Point?, direction: Point, canAim: Boolean, deltaSeconds: Float) = Unit
    override fun beginInput(x: Int, y: Int, cueBall: Point, direction: Point) = tablePoint(x, y)
    override fun moveInput(x: Int, y: Int, phase: CuePhase, direction: Point) = tablePoint(x, y)

    private fun tablePoint(x: Int, y: Int): Point {
        return Point((x.toFloat() / width - 0.5f) * camera.viewportWidth,
            (y.toFloat() / height - 0.5f) * camera.viewportHeight)
    }

    override fun cueLength(tip: Point, direction: Point): Float {
        var length = CueGeometry.DISPLAY_LENGTH
        if (abs(direction.x) > 0.001f) length = min(length, (camera.viewportWidth / 2 - 0.03f + sign(direction.x) * tip.x) / abs(direction.x))
        if (abs(direction.z) > 0.001f) length = min(length, (camera.viewportHeight / 2 - 0.13f + sign(direction.z) * tip.z) / abs(direction.z))
        return length.coerceAtLeast(0.04f)
    }
}

class PlayerView(private val tuning: ViewTuning = ViewTuning()) : GameView {
    override val camera = PerspectiveCamera(tuning.fieldOfView, 420f, 933f)
    private var metresPerPixel = 1.92f / 420f
    private var target = Point(0f, 0.58f)
    private var heading = Point(0f, -1f)
    private var overviewProgress = 0f
    private var lastX = 0
    private var lastY = 0
    private var inputOrigin = target
    private var inputPosition = target
    private var inputAngle = 0f
    private var inputRadius = tuning.aimingRadius

    init { placeCamera() }

    override fun resize(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        metresPerPixel = gestureWorldWidth(w, h) / w
        camera.viewportWidth = w.toFloat()
        camera.viewportHeight = h.toFloat()
        placeCamera()
    }

    override fun update(cueBall: Point?, direction: Point, canAim: Boolean, deltaSeconds: Float) {
        if (canAim && cueBall != null) {
            target = cueBall
            heading = direction
        }
        val delta = if (deltaSeconds.isFinite()) deltaSeconds.coerceAtLeast(0f) else 0f
        overviewProgress = if (canAim) (overviewProgress - delta / tuning.returnSeconds).coerceAtLeast(0f)
            else (overviewProgress + delta / tuning.pullbackSeconds).coerceAtMost(1f)
        placeCamera()
    }

    private fun placeCamera() {
        val radians = Math.toRadians(tuning.elevationDegrees.toDouble())
        val forward = Vector3(heading.x * cos(radians).toFloat(), -sin(radians).toFloat(), heading.z * cos(radians).toFloat())
        val blend = overviewProgress * overviewProgress * (3f - 2f * overviewProgress)
        val aimTarget = Vector3(target.x, TableGeometry.BALL_RADIUS, target.z)
        val overviewTarget = Vector3(0f, TableGeometry.BALL_RADIUS, 0f)
        val distance = (tuning.distance / cos(radians).toFloat()) * (1f - blend) + overviewDistance(forward) * blend
        val lookAt = aimTarget.lerp(overviewTarget, blend)
        camera.position.set(lookAt).mulAdd(forward, -distance)
        camera.direction.set(forward)
        camera.up.set(Vector3.Y)
        camera.normalizeUp()
        camera.near = tuning.near + (tuning.overviewNear - tuning.near) * blend
        camera.far = tuning.far
        camera.update()
    }

    private fun overviewDistance(forward: Vector3): Float {
        val right = forward.cpy().crs(Vector3.Y).nor()
        val up = right.cpy().crs(forward).nor()
        val verticalTangent = tan(Math.toRadians(tuning.fieldOfView.toDouble()) / 2).toFloat()
        val horizontalTangent = verticalTangent * camera.viewportWidth / camera.viewportHeight
        var distance = 0f
        // Start from a table-relative fit, then move closer for larger balls; outer edges may leave the screen.
        for (x in listOf(-0.76f, 0.76f)) for (z in listOf(-1.32f, 1.32f)) for (y in listOf(-0.13f, 0.06f)) {
            val offset = Vector3(x, y - TableGeometry.BALL_RADIUS, z)
            val depth = offset.dot(forward)
            val vertical = offset.dot(up)
            distance = max(distance, abs(offset.dot(right)) / (horizontalTangent * tuning.overviewHorizontalLimit) - depth)
            distance = max(distance, abs(vertical) / (verticalTangent * if (vertical >= 0f) tuning.overviewTopLimit else tuning.overviewBottomLimit) - depth)
        }
        return distance * tuning.overviewDistanceScale
    }

    fun reset() { overviewProgress = 0f; placeCamera() }

    override fun beginInput(x: Int, y: Int, cueBall: Point, direction: Point): Point {
        lastX = x
        lastY = y
        inputOrigin = cueBall
        inputAngle = atan2(direction.x, -direction.z)
        inputRadius = tuning.aimingRadius
        inputPosition = cueBall - direction * inputRadius
        return inputPosition
    }

    override fun moveInput(x: Int, y: Int, phase: CuePhase, direction: Point): Point {
        val sideways = (x - lastX) * metresPerPixel
        val backward = (y - lastY) * metresPerPixel
        lastX = x
        lastY = y
        if (phase == CuePhase.STROKING) {
            inputPosition -= direction * backward
        } else {
            inputAngle += sideways / tuning.aimingRadius
            inputRadius += backward
            val aim = Point(sin(inputAngle), -cos(inputAngle))
            inputPosition = inputOrigin - aim * inputRadius
        }
        return inputPosition
    }

    override fun cueLength(tip: Point, direction: Point): Float {
        // Clip against the camera's near plane, not the overhead viewport rectangle.
        val front = camera.direction
        val depth = Vector3(tip.x, TableGeometry.BALL_RADIUS, tip.z).sub(camera.position).dot(front)
        val rearwardDepth = direction.x * front.x + direction.z * front.z
        if (rearwardDepth <= 0.001f) return CueGeometry.DISPLAY_LENGTH
        return ((depth - camera.near - 0.02f) / rearwardDepth).coerceIn(0.04f, CueGeometry.DISPLAY_LENGTH)
    }
}

class GameViews {
    val replay = ReplayCamera()
    private val top = TopView()
    private val player = PlayerView()
    var active: GameView = top
        private set

    fun follow(session: GameSession) {
        val next = if (session.placement != null || session.player == Player.COMPUTER) top else player
        if (active !== next && next === player) player.reset()
        active = next
    }

    fun reset() = player.reset()
    fun resize(width: Int, height: Int) { top.resize(width, height); player.resize(width, height) }
    fun update(cueBall: Point?, direction: Point, canAim: Boolean, deltaSeconds: Float = 0f) {
        player.update(cueBall, direction, canAim, deltaSeconds)
    }
}
