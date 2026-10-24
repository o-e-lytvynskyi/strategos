# M4 — Economy and construction: implementation spec

*Written 2026-10-24 by the assistant as M3 closed, from the 18 approved M4 cards in
[roadmap/M4-economy-and-construction.md](../roadmap/M4-economy-and-construction.md),
[04-architecture.md](../04-architecture.md) (cited as "04 §n"), the ADRs in [adr/](../adr/README.md),
chiefly [ADR-0018](../adr/0018-persistence-and-versioning.md) (persistence) and
[ADR-0021](../adr/0021-economy-and-construction-model.md), and M3's spec and code
([M3-first-loop.md](M3-first-loop.md), cited as "M3 §n"). Status: **specified**. The owner's word
of 2026-10-24 («как закончишь м3 - начинай м4, все решения принимай сам, которые рекомендуешь»)
starts M4 without waiting; the decisions it needs were taken on 2026-10-21: D-14 (auto-reseed for
wood at the farmstead) and D-15 (repair free at half the build speed). Engine names cited here come
from the cards and the architecture; every other Minecraft or NeoForge name is checked in
`build/mc-src` by the task that first uses it, and recorded in §1.6 when it differs. Clean-room as
before.*

---

## 0. Read this first

### 0.1 Authority

As M3 §0.1: the architecture and the ADRs decide *what*, this spec *how M4 builds it*; the cards
define scope and acceptance; ROADMAP.md holds the statuses; a disagreement is a §1.6 row. M3's code
is the starting point.

### 0.2 Working rules for every task

M3 §0.2 holds, with these changes:
- **Windows.** The owner allowed every kind of test on 2026-10-24, client windows included. L4
  checks still run together in the consent batch (T27), so the screen is taken once; a client
  frame measurement a task needs earlier may run when it does.
- **Goldens** are pinned scenes in the manner of M3's `m3_goldens` (M3 §1.6 row 76): the world
  changed only by commands the server sends at fixed core ticks, frozen at the first tick a
  condition holds, bodies pinned; a frame that differs between two repeats is not a golden.
- **Scenes tell the server their render distance** (`ScenarioWorld.renderDistance`, M3 row 76).

### 0.3 Code conventions

