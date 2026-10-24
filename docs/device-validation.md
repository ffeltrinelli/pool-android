# Device validation — 19 October 2026

This is a chronological validation log. Earlier sections describe the builds tested at that time, not necessarily the current rules. See the latest section and [README](../README.md) for current behavior.


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

Twelve passive SurfaceFlinger presentation windows over 63 seconds measured **60.27 FPS median** (minimum 60.25 FPS; sampled p95 intervals approximately 16.63–16.93 ms). These are short windows during a phone trial, not continuous profiling or proof of sustained performance during planning. The user confirmed that the computer cue aiming/strike looks natural and the opponent feels reasonable for casual play (reply: “yes”). Full matches in both views, device scratch/foul timing, interruption at each planner/animation stage, and a fresh ten-minute soak remain unconfirmed. Unit/native and rendered coverage does not substitute for those device checks. [Machine-readable device summary](validation/computer-opponent-device.json).

Run the explicit device smoke check with `python3 tools/check_opponent_device.py --adb /path/to/adb` (requires FFmpeg on PATH). It resets the rack and leaves Vs computer ready; captures and raw results stay under `build/reports/device-computer`. Set `POOL_RECORD_COMPUTER=1` for the desktop visual task to produce deterministic motion frames, then encode them using FFmpeg if a new recording is needed.


### README navigation and repeated cue-ball pocket audio

Added a README table of contents with twelve checked section links. Pocket audio already handled the first cue-ball drop, but its per-rack duplicate set suppressed subsequent scratches after cue replacement in Vs computer. Cue replacement now re-enables only cue-ball pocket audio; object-ball duplicate suppression stays intact and the current clip is not stopped. Regression coverage verifies repeated scratches, one sound per physical drop, and pause/resume without replay. All **125 unit/native tests**, desktop audio verification, Android lint and debug APK assembly pass. This has no visual gameplay change; existing screenshots remain representative. Repeated-scratch audibility on the new device build has not yet been confirmed by the user.


### Stalled handoff caused by cloth-contact jitter

The user reported a stopped-looking table where the computer never began. A read-only live JDWP inspection confirmed `MOVING` with only 0.1 s accumulated quiet time and no AI request. The striped 13 at approximately (-0.50159, -0.93305) m was the only ball changing pose; one-step transform differences corresponded to roughly 0.0074 m/s and 0.26 rad/s. Small contact oscillations near peripheral support geometry were resetting the instantaneous-speed quiet interval. The debugger detached and resumed the original rack before installing the correction.

The original low-speed settling path remains. An additional cloth-contact stability tracker recognizes a full 300 ms within 0.5 mm of an anchor position and 2° of its orientation, with instantaneous safety caps of 0.025 m/s / 0.8 rad/s and cloth-height tolerance 1 mm. It is not a shot-duration timeout: continuous rolling, meaningful spin, falling, identity changes and reset invalidate stability. All tuning is in `PhysicsTuning`. Controlled oscillation tests cover the formerly unhandled case; native tests replay the captured ball pose and soft/medium/maximum breaks, requiring exactly one settling event. A cold reconstruction of the pose alone did not reproduce the original warm-contact stall, so the oscillation regression exercises the observed behavior separately.

All **130 unit/native tests**, **103 rendered fixtures**, desktop audio checks, lint and APK build pass. The fixed APK is installed on the OnePlus. A scripted break produced a computer planning result after 8.66 seconds including ball travel and planning: **16 completed evaluations in 2,000 ms, no fallback**. This verifies a real break-to-computer handoff; repeated attempts at the exact original warm-contact state remain a human follow-up. Installation started a fresh rack. No graphics or audio assets changed.


## Computer difficulty and compact Settings — 2026-10-23

Added a persistent three-stop Easy / Medium / Hard slider. Medium is the default and retains the accepted seeded 0.25° Gaussian angular deviation clipped to ±0.75°, with uniform ±3% power variation. Easy uses 0.75° clipped to ±2.25° and ±8% power. Hard adds no execution error and returns the raw planned command exactly; it does not promise a pot. All levels share the existing CMA-ES algorithm and tuning, a 64-attempt cap, and the increased five-second deadline. The one-active-second minimum thinking delay and cue animation are unchanged. Difficulty is applied after planning, when the stroke starts. A retained raw plan and seed make suspension repeatable without compounding error; changing difficulty affects the next unstruck shot without resetting the rack. Worker-failure and deadline fallbacks use the same execution profiles.

