# Device validation — 19 October 2026

This is a chronological validation log. Earlier sections describe the builds tested at that time, not necessarily the current rules. See the latest section and [README](../README.md) for current behavior. Historical screenshots and recordings were removed when the current gallery was introduced; their original versions remain in Git history.


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
- The app-specific log contains no fatal/native-crash or audio-failure diagnostics. Local evidence is under `build/reports/device-cloth/`; the raw timing summary is preserved in [cloth-device-soak.json](validation/cloth-device-soak.json). A device break screenshot showed moving balls with rotated markings; that historical capture has since been removed.
- The final APK was rebuilt and reinstalled after explicitly centralizing the unchanged default solver iteration count (10). The soak used the same physics and graphics settings.

Remaining hands-on acceptance: deliberate soft and strong shots, controlled cushion/pocket approaches, and full rounds with the new travel distances. The scripted run exercises restart/break cycles and Home/resume, not all those gameplay situations. Current friction values prioritize plausible motion rather than matching the previous damping-based shot distances.


### Reduced glare follow-up

After user feedback that reflections obscured the numbers, ball specular intensity was reduced from 1.0 to 0.08 (via an initial 0.15 adjustment) while keeping the light setup and shininess unchanged. All 43 unit/native tests and 15 rendered scenarios pass, and the debug APK builds. Updated initial-rack and rolling-stage screenshots show clearer numbers. The ten-minute measurements above predate this material-only adjustment; physics is unchanged.


## Bidirectional cue strokes — 20 October 2026

- The cue now follows backward and forward finger displacement, with direction locking after two aligned samples and 14 mm in either direction. Repeated practice strokes retain the locked aim; lifting cancels and resets the 32 mm tip gap. Maximum withdrawal is 250 mm and contact fires once.
- Local verification passes **57 JUnit/native tests**, **18 rendered scenarios** at 1080 × 2400, OpenAL null-driver asset/lifecycle verification, Android lint, and debug APK assembly. New tests cover preview/reset, locks from both directions, reversals, withdrawal clamping, cancellation, velocity-window interpolation, and power independent of backswing distance and pause.
- The APK installed and cold-launched on the OnePlus 8T (KB2003), Android 14 / API 34, in **1,242 ms**. Scripted touch input exercised withdrawal, forward return, repeated reversals, release before contact, a forward-only shot, withdrawal toward the lower rail, background/resume during a held gesture, and backswing-to-contact. The game process survived background/resume. Device screenshots show the cue withdrawn and then returned, and the cue ball moving after both shot styles.
- Screenshots and the app-specific log are under `build/reports/device-cue/`. The Android `screenrecord` utility exited with status 139 and produced no video; this was a recording-tool failure, not a game-process crash. Deterministic desktop screenshots supported the cue review; those historical captures have since been removed.

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
- Review images and a five-second motion recording used the production desktop renderer to demonstrate aiming, withdrawal, forward return, contact, and subsequent motion. This was deterministic desktop evidence, not finger-input or Android-performance evidence. The historical media has since been removed.

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

Validation passes: **123 unit/native tests**, **103 deterministic rendered scenarios**, all four sound assets and production audio lifecycle through desktop OpenAL null output, Android lint (**0 errors, 5 existing warnings**) and debug APK assembly. Native tests verify a reproducible direct pot without a scratch, bounds, fallback, cancellation during stepping, independent-world disposal, and preserved live state. A controlled 2.5-second collision fixture agrees within 1 cm after snapshot restoration; cached native contact state is not copied and exact trajectory identity is not asserted. New rendered fixtures cover the computer cue at three stages in both views, scores, thinking, foul notices, Settings and all results. A deterministic 2.5-second computer-stroke recording supported this review; it has since been removed.

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

Aided aim now matches the 146 × 48 mode buttons. Restart and Exit game share a row in those same columns. The slider remains editable in Solo with an explanatory label. Settings retains X/Back dismissal, modal input capture and independent background suspension. Before/after Settings comparisons were deterministic production desktop renders, not Android captures; those historical images have since been removed.

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

Matching before/after screenshots covered Top and Player views through deterministic desktop captures at 1080 × 2400, not phone screenshots. Those historical comparisons have since been removed.

The debug APK installed and cold-launched on the **OnePlus 8T / Android 14 / API 34** in 808 ms. A real-device Player-view capture confirms the cue remains continuous across the near cushion and wooden rail. Saved Vs computer / Player view / Easy / aided aim Off preferences were preserved. The raw capture is under `build/reports/device-visible-cue/launched.png`. The user tested the OnePlus build and confirmed that cue visibility near rails, including backward/forward practice strokes, looks right ("Yes, it looks right"). This accepts the visual change; separate completion of every scenario in both views and a new sustained-performance check are not claimed.

## Computer-only play and clean-code cleanup (2026-10-24)

