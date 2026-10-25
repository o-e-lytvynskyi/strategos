# Minecraft mods on NeoForge 1.21.1

Facts and pitfalls of the toolchain, tests and licences for Minecraft Java mods. The reference
is this repository (an RTS mod): [02-tooling.md](../02-tooling.md),
[05-testing.md](../05-testing.md), [research/](../research/) (verified reports with sources).
Saves (`SavedData`) — [minecraft-mod-data.md](minecraft-mod-data.md), GameTest and measurements —
[minecraft-mod-gametest.md](minecraft-mod-gametest.md), client (window focus, screenshots, frame,
Xvfb) — [minecraft-mod-client.md](minecraft-mod-client.md), build (Gradle, MDG, Spotless,
ErrorProne) — [minecraft-mod-build.md](minecraft-mod-build.md).

## Versions and toolchain (checked 2026-10-20)

- 1.21.1 is the main version of the NeoForge ecosystem: Modrinth has 21.5k mods and 2.2k
  modpacks for it, against 9.7k and 99 for all 26.x together. The 21.1 branch is alive: about
  ten builds in October 2026; 21.1.262 closes three GitHub advisories on networking, 21.1.264
  syncs attachments with a per-player filter and on the first send.
- 26.x (26.1 on 24.03.2026, 26.3 on 15.09.2026) needs Java 25 and ships without obfuscation,
  but it breaks every quarter (rendering, GUI, input).
- The official MDK `NeoForgeMDKs/MDK-1.21.1-ModDevGradle`: Gradle 9.2.1, ModDevGradle 2.0.148,
  Parchment 1.21.1/2024.11.17, foojay-resolver 1.0.0. A cold build on an M4 Pro takes 98 s; the
  Gradle caches are about 1 GB.
- Gradle installs JDK 21 itself through foojay into `~/.gradle/jdks` (Temurin 21.0.12). Do not
  install `openjdk@21` from Homebrew: keg-only JDKs get mixed up in the lookup. The daemon can
  run on the Homebrew JDK 25 (Gradle >= 9.1), but it is better to pin it to 21 with
  `gradle/gradle-daemon-jvm.properties`.

## Server config: read it no earlier than `ServerAboutToStartEvent`

A `SERVER` config (`ModConfigSpec`) is loaded from the world's `serverconfig/` right before
`ServerAboutToStartEvent` (ServerLifecycleHooks.java:96), the same way on a dedicated, an
integrated and a GameTest server; an earlier `.get()` throws. Register with
`ModContainer#registerConfig(Type, spec, name)`; FML passes the container to the `@Mod`
constructor together with the bus: `IEventBus`, `ModContainer`, `FMLModContainer`, `Dist` are
allowed in any order (FMLModContainer#constructMod, javap). There is no public list of registered
configs: the `ConfigTracker` maps are package-private, so the inventory test reads its own list,
from which the mod also registers.

## Brigadier: two `register(literal("x"))` merge into one root; `word()` and `=`

- A second `dispatcher.register(literal("strategos")...)` does not replace the first:
  `CommandNode.addChild` merges its children into the existing node. `requires` stays from the
  first root.
- So subcommands can be registered from a separate class without touching the main one.
- `StringArgumentType.word()` takes only `[0-9A-Za-z_\-.+]`: an argument like `seed=3` stops at
  `=`, and the command silently finds no branch ("Incorrect argument"). For `key=value` in the
  tail use `greedyString()` and parse it yourself (or `string()` with quotes).

## A block batch with `UPDATE_KNOWN_SHAPE`: what still changes the states

The flags `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` silence neighbour updates, but not `onPlace`:
sand and gravel schedule a falling tick, water and lava schedule a flow tick (place them after
the supports, liquids last, into closed banks). Any `updateShape` on leaves schedules a tick, and
on it `distance` is recomputed, even with `persistent=true` — so a shape pass over the edge of an
imported piece breaks a byte-for-byte comparison (in Strategos the map hash did not match). Grass
under an opaque block turns into dirt on a random tick; non-persistent leaves without logs decay.
Air is not written into tiles, so an import is placed only into empty space.

## Chunk unload: the leave event has no reason, and the entity can come back

A real unload sends `EntityLeaveLevelEvent` when the chunk section becomes HIDDEN
(`stopTracking`), and `getRemovalReason()` is still `null` at that moment: `UNLOADED_TO_CHUNK` is
set later by `unloadEntity`, silently. If nothing is done on `null`, the entity stays in the
hidden section and is tracked again when the chunk comes back — without an enter event. Your own
body bookkeeping ("one body per unit") must treat the body as stale on `null` and remove it
outside the engine's section flow (on the next tick); otherwise there are two bodies after the
chunk returns.

## A `MISC` entity without attributes does not complain at startup

`DefaultAttributes.validate()` checks the attributes of all types except `MobCategory.MISC`. A
`LivingEntity` with the `MISC` category (convenient for units: no spawner costs) that is not
registered in `EntityAttributeCreationEvent` passes startup silently and fails only on spawn.
Register the attributes explicitly and cover the spawn with a test.

## Block protection by events: where cancelling does not work

`EventHooks.fireFluidPlaceBlockEvent` returns `event.getNewState()` and ignores cancellation: to
stop a liquid from turning a block, the handler sets `setNewState(getOriginalState())`.
`EntityMobGriefingEvent` is not cancelled; it is decided with `setCanGrief(false)`.
`PistonEvent.Pre` is cancelled before `moveBlocks`, so the piston does not extend. In a GameTest
it is convenient to check the handlers through the hooks themselves (`EventHooks.onBlockPlace`,
`canEntityGrief`, `onEntityDestroyBlock`, `fireFluidPlaceBlockEvent`), and spawning by posting
`MobSpawnEvent.PositionCheck` and reading its `getResult()`: with `DEFAULT`, `checkSpawnPosition`
also applies the light rules, so a "before the match" control proves nothing. Block breaking —
`FakePlayerFactory.getMinecraft(level).gameMode.destroyBlock(pos)`: it posts `BreakEvent` and
returns `false` on cancel.

## jqwik >= 1.10 is not allowed

Since 1.10 the jqwik licence has an "Anti-AI Usage Clause", and 1.10.0 printed text aimed at
coding agents (LWN). Instead of property tests use `@ParameterizedTest` over a hundred seeds,
printing the seed on failure.

## Licences of other mods: ideas only

- Reign of Nether (RTS, Forge 1.20.1) — GPL-3.0-only; MineColonies, Structurize, BlockUI —
  GPL-3.0; Villager Recruits — all rights reserved; 0 A.D. — code and data GPL-2.0+, art
  CC BY-SA 3.0.
- By the FSF position, a GPL plugin to a closed host (Minecraft) is already a violation, so
  "let us make the mod GPL" is not a way out.
- An agent with GPL code in its context may reproduce it, so implementation sessions do not open
  such repositories — only behavioural notes. MIT/Apache (i113w Camera Lib, Freecam, PathWeaver,
  RVO2) are fine, with a NOTICE.
