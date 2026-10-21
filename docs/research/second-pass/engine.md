# Strategos: Minecraft 1.21.1 engine constraints and techniques for an RTS with hundreds of units

*Research date 2026-10-20. Independently fact-checked 2026-10-20/21 against the decompiled 1.21.1 + NeoForge 21.1.262 tree, NeoForge Maven, the Modrinth v2 API and the upstream repositories (see "Verification notes"). Target: Minecraft Java 1.21.1 with NeoForge 21.1.x, Mojang mappings. Written for the tech lead, who will choose the loader, libraries, architecture and roadmap.*

The fact-check adds three markers:

- *(corrected)*: the statement was changed after verification.
- *(added)*: a new verified fact.
- (unverified): the statement could not be confirmed from a primary source.

## 0. Method, evidence tags, version snapshot

Every claim carries one of these tags:

- **[src]**: I read it in source code. That means the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 tree (Mojang names, Parchment parameters) from the reference MDK build (`scratchpad/mc-src-1.21.1`), or a cloned upstream repository (commit date given).
- **[doc]**: official docs, changelogs, or the NeoForged porting primers ([primers repo](https://github.com/neoforged/.github/tree/main/primers)).
- **[bench]**: a third-party benchmark. All of them are self-reported by mod authors.
- **[inf]**: my own inference or estimate. Treat it as a hypothesis to measure.

Version snapshot (all time-sensitive; Modrinth v2 API and Maven queried 2026-10-20 and re-queried by the fact-check):

| Component | State on 2026-10-20 | Licence |
|---|---|---|
| NeoForge for 1.21.1 | **21.1.264** (2026-10-19). The MDK pins 21.1.262 (2026-10-15); 21.1.263 (2026-10-18) sits in between *(added)*. Still receiving backports ([Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml), [changelog](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-changelog.txt)) | LGPL-2.1 |
| NeoForge, newest line | 26.3.0.95 (2026-10-20) | LGPL-2.1 |
| Fabric API for 1.21.1 | 0.116.17 (2026-09-01) | Apache-2.0 |
| [Lithium](https://modrinth.com/mod/lithium), NeoForge 1.21.1 | 0.15.4 (2026-06-27) | LGPL-3.0 |
| [Moonrise](https://github.com/Tuinity/Moonrise), NeoForge 1.21.1 | 0.1.0-beta.15 (2025-10-10; a beta, and the newest 1.21.1 build). Rewrites the chunk system and the entity-tracker tick loop, but keeps vanilla `TrackedEntity#updatePlayer` *(corrected wording)* | GPL-3.0 |
| [Sodium](https://modrinth.com/mod/sodium), NeoForge 1.21.1 | 0.8.13 (2026-08-28) | PolyForm Shield 1.0.0 |
| [Iris](https://modrinth.com/mod/iris), NeoForge 1.21.1 | 1.8.14-beta.1 (2026-06-13). This is a **beta** (changelog: "Updates to Sodium 0.8"). The last stable NeoForge 1.21.1 release is 1.8.12 (2025-06-17), which predates Sodium 0.8 *(added)* | LGPL-3.0 |
| [spark](https://modrinth.com/mod/spark), NeoForge 1.21.1 | 1.10.124 (2025-02-23) | GPL-3.0 |
| [Reign of Nether](https://github.com/SoLegendary/reignofnether), the most mature MC RTS | 1.5.0b (2026-10-14), **Forge 1.20.1 only** | GPL-3.0 |
| [MineColonies](https://github.com/ldtteam/minecolonies) | `version/1.21` branch for NeoForge 1.21.1 (built against 21.1.80), commit 2026-10-06 | GPL-3.0 |
| [Baritone](https://github.com/cabaletta/baritone) | `1.21.1` branch, last commit 2026-09-01. That commit, "deprecate 1.21.1", **deprecates the branch**: no further updates, and primary development moved to `1.21.4` *(corrected)* | LGPL-3.0 |

NeoForge 21.1 has first-party hooks for almost every engine need below: ticket controllers, render-stage events (Sodium dispatches some of them), section geometry, GUI layers, synced attachments, and a packet splitter. I did not audit the Fabric equivalents.

---

## 1. Server tick budget and per-entity cost

### 1.1 What one `Mob` costs per tick in 1.21.1 [src]

`ServerLevel#tick` runs `entityTickList.forEach(...)`. Each stage, what it scales with, and what it means for an RTS:

| Stage | 1.21.1 code path | Scales with | RTS consequence |
|---|---|---|---|
| Despawn check | `Entity#checkDespawn` runs **every tick for every entity in the ticking list, before the range gate**. `Mob#checkDespawn` first posts NeoForge's `MobDespawnEvent` *(added)*. It then calls `getNearestPlayer` unless `isPersistenceRequired()` or `requiresCustomPersistence()` | players | Make units persistent and turn the check into a no-op, which also skips the per-tick event |
| Range gate | `DistanceManager#inEntityTickingRange(chunk)`: only chunks inside simulation distance (ticket level ≤ 31) tick entities | – | Units elsewhere are frozen (§3) |
| NeoForge hook | `EventHooks.fireEntityTickPre`, i.e. the cancelable `EntityTickEvent.Pre`, then `Entity#tick`, then `EntityTickEvent.Post` | event bus | Can throttle *other mods'* entities |
| Base tick | `Entity#baseTick`: fluid state and fluid pushing over the AABB, fire, portal, void check. `LivingEntity#baseTick`: in-wall (suffocation) check, NeoForge `LivingBreatheEvent` every tick *(added)*, `hurtTime--`, `invulnerableTime--`, effects | block reads | – |
| Living tick | `LivingEntity#tick`: `detectEquipmentUpdates()` every tick, `CombatTracker` every 20 ticks, then `aiStep()`, then head/body rotation | slots | – |
| AI | `Mob#serverAiStep` is **`final`**. It runs `sensing.tick()`. The target and goal selectors do a full `tick()` when `(tickCount + getId())` is even and only `tickRunningGoals(false)` otherwise. Then `navigation.tick()`, `customServerAiStep()`, and the move/look/jump controls | goals | `customServerAiStep()` is the only empty hook inside. The navigation (`createNavigation`) and the move/look/jump controls (protected fields) can also be replaced with our own classes *(corrected)* |
| Movement | `travel` → `handleRelativeFrictionAndCalculateMovement` → `Entity#move`, **every tick, even when idle**, because gravity pushes into the ground. `collide` runs an entity-section query (`EntityGetter#getEntityCollisions`) plus block collision, then `checkInsideBlocks`, step sounds and `GameEvent.STEP` vibrations | neighbours, blocks | Idle units still pay for collision |
| Pushing | `LivingEntity#pushEntities` calls `level.getEntities(this, bbox, EntitySelector.pushableBy(this))` and `doPush`. With 24+ overlapping pushables (gamerule `maxEntityCramming`, default 24) there is a 1-in-4 chance per tick of 6 cramming damage | neighbours, so O(n²) in clumps | Armies clump, so turn it off |
| Loot | `Mob#aiStep` scans for `ItemEntity` if `canPickUpLoot()` and mob griefing allows it | – | Keep it off |
| Replication | `ServerLevel#tick` calls `ServerChunkCache#tick`, which runs `ChunkMap#tick` **before** the entity loop. It visits every tracked entity, so each tick ships the state the previous tick produced *(corrected: the report said "after the loop")*. `ServerEntity#sendChanges` builds packets every `updateInterval` ticks | entities × viewers | §4 |

Costs that grow with unit count across the whole level [src]:

- **Path invalidation on every block change.**
  - `ServerLevel#sendBlockUpdated` loops over `navigatingMobs`, which holds every *loaded* `Mob` in the level (each is added when entity tracking starts).
  - The loop runs for changes whose `setBlock` flags include `UPDATE_CLIENTS`. For each block whose collision shape changes, it calls `PathNavigation#shouldRecomputePath(pos)` and then `recomputePath()` synchronously (at most once per 20 ticks per mob).
  - Construction therefore costs O(blocks placed × mobs).
  - Lithium's `mixin.entity.inactive_navigations` (on by default, also in the 1.21.1 build) skips mobs that cannot react ([Lithium 1.21.1 NeoForge config](https://github.com/CaffeineMC/lithium/blob/1.21.1/lithium-neoforge-mixin-config.md)).
- **Tracker work on every player move.**
  - `ChunkMap#move(ServerPlayer)` runs from each movement packet and calls `TrackedEntity#updatePlayer` on **all** tracked entities. A commander who pans every tick costs O(entities) per tick.
  - Under Moonrise this loop is disabled. Instead, every tracked entity re-runs `updatePlayer` each tick for every player near its chunk, which costs O(entities × nearby players) per tick *(added)*.
- **Entity lookups.**
  - `Level#getEntities` grows the AABB by 2 blocks horizontally and 4 down. For each X section column that box spans, it iterates **every existing entity section in that column** (all Z and Y) from a sorted section-id set and filters by Y and Z. It then tests every entity in the matching sections *(precision added)*.
  - Cost therefore also grows with the loaded area along Z.
  - Sections index entities by class (`ClassInstanceMultiMap`), so `getEntitiesOfClass(UnitEntity.class, …)` is cheaper than an untyped query.
- **Empty levels stop.**
  - A level with no players stops ticking entities and block entities after 300 ticks (`emptyTime`), unless it has forced chunks.
  - NeoForge 21.1 patches this check to also count its own ticket-controller chunks (`ForcedChunkManager.hasForcedChunks`).
  - Only `ForcedChunksSavedData` entries count, meaning vanilla `/forceload` and NeoForge `TicketController` tickets. Ordinary `TicketType` region tickets do **not** keep an empty level ticking *(added)*.

### 1.2 Benchmarks and realistic unit counts

| Source | Setup | Result |
|---|---|---|
| [PathWeaver](https://github.com/zimdin12/PathWeaver) (MIT, Fabric 26.x) [bench] | 1,024 zombies in a walled maze, all retargeting every 6 ticks. Shipped config: 0.6.1 jar, Lithium loaded, measured 2026-07-31; hardware not stated for this run | Synchronous (vanilla) arm: mean tick interval 88.5–96.6 ms, p99 832–958 ms, **480–500 µs of main-thread time per path request**. Earlier gate-forced runs on a 16-core/32-thread desktop measured mean 83–87 ms and p95 738–766 ms. All figures are wall-clock tick intervals, not MSPT *(corrected: the report merged two runs)* |
| same, earlier gate-forced runs (16-core/32-thread desktop) | 1,024 zombies, each retargeting every 20 ticks (~51 requests/tick) | Vanilla holds 20 TPS, but p95 is 59–67 ms and p99 77–80 ms |
| same, spark profile | 220 mixed mobs retargeting every 6 ticks, driven by a synthetic in-mod probe; 221-mod pack | Off arm: pathfinding took 5,572 ms of 45 s of sampled server-thread time (12.4 %), but that thread was 67–74 % idle. Measured against tick time, pathfinding was about **6.2 ms of a 15.47 ms tick, i.e. 40.3 % of tick work**. The synthetic probe accounts for 3,604 of the 5,572 ms *(corrected: 15.5 ms/tick is the whole tick, not pathfinding)* |
| [Async](https://modrinth.com/mod/async) (GPL-3.0) [bench] | 9,000 villagers, MC 1.21.4, Ryzen 9 7950X3D. The baseline pack ran Lithium **plus** ServerCore (entity activation range, villager lobotomization), Concurrent Chunk Management Engine, ScalableLux, TT20, Carpet and others *(added)* | 225.4 ms/tick (4.4 TPS), about 25 µs per villager. That is not a clean vanilla per-mob cost *(corrected)*. Async itself now requires MC 26.1+ and Java 25 |
| [Laminar](https://modrinth.com/mod/laminar-engine) (ARR) [bench] | 500 zombies, 32 chunks, i7-4770K @ 4.3 GHz. The benchmark table does not state the MC version, but the benchmarked v1.2.7 was published only for 26.1–26.1.2 (Fabric) *(corrected)* | Vanilla median 31.1 ms, p95 50.5 ms. That is about 60 µs per zombie on a 2013 CPU, computed as whole tick ÷ 500, so an upper bound |
| Reign of Nether [src] | `maxPopulation` gamerule default | 150 population per player |

What these numbers suggest together [inf] (unverified; measure in week 1):

- **Per-mob cost.**
  - A vanilla-AI mob costs roughly **20–30 µs per tick** on a current desktop core, and more in dense clumps. (unverified: the Async baseline was heavily optimised, and the Laminar figure includes the whole tick.)
  - A vanilla path search in non-trivial terrain costs **0.1–0.5 ms** of main-thread time. PathWeaver measured 480–500 µs per long maze search and 101–103 µs per swim search with 1,024 cod.
  - Pathfinding is a large share of tick work whenever mobs repath often: about 40 % of the tick in PathWeaver's 220-mob profile *(corrected: the report said it is "normally a minority")*. It dominates during repath bursts, and RTS group orders are exactly that.
- **Capacity on the dev machine.**
  - Public single-thread scores put the M4 Pro at or above the Zen 4 desktops in these tests: Geekbench 6 single-core is about 3,900 versus about 3,000 for the 7950X3D. That is indicative only; JVM-on-ARM performance was not checked.
  - At 20–30 µs per mob, a 50 ms tick holds about 1,700–2,500 vanilla-AI mobs with nothing else running. The report's 1,000–1,500 implies 33–50 µs per mob, as in clumps or with other work *(corrected arithmetic)*.
- **Budget for an RTS.**
  - Plan about **20 ms of the 50 ms tick for units**. The rest goes to chunks, block entities, our economy and AI, and GC pauses.
  - That allows roughly 670–1,000 vanilla-AI units at 20–30 µs each; the report's 600–800 implies 25–33 µs *(corrected arithmetic)*. Alternatively, **1,500–2,000 stripped units at about 10 µs average** with level of detail and off-thread pathing (unverified).
  - Client rendering of that many animated models may hit its limit first. I did not measure it (unverified).

### 1.3 Choosing the base class

| Base | What you get | What it costs | Use for |
|---|---|---|---|
| `Entity` | Cheapest tick; you own everything | No health/damage pipeline, effects, attributes or equipment layers. Vanilla mobs and projectiles treat it as non-living | Projectiles, markers, rally flags |
| `LivingEntity` | Health, NeoForge damage events (`LivingIncomingDamageEvent`, `DamageContainer`), attributes, effects, equipment rendering. **Not** registered in `navigatingMobs`. `LivingEntity#serverAiStep` is an empty, non-final hook *(added)* | No `MoveControl`, `PathNavigation` or goals. You drive movement yourself through `xxa`/`zza` + `travel` | A lean unit if we never want vanilla navigation |
| `Mob` | Adds goal/target selectors, `Sensing`, `PathNavigation`, controls, persistence and leads | Registered in `navigatingMobs`; has despawn logic (and a per-tick `MobDespawnEvent`); `serverAiStep` is final | – |
| `PathfinderMob` | Adds `getWalkTargetValue` and the vanilla stroll/panic helpers | Nothing important beyond `Mob` | **Recommended base, stripped down** |

**Recommendation [inf]:** subclass `PathfinderMob` and strip it down.

- With zero goals and an empty brain, the overhead `Mob` adds is small next to movement and collision, which a `LivingEntity` pays as well.
- Staying a `Mob` keeps spawn eggs, `Mob#doHurtTarget`, held-item and armour rendering, leads, vanilla mob targeting, and compatibility with other mods.
- Confirm it with a week-1 micro-benchmark: 1,000 idle and 1,000 moving units, `PathfinderMob` versus `LivingEntity`.

### 1.4 Lightweight unit recipe (1.21.1 names)

- **Entity type.**
  - `EntityType.Builder.of(...).sized(0.6F, 1.8F).clientTrackingRange(n).updateInterval(3–4)`. The builder defaults are range 5 chunks and interval 3 ticks; vanilla mobs such as zombies set range 8 [src].
  - NeoForge's `setShouldReceiveVelocityUpdates(false)` drops routine `ClientboundSetEntityMotionPacket`s, about 12 B per update.
  - Knockback still reaches clients through `hurtMarked`, which always broadcasts the motion packet [src].
- **No vanilla AI.**
  - Leave `registerGoals()` empty. The default `Brain` from `brainProvider()` is empty and is never ticked by `LivingEntity`/`Mob` [src].
  - Run the unit controller from `customServerAiStep()`, staggered with `(tickCount + getId()) % interval`. Vanilla uses the same trick for its goal selectors.
- **No vanilla pushing.**
  - `isPushable() → false`, and `pushEntities()` becomes a no-op. Separation comes from our own steering (§2).
  - Non-pushable units are never counted for cramming.
- **No despawn, loot or vibrations.** *(corrected)*
  - Call `setPersistenceRequired()`, return false from `removeWhenFarAway`, make `checkDespawn()` a no-op, and keep `canPickUpLoot` false.
  - Return `NONE` from `getMovementEmission()`; this is the only value that stops `GameEvent.STEP` dispatch [src].
  - `SOUNDS` does not stop it in 1.21.1. The first step-effect call in `Entity#move` passes "on-pos equals legacy on-pos" as the game-event flag instead of `emitsEvents()`, so `SOUNDS` still emits `GameEvent.STEP` in the common case.
  - Play footstep sounds client-side if wanted.
- **Movement.**
  - Write a custom `MoveControl` fed by steering, and keep the vanilla `travel`/`move` for physics.
  - Set `Attributes.STEP_HEIGHT` to 1.0. Units then walk up single blocks with no jump logic, because `LivingEntity#maxUpStep` reads this attribute [src].
- **Combat invulnerability frames.**
  - While `invulnerableTime > 10`, `LivingEntity#hurt` ignores any hit no stronger than the previous one. A stronger hit inside that window deals only the difference (`amount − lastHurt`) and does not reset the timer *(added)*.
  - A hit outside the window resets the timer to the container's `getPostAttackInvulnerabilityTicks()` (20).
  - When many archers focus one target, damage is lost silently.
  - Fix it in `LivingIncomingDamageEvent`, which is posted before the i-frame check: call `getContainer().setPostAttackInvulnerabilityTicks(0)` for unit-on-unit damage. Alternatively, tag our damage types `minecraft:bypasses_cooldown` [src].
- **Level of detail (LOD) tiers [inf].**
  - T0, visible or fighting: full tick; think every 2–4 ticks.
  - T1, moving but unseen: full physics; think every 8–10 ticks.
  - T2, idle, garrisoned or gathering: skip `super.tick()` and run a cheap sanity tick every 20 ticks.
  - T3, unloaded: a data record only (§3).
  - Caveat: skipping ticks freezes `hurtTime` and `invulnerableTime`, which only `LivingEntity#baseTick` decrements [src]. A sleeping unit must wake in `hurt`, and on orders, effects, fire, fluids, or a block change under it.
- **Client.**
  - Override `shouldRenderAtSqrDistance`. Vanilla culls at the average AABB side × 64 × clamp(render distance ÷ 8, 1, 2.5) × the "Entity Distance" setting [src] *(corrected: the report omitted the render-distance factor)*.
  - For a zombie-sized mob that is about 67 blocks only at render distance ≤ 8. At the default 12 chunks it is about 101 blocks, and at ≥ 20 up to about 168.
  - That can be too short for a camera 40–80 blocks above the battlefield at low render distances, and it depends on client settings, so set our own distance.

### 1.5 Measuring

- `/tick query`
- `/tick sprint <ticks>`, which reports ms per tick
- `/neoforge tps`
- `/neoforge track start entity <seconds>`, then `/neoforge track entity` for per-entity timings
- `/jfr start|stop`, all present in 1.21.1 [src]
- spark `/spark profiler` and `/spark tickmonitor`

---

## 2. Pathfinding

### 2.1 Vanilla pathfinding in 1.21.1 [src]

- **Node cap.**
  - The `PathNavigation(Mob, Level)` constructor fixes `maxVisitedNodes = floor(FOLLOW_RANGE × 16)` once.
  - That is 256 nodes for the `Mob` default of 16, and 560 for zombies (35). `setMaxVisitedNodesMultiplier` scales it.
- **Search region.**
  - `createPath(...)` builds a `PathNavigationRegion` with radius `followRange + 8`, or `+16` for entity targets.
  - The region holds **live `ChunkAccess` references** from `ChunkSource#getChunkNow`. Unloaded chunks read as `EmptyLevelChunk` (air).
- **Search.**
  - `PathFinder#findPath` is A* over a `BinaryHeap` with the heuristic weighted ×1.5 (`FUDGING`). It is fast but not optimal.
  - It stops at the first target within `accuracy` (Manhattan distance) or when it hits `maxVisitedNodes`, and never expands nodes beyond `maxRange`.
  - If no target is reached it returns a **partial path** to the closest node.
- **Movement rules.**
  - `WalkNodeEvaluator` uses 4 cardinal and 4 diagonal neighbours, step-up `max(1, maxUpStep())`, and `getMaxFallDistance()`.
  - It also handles doors (`canOpenDoors`/`canPassDoors`), fences, water (`canFloat`), and a per-`PathType` malus.
- **Type cache.** `PathTypeCache` is a single 4096-slot direct-mapped cache per `ServerLevel`, built from two plain arrays and invalidated in `sendBlockUpdated`. It is **not thread-safe**.
- **Following.** `PathNavigation#tick` → `followThePath` → `MoveControl#setWantedPosition`, with a stuck check every 100 ticks. Paths are recomputed after block changes, at most once per 20 ticks.

Why this doesn't fit an RTS:

- The horizon is short, tens of blocks.
- It runs one synchronous search per unit. An order to 100 units means 100 searches, roughly 10–50 ms by the PathWeaver numbers.
- Weighted A* on an 8-neighbour grid gives unsmoothed, suboptimal paths, which show as zig-zags [inf].
- There is no group, formation or avoidance logic beyond pushing.
- Construction triggers invalidation storms.

### 2.2 Recommended stack [inf]

| Layer | Technique | Notes |
|---|---|---|
| Navigation grid | Compact per-chunk cell classes (standable, step-up, water, door, fence or wall, damage, cost) over a Y band. Built on the main thread from block states and published as immutable copy-on-write snapshots | Reign of Nether's `WalkabilityGrid` does this, with budgeted classification and region-only patches on block change [src] |
| Hierarchy | HPA*-style clusters (16×16 columns or 16³ sections) with entrance nodes, plus connected-component labels for O(1) reachability checks | [Botea, Müller, Schaeffer 2004](http://web.archive.org/web/20250526142416/http://webdocs.cs.ualberta.ca/~mmueller/ps/hpastar.pdf): up to 10× faster than optimised A*, within 1 % of optimal. The original ualberta link now returns 404, so this is an archived copy *(corrected link)*. [Factorio FFF #317](https://factorio.com/blog/post/fff-317) uses an abstract chunk search as the heuristic for the real one |
| Low level | A* inside the cluster corridor, then string-pulling smoothing | Jump point search (JPS) assumes uniform costs and 2D moves. Step-ups, drops, water and door costs break that, and block edits invalidate JPS+ preprocessing |
| Groups | Flow fields per destination over the corridor (cost field → integration field → direction field), cached per goal cluster | [Emerson, *Game AI Pro* ch. 23](http://www.gameaipro.com/GameAIPro/GameAIPro_Chapter23_Crowd_Pathfinding_and_Steering_Using_Flow_Field_Tiles.pdf), the Supreme Commander 2 technique |
| Local avoidance | ORCA ([RVO2](https://github.com/snape/RVO2), C++, Apache-2.0, small enough to port) on the XZ plane, or boids-style separation. Neighbours come from a per-tick uniform spatial hash of our own units | Replaces vanilla pushing; O(n·k) |
| Formations | Slot templates (line, box, staggered, flank, as in AoE II). One path for the formation centre, greedy or Hungarian slot assignment, speed set by the slowest member | 0 A.D. moves formations through a controller entity |

### 2.3 Running it off the main thread safely

- **Never touch `Level` or `ServerChunkCache` from a worker [src].**
  - Called off-thread, `ServerChunkCache#getChunk` runs `CompletableFuture.supplyAsync(..., mainThreadProcessor).join()`. The worker blocks on the server thread, which means stalls and a risk of deadlock.
  - `getChunkNow` returns `null` off-thread, so a vanilla `PathNavigationRegion` built on a worker sees only air.
- **Snapshot on the main thread [src].**
  - `PalettedContainer#copy()` clones the palette and storage.
  - Vanilla does exactly this for off-thread chunk meshing: `RenderChunk` copies `LevelChunkSection#getStates().copy()`.
  - Live `get()` reads take no lock; the mutators and network (de)serialisation (`set`, `getAndSet`, `read`, `write`) use the `ThreadingDetector` *(precision)*. Under concurrent writes, reads can return stale or `null` states [inf].
  - Prefer our own compact nav grid with immutable snapshots.
- **Apply results on the main thread [inf].**
  - Tag each request with a sequence number and drop results for superseded orders.
  - Use bounded queues and a per-tick budget for main-thread classification.
  - Return a "busy" answer distinct from "no path".
- **Don't share vanilla caches.** Give each worker its own type cache; evaluators and `PathTypeCache` are single-threaded.
- **Invalidation hook.**
  - Option 1: a mixin on `LevelChunk#setBlockState`, which catches everything. This is Reign of Nether's choice.
  - Option 2: hook `ServerLevel#onBlockStateChange` with a mixin. `Level#markAndNotifyBlock` calls it for every `Level#setBlock` that changes state [src]. While NeoForge's block-snapshot capture is active (e.g. during item placement), the call is deferred until the snapshot is applied *(added)*.
  - NeoForge has no catch-all block-change event *(corrected)*. Its `BlockEvent` subclasses each cover a specific cause:
    - player breaks (`BreakEvent`)
    - entity placement (`EntityPlaceEvent`, `EntityMultiPlaceEvent`)
    - neighbour notifications (`NeighborNotifyEvent`)
    - fluid placement (`FluidPlaceBlockEvent`)
    - farmland trampling, portal spawn, tool modification
  - Mark dirty regions, coalesce them, rebuild on a budget, and keep serving the stale-but-walkable snapshot meanwhile. Reign of Nether does this deliberately, so units are not stranded in a "blocked" hole.

### 2.4 What other projects do

| Project | Approach | Licence |
|---|---|---|
| MineColonies (1.21.1 branch) [src] | Job-based A* (`AbstractPathJob`) on **one** worker (`ThreadPoolExecutor(1,1)`, queue of 10,000). Its `ChunkCache` holds live `LevelChunk` references captured on the main thread and reads them off-thread, tolerating the race | GPL-3.0 |
| Baritone (1.21.1 branch, deprecated since 2026-09-01) [src] | Client-side A* with cost coefficients {1.5 … 10}, primary and failure timeouts, segmented paths. Reads `ClientChunkCache` directly and falls back to cached 512×512 regions at 2 bits per block | LGPL-3.0 |
| Reign of Nether (Forge 1.20.1) [src] | `RtsPathfinder` takes a snapshot and hands it to `PathfinderWorkerPool`. Grid warm-up "MUST happen on the main thread" and is budgeted. Results are converted to vanilla `Path`. Gamerules `pathfindingThreads` and `pathfindingChunkBuildsPerTick`. The custom pathfinder (gamerule `rtsPathfinding`, default off) switches on automatically only on "RTS-optimised" maps with a world border ≤ 1,280 blocks, where it pre-warms the whole navmesh *(added)* | GPL-3.0 |
| PathWeaver (Fabric 26.x) [bench] | Vanilla A* on workers over a live-backed region with per-thread caches. Region building and result installation stay on the main thread. Moves about **60 %** of path cost off the main thread: 480–500 → 195–202 µs per request, and pathfinding's share of the tick falls from 40.3 % to 19.8 % *(corrected: the report said about 3/4)*. Its README warns that workers can observe mid-search block changes | MIT |
| Lithium [doc] | Skips inactive navigations on block updates (`mixin.entity.inactive_navigations`, on by default). Its faster node-type lookups and chunk access for vanilla pathing (`mixin.ai.pathing`) are **off by default in the 1.21.1 builds** (Fabric and NeoForge 0.15.x) and must be enabled in `config/lithium.properties`. They are on by default only in newer Lithium versions *(corrected)* | LGPL-3.0 |

**Licensing.** *(corrected precision)*

- Copying GPL-3.0 code (Reign of Nether is GPL-3.0-only; MineColonies is GPL-3.0-or-later on Modrinth) makes the combined work GPLv3. Strategos itself would then have to be distributed under GPL-3.0; a merely "GPL-compatible" permissive licence is not enough.
- Study their designs, but write our own code.
- RVO2 (Apache-2.0) can be ported if its licence and NOTICE are kept. PathWeaver (MIT) can be ported if its copyright notice is kept.

---

## 3. Chunk loading and the world

### 3.1 Facts [src]

- **Ticket levels.**
  - Level 33 means accessible, 32 block ticking, and ≤ 31 entity ticking.
  - Entities tick only where the simulation-distance `TickingTracker` reports entity ticking. In border chunks they exist but are frozen.
- **Vanilla region tickets.**
  - `ServerChunkCache#addRegionTicket(TicketType, ChunkPos, distance, value[, forceTicks])` sets level `33 − distance` and also feeds the simulation tracker. The `forceTicks` overload is a NeoForge addition.
  - `TicketType.create(name, comparator, lifespanTicks)` gives self-expiring tickets; vanilla `PORTAL` lives 300 ticks.
- **NeoForge `TicketController`.**
  - Construct it as `TicketController(ResourceLocation id, @Nullable LoadingValidationCallback)` and register it in `RegisterTicketControllersEvent` (mod bus). Call `forceChunk(ServerLevel, BlockPos|Entity|UUID owner, chunkX, chunkZ, add, ticking)`.
  - Tickets persist in `ForcedChunksSavedData` and are revalidated on load.
  - It uses distance 2 (level 31), so **only the forced chunk itself is entity-ticking**.
  - `ticking = true` also enables random ticks, and natural spawning, with no players nearby (NeoForge's `forceTicks` patch).
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
   - Such tickets do not count as forced chunks *(added)*. In a level with no players, entity ticking still stops after 300 ticks unless a vanilla forced chunk or a NeoForge `TicketController` ticket exists in that level (§1.1).
   - Vanilla 1.21.2+ does the same for thrown ender pearls (`placeEnderPearlTicket`) [doc].
   - Cap the number of such groups, and never trigger worldgen: pregenerate the match map.
4. **Bounded maps.**
   - A pregenerated arena inside the world border keeps loading, fog of war and persistence tractable.
   - Reign of Nether only enables fog of war on "RTS-optimised" small maps, meaning a world border ≤ 1,280 blocks (80×80 chunks).

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
  - Vanilla custom payloads are capped at 1 MiB clientbound and 32,767 B serverbound.
  - NeoForge's `GenericPacketSplitter` splits larger packets on NeoForge-to-NeoForge connections. Since 21.1.260 the reassembled size is capped by `splitPacketMaxSizeLimitMb` (default 128 MB) *(added)*.
  - A bundle holds at most 4,096 packets.
- **`SynchedEntityData`.**
  - Ids come from `defineId`, declared in `defineSynchedData(SynchedEntityData.Builder)`. The maximum id is 254, so a class hierarchy has 255 slots, of which `Entity`, `LivingEntity` and `Mob` already use 16 *(corrected)*.
  - Dirty values go to trackers through `ServerEntity#sendDirtyEntityData`.
  - Custom serializers are registered in `NeoForgeRegistries.ENTITY_DATA_SERIALIZERS`.
- **Spawn data.** `IEntityWithComplexSpawn`, `IEntityExtension#sendPairingData(ServerPlayer, Consumer<CustomPacketPayload>)`, and the `PlayerEvent.StartTracking`/`StopTracking` events.
- **Attachment sync.**
  - `AttachmentType.Builder#sync(...)` and `IAttachmentHolder#syncData` were backported in **21.1.197**.
  - Until **21.1.264** (2026-10-19), the per-player `sendToPlayer` predicate was not checked on *initial* sync [doc: changelog; src: `AttachmentSync#syncInitialAttachments` in 21.1.262].
  - Fog-of-war-filtered attachments therefore need NeoForge 21.1.264 or later.

### 4.2 Tracker behaviour [src]

- **Range.**
  - The range is `clientTrackingRange × 16` blocks, scaled by `entity-broadcast-range-percentage` and capped by the player's view distance.
  - Distance is measured **horizontally from the player entity, not the camera**.
- **Update cadence.** `ServerEntity#sendChanges` sends only every `updateInterval` ticks, after an impulse, or when entity data is dirty.
- **Packets sent per update.**
  - Position and rotation go as `ClientboundMoveEntityPacket.Pos`, `Rot` or `PosRot`: deltas in 1/4096 block, good for ±8 blocks.
  - A `ClientboundTeleportEntityPacket` replaces it when the delta is out of range, every 400 updates, and **every time `onGround` flips** (every jump).
  - `ClientboundSetEntityMotionPacket` is sent when velocity changes, if velocity is tracked; `ClientboundRotateHeadPacket` when the head turns.
- **Flushing.** `MinecraftServer#tickChildren` suspends flushing during the tick, so each player gets one flush per tick.

### 4.3 Bandwidth for moving units [inf, computed from 1.21.1 packet formats]

Assumptions:

- The entity-id VarInt takes 3 B (ids ≥ 16,384).
- Compression is on with the default threshold of 256, so small packets travel uncompressed with a 1 B marker, plus a 1 B length prefix.
- Rot and HeadRot are sent only when rotation changes, so the vanilla row is an upper-end estimate.

| Configuration | Bytes per unit-update | Rate | 300 moving units | 1,000 moving units |
|---|---|---|---|---|
| Vanilla defaults (PosRot 15 + Motion 12 + HeadRot 7) | ~34 | 6.7 Hz (interval 3) | ~68 KB/s (0.54 Mbit/s) | ~227 KB/s (1.8 Mbit/s) |
| Velocity updates off | ~22 | 6.7 Hz | ~44 KB/s | ~147 KB/s |
| Velocity off, interval 4 | ~22 | 5 Hz | ~33 KB/s | ~110 KB/s |
| Custom batched delta channel (~6 B per unit) | ~6 | 6.7 Hz | ~12 KB/s | ~40 KB/s |

- These figures are per client. The host's upload is that times the number of clients.
- Not included:
  - teleports on jumps, about 33 B each
  - entity-data changes, about 13 B per value
  - spawn bundles
  - TCP/IP overhead, about 2–3 KB/s at 20 flushes per second (unverified estimate)
- **Conclusion.**
  - Vanilla tracking with velocity off is fine for LAN and for 2–4-player internet games at up to 300–500 visible moving units.
  - A custom batched movement channel, with our own client interpolation, is an optimization to keep in reserve.

### 4.4 Fog-of-war filtering

- **The hook [src].** `Entity#broadcastToPlayer(ServerPlayer)` (default `true`) is part of the pairing condition in `TrackedEntity#updatePlayer`. Override it in our unit and building classes with an O(1) lookup into the viewer faction's visibility grid.
- **Pitfall: visibility is not re-evaluated on its own in vanilla [src].**
  - `updatePlayer` only runs when the entity changes section, when that player moves (`ChunkMap#move`), or when the entity spawns.
  - So a stationary enemy uncovered by your scout is *not* re-evaluated while the commander stands still.
  - Under Moonrise, the tracker calls the vanilla `updatePlayer` every tick for every player whose view distance covers the entity's chunk *(added)*. Re-evaluation is automatic there, and the visibility lookup must stay O(1) because it runs O(entities × nearby players) times per tick.
  - Fix [inf]: every 5–10 ticks, re-evaluate the entities whose visibility flipped. That needs an access transformer:
    `public net.minecraft.server.level.ChunkMap entityMap` and `public net.minecraft.server.level.ChunkMap$TrackedEntity`; then call `updatePlayer(player)`.
  - Reign of Nether instead injects at the head of `TrackedEntity#updatePlayer` [src].
- **Leaks the entity filter doesn't stop.**
  - Chunk and block updates. Reign of Nether gates chunk packets per column and serves a disk-backed pre-match snapshot (about 50–500 KB per chunk) for fogged chunks.
  - Sounds and particles, which are broadcast by position.
  - Block-break progress.
  - Plan: v1 hides entities, which is cheap and authoritative. Terrain and block secrecy is a later milestone.
- **Compatibility [src, Moonrise `mc/1.21.1` at 0.1.0-beta.15].** *(corrected: the report only speculated here)*
  - Moonrise redirects the loops in `ChunkMap#move` and `ChunkMap#tick`, but keeps `entityMap`, `TrackedEntity` and the vanilla `updatePlayer`.
  - So the access-transformer approach and a HEAD injection into `updatePlayer` (Reign of Nether style) still apply. Code injected inside `ChunkMap#tick`'s loop would not run.
  - The `broadcastToPlayer` override is the most portable part. Test it at runtime.

### 4.5 RTS state traffic [inf]

Use two-tier replication:

1. Vanilla tracking for units near the commander.
2. One batched, fog-filtered "minimap/army" payload per player at 1–2 Hz, with coarse positions (about 4 B per unit) for the whole map.

Other traffic:

- Orders, production queues and resources: event-driven payloads with per-faction interest management.
- HUD health and state: `SynchedEntityData`, which is delta-synced automatically.

---

## 5. Client RTS camera and UX

### 5.1 Camera approaches [src]

| Approach | How it works | Pros | Cons |
|---|---|---|---|
| A. Mixin `Camera#setup` (TAIL) and move the player | Set position and rotation through the protected `setPosition`/`setRotation`. The commander's own player entity moves with the camera (spectator, flying or invisible) | Chunk loading and tracking follow automatically. Used by Reign of Nether | Mixins into `Camera`; spectator side effects |
| B. Camera entity | A client-only entity set with `Minecraft#setCameraEntity`. [Freecam](https://github.com/MinecraftFreecam/Freecam) (MIT) uses `FreeCamera extends AbstractClientPlayer`; [RTSbuilding](https://github.com/Hcrab/RTSbuilding) (LGPL-3.0, NeoForge 1.21.1) adds a server-side anchor | Clean separation from the player | The server still loads and tracks around the *player*, so RTSbuilding clamps the camera to an action radius |
| C. NeoForge events only | `ViewportEvent.ComputeCameraAngles` (yaw/pitch/roll, posted inside `Camera#setup`) plus `CalculateDetachedCameraDistanceEvent` in third person | No mixins | Camera stays tied to the entity, and `Camera#getMaxZoom` raycasts and pulls it in when terrain is in the way |

- **Projection.**
  - Orthographic projection is possible by injecting at the head of `GameRenderer#getProjectionMatrix`, as Reign of Nether does.
  - With it, `Frustum#offsetToFullyIncludeCameraCube` can loop forever, and Reign of Nether cancels it. That method still exists in 1.21.1.
  - A perspective camera at 50–60° pitch avoids the issue.
- **Cursor.**
  - Call `MouseHandler#releaseMouse()`.
  - In-world, vanilla re-grabs the mouse on the next click (`onPress` → `grabMouse()` when `screen == null`). The cancelable `InputEvent.MouseButton.Pre` fires first, though; cancelling it in RTS mode keeps the cursor free and suppresses attack/use.
  - The alternative is a full-window screen. Reign of Nether's `TopdownGui extends AbstractContainerScreen`. Mouse events are robust there, but vanilla keybinds stop and you poll keys yourself.
  - `MovementInputUpdateEvent` and `CalculatePlayerTurnEvent` neutralize player input.
- **Picking.**
  - Take the cursor from `MouseHandler#xpos()/ypos()` and normalise by `Window#getScreenWidth/Height`, **not** `getWidth/Height`. The latter are framebuffer pixels, 2× on the Retina dev Mac.
  - Normalised device coordinates (NDC) = (2x/w − 1, 1 − 2y/h).
  - Unproject with the inverse of `projection × modelView`, captured in `RenderLevelStageEvent` (`getProjectionMatrix()`, `getModelViewMatrix()`). The model-view matrix is rotation-only, so add `Camera#getPosition()`.
  - Disable view bobbing in RTS mode; it is baked into the projection, together with hurt shake and nausea.
  - Blocks: `level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()))`.
  - Entities: clip AABBs over `ClientLevel#entitiesForRendering()`, or use `ProjectileUtil.getEntityHitResult(level, entity, from, to, box, filter)`.
  - Box selection: project unit positions to the screen and test them against the rectangle.

### 5.2 World rendering [src]

- **Stages.** `RenderLevelStageEvent.Stage` has `AFTER_SKY`, `AFTER_SOLID_BLOCKS`, `AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS`, `AFTER_CUTOUT_BLOCKS`, `AFTER_ENTITIES`, `AFTER_BLOCK_ENTITIES`, `AFTER_TRANSLUCENT_BLOCKS`, `AFTER_TRIPWIRE_BLOCKS`, `AFTER_PARTICLES`, `AFTER_WEATHER` and `AFTER_LEVEL`. The event also exposes the `PoseStack`, `Camera`, `Frustum` and partial tick (`DeltaTracker`).
- **Selection rings, health bars, rally lines.** Draw them in one batched pass per frame at `AFTER_ENTITIES` or `AFTER_TRANSLUCENT_BLOCKS`. Use a `MultiBufferSource.BufferSource` with vanilla `RenderType`s (`lines()`, `debugQuads()`, or a translucent position-colour type), translated by −camera. A few hundred quads is negligible.
- **Static terrain decals** (territory tint, build grid).
  - `AddSectionGeometryEvent` bakes extra geometry into the chunk section meshes.
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
  - The other stages are still posted from vanilla `LevelRenderer` code. Entity rendering stays vanilla-driven, but Sodium patches it *(corrected: the report said it remains vanilla)*:
    - `ModelPart`/`Cube` fast paths
    - shadows
    - culling of entities whose chunk sections are not visible, by wrapping `EntityRenderer#shouldRender`; glowing or name-tagged entities are exempt, so outlined selected units always render
- **Iris [src].**
  - Check `IrisApi.getInstance().isShaderPackInUse()` and `isRenderingShadowPass()`, and degrade gracefully: skip custom post effects and don't draw in the shadow pass.
  - For Sodium 0.8 on NeoForge 1.21.1, Iris exists only as 1.8.14-beta.1 *(added)*.
  - The vanilla outline post-chain under shader packs is unverified. Test it.
- **Compatibility pressure.**
  - Reign of Nether needed `Frustum` and chunk-region mixins.
  - RTSbuilding ships compatibility mixins for Sodium, Embeddium and Flywheel.
  - Camera mods touch the renderer often (unverified generalisation).
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
| Datapack registry: `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec[, Consumer<RegistryBuilder>])` | world load only ([docs](https://docs.neoforged.net/docs/1.21.1/concepts/registries)) | **no** for entries; tags of all registries, including datapack registries, are reloaded *(added)* | automatic if `networkCodec` is given | not possible before decoding |
| Reload listener: `AddReloadListenerEvent#addListener`, e.g. `SimpleJsonResourceReloadListener(Gson, dir)` with `apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)` | world load and `/reload` | yes | manual payload on `OnDatapackSyncEvent` (one player at login, all players on reload) | yes, we control parsing |

Recommended template pipeline [inf]:

1. Scan `data/<ns>/strategos/templates/**.json`.
2. Resolve `"parent"` chains and detect cycles.
3. Deep-merge objects: the child wins, and arrays replace unless an explicit operation is given (`$append`, `$remove`, numeric `$mul`/`$add`), in the spirit of 0 A.D.'s template merge operations.
4. Decode with `Codec#parse(RegistryOps.create(JsonOps.INSTANCE, event.getRegistryAccess()), json)`, honouring NeoForge conditions through `getConditionContext()`. Report every error with its file path.
5. Build an immutable, versioned `TemplateSet` and swap it in atomically on the main thread.
6. Sync it on `OnDatapackSyncEvent` with a compact `StreamCodec` plus a hash, so clients can verify they have the same set.

Where datapack registries still fit:

- Use them only for stable, tag-heavy definitions (civilizations, damage classes).
- NeoForge prefixes modded registry folders with the namespace:
  - elements go in `data/<ns>/<regns>/<regpath>/`
  - tags go in `data/<ns>/tags/<regns>/<regpath>/`
  - see `Registries#elementsDirPath`/`tagsDirPath` [src]
- For unit-class counters, 0 A.D.-style class tokens inside templates are simpler than Minecraft tags.

**Changes in 26.x [doc + src].**

- `SimpleJsonResourceReloadListener` became codec-based (1.21.2 and 1.21.4).
- `AddReloadListenerEvent` was renamed `AddServerReloadListenersEvent` already in NeoForge **21.4.84-beta** (MC 1.21.4), not in 26.3 *(corrected)*.
- NeoForge 26.3 renamed `DataPackRegistryEvent.NewRegistry` to `NewDatapackRegistryEvent`; 26.2 still has the old name [src: NeoForge universal jars].
- Vanilla 26.3 adds *reloadable* datapack registries. Vanilla's own `RegistryDataLoader#RELOADABLE_REGISTRIES` list covers only vanilla registries.
- NeoForge 26.3 adds `NewDatapackRegistryEvent#reloadableRegistry(...)`, so mods can register reloadable datapack registries too *(added)*.

---

## 7. Persistence [src + doc]

- **`SavedData` (1.21.1).**
  - Implement `save(CompoundTag, HolderLookup.Provider)`. Create it with `new SavedData.Factory<>(Supplier<T>, BiFunction<CompoundTag, HolderLookup.Provider, T>, @Nullable DataFixTypes)` and `level.getDataStorage().computeIfAbsent(factory, "strategos_world")`. Keep global state on the overworld.
  - NeoForge copies the data and writes it on an IO worker (21.1.118), and cleans up temp files asynchronously (21.1.146). Serialisation to NBT still happens on the main thread.
  - Autosave runs every 6,000 ticks. Split data by subsystem and region, and `setDirty()` only what changed.
- **Data attachments.**
  - `AttachmentType.builder(...).serialize(Codec)`, optionally `.copyOnDeath()` and `.sync(...)`, on entities, chunks, levels and block entities. Level attachments persist through `LevelAttachmentsSavedData`.
  - `setData` marks chunks dirty; in-place mutation needs `setUnsaved(true)` ([docs](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments)). The 1.21.1 docs page predates the 21.1.197 sync backport and still describes manual syncing.
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
  - `StructureTemplateManager#getOrCreate(ResourceLocation)` loads `data/<ns>/structure/<path>.nbt`. The 1.21.1 folder name is `structure`; generated templates go to `<world>/generated/<ns>/structures`. Templates are reloaded by `/reload` *(added)*.
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
  - Fire removes blocks without an event (only the per-block `onCaughtFire` callback runs). Use non-flammable building blocks, a mixin, or simply the `doFireTick` gamerule *(added)*.
- **Building HP.**
  - Reign of Nether derives HP from the blocks present [src]:
    - 2 HP per block by default, with optional fixed HP per building type.
    - A finished building is destroyed once ≤ 50 % of its blocks remain.
    - When a player mines building blocks, there is a chance (30 % by default) of small explosions that destroy more.
  - Alternative [inf]: abstract, AoE-style HP, with deterministic removal of visual blocks at damage thresholds and re-placement on repair. It is easier to balance and sync.
- **Detecting player edits [inf].** Use the same block-change hook as the nav grid, look the position up in a chunk → building spatial index, and update a per-building placed-block set incrementally. Reign of Nether keeps `placedBlockPosSet`.

---

## 9. Teams and ownership [src]

- **Scoreboard teams.**
  - `PlayerTeam` offers `setColor(ChatFormatting)` (only 16 colours), `setAllowFriendlyFire`, `setSeeFriendlyInvisibles`, `setCollisionRule` and `setNameTagVisibility`.
  - Membership is keyed by scoreboard name, which is the UUID string for non-players (`Scoreboard#addPlayerToTeam`), and is synced to every client.
  - `Entity#getTeam` looks up the scoreboard, `getTeamColor` drives the outline colour, and `EntitySelector.pushableBy` reads the collision rules.
  - The friendly-fire flag is only consulted in `Player#canHarmPlayer`, i.e. player versus player. `AbstractArrow` applies it to player-owned arrows hitting players. Vanilla `Projectile#canHitEntity` ignores teams, and mob AI targeting uses `Entity#isAlliedTo`.
- **Recommendation [inf]: our own faction system.**
  - Each unit and building carries a faction id; the diplomacy matrix lives in `SavedData`.
  - Override `isAlliedTo`, `getTeamColor` (any RGB) and `broadcastToPlayer`.
  - Enforce friendly fire in `LivingIncomingDamageEvent` and `ProjectileImpactEvent`.
  - Optionally mirror factions into scoreboard teams for vanilla UX (coloured names, `/team`), but never depend on them.

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
  - JUnit with a live server [doc]: ModDevGradle `unitTest { enable(); testedMod = mods.strategos }` plus `net.neoforged:testframework` (published for 21.1.262–264) with `@ExtendWith(EphemeralTestServerProvider.class)` ([ModDevGradle README](https://github.com/neoforged/ModDevGradle)).
  - Keep the simulation core in plain Java so it can be unit-tested without Minecraft.
  - 1.21.5 overhauled GameTest into registries [doc], so budget a port.

---

## Architecture implications: the ten constraints that matter most

1. **One 50 ms server thread; units get about 20 ms.**
   - Technique: a stripped `PathfinderMob`, LOD tiers, staggered thinking, per-subsystem time budgets, and MSPT regression tests from week 1.
   - Why: vanilla-AI mobs cost roughly 20–30 µs each (unverified estimate), so without this the budget is gone at a few hundred units.
2. **Cost in dense groups is quadratic.**
   - Technique: `isPushable=false`, a no-op `pushEntities`, and our own spatial hash with ORCA or boids separation.
   - Why: pushing, entity-collision queries and cramming scale with neighbour count. Armies clump, and vanilla also deals cramming damage at 24+ overlapping pushables.
3. **Vanilla pathfinding is local, per-unit and synchronous.**
   - Technique: our own nav grid, HPA*, flow fields and async A* workers. Vanilla `PathNavigation` only as an adapter for following our paths.
   - Why: 0.1–0.5 ms per search, multiplied by group orders, with a short range and storms of block-change invalidations.
4. **World access is main-thread only.**
   - Technique: workers read only immutable snapshots (our grid, `PalettedContainer#copy`); results are applied on the main thread with sequence numbers.
   - Why: off-thread `getChunk` blocks on the server thread, `getChunkNow` returns null, and vanilla caches aren't thread-safe.
5. **The world only simulates near players.**
   - Technique: a data-first model in which `SavedData` records are authoritative and entities are views; abstract off-screen movement; a cap on follow-tickets; bounded, pregenerated maps.
   - Why: units freeze outside entity-ticking chunks and are saved away on unload. Empty levels stop ticking after 300 ticks, and custom region tickets do not prevent that; only forced chunks do *(added)*.
6. **Replication is per entity and per player.**
   - Technique:
     - turn velocity updates off and use an update interval of 3–4
     - filter fog of war with `broadcastToPlayer` plus forced re-evaluation
     - add a coarse batched minimap channel
     - build a custom delta channel only if measurement demands it
   - Why: about 0.35–0.55 Mbit/s per client at 300 moving units. In vanilla, visibility is re-evaluated only on section changes and viewer movement; under Moonrise it is re-evaluated every tick *(corrected)*.
7. **Chunk loading and tracking are centred on the player entity, not the camera.**
   - Technique: the commander's player follows the camera, a `Camera` mixin, the cursor freed via the cancelable Pre event, a raised entity render distance, and Retina-correct picking.
   - Why: the server sends chunks and entities around the player.
8. **Rendering APIs churn and must coexist with Sodium and Iris.**
   - Technique: few custom draws, vanilla `RenderType`s, NeoForge events, an isolated client module, and a test matrix of Sodium 0.8.x and Iris 1.8.x (beta).
   - Why: eight rendering reworks between 1.21.2 and 26.3. Sodium dispatches only some of the hooks itself and also culls entities by section visibility.
9. **Data reload semantics differ between mechanisms.**
   - Technique: templates through a reload listener with our own inheritance and a codec pass; datapack registries only for stable definitions; explicit client sync.
   - Why: 1.21.1 datapack registries neither reload their entries (only their tags reload) nor support parent templates.
10. **Save formats and vanilla team semantics will bite.**
    - Technique: codec-first persistence, schema versions with migrations, fixture-save tests, and our own faction model instead of scoreboard teams.
    - Why: 1.21.5–26.1 replace `SavedData`, NBT access and `ResourceLocation`. Scoreboard teams have 16 colours and string keys, and projectiles ignore them.

---

## Open questions (updated by the fact-check)

1. **Still open.** Actual per-tick cost on the M4 Pro of a stripped `PathfinderMob` versus a `LivingEntity`-based unit (idle, moving, fighting). The 20–30 µs vanilla figure and the ~10 µs stripped target are extrapolations from third-party benchmarks on other CPUs, MC versions and heavily modded packs.
2. **Still open.** Client FPS with 300–1,000 animated humanoid units, vanilla models versus GeckoLib, under Sodium 0.8.x on Apple silicon. GeckoLib 4.9.3 (2026-09-16, MIT) is the current NeoForge 1.21.1 build. Sodium's section-visibility entity culling applies to these units too.
3. **Partly answered from source.** Moonrise's tracker (`mc/1.21.1` at 0.1.0-beta.15) keeps `TrackedEntity` and the vanilla `updatePlayer`, and therefore `Entity#broadcastToPlayer`, and calls it every tick per nearby player. Access-transformer or HEAD-inject re-evaluation still applies. Runtime behaviour is untested.
4. **Still open.** Behaviour of the vanilla glowing-outline post-chain and of custom `RenderLevelStageEvent` geometry under Iris shader packs on NeoForge 1.21.1. Iris for Sodium 0.8 is a beta.
5. **Still open, with a data point.** Feasibility and cost of terrain-level fog-of-war secrecy for maps larger than about 512×512 on 1.21.1. Reign of Nether bounds it to world borders ≤ 1,280 blocks and stores a disk-backed snapshot of about 50–500 KB per chunk.
6. **Product decision.** Whether multiplayer over the internet is a first-class target (bandwidth design) or the product starts with singleplayer/LAN versus AI. This changes how much custom replication is needed.
7. **Still open.** Perspective versus orthographic RTS camera: UX preference, plus the frustum/culling side effects with Sodium 0.8 on NeoForge. Reign of Nether's experience is on Forge 1.20.1.
8. **Partly answered.** NeoForge 26.3.x's `ChunkMap` patch still references `entityMap`, `TrackedEntity#seenBy` and `updatePlayer`. The primers up to 26.3 list no removal of `Entity#broadcastToPlayer` (unverified).
9. **Still open.** Exact memory and CPU cost of a per-chunk nav grid over a Y band for a 1–2k block map, and the right cell encoding (bits per cell, layered columns for caves and bridges). Needs a prototype measurement.

---

## Verification notes

**Scope.** Every claim tagged time-sensitive or decision-critical was re-checked, plus every other statement that could change a decision: versions, maintenance status, licences, API names and behaviour, availability on 1.21.1, benchmark figures and arithmetic. The structured verdict list contains about 60 entries.

**Primary sources used.**

- Minecraft and NeoForge source, read-only, nothing built:
  - the decompiled Minecraft 1.21.1 + NeoForge 21.1.262 tree (`scratchpad/mc-src-1.21.1`);
  - the MDK (`scratchpad/mdk-1.21.1`); no Gradle was run.
- NeoForge Maven:
  - `maven-metadata.xml` and POM `Last-Modified` headers;
  - changelogs for 21.1.264, 21.3.100, 21.4.161, 21.5.101, 21.6–21.11, 26.1, 26.2 and 26.3;
  - the class lists of the universal jars for 21.3.100, 21.4.83-beta, 21.4.84-beta, 21.4.161, 21.5.101, 21.8.57, 21.11.48, 26.1.2.122, 26.2.0.93 and 26.3.0.95;
  - the `testframework` and ModDevGradle metadata;
  - FML `loader-4.0.45.jar`, inspected with `javap`.
- Modrinth v2 API: versions, licences and project bodies for Fabric API, Lithium, Moonrise, Sodium, Iris, spark, Reign of Nether, Async, Laminar, ServerCore and GeckoLib.
- GitHub: branch heads and licences through the API, plus raw files and git trees:
  - Reign of Nether `1.20.1-dev`@3943983
  - MineColonies `version/1.21`@dae860e
  - Baritone `1.21.1`@11ea66c
  - Sodium `1.21.1/stable`@272a4aa
  - Iris `1.21.1`@eb7afb9
  - Freecam@6868852
  - RTSbuilding@8110237
  - NeoForge `26.3.x`@407e6e2
  - neoforged/.github primers@2bb6f9f
  - Moonrise `mc/1.21.1`@2eae1b1
  - the Lithium mixin-config docs on the `1.21.1` and `develop` branches
  - the PathWeaver README
- Other references:
  - the NeoForge 1.21.1 docs pages;
  - spark docs;
  - Factorio FFF #317;
  - the Game AI Pro ch. 23 PDF;
  - the HPA* paper (Wayback copy);
  - 0 A.D. forum;
  - Geekbench aggregates, via search.

**What changed, in order of decision impact.**

1. **Loader and API timeline.** `AddReloadListenerEvent` became `AddServerReloadListenersEvent` in NeoForge 21.4.84-beta, not 26.3. NeoForge 26.3 additionally lets mods register reloadable datapack registries. `/reload` already reloads tags of datapack registries in 1.21.1.
2. **Lithium on 1.21.1.** The pathing optimisation (`mixin.ai.pathing`) is off by default in 0.15.x. Only `inactive_navigations` is on.
3. **Benchmarks.**
   - PathWeaver's figures were merged from two runs and misread: pathfinding was 40 % of tick work, not 12 %, and about 60 % of path cost moved off-thread, not 3/4.
   - Async's baseline was a heavily optimised pack.
   - Laminar ran on 26.1.x.
   - The capacity arithmetic was inconsistent and is now corrected.
4. **Unit recipe.**
   - `MovementEmission.SOUNDS` still emits `GameEvent.STEP`; use `NONE`.
   - The entity cull distance depends on render distance: about 101 blocks at the default, not 67.
   - The invulnerability-frame precision was added.
5. **World simulation.** Custom region tickets do not keep a player-less level ticking. NeoForge also posts per-tick events per unit: `EntityTickEvent`, `LivingBreatheEvent` and `MobDespawnEvent`.
6. **Tracker.** The tracker pass runs before the entity loop. Moonrise re-evaluates `broadcastToPlayer` every tick and keeps `TrackedEntity`, so the fog-of-war approach works under it.
7. **Sodium.** Sodium does modify entity rendering (fast paths, section-visibility culling, shadows).
8. **Status and licensing.**
   - Baritone's 1.21.1 branch is deprecated.
   - Iris for NeoForge 1.21.1 with Sodium 0.8 is a beta.
   - Copying GPL-3.0 code requires releasing Strategos under GPLv3.
   - The HPA* link is dead; it now points to an archived copy.
9. **Smaller precisions.**
   - `SynchedEntityData` ids run 0–254.
   - NeoForge has no catch-all block-change event.
   - The split-packet size is capped since 21.1.260.
   - Templates reload on `/reload`.
   - `doFireTick` is the simplest fire guard.
   - Sections are iterated per X column in `getEntities`.

**Still unverified.**

- The per-mob cost and capacity estimates.
- Client rendering limits with hundreds of animated units.
- The outline post-chain under Iris.
- TCP/IP overhead figures.
- Moonrise runtime behaviour; it was only read from source.
- JVM-on-ARM performance of the M4 Pro.
- Whether `Entity#broadcastToPlayer` survives unchanged in 26.x.

Everything else in the report was confirmed against source or primary documents.

## Recommendations (after verification)

1. 1. Before any features, build a performance harness. Use a GameTest or dev-command world that spawns N units (idle, moving, fighting, group-ordered) and records MSPT with /tick sprint, spark and /neoforge track (durations are in seconds). Gate CI on regressions. Use it in week 1 to choose PathfinderMob or LivingEntity as the unit base, and to replace the unverified 20-30 us and 10 us per-unit estimates with M4 Pro measurements. Run baselines with and without Lithium. In Lithium 0.15.x for 1.21.1, mixin.ai.pathing is off by default, so enable it explicitly if you want its pathing gains.
2. 2. Implement units as a stripped PathfinderMob subclass: (a) no goals or brain; run the controller in customServerAiStep, staggered by (tickCount+id). (b) isPushable=false and a no-op pushEntities. (c) persistent, with checkDespawn as a no-op, which also removes the per-tick MobDespawnEvent; no loot pickup. (d) getMovementEmission() returns NONE; SOUNDS still emits GameEvent.STEP in 1.21.1. (e) STEP_HEIGHT 1.0 and a custom MoveControl. (f) velocity updates off; updateInterval 3-4. (g) shouldRenderAtSqrDistance set to our own fixed distance; vanilla culls at about 67 blocks at render distance 8 and about 101 at the default 12. (h) invulnerability frames neutralised via LivingIncomingDamageEvent/DamageContainer or the minecraft:bypasses_cooldown tag; inside the window, stronger hits deal only the difference.
3. 3. Adopt a data-first architecture. A plain-Java simulation core (factions, economy, orders, unit/army/building records) is persisted in SavedData through Codecs with schema versions. Entities are views, materialised only in entity-ticking chunks. This keeps the core unit-testable without Minecraft and survives the 1.21.5, 1.21.6 and 26.1 persistence changes.
4. 4. Write our own pathfinding stack instead of vanilla PathNavigation. (a) A compact per-chunk nav grid built on the main thread with a per-tick budget, published as copy-on-write snapshots. Invalidate it from a mixin on LevelChunk#setBlockState or ServerLevel#onBlockStateChange, because NeoForge has no catch-all block-change event. (b) An async A* worker pool that reads only snapshots, with results applied on the main thread by sequence number. (c) Then HPA* clusters, flow fields for group orders, ORCA separation (port RVO2, Apache-2.0, keeping its licence and NOTICE) and formation slots.
5. 5. Use bounded, pregenerated match maps (world border plus /neoforge generate). Move off-screen armies and traders abstractly in the data layer. Allow ticket-following (a custom TicketType with a lifespan, or a NeoForge TicketController) only for a capped number of groups that must physically simulate. Custom TicketType region tickets do not keep a player-less level ticking: after 300 ticks only vanilla forced chunks or TicketController tickets do. Any level that must simulate without players needs at least one such forced chunk.
6. 6. Networking: (a) rely on vanilla tracking, tuned per EntityType. (b) Implement fog of war via Entity#broadcastToPlayer on our classes, kept O(1), plus a periodic re-evaluation pass using an access transformer on ChunkMap.entityMap and ChunkMap$TrackedEntity. Under Moonrise, updatePlayer already runs every tick per nearby player, so the pass is redundant there but harmless. (c) Add a batched, fog-filtered 1-2 Hz minimap/army payload and event-driven order payloads. (d) Build a custom delta movement channel only if measurements exceed roughly 2 Mbit/s per client. (e) Require NeoForge >= 21.1.264 if using predicate-filtered attachment sync.
7. 7. Camera: make the commander's player entity follow the camera, in a spectator-like or flying invisible mode, so chunk loading and tracking follow it. Keep spectatorsGenerateChunks on if the commander is a spectator. Use a Camera#setup mixin and start with a perspective projection; an orthographic one triggers the Frustum#offsetToFullyIncludeCameraCube loop. Free the cursor by cancelling InputEvent.MouseButton.Pre. Do Retina-correct unprojection (Window#getScreenWidth/Height) with matrices captured in RenderLevelStageEvent, and disable view bobbing.
8. 8. Keep client rendering minimal and isolated behind our own interfaces: (a) batched vanilla RenderTypes in RenderLevelStageEvent; (b) AddSectionGeometryEvent only for static overlays; (c) outlines via isCurrentlyGlowing/getTeamColor overrides, which also exempt selected units from Sodium's entity culling; (d) HUD via RegisterGuiLayersEvent. Test matrix: Sodium 0.8.13, Iris 1.8.14-beta.1 (the only Iris build for Sodium 0.8 on NeoForge 1.21.1, and a beta), Lithium 0.15.4, optionally Moonrise 0.1.0-beta.15. Include a check that units still render under Sodium's section-visibility entity culling with a high RTS camera.
9. 9. Data-driven content: put unit, building and tech templates in a reload listener (AddReloadListenerEvent on 1.21.1; it was renamed AddServerReloadListenersEvent from NeoForge 21.4.84). Add our own parent resolution and deep merge with 0 A.D.-style operators. Decode with Codecs and RegistryOps, and sync to clients on OnDatapackSyncEvent with a hash. Use datapack registries only for stable, tag-heavy definitions; their tags reload on /reload, their entries do not. When porting to 26.3, consider NeoForge's NewDatapackRegistryEvent#reloadableRegistry for templates.
10. 10. Buildings: parse vanilla .nbt structures into our own immutable Blueprint (templates also reload on /reload). Construct progressively with a precomputed order and flags UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE, under a global per-tick placement budget. Protect blocks via BlockEvent, ExplosionEvent, LivingDestroyBlockEvent, EntityMobGriefingEvent and PistonEvent. Handle fire with the doFireTick gamerule, non-flammable blocks or a mixin, since fire fires no event. Start with abstract HP plus deterministic visual damage, and keep a chunk-to-building index for edit detection.
11. 11. Build our own faction system (ids, a diplomacy matrix, any RGB colour) with overrides of isAlliedTo, getTeamColor and broadcastToPlayer. Enforce friendly fire in LivingIncomingDamageEvent and ProjectileImpactEvent. Mirror factions into scoreboard teams only for cosmetics.
12. 12. Ship dev tooling early: /strategos debug commands, and a dev-only overlay (nav grid, paths, flow fields, fog grid, LOD tier per unit) gated by FMLEnvironment.production and fed by an optional dev payload. Add JUnit via ModDevGradle unitTest and the NeoForge testframework (published for 21.1.262-264), plus GameTests run with neoforge.enabledGameTestNamespaces.
13. 13. Licensing: decide the project licence early. Copying GPL-3.0 code from Reign of Nether or MineColonies would force Strategos itself to be released under GPL-3.0; a merely GPL-compatible permissive licence is not enough. Study their designs instead. Port RVO2 (Apache-2.0: keep the licence and NOTICE) and PathWeaver ideas (MIT: keep the notice) with attribution. Treat Baritone only as a design reference; its 1.21.1 branch has been deprecated since 2026-09-01.

## Fact-check verdicts

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | NeoForge for 1.21.1 is still maintained: 21.1.264 was published 2026-10-19, the official MDK pins 21.1.262 (2026-10-15), and backports continue. | None. 21.1.263 (2026-10-18) also exists; 21.1.260-262 were network-hardening fixes. | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (newest 21.1.x = 21.1.264). POM Last-Modified: 262 = 2026-10-15 01:43 GMT, 263 = 2026-10-18 13:35 GMT, 264 = 2026-10-19 03:40 GMT. 21.1.264 changelog line 1: 'Backport to 1.21.1: Check AttachmentSyncHandler#syncToPlayer() for initial sync (#3670)'. mdk-1.21.1/gradle.properties: neo_version=21.1.262. |
| confirmed | The newest NeoForge line is 26.3.0.95 (2026-10-20), LGPL-2.1. | None. | maven-metadata.xml latest/release = 26.3.0.95; its POM Last-Modified = 2026-10-20 02:29 GMT. GitHub license API for neoforged/NeoForge: LGPL-2.1. |
| confirmed | Version table: Fabric API 0.116.17 (2026-09-01, Apache-2.0); Lithium NeoForge 0.15.4 (2026-06-27, LGPL-3.0); Moonrise NeoForge 0.1.0-beta.15 (2025-10-10, GPL-3.0); Sodium NeoForge 0.8.13 (2026-08-28, PolyForm Shield 1.0.0); spark NeoForge 1.10.124 (2025-02-23, GPL-3.0). | None. | Modrinth v2 API /project/{fabric-api,lithium,moonrise-opt,sodium,spark}/version filtered by loader and game_versions=[1.21.1], queried 2026-10-20. Licence fields: Apache-2.0, LGPL-3.0-only, GPL-3.0-only, LicenseRef-Polyform-Shield-1.0.0, GPL-3.0-only. |
| corrected | Iris for NeoForge 1.21.1 is 1.8.14-beta.1 (2026-06-13), LGPL-3.0. | Version, date and licence are right, but it is a beta (changelog: 'Updates to Sodium 0.8'). The last stable NeoForge 1.21.1 release is 1.8.12 (2025-06-17), which predates Sodium 0.8. | Modrinth /project/iris/version?loaders=[neoforge]&game_versions=[1.21.1]: 1.8.14-beta.1 version_type=beta, then 1.8.12 release 2025-06-17. Iris branch 1.21.1 head eb7afb9 (2026-06-13) 'Update to latest Sodium 1.21.1 beta'. |
| confirmed | Reign of Nether 1.5.0b (2026-10-14) is Forge 1.20.1 only and GPL-3.0. MineColonies version/1.21 targets NeoForge 1.21.1 (commit 2026-10-06), GPL-3.0. | None. | Modrinth reign-of-nether-rts 1.5.0b, published 2026-10-14T13:40Z, loaders [forge], game version 1.20.1. RoN build.gradle: forge 1.20.1-47.4.0; LICENSE.txt is GPLv3. GitHub API branch heads: RoN 1.20.1-dev 3943983 (2026-10-14T13:54Z); minecolonies version/1.21 dae860e (2026-10-06T07:14Z, gradle.properties forgeVersion=21.1.80, minecraftVersion=1.21.1). GitHub license API: GPL-3.0 for both. |
| corrected | Baritone's 1.21.1 branch has a commit from 2026-09-01; LGPL-3.0. | The 2026-09-01 commit is titled 'deprecate 1.21.1'. The branch README now says it is deprecated and will no longer receive updates, and that primary development is on 1.21.4. LGPL-3.0 is correct. | GitHub API: cabaletta/baritone branch 1.21.1 = 11ea66cd, 2026-09-01T22:29Z, 'deprecate 1.21.1'. README.md on that branch: 'This branch of Baritone is deprecated. It will no longer recieve updates.' |
| corrected | Mob#serverAiStep is final. The selectors run a full tick when (tickCount + id) is even and only tickRunningGoals(false) otherwise. customServerAiStep() is the only override point inside. | The mechanics are right. customServerAiStep() is the only empty hook, but the navigation (createNavigation) and the move/look/jump controls (protected fields) can also be replaced by our own classes. | mc-src-1.21.1/net/minecraft/world/entity/Mob.java:773-812 (final serverAiStep, i % 2 logic), 108-117 (protected lookControl/moveControl/jumpControl/navigation), 163 (createNavigation). |
| confirmed | Entity#checkDespawn runs every tick for every entity in the ticking list, before the range gate. Mob#checkDespawn calls getNearestPlayer unless the mob is persistent. | Added detail: Mob#checkDespawn first posts NeoForge's MobDespawnEvent, every tick for every Mob. A no-op override also removes that event. | ServerLevel.java:403-423 (checkDespawn at 409, inEntityTickingRange at 411). Mob.java:745-770. neoforge/event/EventHooks.java:380-391. |
| confirmed | Idle living entities still run Entity#move every tick (gravity), including an entity-section collision query. pushEntities queries neighbours every tick, and 24+ overlapping pushables give a 1-in-4 chance per tick of 6 cramming damage, so dense groups cost O(n^2). | None. | LivingEntity.java:2721-2848 (aiStep -> travel -> pushEntities), 2383-2386 (handleRelativeFrictionAndCalculateMovement -> move). Entity.java:619, 901-903 (collide -> getEntityCollisions). EntityGetter#getEntityCollisions -> getEntities. LivingEntity.java:2891-2915 (cramming). GameRules.java:98-100 (maxEntityCramming 24). EntitySelector.java:50-75 (pushableBy requires isPushable). |
| confirmed | ServerLevel#sendBlockUpdated loops over navigatingMobs on every block-shape change and may recompute paths synchronously (at most once per 20 ticks per mob), so construction costs O(blocks x mobs). Lithium's inactive_navigations mitigates this. | Precision: navigatingMobs holds every loaded Mob (added on entity tracking start). The loop runs only when the collision shape changes and the setBlock flags include UPDATE_CLIENTS. inactive_navigations is on by default in Lithium for 1.21.1. | ServerLevel.java:1080-1110, 1781. Level.java:280-284 (flag 2 gate). PathNavigation.java:91-101, 424-434. Lithium 1.21.1 lithium-neoforge-mixin-config.md: mixin.entity.inactive_navigations default true. |
| corrected | Replication: after the entity loop, ChunkMap#tick visits every tracked entity. | ChunkMap#tick (the tracker) runs inside ServerChunkCache#tick, which ServerLevel#tick calls before the entity loop. Each tick therefore ships the previous tick's state. The cost statement is unchanged. | ServerLevel.java:382 (getChunkSource().tick) runs before 403 (entityTickList.forEach). ServerChunkCache.java:316-330 (chunkMap.tick() at 325). ChunkMap.java:1151-1182. |
| confirmed | ChunkMap#move(ServerPlayer) runs on each movement packet and calls TrackedEntity#updatePlayer for all tracked entities, so a panning commander costs O(entities) per tick. | Added: Moonrise disables this loop. Instead it calls updatePlayer every tick for every player near each tracked entity. | ChunkMap.java:1024-1031. ServerGamePacketListenerImpl.java:970. Moonrise mc/1.21.1@2eae1b1: mixin/entity_tracker/ChunkMapMixin.java (skipMoveTrackerUpdate, newTrackerTick) and TrackedEntityMixin.java (moonrise$tick). |
| confirmed | Level#getEntities walks a sorted section-id set over the AABB (grown 2 blocks horizontally and 4 down). Sections index entities by class (ClassInstanceMultiMap). | Precision: for each X section column the box spans, it iterates every existing entity section in that column (all Z and Y) and then filters by Y and Z. The cost therefore also grows with the loaded area along Z. | mc-src-1.21.1/net/minecraft/world/level/entity/EntitySectionStorage.java:35-63 (subSet over asLong(x,0,0)..asLong(x,-1,-1)). EntitySection.java:14-19. |
| confirmed | A level with no players stops ticking entities after 300 ticks unless it has forced chunks; NeoForge also counts its own ticket-controller chunks. | Caveat added: only ForcedChunksSavedData entries count (vanilla /forceload and NeoForge TicketController tickets). Plain TicketType region tickets, such as the report's follow-tickets, do not keep an empty level ticking. | ServerLevel.java:390-395. neoforge/common/world/chunk/ForcedChunkManager.java:68-72. |
| corrected | PathWeaver: about 480-500 us per path request; 1,024 zombies retargeting every 6 ticks gave 83-97 ms mean ticks and p99 832-958 ms on a 16-core desktop; staggered (~51 req/tick) held 20 TPS with p95 59-67 ms and p99 77-80 ms. | The report merges two runs. Shipped config (0.6.1 jar, Lithium loaded, measured 2026-07-31; hardware not stated for this run): synchronous mean tick interval 88.5-96.6 ms, p99 832-958 ms, 480-500 us main-thread cost per request. Earlier gate-forced runs on a 16-core/32-thread desktop: mean 83-87 ms, p95 738-766 ms; staggered: 20 TPS, p95 59-67 ms, p99 77-80 ms. All figures are wall-clock tick intervals, not MSPT. | https://github.com/zimdin12/PathWeaver README (raw, fetched 2026-10-20): sections 'Measured on the configuration you would actually get' and 'Earlier figures, measured with the gate forced open'. Repo licence: MIT. |
| corrected | PathWeaver spark profile: pathfinding takes 12.4% of server-thread time (15.5 ms/tick). | 12.38% is the share of the sampled server thread, which was 67-74% idle. 15.47 ms/tick is the whole tick in the off arm, not the pathfinding time. Pathfinding was about 6.2 ms/tick, 40.3% of tickServer. A synthetic retarget probe accounts for 3,604 of the 5,572 ms. | PathWeaver README, section 'Profiled on a real modpack, with spark'. |
| corrected | PathWeaver moves about 3/4 of path cost off the main thread. | About 60%: main-thread cost per request fell from 480-500 us to 195-202 us; spark share fell from 12.38% to 4.92%; share of tickServer fell from 40.3% to 19.8%. | PathWeaver README, same sections. |
| corrected | Async benchmark: 9,000 villagers, MC 1.21.4, Ryzen 9 7950X3D, with Lithium: 225.4 ms/tick (4.4 TPS), about 25 us per villager. | The numbers match the page, but the baseline is not vanilla plus Lithium. The pack also had ServerCore (entity activation range, villager lobotomization), Concurrent Chunk Management Engine, ScalableLux, TT20, Carpet and others. 25 us is not a clean vanilla per-mob cost. Async itself now requires MC 26.1+ and Java 25. | https://modrinth.com/mod/async (Modrinth API project body: test configuration and mod list). https://modrinth.com/mod/servercore body (Entity Activation Range, villager lobotomization). |
| corrected | Laminar benchmark: 500 zombies, 32 chunks, i7-4770K, MC version not stated; vanilla median 31.1 ms, p95 50.5 ms (~60 us per zombie). | The numbers are confirmed. The table does not state the MC version, but the benchmarked Laminar v1.2.7 was published only for 26.1-26.1.2 (Fabric), so the run was on 26.1.x. 62 us is the whole tick divided by 500, an upper bound. | Modrinth laminar-engine project body and version list (1.2.7 published 2026-06-13 for 26.1, 26.1.1, 26.1.2; licence All-Rights-Reserved). |
| unverifiable | Inference: vanilla-AI mobs cost 20-30 us; 1,000-1,500 fill a 50 ms tick; 600-800 vanilla or 1,500-2,000 stripped (10 us) units fit a 20 ms budget. | The inputs are not clean vanilla figures, and the arithmetic is inconsistent. At 20-30 us per mob, 50 ms holds about 1,700-2,500 mobs and 20 ms holds about 670-1,000. The report's 600-800 per 20 ms implies 25-33 us; 1,000-1,500 per 50 ms implies 33-50 us. This must be measured on the M4 Pro. | Arithmetic on the sources above. |
| confirmed | Public single-thread scores put the M4 Pro at or above the Zen 4 desktops in these tests. | Indicative only: Geekbench 6 single-core is about 3,900 for the M4 Pro versus about 3,000 for the 7950X3D. JVM performance on ARM was not checked. | Web search: nanoreview.net M4 vs 7950X3D (3,777 vs 3,003); techspot and tweaktown M4 Pro/Max GB6 single-core 3,925. |
| confirmed | maxVisitedNodes = floor(FOLLOW_RANGE x 16) is fixed at construction (256 default, 560 for zombies). Region radius is followRange+8 (+16 for entities). The heuristic is weighted x1.5, accuracy is Manhattan, partial paths are returned, and the region holds live chunks from getChunkNow with unloaded chunks reading as air. | None. | PathNavigation.java:62-66, 129-169. Mob.java:160. monster/Zombie.java:122. pathfinder/PathFinder.java:21, 55-117 (h * 1.5F at 102). PathNavigationRegion.java:48 and getChunk (EmptyLevelChunk fallback). |
| confirmed | PathTypeCache is one 4096-slot direct-mapped cache per ServerLevel, built from two plain arrays, invalidated in sendBlockUpdated, and not thread-safe. WalkNodeEvaluator uses 8 neighbours and step-up max(1, maxUpStep). | None. | pathfinder/PathTypeCache.java (SIZE 4096, long[] positions, PathType[] pathTypes). ServerLevel.java:192, 1087. WalkNodeEvaluator.java:127-152. |
| confirmed | Off-thread: ServerChunkCache#getChunk joins a main-thread future, getChunkNow returns null, PalettedContainer#copy clones palette and storage, RenderChunk copies sections for meshing, and get() is lock-free with only set/getAndSet using the ThreadingDetector. | Precision: read/write(FriendlyByteBuf) also acquire the detector. 'Stale or null under concurrent writes' is an inference from the code, not an observation. | ServerChunkCache.java:136-138, 173-175. chunk/PalettedContainer.java:119-149, 158-165, 176-196, 290-292. client/renderer/chunk/RenderChunk.java:42. |
| corrected | Level#markAndNotifyBlock calls ServerLevel#onBlockStateChange for every setBlock that changes state, and NeoForge BlockEvents only cover player and entity actions. | The first part is confirmed; the call is deferred while NeoForge block-snapshot capture is active. The second part is wrong: BlockEvent also includes NeighborNotifyEvent, FluidPlaceBlockEvent, FarmlandTrampleEvent, PortalSpawnEvent and BlockToolModificationEvent. There is no catch-all block-change event. | Level.java:233-301 (snapshot gate 244-261, onBlockStateChange at 300). neoforge/event/level/BlockEvent.java:69-295. |
| confirmed | Reign of Nether uses a LevelChunk#setBlockState mixin, a budgeted dirty-chunk drain and stale-but-walkable serving; RtsPathfinder snapshots and hands off to PathfinderWorkerPool; grid warm-up MUST run on the main thread; results become vanilla Path; gamerules pathfindingThreads and pathfindingChunkBuildsPerTick. | Added: the custom pathfinder (gamerule rtsPathfinding, default off) is enabled automatically only on maps whose world border is at most 1,280 blocks. | RoN 1.20.1-dev@3943983: mixin/LevelChunkMixin.java; unit/pathfinding/WalkabilityGrid.java:24-28, 102-139; PathfinderWorkerPool.java:43-45; RtsPathfinder.java:18-58; registrars/GameRuleRegistrar.java:107-122; worldborder/WorldBorderServerEvents.java:21-35. |
| confirmed | MineColonies runs job-based A* on one worker (ThreadPoolExecutor(1,1), queue 10,000) over a ChunkCache of live LevelChunk references. | None. | minecolonies version/1.21@dae860e: core/entity/pathfinding/Pathfinding.java:17, 50; world/ChunkCache.java:62-85; pathjobs/AbstractPathJob.java:188. |
| confirmed | Baritone: client-side A* with cost coefficients {1.5..10}, primary/failure timeouts, segmented paths, ClientChunkCache reads, cached 512x512 regions at 2 bits per block. | None, except that the branch is deprecated (see the Baritone verdict above). | baritone 1.21.1@11ea66c: AbstractNodeCostSearch.java:69, 100; api/Settings.java:598-610; cache/CachedRegion.java:50-52 (32x32 chunks); CachedChunk.java:118-180; utils/BlockStateInterface.java:44-69. |
| corrected | Lithium gives faster node-type lookups and chunk access for vanilla pathing and skips inactive navigations on block updates. | On the 1.21.1 builds (0.15.x, Fabric and NeoForge), mixin.ai.pathing defaults to false and must be enabled in the config; it is on by default only on newer Lithium (develop). inactive_navigations defaults to true. The report's link points to the develop-branch doc. | raw.githubusercontent.com/CaffeineMC/lithium/1.21.1/lithium-neoforge-mixin-config.md and lithium-fabric-mixin-config.md: '### mixin.ai.pathing (default: false)'. The develop branch doc says '(default: true)'. |
| corrected | Licensing: GPL-3.0 code (RoN, MineColonies) can be copied only if Strategos ships under a GPL-compatible licence. RVO2 (Apache-2.0), PathWeaver (MIT) and Baritone (LGPL-3.0) are usable references. | The licences are right. Copying GPL-3.0 code makes the combined work GPLv3: Strategos would have to be distributed under GPL-3.0 (RoN is GPL-3.0-only; MineColonies is GPL-3.0-or-later on Modrinth). A merely 'GPL-compatible' permissive licence is not enough. Apache-2.0 requires keeping the licence and NOTICE; MIT requires keeping the notice. | GitHub license API: RoN GPL-3.0, minecolonies GPL-3.0, baritone LGPL-3.0, snape/RVO2 Apache-2.0, zimdin12/PathWeaver MIT. Modrinth licence fields. RoN LICENSE.txt. |
| confirmed | Ticket levels are 33 accessible, 32 block ticking, <=31 entity ticking. addRegionTicket sets level 33 - distance and feeds the simulation tracker. TicketType.create takes a lifespan; PORTAL lives 300 ticks. | The forceTicks overload is a NeoForge addition. | server/level/ChunkLevel.java:10-12. DistanceManager.java:194-202, 256-257. ServerChunkCache.java:445-450. TicketType.java:17-25. |
| confirmed | NeoForge TicketController(ResourceLocation, @Nullable LoadingValidationCallback), registered in RegisterTicketControllersEvent (mod bus); forceChunk(level, BlockPos\|Entity\|UUID, x, z, add, ticking); persisted in ForcedChunksSavedData and revalidated; distance 2 (level 31); ticking=true enables random ticks without players. | Added: ticking=true also enables natural spawning in that chunk. | neoforge/common/world/chunk/TicketController.java:27-67. RegisterTicketControllersEvent.java:16 (IModBusEvent). ForcedChunkManager.java:81-121, 129-145. ServerChunkCache.java:367-375. |
| confirmed | Entity storage, spectators and chunk tooling: entities saved to entities/ with UNLOADED_TO_CHUNK; spectatorsGenerateChunks default true (ChunkMap#skipPlayer); /neoforge generate; ChunkTicketLevelUpdatedEvent; ChunkEvent.Load/Unload. A distance-3 ticket gives 3x3 entity ticking and about 7x7 loaded chunks. | None. | ServerLevel.java:241-242. PersistentEntitySectionManager.java:235. GameRules.java:89-91. ChunkMap.java:992-994. server/command/GenerateCommand.java:32. event/level/ChunkTicketLevelUpdatedEvent.java and ChunkEvent.java:55, 87. ChunkLevel/TickingTracker propagation (+1 per ring). |
| confirmed | 26.x tickets: 1.21.2 placeEnderPearlTicket; 1.21.5 TicketType became a registered record in BuiltInRegistries.TICKET_TYPE, with TicketStorage and addTicketWithRadius; 1.21.9 added FLAG_*; TicketController still exists on NeoForge 26.3.x. | None. | neoforged/.github primers: 1.21.2/index.md:2151; 1.21.5/index.md:301-344; 1.21.9/index.md:2130-2139. neoforge-26.3.0.95-universal.jar contains common/world/chunk/TicketController.class, as do the 21.3-26.2 jars. |
| confirmed | Payload API (registrar, playToClient/Server/Bidirectional, executesOn MAIN by default, versioned, optional; PacketDistributor methods); limits 1 MiB clientbound / 32,767 B serverbound; GenericPacketSplitter; bundles of up to 4,096 packets. | Added: since 21.1.260, reassembled split packets are capped by splitPacketMaxSizeLimitMb (default 128 MB). | network/registration/PayloadRegistrar.java:29, 44-62, 129-158. RegisterPayloadHandlersEvent.java:40. PacketDistributor.java:38-113. ClientboundCustomPayloadPacket.java:36. ServerboundCustomPayloadPacket.java:16. BundlerInfo.java:11. filters/GenericPacketSplitter.java:45-60, 157. NeoForgeConfig.java:90-93. |
| corrected | SynchedEntityData allows at most 254 ids per class hierarchy. | The maximum id value is 254, so ids 0-254 give 255 slots per hierarchy. Entity, LivingEntity and Mob already use 16 of them. | network/syncher/SynchedEntityData.java:25 (MAX_ID_VALUE = 254), 53-56 ('if (i > 254) throw'). |
| confirmed | Spawn data and entity-data hooks: IEntityWithComplexSpawn, IEntityExtension#sendPairingData, PlayerEvent.StartTracking/StopTracking, NeoForgeRegistries.ENTITY_DATA_SERIALIZERS. | None. | neoforge/entity/IEntityWithComplexSpawn.java. common/extensions/IEntityExtension.java:396. event/entity/player/PlayerEvent.java:261, 281. registries/NeoForgeRegistries.java:34. |
| confirmed | Attachment sync was backported in 21.1.197; until 21.1.264 the per-player predicate was not checked on initial sync. | None. | 21.1.264 changelog lines 1 and 135. 21.1.262 attachment/AttachmentSync.java:154-186 (syncInitialAttachments has no sendToPlayer check), while syncUpdate at 85-108 does check it. AttachmentType.java:250-273. |
| confirmed | Tracker: range = clientTrackingRange x 16, scaled by broadcast percentage, capped by view distance and measured horizontally from the player. sendChanges runs every updateInterval, on impulse or when data is dirty. Deltas are 1/4096 block (+-8 blocks). Teleport packets on out-of-range deltas, every 400 updates and on onGround flips. Motion only when deltas are tracked; head-rotation packet; hurtMarked always broadcasts. One flush per tick. | None. Builder default range is 5, but vanilla mobs such as zombies set 8. | ChunkMap.java:1110-1121, 1327-1345. ServerEntity.java:86-200. VecDeltaCodec.java:7-17. EntityType.java:1204-1215, 1256-1257, 1384-1385, 767-775. MinecraftServer.java:1034, 1083. |
| confirmed | Bandwidth: about 34 B per unit-update with vanilla defaults (~0.54 Mbit/s per client for 300 moving units); about 0.35 Mbit/s with velocity updates off. | The arithmetic holds under the stated assumptions. It is an upper-end estimate, because Rot and HeadRot packets are sent only when rotation changes. | Packet field sizes in ClientboundMoveEntityPacket, ClientboundSetEntityMotionPacket and ClientboundRotateHeadPacket. Recomputed: 300 x 34 B x 6.67 Hz = 68 kB/s; with 22 B, 44 kB/s. |
| confirmed | Entity#broadcastToPlayer gates tracking, but TrackedEntity#updatePlayer re-runs only on section change, player movement or spawn, so fog of war needs forced re-evaluation (access transformer on ChunkMap.entityMap and ChunkMap$TrackedEntity), as Reign of Nether does with a TrackedEntity mixin. | Confirmed for vanilla, and the access-transformer lines are right. Added: under Moonrise, vanilla updatePlayer (and so broadcastToPlayer) runs every tick for every player near the entity's chunk, so re-evaluation is automatic there and the lookup must be O(1). | ChunkMap.java:150 (private entityMap), 1024-1031, 1110-1121, 1151-1182, 1278 (package-private TrackedEntity), 1327-1345. Entity.java:3021. RoN mixin/fogofwar/TrackedEntityMixin.java (HEAD inject on updatePlayer, cancellable). Moonrise TrackedEntityMixin.java moonrise$tick. |
| corrected | Moonrise replaces the entity tracker, so mixins or access transformers into ChunkMap internals may not apply under it; the broadcastToPlayer override is the most portable part. | Partly resolved from source. Moonrise redirects the iterator loops in ChunkMap#move and ChunkMap#tick, but keeps entityMap, TrackedEntity and vanilla updatePlayer. An access transformer with an updatePlayer call, or a HEAD injection into updatePlayer, still applies; only code injected inside ChunkMap#tick's loop would not run. A runtime test is still advised. | Moonrise mc/1.21.1@2eae1b1 (= 0.1.0-beta.15): src/main/java/ca/spottedleaf/moonrise/mixin/entity_tracker/{ChunkMapMixin,TrackedEntityMixin,EntityMixin}.java (raw files). |
| corrected | Vanilla culls entity rendering at average AABB side x 64 x Entity Distance, about 67 blocks for a zombie-sized mob. | The formula also multiplies by clamp(renderDistance / 8, 1, 2.5). So the figure is about 67 blocks only at render distance <= 8, about 101 blocks at the default 12 chunks, and up to about 168 at >= 20 (Entity Distance 100%). Overriding it is still worthwhile so the distance does not depend on client settings. | Entity.java:1692-1700. client/renderer/LevelRenderer.java:856-858 (setViewScale). client/Options.java:1108-1112 (default render distance 12), 126-131 (entityDistanceScaling default 1.0). EntityType ZOMBIE sized(0.6F, 1.95F). |
| confirmed | Picking: normalise by Window#getScreenWidth/Height; unproject with matrices from RenderLevelStageEvent (model-view is rotation-only); view bobbing is baked into the projection; ClipContext with CollisionContext; ProjectileUtil.getEntityHitResult; entitiesForRendering. | Added: hurt shake and nausea are also baked into the projection. | client/MouseHandler.java:345-349, 52-110. blaze3d/platform/Window.java:425-445. GameRenderer.java:1250-1277. RenderLevelStageEvent.java:85-120. ClipContext.java:23, 27. ProjectileUtil.java:108. ClientLevel.java:275. |
| confirmed | Camera hooks: a Camera#setup TAIL mixin with the protected setPosition/setRotation; ViewportEvent.ComputeCameraAngles posted inside Camera#setup; CalculateDetachedCameraDistanceEvent; Camera#getMaxZoom raycasts and pulls the camera in. | None. | client/Camera.java:47-112, 113-134. neoforge/client/ClientHooks.java:375. |
| confirmed | Reign of Nether makes the spectator player the camera, injects an orthographic projection at the HEAD of GameRenderer#getProjectionMatrix, cancels Frustum#offsetToFullyIncludeCameraCube, and its TopdownGui extends AbstractContainerScreen. RTSbuilding (LGPL-3.0, NeoForge 1.21.1) clamps a camera entity to an action radius and ships Sodium/Embeddium/Flywheel mixins. Freecam (MIT) FreeCamera extends AbstractClientPlayer. | None. | RoN: mixin/OrthoViewMixin.java, FrustumMixin.java, CameraMixin.java (setup TAIL), guiscreen/TopdownGui.java:15, orthoview/OrthoviewClientEvents.java:140, 279, 403. RTSbuilding@8110237: gradle.properties neo_version=21.1.238; LICENSE is LGPL v3; server/camera/RtsCameraManager.java:119-149; mixin/{SodiumLevelSlice,EmbeddiumWorldSlice,FlywheelBlockEntityStorage}Mixin.java. Freecam@6868852 common/.../FreeCamera.java:36. client/renderer/culling/Frustum.java:33. |
| confirmed | Cursor: InputEvent.MouseButton.Pre fires before grabMouse(), so cancelling it keeps the cursor free and suppresses attack/use. MovementInputUpdateEvent and CalculatePlayerTurnEvent can neutralise player input. | None. | client/MouseHandler.java:87-92. neoforge/client/event/InputEvent.java:99. MovementInputUpdateEvent.java:24. CalculatePlayerTurnEvent.java:24-62. |
| confirmed | Render hooks: the RenderLevelStageEvent stage list and getters; AddSectionGeometryEvent; LevelRenderer#setSectionDirty; outlines via isCurrentlyGlowing/getTeamColor; requestOutlineEffect (21.1.51); RegisterGuiLayersEvent and a cancelable RenderGuiLayerEvent.Pre; minimap APIs. | None. | RenderLevelStageEvent.java:64-120, 158-207 (all 11 stages). LevelRenderer.java:2543, 2572. Minecraft.java:2624-2626. Entity.java:281-284, 2407. 21.1.264 changelog entry 21.1.51 (#1517). RegisterGuiLayersEvent.java:47-81. RenderGuiLayerEvent.java:70. gui/VanillaGuiLayers.java. BlockBehaviour.java:633. texture/DynamicTexture.java:22. |
| corrected | Sodium's 1.21.1/stable branch dispatches the chunk-layer RenderLevelStageEvents and runs AddSectionGeometryEvent renderers; entity rendering and the other stages remain vanilla code paths. Iris offers IrisApi#isShaderPackInUse and isRenderingShadowPass. | The dispatch claim is confirmed, and the other stages are still posted from vanilla LevelRenderer code. But Sodium does patch entity rendering: ModelPart/Cube fast paths, shadows, and culling of entities whose chunk sections are not visible (wrapping EntityRenderer#shouldRender; glowing or name-tagged entities are exempt). The Iris API is confirmed. | sodium 1.21.1/stable@272a4aa: neoforge/.../level/NeoForgeLevelRenderHooks.java; mixin/features/render/entity/{CubeMixin,ModelPartMixin,cull/EntityRendererMixin,shadows/EntityRenderDispatcherMixin}.java; SodiumWorldRenderer#isEntityVisible (lines 494-513). Iris 1.21.1@eb7afb9 common/src/api/.../IrisApi.java:16, 42, 63. |
| confirmed | Rendering reworks after 1.21.1: 1.21.2 entity render states, 1.21.5 render pipelines, 1.21.6 GUI render states, 1.21.9 submission-based rendering and debug overhaul, 1.21.11 RenderType shuffle and ResourceLocation -> Identifier, 26.1 camera render state, 26.2 Vulkan backend, 26.3 Renderpearl. | None. | neoforged/.github primers@2bb6f9f: 1.21.2/index.md:397; 1.21.5:1362; 1.21.6:18-32; 1.21.9:41-48, 400; 1.21.11:23-25, 361-367; 26.1:2432-2434; 26.2:22-24; 26.3:66-68. |
| corrected | In 1.21.1, datapack registries load only at world load, not on /reload, while reload listeners do reload. Vanilla 26.3 adds reloadable datapack registries only for a list of vanilla registries. | Registry entries are indeed not reloaded, but /reload does reload the tags of all registries, datapack registries included. In 26.3, vanilla's RegistryDataLoader#RELOADABLE_REGISTRIES list is vanilla-only, but NeoForge 26.3 adds NewDatapackRegistryEvent#reloadableRegistry(...) for modded reloadable registries. | MinecraftServer.java:1504-1545 (reloadResources: updateRegistryTags at 1538, structureTemplateManager reload at 1542). https://docs.neoforged.net/docs/1.21.1/concepts/registries. primers/26.3/index.md:1307-1309. NeoForge 26.3.x registries/NewDatapackRegistryEvent.java:91-126. |
| confirmed | Datapack and reload APIs: DataPackRegistryEvent.NewRegistry#dataPackRegistry overloads; AddReloadListenerEvent#addListener, getConditionContext, getRegistryAccess; SimpleJsonResourceReloadListener(Gson, dir); OnDatapackSyncEvent; namespace-prefixed registry folders. | Detail: the 4-argument overload takes a Consumer<RegistryBuilder<T>>. | registries/DataPackRegistryEvent.java:53-97. event/AddReloadListenerEvent.java:44-74. SimpleJsonResourceReloadListener.java:23. SimplePreparableReloadListener.java:27. OnDatapackSyncEvent.java:43-53. core/registries/Registries.java:251-257. CommonHooks.java:1340-1342. |
| corrected | NeoForge 26.3 renamed AddReloadListenerEvent to AddServerReloadListenersEvent and DataPackRegistryEvent.NewRegistry to NewDatapackRegistryEvent. | The reload-listener rename happened in NeoForge 21.4.84-beta (MC 1.21.4, PR #1915). Only the datapack-registry rename is new in 26.3; 26.2.0.93 still has DataPackRegistryEvent. | Class lists of the neoforge universal jars: 21.3.100 and 21.4.83-beta have AddReloadListenerEvent; 21.4.84-beta, 21.4.161, 21.5.101, 21.8.57, 21.11.48, 26.1.2.122 and 26.2.0.93 have AddServerReloadListenersEvent and DataPackRegistryEvent; 26.3.0.95 has NewDatapackRegistryEvent. 21.4.161 changelog line 149. |
| confirmed | SimpleJsonResourceReloadListener became codec-based (1.21.2 and 1.21.4). | None. | primers 1.21.2/index.md:2085-2097; 1.21.4/index.md:1080-1105. |
| confirmed | SavedData API: save(CompoundTag, Provider), a Factory with nullable DataFixTypes, computeIfAbsent. NeoForge copies the data and writes it on an IO worker (21.1.118) and cleans temp files asynchronously (21.1.146). Autosave every 6,000 ticks. | Precision: serialisation to NBT still runs on the main thread; only the copy is written off-thread. | world/level/saveddata/SavedData.java:36-60. storage/DimensionDataStorage.java:43. Changelog entries 21.1.118 and 21.1.146. MinecraftServer.java:183, 977-987. |
| confirmed | Data attachments work on entities, chunks, levels and block entities (serialize, copyOnDeath, sync); level attachments persist through LevelAttachmentsSavedData; setData marks the holder dirty, and in-place mutation needs setUnsaved(true). | None. | attachment/AttachmentType.java:95-273. Level.java:79 (extends AttachmentHolder). LevelAttachmentsSavedData.java:16-34. https://docs.neoforged.net/docs/1.21.1/datastorage/attachments. |
| confirmed | Persistence changes: 1.21.5 SavedDataType(id, ctor, codec, dataFix); 1.21.6 ValueInput/ValueOutput; 26.1 SavedDataStorage with Identifier paths and server-global saved data. | None. | primers 1.21.5/index.md:1276-1356; 1.21.6/index.md:1459-1495; 26.1/index.md:1835-1860, 1941. |
| confirmed | Structure templates: getOrCreate loads data/<ns>/structure/; generated templates go to generated/<ns>/structures; fillFromWorld, placeInWorld, filterBlocks, getSize(Rotation) and transform have the stated signatures; the palettes field is private. | Added: templates are reloaded by /reload. | templatesystem/StructureTemplateManager.java:54-83. StructureTemplate.java:66, 83, 182, 226-228, 496, 506. MinecraftServer.java:1542. |
| confirmed | Flags 2\|16 avoid neighbour cascades; ChunkHolder batches section updates; protection events (BreakEvent, EntityPlaceEvent, ExplosionEvent.Detonate, LivingDestroyBlockEvent, EntityMobGriefingEvent, PistonEvent.Pre, FluidPlaceBlockEvent) exist; fire removes blocks without an event. | Added: the doFireTick gamerule is the simplest guard against fire. | block/Block.java:77-81. Level.java:280-299. ChunkHolder.java:52, 133-142, 166-190. BlockEvent.java:69, 98, 205. ExplosionEvent.java:65-74. entity/living/LivingDestroyBlockEvent.java. entity/EntityMobGriefingEvent.java. PistonEvent.java:77. block/FireBlock.java:167-272 (removeBlock/setBlock, only the onCaughtFire callback). |
| confirmed | Reign of Nether building HP: 2 HP per block by default with optional fixed HP; a building is destroyed at <=50% of its blocks; mining building blocks has a chance of small explosions; the mod keeps a placedBlockPosSet. | Detail: explodeChance is 0.3, and explosions can start fires below 75% of blocks. | RoN building/Building.java:37-68. BuildingPlacement.java:147-163, 246-253, 496-517, 675-683, 785-805. |
| confirmed | Scoreboard teams have only 16 colours; membership is keyed by scoreboard name (UUID for non-players); friendly fire is checked only in Player#canHarmPlayer; Projectile#canHitEntity ignores teams; pushableBy reads the collision rules. | Note: AbstractArrow applies canHarmPlayer to player-owned arrows that hit players, and mob AI targeting uses Entity#isAlliedTo. | scores/PlayerTeam.java:120-201. Entity.java:2843-2845. player/Player.java:970-977. projectile/Projectile.java:257-264. AbstractArrow.java:230. EntitySelector.java:50-75. |
| confirmed | Commands and debug: RegisterCommandsEvent getters; RegisterClientCommandsEvent; FMLEnvironment.production (FML 4.0.45); DebugPackets.sendPathFindingPacket is empty; DebugRenderer#render draws only chunk borders and GameTest markers; renderFilledBox/renderFloatingText; /tick query\|sprint, /neoforge tps\|track, /jfr; spark /spark profiler and /spark tickmonitor. | None. /neoforge track durations are in seconds. | event/RegisterCommandsEvent.java:36-50. javap of FMLEnvironment from loader-4.0.45.jar (public static final boolean production). Changelog entry 21.1.253. protocol/game/DebugPackets.java:78-79. debug/DebugRenderer.java:101-107, 134-234. commands/TickCommand.java:25-54. server/command/TrackCommand.java:28-130 and timings/TimeTracker.java:107 (TimeUnit.SECONDS). TPSCommand.java:35. https://spark.lucko.me/docs/Command-Usage. |
| confirmed | Tests: @GameTestHolder, @PrefixGameTestTemplate, RegisterGameTestsEvent, an MDK gameTestServer run with neoforge.enabledGameTestNamespaces; ModDevGradle unitTest with testframework and EphemeralTestServerProvider; GameTest moved to registries in 1.21.5; 1.21.9 adds DebugSubscriptions. | None. | neoforge/gametest/{GameTestHolder,PrefixGameTestTemplate}.java. event/RegisterGameTestsEvent.java. mdk-1.21.1/build.gradle:58-72. ModDevGradle README:430-478. Maven net/neoforged/testframework has 21.1.262-264. primers 1.21.5/index.md:349-539; 1.21.9/index.md:41-48. |
| corrected | HPA* (Botea, Mueller, Schaeffer 2004): up to 10x faster than optimised A*, within 1% of optimal (link webdocs.cs.ualberta.ca/~mmueller/ps/hpastar.pdf). | The claim is confirmed verbatim from the abstract. The link is dead (301 to sites.ualberta.ca, then 404); use the archived copy http://web.archive.org/web/20250526142416/http://webdocs.cs.ualberta.ca/~mmueller/ps/hpastar.pdf. | Abstract text extracted from the Wayback PDF: 'Compared to a highly-optimized A*, HPA* is shown to be up to 10 times faster, while finding paths that are within 1% of optimal.' |
| confirmed | Factorio FFF #317 uses an abstract chunk search as the heuristic; Emerson, Game AI Pro ch. 23, describes Supreme Commander 2 flow-field tiles; RVO2 is C++ under Apache-2.0; 0 A.D. moves formations through a controller entity; AoE II formation types. | None. | https://factorio.com/blog/post/fff-317 (2019-10-18: 'use it to provide a smart version of the heuristic function'). gameaipro.com ch. 23 PDF (cost/integration/flow fields, Supreme Commander 2). GitHub license API snape/RVO2: Apache-2.0. wildfiregames.com forum topic 16739 (formation controller: invisible entity that units offset from). |
| confirmed | Unit recipe: Attributes.STEP_HEIGHT is read by LivingEntity#maxUpStep; EntityType builder defaults are range 5 and interval 3; setShouldReceiveVelocityUpdates(false) drops routine motion packets while hurtMarked still broadcasts; the default Brain is empty and never ticked; hurtTime and invulnerableTime are decremented only in LivingEntity#baseTick. | None. | LivingEntity.java:3732-3735, 293-295, 472-478. EntityType.java:1256-1263, 1384-1385, 1204-1215. ServerEntity.java (hurtMarked block at the end of sendChanges). getBrain().tick appears only in subclasses (Goat, Frog, Allay, ...). |
| refuted | Returning SOUNDS or NONE from getMovementEmission() means no GameEvent.STEP is dispatched. | Only NONE guarantees this. In 1.21.1, Entity#move passes flag2 (on-pos equals legacy on-pos), not emitsEvents(), as the broadcastGameEvent argument of its first vibrationAndSoundEffectsFromBlock call. SOUNDS therefore still emits GameEvent.STEP in the common case. | world/entity/Entity.java:706 ('vibrationAndSoundEffectsFromBlock(blockpos, blockstate, entity$movementemission.emitsSounds(), flag2, pos)') and the vibrationAndSoundEffectsFromBlock body; MovementEmission enum at 3863-3888. |
| confirmed | Invulnerability frames: while invulnerableTime > 10 a hit no stronger than the last is ignored, and the timer resets to the container's getPostAttackInvulnerabilityTicks() (20). The fix is setPostAttackInvulnerabilityTicks(0) or the minecraft:bypasses_cooldown tag. | Precision: inside the window a stronger hit deals only amount - lastHurt and does not reset the timer. LivingIncomingDamageEvent fires before the i-frame check. | LivingEntity.java:1142-1205 (event at 1153, i-frame branch at 1190). damagesource/DamageContainer.java:62, 130-137. tags/DamageTypeTags.java:12. |
| confirmed | Added finding: NeoForge per-tick event overhead per living unit. | Every ticking entity posts EntityTickEvent.Pre and Post; every LivingEntity#baseTick posts LivingBreatheEvent; every Mob posts MobDespawnEvent before the range gate. This is small per call but scales with unit count. | ServerLevel.java:776-778. LivingEntity.java:428. CommonHooks.java:1382-1393. EventHooks.java:380-391, 952-954. |
| confirmed | MineColonies is data-first: ICitizenData is authoritative, and ICitizenManager#spawnOrCreateCitizen(data, level) (re)creates the entity. | None. | minecolonies version/1.21@dae860e api/colony/managers/interfaces/ICitizenManager.java:31-46. |
| confirmed | Reign of Nether enables fog of war only on RTS-optimised small maps, gates chunk packets per column, and serves a pre-match snapshot for fogged chunks. | Detail: 'RTS-optimised' means a world border of at most 1,280 blocks (80x80 chunks). The snapshot is disk-backed at about 50-500 KB per chunk. | RoN worldborder/WorldBorderServerEvents.java:21-35. fogofwar/FogOfWarServerEvents.java:264-275. fogofwar/FogChunkSnapshot.java header comment. mixin/fogofwar/ChunkHolderMixin.java:17-20. ChunkMapInitialSendMixin.java:17. |
| confirmed | GeckoLib 4.9.x exists for NeoForge 1.21.1 (open question 2). | None; the latest is 4.9.3 (2026-09-16), licensed MIT. | Modrinth /project/geckolib/version?loaders=[neoforge]&game_versions=[1.21.1]. |
