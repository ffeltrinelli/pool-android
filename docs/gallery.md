# Current screenshots and gameplay video

The README embeds the player-view start alongside the current gameplay-video preview and links to the [gallery folder](images/). Gallery captures use the production desktop renderer at 1080 × 2400; they are not Android device captures. Top view depicts a computer turn; placement uses the same overhead framing. Settings contains Aided aim, Difficulty, Restart and Exit game. The start fixture confirms break placement and aims the resting cue toward the fresh rack before any shot.

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

Visual PRs must review the gallery and update affected images and captions in the same PR. Remove obsolete images rather than adding historical before/after copies. Put comparisons in accessible PR attachments; Git history retains superseded assets. CI generates the complete visual report on every PR but does not update committed gallery images or compare screenshot baselines.

The table-concepts SVG remains a rules diagram, maintained against shared table geometry. It is not a gameplay screenshot. Android launcher masking and startup transitions require separate device or emulator inspection.

## Gameplay video

The README links a 30-second, normal-speed desktop recording (540 × 1200, 30 fps, H.264 video and AAC audio). It runs `PoolGame` with regular pointer gestures, production physics, rules, camera transitions, and the real computer planning worker. It is not an Android device capture. The procedure is repeatable; rack shuffling and worker timing mean recordings need not be identical.

With JDK 21, a working OpenGL context, Python 3, `ffmpeg`, and `ffprobe` on PATH:

```sh
python3 tools/record_gameplay_demo.py
```

The opt-in `:lwjgl3:gameplayDemo` task captures 900 frames with a fixed frame interval, confirms placement, and performs a human break through the normal input handler. It requires at least one human and one computer shot. Preferences are isolated under the report directory. Capture stays in test tooling; no production setup controls or wall-clock sleeps are added. On headless Linux, prefix the Python command with `xvfb-run -a`.

The soundtrack uses the actual production `SoundPlayer` playback commands and bundled PCM samples, preserving event timing, gain, overlapping voices, and stop times. It does not record microphones or unrelated system audio. There is no music or narration. Cue/ball/cushion sounds are CC0; “Pool ball falling into pocket” by freesman, Directory.Audio, is CC BY 3.0, edited with downmixing, resampling, trimming, and fades. See [full audio credits and source links](../assets/audio/README.md); attribution is also embedded in the MP4 metadata.

Outputs are `build/reports/gameplay-demo/gameplay.mp4`, `audio-events.json`, and `verification.json`. The script rejects missing meaningful audio, clipping, unexpected codecs/frame count, duration other than 30 seconds, or size above 10 MB. It copies the five-second poster to `docs/images/gameplay-preview.png`. The six-image screenshot refresh above does not overwrite that poster. `--reuse-capture` remuxes an already fresh capture from the current checkout without replaying gameplay.

Watch the entire clip with sound: check natural pacing, readable HUD, cue and camera motion, computer turns, and synchronized impacts. Upload the verified clip to the current PR, then update the README preview link to the returned attachment URL:

```sh
gh pr edit NUMBER --attach build/reports/gameplay-demo/gameplay.mp4
```

GitHub renders the attachment link as an inline video player beside the screenshot; its audio starts muted until the viewer unmutes it. The linked poster remains a fallback for other Markdown viewers.

Keep only the poster in Git; the MP4 and capture intermediates stay in ignored build reports. Commit the README link and poster together. Keep before/after media in accessible PR attachments. Review the video whenever a PR changes depicted visuals, gameplay, or audio; re-record when it becomes outdated, not for unrelated changes. CI tests the audio mixer and retains existing screenshot/audio reports, but does not record or compare videos.
