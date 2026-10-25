# m6-t2-t17 on dev/dyadik/m6-t2-t17

**Developer:** dyadik (Claude Code, Opus) · **Branch:** dev/dyadik/m6-t2-t17 · **Work:** M6 T2–T17 · **Base:** main @ 1dae9fa · **Claimed:** 2026-10-25 · **Status:** in progress

## Now

- **Milestone:** M6 MVP: First skirmish, 1 of 28 tasks done (T2 split into T2.1 and T2.2); spec [M6-first-skirmish.md](../milestones/M6-first-skirmish.md).
- **In progress:** M6 T2.2 since 2026-10-25.
- **Last finished task:** M6 T2.1 (2026-10-25), the commit `M6 T2.1: technologies as content`.
- **Next task:** M6 T2.2 · TECH-01, research.
- **Merged from main last:** 0a7cc3b (2026-10-25).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 Combat (assigned to lev1pes, not started) | T12 (MAP-02): the horseman scout (UNIT-07's `unit/horseman`) and the large class's reachability (PATH-11). Nothing else in T2–T17 | independent: T2–T11 and T13–T17 need nothing from M5 (spec §7.1); T12 against a contract: the manifest names the horseman, and the checker's per-class reachability covers the classes that exist, with the large class added when PATH-11 lands | the spec's AFTER-M5 lines in §2.10; code markers added in T12 |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-10-25 — M6 T2–T17 claim

- **M6 T2.1 · TECH-01, the content** — TECH-01 `in_progress`. `TechDef`, `TechEffect`, `Requirement` with `Requirements` and `FactionView`, `TechId`, the append-only `TechIdTable`; `strategos/techs/` loaded beside the templates; the techs in the set's hash under `$techs` only when present (a set without techs keeps its sha, pinned); `TemplateSetView` carries them (spec §1.6 row 24); the sample `strategos:gather_wood_1` (wood ×1.2 for workers, 100 food 50 wood, 30 s) at the storehouse, with provenance. New: `RequirementTest`, `TechDefTest` (15 malformed cases), `TemplateSetViewTest`; the built-in set's pin moved to 3f019e66. `verify` green in 1 min 50 s (211 GameTests).
- **Claim merged** — #17 (0a7cc3b), merged by the owner; its body asked for «open M6 for claims», and STATE's "Open for claims" is not yet updated (the maintainer's record). ADR-0025 is still Proposed: it blocks only T6's move rule.
- **T2 split** into T2.1 (the content) and T2.2 (research) before work, from the implementation map: the spec's §1.6 rows 18–23 record what the code showed (queue entries, Village without a tech, stable tech ids, techs in the set's hash, modifiers at restore, the card's token lists).
- **Claim** — M6 T2–T17, the part of M6 that needs nothing from M5, claimed after the M6 spec merged (#16). M6 is not yet in STATE "Open for claims": the claim asks the owner to open it. The developer's restriction for this session: no client windows; L4 runs go to the consent batch (T27).
- **Next:** T2.2.

## For the owner

1. **Opening.** «open M6 for claims»: this claim covers T2–T17 only; T1 and T18–T28 wait for M5.
2. **Approval: [ADR-0025](../adr/0025-commands-into-unexplored-cells.md)** (Proposed, from the M6 spec): moves into unexplored cells. Blocks T6's move rule, nothing before it.

## Decisions taken

- 2026-10-25 · A view with techs marks its version `+t` on the wire (spec row 24) — journals are unframed, and old streams must read unchanged — a framed view with a format byte, at the cost of every committed journal.
- 2026-10-25 · The syntax of class filters and token edits lives once, in `content.StatSyntax`; `ClassFilter.check` and `TokenEdit.valid` call it — the content layer may not import `sim` (ArchUnit B3), and the worker's first cut copied the checks — copy them back into `sim.stats`.
- 2026-10-25 · A tech's `name` is a lang key (`strategos.tech.gather_wood_1`, in `en_us.json`), as templates' are — the client translates it — a literal name.
- 2026-10-25 · trial implementer (general-purpose on Sonnet, the role file): pass — T2.1 green on its first run; one deviation (copied syntax checks) found in the diff and folded into one source; it reported the tests were written with the code, not before.
- 2026-10-25 · The claim takes T2–T17 and leaves T1 and T18–T28 — they use M5's classes, and work that can only follow M5 is not claimed yet (AGENTS.md) — widen it by a new claim once M5 lands.

## Pitfalls

New pitfalls go straight into `docs/kb/` on the branch; list here which entries were added.

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
