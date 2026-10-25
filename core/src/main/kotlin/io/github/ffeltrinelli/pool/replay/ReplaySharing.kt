package io.github.ffeltrinelli.pool.replay

/** Platform work receives only immutable data and bounded RGBA frames, never GL objects. */
sealed interface ShareState {
    data object Idle : ShareState
    data object Encoding : ShareState
    data object Ready : ShareState
    data object Sharing : ShareState
    data object Complete : ShareState
    data class Failed(val message: String) : ShareState
}
interface ReplaySharing : AutoCloseable {
    val available: Boolean
    val state: ShareState
    fun start(clip: ReplayClip, frames: Int)
    fun offerFrame(rgba: ByteArray): Boolean
    fun share()
    fun cancel()
    override fun close() = cancel()
}
object NoReplaySharing : ReplaySharing {
    override val available = false
    override val state = ShareState.Idle
    override fun start(clip: ReplayClip, frames: Int) = Unit
    override fun offerFrame(rgba: ByteArray) = false
    override fun share() = Unit
    override fun cancel() = Unit
}