Aided aim now matches the 146 × 48 mode buttons. Restart and Exit game share a row in those same columns. The slider remains editable in Solo with an explanatory label. Settings retains X/Back dismissal, modal input capture and independent background suspension. [Before Settings](images/computer-difficulty/settings-before.png) and [after Settings](images/computer-difficulty/settings-after.png) are deterministic production desktop renders; they are not Android captures.

Local verification: **139 passing unit/native tests**, one intentionally skipped opt-in calibration test, **109 rendered scenarios**, all four audio assets plus production audio lifecycle, Android lint (**0 errors, 5 existing warnings**) and debug APK assembly. The separate `:core:difficultyCalibration` task passes. It runs eight fixed layouts (short/long side and corner pots, cuts, crowded side/corner), plans once per layout at a fixed 64-attempt budget without a wall-clock deadline, and replays each raw plan with the same 64 seeded execution samples at every level. No search effort changes between profiles. Raw results are preserved in [initial calibration CSV](validation/computer-difficulty-initial-calibration.csv).

| Level | Executions | Settled pot successes | Awarded points | Scratches | Unsettled at 60 simulated seconds |
|---|---:|---:|---:|---:|---:|
| Easy | 512 | 202 (39.5%) | 197 | 22 | 7 |
| Medium | 512 | 299 (58.4%) | 299 | 11 | 4 |
| Hard | 512 | 512 (100%) | 575 | 0 | 0 |

Easy lowers aggregate pot success by **18.9 percentage points**, exceeding the proposed ten-point target, so its initial values were retained. Results do not rank every individual layout monotonically: the crowded-corner fixture happens to favor Easy over Medium. Hard's 100% is specific to these selected layouts and restored-world replays, not a live-game guarantee. Unsettled runs remain in the denominator and receive no pot success or points; they are reported explicitly rather than omitted. The initial 30-second replay window exposed an unsettled execution, so calibration uses a diagnostic 60-second window; the planner's production 30-second simulation limit and live physics are unchanged. These residual settling cases are a known diagnostic limitation, not evidence that every perturbation settles promptly. Existing repeated-scratch audio and cloth-jitter regression tests still pass.


The **final APK** installed on the OnePlus 8T, Android 14 / API 34. All three slider taps, endpoint-clamped drags, outside-tap blocking, X/Back dismissal, saved play mode/difficulty after process restart, three compact-button restarts, Home/resume with the same process and Settings still open, and compact-button Exit passed. Device captures confirm the new layout. A foreground/focus guard and Pool-specific gear-color predicate now reject the launcher and notification shade before the script sends taps. Earlier harness attempts exposed overly broad brightness detection, dimmed-HUD detection with Settings open, and asynchronous preference-write races; corrected sequences passed on the final APK.

Real breaks in **both views** reached visible computer thinking. Settings interrupted pending searches and kept captured app pixels unchanged across 500 ms, excluding the leftmost 20 columns occupied by the OnePlus overlay. Home/resume retained the process and open modal; changes to the accuracy selection persisted. Completed resumed searches reported **38 evaluations / 5,000 ms in Top view** and **39 / 5,000 ms in Player view**, both without fallback. Reopening Settings after each result froze the selected cue stroke; changing to Hard and resuming produced no additional search result. Both shots returned to the human turn. The Top-view sequence detected that return automatically; the final Player-view screenshot was inspected directly because OCR missed the visible “Your turn” text against the table background. Continuous logging was used after Android log-buffer eviction invalidated an earlier count-based harness check. Captured app logs contain no PoolAI, PoolAudio, PoolPhysics or AndroidRuntime error entries. These are functional checks, not continuous profiling or a sustained-performance claim.

The phone is left on a fresh Player-view Vs computer rack with **Medium** selected; the existing aided-aim preference is preserved. **Still pending:** human confirmation of Easy/Medium/Hard feel and subjective responsiveness, full matches in both views, repeated-scratch audibility and comprehensive device foul timing, and a new ten-minute performance soak. Native/rendered coverage does not substitute for those checks. [Device summary and evidence notes](validation/computer-difficulty-device.json); raw captures and continuous logs remain under `build/reports/device-difficulty/`.


### Manual difficulty feedback and easier Easy

The user manually tested the levels and confirmed that **Hard and Medium look good**, while Easy should be a little easier. Easy's Gaussian angular standard deviation is now **1.0°**, clipped to **±3.0°**, with uniform power variation of **±10%** (previously 0.75° / ±2.25° / ±8%). This is a moderate execution-only adjustment: Medium, Hard, the five-second shared deadline, 64-attempt cap and all search/animation tuning are unchanged. The revised Easy still needs hands-on acceptance; the user's feedback accepts the previous Hard/Medium behavior, not comprehensive full-match, scratch-audio or performance validation.


