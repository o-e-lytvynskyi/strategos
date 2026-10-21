# Strategos: mod loader, toolchain and version strategy (Minecraft 1.21.1)

*Research date: 2026-10-20. Scope: loader, build toolchain, project layout and version strategy for a new, large, long-lived RTS overhaul mod.*

*Sources and method. All versions and dates come from primary sources read today. Those are: Maven metadata (with HTTP `Last-Modified` of the POMs), GitHub branches and commits, official blogs and docs, and Modrinth API queries. API claims were checked against the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 sources (`scratchpad/mc-src-1.21.1`), against the reference MDK build log, and against artifacts I downloaded (the NeoForge testframework jar, Fabric API and Loom sources). Anything marked **Inference** is my own judgement, not documented fact. Not done: no Gradle builds (forbidden), no CurseForge API (it needs a key). An independent fact-check re-verified the claims later the same day. Fixes are marked *(corrected)* inline and summarised in "Verification notes" at the end.*

---

## 0. Decision summary

| Decision | Recommendation | Confidence |
|---|---|---|
| Loader | **NeoForge 21.1.x, single loader** (start on 21.1.264, 2026-10-19) | High |
| Build plugin | **ModDevGradle (MDG) 2.0.148** (2026-09-29) | High |
| Gradle wrapper | **9.2.1** at bootstrap (MDK default, verified on this machine today), then **9.8.2** after a smoke test | Medium-high |
| Java | **Toolchain 21** (Temurin 21.0.12.1 via foojay 1.0.0). Pin the Gradle daemon to 21 | High |
| Names | **Mojang mappings + Parchment `1.21.1` / `2024.11.17`** | High |
| Layout | **`core` (pure Java, no Minecraft) + `mod` (NeoForge/MDG) + `gametest` source set run as a separate test mod** | High for the core/mod split; Medium for the gametest wiring (verify at bootstrap) |
| Multi-loader | **No** for v1. Keep the loader glue in one package so it stays possible | High |
| Version | **Start on 1.21.1 as chosen, but treat the 26.x port as planned and budgeted work.** Architect for it now and set a re-decision gate | Medium (see §6) |

**Why.**
- In October 2026, 1.21.1 is still the main NeoForge version by a wide margin. Modrinth has 21,500 mods and 2,178 modpacks with NeoForge 1.21.1 builds, against 9,690 mods and 99 modpacks for any 26.x.
- Large content and system mods and kitchen-sink packs live there: ATM10 has about 22.5M downloads *(corrected from 22.4M; 22,489,861)* and its 8.3 release came out 2026-10-15.
- The 21.1 branch is still patched: 10 builds in October 2026, including network-hardening security fixes.
- NeoForge covers far more of what an RTS needs than Fabric API does on 1.21.1. That includes:
  - synced attachments with per-player filtering;
  - persistent chunk tickets;
  - HUD layers and render stages;
  - cancellable mouse events;
  - data maps;
  - a SERVER config that can be overridden per world and is synced to clients *(corrected wording)*;
  - a JUnit/GameTest stack with a live-server JUnit provider.
- NeoForge has run on Mojang names since 1.20.2, so the eventual 26.x port carries no mapping migration.

The risk is the version, not the loader. The ecosystem frontier is moving to 26.x, and porting 1.21.1 → 26.3 is a large job concentrated in rendering, GUI and input, which are exactly the areas an RTS client leans on (§6).

---

## 1. NeoForge 21.1.x on 1.21.1

### 1.1 Release status and cadence

