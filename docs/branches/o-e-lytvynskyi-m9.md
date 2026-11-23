# M9 on dev/o-e-lytvynskyi/m9

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m9 · **Work:** M9 · **Base:** main @ d39069e · **Claimed:** 2026-11-24 · **Status:** in progress

**Note:** the owner's word in the developer session, 2026-11-24: «надо начинать м9 … м7 и м8 заняты другим человеком, надо делать то, что не требует их участия», and the approval of all ten M9 cards («Одобряю все 10»). The claim covers the M9 spec and every M9 task; the parts that need M7 (MATCH-06, PERF-10) are built against a contract and finished after M7 merges.

## Now

- **Milestone:** M9 Territory, capture and defence, 3 of 20 tasks done; spec [M9](../milestones/M9-territory-capture-and-defence.md).
- **In progress:** none.
- **Last finished task:** T3 (2026-11-24).
- **Next task:** T4 · TERR-01 · The payload and the client grid (the core's codec is in; the mod's payload next).
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

### 2026-11-24 — M9 spec, T1–T3

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
- 2026-11-24 · the territory grid is not hashed (spec §1.6 row 8): it is a function of the saved source sets, and hashing it would change every journal's hashes — undo: mix `TerritoryGrid.hash()` in `StateHasher` once a grid exists.
- 2026-11-24 · right-click attacks a building, the capture key captures (spec §1.6 row 3), as 0 A.D. since Alpha 27 — undo: swap the default in the client's order mapping.
- 2026-11-24 · territory updates as deltas split above 7 KB (spec §1.6 row 4) — the card's 8 KB cannot hold a full 1,024² grid — undo: raise the cap, which is the owner's.

## Pitfalls

- None.

## Review
