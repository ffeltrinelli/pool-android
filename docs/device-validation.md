# Device validation

Observations apply to the build tested on that date. They are not acceptance of later builds. Device: **OnePlus 8T / KB2003, API 34, ARM64, 1080 × 2400**. Current behavior is in the [README](../README.md). Detailed older narratives and removed media remain in Git history; local `build/reports/` evidence is not committed.

## Outstanding hands-on checks

Subjective finger comfort, slow/medium/fast stroke calibration, shots beside every rail, full-match play, and sustained audiovisual performance still need human review. Scripted gestures and fixed layouts do not establish these. No recent change has repeated the original ten-minute soak.

## Initial gameplay and audio — 19–21 October 2026

- Initial soak: **601.3 seconds**, 20 restart/break cycles, 58 frame windows, no detected process restart or script error. Median sampled rate **59.80 FPS**; after the first 20 seconds, windows ranged **59.30–60.28 FPS**, maximum sampled p95 interval **16.94 ms**. Ten-second sampling is not a continuous trace or proof of a 60 FPS floor. Evidence: `build/reports/device/soak.json`.
- Audio follow-up: 35.4 seconds, two restart/break cycles and Home/resume passed. Median of four frame samples was 57.39 FPS. A startup pause-before-create failure was reproduced and fixed; a later attempt stopped because the screen was off. Evidence: `build/reports/device-audio/`.
- The user accepted the sound direction and selected Za-Games’ ball clack. That selection followed the scripted audio run, so it was not covered by that run. Later pocket-drop changes require their own listening acceptance.
- Cloth support, moving ball markings, bidirectional practice strokes, circling, Player view, physical pocket entries, and aided aim received scripted device checks. Subsequent user feedback drove camera framing, resting-gap, and guide corrections. Those observations do not substitute for current-build gesture acceptance.

## Computer and difficulty — 23 October 2026

Settings interactions, saved difficulty, restart, and Home/resume were exercised on the phone. Easy’s historical calibration targeted approximately 30% fixed-layout pot success; this is **not** an eight-ball win-rate measurement. The user accepted cue visibility over rails, including practice strokes. Historical mode/view preferences described in older evidence have since been removed.

## Computer-only play and clean-code cleanup (2026-10-24)

143 unit/native tests passed, one calibration skipped; 121 rendered scenarios and audio/lint/APK checks passed. Phone checks covered settings taps/slider, outside-modal blocking, X/Back, three restarts, preference persistence, Home/resume with Settings open, and Exit. Production human breaks reached computer planning and returned to the human turn. Planner observations were 20 and 30 evaluations in 5,000 ms without fallback.

Committed evidence: [lifecycle](validation/computer-only-lifecycle.json), [turns](validation/computer-only-turns.json). A full match, deliberate scratch, subjective touch/audio review, and a new soak were not repeated.

## Simplified eight-ball (2026-10-24)

Initial checks: 161 unit/native tests passed, one calibration skipped; 135 rendered scenarios and audio/lint/APK checks passed. Phone observations covered placement, group/turn display, and reserved HUD space. Later focused legality checks reached 166 passing tests. The user’s historical no-cushion shot lacked a replayable initial state, so those regressions did not establish its exact cause.

Rules and architectural rationale are in the [eight-ball design note](eight-ball-review.md). Historical ball-count scoring and human-only fresh-break descriptions apply only to their original builds.

## Branding, typography, and recording — 24 October 2026

- The adaptive icon initially appeared reduced on a white plate and sorted under the old name. After correction, the launcher drawer showed Feltri 8-Ball in the F group with the intended full-size dark icon. Typed Global Search was not rechecked. Evidence: `build/reports/device-branding/launcher-corrected.png`.
- Inspected splash, HUD, and Settings text on the phone; Home/resume retained Settings. The final typography checks passed 173 unit/native tests (one calibration skipped), 141 rendered scenarios, lint and APK assembly. Device screen recording exited with status 139, so coarse screenshots supplemented controlled-time startup tests.
- The desktop gameplay recording was verified at 900 frames / 30 seconds, H.264/AAC, with real production sound commands. It is not a phone or microphone recording. Later recordings supersede its visuals. No new full match, speaker acceptance, or soak was claimed.
- An apparent eight-ball freeze completed during inspection. Only small cushion-area motion was visible; the debugger was attached after settlement, so that capture did not establish pre-settlement velocities.

## Automatic cameras — 25 October 2026

176 unit/native tests passed (one calibration skipped), 113 rendered scenarios and audio/lint/APK checks passed. The phone cold-launched in 633 ms. Placement showed the whole table; confirming switched to Player view. A human break retained its pullback, and the alternating computer break stayed overhead before handing back to the human.

Opening Settings during motion froze the decoded game pixels. Home/resume retained PID 1258, Settings, and those pixels; comparison excluded the cutout and gaming overlay. No app-specific fatal, physics, or audio errors were observed. Evidence: `build/reports/device-automatic-cameras/`. This was a scripted smoke check, not a full rack, soak, or speaker acceptance.

## Replay and Android export — 25 October 2026

The physical device passed export cancellation/retry, H.264/AAC generation, cache URI access, and complete recipient playback using temporary read permission. A deterministic legal shot covered Settings suspension, Share during the final hold, chooser dismissal, and return. No personal sharing target was used.

The initial synthetic export had 141 frames with 60 silent splash frames; FFmpeg checks verified portrait video, meaningful unclipped audio, metadata, and omission of the decision hold. Close and full-table frames were inspected. A final local desktop rerun initially failed at GLFW startup because macOS reported no primary monitor; it did not reach rendering. Later reruns succeeded. Other codecs/devices remain untested. Evidence: `build/reports/replay/`.

