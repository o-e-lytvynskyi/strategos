# Library mods and development libraries for a Minecraft 1.21.1 RTS mod (Strategos)

Research date: 2026-10-20. Fact-checked against primary sources on 2026-10-20: fixed statements are marked *(corrected)*, statements that could not be checked are marked *(unverified)*, and the checks are summarised in **Verification notes** at the end. Target: Minecraft Java 1.21.1 on Java 21. NeoForge 21.1.x is the primary loader, and Fabric equivalents are noted where they differ. Unless a row says otherwise, a version is the newest build for **1.21.1** on 2026-10-20, and "size" is the primary NeoForge jar listed on Modrinth.

---

## 0. Bottom line

1. **The MVP can ship with zero hard runtime dependencies besides NeoForge.** NeoForge 21.1 already covers most of what the candidate libraries would add (see §2): Mixin and MixinExtras (bundled), data attachments with client sync, datapack registries, data maps, config with a generated config screen, HUD layers, a JSON keyframe-animation loader for vanilla models, chunk-section geometry injection, entity outline hooks, and GameTest/JUnit integration. The newest build is **21.1.264 (2026-10-19)**. The 21.1 line still gets frequent fixes: 13 published builds between 2026-09-06 and 2026-10-19, after a quiet August (21.1.248 on 08-01, 21.1.249 on 08-28) *(corrected: not strictly weekly)*. Pin `>= 21.1.264` for two reasons. First, the network-decoding hardening listed in the changelog as 21.1.260–262 first shipped in 21.1.262, because 21.1.260 and 21.1.261 were never published to Maven *(corrected)*. Second, 21.1.264 fixed attachment initial sync ignoring the per-player predicate. The hardening changed internal network code that mods rely on: owo-lib's 2026-10-16 NeoForge beta "Resolve[s] issue with latest neoforge update due to changes to internal network code". So re-check every compat mod against ≥ 21.1.262.
2. **Rendering compatibility is the hardest constraint.** Iris is the only shader mod for NeoForge and Fabric on 1.21.1. Oculus has no 1.21.1 build, and OptiFine HD U J1 (2024-09-24) exists for 1.21.1 but only for vanilla and Forge 52.0.16 *(corrected)*. Iris ignores custom mod shader programs while a shader pack is active, and it officially asks mods to keep a fallback path that uses no custom shaders. Iris's current *release* for 1.21.1 (1.8.12) works only with Sodium 0.6.x; with today's Sodium 0.8.13 the only working Iris is 1.8.14-beta.1 *(corrected)*. Flywheel turns its GPU backends off under shader packs, and its indirect backend needs OpenGL 4.6, while macOS stops at 4.1. Draw everything with vanilla RenderTypes and shader programs, and defer Flywheel, Veil and Lodestone.
3. **Write the simulation core, pathfinding and AI ourselves.** Java ECS and behaviour-tree libraries are dormant (last releases 2019–2023) or pull in libGDX. Baritone (LGPL-3.0) and MineColonies (GPL-3.0) are design references, not dependencies. The core can compile against what Minecraft already ships, at exactly these versions: fastutil 8.5.12, JOML 1.10.5, DFU 8.0.16, Guava 32.1.2-jre, Gson 2.10.1, slf4j-api 2.0.9.
4. **Start unit models on vanilla.** HumanoidModel/PlayerModel with the armour and held-item layers costs nothing, works with resource packs, and uses Sodium's entity-model fast path. NeoForge's JSON keyframe animations cover bespoke animation without a dependency. GeckoLib 4.9.3 waits for a benchmark at scale.
5. **Signal for the loader decision.** Forge 1.21.1 (52.1.20, 2026-10-15) has **no** builds of Sodium, Iris, Lithium, FerriteCore, ModernFix, C2ME, Flywheel, Veil, EMI, Jade, Patchouli or GuideME. Its only shader option is OptiFine HD U J1 *(corrected: OptiFine added)*. NeoForge has all of them. Fabric has all except GuideME and LDLib2.

---

## 1. Method and evidence quality