M3 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Snapshot schema | M4's new stores (damage stages, deposits, outcrops, livestock, auto-reseed, activity tiers' wake state) raise the schema to 3 with a `Migrations` step 2 → 3 and released fixtures of 2 kept readable (M1 §1.6 row 125's procedure) |
| Older journals | A store hashes only when it holds something (M3 §0.3); a change to the proving ground's terrain is a new map version (v3), and what pins v2's hash is re-recorded with a reason in `REBASELINE.md` |
| Commands | New ids follow 18: `Build` 19, `CancelBuilding` 20, `Demolish` 21, `SetAutoReseed` 22, `TrainBatch` 23, `SpawnDeposit` 24; each in `CommandKinds`, the protocol pins and the engine-surface inventory where it adds a payload |
| Gaia | The neutral faction is a slot after the players', `FactionSlot.gaia(factions)`; it owns no buildings, holds no treasury and appears in no victory check |

---

## 1. Goal, demo and scope

### 1.1 Goal

Deepen the economy and the town: stone and iron from real rock and deposits, each with things to
buy; livestock at the start; fields that reseed themselves; the full starting building set on
uneven ground; abstract HP with blocks that fall away and return; protection against vanilla
interference; batches and the complete command card; save and resume; an AI that runs the whole
economy (the roadmap's goal).

### 1.2 Slices

| Slice | Content | Demo at its end |
|---|---|---|
| 4.0 carry-over | T1 health check, T2 `Build` (help build), T3 the vanilla crowd LOD | Workers sent to a foundation help build it; 100 selected armoured spearmen draw at playable rates on vanilla |
| 4.1 resources | UNIT-05, ECO-07, ECO-08, ECO-16, ECO-17 | Workers mine an outcrop and an iron mine, slaughter sheep, farms reseed themselves |
| 4.2 the town | BLD-04, BLD-05, BLD-06, BLD-07, BLD-08, BLD-09, PROD-02 | A barracks on a slope; torches knock blocks off and repair puts them back; explosions and pistons change nothing; batches of five |
| 4.3 interface and persistence | UI-08, UI-09, MATCH-04 | The full card with hotkeys and reasons; Shift-placed rows; save, quit, resume exactly |
| 4.4 the opponent | AI-02, QA-11's M4 run | The bot lays out its own base and uses all four resources |
| gates | ART-02, PERF-07, the consent batch | M4's Done gate |

### 1.3 Demo script (L7, the owner)

1. `scripts/playtest.sh`; `/strategos skirmish strategos:proving_ground` seats the owner in slot 0
   against the bot (M3's command, the M4 scenario `economy_loop`).
2. Workers mine the rock outcrop by the start: the hill lowers block by block, nobody falls into a
   pit. Two go to the iron mine: its look changes at 75 %.
3. Slaughter a sheep and gather its meat; the carcass empties and goes.
4. Build a farmstead; toggle auto-reseed; a field runs out and reseeds for wood.
5. Place a barracks on the slope by the river: the ground is prepared, then it rises; compare one
   builder with five on two houses.
6. Shift-place three houses in a row for the same builders; cancel the third at half progress and
   see the refund.
7. Train a batch of five spearmen (Shift-click): they reach the rally point together.
8. The bot's torches knock blocks off a house at each threshold; select workers, right-click the
   house: they repair it and the blocks return.
9. `/summon tnt` beside the town and push a piston into a wall: nothing changes.
10. Save and quit mid-match, reload: the match resumes paused, exactly as it was.
11. The questionnaire (`docs/testing/playtest.md`).

### 1.4 Cards, tasks and checks (traceability)

| Card | Task | Checks (level) |
|---|---|---|
| UNIT-05 | T4 | 1 `ActivityTierTest.theTableOfStatesAndOrders` (L1) · 2 `ActivityTierTest.aLongIdleUnitAnswersAnOrderAndABlowWithinATick` (L1) · 3 `ActivityTierTest.hitsLandOnTheSameTicksOnALongIdleTargetAndAJustOrderedOne` (L1) · 4 `nettests.a_viewer_changes_no_hash` (L3) · 5 `perftests.idle_300_costs_a_third` (L6) |
| ECO-07 | T5 | 1 `OutcropTest.aMinedOutcropLeavesItsFloor` (L1) · 2 `OutcropTest.theRateIsTheSheets` (L1) · 3 `OutcropTest.noUnitEndsBelowTheFloor` (L1) · 4 `ecotests.a_worker_mines_an_outcrop_with_no_page_job` (L3) |
| ECO-08 | T6 | 1 `DepositTest.nRemainingYieldsNThenItIsExhaustedAndFreesItsSlots` (L1) · 2 `DepositTest.slotsFollowTheSize` (L1) · 3 `ecotests.a_deposit_changes_look_at_its_stages` (L3); the amount on selection: `DepositStateTest` (L1, 2), `replicationtests.deposits_reach_every_player_as_they_fall` (L3); the map: `FirstLoopDepositsTest` (L1) |
| ECO-16 | T7 | 1 `LivestockTest.killAndGatherYieldTheTemplate` (L1) · 2 `LivestockTest.animalsNeverFleeAndCostNoPopulation` (L1) · 3 `ecotests.a_carcass_depletes_and_its_body_goes` (L3) · 4 the engine-surface inventory lists the fauna type (L2) · 5 `livestock` goldens at 60 blocks (L4, T27) |
| ECO-17 | T15 | 1 `AutoReseedTest.reseedsWhenPaidElseWaits` (L1) · 2 `AutoReseedTest.manualReseedWithTheToggleOff` (L1) · 3 `replicationtests.the_toggle_reaches_its_owner_only` (L3) |
| BLD-04 | T8 | 1 `PlacementRulesTest.theRules` "a sixteenth over 2" (L1, M3's rule kept), and the server's `NOT_FLAT` in check 2 (L3) · 2 `constructiontests.a_barracks_on_a_slope_is_prepared_and_completed` (L3) · 3 `constructiontests.preparation_within_the_writer_budget` (L3) |
| BLD-05 | T9 | 1 `DamageStagesTest.eachThresholdRemovesItsRankedBlocks` (L1) · 2 `DamageStagesTest.thirtyPercentAndBack` (L1) · 3 `DamageStagesTest.hysteresisOfTwoPercent` (L1) · 4 `DamageStagesTest.theBottomRingStays` (L1) · 5 `constructiontests.stages_and_repair_survive_save` (L3) |
| BLD-06 | T10 | 1 `RepairTest.repairTimeIsExactForTheFormula` (L1) · 2 and 3 `RepairTest.workersRepairForFreeAndNeverPastTheMost` (L1) · 4 `constructiontests.stages_and_repair_survive_save` (L3, with BLD-05.5) |
| BLD-07 | T11 | 1 `protectiontests.*`, one per source, each with its control: `a_player_cannot_break_a_building`, `nothing_is_placed_in_a_footprint`, `an_explosion_spares_a_building_and_hurts_it`, `pistons_do_nothing_in_a_match`, `mobs_grief_nothing_in_a_match`, `no_monster_spawns_in_a_match`, `fluids_turn_no_building_block`; crop growth is M3's ECO-05.7 test (L3) · 2 `protectiontests.a_direct_chunk_write_is_undone` (L3) · 3 `protectiontests.builders_blocks_are_protected` and `nothing_is_placed_in_a_footprint` (L3) · 4 `ProtectedBlockIndexTest.aLookupCostsTheSameAtAnySize` (L1) |
| BLD-08 | T12 | 1 `buildingtests.every_role_end_to_end` (L3) · 2 `BuildingLimitTest.oneCivicCentreAFaction` (L1) · 3 `BalanceSheetTest` lists every template (L2) · 4 the balance sheet's sink check (DATA-04.5) (L1) |
| BLD-09 | T13 | 1 `CancelTest.theRefundIsTheDocumentedShare` and `aFinishedBuildingIsDemolishedForNothing` (L1) · 2 `constructiontests.demolish_removes_every_block_and_effect` (L3) |
| PROD-02 | T14 | 1 `BatchTest.aBatchOfFiveTakesTheUnitTimeTimesFiveToThePointEight`, `aCancelledBatchGivesTheFiveCostsBack` (L1) · 2 `prodtests.a_batch_steps_out_together_and_rallies` (L3) |
| UI-08 | T16 | 1 `CommandCardTest.theGridHotkeysAndTheLayout` (L1) · 2 `CommandCardTest.aDisabledButtonNamesItsReason` (L1) · 3 `CommandCardTest.theTooltipNumbersAreTheServersBreakdown` (L1) · 4 `m4_goldens` card frames (L4, T27) |
| UI-09 | T17 | 1 `BuildPageTest` (2: the page's buildings, costs, requirement and keys; each own site's progress bar) (L1) · 2 `constructiontests.shift_placed_foundations_are_built_in_order` (L3) · 3 `m4_goldens` placement frames (L4, T27) |
| MATCH-04 | T18 | 1 `ResumeTest.reloadAtTenEqualsFifteenUninterrupted` (L1, the minutes) and `persistencetests.reload_at_ten_equals_fifteen_uninterrupted` (L3, as ticks) · 2 `runServerSmokeRestart` with a house and a tree (L5) · 3 `persistencetests.the_reconciler_converges_a_crash_between_saves` (L3) · 4 `JournalRingTest.aSavedRingReplaysToItsHash` (L1) · 5 `ClientResumeTest` (L1) |
| AI-02 | T19 | 1–4 `EconomyBotTest.tenSeededFifteenMinuteMatches` (L1: each seed's build order completed with no site a minute without progress or builder; no worker idle over 30 s; after minute 3 every share, a minute's mean, within 10 % of the ratio; no own unit or base node walled off, checked each minute; no think over `AiBudget.CAP`; animals and all four resources gathered) · 5 `aitests.economy_seed_replays_to_its_hash` (L3) |
| ART-02 | T20, T27 | 1 `m4_goldens` node frames at every stage (L4, T27) · 2 the owner's look (L7) |
| PERF-07 | T21 | 1–3 `perftests.economy_soak` and its 2× pass (L6) · 4 the report's node-index, dropsite, placement and writer times (R) |

T1 is the health check from M3's close; T2 and T3 M3's carry-over (§1.6 rows 1–3); T22 the M4 run of
QA-11; T27 the consent batch; T28 the close.

### 1.5 Moved out of M4, with the reason

Nothing moves out at specification.

### 1.6 Differences resolved

| # | Topic | Resolution |
|---|---|---|
| 1 | Builders after placement (M3) | M3 had no way to send workers to an existing foundation: `PlaceBuilding`'s builders were the only ones, and a later order took them off for good (M3's bot lost its barracks that way, M3 §1.6). UI-09's builders taking Shift-placed foundations in order, AI-02's "no abandoned foundations" and BLD-06's repair order all need it: `Build` (wire 19, the issuer's units, a building handle, queue mode) gives each unit `OrderKind.BUILD` on an own foundation, or `REPAIR` on an own damaged finished building; a right click on one with workers selected sends it |
| 2 | The vanilla crowd (M3 §1.6 row 77) | 200 armoured spearmen cost 8.9 ms of units and 37 ms of outline on vanilla; Sodium needs nothing. As built in T3 (`UnitLod`): a selection outline is the body's alone (the outline buffer serves the body's render type only, the layers draw plain), and without Sodium a body past the first 50 drawn in a frame, or beyond 128 blocks, draws without armour and items. 48 blocks, the first plan, would have stripped the looks at 60 blocks (ART-01.3), and 100 bodies left the units at 6 ms and the client tick at 2.6 ms. Vanilla, medians of three: frame 4.91 / 9.63 ms, units 3.80, our drawing 0.49, client tick 0.93 ([perf/client/m4pro.json](../../perf/client/m4pro.json)); every golden but two `m3_goldens` frames unchanged, and those two lost only the held item's outline. `rts_frame`'s drag now counts the own bodies on screen, which the wider spearmen leave |
| 4 | T1's splits | `RtsHud` 1,099 → 789 lines: `HudPaint` (panels, items, text), `HudBanners` (notices, end screen, pace, the group bar), `ResourceBarSegments`, the minimap's drawing in `RtsMinimap`; every golden frame identical after it. `MatchState` 956 → 788: `BuildingLife` (hit points, instant placement, the fall), `TerrainMaterials`, `EconomyChecks` (claims on restore, the books), `Leaders`; it keeps its calls. `NetTests` 914 → 720: helpers to `NetFixtures`, the two economy replication tests to `ReplicationTests` (now `replicationtests.the_queue_reaches_its_owner_as_the_server_has_it` and `replicationtests.faction_state_equals_the_server`). `CrowdSoak` 876 → 616: `SoakResult`, `SoakCosts`. `cameratests.chunk_sent_repair` gets 4,000 ticks |
| 5 | T2 as built | `Build` (wire 19) and `BuildRule`: an unknown handle, another's building or a finished one is refused for every unit (`UNKNOWN_TARGET`, `NOT_OWNER`, `UNSUPPORTED` until BLD-06's repair); the units' orders come from `Builders.assign`, which `PlaceBuildingRule` now shares. The right click on an own unfinished building with units selected sends it (green marker); a spearman in the selection is refused as unsupported. M3's bot sends the nearest free worker to any own unfinished building nobody is on, each think. Checks: `BuildCommandTest` (2), `constructiontests.workers_sent_to_a_foundation_finish_it`; the ten headless seeds unchanged |
| 6 | UNIT-05 measured before building (§5) | On the core alone, 300 standing units cost 15 % of 300 walking ones (workers and spearmen; debug walkers 26 %): UNIT-05.5's 30 % holds already, because each leaf of the unit tree has its own think period (an idle scan every 10 ticks, approaching every 5, working and striking every tick, where the work timers live) and a bus message reaches a unit's machine at once, so nothing sleeps through an order or a blow. T4 therefore makes the tiers explicit without slowing anyone: `ActivityTier` from the unit's state and whether it was ordered within 40 ticks (a view's statistic, neither hashed nor saved), the tier counts in the queries and the overlay, and the card's checks; the leaves keep their periods, so no golden journal moves. The architecture's table stays the target for M6's larger fights, where PERF-08 decides whether working units should think less often |
| 7 | T4 as built | `ActivityTier` (IDLE 0, MOVING 1, WORKING 2, ACTIVE 3, the overlay's codes) from the unit's state and `ActivityTiers`' last order tick, which `UnitAiSystem` notes on every `OrderReceived` (a statistic, neither hashed nor saved); `MatchQueries.tier` and `tierCounts`; the overlay colours each mark by its tier and its panel counts the tiers in view. Checks: `ActivityTierTest` (4: the table; an order keeps a unit ACTIVE 40 ticks, then it walks MOVING; a unit idle for 400 ticks answers an order and a blow within a tick; a spearman's hits land on the same ticks on a target idle 400 ticks and one just ordered), `nettests.a_viewer_changes_no_hash` (two matches of one seed, one watched by a seated viewer: equal hashes at ten checks), `perftests.idle_300_costs_a_third` (standing 0.19 of walking) |
| 8 | T5 as built | An outcrop (`NodeDiscovery.outcrops`) is a group of columns side by side whose top block is rock (`MaterialKind.STONE` from `resource_yield`), its floor the lowest top of the columns round it; its node holds the rock above that floor, top-down then outside-in from the group's middle, its slots the ring round its columns. So flat rock ground is no node, and any stone wall a scene builds is one, harmless until someone mines it. `Quarrying.take` removes a block once its share is gone, through the writer and the core's terrain at once, and cracks the next, as felling does. The worker gathers stone at 0.4 a second, 10 a trip (sheet rows with provenance). DATA-04.5 then wanted stone in two costs, so BLD-08's barracks cost of 100 stone came forward; `first_loop` gives each start 100 stone and the soak's scene a million, and the ten headless seeds still end with a winner (two of them now over the food wait, reported for AI-02). Checks: `OutcropTest` (3), `ecotests.a_worker_mines_an_outcrop_with_no_page_job`; `TemplateSetTest`'s pinned hash re-taken for the new template set |
| 9 | T6's plan, written as T5 closed | A deposit is a building template with a `Deposit` component (`resource`, `subtype`, `amount`, `slots`, `depleted`: the block state its blocks turn to stage by stage) and a placeholder blueprint, small and large for the quarry and the iron mine (`building/quarry`, `quarry_large`, `iron_mine`, `iron_mine_large`). It is spawned, not built, and belongs to no faction, so there is no building record: Gaia as a building owner would reach every per-faction array (`StatService`, population, treasury). `SpawnDeposit` (admin, wire 24: template, corner) blocks its columns, has the writer place its blueprint, and adds a node of a new `MaterialKind.DEPOSIT` holding the blueprint's blocks in reverse build order, the ring round its footprint as slots and the template's amount; its numbers are read with `TemplateSet.value`, which needs no faction. `DepositStore` (in `MatchState`, the snapshot's schema 3, hashed only when it holds something) keeps each deposit's template and stage. `Deposits.take` lowers the amount; at 75, 50, 25 and 0 % the writer turns the next quarter of its blocks to `depleted`; at 0 the slots are freed and the footprint stays. The map's manifest gains `deposits` (template, corner per start, mirrored), which the match's setup spawns. Iron gets a worker rate (0.35 a second, 10 a trip) and two sinks the M3 bot can afford, the civic centre (100) and the barracks (50), the first loop's starts bringing 100 iron |
| 10 | T6 as built | As row 9 planned, with three changes. No `DepositStore`: the node keeps its deposit's template (`NodeStore.template`, -1 for any other node, hashed and saved only when set; the snapshot's node gains an optional `template` id, so older saves load), and the stage comes from the amount left of its most, so nothing else is stored. The deposits are placed by the scenario, not the map's manifest: `first_loop` lists a quarry and an iron mine per start among its entities (offsets from the start, like its units), and `ScenarioSetup` sends `SpawnDeposit` for a deposit template; one placement path serves scenarios, GameTests and maps, and the proving ground's terrain stays v2, so nothing that pins it is re-recorded (an outcrop per start is left to the map: the quarry gives each start its stone; the livestock spots come with T7's scenario entities, the slope with T8). The iron mine's vein mixes iron and deepslate iron ore, so the stone a stage leaves can be counted in the world. The remaining amount on selection is a `deposit_state` payload (the same list for every player, on change): a click on a deposit's blocks shows its name and "N / M iron left", or "Exhausted". Deposits are refused by `PlaceBuildingRule` and left off the build page. Costs: the civic centre 100 iron and the barracks 50 iron (sheet and provenance), the first loop's starts bring 100 iron; the ten headless seeds still end with a winner, faction 0 in all ten, as before T6 (the slot's edge is AI-02's, T19). Checks: §1.4's ECO-08 row |
| 11 | T7's plan, written as T6 closed | Gaia is the slot after the players' (`MatchState.gaia()`, `config.factions()`), seated by nobody: the stats get a block for it, while the treasury, population, AI, alerts, notices and victory stay the players'; a melee scan looks at the players' units only, so nobody engages an animal by itself. `unit/sheep`, `pig` and `chicken` (parent `template/unit_fauna`: class `Fauna`, no cost, no flee, a `Fauna/meat` and `Fauna/slots`) are spawned as Gaia units, each with its meat node from the start: a `MaterialKind.CARCASS` node of food's new subtype `meat` at its cell, slots round it, linked to the animal (`NodeStore.unit`, saved and hashed only when set). So the gathering machine serves it unchanged: a worker sent to it takes a slot, and while the animal lives its work is a blow of its `ResourceGatherer/slaughter` every second through core damage; once it is dead the node is gathered as any other, carried home, and the next animal near is taken when it is empty. An `Attack` on an animal gives a unit that can fight an attack and one that cannot but gathers meat this gather order (the right click needs nothing new). At death a linked animal leaves `CarcassLeft`: the mod keeps its body lying where it fell, not dying, until `CarcassGone` when the node is empty. Animals stand where they are put: §2.4's wander would move the node and needs a home per animal, so it waits for a reason (ART-02 or ECO-10). The bodies get an entity type of their own, `strategos:unit_fauna`, chosen by `Body/entityType`, and a renderer by `Body/model`: vanilla's `PigModel` and `ChickenModel` as they are, and the sheep's baked `ModelLayers.SHEEP` and `SHEEP_FUR` through our own `QuadrupedModel` subclass, so no mesh is copied; ADR-0011 is amended. The first loop puts four sheep, two pigs and three chickens by each start |
| 12 | T7 as built | As row 11 planned. Gaia's units are spawned by `SpawnUnits` only for an animal template, and an animal only for Gaia; `ScenarioSetup` seats a scenario's animals with Gaia, the slot after its own. A worker strikes 2 a second (`ResourceGatherer/slaughter`) and gathers meat at 1 a second; sheep 8 hit points and 100 meat, pig 10 and 150, chicken 4 and 40 (sheet rows with provenance). The node codec's two links, the deposit's template and the animal, are one flat optional map (a record builder takes sixteen fields). Animals stand, though a crowd may jostle them a little; their meat node stays at their spawn cell. M3's bot leaves animals alone: with the first loop's 820 food a side within reach, its rebalance stripped food of gatherers and the soldiers' wood starved the build order (seed 1's done at 14,000 ticks, not 7,200); reserving the next building's cost stalled it instead, so livestock waits for AI-02 (T19), whose scope names it. The headless runs count Gaia's deaths and blows as no faction's; the ten seeds end with a winner, as in T6. Checks: `LivestockTest` (2), `ecotests.a_carcass_depletes_and_its_body_goes`, the inventory's `strategos:unit_fauna`; the `livestock` goldens wait for T27's consent batch, and `m3_goldens` frames that show a start may need re-recording there |
| 13 | T8 as built | M3's construction already prepared the ground at a site's commit: the floor layer at the highest ground under the footprint (`Placing.baseY`), each lower column filled with dirt down to the ground, the blueprint's air cleared, as writer work of the site queued before its first layer, so BLD-04 needed its checks, not code. Two things of §2.6 are left out on purpose. The preparation is not a share of the foundation's progress: the core never learns when the writer is done (the writer's pace must not reach the hash, `writer_budget_never_changes_the_hash`), and the writer serves a site's writes in order, so no layer lands before its ground. The optional apron waits for a reason: the builder slots round a 2-block slope are reached by walking, as every check showed. Checks: the barracks refused on a 3-block step as `NOT_FLAT`, then raised on a 2-block one with no gap under any column and equal to its blueprint; with the writer cut to 8 a tick, never more than 8 a tick and every fill before the first layer |
| 14 | T9 and T10 as built, together | `DamageRanks.of(blueprint)`: every block but the two lowest layers of the outer ring (a column with a side open to the outside), ranked by a hash of the blueprint's id and the block's index, a block ready to fall only once the block standing on it has; worked out once a match per blueprint (derived, neither hashed nor saved). `DamageStages`: the template's `DamageStages/first`, `second`, `third` (85, 65, 35 on `building_base`, with provenance); below a threshold the stage rises at once and the ranks down to the share of hit points it lost fall (15, 35 and 65 % of the falling blocks), each with a `BlockFell` (vanilla's block-break dust); back up, a stage returns only 2 % over its threshold, its blocks in the reverse order. Only a finished building has stages; the stage is saved (optional `stage`) and hashed when not 0, so M3's journals keep their hashes. The core's terrain is not told of a fallen block: the footprint's columns stay blocked and the outer ring stands, so no unit could use the gap. Repair (BLD-06): `Build` on an own damaged finished building (a scenario's gets its builder slots then), the right click on one; the builders' work, by the builder rule, adds `max × work ÷ (2T)` hit points a tick, free (D-15), never past the most, and the stages come back on the way. Checks: `DamageStagesTest` (4), `RepairTest` (2), `constructiontests.stages_and_repair_survive_save` (eight torches to stage 3, its blocks air in the world; saved and restored with its hash; repaired by two workers to its blueprint) |
| 15 | T11 as built | `Protection` (mod, `server.world`), each handler in the match's `FaultBarrier`: a player's break of an indexed block is cancelled (the `breakDamage` rule that would turn it into damage waits for a reason: first-person players are in adventure mode); a block placed by any entity into an indexed position or a building's box (footprint, floor to height) is cancelled; an explosion's protected positions leave its list, and each crushes its building with 4 × (1 − d ÷ 2r) as `ExternalDamage`, a player's when a player set it off; pistons, mob griefing and a mob's block breaking are refused anywhere in a match's space; a fluid turning an indexed block keeps the old state, set back by hand since NeoForge's hook returns the event's new state even when it is cancelled. The backstop: the block-change router, which already sees every change behind the writer's flag, queues an indexed block's old state, and the end of the server tick puts it back through `BlockWriter.restore`, logged once per match. Crop growth and natural spawns stay M3's. Checks: `protectiontests` (9: one per source with its control, the direct chunk write, a worker-built house in the index), `ProtectedBlockIndexTest` (2: lookups at 64 sections within 3× of 4) |
| 16 | T12 as built | Storehouse (wood, stone, iron; 6 × 5 × 6, 128 blocks; 100 wood and 50 stone, 40 s) and farmstead (food; 6 × 4 × 5, 88 blocks; 100 wood, 35 s), placeholder blueprints of non-flammable blocks, style-guide rows and sheet rows with provenance. `Limit/count` (1 on the civic centre) is checked at placement before the ground's rules, counting foundations, with a new reason `LIMIT` (wire 17). Stone now buys three things (civic centre, barracks, storehouse) and iron two (civic centre, barracks); the spearman's iron of §2.9 is left out: M3's bot gathers only food and wood, so a soldier costing iron would stop its army, and AI-02 (T19) brings iron to the bot. The built-in set's net indices are now pinned in full (ADR-0012's append-only table, kept across versions): the farmstead's id sorts before the field's and would have moved every later index, which buildings save and hash. Checks: `buildingtests.every_role_end_to_end` (wood home to the storehouse, food to the farmstead, far from the centre), `BuildingLimitTest`, `StyleGuideRangesTest`, the sheet's sink check |
| 17 | T13 as built | `CancelBuilding` (wire 20) and `Demolish` (wire 21), each naming a building. A cancel takes an own foundation or site down as a fall does (`BuildingLife.destroy`: blocks above the floor through the writer, columns released, housing and queue gone) and refunds `cost × (1000 − progress) ÷ 1000` at today's cost, rounded down (`BuildingLife.refund`; the sheet's new section says so); a finished one is refused as unsupported. A demolish takes an own finished building down the same way with a `BuildingDestroyed`, so the AI and victory see it as a loss; a site is refused. The command card's buttons and the confirmation are UI-08's (T16) |
| 18 | T14 as built | `TrainBatch` (wire 23: building, template, count 1–5) joins one entry to the queue, paid count times at once; the store keeps a batch count beside each entry (saved and hashed only above 1, so M3's queues and journals are as they were). The head takes count × the unit's population, trains for `round(seconds × 20 × count^e)` ticks, e the building's `Trainer/batchExponent` (0.8 on the civic centre and the barracks, with provenance; 0.8 by default), and steps out all of its units on one tick at the door, each taking the rally orders; a cancel refunds count costs and frees the whole reservation. Shift on a train button sends a batch of five. `building_state` does not carry the count yet: the queue's ×5 badge comes with the command card (T16) |
| 19 | T15 as built | A building with a `Reseeder/reach` (the farmstead's 24, with provenance) sows the empty fields of its faction whose middle lies within that reach of its own, once finished and while its toggle is on: each tick, after the fields ripen, an empty one in reach is reseeded as soon as its wood can be paid, and waits otherwise. The toggle starts on when the farmstead stands finished; `SetAutoReseed` (wire 22: building, on) turns it, refused as unsupported on a building with no reach. It is saved with the building's production (optional `autoReseed`) and hashed only when on. It reaches its owner in `building_state`'s owner fields rather than `faction_state`, as §2.5 had it: it is one building's, like its queue and rally, and those fields are already its owner's only. On the card a farmstead shows a hay-bale toggle, dimmed when off. The manual `Reseed` still works |
| 20 | T16 as built | The grid's fifteen cells are Alt and Q W E R T / A S D F G / Z X C V B (actions `CARD_1`–`CARD_15`, rebindable key mappings carrying NeoForge's `KeyModifier.ALT`), not the bare grid of §2.12: W, A, S and D pan the camera, Z, V, B and R are actions, so the card's bindings come first and a held Alt sends the key there; the default-keys test counts a modified binding as no clash, vanilla's gameplay keys never acting in the RTS screen. Each cell shows its key in the corner. A button that cannot be used now names why, first the limit (`Limit reached`), then a full queue, then housing (`Needs housing`), then the missing amounts (`Needs 40 food`), and a press of it sends nothing. The tooltip adds the template's numbers (hit points, the melee blow, armour, speed in blocks a second, time, population, housing), read from the set as the server's stat service reads a template with no modifier, which the test checks. An own site shows Cancel; a finished building Demolish, which a first click arms (red) and a second sends. The queue shows a batch's ×5, `building_state` now carrying the counts (single entries none, so older frames compare equal). The card's goldens wait for T27 |
| 21 | T17 as built | M3's placement already kept itself armed with Shift and queued each foundation for the same builders (`PlaceBuilding` with `QUEUE`); the GameTest proves the builders take them in order. The build page is UI-08's card on its build page: every building a worker places, in net-index order and the grid's cells, each with its cost, the limit as its requirement, back in the last cell. Each own foundation or site has an amber progress bar half a block over its blueprint's top, drawn with the health bars (`HealthBars.sites`). Escape or a right click cancels placement, as in M3. Its goldens wait for T27 |
| 22 | T18 as built | The blob already held each page's slice hash (`PageRef`) and the stores' optional fields of M4; a restored match already came back PAUSED and recaptured its pages from the world, publishing them as the first tick's journaled `TerrainPage` inputs, which is how every difference the core does not own comes in (no new `WorldChange` input was needed). `WorldReconciler` runs before that capture: every block the core owns (`MatchQueries.worldClaims`: each building as built and less its fallen ranks, each block felling and quarrying took) is put back at once through the writer, outside its budget, and each building block protected again, the index being the server's memory. The journal keeps the last `journalRingMinutes` (10 by default, a server setting) for memory and disk alike, and `/strategos journal save` writes them. The client keeps each match's camera pose and control groups by match id (`ClientResume`) and finds them on its return. L1's run from minute 10 to 15 of the first loop found three restore bugs, fixed: the animals counted in a population Gaia lacks (a restore with animals threw), fields blocking their columns after a restore (their farmers stood in a footprint), and `AlertSystem`'s live alerts lost (now `Alerts`, saved, not hashed), whose loss woke the computer players anew. The L3 check runs its minutes as 200 ticks: a GameTest cannot run a quarter hour |
| 23 | T19 as built | The economy script (`ai/economy.json`: ratio 5 food, 5 wood, 1 stone, 1 iron; livestock, balance, reserve; a storehouse by the wood first, then the farmstead and its fields, the barracks, a storehouse by the stone; sites found, none scripted) drives M3's modules with AI-02's options, each off by default so M3's bot plays on as it did. `SiteFinder` takes the corners within 40 blocks of the centre nearest the point a step wants (its resource's nearest node, or an own building), two blocks clear of other footprints so no one-block lane jams, that the placement rules take (`MatchQueries.placementRefusal`), and that wall nothing off: a walk of the region's floors from the centre must reach every node and own unit it reached before. Not §2.14's site map made at match start: the ground changes as the base grows, so each search reads the match as it is, at most 48 sites looked at, each look on the budget. `BuildManager` keeps the stock for the next building a minute (`reserve`) and, before the build order, puts a storehouse by the nearest node beyond 24 blocks of the dropsites once a resource has under 600 left near them, keeping the stock for it until placed; only nodes within the sites' reach count, since a building must stand within its civic centre's 48. `EconomyManager` moves gatherers by the ratio, leaves the enemy's home (64 blocks) alone and holds the fields' auto-reseed while the build order's reserve waits. Ten 15-minute L1 matches found four core faults, each now with its test: a worker pushed out of a foundation landed on a berry bush (`Placing.standFloor`; `BuilderSlotTest`); a unit that walked onto a foundation's columns in the up to `pageK` ticks before the grid had them stood on no surface for good (`Construction.evictBlocked`, every tick in WORLD); a drop point behind a trunk was out of reach from the nearest ground (`Dropsites.dropPoint` on standable ground; `DropsiteTest`); and a queued order its state ended on entry left an `OrderReceived` for an empty queue (`QueuedBuildTest`). The AI's free slots are now only those a unit can stand on (`GatherSlots`). Two judgements: a share is a minute's mean (with 15 gatherers one worker is 7 %, so a single sample breaks 10 % whenever two change jobs), and the 15-minute matches are not held to QA-11's stall rule: once its grove is cut, a side's only wood is the shared forest 180 blocks off, outside any territory, and a player saving 40 wood for a soldier is silent two minutes. The proving ground's wood is a finding for the owner (STATE) |
| 24 | T20 as built | Most of §2.15 was already in: deposits turn a quarter of their blocks to stone a stage (T6), outcrops are stone above the ground round them (T5), a field's crop age follows what is left (M3's `Fields.age`), the animals are vanilla's models (T7). T20 adds the node's selection marker, a quarter-block band of solid quads in the HUD's accent round the selected deposit's footprint, a piece on each ring cell at its own ground (`NodeRing`, core; `NodeMarker`, client), and the `m4_goldens` scenario: the scene is laid out and worked to its stages under `/tick sprint`, each job's workers stopped at its amount (deposits 1,000, 749, 499, 249 and 0 of a row of iron mines and one of quarries; outcrops 90 and 44; fields 300, 150, 0 and one half ripe; a carcass at 50), frozen, then framed from 60 blocks: `deposit_stages`, `outcrop_stages`, `field_stages`, `livestock`, `deposit_selected`. Its first run passed on this Mac; the frames are recorded with and without Sodium in T27. Checks: `NodeRingTest` |
| 25 | T21 as built | `EconomyScene` (core testkit) is §6's: two factions of 75 workers at trees, quarries, mines, fields and sheep (three of eight a handle's turn at trees, two at fields, one each at the rest; a field reaped bare is reseeded), 75 spearmen in M3's waves, towns of 28 laid out a building a tick on a ring of sites the placement rules take (a large quarry and iron mine each besides), five barracks under construction and two houses under repair at any time (one crushed to 60 % as vanilla damage, two workers sent); `FirstLoopScene` and it are a `SoakScene` the soak takes, and `EconomySceneTest` plays it in L1 (constructions in 868 of 869 seconds, repairs in 861). Still map v2, as T6 decided; v3 comes with T22. The core times the node index's searches and builds and the dropsite queries (`EconomyTimes`, measured, never saved or hashed). Results on this Mac (`perf/economy-m4pro.json`): A p50 0.81 ms, p99 5.0 ms; B p95 2.7 ms; C p95 5.9 ms; D p99 0.11 ms; the save 10 ms; a node index search 55 µs (669 in the window), a build 1.5 ms (18), a dropsite query 8 µs (2,677); the stress pass's C p95 7.9 ms. The run found that every scene's workers had stood idle on the server: the arena lies below height 0, and a node's next block, whose height is the world's, was taken for "none left" when negative (`NodeView.hasBlock` now; the economy AI had the same test, so a computer player in an arena world gathered nothing; `AiDecisionTest.belowHeightZeroIdleWorkersStillGather`). M3's per-unit base was taken so, with idle workers, at 2,966 ns; re-taken on this commit with them gathering it is 5,728 (A, 200 units, +2 % over M1) and 9,207 (B), and the economy's 4,919 is 0.86 of it |
| 26 | T22 as built | The proving ground's version 3: a wood of thirty oaks north of each start (5 x 6, 7 apart, Z 120–155; the west's X 8–36, the east's X 347–375, turned round the X axis only, since turned half round the centre it stood in the east's walled yard), its nearest oak about 38 blocks from its start, so the economy outlasts its grove (1,080 wood) without walking to the forest, 180 blocks off and nearer the east. A first place nearer the start's row sent `maptests.two_hundred_cross`'s crowd round it and jammed a ford, and one turned half round sent the east's gatherers into the yard; both are recorded in the generator's comment. The map package is regenerated (eight tiles and the manifest's hash), the minimap image's hash re-pinned, `NodeDiscoveryBench` counts 160 nodes; no golden journal stands on the proving ground. QA-11 on M4 is `EconomyBotTest.tenSeededMatchesEndWithAWinnerWithinThirtyMinutes`: the economy script on both sides, held to QA-11's ending (a winner by 30 minutes, no player silent two minutes), every seed decided (3 to faction 0, 7 to faction 1, from 14:28 to 27:22), no worker idle over 30 s, animals and all four resources gathered, no food wait over a minute after minute 5. The new map found a restore fault: a resumed match's deposits blocked no columns (`Deposits.restoreFootprints` had never been called), so units walked through them after a load and `ResumeTest` diverged at tick 16,497; now restored with the buildings' footprints, before the pages (`DepositTest.aRestoredDepositStillBlocksItsColumns`). The perf soaks re-run on v3: the first loop's A p95 per unit read 11,568, 11,618, 11,737 and 11,648 ns, twice its v2 figure, though the L1 replay of the same match has the step's p95 at 0.37 ms (v3's woods add about a fifth in L1, the busier gathering in UNIT_AI): the server's p95 is mostly its surroundings, vanilla's chunk and GameTest ticking between paced steps (a JFR profile of the server thread, 39 collections in 18 minutes). The report now splits A into the core's step, its phases and the mod's part (0.25 ms at p95). M1's crowd soak no longer fits as PERF-06.2's base: both soaks compare with the first loop's own, 11,648 ns a unit (`perf/first-loop-m4pro.json`, STATE item 18); the economy reads 6,878 |
| 27 | T27 as built | The consent batch, under the owner's word of 2026-10-24. `m4_goldens` gains the town's frames: the civic centre's and the barracks' cards, the idle worker's build page, three houses placed in order for one builder with their bars and a house crushed past its three stages; recorded on macOS and with Sodium, each identical over two runs. A first record differed in six frames: the crushed house's dust, which the sprint left the client no time to live out and the freeze then kept, so the last 80 ticks before the freeze run at the normal pace. The older scenes re-run on M4: `art_look` unchanged with and without Sodium; `m3_goldens`' `select_civic_centre` shows the card's demolish button (re-recorded with the scene's other frames); `overlay_goldens`' `overlay_all` has walkers a pixel apart (re-recorded); `rts_goldens` had failed a check since M3, unseen because a run judges its pixels only: its "empty" click fell under the selection panel and left the selection, which its later frames then showed; it now clicks open grass and its frames are re-recorded. PERF-14 with M4's HUD: vanilla's frame p50 5.2 ms, p99 9.7, units 4.1 ms of 6, the client tick at 120 fps 0.93 ms; with Sodium 2.9, 6.3, 2.2 and 0.83 (`perf/client/m4pro.json`). Linux CI's goldens (`linux-llvmpipe`) are re-recorded when the scenario job next runs by hand |
| 3 | M3's bot and §8.4 | The scripted bot keeps its training fed within 60 s in nine seeds of ten (M3 §1.6); AI-02's economy owns the criterion from M4 on: its L1 run gates it |

### 1.7 Consent plan

The owner's word of 2026-10-24 covers every kind of test. The goldens (`m4_goldens`: the complete
card, placement rows, damage stages, repair, deposits and their stages, outcrops, fields with
reseed, livestock), the ART-02 frames at 60 blocks with and without Sodium, and PERF-14 with M4's
town run together in T27.

