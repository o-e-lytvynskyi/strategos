# M5 on dev/o-e-lytvynskyi/m5

**Developer:** o-e-lytvynskyi · **Branch:** dev/o-e-lytvynskyi/m5 · **Work:** M5 · **Base:** main @ cb4044b · **Claimed:** 2026-10-26 · **Status:** in progress

## Now

- **Milestone:** M5 Combat; 11 of 23 tasks complete (T1–T5b).
- **In progress:** M5 T6 mounted units (UNIT-06) since 2026-10-27.
- **Last finished task:** T5b FLEEING, stance buttons and GameTests (2026-10-27).
- **Next task:** T6, then T7 the roster (UNIT-07).
- **Merged from main last:** a10a8fd (2026-10-27), PR #21 fix-main.

## Depends on unfinished work

- Within M5: CMB-16 check 2 (archers on a 6-block hill win ≥ 170 of 200 seeded duels) runs on T7's duel harness; the card stays in_progress until then.
- M8 fog of war: `Audience.SeesEither` (shot FX) goes to every match member, as `SeesCell` does; `AFTER-M8` in NetGateway marks the union of the two cells' viewers.
- M0-M4 are merged. No unfinished milestone implementation is required by M5.
- fix-main (lev1pes) is done and merged into main by PR #21; it no longer bears on M5.
- Preserve codex/m5-combat and dev/lev1pes/m5. Import code and task-specific evidence selectively after assignment; do not copy old central STATE/worklog records over current main.

## Log

### 2026-10-27 — M5 T5b

- **T5b · FLEEING, the card and the GameTests** — CMB-05 done. FLEEING (state 21, think 5) holds its attacker in the FLEE order's target (saved and hashed), re-plans its point from the attacker's current place each think, ends at the flee distance from the attacker or on its death, turns on a new attacker; the card's five stance buttons (slots 5–9, active when the whole selection shares a stance) send one `SetStance`; `strategos.stance.*` labels. Tests: `FleeingTest` (6), `FleeTransitionRows` (23), four `CommandCardTest` cases, `combattests.stance_aggressive/defensive/stand_ground/passive`. No golden changed.
- Gates: `verify verifySlow verifySoak` 4m 37s green. JMH: `SpatialBench.rebuild1000` again read one fork at 7.39 us and one at 4.81 (6.10 averaged, +27 %), as on T3b.1's first run; the single rerun gave 4.69/4.73 and the whole gate passed. Second occurrence: For the owner 5.
- **Next:** T6 mounted units.

### 2026-10-27 — M5 T5 split, T5a

