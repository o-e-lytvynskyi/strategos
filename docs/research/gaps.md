# Research gaps

Two completeness critics (one per research pass) read the verified reports and raised the questions below. The follow-up research round was cut short twice by usage limits, so each group is carried into the design phase, an M0 spike, or the owner's decisions.

## G1. Unit authority, movement model and unit count

**Disposition.** **Design + M0 spikes.** The architects choose who owns unit position and state, behind a body seam that allows both a physics body and a kinematic puppet. M0 measures server cost per unit (spike S1); client frame time is measured with the owner's consent (spike S4).

### As raised by the first critic

**Question.** Where is per-unit state authoritative, and how do units move? Variant A: the Minecraft-free core owns units as records (position, HP, order queue, stats from the modifier pipeline) and moves them kinematically on its own nav grid. Each MC entity is a non-saved view whose position the core sets every tick (noPhysics, no gravity, vanilla travel/move skipped). Variant B: a stripped PathfinderMob owns position and physics (vanilla travel/move, step-up, fluids, collisions) and its own persistence; the core supplies rules and planners. For each variant, on the M4 Pro with NeoForge 21.1.264, measure: (1) server ms/tick (p50/p95) at 250, 500 and 1,000 units, idle, moving and fighting; (2) what A loses in fidelity (slabs, stairs, snow layers, water, doors, knockback, pistons, vanilla damage sources) and what floor-height data its nav grid must then store; (3) whether async path results delivered at a fixed tick N+k keep seeded core replays deterministic.

**Why it matters.** This is the keystone of the architecture, and the reports answer it inconsistently. Platforms §6.4(1) and Testing §4 assume the core can run whole seeded matches in plain JUnit (Simulation.newMatch, deterministicReplay), and Mechanics §1.8/§2.4 computes damage and the unit FSM in our own simulation. Engine §1.3–1.4 keeps vanilla travel/move physics on a stripped PathfinderMob. Libraries §3.9 keeps per-unit state in entity attachments, while Engine §3.2/§7 makes SavedData records authoritative with entities as views. Prior-art §3.4 keeps stats as vanilla Attributes. Prior art splits the same way: RoN and MineColonies feed vanilla Path/MoveControl (B), while 0 A.D.'s UnitMotion is fully simulation-side (A). The answer fixes: the unit entity class; the nav-grid encoding; persistence (SavedData or entity NBT/attachments, and how units avoid duplicating when chunks reload); the damage pipeline; how much can be tested without Minecraft; and the unit counts the MVP can promise. Every per-unit cost in the reports is extrapolated from other MC versions, loaders and CPUs (Engine §1.2, marked unverified).

**Suggested method.** A two-day spike on the MDK (NeoForge 21.1.264) with both variants of a minimal unit. A GameTest 'perf' batch spawns N units in an empty arena and replays scripted move and attack orders. Record MinecraftServer#getTickTimesNanos p50/p95 and /neoforge track entity, plus a JFR profile read with 'jfr view hot-methods'. Before coding, list from the decompiled sources what a kinematic unit skips and what it still pays. Entity#move's noPhysics branch only calls setPos, but Entity#baseTick still runs fluid pushing; also check LivingEntity#travel/aiStep, ServerEntity#sendChanges and the client's LivingEntity#lerpTo. Check A's stepping on a slab-and-stair arena with a scenario screenshot. Test determinism with a core prototype that joins path jobs at tick N+k.

### As raised by the second critic

**Question.** Which implementation should carry a Strategos unit, and at what unit count? Candidates: (a) a stripped PathfinderMob moved by vanilla travel/move with its own MoveControl (Engine §1.3–1.4); (b) a 'puppet' entity whose position, HP and orders belong to the deterministic core and are copied onto it each tick (noPhysics, no vanilla AI, optionally skipping super.tick()); (c) a plain LivingEntity. For each, measure on the M4 Pro at 200, 500 and 1,000 units: server µs per unit per tick (idle, moving, fighting), and client ms per frame with vanilla HumanoidModel plus armour and held-item layers, a fixed RTS camera, with and without Sodium 0.8.13. Also list which vanilla interactions each variant keeps: player melee, arrows, knockback, fluids, falling, invulnerability frames.