Revalidation passes **139 unit/native tests**, the separate native calibration, Android lint (**0 errors, 5 existing warnings**) and debug APK assembly. On the unchanged eight-layout / 64-seed corpus, revised Easy records **172/512 settled pot successes (33.6%)**, down from 202/512 (39.5%): a further **5.9 percentage-point reduction**. It awards 167 points, records 20 scratches, and has one unsettled execution at the diagnostic 60-second limit. Medium records 300/512 successes (58.6%), 300 points, 11 scratches and four unsettled executions; Hard records 512/512 successes, 575 points, no scratches and no unsettled executions. The revised Easy/Medium pot-success gap is **25.0 percentage points**. Medium's one-execution difference from the initial benchmark is a reminder that restored native contact outcomes are approximate; its execution formula is still covered by the unchanged exact compatibility test. [First retuning calibration CSV](validation/computer-difficulty-easy-first-retuning.csv); [initial calibration CSV](validation/computer-difficulty-initial-calibration.csv). No visual or audio behavior changed, so their previous checks and screenshots remain representative.

The retuned APK installed successfully on the OnePlus. Launch and saved Easy selection were verified; it is ready in Player view / Vs computer, preserving aided aim Off. [Retuning summary](validation/computer-difficulty-easy-retuning.json). Revised Easy feel awaits the user’s next manual test.


### Easy calibrated to approximately 30% pot success

At the user's request, Easy now targets **30%** on the same eight layouts and 64 fixed execution seeds per layout. Its final accuracy profile is **1.2° Gaussian angular deviation**, clipped to **±3.6°**, and uniform **±14% power variation**. Medium and Hard profiles, the CMA-ES algorithm, five-second timeout, 64-attempt cap and all other search/animation tuning are unchanged. Difficulty still changes execution error only.

The final calibration passes with **154/512 settled pot successes (30.1%)** for Easy, awarding 153 points with 13 scratches and one unsettled replay at the diagnostic 60-second limit. Medium records 299/512 successes (58.4%), 299 points, 11 scratches and four unsettled replays; Hard records 512/512 successes, 574 points, no scratches and no unsettled replays. Unsettled trials remain in the denominator. The opt-in calibration now checks that Easy stays within **one percentage point of 30%**, alongside the existing Easy/Medium separation check. Actual match rates depend on layouts and seeds. [Current calibration CSV](validation/computer-difficulty-calibration.csv) and [previous 33.6% calibration](validation/computer-difficulty-easy-first-retuning.csv) preserve the comparison.

Final verification passes **139 unit/native tests**, the separate 30%-target calibration, Android lint (**0 errors, 5 existing warnings**) and APK assembly. The added target check runs only with the opt-in calibration task; the ordinary test suite intentionally skips that one experiment. No UI or audio changes were made in this tuning.

The final 30.1% build installed and launched on the OnePlus; Easy selection was verified in saved preferences. It is ready on a fresh Player-view Vs computer rack, preserving aided aim Off. [30% tuning summary](validation/computer-difficulty-easy-thirty-percent.json). Human acceptance of this latest Easy profile remains pending.

The user accepted the final 30.1% Easy calibration and requested merging the PR. Hard and Medium were previously accepted after manual testing. Full-match, repeated-scratch audibility and long-soak checks remain unclaimed.


## Cue visibility over rails and balls (2026-10-23)

Based on updated `main` at `0c3c459` (including the merged difficulty selector). The table/balls and depth-tested aided aim render first. The cue gets a separate depth-cleared pass using the same camera, lighting and models; the HUD and modal Settings draw last. Rails and object balls no longer hide either human or computer cues. The cue's own surfaces remain depth-tested, its screen/near-plane clipping stays unchanged, and controls, physics and cue visibility timing are unchanged.

Local checks pass: **139 unit/native tests**, one intentionally skipped opt-in difficulty calibration, **121 rendered scenarios**, four audio assets and production audio lifecycle, Android lint (**0 errors, 5 existing warnings**) and debug APK assembly. Twelve new rendered fixtures cover initial, withdrawn and returning rail-adjacent strokes, object-ball overlap, computer cues and Settings in both views. Framebuffer probes compare each fixture with its cue hidden: ten assert visible cue pixels over the obstacle, and two assert that the opaque Settings panel covers the cue. Repeating these checks against the original renderer produced ten expected hidden-cue failures; all pass with the separate cue pass. Existing aided-aim, moving-ball and result fixtures also pass.