- **Versions, licences, dates and sizes** come from the Modrinth API v2 (`/v2/project/{slug}` and `/version?game_versions=["1.21.1"]&loaders=[…]`, queried 2026-10-20). Maven metadata came from NeoForge, Maven Central, the Gradle Plugin Portal, maven.createmod.net, maven.gegy.dev and Cloudsmith. Repository licences, activity and releases came from GitHub.
- **API claims** come from two kinds of source:
    - The decompiled Minecraft 1.21.1 + NeoForge 21.1.262 reference sources at `/private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1`, read-only.
    - Upstream source at the 1.21.1 tag or branch for [Sodium](https://github.com/CaffeineMC/sodium/releases/tag/mc1.21.1-0.8.13) (`mc1.21.1-0.8.13`), [Iris](https://github.com/IrisShaders/Iris/tree/1.21.1) (`1.21.1`), [Lithium](https://github.com/CaffeineMC/lithium) (`mc1.21.1-0.15.4`), [Flywheel](https://github.com/Engine-Room/Flywheel/tree/1.21.1/dev) (`1.21.1/dev`), [GeckoLib](https://github.com/bernie-g/geckolib/tree/1.21.1) (`1.21.1`), [ModernFix](https://github.com/embeddedt/ModernFix/tree/1.21.1) (`1.21.1`) and [Entity Culling](https://github.com/tr7zw/EntityCulling) (HEAD).
- **Libraries bundled with Minecraft** come from Mojang's [1.21.1 version JSON](https://piston-meta.mojang.com/v1/packages/1757c8b1fbfc6b638504e1f6cceb05893f7389d5/1.21.1.json), the dedicated-server jar's `META-INF/libraries.list`, and NeoForge's `neoforge-21.1.262.module` and `minecraft-dependencies-1.21.1.module`. The latter is served from `https://maven.neoforged.net/mojang-meta/`, not from `releases/`.
- **Labels.** Text marked *Assessment* is my inference, and everything else comes from the cited source. I ran no game or benchmark. Performance statements about scale are therefore reasoning from code, not measurements.

---

## 2. What NeoForge 21.1 and Minecraft 1.21.1 already provide

| Need | Built-in API | Version notes | Evidence |
|---|---|---|---|
| Mixins | `net.fabricmc:sponge-mixin:0.16.4+mixin.0.8.7`, plus **MixinExtras 0.5.5 bundled jar-in-jar inside NeoForge** | Updated in 21.1.254 and 21.1.255 | `neoforge-21.1.262.module`, universal jar `META-INF/jarjar/metadata.json`, [changelog](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt) |
| Access changes | Access transformers. MDG and FML auto-detect the default `META-INF/accesstransformer.cfg` | — | [NeoForge AT docs](https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers), MDK `neoforge.mods.toml` template |
| Per-object state | Data attachments on `Entity`, `BlockEntity`, `ChunkAccess`/`LevelChunk` and `Level`, with Codec persistence and `copyOnDeath`. **Client sync** via `AttachmentType.Builder#sync(...)` and `IAttachmentHolder#syncData`. The `sync(StreamCodec)` overloads always write full data; `sync(AttachmentSyncHandler)` can write deltas through `write(buf, value, initialSync)` | Sync backported in **21.1.197** for all four holder types. Initial sync honours `sendToPlayer` only from **21.1.264** | `attachment/AttachmentType.java`, `attachment/AttachmentSyncHandler.java`, `attachment/AttachmentSync.java`, changelog |
| Interop interfaces | Capabilities: `BlockCapability`, `EntityCapability`, `ItemCapability`, and `Capabilities.ItemHandler/FluidHandler/EnergyStorage` | — | `capabilities/Capabilities.java` |
| Data-driven content | Datapack registries with a network codec (`DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec)`). Data Maps (`RegisterDataMapTypesEvent`, `DataMapType.Builder#synced`) | — | `registries/DataPackRegistryEvent.java`, `registries/datamaps/` |
| Config | `ModConfigSpec`. FML 4.0.45 types are COMMON, CLIENT, SERVER and STARTUP, stored as NightConfig 3.8.3 TOML. NeoForge generates `ConfigurationScreen`, registered via `IConfigScreenFactory` | Config UI added in 21.0.110 | `client/gui/ConfigurationScreen.java` |
| HUD | `RegisterGuiLayersEvent`: `registerAbove/Below/AboveAll/BelowAll`, `replaceLayer`, `wrapLayer` | `wrapLayer` backported in 21.1.190 | `client/event/RegisterGuiLayersEvent.java` |
| Keyframe animation | Vanilla `AnimationDefinition`/`KeyframeAnimations` for `HierarchicalModel`. NeoForge `AnimationLoader` loads JSON from `assets/<ns>/neoforge/animations/entity/`, and patches overloads `HierarchicalModel#animate(AnimationState, AnimationHolder, …)` | 21.1.42; path namespaced in 21.1.44 | `client/entity/animation/json/*`, [PR #1476](https://github.com/neoforged/NeoForge/pull/1476) |
| World overlays | `RenderLevelStageEvent` (AFTER_SKY … AFTER_LEVEL). `AddSectionGeometryEvent` bakes static geometry into chunk sections | — | `client/event/*` |
| Selection highlight | `IEntityExtension#hasCustomOutlineRendering(Player)` and `LevelRenderer#requestOutlineEffect()` | `requestOutlineEffect()` added in 21.1.51 (#1517); `hasCustomOutlineRendering` predates 21.1 *(corrected)* | `common/extensions/IEntityExtension.java` |
| Per-player entity visibility | Vanilla `Entity#broadcastToPlayer(ServerPlayer)`, checked in `ChunkMap.TrackedEntity#updatePlayer`. NeoForge `IEntityExtension#sendPairingData` | — | `server/level/ChunkMap.java` |
| Tests | GameTest via the MDK's `gameTestServer` run. MDG `unitTest` with `junit-fml 4.0.45`. `net.neoforged:testframework:21.1.264` with `EphemeralTestServerProvider` | — | MDK `build.gradle`, [MDG README](https://github.com/neoforged/ModDevGradle/blob/main/README.md), testframework jar |
| Datagen/registration | `GatherDataEvent` providers, including `JsonCodecProvider`, `DataMapProvider` and `DatapackBuiltinEntriesProvider`. `DeferredRegister.Blocks`/`Items` helpers (`registerSimpleBlock`/`registerSimpleItem`) | — | `common/data/`, `registries/DeferredRegister.java` |

NeoForge has run on Mojang names in production since 1.20.2 ("We are now using MojMaps everywhere", [20.2 release post](https://neoforged.net/news/20.2release/)). MDG reobfuscation exists only for legacy Forge 1.20.1 and older ([LEGACY.md](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)), and the reference MDK build ran `compileJava → jar` with no reobf step. In practice this means:

- Mixins need no refmap.
- ATs use Mojang names.
- Any NeoForge mod jar can go straight onto the dev runtime classpath for compat testing.

---

## 3. Candidates by category

### 3.1 Entity models and animation

| Candidate | Use for Strategos | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|---|
| **Vanilla `HumanoidModel`/`PlayerModel` + `HumanoidArmorLayer`, `ItemInHandLayer`, `CustomHeadLayer`** | Villagers and infantry with procedural walk, attack swing and `ArmPose`s. Real armour and weapons render for free. 64×64 player-skin textures make per-civ skins cheap | built in | built in | — | 0 | **Adopt now** |
| **Vanilla `HierarchicalModel` + `KeyframeAnimations` + NeoForge JSON animations** | Bespoke keyframe animations (gather, build, siege engines, death) without a library. Blockbench exports them with the "Animation to JSON Converter" plugin by Gaming32, who also wrote PR #1476. The PR itself only says "There is an existing Blockbench plugin to export in this format" and does not name the plugin *(corrected)* | built in (21.1.42+) | Vanilla classes only. No JSON loader; the Fabric "Json Entity Animation" lib stopped at 1.20.1 | NeoForge code LGPL-2.1 | 0 | **Adopt now**, when keyframes are needed |
| [GeckoLib](https://modrinth.com/mod/geckolib) | Bedrock-format models, Molang, sound/particle keyframes, server-triggered animations, armour/item support | **4.9.3** (2026-09-16) | 4.9.3 (2026-09-16), needs Fabric API | MIT | 0.63 MB | **Consider later** |
| [AzureLib](https://modrinth.com/mod/azurelib) | GeckoLib-4-derived engine | 3.1.22 (2026-10-16); 3.1.23-alpha.2 (2026-10-16, tagged "release" on Modrinth) | same | MIT, with MPL-2.0 parts | 0.97 MB | **Avoid**: a GeckoLib fork with a small community (49 GitHub stars) and no benefit over upstream |
| [playerAnimator](https://modrinth.com/mod/playeranimator) | Animations for the *player* model | 2.0.4+1.21.1 (2025-12-28, beta) | same | MIT | 0.20 MB | **Avoid**: covers players only, not our mobs |

Notes:

- **HumanoidModel is not a `HierarchicalModel` in 1.21.1.** It is declared `HumanoidModel<T> extends AgeableListModel<T> implements ArmedModel, HeadedModel`, and `KeyframeAnimations.animate(...)` takes a `HierarchicalModel<?>`. Keyframed units therefore need their own hierarchical model. Held items still work, because `ItemInHandLayer` only requires `ArmedModel`. Vanilla armour does not, because `HumanoidArmorLayer` requires `HumanoidModel`. *Assessment:* use vanilla humanoids for the MVP. Move only special units (siege, ships, animals) to hierarchical models with JSON animations.
- **Sodium speeds up vanilla models.** Sodium 0.8.13 overwrites `ModelPart#translateAndRotate` and patches `ModelPart.Cube` (`mixin/features/render/entity/{ModelPartMixin,CubeMixin}`). GeckoLib 4.9.3 builds its own quads (`GeoRenderer#renderCube → createVerticesOfQuad → buffer.addVertex`), so that fast path does not apply to it. No GeckoLib per-entity cost figures are published *(unverified: a negative claim)*. The only signals are third-party optimiser add-ons such as "GeckoLib Animation Optimizer" and "GeckolibBetterFPS" (both 1.20.1), so the cost has to be measured.
- **GeckoLib's footprint and porting cost.** GeckoLib registers its payloads as `optional()`. Once our entity classes implement `GeoEntity`, though, the dedicated server must also load GeckoLib (class loading; *assessment*). GeckoLib moved to the 5.x API at MC 1.21.5: 5.0-alpha1 on 2025-04-08 and 5.0 on 2025-04-21, with 5.1.0 following on 2025-06-16 *(corrected)*. Leaving 1.21.1 later therefore means a GeckoLib migration on top of the MC port.
- **Blockbench workflow.** Use "Modded Entity" export (template "Forge 1.17+ (Mojmaps)", which emits a Java `LayerDefinition` with Mojang names) for vanilla models, the "Animation to JSON Converter" plugin for NeoForge JSON animations, and the GeckoLib plugin only if GeckoLib is adopted. Keep `.bbmodel` sources in the repo; the MDK already excludes `**/*.bbmodel` from the jar.

### 3.2 UI and HUD

| Candidate | Use | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|---|
| **Vanilla `Screen`, `GuiGraphics`, layouts (`GridLayout`, `LinearLayout`, `FrameLayout`, `HeaderAndFooterLayout`), widgets, GUI sprite atlas (nine-slice/tile via `GuiSpriteManager`)** + NeoForge **`RegisterGuiLayersEvent`** | Command card, selection panel, resource bar, minimap frame, build menus | built in | built in + Fabric API `HudRenderCallback` | — | 0 | **Adopt now** |
| [owo-lib / owo-ui](https://modrinth.com/mod/owo-lib) | Declarative XML/Java UI | **Yes, but beta:** 0.12.15.6-beta.1+1.21 (2026-10-16). Beta since the first NeoForge release on 2024-10-22 | 0.12.15.4+1.21 (2025-04-20, release); 0.13.0-alpha.15 (2026-04-29) | MIT | 1.2 MB | **Avoid** on NeoForge. The port is still beta. Earlier builds embedded Forgified Fabric API, and the latest beta had to fix breakage from NeoForge's new network code |
| [LDLib2](https://github.com/Low-Drag-MC/LDLib2) (Modrinth "ldlib") | Full framework: Taffy-based layout, LSS stylesheets, XML UIs, `@Persisted`/`@DescSynced` sync, in-game editors | mc1.21.1-2.2.42 (2026-10-04), very active | none | LGPL-3.0 | **7.6 MB** | **Avoid**: NeoForge-only, heavy, and locks the architecture into its framework |
| [ModernUI](https://modrinth.com/mod/modern-ui) | Alternative UI and text engine | 3.13.0.1 (2026-06-06) | same | LGPL-3.0-or-later | **25 MB** | **Avoid**: client-only, changes text rendering globally, very heavy |

*Assessment.* None of these libraries solves the RTS-specific parts:

- HUD layers (`LayeredDraw.Layer`) only draw. Interactive RTS mode needs a non-pausing `Screen` (`isPauseScreen()` false, no background blur) that owns the cursor.
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

*Assessment.* Keep configs for client preferences and server knobs. Keep **game balance out of configs**. Unit, building, tech and civilisation definitions belong in **datapack registries** synced with a network codec and generated by `JsonCodecProvider`. Gatherable-resource yields per block belong in **Data Maps**. That keeps balance data versioned, diffable, testable in JUnit (decode the JSON with the same Codec), and overridable per world by datapacks, which is the 0 A.D. template model.

### 3.4 In-game guide

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| [Patchouli](https://modrinth.com/mod/patchouli) | 1.21.1-93 (2026-03-08) | 1.21.1-93 | **CC-BY-NC-SA-3.0** ([LICENSE](https://github.com/VazkiiMods/Patchouli/blob/HEAD/LICENSE)) | 0.65 MB | Consider later. Acceptable, but has a non-commercial licence and an older design |
| [Modonomicon](https://modrinth.com/mod/modonomicon) | 1.21.1-1.120.7 (2026-09-25) | same; Forge too | MIT AND CC-BY-4.0 | 2.4 MB | **Consider later**: best multi-loader option |
| [GuideME](https://github.com/AppliedEnergistics/GuideME) | 21.1.19 (2026-09-16) | **none** | LGPL-3.0, plus bundled libraries: a shaded Apache Lucene (Apache-2.0) and micromark/mdast ports | **9.3 MB** | **Consider later**: best NeoForge-only option. Markdown pages, 3D structure scenes for showcasing buildings, search |
| [Lavender](https://modrinth.com/mod/lavender) | none | 0.1.15+1.21 (2025-01-20), needs owo-lib | MIT | 0.54 MB | Fabric only |
| [Ponder](https://github.com/Creators-of-Create/Ponder) (Create's scene tutorials) | 1.0.87+mc1.21.1 (2026-06-20, maven.createmod.net; runtime dependency on Flywheel 1.0.4) | — | MIT | 0.82 MB | Later, for animated tutorials |

MVP: none. *Assessment:* a simple codex screen generated from the same unit and tech registries is cheap, and it never drifts from the data.

### 3.5 Compat and info mods

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| [JEI](https://modrinth.com/mod/jei) | 19.69.0.457 (2026-10-20) is a **beta**; the latest release is 19.64.0.455 (2026-10-16) *(corrected)*. API `mezz.jei:jei-1.21.1-neoforge-api` on maven.blamejared.com | same | MIT | 2.4 MB release; 7.4 MB for the 19.69 beta *(corrected)* | **Later**, only if we add items or recipes. One JEI plugin also covers EMI ("install JEI alongside EMI… JEI will be hidden", [EMI page](https://modrinth.com/mod/emi)) |
| [EMI](https://modrinth.com/mod/emi) | 1.1.24 (2026-05-13) | same | MIT | 1.1 MB | Later, via JEI compat |
| [REI](https://modrinth.com/mod/rei) | 16.0.799 (2025-02-16, **beta** on NeoForge) | 16.0.799 release | MIT | 2.4 MB, plus Architectury and Cloth | Avoid / lowest priority |
| [Jade](https://modrinth.com/mod/jade) | 15.10.6 (2026-08-06) | same | CC-BY-NC-SA-4.0 | 0.73 MB | **Later**: unit and building tooltips in first-person mode |
| [WTHIT](https://modrinth.com/mod/wthit) | 12.10.2 (2026-03-24, beta on NeoForge) | release | CC-BY-NC-SA-4.0 | 0.86 MB, plus badpackets | Later, low priority |

Compile against compat APIs as `compileOnly`, and load the mods in dev through `localRuntime`. The [Modrinth Maven](https://api.modrinth.com/maven) (`maven.modrinth:<slug>:<version or version-id>`) serves any Modrinth jar. Use the version ID where several loaders share one number. For example, Entity Culling NeoForge 1.11.3 is `eieYbWYb`, while `entityculling:1.11.3` resolves to a Fabric 1.19.4 jar. A compat source set keeps the main module clean.

### 3.6 Registration and datagen

| Candidate | NeoForge 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|
| **`DeferredRegister` (+ `.Blocks`/`.Items` helpers) + NeoForge datagen** (`BlockStateProvider`, `ItemModelProvider`, `LanguageProvider`, loot, recipes, tags, `JsonCodecProvider`, `DataMapProvider`, `DatapackBuiltinEntriesProvider`), run through the MDK `data` run | built in | — | 0 | **Adopt now** |
| [Registrate](https://github.com/tterrag1098/Registrate) | MC1.21.1-1.4.8 (2026-06-12, on `maven.gegy.dev/releases`); used by Create, which jar-in-jars it (Create mc1.21.1 bundles the older MC1.21-1.3.0+67 from maven.ithundxr.dev) | MPL-2.0 | 0.18 MB | **Avoid** |

*Assessment.* Registrate pays off for mods with hundreds of blocks and items. Strategos is mostly entities plus data-driven definitions, and the NeoForge helpers already remove most of the boilerplate. Registrate would add another abstraction between the code and the APIs every NeoForge doc and example uses, plus another jar-in-jar to update.

### 3.7 Rendering libraries, and Sodium/Iris compatibility of custom world rendering

| Candidate | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | Size | Verdict |
|---|---|---|---|---|---|
| **Vanilla RenderTypes and core shaders**, via `RenderLevelStageEvent`, `AddSectionGeometryEvent`, `VertexBuffer` and the outline API | built in | `WorldRenderEvents`; no `AddSectionGeometryEvent` equivalent in Fabric API 0.116.17 (module list checked) | — | 0 | **Adopt now** |
| [Flywheel](https://github.com/Engine-Room/Flywheel) (GPU instancing, used by Create 6) | **1.0.7-48 (2026-09-20)**, standalone only on [maven.createmod.net](https://maven.createmod.net/dev/engine-room/flywheel/flywheel-neoforge-1.21.1/maven-metadata.xml). Players otherwise get it bundled in Create 6 or in Vanillin (Modrinth `flw-vanillin` 1.1.3, which jar-in-jars Flywheel 1.0.4) | 1.0.7-48 | MIT | 0.87 MB (jar-in-jar) | **Consider later** |
| [Veil](https://modrinth.com/mod/veil) (FoundryMC: framebuffers, post-processing, shader injection, "Necromancer" skeletal animation) | 4.5.2 (2026-10-10); targets 1.21.1 only | 4.5.2 | LGPL-3.0-only | 3.4 MB | **Avoid**: it is a custom shader and post stack outside Iris's pipeline. Iris's docs say that is unsupported under shader packs; exactly how it misrenders is *(unverified)* |
| [Lodestone](https://modrinth.com/mod/lodestonelib) (VFX/particles, from Malum) | 1.8.2 (2026-01-12) | 1.21.1-1.7.0.5 (2024-12-06, beta) | LGPL-3.0-only | 0.82 MB | **Avoid** for now |
| [Satin API](https://modrinth.com/mod/satin-api) | — | 2.0.0 (2024-06-13) | LGPL-3.0 | 0.1 MB | Fabric only; avoid |

**Flywheel facts, from the 1.21.1/dev source:**

- It is not published as a standalone 1.21.1 mod. The Modrinth "Flywheel" project is a "Legacy" build for 1.18.2/1.19.2; the same is said of CurseForge *(CurseForge part unverified)*. Create 6 jar-in-jars it: `jarJar(runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-$minecraft_version:$flywheel_version"))` in [Create's build.gradle](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/build.gradle), with `strictly "[${flywheel_version},2.0)"`. Create currently pins 1.0.6. Engine Room's own Vanillin mod (Modrinth `flw-vanillin` 1.1.3, 2025-04-21, MIT) also bundles it (Flywheel 1.0.4).
- `Backends.INSTANCING` and `Backends.INDIRECT` are only `supported` when `!ShadersModHelper.isShaderPackInUse()`. With an Iris pack active, Flywheel falls back to vanilla rendering, so **a vanilla renderer has to exist anyway**. The third-party [Iris & Oculus Flywheel Compat](https://modrinth.com/mod/iris-flw-compat) add-on (1.21.1+2.4.0, 2026-05-06, CC0) exists for this case.
- The indirect backend needs OpenGL 4.6, or a long ARB extension list (compute shaders, multi-draw indirect, SSBOs). macOS caps at GL 4.1, so on the owner's M4 Pro only the instancing backend (GL 3.3) can run. The owner's own Minecraft log reports "OpenGL Version: 4.1 Metal - 90.5" on the Apple M4 Pro.
- From the docs ([concepts.md](https://github.com/Engine-Room/Flywheel/blob/1.21.1/dev/docs/flywheel/api/concepts.md)): "All instances are deleted and all visuals are recreated when the render origin updates". The origin only moves when the camera gets more than 256 blocks from it (both backends build `EngineImpl` with `maxOriginDistance` 256). *Assessment:* that is a hitch risk for a fast-panning RTS camera, roughly once per 256 blocks of camera travel.
- There is no Forge 1.21.1 artifact (`flywheel-forge-1.21.1` returns 404).

**Iris rules that apply to every custom draw, verified in Iris branch 1.21.1:**

- The official doc ([core-shaders.md](https://github.com/IrisShaders/Iris/blob/1.21.1/docs/development/compatibility/core-shaders.md)) says: "Custom shaders added by mods or resource packs are ignored by Iris when an Iris shader pack is loaded". It also says mods should "avoid using their own shader programs if at all possible… create fallback rendering pathways that do not use custom shaders".
- In code, `MixinShaderInstance#iris$shouldSkipThis` disables colour and depth writes for any non-Iris `ShaderInstance` when a pack is active. The `allowUnknownShaders` setting defaults to `false`.
- Vanilla shader getters (`GameRenderer#getPositionColorShader`, `getRendertypeLinesShader`, `getRendertypeEntityTranslucentShader`, …) are redirected to the pack's programs, so vanilla RenderTypes keep working.
- Iris lists Botania's custom shaders and ComputerCraft's TBO renderer as unfixable on its side ([incompatible-mods.md](https://github.com/IrisShaders/Iris/blob/1.21.1/docs/usage/incompatible-mods.md)). CC: Tweaked solved it by switching to VBO mode while shaders are on.
- The same doc says **Tweakeroo's freecam misbehaves with Iris** and recommends the [Freecam](https://github.com/hashalite/Freecam) mod (MIT). Freecam's `FreeCamera extends AbstractClientPlayer` and is installed as the camera entity, which is a useful reference for an Iris-safe RTS camera.
- Soft-depend on `IrisApi` (`isShaderPackInUse()`, `isRenderingShadowPass()`; API minor revision 2) to switch to fallbacks. Iris also declares Embeddium incompatible ("Please use Sodium 0.8 instead" in the 1.21.1 branch and in 1.8.14-beta.1; the 1.8.12 jar still says "Sodium 0.6").
- **Iris/Sodium pairing** *(corrected, new)*. Iris 1.8.12, the latest release, requires Sodium "[0.6,)" with the reason "Please install Sodium 0.6 for Iris to work", and its Modrinth dependency is Sodium mc1.21.1-0.6.13. Sodium's [0.8.12 release notes](https://github.com/CaffeineMC/sodium/releases/tag/mc1.21.1-0.8.12) state "Iris works as of 1.8.13". The only such Iris for 1.21.1 is **1.8.14-beta.1** (2026-06-13, "Updates to Sodium 0.8").

**Sodium rules, verified in tag mc1.21.1-0.8.13:**

- Sodium's NeoForge module still fires `RenderLevelStageEvent` for block layers (`NeoForgeLevelRenderHooks` calls `ClientHooks.dispatchRenderStage`), and it runs `AddSectionGeometryEvent` renderers while building sections.
- Entity culling (`SodiumWorldRenderer#isEntityVisible`) tests `getBoundingBoxForCulling()` against visible chunk sections. It skips the test for glowing or name-shown entities, and for boxes over 15 sections of volume. Large units such as siege engines and ships must override `getBoundingBoxForCulling`. The check wraps the frustum test inside vanilla `EntityRenderer#shouldRender`, so `noCulling` entities bypass it.
- Sodium's licence is **PolyForm Shield 1.0.0**: fine to be compatible with, not to copy from.
- Sodium and Lithium both read per-mod option overrides from mod metadata (`sodium:options`, `lithium:options` keys). A conflicting optimisation can be switched off from our own `neoforge.mods.toml` as a last resort.

*Assessment, mapping RTS visuals onto these rules:*

| Visual | Approach |
|---|---|
| Selection circles, rally lines, placement ghosts | Immediate-mode quads and lines with vanilla RenderTypes in `AFTER_TRANSLUCENT_BLOCKS`/`AFTER_PARTICLES` |
| Territory borders and terrain decals | Cached per-chunk `VertexBuffer`s with vanilla shaders. `AddSectionGeometryEvent` suits rarely changing data (every change rebuilds sections) |
| Fog of war | World-space darkening geometry plus server-side hiding of entities (§3.9). Avoid a custom post-processing pass: it is a custom shader program, which Iris's compatibility doc advises against. Iris has no special handling for vanilla `PostChain` (its `MixinPostChain` is empty), so what such a pass does under a pack is *(unverified)* *(corrected)* |
| Selected-unit outline | NeoForge outline hooks. Behaviour under shader packs untested |

### 3.8 Mixins, access transformers, interface injection

- **Availability:** verified. NeoForge 21.1.262 depends on `net.fabricmc:sponge-mixin:0.16.4+mixin.0.8.7`, and its universal jar contains `META-INF/jarjar/mixinextras-neoforge-0.5.5.jar` (range `[0.5.5,)`). MixinExtras 0.5.5 (2026-08-27, MIT) is the current upstream release. Older 21.1 builds shipped MixinExtras 0.4.1 (from 21.1.22), 0.5.0 (from 21.1.195) or 0.5.3 (from 21.1.221) *(corrected: 0.5.3 added)*, which is another reason to pin a recent NeoForge.
- **Declaring mixins:** `[[mixins]] config="${mod_id}.mixins.json"` in `neoforge.mods.toml` (MDK template). No refmap is needed (§2).
- **Access transformers:** the default `META-INF/accesstransformer.cfg` is auto-detected; declare `[[accessTransformers]]` only for other paths. Per the NeoForge docs, directives "only modify the method they directly reference", and overrides are not transformed.
- **Concrete Strategos example: fog-of-war re-checks** *(corrected)*. Vanilla re-checks `broadcastToPlayer` in two cases:
    - For one entity against all players, when that entity changes chunk section (`ChunkMap#tick`).
    - For all tracked entities against one player, every time that player's movement packet is processed. `ServerGamePacketListenerImpl#handleMovePlayer` calls `ServerChunkCache#move`, which calls `ChunkMap#move`. The vanilla client sends a position packet at least every 20 ticks, so a player is re-checked at least about once a second.

  Prompt reveal/hide therefore still needs a forced re-check. Per player, no AT is needed: the public `ServerChunkCache#move(ServerPlayer)` re-runs `TrackedEntity#updatePlayer` for every tracked entity (O(tracked entities)). Re-checking one entity against all players needs `ChunkMap#entityMap` (private) and `ChunkMap.TrackedEntity#updatePlayers` (package-private class) through an AT or mixin.
- **Interface injection:** MDG `interfaceInjectionData` is dev-time only, so a Mixin (or coremod) must add the interface at runtime (MDG README).
- [MixinSquared](https://github.com/Bawnorton/MixinSquared) (MIT): later, only if we must patch another mod's mixins.

### 3.9 Data: attachments, capabilities, components; Fabric equivalents

| Mechanism | Use in Strategos | Notes |
|---|---|---|
| **Data attachments** (NeoForge) | Per-unit persistent state (owner faction, orders queue); per-chunk territory and exploration; per-level faction state | `serialize(Codec)`. `sync(StreamCodec)` "always writes the full data", so it suits low-frequency or moderate-size state. A custom `AttachmentSyncHandler` can send deltas, because `write` receives `initialSync`. Per-player sync uses `sync(BiPredicate<IAttachmentHolder, ServerPlayer>, codec)`, e.g. faction resources only to that faction. **Before 21.1.264 the initial sync ignored this predicate** ([changelog](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt): "Check AttachmentSyncHandler#syncToPlayer() for initial sync") |
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

- `PathNavigation` builds paths synchronously on the server thread through a `PathNavigationRegion` snapshot (a region view over chunk references), with `maxVisitedNodes = floor(FOLLOW_RANGE × 16)`.
- `ServerLevel#sendBlockUpdated` loops over **all** `navigatingMobs` whenever a collision shape changes. The set holds every loaded `Mob`, added on tracking start. It synchronously recomputes the paths of mobs whose current path passes near the changed block (`PathNavigation#shouldRecomputePath`). Each mob recomputes at most once per 20 ticks; later changes are deferred to its next navigation tick (`hasDelayedRecomputation`). That is O(units) work per block change plus path searches for the affected units, in a game where construction changes blocks constantly. Lithium's `entity.inactive_navigations` mixin narrows the scan.

| Reference | 1.21.1 status | Licence | Verdict |
|---|---|---|---|
| [Baritone](https://github.com/cabaletta/baritone) | v1.11.3 for 1.21.1 (release 2026-09-01) | "LGPL-3.0 with anime exception" | **Reference only.** Client-side single-player bot. Good A* engineering (segmented paths, movement cost model). Not a library for hundreds of server-side units |
| [MineColonies](https://github.com/ldtteam/minecolonies) | Active (pushed 2026-10-13) | **GPL-3.0** | **Reference only.** Async design: a dedicated `ThreadPoolExecutor` ("Minecolonies Pathfinding Worker", 1 thread) runs `AbstractPathJob`s over cached world data (a `ChunkCache`) ([Pathfinding.java](https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/pathfinding/Pathfinding.java)). Copying code would force GPL-compatible licensing |
| [gdx-ai](https://github.com/libgdx/gdx-ai) (pathfinding, steering, formations, BTs) | 1.8.2 (**2019-07-26**); compile dependency on libGDX `gdx` 1.9.8 | Apache-2.0 | **Avoid** |
| GitHub search (2026-10-20) for Minecraft/Java pathfinding libraries | Only Spigot plugins and bots | — | None reusable |

*Assessment:* write our own, in the Minecraft-free core:

- Grid walkability snapshots per region.
- Hierarchical A* (HPA*) for long routes, flow fields for group moves, and local steering and formation slots.
- Running on worker threads over immutable snapshots, then committing on the server thread.

Two hard rules:

- Never touch `Level` or `level.random` off-thread. C2ME ships `CheckedThreadLocalRandom` "to prevent mods from screwing up Minecraft internals" and will flag it ([C2ME page](https://modrinth.com/mod/c2me-neoforge)).
- Use our own seeded `java.util.random.RandomGenerator` for reproducible tests.

### 3.11 Java libraries bundled with Minecraft 1.21.1; external libraries via jarJar

**Available on both client and dedicated server.** Taken from the server jar's `META-INF/libraries.list`; NeoForge pins identical versions with Gradle `strictly` in `net.neoforged:minecraft-dependencies:1.21.1` (served from `maven.neoforged.net/mojang-meta`).

| Library | Version | Licence | Use in a Minecraft-free core |
|---|---|---|---|
| fastutil | **8.5.12** | Apache-2.0 | Yes: primitive collections for struct-of-arrays unit storage |
| JOML | **1.10.5** | MIT | Optional: vector and matrix math |
| Guava | **32.1.2-jre** (+ failureaccess 1.0.1) | Apache-2.0 | Sparingly |
| Gson | **2.10.1** | Apache-2.0 | Optional |
| DataFixerUpper | **8.0.16** (`Codec`, `MapCodec`, `RecordCodecBuilder`, `DataResult`) | MIT | Yes, if the core owns data definitions. `StreamCodec` lives in Minecraft, not DFU. DFU is hosted on libraries.minecraft.net, not Maven Central |
| Brigadier | 1.3.10 | MIT | No (mod layer) |
| Netty | 4.1.97.Final | Apache-2.0 | No (mod layer) |
| slf4j-api | **2.0.9** (log4j 2.22.1 behind it) | MIT | Yes (logging facade) |
| commons-lang3 / commons-io | 3.14.0 / 2.15.1 | Apache-2.0 | Avoid in the core |
| lz4-java, oshi, jna, jopt-simple, authlib, mojang logging | 1.8.0, 6.4.10, 5.14.0, 5.0.4, 6.0.54, 1.2.7 | various | No |

- **Client-only** (absent on a dedicated server, so the core must not touch them): LWJGL 3.3.3, icu4j 73.2, commons-compress 1.26.0, commons-codec 1.16.0, httpclient 4.5.13, jorbis, text2speech, java-objc-bridge.
- **NeoForge adds:** NightConfig 3.8.3, ASM 9.10.1, the Mixin and MixinExtras builds above, FML 4.0.45, event bus 8.0.5.
- **Build rule:** compile the core against these exact versions (`compileOnly` in the core, `testImplementation` for tests). Because NeoForge declares them `strictly`, a core that exposes a newer fastutil or Guava (through `api`/`implementation`) will fail dependency resolution in the mod project. The newest upstream versions today are fastutil 8.5.19, JOML 1.10.9 and Guava 33.7.2.

**External libraries (jarJar):**

| Library | Last release | Licence | Notes | Verdict |
|---|---|---|---|---|
| [Dominion ECS](https://github.com/dominion-dev/dominion-ecs-java) | 0.9.0 (**2023-07-16**); repo idle since 2024-03 | MIT | 0.16 MB, 0.x API | Avoid |
| [Artemis-odb](https://github.com/junkdog/artemis-odb) | 2.3.0 (**2019-11-09**) | BSD-2-Clause | — | Avoid |
| [Ashley](https://github.com/libgdx/ashley) | 1.7.4 (2021-02-05) | Apache-2.0 | Compile dependency on libGDX `gdx` 1.9.9 | Avoid |
| Behaviour trees: gdx-ai | 1.8.2 (2019) | Apache-2.0 | Pulls libGDX | Avoid |

No maintained, standalone Java BT library worth a dependency turned up.

*Assessment:* write our own. Entity handles become int index plus generation, components become fastutil arrays, and systems become plain classes with a deterministic order. A BT or utility-AI layer is a few hundred lines, fully testable, and needs no jar-in-jar.

If a non-mod library is ever jar-in-jarred:

- Use MDG's `jarJar(implementation("g:a")) { version { strictly '[x,y)'; prefer 'x.y.z' } }`.
- On MC < 1.21.9, also add `additionalRuntimeClasspath`. The README warns of `ClassNotFoundException` in dev runs otherwise.

Example of a hidden version conflict *(corrected)*. imgui-java 1.92.7.1 (2026-07-27, MIT) ships its LWJGL backend modules (`imgui-java-lwjgl3`, `imgui-java-app`) built on LWJGL 3.4.1 and `lwjgl-sdl`, but MC 1.21.1 pins LWJGL **3.3.3**. The `imgui-java-binding` artifact itself has no LWJGL dependency. A dev-only ImGui debug overlay can therefore use ImGuiMC (FoundryMC `imguimc` 2.0.5+mc1.21.1, 2026-08-10, LGPL-3.0-only, 10.7 MB). It jar-in-jars `imgui-java-binding` 1.92.7.1 plus natives and renders through its own Minecraft-side renderer. Veil does **not** bundle ImGui; it only declares ImGuiMC `[2.0.0,3.0.0)` as an optional dependency. Using imgui-java's own LWJGL backend would need imgui-java 1.86.11 (2023-09-29), the last built on LWJGL 3.3.3.

### 3.12 Development and test toolchain

| Tool | Latest (date) | Recommendation |
|---|---|---|
| JUnit | 6.1.3 (2026-08-07); **5.14.4** (2026-04-26) | **5.14.4.** `junit-fml 4.0.45` is built against JUnit BOM 5.10.2, and jqwik 1.10.2 depends on JUnit Platform 1.14.4. Mixing in JUnit 6 is untested |
| AssertJ | 3.27.7 (2026-01-24); 4.0.0-M1 is a milestone | 3.27.7 |
| jqwik | 1.10.2 (2026-10-10) | Property tests for economy, combat and pathfinding invariants |
| Mockito | 5.24.0 (2026-09-23) | Sparingly. Do not mock Minecraft classes; isolate the core instead. Configure Mockito as a `-javaagent` on JDK 21. Mockito's own docs require this ("Explicitly setting up instrumentation for inline mocking (Java 21+)"); it is not just an assessment *(corrected)* |
| JMH | 1.37 (2023-08-03, still current) | Microbenchmarks for the core (spatial index, pathfinding, tick) |
| `me.champeau.jmh` plugin | **0.7.3** (Plugin Portal 2025-01-30; GitHub release 2025-04-16; functional tests at the 0.7.3 tag run on Gradle 7.0, 8.0 and the wrapper's 8.12 *(corrected)*) | Gradle 9 support was merged 2026-07-19 ([#282](https://github.com/melix/jmh-gradle-plugin/issues/282), PR #288) **but is not released**. Try it on Gradle 9.2.1; fall back to a `jmh` source set with `jmh-generator-annprocess` and a `JavaExec` running `org.openjdk.jmh.Main` |
| Spotless | 8.10.4 (2026-10-08) | With **palantir-java-format 2.102.0** (2026-10-05). Its 120-column default suits Minecraft's long names. google-java-format 1.37.0 (2026-10-01) is the alternative |
| Error Prone | 2.50.0 (2026-06-10); `net.ltgt.errorprone` 5.1.1 (2026-08-25) | Adopt. "Error Prone must be run on JDK 21 or newer" ([docs](https://errorprone.info/docs/installation)), which matches our toolchain |
| NullAway | 0.14.2 (2026-09-25); `net.ltgt.nullaway` 3.3.0 (2026-10-05) | Start in **standard mode**, which honours JSpecify annotations. JSpecify mode needs javac ≥ 22, or OpenJDK ≥ 21.0.8 with `-XDaddTypeAnnotationsToSymbol=true` ([wiki](https://github.com/uber/NullAway/wiki/JSpecify-Support)) |
| JSpecify | 1.0.1 (2026-07-29) | `@NullMarked` packages; `compileOnly` |
| ArchUnit | 1.5.1 (2026-09-25) | Enforce that common code never references `net.minecraft.client..`, catching dedicated-server crashes at test time, plus core layering rules |
| NeoForge testing | `testframework` 21.1.264; MDG `unitTest { enable(); testedMod = mods.strategos }`; GameTests via the `gameTestServer` run | Pure-core JUnit for logic; GameTests for in-world behaviour; both in CI |

### 3.13 Performance mods to stay compatible with, and pitfalls with many entities

| Mod | NeoForge 1.21.1 | Fabric 1.21.1 | Licence | What to watch |
|---|---|---|---|---|
| [Sodium](https://modrinth.com/mod/sodium) | **mc1.21.1-0.8.13** (2026-08-28). 0.8.x is a 2026 backport; the previous 1.21.1 line ended at 0.6.13 (2025-04-04) | same | PolyForm Shield 1.0.0 | Entity culling by `getBoundingBoxForCulling` and section visibility; model fast path; fires NeoForge stage events |
| Embeddium | 1.0.15+mc1.21.1 (2025-01-24), last | — | LGPL-3.0 | Obsolete on 1.21.1; Iris marks it incompatible. Do not target |
| [Iris](https://modrinth.com/mod/iris) | 1.8.12 (2025-06-17, release; **Sodium 0.6.x only**); 1.8.14-beta.1 (2026-06-13; the only build that works with Sodium 0.8.x) *(corrected)* | 1.8.14-beta.1 (latest Fabric release 1.8.8, 2025-02-20, from the Sodium 0.6 era) | LGPL-3.0 | Custom shaders ignored under packs; detached-camera issues; pick the Iris build that matches the Sodium line |
| Oculus | no 1.21.1 build | — | — | Obsolete |
| [Lithium](https://modrinth.com/mod/lithium) | mc1.21.1-0.15.4 (2026-06-27) | same | LGPL-3.0 | Mixins in `ai.pathing` (disabled by default in 0.15.4), `entity.collisions` and `entity.inactive_navigations` (block updates skip mobs that won't react); mod-side overrides via `lithium:options` |
| Radium | 0.13.1 (2024-09-20, beta), last | — | LGPL-3.0 | Superseded by Lithium on NeoForge |
| [ModernFix](https://modrinth.com/mod/modernfix) | 5.27.26 (2026-10-08) | 5.25.1 (2025-11-08) | LGPL-3.0 | Opt-in `dynamic_entity_renderers` ("Some mods are not compatible… EntityRenderer crashes") and `dynamic_resources`, both default `false`. Never cache renderers or baked models at startup |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | 7.0.3 (2026-01-25) | same | MIT | Low risk |
| [Entity Culling](https://modrinth.com/mod/entityculling) | 1.11.3 (2026-10-08) | same | tr7zw Protective License (no commercial use) | Async occlusion culling plus **client tick culling** (`tickCulling=true` by default). Respects the vanilla `noCulling` flag; does not cull hitboxes over 50 blocks; API `addDynamicEntityWhitelist(Function<Entity,Boolean>)`; config whitelists |
| [C2ME](https://modrinth.com/mod/c2me-neoforge) | 0.4.0-alpha.0.123+1.21.1 (2026-10-11, **all 1.21.1 builds alpha**) | c2me-fabric 0.4.0-alpha.0.30 | MIT | Threaded chunk generation and IO; never use world random off-thread |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 1.6.15 (2026-10-13) | same | LGPL-3.0-or-later | Batches GUI/HUD drawing (and other mods' immediate-mode rendering); test our HUD with it |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | 3.3.3 (2026-09-29) | same | LGPL-3.0 | 28 MB. Our overlays will not appear on LOD terrain *(unverified)*; test later |
| Moonrise, Krypton | Moonrise 0.1.0-beta.15 (2025-10-10, beta) | Moonrise same; Krypton 0.2.8 (2024-06-22, Fabric-only) | GPL-3.0-only / LGPL-3.0-only | Low priority |

**Pitfalls for mods with many custom entities**, all verified in decompiled 1.21.1:

1. **Cramming.** The gamerule `maxEntityCramming` defaults to 24. `LivingEntity#pushEntities` runs an AABB entity query per entity per tick. It deals 6.0 cramming damage when more than 24 entities overlap, checked with a 1-in-4 random chance per tick. RTS blobs will hit this; override pushing and cramming behaviour for units.
2. **Navigation recompute storm.** `ServerLevel#sendBlockUpdated` scans every loaded mob on each collision-shape change and recomputes affected paths (at most once per 20 ticks per mob) (§3.10).
3. **Tracking cost.** Each tracked entity in ticking range calls `ServerEntity#sendChanges()` every server tick. Visibility (`broadcastToPlayer`) is re-checked when the entity changes chunk section and whenever a player's movement packet is processed (at least about once a second per player). It is not re-checked only on section changes *(corrected)*; see §3.8 for forcing a prompt re-check. Set `clientTrackingRange`/`updateInterval` per unit type deliberately; vanilla mobs use 8–10 chunks and interval 2–3.
4. **Culling boxes.** Sodium, Entity Culling and vanilla frustum checks all use `getBoundingBoxForCulling()`. Large units must override it, or set `noCulling`, which all three honour.
5. **Thread safety.** C2ME runs generation in parallel. Our chunk attachments and generation hooks must tolerate off-main-thread calls, and our worker threads must never call into `Level`.

### 3.14 Dev tools

- [spark](https://modrinth.com/mod/spark): **1.10.124-neoforge-1.21.1 (2025-02-23)**; Fabric 1.10.109 (2024-09-26). GPL-3.0, 3.6 MB. Keep it in dev as `localRuntime` (`maven.modrinth:spark:1.10.124-neoforge-1.21.1`); never ship it. ModernFix has opt-in `spark_profile_launch` and `spark_profile_world_join` options for profiling startup and world joins.
- Vanilla `/jfr start|stop` (Java Flight Recorder; MC ships `flightrecorder-config.jfc`) records tick, network and chunk events with no mod.
- **GPU debugging on macOS is weak.** RenderDoc supports Windows, Linux and Android ("RenderDoc supports Windows, Linux, and Android for capture and replay out of the box", [renderdoc.org](https://renderdoc.org/); its [FAQ](https://renderdoc.org/docs/getting_started/faq.html) lists no platforms) *(corrected source)*. macOS OpenGL stops at 4.1, which the owner's M4 Pro confirms ("OpenGL Version: 4.1 Metal - 90.5"). *Assessment:* if rendering work grows, keep a Windows or Linux machine for RenderDoc and for Flywheel indirect testing.

---

## 4. Fabric equivalents at a glance

| Need | NeoForge 21.1 | Fabric 1.21.1 |
|---|---|---|
| Mixin / MixinExtras | Bundled (0.16.4 / 0.5.5) | Bundled in Fabric Loader (0.20.1: Mixin 0.17.5, MixinExtras 0.5.5) |
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

**Shipped runtime dependencies: none.** Only `neoforge >= 21.1.264` and `minecraft [1.21.1]`.

Build sketch (Groovy DSL, MDG 2.0.148 is the latest, 2026-09-29). Not run; versions as above, with the Iris coordinate corrected.

```groovy
// core: pure Java 21, no Minecraft on the classpath. Exact MC-bundled versions.
dependencies {
    compileOnly 'it.unimi.dsi:fastutil:8.5.12'
    compileOnly 'org.slf4j:slf4j-api:2.0.9'
    compileOnly 'org.jspecify:jspecify:1.0.1'
    // optional: 'org.joml:joml:1.10.5', 'com.mojang:datafixerupper:8.0.16' (libraries.minecraft.net, not Maven Central)
    testImplementation platform('org.junit:junit-bom:5.14.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly   'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.assertj:assertj-core:3.27.7'
    testImplementation 'net.jqwik:jqwik:1.10.2'
    testImplementation 'it.unimi.dsi:fastutil:8.5.12'
}
// mod: neoForge { version = '21.1.264'; unitTest { enable(); testedMod = mods.strategos } }
// repositories: maven { url 'https://api.modrinth.com/maven'; content { includeGroup 'maven.modrinth' } }
dependencies {
    testImplementation 'net.neoforged:testframework:21.1.264'
    localRuntime 'maven.modrinth:spark:1.10.124-neoforge-1.21.1'      // dev only
    if (providers.gradleProperty('compat').isPresent()) {              // ./gradlew runClient -Pcompat
        localRuntime 'maven.modrinth:sodium:mc1.21.1-0.8.13-neoforge'
        localRuntime 'maven.modrinth:iris:1.8.14-beta.1+1.21.1-neoforge' // corrected: beta, the only Iris for Sodium 0.8; 1.8.12 needs Sodium 0.6.13
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

All Modrinth Maven coordinates above were resolved against `api.modrinth.com/maven` and point at the NeoForge 1.21.1 files.

Two ways to put `core` inside the mod jar. Confirm which one in the first build spike:

- A separate source set in the same project, declared in MDG's `mods { strategos { sourceSet … } }` (the README shows multi-source-set mods).
- A plain `java-library` subproject whose classes are merged into the mod jar.

Do not jar-in-jar the core as a separate library: on MC < 1.21.9 that needs `additionalRuntimeClasspath` in dev runs.

## 6. "Later" list, in order of likely need

1. **Iris fallback and test pass** with two or three popular shader packs. Soft dependency on `IrisApi` (Modrinth Maven jar, `compileOnly`). Use Iris 1.8.14-beta.1 with Sodium 0.8.13, or Iris 1.8.12 with Sodium 0.6.13, never mixed *(corrected)*.
2. **GeckoLib 4.9.3**, only if the art direction needs bespoke animated models beyond vanilla plus JSON keyframes. First benchmark 300–500 animated units against vanilla models with Sodium.
3. **Flywheel 1.0.7** as an optional render backend, only if profiling shows entity rendering is the bottleneck. Keep the vanilla renderer as the fallback for Iris and macOS.
4. **JEI plugin** (also covers EMI) if items or recipes appear; compile against the JEI API and test against the latest *release* (19.64.0.455 today; 19.69.x is beta). **Jade plugin** for unit tooltips.
5. **Guide:** GuideME (NeoForge-only, 3D building scenes) or Modonomicon (multi-loader); Ponder for animated tutorials.
6. **Minimap integration** for territory overlays: JourneyMap 1.21.1-6.0.9 (all rights reserved, with a public API repo, TeamJM/journeymap-api) or Xaero's 26.6.0 (all rights reserved) *(corrected)*.
7. **Distant Horizons 3.3.3** compatibility pass for long-range top-down views.
8. **Forge Config API Port**, only for a Fabric port.
9. **MixinSquared**, only if another mod's mixins must be patched.
10. **Dev-only ImGui overlay** via ImGuiMC 2.0.5+mc1.21.1, which bundles imgui-java-binding 1.92.7.1 and works with LWJGL 3.3.3. Alternatively use imgui-java 1.86.11 with its own LWJGL 3.3.3 backend *(corrected)*.

## 7. Avoid list, with reasons

| Avoid | Reason |
|---|---|
| owo-lib on NeoForge | Beta port |
| LDLib2 | 7.6 MB, NeoForge-only framework lock-in |
| ModernUI | 25 MB, global UI and text changes |
| YACL, Cloth Config, Fzzy Config | Redundant with NeoForge's config screen |
| Registrate | Unneeded abstraction for an entity-centric mod |
| Veil, Lodestone, Satin | Custom shader and post stacks that Iris does not support under shader packs *(corrected wording)* |
| AzureLib | Fork of GeckoLib |
| REI on NeoForge | Stale beta |
| Embeddium, Oculus, Radium | Obsolete on 1.21.1 |
| ECS and BT libraries | Dormant, or pull in libGDX |
| Baritone, MineColonies code | Licence and architecture mismatch; read for ideas only |
| Vanilla per-unit `PathNavigation` for mass movement | Synchronous A* per mob; recompute storms on block changes (§3.10) |

---

## Verification notes

**How it was checked (2026-10-20).** Each claim was re-queried from primary sources rather than taken from the earlier session's downloads. Reused downloaded files were integrity-checked first. The server.jar sha1 matches Mojang's version JSON. The NeoForge sources jar, testframework jar and FML jar sha1s match maven.neoforged.net. The Iris, Veil, Vanillin and ImGuiMC jars' sha512 hashes match Modrinth.

- **Modrinth API v2:** project and version queries for 1.21.1 × {neoforge, fabric, forge} for about 50 projects, plus all-version queries for GeckoLib, owo-lib and Veil.
- **CurseForge:** checked via api.cfwidget.com for 8 of the 12 "no Forge build" mods.
- **Maven:** NeoForge maven-metadata, POM Last-Modified dates, changelog, the 21.1.262/264 `.module` files, universal-jar jarjar metadata, the `mojang-meta` minecraft-dependencies module, NeoForm, junit-fml and testframework. Also Maven Central (toolchain, MixinExtras, ECS/AI POMs, imgui-java), the Gradle Plugin Portal, maven.createmod.net (Flywheel, Ponder), maven.gegy.dev (Registrate), maven.blamejared.com (JEI), maven.fabricmc.net (Fabric API, Fabric Loader), the Forge maven and promotions, and OptiFine downloads.
- **Decompiled MC 1.21.1 + NeoForge 21.1.262** (scratchpad mc-src-1.21.1): attachments, capabilities, registries/data maps, config, GUI layers, animation loader, render stages, section geometry, outline hooks, ChunkMap tracking, ServerLevel/PathNavigation, cramming, EntityType ranges, model and layer generics, Screen, layouts, GUI sprites and JfrCommand. Plus a source diff of AttachmentSync between 21.1.262 and 21.1.264.
- **Upstream sources:** Sodium tag mc1.21.1-0.8.13, Iris branch 1.21.1 (docs, mixins, config, API, mods.toml), Lithium tag mc1.21.1-0.15.4, Flywheel 1.21.1/dev, GeckoLib 1.21.1, ModernFix 1.21.1, Entity Culling main, Create mc1.21.1/dev, MineColonies version/main, Freecam, Fabric API 1.21.1, Blockbench, MDG README and LEGACY, and NeoForge PR #1476.
- **Docs:** NeoForge AT docs, NeoForge 20.2 post, Error Prone, NullAway wiki, RenderDoc, Gradle rich versions and the Mockito javadoc.
- **The owner's local Minecraft log:** for the Mac's OpenGL version.
- **What was not run:** no Gradle in the MDK, and no game or benchmark.

**What changed:**

1. **Compat profile (decision-relevant).** Sodium 0.8.13 + Iris 1.8.12 does not work. Iris 1.8.12 needs Sodium 0.6.x, and Sodium's 0.8.12 notes say "Iris works as of 1.8.13". The fix is Iris 1.8.14-beta.1 (beta), or Sodium 0.6.13 with Iris 1.8.12. This touches §0.2, §3.7, §3.13, §5, §6 and recommendation 9.
2. **Fog-of-war tracking cadence (decision-relevant).** `broadcastToPlayer` is re-checked not only on chunk-section changes but also on every processed player movement packet (`ChunkMap#move`, at least about once a second). A per-player forced re-check needs no AT (`ServerChunkCache#move`). This touches §3.8, §3.13 and recommendation 7.
3. **"Iris is the only shader mod on 1.21.1".** True only for NeoForge/Fabric. OptiFine HD U J1 exists for vanilla and Forge 1.21.1. This affects the loader signal in §0.5.
4. **JEI.** 19.69.0.457 is a beta. The latest release is 19.64.0.455 (2.4 MB).
5. **ImGui.** `imgui-java-binding` has no LWJGL dependency. Veil does not bundle ImGui. ImGuiMC 2.0.5 (bundling imgui-java-binding 1.92.7.1) is the 1.21.1 route, and imgui-java 1.86.11 is the last LWJGL-3.3.3 backend.
6. **GeckoLib.** 5.x started with 5.0 (2025-04-21; alpha 2025-04-08), not 5.1.0.
7. **PR #1476.** It does not name the Blockbench plugin. The plugin exists and is by the PR's author.
8. **NeoForge release cadence.** It is not strictly weekly (four-week gap in August). 21.1.260 and 21.1.261 were never published; their changes first shipped in 21.1.262.
9. **Smaller corrections:**
    - MixinExtras history: added 0.5.3 (21.1.221).
    - Outline hooks: 21.1.51 added only `requestOutlineEffect`.
    - jmh-gradle-plugin 0.7.3 CI covers Gradle 7.0/8.0/8.12, not 8.10.
    - The RenderDoc evidence moved from the FAQ to the homepage.
    - Mockito's `-javaagent` advice is documented, not just an assessment.
    - JourneyMap is all rights reserved too.
    - Radium 0.13.1 is a beta.
    - The Moonrise/Krypton row's columns were realigned.
10. **Added detail from checks, no claim reversed:**
    - Flywheel's render origin moves only after 256 blocks.
    - Vanillin on Modrinth bundles Flywheel 1.0.4; Create pins Flywheel 1.0.6 and bundles Registrate MC1.21-1.3.0+67.
    - Vanilla path recompute is throttled to once per 20 ticks per mob, and navigatingMobs holds every loaded Mob.
    - Lithium's `ai.pathing` is off by default, and `inactive_navigations` narrows the block-update scan.
    - Sodium culling sits inside vanilla's `!noCulling` branch.
    - The attachment `AttachmentSyncHandler` can send deltas.
    - The minecraft-dependencies module lives in `maven.neoforged.net/mojang-meta`.
    - DFU is on libraries.minecraft.net.
    - owo-lib's latest beta fixed breakage from NeoForge's network hardening.
    - GuideME's bundled licences are Lucene (Apache-2.0) and micromark/mdast.

**Still unverified:**

- How custom post-processing (PostChain) and Veil's own programs actually render under Iris. Iris has no PostChain handling, so they need a test.
- That Distant Horizons LOD terrain hides our overlays.
- CurseForge's Flywheel listing; cfwidget returned 404 for Iris, FerriteCore, Flywheel and Veil, which Modrinth and Maven cover.
- That "no GeckoLib per-entity cost figures are published", a negative claim.
- Which artifact the report sourced from Cloudsmith.

**Partial answers to the open questions:**

- **Q5 (Flywheel origin resets).** A reset happens once per 256 blocks of camera travel (`EngineImpl` `maxOriginDistance` = 256), not per frame.
- **Q8 (Entity Culling at RTS height).** Entities farther than `tracingDistance` (128) are never occlusion-culled. Tick culling still applies to any entity that was culled or outside the camera frustum on the last frame, and such entities get a reduced `basicTick` (position history, `aiStep`, hurt timer).
- **Q11 (imgui-java on LWJGL 3.3.3).** imgui-java 1.86.11 is the last whose `imgui-java-lwjgl3` backend targets LWJGL 3.3.3. ImGuiMC 2.0.5+mc1.21.1 runs imgui-java-binding 1.92.7.1 on 1.21.1 with its own renderer.

## Recommendations (after verification)

1. 1. Ship the MVP with zero hard runtime dependencies on NeoForge >= 21.1.264. That build fixes the attachment initial-sync leak: before it, per-player sync predicates were ignored on initial sync. The network-decoding hardening (changelog entries 21.1.260–262) first shipped in 21.1.262, because 260 and 261 were never published. Re-test every compat mod against >= 21.1.262, since that hardening already broke owo-lib's earlier NeoForge build. NeoForge bundles Mixin 0.16.4 and MixinExtras 0.5.5.
2. 2. Build the simulation core as a Minecraft-free module compiled against the exact MC-bundled versions: fastutil 8.5.12, slf4j-api 2.0.9 and JSpecify 1.0.1; add JOML 1.10.5 and DFU 8.0.16 only if needed. DFU comes from libraries.minecraft.net, not Maven Central. Keep these compileOnly so NeoForge's strictly pins cannot conflict. Write our own SoA entity store, pathfinding (HPA*/flow fields on worker threads over immutable snapshots) and AI (BT/utility). Jar-in-jar no external libraries.
3. 3. Render units with vanilla HumanoidModel/PlayerModel plus the armour, held-item and head layers. Use NeoForge JSON keyframe animations with custom HierarchicalModels only for special units. Blockbench exports these through the 'Animation to JSON Converter' plugin by Gaming32; the 'Modded Entity – Forge 1.17+ (Mojmaps)' template exports models. Decide on GeckoLib 4.9.3 only after a 300–500-unit benchmark against the vanilla path with Sodium. GeckoLib's 5.x API (from 5.0, 2025-04-21, MC 1.21.5) means a migration on any later MC port.
4. 4. Draw every custom world visual (selection circles, territory, decals, fog overlay) with vanilla RenderTypes and shader programs, through RenderLevelStageEvent, cached VertexBuffers or AddSectionGeometryEvent. Ship no custom ShaderInstance or post pass (PostChain) without a fallback that checks IrisApi.isShaderPackInUse(). Iris skips unknown ShaderInstances and has no PostChain handling at all. Base the RTS camera on a camera entity (Freecam-style), not a hacked detached camera.
5. 5. Build UI on vanilla Screen, GuiGraphics, layouts and nine-slice GUI sprites, plus RegisterGuiLayersEvent for the passive HUD. Write a small in-house RTS widget kit inside a non-pausing Screen. Do not adopt owo-lib (still beta on NeoForge), LDLib2 or ModernUI.
6. 6. Use ModConfigSpec and NeoForge's ConfigurationScreen for settings only. Put balance and content (units, buildings, techs, civs) in datapack registries generated with JsonCodecProvider, and per-block resource yields in Data Maps.
7. 7. Store persistent per-unit, per-chunk and per-faction state in data attachments, with per-player sync predicates for secret data. Use a custom AttachmentSyncHandler where deltas matter, because the StreamCodec overloads always send full data. Use SynchedEntityData for hot per-unit fields and batched custom payloads for bulk updates. Use capabilities only for interop, e.g. IItemHandler on drop sites. For server-side fog of war use Entity#broadcastToPlayer, and force a re-check when visibility changes, since vanilla re-checks only on the entity's section change and on the player's movement packets (at least about once a second). Per player, call the public ServerChunkCache#move(ServerPlayer) (re-runs updatePlayer for all tracked entities, no AT). For one entity against all players, add an AT/mixin for ChunkMap#entityMap and TrackedEntity#updatePlayers.
8. 8. Adopt this toolchain: JUnit 5.14.4, AssertJ 3.27.7, jqwik 1.10.2, and Mockito 5.24.0 used sparingly, never mocking Minecraft, attached as a -javaagent on JDK 21 as Mockito's docs require. Add JMH 1.37 (try me.champeau.jmh 0.7.3, whose tests stop at Gradle 8.12; fall back to a JavaExec of org.openjdk.jmh.Main), Spotless 8.10.4 with palantir-java-format 2.102.0, Error Prone 2.50.0 with NullAway 0.14.2 in standard mode and JSpecify annotations, ArchUnit 1.5.1 for client/common separation, MDG unitTest, testframework 21.1.264, and GameTests in CI.
9. 9. Keep a dev-only compat profile (localRuntime behind -Pcompat) through the Modrinth Maven: Sodium mc1.21.1-0.8.13 with Iris 1.8.14-beta.1+1.21.1, plus a shader pack. 1.8.14-beta.1 is a beta, but it is the only Iris that works with Sodium 0.8.x. Iris 1.8.12 needs Sodium 0.6.13, so never pair it with 0.8.13. Add Lithium 0.15.4, ModernFix 5.27.26, FerriteCore 7.0.3, Entity Culling 1.11.3 (by version id eieYbWYb), ImmediatelyFast 1.6.15, and C2ME for server tests. Keep spark 1.10.124 in every dev run.
10. 10. Design units around known vanilla costs. Disable or replace cramming and pushing, because cramming deals 6.0 damage once 24 or more other entities overlap. Avoid vanilla PathNavigation for mass movement: every collision-shape change scans every loaded mob, and paths are recomputed synchronously, throttled to once per 20 ticks per mob. Override getBoundingBoxForCulling for large units, or set noCulling, which vanilla, Sodium and Entity Culling all honour. Set clientTrackingRange and updateInterval per unit type.
11. 11. Defer to the later list: Flywheel 1.0.7 (jar-in-jar, with a vanilla fallback; only if profiling shows a render bottleneck; origin rebuild every 256 blocks of camera travel), a JEI plugin (covers EMI; target the latest JEI release, 19.64.0.455 today, since 19.69.x is beta), a Jade plugin, and a guide (GuideME or Modonomicon). Also defer minimap integration (JourneyMap and Xaero's are both all rights reserved; use their public APIs) and a Distant Horizons pass. For a dev-only ImGui overlay use ImGuiMC 2.0.5+mc1.21.1, not imgui-java's LWJGL 3.4.1 backend.
12. 12. If rendering work grows, keep a Windows or Linux GPU machine for RenderDoc and for Flywheel's indirect backend. RenderDoc supports Windows, Linux and Android only. The owner's M4 Pro reports OpenGL 4.1 Metal, so only Flywheel's instancing backend can run there.

## Fact-check verdicts

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | NeoForge 21.1.x is actively maintained: 21.1.264 was published 2026-10-19; 21.1.260–262 (Oct 2026) enforced network decoding/payload limits. | Confirmed. One detail: 21.1.260 and 21.1.261 were never published to Maven (both 404). Their changes first shipped in 21.1.262 (2026-10-15). | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (21.1 list ends 259, 262, 263, 264); neoforge-21.1.264.pom Last-Modified Mon, 19 Oct 2026 03:40:46 GMT; neoforge-21.1.264-changelog.txt lines: 21.1.262 'Enforce ad-hoc channel identifier limits', 21.1.261 'Enforce network setup payload directions and decoding limits', 21.1.260 'Verify size of split packets on reception'; .../21.1.260/neoforge-21.1.260.pom and .../21.1.261/... both HTTP 404 |
| corrected | The 21.1 line still gets fixes every week (report §0.1). | Fixes are frequent but not weekly. There were 13 builds from 2026-09-06 to 2026-10-19, but August 2026 had a four-week gap: 21.1.248 on 08-01, 21.1.249 on 08-28, 21.1.250 on 09-06. | Last-Modified headers of neoforge-21.1.{240..264}.pom on maven.neoforged.net (e.g. 248: 2026-08-01, 249: 2026-08-28, 250: 2026-09-06, 251: 09-18, 252: 09-26, 253: 10-03, 254/255: 10-04, 256: 10-05, 257: 10-09, 258/259: 10-10, 262: 10-15, 263: 10-18, 264: 10-19) |
| confirmed | NeoForge 21.1.262 depends on net.fabricmc:sponge-mixin 0.16.4+mixin.0.8.7 and bundles MixinExtras 0.5.5 as jar-in-jar; no separate mixin library is needed. |  | neoforge-21.1.262.module and neoforge-21.1.264.module: sponge-mixin requires 0.16.4+mixin.0.8.7, mixinextras-neoforge 0.5.5; neoforge-21.1.262-universal.jar and -264-universal.jar META-INF/jarjar/metadata.json: mixinextras-neoforge range [0.5.5,), artifactVersion 0.5.5; changelog 21.1.254 (Mixin) and 21.1.255 (MixinExtras); Maven Central io/github/llamalad7/mixinextras-common maven-metadata.xml latest 0.5.5, lastUpdated 20260827; GitHub LlamaLad7/MixinExtras licence MIT |
| corrected | Older 21.1 builds shipped MixinExtras 0.4.1 (21.1.22) or 0.5.0 (21.1.195). | This is incomplete. The history is 0.4.1 from 21.1.22, 0.5.0 from 21.1.195, 0.5.3 from 21.1.221, and 0.5.5 from 21.1.255. | neoforge-21.1.264-changelog.txt: 21.1.22 'mixinextras-neoforge to v0.4.1', 21.1.195 'to v0.5.0', 21.1.221 'Bump MixinExtras to 0.5.3', 21.1.255 'MixinExtras to v0.5.5' |
| confirmed | NeoForge 21.1 data attachments (Entity, BlockEntity, Chunk, Level) support Codec persistence and client sync (backported in 21.1.197); the per-player sendToPlayer predicate is honored for the initial sync only since 21.1.264. | Confirmed. Addition: the sync(StreamCodec) overloads always write the full data, but sync(AttachmentSyncHandler) can write deltas, because write(buf, value, initialSync) is told whether this is the initial sync. All four holder types are synced. | mc-src-1.21.1/net/neoforged/neoforge/attachment/AttachmentType.java:184 serialize(Codec), :224 copyOnDeath, :250 sync(AttachmentSyncHandler), :261 sync(StreamCodec) ('The full data is always written'), :273 sync(BiPredicate, StreamCodec); AttachmentSyncHandler.java write(..., boolean initialSync); AttachmentSync.java:74-77 BlockEntity/Chunk/Entity/Level targets; Entity.java:131, Level.java:79, ChunkAccess.java:60, LevelChunk.java:53 implement the holder; changelog 21.1.197 'Syncable Data Attachments'; diff of AttachmentSync.java between the 21.1.262 decompile and neoforge-21.1.264-sources.jar (sha1 cf05631a… matches maven): 264 adds 'type.syncHandler.sendToPlayer(holder, to)' to the initial-sync filter |
| corrected | NeoForge 21.1 includes a JSON keyframe-animation loader (assets/<ns>/neoforge/animations/entity) with HierarchicalModel#animate(AnimationState, AnimationHolder, ...) overloads; Blockbench's 'Animation to JSON Converter' plugin exports this format (named in PR #1476). | The loader and overloads are confirmed (21.1.42, namespaced in 21.1.44). PR #1476 does not name the plugin. Its whole body is 'There is an existing Blockbench plugin to export in this format.' The plugin is 'Animation to JSON Converter' by Gaming32, who also wrote the PR. In the Blockbench plugin registry it is described as 'Converts Blockbench animations to the JSON format for the NeoForge mod loader'. | mc-src-1.21.1/net/neoforged/neoforge/client/entity/animation/json/AnimationLoader.java:38 (directory 'neoforge/animations/entity'); net/minecraft/client/model/HierarchicalModel.java:51,61,70,78 (AnimationHolder overloads); https://api.github.com/repos/neoforged/NeoForge/pulls/1476 (body, author Gaming32, merged 2024-09-05); https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins.json entry animation_to_json; changelog 21.1.42/21.1.44 |
| confirmed | NeoForge 21.1 provides a generic ConfigurationScreen for ModConfigSpec registered via IConfigScreenFactory (config UI since 21.0.110); FML 4.0.45 config types are COMMON, CLIENT, SERVER, STARTUP. |  | mc-src-1.21.1/net/neoforged/neoforge/client/gui/ConfigurationScreen.java and IConfigScreenFactory.java; MDK ExampleModClient.java:22 registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new); changelog '21.0.110-beta Added configuration screen (#1199)'; javap of net.neoforged.fml.config.ModConfig$Type in loader-4.0.45.jar (sha1 4a0968c2… matches maven): COMMON, CLIENT, SERVER, STARTUP; neoforge module: night-config 3.8.3 |
| confirmed | Iris (1.21.1 branch) ignores custom mod shader programs while a shader pack is active (allowUnknownShaders defaults to false), officially advises mods to avoid custom shaders or provide fallbacks; vanilla GameRenderer shader getters are remapped to pack programs. |  | https://github.com/IrisShaders/Iris/blob/1.21.1/docs/development/compatibility/core-shaders.md (both quotes verbatim); Iris 1.21.1 common/.../mixin/MixinShaderInstance.java iris$shouldSkipThis(): when allowUnknownShaders is false, every shader that is not an ExtendedShader or FallbackShader is skipped while shouldOverrideShaders(), and onTail then calls DepthColorStorage.disableDepthColor(); config/IrisConfig.java:59 allowUnknownShaders = false; mixin/MixinGameRenderer.java HEAD-cancellable injects into getPositionColorShader, getRendertypeEntityTranslucentShader, getRendertypeLinesShader and others |
| confirmed | Sodium 0.8.13 for 1.21.1 (2026-08-28) supports NeoForge's AddSectionGeometryEvent, dispatches RenderLevelStageEvent block stages, and culls entities using getBoundingBoxForCulling against visible sections. | Confirmed. Addition: Sodium's cull wraps the frustum call inside vanilla EntityRenderer#shouldRender, and that call is only reached when !noCulling. So noCulling entities bypass Sodium's section culling too. | https://github.com/CaffeineMC/sodium/releases/tag/mc1.21.1-0.8.13 (published 2026-08-28); neoforge/.../NeoForgeLevelRenderHooks.java (ClientHooks.dispatchRenderStage, gatherAdditionalRenderers, AddSectionGeometryEvent.SectionRenderingContext); common/.../client/render/SodiumWorldRenderer.java:488-513 (MAX_ENTITY_CHECK_VOLUME = 16*16*16*15, skips glowing/name-shown); mixin/features/render/entity/cull/EntityRendererMixin.java (@WrapOperation on Frustum.isVisible in shouldRender); mc-src EntityRenderer.java:52 (noCulling short-circuit) |
| confirmed | On Forge 1.21.1 there are no builds of Sodium, Iris, Lithium, FerriteCore, ModernFix, C2ME, Flywheel, Veil, EMI, Jade, Patchouli or GuideME; NeoForge has all of them; Fabric has all except GuideME and LDLib2. | Confirmed. Add that Forge 1.21.1 does have a shader option, OptiFine HD U J1 (2024-09-24, Forge 52.0.16). Forge is latest 52.1.20 (2026-10-15), recommended 52.1.0. | Modrinth API v2 version queries (game_versions=['1.21.1'], loaders forge/neoforge/fabric) re-run 2026-10-20: forge 'none' for all twelve, neoforge builds for all twelve, fabric none only for guideme and ldlib; CurseForge via api.cfwidget.com: 0 Forge+1.21.1 files for sodium, lithium, modernfix, c2me, emi, jade, patchouli, guideme; https://maven.createmod.net/dev/engine-room/flywheel/flywheel-forge-1.21.1/maven-metadata.xml -> 404; https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json; https://optifine.net/downloads |
| corrected | Iris is the only shader mod on 1.21.1 (report §0.2). | This holds only for NeoForge and Fabric. Oculus has no 1.21.1 build. OptiFine HD U J1 exists for 1.21.1, but only for vanilla and Forge 52.0.16. | https://optifine.net/downloads (HD U J1, 24.09.2024, Forge 52.0.16); Modrinth oculus project: no 1.21.1 versions |
| refuted | Compat profile pairs Sodium mc1.21.1-0.8.13 with Iris 1.8.12 (report §5, recommendation 9). | Iris 1.8.12 targets Sodium 0.6.x and does not work with Sodium 0.8.x. Use Iris 1.8.14-beta.1+1.21.1 (beta, 2026-06-13, the only 1.21.1 Iris build for Sodium 0.8) with Sodium 0.8.13. Alternatively, keep Iris 1.8.12 and drop to Sodium mc1.21.1-0.6.13 (2025-04-04). | https://github.com/CaffeineMC/sodium/releases/tag/mc1.21.1-0.8.12 ('Mod compatibility: … Iris works as of 1.8.13'); Modrinth version t3ruzodq (Iris 1.8.12) depends on Sodium version Pb3OXVqC = mc1.21.1-0.6.13-neoforge; the iris-neoforge-1.8.12+mc1.21.1.jar mods.toml (sha512 matches Modrinth) declares sodium versionRange '[0.6,)' with reason 'Please install Sodium 0.6 for Iris to work.' and an Embeddium message 'Please use Sodium 0.6 instead'; Modrinth version KduFYu4t (1.8.14-beta.1) changelog 'Updates to Sodium 0.8, and fixes a few old bugs.'; Iris NeoForge 1.21.1 versions on Modrinth: …1.8.8, 1.8.12, 1.8.14-beta.1 (no 1.8.13) |
| confirmed | Flywheel 1.0.7-48 for NeoForge/Fabric 1.21.1 (2026-09-20, MIT) is only on maven.createmod.net and must be jar-in-jar'd; its instancing and indirect backends are disabled when a shader pack is in use; the indirect backend needs OpenGL 4.6 or an ARB set unavailable on macOS (GL 4.1). | Confirmed. Added detail: players can also get Flywheel bundled in Engine Room's Vanillin (Modrinth flw-vanillin 1.1.3, 2025-04-21, MIT), which jar-in-jars Flywheel 1.0.4. Create 6 pins Flywheel 1.0.6 with strictly [1.0.6,2.0). | https://maven.createmod.net/dev/engine-room/flywheel/flywheel-neoforge-1.21.1/maven-metadata.xml (latest 1.0.7-48, lastUpdated 20260920165152; fabric artifact same; jar 874,794 bytes); Flywheel 1.21.1/dev common/src/backend/.../Backends.java (supported only if !ShadersModHelper.isShaderPackInUse()), gl/GlCompat.java:145-173 (OpenGL33 + ARB_shader_bit_encoding for instancing; OpenGL46 or the ARB list for indirect); LICENSE.md is MIT text; vanillin-neoforge-1.21.1-1.1.3.jar (sha512 matches Modrinth) META-INF/jarjar/metadata.json -> flywheel-neoforge-1.21.1-1.0.4; Create mc1.21.1/dev build.gradle:133-136 and gradle.properties flywheel_version = 1.0.6 |
| confirmed | Flywheel docs: 'All instances are deleted and all visuals are recreated when the render origin updates' (hitch risk for a fast-panning RTS camera). | The quote is confirmed. Added detail: the origin only moves when the camera gets more than 256 blocks from it (both backends construct EngineImpl with maxOriginDistance 256). So the cost is one rebuild per 256 blocks of camera travel, not a continuous cost. | https://github.com/Engine-Room/Flywheel/blob/1.21.1/dev/docs/flywheel/api/concepts.md lines 57-59; Backends.java engineFactory(... , 256); backend/engine/EngineImpl.java:38-73 (sqrMaxOriginDistance check in updateRenderOrigin) |
| corrected | GeckoLib 4.9.3 (2026-09-16, MIT) is the 1.21.1 line; Minecraft 1.21.5+ uses GeckoLib 5.x (5.1.0, 2025-06-16); GeckoLib renders its own quads, so Sodium's ModelPart/Cube fast path does not apply. | 4.9.3 and the render path are confirmed. GeckoLib 5.x did not start with 5.1.0. 5.0-alpha1 came on 2025-04-08 and 5.0 on 2025-04-21 for MC 1.21.5. 5.1.0 (2025-06-16) was a later 1.21.5 release. | https://api.modrinth.com/v2/project/geckolib/version?loaders=['neoforge'] (5.0-alpha1 2025-04-08, 5.0 2025-04-21, 5.0.1, 5.0.2, 5.1.0 2025-06-16, all ['1.21.5']); 1.21.1 versions 4.9.3 2026-09-16 (0.63 MB, MIT); geckolib 1.21.1 branch common/.../renderer/GeoRenderer.java:312 renderCube -> :328 createVerticesOfQuad -> :341 buffer.addVertex; Sodium ModelPartMixin (@Overwrite translateAndRotate) and CubeMixin (ModelPart.Cube compile) |
| confirmed | GeckoLib registers its NeoForge payloads as optional(). |  | geckolib 1.21.1 neoforge/src/main/java/software/bernie/geckolib/network/GeckoLibNetworkingNeoForge.java:29 event.registrar(MODID).optional() |
| confirmed | owo-lib on NeoForge 1.21.1 is still beta (0.12.15.6-beta.1+1.21, 2026-10-16; beta since 2024-10-22); earlier builds depended on Forgified Fabric API. | Confirmed. Earlier builds embedded Forgified Fabric API (Modrinth dependency type 'embedded'). The 2026-10-16 beta lists no dependencies, and its changelog fixes an 'issue with latest neoforge update due to changes to internal network code'. That fix shows the 21.1.260–262 hardening broke a real mod. | https://api.modrinth.com/v2/project/owo-lib/version (NeoForge 1.21.1: 13 builds, all beta, first 0.12.15-beta.4+1.21 on 2024-10-22; dependency Aqlf1Shp = forgified-fabric-api 'embedded' from beta.5 to 0.12.15.5-beta.1; version wbdHEItB changelog) |
| confirmed | MC 1.21.1 dedicated server bundles fastutil 8.5.12, JOML 1.10.5, Guava 32.1.2-jre, Gson 2.10.1, DFU 8.0.16, Brigadier 1.3.10, Netty 4.1.97.Final, slf4j-api 2.0.9, log4j 2.22.1, commons-lang3 3.14.0, commons-io 2.15.1; NeoForge pins these with Gradle 'strictly'. | Confirmed. The pinning module net.neoforged:minecraft-dependencies:1.21.1 is served from https://maven.neoforged.net/mojang-meta/ (not releases/). DFU is hosted on libraries.minecraft.net, not Maven Central. | server.jar sha1 59353fb4… matches the piston-meta 1.21.1.json (sha1 1757c8b1…); its META-INF/libraries.list lists exactly these versions plus oshi 6.4.10, jna 5.14.0, jopt-simple 5.0.4, authlib 6.0.54, logging 1.2.7, lz4 1.8.0; https://maven.neoforged.net/mojang-meta/net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.module: {strictly: '8.5.12'} etc. in client/server compile and runtime variants; neoform-1.21.1-20240808.144430.module depends on it; version JSON: datafixerupper URL libraries.minecraft.net; repo1.maven.org DFU 8.0.16 pom -> 404 |
| confirmed | Because NeoForge declares them strictly, a core that asks for a newer fastutil or Guava will fail dependency resolution in the mod project. | Confirmed when the core exposes the dependency (api/implementation). A compileOnly dependency is not exported, so it would not conflict. | https://docs.gradle.org/current/userguide/dependency_versions.html ('For transitive dependencies, if no acceptable version is found, dependency resolution will fail'; 'A strict version cannot be upgraded') |
| confirmed | Candidate Java ECS/AI libraries are dormant: Dominion ECS 0.9.0 (2023-07-16), Artemis-odb 2.3.0 (2019-11-09), Ashley 1.7.4 (2021-02-05, depends on libGDX), gdx-ai 1.8.2 (2019-07-26, depends on libGDX). |  | Maven Central maven-metadata.xml / POM Last-Modified: dominion-ecs-engine 0.9.0 2023-07-16 (jar 157,982 B), artemis-odb 2.3.0 2019-11-09, ashley 1.7.4 2021-02-05 (compile gdx 1.9.9), gdx-ai 1.8.2 2019-07-26 (compile gdx 1.9.8); GitHub: dominion-ecs-java MIT pushed 2024-03-12, artemis-odb BSD-2-Clause pushed 2023-04-14, ashley Apache-2.0, gdx-ai Apache-2.0 |
| confirmed | Vanilla ServerLevel#sendBlockUpdated iterates all navigating mobs and synchronously recomputes affected paths on every collision-shape change; PathNavigation runs A* on the server thread with maxVisitedNodes = floor(FOLLOW_RANGE*16). | Confirmed, with three details. navigatingMobs holds every loaded Mob (added on tracking start). Only mobs whose current path passes near the block are recomputed. Each mob recomputes at most once per 20 ticks, and later changes set hasDelayedRecomputation and are handled on its next navigation tick. Lithium's entity.inactive_navigations mixin narrows the scan. | mc-src ServerLevel.java:1080-1110 (loop over navigatingMobs, shouldRecomputePath, recomputePath), :193 and :1781 (every Mob added in onTrackingStart); PathNavigation.java:65 (floor(FOLLOW_RANGE*16)), :91-101 (20-tick throttle), :168 (PathNavigationRegion), :424-435 (shouldRecomputePath); Lithium mc1.21.1-0.15.4 mixin/entity/inactive_navigations/package-info.java |
| refuted | Vanilla entity tracking re-evaluates Entity#broadcastToPlayer only when the entity or a player changes chunk section (ChunkMap#tick), so server-side fog-of-war hiding needs a forced re-check (AT/mixin on ChunkMap.TrackedEntity). | Vanilla re-checks visibility in two cases. ChunkMap#tick re-checks one entity against all players when that entity changes section. ChunkMap#move(player) re-checks every tracked entity against one player each time that player's movement packet is processed. The vanilla client sends a position packet at least every 20 ticks, so a player is re-checked at least about once a second, and every tick while moving. A forced re-check is still advisable for prompt reveal/hide. Per player it needs no AT: the public ServerChunkCache#move(ServerPlayer) re-runs TrackedEntity#updatePlayer for every tracked entity. Re-checking one entity against all players still needs the AT/mixin on ChunkMap#entityMap and TrackedEntity#updatePlayers. | mc-src ChunkMap.java:1024-1031 (move: updatePlayer(player) for every TrackedEntity), :1151-1177 (tick: section-change path), :1327-1345 (updatePlayer calls broadcastToPlayer); ServerChunkCache.java:464-467 (public move); ServerGamePacketListenerImpl.java:970 (handleMovePlayer calls getChunkSource().move(player)); LocalPlayer.java:283-284 (positionReminder >= 20 forces a position packet) |
| confirmed | Vanilla gamerule maxEntityCramming defaults to 24; LivingEntity#pushEntities does a per-entity AABB query each tick and deals cramming damage above the limit. | Confirmed. The damage is 6.0, applied with a 1-in-4 random chance per tick once 24 or more other non-passenger pushable entities overlap. | mc-src GameRules.java:98-100 (IntegerValue.create(24)); LivingEntity.java:2891-2915 (getEntities over bounding box, random.nextInt(4) == 0, hurt(cramming(), 6.0F)), called each tick from aiStep (:2848) |
| confirmed | junit-fml 4.0.45 is built against JUnit BOM 5.10.2 and jqwik 1.10.2 depends on JUnit Platform 1.14.4, so JUnit 5.14.4 (not 6.1.3) is the consistent choice. |  | https://maven.neoforged.net/releases/net/neoforged/fancymodloader/junit-fml/4.0.45/junit-fml-4.0.45.module (runtimeElements: org.junit:junit-bom requires 5.10.2, junit-platform-launcher); Maven Central jqwik-engine-1.10.2.pom (junit-platform-commons 1.14.4 compile, junit-platform-engine 1.14.4 runtime); junit-bom 5.14.4 Last-Modified 2026-04-26, 6.1.3 2026-08-07 |
| corrected | me.champeau.jmh's latest release is 0.7.3; Gradle 9 support was merged on 2026-07-19 but not released (CI tested on Gradle 8.10). | The release status is confirmed: 0.7.3 is on the Plugin Portal since 2025-01-30 and on GitHub since 2025-04-16, and issue #282 was closed by PR #288, merged 2026-07-19 and unreleased. The CI detail is wrong. Functional tests at the 0.7.3 tag run on Gradle 7.0, 8.0 and the wrapper version 8.12, not 8.10. | https://plugins.gradle.org/m2/me/champeau/jmh/me.champeau.jmh.gradle.plugin/maven-metadata.xml (latest 0.7.3); GitHub API: issue melix/jmh-gradle-plugin#282 closed 2026-07-19 by PR #288 (merged 2026-07-19T10:43:17Z); releases list (RELEASE_0_7_3 2025-04-16 is latest); RELEASE_0_7_3 src/funcTest/.../AbstractFuncSpec.groovy:27-31 and gradle-wrapper.properties (gradle-8.12) |
| confirmed | Error Prone (2.50.0) must run on JDK 21+; NullAway's JSpecify mode needs javac 22+ or OpenJDK 21.0.8+ with -XDaddTypeAnnotationsToSymbol=true, while standard mode already honors JSpecify annotations. |  | https://errorprone.info/docs/installation ('Please note that Error Prone must be run on JDK 21 or newer.'); https://github.com/uber/NullAway/wiki/JSpecify-Support (JDK 22+ javac, or the flag on OpenJDK 21.0.8+, not Oracle JDK; 'Out of the box, NullAway supports JSpecify annotations in its standard mode'); Maven Central error_prone_core 2.50.0 2026-06-10, nullaway 0.14.2 2026-09-25 |
| confirmed | RenderDoc supports Windows and Linux only (no macOS), per its FAQ. | The substance is confirmed, but the cited source is wrong. The FAQ lists no platforms. The RenderDoc homepage says 'RenderDoc supports Windows, Linux, and Android for capture and replay out of the box', with no macOS. | https://renderdoc.org/ (Platform Support section); https://renderdoc.org/docs/getting_started/faq.html (no platform list) |
| confirmed | macOS caps OpenGL at 4.1, so on the owner's M4 Pro only Flywheel's instancing backend can run. |  | The owner's own Minecraft log (~/Library/Application Support/minecraft/logs, 2026-08-01) reports 'OpenGL Renderer: Apple M4 Pro' and 'OpenGL Version: 4.1 Metal - 90.5'; Flywheel GlCompat.java:145-165 (instancing needs GL 3.3, indirect needs GL 4.6 or the ARB list) |
| confirmed | All C2ME NeoForge 1.21.1 builds are alpha (latest 0.4.0-alpha.0.123+1.21.1, 2026-10-11); C2ME flags off-thread misuse of world random via CheckedThreadLocalRandom. |  | Modrinth c2me-neoforge versions (72 builds, none release-type; latest 2026-10-11, MIT); project body: 'C2ME includes CheckedThreadLocalRandom for world random … These detections exist to prevent mods from screwing up Minecraft internals' |
| confirmed | Baritone is LGPL-3.0 ('with anime exception', v1.11.3 for 1.21.1) and MineColonies is GPL-3.0; both are references only. |  | GitHub cabaletta/baritone: licence LGPL-3.0, README badge 'license-LGPL--3.0 with anime exception', release v1.11.3 2026-09-01 'For Minecraft 1.21 and 1.21.1'; ldtteam/minecolonies GPL-3.0, pushed 2026-10-13; version/main Pathfinding.java: ThreadPoolExecutor(1, 1, …) with threads 'Minecolonies Pathfinding Worker #n'; AbstractPathJob.java:190 builds a ChunkCache |
| confirmed | Fabric API 0.116.17+1.21.1 (2026-09-01) includes data-attachment syncing (AttachmentRegistry.Builder#syncWith), so Cardinal Components API is optional on Fabric. |  | https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/fabric-api-0.116.17+1.21.1.pom (fabric-data-attachment-api-v1 1.4.7+5b36e0f719); fabric-api 1.21.1 branch AttachmentRegistry.java:160 syncWith(PacketCodec<? super RegistryByteBuf, A>, AttachmentSyncPredicate); Modrinth fabric-api 0.116.17+1.21.1 2026-09-01; cardinal-components-api 6.1.3 2025-11-22 MIT 0.23 MB |
| confirmed | Embeddium's last 1.21.1 build is 1.0.15 (2025-01-24) and Iris declares it incompatible ('Please use Sodium 0.8 instead'); Oculus has no 1.21.1 build. | Confirmed for the 1.21.1 branch and 1.8.14-beta.1. The 1.8.12 release jar still says 'Please use Sodium 0.6 instead'. | Modrinth embeddium 1.0.15+mc1.21.1 2025-01-24; oculus: no 1.21.1 versions; Iris 1.21.1 branch neoforge/src/main/resources/META-INF/neoforge.mods.toml and iris-neoforge-1.8.14-beta.1 jar (Embeddium incompatible, 'Please use Sodium 0.8 instead.'); iris-neoforge-1.8.12 jar ('Please use Sodium 0.6 instead.') |
| corrected | JEI 19.69.0.457 (2026-10-20), 7.4 MB, is the NeoForge 1.21.1 version. | 19.69.0.457 is a beta on Modrinth. The latest release is 19.64.0.455 (2026-10-16, 2.38 MB), and 7.4 MB is the size of the beta jar. The API artifacts on maven.blamejared.com are confirmed. | Modrinth jei versions for neoforge 1.21.1 (19.69.0.457 [beta] 2026-10-20 7.35 MB; 19.69.0.456 [beta]; 19.64.0.455 [release] 2026-10-16 2.38 MB); https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge-api/maven-metadata.xml |
| corrected | imgui-java 1.92.7.1 is built on LWJGL 3.4.1 and lwjgl-sdl, so a dev-only ImGui overlay on MC 1.21.1 (LWJGL 3.3.3) needs an older binding or Veil's bundled copy. | Only imgui-java's LWJGL backend modules (imgui-java-lwjgl3, imgui-java-app) depend on LWJGL 3.4.1 + lwjgl-sdl. imgui-java-binding has no LWJGL dependency. Veil 4.5.2 does not bundle ImGui; it declares ImGuiMC [2.0.0,3.0.0) as an optional dependency. ImGuiMC 2.0.5+mc1.21.1 (FoundryMC, 2026-08-10, LGPL-3.0-only, 10.7 MB) jar-in-jars imgui-java-binding 1.92.7.1 + natives and renders through its own Minecraft-side renderer. imgui-java 1.86.11 (2023-09-29) is the last whose lwjgl3 backend targets LWJGL 3.3.3. | Maven Central imgui-java-lwjgl3-1.92.7.1.pom (lwjgl-bom 3.4.1, lwjgl-sdl), imgui-java-binding-1.92.7.1.pom (no dependencies, MIT), imgui-java-lwjgl3 1.86.11 pom (LWJGL 3.3.3), 1.87.7–1.89.0 (3.3.4), 1.90.0 (3.3.6); veil-neoforge-1.21.1-4.5.2.jar (sha512 matches Modrinth) jarjar holds only molang-compiler and glsl-processor, mods.toml modId imguimc optional [2.0.0,3.0.0); imguimc-neoforge-2.0.5+mc1.21.1.jar (sha512 matches Modrinth) META-INF/jarjar: imgui-java-binding and natives 1.92.7.1, own renderer in foundry/imgui/impl/renderer |
| confirmed | Lithium mc1.21.1-0.15.4 has mixins in ai.pathing, entity.collisions and entity.inactive_navigations, with mod-side overrides via lithium:options. | Confirmed. Detail: ai.pathing is disabled by default in 0.15.4 (enabled = false). entity.inactive_navigations ('Block updates skip notifying mobs that won't react to the block update anyways') mitigates the navigatingMobs scan. | GitHub CaffeineMC/lithium tag mc1.21.1-0.15.4 tree (common/.../mixin/ai/pathing, entity/collisions, entity/inactive_navigations); ai/pathing/package-info.java (enabled = false); neoforge/.../NeoForgeMixinOverrides.java:13 'lithium:options' read via getOwningFile().getConfigElement |
| confirmed | Sodium reads per-mod option overrides from mod metadata (sodium:options). |  | Sodium mc1.21.1-0.8.13 neoforge/.../ForgeMixinOverrides.java (JSON_KEY_SODIUM_OPTIONS = 'sodium:options', read from ModInfo.getConfigElement); Iris's own mods.toml uses [mods.'sodium:options'] |
| confirmed | Entity Culling 1.11.3: async occlusion culling plus client tick culling (tickCulling=true by default), respects noCulling, does not cull hitboxes over 50 blocks, API addDynamicEntityWhitelist(Function<Entity,Boolean>), config whitelists, 128-block tracing distance; tr7zw Protective License (no commercial use). |  | tr7zw/EntityCulling main: EntityCulling-Versionless/.../Config.java:30-37 (tracingDistance = 128, hitboxLimit = 50, tickCulling = true, whitelists); CullTask.java:110-119; EntityCullingModBase.java:274 addDynamicEntityWhitelist; mixin/ClientWorldMixin.java:44-50 and EntityRendererMixin.java:80 (entity.noCulling on <= 1.21.1); LICENSE-EntityCulling ('may not be used to get a) a commercial advantage, or b) monetary compensation'); Modrinth 1.11.3 2026-10-08 id eieYbWYb |
| confirmed | ModernFix: dynamic_entity_renderers ('Some mods are not compatible… EntityRenderer crashes') and dynamic_resources are opt-in (default false); spark_profile_launch and spark_profile_world_join options exist. |  | embeddedt/ModernFix 1.21.1 src/main/java/.../core/config/ModernFixEarlyConfig.java:247-268 (DEFAULT_SETTING_OVERRIDES false for these); assets/modernfix/lang/en_us.json option descriptions; Modrinth 5.27.26+mc1.21.1 2026-10-08 |
| confirmed | Modrinth Maven coordinates in the compat profile resolve, and the version ID is needed where loaders share a version number (Entity Culling eieYbWYb). |  | HEAD https://api.modrinth.com/maven/maven/modrinth/<slug>/<ver>/<slug>-<ver>.jar -> 307 to the NeoForge 1.21.1 file for spark, sodium, iris (1.8.12 and 1.8.14-beta.1), lithium, modernfix, ferrite-core, entityculling:eieYbWYb, immediatelyfast; entityculling:1.11.3 resolves to entityculling-fabric-1.11.3-mc1.19.4.jar |
| corrected | JourneyMap 1.21.1-6.0.9 with its public API repo; Xaero's 26.6.0, all rights reserved. | Versions are confirmed, but JourneyMap is all rights reserved too. Only its API (TeamJM/journeymap-api) is a public repo. | Modrinth journeymap license LicenseRef-All-Rights-Reserved, 1.21.1-6.0.9 2026-09-19; xaeros-minimap LicenseRef-All-Rights-Reserved, neoforge-1.21.1-26.6.0 2026-10-05; GitHub TeamJM/journeymap-api (default branch 1.21.1_2.0.0) |
| confirmed | HumanoidModel extends AgeableListModel (not HierarchicalModel); KeyframeAnimations.animate takes HierarchicalModel<?>; ItemInHandLayer needs ArmedModel, HumanoidArmorLayer needs HumanoidModel, CustomHeadLayer needs HeadedModel. |  | mc-src HumanoidModel.java:22; PlayerModel.java:23; KeyframeAnimations.java:15; layers/ItemInHandLayer.java:18; HumanoidArmorLayer.java:32; CustomHeadLayer.java:33; HumanoidModel.ArmPose enum :471 |
| confirmed | Toolchain versions: JUnit 6.1.3 (2026-08-07) / 5.14.4 (2026-04-26), AssertJ 3.27.7 (2026-01-24, 4.0.0-M1 milestone), jqwik 1.10.2 (2026-10-10), Mockito 5.24.0 (2026-09-23), JMH 1.37 (2023-08-03), Spotless 8.10.4 (2026-10-08), palantir-java-format 2.102.0 (2026-10-05), google-java-format 1.37.0 (2026-10-01), net.ltgt.errorprone 5.1.1 (2026-08-25), net.ltgt.nullaway 3.3.0 (2026-10-05), JSpecify 1.0.1 (2026-07-29), ArchUnit 1.5.1 (2026-09-25), MDG 2.0.148 (2026-09-29); upstream fastutil 8.5.19, JOML 1.10.9, Guava 33.7.2. |  | Maven Central and plugins.gradle.org maven-metadata.xml latest/release values and POM Last-Modified headers re-queried 2026-10-20 (all match); palantir-java-format README 'A modern, lambda-friendly, 120 character Java formatter' |
| confirmed | Mockito: configure as a -javaagent on JDK 21 (labelled Assessment). | This is not just an assessment. Mockito documents it in section 0.3, 'Explicitly setting up instrumentation for inline mocking (Java 21+)'. | https://raw.githubusercontent.com/mockito/mockito/v5.24.0/mockito-core/src/main/java/org/mockito/Mockito.java lines 195-311 |
| confirmed | Library table versions/licences/sizes for UI, config, guide and compat mods (owo-lib, LDLib2 7.6 MB, ModernUI 25 MB, YACL 3.8.2, Cloth 15.0.140 (2024-09-16), FCAP v21.1.6, Fzzy 0.7.7 TDL-M, Patchouli 1.21.1-93 CC-BY-NC-SA-3.0, Modonomicon 1.21.1-1.120.7, GuideME 21.1.19 9.3 MB LGPL-3.0, Lavender 0.1.15, EMI 1.1.24, REI 16.0.799 beta, Jade 15.10.6, WTHIT 12.10.2 beta, AzureLib 3.1.22/3.1.23-alpha.2 49 stars MIT+MPL, playerAnimator 2.0.4 beta, Veil 4.5.2, Lodestone 1.8.2, Satin 2.0.0, spark 1.10.124, FerriteCore 7.0.3, ImmediatelyFast 1.6.15, DH 3.3.3 28 MB, Moonrise beta.15, Krypton 0.2.8). | All confirmed. Details: AzureLib 3.1.23-alpha.2 is tagged release on Modrinth. GuideME's 'MIT/Apache bundled libs' are a shaded Apache Lucene plus micromark/mdast ports. Radium 0.13.1 is a beta. Moonrise is GPL-3.0-only and Krypton LGPL-3.0-only. | Modrinth API v2 project + version queries re-run 2026-10-20 (fresh/modrinth-a.txt, modrinth-b.txt in the scratchpad); GitHub licences (AzureLib LICENSE MIT + LICENSE_SBLForked MPL-2.0, 49 stars; Patchouli LICENSE CC BY-NC-SA 3.0; GuideME LICENSE.md LGPL-3.0); guideme-21.1.19.jar contents (guideme/internal/shaded/lucene, META-INF/LICENSE.txt Apache-2.0); Lavender requires owo-lib, REI requires architectury-api + cloth-config, WTHIT requires badpackets |
| confirmed | Ponder 1.0.87+mc1.21.1 (2026-06-20, maven.createmod.net; runtime dependency on Flywheel), MIT, 0.82 MB. |  | https://maven.createmod.net/net/createmod/ponder/ponder-neoforge/1.0.87+mc1.21.1/ (jar Last-Modified 2026-06-20, Content-Length 822,408; POM runtime dependency flywheel-neoforge-1.21.1 1.0.4); GitHub Creators-of-Create/Ponder licence MIT |
| confirmed | Registrate MC1.21.1-1.4.8 (2026-06-12, maven.gegy.dev/releases), MPL-2.0, 0.18 MB; used by Create, which jar-in-jars it. | Confirmed. Detail: Create mc1.21.1/dev jar-in-jars Registrate MC1.21-1.3.0+67 from maven.ithundxr.dev, not 1.4.8. | https://maven.gegy.dev/releases/com/tterrag/registrate/Registrate/maven-metadata.xml (MC1.21.1-1.4.8; jar Last-Modified 2026-06-12); GitHub tterrag1098/Registrate MPL-2.0; Create build.gradle:130 jarJar(api(Registrate)), gradle.properties registrate_version = MC1.21-1.3.0+67 |
| confirmed | Iris recommends hashalite/Freecam (MIT) over Tweakeroo's freecam; Freecam's FreeCamera extends AbstractClientPlayer and is installed as the camera entity. |  | Iris 1.21.1 docs/usage/incompatible-mods.md (Tweakeroo section); hashalite/Freecam LICENSE (MIT); common/src/main/java/net/xolt/freecam/util/FreeCamera.java:36; Freecam.java:200,225 MC.setCameraEntity(freeCamera) |
| confirmed | IrisApi offers isShaderPackInUse() and isRenderingShadowPass(); API minor revision 2. |  | Iris 1.21.1 common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java:28,42,63; apiimpl/IrisApiV0Impl.java:22-23 (getMinorApiRevision returns 2) |
| confirmed | NeoForge runs on Mojang names in production since 1.20.2; MDG reobf is only for legacy Forge 1.20.1 and older; the MDK build has no reobf step. |  | https://neoforged.net/news/20.2release/ ('We are now using MojMaps everywhere. … allows debugging running modpacks'); https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md (legacy plugin for 1.17–1.20.1, 'Reobfuscating artifacts'); scratchpad mdk-build.log (:compileJava -> :jar -> :build, no reobf task) |
| confirmed | MDG README: jarJar with strictly/prefer; additionalRuntimeClasspath needed for non-mod libraries before MC 1.21.9; interfaceInjectionData is dev-time only; unitTest { enable(); testedMod = mods.x }; multi-source-set mods; testframework EphemeralTestServerProvider. | Confirmed. Detail: the README says interface injection must be made real at runtime with 'Mixins or Coremods', not only Mixins. | https://raw.githubusercontent.com/neoforged/ModDevGradle/main/README.md lines 261-271, 351-367, 449-484, 567-583, 381-399; testframework-21.1.264.jar (sha1 290771d0… matches maven) contains net/neoforged/testframework/junit/EphemeralTestServerProvider.class |
| confirmed | NeoForge AT docs: directives only modify the method they directly reference; the default META-INF/accesstransformer.cfg needs no declaration. |  | https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers ('Directives only modify the method they directly reference; any overriding methods will not be access-transformed.'); MDK neoforge.mods.toml template lines 56-59; MDG README line 519 |
| confirmed | RenderLevelStageEvent stages AFTER_SKY … AFTER_LEVEL; AddSectionGeometryEvent bakes static geometry into sections (every change rebuilds sections). |  | mc-src RenderLevelStageEvent.java:158-207; AddSectionGeometryEvent.java javadoc (fired when a section is queued for (re)building; trigger via LevelRenderer#setSectionDirty; renderers run off the main thread) |
| corrected | Selection highlight hooks IEntityExtension#hasCustomOutlineRendering(Player) and LevelRenderer#requestOutlineEffect() date from 21.1.51. | 21.1.51 (#1517) added requestOutlineEffect(). hasCustomOutlineRendering already existed before 21.1. | neoforge-21.1.264-changelog.txt line 459; mc-src IEntityExtension.java:386; LevelRenderer.java:2568-2574 ('Neo: Request outline effect post-processing …') |
| unverifiable | Avoid a custom post-processing pass for fog of war, 'which Iris will not run'; Veil/Lodestone/Satin are stacks 'that Iris will not render'. | The advice to avoid them stands, because Iris's docs advise against custom shader programs. That Iris will 'not run' them is unverified. Iris's skip logic covers only ShaderInstance. Its MixinPostChain is empty, so vanilla PostChain passes and programs outside ShaderInstance get no special handling. How they actually render under a pack would need a test. | Iris 1.21.1 common/.../mixin/state_tracking/MixinPostChain.java (empty class); MixinShaderInstance.java (ShaderInstance only); core-shaders.md conclusion |
| unverifiable | No GeckoLib per-entity cost figures are published; the only signals are third-party optimiser add-ons such as GeckoLib Animation Optimizer (1.20.1). | The add-on exists (Forge 1.20.1, CurseForge). A second one, GeckolibBetterFPS (1.20.1), claims about 25% faster rendering. That no per-entity figures exist at all is a negative claim and cannot be proven. | https://www.curseforge.com/minecraft/mc-mods/geckolib-animation-optimizer; https://modrinth.com/mod/geckolibbetterfps (via web search) |
| confirmed | Fabric Loader bundles Mixin/MixinExtras; Fabric API 0.116.17 has no AddSectionGeometryEvent equivalent; HudRenderCallback, WorldRenderEvents and fabric-gametest-api-v1 exist; no JSON entity-animation loader (Json Entity Animation stopped at 1.20.1). |  | fabric-loader-0.20.1.json (sponge-mixin 0.17.5+mixin.0.8.7) and fabric-loader-0.20.1.jar META-INF/jars/mixinextras-fabric-0.5.5.jar; fabric-api-0.116.17 POM (44 modules, none for section geometry); fabric-api 1.21.1 HudRenderCallback.java, WorldRenderEvents.java, FabricGameTest.java (HTTP 200); Modrinth json-entity-animation game versions 1.19.2–1.20.1 |
| confirmed | Vanilla mobs use clientTrackingRange 8–10 chunks and updateInterval 2–3; each tracked entity in ticking range calls ServerEntity#sendChanges() every server tick. |  | mc-src EntityType.java (Zombie/Skeleton/Pillager 8, Villager/Cow/IronGolem 10; builder defaults :1256-1257 range 5, interval 3; Allay interval 2); ChunkMap.java:1175-1177 (sendChanges when section changed or in entity ticking range) |
| confirmed | Attachments do not apply to ItemStack (data components instead); StreamCodec lives in Minecraft, not DFU. |  | mc-src ItemStack.java:95 (DataComponentHolder, no IAttachmentHolder); net/minecraft/network/codec/StreamCodec.java:14 |
