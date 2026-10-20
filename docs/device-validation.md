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
