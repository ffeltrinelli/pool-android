# Agent instructions

## Clean code

Use the `clean-code` skill. Preserve behavior and local reasoning, name concepts precisely, separate mutation from queries, and keep resource cleanup explicit. [Source guidance](https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md).

## Repository orientation

- Start with [README](README.md) for current rules, controls, architecture, tuning, and setup. Read only the sections and source relevant to the task.
- `core` contains shared Kotlin gameplay, Bullet physics, rendering, audio, and opponent planning; `android` owns the Android launcher; `lwjgl3` owns the desktop launcher and rendered/audio test tooling.
- Use [device validation](docs/device-validation.md) for dated observations and outstanding checks. Historical results are not validation of a new build. Keep detailed decisions and validation evidence in README or `docs`, with links here only when they help future work.

## Build and verification

Use the committed Gradle wrapper with JDK 21. Follow [build setup](README.md#build-and-run) for the Android SDK; keep machine-specific paths in environment variables or ignored `local.properties`.

```sh
./gradlew :core:test :android:lintDebug :android:assembleDebug
./gradlew :lwjgl3:visualTest
./gradlew :lwjgl3:audioTest
```

- Run focused checks while changing code and the relevant full suites before delivery. For documentation-only edits, validate links and guidance against source/build configuration; gameplay suites need not be rerun locally.
- Before delivering app changes, run `adb devices -l` to check for a connected physical debug device. When an authorized device is available, build the current debug APK, install it with `adb -s SERIAL install -r android/build/outputs/apk/debug/android-debug.apk`, launch it, and test the changed behavior on the real device, including relevant lifecycle checks. Target the intended serial explicitly when multiple devices are connected. Use the [device validation procedure](README.md#oneplus-8t-validation) where applicable; its scripted gestures require an unlocked 1080 × 2400 phone. Record dated results and limitations in `docs/device-validation.md`; if no usable device is available, report that device validation was skipped and why.
- Rendered and audio smoke tests require a graphics context. On headless Linux, use `xvfb-run -a`; audio verification can use `ALSOFT_DRIVERS=null`. A requested rendered run must fail clearly if graphics initialization fails. See [verification details and report locations](README.md#automated-verification) and the [PR CI workflow](.github/workflows/verify.yml).
- Keep deterministic fixtures and screenshot capture in test tooling. Capture on the graphics thread after a specified simulation step; do not add production setup controls or wall-clock sleeps. Use physical tolerances for native tests and evaluation budgets for deterministic AI tests. Difficulty calibration is opt-in. Replay export device tests use `:android:connectedDebugAndroidTest`; see [export verification](docs/gallery.md#android-replay-export-verification). Keep test-recipient activities and deterministic setup in `androidTest`, never production launch controls. Observe chooser launch through Android lifecycle callbacks in device tests; the graphics thread may pause before another frame can report sharing. Configure replay fixtures independently of saved player preferences without overwriting those preferences.

## Architecture and resource ownership

- Replay must consume immutable recorded presentation/audio data, never restore the live Bullet world or deliver rule events. Keep playback/hold timers separate from match time. Apply motion-speed conversion once to the shared clip before playback/export; keep the countdown clock independent. Android export owns codecs/EGL on its worker; offscreen rendering and framebuffer disposal remain on the graphics thread. Increase the replay near plane during pullback and prefer a 24-bit export depth buffer; libGDX’s basic framebuffer depth is only 16-bit. Validate oblique export views. See [replay behavior](README.md#pocket-replays-and-sharing).
- Resolve residual contact jitter through bounded pose stability, never a blanket shot timeout. Put settled Bullet bodies to sleep after zeroing velocities; verify later cue strikes and object-ball collisions wake them. Copy native velocity return values before another native vector query, because libGDX Bullet reuses temporary vectors.
- Keep eight-ball decisions pure in `EightBallRules`, shared by `GameSession` and silent shot evaluation. Physics reports facts; rule events and audio events remain separate. Record persistent stats only from live match completion; silent evaluation and replay must never update them.
- Keep automatic camera selection in `GameViews`, based on turn ownership and placement rather than shoot readiness; human ball motion retains Player view. Keep view-specific input mapping there, shared stroke interpretation in `CueGesture`, and placement input separate. Use `HudLayout` for both UI hit regions and the game viewport offset; table rendering and pointer mapping must agree when HUD height changes.
- Preserve a pot candidate’s target-ball identity through optimization and placement seeds; incidental pots must not replace intended pots merely for lower impulse. Keep wins, losses, fouls, and legal pot count ahead of that preference.
- Validate pocket-route obstruction to the actual ray/mouth intersection; radial distance to the mouth center rejects valid off-center entries. Planar jaw rejection can be conservative: keep any native-evaluation exception bounded to the intended mouth and check object-ball occlusion separately. Cover captured AI failures with immutable pre-shot fixtures and independent native execution.
- Keep direct-pot and rebound phases within one request deadline and attempt cap. Generate only single-cushion rebounds after direct-pot planning fails; verify the actual cushion route in native tests. Run the opt-in [planning benchmarks](docs/ai-planning.md) for throughput claims, including on an available physical device.
- Shared opponent layouts live in `core/src/test/resources/opponent` and are packed only into Android test assets. `WorkerShotPlanningService.lastRequest` retains the last immutable request for debugger capture even when replays are off; inspect it before resetting or reinstalling after an AI report.
- Keep opponent execution accuracy separate from shot planning. Transfer immutable table/rules snapshots and results across the worker boundary; the planning worker exclusively owns and disposes its Bullet world. Preserve cancellation and stale-result guards.
- Preserve settled ball position/rotation when evaluating ordinary shots; reconstruct the cue only for actual placement. Render ball markings from the interpolated Bullet transform, without separate visual rotation.
- Give every native/graphics/audio allocation an explicit owner and cleanup path. Dispose bodies before their shared collision shapes and world dependencies; restart must clear per-rack state without leaking resources or replaying pending audio. Settings/background suspension must stop simulation and resume without accumulated time.
- Use shared `TableGeometry`/`PocketGeometry` definitions for visible geometry, colliders, placement validation, and prediction. Keep tuning centralized; avoid incidental physics, camera, or AI tuning changes during unrelated work.
- Keep both standard and round Android launcher icons adaptive. Verify branding in the actual launcher drawer/search as well as App info; follow [branding asset guidance](assets/branding/README.md).
- Render contact shadows as translucent cloth-level planes after opaque geometry, with depth testing and no depth writes; avoid raised solid shadow meshes. See [rendering notes](README.md#architecture-and-tuning).
- Generate UI fonts through `UiFonts` at the actual render-target pixel width; do not enlarge the small default bitmap font. Keep font generation outside drawing queries and dispose replaced atlases. See [font attribution](assets/fonts/README.md).

## Pull request descriptions

Keep docs and PR descriptions focused on concepts, rationale, and evidence that source does not explain. Briefly explain unfamiliar concepts and expand specialized acronyms on first use; preserve navigation aids. Keep external-resource credits in the README and linked asset notices; replay share text and video metadata should link to the repository.

Include screenshots of the game in PR descriptions when relevant to the changes. For changes to existing visuals, include before-and-after screenshots where practical. Briefly caption screenshots to identify what they demonstrate. Screenshots are optional for changes without a relevant visual effect. Use accessible image attachments or links, not local filesystem paths.

## Current documentation gallery

- Visual changes must review the current gallery and refresh affected images and captions in the same PR. Remove obsolete media; keep historical comparisons in PR attachments.
- Review the README gameplay video and preview when changing depicted visuals, gameplay, or audio. Re-record them in the same PR when outdated; unrelated changes do not require a new recording.
- Follow [gallery capture and refresh](docs/gallery.md). CI uploads fresh reports but does not update committed gallery images.
