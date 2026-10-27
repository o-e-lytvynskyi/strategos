# M5 on dev/o-e-lytvynskyi/m5

**Developer:** o-e-lytvynskyi · **Branch:** dev/o-e-lytvynskyi/m5 · **Work:** M5 · **Base:** main @ cb4044b · **Claimed:** 2026-10-26 · **Status:** in progress

## Now

- **Milestone:** M5 Combat; 6 of 22 tasks complete (T1, T2a, T2b, T3a, T3b.1, T3b.2).
- **In progress:** M5 T3c.1 shot FX state and wire since 2026-10-27.
- **Last finished task:** T3b.2 archer content and engine integration (2026-10-27).
- **Next task:** T3c.1, then T3c.2 delivery and client, then T4 high ground.
- **Merged from main last:** a10a8fd (2026-10-27), PR #21 fix-main.

## Depends on unfinished work

- M0-M4 are merged. No unfinished milestone implementation is required by M5.
- fix-main (lev1pes) is done and merged into main by PR #21; it no longer bears on M5.
- Preserve codex/m5-combat and dev/lev1pes/m5. Import code and task-specific evidence selectively after assignment; do not copy old central STATE/worklog records over current main.

## Log

### 2026-10-27 — M5 T3b.2

- **T3b.2 · Archer content and engine integration** — CMB-04 in progress, UNIT-07 stays specified until T7's duel matrix. The archer template with the §2.7 numbers (builtin and datapack copy), every number's provenance row, regenerated balance sheet, `strategos.unit.archer` label; builtin indices made explicit for farmstead/storehouse before the archer (index 16). `ArcherTemplateTest` (values, unchanged index prefix); `combattests.archer_volley_replays_as_the_core_predicts` (L3, 1.15 s): five archers damage core units, living bodies' vanilla health stays full, the journal replays to the same hash.
- Merged origin/main a10a8fd (fix-main PR #21); two docs conflicts kept both sides.
- Gates: `verify verifySlow verifySoak` 4m 52s, 1,987 tests (core 1,662, mod 325), 0 failures/errors/skips. The first run failed only on TemplateSetTest pins (17 templates, new SHA) and formatting, both expected from the archer. JMH not rerun: no hot path or codec changed since T3b.1's green run.
- **Next:** T3c.1 shot FX state and wire.

### 2026-10-27 — M5 T3b.1 (session moved from Codex to Claude Code)

- **T3b.1 · Ranged behavior** — CMB-04 in progress. Typed `Attack/Ranged` stats with validated ranges, `Ranged` approach without a melee ring, wind-up/repeat on M2's timer, `ShotRequested` intents released in COMBAT after MOVEMENT with flat-world lead from transient displacement; `CombatActions` dispatches melee/ranged. Tests: `RangedStatsTest`, `RangedValidationTest`, `RangedOrdersTest`, `MovingShotTest` (constant-velocity positive control, direction changes).
- Gates: `verify verifySlow verifySoak` green in about 4.5 min, 1,985 tests, 0 failures/errors/skips; roadmap 245 cards, claims 5 rows. JMH: first run flagged `SpatialBench.rebuild1000` 6.28 us vs 4.79 (one fork 7.7 us, the other 4.87 us; spatial code untouched); the single confirming rerun passed the whole gate in 7m 03s with rebuild1000 at 4.88 us in both forks. Recorded as a one-fork outlier of the benchmark, not of T3b.1; a repeat would make it a gate-noise item for the owner.
- The uncommitted Codex worktree state was adopted as is after reading its diff; the T3b.2 draft from a second worktree is kept as a patch and applied next.
- **Next:** T3b.2 archer template, provenance, balance sheet and the L3 volley; merge origin/main (PR #21) first.

### 2026-10-27 — bounded presentation tasks and ranged review

- Split T3c into core FX state/wire and server/client presentation before implementation: the combined estimate exceeds 1,500 source/test lines. Same approved launch/impact, visibility, images and byte-budget criteria remain required.
- Ranged review found and fixed automatic sight loss, sub-block chase stalling, stale weapon wind-ups and disabled-weapon fallback. Tests pin the initial prepare deadline and weapon changes. Full functional and performance gates remain pending before completing T3b.1.

### 2026-10-26 — split ranged behavior and archer integration before expansion

- Split T3b into T3b.1 typed stats/AI/release timing and T3b.2 content/provenance/L3 integration before adding the controller. Their combined estimate exceeds the per-task source/test target. Every original gate and acceptance criterion remains required; CMB-04 stays in_progress.
- Preserve all existing builtin network indices: the current explicit pinned prefix is followed by implicit farmstead/storehouse indices, which must be made explicit before appending the archer. No old fixture is rewritten to hide renumbering.
- **Next:** finish typed ranged stats, implement range-based approach and post-MOVEMENT firing, then validate full gates/JMH before the archer integration task.

### 2026-10-26 — complete T3a integration

- **T3a** — owned shot store registered before CombatSystem; optional nested Play snapshot state, pristine omission, used-empty allocator persistence, dead-shooter restoration, full frozen-packet codec and allocation-free hash loops. Six pipeline/persistence tests pass, including real used-empty relaunch; independent review has no remaining findings.
- verifyFast 49s; final verify verifySlow verifySoak 4m 39s, 1,958 cases, no failures/errors/skips. Full JMH 7m 02s, 25 configurations present, no regression/missing/allocation failures. Hash 300 active shots 32.394 us,0.223 B/op profiler background within unchanged gate; volley60 among 300 fighting infantry 5.348 us preferred, 6.270 us collateral, 0.827 us miss.
- First profiles for four new shot cases; all 20 prior primary metrics, 15% limit and allocation criteria unchanged. Added the active-shot hash gate. Benchmark uses direct package access rather than reflection and trial assertions prevent empty/depleted workloads. Evidence saved without personal paths.
- **Next:** T3b core ranged behavior and archer data, then T3c presentation. CMB-04 stays in_progress until all criteria including T14 budget E pass.

### 2026-10-26 — resume the remaining M5 work

- The owner requested completion of all M5 work. Claim remains assigned, no standing restrictions and no main changes since b207e50; the existing gradlew.bat checkout difference remains excluded. Reoriented from 08da21c and refreshed the worktree session lock.
- Initial verifyFast passes in 654ms; roadmap checks 245 cards and the current claims registry is valid. Resume T3a integration before T3b/T3c and the remaining approved tasks.
- **Next:** test in-flight and used-empty saved continuations, connect optional nested Play shot state without changing released snapshot/journal semantics, then run full gates and JMH.

### 2026-10-26 — verified T3a model checkpoint; stop on usage limit

- **T3a model slice** — ShotAim, ShotStore and standalone ShotSystem, SHOT stream 2 and deterministic log helper; 13 new tests. Exact independently verified 126/500 hits and 0.4921875 damage, immutable snapshots, due compaction, exhaustion/failed-launch atomicity, full packet preservation, shooter death and recycled handles are covered. No in-game firing path is registered yet.
- Independent source review found no defects. Orchestrator then removed empty/in-flight-only due-list allocations; final verify verifySlow verifySoak passes in 4m26s: 1,952 functional cases, 0 failures/errors/skips. Roadmap 245 cards and claims check pass. The current slice changes neither the running core step nor snapshot codecs, so integration/JMH remains in the unfinished T3a step, not claimed complete here.
- Implementation worker returned usage limit on the follow-up. Finish and push the green model step, then stop under strategos-dev §8. No source edits remain uncommitted except the pre-existing gradlew.bat normalization difference.
- Added analytic-combat KB notes, including post-MOVEMENT release construction for T3b's constant-velocity control. No new owner permission is required to resume the approved work when execution capacity is available.
- **Next:** T3a state/scheduler/optional-Play persistence and allocation-free hash integration, legacy save/replay canaries, in-flight and used-empty continuation tests, then required full gates/JMH. T3b orders/data and T3c FX remain untouched. Historical main CI stays a separate unresolved claim.

### 2026-10-26 — restore bounded T3 planning and start T3a

- Restored the legacy e8cf0c2 split into shot lifecycle, ranged orders/data and presentation before implementation. CMB-04 enters in_progress; no criterion or approved scope changes. T2a–T2b is pushed as 40491df; all its gates are green.
- Normalized trailing whitespace in the two baseline artifacts without changing parsed JSON; the new proposal inherited an extra EOF blank line from the old baseline. No source or numeric result changes.
- **Next:** inspect existing combat, RNG, snapshot and hash contracts; write T3a behavior tests first and implement the smallest lifecycle slice.

### 2026-10-26 — accept recovered T2a–T2b after full validation

- **M5 T2a–T2b · Mobility and performance** — six integration regressions, nine further cache/publication cases, verifyFast 49s, final verify verifySlow verifySoak 4m24s (1,939 tests), plus two targeted perf tests in 18s; no failures/errors/skips. Navigation on populated 512x512 with bridge is 10,114,672 bytes / 32 MiB.
- Full JMH and comparison pass in 5m56s: 21 measured configurations, no regression/missing/allocation failures. Movement 24.904 us/op; terrain 515.312 us/op against conservative documented two-grid profile 481.023; both-class page building 112.663 us/op gets its first tracked profile. Metadata-only comparison then passes (21 measured / 20 tracked, 445ms). NodeDiscovery remains informational as before.
- Absolute path p99 0.427499 ms / 2 ms; idle ratio 0.218951 / 0.30; both have valid low foreign load. Independent final source review has no findings. Preserve all historical branches and original control numbers in the new sanitized evidence report; no old central records or Windows baselines are imported.
- **Next:** restore the planned T3 split and continue T3a. The local red-budget stop is resolved; the separately assigned historical main investigation remains open and is not claimed as repaired by these Mac results.

### 2026-10-26 — isolate recovery performance cost

- Same-host T1 control (152ca9d), unchanged JMH settings and Temurin 21.0.12.1: MovementBench.step600 23.249 ± 0.462 us/op; TerrainEditBench.rebuildOnePage 290.034 ± 6.111 us/op. The focused diagnostic ran once in 36s; it is not a replacement for the full comparison gate.
- The original terrain benchmark edits page (0,0), but the recovery rebuilt four large pages on every operation. MovementBench uses the flat-world fallback, yet WORLD eviction reclassified all units every tick even with no footprints. Target those avoidable costs, retaining full clearance semantics and the existing workloads.
- **Next:** validate mobility reload/sparse-index caching, footprint presence and incremental-versus-full terrain publication, then repeat full gates and unchanged-baseline JMH.

### 2026-10-26 — split recovery after measured regressions

- T2 integration regressions first reproduced the defects, then all six cases passed; verifyFast 55s and verify verifySlow verifySoak 4m27s pass (1,930 deduplicated tests; no failures/errors/skips). Independent final review has no findings.
- JMH on the current M4 Pro / macOS 27.0.1 / Temurin 21.0.12.1 failed in 5m59s: MovementBench.step600 22.003 to 27.479 us/op (+24.9%); TerrainEditBench.rebuildOnePage 291.665 to 1027.918 us/op (+252.4%). No missing benchmarks or allocation findings. Baselines and thresholds are unchanged.
- Split T2a–T2b before expanding the recovery beyond its size target. Preserve all original gates and accept the two code steps atomically only after performance is green. The planning commit contains no product code; current recovery source remains uncommitted.
- **Next:** measure the T1 control, then remove repeated mobility classification, empty-footprint scans, unnecessary halo builds and duplicate decoding. No new features before resolving the measured regressions.

### 2026-10-26 — recover and verify T1

- **M5 T1 · Health check** — selectively imported nine reviewed source/test files from a13850b. Reproduced the scene-verdict failure and two roadmap failures before fixing them. Initial verifyFast 1m20s; recovered verifyFast 47s; verify verifySlow verifySoak 4m39s, 1,908 deduplicated tests, no failures/errors/skips. All 12 Python tool tests pass. No central records, baselines, thresholds or images were imported or changed.
- Added the UTF-8/empty-discovery pitfall to minecraft-mod-build.md. Confirmed extracted HUD calls in the exact NeoForge 21.1.264 Minecraft sources; no new engine API is introduced.
- Independent T2 review found adjacent-footprint eviction, mixed-class slot trading and repeated changed-page traversal gaps; reproduce and fix these during recovery before accepting T2.
- **Next:** recover T2 with regression tests and review fixes; validate full movement gates and JMH. The separate main perf claim is unresolved; no new feature work starts yet.

### 2026-10-26 — resume assigned M5 recovery

- The owner merged assignment #19 and authorized autonomous development and recommended decisions. Merged main b207e50 without rewriting history. Main merges remain human actions.
- Started T1 recovery with a current-main verifyFast runner and an independent read-only review of legacy T1. Product source on main matches the parent of legacy T1; selectively reuse reviewed changes rather than replaying old central records.
- **Next:** import and verify T1, then review and recover T2; preserve the unresolved separate perf dependency before new feature work.

### 2026-10-26 — request the successor M5 assignment

- The owner merged release PR #18 as cb4044b and requested adoption to continue. Fast-forwarded this branch to the merged main without rewriting history.
- Added the M5 assignment request for o-e-lytvynskyi. The released lev1pes row and both historical branches are preserved. No code, build surface, thresholds or measurement baselines are changed.
- Assignment assessment: M0-M4 are merged, the legacy T1-T2 are recoverable within M5, and M6 is outside this claim. The separate fix-main investigation still belongs to lev1pes; reconciliation can precede new feature work, which remains blocked by its unresolved perf failures.
- **Next:** obtain a human merge of this assignment, then review and selectively import T1-T2 with current records and fresh verification. No product implementation started while assignment is pending.

### 2026-10-26 — inspect M5 and prepare owner-directed takeover

- The owner explicitly authorized transfer in this session. This is an owner-directed exception to the normal holder-approved release procedure; no approval by lev1pes is asserted.
- Read the developer procedure, security rules, people/claims, standing restrictions, delegation rules, M5 task design and legacy progress. Confirmed the authenticated identity is o-e-lytvynskyi.
- Assignment #15 and fix-main assignment #13 are merged; both assign work rather than deliver implementation. The latest M5 branch commit 6bf30df records a pause after T2 and possible future migration; no T3 implementation exists.
- T1 splits oversized classes and makes failed scene checks fail scenario comparison. T2 implements LARGE clearance and class-specific navigation. Historical full verification was on Windows with an older toolchain; it must not be presented as current Mac validation.
- The original working copy has an existing gradlew.bat line-ending difference. A separate worktree preserves it; the fresh worktree also shows the same checkout normalization difference, excluded from all commits.
- **Next:** merge release, obtain assignment, inspect/import T1-T2 with current records and sanitized measurement paths, run required gates, then implement T3a-T3c. The red perf dependency remains open.

## For the owner

1. Release #18 and successor assignment #19 are merged. No assignment approval remains outstanding for M5.
2. The historical fix-main investigation remains with lev1pes. Release proposal [#20](https://github.com/o-e-lytvynskyi/strategos/pull/20) remains a human decision; no implementation under that claim starts without successor assignment. Current M5 performance is now green, so this proposal no longer pauses ready M5 tasks. Historical Linux CI is not declared repaired.

## Decisions taken

- 2026-10-27 · Spec traceability for CMB-04 check 4 names the existing GameTest `combattests.archer_volley_replays_as_the_core_predicts` (M2's "as the core predicts" convention: the headless replay is the L1 prediction) instead of the planned `a_volley_damages_as_l1_predicts`. Undo by renaming the test and the row.
- 2026-10-27 · Re-pin the builtin template set's SHA (e4624330…) and size 17 for the archer; the canonical form changed only by the appended template. Undo with the archer.
- 2026-10-27 · trial runner: pass — three bounded gate/JMH runs with exact failing tests, pinned values and per-fork evidence; no retries or edits.
- 2026-10-27 · Persist optional shot release origin/tick for future impact feedback across death/restore; preserve old snapshot and FX bytes when the extension is absent. Deliver once to viewers of either endpoint and clear client arcs on match changes; no past-launch resync is added. Undo through a reviewed feedback contract change.

- 2026-10-27 · Derive flat-world target velocity from transient pre-/post-MOVEMENT positions at release. Preserve the existing pose-only flat snapshot/hash contract; terrain still uses MotionStore velocity. Undo by a reviewed motion-state version change. Bound ranged damage to finite float-safe values so accepted templates and modifiers cannot fail when converted to a damage packet.

- 2026-10-26 · Establish first Mac profiles for active-shot hashing and preferred/collateral/miss volley60 cases from the full measured run. All existing profile scores and limits remain unchanged; remove only these four rows to undo. Keep T2's proposed baseline as its historical decision artifact.
- 2026-10-26 · Use available standard worker gpt-6-sol for the unchanged tier-B contract after the earlier model's usage limit; runner and independent reviewer remained available. Trial implementer/reviewer/runner pass: scoped integration, actual continuation tests, representative combat fixture, bounded summaries and unchanged gates verified by the orchestrator.

- 2026-10-26 · Accept the shot golden only after an independent SplitMix64/Box-Muller calculation matched 126/500 hits, first 32 outcomes and exact damage 126/256. The nearest circle-boundary margin is 0.000171829 in squared distance, well above floating-point rounding differences. Undo by replacing the test golden through a reviewed behavior change; statistical bounds and seed stay fixed.
- 2026-10-26 · Use circular body-radius footprints with a body-height check and frozen aim Y to avoid cross-layer hits; preferred full target handle precedes ascending any-faction collateral. This refines the approved landing-point model. Undo by revising the collision policy and its explicit circle/bridge/friendly-collateral tests.

- 2026-10-26 · Continue ready M5 tasks once this branch's full functional and performance gates are green, including both historical failure counterexamples. Keep the separate Linux main investigation assigned and unresolved; its release proposal is optional coordination, not a reason to idle validated M5 work. Undo by pausing features until that separate claim is delivered.
- 2026-10-26 · trial implementer: pass — integration and performance briefs stayed within named source/test files, preserved old query/reload behavior, demonstrated meaningful regression cases and differential terrain checks, and returned focused/full-fast evidence without commits or records edits.
- 2026-10-26 · trial reviewer: pass — found and then verified fixes for eviction, mixed slots and quadratic page access; final cache/signature review has no findings, with the orchestrator reading source/test diffs.
- 2026-10-26 · trial runner: pass — final full gates, isolated controls/diagnostics, targeted perf and full JMH ran serially with bounded summaries; no unrequested retries or baseline edits. Corrected corpus counts distinguish 1,939 functional tests from two extra perf tests.

- 2026-10-26 · Establish TerrainEditBench's two-grid Mac profile at 481.023 us/op after reducing the initial recovery cost from 1027.918, confirming a 290.034 us/op same-host T1 control and passing absolute D (0.427499 ms p99 / 2 ms) and idle ratio (0.218951 / 0.30). This is the new class's measured cost within the planned roughly twofold rebuild; owner delegated recommended baseline decisions under AGENTS.md. Keep the 15% limit and every other existing score/budget unchanged. ADR-0019 and a retained proposal record it; undo by restoring only this benchmark's prior row from 5437a1f. Final full comparison is still required.

- 2026-10-26 · Split mobility recovery and measured performance work; keep acceptance and code delivery atomic so no red intermediate implementation is committed. Undo by collapsing the two progress entries; the gates and feature scope remain unchanged.

- 2026-10-26 · Recover reviewed T1 source and tests selectively; keep historical Windows artifacts in the preserved branch and require fresh current-host evidence. Undo by reverting this recovery commit; no pushed history is rewritten.
- 2026-10-26 · trial runner: pass — baseline, deliberate red tests and final gates ran sequentially with compact verdicts, timings and no unrequested retries; the negative checks failed before the fixes and passed afterwards.
- 2026-10-26 · trial reviewer: pass — reviewed immutable T1 source without edits/builds, verified semantic preservation and scene guard behavior; T2 findings are being validated separately.

- 2026-10-26 · Follow the owner's explicit takeover authorization through a release PR followed by an assignment PR; retain all historical branches and evidence. Undo by rejecting the release or assigning M5 back by PR.
- 2026-10-26 · Prefer selective reuse of legacy T1-T2 over reimplementation, subject to review and current verification. Keep Windows evidence labeled and sanitize personal paths without changing numbers; discard the import if validation reveals an unsuitable design.

## Pitfalls

- Added docs/kb/rts-sim-combat.md: used-empty allocator history, release/movement timing and body/layer impact checks.

- Added docs/kb/rts-sim-large.md: expanded-clearance eviction, class-safe stuck recovery, ordered page notifications, template-cache reloads and complete effective-border signatures.

- Added Python UTF-8 document parsing and empty-discovery validation to docs/kb/minecraft-mod-build.md.

- Existing Windows wrapper line-ending normalization is recorded on the previous fix-main branch; no wrapper or shared settings change is included here.

## Review

Pending independent review. No product code changed and no legacy implementation was built in this preparation.
