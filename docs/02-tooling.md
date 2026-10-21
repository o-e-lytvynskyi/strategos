# 02 — Tooling and platform

Decided 2026-10-20. Evidence and sources: [research/platforms.md](research/platforms.md),
[research/libraries.md](research/libraries.md), [research/testing.md](research/testing.md).
Every version below was checked against Maven metadata, Modrinth or GitHub on 2026-10-20,
and the toolchain was built on the dev machine (cold build of the official MDK: 98 s).

## Decisions

| Area | Choice | Why |
|---|---|---|
| Minecraft | **1.21.1** (Java 21) | Owner's choice. It is still the main NeoForge version: Modrinth has 21,500 mods and 2,178 modpacks for NeoForge 1.21.1, against 9,690 mods and 99 packs for all 26.x lines combined |
| Loader | **NeoForge 21.1.x, single loader**, minimum **21.1.264** | Richest API for an RTS on 1.21.1: chunk-ticket controller, HUD layers, render-stage events, cancellable mouse input, data attachments with per-player sync, data maps, GameTest + JUnit integration, `testframework`. The 21.1 line is still patched (10 builds in October 2026). 21.1.262 fixes three network advisories; 21.1.264 makes attachment *initial* sync respect the per-player filter, which fog of war relies on |
| Build | **ModDevGradle 2.0.148**, Gradle wrapper **9.2.1** | MDK defaults, verified on this machine. Gradle 9.8.x only after `verify` passes on it |
| Java | **Toolchain 21** (Temurin 21.0.12.1, auto-provisioned by foojay 1.0.0); daemon pinned to 21 via `gradle/gradle-daemon-jvm.properties` | Minecraft 1.21.1 ships Java 21. NullAway's JSpecify mode needs an OpenJDK ≥ 21.0.8 |
| Names | Mojang mappings + **Parchment 1.21.1 / 2024.11.17** | NeoForge standard; the same names continue into 26.x, so a later port needs no remapping |
| Language | **Java 21**, no Kotlin | No extra language runtime for players; records, sealed types and pattern matching are enough |
| Runtime dependencies | **None besides NeoForge** | NeoForge already bundles Mixin + MixinExtras and covers attachments, config (with a generated screen), HUD layers, JSON keyframe animations, entity outlines and section-geometry injection |
| Unit art | Vanilla `HumanoidModel`/`PlayerModel` + armour and held-item layers; NeoForge JSON keyframe animations | Zero dependencies, resource-pack friendly, uses Sodium's entity fast path. GeckoLib only after a benchmark at scale |
| Rendering | Vanilla `RenderType`s and NeoForge events only; no custom shaders, no raw GL | Iris ignores mod shader programs while a pack is active; Flywheel turns its GPU backends off under shader packs and its indirect backend needs OpenGL 4.6 (macOS stops at 4.1) |
| Layout | Gradle multi-project: `build-logic` (convention plugin), `core` (pure Java, no Minecraft), `mod` (NeoForge) | `core` stays free of Minecraft at compile time, tests in milliseconds and survives version ports. Details in [04-architecture.md](04-architecture.md) |

## Rejected alternatives

| Alternative | Why not |
|---|---|
| Fabric on 1.21.1 | Viable, but its 1.21.1 API is thinner for this mod (no persistent chunk tickets, HUD layers or render stages of the same depth), and the Fabric ecosystem has already moved on to 26.x. Fabric's client-gametest API, its one testing advantage, never reached 1.21.1 (first shipped for 1.21.4) |
| MinecraftForge 52 | The community moved to NeoForge; Forge 1.21.1 has no builds of Sodium, Iris, Lithium, FerriteCore, ModernFix, Flywheel, EMI or Jade |
| Multi-loader (Architectury, MultiLoader-Template) | Doubles the integration and test surface for no player-facing gain; the Minecraft-free `core` already gives most of the portability |
| Minecraft 26.x now | Quarterly breaking releases (26.2 and 26.3 both rewrote parts of rendering and input) and a NeoForge ecosystem less than half the size. Revisited at the decision gate below |
| GeckoLib, AzureLib | Wait for a benchmark of 300–500 animated units against vanilla models |
| owo-lib, LDLib2, ModernUI | Beta on NeoForge / heavy framework lock-in / global UI changes |
| Registrate | Unneeded abstraction for an entity-centric mod |
| Veil, Lodestone, Satin, Flywheel | Custom shader and post stacks that shader packs will not render; Flywheel only if profiling ever shows entity rendering is the bottleneck, behind a vanilla fallback |
| ECS / behaviour-tree libraries | Dormant (last releases 2019–2023) or pull in libGDX; the simulation core is ours |
| Baritone, MineColonies, Reign of Nether code | LGPL/GPL and the wrong shape for RTS movement; used as design references only (see the clean-room rule) |
| jqwik ≥ 1.10 | Its licence has an "Anti-AI Usage Clause", and 1.10.0 shipped output aimed at coding agents. Seeded fuzz tests with JUnit's `@ParameterizedTest` replace it |
| Checkstyle, PMD | Overlap with the formatter and ErrorProne; noise |

