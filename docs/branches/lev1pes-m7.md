# M7 on dev/lev1pes/m7

**Developer:** lev1pes · **Branch:** dev/lev1pes/m7 · **Work:** M7 · **Base:** main @ 267da40 · **Claimed:** 2026-10-29 · **Status:** in progress

**Note:** whole M7 requested by the operator on 2026-10-29; draft [PR #34](https://github.com/o-e-lytvynskyi/strategos/pull/34); explicit pre-merge development authorization persists on resume. Assignment is represented by this branch file after main removed the shared registry.

## Now

- **Milestone:** M7 Multiplayer; 6 of 28 implementation steps complete. Specification: [M7](../milestones/M7-multiplayer.md), based on planning PR #32; T3 is split before implementation into independent aggregate and production integration.
- **In progress:** M7 T4 lobby presentation since 2026-11-18; T3b is accepted on e382f7b6. Whole M7, including optional T24 requested by the operator, remains in progress. All further checks run in the cloud.
- **Last finished task:** T3b authenticated service/protocol/production start: cloud verify 44148921127 (2,669 canonical cases), complete full-ci 44148929120 (248 slow/core cases, all twenty AI seeds and aggregate), zero failures/errors/skips in the acceptance reports.
- **Next task:** T4 immutable lobby view-model, screen/readiness/errors and cloud L4 scenario; then T5 persistence/migration and the later integration tasks in specification order. D-20 remains accepted; T8's co-op routing is not inferred from four-faction start acceptance.
- **Merged from main last:** d39069e7 (2026-11-15, PR #55/M6); merge 213d0564 is zero commits behind that main. Cloud verify 43756194393 passed: 2,623 canonical XML cases, zero failures/errors/skips, 7m 22s. Prior T2b slow/soak/L6 evidence remains historical; new integration will receive its required gates.

The operator requested ownership of the whole M7 and work on what is currently possible on
2026-10-29. This assignment intentionally includes responsibility for later blocked tasks;
it does not declare them ready, waive their dependencies or accept any product decision.
The operator subsequently explicitly instructed development without waiting for approval/merge. That instruction overrides the assignment wait for this session; independent review and human merge are still required.
The planning specification from our own PR #32 is incorporated and reconciled in this branch; its registry row and completion status are not imported. Its checks are not runtime implementation evidence.

## Depends on unfinished work

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M5 | Combat authority, final damage, projectile output and close findings | Independent: combat delivered by PR #33 at the base; inspect current implementation during T1 | None required for merged M5 |
| M6 MATCH-02 / MATCH-03 | Production start pipeline and terminal outcome/snapshot integration | Delivered by PR #55; snapshot composition verified in 213d0564; production start, coalition victory and restore still need M7 integration | T3b/T7/T5 remain; the M6 implementation is no longer missing |
| M6 AI-05 / AI-18 / UI-20 / solo fog | Observed AI inputs, difficulty, minimap and authoritative visibility | Delivered by PR #55; integrate the actual adapters at T13/T14/T15/T9 | Existing source markers identify unimplemented M7 adapters, not missing M6 code |
| M6 QA-13 / PERF-09 | MVP fun verdict and accepted performance reference | PERF-09 reference delivered by PR #55; QA-13 owner playtest remains open in the M6 hand-in | ART-03 and final PERF-10 retain their own prerequisites |
| M8 foundations | Shared-vision and recipient policy contracts | Independent M7 identity/grant rules; reconcile contracts without importing or changing the separate M8 branch | AFTER-M8: shared-vision reconciliation when introduced |
| Save/reload PR #30 | Stable template references and terminal-state restoration fixes | Delivered on main and validated by T2b; the M6 snapshot composition is now reconciled | T5 still needs multiplayer roster, pending launch and restore integration |

Initial order: T1 readiness, T2a pure core foundations, T2b authority/persistence installation, then reassess T3/T5/T6 and later tasks
against merged prerequisites. Split oversized or partially ready tasks in the specification
before implementing them. T2 does not certify friendly-fire, allied victory, command adapters
or replication privacy; those retain their own integration tests and dependencies.

## Log

### 2026-11-18 — T4.2 actual lobby screen and cloud visual checkpoint

- Fresh main remains d39069e7, with no commits missing from this branch. T4.1 checkpoint 49aad4b9 passed cloud verify 44198552285: 2,682 canonical cases, zero failures/errors/skips, including idempotent pending-admission cancellation.
- Added the connected pause-menu entry and screen over canonical private session state, server-confirmed edits/readiness, bounded refusals, frozen preparation, leave/cancel and logout cleanup. The host edits vacant seat kinds and faction team/colour, available civilisation and shared skirmish settings; members view the same configuration without host controls. No names or UUIDs confer authority.
- Added the L4 scenario before the screen: seven detached presentation fixtures followed by real menu/button/transport flow through create, AI seat edit, ready, start, admission and automatic closure. Fixture screenshots are distinguished from real server acceptance. Compact layout keeps all four seats visible; an opaque background avoids moving-world golden noise.
- Initial checkpoint 652bed22 failed cloud verify 44200621297 before tests: ErrorProne rejected reference equality for the record view. Use value equality when deciding whether widgets need rebuilding; retain the strict warning gate. Cancel the obsolete first visual run, which has the same uncompilable source.
- Visual checkpoint b5fad06a / scenario 44201293302 captured all seven PNGs and completed all five real clicks, but its final assertion incorrectly required no screen. Existing `RtsMode` automatically opens/restores `RtsScreen` for the admitted commander. Correct the final assertion to require STARTED, the expected player/faction admission, the RTS screen and its camera rig; keep the same bounded timeout. Failure diagnostics now identify the final lobby phase, screen type and whether admission arrived.
- Personally inspected every cloud PNG: entry, host, ready, member, preparing, preparation failure and refusal; all text/rows/settings fit with clear disabled states and recipient highlighting. Promote these unchanged Linux/Mesa frames as the first baseline; the next run must pass strict comparison and the altered-golden negative guard. Add actual enabled-state and on-screen-control assertions to the presentation fixture run. These fixtures do not certify runtime start or the cloud runner's import speed.
- **Pending:** corrected cloud compilation/full verify and the first cloud L4 run, then personal inspection of every new PNG and strict approved-golden rerun. T4 and NET-02 remain unchecked. All checks/tests run in the cloud; the local formatter edits source only.

### 2026-11-18 — T4.1 private join/refusal and client session state

- Split T4 before its UI/transport integration grows: T4.1 owns detached presentation/session contracts and the small private join/refusal adapters; T4.2 owns the actual screen, entry point and inspected L4 goldens. No NET-02 scope or parent acceptance is removed.
- A roster is private until admission, so `JoinAvailable` asks the server to pick the first vacant HUMAN seat. Explicit slot joins remain supported. The test-first contract skips AI/closed seats, rejects full lobbies without publishing their roster, and resends only an authenticated existing member's view without resetting readiness or configuration. Actual registered-ingress coverage now uses this intent.
- Added a two-byte `LobbyReply`/payload for REFUSED, INVALID, ERROR and RATE_LIMITED. It goes only to the requesting player and contains no membership or exception details. Stream/audience/version/length contracts and the actual outsider refusal are covered; machine-readable inventory and architecture catalogue include the new type. Existing chat feedback remains while screen wiring is unfinished.
- The pure `LobbyClientSession` accepts views only after an explicit request, keeps server acknowledgements canonical, renders bounded errors, and ignores late views/replies after leaving or disconnecting until a new request. Three test-first session contracts cover these transitions. No Minecraft client hook is installed by this foundation alone.
- Added bounded idempotent `Cancel` for a screen closing before its join/open acknowledgement arrives: it removes only the authenticated caller's editing/preparing membership, ignores unknown callers, and cannot leave a started match. The test-first contract covers no lobby, an outsider, duplicate cancellation and host closure; ordinary explicit `Leave` keeps its existing strict membership checks.
- **Pending:** complete cloud verify, then T4.2 screen/connection handling and cloud scenario goldens. All tests/checks remain cloud-only; local formatting only edits source files. T4 remains unchecked.


### 2026-11-18 — T4 detached presentation contract

- Added five test-first view-model contracts for explicit host/operator permission, all-human readiness including empty seats, AI/closed seats, own-ready intents tied to configuration revision, every frozen phase, immutable ordered rows and safe status translations.
- `LobbyScreenModel` reads only the immutable private recipient DTO. It does not optimistically change acknowledgements or infer authority from a display name. Start/ready affordances are presentation only; server validation remains authoritative.
- **Pending:** cloud verification of this foundation, actual screen/session wiring, visible refusals and inspected cloud-rendered lobby goldens. T4 and NET-02 remain in progress. Formatting is an edit operation; no local tests or verification builds are run under cloud-only testing.


### 2026-11-18 — Complete T3b cloud acceptance; start T4

- Fresh main remains d39069e7 and is already merged. The pending push completed: e382f7b6 is present on origin. Verify 44148921127 passed 2,669 canonical cases with zero failures/errors/skips, including sixteen service contracts and the new initial-empty snapshot/header checks.
- Full-ci 44148929120 passed its evidence-checker job and 248 canonical slow/core-soak cases. Actual preparation passed in 5.287 s, cancellation in 1.202 s, and four-player initial-state acceptance in 4.710 s for the complete case under the unchanged ten-second start limit.
- Every original AI seed 1–20 passed its full match/replay assertion, and `complete-soak` passed. Independently inspected aggregate artifact 14362975462: exact commit e382f7b6008a34bfc1cfff804cf14c8e5401709b, twenty matches, 896,421 ticks, seventeen conquests and three complete game-limit runs, with per-shard XML/log hashes. No partial matrix is accepted. The same-run Maven-module sharing route succeeded without bypassing source compilation or tests.
- T3b is complete; specification progress is checked. NET-02 remains in progress until T4's screen and inspected goldens pass. T4 starts against the delivered private state/intents; reconnect, durable roster and co-op control keep their own gates. No local tests are started under the operator's renewed cloud-only instruction.


### 2026-11-17 — Resume with local tests and isolate incomplete initial snapshots

- The operator authorized local testing, use of computer resources and closing applications when needed. No application needed closing for these headless checks. Main was fetched unchanged at d39069e7; roadmap validation passed.
- The unchanged four-player case failed locally at 11,961 ms. Metadata-only inspection removed redundant full tile decoding from preflight/arena bounds and the faithful test placer. Its new real-server contract passed: world manifests take precedence without tile reads, full loading still refuses missing exported tiles, malformed manifests do not fall back, and cleanup restores resource lookup. The four-player case still reached 10,008 ms, so metadata work alone did not resolve acceptance.
- A smaller actual-network regression isolated the missing initial table: a newly seated player received zero building snapshots for an empty world. Buildings and deposits used the same first-empty suppression. The fix sends their first projected table even when empty, remembers it, and continues suppressing unchanged frames. The regression covers one frame per type, no repeat and no outsider delivery. The four-player assertions retain the ten-second deadline and now also require initial deposits; timeout diagnostics expose lifecycle and per-recipient frame counts without weakening the checks.
- Targeted local checks passed before the operator restored cloud-only testing: the initial-empty building/deposit regression passed, and the four-player deadline case passed (9.817 s for the entire case, including fixture setup). The ten-second start bound is unchanged. The metadata-only/export-precedence contract also passed.
- The broader local `verify` produced 265 passing ordinary GameTests but no terminal build verdict; its processes are no longer running. It is incomplete evidence, not a successful gate. The operator subsequently required all further checks in the cloud.
- **Pending:** complete cloud verify and slow/20-seed evidence on this exact source. No further local tests start. T3b and all downstream task/card completion states remain unchanged.


### 2026-11-17 — Pause on the four-player deadline regression

- The operator requested stopping until tomorrow. No new implementation or test run starts after that request.
- Verify 43990044644 on 8e4e86a2 succeeded. Full-ci 43990049824 failed its functional slow job: the four-player initial-state case reached 10,002 ms before all frames were available. The ten-second limit remains unchanged. The two-player preparation case passed in 9.633 s and shutdown cancellation in 2.124 s; passing subsets do not establish the full gate. The dependent AI matrix was correctly skipped, so no AI acceptance is claimed.
- Source audit found avoidable full tile/NBT loads when only manifest metadata is used: `LobbyBackend.validate` and `LobbyPreparation.start` each inspect the map through a full package load; the arena allocator and test placer also load full tiles for bounds/starts before creation loads the actual package. No optimization has been applied yet. Resume test-first with manifest-only inspection that preserves validation, exported-map precedence and full-load rejection of missing/malformed tiles; use it for metadata-only callers and retain actual import/hash verification. Then rerun the unchanged deadline and complete all required gates.
- T3b stays unchecked. Optional T24 remains in the requested completion scope. No auto-join or T4/T7 implementation was begun during the wait.


### 2026-11-17 — Include optional work in the session's completion scope

- The operator explicitly requested completing optional work too, including T24. This session retains responsibility for all 23 remaining checklist items, rather than stopping when the mandatory M7 subset is handed in.
- T24's independent status and its own decision/spike acceptance remain intact; this instruction does not pre-approve its technical conclusion or waive the owner's art, gameplay, platform-decision or merge roles.


### 2026-11-17 — Verify protocol integration and exercise four-player start

- Verify 43963085367 on 0a80ddb4 passed all 2,668 canonical cases, zero failures/errors/skips, including 16 service contracts, intent/view/payload checks and actual registered ingress with the production solo guard. Fresh `origin/main` is unchanged at d39069e7, already merged.
- Added a four-player actual-import case with four distinct player/faction identities, commander admission, per-recipient lobby/match_join/initial-summary assertions and an outsider negative control. Its ten-second wall-clock bound begins before start validation/allocation and ends only after every initial frame arrives. It does not use four co-op identities to bypass the unfinished T8 routing work.
- The devtools-only scenario uses four independent workers. The engine fixture copies the shipped M6 terrain unchanged into the isolated GameTest world's map exports, preserving the hash/jobs/palette and adding four non-overlapping starts inside the original clearings. No production map, default setup, balance value or art asset changes. The test placer now preserves the requested faction count while retaining unsaved GameTest ownership. The new solo slow batch has its explicit standard cleanup hook.
- **Pending:** verify and complete slow/soak on the source containing this case; the previous matrix's HTTP 429 failures remain failed historical evidence. No parent task/card is marked complete until its required gates pass.


### 2026-11-16 — Correct the protocol inventory and stale-ready assertion

- Verify 43961428636 on f893a0f5 compiled and reported 2,665 canonical cases with six unit failures. Five were the exact engine-surface inventory guard/counterexamples: both registered payload ids were present in the architecture but missing from `engine-surface.json`. Added their actual ids to the machine-readable inventory; no comparison or negative case changed.
- The remaining failure was the new intent fixture expecting `IllegalArgumentException` for an existing member's stale acknowledgement. The established aggregate contract throws `IllegalStateException` for changed settings. Corrected this exact assertion, retaining refusal and the empty ready-set check; no production rejection behavior changed.
- The actual registered two-member network/privacy case passed in 0.732 s, but its passing subset does not establish the whole gate. Subsequent verification includes the legacy solo guard and must pass in full before the slow/AI batch starts.


### 2026-11-16 — Register private lobby ingress and lifecycle ownership

- Verify 43958753086 on 50de3e72 passed 2,658 canonical cases, zero failures/errors/skips, including the real backend preflight and seven new bounded-wire tests. It does not certify subsequent intent/view or lifecycle changes.
- Test-first 0a573e0c covers shutdown cancellation, completed-match preservation and fail-closed cleanup propagation; 7d400d17 covers actual stream codecs, consumed malformed bytes and exclusive PLAYER state audience. Checkpoint 977c8eed adds registered real-connection ingress, stale/outsider refusal and recipient-private views, plus STARTED views in the actual M6 preparation case; the new solo batch has its explicit cleanup hook.
- `ServerLobby` now owns the service on the production server, ticks after imports, publishes only through the gateway to admitted recipients, and cancels transient preparation before `prepareSave` on stop. Names retained for final disconnect views are bounded by the actual roster. Unresolved cleanup propagates rather than reaching the normal persistence call.
- Registered lobby intents/state in `ModPayloads`, inventory and audience checks. Oversized/malformed intent bytes are consumed without allocation above the cap and arrive as a bounded violation. Connection authentication, existing ingress budgets and violation counting are shared with commands; T11 retains its full security acceptance. User-visible refusals use bounded translated messages, never backend exception text.
- T3b.3 checkpoint 3dc7f0d3 specifies refusal of the legacy solo payload while a lobby owns start. The production `ServerPlayer` solo entry (payload and command) now shares the one-active-match guard; explicit UUID/test placers retain the M6 two-isolated-matches harness. This is not removal of developer/test registry capability.
- **Pending:** combined-source verify, actual service-driven slow preparation/cleanup, full twenty-seed same-source AI gate. T3b.3's four-player start/deadline and legacy-entry reconciliation, T4 client reset/rendering, and T5 durable roster/launch restore remain incomplete. No task/card is marked complete by this integration checkpoint.


### 2026-11-16 — T3b.2 bounded wire values

- Split the oversized parent into preparation/backend, protocol/lifecycle adapters and four-player acceptance; parent T3b and the 5/28 completion count remain unchanged.
- Added test-first wire contracts in 40b63756: all six intent shapes (including both ready values), private four-member views in every phase, field/list bounds, detached collections, truncation and 100,000 seeded malformed inputs for each decoder. The intent schema contains no connection UUID or authoritative occupant assignment.
- Implemented detached `LobbyConfiguration`, sealed `LobbyAction` and recipient `LobbyState` using existing bounded wire primitives. Configuration reuses the aggregate's seat/faction consistency rules. Explicit version/op/enum codes reject unsupported messages; decode failures stay `WireException`. Views carry names, player seats, self and authority/readiness flags; UUIDs remain server-side.
- **Pending:** cloud verification. Backend verify 43957222786 remained queued without a job while this next step was prepared; combined-source verification supersedes it. These values are not registered payloads and install no network admission or global service.
- Test-first d6e72867 specifies intent translation and recipient projection. `LobbyIntents` assigns the authenticated sender on open/join and preserves server occupants during edits; `LobbyViews` refuses outsiders before display-name lookup. Actual gateway publication and tick/stop integration still require engine tests.





### 2026-11-16 — Connect the real lobby backend and avoid matrix dependency stampedes

- Verify 43952117807 on 504a73a1 passed 2,651 canonical cases with zero failures/errors/skips. Full-ci 43954269214 passed functional slow/core replay; its actual preparation/cancellation GameTests passed (6.372 s / 1.585 s). The full AI gate is not green: seeds 10 and 15 failed before tests because Maven Central returned HTTP 429 while compiling build-logic.
- Test-first checkpoint 1905e9f4 replaces the direct positive preparation call with authenticated `LobbyService` and the real `LobbyBackend`, retaining all import, authority, commander and private initial-frame assertions. Verify 43956229197 failed at compilation with the expected missing `LobbyBackend`; all 1,944 reported core cases passed and no backend acceptance is inferred.
- The backend checks actual online membership, owner/operator permissions, occupied seats and retained active/faulted runtimes; it uses the same no-allocation preflight as preparation and requires an explicit private-state publisher. The fast fixture also checks actual operator authority and invalid preflight before allocation. No global service, lobby payload or shutdown hook is claimed yet.
- The matrix now restores Maven dependency artifacts packed by its own successful functional job. This removes twenty simultaneous downloads of identical dependencies without replacing source compilation, assertions, replay, the twenty seeds or the aggregate evidence gate. Only dependency modules are shared; no credentials or project outputs are archived.
- **Next:** verify the backend and exercise its service-driven map start in slow GameTests; obtain a complete same-commit AI gate on the repaired route. T3b remains incomplete.


### 2026-11-16 — Complete registration of the preparation batches

- Verify 43950556665 on 7bc8aa1a correctly rejected all three new batch names because their mandatory `@AfterBatch` hooks were absent (QA-04.6); 1,944 core cases passed and no engine acceptance is inferred.
- Added explicit hooks for `lobby_preflight`, `map_lobby_start` and `map_lobby_cancel`, each using the standard shared cleanup and end-of-batch verification. Reviewed the generator's complete rejection/registration path: each solo batch now has one case and a hook. No isolation, cleanup or report guard was weakened.
- Strengthened the invalid-civ fixture with actual connected members, so an unrelated missing-player rejection cannot make that check pass. Subsequent AI matrices now wait for functional slow/core success before consuming long-runner capacity; the complete twenty-seed gate is unchanged.
- **Next:** verify the corrected registration and real preflight; then run the mandatory full slow/soak against this runtime source. These are concrete fixture corrections, not retries of unchanged code.

### 2026-11-16 — Correct the preparation test's isolated batch

- Verify 43768987265 on 33728b65 compiled the implementation, then the generator correctly rejected the new solo preflight test in the shared `default` batch (QA-04.5). Its engine suite did not run; 1,944 canonical core cases passed. This is a test declaration error, not an accepted runtime result.
- Assigned the test its own `lobby_preflight` batch, retaining `solo`, the timeout and every assertion. No isolation guard or gate was changed.
- **Next:** full verify of the corrected fixture; only then launch complete slow/soak for the new runtime path.

### 2026-11-16 — Implement real M6 preparation and atomic admission

- Test-first cloud verify 43767063415 on 89efdf1a failed at GameTest compilation for absent `LobbyPreparation`, as intended; 1,944 core cases passed. Implemented actual map/scenario/template preflight, import allocation, queued first-tick authority/admin setup and checked command replies before admission.
- A fresh importing runtime accepts only trusted admin initialization after authority is queued; ordinary player submissions remain refused. Prepared terrain/starts/templates are checked again before entering commanders and sending match_join. The real test also requires initial unit/faction/building frames for both members and none for an outsider.
- Cancellation disposes the partial runtime and drops unsent data without resetting the incoming violation limiter. Failed begin with retained runtime state, or explicitly incomplete cleanup after registry removal, keeps the service frozen rather than advertising safe retry; two new service counterexamples cover these cases. Original and cleanup failures remain linked.
- Production preparation refuses non-identity player/faction layouts until T8 installs their command/client/recipient routing; the canonical co-op plan remains tested independently. This is a safe intermediate boundary, not a cut to M7 or completed T3b. Global service/shutdown wiring and private lobby intents/state remain to be installed before production use and whole-card acceptance.
- **Next:** cloud verify of this implementation, then full slow/soak on the same source after compile/fast gates pass. Continue with the actual backend/protocol integration; no owner decision is pending.

### 2026-11-16 — T3b real preparation test-first checkpoint

- Added real-world engine contracts for the shipped M6 map/scenario: initialization before two commander admissions, member-only match_join delivery, invalid preflight before the world allocator, and cancellation of an importing partial runtime.
- `LobbyPreparation` is intentionally absent. Tests use the existing MockViewer and the M6 placer seam, not a substitute runtime or a network stub. Verified the relevant Minecraft/NeoForge 21.1.264 connection, permission and GameTest callback APIs in the source artifact.
- **Next:** inspect the expected missing-API cloud result, implement the preparation transaction and queued import initialization, then full cloud verify/slow/soak. Private lobby intents and roster payloads remain separate required T3b work.

### 2026-11-16 — Full cloud soak repaired and preflight verified

- Full-ci [43763260481](https://github.com/o-e-lytvynskyi/strategos/actions/runs/43763260481) on 6043f74e passed every constituent job and `complete-soak`. Independently read its aggregate manifest: exact seeds 1..20, 20 completed matches, 896,421 game ticks, 17 conquests/3 full-limit matches, one commit, all per-seed XML/log hashes verified. Its checker has eight counterexample tests; 24 slow GameTests, 21 core slow cases and 200 core replay seeds also passed. The inherited 25-minute cancellation is resolved without shrinking the workload or changing its simulation assertions.
- Cloud verify [43765638626](https://github.com/o-e-lytvynskyi/strategos/actions/runs/43765638626) on f1260963 passed: 2,648 canonical cases, zero failures/errors/skips, all seven preflight contracts. Earlier smoke 43763264065 is green; all six PNGs were inspected and no golden changed. The later matrix concurrency cap is scheduling only; all twenty seed jobs and aggregate evidence remain mandatory.
- The runtime first-tick/admission step from 263a137b is now verified through the unchanged production code on 6043f74e; the later pure preflight changes no running tick, core kernel or codec. No JMH/baseline claim is inferred from functional cloud timings.
- **Next:** real M6 allocation, pending import initialization, commander admission and rollback, then private lobby protocol; T3b remains unchecked. No owner input or permission is needed for this authorized continuation.

### 2026-11-15 — Implement validated M6 lobby launch preflight

- Cloud verify 43764218449 on b9772439 failed at unit-test compilation for the absent `LobbyLaunchPlan`, as intended; 1,944 core cases passed. Implemented the immutable preflight via actual M6 `ScenarioSetup.commands`, `SkirmishSetup` resource presets and shipped AI level resolution.
- A frozen ready lobby now yields one initialization per economy faction, distinct player bindings, locked teams, the chosen play/population rules and a frozen template-set hash. Unsupported map/scenario capacity, civ assets, templates or controller combinations are rejected before allocation. Closed factions receive no starting assets. Seven contracts include the real shipped Hard AI strategy.
- Reserve hosted-runner capacity for development by capping subsequent AI matrices at 16 parallel jobs; all twenty seeds and the aggregate coverage gate remain mandatory. The first complete route invocation continues undisturbed on 6043f74e.
- **Next:** full cloud verify for preflight, finish monitoring all twenty seeds, then implement the M6 allocation/preparation/admission backend and private protocol. No world start or payload is certified by this pure plan.

### 2026-11-15 — Cloud route preliminary gates green; T3b launch-plan checkpoint

- Cloud verify 43763256587 on 6043f74e passed: 2,641 canonical XML cases, zero failures/errors/skips, including all three seed-range contracts. Full-ci's evidence-checker counterexamples and slow/core job passed; all twenty AI jobs are still running and the aggregate is not accepted yet.
- Headless smoke 43763264065 passed on linux-llvmpipe. Inspected all six generated/candidate PNGs: scene visible, no black frames or new visual defect. No golden was changed.
- Added six test-first launch-plan contracts using the real M6 scenario command builder: four co-op members/two faction initializations, map/scenario capacity, supported civilisation, frozen readiness and map identity, resource scaling/closed seats, invalid templates, and AI/human controller conflicts/script requirements.
- `LobbyLaunchPlan` is intentionally absent at this checkpoint. **Next:** inspect its cloud missing-API result, implement pure preflight, then the actual M6 preparation backend and private protocol. The complete AI soak remains monitored independently on 6043f74e.

### 2026-11-15 — Implement the complete cloud soak route

- Cloud test-first checkpoint 43762586891 on e3463a9b failed at unit-test compilation for the absent `AiSoakSeeds` selector; 1,944 core cases passed. Implemented validated seed ranges afterward; default seeds remain 1..20, with the same 45-game-minute limit and replay/fault assertions.
- Full-ci now runs its original slow/core replay gates separately from twenty isolated AI seed jobs. Every AI job must produce one successful case, exactly its requested completed replay, and a same-commit manifest; an aggregate gate checks all twenty artifacts and their original XML/log hashes. Missing/duplicate seeds, mixed commits, skipped/failed/empty/truncated XML and incomplete logs have explicit checker counterexamples.
- Complete/incomplete AI logs and crash reports are archived, unlike the inherited route. Pinned download-artifact v8.0.2 to the commit verified against the official release/ref/action.yml. No production dependency or simulation behavior changes.
- **Next:** full cloud verify, complete sharded slow/soak, and headless smoke for the build/workflow change. Do not accept the route or runtime step until the full evidence set is green.

### 2026-11-15 — Continue with deterministic cloud soak sharding

- Operator explicitly requests continuation without another permission pause. Main remains d39069e7; the worktree was clean and no other session lock exists.
- Test-first contract: default seed range remains 1..20; twenty single-seed shards cover exactly that range; invalid/overflowing ranges and indices are refused. Added GameTest output to the unit-test classpath only so the pure test-harness selector can be checked without shipping it in the production jar.
- **Next:** cloud missing-selector checkpoint, then bounded per-seed jobs, complete XML/log coverage validation, and full cloud gates. No simulation duration, assertion or local default seed coverage is reduced.

### 2026-11-15 — Runtime verify/slow passed; cloud soak route cannot finish

- Cloud verify [43759504619](https://github.com/o-e-lytvynskyi/strategos/actions/runs/43759504619) on 263a137b passed: 2,638 canonical XML cases, zero failures/errors/skips; all 11 service contracts and four runtime admission/setup/replay GameTests ran. No new ErrorProne/NullAway finding is present in the changed code; the reported warnings name unchanged core tests/fixtures.
- Cloud full-ci [43759507763](https://github.com/o-e-lytvynskyi/strategos/actions/runs/43759507763), same checkpoint: `verifySlow` completed (24 GameTests, L3 wall time 427.149 s; 21 core slow cases), and the 200-seed core replay soak passed. Its 245 canonical cases have zero failures/errors/skips. The M6 AI soak began, but produced no final XML before runner cancellation.
- GitHub's job annotation is explicit: `The job has exceeded the maximum execution time of 25m0s`. The run is cancelled, not a passing verifySoak. M6 added `:mod:runGameTestSoak` (20 AI matches); its accepted M6 report records 3 h 13 min for verifySoak, while the existing cloud full-ci still has its older 25-minute whole-job bound. Its artifact paths also omit `gametest-soak/logs`.
- **Stop:** developer procedure §8, environment failure: the only currently configured cloud route cannot complete the required gate. The operator requires cloud tests, so no local fallback, shortened seed set, relaxed assertion or blind rerun was used. The runtime step stays WIP, T3b stays unchecked, and the PR stays draft. All launched runs have ended.
- **Next:** provide a cloud execution route for all 20 unchanged AI seeds with per-seed evidence and preserved logs (recommend deterministic sharding and an aggregate coverage check), then finish T3b's actual M6 start/backend and private payload integration. This does not open later cards or accept art/owner decisions.

### 2026-11-15 — T3b runtime authority/admission implementation

- Test-first cloud verify 43759001528 on d94284ac failed at GameTest compilation for the absent `initializeDiplomacy` API, as expected; 1,944 canonical core cases passed. No runtime test acceptance is inferred from that checkpoint.
- Added single-use authority setup for a fresh, unseated runtime; the normal first tick journals it before commands. Admission is refused while authority is queued. Thereafter primary bindings, not the economy faction count, validate player slots; legacy unbound-seat and administrator behavior are preserved.
- Four engine cases cover four players/two factions, early admission without a partial seat, duplicate/late setup, wrong faction count without consuming the setup slot, unchanged legacy refusal and replay to the live hash.
- NET-02 is now `in_progress` in the overview and card: resumed implementation follows the operator's 2026-11-15 instruction to finish M7, within the existing specification. No card acceptance, new roadmap scope or art/owner decision is implied.
- This runtime queue is transient: the production preparation backend must cancel on server stop until T5 covers its persistence. No production start, C2S endpoint, client snapshot privacy or whole T3b acceptance is installed by this step. Recorded the admission/persistence boundary in the lobby KB.
- **Next:** cloud verify plus verifySlow/verifySoak on this exact checkpoint; only accept after all required gates finish. Then implement the production M6 preparation backend and private protocol.

### 2026-11-15 — T3b runtime admission test-first checkpoint

- Added three engine regressions: four distinct player seats controlling two economy factions, single-use first-tick authority installation, and preserved refusal of unbound legacy seats without partial roster changes.
- `MatchRuntime.initializeDiplomacy` is intentionally absent in this checkpoint. These tests establish runtime/registry integration, not mock-client transport or whole-lobby start acceptance. Verified GameTest sequence APIs in the NeoForge 21.1.264 source artifact.
- **Next:** inspect the expected missing-API cloud failure; install the bounded runtime setup path and diplomacy-based seat validation; verify/slow/soak in the cloud before accepting this tick-boundary step.

### 2026-11-15 — T3b authority/preparation service implementation

- Test-first cloud verify 43757256933 on 193c84b7 failed at `:mod:compileTestJava` because `LobbyService` did not exist, as intended; this is not a runtime failure.
- Added authenticated lobby admission/edit/readiness and a frozen preparation transaction behind a backend boundary. Only admitted online members receive state; client configuration cannot assign UUID occupants. A failed/disconnected preparation cancels partial runtime state before readiness resets; failed cleanup keeps the lobby frozen instead of admitting another launch.
- Added asynchronous failure and cleanup-failure cases (11 service contracts total). The production M6 backend, payload registration/client projection and real mock-player start remain unimplemented; this code alone does not close T3b or certify networking/privacy at runtime.
- Cloud verify [43757985631](https://github.com/o-e-lytvynskyi/strategos/actions/runs/43757985631) on f9f002d2 passed: 2,634 canonical XML cases, zero failures/errors/skips, all 11 new service contracts. This pre-match service changes no engine API, payload, running simulation tick or core hot path.
- **Next:** test-first runtime authority installation and player-slot admission, then M6 start/backend and private payload integration. T3b remains unchecked.

### 2026-11-15 — T3b service test-first checkpoint

- Added nine L2 service contract tests for host authority, member-only publication, authenticated readiness, occupied-seat/duplicate-member rejection, configuration invalidation, validation before allocation, frozen single preparation, allocation rollback, disconnect cancellation and the one-active-match restriction.
- `LobbyService` is intentionally absent at this checkpoint. The cloud compilation must fail for that missing API before implementation; this is not runtime acceptance. Production M6 import, admission/payloads and mock-player start remain required parts of T3b.
- **Next:** inspect the cloud red checkpoint, implement the service and its M6 backend, then full required gates.

### 2026-11-15 — Reconcile delivered M6 before resuming T3b

- Fetched main at d39069e7 (PR #55, M6 delivery); reconciled snapshot composition with schema-4 diplomacy, preserving M6 research progress, statistics and vision in compatibility constructors and `withDiplomacy`.
- Added a regression for installing authority over populated M6 fields; stance absence remains checked alongside required diplomacy and optional M6 state.
- Owner requests all tests in the cloud. This merge checkpoint is not accepted until full cloud verify reports pass. M6 runtime dependencies are now available; final art/MVP acceptance prerequisites still require their own evidence.
- Cloud verify [43756194393](https://github.com/o-e-lytvynskyi/strategos/actions/runs/43756194393) on 213d0564 passed in 7m 22s: 2,623 canonical XML cases, zero failures/errors/skips, including the new composition regression and 11 diplomacy cases. No engine API, network path or running tick hot path changed in the conflict resolution.
- Roadmap validation: 245 cards, zero problems. Claims validation inherits one main record error: PR #55 is merged but the M6 branch file remains `ready for review`; the maintainer owns that status correction.
- **Next:** production lobby/start integration (T3b), test-first in the cloud.

### 2026-10-30 — T3a accepted; ready work exhausted

- Cloud verify 41057526203 on 0f0ae4a passed in 9m 3s: 2,321 canonical cases, zero failures/errors/skips; all 12 MatchLobbyTest cases ran. The model uses no engine API or world mutation, adds no payload and touches no running simulation hot path; its exact L1 contract plus full verify is the required foundation evidence. Real mock-player admission/start/privacy and lobby L4 remain T3b/T4 gates.
- Covers four-member acknowledgements of one configuration, stale-ack rejection, co-op field consistency, immutable canonical views, reset/no-op rules and preparation/rollback. AFTER-M6 marker in MatchLobby names real setup validation, atomic freezing and partial-world cleanup still needed in LobbyService. Added the lobby-readiness KB entry.
- **T3a complete (5/28).** T3b requires M6 MATCH-02, which is still unmerged/assigned separately. T4/T5/T6/T8/T9 and later networking tasks follow that production/test-kit chain; T7 also needs M6 MATCH-03 and a safe frozen-damage provenance policy (new KB finding); AI/HUD/final performance need their M6 deliveries; art/floating-platform work retains the MVP/owner/date gates. No further implementation task in the current dependency matrix is ready.
- **Stop:** strategos-dev §8, "nothing is ready". This is the missing runtime prerequisite, not a wait for assignment approval or D-20. All launched cloud runs are finished; the branch and draft PR remain the continuing M7 claim, with no main merge or whole-M7 acceptance implied.

### 2026-10-30 — T3a implementation after the missing-API checkpoint

- Cloud verify 41056261681 failed at mod test compilation because MatchLobby did not yet exist, as expected; 1,686 core cases had passed. This is the preserved test-first checkpoint, not a runtime failure or a hand-in.
- Installed an immutable bounded lobby aggregate with canonical seats, UUID uniqueness outside core, explicit player/faction separation, co-op faction consistency, readiness invalidation and preparation/rollback. It performs no world mutation, network admission, asset lookup or production start.
- Refined ready intents to require the observed configuration revision. State revision still advances on every real update; configuration revision changes only for actual settings changes/rollback, so peers may confirm one configuration concurrently. Added the stale-acknowledgement regression before installing that API. All changes are bounded pre-match logic, not a running simulation hot path.
- AFTER-M6: authenticated LobbyService must validate the real supported setup, freeze it atomically with this value, invoke M6 and clean up partial world state. T3a alone does not prove NET-02 admission/privacy/start acceptance. Full cloud verify is required before acceptance.

### 2026-10-30 — T3a test-first checkpoint

- Published independent lobby-contract tests before MatchLobby exists: canonical detached configuration, player/member/faction bounds, shared-faction consistency, readiness invalidation/no-op updates, open seats/AI, and preparation/rollback. No network payload, player admission, match creation or asset choice is installed.
- T3a starts after the accepted T2b checkpoint 9a8ab27. The expected initial cloud compilation failure establishes the missing API; implementation follows those contracts. Hosting is the owner's accepted D-20 choice; actual production start remains AFTER-M6 in T3b.

### 2026-10-30 — T2b accepted; advance to T3a

- Standard L6 41048400982 passed in 59m 51s: all 22 required GameTests, 27 JMH configurations, 19 passing and two existing report-only scene reports, no invalid report. Foreign CPU mean windows were 0.002102–0.006453; idle ratio 0.270785/0.30, path D p99 0.825912 ms/6 ms, protected-lookup ratio 2.412562/3.0. Current accepted validity policy applies, no threshold or native reference changed.
- Combined current task evidence is complete: verify 41051799948 (2,309 cases), core slow/soak 41050854156 (216), matched server slow 41048580649 (19 M7 cases, identical control set), paired JMH 41051803906 (27 matching configurations, unchanged allocation/time/validity gates), L4 smoke 41054233370 (three inspected original frames and three candidates). All have zero failures/errors/skips where applicable. Earlier red/interrupted evidence remains unchanged in this log.
- The standard server/perf source 42032ea has identical production runtime to the final verified checkpoint e7a75bd. Its JMH allocation evidence is superseded by the repaired identical-probe paired experiment; the probe-only change does not modify the GameTest/perf runtime. No aggregate failed job is relabelled as passed.
- Removed temporary paired slow/core-only/JMH workflow modes and their concurrency branches after collection. Standard nightly command, 25-minute job bound and ten-minute server task bounds are unchanged; standard perf retains only its documented 90-minute aggregate resource window. Telemetry/log retention remains.
- **T2b.1 and T2b.2 complete (4/28).** Proceeding to T3a contract tests before implementation; no M7 feature card or full multiplayer acceptance is declared complete by these foundations. PR #34 stays draft.

### 2026-10-30 — profiler repair validated without relaxing gates

- Verify 41051799948 on e7a75bd passed in 8m 25s: 2,309 canonical cases, zero failures/errors/skips, including the four new Linux counter tests. Core slow/soak 41050854156 completed 216 cases in 4m 23s; server slow 41048580649 completed all 19 M7 cases within its original ten-minute task bound. These complete components are separate raw runs, not a relabelled pass for the older interrupted aggregate jobs.
- Paired JMH 41051803906 passed all 27 matching configurations with the identical measurement repair on both sides. Maximum runtime ratio 1.099172; StateHash 1.000098 and Motion 1.000922. Both sides had valid cumulative CPU telemetry (maximum per-benchmark mean 0.007754/0.005631 against 0.10) and zero observed steal.
- Non-event GC allocation rate fell from about 0.208 MB/s to 0.0073 MB/s; candidate StateHash 0.166 B/op, Motion 2.452, Spatial 0.037 and ShotHash 0.236. Every existing kernel allowance passed. No acceptance threshold, background allowance, production kernel or approved reference changed. See the new JMH-profiler KB entry for the source/experiment distinction.
- Full standard L6 41048400982 and required tooling L4 smoke 41054233370 are still pending; both T2b checkboxes remain open until their evidence is accepted.
- L4 smoke 41054233370 subsequently passed in 3m 16s under Linux llvmpipe/Xvfb, result.ok=true. Inspected all three full-resolution frames and their three resized candidates: rendered terrain/units present, no black frames. The smoke scenario has no approved golden frames (empty diff frame list); this is client/tooling smoke evidence, not golden or M7 lobby acceptance. Standard L6 remains pending.

### 2026-10-30 — D-20 hosting decision received

- The owner explicitly selected dedicated servers plus Open to LAN through the same service, with one active match per server, in this session. Recorded the decision in the M7 card/spec; dedicated-server tests remain authoritative, multiple-match discovery stays outside M7. Main STATE remains the maintainer's record to reconcile.

### 2026-10-30 — matched slow passed; Linux profiler allocation defect

- Matched server run 41048580649 passed on both sides: main 7m 24s, M7 7m 55s, identical 19-case sets, no failures/errors/skips, original ten-minute bounds. Previous interrupted runs remain evidence; a server runtime regression was not established by this control.
- Paired JMH 41048405027 failed the unchanged allocation gate, not the 15% timing or validity gate. All 27 configurations matched; maximum timing ratio 1.061640, CPU load valid on both sides. Five kernels showed essentially identical unexpected allocations in main and M7: steady GC allocation rate about 0.208 MB/s (e.g. Motion 116.49/115.91 B/op), pointing to shared measurement overhead rather than M7's runtime.
- The newly delivered InternalProfiler invokes the Linux MXBean CPU-load reader inside GC measurement callbacks. Replace that Linux path with reusable-buffer cumulative /proc counters, keeping the same validity limit, unavailable verdict and non-Linux behavior. Regression coverage checks counter semantics, malformed/overflowing input and bounded sampling allocation. No allowance, threshold, kernel code or approved baseline changes.
- Next paired measurement applies the identical measurement-only repair to both pinned main and candidate, retaining the control patch and source digests. This isolates runtime differences and tests whether the shared profiler caused the observed allocation rate; no pass is claimed before that experiment completes.

### 2026-10-30 — operator resumed T2b and M7

- The operator explicitly resumed T2b to completion and then available M7 work. The previous stop and its three failed slow attempts remain recorded; this resume starts a fresh investigation, not an unchanged retry.
- Integrated main 8111c0a, including delivered PR #30 and accepted ADR-0026 part B. Resolved shared persistence conflicts by retaining schema-4 authority plus main's stable-template implementation, review cleanup in ContentTests and ADR-0027's ended-match consequence. No released fixtures changed.
- Added a temporary opt-in matched slow job: current main 8111c0a and M7 on one runner, both built before measurement, then sequential unchanged ten-minute server gates. Both exit statuses and logs are preserved even if the first gate fails. This diagnoses server/harness timing before selecting a fix; new main's validity rules apply unchanged.
- Reintroduced temporary paired JMH against delivered main 8111c0a so both sides use ForeignLoadProfiler and the current validity gate. Standard cloud L6 is rerun under the accepted policy; old Linux passes are not silently relabelled as current-policy acceptance.
- Diagnostic run 41048334339 stopped during checkout of the abbreviated control SHA; no build or test executed. Pinned the full 40-character SHA for actions/checkout, as in paired JMH; preserved the failed setup evidence.
- Current merged-head verify 41048293613 passed in 8m 37s, 2,305 canonical cases with no failures/errors/skips. Isolated core slow/soak is collected separately from the matched server run; acceptance requires both complete components, with their original task bounds and all cases present. This is task-level evidence, not a claim that the old aggregate full-ci failures passed.

### 2026-10-30 — full cloud L6 passed, checkpoint and stop

- [Standard perf 40972197958](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972197958) on 33ecb12 passed in 1h 1m 55s. The server log explicitly confirms all 22 required tests passed; all 27 JMH configurations completed. The 21 scene reports contain 19 `pass` and two unchanged `report-only` verdicts (page capture/publication and viewer bytes), no failed/invalid report or CPU-counter validity reason. Nested report-only metrics retain their existing policy.
- Measured foreign CPU ranged from 0 to 0.009142 (0.9142%); idle/moving ratio 0.218495 against 0.30, path D p99 0.484812 ms against the unchanged Linux CI limit of 6 ms, protected-lookup ratio 1.162653 against 3.0. This is full standard Linux CI evidence with factor 3, not native Mac or final multiplayer PERF-10 acceptance.
- Runtime sources are unchanged since the verified/measured 33ecb12 checkpoint. Later commits only record results and adjust diagnostics. Records checks pass: 14 claims, 245 cards, zero problems. No performance reference or threshold was changed.
- **Stop:** strategos-dev §8, "a task fails three times". The slow gate failed in three honest attempts, including true process isolation; T2b.1/T2b.2 remain open (2/27 steps complete). All launched cloud runs and local monitors are finished. The branch is pushed, PR #34 remains draft, and no merge/approval or full-M7 completion is claimed. On resume, investigate a matched server control and harness timing; later M7 runtime work retains the documented M6/hosting dependencies.

### 2026-10-30 — third slow failure, isolation did not explain the deadline

- [Full-ci 40975691944](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40975691944) completed core slow/soak successfully in 3m 48s (216 cases, no failures/errors/skips), then started server slow in a separate Gradle process. The server again hit its unchanged ten-minute timeout; 18/19 server cases had completed.
- `map_fifty` started at 00:02:53 UTC and was interrupted at 00:04:31, after about 97 seconds. The earlier prerequisite control finished that scenario in roughly 105 seconds, but on a different runner; these timings cannot establish a runtime regression or justify a relaxed deadline.
- Retained 898 vmstat intervals: mean runnable queue 2.31, idle 51.26%, zero observed CPU steal. Core workload overlap is not a sufficient explanation because this run eliminated it. Paired core JMH remains green, but does not certify server/harness performance.
- Removed the ineffective diagnostic scheduling changes; retained vmstat and slow-server logs, the original gate command, every test and every timeout. No fourth slow attempt or new feature task is started under strategos-dev §8. Both T2b steps remain unchecked; standard L6 was already running and will be collected before stopping.
- A resumed investigation should compare the prerequisite and candidate server suites on one cloud host, inspect scenario/tick/terrain/GC timing, and preserve failed evidence. No new baseline or threshold is proposed from the current data. Other M7 runtime tasks still need M6/D-20 or later multiplayer dependencies; QA-14 has its own MVP/date gate and branch.

### 2026-10-30 — scheduling hypothesis not yet isolated

- [Full-ci 40974064459](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40974064459) again hit the ten-minute server timeout, after 216 passing core cases and 18/19 finished server cases. `map_fifty` had 38 seconds before termination. The logs still show core soak executing during the server run despite `--no-parallel`; the attempted isolation was ineffective.
- Retained vmstat: 777 intervals, zero CPU steal, mean runnable queue 3.79, mean idle 33.04%; these whole-machine counters cannot attribute load to the mod or core soak individually.
- Split the existing required tasks into two separate Gradle invocations: core slow/soak completes before server slow starts. Added slow-server logs to retained artifacts. No test, timeout, threshold or workload change. The two unsuccessful runs remain recorded; the next run tests actual process isolation.
- Fetched main at this checkpoint: zero commits behind 134271f.

### 2026-10-30 — paired JMH accepted, diagnostic workflow removed

- [Paired JMH 40972202260](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972202260) passed on 33ecb12 against pinned prerequisite 0debda5 on the same runner. Both sides contain 27 matching configurations with equal measurement/JVM settings; the unchanged 15% and allocation comparator passed.
- Maximum candidate/control time ratio was 1.050607 (`CrossingBench.cross384`); codec encode ratio 1.013487. Each JMH took about 8m 42s. vmstat retained 522 control and 521 candidate intervals, zero observed CPU steal on either side, mean idle 67.33%/67.19%. These are supplemental Linux results, not native Mac baseline acceptance or a baseline promotion.
- Removed the temporary opt-in paired job/input and its mode-specific concurrency after collecting its complete evidence. Standard perf keeps its original command/measurements and the documented 90-minute aggregate resource limit. Isolated slow/soak run 40974064459 and standard perf 40972197958 remain pending; T2b steps stay unchecked.

### 2026-10-30 — slow gate timeout and isolated scheduling

- Merged-head verify [40972188768](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972188768) passed in 8m 49s: 2,303 canonical cases, zero failures/errors/skips.
- Full-ci [40972193346](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40972193346) failed at the unchanged 10-minute `runGameTestSlow` timeout. Core slow/soak passed all 216 cases; 18 of 19 server cases had finished, with `map_fifty` starting only 17 seconds before termination. No full slow pass is claimed.
- The logs show core soak concurrent with the server on the two-core runner. The prerequisite control completed the same server suite, including `map_fifty`, in the earlier green run 40941840635. Neither observation proves a runtime regression or excludes one.
- Changed full-ci scheduling to `--no-parallel` and added retained vmstat evidence to test the contention hypothesis. All tests, scene lengths, individual timeouts and the 25-minute job limit are unchanged. The failed run remains evidence; no unchanged retry.

### 2026-10-30 — functional authority gate passed, main synchronized

- Verify 40970846751 on 82ee012 passed in 9m 2s: 2,303 canonical XML test cases, zero failures/errors/skips, including ten core authority cases, two journal-input cases, the NBT authority round trip and both schema-4 fixture readers. Existing shot/stance allocator/hash assertions pass with the mandatory authority-only JSON block.
- Merged main 134271f. Incoming changes are CI dependency-graph submission, security documentation and its branch record; `git diff` confirms zero core/mod runtime changes relative to the verified checkpoint.
- **Next:** final merged-head verify/slow/soak, complete 22-scene L6 under the corrected Actions resource limit, and pinned same-host JMH with the unchanged 15%/allocation gate. Both T2b checkboxes stay open until accepted.

### 2026-10-30 — resumed after usage interruption, schema-shape compatibility

- The operator resumed after the automatic approval reviewer hit a usage limit. No blocked action was bypassed; the same reviewed cloud-read route succeeded on resume.
- Previous verify 40954525926 on 52a563c ran 1,913 canonical cases: two failures, zero skips. All ten new MatchDiplomacyTest cases, both input-codec cases and all sixteen foundation cases passed. The two failures were old shot/stance tests asserting that an unused `play` block is absent; schema 4 now requires authority there.
- Refined those assertions to require exactly the `diplomacy` key while leaving allocator-history, stance, round-trip and hash assertions intact. No scenario, check, fixture or performance criterion is removed.
- **Next:** complete cloud verify, then slow/soak, full L6 and the pinned same-host comparison.

### 2026-10-29 — T2b.2 static-check correction

- Verify 40953293646 on b1239f5 reached ErrorProne and failed `OperatorPrecedence` under the unchanged `-Werror` policy: mixed `||`/`&&` in value equality needed explicit grouping. Added parentheses; no warning was suppressed and no test case had run in that attempt.
- **Next:** third implementation verification attempt. If it fails again, stop this task line under the developer procedure and record the concrete result.

### 2026-10-29 — T2b.2 compilation correction

- Implementation checkpoint e340d0f failed verify 40952453183 at core compilation: the setup diagnostic omitted MatchLog.warn's required simulation-tick argument. No test cases ran. Verified the actual signature and corrected the call; no check or threshold changed.
- **Next:** complete cloud verify before starting the expensive post-implementation gates.

### 2026-10-29 — T2b.2 test-first and implementation

- Test-only checkpoint ac81511 failed cloud verify 40951563816 at compilation because the new setup/query/hash APIs were absent, as intended; zero test cases ran. Added the implementation after inspecting that failure.
- MatchState now owns immutable authority; MatchQueries exposes a detached view. Trusted locked setup is canonicalized, journaled with bounded tag 9, and accepted only once on tick 1. Legacy identity hashes are preserved, with a primitive mixer for non-default authority.
- Schema 4 carries immutable authority in Play, requires its block, migrates old schemas to identity, and validates bounded/canonical data. Existing schema-1/2/3 fixtures remain untouched; new JSON/NBT v4 fixtures/readers are append-only. Added ADR-0028 and a source AFTER-M6 marker for outcome/statistics reconciliation.
- **Next:** cloud verify/slow/soak, full standard perf under the corrected runner limit, and pinned same-host JMH. Both T2b steps remain unchecked until required acceptance completes.

### 2026-10-29 — composition gates and CI execution limit

- Composition 0debda5: verify run 40941835647 passed in 5m 48s with 2,288 canonical XML cases; full-ci 40941840635 passed in 10m 21s with 235 cases. Both have zero failures/errors/skips.
- Standard perf run 40941845938 was cancelled by the GitHub job limit: annotation explicitly says maximum execution time 55m0s. It completed 27 JMH configurations and 16/22 GameTests, with 14 passing perf reports plus one report-only byte report. No full L6 pass is claimed.
- GitHub cancelled during map setup for the next scene; JMH started at 19:42:52 UTC, speed ratio at 19:51:26, and 22 GameTests at 19:52:41. The job ended at 20:33:16. This is the aggregate runner deadline, not evidence of a per-test budget failure.
- Raised only the aggregate Actions resource deadline to 90 minutes so all existing serial scenes can run; individual game-test timeouts, workload lengths, CI factors, performance thresholds and baselines remain unchanged. Added an opt-in pinned same-host JMH job with the existing 15%/allocation comparator.
- Refined phasing: T2b.2 may use the verified persistence contract while T2b.1 stays incomplete; the corrected full L6 closes both steps only after the combined result passes. No performance gate is removed or accepted from the partial run. Publishing test-first contracts before runtime code.
- **Next:** cloud red checkpoint, then production implementation and full combined gates.

### 2026-10-29 — T2b.1 explicit persistence prerequisite composition

- Stacked corrected PR #30 at 588fa57 without treating it as delivered or changing its branch/status. Its M5 snapshot merge and legacy-reference review fixes are now present in this composition; full combined gates are pending.
- Resolved ADR index conflict by keeping main ADR-0026 performance validity and renumbering the imported save-identity ADR to 0027, with policy links updated. The original dependency branch/review records retain their historical numbering. No semantic policy, threshold or baseline change.
- Split T2b before implementation: T2b.1 prerequisite composition/gates, T2b.2 authority installation with versioned snapshots and journaled setup. No live authority integration starts without preservation across restore/replay.
- Main merge checkpoint 760a3b4 passed [cloud verify 40940234420](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40940234420) in 8m 51s: 2,267 canonical XML cases, zero failures/errors/skips, including the 16 T2a cases.
- **Next:** cloud verify, full slow/soak and standard cloud performance on the combined prerequisite; then T2b.2 with failing contract tests first.

### 2026-10-29 — resumed, main synchronization and claims migration

- The operator resumed work, lifting the earlier session stop. Cloud-only testing and pre-merge development authorization remain in effect.
- Merged main through b893a0d: the shared claims registry is removed, claims live in branch headers, the M7 planning delivery is on main, and performance-validity instrumentation/ProtectedBlockIndex structural tests are included. Preserved the implementation specification because the planning document on main is identical to our original planning baseline; retained all T1/T2a implementation changes.
- Removed the obsolete registry conflict and represented M7 ownership in this file; no other claim is changed locally.
- PR #30 now records implemented review fixes and a resolved M5 composition conflict, with verify/soak passing, but full slow/L6 gates are unaccepted. Reassess the corrected contract and actual cloud evidence before installation; do not carry forward stale claims that review fixes are missing.
- **Next:** cloud merge gate and T2b readiness assessment.

### 2026-10-29 — verified main merge and operator stop

- **Main synchronization** — [cloud verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40789677322) on merge 4e1ff97 passed in 6m 2s: 2,253 canonical XML test cases, zero failures/errors/skips; all 16 diplomacy cases executed. Expected negative harness failures were caught by their gates.
- Final fetch retains main dcb5013; main is an ancestor, zero commits behind. No main push, rebase or force-push. Claim and roadmap checks cover 8 rows and 245 cards with zero problems.
- **Stop:** the operator explicitly requested stopping for today once T2a and the main merge were finished. Both are complete. T2b and all later tasks remain unstarted; no continuation or new test run is scheduled. PR #34 stays draft because the whole milestone is not complete.
- **Next on resume:** verify the persistence prerequisite fixes before authority/schema integration; missing M6 runtime and owner/product acceptance gates remain explicit.

### 2026-10-29 — requested main synchronization

- Fetched main from 267da40 to dcb5013 (five commits). Merged current central records and M5 delivery status; resolved the claims.md conflict by keeping all main rows and appending our M7 assignment, without rolling back M6/M5 status fixes.
- The Windows wrapper delta has no executable changes when line endings are ignored; no history is rewritten. T2a runtime sources and tests are retained.
- Reassessed PR #30 at c9dd148: the new commit is a maintainer review record, not a persistence repair. Its open major finding concerns legacy indices reinterpreted against the expanded M5 built-in table; SavedTemplates.resolve still uses the current built-in index table for legacy references. This is a reported unresolved compatibility risk, not a new locally reproduced test failure. T2b/T5 need the verified migration policy and M5 snapshot composition before installation; waiting for claim approval is not the blocker.
- **Next:** cloud verify on the merge checkpoint, then record its result.

### 2026-10-29 — T2a complete

- **T2a** — [cloud verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40788528341) on e8438cd passed in 7m 20s. Canonical XML reports contain 2,253 test cases, zero failures/errors/skips, including all 16 new DiplomacyStateTest cases. Existing static/architecture, adapter, GameTest, server-smoke and negative harness gates pass. No running tick, order or hash kernel changes; no slow/soak/JMH gate is waived. MATCH-06 remains in progress; no card acceptance is claimed.
- A read-only merge-tree assessment against our own save/reload PR #30 found one conflict in Snapshots.java with M5; no prerequisite merge or conflict resolution was applied during T2a. Reassess after fetching current main.
- **Next:** operator-requested main merge, cloud gates, then T2b readiness.

### 2026-10-29 — T2a test-first and implementation checkpoint

- Added 16 L1 contract tests before publishing production classes. [Cloud red checkpoint](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40788100711), commit 4817ee5, failed at core test compilation because DiplomacyState/DiplomacyView were absent, as intended; it ran zero test cases and is not runtime failure evidence.
- Added immutable state/view, explicit legacy identity setup, primary-faction bindings, locked teams, faction-owned non-transitive grants, stale-authority revocation, validated detached snapshot reconstruction and canonical hash contribution. No MatchState, command, save codec, network or step/hash kernel is wired by this foundation.
- **Next:** full cloud verify on the implementation; do not mark T2a complete until its XML reports and gate results pass.

### 2026-10-29 — T1 readiness and task split complete

- **T1** — imported our planning spec, reconciled M5 delivery and pending M6/persistence decisions, split T2a/T2b before implementation. [Cloud verify](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40787146879) on 40e770d passed in 6m 46s with 2,237 canonical XML cases, zero failures/errors/skips. Negative harness inputs were rejected as intended; no runtime defect is inferred from their expected log failures. Records check also passed.
- **Next:** T2a test-first cloud checkpoint, followed by implementation and full cloud verify.

### 2026-10-29 — pre-merge development authorized

- The operator explicitly instructed development without waiting for claim approval. This is a procedural exception for this session, not an independent review, merge, missing prerequisite or product-decision waiver.
- Incorporated our M7 planning specification, reconciled M5 delivery and pending M6/persistence contracts, and split T2 into independent foundations (T2a) and authority/persistence installation (T2b) before runtime changes.
- MATCH-06 enters in progress for those bounded foundations; no card acceptance is complete.
- The operator requested cloud tests. The initial local baseline verifyFast was interrupted on that instruction; it is not passing evidence. Use the existing workflow_dispatch route without changing gates. Publish a WIP checkpoint for cloud verification, then record green task completion only after reading the reports.
- **Next:** cloud T1 gates, then failing L1 tests for T2a.

### 2026-10-29 — whole-milestone assignment request

- The operator requested M7 ownership and available work. Fetched main at 267da40 includes M5 delivery PR #33. The main registry has no M7 implementation holder or reservation; the open PR list contains planning PR #32, save/reload PR #30 and records PR #29, with no competing M7 implementation claim.
- Prepared an assignment-only change: this branch record and one registry row. No runtime, tests, roadmap statuses, STATE or worklog changes.
- Validation: UTF-8 claim check covers 8 rows with zero problems; roadmap check covers 245 cards with zero problems; `git diff --check` passes. No Gradle run is needed for this assignment-only change; no implementation gate is claimed.
- Reviewed the 25-task planning breakdown and separated independent core rules from missing M6 adapters, pending persistence fixes, owner decisions and real-client acceptance.
- **Next:** independent claim review and human merge, then T1/T2; implementation remains unstarted.

## For the owner

1. Independently review and merge the assignment PR. The operator explicitly waived the development wait in this session; independent review and human merge remain required.
2. Planning PR #32 and persistence PR #30 are delivered on current main. D-20 was explicitly resolved in this M7 session on 2026-10-30: dedicated plus LAN, one active match. Reconcile its main STATE record at review. D-03 themes, deferred first-person fighting/scouting, the MVP fun verdict and art acceptance remain separate owner gates.
3. Later two-client measurement and acceptance require the owner's clients and the completed hardening prerequisites. No platform-port decision, public release or performance baseline change is included.

4. Cloud verify takes 6m 46s on the readiness baseline and 7m 20s with the foundation, beyond the procedure's 5-minute target. Keep all existing gates; recommend accepting the cloud duration. Moving checks or changing a Done gate requires an explicit owner decision.

5. Independently review the measured Linux JMH-profiler repair included here. The same allocation pollution occurs on delivered main; extracting the tested measurement-only change before full M7 delivery would unblock other Linux branches. Keep the accepted validity and allocation policies; no main merge or baseline promotion was performed by this session.

6. Main PR #55 merged M6, but `docs/branches/o-e-lytvynskyi-m6-t2-t17.md` still says `ready for review`. `tools/claims.py check --base origin/main` therefore reports one inherited record error. The maintainer must reconcile that status on main; this developer branch does not rewrite another claim's delivery record.

7. **Resolved 2026-11-16:** full cloud soak 43763260481 passed the complete 20-seed set and aggregate evidence gate. The operator authorized the repair and continuation; no decision is pending here. Review the workflow/harness changes normally with the M7 hand-in.

## Decisions taken

- 2026-11-18 · Accept the seven personally inspected Linux/Mesa lobby presentation frames from scenario 44201293302 as the initial visual baseline. The real transport path still needs its corrected final assertion and strict cloud rerun; no gate is waived. Undo by replacing the frames only after inspecting an intended UI change.

- 2026-11-15 · Execute all 20 M6 AI seeds on isolated cloud runners and require an exact same-commit evidence set before the full gate can pass. The unchanged local aggregate remains available. The existing 25-minute slow/core job remains bounded; each new AI job is bounded at 90 minutes, separate from the unchanged 45-game-minute match limit and 240-minute local task bound. Undo by restoring one sequential cloud job only when its runner can execute the complete workload. This applies the operator's explicit continuation/cloud-tests instruction; no further approval pause is needed for branch implementation.

- 2026-10-30 · Split T3a immutable lobby contract from T3b authenticated production service — configuration/readiness transitions can be proved independently while M6/start and D-20 remain open — recombine the task records after both parts pass; no NET-02 runtime criterion is removed or declared complete by T3a.

- 2026-10-30 · Undo diagnostic slow-gate scheduling after true process isolation still failed — overlap was not sufficient to explain the timeout — investigate a matched server control before changing execution policy again; retain telemetry and all unsuccessful evidence.

- 2026-10-30 · Serialize full-ci test projects and retain vmstat — the failed run overlapped core soak with a server on two cores — restore parallel scheduling only after evidence establishes safe coexistence within unchanged timeouts.

- 2026-10-29 · Keep immutable authority directly in Play and validate one value at decode — avoids repeated per-seat reconstruction while retaining detached snapshots — change only the internal representation behind the codec/value boundary if measurements justify it.
- 2026-10-29 · Use schema 4 with explicit legacy identity migration and required current authority — old readers must reject rather than ignore rights — any future format interpretation requires a new migration/version.

- 2026-10-29 · Give the standard perf Actions job 90 minutes after the observed 55-minute cancellation at 16/22 cases — allows completion of the same measurements without changing their acceptance criteria — restore the old resource limit if a reviewed execution layout fits the full suite within it.
- 2026-10-29 · Share the final full L6 acceptance across composition and authority installation — dependency code already passes verify/slow/soak; the remaining issue is a runner deadline, not an accepted performance result — require separate complete runs if review needs independent predecessor L6 evidence.

- 2026-10-29 · Explicitly stack our corrected PR #30 as a prerequisite — its persistence contract is needed to avoid competing schema changes; combined gates must pass and its own review/delivery remains separate — base M7 on the delivered equivalent when that PR merges.
- 2026-10-29 · Renumber the imported save-identity ADR to 0027 — main already owns 0026 for performance validity — reconcile the number with the eventual dependency delivery without changing either policy.

- 2026-10-29 · Make the standalone diplomacy state immutable and use it as the read view — old query/snapshot references cannot observe later mutation; canonical snapshots and a hash contribution are tested separately from match-schema integration — replace the representation behind DiplomacyView if measured setup costs justify it.
- 2026-10-29 · Grants are faction-owned, primary-only and non-transitive; rebind/team changes revoke stale authority — prevents a reused seat or alliance change from retaining old control — change the explicit policy and regression tests together if the product model changes. Disconnect does not unbind.

- 2026-10-29 · Keep whole-milestone ownership as requested, with per-task readiness gates — prevents competing claims without pretending missing dependencies are delivered — release or narrow the assignment through the normal registry PR if ownership changes.
- 2026-10-29 · Start with independent core identity/diplomacy/grant foundations after readiness reconciliation — they can be tested without unmerged M6 runtime code — revisit the order when prerequisites merge.

## Pitfalls

- Runtime admission must wait for installed authority and resolve player-to-faction bindings; the first-tick queue is transient, not a durable launch transaction ([lobby readiness](../kb/lobby-readiness.md)).

- Added [lobby readiness](../kb/lobby-readiness.md) and the unresolved [frozen damage provenance](../kb/frozen-damage-authority.md) finding for T7. No allied-damage runtime acceptance is inferred from that source audit.

- Added [diplomacy restore](../kb/diplomacy-restore.md): current authority versus legacy fallback, immutable sharing, compatible live writers and hash preservation.

- Use `python -X utf8` for bookkeeping on Windows; a legacy-code-page run can misleadingly report zero cards. Existing toolchain KB documents this.

## Review

Pending independent review.
