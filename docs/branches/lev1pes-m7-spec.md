# M7 spec on dev/lev1pes/m7-spec

**Developer:** lev1pes · **Branch:** dev/lev1pes/m7-spec · **Work:** M7 spec · **Base:** main @ 3c1f4300bd457788374c74fe4ddbfc2679f17ec4 · **Claimed:** 2026-10-28 · **Status:** claimed

## Now

- **Milestone:** M7 Multiplayer specification only; no implementation tasks claimed.
- **In progress:** none; assignment-only PR awaits human merge before specification work.
- **Last finished task:** claim availability and preliminary dependency assessment.
- **Next task:** after assignment merge, write the M7 implementation specification with card-to-task coverage, dependency ordering, contracts, acceptance evidence and required test gates.
- **Merged from main last:** 3c1f430 (2026-10-28), branch creation base.

## Depends on unfinished work

| Milestone | What this work needs from it | Plan: independent / against a contract / after | AFTER markers |
|---|---|---|---|
| M5 | Combat events, projectile channels and player authority interactions relevant to network hardening | Independent specification from approved cards and architecture; record runtime validation after M5, without building or changing its branch | AFTER-M5: combat-channel integration and hardening validation |
| M6 | AI-05 for AI-06, UI-20 for UI-15, QA-13 for ART-03, PERF-09 for PERF-10, and the solo-fog/observed-AI interfaces used by multiplayer | Against the merged M6 specification's contracts; identify adapters and later validation explicitly; planning does not require runtime delivery | AFTER-M6: AI/HUD/fog adapters, release and performance prerequisites |
| M8 T1–T6 | Shared-vision, audience and observed-information contracts on our separate foundation branch | Independent M7 specification; inventory the contract boundaries and reconciliation work without copying or claiming unfinished implementation | AFTER-M8: reconcile shared-vision and recipient contracts after delivery |

Existing main contracts cover the earlier match, core, replication and test infrastructure. The M7 specification will trace every card dependency, separate independent work from integration work, and retain all acceptance criteria. Actual integration and runtime verification belong to later implementation claims.

## Log

### 2026-10-28 — assignment request

- The owner requested the M7 spec claim. The fetched main registry has no M7 assignment or reservation; the open PR list has no competing M7 claim.
- Scope: plan all M7 cards, define implementation task boundaries and dependencies, multiplayer contracts, security checks, test levels and handoff evidence. QA-14 keeps its floating timing; ART-03 keeps its MVP and owner-decision prerequisites.
- This PR changes only the claim row and this branch file. No specification, production code, tests, roadmap status or maintainer records are changed.

## For the owner

1. Merge the assignment-only PR to register this planning claim. Specification work waits for that merge.
2. D-20 hosting and D-03 civilisation theme remain owner decisions. The specification can document recommended options and their blocked implementation tasks; this claim does not decide them or open M7 implementation.

## Decisions taken

- 2026-10-28 · Claim planning only on a separate branch from main — keeps ownership bounded and independent of existing deliveries — change scope only through a reviewed claim update.

## Pitfalls

- None added; this is an assignment-only change.

## Review

Awaiting independent maintainer review and human assignment merge.
