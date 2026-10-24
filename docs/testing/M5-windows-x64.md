# M5 test environment — Windows x64

Recorded on 2026-10-24 for `codex/m5-combat`. The owner's target is a MacBook M5.
The target's macOS release, memory, display settings and JDK build have not been supplied.
This host is not an Apple Silicon or macOS emulator; its timings and pictures must not be
treated as evidence of performance or rendering on that target.

## Actual host

| Item | Value |
|---|---|
| OS | Windows 10 Home, 10.0.19045, x64 |
| CPU | Intel Core i5-9400F, 2.90 GHz, 6 cores / 6 threads |
| Windows power plan | High performance (`8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c`) |
| OS-visible physical memory | 16,693,232 KiB (about 15.9 GiB) |
| GPU | NVIDIA GeForce GTX 1060 6GB |
| Graphics driver | 32.0.15.8228 |
| JDK | Eclipse Temurin 21.0.12.1+1, Windows x64, project-local |
| Gradle | Wrapper 9.2.1; at most 2 workers |
| JVM | Project defaults: 2 GiB daemon heap, Parallel GC, UTF-8 |
| JMH forks | 1 GiB initial/maximum heap, G1 GC, 2 forks, 3 × 1s warm-up and 5 × 1s measurement per fork |
| Server performance JVM | 3 GiB initial/maximum heap, G1 GC, JFR profiling; one performance run at a time |
| Game / loader | Minecraft 1.21.1 / NeoForge 21.1.264 |
| Initial revision | `37051181d12eac45e25e2ec2114870b86bf75459` |
| Dependency cache | Initially project-local; actual test runs use `%TEMP%/strategos-m5-gradle` |

Commands run from the repository root in PowerShell with `JAVA_HOME` pointing to
`build/toolchains/jdk-21.0.12.1+1` and `GRADLE_USER_HOME` to `%TEMP%/strategos-m5-gradle`.
On 2026-10-25 an unused `S:` drive was temporarily mapped to the repository using
Windows `subst`, so Java sees ASCII paths throughout. Files stay in the owner's
original folder. Builds on that mapping use `--no-configuration-cache` to avoid
reusing paths captured before the mapping.
Python documentation checks use `python -X utf8`.

## Results

- `python -X utf8 tools/test_roadmap.py`: **3 tests passed**, including UTF-8 parsing
  under a simulated Windows CP1251 default and rejection of an empty roadmap.
- `python -X utf8 tools/roadmap.py check`: **245 cards, 0 problems**.
- `gradlew.bat :core:test :mod:checkScenarioDiffSelftest --no-configuration-cache
  --console=plain --max-workers=2` through `S:`: **passed in 1m 46s**. All **1,298 core
  tests passed**, with zero failures, errors or skips. The scene-diff selftest passed,
  including identical images with a failed/missing scene verdict and no platform goldens.
- Bootstrap `verifyFast`: **environment failure**, before meaningful test execution.
  A project-local cache under the Cyrillic directory failed to load `GradleWorkerMain`.
  A short 8.3 path did not help because Gradle canonicalised it. Moving the dependency
  cache to an ASCII temporary path allowed compilation (core, fixtures and tests,
  plus Minecraft/NeoForge artifacts), but the test JVM still could not load classes
  from the Cyrillic project path. Its report is a test-executor failure, not passed
  tests or evidence of a gameplay defect. The ASCII drive mapping addresses the
  remaining project path; the successful run above verifies it. Full `verify verifySlow`
  **passed in 18m 32s** on 2026-10-25: 1298 core, 3 slow core, 200 determinism soak, 73 journal replay, 95 Minecraft unit, 12 architecture, 211 GameTest, 13 slow GameTest. No gameplay card is done;
  this closes the T1 health check only.

### T2 mobility checks (2026-10-25, passed)

- The 17 focused tests in `MobilityTest`, `LargeMovementTest` and `LargeTemplateTest`
  pass: narrow and wide gaps, steps and headroom, diagonal body-box corners across page
  borders, immutable terrain updates, mixed orders with a separate wide opening,
  700 ticks without crossing an impassable gap, and exact save/resume hashes.
- The slow populated-map check also passes: a 512 × 512 floor with a 512 × 8 bridge,
  two layers in both mobility grids, **10,049,008 navigation bytes (9.58 MiB)** against
  the unchanged 32 MiB limit. This is a populated-map measurement, unlike the native
  prewarm fixture below.
- The final native navigation performance run passes four checks in **1m 14s**:
  ordinary-path D p99 **0.9465 ms**, group-path D p99 **1.0524 ms**, both below 2 ms,
  with strict delivery K=2 and answer latency p95 of 4 ticks. Page publication p99 **0.1105 ms** is
  report-only. The generated 512² prewarm fixture is mostly empty: its 2,154,512-byte
  navigation report is not evidence for a populated map. Raw report:
  [M5-T2-navigation-windows-x64.json](data/M5-T2-navigation-windows-x64.json).
- Full JMH ran sequentially on this same host at T1 commit `a13850b` and the T2 tree,
  including diagonal clearance. All 20 common benchmarks ran, plus the new two-class
  builder benchmark. One-class page building is **99.97 µs** (T1 99.07 µs); both classes
  together are **255.51 µs**, below the existing 500 µs page target. All other common
  operations and hot-kernel allocation checks pass the unchanged comparison rules,
  except `TerrainEditBench.rebuildOnePage`: **739.15 → 1,848.21 µs (+150.04%)**.
  Rebuilding the second grid and its clearance halo is additional feature work, anticipated
  in spec §2.1, but it still fails the 15% JMH regression gate. It is **not green**.
  [Comparison and source fingerprint](data/M5-T2-jmh-comparison-windows-x64.json),
  [T1 raw control](data/M5-T1-control-windows-x64.json), and
  [approved Windows baseline](../../perf/baseline-windows-i5-9400f.json).
  The owner approved this host's T2 base on 2026-10-25 and instructed M5 to continue,
  preserving the thresholds and the required MacBook M5 checks. Future comparisons on
  this host select `'-Pperf.machine=windows-i5-9400f'`; the Mac M4 Pro baseline stays unchanged.
  PowerShell parameters with dots must be quoted, for example
  `'-Pjmh.baseline=build/m5-t1-control-jmh.json'`; an unquoted parameter failed during
  build configuration before any comparison took place.
- Final `verify verifySlow verifySoak` **passed in 15m 44s**: 1315 core, 4 slow core,
  200 determinism soak, 73 journal replay, 95 Minecraft unit, 12 architecture,
  211 GameTest and 13 slow GameTest; zero failures, errors or skips. Ordinary L3 wall
  time was 80.491s against 180s; the slow batch reported 482.404s. The approved-machine
  `:core:jmhCompare '-Pperf.machine=windows-i5-9400f'` also **passed**, in 1s.
  [Verification summary](data/M5-T2-verification-windows-x64.json).
  T2 is closed; PATH-11 remains in progress until T6's actual mounted L3 crossing.

## Checks required on the MacBook M5

Use the same commit, Minecraft and NeoForge versions and Java 21. Record the exact model,
macOS, memory, JDK, display resolution, GUI scale and render distance. Run the functional
gates (`verify`, `verifySlow`, `verifySoak`) and the M5 consent batch on macOS, with and
without the compatibility profile. Compare macOS images with their macOS goldens only.
Run the battle and mounted-unit performance checks and PERF-14 on the MacBook, after
warm-up with no competing workload. Record its own machine id and measurements; the
existing M4 Pro baselines stay unchanged. New goldens or performance baselines require
the owner's approval. The owner still performs M5's L7 battle playtest.
