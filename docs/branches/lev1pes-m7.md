# M7 on dev/lev1pes/m7

**Developer:** lev1pes · **Branch:** dev/lev1pes/m7 · **Work:** M7 · **Base:** main @ 267da40 · **Claimed:** 2026-10-29 · **Status:** claimed

## Now

- **Milestone:** M7 Multiplayer; 0 of 25 implementation tasks complete. Planning specification: [PR #32](https://github.com/o-e-lytvynskyi/strategos/pull/32).
- **In progress:** none; assignment merge pending.
- **Last finished task:** none.
- **Next task:** after independent assignment review and human merge, T1 readiness reconciliation, then T2 independent core identity, diplomacy, locked teams and explicit control grants.
- **Merged from main last:** 267da40 (2026-10-29).

The operator requested ownership of the whole M7 and work on what is currently possible on
2026-10-29. This assignment intentionally includes responsibility for later blocked tasks;
it does not declare them ready, waive their dependencies or accept any product decision.
Only ready work or explicitly bounded contract foundations may start after assignment merge.
Reconcile the planning delivery in PR #32 before runtime edits; do not silently import its
unmerged claim or treat its planning checks as implementation evidence.

## Depends on unfinished work

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 | Combat authority, final damage, projectile output and close findings | Independent: combat delivered by PR #33 at the base; inspect current implementation during T1 | None required for merged M5 |
| M6 MATCH-02 / MATCH-03 | Production start pipeline and terminal outcome/snapshot integration | Against a contract for pure rules; after for production lobby start, coalition victory and restore validation | AFTER-M6: start, outcome and schema adapters when introduced |
| M6 AI-05 / AI-18 / UI-20 / solo fog | Observed AI inputs, difficulty, minimap and authoritative visibility | Against a contract only where fakes prove independent behavior; after for runtime AI, HUD and hidden-body integration | AFTER-M6: AI, HUD and visibility adapters when introduced |
| M6 QA-13 / PERF-09 | MVP fun verdict and accepted performance reference | After: ART-03 start and final PERF-10 acceptance; do not substitute provisional results | AFTER-M6: art and final performance prerequisites |
| M8 foundations | Shared-vision and recipient policy contracts | Independent M7 identity/grant rules; reconcile contracts without importing or changing the separate M8 branch | AFTER-M8: shared-vision reconciliation when introduced |
| Save/reload PR #30 | Stable template references and terminal-state restoration fixes | Independent core foundation first; after merge, reconcile persistence against the delivered schema rather than fork it | List unresolved integration in T1/T5 |

Initial order: T1 readiness, T2 pure core foundations, then reassess T3/T5/T6 and later tasks
against merged prerequisites. Split oversized or partially ready tasks in the specification
before implementing them. T2 does not certify friendly-fire, allied victory, command adapters
or replication privacy; those retain their own integration tests and dependencies.

## Log

### 2026-10-29 — whole-milestone assignment request

- The operator requested M7 ownership and available work. Fetched main at 267da40 includes M5 delivery PR #33. The main registry has no M7 implementation holder or reservation; the open PR list contains planning PR #32, save/reload PR #30 and records PR #29, with no competing M7 implementation claim.
- Prepared an assignment-only change: this branch record and one registry row. No runtime, tests, roadmap statuses, STATE or worklog changes.
- Validation: UTF-8 claim check covers 8 rows with zero problems; roadmap check covers 245 cards with zero problems; `git diff --check` passes. No Gradle run is needed for this assignment-only change; no implementation gate is claimed.
- Reviewed the 25-task planning breakdown and separated independent core rules from missing M6 adapters, pending persistence fixes, owner decisions and real-client acceptance.
- **Next:** independent claim review and human merge, then T1/T2; implementation remains unstarted.

## For the owner

1. Independently review and merge the assignment PR. Project procedure requires assignment on main before development; this session does not approve or merge its own work.
2. Planning PR #32 is still open. Reconcile its scope/status on review; this request authorizes available M7 work, but does not resolve D-20 hosting, D-03 themes, deferred first-person fighting/scouting, the MVP fun verdict or art acceptance. Continue independent foundations while these gates remain open.
3. Later two-client measurement and acceptance require the owner's clients and the completed hardening prerequisites. No platform-port decision, public release or performance baseline change is included.

## Decisions taken

- 2026-10-29 · Keep whole-milestone ownership as requested, with per-task readiness gates — prevents competing claims without pretending missing dependencies are delivered — release or narrow the assignment through the normal registry PR if ownership changes.
- 2026-10-29 · Start with independent core identity/diplomacy/grant foundations after readiness reconciliation — they can be tested without unmerged M6 runtime code — revisit the order when prerequisites merge.

## Pitfalls

- Use `python -X utf8` for bookkeeping on Windows; a legacy-code-page run can misleadingly report zero cards. Existing toolchain KB documents this.

## Review

Pending independent review.
