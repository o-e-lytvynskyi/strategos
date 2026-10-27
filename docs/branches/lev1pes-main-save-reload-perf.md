# main-save-reload — performance evidence

## Verdict

The independent cloud same-runner JMH gate passed on 2026-10-28: all 21 benchmarks, maximum candidate/control 1.095538, and every existing allocation check passed. Standard cloud server perf also passed all 18 required GameTests (details below). The three local failures below remain part of the evidence. No threshold or approved baseline was changed, and no application was paused or terminated.

- First candidate versus historical accepted same-host control: six breaches, maximum ratio 1.238304; full run 6m 51s.
- Same first candidate versus one fresh pristine bb800d0 control: six breaches, maximum ratio 1.330130; control run 6m 41s.
- One bounded confirmatory candidate with CPU telemetry versus the fresh control: two breaches (cross384 and flat512), maximum ratio 1.253488; full run 6m 33s. No further candidate retry was made.

## Controls and scope

All four results contain the same 21 benchmark keys and parameters. JVM version, executable, arguments, forks, warm-up and measurement configuration match. Runtime, benchmark and build sources of historical control f25e2c9 and pristine main bb800d0 differ only in docs. Every JMH class and all 54 navigation classes are byte-identical between candidate and fresh control; shared fixture classes differ only in the schema-3 fixture helper. Crossing setup markers also match: 384 uses 2,781 expansions / four waypoints; 512 uses 3,010 / seven; both flat searches reach the same 20,000 cap.

These facts and the observed variability warranted investigating measurement context. They do not establish a specific environmental cause or invalidate a local failure. The independent same-runner cloud comparison below subsequently passed. It does not claim validation against native Mac budgets or replace an approved machine baseline.

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

## Cloud same-runner JMH — 2026-10-28

[Run 40595834693](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40595834693) passed. Control: pristine main `bb800d0e526bf4d05d5bf2bcc3b71797e52e1d6b`; candidate: `8e98d8b755f819bf042ff6b80309c243ac970d00`. The candidate's core, mod and build-logic trees are identical to the functionally verified f9f9846; intervening commits change only the workflow and branch record.

Ubuntu 24.04, four vCPUs, AMD EPYC 9V74, Temurin 21.0.12.1+1. Compile both benchmark sets first, then run control and candidate sequentially on the same runner. Each full measurement took 6m 16s. All 21 keys, parameters, JVM arguments, heap/GC, forks, warm-up and measurement settings and score units match. The existing `:core:jmhCompare` task passed with its unchanged 1.15 limit and allocation checks, using the raw control report as an explicit one-run comparison input. No approved baseline was written or promoted.

Maximum ratio: 1.095538 (MatchBusBench.publish1000, 9.554%). Snapshot encoding: 1.006691. Both formerly failing Crossing cases pass here: cross384 1.012270; flat512 0.993781. This is a fresh independent cloud comparison, not a retry of the local candidate under unchanged conditions. It does not establish why the local failures occurred, and those results remain above.

vmstat context: 376 one-second samples per measurement after excluding the first cumulative sample. Mean idle: control 68.245%, candidate 68.162%; mean and peak steal 0% for both. These are system-level context, not measurements of foreign-process CPU. Existing FirstLoopScene and SpatialBench compiler warnings occur in both control and candidate; no new warning is introduced by the repair.

