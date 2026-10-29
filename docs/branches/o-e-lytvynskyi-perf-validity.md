# perf-validity on dev/o-e-lytvynskyi/perf-validity

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/perf-validity · **Work:** perf-validity · **Base:** main @ 2075428 · **Claimed:** 2026-10-29 · **Status:** ready for review

**Note:** issue #22, direction approved by the owner 2026-10-29: per-second foreign-load telemetry, preflight, `invalid environment` verdict, ADR; QA-07 thresholds change only after the Mac calibration and the owner's approval of the numbers

## Now

- **Work:** issue [#22](https://github.com/o-e-lytvynskyi/strategos/issues/22); the owner approved the direction on 2026-10-29 (recorded in the issue).
- **Part A:** per-second foreign-load sampling with p95 and peak, a 60-second quiet preflight, an `invalid environment` verdict kept apart from budget failures (missing counters are invalid, never zero), report fields (telemetry source, logical CPUs, reasons), deterministic tests of the arithmetic and the boundaries, an ADR. The gate stays at QA-07's 25 % mean; the new figures are recorded, not enforced.
- **Part B:** calibration on the owner's Mac (quiet, one busy core, typical desktop load) with a fixed protocol; the proposed limits (`min(10 %, 50 %/N)` mean, `min(20 %, 100 %/N)` p95) or the measured alternative go to the owner; the gate switches only on the owner's approval of the numbers.
- **In progress:** part B, the calibration on the owner's Mac (ADR-0026 "Calibration").
- **Last finished task:** part A, the telemetry and ADR-0026 (2026-10-29).
- **Merged from main last:** 2075428 (2026-10-29).

## Depends on unfinished work

- None. Windows and Linux calibration are optional evidence (Linux by the `perf` label, Windows by a Windows developer), not prerequisites.

## Log

### 2026-10-29 — round 3 and the merged short intervals

- Round 3 (a) and (b) on e8d84d7, 90 min each, awake; numbers and the recommendation in ADR-0026.
  The mean limit separates the conditions (3.45 % against 9.35 %); p95 on short windows is one burst.
- Fixed: readings milliseconds apart (no counter movement) made 7 windows unavailable; intervals under
  0.5 s are now merged (`ForeignLoad.MIN_INTERVAL_NANOS`), a test pins it (14 in `ForeignLoadTest`).
  `verifyFast` green. Round 4 checks the fix.

### 2026-10-29 — round 2 lost to sleep; sleep detection

- Round 2 (a) on 9e270aa ran 3.5 h: the Mac entered idle sleep at 06:55 (`pmset -g log`; the
  `caffeinate` that kept round 1 awake had exited at 06:05). Stopped before (b); its results are
  discarded, not used.
- `PerfVerdicts.Window` now marks a window `invalid` ("the machine slept about N s") when the wall
  clock ran more than 2 s past `nanoTime` — sleep stops both `nanoTime` and the Mach ticks, so it was
  invisible. Calibration batches run under `caffeinate -i`. A pitfall in docs/kb/perf-tick-spacing.md.
  `verifyFast` green.

### 2026-10-29 — calibration round 1 and two harness fixes

- (a) quiet and (b) one busy CPU ran unattended on e1896f6, 90 and 89 minutes; results in ADR-0026
  "Round 1". The 25 % rule accepted every (b) window; the proposed mean limit separates (a) and (b);
  the proposed p95 limit rejected two quiet results.
- Fixed: a real 60 s preflight (the sampler reads while `RegisterGameTestsEvent`'s handler waits on the
  main thread; verified on the sources: posted from `Main` before the server thread, no watchdog on
  `GameTestServer`); loads judged over at least 10 s (a 46 ms window read 56 % in (b)).
  `verifyFast` green. Round 2 reruns (a) and (b) on the fixed commit.

### 2026-10-29 — the flaky L1 timing test (taken into this claim)

- Reported to the owner: `ProtectedBlockIndexTest.aLookupCostsTheSameAtAnySize` (a `nanoTime` ratio of
  lookups in 64 and 4 sections, limit 3) failed in a parallel `verify` for lev1pes (269.1 / 54.1 ns =
  4.98) and blocks the hand-in of M8 T5–T6; alone it passes.
- L1 now checks the structure instead: `aLookupConsultsOneBoundedSectionAtAnySize` — at 4 and 64 full
  sections every lookup consults one section map of 4,096 positions (`ProtectedBlockIndex.sectionSize`,
  package-private) and finds its owner. The time moved to the perf batch,
  `PerfTests.protectedLookupCost` (batch `perf_protected`, report `protected_lookup_ratio`): the same
  probes and median, ratio limit 3 unchanged, asserted only when the window is valid. A pitfall in
  docs/kb/perf-tick-spacing.md.
- Searched every `*/src/test` and `testFixtures` for wall-clock timing: no other assertion (the
  `ClockIndependenceTest` clock is injected; `DeterminismFixtures` are ArchUnit fixtures). In
  gametests outside the perf batch: `CameraTests` checks coverage within 3,000 ms of wall time, and
  the smoke driver has a 180 s hang guard — see "For the owner".
- Gates: `verifyFast` green; `:mod:test --tests ProtectedBlockIndexTest` 2 / 2.

### 2026-10-29 — part A: telemetry

- `ForeignLoad` (core testFixtures): exact window mean from cumulative readings, nearest-rank p95 and
  peak of one-second intervals, the proposed limits `min(10 %, 50 %/N)` and `min(20 %, 100 %/N)`,
  `assess` → gate verdict (25 % mean, unchanged; unavailable counters are invalid) and the proposed
  verdict with reasons. 13 tests in `ForeignLoadTest`.
- `CpuCounters` (gametest): cumulative counters on Windows (existing), macOS (Mach `host_statistics`
  HOST_CPU_LOAD_INFO, 100 Hz — checked on this Mac: 99.2 ticks/s/CPU on 12 CPUs; 32-bit ticks widened
  by unsigned deltas) and Linux (`/proc/stat`, `/proc/self/stat`); anything else is `Unavailable`.
- `ForeignLoadSampler`: one daemon thread reads every second from the perf batch's registration, so
  the readings before the first window are the preflight. `PerfVerdicts.Window` measures from its
  readings; every result JSON gains `foreignLoad` (source, CPUs, intervals, mean, p95, peak, limits,
  `proposedValid`, reasons, preflight). The Gradle guard lists each invalid test's reasons.
- ADR-0026 (proposed): attribution before a fix, the telemetry, the proposed limits and the
  calibration protocol.
- Gates: `:core:test`, `:mod:compileGametestJava`, `verifyFast` green. Built by a delegated
  `implementer` (Sonnet) from the orchestrator's brief; diff read in full; the Mach wrap fixed here.

### 2026-10-29 — claim

- **perf-validity · claim** — assessment: the L6 harness (`PerfTests`, `PerfVerdicts`, the guard in `mod/build.gradle`), QA-07 and an ADR; no milestone dependency; overlaps no active claim.

## For the owner

1. `CameraTests` (an L3 gametest in `verify`) asserts that coverage after a camera jump takes at most
   3,000 ms of wall time. Generous, but wall time again without a load guard; the same check in ticks
   would be load-proof. Not changed here (it is a card criterion); say if it should move to ticks.
2. fix-main recorded the protected-lookup ratio over 3 even on a quiet Windows machine (75.9 / 24.3 ns
   = 3.13). Moving it to the perf batch makes such a result attributable, not green: if the perf
   batch shows it valid and over 3, that is a finding about the index on that machine.

## Decisions taken

- 2026-10-29 · The flaky `ProtectedBlockIndexTest` timing (owner's request: «взять в свою работу или
  завести отдельный fix-…») is taken into this claim, not a new one — same subject (#22), and a new
  claim would wait for a merge — undo: move the two commits to a `fix-…` claim.
- 2026-10-29 · The moved ratio check is enforced in the perf batch when the window is valid, as the
  L1 test enforced it — it is not a new gate, so QA-07's five-run rule does not apply — undo: make it
  report-only.

- 2026-10-29 · Unavailable counters now make a run `invalid` (before: read as 0 on macOS/Linux) — the
  owner-approved direction of #22; a broken counter must not look like a quiet machine — undo: return
  `fixed(0)` instead of `unavailable` in `PerfVerdicts.Window.measure`.
- 2026-10-29 · The preflight is the sampler's readings from the batch's registration to the first
  window, not a separate 60 s wait — no extra wall time; its length is recorded and the proposed
  rule asks for 60 s — undo: add a wait before the first window.

## Pitfalls

## Review
