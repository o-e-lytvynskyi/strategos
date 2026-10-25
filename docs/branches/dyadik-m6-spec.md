# m6-spec on dev/dyadik/m6-spec

**Developer:** dyadik (Claude Code, Opus) · **Branch:** dev/dyadik/m6-spec · **Work:** M6 spec · **Base:** main @ 046f021 · **Claimed:** 2026-10-25 · **Status:** in progress

## Now

- **Milestone:** M6 MVP: First skirmish, the implementation spec (planning): [M6-first-skirmish.md](../milestones/M6-first-skirmish.md), 27 tasks; the 23 cards `specified`.
- **In progress:** M6 spec since 2026-10-25.
- **Last finished task:** none.
- **Next task:** hand in the M6 spec (§12).
- **Merged from main last:** cb18b94 (2026-10-25).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 Combat (specified, not started) | UNIT-06 and PATH-11 for CMB-09 (the ram, T19) and MAP-02's large-class reachability and horseman scout (T12); CMB-07 for BLD-17 (T20); UNIT-07 for TECH-03's roster tiers (T18) and the counters in the MVP regression suite; AI-03 and BLD-16 for AI-04 (T21); PERF-08's battle base for PERF-09 (T24); M5's close follow-ups for T1 | against a contract: the spec designs on M5's spec (§0.1); T2–T13 need nothing from M5 and may start before it (§7.1) | the spec's `AFTER-M5` lines (§0.1, §2.10, §2.16–§2.19, §2.22, §7.2 T1, §8 row 7): check each against M5's code and Results when M5 lands, and write T1 from M5's close |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-10-25 — M6 spec

- **M6 spec written** — verify green (2 min 54 s, 84 tasks); [M6-first-skirmish.md](../milestones/M6-first-skirmish.md): 23 cards traced to 27 tasks (T1–T25, T27, T28; T26 reserved), 17 differences resolved (§1.6), 8 risks, ADR-0025 proposed; reviewed before commit (22 findings applied); the cards `approved` → `specified` in ROADMAP.md and the card file; `roadmap.py check` 245 cards, 0 problems. Inputs: the cards, 04, the ADRs, the M5 spec as a contract, `main` @ cb18b94. Engine names checked in the NeoForge 21.1.264 sources (§0.4); `verifyFast` green on the branch (4 min 06 s, the first run with a cold cache). No client window opened.

- **Claim merged** — PR #11 (f723161); `origin/main` merged at cb18b94.
- **Claim** — M6 spec claimed as a collaborator; dependencies on M5 assessed above. The developer's restriction for this session: no client windows; window runs go into the spec's consent batch.
- **Next:** write docs/milestones/M6-first-skirmish.md.

## For the owner

1. **Opening.** M6 is specified: «open M6 for claims». T2–T13 need nothing from M5 (§7.1), so M6 may open before M5 closes; T1 waits for M5's close.
2. **Approval: [ADR-0025](../adr/0025-commands-into-unexplored-cells.md)** (Proposed). Solo fog needs moves into unexplored cells for scouting, which ADR-0014 and 04 §7.3 forbid; the ADR keeps the explored check for placement, node targets and the observer. Options: accept (recommended); keep ADR-0014 as it is, so scouting moves only to the edge of explored land. Blocks M6 T6's move rule, nothing before it.
3. **Owner inputs for later** (spec §10): the first map's hand-finishing with a date (after T12); the MVP perf baseline on the M4 Pro (T24); the goldens (T27); the MVP playtests.

## Decisions taken

- 2026-10-25 · Phases are technologies with the class `Phase`; `phase_village` researched at tick 0 (spec §1.6 row 1) — the cards say so, the architecture is silent — change §2.2.
- 2026-10-25 · Moves, attack-moves and rally points may target unexplored cells; placement, node targets and the observer still need explored ones (row 2) — scouting needs it — written as ADR-0025, Proposed, for the owner (item 2).
- 2026-10-25 · No `ping` payload in M6; alert pings are drawn from `match_event` (row 3) — UI-15 (M7) owns multiplayer pings — add the payload to T16.
- 2026-10-25 · Equipment tiers ride `match_state` as faction values, no re-pair (row 5) — fewer bytes and no tracker churn — switch to spawn data in T18.
- 2026-10-25 · The jar's map rule becomes an allowlist of shipped maps (row 7) — the released jar has no playable map otherwise — revert T12's build change.
- 2026-10-25 · Easy, Normal and Hard take no bonus; Hard becomes a labelled top level only if AI-05.2 fails fair (row 9, R6) — D-19 — label Hard now.
- 2026-10-25 · A skirmish starts through a C2S `strategos:skirmish` payload on an integrated server, a command on a dedicated one (row 10) — a command needs a match — change §2.11.
- 2026-10-25 · Conquest-critical: units and buildings past FOUNDATION, without `Field` and `Decoration` (row 11) — a foundation must not stall a match — count foundations in §2.3.
- 2026-10-25 · A player in no match keeps seeing every body, as M1 decided (row 15) — solo play has no such viewer; the non-participant rule waits for multiplayer — hide them in `FogGate`.
- 2026-10-25 · "Stone needs the ram" read as a balance target: two rams within 60 s, torches no less than 5 minutes (row 14) — D-12 lets stone burn — change the sheet rows.
- 2026-10-25 · M6's command ids from 31 and Rng streams from 10, leaving M5 its 25–30 and 2–9 (§0.3) — M5 is unbuilt — renumber when M5 lands.
- 2026-10-25 · trial Explore (built-in, read-only): pass — three parallel inventories (architecture, core, mod) with `path:line` facts; spot-checked against the code (`CancelTrain`, `PlayRules`, `ScenarioRunner`).
- 2026-10-25 · trial general-purpose as reviewer (the spec before commit): pass — 22 findings, no blocker; the 13 should-fix and 9 nits checked against the code and ADRs and applied (ADR-0025, rows 13 and 15–17, §4 per level, the task dependencies, `MvpScene`, the AI script cap).
- 2026-10-25 · Handle `dyadik` for GitHub @dyadik9616, kept after the procedure moved to handles equal to GitHub logins (6c4db24) — the owner merged the claim and people.md with `dyadik`, and a rename means a new branch and a new claim — rename on the next claim if the owner asks.

## Pitfalls

New pitfalls go straight into `docs/kb/` on the branch; list here which entries were added.

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
