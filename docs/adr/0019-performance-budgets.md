# ADR-0019: Performance budgets A–G at 600 units, measured from M0

Date: 2026-10-21

## Status

Accepted (2026-10-22): the owner approved the M0 revision below, every budget unchanged; spike S4
then measured budget F for bare humanoids, inside every value, and F stays provisional for armoured
units until ART-01's pass (M3)

**Revised 2026-10-21**, still Proposed, to follow the roadmap's
[engineering baseline](../roadmap/M0-foundation.md#plan-wide-engineering-baseline) (the review
requests are in [design/roadmap-changes.md](../design/roadmap-changes.md)):
- budget D counts path-result latency, not motion start: provisional steering moves every unit on
  the tick its order applies, so motion start can never miss the budget (TL-28);
- the milestone targets are the roadmap's budget matrix, which gates M1 at 600 units instead of
  300 (QAR-07, TL-37), and gates below 600 units also check the cost per unit (QAR-08);
- a gate asserts the budgets at its milestone's target scale, and the stress pass at twice that
  scale asserts only C p95 ≤ 50 ms (QAR-111).

The numbers in the table are unchanged. PERF-01 confirms the revised table with the M0
measurements and moves this ADR to Accepted.

## Context

- **Research contradiction 8.** The testing report capped "mod work" at ≤ 5 ms p50 and ≤ 10 ms p99
  for 4 × 200 units; the engine report planned about 20 ms of the tick for units. Neither figure is
  measured, they differ by a factor of four, and they measure different things.
- **Every per-unit cost is an unverified extrapolation** from other versions, loaders and CPUs
  (gap G1). Client frame time with hundreds of humanoids is unmeasured (gap G5).
- **Unit counts** follow [01-feasibility §5.1](../01-feasibility.md):
  - MVP: 2 × 150 (population cap D-01: 150 per player by default, decided 2026-10-21);
  - multiplayer stage: 4 × 150 = 600;
  - stretch: 800, only with 2× headroom.
- **Hosts.** A host twice as slow as the owner's M4 Pro must still hold 20 TPS.
- **MSPT misses work.** `MinecraftServer#getTickTimesNanos` covers `tickServer` only, so packet
  handlers and the `ChunkMap#move` that every accepted movement packet triggers are invisible to
  MSPT ([design/judgement-performance.md §6.2](../design/judgement-performance.md)).

## Decision

The budgets apply to the M4 Pro, at the design scale of 600 units unless noted:

| ID | Budget | Measures | Target |
|---|---|---|---|
| A | Core | `MatchCore.step` plus our adapter appliers | p50 ≤ 5 ms, p99 ≤ 10 ms |
| B | Strategos-attributable | A + vanilla work on our bodies + server-thread job work + replication + our between-tick handlers; measured as the same arena with and without the match | p95 ≤ 17 ms |
| C | Whole tick | MSPT plus between-tick task time | p95 ≤ 23 ms |
| D | Pathfinding, main thread | Admission, delivery, joins; latency counted as path-result latency, not motion start (provisional steering moves units at once) | ≤ 2 ms p99; a 100-unit order's path results within 5 ticks p95 with 200 queued |
| E | Network per client | All bytes, ours and vanilla's | ≤ 100 KB/s with 600 visible moving units |
| F | Client frame (200 units on screen, render distance 16, Sodium on and off) | Frame, our drawing, unit drawing, client tick | p50 ≤ 16.7 ms, p99 ≤ 25 ms; ours ≤ 3 ms; units ≤ 6 ms; client tick ≤ 1 ms |
| G | Memory and saves | Core state, nav, autosave | ≤ 64 MB per 384² match; nav ≤ 32 MB at 512²; serialisation ≤ 100 ms |

- **The two contested figures are budgets A and B.** Testing §9's figure is A, the core's own work.
  Engine §1.2's "≈ 20 ms for units" is B, which includes vanilla's cost of our bodies. Both hold.
