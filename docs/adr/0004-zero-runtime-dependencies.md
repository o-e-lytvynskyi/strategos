# ADR-0004: No runtime dependencies besides NeoForge

Date: 2026-10-21

## Status

Accepted

## Context

- Every runtime dependency is an extra download for players, a version conflict waiting in
  modpacks, and code that may not be ported when we move to 26.x.
- **NeoForge 21.1 already bundles much of what an RTS needs:** Mixin and MixinExtras, data
  attachments, config with a generated screen, HUD layers, JSON keyframe animations, entity
  outlines and section-geometry injection ([02-tooling.md](../02-tooling.md)).
- **The candidate libraries all fell short**
  ([research/libraries.md](../research/libraries.md)):
  - GeckoLib and AzureLib are unbenchmarked at 300–500 animated units.
  - Flywheel turns its GPU backends off under shader packs, and its indirect backend needs
    OpenGL 4.6, which macOS lacks.
  - Veil and Satin are custom shader stacks that Iris ignores.
  - owo-lib, LDLib2 and ModernUI are beta or lock us into their frameworks.
  - The ECS and behaviour-tree libraries are dormant.
  - Infiniverse would bring runtime dimensions.

## Decision

- **Strategos ships with no runtime dependency besides NeoForge.** Players install one jar.
- **`core`** compiles only against libraries Minecraft 1.21.1 already ships, as `compileOnly`
  (ADR-0003). It never gains a dependency Minecraft does not ship.
- **Third-party code we need is adapted as source** under its licence, with the notice kept in
  `NOTICE` (ADR-0005). Today that is only the RVO2 ORCA port (Apache-2.0).
- **Soft integrations only.** Optional integrations, such as skipping our overlays during an Iris
  shadow pass, are detected by class presence and never required.
- **Dev-only tools are allowed** because they never reach players: spark as `localRuntime`, the
  `-Pcompat` profile (Sodium, Iris, Lithium, ModernFix, FerriteCore, Entity Culling,
  ImmediatelyFast), JMH and test libraries.
- **A new runtime dependency** needs its own ADR that supersedes this one for that library, and
  the owner's approval ([03-design-process.md](../03-design-process.md), design rule 1).

## Consequences

- We build the camera, HUD, navigation, avoidance and networking ourselves. That is planned work
  in M1 and M2, not a surprise.
- Unit art uses vanilla `HumanoidModel` with armour and held-item layers and NeoForge keyframe
  animations. That is resource-pack friendly and on Sodium's entity fast path.
- No version conflicts in modpacks, and one jar to port at the 26.x gate.
- Benchmarks can still justify a library later: for example GeckoLib after spike S4, or Flywheel
  behind a vanilla fallback if entity rendering is ever the bottleneck.

## Alternatives considered

| Alternative | Why not |
|---|---|
| GeckoLib or AzureLib for unit models | Unbenchmarked at our scale; wait for S4 and the art decision D-04 |
| Flywheel for instanced unit rendering | Off under Iris shader packs; its indirect backend needs GL 4.6, which macOS lacks |
| Veil, Lodestone, Satin | Custom shader and post stacks that shader packs won't render |
| Architectury API | Multi-loader is rejected (ADR-0001) |
| Infiniverse for runtime dimensions | The arena world preset needs none (ADR-0010) |
| An ECS or behaviour-tree library | Dormant projects or a libGDX dependency; the simulation core is ours by design |
| Registrate, owo-lib, LDLib2, ModernUI | Unneeded abstraction, beta quality or global UI changes |
