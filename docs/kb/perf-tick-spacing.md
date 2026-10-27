# Performance fixtures: tick spacing and valid sampling

## The first asynchronous deadline needs a real interval

A paced loop initialized with `next = now` runs its first tick immediately. If the preceding
setup tick already admitted jobs, STRICT K=2 delivery then gets only one 50 ms interval,
not two. On a single worker the server may spend the missing interval waiting in `join`.
The historical perf run 40176248614 showed this pattern: D p99 50.288 ms, join p99 49.743 ms,
with low measured foreign CPU. That is evidence to inspect pacing, not to raise a budget.

`PathLoad` uses the pure test fixture `StepPacer`, initialized before the scattered-order tick so its work counts toward
the first interval. Fake-clock tests cover the first K=2 deadline, work inside a tick,
early wakeups and slow steps without catch-up bursts. Both normal and group loads use it.
Latency in simulation ticks, admission W, delivery K, workload and all limits stay fixed.
An interrupted pacing wait fails the fixture instead of silently removing its pacing.

## Twenty GameTest ticks are not one second

The GameTest server runs ticks back to back unless the fixture explicitly enables pacing.
An idle/moving comparison's warm-up can therefore rush past while the JIT is still compiling;
CPU-load polls every 20 ticks can occur much faster than the intended one-second interval.
The comparison is asymmetric: idle runs first, movement after another 800 simulation ticks.

The idle-300 fixture enables the existing 20 TPS server pacer after all bodies are ready,
then disables it before its verdict. Its 200 warm-up ticks and 600 samples per mode are
unchanged. The real spacing gives background work and CPU-load sampling their intended
intervals; the pause is outside all measured core/adapter spans. The existing batch cleanup resets pacing
after failure or timeout too, so the next test does not inherit it.

Even correctly spaced sequential modes can carry different branch profiles. The completed
JFR showed walking-triggered deoptimizations and late Avoidance C2 compilation after idle
was measured. Precondition both modes on a disposable match, dispose it, then measure on a
fresh original scene. Retain the original per-mode warm-up and sample counts, orders and
threshold. Report preconditioning counts explicitly; do not accept an invalid CPU window
as an A/B comparison or add warm-up repeatedly just to select a green result.

## A size comparison must keep the probe-object working set fixed

A 64K-element probe array does not imply 64K distinct position objects. Filling a small
index's unused probe slots by copying references left 16K objects, versus 64K for the large
index. A lookup benchmark then measured both index size and the input objects' cache locality.
Allocate distinct immutable probe positions in the tail too. Section counts, coordinate
coverage, lookup count, warm-up, median calculation and the ratio limit stay unchanged.

## Invalid measurements remain invalid

Windows measurements are not a Mac baseline. Preserve actual worker count and CPU verdict.
If foreign load exceeds 0.25, retain the invalid report and identify/reduce interfering load;
never override the value or retry until a green result appears. A newly started VM can change
the environment halfway through a benchmark. Pausing applications temporarily needs the
operator's authorization, and every successfully paused process must be resumed in `finally`.

## Match the engine clock to the pacer too

Vanilla advances its next-tick deadline even though GameTestServer omits the corresponding
wait. Accelerated setup can leave that deadline far ahead. Changing only the Post-event sleep
to 33 ms for 1.5x speed still leaves vanilla calculating 50 ms work budgets and can let optional
chunk work dominate a measured frame. The dev-only pacer now sets the engine rate to the same
20 x speed used by production controls, aligns both next-tick/deferred-task deadlines at Pre,
and restores the prior rate afterwards. A dedicated L3 test first failed on the old 20 TPS
engine rate, then passed at 30 TPS, a bounded deadline, and restored original rate.

## Windows CPU accounting and actual background processes

Windows uses cumulative GetSystemTimes and GetProcessTimes counters over one shared window.
The system kernel counter includes idle time; busy system time minus this process's kernel
and user time gives foreign CPU time. All counters use the same 100 ns units across CPUs.
The reader uses JNA already provided by Minecraft and explicitly supports one Windows
processor group (up to 64 logical CPUs). Missing counters fail measurement; no load is forged.
Non-Windows sampling stays unchanged. Every report identifies its CPU source.

New background processes can start after the initial application pause. In this investigation
system/raw process counters showed almost three cores used by a new VM, Python and a game.
These are real resource costs, not evidence to ignore a load guard. Respect the operator's
constraint to leave Python running and let them close their game. Do not export huge JFR JSON
or run another benchmark alongside an active measurement; analyze binary recordings afterwards.

## Idle geometry versus idle AI

A unit with IDLE AI can still move through avoidance. In this 300-unit fixture about 50 units
per tick retained nonzero displacement even after 1,200 ticks; the separation/ORCA behaviour
must remain active. Caching only the footprint floor, and separately removing intermediate
spatial query ordering, did not close the gate and were removed.

An earlier prototype cached exact positions only after height stopped changing. That did
not close the idle gate: small avoidance steps invalidated it too often. The final cache
instead keys the actual support cells, described below. Height easing, avoidance, velocity
reset, pushed countdown and arrival-latch handling continue every required tick. This derived
match-local cache is neither serialized nor hashed. Idle speed is read only when the
feasible-zero shortcut cannot apply.

The feasible-zero shortcut requires stationary self, no nearby external agents, no walking
neighbour, no crowded separation fallback, and a conservative no-collision bound for every
selected neighbour: distance exceeds reach plus horizon times L1 speed. This bounds any
possible approach without square roots. Validate it against the full ORCA solver, including
small residual velocities; do not round velocity to zero or reduce the avoidance cadence.

## Slice one generated voxel world, not one new world per page

ProvingGround.voxels() constructs the complete 384x384x64 voxel representation. Calling it
inside a 576-page loop created about 10.4 GiB of temporary allocations in a single PathLoad
construction, with repeated G1 humongous allocation/full compaction pressure. Construct the
voxel world once and take each page slice from it. Do not exclude GC samples or raise D to
hide fixture allocation pressure; keep the map, page count, jobs and timing unchanged.

## Preserve the spatial snapshot's candidate radius

UnitGrid deliberately stores positions from the last rebuild. A unit's current MotionStore
position may already differ. A rest-only query with a smaller radius can therefore miss a
candidate that the original full-radius query would inspect at its new position. A regression
reproduces this with displacement between movements and requires the same-tick reaction.
Keep the original radius and ordered nearest-neighbour selection. The final shortcut runs
a conservative clearance check on that same selected set; it rejects zero preference for a
walking neighbour, unsafe full-horizon clearance, nearby agent or crowded separation.
Any uncertainty uses the full existing solver. Experimental unordered early-exit probes
were removed after they added cost or missed snapshot semantics. Never use unordered
traversal for the solver's floating-point sums or nearest-neighbour tie-breaking.

## Cache the actual support cells

Ground validity depends on the centre column, selected floor and terrain version. The
footprint's standing floor depends on its four integer column bounds, selected floor and
version. Exact X/Z movement inside those same bounds does not alter the geometric answer.
Compute bounds with the current radius, and keep height easing outside the cache: a changed
Y still falls, and a step still rises in two ticks even when geometry is reused. An unchanged
height may skip its store write only with identical bits and no rising flag, retaining signed
zero normalization. A just-verified ground can be passed to idle movement without querying
that same column twice. Retain the reference calculation on every key change.

The attempted early-exit spatial probes did not improve the accepted workload and were
removed entirely. The original ordered query is retained; do not infer that those historical
experiments are part of the final candidate.
