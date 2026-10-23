# Device validation — 19 October 2026

Device: OnePlus 8T (KB2003), API 34, ARM64, physical resolution 1080 × 2400.
App: io.github.ffeltrinelli.pool 0.1.0; minimum API 26, target API 34, compile API 36.

## Observed

- Debug APK installs and cold-launches on the physical device. Android reported a cold-launch total time of 1,091 ms on the first installation.
- Portrait, immersive rendering, the complete table, numbered rack, cue, short aiming line, count and restart control are visible in a device screenshot.
- A real Android touch swipe produces a break shot, scatters the rack, and disables aiming while the balls move. The post-shot screenshot was visually inspected.
- Home then activity resume preserved the game process and rendered the scene again.
- Physical-device soak completed in **601.3 seconds**, with **20 scripted restart/break cycles**, **58 frame sampling windows**, and no detected process restart or script error.
- Median sampled rate: **59.80 FPS**. The initial window, immediately after Home/resume and initial capture, measured **37.32 FPS**; the next measured 57.09 FPS.
- Excluding the first 20 seconds as a descriptive warm-up analysis, the 56 remaining windows ranged from **59.30 to 60.28 FPS**, with a median of **59.80 FPS**. Their maximum sampled p95 frame interval was **16.94 ms**.
- These are short presentation-time windows sampled every ten seconds, not a continuous trace or proof of a strict uninterrupted 60 FPS floor. Raw samples and initial outliers remain in `build/reports/device/soak.json`.
- The final branding update changes icon resources only; gameplay code matches the soak-tested build. The final APK was rebuilt and reinstalled afterward.

Local evidence is generated under `build/reports/device/`. Screenshots are evidence of the current run, not reference baselines. Device screenshots include the Android cutout area; desktop fixture screenshots cover the game framebuffer only.

## Remaining hands-on acceptance

- Judge continuous circling-to-stroke comfort with an actual finger.
- Compare deliberate slow, medium, and fast strokes and tune thresholds if needed.
- Aim from every side, particularly when the cue ball rests beside rails or pockets.
- Play through scratch and full-clear outcomes naturally. Automated session/physics tests and rendered result fixtures cover their logic and appearance.

A scripted soak supplements these checks; it does not establish subjective gesture quality or performance under every gameplay configuration.

## Collision sound update — 19 October 2026

- The sound-enabled debug APK was built and installed successfully on the same OnePlus 8T/API 34.
- An immediate background/resume during startup exposed Android calling `pause()` before `create()`. The audio lifecycle now checks resource initialization, with a JVM regression test covering pause/resume/dispose before creation.
- After rebuilding and reinstalling the fix, the first retry retained the process but stopped before touch input because the screen was off. Once the user foregrounded the game, a fresh check completed **35.4 seconds**, **two scripted restart/break cycles**, and Home/resume in the same process with **no script errors**. The post-shot screenshot shows the rack scattered; the app-specific log contains no audio-loading/playback or fatal error diagnostics.
- Four short frame samples measured 53.46, 59.78, 59.77 and 55.00 FPS (median **57.39 FPS**). The first and final windows overlap scripted interaction/capture. This short check is not a ten-minute sound-enabled performance acceptance run.
- Local evidence is in `build/reports/device-audio/soak.json`, its screenshots and `app-log.txt`. The earlier ten-minute MVP soak above predates sound playback and does not establish performance of this update.
- Desktop verification passes all 38 JUnit/native Bullet tests, ten rendered scenarios, Android lint/APK, and real OpenAL decoding/source allocation for all three effects. The audio smoke also passes with `ALSOFT_DRIVERS=null`, as used in CI.

The user listened on the OnePlus and reported that sounds felt OK, but preferred a different ball-to-ball effect. They selected **A — “billiard ball clack” by Za-Games** from four playable alternatives. The exact auditioned clip now replaces `ball.wav`; the scripted device results above used the earlier ball recording.

Remaining device acceptance: check the selected replacement in gameplay, immediate cue contact, quieter soft shots, and a busy break without crackling or resting-contact buzz. The scripted check covers repeated restart and Home/resume, but backgrounding during an audible effect still needs a listening check. Sound-enabled long-duration performance acceptance remains pending.


