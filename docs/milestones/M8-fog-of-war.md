# M8 · Fog of war

Status: independent foundations authorized by the owner on 2026-10-28; assignment
merged in PR #31. Full runtime integration is blocked on M6/M7. This is a partial
delivery plan, not an assertion that the proposed cards are already implemented.

## 1. Goal, demo and scope

Fair multiplayer fog: team vision, targeted tracking, last-seen building mirages,
guarded outbound channels and observed-only AI knowledge. Final demonstration:
two clients in a 2 v 2; allies share vision, enemies disappear from the entity
list, destroyed unseen buildings remain ghosts until scouted, and AI scouts starts.

### 1.1 Authority and prerequisites

The five cards in [M8](../roadmap/M8-fog-of-war.md), ADR-0016 and the cumulative
Definition of Done remain authoritative. Main has M0–M4 runtime. M6's merged
specification defines `sim.vision.VisionGrid`, `VisibilityIndex` and observed
`AiView`; these implementations are not available on this base. Do not duplicate
them. M7 supplies diplomacy, recipient roles, commander gates and PERF-10.
Only T1–T6 are assigned here; T7–T12 need future claims and merged prerequisites.

### 1.2 Traceability and acceptance matrix

| Card | Foundation | Final acceptance and integration tasks |
|---|---|---|
| FOW-02 | T2: direct shared vision/explored union and existing replication seam | T7: hidden enemy never paired, visible positive control, reveal ≤6 ticks, hide ≤15 ticks, allied vision, inventory without updatePlayer cancellation; T11: 4×150 budget A |
| FOW-03 | T3: immutable last-seen buildings and standalone stable-ID snapshot | T8: destruction unseen preserves memory; only observation changes it; real match save/load of memory and shared explored state; T12: minimap ghost golden |
| FOW-05 | T4: channel inventory and fail-closed decision contract | T9: one mock-player negative/positive pair per channel; existing L2 payload audience declaration check and explicit undeclared-type rejection; documented terrain/entity-ID leaks |
| AI-07 | T5: knowledge input with visible targets, remembered buildings and known starts | T10: never target unseen enemy; scout every start within 5 minutes; Normal beats Easy ≥124/200 seeded matches; L3 adapter checks |
| PERF-15 | T6: handoff and unchanged budget plan | T11: 20 minutes of 4×150 fog-edge combat within A–E, 2× stress C p95 ≤50ms, report vision/tracking/pairing costs; T12: PERF-14 fog scene |

### 1.3 Differences and implementation findings

| Item | Decision and reason | Final obligation |
|---|---|---|
| Partial opening | Owner authorized independent foundations before M6/M7 runtime | Full cards remain in_progress until integration acceptance passes |
| Solo fog | M8 card prose assumes M6 already exists; it does not on this base | Consume merged M6, never replace its grid/hysteresis |
| Memory persistence | Standalone codec over immutable observed records only, not MatchSnapshot edits | T8 wires save/hash/schema migration against actual merged persistence version |
| Audience guards | Pure decisions are not installed in the gateway yet | T9 wires every sender and proves real packets with mock players |
| AI | Knowledge input is separate from the currently omniscient AiView | T10 supplies it from AI-18; no scouting/balance claim from a contract |

No card criterion, timing target, threshold or baseline is removed or weakened.
PERF-15 stays specified while runtime cards may be in_progress for foundations.

## 2. Design and contracts

### 2.1 Team vision (T2)

`sim.replicate.TeamVision` implements existing `Visibility`. Constructor inputs:
participant count (1–16), the solo `Visibility` query, visible/explored cell queries,
and a directional `shares(source, viewer)` query. Queries must be immutable for
the tick. Self always contributes; only directly authorized sources contribute;
sharing is not transitive. Invalid/non-participant slots fail closed. An observer
is a separate recipient role, never an extra source. Owner/allied bodies whose
faction directly contributes vision are visible. Enemy visibility unions the
authorized solo views. Cell visibility/exploration uses OR, not addition.

This contract neither owns a grid nor runs the 10-tick hysteresis again. It reads
M6's already filtered unit visibility. At T7, freeze diplomacy for each tick and
compute changed viewer/unit pairs when either vision or sharing changes. Removing
sharing removes access immediately to that source's current query; permanent
exploration received earlier must be materialized into each recipient's saved M6
explored state before diplomacy changes. The contract itself is a stateless query.