- Removed Solo and Play mode settings. All launches and fresh racks now start a match against the computer with the human breaking. The obsolete `play-mode` preference is ignored, including existing Solo/Computer or invalid values; view, aided aim and difficulty remain saved independently.
- Reviewed application/input, session, opponent, physics/audio, views/rendering, tests/reports, launchers, build/CI, assets and device tooling using the installed clean-code skill. [Review coverage and decisions](computer-only-cleanup.md) documents both changes and retained code. Gameplay/AI/physics tuning is unchanged.
- Automated checks: **143 passing unit/native tests**, one intentionally skipped opt-in difficulty-calibration test; **121 rendered scenarios** at 1080 × 2400; all four audio assets and production audio lifecycle; Android lint **0 errors / 5 existing warnings**; debug APK build. Added input routing, modal/background blocking, side-effect-free settings construction and exactly-once routed shot delivery tests. Existing native/search/worker/lifecycle contracts remain covered.
- OnePlus 8T KB2003, Android 14 / API 34: installed the new debug APK. Automated Settings checks passed all three difficulty taps, slider snapping/clamping, outside-modal blocking, X and Android Back dismissal, three rack restarts, difficulty persistence over process restart, background/resume in the same process with Settings still open, and Exit. [Lifecycle results](validation/computer-only-lifecycle.json).
- On the final production build, exercised fresh human breaks in Top and Player views. Both reached computer planning, showed its cue and strike, and returned to the human turn. Saved phone frames cover fresh rack, selected computer cue, shot motion and returned turn. Planning logged **20 completed evaluations in 5000 ms** (Top) and **30 in 5000 ms** (Player), both without fallback. The roughly 13.5-second break-to-selected-shot measurement also includes the human break settling and capture overhead; it is not planner latency. [Turn evidence](validation/computer-only-turns.json).
- Human/computer score transitions, scratches, deterministic cue replacement and match results were verified in automated rules/native/rendered suites. A complete phone match, deliberate scratch/replacement scenario, subjective gesture/audio re-acceptance and a new ten-minute performance soak were **not repeated** for this cleanup. Earlier acceptance records remain historical evidence, not new checks.
- A captioned Settings comparison used the production desktop renderer; the historical images have since been removed. The phone lifecycle/turn captures are under `build/reports/device-computer-only/`.

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

Local verification: 162 tests passed, one opt-in calibration skipped, 135 rendered scenarios passed, four audio assets/lifecycle passed, Android lint and debug APK passed. Tests cover the top confirmation hit region, blocked invalid confirmation, viewport partition and mapped table touches. Installed on OnePlus 8T/API 34 and visually inspected the new top placement layout; before/after phone captures supported that inspection and have since been removed. User acceptance of the new layout remains pending.

### Hard-difficulty no-cushion foul

The user accepted the unified placement/HUD layout, then reported a Hard computer shot fouling because no cushion was reached. The post-shot phone screenshot showed human ball in hand. Recent PoolAI logs showed 25 and 32 evaluations at the five-second deadline, both without fallback; the exact pre-shot snapshot was not recorded, so that individual shot was not replayed.

Added firm centre-ball contact alternatives on up to two visible legal targets, evaluated after the first pot seed and before the remaining seeds. These share the existing five-second/64-attempt search budget and the same foul evaluation. Ordinary planning now preserves the existing cue snapshot instead of reconstructing its settled height, rotation and body order. Actual ball-in-hand placement still creates the fresh cue body. Diagnostics include selected impulse/direction and predicted foul; an untested fallback remains identified separately. Hard accuracy and physics tuning are unchanged.

Local verification: 166 tests passed, one opt-in calibration skipped, 135 rendered scenarios passed, audio/lint/debug APK passed. New coverage checks no-cushion rejection versus a legal contact, visible legal targets, preservation of the settled cue pose, and a complete Hard shot on a blocked-pot layout. Existing winning-eight ball-in-hand tests remain passing. These fixed regressions establish specific legal choices, not a guarantee against fouls in every layout. Revised build installed and launched on the OnePlus; a fresh full device rack and confirmation against the reported failure remain outstanding.


## Feltri 8-Ball branding and current gallery (2026-10-24)

- Renamed the display label and desktop title without changing application ID, packages, or preferences. The user selected the orange comic-burst cartoon eight-ball from three generated proposals. Android adaptive, themed, and legacy icon derivatives share that artwork with the enlarged splash.
- Startup now shows the application name and author credit for at least three foreground seconds, then enters gameplay after initialization. A dedicated coordinator gates gameplay input/frames, excludes background time, forwards lifecycle events, and owns splash cleanup. The cue display cap is 0.50 m as an appearance choice. Top view is approximately 15% closer, constrained to preserve outer rails and pockets; player-view framing and gesture scale are unchanged.
- Verification: **172 passing unit/native tests**, one opt-in calibration skipped; **141 passing rendered scenarios**; Android debug APK assembled; lint **0 errors / 6 warnings**. Five warnings predate this change; the additional `IconXmlAndPng` warning concerns the intentional adaptive XML and legacy bitmap variants. Startup tests cover minimum duration, slow initialization, input gating, background/resume, resize, and disposal. Framing tests cover portrait sizes, placement HUD height, and outer bounds.
- Installed the final APK on OnePlus 8T / API 34. Inspected the installed app name and circular adaptive icon, sampled a cold launch through the branded splash to placement, confirmed placement, inspected both views, and resumed from Home without replaying startup. Restored Top view afterward; aided-aim and difficulty preferences were retained. Timestamped launch samples and final device captures are local under `build/reports/device-branding/`. The device screen-recording utility again exited with status 139, so timestamped screenshots were used instead; the exact three-second boundary is established by controlled-time tests rather than those coarse samples.
- Removed 58 historical PNGs and three recordings. Six freshly rendered current images replace them; the table diagram remains. The README embeds the fresh player-view break setup and links the gallery folder. [Refresh procedure](gallery.md), agent guidance, and the PR checklist now require affected images/captions to be maintained in visual PRs. Before-change comparisons are retained only in local build reports and Git history.
- Audio, physical shot tuning, AI budgets, and rules were not changed. A new full match, audio acceptance session, and performance soak were not part of this presentation change.

