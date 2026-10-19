package io.github.ffeltrinelli.pool

import org.junit.Test

class PoolGameLifecycleTest {
    @Test fun `Android may pause resume or destroy before the first graphics frame creates resources`() {
        val game = PoolGame()
        game.pause()
        game.resume()
        game.pause()
        game.dispose()
    }
}
