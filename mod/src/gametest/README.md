# GameTests (L3)

`./gradlew :mod:runGameTestServer` runs every suite in a fresh GameTest server and writes
`mod/build/test-results/gametest/TEST-gametest.xml`. The Gradle guard fails the run when the
report is missing or empty, and prints the run's wall time against the 3-minute budget.

## The GameTest server's defaults

Minecraft 1.21.1 starts the GameTest server (`GameTestServer.java:64–69, 85`) with:

- creative mode and normal difficulty;
- a flat world (`WorldPresets.FLAT`);
- mob spawning, the weather cycle and fire tick off;
- random ticks at 0.

A test that relies on one of these behaving as in survival play must set it up itself.

The first run in a fresh `mod/build/gametest` logs a vanilla error, `Failed to load properties
from file: server.properties`. Vanilla `Main` reads `server.properties` from the run directory and
then writes a default copy there (`Main.java:123–126`), so later runs log no error. The GameTest
server builds its world from its own settings above; from the file it takes only `level-name`
(`world`, which the guard wipes) and the region-file compression.

## The control rule

A test of a guard against a vanilla mechanism first enables that mechanism and shows it working
on a control case outside the match, then shows the guard holding inside it. Without the control,
a guard test passes just as well when the mechanism is off in the test world. For example, a test
that units make no vibrations first shows a sculk sensor reacting to a vanilla mob.

## Writing a test

- A suite is a class in `suites/`, listed in `Suites`. Each `public static` method annotated
  `@StrategosTest` and taking a `GameTestHelper` is one test, named
  `<suite in lower case>.<method in snake_case>`.
- Arenas are SNBT files in `structures/`, written by `./gradlew :mod:generateArenas`; commit the
  output.
- `attempts > 1` needs a `retryReason` the owner approved (QA-04.4). A `solo` test, or any test in
  a `perf_*` batch, runs alone in its batch (QA-04.5). The generator rejects both mistakes before
  the server starts.
- `-Pgametest.filter=<regex>` runs only the matching tests.
