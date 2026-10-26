# M5 on dev/o-e-lytvynskyi/m5

**Developer:** o-e-lytvynskyi · **Branch:** dev/o-e-lytvynskyi/m5 · **Work:** M5 · **Base:** main @ cb4044b · **Claimed:** 2026-10-26 · **Status:** in progress

## Now

- **Milestone:** M5 Combat; this branch has 1 of 18 tasks complete after splitting T2 into recovery and measured performance work. Legacy work records T1-T2 complete and splits T3 into T3a-T3c (19 tasks total).
- **In progress:** M5 T2a–T2b since 2026-10-26; functional gates pass, but JMH is red and performance work is the only implementation priority.
- **Last finished task:** recovered T1 (2026-10-26); legacy source a13850b. T2 source is a545902; T3 planning is e8cf0c2; legacy tip bde875d.
- **Next task:** recover T2, fix review findings with regressions, then run full movement gates and JMH. Resolve the separate red perf dependency before new features (T3a).
- **Merged from main last:** b207e50 (2026-10-26), including assignment #19.

## Depends on unfinished work

- M0-M4 are merged. No unfinished milestone implementation is required by M5.
- The separate fix-main claim remains assigned to lev1pes. Main perf run 40176248614 failed idle_300_costs_a_third (0.3255060777 versus 0.30) and path_budget_d (p99 50.288387 ms versus 6 ms). No fixes were delivered by its assignment PR. Resolve this dependency before new M5 feature work; this release does not transfer fix-main.
- Preserve codex/m5-combat and dev/lev1pes/m5. Import code and task-specific evidence selectively after assignment; do not copy old central STATE/worklog records over current main.

## Log

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
2. The separate fix-main claim remains with lev1pes and blocks new M5 feature implementation until resolved. Coordinate its completion or explicitly transfer that claim separately.

## Decisions taken

- 2026-10-26 · Split mobility recovery and measured performance work; keep acceptance and code delivery atomic so no red intermediate implementation is committed. Undo by collapsing the two progress entries; the gates and feature scope remain unchanged.

- 2026-10-26 · Recover reviewed T1 source and tests selectively; keep historical Windows artifacts in the preserved branch and require fresh current-host evidence. Undo by reverting this recovery commit; no pushed history is rewritten.
- 2026-10-26 · trial runner: pass — baseline, deliberate red tests and final gates ran sequentially with compact verdicts, timings and no unrequested retries; the negative checks failed before the fixes and passed afterwards.
- 2026-10-26 · trial reviewer: pass — reviewed immutable T1 source without edits/builds, verified semantic preservation and scene guard behavior; T2 findings are being validated separately.

- 2026-10-26 · Follow the owner's explicit takeover authorization through a release PR followed by an assignment PR; retain all historical branches and evidence. Undo by rejecting the release or assigning M5 back by PR.
- 2026-10-26 · Prefer selective reuse of legacy T1-T2 over reimplementation, subject to review and current verification. Keep Windows evidence labeled and sanitize personal paths without changing numbers; discard the import if validation reveals an unsuitable design.

## Pitfalls

- Added Python UTF-8 document parsing and empty-discovery validation to docs/kb/minecraft-mod-build.md.

- Existing Windows wrapper line-ending normalization is recorded on the previous fix-main branch; no wrapper or shared settings change is included here.

## Review

Pending independent review. No product code changed and no legacy implementation was built in this preparation.
