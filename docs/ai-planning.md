# Opponent planning

The planner uses CMA-ES (Covariance Matrix Adaptation Evolution Strategy), an optimizer that samples batches of shot variations and uses better outcomes to choose which directions and powers to explore next. Bullet, the game’s physics engine, simulates each candidate.

The search balances useful alternatives against a five-second turn budget. Direct pots come first; if none succeeds, single-cushion banks (object ball rebounds) and kicks (cue ball rebounds before contact) receive the remaining budget. Geometry proposes routes, while native simulation and shared rules decide their outcome. A fallback may be unevaluated and cannot guarantee a legal shot.

Captured failures exposed two recurring weaknesses: a conservative geometric model can reject valid entries near rails or jaws (the angled cushion ends beside a pocket opening), and an incidental lower-power pot can displace a more reliable intended pot. The planner therefore tests several usable mouth entries, bounds near-jaw exceptions, checks independently whether other balls block the route, and preserves intended targets through refinement. Additional legal pots and better rack outcomes still take precedence.

Ball in hand allows cue-ball placement after a foul; its candidates prioritize clear pot lanes and different targets before duplicate routes. Contact alternatives must not consume the entire direct phase. Restored native contacts remain approximate; exact Hard execution can still differ from prediction. Retaining a successful seed (an initial candidate shot) against an equally scoring refinement is a reliability choice, not proof of optimal play.

## Captured failures

Fixtures—saved test layouts—in `core/src/test/resources/opponent` retain immutable geometry for independent native execution. They are bundled only in Android test assets. Inspect the worker’s retained `lastRequest` before resetting a reported shot; replay preferences do not control its availability.

| Fixture | Evidence and lesson |
|---|---|
| `missed-direct-request.json` | Exact pre-shot capture: ball 4 had a direct rail pot omitted by pocket-center-only geometry. |
| `ball-two-direct-request.json` | Exact capture: planar jaw rejection hid a ball-2 pot that native trials accepted. |
| `green-ball-direct-request.json` | Exact capture: an interior pocket-mouth entry potted ball 6 while the center line met a cushion. |
| Post-miss placement layout | Settled table only; both old and new planners pass it, so it does not reproduce the original miss. |

Device reruns reproduced missing candidates or indirect choices, but some independently potted when the live shot missed. These fixtures establish specific regressions, not universal accuracy. Detailed build-specific observations are in [device validation](device-validation.md).

Recreating Bullet’s ball bodies from saved poses measured below one percent of representative phone requests. Reusing those bodies instead (body pooling) was rejected because stale collision history and resource-lifetime risks outweighed that small potential saving. Simulation remains the dominant cost.

## Measurement method

Baseline: Git revision `3edd07e4f7210ee73946ef110ec937fa7d5d3881`, built in an isolated temporary directory with benchmark-only tests added. Both revisions use identical fixture coordinates and random seed 17. Device: connected OnePlus 8T, model KB2003, Android API 34. Desktop: local Apple Silicon Mac, JDK 21. Wall-clock samples are observations, not performance guarantees or hard-coded test expectations.

Each of six layouts runs a fixed 32-attempt search and the production search with its time and attempt limits twice with one planner instance: a cold world followed by a warm reuse. "Cold" is the first request on that world, not a fresh process, filesystem cache or thermally controlled device. Each selected device command is replayed in a separate native world at exact Hard accuracy and resolved by shared rules. Layout names describe their purpose, not a requirement that the selected shot have that kind; e.g. a bank fixture can select a better kick or contact.

A separate throughput test measures simulation work per unit time by repeating identical commands 16 times in four rounds on sparse and full-rack snapshots. Compare the median simulation time of the last three rounds, and check matching step counts. This isolates efficiency from different search choices and the larger cap. Restoration is measured separately. Timing and native contact differences can change how many candidates complete by the deadline. The fixtures sample specific situations, not match win rates or an exhaustive quality benchmark.

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

Every final cold/warm timed phone request reported 5,000 ms, with 93–206 completed evaluations, no fallback and no predicted or independently replayed foul. The clock is checked between native steps and geometry operations; thread scheduling, garbage collection, and calls into Bullet can overrun the boundary slightly (one desktop gameplay capture reported 5,013 ms), rather than extending the configured budget.

At the same 32-attempt limit, warm OnePlus direct/open/blocked/bank/kick layouts took 828/1,161/1,144/1,235/1,213 ms versus 915/1,854/1,523/1,703/1,678 ms before. Completed counts differ because illegal directions are rejected before simulation and exact trials are cached. Placement now uses 31 completed evaluations instead of leaving the old optimizer's small per-position budget unused at eight; its fixed-budget request consequently takes longer, 784 ms versus 243 ms, while evaluating more alternatives.

Identical-rollout warm medians (16 simulated shots per round):

| Machine / layout | Before simulation ms | Final after simulation ms |
|---|---:|---:|
| Desktop / sparse | 173.4 | 156.3 |
| Desktop / rack | 1,505.3 | 1,380.7 |
| OnePlus / sparse | 862.1 | 942.7 |
| OnePlus / rack | 3,809.1 | 4,026.6 |

The desktop observations were about 10% and 8% faster. An earlier OnePlus sample was about 16% and 6% faster, but the final phone repeat was about 9% and 6% slower than baseline. Phone temperature and CPU clock policies were not controlled during these runs, so they do **not** establish a consistent per-step speed improvement on the phone. The repeatable practical result is more completed useful evaluations within the existing deadline, better blocked-layout legality, and less work wasted on known illegal directions. Physics step counts for the identical commands match across revisions on each machine.

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
