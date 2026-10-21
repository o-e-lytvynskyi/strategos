# Research digest

Research ran on 2026-10-20 as a multi-agent workflow. Six topic researchers read primary
sources (Maven metadata, GitHub, Modrinth, official docs) and the decompiled Minecraft 1.21.1
+ NeoForge 21.1.262 sources. An independent fact-checker then verified each report. A second,
independent pass of the same research and checks ran later that day. Both passes agree on
every decision. They differ only in details, and where they did, primary sources decided:
for example, the three NeoForge GitHub advisories of 2026-10-15 do exist, which the
second pass wrongly denied.

| Report | Canonical (first pass) | Second pass |
|---|---|---|
| Loader, toolchain, version strategy | [platforms.md](platforms.md) | [second-pass/platforms.md](second-pass/platforms.md) |
| Library mods and dev libraries | [libraries.md](libraries.md) | [second-pass/libraries.md](second-pass/libraries.md) |
| Prior art: RTS, army and colony mods; Minecraft Legends | [prior-art.md](prior-art.md) | [second-pass/prior-art.md](second-pass/prior-art.md) |
| 0 A.D. / AoE2 / AoE4 mechanics catalog | [mechanics.md](mechanics.md) | [second-pass/mechanics.md](second-pass/mechanics.md) |
| Minecraft 1.21.1 engine constraints | [engine.md](engine.md) | [second-pass/engine.md](second-pass/engine.md) |
| Testing, QA, dev workflow | [testing.md](testing.md) | [second-pass/testing.md](second-pass/testing.md) |
| Contradictions between reports | [contradictions.md](contradictions.md) | |
| Open research gaps and their disposition | [gaps.md](gaps.md) | |

Each report ends with its fact-check verdict table (claim, verdict, correction, evidence).

**Paths in these reports.** `scratchpad/…` and `/private/tmp/claude-501/…` were the research
session's temporary directory: the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 tree, the MDK
clone, a test server and build logs. They are not project files and are gone; never look for them.
The API reference is the project's own decompiled sources jar (strategos-dev skill §3.3), and the
MDK commit the toolchain check built is pinned in the M0 spec §4.5.

---

## 1. Owner decisions (2026-10-20)

| Topic | Decision |
|---|---|
| Version | Minecraft 1.21.1 |
| Match model | A bounded, pregenerated match arena first. RTS in an ordinary open world maybe later, so the architecture must not rule it out |
| First playable target | Solo against an AI opponent. A 2–4 player server is the next stage. Networking is server-authoritative from day one |
| Controls | RTS camera (box select, mouse orders) plus the ability to drop into first person |
| World and buildings | Hybrid. Trees and stone are real world blocks that workers cut and mine. Mines and fields are deposit objects with a remaining amount. Buildings rise block by block, but HP is abstract: blocks fall away at damage thresholds and return on repair |

Later decisions live in [../ROADMAP.md](../ROADMAP.md) ("Decisions needed from the owner"). On
2026-10-21 the owner took every decision needed by M6 as recommended, including 150 population per
player and AoE-style villagers. Civilisation themes, map generation, licence, multiplayer hosting
and the public release stay open until their milestones.

## 2. Settled tooling

See [../02-tooling.md](../02-tooling.md). In short:
- **Platform:** NeoForge ≥ 21.1.264 as the only loader; ModDevGradle 2.0.148, Gradle 9.2.1, Java 21 toolchain and daemon, Parchment 2024.11.17.
- **Dependencies:** none at runtime besides NeoForge.
- **Layout:** `build-logic`, `core` (pure Java), `mod`. The `core` classes are merged into the mod through MDG's `mods { sourceSet … }` and `jar.from(core output)`; no jarJar for first-party code, and no `module-info` in core.
- **Quality and tests:** Spotless with palantir-java-format, ErrorProne + NullAway (JSpecify mode), ArchUnit; JUnit 5.14.4 + AssertJ; no jqwik.

