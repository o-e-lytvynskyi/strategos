# Worklog

Append-only session log, newest first. Each entry says:
- what was done (milestone tasks and cards);
- the results, with numbers;
- the problems met;
- what comes next.

The owner reads it to see what happened since the last visit; [STATE.md](STATE.md) shows where
things stand now. Commits are named `Mx Tn: …`, so `git log --grep "M0 T3"` finds a task's work.

## 2026-10-22 — the owner's setup, M0 T7c, T8

- **M0 T8 · Commands, the walking skeleton end to end, the playtest kit** — QA-04 and CORE-03 done;
  ORD-01, QA-05, QA-09, QA-10, CORE-01, CORE-02.
  - **Results:**
    - `StrategosCommands`: `/strategos match create|list|pause|resume|dispose`, `debug
      spawn|move|stop|replay`, `hash`, all at permission level 2; debug orders go through the
      inbox and reply after the step;
    - `RuntimeScenarioContext` and `ScenarioGameTests`: `scenarios.*` (3), the 20
      `scenarios.walking_skeleton_seed_*` in batch `skeleton` and the 50 `isolation.match_*` in
      batch `isolation`; each stops on the same tick with the same hash as its L1 twin and exports
      its journal;
    - `commandtests.*` (3): permission level 2, the command path, match create, list and dispose;
    - `:core:replayJournals` replays all 73 journals to their live hashes; it is part of `verify`;
    - the server smoke runs a 16 x 16 match at the spawn: 8 units on the target after 100 ticks,
      8 bodies, median tick 0.38 ms;
    - the functional GameTest run: 89 tests, 3.8 s of L3 wall time; `verify` green in 23 s;
    - `clientJoin` run, `scripts/playtest.sh` (checked with `sh -n` only, never run) and
      `docs/testing/playtest.md`; `createLaunchScripts` writes `runClientJoin.sh`.
  - **Problems:** none in the code; `match create`'s Y band is cut to the build height, since the
    GameTest floor lies 6 blocks above it (spec §1.6 D row 52).

- **M0 T7c · Body and match GameTests, the batch-end check** — UNIT-01, CORE-01, CORE-06, QA-04,
  QA-01, all still in progress (UNIT-01's criteria 1–4 pass; 5 is NET-01's, in M1).
  - **Results:**
    - finished from `wip/M0-T7c` after a rebase onto `main`;
    - `GameTestTickets` with its controller, `BatchEndCheck` after every batch's `@AfterBatch`
      cleanup, `MatchFixture`, `JUnitXmlReporter#recordFailure`, `LogCapture`;
    - `bodytests.*` (9, the 6,000-tick one in batch `slow`) and `matchtests.*` (3) pass;
    - the functional run: 13 GameTests in 1.9 s of L3 wall time; the slow run: 3.0 s;
    - `verify verifySlow` green in 22 s; `verifyHarness` runs `runGameTestTicketLeak`;
    - S5 check 17: `-Pstrategos.selftest=ticketLeak` failed with
      `GameTest failures in TEST-gametest.xml: [batchend.selftest]`, naming the chunk;
    - CI now runs `verify verifySlow verifySoak`.
  - **Problems:**
    - `/tp` rejected the body's coordinates: the arenas lie millions of blocks out, where
      `Double.toString` writes `1.13E7`; commands now format coordinates with `%.6f` (README);
    - `Entity#push` ignores two entities on the very same point, so the push control's pigs stand
      0.1 apart (spec §1.6 D row 51).
- **Commit prefixes:** two of the assistant's docs commits of 2026-10-21/22 (92b7757, b8fcf74) start
  with `State:` instead of `Docs:`; the skill's owner-commit search lists them, but they are not
  the owner's. The owner's commit is 021f641.