## Bullet cloth and surface markings — 20 October 2026

- Object-ball numbers and stripes now share the interpolated Bullet orientation. Native cloth contact produces sliding-to-rolling motion; no visual rotation driver is used.
- Local verification passes **43 JUnit/native tests**, **15 rendered scenarios** at 1080 × 2400, desktop OpenAL decoding/playback, Android lint, and debug APK assembly. Native checks cover stable support, reduced contact slip, rotation direction, energy loss, angular settling, reset, existing collisions/CCD, and all six pockets. Cloth contacts are silent and cannot trigger pocket capture.
- The debug APK installed and cold-launched on the OnePlus 8T (KB2003), **Android 14 / API 34**, ARM64, 1080 × 2400. Android reported 1,095 ms launch time. The user observed automatic breaks and confirmed that the rolling numbers and ball motion **look natural**.
- The new physics/rendering build completed **600.8 seconds**, **20 scripted restart/break cycles**, and **58 frame sampling windows**, with no detected process restart or script error. Home/resume retained the same process. Median sampled rate was **59.77 FPS**, minimum **55.84 FPS**; the largest sampled p95 frame interval was **17.04 ms**. These are short SurfaceFlinger presentation windows sampled about every ten seconds, not a continuous trace or proof of an uninterrupted 60 FPS floor.
- The app-specific log contains no fatal/native-crash or audio-failure diagnostics. Local evidence is under `build/reports/device-cloth/`; the raw timing summary is preserved in [cloth-device-soak.json](validation/cloth-device-soak.json). A [device break screenshot](images/rolling-ball-numbers/oneplus-break.png) shows moving balls with rotated markings.
- The final APK was rebuilt and reinstalled after explicitly centralizing the unchanged default solver iteration count (10). The soak used the same physics and graphics settings.

Remaining hands-on acceptance: deliberate soft and strong shots, controlled cushion/pocket approaches, and full rounds with the new travel distances. The scripted run exercises restart/break cycles and Home/resume, not all those gameplay situations. Current friction values prioritize plausible motion rather than matching the previous damping-based shot distances.


### Reduced glare follow-up

After user feedback that reflections obscured the numbers, ball specular intensity was reduced from 1.0 to 0.08 (via an initial 0.15 adjustment) while keeping the light setup and shininess unchanged. All 43 unit/native tests and 15 rendered scenarios pass, and the debug APK builds. Updated initial-rack and rolling-stage screenshots show clearer numbers. The ten-minute measurements above predate this material-only adjustment; physics is unchanged.


## Bidirectional cue strokes — 20 October 2026

- The cue now follows backward and forward finger displacement, with direction locking after two aligned samples and 14 mm in either direction. Repeated practice strokes retain the locked aim; lifting cancels and resets the 32 mm tip gap. Maximum withdrawal is 250 mm and contact fires once.
- Local verification passes **57 JUnit/native tests**, **18 rendered scenarios** at 1080 × 2400, OpenAL null-driver asset/lifecycle verification, Android lint, and debug APK assembly. New tests cover preview/reset, locks from both directions, reversals, withdrawal clamping, cancellation, velocity-window interpolation, and power independent of backswing distance and pause.
- The APK installed and cold-launched on the OnePlus 8T (KB2003), Android 14 / API 34, in **1,242 ms**. Scripted touch input exercised withdrawal, forward return, repeated reversals, release before contact, a forward-only shot, withdrawal toward the lower rail, background/resume during a held gesture, and backswing-to-contact. The game process survived background/resume. Device screenshots show the cue withdrawn and then returned, and the cue ball moving after both shot styles.
- Screenshots and the app-specific log are under `build/reports/device-cue/`. The Android `screenrecord` utility exited with status 139 and produced no video; this was a recording-tool failure, not a game-process crash. Deterministic desktop screenshots are preserved in [the cue review images](images/bidirectional-cue/).

Hands-on confirmation: after the final adjustments, the user reported that both steady circling and the transition into a backswing feel right on the OnePlus 8T. Remaining hands-on acceptance: deliberate soft/strong power comparisons and cue-ball positions adjacent to each rail. The scripted near-rail check withdraws the cue toward the lower rail from the opening cue-ball position; it does not cover every rail-adjacent ball position. No new long-duration performance run was performed for this input-only change.

