# fix-main performance recovery on Windows x64

Status: local Windows CI-profile validation complete; ready for review. Independent Ubuntu and Mac validation remain pending.

## Environment and scope

Windows 10 Home 19045 x64; Intel Core i5-9400F (6 cores/6 threads); approximately
16 GiB RAM; GTX 1060 6 GB; Temurin JDK 21.0.12.1+1; Gradle 9.8.2; High Performance
power plan. Tests use an ASCII drive mapping and one configured path worker to match the
historical CI worker count. Logs, temporary drive paths and local credentials are not evidence
published in this repository. Python and the operator's game are never pause targets.
Completed acceptance gates kept Discord and Spotify running. The full profile resumed
after reboot with Discord running and Spotify absent; both application states are recorded.

Windows headless evidence does not validate MacBook M5. Existing Mac baselines, performance
limits and the CPU invalidation limit remain unchanged. A maintainer controls the independent
Ubuntu perf workflow; the developer does not approve or merge this work.

## Findings

Historical run 40176248614 at 3705118 failed idle ratio 0.3255060777 (limit 0.30)
and path D p99 50.288387 ms (CI limit 6 ms), including 49.743357 ms path join p99.
Its reported foreign CPU loads do not explain away those failures.

A later valid native D measurement stopped at 6.4767 ms despite only 0.0372 ms join p99.
The fixture constructed the entire voxel map 576 times while slicing its pages; streaming
JFR samples attribute about 10.4 GiB of temporary allocations to VoxelWorld construction,
with repeated G1 humongous allocations/full compactions. PathLoad now creates one voxel
representation and slices every page from it. Exact overlap of the old maximum sample
with a GC pause was not recorded, so this is documented allocation pressure rather than
an invented per-sample causal timestamp.

PathLoad measured the first group tick immediately after the scattered-order tick. STRICT
K=2 received only one 50 ms interval before delivery. StepPacer now includes the first interval
and counts tick work inside it; jobs, worker budget, K and tick delivery remain unchanged.

Idle comparison previously used accelerated GameTest ticks and measured idle before walking
had warmed shared code paths. A completed JFR shows walking-triggered deoptimizations and
late C2 compilation of Avoidance after idle was measured. Both modes are now preconditioned
on a disposable match, followed by the original workload in a fresh match. Each measured
mode retains 300 units, 200 warm-up ticks, 600 samples and the same orders and 30% limit.

Runtime caches ground geometry by centre column, own floor and terrain version, and footprint
support by the exact footprint column bounds, own floor and version. Small avoidance steps
inside the same cells reuse the same geometric result. Per-tick height easing remains active,
including rising/falling and external Y changes. Velocity reset, avoidance cadence, pushed
countdown and latch checks continue. Radius changes feed the fresh footprint bounds.
A stationary zero-velocity ORCA preference bypasses the solver only when a conservative
full-horizon clearance bound proves it feasible; crowded separation and interactions remain.
Derived caches do not enter snapshots or hashes. Experimental spatial probes were removed;
the original ordered neighbour query remains unchanged.

The 1.5x fixture now sets the real engine rate to 30 TPS and aligns vanilla work deadlines,
then restores the prior rate. Its previous 20 TPS engine budget and stale setup deadlines
could admit too much optional chunk work into the 1.5x measurement.

Windows foreign CPU uses shared-window cumulative Win32 system/process counters instead of
subtracting recent-load samples with different intervals. Missing counters fail; no load is
forged. Each report identifies the CPU source. Loaded and interrupted runs remain invalid.

The protected-index test used different probe-object working sets at its two index sizes.
Both 64K probe arrays now contain distinct immutable positions; lookup counts, seven medians,
4/64 sections and the ratio limit 3 remain unchanged. The index implementation is unchanged.

## Acceptance results

The frozen support-cell candidate completes all listed local gates. The complete profile
passes in 38m 59s on 2026-10-27 after a fresh uninterrupted launch at 10:39 local.
The earlier attempt ended when Windows powered off at 02:12 and is not accepted as a pass.
No implementation changes were made between the completed functional/JMH gates and this profile.

| Gate | Result | Duration / evidence |
|---|---|---|
| verifyFast | Pass | 2m 16s |
| Full JMH, jmhCompare, jmhSpeedRatio | Pass | 21 benchmarks, 6m 42s |
| verify, verifySlow, verifySoak | Pass | 1,918 tests, zero failures/errors/skips, 17m 38s |
| Five-case short CI perf profile | Pass | 2m 32s, no invalid CPU windows |
| Separate first_loop_fast CI profile | Pass | 4m 23s |
| Complete 18-case CI perf profile | Pass | 38m 59s, no invalid windows |

The short navigation profile records idle ratio 0.2840489053 against 0.30 (foreign CPU
0.088614), D p99 0.8638 ms against 6 ms (CPU 0.090951), and group D p99 0.9916 ms
against 6 ms (CPU 0.143778). Page capture is report-only, CPU 0.236364. Prewarm reports
metadata without a verdict by design. The separate 1.5x loop records C p95 4.4214 ms
against the unchanged 45.999999 ms limit, foreign CPU 0.125554.

