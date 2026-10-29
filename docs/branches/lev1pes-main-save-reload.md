# main-save-reload on dev/lev1pes/main-save-reload

**Developer:** lev1pes · **Branch:** dev/lev1pes/main-save-reload · **Work:** main-save-reload · **Base:** main @ b207e509a85a3303f515a716b04a87bc90ee2cb8 · **Claimed:** 2026-10-27 · **Status:** in progress

**Note:** assignment merged in [PR #26](https://github.com/o-e-lytvynskyi/strategos/pull/26), not an implementation delivery; fixes #23, #24, #25; excludes fix-main and new M5 scope

## Now

- **Milestone:** bounded follow-up fixes for three independently reproduced bugs in existing main: [#23](https://github.com/o-e-lytvynskyi/strategos/issues/23), [#24](https://github.com/o-e-lytvynskyi/strategos/issues/24), [#25](https://github.com/o-e-lytvynskyi/strategos/issues/25).
- **State:** review corrections implemented; verify and verifySoak pass. Full slow and performance gates remain unaccepted. Assignment merged by PR #26.
- **Last finished task:** all code review items and nits implemented, with 2,274 passing tests in complete reports and a passing isolated map_fifty diagnostic. Full slow gate hits its 10-minute timeout on both candidate and unchanged control. Paired local JMH is red under excessive foreign CPU load; no green L6 result is claimed.
- **Next task:** independent maintainer review and accepted full slow/L6 gates in a suitable environment. All review fixes and evidence are recorded; do not mark ready for review while those gates remain red/unaccepted.
- **Merged from main last:** cc43715 via f91f212 (2026-10-29). Its claims-format and records updates are integrated; runtime/build sources remain identical to the verified review candidate.

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
- Full score table, controls, binary/workload checks, CPU context and raw hashes: [local JMH evidence](../testing/main-save-reload-perf.md).

### 2026-10-28 — cloud verification requested

- The owner requested cloud tests and continuation of the assigned work. Standard perf run 40594606609 targets f9f9846 and is still running. It checks JMH speed ratios and server budgets, but deliberately does not run jmhCompare.
- Add an opt-in paired-JMH mode to the existing workflow_dispatch route, without opening an unfinished hand-in PR. The control is the trusted pre-repair main bb800d0; both versions run on one Ubuntu runner with the same JDK, heap, forks and iterations. All 21 benchmark keys and configurations must match before the existing comparison runs.
- Workflow validation: YAML parse, all pinned action hashes, read-only token permissions, Bash syntax of every run block, embedded Python syntax and roadmap checks pass. Production/test sources and benchmark settings are unchanged, so the completed 1,936-test functional evidence is retained. No new local Gradle run is started.
- No new approved baseline, raised limit or skipped gate. Cloud outcome and artifacts must be inspected before declaring this claim ready.
- First paired workflow run 40595484377 stopped before measurement because the preparation step used jmhJar, which this plugin-free harness does not define. Inspect the actual source-set convention and replace that preparation with jmhClasses; no benchmark result was produced or discarded. Preserve the failed run and logs. This is a workflow repair, not another unchanged performance retry.
- Corrected paired run [40595834693](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40595834693) passed. Pin pristine bb800d0 and candidate 8e98d8b on the same Ubuntu four-vCPU EPYC 9V74 runner, Temurin 21.0.12.1+1. Both full JMH measurements took 6m 16s; all 21 configurations match, the unchanged jmhCompare gate and allocation checks pass. Maximum ratio 1.095538; SnapshotCodec 1.006691. All raw scores, hashes, CPU context and prior failures are in [performance evidence](../testing/main-save-reload-perf.md). Standard perf remains pending.

### 2026-10-28 — hand-in report

- **Built:** #23 keeps ENDED across restore and rejects resume/commands; #24 shares complete in-use template validation before server publication/core swap; #25 resolves saved building/production references by stable IDs across session index changes.
- **Gates:** verifyFast 2m 18s; verify/slow/soak 19m 16s, 1,936 tests with no failures/errors/skips. Independent cloud paired JMH [40595834693](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40595834693): all 21 configurations and unchanged 15%/allocation gates pass, maximum ratio 1.095538; each measurement 6m 16s. Standard cloud [40594606609](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40594606609): all 18 required GameTests pass in 37.67m, whole build 45m 59s. All raw reports and verdicts inspected. This is Linux CI evidence, not native Mac-budget validation. L4 is not required for these persistence/reload fixes.
- **Deviations:** schema 3 protects against silent downgrade; legacy fixed built-ins remain supported, ambiguous custom indices explicitly fail. Released fixtures unchanged; new JSON/NBT fixtures pin buildings, queued products, progress and bindings.
- **Decisions taken:** share the reload seam and conservatively remap before reading templates. Reverting implementation removes the repairs/schema-3 support. Use trusted pristine bb800d0 as the same-runner comparison input, without promoting a baseline; retain every earlier failure. Remove the one-off paired workflow from the final diff after successful validation; its exact version remains at 8e98d8b.
- **For the owner:** independent maintainer review/human merge and the serial Windows verify-duration decision below. No gate or limit is weakened.
- **Links:** [hand-in PR #30](https://github.com/o-e-lytvynskyi/strategos/pull/30), this branch record and [performance evidence](../testing/main-save-reload-perf.md). Main was freshly fetched and remains bb800d0, already merged here; core/mod/build-logic trees are identical to the verified candidate.

### 2026-10-29 - PR #30 review corrections

- Integrated main 45c97ee in 8d4bdc3. Baseline verifyFast passed (up-to-date tasks).
- New regressions first failed for legacy remapping and schema-3 saves without bindings.
  Historical inspection also found early M3 house index 5 later became the field (47d7cbf,
  1a2f675 versus the later built-in table). Migration now supplies only the common reserved
  prefix 0..4; all later building/queue references fail explicitly. Re-saving migrated data
  keeps those conservative bindings instead of re-enabling implicit legacy resolution.
- Schema-3 decoding refuses live buildings without bindings. Runtime restore always resolves
  bindings; it never infers a legacy table from an empty list or a content hash.
- Server reload excludes terminal matches from both validation and queued swaps, preserving
  their frozen queryable set. The adapter rechecks a published swap before advancing the
  simulation; an invalidated swap throws through the existing match fault barrier.
- Added core coverage for template-backed nodes, kind changes, M4/M5 custom-index collisions
  and early M3 house/field ambiguity. Added L3 ENDED/FAULTED reload and publication-boundary
  fault regressions. The terminal-outcome save test now uses Outcome.save(); fixed fixture nits.
- ADR-0026 owns the persistence/legacy policy and reload boundary; ADR-0012 and ADR-0018 point
  to it. Architecture and the save/reload KB describe the compatibility limitation explicitly.
- First edit-batch verifyFast passed in 3m10s; refined historical-prefix batch in 1m54s.
  `verify` passed, including all 233 normal L3 tests (74.940s), 73 journal replays,
  200 soak seeds, 111 L2 tests, 12 mod architecture tests and 4 smoke tests. All 1,625
  normal and 16 slow core tests pass; complete reports total 2,274 tests with no failures,
  errors or skips. The combined `verify verifySlow verifySoak` invocation stopped after
  21m34s: runGameTestSlow hit its unchanged 10-minute task timeout, with 18/19 scenarios
  complete and map_fifty still running. No assertion failure or final slow XML exists;
  this is a failed/incomplete slow gate, not a passing functional aggregate.
- The unchanged control at 8d4bdc3 also hits the same 10-minute runGameTestSlow timeout
  (whole build 10m13s). Its worktree stays clean; all 55 navigation classes and MatchCore
  are byte-identical to the candidate. This establishes that the timeout also occurs without
  the review fixes; it does not turn either failed gate green or prove a particular CPU cause.
  The isolated map_fifty diagnostic passes: 1/1 required test, 19.142s reported L3 time,
  whole invocation 38s; verifySoak is confirmed (the same 200-seed result). That filtered
  diagnostic does not replace the failed full slow gate. No timeout, threshold, fixture
  or baseline changes.
- Paired local JMH completed: 27/27 configurations match, all 131 benchmark classes are
  byte-identical, seven breaches above 1.15 (maximum 1.382045), no allocation breaches.
  Candidate weighted foreign CPU is 28.000%, versus control 17.144%; neither a clean L6
  result nor a production regression is established. Full evidence and raw hashes are in
  [performance evidence](../testing/main-save-reload-perf.md). No further unchanged retry.
- During verification main advanced to cc43715: records for #36 were repaired in #37;
  #39/#40 move claims into branch-file headers. No runtime/build sources changed. Integrate
  those maintainer-owned changes rather than editing another developer's records.
- Main cc43715 is now merged via f91f212; verifyFast passes in 3s (no source rebuild).
  Run the new claims checker with Python UTF-8 mode on Windows: the default cp1251
  decoder misreads the UTF-8 header delimiters. Move the auxiliary performance report
  to docs/testing/main-save-reload-perf.md, since docs/branches now contains claims only.
  Every link in this branch record is updated; report content and raw evidence are retained.
- Post-merge records checks pass: 12 claims, 245 roadmap cards, zero problems; all 21
  claims-tool unit tests pass. Runtime/build trees match 4eef24d, so the completed functional
  evidence and recorded red slow/JMH gates apply unchanged to this synchronization.

## For the owner

### 2026-10-29 synchronization evidence

- Fast-forwarded the local worktree to the maintainer review c9dd148, then merged
  origin/main dcb5013. The only source conflict was Snapshots.take; retained M5's
  shots, stances and garrisons together with this branch's template bindings.
- verifyFast passes in 5m 48s; 1,621 core tests, zero failures/errors/skips. Roadmap
  checks 245 cards and claims checks 7 rows with no problems; diff whitespace check
  passes. No full verify, slow/soak or performance rerun is claimed for this refresh.
- Synchronization is complete; implementation of the maintainer's remaining review
  findings is a separate next step on this assigned branch. No release or merge
  into main was performed.

0. Current status: review items 1-7 and nits are implemented. The current local verify and
   verifySoak evidence supersedes the pre-M5 functional report. The complete Windows slow
   gate and local paired JMH remain red/unaccepted, so the branch stays in progress. Old
   cloud results validate the pre-M5 candidate and are not proof of this head's slow/L6 gates.

1. Independent maintainer review and human merge. Both owner-requested cloud runs passed: [standard perf](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40594606609) and [paired JMH](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40595834693). This is same-host regression/Linux CI evidence, not promotion of an approved baseline or native Mac-budget validation. Retain [all local failures](../testing/main-save-reload-perf.md).
2. Review the serial Windows `verify` duration (about 9m 10s versus the documented 5-minute budget): accept the platform time or decide separately which run moves to `verifySlow`. No run or gate was moved here.
3. Assignment merged by PR #26. The claim is ready for review and remains assigned until the maintainer merges the hand-in and closes it. No M5/M6 work is included.

4. The inherited #36 records failure was repaired by the maintainer in PR #37. Current
   main cc43715 also migrates claims to branch headers (#39/#40), now integrated. Use
   the new claims checker in UTF-8 mode on Windows. No other developer's record is changed
   by this session.

5. Both the review candidate and clean 8d4bdc3 control exceed the unchanged 10-minute
   Windows slow-server task limit. A maintainer must obtain an unfiltered passing full slow
   gate on a suitable machine, or have the owner decide the batch/platform budget. No timeout
   or test selection is changed in the project. Local logs are under ignored `build/pr30-*`.

6. Obtain accepted L6 evidence in a suitable environment. The one bounded paired comparison
   remains red (seven scores >1.15) with candidate foreign CPU mean 28.000%, above the
   existing 25% validity criterion. The separate owner-held perf-validity claim owns harness
   changes. No benchmark, threshold, baseline or application state is changed here.

## Decisions taken

- 2026-10-28 · After both cloud runs pass, restore perf.yml exactly from bb800d0 so a permanently pinned task-specific mode is not added to the final bug-fix diff. Its validated workflow and run remain at 8e98d8b/40595834693; restore that workflow commit if the same diagnostic must be investigated again.

- 2026-10-28 · On the owner's explicit cloud-validation request, add opt-in paired JMH to the existing manual perf workflow. Pin the control to pristine bb800d0; compile both jars before measuring; use one runner, sequential measurements, identical configurations and the unchanged jmhCompare gate. Capture vmstat context and both raw reports. The standard perf mode remains the default and its budgets are unchanged. Separate concurrency groups keep this diagnostic from cancelling standard perf. No automatic retries, baseline writes or local heavy tests. Undo by reverting this workflow-only addition.

- 2026-10-27 · Keep these related save/reload repairs in a dedicated claim — avoid overlap with the completed performance work in PR #21 and active M6 work — split the claim if review finds a dependency or ownership conflict.
- 2026-10-27 · Use one agent and an isolated main checkout with temporary diagnostic probes; no client windows or production-code edits.

- 2026-10-27 · Write schema 3 so older builds cannot ignore bindings and silently misread new saves. Migrate versions 1 and 2 without inventing identities, leaving all released fixtures unchanged. Legacy fixed built-in indices resolve through the seeded built-in table; ambiguous custom indices explicitly fault instead of guessing. The content hash does not cover index assignment. Undo by reverting the bindings changes; doing so loses safe custom-save identity.
- 2026-10-27 · Use one agent and review the full diff locally; no delegates or client windows. Compare JMH against an unmodified same-host control in addition to keeping the approved Mac baseline untouched.

- 2026-10-29 - Resolve review items 2-7 rather than waive them. Freeze legacy migration to
  the common historical prefix 0..4 after checking the earliest building/production history;
  reject ambiguous later builtins too, because custom-table identities cannot be proven.
  This intentionally narrows schema-1/2 compatibility. Undo by reverting the migration,
  but doing so reopens silent identity corruption. Record this policy in ADR-0026.
- 2026-10-29 - Keep terminal matches on their original set and fault an invalidated published
  swap before advancing the affected match. Undo by reverting the adapter/server guards;
  doing so restores terminal reload vetoes and permits silent server/match divergence.
- 2026-10-29 - One agent; no delegates or client windows. Use an unchanged detached copy
  of 8d4bdc3 for same-host JMH comparison; preserve raw reports and CPU telemetry, never
  promote that diagnostic control to an approved baseline or retry unchanged until green.

## Pitfalls

- See [save/reload identities](../kb/save-reload-identities.md): content hash versus session table, every in-use reload reference, and terminal lifecycle after load.

## Review

Assignment merged by PR #26; hand-in [PR #30](https://github.com/o-e-lytvynskyi/strategos/pull/30) remains open. Code review corrections are implemented; the current full slow and performance gates remain unaccepted. The branch stays in progress. Every approval and merge remains human.

### Maintainer review 2026-10-29 (head a91996f)

**Recommendation: merge after fixes.** No blockers in the repairs themselves; one major issue appears when the branch meets M5 (merged into `main` as #33), and the branch conflicts with `main`.

**Gates.** PR CI on a91996f: `records`, `verify` (5m41s) green. Local trial merge of `origin/main` 267da40 (macOS, not pushed; the only conflict, `Snapshots.java`, resolved by keeping M5's `shots/stances/garrison` and this branch's bindings): `verify verifySlow verifySoak` green in 5m47s, 2,286 tests, 0 failures/errors/skips. `jmhCompare` not repeated: nothing new runs per tick (reviewed), and the paired cloud JMH passed 21/21. Records: `roadmap.py check` 245 cards / 0 problems, `claims.py check` 0 problems; no build-surface, `.claude/settings.json`, dependency or `NOTICE` changes in the final diff; released fixtures append-only; no tool traces or personal data. The temporary paired-JMH workflow in d33aa0b–f1ce9f8 used pinned actions and no secrets, and is gone from the final diff.

**Must fix before merge**
1. **Merge `origin/main`** (M5) into the branch; the `Snapshots.take` conflict resolves by keeping both sides (M5's three `Play` fields, then the bindings argument).
2. **[major] Legacy indices silently remap after M5.** `SavedTemplates.resolve` (`core/.../sim/SavedTemplates.java:62-68`) treats any legacy (schema 1/2) index inside `TemplateSet.builtin()` as built-in. M5 appended `building/farmstead`, `building/storehouse`, `unit/archer`, `unit/horseman` to the pinned built-in table, so a pre-M5 save whose custom template sat at the old first free index now resolves to `archer`/`horseman` with a matching kind — exactly the silent remap this work set out to prevent; "ambiguous custom references fail explicitly" does not hold on `main`. Either reject legacy indices that are ambiguous (e.g. when the saved template-set hash differs from the built-in one and the index is in the range appended after the save's era), or, if that cannot be decided, state the limitation in `docs/kb/save-reload-identities.md` and the ADR note, and log a warning on such loads. A test that pins the chosen behaviour.

**Should fix (your call; record in "Decisions taken" if declined)**
3. `SavedTemplates.java:27`: a schema-3 snapshot with live buildings but no bindings is treated as legacy. Make legacy explicit (from the decoded version) or throw for v3 without bindings.
4. `templateSwapProblem` also checks ENDED matches, which never step, so a finished match can block every reload that drops a template it used. Skip ENDED/FAULTED, or record the rule in the ADR.
5. Server publishes the new set at load, each match swaps at its next tick; a Train/SpawnBuilding in between can create a reference the new set drops, and the core refusal is only a `log.warn`, leaving server and match sets diverged. Pre-existing, widened here: fault the match or re-queue, or raise it in "For the owner".
6. Tests: no core test for the node-template branch or the kind-change branch of `templateSwapProblem`; the terminal-outcome GameTest hand-rolls `List.of(1L,0L,1L,0L,tick)` — build it with `Outcome.save()`.
7. The schema-3 / bindings / legacy policy is a persistence-format decision; it rewrites `04-architecture.md` ("saved by string id, never by network index") and is appended to ADR-0012 (content pipeline). Prefer a short new ADR for save identities, with ADR-0012 pointing to it.

**Nits.** `MatchSnapshotSchema3NbtFixtureTest` comment says "for schema 2, released with M1 (T27)"; `FixtureMatches.schema3()` lacks the blank line before its javadoc and `SCHEMA_3_TICK` a javadoc; `MatchSnapshot` class javadoc exceeds 120 columns. The `match_v3` fixtures pin only the pre-M5 built-in table at tick 9 with no units; fine as a format pin, custom/shifted indices stay covered by `SavedTemplatesTest`.

**Decisions taken the owner may want to see:** the schema bump to 3 (older readers reject rather than ignore bindings); legacy custom references fail instead of loading; reload validation shared by server publication and core INGEST.

**Answered checks.** Every numeric template reference persisted (`BuildingStore.Saved.template`, `Production.queue`) is bound; units, nodes, pending commands, garrisons, stances and shots are saved by id or handle. Binding order is deterministic (`TemplateSet.all()`). Reload refusals change nothing. ENDED restore: submit, resume and ticking all refuse. No per-tick cost.