- **Owner's setup items done** (STATE "For the owner" 1a and 1b of 2026-10-21):
  - the allowlist is active: `.claude/settings.json`, identical to the proposal (commit 021f641);
  - `gh` is installed and logged in as `o-e-lytvynskyi`, so sessions read CI on `main`.
  - **Results:** CI on `main` is green; the newest run (M0 T7b, 39670656358) passed 825 tests,
    the S6 canary on Linux against the macOS golden among them (QA-08.3). The cancelled runs (T4b,
    T6b, the T5b review fixes) were cancelled by the next push, whose run passed. About 61 of the
    month's 2,000 minutes are used.
  - **Problems:** T7b's review did not run: its three reviewers stopped at the usage limit of
    2026-10-21, so the workflow returned nothing. Run it again before finishing T7c.
- **Next:** M0 T9 · persistence and quarantine; T7b's review is running again.

## 2026-10-21 — M0 T1, T2, T3, T18, T4a, T4b, T5a, T5b, T6a, T6b, T7a, T7b, T7c (started)

- **M0 T7c · Body and match GameTests, the batch-end check** — started, not finished: the session
  stopped as usage ran low. The work is on `wip/M0-T7c` (commit c064b5f, unverified).
  - **Done on the branch:** `GameTestTickets` (the ticket controller and its held chunks),
    `BatchEndCheck` (leftover tickets, GameTest matches and unit bodies are recorded as
    `batchend.<batch>`, then cleared), `MatchFixture`, `JUnitXmlReporter#recordFailure`.
  - **Left:**
    - register the controller in `GameTestBootstrap` (`RegisterTicketControllersEvent`, GameTest
      server only);
    - in `StrategosGameTests`, the `@AfterBatch` hooks of every batch (`MatchFixture.disposeAll`,
      then `BatchEndCheck.verify`), and the slow-batch rule (`strategos.gametest.slow`);
    - `Suites`: `BodyTests` and `MatchTests` in the default run, `ticketLeak` →
      `TicketLeakSelfTest`; the three suites of spec §7.4;
    - `mod/build.gradle`: the runs `gameTestSlow` and `gameTestTicketLeak`, the guard's failed-case
      check, the slow run's guard, the ticket-leak run's `expectVerdicts`; root `verifySlow`,
      `verifyHarness` gains `:mod:runGameTestTicketLeak`; CI runs `verify verifySlow verifySoak`;
    - the README's notes on the batch-end check and the slow batch.
  - **Notes for the tests:** "one log line" in `discarded_body_rematerialises` needs a log capture
    (a Log4j2 appender filtered by the match prefix); in `no_vibrations_no_push` the pig controls
    must stay more than 8 blocks from the unit's sensor (the sculk sensor's range) and run first,
    with the pigs discarded before the unit part.
  - **Reviews:** T6b's review (three lenses, each finding checked by a skeptic) confirmed nothing.
    T7b's review was still running when the session stopped: if its result is not recorded here,
    run it again before finishing T7c.

- **M0 T7b · Holder, match registry, tick driver, bodies** — CORE-10 in progress; CORE-01, CORE-02,
  CORE-06, UNIT-01.
  - **Results:**
    - `StrategosServer`, the per-server holder from `ServerAboutToStartEvent` to
      `ServerStoppedEvent`, with `StrategosSettings`;
    - `server.match`: `MatchSpace` with its codec, `MatchOptions`, `MatchRegistry` (size, height,
      build-height and 2-block overlap checks; create, pause, resume, dispose, unload),
      `MatchRuntime` and the chunk-based `MatchRegionIndex`;
    - `server.tick`: `TickDriver` in `LevelTickEvent.Pre`, gated by `/tick freeze`;
      `InputCollector`, `OutputAppliers` and `FaultBarrier`;
    - `server.body`: `BodyRegistry` (bodies in handle order where entities tick, within the 64-per-tick
      budget; parked after a chunk unload, re-made after any other removal), `KinematicDriver`,
      `SpawnBudget`, `BodyEvents`; `UnitEntity#hurt` reaches the match through the fault barrier;
    - every engine name was checked in the 21.1.264 sources first;
    - `:mod:test` passes 23 tests; `verify` is green, and the smoke also checks the holder and the
      registered body type.
  - **Problems:** the spec's `MatchException` clashes with Java 21's `java.lang.MatchException`;
    it is `MatchRefusedException` now (spec §1.6 D row 50, with three small API additions).

