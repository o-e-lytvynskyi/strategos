# M5 takeover preparation on dev/o-e-lytvynskyi/m5

**Developer:** o-e-lytvynskyi · **Branch:** dev/o-e-lytvynskyi/m5 · **Work:** M5 takeover preparation · **Base:** main @ 0a7cc3b · **Claimed:** not assigned · **Status:** claimed

## Now

- **Milestone:** M5 Combat; main has 0 of 17 tasks complete. Legacy work records T1-T2 complete and splits T3 into T3a-T3c (19 tasks total).
- **In progress:** owner-authorized release and takeover preparation only; no M5 development is assigned to this branch yet.
- **Last finished task:** no task delivered by this session. Legacy T1 is a13850b; T2 is a545902; T3 planning is e8cf0c2; the legacy tip is bde875d.
- **Next task:** a human merges the release PR; then submit and obtain a merged assignment PR for M5 before changing product code. Reconcile legacy T1-T2 and revalidate on the current toolchain before starting T3a.
- **Merged from main last:** based on 0a7cc3b (2026-10-26).

## Depends on unfinished work

- M0-M4 are merged. No unfinished milestone implementation is required by M5.
- The separate fix-main claim remains assigned to lev1pes. Main perf run 40176248614 failed idle_300_costs_a_third (0.3255060777 versus 0.30) and path_budget_d (p99 50.288387 ms versus 6 ms). No fixes were delivered by its assignment PR. Resolve this dependency before new M5 feature work; this release does not transfer fix-main.
- Preserve codex/m5-combat and dev/lev1pes/m5. Import code and task-specific evidence selectively after assignment; do not copy old central STATE/worklog records over current main.

## Log

### 2026-10-26 — inspect M5 and prepare owner-directed takeover

- The owner explicitly authorized transfer in this session. This is an owner-directed exception to the normal holder-approved release procedure; no approval by lev1pes is asserted.
- Read the developer procedure, security rules, people/claims, standing restrictions, delegation rules, M5 task design and legacy progress. Confirmed the authenticated identity is o-e-lytvynskyi.
- Assignment #15 and fix-main assignment #13 are merged; both assign work rather than deliver implementation. The latest M5 branch commit 6bf30df records a pause after T2 and possible future migration; no T3 implementation exists.
- T1 splits oversized classes and makes failed scene checks fail scenario comparison. T2 implements LARGE clearance and class-specific navigation. Historical full verification was on Windows with an older toolchain; it must not be presented as current Mac validation.
- The original working copy has an existing gradlew.bat line-ending difference. A separate worktree preserves it; the fresh worktree also shows the same checkout normalization difference, excluded from all commits.
- **Next:** merge release, obtain assignment, inspect/import T1-T2 with current records and sanitized measurement paths, run required gates, then implement T3a-T3c. The red perf dependency remains open.

## For the owner

1. Review and merge the release PR. The registry checker requires holder handover text for a direct reassignment; use separate release and assignment PRs rather than invent holder consent or weaken the checker. This preparatory branch record does not grant an assignment.
2. The separate fix-main claim remains with lev1pes and blocks new M5 feature implementation until resolved. Coordinate its completion or explicitly transfer that claim separately.

## Decisions taken

- 2026-10-26 · Follow the owner's explicit takeover authorization through a release PR followed by an assignment PR; retain all historical branches and evidence. Undo by rejecting the release or assigning M5 back by PR.
- 2026-10-26 · Prefer selective reuse of legacy T1-T2 over reimplementation, subject to review and current verification. Keep Windows evidence labeled and sanitize personal paths without changing numbers; discard the import if validation reveals an unsuitable design.

## Pitfalls

- Existing Windows wrapper line-ending normalization is recorded on the previous fix-main branch; no wrapper or shared settings change is included here.

## Review

Pending independent review. No product code changed and no legacy implementation was built in this preparation.