- **Milestone targets** are the roadmap's
  [budget matrix per milestone](../roadmap/M0-foundation.md#budget-matrix-per-milestone-delivered-and-kept-by-perf-01),
  which PERF-01 delivers and keeps: the scale, window and budgets of every gate (M1 600 moving
  units; M3 2 × 100 and 20 buildings; M4 300 units and 60 buildings; M5 300 fighting; M6 2 × 150,
  60 buildings and the AI; M7 4 × 150 and 120 buildings). Each budget is asserted from the first
  gate that can measure it: A and G from M0, B to E from M1, F only with the owner's consent
  (PERF-04, PERF-14).
- **Every perf gate** asserts the budgets at its milestone's target scale. A stress pass at twice
  that scale asserts only C p95 ≤ 50 ms, after 200 warm-up ticks. Below 600 units the unit-driven
  buckets are also normalised per unit: a gate fails when p95 per unit grows more than 15 % over
  the previous milestone's committed baseline.
- **C's R1000 profile** (1,000 units, 8 factions) is informational and never a merge gate.
- **Enforcement.**
  - The L6 perf batch runs alone and drops 200 warm-up ticks. It gates locally at the budget and on
    CI at 3×.
  - JMH fails above a 15 % regression.
  - L2 size tests cap every payload.
  - Every claim comes from a report in `docs/spikes/`.
  - Baselines and thresholds change only with the owner's approval and an ADR.
- **The core never degrades itself:** caps count work (ADR-0009). Any overload policy touches only
  presentation rates.

**What the spikes replace:**
- **S1:** the per-body cost behind B, and with it the population cap D-01. A best body above 15 µs
  at 500 moving units drops the default cap to 100 per player. *Reported 2026-10-22: the
  kinematic body costs 0.12 µs at 500 moving (0.10 beyond the core), so D-01 stays at 150; the
  whole server tick with 1,000 kinematic bodies fighting is 0.20 ms p50 and 0.30 ms p99 without
  players ([S1](../spikes/S1-server-cost-per-body.md)); the revision proposal below carries it.*
- **S2:** the vanilla baseline of a force-loaded arena (≤ 4 ms idle at 576 chunks), which sets the
  arena size D-17. *Reported 2026-10-22: 0.06 ms idle at 576 chunks, autosave median 5.3 ms;
  no size is limited by the server's arena cost ([S2](../spikes/S2-force-loaded-arena.md)).*
- **S4:** budget F, run with the owner's consent. If 200 armoured humanoids cost more than 6 ms with
  Sodium, distant units lose their layers. M0's S4 (PERF-04) measures bare humanoids, because
  equipment layers arrive with ART-01; the armoured and held-item pass runs with ART-01 in M3
  (noted 2026-10-21). *Reported 2026-10-22: 200 bare humanoids cost 0.69 ms of the frame with the
  client compat set (Sodium and the rest) and 4.47 ms without it, and the client tick is 0.72 ms
  p50; D-01 stays at 150 ([S4](../spikes/S4-client-frame-time.md)).*

PERF-01 confirms this table with the M0 measurements and moves this ADR to Accepted once the owner
approves the numbers. If S4 has not run by then, budget F stays provisional, and the status says it
is pending consent.

## Revision from M0 (2026-10-22), approved by the owner

PERF-01.3. The M0 measurements, on the M4 Pro, against the provisional table. The owner approved
the proposal on 2026-10-22, so the table above stands as it is.

| Budget | Provisional | Measured in M0 | Proposed |
|---|---|---|---|
| A, core | p50 ≤ 5 ms, p99 ≤ 10 ms | 600 walking units in a real server, step plus adapter: p50 about 0.05 ms, p99 about 0.3 ms (`perftests.walk_600_budget_a`); one core step with 600 walkers 0.014 ms (JMH) | unchanged: M0's core has no pathfinding, combat, economy or AI yet, and the headroom is theirs |
| B, Strategos-attributable | p95 ≤ 17 ms | 1,000 kinematic bodies fighting, the whole server tick: p50 0.20 ms, p99 0.30 ms; a body costs about 0.1 µs beyond the core ([S1](../spikes/S1-server-cost-per-body.md)); no players, so replication is not in it | unchanged; first gated by PERF-05 in M1 |
| C, whole tick | p95 ≤ 23 ms | a 384² arena, `ticking=false`: idle p50 0.06 ms; with 600 bodies p95 0.32 ms ([S2](../spikes/S2-force-loaded-arena.md)); no between-tick work without players | unchanged |
| D, pathfinding | ≤ 2 ms p99; 100-unit results within 5 ticks p95 | not measurable before M1's pathfinding | unchanged |
| E, network per client | ≤ 100 KB/s with 600 visible moving units | not measurable before M1's payloads | unchanged |
| F, client frame | p50 ≤ 16.7 ms, p99 ≤ 25 ms; ours ≤ 3 ms; units ≤ 6 ms; client tick ≤ 1 ms | Filled in after the approval, from [S4](../spikes/S4-client-frame-time.md): 200 bare humanoids at render distance 16, with the client compat set / vanilla: frame p50 1.06 / 4.95 ms, p99 3.93 / 7.42 ms; units 0.69 / 4.47 ms; client tick p50 0.72 / 0.91 ms, p99 0.91 / 1.30 ms; M0 draws nothing of its own | unchanged; provisional for armoured units until ART-01's pass (M3) |
| G, memory and saves | core state ≤ 64 MB per 384² match; nav ≤ 32 MB at 512²; serialisation ≤ 100 ms | saving a 500-unit blob: median about 1 ms (`perftests.snapshot_500_budget_g`); encoding it to JSON 0.22 ms (JMH); autosave of a 384² arena with a 600-unit match: median 6.6 ms; the heap grows by about 3 MB for 600 bodies | unchanged |

- **D-01** stays at 150 per player (S1: the best body is 0.12 µs at 500 moving, against 15; S4:
  200 bare humanoids cost 0.69 ms of the frame with the compat set, against 6).
- **Gates.** The proposal keeps the
  [budget matrix](../roadmap/M0-foundation.md#budget-matrix-per-milestone-delivered-and-kept-by-perf-01)
  as it is: every gate keeps its scale, window and budgets (M0: A and G, at the spike scales; M1:
  PERF-05, 600 moving with a 1,200 stress pass, 10 minutes, A–E and G; and so on to M14). In M0
  both perf gates are enabled: five runs of one tree gave a p95 coefficient of variation of 4.9 %
  for A and 3.2 % for G.
- **Why not tighten now.** The measured values are 20 to 100 times inside the budgets, but every
  system that will spend the budgets arrives after M0. A tighter budget now would only fail later
  for reasons M0 cannot see; the per-unit check below 600 units already catches regressions.

## M5 two-mobility rebuild profile (2026-10-26)

Under the owner's delegated recommended decisions and AGENTS.md autonomy defaults, establish
TerrainEditBench.rebuildOnePage's two-grid Mac profile after removing avoidable recovery overhead.
Its pre-M5 baseline is 291.665 us/op; the same-host T1 control is 290.034 us/op. The first
recovery measured 1027.918 us/op; cached template mobility, empty-footprint eviction bypass,
effective-slice sharing and selective border invalidation reduce it to 481.023 us/op.
The remaining 1.66x cost builds the second class's clearance, links and components, within
M5 §2.1's planned roughly twofold cost. The unchanged absolute path budget passes at
0.427499 ms p99 against 2 ms; the standing-unit ratio is 0.218951 against 0.30, with valid load.

Only this existing benchmark's score profile changes; the 15% JMH regression limit, all other
existing benchmark scores, absolute budgets and per-unit comparisons remain unchanged. The
proposal is retained as perf/baseline-m4pro.proposed.json; metadata paths are sanitized.
The diagnostic uses the standard forks/warmup/measurement settings and is not itself a full
comparison gate; final full JMH passes against this profile (21 configurations, 5m56s).
The newly introduced PageBuilderBench.buildBothMobilityClasses also receives its first
profile at 112.663 us/op; existing PageBuilderBench.build and every other existing score
remain unchanged. NodeDiscoveryBench.provingGround remains informational as before.
Evidence and rollback: docs/testing/data/M5-T2-recovery-m4pro.json; restore the prior
TerrainEditBench row from commit 5437a1f to reinstate the single-grid comparison. This does not
close the separate historical main CI investigation or approve a different platform's baseline.

## Consequences

- Every phase has a target (§4.4 of [04-architecture.md](../04-architecture.md)), and budget
  regressions fail the build instead of surfacing in playtests.
- Between-tick work is accounted for, so a design that moves cost into packet handlers cannot
  hide it.
- Until S1, S2 and S4 report, the numbers are hypotheses, and this ADR says so in its status.
- A slower host loses frame rate or TPS but never diverges in outcome.

## Alternatives considered

| Alternative | Why not |
|---|---|
| A single "mod time" budget | Hides vanilla's cost of our bodies, which is the largest unknown |
| R1000 on 1,024² as the gating reference (C) | More than three times the MVP's load; would block merges of ordinary feature work |
| MSPT alone | Misses packet-handler time, including `ChunkMap#move` per movement packet |
| Time-based adaptive budgets in the simulation | Outcomes would depend on CPU speed |
