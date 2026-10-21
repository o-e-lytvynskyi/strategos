# S5: build wiring — first report

*First version, written with M0 T3 on 2026-10-21; T15 completes it with checks 1, 2, 13, 14 and
17 under the final M0 build. Spec: [M0 §8.1](../milestones/M0-foundation.md#81-s5-build-wiring-t3-first-version-t15-final--confirms-adr-0007-and-executes-adr-0003).*

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
  commit.

## Method

Each check ran as the "How" column of M0 §8.1 says, from the repository root with
`./gradlew --console=plain`. Temporary probes were deleted afterwards, and `git status` showed none.

## Results

| # | Check | Result |
|---|---|---|
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

`verifyHarness` repeats checks 5, 6, 7, 9 (`crashOnStart`), 15 and 16 on every `verify` through
inverted guards. The inversion was proven by disabling the generator's retry-reason rejection
for one run: `runGameTestRetryNoReason` then failed with "wrote a report; the GameTest guard no
longer catches a retry without a reason".

### Times so far (check 13 is final in T15)

| Run | Time | Budget |
|---|---|---|
| First build of the checkout (downloads NeoForge, decompiles, 787 MiB of assets) | 55 s | – |
| `clean verifyFast :mod:test` without the build cache | 5.9 s | `verifyFast` ≤ 1 min (QA-01.1) |
| `:mod:test` with the game bootstrap (L2) | 3.8 s | ≤ 60 s (QA-03.3) |
| L3 wall time of `runGameTestServer` | 1.2–1.4 s | ≤ 3 min (QA-04.3) |
| `runServerSmoke` | 6.1 s | < 60 s (QA-05.1) |
| `runServerSmoke` then `runServerSmokeRestart` (T9b, two server boots in one game directory) | 19.2 s for both | run 1 < 60 s (QA-05.1) |
| `clean verify` (local build cache on) | 19.8 s | ≤ 5 min |
| `verify` again | 18.9 s | ≤ 5 min |

The configuration cache is reused on the second `verifyFast` ("Reusing configuration cache.",
check 2's criterion).

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

- **T15:**
  - check 1 (a clean `verify` without the build cache);
  - check 13 with the restart run, `verifySlow` and `:core:soak`;
  - check 14;
  - check 17 after T7c.
- **T18:** the same checks on Linux CI.
