# Pool — solo practice

A silent, offline 3D pool demo for Android, based on [MVP Design #1](https://github.com/ffeltrinelli/pool-android/issues/1). Aim and shoot with one continuous touch gesture. The round ends when the cue ball is pocketed or all 15 object balls are cleared; the eight ball has no special rule.

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

The Gradle task adds macOS's required `-XstartOnFirstThread`. Mouse dragging uses the same gesture interpreter as touch. Audio is disabled on both platforms.

## Controls

- Touch behind the cue ball, away from its immediate center. The cue lies between your finger and the ball.
- Circle your finger around the cue ball to aim. A short direction line shows the initial shot direction.
- Without lifting, push forward along the cue toward the ball. A faster push produces a stronger shot. Releasing before contact cancels the stroke.
- Wait for all balls to settle before aiming again. Additional fingers are ignored.
- Use **Restart** at any time, or **Play again** after a result.

The fixed portrait camera shows the whole table with gesture space around the rails. The cue can pass visually over rails and balls; there are no cue obstruction, spin, jump-shot, or eight-ball rule controls.

## Architecture and tuning

The scaffold comes from official **gdx-liftoff 1.14.2.3**, using its Kotlin template and Bullet extension. Build tools: Gradle 8.11.1, Android Gradle Plugin 8.10.1, Kotlin 2.2.21, libGDX/Bullet 1.14.2. All graphics use libGDX models, materials, lighting, textures, and text rendering; there is no custom renderer or collision solver.

- `core`: `GameSession` owns round rules; `CueGesture` consumes timestamped table-space input and emits one `ShotCommand`; `PoolPhysics` owns Bullet bodies, sensors, fixed-step simulation and `PhysicsEvent` delivery; `PoolRenderer` draws the same scene on both platforms. `PoolGame` coordinates them.
- `android`: immersive portrait launcher, safe cutout policy, disabled sensors/audio, and keep-screen-on while foregrounded.
- `lwjgl3`: desktop launcher and separate test-only rendered fixtures. Fixtures do not introduce game debug controls.

Physics units are metres and kilograms. The table is 1.12 × 2.24 m; ball radius is 28.575 mm and mass is 170 g. Ball movement is constrained to the table plane. Engine damping approximates cloth resistance, with Bullet handling ball/cushion collisions and pocket-sensor contacts. Pocketed bodies are removed after contact processing; sinking is a visual animation. Balls escaping the outer pocket jaws are also removed as a safety fallback.

`TableGeometry` owns rack, cushion, and pocket dimensions. `GestureTuning` centralizes dead zone, stroke threshold, lateral tolerance, velocity window, and power cap. Input is unprojected to the fixed table size, so power does not depend on screen resolution. Forward velocity comes from a recent 75 ms event window; distance controls cue contact, not power. Direction locks after at least two aligned forward samples and 14 mm of travel; contact is at 32 mm. Impulse is capped at 1.15 N·s.

Physics advances at 120 Hz with up to 100 ms / 12 steps of catch-up per render frame. Rendering interpolates the previous and current poses. Movement must remain below the settling threshold for 300 ms before input unlocks. Backgrounding freezes simulation and cancels gestures; resume drops its first delta to prevent catch-up. Process death starts a new rack.

Native bodies and graphics resources have explicit owners and disposal paths. A restart reuses shared collision shapes and models while replacing ball bodies. `AGENTS.md` contains the locally embedded clean-code instructions and screenshot requirements for PR descriptions.

## Automated verification

```sh
# Fast gesture/session tests and real native Bullet integration tests
./gradlew :core:test

# Production renderer at 1080 × 2400, writing PNGs and HTML
./gradlew :lwjgl3:visualTest

# Android static checks and APK
./gradlew :android:lintDebug :android:assembleDebug
```

JUnit tests cover continuous aiming/stroking, cancellation, noisy and multi-pointer input, power scaling/capping, exactly one impulse, settling, game outcomes, restart, native collisions, six pocket sensors, high-speed cushion hits, rack integrity, and fixed-step consistency. Physics assertions use tolerances rather than exact trajectories.

Rendered smoke tests capture ten deterministic scenarios: initial rack, cue/aim, six pocket approaches, scratch result, and cleared result. Captures run on the graphics thread into a fixed-size framebuffer after fixture setup or a fixed simulation pre-roll. No timing sleeps or image baselines are used. Assertions check fixture state; screenshots support human visual review rather than automatic appearance approval.

Reports:

- `core/build/reports/tests/test/index.html`: JUnit and Bullet results.
- `build/reports/visual/index.html`: scenario results, screenshots, viewport/backend, and failure details. Failed rendering scenarios preserve their last frame when capture remains possible.
- `android/build/reports/lint-results-debug.html`: Android lint.

Visual tests need a working OpenGL context and fail if one cannot be created. On Linux CI they run under Xvfb with Mesa software rendering. `.github/workflows/verify.yml` uploads APK, screenshots, HTML, JUnit XML, and lint reports. Relevant screenshots should be attached or linked in PR descriptions with short captions; use before/after images when practical. Local paths cannot serve as GitHub images.

## OnePlus 8T validation

The connected acceptance device is **OnePlus KB2003 / OnePlus 8T, Android API 34, 1080 × 2400, ARM64**. See [device validation](docs/device-validation.md) for observed results and remaining hands-on checks.

The opt-in device script operates the foreground game on an unlocked 1080 × 2400 phone, including Home/resume, restart taps and opening break swipes. It stops before sending more touch input if the game leaves the foreground and does not change system settings:

```sh
python3 tools/device_check.py --adb "$ANDROID_HOME/platform-tools/adb" --seconds 600
```

It writes screenshots and `soak.json` under `build/reports/device/`. Performance uses short SurfaceFlinger presentation-time windows sampled every ten seconds; it is not a continuous frame trace. Run with only the intended adb device connected. Gesture comfort, deliberate potting from different positions, and slow/medium/fast touch calibration still require hands-on testing.

## Assets and attribution

Table/cue models, sphere textures, HUD, and the eight-ball vector icon are built from engine primitives and repository source, without paid or downloaded art. The bundled default bitmap font is supplied by libGDX. The clean-code rules in `AGENTS.md` are attributed to [ciembor/agent-rules-books](https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md).