### OnePlus launcher ordering and icon correction

After the branding update, OnePlus Global Search continued returning an old Pool result even though the installed APK and App info showed Feltri 8-Ball. Restarting Search refreshed its recent-app entry but did not immediately update typed search results. The app drawer also showed the new label in the old Pool position, with an additional white plate around a reduced icon.

Added an explicit adaptive `roundIcon` and restored adaptive definitions to Android's standard `mipmap-anydpi-v26` directory. Rebuilt, linted, and installed the APK; restarted the launcher without clearing its data. The final drawer capture confirms Feltri 8-Ball in the F group and a full-size dark circular icon without the extra white plate. Evidence is local at `build/reports/device-branding/launcher-corrected.png`. The in-game splash and documentation gallery artwork did not change. Typed Global Search results were not rechecked after this final package update.

### Display-sized TrueType UI text

Replaced the enlarged libGDX default bitmap font in the splash, HUD, Settings, and result panels with bundled Lato Regular under SIL OFL 1.1. `UiFonts` owns FreeType generation and caches the selected text styles at the actual render-target pixel width. Fonts are mapped back into logical UI coordinates, with linear filtering and fractional positions; resizing releases old atlases and final disposal releases both atlases and the native generator. Ball-number textures remain unchanged. FreeType natives are packaged for all four existing Android ABIs and desktop.

The splash timer now starts after the initial drawing work, excluding first-frame font preparation from its three-second minimum. A controlled-time regression covers this case.

Verification: **173 passing unit/native tests**, one opt-in calibration skipped; **141 passing rendered scenarios**, including new assertions at 420, 720, 1080, and 1440 render widths for glyph density, stable logical text size, font reuse, and atlas disposal; Android lint **0 errors / 6 existing warnings** and debug APK assembly passed. All six documentation gallery images were regenerated and inspected; before/after detail crops remain local under `build/reports/typography/`.

Installed the TrueType build on OnePlus 8T / API 34 and inspected the splash, HUD, and Settings text. Settings and text rendering survived Home/resume; closing Settings restored gameplay. The final APK, including the startup timing correction, was installed afterward. Device captures are local under `build/reports/device-typography/`. A full match, new performance soak, and audio acceptance were not repeated for this typography change.

### Reported apparent freeze after the computer's eight-ball shot

On the OnePlus, the user reported an apparently stuck table after the computer pocketed the eight. Captures at 22:59:36 and 22:59:51 show small changes restricted to the cue/nearby stripe area along the left cushion; the render/simulation loop was still advancing, and app logs contained no crash. The match completed without restart during inspection and displayed Computer wins. A live debugger capture confirmed `FINISHED`, `COMPUTER_WINS`, the eight pocketed, controls neither backgrounded nor in Settings, and physics marked settled.

The observed delay is consistent with residual contact motion preventing the shot's settling condition. The exact pre-settlement velocities were not captured, so the reason this motion persisted unusually long is not yet established and no physics fix is claimed. Debugger evidence was captured after completion; it is not a replayable pre-shot snapshot. Local screenshots, app-specific logs, and the completed-state capture are under `build/reports/device-settling/`. No rack restart or gameplay-state edit was performed.


### README gameplay recording with sound (2026-10-24)

Added a clickable video preview alongside the README's player-view screenshot. The 30-second desktop recording shows a human break and two actual computer strokes, with production gameplay, rendering, input, and planning. A recording sound adapter captures production playback commands; the soundtrack mixes the bundled samples at their actual event times, gains, and stop boundaries. It is not a OnePlus capture or microphone recording.

Verified 900 frames at 30 fps, 540 × 1200 H.264 video and AAC sound, duration 30.0 seconds, 478,619 bytes. The clip contains 3 cue, 24 ball, 37 cushion, and 2 pocket playback events; mixed peak is 0.528 full scale with no clipping. Inspected a contact sheet across the full clip. Two signal-fixture tests verify onsets, overlap, gain, early stops, silence, and clipping rejection. Audio source attribution is retained in the README, refresh documentation, and MP4 metadata. The poster is committed; the MP4 is hosted as a GitHub PR attachment. GitHub Markdown rendering was checked: it converts the linked preview into a native inline video player beside the screenshot, initially muted with sound available on unmute. Refresh guidance and the PR checklist cover the video when relevant appearance, gameplay, or sound becomes outdated.

