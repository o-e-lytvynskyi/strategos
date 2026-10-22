# S4: client frame time with many units — plan, pending consent

*Prepared with M0 T16 on 2026-10-22; not run. It opens a game window, so it waits for the owner's
consent to M0's client batch ([D-22](../roadmap/M0-foundation.md#d-22--client-run-l4-consent-policy)).
Spec: [M0 §8.6](../milestones/M0-foundation.md#86-s3-and-s4-prepared-pending-consent-t16) and §7.7.*

## Question and exit criterion

What does drawing many unit bodies cost the client's frame? PERF-04.1.

Exit: ≤ 6 ms of the frame for 200 armoured units with Sodium, and a client tick ≤ 1 ms, or the
report names the level of detail needed. Above 6 ms D-01's default cap drops from 150 to 100; the
numbers also feed D-04 and budget F of ADR-0019 (PERF-04.2). M0 has no equipment layers yet (they
arrive with ART-01), so M0 measures bare humanoids and says so (§1.6 row 34); the armoured pass
runs with ART-01 in M3.

## Setup (as built)

The `s4_units` scenario of the L4 harness ([S3](S3-client-scenario-harness.md)): `strategos.s4.count`
units in a 64 × 64 match at the player, a spectator camera 30 blocks up at 50° pitch, render
distance `strategos.s4.renderDistance`. It times 600 frames between `RenderFrameEvent.Pre` and
`Post` with no units, then spawns them, waits for every body and 40 more ticks, takes one PNG and
times 600 more frames, and the client tick between `ClientTickEvent.Pre` and `Post`.
`result.json` holds the frame p50, p99 and max, the client tick p50 and p99, and the units' share
of the frame, (p50 with units − p50 without) ÷ p50 with units.

## Method (to run)

`./gradlew :mod:runScenario -Pscenario=s4_units -Ps4.count=<n> -Ps4.renderDistance=<d>`, for
n = 200, 500 and 1,000 and d = 16 and 24, each without and with `-Pcompat` (Sodium and the client
set): twelve runs.

## Results

Pending the owner's consent.

## What it will decide

D-01's default (with S1, which kept it at 150 on the server side), D-04, and budget F.
