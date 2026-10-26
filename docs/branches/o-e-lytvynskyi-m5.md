# M5 on dev/o-e-lytvynskyi/m5

**Developer:** o-e-lytvynskyi · **Branch:** dev/o-e-lytvynskyi/m5 · **Work:** M5 · **Base:** main @ cb4044b · **Claimed:** 2026-10-26 · **Status:** claimed

## Now

- **Milestone:** M5 Combat; main has 0 of 17 tasks complete. Legacy work records T1-T2 complete and splits T3 into T3a-T3c (19 tasks total).
- **In progress:** assignment request only; release PR #18 is merged. No product development before this assignment is merged.
- **Last finished task:** no task delivered by this session. Legacy T1 is a13850b; T2 is a545902; T3 planning is e8cf0c2; the legacy tip is bde875d.
- **Next task:** after a human merges this assignment, reconcile legacy T1-T2 with current main and validate the import on the current toolchain. Resolve the separate red perf dependency before starting new M5 features (T3a).
- **Merged from main last:** cb4044b (2026-10-26), including the approved release in #18.

## Depends on unfinished work

- M0-M4 are merged. No unfinished milestone implementation is required by M5.
- The separate fix-main claim remains assigned to lev1pes. Main perf run 40176248614 failed idle_300_costs_a_third (0.3255060777 versus 0.30) and path_budget_d (p99 50.288387 ms versus 6 ms). No fixes were delivered by its assignment PR. Resolve this dependency before new M5 feature work; this release does not transfer fix-main.
- Preserve codex/m5-combat and dev/lev1pes/m5. Import code and task-specific evidence selectively after assignment; do not copy old central STATE/worklog records over current main.

## Log

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

1. Release PR #18 is merged. Review and merge this successor assignment PR; development starts only after it is merged. No further holder approval is required following the owner-authorized release.
2. The separate fix-main claim remains with lev1pes and blocks new M5 feature implementation until resolved. Coordinate its completion or explicitly transfer that claim separately.

## Decisions taken

- 2026-10-26 · Follow the owner's explicit takeover authorization through a release PR followed by an assignment PR; retain all historical branches and evidence. Undo by rejecting the release or assigning M5 back by PR.
- 2026-10-26 · Prefer selective reuse of legacy T1-T2 over reimplementation, subject to review and current verification. Keep Windows evidence labeled and sanitize personal paths without changing numbers; discard the import if validation reveals an unsuitable design.

## Pitfalls

- Existing Windows wrapper line-ending normalization is recorded on the previous fix-main branch; no wrapper or shared settings change is included here.

## Review

Pending independent review. No product code changed and no legacy implementation was built in this preparation.