| Item | Value | Source |
|---|---|---|
| Latest 1.21.1 build | **21.1.264**, 2026-10-19 | [maven-metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml), POM Last-Modified |
| MDK default | 21.1.262 (2026-10-15); MDK repo updated 2026-10-18. The MDK sets `loader_version_range=[1,)` | [MDK-1.21.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) |
| First 21.1 build | 21.1.1, 2024-08-08, the day 1.21.1 shipped | Maven, [Mojang manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) |
| Cadence | 63 builds since 21.1.200 (2025-08-09). Per month: 2025-08 7, -09 3, -10 4, -11 3, -12 1; 2026-01 2, -02 0, -03 3, -04 6, -05 5, -06 1, -07 13, -08 2, -09 3, **-10 10**. 21.1.260 and 21.1.261 were never published | POM Last-Modified |
| Security | **21.1.229** (May 2026): fixes a `FriendlyByteBuf#readCollection/readMap` crash in which a client can force a huge allocation. **21.1.262** (2026-10-15): enforces payload directions and decode limits, checks split-packet sizes and limits channel ids. It was released the same day as 21.11.48, 26.1.2.122 and 26.2.0.93. *(corrected source attribution)* The NeoForged blog post (2026-05-11) covers only the 21.1.229 fix. The 21.1.262 hardening has no blog post or advisory. The only record of it is three commits of 2026-10-14 on the 1.21.1 branch; the same three also landed on 26.1.x | [NeoForged blog](https://neoforged.net/news/mitigating-vulnerabilities-network/), [1.21.1 commits](https://github.com/neoforged/NeoForge/commits/1.21.1) |
| Latest backports | 21.1.264: "Check AttachmentSyncHandler#syncToPlayer() for initial sync (#3670)". The patch actually calls `AttachmentSyncHandler#sendToPlayer(IAttachmentHolder, ServerPlayer)`. In 21.1.262, update syncs already applied that filter, but the initial sync (`AttachmentSync#syncInitialAttachments`) skipped it. The per-player filter now applies to the initial sync too, which matters if attachments carry hidden information. 21.1.263: enchanted-loot events. Also FML 4.0.45, Mixin 0.16.4+mixin.0.8.7, MixinExtras 0.5.5 (2026-10-03/04) | same |
| NeoForge's own build | 1.21.1 branch builds with Gradle **9.8.1** (since 2026-10-10) and MDG 2.0.115 (since 2025-10-11) | [gradle-wrapper.properties](https://github.com/neoforged/NeoForge/blob/1.21.1/gradle/wrapper/gradle-wrapper.properties), [gradle.properties](https://github.com/neoforged/NeoForge/blob/1.21.1/gradle.properties) |
| License | LGPL-2.1 (puts no constraint on the mod's own license) | GitHub |

### 1.2 Support policy

- **There is no formal LTS.** NeoForge team notes ([NAP 2025](https://hackmd.io/@neoforged/rJ_d8ZxLxl); the HackMD page carries no date): "The idea of LTS is not a thing as NeoForge will accept backports of non-breaking PRs to any past version minus 1.20.1." Also: "LTS is determined by community engagement."
- **Workflow.** A PR must land on the latest branch before it can be backported ([21.2 post, 2024-10-22](https://neoforged.net/news/21.2release/)). That same post asked modders to "continue to actively maintain the 1.21.1 version of their mods until the release of Minecraft 1.22". There never was a 1.22; 26.1 took its place.
- **Signal about the successor.** The [26.1 post (2026-03-24)](https://neoforged.net/news/26.1release/) says there are "strong indications that 26.1 will be the next stable modding version, replacing 1.21.1". The author labels this a personal guess, not an official position.
- **Inference.** Maintenance of 1.21.1 is driven by demand. With ATM10 and more than 21k mods, demand should last through 2027. But there is no committed end date, and backports must be non-breaking. Big new APIs (for example the 21.9 transfer rework) will not come to 21.1.

### 1.3 ModDevGradle 2.0.148 (2026-09-29, LGPL-2.1)

Verified against the [MDG README and source](https://github.com/neoforged/ModDevGradle), the MDK, and MDG's own `testproject`.

| Capability | Status | Notes |
|---|---|---|
| Run types | `client`, `server`, `data`, `gameTestServer`, plus any number of named runs. Per run: `sourceSet`, `loadedMods`, JVM and system properties, `devLogin`, `taskBefore` | `gameTestServer` exits with the number of failed required tests ([docs](https://docs.neoforged.net/docs/1.21.1/misc/gametest)) |
| JUnit | `neoForge.unitTest { enable(); testedMod = mods.x }` | Combine with `net.neoforged:testframework:21.1.264`. Its `EphemeralTestServerProvider` hands a live `MinecraftServer` to JUnit tests. The jar also ships `EmptyTemplate`, `GameTestPlayer`, `ExtendedGameTestHelper`, `StructureTemplateBuilder` (checked in the downloaded jar). *(added)* `enable()` does not add JUnit itself. The JUnit Jupiter and launcher dependencies and `useJUnitPlatform()` must be declared (see §7.3) |
| Data generation | `data()` run on 1.21.1 | The MDK passes `--mod/--all/--output/--existing` |
| Parchment | `parchment { minecraftVersion; mappingsVersion }` or `gradle.properties` | MDG 2 requires both values (BREAKING_CHANGES.md) |
| Access transformers | Default `META-INF/accesstransformer.cfg` is auto-detected. `validateAccessTransformers = true`. Can be published and consumed | |
| Interface injection | `interfaceInjectionData.from(...)` | Dev-time only; needs Mixin (or ASM/coremods) at runtime |
| Jar-in-Jar | `jarJar` configuration with version ranges, local files and subprojects | |
| Multi-source-set / multi-project mods | `mods { x { sourceSet a; sourceSet project(':p').sourceSets.main } }`, `addModdingDependenciesTo` | MDG's own test project uses exactly this pattern |
| Vanilla mode | `neoFormVersion` instead of `version` | For loader-agnostic "common" modules |
| CI speed | `disableRecompilation`; on by default when `CI=true` (since 2.0.136) | |
| Configuration cache | Supported | The reference build stored a configuration-cache entry |
| Gradle floor | The trampoline enforces Gradle ≥ 8.8 (and Java ≥ 17) | `TrampolinePlugin.java` |
| macOS | Adds `-XstartOnFirstThread` automatically for client runs on macOS | `PrepareRunOrTest.java` |

**NeoGradle.** NeoGradle 7.1.39 (2026-09-15) is still maintained, and the mod generator still offers it. But NeoForge itself builds with MDG components, and the MDG MDK is the de facto default: it has 160 stars against 35 for the NeoGradle MDK. The [MDG 2 announcement](https://neoforged.net/news/moddevgradle2/) describes MDG as "focused on performance, simplicity, and ease of use". **Use MDG.**

### 1.4 Gradle, JDKs, configuration cache

- **Gradle versions.**
  - Current is 9.8.2 (2026-10-19). 9.8.1 is from 2026-10-07.
  - **9.2.1** (2025-11-17) is the wrapper in *every* current NeoForge MDK ([services.gradle.org](https://services.gradle.org/versions/all)). That covers 1.21.1, 26.1.2, 26.2 and 26.3, in both the MDG and NeoGradle flavours.
  - The MDG flavours all use MDG 2.0.148. MDG itself builds with Gradle 8.14.5.
- **Reference build on this M4 Pro.**
  - Setup: NeoForge 21.1.262 + MDG 2.0.148 + Gradle 9.2.1, from a cold start.
  - Result: `BUILD SUCCESSFUL in 1m 37s`, and the configuration-cache entry was stored.
  - The NeoForm pipeline took about 50 s of that (Vineflower decompile 13.9 s, recompile 12.4 s).
  - Gradle printed: "Deprecated Gradle features were used … incompatible with Gradle 10."
- **JVM support.** Gradle needs a JVM between 17 and 27 to run. Java 25 support (both running and toolchains) arrived in Gradle 9.1.0 ([compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)).
- **This machine.**
  - `/usr/libexec/java_home` resolves only Homebrew OpenJDK **23.0.2**, a non-LTS release that is out of support.
  - Homebrew's keg-only `openjdk@25` (25.0.4.1) is also installed, but `java_home` does not list it.
  - foojay provisioned **Temurin 21.0.12.1** (21.0.12.1+1, build date 2026-08-18) into `~/.gradle/jdks` for the toolchain.
  - Pin the daemon with `./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium` and commit `gradle/gradle-daemon-jvm.properties` ([docs](https://docs.gradle.org/current/userguide/gradle_daemon.html)).
  - Optional: JetBrains Runtime 21.0.11 (also available through foojay) for `-XX:+AllowEnhancedClassRedefinition` hot-swap.
  - **Inference:** a quarterly Java update may bring a newer 21.0.x shortly. Pin the toolchain's major version (21), not a patch release.

---

## 2. Fabric on 1.21.1

| Component | Latest usable for 1.21.1 | Date | License |
|---|---|---|---|
| Fabric Loader | 0.19.5 stable (0.20.0/0.20.1 are unstable) | 2026-08-28 | Apache-2.0 |
| Fabric API | **0.116.17+1.21.1** | 2026-09-01 | Apache-2.0 |
| Loom | **1.19.2**, plugin id **`net.fabricmc.fabric-loom-remap`** (for obfuscated versions ≤ 1.21.11) | 2026-10-19 | MIT |
| Yarn | 1.21.1+build.3 (final) | 2024-08-10 | CC0 |
| Parchment (via `loom.layered`) | 2024.11.17 | 2024-11-17 | CC0 |

Sources: [Fabric meta](https://meta.fabricmc.net/v2/versions/loader), [Fabric API Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml), [Loom Maven](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml), [Loom docs](https://docs.fabricmc.net/1.21.11/develop/loom).

- **Maintenance.**
  - The 0.116.x line for 1.21.1 has had 18 releases since 2025-05-06. In 2026: January, February, April ×2, May (security), July ×3, September ×2.
  - The [1.21.1 branch](https://github.com/FabricMC/fabric-api/tree/1.21.1) gets small backports.
  - On 2026-09-01 a HUD render events backport (#5541) was merged and reverted the same day. 0.116.16 shipped with it; 0.116.17 is the revert.
  - This is maintenance mode.
- **Toolchain constraint.** Loom 1.19.2's Gradle module metadata declares `org.gradle.jvm.version=25` and `org.gradle.plugin.api-version=9.8.0` ([.module](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.2/fabric-loom-1.19.2.module)). **Inference:** the latest Loom therefore needs Gradle ≥ 9.8 running on JDK 25, even for a 1.21.1 project. Fabric says support for "modding older game versions with the latest Loom is a very high priority" ([obfuscation post, 2025-10-31](https://fabricmc.net/2025/10/31/obfuscation.html)).
- **Mappings.**
  - Yarn "is no longer officially supported" from 26.1 on ([26.1 post](https://fabricmc.net/2026/03/14/261.html)). Fabric recommends Mojang names for all new mods.
  - On 1.21.1, though, Fabric still runs on *intermediary* names in production.
  - So a Fabric 1.21.1 mod faces a toolchain migration at the 26.x port:
    - remap Loom → no-remap Loom;
    - `modImplementation` → `implementation`;
    - Fabric API renames that "are not backwards compatible" (for example `ItemGroupEvents` → `CreativeModeTabEvents`).
- **Gaps on 1.21.1** (checked in the branch source):
  - No config API.
  - No chunk-ticket API.
  - No data maps.
  - HUD support is `HudRenderCallback` only.
  - The Data Attachment API is `@ApiStatus.Experimental`; its sync feature was added on 2024-11-12.
  - No client GameTest module (it arrived with 1.21.4).
  - No capability system; API Lookup and Transfer stand in for it.
- **Where Fabric is the better tool.** Small client or vanilla+ mods, fast tracking of new versions, and Fabric-centric packs (Fabulously Optimized, Cobblemon packs). None of these describes Strategos.

---

## 3. MinecraftForge 52.x and Quilt

- **MinecraftForge.**
  - Latest is `1.21.1-52.1.20` (2026-10-15); recommended is 52.1.0 ([promotions](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json)).
  - It is maintained: commits by LexManos, Paint_Ninja and Jonathing, network-list hardening in October 2026, and the ForgeGradle 7 MDK backported in March 2026 ([commits](https://github.com/MinecraftForge/MinecraftForge/commits/1.21.1)).
  - Adoption on 1.21.1 is marginal. Modrinth has 5,451 mods with Forge 1.21.1 builds, against 21,500 for NeoForge, and **25 Forge 1.21.1 modpacks against 2,178** for NeoForge.
  - The APIs have diverged from NeoForge since 1.20.2 (NeoForge reworked networking, capabilities and attachments).
  - **Verdict: no.**
- **Quilt.**
  - Quilt Loader is at 0.31.0-beta.4 (2026-09-04) and runs Fabric mods.
  - QSL stopped at `10.0.0-alpha.5+1.21.1` (2025-09-21). Quilted Fabric API stopped at 7.7.0 for 1.20.1 (2024-12-02).
  - Quilt Mappings were retired ([blog, 2026-02-03](https://quiltmc.org/en/blog/1/), post "Bringing Quilt into the non-obfuscated era").
  - **Verdict: no.**

---

## 4. Multi-loader options: real cost and benefit

| Option | What it is | Latest (date), license | Who uses it (evidence) |
|---|---|---|---|
| Architectury (API + Architectury Loom + plugin) | A common module built against vanilla, plus a module per platform. The API abstracts events, networking and registries | API **13.0.11** for 1.21.1 (2026-07-23, LGPL-3.0); Loom 1.17.493 (2026-09-12, MIT, a fork of Fabric Loom); plugin 3.5.170 (2026-09-14) | GitHub code search finds Architectury Loom in Terra, Geyser, Sodium Extra, Steam 'n' Rails, Oh The Biomes We've Gone, playerAnimator and others. Only Geyser was re-checked; the others are (unverified) |
| [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template/tree/1.21.1) | Gradle template only: `common` (MDG vanilla mode via NeoForm), `fabric` (Loom), `neoforge` (MDG), `forge`. Platform services through ServiceLoader. No runtime library | 1.21.1 branch, last commit 2026-10-01. Pins are stale (NeoForge 21.1.80, Fabric API 0.109.0, Loader 0.16.9). CC0-1.0 | Widely copied for libraries and smaller mods |
| Stonecutter | Comment-based preprocessor for multiple versions (and loaders) from one source tree | 0.9.8 (2026-08-31), LGPL-3.0 | Controlify, Zoomify and YACL were re-checked (each has a `stonecutter.gradle.kts`). Friends & Foes, AutoModpack and MidnightControls are (unverified) |
| Sinytra Connector (+ FFAPI) | Runs Fabric mods on NeoForge. A player-side tool, not a dev tool | Supports 1.21.1, 26.1.2, 26.2 (Modrinth), MIT | Means a NeoForge-only mod can still share a pack with many Fabric mods |

**What the large mods do on 1.21.1** (Modrinth per-loader files):
- NeoForge only: **Create 6.0.10** (2026-04-21), Mekanism 10.7.19, AE2 19.2.18, KubeJS 2101.7.2, Curios, Ars Nouveau, Iron's Spells, Create Aeronautics.
- Multi-loader: JEI, GeckoLib 4.9.3, Waystones, Cobblemon, Supplementaries, Sodium, Iris, Lithium, Distant Horizons, Jade, Better Combat, SmartBrainLib, YACL, owo-lib.
- The pattern: **libraries and client or performance mods go multi-loader; large gameplay systems stay on one loader.**
- For multiple MC versions, the big projects use one branch per version, not preprocessors. Mekanism has `1.21.x`, `26.1`, `26.2`, `26.3`; AE2 and Create have per-version branches too.

**Cost for Strategos.**
- About ten platform seams would each need two implementations and two test runs: registration, networking, attachments, config, chunk tickets, HUD layers, world-render hooks, in-world input and camera hooks, GameTests, and datagen.
- *(corrected)* Fabric API on 1.21.1 has nothing for three of these seams: config, chunk tickets and layered HUD. It also lacks data maps. All of these would have to be filled with your own code or third-party libraries.
- CI time and bug surface roughly double.
- **Inference:** about 20–35% extra effort through v1.

**Benefit.** Access to the Fabric 1.21.1 audience (19,665 mods), whose packs lean towards performance, vanilla+ and Cobblemon. An RTS overhaul is a game mode that players install deliberately, so cross-loader reach matters less than it does for a library.

**Verdict:** single loader. Keep the NeoForge glue in one package (`…platform.neoforge`) so that a later MultiLoader-style split stays mechanical. If a Fabric port is ever wanted, follow the MultiLoader-Template approach (each loader's official toolchain) rather than adding an Architectury runtime dependency.

---

## 5. The 1.21.1 ecosystem and API coverage

### 5.1 Numbers

Modrinth API, 2026-10-20.
- Counts are distinct projects with at least one file for that game version tagged with that loader.
- Facets are evaluated per file. For example, Create matches `[1.21.1, neoforge]` but not `[1.21.1, forge]`.
- "New" means the project was created after 2026-07-22.
- Many 1.20.1 Forge files also tag NeoForge (5,100 NeoForge-tagged 1.20.1 mods against 25,273 Forge).
- A re-query a few hours later moved every figure by 0.2% or less, for example 21,501 / 19,666 / 9,695 / 16,846 mods.

| | NeoForge 1.21.1 | NeoForge, any 26.x | Fabric 1.21.1 | Fabric, any 26.x |
|---|---|---|---|---|
| Mods | **21,500** | 9,690 (26.1.2: 8,147; 26.2: 6,847; 26.3: 4,554) | 19,665 | 16,840 |
| New mods in the last 90 days | **2,604** | 1,662 | 1,583 | 3,444 |
| Modpacks | **2,178** | 99 | 3,066 | 2,318 |
| New modpacks in the last 90 days | **342** | 20 | 141 | 488 |

- Across all loaders, 1.21.1 has 32,271 mods; any 26.x has 19,058.
- At least 8,894 projects ship both NeoForge and Fabric 1.21.1 builds. This is a lower bound from inclusion–exclusion.
- NeoForge's own [2025 retrospective](https://neoforged.net/news/2025-retrospection/) says 1.21.1 "still remains the most popular NeoForge version", with more than 16,000 mods. *(corrected)* On their Maven, the 1.21.1 NeoForm artifact "used up most of the bandwidth": it was requested over 10 million times. The post does not call it the most-downloaded file.
- **Where the big packs are.**
  - CurseForge: [ATM10](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10) (NeoForge 1.21.1; 8.3 released 2026-10-15; about 22.5M downloads *(corrected from 22.4M)*).
  - CurseForge: [ATM11](https://www.curseforge.com/minecraft/modpacks/all-the-mods-11) (NeoForge **26.1.2**; 0.12.0-beta released 2026-10-15; 1.1M downloads; billed as "Super early alpha").
  - Top Fabric 1.21.1 packs on Modrinth: Fabulously Optimized and the Cobblemon packs.
  - Modrinth download counts are lifetime totals across all versions.

### 5.2 API coverage for what an RTS needs

The NeoForge column was checked in the 21.1.262 sources; the Fabric column in the `fabric-api` 1.21.1 branch.

| Need | NeoForge 21.1 | Fabric API 0.116.17 (1.21.1) |
|---|---|---|
| Entities and renderers | `DeferredRegister`, `EntityRenderersEvent.RegisterRenderers/RegisterLayerDefinitions/AddLayers`, `IEntityWithComplexSpawn` (extra spawn data) | Vanilla registries plus `EntityRendererRegistry` and `EntityModelLayerRegistry`; no spawn-data helper |
| Networking | `RegisterPayloadHandlersEvent` → `PayloadRegistrar` (`playToClient/ToServer/Bidirectional`, `configuration*`, `versioned`, `optional`, `executesOn`). `PacketDistributor` covers player, near, dimension, all, `TrackingEntity(+AndSelf)` and `TrackingChunk`. `GenericPacketSplitter` splits large packets. Limits: clientbound ≤ 1 MiB, serverbound < 32 KiB ([docs](https://docs.neoforged.net/docs/1.21.1/networking/payload)) | `PayloadTypeRegistry`, `Server/ClientPlayNetworking`, `PlayerLookup` |
| Per-object state | **Data attachments** on `Entity`, `Level`, `LevelChunk`/`ChunkAccess` and `BlockEntity`. Supports `serialize(Codec)`, `copyOnDeath`, and **`sync(StreamCodec)` with a per-player predicate** (`sync(BiPredicate<IAttachmentHolder, ServerPlayer>, StreamCodec)`). Sync was backported 2025-07-31 (#2544) and shipped in 21.1.197. The predicate applies to the initial sync only from 21.1.264 | Data Attachment API (experimental) with `syncWith` and `AttachmentSyncPredicate` |
| Capabilities | `Block/Entity/ItemCapability`, `RegisterCapabilitiesEvent` | API Lookup and Transfer API |
| Datapack registries | `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec)` | `DynamicRegistries.register/registerSynced` |
| Data maps | `RegisterDataMapTypesEvent` / `DataMapType` (data-driven per-entry stats) | none |
| Config | `ModConfigSpec` with STARTUP, CLIENT, COMMON and SERVER types. *(corrected)* SERVER is loaded from the config folder, can be overridden per world (`<world>/serverconfig`) and is synced to clients. Built-in `ConfigurationScreen` ([docs](https://docs.neoforged.net/docs/1.21.1/misc/config)) | none (Cloth Config, YACL, owo-config, Forge Config API Port) |
| Chunk loading | **`TicketController`** plus `RegisterTicketControllersEvent`: persistent tickets per owner (block, entity or UUID) with a validation callback | Vanilla only: `addRegionTicket` with a custom `TicketType` (not persisted), or `setChunkForced` (persisted, but no owner) |
| Entity tracking | `PlayerEvent.StartTracking/StopTracking`. Vanilla `Entity#broadcastToPlayer` gates tracking (`ChunkMap.TrackedEntity#updatePlayer`). The gate is re-evaluated only when the entity changes chunk section, is added, or a player moves (see point 2 below) | `EntityTrackingEvents.START/STOP_TRACKING`, same vanilla gate |
| GameTest | `@GameTestHolder`, `@PrefixGameTestTemplate`, `RegisterGameTestsEvent`, `gameTestServer` run. The `@GameTestHolder` value must match the mod id (docs). The testframework adds `EmptyTemplate`, `GameTestPlayer` and helpers | `fabric-gametest-api-v1` (`FabricGameTest.EMPTY_STRUCTURE`); Loom `configureTests()` (experimental); no client GameTest |
| JUnit | MDG `unitTest` (mod loaded inside tests) plus `EphemeralTestServerProvider` (live server) | `fabric-loader-junit` (no server provider) |
| HUD | **`RegisterGuiLayersEvent`** (`registerAbove/Below/AboveAll/BelowAll`, `replaceLayer`, `wrapLayer`), 25 named `VanillaGuiLayers`, cancellable `RenderGuiLayerEvent.Pre` for hiding vanilla HUD | `HudRenderCallback` only *(corrected)*. A layered HUD API exists from Fabric API for 1.21.4 (`HudLayerRegistrationCallback`), reworked into `HudElementRegistry` in 1.21.6. The 1.21.1 backport was reverted |
| World overlays | `RenderLevelStageEvent`: 11 stages (`AFTER_SKY` … `AFTER_LEVEL`) and custom stages | `WorldRenderEvents` (10 events, `START` … `END`) |
| Camera | `ViewportEvent.ComputeCameraAngles` (yaw, pitch, roll), `ComputeFov`, `CalculateDetachedCameraDistanceEvent`. **Camera position has no hook**: needs a Mixin on `Camera#setup` or a camera-entity approach | Mixin for everything |
| Input | Cancellable `InputEvent.MouseButton.Pre` and `MouseScrollingEvent`, plus `Key` and `InteractionKeyMappingTriggered`. `RegisterKeyMappingsEvent` with `KeyConflictContext` and `KeyModifier`. `CalculatePlayerTurnEvent` and `MovementInputUpdateEvent` | `KeyBindingHelper`; in-world mouse handling needs Mixin (`ScreenMouseEvents` only works while a screen is open) |
| Datagen | `GatherDataEvent` plus the `data` run | `fabric-data-generation-api-v1` plus `configureDataGeneration()` |
| Mixin | Bundled (Mixin 0.16.4+mixin.0.8.7, MixinExtras 0.5.5) | Bundled |

**Points specific to an RTS:**
1. Neither loader exposes a camera-position event on 1.21.1, so the RTS camera needs a small Mixin either way.
2. `Entity#broadcastToPlayer` is vanilla and overridable. Strategos's own unit entities can use it as the gate for **server-authoritative fog of war** without a Mixin; vanilla entities would need one. *(corrected)* There is a caveat: `ChunkMap` re-checks the gate only in three cases:
   - the entity crosses a chunk-section boundary (`ChunkMap#tick`);
   - the entity is added;
   - the observing player moves (`ChunkMap#move`).

   A unit that becomes visible or hidden inside its section, watched by a stationary player, keeps its old tracking state until one of those happens. The fog-of-war system must therefore force a re-check whenever visibility changes. One way is an access transformer to the private `ChunkMap.entityMap`, then calling `TrackedEntity#updatePlayer` (a public method on a package-private class). That still needs no Mixin, but the override alone is not enough.
3. Attachment sync with a per-player predicate, together with 21.1.264's initial-sync fix, suits hidden per-player data.
4. Design serverbound packets as small intent commands. Cap list sizes, validate on the server, and keep clientbound snapshots under 1 MiB. NeoForge caps the initial capacity of decoded collections at 65,536 entries. It does not cap the element count, so validate counts yourself.

---

## 6. Version check: 1.21.1 against 26.x

### 6.1 Where 26.x stands

- **Releases.** 26.1 on 2026-03-24, 26.1.1 on 04-01, 26.1.2 on 04-09, 26.2 on 06-16, 26.3 on 09-15. 26.4 snapshots are running (snapshot-5 on 2026-10-20). 1.21.11 (2025-12-09) was the last obfuscated release; 26.x ships no mappings and requires Java 25 ([manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)).
- **NeoForge stable status.** The beta tag came off on 26.1.2 at 26.1.2.71 (2026-06-02), on 26.2 at 26.2.0.57 (08-09), and on 26.3 at 26.3.0.76 (10-11). The 26.1.x branch also receives automated backports (26.1.2.122 on 2026-10-15), including the same October network hardening as 21.1.262.
- **Ecosystem.** See §5.1. For NeoForge, new mod projects target 1.21.1 at roughly twice the rate of the largest single 26.x line. New modpacks target it at 17 times the rate of all 26.x combined. Fabric has already moved on to 26.x.
- **Big mods.**
  - Create has no official 26.x release. There is an unofficial "Create: Adapted" for 26.2 (on CurseForge).
  - Mekanism has 26.x branches but nothing on Modrinth.
  - AE2, GeckoLib, JEI and Curios have 26.x builds.
  - ATM11 on 26.1.2 is still beta.
  - **Inference:** 26.1.2 is the front-runner for the next stable modding version, but no one has said so officially.

### 6.2 What moving to 26.x gains and loses

| Gained on 26.x | Lost or risked |
|---|---|
| Unobfuscated game: readable stack traces in players' crash reports, official parameter names, no Parchment | A NeoForge ecosystem less than half the size; about 5% of the modpacks; several flagship content mods not ported yet |
| Java 25 (flexible constructor bodies, scoped values, module imports, stream gatherers, AOT cache, opt-in compact object headers) | A moving target: each quarterly release breaks a lot (26.2 "Too Many Rendering Changes", 26.3 "There's Always a Client Rewrite"), and no stable version has emerged yet |
| A modern renderer: Vulkan backend (26.2), Renderpearl API and order-independent transparency (26.3); gizmos for debug shapes (1.21.11) | Less documentation and fewer worked examples. **Inference:** the AI assistant's training data (cutoff June 2026) covers 1.21.1-era APIs well, 26.1 partly, and 26.2/26.3 not at all. The decompiled sources reduce but do not remove this gap |
| More data-driven vanilla systems: datapack villager trades (26.1), reloadable datapack registries (26.3) | 26.x needs Gradle ≥ 9.1 and a Java 25 toolchain everywhere |
| Longevity: the mod would be "current" when it ships in 2027–28 | |

### 6.3 What drives the cost of porting 1.21.1 → 26.x

Sizing: the NeoForge porting primers from [1.21.2 to 26.3](https://github.com/neoforged/.github/tree/main/primers) total **29,760 lines (1.87 MB)**. *(corrected precision)* They contain about **10,500 bullet entries naming an API element** (10,501 bullets at nesting depth ≤ 2 that start with a code span). That is a heuristic proxy for individual API changes, not an exact count.

| Version (date) | Main breaking changes (primer sections) | Strategos areas hit |
|---|---|---|
| 1.21.2/1.21.3 (2024-10) | **Entity render states**, shader rewrites, GUI render types (`blit` takes a RenderType function), equipment and consumables, recipes as registries, `ServerLevel` parameters | Every unit and building renderer; HUD drawing |
| 1.21.4 (2024-12) | **Client item definitions** (`assets/*/items/*.json`); particles through render types | Item and icon assets (regenerate via datagen) |
| 1.21.5 (2025-03) | **RenderPipeline rework**, model rework, **GameTest overhaul** (registry-based), **chunk ticket rework**, data-component getters, typed SavedData | Tests, base chunk-loading, overlays |
| 1.21.6–1.21.8 (2025-06/07) | **GUI prepare/render split** (`GuiRenderState`), Blaze3d changes, **`ValueInput`/`ValueOutput` replace direct NBT**, waypoints | Command card, minimap, all persistence |
| 1.21.9/1.21.10 (2025-09/10) | **Feature submission** (`SubmitNodeCollector`) for entities, block entities and particles; **input events as objects**; key-mapping categories. NeoForge: **transfer rework** (`IItemHandler` → `ResourceHandler`), FML API changes, `RenderHighlightEvent` removed | Renderers again; selection input; resource storage |
| 1.21.11 (2025-12) | `ResourceLocation` → `Identifier`; another rendering rewrite (samplers, `RenderTypes`); permissions overhaul; game rules shuffle. *(corrected)* Vanilla moves to JSpecify (`@NullMarked`); NeoForge's current branch uses JSpecify as well, but the version where NeoForge switched is (unverified) | Mechanical renames; overlays |
| 26.1 (2026-03) | Java 25, unobfuscated; `GuiGraphics` → `GuiGraphicsExtractor`; `ItemStackTemplate`; data-component initializers; dynamic render-layer selection | GUI, items |
| 26.2 (2026-06) | Vulkan backend, `GpuFormat`, vertex formats rewritten | Custom rendering |
| 26.3 (2026-09) | **SDL replaces GLFW** (input codes, text input); Renderpearl; OIT; reloadable datapack registries; loot, recipes and advancements become (reloadable datapack) registries | RTS input and camera; data |

NeoForge-side changes confirmed on today's default branch (26.3.x):
- `RenderLevelStageEvent` is now abstract, with a subclass per stage and a `LevelRenderState`.
- *(corrected)* `@GameTestHolder` and `@PrefixGameTestTemplate` are gone. `RegisterGameTestsEvent` still exists, but it has been rewritten for registry-based GameTests: `registerEnvironment(Identifier, TestEnvironmentDefinition…)` and `registerTest(Identifier, GameTestInstance)`.
- `TicketController`, `RegisterGuiLayersEvent` and testframework's `EphemeralTestServerProvider` still exist.

### 6.4 How to architect now to keep a future port small

1. **Keep the simulation core in pure Java (`core`, no `net.minecraft` imports).** It covers:
   - economy, gathering rates, construction queues, population, ages and techs;
   - combat and counters, formations, stances;
   - diplomacy, trade, victory conditions, AI planners;
   - territory and visibility on an abstract grid.

   Make it tick-driven and deterministic, tested with plain JUnit. It survives every port unchanged.
2. **Put the engine behind narrow ports:** world queries, entity bridge, pathing costs, persistence, network, render, UI, input and audio. Only their adapters live in `mod`.
3. **Persist through Codecs and DTO records.** On 1.21.6+, `ValueInput.read(key, codec)` and `ValueOutput.store(key, codec, value)` / `storeNullable(...)` take codecs directly *(corrected signatures)*. The NBT → ValueIO change therefore stays in one adapter.
4. **Networking.** Payload records with `StreamCodec`s in one package; small intent packets with size caps; batched deltas.
5. **Write rendering in "extract, then draw" style today.** Compute a per-frame render-state object from the unit, and draw only from that state. This mirrors 1.21.2+ render states and 1.21.9 submission, so the port becomes mechanical. Use only vanilla `RenderType`/`VertexConsumer` abstractions: no raw GL, no `RenderSystem` state hacks, and no custom shaders until they are needed.
6. **UI from immutable view-models** produced by `core`. Widgets draw only. This limits the impact of the 1.21.6 GUI split and the 26.1 extractor rename.
7. **Map inputs through your own action layer** (actions, not GLFW codes). This localises the 1.21.9 input-event change and the 26.3 SDL scancodes.
8. **Generate everything with datagen** (models, client items, lang, tags, loot, recipes). Keep units, buildings, techs and civilisations as Codec-backed datapack registries, so content data carries over almost unchanged.
9. **Keep game tests behind your own thin DSL** (arena, spawn, order, advance ticks, assert). Prefer JUnit with `EphemeralTestServerProvider` for integration tests. The 1.21.5 GameTest overhaul then only touches the DSL backend. So does NeoForge's 26.x rewrite of `RegisterGameTestsEvent` into registry-based `GameTestInstance`s with no `@GameTestHolder`.
10. **Keep Mixins minimal and inventoried** (target, reason, version notes). Prefer NeoForge events. Use MixinExtras `@WrapOperation`/`@ModifyExpressionValue` rather than `@Redirect`/`@Overwrite`.
11. **Avoid APIs that are already doomed.**
    - Wrap `IItemHandler` behind your own storage interface.
    - Use JSpecify `@NullMarked`/`@Nullable` from day one.
    - Create ids through a helper, so the `ResourceLocation` → `Identifier` rename touches one place.
    - Use no Java preview features.

### 6.5 Decision gate

Re-decide at the end of the first playable "core mechanics" milestone or in 2027-Q2, whichever comes first. Before deciding, do a time-boxed port spike of `mod` to the leading 26.x line to measure the real cost. Triggers to watch:
- (a) NeoForge or the community names a 26.x stable modding version.
- (b) ATM11 or another flagship pack leaves beta, and Create and Mekanism publish 26.x releases.
- (c) New NeoForge mods per 90 days for the leading 26.x line overtake 1.21.1. Today it is 1,131 for 26.1.2 against 2,604 for 1.21.1.
- (d) NeoForge 21.1 drops to security-only fixes for two or more quarters.

**Alternative if the owner reconsiders now:** NeoForge 26.1.2.122 + MDG 2.0.148 + Gradle ≥ 9.1 (the MDK uses 9.2.1) + Java 25 (Temurin 25.0.4.1), no Parchment. It avoids the big port but accepts quarterly churn and a thinner ecosystem. **Inference:** I'd only prefer it if ecosystem compatibility on 1.21.1 doesn't matter to the owner and they accept moving to whichever 26.x line becomes the stable one.

---

## 7. Recommendation

### 7.1 Exact pins (as of 2026-10-20)

| Item | Pin |
|---|---|
| Minecraft | 1.21.1 (Java 21, `java-runtime-delta`) |
| NeoForge | `21.1.264`: ≥ 21.1.262 for the October 2026 network hardening, ≥ 21.1.264 for the attachment initial-sync fix. `loader_version_range=[4,)` (FML 4.x) is valid because javafml reports FML 4.0 on 21.1. The MDK default `[1,)` works too *(clarified)* |
| ModDevGradle | `2.0.148` |
| Gradle wrapper | 9.2.1 (verified today). Move to 9.8.2 once `build`, `test`, `runGameTestServer` and a configuration-cache hit pass. NeoForge's own branch uses 9.8.1 with MDG 2.0.115 |
| Toolchain resolver | `org.gradle.toolchains.foojay-resolver-convention` `1.0.0` |
| Java | Toolchain 21 (today Temurin 21.0.12.1). Daemon pinned to 21 via `updateDaemonJvm`. Optional JBR 21.0.11 for hot-swap |
| Parchment | `minecraftVersion=1.21.1`, `mappingsVersion=2024.11.17` (final release) |
| Mixin | Use the copies bundled with NeoForge |
| Tests | JUnit Jupiter 5.14.x plus `net.neoforged:testframework:21.1.264`. MDG's own test project uses 5.14.1; the latest 5.x is 5.14.4. JUnit 6.1.3 is the newest major, but MDG's test project and `junit-fml` are on JUnit 5, so stay on 5.14.x |
| Gradle properties | `configuration-cache`, `caching` and `parallel` on; `-Xmx2G` (NeoForge's own `org.gradle.jvmargs`; the MDK uses `-Xmx1G`) |

### 7.2 Layout

```
strategos/
├─ settings.gradle        foojay 1.0.0; include 'core', 'mod'
├─ gradle/libs.versions.toml, gradle-daemon-jvm.properties
├─ core/                  java-library, Java 21, NO Minecraft; JUnit 5 (+ optional jqwik)
└─ mod/                   net.neoforged.moddev 2.0.148; NeoForge glue + client
   ├─ src/main            registration, events, payloads, codec adapters, render/UI/input adapters
   ├─ src/test            MDG unitTest + EphemeralTestServerProvider
   ├─ src/gametest        separate test mod 'strategos_gametest' (own neoforge.mods.toml)
   └─ src/generated       datagen output
```

Why not a single module: a separate `core` project enforces "no Minecraft" at compile time, runs in milliseconds without NeoForm setup, is easy for the AI assistant to test in isolation, and is untouched by ports. Why not a JiJ library: bundling `core` classes into the one mod jar avoids JPMS module-name and Jar-in-Jar selection issues.

### 7.3 `mod/build.gradle` sketch

Not run. It follows the MDK and MDG's own `testproject` patterns. Verify it at bootstrap. *(corrected)* The JUnit dependencies and `useJUnitPlatform()` were added: the MDG README requires them, and `unitTest { enable() }` does not add them.

```groovy
plugins { id 'java-library'; id 'net.neoforged.moddev' version '2.0.148' }
evaluationDependsOn(':core')
java.toolchain.languageVersion = JavaLanguageVersion.of(21)
sourceSets { gametest }
dependencies {
    implementation project(':core')
    testImplementation enforcedPlatform('org.junit:junit-bom:5.14.4')   // as in MDG's testproject
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation "net.neoforged:testframework:${neo_version}"
    gametestImplementation sourceSets.main.output
    gametestImplementation project(':core')
    gametestImplementation "net.neoforged:testframework:${neo_version}"
}
neoForge {
    version = neo_version                       // 21.1.264
    parchment { minecraftVersion = '1.21.1'; mappingsVersion = '2024.11.17' }
    validateAccessTransformers = true
    addModdingDependenciesTo sourceSets.gametest
    mods {
        strategos          { sourceSet sourceSets.main; sourceSet project(':core').sourceSets.main }
        strategos_gametest { sourceSet sourceSets.gametest }
    }
    runs {
        client { client() }
        server { server(); programArgument '--nogui' }
        data   { data() }                       // + MDK's --mod/--all/--output/--existing args
        gameTestServer {
            type = 'gameTestServer'
            sourceSet = sourceSets.gametest
            loadedMods = [mods.strategos, mods.strategos_gametest]
            systemProperty 'neoforge.enabledGameTestNamespaces', 'strategos_gametest'
        }
    }
    unitTest { enable(); testedMod = mods.strategos }
}
tasks.named('test', Test) { useJUnitPlatform() }
tasks.named('jar', Jar) { from(project(':core').sourceSets.main.output) }   // ship one jar
```

GameTest classes in `src/gametest` must use `@GameTestHolder("strategos_gametest")` (or `templateNamespace`). The holder value has to match the mod id or namespace for the tests to load under the namespace filter.

Fallback if the second-mod wiring gives trouble: keep the GameTests in `main` under their own namespace, filtered with `neoforge.enabledGameTestNamespaces`.

### 7.4 Tests and CI (what the AI assistant runs)

| Command | What it covers | Speed |
|---|---|---|
| `./gradlew :core:test` | Pure logic | Milliseconds |
| `./gradlew :mod:test` | NeoForge-bootstrapped JUnit, plus live-server tests | Seconds |
| `./gradlew :mod:runGameTestServer` | Headless GameTests; non-zero exit means failed required tests | Seconds to minutes |

- CI: GitHub Actions on Linux, Temurin 21, `gradle/actions/setup-gradle`. With `CI=true`, MDG skips recompilation.
- Client behaviour (camera, HUD) has **no client GameTest on 1.21.1 in either loader**. Unit-test the view-models and input mapping in `core`, and keep a scripted manual or screenshot check.
- Version bumps (NeoForge 21.1.x, MDG, Gradle) arrive as bot PRs (Renovate or Dependabot) and are gated by this pipeline. Never use floating versions.

### 7.5 Explicit "don'ts"

- Forge 52, Quilt, NeoGradle, an Architectury runtime dependency, multi-loader in v1.
- Stonecutter-style preprocessing for 1.21.1 ↔ 26.x. Use a branch per version, as Mekanism and AE2 do.
- Raw GL calls, or game logic inside Mixins.

---

## 8. Risks and unknowns

- **How long 1.21.1 stays maintained.** It depends on demand; there is no committed end date. Mitigation: the decision gate in §6.5 and the port-ready architecture in §6.4.
- **Gradle 9.8.x with MDG 2.0.148 is not tested here** (Gradle runs were forbidden). The evidence for it is indirect: NeoForge's own build uses 9.8.1, with the older MDG 2.0.115. MDG itself builds with Gradle 8.14.5, and the MDKs ship 9.2.1.
- **Licensing of prior art.**
  - The closest RTS prior art is [Reign of Nether](https://github.com/SoLegendary/reignofnether): Forge 1.20.1, **GPL-3.0-only**. Its Modrinth page was updated 2026-10-14, and the repo was pushed 2026-10-20.
  - [MineFortress](https://github.com/remmintan/minefortress): Fabric 1.20.1, MIT. *(corrected)* Its last Modrinth release is from 2025-07-05 and its last GitHub commit from 2025-08-09.
  - Villager Recruits: Forge 1.20.1, all rights reserved.
  - Copying GPL code would force a GPL-compatible license on Strategos; reading it for study is fine.

---

## Verification notes

The fact-check ran on 2026-10-20 between about 20:20 and 21:20 UTC. It used fresh primary-source lookups only. Raw evidence is saved under /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-platforms/v2/.

**What was checked**
- **Maven metadata and POM Last-Modified dates:**
  - NeoForge 21.1.x, 26.1.2, 26.2 and 26.3 lines (including where the beta tag came off);
  - MDG, NeoGradle userdev, testframework, FML 4.0.45;
  - Fabric Loader, Fabric API, Loom and its plugin markers, Yarn, Parchment;
  - Forge promotions, QSL, QFAPI, Quilt Loader;
  - Architectury API, Loom and plugin; Stonecutter; JUnit; the foojay plugin.
- **Gradle:** services.gradle.org (9.8.2, 9.8.1, 9.2.1); the 9.8.2 compatibility matrix; the daemon docs (`updateDaemonJvm`).
- **GitHub raw files and commits:**
  - all four MDG MDKs and the NeoGradle MDKs;
  - NeoForge 1.21.1 build files and recent commits, the #3670 patch and the #2544 backport;
  - NeoForge 26.3.x files (`RenderLevelStageEvent`, `RegisterGameTestsEvent`, `TicketController`, `RegisterGuiLayersEvent`, `EphemeralTestServerProvider`, transfer API);
  - the 26.1.x commit page;
  - MDG README, BREAKING_CHANGES, `TrampolinePlugin`, `PrepareRunOrTest`, testproject;
  - the Fabric API 1.21.1/1.21.3–1.21.8 branches (modules, HUD APIs, attachments, render events, commits);
  - the Loom `FabricApiExtension`; MultiLoader-Template 1.21.1;
  - branch lists for Mekanism, AE2 and Create; prior-art repos; license files.
- **Docs and blogs:**
  - NeoForge docs (payload, config, gametest);
  - NeoForged posts: security, 21.2, 21.9, transfer rework, 26.1, MDG 2, 2025 retrospective, discussion #199, the NAP HackMD notes;
  - Fabric posts (obfuscation, 26.1) and the Loom docs;
  - the Quilt blog; the JDK 25 project page.
- **Modrinth API:** all counts and 90-day counts, re-queried, plus a per-file facet test; per-project loaders and versions for every named mod; top-pack rankings. CurseForge pack figures came via the cfwidget mirror.
- **Decompiled 1.21.1 + NeoForge 21.1.262 sources:** every NeoForge class, method and event in §5.2. Also the vanilla payload limits, the `MAX_INITIAL_COLLECTION_SIZE` cap, `Camera#setup`, `Entity#broadcastToPlayer` and the `ChunkMap` tracking logic, the GameTest exit code and the namespace filter.
- **Local machine:** `java_home`, Homebrew JDKs, `~/.gradle/jdks` (Temurin 21.0.12.1+1), and the reference build log.
- **Porting primers:** 1.21.2–26.3 downloaded; lines and bytes counted; section headings and the keywords behind each table row checked.

**What changed**
1. **Fog of war (§5.2 point 2, recommendation 7).** Vanilla re-evaluates the `broadcastToPlayer` gate only on a section change, an entity add or player movement. A forced re-check (for example via an access transformer) is required.
2. **26.3.x GameTest API.** `RegisterGameTestsEvent` is not gone; it is now registry-based. Only `@GameTestHolder` and `@PrefixGameTestTemplate` were removed.
3. **Fabric layered HUD.** It exists from 1.21.4 (`HudLayerRegistrationCallback`), not 1.21.6. 0.116.16 briefly shipped the 1.21.1 backport.
4. **JSpecify in 1.21.11.** It is a vanilla change. NeoForge's current branch also uses JSpecify, but its switch version is unverified.
5. **Security attribution.** The NeoForged blog covers only 21.1.229. The 21.1.262 hardening is documented only by commits; 21.1.260 and 21.1.261 do not exist.
6. **Commit title vs. method.** The #3670 title says `syncToPlayer()`; the method is `sendToPlayer`.
7. **SERVER config.** It is per-world-overridable, not purely per-world.
8. **2025 retrospective wording.** The NeoForm artifact "used up most of the bandwidth"; the post does not call it the most-downloaded file.
9. **Small corrections:**
   - ATM10 has about 22.5M downloads, not 22.4M.
   - MineFortress: the last GitHub commit is 2025-08-09; 2025-07-05 is the last Modrinth release.
   - The ValueInput/ValueOutput signatures include a key.
   - "About 10,500 API changes" is a heuristic bullet count.
   - The multi-loader "four seams" sentence was internally inconsistent; data maps are not one of the ten seams.
10. **Build sketch.** JUnit dependencies and `useJUnitPlatform()` were added. Notes cover the `@GameTestHolder` namespace and `loader_version_range` (the MDK default is `[1,)`; `[4,)` is also valid).
11. **Additions:**
    - Homebrew `openjdk@25` 25.0.4.1 is installed but not registered with `java_home`.
    - JUnit 6.1.3 exists; 5.14.x was kept for compatibility with MDG's test project and `junit-fml`.
    - MDG itself builds with Gradle 8.14.5.
    - 26.1.x received the same October hardening as 21.1.262.

**Confirmed unchanged.** All version numbers and dates in the decision summary and §1–§3 were confirmed; Modrinth counts drifted by 0.2% or less. The MDG feature list and Gradle floor, the reference build figures, and every NeoForge and Fabric API name in §5.2 were also confirmed. So were the primer totals, the Mojang release dates and Java requirements, and the licenses.

**Still unverified**
- Most of the Architectury Loom user list (only Geyser was re-checked).
- Three of the six Stonecutter users (Friends & Foes, AutoModpack, MidnightControls).
- The NeoForge version that adopted JSpecify.
- Every item labelled **Inference**.

## Recommendations (after verification)

1. 1. Choose NeoForge as the single loader on 1.21.1 and start on NeoForge 21.1.264 (2026-10-19). It is the first build with both the October 2026 network hardening (21.1.262+) and the attachment initial-sync fix (21.1.264). Pin the exact version. For loader_version_range, either keep the MDK default [1,) or tighten it to [4,); javafml reports FML 4.0 on 21.1, so both work.
2. 2. Use ModDevGradle 2.0.148 with Mojang mappings plus Parchment 1.21.1/2024.11.17. Use a Java 21 toolchain: today Temurin 21.0.12.1 via foojay-resolver-convention 1.0.0, but pin the major version, not the patch. Pin the Gradle daemon with `./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium` and commit gradle/gradle-daemon-jvm.properties. Do not let the daemon float on Homebrew's EOL JDK 23, the only JDK java_home lists (Homebrew's keg-only openjdk@25 is not registered). Turn on configuration cache, build cache and parallel builds, and set -Xmx2G (NeoForge's own value; the MDK uses -Xmx1G).
3. 3. Start on Gradle wrapper 9.2.1, the default in every current NeoForge MDK and verified on this machine. In the bootstrap PR, upgrade to 9.8.2 only after `build`, `test`, `runGameTestServer` and a configuration-cache hit all pass. The evidence for 9.8.x is indirect: NeoForge's 1.21.1 branch uses 9.8.1 with the older MDG 2.0.115, and MDG itself builds with Gradle 8.14.5.
4. 4. Lay the project out as three parts. `core`: pure Java 21, no Minecraft, JUnit. `mod`: NeoForge/MDG; it includes the `core` source set in the mod and bundles its classes into the one jar. `gametest`: a source set run as a separate test mod (strategos_gametest) by runGameTestServer, with @GameTestHolder values equal to that mod id. Verify the gametest-as-mod wiring at bootstrap; the fallback is a namespace filter in main.
5. 5. Build the test pyramid from day one. (a) Fast JUnit in `core`. (b) MDG unitTest with explicit JUnit dependencies: JUnit BOM 5.14.4, junit-jupiter, junit-platform-launcher and test { useJUnitPlatform() }, since unitTest { enable() } does not add them. Add testframework's EphemeralTestServerProvider for server-backed integration tests. (c) A thin internal GameTest DSL on testframework helpers (EmptyTemplate, GameTestPlayer). (d) CI on GitHub Actions; CI=true makes MDG skip recompilation. Stay on JUnit 5.14.x, because MDG's test project and junit-fml are on JUnit 5 even though 6.1.3 exists. Bump dependencies through bot PRs gated by CI.
6. 6. Enforce the port-proofing rules: (a) keep simulation in `core`; (b) persist through Codec-based DTOs, which map directly onto 1.21.6+ ValueInput.read(key, codec) / ValueOutput.store(key, codec, value); (c) keep StreamCodec payloads in one package with size caps; (d) render in 'extract state, then draw' style using only vanilla RenderType/VertexConsumer (no raw GL); (e) build UI from immutable view-models; (f) map inputs to your own actions; (g) generate all assets with datagen; (h) define content as datapack registries; (i) keep Mixins minimal and inventoried; (j) wrap IItemHandler, ResourceLocation creation and nullability (use JSpecify).
7. 7. Implement server-side fog of war by overriding Entity#broadcastToPlayer on Strategos's own unit entities, plus a forced tracking re-check whenever visibility changes. Vanilla re-evaluates that gate only when an entity changes chunk section, is added, or a player moves. An access transformer to ChunkMap.entityMap and TrackedEntity#updatePlayer is enough; no Mixin is needed. Combine it with per-player-filtered attachment sync, which needs 21.1.264 or later for the filter to apply on initial sync. Keep serverbound packets as small, validated intent commands. NeoForge's limits are under 32 KiB serverbound and at most 1 MiB clientbound. Only the initial capacity of decoded collections is capped (65,536), so validate element counts yourself.
8. 8. Do not go multi-loader in v1, and do not use Forge 52, Quilt, NeoGradle or an Architectury runtime dependency. Keep loader glue in one package so a MultiLoader-Template-style split stays possible if real demand appears.
9. 9. Set a formal version gate at the end of the first playable core-mechanics milestone or in 2027-Q2, whichever comes first. Before it, do a time-boxed port spike of `mod` to the leading 26.x line; today that is 26.1.2, with the most NeoForge 26.x mods and ATM11. Triggers: (a) an official or community stable 26.x version; (b) ATM11 leaving beta and Create/Mekanism publishing 26.x; (c) new 26.x NeoForge projects overtaking 1.21.1; (d) 21.1 dropping to security-only fixes.
10. 10. Keep a branch per Minecraft version for the eventual port, as Mekanism and AE2 do, rather than a preprocessor such as Stonecutter. Follow the NeoForge porting primers for each version step. Budget for the GameTest API change: on 26.x, @GameTestHolder is gone and RegisterGameTestsEvent registers GameTestInstances into registries.

## Fact-check verdicts

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | NeoForge 21.1.264 (2026-10-19) is the latest 1.21.1 build; 63 builds since 21.1.200 (2025-08-09), 10 of them in October 2026, with network-hardening fixes in 21.1.229 (May 2026) and 21.1.262 (2026-10-15). | The numbers are right. Two additions: 21.1.260 and 21.1.261 were never published. The cited NeoForged blog post (2026-05-11) covers only 21.1.229. The 21.1.262 hardening has no blog post or advisory; the only record of it is three commits dated 2026-10-14. | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (lastUpdated 20261020022939; 63 versions >= 21.1.200; latest 21.1.264). POM Last-Modified: 21.1.264 Mon 19 Oct 2026 03:40:46 GMT; 21.1.200 Sat 9 Aug 2025; 21.1.229 Mon 11 May 2026; 21.1.262 Thu 15 Oct 2026 01:43 GMT. Commits d1ee810bc8 (2026-05-11, #3164) and bd7aff4cb0 / 0e98dadf67 / 00c6a3fd23 (2026-10-14) on https://github.com/neoforged/NeoForge/commits/1.21.1. https://neoforged.net/news/mitigating-vulnerabilities-network/ names only 26.1.2.44-beta and 21.1.229. |
| confirmed | NeoForge has no formal LTS and accepts non-breaking backports to any past version except 1.20.1. PRs must land on the latest branch first. The 26.1 post's view that 26.1 will replace 1.21.1 is a personal guess. | None. Note that the HackMD 'NAP 2025 Team notes' page carries no date. | https://hackmd.io/@neoforged/rJ_d8ZxLxl (TG: 'The idea of LTS is not a thing...minus 1.20.1'; Orion: 'LTS is determined by community engagement'). https://neoforged.net/news/21.2release/ (2024-10-22: PRs must first be accepted into 1.21.x; 'continue to actively maintain the 1.21.1 version...until the release of Minecraft 1.22'). https://neoforged.net/news/26.1release/ (2026-03-24, Technici4n: 'personal educated guess, not an official statement'). |
| confirmed | Modrinth: 21,500 mods and 2,178 modpacks for NeoForge 1.21.1 against 9,690 mods and 99 modpacks for NeoForge 26.x. New in the last 90 days: 2,604 mods and 342 packs (1.21.1) against 1,662 mods and 20 packs (26.x). | A re-query a few hours later gave 21,501 / 2,178 / 9,695 / 99, with 2,600 / 340 / 1,665 / 20 new. That is normal drift of 0.2% or less. Facets are indeed evaluated per version file. | https://api.modrinth.com/v2/search with facets project_type, versions, categories and created_timestamp>2026-07-22. Script: /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-platforms/v2scripts/mrcount.py; output: /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-platforms/v2/mr-counts.txt. Create appears for [1.21.1, neoforge] but not for [1.21.1, forge]. |
| confirmed | NeoForge leads Fabric on 1.21.1 (21,500 vs 19,665 mods). Fabric leads on 26.x (16,840 vs 9,690) and in new projects (3,444 vs 1,662). | Re-query: Fabric 1.21.1 19,666; Fabric 26.x 16,846; new in 90 days 3,443 (Fabric 26.x) vs 1,665 (NeoForge 26.x). | Same Modrinth re-query (mr-counts.txt). |
| confirmed | The major content mods are NeoForge-only on 1.21.1 (Create 6.0.10, Mekanism 10.7.19, AE2 19.2.18, KubeJS, Curios, Ars Nouveau, Iron's Spells). ATM10 is NeoForge 1.21.1 (8.3 on 2026-10-15, 22.4M downloads). ATM11 on 26.1.2 is still beta. | ATM10 has 22,489,861 downloads, which is about 22.5M rather than 22.4M. Everything else holds. | Modrinth project and version API: create 6.0.10+mc1.21.1 (2026-04-21, neoforge only); mekanism 10.7.19.85; ae2 19.2.18; kubejs 2101.7.2-build.377; curios, ars-nouveau, irons-spells-n-spellbooks and create-aeronautics all have only neoforge files for 1.21.1. CurseForge data via the api.cfwidget.com mirror: ATM10 total 22,489,861, file 'All the Mods 10-8.3' 2026-10-15 [1.21.1, NeoForge]; ATM11 total 1,136,276, '0.12.0-beta' 2026-10-15 [NeoForge, 26.1.2], summary 'Super early alpha for 26.1.2'. |
| confirmed | MDG 2.0.148 (2026-09-29, LGPL-2.1) supports client, server, data and gameTestServer runs, unitTest, Parchment, AT validation, interface injection, jarJar, multi-source-set and multi-project mods, vanilla mode and the configuration cache. It requires Gradle 8.8 or newer. | None. | MDG maven-metadata (release 2.0.148, lastUpdated 20260929104918; POM Last-Modified 2026-09-29). https://github.com/neoforged/ModDevGradle README: run types; unitTest; 'As of MDG 2.0.136, this pipeline will be used by default...if the CI environment variable is true'; neoFormVersion; jarJar; interfaceInjectionData; accesstransformer.cfg. BREAKING_CHANGES.md: 'Specifying only the Minecraft version or only the mapping version will now fail'. src/java8/.../TrampolinePlugin.java: MIN_GRADLE_VERSION = 8.8. GitHub API: license LGPL-2.1. |
| confirmed | The reference build (NeoForge 21.1.262, MDG 2.0.148, Gradle 9.2.1) succeeded from cold in 1m37s, stored a configuration-cache entry, and warned about features incompatible with Gradle 10. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-build.log: 'BUILD SUCCESSFUL in 1m 37s', 'Total runtime: 50.25s' (decompile 13.92s, recompile 12.41s), 'Configuration cache entry stored.', 'incompatible with Gradle 10'. |
| confirmed | All current NeoForge MDKs (1.21.1, 26.1.2, 26.3) ship Gradle 9.2.1 with MDG 2.0.148. NeoForge's own 1.21.1 branch has used Gradle 9.8.1 since 2026-10-10. The latest Gradle is 9.8.2 (2026-10-19). | This also holds for the 26.2 MDK and for the NeoGradle-flavoured MDKs. | raw.githubusercontent.com/NeoForgeMDKs/MDK-{1.21.1,26.1.2,26.2,26.3}-ModDevGradle/main: gradle-9.2.1-bin.zip and moddev 2.0.148; the NeoGradle MDKs also use 9.2.1. raw NeoForge 1.21.1 gradle-wrapper.properties: gradle-9.8.1-bin.zip, from commit 289ef53a1e 'Update Gradle and GradleUtils (#3622)' on 2026-10-10. moddevgradle_plugin_version=2.0.115 (commit c8758d62e0, 2025-10-11). https://services.gradle.org/versions/current: 9.8.2, buildTime 20261019162926; 9.8.1 is 20261007; 9.2.1 is 20251117. |
| confirmed | Parchment for 1.21.1 ended at 2024.11.17 (CC0) and is not needed from 26.1 on. | The NeoForge 26.1 post says Parchment can be dropped but may be kept for its javadocs. | https://maven.parchmentmc.org/org/parchmentmc/data/parchment-1.21.1/maven-metadata.xml (redirects to ldtteam.jfrog.io): release 2024.11.17, lastUpdated 20241117123836. ParchmentMC/Parchment LICENSE.txt is CC0 1.0. https://neoforged.net/news/26.1release/. |
| confirmed | Fabric on 1.21.1: Loader 0.19.5 (2026-08-28), Fabric API 0.116.17+1.21.1 (2026-09-01), Loom 1.19.2 (2026-10-19) under net.fabricmc.fabric-loom-remap. Loom 1.19.2 needs JVM 25 and plugin API 9.8.0. Yarn is unsupported from 26.1. | None. | https://meta.fabricmc.net/v2/versions/loader (0.20.1 and 0.20.0 stable=false, 0.19.5 stable=true); loader 0.19.5 POM 28 Aug 2026. fabric-api 0.116.17+1.21.1 POM Tue 01 Sep 2026 16:34. Loom maven-metadata release 1.19.2 (POM 19 Oct 2026); plugin marker net.fabricmc.fabric-loom-remap at 1.19.2. fabric-loom-1.19.2.module: org.gradle.jvm.version=25, org.gradle.plugin.api-version=9.8.0. https://docs.fabricmc.net/1.21.11/develop/loom: -remap is for 1.21.11 or older. https://fabricmc.net/2026/03/14/261.html: 'Yarn is no longer officially supported by Fabric'. |
| confirmed | Fabric API on 1.21.1 has no config API, chunk-ticket API, data maps, layered HUD API (a backport was reverted on 2026-09-01) or client GameTest module. Its Data Attachment API, sync included, is experimental. | 0.116.16 actually shipped the HUD backport; 0.116.17 is the revert. The current 1.21.1 line therefore has HudRenderCallback only. | raw FabricMC/fabric-api/1.21.1/settings.gradle has no such modules. HudRenderCallback.java returns 200; HudLayerRegistrationCallback.java and hud/HudElementRegistry.java return 404. Commits ebf276f36b '[1.21.1] Hud Render Events Backport (#5541)' (2026-09-01 10:11) and f44c2eea0d 'Revert ... (#5563)' (16:24); 0.116.16 POM at 10:22, 0.116.17 at 16:34. AttachmentRegistry.java:44 @ApiStatus.Experimental, syncWith(PacketCodec, AttachmentSyncPredicate) at line 160. Commit 8e331c5709 'Data Attachment Sync API (#4049)' 2024-11-12. fabric-client-gametest-api-v1 first appears in the 1.21.4 branch. |
| corrected | NeoForge 21.1 provides synced attachments with a per-player predicate on entities, levels, chunks and block entities (backported 2025-07-31), TicketController, RegisterGuiLayersEvent, 11 render stages, camera-angle and FOV events, cancellable mouse events, data maps, datapack registries, and ModConfigSpec with per-world SERVER configs synced to clients. | Everything checks out except the config wording. In 1.21.1 a SERVER config is loaded from the global config folder and can be overridden per world in <world>/serverconfig; it is synced to clients. It is not purely per-world. | Holders: /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/Entity.java:131, .../world/level/Level.java:79, .../level/block/entity/BlockEntity.java:30, .../level/chunk/ChunkAccess.java:60. neoforge/attachment/AttachmentType.java:184, 224, 261, 273 (sync with BiPredicate). common/world/chunk/TicketController.java:47-67 plus ForcedChunkManager reinstatePersistentChunks. RegisterGuiLayersEvent.java:47-107. VanillaGuiLayers has 25 constants. RenderLevelStageEvent has 11 stages, plus RegisterStageEvent at :132. ViewportEvent.java:254 and :327. InputEvent.java:99 and :136 (ICancellableEvent). ModConfig$Type {COMMON, CLIENT, SERVER, STARTUP} in fancymodloader loader-4.0.45.jar. network/ConfigSync.java:26. https://docs.neoforged.net/docs/1.21.1/misc/config: SERVER 'Can be overridden for each world', 'Synced across the network to the client'. |
| confirmed | testframework 21.1.264 ships EphemeralTestServerProvider plus EmptyTemplate, GameTestPlayer and ExtendedGameTestHelper. EphemeralTestServerProvider still exists on 26.3.x. | None. Note that testframework is itself a mod (modId testframework). | https://maven.neoforged.net/releases/net/neoforged/testframework/21.1.264/testframework-21.1.264.jar (Last-Modified 19 Oct 2026) contains gametest/EmptyTemplate, GameTestPlayer, ExtendedGameTestHelper, StructureTemplateBuilder and junit/EphemeralTestServerProvider; META-INF/neoforge.mods.toml modId=testframework. raw NeoForge 26.3.x testframework/src/main/java/net/neoforged/testframework/junit/EphemeralTestServerProvider.java returns 200. |
| corrected | No camera-position hook on 1.21.1 in either loader, so the RTS camera needs a Mixin on Camera#setup or a camera entity. Overriding Entity#broadcastToPlayer, which gates ChunkMap.TrackedEntity#updatePlayer, gives custom units server-side fog of war without a Mixin. | The camera part is confirmed. The fog-of-war part needs a caveat. Vanilla re-evaluates the gate only when the entity crosses a chunk-section boundary (ChunkMap#tick), when it is added, or when the observing player moves (ChunkMap#move). A unit whose visibility flips inside its section, watched by a stationary player, keeps its old state. The mod must force a re-check, for example through an access transformer to the private ChunkMap.entityMap and the package-private TrackedEntity#updatePlayer. That still needs no Mixin, but the override alone is not enough. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/client/Camera.java:47-68 (posts ComputeCameraAngles only; position comes from the entity). Entity.java:3021 broadcastToPlayer returns true. server/level/ChunkMap.java: :150 private entityMap; :1278 package-private class TrackedEntity; :1327-1345 updatePlayer gate (:1335); tick() calls updatePlayers only when the SectionPos changes (:1164) or for moved players (:1180); move(ServerPlayer) at :1027/:1029. ViewportEvent has only RenderFog, ComputeFogColor, ComputeCameraAngles and ComputeFov. |
| confirmed | MinecraftForge 1.21.1-52.1.20 (2026-10-15) is maintained but marginal: 25 Forge 1.21.1 modpacks against 2,178 for NeoForge. | None. | https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json: 1.21.1-latest 52.1.20, recommended 52.1.0. POM Last-Modified 15 Oct 2026. Commits on 1.21.1: LexManos 'Add protection against large network channel lists' (2026-10-15); Jonathing 'Migrate the MDK to ForgeGradle 7' (2026-03-18); Paint_Ninja (2026-03-17). Modrinth: Forge 1.21.1 has 5,451 mods and 25 modpacks. |
| confirmed | Quilt is not viable: QSL stopped at 10.0.0-alpha.5+1.21.1 (2025-09-21), QFAPI at 7.7.0 for 1.20.1 (2024-12-02), Quilt Mappings retired (2026-02-03), loader at 0.31.0-beta.4. | None. | maven.quiltmc.org: qsl latest 10.0.0-alpha.5+1.21.1, lastUpdated 20250921222943; quilted-fabric-api 7.7.0+0.92.2-1.20.1, lastUpdated 20241202035640; quilt-loader 0.31.0-beta.4, lastUpdated 20260904212919. https://quiltmc.org/en/blog/1/: 'Bringing Quilt into the non-obfuscated era', 2026-02-03 ('Goodbye, Quilt Mappings'). |
| confirmed | The NeoForge porting primers from 1.21.2 to 26.3 total 29,760 lines and list about 10,500 API changes, with the named rewrites in each version. | 29,760 lines and 1,867,831 bytes are exact. '~10,500 API changes' is a heuristic: it counts bullets at nesting depth 2 or less that start with a code span (10,501). It is not an exact count of changes. | raw.githubusercontent.com/neoforged/.github/main/primers/{1.21.2,1.21.4,...,26.3}/index.md downloaded to /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-platforms/v2/primers. Section headings checked: Entity Render States, Gui Render Types (Function<ResourceLocation, RenderType>), Tickets, The Game Test Overhaul, Render Pipeline Rework, GUI Changes (GuiRenderState), Generic Encoding and Decoding (ValueInput/ValueOutput), Feature Submissions (SubmitNodeCollector), Input Handling Consolidation, ResourceLocation to Identifier, Gizmos, GuiGraphics to GuiGraphicsExtractor, Vulkan/GpuFormat, LWJGL with SDL, Renderpearl/OIT. |
| confirmed | NeoForge has used Mojang names and stopped reobfuscating mods since 1.20.2. A Fabric 1.21.1 mod runs on intermediary names and must move to no-remap Loom and the renamed Fabric API. | None. | https://github.com/neoforged/NeoForge/discussions/199 ('1.20.2 Release', 2023-10-27): 'swapping out SRG names in favor of using Mojang's Mapping Set'; 'mods don't get obfuscated anymore in 20.2'. https://fabricmc.net/2026/03/14/261.html: new net.fabricmc.fabric-loom, modImplementation becomes implementation, renames 'not backwards compatible' (ItemGroupEvents to CreativeModeTabEvents). |
| confirmed | Libraries and client or performance mods go multi-loader; big content mods stay single-loader on NeoForge. Big mods use a branch per MC version (Mekanism 1.21.x/26.1/26.2/26.3, AE2), not preprocessors. | None. | Modrinth per-loader files: jei, geckolib 4.9.3, waystones, cobblemon, supplementaries, sodium, iris, lithium, distanthorizons, jade, better-combat, smartbrainlib, yacl and owo-lib all have both fabric and neoforge for 1.21.1. GitHub branches: Mekanism has 1.21.x, 1.21.11, 26.1, 26.2, 26.3; AE2 has 1.21.1, 1.21.4 ... 1.21.11, 26.1, main; Create has mc1.21.1/dev etc. |
| confirmed | NeoForge 26.1.2 stable since 26.1.2.71 (2026-06-02), current 26.1.2.122 (2026-10-15), with automated backports on 26.1.x. 26.2 stable since 2026-08-09, 26.3 since 2026-10-11. 26.4 snapshots ongoing. | None. 26.1.x also received the same three network-hardening commits on 2026-10-14. | maven-metadata: 26.1.2.70-beta then 26.1.2.71; 26.2.0.56-beta then 26.2.0.57; 26.3.0.75-beta then 26.3.0.76. POMs dated 2 Jun, 9 Aug and 11 Oct 2026; 26.1.2.122 at 15 Oct 17:46 UTC. https://github.com/neoforged/NeoForge/commits/26.1.x shows neoforged-automation[bot] 'Backport to 26.1.x' commits. version_manifest_v2.json: latest snapshot 26.4-snapshot-5 (2026-10-20). |
| confirmed | On NeoForge 1.21.1 clientbound payloads are at most 1 MiB and serverbound payloads under 32 KiB. Decoded collections have their initial capacity capped at 65,536 entries. | None. Only the initial capacity is capped, not the element count. | https://docs.neoforged.net/docs/1.21.1/networking/payload ('at most 1 MiB', 'less than 32 KiB'). mc-src-1.21.1/net/minecraft/network/protocol/common/ClientboundCustomPayloadPacket.java:36 (1048576); ServerboundCustomPayloadPacket.java:16 (32767); network/codec/ByteBufCodecs.java:44 MAX_INITIAL_COLLECTION_SIZE = 65536; network/FriendlyByteBuf.java:125 and :171. |
| confirmed | 1.21.1 runs on Java 21 (java-runtime-delta), all 26.x need Java 25, and Gradle 9.1.0+ is needed for Java 25. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/version.json (java_component java-runtime-delta, java_version 21). The 26.1 and 26.3 version JSONs have javaVersion majorVersion 25 (java-runtime-epsilon) and no client_mappings; 1.21.11 still has mappings. https://docs.gradle.org/current/userguide/compatibility.html (9.8.2 docs): Java 25 from 9.1.0; Gradle runs on JVM 17 to 27. |
| corrected | On NeoForge's 26.3.x default branch, GameTestHolder and RegisterGameTestsEvent are gone. | @GameTestHolder and @PrefixGameTestTemplate are gone. RegisterGameTestsEvent still exists, rewritten for registry-based GameTests: registerEnvironment(Identifier, TestEnvironmentDefinition...) and registerTest(Identifier, GameTestInstance). | raw.githubusercontent.com/neoforged/NeoForge/26.3.x: src/main/java/net/neoforged/neoforge/gametest/GameTestHolder.java and PrefixGameTestTemplate.java return 404; src/main/java/net/neoforged/neoforge/event/RegisterGameTestsEvent.java returns 200 with the registry-based methods. Copy at /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-platforms/v2/nf263/RegisterGameTestsEvent.java. |
| confirmed | On 26.3.x, RenderLevelStageEvent is abstract with one subclass per stage and a LevelRenderState. TicketController, RegisterGuiLayersEvent and EphemeralTestServerProvider still exist. | None. | raw 26.3.x src/client/java/net/neoforged/neoforge/client/event/RenderLevelStageEvent.java:56 'public abstract class RenderLevelStageEvent', with subclasses AfterSky, AfterOpaqueBlocks, ... and getLevelRenderState(). TicketController.java, client/event/RegisterGuiLayersEvent.java and testframework EphemeralTestServerProvider.java all return 200. |
| corrected | In Fabric, a layered HUD exists from 1.21.6. | A layered HUD API (HudLayerRegistrationCallback) exists from Fabric API for 1.21.4. It was reworked into HudElementRegistry in 1.21.6. On 1.21.1 there is only HudRenderCallback. | raw FabricMC/fabric-api: on branches 1.21.4 and 1.21.5, fabric-rendering-v1/.../HudLayerRegistrationCallback.java returns 200 and hud/HudElementRegistry.java 404. On 1.21.6 and 1.21.8 it is the reverse. On 1.21.3 both return 404. |
| corrected | 1.21.11 (primer table): NeoForge moves to JSpecify. | The 1.21.11 primer documents vanilla Minecraft moving to JSpecify (@NullMarked). NeoForge's current 26.3.x sources also use JSpecify, while 1.21.1 uses javax.annotation and Mojang's annotations. The exact NeoForge version that switched was not verified. | primers/1.21.11 '### JSpecify Annotations': 'Mojang has moved from using a mix of their own annotations to those available in JSpecify'. raw NeoForge 26.3.x gametest/package-info.java has @NullMarked (org.jspecify); the 1.21.1 package-info.java uses javax.annotation.ParametersAreNonnullByDefault. |
| confirmed | 1.21.9/21.9 NeoForge: transfer rework (IItemHandler to ResourceHandler), FML API changes, RenderHighlightEvent removed, key-mapping categories. | None. | https://neoforged.net/news/21.9release/ (2025-09-30): 'The old RenderHighlightEvent was removed', 'large update and refactor of FML', 'Key mapping categories...are now a KeyMapping.Category record'. https://neoforged.net/news/21.9-transfer-rework/: IItemHandler is succeeded by ResourceHandler<ItemResource>. raw 26.3.x transfer/ResourceHandler.java returns 200; items/IItemHandler.java returns 404. |
| confirmed | 26.3: SDL replaces GLFW, Renderpearl, OIT, reloadable datapack registries; loot, recipes and advancements become registries. | None. Precisely, they become reloadable datapack registries. | primers/26.3: '### LWJGL with SDL' (input codes changed); Renderpearl (73 mentions), OIT; '### Reloadable Datapack Registries': 'LootTables, LootItemConditions, ..., Advancements, and Recipes are now datapack registries'. |
| corrected | NeoForge's 2025 retrospective says the 1.21.1 NeoForm artifact was the most-downloaded file on their Maven. | The post says it 'used up most of the bandwidth' and was requested over 10 million times. The '1.21.1 still remains the most popular NeoForge version' and 'over 16000 mods' quotes are correct. | https://neoforged.net/news/2025-retrospection/ (2026-01-01). |
| corrected | MineFortress (Fabric 1.20.1, MIT) was last updated 2025-07-05. | 2025-07-05 is the last Modrinth update. The last GitHub commit is 2025-08-09. | Modrinth project minefortress: updated 2025-07-05, MIT, fabric, 1.20.1/1.20.2. GitHub remmintan/minefortress latest commit 2025-08-09T23:02:52Z. |
| confirmed | Reign of Nether is Forge 1.20.1, GPL-3.0-only, updated 2026-10-14. Villager Recruits is Forge 1.20.1, all rights reserved. | None. The Reign of Nether GitHub repo also received a push on 2026-10-20. | Modrinth reign-of-nether-rts: license GPL-3.0-only, forge, latest game version 1.20.1, updated 2026-10-14. GitHub SoLegendary/reignofnether: default branch 1.20.1-dev, LICENSE.txt is GPLv3, pushed_at 2026-10-20. Modrinth villager-recruits: LicenseRef-All-Rights-Reserved, forge, up to 1.20.1. |
| confirmed | Pin loader_version_range=[4,) (FML 4.x). | [4,) is valid, but it is not the MDK default. The MDK uses [1,), and NeoForge's own testframework mod uses [1,). Either works on 21.1. | MDK-1.21.1-ModDevGradle gradle.properties: loader_version_range=[1,). NeoForge 21.1.264 .module requires fancymodloader loader 4.0.45. loader-4.0.45.jar manifest Implementation-Version 4.0; BuiltInLanguageLoader#version() reads the jar version or FMLLoader.versionInfo().fmlVersion(). |
| confirmed | 21.1.264 bundles FML 4.0.45, Mixin 0.16.4+mixin.0.8.7 and MixinExtras 0.5.5, bumped 2026-10-03/04. | None. | https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264.module dependencies. Commits ea9161e47b (2026-10-03), 411eb80132 and 003d690a82 (2026-10-04). loader-4.0.45.pom Last-Modified 3 Oct 2026. |
| confirmed | java_home resolves only Homebrew OpenJDK 23.0.2. foojay provisioned Temurin 21.0.12.1. JBR 21.0.11 and Temurin 25.0.4.1 are available. | Addition: Homebrew's keg-only openjdk@25 (25.0.4.1) is also installed, but java_home does not list it. | '/usr/libexec/java_home -V' lists only 23.0.2 Homebrew. ~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.12.1+1/Contents/Home/release has JAVA_VERSION=21.0.12.1, date 2026-08-18. /opt/homebrew/opt/openjdk@25/bin/java -version reports 25.0.4.1. foojay disco API: temurin 21 latest 21.0.12.1+1, jetbrains 21.0.11, temurin 25 latest 25.0.4.1+1. |
| confirmed | Gradle needs a JVM between 17 and 27. Pin the daemon with ./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium and commit gradle/gradle-daemon-jvm.properties. | None. The task needs toolchain download repositories configured; the foojay plugin in settings.gradle provides them. | https://docs.gradle.org/current/userguide/compatibility.html. https://docs.gradle.org/current/userguide/gradle_daemon.html (example '--jvm-version=17 --jvm-vendor=adoptium', file gradle/gradle-daemon-jvm.properties). Plugin portal: foojay-resolver-convention latest is 1.0.0. |
| confirmed | JUnit Jupiter 5.14.x: MDG's own test project uses 5.14.1; the latest is 5.14.4. | Addition: JUnit 6.1.3 is the newest major. junit-fml 4.0.45 imports JUnit BOM 5.10.2, so staying on 5.14.x is the conservative choice. | repo1.maven.org junit-jupiter maven-metadata: 5.14.0-5.14.4 and 6.0.0-6.1.3; 5.14.4 POM dated 26 Apr 2026. MDG testproject/build.gradle: enforcedPlatform('org.junit:junit-bom:5.14.1'). junit-fml-4.0.45.pom: junit-bom 5.10.2. |
| corrected | The mod/build.gradle sketch gives a working unitTest setup. | unitTest { enable() } does not add JUnit. The sketch must add the JUnit BOM, junit-jupiter and junit-platform-launcher, plus test { useJUnitPlatform() }. Also, NeoForge docs require the @GameTestHolder value to match the mod id, so the gametest mod's tests need @GameTestHolder('strategos_gametest'). | MDG README 'Unit testing with JUnit' (testImplementation 'org.junit.jupiter:junit-jupiter', testRuntimeOnly 'org.junit.platform:junit-platform-launcher', test { useJUnitPlatform() }). MDG testproject/build.gradle. https://docs.neoforged.net/docs/1.21.1/misc/gametest. |
| confirmed | #3670 makes the per-player sync filter apply on initial sync. | The commit title says syncToPlayer(), but the method is AttachmentSyncHandler#sendToPlayer(IAttachmentHolder, ServerPlayer). | Patch https://github.com/neoforged/NeoForge/commit/e0e7ab5d27.patch adds 'type.syncHandler.sendToPlayer(holder, to)' to initial sync. In 21.1.262, mc-src-1.21.1/net/neoforged/neoforge/attachment/AttachmentSync.java:154-162 has no filter on initial sync, while :88 and :108 filter update syncs. AttachmentSyncHandler.java:39. |
| confirmed | Attachment sync was backported 2025-07-31 (#2544), around 21.1.197. | It landed exactly in 21.1.197. | Commit 8404b0625a 'Backport to 1.21.1: Syncable Data Attachments (#2544)' 2025-07-31T21:28Z. 21.1.197 POM Last-Modified Thu 31 Jul 2025 21:34:41. |
| confirmed | Architectury: API 13.0.11 for 1.21.1 (2026-07-23, LGPL-3.0); Loom 1.17.493 (2026-09-12, MIT); plugin 3.5.170 (2026-09-14). | None. | maven.architectury.dev: architectury-neoforge 13.0.11 POM 23 Jul 2026; architectury-loom 1.17.493 POM 12 Sep 2026; architectury-plugin marker lastUpdated 20260914141801 with latest 3.5.170. Modrinth architectury-api license LGPL-3.0-only. architectury/architectury-loom LICENSE is MIT. |
| confirmed | MultiLoader-Template 1.21.1 branch (common on MDG vanilla mode, fabric, neoforge, forge; ServiceLoader) was last committed 2026-10-01, with stale pins NeoForge 21.1.80, Fabric API 0.109.0, Loader 0.16.9; CC0-1.0. | None. | raw jaredlll08/MultiLoader-Template/1.21.1: gradle.properties (neoforge_version=21.1.80, fabric_version=0.109.0+1.21.1, fabric_loader_version=0.16.9, neo_form_version=1.21.1-20240808.144430); common/build.gradle uses moddev with neoFormVersion; Services.java uses ServiceLoader. Latest branch commit 2026-10-01T04:25:12Z. Repo license CC0-1.0. |
| confirmed | Stonecutter 0.9.8 (2026-08-31, LGPL-3.0) is used by Controlify, Zoomify, YACL, Friends & Foes, AutoModpack and MidnightControls. | Version and license confirmed. Only YACL, Controlify and Zoomify were re-checked; Friends & Foes, AutoModpack and MidnightControls are unverified. | maven.kikugie.dev stonecutter latest 0.9.8, lastUpdated 20260831103632. GitHub stonecutter-versioning/stonecutter license LGPL-3.0. isXander/YetAnotherConfigLib, Controlify and Zoomify each have stonecutter.gradle.kts. |
| unverifiable | Architectury Loom is used by Terra, Geyser, Sodium Extra, Steam 'n' Rails, Oh The Biomes We've Gone and playerAnimator. | Only Geyser was confirmed. The rest could not be re-checked because GitHub code search needs authentication. Mark them unverified. | GeyserMC/Geyser settings.gradle.kts and bootstrap/mod/build.gradle.kts reference architectury. |
| confirmed | Sinytra Connector supports 1.21.1, 26.1.2 and 26.2; MIT. | None. | Modrinth connector: license MIT; 1.21.1 neoforge 2.0.0-beta.17+1.21.1; 26.x game versions 26.1.2 and 26.2. |
| confirmed | Create has no official 26.x release, but an unofficial 'Create: Adapted' exists for 26.2. Mekanism has nothing on Modrinth for 26.x. AE2, GeckoLib, JEI and Curios have 26.x builds. | Create: Adapted is on CurseForge, not Modrinth. | Modrinth: create and mekanism have no 26.x files; ae2 has 26.1.2/26.2/26.3; geckolib, jei and curios have 26.x. https://www.curseforge.com/minecraft/mc-mods/create-adapted is an unofficial NeoForge 26.2 adaptation. |
| confirmed | The top Fabric 1.21.1 packs on Modrinth are Fabulously Optimized and the Cobblemon packs. | None. | Modrinth search, modpacks [1.21.1, fabric], sorted by downloads: fabulously-optimized 19.06M, cobblemon-fabric 11.41M, cobbleverse 7.22M. |
| confirmed | Release dates: 26.1 (2026-03-24), 26.1.1 (04-01), 26.1.2 (04-09), 26.2 (06-16), 26.3 (09-15); 1.21.11 (2025-12-09) was the last obfuscated release; the 1.21.2-1.21.10 dates in the primer table. | None. | https://piston-meta.mojang.com/mc/game/version_manifest_v2.json releaseTime values. 1.21.11's version JSON has client_mappings; 26.1's does not. |
| confirmed | Fabric API 0.116.x has had 18 releases since 2025-05-06. The 2026 cadence was Jan, Feb, Apr x2, May (security), Jul x3, Sep x2. | None. | POM Last-Modified for 0.116.0+1.21.1 (2025-05-06) through 0.116.17+1.21.1 (2026-09-01). Commit d30f6a79f7 (2026-05-11) 'Limit initial capacity of collections/maps when decoding packets' became 0.116.12. |
| confirmed | NeoGradle 7.1.39 (2026-09-15) is maintained. The MDG MDK has 160 stars against 35 for the NeoGradle MDK. The MDG 2 post describes it as 'focused on performance, simplicity, and ease of use'. | None. The MDG 2 post also says NeoGradle 'continues to be supported'. | maven.neoforged.net net/neoforged/gradle/userdev: latest 7.1.39, POM 15 Sep 2026. GitHub org NeoForgeMDKs repos: MDK-1.21.1-ModDevGradle 160 stars, MDK-1.21.1-NeoGradle 35. https://neoforged.net/news/moddevgradle2/ (2025-01-02). |
| corrected | Multi-loader cost: Fabric lacks four of the ten platform seams entirely (config, tickets, data maps, HUD layers). | Data maps are not among the ten seams listed. Among the seams, Fabric API on 1.21.1 lacks config, chunk tickets and layered HUD; it lacks data maps as well. | Internal consistency of section 4; module list of the Fabric API 1.21.1 branch. |
| corrected | On 1.21.6+, ValueInput.read(codec) and ValueOutput.store(codec) take codecs directly. | The methods take a key: ValueInput.read(String key, Codec<T>) returns an Optional, and ValueOutput.store(String key, Codec<T>, T) / storeNullable(...) write it. The design point holds. | primers/1.21.6 lines 1469 and 1476: in.read('example_stack', ItemStack.CODEC) and storeNullable('example_stack', ItemStack.CODEC, ...). |
| confirmed | gameTestServer exits with the number of failed required tests. neoforge.enabledGameTestNamespaces filters tests. | None. An empty or unset property loads all namespaces. | mc-src-1.21.1/net/minecraft/gametest/framework/GameTestServer.java:227 System.exit(this.testTracker.getFailedRequiredCount()). neoforge/gametest/GameTestHooks.java:66. https://docs.neoforged.net/docs/1.21.1/misc/gametest. |
| confirmed | MDG adds -XstartOnFirstThread on macOS automatically. | It does so only for client runs. | ModDevGradle src/main/java/net/neoforged/moddevgradle/internal/PrepareRunOrTest.java:154-156: if (isClientDistribution() && OperatingSystem.current() == MACOS). |
| confirmed | -Xmx2G is NeoForge's own value. | It is NeoForge's org.gradle.jvmargs. The MDK default is -Xmx1G. | raw NeoForge 1.21.1 gradle.properties: org.gradle.jvmargs=-Xmx2G. MDK-1.21.1 gradle.properties: -Xmx1G. |
| confirmed | Many 1.20.1 Forge files also tag NeoForge. | None. | Modrinth: 5,100 mods for [1.20.1, neoforge] against 25,273 for [1.20.1, forge]. |
| confirmed | The MDK repo was last updated 2026-10-18 and its MDK default is 21.1.262. | None. | GitHub API commits for NeoForgeMDKs/MDK-1.21.1-ModDevGradle: latest 2026-10-18T04:30:41Z 'Update MDK with new output from mod generator (#81)'. gradle.properties neo_version=21.1.262. |
