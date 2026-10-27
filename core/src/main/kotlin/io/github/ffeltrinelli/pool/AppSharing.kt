package io.github.ffeltrinelli.pool

interface AppSharing {
    val available: Boolean
    fun share()
}

object NoAppSharing : AppSharing {
    override val available = false
    override fun share() = Unit
}
