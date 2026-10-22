# S7: path service under load

*Written with M1 T22 on 2026-10-23. Spec: [M1 §8.4](../milestones/M1-movement.md#84-s7-path-service-under-load-t22--confirms-adr-0015s-delivery);
card PATH-16.*

## Question and exit criterion

Does [ADR-0015](../adr/0015-pathfinding-approach.md)'s deterministic delivery keep the server
thread free under a burst of path requests, and how should `k`, the live delivery mode and the
admission window W be set?

Exit (spec §8.4): **the main thread's path work ≤ 2 ms at p99**, and **a 100-unit order's results
within 5 ticks at p95 with 200 requests queued**. The report sets `k`, the live delivery mode
(`STRICT` everywhere if its join stays ≤ 0.5 ms at p99 on the slow-host proxy) and W.

## Setup

- **Machine:** Apple M4 Pro, 12 cores; "Mac OS X 27.0.1" on aarch64 as the JVM reports it.
- **Software:** Temurin 21.0.12.1, heap 4 GB; NeoForge 21.1.264.
- **Tree:** 2026-10-23, M1 T23 (commit `0a83796`) with T22 uncommitted.
- **Command:** `./gradlew :mod:runSpike -Pspike=s7`: 34 configurations in about 2 minutes, ending
  with its `DONE` marker. The data: [S7-results.csv](data/S7-results.csv),
  [S7-env.json](data/S7-env.json).

## Method

`S7PathLoad` (`mod/src/gametest/.../spike/`) runs `PathLoad` (`mod/src/gametest/.../PathLoad.java`)
once per configuration, on the spike server's thread, with the mod's own pool
(`PooledJobExecutor`) or inline jobs:

1. **The terrain** is the proving ground's, 384², built from `ProvingGround`, the generator the map
   package is written from, so it is the imported map's terrain without a level import. The 576
   pages are built once and published into a fresh match for each configuration.
2. **The load.** 200 units stand on land at random columns (a fixed seed), each sent to a random
   land column more than 120 blocks away, all in one tick; the next tick 100 units at the west
   start are sent as one group to the east start (group places, so 100 more requests). Every
   order of a tick applies in that tick.
3. **The ticks are paced** 50 ms apart, as a server at 20 ticks a second runs them, so the workers
   get the time between ticks. A first run stepped them back to back, and the workers had only
   the main thread's own step time: 130–170 of the 300 results were joined before their job had
   finished, and the main thread did their searches. That run is not reported.
4. **Measured** from the group's order until every unit has its answer:
   - the main thread's path work each tick: delivery, joins, requests and admission
     (`PathService.Stats.lastWork`), p50, p99 and max;
   - its joins alone each tick, p99;
   - each group unit's latency in ticks from its order to its result, p50, p95 and max, and
     every unit's p95;
   - the results joined before their job had finished.
5. **The configurations,** for `STRICT` and `ADAPTIVE` each:
   - W = 200,000, 400,000, 800,000, 1,200,000, 1,600,000, 2,400,000 and 3,200,000, with 4 threads
     and `k` = 2;
   - 0 to 4 threads with W = 1,600,000 and `k` = 2;
   - `k` = 1 to 4 with 4 threads and W = 1,600,000;
   - **the slow-host proxy:** 1 thread while busy threads, twice as many as cores (24), load the
     whole host, at W = 800,000 and 1,600,000. `perf.fakeForeignLoad`, which the spec named, only
     replaces a measured number in the perf tests, so it cannot slow a host.

A request's cost in W is its expansion cap, 20,000. The 300 searches expand about 560,000
nodes in all, 1,870 each on average (HPA*, PATH-08), about 0.48 ms each on the main thread.

## Results

All times in ms; latencies in ticks. "Unfinished" counts results joined before their job had
finished.

| Configuration | main p50 | main p99 | main max | join p99 | group p50 | group p95 | all p95 | unfinished |
|---|---|---|---|---|---|---|---|---|
| `STRICT`, W 200,000 | 0.213 | 0.515 | 0.515 | 0.015 | 25 | 30 | 29 | 0 |
| `STRICT`, W 400,000 | 0.221 | 0.468 | 0.468 | 0.032 | 13 | 15 | 15 | 0 |
| `STRICT`, W 800,000 | 0.262 | 0.302 | 0.302 | 0.011 | 7 | 8 | 8 | 0 |
| `STRICT`, W 1,200,000 | 0.249 | 0.302 | 0.302 | 0.014 | 5 | 5 | 5 | 0 |
| **`STRICT`, W 1,600,000** | **0.251** | **0.383** | 0.383 | **0.017** | 4 | **4** | 4 | 0 |
| `STRICT`, W 2,400,000 | 0.173 | 0.379 | 0.379 | 0.020 | 3 | 3 | 3 | 0 |
| `STRICT`, W 3,200,000 | 0.279 | 0.413 | 0.413 | 0.026 | 2 | 2 | 3 | 0 |
| `STRICT`, 0 threads | 23.837 | 34.240 | 34.240 | 34.121 | 4 | 4 | 4 | 300 |
| `STRICT`, 1 thread | 0.178 | 0.256 | 0.256 | 0.023 | 4 | 4 | 4 | 0 |
| `STRICT`, 2 threads | 0.165 | 0.235 | 0.235 | 0.030 | 4 | 4 | 4 | 0 |
| `STRICT`, 3 threads | 0.233 | 0.267 | 0.267 | 0.026 | 4 | 4 | 4 | 0 |
| `STRICT`, `k` 1 | 0.265 | 5.705 | 5.705 | 5.539 | 3 | 3 | 3 | 33 |
| `STRICT`, `k` 3 | 0.147 | 0.294 | 0.294 | 0.018 | 5 | 5 | 5 | 0 |
| `STRICT`, `k` 4 | 0.155 | 0.285 | 0.285 | 0.017 | 6 | 6 | 6 | 0 |
| `STRICT`, slow host, W 800,000 | 0.162 | 0.364 | 0.364 | 0.009 | 7 | 8 | 8 | 0 |
| **`STRICT`, slow host, W 1,600,000** | 0.410 | **0.564** | 0.564 | **0.022** | 4 | **4** | 4 | 0 |
| `ADAPTIVE`, W 200,000 | 0.120 | 0.362 | 0.362 | 0.008 | 25 | 30 | 29 | 0 |
| `ADAPTIVE`, W 1,600,000 | 0.155 | 0.224 | 0.224 | 0.015 | 4 | 4 | 4 | 0 |
| `ADAPTIVE`, 0 threads | 25.329 | 33.889 | 33.889 | 33.814 | 4 | 4 | 4 | 300 |
| `ADAPTIVE`, `k` 1 | 0.076 | 0.282 | 0.282 | 0.014 | 3 | 3 | 3 | 0 |
| `ADAPTIVE`, slow host, W 1,600,000 | 0.196 | 0.429 | 0.429 | 0.008 | 4 | 4 | 4 | 0 |

The remaining `ADAPTIVE` rows follow `STRICT`'s within a tenth of a millisecond and with the same
latencies; they are in the data.

- **The main thread** stays far inside 2 ms whenever there is a worker: at most 0.56 ms at p99, on
  the slow-host proxy. Without workers (`pathThreads = 0`) it runs every search itself, 34 ms at
  p99: that setting is for tests and debugging.
- **`STRICT`'s join** stays at 0.03 ms at p99 or less with `k` = 2 or more, the slow-host proxy
  included: the workers finish within the two ticks. With `k` = 1 they do not, and the main
  thread joins 33 unfinished jobs, 5.5 ms at p99.
- **The latency is W's:** admission takes 300 requests at W / 20,000 a tick, so the group, behind
  the 200, waits 30 ticks at W = 200,000 and 4 at 1,600,000. `k` adds itself: 6 ticks at `k` = 4.

**budget D's perf test** (`perftests.path_budget_d`, the same load in the perf batch's GameTest
server, 4 threads, JFR on): after two warm-up runs, ten runs pooled give 50 ticks with the main
thread's path work at 0.26 ms at p99, joins at 0.02 ms, and the group at 4 ticks at p95. Its first
run in a cold JVM joined 17 unfinished jobs, 11.6 ms on one tick, before the JIT had compiled the
search: the first large order after a server start can cost the main thread one long tick.

## Decision

- **`k` = 2**, unchanged: `k` = 1 makes `STRICT` join unfinished jobs, and every tick beyond 2
  adds a tick of latency.
- **The live delivery mode is `STRICT` everywhere.** Its join stays at 0.022 ms at p99 on the
  slow-host proxy, well inside the 0.5 ms the spec allows. `ADAPTIVE` stays built and tested
  (PATH-16.1) for hosts where that stops holding.
- **W = 1,600,000** expansion caps a tick, 80 searches at the 20,000 cap, instead of the
  estimated 200,000: behind 200 requests, a 100-unit order's results arrive in 4 ticks at p95,
  one inside the exit, and the main thread's path work stays at 0.38 ms at p99 (0.56 ms on the
  slow-host proxy). At the cap's worst case, 80 full searches cost the workers about 160 ms a
  tick; the searches measured here expand a tenth of the cap. Group paths (PATH-09, T24) turn a
  group order into one search and short joins, which W can be weighed against again.

**Both exits are met**, at the chosen settings, on one to four threads and on the slow-host
proxy. With S8's, which met its own on 2026-10-22, [ADR-0015](../adr/0015-pathfinding-approach.md)
moves to Accepted.
