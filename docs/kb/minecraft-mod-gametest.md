# Minecraft mods: GameTest and measurements (NeoForge 1.21.1)

How the 1.21.1 GameTest server behaves and how to measure on it. The arena, coordinates and
players in a test are in [minecraft-mod-gametest-arena.md](minecraft-mod-gametest-arena.md). The
general toolchain is in [minecraft-mod.md](minecraft-mod.md), saves in
[minecraft-mod-data.md](minecraft-mod-data.md). Reference implementation: this repository,
`mod/build.gradle` (guards) and `mod/src/gametest/`.

## GameTest on 1.21.1: green does not mean passed

- An empty test set or a mod load error is caught in the server's `main()` and only logged. The
  process almost certainly exits with code 0.
- No report is written at all: install `GlobalTestReporter.replaceWith(...)` yourself.
- `attempts` and `requiredSuccesses` are swapped in `GameTestRegistry`.
- `neoforge.enabledGameTestNamespaces` silently drops tests whose template has no namespace.
- The tests of a batch run at the same time in one world. The start is random within
  ±15 million blocks (a float has a one-block step there), and ticks run without pauses.

Fix: your own `TestFunction` generator through `RegisterGameTestsEvent`, your own JUnit XML, and
a Gradle task that fails if there is no report or it has no `<testcase>`.
A crashed dev server also exits with 0, so the smoke run takes its verdict from a marker file.

## Re-runs (`maxAttempts > 1`) do not work on the 1.21.1 GameTest server

`GameTestServer#startTests` builds its `MultipleTestTracker` once, from the first attempts.
`GameTestRunner#rerunTest` makes a copy (`copyReset`) but does not tell the server's tracker about
it. After the first failure the tracker sees all tests as finished, the server stops before the
re-run and exits with that failure in its return code.
Symptom: the log has "Starting re-run of tests: …", right after it "GAME TESTS COMPLETE", and the
test is counted as failed.

The fix needs no mixin and no AT (reference: this repository,
`mod/src/gametest/.../RerunTracking.java`):
- A `GameTestListener` is attached to every test in `ServerTickEvent.Pre`. The tracker is created
  at the end of the previous tick, and the tests start ticking later, inside `tickChildren`.
- On `testAddedForRerun` it attaches itself to the copy and puts the copy in place of the old
  attempt in the tracker's list.
- The private `GameTestServer#testTracker` and `MultipleTestTracker#tests` are read by reflection,
  lazily in the handler. Not in static initialisation: the class is loaded when subscribers are
  registered, in all dev runs.

## A chunk two away from a forced one is not unloaded

GameTest force-loads the chunks of its structure (`StructureUtils.forceLoadChunks`, level 31).
Neighbours at distance 1 are level 32, at distance 2 level 33 (FULL): the chunk is loaded, the
entities in it are tracked but do not tick, and there is no unload event. A test "release the
ticket, the chunk unloads" must take a chunk at least three away from the structure. The start of
the test grid is random, so "72 blocks from a 32×32 arena" is sometimes two chunks and sometimes
three: the test is flaky in about half of the runs.

## `/tick freeze` stops GameTests too

`GameTestTicker` ticks only when `tickRateManager.runsNormally()` (`MinecraftServer#tickChildren`).
A test that freezes the server to check freeze/step hangs itself and the whole batch until the
timeout. Do such checks in L2 (JUnit against `TickRateManager`) or by hand.

## Ticket `ticking=false`: entities tick; `ticking=true`: random ticks too

`TicketController#forceChunk(level, owner, x, z, add, ticking)`: with `ticking=false` the chunk is
loaded and its entities tick without a player nearby, because vanilla `addRegionTicket` puts every
region ticket into `tickingTicketsTracker` as well (DistanceManager.java:197–202). `ticking=true`
(NeoForge `forceTicks`) adds random ticks and natural spawning without players (`shouldForceTicks`
in `tickChunks`). The level does not fall asleep in either case: NeoForge counts its own tickets in
the "empty level" check (ServerLevel.java:390). Checked on a dedicated server: a body with
`ticking=false` went through 11,999 ticks out of 12,000. If a body "froze", look for neighbours
(a chunk two away from a forced one is FULL, see above), not for the ticket mode.

## `forceChunk` generates the chunk synchronously: do not use it for mass generation

After the ticket, `TicketController#forceChunk(..., add=true, ...)` calls `level.getChunk(x, z)`
(ForcedChunkManager.java:96): the server thread waits for generation up to FULL. The Strategos S8
census forced 32 fresh chunks of vanilla terrain per tick. On the third 512² square the light
worker threw `MissingPaletteEntryException` on a ProtoChunk, and the server thread hung in
`getChunk` for good (2 % CPU, only the worker error in the log). SIGTERM does not stop such a
server, because the shutdown hook waits for the same thread. Only `kill -9` helps.

