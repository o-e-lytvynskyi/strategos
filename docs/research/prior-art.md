# Prior art for Strategos: RTS, army-command and colony mods, Minecraft Legends, pathfinding

Research date: 2026-10-20. Target: Minecraft Java 1.21.1, Java 21, loader not yet chosen.

*Independently fact-checked on 2026-10-20. Corrections are marked **[corrected]**, statements that could not be checked are marked **(unverified)**, and "Verification notes" at the end summarises the checks.*

**Method.** I cloned and read the source of:
- Reign of Nether (`1.20.1-dev`, 968 Java files, ~141k lines);
- MineColonies (`version/1.21`, 2,062 files, ~307k lines) and Structurize (`version/1.21`);
- Villager Recruits (`main`, 466 files, ~72k lines);
- RTS Building (`main`, ~155k lines);
- i113w's Camera Lib (`main`, 25 files, ~1.6k lines).

I read Ancient Warfare 2, MineFortress, FrostedHeart and Baritone through the GitHub API. Versions, dates and licenses come from the Modrinth API, CurseForge (via the cfwidget API), and the GitHub and GitLab APIs. Vanilla and NeoForge API claims are checked against the decompiled MC 1.21.1 + NeoForge 21.1 sources in the scratchpad. Anything not marked **[inference]** was seen in a source. Download counts are as of 2026-10-20.

---

## 1. Executive summary

