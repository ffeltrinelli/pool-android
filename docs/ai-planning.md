# Opponent planning review — 25 October 2026

The five-second request deadline includes setup, candidate generation, all placements, seeds and optimization. The attempt cap is now 256 instead of 64. All difficulties use the same search; execution-error profiles and production 120 Hz Bullet settings are unchanged.

## Findings and changes

The original fallback pointed at the nearest legal target without checking the intervening path. Two deterministic blocked layouts reproduced `WRONG_FIRST` in both prediction and independent native execution on the OnePlus. Group assignment and live snapshot construction already pass the current player's group correctly; these observations do not establish the exact cause of the user's historical shots, whose initial layouts were not captured.

Search now rejects known wrong first contacts before simulation, uses obstruction-aware fallbacks, and constructs one-cushion banks, kick pots and kick contacts when no intended direct pot succeeds. A completed legal intended direct pot suppresses rebound search. Otherwise the direct phase yields at half the attempt cap or 2.5 seconds, leaving the same request's remaining time to rebounds. Completed legal outcomes survive interruptions. A geometrically valid fallback is explicitly unevaluated and can still foul.

Candidate kinds and intended targets persist through refinement. Native evaluation records first contacts, object-ball cushions before pocketing, and cue cushions before first contact, so a direct pot cannot masquerade as a successful bank. Wins and additional legal pots remain more valuable than incidental proximity or power differences. An expanded search exposed a fragile lower-power variation in the existing open-table Medium device regression. A successful starting pot is now retained against refinements that also pocket their intended target with the same legal pot count and rack outcome; difficulty errors and that regression's assertions remain unchanged.

Code responsibilities separate mirror geometry, rebound construction, optimizer integration, request bookkeeping and silent simulation. Exact trials are cached per request. Geometric obstacle lists and rules targets are reused. Audio-impact aggregation is absent from silent worlds. Physics contact collection skips cloth/support manifolds that cannot generate rule contacts or impact audio, reuses contact buffers and a vector, and allocates event collections only when needed. Rule facts and native trajectories are covered by audible/silent parity tests.

Restoration profiling found native ball reconstruction accounts for less than one percent of representative timed requests on the phone. Body pooling was therefore not introduced: its contact-cache, activation and ownership risks were not justified by that small share. Simulation and native collision/settling work remain the dominant cost. Material, solver, settling and simulation-duration tuning are unchanged.

## Ball-in-hand follow-up

A subsequent live OnePlus shot used all five seconds: 21 attempts in the direct phase and 20 in rebound search, with 38 completed simulations. It ultimately selected a 1.00 N·s contact on ball 2, predicting an incidental pot of ball 1. Read-only debugger inspection confirmed the actual result was no pot and no foul, on a computer solids turn. Replays were disabled and no original request remained reachable; the committed fixture is explicitly the settled **post-miss** table, not a reconstruction of the original shot.

Placement selection previously shortlisted merely valid cue positions before checking the actual pot lanes. It could also evaluate up to 32 contact alternatives before refining placement pots, consuming the direct phase. Clear placement routes now use the same ghost-ball validation as ordinary direct shots, retain their exact target seed, and cover different legal targets first. Multiple cue distances allow tighter placements near cushions. Clear routes precede unchecked aligned positions and grid fallbacks, preserving native coverage around jaws where the predictor is conservative. With clear direct seeds, only two contact fallbacks precede refinement; without them, one contact per placement still gets a chance.

One immutable `WorkerShotPlanningService.lastRequest` is retained for debugger inspection even with replays off, and logs include the chosen cue position. Shared test geometry lives in `core/src/test/resources/opponent`; Android includes it only in test assets. The post-miss regression checks an intended direct pot within the production deadline and replays it in an independent native world.

## Captured direct rail pot

A later OnePlus report supplied an exact retained pre-shot request (log timestamp 18:42:35, seed 4), now shared as `opponent/missed-direct-request.json`. The computer had solids and a fixed cue position. Its old generator returned no direct pots and selected a kick contact on ball 1, predicting a pot; live execution made no pot and no foul. A direct shot on ball 4 along the left cushion was independently verified in Bullet at several powers.

Aiming only at the pocket center pointed that object-ball route into the cushion. Direct geometry now also constructs table-axis entries when the line passes the mouth and intersects the pocket opening. It checks the object path against the actual ray/mouth intersection rather than radial distance to the mouth center, which incorrectly rejects off-center entries. Centered seeds remain first; if none succeeds, supplemental direct entries are evaluated before refinement and rebounds. They share the existing phase deadline and attempt cap. Geometry is cached per positioned snapshot.

The existing Medium fixture exposed a refinement that switched intended targets merely for proximity/power. Successful starting pots now survive these same-outcome refinements across targets as well. Wins, legal pot count and foul/loss avoidance still take precedence; execution errors are unchanged. Core coverage includes the exact request with independent execution, mirrored rail routes, blocked object lanes, and actual mouth crossings. The original Medium and winning-eight assertions remain unchanged.

