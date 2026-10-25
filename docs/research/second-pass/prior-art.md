# Prior art for Strategos: RTS, army-command and colony mods, Minecraft Legends, pathfinding

Research date: 2026-10-20. Target: Minecraft Java 1.21.1, Java 21, loader not yet chosen.

**Method.** I cloned and read the source of:
- Reign of Nether (`1.20.1-dev`, 968 Java files, ~141k lines);
- MineColonies (`version/1.21`, 2,062 files, ~307k lines) and Structurize (`version/1.21`);
- Villager Recruits (`main`, 466 files, ~72k lines);
- RTS Building (`main`, ~155k lines in `src/main`);
- i113w's Camera Lib (`main`, 25 files, ~1.6k lines).

I read Ancient Warfare 2, MineFortress, FrostedHeart and Baritone through the GitHub API. Versions, dates and licenses come from the Modrinth API, CurseForge (via the cfwidget API), and the GitHub and GitLab APIs. Vanilla and NeoForge API claims are checked against the decompiled MC 1.21.1 + NeoForge 21.1 sources in the scratchpad. Anything not marked **[inference]** was seen in a source. Download counts are as of 2026-10-20. Changes made by the fact-check are marked **[corrected]** inline. The "Verification notes" section at the end summarises them.

---

## 1. Executive summary

