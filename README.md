# Feltri 8-Ball — eight-ball against the computer

An offline 3D eight-ball game for Android. Play solids versus stripes against a local computer opponent, with simplified WPA rules, physical pockets, and continuous touch aiming and strokes.

## Screenshots

<table>
<tr><th>Player view before the break</th><th>Replay: cue stroke</th><th>Replay: full table</th></tr>
<tr>
<td><img src="docs/images/player-view-start.png" width="240" alt="Player view with the cue aimed toward a fresh rack."></td>
<td><img src="docs/images/replay-close.png" width="240" alt="Low replay camera behind the cue ball, showing the cue, soft contact shadows, and square Share button."></td>
<td><img src="docs/images/replay-overview.png" width="240" alt="Replay after first contact, showing the entire table and Share and Skip controls."></td>
</tr>
</table>

See the [screenshot gallery](docs/images/), including the [diagonal replay view](docs/images/replay-diagonal.png), and [media refresh procedure](docs/gallery.md). The current [30-second gameplay video with synchronized game sounds](https://github.com/user-attachments/assets/7ac66daf-dac7-452c-bdff-470245067b74) is attached to [PR #18](https://github.com/ffeltrinelli/pool-android/pull/18); its [preview](docs/images/gameplay-preview.png) is committed.

## Build and run

Requirements: JDK 21, Android SDK platform 36, and build-tools 35.0.0. Android Studio can import the root Gradle project. The Gradle wrapper is included; dependencies need network access on the first build.

In Android Studio, select **JDK 21** as the Gradle JDK in Settings → Build Tools → Gradle; this matters if the IDE bundles JDK 25.

Set `JAVA_HOME` to your JDK 21 installation. Set `ANDROID_HOME` to your Android SDK, or create an ignored `local.properties` containing `sdk.dir=/absolute/path/to/sdk`.

```sh
./gradlew :android:assembleDebug
adb install -r android/build/outputs/apk/debug/android-debug.apk
adb shell am start -n io.github.ffeltrinelli.pool/.android.AndroidLauncher
```

Requires Android API 26 or newer. This is a sideloaded demo.

For desktop development:

```sh
./gradlew :lwjgl3:run
```

The Gradle task adds macOS's required `-XstartOnFirstThread`. Mouse dragging uses the same gesture interpreter as touch. Collision, cue and pocket-drop sounds play on both platforms and follow the device media volume.

## Rules of this game

Clear your seven solids (1–7) or stripes (9–15), then legally pocket the eight in any pocket to win. The table stays open after the break; the first subsequent legal shot pocketing only one group assigns it to the shooter. Keep shooting when you legally pocket your own ball, otherwise the turn passes. Hit your group first (the eight only after clearing your group); if nothing is pocketed, a ball must reach a cushion after contact. Scratches, wrong/no first contact, off-table balls, and failure to reach a cushion are fouls. The incoming player gets cue ball in hand. Pocketing the eight early, while fouling, or knocking it off-table loses the rack. There are no ball-count scores or draws.

| Situation | This game's rule |
|---|---|
| Breaker | Human on application launch; Restart and Play again alternate breakers |
| Fresh break | Place the cue ball behind the head string; pocket a ball or send four distinct object balls to cushions |
| Insufficient break | Message, automatic fresh rack, opponent breaks |
| Eight on break | Return it to the foot spot, or nearest available position on the spotting line; never an immediate win/loss |
| Break foul | Opponent places behind the head string; cross that line before hitting a ball behind it |
| Ordinary foul | Opponent places anywhere valid; pocketed object balls stay down |
| Open-table mixed pot | Groups remain unassigned; the shooter continues if there was no foul |
| Opponent's ball pocketed | Stays down; not itself a foul, but does not earn another shot |
| Last group ball and eight together | Loss: the group must be clear before the eight-ball shot starts |
| Scratch while attempting eight | Ordinary foul if the eight stays on the table; loss if it is pocketed too |

The source is the [WPA Rules of Play, effective 15 September 2025](https://www.wpapool.com/wp-content/uploads/2026/01/2026.01.02-WPA-Rules.pdf), especially sections 1–4. **Our deviations:** no called shots or called pocket for the eight; legal accidental pots count; mixed-group open-table pots use the rule above; break choices resolve automatically; and there is no automatic referee/stalemate procedure. Use Settings → Restart for a stuck rack. On the rare open table with one group already completely gone, the eight is a legal winning target. Restricted placement also handles WPA's nearest-legal-ball spotting exception if every eligible target lies behind the head string.

![Portrait table diagram: head end and shaded break-placement area below the head string, foot spot and rack at the opposite end, six pockets, centre line, and enlarged rack showing the central eight and opposite-group rear corners.](docs/images/eight-ball/table-concepts.svg)

The head string lies one-quarter of the table length from the head end; the foot spot lies one-quarter from the foot end. The rack's apex sits on the foot spot. Its eight is centred in row three and the rear corners contain opposite groups; other positions are shuffled. The suggested cue-ball position is not mandatory. Guide lines in this diagram explain the geometry; gameplay draws placement guides only when needed.

## Controls

- **Place:** tap or drag the cue ball in Top view, then **Confirm placement**. Red positions are invalid.
- **Aim:** drag left or right in Player view to turn. The optional dashed guide shows the cue ball’s first contact, without predicting rebounds.
- **Strike:** drag down to pull back, then push up until the cue touches the ball. Faster forward motion means more power. Practice strokes can stay under one finger; lift to re-aim or cancel before contact.
- Wait for the balls to stop before shooting again. Views switch automatically between your shots, placement, and computer turns.
- The **gear** opens Settings. **?** beside **X** shows illustrated instructions. X or Android Back returns one screen; play remains paused until Settings closes.
- Aided aim, replays, and difficulty are remembered. Restart alternates breakers; Exit closes the app. Racks are not saved.

## Pocket replays and sharing

Legal scoring shots replay automatically at 75% speed with synchronized game sounds. The final picture stays for five seconds; **Skip** returns immediately. Settings and backgrounding pause playback and the countdown.

On Android, **Share** creates a portrait MP4 and opens the system share sheet. Returning resumes the replay. Export can be cancelled or retried after failure. Videos include a two-second title/author intro and game audio, with controls and the decision hold omitted. Shared text and video metadata link to this repository for credits. Export uses temporary cache files; it needs no microphone or storage permission. Desktop supports playback only.

## Architecture and tuning

Live play and opponent evaluation share Bullet physics and eight-ball rules. Replays use recorded presentation and sound rather than resimulating a shot, avoiding changes to the live match. Planning and Android encoding each own their native resources on a worker; rendering resources stay on the graphics thread.

Table dimensions and pocket shapes are shared by rendering, physics, placement, and prediction. Pockets use gravity-driven entry rather than a scripted sinking animation. Dimensions follow the [WPA equipment specifications](https://www.wpapool.com/wp-content/uploads/2024/01/RECOMMENDED-EQUIPMENT-SPECIFICATIONS.pdf), without claiming a complete regulation simulation. There are no spin or jump-shot controls.

The camera prioritizes readable human shots over keeping every rail visible; Top view fits the full table. The short displayed cue is an appearance choice. Contact shadows blend at cloth level without writing depth. Replay pullback raises the near plane and export prefers 24-bit depth because oblique views exposed depth fighting with 16-bit depth. UI text is rasterized at the render target’s pixel density.

Bounded pose stability allows residual contact rocking to settle without a blanket shot timeout. Settled bodies sleep until a strike or collision wakes them. Backgrounding cancels input and freezes play; resume discards accumulated time. Settings and OS suspension remain independent.

Tune geometry, physics, gestures, views, and audio in their respective tuning definitions. Numerical settings and class inventories belong in source. See [planning rationale and measurements](docs/ai-planning.md) before making opponent throughput claims.

## Automated verification

Use JDK 21 and the configured Android SDK:

```sh
./gradlew :core:test :android:lintDebug :android:assembleDebug
./gradlew :lwjgl3:visualTest :lwjgl3:audioTest
```

Rendered/audio smoke tests need a graphics context and fail clearly when unavailable. On headless Linux use `ALSOFT_DRIVERS=null xvfb-run -a` before Gradle; the null audio driver checks decoding and allocation, not speaker output. Native assertions use physical tolerances; deterministic planning tests use evaluation budgets rather than exact timings. Screenshot fixtures support human review, without image baselines.

Reports are in `core/build/reports/tests/test/`, `android/build/reports/lint-results-debug.html`, and `build/reports/{visual,audio}/`. PR CI runs these checks and uploads reports and APKs.

Opt-in checks: `:core:difficultyCalibration`, [planning benchmarks](docs/ai-planning.md#reproduction), and [Android replay export tests](docs/gallery.md#android-replay-export-verification). See [gallery capture](docs/gallery.md) for screenshots and gameplay recording.

## OnePlus 8T validation

The connected acceptance device is **OnePlus KB2003 / OnePlus 8T, Android API 34, 1080 × 2400, ARM64**. See [device validation](docs/device-validation.md) for observed results and remaining hands-on checks.

The opt-in device script operates the foreground game on an unlocked 1080 × 2400 phone, including Home/resume, settings-based restarts and opening break swipes. Camera views follow placement and turn ownership automatically. It stops before sending more touch input if the game leaves the foreground and does not change system settings:

```sh
python3 tools/device_check.py --adb "$ANDROID_HOME/platform-tools/adb" --seconds 600
```

It writes screenshots and `soak.json` under `build/reports/device/`. Performance uses short SurfaceFlinger presentation-time windows sampled every ten seconds; it is not a continuous frame trace. Run with only the intended adb device connected. Gesture comfort, deliberate potting from different positions, and slow/medium/fast touch calibration still require hands-on testing.

## Assets and attribution

Credits for external resources used in Feltri 8-Ball: [libGDX and its Bullet/FreeType extensions](https://libgdx.com/) provide the game engine, physics, and font rendering; [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) supplied the project scaffold; [Apache Commons Math](https://commons.apache.org/proper/commons-math/) supplies CMA-ES under [Apache 2.0](assets/licenses/Apache-2.0.txt), with its [notice](assets/licenses/commons-math3-NOTICE.txt). Ball collisions use “billiard ball clack” by [Za-Games](https://freesound.org/people/Za-Games/sounds/539854/), and cushion/cue effects use [Kenney Impact Sounds](https://kenney.nl/assets/impact-sounds), all under CC0. “Pool ball falling into pocket” by **freesman**, [Directory.Audio](https://directory.audio/sound-effects/sports/26410-pool-ball-falling-into-pocket), is licensed under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/); our edits downmix, resample, trim, and fade the recording. [Audio credits](assets/audio/README.md) retain asset mappings and notices. The UI uses **Lato Regular by Łukasz Dziedzic**, under [SIL OFL 1.1](assets/fonts/OFL.txt); ball numbers use libGDX’s default font. OpenAI image generation produced the [icon and splash artwork](assets/branding/README.md). Table, cue, and ball visuals are built from engine primitives and project source. Development guidance draws on [ciembor/agent-rules-books](https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md).

## Playing against the computer

The opponent plans one shot at a time offline. Geometry supplies promising direct pots and legal contacts; CMA-ES varies their direction and power, and Bullet evaluates the outcomes under the same rules as live play. If direct-pot planning fails, it tries single-cushion banks and kicks within the same deadline. Wins, avoiding fouls, and legal pot count take priority. Intended pots outrank equally scoring incidental pots because lower power alone proved a poor guide to reliability.

All difficulties share the search budget. Easy and Medium add execution error; Hard uses the planned command exactly. Exact execution cannot guarantee a pot because the restored simulation approximates live native contacts. The planner has no multi-turn lookahead or learning. Settings and background suspension preserve the selected plan without accumulating execution error; restart discards it.

See [AI planning](docs/ai-planning.md) for captured failures, design tradeoffs, benchmark evidence, and reproduction. [Device validation](docs/device-validation.md) records build-specific observations and outstanding hands-on checks.
