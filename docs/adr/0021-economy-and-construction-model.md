# ADR-0021: Hybrid world economy: real-block nodes, finite deposits and fields, abstract building HP

Date: 2026-10-21

## Status

Accepted; the leaf rule (leaves only at exhaustion) superseded by [ADR-0024](0024-canopy-thinning-by-the-vanilla-distance-rule.md)
on 2026-10-23

## Context

- **The owner decided a hybrid world** on 2026-10-20 ([research/README.md §1](../research/README.md)):
  - trees and stone are real world blocks that workers cut and mine;
  - mines, quarries and fields are deposit objects with a remaining amount;
  - buildings rise block by block from blueprints;
  - building HP is abstract: blocks fall away at damage thresholds and return on repair.
- **All three proposals made fields infinite,** following the feasibility study. All three
  judgements flagged that as contradicting the owner's decision.
- **Block writes are costly.** Every write costs lighting, neighbour updates, a client packet
  (about 4 bytes per tracking client) and a render-section re-mesh on every client. Fire removes
  blocks without any event (fact 25).
- **Vanilla templates don't fit.** `StructureTemplate` palettes are private, and `placeInWorld`
  places a whole template per call (StructureTemplate.java:66, 226).

## Decision

- **Every resource source is a core node.**
  - **Trees** (real logs and leaves): logs are removed top-down per gathered quota, and leaves when
    the tree is exhausted.
  - **Stone outcrops** (real blocks): removed top-down and outside-in, never below the surrounding
    floor.
  - **Deposits** (mines, quarries): blocks placed by the map, an abstract remaining amount,
    gatherer slots, visual stages at amount thresholds.
  - **Fields:** a finite amount, 5 gatherer slots, diminishing returns of 0.9 per extra gatherer,
    and a reseed rule in data (manual, automatic at a cost, or free regrowth; the choice is the
    owner's D-14, and the recommendation is automatic reseed for wood toggled at the farmstead).
  - **Forage** (berry bushes): an amount and a regrowth rule.
- **Discovery.** Nodes are discovered at prepare by flood fill over a material channel from the
  `strategos:resource_yield` data map.
- **Resources and the treasury.** Resource types are data. The treasury is per faction and fixed
  point, and conservation is a harness invariant.
- **Buildings are core entities,** and their blocks are the picture.
  - **Blueprints** are vanilla structure `.nbt` files parsed by our own loader into a core
    `BlueprintShape`: footprint, build order, damage ranks, markers.
  - **Placement** runs the same `PlacementRules` code on the client (ghost) and the server
    (authoritative).
  - **Progress.** Builders add `rate × n^0.7`, and the core asks for `floor(progress × blocks)` to
    exist.
  - **HP** lives in the core. Damage thresholds (85, 65 and 35 % by default) remove the blocks
    ranked below each stage, deterministically and respecting supports. Repair 2 % above a
    threshold restores them.
  - **No passages.** Damage never removes the two lowest layers of a footprint's outer ring, so no
    passage opens for first-person players.
  - **Destruction** at 0 HP removes the rest under budget and clears the footprint.
  - **Repair cost** is a rule (D-15).
- **One block writer** (rule B7) does every block write, with flags `UPDATE_CLIENTS |
  UPDATE_KNOWN_SHAPE`, under one global budget of 64 placements per tick across matches.
  - Sites are served round-robin, and each flushes in bursts at most every 4 ticks.
  - Priority: node removals, construction, damage stages, cosmetics.
  - Writes carry an `OWN_WRITE` flag. The core updates its own navigation overlays when it changes
    a building or node, so these edits are never observed back.
- **Protection.**
  - Protection events cancel or convert player breaking and placing, explosions, mob griefing,
    pistons, fluids and crop growth inside match regions.
  - In arena worlds, `doFireTick` is false and blueprints avoid flammable blocks.
  - The `LevelChunk#setBlockState` backstop restores any protected block changed without the
    writer's flag.

## Consequences

- The world looks and behaves like Minecraft (trees fall log by log, buildings rise) while
  balance, HP and navigation stay deterministic core state.
- If the writer lags, only the picture is late; progress and HP never wait for blocks.
- Construction and damage cost no navigation work.
- External edits shrink nodes and credit nothing unless a rule says so.
- Fields have meaning as an economic decision (the reseed rule) instead of being free forever.
- The building HP model is fixed for the MVP. How buildings fall (torches and a ram, D-12) is
  content.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Infinite fields (all three proposals, feasibility §6.2) | Contradicts the owner's decision |
| Real block destruction as building HP (RoN-style: health = blocks placed) | Navigation rebuild storms, griefing, no deterministic repair; rejected by the owner's decision |
| Item logistics instead of resource counters (MineColonies) | Slow, buggy and opaque at RTS scale; a treasury is the RTS model |
| Abstract trees and stone too | Contradicts the owner's decision for real trees and stone |
| 256 placements per tick (A) | 4× the server work, bandwidth and client re-meshing of 64 |
| `StructureTemplate#placeInWorld` for construction and maps | Places a whole template per call; it cannot honour a budget |
