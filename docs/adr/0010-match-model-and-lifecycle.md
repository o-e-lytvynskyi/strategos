# ADR-0010: A match is a region of a level; arenas live in an arena world

Date: 2026-10-21

## Status

Accepted; amended by [ADR-0023](0023-plan-alignment-after-roadmap-approval.md) (map-scale
GameTests place and release their own tickets, with a batch-end check). Spike S2 (2026-10-22)
confirmed the `ticking=false` tickets and the three sizes with numbers
([S2](../spikes/S2-force-loaded-arena.md)).

## Context

Gap G7: what a match is in world terms, and its lifecycle.
- **Owner decisions:** a bounded, pregenerated match arena first, RTS in an open world maybe later,
  solo against the AI first, then 2–4 players on a server.
- **Verified constraints:**
  - The vanilla world border is saved only for the overworld, and every other level follows it
    (fact 10), so it cannot bound several matches.
  - GameTests run up to 50 tests of a batch concurrently in one level at random origins (fact 26),
    so match state scoped to a level or to statics would collide (fact 27).
  - `TicketController.forceChunk(…, ticking=false)` makes forced chunks entity-ticking and keeps a
    player-less level ticking; `ticking=true` adds random ticks and natural spawning (facts 8, 9).
  - A datapack dimension makes every world "experimental": `WorldDimensions#bake` gives an
    experimental lifecycle unless the level stems are exactly the three vanilla ones, and only
    vanilla-like stems count as stable (WorldDimensions.java:121–185). The player then sees the
    experimental-settings confirmation and a backup warning (WorldOpenFlows.java:228–245, 383).
  - NeoForge 21.1 cannot create dimensions at runtime without a library (ADR-0004).
  - `StructureTemplate#placeInWorld` places a whole template per call, so it cannot honour a
    per-tick budget (StructureTemplate.java:226).

## Decision

- **A match is a region of one `ServerLevel`.**
  - Its `MatchSpace` is a level, an origin, a size and a Y band. The core works in match-local
    coordinates (`double` X and Z, absolute Y).
  - Any number of matches can share a level. `MatchRegionIndex` routes chunks to matches, and
    bodies are never paired with members of another match.
  - All state is per match: no statics, no level-scoped match state.
- **GameTests** bind a match to `GameTestHelper#getBounds()`. Fifty tests are fifty matches.
- **Production arenas live in an arena world.**
  - **The preset.** A world preset `strategos:arena`, listed in `#minecraft:normal`, gives a void
    flat overworld with dimension type `minecraft:overworld`, a `strategos:arena` biome (no spawns,
    no precipitation, no features) and the vanilla nether and end. The lifecycle stays stable.
    Dedicated servers use `level-type=strategos:arena`.
  - **World rules.** On first load, `ArenaWorld` applies the controlled-arena gamerules once and
    records that it did:
    - no mob spawning, fire tick, mob griefing or weather;
    - fixed daytime;
    - random ticks 0;
    - `spectatorsGenerateChunks` false;
    - a lobby pad at the world spawn.

    These are the D-13 recommendation; "Minecraft twists" can become match rules later.
  - **Slots.** Matches take slots 2,048 blocks apart; v1 takes a fresh slot per match.
- **Maps.** Map packages are a manifest plus tiles of vanilla structure NBT. `/strategos map
  export` writes them with `StructureTemplate#fillFromWorld`. Our `MapImporter` places them under a
  budget: 8,192 blocks per tick while no match in the level is running, otherwise a low-priority
  share of the block writer. The `generated` source uses a region of any existing world as it is,
  for development and later the open world.
- **Loading.** Every arena chunk is force-loaded with `TicketController#forceChunk(level,
  matchUuid, x, z, true, false)`. A `LoadingValidationCallback` drops tickets of dead matches.
  GameTest matches place no tickets.
- **Sizes.** 256² for tests, 384² by default (D-17), 512² after spike S2 and PERF-12. The Y band is
  at most 128.
- **Lifecycle.** `CREATED → PREPARING → READY → RUNNING ⇄ PAUSED → ENDED → DISPOSED`, with
  `FAULTED` from any running state. Prepare imports the map, builds every navigation page,
  discovers nodes and creates the start entities, under budget and with a progress bar.
- **`ArenaPolicy`** holds every arena-only assumption (force-load all, prewarm all, a body for
  every unit, adventure first person, whole-region protection, camera clamp). `OpenWorldPolicy`
  replaces it in M13. Outside arena worlds, no world gamerule is ever changed.

## Consequences

- Parallel GameTests, multi-match servers and the open world share one model.
- Players create a dedicated "Strategos Arena" world for skirmishes. Playing inside their own
  survival world waits for the open-world mode (risk R17).
- Retired slots stay on disk until `/strategos arena prune` (deferred). Same-slot rematches wait
  for the reset journal (deferred).
- Map packages are versioned data with terrain hashes, and two imports give identical terrain.
- Spike S2 prices force-loading 256, 576 and 1,024 chunks and the import time, and sets the final
  sizes.

## Alternatives considered

| Alternative | Why not |
|---|---|
| A datapack dimension `strategos:arena` with slots (B) | The experimental-world warning in every world with the mod (verified above) |
| One match per world, bounded by the vanilla border (RoN) | No parallel GameTests, no multi-match servers; the border is shared by all levels |
| Shipped world saves copied per match (C) | Cannot be placed inside GameTests; a world copy per match. Kept as a deferred `world` source for very large authored maps |
| A runtime dimension per match | No NeoForge 21.1 API; would need a runtime library |
| Matches in the player's own survival world from the start | Shared gamerules and unbounded terrain; this is the later open-world mode |
| `ticking=true` tickets | Random ticks and natural mob spawning across the whole unwatched arena |
