# M8 T1–T6 on dev/lev1pes/m8-foundations

**Developer:** lev1pes · **Branch:** dev/lev1pes/m8-foundations · **Work:** M8 T1–T6 · **Base:** main @ bb800d0e526bf4d05d5bf2bcc3b71797e52e1d6b · **Claimed:** 2026-10-28 · **Status:** in progress

**Note:** owner requested M8 independent work in the operator session; assignment merged in [PR #31](https://github.com/o-e-lytvynskyi/strategos/pull/31), not an implementation delivery; specification and contract-tested foundations only; runtime M6/M7 integration and final M8 gates excluded until prerequisites merge

## Now

- **Milestone:** M8 Fog of war; only independent T1–T6 are assigned. Claim merged in PR #31. No implementation tasks are complete.
- **In progress:** none. Stopped after completed T1 at the owner's request on 2026-10-28; continue remaining work the next day only after the owner resumes.
- **Last finished task:** T1 specification and acceptance/dependency matrix in [M8-fog-of-war.md](../milestones/M8-fog-of-war.md); all checks pass as recorded below.
- **Next task:** T2 shared visibility policy, only after the owner resumes. Full runtime integration remains unassigned T7–T12.
- **Merged from main last:** 3c1f430 (2026-10-28).

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

Main contains M0–M4 runtime. M5 is assigned elsewhere; M6's specification is merged, its runtime is not. M7/M8 implementations are absent. Read other developers' specifications as contract data; do not build, copy or alter their unfinished branches.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | AFTER markers planned |
|---|---|---|---|
| M5 | Projectile effects and combat-only outbound channels | Independent channel inventory now; against declared inputs for policies; actual projectile guard/L3 integration after M5 | AFTER-M5: projectile FX channel audit/positive controls |
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

## For the owner

1. Claim #31 is merged. Maintainer records may reflect the bounded M8 opening; this developer session does not write STATE.md or merge its own work. No new permission is needed to resume assigned T2–T6.
2. M6/M7 runtime delivery remains a prerequisite for integrated M8 acceptance. The specification preserves all full-card criteria; specified does not mean implemented or accepted.

3. Serial Windows verify took 6m 8s against the five-minute target. Owner may accept the platform duration or authorize a separate gate redesign; no task was moved or weakened. The existing timing-test variability above is not fixed by this documentation task.

## Decisions taken

- 2026-10-28 · Use a dedicated main-based branch and one agent. Keep prior bug-fix and records PRs separate. Scope the assignment to specification and independent/contract-based foundations; undo by releasing this assignment, without touching others' work.
- 2026-10-28 · Test vision, building memory and audience decisions over explicit inputs rather than importing or rebuilding M6/M7 internals. Actual adapters and end-to-end acceptance remain AFTER-M dependencies. Refine these boundaries in T1 before production edits.
- 2026-10-28 · Specify the full T1–T12 acceptance path while implementing only assigned T1–T6. Keep all five cards specified until implementation starts, never done from isolated contracts. Undo by revising the spec/task mapping without dropping card criteria.

## Pitfalls

- Specified M8 cards and a foundation implementation are not completed integrated fog. Source-set contracts must not leak hidden live state into remembered building/AI records.

## Review

Assignment merged in #31; no hand-in PR yet because T2–T6 are unfinished. T1 is documentation only; runtime behavior is unchanged.
