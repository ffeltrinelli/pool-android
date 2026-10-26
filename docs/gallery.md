# Current screenshots and gameplay video

The [README](../README.md#screenshots) uses desktop captures from the production renderer at 1080 × 2400. They are not device screenshots. Settings includes a **?** help button opening the illustrated [how-to-play overlay](images/help.png) and a bar-chart button opening [Stats](images/stats.png). The Stats capture uses sample results to demonstrate the per-difficulty table and Reset button.

## Refresh procedure

With JDK 21 and a working graphics context:

```sh
./gradlew :lwjgl3:visualTest
python3 tools/update_gallery.py
```

On headless Linux, prefix the Gradle command with `xvfb-run -a`. Check `build/reports/visual/index.html` for a successful run, then visually inspect each copied image. The copy script checks capture presence; it does not establish freshness or visual correctness. Run the suite on the current checkout before copying.

Each deterministic scenario uses a fixed test setup so its screenshot can be reproduced.

| Gallery file | Deterministic source scenario |
|---|---|
| `splash.png` | `gallery-splash` |
| `top-view.png` | `gallery-top` |
| `player-view-start.png` | `gallery-player` |
| `settings.png` | `gallery-settings` |
| `help.png` | `gallery-help` |
| `stats.png` | `gallery-stats` |
| `placement.png` | `gallery-placement` |
| `result.png` | `gallery-result` |
| `replay-close.png` | `replay-close` |
| `replay-overview.png` | `replay-overview` |
| `replay-diagonal.png` | `replay-diagonal` |
| `replay-hold.png` | `replay-hold` |

Visual PRs must review the gallery and update affected images and captions in the same PR. Remove obsolete images rather than adding historical before/after copies. Put comparisons in accessible PR attachments; Git history retains superseded assets. CI generates the complete visual report on every PR but does not update committed gallery images or compare screenshot baselines.

The table-concepts SVG remains a rules diagram, maintained against shared table geometry. It is not a gameplay screenshot. Android launcher masking and startup transitions require separate device or emulator inspection.

## Gameplay video

The gallery procedure produces a 30-second, normal-speed desktop recording (540 × 1200, 30 frames per second, H.264 video compression and AAC audio compression). It runs `PoolGame` with regular pointer gestures, production physics, rules, camera transitions, and the real computer planning worker. It is not an Android device capture. The procedure is repeatable; rack shuffling and worker timing mean recordings need not be identical.

With JDK 21, a working OpenGL context, Python 3, `ffmpeg`, and `ffprobe` on PATH:

```sh
python3 tools/record_gameplay_demo.py
```

The recording must include human and computer shots with production sound timing. Preferences and capture controls stay in test tooling. On headless Linux, prefix the command with `xvfb-run -a`. See the [repository credits](../README.md#assets-and-attribution); new video metadata links there.

Outputs are `build/reports/gameplay-demo/gameplay.mp4`, `audio-events.json`, and `verification.json`. The script rejects missing meaningful audio, clipping, unexpected codecs/frame count, duration other than 30 seconds, or size above 10 MB. It copies the five-second poster to `docs/images/gameplay-preview.png`. The screenshot refresh above does not overwrite that poster. `--reuse-capture` combines fresh video and audio from the current checkout into a new MP4 without replaying gameplay.

Watch the entire clip with sound: check natural pacing, readable HUD, cue and camera motion, computer turns, and synchronized impacts. Upload the verified clip to the current PR, and use its accessible attachment URL in the PR description:

```sh
gh pr edit NUMBER --attach build/reports/gameplay-demo/gameplay.mp4
```

GitHub renders the attachment link as an inline video player; its audio starts muted until the viewer unmutes it. The committed poster remains available for previews.

Keep only the poster in Git; the MP4 and capture intermediates stay in ignored build reports. Keep the poster current with the generated recording. Keep before/after media in accessible PR attachments. Review the video whenever a PR changes depicted visuals, gameplay, or audio; re-record when it becomes outdated, not for unrelated changes. CI tests the audio mixer and retains existing screenshot/audio reports, but does not record or compare videos.

## Android replay export verification

With one unlocked, authorized device connected, build and run the test APK:

```sh
./gradlew :android:connectedDebugAndroidTest
```

The test APK (Android application package) runs fixed fixtures—saved test setups—on the phone. Device acceptance covers replay/Settings suspension, the five-second hold, Share/return, cancellation/retry, recipient playback, and oblique export depth. Fixtures and the recipient activity stay in `androidTest`; no personal sharing target is used.

The test saves `files/replay-device-test.mp4` and `files/replay-real-shot.mp4` inside the debug app. With multiple devices, target the intended serial explicitly when retrieving evidence:

```sh
adb -s SERIAL exec-out run-as io.github.ffeltrinelli.pool cat files/replay-device-test.mp4 > build/reports/replay/device-export.mp4
python3 tools/verify_replay_video.py build/reports/replay/device-export.mp4 --replay-seconds 3.3466667
```

The verifier requires FFmpeg/ffprobe. It checks H.264/AAC, portrait dimensions and frame rate, the 60-frame static splash followed by replay, silent intro, meaningful unclipped replay audio, repository-link metadata, and the expected duration without the five-second decision hold. Share-sheet screenshots may contain private Android suggestions; keep them in local build reports rather than the committed gallery.
