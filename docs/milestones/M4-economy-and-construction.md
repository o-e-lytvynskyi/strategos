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
| ECO-08 | T6 | 1 `DepositTest.nRemainingYieldsN` (L1) · 2 `DepositTest.slotsFollowTheSize` (L1) · 3 `ecotests.a_deposit_changes_look_at_its_stages` (L3) |
| ECO-16 | T7 | 1 `LivestockTest.killAndGatherYieldTheTemplate` (L1) · 2 `LivestockTest.animalsNeverFleeAndCostNoPopulation` (L1) · 3 `ecotests.a_carcass_depletes_and_its_body_goes` (L3) · 4 the engine-surface inventory lists the fauna type (L2) · 5 `livestock` goldens at 60 blocks (L4, T27) |
| ECO-17 | T15 | 1 `AutoReseedTest.reseedsWhenPaidElseWaits` (L1) · 2 `AutoReseedTest.manualReseedWithTheToggleOff` (L1) · 3 `nettests.the_toggle_reaches_its_owner_only` (L3) |
| BLD-04 | T8 | 1 `PlacementRulesTest.aSpreadAboveTwoIsRefusedWithItsReason` (L1, M3's rule kept) · 2 `constructiontests.a_barracks_on_a_slope_is_prepared_and_completed` (L3) · 3 `constructiontests.preparation_within_the_writer_budget` (L3) |
| BLD-05 | T9 | 1 `DamageStagesTest.eachThresholdRemovesItsRankedBlocks` (L1) · 2 `DamageStagesTest.thirtyPercentAndBack` (L1) · 3 `DamageStagesTest.hysteresisOfTwoPercent` (L1) · 4 `DamageStagesTest.theBottomRingStays` (L1) · 5 `constructiontests.stages_and_repair_survive_save` (L3) |
| BLD-06 | T10 | 1 `RepairTest.timeForKBuildersIsTheFormula` (L1) · 2 `RepairTest.costsFollowD15` (L1) · 3 `RepairTest.neverAboveMaximum` (L1) · 4 `constructiontests.repair_restores_the_blocks` (L3) |
| BLD-07 | T11 | 1 `protectiontests.*`, one per source, each with its control (L3) · 2 `protectiontests.a_direct_chunk_write_is_restored` (L3) · 3 `protectiontests.builders_blocks_pass_foreign_blocks_do_not` (L3) · 4 `ProtectedBlockIndexTest.aLookupIsConstant` (L1) |
| BLD-08 | T12 | 1 `buildingtests.every_role_end_to_end` (L3) · 2 `BuildingLimitTest` (L1) · 3 `BalanceSheetTest` lists every template (L2) · 4 `BalanceSheetTest.stoneAndIronEachBuyTwoThings` (L1) |
| BLD-09 | T13 | 1 `CancelTest.theRefundIsTheDocumentedShare` (L1) · 2 `constructiontests.demolish_removes_every_block_and_effect` (L3) |
| PROD-02 | T14 | 1 `BatchTest.fiveTakeUnitTimeTimesFiveToThePointEight` (L1) · 2 `prodtests.a_batch_of_five_reaches_its_rally_point` (L3) |
| UI-08 | T16 | 1 `CommandCardHotkeyTest` (L1) · 2 `CommandCardTest.aDisabledButtonNamesWhatIsMissing` (L1) · 3 `CommandCardTest.tooltipNumbersAreTheServersBreakdown` (L1) · 4 `m4_goldens` card frames (L4, T27) |
| UI-09 | T17 | 1 `BuildPageTest` layout and hotkeys (L1) · 2 `constructiontests.shift_placed_foundations_are_built_in_order` (L3) · 3 `m4_goldens` placement frames (L4, T27) |
| MATCH-04 | T18 | 1 `persisttests.reload_at_ten_equals_fifteen_uninterrupted` (L3) · 2 `runServerSmoke`'s restart round trip with buildings and nodes (L5) · 3 `persisttests.the_reconciler_converges_a_crash_between_saves` (L3) · 4 `JournalRingTest.aSavedRingReplaysToItsHash` (L1) · 5 `ClientResumeTest` (L1) |
| AI-02 | T19 | 1 `EconomyBotTest.tenSeedsNoBlockedOrAbandonedFoundation` (L1) · 2 `EconomyBotTest.noIdleWorkerOver30sAndSharesWithin10Percent` (L1) · 3 `EconomyBotTest.neverWallsOffItsOwn` (L1) · 4 `EconomyBotTest.withinTheBudget` (L1) · 5 `aitests.economy_seed_replays_to_its_hash` (L3) |
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

The M4 row of the budget matrix: on the proving ground v3, 150 workers on trees, rock, mines,
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

#### T20 · ART-02's scenes · T21 · PERF-07
- §2.15 and §6.

#### T27 · The consent batch · T28 · M4 close
- §1.7; §8 walked into results, the health check, STATE with the demo.

### 7.3 Progress

- [x] T1 (2026-10-24) · [x] T2 (2026-10-24) · [x] T3 (2026-10-24) · [x] T4 (2026-10-24) · [x] T5 (2026-10-24) · [ ] T6 · [ ] T7 · [ ] T8 · [ ] T9 · [ ] T10 · [ ] T11
- [ ] T12 · [ ] T13 · [ ] T14 · [ ] T15 · [ ] T16 · [ ] T17 · [ ] T18 · [ ] T19 · [ ] T20 · [ ] T21 · [ ] T22
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