Full verification for this recording update: **173 unit/native tests passed**, one opt-in calibration skipped; **141 rendered scenarios passed**; audio decoding/playback lifecycle passed; Android lint **0 errors / 6 existing warnings** and debug APK assembly passed. Both Python soundtrack tests passed. No new device validation or physics change is claimed.


## Automatic cameras and 70 cm cue — 25 October 2026

Camera selection now follows turn ownership and placement: human aiming and ball motion use Player view, while placement and the entire computer turn use Top view. Human shots retain their animated pullback. Results keep the last shooter's view. The overhead projection fits outer table dimensions plus an 80 mm total margin. Settings no longer selects or saves camera views; old view preferences are ignored. The displayed cue cap is 0.70 m, subject to camera clipping.

Desktop verification on macOS ARM64 with JDK 21:

- Core suite: 177 tests, 176 passed and the opt-in difficulty calibration skipped; no failures. Coverage includes ownership during motion, retained computer turns, handoff, placement confirmation, alternating restart, suspension, legacy preferences, cue clipping, and overhead projection at portrait, square and landscape sizes.
- Android lint and debug APK assembly passed. Desktop audio smoke passed decoding, playback allocation and production lifecycle checks.
- All 113 deterministic rendered scenarios passed. Manual image inspection covered compact settings, full-table overhead framing, placement, player aiming, results, and withdrawn human/computer cues beside rails. Updated the current gallery; removed obsolete manual-view fixture combinations.
- Re-recorded the production gameplay demo: 900 frames, 30 seconds, 540 × 1200, H.264/AAC, 473,252 bytes. Audio verification reported peak 0.528, RMS 0.0148, and cue/ball/cushion/pocket events. One-second samples across the clip show the human pullback and overhead computer planning, strokes and motion. The MP4 and detailed reports remain in ignored `build/reports/`; the refreshed poster is updated alongside the gallery.