- **Reign of Nether (RoN) is the only mature, actively developed, full RTS mod.**
  - It ships more often than weekly: 1.5.0b came out on 2026-10-14.
  - It runs only on **Forge 1.20.1** and is **GPL-3.0-only**, with a CLA.
  - On 2026-09-11 the maintainer wrote that there are no plans for 1.21.1 ([#485](https://github.com/SoLegendary/reignofnether/issues/485)).
  - Use it as a design reference only, not as a code base or a dependency.
- **RoN's two hardest problems were pathfinding and fog of war. Contributors rewrote both in mid-2026.**
  - Pathfinding became an off-thread grid A* over cached per-chunk walkability (July 2026).
  - Fog became server-authoritative and block-granular (1.4.0, August 2026).
  - Both are tied to bounded maps (world border ≤ 1,280 blocks). Fog is only available there, and `rtsPathfinding` turns on automatically, with a whole-map navmesh prewarm, only there. **[corrected]** The `rtsPathfinding` gamerule can still force the new pathfinder on any map, with a performance warning. Bounded match maps are what made RoN's performance manageable.
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
- **The top-down camera is a small, solved problem on 1.21.1.** The pieces:
  - orthographic projection via a mixin on `GameRenderer#getProjectionMatrix(double)`;
  - a way to free the cursor. **[corrected]** RoN opens a dummy container screen; Camera Lib calls `MouseHandler#releaseMouse` every client tick;
  - mouse picking with a ray through the inverse of projection × model-view;
  - a required bypass of the vanilla frustum-offset loop, which otherwise freezes the game.

  i113w's Camera Lib does all of this under **MIT** for NeoForge 1.21.1. **[corrected]** Its mixin plugin turns the frustum bypass off when Sodium, Embeddium or Rubidium is loaded. Orthographic rendering under those renderers is untested.
- **Pathfinding:**
  - Vanilla `PathNavigation` runs synchronously on the server thread, with a node budget of `floor(FOLLOW_RANGE × 16)` × a multiplier. The budget is fixed when the navigation is constructed.
  - Baritone (LGPL-3.0, has 1.21.1 builds) is a pathing bot for one player-sized agent. Its server-side fork, Automatone, stops at MC 1.20.1 (release 0.11.0, 2024-01-21). MineFortress maintains its own Automatone fork for 1.20.2.
  - RoN's design is the best precedent. FrostedHeart has the only flow-field code I found. None of the mods I read uses a hierarchical (portal / HPA*) layer.
- **Minecraft Legends (2023) shows what goes wrong when you command units in third person in a voxel world.**
  - Problems: orders only reach units in a small circle around the avatar, groups can't be split, units fall off ramps, previews are unclear, AI bases are samey.
  - Mojang's own fixes (August 2023) were about predictability.
  - Content development ended in January 2024.
- **Licensing:** nearly all serious prior art is GPL or ARR.
  - Code from MIT projects (Camera Lib, Better Mine Team, MineFortress, Guard Villagers' code, Easy NPC's code) can be copied with attribution without fixing Strategos's license.
  - LGPL and MPL projects can be used as separate jars.
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
| [Structurize](https://github.com/ldtteam/Structurize) / [BlockUI](https://github.com/ldtteam/BlockUI) | 1.21.1 NeoForge | Structurize 1.0.835-1.21.1-snapshot (beta), 2026-09-30. **[corrected]** The last release is 1.0.832-1.21.1, from 2026-08-09 | GPL-3.0 | GitHub | M — blueprints, UI |
| [Villager Recruits](https://modrinth.com/mod/villager-recruits) | 1.20.1 Forge | 1.15.2, 2026-06-29. **[corrected]** No commits from 2026-06-29 until they resumed on 2026-10-16 and 2026-10-19 | ARR | [GitHub](https://github.com/talhanation/recruits) (readable) | **H** — command UX |
| [Villager Workers](https://modrinth.com/mod/villager-workers) | 1.20.1 Forge | 2.0.3, 2026-07-01 | ARR | GitHub | L–M |
| [Siege Weapons](https://modrinth.com/mod/siegeweapons) | 1.20.1 Forge | 0.2.5 (beta), 2026-04-10 | ARR | GitHub | L–M |
| [Small Ships](https://modrinth.com/mod/small-ships) | 1.21.1 NeoForge/Fabric; 1.20.1. **[corrected]** It also has a 1.21.4 beta (2.0.0-b2.1, 2025-05-16) | 2.0.2, 2026-10-16 | ARR | GitHub | L |
| [Millénaire](https://www.curseforge.com/minecraft/mc-mods/millenaire) | **1.21.1 NeoForge** (9.x); 1.12.2 Forge (8.x) | 9.0.2, 2026-09-18 | Custom; Java source not public | [GitLab, content only](https://gitlab.com/Millenaire/Public). **[corrected]** No commits since 2018-07-07 | M — data-driven villages |
| [Ancient Warfare 2](https://github.com/P3pp3rF1y/AncientWarfare2) | 1.12.2 Forge | 2.7.0.1038, 2021-04-29 (dead) | GPL-3.0-or-later | GitHub | M — orders, siege |
| [Guard Villagers](https://modrinth.com/mod/guard-villagers) | 1.21.1 NeoForge (2.4.12, 2026-08-30) up to 26.3 | 2026-10-05 | MIT code / ARR assets | GitHub | L |
| [Easy NPC](https://modrinth.com/mod/easy-npc) | 1.21.1 NeoForge/Forge/Fabric | 7.16.0, 2026-10-17 | MIT. **[corrected]** Code only: the licence excludes assets | GitHub | L |
| [Human Companions](https://github.com/justinwon777/HumanCompanions) | 1.20.1 Forge | 1.7.6, 2024-12-15 | GPL-3.0 | GitHub | L |
| [Custom NPCs](https://www.curseforge.com/minecraft/mc-mods/custom-npcs) | ≤1.16.5 on CurseForge | 2022-05-15 | Closed (Modrinth listing CC-BY-NC-4.0) | — | L |
| [MCA Reborn](https://modrinth.com/mod/minecraft-comes-alive-reborn) | 1.21.1 NeoForge/Fabric. **[corrected]** Its newest target is 26.2 (8.1.11, 2026-08-29) | 7.7.36 for 1.21.1, 2026-08-16 | GPL-3.0-only | GitHub | L |
| [Taterzens](https://modrinth.com/mod/taterzens) | Fabric up to 1.21.6, no 1.21.1 build | 1.16.2, 2025-06-26 | MIT | GitHub | L |
| [MineFortress](https://github.com/remmintan/minefortress) | 1.20.1/1.20.2 Fabric | last commit 2025-08-09 | **MIT** | GitHub | M — MIT RTS colony |
| [Hundred Years Warfare](https://modrinth.com/mod/hundred-years-warfare) | 1.20.1, 1.21.1 (Fabric/Forge/NeoForge) | 2026-08-20 | ARR, no source | — | M — "RTS mode" UX |
| [Better Mine Team](https://modrinth.com/mod/bettermineteam) + [i113w Camera Lib](https://github.com/i113w/113CameraLib) | **1.21.1 NeoForge**, 1.20.1 Forge, 26.1.2 | 2026-09-05 | **MIT** | GitHub. **[corrected]** Camera Lib's GitHub has only the 1.21.1 `main` source; Better Mine Team's RTS code is on branch `neo-1.21.1-dev2` | **H** — camera reference |
| [RTS Building](https://modrinth.com/mod/rts-building-build-from-above) | **1.21.1 NeoForge** (+1.20.x, 26.1.2). **[corrected]** It also has a Fabric 1.21.1 alpha and a 1.12.2 Forge port | 2026-10-06 (repo 2026-10-19) | LGPL-3.0-only code, ARR assets. **[corrected]** The repo also still carries an older, more restrictive "User Terms 1.0" | [GitHub](https://github.com/Hcrab/RTSbuilding) | M — camera, tests |
| [Siege of Settlements](https://modrinth.com/mod/siege-of-settlements), [Boobcat Edition](https://modrinth.com/mod/reign-of-nether-boobcat-edition) | 1.20.1 Forge | 2026-09-17 / 2025-10-06 | RoN derivatives with questionable labels | partial | L |
| [Ages of Dominion](https://www.curseforge.com/minecraft/mc-mods/ages-of-dominion) | 26.1.2 NeoForge (ModJam 2026) | 4.2.0, 2026-09-09 | **[corrected]** Custom licence, currently ARR; no source | — | L |
| [Baritone](https://github.com/cabaletta/baritone) / [Automatone](https://github.com/Ladysnake/Automatone) | Baritone: 1.21.1 builds v1.11.3; Automatone: Fabric up to 1.20.1 | Automatone last push 2024-01-21 | LGPL-3.0 | GitHub | M — algorithms |
| [FrostedHeart](https://github.com/TeamMoegMC/FrostedHeart) | 1.20.1 Forge | 2026 | GPL-3.0 | GitHub | M — flow fields |
| [SmartBrainLib](https://modrinth.com/mod/smartbrainlib) | 1.21.1 NeoForge (1.16.11, 2025-10-01) | 2026-10-05 | MPL-2.0 | GitHub | M — AI library option |
| Minecraft Legends | Mojang/Blackbird, released 2023-04-18 | content ended Jan 2024 | proprietary | — | **H** — UX lessons |

---

## 3. Reign of Nether (SoLegendary)

### 3.1 Status and maintenance

- **Builds:** Forge 1.19.1/1.19.2 for mod ≤ 1.1.3, Forge 1.20.1 from 1.1.4. Toolchain: ForgeGradle 5.1, Forge `1.20.1-47.4.0`, Parchment `2023.09.03-1.20.1`, Mixin, and the Essential partner SDK for peer-to-peer hosting.
- **Cadence:** **[corrected]** 11 Modrinth versions between 2026-09-26 (1.4.4e) and 2026-10-14 (1.5.0b): 6 releases and 5 betas. The README roadmap (updated 2026-09-23) lists hero items, villager-faction reworks, more heroes and units, and scenarios.
- **Reach:** Modrinth about 421k downloads and 845 followers; CurseForge about 977k downloads; GitHub 338 stars and 112 forks.
- **Contributors:** major work now comes from contributors. S0ly did the pathfinding and fog rewrites; dmzz-yyhyy did performance work.
- **Port requests:** [#243](https://github.com/SoLegendary/reignofnether/issues/243) (NeoForge/1.20.4+) has been open since 2025-04. [#485](https://github.com/SoLegendary/reignofnether/issues/485) (1.21.1) got the answer "no current plans". [PR #299](https://github.com/SoLegendary/reignofnether/pull/299) ("Fabric base for 1.21.8") was only an empty skeleton.
- **Forks:** Siege of Settlements is labelled LGPL although it derives from GPL code (its repo's own LICENSE.txt is GPL-3.0). The Boobcat Edition has a GPL listing but no source link. Don't take code from forks.

### 3.2 Camera and cursor

All files are under `src/main/java/com/solegendary/reignofnether/`.

- **Toggle and body:** F12 toggles the RTS camera. The server switches the player to spectator ([PlayerServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java)). The body flies at about average terrain height + 30, up to + 100. Chunk loading and entity tracking therefore follow the camera "for free", which is why servers are told to use `view-distance=25`.
- **Projection:** [OrthoViewMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/OrthoViewMixin.java) replaces `GameRenderer#getProjectionMatrix` with an orthographic matrix. [CameraMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/CameraMixin.java) forces a detached camera pulled back 20 blocks, so terrain still renders when the body clips into a hill.
- **Camera limits** ([OrthoviewClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/orthoview/OrthoviewClientEvents.java)): zoom 10–90 (zoom × 2 = visible height in blocks), pitch −20° to −90°, default yaw 135° and pitch −45°. Also edge-pan, camera hotkeys and scripted pans.
- **Frustum freeze:** [FrustumMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/FrustumMixin.java) cancels `Frustum#offsetToFullyIncludeCameraCube`. Its comment says the game "freezes… forever" otherwise. In 1.21.1 that method loops until the camera cube is inside the frustum (verified in `Frustum.java`), and with an orthographic projection it can fail to converge. i113w's Camera Lib needed the same bypass (§7); whether it found it independently is (unverified).
- **Leaf hiding:** [RenderChunkRegionMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/RenderChunkRegionMixin.java) swaps leaves for stained glass or hides them, either around units and the cursor or everywhere. Without it, canopies hide units.
- **Free cursor:** RoN opens a dummy container screen ([TopdownGui](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/guiscreen/TopdownGui.java)) and handles all clicks and drags in `ScreenEvent` handlers. [CursorClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/cursor/CursorClientEvents.java) turns the screen position into a world ray, refines the block hit, and picks entities with its own ray–AABB tests.

### 3.3 Selection, control groups, commands

- **Selection:**
  - box select with a 3D rectangle test;
  - double-click (within 500 ms) or Ctrl-click selects all units of the same type ([UnitClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/UnitClientEvents.java));
  - 10 control groups, kept client-side as entity IDs ([HudClientEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/hud/HudClientEvents.java)).
- **Command model:**
  - Commands are one enum, [UnitAction](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/UnitAction.java), with **138 values**. It mixes generic orders (MOVE, ATTACK_MOVE, HOLD, GARRISON) with per-ability entries (e.g. `CAST_SUMMON_VEXES_AUTOCAST_ENABLE`).
  - [UnitActionServerboundPacket](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/packets/UnitActionServerboundPacket.java) carries `ownerName`, the action, unit IDs, target positions and a shift-queue flag.
- **Validation:** the server checks that the sender's **name** equals `ownerName`. **[corrected]** The exceptions are a sandbox owner (`isSandboxPlayer(ownerName)`, which tests the named owner, not the sender) and a sender with allied control over `ownerName`. It then checks each unit's owner in `UnitActionItem`.
- **Queueing:** actions go into a fast queue, or into a slow queue when shift-queued.
- **Client prediction:** the client also runs the action locally for responsiveness.
- **Formations:**
  - [UnitFormations](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/UnitFormations.java) spirals out square slots and pairs units to slots by sorted x/z.
  - `FormationDragMove` builds formations from a drag line.
  - With vanilla pathfinding, group moves are spread over ticks, 5 units per tick.

### 3.4 Units and AI

- **Inheritance:** 54 unit classes, each extending a vanilla mob (6 × Vindicator, PiglinBrute, Skeleton, Wolf…; a few extend another unit class or the generic `Monster`/`Mob`) and implementing `Unit`, `AttackerUnit` or `WorkerUnit`. The comment in [Unit.java](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/interfaces/Unit.java) calls this a "workaround" for the lack of multiple inheritance. Each unit re-declares about 100 lines of the same state (goals, cooldowns, owner, anchor, checkpoints): see [VindicatorUnit](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/units/villagers/VindicatorUnit.java). The ~100-line figure is an approximation and was not recounted.
- **Owner:** stored in `SynchedEntityData` as the player's name.
- **AI:** vanilla `GoalSelector` with custom goals: MoveToTargetBlock, SelectedTarget, GatherResources, BuildRepair, ReturnResources, Garrison and others.
- **Stats:** RTS stats are **registered Attributes**: attack range, aggro range, sight range, ranged/magic resist, attacks per second, and since 1.5.0 crit, lifesteal and similar. Upgrades and items can therefore use attribute modifiers. The values sync to the client because each attribute is registered with `setSyncable(true)`. This is worth copying.

### 3.5 Pathfinding

**History:**
- Started with vanilla navigation, follow range raised to 64.
- `improvedPathfinding` gamerule in 1.0.11/1.0.12 (December 2024), shipped with warnings about higher CPU use.
- Experimental `rtsPathfinding` in 1.3.7 (2026-07-03); "Pathfinding Overhaul" in 1.3.8 (2026-07-06). The 1.3.8 changelog auto-enabled it on borders ≤ 1,024 blocks; the code now uses ≤ 1,280.
- 1.3.8e (2026-07-18) fixed "significant delays with higher unit volumes".
- [PR #445](https://github.com/SoLegendary/reignofnether/pull/445) (2026-06-27) found four stacked causes:
  - workers re-pathing every second;
  - each worker scanning blocks for resources;
  - unreachable leaf targets burning the full ~250 ms node budget;
  - a wall-avoidance cost recomputed per cell, per unit, per search, which was "~95% of A*'s time".

  The fix made pathfinding about 10× faster. The PR says it was "tested with 200 workers mining on a server: held 66 TPS". What the TPS figure means is (unverified).

**Current architecture** (all in [`unit/pathfinding/`](https://github.com/SoLegendary/reignofnether/tree/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding)):
- [RtsPathfinder](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/RtsPathfinder.java) is the entry point. It snaps the goal to a standable cell within radius 4. If no such cell exists, the request runs on a capped budget of 8,000 nodes and approaches best-effort.
- [WalkabilityGrid](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/WalkabilityGrid.java) caches per-chunk walkability:
  - it is built **on the main thread**, because `getBlockState` and collision shapes must be read there, with a budget of 4 cold chunks per tick (gamerule, clamped 1–64);
  - the cell kinds are land, water, fire, lava and slime, plus a precomputed "crowd" wall-proximity cost;
  - only a vertical band of ±24 around the path is classified;
  - a block change marks a chunk dirty and only the changed box is reclassified, after 4 quiet ticks and at most 40 ticks later. Stale cells stay walkable in the meantime, so units are never stranded.
- [PathfinderWorkerPool](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/PathfinderWorkerPool.java) does the compute:
  - it takes an immutable snapshot and runs **3D grid A*** on a fixed pool (default: half the cores, resizable at runtime, capped at 32);
  - results are delivered back on the main thread, at most 256 per tick;
  - a validity check (`valid`) drops superseded or dead requests before they spend budget;
  - backpressure: above 500 queued requests the caller gets a "busy" signal and keeps its old path, which prevents re-submit loops.
- [PathfinderConfig](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/PathfinderConfig.java) and [MobilityClass](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/pathfinding/MobilityClass.java) set the search:
  - radius 96 and 250k nodes per segment;
  - up to 10 **chained segments** for long orders;
  - falls of up to 3 blocks;
  - wall-climbing cells for spiders;
  - mobility classes HUMANOID, AQUATIC, LARGE and FIRE_IMMUNE, plus a footprint radius for wide mobs;
  - an arrival "settle" distance of 1.5 blocks, so stopped groups don't jitter.
- The finished path is converted to a vanilla `Path`; vanilla movement control still does the walking.
- [WorldBorderServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/worldborder/WorldBorderServerEvents.java): `rtsPathfinding` turns on automatically only when the **world border is ≤ 1,280 blocks** (80 × 80 chunks). The whole map's navmesh is then built up front at server start. **[corrected]** The cache holds 72 × 72 = 5,184 chunks; a code comment says it was sized for a 1,024-block border (64 × 64 chunks plus margin). Near the 1,280-block threshold the prewarm walks about 81 × 81 = 6,561 chunks, so on borders above roughly 1,140 blocks it overflows the LRU cache and evicts chunks, despite the "never evicts" comments [inference from code]. On open worlds RoN stays on vanilla pathfinding unless the `rtsPathfinding` gamerule forces the new pathfinder on.
- **Observability:** the F7 overlay shows path compute time, end-to-end latency and queue depth. Strategos should have this from day one.
- **Not present** [inference from reading]: no flow fields, no hierarchical layer, no explicit local avoidance beyond vanilla entity push and formation slots. Comments still mention a `UnitSeparation` class that is not in the tree.

### 3.6 Buildings

- **Templates:** buildings are vanilla structure NBT templates: 67 files in `data/reignofnether/structures`, loaded in [BuildingBlockData](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/building/BuildingBlockData.java).
- **Placement:** a client ghost, then server validation in [BuildingValidators](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/building/BuildingValidators.java): terrain, overlap, nether terrain for piglins, explored-in-fog.
- **Construction:** blocks are placed one at a time, with builder speed taken from Age of Empires II: `msPerBlock = 3·base/(builders+2)`, i.e. 1 builder = 100 %, 2 = 75 %, 5 ≈ 43 % ([BuildingPlacement](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/building/BuildingPlacement.java)).
- **Health:** health is proportional to blocks placed, and a building is destroyed below a minimum block fraction (default 50 %).
- **Custom buildings:** mapmakers can save their own structures as buildings, and since 1.5.0 as shops.
- **Fixed lag:** [PR #348](https://github.com/SoLegendary/reignofnether/pull/348) replaced "BuildingPlacement scan thousands of blocks every tick" with event-driven counting.

### 3.7 Economy: resources, gathering, production, research, population

- **Resources:** food, wood and ore; emeralds became a 4th resource in 1.5.0 (2026-10-11).
- **Gathering uses real world blocks.** [ResourceSources](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/resources/ResourceSources.java) defines 41 source types (logs, ores, crops, farmland and more). Workers break the blocks with `destroyBlock` and replant farms ([GatherResourcesGoal](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/unit/goals/GatherResourcesGoal.java)). [ResourceIndex](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/resources/ResourceIndex.java) is a per-chunk spatial index, filled at 8 chunks per tick and capped at 2,048 chunks (LRU).
- **Production and population:** each building has its own production queue. Population comes from houses; the default `maxPopulation` is **150** ([ResourceCosts](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/resources/ResourceCosts.java)).
- **Research is hard-coded.** There are 52 Java classes, one per tech. What is researched is stored as (player name, id) pairs in SavedData. Effects are `playerHasResearch(...)` checks scattered through gameplay code ([ResearchServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/research/ResearchServerEvents.java)). This won't scale to 0 A.D./AoE tech trees; use data-defined modifiers instead.

### 3.8 Fog of war (1.4.0, 2026-08-04; [PR #422](https://github.com/SoLegendary/reignofnether/pull/422))

- **Scope:** block-granular, optional (`/rts-fog enable`, or gamerule `reignofnetherForceFog`), and only on maps up to 80 × 80 chunks.
- **Server side** ([FogOfWarServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/fogofwar/FogOfWarServerEvents.java)):
  - vision is recomputed **every 10 ticks** into "live" chunks, "edge" chunks and a 16 × 16 bitmask of visible columns per edge chunk;
  - [ChunkMapMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/fogofwar/ChunkMapMixin.java) drops players for whom the chunk is dark from chunk broadcasts. **[corrected]** The per-column filtering of edge chunks is done by `ChunkHolderMixin`;
  - [TrackedEntityMixin](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/mixin/fogofwar/TrackedEntityMixin.java) cancels `ChunkMap$TrackedEntity#updatePlayer` for entities whose column is outside the viewer's vision;
  - a map snapshot taken at match start is served to players who join or rejoin, so reconnecting can't be used to cheat;
  - a unit attacking from fog is revealed for a short time. This applies to ranged attackers and is a client-side render exception; whether it works end to end is (unverified).
- **Client side:** the fog is drawn through the biome-tint system, so it works under Embeddium, Oculus, OptiFine and shaders (per PR #422; not tested here). The client also cancels rendering of living entities outside visible areas.
- **Sight:** sight ranges are Attributes (default 16; heroes 24; garrisons and capitols 32).
- **Caveats** [inference from code; needs a runtime test]:
  - (a) Fog was added on top of a network layer that broadcasts: there are **135 `PacketDistributor.ALL` call sites**. Unit stat sync is fog-gated, but much else (resources, anchors, animations) goes to everyone.
  - (b) The mixin cancels `updatePlayer` at its start, and in vanilla unpairing also happens inside `updatePlayer`. So an enemy that was visible and then walks into fog stays paired for as long as it stays fogged: every later evaluation is cancelled. **[corrected]** Vanilla re-evaluates pairing more often than chunk-section changes. `ChunkMap.tick()` handles section changes, and `ChunkMap.move(ServerPlayer)` re-evaluates every tracked entity on each accepted player-movement packet, which clients send at least every 20 ticks (verified in the 1.21.1 sources). Because the client cancels rendering of fogged entities, this is a data leak to modified clients rather than a visible one.

### 3.9 Multiplayer model

- **Server:** one shared world, on a dedicated server or hosted peer-to-peer through Essential.
- **Two kinds of player:**
  - RTS commanders, in spectator mode with the top-down camera;
  - normal first-person (survival) players, who coexist with them: they fight units, drop resources at stockpiles, use portals and can ally with commanders.

  Units attack enemy survival players on their own.
- **Other systems:** alliances with allied control, coop mode, scenario mode, a start-match menu (F9). **[corrected]** The game modes in code are Classic (shown as King of the Beacon when the map has a capturable beacon), Wave Survival, Sandbox and Scenario.
- **Identity:** players are identified by **name** everywhere; a code comment says "match by name; entity ids change on reconnect".

### 3.10 AI opponents

None. `RTSPlayer.isBot()` (id < 0) exists, but bots are only created by `startRTSBot`, and the only caller is the tutorial ([PlayerServerEvents](https://github.com/SoLegendary/reignofnether/blob/1.20.1-dev/src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java), `TutorialServerEvents`). There is no decision-making AI. PvE content is scripted: waves, neutral units, scenarios.

### 3.11 Performance limits and known issues

- **Scale:** 150 population per player by default.
- **Map size:** the optimised paths (prewarmed navmesh, fog snapshot) need world border ≤ 1,280; the navmesh cache only fully holds borders up to about 1,140 blocks (§3.5). On open worlds, servers are told to use `view-distance=25` and to turn off natural spawns.
- **Fixed hot spots:**
  - re-path loops, per-worker scans, unreachable targets and per-cell cost recompute (PR #445);
  - Java streams in hot paths and per-tick block scans, plus caching selection sorting during rendering (PR #348, which claimed "at least 6%" from removing streams);
  - lag in large custom buildings (1.5.0).
- **Code-quality signals:**
  - global static mutable state in the `*ServerEvents` classes caused bugs like research and cheats carrying over between singleplayer worlds (fixed in 1.4.0). The changelog confirms the bug; the static-state cause is [inference from code];
  - identity by player name;
  - one giant action enum;
  - per-unit boilerplate;
  - one contributor wrote that "the code quality in the project was extremely low" (PR #348).
- **Tests:** one unit-test class (`MiscUtilTest`).

### 3.12 What players praise and criticise

Third-party evidence is thin; the maintainer runs support on Discord. The signals I could find:
- **Praise:** "transforms it into an entirely new experience" ([80.lv, 2025-06-27](https://80.lv/articles/check-out-this-mod-that-reimagines-minecraft-as-rts)). There is an official map pack with 21 PvP maps by community mapmakers, and the download numbers above.
- **Criticism (from the mod's own changelogs and PRs):** performance and pathfinding lag, fog labelled experimental, and problems with Embeddium/Oculus until PR #422.
- **Unverified:** a Moddex review (fetch blocked, summary only) reportedly rates performance below gameplay and mentions conflicts with culling mods.

### 3.13 Verdict

- **Borrow (as design only):**
  - a spectator body or a camera entity, plus an orthographic mixin, a cursor screen, frustum and leaf workarounds;
  - stats as Attributes;
  - vanilla NBT templates, AoE2 builder formula, health = blocks placed;
  - resources as real world blocks backed by a spatial index;
  - the whole pathfinding pipeline: budgeted main-thread caching, immutable snapshots, worker pool, request sequencing, backpressure, chained segments, mobility classes;
  - server-authoritative fog with a biome-tint renderer and a match snapshot;
  - bounded-map mode (with caches sized from the actual border), tunables as gamerules, an F7-style debug overlay;
  - a mixed RTS + FPS player model.
- **Avoid:**
  - inheriting from vanilla mob classes for every unit type;
  - identity by name;
  - one giant action enum;
  - hard-coded techs;
  - static global state;
  - broadcast-first networking;
  - fog bolted on afterwards, including cancelling entity tracking wholesale.
- **License:** GPL-3.0-only plus CLA. Clean-room only, unless Strategos itself becomes GPL-3.0.

---

## 4. MineColonies + Structurize + BlockUI (ldtteam)

**Status.**
- 1.21.1 NeoForge release 1.1.1403-1.21.1 (2026-10-04). **[corrected]** Snapshots came several times a week through September. There has been no 1.21.1 build since that release, and the last `version/1.21` commit is from 2026-10-06. 1.20.1 Forge is maintained in parallel.
- About 89.5M CurseForge downloads.
- The `version/1.21` branch builds against NeoForge 21.1.80, Structurize 1.0.832-1.21.1-snapshot and BlockUI 1.0.199-1.21.1-snapshot.
- All GPL-3.0, with CLAs. MineColonies' CONTRIBUTING also has an AI disclosure policy that rejects patches written mostly by AI.
- No `src/test` directory on `version/1.21`.

**Citizen and worker AI architecture.**
- **State machines:** worker AI is a deep class hierarchy. **[corrected]** The order is `AbstractAISkeleton → AbstractEntityAIBasic → …Skill → …Interact → …Structure → …StructureWithWorkOrder`. Each job AI registers transitions on a [TickRateStateMachine](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java).
  - Transitions come in priority tiers: AI-blocking, events, state-blocking, then the current state.
  - **Each transition has its own tick rate**, so most transitions are checked only every N ticks; that is the cheap part for hundreds of agents. **[corrected]** Lag scaling does not stretch these intervals. When the last tick took more than 50 ms, a global `slownessFactor = clamp(lastTickMs / 50, 1, 5)` *divides* each interval. Transitions are then checked more often per tick to keep their real-time cadence: this compensates for lag and does not throttle (`EventHandler.onServerTick`).
- **Data separate from the entity:**
  - Colonies live in `SavedData` ([ServerColonySaveData](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/api/colony/savedata/ServerColonySaveData.java)).
  - [CitizenData](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/CitizenData.java) owns job, home, skills and inventory. `updateEntityIfNecessary()` re-spawns the entity when it is missing.
  - [TravellingManager](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/managers/TravellingManager.java) handles trips abstractly: the entity is absent while "travelling" (used for Nether expeditions). This is a good model for AoE-style trade carts and off-screen logistics.
  - Clients get a serialized [ColonyView](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/ColonyView.java), sent only to subscribers.

**Pathfinding.**
- **Executor:** [Pathfinding.java](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/Pathfinding.java) is a `ThreadPoolExecutor(1, 1)` — **one worker thread** — with a 10,000-job queue.
- **Jobs:** [AbstractPathJob](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/pathjobs/AbstractPathJob.java) is A* with a default `MAX_NODES = 8000` (ranged jobs use min(8000, range²)), times a config multiplier of 1–4. There are subclasses for specific goals: find tree, find water, random position, move away, raider path, and others.
- **World reads:** [ChunkCache](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/world/ChunkCache.java) holds references to **live** `LevelChunk`s that are read off-thread. This is faster than copying, but racy [inference: tolerated in practice, but not safe].
- **Costs:** [PathingOptions](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/PathingOptions.java) has rich costs: jump 2, drop 1, on-path ⅙, rail ⅒, swim 4, entering water 24, doors/gates 3.
- **Stuck handling:** [PathingStuckHandler](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/navigation/PathingStuckHandler.java) escalates: repath → teleport a few nodes along the path → place ladders or leaves → break the blocks ahead → full "stuck" action (teleport to the goal or take damage).
- **Raiders:** [PathJobRaiderPathing](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/pathjobs/PathJobRaiderPathing.java), which RaidManager uses to plan raid routes, can path *through* blocks at cost 30 and place ladders.
- **Distance guard:** [MinecoloniesAdvancedPathNavigate](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java) refuses path jobs longer than **900 blocks**. It logs an error with a stack trace, teleports the entity (citizens to their home, others to the destination) and pauses navigation for 300 s. **[corrected]** In 1.20.1-1.1.814 the limit was 500 blocks. Guards and couriers on trips of about 630 blocks, in a colony more than 40 chunks across, flooded the log to over 150 MB ([#10692](https://github.com/ldtteam/minecolonies/issues/10692), 2025-03).

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

  REMOVE_WATER and REMOVE are used for deconstruction. The progress position is saved so building resumes after a restart.
- **Placement handlers:** each block type has an [IPlacementHandler](https://github.com/ldtteam/Structurize/blob/version/1.21/src/main/java/com/ldtteam/structurize/placement/handlers/placement/IPlacementHandler.java) that computes required items and handles doors, beds and multi-blocks. Iterators (default, Hilbert, inward circle, inward circle by height, random) set the build order.
- **Materials:** materials reach the builder through the colony request system, with couriers delivering from warehouses [inference: much more complex than an AoE-style resource counter needs].

**Research, UI, raids, limits.**
- **Research** is datapack JSON (`researches`, loaded by [ResearchListener](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/datalistener/ResearchListener.java)).
- **UI** is BlockUI: XML layouts plus `Window` classes (GPL).
- **Raids:**
  - Raid types: barbarian, pirate and Norsemen ships, drowned pirates, Egyptian, Amazon.
  - Each runs a state machine ([HordeRaidEvent](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/core/colony/events/raid/HordeRaidEvent.java); `EventStatus` STARTING → PREPARING → PROGRESSING (→ WAITING) → DONE/CANCELED).
  - Raiders first **camp at campfires**, which telegraphs the attack, then attack, breaking blocks and doors if configured.
- **Defaults:** `maxCitizenPerColony` = 250 and `maxRaiders` = 80 ([ServerConfiguration](https://github.com/ldtteam/minecolonies/blob/version/1.21/src/main/java/com/minecolonies/api/configuration/ServerConfiguration.java)).

**Verdict.**
- **Borrow (design only):**
  - per-transition tick rates. TPS-aware throttling would be Strategos's own design: MineColonies' lag factor does the opposite;
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
  - deep inheritance of AI classes;
  - lag compensation that adds AI work exactly when the server is slow.
- **License:** GPL-3.0. Even a hard dependency on Structurize (calling it via its API) arguably makes Strategos a combined work [inference: the FSF's view on plugins]. Avoid that unless Strategos goes GPL.

---

## 5. Villager Recruits and Talhanation's family

- **Recruits** ([repo](https://github.com/talhanation/recruits)):
  - **Status:** 1.15.2 for Forge 1.20.1 (2026-06-29). **[corrected]** There were no commits on `main` between 2026-06-29 and 2026-10-16; work resumed on 2026-10-16 and 2026-10-19. README: "All Rights Reserved unless otherwise explicitly stated"; Modrinth says ARR.
  - **Command system:**
    - Keys: R opens the command screen, U teams, M the world map ([ModShortcuts](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/init/ModShortcuts.java)).
    - The [CommandScreen](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/client/gui/CommandScreen.java) has movement, combat and other categories.
    - Targets come from where the player looks, via `player.pick(100–200)` on the server ([CommandEvents](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/CommandEvents.java)).
    - Orders: move, hold, follow, wander, back-to-position, face, attack, strategic (area) fire, mount, protect, upkeep, aggro states.
  - **Formations** ([FormationUtils](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/util/FormationUtils.java)): line, square, triangle, circle, hollow circle, hollow square and V, with spacing 1.75 and a "tight" option, oriented to the player's facing.
  - **Groups** are UUID-keyed.
  - **Factions:** layered on vanilla scoreboard `PlayerTeam`s, with diplomacy NEUTRAL/ALLY/ENEMY, treaties, chunk claims (default 50) and sieges ([RecruitsDiplomacyManager](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/world/RecruitsDiplomacyManager.java)). The world map has routes and waypoints.
  - **Leaders (commander, captain) and messengers are chunk loaders** via `ForgeChunkManager`, using non-ticking tickets ([AbstractChunkLoaderEntity](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/entities/AbstractChunkLoaderEntity.java)), so patrols keep running away from players.
  - **Async pathfinding:** **[corrected]** on by default (`UseAsyncPathfinding = true`) with one worker thread (`AsyncPathfindingThreadsCount = 1`). It is marked "petal start/end", i.e. taken from the archived Petal server fork, which is GPL-3.0. This is another license tangle.
  - **Limits:** 100 recruits per player and 500 NPCs per faction by default.
- **Villager Workers 2.0** (2026-05): the worker is "only the labour". Work is defined by *Work Area* entities: storage, lumber, fishing, animal farm, mining, building, crop farm, plus couriers. This cleanly separates "who works" from "what work".
- **Siege Weapons:** catapults and ballistae with projectile types.
- **Small Ships:** has 1.21.1 builds (and a 1.21.4 beta).

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
  - **Status:** **9.0.2 for NeoForge 1.21.1** (2026-09-18; 9.0.0-beta.2 was 2026-07-25). Custom license. Maintained by kinniken (owner) and FireController1847 (listed as maintainer); CurseForge also lists MoonCutter2B as author.
  - **Source:** the Java source is not public. The [GitLab project](https://gitlab.com/Millenaire/Public) holds only content: cultures, villagers, goals, quests, languages. **[corrected]** Its last commit is from 2018-07-07 ("Update to 6.2.8"), so it documents the older data format and may not match 9.x.
  - **Data format:** buildings are **PNG layer plans** with upgrade levels (`carpenterhouse_A0.png`…`A3.png`) plus small text configs (`max:2;priority:800;…`) ([example](https://gitlab.com/Millenaire/Public/-/tree/master/millenaire/cultures/norman/buildings/houses)).
  - **Borrow:** cultures (≈ civilizations) as data; buildings that upgrade through successive plans.
  - **License:** ideas only.
- **Ancient Warfare 2:**
  - **Status:** GPL-3.0-or-later. Last release 2.7.0.1038 for 1.12.2 (2021-04-29); repo idle since 2021-11.
  - **Modules:** Core, Automation, NPCs (workers, soldiers, world-gen factions), Structures (its own template system), Vehicles (siege engines).
  - **Commands:** a *command baton* selects NPCs and issues MOVE/ATTACK/ATTACK_AREA/GUARD/SET_HOME/SET_UPKEEP ([NpcCommand](https://github.com/P3pp3rF1y/AncientWarfare2/blob/1.12.x/src/main/java/net/shadowmage/ancientwarfare/npc/npc_command/NpcCommand.java)). Standing instructions are **order items** — Work, Routing, Upkeep, Combat, Trade ([orders/](https://github.com/P3pp3rF1y/AncientWarfare2/tree/1.12.x/src/main/java/net/shadowmage/ancientwarfare/npc/orders)).
  - **Known problems:** NPCs get stuck on steps and ladders ([#1365](https://github.com/P3pp3rF1y/AncientWarfare2/issues/1365), [#1410](https://github.com/P3pp3rF1y/AncientWarfare2/issues/1410)). The source code admits the orders design needs refactoring ("TODO refactor to proper individual instances of commands").
- **Guard Villagers:** code under MIT, `src/main/resources` ARR. Its asset license explicitly forbids "any ai-directed version ports" — relevant to an AI-assisted project. Patrol and follow guards, nothing RTS-specific.
- **Easy NPC** (MIT for code only; assets are excluded; 1.21.1): NPC authoring (skins, dialogs, actions). **MCA Reborn** (GPL-3.0, 1.21.1; newest builds for 26.x): villager life simulation. **Human Companions** (GPL-3.0, 1.20.1, idle since 2024-12). **Taterzens** (MIT, Fabric, no 1.21.1 build). **Custom NPCs** (closed, ≤1.16.5 on CurseForge).
  - None of these matters for RTS beyond small UX ideas.
- **Towny** ([CC BY-NC-ND 3.0](https://github.com/TownyAdvanced/Towny)) is the server-plugin precedent for claims and nations. It forbids derivatives.

---

## 7. Other RTS-like mods for 1.20+/1.21.x

Searched on Modrinth (rts, strategy, army, commander, top-down camera, kingdom) and on the web for CurseForge.

- **i113w's Camera Lib** (MIT; client-only; v0.0.x; ~560 downloads) is the cleanest reusable reference. **[corrected]** It targets NeoForge 1.21.1: it is built against 21.1.209 but declares NeoForge `[21,)` and Minecraft `[1.21,1.22)`. The latest 1.21.1 build is 0.0.3-alpha3 (2026-06-09), and GitHub holds only that source; the 1.20.1 and 26.1.2 builds have no public source. Its pieces:
  - a client camera entity set with `Minecraft#setCameraEntity`. [inference] The server-side player does not move with it, so chunk loading still follows the real player;
  - an optional orthographic mixin on `GameRenderer#getProjectionMatrix(D)` ([GameRendererMixin](https://github.com/i113w/113CameraLib/blob/main/src/main/java/com/i113w/camera_lib/mixin/GameRendererMixin.java));
  - [LevelRendererFrustumMixin](https://github.com/i113w/113CameraLib/blob/main/src/main/java/com/i113w/camera_lib/mixin/LevelRendererFrustumMixin.java) skips `LevelRenderer#offsetFrustum` — the same freeze RoN works around. Its mixin plugin disables this when Sodium, Embeddium or Rubidium is loaded;
  - the cursor is freed with `MouseHandler#releaseMouse` every client tick, with no screen;
  - [MouseRayCaster](https://github.com/i113w/113CameraLib/blob/main/src/main/java/com/i113w/camera_lib/math/MouseRayCaster.java): ray through the inverse of projection × model-view, then block clip, then entity hit;
  - box-select and right-click events.

  [Better Mine Team](https://github.com/i113w/BetterMineTeam) (MIT; an unofficial rewrite of XiaoHuNao's MineTeam, "developed with AI assistance"; NeoForge 1.21.1) builds RTS command on top of it: box select, move, attack, attack-move, formations. The RTS code is on branch `neo-1.21.1-dev2`; `main` is older.
- **RTS Building** (LGPL-3.0-only code, ARR assets; NeoForge 21.1.238; ModDevGradle 2.0.140). **[corrected]** The repo also still contains an older "RTS Building User Terms 1.0" (`License.md`). It requires projects that use its code to be open source and bans charging for distribution, and the README says earlier releases keep the terms they shipped with. Its pieces:
  - a server-spawned camera entity, kept within an "action radius" of the player;
  - **`@Pseudo` mixins for Sodium's `LevelSlice` and Embeddium**, because Sodium builds chunk meshes off the vanilla `RenderChunkRegion` path, so block hiding must be done twice ([SodiumLevelSliceMixin](https://github.com/Hcrab/RTSbuilding/blob/main/src/main/java/com/rtsbuilding/rtsbuilding/mixin/SodiumLevelSliceMixin.java));
  - the best testing setup seen: [src/test](https://github.com/Hcrab/RTSbuilding/tree/main/src/test) with unit, contract and performance JUnit 5 + Mockito suites, plus `gameTestServer` runs.
- **MineFortress** (MIT, Fabric 1.20.1/1.20.2, dormant since 2025-08-09):
  - a "Fortress" game mode, perspective camera, mouse picking by redirecting `getRotationVec` ([FortressGameRendererMixin](https://github.com/remmintan/minefortress/blob/master/src/main/java/org/minefortress/mixins/renderer/FortressGameRendererMixin.java));
  - blueprints and area designations;
  - pawns move with **one Automatone (Baritone) instance per entity**, using MineFortress's own Automatone fork 1.0.9 ([BaritoneMoveControl](https://github.com/remmintan/minefortress/blob/master/src/main/java/org/minefortress/entity/ai/controls/BaritoneMoveControl.java)) [inference: fine for dozens of pawns, not hundreds];
  - Kotlin and Yarn mappings, so porting to Mojmap/NeoForge is work.
- **Hundred Years Warfare** (ARR, closed; 1.20.1/1.21.1; ~178k downloads): "RTS mode" with hotkeys, formations and shift-queued commands; counter relationships between unit types; workers "reworked based on Ancient Warfare 2". It says of itself that it is "not an RTS-focused mod".
- **Others:**
  - Ages of Dominion (ModJam 2026, NeoForge 26.1.2, singleplayer only; custom/ARR licence, no source; AoE-like Dark, Feudal and Castle ages);
  - Tale of Kingdoms (LGPL-3.0, Fabric). **[corrected]** Its 1.21.1 build is release 1.0.6 (2024-09), not an alpha;
  - Steve's Army (LGPL-2.1+, squad AI with fire teams and threat sharing, 1.20.1);
  - RTS Map Command Recruits and Commanders Vision (addons for Recruits).

---

## 8. Minecraft Legends (Mojang Studios / Blackbird Interactive)

**Facts.**
- Released 2023-04-18 on PC, Xbox, PlayStation and Switch, with a campaign and 4v4 PvP ([VGC](https://www.videogameschronicle.com/news/mojang-reveals-minecraft-legends-april-release-date-and-shows-pvp-gameplay/)).
- Scores: **[corrected]** Metacritic PC 65 from 36 critics, user score 5.1 from 85 ratings ([critic page](https://www.metacritic.com/game/minecraft-legends/critic-reviews/?platform=pc), [user page](https://www.metacritic.com/game/minecraft-legends/user-reviews/?platform=pc); the legacy page now returns 403). OpenCritic top-critic average 70, 41 % recommend, 106 reviews ([OpenCritic](https://opencritic.com/game/14349/minecraft-legends)).
- In January 2024 Mojang said no new content would come; the Snow vs Snouts Lost Legends drop was the final content ([Nintendo Life](https://www.nintendolife.com/news/2024/01/minecraft-legends-development-ends-as-final-update-launches), 2024-01-10). **[corrected]** Nintendo Life does not say which update was the last major one. TrueAchievements names the November 2023 update; other outlets differ (unverified).

**Command model.**
- The hero's banner issues Rally, Quick Direct, Banner View (aim at a target), Charge, Focus Target and Type Select, plus group variants of Charge and Focus Target ([guide](https://upcomer.com/how-to-control-your-mobs-in-minecraft-legends)).
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
| Vanilla 1.21.1 `PathNavigation` | Synchronous, server thread | `PathNavigationRegion` within ±(follow range + 8/16) | A* per mob; budget `floor(FOLLOW_RANGE×16) × multiplier`, fixed at construction | Follow range (16 by default in `Mob.createMobAttributes`; many mobs override it) | Mojang (callable, not copyable) |
| RoN `rtsPathfinding` | Main thread builds cache under a budget; A* on a pool (cores/2) | Immutable snapshot of a per-chunk walkability cache | 3D grid A*, 8 directions, ±1 step, falls ≤ 3, climbing, 4 mobility classes, footprints, 10 chained segments of radius 96 | World border ≤ 1,280 for prewarm (cache of 5,184 chunks fully holds only ≈ ≤ 1,140-block borders) | GPL-3.0 |
| MineColonies | One worker thread, 10k queue | Live `LevelChunk` refs + caching lookup | A* with rich costs; typed path jobs; escalating stuck handler | 8k nodes (×1–4); 900-block guard (teleport + 300 s pause) | GPL-3.0 |
| Recruits | Pool (default on, 1 thread) | Vanilla region (Petal approach) | Vanilla A*, async | — | ARR / Petal GPL |
| Baritone v1.11.3 (1.21.1) | Own thread | 2-bit chunk cache (AIR/SOLID/WATER/AVOID) | Segmented A*, incremental cost backoff, path splicing, breaks and places blocks | One 1×2 agent; "horses (2×3)" may never be supported | LGPL-3.0 |
| Automatone | Server, Fabric | as Baritone | Baritone for any `LivingEntity` | MC ≤ 1.20.1; idle since 2024-01 | LGPL-3.0 |
| FrostedHeart | Main thread snapshot → worker → main thread | 64×64 heightmap snapshot | **Reverse-BFS flow field per target chunk**, shared by every citizen with that target; at most 2 builds per tick; rebuilt every 2,400 ticks | 2.5D: no caves or overhangs; a height step > 1 blocks | GPL-3.0 |

Sources: [Baritone FEATURES.md](https://github.com/cabaletta/baritone/blob/1.21.1/FEATURES.md); [FlowFieldCache](https://github.com/TeamMoegMC/FrostedHeart/blob/HEAD/src/main/java/com/teammoeg/frostedheart/content/town/citizen/nav/FlowFieldCache.java); Recruits [AsyncPathProcessor](https://github.com/talhanation/recruits/blob/main/src/main/java/com/talhanation/recruits/pathfinding/AsyncPathProcessor.java). The Boobcat Edition claims "flow-field smoothing", but there is no source to check (unverified).

**What follows for Strategos** [inference, ranked by evidence]:
1. Own the pathfinding pipeline from day one; vanilla navigation is the wrong shape. RoN's pipeline is the right skeleton:
   - budgeted main-thread classification;
   - incremental reclassification of dirty regions;
   - immutable snapshots;
   - worker pool;
   - request sequence numbers;
   - backpressure;
   - an arrival settle distance.

   Unlike RoN, derive cache capacities from the map size.
2. Precompute per-cell costs once per chunk, not per search; that was RoN's 10× win. Fail fast on unreachable goals.
3. For many units sharing a target (an army's move, workers going to a drop-off), use shared fields or a leader path plus slots, as FrostedHeart and RoN's formations do. Cap field builds per tick and rebuild on terrain change.
4. Add a coarse hierarchical layer (chunk portals / HPA*) for cross-map orders. Nobody has done this; RoN instead chains 96-block A* segments.
5. Write an escalation ladder for stuck units (MineColonies), and add explicit cost models for doors, water and fire.
6. Baritone and Automatone are not RTS movers: one agent, player-sized, the wrong licence shape for copying. Read FEATURES.md for ideas (segmenting, cost backoff, chunk compaction).

---

## 10. Platform facts in 1.21.1 / NeoForge 21.1 that these techniques rely on

All verified in the decompiled sources.

- `GameRenderer#getProjectionMatrix(double)` (line 980) is also used to build the culling frustum (line 1276). NeoForge has `ViewportEvent.ComputeCameraAngles` and `ComputeFov` and `CalculateDetachedCameraDistanceEvent`, but no event to replace the projection, so orthographic needs a mixin.
- `LevelRenderer#offsetFrustum` → `Frustum#offsetToFullyIncludeCameraCube(8)` loops until the camera cube is inside the frustum. Bypass it when orthographic is on.
- Entity visibility: `ChunkMap$TrackedEntity#updatePlayer` pairs or unpairs on `distance && entity.broadcastToPlayer(player) && isChunkTracked`. **[corrected]** It is called from four places:
  - `ChunkMap.tick()` when an entity changes chunk section (against all players), and when a player changes section (every entity against that player);
  - `ChunkMap.move(ServerPlayer)`, for every tracked entity on each accepted movement packet from that player. Clients send one at least every 20 ticks even when idle; vehicle moves and spectator-camera follow also call it;
  - when an entity is added;
  - when a player is added.

  `PlayerEvent.StartTracking` is **not** cancellable.
  - So for Strategos's own entities, fog can be enforced by overriding `Entity#broadcastToPlayer(ServerPlayer)`. Without extra work, a vision change takes effect at the next evaluation, which is up to about a second for a connected client. A fog system should still force re-evaluation (via an invoker or accessor on the package-private `updatePlayer`) whenever vision changes. It must never cancel `updatePlayer` outright, because that also blocks unpairing.
- Chunk tickets: `TicketController#forceChunk(level, BlockPos | Entity | UUID owner, x, z, add, ticking)` keeps armies and AI bases simulating away from players (Recruits does the equivalent on Forge 1.20.1). `TicketController` is a record registered in `RegisterTicketControllersEvent`. Entity owners are stored by UUID, and `ticking = true` also gives full chunk ticks with no player nearby.
- `ServerPlayer#setCamera` and `getCamera` exist (spectate-style camera body). While a camera entity is set, `ServerPlayer.tick` moves the player onto it every tick, so chunk loading and tracking follow the camera. RoN instead flies the spectator body itself.

---

## 11. License compatibility and clean-room protocol

| License class | Projects | What Strategos may do (license undecided, private now, maybe permissive later) |
|---|---|---|
| MIT | Camera Lib, Better Mine Team (keep both the XiaoHuNao and i113w notices), MineFortress, Guard Villagers (code only), Easy NPC (code only), Taterzens | Copy or fork with the copyright notice kept. Best sources for actual code. |
| LGPL-3.0 / LGPL-2.1 | Baritone, Automatone, RTS Building (code; see the conflicting legacy "User Terms 1.0" in its repo), Tale of Kingdoms, Steve's Army | Depend on them as a separate, unmodified jar. Copied code becomes part of a modified Library and must stay under LGPL. If you distribute changes to the library, you must make their source available to recipients under LGPL. Treat RTS Building's code as off-limits for copying until its terms are clarified. |
| MPL-2.0 | SmartBrainLib | Depend on it freely; changes to MPL files stay MPL (file-level copyleft). |
| GPL-3.0 | RoN, MineColonies, Structurize, BlockUI, Domum Ornamentum, AW2, Human Companions, MCA Reborn, FrostedHeart, Petal | Ideas only. Copying code or hard-linking makes the distributed Strategos GPL-3.0. Private, undistributed use carries no GPL obligations, but it closes off a later permissive release. Contributing upstream requires signing their CLAs, and MineColonies also rejects patches written mostly by AI. |
| ARR / custom / no derivatives | Recruits, Workers, Siege Weapons, Small Ships, Hundred Years Warfare, Millénaire, Ages of Dominion, Custom NPCs, Towny, Guard Villagers' assets, Easy NPC's assets | Ideas only. No code and no assets. |

**Clean-room protocol, suited to AI-assisted work:**
1. Read GPL or ARR sources in a separate research step and write **behavioural notes**: what the system does, its constants and its failure modes, as in this report. Do not keep code snippets.
2. Implement only from the notes, in sessions that have no copyleft source in context.
3. Record where each subsystem's ideas came from.
4. Never copy assets. Guard Villagers bans AI-directed ports of its assets outright.

---

## 12. Consolidated borrow / avoid

**Borrow (as design):**
- **Bounded match maps:** prewarm walkability and fog for the whole map, sizing caches from the actual border (RoN's cache constant lags its own threshold); open-world support later (RoN).
- **Camera:** orthographic or perspective camera with an optional orthographic mode; a cursor screen or a mouse release; inverse-MVP picking; frustum bypass; leaf hiding; Sodium/Embeddium adapters (RoN, Camera Lib, RTS Building).
- **Server-authoritative interest management from day one:**
  - a vision grid per team on a fixed cadence (RoN: 10 ticks);
  - entity gating through `broadcastToPlayer` plus forced re-tracking (never cancelling `updatePlayer` outright);
  - chunk and column gating;
  - a single sender for mod packets that knows about visibility;
  - biome-tint fog rendering;
  - a match-start snapshot.
- **Data first:** unit, building and player state keyed by UUID, scoped to the server instance, with entities as avatars (MineColonies). No static globals (RoN's bugs).
- **Stats and techs:** stats as registered, syncable Attributes; techs as data-defined modifiers (RoN attributes + MineColonies JSON research).
- **Buildings:** NBT/structure templates, per-level upgrade plans, phased progressive placement, per-block placement handlers, AoE2 builder formula, health = blocks placed (RoN, MineColonies/Structurize, Millénaire).
- **Commands:** command objects in a registry, shift queues, server validation by UUID, optional client prediction; formation catalogue and drag-to-face; aggro and stance states; control groups (RoN, Recruits).
- **AI scheduling:** per-transition tick rates, a stuck-handling ladder and abstract travel for off-screen logistics (MineColonies). TPS-aware throttling has to be Strategos's own design, because MineColonies' lag factor speeds AI up under lag.
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

**Scope and method (2026-10-20, independent pass).**
- **Fresh source.** New shallow clones, not the researcher's copies:
  - RoN `1.20.1-dev` (HEAD 3943983, "1.20.1 1.5.0b"; 968 main + 1 test Java files);
  - MineColonies and Structurize `version/1.21`;
  - Recruits `main`, RTS Building `main`, Camera Lib `main`;
  - Better Mine Team `main` and `neo-1.21.1-dev2`;
  - MineFortress `master`.

  Raw files were read from AW2, FrostedHeart, Baritone (`1.21.1`), Automatone (`1.20`), Guard Villagers, Towny, Petal, Easy NPC, Domum Ornamentum, SmartBrainLib and the Siege of Settlements repo.
- **Metadata:**
  - Modrinth API: project and version lists for 18 projects;
  - CurseForge via cfwidget (RoN, MineColonies, Structurize, Millénaire, AW2, Custom NPCs, Ages of Dominion, Human Companions), plus CurseForge pages for Millénaire, Ages of Dominion and MineColonies' file list;
  - GitHub REST API: repo metadata; RoN #243/#299/#348/#422/#445/#485; MineColonies #10692; AW2 #1365/#1410; Baritone releases; Automatone branches and releases; Recruits commit history;
  - GitLab API for Millenaire/Public.
- **Decompiled MC 1.21.1 + NeoForge 21.1:**
  - GameRenderer.java:980/1276; Frustum.java:33; LevelRenderer.java:865/874;
  - ChunkMap.java:1023-1031/1108-1131/1151-1180/1327-1345; ServerChunkCache.java:464; ServerGamePacketListenerImpl.java:460/891/970; LocalPlayer.java:283-284;
  - ServerPlayer.java:524-528/1734-1748; Entity.java:3021; PlayerEvent.java:261; ViewportEvent.java;
  - TicketController.java:27-68; ForcedChunkManager.java:111-121;
  - PathNavigation.java:65/117-143/155-169; PathFinder.java:70; Mob.java:160; Attributes.java:71.
- **Web:** OpenCritic; metacritic.com critic and user pages; Nintendo Life review and news; Kotaku review; Nintendo Everything patch notes; Upcomer guide; 80.lv.

**Material changes.**
1. **RoN navmesh cache.** `MAX_CACHED_CHUNKS = 5184` (72×72) was sized for a 1,024-block border, but the threshold is now 1,280 (≈ 6,561 chunks). The whole-map prewarm therefore overflows and LRU-evicts on borders above about 1,140 blocks; the "never evicts" claim is wrong. The `rtsPathfinding` gamerule can also force the pathfinder on any map.
2. **Vanilla entity tracking.** Re-evaluation is not limited to chunk-section changes: `ChunkMap.move(ServerPlayer)` re-evaluates all tracked entities on each player movement packet (at least every 20 ticks). RoN's fog leak is still real, because cancelling at HEAD blocks unpairing, but its stated cause was wrong. The client hides fogged entities, so the leak exposes data to modified clients rather than showing them.
3. **MineColonies `slownessFactor`.** It divides transition intervals under lag (more checks per tick), so it compensates and does not throttle. This changes the "TPS-aware throttling" borrow item and recommendation 11.
4. **Recruits.** Async pathfinding is on by default (1 thread). Commits were not daily: there was a gap from 2026-06-29 to 2026-10-16.
5. **MineColonies distance guard.** The 900-block guard also teleports the entity and pauses navigation for 300 s. Issue #10692 was about the older 500-block limit (~630-block trips). There has been no 1.21.1 build since 2026-10-04.
6. **Camera Lib.** It declares NeoForge `[21,)`, frees the cursor via `releaseMouse`, disables its frustum bypass under Sodium, Embeddium or Rubidium, and has public source only for 1.21.1 0.0.3. Better Mine Team's RTS code is on a dev branch, and the mod is a MIT rewrite of MineTeam.
7. **RTS Building.** The repo still carries a legacy "User Terms 1.0" alongside the LGPL, so its licence is ambiguous.
8. **Millénaire.** The GitLab content repo has been unchanged since 2018 (v6.2.8).
9. **Minor fixes:**
   - RoN cadence (6 releases + 5 betas), validation nuance, game-mode list and map pack (21 maps);
   - Structurize snapshot vs release; MineColonies class order, `EventStatus` WAITING and snapshot dependencies;
   - Small Ships 1.21.4 beta; MCA Reborn's newest target 26.2; Easy NPC assets excluded; Tale of Kingdoms 1.21.1 is a release;
   - Ages of Dominion licence (custom/ARR, no source);
   - Metacritic figures (36 critics, user 5.1/85); attribution of Legends' "last major update"; Automatone 0.11.0 = MC 1.20.1.

**Confirmed without change.**
- RoN version, licence, CLA and no-1.21.1 statement; camera, frustum, cursor, selection and command code; 138-value enum; 54 unit classes; Attributes.
- RoN pathfinding constants; PR #445 text; fog cadence and 135 broadcast sites; building, resource and research code; no AI opponent; single test class.
- MineColonies pathfinding executor, node budget, `ChunkCache`, costs and build stages.
- Recruits keys, formations, diplomacy, chunk loading and limits; Petal GPL.
- AW2 status and licence; Baritone 1.21.1 builds and FEATURES.md; FrostedHeart flow field.
- RTS Building tests and toolchain; Guard Villagers licence.
- Legends quotes and patch notes; all decompiled-source line references.

**Still unverified.** The Moddex review; the meaning of "66 TPS"; whether RoN's fog leak shows at runtime (certain at code level, untested); the "~100 lines per unit" estimate; whether RoN and Camera Lib found the frustum bypass independently; the month of Legends' last major update; Hundred Years Warfare internals; Boobcat Edition's flow-field claims.

**Open questions after verification.**
1. RoN unit capacity with 4–8 players: still open; build our own benchmark harness.
2. RoN fog leak: the code path is confirmed (cancelled `updatePlayer` blocks unpairing) and the client hides such entities. A runtime test with a modified client is still needed to measure exposure.
3. Failure rate of MineColonies' live-chunk reads: still open; no reports found.
4. Millénaire 9.x internals: still open. The public content repo predates 9.x (last commit 2018).
5. Hundred Years Warfare's RTS-mode implementation and its AW2-derived workers (GPL-3.0-or-later code in an ARR mod): still open.
6. Boobcat Edition's flow-field and multithreaded A* claims: still unverifiable, as there is no source.
7. RoN reception beyond changelogs: still thin.
8. Camera Lib maintenance outlook: an alpha (0.0.3-alpha3, 2026-06-09) by a solo author with ~560 downloads, and source only for 1.21.1. Fork or reimplement rather than depend on it.
9. Orthographic rendering with Sodium 0.6 / Iris on NeoForge 1.21.1: still untested. Camera Lib simply disables its frustum bypass under Sodium, Embeddium or Rubidium.
10. More than about 500 concurrently commanded units at stable TPS: no evidence found.
11. Licences of forks and small mods: Ages of Dominion is now known (custom/ARR, no source). Siege of Settlements is confirmed contradictory (its repo's LICENSE.txt is GPL-3.0, Modrinth says LGPL-3.0-only). RTS Building's licence is also ambiguous because of its legacy User Terms.

## Recommendations (after verification)

1. 1. Adopt a clean-room rule before any code is written. GPL, ARR and custom-licensed projects are design references only: RoN, MineColonies/Structurize/BlockUI, AW2, the Recruits family, Millénaire, Hundred Years Warfare and Ages of Dominion. Write behavioural notes from them, implement only from the notes in sessions with no copyleft code in context, and log provenance per subsystem. Copy code only from MIT projects, keeping their notices: i113w Camera Lib; Better Mine Team (keep both the XiaoHuNao and i113w notices); MineFortress; Guard Villagers' code (never its ARR assets); Easy NPC's code (its assets are excluded from MIT). Use LGPL/MPL projects (Baritone, SmartBrainLib) only as separate, unmodified jars. Do not copy RTS Building code until its licence is clarified: the repo pairs LGPL-3.0-only with a legacy 'User Terms 1.0' that demands open-source derivatives.
2. 2. Make bounded match maps the first-class mode, as RoN does with world border ≤ 1,280: prewarm walkability and fog for the whole map at match start, and keep open-world play as a later feature. Size the walkability and fog caches from the actual border and budget memory per map size. RoN's 5,184-chunk cache was sized for a 1,024-block border, so near its 1,280 threshold the prewarm overflows and evicts. This single constraint is what made RoN's pathfinding and fog affordable.
3. 3. Build the RTS camera from the proven 1.21.1 recipe. Use Camera Lib (MIT; alpha 0.0.3, solo author, public source only for 1.21.1) as a reference or fork. The pieces: a camera entity or spectator body; an optional orthographic mixin on GameRenderer#getProjectionMatrix(double); a mandatory bypass of LevelRenderer#offsetFrustum when orthographic; an inverse-MVP mouse ray; cursor release (a dummy screen as in RoN, or MouseHandler#releaseMouse as in Camera Lib); leaf/canopy hiding with Sodium/Embeddium adapters. Test orthographic culling under Sodium/Embeddium explicitly, because Camera Lib simply disables its frustum bypass there. Decide early how the server-side body works, because chunk sending and entity tracking follow the ServerPlayer. The options are: fly a spectator body (RoN); call ServerPlayer#setCamera on a server camera entity (vanilla then moves the player onto it every tick); or keep a camera entity within an action radius plus chunk tickets (RTS Building). A purely client-side camera entity does not move the server-side player.
4. 4. Own the pathfinding pipeline from the first milestone. Build per-chunk walkability per mobility class on the main thread under a tick budget, and reclassify only dirty regions after a settle delay. Precompute per-cell costs once. Run A* on a worker pool over immutable snapshots, and deliver results on the main thread guarded by request sequence numbers. Use backpressure plus fail-fast budgets for unreachable goals, and derive cache capacities from the map size rather than a constant. Add shared fields or leader paths for group moves and a coarse hierarchical layer for cross-map orders. Instrument it from day one: compute time, end-to-end latency and queue depth in an F7-style overlay.
5. 5. Design interest management (who sees what) before networking. Keep a per-team vision grid on a fixed cadence (RoN uses 10 ticks). Gate Strategos entities by overriding Entity#broadcastToPlayer. Force re-evaluation through an invoker on the package-private ChunkMap.TrackedEntity#updatePlayer whenever vision changes. Vanilla re-evaluates only on chunk-section changes, entity/player addition and the viewer's movement packets (about once per second per client). Never cancel updatePlayer wholesale: RoN's HEAD-cancel also blocks unpairing and keeps fogged enemies streaming to clients. Gate chunk and column updates. Send every mod packet through one sender that knows about visibility, never to all players by default. Render fog with biome or block tint for renderer compatibility. Snapshot the map at match start for rejoining players.
6. 6. Keep simulation data separate from entities. Hold units, buildings, players and teams as UUID-keyed state scoped to the server instance (SavedData or attachments), with entities as avatars that can respawn, as MineColonies' CitizenData does. No static global mutable state; RoN's world-switch bugs are consistent with it. This also lets most logic be unit-tested without bootstrapping Minecraft.
7. 7. Define content as data: units, buildings, technologies, civilizations and ages in datapack JSON with codecs. Register unit stats as Attributes, as RoN does, and mark them setSyncable(true) where the client needs them. Apply technologies as data-defined AttributeModifiers instead of hard-coded research classes or scattered hasResearch checks.
8. 8. Model orders as typed command objects in a registry, not a giant enum. Validate them on the server by UUID ownership, with optional client prediction, shift queues, stances and aggro states. Include a formation catalogue (Recruits' set plus RoN's slot pairing and drag-to-face) and client-side control groups. Copy Legends' fix: show exact destination and formation footprint before the order is confirmed.
9. 9. Construction: use vanilla structure templates from datapacks with per-level upgrade plans and phased progressive placement (solid, weak solid, water, non-solid, decorate, entities). Add per-block placement handlers for stateful and multi-block blocks, the AoE2 builder-count formula, and health proportional to blocks placed.
10. 10. Plan the AI opponent as a first-class player that uses the same command API as humans. Give it telegraphed attacks (MineColonies' campfire camps, Legends' attack timers) and varied strategies. No prior mod solves this; Ages of Dominion's night waves and Hundred Years Warfare's factions are scripted too. Prototype it early, as soon as the economy, production and combat loops exist.
11. 11. Schedule agent AI deliberately. Use state machines with per-transition tick rates (MineColonies), an escalating ladder for stuck units, and abstract travel for off-screen logistics such as trade carts. If you want TPS-aware degradation, design it yourself and test it: MineColonies' slownessFactor does the opposite, shortening intervals under lag to keep a real-time cadence. Use NeoForge TicketController chunk tickets, registered in RegisterTicketControllersEvent, for armies and AI bases that must keep simulating away from players.
12. 12. Set the testing bar higher than the prior art from the start. Use RTS Building's NeoForge 1.21.1 setup as a pattern: ModDevGradle 2.0.140; JUnit 5.11 and Mockito 5.12 for pure logic; unit, contract and performance test sets; gameTestServer runs for in-world behaviour. Re-create the build configuration from the ModDevGradle docs rather than copying its files, given its licence ambiguity. Add pathfinding and fog benchmarks with fixed seeds and unit counts (for example 200, 500 and 1,000 units) as regression gates.
13. 13. Watch Reign of Nether for ideas, since it releases more than weekly and its changelogs document real RTS pain points. Do not plan around a 1.21.1 port or contributing upstream: there is no port plan, and contributions require a GPL project's CLA. MineColonies additionally rejects patches written mostly by AI under its AI disclosure policy.

## Open questions (from the original research)

- How many units RoN actually sustains with 4-8 players under rtsPathfinding and fog. The only figure is a contributor's '200 workers mining… held 66 TPS', which is ambiguous; this needs our own benchmark harness.
- Whether RoN's fog leaks entities that walk into fog after being seen. Cancelling TrackedEntity#updatePlayer at its start appears to skip unpairing; I inferred this from code and did not test it at runtime.
- Real-world failure rate of MineColonies' off-thread reads of live LevelChunks (races, ConcurrentModificationException). I found no issue reports, so the safety of that shortcut is unproven.
- Millénaire 9.x internals on NeoForge 1.21.1, e.g. whether it is a rewrite or a port and how its village AI is built: the Java source is not public.
- Hundred Years Warfare's 'RTS mode' implementation (hotkeys, formations, shift commands) and whether its AW2-derived workers raise license problems: the mod is closed source and ARR.
- Whether the Reign of Nether Boobcat Edition really has flow-field smoothing and multithreaded A*. There is no source link despite its GPL listing.
- Player reception of Reign of Nether beyond changelogs and PRs: the community lives on Discord; the Moddex review was not fetchable (HTTP 403); there is no major press review.
- Maintenance outlook for i113w's Camera Lib (v0.0.x, about 560 downloads, solo author): depend on it, fork it, or reimplement from it?
- Whether an orthographic projection behaves acceptably with Sodium 0.6 / Iris on NeoForge 1.21.1. RoN reported culling-mod conflicts on Forge 1.20.1 and RTS Building needed Sodium/Embeddium pseudo-mixins; nobody has published a check for 1.21.1.
- Whether any studied mod shows more than about 500 concurrently commanded units at stable TPS. I found no evidence either way; AoE-scale populations (8 players × 200) are unproven in Minecraft.
- License status of Ages of Dominion and some smaller RoN forks (Siege of Settlements is labelled LGPL despite deriving from GPL): not checked or contradictory, and irrelevant only as long as no code is taken.

## Fact-check verdicts

confirmed: 30, corrected: 18, unverifiable: 2

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | Reign of Nether's latest release is 1.5.0b (2026-10-14), for Forge 1.20.1 only (1.19.x for mod versions up to 1.1.3). It is GPL-3.0-only with a CLA. On 2026-09-11 the maintainer said there are no plans for a 1.21.1 version. |  | Modrinth API project/reign-of-nether-rts: license GPL-3.0-only, game_versions [1.19.1,1.19.2,1.20.1], loaders [forge], newest version 1.5.0b 2026-10-14; last 1.19 build 1.1.3a (2025-03-08), first 1.20.1 release 1.1.4. https://github.com/SoLegendary/reignofnether/issues/485: SoLegendary 2026-09-11 'I have no current plans to do this'. CLA: CLAassistant comment on PR #299 (2025-09-10). README: 'For mod versions 1.1.3 and below, use Forge 1.19.2'. |
| corrected | RoN cadence: 11 releases between 2026-09-26 (1.4.4e) and 2026-10-14 (1.5.0b); ships about weekly. | 11 Modrinth versions in that window, of which 6 are releases (1.4.4e/f/g, 1.5.0, 1.5.0a, 1.5.0b) and 5 are betas (1.5.0-beta-3/4/5/7/8). Cadence is more than weekly. | Modrinth API project/reign-of-nether-rts/version, filtered date_published >= 2026-09-26 (version_type field). |
| confirmed | RoN reach: Modrinth ~421k downloads, 845 followers; CurseForge ~977k; GitHub 338 stars, 112 forks. README roadmap updated 2026-09-23. |  | Modrinth API: downloads 421,295, followers 845; cfwidget minecraft/mc-mods/reign-of-nether-rts-in-minecraft: 977,111; GitHub API repos/SoLegendary/reignofnether: 338 stars, 112 forks; README.md line 8 'last updated 23 September 2026'. |
| confirmed | RoN toolchain: ForgeGradle 5.1, Forge 1.20.1-47.4.0, Parchment 2023.09.03-1.20.1, Mixin, Essential partner SDK. |  | Fresh clone of 1.20.1-dev (HEAD 3943983 '1.20.1 1.5.0b'): build.gradle lines 9 (ForgeGradle 5.1.+), 45 (parchment 2023.09.03-1.20.1), 149 (forge 1.20.1-47.4.0), 150 (mixin 0.8.5), 213 (gg.essential:partner-mod-integration-1.20.1-forge:1.0.2). |
| confirmed | RoN camera: orthographic projection via a mixin on GameRenderer#getProjectionMatrix; server puts the player in spectator and flies the body; a dummy container screen frees the cursor. Camera Lib uses the same projection technique under MIT on NeoForge 1.21.1. | Camera Lib uses the same projection mixin, but it frees the cursor differently: it calls MouseHandler#releaseMouse every client tick instead of opening a dummy screen. | RoN mixin/OrthoViewMixin.java (@Inject HEAD getProjectionMatrix, setReturnValue ortho matrix); player/PlayerServerEvents.java:884 setGameMode(SPECTATOR); guiscreen/TopdownGui.java:15 extends AbstractContainerScreen. Camera Lib mixin/GameRendererMixin.java:16 'getProjectionMatrix(D)Lorg/joml/Matrix4f;'; input/RTSInputHandler.java:88 releaseMouse(). |
| confirmed | With an orthographic projection the vanilla frustum offset loop (LevelRenderer#offsetFrustum -> Frustum#offsetToFullyIncludeCameraCube(8)) must be bypassed or the game freezes; RoN and Camera Lib each patched it. | Addition: Camera Lib's mixin plugin turns its bypass off when Sodium, Embeddium or Rubidium is loaded. Whether the two projects found the fix independently cannot be checked. | mc-src-1.21.1 Frustum.java:33-56 (loop moves cam back along viewVector until intersectAab == -2); LevelRenderer.java:865 applyFrustum(offsetFrustum(frustum)), 874-875 offsetToFullyIncludeCameraCube(8). RoN mixin/FrustumMixin.java comment 'the game freezes and gets stuck inside this function forever'. Camera Lib mixin/LevelRendererFrustumMixin.java; mixin/plugin/CameraLibMixinPlugin.java shouldApplyMixin returns false if embeddium/rubidium/sodium are loaded. |
| confirmed | RoN camera limits: zoom 10-90 (zoom x2 = visible height), pitch -20 to -90, default yaw 135/pitch -45; body at avg terrain +30 up to +100; servers told to use view-distance=25; F12 toggle; CameraMixin pulls the camera back 20 blocks. |  | orthoview/OrthoviewClientEvents.java:85-93, 112-114, 158-180 (avgHeight + 30 / + 100), 475 (F12); mixin/CameraMixin.java 'pDistanceOffset -= 20'; Modrinth description 'view-distance=25, spawn-monsters=false, spawn-npcs=false'. |
| corrected | RoN rtsPathfinding: main-thread per-chunk walkability under a budget (4 cold chunks/tick), 3D grid A* on a worker pool over immutable snapshots; cores/2 threads, 250k nodes and radius 96 per segment, up to 10 chained segments, backpressure at 500; enabled only when the world border is at most 1,280 blocks; whole-map navmesh built at server start; cache of 72x72 = 5,184 chunks so it never evicts. | All constants are confirmed. Two parts are wrong. (1) The 5,184-chunk cache was sized for a 1,024-block border (a code comment says '64x64 chunks' plus margin). The threshold in force is 1,280 blocks, which is about 81x81 = 6,561 chunks, so on borders above roughly 1,140 blocks the prewarm overflows the LRU cache and evicts chunks. The 'never evicts' comments are stale. (2) The border only controls auto-enable: the rtsPathfinding gamerule can force the new pathfinder on any map. The 1.3.8 changelog gave the threshold as <=1024. | unit/pathfinding/PathfinderConfig.java (MAX_RADIUS 96, MAX_NODES 250000, MAX_NODES_UNREACHABLE_GOAL 8000, MAX_CHAIN_SEGMENTS 10, QUEUE_BACKPRESSURE_CAP 500, CHUNK_BUILDS_PER_TICK_DEFAULT 4, WORKER_THREADS cores/2, MAX_CACHED_CHUNKS 5184 with comment 'world border <= 1024 blocks = 64x64 chunks'); WalkabilityGrid.java:88 'while (chunks.size() > MAX_CACHED_CHUNKS) chunks.removeLast()'; worldborder/WorldBorderServerEvents.java:26 RTS_OPTIMIZED_BORDER = 1280 and prewarm loop over floor((c±size/2)/16); gamerules/GameruleServerEvents.java:112/180 sets UnitServerEvents.rtsPathfinding from the gamerule; PathfinderWorkerPool.java:39 MAX_THREAD_CAP 32, :161 256 results/tick; Modrinth 1.3.8 changelog. |
| confirmed | RoN PR #445 (2026-06-27): ~95% of A* time was a wall-avoidance cost recomputed per cell/unit/search; precomputing it per cached chunk plus fixing re-path loops and unreachable targets made pathfinding ~10x faster ('200 workers mining... held 66 TPS'). |  | GitHub API issues/445 body by S0ly (created 2026-06-27, closed 2026-06-28): four causes listed, '~95% of A*'s time', '~250ms', 'improved RTS pathfinding speed by ~10x. Tested with 200 workers mining on a server : held 66 TPS.' |
| corrected | RoN fog (1.4.0, 2026-08-04) is server-authoritative and block-granular, recomputed every 10 ticks; mixins gate chunk packets and entity pairing (ChunkMap$TrackedEntity#updatePlayer); biome-tint rendering; 135 PacketDistributor.ALL call sites; cancelling updatePlayer at HEAD probably leaves entities that walk into fog still paired. | The mechanism facts are confirmed. Precise attribution: ChunkMapMixin drops dark players from chunk broadcasts, and ChunkHolderMixin does the per-column filtering of edge chunks. The leak is not merely 'probable': cancelling at HEAD skips the removePairing branch every time, so a fogged entity stays paired until it is visible again. The report's reason for it is wrong, though. Vanilla re-evaluates pairing not only on chunk-section changes but also in ChunkMap.move(ServerPlayer), which runs on every accepted movement packet (clients send one at least every 20 ticks). RoN cancels rendering of fogged entities on the client (RenderLivingEvent), so the leak exposes data to modified clients; it is not a visual leak. | fogofwar/FogOfWarServerEvents.java:102 UPDATE_TICKS = 10; mixin/fogofwar/TrackedEntityMixin.java (@Inject HEAD updatePlayer, ci.cancel()); ChunkMapMixin.java:15-16 and ChunkHolderMixin.java:17-20 comments; grep count 135 for PacketDistributor.ALL; FogOfWarClientEvents.java:283-293 RenderLivingEvent cancel; mc-src ChunkMap.java:1327-1345 (add/remove pairing both inside updatePlayer), 1023-1031 move(ServerPlayer), ServerGamePacketListenerImpl.java:970, LocalPlayer.java:284 positionReminder >= 20; Modrinth 1.4.0 changelog; PR #422 body. |
| confirmed | RoN has no AI opponent; RTSPlayer bots (id < 0) are created only by the tutorial; PvE is scripted. None of the mods read has an economic RTS AI. |  | player/RTSPlayer.java:141-143 isBot() id < 0; only call of PlayerServerEvents.startRTSBot is tutorial/TutorialServerEvents.java:141. Descriptions of Ages of Dominion (night waves) and Hundred Years Warfare (NPC factions, raids) also describe scripted PvE only. |
| confirmed | RoN default population 150 (gamerule maxPopulation); 54 unit classes each subclassing a vanilla mob; identity by player name; 138-value UnitAction enum. | Minor: a few unit classes extend another unit class or the generic Monster/Mob rather than a specific vanilla mob. | resources/ResourceCosts.java:215 DEFAULT_MAX_POPULATION = 150; registrars/GameRuleRegistrar.java:44 'maxPopulation'; 54 files declaring class *Unit under unit/units (6 extend Vindicator); comment-stripped parse of unit/UnitAction.java = 138 constants; VindicatorUnit.java:124 EntityDataAccessor<String> ownerDataAccessor; FogOfWarServerEvents.java:111 'match by name; entity ids change on reconnect'. |
| confirmed | RoN buildings are vanilla NBT templates placed block by block; builder speed 3*base/(builders+2); health proportional to blocks placed; resources are world blocks behind a per-chunk index (8 chunks/tick, 2,048 LRU); 41 resource source types; 52 hard-coded research classes stored as (name, id) pairs. |  | 67 .nbt files in src/main/resources/data/reignofnether/structures; building/BuildingPlacement.java:1018-1024 (AoE 2 comment + formula), :147 minBlocksPercent = 0.5; resources/ResourceSources.java 41 'new ResourceSource('; resources/ResourceIndex.java:30-31 (2048, 8); research/researchItems 52 files; research/ResearchSaveData.java:18 ArrayList<Pair<String, ResourceLocation>>. |
| corrected | RoN validation: server checks that the sender's name equals ownerName (or that the sender is sandbox or has allied control); fast/slow (shift) queues; client also runs the action locally; vanilla-mode formation moves dispatched 5 per tick. | The sandbox exception tests the named owner, not the sender: SandboxServer.isSandboxPlayer(ownerName). The other points are confirmed. | unit/packets/UnitActionServerboundPacket.java:81-83; unit/UnitServerEvents.java:115-117, 297-337 (slow/fast queues), 127-136 FORMATION_DISPATCH_PER_TICK = 5 'VANILLA-ONLY'; unit/UnitClientEvents.java:293-309 actionItem.action(MC.level) before sending. |
| confirmed | RoN RTS stats are registered Attributes that sync to the client automatically (attack range, aggro range, sight range, resists, attacks per second; crit/lifesteal since 1.5.0). | They sync because each one is registered with setSyncable(true), so this is not automatic. | registrars/AttributeRegistrar.java (21 x setSyncable(true); attack_range, aggro_range, sight_range, ranged/magic_damage_resist, attacks_per_second, critical_hit_chance, lifesteal); Modrinth 1.5.0 changelog. |
| corrected | RoN game modes Classic, Wave Survival, King of the Beacon and Sandbox (plus scenario mode). | The GameMode enum is CLASSIC, SURVIVAL (wave survival), SANDBOX and SCENARIO. King of the Beacon is a CLASSIC match on a map that has a capturable beacon. | gamemode/GameMode.java:3-9. |
| confirmed | RoN praise: a community map pack with 20+ PvP maps. | It is the official map pack, which lists 21 PvP maps made by community mapmakers. | Modrinth project description, 'RTS Maps' section: 21 entries ([2p] Cabana Republic ... [6p] Essential Isles). |
| confirmed | MineColonies has a NeoForge 1.21.1 release (1.1.1403-1.21.1, 2026-10-04), GPL-3.0 with a CLA; one worker thread with a 10,000-job queue reading live LevelChunks off-thread; 8,000-node budget; 900-block distance guard. | Nuances. Ranged jobs use min(8000, range^2) nodes, then the config multiplier (1-4). The 900-block guard does more than refuse the job: it logs an error with a stack trace, teleports the entity (citizens to their home, others to the destination) and pauses navigation for 300 s. | cfwidget + https://www.curseforge.com/minecraft/mc-mods/minecolonies/files/all (1.1.1403-1.21.1 R, 2026-10-04); GitHub API license GPL-3.0; CONTRIBUTING.md:8 CLA; core/entity/pathfinding/Pathfinding.java:17,50 (ArrayBlockingQueue 10000, ThreadPoolExecutor(1,1)); pathjobs/AbstractPathJob.java:57,191,206; world/ChunkCache.java:46,81 (LevelChunk refs via holder.getChunkIfPresent); navigation/MinecoloniesAdvancedPathNavigate.java:310-331; ServerConfiguration.java:201 pathNodeLimitMultiplier (1,1,4). |
| corrected | MineColonies issue #10692 (2025-03): in 1.20.1-1.1.814 couriers working across a 600-block colony flooded the log (against the 900-block guard). | The guard was 500 blocks in that version. The issue title is '500 block max pathing is causing issues in large colonies'. Guards and couriers on trips of about 630 blocks, in a colony more than 40 chunks across, produced a log over 150 MB. The current version/1.21 code uses 900 blocks. | GitHub API repos/ldtteam/minecolonies/issues/10692 (created 2025-03-07; MineColonies 1.20.1-1.1.814; log line 'distance:630.9'). |
| corrected | MineColonies: 1.21.1 snapshots several times a week; version/1.21 builds against NeoForge 21.1.80, Structurize 1.0.832-1.21.1, BlockUI 1.0.199-1.21.1; ~89.5M CurseForge downloads; no src/test. | Snapshots came several times a week through September. There has been no 1.21.1 build since the 2026-10-04 release, and the last version/1.21 commit is from 2026-10-06. The Structurize and BlockUI dependencies are -snapshot builds. The download count and the absence of tests are confirmed. | CurseForge files page (latest 1.21.1 file 2026-10-04); fresh clone HEAD dae860ee 2026-10-06; gradle.properties forgeVersion=21.1.80, structurize_version=1.0.832-1.21.1-snapshot, blockUI_version=1.0.199-1.21.1-snapshot; cfwidget downloads 89,489,235; src/ contains only datagen and main. |
| corrected | MineColonies patterns: state machines with per-transition tick rates scaled by TPS (rates stretched by slownessFactor when the server lags); CitizenData separate from the entity with respawn and abstract travel; phased placement CLEAR, BUILD_SOLID, WEAK_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN; JSON research; hierarchy Skeleton -> Basic -> Interact -> Skill -> Structure -> StructureWithWorkOrder. | The TPS scaling runs the other way. When the last tick took more than 50 ms, slownessFactor = clamp(lastTickMs/50, 1, 5), and each transition's interval is divided by it. Under lag, transitions are therefore checked more often per tick to keep their real-time cadence: this compensates for lag and does not throttle. The class order is Basic -> Skill -> Interact. Stages, CitizenData, TravellingManager and JSON research are confirmed. | core/event/EventHandler.java:754-761; api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java:115-123 (setTicksToUpdate(getTickRate()/slownessFactor)) and 74-106 (tiers AI_BLOCKING, EVENT, STATE_BLOCKING, current); AbstractEntityAIStructure.java:705-720 stage arrays; CitizenData.java:935 updateEntityIfNecessary; ResearchListener.java:157 'researches'; superclass scan: AbstractEntityAISkill extends AbstractEntityAIBasic, AbstractEntityAIInteract extends AbstractEntityAISkill. |
| corrected | Structurize latest: 1.0.835-1.21.1-snapshot, 2026-09-30. | That file is a beta (snapshot). The latest release is 1.0.832-1.21.1, from 2026-08-09. | cfwidget minecraft/mc-mods/structurize files (type beta 2026-09-30; release 2026-08-09). |
| confirmed | Villager Recruits (1.15.2, 2026-06-29), Villager Workers, Siege Weapons and Small Ships are ARR; all Forge 1.20.1 only except Small Ships with NeoForge/Fabric 1.21.1 builds (2.0.2, 2026-10-16). | Addition: Small Ships also has a 1.21.4 beta (2.0.0-b2.1, 2025-05-16). | Modrinth API: villager-recruits, villager-workers, siegeweapons, small-ships all LicenseRef-All-Rights-Reserved; version lists (recruits 1.15.2 2026-06-29, workers 2.0.3 2026-07-01, siegeweapons 0.2.5 2026-04-10, small-ships 2.0.2-neoforge-1.21.1 2026-10-16, 2.0.0-b2.1 for 1.21.4). |
| corrected | Recruits: daily commits on main (2026-10-19); async pathfinding off by default thread-wise (UseAsyncPathfinding, 1 thread), from Petal (GPL-3.0). | There were no commits between 2026-06-29 and 2026-10-16; work resumed on 2026-10-16 and 2026-10-19. Async pathfinding is on by default (UseAsyncPathfinding = true) and uses one thread (AsyncPathfindingThreadsCount = 1). It does come from Petal, which is GPL-3.0 and archived. | GitHub API commits?per_page=40 (dates: 11 on 2026-10-19, 2 on 2026-10-16, then 2026-06-29 and earlier); config/RecruitsServerConfig.java:794-811; pathfinding/AsyncPathNavigation.java 'petal start'; GitHub API Bloom-host/Petal license GPL-3.0, archived; README 'Petal is licensed under GPLv3'. |
| confirmed | Recruits command model: R command screen, targets via player.pick(100-200), formations (line, square, triangle, circle, hollow circle, hollow square, V; spacing 1.75), UUID groups, factions/diplomacy on vanilla teams, leaders force-load chunks; limits 100 recruits/player, 500 NPCs/faction, 50 claims. | Addition: messengers are chunk loaders too, and the forced-chunk tickets are non-ticking (ticking=false). | init/ModShortcuts.java:17-19 (R/U/M); CommandEvents.java:70,135,350,427 pick(200/100); util/FormationUtils.java:18 spacing 1.75 + formation methods; world/RecruitsDiplomacyManager.java:68-71; entities/AbstractChunkLoaderEntity.java:94 forceChunk(..., add, false); AbstractLeaderEntity and MessengerEntity extend it; RecruitsServerConfig.java:197, 721, 844. |
| confirmed | Millénaire 9.0.2 (2026-09-18) runs on NeoForge 1.21.1 under a custom license; Java source not public; GitLab holds only content (PNG layer plans, text configs); maintained by kinniken and FireController1847. | Nuance: the public GitLab content repo has no commits since 2018-07-07 ('Update to 6.2.8'). It shows the old data format and may not match 9.x. CurseForge lists kinniken (owner), MoonCutter2B (author) and FireController1847 (maintainer). | cfwidget millenaire files (9.0.2 R 2026-09-18 NeoForge 1.21.1; 9.0.0-beta.2 2026-07-25); CurseForge page (Custom License; members); GitLab API projects/5632577: tree only millenaire/ and millenaire-custom/, master last commit 2018-07-07; carpenterhouse_A0-A3.png and carpenterhouse_A.txt 'max:2;priority:800;...'. |
| confirmed | Ancient Warfare 2 (GPL-3.0-or-later) has been dead since 2.7.0.1038 for 1.12.2 (2021-04-29); command baton and order items (work, routing, upkeep, combat, trade). |  | cfwidget ancient-warfare-2 newest file ancientwarfare-1.12.2-2.7.0.1038.jar 2021-04-29; README 'either version 3 of the License, or (at your option) any later version'; GitHub pushed_at 2021-11-12; NpcCommand.java CommandType + TODO refactor; orders/ CombatOrder, RoutingOrder, TradeOrder, UpkeepOrder, WorkOrder; issues #1365 and #1410 open. |
| confirmed | Baritone (LGPL-3.0) has 1.21.1 builds (v1.11.3) for Forge, Fabric and NeoForge, plans for one 1x2 agent, horses 2x3 may never be supported; Automatone stops at MC 1.20, last pushed 2024-01-21. | Precision: Automatone's last release, 0.11.0, targets MC 1.20.1. MineFortress ships its own Automatone fork (1.0.9-minefortress) for 1.20.2. | GitHub release v1.11.3 (2026-09-01) body 'For Minecraft 1.21 and 1.21.1. Forge is supported. Fabric is supported. NeoForge is supported.'; branch 1.21.1 FEATURES.md (2-bit chunk cache, segmented calc, cost backoff; 'Horses (2x3 path instead of 1x2)' under 'may not ever have'); Automatone branches up to 1.20, 1.20/gradle.properties mod_version 0.11.0 minecraft_version 1.20.1, release 0.11.0 2024-01-21; minefortress build.gradle:95. |
| corrected | i113w's Camera Lib: MIT, NeoForge 1.21.1 from 21.1.209, client-only, ~1.6k lines, v0.0.x; camera entity, optional ortho projection, inverse-MVP ray casting, box-select events; Better Mine Team (MIT) builds RTS command on top. | It is built against NeoForge 21.1.209 but declares NeoForge [21,) and Minecraft [1.21,1.22). The newest 1.21.1 build is 0.0.3-alpha3 (2026-06-09). GitHub has only the 1.21.1 main branch, so the 1.20.1 and 26.1.2 builds have no public source. It frees the cursor with MouseHandler#releaseMouse and turns its frustum bypass off under Sodium, Embeddium or Rubidium. Better Mine Team is an MIT rewrite of XiaoHuNao's MineTeam ('developed with AI assistance'). Its RTS code is on branch neo-1.21.1-dev2, not on main. | cameralib gradle.properties (neo_version=21.1.209, neo_version_range=[21,), minecraft_version_range=[1.21,1.22)); 25 files / 1,648 lines; Modrinth i113ws-camera-lib (MIT, 559 downloads, client required/server unsupported, versions list); git ls-remote BetterMineTeam heads (main, neo-1.21.1-dev, neo-1.21.1-dev2, neo-26.1.2-dev1, forge-1.20.1); bmt-dev2 build.gradle:131 curse.maven i113w-camera-lib; LICENSE 'Copyright (c) 2024 XiaoHuNao'; Modrinth bettermineteam body (box select, move/attack, formation support, Attack Move). |
| corrected | In vanilla 1.21.1 entity tracking pairs through Entity#broadcastToPlayer and re-evaluates only on chunk-section changes; PlayerEvent.StartTracking is not cancellable; gate own entities via broadcastToPlayer and force re-tracking when vision changes. | updatePlayer is also called from ChunkMap.move(ServerPlayer) for every tracked entity. That method runs on each accepted player-movement packet (an idle client still sends one at least every 20 ticks), on vehicle moves, while spectating a camera entity, and when entities or players are added. Pairing is therefore re-evaluated about once per second per viewer even without section changes. Forcing re-evaluation when vision changes is still advisable for timely hide and reveal. StartTracking is not cancellable and broadcastToPlayer defaults to true; both confirmed. | mc-src ChunkMap.java:1151-1180 (tick), 1023-1031 (move), 1108-1131 (addEntity), 1327-1345 (updatePlayer); ServerChunkCache.java:464-467; ServerGamePacketListenerImpl.java:460, 891, 970; LocalPlayer.java:283-284; ServerPlayer.java:524-528; Entity.java:3021; NeoForge PlayerEvent.java:261 (StartTracking extends PlayerEvent, no ICancellableEvent), EventHooks.java:479. |
| confirmed | Vanilla 1.21.1 PathNavigation is synchronous on the server thread, region = follow range + 8/16, node budget floor(FOLLOW_RANGE*16) x multiplier. | Nuances: the budget is fixed when the PathNavigation is constructed. The base FOLLOW_RANGE in Mob.createMobAttributes is 16, but many mobs override it (Zombie 35, Villager 48). RoN raises it to 64. | mc-src PathNavigation.java:65, 117-143 (offsets 8/16), 155-169; PathFinder.java:70; Mob.java:160; Zombie.java:122; Villager.java:270; RoN unit/interfaces/Unit.java:111 FOLLOW_RANGE_IMPROVED = 64. |
| corrected | Minecraft Legends (2023-04-18): Metacritic PC 65 from 33 critics, user 5.4; OpenCritic 70, 41% recommend; review criticisms; August 2023 patch; content ended January 2024 with the November 2023 update the last major one (Nintendo Life). | The current Metacritic PC page shows 65 from 36 critic reviews and a user score of 5.1 from 85 ratings; the legacy page now returns 403. The Nintendo Life news item (2024-01-10) says no new content will come and names Snow vs Snouts as the final Lost Legends drop, but it does not name a last major update. TrueAchievements calls the November update the last one, and other outlets disagree. OpenCritic (106 reviews), the review quotes and the patch notes are confirmed. | https://opencritic.com/game/14349/minecraft-legends (70, 41%, 106 reviews, April 18 2023); https://www.metacritic.com/game/minecraft-legends/critic-reviews/?platform=pc and /user-reviews/?platform=pc; Nintendo Life review 2023-04-25 (4/10, quotes); Kotaku review 2023-04-14 (quotes); Nintendo Everything 2023-08-16 patch 1.17.50310 notes; Nintendo Life news 2024-01-10; web search (Gfinity, MP1st, TrueAchievements). |
| confirmed | FrostedHeart (GPL-3.0, Forge 1.20.1) is the only flow-field precedent: 64x64 heightmap reverse-BFS field per target chunk, off-thread, shared, rebuilt every 2,400 ticks, max 2 new builds/tick, height step > 1 blocks; no studied mod uses HPA*. |  | raw master content/town/citizen/nav/FlowFieldCache.java:30-56 (MAX_NEW_PER_TICK 2, INSTALL_PER_TICK 4, REFRESH_INTERVAL 2400, MOTION_BLOCKING_NO_LEAVES snapshot), FlowField.java:43 SIZE 64, :106 abs(nh-ch) > 1; gradle.properties minecraft_version 1.20.1, forge 47.3.0; GitHub API license GPL-3.0; no hierarchical layer in RoN unit/pathfinding (15 files) or MineColonies pathfinding. |
| confirmed | Testing is rare: RoN has one test class, MineColonies version/1.21 has no src/test; RTS Building (LGPL-3.0, NeoForge 21.1.238, ModDevGradle 2.0.140) has ~306 JUnit 5/Mockito test files plus GameTest server runs. |  | RoN src/test/java/.../util/MiscUtilTest.java only; minecolonies src/ = datagen, main; rtsbuilding: 306 .java under src/test (unit, contract, performance), build.gradle:4 net.neoforged.moddev 2.0.140, :77-86 gameTestServer, :166-169 junit-jupiter 5.11.0 + mockito 5.12.0; gradle.properties neo_version=21.1.238. |
| confirmed | Guard Villagers' code is MIT, its assets ARR and may not be used for 'any ai-directed version ports'. |  | https://raw.githubusercontent.com/seymourimadeit/guardvillagers/main/LICENSE (/src/main/resources/ ARR with 'These assets may not be used for any ai-directed version ports'; other files MIT); Modrinth guard-villagers 2.4.12 (1.21.1, 2026-08-30), 5.0.0 (26.3, 2026-10-05). |
| corrected | RTS Building license: LGPL-3.0-only code, ARR assets. | README and LICENSE confirm LGPL-3.0-only code and ARR assets. The repo also still contains an older 'RTS Building User Terms 1.0' (License.md). It requires projects that use its code to be open source and bans charging for distribution, and the README says earlier releases stay under the terms they shipped with. The licence situation is ambiguous enough that code should not be copied without clarification. Modrinth also lists a Fabric 1.21.1 alpha and a 1.12.2 Forge port. | rtsbuilding README.md:8-14, LICENSE (LGPL v3), LICENSE-ASSETS (ARR), License.md section 2.4; Modrinth rts-building-build-from-above versions 1.1.6-patch2-fabric-alpha.1 (1.21.1 fabric), 1.1.6-1.12.2-port. |
| corrected | MCA Reborn: 1.21.1 NeoForge/Fabric, 7.7.36 (2026-08-16), GPL-3.0-only (listed as newest MC/loader). | The 1.21.1 build is confirmed, but 1.21.1 is not its newest target: 8.1.11 for 26.2 came out on 2026-08-29, and 1.20.1 betas continue (2026-10-18). | Modrinth minecraft-comes-alive-reborn versions. |
| corrected | Easy NPC is MIT (1.21.1 NeoForge/Forge/Fabric, 7.16.0, 2026-10-17). | Only the code is MIT. The licence explicitly excludes images, models and other assets. | https://raw.githubusercontent.com/MarkusBordihn/BOs-Easy-NPC/master/LICENSE.md; Modrinth easy-npc 7.16.0 2026-10-17. |
| corrected | Tale of Kingdoms (LGPL-3.0, Fabric, 1.21.1 alpha, 2024-09). | 1.0.6 'for 1.21.1' (2024-09-04) is a release, not an alpha. | Modrinth tale-of-kingdoms-a-new-conquest versions (version_type release). |
| corrected | Ages of Dominion: 26.1.2 NeoForge (ModJam 2026), 4.2.0 2026-09-09, license not checked. | The licence is Custom and currently 'All Rights Reserved', with no source link. The mod is singleplayer-only and has AoE-style Dark, Feudal and Castle ages. | https://www.curseforge.com/minecraft/mc-mods/ages-of-dominion (Custom License, ARR, no source, author Hyrrx); cfwidget files (v4.2.0 2026-09-09, NeoForge 26.1.2); description mentions 'CurseForge ModJam 2026' and 'single-player only'. |
| confirmed | Hundred Years Warfare: ARR, closed, 1.20.1/1.21.1, ~178k downloads; RTS mode with hotkeys, formations, shift commands; counters; workers based on AW2; 'not an RTS-focused mod'. |  | Modrinth hundred-years-warfare: LicenseRef-All-Rights-Reserved, 177,775 downloads, no source_url, body lines 5, 14, 16, 18, 34; versions 0.7.1r-fix1 2026-08-20. |
| confirmed | Siege of Settlements is labelled LGPL although derived from GPL code; Boobcat Edition has a GPL listing without source. |  | Modrinth siege-of-settlements license LGPL-3.0-only, source WardenMine242/rts-reign-of-nether-plus whose master LICENSE.txt is GPL v3 and README is RoN's; Modrinth reign-of-nether-boobcat-edition GPL-3.0-only, no source_url, body claims multithreaded A* with flow-field smoothing. |
| confirmed | GameRenderer#getProjectionMatrix(double) (line 980) is also used for the culling frustum; NeoForge offers ComputeCameraAngles, ComputeFov and CalculateDetachedCameraDistanceEvent but no projection-replacement event. |  | mc-src GameRenderer.java:980-993 (no hook), :1276 prepareCullFrustum(..., getProjectionMatrix(...)); NeoForge client/event/ViewportEvent.java subclasses RenderFog, ComputeFogColor, ComputeCameraAngles, ComputeFov; CalculateDetachedCameraDistanceEvent.java; RenderLevelStageEvent only exposes a getter. |
| confirmed | TicketController#forceChunk(level, BlockPos \| Entity \| UUID owner, x, z, add, ticking). | Additions: TicketController is a record registered through RegisterTicketControllersEvent. Entity owners are stored by UUID. ticking=true makes the chunk receive full ticks with no player nearby. | mc-src net/neoforged/neoforge/common/world/chunk/TicketController.java:27, 47-68; ForcedChunkManager.java:111-121 (addRegionTicket distance 2). |
| confirmed | ServerPlayer#setCamera and getCamera exist (spectate-style camera body). | Addition: while a camera entity is set, ServerPlayer.tick moves the player to it every tick (absMoveTo plus chunk-source move), so chunk loading and entity tracking follow the camera. | mc-src ServerPlayer.java:524-528, 1734-1748. |
| confirmed | MineFortress: MIT, Fabric 1.20.1/1.20.2, dormant since 2025-08-09; picking by redirecting getRotationVec; one Automatone instance per pawn; Kotlin + Yarn. |  | Fresh clone: LICENSE MIT; gradle.properties minecraft_version 1.20.2, yarn 1.20.2+build.4; 175 .kt files; FortressGameRendererMixin.java:96 @Redirect getRotationVec; BaritoneMoveControl.java:38 BaritoneAPI.getProvider().getBaritone(entity); GitHub pushed_at 2025-08-09. |
| confirmed | Licences of the remaining projects: Towny CC BY-NC-ND 3.0; Domum Ornamentum GPL-3.0; SmartBrainLib MPL-2.0 (1.16.11 for 1.21.1); Human Companions GPL-3.0 (1.7.6, 2024-12-15); Taterzens MIT with no 1.21.1 build; Custom NPCs closed, newest CurseForge file 1.16.5 (2022-05-15). |  | Towny LICENSE.md and README; ldtteam/Domum-Ornamentum master LICENSE (GPL v3); Tslat/SmartBrainLib main LICENSE (MPL 2.0) + Modrinth 1.16.11 2025-10-01; cfwidget human-companions 1.7.6; Modrinth taterzens versions (none for 1.21.1); cfwidget custom-npcs, Modrinth customnpcs_ CC-BY-NC-4.0. |
| confirmed | MineColonies raids: barbarian, pirate and Norsemen ships, drowned pirates, Egyptian, Amazon; EventStatus STARTING -> PREPARING -> PROGRESSING -> DONE/CANCELED; raiders camp at campfires; maxCitizenPerColony 250, maxRaiders 80. | EventStatus also has WAITING. PathJobRaiderPathing is used by RaidManager to plan raid routes. | core/colony/events/raid/* directories; api/colony/colonyEvents/EventStatus.java:8-13; HordeRaidEvent.java:242; ServerConfiguration.java:134, 179; RaidManager.java:474. |
| unverifiable | 80.lv (2025-06-27) praise quote; Upcomer command list; Moddex review rates performance below gameplay. | The 80.lv article (27 June 2025, 'transforms it into an entirely new experience') and the Upcomer command list are confirmed; Upcomer also lists Group Charge and Group Focus Target. The Moddex review could not be fetched and stays unverified. | https://80.lv/articles/check-out-this-mod-that-reimagines-minecraft-as-rts; https://upcomer.com/how-to-control-your-mobs-in-minecraft-legends; Moddex not fetched. |
| unverifiable | PR #445's '200 workers mining... held 66 TPS'. | The quote is exact, but its meaning is ambiguous (vanilla caps at 20 TPS), so the figure cannot be checked. | PR #445 body. |
