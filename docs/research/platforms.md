# Strategos: mod loader, toolchain and version strategy (Minecraft 1.21.1)

*Research date: 2026-10-20. Scope: loader, build toolchain, project layout and version strategy for a new, large, long-lived RTS overhaul mod.*

*Sources and method. All versions and dates come from primary sources read today. Those are: Maven metadata (with HTTP `Last-Modified` of the POMs), GitHub branches and commits, official blogs and docs, and Modrinth API queries. API claims were checked against the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 sources (`scratchpad/mc-src-1.21.1`), against the reference MDK build log, and against artifacts I downloaded (the NeoForge testframework jar, Fabric API and Loom sources). Anything marked **Inference** is my own judgement, not documented fact. Not done: no Gradle builds (forbidden), no CurseForge API (it needs a key).*

*Independent fact-check (2026-10-20). Every versioned, dated or API claim was re-checked against fresh primary sources: Maven metadata and POM dates, Mojang version JSONs, the Gradle and foojay APIs, the GitHub REST API (commits, trees, security advisories), re-run Modrinth queries, cfwidget for CurseForge pack data, blogs and docs, and the decompiled 1.21.1 sources. Errors are fixed inline, and statements that could not be reproduced are marked "(unverified)". The final section, "Verification notes", lists every change.*

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
- Large content and system mods and kitchen-sink packs live there. ATM10 has about 22.5M downloads (22,487,032 on CurseForge) and its 8.3 release came out 2026-10-15.
- The 21.1 branch is still patched: 10 builds in October 2026. They include 21.1.262, which fixes three GitHub security advisories published 2026-10-15 (network hardening).
- NeoForge covers far more of what an RTS needs than Fabric API does on 1.21.1. That includes persistent chunk tickets, HUD layers, render stages, cancellable mouse events, data maps, per-world synced config, and a JUnit/GameTest stack with a live-server JUnit provider. Synced attachments with per-player filtering exist on both loaders (Fabric's API is marked experimental), so that item is parity, not an advantage.
- NeoForge has run on Mojang names since 1.20.2, so the eventual 26.x port carries no mapping migration.

The risk is the version, not the loader. The ecosystem frontier is moving to 26.x, and porting 1.21.1 → 26.3 is a large job concentrated in rendering, GUI and input, which are exactly the areas an RTS client leans on (§6).

---

## 1. NeoForge 21.1.x on 1.21.1

### 1.1 Release status and cadence

| Item | Value | Source |
|---|---|---|
| Latest 1.21.1 build | **21.1.264**, 2026-10-19 | [maven-metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml), POM Last-Modified |
| MDK default | 21.1.262 (2026-10-15); MDK repo updated 2026-10-18 | [MDK-1.21.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) |
| First 21.1 build | 21.1.1, 2024-08-08, the day 1.21.1 shipped | Maven, [Mojang manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) |
| Cadence | 63 builds since 21.1.200 (2025-08-09); 21.1.260 and 21.1.261 were never published. Per month: 2025-08 7, -09 3, -10 4, -11 3, -12 1; 2026-01 2, -02 0, -03 3, -04 6, -05 5, -06 1, -07 13, -08 2, -09 3, **-10 10** | POM Last-Modified |
| Security | **21.1.229** (2026-05-11): fix for a `FriendlyByteBuf#readCollection/readMap` crash (a client-supplied count can force a huge allocation, CWE-789). This is the only fix the NeoForged blog post describes. **21.1.262** (2026-10-15) fixes three medium-severity GitHub advisories: [GHSA-cpm9-qrxg-6x27](https://github.com/neoforged/NeoForge/security/advisories/GHSA-cpm9-qrxg-6x27) (authenticated DoS in server-bound packet reassembly), [GHSA-xmvj-vxj4-4846](https://github.com/neoforged/NeoForge/security/advisories/GHSA-xmvj-vxj4-4846) (improper direction validation) and [GHSA-w776-qpv9-86xq](https://github.com/neoforged/NeoForge/security/advisories/GHSA-w776-qpv9-86xq) (unbounded ad-hoc channels). The fixes enforce payload directions and decode limits, add split-packet size checks and add channel-id limits. New NeoForge COMMON config options: `splitPacketMaxSizeLimitMb`, default 128, and `maxAdhocChannelLimit`, default 2048. The fixes shipped on 2026-10-15 in a coordinated release across 11 lines: 26.3.0.83, 26.2.0.93, 26.1.2.120, 21.11.48, 21.8.57, 21.5.101, 21.4.161, 21.3.100, 21.1.262, 20.6.144 and 20.4.253. 26.1.2.122 came later the same day. The blog has no post on the October fixes | [NeoForged blog, 2026-05-11](https://neoforged.net/news/mitigating-vulnerabilities-network/), [GitHub security advisories](https://github.com/neoforged/NeoForge/security/advisories), [1.21.1 commits](https://github.com/neoforged/NeoForge/commits/1.21.1) |
| Latest backports | 21.1.264: "Check AttachmentSyncHandler#syncToPlayer() for initial sync (#3670)". On 21.1 the method is named `AttachmentSyncHandler#sendToPlayer`. The patch makes `AttachmentSync#syncInitialAttachments` check it, so the per-player sync filter is now also honoured on initial sync. Before 21.1.264, initial sync sent every syncable attachment regardless of the filter. That matters if attachments carry hidden information. 21.1.263: enchanted-loot events. Also FML 4.0.45, Mixin 0.16.4+mixin.0.8.7, MixinExtras 0.5.5 (2026-10-03/04) | same; [patch e0e7ab5d27](https://github.com/neoforged/NeoForge/commit/e0e7ab5d27) |
| NeoForge's own build | 1.21.1 branch builds with Gradle **9.8.1** (since 2026-10-10) and MDG 2.0.115. The default 26.3.x branch builds with Gradle 9.8.1 and MDG **2.0.148**, used as a buildSrc library | [gradle-wrapper.properties](https://github.com/neoforged/NeoForge/blob/1.21.1/gradle/wrapper/gradle-wrapper.properties), [gradle.properties](https://github.com/neoforged/NeoForge/blob/1.21.1/gradle.properties), [26.3.x gradle.properties](https://github.com/neoforged/NeoForge/blob/26.3.x/gradle.properties) |
| License | LGPL-2.1 (puts no constraint on the mod's own license) | GitHub |

### 1.2 Support policy

- **There is no formal LTS.** NeoForge team notes ([NAP 2025](https://hackmd.io/@neoforged/rJ_d8ZxLxl), a compiled community Q&A with maintainers, not formal minutes): "The idea of LTS is not a thing as NeoForge will accept backports of non-breaking PRs to any past version minus 1.20.1." Also: "LTS is determined by community engagement."
- **Workflow.** A PR must land on the latest branch before it can be backported ([21.2 post, 2024-10-22](https://neoforged.net/news/21.2release/)). That same post asked modders to "continue to actively maintain the 1.21.1 version of their mods until the release of Minecraft 1.22". There never was a 1.22; 26.1 took its place.
- **Evidence that the policy holds in practice.** The October 2026 security fixes shipped to 11 NeoForge lines, back to 20.4 (§1.1).
- **Signal about the successor.** The [26.1 post (2026-03-24)](https://neoforged.net/news/26.1release/) says there are "strong indications that 26.1 will be the next stable modding version, replacing 1.21.1". The author labels this a personal guess, not an official position.
- **Inference.** Maintenance of 1.21.1 is driven by demand. With ATM10 and more than 21k mods, demand should last through 2027. But there is no committed end date, and backports must be non-breaking. Big new APIs (for example the 21.9 transfer rework) will not come to 21.1.

### 1.3 ModDevGradle 2.0.148 (2026-09-29, LGPL-2.1)

Verified against the [MDG README and source](https://github.com/neoforged/ModDevGradle), the MDK, and MDG's own `testproject`.

| Capability | Status | Notes |
|---|---|---|
| Run types | `client`, `server`, `data`, `gameTestServer`, plus any number of named runs. Per run: `sourceSet`, `loadedMods`, JVM and system properties, `devLogin`, `taskBefore` | `gameTestServer` exits with the number of failed required tests (`GameTestServer.java:227`; [docs](https://docs.neoforged.net/docs/1.21.1/misc/gametest)) |
| JUnit | `neoForge.unitTest { enable(); testedMod = mods.x }` | Combine with `net.neoforged:testframework:21.1.264`. Its `EphemeralTestServerProvider` (a JUnit `ParameterResolver`) hands a live `MinecraftServer` to JUnit tests. The jar also ships `EmptyTemplate`, `GameTestPlayer`, `ExtendedGameTestHelper`, `StructureTemplateBuilder` (checked in the downloaded jar). JUnit itself must be declared (`junit-jupiter`, `junit-platform-launcher`, `useJUnitPlatform()`); the testframework POM does not bring it |
| Data generation | `data()` run on 1.21.1 | The MDK passes `--mod/--all/--output/--existing` |
| Parchment | `parchment { minecraftVersion; mappingsVersion }` or `gradle.properties` | MDG 2 requires both values |
| Access transformers | Default `META-INF/accesstransformer.cfg` is auto-detected. `validateAccessTransformers = true`. Can be published and consumed | |
| Interface injection | `interfaceInjectionData.from(...)` | Dev-time only; needs Mixin (or coremods) at runtime |
| Jar-in-Jar | `jarJar` configuration with version ranges, local files and subprojects | |
| Multi-source-set / multi-project mods | `mods { x { sourceSet a; sourceSet project(':p').sourceSets.main } }`, `addModdingDependenciesTo` | MDG's own test project uses exactly this pattern |
| Vanilla mode | `neoFormVersion` instead of `version` | For loader-agnostic "common" modules |
| CI speed | `disableRecompilation`; on by default when `CI=true` (since 2.0.136) | |
| Configuration cache | Supported | The reference build stored a configuration-cache entry |
| Gradle floor | The trampoline enforces Gradle ≥ 8.8 (and Java ≥ 17 to run Gradle) | `TrampolinePlugin.java` |
| macOS | Adds `-XstartOnFirstThread` automatically to client runs | `PrepareRunOrTest.java` |

**NeoGradle.** 7.1.39 (2026-09-15) is still maintained, and the mod generator still offers it. Its 1.21.1 NeoGradle MDK was last regenerated on 2026-07-05, on NeoGradle 7.1.38. The [MDG 2 announcement](https://neoforged.net/news/moddevgradle2/) (2025-01-02) also says "NeoGradle continues to be supported". But NeoForge itself builds with MDG components, and the MDG MDK is the de facto default (160 stars against 35 for the NeoGradle MDK). The same announcement describes MDG as "focused on performance, simplicity, and ease of use". **Use MDG.**

### 1.4 Gradle, JDKs, configuration cache

- **Gradle versions.** Current is 9.8.2 (2026-10-19). 9.8.1 is from 2026-10-07. **9.2.1** (2025-11-17) is the wrapper in *every* current NeoForge MDK (1.21.1, 26.1.2, 26.2, 26.3), all with MDG 2.0.148 ([services.gradle.org](https://services.gradle.org/versions/all)).
- **Reference build on this M4 Pro.** NeoForge 21.1.262 + MDG 2.0.148 + Gradle 9.2.1 gave `BUILD SUCCESSFUL in 1m 37s` from a cold start. The NeoForm pipeline took about 50 s of that (Vineflower decompile 13.9 s, recompile 12.4 s). The configuration-cache entry was stored. Gradle printed: "Deprecated Gradle features were used … incompatible with Gradle 10."
- **JVM support.** Gradle 9.8.x runs on a JVM between 17 and 27. Gradle 9.2.1, the bootstrap wrapper, runs on 17–25: Java 26 support arrived in 9.4.0 and Java 27 in 9.8.0. Java 25 support (both running and toolchains) arrived in Gradle 9.1.0 ([compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)).
- **This machine.** `/usr/libexec/java_home` resolves only Homebrew OpenJDK **23.0.2**, a non-LTS release that is out of support. Homebrew `openjdk@25` (25.0.4.1) is installed but not registered with `java_home`. foojay provisioned **Temurin 21.0.12.1** into `~/.gradle/jdks` for the toolchain. Pin the daemon with `./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium` and commit `gradle/gradle-daemon-jvm.properties` ([docs](https://docs.gradle.org/current/userguide/gradle_daemon.html); it needs toolchain download repositories, which foojay provides). Optional: JetBrains Runtime 21.0.11 (also available through foojay) for `-XX:+AllowEnhancedClassRedefinition` hot-swap.

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

- **Maintenance.** The 0.116.x line for 1.21.1 has had 18 releases since 2025-05-06. In 2026: January, February, April ×2, May (security: limits on the initial capacity of decoded collections), July ×3, September ×2. The [1.21.1 branch](https://github.com/FabricMC/fabric-api/tree/1.21.1) gets small backports. On 2026-09-01 a HUD render events backport (#5541) was merged and reverted the same day. This is maintenance mode.
- **Toolchain constraint.** Loom 1.19.2's Gradle module metadata declares `org.gradle.jvm.version=25` and `org.gradle.plugin.api-version=9.8.0` ([.module](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.2/fabric-loom-1.19.2.module)). **Inference:** the latest Loom therefore needs Gradle ≥ 9.8 running on JDK 25, even for a 1.21.1 project. Fabric says support for "modding older game versions with the latest Loom is a very high priority" ([obfuscation post, 2025-10-31](https://fabricmc.net/2025/10/31/obfuscation.html)).
- **Mappings.**
  - Yarn "is no longer officially supported" from 26.1 on ([26.1 post](https://fabricmc.net/2026/03/14/261.html)). The post tells developers who have not moved to Mojang's names that "now is the time to do so". On 26.1+, Mojang's names are the only supported set.
  - On 1.21.1, though, Fabric still runs on *intermediary* names in production.
  - So a Fabric 1.21.1 mod faces a toolchain migration at the 26.x port: remap Loom → no-remap Loom, `modImplementation` → `implementation`, and Fabric API renames that "are not backwards compatible" (for example `ItemGroupEvents` → `CreativeModeTabEvents`).
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
  - Quilt Loader's latest build is 0.31.0-beta.4 (2026-09-04); the latest stable is 0.30.1 (2026-08-28). It runs Fabric mods.
  - QSL stopped at `10.0.0-alpha.5+1.21.1` (2025-09-21). Quilted Fabric API's last release is 7.7.0 for 1.20.1 (2024-12-02). Its 1.21 builds stayed alpha (last 2024-08-12), and none exists for 1.21.1.
  - Quilt Mappings were retired ([blog, 2026-02-03](https://quiltmc.org/en/blog/1/)).
  - **Verdict: no.**

---

## 4. Multi-loader options: real cost and benefit

| Option | What it is | Latest (date), license | Who uses it (evidence) |
|---|---|---|---|
| Architectury (API + Architectury Loom + plugin) | A common module built against vanilla, plus a module per platform. The API abstracts events, networking and registries | API **13.0.11** for 1.21.1 (2026-07-23, LGPL-3.0); Loom 1.17.493 (2026-09-12, MIT, a fork of Fabric Loom); plugin 3.5.170 (2026-09-14) | GitHub code search finds Architectury Loom in Terra, Geyser, Steam 'n' Rails and playerAnimator (spot-checked in their build files), and Sodium Extra, Oh The Biomes We've Gone and others (unverified) |
| [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template/tree/1.21.1) | Gradle template only: `common` (MDG vanilla mode via NeoForm), `fabric` (Loom), `neoforge` (MDG), `forge`. Platform services through ServiceLoader. No runtime library | 1.21.1 branch, last commit 2026-10-01. Pins are stale (NeoForge 21.1.80, Fabric API 0.109.0, Loader 0.16.9). CC0-1.0 | Widely copied for libraries and smaller mods |
| Stonecutter | Comment-based preprocessor for multiple versions (and loaders) from one source tree | 0.9.8 (2026-08-31), LGPL-3.0 | Controlify, Zoomify, YACL (spot-checked); Friends & Foes, AutoModpack, MidnightControls (GitHub code search, unverified) |
| Sinytra Connector (+ FFAPI) | Runs Fabric mods on NeoForge. A player-side tool, not a dev tool | Supports 1.21.1, 26.1.2, 26.2 (Modrinth), MIT | Means a NeoForge-only mod can still share a pack with many Fabric mods |

**What the large mods do on 1.21.1** (Modrinth per-loader files):
- NeoForge only: **Create 6.0.10** (2026-04-21), Mekanism 10.7.19, AE2 19.2.18, KubeJS 2101.7.2, Curios, Ars Nouveau, Iron's Spells, Create Aeronautics.
- Multi-loader: JEI, GeckoLib 4.9.3, Waystones, Cobblemon, Supplementaries, Sodium, Iris, Lithium, Distant Horizons, Jade, Better Combat, SmartBrainLib, YACL, owo-lib.
- The pattern: **libraries and client or performance mods go multi-loader; large gameplay systems stay on one loader.** For multiple MC versions, the big projects use one branch per version (Mekanism has `1.21.x`, `26.1`, `26.2`, `26.3`; AE2 and Create have per-version branches), not preprocessors.

**Cost for Strategos.**
- About ten platform seams would each need two implementations and two test runs: registration, networking, attachments, config, chunk tickets, HUD layers, world-render hooks, in-world input and camera hooks, GameTests, and datagen.
- On 1.21.1, Fabric lacks four of these entirely: config, tickets, data maps and HUD layers. Those would have to be filled with your own code or third-party libraries.
- CI time and bug surface roughly double.
- **Inference:** about 20–35% extra effort through v1.

**Benefit.** Access to the Fabric 1.21.1 audience (19,665 mods), whose packs lean towards performance, vanilla+ and Cobblemon. An RTS overhaul is a game mode that players install deliberately, so cross-loader reach matters less than it does for a library.

**Verdict:** single loader. Keep the NeoForge glue in one package (`…platform.neoforge`) so that a later MultiLoader-style split stays mechanical. If a Fabric port is ever wanted, follow the MultiLoader-Template approach (each loader's official toolchain) rather than adding an Architectury runtime dependency.

---

## 5. The 1.21.1 ecosystem and API coverage

### 5.1 Numbers

Modrinth API, 2026-10-20. Counts are distinct projects with at least one file for that game version tagged with that loader. Facets are evaluated per file: for example, Create matches `[1.21.1, neoforge]` but not `[1.21.1, forge]`. "New" means the project was created after 2026-07-22. Many 1.20.1 Forge files also tag NeoForge (5,099 mods have a NeoForge-tagged 1.20.1 file).

| | NeoForge 1.21.1 | NeoForge, any 26.x | Fabric 1.21.1 | Fabric, any 26.x |
|---|---|---|---|---|
| Mods | **21,500** | 9,690 (26.1.2: 8,147; 26.2: 6,847; 26.3: 4,554) | 19,665 | 16,840 |
| New mods in the last 90 days | **2,604** | 1,662 | 1,583 | 3,444 |
| Modpacks | **2,178** | 99 | 3,066 | 2,318 |
| New modpacks in the last 90 days | **342** | 20 | 141 | 488 |

- Across all loaders, 1.21.1 has 32,271 mods; any 26.x has 19,058.
- About 9,270 projects ship both NeoForge and Fabric 1.21.1 builds. That is 21,500 + 19,665 − 31,898, where 31,898 comes from a union query for NeoForge or Fabric on 1.21.1. Subtracting the 32,271 all-loader total instead gives 8,894, a valid but looser lower bound.
- NeoForge's own [2025 retrospective](https://neoforged.net/news/2025-retrospection/): 1.21.1 "still remains the most popular NeoForge version" with more than 16,000 mods. Its NeoForm artifact used the most bandwidth of any file on their Maven: requested over 10 million times, 11 TB, about 3% of traffic.
- **Where the big packs are.**
  - CurseForge: [ATM10](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10) (NeoForge 1.21.1; 8.3 released 2026-10-15; about 22.5M downloads).
  - CurseForge: [ATM11](https://www.curseforge.com/minecraft/modpacks/all-the-mods-11) (NeoForge **26.1.2**; 0.12.0-beta released 2026-10-15; 1.1M downloads; billed as "Super early alpha").
  - Top Fabric 1.21.1 packs on Modrinth: Fabulously Optimized and the Cobblemon packs.
  - Modrinth download counts are lifetime totals across all versions.

### 5.2 API coverage for what an RTS needs

The NeoForge column was checked in the 21.1.262 sources; the Fabric column in the `fabric-api` 1.21.1 branch.

| Need | NeoForge 21.1 | Fabric API 0.116.17 (1.21.1) |
|---|---|---|
| Entities and renderers | `DeferredRegister`, `EntityRenderersEvent.RegisterRenderers/RegisterLayerDefinitions/AddLayers`, `IEntityWithComplexSpawn` (extra spawn data) | Vanilla registries plus `EntityRendererRegistry` and `EntityModelLayerRegistry`; no spawn-data helper |
| Networking | `RegisterPayloadHandlersEvent` → `PayloadRegistrar` (`playToClient/ToServer/Bidirectional`, `configuration*`, `common*`, `versioned`, `optional`, `executesOn`). `PacketDistributor` covers player, near, dimension, all, `TrackingEntity(+AndSelf)` and `TrackingChunk`. The internal (`@ApiStatus.Internal`) `GenericPacketSplitter` transparently splits packets above vanilla's frame limits (2 MiB compressed / 8 MiB uncompressed). Since 21.1.262, reassembly is capped at 128 MB (`splitPacketMaxSizeLimitMb`). Documented limits: clientbound ≤ 1 MiB, serverbound < 32 KiB ([docs](https://docs.neoforged.net/docs/1.21.1/networking/payload)). In the source these numbers are vanilla's caps for unknown payloads, and I found no size check on the decode path of registered mod payloads. Treat them as the design budget (enforcement unverified) | `PayloadTypeRegistry`, `Server/ClientPlayNetworking`, `PlayerLookup` |
| Per-object state | **Data attachments** on `Entity`, `Level`, `LevelChunk`/`ChunkAccess` and `BlockEntity`. Supports `serialize(Codec)`, `copyOnDeath`, and **`sync(StreamCodec)` with a per-player predicate** (`sync(BiPredicate<IAttachmentHolder, ServerPlayer>, StreamCodec)`; `AttachmentSyncHandler#sendToPlayer`). Sync was backported 2025-07-31 (#2544) and shipped in 21.1.197. The predicate is honoured on initial sync only from 21.1.264 | Data Attachment API (experimental) with `syncWith` and `AttachmentSyncPredicate` |
| Capabilities | `Block/Entity/ItemCapability`, `RegisterCapabilitiesEvent` | API Lookup and Transfer API |
| Datapack registries | `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec)` | `DynamicRegistries.register/registerSynced` |
| Data maps | `RegisterDataMapTypesEvent` / `DataMapType` (data-driven per-entry stats) | none |
| Config | `ModConfigSpec` with STARTUP, CLIENT, COMMON and SERVER types. SERVER is per world and synced to clients. Built-in `ConfigurationScreen` ([docs](https://docs.neoforged.net/docs/1.21.1/misc/config)) | none (Cloth Config, YACL, owo-config, Forge Config API Port) |
| Chunk loading | **`TicketController`** plus `RegisterTicketControllersEvent`: persistent tickets per owner (block, entity or UUID) with a validation callback | Vanilla only: `addRegionTicket` with a custom `TicketType` (not persisted), or `setChunkForced` (persisted, but no owner) |
| Entity tracking | `PlayerEvent.StartTracking/StopTracking`. Vanilla `Entity#broadcastToPlayer` gates tracking (`ChunkMap.TrackedEntity#updatePlayer`). The gate is re-evaluated only when the entity crosses a chunk-section boundary (`ChunkMap#tick`) or a player moves (`ChunkMap#move`) | `EntityTrackingEvents.START/STOP_TRACKING`, same vanilla gate |
| GameTest | `@GameTestHolder`, `@PrefixGameTestTemplate`, `RegisterGameTestsEvent`, `gameTestServer` run. The testframework adds `EmptyTemplate`, `GameTestPlayer` and helpers | `fabric-gametest-api-v1` (`FabricGameTest.EMPTY_STRUCTURE`); Loom `configureTests()` (experimental); no client GameTest |
| JUnit | MDG `unitTest` (mod loaded inside tests) plus `EphemeralTestServerProvider` (live server) | `fabric-loader-junit` (no server provider) |
| HUD | **`RegisterGuiLayersEvent`** (`registerAbove/Below/AboveAll/BelowAll`, `replaceLayer`, `wrapLayer`), 25 named `VanillaGuiLayers`, cancellable `RenderGuiLayerEvent.Pre` for hiding vanilla HUD | `HudRenderCallback` only. Fabric's layered HUD API (`HudLayerRegistrationCallback`) arrived in Fabric API for 1.21.4 (added 2025-02-09) and was reworked into `HudElementRegistry` for 1.21.6. The 1.21.1 backport was reverted |
| World overlays | `RenderLevelStageEvent`: 11 stages (`AFTER_SKY` … `AFTER_LEVEL`) and custom stages | `WorldRenderEvents` (10 events, `START` … `END`) |
| Camera | `ViewportEvent.ComputeCameraAngles` (yaw, pitch, roll), `ComputeFov`, `CalculateDetachedCameraDistanceEvent`. **Camera position has no hook**: `ComputeCameraAngles` fires inside `Camera#setup` before the position is set from the entity. It needs a Mixin on `Camera#setup` or a camera-entity approach | Mixin for everything |
| Input | Cancellable `InputEvent.MouseButton.Pre` and `MouseScrollingEvent`, plus `Key` and `InteractionKeyMappingTriggered`. `RegisterKeyMappingsEvent` with `KeyConflictContext` and `KeyModifier`. `CalculatePlayerTurnEvent` and `MovementInputUpdateEvent` | `KeyBindingHelper`; in-world mouse handling needs Mixin |
| Datagen | `GatherDataEvent` plus the `data` run | `fabric-data-generation-api-v1` plus `configureDataGeneration()` |
| Mixin | Bundled (Mixin 0.16.4+mixin.0.8.7, MixinExtras 0.5.5) | Bundled |

**Points specific to an RTS:**
1. Neither loader exposes a camera-position event on 1.21.1, so the RTS camera needs a small Mixin either way.
2. `Entity#broadcastToPlayer` is vanilla and overridable. Strategos's own unit entities can use it for **server-authoritative fog of war** without a Mixin. Vanilla entities would need one. **Caveat:** vanilla re-checks the gate only when a tracked entity crosses a chunk-section boundary or a player moves, so a unit that becomes visible or hidden inside one section is not re-checked on its own. The mod must trigger the refresh. The public `ServerChunkCache#move(ServerPlayer)` re-checks every tracked entity for one player (no Mixin, O(entities) per call, which is what vanilla does on each player move). For per-entity refreshes, add an access transformer on the package-private `ChunkMap$TrackedEntity#updatePlayer`.
3. Attachment sync with a per-player predicate, together with 21.1.264's initial-sync fix, suits hidden per-player data.
4. Design serverbound packets as small intent commands. Cap list sizes, validate on the server, and keep clientbound snapshots under 1 MiB. NeoForge caps the initial capacity of collections decoded through `FriendlyByteBuf#readCollection/readMap` at 65,536 entries (vanilla's `ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE`, since 21.1.229).

---

## 6. Version check: 1.21.1 against 26.x

### 6.1 Where 26.x stands

- **Releases.** 26.1 on 2026-03-24, 26.1.1 on 04-01, 26.1.2 on 04-09, 26.2 on 06-16, 26.3 on 09-15. 26.4 snapshots are running (snapshot-5 on 2026-10-20). 1.21.11 (2025-12-09) was the last obfuscated release; 26.x ships no mappings and requires Java 25 ([manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)).
- **NeoForge stable status.** The beta tag came off on 26.1.2 at 26.1.2.71 (2026-06-02), on 26.2 at 26.2.0.57 (08-09), and on 26.3 at 26.3.0.76 (10-11). The 26.1.x branch also receives automated backports (26.1.2.122 on 2026-10-15).
- **Ecosystem.** See §5.1. For NeoForge, new mod projects target 1.21.1 at roughly twice the rate of the largest single 26.x line, and new modpacks at 17 times the rate of all 26.x combined. Fabric has already moved on to 26.x.
- **Big mods.**
  - Create has no official 26.x release. There are unofficial ports: "Create: Adapted" for NeoForge 26.2 (CurseForge, labelled experimental) and "Create Fly" for Fabric 26.1.2/26.2 (Modrinth).
  - Mekanism has 26.x branches but nothing on Modrinth.
  - AE2, GeckoLib, JEI and Curios have 26.x builds.
  - ATM11 on 26.1.2 is still beta.
  - **Inference:** 26.1.2 is the front-runner for the next stable modding version, but no one has said so officially.

### 6.2 What moving to 26.x gains and loses

| Gained on 26.x | Lost or risked |
|---|---|
| Unobfuscated game: readable stack traces in players' crash reports, official parameter names, no Parchment | A NeoForge ecosystem less than half the size; about 5% of the modpacks; several flagship content mods not ported yet |
| Java 25 (flexible constructor bodies, scoped values, module imports, stream gatherers, AOT cache, opt-in compact object headers) | A moving target: each quarterly release breaks a lot (26.2 "Too Many Rendering Changes", 26.3 "There's Always a Client Rewrite"). No line has yet been named the next stable modding version, although NeoForge builds for 26.1.2, 26.2 and 26.3 are all out of beta |
| A modern renderer: Vulkan backend (26.2), Renderpearl API and order-independent transparency (26.3); gizmos for debug shapes (1.21.11) | Less documentation and fewer worked examples. **Inference (unverified):** the AI assistant's training data (cutoff June 2026) covers 1.21.1-era APIs well and 26.1 partly. It covers 26.2 at most partly: 26.2 shipped on 2026-06-16, with snapshots before that. It does not cover 26.3 at all. The decompiled sources reduce but do not remove this gap |
| More data-driven vanilla systems: datapack villager trades (26.1), reloadable datapack registries (26.3) | 26.x needs Gradle ≥ 9.1 and a Java 25 toolchain everywhere |
| Longevity: the mod would be "current" when it ships in 2027–28 | |

### 6.3 What drives the cost of porting 1.21.1 → 26.x

Sizing: the NeoForge porting primers from [1.21.2 to 26.3](https://github.com/neoforged/.github/tree/main/primers) total **29,760 lines (1.87 MB)**. They list about **10,500 individual API changes** (unverified: depending on how entries are counted, the files hold roughly 6,400 to 16,500 backtick-led bullet entries).

| Version (date) | Main breaking changes (primer sections) | Strategos areas hit |
|---|---|---|
| 1.21.2/1.21.3 (2024-10) | **Entity render states**, shader rewrites, GUI render types (`blit` takes a RenderType function), equipment and consumables, recipes as registries, `ServerLevel` parameters | Every unit and building renderer; HUD drawing |
| 1.21.4 (2024-12) | **Client item definitions** (`assets/*/items/*.json`); particles through render types | Item and icon assets (regenerate via datagen) |
| 1.21.5 (2025-03) | **RenderPipeline rework**, model rework, **GameTest overhaul** (registry-based), **chunk ticket rework**, data-component getters, typed SavedData | Tests, base chunk-loading, overlays |
| 1.21.6–1.21.8 (2025-06/07) | **GUI prepare/render split** (`GuiRenderState`), Blaze3d changes, **`ValueInput`/`ValueOutput` replace direct NBT**, waypoints | Command card, minimap, all persistence |
| 1.21.9/1.21.10 (2025-09/10) | **Feature submission** (`SubmitNodeCollector`) for entities, block entities and particles; **input events as objects**; key-mapping categories. NeoForge: **transfer rework** (`IItemHandler` → `ResourceHandler`), FML API changes, `RenderHighlightEvent` removed | Renderers again; selection input; resource storage |
| 1.21.11 (2025-12) | `ResourceLocation` → `Identifier`; another rendering rewrite (samplers, `RenderTypes`); permissions overhaul; game rules shuffle; NeoForge moves to JSpecify | Mechanical renames; overlays |
| 26.1 (2026-03) | Java 25, unobfuscated; `GuiGraphics` → `GuiGraphicsExtractor`; `ItemStackTemplate`; data-component initializers; dynamic render-layer selection | GUI, items |
| 26.2 (2026-06) | Vulkan backend, `GpuFormat`, vertex formats rewritten | Custom rendering |
| 26.3 (2026-09) | **SDL replaces GLFW** (input codes, text input); Renderpearl; OIT; reloadable datapack registries; loot, recipes and advancements become registries | RTS input and camera; data |

NeoForge-side changes confirmed on today's default branch (26.3.x):
- `RenderLevelStageEvent` is now abstract, with a subclass per stage and a `LevelRenderState`.
- `@GameTestHolder` and `@PrefixGameTestTemplate` are gone. `RegisterGameTestsEvent` still exists but is now registry-based: `registerEnvironment(...)` with `TestEnvironmentDefinition`, and `registerTest(...)` with `GameTestInstance`.
- `IItemHandler` is gone, replaced by `transfer/ResourceHandler`.
- `TicketController`, `RegisterGuiLayersEvent` and testframework's `EphemeralTestServerProvider` still exist. So do `EmptyTemplate`, `GameTestPlayer`, `ExtendedGameTestHelper` and `StructureTemplateBuilder`.

### 6.4 How to architect now to keep a future port small

1. **Keep the simulation core in pure Java (`core`, no `net.minecraft` imports).** That covers economy, gathering rates, construction queues, population, ages and techs, combat and counters, formations, stances, diplomacy, trade, victory conditions, AI planners, and territory and visibility on an abstract grid. Make it tick-driven and deterministic, tested with plain JUnit. It survives every port unchanged.
2. **Put the engine behind narrow ports:** world queries, entity bridge, pathing costs, persistence, network, render, UI, input and audio. Only their adapters live in `mod`.
3. **Persist through Codecs and DTO records.** On 1.21.6+, `ValueInput#read(key, codec)` and `ValueOutput#store(key, codec, value)` take codecs directly, so the NBT → ValueIO change stays in one adapter.
4. **Networking.** Payload records with `StreamCodec`s in one package; small intent packets with size caps; batched deltas.
5. **Write rendering in "extract, then draw" style today.** Compute a per-frame render-state object from the unit, and draw only from that state. This mirrors 1.21.2+ render states and 1.21.9 submission, so the port becomes mechanical. Use only vanilla `RenderType`/`VertexConsumer` abstractions: no raw GL, no `RenderSystem` state hacks, and no custom shaders until they are needed.
6. **UI from immutable view-models** produced by `core`. Widgets draw only. This limits the impact of the 1.21.6 GUI split and the 26.1 extractor rename.
7. **Map inputs through your own action layer** (actions, not GLFW codes). This localises the 1.21.9 input-event change and the 26.3 SDL scancodes.
8. **Generate everything with datagen** (models, client items, lang, tags, loot, recipes). Keep units, buildings, techs and civilisations as Codec-backed datapack registries, so content data carries over almost unchanged.
9. **Keep game tests behind your own thin DSL** (arena, spawn, order, advance ticks, assert). Prefer JUnit with `EphemeralTestServerProvider` for integration tests. The 1.21.5 GameTest overhaul (annotations removed, `RegisterGameTestsEvent` becomes registry-based) then only touches the DSL backend.
10. **Keep Mixins minimal and inventoried** (target, reason, version notes). Prefer NeoForge events. Use MixinExtras `@WrapOperation`/`@ModifyExpressionValue` rather than `@Redirect`/`@Overwrite`.
11. **Avoid APIs that are already doomed:** wrap `IItemHandler` behind your own storage interface; use JSpecify `@NullMarked`/`@Nullable` from day one; create ids through a helper so the `ResourceLocation` → `Identifier` rename touches one place. No Java preview features.

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
| NeoForge | `21.1.264` (≥ 21.1.262 for the October 2026 security fixes; ≥ 21.1.264 for the attachment initial-sync fix). `loader_version_range=[4,)` (FML 4.x). The MDK template writes `versionRange="[${neo_version},)"`, so 21.1.264 also becomes the minimum NeoForge for players and servers. That is intended, because the initial-sync fix runs on the server |
| ModDevGradle | `2.0.148` |
| Gradle wrapper | 9.2.1 (verified today) → 9.8.2 once `build`, `runGameTestServer` and a configuration-cache hit pass. NeoForge's own 1.21.1 branch uses 9.8.1, and its 26.3.x branch uses 9.8.1 with MDG 2.0.148 |
| Toolchain resolver | `org.gradle.toolchains.foojay-resolver-convention` `1.0.0` |
| Java | Toolchain 21 (Temurin 21.0.12.1). Daemon pinned to 21 via `updateDaemonJvm`. Optional JBR 21.0.11 for hot-swap |
| Parchment | `minecraftVersion=1.21.1`, `mappingsVersion=2024.11.17` (final release) |
| Mixin | Use the copies bundled with NeoForge |
| Tests | JUnit Jupiter 5.14.x (MDG's own test project uses 5.14.1; latest 5.x is 5.14.4). Stay on 5.x: JUnit 6.1.3 exists, but junit-fml 4.0.45 is built against JUnit 5.10.2 and 6.x is untested with it. Plus `net.neoforged:testframework:21.1.264` |
| Gradle properties | `configuration-cache`, `caching` and `parallel` on; `-Xmx2G` (the value NeoForge's own repository uses for recompilation; the MDK default of `-Xmx1G` passed the reference build, so 2G is headroom) |

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

Not run. It follows the MDK and MDG's own `testproject` patterns. Verify it at bootstrap. The JUnit lines were added by the fact-check, following the MDG README; the testframework POM does not bring JUnit.

```groovy
plugins { id 'java-library'; id 'net.neoforged.moddev' version '2.0.148' }
evaluationDependsOn(':core')
java.toolchain.languageVersion = JavaLanguageVersion.of(21)
sourceSets { gametest }
dependencies {
    implementation project(':core')
    testImplementation platform('org.junit:junit-bom:5.14.1')   // per MDG README / testproject
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation "net.neoforged:testframework:${neo_version}"
    gametestImplementation sourceSets.main.output
    gametestImplementation project(':core')
    gametestImplementation "net.neoforged:testframework:${neo_version}"
}
tasks.named('test') { useJUnitPlatform() }
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
tasks.named('jar', Jar) { from(project(':core').sourceSets.main.output) }   // ship one jar
```

Fallback if the second-mod wiring gives trouble: keep the GameTests in `main` under their own namespace, filtered with `neoforge.enabledGameTestNamespaces`.

### 7.4 Tests and CI (what the AI assistant runs)

| Command | What it covers | Speed |
|---|---|---|
| `./gradlew :core:test` | Pure logic | Milliseconds |
| `./gradlew :mod:test` | NeoForge-bootstrapped JUnit, plus live-server tests | Seconds (unverified; not run) |
| `./gradlew :mod:runGameTestServer` | Headless GameTests; non-zero exit means failed required tests | Seconds to minutes (unverified; not run) |

- CI: GitHub Actions on Linux, Temurin 21, `gradle/actions/setup-gradle`. With `CI=true`, MDG skips recompilation.
- Client behaviour (camera, HUD) has **no client GameTest on 1.21.1 in either loader**. The testframework's `client` package is only a manual in-game test screen. Unit-test the view-models and input mapping in `core`, and keep a scripted manual or screenshot check.
- Version bumps (NeoForge 21.1.x, MDG, Gradle) arrive as bot PRs (Renovate or Dependabot) and are gated by this pipeline. Never use floating versions. Also watch NeoForge's GitHub security advisories for the 21.1 line.

### 7.5 Explicit "don'ts"

- Forge 52, Quilt, NeoGradle, an Architectury runtime dependency, multi-loader in v1.
- Stonecutter-style preprocessing for 1.21.1 ↔ 26.x. Use a branch per version, as Mekanism and AE2 do.
- Raw GL calls, or game logic inside Mixins.

---

## 8. Risks and unknowns

- **How long 1.21.1 stays maintained.** It depends on demand; there is no committed end date. Mitigation: the decision gate in §6.5 and the port-ready architecture in §6.4.
- **Gradle 9.8.x with MDG 2.0.148 is not tested here** (Gradle runs were forbidden). The evidence is indirect but fairly strong. NeoForge's default 26.3.x branch builds with Gradle 9.8.1 and MDG 2.0.148 (as a buildSrc library), its 1.21.1 branch builds with 9.8.1 and MDG 2.0.115, and MDG 2.0.148 was released after Gradle 9.8.0.
- **Licensing of prior art.**
  - The closest RTS prior art is [Reign of Nether](https://github.com/SoLegendary/reignofnether) (Forge 1.20.1, **GNU GPLv3**; last commit 2026-10-14). Its README and mods.toml say "GNU GPLv3" but do not state whether it is -only or -or-later.
  - Others: [MineFortress](https://github.com/remmintan/minefortress) (Fabric; latest releases target 1.20.2; MIT; last release 2025-07-05, last commit 2025-08-09) and Villager Recruits (Forge 1.20.1, all rights reserved; last release 2026-06-29).
  - Copying GPL code would force a GPL-compatible license on Strategos; reading it for study is fine.

---

## Verification notes

**Scope and method.** An independent fact-check on 2026-10-20 re-checked every key claim (time-sensitive or decision-critical) and every other statement whose error would change a decision. It used fresh primary-source lookups only:
- Maven metadata and POM `Last-Modified` headers for NeoForge, MDG, testframework, FML/junit-fml, Fabric (Loader, API, Loom, Yarn), Quilt, Architectury, Parchment, Stonecutter, Maven Central (JUnit) and the Gradle plugin portal.
- Mojang's version manifest and per-version JSONs (Java runtime, mappings downloads, release dates).
- services.gradle.org, the Gradle compatibility and daemon docs, and the foojay disco API.
- The GitHub REST API (commits, branches, git trees, security advisories, licenses) and raw files. Commit patches were read directly: the 21.1.264 attachment fix and the three 21.1.262 hardening commits.
- The Modrinth v2 API: every count was re-run, facet semantics were tested with Create, and per-project loaders were checked for 22 mods.
- The cfwidget mirror of CurseForge data for ATM10/ATM11.
- NeoForged, Fabric and Quilt blogs and NeoForge docs.
- The decompiled 1.21.1 + NeoForge 21.1.262 sources (read-only) at /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1.
- The MDK at /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1 and its build log. No Gradle was run.
- The testframework 21.1.264 jar, inspected with `javap`.
- All 12 NeoForge primers from 1.21.2 to 26.3, downloaded and counted.

Temporary files are in /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-platforms/.

**Confirmed as written.**
- NeoForge 21.1.264 as the latest 1.21.1 build, the 63-build cadence and its per-month split, the 21.1.229 and 21.1.262 fix contents, the 21.1.263 and 21.1.264 backports, FML/Mixin/MixinExtras versions, the 2025-07-31 sync backport, and the NeoForge 1.21.1 branch on Gradle 9.8.1 and MDG 2.0.115.
- The LTS and backport policy quotes and the 26.1 "personal educated guess".
- All Modrinth totals. The 90-day counts are within 0.5% because the window moves.
- Big-mod loader splits and versions, ATM10/ATM11 releases, and ATM11's beta status.
- MDG 2.0.148 date, license, features, Gradle 8.8 floor and the macOS flag.
- The reference build log, the Gradle 9.2.1 MDK wrappers, Gradle release dates, the Java 25 / Gradle 9.1.0 requirement, and the `updateDaemonJvm` syntax.
- This machine's JDKs, Temurin 21.0.12.1, JBR 21.0.11 and Temurin 25.0.4.1.
- Parchment 2024.11.17 and all Fabric versions, dates, licenses and module metadata.
- Fabric 1.21.1 API gaps and the 2026-09-01 HUD backport revert.
- The Forge 52.1.20 status, the Quilt QSL and QFAPI stop points and the mappings retirement.
- Architectury, MultiLoader-Template, Stonecutter and Connector versions and licenses.
- Every NeoForge 21.1 API name and behaviour in §5.2.
- The `Entity.java:3021` and `ChunkMap.java:1335` references, and the camera-position gap.
- The 26.x release, NeoForge beta-exit and 26.4-snapshot dates, and the primer topics, line count and size.
- The NeoForge 21.9 and 21.11 changes, and the Mekanism, AE2 and Create branch layouts.

**Changed.**
1. §6.3, 26.3.x: `RegisterGameTestsEvent` is **not** gone. It is now registry-based. Only `@GameTestHolder` and `@PrefixGameTestTemplate` were removed. Added that `IItemHandler` is gone on 26.3.x.
2. §1.1 security: added the three GitHub advisories, GHSA-cpm9-qrxg-6x27, GHSA-xmvj-vxj4-4846 and GHSA-w776-qpv9-86xq, as the source for 21.1.262. The blog post covers only 21.1.229. The coordinated release on 2026-10-15 spanned 11 lines, and the patched 26.1.2 build is 26.1.2.120, not 26.1.2.122. Added the new config limits (128 MB split-packet reassembly, 2048 ad-hoc channels).
3. §5.2 point 2 and the entity-tracking row: `broadcastToPlayer` is only re-evaluated on section crossings or player moves. Added how to force a refresh without a Mixin (`ServerChunkCache#move`), or with an access transformer.
4. §5.2 HUD row: Fabric's layered HUD API dates from 1.21.4 (`HudLayerRegistrationCallback`, 2025-02-09) and was reworked into `HudElementRegistry` in 1.21.6. The report had it starting in 1.21.6.
5. §3 Quilt: the latest stable loader is 0.30.1 (2026-08-28); the 0.31 betas are newer. QFAPI's 1.21 builds stayed alpha, and none exists for 1.21.1.
6. §5.1: the NeoForge ∩ Fabric overlap on 1.21.1 is about 9,270 (8,894 was a looser lower bound). The retrospective says the NeoForm artifact used the most bandwidth, not that it was the most-downloaded file. ATM10 has about 22.5M downloads.
7. §1.4: Gradle 9.2.1 runs on JVM 17–25; the 17–27 range applies to 9.8.x.
8. §7.1 Tests: JUnit 6.1.3 exists, and 5.14.4 is only the latest 5.x. Keep 5.14.x because junit-fml is built against 5.10.2.
9. §1.1, §7.1 and §8: stronger evidence for Gradle 9.8 with MDG 2.0.148, since NeoForge's 26.3.x branch builds on that pair.
10. §5.2 networking: the 1 MiB and 32 KiB limits are documented, but the code matches vanilla's unknown-payload caps and I found no check for registered payloads. `GenericPacketSplitter` is internal and automatic. The limits are now framed as a design budget.
11. §8 prior art: Reign of Nether's license is GPLv3, with -only vs -or-later not stated. MineFortress's latest releases target 1.20.2, and its last commit is 2025-08-09. Added the Recruits release date.
12. §0 "Why": per-player attachment sync is parity with Fabric (experimental there), not a NeoForge advantage.
13. §7.3: the sketch now includes the JUnit dependencies and `useJUnitPlatform()` that MDG requires.
14. §2 mappings: reworded the Fabric 26.1-post statement on Mojang names.
15. §6.1: added the unofficial Fabric port "Create Fly" next to "Create: Adapted".
16. §7.1: noted the `-Xmx1G` MDK default, and that the pinned `neo_version` becomes the players' minimum NeoForge.

**Not verifiable here (marked in text).**
- The "about 10,500 API changes" count, which depends on the counting method.
- The AI training-data coverage statement.
- Test-run speeds.
- Enforcement of payload size limits for registered payloads at runtime.
- The gametest-as-second-mod wiring and Gradle 9.8.2 + MDG 2.0.148 on a 1.21.1 mod. Both need a Gradle run at bootstrap.
- GitHub code-search "who uses it" entries beyond the spot-checked ones.
- Whether Reign of Nether is GPL-3.0-only or -or-later.
- CurseForge-wide counts. The CurseForge API needs a key; ATM10/ATM11 were checked via cfwidget.

**Effect on the decision.** None of the corrections changes the recommendations: NeoForge 21.1.264 as a single loader, MDG 2.0.148, Gradle 9.2.1 → 9.8.2, a Java 21 toolchain, the core/mod split, no multi-loader, and 1.21.1 with a 26.x decision gate. The corrections add three implementation requirements: an explicit fog-of-war visibility refresh, explicit JUnit dependencies, and a GameTest DSL whose backend will move to the registry-based `RegisterGameTestsEvent` when porting.

## Recommendations (after verification)

1. 1. Choose NeoForge as the single loader on 1.21.1 and start on NeoForge 21.1.264. It is the first build with both the October 2026 security fixes (21.1.262+, advisories GHSA-cpm9-qrxg-6x27, GHSA-xmvj-vxj4-4846 and GHSA-w776-qpv9-86xq) and the attachment initial-sync fix (21.1.264). Pin the exact version and set loader_version_range=[4,). The MDK template writes versionRange=[${neo_version},), so 21.1.264 also becomes the minimum NeoForge for players and servers. Keep it that way: the initial-sync fix runs on the server, and hidden per-player data depends on it. Watch NeoForge's GitHub security advisories for the 21.1 line.
2. 2. Use ModDevGradle 2.0.148 with Mojang mappings plus Parchment 1.21.1/2024.11.17. Use a Java 21 toolchain (Temurin 21.0.12.1 via foojay-resolver-convention 1.0.0). Pin the Gradle daemon to 21 with `./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium` and commit gradle/gradle-daemon-jvm.properties. Do not let the daemon float on Homebrew's EOL JDK 23. Turn on configuration cache, build cache and parallel builds. -Xmx2G is fine as headroom: it is NeoForge's own setting, and the MDK's -Xmx1G already passed the reference build.
3. 3. Start on Gradle wrapper 9.2.1, the MDK default, verified on this machine (it runs on JDK 17-25). In the bootstrap PR, upgrade to 9.8.2 once `build`, `runGameTestServer` and a configuration-cache hit all pass. Supporting evidence: NeoForge's default 26.3.x branch already builds with Gradle 9.8.1 and MDG 2.0.148, and its 1.21.1 branch with 9.8.1 and MDG 2.0.115.
4. 4. Lay the project out as three parts. `core`: pure Java 21, no Minecraft, JUnit. `mod`: NeoForge/MDG, with the `core` source set included in the mod and its classes bundled into the one jar. `gametest`: a source set run as a separate test mod by runGameTestServer. Verify the gametest-as-mod wiring at bootstrap; the fallback is a namespace filter in main.
5. 5. Build the test pyramid from day one. Fast JUnit in `core`. MDG unitTest with testframework's EphemeralTestServerProvider for server-backed integration tests; declare JUnit explicitly (junit-bom 5.14.x, junit-jupiter, junit-platform-launcher, useJUnitPlatform()). Stay on JUnit 5.14.x rather than 6.x, because junit-fml 4.0.45 is built against 5.10.2 and MDG's test project proves 5.14.1. A thin internal GameTest DSL on the NeoForge testframework helpers (EmptyTemplate, GameTestPlayer, ExtendedGameTestHelper, StructureTemplateBuilder), all still present on 26.3.x. CI on GitHub Actions with CI=true so MDG skips recompilation. Bump dependencies through bot PRs gated by CI.
6. 6. Enforce the port-proofing rules: keep simulation in `core`; use Codec-based persistence DTOs (ValueInput#read(key, codec) and ValueOutput#store(key, codec, value) on 1.21.6+); keep StreamCodec payloads in one package with size caps; render in 'extract state, then draw' style using only vanilla RenderType/VertexConsumer (no raw GL); build UI from immutable view-models; map inputs to your own actions; generate all assets with datagen; define content as datapack registries; keep Mixins minimal and inventoried; wrap IItemHandler (removed by 26.3.x), ResourceLocation creation and nullability (use JSpecify).
7. 7. Use server-side fog of war through Entity#broadcastToPlayer overrides on Strategos's own unit entities and per-player-filtered attachment sync. Vanilla re-checks the tracking gate only when an entity crosses a chunk section or a player moves, so add an explicit visibility refresh whenever the fog changes. The public ServerChunkCache#move(player) re-checks all tracked entities for one player (no Mixin). An access transformer on ChunkMap$TrackedEntity#updatePlayer allows per-entity updates. Benchmark the cost with hundreds of units. Keep serverbound packets as small, validated intent commands. Treat NeoForge's documented limits (under 32 KiB serverbound, at most 1 MiB clientbound) as the design budget, even though the internal packet splitter would carry more (up to the 128 MB reassembly cap).
8. 8. Do not go multi-loader in v1, and do not use Forge 52, Quilt, NeoGradle or an Architectury runtime dependency. Keep loader glue in one package so a MultiLoader-Template-style split stays possible if real demand appears.
9. 9. Set a formal version gate at the end of the first playable core-mechanics milestone or in 2027-Q2, whichever comes first. Before it, do a time-boxed port spike of `mod` to the leading 26.x line (today 26.1.2). Triggers: an official or community stable 26.x version; ATM11 leaving beta and Create/Mekanism publishing official 26.x releases (today only unofficial ports exist: Create: Adapted and Create Fly); new 26.x NeoForge projects overtaking 1.21.1; or 21.1 dropping to security-only fixes.
10. 10. For the eventual port, keep a branch per Minecraft version, as Mekanism and AE2 do, rather than preprocessors such as Stonecutter. Follow the NeoForge porting primers for each version step. Budget for the 1.21.5 GameTest migration: @GameTestHolder and @PrefixGameTestTemplate are removed and RegisterGameTestsEvent becomes registry-based. That is why the GameTest DSL backend must stay thin.

## Open questions (from the original research)

- Which 26.x line (26.1.2, 26.2, 26.3 or a later one) will become the next 'stable modding version' for NeoForge, and when? As of 2026-10-20 there is no official statement, only a personal guess in the 26.1 post and ATM11 choosing 26.1.2.
- How long the community will keep requesting 1.21.1 backports. NeoForge has no LTS commitment, so the maintenance horizon past 2027 is not guaranteed.
- Whether MDG 2.0.148 works cleanly with Gradle 9.8.2, including the configuration cache. I could not run Gradle here; the only evidence is indirect (NeoForge's own build on 9.8.1 with MDG 2.0.115).
- Whether the MDG wiring of a separate `gametest` source set as a second mod (its own neoforge.mods.toml, gameTestServer run with sourceSet and loadedMods) works as sketched on 1.21.1, or whether GameTests should stay in main behind a namespace filter.
- CurseForge-side numbers (mod and modpack counts and downloads per loader and version) were not collected because the CurseForge API needs a key; the counts here are Modrinth-only.
- Neither loader's runtime overhead with hundreds of simulated RTS units was measured. NeoForge vs Fabric tick cost is assumed to be dominated by vanilla entity, AI and pathfinding code; a dedicated benchmark would settle it.
- When Create and other flagship content mods will ship official 26.x versions. This determines when the ecosystem's centre of gravity moves.
- Whether the owner has any requirement to coexist with specific 1.21.1 mods (Create, minimap mods, JEI/EMI), which would strengthen the case for staying on 1.21.1, or to reach Fabric players, which would reopen the multi-loader question.
- License choice for Strategos: copying from the closest prior art (Reign of Nether, GPL-3.0-only) or reusing 0 A.D. assets would impose GPL or copyleft obligations. That is outside this topic but affects how prior art can be used.

## Fact-check verdicts

confirmed: 33, corrected: 15, unverifiable: 1

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | NeoForge 21.1.264 (2026-10-19) is the latest build for 1.21.1; 63 builds since 21.1.200 (2025-08-09); per-month cadence 7,3,4,3,1,2,0,3,6,5,1,13,2,3,10 with 10 in October 2026; first 21.1 build 21.1.1 on 2024-08-08, the day 1.21.1 shipped. | None. 21.1.260 and 21.1.261 were never published, which is why 21.1.200..264 gives 63 builds. | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (highest 21.1 build = 21.1.264); POM Last-Modified: 21.1.264 = Mon 19 Oct 2026 03:40 GMT, 21.1.200 = Sat 9 Aug 2025, 21.1.1 = Thu 8 Aug 2024; per-month recount matches exactly; Mojang manifest: 1.21.1 released 2024-08-08T12:24Z. |
| confirmed | Security fixes: 21.1.229 (May 2026) fixes a FriendlyByteBuf#readCollection/readMap huge-allocation crash; 21.1.262 (2026-10-15) enforces payload directions and decode limits, split-packet size checks and channel-id limits. | Content is right, but the cited blog post (2026-05-11) covers only 21.1.229 and 26.1.2.44-beta. The primary sources for 21.1.262 are three medium-severity GitHub advisories published 2026-10-15: GHSA-cpm9-qrxg-6x27 (authenticated DoS in server-bound packet reassembly), GHSA-xmvj-vxj4-4846 (improper direction validation), GHSA-w776-qpv9-86xq (unbounded ad-hoc channels). 21.1.262 also adds NeoForge COMMON config limits: splitPacketMaxSizeLimitMb (default 128) and maxAdhocChannelLimit (default 2048). | https://neoforged.net/news/mitigating-vulnerabilities-network/ (May 11, 2026; fixed in 21.1.229); GitHub API /repos/neoforged/NeoForge/security-advisories; commits 00c6a3fd23, 0e98dadf67, bd7aff4cb0 (2026-10-14) on branch 1.21.1 and d1ee810bc8 (2026-05-11, '[21.1] Limit initial capacity size for collections and maps during network decode (#3164)'); patches https://github.com/neoforged/NeoForge/commit/bd7aff4cb0.patch and /00c6a3fd23.patch. |
| corrected | 21.1.262 was released the same day as 21.11.48, 26.1.2.122 and 26.2.0.93. | It was a coordinated release on 2026-10-15 (about 01:40 UTC) across 11 lines. The advisories list the patched builds as 26.3.0.83, 26.2.0.93, 26.1.2.120, 21.11.48, 21.8.57, 21.5.101, 21.4.161, 21.3.100, 21.1.262, 20.6.144 and 20.4.253. 26.1.2.122 is a later build from the same day (17:46 UTC), not the patched one. | GHSA patched_versions fields (GitHub REST API); POM Last-Modified: 26.1.2.120 = 15 Oct 2026 01:40, 26.1.2.122 = 15 Oct 2026 17:46, 21.11.48 = 01:40, 26.2.0.93 = 01:40, 26.3.0.83 = 01:38, 20.4.253 = 01:46 GMT. |
| confirmed | 21.1.264 backports 'Check AttachmentSyncHandler#syncToPlayer() for initial sync (#3670)', so the per-player sync filter is also honoured on initial sync; 21.1.263 adds enchanted-loot events; FML 4.0.45, Mixin 0.16.4+mixin.0.8.7, MixinExtras 0.5.5 landed 2026-10-03/04. | On 21.1 the method is named AttachmentSyncHandler#sendToPlayer; only the commit title says syncToPlayer. Before 21.1.264, AttachmentSync#syncInitialAttachments sent every syncable attachment to the receiving player without checking the predicate. Hidden per-player data therefore needs a server on 21.1.264 or later. | https://github.com/neoforged/NeoForge/commit/e0e7ab5d27.patch (adds type.syncHandler.sendToPlayer(holder, to) to syncInitialAttachments); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/attachment/AttachmentSyncHandler.java:39; branch 1.21.1 commits 61e7f77a81 (2026-10-18), ea9161e47b, 411eb80132, 003d690a82 (2026-10-03/04). |
| confirmed | Attachment sync was backported to 1.21.1 on 2025-07-31 (#2544), around 21.1.197. | None. It shipped in 21.1.197. | Commit 8404b0625a, 2025-07-31T21:28Z, 'Backport to 1.21.1: Syncable Data Attachments (#2544)'; 21.1.197 POM Last-Modified 2025-07-31 21:34 GMT. |
| confirmed | NeoForge has no formal LTS and accepts non-breaking backports to any past version except 1.20.1. PRs must land on the latest branch first. The 21.2 post asked modders to maintain 1.21.1 until 1.22. The 26.1 post's 'strong indications that 26.1 will be the next stable modding version' is a personal guess. | None. Additional evidence: the October 2026 security fix shipped to 11 lines going back to 20.4. | https://hackmd.io/@neoforged/rJ_d8ZxLxl (NAP 2025 Team notes, community Q&A, not official minutes; quotes match); https://neoforged.net/news/21.2release/ (2024-10-22); https://neoforged.net/news/26.1release/ (2026-03-24, Technici4n: 'This is a personal educated guess, not an official statement from the NeoForge team as a whole'). |
| confirmed | Modrinth, 2026-10-20: NeoForge 1.21.1 has 21,500 mods and 2,178 modpacks; NeoForge on any 26.x has 9,690 mods (26.1.2: 8,147; 26.2: 6,847; 26.3: 4,554) and 99 modpacks. Created in the last 90 days: 2,604 mods and 342 packs on 1.21.1, against 1,662 mods and 20 packs on 26.x. | None. A re-run gave identical totals (26.3 at 4,555). With the window fixed at created_timestamp > 2026-07-22T00:00Z, the 90-day counts were 2,599 / 1,661 (26.1.2: 1,130) / 340 / 20, within 0.5% of the report. | https://api.modrinth.com/v2/search with facets project_type, versions, categories and created_timestamp > 1784678400. Per-file facet semantics confirmed: Create (LNytGWDc) matches [1.21.1, neoforge] but not [1.21.1, forge]. |
| confirmed | On 1.21.1 NeoForge leads Fabric in mods (21,500 vs 19,665). On 26.x Fabric leads (16,840 vs 9,690) and has more new projects (3,444 vs 1,662 in 90 days). | None. The re-run 90-day figures were 3,438 vs 1,661. | Modrinth search API re-run 2026-10-20: Fabric 1.21.1 = 19,665 mods and 3,066 packs; Fabric 26.x = 16,840 mods and 2,318 packs; Forge 1.21.1 = 5,451 mods and 25 packs; all loaders 1.21.1 = 32,271; any 26.x = 19,058. |
| corrected | At least 8,894 projects ship both NeoForge and Fabric 1.21.1 builds (a lower bound from inclusion-exclusion). | The bound holds but is loose. A union query ([versions:1.21.1] AND [neoforge OR fabric]) returns 31,898 projects, so the overlap is 21,500 + 19,665 - 31,898, about 9,270. | Modrinth search facets [[project_type:mod],[versions:1.21.1],[categories:neoforge,categories:fabric]] = 31,898. |
| confirmed | On 1.21.1 the big content mods are NeoForge-only: Create 6.0.10 (2026-04-21), Mekanism 10.7.19, AE2 19.2.18, KubeJS 2101.7.2, Curios, Ars Nouveau, Iron's Spells, Create Aeronautics. ATM10 is on NeoForge 1.21.1 (8.3 released 2026-10-15, 22.4M downloads). ATM11 on NeoForge 26.1.2 is still beta. | Rounding only: ATM10 has 22,487,032 downloads, so about 22.5M. ATM11 0.12.0-beta (2026-10-15, NeoForge 26.1.2) has 1,135,665 downloads and the summary 'Super early alpha for 26.1.2'. | Modrinth /v2/project/{slug}/version?game_versions=[1.21.1]: every listed mod has loaders=[neoforge] only; Create 6.0.10+mc1.21.1 dated 2026-04-21; Mekanism 10.7.19.85 dated 2026-04-10. https://api.cfwidget.com/minecraft/modpacks/all-the-mods-10 and /all-the-mods-11. |
| corrected | NeoForge 2025 retrospective: 1.21.1 'still remains the most popular NeoForge version' with more than 16,000 mods, and its NeoForm artifact was the most-downloaded file on their Maven. | The popularity quote and the 16,000-mod figure are right. The retrospective does not call the 1.21.1 NeoForm artifact the most-downloaded file. It says that artifact used the most bandwidth: requested over 10 million times, 11 TB, about 3% of total traffic. | https://neoforged.net/news/2025-retrospection/ ('Yes. Stats.' section). |
| confirmed | ModDevGradle 2.0.148 (2026-09-29, LGPL-2.1) supports client/server/data/gameTestServer runs, JUnit via neoForge.unitTest, Parchment, validated ATs, interface injection, jarJar, multi-source-set and multi-project mods, vanilla (NeoForm) mode and the configuration cache. It requires Gradle 8.8 or newer. | None. Two additions: the trampoline also requires Java 17 or newer to run Gradle, and -XstartOnFirstThread is added only for client runs on macOS. Parchment needs both minecraftVersion and mappingsVersion (BREAKING_CHANGES.md). | https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml (latest 2.0.148, POM 29 Sep 2026); GitHub license LGPL-2.1; README (run types, unitTest/testedMod, validateAccessTransformers, interfaceInjectionData, jarJar version ranges, neoFormVersion, disableRecompilation default with CI=true since 2.0.136); src/java8/.../TrampolinePlugin.java (MIN_GRADLE_VERSION 8.8, MIN_JAVA_VERSION 17); src/main/.../PrepareRunOrTest.java:154-156; testproject/build.gradle (junit-bom 5.14.1, mods spanning sourceSets and project(':subproject')). |
| confirmed | Reference build (NeoForge 21.1.262, MDG 2.0.148, Gradle 9.2.1) succeeded from cold in 1m37s and stored a configuration-cache entry. Gradle warned about features incompatible with Gradle 10. | None. The NeoForm pipeline took 50.25 s (Vineflower 13.92 s, recompile 12.41 s). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-build.log ('BUILD SUCCESSFUL in 1m 37s', 'Configuration cache entry stored.', 'Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.'). |
| confirmed | All current NeoForge MDKs (1.21.1, 26.1.2, 26.3) ship Gradle 9.2.1 with MDG 2.0.148. NeoForge's 1.21.1 branch has built with Gradle 9.8.1 since 2026-10-10. Latest Gradle is 9.8.2 (2026-10-19). | None. The 26.2 MDK is the same. Stronger evidence than the report gives: NeoForge's default 26.3.x branch builds with Gradle 9.8.1 and MDG 2.0.148, used as a buildSrc library. The 1.21.1 branch uses MDG 2.0.115. | raw gradle-wrapper.properties and build.gradle of NeoForgeMDKs/MDK-{1.21.1,26.1.2,26.2,26.3}-ModDevGradle; neoforged/NeoForge 1.21.1 gradle-wrapper.properties (9.8.1) changed by commit 289ef53a1e on 2026-10-10; 26.3.x gradle.properties moddevgradle_plugin_version=2.0.148 with wrapper 9.8.1; https://services.gradle.org/versions/all (9.8.2 = 2026-10-19, 9.8.1 = 2026-10-07, 9.2.1 = 2025-11-17). |
| corrected | Gradle needs a JVM between 17 and 27 to run; Java 25 support (running and toolchains) arrived in Gradle 9.1.0. | The 17-27 range holds for Gradle 9.8.x. The bootstrap wrapper, Gradle 9.2.1, runs on JVM 17-25: Java 26 support came in 9.4.0 and Java 27 in 9.8.0. The Java 25 / 9.1.0 part is correct. | https://docs.gradle.org/current/userguide/compatibility.html (page for 9.8.2: 'A JVM version between 17 and 27 is required'; table: 25 -> 9.1.0, 26 -> 9.4.0, 27 -> 9.8.0). |
| confirmed | This machine: /usr/libexec/java_home resolves only Homebrew OpenJDK 23.0.2; foojay provisioned Temurin 21.0.12.1. Pin the daemon with './gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium' and commit gradle/gradle-daemon-jvm.properties. JBR 21.0.11 and Temurin 25.0.4.1 are available via foojay. | None. Homebrew openjdk@25 (25.0.4.1) is also installed but is not registered with java_home. updateDaemonJvm needs toolchain download repositories, which foojay provides. | /usr/libexec/java_home -V (1 JVM: 23.0.2 Homebrew); ~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2 release file (Temurin-21.0.12.1+1); /opt/homebrew/Cellar/openjdk@25/25.0.4.1; https://docs.gradle.org/current/userguide/gradle_daemon.html; foojay disco API (temurin 25.0.4.1, 21.0.12.1; JBR 21.0.11). |
| confirmed | Parchment for 1.21.1 is final at 2024.11.17 (CC0) and is not needed from 26.1 on. | None. | https://maven.parchmentmc.org/org/parchmentmc/data/parchment-1.21.1/maven-metadata.xml (release 2024.11.17, lastUpdated 20241117); ParchmentMC/Parchment license CC0-1.0; 26.1 post: 'Parchment can be removed since Mojang's parameter names are now available'. |
| confirmed | Fabric on 1.21.1 means Loader 0.19.5 (2026-08-28), Fabric API 0.116.17+1.21.1 (2026-09-01) and Loom 1.19.2 (2026-10-19) under plugin id net.fabricmc.fabric-loom-remap. Loom 1.19.2's module metadata requires JVM 25 and Gradle plugin API 9.8.0. Yarn 1.21.1+build.3 (2024-08-10) is final and is unsupported from 26.1. | None. | https://meta.fabricmc.net/v2/versions/loader (0.19.5 stable; 0.20.0/0.20.1 unstable); Fabric API POM 0.116.17+1.21.1 = 01 Sep 2026 16:34; https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.2/fabric-loom-1.19.2.module (org.gradle.jvm.version=25, org.gradle.plugin.api-version=9.8.0); plugin marker net.fabricmc.fabric-loom-remap release 1.19.2; https://docs.fabricmc.net/1.21.11/develop/loom; https://fabricmc.net/2026/03/14/261.html; licenses Apache-2.0/Apache-2.0/MIT/CC0-1.0. |
| confirmed | Fabric API 1.21.1 is in maintenance mode: 18 releases of 0.116.x since 2025-05-06 (2026: Jan, Feb, Apr x2, May security, Jul x3, Sep x2). A HUD render events backport (#5541) was merged and reverted on 2026-09-01. | None. The May release (0.116.12, 2026-05-11) carries 'Limit initial capacity of collections/maps when decoding packets'. 0.116.16 contains the HUD backport and 0.116.17 reverts it. | Fabric API POM dates 0.116.0 (2025-05-06) through 0.116.17; fabric-api 1.21.1 commits ebf276f36b and f44c2eea0d (2026-09-01) and d30f6a79f7 (2026-05-11). |
| confirmed | Fabric API on 1.21.1 has no config API, no chunk-ticket API, no data maps, no layered HUD API (only HudRenderCallback) and no client GameTest module. Its Data Attachment API, including sync (added 2024-11-12), is marked experimental. | None. | fabric-api 1.21.1 settings.gradle module list (no config, ticket, data-map or client-gametest module); AttachmentRegistry.java:44 and AttachmentType.java:62 carry @ApiStatus.Experimental; AttachmentSyncPredicate added by commit 8e331c5709 on 2024-11-12 ('Data Attachment Sync API (#4049)'); fabric-client-gametest-api-v1 first appears on the 1.21.4 branch. |
| corrected | Fabric: a layered HUD API exists from 1.21.6 (the 1.21.1 backport was reverted). | A layered HUD API (HudLayerRegistrationCallback / IdentifiedLayer) was added on the 1.21.4 branch on 2025-02-09 (#4119) and also exists on 1.21.5. It was reworked into HudElementRegistry for 1.21.6. The 1.21.1 backport of these HUD events (#5541) was reverted the same day, 2026-09-01. | raw.githubusercontent.com/FabricMC/fabric-api/{1.21.3,1.21.4,1.21.5}/fabric-rendering-v1/.../HudLayerRegistrationCallback.java returns 404/200/200; 1.21.6 .../hud/HudElementRegistry.java exists; commit 44a0820dd2 (2025-02-09) 'Add Hud Render Events (#4119)'. |
| corrected | Fabric recommends Mojang names for all new mods (26.1 post); ItemGroupEvents -> CreativeModeTabEvents renames 'are not backwards compatible'. | The rename quote is right. The post does not state a rule for new mods. It says Yarn 'is no longer officially supported' and tells developers who have not moved to Mojang's mappings that 'now is the time to do so'. On 26.1+, Mojang's names are the only supported set. The post also requires Java 25 for the Gradle JVM, plus Loom 1.15 and Gradle 9.4.0 at the time of writing. | https://fabricmc.net/2026/03/14/261.html. |
| confirmed | NeoForge 21.1 provides synced data attachments with a per-player predicate on entities, levels, chunks and block entities; persistent per-owner chunk tickets (TicketController); HUD layers (RegisterGuiLayersEvent, 25 VanillaGuiLayers, cancellable RenderGuiLayerEvent.Pre); 11 render stages plus custom stages; camera-angle and FOV events; cancellable mouse button and scroll events; data maps; datapack registries; and ModConfigSpec with per-world SERVER configs synced to clients. | None. | In /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1: net/neoforged/neoforge/attachment/AttachmentType.java:184,224,261,273; attachment/AttachmentSync.java:114-143 (block entity, chunk, entity, level); holders at net/minecraft/world/entity/Entity.java:131, world/level/Level.java:79, world/level/chunk/ChunkAccess.java:60, LevelChunk.java:53, world/level/block/entity/BlockEntity.java:30; common/world/chunk/TicketController.java:27-67 (BlockPos/Entity/UUID owners, ForcedChunksSavedData), LoadingValidationCallback.java:18; client/event/RegisterGuiLayersEvent.java:47-107; client/gui/VanillaGuiLayers.java (25 ids); client/event/RenderGuiLayerEvent.java:70; client/event/RenderLevelStageEvent.java:132-143 (RegisterStageEvent) and 158-207 (11 stages); client/event/ViewportEvent.java:254,327; client/event/InputEvent.java:99,136,214,296; registries/DataPackRegistryEvent.java:74; https://docs.neoforged.net/docs/1.21.1/misc/config. |
| confirmed | testframework 21.1.264 ships EphemeralTestServerProvider (JUnit with a live MinecraftServer) plus EmptyTemplate, GameTestPlayer, ExtendedGameTestHelper and StructureTemplateBuilder; EphemeralTestServerProvider still exists on 26.3.x. | None. All five classes also exist on 26.3.x. The jar's 'client' package is a manual in-game test screen, not automated client GameTests. | https://maven.neoforged.net/releases/net/neoforged/testframework/21.1.264/testframework-21.1.264.jar (unzip listing; javap shows EphemeralTestServerProvider implements org.junit.jupiter.api.extension.ParameterResolver and exposes MinecraftServer); GitHub tree of NeoForge 26.3.x (testframework/src/main/java/net/neoforged/testframework/junit/EphemeralTestServerProvider.java and gametest/*). |
| corrected | Neither loader has a camera-position hook on 1.21.1, so the RTS camera needs a Mixin on Camera#setup or a camera entity. Vanilla Entity#broadcastToPlayer gates ChunkMap.TrackedEntity#updatePlayer, so custom unit entities can implement server-side fog of war without a Mixin. | Every stated fact is correct, but the claim leaves out something the design depends on. Vanilla re-runs the gate only when a tracked entity crosses a chunk-section boundary (ChunkMap#tick) or when a player moves (ChunkMap#move). A unit that becomes visible or hidden inside one section is therefore not re-checked on its own. Fog of war needs an explicit refresh: the public ServerChunkCache#move(ServerPlayer) re-checks every tracked entity for one player (no Mixin, O(entities) per call). Alternatively, an access transformer on the package-private ChunkMap$TrackedEntity#updatePlayer allows per-entity refreshes. The camera part is accurate: ComputeCameraAngles fires inside Camera#setup before setPosition, and the detached distance goes through ClientHooks. | In /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1: net/minecraft/world/entity/Entity.java:3021; net/minecraft/server/level/ChunkMap.java:1327-1343 (updatePlayer, gate at 1335), 1151-1170 (tick: updatePlayers only if lastSectionPos changed), 1023-1030 (public move(ServerPlayer) calls updatePlayer for all), 1278 (package-private class TrackedEntity), 150 (private entityMap); net/minecraft/server/level/ServerChunkCache.java:464 (public move); net/minecraft/client/Camera.java:47-73. |
| confirmed | On NeoForge 1.21.1 clientbound payloads carry at most 1 MiB and serverbound payloads less than 32 KiB; NeoForge caps the initial capacity of decoded collections at 65,536 entries. | These are the documented limits. In the 1.21.1 source the same numbers are vanilla's caps for unknown (discarded) payloads, and I found no size check on the decode path of registered mod payloads. Treat them as a design budget. NeoForge's internal GenericPacketSplitter transparently splits packets above vanilla's frame limits (2 MiB compressed / 8 MiB uncompressed); since 21.1.262 reassembly is capped at 128 MB. The 65,536 cap is vanilla's ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE, applied by NeoForge to FriendlyByteBuf#readCollection/readMap since 21.1.229. | https://docs.neoforged.net/docs/1.21.1/networking/payload; mc-src-1.21.1/net/minecraft/network/protocol/common/ServerboundCustomPayloadPacket.java:16 (32767), ClientboundCustomPayloadPacket.java:36 (1048576), net/minecraft/network/codec/ByteBufCodecs.java:44 (65536), net/minecraft/network/FriendlyByteBuf.java:125,171, net/minecraft/network/CompressionDecoder.java:18-19, net/neoforged/neoforge/network/filters/GenericPacketSplitter.java (@ApiStatus.Internal); patch bd7aff4cb0 (splitPacketMaxSizeLimitMb default 128). |
| confirmed | MinecraftForge 1.21.1-52.1.20 (2026-10-15) is maintained but marginal on 1.21.1; recommended is 52.1.0. Modrinth lists 25 Forge 1.21.1 modpacks against 2,178 for NeoForge. Commits by LexManos, Paint_Ninja and Jonathing; network-list hardening in October 2026; FG7 MDK backported in March 2026. | None. | https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json (1.21.1-latest 52.1.20, recommended 52.1.0); POM 15 Oct 2026; MinecraftForge 1.21.1 branch commits 8199135df7 (2026-10-15 'Add protection against large network channel lists') and 0675635b0c (2026-03-18 'Migrate the MDK to ForgeGradle 7'); Modrinth re-run: 5,451 mods and 25 modpacks. |
| corrected | Quilt is not viable: QSL stopped at 10.0.0-alpha.5+1.21.1 (2025-09-21), Quilted Fabric API at 7.7.0 for 1.20.1 (2024-12-02), Quilt Mappings were retired (blog 2026-02-03), and the loader is in beta (0.31.0-beta.4, 2026-09-04). | Quilt Loader's latest build is 0.31.0-beta.4 (2026-09-04), but the latest stable is 0.30.1 (2026-08-28), so 'the loader is in beta' is misleading. QFAPI's last release is 7.7.0 for 1.20.1 (2024-12-02); its 1.21 builds stayed alpha (last 11.0.0-alpha.3, 2024-08-12) and none exists for 1.21.1. The other facts and the 'no' verdict stand. | https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/maven-metadata.xml plus quilt-last-modified-timestamp headers (0.30.1 = 2026-08-28, 0.31.0-beta.4 = 2026-09-04); qsl metadata (10.0.0-alpha.5+1.21.1 = 2025-09-21); quilted-fabric-api metadata; https://quiltmc.org/en/blog/1/ (2026-02-03 'Bringing Quilt into the non-obfuscated era': 'Goodbye, Quilt Mappings'). |
| corrected | The NeoForge porting primers 1.21.2 through 26.3 total 29,760 lines (1.87 MB) and list about 10,500 API changes. The per-version list of rewrites (render states, RenderPipeline/GameTest/tickets, GUI render state and ValueInput/ValueOutput, feature submission and input, Identifier, Java 25/unobfuscation, Vulkan, SDL/Renderpearl) is accurate. | Lines (29,760) and size (1,867,831 bytes) are exact, and every listed topic is a primer section. The '~10,500 API changes' figure could not be reproduced and is now marked unverified. Depending on the counting method, the files hold 6,431 (backtick bullets inside change-list sections) to 16,524 (all backtick-led bullets) entries. | https://github.com/neoforged/.github/tree/main/primers (12 index.md files 1.21.2-26.3 downloaded and counted); section headings checked, e.g. 1.21.2 'Entity Render States'/'Gui Render Types'; 1.21.5 'Render Pipeline Rework'/'The Game Test Overhaul'/'Tickets'; 1.21.6 'Prepare and Render' plus ValueInput/ValueOutput; 1.21.9 'Feature Submissions'/'Input Handling Consolidation'; 1.21.11 'ResourceLocation to Identifier'; 26.1 GuiGraphics -> GuiGraphicsExtractor; 26.2 'Too Many Rendering Changes'/'Vulkan'; 26.3 'There's Always a Client Rewrite'/'LWJGL with SDL'/'Renderpearl'/'Reloadable Datapack Registries'. |
| corrected | On NeoForge's default 26.3.x branch: RenderLevelStageEvent is abstract with a subclass per stage and a LevelRenderState; GameTestHolder and RegisterGameTestsEvent are gone; TicketController, RegisterGuiLayersEvent and EphemeralTestServerProvider still exist. | RegisterGameTestsEvent is not gone. It still exists on 26.3.x in a registry-based form: registerEnvironment(...) with TestEnvironmentDefinition, and registerTest(...) with GameTestInstance. Only @GameTestHolder and @PrefixGameTestTemplate are gone. The rest is confirmed. IItemHandler is also gone on 26.3.x, replaced by transfer/ResourceHandler. | https://raw.githubusercontent.com/neoforged/NeoForge/26.3.x/src/main/java/net/neoforged/neoforge/event/RegisterGameTestsEvent.java (HTTP 200, registry-based API); 26.3.x git tree: no GameTestHolder.java, PrefixGameTestTemplate.java or IItemHandler.java; src/client/java/.../RenderLevelStageEvent.java:56 'public abstract class' with AfterSky...AfterLevel subclasses and getLevelRenderState(). |
| confirmed | NeoForge has used Mojang names and stopped reobfuscating mods since 1.20.2, so a NeoForge 1.21.1 mod needs no mapping migration at 26.1. A Fabric 1.21.1 mod runs on intermediary and must move to the no-remap Loom plugin and the renamed Fabric API. | None. | https://github.com/neoforged/NeoForge/discussions/199 (2023-10-27: 'swapping out SRG names in favor of using Mojang's Mapping Set'; maintainer: 'mods don't get obfuscated anymore in 20.2'); https://fabricmc.net/2025/10/31/obfuscation.html; https://fabricmc.net/2026/03/14/261.html. |
| confirmed | Libraries and client or performance mods are multi-loader on 1.21.1 (JEI, GeckoLib 4.9.3, Waystones, Cobblemon, Supplementaries, Sodium, Iris, Lithium, Distant Horizons, Jade, Better Combat, SmartBrainLib, YACL, owo-lib). Big content mods stay single-loader. Big mods use a branch per MC version (Mekanism 1.21.x/26.1/26.2/26.3, AE2, Create). | None. | Modrinth version API per project (all listed have fabric plus neoforge files for 1.21.1); GitHub branches: mekanism/Mekanism has 1.21.x, 1.21.11, 26.1, 26.2, 26.3; AE2 has 1.21.1 ... 1.21.11 and 26.1; Creators-of-Create/Create has mc1.21.1/dev and no 26.x branch. |
| confirmed | NeoForge on 26.1.2 has been stable since 26.1.2.71 (2026-06-02); current build 26.1.2.122 (2026-10-15); the 26.1.x branch receives automated backports. 26.2 stable since 26.2.0.57 (2026-08-09), 26.3 since 26.3.0.76 (2026-10-11). 26.4 snapshots are running (snapshot-5 on 2026-10-20). | None. The preceding builds are 26.1.2.70-beta, 26.2.0.56-beta and 26.3.0.75-beta. The latest 26.3 build is 26.3.0.95 (2026-10-20). | NeoForge maven-metadata.xml and POM dates; 26.1.x branch commits by neoforged-automation[bot] 'Backport to 26.1.x: ...'; Mojang manifest (26.4-snapshot-5 at 2026-10-20T12:15Z; 26.1 2026-03-24, 26.1.1 04-01, 26.1.2 04-09, 26.2 06-16, 26.3 09-15). |
| confirmed | 1.21.1 runs on Java 21 (java-runtime-delta). All 26.x versions require Java 25. Gradle 9.1.0 or newer is needed for Java 25. 1.21.11 (2025-12-09) was the last obfuscated release and 26.x ships no mappings. | None. | Mojang version JSONs: 1.21.1 and 1.21.11 javaVersion java-runtime-delta/21 with client_mappings and server_mappings downloads; 26.1, 26.1.2, 26.2, 26.3 and 26.4-snapshot-5 have java-runtime-epsilon/25 and only client/server downloads; Gradle compatibility matrix. |
| confirmed | Create has no official 26.x release; there is an unofficial 'Create: Adapted' for 26.2. Mekanism has 26.x branches but nothing on Modrinth. AE2, GeckoLib, JEI and Curios have 26.x builds. | None. A second unofficial port exists: 'Create Fly' (Fabric/Quilt, 26.1.2 and 26.2, Modrinth). Create: Adapted is on CurseForge, not Modrinth, targets NeoForge 26.2 and is labelled experimental. | Modrinth: create has no 26.x versions; create-fly has fabric 26.1.2/26.2; mekanism has none for 26.x; ae2, geckolib, jei and curios have 26.x files. Web search: curseforge.com/minecraft/mc-mods/create-adapted (unofficial NeoForge 26.2 port, tested with NeoForge 26.2.0.57+). |
| confirmed | Multi-loader tooling: Architectury API 13.0.11 for 1.21.1 (2026-07-23, LGPL-3.0), Architectury Loom 1.17.493 (2026-09-12, MIT fork of Fabric Loom), plugin 3.5.170 (2026-09-14); MultiLoader-Template 1.21.1 branch last commit 2026-10-01 with stale pins (NeoForge 21.1.80, Fabric API 0.109.0, Loader 0.16.9), CC0-1.0; Stonecutter 0.9.8 (2026-08-31, LGPL-3.0); Sinytra Connector supports 1.21.1, 26.1.2 and 26.2 (MIT). | None. The 'who uses it' columns were spot-checked only: YACL, Controlify and Zoomify use Stonecutter; Geyser, Terra, playerAnimator and Steam 'n' Rails reference Architectury. The other named projects are unverified. | maven.architectury.dev metadata and POM dates; GitHub licenses (architectury-api LGPL-3.0, architectury-loom MIT, fork=true); MultiLoader-Template 1.21.1 gradle.properties and commit 7bd74e0279 (2026-10-01); maven.kikugie.dev and plugin-portal metadata (0.9.8, lastUpdated 20260831); stonecutter-versioning/stonecutter LGPL-3.0; Modrinth connector (MIT; 1.21, 1.21.1, 26.1.2, 26.2). |
| corrected | JUnit Jupiter 5.14.x (MDG's own test project uses 5.14.1; latest is 5.14.4). | 5.14.4 (2026-04-26) is the latest 5.x release. The latest JUnit overall is 6.1.3. Staying on 5.14.x is still the right call: junit-fml 4.0.45, which MDG's unitTest uses, is built against JUnit 5.10.2, and MDG's test project proves only 5.14.1. JUnit 6 is untested with it. | https://repo1.maven.org/maven2/org/junit/junit-bom/maven-metadata.xml (release 6.1.3; 5.14.4 POM 2026-04-26); https://maven.neoforged.net/releases/net/neoforged/fancymodloader/junit-fml/4.0.45/junit-fml-4.0.45.pom (junit-bom 5.10.2). |
| confirmed | Gradle properties: configuration-cache, caching and parallel on; -Xmx2G is NeoForge's own value for recompilation. | -Xmx2G is the value NeoForge's own repository uses ('required to provide enough memory for recompilation' of NeoForge itself). The 1.21.1 MDK ships -Xmx1G, and the reference build passed with it, so 2G is headroom, not a requirement. | raw NeoForge 1.21.1 gradle.properties (org.gradle.jvmargs=-Xmx2G plus comment); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/gradle.properties (-Xmx1G, parallel/caching/configuration-cache true). |
| confirmed | Pin NeoForge 21.1.264 with loader_version_range=[4,) (FML 4.x). | [4,) is valid: 21.1 builds depend on FML 4.0.24 (21.1.1) through 4.0.45 (21.1.264); the MDK default is [1,). Side effect: the MDK template writes versionRange="[${neo_version},)" for the neoforge dependency, so pinning 21.1.264 makes it the minimum NeoForge for players and servers. That is desirable here because the initial-sync fix is server-side. | neoforge-21.1.264.pom and neoforge-21.1.1.pom (fancymodloader loader 4.0.45 / 4.0.24); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/src/main/templates/META-INF/neoforge.mods.toml:11,72. |
| corrected | Prior art: Reign of Nether (Forge 1.20.1, GPL-3.0-only, updated 2026-10-14); MineFortress (Fabric 1.20.1, MIT, last updated 2025-07-05); Villager Recruits (Forge 1.20.1, all rights reserved). | Reign of Nether is Forge 1.20.1 (forge 1.20.1-47.4.0) and GPLv3: its README says 'GNU General Public License v3.0' and mods.toml says 'GNU GPLv3'. Whether it is -only or -or-later is not stated, so 'GPL-3.0-only' is unverified. Its last commit is 2026-10-14. MineFortress's latest releases target Fabric 1.20.2, not 1.20.1; it is MIT, with the last release on 2025-07-05 and the last commit on 2025-08-09. Villager Recruits is confirmed: Forge 1.20.1, all rights reserved, last release 2026-06-29. The copyleft conclusion is unchanged. | GitHub API SoLegendary/reignofnether (license GPL-3.0, default branch 1.20.1-dev, last commit 2026-10-14) plus raw README/mods.toml/build.gradle; Modrinth minefortress versions (1.17.5-beta, 2025-07-05, [1.20.2], fabric) and GitHub last commit 2025-08-09; Modrinth villager-recruits (LicenseRef-All-Rights-Reserved, forge, 1.20.1). |
| corrected | Gradle 9.8.x with MDG 2.0.148 is untested; the only evidence is indirect (NeoForge's own 1.21.1 build on 9.8.1 with MDG 2.0.115). | It is still untested for a 1.21.1 mod project here, but there is stronger indirect evidence: NeoForge's default 26.3.x branch builds with Gradle 9.8.1 and MDG 2.0.148 (as a buildSrc library), and MDG 2.0.148 (2026-09-29) was released after Gradle 9.8.0 (2026-09-24). | raw neoforged/NeoForge 26.3.x gradle/wrapper/gradle-wrapper.properties (9.8.1), gradle.properties (moddevgradle_plugin_version=2.0.148), buildSrc/build.gradle (implementation net.neoforged:moddev-gradle:...); services.gradle.org (9.8.0 = 2026-09-24). |
| corrected | Why-section: NeoForge covers far more than Fabric API on 1.21.1, including synced attachments with per-player filtering. | The overall conclusion stands, but synced attachments with a per-player predicate exist on Fabric 1.21.1 too (AttachmentRegistry.Builder#syncWith with AttachmentSyncPredicate, experimental). That item is parity. The real differences are persistent owned chunk tickets, HUD layers, data maps, config, cancellable in-world mouse events and a live-server JUnit provider. | fabric-api 1.21.1 AttachmentRegistry.java:160 (syncWith(PacketCodec, AttachmentSyncPredicate)), AttachmentSyncPredicate.java:33. |
| corrected | §7.3 mod/build.gradle sketch (follows MDK and MDG testproject patterns; verify at bootstrap). | The MDG wiring matches the README and testproject patterns, but the sketch is missing the JUnit setup MDG requires. Add testImplementation platform('org.junit:junit-bom:5.14.1'), testImplementation 'org.junit.jupiter:junit-jupiter', testRuntimeOnly 'org.junit.platform:junit-platform-launcher' and tasks.named('test') { useJUnitPlatform() }. The testframework POM does not bring JUnit. The gametest-as-second-mod run wiring remains unverified until bootstrap. | MDG README 'Unit testing with JUnit' section; testframework-21.1.264.pom has no junit dependency; MDG testproject/build.gradle lines 25-28. |
| confirmed | MDK data run passes --mod/--all/--output/--existing; gameTestServer exits with the number of failed required tests. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/build.gradle (data run programArguments); mc-src-1.21.1/net/minecraft/gametest/framework/GameTestServer.java:227 (System.exit(this.testTracker.getFailedRequiredCount())). |
| confirmed | NeoGradle 7.1.39 (2026-09-15) is still maintained and offered by the mod generator; the MDG MDK has 160 stars against 35 for the NeoGradle MDK; the MDG 2 post calls MDG 'focused on performance, simplicity, and ease of use'. | None. The MDG 2 post is dated 2025-01-02 and also says 'NeoGradle continues to be supported'. The NeoGradle 1.21.1 MDK was last regenerated by the mod generator on 2026-07-05, on NeoGradle 7.1.38. | maven.neoforged.net net/neoforged/gradle/userdev metadata (7.1.39, POM 15 Sep 2026); GitHub API stargazers (160 / 35); NeoForgeMDKs/MDK-1.21.1-NeoGradle commits; https://neoforged.net/news/moddevgradle2/. |
| confirmed | Fabric on 1.21.1 has only vanilla chunk loading: addRegionTicket with a custom TicketType (not persisted) or setChunkForced (persisted, no owner). | None. | mc-src-1.21.1/net/minecraft/server/level/ServerChunkCache.java:445; ServerLevel.java:1415; TicketType.java:21-25; no ticket module in the fabric-api 1.21.1 module list. |
| confirmed | 1.21.9: NeoForge transfer rework (IItemHandler -> ResourceHandler), FML API changes, RenderHighlightEvent removed, key-mapping categories. 1.21.11: NeoForge moves to JSpecify. | None. In 21.9 the old handler interfaces were deprecated for removal; they are absent on 26.3.x. | https://neoforged.net/news/21.9release/ ('The old RenderHighlightEvent was removed', FMLLoader.getCurrent(), KeyMapping.Category); https://neoforged.net/news/21.9-transfer-rework/ (ResourceHandler<ItemResource>, EnergyHandler); https://neoforged.net/news/21.11release/ ('Minecraft and NeoForge switched to JSpecify's nullability annotations'). |
| confirmed | Port-proofing: on 1.21.6+, ValueInput.read(codec) and ValueOutput.store(codec) take codecs directly. | Precision only: both take a key plus a codec, e.g. in.read("example_stack", ItemStack.CODEC). | primers/1.21.6/index.md:1459-1469. |
| unverifiable | Inference: the AI assistant's training data (cutoff June 2026) covers 1.21.1-era APIs well, 26.1 partly, and 26.2/26.3 not at all. | Cannot be verified from sources. The dates make 'not at all' for 26.2 too strong: 26.2 snapshots ran before the June 2026 cutoff and 26.2 shipped on 2026-06-16, while 26.3 snapshots began 2026-06-23. A safer wording is '26.2 at most partly (early snapshots), 26.3 not at all'. | Mojang manifest release dates; stated model cutoff. |
