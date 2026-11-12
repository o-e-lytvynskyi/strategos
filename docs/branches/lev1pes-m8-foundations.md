# M8 T1–T6 on dev/lev1pes/m8-foundations

**Developer:** lev1pes · **Branch:** dev/lev1pes/m8-foundations · **Work:** M8 T1–T6 · **Base:** main @ bb800d0e526bf4d05d5bf2bcc3b71797e52e1d6b · **Claimed:** 2026-10-28 · **Status:** claimed

**Note:** owner requested M8 independent work in the operator session; assignment merged in [PR #31](https://github.com/o-e-lytvynskyi/strategos/pull/31), not an implementation delivery; specification and contract-tested foundations only; runtime M6/M7 integration and final M8 gates excluded until prerequisites merge

## Now

- **Milestone:** M8 Fog of war; independent foundation phase, zero implementation tasks complete. The owner requested taking M8 and implementing what can work without preceding unfinished milestones in the operator session on 2026-10-28.
- **In progress:** assignment request only; no development before its merge.
- **Last finished task:** pre-claim dependency assessment against main, the five M8 cards, ADR-0016 and the merged M6 specification.
- **Next task:** after assignment merge, T1 specifies the full M8 plan and formalizes the independent T1–T6 below. Existing M8 cards are proposed; record the owner's authorization for this independent phase in the specification and keep incomplete integration criteria explicit.
- **Merged from main last:** bb800d0 (2026-10-28).

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

## For the owner

1. Independent maintainer review/merge of this claim is required before development. Record the owner's M8 independent-phase opening in main's records in the maintainer role; this developer session does not write STATE.md or merge its own claim.
2. M6/M7 runtime delivery remains a prerequisite for integrated M8 acceptance. Recommend the bounded T1–T6 foundation phase above; do not declare multiplayer fog, leak freedom or performance complete from unit contracts.

## Decisions taken

- 2026-10-28 · Use a dedicated main-based branch and one agent. Keep prior bug-fix and records PRs separate. Scope the assignment to specification and independent/contract-based foundations; undo by releasing this assignment, without touching others' work.
- 2026-10-28 · Test vision, building memory and audience decisions over explicit inputs rather than importing or rebuilding M6/M7 internals. Actual adapters and end-to-end acceptance remain AFTER-M dependencies. Refine these boundaries in T1 before production edits.

## Pitfalls

- Proposed M8 cards and a foundation implementation are not completed integrated fog. Source-set contracts must not leak hidden live state into remembered building/AI records.

## Review

Claim request only; awaiting independent maintainer review and human merge.
