# M7 spec on dev/lev1pes/m7-spec

**Developer:** lev1pes · **Branch:** dev/lev1pes/m7-spec · **Work:** M7 spec · **Base:** main @ 3c1f4300bd457788374c74fe4ddbfc2679f17ec4 · **Claimed:** 2026-10-28 · **Status:** merged 2026-10-28

**Note:** specification merged 2026-10-28 in [PR #32](https://github.com/o-e-lytvynskyi/strategos/pull/32); planning only, requested by the owner; task/dependency matrix, multiplayer contracts and verification plan; no implementation claim or milestone opening

## Now

- **Milestone:** M7 Multiplayer specification only; no implementation tasks claimed.
- **In progress:** none; planning delivery complete, awaiting independent review.
- **Last finished task:** [M7 planning specification](../milestones/M7-multiplayer.md): 25 future tasks, all 13 cards traced; cloud verify passed on ea45a9c with 1,906 test cases and no failures, errors or skips.
- **Next task:** independent review of [PR #32](https://github.com/o-e-lytvynskyi/strategos/pull/32), owner scope/decision review and human merge. No M7 runtime implementation is claimed or opened.
- **Merged from main last:** dcb5013 (2026-10-29), conflict resolution for PR #32.

## Depends on unfinished work

| Milestone | What this work needs from it | Plan: independent / against a contract / after | AFTER markers |
|---|---|---|---|
| M5 | Combat events, projectile channels and player authority interactions relevant to network hardening | Independent specification from approved cards and architecture; record runtime validation after M5, without building or changing its branch | AFTER-M5: combat-channel integration and hardening validation |
| M6 | AI-05 for AI-06, UI-20 for UI-15, QA-13 for ART-03, PERF-09 for PERF-10, and the solo-fog/observed-AI interfaces used by multiplayer | Against the merged M6 specification's contracts; identify adapters and later validation explicitly; planning does not require runtime delivery | AFTER-M6: AI/HUD/fog adapters, release and performance prerequisites |
| M8 T1–T6 | Shared-vision, audience and observed-information contracts on our separate foundation branch | Independent M7 specification; inventory the contract boundaries and reconciliation work without copying or claiming unfinished implementation | AFTER-M8: reconcile shared-vision and recipient contracts after delivery |

Existing main contracts cover the earlier match, core, replication and test infrastructure. The M7 specification will trace every card dependency, separate independent work from integration work, and retain all acceptance criteria. Actual integration and runtime verification belong to later implementation claims.

## Log

### 2026-10-29 — resolve PR #32 merge conflict

- Merged current main dcb5013 into the planning delivery. The only conflict was the claims table: retained all current main statuses and historical PR references, then preserved this branch's planning-only M7 specification assignment. No implementation assignment or milestone opening was added.
- Runtime sources merged automatically; the conflict resolution changes documentation only. The resulting PR diff against main remains limited to five documentation files.
- Verification: 12 bookkeeping-tool tests passed; claims check against origin/main passed (8 rows); roadmap check passed (245 cards); git diff --check passed. The sandbox blocked temporary test directories, so the identical tool suite was run outside it and passed. Full Gradle verification was not repeated for this documentation-only resolution; prior cloud evidence remains recorded below.

### 2026-10-28 — cloud verification and handoff

- [GitHub Actions verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40773257776) passed on ea45a9c: `BUILD SUCCESSFUL in 6m 25s`, 84 executed tasks. The canonical XML reports contain 1,906 test cases (including 212 GameTests), zero failures, zero errors and zero skips; L3 wall time 72.958 s against the unchanged 180 s limit. Harness self-tests also passed through `verify`.
- `records`, test-results reporting and the repository security check passed. The final follow-up commit changes only this branch's status/evidence record; implementation plan and runtime sources are unchanged from the cloud-verified commit.
- PR #32 now presents the combined assignment and completed planning specification, with the owner's pre-merge planning instruction disclosed. The planning claim is ready for independent review; the 25 implementation checkboxes remain unchecked.

### 2026-10-28 — planning specification

- Following the owner's explicit instruction to do the work and then send the PR, prepared the specification before assignment merge. PR #32 will become the combined assignment and planning delivery; the procedural exception does not authorize a merge or runtime implementation.
- Designed separate player/faction identity, co-op control, diplomacy/victory, lobby, recipient privacy, reconnect, fuzzing, commander hardening, team UI/AI and S11/PERF-10. Every card has task and acceptance traceability; QA-14 remains floating and ART-03 retains its MVP/theme gates.
- Inspected current roster/ownership/replication/gateway/test seams and checked commander hook signatures in cached Minecraft 1.21.1 + NeoForge 21.1.264 sources. No Minecraft API or runtime implementation was added.
- Card statuses remain proposed; the roadmap/card pages link the planning specification. No STATE, worklog, accepted ADR or product decision changed.
- Reconciled the co-op demo's fighting/scouting prose with D-08: the existing first-person observer remains the baseline; fighting/scouting requires an owner scope decision because CMB-08 is deferred.
- Local bookkeeping: UTF-8 roadmap check covers 245 cards with zero problems; claim check covers eight rows with zero problems. Additional document checks cover all 13 cards, 25 tasks with matching progress entries, acyclic explicit task dependencies and resolving local links. `git diff --check` passed.
- Local `verifyFast verify` was interrupted at the owner's request to run checks in the cloud; it is not a passing full-gate result. GitHub Actions `verify.yml` will validate the pushed specification commit via workflow dispatch; no workflow or threshold change is needed.

### 2026-10-28 — assignment request

- The owner requested the M7 spec claim. The fetched main registry has no M7 assignment or reservation; the open PR list has no competing M7 claim.
- Scope: plan all M7 cards, define implementation task boundaries and dependencies, multiplayer contracts, security checks, test levels and handoff evidence. QA-14 keeps its floating timing; ART-03 keeps its MVP and owner-decision prerequisites.
- This PR changes only the claim row and this branch file. No specification, production code, tests, roadmap status or maintainer records are changed.

## For the owner

1. Review the combined assignment/planning delivery in PR #32. The M7 implementation plan is specified; approve scope and explicitly open ready tasks for claims when appropriate. Card statuses remain proposed until then.
2. D-20 hosting and D-03 civilisation theme remain owner decisions; confirm the applicable D-04 style direction and name owner asset tasks before ART-03. The specification documents recommendations and blocked implementation tasks without deciding them.

## Decisions taken

- 2026-10-28 · Claim planning only on a separate branch from main — keeps ownership bounded and independent of existing deliveries — change scope only through a reviewed claim update.
- 2026-10-28 · Complete planning before assignment merge on the owner's explicit session instruction — deliver a combined assignment/specification PR — the owner may return it to assignment-only by deferring the specification commit.

## Pitfalls

- No new KB entry: the UTF-8 bookkeeping requirement is already documented in `docs/kb/minecraft-mod-build.md` and was followed for final checks.

## Review

Cloud validation passed as recorded above. Awaiting independent maintainer review and human merge of the planning delivery.
