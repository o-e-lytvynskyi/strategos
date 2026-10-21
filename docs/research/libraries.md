# Library mods and development libraries for a Minecraft 1.21.1 RTS mod (Strategos)

Research date: 2026-10-20. An independent fact-check ran the same day; see "Verification notes" at the end. Corrections are made inline and marked *(corrected)*. Findings that were not in the original are marked *(added during verification)*. Target: Minecraft Java 1.21.1 on Java 21. NeoForge 21.1.x is the primary loader, and Fabric equivalents are noted where they differ. Unless a row says otherwise, a version is the newest build for **1.21.1** on 2026-10-20, and "size" is the primary NeoForge jar listed on Modrinth.

---

## 0. Bottom line

1. **The MVP can ship with zero hard runtime dependencies besides NeoForge.** NeoForge 21.1 already covers most of what the candidate libraries would add (see §2):
   - Mixin and MixinExtras (bundled);
   - data attachments with client sync;
   - datapack registries and data maps;
   - config with a generated config screen;
   - HUD layers;
   - a JSON keyframe-animation loader for vanilla models;
   - chunk-section geometry injection;
   - entity outline hooks;
   - GameTest/JUnit integration.

   The newest build is **21.1.264 (2026-10-19)**. *(corrected)* The 21.1 line is still actively patched: 12 builds between 2026-09-06 and 2026-10-19, several per week in October. Earlier gaps reached four weeks, for example 21.1.248 (2026-08-01) → 21.1.249 (2026-08-28).

   Pin `>= 21.1.264`, for two reasons:
   - *(corrected)* The network-decoding hardening listed under 21.1.260–262 first shipped in 21.1.262 (2026-10-15). Builds 21.1.260 and 21.1.261 were never published to Maven.
   - 21.1.264 fixed attachment initial sync ignoring the per-player predicate.

   The MDK template still pins 21.1.262.
2. **Rendering compatibility is the hardest constraint.** *(corrected)* Iris is the only shader mod for NeoForge or Fabric on 1.21.1. OptiFine HD U J1 exists for 1.21.1, but only for vanilla and Forge 52.0.16.
   - Iris ignores custom mod shader programs while a shader pack is active, and it officially asks mods to keep a fallback path that uses no custom shaders.
   - Flywheel turns its GPU backends off under shader packs, and its indirect backend needs OpenGL 4.6, while macOS stops at 4.1.

   Draw everything with vanilla RenderTypes and shader programs, and defer Flywheel, Veil and Lodestone.
3. **Write the simulation core, pathfinding and AI ourselves.**
   - Java ECS and behaviour-tree libraries are dormant (last releases 2019–2023) or pull in libGDX.
   - Baritone (LGPL-3.0) and MineColonies (GPL-3.0) are design references, not dependencies.
   - The core can compile against what Minecraft already ships, at exactly these versions: fastutil 8.5.12, JOML 1.10.5, DFU 8.0.16, Guava 32.1.2-jre, Gson 2.10.1, slf4j-api 2.0.9.
4. **Start unit models on vanilla.**
   - HumanoidModel/PlayerModel with the armour and held-item layers costs nothing, works with resource packs, and uses Sodium's entity-model fast path.
   - NeoForge's JSON keyframe animations cover bespoke animation without a dependency.
   - GeckoLib 4.9.3 waits for a benchmark at scale.
5. **Signal for the loader decision.** Forge 1.21.1 (52.1.20, 2026-10-15) has **no** builds of Sodium, Iris, Lithium, FerriteCore, ModernFix, C2ME, Flywheel, Veil, EMI, Jade, Patchouli or GuideME. NeoForge has all of them. Fabric has all of them except GuideME; LDLib2 is also missing on Fabric. For balance, Forge 1.21.1 does have builds of JEI, GeckoLib, Entity Culling, ImmediatelyFast, Modonomicon, WTHIT, ModernUI, Cloth Config, Forge Config API Port, JourneyMap, Xaero's Minimap and spark.

---

## 1. Method and evidence quality

- **Versions, licences, dates and sizes** come from:
  - the Modrinth API v2 (`/v2/project/{slug}` and `/version?game_versions=["1.21.1"]&loaders=[…]`, queried 2026-10-20);
  - Maven metadata from NeoForge, Maven Central, the Gradle Plugin Portal, maven.createmod.net, maven.gegy.dev and Cloudsmith;
  - GitHub, for repository licences, activity and releases.
