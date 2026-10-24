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
| Available physical memory | 16,693,232 KiB reported by Windows |
| GPU | NVIDIA GeForce GTX 1060 6GB |
| Graphics driver | 32.0.15.8228 |
| JDK | Eclipse Temurin 21.0.12.1+1, Windows x64, project-local |
| Gradle | Wrapper 9.2.1; at most 2 workers |
| JVM | Project defaults: 2 GiB daemon heap, Parallel GC, UTF-8 |
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

## Checks required on the MacBook M5

Use the same commit, Minecraft and NeoForge versions and Java 21. Record the exact model,
macOS, memory, JDK, display resolution, GUI scale and render distance. Run the functional
gates (`verify`, `verifySlow`, `verifySoak`) and the M5 consent batch on macOS, with and
without the compatibility profile. Compare macOS images with their macOS goldens only.
Run the battle and mounted-unit performance checks and PERF-14 on the MacBook, after
warm-up with no competing workload. Record its own machine id and measurements; the
existing M4 Pro baselines stay unchanged. New goldens or performance baselines require
the owner's approval. The owner still performs M5's L7 battle playtest.
