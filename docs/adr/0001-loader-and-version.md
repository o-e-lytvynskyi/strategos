# ADR-0001: Minecraft 1.21.1 on NeoForge 21.1 as the only loader

Date: 2026-10-21

## Status

Accepted

## Context

- **The owner chose Minecraft 1.21.1** on 2026-10-20 ([research/README.md §1](../research/README.md)).
  It is still the main NeoForge version: Modrinth lists 21,500 mods and 2,178 modpacks for NeoForge
  1.21.1, against 9,690 mods and 99 packs for all 26.x lines combined
  ([02-tooling.md](../02-tooling.md)).
- **An RTS needs engine features** that only some loaders offer on 1.21.1: persistent chunk
  tickets that keep an arena ticking with nobody near, HUD layers, render-stage events,
  cancellable mouse input, data maps, and GameTest and JUnit integration
  ([research/platforms.md](../research/platforms.md)).
- **Minimum NeoForge build.**
  - NeoForge 21.1.262 fixed three network advisories (2026-10-15).
  - 21.1.264 makes attachment initial sync respect the per-player filter (fact 17).
- **Fog of war, multiplayer and the client** all lean on these APIs. The 1.21.1 → 26.x port is
  concentrated in rendering, GUI and input, the areas an RTS client uses most.

## Decision

- **Minecraft 1.21.1**, Java 21 (toolchain and Gradle daemon pinned to 21), no Kotlin.
- **NeoForge 21.1.x as the only loader**, minimum **21.1.264**, with
  `loader_version_range=[4,)` (FML 4.x).
- **The build toolchain as pinned in [02-tooling.md](../02-tooling.md):** ModDevGradle 2.0.148,
  Gradle wrapper 9.2.1, foojay 1.0.0, Mojang mappings with Parchment 1.21.1 / 2024.11.17, and
  `net.neoforged:testframework` 21.1.264 for tests.
- **Every version is pinned** in `gradle/libs.versions.toml`. A bump is a separate change gated by
  the full `verify` run.
- **The 26.x decision gate.** We decide again at the end of the first playable milestone or in
  2027-Q2, whichever comes first, after a time-boxed port spike of `mod` (QA-14). The triggers to
  watch:
  - a named 26.x "stable modding version";
  - flagship packs leaving beta on 26.x;
  - new NeoForge mods per quarter on one 26.x line overtaking 1.21.1;
  - NeoForge 21.1 dropping to security-only fixes.

## Consequences

- **What we gain:**
  - `TicketController` force-loading (ADR-0010);
  - `RegisterGuiLayersEvent` and `RenderGuiLayerEvent`;
  - `RenderLevelStageEvent`;
  - the cancellable `InputEvent.MouseButton.Pre`;
  - data maps (ADR-0012);
  - `RegisterGameTestsEvent` and FML's JUnit integration (ADR-0006, ADR-0007);
  - payload registration with versioning.

  These are all verified in the decompiled 21.1.262 tree (index in
  [04-architecture.md, Appendix A](../04-architecture.md)).
- **Sodium, Iris, Lithium, ModernFix, FerriteCore, Entity Culling and ImmediatelyFast** exist for
  this line and form the `-Pcompat` profile that every client feature is checked against.
- **We depend on NeoForge keeping 21.1 patched.** The decision gate watches for the end of that.
- **A later port to 26.x is contained:** `core` is untouched, and each known change lands in one
  adapter package ([04-architecture.md §18.1](../04-architecture.md)).

## Alternatives considered

| Alternative | Why not |
|---|---|
| Fabric on 1.21.1 | Viable, but its 1.21.1 API is thinner for this mod (no persistent chunk tickets, HUD layers or render stages of the same depth). Its ecosystem has moved on to 26.x, and its client-gametest API, its one testing advantage, never reached 1.21.1 |
| MinecraftForge 52 | The community moved to NeoForge; Forge 1.21.1 has no builds of Sodium, Iris, Lithium, FerriteCore, ModernFix, Flywheel, EMI or Jade |
| Multi-loader (Architectury, MultiLoader-Template) | Doubles the integration and test surface for no player-facing gain. The Minecraft-free `core` already gives most of the portability |
| Minecraft 26.x now | Quarterly breaking releases (26.2 and 26.3 both rewrote parts of rendering and input) and a NeoForge ecosystem less than half the size. Revisited at the decision gate |
| NeoForge below 21.1.264 | Misses the network advisory fixes and the per-player initial attachment sync |