### 2.2 Building memory (T3)

`sim.replicate.BuildingMemory` is an immutable, deterministically handle-sorted
snapshot. Nested `Seen` stores only handle, stable `TemplateId`, observed owner,
anchor/baseY/rotation, health fraction and observation tick. No queue, live
BuildingView, current enemy statistics or hidden state reference is retained.
Explicit `observe` replaces a record, while `scoutedEmpty` removes it only after
the adapter positively observes that remembered site empty. Missing from a live
building list is not evidence of an empty site. Hidden damage, capture, template
changes and destruction do not create observations. No last-seen units.

The standalone codec round-trips the observed records with stable template IDs,
not session-local net indices. Reject negative ticks/handles, invalid owner,
rotation/health, duplicates and oversized collections. Capture snapshots by value.
T8 must scope memory by match and viewer, validate site emptiness against the
remembered footprint, handle replacement/generation identity, materialize shared
exploration, migrate saves and include gameplay-relevant memory in the state hash.
Rendering resolves the MIRAGE kind from stable identity; it never queries the
hidden live building to refresh remembered health/owner.

### 2.3 Channel guard contract (T4)

`sim.replicate.FogAudience` declares semantic channels and explicit recipient
evidence: member of this match, observer, owner, sees source, sees destination.
Unknown channel IDs deny. Nonmembers deny even if other flags are true. Observers
are authorized only through explicit recipient classification. Hidden commanders
deny for enemies even when visible. Projectiles require either end visible.
Remembered building data uses its own last-seen channel and evidence; current
selection/HP channels cannot use that evidence. Known terrain/entity-ID side
channels are documented, not misrepresented as protected payloads.

The planned `docs/design/m8-outbound-channels.md` inventory is mandatory T4 output.
Existing `PayloadAudienceTest` remains the L2 declaration gate. A pure channel
test proves decisions only; it cannot prove packet emission or vanilla backstops.

### 2.4 AI knowledge input (T5)

`sim.ai.ScoutKnowledge` accepts visible enemy observations, immutable building
memory and public map start positions. It exposes attack candidates only from
currently visible units; a remembered building is a scouting destination, never
an attackable handle. No MatchQueries/BuildingView/world reference is accepted.
Visited starts are derived from observations at the start, not the enemy's current
position. Deterministic map ordering and immutable copies prevent hidden mutation.
This is input preparation, not a second AI controller. It cannot grant a
reveal-map exception; the labelled top-level exception remains the existing D-19
rule for T10. Supply genuinely observed-only inputs from AI-18 after M6.

### 2.5 Runtime integration (T7–T12)

T7 targets only flipped owned-body/viewer pairs every 5 ticks via ADR-0016's two
access transformers; never cancel updatePlayer. Verify the actual engine APIs in
the 1.21.1 sources, register inventory entries and keep the public move(viewer)
fallback. Re-pair chunk arrivals, stationary viewers and alliance changes. Run
compat including Moonrise. T8 publishes per-viewer memory/exploration deltas and
minimap ghosts. T9 audits every vanilla and custom path, including debug/operator
policy; one network gateway and no fog-bypassing sounds/particles/scoreboard data.
T10 routes ordinary AI commands through observed data and the normal validators.

## 3. Verification, risks and consent batch

Foundations: failing L1 tests first; `verifyFast` after edit batches, `verify` before
task commits. Codec behavior gets JSON round-trip and malformed-input L1 tests;
live NBT/save integration is T8 L2/L3. No new engine surface in T1–T6, no client
window, no hot kernel, tick, order, movement or existing persistence codec edit.
If scope touches one, add its slow/soak/JMH gates before committing. Existing
L0–L5 in verify still run; never infer success from a zero exit without reports.

Final levels: FOW-02 L0/L2/L3/L5/L6, FOW-03 L0/L1/L3/L4,
FOW-05 L0/L2/L3/docs, AI-07 L0/L1/L3, PERF-15 L6/report. Integration runs must
exercise mock players with both positive and negative controls. L4: two static
scenes each, vanilla/Sodium/Iris, mirages and shared explored shroud, PERF-14 fog
scene. Batch bounded client windows under AGENTS.md; inspect every changed PNG.
L7: two-client 2v2 questionnaire and no open P0/P1 before final acceptance.

