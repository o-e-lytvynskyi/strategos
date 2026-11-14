# S13: the arena world from code

*Written with M6 T11 on 2026-11-13. Spec: [M6 §2.9](../milestones/M6-first-skirmish.md#29-the-arena-world-from-code-match-14);
card MATCH-14.*

## Question and exit criterion

Can a client make the `strategos:arena` world from code when it is missing, open it again when it
exists, and get the arena rules exactly once, so that MATCH-02's **Skirmish** button never sends
the player to Create World?

Exit (MATCH-14): a scripted scenario creates the arena world from code, and it loads with the
preset's biome and the arena rules applied once (L4); this report states how MATCH-02 creates and
reuses the world (R).

## Setup

- **Machine:** Apple M1 Pro, 10 cores, macOS 15.7.3 on aarch64.
- **Software:** Temurin 21.0.12.1; NeoForge 21.1.264; no compat mods.
- **Tree:** `dev/dyadik/m6-t2-t17` after `c5b568d`, with T11 uncommitted.
- **Command:** `./gradlew :mod:runScenario -Pscenario=arena_world -Pscenario.timeoutSeconds=300`.
  The scenario game directory is synced fresh for every run, so the first launch always creates.

## What the scenario does

`ArenaWorldScenario` (devtools) opens every world through `ArenaWorldLauncher` instead of the
runner's flat creative world (`Scenario#openWorld`).

1. **Create.** No save named `strategos_skirmish`: the launcher calls
   `WorldOpenFlows#createFreshLevel` with the preset's dimensions. On the integrated server the
   scenario checks: `ArenaWorld.isArena`, the biome under the spawn is `strategos:arena`, stone
   under the spawn (the lobby pad), D-13's rules (no mob spawning, fire tick, mob griefing,
   weather cycle; random tick speed 0; no daylight cycle), and `ArenaWorld.onStarted` returned
   `APPLIED`. Then it turns the daylight cycle on, as an owner might, and leaves the world.
2. **Reopen.** The save exists: the launcher calls `WorldOpenFlows#openWorld`. The checks: the same
   arena, biome and pad, `onStarted` returned `ALREADY_APPLIED`, the owner's daylight cycle is still
   on (the rules were not applied again), NeoForge's confirmed-experimental flag is set, and there
   is still exactly one save whose name starts with `strategos_skirmish`.

## Results

Green on its first run: the whole scenario took 10 s from the title screen, both worlds included.

| | Create | Reopen |
|---|---|---|
| `ArenaWorld.onStarted` | `APPLIED` | `ALREADY_APPLIED` |
| Game type, difficulty, cheats | adventure, peaceful, off | adventure, peaceful, off |
| Biome under the spawn, lobby pad, D-13's rules | yes | yes |
| The owner's daylight cycle kept | — | yes |
| Backup screen on opening | — | none |
| Saves named `strategos_skirmish*` | — | 1 |

The two frames (`arena_created`, `arena_reopened`) show the same lobby pad under the noon sky in
adventure mode; they are not goldens.

## How MATCH-02 creates and reuses the world

- **One save, by name.** Every skirmish plays in the save `strategos_skirmish`
  (`ArenaWorldLauncher.WORLD`): `LevelStorageSource#levelExists` decides between
  `openWorld(WORLD, onFail)` and `createFreshLevel(WORLD, settings, options, preset, parent)`. A
  skirmish is a new match in the next free slot of that world (MATCH-02), never a new world per
  map.
- **The settings** (`ArenaWorldLauncher.settings()`): display name "Strategos Skirmish", adventure
  mode (the lobby pad's blocks stay; `CommanderService` switches the player to spectator or
  adventure itself and restores the saved mode), peaceful, not hardcore, cheats off, the game's
  default rules (D-13's are applied by `ArenaWorld.onStarted`, once, recorded in the registry blob),
  `WorldDataConfiguration.DEFAULT`; world options with a random seed (the arena is flat), no
  structures, no bonus chest. The dimensions come from the `strategos:arena` world preset in the
  data-pack registries handed to `createFreshLevel`.
- **Cheats off has a consequence:** `/strategos` needs permission level 2, which a singleplayer
  host without cheats lacks. MATCH-02 starts the match through its own payload
  (`strategos:skirmish` → `Skirmishes.start`), never through the command; an owner who wants the
  debug commands opens the world to LAN with cheats.
- **The backup screen.** A world made from a data-pack preset has an experimental generator
  lifecycle: entries from a pack that is not vanilla's are `Lifecycle.experimental()`
  (`RegistryDataLoader`), and so is a non-vanilla dimension (`WorldDimensions#checkStability`).
  `openWorld` then asks for a backup on every opening (`WorldOpenFlows#openWorldCheckWorldStemCompatibility`)
  unless NeoForge's flag is set. The launcher sets `PrimaryLevelData#withConfirmedWarning(true)` on
  the server thread once it has checked the world is an arena; the flag is saved in `level.dat` as
  `confirmedExperimentalSettings`, and the reopen above showed no screen.
- **Failure paths.**
  - `createFreshLevel` that cannot load the data packs returns to the launch's screen (vanilla:
    `setScreen(lastScreen)`); `openWorld` that cannot read the save runs `onFail`, which returns to
    the launch's screen too.
  - **A save of that name that is not an arena** (an owner's own world called
    `strategos_skirmish`): on joining, the launcher asks the integrated server `ArenaWorld.isArena`;
    if not, the client disconnects and shows "Not an arena world" (`strategos.skirmish.not_arena`),
    whose OK returns to the launch's screen. The world is not touched: `ArenaWorld.onStarted`
    leaves a non-arena world alone. This path ran in no scenario; MATCH-02's L4 run can add it.

## Follow-ups

- MATCH-02 (T13): `SkirmishScreen`'s **Start** calls `StrategosClient.arena().launch(mc, screen)`;
  the `strategos:skirmish` payload is sent on joining, after the launcher's check.
