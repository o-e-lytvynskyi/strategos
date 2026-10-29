# perf-validity on dev/o-e-lytvynskyi/perf-validity

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/perf-validity · **Work:** perf-validity · **Base:** main @ 2075428 · **Claimed:** 2026-10-29 · **Status:** claimed

**Note:** issue #22, direction approved by the owner 2026-10-29: per-second foreign-load telemetry, preflight, `invalid environment` verdict, ADR; QA-07 thresholds change only after the Mac calibration and the owner's approval of the numbers

## Now

- **Work:** issue [#22](https://github.com/o-e-lytvynskyi/strategos/issues/22); the owner approved the direction on 2026-10-29 (recorded in the issue).
- **Part A:** per-second foreign-load sampling with p95 and peak, a 60-second quiet preflight, an `invalid environment` verdict kept apart from budget failures (missing counters are invalid, never zero), report fields (telemetry source, logical CPUs, reasons), deterministic tests of the arithmetic and the boundaries, an ADR. The gate stays at QA-07's 25 % mean; the new figures are recorded, not enforced.
- **Part B:** calibration on the owner's Mac (quiet, one busy core, typical desktop load) with a fixed protocol; the proposed limits (`min(10 %, 50 %/N)` mean, `min(20 %, 100 %/N)` p95) or the measured alternative go to the owner; the gate switches only on the owner's approval of the numbers.
- **In progress:** none (waiting for this claim to merge).
- **Merged from main last:** 2075428 (2026-10-29).

## Depends on unfinished work

- None. Windows and Linux calibration are optional evidence (Linux by the `perf` label, Windows by a Windows developer), not prerequisites.

## Log

### 2026-10-29 — claim

- **perf-validity · claim** — assessment: the L6 harness (`PerfTests`, `PerfVerdicts`, the guard in `mod/build.gradle`), QA-07 and an ADR; no milestone dependency; overlaps no active claim.

## For the owner

- None.

## Decisions taken

## Pitfalls

## Review