## Pinned versions

| Component | Version | Notes |
|---|---|---|
| NeoForge | 21.1.264 | `loader_version_range=[4,)` (FML 4.x) |
| ModDevGradle | 2.0.148 | |
| Gradle wrapper | 9.2.1 | |
| foojay resolver | 1.0.0 | |
| Parchment | 1.21.1 / 2024.11.17 | final 1.21.1 release |
| `net.neoforged:testframework` | 21.1.264 | L2/L3 helpers (mock players, empty templates) |
| JUnit Jupiter | 5.14.4 | stay on 5.x: FML's JUnit integration is built against 5.10 |
| AssertJ | 3.27.7 | |
| ArchUnit | 1.5.1 | plain-JVM `archTest` task |
| Spotless / palantir-java-format | 8.10.4 / 2.102.0 | |
| ErrorProne / NullAway / JSpecify | 2.50.0 / 0.14.2 / 1.0.1 | `net.ltgt.errorprone` 5.1.1 |
| JMH | 1.37 | a plugin-free `jmh` source set run through `JavaExec` ([ADR-0003](adr/0003-module-layout-and-core-merge.md)); the `me.champeau.jmh` plugin (0.7.3 predates Gradle 9 support) is not used |

`core` compiles only against the JDK and libraries Minecraft 1.21.1 already ships, as
`compileOnly` at exactly the shipped versions: fastutil 8.5.12, slf4j-api 2.0.9,
DataFixerUpper 8.0.16 (Codecs) and Gson 2.10.1, plus the JSpecify 1.0.1 annotations; no Guava,
JOML or Netty ([ADR-0003](adr/0003-module-layout-and-core-merge.md)).

## Dev-only tools

- **spark 1.10.124** as `localRuntime` for profiling in dev runs; JFR (`/jfr start|stop`,
  `jfr view hot-methods`) for text-readable profiles; async-profiler 4.x with `-e itimer` on macOS.
- **Compatibility profile** `-Pcompat` adds Sodium 0.8.13, Iris 1.8.14-beta.1, Lithium,
  ModernFix, FerriteCore, Entity Culling and ImmediatelyFast to dev runs, so every client
  feature can be checked against the mods most players run.
- **CI** (when the repository goes to GitHub): one Linux job on GitHub Actions with
  `gradle/actions/setup-gradle` on `cache-provider: basic`; client scenarios under Xvfb + Mesa.

## Dependency policy

1. Every version is pinned in `gradle/libs.versions.toml`; no dynamic versions. Bumps arrive
   as separate changes gated by the full `verify` run.
2. A new **runtime** dependency for players needs an ADR in [adr/](adr/).
3. `core` never gains a dependency Minecraft does not already ship.
4. Licences: GPL and all-rights-reserved code is never copied. MIT/Apache-2.0 code may be
   adapted with its notice kept in `NOTICE`. Assets are never copied.

## Version strategy and the 26.x decision gate

1.21.1 is the right base today, but the ecosystem frontier is moving to 26.x, and the
1.21.1 → 26.x port is concentrated in rendering, GUI and input — the areas an RTS client
leans on hardest. The architecture keeps that port small: game rules in `core`, rendering
written "extract state, then draw", UI drawn from view-models, input mapped through our own
action layer, persistence and networking through codecs.

Re-decide at the MVP gate (the end of M6, the first playable version) or in 2027-Q2, whichever
comes first, after a time-boxed port spike of `mod` to the leading 26.x line (card QA-14 in
[ROADMAP.md](ROADMAP.md)). Triggers to watch:
a named 26.x "stable modding version"; flagship packs (ATM11) leaving beta with Create and
Mekanism on 26.x; new NeoForge mods per quarter on one 26.x line overtaking 1.21.1;
NeoForge 21.1 dropping to security-only fixes.

## Machine notes (owner's Mac)

- Apple M4 Pro, 24 GB, macOS 26. JDK 21 is provisioned by Gradle into `~/.gradle/jdks`; do not
  install `openjdk@21` from Homebrew. Homebrew JDK 25 can run the Gradle daemon if needed.
- Gradle caches after the first MDK build: about 1 GB. The disk had 47 GB free on 2026-10-20.
- Client runs need `-XstartOnFirstThread` on macOS; MDG adds it automatically.
- Interactive `runClient`/`runServer` sessions are started by the owner. The assistant only
  runs tasks that end on their own (builds, unit tests, GameTests, scripted scenario runs).