## Captured ball-2 jaw route

The next retained request (OnePlus log 19:07:31, seed 11) had a direct pot on ball 2 into the nearby corner. The old generator returned no direct candidates because its planar object ray met the cushion shortly before the pocket mouth. Independent native trials potted ball 2 at 0.18, 0.25, 0.35 and 0.50 N·s; the 0.25 N·s regression also verifies no object-ball cushion contact. The live planner instead selected a kick pot on ball 1, predicting a pot; the actual result was no pocket and no foul. The exact pre-shot fixture is `opponent/ball-two-direct-request.json`.

A route whose predicted cushion contact lies within one ball radius of an intended jaw endpoint, and no more than one diameter short of its mouth, is now a supplemental direct seed. A separate ball-only query still rejects obstructed object lanes, including a ball hidden behind the earlier predicted cushion contact. Distant cushion routes remain rejected. Clear centered seeds retain priority; supplemental routes share the direct phase and request budgets. Bullet decides the result, and a native cue rebound before first contact cannot count as a direct shot. Direct success does not require zero object-rail contacts: an along-rail shot can graze the cushion, and a pocket can rattle within its throat.

Tests cover the captured request, independent direct execution, a ball behind the conservative jaw stop, a distant cushion rejection and the earlier direct-rail/Medium/placement cases. The shared fixture remains in test assets only. Neither physics fidelity, difficulty errors nor the 5,000 ms deadline changed. Earlier benchmark rows describe their recorded revisions and are not new speed measurements for this change.

## Captured green-ball pocket entry

The next OnePlus report (log 21:39:05, seed 2) selected a firm `CONTACT` at the center of ball 6, not an explicit bank or kick candidate. The generator supplied no direct pots, and after four direct-contact trials and 32 rebound attempts it kept that contact's predicted incidental pot. The live result made no pocket and no foul. The exact request is `opponent/green-ball-direct-request.json`.

The object-ball ray toward the corner pocket center met the side cushion well before its mouth. A different route through the usable mouth was clear. Independent desktop trials at an interior mouth entry potted ball 6 at 0.25 N·s without a target-ball cushion contact. Direct candidates now sample the mouth center and two interior lateral positions, in addition to pocket-center and table-axis routes. The lateral span subtracts ball radius; each added ray must cross the mouth, intersect the pocket opening, and pass the existing cue/object obstruction checks. Near-jaw allowances remain separately bounded.

These entries remain supplemental: successful clear centered seeds keep priority, and the existing shortlists, phase deadline, 256-attempt cap and 5,000 ms request deadline are unchanged. The captured regression independently executes the selected pot and permits only pocket-throat target contacts, rejecting a cushion bounce before the ball reaches the mouth. Mirrored-corner tests cover the same route and reject an added blocking ball. Earlier rail, ball-2, placement, Medium and legality regressions remain unchanged.

## Measurement method

Baseline: Git revision `3edd07e4f7210ee73946ef110ec937fa7d5d3881`, built in an isolated temporary directory with benchmark-only tests added. Both revisions use identical fixture coordinates and random seed 17. Device: connected OnePlus 8T, model KB2003, Android API 34. Desktop: local Apple Silicon Mac, JDK 21. Wall-clock samples are observations, not performance guarantees or hard-coded test expectations.

Each of six layouts runs a fixed 32-attempt search and the production deadline/cap search twice with one planner instance: a cold world followed by a warm reuse. "Cold" is the first request on that world, not a fresh process, filesystem cache or thermally controlled device. Each selected device command is replayed in a separate native world at exact Hard accuracy and resolved by shared rules. Layout names describe their purpose, not a requirement that the selected shot have that kind; e.g. a bank fixture can select a better kick or contact.

A separate throughput test repeats identical commands 16 times in four rounds on sparse and full-rack snapshots. Compare the median simulation time of the last three rounds, and check matching step counts. This isolates efficiency from different search choices and the larger cap. Restoration is measured separately. Timing and native contact differences can change how many candidates complete by the deadline. The fixtures sample specific situations, not match win rates or an exhaustive quality benchmark.

## Results

[Search samples](benchmarks/ai-planning-2026-10-25.csv) retain both cold and warm runs on both machines. [Identical-rollout samples](benchmarks/ai-physics-2026-10-25.csv) retain restoration/simulation times and step counts. Detailed request-stage diagnostics remain in local `build/reports/planning/*-details.txt` and desktop planner CSV files.

Warm OnePlus production searches before the ball-in-hand follow-up:

