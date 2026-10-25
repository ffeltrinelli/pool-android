# Opponent planning review — 25 October 2026

The five-second request deadline includes setup, candidate generation, all placements, seeds and optimization. The attempt cap is now 256 instead of 64. All difficulties use the same search; execution-error profiles and production 120 Hz Bullet settings are unchanged.

## Findings and changes

The original fallback pointed at the nearest legal target without checking the intervening path. Two deterministic blocked layouts reproduced `WRONG_FIRST` in both prediction and independent native execution on the OnePlus. Group assignment and live snapshot construction already pass the current player's group correctly; these observations do not establish the exact cause of the user's historical shots, whose initial layouts were not captured.

Search now rejects known wrong first contacts before simulation, uses obstruction-aware fallbacks, and constructs one-cushion banks, kick pots and kick contacts when no intended direct pot succeeds. A completed legal intended direct pot suppresses rebound search. Otherwise the direct phase yields at half the attempt cap or 2.5 seconds, leaving the same request's remaining time to rebounds. Completed legal outcomes survive interruptions. A geometrically valid fallback is explicitly unevaluated and can still foul.

Candidate kinds and intended targets persist through refinement. Native evaluation records first contacts, object-ball cushions before pocketing, and cue cushions before first contact, so a direct pot cannot masquerade as a successful bank. Wins and additional legal pots remain more valuable than incidental proximity or power differences. An expanded search exposed a fragile lower-power variation in the existing open-table Medium device regression. A successful starting pot is now retained against refinements with the same intended target, legal pot count and rack outcome; difficulty errors and that regression's assertions remain unchanged.

Code responsibilities separate mirror geometry, rebound construction, optimizer integration, request bookkeeping and silent simulation. Exact trials are cached per request. Geometric obstacle lists and rules targets are reused. Audio-impact aggregation is absent from silent worlds. Physics contact collection skips cloth/support manifolds that cannot generate rule contacts or impact audio, reuses contact buffers and a vector, and allocates event collections only when needed. Rule facts and native trajectories are covered by audible/silent parity tests.

Restoration profiling found native ball reconstruction accounts for less than one percent of representative timed requests on the phone. Body pooling was therefore not introduced: its contact-cache, activation and ownership risks were not justified by that small share. Simulation and native collision/settling work remain the dominant cost. Material, solver, settling and simulation-duration tuning are unchanged.

## Measurement method

Baseline: Git revision `3edd07e4f7210ee73946ef110ec937fa7d5d3881`, built in an isolated temporary directory with benchmark-only tests added. Both revisions use identical fixture coordinates and random seed 17. Device: connected OnePlus 8T, model KB2003, Android API 34. Desktop: local Apple Silicon Mac, JDK 21. Wall-clock samples are observations, not performance guarantees or hard-coded test expectations.

Each of six layouts runs a fixed 32-attempt search and the production deadline/cap search twice with one planner instance: a cold world followed by a warm reuse. "Cold" is the first request on that world, not a fresh process, filesystem cache or thermally controlled device. Each selected device command is replayed in a separate native world at exact Hard accuracy and resolved by shared rules. Layout names describe their purpose, not a requirement that the selected shot have that kind; e.g. a bank fixture can select a better kick or contact.

A separate throughput test repeats identical commands 16 times in four rounds on sparse and full-rack snapshots. Compare the median simulation time of the last three rounds, and check matching step counts. This isolates efficiency from different search choices and the larger cap. Restoration is measured separately. Timing and native contact differences can change how many candidates complete by the deadline. The fixtures sample specific situations, not match win rates or an exhaustive quality benchmark.

## Results

[Search samples](benchmarks/ai-planning-2026-10-25.csv) retain both cold and warm runs on both machines. [Identical-rollout samples](benchmarks/ai-physics-2026-10-25.csv) retain restoration/simulation times and step counts. Detailed request-stage diagnostics remain in local `build/reports/planning/*-details.txt` and desktop planner CSV files.

Warm OnePlus production searches:

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

## Reproduction

```sh
./gradlew :core:planningBenchmark -PplanningReport=after
```

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

The refreshed 30-second gameplay recording lives at `build/reports/gameplay-demo/gameplay.mp4`; its poster is committed. Static gallery images still represent the unchanged rendering and controls. Upload the recording as an accessible attachment when publishing a PR; the earlier README video attachment is explicitly historical.