### Backswing startup adjustment

The user reported a small unwanted rotation when starting some backswings. A 14 mm accumulated lateral threshold now separates deliberate circling from startup sideways noise; sub-threshold axial displacement still moves the cue immediately. Once circling is established it tracks continuously, and mainly axial motion starts a stroke candidate. Three regression tests cover noisy backswing startup, small samples accumulating into a deliberate circle, and noisy backswing after circling. The rebuilt APK installed and cold-launched again in 945 ms. All 55 tests and the full local verification pass. The user reported improved backswing startup, then confirmed the final combined behavior below.

### Stable cue distance during circling

The user confirmed improved backswing startup but observed small forward/backward movements while circling. Established circling now holds the tip at its resting gap until a sustained axial stroke meets the two-sample / 14 mm threshold; radial wobble no longer previews cue travel. Two regression tests cover wobble without distance changes and the transition from circling to a deliberate backswing. All 57 unit/native tests, 18 rendered fixtures, audio verification, lint, and APK assembly pass locally. The final APK installed and cold-launched in 909 ms. The user tested it on the OnePlus 8T and confirmed that steady circling and the transition into a backswing both feel right.


## Settings and player view — 20 October 2026

- Removed the on-screen gesture/status instructions and replaced the in-game Restart button with a 48-logical-pixel gear target. Settings contains Restart, Top view / Player view selection, and Close. Results retain Play again; the gear remains accessible over results.
- Top view retains its overhead projection and shared stroke behavior. Player view uses a 30° elevation, 60° vertical field of view, 0.95 m horizontal camera offset, 0.01 / 20 m clipping planes, and a 0.30 m virtual aiming radius. The camera follows aim, freezes at contact, and repositions only after settling. Both modes use the same stroke detection and power calculation.
- Local verification passes **70 unit/native tests**, **35 deterministic rendered scenarios** at 1080 × 2400, desktop OpenAL null-driver verification, Android lint, and debug APK assembly. New coverage includes modal/background suspension, preference persistence, input barriers, camera poses and clipping, player circling without cue pumping, practice strokes, and equal-speed power across modes. Six rendered positions cover rail/pocket-adjacent cameras.
- The APK installed and cold-launched on the OnePlus 8T, Android 14 / API 34, in **1,072 ms**. Scripted Android touch events exercised both view selectors, player aiming, withdrawal/return, release cancellation, forward-only shots, restart, and the original top-view stroke path.
- Opening settings during a moving shot produced two **byte-identical screenshots** 0.5 seconds apart. A tap outside the panel left the frame unchanged. Home/resume retained the process and kept settings open; Android Back closed it. Player-view selection survived a force-stop and fresh process launch. The saved preference was checked through the debuggable app's own preferences file.
- Local device screenshots, app log, and summary are in `build/reports/device-player-view/`. The summary is also preserved in [player-view-device.json](validation/player-view-device.json). No fatal game or audio-failure diagnostics were observed; the device log contains nonfatal vendor GC-reflection and swap-behavior warnings.
- [Review images and a five-second motion recording](images/player-view/) use the production desktop renderer. The recording demonstrates aiming, withdrawal, forward return, contact, and subsequent motion. It is deterministic desktop evidence, not a recording of finger input or Android performance.

Remaining hands-on acceptance: judge player-view camera framing and touch comfort, deliberate soft/strong strokes, and rail-adjacent shots on the phone. Automated screenshots establish camera coverage but do not establish subjective feel. No new ten-minute performance soak was performed for this change. The phone is left in Player view with a fresh rack ready for review.


### Player-camera feedback: 45° aim, no guide, animated shot overview

The user requested a steeper 45° aiming angle, complete removal of the aim aid in both modes, and a zoom-out after striking so the whole table is visible. The aiming-guide geometry and rendering have been removed. Player view now pulls back over 0.65 s from cue contact, keeping shot yaw and 45° elevation while recentering and fitting all table corners within the viewport below the HUD. It holds the overview during movement/results, then returns to the settled cue ball over 0.45 s. Settings/background suspension pauses animation; restart resets it. The near plane blends to 0.25 m during pullback to avoid depth artifacts on the table surface.

