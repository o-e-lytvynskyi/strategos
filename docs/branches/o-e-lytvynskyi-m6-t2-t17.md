# m6-t2-t17 on dev/o-e-lytvynskyi/m6-t2-t17

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m6-t2-t17 · **Work:** M6 · **Base:** dev/dyadik/m6-t2-t17 @ 7f08f40 · **Claimed:** 2026-11-14 · **Status:** in progress

**Note:** taken over on the owner's word, 2026-11-14 («давай перехватим задачу и добьем ее»); dyadik's claim was released by the owner in [PR #54](https://github.com/o-e-lytvynskyi/strategos/pull/54), not handed over by its holder; the work continues from dyadik's head 7f08f40 ([PR #53](https://github.com/o-e-lytvynskyi/strategos/pull/53)), whose commits and branch-file log stay in the history. Widened to the whole of M6 (T1 and T18–T28, unclaimed) on the owner's word, 2026-11-14 («продолжай до того момента как закончишь полностью веху»).

## Now

- **Milestone:** M6 MVP: First skirmish, the claim's T2.1–T17 done (18 of 29 tasks with the T2 and T12 splits; T1 and T18–T28 are outside the claim); spec [M6-first-skirmish.md](../milestones/M6-first-skirmish.md).
- **In progress:** M6 T18 (TECH-03) and T21 (AI-04) since 2026-11-14.
- **Last finished task:** M6 T1 (2026-11-14).
- **Next:** T19, T20, T22, T23, T24, T25, T27, T28.
- **Merged from main last:** a24572d (2026-11-14).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M6 T22 (AI-05, unclaimed) | The skirmish's AI level: slot 1 plays `ai:first_loop` until `ai/skirmish.json` exists (spec §1.6 row 46) | against a contract: `SkirmishSetup` carries the level end to end | `AFTER-T22` in `Skirmishes.play` |
| M8 foundations (FOW-02/03/05, `lev1pes`, claimed) | Nothing; it overlaps fog: team vision, last-seen buildings, leak audit | independent: T5–T8's vision grid and client shroud are M6's solo fog; M8 extends them, the owner settles the base if both land fog | none |

## Log

Newest first; one entry per session in the worklog's shape. Before 2026-11-14: dyadik's branch file on 7f08f40.

### 2026-11-14 — M6 T1 and the vision fix

- **M6 T1 · health check from M5's close** — the classes over 800 lines split by moves only: `RtsHud` 1,100 → 756 (`HudNews`, `HudBuildingCard`, `HudOverlays`, `HudTooltips`), `TemplateCompiler` 1,013 → 597 (`TechChecks`, `Fields`), `MatchState` 897 → 732 (`MobilityCache`, `TemplateSwap`, `BuildingRestore`, `UnitLifecycle`, `NodeIndexCache`, `DamageRankCache`, `PopulationCost`); same API, pinned set SHA and hashes (1,795 core tests rerun). The largest class is now `BuildingStore` (798). M5's other follow-ups: cavalry and buildings and the bots' trees stay with the owner and T23's tuning; the thin perf margins are measured at the close (`verifyPerf`). `verify` 1 min 31 s, `verifySlow` 22 GameTests in 3 min 27 s.
- **Vision** — `jmhCompare` red on `MovementBench.step600` (+51 % against the M4 Pro baseline; 34.4 µs against main's 24.7 here), bisected to the T2–T6 merge and profiled: the fog's stamping (24.7 µs with `VisionSystem` stubbed). On the way: no per-tick pass over the explored grid (a stamp marks its cells), building stamps revisited only when buildings or a faction's modifiers change, and buildings see by their faction's `Vision/range` (`BuildingVisionTest`: fails without it). For the owner 3.

### 2026-11-14 — M6 T13–T17: tests, fixes, gates

- **M6 T13 · MATCH-02** — `SkirmishSetupTest` (8: defaults, every combination on the wire, the cap's bounds, the map id, resource presets, stable codes, malformed input), `SkirmishOptionsTest` (3: the command's options), GameTests `skirmishtests.setup_makes_the_match_with_the_chosen_settings`, `a_second_skirmish_is_a_match_of_its_own` (both `map_` batches, slow) and `the_payload_needs_the_host_or_an_operator`; the arena smoke's run 2 checks two skirmishes take slots 3 and 4. Fixes: a malformed `SkirmishSetup` is a `WireException`, not an `IllegalArgumentException`; a player with a skirmish still importing cannot start another. Spec rows 46–47.
- **M6 T14 · UI-21** — `NotificationFeedTest` (8), `AlertSoundPlanTest` (2), `PhaseNewsTest` (2), `NoticeKindCodecTest`, TRAINED/BUILT/RESEARCHED cases in `ProductionTest`, `ConstructionTest`, `ResearchTest`, `PhaseTest`. The sound mapping and its limit move into core `AlertSoundPlan` (`AlertSounds` is its adapter; ids checked in the sources). Fixes: J keeps the newest alert after it fades; the feed and the HUD's news counts reset when the model forgets a match (`clears()`), else a second match's first alerts, waves and phases went unheard (row 49).
- **M6 T15 · UI-13** — `PhasePanelTest.theChecklistMirrorsTheServer` (eight states of a real match, through the wire into `ClientFactionView`), `anEnemyBuildingOrAFoundationIsNeverCounted`, `CommandCardResearchTest` (6), two `WireCodecSizeTest` cases for `faction_state`'s new fields, GameTest `persistencetests.research_progress_survives_save_and_load`. No production fix needed; `CommandCardTest`'s unused local removed (ErrorProne).
- **M6 T16 · UI-20** — `MinimapOrderTest` (3), `MinimapTilesTest` (5), GameTest `nettests.a_minimap_order_applies`, `MinimapBench` (0.76 µs a tile, M4 Pro, under load). `MinimapOrders`, `MinimapPings` and `DirtyTiles` are pure core; `RtsMinimap` and `RtsHud` adapt them. Fix: a building that appears or goes away marks its tiles too (row 48).
- **M6 T17 · UI-14** — `HintBookTest` (8), `ControlsOverlayTest` (3); the overlay's rows come from core `ControlsOverlay`.
- **Gates** (M4 Pro, 2026-11-14): `verify` green after each task (1 min 24–34 s); `verifySlow` green, 3 min 24 s; `verifySoak` green (200 seeded replays, up to date on the same inputs); `:core:jmh` ran, `jmhCompare` **invalid environment** for all 30 benchmarks (foreign load 8.8–16.3 % against the 7.5 % limit: chat, editor and window server of the person at the machine) — not a failure (ADR-0026), not yet a pass: to rerun on a quiet machine.

### 2026-11-14 — claim

- **Claim** — M6 T2–T17 taken over from dyadik on the owner's word; the release is #54 (merged by the owner). Merged dyadik's head 7f08f40 and `origin/main` a24572d; the code equals 7f08f40, on which `verifySlow` (20 required GameTests, 3 min 19 s) and `verifySoak` passed locally. Draft pull request #55.

## For the owner

1. **The minimap's "changed chunks" (UI-20, spec rows 45 and 48):** construction, new and destroyed buildings redraw within a second; other terrain changes (a felled tree, a placed block) wait for the round, a few seconds. Recommended: keep it; a client block-update hook would make it strict (a mixin, no NeoForge event). Say if you want the hook.
2. **`MinimapBench` has no baseline entry:** it joins `perf/baseline-m4pro.json` at the next baseline you approve (0.76 µs a tile against a 0.5 ms budget).
3. **`jmhCompare` red, one benchmark** (2026-11-14, a valid run: chat closed): `MovementBench.step600` 22.0 → 33.3 µs against the baseline (main itself reads 24.6–25.0 here). Bisected and profiled: vision stamping is the whole difference (24.7 µs with `VisionSystem` stubbed); `VisionBench` reads the same ~11 µs for 300 moving units, inside WORLD's 0.5 ms budget (spec §6). Recommended: accept it as the fog's own cost — a proposed baseline (`perf/baseline-m4pro.proposed.json`) from the full run at the close, for your approval; a cheaper restamp (only the disc's changed edge on a one-cell move) is possible later if the budget tightens.

## Decisions taken

- 2026-11-14 · The claim covers all of M6, not only T2–T17, and M6 counts as opened for this branch — the owner's word «продолжай … до того момента как закончишь полностью веху»; the maintainer records the opening in STATE at the merge — undo: narrow `Work` back to `M6 T2–T17` and release T1, T18–T28.

- 2026-11-14 · The claim and the delivery go in one pull request (#55, a draft until the hand-in), and development starts before a separate claim merge — the owner's word «продолжай автономную разработку, принимай все рекомендуемые решения» after merging the release #54; the precedent is #39 and #43 — undo: split out a claim-only pull request of this file.
- 2026-11-14 · trial implementer ×4 (T13; T14 with T17; T15; T16): pass — each built its tests, found real bugs (wire fault, double start, J's target, the T16 extraction), and reported its gaps; the orchestrator found two more in review (the feed across matches, tiles of new and destroyed buildings). The isolated worktrees the Agent tool creates start from `main`, not the branch: the workers were given detached worktrees at the branch head instead.
- 2026-11-14 · Trace-table test names replaced by the tests' real names (spec §1.4 rows for MATCH-02, UI-20, UI-21, UI-14) — the planned names were intents, one check became several tests — undo: rename the tests.
- 2026-11-14 · MATCH-02 check 2 is split between a GameTest and the arena smoke (row 47) — a GameTest world has no arena slots — undo: an arena GameTest harness.
- 2026-11-14 · The released `dyadik-m6-t2-t17.md` keeps dyadik's log from 7f08f40 with `main`'s released header and note (the merge's resolution) — undo: take `main`'s version of that file.

## Pitfalls

- None yet.

## Review

Written by the validation. The final review is done by a fresh maintainer session: this session reviewed #53 and now develops the work.