## 3. Engine facts the design must respect

All verified in the decompiled 1.21.1 sources unless marked otherwise. Numbers are from
[engine.md](engine.md).

**Server tick and entities**
1. One 50 ms server thread. Plan about 20 ms per tick for units. A vanilla-AI mob costs an estimated 20–30 µs per tick, and a stripped unit is targeted at about 10 µs. Both figures are unverified extrapolations, so M0 measures them.
2. `Mob#serverAiStep` is `final`, so the hook is `customServerAiStep()`. Navigation and the move, look and jump controls are replaceable objects.
3. Per-entity costs to switch off:
   - `checkDespawn` runs every tick: use `setPersistenceRequired()` and a no-op override;
   - `pushEntities` is O(n²) in clumps, and cramming damage starts at 24 neighbours: use `isPushable=false` and a no-op override;
   - `GameEvent.STEP` vibrations: return `NONE` from `getMovementEmission()`;
   - loot pickup: keep it off.
4. Invulnerability frames silently drop focused-fire damage. In `LivingIncomingDamageEvent`, set `getContainer().setPostAttackInvulnerabilityTicks(0)` for unit-on-unit damage.
5. `Attributes.STEP_HEIGHT` = 1.0 lets units walk up single blocks without jumping.
6. `ServerLevel#sendBlockUpdated` loops over every loaded `Mob` in `navigatingMobs`, so construction costs O(blocks placed × mobs). A `LivingEntity` base is not registered in `navigatingMobs`.
7. Queries typed by class (`getEntitiesOfClass`) are cheaper than untyped ones. Avoid multipart units: NeoForge scans every `PartEntity` on each query.

**Chunks and the world**

8. Entities tick only in entity-ticking chunks near players, and a level with no players stops ticking entities after 300 ticks. The exception is chunks held by `/forceload` or a NeoForge `TicketController`; custom vanilla ticket types don't count.
9. `TicketController.forceChunk(level, owner, x, z, add, ticking)`: with `ticking=false` the forced chunk already ticks its entities. `ticking=true` adds random ticks and natural mob spawning.
10. The vanilla world border is saved only for the overworld, and every other level follows it. It cannot bound several matches, or a per-match dimension. Match bounds are therefore ours.
11. Never touch `Level` or `ServerChunkCache` from a worker thread:
    - off-thread `getChunk` blocks on the server thread, and `getChunkNow` returns null;
    - the per-level `PathTypeCache` is not thread-safe;
    - snapshot data on the main thread instead, either with `PalettedContainer#copy()` or as our own grid.
12. Block-change hooks: a mixin on `LevelChunk#setBlockState` catches everything. `ServerLevel#onBlockStateChange` misses direct chunk writes and runs late during player placement. NeoForge's block events cover only player and entity actions.

**Networking and visibility**

13. Payloads are registered through `RegisterPayloadHandlersEvent` → `PayloadRegistrar.playToClient/playToServer/playBidirectional(type, StreamCodec, handler)`, with `.versioned()` and `.optional()`. Send them with the `PacketDistributor` helpers.
14. Tracking:
    - range is `clientTrackingRange × 16` blocks, measured from the **player entity**, not the camera;
    - updates go out every `updateInterval` ticks;
    - a teleport packet is sent on every `onGround` flip and every 400 updates;
    - a dirty `SynchedEntityData` value forces a position update that tick;
    - `setShouldReceiveVelocityUpdates(false)` saves about 12 B per update.
