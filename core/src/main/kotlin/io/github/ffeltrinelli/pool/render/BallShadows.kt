package io.github.ffeltrinelli.pool.render

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.TableGeometry
import io.github.ffeltrinelli.pool.replay.BallPose

/** Owns a feathered contact-shadow texture and flat, non-depth-writing shadow geometry. */
internal class BallShadows : Disposable {
    private val texture = createTexture()
    private val model = ModelBuilder().createRect(
        -RADIUS, 0f, RADIUS, RADIUS, 0f, RADIUS,
        RADIUS, 0f, -RADIUS, -RADIUS, 0f, -RADIUS,
        0f, 1f, 0f,
        Material(TextureAttribute.createDiffuse(texture),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false)),
        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
    )
    private val instances = (0..15).map { ModelInstance(model) }

    /** Render after opaque table/balls so the depth test keeps the shadow beneath each ball. */
    fun render(batch: ModelBatch, camera: Camera, visibleBalls: List<BallPose>) {
        batch.begin(camera)
        visibleBalls.forEach { ball ->
            if (ball.y < TableGeometry.BALL_RADIUS - 0.005f) return@forEach
            val instance = instances[ball.id]
            instance.transform.setToTranslation(ball.x + OFFSET_X, CLOTH_CLEARANCE, ball.z + OFFSET_Z)
            batch.render(instance)
        }
        batch.end()
    }

    private fun createTexture(): Texture {
        val pixels = Pixmap(TEXTURE_SIZE, TEXTURE_SIZE, Pixmap.Format.RGBA8888)
        try {
            pixels.setBlending(Pixmap.Blending.None)
            for (y in 0 until TEXTURE_SIZE) for (x in 0 until TEXTURE_SIZE) {
                val dx = (x + 0.5f) * 2f / TEXTURE_SIZE - 1f
                val dy = (y + 0.5f) * 2f / TEXTURE_SIZE - 1f
                val falloff = (1f - dx * dx - dy * dy).coerceAtLeast(0f)
                pixels.setColor(0f, 0f, 0f, MAXIMUM_ALPHA * falloff * falloff)
                pixels.drawPixel(x, y)
            }
            return Texture(pixels).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
            }
        } finally { pixels.dispose() }
    }

    override fun dispose() { model.dispose(); texture.dispose() }

    private companion object {
        const val TEXTURE_SIZE = 128
        const val RADIUS = TableGeometry.BALL_RADIUS * 1.3f
        const val CLOTH_CLEARANCE = 0.0004f
        const val OFFSET_X = 0.003f
        const val OFFSET_Z = 0.004f
        const val MAXIMUM_ALPHA = 0.38f
    }
}