- Split T5 into T5a (core stances) and T5b (FLEEING, card, GameTests) before implementing: the estimate passed 1,500 lines. Spec finding 11 (a passive unit flees its template's `Behaviour/flee`, else 12; the worker keeps M4's 8) and 12 (where §2.4's table is silent).
- **T5a · Stances in the core** — CMB-05 in_progress (checks 1–3 pass at L1; 4 in T5b). `Stance` (5, wire codes 0–4), `Behaviour/stance` by name (worker passive), `StanceStore` (explicit stance and spot, cleared on spawn and removal, saved and hashed only for live rows), `SetStance` (25) with its rule, stance-aware notice, retaliation, leash and return, the out-of-vision end of a chase, `preferred` classes before distance. Tests: `StanceTest` (`theTable` per stance, `targetPreferences`, `aChaseEndsOutOfVision`, archer defensive/stand-ground, no chain kills past the leash, passive worker flees 8, handle wrap), `StancePersistenceTest`, `SetStanceRuleTest`, `SetStanceTest`, `StanceDataTest`. No golden changed; a stance-free match saves and hashes as before.
- Review (reviewer): a defensive archer's notice radius (35.5) passed its vision (32) and looped engage/finish every think; per-call `values()` clone; next-target search off the leash; dead rows reviving after 4,095 reuses. All fixed with tests.
- JMH: `TemplateCompileBench.compile500` read 6.48 ms (+17.8 %): the compiler split every value path's string once a template; splitting once a set brings it to 5.36 ms, under the baseline. Gate green, no baseline touched.
- Gates: `verify verifySlow verifySoak` 4m 14s green; `jmhCompare` green.
- **Next:** T5b.

### 2026-10-27 — M5 T4

- **T4 · CMB-16 High ground** — in_progress (checks 1, 3, 4 pass; 2 in T7). `Attack/Ranged/highGround/{rangePerBlock 0.05, damagePerBlock 0.04, maxBlocks 8}` core defaults with provenance and template validation; `HighGround` (height = the difference of the units' floor Ys, capped, 0 on flat ground) grows the range of `Ranged`'s approach, chase and the shot intent and its damage; the command card's breakdown shows the ranged weapon and three "High ground" lines. Tests: `HighGroundTest` (5: 0/3/6/10/−4 blocks, 1.3 × range and 1.24 × pierce at six, template override, engagement at 25 vs 27 blocks), `RangedValidationTest`, `CommandCardTest`, `combattests.high_ground_reaches_farther` (raised archers fire unmoved at 25.4 blocks; the ground twins walk).
- A GameTest failed 6 runs in 7 once T4 joined the batch: `persistencetests.the_reconciler_converges_a_crash_between_saves` ("rewrote 62"). Cause: the test saved once one wall block was protected, while the shared writer budget (64 a tick for the batch) still had the house queued. It now waits for `BlockWriter.pendingIn` of its match to empty; two full runs 215/215. The new test's first platform also stood above its 4-block arena (blocks GameTest never clears); it moved into `arena_flat_48_high`. Both in docs/kb.
- Gates: `verify verifySlow verifySoak` 4m 09s green; JMH not rerun (no benchmark has a ranged unit; ShotBench drives ShotSystem directly).
- **Next:** T5 stances.

### 2026-10-27 — M5 T3c.2

- **T3c.2 · Shot FX delivery and client** — CMB-04 in progress (criteria 1–4 pass; 5 waits for T14's budget E). `ShotFx` turns `ShotFired`/`ShotLanded` into path events (core Y made origin-relative like FLAME) grouped by the pages of both ends, 64 to a payload, sent once to the new `Audience.SeesEither`; client `ShotArcs` (cap 512, dropped at landing, on the matching impact, on match change) drawn as the vanilla arrow texture on crossed quads at AFTER_ENTITIES; bow cue at the launch, arrow-hit cue at the landing point. Tests: `ShotArcTest` (4, L1), `ShotArcsTest`, `ShotFxTest`, `PayloadRoundTripTest.aShotFxPayloadRoundTripsWithinItsCap` (L2), `nettests.shot_fx_audience` (member gets 3 launches and 1 impact, outsider none).
- Engine names checked in the 1.21.1 sources: `TippableArrowRenderer.NORMAL_ARROW_LOCATION`, `RenderType.entityCutoutNoCull`, ArrowRenderer's UVs, `LightTexture.FULL_BRIGHT`, `OverlayTexture.NO_OVERLAY`, `RenderLevelStageEvent.Stage.AFTER_ENTITIES`. The arc's look is unseen until T17's window batch.
- `verify` green (about 71 s); no core tick change, so no slow gates or JMH.
- **Next:** T4 high ground.

### 2026-10-27 — M5 T3c.1

- **T3c.1 · Shot FX state and wire** — CMB-04 in progress. `ShotStore.Release` (origin and tick) frozen at launch, persisted as an optional `release` key and hashed only when present (release-less shots keep their bytes and hash); `ShotFired` per launch and `ShotLanded` per impact, misses (`Handle.NONE`) and dead shooters included; `FxKind.SHOT_LAUNCH`/`SHOT_IMPACT` (codes 6, 7) with a path (other end, flight ≤ 16,383 ticks), old kinds' bytes unchanged, `Fx` max size 6 + 64 × 21; bow and arrow-hit cues. Tests: `ShotFeedbackTest` (4), `ShotStoreTest`, `ShotPersistenceTest`, `WireCodecSizeTest` (2), `FxMappingTest`.
- Gates: `verify verifySlow verifySoak` 4m 24s green. JMH found three things: (1) `jmhCompare` never reported missing benchmarks — a Groovy line starting with `+` was a separate statement — and died once `ShotBench.volley60` went missing; fixed through `JmhGate.all`, which `jmhSelftest` now exercises (kb entry). (2) The volley fixture counted all outputs and threw on the new `ShotLanded`; it now counts hits, the measured path unchanged. (3) `volley60{miss}` 0.827 → 1.085 us (+31 %, both forks 1.05/1.13): 60 `ShotLanded` records, +3,840 B/op, the required miss feedback; baseline row re-taken (Decisions). preferred +3.5 %, collateral +8 %. Watch: `TemplateCompileBench.compile500` +14.2 % (T3b.1's nine ranged default paths per template, +5.9 % B/op) and `MovementBench.step600` +13 % sit under the 15 % limit.
- **Next:** T3c.2 delivery to viewers of either endpoint, client arcs and sounds.

### 2026-10-27 — M5 T3b.2

- **T3b.2 · Archer content and engine integration** — CMB-04 in progress, UNIT-07 stays specified until T7's duel matrix. The archer template with the §2.7 numbers (builtin and datapack copy), every number's provenance row, regenerated balance sheet, `strategos.unit.archer` label; builtin indices made explicit for farmstead/storehouse before the archer (index 16). `ArcherTemplateTest` (values, unchanged index prefix); `combattests.archer_volley_replays_as_the_core_predicts` (L3, 1.15 s): five archers damage core units, living bodies' vanilla health stays full, the journal replays to the same hash.
- Merged origin/main a10a8fd (fix-main PR #21); two docs conflicts kept both sides.
- Gates: `verify verifySlow verifySoak` 4m 52s, 1,987 tests (core 1,662, mod 325), 0 failures/errors/skips. The first run failed only on TemplateSetTest pins (17 templates, new SHA) and formatting, both expected from the archer. JMH not rerun: no hot path or codec changed since T3b.1's green run.
- **Next:** T3c.1 shot FX state and wire.

### 2026-10-27 — M5 T3b.1 (session moved from Codex to Claude Code)

- **T3b.1 · Ranged behavior** — CMB-04 in progress. Typed `Attack/Ranged` stats with validated ranges, `Ranged` approach without a melee ring, wind-up/repeat on M2's timer, `ShotRequested` intents released in COMBAT after MOVEMENT with flat-world lead from transient displacement; `CombatActions` dispatches melee/ranged. Tests: `RangedStatsTest`, `RangedValidationTest`, `RangedOrdersTest`, `MovingShotTest` (constant-velocity positive control, direction changes).
- Gates: `verify verifySlow verifySoak` green in about 4.5 min, 1,985 tests, 0 failures/errors/skips; roadmap 245 cards, claims 5 rows. JMH: first run flagged `SpatialBench.rebuild1000` 6.28 us vs 4.79 (one fork 7.7 us, the other 4.87 us; spatial code untouched); the single confirming rerun passed the whole gate in 7m 03s with rebuild1000 at 4.88 us in both forks. Recorded as a one-fork outlier of the benchmark, not of T3b.1; a repeat would make it a gate-noise item for the owner.
- The uncommitted Codex worktree state was adopted as is after reading its diff; the T3b.2 draft from a second worktree is kept as a patch and applied next.
- **Next:** T3b.2 archer template, provenance, balance sheet and the L3 volley; merge origin/main (PR #21) first.

### 2026-10-27 — bounded presentation tasks and ranged review

- Split T3c into core FX state/wire and server/client presentation before implementation: the combined estimate exceeds 1,500 source/test lines. Same approved launch/impact, visibility, images and byte-budget criteria remain required.
- Ranged review found and fixed automatic sight loss, sub-block chase stalling, stale weapon wind-ups and disabled-weapon fallback. Tests pin the initial prepare deadline and weapon changes. Full functional and performance gates remain pending before completing T3b.1.

### 2026-10-26 — split ranged behavior and archer integration before expansion

- Split T3b into T3b.1 typed stats/AI/release timing and T3b.2 content/provenance/L3 integration before adding the controller. Their combined estimate exceeds the per-task source/test target. Every original gate and acceptance criterion remains required; CMB-04 stays in_progress.
- Preserve all existing builtin network indices: the current explicit pinned prefix is followed by implicit farmstead/storehouse indices, which must be made explicit before appending the archer. No old fixture is rewritten to hide renumbering.
- **Next:** finish typed ranged stats, implement range-based approach and post-MOVEMENT firing, then validate full gates/JMH before the archer integration task.

### 2026-10-26 — complete T3a integration

- **T3a** — owned shot store registered before CombatSystem; optional nested Play snapshot state, pristine omission, used-empty allocator persistence, dead-shooter restoration, full frozen-packet codec and allocation-free hash loops. Six pipeline/persistence tests pass, including real used-empty relaunch; independent review has no remaining findings.
- verifyFast 49s; final verify verifySlow verifySoak 4m 39s, 1,958 cases, no failures/errors/skips. Full JMH 7m 02s, 25 configurations present, no regression/missing/allocation failures. Hash 300 active shots 32.394 us,0.223 B/op profiler background within unchanged gate; volley60 among 300 fighting infantry 5.348 us preferred, 6.270 us collateral, 0.827 us miss.
- First profiles for four new shot cases; all 20 prior primary metrics, 15% limit and allocation criteria unchanged. Added the active-shot hash gate. Benchmark uses direct package access rather than reflection and trial assertions prevent empty/depleted workloads. Evidence saved without personal paths.
- **Next:** T3b core ranged behavior and archer data, then T3c presentation. CMB-04 stays in_progress until all criteria including T14 budget E pass.

### 2026-10-26 — resume the remaining M5 work

- The owner requested completion of all M5 work. Claim remains assigned, no standing restrictions and no main changes since b207e50; the existing gradlew.bat checkout difference remains excluded. Reoriented from 08da21c and refreshed the worktree session lock.
- Initial verifyFast passes in 654ms; roadmap checks 245 cards and the current claims registry is valid. Resume T3a integration before T3b/T3c and the remaining approved tasks.
- **Next:** test in-flight and used-empty saved continuations, connect optional nested Play shot state without changing released snapshot/journal semantics, then run full gates and JMH.

### 2026-10-26 — verified T3a model checkpoint; stop on usage limit

- **T3a model slice** — ShotAim, ShotStore and standalone ShotSystem, SHOT stream 2 and deterministic log helper; 13 new tests. Exact independently verified 126/500 hits and 0.4921875 damage, immutable snapshots, due compaction, exhaustion/failed-launch atomicity, full packet preservation, shooter death and recycled handles are covered. No in-game firing path is registered yet.
- Independent source review found no defects. Orchestrator then removed empty/in-flight-only due-list allocations; final verify verifySlow verifySoak passes in 4m26s: 1,952 functional cases, 0 failures/errors/skips. Roadmap 245 cards and claims check pass. The current slice changes neither the running core step nor snapshot codecs, so integration/JMH remains in the unfinished T3a step, not claimed complete here.
- Implementation worker returned usage limit on the follow-up. Finish and push the green model step, then stop under strategos-dev §8. No source edits remain uncommitted except the pre-existing gradlew.bat normalization difference.
- Added analytic-combat KB notes, including post-MOVEMENT release construction for T3b's constant-velocity control. No new owner permission is required to resume the approved work when execution capacity is available.
- **Next:** T3a state/scheduler/optional-Play persistence and allocation-free hash integration, legacy save/replay canaries, in-flight and used-empty continuation tests, then required full gates/JMH. T3b orders/data and T3c FX remain untouched. Historical main CI stays a separate unresolved claim.

### 2026-10-26 — restore bounded T3 planning and start T3a

- Restored the legacy e8cf0c2 split into shot lifecycle, ranged orders/data and presentation before implementation. CMB-04 enters in_progress; no criterion or approved scope changes. T2a–T2b is pushed as 40491df; all its gates are green.
- Normalized trailing whitespace in the two baseline artifacts without changing parsed JSON; the new proposal inherited an extra EOF blank line from the old baseline. No source or numeric result changes.
- **Next:** inspect existing combat, RNG, snapshot and hash contracts; write T3a behavior tests first and implement the smallest lifecycle slice.

### 2026-10-26 — accept recovered T2a–T2b after full validation

- **M5 T2a–T2b · Mobility and performance** — six integration regressions, nine further cache/publication cases, verifyFast 49s, final verify verifySlow verifySoak 4m24s (1,939 tests), plus two targeted perf tests in 18s; no failures/errors/skips. Navigation on populated 512x512 with bridge is 10,114,672 bytes / 32 MiB.
- Full JMH and comparison pass in 5m56s: 21 measured configurations, no regression/missing/allocation failures. Movement 24.904 us/op; terrain 515.312 us/op against conservative documented two-grid profile 481.023; both-class page building 112.663 us/op gets its first tracked profile. Metadata-only comparison then passes (21 measured / 20 tracked, 445ms). NodeDiscovery remains informational as before.
- Absolute path p99 0.427499 ms / 2 ms; idle ratio 0.218951 / 0.30; both have valid low foreign load. Independent final source review has no findings. Preserve all historical branches and original control numbers in the new sanitized evidence report; no old central records or Windows baselines are imported.
- **Next:** restore the planned T3 split and continue T3a. The local red-budget stop is resolved; the separately assigned historical main investigation remains open and is not claimed as repaired by these Mac results.

### 2026-10-26 — isolate recovery performance cost

- Same-host T1 control (152ca9d), unchanged JMH settings and Temurin 21.0.12.1: MovementBench.step600 23.249 ± 0.462 us/op; TerrainEditBench.rebuildOnePage 290.034 ± 6.111 us/op. The focused diagnostic ran once in 36s; it is not a replacement for the full comparison gate.
- The original terrain benchmark edits page (0,0), but the recovery rebuilt four large pages on every operation. MovementBench uses the flat-world fallback, yet WORLD eviction reclassified all units every tick even with no footprints. Target those avoidable costs, retaining full clearance semantics and the existing workloads.
- **Next:** validate mobility reload/sparse-index caching, footprint presence and incremental-versus-full terrain publication, then repeat full gates and unchanged-baseline JMH.

### 2026-10-26 — split recovery after measured regressions

- T2 integration regressions first reproduced the defects, then all six cases passed; verifyFast 55s and verify verifySlow verifySoak 4m27s pass (1,930 deduplicated tests; no failures/errors/skips). Independent final review has no findings.
- JMH on the current M4 Pro / macOS 27.0.1 / Temurin 21.0.12.1 failed in 5m59s: MovementBench.step600 22.003 to 27.479 us/op (+24.9%); TerrainEditBench.rebuildOnePage 291.665 to 1027.918 us/op (+252.4%). No missing benchmarks or allocation findings. Baselines and thresholds are unchanged.
- Split T2a–T2b before expanding the recovery beyond its size target. Preserve all original gates and accept the two code steps atomically only after performance is green. The planning commit contains no product code; current recovery source remains uncommitted.
- **Next:** measure the T1 control, then remove repeated mobility classification, empty-footprint scans, unnecessary halo builds and duplicate decoding. No new features before resolving the measured regressions.

### 2026-10-26 — recover and verify T1

- **M5 T1 · Health check** — selectively imported nine reviewed source/test files from a13850b. Reproduced the scene-verdict failure and two roadmap failures before fixing them. Initial verifyFast 1m20s; recovered verifyFast 47s; verify verifySlow verifySoak 4m39s, 1,908 deduplicated tests, no failures/errors/skips. All 12 Python tool tests pass. No central records, baselines, thresholds or images were imported or changed.
- Added the UTF-8/empty-discovery pitfall to minecraft-mod-build.md. Confirmed extracted HUD calls in the exact NeoForge 21.1.264 Minecraft sources; no new engine API is introduced.
- Independent T2 review found adjacent-footprint eviction, mixed-class slot trading and repeated changed-page traversal gaps; reproduce and fix these during recovery before accepting T2.
- **Next:** recover T2 with regression tests and review fixes; validate full movement gates and JMH. The separate main perf claim is unresolved; no new feature work starts yet.

### 2026-10-26 — resume assigned M5 recovery

- The owner merged assignment #19 and authorized autonomous development and recommended decisions. Merged main b207e50 without rewriting history. Main merges remain human actions.
- Started T1 recovery with a current-main verifyFast runner and an independent read-only review of legacy T1. Product source on main matches the parent of legacy T1; selectively reuse reviewed changes rather than replaying old central records.
- **Next:** import and verify T1, then review and recover T2; preserve the unresolved separate perf dependency before new feature work.

### 2026-10-26 — request the successor M5 assignment

- The owner merged release PR #18 as cb4044b and requested adoption to continue. Fast-forwarded this branch to the merged main without rewriting history.
- Added the M5 assignment request for o-e-lytvynskyi. The released lev1pes row and both historical branches are preserved. No code, build surface, thresholds or measurement baselines are changed.
- Assignment assessment: M0-M4 are merged, the legacy T1-T2 are recoverable within M5, and M6 is outside this claim. The separate fix-main investigation still belongs to lev1pes; reconciliation can precede new feature work, which remains blocked by its unresolved perf failures.
- **Next:** obtain a human merge of this assignment, then review and selectively import T1-T2 with current records and fresh verification. No product implementation started while assignment is pending.

### 2026-10-26 — inspect M5 and prepare owner-directed takeover

- The owner explicitly authorized transfer in this session. This is an owner-directed exception to the normal holder-approved release procedure; no approval by lev1pes is asserted.
- Read the developer procedure, security rules, people/claims, standing restrictions, delegation rules, M5 task design and legacy progress. Confirmed the authenticated identity is o-e-lytvynskyi.
- Assignment #15 and fix-main assignment #13 are merged; both assign work rather than deliver implementation. The latest M5 branch commit 6bf30df records a pause after T2 and possible future migration; no T3 implementation exists.
- T1 splits oversized classes and makes failed scene checks fail scenario comparison. T2 implements LARGE clearance and class-specific navigation. Historical full verification was on Windows with an older toolchain; it must not be presented as current Mac validation.
- The original working copy has an existing gradlew.bat line-ending difference. A separate worktree preserves it; the fresh worktree also shows the same checkout normalization difference, excluded from all commits.
- **Next:** merge release, obtain assignment, inspect/import T1-T2 with current records and sanitized measurement paths, run required gates, then implement T3a-T3c. The red perf dependency remains open.

## For the owner

1. Release #18 and successor assignment #19 are merged. No assignment approval remains outstanding for M5.
2. fix-main is done (lev1pes, PR #21, merged here at a10a8fd); release proposal #20 is moot.
3. Commit 287bf54 on this branch contains the home-directory JVM path in one re-taken perf/baseline-m4pro.json row; cc04642 removes it. The history is pushed and is not rewritten (AGENTS.md); the repository is private. Nothing needed unless the history should be scrubbed before any visibility change.
4. Obsolete: `dev/o-e-lytvynskyi/fix-main` (one docs commit 526dc72 proposing a release of lev1pes's fix-main, made moot by PR #21) and the local worktrees `strategos-fix-main`, `strategos-m5-content` (its T3b.2 draft is now in 95484c3) and `strategos-m5-control`. Recommend deleting them; left in place until you say so.
5. `SpatialBench.rebuild1000` is bimodal across JMH forks on this Mac: twice today one fork read 7.4–7.7 us while the other read 4.8 (baseline 4.79), failing the 15 % gate on the average; each single rerun passed with both forks at 4.7–4.9. No code touched the spatial index. Options: (a) leave it — a red run is rerun once by the rule (current); (b) give that benchmark more forks so one outlier fork cannot carry the average (a measurement change, your call); (c) investigate the bimodality (memory layout of the grid arrays). Recommend (b) with 5 forks for this benchmark only.
6. Proposal (scope): replicate each unit's stance to its owner so the card's active stance is always right (now it shows the stance the player's own card last sent, else the template's). A small protocol addition; not in the approved cards' wording, so it waits for you.

## Decisions taken

- 2026-10-27 · A flight's point and its end are measured from the attacker (§2.4: "back to the order when it is 12 blocks off"), not from where the unit stood; StanceTest's passive case and WorkerModelTest now assert that. Undo by planning from the unit.
- 2026-10-27 · The card's active stance is what the client knows (the stance its card last sent, else the template's); stances are not replicated. Proposal in For the owner 6. Undo n/a.
- 2026-10-27 · trial implementer: pass — T5b in one round, reported the gate's one red row honestly instead of retrying.
- 2026-10-27 · Spec finding 12 (stance details the table leaves open) and the template SHA re-pinned for the worker's passive stance (8bb9790c…); RangedOrdersTest's receding dummy moved inside sight (22 → 11 blocks) because the out-of-vision rule now ends that chase as specified. Undo with finding 12.
- 2026-10-27 · trial reviewer: pass — found a real livelock and three determinism/cost defects in T5a, each with a scenario; read-only, no Gradle while JMH ran.
- 2026-10-27 · trial implementer: pass — T5a plus its review round; it found the compiler's repeated path split behind the JMH regression instead of proposing a baseline.
- 2026-10-27 · High ground uses the units' motion Y (the floor on terrain) and applies only to ranged attacks, as §2.3 states; the card's breakdown is the template tooltip (per-template knobs), the situational bonus is shown there as rates. Undo with the card's data knobs.
- 2026-10-27 · Fix the reconciler GameTest's race (wait for the match's queued writes) rather than retry or mark it flaky. Undo is not recommended.
- 2026-10-27 · trial implementer: pass — T4 met the brief, found the floor-Y convention and reported a GameTest failure it could not pin; the orchestrator traced it to a pre-existing race.
- 2026-10-27 · Shot arcs render at AFTER_ENTITIES (the entity cutout render type fits there) and in any camera mode; size and orientation are checked in T17's images. Undo by moving the stage.
- 2026-10-27 · trial implementer: pass — T3c.2 verified every engine name in the sources with file:line, reported the coordinate convention it found, and stayed inside its files.
- 2026-10-27 · Re-take only the `ShotBench.volley60{mode=miss}` row of perf/baseline-m4pro.json (0.827 → 1.085 us/op): the growth is the 60 `ShotLanded` records the spec requires for misses, confirmed in two runs; limits unchanged. Undo by restoring the row from 95484c3.
- 2026-10-27 · Fix `jmhCompare` to report missing benchmarks (it never did); a stricter gate, not a weaker one. Undo is not recommended.
- 2026-10-27 · trial implementer: pass — T3c.1 matched the brief with no deviations, kept old constructors and bytes, named its one test-assertion change; the orchestrator read the core diff.
- 2026-10-27 · Spec traceability for CMB-04 check 4 names the existing GameTest `combattests.archer_volley_replays_as_the_core_predicts` (M2's "as the core predicts" convention: the headless replay is the L1 prediction) instead of the planned `a_volley_damages_as_l1_predicts`. Undo by renaming the test and the row.
- 2026-10-27 · Re-pin the builtin template set's SHA (e4624330…) and size 17 for the archer; the canonical form changed only by the appended template. Undo with the archer.
- 2026-10-27 · trial runner: pass — three bounded gate/JMH runs with exact failing tests, pinned values and per-fork evidence; no retries or edits.
- 2026-10-27 · Persist optional shot release origin/tick for future impact feedback across death/restore; preserve old snapshot and FX bytes when the extension is absent. Deliver once to viewers of either endpoint and clear client arcs on match changes; no past-launch resync is added. Undo through a reviewed feedback contract change.

- 2026-10-27 · Derive flat-world target velocity from transient pre-/post-MOVEMENT positions at release. Preserve the existing pose-only flat snapshot/hash contract; terrain still uses MotionStore velocity. Undo by a reviewed motion-state version change. Bound ranged damage to finite float-safe values so accepted templates and modifiers cannot fail when converted to a damage packet.

- 2026-10-26 · Establish first Mac profiles for active-shot hashing and preferred/collateral/miss volley60 cases from the full measured run. All existing profile scores and limits remain unchanged; remove only these four rows to undo. Keep T2's proposed baseline as its historical decision artifact.
- 2026-10-26 · Use available standard worker gpt-6-sol for the unchanged tier-B contract after the earlier model's usage limit; runner and independent reviewer remained available. Trial implementer/reviewer/runner pass: scoped integration, actual continuation tests, representative combat fixture, bounded summaries and unchanged gates verified by the orchestrator.

- 2026-10-26 · Accept the shot golden only after an independent SplitMix64/Box-Muller calculation matched 126/500 hits, first 32 outcomes and exact damage 126/256. The nearest circle-boundary margin is 0.000171829 in squared distance, well above floating-point rounding differences. Undo by replacing the test golden through a reviewed behavior change; statistical bounds and seed stay fixed.
- 2026-10-26 · Use circular body-radius footprints with a body-height check and frozen aim Y to avoid cross-layer hits; preferred full target handle precedes ascending any-faction collateral. This refines the approved landing-point model. Undo by revising the collision policy and its explicit circle/bridge/friendly-collateral tests.

- 2026-10-26 · Continue ready M5 tasks once this branch's full functional and performance gates are green, including both historical failure counterexamples. Keep the separate Linux main investigation assigned and unresolved; its release proposal is optional coordination, not a reason to idle validated M5 work. Undo by pausing features until that separate claim is delivered.
- 2026-10-26 · trial implementer: pass — integration and performance briefs stayed within named source/test files, preserved old query/reload behavior, demonstrated meaningful regression cases and differential terrain checks, and returned focused/full-fast evidence without commits or records edits.
- 2026-10-26 · trial reviewer: pass — found and then verified fixes for eviction, mixed slots and quadratic page access; final cache/signature review has no findings, with the orchestrator reading source/test diffs.
- 2026-10-26 · trial runner: pass — final full gates, isolated controls/diagnostics, targeted perf and full JMH ran serially with bounded summaries; no unrequested retries or baseline edits. Corrected corpus counts distinguish 1,939 functional tests from two extra perf tests.

- 2026-10-26 · Establish TerrainEditBench's two-grid Mac profile at 481.023 us/op after reducing the initial recovery cost from 1027.918, confirming a 290.034 us/op same-host T1 control and passing absolute D (0.427499 ms p99 / 2 ms) and idle ratio (0.218951 / 0.30). This is the new class's measured cost within the planned roughly twofold rebuild; owner delegated recommended baseline decisions under AGENTS.md. Keep the 15% limit and every other existing score/budget unchanged. ADR-0019 and a retained proposal record it; undo by restoring only this benchmark's prior row from 5437a1f. Final full comparison is still required.

- 2026-10-26 · Split mobility recovery and measured performance work; keep acceptance and code delivery atomic so no red intermediate implementation is committed. Undo by collapsing the two progress entries; the gates and feature scope remain unchanged.

- 2026-10-26 · Recover reviewed T1 source and tests selectively; keep historical Windows artifacts in the preserved branch and require fresh current-host evidence. Undo by reverting this recovery commit; no pushed history is rewritten.
- 2026-10-26 · trial runner: pass — baseline, deliberate red tests and final gates ran sequentially with compact verdicts, timings and no unrequested retries; the negative checks failed before the fixes and passed afterwards.
- 2026-10-26 · trial reviewer: pass — reviewed immutable T1 source without edits/builds, verified semantic preservation and scene guard behavior; T2 findings are being validated separately.

- 2026-10-26 · Follow the owner's explicit takeover authorization through a release PR followed by an assignment PR; retain all historical branches and evidence. Undo by rejecting the release or assigning M5 back by PR.
- 2026-10-26 · Prefer selective reuse of legacy T1-T2 over reimplementation, subject to review and current verification. Keep Windows evidence labeled and sanitize personal paths without changing numbers; discard the import if validation reveals an unsuitable design.

## Pitfalls

- Added docs/kb/rts-sim-combat.md: used-empty allocator history, release/movement timing and body/layer impact checks.

- Added docs/kb/rts-sim-large.md: expanded-clearance eviction, class-safe stuck recovery, ordered page notifications, template-cache reloads and complete effective-border signatures.

- Added Python UTF-8 document parsing and empty-discovery validation to docs/kb/minecraft-mod-build.md.

- Existing Windows wrapper line-ending normalization is recorded on the previous fix-main branch; no wrapper or shared settings change is included here.

## Review

Pending independent review. No product code changed and no legacy implementation was built in this preparation.