- **M0 T7a · Unit entity, registry, renderer** — UNIT-01 in progress; QA-03.
  - **Results:**
    - `ModEntityTypes` registers `strategos:unit_humanoid` (MISC, `noSave`, `noSummon`, fire
      immune, tracking range 10, interval 3, no velocity packets) with its attributes;
    - `UnitEntity`: a kinematic view; the server tick is empty, no pushing, loot, riding, portals,
      damage or equipment; the spawn data rides the complex-spawn packet;
    - `UnitSpawnData`: four VarInts and a byte, 13 bytes at most;
    - `Ids`, the client entry `StrategosClient` and `UnitRenderer` (the player model with the
      vanilla wide Steve skin, never copied);
    - `:mod:test` passes 8 tests: the type's settings, its attributes, the spawn data round trip
      and size, and `CodecSizes`, which fails above a cap with "UnitSpawnData encoded to 8 bytes,
      cap 7";
    - every engine name was checked in the 21.1.264 sources first;
    - `verify` green in 19 s; the dedicated and the GameTest server start with the type.
  - **Problems:** none.

- **M0 T5b review** (three lenses, every finding checked by a skeptic): five findings confirmed, all
  in tests and the spec; the kernel was right.
  - **Results:**
    - `MoveRuleTest`: "a refused Move changes nothing" compares the state hash with a twin match;
      the old assertion could never fail;
    - `StopRuleTest`: ownership is checked on a walking unit, plus stale and never-issued handles;
    - `SpawnUnitsRuleTest`: the unit cap with units already alive; golden positions in the stored
      units, on a floor at y 4;
    - the empty unit set's `INVALID_ARGUMENT` for Move and Stop is now in the spec (§1.6 D row 49);
    - each strengthened test fails under the mutation the review named (Stop applied before the
      ownership check, a cap that ignores live units, poses stored at `minY` or at the centre).
  - **Problems:** none.

- **M0 T6b · Test kit, scenarios, seeded runs, S6 canary** — QA-02 done; PERF-01 in progress;
  CORE-05, CORE-07, ORD-01, QA-01.
  - **Results:**
    - the rest of the test kit:
      - `SimHarness` checks `Invariants` after every step and throws `seed=<n> tick=<t>: …`;
      - `restore`, `replay`, `runUntil`, `run` and `play`;
      - `FakeBodies`, `ThrowingMetrics`, `RandomCommands`, `ReplayRunner`, `JournalFiles`,
        `Seeds`, `Percentiles` and `BudgetCheck`;
    - the scenarios `walking_skeleton`, `out_of_bounds`, `stop_mid_walk`, `patrol` and `canary`;
    - the seed sets `m0-200`, `skeleton-20`, `isolation-50` and `canary`;
    - `:core:test` passes 593 tests in 3.8 s wall, 2.9 s of it in tests (QA-02.1: under 10 s);
    - the walking skeleton arrives exactly at tick 81 for all 20 seeds;
    - 200 seeds × 1,000 ticks of random commands break no invariant;
    - 200 seeds × 2,000 ticks replay with every checkpoint equal;
    - the soak, 200 seeds × 10,000 ticks, takes 8.5 s and is part of `verify` and `verifySoak`;
    - the S6 canary gives equal hashes twice in one JVM and matches its golden
      (`golden/s6-canary.json`);
    - the released save fixture `fixtures/match_v1.json` (the canary at tick 500) decodes to its
      recorded hash, and a test-only step migrates it to version 2;
    - with a checkpoint every tick, dropping the command at tick 137 names tick 137;
    - negative checks, both reverted:
      - a deliberately broken seed failed as `seed=1627107988357113642 tick=501: …` and reran
        alone with `-Dstrategos.seed` (QA-02.2);
      - `testFixturesImplementation libs.assertj` failed `checkTestFixturesDeps` (QA-02.4);
    - CI runs `verify verifySoak`.
  - **Problems:** none in the code. `run` is split into `forScenario` and `play`, so a test can read
    the journal of the run it played (spec §1.6 D row 48).

