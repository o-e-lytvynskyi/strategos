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
| Dependency cache | Project-local `build/gradle-home`, initially empty |

Commands run from the repository root in PowerShell with `JAVA_HOME` pointing to
`build/toolchains/jdk-21.0.12.1+1` and `GRADLE_USER_HOME` to `build/gradle-home`.
Python documentation checks use `python -X utf8`.

## Results

Bootstrap `verifyFast` is in progress. Results will be recorded here as the commands finish;
an attempted or pending run is not a pass.

## Checks required on the MacBook M5

Use the same commit, Minecraft and NeoForge versions and Java 21. Record the exact model,
macOS, memory, JDK, display resolution, GUI scale and render distance. Run the functional
gates (`verify`, `verifySlow`, `verifySoak`) and the M5 consent batch on macOS, with and
without the compatibility profile. Compare macOS images with their macOS goldens only.
Run the battle and mounted-unit performance checks and PERF-14 on the MacBook, after
warm-up with no competing workload. Record its own machine id and measurements; the
existing M4 Pro baselines stay unchanged. New goldens or performance baselines require
the owner's approval. The owner still performs M5's L7 battle playtest.
