# perf-validity-b on dev/o-e-lytvynskyi/perf-validity-b

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/perf-validity-b · **Work:** perf-validity-b · **Base:** main @ 134271f · **Claimed:** 2026-10-30 · **Status:** ready for review

**Note:** issue #22 part B on the owner's word, 2026-10-30 («можно начинать прогоны и заканчивать эту задачу … имитируй какую-то деятельность — уровня работы в хроме, дискорд, другие окна vscode, телеграм»); follows perf-validity (#41); claim and delivery in one pull request on that word.

## Now

- **Work:** adopt ADR-0026's measured mean limit in the perf gates, judged over at least 60 s; foreign-load telemetry
  and the invalid-environment verdict for JMH (`jmhCompare`); calibration round 5 on the Mac in three conditions
  (quiet, one CPU busy, a simulated desktop).
- **In progress:** none; hand-in pull request open.
- **Merged from main last:** 134271f (2026-10-30).

## Depends on unfinished work

- None.

## Log

### 2026-10-30 — calibration round 5; the limit set at 0.9/N

- Round 5 on e3682ff, (a) quiet, (b) one CPU busy, (c) `tools/desktop_load.py`; each the GameTest perf batch and
  `:core:jmh` + `jmhCompare`. (b): all 20 GameTests and all 27 benchmarks invalid (9.10–11.03 %); (a): all valid
  (≤ 5.91 %); (c): GameTests valid (4.25–5.14 %), one benchmark of 27 at 6.44 %, above `0.75 / N`.
- The limit becomes `min(10 %, 0.9 / N)` (7.5 % on 12): (c) valid with 1 point to spare, (b) rejected with 1.6.
  ADR-0026 Accepted, with round 5. `verifyFast` (1,628 core tests), `spotlessCheck`, `jmhSelftest` green.
- My calibration script logged `exit=0` for the failed (b) runs: `$(date)` in the same `echo` reset `$?`. The
  gates themselves failed as they should (the logs say so).

### 2026-10-30 — the gate adopts the limit; JMH telemetry; a desktop-load simulator

- `ForeignLoad.meanLimit(cpus)` = `min(10 %, 0.75/N)` replaces QA-07's 25 % in the GameTest perf gate; the window is
  judged over at least its last 60 s; a preflight above the limit invalidates the run. p95 stays report-only.
- JMH: `ForeignLoadProfiler` (`-prof`, next to `gc`) records `·foreign.mean/peak/limit` per benchmark;
  `jmhCompare` reports a benchmark above its limit, or without telemetry, as `invalid environment` instead of a
  pass or a regression. `jmhSelftest` pins both.
- `tools/desktop_load.py`: condition (c), a typical desktop (browser with video, chat, messenger, two editors,
  the window server), about 0.3–0.45 of one core with bursts, seeded.
- `verifyFast`, `:core:test`, `:core:jmhSelftest` green.

## For the owner

1. **The limit is yours to veto:** `min(10 %, 0.9 / N)`, 7.5 % on the Mac (ADR-0026 round 5). A typical desktop
   (simulated: browser with video, chat, messenger, two editors) reads 4–6.5 %; one busy CPU at least 9.10 %.
2. **Follow-up, not in this claim:** `mounted_cost` measures its foot and mounted phases one after the other, so a
   load that changes the CPU's speed between them skews the ratio (1.77 in (c): the foot phase ran faster, mounted
   as usual). Interleave the phases, as `idle_300` does since M5 T18.

## Decisions taken

- The mean limit is `0.9 / N`, not the `0.75 / N` of round 4's recommendation: with it, a simulated desktop failed
  one JMH benchmark of 27 (6.44 %); `0.9 / N` stays 1.6 points under the lowest busy-CPU reading. Undo:
  `ForeignLoad.meanLimit`.

- The JMH profiler reads the JDK's own `OperatingSystemMXBean.getCpuLoad()` (the machine's load since its previous
  call) instead of the Mach/`/proc` counters of the GameTest sampler, so core's JMH needs no JNA. Undo: share
  `CpuCounters` with core.
- An unavailable preflight does not gate a run (the window's own load still does). Undo: `ForeignLoad.assess`.

## Pitfalls

## Review