Full JMH records IdleBench 119.487539 us/op versus the same-host control's
138.533267 us/op (13.7% lower). Maximum candidate/control score ratio is 1.063532
against the unchanged 1.15 gate; kernel allocation gates pass. The existing speed-ratio
task reports 2.529726 across 19 shared M4 Pro reference benchmarks. This is descriptive
Windows evidence, not MacBook M5 validation or promotion of a new approved baseline.

Functional XML totals: 1,310 core, 3 slow core, 200 seeded soaks, 73 journal replays,
95 Minecraft unit tests, 12 architecture checks, 212 normal and 13 slow GameTests.
Normal L3 wall time is 69.827s against 180s. No source changes follow these gates.

Discord and Spotify were running throughout the completed acceptance gates. After the
Windows reboot, the resumed full profile has Discord running and Spotify absent. The
operator authorized pausing either between runs if needed. The runner temporarily pauses
previously authorized Chrome, Telegram and QEMU processes, then resumes them in a finally
block. Python and Scavland remain excluded. Every perf window retains its real foreign-CPU
measurement and the unchanged 25% invalidation rule.

### Reproduction commands

Run serially with the documented JDK and environment. The comparison input is the retained
same-host diagnostic control, not an approved baseline. The complete perf invocation matches
`.github/workflows/perf.yml`; no local profile substitutions are hidden.

```text
gradlew verifyFast
gradlew --max-workers=1 verify verifySlow verifySoak
gradlew :core:jmh :core:jmhCompare :core:jmhSpeedRatio '-Pjmh.baseline=build/fix-main-idle-control-jmh.json'
gradlew --max-workers=2 :mod:runGameTestPerf '-Pperf.ciFactor=3' '-Psoak.ticks=2400' '-Ploop.ticks=3600'
```

Gradle reports 38m 57s for the complete perf task; the recorded stage including report
archival is 38m 59s. Independent CI/Mac validation retains its own machine and baseline.

### Complete CI-profile reports

Complete existing 18-case CI perf profile: pass in 38m 59s, no failures or invalid CPU windows. Idle ratio 0.2844054441/0.30; D p99 0.6023 ms/6 ms, join p99 0.0293 ms, zero unfinished joins. Full-profile 1.5x C p95 3.6675 ms/45.999999 ms.

| Scenario | Verdict | Foreign CPU |
|---|---|---|
| block_writer_cost | pass | 8.333% |
| commander_pan_move_cost | pass | 24.948% |
| crowd_soak_600 | pass | 7.793% |
| economy_soak | pass | 4.709% |
| economy_stress | pass | 5.783% |
| first_loop_fast | pass | 4.350% |
| first_loop_soak | pass | 4.515% |
| first_loop_stress | pass | 3.716% |
| group_paths_budget_d | pass | 2.694% |
| idle_300 | pass | 3.831% |
| melee_soak_200 | pass | 4.803% |
| page_capture_and_publication | report-only | 3.788% |
| path_budget_d | pass | 3.072% |
| prewarm_512_generated | Prewarm metadata | Not sampled |
| snapshot_500 | pass | 8.000% |
| stress_1200 | pass | 4.533% |
| viewer_bytes_600 | report-only | Not sampled |
| walk_600 | pass | 7.323% |

Every guarded window is at or below 25%. The commander window is close to the guard
at 24.948%; it remains a recorded valid observation, not a discarded or retried sample.
The no-CPU viewer report and prewarm metadata do not claim CPU measurements.

## Native diagnostics versus the historical CI gate

The unscaled Windows diagnostic is not entirely green: the refined candidate records idle
0.289988 (pass), ordinary D p99 2.3812 ms (above 2 ms), and an invalid group CPU window
while requested applications were restored. These are retained as diagnostics, not acceptance.
The historical CI gate is 6 ms: the unchanged workflow uses ciFactor=3, soak=2400 and
loop=3600. The complete final sequence passes that existing 18-case profile
without invalid CPU windows. No threshold or workflow is edited to get a green verdict.
Mac native-budget validation remains pending and Windows does not replace it.

## Rejected evidence and review boundaries

Keep failures distinct from invalid observations. The earlier full profile had two failures
and four invalid reports; a large JFR export overlapped part of that diagnostic run. It is not
clean acceptance evidence. A subsequent full JMH was interrupted by a changed/restarted host.
The fixture-only control on 2026-10-27 also exceeded the CPU guard and is not a valid A/B result.

No baseline or threshold is promoted. MacBook M5 and independent maintainer CI validation
remain pending. M5 development is outside this claim.

Raw generated perf/JMH/XML evidence stays under ignored build output, as required by
[the artifact policy](../05-testing.md). This report
publishes the environment, exact commands and measured summaries; it does not commit
logs, machine baselines or binary JFR recordings. A sanitized local review bundle is
retained separately, with its JVM path replaced by a generic placeholder.