---

## 2. Design of the M4 slice

### 2.1 Activity tiers (UNIT-05, 04 §6.5)

- `ActivityTiers` (core, `sim.unitai`): per unit a tier and the tick of its next think, both in
  the snapshot and the hash; ACTIVE 2, MOVING 4, WORKING 10, IDLE 20, ABSTRACT 20 ticks. The tier
  follows the unit's state and its last order (ordered within 40 ticks → ACTIVE), never selection
  or viewers.
- `UnitAiSystem` thinks a unit only on its tick; between thinks, timers drive work (gather,
  build, repair accumulate per tick as now, so totals do not change). Movement and ORCA keep their
  per-tick and every-other-tick cadence by tier; the target scan follows the table.
- Wake messages (attacked, order received, target gone, node exhausted, a block change under it,
  a stat change) set the tier to ACTIVE and the next think to the next tick; posted by the systems
  that cause them, through `ActivityTiers.wake(handle)`.
- Determinism: the phase within a tier's period is `handle index mod period`, so thinks spread
  over ticks and never depend on iteration order.
- The overlay's unit layer shows tier counts.

### 2.2 Stone outcrops (ECO-07, 04 §11.2)

- `NodeDiscovery` already indexes stone-family blocks through `resource_yield` (M3 §1.5); an
  outcrop is a connected set of exposed stone blocks above the surrounding floor (the page's
  surface height around it).
