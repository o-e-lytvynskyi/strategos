# main-save-reload on dev/lev1pes/main-save-reload

**Developer:** lev1pes · **Branch:** dev/lev1pes/main-save-reload · **Work:** main-save-reload · **Base:** main @ b207e509a85a3303f515a716b04a87bc90ee2cb8 · **Claimed:** 2026-10-27 · **Status:** in progress

## Now

- **Milestone:** bounded follow-up fixes for three independently reproduced bugs in existing main: [#23](https://github.com/o-e-lytvynskyi/strategos/issues/23), [#24](https://github.com/o-e-lytvynskyi/strategos/issues/24), [#25](https://github.com/o-e-lytvynskyi/strategos/issues/25).
- **In progress:** WIP checkpoint: all three fixes implemented; functional gates green; performance verification unresolved after three failed comparisons. Assignment merged by PR #26.
- **Last finished task:** implementation and 1,936-test functional verification for #23, #24 and #25; schema-3 JSON/NBT fixtures pinned.
- **Next task:** finish the owner-requested cloud validation: inspect standard perf run 40594606609 and one paired same-runner JMH run against pinned bb800d0. Keep this claim assigned; do not hand in until the performance gate is resolved.
- **Merged from main last:** bb800d0 (2026-10-27), including the assignment merge (#26), fix-main (#21) and owner answers (#27).

## Depends on unfinished work

- None. All three defects reproduce on main b207e50. PR #19 registered the separate M5 claim (records only); this claim covers defects in existing functionality, not new M5 tasks or the separate M6 work.
- PR #21 is merged in the current base. This claim changes no performance thresholds, approved baselines or new M5 scope.

## Log

### 2026-10-27 — audit and assignment request

- Baseline: isolated, unmodified main `verifyFast` passed in 1m 57s (34 actionable tasks).
- **#23 (P2):** one isolated L3 GameTest ended a persistent match, verified its record was ENDED, saved/unloaded/restored it, and failed with `ended match restored as PAUSED`. Source tracing confirms that resume and command submission are then allowed; VictoryCheck already has an ended outcome and emits no new end event.
- **#24 (P1):** isolated L1 ContentSwap probe spawned a finished house and supplied a valid set omitting its template. The unsafe swap was accepted; building queries then threw ArrayIndexOutOfBoundsException through StatService.block -> BuildingLife.maxHp -> QueriesImpl.buildings. Both server and core reload guards check live units only. Real data-pack /reload was traced in source, not executed with a modified pack.
- **#25 (P1):** isolated L1 probe retained built-in indices and restored a saved custom audit:z_house against a new-session table with audit:a_house inserted before it. The restored identity was audit:a_house, without rejection. This models fresh custom-ID assignment using two valid compiled sets, not two live server processes. Production queues share numeric persistence and require follow-up inspection; their corruption was not independently reproduced.
- All issues are assigned to lev1pes. Diagnostic sources and logs remain local under the original checkout's ignored build/audit-tools; temporary edits were restored byte-for-byte. This claim includes no implementation or test changes.
- **Planned scope:** #25 stable building/production template identity with explicit legacy-save handling; #24 atomic validation of all in-use template references; #23 preserve terminal lifecycle and reject resume/commands after load. Do not silently reinterpret legacy indices, weaken gates, or change performance thresholds/baselines.
- **Validation plan:** failing core and persistence/reload GameTests before fixes; verifyFast per edit batch; verify before task commits. Run the additional slow/soak/JMH gates if a fix touches their required change types. Keep malformed/missing-template saves explicit and preserve existing fixtures or migrate with a documented policy.

### 2026-10-27 — implementation and regressions

- #23: permanent isolated L3 regression failed with `ended match restored as PAUSED`. Restore now preserves an ENDED record or a terminal saved outcome. Regressions also cover an older PAUSED record and rejection of resume/commands.
- #24: permanent L1 regression failed when a live building lost its template. One shared guard now checks units, buildings, every queued product and template-backed resource nodes before either server publication or core INGEST. L3 checks that a refused building reload preserves both set and id table.
- #25: permanent L1 regression failed by restoring a custom house as a newly inserted custom id. Snapshots now carry optional index-to-stable-id bindings; restore validates/remaps buildings and production queues before footprint/stat/production reads. Tests cover JSON and NBT, queue batches/progress, missing templates and legacy policy.
- Initial baseline verification was invalidated by overlapping Gradle invocations; its results are not evidence. Subsequent early edit checks exposed the core API boundary and forbidden hash collections, both corrected. Source edits during compilation also invalidated one compiler run; final gates run serially with separate logs and no source edits.
- Serial verifyFast passed in 2m 21s with no new compiler warnings. The first full run stopped at the solo-batch guard: the new building reload test shared a batch with an existing solo case. Give it its own batch; no guard is weakened.
- Diff review also exposed a downgrade hazard in the initial additive-schema proposal: old builds would ignore bindings. Write schema 3 and test the conservative 2-to-3 migration and rejection by schema-2 readers.
- Schema-3 JSON/NBT fixtures were generated once and pass their recorded hash checks (two civic centres, two queued workers, 16 bindings). Versions 1 and 2 fixtures remain unchanged. The current schema-3 `verifyFast` passes in 2m 18s.
- The full main verification stage passed in about 9m 10s with one Gradle worker (215 normal L3 tests); slow/soak are still running. Record the final aggregate and exact total before hand-in.
- Reuse the accepted full same-host JMH result from 2026-10-27 01:40 local as control: 21 benchmarks, SHA-256 `4658f73d256e5f99d47627a13070f3df5fabda4edd53692396ee387496210614`, identical to `build/fix-main-final-jmh.json` and the original checkout's full result. Between f25e2c9 and bb800d0 only docs changed; runtime, benchmark and build sources are identical. Freeze a copy under `build/save-reload-control-jmh.json`; run the changed candidate against it. This is diagnostic same-host evidence, not an approved replacement for the Mac baseline.
- Final functional aggregate: `verify verifySlow verifySoak` passed in 19m 16s; 1,936 tests, zero failures/errors/skips (L1 1,319; slow L1 3; soak 200; journal replays 73; L2 97; mod ArchUnit 12; normal L3 215; slow L3 13; smoke 4). Normal L3 passed in about 79s; slow L3 in 487.235s.
- First full candidate JMH against the historical accepted control: RED, 6m 51s. All 21 benchmarks and matching JVM/arguments are present; six scores exceed 1.15, maximum 1.238304 (`SnapshotCodecBench.encode500`). Unchanged NodeDiscovery/Idle/Crossing kernels also slow by 15–23%. Preserve the raw candidate as `build/save-reload-first-jmh.json` and its complete comparison summary; do not treat this as a passing or invalid run.
- Next diagnostic: one fresh full JMH measurement of unmodified bb800d0 in isolated detached worktree `build/save-reload-control`, under current conditions, to separate source changes from drift in the historical control. Do not rerun the candidate until green, pause applications, weaken thresholds or replace approved baselines.
- Fresh pristine control: 21 benchmarks, 6m 41s. The unchanged candidate still fails comparison: TemplateCompile, TerrainEdit and four Crossing cases, maximum candidate/control 1.330130. SnapshotCodec is instead below this fresh control. Every JMH class is byte-identical, all 54 navigation classes are byte-identical, and shared fixture classes differ only in the unused schema-3 fixture helper. JVM, arguments, benchmark configuration and parameter sets match. This is not a passing gate and no specific foreign-CPU cause is established.
- One bounded confirmatory candidate run is planned with same-window Windows system/Java CPU telemetry, using the same 1.15 gate and fresh control. Retain every earlier failure. No further candidate retry in this session if it remains red; document and hand off unresolved measurement validity instead of weakening checks. The diagnostic monitor is local under ignored build output and changes no production or CI gate.
- Confirmatory candidate: RED, two Crossing breaches, maximum 1.253488, 6m 33s. CPU telemetry: 395 windows, zero errors, foreign busy mean 11.035%, peak 68.010%, 64 windows above 25%. This does not establish a particular cause or a production regression. All raw results are retained.
- Stop under strategos-dev section 8 after three failed verification comparisons. No more perf retries in this session; no threshold or approved baseline changes; no applications paused/closed. The code is saved as WIP and no hand-in PR is opened.
- Full score table, controls, binary/workload checks, CPU context and raw hashes: [local JMH evidence](lev1pes-main-save-reload-perf.md).

### 2026-10-28 — cloud verification requested

- The owner requested cloud tests and continuation of the assigned work. Standard perf run 40594606609 targets f9f9846 and is still running. It checks JMH speed ratios and server budgets, but deliberately does not run jmhCompare.
- Add an opt-in paired-JMH mode to the existing workflow_dispatch route, without opening an unfinished hand-in PR. The control is the trusted pre-repair main bb800d0; both versions run on one Ubuntu runner with the same JDK, heap, forks and iterations. All 21 benchmark keys and configurations must match before the existing comparison runs.
- Workflow validation: YAML parse, all pinned action hashes, read-only token permissions, Bash syntax of every run block, embedded Python syntax and roadmap checks pass. Production/test sources and benchmark settings are unchanged, so the completed 1,936-test functional evidence is retained. No new local Gradle run is started.
- No new approved baseline, raised limit or skipped gate. Cloud outcome and artifacts must be inspected before declaring this claim ready.
- First paired workflow run 40595484377 stopped before measurement because the preparation step used jmhJar, which this plugin-free harness does not define. Inspect the actual source-set convention and replace that preparation with jmhClasses; no benchmark result was produced or discarded. Preserve the failed run and logs. This is a workflow repair, not another unchanged performance retry.

## For the owner

1. Cloud verification requested in the owner's session on 2026-10-28: standard [perf run 40594606609](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40594606609) is running. A manual paired-JMH mode runs pristine bb800d0 and this branch sequentially on one runner with the existing 15% and allocation gates; results remain pending. This is supplemental same-host evidence, not promotion of an approved baseline. Retain [all local failures](lev1pes-main-save-reload-perf.md).
2. Review the serial Windows `verify` duration (about 9m 10s versus the documented 5-minute budget): accept the platform time or decide separately which run moves to `verifySlow`. No run or gate was moved here.
3. Assignment merged by PR #26. This WIP branch is not ready for hand-in; independent maintainer review and human merge follow resolved verification.

## Decisions taken

- 2026-10-28 · On the owner's explicit cloud-validation request, add opt-in paired JMH to the existing manual perf workflow. Pin the control to pristine bb800d0; compile both jars before measuring; use one runner, sequential measurements, identical configurations and the unchanged jmhCompare gate. Capture vmstat context and both raw reports. The standard perf mode remains the default and its budgets are unchanged. Separate concurrency groups keep this diagnostic from cancelling standard perf. No automatic retries, baseline writes or local heavy tests. Undo by reverting this workflow-only addition.

- 2026-10-27 · Keep these related save/reload repairs in a dedicated claim — avoid overlap with the completed performance work in PR #21 and active M6 work — split the claim if review finds a dependency or ownership conflict.
- 2026-10-27 · Use one agent and an isolated main checkout with temporary diagnostic probes; no client windows or production-code edits.

- 2026-10-27 · Write schema 3 so older builds cannot ignore bindings and silently misread new saves. Migrate versions 1 and 2 without inventing identities, leaving all released fixtures unchanged. Legacy fixed built-in indices resolve through the seeded built-in table; ambiguous custom indices explicitly fault instead of guessing. The content hash does not cover index assignment. Undo by reverting the bindings changes; doing so loses safe custom-save identity.
- 2026-10-27 · Use one agent and review the full diff locally; no delegates or client windows. Compare JMH against an unmodified same-host control in addition to keeping the approved Mac baseline untouched.

## Pitfalls

- See [save/reload identities](../kb/save-reload-identities.md): content hash versus session table, every in-use reload reference, and terminal lifecycle after load.

## Review

Assignment merged by PR #26. WIP: performance gate unresolved; no hand-in PR, self-approval or merge. Independent verification is required before hand-in.
