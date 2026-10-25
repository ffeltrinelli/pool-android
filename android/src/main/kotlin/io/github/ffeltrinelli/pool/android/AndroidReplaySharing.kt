package io.github.ffeltrinelli.pool.android

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import io.github.ffeltrinelli.pool.replay.*
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Activity owns sharing; each export worker owns its independent cancellation token and queue. */
class AndroidReplaySharing(private val activity: Activity) : ReplaySharing {
    private class Job(val destination: File) {
        val cancelled = AtomicBoolean(false)
        val frames = ArrayBlockingQueue<ByteArray>(2)
    }
    @Volatile private var job: Job? = null
    @Volatile override var state: ShareState = ShareState.Idle
        private set
    override val available = true
    private val directory = File(activity.cacheDir, "replays").apply { mkdirs() }
    init {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
    override fun start(clip: ReplayClip, frames: Int) {
        cancel()
        val current = Job(File(directory, "feltri-replay-${UUID.randomUUID()}.mp4"))
        job = current; state = ShareState.Encoding
        Thread({
            try {
                ReplayVideoEncoder(activity.assets, current.cancelled).encode(clip, frames, current.frames, current.destination)
                synchronized(this) {
                    if (job === current && !current.cancelled.get()) state = ShareState.Ready
                    else current.destination.delete()
                }
            } catch (failure: Exception) {
                current.destination.delete()
                if (!current.cancelled.get()) {
                    Log.e("PoolReplay", "Replay export failed", failure)
                    synchronized(this) { if (job === current) state = ShareState.Failed("Could not create replay video") }
                }
            } finally { current.frames.clear() }
        }, "pool-replay-export").start()
    }
    override fun offerFrame(rgba: ByteArray): Boolean = job?.takeUnless { it.cancelled.get() }?.frames?.offer(rgba) ?: false
    override fun share() {
        val current = job ?: return
        if (state != ShareState.Ready) return
        state = ShareState.Sharing
        activity.runOnUiThread {
            if (job !== current || current.cancelled.get()) return@runOnUiThread
            try {
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.replays", current.destination)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, REPOSITORY_URL)
                    clipData = ClipData.newRawUri("Feltri 8-Ball replay", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                @Suppress("DEPRECATION")
                activity.startActivityForResult(Intent.createChooser(send, "Share replay"), SHARE_REQUEST)
            } catch (failure: RuntimeException) {
                Log.e("PoolReplay", "Could not open Android sharing", failure)
                state = ShareState.Failed("Could not open sharing")
            }
        }
    }
    fun returnedFromSharing() { if (state == ShareState.Sharing) state = ShareState.Complete }
    @Synchronized override fun cancel() {
        job?.cancelled?.set(true)
        job = null; state = ShareState.Idle
    }
    companion object {
        const val SHARE_REQUEST = 8124
        const val REPOSITORY_URL = "https://github.com/ffeltrinelli/pool-android"
    }
}
