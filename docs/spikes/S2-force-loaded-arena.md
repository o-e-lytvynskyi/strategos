# S2: force-loaded arena cost

*Written with M0 T14 on 2026-10-22. Spec: [M0 §8.5](../milestones/M0-foundation.md#85-s2-force-loaded-arena-cost-t14--sizes-of-adr-0010-budget-c-baseline);
the runner is §8.3.*

## Question and exit criterion

What does a force-loaded arena cost a server with nobody online, at the three sizes of
[ADR-0010](../adr/0010-match-model-and-lifecycle.md) (256², 384² and 512²): MSPT idle and with
600 bodies, heap, autosave time and import time? Do `TicketController` tickets with
`ticking=false` keep the arena's entities ticking? The answers size the arenas
([D-17](../roadmap/M1-movement.md#d-17--arena-size)) and give budget C its vanilla baseline.

Exit (04 §19.2): **≤ 4 ms MSPT idle at 576 chunks**; **autosave ≤ 100 ms**; entities keep ticking
for 10 game minutes with no player online (PERF-03.2).

## Setup

- Apple M4 Pro, 12 cores; "Mac OS X 27.0.1" on aarch64 as the JVM reports it; Temurin 21.0.12.1,
  heap pinned at 4 GB; NeoForge 21.1.264; the dedicated dev server on a fresh flat world, no
  players.
- 2026-10-22, on the M0 T13 tree (commit `095ffc5`) with T14 uncommitted.
- Commands: `./gradlew :mod:runSpike -Pspike=s2` (9 s), then with `-Pspike.jfr`, the recording
  read as in S1.

## Method

`S2ArenaCost` runs on the spike server of §8.3 (arena rules, back-to-back ticks). For each size,
at (8192, floor, 8192):
1. every chunk force-loaded with `forceChunk(level, uuid, x, z, true, false)`, timed;
2. 100 settle ticks, then 200 warm-up and 600 measured ticks of MSPT, idle;
3. heap after two `System.gc()` calls;
4. three timed `server.saveEverything(true, false, true)` calls with saving on;
5. 600 production bodies of one saved match (300 a faction, a 96 × 96 match space at the arena's
   centre, walking corner to corner and re-ordered every 120 ticks): 200 + 600 ticks of MSPT, then
   three autosaves again, the match's blob included.

At 576 chunks also:
6. keep-alive: a probe body (`SpikePhysicsBody`, idle) through 12,000 ticks with nobody online;
   its own `tickCount` must advance by at least 11,900;
7. for the record, the idle measurement again after swapping every ticket for a `ticking=true`
   one, once under the arena's rules and once under the defaults (`randomTickSpeed` 3,
   `doMobSpawning` true).

## Results

MSPT in ms ([data/S2-arena.csv](data/S2-arena.csv)):

| Arena | Phase | Tickets | MSPT p50 | p95 | p99 | max | Heap MB | Import ms | Autosave median / max ms |
|---|---|---|---|---|---|---|---|---|---|
| 256², 256 chunks | idle | `ticking=false` | 0.065 | 0.075 | 0.098 | 1.240 | 222 | 242 | 8.3 / 104.4 |
| | 600 bodies | `ticking=false` | 0.154 | 0.347 | 0.402 | 0.608 | 225 | | 6.9 / 10.8 |
| 384², 576 chunks | idle | `ticking=false` | **0.056** | 0.076 | 0.111 | 0.147 | 243 | 187 | **5.3** / 54.9 |
| | 600 bodies | `ticking=false` | 0.126 | 0.322 | 0.371 | 0.829 | 246 | | **6.6** / 6.8 |
| | idle, record | `ticking=true`, arena rules | 0.064 | 0.067 | 0.071 | 0.094 | | | |
| | idle, record | `ticking=true`, default rules | 0.187 | 0.312 | 0.363 | 0.666 | | | |
| 512², 1,024 chunks | idle | `ticking=false` | 0.103 | 0.178 | 0.189 | 0.291 | 268 | 195 | 7.0 / 36.6 |
| | 600 bodies | `ticking=false` | 0.170 | 0.224 | 0.346 | 0.414 | 272 | | 5.4 / 6.1 |

- Keep-alive at 576 chunks: the probe ticked **11,999 of 12,000** times with nobody online.
- The heap grows by about 0.06 MB per loaded flat chunk (222 MB at 256 chunks, 268 MB at 1,024).
- The first autosave of each size writes every freshly generated chunk (104 ms at 256 chunks, the
  only time above 100 ms); later ones take 5–11 ms.
- Attribution with JFR ([data/S2-jfr-attribution.txt](data/S2-jfr-attribution.txt), only 118
  server-thread samples, since the whole run takes seconds): `ServerChunkCache#tickChunks` 80 %,
  `ChunkMap#tick` 5 %, our `MatchRuntime#tick` 7 %, the entity loop 2 %,
  `NaturalSpawner.createState` 0 %.

## Analysis

- **Idle at 576 chunks: 0.056 ms p50 (0.11 ms p99), against ≤ 4 ms.** Met by a factor of 70.
  Even 1,024 chunks with 600 walking bodies stay under 0.35 ms p99.
- **Autosave: a median of 5.3 ms idle and 6.6 ms with the match's blob, against ≤ 100 ms.** Met;
  only the very first save of a fresh arena, which writes every new chunk, reaches 104 ms, and it
  happens once, when the arena is made.
- **Keep-alive:** `ticking=false` tickets keep the level and the arena's entities ticking with
  nobody online, as ADR-0010 says: every region ticket enters the ticking tracker
  (DistanceManager.java:197–202), and NeoForge counts its tickets in the empty-level check
  (ServerLevel.java:390). The 600 bodies also appeared and walked on `ticking=false` tickets.
- **`ticking=true`** costs nothing extra under the arena's rules (random ticks 0, no spawning),
  and about 0.13 ms more under the defaults: random ticks across the arena, which the arena rules
  exist to switch off.

## What it decides

- **ADR-0010:** arenas keep `ticking=false` tickets; the ticket mode is confirmed.
- **D-17:** the measured costs allow all three sizes; 384² for the 1 v 1 MVP stays as recommended,
  and 512² waits only for PERF-12's large-map gate, not for the server's arena cost.
- **Budget C (ADR-0019):** the vanilla baseline of a 384² arena is 0.06 ms idle and 0.37 ms p99
  with 600 bodies, against C's 23 ms p95. The revision proposal in ADR-0019 carries the numbers.
- **Budget G:** autosave stays far inside 100 ms at every size.
- The 384² import time (187 ms here, of flat chunks) moves to MAP-01, as planned.

## Raw data

[data/S2-arena.csv](data/S2-arena.csv), [data/S2-env.json](data/S2-env.json),
[data/S2-jfr-attribution.txt](data/S2-jfr-attribution.txt).

## Deviations and follow-ups

- **The keep-alive probe** is a `SpikePhysicsBody`, not a unit body: `UnitEntity` does nothing on
  the server's tick, so its `tickCount` never moves.
- **Import times after the first size** may reuse chunks the previous size had loaded (each size
  is released 100 ticks before the next), so only 256²'s 242 ms is a cold figure: about 1 ms per
  flat chunk. MAP-01 measures real map imports.
- **JFR** has few samples here; the attribution is an indication, not a measurement.
