# S5: build wiring

*Final report: first written with M0 T3 on 2026-10-21, completed with T15 on 2026-10-22 (checks 1,
2, 13, 14 and 17 under the final M0 build). Spec: [M0 §8.1](../milestones/M0-foundation.md#81-s5-build-wiring-t3-first-version-t15-final--confirms-adr-0007-and-executes-adr-0003).*

## Question and exit criterion

Does the build wiring of [ADR-0003](../adr/0003-module-layout-and-core-merge.md) and
[ADR-0007](../adr/0007-gametest-wiring.md) hold in practice?
- `core` and its test kit merge into the `strategos` module in dev runs and in the jar;
- GameTests registered by our own generator run inside the mod;
- each guard catches its broken input.

Exit: every check below behaves as the spec expects, or the fallback of ADR-0007 is taken (a
separate `strategos_gametest` mod, M0 §11 R1).

## Setup

- Apple M4 Pro, 24 GB, macOS 26.6.2.
- Gradle 9.2.1, its daemon on Temurin 21.0.12.1; Java toolchain 21.
- NeoForge 21.1.264, ModDevGradle 2.0.148, Parchment 1.21.1/2024.11.17.
- 2026-10-21, on the working trees of M0 T1–T3; commits `e5d9477` (T1), `33175b8` (T2) and the T3
  commit. The final checks: 2026-10-22, on the M0 T16 tree (commit `9427f65`).

## Method

Each check ran as the "How" column of M0 §8.1 says, from the repository root with
`./gradlew --console=plain`. Temporary probes were deleted afterwards, and `git status` showed none.

## Results

| # | Check | Result |
|---|---|---|
| 1 | Clean build and `verify` | Green: `./gradlew clean` then `./gradlew verify --no-build-cache` in 39.3 s, and `verify` again in 31.2 s (2026-10-22) |
| 2 | Configuration cache | Both `verifyFast` runs print "Reusing configuration cache." (0.35 and 0.36 s); no task needed `notCompatibleWithConfigurationCache` |
| 3 | Core merged in dev | Pass. `buildtests.core_in_mod_module` and `buildtests.testfixtures_in_mod_module` pass on the GameTest server. The smoke's step 1 passes on a dedicated server: `strategos` is loaded and `Handle` runs in the mod's module |
| 4 | Core merged in the jar | Pass. `:mod:checkJarContents` finds every core class exactly once, no dev-only class and no `module-info`; `strategos-0.0.1.jar` holds `dev/strategos/core/shared/Handle.class` |
| 5 | Empty test set | Fails as designed: "GameTest report missing"; the log says "filter '^nomatch$' matched no GameTest" and then "No test functions were given!". The JVM exited 0: the guard runs only after a zero exit, and it was the guard that failed the build |
| 6 | Broken mod load (`failModLoad`) | Fails as designed: "GameTest report missing"; the JVM again exited 0 |
| 7 | Retry semantics | As designed after a fix. `selftests.two_of_three` passed after 3 attempts and is listed as `retried` with its reason; `selftests.exhausted` failed. The first run showed that 1.21.1's GameTest server never runs a retry: see "Analysis" |
| 8 | JUnit XML | Pass. The report parses; every test case has a duration; the suite's `time` is the run's wall time, which the guard prints ("L3 wall time 1.226 s (budget 180 s)") |
| 9 | Smoke negatives | Both fail as designed. `crashOnStart`: "Server smoke crashed". `clientRef`: the server crashes too, but with NeoForge's dist cleaner ("Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER") rather than the `NoClassDefFoundError` the spec expected; the guard catches both |
| 10 | Catalog negative | Fails as designed: `palantir = "2.+"` gives "line 16: dynamic version or range '2.+'". The check also catches `2+` and `group:name:latest.release`, which an earlier version missed (M0 §1.6 row 40) |
| 11 | Compile probes | Both fail as designed. `ProbeMc` importing `net.minecraft.world.level.Level` in core: "package net.minecraft.world.level does not exist". `ProbeNull`: "[NullAway] dereferenced expression 'value' is @Nullable" |
| 12 | ArchUnit probe | Fails as designed: `ProbeClient` in mod `main` breaks B5 ("Method <dev.strategos.mod.ProbeClient.client()> calls method <net.minecraft.client.Minecraft.getInstance()>") |
| 15 | Retry without a reason | Fails as designed; the log names "retrynoreasonselftest.no_reason: 2 attempts without a retryReason (QA-04.4)" |
| 16 | Solo test in a shared batch | Fails as designed; the log names "batch 'selftest' must hold one test but holds [sharedbatchselftest.plain, sharedbatchselftest.solo] (QA-04.5)" |
| 13 | Timings | Every one inside its budget: see the table below |
| 14 | Workspace clean | No probe file is left (`git status` shows only the report's own changes), and `verify` is green again |
| 17 | Leaked ticket | Fails as designed (T7c, 2026-10-22): `-Pstrategos.selftest=ticketLeak` gives "GameTest failures in TEST-gametest.xml: [batchend.selftest]", naming the chunk; the next run starts clean. `verifyHarness` repeats it as `runGameTestTicketLeak` |

`verifyHarness` repeats checks 5, 6, 7, 9 (`crashOnStart`), 15, 16 and 17 on every `verify`
through inverted guards. The inversion was proven by disabling the generator's retry-reason rejection
for one run: `runGameTestRetryNoReason` then failed with "wrote a report; the GameTest guard no
longer catches a retry without a reason".

### Times (check 13, final, 2026-10-22)

| Run | Time | Budget |
|---|---|---|
| First build of the checkout (downloads NeoForge, decompiles, 787 MiB of assets; 2026-10-21) | 55 s | – |
| `clean verifyFast` without the build cache | 8.6 s | `verifyFast` ≤ 1 min (QA-01.1) |
| `verifyFast` warm | 0.4 s | ≤ 1 min |
| `:mod:test` with the game bootstrap (L2, 45 tests), rerun | 4.0 s | ≤ 60 s (QA-03.3) |
| L3 wall time of `runGameTestServer` (97 GameTests), as the guard prints it | 4.2 s | ≤ 3 min (QA-04.3) |
| `runServerSmoke` | 11.5 s | < 60 s (QA-05.1) |
| `runServerSmoke` then `runServerSmokeRestart` (two server boots) | 19.3 s for both | reported |
| `verifyHarness` (eight harness runs, `:mod:archTest`, `:core:test` with 616 tests, the fixture and JMH self-tests) | 12.3 s | reported |
| `:core:soak` (200 seeds × 10,000 ticks), rerun; `verifySoak` runs it | 9.1 s | reported |
| `verifySlow` alone (the slow GameTest batch, 1.5 s of L3 wall time) | 6.2 s | reported |
| `clean verify` without the build cache (cold) | 39.3 s | ≤ 5 min |
| `verify` again (warm) | 31.2 s | ≤ 5 min |

The configuration cache is reused on the second `verifyFast` ("Reusing configuration cache.",
check 2's criterion). CI runs `verify verifySlow verifySoak` in 6 to 10 minutes on GitHub's
runners (T18).

## Analysis

ADR-0007's design holds. `core`, its test kit and the GameTest code run inside the `strategos`
module on the GameTest server and on a dedicated server, and the jar carries core exactly once.
No duplicate classes or loading-order problem appeared, so the fallback is not needed.

The run found three problems in vanilla behaviour and in our build, each fixed and recorded in
M0 §1.6 D:
- **Retries never ran (row 41).** `GameTestServer#startTests` builds its `MultipleTestTracker` from
  the first attempt of each test and never hears of a rerun. After a first failure the server
  halted before the retry and exited with that failure. `RerunTracking` keeps the tracker on the
  newest attempt.
- **Stale game directories (row 42).** The `Sync` tasks that wipe a server's game directory stayed
  UP-TO-DATE after a run, because Gradle did not see what the forked server wrote. A smoke run then
  failed on the previous run's crash report. They now run every time.
- **Spotless loaded twice (row 38).** `core` and `mod` apply different plugin sets, so Spotless was
  loaded in two class loaders and its shared build service failed. The root build now declares the
  convention plugin.

## What it decides

- **ADR-0007:** Accepted, with this report as its evidence.
- **ADR-0003:** its merge is executed and checked (checks 3 and 4).

## Raw data

[data/S5-gametest-run1.xml](data/S5-gametest-run1.xml) (the functional run's report) and
[data/S5-gametest-retry.xml](data/S5-gametest-retry.xml) (the retry self-test).

## Follow-ups

- None for M0. CI repeats `verify verifySlow verifySoak` on Linux on every push (T18).
