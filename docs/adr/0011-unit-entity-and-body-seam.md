# ADR-0011: Units are kinematic `LivingEntity` views behind a body seam

Date: 2026-10-21

## Status

Proposed - pending spike S10

**S1 reported 2026-10-22** ([S1-server-cost-per-body.md](../spikes/S1-server-cost-per-body.md)),
still Proposed until S10: at 500 moving bodies a kinematic body costs 0.10 µs per tick beyond the
core (exit ≤ 4) and 0.12 µs in all, against 4.9 µs for a stripped `PathfinderMob` and for a physics
`LivingEntity` on a vanilla server (2.2 and 1.9 with Lithium). The best body is far below 15 µs, so
D-01's default stays at 150. A vanilla body's cost is movement against block collisions (76 % of
the server thread's samples), not AI.

**Amended 2026-10-21** by [ADR-0023](0023-plan-alignment-after-roadmap-approval.md), still
Proposed: D-08 chose the observer avatar, which units never target, so units hitting players or
mobs with the Strategos damage types and the `LivingIncomingDamageEvent` handler wait for CMB-08,
which is deferred.

## Context

- **With the core owning all state (ADR-0008),** a unit's entity only has to show the unit, be hit
  by vanilla sources and be tracked.
- **Verified in the 1.21.1 sources:**
  - **No mob costs.** Only `Mob`s join `ServerLevel.navigatingMobs`, the list that
    `sendBlockUpdated` loops over on every collision-shape change (ServerLevel.java:1773–1781,
    1093). So construction costs O(blocks × mobs) only for mobs.
  - **No AI to strip.** `Mob#serverAiStep` is final and always runs sensing, selectors,
    navigation and controls (Mob.java:773).
  - **No despawn.** `Entity#checkDespawn` is empty (Entity.java:3555).
  - **Vanilla rendering still works.** The humanoid renderer, model, armour and held-item layers
    accept any `LivingEntity` (LivingEntityRenderer.java:31, HumanoidModel.java:22). Only
    `HumanoidMobRenderer` needs a `Mob`.
  - **Bodies never persist.** `noSave()` keeps entities out of chunk storage, because `getEncodeId`
    returns null (EntityType.java:1339, Entity.java:1914).
  - **No spawner cost.** `MobCategory.MISC` skips `NaturalSpawner.createState`'s per-entity work
    (NaturalSpawner.java:74).
  - **Sizes.** `LivingEntity#getDimensions(Pose)` is final, and `getDefaultDimensions` can be
    overridden (LivingEntity.java:3423, 3427).
  - **Damage pipeline.** Invulnerability frames drop focused fire (`invulnerableTime > 10`,
    LivingEntity.java:1190) unless the damage type is `minecraft:bypasses_cooldown`.
  - **Replication.** A dirty `SynchedEntityData` value forces a position update, and an `onGround`
    flip forces a teleport packet (ServerEntity.java:121, 150).
- **Every per-unit cost figure is an unverified extrapolation** (gap G1), and kinematic movement may
  look wrong on partial blocks. Two measurements decide:
  - **S1:** server µs per body at 200, 500 and 1,000 bodies, idle, moving and fighting, for a
    kinematic `LivingEntity` view, a stripped `PathfinderMob` and a physics `LivingEntity`.
  - **S10:** kinematic fidelity on slabs, stairs, snow, carpets, paths, farmland, shallow water and
    drops.

## Decision

- **One class:** `UnitEntity extends LivingEntity implements IEntityWithComplexSpawn`, on three
  entity types by body size:
  - `strategos:unit_humanoid` (M0), `unit_mount` (M5) and `unit_large` (M6);
  - each built with `MobCategory.MISC`, `noSave()`, `noSummon()`, `fireImmune()` and
    `setShouldReceiveVelocityUpdates(false)`;
  - tracking range 10–12 chunks, update interval 3;
  - per-template sizes through `getDefaultDimensions`.
- **The server side in `KINEMATIC` mode:**
  - `tick()` skips `super.tick()` and runs only `tickDeath()` while dying;
  - pushing off, `MovementEmission.NONE`, no loot, no riding, no portals;
  - `onGround` constant;
  - two synced bytes changed only on transitions, with HP never in synced data (HUD HP travels in
    our `unit_hud` payload);
  - `broadcastToPlayer` asks `FogGate`.
- **The client side** keeps vanilla interpolation and walk animation. It overrides glow and team
  colour for selection, render distance and the culling box, and fills equipment from the
  template.
- **Damage.**
  - Unit against unit is resolved in the core; `LivingEntity#hurt` is never called for it.
  - Vanilla sources hitting a unit go through an overridden `hurt` and `knockback` into the core as
    journaled inputs.
  - Units hitting players or mobs use the target's own `hurt` with our damage types, which are
    tagged `minecraft:bypasses_cooldown`, while a `LivingIncomingDamageEvent` handler zeroes the
    post-attack invulnerability.
  - Death calls `die()`, which broadcasts entity event 3 to trackers, then drives `tickDeath()`.
- **The body seam:** `BodyMode { KINEMATIC, PHYSICS, NONE }`, with `BodyPose`, `DriveIntent` and
  `BodyReport` records. `KINEMATIC` is the default. `PHYSICS` (vanilla `travel` driven by the core,
  pose fed back as a journaled input) is built only if S10 rejects kinematic visuals for a class of
  templates. `NONE` is a unit without a body.
- **Materialisation.** `BodyRegistry` is the only creator of bodies, at most 64 spawns per tick. In
  arenas every unit has a body. View-driven materialisation is the open-world and overload reserve.
- **Level of detail by activity** (ACTIVE, MOVING, WORKING, IDLE, ABSTRACT), never by observation.
  It arrives with UNIT-05, after S1 has priced a body.

**Exit criteria that confirm this ADR:**
- **S1:** a kinematic body costs ≤ 4 µs per tick beyond the core at 500 moving bodies, and the
  best body ≤ 15 µs; otherwise the default population cap drops to 100 per player (D-01).
- **S10:** floor error ≤ 1/16 block, and no visible pops at steps.

## Consequences

- Construction storms never visit units; there is no vanilla AI, despawn or mob-cap cost; bodies
  never duplicate.
- Kinematic units do not get vanilla step-up, fluids, knockback, pistons or falls for free. The
  core models floor heights, falls, impulses and hazards (§6.6 of
  [04-architecture.md](../04-architecture.md)).
- Vanilla armour, shields and totems never apply to units. Mods that act on `Mob`s ignore units.
- Selection outlines and health bars come from our client code, not from scoreboard teams or
  vanilla health.

## Alternatives considered

| Alternative | Why not |
|---|---|
| A stripped `PathfinderMob` with vanilla physics (B) | Joins `navigatingMobs`; physics moves query entities per move, which is O(n²) in melee blobs; mob overheads must be neutralised one by one; ties authority to vanilla physics. Measured in S1 for the record |
| Physics `LivingEntity` by default | Collision and travel every tick for every unit, and poses outside the determinism guarantee. Kept as the `PHYSICS` fallback |
| One entity class per unit type | RoN's anti-pattern; `EntityType`s are fixed at startup anyway, so data-defined units need generic types |
| Multipart entities for large units | NeoForge scans every `PartEntity` on entity queries (fact 7) |
| Mirroring HP into vanilla health | Every hit dirties synced data and forces a position update |
