# Cloud gate diagnosis

- A successful XML subset does not establish a successful gate. M7 full-ci run 40972193346
  reported 216 passing core cases, but `runGameTestSlow` exceeded its unchanged ten-minute
  task timeout before the server wrote its final report. Eighteen of nineteen server cases
  had finished; `map_fifty` started only seventeen seconds before termination.
- Gradle project parallelism can overlap core soak with the server JVM even with two workers.
  On a two-core runner, inspect task timestamps before attributing a longer server run to
  simulation changes. Run 40974064459 still overlapped core soak and the server despite
  `--no-parallel`, then timed out. Run 40975691944 finished core slow/soak before starting
  the server in a separate invocation and still hit the ten-minute server timeout. Overlap
  is therefore not a sufficient explanation. The diagnostic scheduling changes were removed;
  full-ci retains vmstat and server logs. Do not raise a timeout or call this a runtime
  regression without attributing the delay against a matched control and inspecting the harness.
- Distinguish aggregate Actions deadlines from scene or metric failures. M7 standard perf
  run 40941845938 hit its 55-minute job cap at 16/22 scenes; its complete suite requires the
  90-minute resource window. Partial passing reports remain partial evidence. Scene lengths,
  individual timeouts, CI factors, thresholds and accepted baselines must stay unchanged.

- A wide matrix can fail before any test when every clean runner downloads the same Maven
  modules. In run 43954269214, seeds 10 and 15 received HTTP 429 for Spotless dependencies
  while compiling build-logic; passing functional XML did not make the full gate green.
  Pack only the resolved `modules-2` dependency cache from the successful prerequisite job
  and restore that same-run artifact in the matrix. Never archive the entire Gradle home
  (which can contain credentials), reuse project outputs as test evidence, or retry until green.
  All twenty source builds, match/replay assertions and the exact-commit aggregate remain required.

## Recover a pinned formatter without changing it

A successful `verifySlow` module-cache producer does not imply Spotless dependencies
were resolved. A later verify may still need its exact formatter, and an old module
metadata restore can coexist with an incomplete branch cache. Normal Central and its
official alias can also return 404 before compilation; these runs prove nothing about
the implementation tests.

If a successful same-branch dispatched verify already archived the approved formatter
POM/JAR under `build/reports/central-endpoint`, `formatter_cache_run` can recover those
two files. The producer must match the repository, head repository, branch, workflow,
trigger and successful completed status. Recovery checks the already pinned SHA-256
for both files before writing either, rejects symlink paths and oversized files, and
publishes only `com.palantir.javaformat:palantir-java-format:2.102.0`. An optional init
script routes only that exact coordinate to the recovered repository. Other dependencies,
versions, checks and test gates remain unchanged. Counterexamples run in the cloud before
recovery; a cache producer is never acceptance evidence for the candidate.

The exact-coordinate recovery repository belongs only to the main Strategos build:
adding any project repository to `build-logic` shadows its settings repositories.
Discard the downloaded producer reports immediately after recovering approved files,
before Gradle and report discovery. Producer XML is never candidate test evidence.

## Preserve positive visual evidence before the negative control

The altered-golden check invokes the same `scenarioDiff` task and overwrites its
`diff/report.json`. A successful workflow can therefore publish a final report
containing deliberately failed frames. Inspect the normal step and the negative
step separately; never call those negative verdicts a normal comparison failure.
The scenario workflow now preserves `normal-report.json` before the altered check
and `altered-report.json` afterward, retaining both unmodified verdicts.

Minecraft GameTestSequence.thenExecute catches GameTestAssertException and
marks the test failed, but can continue later sequence actions in that same tick.
A failed pre-save assertion followed by a stale-positive wait can reach a second
resume and crash the whole server with RUNNING -> RUNNING, hiding the original
case from XML. Guard later mutation phases with a flag set only after the prior
assertions and restore complete. Preserve the original assertion and report its
actual diagnostic; do not weaken it or make lifecycle transitions idempotent.

A saved-world client can automatically enter its RTS screen during the loading
phase, before a scenario setup callback runs. A readiness gate that requires
exactly a null screen deadlocks genuine reconnect tests. Accept the actual RTS
gameplay screen too while retaining the player/level/all-rendered-section
conditions. Reopen the saved world through WorldOpenFlows rather than creating
a fresh same-named world; prove a new runtime and automatic UUID seat reclaim,
and keep every world switch and the whole scenario bounded.
