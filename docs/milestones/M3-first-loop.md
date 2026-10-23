# M3 — First loop: implementation spec

*Written 2026-10-23 by the assistant after M2 closed, from the 27 approved M3 cards in
[roadmap/M3-first-loop.md](../roadmap/M3-first-loop.md), [04-architecture.md](../04-architecture.md)
(cited as "04 §n"), the ADRs in [adr/](../adr/README.md), chiefly
[ADR-0021](../adr/0021-economy-and-construction-model.md), and M1's and M2's specs and code
([M1-movement.md](M1-movement.md), [M2-command.md](M2-command.md), cited as "M1 §n", "M2 §n").
Status: **specified**. The decisions M3 needs were taken as recommended on 2026-10-21: D-25 (a),
D-02 (villagers), D-04, D-05, D-10 (food, wood, stone, iron), D-11, D-12 (torches, the ram in M6),
D-16 and D-18; D-03 stays open until M7, so M3's placeholders keep a neutral palette. Engine names
cited here come from the cards and the architecture, which verified them; every other Minecraft or
NeoForge name is checked in `build/mc-src` by the task that first uses it, and recorded in §1.6
when it differs. Clean-room as before: our documents, our code, decompiled vanilla and NeoForge
only.*

---

## 0. Read this first

### 0.1 Authority

As M2 §0.1: the architecture and the ADRs decide *what*, this spec *how M3 builds it*; the cards
define scope and acceptance; ROADMAP.md holds the statuses; a disagreement is a §1.6 row. M2's code
is the starting point.

### 0.2 Working rules for every task