Local verification passes **73 unit/native tests**, **37 rendered scenarios**, desktop audio verification, lint and APK assembly. Camera tests cover all 24 headings at three portrait viewports, smooth movement and return, equivalent frame partitions, pause, resize, and restart reset. Updated screenshots and the five-second recording include the pullback; there is no direction line in either mode.

The revised APK installed and cold-launched on the OnePlus in **909 ms**. Scripted shots and inspected screenshots confirm the 45° aiming view, no aiming line in either mode, and the full table visible with moving balls after pullback. Two captures 0.5 s apart while settings was open remained byte-identical, including the paused camera. Home/resume preserved the process; restarting returned to close aim. The phone is left with a fresh Player-view rack. [Camera follow-up device summary](validation/player-view-camera-device.json) records these checks. Subjective angle, framing, and animation timing await user review.

After trying the 45° build, the user requested slightly less zoom-out. The overview now uses tighter framing (96% of viewport width, with separate top/bottom margins) while retaining full-table and six-pocket coverage. The updated automated projection checks still cover every tested heading and viewport.

The tighter-framing APK was reinstalled and the OnePlus shot/overview, paused animation, Home/resume, and restart checks passed again. Its cold launch took 1,225 ms. Screenshots still contain the complete table and all six pockets; the phone is ready in the revised Player view.

### Closer 45° overview with permitted edge cropping

The user requested less zoom-out again and explicitly chose to retain 45° while allowing table edges off-screen. The overview now stops at **80% of the full-table-fit camera distance**, providing a noticeably closer view of ball motion. Whole-table/six-pocket visibility is no longer a guarantee in Player view; Top view remains available for the complete overhead table. The 45° aiming angle, animation durations, and controls are unchanged.

Updated verification passes **74 unit/native tests** and **37 rendered scenarios**, audio checks, lint, and APK assembly. Projection tests cover central play at 24 headings and three viewports; a regression test verifies that the outer rails may now intentionally extend beyond the viewport. Screenshots and the motion recording reflect the closer framing.

The 80%-distance build installed and cold-launched on the OnePlus in **1,145 ms**. Scripted shots, pause/background-resume, and restart passed again. The revised framing is installed and ready for hands-on review.

### Resting cue gap increased to 50 mm

The resting tip-gap setting is now **50 mm** in both modes, increased from 32 mm at user request. Withdrawal remains capped at 250 mm and contact still triggers the shot. Updated contact/power and reset tests, plus rendered cue-position fixtures, cover the new default. All **75 unit/native tests**, **37 rendered scenarios**, audio verification, Android lint, and APK assembly pass. The APK was installed on the OnePlus; a forward shot and restart were exercised, with local evidence in `build/reports/device-player-view/player-50mm-*.png`.


## Realistic pockets and pocket-drop sound — 2026-10-20

Implemented real cloth/table openings with connected rail caps and jaws, 114.3 mm corner / 127 mm side mouths, 30 / 5 mm shelves, 3 mm rounded lips and recessed liners. Balls retain Bullet translation and rotation throughout the fall. Capture occurs at a centre depth of 120 mm; selected sound C plays once then, including scratches. The 50 mm cue gap and camera/input tuning are unchanged.

Local verification: **87 unit/native tests**, **65 rendered scenarios**, four WAV assets decoded and allocated through OpenAL null output, Android debug APK, and lint (**0 errors, 5 existing warnings**). Native tests include soft and angled entry into every pocket, shelf support, jaw deflection, maximum-power containment, falling rotation, simultaneous drops/scratch precedence, reset during a fall, quiet interiors, support seams and frame-partition consistency. The rendered report includes five physical drop stages per pocket type and view, plus shelf/jaw positions and detail crops. A four-second silent recording shows the corner drop in both views at half speed.

OnePlus 8T KB2003, Android 14 / API 34, 1080 × 2400: installed final APK; measured cold launch 1,067 ms. Scripted top-view and player-view runs each completed about 61 seconds and two break/restart cycles, with unchanged process across background/resume and no script errors. Top-view sampling: median **59.79 FPS**, minimum **49.14 FPS**; player-view sampling: median **58.19 FPS**, minimum **53.82 FPS**. These are short SurfaceFlinger windows, including settings/restart transitions, not an uninterrupted trace or a ten-minute performance acceptance result. The top soak predates the final 1 mm cushion-margin removal; the final APK was also checked in Top view with the isolated corner shot below.

