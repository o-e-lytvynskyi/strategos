# ADR-0006: Test levels L0–L7 and the Definition of Done

Date: 2026-10-21

## Status

Accepted; amended by [ADR-0022](0022-definition-of-done-amendments.md) (the Definition of Done for
networking and for build and tooling changes, L5 on every change, the inventory check in L2)

## Context

- **The owner's bar:** professional, best-practice engineering, with automated tests of every
  intermediate result.
- **The assistant writes the code and runs the tests**, so feedback must be fast, textual and
  unattended. The assistant runs only tasks that end on their own. Interactive `runClient` and
  `runServer` sessions are started by the owner ([03-design-process.md](../03-design-process.md)).
- **A client window can take focus and swallow the owner's typing** (gap G4; a recorded pitfall on
  this machine), so client runs need consent until spike S3 shows otherwise.
- **1.21.1 GameTests have traps** ([research/testing.md §6](../research/testing.md)):
  - an empty test set or a mod-loading error very likely exits 0;
  - no report file is written;
  - the retry attributes are swapped;
  - tests in a batch run concurrently in one world.
- **jqwik ≥ 1.10 ships an anti-AI usage clause** and printed instructions aimed at coding agents,
  so it is banned.

## Decision

**Levels** ([research/testing.md §2](../research/testing.md)):

| Level | Proves | Task | Who runs it |
|---|---|---|---|
| L0 static | Formatting, compilation, ErrorProne + NullAway (JSpecify), ArchUnit rules B1–B13, the engine-surface inventory | `spotlessCheck`, `compileJava`, `:mod:archTest` | Assistant after every edit batch; CI |
| L1 core unit | All game rules and whole matches without Minecraft; determinism, invariants, replays; client maths and view-models | `:core:test` (JUnit 5.14.4 + AssertJ 3.27.7) | Assistant, continuously |
| L2 MC unit | Codecs, payload sizes, migrations from fixtures, shipped data through the real loaders, registries | `:mod:test` (MDG `unitTest`, FML JUnit, testframework) | Assistant on adapter or data changes |
| L3 GameTest | Adapters in a real server world, mock players, save and load, fault injection, parallel matches | `:mod:runGameTestServer` with our generator, JUnit XML and the non-empty-report guard | Assistant before every commit |
| L4 client scenario | HUD, camera, rendering: PNG plus JSON | `:mod:runScenario`, `:mod:scenarioDiff` | Only with the owner's consent until S3 passes; CI under Xvfb |
| L5 server smoke | No client classes on a dedicated server; the mod loads; a headless match runs | `:mod:runServerSmoke` with a marker-file verdict | Assistant before commit |
| L6 performance | Budgets (ADR-0019); JMH regressions above 15 % | perf GameTest batch alone; `:core:jmh` | Assistant on hot paths; the owner approves baselines |
| L7 manual | Feel, multiplayer, the production jar | Dedicated server and two clients; mc-runtime-test on releases | Owner at milestones |

**Rules that make the levels trustworthy:**
- **Client logic is pure code in `core.client`,** so L1 covers it: rig maths, HUD layout,
  picking, selection, gestures, view-models. L4 only confirms the drawing (gap G4). If S3 shows
  that local windows steal focus, Linux container or CI goldens become the source of truth.
- **One scenario, two runners.** Scenarios in `core/testFixtures` run in L1 against fakes and as
  GameTests (ADR-0007).
- **Cross-level replay.** Every behaviour GameTest exports its journal, and L1 replays it to the
  same state hash. L1 also asserts identical hashes for 0, 1 and 4 path threads (ADR-0009).
- **Seeded invariant runs** use JUnit's `@ParameterizedTest` over 200 seeds. The seed is printed in
  every failure and reproducible with `-Dstrategos.seed=<n>`. No property-testing library.
- **Every task has a timeout and an external verdict:** the report guard, the marker file, the
  watchdog.
- **Owner approval.** Goldens and performance baselines change only with the owner's approval.
  Thresholds are never raised, and tests are never marked flaky to get a green build; a flaky test
  is a bug.

**Definition of Done** (R = required, C = conditional):

| Change type | L0 | L1 | L2 | L3 | L4 | L5 | L6 | L7 |
|---|---|---|---|---|---|---|---|---|
| Core simulation, pathfinding, economy | R | R, with new tests | – | C (if visible in the world) | – | – | C (hot path) | – |
| Server gameplay adapter (bodies, blocks, AI glue) | R | R | C | R, with a new GameTest | – | R | C | milestone |
| Data, registries, codecs, datagen | R | – | R | R | – | R | – | – |
| Networking and payloads | R | – | R (round trip and size) | R (mock players) | – | R | C | R before merge |
| Client UI, HUD, camera, rendering | R | R (pure maths) | – | – | R (consent) | R | C (frame time) | – |
| Build and tooling | R (full `verify`) | R | R | R | R | R | – | – |

Done also means:
- behaviour is tested at the lowest level that can express it;
- no new ErrorProne warnings, and NullAway is clean;
- affected documents are updated (STATE.md, roadmap statuses, the architecture or an ADR);
- any new pitfall is written to the knowledge base in the same session.

The commands and details will live in `05-testing.md`.

## Consequences

- Most behaviour, including whole AI-versus-AI matches, is proven in seconds in L1. GameTests
  prove the adapters. That is the main reason the architecture keeps state in the core
  (ADR-0008).
- M0 builds every harness before gameplay, and the walking-skeleton tripwire keeps that bounded
  (risk R11).
- Client features can be finished without a window, but each still gets its L4 confirmation when
  the owner consents, or on CI.
- The test suites grow with every card; the L3 suite target is under 3 minutes of wall time
  ([01-feasibility §5.6](../01-feasibility.md)).

## Alternatives considered

| Alternative | Why not |
|---|---|
| Behaviour tested mainly by GameTests (B) | 40–120 s per run and outcome bounds instead of state hashes: the slowest loop for an assistant that cannot see the game |
| Manual testing for client features | Breaks "every intermediate result is tested" |
| jqwik property tests | Licence clause aimed at AI agents; seeded parameterised tests do the job |
| `EphemeralTestServerProvider` for world tests | Its own Javadoc forbids world interaction; it gives only a void overworld |
| Fabric's client gametest API | Not available on 1.21.1 |