- **M0 T6a · Journal, snapshot, migrations, replay** — CORE-07 in progress; CORE-05, CORE-03.
  - **Results:**
    - journal recording in INGEST, checkpoints and re-basing in FINAL;
    - `MatchCore.snapshot`, `restore` and `journal`;
    - `JournalCodec`, `ReplayDriver` and `MatchSnapshotCodec` with `"v"` and `Migrations`;
    - `:core:test` passes 140 tests;
    - a 2,000-tick random run replays with all 100 checkpoints equal;
    - removing one recorded command reports the first checkpoint after it;
    - a 500-unit snapshot survives JSON with an equal hash, and the restored core matches the original
      for 200 more ticks;
    - pending commands survive a snapshot.
  - **Problems:** none; the codec's two extra fields are spec §1.6 D row 47.

- **M0 T5b · `MatchCore`, commands, UnitAI, movement** — ORD-01 and QA-02 in progress; CORE-02,
  CORE-03, CORE-05.
  - **Results:**
    - the driving port and its records;
    - `MatchCoreImpl` with the nine-phase step;
    - `StateHasher`;
    - Move, Stop and SpawnUnits rules with ownership and `UNKNOWN_TARGET` for missing and stale
      handles;
    - 256 commands per tick, the rest next tick in order;
    - bus-driven UnitAI and exact movement;
    - the first cut of the test kit (`SimHarness`, clocks, `RecordingMetrics`);
    - `:core:test` passes 124 tests;
    - a clock ten times slower leaves 1,000 ticks of state hashes identical;
    - the spawn positions match an independent implementation of the formula bit for bit.
  - **Problems:**
    - The spec's snap rule `d <= speed` made a 3-4-5 move take 21 steps instead of 20; a 1e-6
      tolerance restores ⌈d / speed⌉ (spec §1.6 D row 46).
    - `MatchCore`'s snapshot and journal belong to T6a (row 45).

- **M0 T5a · Kernel internals** — CORE-01 and CORE-04 in progress; CORE-02, CORE-06.
  - **Results:**
    - `MatchLifecycle`, checked against an independent table of its 15 transitions;
    - `Phase`, `Cadence`, `Stagger` and `Scheduler`;
    - `MatchBus`: FIFO after the current message, a 10,000-deep chain without stack growth;
    - `EntityStore` with `EntitySlots`, which reuses the lowest free index and bumps generations;
    - the component stores;
    - `:core:test` passes 81 tests.
  - **Problems:** the spec put `MatchState` in T5b although T5a's scheduler needs it; T5a brings it
    with only its tick, and `UnitState` with explicit codes (spec §1.6 D row 44). NullAway caught
    an unchecked `EnumMap.get`.

- **M0 T4b · Core `protocol`, `content`** — CORE-03 in progress.
  - **Results:**
    - the sealed `Command` with `Move`, `Stop` and `SpawnUnits`, each hashable;
    - `UnitSet`, capped at 300 on decode too;
    - `CommandKinds`, whose codec dispatches on `kind` and names an unknown one;
    - `QueueMode`, `RejectReason` and `CommandOutcome`;
    - `TemplateSet.builtin()` with the debug walker and a golden hash;
    - `:core:test` passes 61 tests; `verify` passes in 20 s.
  - **Problems:** ErrorProne refused hashing `QueueMode` by its ordinal, which would let a
    reordered enum change state hashes silently; it hashes the mode's id.

