# 05 — Testing

How every intermediate result of Strategos is tested: the levels, the commands, who runs what,
and the rules that keep the tests trustworthy. The decisions behind this page are
[ADR-0006](adr/0006-test-levels-and-definition-of-done.md) (levels and Definition of Done),
[ADR-0007](adr/0007-gametest-wiring.md) (GameTest wiring) and
[04-architecture.md §16](04-architecture.md) (testing seams). The evidence is in
[research/testing.md](research/testing.md).

M0 builds every harness on this page. The task names and options below are the ones the M0
implementation spec defines ([milestones/M0-foundation.md](milestones/M0-foundation.md) §4, §7
and §8). They exist once M0 is built; until then they are the target interface, not yet working
tasks. The few that arrive after M0 say when.

## Levels

| Level | What it proves | Command | Typical time *(estimate)* | Runs it |
|---|---|---|---|---|
| **L0** static | Formatting, compilation, ErrorProne + NullAway (JSpecify), ArchUnit boundary and determinism rules, the version catalog and the jar contents | `./gradlew checkVersionCatalog spotlessCheck compileJava :mod:archTest :mod:checkJarContents`; the core ArchUnit rules run in `:core:test` | 10–60 s | assistant after every edit batch |
| **L1** core unit | All game rules and whole matches without Minecraft: state machines, modifiers, templates, pathfinding, economy, AI-versus-AI, replays, client maths and view-models | `./gradlew :core:test`; the seeded soak `:core:soak` and the GameTest journal replay `:core:replayJournals` run in `verify` | seconds | assistant, continuously |
| **L2** Minecraft unit | Codecs, payload sizes, migrations from fixture files, shipped data through the real loaders, registries, and the engine-surface inventory check, which reads the registered types and payloads ([ADR-0022](adr/0022-definition-of-done-amendments.md)) | `./gradlew :mod:test` | 15–40 s | assistant on adapter or data changes |
| **L3** GameTest | The adapters in a real server world: bodies, blocks, mock players, save and reload, fault injection, parallel matches | `./gradlew :mod:runGameTestServer` | 40–120 s | assistant before every commit |
| **L4** client scenario | HUD, camera and rendering on screen: a PNG plus JSON per shot | `./gradlew :mod:runScenario -Pscenario=<id>`; `:mod:scenarioDiff` and the goldens arrive with QA-18 in M2 | 45–120 s | **only with the owner's consent** (a game window opens and can take focus); CI under Xvfb |
| **L5** server smoke | The mod loads on a dedicated server with no client classes; a headless match runs | `./gradlew :mod:runServerSmoke` | 20–40 s | assistant before commit |
| **L6** performance | The budgets of [ADR-0019](adr/0019-performance-budgets.md); JMH regressions above 15 % fail | `./gradlew verifyPerf`: `:core:jmh` and `:core:jmhCompare`, then the perf GameTest batch alone in `:mod:runGameTestPerf` | minutes | assistant on hot paths; the owner approves baselines |
| **L7** manual | Feel, multiplayer, the production jar | a dedicated server and two clients (`runServer`, `runClientJoin`, `runClient2`, or `scripts/playtest.sh`), started by the owner; a plain `runClient` opens the title screen and joins nothing | — | owner at milestones |

Aggregate tasks:
- `./gradlew verifyFast` = L0 + L1, the inner loop: `checkVersionCatalog`, `checkFixtures`,
  `:core:check`, `:mod:spotlessCheck`, the mod's compiles of every source set, `:mod:archTest`
  and `:mod:checkJarContents`;
- `./gradlew verifyHarness`, part of `verify`, proves the guards: runs that feed each guard its
  broken input (`:mod:runGameTestEmptyFilter`, `:mod:runGameTestBrokenLoad`,
  `:mod:runServerSmokeNoMarker`, `:mod:runGameTestRetry`, `:mod:runGameTestRetryNoReason`,
  `:mod:runGameTestSharedBatch`, `:mod:runGameTestTicketLeak`), every ArchUnit rule on its
  violating fixture, `checkFixturesSelftest` and `:core:jmhSelftest`;