A scripted, unobstructed lower-right corner shot in Top view produced “Cue ball pocketed.” Opening settings during a Player-view break froze the table/camera: captures 500 ms apart matched byte-for-byte. Home/resume retained the same process and open settings; app pixels matched, excluding the leftmost 20 columns where the OnePlus gaming overlay changed (observed difference bounds x=0..19, y=300..659). Back resumed motion and Restart restored a fresh rack. Final app-specific error log was empty for PoolPhysics, PoolAudio and AndroidRuntime. Left the final APK foregrounded on a fresh rack in Player view.

Raw automated results: [realistic-pockets-device.json](validation/realistic-pockets-device.json). Screenshots and full reports remain under `build/reports/device-pockets`, `build/reports/visual`, `build/reports/audio` and the module build reports.

**Human acceptance:** On 2026-10-20, the user confirmed the smaller pockets, physical drops, and sound C timing/volume feel right in both views: “Yes, all good.” Exhaustive slow/strong entry checks around all six mouths and a new ten-minute soak were not individually confirmed. Desktop OpenAL null output verifies decoding/allocation; audible timing/volume were accepted by the user on the OnePlus.

## Aided aim, settings, and restart direction — 2026-10-21

Settings now contains View mode, Aided aim, Restart and Exit game, in that order, with an X close control at the panel's top right. Every control has a 48-logical-pixel minimum touch target. Aided aim defaults to On when absent and persists independently of the existing view preference. Exit requests normal backend shutdown; racks remain unsaved. Fresh racks clear gesture state and restore cue/camera heading toward the apex; ordinary cancellation retains the last aim.

A read-only predictor computes direct cue-centre contact against expanded object balls and shared cushion/jaw outlines, ending at a pocket mouth if reached first. The world-space dashed mesh is depth-tested in both modes and hidden during motion, results and settings. It predicts no rebounds or object-ball trajectory. Regression coverage includes moving away from a ball or rail already touching the cue.

Local verification: **97 unit/native tests**, **81 deterministic rendered scenarios**, four audio assets decoded and allocated through the desktop OpenAL null backend, Android debug APK, and lint (**0 errors, 5 existing warnings**). Sixteen new rendered scenarios cover aid On/Off, glancing contact, rail/pocket endpoints, obstruction, reset alignment and settings Off in both views. Existing motion/result fixtures also exercise guide suppression.

On the OnePlus 8T KB2003, Android 14 / API 34, the build installed and cold-launched in **1,343 ms**. Scripted checks and inspected captures verify visible guides in both modes, toggling Off, saved preference persistence across restart and force-stop/relaunch, changing heading then restarting toward the rack in both modes, X dismissal, Android Back dismissal, and practice/forward strokes. Settings during a moving shot froze app pixels across captures 500 ms apart (RGB comparison excluding the leftmost 20 columns occupied by the OnePlus gaming overlay: zero changed channels). Exit finished the activity; reopening retained Player view and aided aim On and began a fresh rack. App-specific PoolPhysics, PoolAudio and AndroidRuntime error output was empty. The final verified APK is installed and foregrounded in Player view with aided aim On.

The final prediction regression (separating from already-touching objects) was added after the scripted device sequence; all suites were rerun and that APK reinstalled. Subjective guide readability/stroke feel and a new ten-minute performance soak remain unconfirmed. [Device check summary](validation/aided-aim-settings-device.json); full local captures are under `build/reports/device-aided-aim`. The opt-in device smoke script's settings coordinates were updated for the new panel.

### Guide endpoint follow-up

The user reported that the guide ended too far before obstacles. The collision prediction correctly stopped the cue **centre** before contact, but the renderer left the cue radius as visible clearance and could also end during a gap in the dash pattern. Prediction now exposes the obstacle contact point separately. Rendering retains the centre trajectory and adds its final short connection to that surface; dashes are anchored at the impact end so no trailing gap shortens the guide. Glancing contacts connect along the contact normal rather than shifting the entire aiming direction. Pocket mouths remain unchanged, with no extension into the pocket.