- **M0 T4a · Core `shared`, `port`, `diag`** — CORE-05, CORE-02 and QA-10 in progress.
  - **Results:**
    - ids with JSON codecs, `DetMath`, the counter-based `Rng`, `Hasher`, `Hex64`, `Clock`,
      `MetricsSink`, `RingHistogram`, `PhaseTimers` and `MatchLog`;
    - `:core:test` passes 46 tests;
    - the golden values of `Rng` and `Hasher` were computed by an independent Python
      implementation of the spec's formulas, and the Java code matches it bit for bit;
    - `below` passes chi-square 8.6 against the 21.67 limit.
  - **Problems:** the test kit's `ManualClock` arrives in T5b, so `PhaseTimersTest` advances a
    clock lambda by hand.

- **M0 T18 · Continuous integration** — QA-08 and CORE-05 in progress.
  - **Results:**
    - one `ubuntu-24.04` job runs `verify` on pushes to `main` and on pull requests;
    - the first run, with a cold cache, passed in 6 min 11 s (budget 20 min, QA-08.2);
    - [pull request #1](https://github.com/o-e-lytvynskyi/strategos/pull/1) shows the check
      "verify: test results" with the L1, L2, L3 and L5 cases (QA-08.1); `main` was then
      fast-forwarded to the branch;
    - the actions are pinned to the commits of their tags;
    - documentation-only pushes run nothing, and a newer push cancels an older run.
  - **Problems:**
    - The current GitHub tools read a pull request's checks but not runs on `main`, so "Now"
      shows CI as not readable for `main` until the owner's setup item 1b.
    - `jmhSpeedRatio`, the soak and the slow batch join with T12, T6b and T7c
      (spec §1.6 D row 43).

- **M0 T3 · Smoke, the first ArchUnit rules, `verify`, `verifyHarness`, the first S5 report** —
  QA-05 and QA-17 in progress; QA-01, QA-04.
  - **Results:**
    - `verify` is green in 18.9 s warm and 19.8 s after `clean` (budget 5 min);
    - the server smoke takes 6.1 s (budget 60 s), L2 with the game bootstrap 3.8 s;
    - ArchUnit B5 and B11 hold on the shipped classes and flag their fixtures;
    - every `verifyHarness` run ends with its designed verdict;
    - every negative check and probe fails as designed (S5 checks 5–12, 15, 16), and disabling
      the retry-reason rejection makes its inverted guard fail the build;
    - ADR-0007 is Accepted on the S5 evidence ([spikes/S5-build-wiring.md](spikes/S5-build-wiring.md)).
  - **Problems:**
    - The `prepare*` `Sync` tasks stayed UP-TO-DATE after a server run, so the next smoke saw the
      last run's crash report; they now run every time (spec §1.6 D row 42; pitfall in the
      knowledge base).
    - `clientRef` crashes through NeoForge's dist cleaner, not `NoClassDefFoundError`; the guard
      catches it either way.
- **M0 T2 · GameTest harness** — QA-04 in progress, QA-01.
  - **Results:**
    - `runGameTestServer` passes `buildtests.core_in_mod_module` and
      `buildtests.testfixtures_in_mod_module`: core and the test kit run inside the mod module;
    - L3 wall time 1.2 s of the 180 s budget;
    - `verifyFast` plus the GameTest run take 6 s warm;
    - all four negative checks behave as designed (S5 checks 5, 7, 15, 16);
    - both SNBT arenas are generated and committed.
  - **Problems:**
    - Retries never ran on the 1.21.1 GameTest server: its tracker counts only first attempts,
      so it halted before a rerun and exited with the first failure. `RerunTracking` keeps the
      tracker on the newest attempt (spec §1.6 D row 41; pitfall in the knowledge base).
    - ErrorProne flagged `LocalDateTime.now()` without a zone: the report's timestamp is UTC.
    - A review of the commit (three lenses, each finding checked by a skeptic) corrected the
      README's account of a vanilla `server.properties` error. It also hardened the report:
      seconds always in the timestamp, no characters XML 1.0 forbids. Test names now treat a
      digit run as a word (`permission_2`), as T8's names need.
