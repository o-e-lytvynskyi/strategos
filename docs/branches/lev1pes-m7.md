# M7 on dev/lev1pes/m7

**Developer:** lev1pes · **Branch:** dev/lev1pes/m7 · **Work:** M7 · **Base:** main @ 267da40 · **Claimed:** 2026-10-29 · **Status:** in progress

## Now

- **Milestone:** M7 Multiplayer; 1 of 26 implementation steps complete. Specification: [M7](../milestones/M7-multiplayer.md), based on planning PR #32.
- **In progress:** T2a independent identity/diplomacy/grant foundations since 2026-10-29; explicit operator exception permits pre-merge development.
- **Last finished task:** T1 readiness reconciliation (2026-10-29), cloud-verified checkpoint 40e770d.
- **Next task:** T2a independent core identity/diplomacy/grant foundations; T2b installs authority with coordinated persistence.
- **Merged from main last:** 267da40 (2026-10-29).

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
| Save/reload PR #30 | Stable template references and terminal-state restoration fixes | Independent core foundation first; after merge, reconcile persistence against the delivered schema rather than fork it | List unresolved integration in T1/T5 |

Initial order: T1 readiness, T2 pure core foundations, then reassess T3/T5/T6 and later tasks
against merged prerequisites. Split oversized or partially ready tasks in the specification
before implementing them. T2 does not certify friendly-fire, allied victory, command adapters
or replication privacy; those retain their own integration tests and dependencies.

## Log

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
2. Planning PR #32 is still open. Its specification is incorporated and updated here; reconcile duplicate planning delivery on review; this request authorizes available M7 work, but does not resolve D-20 hosting, D-03 themes, deferred first-person fighting/scouting, the MVP fun verdict or art acceptance. Continue independent foundations while these gates remain open.
3. Later two-client measurement and acceptance require the owner's clients and the completed hardening prerequisites. No platform-port decision, public release or performance baseline change is included.

## Decisions taken

- 2026-10-29 · Make the standalone diplomacy state immutable and use it as the read view — old query/snapshot references cannot observe later mutation; canonical snapshots and a hash contribution are tested separately from match-schema integration — replace the representation behind DiplomacyView if measured setup costs justify it.
- 2026-10-29 · Grants are faction-owned, primary-only and non-transitive; rebind/team changes revoke stale authority — prevents a reused seat or alliance change from retaining old control — change the explicit policy and regression tests together if the product model changes. Disconnect does not unbind.

- 2026-10-29 · Keep whole-milestone ownership as requested, with per-task readiness gates — prevents competing claims without pretending missing dependencies are delivered — release or narrow the assignment through the normal registry PR if ownership changes.
- 2026-10-29 · Start with independent core identity/diplomacy/grant foundations after readiness reconciliation — they can be tested without unmerged M6 runtime code — revisit the order when prerequisites merge.

## Pitfalls

- Use `python -X utf8` for bookkeeping on Windows; a legacy-code-page run can misleadingly report zero cards. Existing toolchain KB documents this.

## Review

Pending independent review.
