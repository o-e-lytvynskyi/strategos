# M9 on dev/o-e-lytvynskyi/m9

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m9 · **Work:** M9 · **Base:** main @ d39069e · **Claimed:** 2026-11-24 · **Status:** in progress

**Note:** the owner's word in the developer session, 2026-11-24: «надо начинать м9 … м7 и м8 заняты другим человеком, надо делать то, что не требует их участия», and the approval of all ten M9 cards («Одобряю все 10»). The claim covers the M9 spec and every M9 task; the parts that need M7 (MATCH-06, PERF-10) are built against a contract and finished after M7 merges.

## Now

- **Milestone:** M9 Territory, capture and defence, 10 of 20 tasks done; spec [M9](../milestones/M9-territory-capture-and-defence.md).
- **In progress:** none.
- **Last finished task:** T11 (2026-11-24).
- **Next task:** T6 · TERR-03 · Borders.
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

### 2026-11-24 — M9 spec, T1–T7, T10–T12

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
- 2026-11-24 · the sentry tower builds in 40 s with pierce 8 (the spec gave neither), carries no `Village` class (it does not count towards Town), and is wood like the house — undo: its template and provenance.
- 2026-11-24 · `BorderBench` in `hotPathKernels` (the 0 B/op gate) with no time baseline yet — a baseline is proposed with T18's quiet JMH run — undo: drop the token in core/build.gradle.
- 2026-11-24 · `Placement/territory` is a number (0, 1, 2), not a token, as every other placement value — undo: a token path and its parser.
- 2026-11-24 · territory is public (spec §1.6 row 9): one grid for every client — undo: per-faction audiences once FOW-05 (M8) asks.
- 2026-11-24 · the territory grid is not hashed (spec §1.6 row 8): it is a function of the saved source sets, and hashing it would change every journal's hashes — undo: mix `TerritoryGrid.hash()` in `StateHasher` once a grid exists.
- 2026-11-24 · right-click attacks a building, the capture key captures (spec §1.6 row 3), as 0 A.D. since Alpha 27 — undo: swap the default in the client's order mapping.
- 2026-11-24 · territory updates as deltas split above 7 KB (spec §1.6 row 4) — the card's 8 KB cannot hold a full 1,024² grid — undo: raise the cap, which is the owner's.

## Pitfalls

- None.

## Review
