# Cloud gate diagnosis

- A successful XML subset does not establish a successful gate. M7 full-ci run 40972193346
  reported 216 passing core cases, but `runGameTestSlow` exceeded its unchanged ten-minute
  task timeout before the server wrote its final report. Eighteen of nineteen server cases
  had finished; `map_fifty` started only seventeen seconds before termination.
- Gradle project parallelism can overlap core soak with the server JVM even with two workers.
  On a two-core runner, inspect task timestamps before attributing a longer server run to
  simulation changes. Run 40974064459 still overlapped core soak and the server despite
  `--no-parallel`, then timed out. Full-ci now runs core slow/soak and server slow in two
  separate invocations, retaining vmstat and server logs. This tests scheduling contention
  without relaxing budgets; it does not by itself prove the cause of earlier failures.
- Distinguish aggregate Actions deadlines from scene or metric failures. M7 standard perf
  run 40941845938 hit its 55-minute job cap at 16/22 scenes; its complete suite requires the
  90-minute resource window. Partial passing reports remain partial evidence. Scene lengths,
  individual timeouts, CI factors, thresholds and accepted baselines must stay unchanged.