- **API claims** come from two kinds of source:
  - The decompiled Minecraft 1.21.1 + NeoForge 21.1.262 reference sources at `/private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1`, read-only.
  - Upstream source at the 1.21.1 tag or branch for:
    - [Sodium](https://github.com/CaffeineMC/sodium/releases/tag/mc1.21.1-0.8.13) (`mc1.21.1-0.8.13`);
    - [Iris](https://github.com/IrisShaders/Iris/tree/1.21.1) (`1.21.1`);
    - [Lithium](https://github.com/CaffeineMC/lithium) (`mc1.21.1-0.15.4`);
    - [Flywheel](https://github.com/Engine-Room/Flywheel/tree/1.21.1/dev) (`1.21.1/dev`);
    - [GeckoLib](https://github.com/bernie-g/geckolib/tree/1.21.1) (`1.21.1`);
    - [ModernFix](https://github.com/embeddedt/ModernFix/tree/1.21.1) (`1.21.1`);
    - [Entity Culling](https://github.com/tr7zw/EntityCulling) (HEAD).
- **Libraries bundled with Minecraft** come from:
  - Mojang's [1.21.1 version JSON](https://piston-meta.mojang.com/v1/packages/1757c8b1fbfc6b638504e1f6cceb05893f7389d5/1.21.1.json);
  - the dedicated-server jar's `META-INF/libraries.list`;
  - NeoForge's `neoforge-21.1.262.module` and `minecraft-dependencies-1.21.1.module`. The latter is served from `maven.neoforged.net/mojang-meta`, not `/releases`.
- **Labels.** Text marked *Assessment* is my inference, and everything else comes from the cited source. I ran no game or benchmark. Performance statements about scale are therefore reasoning from code, not measurements.

---

## 2. What NeoForge 21.1 and Minecraft 1.21.1 already provide

| Need | Built-in API | Version notes | Evidence |
|---|---|---|---|
| Mixins | `net.fabricmc:sponge-mixin:0.16.4+mixin.0.8.7`, plus **MixinExtras 0.5.5 bundled jar-in-jar inside NeoForge** | Updated in 21.1.254 and 21.1.255 | `neoforge-21.1.262.module`, universal jar `META-INF/jarjar/metadata.json`, [changelog](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt) |
| Access changes | Access transformers. MDG and FML auto-detect the default `META-INF/accesstransformer.cfg` | — | [NeoForge AT docs](https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers), MDK `neoforge.mods.toml` template |
| Per-object state | Data attachments on `Entity`, `BlockEntity`, `ChunkAccess`/`LevelChunk` and `Level`, with Codec persistence and `copyOnDeath`. **Client sync** via `AttachmentType.Builder#sync(...)` and `IAttachmentHolder#syncData` | Sync backported in **21.1.197**. Initial sync honours `sendToPlayer` only from **21.1.264** | `attachment/AttachmentType.java`, changelog |
| Interop interfaces | Capabilities: `BlockCapability`, `EntityCapability`, `ItemCapability`, and `Capabilities.ItemHandler/FluidHandler/EnergyStorage` | — | `capabilities/Capabilities.java` |
| Data-driven content | Datapack registries with a network codec (`DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec)`). Data Maps (`RegisterDataMapTypesEvent`, `DataMapType.Builder#synced`) | — | `registries/DataPackRegistryEvent.java`, `registries/datamaps/` |
| Config | `ModConfigSpec`. FML 4.0.45 types are COMMON, CLIENT, SERVER and STARTUP, stored as NightConfig 3.8.3 TOML. NeoForge generates `ConfigurationScreen`, registered via `IConfigScreenFactory` | Config UI added in 21.0.110 | `client/gui/ConfigurationScreen.java` |
| HUD | `RegisterGuiLayersEvent`: `registerAbove/Below/AboveAll/BelowAll`, `replaceLayer`, `wrapLayer` | `wrapLayer` backported in 21.1.190 | `client/event/RegisterGuiLayersEvent.java` |
| Keyframe animation | Vanilla `AnimationDefinition`/`KeyframeAnimations` for `HierarchicalModel`. NeoForge `AnimationLoader` loads JSON from `assets/<ns>/neoforge/animations/entity/`, and patches overloads `HierarchicalModel#animate(AnimationState, AnimationHolder, …)` | 21.1.42; path namespaced in 21.1.44 | `client/entity/animation/json/*`, [PR #1476](https://github.com/neoforged/NeoForge/pull/1476) |
| World overlays | `RenderLevelStageEvent` (AFTER_SKY … AFTER_LEVEL). `AddSectionGeometryEvent` bakes static geometry into chunk sections. Its renderers run on the section-rebuild thread, usually not the main thread, so gather data in the event handler | — | `client/event/*` |
| Selection highlight | `IEntityExtension#hasCustomOutlineRendering(Player)` and `LevelRenderer#requestOutlineEffect()` | 21.1.51 | `common/extensions/IEntityExtension.java` |
| Per-player entity visibility | Vanilla `Entity#broadcastToPlayer(ServerPlayer)`, checked in `ChunkMap.TrackedEntity#updatePlayer`. *(corrected)* It runs on entity or player section changes **and** on every accepted player movement packet via `ChunkMap#move`. NeoForge adds `IEntityExtension#sendPairingData` | — | `server/level/ChunkMap.java` |
| Tests | GameTest via the MDK's `gameTestServer` run. MDG `unitTest` with `junit-fml 4.0.45`. `net.neoforged:testframework:21.1.264` with `EphemeralTestServerProvider` | — | MDK `build.gradle`, [MDG README](https://github.com/neoforged/ModDevGradle/blob/main/README.md), testframework jar |
| Datagen/registration | `GatherDataEvent` providers, including `JsonCodecProvider`, `DataMapProvider` and `DatapackBuiltinEntriesProvider`. `DeferredRegister.Blocks`/`Items` helpers (`registerSimpleBlock`/`registerSimpleItem`) | — | `common/data/`, `registries/DeferredRegister.java` |

NeoForge has run on Mojang names in production since 1.20.2 ("We are now using MojMaps everywhere", [20.2 release post](https://neoforged.net/news/20.2release/)). MDG reobfuscation exists only in the legacy plugin, for Forge 1.17–1.20.1 ([LEGACY.md](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)). The reference MDK build ran `compileJava → jar` with no reobf step. In practice this means:

- Mixins need no refmap.
- ATs use Mojang names.
- Any NeoForge mod jar can go straight onto the dev runtime classpath for compat testing.

---

## 3. Candidates by category

### 3.1 Entity models and animation

| Candidate | Use for Strategos | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|---|
| **Vanilla `HumanoidModel`/`PlayerModel` + `HumanoidArmorLayer`, `ItemInHandLayer`, `CustomHeadLayer`** | Villagers and infantry with procedural walk, attack swing and `ArmPose`s. Real armour and weapons render for free. 64×64 player-skin textures make per-civ skins cheap | built in | built in | — | 0 | **Adopt now** |
| **Vanilla `HierarchicalModel` + `KeyframeAnimations` + NeoForge JSON animations** | Bespoke keyframe animations (gather, build, siege engines, death) without a library. Blockbench exports them with the "Animation to JSON Converter" plugin, which a comment on PR #1476 links via blockbench-plugins PR #602. It is in the official Blockbench plugin registry as `animation_to_json` 1.0.1 | built in (21.1.42+) | Vanilla classes only. No JSON loader; the Fabric "Json Entity Animation" lib stopped at 1.20.1 | NeoForge code LGPL-2.1 | 0 | **Adopt now**, when keyframes are needed |
| [GeckoLib](https://modrinth.com/mod/geckolib) | Bedrock-format models, Molang, sound/particle keyframes, server-triggered animations, armour/item support | **4.9.3** (2026-09-16) | 4.9.3 (2026-09-16), needs Fabric API | MIT | 0.63 MB | **Consider later** |
| [AzureLib](https://modrinth.com/mod/azurelib) | GeckoLib-4-derived engine | 3.1.22 (2026-10-16); 3.1.23-alpha.2 (same day, tagged "release" on Modrinth) | same | MIT, with MPL-2.0 parts (`LICENSE_SBLForked`) | 0.97 MB | **Avoid**: a GeckoLib fork with a small community (49 GitHub stars) and no benefit over upstream |
| [playerAnimator](https://modrinth.com/mod/playeranimator) | Animations for the *player* model | 2.0.4+1.21.1 (2025-12-28, beta) | same | MIT | 0.20 MB | **Avoid**: covers players only, not our mobs |

Notes:

- **HumanoidModel is not a `HierarchicalModel` in 1.21.1.**
  - It is declared `HumanoidModel<T> extends AgeableListModel<T> implements ArmedModel, HeadedModel`, and `KeyframeAnimations.animate(...)` takes a `HierarchicalModel<?>`. Keyframed units therefore need their own hierarchical model.
  - Held items still work, because `ItemInHandLayer` only requires `ArmedModel`.
  - Vanilla armour does not, because `HumanoidArmorLayer` requires `HumanoidModel`.
  - *Assessment:* use vanilla humanoids for the MVP. Move only special units (siege, ships, animals) to hierarchical models with JSON animations.
- **Sodium speeds up vanilla models.**
  - Sodium 0.8.13 overwrites `ModelPart#translateAndRotate` and patches `ModelPart.Cube` (`mixin/features/render/entity/{ModelPartMixin,CubeMixin}`).
  - GeckoLib 4.9.3 builds its own quads (`GeoRenderer#renderCube → createVerticesOfQuad → buffer.addVertex`), so that fast path does not apply to it.
  - No GeckoLib per-entity cost figures are published (unverified: absence cannot be proven).
  - The only signals are third-party optimiser add-ons: "GeckoLib Animation Optimizer" (CurseForge, Forge 1.20.1) and, *(corrected)* on 1.21.1, GeckolibBetterFPS 1.0.2 (NeoForge, alpha, 2026-08-07), which claims about 25% faster GeckoLib rendering. The cost has to be measured.
- **GeckoLib's footprint and porting cost.**
  - GeckoLib registers its payloads as `optional()`. Once our entity classes implement `GeoEntity`, though, the dedicated server must also load GeckoLib (class loading; *assessment*).
  - *(corrected)* GeckoLib moved to the 5.x API at MC 1.21.5: 5.0-alpha1 on 2025-04-08 and 5.0 on 2025-04-21; 5.1.0 (2025-06-16) is the last 1.21.5 build. Leaving 1.21.1 later therefore means a GeckoLib migration on top of the MC port.
- **Blockbench workflow.**
  - Use "Modded Entity" export for vanilla models: the "Forge 1.17+ (Mojmaps)" template, which emits a Java `LayerDefinition` with Mojang names.
  - Use the "Animation to JSON Converter" plugin for NeoForge JSON animations.
  - Use the GeckoLib plugin only if GeckoLib is adopted.
  - Keep `.bbmodel` sources in the repo; the MDK already excludes `**/*.bbmodel` from the jar.

### 3.2 UI and HUD

| Candidate | Use | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|---|
| **Vanilla `Screen`, `GuiGraphics`, layouts (`GridLayout`, `LinearLayout`, `FrameLayout`, `HeaderAndFooterLayout`), widgets, GUI sprite atlas (nine-slice/tile via `GuiSpriteManager`)** + NeoForge **`RegisterGuiLayersEvent`** | Command card, selection panel, resource bar, minimap frame, build menus | built in | built in + Fabric API `HudRenderCallback` | — | 0 | **Adopt now** |
| [owo-lib / owo-ui](https://modrinth.com/mod/owo-lib) | Declarative XML/Java UI | **Yes, but beta:** 0.12.15.6-beta.1+1.21 (2026-10-16). Beta since the first NeoForge release on 2024-10-22 | 0.12.15.4+1.21 (2025-04-20, release); 0.13.0-alpha.15 (2026-04-29) | MIT | 1.2 MB | **Avoid** on NeoForge. Port still beta; earlier builds (0.12.15-beta.5 to 0.12.15.5-beta.1) embedded Forgified Fabric API |
| [LDLib2](https://github.com/Low-Drag-MC/LDLib2) (Modrinth "ldlib") | Full framework: Taffy-based layout, LSS stylesheets, XML UIs, `@Persisted`/`@DescSynced` sync, in-game editors | mc1.21.1-2.2.42 (2026-10-04), very active | none | LGPL-3.0 | **7.6 MB** | **Avoid**: NeoForge-only, heavy, and locks the architecture into its framework |
| [ModernUI](https://modrinth.com/mod/modern-ui) | Alternative UI and text engine | 3.13.0.1 (2026-06-06) | same | LGPL-3.0-or-later | **25 MB** | **Avoid**: client-only, changes text rendering globally, very heavy |

*Assessment.* None of these libraries solves the RTS-specific parts:

- HUD layers (`LayeredDraw.Layer`) only draw. Interactive RTS mode needs a non-pausing `Screen` that owns the cursor: `isPauseScreen()` false, and no background blur (override `renderBackground`, which calls `renderBlurredBackground`).
- That screen must raycast from the camera through the cursor to pick units and terrain.
- A small in-house widget kit on top of vanilla layouts (grid of command buttons, tooltips, hotkeys) is a few hundred lines. Being ours, it is fully testable and costs users nothing.

### 3.3 Config

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| **NeoForge `ModConfigSpec` + `ConfigurationScreen`** | built in | — | LGPL-2.1 | 0 | **Adopt now** |
| [YACL](https://modrinth.com/mod/yacl) | 3.8.2+1.21.1 (2026-01-09) | 3.8.2 (2026-01-09) | LGPL-3.0-or-later | 1.1 MB | **Avoid**: duplicates the built-in screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | 15.0.140 (**2024-09-16**, no newer 1.21.1 build) | same | LGPL-3.0-only | 1.2 MB | **Avoid** |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | v21.1.6 (2025-11-12), a 0.1 MB shim | v21.1.6 (0.63 MB, needs Fabric API) | MPL-2.0 | — | **Consider later**, only for a Fabric port: it provides the `ModConfigSpec` API on Fabric |
| [Fzzy Config](https://modrinth.com/mod/fzzy-config) | 0.7.7 (2026-09-15) | 0.7.7 | TDL-M | 2.4 MB | **Avoid** |

*Assessment.* Keep configs for client preferences and server knobs, and keep **game balance out of configs**:

- Unit, building, tech and civilisation definitions belong in **datapack registries**, synced with a network codec and generated by `JsonCodecProvider`.
- Gatherable-resource yields per block belong in **Data Maps**.

That keeps balance data versioned, diffable, testable in JUnit (decode the JSON with the same Codec), and overridable per world by datapacks, which is the 0 A.D. template model.

### 3.4 In-game guide

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| [Patchouli](https://modrinth.com/mod/patchouli) | 1.21.1-93 (2026-03-08) | 1.21.1-93 | **CC-BY-NC-SA-3.0** ([LICENSE](https://github.com/VazkiiMods/Patchouli/blob/HEAD/LICENSE)) | 0.65 MB | Consider later. Acceptable, but has a non-commercial licence and an older design |
| [Modonomicon](https://modrinth.com/mod/modonomicon) | 1.21.1-1.120.7 (2026-09-25) | same; Forge too | MIT AND CC-BY-4.0 | 2.4 MB | **Consider later**: best multi-loader option |
| [GuideME](https://github.com/AppliedEnergistics/GuideME) | 21.1.19 (2026-09-16) | **none** | LGPL-3.0 (+ MIT/Apache bundled libs) | **9.3 MB** | **Consider later**: best NeoForge-only option. Markdown pages, 3D structure scenes for showcasing buildings, search |
| [Lavender](https://modrinth.com/mod/lavender) | none | 0.1.15+1.21 (2025-01-20), needs owo-lib | MIT | 0.54 MB | Fabric only |
| [Ponder](https://github.com/Creators-of-Create/Ponder) (Create's scene tutorials) | 1.0.87+mc1.21.1 (2026-06-20; `net.createmod.ponder:ponder-neoforge` on maven.createmod.net; runtime dependency on Flywheel 1.0.4+). The Modrinth slug "ponder" is the unrelated "Ponder for KubeJS" | — | MIT | 0.82 MB | Later, for animated tutorials |

MVP: none. *Assessment:* a simple codex screen generated from the same unit and tech registries is cheap, and it never drifts from the data.

### 3.5 Compat and info mods

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| [JEI](https://modrinth.com/mod/jei) | *(corrected)* 19.69.0.457 (2026-10-20) is a **beta**. Latest release: 19.64.0.455 (2026-10-16). API `mezz.jei:jei-1.21.1-neoforge-api` on maven.blamejared.com | same | MIT | 7.35 MB (beta) / 2.38 MB (release) | **Later**, only if we add items or recipes. One JEI plugin also covers EMI ("install JEI alongside EMI… JEI will be hidden", [EMI page](https://modrinth.com/mod/emi)) |
| [EMI](https://modrinth.com/mod/emi) | 1.1.24 (2026-05-13) | same | MIT | 1.1 MB | Later, via JEI compat |
| [REI](https://modrinth.com/mod/rei) | 16.0.799 (2025-02-16, **beta** on NeoForge) | 16.0.799 release | MIT | 2.4 MB, plus Architectury and Cloth | Avoid / lowest priority |
| [Jade](https://modrinth.com/mod/jade) | 15.10.6 (2026-08-06) | same | CC-BY-NC-SA-4.0 | 0.73 MB | **Later**: unit and building tooltips in first-person mode |
| [WTHIT](https://modrinth.com/mod/wthit) | 12.10.2 (2026-03-24, beta on NeoForge) | release | CC-BY-NC-SA-4.0 | 0.86 MB, plus badpackets | Later, low priority |

Compile against compat APIs as `compileOnly`, and load the mods in dev through `localRuntime`. A compat source set keeps the main module clean.

The [Modrinth Maven](https://api.modrinth.com/maven) (`maven.modrinth:<slug>:<version or version-id>`) serves any Modrinth jar. Use the version ID where several loaders share one number. For example, Entity Culling NeoForge 1.11.3 is `eieYbWYb`, while `maven.modrinth:entityculling:1.11.3` actually resolves to the Fabric 1.19.4 jar.

### 3.6 Registration and datagen

| Candidate | NeoForge 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|
| **`DeferredRegister` (+ `.Blocks`/`.Items` helpers) + NeoForge datagen** (`BlockStateProvider`, `ItemModelProvider`, `LanguageProvider`, loot, recipes, tags, `JsonCodecProvider`, `DataMapProvider`, `DatapackBuiltinEntriesProvider`), run through the MDK `data` run | built in | — | 0 | **Adopt now** |
| [Registrate](https://github.com/tterrag1098/Registrate) | MC1.21.1-1.4.8 (2026-06-12, on `maven.gegy.dev/releases`). *(corrected)* Create's 1.21.1 build jar-in-jars a different line, `MC1.21-1.3.0+67` from `maven.ithundxr.dev/snapshots` | MPL-2.0 | 0.18 MB | **Avoid** |

*Assessment.* Registrate pays off for mods with hundreds of blocks and items. Strategos is mostly entities plus data-driven definitions, and the NeoForge helpers already remove most of the boilerplate. Registrate would add another abstraction between the code and the APIs every NeoForge doc and example uses, plus another jar-in-jar to update.

### 3.7 Rendering libraries, and Sodium/Iris compatibility of custom world rendering

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| **Vanilla RenderTypes and core shaders**, via `RenderLevelStageEvent`, `AddSectionGeometryEvent`, `VertexBuffer` and the outline API | built in | `WorldRenderEvents`; no `AddSectionGeometryEvent` equivalent in Fabric API 0.116.17 (module list checked) | — | 0 | **Adopt now** |
| [Flywheel](https://github.com/Engine-Room/Flywheel) (GPU instancing, used by Create 6) | **1.0.7-48 (2026-09-20)**, on [maven.createmod.net](https://maven.createmod.net/dev/engine-room/flywheel/flywheel-neoforge-1.21.1/maven-metadata.xml) only | 1.0.7-48 | MIT | 0.87 MB (jar-in-jar) | **Consider later** |
| [Veil](https://modrinth.com/mod/veil) (FoundryMC: framebuffers, post-processing, shader injection, "Necromancer" skeletal animation) | 4.5.2 (2026-10-10); targets 1.21.1 only | 4.5.2 | LGPL-3.0-only | 3.4 MB | **Avoid**: it is a custom shader and post stack, which Iris will not render |
| [Lodestone](https://modrinth.com/mod/lodestonelib) (VFX/particles, from Malum) | 1.8.2 (2026-01-12) | 1.21.1-1.7.0.5 (2024-12-06, beta) | LGPL-3.0-only | 0.82 MB | **Avoid** for now |
| [Satin API](https://modrinth.com/mod/satin-api) | — | 2.0.0 (2024-06-13) | LGPL-3.0 | 0.1 MB | Fabric only; avoid |

**Flywheel facts, from the 1.21.1/dev source:**

- **Distribution.**
  - It is not published as a standalone 1.21.1 mod. The CurseForge and Modrinth "Flywheel" projects are "Legacy" builds for 1.18/1.19.
  - Create 6 jar-in-jars it: `jarJar(runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-$minecraft_version:$flywheel_version") { version { strictly "[${flywheel_version},2.0)"; prefer flywheel_version } })` in [Create's build.gradle](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/build.gradle).
  - Vanillin 1.1.3 (Modrinth, 1.21.1) also jar-in-jars Flywheel 1.0.4.
- **Shader packs.** `Backends.INSTANCING` and `Backends.INDIRECT` are only `supported` when `!ShadersModHelper.isShaderPackInUse()`. With an Iris pack active, Flywheel falls back to vanilla rendering, so **a vanilla renderer has to exist anyway**. The third-party [Iris & Oculus Flywheel Compat](https://modrinth.com/mod/iris-flw-compat) add-on (1.21.1+2.4.0, 2026-05-06, CC0) exists for this case.
- **macOS.** The indirect backend needs OpenGL 4.6, or a long ARB extension list (compute shaders, multi-draw indirect, SSBOs). macOS caps at GL 4.1, so on the owner's M4 Pro only the instancing backend (GL 3.3) can run.
- **Render origin.** The docs ([concepts.md](https://github.com/Engine-Room/Flywheel/blob/1.21.1/dev/docs/flywheel/api/concepts.md)) say: "All instances are deleted and all visuals are recreated when the render origin updates". The engine moves the origin only when the camera is more than 256 blocks from it (`EngineImpl` `maxOriginDistance` = 256). *Assessment:* a hitch roughly once per 256 blocks of fast RTS panning, not every frame; magnitude unmeasured.
- **Forge.** There is no Forge 1.21.1 artifact (`flywheel-forge-1.21.1` returns 404).

**Iris rules that apply to every custom draw, verified in Iris branch 1.21.1:**

- The official doc ([core-shaders.md](https://github.com/IrisShaders/Iris/blob/1.21.1/docs/development/compatibility/core-shaders.md)) says: "Custom shaders added by mods or resource packs are ignored by Iris when an Iris shader pack is loaded". It also says mods should "avoid using their own shader programs if at all possible… create fallback rendering pathways that do not use custom shaders".
- In code, `MixinShaderInstance#iris$shouldSkipThis` disables colour and depth writes for any non-Iris `ShaderInstance` when a pack is active. The `allowUnknownShaders` setting defaults to `false`.
- Vanilla shader getters (`GameRenderer#getPositionColorShader`, `getRendertypeLinesShader`, `getRendertypeEntityTranslucentShader`, …) are overridden (`@Inject` at HEAD) to return the pack's programs, so vanilla RenderTypes keep working.
- Iris lists Botania's custom shaders and ComputerCraft's TBO renderer as unfixable on its side ([incompatible-mods.md](https://github.com/IrisShaders/Iris/blob/1.21.1/docs/usage/incompatible-mods.md)). CC: Tweaked solved it by switching to VBO mode while shaders are on.
- **Freecam.** The same doc says **Tweakeroo's freecam misbehaves with Iris** and recommends Freecam (MIT). *(corrected)*
  - The doc's link, `hashalite/Freecam`, is now a fork. The canonical repo is [MinecraftFreecam/Freecam](https://github.com/MinecraftFreecam/Freecam).
  - Its `FreeCamera` is a client-side player entity installed with `Minecraft#setCameraEntity`. It extends `LocalPlayer` in the 1.21-era source (tag `v1.3.0+mc1.21`, Modrinth build 1.3.0+mc1.21.1) and `AbstractClientPlayer` on current main. That makes it a useful reference for an Iris-safe RTS camera.
  - *(added during verification)* A purely client-side camera only sees what the server sends. The server centres chunk tracking (`ChunkMap#updateChunkTracking`, `player.chunkPosition()`) and entity tracking (`TrackedEntity#updatePlayer`, `player.position()`) on the **server-side player**, so the RTS camera design must move that player with the camera or send the viewed area another way.
- **Iris API.** Soft-depend on `IrisApi` (`isShaderPackInUse()`, `isRenderingShadowPass()`; API minor revision 2) to switch to fallbacks.
- **Embeddium.** Iris declares Embeddium incompatible. *(corrected)* The message is "Please use Sodium 0.8 instead" in 1.8.14-beta.1 and the branch head, but "Please use Sodium 0.6 instead" in the 1.8.12 release.
- *(added during verification)* **Iris–Sodium pairing.**
  - Iris 1.8.12 (the latest release) was built for Sodium 0.6. Its Modrinth dependency is Sodium mc1.21.1-0.6.13, and its mods.toml requires Sodium `[0.6,)` "Please install Sodium 0.6".
  - Sodium 0.8.13 declares `breaks: iris <1.8.13` in its Fabric metadata.
  - Iris 1.8.14-beta.1 (2026-06-13, "Updates to Sodium 0.8") is the only 1.21.1 Iris build for Sodium 0.8.x.

**Sodium rules, verified in tag mc1.21.1-0.8.13:**

- Sodium's NeoForge module still fires `RenderLevelStageEvent` for block layers (`NeoForgeLevelRenderHooks` calls `ClientHooks.dispatchRenderStage`), and it runs `AddSectionGeometryEvent` renderers while building sections.
- Entity culling (`SodiumWorldRenderer#isEntityVisible`) tests `getBoundingBoxForCulling()` against visible chunk sections. It skips the test for glowing or name-shown entities, and for boxes over 15 sections of volume. Large units such as siege engines and ships must override `getBoundingBoxForCulling`.
- Sodium's licence is **PolyForm Shield 1.0.0**: fine to be compatible with, not to copy from.
- Sodium and Lithium both read per-mod option overrides from mod metadata, so a conflicting optimisation can be switched off from our own `neoforge.mods.toml` as a last resort. The keys go in different places:
  - `sodium:options` goes inside our `[[mods]]` entry, e.g. `[mods."sodium:options"]`;
  - `lithium:options` goes at the file top level.

*Assessment, mapping RTS visuals onto these rules:*

| Visual | Approach |
|---|---|
| Selection circles, rally lines, placement ghosts | Immediate-mode quads and lines with vanilla RenderTypes in `AFTER_TRANSLUCENT_BLOCKS`/`AFTER_PARTICLES` |
| Territory borders and terrain decals | Cached per-chunk `VertexBuffer`s with vanilla shaders. `AddSectionGeometryEvent` suits rarely changing data, because every change rebuilds sections and its renderers run off the main thread |
| Fog of war | World-space darkening geometry plus server-side hiding of entities (§3.9). Avoid a custom post-processing pass, which Iris will not run |
| Selected-unit outline | NeoForge outline hooks. Behaviour under shader packs untested |

### 3.8 Mixins, access transformers, interface injection

- **Availability:** verified.
  - NeoForge 21.1.262 depends on `net.fabricmc:sponge-mixin:0.16.4+mixin.0.8.7`.
  - Its universal jar contains `META-INF/jarjar/mixinextras-neoforge-0.5.5.jar` (range `[0.5.5,)`).
  - MixinExtras 0.5.5 (2026-08-27, MIT) is the current upstream release.
  - Older 21.1 builds shipped MixinExtras 0.4.1 (21.1.22), 0.5.0 (21.1.195) or 0.5.3 (21.1.221), another reason to pin a recent NeoForge.
- **Declaring mixins:** `[[mixins]] config="${mod_id}.mixins.json"` in `neoforge.mods.toml` (MDK template). No refmap is needed (§2).
- **Access transformers:** the default `META-INF/accesstransformer.cfg` is auto-detected; declare `[[accessTransformers]]` only for other paths. Per the NeoForge docs, directives "only modify the method they directly reference", and overrides are not transformed.
- **Fog-of-war tracking (worked example).** *(corrected)*
  - Vanilla re-evaluates `broadcastToPlayer` when the entity or a player changes chunk section (`ChunkMap#tick`).
  - It also does so on every accepted player movement packet: `ChunkMap#move(ServerPlayer)` calls `updatePlayer` for every tracked entity, and the client sends a position packet at least every 20 ticks (`LocalPlayer#sendPosition`, `positionReminder >= 20`). It does so every tick for a player whose camera is another entity. Hidden units are therefore revealed or hidden within about one second without any AT.
  - An immediate per-player re-check needs no AT either: the public `ServerChunkCache#move(ServerPlayer)` costs one pass over all tracked entities.
  - A targeted per-entity re-check needs ATs on `ChunkMap#entityMap` (private) and `ChunkMap.TrackedEntity#updatePlayer` (method of a package-private class).
- **Interface injection:** MDG `interfaceInjectionData` is dev-time only, so a Mixin must add the interface at runtime (MDG README).
- [MixinSquared](https://github.com/Bawnorton/MixinSquared) (MIT): later, only if we must patch another mod's mixins.

### 3.9 Data: attachments, capabilities, components; Fabric equivalents

| Mechanism | Use in Strategos | Notes |
|---|---|---|
| **Data attachments** (NeoForge) | Per-unit persistent state (owner faction, orders queue); per-chunk territory and exploration; per-level faction state | Persistence via `serialize(Codec)`. `sync(StreamCodec)` "always writes the full data", so it suits low-frequency or moderate-size state; the `sync(AttachmentSyncHandler)` overload gets `initialSync`/`previousValue` and can delta-encode. Per-player sync uses `sync(BiPredicate<IAttachmentHolder, ServerPlayer>, codec)`, e.g. faction resources only to that faction. **Before 21.1.264 the initial sync ignored this predicate** ([changelog](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt): "Check AttachmentSyncHandler#syncToPlayer() for initial sync") |
| `SynchedEntityData` (vanilla) | Hot per-unit fields (HP fraction, current action, carried resource) | Dirty-tracked deltas through vanilla tracking |
| Custom payloads | Bulk unit snapshots, selection, commands | Batch per tick per player; *assessment:* needed at hundreds of units |
| **Capabilities** | Exposing interfaces only, e.g. `Capabilities.ItemHandler.BLOCK` on a storehouse so other mods' pipes and hoppers can interact | Not for storage |
| Data components (vanilla, 1.20.5+) | Item stacks (build wands, relics) | Attachments do not apply to `ItemStack` (holders: Entity, BlockEntity, ChunkAccess, Level) |
| Datapack registries + Data Maps | Unit, building, tech and civ definitions; block → resource yields | Synced to clients via network codec / `synced(...)` |

Fabric equivalents:

- Fabric API **0.116.17+1.21.1 (2026-09-01)** ships `fabric-data-attachment-api-v1` 1.4.7 with `AttachmentRegistry.Builder#syncWith(PacketCodec, AttachmentSyncPredicate)` ([source](https://github.com/FabricMC/fabric-api/blob/1.21.1/fabric-data-attachment-api-v1/src/main/java/net/fabricmc/fabric/api/attachment/v1/AttachmentRegistry.java)).
- [Cardinal Components API](https://modrinth.com/mod/cardinal-components-api) 6.1.3 (2025-11-22, MIT, 0.23 MB) is no longer required for synced data on Fabric. Choose it only for its component-interface ergonomics.

### 3.10 Pathfinding and AI references

**Vanilla is the wrong tool for mass RTS movement.** Evidence from decompiled 1.21.1:

- `PathNavigation` builds paths synchronously on the server thread through a `PathNavigationRegion` snapshot, with `maxVisitedNodes = floor(FOLLOW_RANGE × 16)`.
- `ServerLevel#sendBlockUpdated` loops over **all** `navigatingMobs` (every loaded `Mob`) whenever a collision shape changes, and recomputes the affected paths synchronously. That is O(units) work per block change, in a game where construction changes blocks constantly.
  - Each affected navigation recomputes at most once per 20 ticks; otherwise `PathNavigation#recomputePath` sets `hasDelayedRecomputation` and retries later.
  - With Lithium installed, `entity.inactive_navigations` limits the scan to mobs with an active path.

| Reference | 1.21.1 status | Licence | Verdict |
|---|---|---|---|
| [Baritone](https://github.com/cabaletta/baritone) | v1.11.3 for 1.21.1 (release 2026-09-01) | "LGPL-3.0 with anime exception" | **Reference only.** Client-side single-player bot. Good A* engineering (segmented paths, movement cost model). Not a library for hundreds of server-side units |
| [MineColonies](https://github.com/ldtteam/minecolonies) | Active (pushed 2026-10-13) | **GPL-3.0** | **Reference only.** Async design: a dedicated `ThreadPoolExecutor` ("Minecolonies Pathfinding Worker", 1 thread) runs `AbstractPathJob`s over cached world data ([Pathfinding.java](https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/pathfinding/Pathfinding.java)). Copying code would force GPL-compatible licensing |
| [gdx-ai](https://github.com/libgdx/gdx-ai) (pathfinding, steering, formations, BTs) | 1.8.2 (**2019-07-26**); compile dependency on libGDX `gdx` 1.9.8 | Apache-2.0 | **Avoid** |
| GitHub search (2026-10-20) for Minecraft/Java pathfinding libraries | Only Spigot plugins and bots (re-checked by the verifier the same day with the same result) | — | None reusable |

*Assessment:* write our own, in the Minecraft-free core:

- Grid walkability snapshots per region.
- Hierarchical A* (HPA*) for long routes, flow fields for group moves, and local steering and formation slots.
- Running on worker threads over immutable snapshots, then committing on the server thread.

Two hard rules:

- Never touch `Level` or `level.random` off-thread. C2ME ships `CheckedThreadLocalRandom` "to prevent mods from screwing up Minecraft internals" and will flag it ([C2ME page](https://modrinth.com/mod/c2me-neoforge)).
- Use our own seeded `java.util.random.RandomGenerator` for reproducible tests.

### 3.11 Java libraries bundled with Minecraft 1.21.1; external libraries via jarJar

**Available on both client and dedicated server.** Taken from the server jar's `META-INF/libraries.list`. NeoForge pins identical versions with Gradle `strictly` in `net.neoforged:minecraft-dependencies:1.21.1`, served from `maven.neoforged.net/mojang-meta`.

| Library | Version | Licence | Use in a Minecraft-free core |
|---|---|---|---|
| fastutil | **8.5.12** | Apache-2.0 | Yes: primitive collections for struct-of-arrays unit storage |
| JOML | **1.10.5** | MIT | Optional: vector and matrix math |
| Guava | **32.1.2-jre** (+ failureaccess 1.0.1) | Apache-2.0 | Sparingly |
| Gson | **2.10.1** | Apache-2.0 | Optional |
| DataFixerUpper | **8.0.16** (`Codec`, `MapCodec`, `RecordCodecBuilder`, `DataResult`) | MIT | Yes, if the core owns data definitions. `StreamCodec` lives in Minecraft, not DFU |
| Brigadier | 1.3.10 | MIT | No (mod layer) |
| Netty | 4.1.97.Final | Apache-2.0 | No (mod layer) |
| slf4j-api | **2.0.9** (log4j 2.22.1 behind it) | MIT | Yes (logging facade) |
| commons-lang3 / commons-io | 3.14.0 / 2.15.1 | Apache-2.0 | Avoid in the core |
| lz4-java, oshi, jna, jopt-simple, authlib, mojang logging | 1.8.0, 6.4.10, 5.14.0, 5.0.4, 6.0.54, 1.2.7 | various | No |

- **Client-only** (absent on a dedicated server, so the core must not touch them): LWJGL 3.3.3, icu4j 73.2, commons-compress 1.26.0, commons-codec 1.16.0, httpclient 4.5.13, jorbis, text2speech, java-objc-bridge.
- **NeoForge adds:** NightConfig 3.8.3, ASM 9.10.1, the Mixin and MixinExtras builds above, FML 4.0.45, event bus 8.0.5.
- **Build rule:**
  - Compile the core against these exact versions: `compileOnly` in the core, `testImplementation` for tests.
  - Because NeoForge declares them `strictly`, a core that asks for a newer fastutil or Guava will fail dependency resolution in the mod project. Gradle: "If a strict version conflicts with a consumer's version requirement, it will trigger a resolution error".
  - The newest upstream versions today are fastutil 8.5.19, JOML 1.10.9 and Guava 33.7.2.
  - DFU comes from `libraries.minecraft.net`, not Maven Central.

**External libraries (jarJar):**

| Library | Last release | Licence | Notes | Verdict |
|---|---|---|---|---|
| [Dominion ECS](https://github.com/dominion-dev/dominion-ecs-java) | 0.9.0 (**2023-07-16**); repo idle since 2024-03 | MIT | 0.16 MB, 0.x API | Avoid |
| [Artemis-odb](https://github.com/junkdog/artemis-odb) | 2.3.0 (**2019-11-09**) | BSD-2-Clause | — | Avoid |
| [Ashley](https://github.com/libgdx/ashley) | 1.7.4 (2021-02-05) | Apache-2.0 | Compile dependency on libGDX `gdx` 1.9.9 | Avoid |
| Behaviour trees: gdx-ai | 1.8.2 (2019) | Apache-2.0 | Pulls libGDX | Avoid |

No maintained, standalone Java BT library worth a dependency turned up (unverified: not re-searched by the verifier).

*Assessment:* write our own:

- Entity handles become int index plus generation.
- Components become fastutil arrays.
- Systems become plain classes with a deterministic order.
- A BT or utility-AI layer is a few hundred lines, fully testable, and needs no jar-in-jar.

If a non-mod library is ever jar-in-jarred:

- Use MDG's `jarJar(implementation("g:a")) { version { strictly '[x,y)'; prefer 'x.y.z' } }`.
- On MC < 1.21.9, also add `additionalRuntimeClasspath`. The README warns of `ClassNotFoundException` in dev runs otherwise.

*(corrected)* Example of a hidden version conflict, and how to avoid it:

- imgui-java 1.92.7.1 (2026-07-27, MIT) ships an optional `imgui-java-lwjgl3` backend module built on LWJGL 3.4.1 and `lwjgl-sdl`, but MC 1.21.1 pins LWJGL **3.3.3**.
- The `imgui-java-binding` artifact itself has no LWJGL dependency.
- [ImGuiMC](https://modrinth.com/mod/imguimc) 2.0.5+mc1.21.1 (2026-08-10, LGPL-3.0-only, 10.7 MB) jar-in-jars binding 1.92.7.1 plus natives for 1.21.1. Veil uses it as an optional dependency and does not bundle ImGui itself.
- Older `imgui-java-lwjgl3` releases targeted LWJGL 3.3.4 (1.87.7–1.89.0) or 3.3.6 (1.90.0).

### 3.12 Development and test toolchain

| Tool | Latest (date) | Recommendation |
|---|---|---|
| JUnit | 6.1.3 (2026-08-07); **5.14.4** (2026-04-26) | **5.14.4.** `junit-fml 4.0.45` is built against JUnit BOM 5.10.2, and jqwik 1.10.2 depends on JUnit Platform 1.14.4. Mixing in JUnit 6 is untested |
| AssertJ | 3.27.7 (2026-01-24); 4.0.0-M1 is a milestone | 3.27.7 |
| jqwik | 1.10.2 (2026-10-10) | Property tests for economy, combat and pathfinding invariants |
| Mockito | 5.24.0 (2026-09-23) | Sparingly. Do not mock Minecraft classes; isolate the core instead. Configure Mockito as a `-javaagent` on JDK 21 (Mockito javadoc §0.3, "Explicitly setting up instrumentation for inline mocking (Java 21+)") |
| JMH | 1.37 (2023-08-03, still current) | Microbenchmarks for the core (spatial index, pathfinding, tick) |
| `me.champeau.jmh` plugin | **0.7.3** (Plugin Portal 2025-01-30; GitHub release 2025-04-16). *(corrected)* Functional tests at that tag ran on Gradle 7.0, 8.0 and the 8.12 wrapper | Gradle 9 support was merged 2026-07-19 ([#282](https://github.com/melix/jmh-gradle-plugin/issues/282), PR #288, which also changed `JMHTask` annotations for Gradle 9 validation) **but is not released**. Try it on Gradle 9.2.1; fall back to a `jmh` source set with `jmh-generator-annprocess` and a `JavaExec` running `org.openjdk.jmh.Main` |
| Spotless | 8.10.4 (2026-10-08) | With **palantir-java-format 2.102.0** (2026-10-05). Its 120-column default suits Minecraft's long names. google-java-format 1.37.0 (2026-10-01) is the alternative |
| Error Prone | 2.50.0 (2026-06-10); `net.ltgt.errorprone` 5.1.1 (2026-08-25) | Adopt. "Error Prone must be run on JDK 21 or newer" ([docs](https://errorprone.info/docs/installation)), which matches our toolchain |
| NullAway | 0.14.2 (2026-09-25); `net.ltgt.nullaway` 3.3.0 (2026-10-05) | Start in **standard mode**, which honours JSpecify annotations. JSpecify mode needs javac ≥ 22, or OpenJDK ≥ 21.0.8 (not Oracle JDK) with `-XDaddTypeAnnotationsToSymbol=true` ([wiki](https://github.com/uber/NullAway/wiki/JSpecify-Support)) |
| JSpecify | 1.0.1 (2026-07-29) | `@NullMarked` packages; `compileOnly` |
| ArchUnit | 1.5.1 (2026-09-25) | Enforce that common code never references `net.minecraft.client..`, catching dedicated-server crashes at test time, plus core layering rules |
| NeoForge testing | `testframework` 21.1.264; MDG `unitTest { enable(); testedMod = mods.strategos }`; GameTests via the `gameTestServer` run | Pure-core JUnit for logic; GameTests for in-world behaviour; both in CI |

### 3.13 Performance mods to stay compatible with, and pitfalls with many entities

| Mod | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | What to watch |
|---|---|---|---|---|
| [Sodium](https://modrinth.com/mod/sodium) | **mc1.21.1-0.8.13** (2026-08-28) | same | PolyForm Shield 1.0.0 | Entity culling by `getBoundingBoxForCulling` and section visibility; model fast path; fires NeoForge stage events. Needs Iris ≥ 1.8.13, i.e. 1.8.14-beta.1 (*corrected*) |
| Embeddium | 1.0.15+mc1.21.1 (2025-01-24), last | — | LGPL-3.0 | Obsolete on 1.21.1; Iris marks it incompatible. Do not target |
| [Iris](https://modrinth.com/mod/iris) | 1.8.12 (2025-06-17, release, **built for Sodium 0.6**); 1.8.14-beta.1 (2026-06-13, the only 1.21.1 build for Sodium 0.8) *(corrected)* | 1.8.14-beta.1 (latest Fabric release: 1.8.8, 2025-02-20) | LGPL-3.0 | Custom shaders ignored under packs; detached-camera issues. Pair Sodium 0.8.13 with Iris 1.8.14-beta.1, or Sodium 0.6.13 with Iris 1.8.12 |
| Oculus | no 1.21.1 build | — | — | Obsolete |
| [Lithium](https://modrinth.com/mod/lithium) | mc1.21.1-0.15.4 (2026-06-27) | same | LGPL-3.0 | Mixins in `ai.pathing`, `entity.collisions`, `entity.inactive_navigations`; mod-side overrides via `lithium:options` |
| Radium | 0.13.1 (2024-09-20), last | — | LGPL-3.0 | Superseded by Lithium on NeoForge |
| [ModernFix](https://modrinth.com/mod/modernfix) | 5.27.26 (2026-10-08) | 5.25.1 (2025-11-08) | LGPL-3.0 | Opt-in `dynamic_entity_renderers` ("Some mods are not compatible… EntityRenderer crashes") and `dynamic_resources`, both default `false`. Never cache renderers or baked models at startup |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | 7.0.3 (2026-01-25) | same | MIT | Low risk |
| [Entity Culling](https://modrinth.com/mod/entityculling) | 1.11.3 (2026-10-08) | same | tr7zw Protective License (no commercial use) | Async occlusion culling plus **client tick culling** (`tickCulling=true` by default): for culled or off-screen entities `Entity#tick` is replaced by a minimal `basicTick`. Respects the vanilla `noCulling` flag; does not cull hitboxes over 50 blocks or entities beyond `tracingDistance` (128); API `addDynamicEntityWhitelist(Function<Entity,Boolean>)`; config whitelists |
| [C2ME](https://modrinth.com/mod/c2me-neoforge) | 0.4.0-alpha.0.123+1.21.1 (2026-10-11, **all 1.21.1 builds alpha**) | c2me-fabric 0.4.0-alpha.0.30 | MIT | Threaded chunk generation and IO; never use world random off-thread |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 1.6.15 (2026-10-13) | same | LGPL-3.0-or-later | Batches GUI/HUD drawing; test our HUD with it |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | 3.3.3 (2026-09-29) | same | LGPL-3.0 | 28 MB. Our overlays will not appear on LOD terrain; test later |
| Moonrise, Krypton | 0.1.0-beta.15 (2025-10-10); Krypton Fabric-only 0.2.8 | GPL-3.0 / LGPL-3.0 | | Low priority |

**Pitfalls for mods with many custom entities**, all verified in decompiled 1.21.1:

1. **Cramming.** The gamerule `maxEntityCramming` defaults to 24. `LivingEntity#pushEntities` runs an AABB entity query per entity per tick. It deals 6.0 cramming damage, rolled with a 1-in-4 chance per tick, when more than 24 entities overlap. RTS blobs will hit this; override pushing and cramming behaviour for units.
2. **Navigation recompute storm.** `ServerLevel#sendBlockUpdated` scans every navigating mob on each collision-shape change (§3.10). Recomputes are throttled to once per 20 ticks per mob.
3. **Tracking cost.** *(corrected)*
   - Each tracked entity in ticking range calls `ServerEntity#sendChanges()` every server tick.
   - Visibility (`broadcastToPlayer`) is re-checked on chunk-section changes **and** for every tracked entity on every accepted movement packet of each player (`ChunkMap#move`, at least once per second per player). Keep `broadcastToPlayer` O(1).
   - Set `clientTrackingRange`/`updateInterval` per unit type deliberately. Vanilla mobs use 8–10 chunks and the default interval 3; only players use 2.
4. **Culling boxes.** Sodium, Entity Culling and vanilla frustum checks all use `getBoundingBoxForCulling()`. Large units must override it, or set `noCulling`.
5. **Thread safety.** C2ME runs generation in parallel. Our chunk attachments and generation hooks must tolerate off-main-thread calls, and our worker threads must never call into `Level`. On the client, `AddSectionGeometryEvent` renderers also run off the main thread.
6. *(added during verification)* **Entity render distance.**
   - Vanilla `Entity#shouldRenderAtSqrDistance` renders an entity only within `avgBoxSize × 64 × viewScale` blocks.
   - `viewScale` = clamp(renderDistance / 8, 1, 2.5) × the Entity Distance option.
   - A player-sized unit at render distance 8 therefore disappears beyond about 64 blocks from the camera. An elevated RTS camera needs unit entities to override `shouldRenderAtSqrDistance`.
7. *(added during verification)* **Server tracking follows the player, not the camera.** Chunks are sent around the server-side player's chunk and entities within `clientTrackingRange` of the player's position. A detached client camera sees nothing beyond that.
8. *(added during verification)* **Client tick culling.** With Entity Culling installed, client-side logic in `tick()` (e.g. `AnimationState` updates, local particles) freezes for off-screen units. Use `addDynamicEntityWhitelist` or drive animation from the renderer.

### 3.14 Dev tools

- [spark](https://modrinth.com/mod/spark): **1.10.124-neoforge-1.21.1 (2025-02-23)**; Fabric 1.10.109 (2024-09-26). GPL-3.0, 3.6 MB.
  - Keep it in dev as `localRuntime` (`maven.modrinth:spark:1.10.124-neoforge-1.21.1`); never ship it.
  - ModernFix has opt-in `spark_profile_launch` and `spark_profile_world_join` options for profiling startup and world joins.
- Vanilla `/jfr start|stop` (Java Flight Recorder; MC ships `flightrecorder-config.jfc`) records tick, network and chunk events with no mod.
- **GPU debugging on macOS is weak.**
  - *(corrected: source)* RenderDoc supports Windows, Linux and Android only. [renderdoc.org](https://renderdoc.org/): "RenderDoc supports Windows, Linux, and Android for capture and replay out of the box"; the [FAQ](https://renderdoc.org/docs/getting_started/faq.html) calls Metal support undecided.
  - macOS OpenGL stops at 4.1.
  - *Assessment:* if rendering work grows, keep a Windows or Linux machine for RenderDoc and for Flywheel indirect testing.

---

## 4. Fabric equivalents at a glance

| Need | NeoForge 21.1 | Fabric 1.21.1 |
|---|---|---|
| Mixin / MixinExtras | Bundled (0.16.4 / 0.5.5) | Bundled in Fabric Loader (0.20.1: sponge-mixin 0.17.5, MixinExtras 0.5.5) |
| Access changes | Access transformers | Access wideners (Loom) |
| Synced per-object data | Attachments (`sync`) | Data Attachment API `syncWith` (Fabric API 0.116.17), or CCA 6.1.3 |
| Config + screen | `ModConfigSpec` + `ConfigurationScreen` | Forge Config API Port v21.1.6, YACL 3.8.2, Cloth 15.0.140, plus Mod Menu |
| HUD | `RegisterGuiLayersEvent` | `HudRenderCallback` |
| World render hooks | `RenderLevelStageEvent`, `AddSectionGeometryEvent` | `WorldRenderEvents`; no section-geometry hook |
| JSON keyframe animations | `AnimationLoader` | None (write our own) |
| Registration / datagen | `DeferredRegister`, `GatherDataEvent` | `Registry.register`, Fabric Data Generation API |
| Game tests | NeoForge GameTest + testframework + junit-fml | `fabric-gametest-api-v1` |
| Models | GeckoLib 4.9.3 | GeckoLib 4.9.3 (+ Fabric API) |
| Guide | GuideME, Modonomicon, Patchouli | Lavender (owo), Modonomicon, Patchouli |
| Instancing | Flywheel 1.0.7-48 | Flywheel 1.0.7-48 |

---

## 5. Recommended minimal dependency set for the MVP

**Shipped runtime dependencies: none.** Only `neoforge >= 21.1.264` and `minecraft [1.21.1]`. The MDK template pins 21.1.262, so bump `neo_version`.

Build sketch (Groovy DSL, MDG 2.0.148 is the latest, 2026-09-29). Not run; versions as above. *(corrected: Iris coordinate)*

```groovy
// core: pure Java 21, no Minecraft on the classpath. Exact MC-bundled versions.
dependencies {
    compileOnly 'it.unimi.dsi:fastutil:8.5.12'
    compileOnly 'org.slf4j:slf4j-api:2.0.9'
    compileOnly 'org.jspecify:jspecify:1.0.1'
    // optional: 'org.joml:joml:1.10.5', 'com.mojang:datafixerupper:8.0.16' (libraries.minecraft.net)
    testImplementation platform('org.junit:junit-bom:5.14.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly   'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.assertj:assertj-core:3.27.7'
    testImplementation 'net.jqwik:jqwik:1.10.2'
    testImplementation 'it.unimi.dsi:fastutil:8.5.12'
}
// mod: neoForge { version = '21.1.264'; unitTest { enable(); testedMod = mods.strategos } }
dependencies {
    testImplementation 'net.neoforged:testframework:21.1.264'
    localRuntime 'maven.modrinth:spark:1.10.124-neoforge-1.21.1'      // dev only
    if (providers.gradleProperty('compat').isPresent()) {              // ./gradlew runClient -Pcompat
        localRuntime 'maven.modrinth:sodium:mc1.21.1-0.8.13-neoforge'
        localRuntime 'maven.modrinth:iris:1.8.14-beta.1+1.21.1-neoforge' // only Iris build for Sodium 0.8; 1.8.12 needs Sodium 0.6
        // all-release alternative: sodium mc1.21.1-0.6.13-neoforge + iris 1.8.12+1.21.1-neoforge
        localRuntime 'maven.modrinth:lithium:mc1.21.1-0.15.4-neoforge'
        localRuntime 'maven.modrinth:modernfix:5.27.26+mc1.21.1'
        localRuntime 'maven.modrinth:ferrite-core:7.0.3-neoforge'
        localRuntime 'maven.modrinth:entityculling:eieYbWYb'           // 1.11.3 NeoForge (version id)
        localRuntime 'maven.modrinth:immediatelyfast:1.6.15+1.21.1-neoforge'
    }
}
// plugins: com.diffplug.spotless 8.10.4 (palantirJavaFormat '2.102.0'), net.ltgt.errorprone 5.1.1
// (errorprone 2.50.0), net.ltgt.nullaway 3.3.0 (nullaway 0.14.2); ArchUnit 1.5.1 in tests; JMH 1.37.
```

Two ways to put `core` inside the mod jar. Confirm which one in the first build spike:

- A separate source set in the same project, declared in MDG's `mods { strategos { sourceSet … } }`. The README shows multi-source-set mods and `addModdingDependenciesTo`.
- A plain `java-library` subproject whose classes are merged into the mod jar.

Do not jar-in-jar the core as a separate library: on MC < 1.21.9 that needs `additionalRuntimeClasspath` in dev runs.

## 6. "Later" list, in order of likely need

1. **Iris fallback and test pass** with two or three popular shader packs. Soft dependency on `IrisApi` (Modrinth Maven jar, `compileOnly`). Test with Sodium 0.8.13 + Iris 1.8.14-beta.1.
2. **GeckoLib 4.9.3**, only if the art direction needs bespoke animated models beyond vanilla plus JSON keyframes. First benchmark 300–500 animated units against vanilla models with Sodium.
3. **Flywheel 1.0.7** as an optional render backend, only if profiling shows entity rendering is the bottleneck. Keep the vanilla renderer as the fallback for Iris and macOS.
4. **JEI plugin** (also covers EMI) if items or recipes appear. **Jade plugin** for unit tooltips.
5. **Guide:** GuideME (NeoForge-only, 3D building scenes) or Modonomicon (multi-loader); Ponder for animated tutorials.
6. **Minimap integration** (JourneyMap 1.21.1-6.0.9 with its public API repo; Xaero's 26.6.0, all rights reserved) for territory overlays.
7. **Distant Horizons 3.3.3** compatibility pass for long-range top-down views.
8. **Forge Config API Port**, only for a Fabric port.
9. **MixinSquared**, only if another mod's mixins must be patched.
10. **Dev-only ImGui overlay** *(corrected)*: use ImGuiMC 2.0.5+mc1.21.1 (bundles imgui-java-binding 1.92.7.1 for 1.21.1), or the binding with our own GL backend. Do not use the `imgui-java-lwjgl3` 1.92.x backend, which needs LWJGL 3.4.1.

## 7. Avoid list, with reasons

| Avoid | Reason |
|---|---|
| owo-lib on NeoForge | Beta port |
| LDLib2 | 7.6 MB, NeoForge-only framework lock-in |
| ModernUI | 25 MB, global UI and text changes |
| YACL, Cloth Config, Fzzy Config | Redundant with NeoForge's config screen |
| Registrate | Unneeded abstraction for an entity-centric mod |
| Veil, Lodestone, Satin | Custom shader and post stacks that Iris will not render |
| AzureLib | Fork of GeckoLib |
| REI on NeoForge | Stale beta |
| Embeddium, Oculus, Radium | Obsolete on 1.21.1 |
| ECS and BT libraries | Dormant, or pull in libGDX |
| Baritone, MineColonies code | Licence and architecture mismatch; read for ideas only |
| Vanilla per-unit `PathNavigation` for mass movement | Synchronous A* per mob; recompute storms on block changes (§3.10) |

## Open questions (status after verification)

1. Actual server-tick and client-frame cost of 300–1000 vanilla-entity units (with and without Sodium/Lithium). Unchanged: this decides entity-based units versus lightweight virtual units with custom rendering and sync, and no benchmark was run.
2. Do translucent overlays drawn in `RenderLevelStageEvent` with vanilla RenderTypes (selection circles, fog darkening) render correctly under popular Iris shader packs? Unchanged; needs testing with 2–3 packs on Sodium 0.8.13 + Iris 1.8.14-beta.1.
3. Does NeoForge's entity outline API (`hasCustomOutlineRendering` / `requestOutlineEffect`) work while an Iris shader pack is active? Unchanged.
4. GeckoLib 4.9.3 per-entity CPU cost at RTS scale. No published benchmark found. GeckolibBetterFPS (1.21.1, alpha) claims about 25% render speedup, a hint that GeckoLib's render cost is significant.
5. How costly are Flywheel's render-origin resets under a fast-panning RTS camera? Partly answered: resets happen only when the camera moves more than 256 blocks from the current origin. Magnitude unmeasured.
6. Does `me.champeau.jmh` 0.7.3 work on Gradle 9.2.1 with the configuration cache? Unchanged. 0.7.3 was tested only up to Gradle 8.12, and the unreleased fix changes task annotations for Gradle 9 validation, so expect the fallback to be needed.
7. Can MDG 2.0.148 attach a source set from a separate core subproject to the mod, or should core be a source set of the mod project? Unchanged; verify in the first build spike.
8. How does Entity Culling's client tick culling interact with units viewed from an elevated RTS camera? Mostly answered:
   - Beyond 128 blocks nothing is occlusion-culled.
   - Any entity not rendered last frame, or occlusion-culled, gets only `basicTick` (old pos/rot, `tickCount`, `aiStep`), so client logic in `tick()` freezes off-screen.
9. Is the project licence still open? It determines whether GPL code (MineColonies) could ever be adapted, and the obligations when bundling LGPL/MPL libraries. Unchanged.
10. Does NeoForge's `AnimationLoader` JSON format exactly match current Blockbench "Animation to JSON Converter" output for 1.21.1? Partly answered: the registry entry (`animation_to_json` 1.0.1) targets "the JSON format for the NeoForge mod loader". Confirm with a sample export.
11. Which imgui-java version still targets LWJGL 3.3.3? Answered:
    - No `imgui-java-lwjgl3` release targets exactly 3.3.3: 1.87.7–1.89.0 use 3.3.4, 1.90.0 uses 3.3.6, 1.92.x uses 3.4.1 + SDL.
    - ImGuiMC 2.0.5+mc1.21.1 ships binding 1.92.7.1 for 1.21.1 without that backend.
12. How do Distant Horizons LOD terrain and our territory/fog overlays interact under a top-down camera? Unchanged.
13. *(added during verification)* Which camera architecture keeps server-side chunk and entity tracking aligned with the RTS camera? Options include moving the server-side player with the camera, or custom chunk tickets plus extended tracking. This should be decided before the camera is built (§3.7, §3.13 pitfall 7).

## Verification notes

**What was checked (2026-10-20), all from primary sources:**

- **NeoForge Maven:**
  - `maven-metadata.xml` and POM `Last-Modified` headers for 21.1.240–264;
  - the 21.1.264 changelog;
  - `neoforge-21.1.262.module` and the universal jar's `META-INF/jarjar/metadata.json`;
  - the 21.1.264 sources jar (`AttachmentSync`);
  - the testframework 21.1.264 jar and the junit-fml 4.0.45 POM;
  - `minecraft-dependencies-1.21.1.module`.
- **FML 4.0.45 loader jar** (javap of `ModConfig$Type` and `VersionSupportMatrix`).
- **Mojang:** the 1.21.1 version JSON and the dedicated server jar's `libraries.list`.
- **Modrinth API v2:** about 60 projects, each queried per loader for 1.21.1, plus version dependency lists and changelogs.
- **Modrinth Maven:** resolution of every compat coordinate.
- **CurseForge** via CFWidget, as a spot check of the Forge claim.
- **Other Maven repositories:** Maven Central, Gradle Plugin Portal, maven.createmod.net, maven.gegy.dev, maven.ithundxr.dev, maven.blamejared.com, maven.fabricmc.net, Forge Maven and promotions.
- **GitHub:** repo metadata, releases and source at the cited tags/branches for:
  - Iris 1.21.1, Sodium mc1.21.1-0.8.13, Lithium mc1.21.1-0.15.4;
  - Flywheel 1.21.1/dev, GeckoLib 1.21.1, ModernFix 1.21.1, Entity Culling main;
  - Create mc1.21.1/dev, Freecam, MineColonies, Baritone, LDLib2, Veil;
  - the jmh-gradle-plugin tag, issue and PR;
  - the Blockbench plugin registry and Modded Entity templates.
- **Decompiled MC 1.21.1 + NeoForge 21.1.262 sources** (read-only), and the MDK template.
- **Docs:** NeoForge AT docs, NeoForge 20.2 post, MDG README and LEGACY.md, Error Prone install page, NullAway JSpecify wiki, RenderDoc home and FAQ, Apple's archived OpenGL support article, OptiFine downloads, Gradle rich-version docs, Mockito javadoc.
- **Downloaded jars**, inspected for metadata only: Iris 1.8.12 and 1.8.14-beta.1 NeoForge, Vanillin 1.1.3, Veil 4.5.2, ImGuiMC 2.0.5, server.jar.

**What changed in the report:**

- **NeoForge cadence.** 21.1.260/261 were never published; the hardening first shipped in 21.1.262. "Every week" was softened.
- **Iris/Sodium pairing (decision-relevant).**
  - Iris 1.8.12 targets Sodium 0.6, and Sodium 0.8.13 declares Iris < 1.8.13 broken.
  - The compat profile now uses Iris 1.8.14-beta.1 and lists the all-release alternative.
  - The Embeddium quote was attributed to the correct Iris build.
- **Fog-of-war tracking (decision-relevant).** `broadcastToPlayer` is re-evaluated on every accepted player movement packet (`ChunkMap#move`), at least once per second, not only on section changes. A forced re-check is possible without an AT via `ServerChunkCache#move`, and `broadcastToPlayer` must be O(1).
- **Other factual corrections:**
  - "Iris is the only shader mod" now excludes OptiFine for Forge.
  - JEI 19.69.0.457 is a beta.
  - Create bundles Registrate MC1.21-1.3.0+67, not the 1.4.x line.
  - The Freecam repo moved, and `FreeCamera` extends `LocalPlayer` in the 1.21-era source.
  - GeckoLib 5.x started with 5.0 (2025-04-21).
  - Vanilla mob `updateInterval` is 3.
  - The jmh plugin 0.7.3 was tested on Gradle 7.0/8.0/8.12.
  - The RenderDoc platform statement comes from the home page, not the FAQ.
  - The imgui-java/LWJGL conflict only concerns the optional backend module; ImGuiMC ships the 1.92.7.1 binding for 1.21.1.
- **Precision added:**
  - MixinExtras 0.5.3 in 21.1.221;
  - `sync(AttachmentSyncHandler)` delta option;
  - 20-tick recompute throttle and Lithium `inactive_navigations`;
  - 1/4 cramming roll;
  - Flywheel 256-block origin threshold;
  - `AddSectionGeometryEvent` off-thread renderers;
  - placement of the `sodium:options` and `lithium:options` keys;
  - Vanillin also bundles Flywheel;
  - GeckolibBetterFPS on 1.21.1;
  - the owo-lib "embedded" dependency;
  - AzureLib alpha tagged release;
  - Iris Fabric latest release 1.8.8;
  - Ponder's exact artifact path;
  - DFU repository;
  - the `mojang-meta` repository for `minecraft-dependencies`;
  - Forge 1.21.1 builds that do exist, for balance.
- **Added during verification** (not in the original): pitfalls 6–8 in §3.13 and open question 13.

**Not verified / limits:**

- No game, benchmark or Gradle build was run.
- CurseForge was only spot-checked; FerriteCore, Iris, Veil and Flywheel slugs could not be queried there.
- Negative claims remain unverified: no GeckoLib per-entity benchmark, no maintained Java BT library.
- That Apple silicon stops at OpenGL 4.1 rests on Apple's archived Intel-era list plus Khronos/community sources.
- The `strictly` resolution failure is from Gradle's documentation, not from a local build.

**Process note:** one Modrinth search request during this check accidentally carried the owner's e-mail address in its User-Agent header; all other requests used a generic agent string.

## Recommendations (after verification)

1. 1. Ship the MVP with zero hard runtime dependencies, on NeoForge >= 21.1.264. That build fixes attachment initial-sync privacy (a per-player predicate is now checked on initial sync) and includes the network-decoding hardening that first shipped in 21.1.262 (21.1.260/261 were never published). NeoForge already bundles Mixin 0.16.4 and MixinExtras 0.5.5. Bump the MDK template's neo_version from 21.1.262.
2. 2. Build the simulation core as a Minecraft-free module compiled against the exact MC-bundled versions: fastutil 8.5.12, slf4j-api 2.0.9 and JSpecify 1.0.1; add JOML 1.10.5 and DFU 8.0.16 (from libraries.minecraft.net) only if needed. NeoForge pins these with Gradle 'strictly', so newer versions fail resolution. Write our own SoA entity store, pathfinding (HPA* and flow fields on worker threads over immutable snapshots) and AI (BT/utility). Jar-in-jar no external libraries.
3. 3. Render units with vanilla HumanoidModel/PlayerModel plus the armour, held-item and head layers. Use NeoForge JSON keyframe animations with custom HierarchicalModels only for special units. Override getBoundingBoxForCulling for large units and shouldRenderAtSqrDistance for all units: vanilla stops rendering a player-sized entity beyond about 64 × viewScale blocks, which an elevated RTS camera will hit. Decide on GeckoLib 4.9.3 only after a 300–500-unit benchmark against the vanilla path with Sodium.
4. 4. Draw every custom world visual (selection circles, territory, decals, fog overlay) with vanilla RenderTypes and shader programs, via RenderLevelStageEvent, cached VertexBuffers or AddSectionGeometryEvent. AddSectionGeometryEvent renderers run off the main thread, so gather data in the event handler. Ship no custom ShaderInstance or post pass without a fallback that checks IrisApi.isShaderPackInUse(). Base the RTS camera on a camera entity (Freecam-style, from MinecraftFreecam/Freecam) rather than a hacked detached camera. The server sends chunks and entities only around the server-side player's position, so the camera design must move that player with the camera, or otherwise make the server send the viewed area.
5. 5. Build UI on vanilla Screen, GuiGraphics, layouts and nine-slice GUI sprites, plus RegisterGuiLayersEvent for passive HUD. Write a small in-house RTS widget kit inside a non-pausing Screen: isPauseScreen() false, and override renderBackground to skip the blur. Do not adopt owo-lib, LDLib2 or ModernUI.
6. 6. Use ModConfigSpec and NeoForge's ConfigurationScreen for settings only. Put balance and content (units, buildings, techs, civs) in datapack registries generated with JsonCodecProvider, and per-block resource yields in Data Maps.
7. 7. Store persistent per-unit, per-chunk and per-faction state in data attachments, with per-player sync predicates for secret data. Use sync(AttachmentSyncHandler) when deltas matter. Use SynchedEntityData for hot per-unit fields and batched custom payloads for bulk updates. Use capabilities only for interop (IItemHandler on drop sites). For server-side fog of war, use Entity#broadcastToPlayer. Vanilla re-evaluates it on section changes and on every accepted player movement packet, at least once per second per player. Force an immediate re-check with the public ServerChunkCache#move(player), or with an AT on ChunkMap.entityMap plus TrackedEntity#updatePlayer for per-entity updates. Keep broadcastToPlayer O(1), because it runs for every tracked entity on every player movement packet.
8. 8. Adopt this toolchain: JUnit 5.14.4, AssertJ 3.27.7, jqwik 1.10.2, Mockito 5.24.0 (as a -javaagent; sparingly; never mock Minecraft), JMH 1.37, Spotless 8.10.4 with palantir-java-format 2.102.0, Error Prone 2.50.0 with NullAway 0.14.2 in standard mode and JSpecify annotations, ArchUnit 1.5.1 for client/common separation, MDG unitTest, testframework 21.1.264, and GameTests in CI. For JMH, try me.champeau.jmh 0.7.3 but expect to need the JavaExec fallback: it was tested only up to Gradle 8.12, and the Gradle 9 fix is unreleased.
9. 9. Keep a dev-only compat profile (localRuntime behind -Pcompat) through the Modrinth Maven. Use Sodium mc1.21.1-0.8.13 with Iris 1.8.14-beta.1+1.21.1-neoforge, not Iris 1.8.12, which targets Sodium 0.6 and is declared broken by Sodium 0.8.13; add a shader pack. Optionally add an all-release profile with Sodium mc1.21.1-0.6.13 and Iris 1.8.12. Also include Lithium 0.15.4, ModernFix 5.27.26, FerriteCore 7.0.3, Entity Culling by version ID eieYbWYb (the plain '1.11.3' resolves to a Fabric 1.19.4 jar), ImmediatelyFast 1.6.15, and C2ME for server tests. Keep spark 1.10.124 in every dev run.
10. 10. Design units around known vanilla costs:
- Disable or replace cramming and pushing (cap 24, 6.0 damage).
- Avoid vanilla PathNavigation for mass movement; every collision-shape change scans all mobs.
- Override getBoundingBoxForCulling and shouldRenderAtSqrDistance.
- Set clientTrackingRange and updateInterval per unit type; vanilla mobs use 8–10 chunks and interval 3.
- If Entity Culling is present, register addDynamicEntityWhitelist or drive animation from the renderer, so client tick() logic does not freeze for off-screen units.
11. 11. Defer to the later list:
- Flywheel 1.0.7: jar-in-jar, with a vanilla fallback, only if profiling shows a render bottleneck. Its render origin resets once per 256 blocks of camera travel.
- JEI plugin, which also covers EMI. Target the 19.64.0.455 release API unless the 19.69 beta is needed.
- Jade plugin.
- A guide (GuideME or Modonomicon).
- Minimap integration and a Distant Horizons pass.
- For a dev-only ImGui overlay, ImGuiMC 2.0.5+mc1.21.1 rather than the imgui-java-lwjgl3 backend, which needs LWJGL 3.4.1.
12. 12. If rendering work grows, keep a Windows or Linux GPU machine for RenderDoc and for Flywheel's indirect backend: macOS offers OpenGL 4.1 only, and RenderDoc supports only Windows, Linux and Android.

## Open questions (from the original research)

- Actual server-tick and client-frame cost of 300–1000 vanilla-entity units (with and without Sodium/Lithium). This decides entity-based units versus lightweight virtual units with custom rendering and sync; no benchmark was run.
- Do translucent overlays drawn in RenderLevelStageEvent with vanilla RenderTypes (selection circles, fog darkening) render correctly under popular Iris shader packs? Needs testing with 2–3 packs.
- Does NeoForge's entity outline API (hasCustomOutlineRendering / requestOutlineEffect) work while an Iris shader pack is active?
- GeckoLib 4.9.3 per-entity CPU cost at RTS scale: no published benchmark was found.
- How costly are Flywheel's render-origin resets (all visuals recreated) under a fast-panning RTS camera?
- Does me.champeau.jmh 0.7.3 work on Gradle 9.2.1 with the configuration cache, given the Gradle 9 fix is merged but unreleased?
- Can MDG 2.0.148 attach a source set from a separate core subproject to the mod, or should core be a source set of the mod project? Verify in the first build spike.
- How does Entity Culling's client tick culling (default on) interact with client-side interpolation/animation of units viewed from an elevated RTS camera beyond its 128-block tracing distance?
- Is the project licence still open? It determines whether GPL code (MineColonies) could ever be adapted, and the obligations when bundling LGPL/MPL libraries.
- Does NeoForge's AnimationLoader JSON format exactly match current Blockbench 'Animation to JSON Converter' output for 1.21.1? Confirm with a sample export.
- Which imgui-java version still targets LWJGL 3.3.3, if a dev-only debug overlay is wanted? 1.92.7.1 targets LWJGL 3.4.1.
- How do Distant Horizons LOD terrain and our territory/fog overlays interact under a top-down camera?

## Fact-check verdicts

confirmed: 31, corrected: 11, refuted: 2

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| corrected | NeoForge 21.1.x is actively maintained: 21.1.264 was published 2026-10-19; 21.1.260–262 (Oct 2026) enforced network decoding/payload limits; the 21.1 line gets fixes every week. | 21.1.264 is the newest 21.1 build (POM Last-Modified 2026-10-19 03:40 UTC) and the changelog entries for 260–262 are as described. But 21.1.260 and 21.1.261 were never published: their POMs return 404 and they are missing from maven-metadata.xml. Those changes first shipped in 21.1.262 (2026-10-15). 'Every week' is too strong. October 2026 had several builds per week, 21.1.250→251 was a 12-day gap, and 21.1.248 (2026-08-01)→21.1.249 (2026-08-28) was a four-week gap. | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (…259, 262, 263, 264); HEAD on neoforge-21.1.26x.pom (260/261: 404; 262: 2026-10-15; 263: 2026-10-18; 264: 2026-10-19); https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt lines 1-12 |
| confirmed | NeoForge 21.1.262 depends on net.fabricmc:sponge-mixin 0.16.4+mixin.0.8.7 and bundles MixinExtras 0.5.5 as jar-in-jar; no separate mixin library is needed. | Correct. One addition: besides 0.4.1 (21.1.22) and 0.5.0 (21.1.195), 21.1.221 shipped MixinExtras 0.5.3. MixinExtras 0.5.5 is the latest upstream release (Maven Central 2026-08-27, GitHub release 2026-08-28, MIT). | neoforge-21.1.262.module, modDevApiElements variant (sponge-mixin 0.16.4+mixin.0.8.7, mixinextras-neoforge 0.5.5); neoforge-21.1.262-universal.jar META-INF/jarjar/metadata.json (mixinextras-neoforge 0.5.5, range [0.5.5,)); changelog 21.1.254/255/221/195/22; https://repo1.maven.org/maven2/io/github/llamalad7/mixinextras-neoforge/maven-metadata.xml |
| confirmed | NeoForge 21.1 data attachments (Entity, BlockEntity, Chunk, Level) support Codec persistence and client sync (backported in 21.1.197); the per-player sendToPlayer predicate is honored for the initial sync only since 21.1.264. | Correct. A nuance for the report: sync(StreamCodec) always writes the full value, but the sync(AttachmentSyncHandler) overload passes initialSync and previousValue, so delta encoding is possible. | mc-src-1.21.1/net/neoforged/neoforge/attachment/AttachmentType.java:224-273 (copyOnDeath, three sync overloads; javadoc 'The full data is always written' :259/:268); AttachmentSync.java:154-185 in 21.1.262 writes every synced type without calling sendToPlayer, whereas syncUpdate checks it at :88/:108; neoforge-21.1.264-sources.jar AttachmentSync.java:163 adds 'type.syncHandler.sendToPlayer(holder, to)'; holders Entity.java:131, BlockEntity.java:30, Level.java:79, ChunkAccess.java:60, LevelChunk.java:53; IAttachmentHolder.java:125 syncData; changelog 21.1.197 and 21.1.264 (PR #3670) |
| confirmed | NeoForge 21.1 includes a JSON keyframe-animation loader (assets/<ns>/neoforge/animations/entity) with HierarchicalModel#animate(AnimationState, AnimationHolder, ...) overloads; Blockbench's 'Animation to JSON Converter' plugin exports this format. | Correct, with one small source fix. The body of PR #1476 only says 'There is an existing Blockbench plugin'. The plugin name comes from a comment on #1476 that links blockbench-plugins PR #602 ('Animation to JsonEA Converter' renamed to 'Animation to JSON Converter'). The plugin is in the official registry as animation_to_json 1.0.1: 'Converts Blockbench animations to the JSON format for the NeoForge mod loader'. | mc-src-1.21.1/net/neoforged/neoforge/client/entity/animation/json/AnimationLoader.java:38 ("neoforge/animations/entity"); net/minecraft/client/model/HierarchicalModel.java:51,61,70,78; changelog 21.1.42/21.1.44; GitHub API pulls/1476 and its comments (TelepathicGrunt, 2024-09-09); https://github.com/JannisX11/blockbench-plugins/pull/602; https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins.json |
| confirmed | NeoForge 21.1 provides a generic ConfigurationScreen for ModConfigSpec registered via IConfigScreenFactory; FML 4.0.45 config types are COMMON, CLIENT, SERVER, STARTUP; config UI added in 21.0.110. |  | mc-src-1.21.1/net/neoforged/neoforge/client/gui/ConfigurationScreen.java:110; client/gui/IConfigScreenFactory.java; changelog '21.0.110-beta Added configuration screen (#1199)'; javap of net.neoforged.fml.config.ModConfig$Type from loader-4.0.45.jar (COMMON, CLIENT, SERVER, STARTUP) |
| confirmed | Iris (1.21.1 branch) ignores custom mod shader programs while a shader pack is active (allowUnknownShaders defaults to false), officially advises mods to avoid custom shaders or provide fallback paths, and remaps vanilla GameRenderer shader getters to pack programs; IrisApi minor revision 2. | Correct. Technically the getters are overridden with @Inject at HEAD (cancellable), not with @Redirect. The effect is the same. | https://github.com/IrisShaders/Iris/blob/1.21.1/docs/development/compatibility/core-shaders.md (both quotes verbatim); Iris 1.21.1 common/.../mixin/MixinShaderInstance.java iris$shouldSkipThis (non-ExtendedShader/FallbackShader skipped → DepthColorStorage.disableDepthColor()); config/IrisConfig.java:59 allowUnknownShaders = false; mixin/MixinGameRenderer.java (getPositionColorShader :55, getRendertypeEntityTranslucentShader :182, getRendertypeLinesShader :393); apiimpl/IrisApiV0Impl.java getMinorApiRevision() returns 2 |
| confirmed | Sodium 0.8.13 for Minecraft 1.21.1 (2026-08-28) supports NeoForge's AddSectionGeometryEvent, dispatches RenderLevelStageEvent block stages, culls entities via getBoundingBoxForCulling against visible sections, overwrites ModelPart#translateAndRotate and patches ModelPart.Cube; licence PolyForm Shield 1.0.0. | Correct. The skip threshold is MAX_ENTITY_CHECK_VOLUME = 16*16*16*15 (15 sections of volume). Glowing entities and entities with a shown name are never culled. | GitHub release mc1.21.1-0.8.13 published 2026-08-28; Modrinth mc1.21.1-0.8.13-neoforge 2026-08-28; neoforge/.../level/NeoForgeLevelRenderHooks.java:23 (ClientHooks.dispatchRenderStage), :33-35 (AddSectionGeometryEvent renderers); common/.../render/SodiumWorldRenderer.java:488-513; mixin/features/render/entity/ModelPartMixin.java:37-38 (@Overwrite translateAndRotate), CubeMixin.java:40 (inject into compile); neoforge.mods.toml license = "Polyform-Shield-1.0.0" |
| confirmed | On Forge 1.21.1 there are no builds of Sodium, Iris, Lithium, FerriteCore, ModernFix, C2ME, Flywheel, Veil, EMI, Jade, Patchouli or GuideME; NeoForge has all of them; Fabric has all except GuideME and LDLib2. Forge 1.21.1 latest is 52.1.20 (2026-10-15). | Correct. For balance, Forge 1.21.1 does have builds of JEI, GeckoLib, Entity Culling, ImmediatelyFast, Modonomicon, WTHIT, ModernUI, Cloth Config, Forge Config API Port, JourneyMap, Xaero's Minimap and spark. | Modrinth API /v2/project/{slug}/version?game_versions=["1.21.1"]&loaders=["forge"] returned no versions for all 12; maven.createmod.net flywheel-forge-1.21.1/maven-metadata.xml → 404; CurseForge via api.cfwidget.com shows 0 Forge+1.21.1 files for Sodium, Lithium, ModernFix, C2ME, EMI, Jade, Patchouli, GuideME; https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json (1.21.1-latest 52.1.20); forge-1.21.1-52.1.20.pom Last-Modified 2026-10-15 |
| confirmed | Flywheel 1.0.7-48 for NeoForge/Fabric 1.21.1 (2026-09-20, MIT) is only on maven.createmod.net and must be jar-in-jar'd; its instancing and indirect backends are disabled when a shader pack is in use; the indirect backend needs OpenGL 4.6 or an ARB set unavailable on macOS (GL 4.1); all visuals are recreated when the render origin updates. | Correct. Two additions. First, the render origin moves only when the camera gets more than 256 blocks from it (EngineImpl maxOriginDistance = 256), so the recreate cost is paid once per 256 blocks of camera travel, not every frame. Second, besides Create 6, Vanillin 1.1.3 (Modrinth, 1.21.1) also jar-in-jars Flywheel (1.0.4). Create's real jarJar line carries a version block: strictly [flywheel_version,2.0), prefer flywheel_version. | https://maven.createmod.net/dev/engine-room/flywheel/flywheel-neoforge-1.21.1/maven-metadata.xml (1.0.7-48, lastUpdated 20260920); jar Content-Length 874794; Flywheel 1.21.1/dev Backends.java (.supported(... && !ShadersModHelper.isShaderPackInUse())); GlCompat.java:145-165; docs/flywheel/api/concepts.md:57-59; backend/engine/EngineImpl.java updateRenderOrigin; Backends engineFactory(..., 256); Create mc1.21.1/dev build.gradle:133-136; vanillin-neoforge-1.21.1-1.1.3.jar META-INF/jars/flywheel-neoforge-1.21.1-1.0.4.jar |
| corrected | GeckoLib 4.9.3 (2026-09-16, MIT) is the 1.21.1 line; GeckoLib moved to 5.x at MC 1.21.5 (5.1.0, 2025-06-16); GeckoLib renders its own quads so Sodium's ModelPart/Cube fast path does not apply; payloads registered optional(). | The 5.x line started at 1.21.5 with 5.0-alpha1 (2025-04-08) and 5.0 (2025-04-21). 5.1.0 (2025-06-16) is the last 1.21.5 build, not the first. Everything else is correct. | Modrinth API geckolib versions for 1.21.5 (5.0-alpha1 2025-04-08, 5.0 2025-04-21, 5.1.0 2025-06-16) and 1.21.1 (4.9.3, 2026-09-16, 0.63 MB, MIT); GeckoLib 1.21.1 GeoRenderer.java:312-341 (renderCube → createVerticesOfQuad → buffer.addVertex); neoforge/.../GeckoLibNetworkingNeoForge.java:29 (registrar(...).optional()) |
| confirmed | owo-lib on NeoForge 1.21.1 is still beta (0.12.15.6-beta.1+1.21, 2026-10-16; beta since 2024-10-22); earlier builds depended on Forgified Fabric API. | Correct. More precisely, 0.12.15-beta.5 through 0.12.15.5-beta.1 declared Forgified Fabric API as 'embedded'. The newest beta declares no dependencies. | Modrinth /v2/project/owo-lib/version (neoforge, 1.21.1): 13 versions, all beta, first 0.12.15-beta.4+1.21 on 2024-10-22; dependency project Aqlf1Shp = forgified-fabric-api |
| confirmed | Minecraft 1.21.1 dedicated server bundles fastutil 8.5.12, JOML 1.10.5, Guava 32.1.2-jre, Gson 2.10.1, DFU 8.0.16, Brigadier 1.3.10, Netty 4.1.97.Final, slf4j-api 2.0.9, log4j 2.22.1, commons-lang3 3.14.0, commons-io 2.15.1; NeoForge pins these with Gradle 'strictly', so a core requiring newer versions fails resolution. | Correct. The NeoForge module is served from maven.neoforged.net/mojang-meta, not /releases, where it returns 404. DFU is on libraries.minecraft.net, not Maven Central. | server.jar (piston-data …/59353fb4…/server.jar) META-INF/libraries.list; https://piston-meta.mojang.com/v1/packages/1757c8b1fbfc6b638504e1f6cceb05893f7389d5/1.21.1.json; ~/.gradle cache minecraft-dependencies-1.21.1.module (all four variants use {"strictly": …}); https://maven.neoforged.net/mojang-meta/net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.module (200); Gradle docs (https://docs.gradle.org/current/userguide/dependency_versions.html): 'If a strict version conflicts with a consumer's version requirement, it will trigger a resolution error.' |
| confirmed | Candidate Java ECS/AI libraries are dormant: Dominion ECS 0.9.0 (2023-07-16), Artemis-odb 2.3.0 (2019-11-09), Ashley 1.7.4 (2021-02-05, depends on libGDX), gdx-ai 1.8.2 (2019-07-26, depends on libGDX). |  | Maven Central POM Last-Modified: dominion-ecs-engine 0.9.0 2023-07-16 (157,982 bytes), artemis-odb 2.3.0 2019-11-09, ashley 1.7.4 2021-02-05 (compile dep com.badlogicgames.gdx:gdx 1.9.9), gdx-ai 1.8.2 2019-07-26 (compile dep gdx 1.9.8); GitHub pushed_at dominion-dev/dominion-ecs-java 2024-03-12; licences MIT/BSD-2-Clause/Apache-2.0/Apache-2.0 |
| confirmed | Vanilla ServerLevel#sendBlockUpdated iterates all navigating mobs and synchronously recomputes affected paths on every collision-shape change; PathNavigation runs A* on the server thread through a PathNavigationRegion snapshot with maxVisitedNodes = floor(FOLLOW_RANGE*16). | Correct, with two nuances. First, each affected navigation recomputes at most once per 20 ticks: PathNavigation#recomputePath otherwise sets hasDelayedRecomputation and retries later. Second, when Lithium is installed, its entity.inactive_navigations mixin ('Block updates skip notifying mobs that won't react to the block update anyways') limits the scan to mobs with active navigation. The scan is still O(units) per collision change in vanilla. | mc-src-1.21.1/net/minecraft/server/level/ServerLevel.java:1080-1110 (loop over navigatingMobs), :1781 (every Mob added on tracking start); net/minecraft/world/entity/ai/navigation/PathNavigation.java:65 (Mth.floor(FOLLOW_RANGE * 16.0)), :91-102 (20-tick throttle), :168-169 (PathNavigationRegion + findPath), :424-434 (shouldRecomputePath); Lithium mc1.21.1-0.15.4 mixin/entity/inactive_navigations/package-info.java |
| refuted | Vanilla entity tracking re-evaluates Entity#broadcastToPlayer only when the entity or a player changes chunk section (ChunkMap#tick), so server-side fog-of-war hiding needs a forced re-check (AT/mixin on ChunkMap.TrackedEntity). | ChunkMap#tick does re-check on section changes. But ChunkMap#move(ServerPlayer) also calls TrackedEntity#updatePlayer, and therefore broadcastToPlayer, for every tracked entity each time a player movement packet is accepted. A player whose camera is another entity triggers it every tick. The client sends a position packet at least every 20 ticks (positionReminder >= 20), so vanilla re-checks visibility for each connected player at least about once per second. An immediate re-check needs no AT: the public ServerChunkCache#move(ServerPlayer) re-runs updatePlayer for all tracked entities. The AT on ChunkMap#entityMap (private) plus TrackedEntity#updatePlayer (package-private class) is only needed for a cheaper per-entity re-check. Consequence: broadcastToPlayer runs O(players × tracked entities) at least once per second, so it must be O(1). | mc-src-1.21.1/net/minecraft/server/level/ChunkMap.java:1023-1030 (move loops entityMap and calls updatePlayer), :1151-1183 (tick), :150 (private entityMap), :1278 (package-private class TrackedEntity), :1327-1344 (updatePlayer uses broadcastToPlayer); ServerChunkCache.java:464-467 (public move); ServerGamePacketListenerImpl.java:872 handleMovePlayer → :970 getChunkSource().move(player); client/player/LocalPlayer.java:284 (positionReminder >= 20); ServerPlayer.java:528 |
| confirmed | Vanilla gamerule maxEntityCramming defaults to 24; LivingEntity#pushEntities does a per-entity AABB query each tick and deals 6.0 cramming damage above the limit. | Correct. The damage roll fires with a 1-in-4 chance per tick (random.nextInt(4) == 0) once 24 or more other non-passenger pushable entities overlap. | mc-src-1.21.1/net/minecraft/world/level/GameRules.java:98-100 (IntegerValue.create(24)); net/minecraft/world/entity/LivingEntity.java:2891-2917 |
| confirmed | junit-fml 4.0.45 is built against JUnit BOM 5.10.2 and jqwik 1.10.2 depends on JUnit Platform 1.14.4, so JUnit 5.14.4 (not 6.1.3) is the consistent choice; JUnit 6.1.3 (2026-08-07), 5.14.4 (2026-04-26). | Correct. 5.14.4 is the newest 5.x release. | https://maven.neoforged.net/releases/net/neoforged/fancymodloader/junit-fml/4.0.45/junit-fml-4.0.45.pom (junit-bom 5.10.2, import); https://repo1.maven.org/maven2/net/jqwik/jqwik-engine/1.10.2/jqwik-engine-1.10.2.pom (junit-platform-commons/engine 1.14.4); junit-bom maven-metadata.xml; POM Last-Modified 6.1.3 = 2026-08-07, 5.14.4 = 2026-04-26 |
| corrected | me.champeau.jmh's latest release is 0.7.3 (Plugin Portal 2025-01-30; GitHub release 2025-04-16; CI tested on Gradle 8.10); Gradle 9 support was merged on 2026-07-19 but not released. | Versions, dates and the 'merged but unreleased' status are correct. At tag RELEASE_0_7_3 the functional tests ran on Gradle 7.0, 8.0 and GradleVersion.current(), which was wrapper 8.12, not 8.10. PR #288 changed JMHTask annotations (@DisableCachingByDefault, @InputFile→@Classpath) to pass Gradle 9 plugin validation, so expect 0.7.3 to have problems on Gradle 9.2.1. | https://plugins.gradle.org/m2/me/champeau/jmh/me.champeau.jmh.gradle.plugin/maven-metadata.xml; GitHub releases RELEASE_0_7_3 2025-04-16; src/funcTest/.../AbstractFuncSpec.groovy:27-31 and gradle-wrapper.properties (8.12) at RELEASE_0_7_3; issues/282 closed 2026-07-19; pulls/288 merged 2026-07-19 (body lists changes) |
| confirmed | Error Prone (2.50.0) must run on JDK 21+; NullAway's JSpecify mode needs javac 22+ or OpenJDK 21.0.8+ with -XDaddTypeAnnotationsToSymbol=true, while standard mode already honors JSpecify annotations. | Correct. Addition: the -XD flag works only on OpenJDK builds such as Temurin or Zulu, not on Oracle JDK 21/17. Since NullAway 0.12.11 a missing flag fails the build. | https://errorprone.info/docs/installation ('Please note that Error Prone must be run on JDK 21 or newer.'); https://github.com/uber/NullAway/wiki/JSpecify-Support; Maven Central error_prone_core 2.50.0 (2026-06-10), nullaway 0.14.2 (2026-09-25); Plugin Portal net.ltgt.errorprone 5.1.1 (2026-08-25), net.ltgt.nullaway 3.3.0 (2026-10-05) |
| confirmed | RenderDoc supports Windows and Linux only (no macOS), limiting GPU debugging on the owner's Mac (per the FAQ). | The conclusion is correct but the source is wrong. The FAQ does not list operating systems; it only calls Metal support 'not clear'. The platform statement is on the RenderDoc home page: 'RenderDoc supports Windows, Linux, and Android for capture and replay out of the box.' | https://renderdoc.org/ ; https://renderdoc.org/docs/getting_started/faq.html |
| confirmed | All C2ME NeoForge 1.21.1 builds are alpha (latest 0.4.0-alpha.0.123+1.21.1, 2026-10-11); C2ME flags off-thread misuse of world random via CheckedThreadLocalRandom; c2me-fabric 0.4.0-alpha.0.30. |  | Modrinth c2me-neoforge: 72 versions for 1.21.1, version_type all 'alpha', latest 0.4.0-alpha.0.123+1.21.1 (2026-10-11, MIT); c2me-fabric 0.4.0-alpha.0.30+1.21.1 (2026-10-11); project body: 'C2ME includes CheckedThreadLocalRandom for world random … These detections exist to prevent mods from screwing up Minecraft internals' |
| confirmed | Baritone is LGPL-3.0 ('with anime exception', v1.11.3 for 1.21.1, released 2026-09-01) and MineColonies is GPL-3.0 (pushed 2026-10-13) with a single-thread 'Minecolonies Pathfinding Worker' executor; both are references only. |  | GitHub API cabaletta/baritone license LGPL-3.0; README badge 'license-LGPL-3.0 with anime exception'; release v1.11.3 published 2026-09-01, body 'For Minecraft 1.21 and 1.21.1'; ldtteam/minecolonies GPL-3.0, pushed 2026-10-13; version/main Pathfinding.java:33 (thread name), :50 (new ThreadPoolExecutor(1, 1, …)) |
| confirmed | Fabric API 0.116.17+1.21.1 (2026-09-01) includes fabric-data-attachment-api-v1 1.4.7 with AttachmentRegistry.Builder#syncWith; no section-geometry hook; Cardinal Components API 6.1.3 is optional. |  | Modrinth fabric-api 0.116.17+1.21.1 (2026-09-01); https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/fabric-api-0.116.17+1.21.1.pom (fabric-data-attachment-api-v1 1.4.7+5b36e0f719; 44 artifacts, none for section geometry); fabric-api 1.21.1 branch AttachmentRegistry.java:160 syncWith(PacketCodec, AttachmentSyncPredicate); Modrinth cardinal-components-api 6.1.3 (2025-11-22, MIT, 0.23 MB) |
| corrected | Embeddium's last 1.21.1 build is 1.0.15 (2025-01-24) and Iris declares it incompatible ('Please use Sodium 0.8 instead'); Oculus has no 1.21.1 build. | The Embeddium and Oculus facts are correct. The quoted Iris reason comes from the 1.21.1 branch head and Iris 1.8.14-beta.1. The latest Iris release, 1.8.12, says 'Please use Sodium 0.6 instead' and requires Sodium [0.6,). | Modrinth embeddium 1.0.15+mc1.21.1 (2025-01-24); Modrinth oculus: no 1.21.1 versions; META-INF/neoforge.mods.toml inside iris-neoforge-1.8.12+mc1.21.1.jar (lines 51-62) and iris-neoforge-1.8.14-beta.1+mc1.21.1.jar (line 53) |
| refuted | Dev compat profile: Sodium mc1.21.1-0.8.13 together with Iris 1.8.12+1.21.1-neoforge (§5 build sketch, recommendation 9). | Iris 1.8.12 (2025-06-17) was built for Sodium 0.6. Its Modrinth dependency is pinned to Sodium mc1.21.1-0.6.13 and its mods.toml says 'Please install Sodium 0.6'. Sodium 0.8.13's own metadata declares "breaks": {"iris": "<1.8.13"}. The only 1.21.1 Iris build for Sodium 0.8 is 1.8.14-beta.1 (2026-06-13, changelog 'Updates to Sodium 0.8'). Use maven.modrinth:iris:1.8.14-beta.1+1.21.1-neoforge with Sodium 0.8.13. Optionally add a second all-release profile: Sodium mc1.21.1-0.6.13 + Iris 1.8.12. Both pairs resolve on the Modrinth Maven. | Modrinth /v2/version/t3ruzodq (Iris 1.8.12) dependencies → version Pb3OXVqC = sodium mc1.21.1-0.6.13-neoforge; /v2/version/KduFYu4t changelog 'Updates to Sodium 0.8'; Sodium tag mc1.21.1-0.8.13 fabric/src/main/resources/fabric.mod.json breaks.iris '<1.8.13'; Iris NeoForge versions for 1.21.1: 1.8.14-beta.1 (beta), 1.8.12, 1.8.8, … (no 1.8.13); Modrinth Maven resolution of both coordinates (HTTP 200) |
| corrected | JEI 19.69.0.457 (2026-10-20), 7.4 MB; API mezz.jei:jei-1.21.1-neoforge-api on maven.blamejared.com. | 19.69.0.457 (2026-10-20, 7.35 MB) is a beta. The latest release is 19.64.0.455 (2026-10-16, 2.38 MB). The API artifact exists as stated, latest 19.69.0.457. | Modrinth JEI versions (neoforge, 1.21.1): 19.69.0.457 version_type beta; 19.64.0.455 release; https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge-api/maven-metadata.xml |
| corrected | Registrate MC1.21.1-1.4.8 (2026-06-12, on maven.gegy.dev/releases, MPL-2.0, 0.18 MB); used by Create, which jar-in-jars it. | Version, date, repository, licence and size (181,998 bytes) are correct. However, Create's mc1.21.1/dev build jar-in-jars a different line: com.tterrag.registrate:Registrate:MC1.21-1.3.0+67 from maven.ithundxr.dev/snapshots. | https://maven.gegy.dev/releases/com/tterrag/registrate/Registrate/maven-metadata.xml (MC1.21.1-1.4.8, jar Last-Modified 2026-06-12); Create mc1.21.1/dev gradle.properties:29 (registrate_version = MC1.21-1.3.0+67), build.gradle:99 (maven.ithundxr.dev/snapshots), :130 (jarJar(api("com.tterrag.registrate:Registrate:$registrate_version"))); GitHub tterrag1098/Registrate MPL-2.0 |
| corrected | Iris recommends the Freecam mod (hashalite/Freecam, MIT); Freecam's FreeCamera extends AbstractClientPlayer and is installed as the camera entity, a useful reference for an Iris-safe RTS camera. | hashalite/Freecam is now a fork; the canonical repo is MinecraftFreecam/Freecam (MIT). FreeCamera extends AbstractClientPlayer only on current main. In the 1.21-era source (tag v1.3.0+mc1.21, Modrinth build 1.3.0+mc1.21.1) it extends LocalPlayer. Both versions set it with Minecraft#setCameraEntity. A client-side camera only shows what the server already sends, and the server centres both chunk tracking and entity tracking on the server-side player position. An RTS camera that pans across a map must therefore move the server-side player too, or otherwise make the server send that area. | Iris 1.21.1 docs/usage/incompatible-mods.md; GitHub API hashalite/Freecam (fork=true, parent MinecraftFreecam/Freecam); MinecraftFreecam/Freecam main common/.../util/FreeCamera.java:36 and tag v1.3.0+mc1.21 FreeCamera.java:31; mc-src-1.21.1 ChunkMap.java:1061-1071 (updateChunkTracking uses player.chunkPosition()), :1327-1344 (updatePlayer uses player.position()) |
| corrected | Iris is the only shader mod on 1.21.1. | True for NeoForge and Fabric. OptiFine HD U J1 (2024-09-24) exists for 1.21.1, but only for vanilla and Forge 52.0.16. Monocle (NeoForge) is an add-on that lets Iris run with Embeddium; it is not a separate shader engine. | https://optifine.net/downloads (1.21.1: OptiFine HD U J1, 24.09.2024, Forge 52.0.16); Modrinth monocle-iris description |
| corrected | imgui-java 1.92.7.1 (2026-07-27, MIT) is built on LWJGL 3.4.1 and lwjgl-sdl, so a dev-only ImGui overlay on MC 1.21.1 (LWJGL 3.3.3) would need an older binding or Veil's bundled copy. | Only the optional imgui-java-lwjgl3 backend module imports LWJGL BOM 3.4.1 + lwjgl-sdl. The imgui-java-binding artifact has no LWJGL dependency. Veil 4.5.2 does not bundle ImGui; it optionally depends on the separate ImGuiMC mod. ImGuiMC 2.0.5+mc1.21.1 (2026-08-10, LGPL-3.0-only, 10.7 MB) jar-in-jars imgui-java-binding 1.92.7.1 plus natives for 1.21.1. Older imgui-java-lwjgl3 releases used LWJGL 3.3.4 (1.87.7–1.89.0) or 3.3.6 (1.90.0). | Maven Central imgui-java-lwjgl3-1.92.7.1.pom (lwjgl-bom 3.4.1, lwjgl-sdl), imgui-java-binding-1.92.7.1.pom (no dependencies), earlier imgui-java-lwjgl3 POMs; GitHub SpaiR/imgui-java MIT, release v1.92.7.1 2026-07-27; veil-neoforge-1.21.1-4.5.2.jar (no jarjar); Modrinth Veil 4.5.2 dependency imguimc 'optional'; imguimc-neoforge-2.0.5+mc1.21.1.jar META-INF/jarjar/metadata.json |
| corrected | Vanilla mobs use clientTrackingRange 8–10 chunks and updateInterval 2–3. | The 8–10 chunk range is correct. Mobs use the builder default updateInterval of 3. Interval 2 applies to players only (range 32, interval 2). | mc-src-1.21.1/net/minecraft/world/entity/EntityType.java:1256-1257 (defaults 5 / 3); registrations: ZOMBIE/SKELETON/PILLAGER 8, VILLAGER/IRON_GOLEM/COW 10, PLAYER 32 with updateInterval(2) |
| confirmed | Ponder 1.0.87+mc1.21.1 (2026-06-20, maven.createmod.net, MIT, 0.82 MB) has a runtime dependency on Flywheel. | Correct. The artifact is net.createmod.ponder:ponder-neoforge:1.0.87+mc1.21.1. The older Ponder-NeoForge-1.21.1 path stops at 1.0.69. Note that the Modrinth slug 'ponder' is 'Ponder for KubeJS', a different project. | https://maven.createmod.net/net/createmod/ponder/ponder-neoforge/maven-metadata.xml; jar Content-Length 822408, Last-Modified 2026-06-20; POM dependency flywheel-neoforge-1.21.1 1.0.4 scope runtime; GitHub Creators-of-Create/Ponder MIT; Create build.gradle:140 |
| confirmed | Version/date/licence/size rows from Modrinth: GeckoLib, AzureLib 3.1.22, playerAnimator, LDLib2, ModernUI, YACL, Cloth Config, Forge Config API Port, Fzzy Config, Patchouli, Modonomicon, GuideME, Lavender, EMI, REI, Jade, WTHIT, Veil, Lodestone, Satin, Lithium, FerriteCore, ModernFix, Entity Culling (id eieYbWYb), ImmediatelyFast, Distant Horizons, Moonrise, Krypton, spark, CCA, Radium, Iris & Oculus Flywheel Compat, JourneyMap, Xaero's Minimap. | All match the Modrinth API on 2026-10-20. Small additions: AzureLib 3.1.23-alpha.2 is tagged 'release' on Modrinth despite its name. The latest Iris Fabric release for 1.21.1 is 1.8.8 (2025-02-20); only 1.8.14-beta.1 is newer. AzureLib's MPL-2.0 part is LICENSE_SBLForked; GuideME's GitHub README states LGPL with MIT/Apache-2.0 embedded libraries; Patchouli's LICENSE is CC BY-NC-SA 3.0. | https://api.modrinth.com/v2/project/{slug} and /version?game_versions=["1.21.1"]&loaders=[…] for each slug (saved under verify-libraries/dl/modrinth); GitHub AzureDoom/AzureLib (MIT, 49 stars, LICENSE_SBLForked = MPL-2.0); AppliedEnergistics/GuideME README; VazkiiMods/Patchouli LICENSE |
| confirmed | Modrinth Maven coordinates in the compat profile resolve; use the version ID where several loaders share a version number (Entity Culling NeoForge 1.11.3 = eieYbWYb). | All eight coordinates resolve to the intended NeoForge 1.21.1 jars. The warning is justified: maven.modrinth:entityculling:1.11.3 resolves to entityculling-fabric-1.11.3-mc1.19.4.jar. The Iris coordinate must change; see the Iris/Sodium pairing verdict. | https://api.modrinth.com/maven/maven/modrinth/<slug>/<version>/… → cdn.modrinth.com redirects (spark v5qtqRQi, sodium uMOpc5uV, iris t3ruzodq, lithium DDUrRVCA, modernfix 7Jy58lnT, ferrite-core x7kQWVju, entityculling eieYbWYb, immediatelyfast EJzmEPZV) |
| confirmed | Toolchain versions: Spotless 8.10.4 (2026-10-08), palantir-java-format 2.102.0 (2026-10-05, 120 columns), google-java-format 1.37.0 (2026-10-01), JSpecify 1.0.1 (2026-07-29), ArchUnit 1.5.1 (2026-09-25), AssertJ 3.27.7 (2026-01-24; 4.0.0-M1 milestone), jqwik 1.10.2 (2026-10-10), Mockito 5.24.0 (2026-09-23), JMH 1.37 (2023-08-03), MDG 2.0.148 (2026-09-29, latest); newest upstream fastutil 8.5.19, JOML 1.10.9, Guava 33.7.2. |  | Maven Central and Gradle Plugin Portal maven-metadata.xml plus POM Last-Modified headers (verify-libraries/out/maven1.txt, maven2.txt, plugins.txt); GitHub diffplug/spotless release gradle/8.10.4 2026-10-08; palantir-java-format README '120 character Java formatter'; mockito-core Mockito.java javadoc §0.3 (-javaagent on Java 21+) |
| confirmed | MDG facts: default META-INF/accesstransformer.cfg needs no configuration; unitTest { enable(); testedMod = mods.<name> }; testframework EphemeralTestServerProvider; interfaceInjectionData is dev-time only; additionalRuntimeClasspath needed before MC 1.21.9; jarJar with strictly/prefer; multi-source-set mods; reobfuscation only in the legacy plugin for 1.17–1.20.1. |  | https://github.com/neoforged/ModDevGradle/blob/main/README.md (lines ~250-270, 345-372, 378-396, 440-485, 510-520, 565-582); LEGACY.md lines 1-3, 44-66; testframework-21.1.264.jar contains net/neoforged/testframework/junit/EphemeralTestServerProvider.class; MDK build.gradle (moddev 2.0.148, runs client/server/gameTestServer/data, exclude **/*.bbmodel) and templates/META-INF/neoforge.mods.toml lines 52-59 |
| confirmed | NeoForge has used Mojang names in production since 1.20.2 ('We are now using MojMaps everywhere'); AT directives only modify the method they directly reference. |  | https://neoforged.net/news/20.2release/ (NeoForge 20.2, 2023-10-30, Toolchain Updates); https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers ('By default, NeoForge will search for META-INF/accesstransformer.cfg'; 'Directives only modify the method they directly reference; any overriding methods will not be access-transformed.') |
| confirmed | Entity Culling 1.11.3: async occlusion culling plus client tick culling (tickCulling=true by default), respects noCulling, does not cull hitboxes over 50 blocks, API addDynamicEntityWhitelist(Function<Entity,Boolean>), 128-block tracing distance; tr7zw Protective License (no commercial use). | Correct. Additional behaviour relevant to RTS units: beyond tracingDistance (128) entities are never occlusion-culled. Tick culling replaces Entity#tick with a minimal basicTick (setOldPosAndRot, tickCount++, LivingEntity#aiStep, hurtTime) for any entity that is culled or was not rendered in the last frame (isOutOfCamera). Client-side logic in tick(), such as AnimationState updates, freezes off-screen unless the entity is whitelisted. | tr7zw/EntityCulling main: EntityCulling-Versionless/.../Config.java:30-35; CullTask.java:102-125; mixin/ClientWorldMixin.java tickEntity; mixin/EntityRendererMixin.java:90-97; NMSCullingHelper.java (≤1.21.1: entity.noCulling, entity.getBoundingBoxForCulling()); EntityCullingModBase.java:274; LICENSE-EntityCulling line 10 |
| confirmed | ModernFix: dynamic_entity_renderers ('Some mods are not compatible… EntityRenderer crashes') and dynamic_resources default false; spark_profile_launch and spark_profile_world_join are opt-in. |  | embeddedt/ModernFix 1.21.1 src/main/java/org/embeddedt/modernfix/core/config/ModernFixEarlyConfig.java:247-268; src/main/resources/assets/modernfix/lang/en_us.json |
| confirmed | Sodium and Lithium both read per-mod option overrides from mod metadata (sodium:options, lithium:options). | Correct, but the keys go in different places in neoforge.mods.toml. Sodium reads 'sodium:options' from the mod's [[mods]] entry (ModInfo#getConfigElement); Iris 1.8.14 itself uses [mods."sodium:options"]. Lithium reads 'lithium:options' from the file top level (ModInfo#getOwningFile().getConfigElement). | Sodium mc1.21.1-0.8.13 neoforge/.../ForgeMixinOverrides.java:12-24; Lithium mc1.21.1-0.15.4 neoforge/.../NeoForgeMixinOverrides.java:13-24; iris-neoforge-1.8.14-beta.1 mods.toml line 47 |
| corrected | No GeckoLib per-entity cost figures are published; the only signals are third-party optimiser add-ons such as 'GeckoLib Animation Optimizer' (1.20.1). | GeckoLib Animation Optimizer (CurseForge, Forge 1.20.1) exists. There is also a 1.21.1 signal: GeckolibBetterFPS 1.0.2 (Modrinth, NeoForge 1.21.1, alpha, 2026-08-07), which claims about 25% faster GeckoLib entity rendering. That no per-entity benchmark is published cannot be proven and remains unverified. | https://www.curseforge.com/minecraft/mc-mods/geckolib-animation-optimizer; Modrinth geckolibbetterfps (version cqGqtGak, LGPL-3.0-or-later) |
| confirmed | Iris lists Botania's custom shaders and ComputerCraft's TBO renderer as unfixable; Tweakeroo's freecam misbehaves with Iris; Lithium mixins in ai.pathing, entity.collisions, entity.inactive_navigations; Fabric Loader bundles Mixin/MixinExtras; hoppers interact through Capabilities.ItemHandler.BLOCK. |  | Iris 1.21.1 docs/usage/incompatible-mods.md; Lithium mc1.21.1-0.15.4 git tree (10/15/8 files in those packages); https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.json (sponge-mixin 0.17.5+mixin.0.8.7, mixinextras-fabric 0.5.5); mc-src-1.21.1 net/neoforged/neoforge/items/VanillaInventoryCodeHooks.java:223 |
| confirmed | macOS caps OpenGL at 4.1, so on the M4 Pro only Flywheel's instancing backend (GL 3.3) can run. | Supported, but Apple's own list is archived and does not include Apple silicon; that Apple silicon also stops at 4.1 comes from community and Khronos sources. Flywheel's instancing check needs OpenGL33 or GL_ARB_shader_bit_encoding, which GL 4.1 satisfies. | https://support.apple.com/en-us/101525 (archived; highest version 4.1); Flywheel GlCompat.java:145-148 |
| confirmed | Vanilla APIs and hooks named in §2/§3: RenderLevelStageEvent stages AFTER_SKY…AFTER_LEVEL, AddSectionGeometryEvent, RegisterGuiLayersEvent (registerAbove/Below/AboveAll/BelowAll, replaceLayer, wrapLayer), IEntityExtension#hasCustomOutlineRendering/sendPairingData, LevelRenderer#requestOutlineEffect, Entity#broadcastToPlayer, DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec), DataMapType.Builder#synced, Capabilities.*, DeferredRegister.Blocks/Items helpers, datagen providers, GuiSpriteScaling NineSlice/Tile, vanilla layouts, Screen#isPauseScreen, LayeredDraw.Layer, VertexBuffer, HumanoidModel/ItemInHandLayer/HumanoidArmorLayer/CustomHeadLayer generics, KeyframeAnimations.animate(HierarchicalModel<?>…). | All exist as described. One nuance from the javadoc: AddSectionGeometryEvent fires on the main thread, but its renderers run on the section-rebuild thread, which is usually not the main thread. | mc-src-1.21.1: RenderLevelStageEvent.java:158-207; AddSectionGeometryEvent.java:26-35; RegisterGuiLayersEvent.java:47-107; IEntityExtension.java:386,396; LevelRenderer.java:2572; Entity.java:3021; DataPackRegistryEvent.java:74; DataMapType.java:152; Capabilities.java:20-48; DeferredRegister.java:390,460,486,622; common/data/{JsonCodecProvider,DataMapProvider,DatapackBuiltinEntriesProvider,LanguageProvider}.java; GuiSpriteScaling.java:23,92,102; client/gui/layouts/*; Screen.java:377-385,421-423; LayeredDraw.java:41-43; com/mojang/blaze3d/vertex/VertexBuffer.java:35,170; HumanoidModel.java:22; ItemInHandLayer.java:18; HumanoidArmorLayer.java:32; CustomHeadLayer.java:33; KeyframeAnimations.java:15 |
