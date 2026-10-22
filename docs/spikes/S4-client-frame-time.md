# S4: client frame time with many units

*Prepared with M0 T16 and run on 2026-10-22, in M0's client batch after the owner's consent
([D-22](../roadmap/M0-foundation.md#d-22--client-run-l4-consent-policy)). Spec:
[M0 §8.6](../milestones/M0-foundation.md#86-s3-and-s4-prepared-pending-consent-t16) and §7.7.*

## Question and exit criterion

What does drawing many unit bodies cost the client's frame? PERF-04.1.

Exit: ≤ 6 ms of the frame for 200 armoured units with Sodium, and a client tick ≤ 1 ms, or the
report names the level of detail needed. Above 6 ms D-01's default cap drops from 150 to 100; the
numbers also feed D-04 and budget F of ADR-0019 (PERF-04.2). M0 has no equipment layers yet (they
arrive with ART-01), so M0 measures bare humanoids and says so (§1.6 row 34); the armoured pass
runs with ART-01 in M3.

## Setup

- The Mac: Apple M4 Pro, macOS 27.0.1, Temurin 21.0.12.1, NeoForge 21.1.264, GL renderer
  "Apple M4 Pro"; the window 1280 × 720 on the main display, an external monitor at scale 1 and
  170 Hz. 2026-10-22, on the M0 tree after commit `68fd08c` with the harness changes of commit
  `a209a1c`.
- The `s4_units` scenario of the L4 harness ([S3](S3-client-scenario-harness.md)), with S3's
  pinned scene (fixed anchor, noon, no chat, held camera): `strategos.s4.count` production unit
  bodies, standing, in a 64 × 64 match at the anchor; the camera 30 blocks up and 24.5 back at 50°
  pitch, so every unit is on screen; render distance `strategos.s4.renderDistance`; the frame
  limit off (260, "unlimited"), vsync off.
- Two profiles: vanilla, and `-Pcompat`, the client set most players run (Sodium 0.8.13, Iris
  1.8.14-beta.1 with no shader pack, Lithium, ModernFix, FerriteCore, Entity Culling,
  ImmediatelyFast) in the run's `mods` folder.
- Commands: `./gradlew :mod:runScenario -Pscenario=s4_units -Ps4.count=<n>
  -Ps4.renderDistance=<d> [-Pcompat]`, for n = 200, 500 and 1,000 and d = 16 and 24: twelve runs,
  19–34 s each.

## Method

The scenario waits at least 100 ticks and until every section is drawn, times 600 frames with no
units, spawns them, waits until every body exists and 40 more ticks, takes one PNG, and times 600
more frames. Three timers:
- **CPU frame:** `RenderFrameEvent.Pre` to `Post`, which wrap only `GameRenderer#render`, the
  work of submitting the frame;
- **frame interval:** `Pre` to the next `Pre`, the whole frame including the buffer swap and the
  wait for the GPU; the units' cost is its p50 with units less its p50 without;
- **client tick:** `ClientTickEvent.Pre` to `Post`, with the units.

## Results

Times in ms ([data/S4-frame-time.csv](data/S4-frame-time.csv), which also has max, p99 of the
CPU frame and both entity shares):

| Profile | Units | RD | CPU frame p50, none → units | Frame interval p50, none → units | Interval p99 | Units' cost | µs per unit | Client tick p50 / p99 |
|---|---|---|---|---|---|---|---|---|
| vanilla | 200 | 16 | 0.27 → 3.65 | 0.48 → 4.95 | 7.42 | **4.47** | 22.4 | 0.91 / 1.30 |
| vanilla | 200 | 24 | 0.27 → 3.69 | 0.47 → 5.16 | 7.12 | 4.69 | 23.4 | 0.85 / 1.22 |
| vanilla | 500 | 16 | 0.27 → 9.01 | 0.47 → 9.86 | 13.41 | 9.38 | 18.8 | 1.07 / 1.29 |
| vanilla | 500 | 24 | 0.26 → 9.11 | 0.47 → 10.00 | 13.24 | 9.54 | 19.1 | 0.98 / 1.21 |
| vanilla | 1,000 | 16 | 0.25 → 19.79 | 0.45 → 20.86 | 24.61 | 20.41 | 20.4 | 1.20 / 1.40 |
| vanilla | 1,000 | 24 | 0.27 → 19.87 | 0.48 → 21.12 | 24.91 | 20.65 | 20.6 | 1.17 / 1.40 |
| compat set | 200 | 16 | 0.16 → 0.82 | 0.37 → 1.06 | 3.93 | **0.69** | 3.4 | **0.72 / 0.91** |
| compat set | 200 | 24 | 0.15 → 0.84 | 0.40 → 1.08 | 3.92 | 0.68 | 3.4 | 0.71 / 0.85 |
| compat set | 500 | 16 | 0.15 → 2.01 | 0.38 → 2.58 | 5.67 | 2.20 | 4.4 | 0.89 / 1.17 |
| compat set | 500 | 24 | 0.14 → 2.03 | 0.37 → 2.57 | 5.96 | 2.20 | 4.4 | 0.86 / 1.14 |
| compat set | 1,000 | 16 | 0.14 → 3.89 | 0.37 → 5.43 | 7.94 | 5.06 | 5.1 | 1.08 / 1.54 |
| compat set | 1,000 | 24 | 0.14 → 3.90 | 0.39 → 5.33 | 8.10 | 4.93 | 4.9 | 1.12 / 1.47 |

- The units are nearly the whole frame: 0.93–0.99 of the CPU frame with vanilla, 0.81–0.96 with
  the compat set.
- Render distance 24 costs the same as 16 here: the world is flat and the camera looks down, so
  terrain is cheap; real maps price terrain in MAP-01 and PERF-14.

## Analysis

- **Exit, bare humanoids: met.** 200 units cost 0.69 ms of the frame with the compat set and
  4.47 ms without it, against 6 ms; the client tick is 0.72 ms p50 and 0.91 ms p99 with the compat
  set, against 1 ms. No level of detail is needed for bare humanoids.
- **The compat set draws a unit about six times cheaper:** 3.4–5 µs against 19–23 µs. Vanilla
  stays inside budget F at 200 units (frame p50 4.95 ms, p99 7.42 ms), but its client tick passes
  1 ms at p99 (1.30 ms).
- **The client tick grows with the count:** above 1 ms p50 at 1,000 units with either profile and
  at 500 with vanilla. M0's units stand still, so this is the bodies' own client tick and their
  interpolation; moving units and our client systems (M1 onwards) add to it.
- **Budget F at 200 units, render distance 16:** frame p50 1.06 / 4.95 ms (compat / vanilla)
  against 16.7; p99 3.93 / 7.42 against 25; units 0.69 / 4.47 against 6; client tick p50 0.72 /
  0.91 against 1. "Our drawing" has nothing to measure yet: M0 draws no HUD or overlay of its own.

## What it decides

- **D-01:** the default population cap stays at 150 per player (S1 kept it on the server side;
  here 200 bodies stay far inside 6 ms).
- **D-04:** vanilla humanoids are cheap enough to draw by the hundred; the armour and held-item
  layers are what is left to price, in ART-01's pass (M3), against the same 6 ms.
- **Budget F (ADR-0019):** unchanged, now measured for bare humanoids; it stays provisional for
  armoured ones until ART-01's pass.

## Raw data

[data/S4-frame-time.csv](data/S4-frame-time.csv), one row per run with every field of its
`result.json`. The PNGs are not committed.

## Deviations and follow-ups

- **Bare humanoids only,** as planned: the armoured pass runs with ART-01 in M3.
- **The first runs crashed with the compat set:** Sodium on the classpath loses its JarJar
  libraries (FML does not unpack a classpath mod's jar-in-jar), so `-Pcompat` now copies the
  client set into the run's `mods` folder (commit `a209a1c`); the server profile
  `-Pcompat.server` stays on the classpath.
- **The first timings were capped:** the frame limit sat at 60 and only the CPU frame was timed;
  the scenario now lifts the limit and times the frame interval too, and waits for the terrain to
  finish drawing before the baseline.
- **GPU time** is inside the frame interval, not separate; a GPU timer query would split it, and
  nothing here needs it.
- **One run per configuration.** The twelve form a matrix; render distance 16 and 24 agree within
  0.2 ms everywhere, which is the repeat.
