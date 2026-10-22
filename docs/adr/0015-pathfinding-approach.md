# ADR-0015: Layered 2.5D surface grid, staged search, ORCA and deterministic job delivery

Date: 2026-10-21

## Status

Proposed - pending spike S7 (S8 reported 2026-10-22)

**Revised 2026-10-21**, still Proposed, to follow the roadmap's
[spike table](../roadmap/M0-foundation.md#spikes) and the revised ADR-0019: S7's latency criterion
counts path results, not motion start, because provisional steering moves units on the tick
their order applies (TL-28); HPA*'s trigger is S8 or the 384² and 512² crossings of PATH-03 and
PERF-05, and PATH-08 is the reserve card that builds it.

**Amended 2026-10-21** by [ADR-0023](0023-plan-alignment-after-roadmap-approval.md), still
Proposed: D-08's observer avatar is not one of ORCA's non-reactive agents, so units pass through it
(PATH-06).

**Revised 2026-10-22** with spike [S8](../spikes/S8-terrain-census.md), still Proposed pending S7.
- **Confirmed:** 4 surfaces a column, topmost first. On 1.74 million vanilla columns, 99.99 % need
  at most 4 in a band that follows the terrain, and the cap never drops a surface within 16 blocks
  of the terrain.
- **Bit layout fixed:** floor 12 bits, headroom 7, kind 3, cost code 2, links 8.
- **Measured:** 2.0–2.8 KB a page, at most 3.4 MB at 512²; a page builds in 42–84 µs (median) and
  at most 0.37 ms (p95).
- **One rule changes:** terrain taken from an existing world gets a band per page: 16 blocks below
  the page's lowest terrain, with the page's base Y stored in the page. One band from the region's
  lowest terrain failed the exit (98.2 %) and left up to 12.7 % of a square's columns above the
  band. Packaged maps keep the manifest's band.

## Context

Research contradictions 4, 5 and 11, and gap G8. Pathfinding at scale is the hardest problem of
the project ([01-feasibility §3.1](../01-feasibility.md)) and was RoN's main performance problem.

- **A single-layer 2.5D grid** cannot represent bridges, wall walkways, towers, overhangs or
  caves (FrostedHeart's limits).
- **RoN's 3D band of ±24 blocks** stores and searches about 49 cells per column, while a ground unit
  stands on one or two surfaces.
- **JPS assumes uniform costs** that step, drop, water and door costs break, and block edits
  invalidate its preprocessing.
- **Determinism.** A worker pool that delivers results when ready breaks deterministic replays.
  0 A.D. delivers at a fixed turn boundary.
- **Worker rules.** Workers must never touch `Level`, `ServerChunkCache` or the per-level
  `PathTypeCache` (fact 11). Snapshots are taken on the main thread with `PalettedContainer#copy()`.
- **The size of everything is unmeasured.** S8 (terrain census and encoding) and S7 (delivery under
  load) measure it.

## Decision

- **Representation.** Each column holds up to 4 standable surfaces, each with a floor height in
  1/16 block, head clearance, a class and a precomputed cost including wall proximity. Pages of
  16×16 columns are immutable once published and replaced copy-on-write. The core keeps overlays
  for building footprints, gates and committed foundations. S8 fixes the bit layout.
- **The pipeline.**
  - A `BlockClassTable` maps every block state to a class, from collision shapes plus
    `strategos:nav_class` overrides.
  - Section copies are captured on the main thread.
  - Pure worker jobs classify them and build pages, and the slices are journaled.
  - Every page of an arena is built at prepare.
- **Search, staged behind one `PathService` API:**
  - M1:
    - connected components for O(1) reachability, with `makeGoalReachable`;
    - no search for moves of at most 24 blocks with line of walk;
    - A* over surfaces (octile heuristic, non-uniform costs, at most 20,000 expansions, partial
      paths flagged);
    - string pulling.
  - HPA* over page portals (reserve card PATH-08) is added when S8 or the crossings of PATH-03
    and PERF-05 show flat A* misses budget D at 384² or 512². A second level comes only for
    1,024² and the open world. JPS is rejected.
- **Groups.**
  - Spread destinations, previewed on the client.
  - One shared group path per group, plus short join paths, issuing at most 10 % of the requests of
    individual planning.
  - Flow fields only if PATH-09 shows they are needed.
  - Formations in M10.
- **Local avoidance.**
  - ORCA ported from RVO2 (Apache-2.0): at most 10 neighbours within 3 blocks, a 1 s horizon,
    cadence by activity tier, separation in cells with more than six units.
  - First-person players and mobs count as non-reactive agents.
  - Arrival settles at 1.5 blocks in groups and 0.5 alone.
  - A deterministic stuck ladder whose steps never depend on observation.
- **Deterministic delivery.** Jobs are pure functions of the snapshot at request time.
  - `STRICT`: results requested at `t` apply at `t+k` in request-id order, and the main thread
    runs or joins any unfinished job. Used by tests, headless matches and replays.
  - `ADAPTIVE`: from `t+k` on, finished results apply. The delivered ids are journaled, so replays
    reproduce the timing; a job unfinished at `t+k+8` is joined.
  - `k` = 2, and 4 for page builds.
  - S7 picks the live mode: `STRICT` everywhere if its join stays ≤ 0.5 ms p99 on the slow-host
    proxy.
- **Admission** counts work: requests are admitted in id order while the summed expansion caps stay
  within W per tick (starting estimate 200,000). A queue beyond 2,048 answers `BUSY`,
  deterministically and distinct from `NO_PATH`.
- **Threads.** Integrated server `clamp(cores/3, 1, 4)`, dedicated `clamp(cores/2 − 1, 1, 4)`;
  `pathThreads = 0` runs jobs inline. Identical hashes for 0, 1 and 4 threads are an L1 test.
- **Invalidation.**
  - The core's own block edits update its overlays directly and are never observed back.
  - External edits arrive through the `LevelChunk#setBlockState` mixin. They are coalesced and
    rebuilt after 4 quiet ticks, at most 4 pages per tick, while stale pages keep serving.
- **Responsiveness.** A unit steers on a provisional straight line from the tick its order applies.

**Exit criteria that confirm this ADR:**
- **S8:** ≥ 99.9 % of columns need at most 4 surfaces; a page builds in ≤ 0.5 ms; nav fits 32 MB
  at 512². Met on 2026-10-22, in a band that follows the terrain (see the revision above).
- **S7:** main-thread path work ≤ 2 ms p99, and a 100-unit order's path results within 5 ticks
  p95 with 200 requests queued.

## Consequences

- Bridges, wall walkways, roofs and caves within the band are walkable. Ladders, climbing and
  flight are not.
- Paths arrive 100 ms after an order. Provisional steering hides that, but the first 100 ms of
  motion may not be on the final path.
- Paths and page builds are bit-identical across runs, thread counts and machines, which makes
  movement bugs reproducible in L1.
- Construction and damage cost no navigation work.
- If S8 fails, the fallbacks are a higher layer cap or Y-band limits per map. If S7 fails, `ADAPTIVE`
  delivery, a larger `k`, a lower W or more workers.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Single-layer 2.5D heightmap with JPS | No multi-level terrain; JPS breaks under non-uniform costs |
| 3D cell band (RoN's ±24) | Memory and search cost over air cells; the band must follow terrain |
| Vanilla `PathNavigation` on bodies | Synchronous at 0.1–0.5 ms per search, a recompute storm on block changes, and units are not `Mob`s |
| Best-effort worker pool delivering when ready, without a journal | Thread timing changes paths, so replays and GameTests diverge |
| HPA* and flow fields from day one (A, C) | Infrastructure before measurement; kept behind the same API |
| Separate per-tick time budget for path work | Outcomes would depend on CPU speed (ADR-0009) |