| Layout | Before: evaluations / ms | After: evaluations / ms | Native outcome before → after |
|---|---:|---:|---|
| Direct | 64 / 1,683 | 206 / 5,000 | Legal pot → legal pot |
| Open table | 64 / 3,522 | 95 / 5,000 | One legal pot → two legal pots |
| Blocked | 64 / 2,995 | 115 / 5,000 | Wrong first contact → legal kick pot |
| Bank layout | 64 / 3,479 | 95 / 5,000 | Legal pot → legal pot |
| Kick layout | 64 / 3,208 | 106 / 5,000 | Wrong first contact → legal kick pot |
| Ball in hand | 64 / 2,276 | 144 / 5,000 | Legal pot → legal pot |

Every final cold/warm timed phone request reported 5,000 ms, with 93–206 completed evaluations, no fallback and no predicted or independently replayed foul. The clock is checked between native steps and geometry operations; scheduling/GC/native-call overhead can overrun the boundary slightly (one desktop gameplay capture reported 5,013 ms), rather than extending the configured budget.

At the same 32-attempt limit, warm OnePlus direct/open/blocked/bank/kick layouts took 828/1,161/1,144/1,235/1,213 ms versus 915/1,854/1,523/1,703/1,678 ms before. Completed counts differ because illegal directions are rejected before simulation and exact trials are cached. Placement now uses 31 completed evaluations instead of leaving the old optimizer's small per-position budget unused at eight; its fixed-budget request consequently takes longer, 784 ms versus 243 ms, while evaluating more alternatives.

Identical-rollout warm medians (16 rollouts per round):

| Machine / layout | Before simulation ms | Final after simulation ms |
|---|---:|---:|
| Desktop / sparse | 173.4 | 156.3 |
| Desktop / rack | 1,505.3 | 1,380.7 |
| OnePlus / sparse | 862.1 | 942.7 |
| OnePlus / rack | 3,809.1 | 4,026.6 |

The desktop observations were about 10% and 8% faster. An earlier OnePlus sample was about 16% and 6% faster, but the final phone repeat was about 9% and 6% slower than baseline. These runs were not thermally/governor controlled, so they do **not** establish a consistent per-step speed improvement on the phone. The repeatable practical result is more completed useful evaluations within the existing deadline, better blocked-layout legality, and less work wasted on known illegal directions. Physics step counts for the identical commands match across revisions on each machine.

The focused desktop placement follow-up completed 250 evaluations in 1,820 ms on the warm timed request, compared with 237 in 2,368 ms in the initial implementation. Its fixed 32-attempt run completed 31 simulations in 131 ms, selecting an intended direct pot. These additional rows are labeled `placement-followup` in the search CSV; the earlier before/after rows remain as historical measurements.

On the OnePlus, the focused warm placement request completed 219 evaluations in 5,000 ms versus 144 in the initial implementation; the fixed run completed 31 simulations in 539 ms versus 784 ms. All four cold/warm requests selected ball 9 and independently potted it without a foul or fallback. These are single-run observations under uncontrolled device conditions. The captured post-miss regression also selected and physically potted ball 2 directly, with 41 completed evaluations in 5,000 ms and no rebound attempts. The previous installed build passed that same post-miss fixture with 32 evaluations, so it does not reproduce the original incident.

## Reproduction

```sh
./gradlew :core:planningBenchmark -PplanningReport=after
```

Use `-PplanningLayout=placement -PplanningReport=placement-followup` for a focused placement rerun. On Android, add `-e poolBenchmarkLayout placement` to the instrumentation command.

This opt-in task writes `build/reports/planning/after.csv` and `after-physics.csv`; ordinary core tests skip benchmark work. The planner CSV includes stage timings, attempts, completed evaluations, rejections, cache hits, physics steps and outcome diagnostics.

Build/install both debug APKs, explicitly targeting the intended device, then run:

```sh
adb -s SERIAL shell am instrument -w -r \
  -e class io.github.ffeltrinelli.pool.android.PlanningBenchmarkDeviceTest \
  -e poolBenchmark true \
  io.github.ffeltrinelli.pool.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL exec-out run-as io.github.ffeltrinelli.pool cat files/planning-benchmark.csv
adb -s SERIAL exec-out run-as io.github.ffeltrinelli.pool cat files/planning-benchmark-physics.csv
adb -s SERIAL exec-out run-as io.github.ffeltrinelli.pool cat files/planning-benchmark-details.txt
```

The benchmark owns its worlds on the instrumentation thread and creates no production fixture controls. Regular device tests skip its two measurement methods. `OpponentLegalityDeviceTest` separately asserts production-budget legal kick contacts around a solid or premature eight. Existing replay, Settings, sharing, export and residual-settling tests remain part of device acceptance. The real-shot replay fixture enables replays in memory rather than assuming the player has enabled the saved setting; it never writes that preference.

The refreshed 30-second gameplay recording lives at `build/reports/gameplay-demo/gameplay.mp4`; its poster is committed. Static gallery images still represent the unchanged rendering and controls. The recording is attached to [PR #18](https://github.com/ffeltrinelli/pool-android/pull/18) and linked from README.
