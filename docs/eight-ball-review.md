# Eight-ball implementation and documentation review

The current game implements the simplified WPA variant described in [the README](../README.md#rules-of-this-game). This review supersedes the ball-count competition descriptions in earlier validation records.

## Implementation boundaries

- `EightBallRules` resolves immutable rules context and collected shot facts. Live `GameSession` and silent Bullet simulations use the same decisions; no second AI-only interpretation of fouls or winning exists.
- Session owns groups, pre-shot eligibility, break/placement phases, alternating requested breakers and final outcomes. Physics reports contact episodes, rail hits, head-string crossing, physical pockets and separate diagnostic off-table removals.
- Human placement has an independent preview and confirmation. Shared geometry validation rejects balls, bounds, jaws, cushions and pocket openings. The computer evaluates bounded placement/shot pairs under its existing budget.
- The eight is spotted by a focused physics operation that preserves every other body's transform. The obsolete automatic starting-half replacement search was removed.
- Ball-in-hand positions prioritize pot-aligned locations before break-spot grid fallbacks, and every shortlisted position gets an evaluated seed. A native Hard regression wins from five eight-ball-only-target layouts, including opponent blockers.
- The HUD omits the Pool title and redundant open-table line, retaining Unassigned in the ownership rows. It uses user-accepted larger text inside a reserved strip above the scene for Player-view readability. A shared layout defines the game viewport and touch offset; messages stay inside a gold notification area, separate from player labels. Placement errors share that area and confirmation occupies a temporary top row. Ordinary row spacing is compact; long messages reserve their wrapped height. Balls use matte materials and softer diffuse illumination below clipping to reduce number-obscuring glare.
- The computer worker retains its cancellation/resource boundary. Selected placement is applied once before animation; suspension retains the selected shot without duplicating placement or contact.
- Physics material/solver tuning, human gestures, camera tuning, execution-error levels and audio assets are unchanged. Rack positions/packing intentionally follow the corrected rack.

## Documentation inventory

| Material reviewed | Result |
|---|---|
| README and table of contents | Replaced obsolete scoring, fixed-break and automatic-replacement instructions; added exact rule deviations, placement controls and table/rack SVG. Updated AI diagram, objectives, target selection and placement budgets. |
| `AGENTS.md` | Clean-code instructions and accessible screenshot requirement remain applicable; no change required. |
| `docs/device-validation.md` and validation JSON/CSV | Preserved chronological evidence, added a historical-context notice and a new build-specific record. Historical difficulty calibration is not represented as an eight-ball win-rate measurement. |
| `docs/computer-only-cleanup.md` | Marked as a historical PR review and linked current guidance. |
| Audio and third-party licence READMEs/notices | Verified that bundled sources and required credits remain unchanged. No licences or attribution removed. |
| Python device tools and launcher descriptions | Added placement confirmation to scripted fresh racks; changed the soak counter to requested racks because breakers alternate. Removed stale “Vs computer ready” wording. |
| Kotlin ownership/API comments and rendered fixture descriptions | Updated rules/AI descriptions, removed obsolete score/replacement assumptions, retained historical filenames where their scenario remains meaningful, replaced draw fixtures. |
| Documentation links and images | Checked repository Markdown local targets and README section anchors. Rendered the SVG for readability and checked measurements against `TableGeometry`: 2.24 m length, strings/spots at ±0.56 m, cue suggestion at +0.58 m, correct third-row eight and rear corners. |

## Validation

Local delivery command:

```sh
ALSOFT_DRIVERS=null ./gradlew :core:test :lwjgl3:visualTest :lwjgl3:audioTest :android:lintDebug :android:assembleDebug --console=plain --no-parallel
```

JDK 21. **161 tests passed; one opt-in calibration test skipped; zero failures/errors.** All **135** rendered scenarios passed at 1080 × 2400. Four WAVs and production audio lifecycle passed the OpenAL null-backend smoke suite. Android lint and debug APK build passed; pre-existing lint warnings remain.

Rules coverage includes groups, mixed pots, first contact/cushion order, break counting, scratches, eight-ball eligibility, final scratches, duplicate events, placement and requested-break alternation. Native coverage includes contact facts across frame partitions, silent off-table removal, non-disruptive spotting, legal computer targets and placement search budgets. Input/coordination tests cover invalid confirmation, placement input isolation, selected-placement delivery once, suspension, stale worker results and failure fallback with a cue position.

OnePlus results and explicitly outstanding acceptance checks are recorded in [device validation](device-validation.md#simplified-eight-ball-2026-10-24).

Opponent legality follow-up: added native contact-alternative and settled-cue preservation regressions, kept the same evaluation/deadline limits and Hard accuracy, and documented the shared contact seeds and predicted-foul diagnostics in README. The user's reported exact shot was not replayable from the available post-shot state. Final local suite: 166 passed tests, one skipped calibration, 135 rendered scenarios; audio/lint/debug APK passed.
