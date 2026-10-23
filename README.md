# Pool — solo practice and computer opponent

An offline 3D pool demo for Android, based on [MVP Design #1](https://github.com/ffeltrinelli/pool-android/issues/1). Aim and shoot with one continuous touch gesture. Choose Solo practice or Vs computer in Settings. In Solo, the round ends when the cue ball is pocketed or all 15 object balls are cleared. Vs computer alternates one shot per player and awards one point per object ball; a scratch cancels all points for that shot. The eight ball has no special rule.

## Table of contents

- [Build and run](#build-and-run)
- [Controls](#controls)
- [Architecture and tuning](#architecture-and-tuning)
- [Automated verification](#automated-verification)
- [OnePlus 8T validation](#oneplus-8t-validation)
- [Assets and attribution](#assets-and-attribution)
- [Playing against the computer](#playing-against-the-computer)
- [How the computer opponent works](#how-the-computer-opponent-works)
  - [Starting shots: ghost-ball geometry](#starting-shots-ghost-ball-geometry)
  - [What CMA-ES searches](#what-cma-es-searches)
  - [Isolation, casual strength and lifecycle](#isolation-casual-strength-and-lifecycle)
  - [Current limits and tuning](#current-limits-and-tuning)

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

The Gradle task adds macOS's required `-XstartOnFirstThread`. Mouse dragging uses the same gesture interpreter as touch. Collision, cue and pocket-drop sounds play on both platforms and follow the device media volume.

## Controls

- Touch behind the cue ball, away from its immediate center. The cue lies between your finger and the ball.
- Circle your finger around the cue ball to aim. Aided aim is enabled by default: a dashed line shows the direct cue-ball path to its first object-ball contact, or the first rail/jaw/pocket mouth if no ball is reached. It accounts for ball radius and does not predict rebounds or the object ball’s path. The guide extends the impact position by the cue-ball contact clearance along the original aim axis, without bending toward the contact normal. The dash pattern always reaches the endpoint.
- Drag backward to withdraw the cue and forward to advance it. You can make repeated practice strokes without lifting. A deliberate axial movement locks the aiming direction until release; lift your finger to re-aim.
- The forward-moving tip strikes on contact; pulling back first is optional. A faster push produces a stronger shot. Releasing before contact cancels the stroke.
- Wait for all balls to settle before aiming again. Additional fingers are ignored.
- Open the **gear** for Settings: choose **Solo** / **Vs computer**, choose **Top view** / **Player view**, toggle **Aided aim**, set **Difficulty** (Easy / Medium / Hard), then use **Restart** or **Exit game** side by side. Aided aim uses the same half-width button as the mode selectors. Settings pauses play and stops current sounds. Use the top-right **X** or Android Back to resume. Exit closes the app immediately without saving the rack. **Play again** remains available after a result. Every fresh rack resets the cue toward the apex ball and aligns the player camera, with a 50 mm resting tip gap.
- In **Player view**, touching preserves aim. Drag sideways to turn the cue and camera together; drag down to pull back and up to strike. Lift to unlock the direction. At contact, the camera smoothly pulls back to a closer overview while balls move; outer table edges may be off-screen, then returns behind the settled cue ball.
- The selected play mode, view, aided-aim setting and computer difficulty are remembered across rack restarts and app launches; the first launch uses Solo, Top view, aided aim On and Medium difficulty. Changing play mode starts a fresh rack immediately.
- Ball collisions click, cushions thud, and cue contact plays a separate strike. Every physical pocket drop plays the pocket sound, including each cue-ball scratch after replacement in Vs computer. Stronger impacts are louder. There is no music or in-game volume control.

Top view uses the original fixed portrait overhead camera and shows the whole table with gesture space around the rails. Player view uses a raised first-person perspective and may crop parts of the table. The cue can pass visually over rails and balls; there are no cue obstruction, spin, jump-shot, or eight-ball rule controls.

## Architecture and tuning

The scaffold comes from official **gdx-liftoff 1.14.2.3**, using its Kotlin template and Bullet extension. Build tools: Gradle 8.11.1, Android Gradle Plugin 8.10.1, Kotlin 2.2.21, libGDX/Bullet 1.14.2. All graphics use libGDX models, materials, lighting, textures, and text rendering; there is no custom renderer or collision solver.

- `core`: `GameSession` owns round rules; `CueGesture` consumes timestamped table-space input and emits one `ShotCommand`; `PoolPhysics` owns Bullet bodies, fixed-step simulation and `PhysicsEvent` delivery; `PoolRenderer` draws the same scene on both platforms; `GameViews` selects separate `TopView` and `PlayerView` camera/input implementations; `GameHud` owns HUD/settings drawing and hit regions; `GameControls` owns modal suspension and saved settings; `PoolAudio` owns preloaded impact effects. `AimPredictor` queries direct contact geometry without modifying physics; `AimGuideRenderer` draws depth-tested world-space dashes in one mesh. `PoolGame` coordinates them.
- `android`: immersive portrait launcher, safe cutout policy, disabled motion sensors, and keep-screen-on while foregrounded.
- `lwjgl3`: desktop launcher and separate test-only rendered fixtures. Fixtures do not introduce game debug controls.

Physics units are metres and kilograms. The table is 1.12 × 2.24 m; ball radius is 28.575 mm and mass is 170 g. Gravity and a static cloth support surface give Bullet real ball/cloth contact. Sliding friction turns a centre-struck ball from sliding into rolling; rolling and spinning friction bring it to rest without linear or angular damping. Bullet handles ball/cushion contacts and gravity-driven pocket entry. Pocket mouths measure 114.3 mm at corners and 127 mm at the sides, between cushion noses; jaw cuts are 142° and 104°, with 30 mm / 5 mm shelves. These dimensions follow the [WPA equipment specifications](https://www.wpapool.com/wp-content/uploads/2024/01/RECOMMENDED-EQUIPMENT-SPECIFICATIONS.pdf), without claiming a complete regulation table simulation. Shared pocket geometry builds the connected wooden rail caps, cloth-covered jaws, 3 mm rounded lips, physical openings and recessed liners. There is no scripted sinking, shrinking or fading: the production renderer uses Bullet's interpolated position and rotation until a ball is fully below the cloth. A ball is counted and removed after contact processing when its centre crosses 120 mm below the cloth inside a pocket. Out-of-bounds removal remains a logged safety fallback and never plays the pocket sound.

`TableGeometry` owns table/ball dimensions and the rack; `PocketGeometry` owns the shared pocket and cushion outlines. `TablePhysics` owns all static native table resources, separately from moving balls and session rules. `GestureTuning` centralizes dead zone, stroke threshold, lateral tolerance, velocity window, and power cap. Top-view input maps to the fixed table size, so power does not depend on screen resolution. Player-view input uses the same world-units-per-pixel scale in virtual gesture coordinates, independent of its moving camera; both modes share the same `CueGesture`. Forward velocity comes from up to the latest 75 ms of the current forward movement, with interpolation at the window boundary and history cleared on reversal or a stationary sample; distance controls cue contact, not power. Direction locks after at least two aligned samples in either direction and 14 mm of travel. Aligned movement previews immediately before locking. A 14 mm accumulated sideways threshold prevents small startup errors from rotating the cue; deliberate circling clears the preview and then tracks continuously. During established circling, the tip gap stays fixed until two consistent, mainly axial samples travel 14 mm; this suppresses radial finger wobble. At that point the cue takes up the accumulated stroke displacement and follows subsequent movement directly. The tip starts 50 mm behind the ball and follows axial displacement one-to-one, up to a 250 mm gap. Excess withdrawal is discarded so reversal responds immediately. Impulse is capped at 1.15 N·s.

`ViewTuning` centralizes player camera elevation (45°), vertical field of view (60°), horizontal distance behind the cue ball (0.95 m), near/far planes (0.01 / 20 m), and virtual aiming radius (0.30 m). Screen-right motion increases yaw; downward motion withdraws the cue. Direction locking and power retain the shared gesture tuning. Camera yaw stays fixed during strokes. At contact, a 0.65 s smooth pullback recenters on the table, retaining the shot yaw and 45° elevation. The overview uses 80% of the full-table-fit camera distance for larger balls; outer table edges may be cropped, as preferred during device review. The overview holds while balls move and during results; settling returns the camera to the cue ball over 0.45 s. The near plane increases from 0.01 to 0.25 m during pullback to preserve depth precision. Settings/background suspension pauses the camera animation too; rack restart resets it. Settings and OS suspension are independent; returning from the background with settings open keeps the shot paused. View mode and aided aim are saved independently; racks are not saved.

Physics advances at 120 Hz with up to 100 ms / 12 steps of catch-up per render frame. Rendering interpolates the previous and current poses. Input unlocks after 300 ms of low linear and angular speed. A separate contact-stability check also accepts cloth-supported balls that stay within a 0.5 mm position envelope and 2° orientation envelope for that interval, despite tiny solver velocity spikes. It requires instantaneous speeds below 0.025 m/s and 0.8 rad/s and a centre within 1 mm of cloth height; genuine rolling, continuous spin and falling do not qualify. Backgrounding freezes simulation and cancels gestures; resume drops its first delta to prevent catch-up. Process death starts a new rack.

Object-ball numbers are baked into two opposite circular patches on each sphere's texture. Numbers and stripes follow the same interpolated Bullet orientation; markings can rotate out of view. A restart faces one number upward. There is no separate visual rolling calculation.

`PhysicsTuning` centralizes solver iterations (10, Bullet’s default), gravity (9.81 m/s²), cloth friction (0.8), ball friction (0.25), ball rolling friction (0.00035), ball spinning friction (0.001), linear sleep/settling speed (0.012 m/s), angular sleep/settling speed (0.3 rad/s), and the 0.3 s quiet interval. Bullet combines the contacting materials' coefficients; these are engine settings, not measured cloth specifications. Cloth restitution is zero; balls retain restitution 0.94 and cushions 0.78. Cloth contacts never trigger sounds or pocket capture. The rigid support has six real openings. A single convex central face avoids triangle seam artifacts on the main cloth; convex peripheral pieces and curved lip meshes extend support up to the openings. Recessed liner contacts use friction 0.35 and restitution 0.15; they are silent. The raised back liner slopes inward at its top to direct fast entries downward. No fabric deformation, cloth-direction effects, physical ball-return mechanisms, or spin controls are simulated.

Pocket drops use selected sound C, “Pool ball falling into pocket” by freesman (CC BY 3.0). A dedicated `PocketDrop` physics callback, separate from rule events and impact strength, queues it exactly once, including scratches. It uses fixed nominal gain 0.35 and the shared eight-voice / total-gain budget; pocket sounds take priority over ordinary impacts. Jaw rebounds retain cushion audio. Pause, settings and restart clear pending playback, and restart clears per-ball duplicate suppression. See [audio credits](assets/audio/README.md) for the source, licence and edits.

Native bodies and graphics resources have explicit owners and disposal paths. A restart reuses shared collision shapes and models while replacing ball bodies. `AGENTS.md` contains the locally embedded clean-code instructions and screenshot requirements for PR descriptions.

`AudioTuning` centralizes minimum impulse (0.002 N·s), full-volume impulse (1.15 N·s), maximum gain (0.45), eight concurrent voices, and summed gain budget (1.0). Bullet impact events are separate from session rules, aggregate contact-point impulses per pair per step, and suppress repeated contact until separation. The cue effect is emitted only when the shot impulse is applied. Stronger pending hits take priority, and stronger new hits can replace quieter active voices. Sounds are stopped on pause/restart and disposed on shutdown. Audio failures are logged without stopping play.

## Automated verification

```sh
# Fast gesture/session tests and real native Bullet integration tests
./gradlew :core:test

# Opt-in fixed-plan difficulty calibration: 8 layouts × 64 seeds × 3 levels
./gradlew :core:difficultyCalibration

# Production renderer at 1080 × 2400, writing PNGs and HTML
./gradlew :lwjgl3:visualTest

# Real OpenAL decoding/source allocation; requires graphics and audio output
./gradlew :lwjgl3:audioTest
# On headless Linux, use OpenAL Soft's null output:
# ALSOFT_DRIVERS=null xvfb-run -a ./gradlew :lwjgl3:audioTest

# Android static checks and APK
./gradlew :android:lintDebug :android:assembleDebug
```

JUnit tests also cover saved view/aided-aim/difficulty selection, discrete slider capture and cancellation, seeded execution profiles and exact Hard commands, radius-aware aim prediction and obstruction, fresh-rack cue/camera alignment, exit delivery, modal hit routing and suspension, independent background/settings pause states, camera elevation/alignment/freeze/resize, virtual input without camera feedback, stable player circling, practice strokes, and equal-speed power across modes. JUnit tests cover continuous aiming/stroking, cancellation, noisy and multi-pointer input, power scaling/capping, exactly one impulse, settling, game outcomes, restart, native collisions, six physical pockets, high-speed cushion hits, rack integrity, fixed-step consistency, stable cloth support, sliding-to-rolling slip, energy loss, angular settling, and pose reset. Physics assertions use tolerances rather than exact trajectories.

Rendered smoke tests capture 109 deterministic scenarios, including all three difficulty selections in both views. Twenty-two other opponent fixtures cover both views, human/computer turns, three computer cue stages, foul notices, all match results, and updated Settings. The original eighteen cover initial rack, cue/aim, three bidirectional cue positions (initial, withdrawn, forward return), six pocket approaches, scratch result, cleared result, and five stages of rolling solid/striped balls (0, 12, 18, 24 and 48 simulation steps). Nineteen additional fixtures cover settings in both modes, player view at four headings, withdrawn/returned strokes, halfway/full shot pullbacks and return to aiming, both player-view results, and six rail/pocket-adjacent positions. Twenty-eight additional pocket fixtures cover corner/side drop stages at 0, 12, 24, 36 and 60 fixed steps, shelf positions and jaw contacts in both views. Sixteen aided-aim fixtures cover enabled/disabled guides, glancing hits, rail and pocket endpoints, obstruction, restart alignment, and the Off setting in both views. Detail crops accompany rolling and pocket fixtures. Captures run on the graphics thread into a fixed-size framebuffer after fixture setup or a fixed simulation pre-roll. No timing sleeps or image baselines are used. Assertions check fixture state; screenshots support human visual review rather than automatic appearance approval.

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

Visual tests need a working OpenGL context and fail if one cannot be created. On Linux CI they run under Xvfb with Mesa software rendering. Audio unit tests use a fake player to verify volume, prioritization, concurrency, gain headroom and lifecycle cleanup. Native Bullet tests cover impact classification/strength, repeated collisions, cue delivery, and silent pocket interiors. Pocket tests also cover soft/angled entries into all six pockets, high-power containment, physical descent and rotation, jaw deflections, shelf support, simultaneous drops with scratch precedence, exactly-once capture, reset during a fall, support seams and frame-partition consistency. The separate audio smoke suite checks all bundled WAVs and production playback through OpenAL; CI uses the null output driver, which verifies decoding and source allocation rather than speaker output. Rendered tests remain audio-disabled.

`.github/workflows/verify.yml` runs on PR updates and manual dispatch, and uploads APK, screenshots, HTML, JUnit XML, lint and audio reports. Relevant screenshots should be attached or linked in PR descriptions with short captions; use before/after images when practical. Local paths cannot serve as GitHub images.

## OnePlus 8T validation

The connected acceptance device is **OnePlus KB2003 / OnePlus 8T, Android API 34, 1080 × 2400, ARM64**. See [device validation](docs/device-validation.md) for observed results and remaining hands-on checks.

The opt-in device script operates the foreground game on an unlocked 1080 × 2400 phone, including Home/resume, settings-based restarts and opening break swipes. `--view top` (the default) or `--view player` selects and saves the view used for the run. It stops before sending more touch input if the game leaves the foreground and does not change system settings:

```sh
python3 tools/device_check.py --adb "$ANDROID_HOME/platform-tools/adb" --seconds 600
```

It writes screenshots and `soak.json` under `build/reports/device/`. Performance uses short SurfaceFlinger presentation-time windows sampled every ten seconds; it is not a continuous frame trace. Run with only the intended adb device connected. Gesture comfort, deliberate potting from different positions, and slow/medium/fast touch calibration still require hands-on testing.

## Assets and attribution

Table/cue models, sphere textures, HUD, and the eight-ball vector icon are built from engine primitives and repository source, without paid or downloaded visual art. The CC0 collision/cue effects and CC BY 3.0 pocket recording, original sources, edits and license notices are documented in [audio credits](assets/audio/README.md). The bundled default bitmap font is supplied by libGDX. The clean-code rules in `AGENTS.md` are attributed to [ciembor/agent-rules-books](https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md).

To record the deterministic corner-pocket entry in both modes (half-speed, no audio):

```sh
POOL_RECORD_POCKETS=1 ./gradlew :lwjgl3:visualTest
ffmpeg -framerate 30 -i build/reports/visual/pocket-motion-frames/frame-%03d.png \
  -vf scale=540:1200 -c:v libx264 -pix_fmt yuv420p -crf 22 -movflags +faststart \
  build/reports/visual/pocket-entry-motion.mp4
```

The optional recording uses test-only fixtures; normal play exposes no fixture controls. FFmpeg is needed only for encoding the optional video, not tests or CI.


## Playing against the computer

Choose **Vs computer** in Settings. You always break. Players alternate a single shot even after pocketing balls. Points are committed when the table settles: each object ball is worth one point, but pocketing the cue ball awards **zero points for the entire shot**. Those object balls stay removed. A three-second foul notice identifies the player during ongoing play; Settings and background suspension pause that timer.

After a scratch, the cue returns to its initial position without disturbing the object balls. If blocked, placement uses the nearest safe point on a deterministic 5 mm lattice in the starting half, with 2 mm extra clearance. The next player then shoots. Once all object balls are gone, the higher score wins; uncredited foul balls make draws possible. The result screen shows the outcome and scores without a foul explanation. Restart and Play again clear scores and restore the human break. Preferences persist, matches do not.

You can open Settings during any turn. The computer's input is not controlled by your gestures. Player view stays in the existing overview during its turn, returning to your previous aiming heading on yours. The computer cue becomes visible after planning: 350 ms at the 50 mm gap, 250 ms backswing to 120 mm, then 200 ms forward to contact. Contact applies one shot impulse and the existing cue sound. Its animation speed does not determine shot power. The aided-aim line is hidden throughout computer turns.

## How the computer opponent works

The opponent is an offline, one-shot numerical search, not a language model or a trained policy. **Apache Commons Math 3.6.1 supplies CMA-ES (Covariance Matrix Adaptation Evolution Strategy)**, a search algorithm that tries batches of variations and uses the better results to guide the next batch. Here, it adjusts shot direction and power; Bullet simulates each candidate. Our pool-specific code supplies starting candidates, outcome scoring, turn coordination and difficulty-dependent execution accuracy. No data is sent to a server.

```mermaid
flowchart TD
    A[Settled table snapshot] --> B[Our code: direct ghost-ball candidates and estimated impulse]
    B --> C[Commons Math: CMA-ES varies angle and impulse]
    C --> D[Silent worker-owned Bullet world simulates a shot]
    D --> E[Our code: score completed outcome]
    E -->|Feedback within budget| C
    E --> F[Best completed shot or legal fallback]
    F --> G[Execution accuracy: Easy / Medium / Hard]
    G --> H[Visible cue animation]
    H --> I[At tip contact: strike in the live game]
```

### Starting shots: ghost-ball geometry

For each remaining object ball and pocket, compute the desired object-ball direction toward the pocket interior. Place an imaginary cue-ball centre two ball radii behind the object ball along that direction. Aiming at this **ghost ball** gives an initial cue direction. Reject paths blocked by balls, cushions or jaws, ghost positions outside the playing bounds, and cuts with insufficient forward momentum transfer. The shared aim predictor checks the initial paths; actual pocket success is decided by Bullet.

```mermaid
flowchart LR
    C[Real cue-ball centre] -->|Initial aiming direction| G[Ghost-ball centre]
    G -->|Centres separated by two radii| O[Object-ball centre]
    O -->|Intended object-ball path| P[Pocket]
```

There are at most 90 ball/pocket combinations. Shortlist up to four by travel distance and cut difficulty. The initial impulse in N·s is `clamp(0.18 + 0.16 × sqrt(cueDistance + objectDistance / cutCosine²), 0.08, 1.15)`, with distances in metres. This is a rough seed, not a second physics model. If no direct pot is available, seed a 0.55 N·s shot toward the nearest object ball.

### What CMA-ES searches

The optimizer changes **angle and impulse together**, sampling populations of six and adjusting their search distribution from the scores. Each shortlisted direction gets a local angle window of ±0.14 radians (about 8°); initial standard deviations are 0.035 radians and 0.12 N·s. Impulse remains within 0.04–1.15 N·s. The optimizer receives no pool rules directly: it sees only the score returned by the simulator. It reuses a proven search implementation instead of introducing our own evolutionary algorithm.

Evaluate geometric seeds first, then divide the remaining evaluation budget between their CMA-ES searches. A turn allows up to 64 candidate attempts and a five-second deadline shared by all difficulties, including setup. Each simulation uses the production 120 Hz physics and must settle within 30 simulated seconds. Incomplete or interrupted simulations cannot win. Bounds and deadlines are checked between physics steps. The best fully evaluated shot is retained even when the optimizer exhausts its budget.

The objective is `100 × awardedPoints + 10 × noScratch + pocketProximity − 0.01 × impulse`. Awarded points are zero on a scratch. Proximity is bounded to 0–1 and uses the nearest remaining object ball's distance to a pocket mouth. These weights prioritize scored balls, then avoiding a scratch, then proximity; lower impulse breaks otherwise equal outcomes. This secondary signal helps distinguish some shots that pocket nothing, but does not guarantee an easy search landscape.

### Isolation, casual strength and lifecycle

A single worker exclusively owns a separate Bullet world and disposes it on that same worker. A settled snapshot carries ball identity, position, height and rotation; velocities are zero because meaningful movement has already ended. Native contact caches are not copied, so predictions are physically approximate rather than bit-identical to the live world. No planning simulation delivers live game or audio events.

The Settings slider has exactly three discrete levels. Tapping a stop selects it; dragging snaps to the nearest stop. The saved selection defaults to **Medium** and is also editable in Solo, where it applies only to future Vs computer play.

| Difficulty | Gaussian angular standard deviation | Angular clipping | Uniform power variation |
|---|---|---|---|
| Easy | 1.2° | ±3.6° | ±14% |
| Medium (default) | 0.25° | ±0.75° | ±3% |
| Hard | None | None | None |

Easy targets approximately 30% settled pot success on the fixed calibration layouts.

The planner returns an unmodified command. When a cue stroke starts, `ExecutionAccuracy` applies the selected seeded profile once, retaining impulse bounds. Medium preserves the previously accepted casual error calculation. Hard uses the exact planned command with no artificial direction or power error; **perfect execution does not guarantee pocketing**, because numerical search and physical predictions remain approximate. Every level uses the same CMA-ES algorithm, five-second deadline, 64-attempt cap and other search tuning. More time can improve the selected shot at every level even though Medium's execution accuracy is unchanged. Thinking remains visible for at least one active second. The live shot is animated using the final varied direction, so the cue matches the actual impulse. If planning fails, log the issue and use the best completed result or the legal fallback.

Settings, backgrounding, restart and mode changes invalidate pending work. A result can only reach the current match and turn. Difficulty changes save immediately without restarting the rack or dismissing Settings and affect the next unstruck computer shot, including a retained plan. Pausing an unfinished cue stroke resets its animation without firing; the raw selected plan and seed are retained on resume, so a changed difficulty is applied without compounding error. The cue always follows the final execution direction. Restart discards both the selected shot and animation. Planning metrics appear in Android logcat under `PoolAI`.

### Current limits and tuning

The three difficulty levels change execution accuracy only; there is no multi-turn lookahead or learning across matches. Candidate generation deliberately considers direct pots, not banks or kicks. Bullet can produce incidental rebounds, and CMA-ES may find nearby rebound outcomes, but reliable bank/kick search is not promised. Time limits mean it selects the best completed attempt, not the globally best shot. Casual error and differences between restored and live contacts can turn predicted pots into misses.

Search limits are centralized in `OpponentTuning` and accuracy profiles in `OpponentDifficulty`; candidate-generation and animation constants live beside their respective algorithms. Device results, difficulty calibration and outstanding human checks are recorded in [device validation](docs/device-validation.md). Deterministic tests use fixed seeds and evaluation budgets rather than asserting exact planning times.

Apache Commons Math is distributed under Apache License 2.0. See [third-party notices](assets/licenses/commons-math3-NOTICE.txt) and the [licence](assets/licenses/Apache-2.0.txt).
