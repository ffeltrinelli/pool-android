package io.github.ffeltrinelli.pool.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import io.github.ffeltrinelli.pool.model.Point
import kotlin.math.min

/** A single dynamic mesh keeps world-space dashes cheap and depth-tested in either camera. */
class AimGuideRenderer : Disposable {
    private val vertices = FloatArray(2048 * 3)
    private val mesh = Mesh(false, 2048, 0, VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"))
    private val shader = ShaderProgram(
        "attribute vec3 a_position; uniform mat4 u_camera; void main(){ gl_Position = u_camera * vec4(a_position, 1.0); }",
        "#ifdef GL_ES\nprecision mediump float;\n#endif\nvoid main(){ gl_FragColor = vec4(0.88, 0.92, 0.83, 1.0); }"
    )

    init { check(shader.isCompiled) { "Aim guide shader: ${shader.log}" } }

    fun render(camera: Camera, origin: Point, end: Point) {
        val length = (end - origin).length()
        if (length < 0.001f) return
        val direction = (end - origin).normalized()
        val side = Point(-direction.z, direction.x) * 0.0012f
        var count = 0
        fun vertex(point: Point) {
            vertices[count++] = point.x
            vertices[count++] = 0.003f
            vertices[count++] = point.z
        }
        var offset = 0f
        while (offset < length && count + 18 <= vertices.size) {
            val start = origin + direction * offset
            val finish = origin + direction * min(offset + 0.025f, length)
            vertex(start - side); vertex(start + side); vertex(finish + side)
            vertex(start - side); vertex(finish + side); vertex(finish - side)
            offset += 0.043f
        }
        mesh.setVertices(vertices, 0, count)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(false)
        try {
            shader.bind()
            shader.setUniformMatrix("u_camera", camera.combined)
            mesh.render(shader, GL20.GL_TRIANGLES, 0, count / 3)
        } finally {
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        }
    }

    override fun dispose() { mesh.dispose(); shader.dispose() }
}