M2 §0.2 holds, with these changes:
- **No client windows for now.** The owner started M3 on 2026-10-23 «пока без окон». L4 checks run
  on CI's Linux route (`scenario.yml`, Xvfb, llvmpipe), which opens nothing on this Mac; what
  needs this Mac (macOS goldens, the owner's looks, client frame times) waits in the consent batch
  (§1.7). A task that cannot go on without a window stops, says so in STATE "For the owner", and
  the session tells the owner; headless tasks continue meanwhile.
- **CI minutes.** Scenario runs on CI cost about 10 minutes each; batch them per task, not per
  commit (the month's budget is 2,000 minutes).
- **Engine names** as M2 §0.2.
- **Done** as M1 §0.4, with this spec's progress list (§7.3).

### 0.3 Code conventions

M2 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Fixed point | Treasury, carried loads, node amounts, gather and build progress are `long` in thousandths of a unit (`Amount`); rates are per tick in the same unit, computed once from per-second template values; no `double` in hashed state |
| Older journals and saves | Every new store hashes and saves only when it holds something, so M1's and M2's golden journals and snapshots keep their hashes (as CMB-01 did, M2 §1.6 row 50); a change that cannot keep them re-records with a reason in `REBASELINE.md` |
| Aggregates | Population, housing, worker counts, dropsite lists and income are counters updated on events, never scans (04 §5.2) |
| Content numbers | Every gameplay number lives in templates and the balance sheet (DATA-04), never in code; code holds only defaults that the sheet lists |
| The world is the picture | The core never waits for a block: node amounts, progress, HP and obstruction are core state; blocks follow through the writer (04 §11.7) |

### 0.4 How a task is finished

As M1 §0.4.

---

## 1. Goal, demo and scope

### 1.1 Goal

Play the whole loop early, crude but complete: gather wood and food, build block by block, train
workers and spearmen, fight, and destroy the opponent's civic centre while a scripted bot does the
same. The verdict answers "is this fun?" before M4 to M6 deepen the systems (decision gate 3).

### 1.2 Slices

| Slice | Content | Demo at its end |
|---|---|---|
| 3.1 economy | DATA-04, UNIT-04, ECO-01, ECO-02, BLD-01 (a civic centre placed instantly), ECO-03, ECO-05, AUD-04 | Five workers chop trees log by log and pick berries; the treasury grows |
| 3.2 building and training | BLD-02, BLD-03, ECO-06, PROD-01, PROD-03, ECO-09, UI-06, UI-07, UI-10, ART-01 | Place a house and a barracks that rise block by block; train workers onto a rally point and spearmen; farm a field |
| 3.3 buildings fall, victory, orientation | CMB-03, MATCH-01, UI-11, UI-12, MATCH-05 | Burn a house down, win by burning the civic centre; pause; the minimap |
| 3.4 the opponent | AI-01, AI-17, QA-11, PERF-06 | A full match against the bot; headless bot-versus-bot runs end with a winner |
| gates | the first-loop soak, the consent batch | M3's Done gate |

### 1.3 Demo script (L7, the owner)

1. `scripts/playtest.sh` starts the dedicated test server and the owner's client (the owner runs
   it). `/strategos skirmish proving_ground` creates the first-loop scenario with the owner in
   slot 0 and the bot in slot 1, and seats the owner.
2. The RTS view opens over the own civic centre and five workers.
3. Box-select the workers, right-click a tree: they chop log by log, with cracks, particles and
   sounds; the wood counter rises. Right-click the berry bushes with two of them.
4. Command card: build a house; place the ghost; it rises block by block; a second worker helps.
5. Train two workers at the civic centre with its rally point on a tree; they start chopping.
   The idle-worker key finds a worker left idle.
6. Build a barracks and a field; train spearmen.
7. The minimap shows the bot's base; its wave is announced (notice, countdown, horn); defend.
8. Attack: spearmen take torches to the bot's houses and civic centre; the end screen shows the
   winner and the duration.
9. Pause once and play a minute at 1.5×.
10. The questionnaire (`docs/testing/playtest.md`), with "would I play another?" and its reason.

### 1.4 Cards, tasks and checks (traceability)

| Card | Task | Checks (level) |
|---|---|---|
| DATA-04 | T2 | 1 `BalanceSheetTest.aRelationshipChangesEveryDerivedValue` (L1) · 2 `BalanceSheetTest.theCommittedSheetIsTheOneTheModsDataGivesAndADiffShowsTheChangedNumber` (L1) · 3 `BalanceSheetTest.anOverrideNeedsAMarkerAndAReasonAndIsListed` (L1) · 4 `BalanceSheetTest.everyValueHasProvenance` (L1) · 5 `BalanceSheetTest.everyGatherableResourceIsSpentTwice` (L1) · 6 the timing targets in `docs/balance/sheet.md` (D) |
| UNIT-04 | T3 | 1 `WorkerModelTest.gatheringAndBuildingFollowClassTokens` (L1) · 2 `WorkerModelTest.bothTemplatesAreOnTheBalanceSheet` (L1) · 3 `contenttests.spawned_worker_and_spearman_read_their_stats` (L3) |
| ECO-01 | T4 | 1 `TreasuryInvariantTest` over 200 seeded streams (L1) · 2 `PaymentTest.anUnpayableCommandNamesTheMissingAmounts`, then the paying commands' own tests in T11 and T14 (L1) · 3 `replicationtests.faction_state_reaches_its_own_faction_only` (L3) · 4 `WireCodecSizeTest.aFactionStateRoundTripsWithinItsCap`, its audience declared (L2) |
| ECO-02 | T6 | 1 `NodeDiscoveryTest` against a reference flood fill on synthetic slices (L1) · 2 `NodeIndexTest.nearestEqualsBruteForce` on random maps (L1) · 3 `NodeSlotsTest` over seeded competitions (L1) · 4 `ecotests.a_resource_yield_entry_makes_a_node` (L3) · 5 `perftests.node_index_proving_ground` under 2 s (L6) |
| BLD-01 | T7 | 1 `BlueprintRoundTripTest` (L2) · 2 `BlueprintLintTest` (L2) · 3 `buildtests.civic_centre_accepts_everything_and_houses` (L3) · 4 `buildtests.units_path_around_a_footprint` with no page job (L3) · 5 `buildtests.building_state_owner_fields_to_the_owner_only` (L3) |
| ECO-03 | T8 | 1 `WoodcuttingTest.oneWorkerGathersTheSheetAmountInTheSheetTime` (L1) · 2 `TreeFellingTest` per species fixture, 2 × 2 included (L1) · 3 `CanopyTest.leavesNearALiveNeighbourStay` (L1) · 4 `NodeSlotsTest.aTwoByTwoTreeTakesItsFullCount` (L1) · 5 `ecotests.no_decayable_leaf_lingers` (L3) · 6 `ecotests.fell_then_walk_the_clearing_with_no_page_job` (L3) |
| ECO-05 | T9 | 1 `GatherTimingTest` (L1) · 2 `GatherRetargetTest` (L1) · 3 `DropsiteTest` (L1) · 4 `GatherSlotsTest.thirtyWorkersOneForest` (L1) · 5 `GatherTimingTest.aSpearmanGathersWhenDataSaysSo` (L1) · 6 `ForageTest` (L1) · 7 `ecotests.bushes_change_only_through_the_core` (L3) · 8 `ecotests.gather_deliver_and_save` (L3) · 9 `worker_tool_and_load` golden at 60 blocks (L4, consent) · 10 the owner's look (L7) |
| AUD-04 | T10 | 1 `nettests.world_fx_reach_who_sees_the_cell` with a positive control (L3) · 2 `WireCodecSizeTest.aSixtyFourPlacementTickFitsTheFxCap` (L1) · 3 `log_crack` golden (L4, consent) · 4 the owner's checklist (L7) |
| BLD-02 | T11 | 1 `PlacementRulesTest`, the numeric limits included (L1) · 2 `PlacementParityTest` (L1) · 3 `buildtests.a_placement_the_server_refuses_has_a_reason` (L3) · 4 `placement_valid`, `placement_invalid` goldens (L4, consent) |
| BLD-03 | T12 | 1 `ConstructionTest.buildTimeForOneTwoFiveAndTenBuilders` (L1) · 2 `buildtests.a_finished_house_equals_its_blueprint` (L3) · 3 `buildtests.no_unit_inside_a_committed_footprint` (L3) · 4 `buildtests.save_mid_build_resumes_at_the_same_block` (L3) · 5 `buildtests.writer_budget_never_changes_the_hash` (L3) · 6 `buildtests.three_sites_within_the_writer_budget` (L3) |
| ECO-06 | T13 | 1 `PopulationTest.invariantsOverSeededTrainingAndDeaths` (L1) · 2 `PopulationTest.trainingWaitsAtTheCapAndResumes` (L1) · 3 `PopulationTest.theCapNeverExceedsTheMatchMaximum` (L1) · 4 `prodtests.a_queue_waits_with_housing_needed` (L3) |
| PROD-01 | T14 | 1 `ProductionTest.noResourceLostOrDuplicated` (L1) · 2 `ProductionTest.aSixthEntryIsRefused` (L1) · 3 `ProductionTest.aFullCapPausesTheHeadKeepingProgress` (L1) · 4 `prodtests.a_unit_spawns_at_the_door_on_a_surface` (L3) |
| PROD-03 | T15 | 1 `RallyTest.workersRalliedToATreeGatherOnArrival` (L1) · 2 `prodtests.a_rally_point_survives_save` (L3) · 3 `rally_marker` golden (L4, consent) |
| ECO-09 | T16 | 1 `FieldTest.yieldFollowsDiminishingReturns` (L1) · 2 `FieldTest.anEmptyFieldWaitsForAPaidReseed` (L1) · 3 `ecotests.crops_change_only_through_the_core` (L3) · 4 `ecotests.a_field_yields_and_is_reseeded` (L3) |
| UI-06 | T17 | 1 `CommandCardLayoutTest` at every resolution and GUI scale (L1) · 2 `CommandCardTest.oneTrainClickSendsOneCommand`, view-model equals model (L1) · 3 `nettests.the_queue_reaches_its_owner_as_the_server_has_it` (L3) · 4 `select_worker`, `select_civic_centre`, `select_barracks` goldens (L4, consent) |
| UI-07 | T18 | 1 `IncomeTest.perMinuteMatchesAGatheredMinute` (L1) · 2 `ResourceBarModelTest` (L1) · 3 `nettests.faction_state_equals_the_server` (L3) · 4 `resource_bar` golden (L4, consent) |
| UI-10 | T19 | 1 `IdleWorkersTest.theKeyCyclesInStableOrder` (L1) · 2 `EconomyNoticesTest.oncePerCause` (L1) · 3 `idle_worker` golden (L4, consent) |
| ART-01 | T20 | 1 the placeholder blueprints pass the lint and T12's GameTests (L3) · 2 `docs/art/style-guide.md` with the per-class ranges (D) · 3 worker and spearman goldens at 60 blocks, with and without Sodium (L4, consent) · 4 the owner's look (L7) · 5 200 armoured humanoids within 6 ms with Sodium, client tick ≤ 1 ms (L6 client, consent) |
| CMB-03 | T21 | 1 `StructureAttackTest.tenSpearmenBurnAHouseInTheSheetTime` (L1) · 2 `StructureAttackTest.woodBurnsTwiceAsFastAsStone` (L1) · 3 `StructureAttackTest.torchesOnlyAgainstBuildings` (L1) · 4 `combattests.a_burn_makes_no_fire_block` with a spreading control (L3) · 5 `combattests.a_burnt_building_is_removed_and_cleared` (L3) |
| MATCH-01 | T22 | 1 `matchtests.a_scenario_loads_exactly_its_entities` (L3) · 2 `VictoryTest.theLastCivicCentreEndsTheMatchWithin20Ticks` (L1) · 3 `RuleChangeTest.journaledAndReplayedToTheSameHash` (L1) · 4 `matchtests.no_hostile_spawns_inside_a_match` with a control (L3) · 5 `matchtests.match_event_reaches_every_participant` (L3) · 6 `end_screen` golden (L4, consent) |
| UI-11 | T23 | 1 `MinimapImageTest.theProvingGroundsPixelsHashAsStored` (L1) · 2 `MinimapMappingTest` (L1) · 3 `cameratests.a_minimap_click_jumps` (L3) · 4 `minimap` golden (L4, consent) |
| UI-12 | T24 | 1 `AttackAlertTest.aSustainedAttackRaisesOneAlert` (L1) · 2 `cameratests.the_alert_key_jumps` (L3) · 3 `attack_alert` golden (L4, consent) |
| MATCH-05 | T25 | 1 `PauseTest.aPausedRunReachesTheSameHash` (L1) · 2 `PauseTest.ordersGivenInPauseApplyOnResume` (L1) · 3 `matchtests.pausing_one_match_leaves_another_stepping` (L3) · 4 C p95 ≤ 15.3 ms in `perftests.first_loop_soak` (L6) |
| AI-01 | T26 | 1 ArchUnit `aiNeverCallsStateMutators` (L0) · 2 `AiDecisionTest` on synthetic states (L1) · 3 `AiBudgetTest` (L1) · 4 `AiCommandsTest.theSameValidatorsAsAPlayer` (L1) |
| AI-17 | T27 | 1 `ScriptedBotTest.buildOrderThenTheFirstWaveOnTime` (L1) · 2 `ScriptedBotTest.everyWaveAnnouncedThirtySecondsAhead` (L1) · 3 `aitests.the_announcement_reaches_the_opponents` (L3) · 4 `ScriptedBotTest.everyThinkWithinItsBudget` (L1) |
| QA-11 | T28 | 1 `:core:headlessMatches`: ten seeds, one summary (L1) · 2 `HeadlessRunnerTest.anExceptionAStallOrTheLimitFails` (L1) · 3 `aitests.sample_journals_replay` (L3) · 4 `runServerSmoke`'s 600 ticks of AI versus AI (L5) · 5 `aitests.observe_binds_an_observer` (L3) |
| PERF-06 | T29 | 1–3 `perftests.first_loop_soak` and its 2× stress pass (L6) · 4 the report's placement, writer and `fx` times (R) |

T1 is the health check from M2's close (§7.2); T30 the consent batch; T31 the close.

### 1.5 Moved out of M3, with the reason

Nothing moves out at specification. Stone outcrops and iron deposits are indexed by ECO-02's
default `resource_yield` entries, but nobody gathers them until ECO-07 and ECO-08 (M4), so DATA-04.5
counts food and wood only in M3.

### 1.6 Differences resolved

Rows written while specifying; tasks append theirs.

| # | Item | Resolution |
|---|---|---|
| 1 | ECO-02: where the core learns materials | Slices hold nav classes only. Each captured page gains a sparse material list: every voxel whose block state has a `strategos:resource_yield` entry (or is a leaf of a tree, row 4), as its voxel index and a legend index; the legend lists kind (log, leaves, stone, ore, forage, crop), resource, subtype and amount per block. It travels inside `TerrainPage` with the slice, is part of the slice's hash and is journaled once per hash, so a replay needs no data map. The adapter fills it in `SliceCapture` from the data map NeoForge syncs |
| 2 | ECO-02: when discovery runs | On the first step at which every page of the match space is published (a map match's prepare publishes them all before READY), in page order, so trees across page edges join; a page published again later (an external edit) only shrinks the nodes it holds, and credits nothing (04 §11.4) |
| 3 | Nodes and buildings as entities | Buildings are core entities in the one `EntityStore` with a kind byte, so `Attack`, selection, rally and `ui_query` take their handles as they take a unit's; every unit system reads `EntityStore#unitAt` (M2 has 19 iteration sites). Resource nodes are not commanded and number in hundreds, so they live in a `NodeStore` with ids of their own; a gather order's target is a node id (`OrderKind` decides what a target is) |
| 4 | ECO-03: leaves and `persistent` | The proving ground's leaves are `persistent=true` (MAP-01, so random ticks never touch them); vanilla decays only non-persistent ones. The core thins a tree's canopy by vanilla's own distance rule (`LeavesBlock.DECAY_DISTANCE` = 7: a leaf more than six leaf steps from every remaining log goes) whatever the flag, so on a map only the core removes leaves; ECO-03.5's GameTest also fells vanilla-grown trees, whose leaves are not persistent. ADR-0024 supersedes ADR-0021's leaf rule (leaves only at exhaustion) |
| 5 | The core's own terrain edits (ECO-03.6, BLD-01.4) | `MatchState` keeps each published page's encoded slice and a per-page edit list (voxel, class, tick). A node block the core removes is an AIR edit at the tick it decides, a building's commit clears its footprint above the ground and fills the columns below it, its destruction clears the blueprint's volume; the page is rebuilt from slice, edits and the core's `NavOverlay` by a page job of the core's own, delivered `pageK` ticks later as path jobs are (M1 §2.6), never by the adapter. A slice the adapter captures later is the world plus the writer's pending writes at its sample tick, so the core re-applies only the edits after that tick. Own writes are never reclassified (`OwnWrite`, PATH-15) |
| 6 | Footprints and fields | A building's footprint columns are blocked in the core's `NavOverlay` from commit to removal, so no unit enters a house through its door; a field does not obstruct (`Footprint/obstructs` false), since farmers stand on it |
| 7 | ECO-02, ECO-03.4: slots per tree | The free standable cells of the ring round the trunk base: at most 8 for a 1 × 1 trunk, 12 for a 2 × 2; a blocked cell is no slot. "8 per tree" is the 1 × 1 count |
| 8 | ECO-05: who gathers | Gathering and building are class tokens with their components (`+Gatherer` with `ResourceGatherer`, `+Builder` with `Builder`), so a spearman gathers when its template says so (ECO-05.5); the bundled spearman does not (D-02) |
| 9 | ECO-05: the building tool | Vanilla 1.21.1 has no hammer; the placeholder is the mace, which reads as one; the style guide (T20) confirms or replaces it and the owner's look decides |
| 10 | ECO-09: growing and the amount | A finished or reseeded field ripens over `Field/ripenTime` (crop age 0 to 7) while its farmers wait in their slots; ripe, the age shows the remainder, `ceil(7 × remaining ÷ amount)`; empty, it stays at age 0 until reseeded. The card's "crops that visibly grow" and "the crop age as the amount indicator" both hold; the owner's look decides in the demo |
| 11 | CMB-03: wood and stone with no flammable block | The lint keeps every palette free of flammable blocks (04 §11.9), so "wooden" is a template value (`Identity/material`: wood or stone) that sets `Resistance/fire`; timber looks come from non-flammable blocks (crimson and warped wood, packed mud, terracotta), chosen in the style guide. Fire is a fourth damage type, dealt only by `Attack/Structure` (the torch) and only to buildings |
| 12 | BLD-02: explored and territory | Until fog (M6) every cell counts as explored, as for CAM-05's drop point (M2 §1.6 row 44). "Within reach of an own civic centre" is `Placement/civicReach` blocks from the centre's footprint edge, a sheet value |
| 13 | BLD-02: civic centres 64 apart | M3's worker builds a house, a barracks and a field; a civic centre is buildable by data, and the 64-block rule is an L1 case |
| 14 | Dropsites | The civic centre accepts every resource; M3 has no other dropsite (the farmstead and storehouse come later), so a field's food goes to the nearest civic centre |
| 15 | The proving ground for M3 | Version 2 adds berry bushes behind each start slot, off the lanes M1's and M2's soaks walk, and keeps its trees. Its terrain hash changes; golden journals and soak baselines on it are re-recorded if their hashes change, with the reason in `REBASELINE.md` (T9) |
| 16 | QA-11: what plays in L1 | A whole match runs in the core on `ProvingGround.voxels()` with its materials, `FakeWorld` taking the writer's effects; the game-time limit is 30 minutes, the Done gate's |
| 17 | MATCH-05.4's number | 23 ms ÷ 1.5, the fastest speed offered, is 15.3 ms C p95 at M3's scale (`perftests.first_loop_soak`) |
| 18 | MATCH-01: who loses | A faction with no civic centre left loses; the last faction with one wins. A civic centre still a foundation counts as none |
| 19 | Saves | Snapshot schema 3 adds treasuries, nodes, buildings, production and rally points; schema 2 saves load into it unchanged (a fixture pair, as M2's `match_v2`) |
| 20 | `building_state` | To its faction in full (footprint, template, progress, HP, queue, rally, population); to the others the same fields but queue, rally and population, once the building is seen (every building until fog). Coalesced per building to one a tick at most |
| 21 | T1: the health check | `HfsmTransitionsTest` (935 lines) split into `UnitTreeTransitionsTest` (the unit tree's 63 rows) and `HfsmTransitionsTest` (the framework's 19 cases on a test tree): 82 cases, as before. `PerfTests` (883) keeps its tests, so their names (`perftests.*`, which the perf filter and the docs use) stay; their verdicts, JSON files, the soaks' judgement and the foreign-load window moved to `PerfVerdicts`: 681 lines. `RtsMode` (780) keeps its public calls; the orders it sends (the right click with its preview and markers, stop, the first-person keys, the crosshair pick) moved to `RtsOrders`: 605 lines. `MotionSystem` is 776 lines since T29 moved its footing out, so it stays as it is. `verify` green, the golden journals unchanged; `perftests.snapshot_500_budget_g` passes through the moved verdict code; `rts_select` and `rts_first_person` on CI's Linux route check `RtsOrders` |
| 22 | T2: where relationships live | In the templates, not in a separate file: a value is `{"$derive": {"from", "path", "factor", "offset", "round"}, "note": ".."}`, `from` a template or `"self"`, evaluated exactly in decimals after flattening, per concrete template, so one written in an abstract parent derives in each child from its own values; a chain of them resolves in any order, a cycle is a problem naming it. A child sets a derived value only with `{"$override": x, "reason": ".."}`; a plain number, an `$op` or an object over it is a problem, as is an override of a value nothing derives. Markers never reach a set: the flattened JSON a journal or a client gets holds the numbers, so `fromFlattened` and every older journal are unchanged. Provenance notes for the numbers templates set live in `strategos/balance/provenance.json`, keyed by the template that sets each (a mixin or a parent), with the key `core` for the core's defaults, which the sheet lists for every path a template leaves out; a derived value's note is its relationship's. The timing targets are `strategos/balance/targets.json`. Costs are `Cost/resources/<resource>`, beside `Cost/population` and `Cost/time`, so the sink check reads resources by their path |
| 23 | T2: tests and the sheet | `BalanceSheetTest` (6): a relationship's factor changed changes the cost of both children that inherit it, rounded half up to its step (DATA-04.1); a chain through three templates and a cycle; the committed `docs/balance/sheet.md` and `sheet.csv` equal what the mod's data gives, the data compiles to the built-in set, and a changed spearman's hack shows in the CSV diff as two rows, its own and the pierce derived from it (DATA-04.2); an override needs its marker and reason and is listed with what the formula gives (.3); a number without a note fails, a derived one takes its relationship's, and the mod's data has a note for every row (.4); a gathered resource spent by one template fails, by two passes (.5). The first relationships: a spearman's pierce is its hack, and its pierce resistance its hack resistance; the flattened set is unchanged, so the template SHA and the golden journals are too. The sheet's first targets: first house by 1:30, barracks by 4:00, the bot's first wave from 8:00, a match within 30:00 (DATA-04.6) |
| 24 | T3: the worker | `unit/worker`: `+Worker +Gatherer +Builder +Organic`, 50 hit points, `Behaviour/flee` 8 (a new core path, 0 to 32, default 0), 60 food, one population, 10 s; the spearman now costs the worker's food (derived), 40 wood and 1.5 × the worker's time (derived, 15 s). Who gathers and builds is `StatService#canGather` and `#canBuild`: the `Gatherer` and `Builder` tokens as modifiers leave them, so a faction modifier that adds `Gatherer` to its spearmen makes them gather (D-02's citizen-soldiers as data). Fleeing: a unit with no melee attack and a flee distance, hit while idle, takes up `OrderKind.FLEE` (code 6, saved as `flee`) before its orders, to the point that far from it straight away from the attacker, walked as a move; there, it goes back to its next order or stands. The gathering and building states take it up the same way (T9, T12). The worker draws as the others do, the placeholder Steve, until ART-01. The new ids sort after M2's, so their net indices stay (0, 1) and the worker is 2; the set's SHA changed (`TemplateSetTest`), the golden journals did not, since they carry their own sets. Building ids sort before `unit/`: T7 pins the indices (§9 R10). Tests: `WorkerModelTest` (3; UNIT-04.1, .2 and the flight), `contenttests.spawned_worker_and_spearman_read_their_stats` (UNIT-04.3) |
| 25 | T4: resources are match rules | A match's resources are part of its `MatchRules` (`economy`: id, subtypes, starting amount, in order; at most 16), copied at creation from the data packs' `strategos/resources/<id>.json` (`order`, `subtypes`; display names `strategos.resource.<id>`), so a reload changes later matches only, and older rules decode with D-10's four at 0. Amounts are thousandths (`Amount`). The `Treasury` keeps, per faction and resource, the stock and what was granted (starting resources, the admin `Grant`, wire id 7), gathered, spent and refunded; a payment covers every resource or changes nothing. Its invariant, granted + gathered + refunded = stock + spent with no stock below 0, is checked by the core itself in FINAL every step, so every harness run, GameTest and server checks it; carried loads and node remainders join it with ECO-05. It hashes and saves only once non-empty (`treasury`, an optional field of schema 2 until the close bumps the schema), so older states and saves are unchanged. Costs are `Cost/resources/<id>` through the faction's modifiers (`Costs`). A refusal for want of resources is `NOT_ENOUGH_RESOURCES` with the missing amounts in thousandths in the result and in `feedback`. `faction_state` goes to each seated player of the faction, looked at every 5 ticks and sent when it changed (so a player who joins gets it at the next look); population and workers are 0 until ECO-06 and ECO-05. ECO-01.2 holds for the result itself here; the first paying commands, `PlaceBuilding` (T11) and `Train` (T14), assert it, and the card is done then |
| 26 | T4: tests | `TreasuryTest` (3), `TreasuryInvariantTest` (200 seeds of 100 steps: grants through the command path, and payments, refunds and deliveries as later systems make them, each stock equal to a model every step; ECO-01.1), `PaymentTest` (3: the grant is the administrator's and names a resource the match has; a refusal names the missing amounts; the treasury survives a snapshot and an economy-less match saves none), `WireCodecSizeTest` (+3: `faction_state` within its cap, about 30 bytes for an opening's state, ECO-01.4; `feedback` with 16 missing amounts; the grant), `replicationtests.faction_state_reaches_its_own_faction_only` (ECO-01.3) |
| 27 | T5: the core's own edits | `MatchState#editVoxel` records an edit (a cell: column and band level, so the slice's height decides its index when applied) with its tick in `TerrainEdits`; `blockColumn` and `releaseColumn` set a footprint in `FootprintOverlay`. The state keeps each published page's slice bytes; a page with edits or footprints is rebuilt from them, in the WORLD phase (`TerrainSystem`) every `pageK` ticks, all edited pages in one publication, and whenever a capture of it is published. A capture carries its sample tick (`TerrainPage#sampleTick`, journaled under a new record tag only when known, so older journals read and write as before): the mod captures after the step and lays the match's waiting writes over the copied blocks (`BlockWriter#pendingIn`, `WriteScheduler#forEachPending`), so the edits up to the sample tick are in the slice and are forgotten. A prewarm's tick is unknown (a restart may have lost waiting writes), so every edit stays applied over it. Edits hash and save (`terrainEdits`) only once there are any. They are never compacted: a felled forest keeps tens of thousands, a few hundred kilobytes in a save (§9 R11) |
| 28 | T5: tests and the rebuild's cost | `TerrainEditsTest` (3: a pillar edited away rebuilds its page at the next multiple of `pageK` and no other; a capture before the edits' writes keeps them applied, one of unknown tick too, one after drops them, and a later capture with the pillar back shows it; edits survive a snapshot and a restore from slices with the same hash, and an unedited match saves none), `writertests.a_capture_holds_the_writers_waiting_writes` (a stone waiting in the writer is solid in the capture while the level is still air; the capture names its tick). `TerrainEditBench.rebuildOnePage`: 307 µs for one page of a 4 × 4 grid against `PageBuilderBench.build`'s 37 µs: the publication, which joins the grid's components again, is most of it, as it is for every page the adapter publishes; hence one publication every `pageK` ticks (§5) |

### 1.7 Consent plan

- **Now (2026-10-23 on): no windows.** Every task is built headless; L4 checks run on CI's Linux
  route where a scenario can show them, and their macOS goldens wait.
- **The consent batch (T30):** the M3 goldens on macOS (worker tool and load, the crack, placement,
  selections, resource bar, idle worker, rally marker, minimap, alert, end screen, the looks at
  60 blocks with and without Sodium), PERF-14 re-run with the M3 HUD, ART-01.5's client frame pass
  (S4's armour and held-item pass), M2's `pose_high`. Pending consent blocks Accepted, not Done.

---

## 2. Design of the M3 slice

### 2.1 Components

| Component | Where | Card |
|---|---|---|
| `BalanceSheet` (relationships, provenance, overrides, Markdown and CSV) | `core.content.balance` | DATA-04 |
| Resource types, `Amount`, `Treasury`, `Cost`, payment | `core.content`, `core.sim.econ` | ECO-01 |
| Material lists, `NodeDiscovery`, `NodeStore`, `NodeIndex`, slots | `core.sim.nav`, `core.sim.econ` | ECO-02 |
| `TerrainEdits`, the core's page jobs | `core.sim.nav` | ECO-03, BLD-01 |
| `TreeNode`, `Canopy`, `ForageNode`, `FieldNode` | `core.sim.econ` | ECO-03, ECO-05, ECO-09 |
| GATHER and BUILD states | `core.sim.unitai` | ECO-05, BLD-03 |
| `BlueprintShape`, `BuildingStore`, `PlacementRules`, `Construction` | `core.sim.build` | BLD-01–03 |
| `ProductionQueue`, `Population`, rally points | `core.sim.prod` | PROD-01, ECO-06, PROD-03 |
| Scenarios, `RuleChange`, victory, pause | `core.sim`, `core.content` | MATCH-01, MATCH-05 |
| `ComputerPlayer`, `AiView`, `CommandSink`, `AiBudget`, `StrategyScript` | `core.sim.ai` | AI-01, AI-17 |
| Headless runner and telemetry | `core` testFixtures, `:core:headlessMatches` | QA-11 |
| `resource_yield` data map, `BlueprintLoader`, material capture | `mod.server.content`, `mod.server.world` | ECO-02, BLD-01 |
| `faction_state`, `building_state`, `match_event`, `match_control` | `mod.server.net.payload` | ECO-01, BLD-01, MATCH-01 |
| Command card, resource bar, idle workers, placement ghost, minimap, alerts, end screen | `core.client.hud`, `mod.client.hud`, `mod.client.rts` | UI-06/07/10/11/12, BLD-02 |
| Cracks, break particles and sounds from `fx` | `mod.client.audio`, `FxPlayer` | AUD-04 |
| Armour and held-item layers, faction leather | `mod.client.UnitRenderer` | ART-01 |

### 2.2 Content and the balance sheet (DATA-04, UNIT-04, ECO-01)

- **Resources** in data (`data/<ns>/strategos/resources/*.json`): food (subtypes fruit, grain),
  wood (tree), stone (rock), iron (ore), each with a display name and an icon item.
- **Templates:** `unit/worker` (`+Gatherer +Builder`, flees by default), `unit/infantry` (the
  spearman, M2), `building/civic_centre` (dropsite for every resource, housing, trains workers),
  `building/house` (housing), `building/barracks` (trains spearmen), `building/field`
  (`FieldNode`). Values through M1's modifier pipeline: `Cost/resources/<resource>`, `Cost/population`,
  `Cost/time`, `ResourceGatherer/rates/<resource>/<subtype>`, `ResourceGatherer/capacity`,
  `Builder/rate`, `Footprint/*`, `Housing`, `Trainer/list`, `Placement/civicReach`, `Field/*`.
- **The sheet** is computed from data by `:core:balanceSheet` into `docs/balance/sheet.md` and
  `sheet.csv`; `BalanceSheetTest` in `verify` fails on any difference, so a changed number shows in
  the diff and is approved like a golden. Relationships are `$derive` values in the templates
  (§1.6 row 22; for example "spearman cost ≈ 1.5 × worker cost"); a template that departs from a
  derived value needs `{"$override": x, "reason": "..."}`.
- **Timing targets** for the Normal opening (D-11), authored by T2 and tuned by playtests: first
  house by 1:30, barracks by 4:00, first bot wave from 8:00, a 1 v 1 against the bot within 30
  minutes. Numbers are our own, from relationships and these targets (ADR-0005).

### 2.3 Nodes (ECO-02, ECO-03, ECO-05)

- **Discovery** flood-fills the material lists (§1.6 rows 1–2) into nodes: a tree is the logs
  connected through logs (diagonals included, for acacia and fancy oak) and its leaves; a bush is
  one forage block; stone and ores cluster for M4. Each node: id, kind, resource subtype, amount
  (`Σ amount per block`), anchor (trunk base centre), work slots (§1.6 row 7), block list ordered
  top-down.
- **`NodeIndex`**: per page and per subtype, sorted by id; `nearest(subtype, x, z, maxDist, filter)`
  equals brute force, filters exhausted nodes, full slots and other components (PATH-04).
- **Trees:** each log's quota gathered removes the highest remaining log (`WorldEffect` with
  `NODE_REMOVAL`, owner the node's id) and pushes an AIR edit (§1.6 row 5); `Canopy` then lists the
  leaves of this tree farther than six leaf steps from every remaining log of any tree, and they go
  as `COSMETIC` removals under the writer budget.
- **Bushes:** `ForageNode` amount and regrowth (`Forage/regrow` per second up to its amount); the
  bush's `age` property follows `ceil(3 × amount ÷ max)`, written by the core only when it changes;
  `CropGrowEvent.Pre` (CropGrowEvent.java:30, posted by `SweetBerryBushBlock#randomTick` and
  `CropBlock#randomTick` through `CommonHooks.canCropGrow`) is denied for match nodes and fields.
- **External edits** shrink a node through the re-published page and credit nothing.

### 2.4 Gathering (ECO-05)

- `GATHER` under INDIVIDUAL: APPROACHING_NODE (WALKING) → GATHERING (`UnitState.GATHERING`, code 3)
  → RETURNING (WALKING) → back; finding the next node is a step, as finding a target is (M2 §1.6
  row 67). The slot is claimed on the order and freed when it ends.
- **Timing:** a fixed-point accumulator per gatherer; `rate` per second from the template becomes
  thousandths per tick; the load is the carried-resource byte plus `Amount`; at capacity the unit
  returns to the nearest own dropsite that accepts the resource (a counter-kept list per faction),
  delivers at the footprint edge, and walks back.
- **Commands:** `Gather` (units, a block position; the server resolves the node that holds it, or
  the nearest of its subtype within 3 blocks, else refuses); a right click on a block whose state
  has a `resource_yield` entry gathers (`OrderIntents.context`).
- **Bodies:** a working unit holds its task's tool (axe for wood, hoe for fields, nothing for
  berries, the mace for building) and, while carrying, its load as an off-hand item (log, sweet
  berries, wheat); both from the carried-resource byte and the state, set server-side on the body,
  so vanilla syncs them to trackers.

### 2.5 Buildings (BLD-01, BLD-02, BLD-03, CMB-03)

- **Blueprints** are vanilla `.nbt` structures under
  `data/<ns>/structure/strategos/buildings/`, parsed by `BlueprintLoader` (adapter, 04 §11.7) into
  a palette of state strings and a core `BlueprintShape`: size, footprint mask, blocks in build
  order (Y up, supports first, pairs together) with each block's nav class, damage ranks, markers
  (`spawn`, `door`, `rally`, `work` from data-mode structure blocks). M3's lint also refuses block
  entities, which no placeholder needs. Placeholders and test blueprints are generated in code
  (`testkit.PlaceholderBlueprints`, written to the mod's data by a devtools driver as `MapgenDriver`
  writes the map; `checkBlueprints` in `verify` keeps them equal).
- **Records:** owner, template, anchor and rotation, footprint, progress, HP (damage taken, as
  units), components (dropsite, housing, trainer, field). Instant placement for scenarios and
  tests (`/strategos build place <template> <x> <z> [rotation] [faction]`).
- **Placement** (`PlacementRules`, one class for client and server, §1.6 rows 12–13): inside the
  space, land under every footprint column, floor spread ≤ 2, no overlap with footprints, nodes or
  reserved cells, explored, civic centres ≥ 64 apart, within `civicReach`, affordable; per-cell
  verdicts with the first failing rule. `PlaceBuilding` (template, anchor, rotation, builders,
  mode) pays and creates the foundation; builders get BUILD orders.
- **Construction:** the first builder's arrival commits (§1.6 rows 5–6): the overlay blocks the
  footprint, units inside are moved to the nearest free surface, the writer clears and fills;
  `BUILD` states (APPROACHING_SITE, BUILDING with `UnitState.BUILDING`, code 4); progress in
  thousandths of the whole, each tick `Builder/rate × n^0.7 ÷ buildTime` (`DetMath.pow`), so n
  builders throughout finish in `ceil(buildTime ÷ n^0.7)` ticks; the core asks for
  `floor(p × blockCount)` blocks in build order (`CONSTRUCTION` writes); HP rises with progress.
- **Destruction:** at 0 HP the building leaves, its remaining blocks go under the budget, the
  overlay clears and the core rebuilds its pages; a civic centre's loss is a `match_event`.
- **Torches (CMB-03):** an attack on a building puts a torch in the attacker's hand and deals
  `Attack/Structure/fire` at the template's rhythm against `Resistance/fire`; flames are `fx`
  particles on hit blocks, never fire blocks; attack places round a footprint as `Engagement`
  rings round its edge.

### 2.6 Production and population (PROD-01, PROD-03, ECO-06)

- **Queue:** at most 5 entries per building, one unit each in M3 (batches are PROD-02); the cost
  paid at `Train`, refunded at `CancelTrain`; population reserved when an entry starts; progress
  in ticks; done, the unit spawns at the `spawn` marker on the nearest free surface outside the
  footprint and gets the rally orders.
- **Population:** `used` = Σ unit costs + reservations; `cap = min(Σ housing of finished buildings +
  base, match maximum)` (D-01: 150); a head entry that does not fit waits with the notice
  "housing needed" and keeps its progress.
- **Rally:** `SetRally` (buildings, a point, a node or a building); a new unit's orders are the
  rally's: a move, a gather on arrival, a walk to the building.

### 2.7 Match rules, scenarios and the end (MATCH-01, MATCH-05)

- **Scenarios** in data (`strategos/scenarios/*.json`): map, slots (human or `ai:<profile>`),
  factions, starting entities per slot, starting resources, victory rule. `first_loop` on the
  proving ground: per side a civic centre at its start slot and five workers (§1.3).
- **`MatchRules`** gain population maximum, starting resources, victory, `revealMap` and AI vision;
  copied in at creation, changed only by the journaled `RuleChange` input (`/strategos match config`).
- **Victory** per §1.6 row 18, checked in FINAL; `match_event` (alerts, the end with winner and
  duration) to every participant; the end screen on the client; resign through `match_control`.
- **Pause:** `PAUSED` stops stepping while the inbox buffers; orders given meanwhile enter the
  first tick after resume. Speed through `ServerTickRateManager#setTickRate` (0.5×, 1×, 1.5×), in
  singleplayer only; never tick freeze.
- `MobSpawnEvent.PositionCheck` is denied inside match regions.

### 2.8 The client (UI-06, UI-07, UI-10, UI-11, UI-12, BLD-02, AUD-04, ART-01)

- **View-models first** (M2 §0.3): `CommandCard`, `ResourceBar`, `IdleWorkers`, `Minimap` and
  `AlertFeed` in `core.client.hud`, L1-tested against `ClientMatchModel`, which gains the faction
  state, building states and match events; the mod draws them in the HUD regions UI-02 laid out.
- **Placement mode:** the command card's build page arms it; the ghost is the blueprint's blocks
  as translucent quads, coloured per cell by the client's run of `PlacementRules` on a terrain view
  of loaded chunks; R rotates; a left click sends `PlaceBuilding` with the selected builders.
- **Minimap:** one `NativeImage` tile per chunk from `BlockState#getMapColor` with height shading,
  uploaded to a `DynamicTexture`, refreshed at most once a second per changed chunk; dots from
  `unit_summary` and building states; the camera frame; a click sends `camera_jump`.
- **World `fx`:** new kinds `BLOCK_CRACK` (position, stage), `BLOCK_BREAK` and `BLOCK_PLACE`
  (position, block state id), `FLAME` (position), `HORN` (wave announcements); sent to `SeesCell`;
  played with `ClientLevel#destroyBlockProgress`, `ClientLevel#addDestroyBlockEffect` and the
  state's `SoundType`, within `SoundBudget`.
- **Looks:** `UnitRenderer` adds vanilla's armour and held-item layers; faction colour through
  dyed leather; role through tools and weapons (D-04, ART-01's guide).

### 2.9 The AI and the headless runner (AI-01, AI-17, QA-11)

- **`ComputerPlayer`** per AI slot runs in the AI phase every 20 ticks, offset by slot, early on
  an attack or a lost building; reads `AiView` (its faction in full, others through visibility,
  everything until fog); writes only through `CommandSink`, whose commands enter the next tick's
  COMMANDS phase under a player slot of its own and pass the same validators; `AiBudget` caps a
  think at 5,000 evaluation units.
- **`StrategyScript`** (`data/<ns>/strategos/ai/<profile>.json`): a build order with conditions,
  worker ratios for wood and food, continuous spearmen, an attack schedule; each wave musters at a
  visible point and is announced ≥ 30 s ahead (notice, countdown, `HORN` to the opponents);
  placement templates per start slot.
- **Headless runner:** `:core:headlessMatches -Pseeds=1..10` plays bot against bot in L1 (§1.6
  row 16) and writes `build/reports/headless/summary.json` with the telemetry; an exception, two
  game minutes without an AI command, or no winner by 30 minutes fails; a failing seed's journal
  replays in L1; `aitests` plays two seeds in L3 inside `verifySlow`;
  `/strategos skirmish <map> observe seed=<n>` opens a seed for the owner to watch.

---

## 3. Build changes

- `core/build.gradle`: `balanceSheet` (its check is `BalanceSheetTest`, in `verify`); `headlessMatches`
  (`-Pseeds`, `-Plimit`); the JMH kernels `NodeIndexBench.nearest`, `ConstructionBench.tick` and
  `GatherBench.tick600` with `@HotPath` where they are kernels, and their baselines proposed.
- `mod/build.gradle`: `runBlueprintgen` (devtools) and `checkBlueprints` (in `verify`); the
  `first_loop` scenario for `runScenario`; `perftests.first_loop_soak` under the perf gates.
- Data: `resource_yield` defaults, resources, templates, blueprints, `first_loop`, the bot's
  profile, `balance/relationships.json`.

---

## 4. Tests per level

| Level | M3 adds |
|---|---|
| L0 | ArchUnit: `core.sim.ai` never calls a state mutator; `core.sim.econ`, `build`, `prod` import no Minecraft; B6 for the four new payloads |
| L1 | The sheet; treasury, payment and conservation over 200 seeds; discovery, index and slots; felling, canopy, gathering, forage, fields; placement, construction (n^0.7), production, population, rally; torches; victory, rule changes, pause; the AI's decisions and budget; ten headless matches; the client's view-models |
| L2 | `faction_state`, `building_state`, `match_event`, `match_control` and the world `fx` round trips and caps; blueprints round-trip and lint (`:mod:test`) |
| L3 | `ecotests.*`, `buildtests.*`, `prodtests.*`, `matchtests.*`, `aitests.*`, the new `combattests`, `nettests` and `cameratests` |
| L4 | The M3 scenarios on CI's Linux route; macOS goldens in the consent batch |
| L5 | `runServerSmoke` with 600 ticks of AI versus AI on the proving ground |
| L6 | `perftests.node_index_proving_ground`, `perftests.first_loop_soak` (§6); ART-01.5 and PERF-14 on this Mac with consent |
| L7 | The owner's match against the bot and the questionnaire |

---

## 5. Measurements before building on them

No spike gates M3. Two numbers are measured where they first appear and decide their task's shape:
- **The core's page rebuild** (T5): the cost of one page job from slice, edits and overlay, and the
  rebuilds a tick a forest of 30 workers causes; if a tick's rebuilds exceed 0.5 ms at p95, edits
  coalesce per page over `pageK` ticks. Measured: 0.31 ms a rebuild, most of it the publication, so
  edits coalesce every `pageK` ticks into one publication (§1.6 row 28); the soak (T29) reports the
  WORLD phase's share.
- **Discovery** (T6): the proving ground indexed in prepare under 2 s (ECO-02.5), else discovery
  becomes a job per page cluster.

## 6. The first-loop soak (T29) — PERF-06

The M3 row of the budget matrix: on the proving ground, two factions of 100 units (60 workers
gathering, 40 spearmen trading attack-moves), 20 buildings and three constructions running, for
15 game minutes paced at 20 ticks a second (M1 §1.6 row 162); budgets A–E, G when an autosave
falls inside; per-unit p95 of the unit-driven buckets within 15 % of M1's baseline; a 2× stress
pass (400 units, six constructions) holding C p95 ≤ 50 ms; MATCH-05.4's C p95 ≤ 15.3 ms; the
report lists placement, writer and `fx` times.

---

## 7. Task breakdown

### 7.1 Order

- **Headless, in this order:** T1 (health check), T2 (DATA-04), T3 (UNIT-04), T4 (ECO-01), T5 (the
  core's own terrain edits), T6 (ECO-02), T7 (BLD-01), T8 (ECO-03), T9 (ECO-05), T10 (AUD-04),
  T11 (BLD-02), T12 (BLD-03), T13 (ECO-06), T14 (PROD-01), T15 (PROD-03), T16 (ECO-09), T21
  (CMB-03), T22 (MATCH-01), T25 (MATCH-05), T26 (AI-01), T27 (AI-17), T28 (QA-11), T29 (PERF-06),
  then the client cards T17 (UI-06), T18 (UI-07), T19 (UI-10), T23 (UI-11), T24 (UI-12), T20
  (ART-01), their view-models and drawing headless, their scenarios on CI's Linux route.
- **With consent:** T30 (the consent batch); T31 closes.
- The critical path is DATA-04 → UNIT-04 → ECO-05 → ECO-09 → AI-17 → QA-11; client tasks may move
  earlier when a core task waits on a measurement.

### 7.2 Tasks

#### T1 · Health check from M2's close · ~1,200 lines moved
- Split `RtsMode` (780 lines) by concern, `HfsmTransitionsTest` and `PerfTests` (over 800 each) by
  area; no behaviour change (§1.6 row 21). **Verification:** `verify`; the golden journals unchanged.

#### T2 · Balance sheet — DATA-04
- **Files:** `$derive` and `$override` in `MergeOps`, `Derivations`; `core.content.balance`;
  `testkit.BalanceData`; `balance/provenance.json`, `balance/targets.json`; `docs/balance/sheet.md`,
  `sheet.csv`; `balanceSheet` (§1.6 rows 22–23). **Verification:** `verify`.

#### T3 · Starting units and the worker model — UNIT-04
- **Files:** `unit/worker.json`; class tokens `Gatherer`, `Builder`; `Behaviour/flee` and `Fleeing`; the sheet's
  rows; `WorkerModelTest`, `contenttests` (§1.6 row 24). **Verification:** `verify`.

#### T4 · Resources and treasury — ECO-01
- **Files:** `core.sim.econ` (`Amount`, `ResourceDef`, `EconomyRules`, `Treasury`, `Costs`); `MatchRules.economy`;
  `strategos/resources/*.json`, `ResourceFiles`; the invariant in FINAL; `Grant`; `faction_state`; the
  missing amounts in `CommandResult` and `feedback` (§1.6 rows 25–26). **Verification:** `verify`.

#### T5 · The core's own terrain edits — for ECO-03 and BLD-01
- **Files:** `TerrainEdits`, `FootprintOverlay`, `TerrainSystem`, `MatchState#editVoxel`; `TerrainPage`'s sample
  tick; `SliceCapture`'s waiting writes; `TerrainEditBench` (§1.6 rows 27–28).
  **Verification:** `verify`; `TerrainEditsTest`; `writertests`.

#### T6 · Resource nodes — ECO-02
- **Files:** `resource_yield` data map (registration, defaults, inventory entry in ADR-0020's
  surface); material lists in `SliceCapture` and `TerrainPage`; `NodeDiscovery`, `NodeStore`,
  `NodeIndex`; `perftests.node_index_proving_ground`. **Verification:** `verify verifyPerf`.

#### T7 · Building records and blueprints — BLD-01
- **Files:** `EntityStore` kinds and `unitAt`; `core.sim.build`; `BlueprintLoader`,
  `BlueprintLint`; `testkit.PlaceholderBlueprints`, `runBlueprintgen`, `checkBlueprints`;
  `building_state`; `/strategos build place`; selecting buildings on the client.
  **Verification:** `verify`.

#### T8 · Woodcutting and the canopy — ECO-03
- **Files:** `TreeNode`, `Canopy`; ADR-0024; tree fixtures per species (oak, birch, spruce,
  jungle, acacia, dark oak, cherry, mangrove; 2 × 2 spruce, jungle and dark oak; touching
  canopies), in L1 as voxel fixtures and in L3 grown from vanilla's configured features.
  **Verification:** `verify`.

#### T9 · Gathering, dropsites and berry bushes — ECO-05
- **Files:** GATHER states, `Gather`, `ForageNode`; the right click; tools and loads on bodies;
  the proving ground v2 (§1.6 row 15). **Verification:** `verify verifySlow verifyPerf`.

#### T10 · World-edit feedback — AUD-04
- **Files:** the new `fx` kinds, `FxSender`'s `SeesCell` route, `FxPlayer`'s cracks, particles and
  sounds. **Verification:** `verify`; the crack on CI's Linux route.

#### T11 · Placement mode and validators — BLD-02 · T12 · Progressive construction — BLD-03
- **Files:** `PlacementRules`, `PlaceBuilding`, the ghost; `Construction`, BUILD states, the commit
  and its eviction; `buildtests`. **Verification:** `verify verifySlow`.

#### T13 · Population — ECO-06 · T14 · Production queue — PROD-01 · T15 · Rally points — PROD-03
- **Files:** `core.sim.prod`; `Train`, `CancelTrain`, `SetRally`; `prodtests`.
  **Verification:** `verify`.

#### T16 · Fields with manual reseed — ECO-09
- **Files:** `FieldNode`, `Reseed`, the field blueprint, crop ages through the writer, the
  grow-event denial. **Verification:** `verify`.

#### T17 · Selection panel and command card — UI-06 · T18 · Resource bar — UI-07 · T19 · Idle workers and notices — UI-10
- **Files:** `core.client.hud` view-models; `mod.client.hud` drawing; the scenarios
  `rts_economy` for CI. **Verification:** `verify`; CI's Linux route.

#### T20 · Placeholder look and the style guide — ART-01
- **Files:** `docs/art/style-guide.md`; the three placeholder blueprints; `UnitRenderer`'s layers.
  **Verification:** `verify`; CI's Linux route; the L6 client pass in T30.

#### T21 · Attacking buildings with torches — CMB-03
- **Files:** `Attack/Structure`, `Resistance/fire`, building attack places, `FLAME` fx.
  **Verification:** `verify verifySlow`.

#### T22 · Scenarios, rules and victory — MATCH-01 · T25 · Pause and game speed — MATCH-05
- **Files:** scenarios in data, `RuleChange`, victory, `match_event`, `match_control`, the end
  screen; `PAUSED`, the tick rate. **Verification:** `verify`.

#### T23 · Minimap — UI-11 · T24 · Under-attack alert — UI-12
- **Files:** `Minimap`, `AlertFeed`; their drawing and keys. **Verification:** `verify`; CI.

#### T26 · AI framework — AI-01 · T27 · Scripted bot — AI-17 · T28 · Headless match runner — QA-11
- **Files:** `core.sim.ai`; `ai/first_loop.json`; `headlessMatches`; `aitests`; the smoke's AI
  phase; `/strategos skirmish`. **Verification:** `verify verifySlow`; ten seeds.

#### T29 · First-loop perf gate — PERF-06
- **Files:** `perftests.first_loop_soak`, its report. **Verification:** `verifyPerf`.

#### T30 · The consent batch
- §1.7's list, in one sitting with the owner's consent.

#### T31 · M3 close
- §8 walked into results; health check; docs; STATE with the demo and the owner's items.

### 7.3 Progress

- [x] T1 (2026-10-23) · [x] T2 (2026-10-23) · [x] T3 (2026-10-23) · [x] T4 (2026-10-23) · [x] T5 (2026-10-23) · [ ] T6 · [ ] T7 · [ ] T8 · [ ] T9 · [ ] T10 · [ ] T11
- [ ] T12 · [ ] T13 · [ ] T14 · [ ] T15 · [ ] T16 · [ ] T17 · [ ] T18 · [ ] T19 · [ ] T20 · [ ] T21
- [ ] T22 · [ ] T23 · [ ] T24 · [ ] T25 · [ ] T26 · [ ] T27 · [ ] T28 · [ ] T29 · [ ] T30 · [ ] T31

---

## 8. Acceptance checklist (M3 exit)

The roadmap's Done gate, then the owner's acceptance.

| # | Criterion | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the M3 cards passes at its level | §1.4 |
| 2 | L1: gather, carry and walk timing; builder progress (n^0.7); production, population and treasury conservation over 200 seeds; the bot's decisions | `GatherTimingTest`, `ConstructionTest`, `ProductionTest`, `TreasuryInvariantTest`, `AiDecisionTest` |
| 3 | L3: a worker gathers and delivers wood and berries; builders finish a house equal to its blueprint; trained units spawn by the door and follow a rally point; ten spearmen burn down a house; a field yields and is reseeded | `ecotests`, `buildtests`, `prodtests`, `combattests` |
| 4 | QA-11: ten L1 seeds and an L3 sample of two each end with a winner within 30 game minutes, no exception, no stall; no AI training queue waits for food longer than 60 s after minute 5; every report carries the telemetry | `headlessMatches`, `aitests` |
| 5 | L6: the first-loop soak within A–E (G with an autosave), per-unit costs within 15 % of M1's baseline; the 2× pass holds C p95 ≤ 50 ms | `perftests.first_loop_soak` |
| 6 | L5: 600 ticks of AI versus AI on the proving ground on a dedicated server | `runServerSmoke` |
| 7 | The consent batch: the M3 goldens, PERF-14 re-run, ART-01's armour and held-item pass | T30 |
| 8 | M1's and M2's gates still pass; `verify`, `verifySlow`, `verifySoak`, `verifyPerf` green; CI green | the runs |
| 9 | L7 (decision gate 3): the owner plays a full match against the bot; "would I play another?" in STATE.md with its reason; a "no" or any score ≤ 2 sends pacing and readability back for tuning | the owner's note |

---

## 9. Risks and fallbacks

| # | Risk | Sign | Fallback |
|---|---|---|---|
| R1 | Core page rebuilds cost too much in a busy forest | §5's measurement | Coalesce edits per page over `pageK` ticks; rebuild only pages whose units' layers changed |
| R2 | A captured slice and the core's edits disagree (a write pending across a capture) | T5's L1 agreement test, the replay check | The sample tick's rule (§1.6 row 5); the edit list kept per page for the match's life |
| R3 | Discovery is slow on big maps | ECO-02.5 | A job per page cluster, delivered before READY |
| R4 | The writer falls behind three constructions and felling | BLD-03.6, the soak's writer time | Construction is core state; only the picture lags (04 §11.7); priorities keep removals first |
| R5 | Vanilla grows or decays a match block (random ticks near a first-person player) | ECO-05.7, ECO-09.3, ECO-03.5 | Deny the grow events; restore through `LevelChunkMixin`'s protection (04 §11.9) |
| R6 | The bot stalls or never wins | QA-11's watchdog and limit | Simpler build order; waves that grow until one wins |
| R7 | Pacing misses 30 minutes | the headless telemetry | Tune the sheet (DATA-04), never code |
| R8 | A golden journal's hash changes with the economy | `GoldenJournalTest` | Empty stores hash as before (§0.3); else re-record with a reason |
| R9 | Client tasks cannot be checked without a window | CI's Linux route fails to show it | The view-model's L1 test and the scenario on CI; the rest waits for T30, and the owner is told |
| R10 | New template ids shift the net indices saves and the built-in set rely on (`building/` sorts before `unit/`) | `TemplateSetTest`, the released fixtures | The built-in set and a session's table start from a pinned order (M2's ids first), new ids appended (T7) |
| R11 | The core's terrain edits grow without bound on pages never captured again | a save's size, budget G | Bake a page's edits into a slice the snapshot carries, when a page holds more than a few hundred |

---

## 10. Owner inputs

| Input | When | Blocks |
|---|---|---|
| Consent for client windows | T30, or earlier if a task stalls | L4 macOS goldens, ART-01.5, PERF-14 |
| The balance sheet's first numbers | after T2, at any visit | nothing: playtests tune them |
| The placeholder look and the style guide | T20, T30 | Accepted |
| The match against the bot and the questionnaire | after the demo | Accepted; decision gate 3 |
| Owner-built blueprints (optional) | any time | nothing |
