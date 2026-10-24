package io.github.ffeltrinelli.pool

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import io.github.ffeltrinelli.pool.startup.StartupSplash
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class StartupGameTest {
    private class Harness {
        var now = 0L
        var processor: InputProcessor? = null
        val events = mutableListOf<String>()
        val gameInput = InputAdapter()
        var initializationSeconds = 0L
        var splashPreparationSeconds = 0L
        val input = Proxy.newProxyInstance(Input::class.java.classLoader, arrayOf(Input::class.java)) { _, method, args ->
            when (method.name) {
                "setInputProcessor" -> { processor = args[0] as InputProcessor?; null }
                "getInputProcessor" -> processor
                else -> error("Unexpected input call: ${method.name}")
            }
        } as Input
        val game = object : ApplicationAdapter() {
            override fun create() { events += "create"; processor = gameInput; now += initializationSeconds * 1_000_000_000L }
            override fun render() { events += "game" }
            override fun resize(width: Int, height: Int) { events += "resize:$width:$height" }
            override fun pause() { events += "pause" }
            override fun resume() { events += "resume" }
            override fun dispose() { events += "dispose-game" }
        }
        val splash = object : StartupSplash {
            override fun render() {
                events += "splash"
                now += splashPreparationSeconds * 1_000_000_000L
                splashPreparationSeconds = 0L
            }
            override fun resize(width: Int, height: Int) { events += "splash-resize:$width:$height" }
            override fun dispose() { events += "dispose-splash" }
        }
        val app = StartupGame({ game }, { splash }, { input }, { now })
        init { app.create(); app.resize(1080, 2400) }
        fun frame(seconds: Double) { now = (seconds * 1_000_000_000L).toLong(); app.render() }
    }

    @Test fun `first frame displays splash before initialization and input waits three seconds`() {
        val h = Harness()
        h.frame(0.0)
        assertFalse(h.events.contains("create"))
        h.frame(0.1)
        assertTrue(h.events.contains("create"))
        assertNull(h.processor)
        h.frame(2.99)
        assertFalse(h.events.contains("game"))
        h.frame(3.0)
        assertSame(h.gameInput, h.processor)
        h.frame(3.01)
        assertEquals("game", h.events.last())
        assertEquals(1, h.events.count { it == "create" })
    }

    @Test fun `first frame preparation does not shorten visible splash duration`() {
        val h = Harness().apply { splashPreparationSeconds = 1 }
        h.frame(0.0); h.frame(1.1)
        h.frame(3.99); assertNull(h.processor)
        h.frame(4.0); assertSame(h.gameInput, h.processor)
    }

    @Test fun `slow initialization counts toward visible duration without gameplay frames`() {
        val h = Harness().apply { initializationSeconds = 4 }
        h.frame(0.0); h.frame(0.1)
        assertNull(h.processor)
        h.app.render()
        assertSame(h.gameInput, h.processor)
        assertFalse(h.events.contains("game"))
    }

    @Test fun `background time is excluded and resume does not replay startup`() {
        val h = Harness()
        h.frame(0.0); h.frame(0.1); h.frame(1.0)
        h.app.pause(); h.frame(20.0); h.app.resume(); h.frame(20.0)
        assertNull(h.processor)
        h.frame(21.99); assertNull(h.processor)
        h.frame(22.0); assertSame(h.gameInput, h.processor)
        h.app.pause(); h.app.resume(); h.frame(30.0)
        assertEquals("game", h.events.last())
        assertEquals(1, h.events.count { it == "dispose-splash" })
    }

    @Test fun `resize reaches initialized game and resources are disposed exactly once`() {
        val h = Harness()
        h.frame(0.0); h.frame(0.1)
        h.app.resize(720, 1600)
        assertTrue(h.events.contains("resize:720:1600"))
        h.app.dispose(); h.app.dispose()
        assertEquals(1, h.events.count { it == "dispose-game" })
        assertEquals(1, h.events.count { it == "dispose-splash" })
        assertNull(h.processor)
    }

    @Test fun `closing before initialization disposes only splash`() {
        val h = Harness()
        h.frame(0.0); h.app.dispose()
        assertFalse(h.events.contains("create"))
        assertFalse(h.events.contains("dispose-game"))
        assertEquals(1, h.events.count { it == "dispose-splash" })
    }
}