- **Reign of Nether (RoN) is the only mature, actively developed, full RTS mod.**
  - It ships about weekly: 1.5.0b came out on 2026-10-14.
  - It runs only on **Forge 1.20.1** and is **GPL-3.0-only**, with a CLA.
  - On 2026-09-11 the maintainer wrote that there are no plans for 1.21.1 ([#485](https://github.com/SoLegendary/reignofnether/issues/485)).
  - Use it as a design reference only, not as a code base or a dependency.
- **RoN's two hardest problems were pathfinding and fog of war. Contributors rewrote both in mid-2026.**
  - Pathfinding became an off-thread grid A* over cached per-chunk walkability (July 2026).
  - Fog became server-authoritative and block-granular (1.4.0, August 2026).
  - Both are tied to bounded maps (world border ≤ 1,280 blocks). `rtsPathfinding` turns itself on automatically only on such maps, though its gamerule can force it on anywhere, and fog is only offered on such maps. **[corrected]** Bounded match maps are what made RoN's performance manageable **[inference]**.
- **No mod has an economic RTS AI opponent.**
  - RoN has none. Its "bots" are placeholder players used by the tutorial.
  - MineColonies, Ancient Warfare 2 (AW2) and Millénaire have scripted raids and factions.
  - Minecraft Legends has scripted piglin bases, which reviewers found repetitive.
  - An AoE-style AI is open ground, and it is a large risk item.
- **MineColonies is the best reference for long-lived worker AI and progressive construction.**
  - Useful parts: state machines with per-transition tick rates, citizen data kept separate from the entity, async path jobs, phased blueprint placement, research defined in JSON.
  - It already runs on NeoForge 1.21.1.
  - All of it (MineColonies, Structurize, BlockUI) is GPL-3.0.
- **Army-command UX:** Villager Recruits has the richest first-person command system: formations, groups, factions and diplomacy on vanilla teams, map routes. It is **All Rights Reserved (ARR)** and 1.20.1-only.
- **The top-down camera is a small problem, and every piece has working open-source precedent on 1.21.1. No mature library exists yet.** **[corrected]** The pieces:
  - orthographic projection via a mixin on `GameRenderer#getProjectionMatrix(double)`;
  - a way to free the cursor: RoN opens a dummy container screen; Camera Lib calls `MouseHandler#releaseMouse()` every client tick and hides the OS cursor **[corrected]**;
  - mouse picking with a ray through the inverse of projection × model-view;
  - a required bypass of the vanilla frustum-offset loop, which otherwise freezes the game.

  i113w's Camera Lib does all of this under **MIT** for NeoForge 1.21.1, handling the cursor itself rather than through a screen. It is alpha quality: the newest 1.21.1 build is 0.0.3-alpha3 (2026-06-09). **[corrected]**
- **Pathfinding:**
  - Vanilla `PathNavigation` runs synchronously on the server thread, with a node budget of `floor(FOLLOW_RANGE × 16)`, fixed when the navigation is created, times a search-depth multiplier. **[corrected: precision]**
  - Baritone (LGPL-3.0, has 1.21.1 builds) is a pathing bot for one player-sized agent. Its server-side fork, Automatone, stops at MC 1.20.
  - RoN's design is the best precedent. FrostedHeart has the only flow-field code I found. A comment in RoN's `GridNeighbors` mentions flow fields, but `1.20.1-dev` contains no flow-field implementation. None of the mods I read uses a hierarchical (portal / HPA*) layer.
- **Minecraft Legends (2023) shows what goes wrong when you command units in third person in a voxel world.**
  - Problems: orders only reach units in a small circle around the avatar, groups can't be split (orders go to a whole mob type), units fall off ramps, previews are unclear, AI bases are samey.
  - Mojang's own fixes (August 2023) were about predictability.
  - Content development ended in January 2024; the last content was the "Snow vs Snouts" Lost Legend that month.
- **Licensing:** nearly all serious prior art is GPL or ARR.
  - Code from MIT projects (Camera Lib, Better Mine Team, MineFortress, Guard Villagers' code, Easy NPC) can be copied with attribution without fixing Strategos's license.
  - LGPL and MPL projects can be used as separate jars. RTS Building is the exception. It is LGPL-3.0, but it also ships custom "User Terms 1.0" that ask any project using its code to be open source, so treat it as ideas-only. **[corrected]**
  - Everything else needs a clean-room process. This matters twice over here: an AI assistant that has GPL code in its context can reproduce it.
- **Testing:**
  - The big mods have almost no automated tests. RoN has one test class; MineColonies `version/1.21` has no `src/test` at all.
  - RTS Building is the exception: on NeoForge 1.21.1 with ModDevGradle 2.0.140, it has about 306 JUnit/Mockito test files plus GameTest server runs.

---

## 2. Landscape

**Relevance key:** H = high, M = medium, L = low.

| Project | MC / loader (newest) | Latest release | License | Source | Relevance |
|---|---|---|---|---|---|
| [Reign of Nether](https://modrinth.com/mod/reign-of-nether-rts) | 1.20.1 Forge (1.19.x up to mod 1.1.3) | 1.5.0b, 2026-10-14 | GPL-3.0-only, CLA | [GitHub](https://github.com/SoLegendary/reignofnether) | **H** — full RTS |
| [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) | **1.21.1 NeoForge**; 1.20.1 Forge | 1.1.1403-1.21.1, 2026-10-04 | GPL-3.0, CLA | [GitHub](https://github.com/ldtteam/minecolonies) | **H** — worker AI, construction |
| [Structurize](https://github.com/ldtteam/Structurize) / [BlockUI](https://github.com/ldtteam/BlockUI) | 1.21.1 NeoForge | Structurize 1.0.835-1.21.1-snapshot, 2026-09-30. This is a snapshot; the last stable release is 1.0.832-1.21.1, 2026-08-09 **[corrected]** | GPL-3.0 | GitHub | M — blueprints, UI |
| [Villager Recruits](https://modrinth.com/mod/villager-recruits) | 1.20.1 Forge | 1.15.2, 2026-06-29 (commits 2026-10-19) | ARR (README statement; the repo has no LICENSE file) | [GitHub](https://github.com/talhanation/recruits) (readable) | **H** — command UX |
| [Villager Workers](https://modrinth.com/mod/villager-workers) | 1.20.1 Forge | 2.0.3, 2026-07-01 | ARR | GitHub | L–M |
| [Siege Weapons](https://modrinth.com/mod/siegeweapons) | 1.20.1 Forge | 0.2.5 (beta), 2026-04-10 | ARR | GitHub | L–M |
| [Small Ships](https://modrinth.com/mod/small-ships) | 1.21.1 NeoForge/Fabric; 1.20.1. An old 2.0.0-b2.1 beta also exists for 1.21.4 (2025-05-16) | 2.0.2, 2026-10-16 | ARR | GitHub | L |
| [Millénaire](https://www.curseforge.com/minecraft/mc-mods/millenaire) | **1.21.1 NeoForge** (9.x); 1.12.2 Forge (8.x) | 9.0.2, 2026-09-18 | Custom; Java source not public | [GitLab, content only](https://gitlab.com/Millenaire/Public) | M — data-driven villages |
| [Ancient Warfare 2](https://github.com/P3pp3rF1y/AncientWarfare2) | 1.12.2 Forge | 2.7.0.1038, 2021-04-29 (dead) | GPL-3.0-or-later | GitHub | M — orders, siege |
| [Guard Villagers](https://modrinth.com/mod/guard-villagers) | 1.21.1 NeoForge (2.4.12, 2026-08-30) up to 26.3 | 2026-10-05 | MIT code / ARR assets | GitHub | L |
| [Easy NPC](https://modrinth.com/mod/easy-npc) | 1.21.1 NeoForge/Forge/Fabric (also Quilt) | 7.16.0, 2026-10-17 | MIT | GitHub | L |
| [Human Companions](https://github.com/justinwon777/HumanCompanions) | 1.20.1 Forge | 1.7.6, 2024-12-15 | GPL-3.0 | GitHub | L |
| [Custom NPCs](https://www.curseforge.com/minecraft/mc-mods/custom-npcs) | ≤1.16.5 on CurseForge | 2022-05-15 | Closed (Modrinth listing CC-BY-NC-4.0) | — | L |
| [MCA Reborn](https://modrinth.com/mod/minecraft-comes-alive-reborn) | 1.21.1 NeoForge/Fabric | 7.7.36, 2026-08-16 | GPL-3.0-only | GitHub | L |
| [Taterzens](https://modrinth.com/mod/taterzens) | Fabric up to 1.21.6, no 1.21.1 build | 1.16.2, 2025-06-26 | MIT | GitHub | L |
| [MineFortress](https://github.com/remmintan/minefortress) | 1.20.1/1.20.2 Fabric (repo `master` targets 1.20.2 with Yarn) | last commit 2025-08-09 | **MIT** | GitHub | M — MIT RTS colony |
| [Hundred Years Warfare](https://modrinth.com/mod/hundred-years-warfare) | 1.20.1, 1.21.1 (Fabric/Forge/NeoForge) | 2026-08-20 | ARR, no source | — | M — "RTS mode" UX |
| [Better Mine Team](https://modrinth.com/mod/bettermineteam) + [i113w Camera Lib](https://github.com/i113w/113CameraLib) | **1.21.1 NeoForge**, 1.20.1 Forge, 26.1.2 | BMT 0.3.0-Hotfix (1.21.1), 2026-09-05. Camera Lib's newest 1.21.1 build is 0.0.3-alpha3, 2026-06-09; its 2026-09-05 upload is the 1.20.1 build **[corrected]** | **MIT** | GitHub | **H** — camera reference |
| [RTS Building](https://modrinth.com/mod/rts-building-build-from-above) | **1.21.1 NeoForge** (plus one Fabric 1.21.1 build, 1.20.x Forge, 26.1.2) | 1.1.8-beta1, 2026-10-06 (repo 2026-10-19) | LGPL-3.0-only code, ARR assets, plus custom "User Terms 1.0" that ask projects using its code to be open source **[corrected]** | [GitHub](https://github.com/Hcrab/RTSbuilding) | M — camera, tests |
| [Siege of Settlements](https://modrinth.com/mod/siege-of-settlements), [Boobcat Edition](https://modrinth.com/mod/reign-of-nether-boobcat-edition) | 1.20.1 Forge | 2026-09-17 / 2025-10-06 | RoN derivatives with questionable labels | partial | L |
| [Ages of Dominion](https://www.curseforge.com/minecraft/mc-mods/ages-of-dominion) | 26.1.2 NeoForge (ModJam 2026) | 4.2.0, 2026-09-09 | ARR, per its CurseForge description **[corrected]** | — | L |
| [Baritone](https://github.com/cabaletta/baritone) / [Automatone](https://github.com/Ladysnake/Automatone) | Baritone: 1.21.1 builds v1.11.3; Automatone: Fabric up to 1.20 | Automatone last push 2024-01-21 | LGPL-3.0 | GitHub | M — algorithms |
| [FrostedHeart](https://github.com/TeamMoegMC/FrostedHeart) | 1.20.1 Forge | 2026 | GPL-3.0 | GitHub | M — flow fields |
| [SmartBrainLib](https://modrinth.com/mod/smartbrainlib) | 1.21.1 NeoForge/Forge/Fabric (1.16.11, 2025-10-01) **[corrected]** | 2026-10-05 | MPL-2.0 | GitHub | M — AI library option |
| Minecraft Legends | Mojang/Blackbird, released 2023-04-18 | content ended Jan 2024 | proprietary | — | **H** — UX lessons |

---

## 3. Reign of Nether (SoLegendary)

### 3.1 Status and maintenance

- **Builds:** Forge 1.19.1/1.19.2 for mod ≤ 1.1.3, Forge 1.20.1 from 1.1.4. A 1.20.1 beta of 1.1.3a also exists. Toolchain: ForgeGradle 5.1, Forge `1.20.1-47.4.0`, Parchment `2023.09.03-1.20.1`, Mixin, and the Essential partner SDK for peer-to-peer hosting.
- **Cadence:** 11 Modrinth uploads between 2026-09-26 (1.4.4e) and 2026-10-14 (1.5.0b): 6 releases and 5 betas. **[corrected]** The README roadmap (updated 2026-09-23) lists hero items, villager-faction reworks, more heroes and units, and scenarios.
- **Reach:** Modrinth about 421k downloads and 845 followers; CurseForge about 977k downloads; GitHub 338 stars and 112 forks.
- **Contributors:** major work now comes from contributors. S0ly did the pathfinding and fog rewrites; dmzz-yyhyy did performance work.
- **Port requests:** [#243](https://github.com/SoLegendary/reignofnether/issues/243) (NeoForge/1.20.4+) has been open since 2025-04. [#485](https://github.com/SoLegendary/reignofnether/issues/485) (1.21.1) got the answer "no current plans". [PR #299](https://github.com/SoLegendary/reignofnether/pull/299) ("Fabric base for 1.21.8") was only an empty skeleton.
- **Forks:** Siege of Settlements describes itself as "a fan-made expansion for the Reign of Nether mod" yet is labelled LGPL, although it derives from GPL code. The Boobcat Edition has a GPL listing but no source link. Don't take code from forks.

### 3.2 Camera and cursor

All files are under `src/main/java/com/solegendary/reignofnether/`.

- **Toggle and body:** F12 toggles the RTS camera. The server switches the player to spectator ([PlayerServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java)). The body flies at about average terrain height + 30, up to + 100. Chunk loading and entity tracking therefore follow the camera "for free". The Modrinth page recommends these server.properties: `view-distance=25`, `spawn-monsters=false`, `spawn-npcs=false`, `spawn-protection=0`, `gamemode=spectator`. It gives no reason, so linking them to the flying body is **[inference]**. **[corrected]**
- **Projection:** [OrthoViewMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/OrthoViewMixin.java) replaces `GameRenderer#getProjectionMatrix` with an orthographic matrix. [CameraMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/CameraMixin.java) forces a detached camera pulled back 20 blocks, so terrain still renders when the body clips into a hill.
- **Camera limits** ([OrthoviewClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/orthoview/OrthoviewClientEvents.java)): zoom 10–90 (zoom × 2 = visible height in blocks), pitch −20° to −90°, default yaw 135° and pitch −45°. Also edge-pan, camera hotkeys and scripted pans.
- **Frustum freeze:** [FrustumMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/FrustumMixin.java) cancels `Frustum#offsetToFullyIncludeCameraCube`. Its comment says the game "freezes… forever" otherwise. In 1.21.1 that method loops until the camera cube is inside the frustum (verified in `Frustum.java`), and with an orthographic projection it can fail to converge. i113w's Camera Lib needed the same bypass (§7); that it found it independently is **[inference]**.
- **Leaf hiding:** [RenderChunkRegionMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/RenderChunkRegionMixin.java) renders leaves as green stained glass (nether-wart blocks as red glass) and hides snow lying on leaves. It does this either around units and the cursor or everywhere. Without it, canopies hide units.
- **Free cursor:** RoN opens a dummy container screen ([TopdownGui](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/guiscreen/TopdownGui.java)) and handles all clicks and drags in `ScreenEvent` handlers. [CursorClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/cursor/CursorClientEvents.java) turns the screen position into a world ray, refines the block hit, and picks entities with its own ray–AABB tests.

### 3.3 Selection, control groups, commands

- **Selection:**
  - box select with a 3D rectangle test;
  - double-click (within 500 ms) or Ctrl-click selects all units of the same type ([UnitClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/UnitClientEvents.java));
  - 10 control groups, kept client-side as entity IDs plus building positions ([HudClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/hud/HudClientEvents.java)).
- **Command model:**
  - Commands are one enum, [UnitAction](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/UnitAction.java), with **138 values**. It mixes generic orders (MOVE, ATTACK_MOVE, HOLD, GARRISON) with per-ability entries (e.g. `CAST_SUMMON_VEXES_AUTOCAST_ENABLE`).
  - [UnitActionServerboundPacket](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/packets/UnitActionServerboundPacket.java) carries `ownerName`, the action, unit IDs, target positions and a shift-queue flag.
- **Validation:** the server accepts the packet if the sender's **name** equals `ownerName`, if `ownerName` belongs to a sandbox player, or if the sender has allied control. **[corrected: minor]** It then checks each unit's owner in `UnitActionItem`.
- **Queueing:** actions go into a fast queue, or into a slow queue when shift-queued.
- **Client prediction:** the client also runs the action locally for responsiveness.
- **Formations:**
  - [UnitFormations](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/UnitFormations.java) spirals out square slots and pairs units to slots by sorted x/z.
  - `FormationDragMove` builds formations from a drag line.
  - With vanilla pathfinding, the server spreads moves for groups of more than 20 units over ticks, 5 units per tick. Smaller groups dispatch at once, and so does everything when `rtsPathfinding` is on. **[corrected]**

### 3.4 Units and AI

- **Inheritance:** 54 unit classes, each extending a vanilla mob (6 × Vindicator, PiglinBrute, Skeleton, Wolf…) and implementing `Unit`, `AttackerUnit` or `WorkerUnit`. The comment in [Unit.java](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/interfaces/Unit.java) calls this a "workaround" for the lack of multiple inheritance. Each unit re-declares about 100 lines of the same state (goals, cooldowns, owner, anchor, checkpoints): see [VindicatorUnit](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/units/villagers/VindicatorUnit.java).
- **Owner:** stored in `SynchedEntityData` as the player's name.
- **AI:** vanilla `GoalSelector` with custom goals: MoveToTargetBlock, SelectedTarget, GatherResources, BuildRepair, ReturnResources, Garrison and others.
- **Stats:** RTS stats are **registered Attributes**: attack range, aggro range, sight range, ranged/magic resist, attacks per second, and since 1.5.0 crit, lifesteal and similar. Upgrades and items can therefore use attribute modifiers. The values reach clients through vanilla attribute sync only because RoN calls `setSyncable(true)` on each of its 21 attributes; vanilla attributes are not synced by default (`Attribute.java:26`). **[corrected]** This is worth copying.

### 3.5 Pathfinding

**History:**
- Started with vanilla navigation. Follow range is 16, raised to 64 when improved pathfinding is on (`Unit.FOLLOW_RANGE_IMPROVED`).
- Improved pathfinding shipped in 1.0.11 (2024-12-09) with warnings about higher CPU use. The `improvedPathfinding` gamerule was added in 1.0.12 (2024-12-23).
- Experimental `rtsPathfinding` in 1.3.7 (2026-07-03), where the gamerule was renamed from `improvedPathfinding`. "Pathfinding Overhaul" in 1.3.8 (2026-07-06); at that point it auto-enabled for borders ≤ 1,024 blocks.
- 1.3.8e (2026-07-18) fixed "significant delays with higher unit volumes".
- [PR #445](https://github.com/SoLegendary/reignofnether/pull/445) (2026-06-27) found four stacked causes:
  - workers re-pathing every second;
  - each worker scanning blocks for resources;
  - unreachable leaf targets burning the full ~250 ms node budget;
  - a wall-avoidance cost recomputed per cell, per unit, per search, which was "~95% of A*'s time".

  The fix made pathfinding about 10× faster. The PR says it was "tested with 200 workers mining on a server: held 66 TPS"; the TPS figure is ambiguous.

**Current architecture** (all in [`unit/pathfinding/`](https://github.com/SoLegendary/reignofnether/tree/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding)):
- [RtsPathfinder](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/RtsPathfinder.java) is the entry point. It snaps the goal to a standable cell within radius 4. If no such cell exists, the request runs on a capped budget of 8,000 nodes and approaches best-effort.
- [WalkabilityGrid](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/WalkabilityGrid.java) caches per-chunk walkability:
  - it is built **on the main thread**, because `getBlockState` and collision shapes must be read there, with a budget of 4 cold chunks per tick (gamerule `pathfindingChunkBuildsPerTick`, clamped 1–64);
  - the cell kinds are land, water, fire, lava and slime, plus a precomputed "crowd" wall-proximity cost;
  - only a vertical band of ±24 around the path is classified;
  - a block change marks a chunk dirty, and only the changed box is reclassified, after 4 quiet ticks and at most 40 ticks later. At most 8 dirty chunks are patched per tick. Stale cells stay walkable in the meantime, so units are never stranded.
- [PathfinderWorkerPool](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/PathfinderWorkerPool.java) does the compute:
  - it takes an immutable snapshot and runs **3D grid A*** on a fixed pool (default: half the cores; resizable at runtime through the `pathfindingThreads` gamerule; capped at 32);
  - results are delivered back on the main thread, at most 256 per tick;
  - a validity check (`valid`) drops superseded or dead requests before they spend budget;
  - backpressure: above 500 queued requests the caller gets a "busy" signal and keeps its old path, which prevents re-submit loops.
- [PathfinderConfig](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/PathfinderConfig.java) and [MobilityClass](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/MobilityClass.java) set the search:
  - radius 96 and 250k nodes per segment;
  - up to 10 **chained segments** for long orders;
  - a 20-neighbour model: 8 horizontal directions plus ±1-block steps;
  - falls of up to 3 blocks;
  - wall-climbing cells for spiders;
  - mobility classes HUMANOID, AQUATIC, LARGE and FIRE_IMMUNE, plus a footprint radius for wide mobs;
  - an arrival "settle" distance of 1.5 blocks, so stopped groups don't jitter.
- The finished path is converted to a vanilla `Path`; vanilla movement control still does the walking.
- [WorldBorderServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/worldborder/WorldBorderServerEvents.java): `rtsPathfinding` turns on automatically only when the **world border is ≤ 1,280 blocks** (80 × 80 chunks). The gamerule can still force it on elsewhere. The whole map's navmesh is then built up front at server start. **[corrected]** The walkability cache (`MAX_CACHED_CHUNKS` = 72 × 72 = 5,184 chunks) was sized, by its own comment, for borders ≤ 1,024 blocks (64 × 64 chunks plus margin). A 1,280-block border means about 81 × 81 = 6,561 chunks, so a full prewarm no longer fits and the LRU evicts about 1,400 chunks **[inference from code; not tested at runtime]**. On open worlds RoN stays on vanilla pathfinding.
- **Observability:** the F7 overlay shows path compute time, end-to-end latency and queue depth. Strategos should have this from day one.
- **Not present** [inference from reading]: no flow fields, no hierarchical layer, no explicit local avoidance beyond vanilla entity push and formation slots. `GridNeighbors.java` describes itself as a "shared movement model for grid A* and flow fields", which suggests flow fields are planned or were removed; none exist in `1.20.1-dev`.

### 3.6 Buildings

- **Templates:** buildings are vanilla structure NBT templates: 67 files in `data/reignofnether/structures`, loaded in [BuildingBlockData](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/building/BuildingBlockData.java).
- **Placement:** a client ghost, then server validation in [BuildingValidators](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/building/BuildingValidators.java): terrain, overlap, nether terrain for piglins, explored-in-fog.
- **Construction:** blocks are placed one at a time, with builder speed taken from Age of Empires II: `msPerBlock = 3·base/(builders+2)`, i.e. 1 builder = 100 %, 2 = 75 %, 5 ≈ 43 % ([BuildingPlacement](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/building/BuildingPlacement.java)).
- **Health:** health is proportional to blocks placed, and a building is destroyed below a minimum block fraction (default 0.5).
- **Custom buildings:** mapmakers can save their own structures as buildings, and since 1.5.0 as shops.
- **Fixed lag:** [PR #348](https://github.com/SoLegendary/reignofnether/pull/348) replaced "BuildingPlacement scan thousands of blocks every tick" with event-driven counting.

### 3.7 Economy: resources, gathering, production, research, population

- **Resources:** food, wood and ore; emeralds became a 4th resource in 1.5.0 (2026-10-11).
- **Gathering uses real world blocks.** [ResourceSources](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/resources/ResourceSources.java) defines 41 source types (logs, ores, crops, farmland and more). Workers break the blocks with `destroyBlock` and replant farms ([GatherResourcesGoal](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/goals/GatherResourcesGoal.java)). [ResourceIndex](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/resources/ResourceIndex.java) is a per-chunk spatial index, filled at 8 chunks per tick and capped at 2,048 chunks (LRU).
- **Production and population:** each building has its own production queue. Population comes from houses; the default `maxPopulation` is **150** ([ResourceCosts](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/resources/ResourceCosts.java)); a gamerule of the same name overrides it.
- **Research is hard-coded.** There are 52 Java classes, one per tech. What is researched is stored as (player name, id) pairs in SavedData. Effects are `playerHasResearch(...)` checks scattered through gameplay code ([ResearchServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/research/ResearchServerEvents.java)). This won't scale to 0 A.D./AoE tech trees; use data-defined modifiers instead.

### 3.8 Fog of war (1.4.0, 2026-08-04; [PR #422](https://github.com/SoLegendary/reignofnether/pull/422))

- **Scope:** block-granular, optional (`/rts-fog enable`, or gamerule `reignofnetherForceFog`), and only on maps up to 80 × 80 chunks.
- **Server side** ([FogOfWarServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/fogofwar/FogOfWarServerEvents.java)):
  - vision is recomputed **every 10 ticks** into "live" chunks, "edge" chunks and a 16 × 16 bitmask of visible columns per edge chunk;
  - [ChunkMapMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/fogofwar/ChunkMapMixin.java) filters `ChunkMap#getPlayers`, so chunk updates don't go to players for whom the chunk is dark. Per-column filtering of edge chunks happens in `ChunkHolderMixin`. **[corrected: minor]**
  - [TrackedEntityMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/fogofwar/TrackedEntityMixin.java) cancels `ChunkMap$TrackedEntity#updatePlayer` for enemies outside the viewer's vision;
  - a map snapshot taken at match start is served to players who join or rejoin, so reconnecting can't be used to cheat;
  - a unit attacking from fog is revealed for a short time.
- **Client side:** the fog is drawn through the biome-tint system, so it works under Embeddium, Oculus, OptiFine and shaders.
- **Sight:** sight ranges are Attributes. The default is 16. Heroes and flying windcallers/ghasts have 24, garrisons and capitols 32, pillagers 18, spiders at night 20, wardens 12.
- **Caveats** [inference from code; needs a runtime test]:
  - (a) Fog was added on top of a network layer that broadcasts: there are **135 `PacketDistributor.ALL` call sites**. Unit stat sync is fog-gated, but much else (resources, anchors, animations) goes to everyone.
  - (b) The mixin cancels `updatePlayer` at its start. In vanilla, unpairing also happens inside `updatePlayer`. Vanilla re-runs `updatePlayer` often: when an entity or player changes chunk section (`ChunkMap.tick`), and through `ChunkMap.move` on every accepted movement packet from the viewer. **[corrected]** Each of those calls is cancelled for a fogged entity, so vanilla's unpair branch never runs, and RoN has no other unpair path. So an enemy that was already visible and then walks into fog probably stays paired, and its updates keep reaching the client.

### 3.9 Multiplayer model

- **Server:** one shared world, on a dedicated server or hosted peer-to-peer through Essential.
- **Two kinds of player:**
  - RTS commanders, in spectator mode with the top-down camera;
  - normal first-person (survival) players, who coexist with them: they fight units, drop resources at stockpiles, use portals and can ally with commanders.

  Units attack enemy survival players on their own.
- **Other systems:** alliances with allied control, coop mode, scenario mode, a start-match menu (F9), and game modes Classic, Wave Survival, King of the Beacon (a Classic variant) and Sandbox.
- **Identity:** players are identified by **name** everywhere; a code comment says "match by name; entity ids change on reconnect".

### 3.10 AI opponents

None. `RTSPlayer.isBot()` (id < 0) exists, but bots are only created by `startRTSBot`, and the only caller is the tutorial ([PlayerServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java), `TutorialServerEvents`). There is no decision-making AI. PvE content is scripted: waves, neutral units, scenarios.

### 3.11 Performance limits and known issues

- **Scale:** 150 population per player by default.
- **Map size:** the optimised paths (prewarmed navmesh, fog snapshot) need world border ≤ 1,280. As noted in §3.5, the navmesh cache is only sized for about 1,024 blocks. For servers in general, the Modrinth page recommends `view-distance=25` and turning off natural mob and NPC spawns. **[corrected]**
- **Fixed hot spots:**
  - re-path loops, per-worker scans, unreachable targets and per-cell cost recompute (PR #445);
  - Java streams in hot paths and per-tick block scans, plus caching selection sorting during rendering (PR #348, which claimed "at least 6%" from removing streams);
  - lag in large custom buildings (1.5.0).
- **Code-quality signals:**
  - global static mutable state in the `*ServerEvents` classes caused bugs like research and cheats carrying over between singleplayer worlds (fixed in 1.4.0; tracing the bug to static state is **[inference]**);
  - identity by player name;
  - one giant action enum;
  - per-unit boilerplate;
  - one contributor wrote that "the code quality in the project was extremely low" (PR #348).
- **Tests:** one unit-test class (`MiscUtilTest`).

### 3.12 What players praise and criticise

Third-party evidence is thin; the maintainer runs support on Discord. The signals I could find:
- **Praise:** "transforms it into an entirely new experience" ([80.lv, 2025-06-27](https://80.lv/articles/check-out-this-mod-that-reimagines-minecraft-as-rts)). There is an official map pack of community-made PvP maps (20+; the count was not re-checked), and the download numbers above.
- **Criticism (from the mod's own changelogs and PRs):** performance and pathfinding lag, fog labelled experimental, and problems with Embeddium/Oculus until PR #422.
- **Unverified:** a Moddex review (fetch blocked, summary only) reportedly rates performance below gameplay and mentions conflicts with culling mods.

### 3.13 Verdict

- **Borrow (as design only):**
  - a spectator body or a camera entity, plus an orthographic mixin, a cursor screen, frustum and leaf workarounds;
  - stats as Attributes, marked syncable;
  - vanilla NBT templates, AoE2 builder formula, health = blocks placed;
  - resources as real world blocks backed by a spatial index;
  - the whole pathfinding pipeline: budgeted main-thread caching, immutable snapshots, worker pool, request sequencing, backpressure, chained segments, mobility classes;
  - server-authoritative fog with a biome-tint renderer and a match snapshot;
  - bounded-map mode, tunables as gamerules, an F7-style debug overlay;
  - a mixed RTS + FPS player model.
- **Avoid:**
  - inheriting from vanilla mob classes for every unit type;
  - identity by name;
  - one giant action enum;
  - hard-coded techs;
  - static global state;
  - broadcast-first networking;
  - fog bolted on afterwards, including cancelling `updatePlayer` instead of letting vanilla unpair;
  - caches sized for a smaller map than the supported maximum.
- **License:** GPL-3.0-only plus CLA. Clean-room only, unless Strategos itself becomes GPL-3.0.

---

## 4. MineColonies + Structurize + BlockUI (ldtteam)

**Status.**
- 1.21.1 NeoForge release 1.1.1403-1.21.1 (2026-10-04). Through September 2026 there were 2–6 snapshot or release uploads a week; CurseForge shows none since 2026-10-06. **[corrected]** 1.20.1 Forge is maintained in parallel.
- About 89.5M CurseForge downloads.
- The `version/1.21` branch builds against NeoForge 21.1.80, Structurize 1.0.832-1.21.1-snapshot and BlockUI 1.0.199-1.21.1-snapshot.
- All GPL-3.0, with CLAs.
- No `src/test` directory on `version/1.21`.

**Citizen and worker AI architecture.**
- **State machines:** worker AI is a deep class hierarchy (`AbstractAISkeleton → AbstractEntityAIBasic → …Interact → …Skill → …Structure → …StructureWithWorkOrder`). Each job AI registers transitions on a [TickRateStateMachine](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java).
  - Transitions come in priority tiers: AI-blocking, events, state-blocking, then the current state.
  - **Each transition has its own tick rate**, and start offsets are staggered across transitions (`tickOffsetVariant`) so agents don't all evaluate on the same tick. This is a cheap way to run hundreds of agents. **[corrected]** When the server lags, a global `slownessFactor` = clamp(last tick ms / 50, 1, 5) *divides* each interval. Transitions then run after fewer ticks and keep their real-time pace. This is lag compensation, not throttling: it adds per-tick AI work when the server is slow.
- **Data separate from the entity:**
  - Colonies live in `SavedData` ([ServerColonySaveData](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/api/colony/savedata/ServerColonySaveData.java)).
  - [CitizenData](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/CitizenData.java) owns job, home, skills and inventory. `updateEntityIfNecessary()` re-spawns the entity when it is missing.
  - [TravellingManager](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/managers/TravellingManager.java) handles trips abstractly: the entity is absent while "travelling" (used for Nether expeditions). This is a good model for AoE-style trade carts and off-screen logistics.
  - Clients get a serialized [ColonyView](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/ColonyView.java), sent only to subscribers.

**Pathfinding.**
- **Executor:** [Pathfinding.java](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/Pathfinding.java) is a `ThreadPoolExecutor(1, 1)` — **one worker thread** — with a 10,000-job queue.
- **Jobs:** [AbstractPathJob](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/pathjobs/AbstractPathJob.java) is A* with a default `MAX_NODES = 8000` (config multiplier 1–4). There are subclasses for specific goals: find tree, find water, random position, move away, raider path, and others.
- **World reads:** [ChunkCache](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/world/ChunkCache.java) holds references to **live** `LevelChunk`s that are read off-thread. This is faster than copying, but racy [inference: tolerated in practice, but not safe].
- **Costs:** [PathingOptions](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/PathingOptions.java) has rich costs: jump 2, drop 1, on-path ⅙, rail ⅒, swim 4, entering water 24, doors/gates 3.
- **Stuck handling:** [PathingStuckHandler](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/navigation/PathingStuckHandler.java) escalates: repath → teleport a few nodes along the path → place ladders or leaves → break the blocks ahead → full "stuck" action (teleport to the goal or take damage).
- **Raiders:** [PathJobRaiderPathing](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/pathjobs/PathJobRaiderPathing.java) can path *through* blocks at cost 30 and place ladders.
- **Distance guard:** [MinecoloniesAdvancedPathNavigate](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java) refuses jobs longer than **900 blocks**. In 1.20.1-1.1.814 the guard was 500 blocks. Guards and couriers in a colony more than 40 chunks across flooded the log with "walk too far" errors ([#10692](https://github.com/ldtteam/minecolonies/issues/10692), 2025-03). **[corrected]**

**Buildings, upgrades, builders.**
- **Blueprints:** every building level is its own blueprint from a style pack. A work order rewrites the trailing level digit to target level N ([WorkOrderBuilding](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/workorders/WorkOrderBuilding.java)).
- **Builder AI** ([AbstractEntityAIStructure](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/ai/workers/AbstractEntityAIStructure.java)): drives Structurize's [StructurePlacer](https://github.com/ldtteam/Structurize/blob/version/1.21/src/main/java/com/ldtteam/structurize/placement/StructurePlacer.java) through the [stages](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/ai/workers/util/BuildingProgressStage.java):
  1. CLEAR
  2. BUILD_SOLID
  3. WEAK_SOLID
  4. CLEAR_WATER
  5. CLEAR_NON_SOLIDS
  6. DECORATE
  7. SPAWN (entities)

  REMOVE and REMOVE_WATER are used for deconstruction. The progress position is saved so building resumes after a restart.
- **Placement handlers:** each block type has an [IPlacementHandler](https://github.com/ldtteam/Structurize/blob/version/1.21/src/main/java/com/ldtteam/structurize/placement/handlers/placement/IPlacementHandler.java) that computes required items and handles doors, beds and multi-blocks. Iterators (default, Hilbert, inward circle, random) set the build order.
- **Materials:** materials reach the builder through the colony request system, with couriers delivering from warehouses [inference: much more complex than an AoE-style resource counter needs].

**Research, UI, raids, limits.**
- **Research** is datapack JSON (`researches`, loaded by [ResearchListener](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/datalistener/ResearchListener.java)).
- **UI** is BlockUI: XML layouts plus `Window` classes (GPL).
- **Raids:**
  - Raid types: barbarian, pirate and Norsemen ships, drowned pirates, Egyptian, Amazon.
  - Each runs a state machine ([HordeRaidEvent](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/events/raid/HordeRaidEvent.java); `EventStatus` STARTING → PREPARING → PROGRESSING → (WAITING) → DONE/CANCELED **[corrected: minor]**).
  - Raiders first **camp at campfires**, which telegraphs the attack, then attack, breaking blocks and doors if configured.
- **Defaults:** `maxCitizenPerColony` = 250 and `maxRaiders` = 80 ([ServerConfiguration](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/api/configuration/ServerConfiguration.java)).

**Verdict.**
- **Borrow (design only):**
  - per-transition tick rates with staggered offsets. Use lag compensation like MineColonies' only as a deliberate choice; an RTS may prefer to shed non-critical AI under lag instead;
  - data-first agents, with entities as avatars;
  - abstract travel;
  - a stuck-handling escalation ladder;
  - phased progressive placement with per-block handlers;
  - per-level building blueprints;
  - JSON research;
  - client views pushed to subscribers;
  - telegraphed raids.
- **Avoid:**
  - a single path thread for an RTS;
  - live-chunk reads off-thread;
  - item-logistics economies;
  - deep inheritance of AI classes.
- **License:** GPL-3.0. Even a hard dependency on Structurize (calling it via its API) arguably makes Strategos a combined work [inference: the FSF's view on plugins]. Avoid that unless Strategos goes GPL.

---

## 5. Villager Recruits and Talhanation's family

- **Recruits** ([repo](https://github.com/talhanation/recruits)):
  - **Status:** 1.15.2 for Forge 1.20.1 (2026-06-29); daily commits on `main` (2026-10-19). README: "All Rights Reserved unless otherwise explicitly stated"; Modrinth says ARR. The repo has no LICENSE file, although Modrinth links one.
  - **Command system:**
    - Keys: R opens the command screen, U teams, M the world map ([ModShortcuts](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/init/ModShortcuts.java)).
    - The [CommandScreen](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/client/gui/CommandScreen.java) has movement, combat and other categories.
    - Targets come from where the player looks, via `player.pick(100–200)` on the server ([CommandEvents](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/CommandEvents.java)).
    - Orders: move, hold, follow, wander, back-to-position, face, attack, strategic (area) fire, mount, protect, upkeep, aggro states.
  - **Formations** ([FormationUtils](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/util/FormationUtils.java)): line, square, triangle, circle, hollow circle, hollow square and V, with spacing 1.75 and a "tight" option, oriented to the player's facing.
  - **Groups** are UUID-keyed.
  - **Factions:** layered on vanilla scoreboard `PlayerTeam`s, with diplomacy NEUTRAL/ALLY/ENEMY, treaties, chunk claims (default 50) and sieges ([RecruitsDiplomacyManager](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/world/RecruitsDiplomacyManager.java)). The world map has routes and waypoints.
  - **Leaders (commander, captain) and messengers are chunk loaders** via `ForgeChunkManager` ([AbstractChunkLoaderEntity](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/entities/AbstractChunkLoaderEntity.java)), so patrols keep running away from players.
  - **Async pathfinding:** on by default (`UseAsyncPathfinding = true`), with one worker thread by default (`AsyncPathfindingThreadsCount = 1`). **[corrected]** It is marked "petal start/end", i.e. taken from the archived Petal server fork, which is GPL-3.0. This is another license tangle.
  - **Limits:** 100 recruits per player and 500 NPCs per faction by default.
- **Villager Workers 2.0** (2026-05): the worker is "only the labour". Work is defined by *Work Areas* placed in the world (whether they are entities was not checked): storage, lumber, fishing, animal farm, mining, building, crop farm, kitchen, market and worker home, plus couriers. This cleanly separates "who works" from "what work".
- **Siege Weapons:** catapults and ballistae with projectile types.
- **Small Ships:** has 1.21.1 builds.

All are ARR.

**Verdict.**
- **Borrow (ideas):**
  - the formation catalogue and spacing;
  - aggro states;
  - UUID groups;
  - diplomacy on top of vanilla teams;
  - leaders that hold chunk tickets;
  - work areas as data.
- **Avoid:** using the look ray as the only way to command large armies.
- **License:** none of their code or assets can be used.

---

## 6. Millénaire, Ancient Warfare 2 and other NPC mods

- **Millénaire:**
  - **Status:** **9.0.2 for NeoForge 1.21.1** (2026-09-18; 9.0.0-beta.2 was 2026-07-25). Custom license. Maintained by kinniken and FireController1847.
  - **Source:** the Java source is not public. The [GitLab project](https://gitlab.com/Millenaire/Public) holds only content: cultures, villagers, goals, quests, languages.
  - **Data format:** buildings are **PNG layer plans** with upgrade levels (`carpenterhouse_A0.png`…`A3.png`) plus small text configs (`max:2;priority:800;…`) ([example](https://gitlab.com/Millenaire/Public/-/tree/master/millenaire/cultures/norman/buildings/houses)).
  - **Borrow:** cultures (≈ civilizations) as data; buildings that upgrade through successive plans.
  - **License:** ideas only.
- **Ancient Warfare 2:**
  - **Status:** GPL-3.0-or-later. Last release 2.7.0.1038 for 1.12.2 (2021-04-29); repo idle since 2021-11.
  - **Modules:** Core, Automation, NPCs (workers, soldiers, world-gen factions), Structures (its own template system), Vehicles (siege engines).
  - **Commands:** a *command baton* selects NPCs and issues MOVE/ATTACK/ATTACK_AREA/GUARD/SET_HOME/SET_UPKEEP ([NpcCommand](https://github.com/P3pp3rF1y/AncientWarfare2/blob/1.12.x/src/main/java/net/shadowmage/ancientwarfare/npc/npc_command/NpcCommand.java)). Standing instructions are **order items** — Work, Routing, Upkeep, Combat, Trade ([orders/](https://github.com/P3pp3rF1y/AncientWarfare2/tree/1.12.x/src/main/java/net/shadowmage/ancientwarfare/npc/orders)).
  - **Known problems:** NPCs get stuck on steps and ladders ([#1365](https://github.com/P3pp3rF1y/AncientWarfare2/issues/1365), [#1410](https://github.com/P3pp3rF1y/AncientWarfare2/issues/1410)). The source code admits the orders design needs refactoring ("TODO refactor to proper individual instances of commands").
- **Guard Villagers:** code under MIT, `src/main/resources` ARR. Its asset license explicitly forbids "any ai-directed version ports" — relevant to an AI-assisted project. Patrol and follow guards, nothing RTS-specific.
- **Easy NPC** (MIT, 1.21.1): NPC authoring (skins, dialogs, actions). **MCA Reborn** (GPL-3.0, 1.21.1): villager life simulation. **Human Companions** (GPL-3.0, 1.20.1, idle since 2024-12). **Taterzens** (MIT, Fabric, no 1.21.1 build). **Custom NPCs** (closed, ≤1.16.5 on CurseForge).
  - None of these matters for RTS beyond small UX ideas.
- **Towny** ([CC BY-NC-ND 3.0](https://github.com/TownyAdvanced/Towny)) is the server-plugin precedent for claims and nations. It forbids derivatives.

---

## 7. Other RTS-like mods for 1.20+/1.21.x

Searched on Modrinth (rts, strategy, army, commander, top-down camera, kingdom) and on the web for CurseForge.

- **i113w's Camera Lib** (MIT; NeoForge 1.21.1 from 21.1.209; client-only; v0.0.x, newest 1.21.1 build 0.0.3-alpha3 from 2026-06-09; ~560 downloads; 0 GitHub stars) is the cleanest reusable reference:
  - a client-only camera entity set with `Minecraft#setCameraEntity`. A side effect: `LocalPlayer#isControlledCamera()` then returns false, so the client stops sending movement packets while the camera is active; Camera Lib does not patch this **[corrected: added]**;
  - an optional orthographic mixin on `GameRenderer#getProjectionMatrix(D)` ([GameRendererMixin](https://github.com/i113w/113CameraLib/blob/main/src/main/java/com/i113w/camera_lib/mixin/GameRendererMixin.java));
  - [LevelRendererFrustumMixin](https://github.com/i113w/113CameraLib/blob/main/src/main/java/com/i113w/camera_lib/mixin/LevelRendererFrustumMixin.java) skips `LevelRenderer#offsetFrustum` — the same freeze RoN works around;
  - a free cursor without a screen: `MouseHandler#releaseMouse()` every client tick, the OS cursor hidden through GLFW, and its own cursor drawn **[corrected: added]**;
  - [MouseRayCaster](https://github.com/i113w/113CameraLib/blob/main/src/main/java/com/i113w/camera_lib/math/MouseRayCaster.java): ray through the inverse of projection × model-view, then block clip, then entity hit;
  - box-select and right-click events.

  [Better Mine Team](https://github.com/i113w/BetterMineTeam) (MIT, NeoForge 1.21.1) builds RTS command on top of it: box select, move, attack, attack-move, formations. It is a "reforged" MineTeam: the MIT notice is © XiaoHuNao, and the README says it was developed with AI assistance.
- **RTS Building** (LGPL-3.0-only code, ARR assets, plus custom "User Terms 1.0" in `License.md` whose §2.4 asks any project using its code or resources to be open source unless permitted; NeoForge 21.1.238; ModDevGradle 2.0.140) **[corrected]**:
  - a server-spawned camera entity, kept within an "action radius" of the player (default 128, range 48–512). On the client a local mirror camera is set with `setCameraEntity`, and a `LocalPlayerMixin` forces `isControlledCamera()` to true so movement packets keep flowing;
  - **`@Pseudo` mixins for Sodium's `LevelSlice` and Embeddium**, because Sodium builds chunk meshes off the vanilla `RenderChunkRegion` path, so block hiding must be done twice ([SodiumLevelSliceMixin](https://github.com/Hcrab/RTSbuilding/blob/main/src/main/java/com/rtsbuilding/rtsbuilding/mixin/SodiumLevelSliceMixin.java));
  - the best testing setup seen: [src/test](https://github.com/Hcrab/RTSbuilding/tree/main/src/test) with unit, contract and performance JUnit 5 + Mockito suites, plus `gameTestServer` runs.
- **MineFortress** (MIT, Fabric 1.20.1/1.20.2, dormant since 2025-08-09):
  - a "Fortress" game mode, perspective camera, mouse picking by redirecting `getRotationVec` ([FortressGameRendererMixin](https://github.com/remmintan/minefortress/blob/master/src/main/java/org/minefortress/mixins/renderer/FortressGameRendererMixin.java));
  - blueprints and area designations;
  - pawns move with **one Automatone (Baritone) instance per entity**, using its own Automatone fork `1.0.9-minefortress` ([BaritoneMoveControl](https://github.com/remmintan/minefortress/blob/master/src/main/java/org/minefortress/entity/ai/controls/BaritoneMoveControl.java)) [inference: fine for dozens of pawns, not hundreds];
  - Kotlin and Yarn mappings, so porting to Mojmap/NeoForge is work.
- **Hundred Years Warfare** (ARR, closed; 1.20.1/1.21.1; ~178k downloads): "RTS mode" with hotkeys, formations and shift-queued commands; counter relationships between unit types; workers "reworked based on Ancient Warfare 2". It says of itself that it is "not an RTS-focused mod".
- **Others:**
  - Ages of Dominion (ModJam 2026, NeoForge 26.1.2, singleplayer only, ARR, with AoE-like Dark/Feudal/Castle ages) **[corrected]**;
  - Tale of Kingdoms (LGPL-3.0, Fabric, 1.21.1 build 1.0.6 listed on Modrinth as a release, 2024-09-04) **[corrected]**;
  - Steve's Army (LGPL-2.1+, squad AI with fire teams and threat sharing, 1.20.1, beta 0.3.1);
  - RTS Map Command Recruits (ARR) and Commanders Vision (MIT), both addons for Recruits.

---

## 8. Minecraft Legends (Mojang Studios / Blackbird Interactive)

**Facts.**
- Released 2023-04-18 on PC, Xbox, PlayStation and Switch, with a campaign and 4v4 PvP ([VGC](https://www.videogameschronicle.com/news/mojang-reveals-minecraft-legends-april-release-date-and-shows-pvp-gameplay/)).
- Scores: Metacritic PC 65. metacritic.com now shows this from 36 critic reviews; Xbox Series X scores 71. The legacy page's figures of 33 critics and a 5.4 user score are unverified, because the page returned HTTP 403; the current page shows a user score of 5.7 from 249 ratings. **[corrected]** OpenCritic top-critic average 70, 41 % recommend ([OpenCritic](https://opencritic.com/game/14349/minecraft-legends)).
- On 2024-01-10 Mojang said no new content would come. The final content was the "Snow vs Snouts" Lost Legend, released that month ([Nintendo Life](https://www.nintendolife.com/news/2024/01/minecraft-legends-development-ends-as-final-update-launches)). That the November 2023 update was the last major one is (unverified): the cited article doesn't say it. **[corrected]**

**Command model.**
- The hero's banner issues Rally, Quick Direct, Banner View (aim at a target), Charge and Group Charge, Focus Target and Group Focus Target, and Type Select ([guide](https://upcomer.com/how-to-control-your-mobs-in-minecraft-legends)). All of them only reach mobs near the hero.
- Allays gather and build automatically. Units come from spawners or are lured from the wild.

**Why it was received as it was** (reviewer statements):
- Orders reach only units in "a small circle around you". "Either all your stone golems go to attack that structure or none of them do." Units drift off to other targets ([Nintendo Life, 4/10](https://www.nintendolife.com/reviews/nintendo-switch/minecraft-legends)).
- Units fell off narrow ramps and then stood idle. There was no zoomed-out view for managing a battle. The rally-and-return loop after losses was tedious. Tower upgrades "would have been far better as a skill tree" ([Kotaku](https://kotaku.com/minecraft-legends-review-xbox-game-pass-mojang-1850334390)).
- The controller UI "feels like a game built for keyboard and mouse". Piglin bases: "once you've seen one of these, you've seen them all." Rewards felt thin ([Nintendo Life](https://www.nintendolife.com/reviews/nintendo-switch/minecraft-legends)).
- Mechanics were explained by telling rather than showing. Singleplayer was always online, so it couldn't be paused ([Kotaku](https://kotaku.com/minecraft-legends-review-xbox-game-pass-mojang-1850334390)).

**Mojang's August 2023 patch (1.17.50310)** ([patch notes](https://nintendoeverything.com/minecraft-legends-update-out-now-version-1-17-50310-patch-notes/)):
- Banner View shows a path rectangle of the **actual width** units will use, updated in near real time.
- Destinations shown are where units will actually go, and mob pathing reaches them more reliably.
- Fewer falls from ledges; mobs can't push each other off.
- Auto-lure after a mob spawns, and refunds for lured mobs at spawners.
- A warning when nearing the structure limit, and a countdown to piglin attacks.

**UX lessons for Strategos.**
1. Provide an overview command mode. Don't tie command range to the avatar.
2. Offer individual, type and group selection, plus control groups.
3. Before an order is confirmed, show exactly where units will go and the footprint of the formation.
4. Pathing in 3D terrain — ramps, ledges, drops — is a UX feature, not an implementation detail.
5. Telegraph AI attacks: timers and warnings, as MineColonies does with campfires.
6. AI opponents need different strategies and base layouts.
7. Teach by doing; RoN ships a tutorial map.
8. Pause in singleplayer.

---

## 9. Pathfinding precedents

| System | Thread model | World access | Algorithm and agents | Limits | License |
|---|---|---|---|---|---|
| Vanilla 1.21.1 `PathNavigation` | Synchronous, server thread | `PathNavigationRegion` within ±(follow range + 8/16) | A* per mob; budget `floor(FOLLOW_RANGE×16) × multiplier`, fixed at navigation creation | Follow range (16 by default) | Mojang (callable, not copyable) |
| RoN `rtsPathfinding` | Main thread builds cache under a budget; A* on a pool (cores/2, gamerule) | Immutable snapshot of a per-chunk walkability cache | 3D grid A*, 8 horizontal directions with ±1-block steps (20 neighbours), falls ≤ 3, climbing, 4 mobility classes, footprints, 10 chained segments of radius 96 | Auto-on and prewarm for world border ≤ 1,280; cache sized for ≤ 1,024 **[corrected]** | GPL-3.0 |
| MineColonies | One worker thread, 10k queue | Live `LevelChunk` refs + caching lookup | A* with rich costs; typed path jobs; escalating stuck handler | 8k nodes (×1–4); 900-block guard | GPL-3.0 |
| Recruits | Pool (on by default, 1 thread by default) **[corrected]** | Vanilla region (Petal approach) | Vanilla A*, async | — | ARR / Petal GPL |
| Baritone v1.11.3 (1.21.1) | Own thread | 2-bit chunk cache (AIR/SOLID/WATER/AVOID) | Segmented A*, incremental cost backoff, path splicing, breaks and places blocks | One 1×2 agent; "horses (2×3)" may never be supported | LGPL-3.0 |
| Automatone | Server, Fabric | as Baritone | Baritone for any `LivingEntity` | MC ≤ 1.20; idle since 2024-01 | LGPL-3.0 |
| FrostedHeart | Main thread snapshot → worker → main thread | 64×64 heightmap snapshot | **Reverse-BFS flow field per target chunk**, shared by every citizen with that target; at most 2 builds per tick; rebuilt every 2,400 ticks | 2.5D: no caves or overhangs; a height step > 1 blocks | GPL-3.0 |

Sources: [Baritone FEATURES.md](https://github.com/cabaletta/baritone/blob/1.21.1/FEATURES.md); [FlowFieldCache](https://github.com/TeamMoegMC/FrostedHeart/blob/HEAD/src/main/java/com/teammoeg/frostedheart/content/town/citizen/nav/FlowFieldCache.java); Recruits [AsyncPathProcessor](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/pathfinding/AsyncPathProcessor.java). The Boobcat Edition claims "flow-field smoothing", but there is no source to check.

**What follows for Strategos** [inference, ranked by evidence]:
1. Own the pathfinding pipeline from day one; vanilla navigation is the wrong shape. RoN's pipeline is the right skeleton:
   - budgeted main-thread classification;
   - incremental reclassification of dirty regions;
   - immutable snapshots;
   - worker pool;
   - request sequence numbers;
   - backpressure;
   - an arrival settle distance.
2. Precompute per-cell costs once per chunk, not per search; that was RoN's 10× win. Fail fast on unreachable goals. Size caches for the largest supported map and test that a full prewarm doesn't evict.
3. For many units sharing a target (an army's move, workers going to a drop-off), use shared fields or a leader path plus slots, as FrostedHeart and RoN's formations do. Cap field builds per tick and rebuild on terrain change.
4. Add a coarse hierarchical layer (chunk portals / HPA*) for cross-map orders. Nobody has done this; RoN instead chains 96-block A* segments.
5. Write an escalation ladder for stuck units (MineColonies), and add explicit cost models for doors, water and fire.
6. Baritone and Automatone are not RTS movers: one agent, player-sized, the wrong licence shape for copying. Read FEATURES.md for ideas (segmenting, cost backoff, chunk compaction).

---

## 10. Platform facts in 1.21.1 / NeoForge 21.1 that these techniques rely on

All verified in the decompiled sources.

- `GameRenderer#getProjectionMatrix(double)` (line 980) is also used to build the culling frustum. NeoForge has `ViewportEvent.ComputeCameraAngles` and `ComputeFov` and `CalculateDetachedCameraDistanceEvent`, but no event to replace the projection, so orthographic needs a mixin.
- `LevelRenderer#offsetFrustum` → `Frustum#offsetToFullyIncludeCameraCube(8)` loops until the camera cube is inside the frustum. Bypass it when orthographic is on.
- Entity visibility: `ChunkMap$TrackedEntity#updatePlayer` pairs or unpairs on `distance && entity.broadcastToPlayer(player) && isChunkTracked`. **[corrected]** It is called:
  - from `ChunkMap.tick()` when an entity or a player changes chunk section;
  - from `ChunkMap.move(ServerPlayer)`, for every tracked entity against that player. That method is public as `ServerChunkCache#move`, and it runs on every accepted movement packet (`ServerGamePacketListenerImpl` lines 460, 891, 970) and every tick for a player spectating another entity.

  Clients send a position packet at least every 20 ticks, but only while their camera entity is the player (`LocalPlayer#isControlledCamera`). `PlayerEvent.StartTracking` is **not** cancellable.
  - So for Strategos's own entities, fog can be enforced by overriding `Entity#broadcastToPlayer(ServerPlayer)`. Vanilla's own unpair branch then runs, unlike RoN's cancel of `updatePlayer`. A fog system should still force re-evaluation whenever vision changes, rather than waiting for the next movement packet. `ServerChunkCache#move(player)` does this without an accessor; an accessor or access transformer on `updatePlayer` also works.
- Client camera entity: with `Minecraft#setCameraEntity(other)`, `LocalPlayer#isControlledCamera()` returns false. The client then stops sending `ServerboundMovePlayerPacket` and stops applying movement input (`LocalPlayer` lines 277 and 700). RTS Building forces it back to true with a mixin. **[corrected: added]**
- Attributes: `Attribute#syncable` defaults to false; call `setSyncable(true)` for stats the client must see. **[corrected: added]**
- Chunk tickets: `TicketController#forceChunk(level, BlockPos | Entity | UUID owner, x, z, add, ticking)` keeps armies and AI bases simulating away from players (Recruits does the equivalent on Forge 1.20.1). Register controllers in `RegisterTicketControllersEvent`; otherwise their tickets are discarded and forcing throws. **[corrected: added]** Tickets are radius-2 region tickets, so entities in the chunk tick. `ticking = true` adds random ticks and spawning. NeoForge counts these chunks when deciding whether a level with no players keeps ticking (`ServerLevel` line 390).
- `ServerPlayer#setCamera` and `getCamera` exist (spectate-style camera body); RoN instead flies the spectator body itself.

---

## 11. License compatibility and clean-room protocol

| License class | Projects | What Strategos may do (license undecided, private now, maybe permissive later) |
|---|---|---|
| MIT | Camera Lib, Better Mine Team (keep both the i113w and the MineTeam/XiaoHuNao notices), MineFortress, Guard Villagers (code only), Easy NPC, Taterzens, Commanders Vision | Copy or fork with the copyright notice kept. Best sources for actual code. |
| LGPL-3.0 / LGPL-2.1 | Baritone, Automatone, RTS Building (code), Tale of Kingdoms, Steve's Army | Depend on them as a separate, unmodified jar. Copying their code into Strategos makes those files LGPL. Changes to the library must be published. **[corrected]** RTS Building also has custom "User Terms 1.0" (`License.md` §2.4) asking that projects using its code or resources be open source, and banning paid distribution. Treat RTS Building as ideas-only. |
| MPL-2.0 | SmartBrainLib | Depend on it freely; changes to MPL files stay MPL (file-level copyleft). |
| GPL-3.0 | RoN, MineColonies, Structurize, BlockUI, Domum Ornamentum, AW2, Human Companions, MCA Reborn, FrostedHeart, Petal | Ideas only. Copying code or hard-linking makes the distributed Strategos GPL-3.0. Private, undistributed use carries no GPL obligations, but it closes off a later permissive release. Contributing upstream requires signing their CLAs. |
| ARR / custom / no derivatives | Recruits, Workers, Siege Weapons, Small Ships, Hundred Years Warfare, Millénaire, Custom NPCs, Towny, Guard Villagers' assets, Ages of Dominion, RTS Map Command Recruits **[corrected]** | Ideas only. No code and no assets. |

**Clean-room protocol, suited to AI-assisted work:**
1. Read GPL or ARR sources in a separate research step and write **behavioural notes**: what the system does, its constants and its failure modes, as in this report. Do not keep code snippets.
2. Implement only from the notes, in sessions that have no copyleft source in context.
3. Record where each subsystem's ideas came from.
4. Never copy assets. Guard Villagers bans AI-directed ports of its assets outright.

---

## 12. Consolidated borrow / avoid

**Borrow (as design):**
- **Bounded match maps:** prewarm walkability and fog for the whole map; open-world support later (RoN). Size the caches for the largest map you allow.
- **Camera:** orthographic or perspective camera with an optional orthographic mode; a cursor screen or released mouse; inverse-MVP picking; frustum bypass; leaf hiding; Sodium/Embeddium adapters (RoN, Camera Lib, RTS Building). Also handle `isControlledCamera` if the client camera is a separate entity.
- **Server-authoritative interest management from day one:**
  - a vision grid per team on a fixed cadence (RoN: 10 ticks);
  - entity gating through `broadcastToPlayer` plus forced re-tracking (`ServerChunkCache#move` or an accessor);
  - chunk and column gating;
  - a single sender for mod packets that knows about visibility;
  - biome-tint fog rendering;
  - a match-start snapshot.
- **Data first:** unit, building and player state keyed by UUID, scoped to the server instance, with entities as avatars (MineColonies). No static globals (RoN's bugs).
- **Stats and techs:** stats as registered, syncable Attributes; techs as data-defined modifiers (RoN attributes + MineColonies JSON research).
- **Buildings:** NBT/structure templates, per-level upgrade plans, phased progressive placement, per-block placement handlers, AoE2 builder formula, health = blocks placed (RoN, MineColonies/Structurize, Millénaire).
- **Commands:** command objects in a registry, shift queues, server validation by UUID, optional client prediction; formation catalogue and drag-to-face; aggro and stance states; control groups (RoN, Recruits).
- **AI scheduling:** per-transition tick rates with staggered offsets, a deliberate lag policy, stuck-handling ladder, abstract travel for off-screen logistics (MineColonies). MineColonies compensates for lag by shortening intervals; it does not throttle. **[corrected]**
- **Persistence away from players:** leaders, armies and AI bases hold chunk tickets (Recruits).
- **Diplomacy:** layered on vanilla teams (Recruits).
- **Telegraphed AI attacks and exact command previews** (MineColonies, Legends patch).
- **Observability:** in-game perf and path overlay, tunables as gamerules (RoN).

**Avoid:**
- inheriting from vanilla mob classes for every unit type;
- identity by player name;
- one giant action enum;
- hard-coded tech classes;
- broadcast-first networking;
- gating entities by cancelling `TrackedEntity#updatePlayer` (it skips unpairing);
- per-tick world scans and streams in hot paths;
- a single path thread, or vanilla synchronous pathing, for armies;
- live-chunk reads off-thread;
- item-logistics economies standing in for AoE-style resource counters;
- third-person-only command;
- avatar-radius command range;
- group-only orders;
- identical AI bases;
- shipping without automated tests.

---

## Verification notes

**What was re-checked (2026-10-20, 23:20–23:55 EEST):**
- Modrinth API, freshly fetched: RoN, Villager Recruits, Villager Workers, Siege Weapons, Small Ships, Guard Villagers, Easy NPC, MCA Reborn, Taterzens, Hundred Years Warfare, Better Mine Team, Camera Lib, RTS Building, Siege of Settlements, Boobcat Edition, SmartBrainLib. Modrinth search: Tale of Kingdoms, Steve's Army, Custom NPCs, Commanders Vision, RTS Map Command Recruits.
- CurseForge via cfwidget: MineColonies, Structurize and Millénaire re-fetched; RoN, AW2, Custom NPCs, Ages of Dominion and Human Companions from earlier the same day. The CurseForge page for Millénaire.
- GitHub:
  - RoN issues #485 and #243, PRs #299, #348, #422 and #445;
  - MineColonies #10692; AW2 #1365 and #1410;
  - Baritone release v1.11.3 and its branches; Automatone branches;
  - Petal, Domum Ornamentum, Workers, Siege Weapons and Small Ships repo metadata.
- Source read on fresh clones:
  - RoN `1.20.1-dev` @3943983 (2026-10-15);
  - MineColonies `version/1.21` @dae860e (2026-10-06);
  - Structurize `version/1.21` @5fa751d;
  - Recruits `main` @44f60bd (2026-10-19);
  - RTS Building `main` @8110237 (2026-10-19);
  - Camera Lib `main` @7d3ad1a;
  - Better Mine Team `neo-1.21.1-dev2` @0a7307a;
  - MineFortress `master` @8bc20c6;
  - FrostedHeart `FlowField`/`FlowFieldCache` and gradle.properties; Baritone FEATURES.md; the Guard Villagers and Towny licenses.
- Decompiled MC 1.21.1 + NeoForge 21.1 in the scratchpad (`mc-src-1.21.1`): Frustum, LevelRenderer, GameRenderer, ChunkMap, ServerChunkCache, ServerGamePacketListenerImpl, ServerPlayer, LocalPlayer, Entity, Attribute, Mob, PathNavigation, PathFinder, ServerLevel; NeoForge ViewportEvent, CalculateDetachedCameraDistanceEvent, PlayerEvent, TicketController, ForcedChunkManager.
- Web: OpenCritic, metacritic.com, the Nintendo Life review and news, the Kotaku review, the nintendoeverything patch notes, the Upcomer guide, VGC, 80.lv, Wikipedia.

**Material corrections:**
1. **RoN navmesh cache.** The cache (5,184 chunks) was sized for 1,024-block borders. RoN now prewarms up to 1,280-block borders (about 6,561 chunks), so "never evicts" is false (inferred from the code). The `rtsPathfinding` gamerule can also force it on any map.
2. **Vanilla entity tracking.** Vanilla re-evaluates pairing on every accepted movement packet through `ChunkMap.move`, not only on section changes. The RoN fog-leak conclusion still stands, because its head-cancel skips the unpair branch. The fix for Strategos is to gate through `broadcastToPlayer` and force re-tracking with `ServerChunkCache#move` or an accessor.
3. **MineColonies `slownessFactor`.** It shortens intervals under lag (compensation), not throttling.
4. **Recruits async pathfinding** is on by default, with 1 thread.
5. **RTS Building licensing.** It has extra "User Terms 1.0" that ask projects using its code to be open source. It is now treated as ideas-only, including its build and test setup.
6. **Camera Lib.** It frees the cursor without a screen and is alpha. Its 1.21.1 build is from 2026-06-09. Using a client camera entity disables movement packets (`isControlledCamera`).
7. **RoN attribute sync** depends on `setSyncable(true)`; vanilla's default is not synced.
8. **Minecraft Legends.** The PC Metascore is now based on 36 critic reviews; the legacy figures (33 critics, user 5.4) are unverified. The final content was "Snow vs Snouts" in January 2024. "November 2023 last major update" is unverified.
9. **Smaller fixes:**
   - RoN cadence (6 releases + 5 betas); the formation-dispatch condition; control groups also store buildings; the sandbox check is on `ownerName`; follow range 16 → 64;
   - MineColonies upload cadence (none since 2026-10-06); the #10692 guard was 500 at the time; `EventStatus` includes WAITING; Structurize's 1.0.835 is a snapshot;
   - SmartBrainLib has 1.21.1 builds for all three loaders; Small Ships has an old 1.21.4 beta;
   - Ages of Dominion is ARR; Tale of Kingdoms' 1.21.1 build is a release; Commanders Vision is MIT and RTS Map Command Recruits is ARR.

**Confirmed unchanged:**
- RoN's version, license, CLA and #485; its camera, frustum and leaf mixins; the selection, command, enum, units, buildings, economy, fog and AI-opponent facts; PR #445; repo metrics;
- MineColonies' 1.21.1 release, pathfinding constants, data/entity split, build stages, research JSON and BlockUI XML;
- the Recruits command model and limits; Millénaire; AW2; Baritone/Automatone; FrostedHeart; the testing findings; the Guard Villagers, Towny, Petal and Domum Ornamentum licenses;
- the platform facts in §10 (projection, frustum, `broadcastToPlayer`, StartTracking, TicketController, `setCamera`);
- the vanilla `PathNavigation` budget and region.

**Still unverified:** the Moddex review; the legacy Metacritic numbers; the "November 2023" Legends statement; the "20+" map count; whether Villager Workers' Work Areas are entities. The RoN fog leak and the cache eviction need runtime tests.

**Open questions affected:**
- Q2 (fog leak): the code-level case is now stronger.
- Q3: a quick search found only 2019–2020 MineColonies pathfinding crash reports (#3826, #4296, #4313, ClassCastException), with no root cause established.
- Q8: Camera Lib has 0 stars and is alpha.
- Q11: Ages of Dominion is ARR.

**Process note:** by mistake, the first batch of Modrinth API requests in this run carried the owner's e-mail address in the User-Agent header. Later requests used a generic User-Agent.

## Recommendations (after verification)

1. 1. Adopt a clean-room rule before any code is written. Design references only: GPL, ARR and custom-licensed projects. These are RoN, MineColonies/Structurize/BlockUI, AW2, the Recruits family, Millénaire, Hundred Years Warfare, Ages of Dominion and RTS Map Command Recruits. Also treat RTS Building as ideas-only: besides LGPL-3.0 it ships custom 'User Terms 1.0' asking projects that use its code to be open source. Write behavioural notes from these sources, implement only from the notes in sessions with no copyleft code in context, and log provenance per subsystem. Copy code only from MIT projects, keeping their notices: i113w Camera Lib, Better Mine Team (which also carries MineTeam's MIT notice), MineFortress, Guard Villagers code (never its assets), Easy NPC, Commanders Vision. Use other LGPL/MPL projects (Baritone, SmartBrainLib) only as separate jars.
2. 2. Make bounded match maps the first-class mode, as RoN does with world border ≤ 1,280: prewarm walkability and fog for the whole map at match start, and keep open-world play as a later feature. This single constraint is what made RoN's pathfinding and fog affordable. Size every per-map cache from the largest map you support, and add a test that a full prewarm does not evict. RoN's 5,184-chunk cache was sized for 1,024-block borders and no longer holds its own 1,280-block maximum (about 6,561 chunks).
3. 3. Build the RTS camera from the 1.21.1 recipe that already works. Treat i113w's Camera Lib (MIT, 0.0.3-alpha3, about 560 downloads) as a reference or fork, not a stable dependency. The pieces:
- an optional orthographic mixin on GameRenderer#getProjectionMatrix(double);
- a mandatory bypass of LevelRenderer#offsetFrustum while orthographic;
- an inverse-MVP mouse ray;
- a free cursor: a dummy screen like RoN, or MouseHandler#releaseMouse plus a hidden OS cursor like Camera Lib;
- leaf/canopy hiding with Sodium/Embeddium adapters.
Decide early how the server-side body works, because chunk sending and entity tracking follow the ServerPlayer: fly a spectator body, or use a camera entity plus chunk tickets. If the client camera is a separate entity (Minecraft#setCameraEntity), LocalPlayer#isControlledCamera() becomes false and the client stops sending movement packets. RTS Building patches that with a mixin; plan the same.
4. 4. Own the pathfinding pipeline from the first milestone.
- Build per-chunk walkability per mobility class on the main thread under a tick budget.
- Reclassify only dirty regions, after a settle delay.
- Precompute per-cell costs once.
- Run A* on a worker pool over immutable snapshots.
- Deliver results on the main thread, guarded by request sequence numbers.
- Use backpressure plus fail-fast budgets for unreachable goals.
Add shared fields or leader paths for group moves, and a coarse hierarchical layer for cross-map orders; no studied mod has one. Instrument it from day one: compute time, end-to-end latency and queue depth in an F7-style overlay.
5. 5. Design interest management (who sees what) before networking.
- Keep a per-team vision grid on a fixed cadence (RoN uses 10 ticks).
- Gate Strategos entities by overriding Entity#broadcastToPlayer, so vanilla's own unpair branch runs. Never cancel ChunkMap$TrackedEntity#updatePlayer at its head as RoN does; that skips unpairing and leaks entities that walk into fog.
- Vanilla already re-evaluates pairing per viewer on accepted movement packets and on section changes. Still force a re-evaluation when vision changes, through the public ServerChunkCache#move(ServerPlayer) or an access-transformed updatePlayer.
- Gate chunk and column updates.
- Send every mod packet through one sender that knows about visibility, never to all players by default.
- Render fog with biome or block tint for renderer compatibility.
- Snapshot the map at match start for rejoining players.
6. 6. Keep simulation data separate from entities. Hold units, buildings, players and teams as UUID-keyed state scoped to the server instance (SavedData or attachments), with entities as avatars that can respawn, as MineColonies' CitizenData does. No static global mutable state; RoN's world-switch bugs (research and cheats carrying over, fixed in 1.4.0) are attributed to it. This also lets most logic be unit-tested without bootstrapping Minecraft.
7. 7. Define content as data: units, buildings, technologies, civilizations and ages in datapack JSON with codecs. Register unit stats as Attributes, as RoN does. Mark them setSyncable(true) where clients need the values, because vanilla attributes are not synced by default. Apply technologies as data-defined AttributeModifiers instead of hard-coded research classes or scattered hasResearch checks.
8. 8. Model orders as typed command objects in a registry, not a giant enum. Validate them on the server by UUID ownership, with optional client prediction, shift queues, stances and aggro states. Include a formation catalogue (Recruits' set plus RoN's slot pairing and drag-to-face) and client-side control groups. Copy Legends' fix: show the exact destination and formation footprint before the order is confirmed.
9. 9. Construction: use vanilla structure templates from datapacks, with:
- per-level upgrade plans;
- phased progressive placement (solid, weak solid, water, non-solid, decorate, entities);
- per-block placement handlers for stateful and multi-block blocks;
- the AoE2 builder-count formula;
- health proportional to blocks placed.
10. 10. Plan the AI opponent as a first-class player that uses the same command API as humans, with telegraphed attacks (MineColonies' campfire camps, Legends' attack timers) and varied strategies. No prior mod solves this, so prototype it early, as soon as the economy, production and combat loops exist.
11. 11. Schedule agent AI the MineColonies way: state machines with per-transition tick rates and staggered start offsets, an escalating ladder for stuck units, and abstract travel for off-screen logistics such as trade carts. Choose the lag policy deliberately. MineColonies divides each interval by a TPS factor of up to 5, so AI keeps its real-time pace under lag but costs more per tick; it does not throttle. An RTS with hundreds of units probably wants the opposite: shed non-critical AI first. Use NeoForge TicketController chunk tickets for armies and AI bases that must keep simulating away from players. Register the controller in RegisterTicketControllersEvent. Tickets are radius-2 region tickets, so entities in the chunk tick; ticking=true adds random ticks and spawning.
12. 12. Set the testing bar higher than the prior art from the start. Take RTS Building's NeoForge 1.21.1 setup as a pattern, not as files to copy, because of its custom User Terms: ModDevGradle (the MDK's 2.0.148) with JUnit 5 and Mockito for pure logic, contract and performance tests, and gameTestServer runs for in-world behaviour. Add pathfinding and fog benchmarks with fixed seeds and unit counts (for example 200, 500 and 1,000 units) as regression gates. Add tests for the RoN-class defects found in this review: cache eviction during prewarm, and entities staying paired after they leave vision.
13. 13. Watch Reign of Nether for ideas, since it releases about weekly (6 releases and 5 betas between 2026-09-26 and 2026-10-14) and its changelogs document real RTS pain points. Do not plan around a 1.21.1 port or contributing upstream: there is no port plan, and contributions require signing a GPL project's CLA.

## Fact-check verdicts

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | Reign of Nether's latest release is 1.5.0b (2026-10-14), for Forge 1.20.1 only (1.19.x for mod versions up to 1.1.3). It is GPL-3.0-only and contributors must sign a CLA. On 2026-09-11 the maintainer said there are no plans for a 1.21.1 version. | Correct. Small detail: 1.1.3a also had a 1.20.1 beta (2025-03-26) before 1.1.4 moved to 1.20.1 for good. | Modrinth API https://api.modrinth.com/v2/project/reign-of-nether-rts, re-fetched 2026-10-20 23:21 EEST: license GPL-3.0-only, loaders [forge], game_versions 1.19.1/1.19.2/1.20.1, 1.5.0b published 2026-10-14T13:40Z, 421,317 downloads, 845 followers. https://github.com/SoLegendary/reignofnether/issues/485 was closed 2026-09-11 with the maintainer's reply 'I have no current plans to do this'. CLA-assistant comment on https://github.com/SoLegendary/reignofnether/pull/299. build.gradle:149 forge 1.20.1-47.4.0 (commit 3943983). |
| confirmed | RoN's camera is an orthographic projection from a mixin on GameRenderer#getProjectionMatrix. The server puts the player in spectator mode and flies the body as the camera, and a dummy container screen frees the cursor. i113w's Camera Lib uses the same projection technique under MIT on NeoForge 1.21.1. | Correct. Detail: Camera Lib frees the cursor differently. It calls MouseHandler#releaseMouse() every client tick, hides the OS cursor with GLFW and draws its own. It uses a client-only camera entity (Minecraft#setCameraEntity) instead of a server-side body. | RoN (1.20.1-dev @3943983): mixin/OrthoViewMixin.java:16-28 (HEAD inject on getProjectionMatrix), mixin/CameraMixin.java:52 (pDistanceOffset -= 20), guiscreen/TopdownGui.java:15 (extends AbstractContainerScreen), player/PlayerServerEvents.java:884 (setGameMode SPECTATOR). Camera Lib (main @7d3ad1a): mixin/GameRendererMixin.java:16 (getProjectionMatrix(D) HEAD), input/RTSInputHandler.java:88 (releaseMouse), client/NativeCursorController.java:14 (GLFW_CURSOR_HIDDEN), camera/RTSCameraController.java:118 (setCameraEntity), entity/RTSCameraEntity throws if spawned on the server; LICENSE is MIT. |
| confirmed | With an orthographic projection, the vanilla frustum offset loop (LevelRenderer#offsetFrustum calling Frustum#offsetToFullyIncludeCameraCube(8)) must be bypassed or the game freezes. RoN and Camera Lib each found and patched this independently. | Correct. That the two projects found it independently is an inference. | <scratchpad>/mc-src-1.21.1/net/minecraft/client/renderer/culling/Frustum.java:33-58 (loops until intersectAab == -2, stepping the camera back 4 x viewVector each pass); .../client/renderer/LevelRenderer.java:865 and 874-875. RoN mixin/FrustumMixin.java:22-33 (comment: 'the game freezes and gets stuck inside this function forever'). Camera Lib mixin/LevelRendererFrustumMixin.java cancels offsetFrustum when orthographic. |
| corrected | RoN's rtsPathfinding builds per-chunk walkability on the main thread under a budget (4 cold chunks per tick) and runs 3D grid A* on a worker pool over immutable snapshots. Defaults: cores/2 threads, 250k nodes and radius 96 per segment, up to 10 chained segments, backpressure at 500. It turns on only when the world border is at most 1,280 blocks; the whole map's navmesh is built at server start (and the 72x72 cache never evicts). | The numbers are right: 4 cold chunks/tick (gamerule pathfindingChunkBuildsPerTick, clamped 1-64), cores/2 workers (gamerule pathfindingThreads, cap 32), 250,000 nodes, radius 96, 10 segments, backpressure 500, threshold 1,280. The claim that the cache never evicts is wrong. MAX_CACHED_CHUNKS = 5,184 (72x72) was sized, by its own comment, for borders up to 1,024 blocks (64x64 chunks plus margin). The threshold is now 1,280 blocks, and the prewarm loop walks about 81x81 = 6,561 chunks, so the LRU drops about 1,400 of them during prewarm (inferred from the code, not tested at runtime). Two more points. The gamerule can still force rtsPathfinding on any map. The 1.3.8 changelog first used a 1,024-block limit. | https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/PathfinderConfig.java#L65-L68 ('sized to hold a whole RTS map (world border <= 1024 blocks = 64x64 chunks) ... 72*72'), plus L9, L13, L17, L18, L20, L30, L76. worldborder/WorldBorderServerEvents.java:26 (RTS_OPTIMIZED_BORDER = 1280) and :75-114 (prewarm over every chunk inside the border). unit/pathfinding/WalkabilityGrid.java:88 (removeLast while size > MAX_CACHED_CHUNKS). PathfinderWorkerPool.java:39 and :174. Modrinth 1.3.8 changelog: 'auto-enabled on maps with a world border <=1024 blocks ... can be toggled in any map using gamerule rtsPathfinding'. |
| confirmed | RoN PR #445 (2026-06-27) found that about 95% of A* time went to a wall-avoidance cost recomputed per cell, per unit, per search. Precomputing it per cached chunk, plus fixing re-path loops and unreachable targets, made pathfinding about 10x faster ('200 workers mining... held 66 TPS'). | Correct. The PR was created 2026-06-27 and merged 2026-06-28. The 66 TPS figure is still ambiguous. | https://github.com/SoLegendary/reignofnether/pull/445 (author S0ly). It lists 4 causes, says the cost was '~95% of A*'s time', 'improved RTS pathfinding speed by ~10x' and 'Tested with 200 workers mining on a server : held 66 TPS'. |
| confirmed | RoN's fog of war (1.4.0, 2026-08-04) is server-authoritative and block-granular. Vision is recomputed every 10 ticks; mixins gate chunk packets and entity pairing; clients render fog through biome tint. But 135 call sites still broadcast to all players, and cancelling updatePlayer at its start probably leaves entities that walk into fog still paired. | Correct, with sharper reasoning. Chunk gating is split: ChunkMapMixin filters ChunkMap#getPlayers for dark chunks, and ChunkHolderMixin filters edge chunks per column. The leak still follows, but not because vanilla rarely re-checks. Vanilla re-checks often (ChunkMap.move on every accepted movement packet). RoN's cancel at the head of updatePlayer skips vanilla's removePairing branch on every one of those checks, and RoN has no other unpair path. This still needs a runtime test. | RoN mixin/fogofwar/TrackedEntityMixin.java:20-27; mixin/fogofwar/ChunkMapMixin.java:20-45; fogofwar/FogOfWarServerEvents.java:48-57 and :102 (UPDATE_TICKS = 10). A grep finds no removePairing, removePlayer or ClientboundRemoveEntitiesPacket in RoN. grep -c 'PacketDistributor.ALL' = 135. Modrinth 1.4.0 changelog ('Fog now is block-granular'). PR #422 merged 2026-07-27. Decompiled ChunkMap.java:1024-1031 and :1327-1345. |
| confirmed | RoN has no AI opponent. RTSPlayer bots (id < 0) are created only by the tutorial; PvE is scripted. None of the mods read has an economic RTS AI. | Correct as of 1.5.0b, source HEAD 2026-10-15. | player/RTSPlayer.java:141-143 (isBot: id < 0). tutorial/TutorialServerEvents.java:141 is the only caller of PlayerServerEvents.startRTSBot (:596). gamemode/GameMode.java lists CLASSIC, SURVIVAL (waves), SANDBOX, SCENARIO. No AI-opponent code or changelog entry through 1.5.0b. |
| confirmed | RoN's default population cap is 150 (gamerule maxPopulation). Units are 54 classes, each subclassing a vanilla mob, with identity by player name and a 138-value UnitAction enum. | Correct. The 54 concrete unit classes are 52 direct implementers (not counting AbstractTotem) plus GrizzlyBearUnit and TemporaryMilitiaUnit. | resources/ResourceCosts.java:215 (DEFAULT_MAX_POPULATION = 150). registrars/GameRuleRegistrar.java:44 (maxPopulation). Enum count of unit/UnitAction.java = 138. Owner is a String in SynchedEntityData (e.g. units/villagers/VindicatorUnit.java:124). |
| confirmed | RoN buildings are vanilla structure NBT templates placed block by block; builder speed follows AoE2 (3*base/(builders+2)); health is proportional to blocks placed; resources are real world blocks backed by a per-chunk spatial index. | Correct. The default minimum block fraction is 0.5. | There are 67 .nbt files in src/main/resources/data/reignofnether/structures. building/BuildingPlacement.java:1018-1024 (AoE 2 comment and formula) and :147 (minBlocksPercent = 0.5f). resources/ResourceIndex.java:30-31 (2,048 chunks LRU, 8 scans/tick). ResourceSources has 41 entries. |
| confirmed | MineColonies has a NeoForge 1.21.1 release (1.1.1403-1.21.1, 2026-10-04), GPL-3.0 with CLA. Pathfinding uses one worker thread with a 10,000-job queue and reads live LevelChunks off-thread; 8,000-node budget, 900-block guard. | Correct. In issue #10692 (1.20.1-1.1.814, 2025-03) the guard was still 500 blocks. Guards and couriers hit it in a colony more than 40 chunks across. It is now 900. | cfwidget https://api.cfwidget.com/minecraft/mc-mods/minecolonies (minecolonies-1.1.1403-1.21.1.jar, release, 2026-10-04, NeoForge 1.21.1; 89,499,778 downloads). Repo version/1.21 @dae860e has a GPL-3.0 LICENSE and CLA.md. core/entity/pathfinding/Pathfinding.java:17 and :50; pathjobs/AbstractPathJob.java:57; world/ChunkCache.java:46 and :81; navigation/MinecoloniesAdvancedPathNavigate.java:310. https://github.com/ldtteam/minecolonies/issues/10692 is titled '500 block max pathing...'. |
| corrected | MineColonies architecture: state machines with per-transition tick rates scaled by TPS; CitizenData separate from the entity with respawn and abstract travel; per-level blueprints placed in phases (CLEAR, BUILD_SOLID, WEAK_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN); research from datapack JSON. | Mostly right, but the TPS scaling works the other way from what the report says. slownessFactor = clamp(lastTickMs/50, 1, 5), and each transition's next delay is tickRate / slownessFactor. Under lag, transitions therefore fire after fewer ticks so they keep their real-time pace. That is lag compensation, not throttling, and it adds per-tick AI work when the server is slow. The saving comes from per-transition intervals plus staggered start offsets (tickOffsetVariant). Everything else is correct, including the phase order. | api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java:19 and :118-124; TickingTransition.java:53 and :118-127; core/event/EventHandler.java:752-761. core/entity/ai/workers/AbstractEntityAIStructure.java:720 (stage array). core/colony/CitizenData.java:935-960. core/colony/managers/TravellingManager.java. core/datalistener/ResearchListener.java:155-157 (directory 'researches'). |
| confirmed | Villager Recruits (1.15.2, 2026-06-29), Villager Workers, Siege Weapons and Small Ships are All Rights Reserved; all Forge 1.20.1 only except Small Ships, which has NeoForge/Fabric 1.21.1 builds (2.0.2, 2026-10-16). | Correct. Details: Small Ships also has an old 2.0.0-b2.1 beta for 1.21.4 (2025-05-16), and Siege Weapons 0.2.5 is a beta. The Recruits repo has no LICENSE file even though Modrinth links one; the README states ARR. | Modrinth API, 2026-10-20: villager-recruits LicenseRef-All-Rights-Reserved, 1.15.2 2026-06-29, forge 1.20.1; villager-workers ARR, 2.0.3 2026-07-01; siegeweapons ARR, 0.2.5 beta 2026-04-10; small-ships ARR, 2.0.2-neoforge-1.21.1 and 2.0.2-fabric-1.21.1 2026-10-16. Recruits README.md:6 'All Rights Reserved unless otherwise explicitly stated'. |
| confirmed | Recruits' command model: R command screen, targets from player.pick(100-200), formations (line, square, triangle, circle, hollow circle, hollow square, V; spacing 1.75), UUID groups, factions/diplomacy on scoreboard teams, leaders that force-load chunks. | Correct. | Recruits main @44f60bd: init/ModShortcuts.java:17-19 (R, U, M); CommandEvents.java:70, :135, :350, :427 (pick 200/100); util/FormationUtils.java:18 (spacing 1.75) and the formation methods; world/RecruitsDiplomacyManager.java:68-71; world/RecruitsGroup.java:42 (UUID); entities/AbstractChunkLoaderEntity.java:94 (ForgeChunkManager.forceChunk); AbstractLeaderEntity extends AbstractChunkLoaderEntity. |
| corrected | Recruits async pathfinding is 'off by default thread-wise (UseAsyncPathfinding, 1 thread)', taken from the archived GPL-3.0 Petal fork. | UseAsyncPathfinding defaults to true. AsyncPathfindingThreadsCount defaults to 1. The Petal origin is confirmed: Bloom-host/Petal is archived and GPL-3.0. | config/RecruitsServerConfig.java:794-802 (define('UseAsyncPathfinding', true)) and :804-811 (threads default 1); pathfinding/AsyncGroundPathNavigation.java:19 and :26 ('petal start'/'petal end'); GitHub Bloom-host/Petal: archived, license GPL-3.0. |
| confirmed | Millénaire 9.0.2 (2026-09-18) runs on NeoForge 1.21.1 under a custom license; Java source not public; GitLab holds only content; maintained by kinniken and FireController1847. | Correct. | cfwidget millenaire: millenaire-9.0.2.jar release 2026-09-18 NeoForge 1.21.1; 9.0.0-beta.2 2026-07-25. The CurseForge page shows a Custom License, owner kinniken and maintainer FireController1847. GitLab project Millenaire/Public describes itself as 'the content part of Millénaire'. |
| confirmed | Ancient Warfare 2 (GPL-3.0-or-later) is dead since 2.7.0.1038 for 1.12.2 (2021-04-29); command baton and order items are design ideas only. | Correct. | cfwidget: ancientwarfare-1.12.2-2.7.0.1038.jar uploaded 2021-04-29. GitHub P3pp3rF1y/AncientWarfare2: default branch 1.12.x, pushed 2021-11-12, GPL-3.0. NpcCommand.java:20-26 (TODO comment; MOVE/ATTACK/ATTACK_AREA/GUARD/SET_HOME/SET_UPKEEP). Issues #1365 and #1410 are still open. |
| confirmed | Baritone (LGPL-3.0) has 1.21.1 builds (v1.11.3) for Forge, Fabric and NeoForge, but plans for one 1x2 agent and says 2x3 (horses) may never be supported. Automatone stops at MC 1.20 and was last pushed 2024-01-21. | Correct. v1.11.3 was published 2026-09-01. | https://github.com/cabaletta/baritone/releases/tag/v1.11.3: 'For Minecraft 1.21 and 1.21.1. Forge is supported. Fabric is supported. NeoForge is supported.' FEATURES.md lists under 'Things it may not ever have': 'Horses (2x3 path instead of 1x2)'. Ladysnake/Automatone has branches 1.17-1.20 only, pushed 2024-01-21, LGPL-3.0. |
| confirmed | i113w's Camera Lib (MIT, NeoForge 1.21.1 from 21.1.209, client-only, about 1.6k lines, v0.0.x) provides an RTS camera entity, optional orthographic projection, inverse-MVP ray casting and box-select events; Better Mine Team (MIT) builds RTS command on top. | Correct: 25 files, 1,648 lines. Its newest 1.21.1 build is 0.0.3-alpha3 (2026-06-09). The 2026-09-05 upload is the 1.20.1 build. The repo has 0 stars and the mod has 559 downloads. Better Mine Team is a 'reforged' MineTeam (MIT, (c) XiaoHuNao), and its README says it was developed with AI assistance. | Camera Lib gradle.properties: neo_version=21.1.209, mod_license=MIT. math/MouseRayCaster.java:60-62 (inverts projection x model-view). api/event/RTSBoxSelectEvent and RTSRightClickEvent. Modrinth i113ws-camera-lib: client required, server unsupported; versions 0.0.3-alpha3 2026-06-09 (1.21.1) and 1.20.1-0.0.4 2026-09-05. Better Mine Team neo-1.21.1-dev2 README: RTS mode with formations and Attack Move; it depends on Camera Lib via curse.maven. |
| corrected | In vanilla 1.21.1, entity tracking pairs through Entity#broadcastToPlayer(ServerPlayer) and re-evaluates only on chunk-section changes; PlayerEvent.StartTracking is not cancellable; gate own entities by overriding broadcastToPlayer and force re-tracking when vision changes. | 'Only on chunk-section changes' is wrong. ChunkMap.tick() re-checks on section changes. ChunkMap.move(ServerPlayer), which is public through ServerChunkCache#move, also calls updatePlayer(player) for every tracked entity. It runs on every accepted movement packet and every tick for a player spectating another entity. Clients send a position packet at least every 20 ticks while their camera entity is the player. The broadcastToPlayer gate and the non-cancellable StartTracking are confirmed. Forcing a re-check on vision change is still advisable, and ServerChunkCache#move(player) does it without an accessor. A client whose camera entity is not the player sends no movement packets at all. | <scratchpad>/mc-src-1.21.1/net/minecraft/server/level/ChunkMap.java:1024-1031 (move), :1151-1182 (tick), :1327-1345 (updatePlayer). .../server/level/ServerChunkCache.java:464-468. .../server/network/ServerGamePacketListenerImpl.java:460, :891, :970. .../server/level/ServerPlayer.java:524-528. .../client/player/LocalPlayer.java:283-284 and :700-701. .../world/entity/Entity.java:3021. .../net/neoforged/neoforge/event/entity/player/PlayerEvent.java:261 (StartTracking does not implement ICancellableEvent). |
| confirmed | Vanilla 1.21.1 PathNavigation runs synchronously on the server thread with a region of follow range plus 8/16 and a node budget of floor(FOLLOW_RANGE*16) times a multiplier. | Correct. The budget is computed once, when the navigation is constructed (Mob default FOLLOW_RANGE is 16). | mc-src-1.21.1 PathNavigation.java:65 (budget computed in the constructor), :130, :135, :143 (offsets 8 and 16), :155-180 (createPath builds a PathNavigationRegion and calls findPath synchronously); PathFinder.java:70; Mob.java:160 (FOLLOW_RANGE 16). |
| corrected | Minecraft Legends scored 65 on Metacritic (PC, 33 critics, user 5.4) and 70 on OpenCritic with 41% recommending; reviewers criticised avatar-radius and group-only commands, ramp falls, repetitive bases, controller UI; the August 2023 patch focused on path previews and ledges; content ended January 2024, the November 2023 update being the last major one. | The PC Metascore of 65 is right, but metacritic.com now shows it based on 36 critic reviews. The legacy page with 33 reviews and a 5.4 user score returned HTTP 403, so those figures are unverified. The current page shows a user score of 5.7 from 249 ratings, on the Xbox Series X page. OpenCritic 70 and 41% are confirmed, as are all reviewer quotes and the patch notes. Mojang ended development on 2024-01-10, with a final Lost Legend, 'Snow vs Snouts', later that month. The cited Nintendo Life article does not say the November 2023 update was the last major one, so that is unverified. | https://www.metacritic.com/game/minecraft-legends/ (PC 65 'Based on 36 Critic Reviews'); legacy.prod.metacritic.com returned 403. https://opencritic.com/game/14349/minecraft-legends (Top Critic Average 70, 41%). https://www.nintendolife.com/reviews/nintendo-switch/minecraft-legends (4/10, 2023-04-25). https://kotaku.com/minecraft-legends-review-xbox-game-pass-mojang-1850334390 (2023-04-14). https://nintendoeverything.com/minecraft-legends-update-out-now-version-1-17-50310-patch-notes/ (2023-08-16). https://www.nintendolife.com/news/2024/01/minecraft-legends-development-ends-as-final-update-launches (2024-01-10, Snow vs Snouts). |
| confirmed | FrostedHeart (GPL-3.0, Forge 1.20.1) is the only flow-field precedent: a 64x64 heightmap reverse-BFS field per target chunk, built off-thread, shared, rebuilt every 2,400 ticks; none of the studied mods uses hierarchical (HPA*/portal) pathfinding. | Correct. Detail: a comment in RoN's GridNeighbors.java:3 mentions 'grid A* and flow fields', but 1.20.1-dev has no flow-field implementation. | FrostedHeart content/town/citizen/nav/FlowFieldCache.java:30-56 (MAX_NEW_PER_TICK = 2, REFRESH_INTERVAL = 2400, BFS on a worker thread); FlowField.java:43 (SIZE = 64) and :106 (abs(height step) > 1 blocks). gradle.properties: minecraft_version 1.20.1, forge 47.3.0. A grep for hierarch/HPA/portal graph/region graph in RoN, MineColonies, Recruits and RTS Building pathfinding finds nothing. |
| confirmed | Automated testing is rare: RoN has one test class and MineColonies version/1.21 has no src/test; RTS Building (LGPL-3.0, NeoForge 21.1.238, ModDevGradle 2.0.140) has about 306 JUnit 5/Mockito unit, contract and performance test files plus GameTest server runs. | Correct. | RoN src/test contains only util/MiscUtilTest.java. MineColonies version/1.21 src has only datagen and main. RTS Building @8110237 has 306 .java files under src/test/{unit,contract,performance}; build.gradle:4 (net.neoforged.moddev 2.0.140), :77-86 (gameTestServer), :166-168 (JUnit 5.11.0, Mockito 5.12.0); gradle.properties neo_version=21.1.238. |
| confirmed | Guard Villagers' code is MIT, but its assets are All Rights Reserved and may not be used for 'any ai-directed version ports'. | Correct. The ARR clause covers everything under /src/main/resources/. | https://github.com/seymourimadeit/guardvillagers/blob/main/LICENSE: '/src/main/resources/ ... All Rights Reserved ... These assets may not be used for any ai-directed version ports'; all other files MIT. Modrinth: LicenseRef-Custom; 2.4.12 for NeoForge 1.21.1 on 2026-08-30; 5.0.0 for 26.3 on 2026-10-05. |
| corrected | RoN cadence: '11 releases between 2026-09-26 (1.4.4e) and 2026-10-14 (1.5.0b)'. | There were 11 Modrinth uploads in that window: 6 releases and 5 betas (1.5.0-beta-3, -4, -5, -7, -8). | Modrinth versions API for reign-of-nether-rts, filtered to date_published >= 2026-09-26: 11 entries, 6 with version_type 'release'. |
| corrected | RoN stats are registered Attributes, so 'the values sync to the client automatically'. | They reach clients only because RoN calls setSyncable(true) on each of its 21 attributes. Vanilla attributes are not synced by default. | registrars/AttributeRegistrar.java (21 x '.setSyncable(true)', e.g. :17 and :23); mc-src-1.21.1/net/minecraft/world/entity/ai/attributes/Attribute.java:26 and :53 (private boolean syncable, set only by setSyncable). |
| corrected | RoN: 'With vanilla pathfinding, group moves are spread over ticks, 5 units per tick'; control groups are 'kept client-side as entity IDs'; the server accepts packets when 'the sender is sandbox'. | The 5-per-tick spread applies only on the server, only when rtsPathfinding is off, and only to groups of more than 20 units. Control groups store entity IDs and building origin positions. The sandbox exception checks whether ownerName belongs to a sandbox player; it does not check the sender. | unit/UnitServerEvents.java:132 (FORMATION_DISPATCH_PER_TICK = 5); unit/UnitActionItem.java:482 (isClientSide \|\| rtsPathfinding \|\| filtered.size() <= 20 dispatches at once); hud/ControlGroup.java:44-45 (entityIds, buildingBps); unit/packets/UnitActionServerboundPacket.java:70-72. |
| corrected | RTS Building is 'LGPL-3.0-only code, ARR assets'. | The repo also ships License.md, 'RTS Building User Terms 1.0'. Its §2.4 says that, unless specifically permitted, a project using code or resources from RTS Building 'must be open source'. It also bans charging for distribution. This goes beyond LGPL, so treat RTS Building as ideas-only, including its build and test files. | https://github.com/Hcrab/RTSbuilding/blob/main/License.md lines 107-120; LICENSE (LGPL v3); LICENSE-ASSETS and ASSET-LICENSES.md (original textures/sounds ARR); Modrinth license LGPL-3.0-only. |
| corrected | Ages of Dominion license 'not checked'. | It is All Rights Reserved, per its CurseForge description. It is a CurseForge ModJam 2026 entry for NeoForge 26.1.2, single-player only, with AoE-like Dark/Feudal/Castle ages. | cfwidget https://api.cfwidget.com/minecraft/mc-mods/ages-of-dominion: 'License Notice: For now, this project is All Rights Reserved'; 'should be treated as single-player only'; 'created for the CurseForge ModJam 2026'; v4.2.0 2026-09-09. |
| corrected | SmartBrainLib: '1.21.1 NeoForge (1.16.11, 2025-10-01)'. | 1.16.11 (2025-10-01) exists for 1.21.1 on NeoForge, Forge and Fabric. MPL-2.0 is confirmed. | Modrinth smartbrainlib versions: 1.16.11 for 1.21.1 published 2025-10-01 for neoforge, fabric and forge; newest overall is 2.0.3 on 2026-10-05; license MPL-2.0. |
| corrected | Structurize latest release '1.0.835-1.21.1-snapshot, 2026-09-30'. | That is a snapshot (beta). The last stable 1.21.1 release is 1.0.832-1.21.1 (2026-08-09). MineColonies version/1.21 builds against structurize 1.0.832-1.21.1-snapshot and blockUI 1.0.199-1.21.1-snapshot. | cfwidget structurize: 1.0.835-1.21.1-snapshot (beta, 2026-09-30), 1.0.832-1.21.1 (release, 2026-08-09). MineColonies gradle.properties: forgeVersion=21.1.80, structurize_version, blockUI_version. |
| corrected | MineColonies ships 'snapshots several times a week'. | That held through September 2026, with 2-6 uploads a week. CurseForge shows no uploads since 2026-10-06, two weeks before the check date. The version/1.21 branch head is also 2026-10-06. | cfwidget minecolonies file list grouped by ISO week (weeks 36-40: 2-9 uploads per week across 1.20.1/1.21.1; last upload 2026-10-06T07:23Z); clone of version/1.21 HEAD dae860e dated 2026-10-06. |
| corrected | Tale of Kingdoms: 'LGPL-3.0, Fabric, 1.21.1 alpha, 2024-09'. | Modrinth lists 1.0.6 for Fabric 1.21.1 (2024-09-04) as a release, not an alpha. LGPL-3.0-only is confirmed. | Modrinth tale-of-kingdoms-a-new-conquest versions: 1.0.6, release, 1.21.1, fabric, 2024-09-04. |
| corrected | Exec summary: the top-down camera is 'a small, solved problem on 1.21.1' and 'i113w's Camera Lib does all of this' (including 'a screen that frees the cursor'). | There is working MIT precedent for every piece, but Camera Lib is alpha (0.0.3-alpha3, 0 stars, 559 downloads). It does not use a screen; it releases the mouse each tick and hides the OS cursor. Sodium/Iris behaviour on 1.21.1 is unverified. With a client camera entity, LocalPlayer#isControlledCamera() is false, so the client stops sending movement packets; RTS Building patches this with a mixin. | Camera Lib input/RTSInputHandler.java:88; client/NativeCursorController.java:14. RTS Building mixin/LocalPlayerMixin.java:14 and :23-24. mc-src-1.21.1 LocalPlayer.java:277 and :700-701. Modrinth i113ws-camera-lib metadata. |
| corrected | MineColonies raids: EventStatus STARTING -> PREPARING -> PROGRESSING -> DONE/CANCELED; raiders camp at campfires. | The enum also has WAITING (STARTING, PREPARING, PROGRESSING, WAITING, DONE, CANCELED). Campfire camps are confirmed. | api/colony/colonyEvents/EventStatus.java:6-14; core/colony/events/raid/HordeRaidEvent.java:52 (implements IColonyCampFireRaidEvent) and :97-99 (campFires). |
| confirmed | NeoForge has ViewportEvent.ComputeCameraAngles/ComputeFov and CalculateDetachedCameraDistanceEvent but no event to replace the projection; GameRenderer#getProjectionMatrix(double) (line 980) also builds the culling frustum. | Correct. | mc-src-1.21.1 GameRenderer.java:980 and :1276 (prepareCullFrustum(... getProjectionMatrix(...))); net/neoforged/neoforge/client/event/ViewportEvent.java:254 and :327; CalculateDetachedCameraDistanceEvent.java:24; RenderLevelStageEvent only exposes the projection matrix for reading. |
| confirmed | TicketController#forceChunk(level, BlockPos\|Entity\|UUID owner, x, z, add, ticking) keeps armies and AI bases simulating away from players; ServerPlayer#setCamera/getCamera exist. | Correct. The controller must be registered in RegisterTicketControllersEvent. Tickets are radius-2 region tickets, so entities in the chunk tick. ticking=true adds random ticks and spawning. NeoForge counts these chunks when deciding whether a level with no players keeps ticking. | mc-src-1.21.1 net/neoforged/neoforge/common/world/chunk/TicketController.java:20-27, :47, :57, :67; ForcedChunkManager.java:111-121 (addRegionTicket radius 2); net/minecraft/server/level/ServerLevel.java:390; ServerChunkCache.java:367 (shouldForceTicks); ServerPlayer.java:1734 and :1738. |
| confirmed | RoN servers are told to use view-distance=25 (because the flying body drives chunk loading). | The recommendation is real: view-distance=25, spawn-monsters=false, spawn-npcs=false, spawn-protection=0, gamemode=spectator. The page gives no reason, so the link to the flying body is an inference. | Modrinth project body of reign-of-nether-rts: 'When installing on a server, it's recommended to use these server.properties settings'. |
| confirmed | RoN pathfinding history: follow range raised to 64; improvedPathfinding gamerule in 1.0.11/1.0.12 with CPU warnings; experimental rtsPathfinding in 1.3.7; Pathfinding Overhaul 1.3.8; 1.3.8e fixed delays with higher unit volumes. | Correct. Follow range is 16 by default and 64 with improved pathfinding (Unit.FOLLOW_RANGE_IMPROVED). 1.0.11 (2024-12-09) shipped the improvements and the CPU warning; 1.0.12 (2024-12-23) added the gamerule. In 1.3.7 the gamerule improvedPathfinding was renamed to rtsPathfinding. | unit/interfaces/Unit.java:111-112; Modrinth changelogs 1.0.11, 1.0.12, 1.3.7 (2026-07-03), 1.3.8 (2026-07-06), 1.3.8e (2026-07-18: 'Fixed rtsPathfinding having significant delays with higher unit volumes'). |
| confirmed | Landscape metadata for smaller mods (Easy NPC 7.16.0 MIT; MCA Reborn 7.7.36 GPL-3.0-only; Taterzens 1.16.2 MIT no 1.21.1; Human Companions 1.7.6 GPL-3.0; Custom NPCs 2022-05-15, Modrinth CC-BY-NC-4.0; Hundred Years Warfare ARR ~178k; Siege of Settlements LGPL; Boobcat GPL no source; RoN reach 421k/977k/338 stars/112 forks). | Correct. Easy NPC also ships Quilt builds. Siege of Settlements describes itself as 'a fan-made expansion for the Reign of Nether mod'. The Boobcat page claims 'flow-field smoothing and multithreaded execution'. | Modrinth API, 2026-10-20 (easy-npc, minecraft-comes-alive-reborn, taterzens, hundred-years-warfare 177,795 downloads, siege-of-settlements, reign-of-nether-boobcat-edition, customnpcs_ search hit). cfwidget human-companions and custom-npcs. GitHub repo metadata for SoLegendary/reignofnether. cfwidget RoN 977,111 downloads. |
| confirmed | Hundred Years Warfare has an RTS mode with hotkeys, formations and shift commands, counter relationships, AW2-based workers, and calls itself 'not an RTS-focused mod'; Better Mine Team adds box select, move, attack, attack-move and formations; Villager Workers 2.0 makes the worker 'only the labour' with Work Areas. | Correct. Villager Workers' Work Areas also include Kitchen, Market and Worker Home. Whether Work Areas are entities was not checked. | Modrinth bodies of hundred-years-warfare, villager-workers; Better Mine Team README (neo-1.21.1-dev2 @0a7307a). |
| confirmed | Towny is CC BY-NC-ND 3.0; Petal is GPL-3.0; Domum Ornamentum is GPL-3.0; Steve's Army is LGPL-2.1+ on 1.20.1. | Correct. Additions for §7: Commanders Vision (a Recruits addon) is MIT; RTS Map Command Recruits is ARR. | TownyAdvanced/Towny LICENSE.md; GitHub Bloom-host/Petal (GPL-3.0, archived); ldtteam/Domum-Ornamentum (GPL-3.0); Modrinth search hits: steves-army LGPL-2.1-or-later 1.20.1 (0.3.1 beta 2026-09-03), commanders-vision-x-villager-recruits-addon MIT, rts-map-command-recruits LicenseRef-All-Rights-Reserved. |
| unverifiable | A Moddex review reportedly rates RoN performance below gameplay and mentions culling-mod conflicts. | Not re-checked. The report already flags it as unverified. | No accessible primary source. |
| unverifiable | Minecraft Legends: 'the November 2023 update was the last major one'. | Not in the cited Nintendo Life article or Wikipedia. Keep it marked unverified. | https://www.nintendolife.com/news/2024/01/minecraft-legends-development-ends-as-final-update-launches (no mention of November); https://en.wikipedia.org/wiki/Minecraft_Legends. |
