# 05 — Testing

How every intermediate result of Strategos is tested: the levels, the commands, who runs what,
and the rules that keep the tests trustworthy. The decisions behind this page are
[ADR-0006](adr/0006-test-levels-and-definition-of-done.md) (levels and Definition of Done),
[ADR-0007](adr/0007-gametest-wiring.md) (GameTest wiring) and
[04-architecture.md §16](04-architecture.md) (testing seams). The evidence is in
[research/testing.md](research/testing.md).

M0 built every harness on this page ([milestones/M0-foundation.md](milestones/M0-foundation.md)
§4, §7 and §8); the few pieces that arrive later say when. The times are M0's, measured on the
owner's M4 Pro on 2026-10-22 ([S5](spikes/S5-build-wiring.md), check 13).

## Levels

| Level | What it proves | Command | Time in M0 | Runs it |
|---|---|---|---|---|
| **L0** static | Formatting, compilation, ErrorProne + NullAway (JSpecify), ArchUnit boundary and determinism rules, the version catalog and the jar contents | `./gradlew checkVersionCatalog spotlessCheck compileJava :mod:archTest :mod:checkJarContents`; the core ArchUnit rules run in `:core:test` | inside `verifyFast`: 8.6 s from clean, 0.4 s warm | assistant after every edit batch |
| **L1** core unit | All game rules and whole matches without Minecraft: state machines, modifiers, templates, pathfinding, economy, AI-versus-AI, replays, client maths and view-models | `./gradlew :core:test`; the seeded soak `:core:soak` and the GameTest journal replay `:core:replayJournals` run in `verify` | 616 tests in 3.6 s; the soak 9 s; 73 journals in 0.2 s | assistant, continuously |
| **L2** Minecraft unit | Codecs, payload sizes, migrations from fixture files, shipped data through the real loaders, registries, and the engine-surface inventory check, which reads the registered types and payloads ([ADR-0022](adr/0022-definition-of-done-amendments.md)) | `./gradlew :mod:test` | 45 tests, 4.0 s | assistant on adapter or data changes |
| **L3** GameTest | The adapters in a real server world: bodies, blocks, mock players, save and reload, fault injection, parallel matches | `./gradlew :mod:runGameTestServer` | 97 GameTests, 4.2 s of wall time | assistant before every commit |
| **L4** client scenario | HUD, camera and rendering on screen: a PNG plus JSON per shot | `./gradlew :mod:runScenario -Pscenario=<id>`; `:mod:scenarioDiff` and the goldens arrive with QA-18 in M2 | `smoke` 11–16 s on the Mac; 2–3 min as a CI job | **only with the owner's consent** (a game window opens and takes the focus); on CI under Xvfb by hand (`.github/workflows/scenario.yml`) |
| **L5** server smoke | The mod loads on a dedicated server with no client classes; a headless match runs | `./gradlew :mod:runServerSmoke`; `:mod:runServerSmokeRestart` adds the restart round trip | 11.5 s; 19.3 s with the restart | assistant before commit |
| **L6** performance | The budgets of [ADR-0019](adr/0019-performance-budgets.md); JMH regressions above 15 % fail | `./gradlew verifyPerf`: `:core:jmh` and `:core:jmhCompare`, then the perf GameTest batch alone in `:mod:runGameTestPerf` | JMH 83 s; the perf batch 6 s | assistant on hot paths; the owner approves baselines |
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
- `./gradlew verifyPerf` = L6 (`:core:jmh`, `:core:jmhCompare` against the owner-approved
  `perf/baseline-m4pro.json`, `:mod:runGameTestPerf`); 96 s. CI runs JMH, `:core:jmhSpeedRatio` and
  the perf GameTests at three times the budgets nightly (`.github/workflows/perf.yml`).

Measured in M0: `verify` 39 s from clean without the build cache and 31 s warm (budget 5
minutes), `verifyHarness` 12 s, `verifySlow` 6 s, `verifySoak` 9 s; CI runs `verify verifySlow
verifySoak` in 6 to 10 minutes.

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
- `-Pjmh.include='<regex>'` limits `:core:jmh` to the matching benchmarks; `-Pjmh.result=<path>`
  and `-Pjmh.baseline=<path>` (from the repository root) point `jmhCompare` at other files, and
  `-Pperf.machine=<id>` picks `perf/baseline-<id>.json` (default `m4pro`).
- `-Pperf.ciFactor=<f>` scales the perf GameTests' budgets (CI uses 3; 0.001 is the synthetic
  overload of T12), `-Pperf.gates=a,g` lists the gates that fail on a breach (the others only
  report), and `-Pperf.fakeForeignLoad=<x>` replaces the measured foreign CPU load, so 0.3 shows an
  `INVALID` run. Results go to `mod/build/reports/perf/`.
- The spikes write `mod/build/spike/out/<id>/` (`results.csv`, `results.md`, `env.json`, `DONE`),
  and `mod/build/spike/out/<id>-lithium/` with `-Pcompat.server`.
