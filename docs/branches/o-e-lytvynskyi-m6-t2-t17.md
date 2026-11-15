# m6-t2-t17 on dev/o-e-lytvynskyi/m6-t2-t17

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m6-t2-t17 · **Work:** M6 · **Base:** dev/dyadik/m6-t2-t17 @ 7f08f40 · **Claimed:** 2026-11-14 · **Status:** ready for review

**Note:** taken over on the owner's word, 2026-11-14 («давай перехватим задачу и добьем ее»); dyadik's claim was released by the owner in [PR #54](https://github.com/o-e-lytvynskyi/strategos/pull/54), not handed over by its holder; the work continues from dyadik's head 7f08f40 ([PR #53](https://github.com/o-e-lytvynskyi/strategos/pull/53)), whose commits and branch-file log stay in the history. Widened to the whole of M6 (T1 and T18–T28, unclaimed) on the owner's word, 2026-11-14 («продолжай до того момента как закончишь полностью веху»).

## Now

- **Milestone:** M6 MVP: First skirmish, all 29 tasks built (with the T2 and T12 splits); spec [M6-first-skirmish.md](../milestones/M6-first-skirmish.md), results in its §11.
- **In progress:** nothing; handed in by [PR #55](https://github.com/o-e-lytvynskyi/strategos/pull/55) for a fresh maintainer session's review.
- **Last finished task:** M6 T28 (2026-11-15); T27's goldens approved the same day.
- **Next:** the merge; then the owner's matches (item 6).
- **Merged from main last:** a24572d (2026-11-14).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M6 T22 (AI-05) | Resolved: T22 is done in this branch; `Skirmishes.play` seats `ai:skirmish` at the setup's level | — | none left |
| M8 foundations (FOW-02/03/05, `lev1pes`, claimed) | Nothing; it overlaps fog: team vision, last-seen buildings, leak audit | independent: T5–T8's vision grid and client shroud are M6's solo fog; M8 extends them, the owner settles the base if both land fog | M5's `AFTER-M8` in `NetGateway` (shot effects to the union of two cells' viewers) stays; M6 adds none |

## Log

Newest first; one entry per session in the worklog's shape. Before 2026-11-14: dyadik's branch file on 7f08f40.

### 2026-11-15 — M6 T23, T24, T27, T28

- **T23 · QA-12** — the AI under fog, the net-progress backstop, openings and levels tuned (rows 60–70); QA-12.1 amended twice on the owner's word (≥ 75 % by conquest, median 20–38). Final `:core:openingBalance`: 1,000 matches in 19.3 min; openings 80, 81, 89 of 200 against each other; Hard 142 against Normal, Normal 169 against Easy; starts 60–92; medians 26:24–38:23; every criterion holds.
- **T24 · PERF-09** — `MvpScene`, `perftests.mvp_soak` and `mvp_stress`; the AI's placement world cached per tick (row 71); `perf/mvp-m4pro.json`.
- **T27 · the consent batch** — `m6_goldens` (23 frames) and new sets for m3, m4, m5, rts_goldens and art_look proposed, each three runs in agreement; seven fixes the windows found (row 72); PERF-14 archived in `perf/client/m4pro.json`.
- **T28 · the close** — `constructiontests` for every MVP blueprint (arsenal, storehouse, farmstead, field built and staged; deposits as their blueprints); `runClientSmokeJar`; the per-unit check without the AI.
- **Gates** (M4 Pro, 2026-11-15): `verify verifySlow` green, 4 min 47 s (258 GameTests); `runServerSmokeJar` green; `verifySoak` green, 3 h 13 min; `:core:openingBalance` green; `runGameTestPerf` green, 2 h 11 min (A per unit without the AI 6.66 µs against 7.83); `runClientSmokeJar` green, 13 s; `jmhCompare` red on `step600` only (item 3).

### 2026-11-14/15 — M6 T18–T22, T25, T28.1

- **T18 · TECH-03** — five Village techs and two Town techs (iron weapons, chainmail) with sheet and provenance rows; the tiers as faction values on `match_state`'s `looks` (cap 1,024 → 1,056), redressing bodies (`UnitLook`). Rows 50–51.
- **T19 · CMB-09** — `unit_large`, the ram (own cube model; crush through the BURN states; restricted targets refused and never picked), the arsenal; the worker's build list `Builder/buildings` (Town adds the tower and the arsenal; the B key and the card follow it). Two rams 49 s on a centre, torch-bearers ~348 s on stone. Row 53. CMB-09 done.
- **T20 · BLD-17** — the defence tower (100 wood 150 stone; 10 pierce every 40 ticks to 28 blocks; vision 24). Row 52. BLD-17 done.
- **T21 · AI-04** — openings, waves at a marked camp (`camp.minDistance`, 40 by default, 0 for M3–M5's scripts so their tests keep their timings), the raid bar, DefenceManager, RebuildManager. Found on the way: the battle bot re-ordered a wave on its target every think, resetting the stuck count, so units in a burners' lane never gave up (a 641-tick stall; `FieldWaveTest`, kb rts-sim); and its own GameTest lane collided with `SkirmishTests`' (the "b is not seated yet" flake). Rows 54–56.
- **T22 · AI-05** — `ai/skirmish.json`, three levels resolved into `SetAi`'s script, `ai_handicap` (penalties only), research and phase steps; Normal's Town 10.7–11.9 min over 10 seeds; the skirmish seats `ai:skirmish` at the setup's level. Rows 57–59.
- **T25** — the m6 demo chapters (research, town, fog, raid, siege, tower, notices); the demo stage runs revealed by default, only the fog chapter fogged; `docs/testing/playtest.md` the MVP protocol. Chapters not yet run (the window batch).
- **T28.1** — `checkNaming` (verifyFast), `CommandCardIconsTest`, `runServerSmokeJar` (verify, ~10 s), `docs/provenance.md`, spec §8.1's regression map.
- **Gates** after T21's merge: `verify` 249 GameTests and `verifySlow` 23, 4 min 51 s; each later task's `verify` green.
- **Delegation:** implementer trials pass for T1 (×3), T18–T22, T25, T28.1; one T18 worker looped on its report and was stopped; workers' own diffs were read and two more fixes asked (the B key's list, the demo's fog default).

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

1. *(Answered 2026-11-15: kept.)* **The minimap's "changed chunks" (UI-20, spec rows 45 and 48):** construction, new and destroyed buildings redraw within a second; other terrain changes (a felled tree, a placed block) wait for the round, a few seconds. Recommended: keep it; a client block-update hook would make it strict (a mixin, no NeoForge event). Say if you want the hook.
2. *(Answered 2026-11-15: in the new baseline.)* **`MinimapBench` has no baseline entry:** it joins `perf/baseline-m4pro.json` at the next baseline you approve (0.76 µs a tile against a 0.5 ms budget).
3. *(Answered 2026-11-15: the proposed baseline approved.)* **`jmhCompare` red, one benchmark** (2026-11-14, a valid run: chat closed): `MovementBench.step600` 22.0 → 33.3 µs against the baseline (main itself reads 24.6–25.0 here). Bisected and profiled: vision stamping is the whole difference (24.7 µs with `VisionSystem` stubbed); `VisionBench` reads the same ~11 µs for 300 moving units, inside WORLD's 0.5 ms budget (spec §6). Recommended: accept it as the fog's own cost — a proposed baseline (`perf/baseline-m4pro.proposed.json`) from the full run at the close, for your approval; a cheaper restamp (only the disc's changed edge on a one-cell move) is possible later if the budget tightens.
   **At the close** (2026-11-15, a valid full run): `jmhCompare`'s only red is still `step600`, 22.0 → 36.3 µs; every other benchmark is within 15 % (`ShotBench.volley60{collateral}` +14 %, `MatchBusBench` −11 %, `TerrainEditBench` −40 %). Re-bisected the same hour: M5's T18 reads 24.9, the vision fix (90c7b34b) 35.0, T23's backstop 36.3, the head 36.0 — the fog is the cost; T23 adds about 1 µs (3 %); the 33.3 of 2026-11-14 was the machine's spread. `perf/baseline-m4pro.proposed.json` is that full run (30 benchmarks, the first scores of `MinimapBench` 0.64 µs, `ShroudBench.extract384` 19.0 µs, `VisionBench` 10.9 µs, `NodeDiscoveryBench` 67.4 µs and `IdleBench` 25.3 µs); on a yes it replaces `perf/baseline-m4pro.json` and item 2 closes with it.

4. *(Answered 2026-11-15: approved, Sodium's m4 too.)* **Goldens to approve (T27, spec row 72):** `mod/src/devtools/goldens-proposed/macos/` holds `m6_goldens` (23 frames, new) and new sets for `m3_goldens`, `m4_goldens`, `m5_goldens`, `rts_goldens` and `art_look` (M6's HUD, T23's arrivals, the horseman's stone sword before a tier, the client's light pinned at noon). Each agreed with itself over three window runs. Two settings to look at: m5's `volley_impact` mask moved onto the new arrows, and m6's `setup_screen` takes a 1e-4 threshold (the blurred world behind the screen varies 1–2e-5; the screen itself is identical). On a yes they move into `goldens/macos/` (README "Approving goldens").
   **Under Sodium and Iris** (spec row 73): `goldens-proposed/macos-sodium/m4_goldens` (10 frames, three runs in agreement, each looked at). `m6_goldens`, `m5_goldens` and `art_look` did not repeat (run 1 against 2 and 3: up to 5e-5, `look_m5_24` 1.1e-4, `shroud_unexplored` once 1.2e-3), so none is proposed; their old Sodium goldens (m5, art_look) predate M6's HUD and fail with `-Pcompat` until then. Recommended: approve m4's, and leave the others as a follow-up of the next milestone.
5. *(Answered 2026-11-15: (c), until M8's leak audit.)* **Buildings stand above the shroud** (FOW-07, spec row 73): in `shroud_unexplored` the enemy's civic centre shows above the black, because the shroud lies on the terrain and a building's blocks rise above it, so an unexplored enemy base can be seen from the RTS camera. Not fixed. Options: (a) lay the shroud over the column's top, buildings included (hides roofs, a wall still shows from the side); (b) a black volume over unexplored columns up to the highest block; (c) leave it until M8's leak audit (FOW-05). Recommended: (b) as the next milestone's first fix if you want the MVP's fog honest; (c) costs nothing now. Your choice.
6. **Your part of the MVP gate** (QA-13.7, spec §8 row 9): the playtest protocol `docs/testing/playtest.md`: one match per level (Easy, Normal, Hard), one on a dedicated server (`scripts/playtest.sh`), a win on Easy, the questionnaire, the audio checklist; two friends' matches. The demo chapters first, if you like: `./gradlew :mod:runScenario -Pscenario=demo -Pdemo=m6 -Pscenario.timeoutSeconds=3600`. Then the cards waiting on you become `done` (the goldens) and `accepted` (your verdict).

## Decisions taken

- 2026-11-15 · The owner on PR #55's items: «3 - c, остальное прими решениея самые подходящие и если все готово, дай ссылку, я смержаю». So: item 5 (buildings above the shroud) is (c), left until M8's leak audit (FOW-05); item 1, the minimap's changed chunks stay as built; items 2–3, `perf/baseline-m4pro.proposed.json` becomes `perf/baseline-m4pro.json` (ADR-0019 "M6 baseline"); item 4, the proposed goldens move into `goldens/macos/` (m3, m4, m5, m6, rts_goldens, art_look) and `goldens/macos-sodium/m4_goldens`; nine cards whose last check was a golden become `done`; FOW-07 stays `in_progress` (its Sodium and Iris-with-a-pack goldens). Undo: revert the commit «M6: the owner's word on the hand-in».

- 2026-11-14 · The claim covers all of M6, not only T2–T17, and M6 counts as opened for this branch — the owner's word «продолжай … до того момента как закончишь полностью веху»; the maintainer records the opening in STATE at the merge — undo: narrow `Work` back to `M6 T2–T17` and release T1, T18–T28.

- 2026-11-14 · The claim and the delivery go in one pull request (#55, a draft until the hand-in), and development starts before a separate claim merge — the owner's word «продолжай автономную разработку, принимай все рекомендуемые решения» after merging the release #54; the precedent is #39 and #43 — undo: split out a claim-only pull request of this file.
- 2026-11-14 · trial implementer ×4 (T13; T14 with T17; T15; T16): pass — each built its tests, found real bugs (wire fault, double start, J's target, the T16 extraction), and reported its gaps; the orchestrator found two more in review (the feed across matches, tiles of new and destroyed buildings). The isolated worktrees the Agent tool creates start from `main`, not the branch: the workers were given detached worktrees at the branch head instead.
- 2026-11-14 · Trace-table test names replaced by the tests' real names (spec §1.4 rows for MATCH-02, UI-20, UI-21, UI-14) — the planned names were intents, one check became several tests — undo: rename the tests.
- 2026-11-14 · MATCH-02 check 2 is split between a GameTest and the arena smoke (row 47) — a GameTest world has no arena slots — undo: an arena GameTest harness.
- 2026-11-14 · The released `dyadik-m6-t2-t17.md` keeps dyadik's log from 7f08f40 with `main`'s released header and note (the merge's resolution) — undo: take `main`'s version of that file.
- 2026-11-14 · Town's wood cost 500 → 600 (spec row 63): with storehouses and Town's price reserved, Normal reached Town near 10:00, below the 12:00 target; 600 puts it at 10:00–12:06 inside DifficultyTest's unchanged window. Undo: `phase_town.json`, `BuiltinTemplates`, its provenance row.
- 2026-11-14 · A net-progress backstop beside the stuck ladder (spec row 61), saved and hashed; five goldens re-recorded (walking_skeleton and canary keep every checkpoint hash), one line in REBASELINE.md. Undo: revert the T23 round-6 commit.
- 2026-11-14 · QA-12.1 amended on the owner's word in this session («да, согласен, давай так, чтобы закончить это дело не сильно затягивая»): ≥ 75% of matches by conquest within 45 minutes instead of every match (first 90%); no exception and no stall in any match; 200 seeds per pairing kept (spec row 65). Then, at 74.5% after four rounds, the owner chose 75% (AskUserQuestion, «Lower the bar to 75%»). Undo: restore "every match" in QA-12.1, the done gate and spec §2.21/§8.
- 2026-11-14 · R6 taken (spec rows 9, 68): Hard is the labelled top level with an open bonus, gather ×3.0 and train ×0.35 (row 70; 2.6/0.4 first, 130 of 200, until Town was put back on time), listed in its setup text; 142 of 200 against Normal. Undo: drop `handicap` from Hard in `skirmish.json` and `Levels.TOP`.
- 2026-11-15 · Early soldiers only from the surplus (spec row 70): a skirmish AI trains a soldier while a build step is due only if the stock pays both; `early` is an opening knob (base 0, rush 1) for soldiers that pass the reserve; the second barracks follows Town (population 23). Keeps AI-05's Town at 12:00 ± 2. Undo: `early` in `skirmish.json`'s openings and script, the barracks step back to 21.
- 2026-11-15 · The AI's start positions are part of the match (spec row 69): `MatchConfig.starts`, filled from the map manifest; `setStartPositions` removed. Undo: none sensible — without it the AI never attacks under fog.
- 2026-11-15 · The pre-M6 L4 scenarios build their matches with `ScenarioWorld.REVEALED`, the rules their goldens were made under (M6 made fog the default); `rts_frame` stays PERF-14's revealed baseline and `rts_frame_shroud` adds the shroud. Undo: `PlayRules.DEFAULTS` in those scenarios.
- 2026-11-15 · m6's `setup_screen` golden takes a 1e-4 threshold (the blurred world behind the screen varies 1–2e-5 run to run), and m5's `volley_impact` mask follows the arrows to their new place (spec row 72). Undo: `goldens.json` in the proposals.
- 2026-11-15 · PERF-09's per-unit check (§2.22) holds A's p95 per unit without the AI's phase to M5's battle soak (1.139 × M4's base, M5 spec §11; no `perf/battle-m4pro.json` was archived), within 15 %; the AI is M6's own new cost and the report gives A with it beside (12.2 µs a unit with, about 7.4 without, against 7.8). Resolves the AFTER-M5 marker. Undo: `PerfVerdicts.mvp`, A with the AI.
- 2026-11-15 · R8 left open (spec row 71): the MVP soak's costs are not compared with a grown match's, because the headless matches keep no per-system times; the scene is placed, as §6 says. Undo: a real clock under a flag in `HeadlessMatch`, summing the phase timers at about 150 population.
- 2026-11-15 · The stress pass runs 6,000 ticks (PERF-09.3 names no length; M5's ran the short first-loop length). Undo: `strategos.mvpStress.ticks`.
- 2026-11-15 · `runGameTestPerf`'s wall-clock bound 110 → 150 min: the MVP soak is 36,000 ticks paced at 50 ms (30 min) and its stress pass six, and the whole perf batch ran out of time in `mvp_soak` at tick 19,058 (2026-11-15), as M1, M3 and M4 raised it for their soaks; it bounds the run, not a budget. Undo: `mod/build.gradle`'s timeout, with the MVP soak in its own task.
- 2026-11-15 · The net-progress count belongs to an order (spec row 68's exceptions): a new order starts it again; a path delivered after its start column was built over stands as found. Undo: `StuckLadder.orderKey`, `PathService.startFrom`'s guard.

## Pitfalls

- None yet.

## Review

Written by the validation. The final review is done by a fresh maintainer session: this session reviewed #53 and now develops the work.
