# Computer-only play and clean-code review

Review date: 2026-10-24. Applied the installed `clean-code` skill's mini rules and repository `AGENTS.md`. Solo removal is the intended gameplay change. Physics, gesture, camera, sound, search and accuracy tuning remain unchanged.

## Review coverage and decisions

| Area | Improvement or decision |
| --- | --- |
| Session model | Removed Solo, mode dispatch and the rules-selection interface. One session owns deferred scoring, alternating turns and match results. States are Ready, Moving and Finished. Named shot completion and point-award commands replace nested event-handler logic. |
| Application/input/lifecycle | Extracted pointer/UI routing into `GameInput`. `PoolGame` coordinates lifecycle and frames. Settings construction reads preferences without issuing game commands; the application explicitly applies its initial view. |
| Settings/HUD | Removed Play mode. Extracted shared `HudLayout` from drawing and the libGDX persistence adapter from control behavior. Legacy `play-mode` values are never read or written. Other preferences and modal suspension remain independent. |
| Rendering | Immutable `TablePresentation` and `HudPresentation` replace mutable public renderer flags. Scene, aided aim, depth-cleared cue overlay and HUD order remain unchanged. Texture baking, table models, materials and resource disposal were retained. |
| Opponent | Worker ownership is separate from turn presentation. A pending request pairs its future with its fallback. Explicit Idle/Planning/Selected/Stroking states eliminate ambiguous combinations of booleans and nullable fields. Raw selected shots survive suspension without applying accuracy variation twice. |
| Search/simulation | `BulletShotPlanner` orchestrates CMA-ES; per-request `ShotSearch` owns budgets and best-result bookkeeping; worker-owned `ShotSimulation` owns the silent Bullet world and rollout outcomes. Seed order, optimizer parameters, objective, five-second deadline, 64 attempts and 30-second rollouts are retained. Candidate ranking cost is named separately from opponent difficulty. |
| Physics | Split stepping into transform history, simulation, contacts, observation/capture and settling. Centralized ball disposal; expose the collection through a read-only `List`. Retained collision geometry, support seams, CCD, contact order, captures, rest tolerances and numerical tuning. |
| Audio | Named voice expiration and eviction phases. Retained gains, priorities, eight-voice budget, duplicate suppression, cue/pocket delivery, backend failure handling and resource ownership. |
| Gestures/views/geometry | Reviewed existing boundaries and retained validated behavior. Avoid duplicate magnitude calculation in point normalization. Kept shared gesture interpretation, separate view implementations and common visible/physical pocket geometry. |
| Tests/reports | Migrated Solo fixtures to ongoing fouls or match results. Added input-boundary tests, retained native/search/concurrency contracts, expanded dense touched test setup, and grouped rendered scenarios explicitly with reusable reset/setup. Fixtures remain outside production gameplay. |
| Launchers/build/CI/assets | Updated desktop title. Reviewed Android lifecycle/manifest, native packaging, pinned dependencies, Gradle tasks, CI triggers and asset credits; retained these rather than introducing platform/dependency changes. Historical device evidence and third-party source/licence files remain intact. |
| Device tooling | Updated both scripts for compact Settings coordinates; removed play-mode selection and obsolete persistence assertions from the opponent smoke check. |

## Verification

The original main baseline passed unit/native tests, 121 rendered scenarios, audio smoke, lint and debug APK build. Focused session/settings/rendered checks passed before the Solo-removal commit. Subsequent regression runs cover explicit turn states, worker cancellation/fallback, simulation reuse, physical tolerances, immutable frame inputs and application lifecycle.

Final automated results and device availability are recorded in [device validation](device-validation.md). Screenshots for the Settings change are in `docs/images/computer-only/`; generated HTML, PNG, XML, lint and audio reports remain under build output and are uploaded by existing PR CI. No image-comparison baseline or test-only production controls were introduced.
