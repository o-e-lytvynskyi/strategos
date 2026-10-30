# M7 on dev/lev1pes/m7

**Developer:** lev1pes · **Branch:** dev/lev1pes/m7 · **Work:** M7 · **Base:** main @ 267da40 · **Claimed:** 2026-10-29 · **Status:** in progress

**Note:** whole M7 requested by the operator on 2026-10-29; draft [PR #34](https://github.com/o-e-lytvynskyi/strategos/pull/34); explicit pre-merge development authorization persists on resume. Assignment is represented by this branch file after main removed the shared registry.

## Now

- **Milestone:** M7 Multiplayer; 4 of 28 implementation steps complete. Specification: [M7](../milestones/M7-multiplayer.md), based on planning PR #32; T3 is split before implementation into independent aggregate and production integration.
- **In progress:** T3a independent lobby contract, tests first. No network admission or production match start is installed by this step.
- **Last finished task:** T2b.1 prerequisite composition and T2b.2 persisted authority, accepted on 2026-10-30 after all required current components passed.
- **Next task:** T3a immutable configuration/readiness/preparation rules; T3b production integration still needs M6 MATCH-02. D-20 is explicitly accepted.
- **Merged from main last:** 8111c0a (2026-10-30), zero commits behind at final T2b fetch. Verify, core slow/soak, matched server slow, paired JMH, standard cloud L6 and tooling smoke passed.

The operator requested ownership of the whole M7 and work on what is currently possible on
2026-10-29. This assignment intentionally includes responsibility for later blocked tasks;
it does not declare them ready, waive their dependencies or accept any product decision.
The operator subsequently explicitly instructed development without waiting for approval/merge. That instruction overrides the assignment wait for this session; independent review and human merge are still required.
The planning specification from our own PR #32 is incorporated and reconciled in this branch; its registry row and completion status are not imported. Its checks are not runtime implementation evidence.

## Depends on unfinished work

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 | Combat authority, final damage, projectile output and close findings | Independent: combat delivered by PR #33 at the base; inspect current implementation during T1 | None required for merged M5 |
| M6 MATCH-02 / MATCH-03 | Production start pipeline and terminal outcome/snapshot integration | Against a contract for pure rules; after for production lobby start, coalition victory and restore validation | AFTER-M6: start, outcome and schema adapters when introduced |
| M6 AI-05 / AI-18 / UI-20 / solo fog | Observed AI inputs, difficulty, minimap and authoritative visibility | Against a contract only where fakes prove independent behavior; after for runtime AI, HUD and hidden-body integration | AFTER-M6: AI, HUD and visibility adapters when introduced |
| M6 QA-13 / PERF-09 | MVP fun verdict and accepted performance reference | After: ART-03 start and final PERF-10 acceptance; do not substitute provisional results | AFTER-M6: art and final performance prerequisites |
| M8 foundations | Shared-vision and recipient policy contracts | Independent M7 identity/grant rules; reconcile contracts without importing or changing the separate M8 branch | AFTER-M8: shared-vision reconciliation when introduced |
| Save/reload PR #30 | Stable template references and terminal-state restoration fixes | T2a independent foundation complete; T2b/T5 require a verified resolution of M5 snapshot composition and the open legacy-index review finding before schema installation, on a merged or explicitly stacked prerequisite | List unresolved integration in T2b/T5 |

Initial order: T1 readiness, T2a pure core foundations, T2b authority/persistence installation, then reassess T3/T5/T6 and later tasks
against merged prerequisites. Split oversized or partially ready tasks in the specification
before implementing them. T2 does not certify friendly-fire, allied victory, command adapters
or replication privacy; those retain their own integration tests and dependencies.

## Log

### 2026-10-30 — T2b accepted; advance to T3a

- Standard L6 41048400982 passed in 59m 51s: all 22 required GameTests, 27 JMH configurations, 19 passing and two existing report-only scene reports, no invalid report. Foreign CPU mean windows were 0.002102–0.006453; idle ratio 0.270785/0.30, path D p99 0.825912 ms/6 ms, protected-lookup ratio 2.412562/3.0. Current accepted validity policy applies, no threshold or native reference changed.
- Combined current task evidence is complete: verify 41051799948 (2,309 cases), core slow/soak 41050854156 (216), matched server slow 41048580649 (19 M7 cases, identical control set), paired JMH 41051803906 (27 matching configurations, unchanged allocation/time/validity gates), L4 smoke 41054233370 (three inspected original frames and three candidates). All have zero failures/errors/skips where applicable. Earlier red/interrupted evidence remains unchanged in this log.
- The standard server/perf source 42032ea has identical production runtime to the final verified checkpoint e7a75bd. Its JMH allocation evidence is superseded by the repaired identical-probe paired experiment; the probe-only change does not modify the GameTest/perf runtime. No aggregate failed job is relabelled as passed.
- Removed temporary paired slow/core-only/JMH workflow modes and their concurrency branches after collection. Standard nightly command, 25-minute job bound and ten-minute server task bounds are unchanged; standard perf retains only its documented 90-minute aggregate resource window. Telemetry/log retention remains.
- **T2b.1 and T2b.2 complete (4/28).** Proceeding to T3a contract tests before implementation; no M7 feature card or full multiplayer acceptance is declared complete by these foundations. PR #34 stays draft.

### 2026-10-30 — profiler repair validated without relaxing gates

- Verify 41051799948 on e7a75bd passed in 8m 25s: 2,309 canonical cases, zero failures/errors/skips, including the four new Linux counter tests. Core slow/soak 41050854156 completed 216 cases in 4m 23s; server slow 41048580649 completed all 19 M7 cases within its original ten-minute task bound. These complete components are separate raw runs, not a relabelled pass for the older interrupted aggregate jobs.
- Paired JMH 41051803906 passed all 27 matching configurations with the identical measurement repair on both sides. Maximum runtime ratio 1.099172; StateHash 1.000098 and Motion 1.000922. Both sides had valid cumulative CPU telemetry (maximum per-benchmark mean 0.007754/0.005631 against 0.10) and zero observed steal.
- Non-event GC allocation rate fell from about 0.208 MB/s to 0.0073 MB/s; candidate StateHash 0.166 B/op, Motion 2.452, Spatial 0.037 and ShotHash 0.236. Every existing kernel allowance passed. No acceptance threshold, background allowance, production kernel or approved reference changed. See the new JMH-profiler KB entry for the source/experiment distinction.
- Full standard L6 41048400982 and required tooling L4 smoke 41054233370 are still pending; both T2b checkboxes remain open until their evidence is accepted.
- L4 smoke 41054233370 subsequently passed in 3m 16s under Linux llvmpipe/Xvfb, result.ok=true. Inspected all three full-resolution frames and their three resized candidates: rendered terrain/units present, no black frames. The smoke scenario has no approved golden frames (empty diff frame list); this is client/tooling smoke evidence, not golden or M7 lobby acceptance. Standard L6 remains pending.

### 2026-10-30 — D-20 hosting decision received

- The owner explicitly selected dedicated servers plus Open to LAN through the same service, with one active match per server, in this session. Recorded the decision in the M7 card/spec; dedicated-server tests remain authoritative, multiple-match discovery stays outside M7. Main STATE remains the maintainer's record to reconcile.

### 2026-10-30 — matched slow passed; Linux profiler allocation defect

- Matched server run 41048580649 passed on both sides: main 7m 24s, M7 7m 55s, identical 19-case sets, no failures/errors/skips, original ten-minute bounds. Previous interrupted runs remain evidence; a server runtime regression was not established by this control.
- Paired JMH 41048405027 failed the unchanged allocation gate, not the 15% timing or validity gate. All 27 configurations matched; maximum timing ratio 1.061640, CPU load valid on both sides. Five kernels showed essentially identical unexpected allocations in main and M7: steady GC allocation rate about 0.208 MB/s (e.g. Motion 116.49/115.91 B/op), pointing to shared measurement overhead rather than M7's runtime.
- The newly delivered InternalProfiler invokes the Linux MXBean CPU-load reader inside GC measurement callbacks. Replace that Linux path with reusable-buffer cumulative /proc counters, keeping the same validity limit, unavailable verdict and non-Linux behavior. Regression coverage checks counter semantics, malformed/overflowing input and bounded sampling allocation. No allowance, threshold, kernel code or approved baseline changes.
- Next paired measurement applies the identical measurement-only repair to both pinned main and candidate, retaining the control patch and source digests. This isolates runtime differences and tests whether the shared profiler caused the observed allocation rate; no pass is claimed before that experiment completes.

### 2026-10-30 — operator resumed T2b and M7

- The operator explicitly resumed T2b to completion and then available M7 work. The previous stop and its three failed slow attempts remain recorded; this resume starts a fresh investigation, not an unchanged retry.
- Integrated main 8111c0a, including delivered PR #30 and accepted ADR-0026 part B. Resolved shared persistence conflicts by retaining schema-4 authority plus main's stable-template implementation, review cleanup in ContentTests and ADR-0027's ended-match consequence. No released fixtures changed.
- Added a temporary opt-in matched slow job: current main 8111c0a and M7 on one runner, both built before measurement, then sequential unchanged ten-minute server gates. Both exit statuses and logs are preserved even if the first gate fails. This diagnoses server/harness timing before selecting a fix; new main's validity rules apply unchanged.
- Reintroduced temporary paired JMH against delivered main 8111c0a so both sides use ForeignLoadProfiler and the current validity gate. Standard cloud L6 is rerun under the accepted policy; old Linux passes are not silently relabelled as current-policy acceptance.
- Diagnostic run 41048334339 stopped during checkout of the abbreviated control SHA; no build or test executed. Pinned the full 40-character SHA for actions/checkout, as in paired JMH; preserved the failed setup evidence.
- Current merged-head verify 41048293613 passed in 8m 37s, 2,305 canonical cases with no failures/errors/skips. Isolated core slow/soak is collected separately from the matched server run; acceptance requires both complete components, with their original task bounds and all cases present. This is task-level evidence, not a claim that the old aggregate full-ci failures passed.

### 2026-10-30 — full cloud L6 passed, checkpoint and stop

- [Standard perf 40972197958](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972197958) on 33ecb12 passed in 1h 1m 55s. The server log explicitly confirms all 22 required tests passed; all 27 JMH configurations completed. The 21 scene reports contain 19 `pass` and two unchanged `report-only` verdicts (page capture/publication and viewer bytes), no failed/invalid report or CPU-counter validity reason. Nested report-only metrics retain their existing policy.
- Measured foreign CPU ranged from 0 to 0.009142 (0.9142%); idle/moving ratio 0.218495 against 0.30, path D p99 0.484812 ms against the unchanged Linux CI limit of 6 ms, protected-lookup ratio 1.162653 against 3.0. This is full standard Linux CI evidence with factor 3, not native Mac or final multiplayer PERF-10 acceptance.
- Runtime sources are unchanged since the verified/measured 33ecb12 checkpoint. Later commits only record results and adjust diagnostics. Records checks pass: 14 claims, 245 cards, zero problems. No performance reference or threshold was changed.
- **Stop:** strategos-dev §8, "a task fails three times". The slow gate failed in three honest attempts, including true process isolation; T2b.1/T2b.2 remain open (2/27 steps complete). All launched cloud runs and local monitors are finished. The branch is pushed, PR #34 remains draft, and no merge/approval or full-M7 completion is claimed. On resume, investigate a matched server control and harness timing; later M7 runtime work retains the documented M6/hosting dependencies.

### 2026-10-30 — third slow failure, isolation did not explain the deadline

- [Full-ci 40975691944](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40975691944) completed core slow/soak successfully in 3m 48s (216 cases, no failures/errors/skips), then started server slow in a separate Gradle process. The server again hit its unchanged ten-minute timeout; 18/19 server cases had completed.
- `map_fifty` started at 00:02:53 UTC and was interrupted at 00:04:31, after about 97 seconds. The earlier prerequisite control finished that scenario in roughly 105 seconds, but on a different runner; these timings cannot establish a runtime regression or justify a relaxed deadline.
- Retained 898 vmstat intervals: mean runnable queue 2.31, idle 51.26%, zero observed CPU steal. Core workload overlap is not a sufficient explanation because this run eliminated it. Paired core JMH remains green, but does not certify server/harness performance.
- Removed the ineffective diagnostic scheduling changes; retained vmstat and slow-server logs, the original gate command, every test and every timeout. No fourth slow attempt or new feature task is started under strategos-dev §8. Both T2b steps remain unchecked; standard L6 was already running and will be collected before stopping.
- A resumed investigation should compare the prerequisite and candidate server suites on one cloud host, inspect scenario/tick/terrain/GC timing, and preserve failed evidence. No new baseline or threshold is proposed from the current data. Other M7 runtime tasks still need M6/D-20 or later multiplayer dependencies; QA-14 has its own MVP/date gate and branch.

### 2026-10-30 — scheduling hypothesis not yet isolated

- [Full-ci 40974064459](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40974064459) again hit the ten-minute server timeout, after 216 passing core cases and 18/19 finished server cases. `map_fifty` had 38 seconds before termination. The logs still show core soak executing during the server run despite `--no-parallel`; the attempted isolation was ineffective.
- Retained vmstat: 777 intervals, zero CPU steal, mean runnable queue 3.79, mean idle 33.04%; these whole-machine counters cannot attribute load to the mod or core soak individually.
- Split the existing required tasks into two separate Gradle invocations: core slow/soak completes before server slow starts. Added slow-server logs to retained artifacts. No test, timeout, threshold or workload change. The two unsuccessful runs remain recorded; the next run tests actual process isolation.
- Fetched main at this checkpoint: zero commits behind 134271f.

### 2026-10-30 — paired JMH accepted, diagnostic workflow removed

- [Paired JMH 40972202260](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972202260) passed on 33ecb12 against pinned prerequisite 0debda5 on the same runner. Both sides contain 27 matching configurations with equal measurement/JVM settings; the unchanged 15% and allocation comparator passed.
- Maximum candidate/control time ratio was 1.050607 (`CrossingBench.cross384`); codec encode ratio 1.013487. Each JMH took about 8m 42s. vmstat retained 522 control and 521 candidate intervals, zero observed CPU steal on either side, mean idle 67.33%/67.19%. These are supplemental Linux results, not native Mac baseline acceptance or a baseline promotion.
- Removed the temporary opt-in paired job/input and its mode-specific concurrency after collecting its complete evidence. Standard perf keeps its original command/measurements and the documented 90-minute aggregate resource limit. Isolated slow/soak run 40974064459 and standard perf 40972197958 remain pending; T2b steps stay unchecked.

### 2026-10-30 — slow gate timeout and isolated scheduling

- Merged-head verify [40972188768](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972188768) passed in 8m 49s: 2,303 canonical cases, zero failures/errors/skips.
- Full-ci [40972193346](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972193346) failed at the unchanged 10-minute `runGameTestSlow` timeout. Core slow/soak passed all 216 cases; 18 of 19 server cases had finished, with `map_fifty` starting only 17 seconds before termination. No full slow pass is claimed.
- The logs show core soak concurrent with the server on the two-core runner. The prerequisite control completed the same server suite, including `map_fifty`, in the earlier green run 40941840635. Neither observation proves a runtime regression or excludes one.
- Changed full-ci scheduling to `--no-parallel` and added retained vmstat evidence to test the contention hypothesis. All tests, scene lengths, individual timeouts and the 25-minute job limit are unchanged. The failed run remains evidence; no unchanged retry.

### 2026-10-30 — functional authority gate passed, main synchronized

- Verify 40970846751 on 82ee012 passed in 9m 2s: 2,303 canonical XML test cases, zero failures/errors/skips, including ten core authority cases, two journal-input cases, the NBT authority round trip and both schema-4 fixture readers. Existing shot/stance allocator/hash assertions pass with the mandatory authority-only JSON block.
- Merged main 134271f. Incoming changes are CI dependency-graph submission, security documentation and its branch record; `git diff` confirms zero core/mod runtime changes relative to the verified checkpoint.
- **Next:** final merged-head verify/slow/soak, complete 22-scene L6 under the corrected Actions resource limit, and pinned same-host JMH with the unchanged 15%/allocation gate. Both T2b checkboxes stay open until accepted.

### 2026-10-30 — resumed after usage interruption, schema-shape compatibility

- The operator resumed after the automatic approval reviewer hit a usage limit. No blocked action was bypassed; the same reviewed cloud-read route succeeded on resume.
- Previous verify 40954525926 on 52a563c ran 1,913 canonical cases: two failures, zero skips. All ten new MatchDiplomacyTest cases, both input-codec cases and all sixteen foundation cases passed. The two failures were old shot/stance tests asserting that an unused `play` block is absent; schema 4 now requires authority there.
- Refined those assertions to require exactly the `diplomacy` key while leaving allocator-history, stance, round-trip and hash assertions intact. No scenario, check, fixture or performance criterion is removed.
- **Next:** complete cloud verify, then slow/soak, full L6 and the pinned same-host comparison.

### 2026-10-29 — T2b.2 static-check correction

- Verify 40953293646 on b1239f5 reached ErrorProne and failed `OperatorPrecedence` under the unchanged `-Werror` policy: mixed `||`/`&&` in value equality needed explicit grouping. Added parentheses; no warning was suppressed and no test case had run in that attempt.
- **Next:** third implementation verification attempt. If it fails again, stop this task line under the developer procedure and record the concrete result.

### 2026-10-29 — T2b.2 compilation correction

- Implementation checkpoint e340d0f failed verify 40952453183 at core compilation: the setup diagnostic omitted MatchLog.warn's required simulation-tick argument. No test cases ran. Verified the actual signature and corrected the call; no check or threshold changed.
- **Next:** complete cloud verify before starting the expensive post-implementation gates.

### 2026-10-29 — T2b.2 test-first and implementation

- Test-only checkpoint ac81511 failed cloud verify 40951563816 at compilation because the new setup/query/hash APIs were absent, as intended; zero test cases ran. Added the implementation after inspecting that failure.
- MatchState now owns immutable authority; MatchQueries exposes a detached view. Trusted locked setup is canonicalized, journaled with bounded tag 9, and accepted only once on tick 1. Legacy identity hashes are preserved, with a primitive mixer for non-default authority.
- Schema 4 carries immutable authority in Play, requires its block, migrates old schemas to identity, and validates bounded/canonical data. Existing schema-1/2/3 fixtures remain untouched; new JSON/NBT v4 fixtures/readers are append-only. Added ADR-0028 and a source AFTER-M6 marker for outcome/statistics reconciliation.
- **Next:** cloud verify/slow/soak, full standard perf under the corrected runner limit, and pinned same-host JMH. Both T2b steps remain unchecked until required acceptance completes.

### 2026-10-29 — composition gates and CI execution limit

- Composition 0debda5: verify run 40941835647 passed in 5m 48s with 2,288 canonical XML cases; full-ci 40941840635 passed in 10m 21s with 235 cases. Both have zero failures/errors/skips.
- Standard perf run 40941845938 was cancelled by the GitHub job limit: annotation explicitly says maximum execution time 55m0s. It completed 27 JMH configurations and 16/22 GameTests, with 14 passing perf reports plus one report-only byte report. No full L6 pass is claimed.
- GitHub cancelled during map setup for the next scene; JMH started at 19:42:52 UTC, speed ratio at 19:51:26, and 22 GameTests at 19:52:41. The job ended at 20:33:16. This is the aggregate runner deadline, not evidence of a per-test budget failure.
- Raised only the aggregate Actions resource deadline to 90 minutes so all existing serial scenes can run; individual game-test timeouts, workload lengths, CI factors, performance thresholds and baselines remain unchanged. Added an opt-in pinned same-host JMH job with the existing 15%/allocation comparator.
- Refined phasing: T2b.2 may use the verified persistence contract while T2b.1 stays incomplete; the corrected full L6 closes both steps only after the combined result passes. No performance gate is removed or accepted from the partial run. Publishing test-first contracts before runtime code.
- **Next:** cloud red checkpoint, then production implementation and full combined gates.

### 2026-10-29 — T2b.1 explicit persistence prerequisite composition

- Stacked corrected PR #30 at 588fa57 without treating it as delivered or changing its branch/status. Its M5 snapshot merge and legacy-reference review fixes are now present in this composition; full combined gates are pending.
- Resolved ADR index conflict by keeping main ADR-0026 performance validity and renumbering the imported save-identity ADR to 0027, with policy links updated. The original dependency branch/review records retain their historical numbering. No semantic policy, threshold or baseline change.
- Split T2b before implementation: T2b.1 prerequisite composition/gates, T2b.2 authority installation with versioned snapshots and journaled setup. No live authority integration starts without preservation across restore/replay.
- Main merge checkpoint 760a3b4 passed [cloud verify 40940234420](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40940234420) in 8m 51s: 2,267 canonical XML cases, zero failures/errors/skips, including the 16 T2a cases.
- **Next:** cloud verify, full slow/soak and standard cloud performance on the combined prerequisite; then T2b.2 with failing contract tests first.

### 2026-10-29 — resumed, main synchronization and claims migration

- The operator resumed work, lifting the earlier session stop. Cloud-only testing and pre-merge development authorization remain in effect.
- Merged main through b893a0d: the shared claims registry is removed, claims live in branch headers, the M7 planning delivery is on main, and performance-validity instrumentation/ProtectedBlockIndex structural tests are included. Preserved the implementation specification because the planning document on main is identical to our original planning baseline; retained all T1/T2a implementation changes.
- Removed the obsolete registry conflict and represented M7 ownership in this file; no other claim is changed locally.
- PR #30 now records implemented review fixes and a resolved M5 composition conflict, with verify/soak passing, but full slow/L6 gates are unaccepted. Reassess the corrected contract and actual cloud evidence before installation; do not carry forward stale claims that review fixes are missing.
- **Next:** cloud merge gate and T2b readiness assessment.

### 2026-10-29 — verified main merge and operator stop

- **Main synchronization** — [cloud verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40789677322) on merge 4e1ff97 passed in 6m 2s: 2,253 canonical XML test cases, zero failures/errors/skips; all 16 diplomacy cases executed. Expected negative harness failures were caught by their gates.
- Final fetch retains main dcb5013; main is an ancestor, zero commits behind. No main push, rebase or force-push. Claim and roadmap checks cover 8 rows and 245 cards with zero problems.
- **Stop:** the operator explicitly requested stopping for today once T2a and the main merge were finished. Both are complete. T2b and all later tasks remain unstarted; no continuation or new test run is scheduled. PR #34 stays draft because the whole milestone is not complete.
- **Next on resume:** verify the persistence prerequisite fixes before authority/schema integration; missing M6 runtime and owner/product acceptance gates remain explicit.

### 2026-10-29 — requested main synchronization

- Fetched main from 267da40 to dcb5013 (five commits). Merged current central records and M5 delivery status; resolved the claims.md conflict by keeping all main rows and appending our M7 assignment, without rolling back M6/M5 status fixes.
- The Windows wrapper delta has no executable changes when line endings are ignored; no history is rewritten. T2a runtime sources and tests are retained.
- Reassessed PR #30 at c9dd148: the new commit is a maintainer review record, not a persistence repair. Its open major finding concerns legacy indices reinterpreted against the expanded M5 built-in table; SavedTemplates.resolve still uses the current built-in index table for legacy references. This is a reported unresolved compatibility risk, not a new locally reproduced test failure. T2b/T5 need the verified migration policy and M5 snapshot composition before installation; waiting for claim approval is not the blocker.
- **Next:** cloud verify on the merge checkpoint, then record its result.

### 2026-10-29 — T2a complete

- **T2a** — [cloud verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40788528341) on e8438cd passed in 7m 20s. Canonical XML reports contain 2,253 test cases, zero failures/errors/skips, including all 16 new DiplomacyStateTest cases. Existing static/architecture, adapter, GameTest, server-smoke and negative harness gates pass. No running tick, order or hash kernel changes; no slow/soak/JMH gate is waived. MATCH-06 remains in progress; no card acceptance is claimed.
- A read-only merge-tree assessment against our own save/reload PR #30 found one conflict in Snapshots.java with M5; no prerequisite merge or conflict resolution was applied during T2a. Reassess after fetching current main.
- **Next:** operator-requested main merge, cloud gates, then T2b readiness.

### 2026-10-29 — T2a test-first and implementation checkpoint

- Added 16 L1 contract tests before publishing production classes. [Cloud red checkpoint](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40788100711), commit 4817ee5, failed at core test compilation because DiplomacyState/DiplomacyView were absent, as intended; it ran zero test cases and is not runtime failure evidence.
- Added immutable state/view, explicit legacy identity setup, primary-faction bindings, locked teams, faction-owned non-transitive grants, stale-authority revocation, validated detached snapshot reconstruction and canonical hash contribution. No MatchState, command, save codec, network or step/hash kernel is wired by this foundation.
- **Next:** full cloud verify on the implementation; do not mark T2a complete until its XML reports and gate results pass.

### 2026-10-29 — T1 readiness and task split complete

- **T1** — imported our planning spec, reconciled M5 delivery and pending M6/persistence decisions, split T2a/T2b before implementation. [Cloud verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40787146879) on 40e770d passed in 6m 46s with 2,237 canonical XML cases, zero failures/errors/skips. Negative harness inputs were rejected as intended; no runtime defect is inferred from their expected log failures. Records check also passed.
- **Next:** T2a test-first cloud checkpoint, followed by implementation and full cloud verify.

### 2026-10-29 — pre-merge development authorized

- The operator explicitly instructed development without waiting for claim approval. This is a procedural exception for this session, not an independent review, merge, missing prerequisite or product-decision waiver.
- Incorporated our M7 planning specification, reconciled M5 delivery and pending M6/persistence contracts, and split T2 into independent foundations (T2a) and authority/persistence installation (T2b) before runtime changes.
- MATCH-06 enters in progress for those bounded foundations; no card acceptance is complete.
- The operator requested cloud tests. The initial local baseline verifyFast was interrupted on that instruction; it is not passing evidence. Use the existing workflow_dispatch route without changing gates. Publish a WIP checkpoint for cloud verification, then record green task completion only after reading the reports.
- **Next:** cloud T1 gates, then failing L1 tests for T2a.

### 2026-10-29 — whole-milestone assignment request

- The operator requested M7 ownership and available work. Fetched main at 267da40 includes M5 delivery PR #33. The main registry has no M7 implementation holder or reservation; the open PR list contains planning PR #32, save/reload PR #30 and records PR #29, with no competing M7 implementation claim.
- Prepared an assignment-only change: this branch record and one registry row. No runtime, tests, roadmap statuses, STATE or worklog changes.
- Validation: UTF-8 claim check covers 8 rows with zero problems; roadmap check covers 245 cards with zero problems; `git diff --check` passes. No Gradle run is needed for this assignment-only change; no implementation gate is claimed.
- Reviewed the 25-task planning breakdown and separated independent core rules from missing M6 adapters, pending persistence fixes, owner decisions and real-client acceptance.
- **Next:** independent claim review and human merge, then T1/T2; implementation remains unstarted.

## For the owner

1. Independently review and merge the assignment PR. The operator explicitly waived the development wait in this session; independent review and human merge remain required.
2. Planning PR #32 and persistence PR #30 are delivered on current main. D-20 was explicitly resolved in this M7 session on 2026-10-30: dedicated plus LAN, one active match. Reconcile its main STATE record at review. D-03 themes, deferred first-person fighting/scouting, the MVP fun verdict and art acceptance remain separate owner gates.
3. Later two-client measurement and acceptance require the owner's clients and the completed hardening prerequisites. No platform-port decision, public release or performance baseline change is included.

4. Cloud verify takes 6m 46s on the readiness baseline and 7m 20s with the foundation, beyond the procedure's 5-minute target. Keep all existing gates; recommend accepting the cloud duration. Moving checks or changing a Done gate requires an explicit owner decision.

5. Independently review the measured Linux JMH-profiler repair included here. The same allocation pollution occurs on delivered main; extracting the tested measurement-only change before full M7 delivery would unblock other Linux branches. Keep the accepted validity and allocation policies; no main merge or baseline promotion was performed by this session.

## Decisions taken

- 2026-10-30 · Split T3a immutable lobby contract from T3b authenticated production service — configuration/readiness transitions can be proved independently while M6/start and D-20 remain open — recombine the task records after both parts pass; no NET-02 runtime criterion is removed or declared complete by T3a.

- 2026-10-30 · Undo diagnostic slow-gate scheduling after true process isolation still failed — overlap was not sufficient to explain the timeout — investigate a matched server control before changing execution policy again; retain telemetry and all unsuccessful evidence.

- 2026-10-30 · Serialize full-ci test projects and retain vmstat — the failed run overlapped core soak with a server on two cores — restore parallel scheduling only after evidence establishes safe coexistence within unchanged timeouts.

- 2026-10-29 · Keep immutable authority directly in Play and validate one value at decode — avoids repeated per-seat reconstruction while retaining detached snapshots — change only the internal representation behind the codec/value boundary if measurements justify it.
- 2026-10-29 · Use schema 4 with explicit legacy identity migration and required current authority — old readers must reject rather than ignore rights — any future format interpretation requires a new migration/version.

- 2026-10-29 · Give the standard perf Actions job 90 minutes after the observed 55-minute cancellation at 16/22 cases — allows completion of the same measurements without changing their acceptance criteria — restore the old resource limit if a reviewed execution layout fits the full suite within it.
- 2026-10-29 · Share the final full L6 acceptance across composition and authority installation — dependency code already passes verify/slow/soak; the remaining issue is a runner deadline, not an accepted performance result — require separate complete runs if review needs independent predecessor L6 evidence.

- 2026-10-29 · Explicitly stack our corrected PR #30 as a prerequisite — its persistence contract is needed to avoid competing schema changes; combined gates must pass and its own review/delivery remains separate — base M7 on the delivered equivalent when that PR merges.
- 2026-10-29 · Renumber the imported save-identity ADR to 0027 — main already owns 0026 for performance validity — reconcile the number with the eventual dependency delivery without changing either policy.

- 2026-10-29 · Make the standalone diplomacy state immutable and use it as the read view — old query/snapshot references cannot observe later mutation; canonical snapshots and a hash contribution are tested separately from match-schema integration — replace the representation behind DiplomacyView if measured setup costs justify it.
- 2026-10-29 · Grants are faction-owned, primary-only and non-transitive; rebind/team changes revoke stale authority — prevents a reused seat or alliance change from retaining old control — change the explicit policy and regression tests together if the product model changes. Disconnect does not unbind.

- 2026-10-29 · Keep whole-milestone ownership as requested, with per-task readiness gates — prevents competing claims without pretending missing dependencies are delivered — release or narrow the assignment through the normal registry PR if ownership changes.
- 2026-10-29 · Start with independent core identity/diplomacy/grant foundations after readiness reconciliation — they can be tested without unmerged M6 runtime code — revisit the order when prerequisites merge.

## Pitfalls

- Added [diplomacy restore](../kb/diplomacy-restore.md): current authority versus legacy fallback, immutable sharing, compatible live writers and hash preservation.

- Use `python -X utf8` for bookkeeping on Windows; a legacy-code-page run can misleadingly report zero cards. Existing toolchain KB documents this.

## Review

Pending independent review.
