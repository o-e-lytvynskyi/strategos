# M9 — Territory, capture and defence: implementation spec

Cards: [roadmap/M9-territory-capture-and-defence.md](../roadmap/M9-territory-capture-and-defence.md#cards), ten
cards approved by the owner on 2026-11-24 («Одобряю все 10»). Developer: `o-e-lytvynskyi`
([branch file](../branches/o-e-lytvynskyi-m9.md)). Base: `main` after M6 (2026-11-24).

## 0. Read this first

### 0.1 Authority

As M6 §0.1: the architecture and the ADRs decide *what*, this spec *how M9 builds it*; the cards define scope and
acceptance; ROADMAP.md holds the statuses; a disagreement is a §1.6 row.

**M7 is not merged** (it is held by `lev1pes`, and its branch carries `sim.diplomacy.DiplomacyView`). M9 needs two
things from it: who is an ally (MATCH-06) and the multiplayer perf batch (PERF-10). Both are built **against a
contract** (§2.1): M9 asks one narrow question, `Allies.allied(a, b)`, answered today by "the same faction", and
**AFTER-M7** by `DiplomacyView.allied`. M8 (fog, `lev1pes`, claimed) is not needed. M9 never edits M7's or M8's
files; where a shared file must change (`BuildingLife`, touched by M7), the change is small and merged by hand.

### 0.2 Working rules for every task

M6 §0.2 holds, with these changes:
- **Windows.** Every L4 check of M9 runs in the consent batch (T19), so the screen is taken once.
- **Long runs** go off the working copy (05-testing.md "Long runs"): `tools/longrun.sh` for `verifySoak` and the
  perf batch, never in the working copy while editing.
- **Order.** T1 first (the health check's moves); then the territory chain (T2–T5) and, beside it, the garrison and
  the towers (T10–T11), which need no territory. Capture (T7–T9) follows territory; gates and palisades (T12–T14)
  need only PATH-04. The AI (T15) and the perf gate (T16) come last (§7.1).

### 0.3 Code conventions

M6 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Snapshot | `MatchSnapshot.Play` gains `territory` (the source set's version and the delivered grid's hash; the grid is re-derived at restore, never saved) and `capture` (per building, its points per faction), each optional and saved only when non-empty, with the back-compat constructors of the M6 pattern; `SchemaVersion` stays 3 |
| Commands | The next free wire id is 32: `Capture` 32, `UpgradeBuilding` 33, `PlaceWall` 34 |
| Randomness | None: territory, capture and gates are deterministic without a stream |
| AI memory | `AiMemory.SIZE` 31 → 34: `CAPTURE` 31 (the target being captured), `CAPTURE_SINCE` 32, `GARRISONED` 33 (the tick of the last defensive garrison) |
| Payloads | New S2C `strategos:territory` (§2.4); `BuildingState.Entry` gains the capture fraction per faction (a byte per faction present, at most 8) and the upgrade's progress; every cap pinned in `WireCodecSizeTest` and `PayloadRoundTripTest` |
| Template paths | `TerritoryInfluence/{weight,radius,root}`, `Placement/territory` (0 anywhere, 1 own, 2 own or neutral), `Capturable/{points,regen,garrisonRegen}`, `Capture/strength`, `GarrisonHolder/{heal,ejectBelow}`, `Upgrade/{to,time}` with the cost under `Upgrade/Cost`, `Wall/{segment,gate}`; constants in `CorePaths`, provenance in `provenance.json` |
| Balance | Every new number has its provenance note and its sheet row (`:core:balanceSheet`) |
| Relations | Every new "friend or foe" question goes through `Allies` (§2.1), never a faction `==` |

### 0.4 Engine names to check (T6, T13; `api-lookup`)

`AddSectionGeometryEvent#addRenderer` and its `SectionRenderingContext` (04 §10.6 cites
AddSectionGeometryEvent.java:72); `LevelRenderer#setSectionDirty(int, int, int)` (LevelRenderer.java:2543);
`FenceGateBlock.OPEN`; Sodium's handling of `AddSectionGeometryEvent` (the L4 goldens under Sodium answer it).
Checked names and their lines go into this section when T6 runs.

---

## 1. Goal, demo and scope

### 1.1 Goal

0 A.D.'s identity on top of the MVP: territory that limits building, buildings you capture instead of raze, the
full garrison (healing, ejection, capture regeneration), towers that garrison and upgrade, and palisades with gates.

### 1.2 Slices

1. **Territory** (TERR-01, TERR-02, TERR-03): the grid, the build rule, the borders.
2. **Capture and garrison** (CMB-10, BLD-10): capture points, the capture order, healing, ejection, regeneration.
3. **Defence works** (BLD-12, BLD-13, PATH-12): the sentry tower and upgrades, palisades and gates.
4. **The AI and the gate** (AI-08, PERF-16).

### 1.3 Demo script (L7, the owner)

`scripts/playtest.sh`, then a skirmish on the first map against Normal:
1. Borders show on the ground and on the minimap; a house dragged outside them is refused with "outside your
   territory".
2. Reach Town; place a second civic centre in neutral land; the border grows when it completes.
3. Twenty infantry hold the capture key on an enemy barracks: the capture bar fills, the barracks turns to your
   colour on the minimap and trains for you.
4. Garrison wounded soldiers in the civic centre: they heal; set a rally point on a tower: new units garrison it.
5. Build a sentry tower at a chokepoint, upgrade it in Town: the garrison stays.
6. Drag a palisade across the gap with a gate: your units walk through, the enemy's walk around or attack it.

The demo chapters (`-Pdemo=m9`): `territory`, `capture`, `garrison_heal`, `towers`, `palisade`.

### 1.4 Cards, tasks and checks (traceability)

| Card | Tasks | Acceptance → check |
|---|---|---|
| TERR-01 | T2, T3, T4, T16 | 1 `TerritoryReferenceTest`; 2 `TerritoryThreadsTest`; 3 PERF-16's churn scene (`perf_territory`); 4 `TerritoryCodecTest` |
| TERR-02 | T5, T15 | 1, 2 `PlacementTerritoryTest`; 3 `AiTerritoryPlacementTest`; 4 `territorytests.refused_outside` |
| TERR-03 | T6, T19 | 1, 2 `BorderGeometryTest`; 3 `BorderBench` in `jmhCompare`, PERF-14's border scene (consent); 4 border goldens, vanilla and Sodium (consent) |
| CMB-10 | T7, T8, T9 | 1 `CaptureMathsTest`; 2 `CaptureOwnershipTest`; 3 `capturetests.captured_barracks_trains` |
| BLD-10 | T10 | 1 `GarrisonHealTest`; 2 `GarrisonEjectTest`; 3 `CaptureRegenTest`; 4 `garrisontests.rally_garrisons` |
| BLD-12 | T11 | 1 `TowerDataTest`; 2 `towertests.upgrade_keeps_garrison`; 3 the tower blueprints in `constructiontests` and the damage-stage suite |
| BLD-13 | T13, T14 | 1 `WallLineTest`; 2 `walltests.segments_match_layout`; 3 `walltests.gate_allies_only`; 4 `walltests.enemies_around_or_through` |
| PATH-12 | T12, T14 | 1 `GateReachabilityTest`; 2 `pathtests.own_gate_no_stop` |
| AI-08 | T15 | 1 `AiCaptureTest`; 2 `AiTerritoryPlacementTest`; 3 `AiGarrisonTowerTest` |
| PERF-16 | T16, T18 | 1 `perf_territory` A–C and E; 2 `perf_territory_stress` C p95 ≤ 50 ms; 3 the report's `territory` and `garrison` costs |

### 1.5 Moved out of M9, with the reason

- **Allied capture, allied gates and allied garrisons in a real team match** — AFTER-M7: MATCH-06's teams. M9's
  tests cover them with a fake `Allies` (§2.1); the GameTests with two allied players run when M7 lands.
- **PERF-16's multiplayer pass** (four clients) — AFTER-M7: PERF-10's batch. M9's gate runs the same budgets on one
  server with four AI factions.
- **Territory decay** — TERR-04, as the cards say.

### 1.6 Differences resolved

| # | Topic | Found | Resolution |
|---|---|---|---|
| 1 | Relations | The core has no ally notion: every friend-or-foe check is a faction `==` (`Garrisons.admits`, `UnitFilter.enemiesOf`) | `Allies` (§2.1), a contract M7's `DiplomacyView` fills |
| 2 | Ownership | `BuildingStore` has no way to change a building's faction | `BuildingStore.transfer` (§2.5), with every owner-derived state re-derived |
| 3 | The default building order | CMB-03's torches are the right-click on buildings | As 0 A.D. since Alpha 27: right-click attacks, holding the capture key (`key.strategos.capture`, C) captures; the AI chooses by the target (§2.12) |
| 4 | Payload cap | TERR-01 asks an update under 8 KB; a full grid of a 1,024² map is 16 K tiles | Deltas of changed tiles; an update over 7 KB is split across ticks (§2.4) |
| 5 | Building colour | Buildings carry no owner colour in the world, only on the minimap | The capture bar and the minimap show ownership; an owner banner on buildings is a proposal (For the owner), not M9 scope |
| 6 | Scale | 0 A.D.'s numbers are in metres on maps three times ours | Distances × 0.4 (the first map is 384 blocks against 0 A.D.'s ~1,000 m); capture points and rates keep 0 A.D.'s ratios to Health (§2.6), each with its provenance note |

| 7 | M6's follow-ups | M6 asked for a fake `DemoStage` in the next milestone's first task; `DemoStage` is a class over a `ServerLevel` | T17, with the new chapters; T1 keeps the moves |

| 10 | The first land (T5) | The first grid was computed on a match's first tick, before its centres came, so the centres waited out the cadence and nothing could be placed for 20 ticks | A recompute is immediate while no source has ever been delivered (§2.3) |
| 9 | Territory and fog (T4) | The cards do not say whether enemy land shows through the fog | Territory is public in M9, one grid for every client, as the shroud does not hide terrain; AFTER-M8: FOW-05's leak audit decides |
| 8 | The territory hash (T2, T3) | Mixing the grid into the state hash would change every saved journal's hashes; the grid is a function of saved state | The grid is never hashed; `Play.territory` saves the delivered and the pending source sets, and a restore rebuilds both grids exactly (§2.3 amended) |

New rows are added as tasks find them.

### 1.7 Consent plan

One batch, T19: the border goldens (three scenes, vanilla and Sodium), PERF-14's border scene (budget F with 200
units and borders on the terrain and the minimap), and the new demo chapters once. About 25 minutes of windows.

