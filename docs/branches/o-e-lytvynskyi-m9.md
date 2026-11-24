# M9 on dev/o-e-lytvynskyi/m9

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m9 · **Work:** M9 · **Base:** main @ d39069e · **Claimed:** 2026-11-24 · **Status:** in progress

**Note:** the owner's word in the developer session, 2026-11-24: «надо начинать м9 … м7 и м8 заняты другим человеком, надо делать то, что не требует их участия», and the approval of all ten M9 cards («Одобряю все 10»). The claim covers the M9 spec and every M9 task; the parts that need M7 (MATCH-06, PERF-10) are built against a contract and finished after M7 merges.

## Now

- **Milestone:** M9 Territory, capture and defence, 17 of 20 tasks done; spec [M9](../milestones/M9-territory-capture-and-defence.md).
- **In progress:** none.
- **Last finished task:** T17 (2026-11-24).
- **Next task:** T18 · the close's long runs (the AI soak again after the `BreachLog` fix; then the perf batch and JMH alone); T19 · the consent batch.
- **Merged from main last:** 05cd50b (2026-11-24).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0). Every other dependency of the M9 cards
(BLD-02, BLD-04, BLD-08, BLD-11, BLD-16, BLD-17, CMB-03, CORE-02, PATH-04, AI-02, AI-05, UI-20) is
`done` or `accepted` on `main`. M8 (fog) is not needed.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M7 | MATCH-06 teams and diplomacy: who is an ally for capture (CMB-10), allied gates (PATH-12, BLD-13) | against a contract: `Allies` (spec §2.1), the same faction today, `FakeAllies` in the tests; an adapter over M7's `DiplomacyView.allied` | `AFTER-M7` at `Allies` and at the allied GameTests (spec §1.5) |
| M7 | PERF-10 multiplayer perf gate: PERF-16's final run builds on it | after, for its multiplayer pass only: PERF-16's one-server scene with four AI factions first (spec §2.13) | `AFTER-M7` at `perf_territory` |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-11-24 — M9 spec, T1–T18

