package io.github.ffeltrinelli.pool.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.VideoView

/** Separate test-APK UID verifies the temporary URI grant and real recipient decoding/playback. */
class ReplayRecipientActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val video = VideoView(this)
        setContentView(video)
        video.setOnPreparedListener { video.start() }
        video.setOnCompletionListener { report(true) }
        video.setOnErrorListener { _, _, _ -> report(false); true }
        video.setVideoURI(intent.data)
    }
    private fun report(success: Boolean) {
        sendBroadcast(Intent("io.github.ffeltrinelli.pool.REPLAY_TEST_PLAYED")
            .setPackage("io.github.ffeltrinelli.pool").putExtra("success", success))
        finish()
    }
}
