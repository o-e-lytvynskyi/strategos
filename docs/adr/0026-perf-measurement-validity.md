# ADR-0026: Performance-measurement validity and attribution

Date: 2026-10-29

## Status

Proposed; the direction approved by the owner on 2026-10-29 ([issue #22](https://github.com/o-e-lytvynskyi/strategos/issues/22)).
Part 1 (attribution) and part 2 (telemetry) apply now. Part 3 (the limits) stays report-only until a calibration on
the owner's Mac is recorded below and the owner approves its numbers; then it amends QA-07's validity rule.

## Context

- QA-07 marks a perf run `invalid` when the mean foreign CPU load over its window exceeds 25 % of the machine. A
  whole-machine mean hides real contention: one fully busy logical CPU is 16.7 % of six and 3.1 % of 32, both
  valid; a long mean also hides short bursts, which reach a p99 first. fix-main's final commander window passed at
  24.948 %.
- On macOS and Linux the harness read the MXBean's "recent load" for the machine and for the process, two
  differently windowed estimates, and turned an unavailable reading into 0 — a broken counter looked like a quiet
  machine. Windows already used cumulative counters over the same interval.
- A red budget has been read as a mod regression and optimised before its cause was known.

## Decision

1. **Attribution comes before a fix.** A perf result is one of: a valid pass; a budget breach with an unknown
   cause; an invalid measurement (environment); a harness defect; a confirmed runtime regression. A breach is
   reported at once but called a regression only with a cause or a reproduction under controlled conditions; a
   runtime optimisation is chosen only with evidence tying the cost to that code (a profile or a causal
   experiment against a valid unchanged-runtime control). Every outcome is kept; no retries until green, no
   raised budgets, no skipped gate. An invalid or interrupted run does not count as a failed fix attempt.
2. **Telemetry.** Foreign load is measured from cumulative counters on every platform — Windows
   `GetSystemTimes`/`GetProcessTimes`, macOS `host_statistics(HOST_CPU_LOAD_INFO)` (100 Hz ticks) with the
   process's CPU time, Linux `/proc/stat` and `/proc/self/stat` — sampled once a second by one daemon thread from
   the start of the perf batch. The test JVM is excluded. Each result records the source, logical CPUs, interval
   count, the window's exact mean, the p95 and peak of the one-second intervals, a preflight (the readings before
   the first window) and the reasons a run is or would be invalid. **Unavailable counters make a run invalid**,
   never zero.
3. **Limits (proposed, report-only until calibrated).** Mean foreign load ≤ `min(10 %, 50 % / N)` and the p95
   of one-second intervals ≤ `min(20 %, 100 % / N)` for N logical CPUs, after a preflight of at least 60 s. Until
   approved, results carry `proposedValid` and its reasons; the gate stays QA-07's 25 % mean.

## Calibration (part 3's evidence)

A fixed protocol on the owner's Mac, each condition one full `runGameTestPerf` batch on one commit: (a) a quiet
machine; (b) one logical CPU kept busy by a synthetic load; (c) a typical desktop load (browser and IDE open, no
builds). Recorded per condition: every test's mean, p95, peak, preflight and verdict under both rules. The
numbers adopted are those that accept (a), reject (b), and are stated for (c); if the proposed limits do not, the
alternative and its evidence go to the owner. Windows and Linux (the `perf` label) results are added when
available; they do not replace the Mac's.

### Round 1 — 2026-10-29, commit e1896f6 (MacBook, 12 logical CPUs; limits for N = 12: mean 4.17 %, p95 8.33 %)

One full batch each, unattended at night; 21 results with telemetry per condition.

| | (a) quiet | (b) one CPU busy (`yes`, theory +8.33 %) |
|---|---|---|
| Mean, windows ≥ 1 s | 1.30–3.22 % | 9.53–10.75 % |
| p95 of 1 s intervals | 1.70–9.58 % (`economy_stress` 9.58, `idle_300` 9.55) | 10.40–15.90 % |
| Peak | up to 25.3 % | up to 34.4 % |
| Verdict under QA-07 (25 % mean) | all valid | all valid except one artefact (below) |
| `protected_lookup_ratio` | 0.88 | 1.01 |

Findings:
- **The 25 % rule accepts a fully busy core**: every (b) window was valid under QA-07 — the case #22 describes.
- **The proposed mean limit separates them**: (a)'s highest mean 3.22 % < 4.17 % < (b)'s lowest 9.53 %.
- **The proposed p95 limit (1/N) is too tight**: two quiet results exceeded 8.33 %, and (b)'s lowest p95 (10.40 %)
  is close above it. A burst limit needs a burst condition to calibrate; until then p95 stays report-only.
- **Two harness defects, fixed before round 2**: the preflight was 2 s (the first window opened 2 s after the
  sampler), so every proposed verdict failed on it — now a 60 s wait before the server thread starts
  (`strategos.perf.preflightSeconds`; the event fires on the main thread, no watchdog on the game test server);
  and windows of 46–360 ms gave meaningless loads (`block_writer_cost` read 56.36 % in (b), the batch's only
  invalid) because Mach's 100 Hz ticks cannot be compared with the exact process time over a few ticks — the load
  is now judged over at least the last 10 s of readings, and the report keeps `windowSeconds` beside `seconds`.

## Consequences

- A run on a busy machine ends `invalid` with a reason, not as a budget failure or a call to optimise.
- Previously accepted runs are not reinterpreted.
- Rejected alternative: tightening the 25 % mean alone — still blind to one busy core on many-core machines and to
  bursts.
