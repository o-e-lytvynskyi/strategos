# Minecraft mods: GameTest arena, coordinates, players in a test (NeoForge 1.21.1)

How the place where GameTest runs is built, and how to put a player into it. Launch, verdicts,
tickets and measurements are in [minecraft-mod-gametest.md](minecraft-mod-gametest.md). Reference
implementation: this repository.

## `GameTestHelper` coordinates start at the structure block, one block below the floor

`GameTestHelper#absolutePos` counts from `testInfo.getStructureBlockPos()`, and the structure block
is placed one block below the structure origin (`StructureUtils#prepareTestStructure` →
`createStructureBlock(..., blockpos.below(), ...)`). So `h.setBlock(new BlockPos(x, 1, z), …)` is
the arena floor layer, not the first layer above it. `getBounds().minY` is the floor too. The error
is invisible: the test builds terrain one block lower than intended and often passes anyway. Check
heights against `getBounds()`, not against relative coordinates.

## The GameTest arena is walled with barriers, the ground around is below the floor

`GameTestInfo#prepareTestStructure` calls `StructureUtils#encaseStructure`: `BARRIER` walls along
the structure perimeter for its whole height (a roof too, if the test has no `skyAccess`). Around
it, `clearSpaceForStructure` clears a box 2–3 blocks wider: stone below the structure-block layer,
air above it. The ground there is two blocks below the top of the floor, and beyond that the world
is as it is. A test where something walks out of the arena over the ground must lay a road through
the wall itself (`setBlock` outside the structure works; `removeBarriers` at the end removes the
remaining barriers).

A barrier roof stops a vanilla tree from growing: `ConfiguredFeature#place` for `TreeFeatures.*`
checks the free trunk height (`TreeFeature#getMaxFreeTreeHeight`) and under a roof at arena height
silently returns `false` ("the tree did not grow"). The roof is removed by the 11th argument of
`TestFunction`, `skyAccess` (in Strategos, the `skyAccess` field of `@StrategosTest`). Then raise
the match height band above the arena with your own `MatchSpace`, and make the test `solo`.

A barrier roof breaks the height map: `getHeight(MOTION_BLOCKING…)` in any arena column returns the
top above the roof, not the floor. Code that looks for "where one can stand" by the height map
always misses in GameTest (in Strategos, the landing point of the first person). Search from the
top down inside your own height band: a sturdy top from below (`isFaceSturdy(UP)`), and two blocks
above it with no collision and no fluid.

The height map of an unloaded chunk silently returns the world bottom: `Level#getHeight` with
`!hasChunk` returns `getMinBuildHeight()` (−64), not the floor of a superflat world (−60). A scene
that takes the floor away from the player (a demo plot, a second arena) builds the match 4 blocks
below the ground. After that nodes, standing height and height-based orders do not agree, and
workers idle without an explicit error. First `level.getChunk(x >> 4, z >> 4)`, then `getHeight`.

Tests of one batch run at the same time and share server services: a check "the failure counter
grew by 3" catches other tests' failures. Count by your own player or your own match.

## A big arena is a structure without blocks, the test lays the floor

For a GameTest wider than 48 blocks, SNBT with a floor comes to hundreds of kilobytes (a line per
block). A structure without blocks works: `{DataVersion: 3955, size: [96, 4, 96], data: [],
entities: [], palette: []}` loads and is placed like a normal one. `clearSpaceForStructure` still
clears the volume (and encloses it with barriers), and the test lays the floor itself with
`h.setBlock` at relative `y = 1` (9,216 calls take a fraction of a second). Reference: this
repository, `arena_air_96.snbt` and `pathtests.hierarchical_paths_follow_a_new_wall`.


## A player who hits: `FakePlayer`, not the helper's mock player

`GameTestHelper#makeMockServerPlayerInLevel` is `@Deprecated(forRemoval)` in 1.21.1. To hit an
entity use `FakePlayerFactory.get(level, new GameProfile(uuid, name))`: its `attack(entity)` goes
through vanilla `Player#attack` (with a "zero" attack timer a fist hits for 0.208), and `new
Arrow(level, fakePlayer, …)` + `shoot(…)` is a real arrow whose damage `getEntity()` is this
player. Reference: this repository, `fidelitytests.player_melee_and_arrow_journaled`.

## A mock player that sees what the client sees

A player on a `Connection` over an `EmbeddedChannel`, placed with `placeNewPlayer` (like
`GameTestHelper#makeMockServerPlayerInLevel`), receives packets as objects: `channel.readOutbound()`
returns them mixed with pipeline setup tasks (`UnconfiguredPipelineHandler`). Skip everything that
is not a `Packet`, and unpack bundles (`BundlePacket#subPackets`). The server sends chunks itself to
all players on the list (`MinecraftServer#tickChildren`), but in batches: without a reply to
`ClientboundChunkBatchFinishedPacket` the player gets one batch (≤ 9 chunks). Reply on the server
thread: `player.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(64))`.
Nobody calls the listener tick of such a player (no keep-alive, no movement checks), and you must
remove it yourself: `getPlayerList().remove(player)` and `channel.finishAndReleaseAll()`.
Reference: `gametest/net/MockViewer`.

## A player name is at most 16 characters

The protocol writes the profile name with a limit of 16: for a mock player with a longer name,
`player_info_update` cannot be encoded. A mock connection has no encoder, so the test passes
silently, and only the byte count through `GameProtocols.CLIENTBOUND_TEMPLATE` fails. It fails with
an exception in the server tick that brings down the whole GameTest server. Check the length when
creating the player.

## An operator in GameTest: permission level 0 until set explicitly

- `GameTestServer#getOperatorUserPermissionLevel()` returns 0, so `PlayerList.op(profile)` creates
  an entry of level 0, and `hasPermissions(2)` of the mock player stays false.
- A direct entry is needed: `getPlayerList().getOps().add(new ServerOpListEntry(profile, 2,
  false))`, and at the end `getOps().remove(profile)`. The entry is written to `ops.json` in the
  server directory and does not touch the world.

## Batches place their structures in the same spot

The test of the next batch stands where the test of the previous batch stood. While everything
happens inside its own structure, this is not visible. But a mock player that rode with a view
distance over a neighbouring spot leaves loaded chunks there, and a test that needs a chunk to
unload fails ("the far chunk still holds the body"). It fails stably and only in a full run.
Anything that goes beyond its own structure (a camera panorama, big match spaces, map imports)
should be moved thousands of blocks aside, each test to its own "lane".

## Blocks above the arena outlive the test

GameTest clears only a structure's bounding box before the next batch takes the same spot, so a
block a test sets above the arena's height stays in the world for whatever test lands there next.
`GameTestHelper` y 1 is the floor (y 0 the structure block): `arena_flat_48_high` takes six
blocks of platform at helper y 2..7 inside its eight. A match band may reach above the arena only
if nothing is placed there. Locally the world under `mod/build/gametest/world` survives between
runs as well.
