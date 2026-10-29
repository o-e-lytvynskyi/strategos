# M8 T1–T6 on dev/lev1pes/m8-foundations

**Developer:** lev1pes · **Branch:** dev/lev1pes/m8-foundations · **Work:** M8 T1–T6 · **Base:** main @ bb800d0e526bf4d05d5bf2bcc3b71797e52e1d6b · **Claimed:** 2026-10-28 · **Status:** in progress

**Note:** owner requested M8 independent work in the operator session; assignment merged in [PR #31](https://github.com/o-e-lytvynskyi/strategos/pull/31), not an implementation delivery; specification and contract-tested foundations only; runtime M6/M7 integration and final M8 gates excluded until prerequisites merge

## Now

- **Milestone:** M8 Fog of war; only independent T1–T6 are assigned. Claim merged in PR #31. T2–T4 are complete; T5–T6 remain.
- **In progress:** T5 implementation and fast checks complete; full gate blocked by the unchanged ProtectedBlockIndexTest timing assertion. T6 review/KB preparation is complete but final verification/handoff is not.
- **Last finished task:** T4 audience decisions and outbound audit; fast/L2/full verify and existing L4 smoke passed.
- **Next task:** owner scope decision for the existing timing-test defect; preserve the failed gate, do not retry until green or hand in unfinished work. Full runtime integration remains unassigned T7–T12.
- **Merged from main last:** dcb5013 (2026-10-29), including M5 delivery (#33) and reconciled claims (#29).

## Scope and intended task split

T1–T6 are the bounded claim; task numbering is to be formalized by T1 because main has no M8 specification. The rest of M8 is not claimed as ready implementation.

| Task | Ready foundation | Verification and boundary |
|---|---|---|
| T1 | M8 specification, card-to-task mapping, acceptance/dependency matrix and integration contracts | Roadmap/claims checks; retain every card criterion and gate; no premature completion of full cards |
| T2 | FOW-02 team visibility/explored-union policy over explicit visibility and diplomacy query contracts | L1 with deterministic fake inputs; exercise the existing Visibility/replication seam where possible; do not duplicate M6 VisionGrid or implement M7 diplomacy |
| T3 | FOW-03 last-seen building memory and immutable snapshot representation | L1: hidden damage/destruction cannot refresh memory; re-scouting updates it; snapshot round-trip using stable IDs; live match persistence integration waits |
| T4 | FOW-05 outbound-channel inventory and a fail-closed audience decision contract | L1/L2 for declared channels and positive/negative visibility inputs; audit current senders; real per-channel mock-player integration waits |
| T5 | AI-07 knowledge/scouting input contract over observed information and known starts | L1 hidden-information/last-seen input invariants; no claim of integrated scouting, difficulty balance or 200-match acceptance before AI-18 exists |
| T6 | Foundation verification, API/KB documentation and integration handoff | Required available gates, each contract seam and AFTER-M markers inventoried; report partial delivery honestly |

## Depends on unfinished work

Main contains M0–M5 runtime. M5 delivery #33 is merged; M6's specification is merged, its runtime is not. M7 runtime is not merged. Read other developers' specifications as contract data; do not build, copy or alter their unfinished branches.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | AFTER markers planned |
|---|---|---|---|
| M5 (merged) | Projectile effects and combat-only outbound channels | Sender/endpoints audited against ShotFx, FxSender and NetGateway; T9 runtime guards still need M6/M7 | M5 audit dependency resolved; runtime positive/negative controls remain T9 |
| M6 | FOW-01 VisionGrid/VisibilityIndex, FOW-07 explored/pairing, AI-18 observed-only AiView | Against query/event contracts, with fakes in L1. Do not recreate the solo grid, shroud or tracker refresher. Real adapters, live persistence and AI routing after M6 | AFTER-M6: solo-vision adapters, snapshot integration and observed AI inputs |
| M7 | MATCH-06 diplomacy/shared vision, NET-03 recipient state, NET-09 commander/first-person bodies | Against a diplomacy/recipient contract now. Actual diplomacy, commander protection, targeted mock-player tests and four-player setup after M7 | AFTER-M7: diplomacy/recipient adapters, commander leak tests and multiplayer fixtures |
| M7 | PERF-10 multiplayer performance reference | After. PERF-15's 20-minute 4x150 scenario and stress acceptance cannot be certified on the M4 base | AFTER-M7: full PERF-15 and multiplayer performance reference |

Full FOW-02 targeted tracking, FOW-03 live save/minimap ghosts, FOW-05 all-channel L3, AI-07 integrated AI/balance, PERF-15, compatibility runs and consent-batch/client acceptance are later integration tasks, excluded from this ready implementation claim. No full M8 completion will be reported from isolated contracts.

## Log

### 2026-10-28 — owner-requested M8 dependency assessment and claim

- No active M8 assignment or competing claim PR exists in the freshly fetched registry/open PRs. Main is bb800d0; open PRs #29/#30 remain separate work.
- M8's five cards are FOW-02, FOW-03, FOW-05, AI-07 and PERF-15. All currently say proposed and no M8 specification exists. The owner's session instruction authorizes the independent phase; it does not erase prerequisites or final acceptance.
- Existing usable seams: core sim.replicate.Visibility, ReplicationService and the single NetGateway/Audience sender. M6 specifies sim.vision.VisionGrid and VisibilityIndex, so this phase uses contracts rather than a competing solo-fog implementation.
- Claim changes only docs/claims.md and this branch file. No production, test, workflow, owner record or roadmap status edits before assignment merge.

### 2026-10-28 — implementation session

- Claim PR #31 merged; fast-forwarded the dedicated worktree to main 3c1f430. New specification preserves every card criterion and separates assigned T1–T6 from future T7–T12 integration. No runtime wiring or prerequisite implementation is claimed.
- Initial sandboxed Gradle startup could not open the global wrapper lock; rerun with the required cache access. This was environment setup, not a failed test.
- T1 complete: all five cards specified; full T1–T12 plan and acceptance/dependency matrix; only T1–T6 assigned. No production or test source changes. Owner requested stopping after T1 and resuming with T2 the next day; no hand-in PR for unfinished foundations.
- Verification: verifyFast 2m 49s; roadmap 245/0 problems; claims 7/0; full serial verify 6m 8s, 1,310 core + 95 mod + 212 GameTests, zero failures/errors/skips. Smoke, restart and both arena markers ok. git diff --check clean.
- Initial default parallel verify failed in 3m 12s at existing ProtectedBlockIndexTest.aLookupCostsTheSameAtAnySize: 269.1ns versus 54.1ns, ratio 4.977 against <3. Saved the original XML as build/m8-t1-first-protected-block.xml. One isolated diagnostic passed in 18s; full verify --max-workers=1 then passed. Preserve both outcomes: variability is established, its cause is not. No test, threshold or baseline changed.
- Local logs: build/m8-baseline.log, build/m8-t1-verify.log, build/m8-t1-protected-confirm.log and build/m8-t1-serial-verify.log. These are ignored evidence, not repository artifacts.

### 2026-10-28 — resumed T2

- Owner resumed assigned T2–T6. Fresh main remains 3c1f430; registry still assigns this phase to lev1pes, no competing M8 PR. Latest main workflow list includes the historical perf failure 40176248614 already repaired by merged #21; no new failure is inferred.
- Baseline verifyFast passes in 31s. New TeamVisionTest first failed all four behavior tests against the deliberately denying implementation (14s); implementation unions direct authorized views and the existing ReplicationService now exercises the contract in L1.
- AFTER-M6 in TeamVision: adapt VisibilityIndex/cell queries. AFTER-M7: frozen diplomacy, persistent received exploration and materialized O(1) tracking views. The runtime is unchanged; FOW-02 is in_progress, not complete.

- T2 final gates: verifyFast 2m 5s; full serial verify 7m 37s; 1,314 core + 95 mod + 212 GameTests, zero failures/errors/skips. No new compiler findings. Existing threshold/baseline files unchanged.

### 2026-10-28 — T3 building memory

- Six L1 tests, initially three expected failures against the incomplete empty-site implementation (14s). Immutable snapshots hold stable TemplateId, full generation-bearing building handle, observed owner/placement/health and time; absent hidden buildings do not update memory.
- Source inspection found valid negative signed handles (Handle uses 20 index + 12 generation bits). Correct the specification's sign restriction: validate generation and building index instead. Pin a generation-2048 round trip and independent reused-generation identity.
- Persist a monotonic event watermark, including removal and unknown empty-site events, so old queued observations cannot resurrect a cleared mirage after save/load. No MatchSnapshot/schema change or live persistence integration.
- AFTER-M6/M7 in BuildingMemory: match/viewer-scoped save/hash adapters, recipient authorization and positive remembered-footprint emptiness. Full FOW-03 remains in_progress.

- T3 final gates: verifyFast 2m 26s; full serial verify 8m 58s; 1,320 core + 95 mod + 212 GameTests, zero failures/errors/skips. No new compiler findings or existing codec/threshold/baseline changes.

### 2026-10-28 — T4 channel decisions and audit

- Five L1 tests, initially four expected failures against the denying stub (18s). Outside-match recipients and unknown/null channels deny; observers/owners have explicit controls; enemy commanders stay hidden; allied bodies use direct sharing; destination sight permits only projectile FX and memory permits only mirages. Scoreboard membership is always forbidden. Exhaustive switch has no default permission for future channels.
- Audited 13 registered S2C payloads, all seven C2S exclusions and vanilla/semantic routes. Current SeesCell broadcasts, live building/deposit frames, page-batched FX and operator debug particles need real guards after M6/M7. Public WAVE telegraph is intentional under merged M6 spec §1.6 row 17.
- New L2 FogChannelInventoryTest compares real ModPayloads with unique documented rows; existing PayloadAudienceTest still checks unknown-type refusal and declared audiences. Add the audit file as a test input so documentation edits invalidate the Gradle test result. No sender, transport or runtime guard changes.
- AFTER-M5/M6/M7 remain in FogAudience and the audit. Full FOW-05 is in_progress; packet controls wait for integration.

- T4 verifyFast + targeted L2 audience/inventory tests pass in 3m 8s (five new L1 cases, four targeted L2 tests). Existing L4 smoke passes in 49s with ok=true; inspected smoke-20/40/60 PNGs: normal scene and all five units. No Windows/NVIDIA goldens exist, so comparison produced candidates only; no golden changed. Initial invocation failed before opening a window because PowerShell split the unquoted dotted -P argument; corrected by quoting it, preserving both logs.

- T4 final serial verify passes in 7m 1s: 1,325 core + 96 mod + 212 GameTests, zero failures/errors/skips. Supporting test metadata adds only the audit input file; no task, threshold, timeout or sender is weakened.

### 2026-10-28 — T5 observed AI/scouting input

- Four L1 tests, initially two expected failures against an unfiltered/non-expiring capture (14s). Filter own/allied/hidden candidates using explicit enemy and visibility queries; retain only public Target fields, never UnitView.target/private orders. Targets expire outside their captured tick.
- Keep immutable building memory separate from current unit targets and public starts in map order. Defensive copies, duplicate-handle and nonfinite-start checks, future-memory rejection and private-order noninterference are pinned. This is knowledge preparation, not a new controller or evidence of five-minute scouting/200-match balance acceptance.
- AFTER-M6/M7 in ScoutKnowledge: authoritative AI-18 observations, ordinary validated commands, diplomacy and viewer-scoped building memories. AI-07 remains in_progress until T10 integration.

### 2026-10-28 — T5 verification blocker and T6 preparation

- T5 verifyFast passes in 1m 47s; all 19 new L1 tests are green, as is the new L2 audit. Full serial verify failed in 7m 21s: ProtectedBlockIndexTest.aLookupCostsTheSameAtAnySize measured 88.6ns/27.5ns = 3.219218 against unchanged <3. Other 1,329 core, 95 remaining mod and 212 GameTests pass. Original XML retained as build/m8-t5-protected-failure.xml; full log build/m8-t5-verify.log.
- The protected-index production and test sources are byte-for-byte unchanged from main (git diff is empty). One unchanged isolated diagnostic passes in 19s; this confirms variability, not a repair. CPU context collected over that later 20.05s window: six logical CPUs, system busy 0.7196, Java CPU fractions total about 0.4497; remaining work includes the profiler and other processes. This is not the missing original failure window and does not retroactively invalidate it. Preserve build/m8-t5-protected-confirm.log and build/m8-t5-protected-context.json.
- Read-only fastutil 8.5.12 probe audit: stride-4 probes cover z modulo 4 == 0 only. That subset averages 1.26855 key probes versus 1.29028 across all keys, so this asymmetry does not explain the high large/small timing ratio. Diagnostic source stays in ignored build/IndexProbeAudit.java. No gate or fixture was changed.
- Prepared T6 API/KB notes, reviewed all four production classes (87/134/70/91 lines; no new Minecraft imports, TODOs, suppressions or mutable static collections). Runtime fog/AI, MatchSnapshot, hashes, tracking and network senders remain untouched. Fresh origin/main is still 3c1f430. All AFTER dependencies remain explicit.
- T5/T6 are not marked done and no hand-in PR is opened while the full gate is red. This is a WIP checkpoint; investigate/fix the pre-existing test only after the owner clarifies assignment scope, keeping the limit and workload intact.

## For the owner

- **Refresh verification (2026-10-29):** verifyFast plus the targeted
  FogChannelInventoryTest pass in 5m 17s; 1,631 core tests and the L2 inventory test
  have zero failures/errors/skips. Roadmap: 245 cards / 0 problems; claims: 7 / 0.
  The three roadmap-tool self-tests also pass. Initial sandbox attempts at those
  self-tests could not access temporary directories; the run with cache/temp
  permissions passed. No full verify, slow/soak, JMH or client acceptance is claimed
  for this synchronization; the previous timing-gate failure is retained.

- **2026-10-29 branch refresh:** integrated main dcb5013 (M5 #33 and claims #29)
  without conflicts. Audited ShotFx/FxSender/NetGateway and updated the outbound
  inventory for SEES_EITHER; M5 is no longer an unfinished dependency. Actual fog
  filtering remains T9 after M6/M7. ProtectedBlockIndex and its timing test have no
  changes between the previous main base and this one; the recorded full-gate
  failure remains unresolved. T5/T6 completion and scope are unchanged. M7 branches
  were excluded from this refresh at the owner's request.

0. Blocking: choose a separate assigned claim for the protected-index measurement defect, or explicitly extend this M8 claim to its bounded correction. The isolated pass is not acceptance evidence for the failed full run; no threshold, retry policy or skipped gate is proposed.
1. Claim #31 is merged. Maintainer records may reflect the bounded M8 opening; this developer session does not write STATE.md or merge its own work. No new permission is needed to resume assigned T2–T6.
2. M6/M7 runtime delivery remains a prerequisite for integrated M8 acceptance. The specification preserves all full-card criteria; specified does not mean implemented or accepted.

3. Serial Windows verify took 6m 8s for T1, 7m 37s for T2 and 8m 58s for T3 against the five-minute target. Owner may accept the platform duration or authorize a separate gate redesign; no task was moved or weakened. The existing timing-test variability above is not fixed by this documentation task.

## Decisions taken

- 2026-10-28 · Use a dedicated main-based branch and one agent. Keep prior bug-fix and records PRs separate. Scope the assignment to specification and independent/contract-based foundations; undo by releasing this assignment, without touching others' work.
- 2026-10-28 · Test vision, building memory and audience decisions over explicit inputs rather than importing or rebuilding M6/M7 internals. Actual adapters and end-to-end acceptance remain AFTER-M dependencies. Refine these boundaries in T1 before production edits.
- 2026-10-28 · Specify the full T1–T12 acceptance path while implementing only assigned T1–T6. Keep all five cards specified until implementation starts, never done from isolated contracts. Undo by revising the spec/task mapping without dropping card criteria.

## Pitfalls

- Specified M8 cards and a foundation implementation are not completed integrated fog. Source-set contracts must not leak hidden live state into remembered building/AI records.

## Review

Assignment merged in #31. T2–T4 are green and pushed; T5 fast checks are green, full verification is blocked by the existing timing test. T6 review/KB prepared; no hand-in PR or ready-for-review claim while the gate is red. Runtime behavior is unchanged.