- **M9 T18 · the AI soak's replay divergence** — the unsharded soak (worktree, 2 h 13 min) ran 12 seeds green (9 by conquest at 28.7–38.0 min, seeds 1, 3, 7 to the limit) and failed on seed 13: its replay diverged at tick 31220, 20 ticks after the journal's start at 31200 (the ring had rolled, so the replay began from a mid-match snapshot). A diagnostic run of seed 13 alone (a throwaway worktree, per-component snapshot text at each checkpoint) reproduced it and showed an archer's place and yaw differing by 0.005 blocks and 1.2°. On the way, `BreachLog` (the wall-breach cooldowns) turned out neither saved nor hashed, which diverges any mid-match replay after a failed breach. Fixed: `Play.breaches` (codec field optional, older saves read empty), restored and mixed into the hash while non-empty; `GateMovementTest.aBreachCooldownSurvivesASaveSoTheRestoredMatchWalksTheSame` fails without the restore and passes with it; kb `save-reload-identities.md` gains the pitfall. Seed 13 alone still diverged with that fix; compared per tick and per unit, it diverged on the first tick after the journal start (31201): an archer's avoidance deflected round an infantryman in the live run and not in the replay. The unit grid is rebuilt where MOVEMENT ends, so a unit set down after it (put out of a gate, out of a building) leaves the live grid behind the poses for the next tick — `gridMatches` false, the wide scan — while the restored match builds it from the poses. Fixed: `MatchCoreImpl.step` rebuilds the grid before the first phase, so every tick starts from the grid a restore builds (one more O(units) rebuild a tick; PERF-16's batch measures it). `MidTickMoveRestoreTest` sets a unit down beside a walker outside MOVEMENT, restores and steps both: it failed on tick 1 before the fix and passes after; `:core:test` green, 1 min 57 s; `verify verifySlow` green, 5 min 14 s; seed 13 alone (the diagnostic worktree, both fixes) now conquers at tick 42873 and its journal replays to its hash.

- **M9 T17 · the palisade chapter** — `PalisadeChapter` (a wall with a gate across a gap, own units through the gate, raiders at the wall), the m9 group's five chapters; `DemoScriptsTest` runs it in the fake (9 segments with the gate complete and registered); the fake has no terrain, so whether raiders stop at the wall is for the window (T19); `verify` green, 2 min 31 s (worktree).

- **M9 T13, T14 · review fixes** — BLD-13 and PATH-12 done; a unit restored inside a gate takes its floor from y and finishes its crossing (its test failed before the fix); an enemy in a gate when it completes or changes hands is put out; a ram never enters a 2-column gate; the breach only when the goal is unreachable, on the goal's side, with a 200-tick cooldown (`BreachLog`, transient); `WallLine` counts in long, `PlaceWall` refuses fewer than 1 or more than 64 segments and a gate past 64 with LIMIT; `GateDoorBlocks` prunes and retries; the selection box hidden only in wall mode; style guide §4 gains the Wall row; spec §1.6 row 22; the once-seen `constructiontests.field_is_built_and_equals_its_blueprint` failure did not recur (3 runs of the suite, 1 full run, 268 GameTests); `verify verifySlow` green, 4 min 49 s (worktree), `verify` 2 min 24 s merged.

- **M9 T17 (part) · the demo chapters** — `Stage`, an interface over `DemoStage` (helpers as default methods, so the fake runs the same code), `FakeStage` on a core harness; chapters `territory`, `capture`, `garrison_heal`, `towers` (group `m9`); `DemoScriptsTest` (6: each new chapter to its end, `tower` and `garrison` as the fake's guard), `DemoCatalogTest` (8); the fake caught a capture chapter with no housing and villages placed off the map before any window; `verify` green, 1 min 21 s merged. The `palisade` chapter follows the walls' fixes; windows wait for T19.

- **M9 T13, T14 · BLD-13, PATH-12 · Palisades and gates** — BLD-13 and PATH-12 in progress until the review's fixes; `WallLine`, `PlaceWall` (wire 34), `palisade` and `palisade_gate` (a completed gate registers its middle columns), movement through allied gates without a stop (eviction exempt, `LineOfWalk` and group jobs take the pass), the breach (an attack-move that ends short attacks a wall), door blocks from `GateDoors`, the client's drag mode; `WallLineTest` (7), `PlaceWallTest` (10), `WallGateTest` (8), `GateMovementTest` (9), `ClientWallTest` (5), `walltests.segments_match_layout`, `walltests.gate_allies_only` (allies at L1, AFTER-M7 in the GameTest), `walltests.enemies_around_or_through`, `pathtests.own_gate_no_stop`; spec §1.6 rows 20–21; `verify verifySlow` green, 5 min 20 s merged, 268 GameTests. The reviewer found a unit restored mid-gate stuck (its floor not restored) and missing tests; fixes follow.

- **M9 T16 · PERF-16 · The perf gate's scene** — PERF-16 in progress (the measured run on a quiet machine, T18; the four-client pass AFTER-M7); `TerritoryScene` (four Hard AI factions of 150 on MAP-02, churn every 30 s, 40 garrisoned; stress: every 15 s, 80), `perf_territory` and `perf_territory_stress` with A–C and E, C p95 ≤ 50 ms under stress, the territory join p99 ≤ 0.5 ms and one recompute per 20 ticks at most; `SystemTimes` (territory run and join, garrison, capture) and the report's `systems.territory` and `systems.garrison`; `TerritorySceneTest` (3), `SystemTimesTest` (2); a 2-minute trial ran both batches in 6 min 15 s, both `invalid environment` (foreign load 32 % and 9 %), territory run p99 99 µs, join max 6.8 µs, 13 recomputes; `verify` green, 2 min 51 s merged. The full run: `tools/longrun.sh -- :mod:runGameTestPerf -Pperf.filter=territory` on a quiet machine.

- **M9 T15 · AI-08 · The AI uses territory and capture** — AI-08 and TERR-02 done; `Seize` (capture when the squad's effect beats 1.5 × the regeneration, burn otherwise or after 90 s), waves and the mop-up capture barracks and towers, `DefenceManager` garrisons attacked towers and houses (once per 200 ticks), a second centre on neutral land in Town; read-only queries on `MatchQueries`/`AiView`; `AiMemory.SIZE` 34; `AiCaptureTest` (3), `AiGarrisonTowerTest` (3), `AiTerritoryPlacementTest` (2); `:core:openingBalance` every criterion holds, 10 min 36 s (rush–boom 88/200, normal–easy 176/200, hard–normal 135/200, Town 11:30–11:31); `verify` green, 2 min 36 s merged.

- **M9 T12 · review fixes** — the reviewer's eight findings on 46e8a0f0: `FootprintOverlay` counts per column (a gate over a footprint stays blocked until both go), `GridAStar` tests `closed` before `step` again, gates validated before anything is blocked (owner, bounds, repeats, at most 64), crossings only between opposite sides, the AFTER-M7 note at `GateOverlay.touch()`, the restored hash asserted, `GateThreadCountHashTest` (0, 1, 4 threads), spec §1.6 row 18 for the API; `FootprintOverlayTest`, `GateValidationTest`; `CrossingBench` cross384 0.52 ms, flat384 4.81 ms (no earlier numbers); `verify verifySlow` green, 4 min 48 s (in a worktree), `verify` 2 min 14 s merged. A unit does not walk through a gate yet: the movement side is T14's.

- **M9 T8, T9 · CMB-10 · The capture order and capture in the game** — CMB-10 done; `Capture` (wire 32), `CaptureRule`, the order kind and UnitAi's CAPTURE states (walk, then capture within reach); `BuildingState.Entry`'s capture shares (a byte a faction, only while captured); the capture bar in the building card; the capture key N (spec §1.6 row 17); `CaptureOrderTest` (20 infantry take an undefended barracks in 17–32 s and go idle), `CaptureTransitionRows` (~60), `CaptureShareTest`, `CaptureBarTest`, `capturetests.captured_barracks_trains`; merged with T11's `UpgradeBuilding` (33); `verify` green, 2 min 5 s.

- **M9 T11 · BLD-12 · The sentry tower and upgrades** — BLD-12 done; `building/sentry_tower` (Village, 100 wood, 400 health, 1 + garrison arrows, garrison 3, its blueprint 5×7×5); `UpgradeBuilding` (wire 33) puts an upgrade at the head of the queue, `Upgrades.apply` swaps the template in place (health ratio, garrison, capture points kept; the blueprint rewritten); `TowerDataTest` (3), `UpgradeTest` (6), `towertests.upgrade_keeps_garrison`, the sentry tower through the construction and damage-stage GameTests; `verify` green, 1 min 21 s (in a worktree). The kb entry on shared writer budgets gains its M9 case.

- **M9 T12 · PATH-12 · Gates in navigation** — PATH-12 in progress (acceptance 2 with T14's palisades); `GateOverlay` (gates blocked in the base grid, saved and hashed only when present), `GateLinks` (crossings beside a gate at door cost), `Reach` per faction through allied gates, `GridAStar` and `PathJob` take the requester's pass, `GateDoors.open` (an allied unit within 3 blocks); `GateReachabilityTest` (7), `GateStateTest` (2); 63 gates: links 1.9 ms, reach 0.02 ms, a 7-wall path 4.6 ms; with no gates paths and hashes are unchanged; `verify verifySlow` green, 5 min 12 s (in a worktree). `verifySoak` for the movement change runs with T18's long runs.

- **M9 T7 · CMB-10 · Transfer and capture maths** — CMB-10 in progress, BLD-10 done; `BuildingLife.transfer` (eject, refund, unhouse, rally cleared, rehouse; vision, territory and stats follow); `CaptureMaths` (effect ÷ (0.1 + 0.9 × health), owner first, ownership only at zero, ties to the lowest index, allies never), `CaptureStore` (hashed and saved only while a capture runs; `Play.capture`), `CaptureSystem` in COMBAT once a second per building with the garrison's regeneration; data: `Capturable` on seven buildings, `Capture/strength` on four units, Town +0.5 garrison regeneration; `CaptureMathsTest` (6: 20 infantry take an empty centre in 98–100 s, 12 make no progress at full health and take it at half in 80–83 s), `TransferTest`, `CaptureRegenTest` (2).

- **M9 T6 · TERR-03 · Borders** — TERR-03 in progress (goldens and PERF-14's border scene in T19's consent batch); `BorderGeometry` (`@HotPath`): strips inside each owned tile on a differing edge, own/ally/enemy colours, dashed when unconnected, wound counter-clockwise from above; `BorderRenderer` on `AddSectionGeometryEvent` with immutable copies, `setSectionDirty` over the match's band; the minimap's 1-pixel owner outline; `BorderGeometryTest` (7, its hash pinned), `MinimapBordersTest` (3); `BorderBench.extractSection` 0.08–0.17 µs/op, 0 B/op, in `hotPathKernels`; engine names in spec §0.4; `verify` green, 1 min 21 s (in a worktree).

- **M9 T10 · BLD-10 · The garrison finished** — BLD-10 in progress (criterion 3, capture regeneration, comes with T7's capture system); `GarrisonSystem` in ECONOMY: healing (`GarrisonHolder/heal`, the centre 1 HP/s) and ejection below `ejectBelow` (0.1); a rally point on a building garrisons the trained unit; houses hold 5 workers, the defence tower 5 infantry with one more arrow each; allies garrison (`Allies` in `admits`, `GarrisonRule`, restore); `GarrisonHealTest` (2), `GarrisonEjectTest`, `GarrisonRallyTest` (4), the ally test with `FakeAllies`, `garrisontests.rally_garrisons`; `verify` green, 1 min 18 s, 260 GameTests (in a worktree).

- **M9 T5 · TERR-02 · The build rule** — TERR-02 in progress (criterion 3 waits for T15's AI test); `PlacementRules.Rule.TERRITORY`, a column rule in place of BLD-02's `REACH`; `Placement/territory` 1 (own) on every building, 2 (own or neutral) on the civic centre; Town's `Limit/count +1` on civic centres; `RejectReason.OUTSIDE_TERRITORY` (wire 19; `OUT_OF_REACH` kept unused); the client's prediction reads the sent grid; `PlacementTerritoryTest` (2), `PlacementRulesTest` rewritten, `territorytests.refused_outside`; the first land immediate (spec §1.6 row 10); `verify` green, 1 min 20 s, 259 GameTests.

- **M9 T4 · TERR-01 · The payload and the client grid** — `strategos:territory` (S2C, public, cap 8 KB): full runs at join, deltas after, a version split into parts above 7 KB, one part a tick per player (`TerritoryFeed`); `ClientTerritory` in `ClientMatchModel`; `TerritoryCodecTest` (40 versions of churn on 384² and 1,024², a full 128² checkerboard in parts), `TerritoryFeedTest` (4), the payload's cap pinned; the 04 payload table and inventory; `verify` green, 1 min 19 s. Commits 76a9c5b and b691c18 do not build (the moved grid's edits were left unstaged); 4e6dcca fixes them.

- **M9 T3 · TERR-01 · The system, the job and the save** — `TerritorySystem` in `WORLD` after vision: sources gathered on a building or stats change, a recompute at most once per 20 ticks as a `PureJob` joined on the next tick, the first grid at once; `Play.territory` (the delivered and pending source sets; SchemaVersion stays 3); `TerritoryGrid` moved to `core.shared` (rule B3 keeps the client out of `sim`); `MatchCore.territory()`; `TerritorySystemTest`: the same grid every tick on 0, 1 and 4 threads, the cadence, a save mid-recompute restores the same future; `verify` green, 1 min 43 s. **`jmhCompare` invalid:** 20 of 21 benchmarks over ADR-0026's 7.5 % foreign load (7.6–9.9 %, the machine in use); the one valid comparison, `SpatialBench.rebuild1000` 4.77 → 5.90 µs (+24 %), measures code M9 has not touched, so it is put down to the same load until a quiet rerun (T18, `tools/longrun.sh`).

- **M9 T2 · TERR-01 · The kernel and the reference** — TERR-01 in progress; `Allies` (the M7 seam, `MatchState.allies()`), `FakeAllies`; `TerritoryGrid`, `TerritorySources`, `TerritoryKernel` (`@HotPath`, integer fixed point, stamping by bounding box); `TerritoryReference` equals the kernel on 500 random layouts (64² to 1,024², 1–4 factions, 1–60 sources); `TerritoryInfluence` on eleven buildings with provenance, the balance sheet regenerated, the template set's two pins rebaselined; `verify` green, 1 min 54 s.
- **M9 T1 · Health check moves** — `MotionSystem` 910 → 658 (`MotionMoves`), `PathService` 837 → 752 (`PathRoutes`), `BuildingStore` 798 → 576 (`BuildingQueues`, `buildings.queues()`); moves only, hash and save formats unchanged; `verify verifySlow` green, 4 min 58 s (in a worktree).

- **M9 spec** — the ten cards approved by the owner and `specified`; 20 tasks, T1 the M6 health check's moves; six §1.6 rows (relations, ownership transfer, the capture key, the payload split, building colour, scale); M7 met by the `Allies` contract.
- **Next:** T1.

## For the owner

1. **Proposal: an owner banner on buildings.** Buildings show their owner only on the minimap and the capture bar (spec §1.6 row 5); a captured barracks looks the same in the world. A banner block in the owner's colour on every blueprint would add scope (a card). Nothing is blocked.

## Decisions taken

- 2026-11-24 · M9's numbers at ×0.4 of 0 A.D.'s distances and its capture ratios to Health (spec §1.6 row 6) — the first map is 384 blocks against ~1,000 m — undo: new provenance notes and `:core:balanceSheet`.
- 2026-11-24 · trial implementer: pass — T1's three moves in a worktree at the branch head, first try, gate green.
- 2026-11-24 · trial implementer: pass — T4's mod payload in a worktree, first try; it found the 04 §2.5 inventory line the brief missed.
- 2026-11-24 · trial implementer: pass — T10 in a worktree; it widened the command rule for allied garrisons beyond the brief, recorded as spec §1.6 row 11.
- 2026-11-24 · trial implementer: pass — T6 in a worktree; it found the dirty-section count wrong in the brief (spec §1.6 row 12) and the off-grid match origin.
- 2026-11-24 · `BreachLog`, a transient per-match store for the breach cooldown (not saved: a restored match may retry a segment early) — undo: drop it and the cooldown.
- 2026-11-24 · trial reviewer: pass — the walls' review found a unit restored mid-gate stuck for good and the missing enemy, ram and cooldown tests.
- 2026-11-24 · trial implementer: pass ×3 — T8+T9, T11 and T12 in three worktrees side by side; the smoke runs' fixed port made each rerun once. trial reviewer: pass — T12's review found a major bug (a gate over a footprint unblocked by one release), now being fixed.
- 2026-11-24 · trial implementer: pass — T15; `Seize` (a class the brief did not name) and a public `CaptureSystem.regen` accepted as the cleanest shape.
- 2026-11-24 · the scene's ownership churn is a demolish and a spawn for the next faction (no admin transfer command): the territory sees the same source change, and `TransferTest` covers the transfer itself — undo: an admin `Transfer` command.
- 2026-11-24 · the sentry tower builds in 40 s with pierce 8 (the spec gave neither), carries no `Village` class (it does not count towards Town), and is wood like the house — undo: its template and provenance.
- 2026-11-24 · `BorderBench` in `hotPathKernels` (the 0 B/op gate) with no time baseline yet — a baseline is proposed with T18's quiet JMH run — undo: drop the token in core/build.gradle.
- 2026-11-24 · `Placement/territory` is a number (0, 1, 2), not a token, as every other placement value — undo: a token path and its parser.
- 2026-11-24 · territory is public (spec §1.6 row 9): one grid for every client — undo: per-faction audiences once FOW-05 (M8) asks.
- 2026-11-24 · the territory grid is not hashed (spec §1.6 row 8): it is a function of the saved source sets, and hashing it would change every journal's hashes — undo: mix `TerritoryGrid.hash()` in `StateHasher` once a grid exists.
- 2026-11-24 · right-click attacks a building, the capture key captures (spec §1.6 row 3), as 0 A.D. since Alpha 27 — undo: swap the default in the client's order mapping.
- 2026-11-24 · territory updates as deltas split above 7 KB (spec §1.6 row 4) — the card's 8 KB cannot hold a full 1,024² grid — undo: raise the cap, which is the owner's.

## Pitfalls

- docs/kb/save-reload-identities.md — "Not saved, changes nothing else" is a replay divergence (M9 T18, the AI soak's seed 13).

## Review
