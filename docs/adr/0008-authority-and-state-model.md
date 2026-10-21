# ADR-0008: The core owns all match state; entities are views

Date: 2026-10-21

## Status

Accepted

## Context

Research contradiction 1 and gap G1. The reports disagreed on who owns unit position, HP and
orders:
- the Minecraft-free core (records plus kinematic movement);
- the entity (a stripped `PathfinderMob` with vanilla physics);
- a mix: SavedData records, or entity attachments.

The answer fixes the tick order, persistence, replication, the damage pipeline, how much can be
tested without Minecraft, and the open-world path.

Three proposals answered it differently:
- A and C put authority in the core.
- B put it in the entity.

The judges ranked A first for maintainability and second for performance and delivery, the best
combined standing. They found no rewrite-forcing flaw in core authority, and a rewrite-forcing one
in B's entity authority: rules live in code typed against Minecraft, behaviour is testable only in
GameTests, and the 26.x port reaches behaviour code
([design/judgement-maintainability.md](../design/judgement-maintainability.md)).

## Decision

- **The core owns every piece of match state:**
  - unit position, heading, velocity, HP, orders and queues, UnitAI state, stats, faction,
    template and rank;
  - buildings (footprint, progress, HP, damage stage, queue, rally);
  - resource nodes, treasury, population, techs, diplomacy, vision and explored state;
  - commander modes.
- **Minecraft entities are views.** A `UnitEntity` is a `noSave()` body that mirrors the core's
  pose in the default `KINEMATIC` mode (ADR-0011). It is created only by `BodyRegistry`, never
  persisted, and recreated from core state after any restart.
- **Blocks of core-owned objects are pictures:** construction stages, damage stages, depleted node
  blocks. The core is authoritative, and the block writer converges the world to it (ADR-0021).
- **Terrain belongs to the world.** The core keeps a derived surface grid, rebuilt from slices.
- **First-person players belong to vanilla.** The core sees them as external agents, mirrored in
  each tick.
- **One port.** The core advances only through `MatchCore.step(TickInputs) → TickOutputs`.
  Everything from Minecraft is a journaled input event; everything the core wants done is an
  output event (ADR-0009). Adapters may use only `@CoreApi` types (ArchUnit rule B2).
- **One writer per datum.** Aggregates are counters updated on events, never scans of loaded
  entities. Players are keyed by UUID and units by core handles; vanilla entity ids are never
  stored.
- **Bodiless units.** A unit can exist without a body (`BodyMode.NONE`), so authority never
  depends on chunk loading. In an arena every unit has a body; in an open world, units outside
  entity-ticking chunks travel abstractly.

## Consequences

- **What it buys:**
  - Whole matches, the AI included, run in plain JUnit with fakes and give identical state hashes.
    A bug seen in a GameTest or in the game replays in JUnit.
  - The 26.x port touches only adapters; `core` is unchanged.
  - The open world needs no change to authority, persistence or simulation.
  - Bodies cannot duplicate on chunk reloads.
  - Construction never loops over units, because bodies are not `Mob`s.
- **What it costs, stated plainly:**
  - Two representations of every unit, with explicit mirror code: roughly 15–25 % more code in the
    unit and building slices *(estimate)*.
  - Terrain semantics written again for kinematic bodies: floor heights, steps, falls, knockback,
    hazards.
  - Weaker interoperability: mods that act on `Mob`s or listen to vanilla damage see units only
    partly.
  - Infrastructure before the first unit walks, bounded by the walking-skeleton tripwire (risk
    R11): the port, the journal, the codecs.
- **Fallbacks inside the decision:**
  - If spike S10 rejects kinematic visuals for some templates, those templates get `PHYSICS`
    bodies. Their poses come back as journaled inputs, so replays stay exact.
  - If spike S1 caps the body count, view-driven materialisation becomes the policy, with no
    change to authority.

## Alternatives considered

| Alternative | Why not |
|---|---|
| The entity owns state: a stripped `PathfinderMob` with vanilla physics, orders and UnitAI in entity fields (B) | Behaviour and rules in Minecraft-typed code contradict design rule 3; behaviour is testable only in 40–120 s GameTests with outcome bounds; no bit-exact replay; the 26.x port reaches UnitAI, combat and construction; the open world needs an unbuilt parking system; one UnitAI exception crashes the server through `guardEntityTick` |
| Core-owned state with units virtual by default, bodies only inside view bubbles (C) | Same authority, but the bubble machinery serves 1,000 units on 1,024² arenas, which the owner has not asked for; battles above the bubble cap show as dots. Kept as the open-world and overload reserve |
| Authoritative SavedData records with vanilla physics bodies | Position would have two owners. Covered instead by the `PHYSICS` body mode with journaled poses |
| Per-unit state in entity attachments, territory in chunk attachments | Attachments vanish with unloaded chunks, and an entity can be duplicated or lost between chunk and data saves |
