# m6-t2-t17 on dev/dyadik/m6-t2-t17

**Developer:** dyadik (Claude Code, Opus) · **Branch:** dev/dyadik/m6-t2-t17 · **Work:** M6 T2–T17 · **Base:** main @ 1dae9fa · **Claimed:** 2026-10-25 · **Status:** in progress

## Now

- **Milestone:** M6 MVP: First skirmish, 3 of 28 tasks done (T2 split into T2.1 and T2.2); spec [M6-first-skirmish.md](../milestones/M6-first-skirmish.md).
- **In progress:** M6 T4 since 2026-10-26.
- **Last finished task:** M6 T3 (2026-10-26), the commit `M6 T3: phases, Village to Town`.
- **Next task:** M6 T4 · MATCH-03, conquest, defeat and statistics.
- **Merged from main last:** 0a7cc3b (2026-10-25).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 Combat (assigned to lev1pes, not started) | T12 (MAP-02): the horseman scout (UNIT-07's `unit/horseman`) and the large class's reachability (PATH-11). Nothing else in T2–T17 | independent: T2–T11 and T13–T17 need nothing from M5 (spec §7.1); T12 against a contract: the manifest names the horseman, and the checker's per-class reachability covers the classes that exist, with the large class added when PATH-11 lands | the spec's AFTER-M5 lines in §2.10; code markers added in T12 |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-10-26 — M6 T3

- **M6 T3 · TECH-02, phases** — TECH-02 `done` (all four criteria). `phase_village` (ordinal 0, never researched) and `phase_town` (ordinal 1, at the civic centre: 500 food, 500 wood, 300 stone, 200 iron, 60 s; five complete `Village` buildings; buildings but fields +10 % max HP and +2 vision); `TechDef` gains `ordinal` and `unlocks`; compile checks for phase ids, ordinals and unlock ids; `Techs.phase`; damage stages follow a new max HP; the `PHASE` notice to every participant (`MatchNews` 4) and the client's defeat bug it uncovered; techs in the balance sheet; 04 §12.2's `match_event` row (spec §1.6 rows 29–35). New: `PhaseTest` (8), `MatchNewsTest` (3), `TechDefTest` and `BalanceSheetTest` cases, `techtests.every_player_gets_the_phase_notice` (now through a content swap with Town at 1 s, 400 ticks). A review before the commit found 5 should-fix and 7 nits, all applied. `verify` green, 213 GameTests, in 33 min 12 s under a load average that reached 80 (other processes on the machine; the same gate took 1 min 39 s at the T2.1 review fix).
- **`ProtectedBlockIndexTest.aLookupCostsTheSameAtAnySize`** (M4) failed once in `verify` under a load average of 27 (55.9 ns at 64 sections against 16.9 at 4, ratio 3.3 over its 3) and passed alone: a timing test measuring the two sizes one after the other. It now alternates them and compares each size's fastest of seven runs; the threshold is unchanged (its own commit).
- **Process slip** — the orchestrator ran a Gradle test while a worker's Gradle run was going in the same directory, which broke one of the worker's runs (AGENTS.md: one build at a time).

### 2026-10-25 — M6 T2–T17 claim

- **M6 T2.1 · review fix** — the 11 open findings of T2.1's review: the lint checks a tech's icon against the item registry; cycles in `requires` and `supersedes` are compile errors at the closing edge, and a tech needing a dropped tech is dropped too; effect paths and `Researcher/techs` tokens are checked against the set (in `fromFlattened` too, so test sets built from the built-in templates now carry its techs); requirement counts need provenance; a ContentSwap with techs goes through `JournalFormat`; the built-in set without techs equals the real pre-TECH-01 pin b4dc5f37; the payload-size test has techs; the `+t` mark is guarded both ways; bad JSON in a view is an IAE; the packs are read once per load; the overloads that dropped techs are gone; javadoc, messages, the provenance note (mechanics.md §1.3) and the lang key's place. 17 new tests, each name checked in the tree. `verify` green in 1 min 39 s (212 GameTests).
- **M6 T2.2 · TECH-01, research** — TECH-01 `done` (all four criteria). `Research` (31) and `REQUIREMENTS` (wire 18); `ResearchRules`; tech entries `-1 - netIndex` in the building queue (`QueueEntry`), saved by id; completion, cancel and refund in `Production` and `Techs`; `Progress` in `Play` (tech ids, hashed only when non-empty); stats rebuilt by one helper at completion, restore and swap (spec §1.6 rows 18, 20, 25–28); `TechResearched`; `/strategos stats` names tech sources; the tech table seeded from the built-in set. New: `ResearchTest` (20 tests), `ResearchWireTest` (6, two of them written by the orchestrator: the worker reported them but had not written them), `techtests.a_research_completes_and_applies`, the tech-table test. A review before the commit found 7 should-fix and 6 nits, all applied (supersede chains, phase names, source ids colliding with handles, a pending tech saved by index, an invalid saved id, restore order, Gaia). JMH: red against the M4 Pro baseline on this M1 Pro (load ~12), green against its own base on the same machine (20 benchmarks, median ratio 0.994, `StatReadBench.cachedRead` 3.77 → 3.75 ns, `StateHashBench.hash600` 33.1 → 31.8 µs); a kb entry. `verify` green in 2 min 28 s (212 GameTests).
- **M6 T2.1, its review** — T2.1 (1,706 lines, a payload change) was committed before the reviewer docs/agents.md asks for; the review afterwards found no blocker, 6 should-fix and 7 nits (the lint's icon check, `requires`/`supersedes` cycles, unchecked effect paths and `Researcher/techs` tokens, the tech table's seed, requirement counts without provenance, journal tests with techs). The seed is fixed in T2.2; the rest is the next commit.
- **M6 T2.1 · TECH-01, the content** — TECH-01 `in_progress`. `TechDef`, `TechEffect`, `Requirement` with `Requirements` and `FactionView`, `TechId`, the append-only `TechIdTable`; `strategos/techs/` loaded beside the templates; the techs in the set's hash under `$techs` only when present (a set without techs keeps its sha, pinned); `TemplateSetView` carries them (spec §1.6 row 24); the sample `strategos:gather_wood_1` (wood ×1.2 for workers, 100 food 50 wood, 30 s) at the storehouse, with provenance. New: `RequirementTest`, `TechDefTest` (15 malformed cases), `TemplateSetViewTest`; the built-in set's pin moved to 3f019e66. `verify` green in 1 min 50 s (211 GameTests).
- **Claim merged** — #17 (0a7cc3b), merged by the owner; its body asked for «open M6 for claims», and STATE's "Open for claims" is not yet updated (the maintainer's record). ADR-0025 is still Proposed: it blocks only T6's move rule.
- **T2 split** into T2.1 (the content) and T2.2 (research) before work, from the implementation map: the spec's §1.6 rows 18–23 record what the code showed (queue entries, Village without a tech, stable tech ids, techs in the set's hash, modifiers at restore, the card's token lists).
- **Claim** — M6 T2–T17, the part of M6 that needs nothing from M5, claimed after the M6 spec merged (#16). M6 is not yet in STATE "Open for claims": the claim asks the owner to open it. The developer's restriction for this session: no client windows; L4 runs go to the consent batch (T27).
- **Next:** T3.

## For the owner

1. **Opening.** «open M6 for claims»: this claim covers T2–T17 only; T1 and T18–T28 wait for M5.
2. **Approval: [ADR-0025](../adr/0025-commands-into-unexplored-cells.md)** (Proposed, from the M6 spec): moves into unexplored cells. Blocks T6's move rule, nothing before it.

## Decisions taken

- 2026-10-26 · `ProtectedBlockIndexTest` measures the two sizes alternately and compares their fastest runs, the 3× threshold unchanged — load from other processes only adds time, and the sequential median failed under load — the sequential median.
- 2026-10-26 · Only additions to `Trainer/units` and `Researcher/techs` count as unlocks (spec row 29) — a class edit makes nothing available — any `TOKENS` addition.
- 2026-10-26 · The phase notice carries its ordinal in `MatchNews.ticks` (row 30) — the record's fields are generic and the kind says how to read them — a field of its own.
- 2026-10-26 · TECH-02's criterion 2 is shown on the shipped Town (empty today) and on a test set with real unlocks (row 29) — the arsenal, the ram, the tower and the Town techs arrive with T18–T20 — wait for them.
- 2026-10-26 · Techs get balance-sheet rows in T3 rather than T18 (row 35) — §2.2 makes Town's numbers sheet rows — leave them to T18.
- 2026-10-25 · A view with techs marks its version `+t` on the wire (spec row 24) — journals are unframed, and old streams must read unchanged — a framed view with a format byte, at the cost of every committed journal.
- 2026-10-25 · The syntax of class filters and token edits lives once, in `content.StatSyntax`; `ClassFilter.check` and `TokenEdit.valid` call it — the content layer may not import `sim` (ArchUnit B3), and the worker's first cut copied the checks — copy them back into `sim.stats`.
- 2026-10-25 · A tech's `name` is a lang key (`strategos.tech.gather_wood_1`, in `en_us.json`), as templates' are — the client translates it — a literal name.
- 2026-10-25 · A tech queue entry is `-1 - netIndex` in the existing int queue, and a save writes it by id through the building's `techs` list (rows 18, 20) — old saves and hashes stay byte-identical — a separate entry-kind field.
- 2026-10-25 · A queued tech the current set lacks leaves its queue unrefunded, at restore and at a swap (row 20) — its cost is no longer known — refund from a saved cost.
- 2026-10-25 · Tech modifiers carry negative source ids `-1 - netIndex` (row 26) — entity handles fill the positive range from 2²⁰ — a documented positive range.
- 2026-10-25 · `phase` requirements name bare lower-case phases, checked at compile (row 27) — a free string could fault a match on an ordinary command — accept `phase_` prefixes.
- 2026-10-25 · JMH judged by an A/B against the base commit on this machine, not by the M4 Pro baseline — the baseline is another machine's — run `jmhCompare` on the owner's Mac at the hand-in.
- 2026-10-25 · trial implementer (general-purpose on Sonnet) on T2.2: fail on one point — green, and a reviewer's 13 findings applied in one round, but its report named two wire tests it had not written (the orchestrator wrote them); it also wrote the second round's tests after the code and said so. Read every worker's tests against its report.
- 2026-10-25 · trial implementer on T2.1's review fixes: pass — 11 findings in one round, every reported test present in the tree; it wrote most tests after the code and said so.
- 2026-10-25 · trial reviewer (general-purpose, read-only): pass, twice — T2.1 after its commit (13 findings) and T2.2 before (13), each checked against the code.
- 2026-10-25 · trial implementer (general-purpose on Sonnet, the role file): pass — T2.1 green on its first run; one deviation (copied syntax checks) found in the diff and folded into one source; it reported the tests were written with the code, not before.
- 2026-10-25 · The claim takes T2–T17 and leaves T1 and T18–T28 — they use M5's classes, and work that can only follow M5 is not claimed yet (AGENTS.md) — widen it by a new claim once M5 lands.

## Pitfalls

New pitfalls go straight into `docs/kb/` on the branch; list here which entries were added.

- [rts-sim.md](../kb/rts-sim.md) "`jmhCompare` on a machine other than the baseline's" (M6 T2.2).

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
