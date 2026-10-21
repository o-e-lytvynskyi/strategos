# ADR-0016: One fog-aware sender, vanilla tracking behind a gate, targeted re-tracking

Date: 2026-10-21

## Status

Accepted; amended by [ADR-0023](0023-plan-alignment-after-roadmap-approval.md) (solo-grade fog in
M6 under D-24 (c), team fog in M8; D-08's observer avatar adds no vision)

## Context

- **Networking is server-authoritative from day one,** and hidden information must never leave the
  server (design rule 5). RoN bolted fog onto 135 broadcast call sites and kept fogged units paired
  by cancelling `updatePlayer` ([research/prior-art.md §3.8](../research/prior-art.md)).
- **Research contradiction 6** asks how often fog is re-evaluated and what leaks the entity filter
  misses. Verified in the 1.21.1 sources:
  - **The pairing test.** `TrackedEntity#updatePlayer` pairs a body with a player only if it is in
    range, `broadcastToPlayer` is true and the chunk is tracked (ChunkMap.java:1327–1345).
    `isChunkTracked` is false while a chunk waits to be sent (245–248).
  - **When vanilla re-checks.** Only on a section change, on add, and on each of the viewer's
    movement packets (`ChunkMap#move`, 1024). A still player sends a reminder only every 20 ticks
    (LocalPlayer.java:283–284).
  - **Access.** `entityMap` is private and `TrackedEntity` package-private (ChunkMap.java:150,
    1278); `updatePlayer` and `ServerChunkCache#chunkMap` are public.
  - **Chunk arrival.** `PlayerChunkSender` removes a chunk from the pending set before it posts
    `ChunkWatchEvent.Sent` (PlayerChunkSender.java:105–107, 82).
  - **Spectators.** Vanilla shows a spectator to every other spectator whose camera is itself
    (ServerPlayer.java:957–962), so enemy commanders would see each other.
  - **Sounds.** A server-side `Entity#playSound` broadcasts by distance regardless of fog
    (Entity.java:1138; ServerLevel.java:989–1035).
  - **Forced updates.** A dirty `SynchedEntityData` value forces a position update
    (ServerEntity.java:121).
  - **Payload size.** Vanilla's 32,767-byte serverbound cap applies only to unknown payloads
    (ServerboundCustomPayloadPacket.java:16).
  - **Singleplayer encodes too.** The in-memory connection still encodes packets
    (Connection.java:549–557).
- **The MVP plays on a revealed map.** Entity fog arrives in M8 and the multiplayer stage in M7,
  but the seams must exist from the first payload.

## Decision

- **Registration.** Payloads use `RegisterPayloadHandlersEvent#registrar("1")` with handlers on the
  main thread; dev payloads are `.optional()`. Each `StreamCodec` delegates to a core `WireCodec`,
  so layouts and sizes are tested in L1.
- **One sender.** `NetGateway.send(Audience, payload)`, where `Audience` is `Player`, `Faction`,
  `TrackersOf(handle)`, `SeesCell(match, cell)` or `Match`.
  - Every payload type declares its allowed audiences, and an L2 test checks them.
  - Payloads are coalesced per player per tick, and bytes are counted per type and player.
  - ArchUnit rule B6 forbids any other sender.
  - From M7, channels have per-tick byte budgets with deferral.
- **Interest is computed in core** by `ReplicationService` over plain state: own units and
  buildings in full; allies per diplomacy; enemies only while visible; enemy buildings as last-seen
  records; observers everything. L1 asserts that no frame carries a hidden handle.
- **Replication tiers:**
  1. Vanilla tracking of bodies: interval 3, velocity updates off, `onGround` constant, two synced
     bytes changed only on transitions, HP never in synced data.
  2. `unit_summary` at 2 Hz for map-wide knowledge: full frames first, deltas against the last
     frame sent when bandwidth needs them.
  3. Details on demand: `unit_detail`, `unit_hud`, `building_state`, `faction_state`.
  4. In reserve: streamed body types with `updateInterval(Integer.MAX_VALUE)` and our motion
     channel, chosen per match at start, only if spike S11 shows tier 1 over budget.
- **Fog enforcement.**
  - **The gate.** `UnitEntity#broadcastToPlayer` returns `FogGate.canSee` in O(1): false across
    matches; true for observers, own faction and allies sharing vision; the visibility bit for
    enemies; the match rule for non-participants. It fails closed.
  - **Vision** is a per-faction grid of 4-block cells updated incrementally each tick. A unit is
    revealed at once and hidden only after 10 ticks unseen, a documented bounded leak that avoids
    pairing churn.
  - **Forced re-tracking (M8).** Every 5 ticks, `TrackedEntity#updatePlayer` runs for exactly the
    flipped (body, viewer) pairs, through two access-transformer lines on `ChunkMap.entityMap` and
    `ChunkMap$TrackedEntity`. `updatePlayer` is never cancelled.
  - **Chunk arrival (M2).** On `ChunkWatchEvent.Sent`, the bodies in that chunk are re-paired for
    that viewer: through the public `ServerChunkCache#move(viewer)` once at the next tick before M8,
    and through targeted `updatePlayer` after.
  - **Players (M7).** A `ServerPlayer#broadcastToPlayer` mixin hides enemy commanders always and
    enemy first-person bodies in fogged cells.
  - **Leak policy:**
    - entity-attached events go to trackers only;
    - units never play server-side positional sounds, and all unit sounds and particles leave as
      `fx` events with spatial audiences;
    - projectile visuals go only to viewers who see either end;
    - rejection reasons are uniform;
    - block changes stay visible until terrain secrecy (FOW-06);
    - the entity-id counter side channel is documented and accepted.
- **Security.**
  - Bounded decoding in every codec, with our own caps.
  - Token buckets per player and payload type.
  - Identity from the connection, slots bound to UUIDs, intents only.
  - Commander hardening in M7: drop spectator teleport-to-entity for match members, block
    spectate-by-attack, clamp commander bodies to the match space, `spectatorsGenerateChunks` false
    in arena worlds. L3 mock players send each hostile packet.
  - Operators are advised to lower NeoForge's `splitPacketMaxSizeLimitMb` on dedicated servers.

## Consequences

- Broadcast-first networking cannot be written, so fog in M8 fills a seam instead of rewiring
  networking.
- Fog latency: a reveal reaches a client within about 6 ticks. Hysteresis keeps a unit visible for
  up to 10 ticks after it leaves vision.
- Bandwidth: about 64 KB/s per client at 600 units with 300 visible and moving, against a budget of
  100 KB/s *(estimate)*; terrain streaming while panning is measured separately (S9, S11).
- The engine surface grows by two access-transformer lines (M8) and two mixins (M7), all listed in
  the inventory (ADR-0020).
- Under Moonrise, which re-checks pairing every tick, the targeted pass is redundant but harmless.
  If it misbehaves, the fallback is `ServerChunkCache#move(viewer)` per pass.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Re-evaluate by calling `ServerChunkCache#move(player)` per viewer at each refresh (B) | O(all tracked entities) per viewer per refresh where flips are few; kept as the zero-access-transformer fallback and for chunk-sent re-pairs before M8 |
| Rely on the viewer's movement packets | A still commander re-checks only every 20 ticks; a camera entity sends none |
| Cancel `updatePlayer` to hide | Blocks unpairing, so fogged units stay visible (RoN's bug) |
| Scoreboard teams for factions | Membership is broadcast to every client and leaks units |
| HP mirrored into vanilla health | Every hit forces a position update; HP travels in `unit_hud` instead |
| Syncable attributes for displayed stats (B) | Bypass the fog-aware sender |
| Cancelling `PlayLevelSoundEvent` as the main sound fix (B) | `AtPosition` carries no entity; units simply never play server sounds, and `AtEntity` is a backstop |
| Interest bubbles with view-driven bodies as the arena default (C) | Caps battles in view; kept as the open-world reserve |
