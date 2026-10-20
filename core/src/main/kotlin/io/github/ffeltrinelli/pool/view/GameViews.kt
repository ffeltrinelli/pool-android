package io.github.ffeltrinelli.pool.view

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import io.github.ffeltrinelli.pool.input.CuePhase
import io.github.ffeltrinelli.pool.model.Point
import io.github.ffeltrinelli.pool.model.TableGeometry
import kotlin.math.*

enum class ViewMode { TOP, PLAYER }

data class ViewTuning(
    val elevationDegrees: Float = 30f,
    val fieldOfView: Float = 60f,
    val distance: Float = 0.95f,
    val near: Float = 0.01f,
    val far: Float = 20f,
    val aimingRadius: Float = 0.30f
)

/** Each view owns projection and screen-input interpretation; the stroke interpreter is shared. */
interface GameView {
    val camera: Camera
    fun resize(width: Int, height: Int)
    fun update(cueBall: Point?, direction: Point, canAim: Boolean)
    fun beginInput(x: Int, y: Int, cueBall: Point, direction: Point): Point
    fun moveInput(x: Int, y: Int, phase: CuePhase, direction: Point): Point
    fun cueLength(tip: Point, direction: Point): Float
}

private fun worldWidth(width: Int, height: Int) = max(1.92f, 3.5f * width / height.coerceAtLeast(1))

class TopView : GameView {
    override val camera = OrthographicCamera()
    private var width = 420
    private var height = 933

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        camera.viewportWidth = worldWidth(this.width, this.height)
        camera.viewportHeight = camera.viewportWidth * this.height / this.width
        camera.position.set(0f, 4f, 0f)
        camera.direction.set(0f, -1f, 0f)
        camera.up.set(0f, 0f, -1f)
        camera.near = 0.1f
        camera.far = 10f
        camera.update()
    }

    override fun update(cueBall: Point?, direction: Point, canAim: Boolean) = Unit
    override fun beginInput(x: Int, y: Int, cueBall: Point, direction: Point) = tablePoint(x, y)
    override fun moveInput(x: Int, y: Int, phase: CuePhase, direction: Point) = tablePoint(x, y)

    private fun tablePoint(x: Int, y: Int): Point {
        return Point((x.toFloat() / width - 0.5f) * camera.viewportWidth,
            (y.toFloat() / height - 0.5f) * camera.viewportHeight)
    }

    override fun cueLength(tip: Point, direction: Point): Float {
        var length = 0.65f
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
        metresPerPixel = worldWidth(w, h) / w
        camera.viewportWidth = w.toFloat()
        camera.viewportHeight = h.toFloat()
        placeCamera()
    }

    override fun update(cueBall: Point?, direction: Point, canAim: Boolean) {
        if (!canAim || cueBall == null) return
        target = cueBall
        heading = direction
        placeCamera()
    }

    private fun placeCamera() {
        camera.position.set(target.x - heading.x * tuning.distance,
            TableGeometry.BALL_RADIUS + tuning.distance * tan(Math.toRadians(tuning.elevationDegrees.toDouble())).toFloat(),
            target.z - heading.z * tuning.distance)
        camera.up.set(Vector3.Y)
        camera.lookAt(target.x, TableGeometry.BALL_RADIUS, target.z)
        camera.near = tuning.near
        camera.far = tuning.far
        camera.update()
    }

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
        if (rearwardDepth <= 0.001f) return 0.65f
        return ((depth - tuning.near - 0.02f) / rearwardDepth).coerceIn(0.04f, 0.65f)
    }
}

class GameViews {
    private val top = TopView()
    private val player = PlayerView()
    var mode = ViewMode.TOP
        private set
    val active: GameView get() = when (mode) { ViewMode.TOP -> top; ViewMode.PLAYER -> player }

    fun select(mode: ViewMode) { this.mode = mode }
    fun resize(width: Int, height: Int) { top.resize(width, height); player.resize(width, height) }
    // Keep the last aiming pose even while top view is selected, so switching during a shot is stable.
    fun update(cueBall: Point?, direction: Point, canAim: Boolean) { player.update(cueBall, direction, canAim) }
}
