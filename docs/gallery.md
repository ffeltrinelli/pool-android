# Current screenshot gallery

The README embeds the player-view start and links to the [gallery folder](images/). Gallery captures use the production desktop renderer at 1080 × 2400; they are not Android device captures. The start fixture confirms break placement and aims the resting cue toward the fresh rack before any shot.

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
