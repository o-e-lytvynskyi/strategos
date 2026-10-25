# RTS simulation in Java: avoidance, crowd arrival, allocation measurements

Facts from this repository: the deterministic core `core/sim`, M1 T20–T23. Minecraft has nothing
to do with it here. This is about ORCA, unit crowds and JMH. The mod toolchain is in
[minecraft-mod.md](minecraft-mod.md).

## ORCA: a pair exactly head-on slows down to zero

- Two agents on one line toward each other do not part under pure ORCA (RVO2). The "cutting
  circle" line is then perpendicular to their path and limits only the longitudinal speed. Both
  slow down and stop at a distance equal to the sum of the radii.
- The RVO2 examples break the symmetry with random noise in the preferred velocity. A
  deterministic simulation cannot do that.
- The fix: if some line constrained the result (it is ≠ the preferred velocity), solve again with
  the preferred velocity rotated by 0.001 rad, to the same side for everyone. Pairs part in ideal
  time, asymmetric cases do not change. A rotation of 0.05 rad or more already sends units
  sideways for no reason.
- A pure solver test catches it: two agents from (0, 0) and (10, 0) toward each other, each
  steering to its own goal point. Both must arrive.

## Avoidance and arrival at one point

- With avoidance toward a common point, one unit stops and the others push in WALKING forever. In
  Strategos this broke 52 GameTests: patrols and "five units to a point".
- What worked while there are no group slots. A unit that has not progressed for 10 ticks arrives
  where it stands, if:
  - the point is taken by a standing unit or a stuck one with the same goal, and the point is no
    more than 1.5 blocks away;
  - it touches an already arrived unit with the same goal, so the crowd settles in layers
    outward.
- What broke:
  - the tolerance "arrived within 0.5 of the goal if avoidance pushed it off" seats units beside
    the slot and in a tight formation locks in those who go deeper;
  - "a stuck walker takes the point" without the condition "same goal": in a formation a foreign
    walker "takes" a slot, and the owner sits beside it.
- A formation test: the slot step is ≥ two radii plus a passage (here 1.5 at radius 0.35; with
  step 1 the gap is 0.3 and the first arrivals wall in the rest). The goals are a shift of the
  formation, not a mirror: the first arrivals take the far places.
- A path from one point (the goal is visible in a straight line): avoidance pushes the unit out of
  the passage, the straight line hits the wall, and the unit presses into it. A path re-request is
  needed: here every 20 ticks without progress the visibility of the point the unit steers to is
  checked.

## Standing units in ORCA: who yields to whom

- Standing units plan less often than walkers (here once per 4 ticks). A walker that counts on
  their "half of the maneuver" gets stuck in a standing crowd: a walker against a standing unit
  takes the whole maneuver.
- A standing unit that sees only the walker's *velocity* does not yield to one stuck in front of
  it: the velocity is zero, a deadlock. A standing unit must avoid the walker's *intention*, its
  preferred step in this tick. For that, first compute the steps of all walkers, then plan all
  units, in the same tick, with no memory between ticks (otherwise a restore from a snapshot
  diverges from the original run).
- Two standing units that settled slightly closer than the sum of the radii push each other
  forever, and the group "trembles". Standing units get their own smaller gap among themselves
  (here 0.6 for a sum of 0.7).

## Group formation: places and fill order

- A place step of "2 radii + 0.1" leaves 0.1 between standing units: a unit whose place is
  surrounded by earlier arrivals cannot get to it. A passage is needed: 4 radii plus a gap.
- Fill front to back: the front units go to the far places, by columns across the path. Swapping
  places for non-crossing paths (as in the matching problem) and filling by rows break this order
  and lock someone in again. Check on a dozen different starting layouts.
- Places beyond the map edge (a match without terrain, a goal at the edge) are pulled inward.
- The check "everyone within N blocks of the goal" for a group with places is flaky: the far place
  of a 3×3 formation is 2.12 from the goal, a group member stops 1.5 from its place, and the latch
  holds it within 1.0. Measure the distance to its own place (≤ 2.5). A random match seed in the
  smoke test gave about 1 failure in 40.
- How to tell an old flake from a new regression: run the same scenario in the core on hundreds of
  seeds, on the current code and on HEAD (`git worktree add --detach`). The same seeds with the
  same coordinates mean the flake is old.

## A large formation (200): swapping places and passing points in a crowd

- 200 units into a 15×14 formation at once: the crowd pushes a unit past its place and it cannot
  fight back, 130+ ticks without progress. The fix: a participant stuck for ≥ 10 ticks takes the
  place of a still-walking comrade within 1.5 blocks of it and sits down. The comrade gets its
  place and a new path. The formation does not care whose place is whose. Result: 57 ticks at most.
- Pitfalls of the swap: (1) the seated unit's "goal" in the movement state is still the old place.
  A partner that touches it triggers the rule "touches an arrived unit with the same goal" and
  sits 2 blocks from its place. Rewrite the seated unit's goal. (2) With two-pass movement the
  partner's step for this tick is already planned to the given-away place, so do not step it in
  this tick. (3) Tests that compare against the original place must track swaps by the change of
  orders.
- An intermediate path point in a crowd: count it as reached within 2 blocks (not 0.5) after 10
  ticks without progress, if the next one is visible in a straight line. Repeat requests for 200
  units dropped by half.
- Lanes of a shared path collapse at walls (a shift into the wall is impossible). Try half and a
  quarter of the offset before standing on the path axis.
- Standing units approach their gap asymptotically: check the gap after ~60 ticks of rest and with
  a tolerance of 1e-3, or the test catches 0.5999999.

## The stuck ladder: what counts as progress

- A "ticks without progress" counter that resets on any change of the point the unit steers to
  breaks the ladder. The first rung is a path re-request, the new path gives a new first point,
  the counter goes to zero, and the unit sits on the first rung forever.
- Progress is only getting closer to the point by a noticeable amount (here 0.05 blocks from the
  best) or passing a path point. A new route is not progress, and a nudge of the ladder is not
  either (the best distance is rewritten after a nudge). A new order and a stop reset the counter
  explicitly.
- The check: for an immobile unit the counter grows by exactly 1 every tick through a route change
  and a nudge.
- "Walled in with no exit" in a core test: walls from the terrain give "no path", and the move
  ends in place (not a failure). A ring of units is passed through on the "no avoidance" rung,
  because units are not solid to each other. Only a unit with speed 0 (a modifier) has no exit on
  any rung.

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
