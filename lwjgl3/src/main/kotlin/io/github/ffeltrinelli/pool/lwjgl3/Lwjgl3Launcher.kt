@file:JvmName("Lwjgl3Launcher")
package io.github.ffeltrinelli.pool.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.ffeltrinelli.pool.PoolGame

fun main() {
    if (StartupHelper.startNewJvmIfRequired()) return
    Lwjgl3Application(PoolGame(), Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pool • Solo practice")
        setWindowedMode(450, 1000)
        useVsync(true)
        setForegroundFPS(60)
        disableAudio(true)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
    })
}
