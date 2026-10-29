# m6-t2-t17 on dev/dyadik/m6-t2-t17

**Developer:** dyadik (Claude Code, Opus) · **Branch:** dev/dyadik/m6-t2-t17 · **Work:** M6 T2–T17 · **Base:** main @ 1dae9fa · **Claimed:** 2026-10-25 · **Status:** claimed

**Note:** assignment merged in [PR #17](https://github.com/o-e-lytvynskyi/strategos/pull/17); implementation not merged; opening M6 remains an owner decision

## Now

- **Milestone:** M6 MVP: First skirmish, 0 of 27 tasks done; spec [M6-first-skirmish.md](../milestones/M6-first-skirmish.md).
- **In progress:** none (the claim waits for «open M6 for claims» and a maintainer's merge).
- **Last finished task:** none.
- **Next task:** M6 T2 · TECH-01, the research system.
- **Merged from main last:** 1dae9fa (2026-10-25).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 Combat (assigned to lev1pes, not started) | T12 (MAP-02): the horseman scout (UNIT-07's `unit/horseman`) and the large class's reachability (PATH-11). Nothing else in T2–T17 | independent: T2–T11 and T13–T17 need nothing from M5 (spec §7.1); T12 against a contract: the manifest names the horseman, and the checker's per-class reachability covers the classes that exist, with the large class added when PATH-11 lands | the spec's AFTER-M5 lines in §2.10; code markers added in T12 |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-10-25 — M6 T2–T17 claim

- **Claim** — M6 T2–T17, the part of M6 that needs nothing from M5, claimed after the M6 spec merged (#16). M6 is not yet in STATE "Open for claims": the claim asks the owner to open it. The developer's restriction for this session: no client windows; L4 runs go to the consent batch (T27).
- **Next:** after the opening and the merge, T2.

## For the owner

1. **Opening.** «open M6 for claims»: this claim covers T2–T17 only; T1 and T18–T28 wait for M5.
2. **Approval: [ADR-0025](../adr/0025-commands-into-unexplored-cells.md)** (Proposed, from the M6 spec): moves into unexplored cells. Blocks T6's move rule, nothing before it.

## Decisions taken

- 2026-10-25 · The claim takes T2–T17 and leaves T1 and T18–T28 — they use M5's classes, and work that can only follow M5 is not claimed yet (AGENTS.md) — widen it by a new claim once M5 lands.

## Pitfalls

New pitfalls go straight into `docs/kb/` on the branch; list here which entries were added.

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
