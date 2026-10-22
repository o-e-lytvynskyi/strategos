# ADR-0007: GameTests inside the `strategos` mod, registered by our own generator

Date: 2026-10-21

## Status

Accepted (2026-10-21, spike S5)

> **2026-10-21, S5 first report** ([spikes/S5-build-wiring.md](../spikes/S5-build-wiring.md)): the merged
> `gametest` source set and `core/testFixtures` run inside the `strategos` module on the GameTest server and
> on a dedicated server, and every guard catches its broken input, so the fallback is not needed. One
> correction: overriding the swapped annotation fields was not enough for retries, because 1.21.1's
> GameTest server tracks only first attempts. The harness's `RerunTracking` keeps its tracker on the newest
> attempt ([M0 spec §1.6 row 41](../milestones/M0-foundation.md#16-differences-resolved)).

> **2026-10-22, S5 final report:** all 17 checks hold under the final M0 build, with the timings
> of check 13 inside every budget (a cold `clean verify` in 39 s); the decision stands.

## Context

Research contradiction 9: one report made GameTests a separate test mod with its own `mods.toml`
and namespace, and another kept them as a source set in the `strategos` mod group. Neither setup
has been run. Facts verified in the 1.21.1 sources ([research/testing.md §6](../research/testing.md)):

- **Registration.** `RegisterGameTestsEvent` registers test classes or methods
  (RegisterGameTestsEvent.java:39, 51).
- **The namespace filter.** `neoforge.enabledGameTestNamespaces` filters by *template namespace*
  and silently drops tests without one.
- **Swapped retries.** `GameTestRegistry` passes `requiredSuccesses()` where `attempts` belongs and
  the reverse (GameTestRegistry.java:156–157).
- **No report.** Nothing installs a report file; `GlobalTestReporter.replaceWith` exists
  (GlobalTestReporter.java:6).
- **Exit code 0 on failure.** An empty test set or a mod-loading error very likely exits 0, so the
  exit code alone is no verdict.
- **Concurrency.** Tests in a batch run concurrently in one world at a random origin up to
  ±15 M blocks; ticks run back to back.
- **Arena templates in dev** load as SNBT from `StructureUtils.testStructuresDir`
  (StructureUtils.java:38).

## Decision

- **A `gametest` source set in `mod`,** joined to the `strategos` mod group in dev runs together
  with `core/testFixtures` (ADR-0003). It is never in the jar.
- **Registration** goes through `RegisterGameTestsEvent` with our own generator.
  - An annotation (`@StrategosTest(arena, batch, timeoutTicks, setupTicks, required, attempts,
    successes)`) defines tests, and generated `TestFunction`s carry `strategos:` template names, so
    they pass the namespace filter.
  - The generator implements retries itself, which fixes the swap.
  - Test names are `<suite>.<method>` in lower case; `-Pgametest.filter=<regex>` selects a subset.
- **Reporting.** Our JUnit XML reporter is installed through `GlobalTestReporter.replaceWith`. The
  Gradle task fails when the report is missing or has no `<testcase>`, which catches an empty set
  and a loading error that exit 0.
- **Arenas** are SNBT files in the repository (`src/gametest/structures`), each with an explicit
  floor layer. Map-scale tests import the proving ground in their setup, in a solo batch.
- **Batches:** `default`, `slow` and `perf`. Perf runs alone in its own invocation.
- **Scenarios.** Every test creates its own match bound to `GameTestHelper#getBounds()`, so
  parallel tests never share state (ADR-0010). Scenario tests use the shared definitions from
  `core/testFixtures`.
- **Spike S5 verifies** dev runs, the jar contents, the report guard, a deliberately empty set, a
  deliberately broken mod load, and a "2 of 3" retry declaration (QA-04).

## Consequences

- One mod id and one namespace; tests can reach package-private code; no second `mods.toml`.
- The guard and our generator make GameTests trustworthy as a merge gate.
- 1.21.5's registry-based GameTests later change only the generator's backend; the scenarios stay
  the same (§18.1 of [04-architecture.md](../04-architecture.md)).
- If S5 shows the merged source set misbehaves, for example duplicate classes or loading order,
  the fallback is a separate `strategos_gametest` mod with its own namespace. The tests stay; only
  registration and wiring move.

## Alternatives considered

| Alternative | Why not |
|---|---|
| A separate `strategos_gametest` test mod | A second `mods.toml` and namespace, and no package-private access. Kept as the fallback |
| `@GameTestHolder` classes with vanilla annotations | The swapped retry attributes, no shared scenario generator, and silent drops without a namespace |
| GameTests in `main`, gated by a flag | Test code would ship in the jar |
| JUnit with `EphemeralTestServerProvider` | No world interaction allowed; a void overworld only |