Matching production-renderer screenshots show [Top view before](images/visible-cue/top-before.png), [Top view after](images/visible-cue/top-after.png), [Player view before](images/visible-cue/player-before.png) and [Player view after](images/visible-cue/player-after.png). These are deterministic desktop captures at 1080 × 2400, not phone screenshots.

The debug APK installed and cold-launched on the **OnePlus 8T / Android 14 / API 34** in 808 ms. A real-device Player-view capture confirms the cue remains continuous across the near cushion and wooden rail. Saved Vs computer / Player view / Easy / aided aim Off preferences were preserved. The raw capture is under `build/reports/device-visible-cue/launched.png`. The user tested the OnePlus build and confirmed that cue visibility near rails, including backward/forward practice strokes, looks right ("Yes, it looks right"). This accepts the visual change; separate completion of every scenario in both views and a new sustained-performance check are not claimed.

## Computer-only play and clean-code cleanup (2026-10-24)

- Removed Solo and Play mode settings. All launches and fresh racks now start a match against the computer with the human breaking. The obsolete `play-mode` preference is ignored, including existing Solo/Computer or invalid values; view, aided aim and difficulty remain saved independently.
- Reviewed application/input, session, opponent, physics/audio, views/rendering, tests/reports, launchers, build/CI, assets and device tooling using the installed clean-code skill. [Review coverage and decisions](computer-only-cleanup.md) documents both changes and retained code. Gameplay/AI/physics tuning is unchanged.
- Automated checks: **143 passing unit/native tests**, one intentionally skipped opt-in difficulty-calibration test; **121 rendered scenarios** at 1080 × 2400; all four audio assets and production audio lifecycle; Android lint **0 errors / 5 existing warnings**; debug APK build. Added input routing, modal/background blocking, side-effect-free settings construction and exactly-once routed shot delivery tests. Existing native/search/worker/lifecycle contracts remain covered.
- OnePlus 8T KB2003, Android 14 / API 34: installed the new debug APK. Automated Settings checks passed all three difficulty taps, slider snapping/clamping, outside-modal blocking, X and Android Back dismissal, three rack restarts, difficulty persistence over process restart, background/resume in the same process with Settings still open, and Exit. [Lifecycle results](validation/computer-only-lifecycle.json).
- On the final production build, exercised fresh human breaks in Top and Player views. Both reached computer planning, showed its cue and strike, and returned to the human turn. Saved phone frames cover fresh rack, selected computer cue, shot motion and returned turn. Planning logged **20 completed evaluations in 5000 ms** (Top) and **30 in 5000 ms** (Player), both without fallback. The roughly 13.5-second break-to-selected-shot measurement also includes the human break settling and capture overhead; it is not planner latency. [Turn evidence](validation/computer-only-turns.json).
- Human/computer score transitions, scratches, deterministic cue replacement and match results were verified in automated rules/native/rendered suites. A complete phone match, deliberate scratch/replacement scenario, subjective gesture/audio re-acceptance and a new ten-minute performance soak were **not repeated** for this cleanup. Earlier acceptance records remain historical evidence, not new checks.
- Captioned Settings comparison: [before](images/computer-only/settings-before.png), [after](images/computer-only/settings-after.png). Desktop report screenshots demonstrate the production renderer; phone lifecycle/turn captures are under `build/reports/device-computer-only/`.

## Simplified eight-ball (2026-10-24)