- **M0 T1 · Build skeleton** — QA-01, QA-03, QA-09 and CORE-06 in progress.
  - **Results:**
    - the wrapper checksum matched the MDK's;
    - the daemon runs on Temurin 21;
    - the first build took 55 s: NeoForge 21.1.264 downloaded and decompiled, 787 MiB of assets;
    - a clean rebuild without the build cache takes 5.9 s;
    - `HandleTest` passes 8 tests, `ModLoadsTest` 2 (the mod loads, Dist `DEDICATED_SERVER`);
    - the jar holds `Handle.class`;
    - no Gradle deprecation warnings.
  - **Problems, recorded in spec §1.6 D:**
    - Spotless was loaded twice because `core` and `mod` apply different plugin sets; the root now
      declares the convention plugin with `apply false` (row 38; pitfall in the knowledge base);
    - Gradle 9's property syntax (row 39);
    - a three-lens review with adversarial checks found that `checkVersionCatalog` missed `2+` and
      versions inside `group:name:version` strings (row 40), now proven on a broken catalog; two
      comments that strayed from the spec were removed;
    - `HandleTest` pins the bit layout with literals, since handles are saved.
- **Next:** finish M0 T7c from `wip/M0-T7c` (skill §9: rebase onto `main`, finish, green gate, fast-forward, delete the branch).

## 2026-10-21 — design closed, roadmap approved, autonomy set up

- **Owner decisions.**
  - Approved M0–M6. CMB-08 is deferred, because D-08 chose the observer avatar.
  - Took every decision needed by M6 as recommended.
  - Asked that every unit can always be controlled on its own, which is now pillar P3 and
    ORD-06's criteria 5–6.
  - Allowed commits and pushes to the private repository without asking.
  - Asked for autonomous development with occasional visits.
- **Done:**
  - the design round: three architectures, judged and synthesised into 04-architecture.md;
    ADRs 0001–0023;
  - feasibility and testing documents;
  - the roadmap reviewed by a game designer, a tech lead and QA (224 change requests);
  - the M0 implementation spec (T1–T18);
  - document reconciliation after the approval;
  - the promo draft;
  - the private GitHub repository `o-e-lytvynskyi/strategos`;
  - the autonomy kit: the `strategos-dev` skill, this worklog, the STATE.md layout, and
    `tools/roadmap.py`;
  - a dry run of the kit by a fresh agent: 30 issues, all fixed;
  - a permission allowlist for unattended sessions, proposed in `tools/claude-settings.json`;
  - a way for sessions to read CI (M0 T18 "Reading results").
- **Results:** `tools/roadmap.py check` passes on 245 cards, 0 problems. Open decisions: D-03,
  D-06, D-07, D-20, D-21, all needed after the MVP.
- **Problems:**
  - Usage limits cut several multi-agent runs short. Prefer inline work and small review
    workflows.
  - Auto mode refused to let the assistant write its own permission settings, as it should, so
    the allowlist waits for the owner (STATE "For the owner" 2a).
  - The GitHub MCP server's default tools cannot read Actions runs on `main` (2b).
- **Next:** M0 T1 (build skeleton), as soon as the owner says to start M0.

## 2026-10-20 — research

- **Owner decisions:**
  - Minecraft 1.21.1;
  - a bounded match arena first;
  - solo against an AI first, then 2–4 players;
  - RTS camera plus first person;
  - a hybrid world.
- **Done:**
  - two independent, fact-checked research passes on six topics;
  - a toolchain check: the NeoForge 1.21.1 MDK builds in 98 s on the owner's Mac;
  - decompiled 1.21.1 sources prepared for API checks;
  - tooling and process documents.
- **Next:** design.