- `-Pscenario=<id>` picks the L4 scenario (`smoke`, `stall`, `world_switch`, `s4_units`),
  `-Pscenario.timeoutSeconds=<n>` the watchdog (default 180), `-Ps4.count=<n>` and
  `-Ps4.renderDistance=<d>` S4's units and render distance. Only with the owner's consent.
- `-Dstrategos.fixtures.update=true` (on `:core:test` or `:mod:test`) writes missing save
  fixtures and corrupted-corpus files, and `-Dstrategos.golden.update=true` rewrites the S6
  canary golden; both are rare, deliberate and reviewed (§ Fixture catalogue).

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

## Fixture catalogue

Every committed fixture, where it lives, what writes it and how it may change (QA-17.4; M0 spec
§7.10). Later cards add rows with their fixtures. `checkFixtures` (part of `verifyFast`) fails on
a fixture a test names that does not exist, on a released-schema fixture missing from disk (the
append-only list is `released-fixtures.txt` at the root) and on a literal seed in a test: seeds
come from the committed sets through `Seeds.set("<name>")` or `Seeds.first("<name>")` (QA-17.5).
`checkFixturesSelftest` (part of `verifyHarness`) proves the check catches all three on the
broken inputs in `gradle/selftest/fixtures/`.

| Kind | Location | Generator | Change rule |
|---|---|---|---|
| SNBT arenas | `mod/src/gametest/structures/arena_flat_<n>.snbt` | `:mod:generateArenas` | regenerate and commit; a new size is a new file |
| Scenario definitions | `core/src/testFixtures/.../testkit/Scenarios.java` | code | reviewed like code; GameTest names follow the scenario ids |
| Seed sets | `core/src/testFixtures/resources/seeds/<name>.txt` | written by hand | append-only; a test that needs other seeds adds a set. A harness built without a seed uses the test kit's default, 42 |
| S6 canary golden | `core/src/test/resources/golden/s6-canary.json` | `-Dstrategos.golden.update=true` | rewritten only with a recorded reason (R12) |
| Golden journals | `core/src/test/resources/journals/<name>.strj` and `REBASELINE.md` | `./gradlew :core:rebaselineJournals -Preason="…"` | re-recorded only with a reason, which the task appends to `REBASELINE.md` (CORE-11.3) |
| Save fixtures, one per released schema | `core/src/test/resources/fixtures/match_v<n>.json`, `mod/src/test/resources/fixtures/match_v<n>.nbt`, each with `match_v<n>.expected.json` | `-Dstrategos.fixtures.update=true`, once per schema | never changed or deleted; listed in `released-fixtures.txt` |
| Corrupted saves | `mod/src/gametest/resources/fixtures/corrupt/` | `CorruptCorpusTest` with `-Dstrategos.fixtures.update=true` | append-only; listed in `released-fixtures.txt` |
| Engine-surface inventory | `mod/src/test/resources/engine-surface.json` | written by hand | changes with the code it lists, reviewed (ADR-0020) |
| ArchUnit violating fixtures and stubs | `core/src/test/java/.../archfixture/`, `mod/src/archTest/java/` | written by hand | one per rule and per banned API; a new rule brings its fixture |
| JMH self-test results | `core/src/jmh/resources/selftest/` | written by hand | fixed |
| Harness self-test inputs | `gradle/selftest/fixtures/` | written by hand | fixed |
| JMH baseline | `perf/baseline-<machine>.json` | `:core:jmh`, copied after the owner approves | only with the owner's approval |
| GameTest journals (binary `.strj`), perf and smoke results | `mod/build/gametest/journals/`, `mod/build/reports/perf/`, `mod/build/server-smoke/` | every run | build output, never committed |

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
  consent batch, and a static scene is repeated two or three times (amended 2026-10-22). Spike
  [S3](spikes/S3-client-scenario-harness.md) found that every Mac run takes the focus for about
  15 s, and that the same scenarios render on Linux under Xvfb with no window.
- On CI (decision D-23: a private GitHub repository with one Linux job), L4 runs under Xvfb with
  Mesa's llvmpipe and its own Linux goldens, with no consent needed: `gh workflow run
  scenario.yml -f scenario=<id>`, the PNGs and logs in the run's artifact.
- A repeatable frame needs the pinned scene of S3: a fixed anchor, the time frozen at noon, no
  chat, the camera held with the mouse released, the vignette pinned; a capture at tick 60 or
  later, after the terrain has arrived; the units' idle animation still differs by a few dozen
  pixels, so QA-18 masks them or freezes the ticks.

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

S1 and S2 measured them in M0, far inside every budget (a 600-unit tick about 0.05 ms p50, a
384² arena idling at 0.06 ms); the owner approved keeping them all (ADR-0019, Accepted
2026-10-22); S4 then measured budget F for bare humanoids (200 cost 0.69 ms of the frame with
the client compat set), and F stays provisional for armoured units until ART-01. Each milestone's perf gate
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