- `./gradlew verify` = `verifyFast` + `verifyHarness` + L2 (`:mod:check`) + `:core:soak` + L3
  (`:mod:runGameTestServer` and its report guard) + L5 (`:mod:runServerSmoke` and
  `:mod:runServerSmokeRestart` with their marker guards) + `:core:replayJournals`, the gate
  before a task is done and before a commit;
- `./gradlew verifyClient` = L4 (`:mod:runScenario`), with consent;
- `./gradlew verifyPerf` = L6 (`:core:jmh`, `:core:jmhCompare`, `:mod:runGameTestPerf`).

`verifySlow` exists from M0 for the GameTest batch `slow` (`:mod:runGameTestSlow`); map-scale
GameTests, `@Tag("slow")` L1 runs and the crowd soak join it from M1. `verifySoak` runs
`:core:soak` from M0 (it is also inside `verify`); the L3 samples of QA-12 (M6) and QA-16 (M10)
join it later. The roadmap's
[test aggregates](roadmap/M0-foundation.md#test-aggregates-and-wall-time-budgets) give the
contents and wall-time budget of every aggregate.

Useful options:
- `-Pgametest.filter='<regex>'` runs a subset of GameTests. Names are the suite class in lower
  case, a dot and the method in snake case, for example `bodytests.body_never_saved`, so
  `'^bodytests\.'` runs one suite. A filter that matches nothing fails the build.
- `-Dstrategos.seed=<n>` reruns one seed of a seeded L1 run.
- `-Pstrategos.selftest=<name>` starts a harness self-test whose run must fail: `retry`,
  `retryNoReason`, `sharedBatch`, `ticketLeak` and `failModLoad` for `runGameTestServer`,
  `crashOnStart` and `clientRef` for `runServerSmoke`.
- `./gradlew :mod:runSpike -Pspike=s1` (or `s2`) runs spike S1 or S2 on a self-terminating
  dedicated server; `-Pspike.jfr` adds a JFR recording.
- `-Pcompat` adds the client compatibility set (Sodium, Iris, Lithium and others) to a run, and
  `-Pcompat.server` the server set (Lithium, ModernFix, FerriteCore).
- `-Pjmh.include='<regex>'` limits `:core:jmh` to the matching benchmarks.

## Where tests live

| Path | Contents |
|---|---|
| `core/src/test` | L1: unit tests, seeded invariant runs, replays, golden journals |
| `core/src/testFixtures` | `SimHarness`, fakes (`FakeWorld`, `FakeBodies`, `FakeNet`), voxel fixtures, the shared scenario definitions |
| `core/src/jmh` | L6 microbenchmarks |
| `mod/src/test` | L2: FML-bootstrapped JUnit |
| `mod/src/gametest` | L3: GameTests, their generator and reporter; `structures/*.snbt` arenas |
| `mod/src/devtools` | L4: the client scenario harness (dev only, never shipped) |
| `mod/src/archTest` | ArchUnit rules on the compiled classes |
| `mod/src/scenario/golden/<platform>/` | approved golden images per platform |

## Rules that keep the tests trustworthy

1. **Test at the lowest level that can express the behaviour.** Game rules are tested in L1;
   GameTests prove the adapters, not the rules.
2. **One scenario, two runners.** A scenario defined in `core/testFixtures` runs in L1 against
   fakes and, through our generator, as a GameTest.
3. **Determinism is checked, not assumed.** Every behaviour GameTest exports its journal, and L1
   replays it to the same state hash. The same match must give the same hash with 0, 1 and 4 path
   threads.
4. **Seeded invariant runs instead of a property-testing library.** A `@ParameterizedTest` over
   200 seeds with random command streams; every failure prints its seed. jqwik ≥ 1.10 is banned:
   its licence carries an anti-AI clause.
5. **No verdict from an exit code alone.** The 1.21.1 GameTest server very likely exits 0 on an
   empty test set or a mod-loading error, and so does a crashed dev server. So:
   - `runGameTestServer` fails when its JUnit XML report is missing or has no `<testcase>`;
   - `runServerSmoke` succeeds only with its marker file and no new crash report;
   - the scenario harness has a watchdog.
6. **Every task ends on its own** and has a Gradle timeout. Interactive `runClient`,
   `runClient2` and `runServer` sessions are the owner's.
7. **Each GameTest owns its match**, bound to the test's bounds, so tests in a batch run in
   parallel safely. Arenas are SNBT files of at most 48 blocks per axis, with an explicit floor.
   Map-scale tests run alone in their own batch.
8. **Performance tests run alone** in their own invocation (`:mod:runGameTestPerf`, one test
   per batch). They measure our own systems' time, because the GameTest server ticks without
   sleeping and tests in a batch share ticks.
9. **Goldens and baselines change only with the owner's approval.** Thresholds are never
   raised and tests are never marked flaky to get a green build; a flaky test is a bug.

## Client checks and the owner's keyboard

A client window on the owner's Mac can take keyboard focus. That already happened with a
demo window that swallowed the owner's typing (recorded in the pet-projects knowledge base).
Therefore:
- Client logic is pure code in `core.client`, so L1 covers the camera rig maths, HUD layout,
  picking, selection and view-models. L4 only confirms the drawing.
- The assistant opens a client window only after asking the owner. Under decision D-22 (taken
  on 2026-10-21) consent is asked once per card or milestone batch; each milestone spec lists its
  consent batch. Spike S3 checks whether a focus-free launch is possible on macOS, or whether a
  Linux container with Xvfb can render the scenarios instead.
- On CI (decision D-23: a private GitHub repository with one Linux job), L4 runs under Xvfb with
  Mesa and its own Linux goldens, with no consent needed.

## Performance budgets

At 600 units, the multiplayer design scale of 4 × 150 ([ADR-0019](adr/0019-performance-budgets.md)):

| Budget | Target |
|---|---|
| A — core work per tick | ≤ 5 ms p50, ≤ 10 ms p99 |
| B — all Strategos-attributable server time | ≤ 17 ms p95 |
| C — whole server tick, including between-tick work | ≤ 23 ms p95 |
| D — path work on the main thread | ≤ 2 ms p99; a 100-unit order's path results within 5 ticks p95 |
| E — network per client | ≤ 100 KB/s |
| F — client frame, 200 units on screen | p50 ≤ 16.7 ms (60 fps), p99 ≤ 25 ms; our own drawing ≤ 3 ms; units ≤ 6 ms; client tick ≤ 1 ms |
| G — memory and saves | core state ≤ 64 MB per 384² match; nav ≤ 32 MB at 512²; autosave ≤ 100 ms |

These are estimates until spikes S1, S2 and S4 measure them. Each milestone's perf gate
asserts the budgets at that milestone's target scale, from the
[budget matrix](roadmap/M0-foundation.md#budget-matrix-per-milestone-delivered-and-kept-by-perf-01);
a stress pass at twice that scale asserts only the whole tick, C p95 ≤ 50 ms, after 200 warm-up
ticks.

## Definition of Done

The table of required levels per change type is in
[ADR-0006](adr/0006-test-levels-and-definition-of-done.md). [ROADMAP.md](ROADMAP.md) ("Rules every
card follows") applies it with the two amendments recorded in
[ADR-0022](adr/0022-definition-of-done-amendments.md) when the owner approved the roadmap on
2026-10-21:
- **networking changes** need L3 with mock players and L5 before merge, and L7 at the milestone
  exit, not L7 before every merge;
- **build and tooling changes** need L4 only once QA-06 exists, and then with the owner's consent
  or on CI.

L0 and L5 run on every change, because both are part of `verify`. In short, a change is done
when its behaviour is tested at the lowest level that can express it, every required level
passes, there are no new ErrorProne or NullAway findings, and the affected docs and the knowledge
base are updated in the same session ([03-design-process.md](03-design-process.md)).