### Depth correction

A user-supplied diagonal export reproduced cloth/cushion depth fighting in decoded frames. The corrected OnePlus export reported **24 depth bits** and clean borders. Four oblique angles each had fewer than 500 significantly different pixels against the corrected 16-bit reference, also exercising the fallback. All three then-current device tests passed. Earlier exports retain their old pixels. Evidence: `build/reports/replay/depth-before.png`, `depth-after.png`, and `depth-fixed-export.mp4`.

### Countdown, speed, and shadows

Each follow-up passed core, rendered, audio, lint/APK, and the three then-current device tests. The five-second countdown remained unchanged through sharing; 75%-speed exports retained the two-second intro and excluded the hold. The slowed synthetic clip passed verification at **161 frames / 30 fps**, with approximately 3.3467 seconds of replay/audio tail. Matching exported shadow frames showed the raised rim replaced by soft contact shading. Evidence: `build/reports/replay/{slow-motion-export.mp4,ball-shadow-before.png,ball-shadow-after.png,soft-shadows-export.mp4}`.

## Residual cushion motion — 25 October 2026

A live `MOVING` session had ball 11 rocking near a cushion at roughly 0.31 rad/s. The captured native fixture failed to settle after ten simulated seconds with the previous bound; its motion spanned approximately 0.6 mm. The corrected fixture settled, remained still, and woke normally on later strikes/collisions.

190 core tests passed (one calibration skipped); rendered/audio/lint/APK passed. Four device methods passed in 37.7 seconds. A chooser-observation race first blocked the suite: Android paused the GL thread before another frame could observe sharing. Lifecycle-based observation fixed the test. This was not a production sharing failure.

## Opponent regressions — 25 October 2026

Captured layouts and rationale are in [AI planning](ai-planning.md). Timed counts reflect different search choices and device scheduling; they are not isolated throughput measurements.

| Report | Observed evidence and independent device result |
|---|---|
| Open table | Live seed 1 chose a weaker incidental pot; Medium missed while the direct ball-3 seed succeeded. After correction: 30 evaluations / 5,000 ms, intended ball 3 potted legally. Five device methods passed in 45.3 seconds. |
| Blocked targets | Two original fixtures reproduced wrong-first fouls. Corrected timed searches completed 93–206 evaluations versus 64 before; both blocked layouts produced legal kick pots. Six functional device methods passed in 56.136 seconds; two benchmark methods ran separately. Raw phone physics repeats varied in direction, so no consistent per-step speedup is established. |
| Ball in hand | Live shot made no pot/foul; only the settled post-miss table was captured. Old and new builds both pass that table, so it does not reproduce the incident. Corrected device fixture potted ball 2 directly: 41 evaluations / 5,000 ms. Seven functional device methods passed. |
| Rail pot, seed 4 | Exact pre-shot capture omitted ball 4’s direct route. Old rerun selected a kick that potted other balls, reproducing the missing candidate rather than the live miss. Corrected rerun potted ball 4 directly: 51 evaluations / 5,000 ms, no rebound attempts. Eight functional device methods passed. |
| Ball 2, seed 11 | Exact capture omitted a valid jaw route. Old rerun selected a successful kick, again not the historical miss. Corrected direct shot legally potted ball 2 without cue rebounds: 33 evaluations / 5,000 ms. Nine functional device methods passed. |
| Green ball, seed 2 | Old rerun retained the live 0.70 N·s contact but potted after five target cushions. Corrected direct shot potted ball 6; its sole target cushion contact was inside the pocket throat: 33 evaluations / 5,000 ms, zero rebound attempts. Ten functional device methods passed. |

The final green-ball build passed **215 core tests**, with three opt-in skips, all **121 rendered fixtures**, audio, Android lint, and debug/test APK assembly. Device coverage included all captured layouts, original Medium accuracy, blocked-target kicks, settling, oblique export, Settings/chooser return, cancellation/retry, and playback. Home/resume retained the process and open Settings; Hard/replays Off/aided aim Off preferences stayed byte-identical. The temporary test APK was removed.

Evidence: [committed benchmark CSVs](benchmarks/), plus local `build/reports/planning/oneplus-*`, `direct-request-*`, `ball-two-*`, and `green-ball-*`. The placement follow-up’s local GLFW runs failed at startup; [CI on code commit 62842b0](https://github.com/ffeltrinelli/pool-android/actions/runs/40216700061) passed rendered/audio checks. Later local runs succeeded. Benchmark conditions and limitations remain in [the measurement note](ai-planning.md#measurement-method).

## Illustrated help and repository credits — 25 October 2026

217 core tests passed, with three opt-in skips; all 122 rendered scenarios, audio smoke, Android lint/APK, and two Python audio tests passed. Settings and help gallery captures were inspected. The existing gameplay recording remains representative.

On the OnePlus, inspected the illustrated overlay, blocked underlying taps/slider drags, and returned to Settings using both X and Back. Home/resume retained the process and Help; saved Hard/replays Off/aided aim Off preferences stayed unchanged. Both replay device tests passed in 31.018 seconds, including Help suspension, chooser return, cancellation/retry, and recipient playback. FFmpeg verification passed for a 161-frame export with silent intro, unclipped sound, and the repository URL as its metadata comment. No personal share target was used. Evidence: `build/reports/device-help/`. No new full-match, listening, or soak acceptance is claimed.

Button-symbol follow-up: enlarged and emboldened **?** and **X** within unchanged buttons; refreshed Settings/Help captures. Core/rendered checks and Android lint/APK passed. Installed on the OnePlus; Help navigation and Home/resume passed.
