# Cloud gate diagnosis

- A successful XML subset does not establish a successful gate. M7 full-ci run 40972193346
  reported 216 passing core cases, but `runGameTestSlow` exceeded its unchanged ten-minute
  task timeout before the server wrote its final report. Eighteen of nineteen server cases
  had finished; `map_fifty` started only seventeen seconds before termination.
- Gradle project parallelism can overlap core soak with the server JVM even with two workers.
  On a two-core runner, inspect task timestamps before attributing a longer server run to
  simulation changes. Full-ci now uses `--no-parallel` and retains vmstat with its reports;
  this tests scheduling contention without relaxing test budgets. It does not by itself prove
  the cause of an earlier failure.
- Distinguish aggregate Actions deadlines from scene or metric failures. M7 standard perf
  run 40941845938 hit its 55-minute job cap at 16/22 scenes; its complete suite requires the
  90-minute resource window. Partial passing reports remain partial evidence. Scene lengths,
  individual timeouts, CI factors, thresholds and accepted baselines must stay unchanged.