**Why it matters.** Every report assumes one Minecraft entity per unit, but nobody has measured one. Both '20–30 µs per vanilla-AI mob' and '~10 µs per stripped unit' are labelled unverified extrapolations from other CPUs, other MC versions and modded packs. Client rendering of hundreds of animated humanoids is untested (Engine open Q1–Q2).

The budgets already disagree. Testing §9 allows at most 5 ms p50 for all mod server work and 3 ms per frame for mod rendering at 4 × 200 units. Engine §1.2 plans about 20 ms per tick for units, which is 8–24 ms for 800.

The measurements decide two things:
- the MVP unit cap (RoN 150 per player, 0 A.D. 300 per player, Engine up to 2,000 in total);
- whether units can keep per-entity vanilla renderers. Flywheel switches off under Iris shader packs, and its indirect backend needs GL 4.6 while macOS stops at 4.1.

The same spike also settles an authority split that no report states. Platform §6.4.1, Mechanics §2.7 and Testing §4 promise a deterministic, Minecraft-free core with replay hashes. Engine §1.4 lets vanilla physics own position, and Libraries §3.9 keeps orders in entity attachments. Whichever wins fixes three things: tick ordering between the core and the entity loop, persistence (entity NBT or core records), and the replication path.

**Suggested method.** Run a 2–3-day spike in the MDK on NeoForge 21.1.264:
- Put the three unit classes behind one interface.
- Add a dev command that spawns N units in a flat, force-loaded arena and gives scripted move and fight orders.
- Server cost: /neoforge track entity, plus a JFR recording read with `jfr view hot-methods`.
- Client cost: frame times recorded in RenderFrameEvent at a fixed camera pose.
- Run once on the integrated server (client and server on one machine), once against a dedicated server, then both again with Sodium 0.8.13.

Before coding, read these in the decompiled tree to predict how a puppet ticks and replicates:
- Entity#move and baseTick;
- LivingEntity#travel and aiStep;
- Mob#serverAiStep (final);
- ServerEntity#sendChanges, which sends teleport packets on onGround flips and every 400 updates.

## G2. Commander camera, cursor, projection and server-side streaming

**Disposition.** **Design**, verified against the decompiled client code; the camera milestone starts with a spike.

### As raised by the first critic

**Question.** Which mechanism keeps the server's streaming position for an RTS commander in step with a smooth client camera on 1.21.1, on a dedicated server as well as in singleplayer? Three options: (A) the client flies its own spectator or mayFly player with the camera and asks the server to teleport it for jumps; (B) a client-only camera entity with the player parked, as in Camera Lib and Freecam; (C) a server camera entity via ServerPlayer#setCamera, with client-side smoothing in a Camera#setup mixin. For each, establish: rubber-banding, server cost, fog re-check cadence, streaming reach, and the effect on first-person coexistence.

**Why it matters.** No report picks one (Libraries open question 13), and the three code paths behave very differently. B: LocalPlayer#sendPosition sends no movement packets at all when Minecraft#getCameraEntity() is not the player (LocalPlayer.java:277/700). The server then never calls ChunkMap#move for that player, so streaming stays around the parked body. The '≤1 s fog re-check with no hook' that Libraries §3.8 and Mechanics §1.18 rely on also disappears. A: ServerGamePacketListenerImpl#handleMovePlayer (lines 916–921) snaps back any move of more than about 10 blocks per packet unless the player is the singleplayer host. Minimap and hotkey jumps therefore work in dev singleplayer but snap back on a dedicated server; RoN avoids this with a serverbound teleport request that the server answers with teleportTo. C: ServerPlayer#tick moves the player back onto the camera entity every tick. Switching to any non-spectator game mode resets the camera, and so does wantsToStopRiding(), which means Shift is held (ServerPlayer.java:524–534 and 1602); RTS UIs use Shift to queue orders. Under A and C, every panning tick costs one ChunkMap#move per commander, which is O(tracked entities). In all three, the streaming radius is clamp(client render distance, 2, server view-distance) (ChunkMap.java:840), and chunk sending is capped at 64 chunks per tick (PlayerChunkSender). A whole-arena overview therefore needs render distance 16–32, and a jump outside the loaded radius takes seconds to fill. The answer decides the camera code, the fog-refresh mechanism, the commander's game mode and the arena size.

