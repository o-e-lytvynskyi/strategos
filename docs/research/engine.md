# Strategos: Minecraft 1.21.1 engine constraints and techniques for an RTS with hundreds of units

*Research date 2026-10-20; fact-checked the same day (see "Verification notes" at the end). Target: Minecraft Java 1.21.1 with NeoForge 21.1.x, Mojang mappings. Written for the tech lead, who will choose the loader, libraries, architecture and roadmap.*

Markers used by the fact-check: **(corrected)** = the statement was wrong or incomplete and has been fixed in place; **(added)** = a fact found during verification that changes how the passage should be read; **(unverified)** = could not be confirmed from a primary source.

## 0. Method, evidence tags, version snapshot

Every claim carries one of these tags:

- **[src]**: I read it in source code. That means the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 tree (Mojang names, Parchment parameters) from the reference MDK build (`scratchpad/mc-src-1.21.1`), or a cloned upstream repository (commit date given).
- **[doc]**: official docs, changelogs, or the NeoForged porting primers ([primers repo](https://github.com/neoforged/.github/tree/main/primers)).
- **[bench]**: a third-party benchmark. All of them are self-reported by mod authors.
- **[inf]**: my own inference or estimate. Treat it as a hypothesis to measure.

Version snapshot (all time-sensitive; Modrinth v2 API and Maven queried 2026-10-20, re-queried by the fact-check):

| Component | State on 2026-10-20 | Licence |
|---|---|---|
| NeoForge for 1.21.1 | **21.1.264** (2026-10-19). The MDK pins 21.1.262 (2026-10-15). Still receiving backports: 21.1.263 (2026-10-18) and 21.1.264 (2026-10-19) are both backports ([Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml), [changelog](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt)) | LGPL-2.1 |
| NeoForge, newest line | 26.3.0.95 (2026-10-20) | LGPL-2.1 |
| Fabric API for 1.21.1 | 0.116.17 (2026-09-01) | Apache-2.0 |
| [Lithium](https://modrinth.com/mod/lithium), NeoForge 1.21.1 | 0.15.4 (2026-06-27) | LGPL-3.0 |
| [Moonrise](https://github.com/Tuinity/Moonrise) ([Modrinth: `moonrise-opt`](https://modrinth.com/mod/moonrise-opt)), NeoForge 1.21.1 | 0.1.0-beta.15 (2025-10-10, the last 1.21.1 build). Rewrites the chunk system and *optimises* the entity tracker: vanilla `ChunkMap$TrackedEntity`, `updatePlayer` and `broadcastToPlayer` stay, but Moonrise drives `updatePlayer` every tick for the players near each tracked entity and skips the `ChunkMap#move` loop **(corrected: it does not replace the tracker)** | GPL-3.0 |
| [Sodium](https://modrinth.com/mod/sodium), NeoForge 1.21.1 | 0.8.13 (2026-08-28) | PolyForm Shield 1.0.0 |
| [Iris](https://modrinth.com/mod/iris), NeoForge 1.21.1 | 1.8.14-beta.1 (2026-06-13), a **beta**, and the build that moves to Sodium 0.8. The last stable NeoForge 1.21.1 build is 1.8.12 (2025-06-17), which requires Sodium 0.6.13 **(added)** | LGPL-3.0 |
| [spark](https://modrinth.com/mod/spark), NeoForge 1.21.1 | 1.10.124 (2025-02-23) | GPL-3.0 |
| [Reign of Nether](https://github.com/SoLegendary/reignofnether), the most mature MC RTS | 1.5.0b (2026-10-14), **Forge 1.20.1 only** | GPL-3.0 |
| [MineColonies](https://github.com/ldtteam/minecolonies) | `version/1.21` branch for NeoForge 1.21.1, commit 2026-10-06 | GPL-3.0 |
| [Baritone](https://github.com/cabaletta/baritone) | `1.21.1` branch: its last commit (2026-09-01) is "deprecate 1.21.1". The branch is **deprecated** and gets no further updates; primary development is on `1.21.4` **(corrected)** | LGPL-3.0 |

NeoForge 21.1 has first-party hooks for almost every engine need below: ticket controllers, render-stage events (Sodium dispatches some of them), section geometry, GUI layers, synced attachments, and a packet splitter. I did not audit the Fabric equivalents.

---

## 1. Server tick budget and per-entity cost

### 1.1 What one `Mob` costs per tick in 1.21.1 [src]

`ServerLevel#tick` runs `entityTickList.forEach(...)`. Each stage, what it scales with, and what it means for an RTS:

| Stage | 1.21.1 code path | Scales with | RTS consequence |
|---|---|---|---|
| Despawn check | `Entity#checkDespawn` runs **every tick for every entity in the ticking list, before the range gate**. `Mob#checkDespawn` first posts NeoForge's `MobDespawnEvent` (every tick, even for persistent mobs) **(added)**, then calls `getNearestPlayer` unless `isPersistenceRequired()` or `requiresCustomPersistence()` | players, event bus | Make units persistent and override `checkDespawn` as a no-op (this also skips the event post) |
| Range gate | `DistanceManager#inEntityTickingRange(chunk)`: only chunks inside simulation distance (ticket level ≤ 31) tick entities | – | Units elsewhere are frozen (§3) |
| NeoForge hook | `EventHooks.fireEntityTickPre`, i.e. the cancelable `EntityTickEvent.Pre`, then `Entity#tick`, then `EntityTickEvent.Post`. `tickCount` is incremented before the event, even when it is cancelled **(added)** | event bus | Can throttle *other mods'* entities |
| Base tick | `Entity#baseTick`: portal, fluid state and fluid pushing over the AABB, fire, void check. `LivingEntity#baseTick`: suffocation check (`isInWall`), breathing (NeoForge posts `LivingBreatheEvent` every tick) **(added)**, `hurtTime--`, `invulnerableTime--`, effects | block reads, event bus | – |
| Living tick | `LivingEntity#tick`: `detectEquipmentUpdates()` every tick, `CombatTracker` every 20 ticks, then `aiStep()`, then head/body rotation | slots | – |
| AI | `Mob#serverAiStep` is **`final`**. It runs `sensing.tick()`. The target and goal selectors do a full `tick()` when `(tickCount + getId())` is even (and on the first two ticks) and only `tickRunningGoals(false)` otherwise. Then `navigation.tick()`, `customServerAiStep()`, and the move/look/jump controls | goals | The only method override point inside is `customServerAiStep()` (plus `sendDebugPackets()`); the navigation and the move/look/jump controls are replaceable objects |
| Movement | `travel` → `handleRelativeFrictionAndCalculateMovement` → `Entity#move`, **every tick, even when idle**, because gravity pushes into the ground. `collide` runs an entity-section query (`EntityGetter#getEntityCollisions`) plus block collision, then `checkInsideBlocks`, step sounds and `GameEvent.STEP` vibrations | neighbours, blocks | Idle units still pay for collision |
| Pushing | `LivingEntity#pushEntities` calls `level.getEntities(this, bbox, EntitySelector.pushableBy(this))` and `doPush`. When an entity overlaps 24 or more *other* pushable, non-passenger entities (gamerule `maxEntityCramming`, default 24), it has a 1-in-4 chance per tick of taking 6 cramming damage | neighbours, so O(n²) in clumps | Armies clump, so turn it off |
| Loot | `Mob#aiStep` scans for `ItemEntity` if `canPickUpLoot()` | – | Keep it off |
| Replication | `ChunkMap#tick` visits every tracked entity. It runs inside `ServerChunkCache#tick`, i.e. *before* the entity loop of the same `ServerLevel#tick`, so it ships the previous tick's state **(corrected: not "after the loop")**. `ServerEntity#sendChanges` builds packets every `updateInterval` ticks | entities × viewers | §4 |

Costs that grow with unit count across the whole level [src]:

- **Path invalidation on every block change.**
  - `ServerLevel#sendBlockUpdated` loops over `navigatingMobs`, which holds every loaded (tracked) `Mob` in the level, not only ticking ones.
  - For each block whose collision shape changes, it calls `PathNavigation#shouldRecomputePath(pos)` and then `recomputePath()` synchronously (at most once per 20 ticks per mob; a second change inside that window defers the recompute to the mob's next navigation tick).
  - The per-mob check is cheap; the expensive synchronous searches run only for mobs whose active path passes near the changed block **(added)**.
  - Construction therefore costs O(blocks placed × mobs) in checks, plus one search per affected moving mob.
  - Lithium's `mixin.entity.inactive_navigations` (on by default in the NeoForge 1.21.1 build) skips mobs that cannot react ([Lithium NeoForge config](https://github.com/CaffeineMC/lithium/blob/develop/lithium-neoforge-mixin-config.md); the [1.21.1 branch](https://github.com/CaffeineMC/lithium/blob/1.21.1/lithium-neoforge-mixin-config.md) has the same option).
- **Tracker work on every player move.** `ChunkMap#move(ServerPlayer)` runs from each movement packet and calls `TrackedEntity#updatePlayer` on **all** tracked entities. A commander who pans every tick costs O(entities) per tick. Under Moonrise this loop is skipped; instead every tracked entity calls `updatePlayer` for each player within view distance every tick **(added)**.
- **Entity lookups.** `Level#getEntities` walks a sorted section-id set over the AABB, grown by 2 blocks horizontally and 4 down, and tests every entity in those sections. Sections index entities by class (`ClassInstanceMultiMap`), so `getEntitiesOfClass(UnitEntity.class, …)` is cheaper than an untyped query. NeoForge also scans every `PartEntity` in the level on each `getEntities`/`getEntitiesOfClass` call; vanilla only has Ender Dragon parts, so avoid multipart units **(added)**.
- **Empty levels stop.**
  - A level with no players stops ticking entities after 300 ticks (`emptyTime`), unless it has forced chunks.
  - NeoForge 21.1 patches this check to also count its own ticket-controller chunks (`ForcedChunkManager.hasForcedChunks`).
  - Custom vanilla `TicketType` region tickets do **not** count; only `/forceload` and NeoForge `TicketController` chunks keep an empty level ticking **(added)**.

### 1.2 Benchmarks and realistic unit counts

| Source | Setup | Result |
|---|---|---|
| [PathWeaver](https://github.com/zimdin12/PathWeaver) (MIT, Fabric, MC 26.1.1–26.2) [bench] | 1,024 zombies in a walled maze, all retargeting every 6 ticks, "many-core" desktop. Both arms run Fabric API + Lithium; "vanilla" means PathWeaver off | Vanilla: mean tick 88.5–96.6 ms, p99 832–958 ms (0.6.1 jar, measured 2026-07-31); an older, gate-cleared harness on a 16-core/32-thread desktop measured 83–87 ms mean, p95 738–766 ms **(corrected: the two runs had been merged as "83–97 ms")**. **About 480–500 µs of main-thread time per path request** |
| same (older harness, 16-core/32-thread desktop) | 1,024 zombies, each retargeting every 20 ticks (~51 requests/tick) | Vanilla holds 20 TPS, but p95 is 59–67 ms and p99 77–80 ms |
| same, spark profile | 220 mixed mobs retargeting every 6 ticks (driven by a synthetic retarget probe), 221-mod pack, two 45-second profiles | Pathfinding takes 12.4 % of *sampled* server-thread time, but the thread was 67–74 % idle; against tick time it is **≈6.2 ms of a 15.47 ms tick (40.3 % of `tickServer`)** **(corrected: 15.5 ms/tick is the whole tick, not the pathfinding cost)** |
| [Async](https://modrinth.com/mod/async) (GPL-3.0-or-later) [bench] | 9,000 villagers, MC 1.21.4 (Fabric), Ryzen 9 7950X3D, with Lithium **plus a full optimisation pack** (C2ME, FerriteCore, ScalableLux, ServerCore, TT20, Very Many Players, Carpet, …) **(corrected)** | 225.4 ms/tick (4.4 TPS) without Async, about 25 µs per villager (total MSPT ÷ villagers) |
| [Laminar](https://modrinth.com/mod/laminar-engine) (ARR) [bench] | 500 zombies, 32 chunks, i7-4770K @ 4.3 GHz. The benchmark does not state the MC version; the mod itself targets Fabric 26.1–26.2 | Vanilla median 31.1 ms, p95 50.5 ms, about 62 µs per zombie (total MSPT ÷ zombies) on a 2013 CPU |
| Reign of Nether [src] | `maxPopulation` gamerule default | 150 population per player |

What these numbers suggest together [inf]:

- **Per-mob cost.**
  - A vanilla-AI mob costs roughly **20–30 µs per tick** on a current desktop core, and more in dense clumps (unverified: both data points are total MSPT divided by mob count, from other MC versions, loaders and mod stacks, and the Async figure includes a full optimisation pack).
  - A vanilla path search in non-trivial terrain costs **0.1–0.5 ms** of main-thread time (PathWeaver: about 0.1 ms for swim searches, 0.48–0.5 ms for zombies in a maze).
  - Pathfinding takes a large share of tick time whenever mobs repath often: 40 % of the tick in PathWeaver's spark profile. It dominates during repath bursts, and RTS group orders are exactly that **(corrected: the report said "normally a minority"; its share in ordinary play is unverified)**.
- **Capacity on the dev machine.**
  - Public single-thread scores put the M4 Pro above the Zen 4 desktops in these tests (Geekbench 6 single-core ≈3,900 vs ≈3,000 for the 7950X3D).
  - At 20–30 µs per mob, a 50 ms tick with nothing else running would hold roughly 1,700–2,500 vanilla-AI mobs; 1,000–1,500 corresponds to 33–50 µs per mob, i.e. dense clumps **(corrected arithmetic; unverified)**.
- **Budget for an RTS.**
  - Plan about **20 ms of the 50 ms tick for units**. The rest goes to chunks, block entities, our economy and AI, and GC pauses.
  - That allows roughly 600–800 vanilla-AI units (at 25–30 µs), or **1,500–2,000 stripped units at about 10 µs average** with level of detail and off-thread pathing (unverified extrapolation; measure in week 1).
  - Client rendering of that many animated models may hit its limit first. I did not measure it.

### 1.3 Choosing the base class

| Base | What you get | What it costs | Use for |
|---|---|---|---|
| `Entity` | Cheapest tick; you own everything | No health/damage pipeline, effects, attributes or equipment layers. Vanilla mobs and projectiles treat it as non-living | Projectiles, markers, rally flags |
| `LivingEntity` | Health, NeoForge damage events (`LivingIncomingDamageEvent`, `DamageContainer`), attributes, effects, equipment rendering. **Not** registered in `navigatingMobs` | No `MoveControl`, `PathNavigation` or goals. You drive movement yourself through `xxa`/`zza` + `travel` | A lean unit if we never want vanilla navigation |
| `Mob` | Adds goal/target selectors, `Sensing`, `PathNavigation`, controls, persistence and leads | Registered in `navigatingMobs`; has despawn logic; `serverAiStep` is final | – |
| `PathfinderMob` | Adds `getWalkTargetValue`, `isPathFinding`/`isPanicking` and leash-follow behaviour; most vanilla movement goals (`RandomStrollGoal`, `PanicGoal`, `MeleeAttackGoal`, …) require a `PathfinderMob` | Nothing important beyond `Mob` | **Recommended base, stripped down** |

**Recommendation [inf]:** subclass `PathfinderMob` and strip it down.

- With zero goals and an empty brain, the overhead `Mob` adds is small next to movement and collision, which a `LivingEntity` pays as well (unverified).
- Staying a `Mob` keeps spawn eggs, `Mob#doHurtTarget`, held-item and armour rendering, leads, vanilla mob targeting, and compatibility with other mods.
- Confirm it with a week-1 micro-benchmark: 1,000 idle and 1,000 moving units, `PathfinderMob` versus `LivingEntity`.

### 1.4 Lightweight unit recipe (1.21.1 names)

- **Entity type.**
  - `EntityType.Builder.of(...).sized(0.6F, 1.8F).clientTrackingRange(n).updateInterval(3–4)`. The defaults are range 5 chunks and interval 3 ticks [src].
  - NeoForge's `setShouldReceiveVelocityUpdates(false)` drops routine `ClientboundSetEntityMotionPacket`s, about 12 B per update.
  - Knockback still reaches clients through `hurtMarked`, which always broadcasts the motion packet [src].
- **No vanilla AI.**
  - Leave `registerGoals()` empty. The default `Brain` from `brainProvider()` is empty and is never ticked by `LivingEntity`/`Mob` [src].
  - Run the unit controller from `customServerAiStep()`, staggered with `(tickCount + getId()) % interval`. Vanilla uses the same trick for its goal selectors.
- **No vanilla pushing.**
  - `isPushable() → false`, and override `pushEntities()` as a no-op (without the override the neighbour query still runs every tick). Separation comes from our own steering (§2).
  - Non-pushable units are never counted for cramming.
- **No despawn, loot or vibrations.** Call `setPersistenceRequired()`, return false from `removeWhenFarAway`, make `checkDespawn()` a no-op, keep `canPickUpLoot` false, and return `NONE` from `getMovementEmission()` so no `GameEvent.STEP` is dispatched. `SOUNDS` is not enough: `Entity#move` still emits `GameEvent.STEP` whenever the unit's on-position and legacy on-position coincide, which is the normal case on full blocks [src] **(corrected)**.
- **Movement.**
  - Write a custom `MoveControl` fed by steering, and keep the vanilla `travel`/`move` for physics.
  - Set `Attributes.STEP_HEIGHT` to 1.0. Units then walk up single blocks with no jump logic, because `LivingEntity#maxUpStep` reads this attribute [src].
- **Combat invulnerability frames.**
  - While `invulnerableTime > 10`, `LivingEntity#hurt` ignores any hit no stronger than the previous one (`lastHurt`), and a stronger hit only deals the difference (`amount − lastHurt`) without resetting the timer. A hit outside that window sets the timer to the container's `getPostAttackInvulnerabilityTicks()` (default 20) **(corrected: the timer is not reset by hits inside the window)**.
  - When many archers focus one target, damage is lost silently.
  - Fix it in `LivingIncomingDamageEvent` (posted before the invulnerability check) with `getContainer().setPostAttackInvulnerabilityTicks(0)` for unit-on-unit damage, or tag our damage types `minecraft:bypasses_cooldown` [src]. A `bypasses_cooldown` hit still sets the timer to 20 against later non-bypassing hits.
- **Level of detail (LOD) tiers [inf].**
  - T0, visible or fighting: full tick; think every 2–4 ticks.
  - T1, moving but unseen: full physics; think every 8–10 ticks.
  - T2, idle, garrisoned or gathering: skip `super.tick()` and run a cheap sanity tick every 20 ticks.
  - T3, unloaded: a data record only (§3).
  - Caveat: skipping ticks freezes `hurtTime` and `invulnerableTime`, which only `LivingEntity#baseTick` decrements [src]. A sleeping unit must wake in `hurt`, and on orders, effects, fire, fluids, or a block change under it.
- **Client.**
  - Override `shouldRenderAtSqrDistance`. Vanilla culls at average AABB side × 64 × `viewScale`, where `viewScale` = clamp(effective render distance ÷ 8, 1.0, 2.5) × the "Entity Distance" setting [src]. For a zombie-sized mob that is about 67 blocks at render distance ≤ 8, about 100 blocks at the default 12 chunks, and about 168 blocks at 20+ chunks (100 % Entity Distance) **(corrected: the render-distance factor was missing)**.
  - That can still be too short for a camera 40–80 blocks above the battlefield looking at distant units, especially on servers with a low view distance.

### 1.5 Measuring

- `/tick query`
- `/tick sprint <ticks>`, which reports ms per tick
- `/neoforge tps`
- `/neoforge track start entity <seconds>`, then `/neoforge track entity` for per-entity timings
- `/jfr start|stop`, all present in 1.21.1 [src] (`/jfr` registers only when JFR is available)
- spark `/spark profiler` and `/spark tickmonitor`

---

## 2. Pathfinding

### 2.1 Vanilla pathfinding in 1.21.1 [src]

- **Node cap.**
  - The `PathNavigation(Mob, Level)` constructor fixes `maxVisitedNodes = floor(FOLLOW_RANGE × 16)` once.
  - That is 256 nodes for the `Mob` default of 16, and 560 for zombies (35). `setMaxVisitedNodesMultiplier` scales it.
- **Search region.**
  - `createPath(...)` builds a `PathNavigationRegion` with radius `followRange + 8`, or `+16` for entity targets.
  - The region holds **live `ChunkAccess` references** from `ChunkSource#getChunkNow`. Unloaded chunks read as `EmptyLevelChunk` (void air).
- **Search.**
  - `PathFinder#findPath` is A* over a `BinaryHeap` with the heuristic weighted ×1.5 (`FUDGING`). It is fast but not optimal.
  - It stops at the first target within `accuracy` (Manhattan distance) or when it hits `maxVisitedNodes`, and never expands nodes beyond `maxRange`.
  - If no target is reached it returns a **partial path** to the closest node.
- **Movement rules.**
  - `WalkNodeEvaluator` uses 4 cardinal and 4 diagonal neighbours, step-up `max(1, maxUpStep())`, and `getMaxFallDistance()`.
  - It also handles doors (`canOpenDoors`/`canPassDoors`), fences, water (`canFloat`), and a per-`PathType` malus.
- **Type cache.** `PathTypeCache` is a single 4096-slot direct-mapped cache per `ServerLevel`, built from two plain arrays and invalidated in `sendBlockUpdated`. Every mob's `PathfindingContext` in that level uses it. It is **not thread-safe**.
- **Following.** `PathNavigation#tick` → `followThePath` → `MoveControl#setWantedPosition`, with a stuck check every 100 ticks. Paths are recomputed after block changes, at most once per 20 ticks.

Why this doesn't fit an RTS:

- The horizon is short, tens of blocks.
- It runs one synchronous search per unit. An order to 100 units means 100 searches, roughly 10–50 ms by the PathWeaver numbers.
- 8-connected node paths without smoothing zig-zag, and the ×1.5 weighting makes them suboptimal.
- There is no group, formation or avoidance logic beyond pushing.
- Construction triggers invalidation storms.

### 2.2 Recommended stack [inf]

| Layer | Technique | Notes |
|---|---|---|
| Navigation grid | Compact per-chunk cell classes (standable, step-up, water, door, fence or wall, damage, cost) over a Y band. Built on the main thread from block states and published as immutable copy-on-write snapshots | Reign of Nether's `WalkabilityGrid` does this, with budgeted classification and region-only patches on block change [src] |
| Hierarchy | HPA*-style clusters (16×16 columns or 16³ sections) with entrance nodes, plus connected-component labels for O(1) reachability checks | Botea, Müller, Schaeffer, "Near Optimal Hierarchical Path-Finding", *Journal of Game Development* 1(1), 2004: up to 10× faster than optimised A*, within 1 % of optimal (the previously cited PDF, webdocs.cs.ualberta.ca/~mmueller/ps/hpastar.pdf, returned 404 on 2026-10-20) **(corrected link)**. [Factorio FFF #317](https://factorio.com/blog/post/fff-317) uses an abstract chunk search as the heuristic for the real one |
| Low level | A* inside the cluster corridor, then string-pulling smoothing | Jump point search (JPS) assumes uniform costs and 2D moves. Step-ups, drops, water and door costs break that, and block edits invalidate JPS+ preprocessing |
| Groups | Flow fields per destination over the corridor (cost field → integration field → direction field), cached per goal cluster | [Elijah Emerson, *Game AI Pro* ch. 23](http://www.gameaipro.com/GameAIPro/GameAIPro_Chapter23_Crowd_Pathfinding_and_Steering_Using_Flow_Field_Tiles.pdf), the Supreme Commander 2 technique |
| Local avoidance | ORCA ([RVO2](https://github.com/snape/RVO2), C++, Apache-2.0, small enough to port) on the XZ plane, or boids-style separation. Neighbours come from a per-tick uniform spatial hash of our own units | Replaces vanilla pushing; O(n·k) |
| Formations | Slot templates (line, box, staggered, flank, as in AoE II). One path for the formation centre, greedy or Hungarian slot assignment, speed set by the slowest member | 0 A.D. moves formations through a controller entity |

### 2.3 Running it off the main thread safely

- **Never touch `Level` or `ServerChunkCache` from a worker [src].**
  - Called off-thread, `ServerChunkCache#getChunk` runs `CompletableFuture.supplyAsync(..., mainThreadProcessor).join()`. The worker blocks on the server thread, which means stalls and a risk of deadlock.
  - `getChunkNow` returns `null` off-thread, so a vanilla `PathNavigationRegion` built on a worker sees only air.
- **Snapshot on the main thread [src].**
  - `PalettedContainer#copy()` clones the palette and storage.
  - Vanilla does exactly this for off-thread chunk meshing: `RenderChunk` copies `LevelChunkSection#getStates().copy()`.
  - Live `get()` reads take no lock (only `set`/`getAndSet` use the `ThreadingDetector`), but under concurrent writes they can return stale or `null` states.
  - Prefer our own compact nav grid with immutable snapshots.
- **Apply results on the main thread [inf].**
  - Tag each request with a sequence number and drop results for superseded orders.
  - Use bounded queues and a per-tick budget for main-thread classification.
  - Return a "busy" answer distinct from "no path".
- **Don't share vanilla caches.** Give each worker its own type cache; evaluators and `PathTypeCache` are single-threaded, and the level's `PathTypeCache` is shared by every mob in that level.
- **Invalidation hook.**
  - Option 1: a mixin on `LevelChunk#setBlockState`, which catches everything, including direct chunk writes. This is Reign of Nether's choice.
  - Option 2: hook `ServerLevel#onBlockStateChange`, which `Level#markAndNotifyBlock` calls for every `Level#setBlock` that changes state [src]. Caveats: during player block placement NeoForge captures block snapshots and defers `markAndNotifyBlock` until `CommonHooks` replays it, and direct `LevelChunk#setBlockState` calls bypass it **(added)**.
  - NeoForge's place/break `BlockEvent`s only cover player and entity actions; `NeighborNotifyEvent` fires only for changes that notify neighbours, and `FluidPlaceBlockEvent` only for fluids. None catches every state change **(corrected)**.
  - Mark dirty regions, coalesce them, rebuild on a budget, and keep serving the stale-but-walkable snapshot meanwhile. Reign of Nether does this deliberately, so units are not stranded in a "blocked" hole.

### 2.4 What other projects do

| Project | Approach | Licence |
|---|---|---|
| MineColonies (1.21.1 branch) [src] | Job-based A* (`AbstractPathJob`) on **one** worker (`ThreadPoolExecutor(1,1)`, queue of 10,000). Its `ChunkCache` holds live `LevelChunk` references captured on the main thread and reads them off-thread, tolerating the race | GPL-3.0 |
| Baritone (1.21.1 branch, deprecated 2026-09-01; reference only) [src] | Client-side A* with cost coefficients {1.5 … 10}, primary and failure timeouts, segmented paths. Reads `ClientChunkCache` directly and falls back to cached 512×512 regions at 2 bits per block | LGPL-3.0 |
| Reign of Nether (Forge 1.20.1) [src] | `RtsPathfinder` takes a snapshot and hands it to `PathfinderWorkerPool`. Grid warm-up "MUST happen on the main thread" and is budgeted. Results are converted to vanilla `Path`. Gamerules `pathfindingThreads` and `pathfindingChunkBuildsPerTick` | GPL-3.0 |
| PathWeaver (Fabric 26.x) [bench] | Vanilla A* on workers over a live-backed region with per-thread caches. Region building and result installation stay on the main thread. Moves about 60 % of main-thread path cost off the main thread (480–500 → 195–202 µs per request; −60 % server-thread pathfinding in the spark profile), at about 12 % more total CPU **(corrected: not 3/4)** | MIT |
| Lithium [doc] | Faster node-type lookups and chunk access for vanilla pathing (`mixin.ai.pathing`, **disabled by default in the NeoForge 1.21.1 build**) **(corrected)**; skips inactive navigations on block updates (`mixin.entity.inactive_navigations`, on by default) | LGPL-3.0 |

**Licensing.** GPL-3.0 code (Reign of Nether, MineColonies) can only be copied if Strategos ships under a GPL-compatible licence; the distributed combined work is then GPLv3. Study their designs, but write our own code. RVO2 (Apache-2.0) and PathWeaver (MIT) can be ported with attribution; Apache-2.0 also requires keeping the licence and NOTICE text and marking changes.

---

## 3. Chunk loading and the world

### 3.1 Facts [src]

- **Ticket levels.**
  - Level 33 means accessible, 32 block ticking, and ≤ 31 entity ticking.
  - Entities tick only where the simulation-distance `TickingTracker` reports entity ticking. In border chunks they exist but are frozen.
- **Vanilla region tickets.**
  - `ServerChunkCache#addRegionTicket(TicketType, ChunkPos, distance, value[, forceTicks])` sets level `33 − distance` and also feeds the simulation tracker. The five-argument `forceTicks` overload is a NeoForge patch.
  - `TicketType.create(name, comparator, lifespanTicks)` gives self-expiring tickets; vanilla `PORTAL` lives 300 ticks.
- **NeoForge `TicketController`.**
  - Construct it as `TicketController(ResourceLocation id, @Nullable LoadingValidationCallback)` and register it in `RegisterTicketControllersEvent` (mod bus). Call `forceChunk(ServerLevel, BlockPos|Entity|UUID owner, chunkX, chunkZ, add, ticking)`.
  - Tickets persist in `ForcedChunksSavedData` and are revalidated on load.
  - It uses distance 2 (level 31), so **only the forced chunk itself is entity-ticking**. Entities and block entities in it tick with `ticking = false` too.
  - `ticking = true` runs the full per-chunk tick with no players nearby: random ticks **and natural mob spawning** (NeoForge's `forceTicks` patch in `ServerChunkCache#tickChunks`) **(corrected)**.
- **Entity storage.** On unload, entities are saved to `entities/r.*.mca` with `RemovalReason.UNLOADED_TO_CHUNK`. They come back only when that chunk's entity section loads again.
- **Spectators.** A spectator only loads or generates chunks while `spectatorsGenerateChunks` is true (default true; `ChunkMap#skipPlayer`).
- **NeoForge extras.** `/neoforge generate` pregenerates chunks; `ChunkTicketLevelUpdatedEvent` and `ChunkEvent.Load`/`Unload` are available.

### 3.2 Implications [inf]

1. **Data first, entities as views.**
   - Authoritative unit, army, caravan and building state lives in our own records in `SavedData`.
   - Entities exist only in entity-ticking chunks; materialize them there and dematerialize when the chunk leaves simulation.
   - MineColonies works this way: `ICitizenData` is authoritative, and `ICitizenManager#spawnOrCreateCitizen(data, level)` (re)creates the entity [src].
2. **Off-screen movement is abstract.** Advance traders and armies along the coarse cluster path at their speed in the data layer. Spawn the entity when it enters a loaded area.
3. **Follow-tickets, sparingly.**
   - Some groups must physically simulate far from players, for example a siege between AI factions that no one is watching.
   - For those, use a custom `TicketType` with a lifespan, re-placed every few ticks at distance 3. That gives a 3×3 entity-ticking area and keeps about 7×7 chunks loaded per group.
   - If the level may have no players at all, a custom `TicketType` alone does not keep it running: entity ticking stops 300 ticks after the level empties unless it holds `/forceload` or NeoForge `TicketController` chunks. Hold at least one `TicketController` ticket (with `ticking = false` unless random ticks are wanted) in such levels **(added)**.
   - Vanilla 1.21.2+ does the same for thrown ender pearls (`placeEnderPearlTicket`) [doc].
   - Cap the number of such groups, and never trigger worldgen: pregenerate the match map.
4. **Bounded maps.**
   - A pregenerated arena inside the world border keeps loading, fog of war and persistence tractable.
   - Reign of Nether only enables fog of war on "RTS-optimised" small maps, i.e. a world border of at most 1,280 blocks [src].

### 3.3 Changes in 26.x [doc]

- 1.21.5 rebuilt tickets:
  - `TicketType` became a registered record in `BuiltInRegistries.TICKET_TYPE`.
  - Tickets are persisted by `TicketStorage`.
  - New calls: `addTicketWithRadius`.
- 1.21.9 added flags: `FLAG_PERSIST`, `FLAG_LOADING`, `FLAG_SIMULATION`, `FLAG_KEEP_DIMENSION_ACTIVE`, `FLAG_CAN_EXPIRE_IF_UNLOADED`.
- `TicketController` still exists on NeoForge `26.3.x` [src].

---

## 4. Networking and synchronization

### 4.1 APIs [src]

- **Payloads.**
  - `RegisterPayloadHandlersEvent#registrar(version)` returns a `PayloadRegistrar`. Register with `playToClient`, `playToServer` or `playBidirectional(CustomPacketPayload.Type, StreamCodec<RegistryFriendlyByteBuf,T>, IPayloadHandler)`.
  - Modifiers: `.executesOn(HandlerThread.MAIN|NETWORK)` (MAIN by default), `.versioned(String)`, `.optional()`.
  - Send with `PacketDistributor.sendToPlayer`, `sendToPlayersTrackingEntity`, `sendToPlayersTrackingChunk`, `sendToPlayersNear` or `sendToPlayersInDimension` ([docs](https://docs.neoforged.net/docs/1.21.1/networking/payload)).
- **Limits.**
  - Vanilla's fallback codec for *unknown* custom payloads (`DiscardedPayload`) caps them at 1 MiB clientbound and 32,767 B serverbound. Payloads registered through NeoForge use their own `StreamCodec` and are bounded instead by the protocol frame limits: at most 2 MiB per compressed frame and 8 MiB uncompressed **(corrected)**.
  - NeoForge's `GenericPacketSplitter` splits packets above those limits on NeoForge-to-NeoForge connections.
  - A bundle holds at most 4,096 packets.
- **`SynchedEntityData`.**
  - Ids come from `defineId`, at most 254 per class hierarchy (the maximum id value is 254; `Entity`, `LivingEntity` and `Mob` already use 16), declared in `defineSynchedData(SynchedEntityData.Builder)`.
  - Dirty values go to trackers through `ServerEntity#sendDirtyEntityData`.
  - Custom serializers are registered in `NeoForgeRegistries.ENTITY_DATA_SERIALIZERS`.
- **Spawn data.** `IEntityWithComplexSpawn`, `IEntityExtension#sendPairingData(ServerPlayer, Consumer<CustomPacketPayload>)`, and the `PlayerEvent.StartTracking`/`StopTracking` events.
- **Attachment sync.**
  - `AttachmentType.Builder#sync(...)` and `IAttachmentHolder#syncData` were backported in **21.1.197**.
  - Until **21.1.264** (2026-10-19), the per-player `sendToPlayer` predicate was not checked on *initial* sync [doc: changelog; src: `AttachmentSync#syncInitialAttachments` in 21.1.262].
  - Fog-of-war-filtered attachments therefore need NeoForge 21.1.264 or later.

### 4.2 Tracker behaviour [src]

- **Range.**
  - The range is `clientTrackingRange × 16` blocks, scaled by `entity-broadcast-range-percentage` and capped by the player's view distance. The entity's chunk must also be tracked by (sent to) that player.
  - Distance is measured **horizontally from the player entity, not the camera**.
- **Update cadence.** `ServerEntity#sendChanges` sends only every `updateInterval` ticks, after an impulse, or when entity data is dirty. A dirty `SynchedEntityData` value or `hasImpulse` runs the full position/rotation path that tick, so frequently changing synced data defeats a long `updateInterval` **(added)**.
- **Packets sent per update.**
  - Position and rotation go as `ClientboundMoveEntityPacket.Pos`, `Rot` or `PosRot`: deltas in 1/4096 block, good for ±8 blocks.
  - A `ClientboundTeleportEntityPacket` replaces it when the delta is out of range, every 400 updates, and **every time `onGround` flips** (every jump).
  - `ClientboundSetEntityMotionPacket` is sent when velocity changes, if velocity is tracked; `ClientboundRotateHeadPacket` when the head turns.
- **Flushing.** `MinecraftServer#tickChildren` suspends flushing during the tick, so each player gets one flush per tick.

### 4.3 Bandwidth for moving units [inf, computed from 1.21.1 packet formats]

Assumptions: the entity-id VarInt takes 3 B; compression is on with the default threshold of 256, so small packets travel uncompressed with a 1 B marker; plus a 1 B length prefix.

| Configuration | Bytes per unit-update | Rate | 300 moving units | 1,000 moving units |
|---|---|---|---|---|
| Vanilla defaults (PosRot 15 + Motion 12 + HeadRot 7) | ~34 | 6.7 Hz (interval 3) | ~68 KB/s (0.54 Mbit/s) | ~227 KB/s (1.8 Mbit/s) |
| Velocity updates off | ~22 | 6.7 Hz | ~44 KB/s | ~147 KB/s |
| Velocity off, interval 4 | ~22 | 5 Hz | ~33 KB/s | ~110 KB/s |
| Custom batched delta channel (~6 B per unit) | ~6 | 6.7 Hz | ~12 KB/s | ~40 KB/s |

- These figures are per client. The host's upload is that times the number of clients.
- They are upper bounds for the motion part: vanilla sends the motion packet only when velocity changes by more than about 3×10⁻⁴ blocks/tick.
- Not included:
  - teleports on jumps, about 33 B each
  - entity-data changes, about 13 B per value (each change also forces a position update that tick)
  - spawn bundles
  - TCP/IP overhead, about 2–3 KB/s at 20 flushes per second
- **Conclusion.**
  - Vanilla tracking with velocity off is fine for LAN and for 2–4-player internet games at up to 300–500 visible moving units (unverified until measured).
  - A custom batched movement channel, with our own client interpolation, is an optimization to keep in reserve.

### 4.4 Fog-of-war filtering

- **The hook [src].** `Entity#broadcastToPlayer(ServerPlayer)` (default `true`) is part of the pairing condition in `TrackedEntity#updatePlayer`. Override it in our unit and building classes with an O(1) lookup into the viewer faction's visibility grid.
- **Pitfall: visibility is not re-evaluated on its own [src].**
  - In vanilla, `updatePlayer` only runs when the entity changes section, when that player moves (`ChunkMap#move`), or when the entity spawns.
  - So a stationary enemy uncovered by your scout is *not* re-evaluated.
  - Fix [inf]: every 5–10 ticks, re-evaluate the entities whose visibility flipped. That needs an access transformer:
    `public net.minecraft.server.level.ChunkMap entityMap` and `public net.minecraft.server.level.ChunkMap$TrackedEntity`; then call `updatePlayer(player)` (reach `ChunkMap` through the public `ServerChunkCache#chunkMap`).
  - Reign of Nether instead injects at the head of `TrackedEntity#updatePlayer` [src].
- **Leaks the entity filter doesn't stop.**
  - Chunk and block updates. Reign of Nether gates chunk packets per column and serves a pre-match snapshot for fogged chunks.
  - Sounds and particles, which are broadcast by position.
  - Block-break progress.
  - Scoreboard-team membership, if units are mirrored into teams: it is sent to every client **(added)**.
  - Plan: v1 hides entities, which is cheap and authoritative. Terrain and block secrecy is a later milestone.
- **Compatibility [src] (corrected).** Moonrise (1.21.1 branch) does not replace `ChunkMap$TrackedEntity`. It keeps vanilla `updatePlayer`, and therefore `broadcastToPlayer`; it turns off the per-move loop in `ChunkMap#move` and calls `updatePlayer` every tick for every tracked entity and each player within view distance. Under Moonrise, visibility flips are therefore re-evaluated every tick even without our pass, and an access-transformer call to `updatePlayer` still works. Mixins into the loops of `ChunkMap#move`/`ChunkMap#tick` would collide with Moonrise's `@Redirect`s. The `broadcastToPlayer` override is the most portable part. Test it.

### 4.5 RTS state traffic [inf]

Use two-tier replication:

1. Vanilla tracking for units near the commander.
2. One batched, fog-filtered "minimap/army" payload per player at 1–2 Hz, with coarse positions (about 4 B per unit) for the whole map.

Other traffic:

- Orders, production queues and resources: event-driven payloads with per-faction interest management.
- HUD health and state: `SynchedEntityData`, which is delta-synced automatically. Keep it to values that change rarely, because every dirty tick also sends a position update.

---

## 5. Client RTS camera and UX

### 5.1 Camera approaches [src]

| Approach | How it works | Pros | Cons |
|---|---|---|---|
| A. Mixin `Camera#setup` (TAIL) and move the player | Set position and rotation through the protected `setPosition`/`setRotation`. The commander's own player entity moves with the camera (spectator, flying or invisible) | Chunk loading and tracking follow automatically. Used by Reign of Nether | Mixins into `Camera`; spectator side effects |
| B. Camera entity | A client-only entity set with `Minecraft#setCameraEntity`. [Freecam](https://github.com/MinecraftFreecam/Freecam) (MIT) uses `FreeCamera extends AbstractClientPlayer`; [RTSbuilding](https://github.com/Hcrab/RTSbuilding) (LGPL-3.0-only code plus an extra user-terms document and separate asset licences; NeoForge 1.21.1) adds a server-side anchor (`RtsCameraEntity`) | Clean separation from the player | The server still loads and tracks around the *player*, so RTSbuilding clamps the camera to an action radius |
| C. NeoForge events only | `ViewportEvent.ComputeCameraAngles` (yaw/pitch/roll, posted inside `Camera#setup`) plus `CalculateDetachedCameraDistanceEvent` in third person | No mixins | Camera stays tied to the entity, and `Camera#getMaxZoom` raycasts and pulls it in when terrain is in the way |

- **Projection.**
  - Orthographic projection is possible by injecting at the head of `GameRenderer#getProjectionMatrix`, as Reign of Nether does.
  - With it, `Frustum#offsetToFullyIncludeCameraCube` can loop forever, and Reign of Nether cancels it. That method still exists in 1.21.1.
  - A perspective camera at 50–60° pitch avoids the issue.
- **Cursor.**
  - Call `MouseHandler#releaseMouse()`.
  - In-world, vanilla re-grabs the mouse on the next click (`onPress` → `grabMouse()` when `screen == null`). The cancelable `InputEvent.MouseButton.Pre` fires first, though; cancelling it in RTS mode keeps the cursor free and suppresses attack/use.
  - `Minecraft#setScreen(null)` also re-grabs the mouse whenever any screen (chat, inventory, pause) closes, so RTS mode must release it again **(added)**.
  - The alternative is a full-window screen. Reign of Nether's `TopdownGui extends AbstractContainerScreen`. Mouse events are robust there, but vanilla keybinds stop and you poll keys yourself.
  - `MovementInputUpdateEvent` neutralizes movement input. `CalculatePlayerTurnEvent` only changes sensitivity and cinematic mode and cannot cleanly zero turning; with the cursor released, vanilla does not turn the player anyway, because `turnPlayer` runs only while the mouse is grabbed **(corrected)**.
- **Picking.**
  - Take the cursor from `MouseHandler#xpos/ypos` and normalise by `Window#getScreenWidth/Height`, **not** `getWidth/Height`. The latter are framebuffer pixels, 2× on the Retina dev Mac.
  - Normalised device coordinates (NDC) = (2x/w − 1, 1 − 2y/h).
  - Unproject with the inverse of `projection × modelView`, captured in `RenderLevelStageEvent` (`getProjectionMatrix()`, `getModelViewMatrix()`). The model-view matrix is rotation-only, so add `Camera#getPosition()`.
  - Disable view bobbing in RTS mode; it is baked into the projection, together with hurt-cam tilt and nausea distortion.
  - Blocks: `level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()))`.
  - Entities: clip AABBs over `ClientLevel#entitiesForRendering()`, or use `ProjectileUtil.getEntityHitResult(level, entity, from, to, box, filter)`.
  - Box selection: project unit positions to the screen and test them against the rectangle.

### 5.2 World rendering [src]

- **Stages.** `RenderLevelStageEvent.Stage` has `AFTER_SKY`, `AFTER_SOLID_BLOCKS`, `AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS`, `AFTER_CUTOUT_BLOCKS`, `AFTER_ENTITIES`, `AFTER_BLOCK_ENTITIES`, `AFTER_TRANSLUCENT_BLOCKS`, `AFTER_TRIPWIRE_BLOCKS`, `AFTER_PARTICLES`, `AFTER_WEATHER` and `AFTER_LEVEL`. The event also exposes the `PoseStack`, `Camera`, `Frustum` and partial tick (as a `DeltaTracker`).
- **Selection rings, health bars, rally lines.** Draw them in one batched pass per frame at `AFTER_ENTITIES` or `AFTER_TRANSLUCENT_BLOCKS`. Use a `MultiBufferSource.BufferSource` with vanilla `RenderType`s (`lines()`, `debugQuads()`, or a translucent position-colour type), translated by −camera. A few hundred quads is negligible.
- **Static terrain decals** (territory tint, build grid).
  - `AddSectionGeometryEvent` bakes extra geometry into the chunk section meshes.
  - The event fires on the main client thread, but the registered renderers run on the chunk-build thread, so read our data in the handler and hand the renderer an immutable copy **(added)**.
  - Re-mesh with `LevelRenderer#setSectionDirty` when they change. Don't use it for per-frame effects.
- **Outlines.**
  - Vanilla draws outlines when `Minecraft#shouldEntityAppearGlowing(e)` → `e.isCurrentlyGlowing()` is true, coloured by `e.getTeamColor()`.
  - Override both in the unit class, client side, to outline the current selection in faction colour. No scoreboard and no server round-trip are needed.
  - `LevelRenderer#requestOutlineEffect()` (NeoForge 21.1.51) covers custom renderers.
- **HUD.** `RegisterGuiLayersEvent` (`registerAbove`, `registerBelow`, `registerAboveAll`, `registerBelowAll`; layer ids in `VanillaGuiLayers`). Hide the vanilla hotbar, crosshair and health by cancelling `RenderGuiLayerEvent.Pre`.
- **Minimap.**
  - Use `BlockState#getMapColor(level, pos)` plus a heightmap per column, written into one `NativeImage` per chunk and uploaded to a `DynamicTexture`.
  - Update it on chunk load and block change; draw units and fog as overlays each frame.

### 5.3 Sodium, Iris and rendering churn

- **Sodium [src].**
  - Sodium's NeoForge `1.21.1/stable` branch (commit 2026-10-18) dispatches NeoForge's chunk-layer `RenderLevelStageEvent`s and runs `AddSectionGeometryEvent` renderers (`NeoForgeLevelRenderHooks`).
  - Entity rendering and the other stages remain vanilla code paths.
- **Iris [src].**
  - Check `IrisApi.getInstance().isShaderPackInUse()` and `isRenderingShadowPass()`, and degrade gracefully: skip custom post effects and don't draw in the shadow pass.
  - On NeoForge 1.21.1, Sodium 0.8.x pairs only with Iris 1.8.14-beta.1; the stable Iris 1.8.12 requires Sodium 0.6.13 **(added)**.
  - The vanilla outline post-chain under shader packs is unverified. Test it.
- **Compatibility pressure.**
  - Reign of Nether needed `Frustum` and chunk-region mixins.
  - RTSbuilding ships compatibility mixins for Sodium, Embeddium and Flywheel.
  - Camera mods touch the renderer often.
- **Changes in 26.x [doc].**
  - 1.21.2: entity render states.
  - 1.21.5: render pipelines.
  - 1.21.6: GUI render states.
  - 1.21.9: submission-based rendering and the debug overhaul.
  - 1.21.11: `RenderType` reshuffle and `ResourceLocation` → `Identifier`.
  - 26.1: camera render state.
  - 26.2: Vulkan backend.
  - 26.3: Blaze3D split out into "Renderpearl".
  - **Client rendering is the least portable part of the mod**, so isolate it behind our own interfaces.

---

## 6. Data-driven content [src + doc]

| Mechanism (1.21.1) | Loaded at | Reloads on `/reload` | Client sync | Parent templates |
|---|---|---|---|---|
| Datapack registry: `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec[, networkCodec[, Consumer<RegistryBuilder<T>>]])` **(corrected signature)** | world load only ([docs](https://docs.neoforged.net/docs/1.21.1/concepts/registries); `WorldLoader` is the only loader [src]) | **no** | automatic if `networkCodec` is given | not possible before decoding |
| Reload listener: `AddReloadListenerEvent#addListener`, e.g. `SimpleJsonResourceReloadListener(Gson, dir)` with `apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)` | world load and `/reload` | yes | manual payload on `OnDatapackSyncEvent` (one player at login, all players on reload) | yes, we control parsing |

Recommended template pipeline [inf]:

1. Scan `data/<ns>/strategos/templates/**.json`.
2. Resolve `"parent"` chains and detect cycles.
3. Deep-merge objects: the child wins, and arrays replace unless an explicit operation is given (`$append`, `$remove`, numeric `$mul`/`$add`), in the spirit of 0 A.D.'s template merge operations.
4. Decode with `Codec#parse(RegistryOps.create(JsonOps.INSTANCE, event.getRegistryAccess()), json)`, honouring NeoForge conditions through `getConditionContext()` (or `makeConditionalOps()`, which `SimplePreparableReloadListener` inherits from NeoForge's `ContextAwareReloadListener` in 1.21.1). Report every error with its file path.
5. Build an immutable, versioned `TemplateSet` and swap it in atomically on the main thread.
6. Sync it on `OnDatapackSyncEvent` with a compact `StreamCodec` plus a hash, so clients can verify they have the same set.

Where datapack registries still fit:

- Use them only for stable, tag-heavy definitions (civilizations, damage classes).
- NeoForge prefixes modded registry folders with the namespace:
  - elements go in `data/<ns>/<regns>/<regpath>/`
  - tags go in `data/<ns>/tags/<regns>/<regpath>/`
  - see `Registries#elementsDirPath`/`tagsDirPath` [src]
- For unit-class counters, 0 A.D.-style class tokens inside templates are simpler than Minecraft tags.

**Changes in 26.x [doc].**

- `SimpleJsonResourceReloadListener` became codec-based (1.21.2 and 1.21.4).
- NeoForge renamed `AddReloadListenerEvent` to `AddServerReloadListenersEvent` already in 21.4 (MC 1.21.4) **(corrected: not 26.3)**, and `DataPackRegistryEvent.NewRegistry` to `NewDatapackRegistryEvent` in 26.3 [src].
- Vanilla 26.3 adds *reloadable* datapack registries for a hard-coded list of vanilla registries, and NeoForge 26.3 exposes `NewDatapackRegistryEvent#reloadableRegistry(...)`, so mod registries can be reloadable there too **(corrected)**.

---

## 7. Persistence [src + doc]

- **`SavedData` (1.21.1).**
  - Implement `save(CompoundTag, HolderLookup.Provider)`. Create it with `new SavedData.Factory<>(Supplier<T>, BiFunction<CompoundTag, HolderLookup.Provider, T>, @Nullable DataFixTypes)` and `level.getDataStorage().computeIfAbsent(factory, "strategos_world")`. Keep global state on the overworld.
  - NeoForge copies the data and writes it on an IO worker (21.1.118), and cleans up temp files asynchronously (21.1.146). The NBT serialisation itself still runs on the main thread.
  - Autosave runs every 6,000 ticks. Split data by subsystem and region, and `setDirty()` only what changed.
- **Data attachments.**
  - `AttachmentType.builder(...).serialize(Codec)`, optionally `.copyOnDeath()` and `.sync(...)`, on entities, chunks, levels and block entities. Level attachments persist through `LevelAttachmentsSavedData`.
  - `setData` marks chunks dirty; in-place mutation needs `setUnsaved(true)` ([docs](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments)).
  - Chunk attachments exist only while the chunk is loaded, so don't use them for map-wide data the minimap or AI needs.
- **Entity NBT.**
  - `addAdditionalSaveData`/`readAdditionalSaveData(CompoundTag)`.
  - Keep hot unit state in plain fields, which is cheaper than attachment map lookups [inf].
- **Versioning [inf].**
  - Every blob carries a `"v"` field. Migrations run on raw NBT/JSON before codec decoding.
  - Old-save fixtures are loaded in GameTests.
  - Vanilla DataFixers aren't practical for mod data; NeoForge accepts a `null` `DataFixTypes`.
- **Changes in 26.x [doc].**
  - 1.21.5: `SavedDataType(id, ctor, codec, dataFix)`.
  - 1.21.6: `ValueInput`/`ValueOutput` replace direct `CompoundTag` access for entities and block entities.
  - 26.1: `SavedDataStorage` with `Identifier` paths, plus server-global saved data.
  - Writing every format as a `Codec` now makes those ports mechanical.

---

## 8. Buildings in a voxel world

- **Templates [src].**
  - `StructureTemplateManager#getOrCreate(ResourceLocation)` loads `data/<ns>/structure/<path>.nbt`. The 1.21.1 folder name is `structure`; generated templates go to `<world>/generated/<ns>/structures`.
  - Available API: `StructureTemplate#fillFromWorld(Level, BlockPos, Vec3i, boolean withEntities, @Nullable Block ignore)`, `placeInWorld(ServerLevelAccessor, BlockPos offset, BlockPos pos, StructurePlaceSettings, RandomSource, int flags)`, `filterBlocks`, `getSize(Rotation)`, `transform`.
  - The block list (`palettes`) is private. Use an access transformer, or parse the NBT (`size`, `palette`, `blocks[pos,state,nbt]`) into our own immutable `Blueprint`.
- **Progressive construction [src + inf].**
  - Precompute the build order:
    1. Y ascending.
    2. Supporting blocks before attached ones; torches, doors, beds and tall plants are placed as pairs.
    3. Block entities last, with their NBT.
  - A builder's work rate becomes blocks per tick.
  - `level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)` (flags 2|16) avoids neighbour cascades, because the template already stores final connected states. Finish with a shape-update pass along the boundary.
  - Vanilla batches changes per section per tick (`ChunkHolder` → `ClientboundSectionBlocksUpdatePacket`).
  - Each placement also runs `sendBlockUpdated`'s loop over `navigatingMobs` and marks our nav grid dirty, so budget placements per tick globally.
- **Foundations [inf].** Validate the footprint against `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES` (maximum height difference), fill columns down to the ground, clear above, and optionally grade a one-block apron, all as budgeted jobs.
- **Protection [src].**
  - `BlockEvent.BreakEvent` and `EntityPlaceEvent`: cancel, or turn into building damage.
  - `ExplosionEvent.Detonate`: prune `getAffectedBlocks()` and convert the removed blocks to damage.
  - `LivingDestroyBlockEvent`, `EntityMobGriefingEvent`, `PistonEvent.Pre`, `BlockEvent.FluidPlaceBlockEvent`.
  - Fire removes blocks without an event (it only calls the burning block's own `onCaughtFire` hook), so use non-flammable building blocks or a mixin.
- **Building HP.**
  - Reign of Nether derives HP from the blocks present [src]:
    - 2 HP per block by default, with optional fixed HP per building type.
    - A finished building is destroyed once ≤ 50 % of its blocks remain.
    - When a player mines building blocks, there is a chance of small explosions that destroy more.
  - Alternative [inf]: abstract, AoE-style HP, with deterministic removal of visual blocks at damage thresholds and re-placement on repair. It is easier to balance and sync.
- **Detecting player edits [inf].** Use the same block-change hook as the nav grid, look the position up in a chunk → building spatial index, and update a per-building placed-block set incrementally. Reign of Nether keeps `placedBlockPosSet`.

---

## 9. Teams and ownership [src]

- **Scoreboard teams.**
  - `PlayerTeam` offers `setColor(ChatFormatting)` (only 16 colours), `setAllowFriendlyFire`, `setSeeFriendlyInvisibles`, `setCollisionRule` and `setNameTagVisibility`.
  - Membership is keyed by scoreboard name, which is the UUID string for non-players (`Scoreboard#addPlayerToTeam`), and is synced to every client. Mirroring units into teams therefore leaks their UUIDs, and so their existence, past fog of war **(added)**.
  - `Entity#getTeam` looks up the scoreboard, `getTeamColor` drives the outline colour, and `EntitySelector.pushableBy` reads the collision rules.
  - The friendly-fire flag is only consulted in `Player#canHarmPlayer`, i.e. player versus player. Vanilla `Projectile#canHitEntity` ignores teams.
- **Recommendation [inf]: our own faction system.**
  - Each unit and building carries a faction id; the diplomacy matrix lives in `SavedData`.
  - Override `isAlliedTo`, `getTeamColor` (any RGB) and `broadcastToPlayer`.
  - Enforce friendly fire in `LivingIncomingDamageEvent` and `ProjectileImpactEvent`.
  - Optionally mirror factions into scoreboard teams for vanilla UX (coloured names, `/team`), but never depend on them, and mirror only players, not units, when fog of war matters.

---

## 10. Debug and testing

- **Commands [src].**
  - `RegisterCommandsEvent` (game bus) gives `getDispatcher()`, `getBuildContext()` and `getCommandSelection()`. Use it for `/strategos …` server commands with permission levels.
  - `RegisterClientCommandsEvent` covers client-only toggles.
- **Dev-only gating [src].**
  - Check `FMLEnvironment.production` (FML 4.0.45 in this MDK).
  - Vanilla 1.21.1 debug renderers exist but are inert: `DebugPackets.sendPathFindingPacket` is empty, and `DebugRenderer#render` only draws chunk borders and GameTest markers.
  - Build our own overlay in `RenderLevelStageEvent`: nav-grid cells, paths, flow arrows, the fog grid, and each unit's LOD tier. Feed it through an `.optional()` dev payload.
  - `DebugRenderer.renderFilledBox` and `renderFloatingText` are reusable helpers.
  - 1.21.9 adds `DebugSubscription`s [doc].
- **Tests.**
  - NeoForge GameTest [src]: `@GameTestHolder`, `@PrefixGameTestTemplate`, `RegisterGameTestsEvent`; the MDK `gameTestServer` run with `neoforge.enabledGameTestNamespaces` ([docs](https://docs.neoforged.net/docs/1.21.1/misc/gametest)).
  - JUnit with a live server [doc]: ModDevGradle `unitTest { enable(); testedMod = mods.strategos }` plus `net.neoforged:testframework` with `@ExtendWith(EphemeralTestServerProvider.class)` ([ModDevGradle README](https://github.com/neoforged/ModDevGradle)).
  - Keep the simulation core in plain Java so it can be unit-tested without Minecraft.
  - 1.21.5 overhauled GameTest into registries [doc], so budget a port.

---

## Architecture implications: the ten constraints that matter most

1. **One 50 ms server thread; units get about 20 ms.**
   - Technique: a stripped `PathfinderMob`, LOD tiers, staggered thinking, per-subsystem time budgets, and MSPT regression tests from week 1.
   - Why: vanilla-AI mobs cost about 20–30 µs each (unverified estimate), so without this the budget is gone at a few hundred units. NeoForge adds per-entity event posts on top (`EntityTickEvent.Pre/Post`, `MobDespawnEvent`, `LivingBreatheEvent`).
2. **Cost in dense groups is quadratic.**
   - Technique: `isPushable=false`, a no-op `pushEntities`, and our own spatial hash with ORCA or boids separation.
   - Why: pushing, entity-collision queries and cramming scale with neighbour count. Armies clump, and vanilla also deals cramming damage when an entity overlaps 24+ other pushables.
3. **Vanilla pathfinding is local, per-unit and synchronous.**
   - Technique: our own nav grid, HPA*, flow fields and async A* workers. Vanilla `PathNavigation` only as an adapter for following our paths.
   - Why: 0.1–0.5 ms per search, multiplied by group orders, with a short range and storms of block-change invalidations.
4. **World access is main-thread only.**
   - Technique: workers read only immutable snapshots (our grid, `PalettedContainer#copy`); results are applied on the main thread with sequence numbers.
   - Why: off-thread `getChunk` blocks on the server thread, `getChunkNow` returns null, and vanilla caches aren't thread-safe (the level's `PathTypeCache` is shared by every mob).
5. **The world only simulates near players.**
   - Technique: a data-first model in which `SavedData` records are authoritative and entities are views; abstract off-screen movement; a cap on follow-tickets; bounded, pregenerated maps.
   - Why: units freeze outside entity-ticking chunks and are saved away on unload, and empty levels stop ticking entities after 300 ticks unless they hold `/forceload` or `TicketController` chunks (custom ticket types don't count).
6. **Replication is per entity and per player.**
   - Technique:
     - turn velocity updates off and use an update interval of 3–4
     - filter fog of war with `broadcastToPlayer` plus forced re-evaluation
     - add a coarse batched minimap channel
     - build a custom delta channel only if measurement demands it
   - Why: about 0.35–0.55 Mbit/s per client at 300 moving units (computed upper bound), and visibility isn't re-evaluated on its own in vanilla.
7. **Chunk loading and tracking are centred on the player entity, not the camera.**
   - Technique: the commander's player follows the camera, a `Camera` mixin, the cursor freed via the cancelable Pre event, a raised entity render distance, and Retina-correct picking.
   - Why: the server sends chunks and entities around the player.
8. **Rendering APIs churn and must coexist with Sodium and Iris.**
   - Technique: few custom draws, vanilla `RenderType`s, NeoForge events, an isolated client module, and a test matrix of Sodium 0.8.x with Iris 1.8.14-beta.1 (and the stable pair Sodium 0.6.13 + Iris 1.8.12).
   - Why: eight rendering reworks between 1.21.2 and 26.3, and Sodium only dispatches some of the hooks.
9. **Data reload semantics differ between mechanisms.**
   - Technique: templates through a reload listener with our own inheritance and a codec pass; datapack registries only for stable definitions; explicit client sync.
   - Why: 1.21.1 datapack registries neither reload nor support parent templates.
10. **Save formats and vanilla team semantics will bite.**
    - Technique: codec-first persistence, schema versions with migrations, fixture-save tests, and our own faction model instead of scoreboard teams.
    - Why: 1.21.5–26.1 replace `SavedData`, NBT access and `ResourceLocation`. Scoreboard teams have 16 colours and string keys, are broadcast to every client, and projectiles ignore them.

---

## Open questions (carried over; updated by the fact-check)

1. Actual per-tick cost on the M4 Pro of a stripped PathfinderMob versus a LivingEntity-based unit (idle, moving, fighting). The 20–30 µs vanilla figure and the ~10 µs stripped target are extrapolations from third-party benchmarks on other CPUs, MC versions and mod stacks. Include NeoForge's per-entity event posts in the measurement.
2. Client FPS with 300–1,000 animated humanoid units (vanilla models versus GeckoLib 4.9.x; current NeoForge 1.21.1 build 4.9.3, MIT) under Sodium 0.8.x on Apple silicon. No credible public benchmark found.
3. **Partly answered from source:** Moonrise's 1.21.1 tracker keeps vanilla `TrackedEntity#updatePlayer`, so `Entity#broadcastToPlayer` semantics are preserved and re-evaluated every tick for nearby players; access-transformer calls to `updatePlayer` still work, mixins into the `ChunkMap#move`/`ChunkMap#tick` loops would conflict. Still to test in a running game.
4. Behaviour of the vanilla glowing-outline post-chain and of custom RenderLevelStageEvent geometry under Iris shader packs on NeoForge 1.21.1.
5. Feasibility and cost of terrain-level fog-of-war secrecy (gating chunk/block packets and serving fogged snapshots, as Reign of Nether does) for maps larger than about 512×512 on 1.21.1. Note that Reign of Nether itself only enables fog on maps with a world border ≤ 1,280 blocks.
6. Whether multiplayer over the internet is a first-class target (bandwidth design) or the product starts with singleplayer/LAN versus AI. This is a product decision that changes how much custom replication is needed.
7. Perspective versus orthographic RTS camera: UX preference, plus the frustum/culling side effects with Sodium 0.8 on NeoForge (Reign of Nether's experience is on Forge 1.20.1).
8. **Partly answered:** NeoForge 26.3.x's `ChunkMap` patch still shows `entityMap`, `TrackedEntity.seenBy` and `updatePlayer(player)`; whether 26.x keeps `Entity#broadcastToPlayer` unchanged is unverified. Relevant if the project later migrates to 26.x.
9. Exact memory and CPU cost of a per-chunk nav grid over a Y band for a 1–2k block map, and the right cell encoding (bits per cell, layered columns for caves and bridges). Needs a prototype measurement.
10. **New:** whether Moonrise's replacement chunk system honours NeoForge `TicketController` tickets and the `forceTicks` patch exactly as vanilla+NeoForge do (not checked in source).

---

## Verification notes

**Method.** Every key claim marked time-sensitive or decision-critical, and every other statement that could change a decision, was re-checked on 2026-10-20 against primary sources: NeoForge Maven metadata and POM `Last-Modified` headers; the 21.1.264 changelog; the Modrinth v2 API (project licences and NeoForge 1.21.1 version lists for Lithium, Sodium, Iris, spark, Moonrise (`moonrise-opt`), Fabric API, Reign of Nether, Async, Laminar, GeckoLib) and Fabric's Maven; GitHub (branches, commits, licence fields and raw files for Moonrise `mc/1.21.1`, Reign of Nether `1.20.1-dev`, MineColonies `version/1.21`, Baritone `1.21.1`, Sodium `1.21.1/stable`, Iris `1.21.1`, Lithium `1.21.1`, RTSbuilding, Freecam, RVO2, PathWeaver, NeoForge `1.21.1`…`26.3.x`, the NeoForged primers 1.21.2–26.3, 0 A.D.); NeoForge docs, the ModDevGradle README and spark docs; and the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 tree in `scratchpad/mc-src-1.21.1` (read-only; class, method, field and event names and behaviour checked line by line). The MDK template was only read.

**Confirmed without change** (selection): NeoForge 21.1.264/21.1.262 dates and backport status; all other version/licence rows except Moonrise, Iris and Baritone; `Mob#serverAiStep` structure; idle movement and collision cost; `sendBlockUpdated` loop; despawn-before-range-gate; node cap, weighted A*, partial paths and live-region semantics; off-thread chunk access behaviour and `PalettedContainer` copying; ticket levels, `TicketController` distance 2 and persistence; tracker range and update cadence; packet sizes and the bandwidth arithmetic; attachment-sync history (the 21.1.262 initial sync indeed ignores `sendToPlayer`); NeoForge networking, camera, render-stage, GUI-layer, outline, persistence, structure, protection, command and test APIs; Reign of Nether and MineColonies internals; Sodium's NeoForge render hooks and the Iris API; the 26.x primer timeline for tickets, rendering and persistence; HPA*, flow-field (Elijah Emerson, Supreme Commander 2), Factorio and 0 A.D. references.

**Corrected in place:**
- Moonrise optimises rather than replaces the entity tracker; it calls vanilla `updatePlayer` every tick for nearby players (§0, §1.1, §4.4, open question 3).
- Baritone's `1.21.1` branch was deprecated on 2026-09-01 (§0, §2.4).
- Iris 1.8.14-beta.1 is a beta; Sodium 0.8 ↔ Iris beta, Sodium 0.6.13 ↔ Iris 1.8.12 (§0, §5.3, constraint 8).
- PathWeaver: the two benchmark runs had been merged; the spark-profile 15.5 ms is the whole tick, pathfinding was ≈6.2 ms (40 %); the off-thread share is ≈60 %, not 3/4; "pathfinding is normally a minority" is not supported (§1.2, §2.4).
- Async ran with a full optimisation pack and is GPL-3.0-or-later; Laminar's MC version is not stated (§1.2).
- Capacity arithmetic: 20–30 µs per mob implies ~1,700–2,500 mobs per 50 ms, not 1,000–1,500; all capacity numbers marked unverified (§1.2).
- `MovementEmission.SOUNDS` still emits `GameEvent.STEP`; use `NONE` (§1.4).
- Invulnerability: in-window stronger hits deal only the difference and do not reset the timer (§1.4).
- Vanilla entity cull distance includes a render-distance factor (≈100 blocks at default settings, not 67) (§1.4).
- `ChunkMap#tick` runs before, not after, the entity loop (§1.1).
- `ticking = true` also enables natural mob spawning; custom `TicketType` tickets don't prevent the 300-tick empty-level stop (§1.1, §3.1, §3.2, constraint 5).
- Custom-payload caps apply to unknown payloads only; registered payloads are bounded by frame limits (§4.1).
- NeoForge `BlockEvent` coverage; `onBlockStateChange` caveats (§2.3).
- Lithium's pathing mixin is off by default on NeoForge 1.21.1 (§2.4).
- `AddReloadListenerEvent` was renamed in NeoForge 21.4, not 26.3; NeoForge 26.3 supports reloadable mod datapack registries; `dataPackRegistry` takes a `Consumer<RegistryBuilder<T>>` (§6).
- `CalculatePlayerTurnEvent` cannot cleanly zero turning; `setScreen(null)` re-grabs the mouse (§5.1).
- HPA* PDF link returns 404; full citation given (§2.2).

**Added facts** (not errors, but they change how passages read): NeoForge per-entity event posts (`MobDespawnEvent`, `LivingBreatheEvent`, `EntityTickEvent.Pre/Post`); NeoForge's `PartEntity` scan on every entity query; the level-shared `PathTypeCache`; dirty `SynchedEntityData` forcing position updates; scoreboard-team membership leaking unit UUIDs; `AddSectionGeometryEvent` renderers running off-thread; Reign of Nether's fog-of-war limit (world border ≤ 1,280 blocks); RTSbuilding's extra user terms and asset licences; the projection also containing hurt-cam and nausea transforms.

**Still unverified:** all per-unit cost and capacity extrapolations (§1.2, constraint 1); the bandwidth conclusion for 300–500 units over the internet (§4.3); the claim that `Mob` overhead is small next to movement (§1.3); `broadcastToPlayer` on 26.x; Moonrise's handling of NeoForge tickets; behaviour under Iris shader packs.

## Recommendations (after verification)

1. 1. Before any features, build a performance harness: a GameTest or dev-command world that spawns N units (idle, moving, fighting, group-ordered) and records MSPT with /tick sprint, spark and /neoforge track. Gate CI on regressions. Use it in week 1 to choose PathfinderMob vs LivingEntity as the unit base. Treat the report's per-unit figures (20–30 µs vanilla, ~10 µs stripped, 600–800 / 1,500–2,000 units) as unverified extrapolations. Measure NeoForge's own per-entity event posts too (EntityTickEvent.Pre/Post, MobDespawnEvent, LivingBreatheEvent), and run the matrix with and without Lithium and Moonrise.
2. 2. Implement units as a stripped PathfinderMob subclass:
- no goals or brain; controller in customServerAiStep, staggered by (tickCount+id)
- isPushable=false and a no-op pushEntities override (without it the neighbour query still runs every tick)
- persistent, with checkDespawn overridden as a no-op (this also skips NeoForge's per-tick MobDespawnEvent); no loot pickup
- getMovementEmission() returns NONE (SOUNDS still emits GameEvent.STEP on ordinary ground)
- STEP_HEIGHT 1.0; a custom MoveControl
- velocity updates off; updateInterval 3–4; keep SynchedEntityData changes rare, because a dirty value forces a full position update that tick
- shouldRenderAtSqrDistance raised (vanilla culls at avg AABB side × 64 × clamp(renderDistance/8, 1, 2.5) × Entity Distance, ≈100 blocks at defaults)
- invulnerability frames neutralised via LivingIncomingDamageEvent / DamageContainer#setPostAttackInvulnerabilityTicks(0) (inside the window, stronger hits deal only the difference)
- no PartEntity-based multipart units (NeoForge scans every part on each entity query)
3. 3. Adopt a data-first architecture. A plain-Java simulation core (factions, economy, orders, unit/army/building records) is persisted in SavedData through Codecs with schema versions. Entities are views materialised only in entity-ticking chunks. This keeps the core unit-testable without Minecraft and survives the 1.21.5/1.21.6/26.1 persistence changes.
4. 4. Write our own pathfinding stack instead of vanilla PathNavigation:
- a compact per-chunk nav grid built on the main thread with a per-tick budget, with copy-on-write snapshots and invalidation from a LevelChunk#setBlockState mixin (catches direct chunk writes too) or ServerLevel#onBlockStateChange (deferred during player block placement, misses direct chunk writes)
- an async A* worker pool reading only snapshots, with results applied on the main thread by sequence number; workers never touch the level-shared PathTypeCache
- then HPA* clusters, flow fields for group orders, ORCA separation (port RVO2, Apache-2.0, keeping its licence/NOTICE) and formation slots
- don't count on Lithium for vanilla pathing speed: its pathing mixin is off by default in the NeoForge 1.21.1 build
5. 5. Use bounded, pregenerated match maps (world border plus /neoforge generate). Move off-screen armies and traders abstractly in the data layer. Allow ticket-following only for a capped number of groups that must physically simulate:
- a custom TicketType with a lifespan, re-placed every few ticks at distance 3, in levels that always have a player
- a NeoForge TicketController in levels that may have no players, because custom ticket types don't stop the 300-tick empty-level shutdown
- ticking=false unless random ticks are wanted: ticking=true also enables natural mob spawning in that chunk
6. 6. Networking:
- rely on vanilla tracking tuned per EntityType
- implement fog of war via Entity#broadcastToPlayer on our classes plus a periodic re-evaluation pass (access transformer on ChunkMap.entityMap and ChunkMap$TrackedEntity, reached via ServerChunkCache#chunkMap). Under Moonrise the pass is redundant but harmless, because Moonrise calls updatePlayer every tick
- add a batched, fog-filtered 1–2 Hz minimap/army payload and event-driven order payloads; registered NeoForge payloads are limited by the 2 MiB compressed / 8 MiB uncompressed frame limits, and GenericPacketSplitter handles larger ones between NeoForge peers
- build a custom delta movement channel only if measurements exceed roughly 2 Mbit/s per client
- require NeoForge ≥ 21.1.264 if using predicate-filtered attachment sync
7. 7. Camera: make the commander's player entity follow the camera (a spectator-like or flying invisible mode) so chunk loading and tracking follow it.
- Use a Camera#setup mixin.
- Start with a perspective projection, which avoids the orthographic Frustum#offsetToFullyIncludeCameraCube loop.
- Free the cursor by cancelling InputEvent.MouseButton.Pre, and release it again after any screen closes, since Minecraft#setScreen(null) re-grabs it.
- Neutralise movement with MovementInputUpdateEvent; CalculatePlayerTurnEvent cannot cleanly zero turning, but a released cursor already stops vanilla turning.
- Do Retina-correct unprojection (Window#getScreenWidth/Height) with matrices captured in RenderLevelStageEvent.
8. 8. Keep client rendering minimal and isolated behind our own interfaces:
- batched vanilla RenderTypes in RenderLevelStageEvent
- AddSectionGeometryEvent only for static overlays; gather data in the main-thread handler, because the renderers run on the chunk-build thread
- outlines via isCurrentlyGlowing/getTeamColor overrides
- HUD via RegisterGuiLayersEvent
- test matrix: Sodium 0.8.13 + Iris 1.8.14-beta.1 (a beta, the only Iris build for Sodium 0.8 on NeoForge 1.21.1), plus the stable pair Sodium 0.6.13 + Iris 1.8.12; Lithium 0.15.4; optionally Moonrise 0.1.0-beta.15 (GPL-3.0, beta, last 1.21.1 build 2025-10-10)
9. 9. Data-driven content: put unit/building/tech templates in a reload listener with our own parent resolution and deep merge (0 A.D.-style operators). Decode them with Codecs and RegistryOps through makeConditionalOps()/getConditionContext() so NeoForge conditions apply, and sync them to clients on OnDatapackSyncEvent with a hash. Use datapack registries (dataPackRegistry(key, codec, networkCodec[, Consumer<RegistryBuilder<T>>])) only for stable, tag-heavy definitions. For a later 26.x port, AddServerReloadListenersEvent has existed since NeoForge 21.4, and NeoForge 26.3 can make mod datapack registries reloadable.
10. 10. Buildings: parse vanilla .nbt structures into our own immutable Blueprint. Construct progressively with a precomputed order and flags UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE under a global per-tick placement budget. Protect blocks via BlockEvent, ExplosionEvent, LivingDestroyBlockEvent, EntityMobGriefingEvent and PistonEvent; use non-flammable building blocks or a fire mixin, since fire posts no event. Start with abstract HP plus deterministic visual damage, and keep a chunk→building index for edit detection.
11. 11. Build our own faction system (ids, diplomacy matrix, any RGB colour) with overrides of isAlliedTo, getTeamColor and broadcastToPlayer, and friendly-fire enforcement in LivingIncomingDamageEvent and ProjectileImpactEvent. Mirror factions into scoreboard teams only for cosmetics, and only for players: team membership is broadcast to every client, so mirroring units would leak them past fog of war.
12. 12. Ship dev tooling early: /strategos debug commands, a dev-only overlay (nav grid, paths, flow fields, fog grid, LOD tier per unit) gated by FMLEnvironment.production and fed by an optional dev payload, plus JUnit via ModDevGradle unitTest and NeoForge testframework (net.neoforged:testframework 21.1.264 is published).
13. 13. Licensing: decide the project licence early. Do not copy GPL-3.0 code from Reign of Nether or MineColonies unless Strategos is GPL-compatible, since the distributed combined work would be GPLv3. Study their designs. Port RVO2 (Apache-2.0, keep licence and NOTICE, mark changes) and PathWeaver ideas (MIT) with attribution. Treat Baritone's 1.21.1 branch (deprecated 2026-09-01) and RTSbuilding (LGPL-3.0 code plus extra user terms and separate asset licences) as design references only.

## Open questions (from the original research)

- Actual per-tick cost on the M4 Pro of a stripped PathfinderMob versus a LivingEntity-based unit (idle, moving, fighting). The 20–30 µs vanilla figure and the ~10 µs stripped target are extrapolations from third-party benchmarks on other CPUs and MC versions.
- Client FPS with 300–1,000 animated humanoid units (vanilla models versus GeckoLib 4.9.x) under Sodium 0.8.x on Apple silicon. I found no credible public benchmark.
- Whether Moonrise's replacement entity tracker preserves Entity#broadcastToPlayer semantics, and whether access-transformer or mixin re-evaluation through ChunkMap$TrackedEntity works under Moonrise.
- Behaviour of the vanilla glowing-outline post-chain and of custom RenderLevelStageEvent geometry under Iris shader packs on NeoForge 1.21.1.
- Feasibility and cost of terrain-level fog-of-war secrecy (gating chunk/block packets and serving fogged snapshots, as Reign of Nether does) for maps larger than about 512×512 on 1.21.1.
- Whether multiplayer over the internet is a first-class target (bandwidth design) or the product starts with singleplayer/LAN versus AI. This is a product decision that changes how much custom replication is needed.
- Perspective versus orthographic RTS camera: UX preference, plus the frustum/culling side effects with Sodium 0.8 on NeoForge (Reign of Nether's experience is on Forge 1.20.1).
- Whether 26.x still provides Entity#broadcastToPlayer and the same ChunkMap$TrackedEntity structure. Not checked; relevant if the project later migrates to 26.x.
- Exact memory and CPU cost of a per-chunk nav grid over a Y band for a 1–2k block map, and the right cell encoding (bits per cell, layered columns for caves and bridges). Needs a prototype measurement.

## Fact-check verdicts

confirmed: 34, corrected: 21, refuted: 1, unverifiable: 1

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | NeoForge for 1.21.1 is still actively maintained: 21.1.264 was published 2026-10-19 (the official MDK pins 21.1.262 from 2026-10-15), with continuing backports. | None. 21.1.263 (2026-10-18, 'Add enchanted loot events to 1.21') is also a backport; the newest line overall is 26.3.0.95 (2026-10-20). | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (latest 21.1.x = 21.1.264, <latest>26.3.0.95, lastUpdated 20261020022939); POM Last-Modified headers: 21.1.262 Thu 15 Oct 2026 01:43 GMT, 21.1.263 Sun 18 Oct 2026 13:35 GMT, 21.1.264 Mon 19 Oct 2026 03:40 GMT, 26.3.0.95 Tue 20 Oct 2026 02:29 GMT; https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt line 1; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/gradle.properties:21 (neo_version=21.1.262) |
| confirmed | In 1.21.1 Mob#serverAiStep is final; goal/target selectors do a full tick when (tickCount + id) is even and only tick running goals otherwise; customServerAiStep() is the only override point inside the AI step. | Precision only: the full selector tick also runs while tickCount <= 1; sendDebugPackets() is also overridable, and navigation/MoveControl/LookControl/JumpControl are replaceable objects (createNavigation, protected fields). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/Mob.java:773-812 |
| confirmed | Idle living entities still call Entity#move every tick (gravity), including an entity-section collision query; LivingEntity#pushEntities queries neighbours every tick and applies cramming damage at 24+ overlapping pushables, so dense armies cost O(n^2). | Precision: cramming triggers when an entity overlaps 24 or more OTHER pushable non-passenger entities (list.size() > maxEntityCramming-1), with a 1-in-4 chance per tick of 6 damage. Non-pushable entities are filtered by EntitySelector.pushableBy, but the getEntities query in pushEntities still runs unless pushEntities is overridden. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/LivingEntity.java:2383-2394, 2891-2917; .../net/minecraft/world/entity/Entity.java:901-904; .../net/minecraft/world/level/EntityGetter.java:63-75; .../net/minecraft/world/entity/EntitySelector.java:50-75; .../net/minecraft/world/level/GameRules.java:98-100 |
| confirmed | ServerLevel#sendBlockUpdated iterates every Mob in the level (navigatingMobs) for each block-shape change and may recompute paths synchronously, so construction costs O(blocks x mobs); Lithium's inactive_navigations mitigates this. | Nuance: only collision-shape changes trigger it; navigatingMobs holds every loaded (tracked) Mob; the per-mob shouldRecomputePath check is cheap and a synchronous search runs only for mobs whose active path passes near the changed block; a second change within 20 ticks defers the recompute. Lithium's mixin.entity.inactive_navigations is on by default in the NeoForge 1.21.1 build. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ServerLevel.java:1080-1110, 1766-1781; .../net/minecraft/world/entity/ai/navigation/PathNavigation.java:91-102, 424-434; https://raw.githubusercontent.com/CaffeineMC/lithium/1.21.1/lithium-neoforge-mixin-config.md lines 333-337 |
| corrected | A vanilla path request costs about 480-500 us of main-thread time in a maze. 1,024 zombies all retargeting every 6 ticks gave 83-97 ms mean ticks; staggered retargeting (~51 requests/tick) held 20 TPS with p95 59-67 ms on a 16-core desktop. | Two different runs were merged. Current 0.6.1 benchmark (measured 2026-07-31; 'synchronous' arm = vanilla pathing with Fabric API + Lithium): mean tick 88.5-96.6 ms, p99 832-958 ms, 480-500 us main-thread per request. The 83-87 ms mean (p95 738-766 ms) and the staggered ~51 req/tick result (20 TPS, p95 59-67 ms, p99 77-80 ms) come from an older harness with the compatibility gate cleared, on a 16-core/32-thread desktop. PathWeaver targets MC 26.1.1/26.1.2/26.2 (Fabric). | https://github.com/zimdin12/PathWeaver README.md (master) lines 3, 173-188, 354-376 |
| corrected | Vanilla-AI mobs cost roughly 20-60 us per tick depending on CPU (9,000 villagers + Lithium = 225 ms/tick on a Ryzen 9 7950X3D; 500 zombies = 31 ms median on an i7-4770K). This implies roughly 600-800 vanilla-AI units or 1,500-2,000 stripped units within a ~20 ms unit budget on the M4 Pro. | Data points are correct but weaker than stated: the Async run used 1.21.4 Fabric with a full optimisation pack (C2ME, FerriteCore, Lithium, ScalableLux, ServerCore, TT20, Very Many Players, Carpet), not just Lithium; Laminar's benchmark does not state the MC version (the mod targets Fabric 26.1-26.2). Both are total MSPT divided by mob count (25 us and ~62 us). Capacity figures are unverified extrapolations, and the report's '1,000-1,500 vanilla-AI mobs fill 50 ms' contradicts its own 20-30 us (that would be ~1,700-2,500). | https://api.modrinth.com/v2/project/async (body: 'Performance Comparison (9000 Villagers)': Lithium without Async 4.4 TPS / 225.4 MSPT; Test Configuration 7950X3D, 1.21.4; Mod List); https://api.modrinth.com/v2/project/laminar-engine (body: i7-4770K, 500 zombies, 32 chunks, median 31.1 ms, p95 50.5 ms; game_versions 26.1-26.2, loader fabric) |
| confirmed | Vanilla maxVisitedNodes = floor(FOLLOW_RANGE x 16) (256 by default) is fixed at construction. The heuristic is weighted x1.5, partial paths are returned, and the search region uses live chunk references where unloaded chunks read as air. | None (unloaded chunks become EmptyLevelChunk, which returns VOID_AIR; zombies get 560 nodes from FOLLOW_RANGE 35). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/ai/navigation/PathNavigation.java:62-67, 150-170; .../net/minecraft/world/level/pathfinder/PathFinder.java:21, 58-121; .../net/minecraft/world/level/PathNavigationRegion.java:48, 72-74; .../net/minecraft/world/level/chunk/EmptyLevelChunk.java:26; .../net/minecraft/world/entity/Mob.java:160; .../net/minecraft/world/entity/monster/Zombie.java:122 |
| confirmed | Off-thread world access is unsafe in 1.21.1: ServerChunkCache#getChunk off-thread joins on a main-thread future, getChunkNow returns null off-thread, the per-level PathTypeCache is plain arrays; vanilla's off-thread chunk meshing copies PalettedContainers on the main thread. | Addition: PathfindingContext makes every mob of a ServerLevel use that level's single PathTypeCache (two arrays written non-atomically), so worker searches must never touch it. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ServerChunkCache.java:136-139, 173-175; .../net/minecraft/world/level/pathfinder/PathTypeCache.java; .../net/minecraft/world/level/pathfinder/PathfindingContext.java:11-17; .../net/minecraft/server/level/ServerLevel.java:192; .../net/minecraft/client/renderer/chunk/RenderChunk.java:42; .../net/minecraft/world/level/chunk/PalettedContainer.java:36-43, 119-160, 290 |
| corrected | Entities tick only in entity-ticking chunks (level <= 31). NeoForge TicketController forced chunks use distance 2, so only the owner chunk is entity-ticking. A level stops ticking entities 300 ticks after it empties unless forced chunks exist (NeoForge counts its own tickets). | Core confirmed. Two corrections: (a) ForcedChunkManager.hasForcedChunks counts only /forceload and NeoForge TicketController chunks, so custom vanilla TicketType region tickets do NOT keep an empty level ticking; (b) ticking=true (forceTicks) runs the whole per-chunk tick without players: natural mob spawning as well as random ticks. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ChunkLevel.java:10-12, 40-60; .../net/minecraft/server/level/DistanceManager.java:194-201, 256-258, 294; .../net/neoforged/neoforge/common/world/chunk/ForcedChunkManager.java:64-71, 113-121; .../net/minecraft/server/level/ServerLevel.java:389-395; .../net/minecraft/server/level/ServerChunkCache.java:364-376 |
| confirmed | Entity#broadcastToPlayer gates entity tracking, but TrackedEntity#updatePlayer only re-runs on section change, player movement or spawn, so fog of war needs forced re-evaluation through ChunkMap internals, as Reign of Nether's TrackedEntity mixin does. | Confirmed for vanilla (callers are only ChunkMap#move, ChunkMap#addEntity and the section-change branch of ChunkMap#tick). Under Moonrise 1.21.1, updatePlayer runs every tick for every tracked entity and each nearby player, so the re-evaluation happens anyway. RoN's TrackedEntityMixin was last changed 2026-07-27 (default branch head 2026-10-14), not 2026-10-15. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ChunkMap.java:1024-1031, 1115-1130, 1151-1183, 1327-1345; https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/fogofwar/TrackedEntityMixin.java (commit 65df26b, 2026-07-27); https://github.com/Tuinity/Moonrise/blob/mc/1.21.1/src/main/java/ca/spottedleaf/moonrise/mixin/entity_tracker/TrackedEntityMixin.java |
| confirmed | With vanilla defaults (update interval 3, velocity tracked), a moving unit costs about 34 B per update (~227 B/s), i.e. ~0.54 Mbit/s per client for 300 moving units; velocity off gives ~0.35 Mbit/s. | Arithmetic checks out; it is an upper bound: the motion packet is sent only when velocity changes by more than ~3e-4 blocks/tick, while a dirty SynchedEntityData value or hasImpulse forces extra full updates between intervals. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ServerEntity.java:86-226 (lines 121, 150, 174, 223) |
| confirmed | Syncable data attachments were backported to NeoForge 21.1.197. The per-player sync predicate is honoured on initial sync only since 21.1.264 (2026-10-19). | None. | neoforge-21.1.264-changelog.txt lines 1 and 135; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/attachment/AttachmentSync.java:154-185 (21.1.262 initial sync never calls sendToPlayer) vs 85-112 (updates do) |
| confirmed | Sodium's NeoForge 1.21.1 stable branch dispatches NeoForge chunk-layer RenderLevelStageEvent stages and runs AddSectionGeometryEvent renderers; Iris exposes IrisApi#isShaderPackInUse for graceful degradation. | Addition: Iris 1.8.14-beta.1 is a beta ('Updates to Sodium 0.8'); the last stable NeoForge 1.21.1 Iris, 1.8.12 (2025-06-17), requires Sodium 0.6.13. | Sodium branch 1.21.1/stable head 272a4aa379 (2026-10-18), https://github.com/CaffeineMC/sodium/blob/1.21.1/stable/neoforge/src/mod/java/net/caffeinemc/mods/sodium/neoforge/level/NeoForgeLevelRenderHooks.java; https://raw.githubusercontent.com/IrisShaders/Iris/1.21.1/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java lines 16, 42, 63; Modrinth API iris versions (neoforge, 1.21.1) and /v2/version/Pb3OXVqC = mc1.21.1-0.6.13-neoforge |
| corrected | Vanilla culls entity rendering at average AABB side x 64 x Entity Distance (~67 blocks for a zombie-sized unit), so an RTS camera 40-80 blocks away needs a shouldRenderAtSqrDistance override; picking must normalise cursor coordinates by Window#getScreenWidth/Height (Retina). | The formula omits viewScale = clamp(effectiveRenderDistance/8, 1.0, 2.5) x entityDistanceScaling. For a zombie (0.6x1.95x0.6, avg side 1.05) the cull distance is ~67 blocks only at render distance <= 8, ~101 blocks at the default 12 chunks, and ~168 blocks at 20+ chunks. The override is still advisable. Retina normalisation confirmed (getWidth = framebuffer pixels, getScreenWidth = window coordinates). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/Entity.java:1692-1700; .../net/minecraft/client/renderer/LevelRenderer.java:856-857; .../net/minecraft/client/Options.java:126-131, 1108-1113, 1616-1618; .../com/mojang/blaze3d/platform/Window.java:425-447; .../net/minecraft/world/entity/EntityType.java:294 |
| confirmed | Server chunk loading and entity tracking are centred on the player entity, not the camera. Reign of Nether makes the spectator player the camera (orthographic projection via a GameRenderer#getProjectionMatrix mixin); RTSbuilding clamps a camera entity to a radius. | Detail: RTSbuilding's camera is a server-side RtsCameraEntity (extends Entity) anchored at the player with an action radius; its code is LGPL-3.0-only, with an extra user-terms document (License.md) and separate asset licences. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ChunkMap.java:1327-1336; RoN 1.20.1-dev mixin/OrthoViewMixin.java, CameraMixin.java, FrustumMixin.java; https://raw.githubusercontent.com/Hcrab/RTSbuilding/main/LICENSE, License.md, gradle.properties (mod_license=LGPL-3.0-only, minecraft_version=1.21.1), server/camera/RtsCameraManager.java lines 119-144, 389-392 |
| corrected | In 1.21.1, datapack registries load only at world load (not on /reload), whereas reload listeners reload. Vanilla 26.3 adds reloadable datapack registries only for specific vanilla registries. | 1.21.1 part confirmed. 26.3 part incomplete: vanilla's reloadable list (RegistryDataLoader#RELOADABLE_REGISTRIES) is hard-coded, but NeoForge 26.3.x adds NewDatapackRegistryEvent#reloadableRegistry(...), so mod registries can be reloadable on 26.3. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/WorldLoader.java:39, 76 (the only loader of datapack registries); https://docs.neoforged.net/docs/1.21.1/concepts/registries; https://github.com/neoforged/.github/blob/main/primers/26.3/index.md lines 1307-1309; https://github.com/neoforged/NeoForge/blob/26.3.x/src/main/java/net/neoforged/neoforge/registries/NewDatapackRegistryEvent.java lines 91-126 |
| confirmed | Rendering APIs changed substantially in 1.21.2, 1.21.5, 1.21.6, 1.21.9, 1.21.11, 26.1, 26.2 (Vulkan backend) and 26.3 (Renderpearl), so client rendering code is the least portable part of the mod. | None. | NeoForged primers: 1.21.2 'Entity Render States'; 1.21.5 'Render Pipeline Rework'; 1.21.6 'GUI Changes' (GuiRenderState); 1.21.9 'Feature Submissions' and 'The Debugging Overhaul'; 1.21.11 'The RenderType Shuffle' and 'ResourceLocation to Identifier'; 26.1 CameraRenderState (line 2434); 26.2 'Vulkan' (lines 22-24); 26.3 'Renderpearl' (lines 66-68) — https://github.com/neoforged/.github/tree/main/primers |
| confirmed | Persistence APIs change after 1.21.1: SavedDataType with codecs (1.21.5), ValueInput/ValueOutput instead of CompoundTag for entities (1.21.6), ResourceLocation->Identifier (1.21.11), SavedDataStorage with Identifier paths (26.1). | None. | primers 1.21.5/index.md lines 1274-1300; 1.21.6/index.md lines 1457-1481; 1.21.11/index.md lines 23-25; 26.1/index.md lines 1828-1860, 1941 |
| confirmed | Reign of Nether (GPL-3.0, Forge 1.20.1 only, 1.5.0b on 2026-10-14) implements a custom walkability grid and worker-pool pathfinder, server-side fog gating, block-count building health and a default 150 population per player; MineColonies 1.21.1 uses a single pathfinding worker over live chunk references. | Addition: RoN enables fog of war only when the world border is <= 1280 blocks (RTS_OPTIMIZED_BORDER). | Modrinth API reign-of-nether-rts versions (1.5.0b 2026-10-14, loaders [forge], all game versions 1.19.1/1.19.2/1.20.1); RoN 1.20.1-dev: unit/pathfinding/WalkabilityGrid.java lines 24-33, PathfinderWorkerPool.java line 43, RtsPathfinder.java lines 19, 52-58, registrars/GameRuleRegistrar.java lines 44, 116-122, resources/ResourceCosts.java:215, building/Building.java lines 37-41, 61, BuildingPlacement.java lines 147, 158, worldborder/WorldBorderServerEvents.java lines 26, 33-35; MineColonies version/1.21 head dae860e (2026-10-06): core/entity/pathfinding/Pathfinding.java lines 17, 50, world/ChunkCache.java lines 46, 81 |
| corrected | Vanilla invulnerability frames (hits <= last ignored while invulnerableTime > 10, reset to 20) silently drop focus-fire damage; DamageContainer#setPostAttackInvulnerabilityTicks or the minecraft:bypasses_cooldown tag fix it. | Inside the window a hit <= lastHurt is dropped and a stronger hit deals only (amount - lastHurt) WITHOUT resetting the timer; only a hit outside the window sets the timer to getPostAttackInvulnerabilityTicks() (default 20). The LivingIncomingDamageEvent fix works (the event fires before the check). A bypasses_cooldown hit still sets the timer to 20 against later non-bypassing hits. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/LivingEntity.java:473-477, 1153, 1190-1206; .../net/neoforged/neoforge/common/damagesource/DamageContainer.java:62, 130-136; .../net/minecraft/tags/DamageTypeTags.java:12 |
| confirmed | Scoreboard team colours are limited to 16 ChatFormatting values; the friendly-fire flag only applies player-vs-player; vanilla projectiles ignore teams; a custom faction system with overrides is needed. | Addition: team membership (keyed by UUID string for non-players) is broadcast to every client, so mirroring units into teams leaks their existence past fog of war. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/player/Player.java:976; .../net/minecraft/world/entity/projectile/Projectile.java:257-264; .../net/minecraft/world/entity/Entity.java:281-284, 2437-2439, 2843-2845 |
| confirmed | GPL-3.0 projects (Reign of Nether, MineColonies) cannot be copied into a non-GPL Strategos; RVO2 (Apache-2.0), PathWeaver (MIT) and Baritone (LGPL-3.0, with conditions) are usable references. | Precision: copying GPL-3.0 code makes the distributed combined work GPLv3 (own files may stay MIT/Apache-compatible, the whole is GPLv3). Apache-2.0 ports must keep the licence and NOTICE text and mark changes. | GitHub API licence fields: snape/RVO2 Apache-2.0, zimdin12/PathWeaver MIT, cabaletta/baritone LGPL-3.0, SoLegendary/reignofnether GPL-3.0, ldtteam/minecolonies GPL-3.0 |
| corrected | Moonrise (NeoForge 1.21.1, 0.1.0-beta.15, 2025-10-10, GPL-3.0) replaces the chunk system and the entity tracker. | Version, date and licence confirmed. It rewrites the chunk system but only optimises the entity tracker: vanilla ChunkMap$TrackedEntity, updatePlayer and broadcastToPlayer are kept; Moonrise turns off the loop in ChunkMap#move and calls updatePlayer every tick for each tracked entity and each player in view distance. The Modrinth slug 'moonrise' is an unrelated MIT project; the right one is 'moonrise-opt'. | https://raw.githubusercontent.com/Tuinity/Moonrise/mc/1.21.1/README.md ('Chunk system rewrite', 'Entity tracker optimisations'); mc/1.21.1 src/main/java/ca/spottedleaf/moonrise/mixin/entity_tracker/ChunkMapMixin.java (skipMoveTrackerUpdate, newTrackerTick) and TrackedEntityMixin.java (moonrise$tick -> updatePlayer); Modrinth API moonrise-opt (GPL-3.0-only, 0.1.0-beta.15+2eae1b1, 2025-10-10, neoforge 1.21/1.21.1) |
| corrected | Baritone: 1.21.1 branch, commit 2026-09-01; client-side A* with coefficients {1.5..10}, primary/failure timeouts, segmented paths, ClientChunkCache reads, cached 512x512 regions at 2 bits per block. | The 2026-09-01 commit is 'deprecate 1.21.1': the branch is deprecated and gets no further updates; primary development is on 1.21.4. Technical details confirmed (COEFFICIENTS {1.5, 2, 2.5, 3, 4, 5, 10}). | https://github.com/cabaletta/baritone/commit/11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7; README.md on branch 1.21.1; src/main/java/baritone/pathing/calc/AbstractNodeCostSearch.java lines 69, 100; cache/CachedRegion.java line 52; cache/CachedChunk.java lines 116-142; utils/BlockStateInterface.java lines 26, 69 |
| corrected | Lithium gives faster node-type lookups and chunk access for vanilla pathing and skips inactive navigations on block updates. | In the NeoForge 1.21.1 build, mixin.ai.pathing (the faster node-type lookups and chunk cache) is disabled by default; only mixin.entity.inactive_navigations (default true) helps. | https://raw.githubusercontent.com/CaffeineMC/lithium/1.21.1/lithium-neoforge-mixin-config.md lines 14, 27-34, 333-337 |
| corrected | PathWeaver spark profile: 220 mixed mobs retargeting every 6 ticks, 221-mod pack; pathfinding takes 12.4 % of server-thread time (15.5 ms/tick). | 12.38 % is the share of the sampled server thread, which was 67-74 % idle. 15.47 ms/tick is the whole tick (MSPT) of the off arm; pathfinding was ~6.2 ms/tick = 40.3 % of tickServer. The load came from a synthetic retarget probe (3,604 of 5,572 ms). | https://github.com/zimdin12/PathWeaver README.md lines 213-249 |
| corrected | Pathfinding is normally a minority of tick time. | The cited evidence shows 40 % of tick time when mobs repath every 6 ticks. Its share in ordinary play is unverified. | PathWeaver README.md lines 243-245 |
| corrected | PathWeaver moves about 3/4 of path cost off the main thread. | About 60 %: per-request main-thread cost 480-500 -> 195-202 us; server-thread pathfinding -60 % in the spark profile; brain sink 57 %; total CPU about +12 %. | PathWeaver README.md lines 177-182, 202-206, 222-225 |
| refuted | Return SOUNDS or NONE from getMovementEmission() so no GameEvent.STEP is dispatched. | Entity#move passes flag2 (getOnPos equals getOnPosLegacy, true on ordinary full blocks) as the broadcastGameEvent argument of the first vibrationAndSoundEffectsFromBlock call regardless of emitsEvents(), so SOUNDS still emits GameEvent.STEP. Only MovementEmission.NONE suppresses STEP (and step sounds). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/Entity.java:705-709, 756-775, 814-843, 3863-3888 |
| corrected | TicketController: ticking = true also enables random ticks with no players nearby (NeoForge's forceTicks patch). | forceTicks runs the full per-chunk tick without players: inhabited time, natural mob spawning (NaturalSpawner.spawnForChunk when doMobSpawning) and random ticks. Entity and block-entity ticking do not need it (level 31). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ServerChunkCache.java:364-376; .../net/minecraft/server/level/DistanceManager.java:160-178, 294-297 |
| corrected | NeoForge 26.3 renamed AddReloadListenerEvent to AddServerReloadListenersEvent and DataPackRegistryEvent.NewRegistry to NewDatapackRegistryEvent. | AddReloadListenerEvent -> AddServerReloadListenersEvent happened in NeoForge 21.4 (MC 1.21.4), not 26.3. DataPackRegistryEvent -> NewDatapackRegistryEvent is 26.3.x. | raw.githubusercontent.com/neoforged/NeoForge/<branch>/src/main/java/net/neoforged/neoforge/event/AddReloadListenerEvent.java: HTTP 200 on 1.21.1 and 1.21.3, 404 on 1.21.4, 1.21.5, 1.21.8, 1.21.11, 26.1.x, 26.2.x, 26.3.x; AddServerReloadListenersEvent.java 200 from 1.21.4 on; registries/DataPackRegistryEvent.java 200 up to 26.2.x and 404 on 26.3.x; registries/NewDatapackRegistryEvent.java 200 only on 26.3.x |
| corrected | After the entity loop, ChunkMap#tick visits every tracked entity. | ChunkMap#tick() runs from ServerChunkCache#tick, which ServerLevel#tick calls before the entity loop. Replication therefore ships the previous tick's state. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ServerChunkCache.java:315-332; .../net/minecraft/server/level/ServerLevel.java:381, 395-430 |
| corrected | Vanilla custom payloads are capped at 1 MiB clientbound and 32,767 B serverbound; NeoForge's GenericPacketSplitter splits larger packets; a bundle holds at most 4,096 packets. | Those caps apply only to vanilla's DiscardedPayload fallback for unknown payload ids. NeoForge-registered payloads use their own StreamCodec and are limited by the protocol frame (<= 2 MiB compressed frame, <= 8 MiB uncompressed), which GenericPacketSplitter works around between NeoForge peers. Bundle limit 4,096 confirmed. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/network/protocol/common/ClientboundCustomPayloadPacket.java:36-38; .../ServerboundCustomPayloadPacket.java:16-18; .../network/protocol/common/custom/CustomPacketPayload.java:24-34; .../net/minecraft/network/CompressionDecoder.java:18-19; .../net/minecraft/network/Varint21FrameDecoder.java:15; .../net/neoforged/neoforge/network/filters/GenericPacketSplitter.java:45-60; .../net/minecraft/network/protocol/BundlerInfo.java:11 |
| corrected | NeoForge BlockEvents only cover player and entity actions. | Place/break events do. NeighborNotifyEvent fires from updateNeighborsAt for any change that notifies neighbours, and FluidPlaceBlockEvent for fluids. None catches every state change, so a setBlockState/onBlockStateChange hook is still required. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/event/level/BlockEvent.java:69-295; .../net/minecraft/world/level/Level.java:360; .../net/minecraft/server/level/ServerLevel.java:1114, 1122 |
| confirmed | ServerLevel#onBlockStateChange is called by Level#markAndNotifyBlock for every Level#setBlock that changes state. | Caveats: while NeoForge captures block snapshots (player item placement) markAndNotifyBlock is deferred and replayed by CommonHooks; the hook runs only if the new state is still present; direct LevelChunk#setBlockState writes bypass it. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/level/Level.java:243-262, 270-305; .../net/neoforged/neoforge/common/CommonHooks.java:594, 662; .../net/minecraft/server/level/ServerLevel.java:1443-1455 |
| corrected | HPA* (Botea, Mueller, Schaeffer 2004): up to 10x faster than optimised A*, within 1 % of optimal (link webdocs.cs.ualberta.ca/~mmueller/ps/hpastar.pdf). | Figures match the paper's abstract as cited by secondary sources, but the linked PDF returned HTTP 404 on 2026-10-20. Cite: Botea, Mueller, Schaeffer, 'Near Optimal Hierarchical Path-Finding', Journal of Game Development 1(1), 2004. | curl -L https://webdocs.cs.ualberta.ca/~mmueller/ps/hpastar.pdf -> 404; WebSearch results incl. https://papersdb.cs.ualberta.ca/~papersdb/view_publication.php?pub_id=585 listing |
| confirmed | Flow fields per Emerson, Game AI Pro ch. 23 (Supreme Commander 2); Factorio FFF #317 uses an abstract search as the heuristic; 0 A.D. moves formations through a controller entity. | None (chapter author Elijah Emerson). | http://www.gameaipro.com/GameAIPro/GameAIPro_Chapter23_Crowd_Pathfinding_and_Steering_Using_Flow_Field_Tiles.pdf (text extracted with PDFKit: 'Elijah Emerson', 'While working on Supreme Commander 2', '23.4 The Three Field Types'); https://factorio.com/blog/post/fff-317; https://raw.githubusercontent.com/0ad/0ad/master/binaries/data/mods/public/simulation/components/Formation.js lines 356, 367 |
| corrected | DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec[, RegistryBuilder]). | The fourth parameter is a Consumer<RegistryBuilder<T>>. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:53, 74, 97 |
| corrected | Async is GPL-3.0. | GPL-3.0-or-later. | https://api.modrinth.com/v2/project/async (license.id GPL-3.0-or-later) |
| confirmed | Version snapshot: Lithium 0.15.4 (2026-06-27), Sodium 0.8.13 (2026-08-28), Iris 1.8.14-beta.1 (2026-06-13), spark 1.10.124 (2025-02-23), Fabric API 0.116.17 (2026-09-01) and their licences. | Iris 1.8.14-beta.1 is a beta (see the Iris verdict). | Modrinth API v2 /project/{slug}/version?loaders=["neoforge"]&game_versions=["1.21.1"]: lithium mc1.21.1-0.15.4-neoforge 2026-06-27; sodium mc1.21.1-0.8.13-neoforge 2026-08-28; iris 1.8.14-beta.1+1.21.1-neoforge 2026-06-13 (beta); spark 1.10.124-neoforge-1.21.1 2025-02-23; fabric-api 0.116.17+1.21.1 2026-09-01 (also maven.fabricmc.net metadata); licences from /v2/project (lithium LGPL-3.0-only, sodium Polyform-Shield-1.0.0, iris LGPL-3.0-only, spark GPL-3.0-only, fabric-api Apache-2.0) |
| confirmed | SynchedEntityData ids come from defineId, at most 254 per class hierarchy. | Precision: 254 is the maximum id value (Mojang's Javadoc: 'A maximum of 254 parameters per entity class'); Entity, LivingEntity and Mob already use 16 ids. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/network/syncher/SynchedEntityData.java:18, 25, 53-55; defineId counts in Entity.java (8), LivingEntity.java (7), Mob.java (1) |
| corrected | Cursor: MouseHandler#releaseMouse; cancelling InputEvent.MouseButton.Pre keeps the cursor free; MovementInputUpdateEvent and CalculatePlayerTurnEvent neutralise player input. | MouseButton.Pre works (onPress returns before grabMouse and the key mappings). CalculatePlayerTurnEvent only changes sensitivity/cinematic mode and cannot zero turning (except via sensitivity -1/3); with the mouse released vanilla does not turn the player anyway, because turnPlayer runs only while grabbed. Minecraft#setScreen(null) re-grabs the mouse whenever any screen closes, so RTS mode must release it again. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/client/MouseHandler.java:87-92, 262-330; .../net/minecraft/client/Minecraft.java:1058-1067; .../net/neoforged/neoforge/client/event/CalculatePlayerTurnEvent.java:46, 62 |
| confirmed | Camera APIs: Camera#setup with protected setPosition/setRotation; ViewportEvent.ComputeCameraAngles inside setup; getMaxZoom raycast; Frustum#offsetToFullyIncludeCameraCube exists; view bobbing is baked into the projection; the model-view matrix is rotation-only. | The projection also bakes in hurt-cam tilt and nausea distortion. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/client/Camera.java:47-75, 113-134; .../net/minecraft/client/renderer/culling/Frustum.java:33-55; .../net/minecraft/client/renderer/GameRenderer.java:980 and renderLevel (bobHurt, bobView, confusion multiplied into the projection; rotation-only frustumMatrix); .../net/neoforged/neoforge/client/ClientHooks.java:288-300 |
| confirmed | RenderLevelStageEvent stages; outlines via isCurrentlyGlowing/getTeamColor; requestOutlineEffect (21.1.51); RegisterGuiLayersEvent; AddSectionGeometryEvent; minimap and picking helpers. | Addition: AddSectionGeometryEvent renderers run on the chunk-build thread, so data must be gathered in the main-thread handler. getPartialTick() returns a DeltaTracker. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/client/event/RenderLevelStageEvent.java:85-120, 158-207; .../AddSectionGeometryEvent.java:25-35; .../RegisterGuiLayersEvent.java:47-81; .../RenderGuiLayerEvent.java:70; .../net/minecraft/client/Minecraft.java:2624-2627; .../net/minecraft/client/renderer/LevelRenderer.java:1027-1031, 2543, 2572; .../net/minecraft/world/entity/projectile/ProjectileUtil.java:68, 108; changelog line 459 |
| confirmed | Networking API names: registrar(version), playToClient/playToServer/playBidirectional, executesOn (MAIN default), versioned, optional; PacketDistributor send methods; IEntityWithComplexSpawn; sendPairingData; Start/StopTracking; ENTITY_DATA_SERIALIZERS. | versioned takes a String. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent.java:40; .../network/registration/PayloadRegistrar.java:29, 44-62, 129-158; .../network/PacketDistributor.java:50-113; .../entity/IEntityWithComplexSpawn.java:14-29; .../common/extensions/IEntityExtension.java:396; .../event/entity/player/PlayerEvent.java:261, 281; .../registries/NeoForgeRegistries.java:34 |
| confirmed | Tracker: range = clientTrackingRange x 16 scaled by entity-broadcast-range-percentage, capped by view distance, measured horizontally from the player entity; sendChanges every updateInterval/impulse/dirty data; Pos/Rot/PosRot deltas, teleport on out-of-range, every 400 updates or onGround flip; motion if tracked; hurtMarked always sends motion; one flush per tick. | Addition: tracking also requires the entity's chunk to be tracked by (sent to) that player (isChunkTracked). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ChunkMap.java:1327-1362; .../net/minecraft/server/level/ServerEntity.java:121-226; .../net/minecraft/server/MinecraftServer.java:1034, 1083 |
| confirmed | EntityType.Builder defaults are range 5 chunks and interval 3 ticks; setShouldReceiveVelocityUpdates(false) drops routine motion packets; STEP_HEIGHT drives maxUpStep; the default brain is empty and never ticked; PathfinderMob adds little beyond Mob. | Precision: PathfinderMob adds getWalkTargetValue, isPathFinding/isPanicking and leash-follow behaviour; most vanilla movement goals (RandomStrollGoal, PanicGoal, MeleeAttackGoal) require it. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/entity/EntityType.java:1207-1208, 1256-1263, 1384-1385; .../LivingEntity.java:293-295, 3732-3735; .../Mob.java:150-152; .../PathfinderMob.java:13-75 |
| confirmed | Per-entity cost table: checkDespawn before the range gate, EntityTickEvent.Pre, baseTick contents, loot scan. | Additions: Mob#checkDespawn first posts NeoForge's MobDespawnEvent every tick (even for persistent mobs); LivingEntity#baseTick posts LivingBreatheEvent every tick and runs a suffocation check; tickCount increments even when EntityTickEvent.Pre is cancelled; Level#getEntities and getEntitiesOfClass also scan every PartEntity in the level. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/level/ServerLevel.java:403-425, 769-780; .../net/neoforged/neoforge/event/EventHooks.java:380-391, 952-954; .../net/minecraft/world/entity/Mob.java:570-589, 745-770; .../net/minecraft/world/entity/LivingEntity.java:414, 428; .../net/neoforged/neoforge/common/CommonHooks.java:1382-1395; .../net/minecraft/world/level/Level.java:920-924, 967-975 |
| confirmed | Measuring and testing tools: /tick query\|sprint, /neoforge tps, /neoforge track start entity <seconds> then /neoforge track entity, /jfr, spark profiler/tickmonitor, RegisterCommandsEvent, NeoForge GameTest annotations, MDK gameTestServer, ModDevGradle unitTest + testframework, FMLEnvironment.production (FML 4.0.45). | None (/jfr registers only when JFR is available; /perf is dedicated-server only). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/commands/Commands.java:207-215; .../net/neoforged/neoforge/server/command/TrackCommand.java:28-60, 114 and TimeTracker (duration in seconds); .../GenerateCommand.java:32; https://spark.lucko.me/docs/Command-Usage; .../net/neoforged/neoforge/event/RegisterCommandsEvent.java:36-50; .../gametest/GameTestHolder.java, PrefixGameTestTemplate.java; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/build.gradle:58-72; https://github.com/neoforged/ModDevGradle README lines 430-480; neoforge-21.1.262.pom (fancymodloader 4.0.45); ~/.gradle/caches/.../loader-4.0.45.jar contains FMLEnvironment.class |
| confirmed | Vanilla 1.21.1 debug renderers are inert: DebugPackets.sendPathFindingPacket is empty and DebugRenderer#render only draws chunk borders and GameTest markers; renderFilledBox/renderFloatingText are reusable. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/network/protocol/game/DebugPackets.java:78-79; .../net/minecraft/client/renderer/debug/DebugRenderer.java render() and lines 134-221 |
| confirmed | Persistence: SavedData.save(CompoundTag, Provider), SavedData.Factory with nullable DataFixTypes, computeIfAbsent; NeoForge async write (21.1.118) and temp-file cleanup (21.1.146); autosave every 6,000 ticks; attachments API; setData marks chunks dirty. | Precision: NBT serialisation still runs on the main thread; only the file write moves to the IO worker. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/level/saveddata/SavedData.java:19, 36-63; .../net/minecraft/world/level/storage/DimensionDataStorage.java:43; .../net/minecraft/server/MinecraftServer.java:183, 219, 939-940; changelog lines 272, 341; .../net/neoforged/neoforge/attachment/AttachmentType.java:95-280; .../net/minecraft/world/level/chunk/ChunkAccess.java:520-528 |
| confirmed | Buildings: structure folder 'structure', generated templates in <world>/generated/<ns>/structures, StructureTemplate API, private palettes, flags 2\|16, per-section batching, protection events; fire removes blocks without an event. | Precision: FireBlock calls the block's own onCaughtFire hook (IBlockExtension) but posts no event. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java:54-64; .../StructureTemplate.java:66, 83, 182-186, 226, 496, 506; .../net/minecraft/world/level/block/Block.java:77-81; .../net/minecraft/server/level/ChunkHolder.java:195-199; .../net/neoforged/neoforge/event/level/ExplosionEvent.java:65-74; .../PistonEvent.java:77; .../net/minecraft/world/level/block/FireBlock.java:263-272 |
| confirmed | MineColonies citizens: ICitizenData authoritative, ICitizenManager#spawnOrCreateCitizen(data, level); Freecam FreeCamera extends AbstractClientPlayer; RoN TopdownGui extends AbstractContainerScreen; RTSbuilding ships Sodium, Embeddium and Flywheel compatibility mixins. | None. | https://raw.githubusercontent.com/ldtteam/minecolonies/version/1.21/src/main/java/com/minecolonies/api/colony/managers/interfaces/ICitizenManager.java lines 31-44; https://github.com/MinecraftFreecam/Freecam/blob/main/common/src/main/java/net/xolt/freecam/util/FreeCamera.java line 36; RoN guiscreen/TopdownGui.java line 15; RTSbuilding mixin/SodiumLevelSliceMixin.java, EmbeddiumWorldSliceMixin.java, FlywheelBlockEntityStorageMixin.java |
| confirmed | 26.x tickets: 1.21.5 TicketType registry record, TicketStorage, addTicketWithRadius; 1.21.9 FLAG_* flags; ender-pearl tickets since 1.21.2; TicketController still exists on NeoForge 26.3.x. | None. | primers 1.21.5/index.md lines 297-347; 1.21.9/index.md lines 2123-2139; 1.21.2/index.md line 2151; https://raw.githubusercontent.com/neoforged/NeoForge/26.3.x/src/main/java/net/neoforged/neoforge/common/world/chunk/TicketController.java (HTTP 200) |
| confirmed | Public single-thread scores put the M4 Pro at or above the Zen 4 desktops in these tests. | It is well above: Geekbench 6 single-core about 3,900 for the M4 Pro vs about 3,000 for the 7950X3D. | WebSearch: TechSpot/TweakTown reports of M4 Pro GB6 single-core 3,925; https://nanoreview.net/en/cpu-compare/apple-m4-vs-amd-ryzen-9-7950x3d (7950X3D 3,003) |
| unverifiable | Open question 8: whether 26.x still provides Entity#broadcastToPlayer and the same ChunkMap$TrackedEntity structure. | Partly answered: NeoForge 26.3.x's ChunkMap patch context still shows entityMap, TrackedEntity.seenBy and updatePlayer(player). broadcastToPlayer in 26.x was not verified. | https://raw.githubusercontent.com/neoforged/NeoForge/26.3.x/patches/net/minecraft/server/level/ChunkMap.java.patch (getPlayersWatching uses entityMap/seenBy; updatePlayer(player) context near line 121); no tracker renames listed in primers 1.21.2-26.3 |
| confirmed | GeckoLib 4.9.x is the candidate for animated units on NeoForge 1.21.1 (open question 2). | Current build is 4.9.3 (2026-09-16), MIT. | Modrinth API geckolib versions (neoforge, 1.21.1): 4.9.3 2026-09-16 release; /v2/project/geckolib license MIT |
