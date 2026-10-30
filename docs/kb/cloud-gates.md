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
