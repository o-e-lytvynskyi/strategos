# ADR-0020: One reviewed inventory of every hook into Minecraft

Date: 2026-10-21

## Status

Accepted

## Context

- **Every mixin, access transformer and registration** is code that a 26.x port must re-verify,
  and a point where other mods (Lithium, Moonrise, C2ME) can collide with ours.
- **AI-written code can add hooks quickly and silently.** The feasibility study lists
  architecture erosion as a failure mode ([01-feasibility §8](../01-feasibility.md)).
- **The preference order for engine access** ([01-feasibility §7](../01-feasibility.md)): events
  first, then access transformers, then narrow mixins with a compatibility test.

## Decision

- **[04-architecture.md §2.5](../04-architecture.md) holds the inventory** of every engine hook:
  mixins (target, injection point, purpose), access-transformer lines, entity types, damage types,
  data maps, world-generation data, ticket controllers, `SavedData` names, reload listeners,
  payloads, key mappings, GUI layers, commands, config files and the events subscribed.
- **An L2 test compares the code with the inventory:** `strategos.mixins.json` entries, the
  `accesstransformer.cfg` lines, registered entity types, payload types and data registrations.
  Any difference fails the build. Changing the inventory is a reviewed change to the architecture
  document, and adding a mixin also needs a line in the risk table.
- **Mixins live only in `dev.strategos.mod.mixin`** and only route calls into named handlers
  (`BlockChangeRouter`, `FogGate`, `CommanderGuard`). ArchUnit rule B10 enforces it.
- **The planned surface by milestone:**

| Milestone | Mixins | Access transformers |
|---|---|---|
| M0 | none | none |
| M1 | `LevelChunkMixin` (RETURN of `LevelChunk#setBlockState`) | none |
| M7 | `ServerPlayerMixin` (HEAD of `broadcastToPlayer` and `attack`); `ServerGamePacketListenerImplMixin` (HEAD of `handleTeleportToEntityPacket`) | none |
| M8 | – | `ChunkMap.entityMap`; `ChunkMap$TrackedEntity` |

- **The 26.x port spike** re-verifies every inventory line on the target version.

## Consequences

- Minecraft coupling stays visible and countable as code is written quickly.
- The end state is small: three mixin classes with four injections and two access-transformer
  lines by M8. No camera, projection, rendering or chunk-mesh mixins.
- The compatibility profile (`-Pcompat`, with Lithium and Moonrise-style tick changes) runs on
  every milestone that adds a hook.
- A new hook costs a document change, which is the point.

## Alternatives considered

| Alternative | Why not |
|---|---|
| No inventory; review mixins ad hoc | Hooks accumulate unnoticed; port costs grow invisibly |
| Ban mixins entirely | `LevelChunk#setBlockState` is the only hook that sees every block change (fact 12), and the spectator hardening has no event |
| An access transformer for everything instead of mixins | Access transformers widen visibility but cannot change behaviour; used where only access is needed |
