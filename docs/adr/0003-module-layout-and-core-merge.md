# ADR-0003: Gradle layout with a Minecraft-free core merged into the mod

Date: 2026-10-21

## Status

Accepted

## Context

- **Design rule 3 of [03-design-process.md](../03-design-process.md):** "rules in `core`, Minecraft
  in `mod`". Game rules must compile without Minecraft, test in milliseconds and survive version
  ports.
- **MDG for 1.21.1 loads only mods in dev runs.** It does not load plain library jars:
  `implementation project(':core')` alone gives `ClassNotFoundException`. It does accept source
  sets from other projects in `mods { … sourceSet … }`, and passes them to FML as one mod folder
  group (verified in MDG's source, [research/testing.md §3.2](../research/testing.md)).
- **Scenarios should run in two places.** The same scenarios should run in L1 against fakes and in
  L3 inside a real server, so the fixtures must be usable in a GameTest run.

## Decision

| Project | Plugins | Source sets |
|---|---|---|
| `build-logic` | `groovy-gradle-plugin` | Convention plugins: `strategos.java-conventions` (toolchain 21, Spotless with palantir-java-format, ErrorProne + NullAway in JSpecify mode, JUnit Platform) and `strategos.core-conventions` (a plugin-free `jmh` source set run through `JavaExec`) |
| `core` | `java-library`, `java-test-fixtures` | `main`, `test` (L1), `testFixtures`, `jmh` |
| `mod` | `net.neoforged.moddev` 2.0.148 | `main`, `test` (L2), `gametest` (L3, perf), `devtools` (L4), `archTest` (L0) |

- **Merge.** `core` joins the `strategos` mod through MDG's
  `mods { strategos { sourceSet core.sourceSets.main } }` in every run, and the jar ships it
  through `jar { from core.sourceSets.main.output }`. First-party code never goes through jarJar.
- **Dev runs** also add `core.testFixtures`, `gametest` and `devtools` to the mod group. The jar
  holds only `mod.main` and `core.main`, and a jar check proves it.
- **Core dependencies.** `core` compiles only against the JDK and libraries Minecraft 1.21.1 ships,
  as `compileOnly` at exactly the shipped versions: fastutil 8.5.12, DataFixerUpper 8.0.16, Gson
  2.10.1, slf4j-api 2.0.9, plus JSpecify 1.0.1 annotations. No Guava, JOML or Netty.
- **No `module-info`** in `core`. Once merged, it would become the mod's module descriptor.
- **`core/testFixtures`** depends only on core `main` and the same libraries, never on JUnit or
  AssertJ, so it runs inside GameTests.
- **Aggregate tasks:** `verifyFast`, `verify`, `verifyClient`, `verifyPerf`
  ([research/testing.md §13](../research/testing.md)).
- **Spike S5 executes the wiring** in dev runs and in the jar, with the configuration cache (QA-01).
  The mechanism itself is decided.

## Consequences

- One module in dev and in production: the same classloader, no split packages, no duplicate
  classes.
- L1 tests of the whole game run in seconds without Minecraft, and the compile classpath stops any
  `net.minecraft` import in `core` (rule B1).
- Test fixtures appear in dev runs only, so scenarios run as GameTests (ADR-0007).
- `mod` is 35–45 % of the code *(estimate)* and the only part a version port touches.
- If S5 finds a defect in the merge, the fallback is jarJar plus `additionalRuntimeClasspath` for
  `core`. That keeps the code and loses only the identical dev and production loading.

## Alternatives considered

| Alternative | Why not |
|---|---|
| `jarJar project(':core')` plus `additionalRuntimeClasspath` | Core then sits on the legacy classpath in dev and in a nested module in production: dev and production differ, split packages are fatal, version-range rules apply. Fine for third-party libraries, not for first-party code |
| Shadow plugin | Still needs the `mods {}` merge; an extra plugin, and MDG's jar wiring must be redone; nothing to relocate on 1.21.1 |
| A single project with package rules only | Nothing stops a Minecraft import in rules code at compile time; core tests would bootstrap Minecraft |
| `core` as a second mod | Two mod ids and two `mods.toml` for one product; cross-mod loading order for no gain |
