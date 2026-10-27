# main-save-reload — local JMH evidence

## Verdict

Performance validation remains unresolved; this is a WIP checkpoint, not a hand-in. The unchanged 15% gate failed in three comparisons. No threshold or approved baseline was changed, and no application was paused or terminated.

- First candidate versus historical accepted same-host control: six breaches, maximum ratio 1.238304; full run 6m 51s.
- Same first candidate versus one fresh pristine bb800d0 control: six breaches, maximum ratio 1.330130; control run 6m 41s.
- One bounded confirmatory candidate with CPU telemetry versus the fresh control: two breaches (cross384 and flat512), maximum ratio 1.253488; full run 6m 33s. No further candidate retry was made.

## Controls and scope

All four results contain the same 21 benchmark keys and parameters. JVM version, executable, arguments, forks, warm-up and measurement configuration match. Runtime, benchmark and build sources of historical control f25e2c9 and pristine main bb800d0 differ only in docs. Every JMH class and all 54 navigation classes are byte-identical between candidate and fresh control; shared fixture classes differ only in the schema-3 fixture helper. Crossing setup markers also match: 384 uses 2,781 expansions / four waypoints; 512 uses 3,010 / seven; both flat searches reach the same 20,000 cap.

These facts and the observed variability warrant investigating measurement context. They do not establish a specific environmental cause or turn a red performance gate green. Native-budget / independent quiet-host or maintainer CI validation is still required.

## Same-window CPU context

The confirmatory run captured 395 one-second windows with 0 counter errors. Weighted foreign busy fraction: 11.035%; peak: 68.010%; 64 windows exceed 25%. The probe retained 46 matching-JDK process handles across JVM exits, subtracting their cumulative CPU from common system total/idle windows. It uses the same GetSystemTimes/GetProcessTimes accounting model as WindowsCpuTimes; the temporary monitor is under ignored build output.

CPU utilization and the 15% execution-time regression limit are different quantities. CPU data includes setup/warm-up as well as measurements; per-benchmark attribution below is approximate, using sampled log offsets. It is context evidence rather than a modified JMH gate.

- `dev.strategos.core.bench.CrossingBench.cross384`: 22 windows; mean foreign CPU 20.588%, peak 67.448%, 8 windows above 25%.
- `dev.strategos.core.bench.CrossingBench.flat512`: 25 windows; mean foreign CPU 27.907%, peak 57.031%, 15 windows above 25%.

## Scores

Ratios are candidate/fresh control; values above 1.15 fail. These are reported means, not statistical significance claims.

| Benchmark | Unit | Historical | First candidate | Fresh control | Confirmation | First/fresh | Confirm/fresh |
|---|---|---:|---:|---:|---:|---:|---:|
| bench.CrossingBench.cross384 | ms/op | 0.961534 | 1.10697 | 0.921987 | 1.06369 | 1.200630 | 1.153689 |
| bench.CrossingBench.cross512 | ms/op | 1.06688 | 1.30777 | 1.02237 | 1.05595 | 1.279152 | 1.032848 |
| bench.CrossingBench.flat384 | ms/op | 8.5051 | 10.4991 | 7.89331 | 8.66079 | 1.330130 | 1.097231 |
| bench.CrossingBench.flat512 | ms/op | 8.6106 | 9.41307 | 7.92032 | 9.92803 | 1.188471 | 1.253488 |
| bench.HealthBarBench.bars300 | us/op | 4.8628 | 5.53905 | 5.56122 | 4.91706 | 0.996014 | 0.884170 |
| bench.HudBench.hudFrame200 | us/op | 2.95455 | 3.33588 | 3.40568 | 3.44987 | 0.979504 | 1.012976 |
| bench.MatchBusBench.publish1000 | us/op | 32.371 | 33.5899 | 34.8591 | 32.9573 | 0.963590 | 0.945441 |
| bench.MovementBench.step600 | us/op | 68.5927 | 77.6847 | 74.3399 | 63.8977 | 1.044994 | 0.859535 |
| bench.NodeDiscoveryBench.provingGround | ms/op | 88.3423 | 103.565 | 94.8938 | 86.7243 | 1.091381 | 0.913909 |
| bench.PageBuilderBench.build | us/op | 94.8088 | 90.1947 | 111.994 | 88.6481 | 0.805353 | 0.791543 |
| bench.PhaseTimersBench.overhead | us/op | 0.319495 | 0.361315 | 0.331806 | 0.308861 | 1.088936 | 0.930850 |
| bench.SnapshotCodecBench.encode500 | us/op | 698.111 | 864.474 | 936.677 | 619.544 | 0.922916 | 0.661428 |
| bench.SpatialBench.query1000 | us/op | 983.388 | 1106.95 | 1075.84 | 947.095 | 1.028919 | 0.880334 |
| bench.SpatialBench.rebuild1000 | us/op | 10.2295 | 9.81377 | 9.8542 | 9.77777 | 0.995898 | 0.992244 |
| bench.StatReadBench.cachedRead | ns/op | 4.39053 | 4.08861 | 4.82377 | 4.06856 | 0.847597 | 0.843441 |
| bench.StateHashBench.hash600 | us/op | 29.6153 | 28.0416 | 29.5008 | 27.1284 | 0.950536 | 0.919579 |
| bench.TemplateCompileBench.compile500 | ms/op | 16.9725 | 18.9687 | 16.3053 | 13.5869 | 1.163344 | 0.833281 |
| sim.IdleBench.step300 | us/op | 119.488 | 138.278 | 125.768 | 117.149 | 1.099466 | 0.931464 |
| sim.MotionBench.step600 | us/op | 583.026 | 646.711 | 659.811 | 558.2 | 0.980146 | 0.845999 |
| sim.OrcaBench.step600 | us/op | 950.913 | 1090.27 | 1130.57 | 918.522 | 0.964355 | 0.812439 |
| sim.TerrainEditBench.rebuildOnePage | us/op | 739.633 | 842.492 | 702.768 | 712.382 | 1.198819 | 1.013680 |

## Raw local evidence

Raw output remains under ignored build paths; hashes pin the retained measurements. JVM paths and other host-specific metadata are not copied into this report.

- historical: `build/save-reload-control-jmh.json`, SHA-256 `4658f73d256e5f99d47627a13070f3df5fabda4edd53692396ee387496210614`.
- first: `build/save-reload-first-jmh.json`, SHA-256 `03b19600f4119929f658b825ff9a7822ed0daf9ad5553f221e6c3cd6b03003be`.
- fresh: `build/save-reload-fresh-control-jmh.json`, SHA-256 `ab9589546647c51a229aec7d2d01611dc5e53a15dc7c38f0330f41e49589b91d`.
- confirm: `build/save-reload-confirm-jmh.json`, SHA-256 `4561a1bc6e83aa3009cf204857abd087067363ec23dbfcc7e1ee8cccbf99087c`.
- CPU windows: `build/save-reload-jmh-cpu.csv`; per-benchmark context: `build/save-reload-cpu-by-benchmark.json`.

## Functional verification

`verify verifySlow verifySoak` passed in 19m 16s with one Gradle worker: 1,936 tests, zero failures/errors/skips. No production or test source changed after that run. Windows verify alone took approximately 9m 10s; its budget exception is recorded for the owner.
