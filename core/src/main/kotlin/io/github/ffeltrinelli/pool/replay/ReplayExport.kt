package io.github.ffeltrinelli.pool.replay

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer
import com.badlogic.gdx.utils.ScreenUtils
import io.github.ffeltrinelli.pool.render.PoolRenderer
import io.github.ffeltrinelli.pool.startup.SplashScreen
import kotlin.math.ceil

/** Owns graphics-thread export resources; encoding runs behind the platform boundary. */
class ReplayExport(private val sharing: ReplaySharing, private val clip: ReplayClip) : AutoCloseable {
    private var target: FrameBuffer? = null
    private var splash: SplashScreen? = null
    val frameCount = INTRO_FRAMES + ceil(clip.videoSeconds * FPS).toInt().coerceAtLeast(1)
    private var submitted = 0
    private var pending: ByteArray? = null
    val progress get() = submitted.toFloat() / frameCount

    init {
        try {
            target = createRenderTarget()
            splash = SplashScreen().apply { resize(WIDTH, HEIGHT) }
            sharing.start(clip, frameCount)
        } catch (failure: Throwable) { close(); throw failure }
    }

    private fun createRenderTarget(): FrameBuffer {
        val depth24 = Gdx.gl30 != null || Gdx.app.type == Application.ApplicationType.Desktop ||
            Gdx.graphics.supportsExtension("GL_OES_depth24")
        return GLFrameBuffer.FrameBufferBuilder(WIDTH, HEIGHT).apply {
            addBasicColorTextureAttachment(Pixmap.Format.RGBA8888)
            addDepthRenderBuffer(if (depth24) GL30.GL_DEPTH_COMPONENT24 else GL20.GL_DEPTH_COMPONENT16)
        }.build()
    }

    fun advance(renderer: PoolRenderer) {
        if (sharing.state != ShareState.Encoding || submitted >= frameCount) return
        if (pending == null) {
            val framebuffer = checkNotNull(target)
            framebuffer.begin()
            try {
                if (submitted < INTRO_FRAMES) checkNotNull(splash).render(WIDTH)
                else renderer.renderReplay(clip, (submitted - INTRO_FRAMES).toFloat() / FPS, WIDTH, HEIGHT)
                pending = ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true)
            } finally { framebuffer.end() }
        }
        if (sharing.offerFrame(pending!!)) { submitted++; pending = null }
    }

    override fun close() {
        pending = null; splash?.dispose(); splash = null; target?.dispose(); target = null
    }
    companion object {
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val FPS = 30
        const val INTRO_FRAMES = 60
    }
}