Replaced ball-count scoring with the simplified WPA rules in the [README](../README.md#rules-of-this-game). Fresh breaks and fouls now require valid cue-ball placement; new requested racks alternate breakers. Added explicit open-table/group messages and eight-ball results.

Local verification: **161 passed tests, one opt-in calibration skipped, zero failures; 135 rendered scenarios passed**. Audio assets and production lifecycle passed through OpenAL's null backend. Android lint and debug APK build passed. Existing lint warnings were not introduced by this change. See the [implementation/documentation review](eight-ball-review.md).

Installed the debug APK on the OnePlus 8T KB2003, Android 14 / API 34, retaining view, aided-aim and difficulty preferences. Observed:

- Cold launch starts human break placement, temporarily in Top view despite saved Player view.
- Placement ahead of the head string turns the highlight red, explains the error, and remains unconfirmed when the confirmation button is tapped.
- A valid off-centre placement survives Home/resume in the same process. Confirmation restores Player view and normal stroke input.
- A human break pockets a ball and correctly keeps the human turn while groups remain open.
- Settings Restart alternates to a computer break. The computer selects its placement, makes its visible stroke, pots a ball, assigns groups on a later legal pot, and continues shooting.
- Recorded computer searches completed 12, 30 and 24 evaluations at the 5,000 ms deadline, without fallback. Inspected captures show the open-table and assigned-group HUD and the Player-view overview. No PoolPhysics escape or AndroidRuntime error appeared in the captured log.

Phone evidence is generated under `build/reports/device-eight-ball`. The final confirmation-alignment correction also has automated rack/gesture coverage; the final APK is reinstalled before delivery.

Not claimed as completed: a full human-played rack through its result, deliberate phone scratch/ball-in-hand recovery, all rare break/head-string exceptions on the phone, a new ten-minute performance soak, or subjective user acceptance of the new placement interaction. Automated rule/native/rendered tests cover these rule outcomes; earlier device records remain historical evidence, not acceptance of this build.

### Feedback and revisions

The user requested removal of the Pool heading, larger text, and reported a poor Hard-difficulty placement when the computer only needed the eight. Removed the heading, enlarged HUD/settings/placement/result text, and added a dark HUD backing for readability over Player view. The placement shortlist had favored proximity to the break spot and could omit later positions from seed evaluation; it now prioritizes pot-aligned positions and evaluates every shortlisted position before optimization. Five deterministic native layouts, including remaining opponent blockers, now verify a winning Hard placement/shot with the 64-evaluation budget. This regression establishes those cases, not a guarantee of every possible pot. The revised build is installed for further human acceptance.

The user accepted the enlarged font sizes, then reported top-view ball glare and asked to remove the redundant “Open table — groups not assigned” line. Removed that line (the two ownership rows retain “Unassigned”), reduced the header's unused height, and removed the balls' specular material component and softened their diffuse illumination to prevent number-patch clipping. Font sizes are unchanged. The user subsequently confirmed that the ball numbers are readable in Top view; the larger fonts and glare reduction are accepted.

### Reserved HUD viewport

The user observed that the HUD's dark backing obscured far-table balls while aiming in Player view. The scene now renders in a separate viewport below a fixed 92-logical-pixel HUD strip. Notices temporarily replace the ownership rows inside that strip instead of extending over the table. The accepted font sizes, camera tuning and gesture sensitivity remain unchanged. Shared layout supplies viewport bounds, density scaling and the touch Y offset. Header touches cannot begin gameplay.

Local verification: 161 passed tests, one opt-in calibration skipped, 135 rendered scenarios passed, audio smoke/lint/debug APK passed. Added viewport partition/density and projected-touch regression tests. The revised APK is installed on the OnePlus; device captures are under `build/reports/device-eight-ball/viewport-*`. The before screenshot shows the user's actual rack; the rendered after screenshot uses a deterministic rail-adjacent fixture, not the same rack. Subjective acceptance of this layout change is pending.

Following further feedback, moved the ownership rows, turn label and gear upward and reduced the reserved strip from 120 to 92 logical pixels without reducing fonts. This returns approximately 72 physical vertical pixels on the 1080-pixel-wide OnePlus. Updated device-script gear coordinates accordingly.

On the revised OnePlus build, the gear at its new location opened Settings and Android Back dismissed it. Captures confirm the compact header and unobstructed scene; the preceding viewport build also exercised valid placement and a cancelled backswing without changing the selected font sizes.

### Unified notification and placement controls

Further feedback requested one notification location, distinct player/message colours, tighter row spacing, and placement confirmation above the table. The normal HUD now uses 76 logical pixels (previously 92), with closer player rows and a gold notification line; invalid-placement feedback uses red in that same line. Placement guidance no longer appears below the table. Human placement adds a 56-logical-pixel confirmation row at the top, removed immediately after confirmation. Long notices wrap in the notification area and reserve their measured height; ownership labels remain visible. The viewport and input offset follow the resulting layout.

Local verification: 162 tests passed, one opt-in calibration skipped, 135 rendered scenarios passed, four audio assets/lifecycle passed, Android lint and debug APK passed. Tests cover the top confirmation hit region, blocked invalid confirmation, viewport partition and mapped table touches. Installed on OnePlus 8T/API 34 and visually inspected the new top placement layout; before/after phone captures are committed under `docs/images/eight-ball/placement-hud-*`. User acceptance of the new layout remains pending.