The initial desktop pass did not include a physical-device run or performance soak; the real-device follow-up below supersedes that limitation for the listed checks. Subjective touch comfort and uninterrupted audiovisual playback remain hands-on checks. The device smoke script now uses automatic cameras and the compact settings Restart position; only its CLI was checked in this session. The refreshed video is [attached to PR #16](https://github.com/user-attachments/assets/527ce3f0-ee9c-42dd-b021-9526481f66fe); the README preview links to that upload.


### Real-device follow-up — 25 October 2026

Detected an authorized, unlocked OnePlus 8T (KB2003), API 34, 1080 × 2400 over wireless adb. Rebuilt the current debug APK and installed it successfully with `install -r`. Cold launch completed in **633 ms**. APK SHA-256: `4144473f9032271baab33c1de3bb06acdb02959a0d3e185e1edcefaf98038361`.

- Inspected overhead initial placement with every rail/pocket visible; Confirm placement switched to close Player view. The longer cue rendered correctly. Settings showed Aided aim, Difficulty, Restart and Exit game without camera controls.
- A scripted human break retained the perspective pullback during motion and returned behind the settled cue ball after a retained turn.
- Opened Settings during that shot: screenshots 0.5 seconds apart had identical decoded game pixels. Home/resume retained PID 1258, kept Settings open, and preserved those same pixels. Comparison excluded the top 103-pixel cutout and leftmost 20 columns occupied by the phone's gaming overlay.
- Restart from compact Settings selected the computer as the alternating breaker. Twenty sampled screenshots showed overhead planning, the cue stroke, and moving balls, followed by close Player view when control passed to the human. All rails and pockets remained visible during overhead play.
- App-specific logs contained no PoolPhysics, PoolAudio, or AndroidRuntime errors or fatal exception/signal. The observed computer break used 12 evaluations at the five-second budget without fallback.

Evidence, screenshot contact sheets, process/frame comparisons, app logs, and `verification.json` are under `build/reports/device-automatic-cameras/`. Left the updated app foregrounded for the human turn, with the existing Aided aim Off and Medium difficulty preserved. These are scripted smoke checks, not a full rack, ten-minute soak, subjective touch evaluation, or speaker/audio acceptance.


## 25 October 2026 — pocket replay and Android export

Tested the current debug APK on the authorized OnePlus 8T (KB2003), API 34, 1080 × 2400, ARM64. The app was installed with `adb -s SERIAL install -r`, launched normally, and a scripted touch break produced a visible replay with the square Share button. Historical results above do not cover this change.

### Verified

- Core tests passed (186 tests, one opt-in calibration skipped), including native physics and replay tests; Android lint and debug APK assembly passed. Difficulty calibration remains opt-in and was skipped.
- Replay tests cover legal scoring eligibility for both players, winning/losing eights and fouls, a three-second lead-in, first cue contact, multiple pockets producing one clip, interpolated visibility, recorded audio gains/stops and slow-frame pocket-audio flushing, full-table camera fit, and the complete two-second final hold.
- Two Android instrumentation tests passed on the physical device. A deterministic legal side-pocket shot exercised the production recorder and playback coordinator. Settings froze the replay clock and live physics. Share was tapped during the final hold; the Android chooser opened, Back dismissed it, and the remaining hold completed before play resumed.
- The encoder test cancelled an in-progress export, successfully started a new one, generated H.264 video and AAC audio with the production offscreen renderer/EGL/MediaCodec path, verified the cache URI, and played the entire video in a separate test-APK Activity using temporary read permission. Only the test recipient was opened; no video was sent to personal sharing targets.
- FFmpeg/ffprobe verification passed for the synthetic export and the real-shot export. The synthetic clip contains 141 frames at 720 × 1280 / 30 fps: exactly 60 static splash frames followed by the 2.68-second replay/audio tail rounded to video frames. MP4 duration is approximately 4.76 seconds including AAC padding. The silent intro, audible unclipped effects, and attribution metadata were verified. The two-second decision hold is absent from export.
- The splash title/author and close cue view were inspected in device-generated video frames. Desktop captures of the close view, full-table view, and updated Settings were inspected and copied into the gallery. Replay overlays explicitly reset depth/texture state so their labels remain visible after the cue disappears.
- A new 30-second desktop gameplay video was recorded: 900 frames, H.264/AAC, approximately 565 KB, with replay sounds, one human shot and two computer shots. Its preview is refreshed. The MP4 remains a local build artifact for review/PR attachment.

Evidence is under `build/reports/replay/` and `build/reports/gameplay-demo/`. Share-sheet screenshots remain local because Android suggestions may contain private contact information. Test fixtures and the recipient Activity are confined to `androidTest`.

### Limits of this run

The desktop visual and audio suites passed earlier in this implementation session, including the replay gallery captures. A later final rerun failed during GLFW graphics initialization because macOS reported no available primary monitor (`glfwGetMonitorPos`); it did not reach scene rendering or audio playback. The subsequent core/Android checks and physical-device instrumentation passed. Other Android devices/codec implementations and subjective camera comfort beside every rail still need hands-on review.


## 25 October 2026 — replay border depth correction

The user's exported diagonal overview exposed cloth/cushion depth fighting that the initial straight-on fixtures missed. The exact MP4 was retrieved from the OnePlus cache and its decoded final frame showed the same defect as the player screenshot, confirming it was baked into the export.

The replay camera now interpolates its near plane from 0.01 m in the cue closeup to 0.25 m in the overview, matching the live camera's depth-precision policy. Export explicitly requests a 24-bit depth renderbuffer on desktop, GLES 3, or GLES 2 with `GL_OES_depth24`; unsupported GLES 2 contexts keep a 16-bit fallback. Table geometry and encoding bitrate are unchanged.

A deterministic 720 × 1280 diagonal fixture reproduced the stripes with the original settings on the physical OnePlus. After the fix, the same frame was visually inspected with clean cushion/cloth boundaries. The device reports **24 export depth bits**. Four oblique views (45°, 135°, 225°, 315°) each had fewer than 500 significantly different pixels between the production 24-bit image and a 16-bit reference using the corrected near plane, exercising the fallback as well. The test captures are in the debug app's `files/replay-diagonal-ANGLE.png`; local comparison evidence is under `build/reports/replay/depth-before.png` and `depth-after.png`.

Core tests, Android lint/debug assembly, desktop visual tests (including the new diagonal gallery fixture), and desktop audio tests passed on this correction. The earlier unavailable-monitor limitation did not recur. The corrected debug build was installed on the OnePlus; all three device tests passed, covering four diagonal depth views, export cancellation/retry, the real-shot Share/return flow, and recipient playback. A newly encoded diagonal MP4 passed the video verifier and its decoded final frame was visually inspected with clean borders (`build/reports/replay/depth-fixed-export.mp4`). Existing MP4 files retain their old pixels and need to be exported again.

The correction adds a near-plane regression test (187 core tests total, one opt-in calibration skipped) and refreshes the diagonal gallery and 30-second gameplay video (approximately 603 KB). The documentation recording logged one off-table ball during its break; physics tuning was not changed by this rendering fix.


## 25 October 2026 — five-second replay decision countdown

The final replay frame now remains visible for five active seconds. Skip shows no suffix during playback, then displays `Skip (5)` through `Skip (1)` before automatic return. Countdown values come from the playback clock, so Settings, background suspension, export, and the share sheet preserve the remaining time; export still shows Cancel. The five-second decision hold is excluded from the video, and the splash intro remains two seconds.

Core tests passed (188 total, one opt-in calibration skipped), including exact countdown boundaries, pause preservation, export-cancel labeling, and unchanged clip duration. Android lint/debug assembly and desktop visual/audio suites passed. The deterministic `replay-hold` capture was visually inspected and added to the gallery; the current gameplay recording is refreshed for this behavior. The updated APK was installed on the OnePlus and all three device tests passed, including the five-second countdown remaining unchanged during export and sharing, then completing on return.


## 25 October 2026 — 75%-speed replays

Replay presentation now runs at 75% speed for both in-app playback and shared video. The immutable clip is retimed once: ball/cue frames, strike/contact/pocket markers, and audio starts share the same stretched timeline. Camera pullback uses the same time scale (approximately 0.87 seconds). Effects retain normal pitch and natural sample length; the five-second decision countdown and two-second splash intro remain in real time.

Core tests passed (189 total, one opt-in calibration skipped), including retimed interpolation/events, synchronized sound starts, natural sound tails, the slowed camera transition, unchanged source data, and the complete five-second countdown. Android lint/debug assembly and desktop visual/audio suites passed. Documentation media was refreshed for the slower presentation. The synthetic export fixture now has approximately 3.3467 seconds of replay/audio tail before video frame rounding; the decision hold is excluded.

The updated APK was installed on the OnePlus and all three device acceptance tests passed, including retimed real-shot playback, unchanged countdown through sharing, and recipient playback. The new synthetic slow-motion export passed `verify_replay_video.py --replay-seconds 3.3466667`: 161 frames at 30 fps with the 60-frame silent splash, synchronized effects, and no decision hold (`build/reports/replay/slow-motion-export.mp4`).


## 25 October 2026 — soft ball contact shadows

Replaced the opaque, raised green shadow cylinders with flat, feathered alpha shadows 0.4 mm above the cloth. The new `BallShadows` owner creates/disposes its radial texture and quad geometry. A separate pass after opaque table/balls uses depth testing without depth writes, preserving ball occlusion while allowing the cloth to show through. The shadow is closer to the ball centre and remains hidden once a ball starts dropping, as before. Physics, replay speed, countdown, and export encoding are unchanged.

Core tests, Android lint/debug assembly, and desktop visual/audio suites passed. The current debug APK was installed on the OnePlus and all three device acceptance tests passed, including close/diagonal framebuffer rendering, Settings/share-sheet suspension, export, and recipient playback. A newly encoded 75%-speed sample passed the video verifier with its unchanged two-second intro and duration. Matching frames at 2.2 seconds from the previous/current export were visually inspected: the hard rim and raised-disc appearance are gone, replaced by a soft contact shadow. Evidence is local at `build/reports/replay/ball-shadow-before.png`, `ball-shadow-after.png`, `soft-shadows-device.png`, and `soft-shadows-export.mp4`.

Affected gallery images and the 30-second gameplay recording/preview were refreshed. Before/after comparisons stay in local reports or PR attachments. The documentation recording again logged one ball leaving the table during its break; this visual change does not modify that existing physics fallback.


## 25 October 2026 — residual cushion motion stalled a computer turn

The connected OnePlus was responsive but its session remained `MOVING`, with no replay, settings, or sharing active. Debugger inspection found ball 11 rocking near the top-left cushion with residual spin around 0.31 rad/s. A native regression copied its pose and linear/angular velocities: with the previous settings the shot never settled after ten simulated seconds. Its bounded rocking spans approximately 0.6 mm, exceeding the previous 0.5 mm anchor tolerance.

The pose-stability bound is now 1 mm across the same 0.3 s quiet interval, retaining the two-degree orientation bound, cloth-height check, and linear/angular speed caps. Settling zeroes velocities and puts the bodies into Bullet's sleeping state so persistent contact impulses cannot restart the jitter. Explicit cue activation and collision activation remain effective. Tests cover the captured fixture, one settlement event, stable resting positions, subsequent strikes/object-ball collisions, and rejection of continuous rolling/spin/falling.

Core tests passed (191 total, one opt-in calibration skipped), along with Android lint/debug assembly and desktop visual/audio suites. The new physical-device cushion fixture passed on the OnePlus. The first broader device run exposed an existing test race: the chooser paused the graphics thread before its next-frame sharing observation, leaving the chooser open and blocking the following activity launch. The acceptance test now observes chooser launch through Android activity lifecycle callbacks rather than requiring another GL frame. No production sharing behavior was changed by this test correction.

After correcting that observation race, all four physical-device tests passed in 37.7 seconds: residual cushion settling, four oblique export views, real-shot replay/Settings/share-return with the five-second hold, and export cancellation/retry plus recipient playback. The fixed debug APK remains installed on the OnePlus. The test APK was removed after validation. The existing gallery/gameplay media was reviewed; this settling correction does not change the depicted visuals or controls, so no media refresh was needed.

## 25 October 2026 — open-table computer pot intent

The user reported an apparently aimless computer shot after a completed break while both groups were unassigned. OnePlus logcat at 10:07:02.347 showed 31 completed evaluations in the five-second budget, no fallback, no predicted foul, and direction `(0.9877108, -0.15629272)` / impulse `0.2738702`. The match was paused in Settings during the shot. Read-only debugger inspection confirmed `breaking=false`, no group or placement restriction, and execution seed 1. The replay recorder still contained the shot's initial ball transforms, which were copied into deterministic desktop and Android test fixtures.

Native reproduction found a direct candidate that hits and pockets ball 3. Optimization instead retained a weaker shot that hits ball 3 but predicts pocketing ball 1. Both scored one legal pot with the same proximity contribution, so the lower impulse won. The configured Medium error for seed 1 makes that incidental pot miss; the direct ball-3 seed still succeeds with the same error. This was a ranking problem, not an open-table target-filter or fallback problem.

Pot seeds now retain their intended ball through optimization and cue-placement alternatives. A completed legal pot of that intended ball receives a two-point preference, behind rack outcomes, foul avoidance, and the 100-point contribution per legal target pocketed, but ahead of proximity and lower impulse. Contact alternatives retain their role when direct pots are unavailable. Difficulty error, search budget, physics, and group-assignment rules are unchanged. Planner logs now include intended target and predicted pocketed target IDs.

Core tests passed (193 total, one opt-in calibration skipped), Android lint/debug assembly and desktop visual/audio suites passed. The captured-layout device fixture passed on the OnePlus using the production five-second budget: 30 completed evaluations selected target 3 with predicted pockets `[3]`; replaying the actual Medium execution pocketed ball 3 legally. Local evidence is in `build/reports/replay/open-table-logcat.txt` and `open-table-planning-device.txt`. Device screenshots and full logs stay local.

All five physical-device tests passed in 45.3 seconds, including the new captured-layout planning check and the previous settling/replay/export/share lifecycle cases. The updated debug build is installed; the test APK was removed. The 30-second gameplay recording and preview were refreshed for the revised planner: 900 frames, H.264/AAC, approximately 563 KB, with actual cue, collision, cushion, and pocket audio. Existing static gallery scenes remain representative.


## 25 October 2026 — opponent legality, one-cushion routes and bounded search efficiency

Reviewed rules/group handoff, worker lifecycle, candidate geometry, scoring, simulation and execution accuracy. Two blocked fixtures reproduced wrong-first-contact shots in the original planner on the OnePlus. The fallback had aimed through blockers, and optimization could spend its whole attempt cap on known illegal directions. Historical reported layouts were unavailable, so the review does not claim exact reproduction of those incidents.

The current planner rejects geometrically illegal first contacts, constructs one-cushion banks/kicks after direct-pot planning fails, and shares one 5,000 ms deadline across setup, placements and both phases. The attempt ceiling is 256. Difficulty errors and production physics settings are unchanged. Initial seed outcomes survive interruption, and an already successful intended starting pot is retained over same-outcome refinements whose only benefit is proximity or power. The latter correction was required by the existing open-table Medium test: expanded search had found a weaker, fragile same-target pot. Its original assertions pass again without changing Medium error.

Benchmarked the unchanged `3edd07e` revision and final implementation on the connected OnePlus 8T (KB2003, API 34), using the same six layouts, random seed 17, cold/warm worlds and exact replay validation. Final timed requests completed 93–206 evaluations in 5,000 ms versus 64 before. Both blocked layouts changed from predicted/replayed wrong-first fouls to legal kick pots; the open-table timed fixture found two legal pots instead of one. Fixed-attempt and identical-command physics benchmarks also ran on desktop and device. The phone's repeated raw physics timings varied in direction, so no consistent per-step speedup is claimed. See [review, measurements and reproduction](ai-planning.md), including committed CSV samples and measurement limitations.

Core suite: 206 tests, 203 passed and three intentional opt-in skips; no failures. Android lint/debug assembly, all 121 rendered scenarios and desktop audio smoke tests passed. The final device acceptance run took 56.136 seconds: six functional tests passed, with two opt-in benchmark methods skipped; the two benchmark methods separately passed with their flag enabled. Device coverage includes the solid/eight blocker cases with exactly one cue cushion before a legal stripe contact, the original Medium open-table fixture, residual cushion settlement, oblique export depth, real-shot replay/Settings/chooser return, export cancellation/retry and recipient playback.

The initial replay acceptance timeout was caused by the saved replay setting being disabled. The test now enables replays in its in-memory fixture only. The saved settings were verified unchanged afterward (replays and aided aim off, Hard difficulty). No production replay setting or behavior was changed. Detailed local evidence is in `build/reports/planning/oneplus-final-suite.txt`, `oneplus-final-benchmark.txt`, `oneplus-legality.txt`, `oneplus-open-table.txt` and before/after CSV/detail reports.

The final debug app remains installed and was launched; the test APK was removed. Static gallery images remain representative. A fresh 30-second production gameplay recording and committed preview were generated (900 frames, H.264/AAC, approximately 564 KB, actual cue/collision/cushion/pocket audio); sampled frames were visually reviewed. The recording is local at `build/reports/gameplay-demo/gameplay.mp4` and attached to [PR #18](https://github.com/ffeltrinelli/pool-android/pull/18), with the current attachment linked in README.


## 25 October 2026 — ball-in-hand placement follow-up

At 17:54:36 the OnePlus logged 38 completed evaluations in five seconds, with 21 direct-phase and 20 rebound-phase attempts. The selected result was a 1.00 N·s `CONTACT` shot hitting ball 2, predicting an incidental pot of ball 1. Read-only debugger inspection confirmed a computer solids shot with no actual pocket and no foul. Replay recording was disabled and the original table snapshot was no longer retained. The live rack was resumed without changing it. A settled post-miss table was captured as a shared test fixture; it is not claimed as the original pre-shot layout.

The placement shortlist now prioritizes clear cue-to-target and target-to-pocket lanes, shares ordinary direct-shot geometry, retains each intended seed, and covers different legal targets before duplicate routes. It tries the established 25 cm alignment plus shorter and longer alternatives. Clear routes precede unchecked aligned positions and safe grid fallbacks; retaining unchecked alternatives preserves native winning-eight coverage where the predictor is conservative around jaws. A clear placement shortlist gets at most two contact alternatives before direct refinement, avoiding the previous potential 32-contact starvation. The worker retains one immutable last request independently of replay preferences, and the selected cue position is logged.

All 209 core tests completed with 206 passing and three opt-in skips, including post-miss direct planning, blocked-lane/target diversity, generation cancellation and existing winning-eight placements. The focused desktop placement benchmark completed 250 simulations in 1,820 ms warm with the same attempt/deadline bounds. The Android fixture is packed only in the test APK. Fresh local rendered/audio reruns could not initialize GLFW because no primary monitor was available; they failed clearly at graphics startup. Fresh [PR CI](https://github.com/ffeltrinelli/pool-android/actions/runs/40216700061) passed core tests, Android lint/build, all rendered fixtures and audio checks on code commit `62842b0`.

With the user’s approval, the fixed APK was installed on the OnePlus 8T/API 34. The shared post-miss regression selected a `DIRECT_POT` on ball 2 and independently pocketed it without a foul, completing 41 simulations in 5,000 ms with zero rebound attempts. The previous installed build also passed this post-miss fixture (32 simulations), so this is not an exact reproduction of the original incident. The focused placement benchmark selected and independently potted ball 9 in all four requests without a foul or fallback: fixed cold/warm 31 evaluations in 938/539 ms; timed cold/warm 207/219 in 5,000 ms each. The earlier warm timed result was 144 evaluations; these observations are not thermally controlled. See [AI planning review](ai-planning.md) and its raw CSV samples.

The final OnePlus suite passed all seven functional methods; the two opt-in benchmark methods skipped as intended. This includes the original Medium intended pot, both blocked-target kick cases, the captured placement, residual settling, oblique replay export, Settings/chooser return, and export cancellation/retry with playback. A separate cold launch took 460 ms; Home/resume retained the process and open Settings, two restarts returned to a fresh rack, and saved Hard/replays Off/aided aim Off preferences remained byte-identical. The phone is left on the fixed build at fresh human placement. Local evidence is under `build/reports/planning/oneplus-placement-*` and `placement-lifecycle/`.

## 25 October 2026 — captured direct rail-pot request

The retained `WorkerShotPlanningService.lastRequest` allowed read-only capture of the exact pre-shot table after the next reported miss, without resetting the live rack. The OnePlus log at 18:42:35 shows seed 4 selecting `KICK_CONTACT` on ball 1 after four direct-contact trials and 29 rebound attempts (32 completed simulations / 5,000 ms). It predicted potting ball 1; live rules recorded no pocket and no foul. Unlike the earlier placement report, `core/src/test/resources/opponent/missed-direct-request.json` contains the actual pre-shot poses, order and cue position.

The old generator returned no direct pots. Ball 4 was against the left cushion, with a legal direct route down that cushion to the corner. Aiming only toward the pocket center sent its predicted path into the cushion. Independent native desktop trials of the straight route potted ball 4 legally at 0.18, 0.24, 0.30 and 0.40 N·s. Direct geometry now includes axis-aligned pocket entries and validates travel to the actual mouth intersection. These remain direct shots, after centered seeds fail and before rebound planning, within the existing phase and request budgets.

Before installing the fix, the new exact-request device test reproduced the old planner's rebound selection: 25 completed simulations / 5,000 ms, four direct and 22 rebound attempts. That timed rerun selected a different kick which independently potted balls 2 and 7; it reproduces the missing direct candidate, not the exact historical miss. After installation, the same production-budget test selected ball 4 directly at 0.283 N·s and independently potted it without a foul: 51 completed simulations / 5,000 ms, 64 direct and zero rebound attempts. Fixed seeds do not remove timing-dependent search differences.

All 213 core tests completed with 210 passing and three opt-in skips. The exact-request test independently executes the selected command; mirrored geometry tests reject blocked lanes and invalid mouth crossings. The existing intended-pot/Medium and winning-eight tests remain unchanged and pass. Android lint/debug/test APK assembly, all 121 rendered fixtures, and desktop audio checks passed locally. Earlier local GLFW initialization failure no longer occurred on this rerun. Current gameplay media does not depict the reported rail shot and remains representative. Detailed local phone logs are under `build/reports/planning/direct-request-*`.

The complete OnePlus suite passed eight functional methods, with the two opt-in benchmark methods skipped. Both the new exact-request rail pot and the previous ball-in-hand capture passed alongside the original Medium pot, blocked-target kicks, settling, oblique export and replay/Settings/chooser/export playback checks.