All **101 unit/native tests**, **81 rendered scenarios**, audio verification, Android lint (0 errors, 5 existing warnings), and APK assembly pass. New tests cover ball surfaces for head-on/glancing/tangent contacts, cushion and jaw surfaces, all pocket mouths, and every dash phase over 1–1,000 mm paths. Updated top/player/glancing screenshots show surface-reaching endpoints. The corrected APK is reinstalled on the OnePlus for hands-on review.

### Keep the whole guide straight

The user’s live OnePlus screen showed the contact-normal connector as a bent tail on an off-centre rack hit. Removed that connector: the contact clearance now extends the guide along the original cue axis, and every dash remains collinear, including the final one. Collision selection remains radius-aware and unchanged. Dashes still reach the endpoint, and pocket mouths receive no extra extension.

All **102 unit/native tests**, **81 rendered scenarios**, audio verification, lint and APK build pass. Tests assert collinearity through head-on, glancing, tangent and oblique-cushion approaches. Updated the glancing screenshot and installed the corrected APK on the OnePlus for review.

## Computer opponent — 2026-10-23

Added saved Solo / Vs computer selection, alternating one-shot turns, deferred scoring, zero-point scratch fouls, cue replacement, and human/computer/draw results. Solo retains scratch-to-end behavior. The computer uses Commons Math 3.6.1 CMA-ES with direct ghost-ball seeds and the production Bullet geometry in a silent worker-owned world. Player view remains in overview during its turn; its visible cue holds, withdraws and strikes at contact. README includes the implemented pipeline and ghost-ball diagrams, tuning, scoring, cancellation and limitations.

Validation passes: **123 unit/native tests**, **103 deterministic rendered scenarios**, all four sound assets and production audio lifecycle through desktop OpenAL null output, Android lint (**0 errors, 5 existing warnings**) and debug APK assembly. Native tests verify a reproducible direct pot without a scratch, bounds, fallback, cancellation during stepping, independent-world disposal, and preserved live state. A controlled 2.5-second collision fixture agrees within 1 cm after snapshot restoration; cached native contact state is not copied and exact trajectory identity is not asserted. New rendered fixtures cover the computer cue at three stages in both views, scores, thinking, foul notices, Settings and all results. A deterministic 2.5-second computer-stroke recording is included under `docs/images/computer-opponent/`.

On the OnePlus 8T KB2003, Android 14 / API 34, installed and launched the new build. The first computer search completed **15 evaluations in 2,000 ms**, with no fallback or PoolAI/native error. A scripted human break earned one point and passed to the computer; inspected captures show scores and the player-view overview. The final APK passed scripted Solo/versus preference selection, persistence after process restart, same-process background/resume, three repeated restarts, Android Back dismissal, and Exit finishing the activity. It is left foregrounded in Vs computer with a fresh human break and the user's existing view/aided-aim preferences preserved.

The phone harness initially sent taps before the first rendered HUD and reported premature Solo/Exit failures; it now waits for visible HUD pixels, saved preferences and activity transitions. The rerun passed. A repeat desktop run also encountered a GLFW monitor-unavailable error while the host display was inactive; restoring the display context and rerunning all checks passed. Graphics startup failures remain explicit test failures.

Twelve passive SurfaceFlinger presentation windows over 63 seconds measured **60.27 FPS median** (minimum 60.25 FPS; sampled p95 intervals approximately 16.63–16.93 ms). These are short windows during a phone trial, not continuous profiling or proof of sustained performance during planning. Full matches in both views, subjective casual strength/cue animation acceptance, device scratch/foul timing, interruption at each planner/animation stage, and a fresh ten-minute soak remain unconfirmed. Unit/native and rendered coverage does not substitute for those device checks. [Machine-readable device summary](validation/computer-opponent-device.json).

Run the explicit device smoke check with `python3 tools/check_opponent_device.py --adb /path/to/adb` (requires FFmpeg on PATH). It resets the rack and leaves Vs computer ready; captures and raw results stay under `build/reports/device-computer`. Set `POOL_RECORD_COMPUTER=1` for the desktop visual task to produce deterministic motion frames, then encode them using FFmpeg if a new recording is needed.
