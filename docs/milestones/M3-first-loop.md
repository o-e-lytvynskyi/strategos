# M3 — First loop: implementation spec

*Written 2026-10-23 by the assistant after M2 closed, from the 27 approved M3 cards in
[roadmap/M3-first-loop.md](../roadmap/M3-first-loop.md), [04-architecture.md](../04-architecture.md)
(cited as "04 §n"), the ADRs in [adr/](../adr/README.md), chiefly
[ADR-0021](../adr/0021-economy-and-construction-model.md), and M1's and M2's specs and code
([M1-movement.md](M1-movement.md), [M2-command.md](M2-command.md), cited as "M1 §n", "M2 §n").
Status: **Done** (2026-10-24, §8.1). The decisions M3 needs were taken as recommended on 2026-10-21: D-25 (a),
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
| ECO-01 | T4 | 1 `TreasuryInvariantTest` over 200 seeded streams (L1) · 2 `PaymentTest.anUnpayableCommandNamesTheMissingAmounts`, then `ConstructionTest.anUnpayablePlacementNamesTheMissingWood` (T11) and T14's (L1) · 3 `replicationtests.faction_state_reaches_its_own_faction_only` (L3) · 4 `WireCodecSizeTest.aFactionStateRoundTripsWithinItsCap`, its audience declared (L2) |
| ECO-02 | T6 | 1 `NodeDiscoveryTest.discoveryEqualsAReferenceFloodFill` (L1) · 2 `NodeDiscoveryTest.theNearestNodeEqualsBruteForce` (L1) · 3 `NodeDiscoveryTest.slotClaimsNeverExceedANodesSlots` (L1) · 4 `ecotests.a_resource_yield_entry_makes_a_node`, `MaterialTableTest` (L3, L2) · 5 `NodeDiscoveryBench.provingGround` under 2 s (L6) |
| BLD-01 | T7 | 1 `BlueprintFilesTest.theCommittedBlueprintsAreThePlaceholders`, `aWrittenBlueprintReadsBackEqual…` (L2) · 2 `BlueprintFilesTest.theLintRefusesABurningBlockAMissingDoorAndARankedBottomRing` (L2) · 3 `buildingtests.civic_centre_accepts_everything_and_houses` (L3) · 4 `buildingtests.units_path_around_a_footprint_with_no_page_job` (L3) · 5 `buildingtests.building_state_owner_fields_to_the_owner_only` (L3) |
| ECO-03 | T8, T9 | 1 `WoodcuttingTest.oneWorkerGathersTheSheetAmountInTheSheetTime` (L1) · 2 `TreeFellingTest.everySpeciesFallsTopDownLogByLogAndNoDecayableLeafIsLeft` (L1) · 3 `TreeFellingTest.leavesANeighbouringTreeHoldsStay` (L1) · 4 `TreeFellingTest.aTwoByTwoTreeTakesItsTwelveWorkers` (L1) · 5 `ecotests.no_decayable_leaf_lingers` (L3) · 6 `ecotests.fell_then_walk_the_clearing_with_no_page_job` (L3) |
| ECO-05 | T9, T30 | 1 `GatherTimingTest.aTripIsTheLoadAtTheRateAndTwoWalksOverSeededRuns` (L1) · 2 `GatherRetargetTest` (L1) · 3 `DropsiteTest` (L1) · 4 `GatherSlotsTest.thirtyWorkersOneForest` (L1) · 5 `GatherTimingTest.aSpearmanGathersWhenDataSaysSo` (L1) · 6 `ForageTest` (L1) · 7 `ecotests.bushes_change_only_through_the_core` (L3) · 8 `ecotests.gather_deliver_and_save` (L3) · 9 `worker_tool_and_load` golden at 60 blocks (L4, T30) · 10 the owner's look (L7) |
| AUD-04 | T10, T30 | 1 `nettests.world_fx_reach_who_sees_the_cell` with a positive control (L3) · 2 `WireCodecSizeTest.aSixtyFourPlacementTickFitsTheFxCap` (L1) · 3 `log_crack` golden (L4, T30) · 4 the owner's checklist (L7) |
| BLD-02 | T11, T30 | 1 `PlacementRulesTest`, the numeric limits included (L1) · 2 `PlacementParityTest` (L1) · 3 `constructiontests.a_placement_the_server_refuses_has_a_reason` (L3) · 4 `placement_valid`, `placement_invalid` goldens (L4, T30) |
| BLD-03 | T12 | 1 `ConstructionTest.buildTimeForOneTwoFiveAndTenBuilders` (L1) · 2 `constructiontests.a_finished_house_equals_its_blueprint` (L3) · 3 the same test's watch on the committed footprint (L3) · 4 `constructiontests.save_mid_build_resumes_at_the_same_block` (L3) · 5 `constructiontests.writer_budget_never_changes_the_hash` (L3) · 6 `constructiontests.three_sites_within_the_writer_budget` (L3) |
| ECO-06 | T13 | 1 `PopulationTest.invariantsOverSeededTrainingAndDeaths` (L1) · 2 `PopulationTest.trainingWaitsAtTheCapAndResumes` (L1) · 3 `PopulationTest.theCapNeverExceedsTheMatchMaximum` (L1) · 4 `prodtests.a_queue_waits_with_housing_needed` (L3) |
| PROD-01 | T14 | 1 `ProductionTest.noResourceLostOrDuplicated` (L1) · 2 `ProductionTest.aSixthEntryIsRefused` (L1) · 3 `ProductionTest.aFullCapPausesTheHeadKeepingProgress` (L1) · 4 `prodtests.a_unit_spawns_at_the_door_on_a_surface` (L3) |
| PROD-03 | T15, T30 | 1 `RallyTest.workersRalliedToATreeGatherOnArrival` (L1) · 2 `prodtests.a_rally_point_survives_save` (L3) · 3 `rally_marker` golden (L4, T30) |
| ECO-09 | T16 | 1 `FieldTest.yieldFollowsDiminishingReturns` (L1) · 2 `FieldTest.anEmptyFieldWaitsForAPaidReseed` (L1) · 3 `ecotests.crops_change_only_through_the_core` (L3) · 4 `ecotests.a_field_yields_and_is_reseeded` (L3) |
| UI-06 | T17 | 1 `CommandCardLayoutTest` at every resolution and GUI scale (L1) · 2 `CommandCardTest.oneTrainClickSendsOneCommand`, view-model equals model (L1) · 3 `replicationtests.the_queue_reaches_its_owner_as_the_server_has_it` (L3) · 4 `select_worker`, `select_civic_centre`, `select_barracks` goldens (L4, consent) |
| UI-07 | T18 | 1 `IncomeTest.perMinuteMatchesAGatheredMinute` (L1) · 2 `ResourceBarModelTest` (L1) · 3 `replicationtests.faction_state_equals_the_server` (L3) · 4 `resource_bar` golden (L4, consent) |
| UI-10 | T19 | 1 `IdleWorkersTest.theKeyCyclesInStableOrder` (L1) · 2 `EconomyNoticesTest.oncePerCause` (L1) · 3 `idle_worker` golden (L4, consent) |
| ART-01 | T20 | 1 the placeholder blueprints pass the lint and T12's GameTests (L3) · 2 `docs/art/style-guide.md` with the per-class ranges (D) · 3 worker and spearman goldens at 60 blocks, with and without Sodium (L4, consent) · 4 the owner's look (L7) · 5 200 armoured humanoids within 6 ms with Sodium, client tick ≤ 1 ms (L6 client, consent) |
| CMB-03 | T21 | 1 `StructureAttackTest.tenSpearmenBurnAHouseInTheSheetTime` (L1) · 2 `StructureAttackTest.woodBurnsTwiceAsFastAsStone` (L1) · 3 `StructureAttackTest.torchesOnlyAgainstBuildings` (L1) · 4 `combattests.a_burn_makes_no_fire_block` with a spreading control (L3) · 5 `combattests.a_burnt_building_is_removed_and_cleared` (L3) |
| MATCH-01 | T22 | 1 `matchtests.a_scenario_loads_exactly_its_entities` (L3) · 2 `VictoryTest.theLastCivicCentreEndsTheMatchWithin20Ticks` (L1) · 3 `RuleChangeTest.journaledAndReplayedToTheSameHash` (L1) · 4 `matchtests.no_hostile_spawns_inside_a_match` with a control (L3) · 5 `matchtests.match_event_reaches_every_participant` (L3) · 6 `end_screen` golden (L4, consent) |
| UI-11 | T23 | 1 `MinimapImageTest.theProvingGroundsPixelsHashAsStored` (L1) · 2 `MinimapMappingTest` (L1) · 3 `cameratests.a_minimap_click_jumps` (L3) · 4 `minimap` golden (L4, consent) |
| UI-12 | T24 | 1 `AttackAlertTest.aSustainedAttackRaisesOneAlert` (L1) · 2 `cameratests.the_alert_key_jumps` (L3) · 3 `attack_alert` golden (L4, consent) |
| MATCH-05 | T25 | 1 `PauseTest.aPausedRunReachesTheSameHash` (L1) · 2 `PauseTest.ordersGivenInPauseApplyOnResume` (L1) · 3 `matchtests.pausing_one_match_leaves_another_stepping` (L3) · 4 C p95 ≤ 15.3 ms in `perftests.first_loop_soak` (L6) |
| AI-01 | T26 | 1 ArchUnit `aiNeverWritesState` (L0) · 2 `AiDecisionTest` on synthetic states (L1) · 3 `AiDecisionTest.aThinkNeverExceedsItsCap` (L1) · 4 `AiDecisionTest.itsCommandsPassTheValidatorsAHumansPass` (L1) |
| AI-17 | T27 | 1 `ScriptedBotTest.completesItsBuildOrderAndLaunchesTheFirstWaveOnTime` (L1) · 2 `ScriptedBotTest.everyWaveIsAnnouncedThirtySecondsAhead` (L1) · 3 `aitests.the_wave_announcement_reaches_the_opponents` (L3) · 4 `ScriptedBotTest.eachThinkStaysWithinTheBudget` (L1) |
| QA-11 | T28 | 1 `:core:headlessMatches`: ten seeds, one summary (L1) · 2 `HeadlessMatchesTest.anExceptionAStallOrNoWinnerFails` (L1) · 3 `aitests.seed_one_replays_to_its_hash`, `seed_two_replays_to_its_hash` (L3) · 4 `runServerSmoke`'s 600 ticks of AI versus AI (L5) · 5 `maptests.skirmish_observe_seats_the_observer` (L3) |
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
| 15 | The proving ground for M3 | Version 2 adds a grove near each start slot and berry bushes behind it, off the lanes M1's and M2's soaks walk, and keeps its trees. Its terrain hash changes; golden journals and soak baselines on it are re-recorded if their hashes change, with the reason in `REBASELINE.md` (T9; as built, row 43: none needed it) |
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
| 29 | T6: materials and discovery | The data map `strategos:resource_yield` (`kind`: log, leaves, stone, ore, forage, crop; `resource`, `subtype`, `amount`) ships `#minecraft:logs` (20 wood a log), `#minecraft:leaves` (nothing), `#minecraft:base_stone_overworld` (5 stone), `#c:ores/iron` (10 iron) and `minecraft:sweet_berry_bush` (60 food); its numbers are in the balance sheet with their provenance. `MaterialTable` gives every block state its entry and is rebuilt with the class table; a page job finds each voxel's material off the main thread, the waiting writes' blocks winning, into the slice's `SliceMaterials` (a legend and a sparse, ascending list), written after the classes only when non-empty. `NodeDiscovery` runs when every page is first published: logs joined through their 26 neighbours are a tree, its blocks top-down, its amount their sum, its anchor the centre of its lowest layer, its slots the 8 cells round a 1 × 1 base or 12 round a 2 × 2 (whether a unit can stand there is the gathering's check); each forage block is a node; rock and ore are not discovered until ECO-07 and ECO-08, whose outcrops and deposits differ, and leaves stay in the slices for the canopy (ECO-03). A material whose resource or subtype the match lacks makes no node. `NodeIndex` buckets nodes by resource, subtype and page; `MatchQueries#nodes` copies them for cold readers. Nodes hash and save only when there are any. A re-published page does not shrink nodes yet; T8 adds it with felling. The proving ground's only forest is in its east (60 oaks round X 208–248), so its v2 (T9) adds one near the west start too, for a fair first loop |
| 30 | T6: tests and numbers | `NodeDiscoveryTest` (5: over 20 seeds of synthetic forests with 2 × 2 trunks, diagonal branches and bushes, the trees equal a reference flood fill and every amount the sum of its logs (ECO-02.1); a resource the match lacks is no node, and the ring is 8 or 12; 2,000 nearest-node queries with filters equal brute force (.2); 20,000 seeded claims and releases by 30 workers never hold more slots than a node has (.3); a match finds its nodes once its pages are in and keeps them through a snapshot and a restore with the same hash, and a match without materials saves none), `MaterialTableTest` (3: the bundled entries decode, a bad one is refused with its reason, any block with an entry has its material in every state: no code), `ecotests.a_resource_yield_entry_makes_a_node` (a three-log oak is a 60-wood tree with 8 slots, a berry bush 60 food, a melon none: ECO-02.4). `NodeDiscoveryBench.provingGround`: 32.8 ms for the proving ground's 576 slices and 60 oaks, against ECO-02.5's 2 s |
| 31 | T7, part 1: building templates and blueprints as content | A building's template has a `Building` component (`blueprint`, `material`) and decodes to a template of mobility `building`, with no body; its numbers are value paths (`Health/max`, `Cost/...`, `Housing/capacity`) and its lists text paths (`Dropsite/resources`, `Trainer/units`). The compiler lays the named blueprint into the flattened JSON under `Blueprint` (size, palette, blocks in build order, damage ranks, markers), so the set's version, journals and client copies carry the shape and `fromFlattened` needs nothing else; the balance sheet leaves the shape out. `PlaceholderBlueprints` (core) draws the civic centre (11 × 7 × 11), house (5 × 5 × 5) and barracks (9 × 6 × 7) in non-flammable blocks (stone bricks, cobblestone, packed mud, mud bricks, crimson planks), each with a north doorway holding its door and spawn markers; build order is Y, then X, then Z; ranks are -1 for the two lowest layers of the outer ring and otherwise a hash of id and index raised above the block below. The core's blueprint rules (a door, known marker kinds, ranks that respect supports and spare the bottom ring, a spawn marker for a trainer) and the mod's palette lint (every state parses, none burns, none holds a block entity) refuse a bad set. The built-in set pins M2's units at net indices 0–2 (§9 R10); the buildings come after them; a session's table starts from the built-in order, so saves keep their indices. `SpawnUnits` refuses a building template. The mod's `.nbt` files, the loader and their round trip are part 3 |
| 32 | T7, part 2: buildings in the core | Buildings keep a store of their own (`BuildingStore`), not the units' `EntityStore`: §1.6 row 3 planned a kind byte there, but every unit system reads that store (19 loops), so instead a building's handle has the units' form with its slot index from `BuildingStore.BASE` (2^19) up, and one `int` still names a unit or a building in commands, queries and selections. A record: template, faction, where it stands (`Footprint`: the blueprint's corner, a quarter-turn rotation; block states are not rotated, which M3's facing-free placeholders allow), the height its lowest layer takes, state (foundation, constructing, complete), progress in thousandths, damage taken. `SpawnBuilding` (admin, wire id 8) places a finished one for scenarios and tests: inside the space, on no other footprint, its floor in place of the ground's top block at the highest standing height under it; its columns are blocked (`FootprintOverlay`), and the writer is asked to clear the volume and place the blocks in build order (`CONSTRUCTION`). A blow on a building (any damage packet, so vanilla blows reach it too) takes its amounts until CMB-03 gives buildings resistances; at its most it falls: `BuildingDestroyed`, its blocks above the floor removed (`DAMAGE_STAGE`, top-down) and edited to air, its columns released. Buildings hash and save once one stood; a restore blocks their columns again before the pages publish. `MatchQueries#buildings` copies them with their dropsite resources and housing |
| 33 | T7, part 2: tests | `BuildingTest` (7: a spawned house blocks its 5 × 5 columns after the rebuild and asks for its 125 blocks; a walker crosses past a civic centre without setting foot in it (BLD-01.4 in L1); a house hit for 150 has 250 left, falls to a lethal blow, removes every block above its floor and gives the ground back exactly; a turned barracks spans 7 × 9 with its doorway east; buildings survive a snapshot and a restore with the same hash and their footprint blocked; a civic centre takes food, wood, stone and iron and houses 20 (BLD-01.3's core); placement is refused on a footprint, off the space, for a unit template and to a player) |
| 34 | T7, part 3: files, payload, command | `BlueprintLoader` reads and writes vanilla structures (`size`, `palette` or the first of `palettes`, `blocks` with `pos` and `state`, StructureTemplate.java:611–650): a data-mode structure block is a marker, its `metadata` the kind, so the door marker stands in the doorway's lower cell and the spawn marker above it; reading builds order and ranks by `PlaceholderBlueprints.build`, so a file's block order never matters. The reload listener reads `data/<ns>/structure/strategos/buildings/*.nbt` with the templates and compiles with them; the three placeholders ship as files (0.5–1.5 KB each; `checkJarContents` now keeps only map tiles out), written by `BlueprintFilesTest` with `-Dstrategos.blueprints.update=true`. `building_state` (per seated player, at the summaries' 2 Hz, when it changed; the owner's entries `own` with the production queue and rally point, which PROD-01 and PROD-03 fill; the enemy's without) and the client's `ClientMatchModel#buildingAt`, which maps a clicked column to a building by its blueprint. `/strategos build place <template> <pos> [rotation] [faction]` sends `SpawnBuilding`. Selecting a building on screen comes with UI-06's panel (T17), which shows it; no BLD-01 acceptance item needs it |
| 35 | T7, part 3: tests | `BlueprintFilesTest` (3: the committed files read back as the placeholders, a written blueprint reads back equal even listed backwards (BLD-01.1); the lint refuses oak planks, a blueprint without its door and a ranked corner of the bottom ring (.2)), `BundledTemplatesTest` now through the files, `buildingtests.civic_centre_accepts_everything_and_houses` (food, wood, stone and iron, 20 housing, its wall and floor raised by the writer: .3), `buildingtests.units_path_around_a_footprint_with_no_page_job` (a walker round a civic centre, no page captured after the placement: .4), `buildingtests.building_state_owner_fields_to_the_owner_only` (.5) |
| 36 | T8: felling and the canopy | `Felling.take` takes what a gatherer asks from a tree; whenever a log's worth has gone (the tree keeps `ceil(left ÷ per log)` logs), its highest standing log goes as a `NODE_REMOVAL` write and as air in the core's terrain. Then the canopy thins by vanilla's rule (ADR-0024): the leaves in the tree's box grown by six that are farther than six leaf steps from every standing log of any tree go as `COSMETIC` writes; the core reads leaves and logs from its slices' materials less its own edits (`MatchState#materialsIn`), never the level. A capture after someone else's edit that no longer shows a node block the core still counts, and did not take itself, makes the node lose it and its yield, crediting nobody. ECO-03.1 (the balance-sheet amount in the balance-sheet time), .5 and .6 need a gathering worker: T9 |
| 37 | T8: tests | `TreeFellingTest` (4): on the shapes of ten species (oak, birch, spruce, cherry, mangrove with prop roots, dark oak, jungle, giant spruce, a leaning acacia, a branching fancy oak; `testkit.TreeFixtures`, drawn from how they look, not from vanilla's code), each whole to start with, every log's worth takes the highest log and leaves no leaf vanilla would decay, by a reference relaxation, until none stands (ECO-03.2); the leaves a neighbouring oak holds stay when the other is felled, exactly those (.3); a log broken by someone else leaves the tree with its 20 wood and credits nobody; a 2 × 2 dark oak takes twelve workers and refuses a thirteenth (.4) |
| 38 | T9: gathering | `OrderKind.GATHER` (code 7, saved `gather`) holds its node's id as its target. Under INDIVIDUAL, GATHER (code 11) has GATHER_APPROACHING (12, reports WALKING), GATHERING (13, `UnitState.GATHERING`, code 3) and GATHER_RETURNING (14, WALKING). The approach drops a load of another resource, claims the free slot nearest the unit whose cell is standable (a passable floor within 2 blocks of the node's base, no footprint) and walks there; an arrival within 1.5 blocks of the slot works. Each tick the unit's rate (`ResourceGatherer/rates/<resource>/<subtype>`, in thousandths a second) adds to a remainder in the leaf's first data word, and each whole twentieth leaves the node (`Felling` for a tree, `Forage` for a bush), up to `ResourceGatherer/capacities/<resource>`. A full load goes to the faction's nearest finished building whose `Dropsite/resources` lists it (read through modifiers, measured to the footprint's edge), to the cell beside that edge nearest the unit; within 1.5 blocks of the footprint it is delivered as `gathered`, and the unit goes back. A spent node, or one with no room, gives way to the nearest of its subtype within 24 blocks of it with room. With none, a unit carrying something takes it home first, then stands idle with an `IDLE_WORKER` notice (`no node`); with no dropsite it keeps its load (`no dropsite`). The slot is held from its claim until the unit leaves GATHER (a leave hook) or dies; a dead unit's load is dropped. Hit while gathering, a unit hits back or flees (UNIT-04). The worker picks 1 food a second, chops 0.6 wood a second and carries 10 of each: 200 ticks for a load of berries, 334 of wood, as the sheet's new Gathering section says |
| 39 | T9: loads and the ledger | `CarryStore` keeps each unit's load (saved as `carried`, hashed only while someone carries one) and the node whose slot it holds. `Ledger` keeps per resource what was found, regrew, was lost to others' edits, was taken by gatherers and was dropped (saved as `ledger`, not hashed, since it follows from the state). FINAL checks both balances every step, beside the treasury's: found + regrown = what nodes hold + lost + taken; taken = carried + gathered + dropped. A save from before T9 gets a ledger whose found is what its nodes hold |
| 40 | T9: the Gather command | Wire id 9, `strategos:gather`: units, a block (match-local x and z, absolute y) and a queue mode. The server resolves the node holding the block, else the nearest node of the block material's subtype within 3 blocks, or for a leaf the tree whose log is nearest within six leaf steps; with none, the command is refused as `UNKNOWN_TARGET`, and a unit that does not gather that subtype as `UNSUPPORTED`. A right click on a block with a `resource_yield` entry sends it, marked in green; the data map is now synced to clients, not required of them |
| 41 | T9: bushes | A forage entry names its regrowth a second (`regrow`; a sweet berry bush 0.1). It travels in the slice's legend, written for forage only, so other slices encode as before. A bush regrows its rate a second, kept in a remainder, up to what it was found with, while it holds any; an emptied bush stays empty. Its `age` shows `ceil(3 × amount ÷ found)`. The core writes it as a property-only effect `[age=N]` (COSMETIC) once at discovery and then on a change; the writer sets those properties on the block standing there, and writes nothing if that block lacks them. `CropGrowEvent.Pre` is denied anywhere in a match space (`MatchGrowth`). Regrowth runs in ECONOMY (`EconomySystem`) |
| 42 | T9: tools and loads | `GearSystem` (WORLD) posts `UnitGear(handle, tool, resource, subtype)` when a unit's gear changes. A unit on a gather order at a tree holds an axe while it walks there, works and carries the wood home; at a bush it holds nothing. Its load shows whenever it carries one. The mod puts iron tools (axe, pickaxe, hoe) or the mace in the body's main hand and the load in its off hand: a log, sweet berries, wheat, cobblestone or raw iron. The gateway sends vanilla's equipment packet to the body's trackers at once, since the body's server tick, which would notice the change, never runs; the body registry gives the gear back to a body made again. ECO-05.9's golden at 60 blocks runs in the consent batch (T30) |
| 43 | T9: the proving ground v2 | Near each start a grove of twelve oaks: X 56–77, Z 222–236 for the west, and the same turned half round the centre (X 306–327, Z 147–161) for the east, so the two starts are alike; both lie off the crossings' lanes. Behind each start, past the 14 blocks the soaks spawn within, eight berry bushes (X 3 and 6, and 377 and 380). `ProvingGround.voxels()` now carries the materials of logs, leaves and bushes. Manifest version 2, terrain hash `e8cf0e6e…`. No golden journal runs on the proving ground, so none is re-recorded; `maptests.*`, the crowd soak and its L1 replay pass on v2 unchanged |
| 44 | T9: tests | L1: `GatherTimingTest` (4: a trip's load in exactly the sheet's 334 ticks, both walks at the walk speed and a delivery of the whole load, over seeds 1, 7 and 42; a spearman gathers once data says so, 500 ticks at 0.4 a second, and is refused before; a match saved mid-trip resumes to the same hashes; a worker shows its axe and its loads), `GatherRetargetTest` (3), `DropsiteTest` (2), `GatherSlotsTest`, `ForageTest` (3), `WoodcuttingTest` (the sheet's load in the sheet's time with the mod's own templates), 28 new rows of the unit tree (`GatherTransitionRows`). L3: `ecotests.bushes_change_only_through_the_core`, `ecotests.gather_deliver_and_save` (with the axe, the log and the berries in hand), `ecotests.no_decayable_leaf_lingers` (vanilla-grown oak, birch, spruce and acacia, random ticks off, under an open sky), `ecotests.fell_then_walk_the_clearing_with_no_page_job` |
| 45 | T10: world fx | `FxKind` gains `BLOCK_CRACK` (its datum the stage 0 to 9, -1 to clear), `BLOCK_BREAK` and `BLOCK_PLACE` (the state's id, `Block.getId`); only these carry a datum on the wire, so M2's unit events encode as before. The core posts `BlockCracked` from `Felling.take` when the next log's crack changes: the stage is the tenths of its share gone, none while it is whole, so a fresh log shows nothing until its first thousandth goes; a felled log's crack goes with its block. The writer records what each write shows (a node's removal or a collapse breaks, construction places; leaves, bushes and imports show nothing) and, after its flush, `FxSender` sends the step's cracks and the flush's blocks to `SeesCell` by 16 × 16 page, at most 64 to a payload; a 64-placement tick is one payload of at most 1,000 bytes. The client: `destroyBlockProgress` with a negative breaker id per block, the break's particles and sound, the place sound, six of a kind a tick (`SoundBudget`), louder from the RTS camera as M2's sounds are. The `log_crack` golden runs in T30 |
| 46 | T10: tests | L1: `WireCodecSizeTest.aSixtyFourPlacementTickFitsTheFxCap`, `TreeFellingTest.aLogsCrackRisesWithWhatIsTakenOfIt`, `anFxBatchRoundTripsWithinItsCap` with a datum. L3: `nettests.world_fx_reach_who_sees_the_cell`: a seated player gets a civic centre's placements, a log's rising cracks and its break as an oak log, at its block; a player beside it with no seat gets no `fx` |
| 47 | T11: placement | `PlacementRules` lives in `core.content`, so the client's prediction and the server's verdict run one class over a `World` each: the server's `Placing.world` reads its surfaces (water no land), every building's footprint (foundations too) and every standing node block; the client's `ClientPlacement.world` reads its `building_state` and its loaded blocks (the top floor with collision, leaves skipped; a log's or a forage plant's column by its synced `resource_yield` entry). Rules in the order told: inside, free, on land, explored (every cell until fog), a floor spread of at most 2, a civic centre at least `Placement/spacing` (64) from every other centre to centre, any other building within `Placement/civicReach` (48, the base building's; 0, none, for the centre) of an own civic centre's footprint, and the cost paid. The column rules are per cell, the others colour every cell; a column under a building is told as taken, not without land. `PlaceBuilding` (wire id 10): a seated player's template, corner, rotation, builders and mode; a refusal names the rule as its reason (`BLOCKED`, `NOT_ON_LAND`, `NOT_EXPLORED`, `NOT_FLAT`, `TOO_CLOSE`, `OUT_OF_REACH`, `OUT_OF_BOUNDS`, `NOT_ENOUGH_RESOURCES` with the missing amounts); else the cost is paid, the foundation laid with its builder slots (the ring of cells round its footprint) and the builders get `OrderKind.BUILD` (code 8, saved `build`) on it. A foundation is no obstruction. The client: B arms placement and cycles the building templates, R turns the ghost, a left click places with the selection (Shift queues and stays armed), a right click or Escape disarms; the ghost is the blueprint's blocks as translucent boxes, green or red per column, its verdict recomputed on a move or once a second. The command card arms it from T17; `placement_valid` and `placement_invalid` run in T30 |
| 48 | T12: construction | BUILD (code 15) has BUILD_APPROACHING (16, WALKING) and BUILDING (17, `UnitState.BUILDING`, code 4); a builder claims the free slot nearest it whose cell is standable within 2 blocks of the site's floor, walks there, and on arriving within 1.5 blocks builds; the first to arrive commits the foundation (`Construction.commit`): its columns block units, units standing on them move to the nearest free ground outside, and the writer clears the blueprint's volume and fills each column down to the ground with dirt. Each tick every builder adds its `Builder/rate` (1 for the worker) and ECONOMY adds `round(10^6 × mean rate × n^0.7)` millionths of a tick to the site's work; the house's 30 s are 600 ticks, so n builders throughout finish in ceil(600 ÷ n^0.7) ticks, the commit's tick counting, since they work in it. Progress (thousandths) and the blocks asked for, `floor(blocks × work ÷ needed)` in build order as `CONSTRUCTION` writes, follow the work; hit points rise with progress from a thousandth of the most; done, the site stands finished and its builders' orders end. Work, blocks asked for and builder claims hash only while a building is not finished, and save with it. The builder holds the mace |
| 49 | T11–T12: tests | L1: `PlacementRulesTest` (19 rows with the numeric limits: a spread of exactly 2 passes and 2.0625 fails, centres 64 apart pass and 63 fail, a house 48 from its centre's footprint passes and 49 fails; per-cell and whole verdicts; a turned footprint), `PlacementParityTest` (the client's verdict equals the server's over every third corner, every rotation and three templates, with trees, a bush, a pond and steps), `ConstructionTest` (5: n = 1, 2, 5, 10; every block once in build order; hit points rising; a save mid-build resuming to the same hashes; an unpayable placement naming the wood it lacks), 28 unit-tree rows (`BuildTransitionRows`). L3, `constructiontests` (the name `buildtests` is the build wiring's): `a_placement_the_server_refuses_has_a_reason`, `a_finished_house_equals_its_blueprint` (with BLD-03.3: no unit on its columns once committed), `save_mid_build_resumes_at_the_same_block`, `writer_budget_never_changes_the_hash`, `three_sites_within_the_writer_budget` |
| 50 | T13–T15: population, training, rally | `Population` keeps each faction's `used` (its units' `Cost/population`, and the reservation of a head entry in training) and `housing` (the `Housing/capacity` of its finished buildings) as counters: a unit counts from `MatchState#spawnUnit` (now SpawnUnits' path too) to `removeUnit`, a building from its completion (instant or built) to its fall; the cap is `min(housing + base, populationMax)`, the base 0 until civilisations, the maximum a new field of the economy rules (D-01's 150). Neither hashed nor saved; a restore counts them once (`Production.recount`). A finished building's queue holds 5 entries at most: `Train` (wire id 11) pays and joins one if its `Trainer/units` names the template, else `UNSUPPORTED`, a full queue `QUEUE_FULL`, a lack `NOT_ENOUGH_RESOURCES` with the missing amounts; `CancelTrain` (12) refunds an entry, and a started head's reservation. Each tick in ECONOMY the head takes its population when it starts or waits, keeping its progress, with one `HOUSING_NEEDED` notice; trained for its `Cost/time`, the unit appears on the free ground outside the footprint nearest the blueprint's door. `SetRally` (13) points buildings at a node (a gather order for a unit that gathers it, else a move to it), at a building (a walk to its nearest edge cell) or at the ground (a move). A fallen building refunds its queue. Queues and rallies hash once set and save with the building (its save grouped into site, production and rally, under the codec's 16 fields). `faction_state` now carries the population, the cap, the idle workers and the gatherers per resource; `building_state` the queue and the rally to the owner |
| 51 | T13–T15: tests | L1: `PopulationTest` (3: the counters equal a scan over 20 seeds of training, cancels, spawns, deaths and houses; training waits at the cap with one notice and resumes when a house stands; the cap stays at the match's 30 with 50 housing), `ProductionTest` (4: 20 seeded streams lose and duplicate no food; a sixth entry is refused, and an unpayable one names a food it lacks; a head waiting at the cap keeps its progress; a trained unit stands by the door outside the footprint, on the ground), `RallyTest` (workers rallied to an oak gather and bring wood home; a rally on the ground walks there). L3, `prodtests`: `a_queue_waits_with_housing_needed`, `a_unit_spawns_at_the_door_on_a_surface`, `a_rally_point_survives_save`. The rally marker's golden needs a selected building (UI-06) and runs in T30 |
| 52 | T16: fields | The field template (`strategos:building/field`, 5 × 5, `Footprint/obstructs` 0 so units walk over it, 60 wood, 20 s, 200 HP) is a 5 × 2 × 5 blueprint of farmland under wheat. Finished, it becomes a `CROP` node of its own (`Fields.create`): its wheat blocks top-down, five work slots (four round the middle and the middle), its `Field/amount` (300 food) held back while it ripens over `Field/ripenTime` (30 s, in `NodeStore`'s ripening ticks); ripe, the ledger counts it found. n farmers each reap their `ResourceGatherer/rates/food/grain` (0.5 for the worker) times `(1 − 0.9ⁿ) ÷ (0.1 n)`; the crop's age is written by the core as a property-only effect, `COSMETIC`. Reaped empty, it stays at age 0, exhausted, until `Reseed` (wire id 14) pays its `Field/reseed/<resource>` (40 wood): a field that is not empty is `INVALID_ARGUMENT`, a lack `NOT_ENOUGH_RESOURCES` with the missing amounts. A fallen field's crop is lost. The adapter denies `CropGrowEvent.Pre` inside match spaces (`MatchGrowth`), so vanilla never ages a crop there; `resource_yield` maps wheat to food/grain. The field's node id saves with the building and hashes |
| 53 | T16: tests | L1: `FieldTest` (2: n = 1…5 farmers reap the diminishing total within a thousandth each; a new field ripens in 599 ticks after the step that placed it, its age rising 1 to 7, reaped empty stays at 0 for 2,000 ticks, a reseed without wood names the 40 it lacks, a paid one ripens again and the ledger finds both sowings). L3, `ecotests`: `crops_change_only_through_the_core` (with random ticks and a first-person player beside it, a ripening field's age in the level follows the core's, a tick behind at most, while a control wheat outside the match grows by itself), `a_field_yields_and_is_reseeded` (two farmers reap a field empty and bring its 300 food home, its crop at age 0; paid for, it is sown again and stands ripe at age 7) |
| 54 | T17: selection panel and command card | View-models in `core.client.hud`: `SelectionPanel` (several units: an icon per type with its count; one unit: its template, hit points, state and carried load from `unit_detail`, which now carries the load's resource and amount; one building: its `building_state` entry, whose own fields now add the head's progress in thousandths and, for a field, the food left and the ticks it still ripens), `CommandCard` (a 5 × 3 grid of square cells, at least 16 GUI pixels: an own finished building's `Trainer/units` and an empty field's reseed; units' build page, when a builder is among them, and stop; the build page's buildings with a blueprint, and back; each button with its cost per resource and whether the stock covers it) and `CardController` (one click sends at most one command: `Train`, `Reseed`, `Stop`, or `CancelTrain` from the panel's queue; or turns the page, or arms placement; a new selection closes the build page). Templates name an item icon in `Identity/icon`. `Selection` takes a building alone (own) or inspects one (another faction's); a click on a building's block selects it by its blueprint's columns, and a right click with it selected sends `SetRally` to the clicked block. Escape while placing disarms however placement was armed (it paused the game when the build key armed it). The panel and the card draw in the HUD's rectangles; a press on them is the HUD's, not the world's |
| 55 | T17: tests | L1: `CommandCardLayoutTest` (12 windows from 854 × 480 to 3840 × 2160 at every GUI scale Minecraft allows there, 56 layouts: the card's 15 cells inside, apart, at least 16 pixels; the panel's icon, three lines and five queue cells inside and apart, ten type icons at least), `CommandCardTest` (6: one click on the centre's worker button sends exactly one `Train`, an empty cell or a point outside none; the panel's building is the client model's entry after the wire, and a click on its second queue cell sends one `CancelTrain`; the build page lists every building with a blueprint at its cost and arms placement; an empty field offers its reseed, a ripe or ripening one nothing; another faction's building offers nothing; every template names an icon). L3: `replicationtests.the_queue_reaches_its_owner_as_the_server_has_it` (each `building_state` a seated mock player receives holds the queue and head progress the core held at its tick). The client scenario `rts_economy` (run on macOS, and by hand on CI's Linux route) clicks the centre, trains a worker the server queues, sets a rally point, shows a worker and arms the house from the build page; its frames `select_civic_centre` and `select_worker` are T30's golden candidates |
| 56 | T18: resource bar | `core.client.hud.Income` keeps the faction states' `gathered` totals back to the newest at least a minute (1,200 ticks) older and gives each resource's income per minute, with less than a minute seen the rate so far scaled to one; another match or an older tick starts afresh. `ResourceBar` shows per resource the stock and income in whole units and the gatherers, the population and its cap (capped when used reaches it) and the idle workers, from the last `faction_state`. The mod draws it in the top row (now the safe width less 100, or 45 % of it where more, at most 300), each resource an item icon at half size with its stock and, where all fit, its income; the population red at its cap; a hover tells a resource's income and gatherers, or why training waits. Neither payload changed |
| 57 | T18: tests | L1: `IncomeTest.perMinuteMatchesAGatheredMinute` (three seeds, three workers on a bush and two on an oak, the states fed four a second: the income per minute equals the food and the wood gathered over the last 1,200 ticks; the bar counts 3 and 2 gatherers), `ResourceBarModelTest` (2: 500 seeded states, a tenth of them stale, through the wire: the bar equals the model after each; the rate scales what it saw to a minute, and a new match starts afresh). L3: `replicationtests.faction_state_equals_the_server` (each state a seated mock player receives is the server's at its tick, one worker on wood, one on food, one idle). The `rts_economy` scenario checks the bar before and after a worker's cost and frames `resource_bar` for T30 |
| 58 | T19: idle workers and notices | `IdleWorkers` lists the faction's own units whose template gathers and whose summary state is idle, in index order; its key, `NEXT_IDLE_WORKER` on I (0 A.D.'s and AoE's full stop is the camera's turn here), takes the first after the one it took last, round to the first, selects it and sends the camera there (a far one by `camera_jump`, selected when its body arrives); the idle count in the resource bar is the same button. `EconomyNotices` turns a `NOT_ENOUGH_RESOURCES` refusal into "not enough" naming what was lacked, `HOUSING_NEEDED` into "population capped" and `IDLE_WORKER` into "a worker is idle" (the card's "deposit exhausted": the worker found nothing more within reach); a message of a live cause renews it without firing, a notice lapses 5 s after its last message, and the cap's lasts while the faction's population is at its cap. The newest three show right-aligned in the notices' strip. The language file gains the field's name and the build, rotate, follow and idle-worker keys it lacked |
| 59 | T19: tests | L1: `IdleWorkersTest.theKeyCyclesInStableOrder` (three idle workers visited 2, 9, 12 and again; a gatherer, another faction's worker and a spearman skipped; a newcomer taken in its turn, a busy one dropped), `EconomyNoticesTest.oncePerCause` (a refused train, a housing notice and an idle worker every tick for 15 s fire three notices; another lack is another cause; lapsed, the same fires again; the cap's cause ends below the cap). The `rts_economy` scenario presses I four times: three workers, then the first again; its frame `idle_worker` is T30's candidate |
| 60 | T20: the placeholder look | [docs/art/style-guide.md](../art/style-guide.md): 16 × 16 texels, the vanilla palette and render types, original assets; the seven faction colours (the outline's) on dyed leather and shield banners; roles by what a body wears and holds; per building class the footprint, height, block count and a rise of 1.5–6 blocks a second with one builder. `core.client.look.UnitLook` gives a body its look from its template's classes and faction: every `Organic` unit a tunic dyed its faction's colour, a spearman an iron helmet, dyed leggings, a trident and a shield with its faction's banner; the debug walker, a test body, nothing, so M1's and M2's goldens hold. `UnitRenderer` gains vanilla's armour and held-item layers (it had none: the hands the server's gear filled were never drawn) and dresses each body on its first frame, client-side only, so no byte goes on the wire; the server's gear still owns the hands, and an empty hand shows the look's rest item. The three placeholder blueprints already keep the ranges |
| 61 | T20: tests | L1: `UnitLookTest` (3: role and owner read from gear and dye; the debug walker wears nothing; seven factions, seven colours and banners), `StyleGuideRangesTest` (each blueprint keeps its class's ranges; a class the guide does not name fails). The blueprint lint and T12's GameTests stay green (ART-01.1). The client scenario `art_look` dresses 12 bodies, 8 spearmen armed, and frames them from 24 and 64 blocks up: the candidates for ART-01.3's goldens in T30, where the 60-block frames with and without Sodium and the 200 armoured humanoids' frame cost (ART-01.5) run |
| 62 | T21: torches | An `Attack` on a building (its handle from `BuildingStore.BASE` up) gives each owned unit with `Attack/Structure/fire` above 0 a `BURN` order (kind 9, saved as `burn`), others `UNSUPPORTED`, the issuer's own building `INVALID_ARGUMENT`. BURN (18) has BURN_APPROACHING (19, WALKING) and BURNING (20, `UnitState.COMBAT`): a burner takes the free cell of the ring round the footprint (`Placing.slots`) nearest it, a place being taken while another burner's current order holds its number one up in the order's slot, so nothing new is stored or hashed; arrived within 1.5 it winds up for `Attack/Structure/prepare` ticks, then deals `Attack/Structure/fire` every `repeat` ticks until the building falls or the order ends. Fire is a fourth damage type (`Damage.fire`); buildings now take every blow through `DamageFormula` against their template's resistances, fire's from `Resistance/fire` (stone 7 in `building_base`, the wooden house, barracks and field 0), and each hit is a `BuildingHurt` (UI-12's alert). A blow shows `FlameShown` at the footprint block nearest the burner, two up: `fx` kind `FLAME` (wire code 5) to those who see the cell, flame and smoke particles forced past 32 blocks and a crackle on the client, never a fire block. The burner holds a torch (`UnitGear.TORCH`) on its way and at work, and only then. The spearman's torch: 3 fire, 10 ticks' wind-up, a blow a second; the balance sheet's new "Burning down" table counts the blows (the house 134, the civic centre 1,394) and the time for ten bearers from one tick (the house 13.5 s, the centre 139.5 s). A right click on an enemy's building sends the attack |
| 63 | T21: tests | L1: `StructureAttackTest` (3: ten spearmen burn the house on its 134th blow, the tick the bearers' rhythm from each one's first blow gives, the sheet's 270 ticks for ten from one tick; a blow on wood takes 2.09 times one on stone and more than twice per hit point; the torch shows only against the building, goes when it falls, a worker has none and an own building is refused), 32 unit-tree rows (`BurnTransitionRows`, the harness's worker given a torch so one unit reaches every subtree). L3, `combattests`: `a_burn_makes_no_fire_block` (fire tick on: no fire block in the match space through the burn, while a control fire on planks outside spreads), `a_burnt_building_is_removed_and_cleared` (the house leaves the match and its blocks above the floor are gone) |
| 64 | T22: rules, scenarios, victory | `MatchRules` gain `play` (`PlayRules`: the victory rule, `none` or `civic_centre`, and `revealMap` and `aiVision`, stored for fog in M6); the population maximum stays the economy's. Rules change only through `RuleChange` (wire id 16, admin only, keys `populationMax` 1–1,000, `victory`, `revealMap`, `aiVision`), a command and so journaled; `MatchState#setRules` swaps the config's rules and the population's maximum. A faction resigns with `Resign` (wire 15; its own, or any for an admin) rather than through a `match_control` payload, so the resignation is journaled with the match; T25's `match_control` carries pause and speed only. The victory check runs in FINAL under `civic_centre`: a faction holding a finished civic centre is in play, one in play holding none any more is defeated (`FactionDefeated`), and once someone lost and at most one stands the match ends (`MatchEnded`, winner or -1, its ticks) the same tick; the outcome (held, defeated, end, winner) hashes and saves once set, and the rules hash once they differ from the defaults, so older journals keep their hashes. A scenario (`data/<ns>/strategos/scenarios/*.json`: map, victory, per slot player, starting resources and entities placed from the map's start slot) becomes admin commands (`ScenarioSetup`): the victory rule, grants, buildings centred on their offsets, units; `first_loop` gives each side a civic centre, five workers and 300 food. `/strategos match scenario <id>` sets one up on a match made on its map, `match config <key> <value>` and `match resign <faction>` send the commands. `match_event` (`MatchNews`: a defeat, or the end with winner and duration) goes to every participant; the runtime goes ENDED and steps no more. The client keeps the end and draws the end screen: Victory or Defeat, the winner with its colour, the duration. `MobSpawnEvent.PositionCheck` fails for a monster anywhere in a match's space unless a command or a spawn egg made it; world game rules are never touched |
| 65 | T22: tests | L1: `VictoryTest` (3: the match ends the tick the last enemy centre falls, faction 0 the winner, once, and restores ended with the same hash; a resignation defeats its faction, only its own; with no victory rule nothing ends and the outcome stays empty), `RuleChangeTest.journaledAndReplayedToTheSameHash` (three changes applied, the replay's hash the live one and not the unchanged run's; a player's change, a value out of range, an unknown key and an unknown rule refused), `ScenarioTest` (2: a scenario's exact commands from two start slots; too few slots and an unknown template refused). L3, `matchtests`: `a_scenario_loads_exactly_its_entities` (first_loop on the 48 arena: two centres, five workers each, 300 food, the rule), `no_hostile_spawns_inside_a_match` (a zombie's natural and spawner checks fail inside, a spawn egg's and one outside are left to vanilla), `match_event_reaches_every_participant` (two seated players hear the defeat and the end with winner and duration; the ended match steps no more). The `rts_economy` scenario ends with faction 1's resignation and frames `end_screen`, MATCH-01.6's candidate for T30 |
| 66 | T23–T24: minimap and alerts | `MinimapImage` (core) shades each column's map colour as vanilla's maps do (brighter rising, 255; level, 220; darker falling, 180, against the column to the north); `Minimap` maps the space north up into the HUD's square, the longer side filling it, centred, `toMap` and `toWorld` inverse, with unit dots from `unit_summary`, building footprints and the camera's frame (the window's corners on the ground plane). The mod's `RtsMinimap` keeps one `DynamicTexture` the size of the joined space, rebuilds 12 chunks a client tick in turn from the loaded blocks' `getMapColor`, and uploads at most once a second when a chunk's pixels changed (a whole 384² map is looked at every 2.4 s); a left click on the image centres the camera there (`camera_jump` when far). Alerts: `AlertSystem` (core, WORLD) turns the tick's blows by one faction on another's unit or building (`UnitHurt`, `BuildingHurt`) into `UnderAttack` events, one for a sustained attack: a blow within 32 blocks of an alert of the same faction whose last blow is under 30 s old sustains it; neither hashed nor saved, as the gear is not. `match_event` gains `ALERT` with the place, to the attacked faction only. The client rings a bell once, shows "Under attack! (J)" for 5 s and a blinking ring on the minimap, and J (`JUMP_TO_ALERT`) centres the camera on the latest alert |
| 67 | T23–T24: tests | L1: `MinimapImageTest` (2: the proving ground's image, its palette's vanilla map colours, hashes as stored, `973a70bdd225deb3`, a tile its own square; height shades as vanilla's), `MinimapMappingTest` (56 layouts and three spaces: a column's pixel maps back to it exactly, the image fills the square's side and stays inside), `AttackAlertTest.aSustainedAttackRaisesOneAlert` (a raider's 134 blows over two minutes raise one alert; a house 80 blocks off a second; the raider's faction none). L3, `cameratests`: `a_minimap_click_jumps` (a column's pixel, mapped back, jumped to and answered by the next tick), `the_alert_key_jumps` (a commander's house burnt: one `ALERT` reaches it, and a jump there is answered by the next tick, within the commander's limits' nudge). The `rts_economy` scenario checks a minimap pixel under the centre |
| 68 | T25: pause and speed | `match_control` (`MatchControl`, client to server: pause, resume, or a speed of 0.5, 1 or 1.5) is taken from a seated player in singleplayer only: a pause is the registry's `PAUSED`, in which the runtime takes commands into its inbox and does not step, a resume steps it again, and a speed sets `ServerTickRateManager#setTickRate` to 20 × the speed, never a tick freeze; the match's end or disposal puts the rate back to 20. The client's F9 pauses or resumes (P is vanilla's social key, F8 the movement overlay's), `[` and `]` step the speed down and up; a paused match shows "Paused (F9)" at the top, another speed than normal shows beside the notices. MATCH-05.4's C p95 ≤ 15.3 ms is measured in T29's soak |
| 69 | T25: tests | L1: `PauseTest` (2: a run paused at tick 80 with an order given meanwhile reaches tick 200 at the hash of one that gave it at tick 80 and never paused; the order applies in the first step after the resume). L3: `matchtests.pausing_one_match_leaves_another_stepping` (two matches in one arena: the paused one stands still for 20 ticks while the other steps, takes an order, and walks it once resumed) |
| 70 | T26: the AI framework | `core.sim.ai`: a `ComputerPlayer` per AI slot (`AiSlots`, saved in the snapshot's `play` with its memory of 16 longs and the next wake) thinks in `AiSystem`, phase AI after the commands, every 20 ticks, offset `7 × ((faction + seed) mod factions)`, and early on an attack or an alert on its faction (`AiSlots.wake`). It reads `AiView`, a read-only facade over `MatchQueries` (its own units, buildings and orders in full; others' as the queries show them, fog in M6), and writes only through `CommandSink`, whose commands join the next tick's pending commands under its `PlayerSlot`, so the same validators and the journal see them as a human's. `AiBudget` counts evaluation units, 5,000 a think, and a module that runs out stops; the think's spend is kept for the tests. ArchUnit's `aiNeverWritesState` keeps `core.sim.ai` off `MatchState`, the stores and every system (a fixture proves the rule fires). `SetAi` (wire 18, admin only) seats a computer in a slot with a strategy script of at most 3,000 bytes; `ScenarioSetup` emits it for a slot whose player is `ai:<script>` |
| 71 | T27: the scripted bot | A `StrategyScript` (`data/<ns>/strategos/ai/*.json`, `first_loop`): the worker and its cap (20), the food:wood ratio (3:2, its weights doubled below 100 of a resource and halved above 400), a build order of `template@population` steps (barracks at 8, fields at 9, 11, 13, 15 and 19, houses at 12 and 17), a housing margin (3), the soldier and its queue (2), the attack (first at 450 s, every 180 s, at least one soldier, muster 24 blocks out, announced 30 s ahead) and 16 site offsets mirrored per start. Its managers in order: `EconomyManager` (idle workers to the resource most behind, up to two moved a think to rebalance), `ProductionManager` (workers, then soldiers; a field reseeded when empty with 40 wood), `BuildManager` (a house first when free room ≤ the margin, then the next step, on the first free site by `PlacementRules`, two nearest workers, retried after 100 ticks), `MilitaryManager` (soldiers muster; at each wave's time `AnnounceWave` (wire 17), and after the announcement's 30 s an attack on the enemy centre; `WaveAnnounced` becomes `match_event` WAVE to the match, the opponents' HUD sounds the raid horn and counts down "Attack coming: N s"). A built house now houses and a built field ripens (`Construction` calls `Production.finished` on completion; it did not before, a regression test pins it) |
| 72 | T28: the headless runner | `HeadlessMatch` plays `first_loop` on `SimHarness` bot against bot to a winner, a 30-minute limit or a stall (no AI command for 2,400 ticks), with telemetry per match: winner, duration, first contact, army per minute, idle-worker seconds, banked resources per minute, units lost, stuck counts, commands, waves (announced, leaving, left), the build order's end, the largest think, the journal's hash, a failure. `:core:headlessMatches -Pseeds=1..10` writes `core/build/reports/headless/summary.json` and a failing seed's journal; ten seeds take about 10 s. `aitests` replays seeds 1 and 2 in the server (batches `ai_seed_1`, `ai_seed_2`, in `verifySlow`) to their L1 hashes. The smoke's AI phase runs 600 ticks of `first_loop` bot against bot on the proving ground on a dedicated server. `/strategos skirmish <map> observe seed=<n>` makes the match, sets up the scenario with every slot a computer once imported, and seats the caller as an observer: the admin's slot, which seats any number of watchers who get every faction's buildings, health and details and whose command batches the gateway drops as a violation without a kick |
| 73 | T26–T28: tests | L0: `CoreArchitectureTest.aiNeverWritesState`. L1: `AiDecisionTest` (4: 300 wood and 9 of 10 → a house; food and room → a worker, idle workers gather; a think within its cap; its commands pass the validators a human's pass and a refused one is refused alike), `ScriptedBotTest` (3: the build order completes and the first wave is announced in [9,001, 9,020] and leaves 600 ticks later; every wave announced 30 s ahead; every think under 5,000), `HeadlessMatchesTest.anExceptionAStallOrNoWinnerFails`, `ConstructionTest.aBuiltHouseHousesAndABuiltFieldRipens`, `MatchRosterTest.theAdministratorsSlotSeatsObservers`. L3: `aitests.seed_one_replays_to_its_hash`, `seed_two_replays_to_its_hash`, `the_wave_announcement_reaches_the_opponents`; `maptests.skirmish_observe_seats_the_observer` (seed 3, the observer seated in the admin's slot, the computers commanding, an observer's command dropped). L5: the smoke's AI phase. Ten seeds: every match ends with a winner between 15:19 and 15:34, first contact near 9:50, three waves each; faction 0 wins all ten (§1.8) |

| 74 | T29: the first-loop soak | `FirstLoopScene` (core testkit) is the soak's first loop as commands, so the server and an L1 run play it alike: per faction a civic centre, a barracks, five houses and three fields spawned finished round its start (the AI's first nine sites, two moved a block so no footprints touch: 20 buildings), a million food and wood, 60 workers, idle ones sent each second to the nearest free node of wood or food by handle parity (own fields only), and 40 spearmen in waves across the fords as the melee soak's; three constructions at all times (two for faction 0, one for faction 1; six in the stress pass), each a barracks with one builder on the next site of a ring 8 blocks apart and 22–52 out, tried each tick, a refusal costing one. It runs through `CrowdSoak` (`Config.firstLoop`), which adds per sampled tick the writer's `setBlock` time, the match's fx time (`MatchRuntime#fxNanos`, timed in `FxSender`), the commands phase of each tick that applied a placement, and one save of the blob halfway (budget G); `TickPacer` paces at a game speed's rate. The verdicts: A–E as the crowd soak's (the crowd's own checks reported: fighting units stand), G on nav and the save, A's p95 per unit at most 15 % over M1's (M1's close: A p99 3.37 ms for 600 units, an upper bound on its unrecorded p95), B per unit reported (below 600 units the tick's fixed work makes most of it); the stress pass gates only C at 50 ms, the 1.5× pass only C at 23 / 1.5 = 15.3 ms (MATCH-05.4) |
| 75 | T29: tests and numbers | L1: `FirstLoopSceneTest` (15 game minutes on the core in 3 s: 20 buildings from the first tick, every construction running in at least 95 % of the seconds after the first half minute, at least 20 placed, four in five workers gathering, at least 25 waves and 100 deaths). L6, this Mac, paced, foreign load 2–6 %: `perftests.first_loop_soak` (17,800 samples): A p50 0.27 ms and p99 0.84 ms against 5 and 10; B p95 1.16 ms against 17; C p95 4.24 ms against 23; D p99 0.04 ms against 2; E 3.9 KB/s per viewer; G nav 2.9 MB and the save 5.4 ms against 100; per unit A 3.0 µs (M1 5.6, 0.53×) and B 5.8 µs (M1 7.5); 31 constructions finished, 60 sites refused, 30 waves, 51 buildings at the end; placement's commands phase p50 0.98 ms and p99 1.84 ms, the writer p99 0.07 ms a tick, fx p99 0.04 ms. `first_loop_stress` (400 units, six constructions): C p95 5.27 ms against 50. `first_loop_fast` (1.5×): C p95 4.23 ms against 15.3. The figures are M3's per-unit baseline for M4 ([perf/first-loop-m4pro.json](../../perf/first-loop-m4pro.json)) |
| 76 | T30: the goldens | One pinned scene, `m3_goldens`: a civic centre, two workers and a bare oak trunk on the flat world, the world changed only by commands the server sends at fixed core ticks, and frozen (`/tick freeze`) at the first core tick a condition holds, which is the same tick every run: tick 40; the trunk's first crack of stage 3; the woodcutter's first load more than 3 blocks from the trunk; the enemy spearman's first blow; the match's end. Frozen, the client selects, points, arms placement and presses keys, which change nothing on the server; bodies are pinned. Nine frames: `select_civic_centre` (with the rally marker, PROD-03.3, UI-06.4), `placement_valid`, `placement_invalid` (BLD-02.4), `idle_worker` (UI-10.3), `log_crack` (AUD-04.3), `worker_tool_and_load`, `select_worker` (ECO-05.9, UI-06.4), `attack_alert` (UI-11.4, UI-12.3), `end_screen` (MATCH-01.6); the resource bar (UI-07.4) and the minimap are in every frame. Identical over two repeats (nmse 0), approved under the owner's word. The rally marker did not exist: `building_state` carried the point, nothing drew it; `RallyMarker` draws a pole, a flag in the owner's colour and a ground square while the building is selected, solid quads, no particles. Scenes now tell the server their render distance (`ScenarioWorld.renderDistance`): the option alone sends no client-information packet, so the server kept sending the harness's 5 chunks, which was M2's open `pose_high` (its edge); `rts_goldens` and `overlay_goldens` are re-recorded for M3's HUD with `pose_high` added, and `art_look` is frozen and pinned, with goldens for macOS and macOS with Sodium (ART-01.3) |
| 77 | T30: the client frame passes | PERF-14 again, with M3's HUD and the server sending the 16 chunks (`rts_frame`, medians of three, [perf/client/m4pro.json](../../perf/client/m4pro.json)): vanilla frame 3.38 / 7.06 ms (p50 / p99), units 2.06, our drawing 0.65, client tick 0.87; Sodium and Iris 1.89 / 4.42, 0.41, 0.85, 0.95. With Sodium the HUD's minimap at 12 chunks a tick put the client tick at 1.02–1.08 ms; it rebuilds 4 now. ART-01.5, the same scene with 200 armoured spearmen (`-Pframe.units=infantry`): with Sodium units 1.62 ms of 6 and the client tick 0.82 ms, so it holds; vanilla does not: units 8.94 ms and the outlined hundred 37 ms of our drawing (frame 47 ms), from the held items' translucent quads sorted at every batch end and the outline pass drawing armour and items again. **The level of detail vanilla needs:** the selection outline from the body alone, without the armour and item layers; and the armour and held items left out beyond about 48 blocks or past a hundred bodies on screen. Sodium needs neither. This goes to M4 as a task (D-01 and budget F read these numbers) |

### 1.7 Consent plan

- **Now (2026-10-23 on): no windows.** Every task is built headless; L4 checks run on CI's Linux
  route where a scenario can show them, and their macOS goldens wait.
- **The consent batch (T30):** the M3 goldens on macOS (worker tool and load, the crack, placement,
  selections, resource bar, idle worker, rally marker, minimap, alert, end screen, the looks at
  60 blocks with and without Sodium), PERF-14 re-run with the M3 HUD, ART-01.5's client frame pass
  (S4's armour and held-item pass), M2's `pose_high`. Pending consent blocks Accepted, not Done.
  Run on 2026-10-24 under the owner's word of that day (rows 76–77).

---

### 1.8 Findings for the owner

- **The first loop favours the west start.** On the proving ground faction 0's wave reaches the
  enemy centre about 22 s before faction 1's reaches its own, and burns it first; faction 1's wave
  dies on the way. Ten seeds out of ten go to faction 0. The bot defends nothing (AI-05, M6), so the
  shorter way decides; the map's starts or a defending bot settle it, not M3's numbers.

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
- **Files:** `resource_yield` data map, `ResourceYield`, `MaterialTable`; `SliceMaterials` in slices and page jobs;
  `NodeDiscovery`, `NodeStore`, `NodeIndex`, `NodeView`; `EcoTests`; `NodeDiscoveryBench` (§1.6 rows 29–30).
  **Verification:** `verify`; `ecotests`; the bench.

#### T7 · Building records and blueprints — BLD-01
- **Files:** `Blueprint`, `PlaceholderBlueprints`, `Footprint` (content); `core.sim.build`; `SpawnBuilding`;
  `BlueprintLoader`, the palette lint; `building_state`; `/strategos build place`; `BuildingTests`
  (§1.6 rows 31–35). **Verification:** `verify`; `buildingtests`.

#### T8 · Woodcutting and the canopy — ECO-03
- **Files:** `Felling`, `NodeStore#lose`, `MatchState#materialsIn`; ADR-0024; `testkit.TreeFixtures`
  (§1.6 rows 36–37). **Verification:** `verify`. ECO-03's L3 items come with the worker in T9.

#### T9 · Gathering, dropsites and berry bushes — ECO-05
- **Files:** `unitai.Gathering` and the GATHER states, `Gather` and `GatherRule`, `CarryStore`, `Ledger`,
  `GatherRates`, `Dropsites`, `Forage`, `EconomySystem`, `GearSystem` and `UnitGear`; the right click; `MatchGrowth`,
  `GearItems` and the bodies' hands; the proving ground v2 (§1.6 rows 38–44). **Verification:** `verify verifySlow
  verifyPerf` (the JMH gate's MatchBusBench, untouched, drifted; STATE item 11).

#### T10 · World-edit feedback — AUD-04
- **Files:** the new `fx` kinds, `BlockCracked`, the writer's `Written`, `FxSender`'s `SeesCell` route,
  `FxPlayer`'s cracks, particles and sounds (§1.6 rows 45–46). **Verification:** `verify`; the crack's golden in
  T30's batch.

#### T11 · Placement mode and validators — BLD-02 · T12 · Progressive construction — BLD-03
- **Files:** `PlacementRules`, `Placing`, `ClientPlacement`, `PlaceBuilding`, the ghost (`RtsPlacement`);
  `Construction`, the BUILD states (`unitai.Constructing`), the commit and its eviction; `constructiontests` (§1.6
  rows 47–49). **Verification:** `verify verifySlow`.

#### T13 · Population — ECO-06 · T14 · Production queue — PROD-01 · T15 · Rally points — PROD-03
- **Files:** `core.sim.prod` (`Population`, `Production`); `Train`, `CancelTrain`, `SetRally` (`TrainRules`); the queue and
  rally in `BuildingStore`; `prodtests` (§1.6 rows 50–51).
  **Verification:** `verify`.

#### T16 · Fields with manual reseed — ECO-09
- **Files:** `Fields` (the spec's `FieldNode`: a field is a node in `NodeStore`), `Reseed`, the
  field template and blueprint, crop ages through the writer, `MatchGrowth`'s grow-event denial.
  **Verification:** `verify`.

#### T17 · Selection panel and command card — UI-06 · T18 · Resource bar — UI-07 · T19 · Idle workers and notices — UI-10
- **Files:** `core.client.hud` view-models; `mod.client.hud` drawing; the scenarios
  `rts_economy` for CI. **Verification:** `verify`; CI's Linux route.

#### T20 · Placeholder look and the style guide — ART-01
- **Files:** `docs/art/style-guide.md`; the three placeholder blueprints; `UnitRenderer`'s layers.
  **Verification:** `verify`; CI's Linux route; the L6 client pass in T30.

#### T21 · Attacking buildings with torches — CMB-03
- **Files:** `Attack/Structure`, `Resistance/fire`, building attack places (`unitai.Burning`), `FLAME` fx,
  the sheet's burn table. **Verification:** `verify verifySlow`.

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

- [x] T1 (2026-10-23) · [x] T2 (2026-10-23) · [x] T3 (2026-10-23) · [x] T4 (2026-10-23) · [x] T5 (2026-10-23) · [x] T6 (2026-10-23) · [x] T7 (2026-10-23) · [x] T8 (2026-10-23) · [x] T9 (2026-10-24) · [x] T10 (2026-10-24) · [x] T11 (2026-10-24)
- [x] T12 (2026-10-24) · [x] T13 (2026-10-24) · [x] T14 (2026-10-24) · [x] T15 (2026-10-24) · [x] T16 (2026-10-24) · [x] T17 (2026-10-24) · [x] T18 (2026-10-24) · [x] T19 (2026-10-24) · [x] T20 (2026-10-24) · [x] T21 (2026-10-24)
- [x] T22 (2026-10-24) · [x] T23 (2026-10-24) · [x] T24 (2026-10-24) · [x] T25 (2026-10-24) · [x] T26 (2026-10-24) · [x] T27 (2026-10-24) · [x] T28 (2026-10-24) · [x] T29 (2026-10-24) · [x] T30 (2026-10-24) · [x] T31 (2026-10-24)

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


### 8.1 Results (T31, 2026-10-24)

| # | Result | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the 27 cards passes at its level but the owner's (ECO-05.10, AUD-04.4, ART-01.4: the demo); all 27 done | §1.4, §1.6 rows 1–77 |
| 2 | Pass | `GatherTimingTest`, `ConstructionTest`, `ProductionTest`, `TreasuryInvariantTest`, `AiDecisionTest` |
| 3 | Pass | `ecotests`, `constructiontests`, `prodtests`, `combattests` |
| 4 | Ten seeds and the L3 sample of two end with a winner at 15:09–15:29, no exception, no stall, the telemetry in every report. The food criterion holds in nine seeds of ten: seed 5's faction 1 waits 65 s once after minute 5 (outside a raid, which a losing economy needs excluded); AI-02 owns it from M4 (M4 §1.6 row 3) | `headlessMatches`, `aitests`, §1.6 row 73 |
| 5 | A p50 0.27 ms, C p95 4.24 ms, per-unit A 0.53× M1's; the 2× pass C p95 5.27 ms; the 1.5× pass 4.23 ms | `perftests.first_loop_soak`, §1.6 rows 74–75 |
| 6 | Pass: 600 ticks of AI versus AI, three commands a side | `runServerSmoke` |
| 7 | Done on 2026-10-24 under the owner's word: nine `m3_goldens` frames, the looks with and without Sodium, `rts_goldens` and `overlay_goldens` again with M2's `pose_high`, PERF-14 with M3's HUD, ART-01.5 (holds with Sodium; vanilla's level of detail named) | §1.6 rows 76–77 |
| 8 | `verify`, `verifySlow`, `verifySoak` green; `verifyPerf`'s JMH gate named `MatchBusBench` alone (+17.7 %, code untouched since M1, M2's tree reads the same), whose baseline was re-taken under the owner's word, the rest within 6 %; the perf GameTests green; CI green but for one timeout of `cameratests.chunk_sent_repair` under load (M4 T1) | the runs, STATE.md |
| 9 | The owner's | STATE.md |

**Deviations.** The rally marker existed only as a text line until T30 (row 76). Scenes never told the
server their render distance, so every client scene since M1 drew 5 chunks of terrain whatever it set,
PERF-14's first baselines included (row 77). The scripted bot lost a barracks when the next placement
took its builders, which no command could send back (row 73; M4's `Build`). The first loop favours the
west start (§1.8).

**Follow-ups** (M4 §1.6 and T1): `RtsHud` (1,099 lines), `MatchState` (956), `NetTests` (914) and
`CrowdSoak` (876) split; vanilla's crowd level of detail; the `Build` command; the CI timeout.

**Status.** **M3 is Done** (2026-10-24): rows 1–8 pass as recorded; row 9 is the owner's and blocks Accepted, not Done.

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