- `Outcrop` (core, `sim.econ`): blocks ordered top-down then outside-in from the outcrop's centre
  of mass; a block is minable only when nothing minable is above it and it is above the floor; the
  per-block quota from the data map, the gather rate from the worker's template (the sheet).
- Mining a block asks the writer to remove it (`BLOCK_BREAK` fx), and the core updates its own
  surface page (no reclassification: PATH-15's rule, as felling does).
- Gather slots: the exposed perimeter cells of the outcrop's current top layer.

### 2.3 Deposits (ECO-08)

- `Deposit` (core): amount (1,000 or 5,000 by size), slots (3 or 6), stage at 75, 50, 25 and 0 %;
  a stage change asks the writer to swap the deposit's blocks to the stage's palette (data:
  `strategos/deposits/<kind>.json`, per stage the block states of its footprint).
- Deposits are placed by the map: the proving ground v3 gets a quarry and an iron mine near each
  start (the map package's manifest lists them as `deposits` with kind, size and footprint).
- Selection shows the remaining amount (`building_state`-like `node_state` entries for the
  selected node only, NET-08's subscription).

### 2.4 Livestock and Gaia (ECO-16)

- Gaia: `FactionSlot.gaia(factions)`, the slot after the players'. The scenario spawns sheep, pigs
  and chickens by each start as Gaia units (`strategos:unit/sheep`, `pig`, `chicken`): no
  population, never fleeing (no FLEE state), a slow wander within 4 blocks of their spawn.
- A worker's gather order on an animal becomes an attack with the worker's melee (core damage);
  at death the animal leaves a `Carcass` node at its position with the template's meat amount; the
  body stays until the node empties, then goes.
- Rendering: `strategos:unit_fauna`, a second entity type with its own renderer that picks the
  model by the template's archetype: vanilla `PigModel` and `ChickenModel` draw our body as they
  are; the sheep's mesh is our own `SheepLikeModel`, its `createBodyLayer()` written from the
  vanilla layer's shape. ADR-0011 is amended and the engine-surface inventory lists the type.

### 2.5 Auto-reseed (ECO-17, D-14)

- A farmstead holds a toggle (`SetAutoReseed`, wire 22, a building handle and on/off); an
  exhausted field within the farmstead's reach (24 blocks) reseeds at its next tick when the
  owner can pay the wood, and waits otherwise. Manual `Reseed` still works.
- The toggle is in `faction_state` per farmstead, its owner's only.

### 2.6 Foundations on uneven ground (BLD-04)

- M3's `PlacementRules` already refuse a floor spread above 2 (M3 §1.6 row 47). M4 prepares the
  ground: the foundation's base is the highest floor under it; columns below are filled with the
  blueprint's base block down to the ground, cells above are cleared, and an optional one-block
  apron levels the ring of builder slots. Preparation is writer work at `WritePriority.BUILD`
  before the first layer, counted in the foundation's progress as its first share, and the core
  updates its overlay as M3's construction does.

### 2.7 Damage stages and repair (BLD-05, BLD-06, 04 §11.8)

- `DamageRanks` (core): per blueprint, every removable block's rank from a hash of the blueprint
  id and the block index, then repaired so no block ranks before one it holds up (the build-order
  support graph, M3's blueprint order); the two lowest layers of the outer ring are excluded.
- A building's stage: the count of thresholds (85, 65, 35 % by default, template values) its HP
  is below; a stage change asks the writer to remove (or restore) the blocks of the ranks between
  the old and the new fraction, with falling-debris fx; repair restores a stage only 2 % above its
  threshold (hysteresis); the stage is saved.
- Repair: `Build` on an own damaged finished building gives `REPAIR`; HP rises at half the build
  rate × n^0.7 (the builder rule), free (D-15); it never exceeds the maximum.

### 2.8 Protection (BLD-07, 04 §11.9)

- `Protection` (mod, `server.world`): one handler per source in the architecture's table, each in
  `FaultBarrier`, each reading `ProtectedBlockIndex` (M1's, filled by the writer): player break
  (cancel; the match rule `breakDamage` converts it to building damage), entity place inside
  footprints, explosion (protected positions pruned from the affected list, converted to crush
  damage by distance), mob griefing and `LivingDestroyBlockEvent`, pistons, fluids, crop growth and
  natural spawns (M3's).
- The backstop: M1's `LevelChunkMixin` already routes every change; a protected position changed
  without the writer's flag is restored at the end of the tick from the core's state, logged once
  per source.

### 2.9 The starting building set (BLD-08)

- New templates and blueprints: storehouse (dropsite for wood, stone and iron) and farmstead
  (dropsite for food; the reseed toggle). Costs and times from the balance sheet: stone pays for
  the civic centre, barracks and storehouse; iron for the spearman's weapons (a cost on
  `strategos:unit/infantry`) and the barracks' upgrade-free roster. DATA-04's sink check (each
  gatherable resource in two costs) then holds for all four.
- Limits: one civic centre until Town (a template value `Limit/count`, checked at placement with
  the reason `LIMIT`).

### 2.10 Cancel and demolish (BLD-09)

- `CancelBuilding` (wire 20): an own foundation or construction; refund = cost × (1 − progress)
  rounded down to the thousandth, documented in the sheet; its blocks removed through the writer.
- `Demolish` (wire 21): an own finished building, after the client's confirmation; no refund; its
  population, dropsite and production effects removed through `Production.finished`'s inverse; the
  blocks removed under the writer's budget.

### 2.11 Batches (PROD-02)

- `TrainBatch` (wire 23): building, template, count (1–5); time = unit time × count^0.8, the
  exponent a building template value, rounded to ticks once; paid in full at queue time; the
  batch spawns together and walks to the rally point. Shift-click on a train button sends a batch
  of five.

### 2.12 The command card and the build menu (UI-08, UI-09)

- Grid hotkeys Q W E R T / A S D F G / Z X C V B for the 15 cells (rebindable as keys), shown in
  the cell's corner; a disabled button names its reason (the missing amounts, the population, the
  limit); the tooltip lists DATA-02's breakdown from the template set; the queue with cancel.
- The worker's build page lists every building with cost and requirement; Shift keeps placement
  armed and queues each foundation for the same builders (`PlaceBuilding` with `QUEUE`); the
  foundations' progress shows on screen.

### 2.13 Save and resume (MATCH-04, 04 §5.6, ADR-0018)

- The blob gains the page content hashes (one `long` per captured page) and the schema 3 stores;
  restored matches already come back `PAUSED` (M1).
- `WorldReconciler` (mod): at resume, re-slices every page, compares its hash; for a page that
  differs, blocks the core owns (footprints, depleted node blocks, damage stages) are rewritten
  from core state by the writer, and every other difference enters the first tick as
  `WorldChange(EXTERNAL)`, a new input that the core treats as M1's block edits.
- The production journal ring: the last 10 minutes (config `journalRingMinutes`), and
  `/strategos journal save` writes it (M1's command, now from the ring).
- The client keeps camera pose and control groups per match id across a rejoin.

### 2.14 The AI economy (AI-02)

- New managers behind M3's `AiModule` interface: `SiteMap` (a pure job at match start: candidate
  sites per building size round the centre, scored by distance and flatness, with reachability
  kept: no site may close the last passage between the centre and a node), `EconomyManager` for
  four resources and livestock with reassignment when nodes run out, dropsites near resources,
  fields with auto-reseed, houses before the cap, and `Build` to staff any own unfinished building
  without builders. One central budget (04 §14).
- Its build order lives in `ai/economy.json`; `first_loop.json` stays M3's.

### 2.15 Resource visuals (ART-02)

- Deposits' stage palettes, outcrops in vanilla stone and ore blocks, crop stages from the field's
  amount, livestock from vanilla models; a selection marker on the selected node (a ring of solid
  quads like M3's rally marker).

---

## 3. Build changes

None planned: the work uses M3's toolchain. `rts_frame` gains `-Pframe.units` (done in M3 T30).

---

## 4. Tests per level

As M3 §4. New suites: `protectiontests` (one batch per source, solo where a vanilla mechanism
needs the world to itself), `persisttests` (the reload and reconciler cases). The headless runner
gains `economy.json` and its gates (T22).

---

## 5. Measurements before building on them

| What | Where | Why |
|---|---|---|
| A sleeping unit's cost against a walking one | `perftests.idle_300_costs_a_third` (T4) | UNIT-05.5 before PERF-07 relies on it |
| Preparation's writer time on a 2-block slope | `constructiontests.preparation_within_the_writer_budget` (T8) | BLD-04.3 |
| A stage change's block count and writer time on the civic centre | `DamageStagesTest` (T9), the soak (T21) | R4 |

---

## 6. The economy soak (T21) — PERF-07

The M4 row of the budget matrix (as built on v2, §1.6 row 25): on the proving ground v3, 150 workers on trees, rock, mines,
fields and livestock, 150 other units (spearmen in M3's waves), 60 buildings with five under
construction and two under repair, for 15 game minutes paced at 20 ticks a second; A–E and G;
per-unit p95 of A within 15 % of M3's baseline ([perf/first-loop-m4pro.json](../../perf/first-loop-m4pro.json));
a 2× stress pass holding C p95 ≤ 50 ms; the report lists node-index, dropsite-query, placement
and writer times. `FirstLoopScene` grows into `EconomyScene` (core testkit) the same way.

---

## 7. Task breakdown

### 7.1 Order

T1 → T2 → T3 → T4 (UNIT-05) → T5 (ECO-07) → T6 (ECO-08) → T7 (ECO-16) → T8 (BLD-04) → T9 (BLD-05) →
T10 (BLD-06) → T11 (BLD-07) → T12 (BLD-08) → T13 (BLD-09) → T14 (PROD-02) → T15 (ECO-17) → T16
(UI-08) → T17 (UI-09) → T18 (MATCH-04) → T19 (AI-02) → T20 (ART-02's scenes) → T21 (PERF-07) → T22
(QA-11 on M4) → T27 (the consent batch) → T28 (close). T23–T26 are reserved for rows a task adds.

### 7.2 Tasks

#### T1 · Health check from M3's close
- Split the four classes over 800 lines: `RtsHud` (1,099: the minimap and alert drawing to
  `RtsHudMap`, the panel and card to `RtsHudCard`), `MatchState` (956: the build and economy
  accessors to a `MatchStores` holder), `NetTests` (914, by area), `CrowdSoak` (876: the first loop's
  scene driving to `FirstLoopSoak`). `cameratests.chunk_sent_repair` timed out once on CI under
  load (run 40055906537): its 2,000 ticks become 4,000 and its wall-clock limit stays.
- **Verification:** `verify verifySlow`; CI.

#### T2 · `Build`: help build and repair orders
- §1.6 row 1. `Build` (wire 19) and its rule; the right click on an own foundation or damaged
  building with workers selected; M3's bot staffs any unfinished building through it.
- **Verification:** `BuildCommandTest` (L1), `constructiontests.workers_sent_to_a_foundation_finish_it` (L3).

#### T3 · The vanilla crowd's level of detail
- §1.6 row 2: `UnitRenderer` skips the armour and item layers in the outline pass, and on vanilla for
  bodies past the first 50 drawn this frame or beyond 128 blocks. `rts_frame -Pframe.units=infantry` on
  vanilla: units ≤ 6 ms and our drawing ≤ 3 ms.
- **Verification:** `rts_frame` three times on vanilla and Sodium; `art_look` and `m3_goldens`
  unchanged.

#### T4 · UNIT-05 activity tiers · T5 · ECO-07 outcrops · T6 · ECO-08 deposits · T7 · ECO-16 livestock
- §2.1–2.4. T6 makes the proving ground v3 (deposits, an outcrop and livestock spots per start, the
  slope for T8), re-records what pins v2.
- **Verification:** `verify verifySlow`; the headless ten seeds stay green.

#### T8 · BLD-04 · T9 · BLD-05 · T10 · BLD-06 · T11 · BLD-07 · T12 · BLD-08 · T13 · BLD-09 · T14 · PROD-02 · T15 · ECO-17
- §2.5–2.11, each with its checks in §1.4.
- **Verification:** `verify verifySlow`.

#### T16 · UI-08 · T17 · UI-09
- §2.12; CI's Linux route for the scenario parts, the goldens in T27.

#### T18 · MATCH-04
- §2.13; the smoke's restart round trip extended to buildings and nodes.
- **Verification:** `verify verifySlow verifySoak`.

#### T19 · AI-02 · T22 · QA-11 on M4
- §2.14; `:core:headlessMatches -Pscript=economy` over ten seeds: every match ends with a winner
  within 30 minutes; the AI uses all four resources and livestock; no worker idle longer than 30 s;
  no training waits for food longer than 60 s after minute 5 outside a raid (M3 §8.4's rule).
- As built (§1.6 row 23): T19 holds AI-02's acceptance in `EconomyBotTest`'s ten 15-minute matches;
  T22's 30-minute gate is QA-11's, run on the same script.

#### T20 · ART-02's scenes · T21 · PERF-07
- §2.15 and §6.

#### T27 · The consent batch · T28 · M4 close
- §1.7; §8 walked into results, the health check, STATE with the demo.

### 7.3 Progress

- [x] T1 (2026-10-24) · [x] T2 (2026-10-24) · [x] T3 (2026-10-24) · [x] T4 (2026-10-24) · [x] T5 (2026-10-24) · [x] T6 (2026-10-24) · [x] T7 (2026-10-24) · [x] T8 (2026-10-24) · [x] T9 (2026-10-24) · [x] T10 (2026-10-24) · [x] T11 (2026-10-24)
- [x] T12 (2026-10-24) · [x] T13 (2026-10-24) · [x] T14 (2026-10-24) · [x] T15 (2026-10-24) · [x] T16 (2026-10-24) · [x] T17 (2026-10-24) · [x] T18 (2026-10-24) · [x] T19 (2026-10-24) · [x] T20 (2026-10-24) · [x] T21 (2026-10-24) · [x] T22 (2026-10-24)
- [ ] T27 · [ ] T28

---

## 8. Acceptance checklist (M4 exit)

| # | Criterion | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the M4 cards passes at its level | §1.4 |
| 2 | L1: gather rates, field returns and reseeding, deposit depletion, damage stages with hysteresis, batch timing and treasury conservation over 200 seeds; every gatherable resource in at least two costs | the L1 suites, `BalanceSheetTest` |
| 3 | L3: deposits yield exactly their amount; a foundation on a slope is prepared and completed; stages and repair round-trip through save; every protection source fails on a building and works on its control; a batch of 5 reaches its rally point; a reload at minute 10 equals an uninterrupted run at 15; a viewer changes no hash | `ecotests`, `constructiontests`, `protectiontests`, `prodtests`, `persisttests`, `nettests` |
| 4 | QA-11: ten L1 seeds and the L3 sample end with a winner; the AI uses four resources and livestock and completes its build order with no worker idle longer than 30 s | `headlessMatches -Pscript=economy`, `aitests` |
| 5 | L6: the economy soak within A–E and G, per-unit A within 15 % of M3's baseline; the 2× pass holds C p95 ≤ 50 ms | `perftests.economy_soak` |
| 6 | L5: the restart round trip continues a match with buildings and nodes bit for bit | `runServerSmoke` |
| 7 | The consent batch: the M4 goldens | T27 |
| 8 | M1's to M3's gates still pass; `verify`, `verifySlow`, `verifySoak`, `verifyPerf` green; CI green | the runs |
| 9 | L7: the owner's playtest with the questionnaire; no score of 2 or less; no P0 or P1 bug open | the owner's note |

---

## 9. Risks and fallbacks

| # | Risk | Sign | Fallback |
|---|---|---|---|
| R1 | Tiers change behaviour (a late reaction reads as a bug) | UNIT-05.3, the golden journals | Wake on every event that matters; shorter periods; tiers off per class |
| R2 | Outcrop mining opens pits on odd terrain | ECO-07.3 | Mine only blocks with a standable neighbour at their height |
| R3 | Damage ranks remove a block something still rests on | BLD-05.1, the support graph | Rank by the build order reversed, no hash |
| R4 | A stage change on a big building exceeds the writer's budget | the soak's writer time | Spread a stage over ticks; the core's HP is authoritative meanwhile |
| R5 | A protection source has no event in 1.21.1 or fires too late | BLD-07.1's control case | The `LevelChunkMixin` backstop restores it at the tick's end |
| R6 | The reconciler rewrites blocks the player changed offline on purpose | MATCH-04.3 | Only core-owned blocks are rewritten; the rest are journaled world changes |
| R7 | The AI walls itself in | AI-02.3 | The site map's reachability rule; a refused site is skipped |
| R8 | Map v3 breaks pinned hashes | `GoldenJournalTest`, `MinimapImageTest` | Re-record with a reason (M1's procedure) |

---

## 10. Owner inputs

| Input | When | Blocks |
|---|---|---|
| The goldens and the looks (ART-02) | T27 | Accepted |
| The playtest and the questionnaire | after the demo | Accepted |
| M3's demo and "would I play another?" (decision gate 3) | any time | M3's Accepted |
