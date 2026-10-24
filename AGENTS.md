# Agent instructions

## OBEY Clean Code by Robert C. Martin

Source: https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md

### When to use

Use when you need a small always-on bias toward readable, low-surprise code.

### Primary bias to correct

Working code is not automatically clean code.

### Decision rules

- Preserve behavior, write for the next reader, and leave touched code cleaner within scope.
- Write for local reasoning and use precise names with one term per concept.
- Split boolean flags, mixed abstraction levels, and hidden side effects out of functions.
- Separate commands from queries and keep parameters small and meaningful.
- Keep the happy path readable; make invalid states, errors, and cleanup explicit instead of implicit.
- Use comments only for rationale or contracts, not to explain confusing code.
- When touching code, remove the smell most likely to make the next change risky or unclear.

### Trigger rules

- When a function both mutates and answers, split it.
- When a comment explains the flow, simplify the code first.
- When async, concurrency, or framework quirks spread the change, reduce shared mutable state and add the right boundary instead of more branching.

### Final checklist

- Local reasoning preserved?
- Clear names?
- Clear mutation boundaries?
- One smell removed?

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
- Rendered and audio smoke tests require a graphics context. On headless Linux, use `xvfb-run -a`; audio verification can use `ALSOFT_DRIVERS=null`. A requested rendered run must fail clearly if graphics initialization fails. See [verification details and report locations](README.md#automated-verification) and the [PR CI workflow](.github/workflows/verify.yml).
- Keep deterministic fixtures and screenshot capture in test tooling. Capture on the graphics thread after a specified simulation step; do not add production setup controls or wall-clock sleeps. Use physical tolerances for native tests and evaluation budgets for deterministic AI tests. Difficulty calibration is opt-in.

## Architecture and resource ownership

- Keep eight-ball decisions pure in `EightBallRules`, shared by `GameSession` and silent shot evaluation. Physics reports facts; rule events and audio events remain separate.
- Keep view-specific camera/input mapping in `GameViews`, shared stroke interpretation in `CueGesture`, and placement input separate. Use `HudLayout` for both UI hit regions and the game viewport offset; table rendering and pointer mapping must agree when HUD height changes.
- Keep opponent execution accuracy separate from shot planning. Transfer immutable table/rules snapshots and results across the worker boundary; the planning worker exclusively owns and disposes its Bullet world. Preserve cancellation and stale-result guards.
- Preserve settled ball position/rotation when evaluating ordinary shots; reconstruct the cue only for actual placement. Render ball markings from the interpolated Bullet transform, without separate visual rotation.
- Give every native/graphics/audio allocation an explicit owner and cleanup path. Dispose bodies before their shared collision shapes and world dependencies; restart must clear per-rack state without leaking resources or replaying pending audio. Settings/background suspension must stop simulation and resume without accumulated time.
- Use shared `TableGeometry`/`PocketGeometry` definitions for visible geometry, colliders, placement validation, and prediction. Keep tuning centralized; avoid incidental physics, camera, or AI tuning changes during unrelated work.

## Pull request descriptions

Include screenshots of the game in PR descriptions when relevant to the changes. For changes to existing visuals, include before-and-after screenshots where practical. Briefly caption screenshots to identify what they demonstrate. Screenshots are optional for changes without a relevant visual effect. Use accessible image attachments or links, not local filesystem paths.