---

## 2. Design of the M9 slice

### 2.1 Allies, the contract (MATCH-06's seam)

`core/sim/Allies.java`: `boolean allied(int factionA, int factionB)` — true for the same faction; Gaia is allied
with nobody. `MatchState.allies()` returns `Allies.SAME_FACTION` today. **AFTER-M7:** an adapter over
`DiplomacyView.allied(FactionSlot, FactionSlot)`; nothing else in M9 changes. Tests use `FakeAllies` (test
fixtures) with two factions allied. `enemy(a, b)` is `!allied(a, b)` for two player factions.

Used by: capture (allies never capture each other's buildings; allied points never count against the owner),
garrisons (an ally may garrison, BLD-10), gates (PATH-12), arrows (`TownArrows` keeps shooting enemies only).

### 2.2 The territory grid (TERR-01, 04 §10.6)

- **Package** `core/sim/territory`. Tiles of 8 × 8 blocks; the first map is 48 × 48 tiles. Paged by 16 × 16
  columns like the other grids (04 line 944), so a 1,024² map costs the same per tile.
- **Sources.** Every COMPLETE building whose template has `TerritoryInfluence`: `weight`, `radius` in blocks,
  `root`. Foundations give none. The radius passes through the stats (`blockValue`), so Town's +25 % on civic
  centres is a tech effect (`phase_town.json`).
- **Influence.** At a tile whose centre lies at distance d from the source's footprint centre: `weight × (r − d) / r`
  for d < r, else 0, in integer fixed point (d by an integer square root of squared distances in blocks). Each tile's
  owner is the faction with the highest summed influence above zero; a tie, or no influence, is neutral.
- **Connectivity.** A tile is connected when it is 4-connected through tiles of the same owner to a tile holding
  one of that owner's root sources. Unconnected tiles exist (they decay in TERR-04); M9 only shows them.
- **Data** (×0.4 of 0 A.D.'s radii, rounded to blocks): civic centre weight 65,535, radius 56, root; house 16;
  storehouse and farmstead 20; barracks, tower and arsenal 24; quarry and mines 12; fields none.
- **`TerritoryKernel`** (pure, `@HotPath`): stamps each source into per-faction accumulators over its bounding box
  only, then resolves owners and floods connectivity. `TerritoryReference` (tests) computes every tile against every
  source, the naive way; `TerritoryReferenceTest` compares the two on 500 random layouts (seeded, 1–4 factions,
  1–60 sources, maps 64² to 1,024²).

### 2.3 The territory system (TERR-01, 04 line 742)

- **`TerritorySystem`** in `WORLD`, after `VisionSystem`. It keeps the source set's version, bumped when a source
  completes, is destroyed, changes owner or changes radius (a stats version change for its template).
- **Cadence.** A recompute starts when the version moved and the last one started 20 or more ticks ago: at most one
  per 20 ticks (the per-tick cap). It runs as a `PureJob` on a frozen copy of the sources, delivered by the
  `JobExecutor`'s STRICT mode at the start tick + 1 (the path service's pattern), so a slow recompute never blocks a
  tick and 0, 1 and 4 threads give the same grid on the same tick. Inline (0 threads) it runs at delivery.
- **Readers** (placement, the AI, capture's future decay) read only the delivered grid: `Queries.territory(x, z)`
  → owner and connected.
- **Save, no hash** (§1.6 row 8). `Play.territory` saves the delivered grid's sources and a pending job's; a restore
  recomputes both at once, so the next tick delivers what it would have. The grid is derived and not hashed.
- **Tests.** `TerritoryThreadsTest` (0, 1, 4 threads; ADAPTIVE replay as `ThreadCountHashTest`),
  `TerritoryCadenceTest` (ten completions in one tick cause one recompute; a change 5 ticks after a start waits
  until tick 20).

### 2.4 The territory payload (TERR-01 4)

`strategos:territory` (S2C, the match's audience): `{matchId, tick, version, full, tiles[]}`. A full update at join
and after a restore: the grid run-length coded (owner + connected bit per tile). Otherwise a delta: changed tiles as
(index gap varint, value byte). The encoder splits an update larger than 7 KB into several payloads, sent on
consecutive ticks, the last one flagged; the client applies a version only when all its parts arrived.
`TerritoryCodecTest`: random churn on 384² and 1,024² maps stays under 8 KB per payload, a full checkerboard grid of
1,024² splits and reassembles exactly. The client's `ClientTerritory` (core `client`) holds the grid for placement
prediction, borders and the minimap.

### 2.5 Ownership transfer (CMB-10's foundation)

`BuildingStore.transfer(handle, faction)` and `BuildingLife.transfer(s, handle, to)`, the one way a building changes
owner, in this order: eject the garrison (§2.7); `Production.lost(s, handle)` for the old owner (housing dropped,
the queue refunded, the head's reservation returned); clear the rally point; change the faction; re-register the
building's vision and territory sources and its stats (the new owner's modifiers); add housing for the new owner;
emit a `NoticeKind` for both owners. Limits are checked only at placement: a captured second civic centre stays.
`BuildingStore` is at 798 lines: T1 moves its queue and rally code to `BuildingQueues` first.

### 2.6 Capture (CMB-10)

- **State.** `CaptureStore`: per capturable building, points per faction (int, in thousandths), lazily created at
  the first capture; a full owner's building has no entry. Saved in `Play.capture`.
- **Data.** `Capturable/points`: civic centre 2,000 (its Health), barracks and tower 800, houses and storehouses
  400, farmstead 300; fields, palisades, walls and gates have no `Capturable`. `Capturable/regen` per second: 5, 10
  for barracks and towers, 30 for the civic centre. `Capture/strength` per second: worker 1.0, infantry and archer
  2.5, horseman 1.75, ram 0. 0 A.D.'s ratios to Health and its worked examples (mechanics §1.12) carry over:
  20 infantry take an empty, full-health civic centre in 2,000 / (50 − 30) = 100 s.
- **Rate.** Each capturing unit in reach (melee reach of the building's footprint) adds its strength, divided by
  `0.1 + 0.9 × health / maxHealth`, every second (every 20 ticks, at the building's phase offset). Capture takes the
  points from the owner first; then from other non-allied factions in proportion. Regeneration (`regen` plus the
  garrison, §2.7) moves points back to the owner from the others, in faction order.
- **Ownership** changes only when the owner's points reach zero: to the faction holding the most points, ties to the
  lowest faction index (`CaptureOwnershipTest`: a 60 % majority never flips it). Then `BuildingLife.transfer`, and
  the new owner holds every point.
- **The order.** `Capture(UnitSet units, int building)`, wire id 32: units with `Capture/strength` > 0 walk into
  reach and capture; refused for allies' buildings, uncapturable templates and units without strength. UnitAi's new
  `CAPTURE` activity; when the target changes owner to the unit's faction or an ally, the unit goes idle.
- **Client.** The capture bar in `HudBuildingCard` and over the building in `HealthBarOverlay` (the owner's share and
  each other faction's, in `HudTheme` colours), from `BuildingState.Entry`'s capture bytes; the capture key
  (`key.strategos.capture`, C) turns a right-click on a building into `Capture`.

### 2.7 The garrison, finished (BLD-10)

- **Healing.** `GarrisonHolder/heal` HP per second for each occupant: civic centre 1, houses and towers 0 (0 A.D.:
  CC 1 HP/s). Applied every 20 ticks in `ECONOMY`, never above max.
- **Ejection.** When a building's health falls below `GarrisonHolder/ejectBelow` (0.1) of its max, `ejectAll`.
- **Capture regeneration.** Each occupant adds its `Capture/strength × Capturable/garrisonRegen` (1.0; Town +0.5 by
  `phase_town.json`) to the building's regeneration.
- **Rally into a building.** A rally point on a building of the owner or an ally with room garrisons the units
  trained there, via `Garrison` on arrival.
- **Data.** Houses: capacity 5, `Worker`; the sentry tower: 3, `Infantry`; the defence tower: 5, `Infantry`. Allies
  may garrison (`Garrisons.admits` asks `Allies`).

### 2.8 The sentry tower and upgrades (BLD-12)

- **Sentry tower** (`building/sentry_tower`), Village: 100 wood, 400 health, one arrow plus one per garrisoned unit,
  interval 40, range 24, vision 20, garrison 3, a 5 × 5 footprint like the defence tower.
- **Defence tower** (BLD-17's `tower`) gains `GarrisonHolder` (5, `Infantry`) and `perGarrisoned 1`.
- **Upgrade.** `Upgrade/to`, `Upgrade/Cost`, `Upgrade/time`, `Upgrade/requires`: the sentry tower upgrades to the
  defence tower in Town for 50 wood, 100 stone, 50 s. `UpgradeBuilding(int building)`, wire id 33, puts an upgrade
  entry at the head of the building's queue (training waits behind it; cancelling refunds it, as research). At its
  end the building's template changes in place: the health ratio is kept, the garrison stays (any occupants over the
  new capacity are ejected), the capture points are scaled by the new `points`. The mod replaces the blueprint's
  blocks through the construction placer, in the same footprint.
- **Blueprints.** `PlaceholderBlueprints.sentryTower()`; both towers in the construction and damage-stage suites.

### 2.9 Gates in navigation (PATH-12)

- **Gate overlay.** `GateOverlay` (core `sim/nav`): gate columns with their owner. In the base grid a gate column is
  blocked, so `Components` split at it; each gate links the components on its two sides.
- **Reachability per faction.** `Reach.reachable(from, to, faction)`: the same component, or connected through gates
  whose owner is allied with the faction (a BFS over the small gate graph, cached by the gate set's version).
- **Path search.** `GridAStar` takes the requester's faction: a gate column is passable at door cost for an allied
  faction, blocked otherwise. The request key gains the faction only when the gate set is non-empty, so paths
  without gates are shared as today.
- **Door state.** A gate is open while an allied unit stands within 3 blocks of it; the state is in the core (for
  the mod's blocks) and never lets an enemy through.

### 2.10 Palisades and gates (BLD-13)

- **Line.** `PlaceWall(TemplateId wall, int x1, int z1, int x2, int z2, List<Integer> gates, UnitSet builders)`, wire
  id 34. `WallLine` (pure): a straight line becomes segments of `Wall/segment` blocks (4) that follow the floor (each
  segment's base at its middle's floor; a step over 2 blocks between neighbours starts a new segment with its own
  base); `gates` replaces the named segments by `palisade_gate`. Every segment is a foundation with its own
  placement check (territory included); a refused segment leaves a gap, and the order's outcome lists the refused
  ones.
- **Data.** `palisade`: 1 × 4 blocks, 3 high, 300 health per segment, 14 wood, 8 s, Village, no `Capturable`;
  `palisade_gate`: 1 × 4, 500 health, 20 wood, 10 s, its middle two columns a gate (§2.9).
- **The mod.** The placement mode draws the line while the mouse is dragged and shows each segment's verdict; blocks
  are fence posts and spruce fences; the gate's middle is two fence gates, opened and shut from the core's door
  state.
- **Enemies at a palisade.** An enemy's move or attack-move whose goal is unreachable for its faction gets the path
  to the nearest reachable point; an attack-move then attacks the nearest blocking segment.

### 2.11 Borders (TERR-03)

- **Geometry, core.** `BorderGeometry` (`core/client/territory`, pure): from `ClientTerritory`, the edges between
  tiles of different owners as quads 0.05 blocks over the floor, per 16 × 16 × 16 section, coloured by the owner
  relative to the viewer (own, ally, enemy, neutral not drawn); unconnected tiles dashed. `BorderGeometryTest`: a test
  layout's geometry equals its stored hash; a changed tile dirties only the sections its edges touch.
  `BorderBench` (JMH, `@HotPath`): extracting one section, in `jmhCompare`.
- **The mod.** `BorderRenderer` subscribes to `AddSectionGeometryEvent` and adds a renderer holding an immutable
  copy of that section's quads (renderers run on chunk-build threads); a territory update calls
  `LevelRenderer#setSectionDirty` for the dirty sections only.
- **Minimap.** `MinimapImage` gains a border layer: tile edges in owner colours, rebuilt with the dirty tiles.

### 2.12 The AI (AI-08)

- **Placement.** `SiteFinder` asks `placementRefusal`, which now includes territory: no change but a test
  (`AiTerritoryPlacementTest`: 100 seeded matches, no AI placement refused for territory; the AI's second civic
  centre in Town goes to neutral land nearest its base).
- **Capture.** `MilitaryManager`: a target building is captured instead of burnt when it is capturable and the
  squad's capture rate, at the building's health, beats its regeneration plus garrison by 50 % (`AiCaptureTest`).
  The mop-up after a centre falls captures barracks and towers, burns the rest.
- **Defence.** `DefenceManager`: when a tower or a house is attacked and has room, nearby infantry (towers) or workers
  (houses) garrison it, once per 200 ticks (`AiGarrisonTowerTest`); the bell (AI-04) is unchanged.

### 2.13 The perf gate (PERF-16)

`perf_territory` (`PerfTests`): four AI factions of 150 population on the first map's arena copy, with a churn
driver that completes, destroys or transfers a building every 30 s and 40 garrisoned units, for 15 game minutes;
budgets A–C and E (ADR-0019, ADR-0026's validity); `perf_territory_stress` doubles the churn and the garrisons and
holds C p95 ≤ 50 ms. The report (`PerfVerdicts`) adds `systems.territory` (the `WORLD` phase's territory share and
the jobs' time) and `systems.garrison`. **AFTER-M7:** PERF-10's four-client pass of the same scene.

## 3. Build changes

None to the build itself. New JMH benchmark `BorderBench`; the perf batches `perf_territory` and
`perf_territory_stress` join `runGameTestPerf`.

## 4. Tests per level

| Level | New |
|---|---|
| L0 | ArchUnit: `sim.territory` imports no `client`; `Allies` is the only relation query in `sim` (no faction `==` in the new code) |
| L1 | `TerritoryReferenceTest`, `TerritoryThreadsTest`, `TerritoryCadenceTest`, `PlacementTerritoryTest`, `BorderGeometryTest`, `CaptureMathsTest`, `CaptureOwnershipTest`, `TransferTest`, `GarrisonHealTest`, `GarrisonEjectTest`, `CaptureRegenTest`, `TowerDataTest`, `UpgradeTest`, `GateReachabilityTest`, `WallLineTest`, `AiCaptureTest`, `AiTerritoryPlacementTest`, `AiGarrisonTowerTest`; the save round trip with territory and capture |
| L2 | `TerritoryCodecTest`; the new commands' and `BuildingState`'s caps |
| L3 | `territorytests`, `capturetests`, `garrisontests` (rally), `towertests`, `walltests`, `pathtests.own_gate_no_stop`; the tower and palisade blueprints in `constructiontests`; `aisoak` stays green with territory and capture |
| L4 | border goldens (vanilla, Sodium); PERF-14's border scene — consent |
| L6 | `BorderBench`; `perf_territory`, `perf_territory_stress` |

## 5. Measurements before building on them

- T2: the kernel's cost per full recompute on 384² with 60 sources and on 1,024² with 200 (a JMH line in the branch
  file); if one recompute passes 2 ms on the M4 Pro, the job stays off-thread by default, as designed.
- T6: whether Sodium calls `AddSectionGeometryEvent` renderers (S-check in the consent batch; the fallback is a
  translucent overlay drawn at `AFTER_TRANSLUCENT_BLOCKS`, recorded as a §1.6 row).

## 6. Risks and fallbacks

| Risk | Fallback |
|---|---|
| Sodium ignores `AddSectionGeometryEvent` | Draw borders in `RenderLevelStageEvent` from the same quads, culled by section |
| Territory recompute too slow on 1,024² | Incremental stamping of the changed sources only (the accumulators kept) |
| Gate links make reachability costly | The gate graph is per version and tiny (≤ 64 gates); measured in `GateReachabilityTest`'s timing line |
| The upgrade's block swap leaves debris | The construction placer clears the footprint first, as a rebuild does |
| M7's `DiplomacyView` lands with another shape | Only `Allies`' adapter changes (§2.1) |

## 7. Task breakdown

### 7.1 Order

T1 → (T2 → T3 → T4 → T5 → T6) ∥ (T10 → T11) ∥ (T12) → T7 → T8 → T9 → T13 → T14 → T15 → T16 → T17 → T18 → T19 →
T20. T10 needs T7 only for its regeneration criterion, which it finishes after T7.

### 7.2 Tasks

#### T1 · Health check moves (M6 follow-ups)
Split `MotionSystem` (910) and `PathService` (837) under 800 lines and move `BuildingStore`'s queue and rally code to
`BuildingQueues`, by moves only. Verify: `verify verifySlow`, the core's hash unchanged on the soak seeds. Size: 600
moved lines. (M6's other first-task follow-up, a fake `DemoStage`, is T17's: `DemoStage` is bound to a
`ServerLevel`, so the fake needs an interface over it, which the new chapters use first; §1.6 row 7.)

#### T2 · TERR-01 · The kernel and the reference
`Allies` (§2.1) and `FakeAllies`; `sim/territory`: `TerritoryGrid`, `TerritoryKernel`, `TerritoryReference`; the
`TerritoryInfluence` data and provenance. Verify: `TerritoryReferenceTest`. Size: 700.

#### T3 · TERR-01 · The system, the job and the save
`TerritorySystem`, its cadence, the `PureJob` and its delivery, `Queries.territory`, `Play.territory`, the hash.
Verify: `TerritoryThreadsTest`, `TerritoryCadenceTest`, the save round trip; `jmhCompare`. Size: 600.

#### T4 · TERR-01 · The payload and the client grid
`strategos:territory`, its codec and splitting, `ClientTerritory`, the mod's payload and audience. Verify:
`TerritoryCodecTest`, `PayloadRoundTripTest`. Size: 500.

#### T5 · TERR-02 · The build rule
`PlacementRules.Rule.TERRITORY` in place of `REACH`, `Placement/territory`, a second civic centre in Town (its limit by
`phase_town.json`), the client prediction, the refusal reason. Verify: `PlacementTerritoryTest`,
`territorytests.refused_outside`, the M4–M6 placement and AI suites. Size: 500.

#### T6 · TERR-03 · Borders
`BorderGeometry`, `BorderBench`, `BorderRenderer`, the minimap layer; the engine names of §0.4. Verify:
`BorderGeometryTest`, `jmhCompare`; the goldens wait for T19. Size: 700.

#### T7 · CMB-10 · Transfer and capture maths
`BuildingLife.transfer` (§2.5), `CaptureStore`, the rate and ownership rules, the `Capturable` and `Capture` data,
`Play.capture`. Verify: `TransferTest`, `CaptureMathsTest`, `CaptureOwnershipTest`. Size: 700.

#### T8 · CMB-10 · The capture order
`Capture` (32), its rule, UnitAi's `CAPTURE` activity, `NetGateway`'s size case. Verify: L1 order tests,
`CommandKindsTest`. Size: 500.

#### T9 · CMB-10 · Capture in the game
`BuildingState`'s capture bytes, the capture bar, the capture key, notices. Verify: `capturetests.*` (a captured
barracks trains for its new owner). Size: 500.

#### T10 · BLD-10 · The garrison finished
Healing, ejection, rally into a building, houses and towers by data, `Allies` in `admits`; capture regeneration after
T7. Verify: `GarrisonHealTest`, `GarrisonEjectTest`, `CaptureRegenTest`, `garrisontests.rally_garrisons`. Size: 500.

#### T11 · BLD-12 · The sentry tower and upgrades
The sentry tower, the defence tower's garrison, `UpgradeBuilding` (33), the in-place template change, the blueprint.
Verify: `TowerDataTest`, `UpgradeTest`, `towertests.upgrade_keeps_garrison`, the construction and damage-stage
suites. Size: 700.

#### T12 · PATH-12 · Gates in navigation
`GateOverlay`, the gate links, `Reach.reachable` per faction, `GridAStar`'s faction, the door state. Verify:
`GateReachabilityTest`, the path suites; `verifySlow` (map tests). Size: 600.

#### T13 · BLD-13 · Walls in the core
`WallLine`, `PlaceWall` (34), the palisade and gate templates and provenance, per-segment foundations. Verify:
`WallLineTest`, the placement tests. Size: 600.

#### T14 · BLD-13 · Walls in the game
The drag placement mode, the blueprints, the gate blocks and their states, enemies at a palisade. Verify:
`walltests.*`, `pathtests.own_gate_no_stop`. Size: 700.

#### T15 · AI-08 · The AI uses territory and capture
§2.12. Verify: `AiCaptureTest`, `AiTerritoryPlacementTest`, `AiGarrisonTowerTest`; `:core:openingBalance` (QA-12
still within its rows). Size: 500.

#### T16 · PERF-16 · The perf gate
`perf_territory`, `perf_territory_stress`, the report's costs. Verify: `tools/longrun.sh -- :mod:runGameTestPerf`
on a quiet machine. Size: 400.

#### T17 · The demo chapters
`territory`, `capture`, `garrison_heal`, `towers`, `palisade` (§1.3); an interface over `DemoStage` and a fake of
it, so each chapter's script runs at L1 (M6 row 72). Verify: `DemoCatalogTest`, the chapters' script tests. Size: 600.

#### T18 · The close's long runs
`verify verifySlow`; `verifySoak` (`tools/longrun.sh --shards 2`); `:core:jmh :core:jmhCompare`; the perf batch.
Size: records.

#### T19 · The consent batch
§1.7. Size: records.

#### T20 · The close
Results, the health check, the hand-in. Size: records.

### 7.3 Progress

- [x] T1 (2026-11-24) · [x] T2 (2026-11-24) · [x] T3 (2026-11-24) · [x] T4 (2026-11-24) · [x] T5 (2026-11-24) · [ ] T6 · [ ] T10 · [ ] T11 · [ ] T12 · [ ] T7
- [ ] T8 · [ ] T9 · [ ] T13 · [ ] T14 · [ ] T15 · [ ] T16 · [ ] T17 · [ ] T18 · [ ] T19 · [ ] T20

## 8. Acceptance checklist (M9 exit)

- [ ] Every acceptance criterion of the ten cards passes at its level (§1.4), except the AFTER-M7 items of §1.5.
- [ ] L1: territory equals the reference; identical with 0, 1 and 4 threads; ownership only at zero owner points.
- [ ] L3: a garrison round trip keeps every unit's state; gates open for allies only (with `FakeAllies` until M7).
- [ ] L6: `perf_territory` within A–C and E; territory never blocks a tick; the stress pass holds.
- [ ] Consent batch: border goldens, also under Sodium; PERF-14's border scene.
- [ ] Cumulative: `verify verifySlow verifySoak`, `jmhCompare`, the earlier milestones' gates.
- [ ] Owner: a playtest with the questionnaire; no P0 or P1 open.

## 9. Owner inputs

- The playtest (§1.3) after the hand-in.
- A proposal: an owner banner on buildings (§1.6 row 5).

## 10. Implementation results

Written at the close (T20).
