# fix-main on dev/lev1pes/fix-main

**Developer:** lev1pes · **Branch:** dev/lev1pes/fix-main · **Work:** fix-main · **Base:** main @ b207e50 · **Claimed:** 2026-10-25 · **Status:** ready for review

## Now

- **Hand-in:** [PR #21](https://github.com/o-e-lytvynskyi/strategos/pull/21), implementation commit f25e2c9; ready for human review.

- **Milestone:** repair the two historical main perf failures; claim #13 is human-merged.
- **Last finished task:** frozen candidate passes all local functional gates (1,918 tests), 21 JMH benchmarks/comparator, short navigation, separate fast-loop and the complete 18-case existing CI perf profile (38m 59s).
- **Next task:** human review and independent maintainer CI/Mac validation. This session stops after hand-in; M5 is untouched.
- **Merged from main last:** b207e50; only own dev/lev1pes/fix-main history is published, without rebase or force push.

## Depends on unfinished work

- No unfinished milestone is required: reproduce against the code already on main, independently of the legacy M5 branch.
- Developer registration [#12](https://github.com/o-e-lytvynskyi/strategos/pull/12) is merged; assignment #13 is also human-merged (verified through GitHub).

## Log

### 2026-10-27 - hand-in report

Repair the two performance failures from run 40176248614: idle_300_costs_a_third and path_budget_d. Claim #13 was assignment only; this PR delivers the implementation.

## Built

- Restore real asynchronous tick intervals, including the first STRICT K=2 deadline. Construct one proving-ground voxel world instead of 576 full copies when building pages.
- Precondition both idle/walking paths before the unchanged measured workload. Reuse exact resting support-cell geometry and bypass avoidance solving only when zero velocity is conservatively feasible; preserve neighbour selection, reactions and height easing.
- Align the 1.5x GameTest engine rate/deadlines and restore the previous rate. Measure Windows foreign CPU over shared cumulative system/process windows. Equalize protected-index probe-object locality without changing its lookup count or limit.

## Gates

Windows 10 x64, i5-9400F (6 cores), approximately 16 GiB RAM, JDK 21.0.12.1+1, Gradle 9.8.2, one path worker. Completed gates before the final full profile kept Discord and Spotify running; the resumed full profile has Discord running and Spotify absent after reboot.

- verifyFast: pass, 2m 16s.
- verify + verifySlow + verifySoak: pass, 17m 38s; 1,918 tests, zero failures/errors/skips; normal L3 69.827s/180s.
- Full JMH and jmhCompare against the same-host diagnostic control: pass, 21 benchmarks, 6m 42s; maximum score ratio 1.063532/1.15, allocation checks pass. IdleBench improves 138.533 to 119.488 us/op (13.7%). No approved baseline is replaced. Speed ratio to M4 Pro is descriptive only.
- Short navigation CI profile and separate 1.5x profile: pass.
- Complete existing 18-case CI perf profile: pass in 38m 59s, no failures or invalid CPU windows. Idle ratio 0.2844054441/0.30; D p99 0.6023 ms/6 ms, join p99 0.0293 ms, zero unfinished joins. Full-profile 1.5x C p95 3.6675 ms/45.999999 ms.

## Deviations and decisions

No feature scope, threshold, workflow or approved baseline changes. UNIT-05 records paired preconditioning as a scope-neutral measurement refinement. Runtime caches are derived and do not change saved state; remove RestingGround and the feasible-zero shortcut to undo that optimization. Experimental spatial probes were rejected and removed. No delegates were used. Earlier failures, invalid load windows and interrupted runs remain recorded separately; the final profile was restarted because Windows powered off, not to select a passing result. L4 is not required for these core/test-fixture changes; no client UI changes.

## For the owner

1. Review this implementation and resolve the open release proposal #20 consistently with the retained assignment; this session does not approve or merge either PR.
2. Run independent Ubuntu perf through the existing maintainer-controlled label. MacBook M5/native-budget validation remains pending; Windows is not Mac evidence. The same-host JMH control is diagnostic, not a new approved baseline.
3. Review the documented serial Windows verify duration; no gate was moved or weakened. M5 is untouched and this session stops after hand-in.

See [branch record](https://github.com/o-e-lytvynskyi/strategos/blob/dev/lev1pes/fix-main/docs/branches/lev1pes-fix-main.md) and [Windows report](https://github.com/o-e-lytvynskyi/strategos/blob/dev/lev1pes/fix-main/docs/testing/fix-main-windows-x64.md).


### 2026-10-27 - both historical failures pass within the full profile

- The uninterrupted resumed CI profile passes idle_300 at 189,699 ns / 667,002 ns = 0.2844054441 against 0.30; foreign CPU 0.038313. Path D passes p99 0.6023 ms against 6 ms, join p99 0.0293 ms, no unfinished joins; one worker, STRICT K=2, W=1,600,000; foreign CPU 0.030725.
- Six reports are complete and passing, including economy stress and 1.5x first loop (C p95 3.6675 ms / 45.999999 ms, foreign CPU 0.043504). Full-profile acceptance remains pending while the other cases run. No source, limit or workload changes.

### 2026-10-27 - final 1.5x acceptance

- The final first_loop_fast CI-profile scenario passes in 4m 23s: C p95 4.4214 ms against 45.999999 ms; foreign CPU 0.125554. The earlier additional 1.5x failure is not present in this run. The source and both applications stay unchanged.
- The complete 18-case existing CI profile started next. It is the last performance gate, not a retry of a failed unchanged run; all source has remained frozen since the support-cell short-profile pass.

### 2026-10-27 - final functional acceptance

- The frozen support-cell candidate passes verify/verifySlow/verifySoak in 17m 38s. Actual XML totals: 1,310 core, 3 slow core, 200 seeded soaks, 73 journal replays, 95 mod unit tests, 12 architecture, 212 normal GameTests and 13 slow GameTests; 1,918 total, zero failures/errors/skips. Normal L3 wall time 69.827s against 180s.
- The sequence advanced to the 1.5x fast-loop CI-profile scenario. Discord and Spotify are still running; the operator's optional permission to pause Spotify has not been needed. Full 18-case perf remains pending. No code changes since the five-case short CI-profile pass.

### 2026-10-27 - final JMH accepted

- The frozen support-cell candidate passes all 21 JMH benchmarks, jmhCompare and jmhSpeedRatio in 6m 42s with Discord and Spotify running. IdleBench is 119.488 us/op versus the same-host control's 138.533 us/op (13.7% lower). Maximum candidate/control score ratio 1.063532 against the unchanged 1.15 gate; kernel allocation checks pass.
- The existing speed-ratio task reports 2.529726 over 19 benchmarks shared with the approved M4 Pro reference. It is descriptive Windows evidence, not MacBook M5 validation or a baseline promotion; the existing CI factor stays 3.
- The fresh complete functional aggregate has started. No code changes since the short CI-profile pass.

### 2026-10-27 - frozen final support-cell candidate

- VerifyFast passes in 2m 16s, including terrain/footprint/external-height and stale-grid reaction regressions. The complete five-case short CI profile then passes in 2m 32s: idle ratio 0.284049, foreign CPU 0.088614; D p99 0.8638 ms, foreign CPU 0.090951; group D p99 0.9916 ms, foreign CPU 0.143778. Page capture is report-only with CPU 0.236364; no invalid windows. Discord and Spotify remain running.
- Source is frozen for full acceptance: 21 JMH benchmarks plus comparison/speed ratio, verify/verifySlow/verifySoak, fast-loop at 1.5x, then all 18 existing CI-profile performance cases. Any failure stops the sequence and archives reports and JFR before another run. No further optimization is planned while these gates run.

### 2026-10-27 - exact support-cell geometry

- The original-radius early-exit probe also fails idle validly at 0.319943; its movement phase costs 148.6 us versus the previous candidate's approximately 123 us. Removed the entire probe/API and restored original ordered neighbour reporting. No new spatial API or AI-store coupling remains; the stale-grid reaction regression is retained.
- The candidate now caches ground by terrain version, centre column and own floor, and footprint support by version, own floor and the exact four footprint column bounds. Small idle avoidance steps inside those same cells do not change either geometric answer. Radius changes are reflected through freshly calculated footprint bounds; every cell/version/floor change recomputes.
- Height easing still runs each tick against the cached support floor, including external Y changes and both ticks of rising. Skipping an unchanged height preserves bitwise signed-zero normalization. A regression moves a resting body across a footprint edge without crossing its centre column, moves it back, then changes its height externally.
- Idle settling also passes its already verified ground result into the existing move routine, avoiding a second identical ground check. Walking and uncertain ground retain the reference path. New fast and short CI-profile gates are pending; full runtime gates must be refreshed.

### 2026-10-27 - preserve the original spatial snapshot semantics

- Source review found that shrinking a query radius needs extra guarantees: UnitGrid intentionally reflects the previous movement, while a store may already have a new position. Reproduced the narrowed prototype's missed same-tick reaction with an external displacement: self incorrectly remains at X=12.5. Added a regression requiring displacement in that same selected tick.
- Replaced the narrowed/global probe with a simpler early-exit predicate over the original full 3-block candidate range. It checks current AI/velocity, crowd count and clearance, exactly retaining the original candidate set and complete sorted solver on any uncertainty. No whole-match speed pass or reduced radius remains. Ordinary spatial reporting stays ordered; only a commutative safety predicate may exit early.
- The v2 JMH completed, then the next stage was deliberately held before starting any new build. That v2 result is diagnostic for the rejected prototype, not acceptance for the new source. Fresh fast, short CI-profile perf and full runtime gates are pending. Discord and Spotify stay on.

### 2026-10-27 - refined probe and unchanged CI profile

- The coarse whole-match exclusion probe fails idle at 0.309960 and is refined: the global maximum now only limits the search radius, capped at the original 3-block range; each found neighbour is checked against its own velocity. Counts still preserve the crowded separation fallback, and walking/agents/uncertainty use the full solver. No neighbour selection or original ordered reporting changes.
- The refined native diagnostic passes idle at 0.289988 (foreign CPU 0.063920) and records D p99 2.3812 ms, join p99 0.0531 ms. That D sample exceeds the optional unscaled Windows diagnostic limit 2 ms; it is not a native-budget pass. The group window is INVALID at foreign CPU 0.283301 while requested applications were being restored. These observations are retained, not rerun until green.
- The requested historical CI gate is 6 ms. `.github/workflows/perf.yml` already uses ciFactor=3, soak=2400 and loop=3600; the complete acceptance profile uses that existing command unchanged. Unscaled Windows diagnostics and Mac validation remain explicitly separate. No threshold, workflow or approved baseline is changed.
- Targeted refined IdleBench is 118.564 us/op. Full fresh JMH/comparison/speed-ratio, functional/slow/soak, fast-loop and all 18 CI-profile perf reports are now running serially with Discord and Spotify left on. Prior full runtime results are superseded until this completes.

### 2026-10-27 - bounded idle safety probe

- After the voxel-copy fix, ordinary D passes natively at p99 1.6492 ms, join p99 0.0581 ms and zero unfinished joins. Idle still fails validly at 0.300875 (foreign CPU 0.068783); this near miss is not accepted.
- The next candidate adds a conservative short safety probe when no unit is WALKING. One existing steering pass computes the maximum nonwalking L1 residual speed. A stationary self checks every same-layer body inside max(1.5, REST_GAP + HORIZON * maxSpeed); any possible approach, nearby agent or crowded fallback rejects the shortcut. Otherwise zero is feasible, without selecting/sorting ten neighbours. The full ordered neighbour solver is unchanged on a miss; other spatial callers retain ordered reporting.
- The unordered visitor is now used only for this order-independent safety/count reduction, not for selecting neighbours. A randomized test checks matching/filter/border/reused-slot parity and nested-query recovery. All final runtime gates must be refreshed for this candidate; prior successes are retained as history.

### 2026-10-27 - path fixture allocation pressure

- The final native group passes, including idle ratio 0.298180 (foreign CPU 0.117466) and group D p99 1.0188 ms. Ordinary path D fails validly at p99/max 6.4767 ms, join p99 only 0.0372 ms, no unfinished joins, foreign CPU 0.109601. The acceptance sequence stops; original reports are archived, not discarded or retried blindly.
- Streaming the existing JFR allocation samples attributes about 10,408 MiB to VoxelWorld construction in the path window. PathLoad called ProvingGround.voxels() once per page: 576 complete 384x384x64 voxel copies instead of one. JFR also shows repeated humongous allocations/full compaction pauses and a later young pause. This does not prove exact overlap with the maximum sample, but is concrete avoidable fixture pressure.
- PathLoad now constructs one voxel representation and slices all pages from it. Terrain, page count, jobs, rules, timing and gates remain unchanged. Targeted perf will validate allocation pressure as well as D; full functional runtime evidence and JMH remain retained, with new fixture checks pending.

### 2026-10-27 - complete functional gate

- Final verify/verifySlow/verifySoak passes in 16m 16s with one Gradle worker. Counts: 1,308 core, 3 slow core, 200 seeded soaks, 73 journal replays, 95 mod unit tests, 12 architecture, 212 normal GameTests and 13 slow GameTests; 1,916 total, zero failures/errors/skips. Normal L3 wall time 61.888s against 180s.
- The sequence advanced to native navigation/idle performance. Earlier functional counts and prototype findings are retained as history; this run validates the current runtime, pacing and CPU-accounting candidate.

### 2026-10-27 - complete JMH gate

- All 21 JMH benchmarks and jmhCompare pass in 6m 32s. Maximum candidate/control score ratio is 1.041156 against the unchanged 1.15 gate; kernel allocation checks pass. IdleBench improves from 138.533 to 124.135 us/op (10.4%). The same-host comparison is diagnostic and does not promote an approved Windows or Mac baseline.
- The acceptance sequence has advanced to full verify/verifySlow/verifySoak with one Gradle worker. No other build or performance measurement runs concurrently.

### 2026-10-27 - final candidate verification

- Paired JVM preconditioning plus confirmed-rest runtime optimization passes native idle: 197.001 us / 698.900 us = 0.281873, foreign CPU 0.198474. Both measured phases retain 200 warm-up ticks and 600 samples.
- The fixture-only control on unchanged main runtime is INVALID (foreign CPU 0.308364). Its ratio 0.303847 is diagnostic only and does not establish a regression or a valid before/after comparison. Do not select it as acceptance evidence.
- Restored the candidate that passed the valid paired measurement. Final verifyFast passes in 6s, and the complete serial acceptance sequence has started: all JMH benchmarks/comparison, full verify/slow/soak, native navigation/idle, fast-loop and complete CI perf profile. Any failed stage stops the sequence; no PR is opened before green.
- origin/main remains b207e50; UTF-8 roadmap check passes (245 cards). M5 is not started.

### 2026-10-26 - paired JVM preconditioning

- The horizon-bound candidate still fails native idle: 0.326548, foreign CPU 0.059547. No threshold is changed and the failure is retained.
- Bounded inspection of the existing JFR finds unstable-branch deoptimizations when walking starts at 21:24:38, followed by three C2 compilations of Avoidance.plan at 21:24:46–47, after idle had already been measured. This supports investigating sequential JVM profile bias; it does not by itself prove the entire regression is a fixture issue.
- The diagnostic candidate preconditions both idle and walking for the existing 200 ticks each, disposes that throwaway match, then creates the original 300-unit workload afresh. Measured phases retain 200 warm-up ticks and 600 samples each, the same orders, the same threshold and normal load guard. Metadata records these counts. The 3,000-tick timeout remains unchanged.

### 2026-10-26 — conservative horizon bound

- Confirmed-rest geometry reduces the targeted core idle benchmark to 124.963 us/op (original control 138.533), but the server gate still fails: idle 225.0 us / walking 689.7 us = 0.326229, foreign CPU 0.050295. Retained as a failure.
- The feasible-zero shortcut now accepts a nonwalking neighbour's small residual velocity only when `distance > reach + horizon * (abs(vx) + abs(vz))`. L1 speed bounds its total possible approach, so no collision can occur within the entire ORCA horizon; self velocity must still be zero. Crowded separation, walkers, close neighbours and external agents retain the full calculation.
- A deterministic 10,000-case test compares accepted bounds against the original full ORCA solver and rejects overlap/fast approach. No response cadence, horizon, gap or performance limit changes.

### 2026-10-26 — confirmed-rest geometry

- The unordered visitor is removed: its valid idle result still fails at 0.342968 (foreign CPU 0.221513), and the targeted core-only benchmark is 136.349 us/op versus the original control's 138.533 us/op. Its additional API is not justified by sufficient benefit.
- Diagnostic state reads show around 50/300 IDLE-AI units per tick have nonzero avoidance velocity, persisting through tick 1,201. Their behaviour and avoidance cadence remain unchanged.
- The new RestingGround cache bypasses repeated ground/height work only after a grounded body's height has stopped changing, with exact position/floor/radius/terrain-version keys, no rising flag and no planned displacement. Velocity reset, pushed countdown and latch checks still run. Full normal height easing is retained on a miss. The zero-plan shortcut also avoids an unnecessary idle speed lookup.
- A regression changes terrain under a resting footprint, checks both ticks of rising/falling, then orders movement off the cached position. All gates are pending for this candidate.

### 2026-10-26 — neighbour reporting profile

- After restoring the environment, cached-footing idle still fails validly: 0.335880, foreign CPU 0.211835, movement p50 136.2 us. Removed the experimental footing cache and its extra geometry state.
- A targeted diagnostic IdleBench stack profile (not the complete JMH gate) attributes the largest visible frames to UnitGrid.forEachInRadius and collect, ahead of footing and solver frames. The new candidate visitor reports matches directly without the intermediate index bitmask. Avoidance still selects by (distance, index), now explicitly tie-breaking regardless of candidate traversal order; other callers retain ordered reporting.
- A randomized regression compares unordered and ordered query matches across filters, free/reused slots and border positions, and verifies recovery after a rejected nested query. All complete gates remain required.

### 2026-10-26 — interrupted measurement environment

- The first optimized full JMH did not finish: the background execution disappeared, no Java processes or S: mapping survived, and Windows reported a later boot time. Its log ends mid-benchmark with an unwritten tail. It is not a complete result and cannot satisfy the gate. The original control JSON remains retained. Restored the free ASCII mapping and session lock after checking the environment; Python is never a pause target.

### 2026-10-26 — main synchronized

- Fast-forwarded origin/main from 1dae9fa to b207e50. Only claim/branch records changed; no code, build surface or developer instructions changed. fix-main remains assigned to lev1pes. M5 is now released from lev1pes and assigned to the owner through merged PRs #18/#19; no M5 development is performed here.
- UTF-8 roadmap validation passes: 245 cards, zero problems.

### 2026-10-26 — remaining idle cost

- The operator authorized continuing beyond the three-attempt limit. Python must remain running; the operator closes Scavland. Only previously authorized Spotify, Discord and VM processes are temporarily paused and resumed in `finally`.
- Corrected-clock, valid-load idle measurements still fail: ratio 0.313585 before runtime optimization; conservative zero-velocity ORCA shortcut gives 0.300290 and then 0.330358. These are retained failures, not accepted evidence.
- Phase diagnostics on the latter: idle total p50 240.6 us, movement 140.8 us, adapter 50.4 us; moving total 728.3 us, movement 535.1 us. Foreign CPU 0.096394. Phase medians do not sum to the total median.
- The current candidate also caches the resting footprint's derived floor by terrain version, exact X/Z, own floor and template radius. Ground validation, height easing, velocity, latch and avoidance remain active. Every publication or geometric key change recomputes the floor; the cache is neither serialized nor hashed. A regression covers terrain rising and falling under a resting footprint over successive ticks.
- Fast verification including the new terrain regression passes in 2m 57s. The first cached-footing idle measurement is invalid (foreign CPU 0.324857); its timings are not accepted. Read-only diagnostics identified substantial Chrome/Telegram activity, so those previously authorized heavy applications join the temporary pause list for the next controlled measurement. Python and Scavland remain excluded.
- Earlier statements describing a fixture-only final candidate are superseded by this entry. Final functional, JMH and complete perf gates remain pending; no hand-in PR exists.


### 2026-10-25 — complete functional gates

- `verify verifySlow verifySoak` passes in 15m 53s with one Gradle worker and authorized temporary application pauses. Ordinary verify is approximately 5m 48s of that serial run; no gate is moved.
- 1,301 core tests, 3 slow core tests, 200 seeded soaks, 73 journal replays, 95 Minecraft unit tests, 12 architecture checks, 211 normal GameTests and 13 slow GameTests: 1,908 total, all zero failures/errors/skips. Normal L3 wall time is 64.814s against 180s; slow L3 is 471.794s.
- The equal-probe protected-index test passes in isolation and in the complete 95-test Minecraft unit suite. Cached Minecraft sources confirm the constructor and coordinate getters used in its fixture.
- **Next:** finish the complete existing CI perf profile with one path worker, inspect every report's CPU verdict, then hand in.

### 2026-10-25 — fixture timing corrected; full gates running

- Final candidate changes performance fixtures only. The runtime ORCA experiment was removed; runtime sources match main byte for byte; test fixtures may change.
- The idle comparison now runs at real 20 TPS (200 warm-up ticks and 600 samples per mode unchanged). PathLoad includes the first 50 ms interval, timed from the preceding order tick's start. Three fake-clock StepPacer tests pass.
- Final fast check passes in 9 s after the fixture edit (the preceding clean core run passed in 1m 42s). Native-budget Windows diagnostics with one worker: idle p50 0.2113 ms / moving 0.7091 ms = 0.297983 (limit 0.30), foreign CPU 0.140771; D p99 1.746 ms (limit 2 ms), join p99 0.0442 ms, no unfinished joins, group results p95 3 ticks; group-path D p99 0.9157 ms, foreign CPU 0.150663. Both reported failures are now valid passes locally.
- That five-test diagnostic invocation still fails its load guard because the separate page-capture window records foreign CPU 0.257037. This is not a green aggregate. Preserve the invalid report; do not weaken the guard.
- **Next:** finish verify/verifySlow/verifySoak and the complete pre-existing CI perf profile (ciFactor=3, soak=2400, loop=3600), retaining the native-limit diagnostic evidence above. No machine baseline or threshold is changed.

### 2026-10-25 — resume accepted fix-main

- Verified main 1dae9fa and merged claims #13/#15 and spec #16. No successful perf run after 40176248614. M6 spec belongs to dyadik; M5 is out of scope.
- Historical Ubuntu 24.04 reports: one path worker, D p99 50.288387 ms, join p99 49.743357 ms, 14 unfinished joins in the last run, foreign CPU 0.003607. Idle p50 0.218300 ms / moving p50 0.670648 ms = 0.325506; foreign CPU 0.090803. Neither failure may be dismissed as excessive foreign load.
- Initial Windows verifyFast passed in 48 s. Roadmap: 245 cards, zero problems with PYTHONUTF8=1 (without it the platform encoding silently matches zero headings).
- Initial native-limit Windows perf: D p99 0.8044 ms with two workers, join p99 0.0318 ms, no unfinished joins; idle ratio 0.22783 but foreign CPU 0.44371 makes the run INVALID and Gradle correctly fails. No gate was weakened. Raw reproduction and JFR retained under build/.
- **Next:** diagnose idle hot work and reproduce the one-worker path workload. Keep all limits and Mac baselines unchanged.

### 2026-10-25 — request the main perf investigation

- Read the updated AGENTS.md, developer procedure, onboarding, security policy and claims registry. GitHub identity is @lev1pes, with collaborator write access; the repository is public.
- Updated main at f723161 passes local `verifyFast` in 5m 26s on Windows x64, Java 21, Gradle 9.8.2, ErrorProne 2.51.0 and Gson 2.14.0. Nine Python tool tests pass; roadmap check reads 245 cards with zero problems.
- Inspected the failed step of [perf run 40176248614](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40176248614), at the earlier main commit 3705118. `idle_300_costs_a_third` measured 0.3255060777 against 0.30; `path_budget_d` measured p99 50.288387 ms against the CI limit of 6 ms. These are historical CI results, not failures of the new local fast check.
- No implementation, perf rerun or threshold change has been made for this claim.
- Registration #12 was merged by a human during this session. Merged later main 2d58bdf locally without rewriting history; read its new GitHub-login and automatic branch-deletion rules. This claim's records CI passes.
- **Next:** reproduce with recorded load and environment, distinguish implementation cost from measurement conditions, then fix and run the applicable gates. A successful fast check does not close a failed perf gate.

## For the owner

1. Review and merge only in a separate human/maintainer session. An independent Ubuntu perf rerun uses the existing maintainer-controlled label; this developer does not apply it.
2. MacBook M5 validation remains pending. Windows headless evidence does not replace it, and no Mac baseline changes.
3. An earlier deliberately serial Windows run took approximately 5m 48s for ordinary verify; the final full verify/verifySlow/verifySoak aggregate passes in 17m 38s. Owner choice: accept this host/serialization cost or review a separate test-placement proposal; no check is moved or weakened here.

## Decisions taken

- 2026-10-27 - The operator subsequently permits pausing Spotify and Discord between runs if needed without restarting completed checks. The resumed full profile keeps Discord running; Spotify is absent after boot. Python and Scavland remain excluded. Undo by restoring the requested applications after any pause.


- 2026-10-27 - Operator update: Discord and Spotify stay running for measurements. Both were restored and removed from automatic pause targets. Ask the operator before any later shutdown if they materially interfere. Python and Scavland remain untouched.


- 2026-10-25 · Operator constraint: never pause or terminate Python processes; the operator closes Scavland personally. No Python or Scavland process was suspended by this session. Temporary pauses remain limited to the previously authorized applications/VM and are always undone.

- 2026-10-25 · The operator explicitly authorized continuing fix-main beyond the developer procedure's three-failed-attempt stop condition. This applies to this claim only; M5 remains stopped and delegation still requires permission.

- 2026-10-25 · Separate the existing red perf investigation from M5 feature work, as the updated procedure prioritises red main CI. Both claims have separate branches and evidence; release this claim by pull request if reassigned.
- 2026-10-25 · Preserve all current thresholds and Mac baselines. Windows measurements describe their actual host and do not replace Mac evidence.
- 2026-10-25 · No workers launched: the operator's requirement for explicit permission before delegation remains in effect. The resumed session investigates and repairs fix-main only.

- 2026-10-26 · Reuse only proven stable ground geometry and skip a solver only for a conservatively feasible zero velocity. This removes repeated idle work without changing separation, avoidance cadence or height easing. Undo by removing RestingGround and the feasible-zero shortcut; no serialized state migration is needed.

## Pitfalls

- The upstream Windows wrapper is stored as CRLF while its text attribute normalises it, causing a whitespace-only dirty checkout. A local, untracked Git attribute preserves its exact upstream bytes; no wrapper content or shared rule was changed. Remove that local override after the upstream blob is normalised.

- Windows Python bookkeeping needs `PYTHONUTF8=1`; otherwise roadmap parsing can silently match zero cards. Recorded in docs/kb/minecraft-mod-build.md.

## Review

Pending a maintainer's review. This session neither approves nor merges its own work.

### 2026-10-27 - operator-requested review of GitHub #21-#26

- Read all six descriptions and published issue comments. PR #21 has no published review or inline comments; its performance implementation remains pending human review.
- [#22](https://github.com/o-e-lytvynskyi/strategos/issues/22) proposes stricter environment validity and causal before/after evidence. Its CPU limits are unapproved calibration proposals, not current gates. Preserve the current thresholds and historical verdicts. Existing combined harness/runtime results do not establish that every runtime optimization was necessary; acceptance under the current foreign-load guard does not establish absence of contention. Any follow-up attribution needs a comparable unchanged-runtime control and a declared repetition protocol.
- [#23](https://github.com/o-e-lytvynskyi/strategos/issues/23) concerns terminal ENDED matches restoring as PAUSED; [#24](https://github.com/o-e-lytvynskyi/strategos/issues/24) concerns unsafe removal of live building templates during reload; [#25](https://github.com/o-e-lytvynskyi/strategos/issues/25) concerns saved buildings changing identity when session-local template indices shift. These are separate persistence/content defects, not changes to this performance claim.
- [PR #26](https://github.com/o-e-lytvynskyi/strategos/pull/26) requests the dedicated main-save-reload assignment for #23-#25 and is still open. No production or regression-test changes for that work before its human merge. The reported probes do not replace a real modified-pack reload or a two-process restart check; keep those validation limits explicit.

## Current implementation findings

- The path fixture ran its first measured tick immediately after admitting the scattered order. STRICT K=2 therefore received only one 50 ms interval before delivery. StepPacer restores both intervals without changing jobs, W, K or tick latency. Fake-clock regression tests cover the boundary, early wakeups and slow ticks.
- The idle fixture ran accelerated GameTest ticks. Its initial 200-tick warm-up and CPU samples every 20 ticks were much shorter than the intended real-server intervals, while movement was measured later in the warmed JVM. It now uses the existing global 20 TPS pacer; sample counts, workload and ratio limit are unchanged. The existing batch cleanup already resets pacing on failure or timeout; its implementation is unchanged.
- The first ORCA prototype was removed during fixture diagnosis. After controlled idle failures persisted, the current candidate reintroduces a conservative zero-velocity feasibility shortcut and derived resting-footprint caching; see the 2026-10-26 log.
- Loaded diagnostic runs remain invalid, regardless of their timing values. The operator authorized temporary pauses of heavy applications; Spotify, Discord and a newly started VM were resumed automatically after each measurement. No personal VM path is part of the repository evidence.
- The original full JMH executed successfully in 6m 27s, but external load changed during it. It is not evidence of a valid before/after comparison and is not promoted to any machine baseline.

### Additional verification finding: protected-index probes

- The first full verify stops at the unchanged ProtectedBlockIndexTest performance assertion: 125.9 ns / 33.1 ns = 3.8066, limit 3. An isolated quiet diagnostic still fails at 75.9 ns / 24.3 ns = 3.1287; load is not the only cause.
- Its 64K probe arrays represented different object working sets: the 4-section case reused references to only 16K BlockPos objects, while the 64-section case dereferenced 64K distinct objects. The tail now allocates distinct immutable positions too, isolating index size from probe-object locality. The index, 4/64 section counts, one million lookups, warm-up, seven medians and ratio limit 3 are unchanged.
- That earlier functional sequence had passed 211 normal L3 cases in 82.160 s. It is superseded by the final 1,918-test passing aggregate recorded above. No hand-in PR exists yet.

### Earlier full-profile failures (historical, superseded by final acceptance)

- The full existing CI profile with one path worker reports first-loop 1.5x C p95 52.2319 ms against 45.999999 ms, foreign CPU 0.137735. The core's step p95 is 1.6734 ms and adapter p95 0.1509 ms; JFR CPU samples primarily show vanilla chunk processing, not core simulation or path joins. Do not raise the limit or dismiss this as excessive foreign load.
- Idle also breaches 0.30 in this cumulative run: ratio 0.313768, foreign CPU 0.144046. D passes at p99 1.1672 ms. Fixture timing alone does not yet close this claim.
- A detailed JFR export and analysis created substantial observer overhead during this run. Keep that interval explicitly marked as diagnostically perturbed; do not present the run as clean validation. The binary JFR is retained for analysis outside measurement windows.
- The operator explicitly chose to continue beyond the three-failed-attempt stop condition. That investigation stayed in fix-main; no hand-in was opened while its full gate was red. The final passing profile is recorded above.
