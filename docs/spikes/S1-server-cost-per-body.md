# S1: server cost per body

*Written with M0 T13 on 2026-10-22, the numbers rerun with T14 on the spec's `ticking=false`
tickets. Spec: [M0 §8.4](../milestones/M0-foundation.md#84-s1-server-cost-per-body-t13--confirms-adr-0011-checks-d-01s-default-feeds-budget-b);
the runner is §8.3.*

## Question and exit criterion

What does one unit body cost the server per tick: a production kinematic body (K), a stripped
`PathfinderMob` (P) and a physics `LivingEntity` (L), idle, moving and fighting, at 200, 500 and
1,000 bodies? The answer prices budget B and checks [ADR-0011](../adr/0011-unit-entity-and-body-seam.md)
and [D-01](../roadmap/M0-foundation.md#d-01--population-cap-per-player)'s default.

Exit (04 §19.2):
- a kinematic body costs **≤ 4 µs per tick beyond the core** at 500 moving bodies;
- the best body costs **≤ 15 µs** at 500 moving, otherwise D-01's default cap drops from 150 to 100
  per player, as the owner decided on 2026-10-21.

## Setup

- Apple M4 Pro, 12 cores; macOS as the JVM reports it, "Mac OS X 27.0.1" on aarch64.
- Temurin 21.0.12.1, heap pinned at 4 GB; NeoForge 21.1.264; the dedicated dev server, no players.
- 2026-10-22, on the M0 T13 tree (commit `095ffc5`) with T14's changes uncommitted.
- Commands, from the repository root:
  - `./gradlew :mod:runSpike -Pspike=s1` (67 s for the whole matrix);
  - `./gradlew :mod:runSpike -Pspike=s1 -Pcompat.server` (Lithium 0.15.4, ModernFix 5.27.26,
    FerriteCore 7.0.3; 37 s);
  - `./gradlew :mod:runSpike -Pspike=s1 -Pspike.jfr`, then `jfr view hot-methods` on the recording
    and `jfr print --json --stack-depth 96 --events jdk.ExecutionSample`, with the server thread's
    samples counted per frame.

## Method

The spike server (`SpikeDriver`, §8.3) turns off saving, the day and weather cycles, mob
spawning, fire, griefing, random ticks and entity cramming, and runs ticks back to back
(`requestGameToSprint`). The arena is 96 × 96 blocks at (1024, −60, 1024), force-loaded with
`TicketController#forceChunk(…, true, false)`.

Each configuration spawns its bodies and waits until all exist, then runs 200 warm-up ticks and
600 measured ticks, and is torn down; 40 idle ticks follow. A first pass of K, P and L, 500
fighting each, is run and not recorded, so that the JIT has compiled every body's path before the
first baseline. The sampler reads the server's own tick time
(`getTickTimesNanos()[getTickCount() % 100]`, written just before `ServerTickEvent.Post`). For K it
also reads the core step and the adapter time from `PhaseTimers`.

Work that a real game does inside the tick runs in `ServerTickEvent.Pre`: the swings and damage
events of "fighting" and the build storm's 64 `setBlock` calls. The driver's bookkeeping (who
arrived, the next orders) runs in `Post`, after the time is taken.

Per body µs = (tick p50 − baseline p50) / count; for K, "beyond the core" also subtracts the
step's p50. The bodies are:
- **K:** the production `UnitEntity`, driven by a production match through `SpawnUnits` and
  `Move`, posed by `KinematicDriver`;
- **P:** `SpikeMob`, with navigation for moving and `MeleeAttackGoal` with a target for fighting;
- **L:** `SpikePhysicsBody`, the full vanilla tick, walking forward at 0.25.

## Results

Vanilla server, tick p50 in ms and the cost per body in µs (all 35 rows, with p95, p99, max, heap
and entity counts, are in [data/S1-vanilla.csv](data/S1-vanilla.csv)):

| Body | Count | Idle p50 | Moving p50 | Fighting p50 | µs/body moving | K: beyond the core |
|---|---|---|---|---|---|---|
| K | 200 | 0.029 | 0.044 | 0.051 | 0.13 | 0.10 |
| K | 500 | 0.045 | 0.077 | 0.102 | **0.12** | **0.10** |
| K | 1,000 | 0.072 | 0.143 | 0.200 | 0.12 | 0.11 |
| P | 200 | 0.768 | 0.761 | 0.725 | 3.71 | – |
| P | 500 | 2.172 | 2.497 | 2.073 | **4.96** | – |
| P | 1,000 | 3.600 | 7.199 | 5.394 | 7.18 | – |
| L | 200 | 0.773 | 0.766 | 0.851 | 3.74 | – |
| L | 500 | 2.151 | 2.606 | 2.841 | **5.18** | – |
| L | 1,000 | 3.504 | 6.773 | 8.088 | 6.76 | – |

- Baseline (no bodies): p50 0.019 ms first and 0.038 ms last, a drift of about 20 µs.
- K at 500 moving: core step p50 0.010 ms, adapter p50 0.022 ms; at 1,000 fighting the whole tick
  takes 0.20 ms p50 and 0.30 ms p99.
- Melee blob, L with pushing, 300 bodies: p50 1.87 ms, 6.2 µs per body.
- Build storm, 500 moving plus 64 block changes a tick: K 0.167 ms (0.30 µs/body), P 2.931 ms,
  L 2.617 ms.
- Spawn category, 500 idle unbound unit bodies: `MISC` 0.039 ms against `CREATURE` 0.126 ms
  (0.04 against 0.22 µs per body).

With Lithium, ModernFix and FerriteCore ([data/S1-lithium.csv](data/S1-lithium.csv)), at 500
moving: K 0.123 µs per body (0.101 beyond the core), P 2.26, L 1.95. Lithium halves or better the
vanilla bodies, most at 1,000 (P moving 7.2 → 2.3 µs), and leaves K where it was. Its baseline
went from 0.012 to 0.032 ms over the run.

Attribution, vanilla, the whole matrix ([data/S1-jfr-attribution.txt](data/S1-jfr-attribution.txt)):
the entity loop holds 92.8 % of the server thread's samples, and 75 % sit in
`LivingEntity#travel`, movement against block collisions (`hasLargeCollisionShape` alone is 17 %).
`Mob#serverAiStep` is 8.3 %, `ChunkMap#tick` 1.3 %, `ServerChunkCache#tickChunks` 0.9 %,
`NaturalSpawner.createState` 0.2 %, and our `MatchRuntime#tick` 0.3 %.

## Analysis

- **Kinematic beyond the core: 0.10 µs at 500 moving, against ≤ 4.** Met by a factor of 40. A K
  body's server tick does nothing (`UnitEntity#tick` runs only on the client); what remains is the
  pose, the entity section move and the tracker's bookkeeping.
- **Best body at 500 moving: K, 0.12 µs, against ≤ 15.** Met, so D-01's default stays at 150 per
  player. Even the vanilla bodies stay inside it (5 µs).
- A vanilla body's cost is movement and collision, not AI: L, with no AI at all, costs what P
  costs. That is the cost ADR-0011 avoids, and the reason the core, not vanilla physics, moves
  units.
- The category check confirms `MISC`: a `CREATURE` body costs five times as much as an idle `MISC`
  one, for the natural spawner's per-category counting.

## What it decides

- **ADR-0011:** S1's exit criterion is met; the status stays "Proposed - pending spike S10", with
  the numbers added there.
- **D-01:** the default population cap stays at 150 per player.
- **Budget B (ADR-0019):** at 1,000 kinematic bodies fighting, the whole server tick takes
  0.20 ms p50 and 0.30 ms p99 without players. The per-body share of B is tiny next to its
  envelope; the revision proposal in ADR-0019 (T14) carries it.

## Raw data

[data/S1-vanilla.csv](data/S1-vanilla.csv), [data/S1-lithium.csv](data/S1-lithium.csv),
[data/S1-env-vanilla.json](data/S1-env-vanilla.json),
[data/S1-env-lithium.json](data/S1-env-lithium.json),
[data/S1-jfr-attribution.txt](data/S1-jfr-attribution.txt). The JFR recording (4 MB) is not
committed.

## Deviations and follow-ups

- **Warm-up pass.** The first run measured the early K rows on a cold JIT (`k_200_idle` slower
  than `k_500_idle`); an unrecorded pass of the three bodies now runs first.
- **The first S1 run used ticking tickets**, on the wrong belief that `ticking=false` leaves
  entities frozen without a player; S2 showed it does not (every region ticket enters the ticking
  tracker, DistanceManager.java:197–202), and these numbers come from the rerun on `ticking=false`.
  The two runs agree within noise.
- **MAX_HEALTH 1,000,000** is clamped by vanilla to 1,024; P's fights never end within a
  configuration either way.
- **No players.** Tracking and its packets cost nothing here; S9 (and S4 on the client) price
  them. "Time outside MSPT" is zero for the same reason.
- **The baseline drifts** by about 20 µs over a run, with and without Lithium; JIT and GC are the
  likely causes. It does not touch the verdicts.