Risks: directional alliance semantics, stale observation reuse, entity generation
reuse, omission of a vanilla channel, unbounded save data and changing M6 contracts.
Default to deny on unknown recipients/channels; keep historical memory immutable;
resolve adapters against merged code rather than copying unfinished branches.
Owner decisions: any roadmap expansion, public release/name, final acceptance,
baseline promotion or merge. No such decision is needed for the assigned contracts.

## 4. Task breakdown

| Task | Depends | Size | Delivery and verification |
|---|---|---|---|
| T1 | assignment merge | ~220 lines | This full plan, cards and branch record; roadmap/claims checks, verifyFast, verify |
| T2 | T1 | ~300 lines | TeamVision and fake-input/real ReplicationService L1 tests; verifyFast, verify |
| T3 | T1 | ~350 lines | BuildingMemory stable-ID codec, hidden-change/re-scout/copy/invalid-data tests; verifyFast, verify |
| T4 | T1 | ~350 lines | FogAudience, exhaustive decision tests and outbound inventory; verifyFast, existing L2 audience gate, verify |
| T5 | T2, T3 | ~300 lines | ScoutKnowledge, immutable observed input and known-start tests; verifyFast, verify |
| T6 | T2–T5 | ~180 lines | Review, KB, AFTER inventory, results and partial hand-in; roadmap/claims/diff checks, verify |
| T7 | T6, merged M6/M7 | ~900 lines | Real team/recipient adapters, targeted tracking and compat; L1/L2/L3/L5, verifySlow/Soak, JMH and L6 A |
| T8 | T7, current persistence schema | ~900 lines | Live memory/explored save/hash, migration and ghosts; L1/L2/L3, persistence fixtures, verify |
| T9 | T7, merged M5/M7 | ~1100 lines | Every channel adapter/backstop and packet controls; L2/L3/L5, verify |
| T10 | T8, AI-18 | ~900 lines | Integrated scouting/memory/difficulties; L1/L3, 200 seeded matches, verifySlow/Soak |
| T11 | T7–T10, PERF-10 | ~450 lines | 20-minute 4×150 + 2× stress; verifyPerf, JMH, costs report, unchanged A–E |
| T12 | T8–T11 | ~450 lines | L4 compatibility/goldens/PERF-14 fog scene, L7 questionnaire and final report |

Each future task must be reassessed against merged dependencies and split before
implementation if it exceeds 1,500 lines. It is not assigned by this document.

### 4.1 Progress

- [x] T1 Specification and scope matrix
- [ ] T2 Shared visibility policy
- [ ] T3 Last-seen building memory
- [ ] T4 Channel inventory and audience decisions
- [ ] T5 Observed AI/scouting knowledge
- [ ] T6 Foundation verification and handoff
- [ ] T7 Team fog and targeted tracking integration
- [ ] T8 Live memory, persistence and minimap integration
- [ ] T9 All-channel guards and mock-player integration
- [ ] T10 Integrated AI scouting and balance
- [ ] T11 Multiplayer fog performance
- [ ] T12 Client compatibility and owner acceptance

## 5. Results

T1 completed on 2026-10-28: full plan, all five cards mapped to final acceptance,
assigned T1–T6 and future unassigned T7–T12 separated, and all five card statuses
set to specified. No production or test source changed. Owner requested stopping
after T1 and resuming with T2 the next day; this is not a foundation hand-in.

Validation: verifyFast 2m 49s; roadmap 245 cards/0 problems; claims 7/0 problems;
full serial verify 6m 8s, 1,310 core + 95 mod + 212 GameTests, zero failures,
errors or skips; smoke, restart and both arena markers all ok. The initial default
parallel verify failed the existing ProtectedBlockIndexTest timing ratio (4.977
against <3). Its report was preserved; one isolated diagnostic passed in 18s,
and full serial verification passed with unchanged tests and thresholds. This
shows measurement variability, not a proven root cause or a repaired test.

The serial verify exceeds the documented five-minute target; the branch file
records this for owner review. No check was moved, skipped or weakened. T1–T6
completion will mean foundations delivered only. Full M8 cards cannot be done
until their remaining criteria above pass. Record APIs and every AFTER marker
at the future hand-in.
