# RTS simulation in Java: measuring the core (JMH, allocations, the job pool)

Facts from this repository about measuring the deterministic core: JMH, allocation counts, the
pathfinding job pool and `jmhCompare`. Movement and crowds are in [rts-sim.md](rts-sim.md).

## A task pool for pathfinding: how to measure

- A harness that steps ticks back to back lies: the worker threads do not get the 50 ms between
  ticks, and the main thread finishes the tasks itself on join. Here 130–170 of 300 results went
  out "unfinished", and the main thread's work came out 10–50 times larger. Step with a real pause
  of up to 50 ms.
- A proxy for a "slow host" is busy threads (2 × cores) next to one worker. An option that only
  replaces the measured foreign load with a number does not slow the host.
- The first big run in a cold JVM: 11.6 ms per tick against 0.26 ms warmed up (the JIT has not
  compiled the search yet). A perf test: two warm-up runs, then several into one sample.
- The tolerance "the sum of expansion limits ≤ W per tick" with a search limit of 20 000 and
  W = 200 000 means 10 searches per tick: an order for 100 units out of 200 in the queue waits 30
  ticks. Choose W by latency (here 1 600 000, 4 ticks), not by an estimate "by eye".

## `Arrays.sort(int[])` allocates

- The JDK sort (DualPivotQuicksort) for 44+ elements tries to merge already sorted runs
  (`tryMergeRuns`) and allocates an array for it if there are several runs. This is exactly what
  results gathered from a grid cell by cell look like: a core "without allocations" suddenly gave
  19.6 KB per step.
- To find it: JMH `-prof jfr` (present in JMH 1.37), then
  `jfr print --events jdk.ObjectAllocationSample --stack-depth 8 <profile.jfr>`. The stack shows
  `DualPivotQuicksort.tryMergeRuns`.
- Running JMH outside the Gradle task: an init script prints the classpath of the `jmh` source set
  (`sourceSets.jmh.runtimeClasspath.asPath`, with `--no-configuration-cache`), then
  `java -cp … org.openjdk.jmh.Main <regex> -prof jfr:dir=…`.
- The fix: return results in ascending index order through a bit mask (a `long[]` by index, a walk
  over the set bits). No sorting, and faster: a query over 1000 units 0.39 → 0.25 ms.

## A for-each over a `List` in a hot path allocates its iterator

- `for (CivId c : civs)` over an immutable list (`List.copyOf`, `List.of` with more than two
  elements) builds an `Iterator` on every call; escape analysis does not always remove it. M10 T3's
  hash guard did this inside `StateHasher.hash`, and `ShotBench.hashActive300` in `jmhCompare`
  failed the allocation gate (32 B/op against 1). Use an index loop (`for (int i = 0; i < n; i++)`)
  in anything a `@HotPath` kernel or the hash calls, and run `:core:jmh :core:jmhCompare` for any
  task that touches the hash, even through a small helper.

## `-prof gc` counts JMH service threads too

- The JMH service threads allocate about 7 KB/s, and `gc.alloc.rate.norm` spreads this over the
  operations: ~0.007 B per microsecond of operation time, for any benchmark. The sign is the same
  ratio of B/op to operation time in all benchmarks.
- So the threshold "≤ 1 B/op" for cores without allocations breaks on operations longer than
  ~140 µs.
- The fix: a tolerance of max(1 B, 2 × background × operation time) and a self-check of the gate
  on fixtures: a long core with real allocation is still caught, a long one with only the
  background passes.

## An exception in `@Setup` silently drops the benchmark

- JMH catches the setup exception, prints `<failure>` and goes on. The run ends with code 0, and
  this benchmark is simply absent from `result.json`. A gate that compares only the present
  results stays green. In this repository two `@HotPath` cores dropped out this way for all of M1.
- The gate must fail itself if a benchmark from the baseline or from the core list is missing from
  the result (`JmhGate.missing`). A benchmark outside the baseline is not seen by the gate at all:
  `NodeDiscoveryBench` threw in the method itself (the "60 nodes" check after a map change) from
  M3 T9 to M4 T19. Look for `<failure>` in the log.
- A setup that waits a fixed number of ticks ("paths arrive in 4") breaks with any change of the
  delivery pace. Wait for a condition bounded from above, or open a tolerance window for the scene.

## `jmhCompare` on a machine other than the baseline's

- `perf/baseline-m4pro.json` comes from one machine. On another (an M1 Pro under a load average of
  about 12, M6 T2.2) about 20 benchmarks come out 40–60 % slower, unrelated ones included, and the
  gate goes red without any regression in the change.
- To judge a change there, compare it with its own base on the same machine. Make a worktree of the
  base commit (`git worktree add <dir> <base>`), run `:core:jmh` in it and in the working tree one
  after the other, never at the same time. Then run `:core:jmhCompare -Pjmh.result=<new> -Pjmh.baseline=<base>`.
- `:core:jmh` always writes `core/build/reports/jmh/result.json` in its own tree; `-Pjmh.result`
  only tells `jmhCompare` where to read. Copy the two files out before comparing them.

## JMH straight after a long all-core run reads high

`:core:jmh` started right after `:core:openingBalance` (nine minutes on every core) flagged
`SpatialBench.rebuild1000` at 5.839 µs/op against a baseline of 4.773 (+22 %); the same code on a
machine left idle first gave 4.805 (M10 T25a). The foreign-load profiler (ADR-0026) sees no other
process, so it calls the environment valid: the residual heat of the previous run is not "foreign
load". Run `jmh` first in a chain of gates, or after an idle pause, and rerun a lone new regression
on a quiet machine before treating it as the code's.
