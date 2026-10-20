# Pool — solo practice

An offline 3D pool demo for Android, based on [MVP Design #1](https://github.com/ffeltrinelli/pool-android/issues/1). Aim and shoot with one continuous touch gesture. The round ends when the cue ball is pocketed or all 15 object balls are cleared; the eight ball has no special rule.

## Build and run

Requirements: JDK 21, Android SDK platform 36, and build-tools 35.0.0. Android Studio can import the root Gradle project. The Gradle wrapper is included; dependencies need network access on the first build.

In Android Studio, select **JDK 21** as the Gradle JDK in Settings → Build Tools → Gradle; this matters if the IDE bundles JDK 25.

Set `JAVA_HOME` to your JDK 21 installation. Set `ANDROID_HOME` to your Android SDK, or create an ignored `local.properties` containing `sdk.dir=/absolute/path/to/sdk`.

```sh
./gradlew :android:assembleDebug
adb install -r android/build/outputs/apk/debug/android-debug.apk
adb shell am start -n io.github.ffeltrinelli.pool/.android.AndroidLauncher
```

The app is `io.github.ffeltrinelli.pool`, version 0.1.0. It supports API 26+, targets **API 34** to match the OnePlus 8T, and compiles against API 36 because libGDX's Android dependency requires it. Compile SDK does not set the device requirement. This is a sideloaded demo, not a store release.

For desktop development:

```sh
./gradlew :lwjgl3:run
```

The Gradle task adds macOS's required `-XstartOnFirstThread`. Mouse dragging uses the same gesture interpreter as touch. Collision and cue sounds play on both platforms and follow the device media volume.

## Controls

- Touch behind the cue ball, away from its immediate center. The cue lies between your finger and the ball.
- Circle your finger around the cue ball to aim. Neither view displays an aiming guide.
- Drag backward to withdraw the cue and forward to advance it. You can make repeated practice strokes without lifting. A deliberate axial movement locks the aiming direction until release; lift your finger to re-aim.
- The forward-moving tip strikes on contact; pulling back first is optional. A faster push produces a stronger shot. Releasing before contact cancels the stroke.
- Wait for all balls to settle before aiming again. Additional fingers are ignored.
- Open the **gear** for Settings: restart the rack or choose **Top view** / **Player view**. Settings pauses play and stops current sounds. Close it or press Android Back to resume. **Play again** remains available after a result.
- In **Player view**, touching preserves aim. Drag sideways to turn the cue and camera together; drag down to pull back and up to strike. Lift to unlock the direction. At contact, the camera smoothly pulls back to show the whole table while balls move, then returns behind the settled cue ball.
- The selected view is remembered across rack restarts and app launches; the first launch uses Top view.
- Ball collisions click, cushions thud, and cue contact plays a separate strike. Stronger impacts are louder. There is no music or in-game volume control.

Top view uses the original fixed portrait overhead camera and shows the whole table with gesture space around the rails. Player view uses a raised first-person perspective and may crop parts of the table. The cue can pass visually over rails and balls; there are no cue obstruction, spin, jump-shot, or eight-ball rule controls.

## Architecture and tuning

The scaffold comes from official **gdx-liftoff 1.14.2.3**, using its Kotlin template and Bullet extension. Build tools: Gradle 8.11.1, Android Gradle Plugin 8.10.1, Kotlin 2.2.21, libGDX/Bullet 1.14.2. All graphics use libGDX models, materials, lighting, textures, and text rendering; there is no custom renderer or collision solver.

- `core`: `GameSession` owns round rules; `CueGesture` consumes timestamped table-space input and emits one `ShotCommand`; `PoolPhysics` owns Bullet bodies, sensors, fixed-step simulation and `PhysicsEvent` delivery; `PoolRenderer` draws the same scene on both platforms; `GameViews` selects separate `TopView` and `PlayerView` camera/input implementations; `GameHud` owns HUD/settings drawing and hit regions; `GameControls` owns modal suspension and the saved view preference; `PoolAudio` owns preloaded impact effects. `PoolGame` coordinates them.
- `android`: immersive portrait launcher, safe cutout policy, disabled motion sensors, and keep-screen-on while foregrounded.
- `lwjgl3`: desktop launcher and separate test-only rendered fixtures. Fixtures do not introduce game debug controls.

Physics units are metres and kilograms. The table is 1.12 × 2.24 m; ball radius is 28.575 mm and mass is 170 g. Gravity and a static cloth support surface give Bullet real ball/cloth contact. Sliding friction turns a centre-struck ball from sliding into rolling; rolling and spinning friction bring it to rest without linear or angular damping. Bullet handles ball/cushion collisions and pocket-sensor contacts. Shot distances differ from the earlier damping-based model. Pocketed bodies are removed after contact processing; sinking is a visual animation. Balls escaping the outer pocket jaws are also removed as a safety fallback.

`TableGeometry` owns rack, cushion, and pocket dimensions. `GestureTuning` centralizes dead zone, stroke threshold, lateral tolerance, velocity window, and power cap. Top-view input maps to the fixed table size, so power does not depend on screen resolution. Player-view input uses the same world-units-per-pixel scale in virtual gesture coordinates, independent of its moving camera; both modes share the same `CueGesture`. Forward velocity comes from up to the latest 75 ms of the current forward movement, with interpolation at the window boundary and history cleared on reversal or a stationary sample; distance controls cue contact, not power. Direction locks after at least two aligned samples in either direction and 14 mm of travel. Aligned movement previews immediately before locking. A 14 mm accumulated sideways threshold prevents small startup errors from rotating the cue; deliberate circling clears the preview and then tracks continuously. During established circling, the tip gap stays fixed until two consistent, mainly axial samples travel 14 mm; this suppresses radial finger wobble. At that point the cue takes up the accumulated stroke displacement and follows subsequent movement directly. The tip starts 32 mm behind the ball and follows axial displacement one-to-one, up to a 250 mm gap. Excess withdrawal is discarded so reversal responds immediately. Impulse is capped at 1.15 N·s.

`ViewTuning` centralizes player camera elevation (45°), vertical field of view (60°), horizontal distance behind the cue ball (0.95 m), near/far planes (0.01 / 20 m), and virtual aiming radius (0.30 m). Screen-right motion increases yaw; downward motion withdraws the cue. Direction locking and power retain the shared gesture tuning. Camera yaw stays fixed during strokes. At contact, a 0.65 s smooth pullback recenters on the table and fits its full bounds with tight edge margins and space for the HUD, retaining the shot yaw and 45° elevation. The overview holds while balls move and during results; settling returns the camera to the cue ball over 0.45 s. The near plane increases from 0.01 to 0.25 m during pullback to preserve depth precision. Settings/background suspension pauses the camera animation too; rack restart resets it. Settings and OS suspension are independent; returning from the background with settings open keeps the shot paused. View mode is the only saved preference; racks are not saved.

Physics advances at 120 Hz with up to 100 ms / 12 steps of catch-up per render frame. Rendering interpolates the previous and current poses. Both linear and angular movement must remain below the settling thresholds for 300 ms before input unlocks. Backgrounding freezes simulation and cancels gestures; resume drops its first delta to prevent catch-up. Process death starts a new rack.

Object-ball numbers are baked into two opposite circular patches on each sphere's texture. Numbers and stripes follow the same interpolated Bullet orientation; markings can rotate out of view. A restart faces one number upward. There is no separate visual rolling calculation.

`PhysicsTuning` centralizes solver iterations (10, Bullet’s default), gravity (9.81 m/s²), cloth friction (0.8), ball friction (0.25), ball rolling friction (0.00035), ball spinning friction (0.001), linear sleep/settling speed (0.012 m/s), angular sleep/settling speed (0.3 rad/s), and the 0.3 s quiet interval. Bullet combines the contacting materials' coefficients; these are engine settings, not measured cloth specifications. Cloth restitution is zero; balls retain restitution 0.94 and cushions 0.78. Cloth contacts never trigger sounds or pocket capture. The rigid support extends beyond pocket mouths; sensors remove captured balls before their visual sink. No fabric deformation, cloth-direction effects, physical pocket interiors, or spin controls are simulated.

Native bodies and graphics resources have explicit owners and disposal paths. A restart reuses shared collision shapes and models while replacing ball bodies. `AGENTS.md` contains the locally embedded clean-code instructions and screenshot requirements for PR descriptions.

`AudioTuning` centralizes minimum impulse (0.002 N·s), full-volume impulse (1.15 N·s), maximum gain (0.45), eight concurrent voices, and summed gain budget (1.0). Bullet impact events are separate from session rules, aggregate contact-point impulses per pair per step, and suppress repeated contact until separation. The cue effect is emitted only when the shot impulse is applied. Stronger pending hits take priority, and stronger new hits can replace quieter active voices. Sounds are stopped on pause/restart and disposed on shutdown. Audio failures are logged without stopping play.

## Automated verification

```sh
# Fast gesture/session tests and real native Bullet integration tests
./gradlew :core:test

# Production renderer at 1080 × 2400, writing PNGs and HTML
./gradlew :lwjgl3:visualTest

# Real OpenAL decoding/source allocation; requires graphics and audio output
./gradlew :lwjgl3:audioTest
# On headless Linux, use OpenAL Soft's null output:
# ALSOFT_DRIVERS=null xvfb-run -a ./gradlew :lwjgl3:audioTest

# Android static checks and APK
./gradlew :android:lintDebug :android:assembleDebug
```

JUnit tests also cover saved view selection, modal hit routing and suspension, independent background/settings pause states, camera elevation/alignment/freeze/resize, virtual input without camera feedback, stable player circling, practice strokes, and equal-speed power across modes. JUnit tests cover continuous aiming/stroking, cancellation, noisy and multi-pointer input, power scaling/capping, exactly one impulse, settling, game outcomes, restart, native collisions, six pocket sensors, high-speed cushion hits, rack integrity, fixed-step consistency, stable cloth support, sliding-to-rolling slip, energy loss, angular settling, and pose reset. Physics assertions use tolerances rather than exact trajectories.

Rendered smoke tests capture 37 deterministic scenarios. The original eighteen cover initial rack, cue/aim, three bidirectional cue positions (initial, withdrawn, forward return), six pocket approaches, scratch result, cleared result, and five stages of rolling solid/striped balls (0, 12, 18, 24 and 48 simulation steps). Nineteen additional fixtures cover settings in both modes, player view at four headings, withdrawn/returned strokes, halfway/full shot pullbacks and return to aiming, both player-view results, and six rail/pocket-adjacent positions. Surface-detail crops accompany the rolling fixtures. Captures run on the graphics thread into a fixed-size framebuffer after fixture setup or a fixed simulation pre-roll. No timing sleeps or image baselines are used. Assertions check fixture state; screenshots support human visual review rather than automatic appearance approval.

For an optional five-second player-view recording, generate deterministic frames with `POOL_RECORD_PLAYER_VIEW=1 ./gradlew :lwjgl3:visualTest`, then encode them with ffmpeg:

```sh
ffmpeg -framerate 30 -i build/reports/visual/player-motion-frames/frame-%03d.png \
  -vf scale=540:1200 -c:v libx264 -pix_fmt yuv420p -movflags +faststart \
  build/reports/visual/player-view-motion.mp4
```

This opt-in capture is test tooling; normal visual checks and CI do not require ffmpeg.

Reports:

- `core/build/reports/tests/test/index.html`: JUnit and Bullet results.
- `build/reports/visual/index.html`: scenario results, screenshots, viewport/backend, and failure details. Failed rendering scenarios preserve their last frame when capture remains possible.
- `android/build/reports/lint-results-debug.html`: Android lint.
- `build/reports/audio/results.txt`: real desktop audio decoding, source allocation, and lifecycle smoke results.

Visual tests need a working OpenGL context and fail if one cannot be created. On Linux CI they run under Xvfb with Mesa software rendering. Audio unit tests use a fake player to verify volume, prioritization, concurrency, gain headroom and lifecycle cleanup. Native Bullet tests cover impact classification/strength, repeated collisions, cue delivery, and silent pocket sensors. The separate audio smoke suite checks all bundled WAVs and production playback through OpenAL; CI uses the null output driver, which verifies decoding and source allocation rather than speaker output. Rendered tests remain audio-disabled.

`.github/workflows/verify.yml` runs on PR updates and manual dispatch, and uploads APK, screenshots, HTML, JUnit XML, lint and audio reports. Relevant screenshots should be attached or linked in PR descriptions with short captions; use before/after images when practical. Local paths cannot serve as GitHub images.

## OnePlus 8T validation

The connected acceptance device is **OnePlus KB2003 / OnePlus 8T, Android API 34, 1080 × 2400, ARM64**. See [device validation](docs/device-validation.md) for observed results and remaining hands-on checks.

The opt-in device script operates the foreground game on an unlocked 1080 × 2400 phone, including Home/resume, settings-based restarts and opening break swipes. `--view top` (the default) or `--view player` selects and saves the view used for the run. It stops before sending more touch input if the game leaves the foreground and does not change system settings:

```sh
python3 tools/device_check.py --adb "$ANDROID_HOME/platform-tools/adb" --seconds 600
```

It writes screenshots and `soak.json` under `build/reports/device/`. Performance uses short SurfaceFlinger presentation-time windows sampled every ten seconds; it is not a continuous frame trace. Run with only the intended adb device connected. Gesture comfort, deliberate potting from different positions, and slow/medium/fast touch calibration still require hands-on testing.

## Assets and attribution

Table/cue models, sphere textures, HUD, and the eight-ball vector icon are built from engine primitives and repository source, without paid or downloaded visual art. The CC0 recorded sound effects, original sources, edits and license notices are documented in [audio credits](assets/audio/README.md). The bundled default bitmap font is supplied by libGDX. The clean-code rules in `AGENTS.md` are attributed to [ciembor/agent-rules-books](https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md).
