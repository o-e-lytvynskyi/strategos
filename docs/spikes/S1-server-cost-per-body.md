# S1: server cost per body

*Written with M0 T13 on 2026-10-22. Spec: [M0 §8.4](../milestones/M0-foundation.md#84-s1-server-cost-per-body-t13--confirms-adr-0011-checks-d-01s-default-feeds-budget-b);
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
- 2026-10-22, on the M0 T12 tree (commit `7e410e4`) with the T13 runner uncommitted.
- Commands, from the repository root:
  - `./gradlew :mod:runSpike -Pspike=s1` (67 s for the whole matrix);
  - `./gradlew :mod:runSpike -Pspike=s1 -Pcompat.server` (Lithium 0.15.4, ModernFix 5.27.26,
    FerriteCore 7.0.3);
  - `./gradlew :mod:runSpike -Pspike=s1 -Pspike.jfr`, then `jfr view hot-methods
    mod/build/spike/out/spike.jfr` and `jfr print --json --stack-depth 96 --events
    jdk.ExecutionSample`, with the server thread's samples counted per frame.

## Method

The spike server (`SpikeDriver`, §8.3) turns off saving, the day and weather cycles, mob
spawning, fire, griefing, random ticks and entity cramming, and runs ticks back to back
(`requestGameToSprint`). The arena is 96 × 96 blocks at (1024, −60, 1024), force-loaded with
ticking tickets (see deviations).

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
| K | 200 | 0.029 | 0.045 | 0.052 | 0.13 | 0.10 |
| K | 500 | 0.046 | 0.079 | 0.099 | **0.12** | **0.10** |
| K | 1,000 | 0.071 | 0.144 | 0.192 | 0.13 | 0.11 |
| P | 200 | 0.791 | 0.736 | 0.730 | 3.59 | – |
| P | 500 | 2.276 | 2.469 | 2.102 | **4.90** | – |
| P | 1,000 | 3.585 | 7.034 | 5.499 | 7.02 | – |
| L | 200 | 0.776 | 0.750 | 0.832 | 3.66 | – |
| L | 500 | 2.178 | 2.465 | 2.751 | **4.89** | – |
| L | 1,000 | 3.460 | 6.507 | 7.944 | 6.49 | – |

- Baseline (no bodies): p50 0.019 ms first and 0.019 ms last; no drift.
- K at 500 moving: core step p50 0.010 ms, adapter p50 0.023 ms; at 1,000 fighting the tick's p99
  is 0.35 ms.
- Melee blob, L with pushing, 300 bodies: p50 1.81 ms, 5.96 µs per body.
- Build storm, 500 moving plus 64 block changes a tick: K 0.185 ms (0.33 µs/body), P 2.909 ms,
  L 2.583 ms.
- Spawn category, 500 idle unbound unit bodies: `MISC` 0.039 ms against `CREATURE` 0.057 ms
  (0.04 against 0.08 µs per body); with Lithium 0.036 against 0.126 ms.

With Lithium, ModernFix and FerriteCore ([data/S1-lithium.csv](data/S1-lithium.csv)), at 500
moving: K 0.124 µs per body (0.105 beyond the core), P 2.19, L 1.91. Lithium halves or better the
vanilla bodies, most at 1,000 (P moving 7.0 → 2.3 µs), and leaves K where it was. Its baseline
went from 0.013 to 0.037 ms over the run, about 20 µs.

Attribution, vanilla, the whole matrix ([data/S1-jfr-attribution.txt](data/S1-jfr-attribution.txt)):
the entity loop holds 92.7 % of the server thread's samples, and 76 % sit in
`LivingEntity#travel`, movement against block collisions (`hasLargeCollisionShape` alone is 16 %).
`Mob#serverAiStep` is 7.8 %, `ChunkMap#tick` 1.0 %, `ServerChunkCache#tickChunks` 0.9 %,
`NaturalSpawner.createState` 0.1 %, and our `MatchRuntime#tick` 0.6 %.

## Analysis

- **Kinematic beyond the core: 0.10 µs at 500 moving, against ≤ 4.** Met by a factor of 40. A K
  body's server tick does nothing (`UnitEntity#tick` runs only on the client); what remains is the
  pose, the entity section move and the tracker's bookkeeping.
- **Best body at 500 moving: K, 0.12 µs, against ≤ 15.** Met, so D-01's default stays at 150 per
  player. Even the vanilla bodies stay inside it (4.9 µs).
- A vanilla body's cost is movement and collision, not AI: L, with no AI at all, costs what P
  costs. That is the cost ADR-0011 avoids, and the reason the core, not vanilla physics, moves
  units.
- The category check confirms `MISC`: a `CREATURE` body doubles the per-body cost (natural spawner
  counting, and more with Lithium).

## What it decides

- **ADR-0011:** S1's exit criterion is met; the status stays "Proposed - pending spike S10", with
  the numbers added there.
- **D-01:** the default population cap stays at 150 per player.
- **Budget B (ADR-0019):** at 1,000 kinematic bodies fighting, the whole server tick takes
  0.19 ms p50 and 0.35 ms p99 without players. The per-body share of B is tiny next to its
  envelope; S2 and the client-side S4 decide the rest of the proposal (T14).

## Raw data

[data/S1-vanilla.csv](data/S1-vanilla.csv), [data/S1-lithium.csv](data/S1-lithium.csv),
[data/S1-env-vanilla.json](data/S1-env-vanilla.json),
[data/S1-env-lithium.json](data/S1-env-lithium.json),
[data/S1-jfr-attribution.txt](data/S1-jfr-attribution.txt). The JFR recording (4 MB) is left in
`mod/build/spike/out/`, not committed.

## Deviations and follow-ups

- **Ticking tickets** (spec §1.6 D row 60). The spec force-loads the arena with `ticking=false`,
  but such a ticket does not make a chunk entity-ticking without a player near (the T7b review's
  engine fact): every body would freeze, and K bodies would be retired by `KinematicDriver`. The
  arena uses ticking tickets.
- **Warm-up pass.** The first run measured the early K rows on a cold JIT (`k_200_idle` slower
  than `k_500_idle`); an unrecorded pass of the three bodies now runs first.
- **MAX_HEALTH 1,000,000** is clamped by vanilla to 1,024; P's fights never end within a
  configuration either way.
- **No players.** Tracking and its packets cost nothing here; S9 (and S4 on the client) price
  them. "Time outside MSPT" is zero for the same reason.
- **Lithium's baseline** rose by about 20 µs over its run; a repeat would show whether that is
  JIT, GC or Lithium's own caches.