Do mass generation without blocking: your own `TicketType.create(name,
Comparator.comparingLong(ChunkPos::toLong))`, `ServerChunkCache#addRegionTicket(type, pos, 0, pos)`
(level 33 = FULL), at most ~128 chunks in flight, poll `getChunkNow(x, z)` every tick, then
`removeRegionTicket`. A square of 1,024 chunks with neighbours loads this way in half a minute
(M4 Pro). Make the "no progress" timeout wall-clock, not in ticks: a server in sprint
(`requestGameToSprint`) runs 6,000 empty ticks in under a second.

## Ticket: presence check and validation moment

`forceChunk(..., add=true, ...)` returns `false` if such a ticket already exists and changes
nothing (ForcedChunkManager.java:94, `TicketTracker#add` → `LongSet#add`); `add=false` returns
whether the ticket existed. The owner maps (`TicketOwner`) are package-private, so this is the only
check from outside. `LoadingValidationCallback` is called in `prepareLevels`, before
`ServerStartedEvent` (MinecraftServer.java:522): the callback reads its own `SavedData` through
`DimensionDataStorage#get`, and a later `get` returns the same cached instance.

## The GameTest server ticks without pauses: a timeout in ticks is seconds

`GameTestServer#waitUntilNextTick` only runs tasks and does not sleep: ticks go back to back,
hundreds per second. Waiting for asynchronous engine work (entities of a chunk loaded by the IO
worker, generation, light) lives in real time, and `timeoutTicks` for it must be counted in seconds
of a slow machine. On CI, 1,600 ticks ended in 3 s, before the entities of the forced chunk had
loaded (the test was flaky). Waiting for pure game logic is in ticks, as usual.

This is also a trap for measurements. Background threads (the pathfinding pool) get no time between
ticks, and the main thread waits for their jobs itself. The time spent on paths per tick comes out
several times higher than on a real server. Set the pace of 20 ticks per second in
`ServerTickEvent.Post`: wait until "tick start + 50 ms". Post fires after the tick time is already
recorded in `tickTimesNanos` (`MinecraftServer#tickServer`), so the pause is not in MSPT. For the
same reason your own work in Post (packet flush and the like) is not in MSPT either: to budget a
whole tick, measure it separately.

## The GameTest server exit code hides the guard's verdict

The server exits with a code equal to the number of failed required tests. `JavaExec` fails on a
non-zero code before the guard's `doLast`, and Gradle prints only "non-zero exit value 2", without
the test messages. If the verdict comes from a guard that reads the report (and it should: on an
empty set the server exits with 0), set `ignoreExitValue = true` and print the test name with its
`failure@message` from the report.

## CPU load: two measurements in a row are noise

`com.sun.management.OperatingSystemMXBean#getCpuLoad` and `getProcessCpuLoad` count from the
previous call. Ten calls within a few milliseconds give random values ("foreign" load of 0.28 on a
quiet machine). Open the measurement window with a discarded first call, then take samples at equal
intervals (every 20 ticks), not back to back.

## Blocks next to a walking unit: terrain pages lag

- The core sees placed blocks only after a page rebuild. In Strategos a page waits 4 quiet ticks,
  then a job for `pageK` ticks (`PageJobs`). Until then the unit walks on the old map.
- A wall two blocks in front of the unit (0.25 block per tick): the unit enters its column before
  the core learns about it, and the check "never stands in a wall" fails.
- In a test, place walls with a margin for this delay: we use ≥ 5 columns ahead.

## Match tick p95 on the server against a replay of the same journal

- A perf run writes a match journal. Replaying it in L1 (`MatchCore.restore` plus steps with your
  own clock) gives the same core work without the server. In Strategos (M4 T22) the p95 of a step
  was 2.1 ms on the server and 0.37 in the replay of the same match. The rest is environment
  (vanilla chunk ticking and GameTest between steps at 20/s), not the simulation. A JFR of the
  server thread confirmed it: the hot part is vanilla.
- So compare a per-unit budget only with the baseline of the same scene on the same map. Keep the
  report broken down into the core step, its phases and the mod part, otherwise there is nothing
  to attach growth to.

## Shared per-tick budgets: wait for the work, never a fixed delay

Two server-wide budgets serve every match of a batch in turn: the writer's 64 placements
(`BlockWriter`) and the 64 body creations (`SpawnBudget`). A test late in the batch's order
gets its blocks or bodies ticks later, and the order shifts whenever a test is added (names
sort). M5 T4: the reconciler test saved a half-written house ("rewrote 62"); wait for
`BlockWriter.pendingIn(level, match, min, max)` to be empty. M5 T6:
`materialises_one_body_per_unit` asserted after `runAfterDelay(2)` and saw "0 bodies"; wait
until `rt.bodies().count()` reaches the count, then assert.
