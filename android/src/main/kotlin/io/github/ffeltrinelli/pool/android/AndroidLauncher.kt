package io.github.ffeltrinelli.pool.android

import android.content.Intent
import io.github.ffeltrinelli.pool.PoolGame
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import io.github.ffeltrinelli.pool.StartupGame

class AndroidLauncher : AndroidApplication() {
    private lateinit var replaySharing: AndroidReplaySharing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Preserve the cutout/system-gesture safe viewport instead of drawing controls beneath it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }
        replaySharing = AndroidReplaySharing(this)
        initialize(StartupGame(gameFactory = { PoolGame(sharing = replaySharing) }), AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            numSamples = 4
            r = 8; g = 8; b = 8; a = 8; depth = 24
        })
    }
    @Deprecated("Uses the platform chooser result supported by the libGDX Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AndroidReplaySharing.SHARE_REQUEST) replaySharing.returnedFromSharing()
    }
}
