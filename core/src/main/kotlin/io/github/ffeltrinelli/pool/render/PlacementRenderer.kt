package io.github.ffeltrinelli.pool.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.TableGeometry
import io.github.ffeltrinelli.pool.rules.PlacementRegion
import kotlin.math.sin

/** Temporary placement guides use table coordinates, independent of the saved camera mode. */
internal class PlacementRenderer : Disposable {
    private val shapes = ShapeRenderer()
    fun render(camera: Camera, frame: TablePresentation) {
        val point = frame.placement ?: return
        shapes.projectionMatrix = camera.combined
        shapes.transformMatrix = Matrix4().setToTranslation(0f, 0.009f, 0f).rotate(Vector3.X, 90f)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.setColor(0.65f, 0.90f, 0.72f, 0.16f)
        val start = if (frame.placementRegion == PlacementRegion.BEHIND_HEAD_STRING) TableGeometry.HEAD_STRING else -1.08f
        shapes.rect(-0.52f, start, 1.04f, 1.08f - start)
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.setColor(0.90f, 0.96f, 0.80f, 1f)
        if (frame.placementRegion == PlacementRegion.BEHIND_HEAD_STRING) shapes.line(-0.54f, start, 0.54f, start)
        shapes.end()
        shapes.transformMatrix = Matrix4().setToTranslation(point.x, 0.075f, point.z).rotate(Vector3.X, 90f)
        shapes.begin(ShapeRenderer.ShapeType.Line)
        if (frame.placementValid) shapes.setColor(1f, 0.96f, 0.55f, 1f) else shapes.setColor(1f, 0.25f, 0.20f, 1f)
        val pulse = 0.052f + sin(frame.activeSeconds * 4f) * 0.006f
        for (r in listOf(0.034f, 0.036f, pulse, pulse + 0.002f)) shapes.circle(0f, 0f, r, 48)
        shapes.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }
    override fun dispose() = shapes.dispose()
}