| Benchmark | Unit | Control | Candidate | Candidate/control |
|---|---|---:|---:|---:|
| bench.CrossingBench.cross384 | ms/op | 0.739882 | 0.74896 | 1.012270 |
| bench.CrossingBench.cross512 | ms/op | 0.847476 | 0.845015 | 0.997096 |
| bench.CrossingBench.flat384 | ms/op | 6.69652 | 6.67663 | 0.997031 |
| bench.CrossingBench.flat512 | ms/op | 6.69793 | 6.65627 | 0.993781 |
| bench.HealthBarBench.bars300 | us/op | 1.97797 | 1.98343 | 1.002758 |
| bench.HudBench.hudFrame200 | us/op | 2.06792 | 2.06602 | 0.999080 |
| bench.MatchBusBench.publish1000 | us/op | 21.5557 | 23.615 | 1.095538 |
| bench.MovementBench.step600 | us/op | 43.0933 | 42.4114 | 0.984176 |
| bench.NodeDiscoveryBench.provingGround | ms/op | 78.1093 | 79.4073 | 1.016617 |
| bench.PageBuilderBench.build | us/op | 73.9215 | 73.8627 | 0.999205 |
| bench.PhaseTimersBench.overhead | us/op | 0.304994 | 0.305308 | 1.001032 |
| bench.SnapshotCodecBench.encode500 | us/op | 464.581 | 467.69 | 1.006691 |
| bench.SpatialBench.query1000 | us/op | 831.359 | 836.271 | 1.005908 |
| bench.SpatialBench.rebuild1000 | us/op | 7.21744 | 7.34818 | 1.018115 |
| bench.StatReadBench.cachedRead | ns/op | 3.67154 | 3.66397 | 0.997938 |
| bench.StateHashBench.hash600 | us/op | 28.4927 | 28.6824 | 1.006659 |
| bench.TemplateCompileBench.compile500 | ms/op | 8.69133 | 8.62095 | 0.991902 |
| sim.IdleBench.step300 | us/op | 44.9348 | 45.4272 | 1.010959 |
| sim.MotionBench.step600 | us/op | 477.198 | 484.797 | 1.015924 |
| sim.OrcaBench.step600 | us/op | 774.641 | 768.979 | 0.992692 |
| sim.TerrainEditBench.rebuildOnePage | us/op | 545.864 | 542.204 | 0.993295 |

Raw JSON SHA-256:

- candidate: `c3d480bbcee0aa762cbf2a082133965917d000266524707263ebc9d1659cf9c3`.
- control: `d9dd0e98e73db5ca00519520ad890de6aebab45e7fbb2a268c71c9eb66a0231e`.

Artifact: `paired-jmh-reports` (id 12776902393), including both raw JSON reports, pinned commits, JDK/CPU metadata and vmstat. Artifacts have seven-day retention; copies and downloaded logs remain in ignored `build/audit-tools/cloud-save-reload/40595834693/` in the original checkout. No personal paths or raw machine metadata are committed.

Preparation-only run [40595484377](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40595484377) failed before measurement because jmhJar does not exist in this plugin-free build. Replace that step with the actual source-set jmhClasses task. Preserve the failed run/log; it produced no benchmark scores.

## Standard cloud server perf — 2026-10-28

[Run 40594606609](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40594606609), candidate f9f9846, passed in 45m 59s. The server log confirms all 18 required GameTests passed in 37.67 minutes. Standard workflow arguments were unchanged: `-Pperf.ciFactor=3 -Psoak.ticks=2400 -Ploop.ticks=3600`; cloud budgets and shortened soak windows are not native Mac-budget validation. JMH produced all 21 results and the speed-ratio report; this standard workflow deliberately does not compare JMH against the Mac baseline.

All 18 server report files were inspected, including their configured report-only metrics. There is no failed/invalid top-level verdict. The maximum reported foreignCpu fraction among these reports is 0.060370 (6.037%), below the unchanged 0.25 invalidation rule; these report aggregates are not instantaneous CPU peaks.

| Report | Top-level result | Reported foreign CPU |
|---|---|---:|
| block_writer_cost.json | pass | 1.111% |
| commander_pan_move_cost.json | pass | 1.149% |
| crowd_soak_600.json | pass | 0.271% |
| economy_soak.json | pass | 0.238% |
| economy_stress.json | pass | 0.265% |
| first_loop_fast.json | pass | 0.349% |
| first_loop_soak.json | pass | 0.262% |
| first_loop_stress.json | pass | 0.225% |
| group_paths_budget_d.json | pass | 0.196% |
| idle_300.json | pass | 0.230% |
| melee_soak_200.json | pass | 0.249% |
| page_capture_and_publication.json | report-only | 6.037% |
| path_budget_d.json | pass | 0.276% |
| prewarm_512_generated.json | checked by GameTest | not recorded |
| snapshot_500.json | pass | 4.935% |
| stress_1200.json | pass | 0.227% |
| viewer_bytes_600.json | report-only | not recorded |
| walk_600.json | pass | 3.473% |

Artifact: `perf-reports` (id 12777882458), 6,661,061 bytes, seven-day retention. Raw reports and full logs were downloaded to ignored `build/audit-tools/cloud-save-reload/40594606609/` in the original checkout. Copies remain local beyond GitHub artifact retention.

The opt-in paired workflow was temporary validation scaffolding, preserved at 8e98d8b and in its successful run. After inspecting both successful runs, restore perf.yml byte-for-byte from bb800d0 before hand-in. The final bug-fix diff does not add a permanently pinned one-off CI mode. Production, benchmark and test sources remain identical to those already verified.
