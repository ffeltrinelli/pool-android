package io.github.ffeltrinelli.pool.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.input.CueGesture
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import kotlin.math.*

class PoolRenderer : Disposable {
    private val models = mutableListOf<Model>()
    private val textures = mutableListOf<Texture>()
    private val scene = mutableListOf<ModelInstance>()
    private val modelBatch = ModelBatch()
    private val sprites = SpriteBatch()
    private val font = BitmapFont()
    private val layout = GlyphLayout()
    private val builder = ModelBuilder()
    private val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
    private val camera = OrthographicCamera()
    private val uiCamera = OrthographicCamera()
    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.60f, 0.65f, 0.68f, 1f))
        add(DirectionalLight().set(0.95f, 0.94f, 0.86f, -0.4f, -1f, -0.3f))
    }
    private val ballColors = listOf("f8f7ed", "efbc35", "397dc1", "ce5246", "8e5bb6", "df8737", "379d81", "9c3e4c", "252d36")
    private val ballModels: List<Model>
    private val ballInstances: List<ModelInstance>
    private val shadowInstances: List<ModelInstance>
    private val cueInstance: ModelInstance
    private val tipInstance: ModelInstance
    private val guideInstance: ModelInstance
    private val shadow: Model
    private val cue: Model
    private val cueTip: Model
    private val guide: Model
    private val white: Texture
    private val disc: Texture
    private val position = Vector3()
    private val rotation = Quaternion()
    var uiHeight = 933f
        private set
    private var width = 420
    private var height = 933

    init {
        white = texture(false)
        disc = texture(true)
        val wood = material("664533")
        box(0f, -0.065f, 0f, 1.46f, 0.12f, 2.58f, wood)
        box(0f, -0.018f, 0f, 1.16f, 0.035f, 2.28f, material("197765"))
        TableGeometry.rails.forEach {
            box(it.center.x, 0.015f, it.center.z, it.halfWidth * 2f, 0.055f, it.halfLength * 2f, material("10564e"))
        }
        // Dark pocket mouths sit above the cloth, behind the playable cushion openings.
        TableGeometry.pockets.forEach {
            val model = own(builder.createCylinder(0.14f, 0.012f, 0.14f, 32, material("091c22"), attributes))
            scene.add(ModelInstance(model).apply { transform.setToTranslation(it.x, 0.006f, it.z) })
        }
        // Rail sights and fine inlay make the plain engine primitives read as a pool table.
        for (side in listOf(-1f, 1f)) {
            for (z in listOf(-0.84f, -0.56f, -0.28f, 0.28f, 0.56f, 0.84f)) {
                box(side * 0.658f, 0.001f, z, 0.012f, 0.008f, 0.018f, material("d9bd83"))
            }
            for (x in listOf(-0.28f, 0f, 0.28f)) {
                box(x, 0.001f, side * 1.22f, 0.018f, 0.008f, 0.012f, material("d9bd83"))
            }
        }
        ballModels = (0..15).map { id ->
            val colorIndex = if (id > 8) id - 8 else id
            val color = Color.valueOf(ballColors[colorIndex])
            val pixmap = Pixmap(64, 32, Pixmap.Format.RGBA8888)
            pixmap.setColor(if (id > 8) Color.valueOf("f8f7ed") else color)
            pixmap.fill()
            if (id > 8) { pixmap.setColor(color); pixmap.fillRectangle(0, 10, 64, 12) }
            val texture = Texture(pixmap)
            pixmap.dispose()
            textures.add(texture)
            val mat = Material(TextureAttribute.createDiffuse(texture), ColorAttribute.createSpecular(Color.WHITE), FloatAttribute.createShininess(48f))
            own(builder.createSphere(TableGeometry.BALL_RADIUS * 2, TableGeometry.BALL_RADIUS * 2, TableGeometry.BALL_RADIUS * 2, 24, 16, mat, attributes))
        }
        shadow = own(builder.createCylinder(0.067f, 0.001f, 0.067f, 24, material("125c50"), attributes))
        cue = own(builder.createCylinder(0.011f, 1f, 0.011f, 12, material("e6c28c"), attributes))
        cueTip = own(builder.createSphere(0.012f, 0.012f, 0.012f, 12, 8, material("76c7cd"), attributes))
        guide = own(builder.createBox(0.003f, 0.002f, 0.16f, material("a7c9b0"), attributes))
        ballInstances = ballModels.map(::ModelInstance)
        shadowInstances = (0..15).map { ModelInstance(shadow) }
        cueInstance = ModelInstance(cue)
        tipInstance = ModelInstance(cueTip)
        guideInstance = ModelInstance(guide)
        resize(420, 933)
    }

    private fun own(model: Model): Model { models.add(model); return model }
    private fun material(hex: String) = Material(ColorAttribute.createDiffuse(Color.valueOf(hex)))
    private fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, material: Material) {
        scene.add(ModelInstance(own(builder.createBox(w, h, d, material, attributes))).apply { transform.setToTranslation(x, y, z) })
    }
    private fun texture(circle: Boolean): Texture {
        val p = Pixmap(32, 32, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE)
        if (circle) p.fillCircle(16, 16, 15) else p.fill()
        val t = Texture(p)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        p.dispose()
        textures.add(t)
        return t
    }

    fun resize(width: Int, height: Int) {
        this.width = width; this.height = height
        uiHeight = 420f * height / width.coerceAtLeast(1)
        camera.viewportWidth = max(1.92f, 3.5f * width / height.coerceAtLeast(1))
        camera.viewportHeight = camera.viewportWidth * height / width.coerceAtLeast(1)
        camera.position.set(0f, 4f, 0f)
        camera.direction.set(0f, -1f, 0f)
        camera.up.set(0f, 0f, -1f)
        camera.near = 0.1f; camera.far = 10f
        camera.update()
        uiCamera.setToOrtho(false, 420f, uiHeight)
    }

    fun tablePoint(screenX: Int, screenY: Int): Point {
        val p = camera.unproject(Vector3(screenX.toFloat(), screenY.toFloat(), 0f), 0f, 0f, width.toFloat(), height.toFloat())
        return Point(p.x, p.z)
    }
    fun isRestart(screenX: Int, screenY: Int, finished: Boolean): Boolean {
        val x = screenX * 420f / width
        val y = uiHeight - screenY * uiHeight / height
        return if (finished) x in 100f..320f && y in (uiHeight / 2 - 85f)..(uiHeight / 2 - 39f)
        else x in 306f..400f && y in (uiHeight - 91f)..(uiHeight - 49f)
    }

    fun render(physics: PoolPhysics, session: GameSession, gesture: CueGesture, pixelWidth: Int = Gdx.graphics.backBufferWidth, pixelHeight: Int = Gdx.graphics.backBufferHeight) {
        Gdx.gl.glViewport(0, 0, pixelWidth, pixelHeight)
        Gdx.gl.glClearColor(0.035f, 0.075f, 0.095f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        modelBatch.begin(camera)
        modelBatch.render(scene, environment)
        physics.balls.forEach { ball ->
            if (ball.pocketed && ball.sinkSeconds > 0.35f) return@forEach
            position.set(ball.previousPosition).lerp(ball.position, physics.interpolation)
            rotation.set(ball.previousRotation).slerp(ball.rotation, physics.interpolation)
            if (ball.pocketed) position.y -= ball.sinkSeconds * 0.20f
            else modelBatch.render(shadowInstances[ball.id].apply { transform.setToTranslation(position.x + 0.006f, 0.008f, position.z + 0.007f) }, environment)
            modelBatch.render(ballInstances[ball.id].apply { transform.set(position, rotation) }, environment)
        }
        val cueBall = physics.balls.firstOrNull { it.id == 0 && !it.pocketed }
        if (session.canShoot && cueBall != null) {
            val d = gesture.direction
            val tip = cueBall.point - d * (TableGeometry.BALL_RADIUS + gesture.tipGap)
            // Keep the rear end inside the visible world, even beside a rail.
            var length = 0.65f
            if (abs(d.x) > 0.001f) length = min(length, ((camera.viewportWidth / 2 - 0.03f + sign(d.x) * tip.x) / abs(d.x)))
            if (abs(d.z) > 0.001f) length = min(length, ((camera.viewportHeight / 2 - 0.13f + sign(d.z) * tip.z) / abs(d.z)))
            length = length.coerceAtLeast(0.04f)
            val center = tip - d * (length / 2)
            modelBatch.render(cueInstance.apply {
                transform.setToTranslation(center.x, TableGeometry.BALL_RADIUS, center.z)
                transform.rotate(Vector3.Y, Vector3(d.x, 0f, d.z))
                transform.scale(1f, length, 1f)
            }, environment)
            modelBatch.render(tipInstance.apply { transform.setToTranslation(tip.x, TableGeometry.BALL_RADIUS, tip.z) }, environment)
            val line = cueBall.point + d * 0.13f
            modelBatch.render(guideInstance.apply {
                transform.setToTranslation(line.x, 0.008f, line.z)
                transform.rotate(Vector3.Y, (atan2(d.x, d.z) * 180f / PI).toFloat())
            }, environment)
        }
        modelBatch.end()
        drawHud(physics, session)
    }

    private fun drawHud(physics: PoolPhysics, session: GameSession) {
        sprites.projectionMatrix = uiCamera.combined
        sprites.begin()
        physics.balls.filter { it.id > 0 && !it.pocketed }.forEach { ball ->
            val projected = camera.project(Vector3(ball.previousPosition).lerp(ball.position, physics.interpolation).add(0f, 0.030f, 0f), 0f, 0f, width.toFloat(), height.toFloat())
            val x = projected.x * 420 / width
            val y = projected.y * uiHeight / height
            sprites.color = Color.valueOf("fff9e7")
            sprites.draw(disc, x - 4.3f, y - 4.3f, 8.6f, 8.6f)
            text(ball.id.toString(), x, y + 3.5f, 0.52f, "172d34", true)
        }
        text("POOL", 28f, uiHeight - 34f, 1.9f, "f2e5c8")
        text("SOLO PRACTICE", 29f, uiHeight - 57f, 0.65f, "8ba6a8")
        text("%02d / 15 LEFT".format(session.remaining), 29f, uiHeight - 86f, 0.85f, "d5e5dd")
        button(306f, uiHeight - 90f, 94f, 38f, "RESTART")
        val status = when (session.state) {
            RoundState.READY -> "Circle to aim. Push forward to strike."
            RoundState.MOVING -> "Let the table settle."
            else -> ""
        }
        text(status, 210f, 90f, 0.84f, "e5dcc8", true)
        text("FASTER PUSH  /  STRONGER SHOT", 210f, 65f, 0.60f, "78979b", true)
        if (session.finished) {
            rectangle(0f, 0f, 420f, uiHeight, Color(0.02f, 0.055f, 0.07f, 0.86f))
            rectangle(30f, uiHeight / 2 - 112f, 360f, 225f, Color.valueOf("163039"))
            text("PRACTICE COMPLETE", 210f, uiHeight / 2 + 80f, 0.72f, "9dbbb7", true)
            text(if (session.state == RoundState.SCRATCHED) "Cue ball pocketed" else "Table cleared", 210f, uiHeight / 2 + 40f, 1.35f, "f2e5c8", true)
            text("${15 - session.remaining} of 15 object balls pocketed", 210f, uiHeight / 2 + 6f, 0.84f, "b8ccca", true)
            button(100f, uiHeight / 2 - 85f, 220f, 46f, "PLAY AGAIN")
        }
        sprites.end()
    }
    private fun rectangle(x: Float, y: Float, width: Float, height: Float, color: Color) {
        sprites.color = color
        sprites.draw(white, x, y, width, height)
    }
    private fun button(x: Float, y: Float, width: Float, height: Float, label: String) {
        rectangle(x, y, width, height, Color.valueOf("32504f"))
        text(label, x + width / 2, y + height / 2 + 5f, 0.78f, "f2e5c8", true)
    }
    private fun text(value: String, x: Float, y: Float, scale: Float, hex: String, centered: Boolean = false) {
        font.data.setScale(scale)
        font.color = Color.valueOf(hex)
        layout.setText(font, value)
        sprites.color = Color.WHITE
        font.draw(sprites, layout, if (centered) x - layout.width / 2 else x, y)
    }
    override fun dispose() {
        modelBatch.dispose(); sprites.dispose(); font.dispose()
        models.forEach { it.dispose() }
        textures.forEach { it.dispose() }
    }
}
