# perf-validity-b on dev/o-e-lytvynskyi/perf-validity-b

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/perf-validity-b · **Work:** perf-validity-b · **Base:** main @ 134271f · **Claimed:** 2026-10-30 · **Status:** in progress

**Note:** issue #22 part B on the owner's word, 2026-10-30 («можно начинать прогоны и заканчивать эту задачу … имитируй какую-то деятельность — уровня работы в хроме, дискорд, другие окна vscode, телеграм»); follows perf-validity (#41); claim and delivery in one pull request on that word.

## Now

- **Work:** adopt ADR-0026's measured mean limit in the perf gates, judged over at least 60 s; foreign-load telemetry
  and the invalid-environment verdict for JMH (`jmhCompare`); calibration round 5 on the Mac in three conditions
  (quiet, one CPU busy, a simulated desktop).
- **In progress:** calibration round 5.
- **Merged from main last:** 134271f (2026-10-30).

## Depends on unfinished work

- None.

## Log

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

- None yet.

## Decisions taken

- The JMH profiler reads the JDK's own `OperatingSystemMXBean.getCpuLoad()` (the machine's load since its previous
  call) instead of the Mach/`/proc` counters of the GameTest sampler, so core's JMH needs no JNA. Undo: share
  `CpuCounters` with core.
- An unavailable preflight does not gate a run (the window's own load still does). Undo: `ForeignLoad.assess`.

## Pitfalls

## Review
