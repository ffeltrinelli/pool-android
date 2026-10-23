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
import io.github.ffeltrinelli.pool.aim.*
import io.github.ffeltrinelli.pool.input.CueGesture
import io.github.ffeltrinelli.pool.opponent.CuePose
import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty
import io.github.ffeltrinelli.pool.model.*
import io.github.ffeltrinelli.pool.physics.PoolPhysics
import io.github.ffeltrinelli.pool.view.GameViews
import io.github.ffeltrinelli.pool.ui.GameHud

class PoolRenderer : Disposable {
    private val models = mutableListOf<Model>()
    private val textures = mutableListOf<Texture>()
    private val scene = mutableListOf<ModelInstance>()
    private val modelBatch = ModelBatch()
    private val font = BitmapFont()
    private val builder = ModelBuilder()
    private val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
    val views = GameViews()
    val hud = GameHud()
    var humanCanAim = true
    var computerPose: CuePose? = null
    var holdOverview = false
    var computerThinking = false
    var foulPlayer: Player? = null
    var difficulty = OpponentDifficulty.MEDIUM
    var aidedAim = true
    private val aimPredictor = AimPredictor()
    private val aimGuide = AimGuideRenderer()
    var settingsOpen = false
    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.60f, 0.65f, 0.68f, 1f))
        add(DirectionalLight().set(0.95f, 0.94f, 0.86f, -0.4f, -1f, -0.3f))
    }
    private val ballModels: List<Model>
    private val ballInstances: List<ModelInstance>
    private val shadowInstances: List<ModelInstance>
    private val cueInstance: ModelInstance
    private val tipInstance: ModelInstance
    private val shadow: Model
    private val cue: Model
    private val cueTip: Model
    private val position = Vector3()
    private val rotation = Quaternion()

    init {
        scene.add(ModelInstance(own(TableModel.create())))
        // Rail sights and fine inlay make the plain engine primitives read as a pool table.
        for (side in listOf(-1f, 1f)) {
            for (z in listOf(-0.84f, -0.56f, -0.28f, 0.28f, 0.56f, 0.84f)) {
                box(side * 0.658f, 0.057f, z, 0.012f, 0.008f, 0.018f, material("d9bd83"))
            }
            for (x in listOf(-0.28f, 0f, 0.28f)) {
                box(x, 0.057f, side * 1.22f, 0.018f, 0.008f, 0.012f, material("d9bd83"))
            }
        }
        BallTextures(font).use { ballTextures ->
            ballModels = (0..15).map { id ->
                val texture = ballTextures.create(id)
                textures.add(texture)
                val mat = Material(TextureAttribute.createDiffuse(texture), ColorAttribute.createSpecular(0.08f, 0.08f, 0.08f, 1f), FloatAttribute.createShininess(48f))
                own(builder.createSphere(TableGeometry.BALL_RADIUS * 2, TableGeometry.BALL_RADIUS * 2, TableGeometry.BALL_RADIUS * 2, 32, 24, mat, attributes))
            }
        }
        shadow = own(builder.createCylinder(0.067f, 0.001f, 0.067f, 24, material("125c50"), attributes))
        cue = own(builder.createCylinder(0.011f, 1f, 0.011f, 12, material("e6c28c"), attributes))
        cueTip = own(builder.createSphere(0.012f, 0.012f, 0.012f, 12, 8, material("76c7cd"), attributes))
        ballInstances = ballModels.map(::ModelInstance)
        shadowInstances = (0..15).map { ModelInstance(shadow) }
        cueInstance = ModelInstance(cue)
        tipInstance = ModelInstance(cueTip)
        resize(420, 933)
    }

    private fun own(model: Model): Model { models.add(model); return model }
    private fun material(hex: String) = Material(ColorAttribute.createDiffuse(Color.valueOf(hex)))
    private fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, material: Material) {
        scene.add(ModelInstance(own(builder.createBox(w, h, d, material, attributes))).apply { transform.setToTranslation(x, y, z) })
    }
    fun resize(width: Int, height: Int) {
        views.resize(width, height)
        hud.resize(width, height)
    }

    fun render(physics: PoolPhysics, session: GameSession, gesture: CueGesture, pixelWidth: Int = Gdx.graphics.backBufferWidth, pixelHeight: Int = Gdx.graphics.backBufferHeight, viewDelta: Float = 0f) {
        Gdx.gl.glViewport(0, 0, pixelWidth, pixelHeight)
        Gdx.gl.glClearColor(0.035f, 0.075f, 0.095f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        val cuePoint = physics.balls.firstOrNull { it.id == 0 && !it.pocketed }?.point
        views.update(cuePoint, gesture.direction, session.canShoot && !holdOverview, viewDelta)
        modelBatch.begin(views.active.camera)
        modelBatch.render(scene, environment)
        physics.balls.forEach { ball ->
            if (ball.pocketed) return@forEach
            position.set(ball.previousPosition).lerp(ball.position, physics.interpolation)
            rotation.set(ball.previousRotation).slerp(ball.rotation, physics.interpolation)
            if (position.y >= TableGeometry.BALL_RADIUS - 0.005f) modelBatch.render(shadowInstances[ball.id].apply { transform.setToTranslation(position.x + 0.006f, 0.008f, position.z + 0.007f) }, environment)
            modelBatch.render(ballInstances[ball.id].apply { transform.set(position, rotation) }, environment)
        }
        val cueBall = physics.balls.firstOrNull { it.id == 0 && !it.pocketed }
        val cuePose = if (session.humanTurn && humanCanAim) CuePose(gesture.direction, gesture.tipGap) else computerPose
        if (session.canShoot && cueBall != null && cuePose != null) {
            val d = cuePose.direction
            val tip = cueBall.point - d * (TableGeometry.BALL_RADIUS + cuePose.tipGap)
            val length = views.active.cueLength(tip, d)
            val center = tip - d * (length / 2)
            modelBatch.render(cueInstance.apply {
                transform.setToTranslation(center.x, TableGeometry.BALL_RADIUS, center.z)
                transform.rotate(Vector3.Y, Vector3(d.x, 0f, d.z))
                transform.scale(1f, length, 1f)
            }, environment)
            modelBatch.render(tipInstance.apply { transform.setToTranslation(tip.x, TableGeometry.BALL_RADIUS, tip.z) }, environment)

        }
        modelBatch.end()
        if (aidedAim && !settingsOpen && session.humanTurn && humanCanAim && cueBall != null) {
            val path = aimPredictor.predict(cueBall.point, gesture.direction,
                physics.balls.filter { !it.pocketed }.map { AimBall(it.id, it.point) })
            val start = cueBall.point + gesture.direction * TableGeometry.BALL_RADIUS
            if ((path.end - cueBall.point).dot(gesture.direction) > TableGeometry.BALL_RADIUS) {
                aimGuide.render(views.active.camera, start, path.end, path.contactPoint)
            }
        }
        hud.render(session, settingsOpen, views.mode, aidedAim, computerThinking, foulPlayer, difficulty)
    }

    override fun dispose() {
        aimGuide.dispose(); modelBatch.dispose(); font.dispose(); hud.dispose()
        models.forEach { it.dispose() }
        textures.forEach { it.dispose() }
    }
}