15. Bandwidth with velocity updates off is about 22 B per unit update at 6.7 Hz, so 300 moving units ≈ 44 KB/s per client. This is a computed upper bound.
16. Fog of war:
    - `Entity#broadcastToPlayer(ServerPlayer)` is part of the pairing test. Override it in our entities.
    - Vanilla only re-evaluates pairing when an entity changes section, on each movement packet from that player (at least every 20 ticks while that player's client sends them), and on add.
    - So a forced re-evaluation pass is needed: an access transformer on `ChunkMap.entityMap` / `TrackedEntity#updatePlayer`.
    - Never cancel `updatePlayer`, because that also blocks unpairing.
    - Scoreboard teams broadcast membership to every client and would leak units through the fog.
17. Attachment sync honours the per-player filter on initial sync only from **21.1.264**.

**Client**

18. Commander and camera:
    - chunks stream around the player entity, not the camera;
    - with a camera entity set, `LocalPlayer#isControlledCamera()` is false and `sendPosition` sends no movement packets, so streaming and the movement-packet fog re-check stall;
    - a spectator body brings conflicts: scroll changes flying speed, attacking makes the player spectate the target, and the hotbar turns into the spectator menu.
19. Cursor and input:
    - `MouseHandler#releaseMouse()` frees the cursor;
    - vanilla re-grabs it on the next in-world click unless the cancellable `InputEvent.MouseButton.Pre` is cancelled;
    - `setScreen(null)` also re-grabs it;
    - with a Screen open, `Minecraft#tick` skips `handleKeybinds`;
    - picking must normalise the cursor by `Window#getScreenWidth/Height`, because framebuffer pixels are 2× on Retina.
20. Projection: orthographic needs a mixin on `GameRenderer#getProjectionMatrix` and a bypass of the `Frustum#offsetToFullyIncludeCameraCube` loop, which otherwise freezes the game. A perspective camera at 50–60° pitch needs neither.
21. Rendering:
    - draw with vanilla `RenderType`s in `RenderLevelStageEvent`;
    - static terrain decals go through `AddSectionGeometryEvent`, and its renderers run on chunk-build threads;
    - selection outlines come from overriding `isCurrentlyGlowing`/`getTeamColor` client-side;
    - the HUD uses `RegisterGuiLayersEvent`.

    Sodium dispatches the chunk-layer events and section geometry; entity rendering stays vanilla. Under Iris, custom shaders are ignored, so keep a no-shader path.

**Data and persistence**

22. Datapack registries load only at world load, ignore `/reload`, and decode each file on its own, so they cannot resolve parents. A reload listener re-runs on `/reload` and lets us resolve inheritance, but needs its own sync payload. `OnDatapackSyncEvent` reaches a joining client before any entity pairing; on `/reload`, entities already exist when it arrives.
23. An `EntityType` — with its tracking range, update interval and renderer — is fixed at startup. Data-defined units must therefore map onto a few generic unit entity types, with the template id synced. A datapack can only add units whose models and textures the client already has.
24. SavedData on 1.21.1:
    - API: `new SavedData.Factory<>(ctor, load, null)`, saved through `level.getDataStorage().computeIfAbsent(...)`;
    - serialisation runs on the main thread, and NeoForge writes the file on an IO worker;
    - autosave runs every 6,000 ticks;
    - chunk attachments exist only while their chunk is loaded;
    - versioning: put a `"v"` field in every blob and migrate raw NBT before codec decoding.
25. Structure templates and progressive construction:
    - `StructureTemplate` palettes are private, so parse the `.nbt` into our own `Blueprint`;
    - place blocks with flags `2|16` and finish with a boundary shape pass;
    - budget placements per tick;
    - fire removes blocks without firing an event.

**GameTest (1.21.1)**

26. GameTest pitfalls and remedies:
    - tests in a batch run concurrently in **one** world, at a random origin within ±15 M blocks, so keep simulation coordinates match-local or `double`;
    - ticks run back to back with no sleep;
    - an empty test set or a mod-loading error very likely exits 0, so guard on a non-empty report;
    - no report file is written by default, so write our own JUnit XML;
    - the `attempts` and `requiredSuccesses` values are swapped in 1.21.1;
    - the namespace filter silently drops tests without a namespace;
    - structure blocks cap saved arenas at 48 per axis.
27. Match state must therefore be scoped to a match object with its own region, not to a level or to statics. Otherwise parallel in-world tests in one level collide.

## 4. Designs to borrow

**From 0 A.D.** ([mechanics.md](mechanics.md) §2), design only:
- **Templates:**
  - entity templates with `parent` chains and mixins;
  - merge operations (`add`, `mul`, `replace`, `disable`) and token lists that add or remove entries (`-Organic`);
  - code-defined filters that derive related kinds from one template: foundation, placement preview, fog ghost.
- **The modifier pipeline**, the most important single idea:
  - techs, auras, ranks, civ bonuses and AI handicaps all modify stats by value path, with add, multiply or replace, filtered by class tokens;
  - stats are read through the pipeline, with results cached and invalidated by messages.
- **UnitAI** as a hierarchical state machine with an order queue:
  - queue operations `Replace` (the default), `Add` (shift), `PushFront` (interrupts) and `Finish`;
  - top-level states: individual, formation controller, formation member.
- **Messaging and tick structure:**
  - a typed, synchronous message bus per match, kept separate from NeoForge's global event bus;
  - player commands applied at tick boundaries, with a fixed phase order;
  - async results delivered at the next boundary.
- **Determinism as a testing tool:**
  - a seeded RNG per match;
  - iteration in stable entity-id order;
  - no wall clock in game logic.
- **Pathfinding:**
  - a hierarchical layer of regions for reachability and for making unreachable goals reachable;
  - a long-range planner over navcells;
  - short-range avoidance.
- **The AI:** one central budget instead of per-queue accounts. Petra overshoots resources because of its queue accounts.

**From Reign of Nether** ([prior-art.md](prior-art.md) §3), design only:
- **The pathfinding pipeline:**
  - budgeted main-thread walkability classification and incremental reclassification of dirty regions;
  - immutable snapshots, a worker pool and request sequence numbers;
  - backpressure: a "busy" answer distinct from "no path";
  - chained segments, mobility classes and footprints, and an arrival settle distance.
- **Bounded maps** with caches sized from the real bounds.
- **Fog of war:**
  - a vision grid recomputed every 10 ticks;
  - fog rendered through biome tint, which works under shader packs;
  - a match-start terrain snapshot for rejoining players.
- **Building construction:** the AoE2 builder formula (`msPerBlock = 3·base/(builders+2)`) and NBT templates.
- **Observability:** an F7-style performance and path overlay, and tunables as gamerules.

**From MineColonies** ([prior-art.md](prior-art.md) §4), design only:
- **Data and AI:**
  - authoritative unit data kept separate from the entity, which is (re)spawned from it;
  - state machines with per-transition tick rates;
  - a stuck-unit escalation ladder.
- **Content:** phased blueprint placement; research defined as data.
- **AI attacks:** telegraphed, so players see them coming.

**UX lessons from Minecraft Legends** ([prior-art.md](prior-art.md) §8):
- **Command reach:** an overview command mode, with command range never tied to the avatar.
- **Selection:** individual, by type and by group, plus control groups.
- **Feedback:** show exactly where units will go before an order is confirmed.
- **Movement:** treat pathing over ramps and ledges as a UX feature.
- **The AI:** telegraph its attacks, and give its bases varied layouts.
- **Teaching and pausing:** teach by doing, and allow pause in singleplayer.

**Reusable code (MIT/Apache, with attribution):**
- **Camera:** i113w Camera Lib (camera entity, ortho mixin, frustum bypass, mouse ray casting), Freecam and Better Mine Team.
- **Pathing:** PathWeaver (vanilla A* on workers) and RVO2 (ORCA local avoidance, Apache-2.0).

## 5. Anti-patterns (observed in prior art)

Every one of these caused real bugs or lag in the mods studied:
- **Code structure:**
  - one vanilla mob subclass per unit type;
  - identifying players by name;
  - one giant action enum;
  - one hard-coded class per tech;
  - static global state.
- **Networking:** broadcast-first networking, then fog bolted on later.
- **Performance:**
  - per-tick world scans and Java streams in hot paths;
  - a single path thread, or vanilla synchronous pathing for armies;
  - reading live chunks off-thread.
- **Design:**
  - item logistics standing in for resource counters;
  - command only from third person;
  - command range limited to a radius around the avatar;
  - group-only orders;
  - identical AI bases.
- **Process:** shipping without automated tests.

## 6. Contradictions for the design phase to settle

Full text in [contradictions.md](contradictions.md). Each needs an explicit answer in the
architecture:

1. **Authority.** Do unit position, HP and orders belong to the Minecraft-free core, or to the entity? The answer fixes tick order, persistence and replication. It must work for a force-loaded arena now and stay possible for an open world later.
2. **Stats.** Core modifier pipeline, or vanilla Attributes, or both, with one source of truth?
3. **Content definitions.** Datapack registries, a reload listener with inheritance, or registries for ids plus reload data.
4. **Determinism.** Can async path results be delivered at a fixed tick (t+k), so seeded core replays stay deterministic? Where does determinism stop (vanilla physics)?
5. **Navigation representation.** 2.5D single-layer, layered columns, or a 3D band; A*/HPA* rather than JPS, given non-uniform costs.
6. **Fog refresh.** Forced re-evaluation cadence, and leaks the entity filter does not cover (chunks, sounds, particles, block-break progress).
7. **Commander camera and input.** Player-follows-camera or a camera entity; a cursor Screen or a released mouse; perspective or orthographic.
8. **Budgets.** The reports suggest "mod work ≤ 5 ms p50 / ≤ 10 ms p99 at 4 × 200 units" and "≈20 ms of the tick for units". These measure different things. Define both and measure them in M0.
9. **GameTest wiring.** A separate test mod, or source sets merged into the `strategos` mod group. Verify in M0.
10. **Licensing of prior art.** No GPL code. Clean-room only (section 8).

## 7. Spikes planned for M0

| Spike | Question | Runs where | Needs owner consent |
|---|---|---|---|
| S1 | Server cost per unit: stripped `PathfinderMob`, kinematic puppet and `LivingEntity` at 200/500/1,000 units, idle/moving/fighting | GameTest server (headless) | no |
| S2 | Cost of a force-loaded arena with no players: 24², 32² and 64² chunks (MSPT, heap, autosave) | GameTest server or a self-terminating dedicated server | no |
| S3 | Client scenario harness on macOS: focus behaviour, rendering while unfocused or covered, Retina framebuffer size, PNG repeatability | real client window | **yes** |
| S4 | Client frame time with 200/500/1,000 humanoid units, with and without Sodium | real client window | **yes** |
| S5 | Build wiring: core merged into the mod in dev and in the jar; gametest source set; JUnit XML report and empty-set guard; server smoke marker; configuration cache | Gradle | no |

This is the research's plan. The design refined it (S1 and S2 run on a self-terminating dedicated
server, and S2 covers 256, 576 and 1,024 chunks) and added S6–S12: the current questions and exit
criteria are in [04-architecture.md §19.2](../04-architecture.md), the schedule in the roadmap's
[spike table](../roadmap/M0-foundation.md#spikes).

## 8. Clean-room rule

Reign of Nether, MineColonies, Structurize, BlockUI and Ancient Warfare 2 are GPL-3.0.
Villager Recruits and its family are all-rights-reserved. 0 A.D.'s code and data are
GPL-2.0-or-later, and its art is CC BY-SA 3.0. An AI assistant that has such code in its
context can reproduce it. So:
1. Implementation sessions never open those repositories. The behavioural notes in these reports are the only input.
2. MIT and Apache-2.0 sources may be read and adapted, with their notices kept in `NOTICE`.
3. Never copy assets. Write our own text, and re-derive every number at Minecraft scale.
4. No "Age of Empires", "AoE" or "0 A.D." in the mod's name, logo or primary tags. Crediting them as inspiration in the README is fine.
