# S6: determinism canary

*Written with M0 T15 on 2026-10-22; the code is T6b's. Spec:
[M0 §8.2](../milestones/M0-foundation.md#82-s6-determinism-canary-t6b-code-t15-report--confirms-adr-0009).*

## Question and exit criterion

Does the core give the same result for the same inputs, every time, across runs, test levels and
platforms? This confirms [ADR-0009](../adr/0009-tick-pipeline-determinism-and-fault-containment.md)'s
determinism boundary.

Exit: the canary is identical twice in one JVM and equal to the committed golden; replays equal
their live runs; every GameTest journal replays to its live hash; Linux agrees with the macOS
golden.

## Setup

- Apple M4 Pro, macOS ("Mac OS X 27.0.1" as the JVM reports it), Temurin 21.0.12.1; and GitHub's
  `ubuntu-24.04` runners with Temurin 21 (T18's CI job).
- 2026-10-22, on the M0 T16 tree (commit `9427f65`).
- `./gradlew :core:test`, `./gradlew :core:soak --rerun`, and `./gradlew :mod:runGameTestServer
  :core:replayJournals`, all part of `verify`; CI runs `verify verifySlow verifySoak` on every
  push to `main`.

## Method and results

| # | Item | Test | Result |
|---|---|---|---|
| 1 | The canary, twice in one JVM, against the golden | `DeterminismCanaryTest`: scenario `canary`, seed 7 from `seeds/canary.txt`, 2,000 ticks, a hash every 100 ticks, against `golden/s6-canary.json` | Identical at all 20 checkpoints and equal to the golden: tick 100 `49f45a710d054ccc`, tick 500 `6dcbd3d871040342`, tick 2,000 `927445d79f144eb2` (0.03 s) |
| 2 | Replay equals live | `SeededReplayTest`, 200 seeds × 2,000 ticks; `SeededReplaySoakTest`, 200 × 10,000 | All 200 + 200 replays equal their live runs at every checkpoint (1.7 s and 8.4 s) |
| 3 | Across test levels | each scenario GameTest against its L1 twin; `:core:replayJournals` over the GameTests' exported journals | the 73 scenario and isolation GameTests end on their twins' tick and hash, and all 73 journals replay to their live hashes (0.19 s) |
| 4 | Thread counts | – | Not applicable in M0: the core has no jobs. M1 adds runs with 0, 1 and 4 path threads |
| 5 | Linux | `DeterminismCanaryTest` on CI against the macOS golden | Passes on every green CI run since T18, for example run 39694961351 (M0 T13) |

Supporting checks that keep the canary honest:
- `ClockIndependenceTest`: a clock reporting phases 10 times slower gives the same hash.
- The ArchUnit rules of B4 (`CoreArchitectureTest`): no wall clock, unseeded randomness,
  transcendental `Math`, hash-ordered collections, threads or parallel streams in the
  deterministic packages, each proved on its violating fixture (T11).
- The save round trip keeps the hash: `persistencetests.save_restore_same_hash` (L3) and the
  restart smoke's replay of two journals (L5, T9b).

## Analysis

Every item holds. The same inputs give the same state hash in one JVM, across 400 seeded replays,
between JUnit and the GameTest server, through a save and a server restart, and on Linux against a
golden written on macOS.

## What it decides

- **ADR-0009:** the determinism boundary works as designed; the ADR stays Accepted, with this
  report as S6's evidence.
- The golden changes only with a recorded reason (R12); `-Dstrategos.golden.update=true` rewrites
  it.

## Raw data

The golden is `core/src/test/resources/golden/s6-canary.json`; the seed sets are
`core/src/testFixtures/resources/seeds/`.

## Follow-ups

- M1: the thread-count item, once the path service runs on jobs.
