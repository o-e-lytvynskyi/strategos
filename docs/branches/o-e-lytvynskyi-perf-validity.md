# perf-validity on dev/o-e-lytvynskyi/perf-validity

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/perf-validity · **Work:** perf-validity · **Base:** main @ 2075428 · **Claimed:** 2026-10-29 · **Status:** in progress

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

- None.

## Decisions taken

- 2026-10-29 · Unavailable counters now make a run `invalid` (before: read as 0 on macOS/Linux) — the
  owner-approved direction of #22; a broken counter must not look like a quiet machine — undo: return
  `fixed(0)` instead of `unavailable` in `PerfVerdicts.Window.measure`.
- 2026-10-29 · The preflight is the sampler's readings from the batch's registration to the first
  window, not a separate 60 s wait — no extra wall time; its length is recorded and the proposed
  rule asks for 60 s — undo: add a wait before the first window.

## Pitfalls

## Review