**Suggested method.** Source reading is done for the lines above. Read RoN's camera and minimap jump flow for behavioural notes only (it is GPL, so clean-room). Then run a spike on a dedicated dev server with two clients and about 1,000 tracked dummy entities. Implement A and C minimally, script pans at 10–60 blocks/s and 300-block jumps, and record four things: 'moved too quickly' warnings; ticks until full chunk coverage after a jump; ChunkMap#move cost (JFR); and whether a fog flip on a stationary unit reaches the client at the expected cadence.

### As raised by the second critic

**Question.** Which camera, cursor and projection design should RTS mode use on NeoForge 21.1, with Sodium 0.8.13 and Iris 1.8.14-beta.1, on the Retina M4 Pro? Three choices:
- Camera: (A) move the commander's own player with the camera (Camera#setup mixin; spectator mode or invisible creative flight), or (B) use a camera entity, either client-only via Minecraft#setCameraEntity (as Camera Lib and Freecam do) or server-anchored (as RTS Building does).
- Cursor: a non-pausing Screen, or MouseHandler#releaseMouse plus a cancelled InputEvent.MouseButton.Pre.
- Projection: perspective, or orthographic (a GameRenderer#getProjectionMatrix mixin plus a bypass of Frustum#offsetToFullyIncludeCameraCube).

**Why it matters.** The reports disagree on every axis:
- Libraries §3.2 requires a non-pausing Screen that owns the cursor; Engine §5.1 frees the cursor with releaseMouse and the cancelled Pre event.
- Engine recommends approach A; Libraries §3.7 points to Freecam's camera entity as the Iris-safe pattern; Camera Lib and RTS Building both use camera entities.
- Ortho vs perspective is still open (Engine Q4, Q7).

The choice decides the Mixin set and the UI and input architecture. With a Screen open, Minecraft#tick skips handleKeybinds and KeyMappings stop updating (verified), so hotkeys and command-card hit-testing have to be built a different way.

It also reaches the server. Chunks and entities stream around the player entity. A camera entity makes LocalPlayer#isControlledCamera() return false, and sendPosition is gated on that (line 277, verified). Movement packets then stop, and with them the at-least-once-a-second ChunkMap#move visibility re-check that fog of war relies on.

A spectator body brings its own conflicts, all verified in source: the scroll wheel changes flying speed, attacking an entity makes the player spectate it (ServerPlayer#attack → setCamera), and the hotbar becomes the spectator menu.

How an ortho projection behaves under Sodium's culling and Iris's shadow pass is untested.

**Suggested method.** First read, in the decompiled tree:
- Camera#setup;
- LocalPlayer#sendPosition and aiStep;
- Minecraft#tick and handleKeybinds;
- MouseHandler#onPress and onScroll (NeoForge's Pre hooks run before grabMouse and before the spectator branch);
- ServerPlayer#attack and setCamera;
- Frustum.

Also read:
- the MIT sources of Camera Lib and Freecam, and RTS Building's mixin list (ideas only), to see what each approach has to patch;
- the section and occlusion culling in Sodium mc1.21.1-0.8.13, to find its perspective assumptions.

Then spend 2 days on a spike:
- Build A with each cursor variant, in both perspective and ortho; build B in perspective only.
- Check each with scenario-harness screenshots.
- Test picking accuracy at GUI scale 2 on Retina by clicking known blocks and units.
- Log movement packets and tracked-entity changes.
- Run each once with Sodium plus Iris and one common shader pack.

## G3. Owner's product constraints

**Disposition.** **Answered by the owner on 2026-10-20:** bounded match arena first, open world maybe later; solo against AI first, a 2-4 player server next; RTS camera plus first person; hybrid world (real trees and stone, abstract deposits and fields, block-by-block construction with abstract HP). Population cap, worker model and licence remain roadmap decision points.

### As raised by the first critic

**Question.** Which v1 product constraints does the owner set? (1) Match model: a dedicated, bounded, pregenerated arena per match (world border about 512–1,024 blocks, force-loaded, nav grid and fog prewarmed, as RoN does and the Engine and Mechanics reports assume), or RTS on top of an ordinary open survival world. (2) Multiplayer target: singleplayer against AI, LAN or integrated host, or internet dedicated servers, and the maximum players per match. (3) Whether first-person players share a match with RTS commanders. (4) Population per player: RoN 150, 0 A.D. 300, AoE about 200; Testing §9 budgets 4 × 200. (5) Economy and buildings: 0 A.D. citizen-soldiers or AoE villager-only workers, and abstract building HP or real block destruction. (6) Licence: permissive or all-rights-reserved, or GPL-3.0.

**Why it matters.** Every report leaves these open (Mechanics open questions 4–6, Engine open question 6), and each one flips a concrete design. (1) decides the chunk-loading strategy, nav and fog cache sizing, match versus world persistence, and whether re-streaming after camera jumps matters. (2) matters because singleplayer hosts are exempt from the movement checks (ServerGamePacketListenerImpl:918) and from bandwidth limits; dedicated servers need server-mediated camera moves, fog anti-cheat and the replication budget in Engine §4.3. (3) decides whether the commander can be a spectator, and whether units must work with vanilla combat. (4) sizes every performance budget and the renderer choice. (5) changes the unit FSM, the economy and the damage model. (6) decides whether reusing GPL code from RoN or MineColonies is an option at all (with the FSF caveat about nonfree hosts), and what bundling LGPL or MPL jars obliges.

**Suggested method.** This cannot be researched: ask the owner. Give them a one-page table of the options and their costs, taken from the six reports, and record the answers before the architecture is drafted.

## G4. Unattended client screenshots on the owner's Mac

**Disposition.** **Design + M0 spike S3, run only with the owner's consent:** the client window can take focus and swallow the owner's typing (recorded in the pet-projects knowledge base).

### As raised by the first critic

**Question.** Can the L4 scenario harness (Testing §7) run unattended on the owner's M4 Pro and produce repeatable screenshots without disturbing the owner? Check five things. (1) Focus. FML's DisplayWindow creates the window hidden, then calls glfwShowWindow (DisplayWindow.java:366/476), and neither it nor Minecraft's Window sets GLFW_FOCUSED or GLFW_FOCUS_ON_SHOW. Does each run steal focus, possibly switch Spaces, and receive the owner's keystrokes? (2) Does rendering keep its normal cadence when the window is unfocused, covered or on another Space, or when the screen is locked or asleep (App Nap, swap throttling)? Or does the 3-minute watchdog fire? (3) Is the framebuffer Retina 2×, and are PNG hashes identical across 10 runs? (4) Can Claude Code's sandboxed Bash open the window at all? (5) If local runs are disruptive, does the same harness run headless in a Linux arm64 VM (Lima, OrbStack or colima) under Xvfb with Mesa llvmpipe? Mojang's 1.21.1 JSON lists no LWJGL natives-linux-arm64, so the 3.3.3 natives would have to be added via additionalRuntimeClasspath.

**Why it matters.** Testing §7.3 assumes a visible window that 'takes focus for about a minute' while the owner keeps working. It calls hidden-window and Retina behaviour unproven and does not consider covered windows at all. The pet KB (.claude/kb/mac-menubar-pitfalls-debug.md) already records a demo window on this machine that grabbed focus and replaced the owner's typing. The owner's keystrokes would also open chat or the pause menu in the scenario world and spoil the captures. The answer decides between two testing strategies. Either visual checks run locally on every client change (the assistant inspects the PNGs, the owner approves goldens), or headless Linux goldens (local VM plus CI) become the source of truth and macOS screenshots are kept for milestones the owner attends.

**Suggested method.** A one-day spike: the MDK client plus a roughly 50-line scenario mod that opens a flat world, captures three frames and exits. Run it 10× from Claude Code while the owner types in another app, plus once with the window covered and once with the screen locked. Log the window's FOCUSED attribute, frame times, framebuffer size and PNG SHA-256s. If focus is stolen, try fml.toml earlyWindowControl=false together with a Window mixin that sets GLFW_FOCUSED and GLFW_FOCUS_ON_SHOW to false. Then run the same jar in a Lima Ubuntu arm64 VM with xvfb-run. Also confirm the harness can join a dedicated dev server via --quickPlayMultiplayer, since singleplayer hides dedicated-server behaviour such as the movement checks.

### As raised by the second critic

**Question.** Can the L4 client-scenario harness run unattended on the owner's M4 Pro, without disturbing them, and still capture complete, reproducible frames? Two routes:
- (a) Natively, with the window kept from taking focus, still rendering while unfocused, fully occluded, on another Space, minimised, or with the display asleep.
- (b) In a Linux container on the Mac with Xvfb and Mesa llvmpipe: either arm64, with the LWJGL 3.3.3 natives-linux-arm64 jars added to the run classpath, or amd64 under Rosetta. In that case, do the PNGs match the ubuntu-24.04 CI goldens within the planned 0.005 MSE threshold?

**Why it matters.** Testing §7.3 runs a real window on the Mac and accepts that it 'appears and takes focus for about a minute'. It calls hidden windows unproven. It did not look at rendering while the window is occluded or on another Space, and the Retina framebuffer size is still unverified.

FML 4.0.45 creates its early window hidden and then shows it with glfwShowWindow (DisplayWindow.java:476). GLFW's default GLFW_FOCUS_ON_SHOW then focuses the window, which on macOS also activates the app.

The owner's knowledge base already records a demo window that activated itself and caught keystrokes they were typing into another app (pet_projects/.claude/kb/mac-menubar-pitfalls-debug.md, '--demo ворует нажатия владельца'). A client that grabs focus several times a day is therefore unlikely to be acceptable.

If (a) fails and (b) is not possible, client features can only be checked on CI or by the owner. That breaks the 'every intermediate result is tested' bar for HUD and camera work. If (b) works, the macOS golden set can be dropped and local and CI checks share one set.

Checked here: Mojang's 1.21.1 version JSON lists no linux-arm64 natives, but LWJGL 3.3.3 publishes natives-linux-arm64 jars on Maven Central for every module 1.21.1 uses (all returned HTTP 200).

**Suggested method.** Write a ~50-line dev mod that waits for a flat world, saves framebuffer PNGs from RenderFrameEvent.Post and exits. The event fires before the blit (Minecraft.java lines 1203 and 1216).

1. Native runs under each condition above. Log frame times, check captured frames for completeness, and watch the frontmost app with `lsappinfo front`. Then repeat with GLFW_FOCUS_ON_SHOW and GLFW_FOCUSED set to false, and with a window that is never shown (earlyWindowControl=false plus hints set by a mixin on com.mojang.blaze3d.platform.Window). Before this, read GLFW's Cocoa focus code and FML's DisplayWindow and ImmediateWindowHandler.
2. Container runs in OrbStack or Docker Desktop: arm64 with the LWJGL natives on additionalRuntimeClasspath, and amd64 under Rosetta. Record startup time and time per scenario.
3. Render the same scenario on a GitHub ubuntu-24.04 runner and diff the images.

## G5. Client frame time with hundreds of units

**Disposition.** **M0 spike S4**, with the owner's consent (see the first group).

### As raised by the first critic

**Question.** On the M4 Pro (Apple's OpenGL 4.1 driver), what client frame time (p50/p99) does NeoForge 21.1.264 reach when an elevated RTS camera shows 200, 500 and 1,000 unit entities? Test two models: (a) vanilla HumanoidModel with armour and held-item layers; (b) a minimal single-part model. Test each at render distance 16 and 32, with and without Sodium 0.8.13. How much of the frame goes to entity rendering and how much to chunk rendering?

**Why it matters.** Libraries open question 1 and Engine open question 2 leave this unmeasured. Engine §1.2 warns that client rendering may hit its limit first, and Testing §9 budgets only ≤3 ms per frame of mod rendering at 4 × 200 units. The answer decides four things. First, whether one vanilla EntityRenderer per unit is viable or a batched renderer is needed: Flywheel's instancing backend runs on GL 3.3 but switches off under Iris shader packs, and its indirect backend needs GL 4.6, which macOS lacks. Second, whether distant units need LOD or impostors. Third, the realistic population cap. Fourth, how much render distance, and therefore arena size, the camera can afford (gap 2).

**Suggested method.** A client spike, either in runClient (started by the owner) or in the scenario harness. A dev command spawns N units, frozen with /tick freeze, around a fixed elevated camera. Record frame times with client JFR (-XX:StartFlightRecording, read with 'jfr view hot-methods') and the F3 frame graph, toggling Sodium and ImmediatelyFast via -Pcompat. Repeat with name tags off and with shouldRenderAtSqrDistance and getBoundingBoxForCulling overridden. Also check whether units under canopies and roofs can be shown without renderer-specific chunk-mesh mixins: Sodium bypasses RoN's RenderChunkRegion mixin, and RTS Building needs separate @Pseudo mixins on Sodium's LevelSlice.

## G6. Where unit, building, tech and civ definitions live

**Disposition.** **Design.**

### As raised by the first critic

**Question.** On 1.21.1, should the 0 A.D.-style templates (units, buildings, techs and civs, with parent/mixin inheritance and modifiers) live in datapack registries or in a reload listener? Datapack registries (DataPackRegistryEvent.NewRegistry) give Holder references, tags and a network codec, but they load once at world load, sync only in the configuration phase, ignore /reload, and cannot resolve parents before decoding. A reload listener (AddReloadListenerEvent with our own inheritance and an OnDatapackSyncEvent payload) hot-reloads but has no Holders or tags. And can the template schema live in the Minecraft-free core (DFU Codecs as compileOnly), so that templates are testable in plain JUnit?

**Why it matters.** The reports disagree. Libraries §3.3/§3.9 and Platforms §6.4(8) put unit, building, tech and civ definitions in datapack registries. Engine §6 and Mechanics §2.11 put templates in a reload listener and keep registries for stable IDs only, because 1.21.1 registries neither reload nor support inheritance. The choice decides five things: the first milestone's registration and sync code; how saves and entities reference template IDs; whether balance can be tuned with /reload in a running dev world or GameTest server or needs a restart every time (the AI-assisted tuning loop); where the core/mod content boundary sits; and how hard the 26.3 port is (NeoForge 26.3 adds reloadable mod registries).

**Suggested method.** Confirm three points in the decompiled sources. Registries sync during configuration (ServerConfigurationPacketListenerImpl / SynchronizeRegistriesTask). /reload calls ReloadableServerResources.loadResources, which runs ReloadableServerRegistries and so reloads loot data only. OnDatapackSyncEvent fires at PlayerList.java:208 (at login, before the player is added to the level) and at :920 (on reload). Then prototype about 20 templates both ways in the gametest source set and measure /reload time. List every planned feature that would need Holder or tag references to templates, such as data maps, tag-based class filters and datagen providers. If none does, the reload-listener path with string IDs is simpler.

### As raised by the second critic

**Question.** Where should unit, building, tech and civilisation definitions live on NeoForge 21.1? Options:
- (a) datapack registries (DataPackRegistryEvent.NewRegistry with a network codec), with 0 A.D.-style parent/mixin inheritance flattened at datagen time;
- (b) a reload listener with runtime inheritance and its own sync payload;
- (c) registries for stable ids, plus reload-listener balance data.

Judge them by:
- inheritance support;
- /reload hot-editing during balance work and tests;
- whether vanilla systems (commands, tags, loot, advancements) need Holder or tag references;
- client availability before entity pairing;
- per-world datapack overrides;
- error reporting;
- the port to 26.3.

**Why it matters.** The reports contradict each other. Platform §6.4.8 and Libraries §3.3 put all four in datapack registries. Engine §6 and Mechanics §2.1/§2.11 put templates in a reload listener with 0 A.D.-style inheritance and keep registries for stable ids.

Verified facts that bound the choice:
- 1.21.1's RegistryDataLoader decodes each element file on its own, and NeoForge's ConditionalOps is the only hook (lines 207, 229). A registry cannot resolve parents at runtime without a mixin.
- Registry entries are not reloaded by /reload. Reloadable datapack registries only arrive in 26.3.
- A payload sent from OnDatapackSyncEvent reaches a joining client before any entity pairing: PlayerList#placeNewPlayer posts the event at line 208 and calls addNewPlayer at line 234. On /reload (line 920), though, it arrives while entities already exist.

One point no report addresses: an EntityType, its tracking range, its update interval and its renderer are all fixed at startup. Data-defined units must therefore map onto a few generic unit EntityTypes with the template id synced. A datapack can only add a unit whose models and textures the client already has.

The choice fixes:
- the datagen providers;
- how entities and saves reference definitions (Holder or id);
- the client sync path;
- the balance-iteration loop.

**Suggested method.** Read the NeoForge 21.1 patches to DataPackRegistriesHooks and RegistryDataLoader, and OnDatapackSyncEvent.

List which planned features need registry Holders or tags.

In a short spike, build both variants for about 20 templates with two levels of inheritance. Measure on the M4 Pro:
- /reload cycle time against world-restart cycle time;
- how clear decode errors are (do they name the file and field?);
- datagen effort.

Read NeoForge 26.3's NewDatapackRegistryEvent#reloadableRegistry, saved at /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/gap-6/dl-nf/263-NewDatapackRegistryEvent.java, to see which variant ports with the least change.

## G7. What a match is in world terms; match lifecycle

**Disposition.** **Design + M0 spike S2** (cost of keeping a force-loaded arena simulating with no players online).

### As raised by the second critic

**Question.** What is a match in world terms on 1.21.1? Options:
- one match per world save, bounded by the vanilla world border (as RoN does);
- a bounded region of a level, with several matches (and several concurrently running GameTests) in one level;
- a dedicated dimension.

Also: how is a played map reset and how are maps distributed? And for the region option: what does it cost on the M4 Pro to keep a whole arena simulating with level-31 forced tickets (TicketController with ticking=false) at 576, 1,024 and 4,096 chunks — MSPT with no players online, heap, autosave time — compared with Engine §3.2's data-first virtualisation?

**Why it matters.** No report defines the match lifecycle: create, play, save and resume, end, reset, ship maps. Yet it shapes persistence, chunk loading and testing.

The reports conflict on two points:
- Keeping the arena alive: Mechanics §1.25 force-loads all of it; Engine §3.2 keeps authoritative records, treats entities as views and caps follow-tickets.
- Where per-chunk state may live: Libraries §3.9 uses chunk attachments for territory and exploration; Engine §7 says chunk attachments vanish on unload.

Constraints verified in source:
- Every non-overworld level's border follows the overworld border (MinecraftServer#createLevels adds a DelegateBorderChangeListener, line 423), and only the overworld border is saved (line 560). The vanilla border therefore cannot bound several maps, or a per-match dimension.
- GameTestServer runs up to 50 tests per batch at the same time in one level, in rows of 8 with gaps of 5 and 6 blocks (GameTestBatchFactory, StructureGridSpawner).
- EphemeralTestServerProvider offers only a void overworld, and its own Javadoc says not to touch it.

So match state held per level, RoN-style (RoN's bugs came from static global state), would block parallel in-world tests. Map-scale tests, such as the 200-unit cross-map move in Mechanics, need their own batch or a separate harness.

**Suggested method.** Read in the decompiled tree:
- GameTestServer, GameTestRunner, StructureGridSpawner and StructureUtils (test chunks are force-loaded with setChunkForced);
- MinecraftServer#createLevels and ForcedChunkManager.

Check whether NeoForge 21.1, or a maintained library such as Infiniverse, can create dimensions at runtime. Note how RoN ships maps (as world saves) and how MineColonies keeps several colonies in one world.

Measure force-load cost with a time-boxed, self-terminating run of the NeoForge 21.1.264 dedicated server already installed at /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/gap-1/server:
- Vanilla /forceload, in 256-chunk batches, places the same level-31 ticket without forceTicks as TicketController with ticking=false.
- Pregenerate areas of 384², 512² and 1,024² blocks.
- With no players online, record /tick query, heap and autosave time.

## G8. Navigation representation and deterministic async path delivery

**Disposition.** **Design**; measured in the pathfinding milestone's first spike.

### As raised by the second critic

**Question.** Which navigation representation should the pathfinder use?
- a single-layer 2.5D column grid (Mechanics §2.8, FrostedHeart);
- a layered 2.5D grid (several standable surfaces per column);
- a 3D cell band (RoN's ±24 blocks, Engine §2.2).

Which search stack: A*/HPA* or JPS?

Can off-thread searches be delivered on a fixed tick schedule — requested at tick t, applied at t+k, waiting if unfinished — so that the same seed and commands always produce the same paths?

What do these choices cost in memory, rebuild time and latency on maps of 512² and 1,024² blocks?

**Why it matters.** Pathfinding is in T0 and was RoN's main performance problem (PR #445). The reports still disagree on its foundation:
- Mechanics recommends a 2.5D walkability grid with off-thread JPS/A*.
- Engine argues that JPS breaks under step-up, drop, water and door costs, and proposes Y-band cell classes.
- RoN runs 3D A* in a ±24 band.
- FrostedHeart's 2.5D flow fields cannot represent caves or overhangs.

Strategos content needs units on walls and towers (0 A.D.'s turrets), bridges, multi-storey buildings, and mobility classes with footprints. The representation therefore fixes memory use, HPA* cluster shape and flow-field design. Engine's open Q9 leaves the cost unmeasured.

A separate problem is determinism. Platform §6.4.1, Mechanics §2.7 and Testing §4 promise a deterministic core: seeded invariant runs, replay hashes, reproducible GameTests. The recommended best-effort worker pool breaks that promise: it applies results when they are ready, at most 256 per tick, and its 'busy' answer keeps the old path. 0 A.D., by contrast, delivers async results at a fixed turn boundary (Mechanics §2.6).

**Suggested method.** 1. Terrain census. On vanilla-generated 512² samples from several biomes, and on two hand-built arenas with walls and bridges, count the columns with more than one standable surface and the Y range in use. An offline region-file scan or a small server command will do.
2. JMH prototypes of each candidate encoding in core. Measure bytes per cell, main-thread classification time per chunk from LevelChunkSection palettes, snapshot copy cost, and A* against HPA* query time and expanded nodes for 100-unit group orders.
3. Read the async delivery in 0 A.D. v0.28.0 (CCmpPathfinder, LongPathfinder) and in RoN's PathfinderWorkerPool. Both are GPL, so take behavioural notes only. Then measure the order-to-move latency of fixed-delay delivery (k = 1–4 ticks) under 200 simultaneous requests.

