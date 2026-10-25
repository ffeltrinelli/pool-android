# Current screenshots and gameplay video

The README embeds the player-view start and replay views and links to the [gallery folder](images/). Gallery captures use the production desktop renderer at 1080 × 2400; they are not Android device captures. Top view depicts a computer turn; placement uses the same overhead framing. Settings contains Aided aim, Replays, Difficulty, Restart and Exit game. Replay captures show the low cue camera, soft cloth-level ball shadows, and the full-table view after first contact. The start fixture confirms break placement and aims the resting cue toward the fresh rack before any shot.

## Refresh procedure

With JDK 21 and a working graphics context:

```sh
./gradlew :lwjgl3:visualTest
python3 tools/update_gallery.py
```

On headless Linux, prefix the Gradle command with `xvfb-run -a`. Check `build/reports/visual/index.html` for a successful run, then visually inspect each copied image. The copy script checks capture presence; it does not establish freshness or visual correctness. Run the suite on the current checkout before copying.

| Gallery file | Deterministic source scenario |
|---|---|
| `splash.png` | `gallery-splash` |
| `top-view.png` | `gallery-top` |
| `player-view-start.png` | `gallery-player` |
| `settings.png` | `gallery-settings` |
| `placement.png` | `gallery-placement` |
| `result.png` | `gallery-result` |
| `replay-close.png` | `replay-close` |
| `replay-overview.png` | `replay-overview` |
| `replay-diagonal.png` | `replay-diagonal` |
| `replay-hold.png` | `replay-hold` |

Visual PRs must review the gallery and update affected images and captions in the same PR. Remove obsolete images rather than adding historical before/after copies. Put comparisons in accessible PR attachments; Git history retains superseded assets. CI generates the complete visual report on every PR but does not update committed gallery images or compare screenshot baselines.

The table-concepts SVG remains a rules diagram, maintained against shared table geometry. It is not a gameplay screenshot. Android launcher masking and startup transitions require separate device or emulator inspection.

## Gameplay video

The gallery procedure produces a 30-second, normal-speed desktop recording (540 × 1200, 30 fps, H.264 video and AAC audio). It runs `PoolGame` with regular pointer gestures, production physics, rules, camera transitions, and the real computer planning worker. It is not an Android device capture. The procedure is repeatable; rack shuffling and worker timing mean recordings need not be identical.

With JDK 21, a working OpenGL context, Python 3, `ffmpeg`, and `ffprobe` on PATH:

```sh
python3 tools/record_gameplay_demo.py
```

The opt-in `:lwjgl3:gameplayDemo` task captures 900 frames with a fixed frame interval, confirms placement, and performs a human break through the normal input handler. It requires at least one human and one computer shot. Preferences are isolated under the report directory. Capture stays in test tooling; no production setup controls or wall-clock sleeps are added. On headless Linux, prefix the Python command with `xvfb-run -a`.

Replays play at 75% speed while their five-second decision hold remains in real time. The soundtrack uses the actual production `SoundPlayer` playback commands and bundled PCM samples, preserving event timing, gain, overlapping voices, and stop times. It does not record microphones or unrelated system audio. There is no music or narration. Cue/ball/cushion sounds are CC0; “Pool ball falling into pocket” by freesman, Directory.Audio, is CC BY 3.0, edited with downmixing, resampling, trimming, and fades. See [full audio credits and source links](../assets/audio/README.md); attribution is also embedded in the MP4 metadata.

Outputs are `build/reports/gameplay-demo/gameplay.mp4`, `audio-events.json`, and `verification.json`. The script rejects missing meaningful audio, clipping, unexpected codecs/frame count, duration other than 30 seconds, or size above 10 MB. It copies the five-second poster to `docs/images/gameplay-preview.png`. The ten-image screenshot refresh above does not overwrite that poster. `--reuse-capture` remuxes an already fresh capture from the current checkout without replaying gameplay.

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

`ReplayExportDeviceTest` exercises a deterministic legal pot through production replay coordination, verifies Settings suspension and frozen live physics, checks the five-second Skip countdown, taps Share during the final hold, dismisses the Android chooser, and verifies automatic return. A separate export test covers cancellation followed by a new export, video/audio tracks, the cache URI, and full playback by a separate test-APK recipient with temporary read permission. The diagonal export-depth test also checks the production framebuffer precision and compares four oblique views against a 16-bit reference to catch depth fighting. Fixture setup and the recipient Activity exist only in `androidTest`.

The test saves `files/replay-device-test.mp4` and `files/replay-real-shot.mp4` inside the debug app. With multiple devices, target the intended serial explicitly when retrieving evidence:

```sh
adb -s SERIAL exec-out run-as io.github.ffeltrinelli.pool cat files/replay-device-test.mp4 > build/reports/replay/device-export.mp4
python3 tools/verify_replay_video.py build/reports/replay/device-export.mp4 --replay-seconds 3.3466667
```

The verifier requires FFmpeg/ffprobe. It checks H.264/AAC, portrait dimensions and frame rate, the 60-frame static splash followed by replay, silent intro, meaningful unclipped replay audio, attribution metadata, and the expected duration without the five-second decision hold. Share-sheet screenshots may contain private Android suggestions; keep them in local build reports rather than the committed gallery.
