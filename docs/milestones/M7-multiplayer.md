# M7 — Multiplayer: implementation specification

Planning delivery, 2026-10-28; implementation readiness reconciled 2026-10-29 against `main` at `267da40` (M5 delivered by PR #33).
Inputs: [M7 cards](../roadmap/M7-multiplayer.md), [architecture](../04-architecture.md),
[M6 specification](M6-first-skirmish.md), ADRs 0008–0010, 0014, 0016–0020, 0022–0023.
Status: **independent foundations in progress**. On 2026-10-29 the operator requested the whole M7 and explicitly instructed development before assignment approval/merge. Claim PR #34 remains pending independent review. This exception permits available foundations; missing runtime dependencies and explicit product/acceptance decisions remain gated.

## 0. Authority and implementation rules

- The roadmap governs scope and scheduling; accepted ADRs govern architecture. Proposed M6
  contracts describe future inputs, not code already delivered on main.
- Rules, faction relations, grants and victory belong to `core`; connection UUIDs, Minecraft
  players, transport and GUI belong to `mod`. No Minecraft imports enter core.
- Core decisions use stable iteration order and simulation ticks. Hash/save all authoritative
  relations and deadlines that affect simulation; derive connection caches and encoded frames.
- Add failing tests before implementation. Each task requires `./gradlew verify`; run
  `verifyFast` after each edit batch. Tick, orders, combat and AI changes also require
  `verifySlow verifySoak`; hot-path changes require JMH and comparison against approved baselines.
- Hook names below were checked in cached Minecraft 1.21.1 + NeoForge 21.1.264 sources on
  2026-10-28. Recheck signatures and thread behavior when implementing. No new runtime library,
  platform port, public release, baseline increase or owner decision is authorized here.
- Future file/class names are design proposals. Reconcile with merged predecessor code before
  introducing them; extend existing seams rather than implementing competing subsystems.

## 1. Goal, scope and demo

### 1.1 Goal

Two to four players use a dedicated server for competitive or cooperative matches, with teams,
shared control, private recipient state, reconnects and bounded network traffic. A modified
client cannot use commander spectator powers to acquire hidden information or leave the arena.

### 1.2 Boundaries and differences from cards

| Item | Treatment | Reason / unchanged acceptance |
|---|---|---|
| Main M7 runtime | All eleven runtime/test/performance cards are covered below | No card criterion removed |
| QA-14 platform decision | Floating T24, not an M7 Done prerequisite | Card explicitly excludes it from every Done gate; due at MVP exit or 2027-Q2, whichever is first |
| ART-03 | Parallel T22–T23 after MVP fun verdict, QA-13 and theme decisions | Still required for the stated M7 visual acceptance; do not hide it behind runtime completion |
| Team vision | M7 publishes relations and recipient identity; M8 implements FOW-02 shared vision and targeted re-tracking | NET-03 explicitly excludes vision; existing solo visibility remains the privacy floor |
| Lobby harness bootstrap | T3b uses minimal existing QA-19 mock helpers; T6 extends the reusable kit | Avoid a NET-02 ↔ QA-15 development cycle; formal QA-15 still depends on NET-02 |
| Fuzz decoding | Expected bounded rejection of invalid bytes is captured as a decode result; no exception escapes the ingress boundary | NET-06's 100,000-input criterion is not an obligation to accept malformed payloads |
| Hosting | D-20 recommendation described, not approved | Dedicated first, LAN supported and one match per server remain pending owner confirmation |
| Co-op first-person wording | Preserve D-08's existing observer avatar; no fighting/scouting avatar is introduced | The card's demo/value prose mentions fighting/scouting, but CMB-08 remains deferred; owner confirmation is needed before adding either ability |
| Card lifecycle | MATCH-06 enters `in_progress` for bounded foundations on the operator's 2026-10-29 instruction; other cards remain proposed until their work is ready | No integration or acceptance criterion is declared complete |

No feature is moved out of its card. Neutral diplomacy/tribute, new players or spectators joining
an active match, matchmaking, voice chat, the second civilisation, terrain secrecy (FOW-06),
and a production platform port remain outside M7.

### 1.3 Owner demo (L7)

Use QA-09's protocol on a dedicated server with two real clients; host/operator starts the
match from the lobby. Play two humans plus two AIs, teams 2 v 2, to allied victory. Use allied
pings, check colours and private faction panels, disconnect one client mid-fight, and reconnect
to the same faction with camera/groups restored. In a separate co-op setup, both humans control
one faction, one in commander mode and one in the existing first-person observer mode; verify
both players' command authority without adding an avatar attack or vision source. Record outcomes, questionnaire, P0/P1
issues, JFR counters for S11, and ART-03 silhouette/look approval. People start interactive clients.

### 1.4 Implementation findings

Current acceptance, 2026-10-30: T2b.1/T2b.2 are complete on delivered main 8111c0a. Verify 41051799948 passed 2,309 cases; core slow/soak 41050854156 passed 216; matched server slow 41048580649 passed all 19 M7 cases at 7m 55s under the original ten-minute bound. Standard L6 41048400982 passed all 22 scenes in 59m 51s under the accepted validity policy. The shared Linux JMH-profiler allocation defect was repaired without changing any allowance, with 27-config identical-probe comparison 41051803906 passing; tooling L4 smoke 41054233370 also passed with inspected frames. Earlier failed/interrupted evidence below is historical and preserved. This is foundation acceptance, not native PERF-10 or whole-M7 acceptance.

2026-10-30 acceptance update: authority installation passes cloud verify (2,303 cases) and pinned same-host JMH (27 matching configurations, unchanged 15%/allocation gate). Server slow reached the unchanged ten-minute timeout in three attempts, including one with core slow/soak completed before server start. The contention hypothesis is insufficient; no runtime regression or relaxed deadline is inferred. Both T2b steps remain open, further slow attempts stop under the developer procedure, and a resumed investigation requires matched server/harness attribution. Existing failed evidence is retained in the branch log.

Final standard cloud L6 run 40972197958 passed in 1h 1m 55s on 33ecb12: all 22 required server scenes and 27 JMH configurations completed; 19 passing and two existing report-only scene reports, no invalid report. This completes the standard cloud performance evidence, while the slow failure still prevents accepting either T2b step. No native reference or final multiplayer PERF-10 acceptance is implied.

2026-10-29 resume: composition verify/slow/soak pass, but its L6 Actions job hit the 55-minute aggregate deadline at 16/22 scenes. The job resource limit is 90 minutes; all measurement parameters/gates are unchanged. T2b.2 proceeds against the functionally verified contract while both steps retain pending acceptance until the corrected full combined L6 succeeds. This replaces the earlier strictly serial gate scheduling, not the required evidence.

| ID | Finding / decision | Scope and follow-up |
|---|---|---|
| D-01 | M5 delivery is already on the implementation base (PR #33); M6 runtime is not | Replace the stale planning assumption at each concrete adapter, not by importing another developer's unfinished branch |
| D-02 | T2 mixes independent rules with live authority/persistence changes | Split T2a pure contract from T2b.2 installation; no numeric authority check is replaced by the standalone foundation |
| D-03 | T2a state itself implements the immutable view; every update returns a new state | Fixed slot arrays stay private, snapshots copy lists, and hash/reconstruction use canonical slot order; T2b.2 owns match snapshot versioning and journal installation |
| D-04 | Explicit grants belong to the granting faction, but can be issued only from a player's primary faction | Reject grant chains and absent/enemy participants; rebind drops incoming grants, removing the last primary controller drops outgoing grants, and team changes revoke incompatible grants. Disconnect alone never unbinds a seat |

## 2. Baseline and dependencies

### 2.1 Inspected baseline

`MatchRoster` currently maps UUID to unique `PlayerSlot`; `of(FactionSlot)` relies on equal
numeric slots. `Ownership.check` compares issuer number directly with unit faction number.
`VictoryCheck` ends when at most one faction remains. `NetGateway.commands` obtains the issuer
from `MatchRegistry.seatOf(UUID)`, and refuses observer commands. Preserve those security
boundaries while removing the player/faction equality assumption. `Audience` currently offers
PLAYER, FACTION, TRACKERS_OF, SEES_CELL and MATCH; `ReplicationService` already accepts a viewer
faction and `Visibility`. QA-19 provides `MockViewer`, `PacketCapture` and `ByteCounter`.

### 2.2 Prerequisite matrix

| Source | Required interface / behavior | Planning now | Runtime release boundary |
|---|---|---|---|
| M0–M4 main | CORE-01/03/07, NET-01/08, QA-19, PERF-01 | Inspect existing classes and tests | Reconcile changes merged before T1 |
| M5 | Allied damage filtering, combat/projectile output and close-out findings | Specify relations as a core query | AFTER-M5: real combat adapters and security/perf scenes cannot pass before combat lands |
| M6 MATCH-03 | Defeat and victory outcome | Extend its documented result model to allied winners | AFTER-M6: integrate with its final outcome/snapshot schema |
| M6 MATCH-02 | Validated setup, map preparation and start pipeline | Reuse `SkirmishSetup` contract rather than a second launcher | AFTER-M6: production lobby start adapter |
| M6 UI-20 | Minimap input/pings and construction view | Define team audience and rate rules | AFTER-M6: UI-15 rendering/input integration |
| M6 AI-05 / AI-18 | Difficulty, wave scheduling and observed-only AI inputs | Specify team query contract with pure fakes | AFTER-M6: production AI-06 routing and difficulty acceptance |
| M6 solo fog | FOW-01/FOW-07 `VisibilityIndex` and `FogGate` | Consume viewer visibility, never infer it from chunks | AFTER-M6: NET-09 hidden-body controls |
| M6 QA-13 / PERF-09 | MVP verdict and validated performance reference | Plan ART-03 and PERF-10 | AFTER-M6: artwork start and final multiplayer measurements |
| M8 foundations / later FOW-02 | Team visibility policy and recipient contracts | Reconcile public contract shape; no dependency on an unmerged implementation | AFTER-M8: shared vision is accepted in M8, not certified by M7 |
| Owner D-20 / D-03 / D-04 | Hosting / theme / style direction | List options and conditional tasks | T3b/T10 hosting and T22/T23 art retain their decision gates |

Planning is independent. When M7 opens, claim only implementation tasks whose prerequisites
are ready, or an explicitly bounded contract-only scope. Do not claim blocked adapters as ready.

### 2.3 Implementation readiness, 2026-10-29

Resume update: main b893a0d uses branch-file claims and includes the M7 planning delivery. Corrected PR #30 at 588fa57 is explicitly stacked for T2b.1; earlier unresolved-code findings below are historical, and combined gates must be checked before acceptance. Its save-identity ADR is numbered 0027 in this composition because main now owns ADR-0026 for perf validity. Schema 4 is the next diplomacy writer format; existing schema-1/2/3 fixtures remain append-only and migrate to explicit identity bindings.

- Operator exception: proceed before claim PR #34 approval/merge. No self-approval or merge is authorized.
- M5 is delivered on main by PR #33; use its current damage/projectile paths at T7, not the old planning assumption that M5 is missing.
- M6 runtime remains unmerged. Its setup/outcome, observed AI, minimap, solo-visibility and perf contracts are not production implementations. T3b/T7/T13/T14/T15/T20 retain integration gates.
- Main was synchronized to dcb5013 on 2026-10-29. PR #30 at c9dd148 still has an unresolved M5 snapshot conflict and a maintainer-reported legacy-index compatibility risk: its legacy resolver uses the expanded current built-in table. T2b.2/T5 need that policy corrected and verified before a versioned diplomacy installation, whether the prerequisite is merged or explicitly stacked.
- PR #30 persistence fixes and planning PR #32 remain open. Import only this reviewed planning document from our own planning branch; do not import its registry row or branch status. T5 coordinates schema numbers after persistence delivery.
- T2 is split before code into T2a bounded pure state/view/snapshot foundations and T2b.2 installation. T2a does not replace numeric authority checks, modify running matches or claim co-op/privacy acceptance. T2b.2 must land together with the appropriate persistence/journal integration so authority is never lost on restore.
- D-20 hosting, D-03 art, the MVP fun verdict and deferred fighting/scouting remain unresolved. They do not prevent T2a.
- No new engine call is needed by T2a. Recheck the planning hook signatures against the then-current engine artifacts when T13 becomes ready; no current hardening implementation is certified here.
- Cloud verification requested by the operator: the initial local baseline `verifyFast` was interrupted and is not a pass. Dispatch existing verify workflow on pushed WIP checkpoints, inspect canonical XML/reports, then record a green task commit. T2a adds failing L1 tests first and must pass V before completion.

T1 verification: [cloud verify 40787146879](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40787146879), commit `40e770d`: BUILD SUCCESSFUL in 6m 46s; 2,237 canonical XML test cases, zero failures/errors/skips. Expected negative harness failures were caught by their self-test gates.

### 2.4 Resume after M6 delivery, 2026-11-15

Main d39069e7 includes M6 through PR #55. Merge checkpoint 213d0564 passed full cloud
verify 43756194393 (7m 22s; 2,623 canonical XML cases, no failures/errors/skips). Its snapshot
composition retains research, match statistics and vision together with schema-4 diplomacy;
`withDiplomacy` must preserve every M6 field. This resolves the snapshot-composition marker.

The earlier M6-unmerged statements in §2.3 describe the historical checkpoint. MATCH-02/03,
UI-20, observed AI and solo fog are now available for actual integration at T3b/T7/T13/T14/T15/T20.
They do not themselves certify multiplayer behavior. M6's owner playtest (QA-13), civilisation/style
decisions and final M7 performance/real-client evidence retain their own acceptance boundaries.
The existing cloud workflow route remains mandatory for tests in this session.

## 3. Design

### 3.1 Identity, teams and control

T6 readiness assessment (2026-11-18): T3b/NET-02 are delivered on this branch and QA-19
is delivered on main (M1 T14 with M2 JFR reconciliation). The reusable engine kit is ready;
QA-15's L7 protocol/use criterion remains T19/T25, not part of an engine-only completion.
Split before implementation into **T6.1** bounded owned player groups, explicit admission,
automatic acknowledgement/polling, per-player payload/byte views and idempotent cleanup;
and **T6.2** bounded real-ingress command/hostile-packet helpers plus a four-player smoke
under the unchanged 30-second limit. A fixture owns its runtime and disposes it when closed;
it must not remove another test's players or call global cleanup. Keep the existing QA-19
encoder/capture as the single byte-counting implementation. T6 stays unchecked until its
cloud engine gate passes. No new owner product decision is needed for this approved scope.

T8 readiness/split (2026-11-18): T2b, T5 and T6 engine dependencies are delivered.
Split this large task before code into **T8.1** existing core-command asset ownership,
primary-faction placement/production/economy authorization and tick-time revoke revalidation;
**T8.2** bounded journaled grant/revoke commands and their replay/primary-only delegation policy;
**T8.3** is split before code: **T8.3a** server primary/control routing, same-faction
private replication/fan-out and first-person visibility; **T8.3b** bounded authenticated
primary/control views, client selection/orders/HUD perspective and grant/revoke updates;
**T8.3c** actual co-op lobby admission and integrated transport acceptance. Keep production
co-op admission refused until all three are ready; **T8.4** durable faction presentation/palette and human-name-only
scoreboard integration for MATCH-06; and **T8.5** integrated two-controller/recipient/visual
acceptance. QA-15's L7 criterion remains T19/T25. UI-15's broader HUD/ping work remains T14.

Audit each numeric seat use by domain: command sequence keys and first-person leader indices
remain player identities; ownership/economy/fog use the primary faction and explicit grants.
Placement creates assets for the primary faction, as the existing intent defines; grants
permit operating another faction's assets without revealing its treasury/research/queues or
transferring ownership. Trusted server ADMIN behavior must remain explicit; no client may
supply that privilege. Parent T8 stays unchecked until every integration gate passes.

T8.3b authority protocol (2026-11-19): protocol 4 extends the initial `MatchJoin`
with a recipient-specific `PlayerAuthority`, also sent as `player_authority` to that
player when permissions change. It contains match/player identity, a monotonic core
tick, the primary faction, and bounded 16-bit control/allied masks. Only an explicit
observer has no primary and zero command rights. Updates cannot change the primary
binding or context, and equal/older ticks cannot restore revoked permissions.
The record's maximum is 22 bytes; the joined record remains under the existing 4 KiB
cap with exact maximum-size coverage. Its immutable source avoids per-tick mask
rebuilding. Control affects selection and permitted orders; private stock, queues,
research, camera fog and result perspective remain primary-only. Delegated-building
cards issue requests without guessing their owner's private availability, use generic
next-item cancellation and explicit reseed enable/disable, and retain primary-owned
cards unchanged. Public participant names/presentation and the usable grant controls
remain the following presentation/integration work; production co-op admission stays
closed until T8.3c.

T8.4 readiness/split (2026-11-19): **T8.4a** owns a bounded immutable
core faction-presentation registry (civilisation and selected colour), trusted journaled
first-tick installation, versioned save migration and deterministic restoration. Released
matches without explicit presentation retain their existing appearance and hash.
**T8.4b** projects public faction presentation and authenticated participant names separately
from the bounded initial authority frame, applies one palette to client faction markers,
and colours human names through scoreboard teams with no unit membership.
**T8.4c** exposes explicit grant/revoke actions for eligible named allies through the normal
command producer; no foreign private queue, treasury or outgoing grant list is exported.
The broader player-list/ping HUD remains T14. These substeps retain the parent T8 gates;
no civilisation art or product theme is selected by this implementation.

T8.4b transport boundary (2026-11-19): publish immutable public faction styles and
bounded authenticated human names/connection status in a separate recipient-context
`MatchPresentation` payload, preserving the existing 4 KiB `MatchJoin` cap. No UUID,
private economy or grant list belongs in it. Install the lobby's choices before initial
admission; restore from the core registry. Use one selected palette for client faction
markers, outlines and equipment. Released legacy appearance remains unchanged.
Human-name scoreboard teams use an isolated board and recipient-only vanilla packets;
never attach Strategos units or change the server's global scoreboard. On context removal,
remove owned client teams and restore existing vanilla name membership without enumerating
foreign team members. Verify outsider negatives and actual packet name membership.

T8.5 readiness/split (2026-11-19): **T8.5a** adds an actual four-peer command
integration with P0/P1 sharing F0, including camera training/move, first-person follow
whose leader remains player 1, same-owner cancellation/refund and separate feedback
sequences. **T8.5b** supplies inspected cloud L4 co-op/palette/sharing-panel frames and
refreshes changed lobby frames without changing image thresholds. **T8.5c** runs the
parent's full slow/soak and applicable hot-path comparison gates before T8 is checked.
Visual DTO fixtures must identify their limited presentation purpose; actual transport
acceptance remains the engine tests, and two-client human acceptance remains T19/T25.

T8 acceptance checkpoint (2026-11-19): functional verify 44614567652 passes
2,793 canonical cases; full-ci 44606455194 passes all 23 jobs, including
249 slow/core cases and twenty AI match/replay seeds (896,421 ticks).
Co-op visual repeat 44613485447 passes the normal reference comparisons
and rejects every altered reference. Personally inspected refreshed lobby
references pass repeat 44616116638 (seven normal NMSE 0, all altered references rejected). The original paired compiler timing
breach and its fixed A/A/bytecode-identical ABBA attribution evidence are
retained in the branch log; the complete performance comparison remains open.
Final T8 acceptance (2026-11-19): complete matched JMH 44616013773 passes
all 30 configurations with unchanged 15% and allocation gates; maximum ratio
1.03549 and compiler ratio 1.02190. Both archived result hashes match the
strict report guard. The original compiler breach remains a budget breach
whose transient cause is unconfirmed; fixed same-binary A/A, bytecode-identical
ABBA and the predeclared reversed-order full comparison do not reproduce it.
No production optimization, reference change or relaxed check was used.
Together these gates complete T8 and MATCH-06; human L7 remains T19/T25.

T7 implementation split (2026-11-18): **T7.1** owns final hostile-damage filtering,
frozen source-faction provenance for delayed/collateral hits, its current/legacy snapshot
and journal policy, and actual engine damage adapters. **T7.2** owns allied coalition
victory, deterministic winner sets/draws and terminal outcome persistence/replication.
The complete parent retains all combat, replay, save/restore, slow/soak and applicable
performance gates. A live-handle lookup at impact cannot identify a dead/reused shooter;
the source provenance finding is recorded in `docs/kb/frozen-damage-authority.md`.
ADR-0030 specifies schema-5 frozen origins and append-only attributed input tag 10. M7
locked-team combat receives final allied filtering; released unlocked combat retains its
old semantics/hashes. A locked legacy shot is migrated only from a matching saved live
source generation, never guessed after death. Current snapshots require explicit provenance.

Extend `core/sim` with `diplomacy/DiplomacyState` and an immutable `DiplomacyView` seam: relation
(ally/enemy), locked team membership, per-player primary faction and explicit control grants.
`PlayerSlot` identifies an issuer; `FactionSlot` identifies an economy/army. They are distinct
domains even if legacy saves used equal values. Stable UUID-to-player-slot binding stays in
`mod/server/match/MatchRoster`; the core stores slots, never connection objects.

Two humans share a faction by binding distinct player slots to that faction. An allied control
grant additionally permits commands over the granting faction's assets; it does not transfer
ownership, reveal treasury/research/queues or bypass fog. No transitive grant: A granting B
does not give C control through B. Reject grants to enemies or absent participants. Locked
teams are selected before start; changing a ready-relevant setting clears readiness. Runtime
neutral relations and tribute remain MATCH-11.

Replace numeric equality checks, including current `PlayerSlot.controls`, across command ownership, production, placement, UI queries,
first-person possession and replication with the same control/identity query. Costs debit the
controlled asset's faction, once, never the issuer's unrelated treasury. Revalidate at tick
application so a queued command cannot retain revoked authority. Hidden and missing targets
retain identical `UNKNOWN_TARGET` behavior. Observer/admin transport privilege is never
inferred from a client-supplied slot.

Use relation filtering at the final authoritative damage application, not just AI targeting:
melee, projectiles, splash and delayed damage all consult current faction relations. Extend
`VictoryCheck` and the M6 outcome record to the surviving allied coalition, with deterministic
winner faction sets and an explicit no-winner result. End only after defeat evaluation and
only once; preserve ended state through restore. Scoreboard teams colour human names only;
units and hidden army membership are never added to vanilla scoreboard teams.

### 3.2 Persistence and restoration

T5 implementation split (2026-11-18): **T5.1** validates persisted roster structure,
UUID uniqueness, authority bindings and cross-match restore conflicts before publishing
membership; **T5.2** versions the adapter session/deadline envelope, migrates valid legacy
rosters and restores connections as disconnected with explicit remaining monotonic duration.
The parent keeps every original persistence and engine acceptance gate. Core schema-4
authority is already delivered by T2b.2 and must not receive a second representation.
Adapter schema 2 is specified in ADR-0029: explicit disconnected-session durations and a
required launch transaction flag. Interrupted production preparation is refused on restore,
including after authority is installed but before initial admission completes; completed
launches preserve the roster, primary bindings and grants.

Implementation boundary: ADR-0028 defines schema 4 over the stacked schema-3 template policy. `Play` retains an immutable `DiplomacyState` value; the encoder derives canonical fields and the decoder validates them once. The versioned decoder requires authority in a current save. Migration 3→4 supplies identity from config.factions, while older migrations and released hashes remain intact. Root compatibility constructors normalize older typed Play values to identity. The trusted first-tick setup has bounded journal tag 9; command/roster/audience integration stays in T8/T5/T9. A source AFTER-M6 marker requires preserving authority when M6 adds outcome/statistics fields.

Version the core snapshot for player/faction bindings, locked teams and grants. Version the
adapter roster/session envelope for UUID seats and reconnect deadlines. Migrate valid older
one-player/one-faction saves to identity bindings; do not reinterpret incompatible slot data.
Add old-schema fixtures, new-schema round trips, duplicate/cross-match UUID rejection, and
malformed faction/grant tests. Restore connections as disconnected until the authenticated
player returns. Rebuild audience caches from authority; never save a transport queue or trust
a previously encoded private frame. Coordinate with the separate save/reload repair delivery
before choosing the next schema number.

### 3.3 Lobby and start

Implementation split (2026-11-16): T3b remains the parent acceptance task. Keep its three
bounded steps separate: **T3b.1** authenticated service, content preflight and transactional
M6 preparation/backend; **T3b.2** bounded lobby intents/private recipient views and production
network/tick/shutdown adapters; **T3b.3** four-player engine acceptance, rollback/privacy and
start deadline. Each step receives its relevant gates; completing a step does not check T3b
or reduce NET-02's four-player acceptance. T4 still depends on the complete parent.


Add `mod/server/match/MatchLobby` with bounded slot records (human, AI, closed), faction,
team, civilisation, colour, mode and ready state. `LobbyService` validates connection membership
and host/operator authority; clients submit intents, not authoritative roster snapshots.
Use the M6 setup/start pipeline for map preparation and match creation. Atomic start validates
unique seats, map capacity, supported configuration and readiness, then freezes the roster;
preparation failure returns a visible error without leaving an active partial match.

Phasing refinement (2026-10-30): T3a is the independent immutable lobby aggregate: bounded
human/AI/closed seats, UUID occupants outside core, faction-consistent team/civilisation/colour,
readiness reset on configuration changes, and editing/preparation/rollback transitions. It creates
no match, admits no network sender and chooses no hosting topology. Its readiness predicate is
only a lobby prerequisite, not complete start validation. T3b supplies authenticated service,
payloads, map/setup validation and the real M6 preparation/start pipeline after its dependencies
land. Observer/combat modes retain the existing product gates; T3a adds no such mode selector.
Ready intents name a configuration revision: actual settings changes and preparation rollback
invalidate it, while another member's ready toggle does not. A separate state revision tracks
all updates. Thus a queued acknowledgement cannot approve new settings, and peers may confirm
the same configuration without retrying merely because another peer became ready first.

Service step (2026-11-15): `LobbyService` authenticates connection UUIDs and owns the
single mutable reference to the immutable lobby/setup pair. Its backend supplies host permission,
connection/match availability, supported map/setup validation, transactional preparation and private
publication. Configuration edits preserve occupants; only a member's join/leave changes its UUID
binding. Validation runs before allocation. Preparation success freezes settings through admission;
failed preparation disposes partial runtime state before resetting acknowledgements. If disposal
fails, the service stays frozen in `FAILED`, rather than announcing a safe retry. Failure messages
are bounded reason codes; backend exception details remain in server logs.

This service step introduces no payload, production launcher or simulation-tick hook. Production
integration must prevalidate `ScenarioSetup.commands` against the actual map starts and templates,
use the existing map import/runtime pipeline, install locked `DiplomacySetup` before the first
simulation tick, seat authenticated members and enter commanders, and prove initial recipient
state plus cancellation with real mock players. Co-op player slots must be checked through
bindings rather than compared with the economy-faction count; ordinary legacy slot admission
must remain covered. T3b stays unchecked until these backend/protocol/engine checks pass.

Runtime authority step (2026-11-15): `MatchRuntime.initializeDiplomacy` admits one locked setup
only before tick 1 on an unseated runtime with matching faction capacity. Its normal tick installs
and journals the setup before commands. The registry refuses admission until it is installed,
then checks the player's primary binding rather than comparing player and faction counts.
The existing administrator behavior and rejection of unbound legacy slots remain. The four
engine regressions also verify replay to the live hash. This transient queue requires cancellation
on server stop by the future production backend; T5 must prove any durable launch transaction.


Production acceptance (2026-11-18): the real server owns `ServerLobby`, authenticated intent
translation, bounded/private payloads, tick polling and cancellation before normal persistence.
The four-human/four-faction actual-terrain case passes the unchanged ten-second deadline
(4.710 s for the complete cloud case); workers-only starts receive initial empty building and
deposit snapshots exactly once. Metadata inspection avoids full tile reads while actual creation
still validates the full package. Verify 44148921127 passed 2,669 cases; full-ci 44148929120
passed all 248 slow/core-soak cases, every original AI seed 1–20 and the exact-commit aggregate
on e382f7b6 (896,421 ticks, 17 conquests and three full-limit runs). These gates complete T3b;
T4 still supplies client presentation/reset, T5 durable roster/launch restore, and T8 co-op routing.

Add bounded `LobbyActionPayload` and `LobbyStatePayload` under `server/net/payload`, registered
through `ModPayloads` and `PayloadSpec`. Only admitted lobby members get lobby state. Active-match
new-player join requests are refused; an existing UUID reclaim uses the reconnect path. No
spectator join flow is added. Every admitted player gets `match_join` and the initial recipient
snapshot within 10 seconds; the default commander state is entered through the existing
`CommanderService`. Add `client/menu/MultiplayerLobbyScreen` over immutable view-models.

T4 implementation split (2026-11-18): keep **T4.1** detached presentation/session contracts
and the small private join/refusal adapters separate from **T4.2** the screen, entry point and
inspected L4 goldens. The complete T4 remains NET-02's UI acceptance task. A joining client
receives no roster before admission: a bounded `JoinAvailable` intent lets the server choose
a vacant human seat. A private bounded refusal reply contains no roster or exception text and
lets the screen render rejected requests. Idempotent cancellation removes only the caller's
editing/preparing membership when a screen closes before admission is acknowledged; late
frames cannot reopen it. It never cancels another player's lobby or leaves a started match.
These refine UI transport, not lobby discovery or
server authority. Neither substep checks the parent before the screen/goldens pass.

Hosting (D-20, explicitly accepted by the owner in this M7 session, 2026-10-30): one active
match per server, dedicated first-class, Open to LAN through the same service. Dedicated tests
are authoritative; multi-match discovery is outside M7. T3b still depends on the delivered M6
start pipeline; T3a does not create or start matches.

### 3.4 Recipient rules (NET-03)

Extend `ReplicationService` and `NetGateway` to resolve recipients against one current
`DiplomacyView` and roster epoch. A frame is projected for its recipient before encoding;
denied private fields are absent, not client-hidden. Keep the existing audience kinds where
sufficient; team fan-out resolves to explicit player/faction sends rather than match broadcast.
Extend `PayloadAudienceTest`, inventory and B6 checks for every new payload.

| Data | Same faction, including co-op | Different allied faction | Opponent / non-member |
|---|---|---|---|
| Own treasury, research, production queues | Full | No private fields, even with control grant | None |
| Public lobby/team/colour/status | Admitted participants | Admitted participants | No match state for non-members |
| Pings and team markers | Yes | Yes, same current team | No |
| Unit/building details and summaries | Own full | Only fields permitted by existing visibility/public-data policy | Visible public projection only |
| Vision/explored state | Own faction's M6 view | No new vision sharing in M7; FOW-02 adds it | No private bitmap |
| Command feedback, camera/groups | Issuing UUID only | None | None |
| AI plans, hidden targets, internal grants | No AI plan export; only necessary own control status | No private plan/state export | None |

Existing observer access remains an explicit server-authorized role and is tested separately;
being a spectator entity does not confer observer authority. No Strategos attachment type syncs
to clients. Scan full and delta payloads, query responses, reconnect snapshots and deferred
frames; dropping a match-wide private broadcast is insufficient if another path leaks it.

T9 readiness/split (2026-11-19): T8, T6 and the existing NET-08 transport
are delivered. Before code, split **T9.1** into recipient-context contracts and
current per-player connection/seat/primary generations for queued frames;
**T9.2** into callback/query/subscription context and full rejoin reset; and
**T9.3** into the actual multi-recipient full/delta/query/observer matrix,
attachment inventory and acceptance. A global roster revision must not discard
the first participant's valid initial frames when later participants join.
Capture origin context when producing a private response and revalidate before
sending; looking up only a UUID at flush or callback time is insufficient.
Use existing primary-private projections and existing visibility policy;
no ally vision, new observer role or T16 bandwidth policy is introduced.
Contracts run cloud-first, then actual engine recipient tests and full verify.
Parent T9 remains unchecked until every path and its required gates pass.
T9.2a passes verify 44627171948 (2,799 cases). T9.2b/c pass
verify 44629974163 (2,801 cases) and inspected client scenario 44629985316,
including same-match private-view reset and unchanged image/negative gates.
T9.3 recipient matrix and parent acceptance pass verify 44746013321 on e501bf96 (2,809 canonical cases, zero failures/errors/skips); full-ci 44641218952 on c3e653f3 passes all 23 jobs, including 249 canonical slow/core cases.
T9.2 refinement before further code (2026-11-20): **T9.2a** binds delayed
command replies to their submission origin; **T9.2b** resets replication and
query memories on recipient-generation/full-join boundaries and restores a
complete initial vision frame; **T9.2c** resets client private views at every
full join, including the same match id. Use separate red/green evidence for
the callback and cache boundaries. Broader reconnect deadlines/camera/groups
remain T10; no client-supplied session token is introduced.

T9.3 structural contracts inventory live Strategos attachment registrations and
reject every attachment-builder client-sync call in shipped bytecode. A local-only
fixture must remain allowed and a sync fixture must be rejected; an unlisted registry
entry must fail the inventory. The actual recipient/observer/reconnect matrix follows
these checks and reuses existing co-op privacy evidence rather than duplicating it.

T9.1 acceptance: verify 44624637029 on 5d969b64 passes 2,798 canonical
cases, including four generation contracts and actual two-peer stale-queue
rejection with current/unrelated positive deliveries. T9.2 and T9.3 are accepted by the subsequent evidence above.

T9 closure (2026-11-20): the actual matrix covers per-recipient queued and
delayed output, same-match full join, paused initial snapshots, cross-match
reused handles, replacement connections, unbound spectators versus explicit
observers, co-op/granted/enemy visibility and individually projected world/shot
FX. Attachment registrations and attachment-builder sync calls are structurally
checked. Client same-match private-view reset retains inspected scenario
44629985316 and its unchanged positive/altered-reference gates. Existing M6
primary visibility is the floor; M8 shared vision remains separate. NET-03 stays
in_progress for T14's existing ping/team-marker criterion; no criterion is removed.

### 3.5 Reconnect (NET-05)

Add `server/match/ReconnectService`: authenticated UUID reclaims its reserved seat, same faction
and grants. Disconnect revokes connection/session tokens and new commands, preserves the core
orders, then units idle when their queues finish. Use a configurable server timeout; recommend
300 seconds as the card's five-minute reclaim window. Keep live timeout accounting on a
monotonic server clock even when the match is paused; serialize remaining duration at save and
restore it explicitly, without depending on wall-clock jumps. Timeout policy is a validated
configuration enum (defeat or AI takeover), applied once. Reconnect at/after expiry cannot undo it.

Handshake sends templates, match identity, role/control status and a full current recipient
snapshot; do not replay old delta queues. Reject concurrent/stale sessions and packets from
another match epoch. Camera pose and control groups are client-owned per-match state: restore
from the client's cached model after UUID/match confirmation, validate group handles, and clamp
camera through the existing server path. Client cache never supplies faction ownership. Test
rejoin before, at and after expiry, paused matches and save/restart.

### 3.6 Ingress hardening (NET-06)

T12.1 passes full verify 44789454855 on 72f894a7: all 2,964 canonical
cases pass, including 100,008 actually generated probes across the nine real
registered C2S codecs and three committed seeds, 27 permanent malformed vectors
and additional accepted/byte-boundary controls. The original decoder inventory
and byte-exact positives remain intact. T12.2 state/journal/subset consistency
gates and parent T12/NET-06 remain open.

T11 is complete (2026-11-20): full verify 44786479783 on f7103b2d
passes all 2,927 canonical cases, zero failures/errors/skips, including L5.
The final subscription fixture preserves accepted own-unit detail/debug output
after both bursts and requires new frames after refill plus actual owner/peer
commands; only the existing debug-rate policy charges protocol violations.
Unchanged camera/mode cooldown cases, nine malformed codecs, finite-field,
sequence, current-transport and exact 100/101 controls all remain green.
Paired core L6 44774432743 covers the identical production core tree: all
30 configurations/28 methods pass the unchanged 15 percent/allocation gates.
T12 and the NET-06 card remain open; this does not accept L7 or whole M7.

T12 refinement before code (2026-11-20): **T12.1** depends only on the accepted
T11.2 decoder foundation, independently of the final subscription-rate fixture. It
shares the existing nine-type
fixture catalog with a permanent append-only corpus and committed seed set;
execute at least 100,000 generated inputs in each L2 build, with bounded bytes,
complete input consumption, safe refusal and accepted round-trip controls.
Persist an exact crashing input in the cloud reports before adding its permanent
regression; do not swallow unexpected exceptions or retry for a passing seed.
**T12.2** depends on completed T11 and feeds a bounded representative corpus
through actual handlers with
accepted owner/peer commands and state/journal controls. Keep its L3 resource
subset byte-identical to the canonical corpus under mod/src/test/resources
through an L2 consistency assertion, using the existing source-set classpaths.
This planning does not accept the corpus or close T11/NET-06.

T11.3b typed-reason and time-control authorization passes full verify
44784613770 on 855f83b8 (2,926 canonical cases, zero issues; L5 included).
The two intended engine failures in 44783227554 are resolved. Three L2
reason/counter contracts, nine private-owner/publication/lifecycle policy cases
and actual dedicated control refusals preserve the established kick budget and
accepted two-peer commands. The final per-type subscription contract remains
pending; parent T11 and the T12 permanent corpus are still open.

T11.3a auxiliary admission passes verify 44780066073 on 38faac03
(2,913 canonical cases, zero failures/errors/skips). Two capped auxiliary
bursts retain actual template resends and accepted commands for both peers;
100 retained stale contexts cannot consume the replacement connection's kick
budget. Stable semantic audit reasons and the remaining T11 gates stay open.

T11.2b current-origin and T11.3 fixed-kernel contracts pass verify 44776130049
on b93d8f1d (2,912 canonical cases, zero issues). The actual registered lobby
handler cannot rebind a retained context to a replacement host; fresh private
state and Ready/Cancel remain functional. All registered C2S wrappers validate
current player/listener/open transport before handler work, while pre-seat
current handshake/lobby traffic remains allowed. Remaining auxiliary admission,
audit reasons, full network gates and the permanent fuzz corpus stay open.

T11.3 refinement before code (2026-11-20): **T11.3a** fixes kernel policy
bounds and adds a separate auxiliary-intent budget (80 burst, 40 per 20 server
ticks), preserving the existing command/handle budget and tighter per-type
limits. Malformed refusals retain the common 100/101 violation policy.
**T11.3b** verifies stable rejection/audit reasons and the remaining per-type
rate/field/handle controls. Test auxiliary bursts with real template acknowledgments,
backend-resend controls and accepted owner/peer unit orders in the same scene.

T11.2b numeric-field contracts pass verify 44754651943 on 9935db19
(2,905 canonical cases, zero issues): 41 raw invalid-field vectors and three
L1 typed-model/wire-fault cases. Raw IEEE primitive reading is preserved;
request records enforce finite values and the already supported SPEED set.
The core-codec L6/JMH gate and all-C2S current-origin acceptance remain open.

T11.2a passes verify 44751375106 on cfd9f0f6 (2,861 canonical cases,
zero issues): all nine registered C2S codecs have four malformed-input and
byte-exact valid controls; a real registered-handler test counts mixed-type
refusals, preserves accepted owner/peer commands at 100 violations, and kicks
only the offender at 101. Field/current-sender/rate/handle validation and the
permanent fuzz corpus remain separate gates.

T11.1a/b pass verify 44748687209 on 1f70e195 (2,814 canonical cases,
zero failures/errors/skips): current-connection denial, whole-range replay and
overlap rejection, wide overflow bounds, gaps and new-view sequence renewal
with existing core orders retained. Ordinary commands and placement share one
client sequence source; protocol 6 declares the compatibility requirement.
T11.2/T11.3 and parent T11 remain open.

T11.2 refinement before code (2026-11-20): **T11.2a** inventories all nine current
serverbound StreamCodecs and requires empty, truncated, over-cap and trailing
input to become consumed refusal values, with byte-exact valid controls.
**T11.2b** applies stable handler rejection, current-sender and field validation
with actual state/transport controls. Strict S2C decoding stays unchanged;
T12 retains the separate permanent fuzz corpus and 100,000-input gate.

T11 readiness/split (2026-11-20): T3b and T6 are delivered. Under the operator's
continuing whole-M7 instruction, begin the existing specified NET-06 scope as
**T11.1** authenticated current-connection and sequence boundaries;
**T11.2** bounded field validation across all registered serverbound types; and
**T11.3** stable rejection reasons, rate/handle limits and kick boundaries with
legitimate traffic controls. T12 retains the permanent 100,000-input corpus.
T11.1 is independent of the running T9 acceptance gates. Tests must establish a
replacement connection with the same UUID, prove a fresh command applies, and
show that a retained predecessor cannot mutate the new recipient's match.
Do not invent a client-supplied authentication token or silently change the
wire contract. Parent T11 and NET-06 remain unaccepted until all required gates.

Extend existing `NetGateway.commands`, `ByteBufWire` and rate limiter; do not create a second
ingress route. Bound every serverbound type, not only commands: list lengths, byte size, enum
tags, finite numeric coordinates, match/handle generations and sequence fields. Preserve the
architecture limits: 32 commands / 4 KiB per batch, 300 handles per command, 40 commands/s with
burst 80 and 3,000 handles/s; more than 100 violations per minute disconnects with a reason.
Ping additionally has its own one-per-two-seconds limit. Confirm these against current codecs
before implementation; never lower safety by relying on vanilla's unknown-payload cap.

Keep a seeded append-only corpus under `mod/src/test/resources/fixtures/net-fuzz/`. For at least
100,000 generated inputs per L2 build, exercise every registered serverbound decoder, malformed
lengths/truncation/overflow and valid boundary cases. The safe decode entry returns a bounded
failure; no uncaught exception, unbounded allocation or server fault is accepted. Preserve every
crashing input as a regression. L3 feeds a bounded representative accepted/rejected corpus
through real ingress and checks state/journal deltas against accepted commands only. Local
rejection records contain stable reason codes with aggregate rate reporting, no hidden target
detail, personal data or full payload dumps. Test kick thresholds and a legitimate traffic
positive control. Unsupported protocol versions fail explicitly.

### 3.7 Commander/body hardening (NET-09)

Proposed files `mod/mixin/ServerGamePacketListenerImplMixin`, `ServerPlayerMixin` and
`server/commander/CommanderGuard` implement architecture §12.6:

- Drop `handleTeleportToEntityPacket` for match members before entity lookup can teleport them;
  retain vanilla behavior outside Strategos matches. Confine match lookup to the server thread;
  verify the handler's `PacketUtils.ensureRunningOnSameThread` behavior when choosing injection.
- In `ServerPlayer.attack(Entity)`, commanders do not change camera by attacking. Preserve
  valid first-person interaction and vanilla behavior for nonparticipants.
- In `ServerPlayer.broadcastToPlayer(ServerPlayer)`, reject enemy commander pairing always;
  enemy first-person bodies require the viewer faction's visibility. Compose with vanilla
  distance/chunk eligibility, fail closed and do not cancel the tracker update that must unpair.
- `CommanderGuard`, via `PlayerTickEvent.Post`, corrects an out-of-bounds commander within
  one tick to `MatchSpace` plus the existing configured camera margin. Check finite coordinates,
  level identity and vertical limits. First-person mode uses its existing rules. Keep arena
  `spectatorsGenerateChunks` false.

Checked source anchors: Minecraft `ServerPlayer.java:957` (`broadcastToPlayer`) and `:1767`
(`attack`); `ServerGamePacketListenerImpl.java:1204` (`handleTeleportToEntityPacket`);
NeoForge `PlayerTickEvent.java:52` (`Post`). These are source observations, not L3 evidence.
Inventory both new mixin classes in `mod/src/test/resources/engine-surface.json` and register
them in `strategos.mixins.json`; add event/injection entries as required by the existing schema.
Every denial test has an allowed non-match/own/visible counterpart. No multiplayer playtest
starts before these gates pass, including under `-Pcompat`.

### 3.8 Team UI and AI

`client/hud/MultiplayerHudModel` and lobby view-models receive player colours, teams and status;
`PingPayload` uses the existing gateway with server-derived team fan-out and one ping per
player per two seconds. Validate coordinates and lifetime, render on minimap and in-world,
expire by tick, and clear on match/team/session change. Preserve M6 minimap behavior; no text
or voice subsystem is added. A rejected ping changes no private state and reaches no opponent.

Extend the M6 `AiView`/wave scheduler with immutable diplomacy and observed allied-threat
inputs. Never hand it unrestricted enemy queries. AI selects no allied attack target; an idle
army helps an allied base within 600 ticks, and allied AI waves launch within 1,200 ticks of
each other (20 TPS). Use stable faction order and bounded coordination, preserve difficulty
and deterministic replay. Active armies are not reassigned by the idle-help rule. Tests use
fixed scenarios where movement/distance makes these deadlines feasible; distinguish a decision
deadline from unsupported teleportation. No coordination through human pings is added.

### 3.9 Bandwidth and S11 (NET-04)

Extend `NetGateway` with `ChannelBudget` and per-recipient pending state. Preserve critical
lifecycle/control delivery; defer low-priority summary/far-FX work when its channel allowance
is exhausted. State deltas are cumulative against the last actually sent revision, never the
last computed frame. A slow recipient cannot block another; bound backlog size and age and
coalesce superseded state. Recheck audience, membership and epoch at flush so a deferred
private frame cannot survive a revoke/rejoin. Report dropped expendable effects separately;
authoritative state and removals must eventually converge without loss.

Measure S11 in `docs/spikes/S11-bandwidth.md`: four acknowledging byte-counting mock players,
600 moving/fighting units; pan 40 blocks/s, fog-edge fight and construction storm. Report all
clientbound traffic (vanilla and Strategos) per player/type/channel; separate terrain streaming
from the E budget as architecture §12.5 requires. State encoded-byte accounting and overhead,
warm-up, exact measured scene duration, mean, maximum one-second window and per-tick peak;
never divide by setup/idle time to dilute traffic. Use 60-second measured scenes after declared
warm-up, plus the 20-minute PERF-10 gate. Each client's gameplay mean stays ≤100 KB/s using
the existing counter's unit convention; preserve peaks even though no new peak limit is imposed.

Two real clients confirm mock accounting with JFR packet events and per-connection summaries,
including vanilla traffic, by the same scenes; explain counter discrepancies. S11 explicitly
decides whether tier 4 is needed. If the validated current tiers exceed E, investigate before
activating the reserve: streamed entity types are selected only at match start, require their
own implementation/codec/render/compat tests and must fit the existing task-size cap. Do not
switch types mid-match or raise the budget. Split reserve work into a reviewed task if needed.

### 3.10 PERF-10, art and platform gate

PERF-10 uses four mock players, 4 × 150 population, 120 buildings for 20 measured minutes after
warm-up. Check A–E per applicable scope: core/server metrics are shared host metrics, path
latency is recorded by issuer where possible, and E is independent for every client. Preserve
A p50 ≤5 ms/p99 ≤10 ms; B p95 ≤17 ms; C p95 ≤23 ms; D main share p99 ≤2 ms and 100-unit
order latency p95 ≤5 ticks with 200 requests queued; E ≤100 KB/s per client. Run the 2× stress
pass with C p95 ≤50 ms. Keep the existing per-unit comparisons and validity guards, record
hardware/CPU interference, reports and crashes, and archive the accepted multiplayer reference.
Non-reference hardware results are diagnostic until the owner accepts another reference.
No M8 fog-cost certification or threshold/baseline change is implied.

ART-03 follows ART-01, D-03 and D-04 after the MVP fun verdict: skins/banners, starting buildings,
tower/arsenal, construction/damage stages, and command-card icons. Name owner/external asset
tasks before they block T22. Lint and GameTest all blueprints, verify complete icon coverage,
inspect 60-block goldens and obtain the owner's silhouette/look verdict. Record asset licence
and provenance without importing prohibited code/data. The second civilisation remains ART-04.

QA-14 is a separate time-boxed spike at the end of MVP or 2027-Q2, whichever comes first.
Its branch measures rendering/GUI/input/persistence port effort, builds and passes its GameTests,
lists every core change (or confirms none), and supplies an owner-approved ADR with next review
date. Current platform facts must be researched then from permitted primary sources; no current
ecosystem claim or production port is made by this planning document.

## 4. Tests and evidence by level

| Level | Required evidence |
|---|---|
| L0 | Formatting/compilation, ErrorProne/NullAway, core purity, B6 single sender, client/common boundaries, jar contents |
| L1 | Exact diplomacy/control/ownership/cost/victory rules; deadline boundaries; deterministic team AI; pending-state convergence and recipient revocation |
| L2 | Every new payload round trip, max size, audience and inventory; 100,000-input seeded fuzz campaign and permanent crash corpus in each build; blueprint/icon checks |
| L3 | Four-player dedicated-world join/start/private-state/co-op/reconnect tests; real ingress state assertions; hostile vanilla packets with positive controls; runtime blueprint tests |
| L4 | Lobby, team HUD, minimap/world ping and ART-03 at 60 blocks; PNG plus metadata and platform goldens, each image inspected |
| L5 | `verify` dedicated-server smoke/restart without client linkage; restore roster/diplomacy/timeout fixtures |
| L6 | S11 byte counts and deferral, JMH for hot paths, PERF-10 20 minutes plus 2× stress, validity and report guards |
| L7 / R / D | Two-client protocol incl. reconnect/co-op/pings, real S11/JFR comparison, owner art verdict; S11/PERF-10 reports; separate QA-14 ADR |

For every negative audience or hardening assertion, demonstrate the corresponding owner/ally/
visible/non-match positive control. Tests assert payload contents and vanilla pairing, not just
absence of a crash. New fast four-player QA-15 smoke must complete in under 30 seconds; longer
reconnect/AI/performance scenes belong to the established slow/soak levels. Never retry a red
measurement until green or weaken a gate. Inspect XML, markers and faults, not exit code alone.

## 5. Task breakdown and traceability

### 5.1 Order and verification conventions

This is the **future implementation** task list, not progress on the planning claim. Sizes are
S ≤400, M ≤900, L ≤1,500 changed lines; split larger work before claiming it. `V` below means
`./gradlew verify`, `F` means `./gradlew verifyFast`, `S` means `./gradlew verifySlow verifySoak`,
`J` means `./gradlew :core:jmh :core:jmhCompare`. Run F per edit batch and V before each task commit.
Commands name current Gradle tasks; proposed tests/scenarios must be registered in their task.
An unavailable prerequisite means blocked, not a waived check. On Windows use `gradlew.bat`
and `python -X utf8` (see [toolchain KB](../kb/minecraft-mod-build.md)).

| Task | Deliverable / card | Depends on | Size | Verification in addition to V |
|---|---|---|---|---|
| T1 | Reconcile merged predecessors, decisions, hook signatures, schema and scope; record readiness | Owner opens approved M7 scope; applicable M5/M6 close results | S | F; `python -X utf8 tools/roadmap.py check`; `python -X utf8 tools/claims.py check --base origin/main` |
| T2a | Independent identity/diplomacy/grant state, detached immutable view and deterministic snapshot/hash contracts; MATCH-06 | T1; CORE-01 | M | `:core:test` exact permission matrix, invalid participants, grant revocation, detached views and deterministic reconstruction; V; no running tick/order path changes |
| T2b.1 | Compose corrected PR #30 prerequisite with current main, resolve identifiers and validate gates | T2a; own prerequisite branch at 588fa57 | S | V, S, standard cloud perf; no gates/thresholds changed |
| T2b.2 | Install T2a authority, immutable query, journaled first-tick setup and versioned diplomacy preservation; retain legacy identity defaults | T2b.1 verified persistence contract; final full L6 shared with T2b.1 | L | `:core:test`, current/legacy JSON/NBT, malformed snapshots and binary-journal replay; V, S, cloud JMH/perf |
| T3a | Immutable bounded lobby configuration/readiness/preparation contract; NET-02 foundation | T2b.2; independent of world start and hosting topology | M | `:mod:test`, seat/config bounds, co-op consistency, detached views, readiness resets and rollback; V |
| T3b | Authenticated lobby/start service, intents and private payloads; NET-02 | T3a; MATCH-02 delivered; D-20 | L | `:mod:test :mod:runGameTestServer`, four-player start ≤10 s and outsider rejection |
| T4 | Lobby screen/readiness/error rendering; NET-02 | T3b | M | `:mod:runScenario -Pscenario=m7-lobby -Pscenario.timeoutSeconds=180` (new scenario) |
| T5 | Slot bindings, grants, roster and deadline migration/restore | T2a, T3b; CORE-07; integrated save/reload schema | M | `:core:test :mod:test :mod:runGameTestServer`, old/new fixtures and malformed saves |
| T6 | Reusable N-player kit, per-player captures and hostile packet helpers; QA-15 | T3b; QA-19 | M | `:mod:runGameTestServer`, four-player smoke <30 s; cleanup/no leaked players |
| T7 | Friendly-fire filters and allied victory; MATCH-06 | T2b.2; M5 combat and M6 MATCH-03 delivered | M | `:core:test :mod:runGameTestServer`, delayed/splash damage, coalition outcomes; S; J if hot path |
| T8 | Co-op authority in every command/query/first-person path; MATCH-06 | T2b.2, T5, T6 | L | `:core:test :mod:runGameTestServer`, two humans one faction, owner costs and revoke; S |
| T9 | Recipient projection/fan-out and no client attachment sync; NET-03 | T2b.2, T6, T8; NET-08 | L | `:core:test :mod:test :mod:runGameTestServer`, full/delta/query recipient matrix |
| T10 | UUID reconnect, expiry, AI/defeat policy, camera/groups and epoch reset; NET-05 | T5, T6, T9; M6 AI adapter if takeover selected; D-20 | L | `:mod:test :mod:runGameTestServer`; S for deterministic deadlines/orders |
| T11 | Field/rate/sequence limits, reason codes and kick policy; NET-06 | T3b, T6 | M | `:mod:test :mod:runGameTestServer`, threshold boundaries and valid traffic |
| T12 | Permanent seeded fuzz corpus and ingress state regression; NET-06 | T11 | M | `:mod:test` ≥100,000 inputs across all serverbound types; L3 bounded corpus via `:mod:runGameTestServer` |
| T13 | Two mixins and commander guard, inventory; NET-09 | T6, T9; M6 visibility delivered | L | `:mod:test :mod:runGameTestServer`; `./gradlew verify -Pcompat`; S for movement |
| T14 | Team HUD/ping routing, lifetime/rate and goldens; UI-15 | T4, T9, T11; M6 UI-20 delivered | L | `:core:test :mod:runGameTestServer`; `:mod:runScenario -Pscenario=m7-team-hud -Pscenario.timeoutSeconds=180` |
| T15 | Observed team AI, idle defence and wave coordination; AI-06 | T2b.2, T7; M6 AI-05/AI-18 delivered | M | `:core:test`, 600/1,200-tick bounds and no ally targeting; S; J if hot path |
| T16 | Channel budgets, cumulative pending state and backpressure; NET-04 | T9, T11 | L | `:core:test :mod:test :mod:runGameTestServer`, deferred removals/epoch/revoke and convergence |
| T17 | S11 mock scenes and archived byte report; NET-04 | T6, T7, T13, T16; PERF-01 | M | Register bounded L6 scene in existing perf harness; four recipients/600 units, counter assertions and report guard |
| T18 | S11 real-client comparison and tier-4 decision; NET-04 | T17; owner two-client session | S | R: same scenes, JFR/counter comparison, explicit reserve decision |
| T19 | Extend QA-09 multiplayer protocol with teams/rejoin/co-op and acceptance record; QA-15 | T6, T10, T13, T14 | S | V plus reviewed step-to-evidence mapping; L7 executed at T25 |
| T20 | 20-minute multiplayer gate and 2× stress; PERF-10 | T15, T17, T18; M6 PERF-09 delivered | M | `./gradlew verifyPerf` after wiring M7 scene/report selection; S, per-client E and host A–D, accepted reference archive |
| T21 | Cross-feature hardening/compatibility regression and leak inventory | T10, T12, T13, T14, T15, T20 | M | `./gradlew verify -Pcompat`; S; full recipient/reconnect matrix |
| T22 | Civilisation assets and complete command-card icons; ART-03 | QA-13 fun verdict; D-03/D-04 and named owner asset tasks | L | `:mod:test :mod:runGameTestServer`, lint/blueprint/damage-stage/icon coverage; split assets if over L |
| T23 | Art goldens and owner silhouette verdict; ART-03 | T22 | M | `:mod:runScenario -Pscenario=m7-art -Pscenario.timeoutSeconds=180`; inspected 60-block PNGs; L7 |
| T24 | Floating platform spike, report and decision ADR; QA-14 | MVP exit or 2027-Q2, whichever first; own bounded branch/claim | M | L0/L3 on spike; per-area report; owner ADR/next review date; not an M7 Done dependency |
| T25 | M7 integration handoff and two-client acceptance | T19, T21, T23; no open P0/P1 | S | V, S, completed L4/L6 evidence and L7 protocol; claim/card/spec records; no public release |

Each task includes the design in §3 for its named feature; T1 must split oversized tasks without
dropping criteria. Per-feature L3 remains one integration scenario where possible; L1 carries
the exact rule matrix. L6 runner/configuration commands in T17/T20 are implementation work,
not existing M7 scenarios that can be run today. If S11 needs the tier-4 reserve, add a bounded
reviewed implementation task between T17 and T18, keeping the acceptance dependency explicit.

### 5.2 Card → task → evidence

| Card | Tasks | Complete acceptance evidence |
|---|---|---|
| MATCH-06 | T2a, T2b.2, T5, T7, T8 | No allied damage; coalition victory; two authorized controllers; player-name colours and zero scoreboard units |
| NET-02 | T3a, T3b, T4 | Four mock lobby clients agree; each `match_join` ≤10 s; outside commands refused; inspected lobby goldens |
| NET-03 | T9 | Owner receives treasury/queues/research, opponent does not; ally rule table exactly; no synced Strategos attachment |
| NET-04 | T16–T18 | Per-client scene mean ≤100 KB/s and peaks recorded; deferral converges; two real clients validate counts; tier-4 report decision |
| NET-05 | T5, T10, T25 | Reclaim ≤5 min; existing orders continue; configured expiry policy once; real mid-fight rejoin |
| NET-06 | T11, T12 | ≥100,000 safe decodes; only accepted commands change L3 state; reason/kick tests; corpus/fixtures run each build |
| NET-09 | T13, T21 | Teleport refused; attack does not change commander camera; enemy commander/fog-body denial with controls; one-tick clamp; two mixins/inventory and compat |
| UI-15 | T14, T25 | Ally-only ping positive/negative tests; HUD/ping goldens; owner uses pings |
| AI-06 | T15 | No allied targeting; idle defence ≤30 s; allied waves within 60 s; L3 adapter proof |
| QA-15 | T6, T19, T25 | Four-player smoke <30 s; per-player payload assertions; extended protocol used at acceptance |
| PERF-10 | T20 | 4 ×150 +120 buildings, 20 min A–E; 2× stress C p95 ≤50 ms; multiplayer baseline report |
| ART-03 | T22, T23 | Blueprint lint/construction/damage GameTests; every command icon; 60-block goldens; owner approval |
| QA-14 | T24 | Per-area port-effort report, owner-approved decision/next review ADR, spike build/GameTests and core-change inventory |

### 5.3 Progress

- [x] T1 (2026-10-29) · Reconcile prerequisites and implementation readiness (bounded foundations; see §2.3).
- [x] T2a (2026-10-29) · Independent identity, diplomacy, grants and immutable snapshot contract.
- [x] T2b.1 (2026-10-30) · Corrected delivered persistence prerequisite composition and required cloud gate components.
- [x] T2b.2 (2026-10-30) · Match authority/setup, schema-4 persistence/hash and binary journal integration.
- [x] T3a (2026-10-30) · Bounded lobby state/readiness/preparation contract; full cloud verify, 12 new contract tests.
- [x] T3b (2026-11-18) · Authenticated lobby service, private protocol and production start; verify 44148921127 and complete slow/20-seed soak 44148929120 on e382f7b6.
- [x] T4 (2026-11-18) · Lobby UI; verify 44202558089 and cloud L4 44203471758, seven inspected zero-difference goldens, real start/RTS flow and altered-golden rejection.
- [x] T5 (2026-11-18) · Adapter blob-2 roster/session migration, disconnected restore and durable launch refusal; verify 44209974849 plus complete full-ci 44208443663 (248 slow/core cases and all twenty AI seeds).
- [x] T6 · Multiplayer test kit. Engine verify 44300748747; four-player smoke 1.099 s under 30 s. QA-15 L7 remains T19/T25.
- [x] T7 (2026-11-18) · Allied damage and victory; verify 44296700394, full-ci 44296705408, L4 44297120970, matched JMH review 44303346246 and complete 24-scene L6 evidence 44314975700.
- [x] T8 (2026-11-19) · Co-op authority; verify 44614567652, full-ci 44606455194, inspected L4 repeats 44613485447/44616116638, complete matched JMH 44616013773.
- [x] T9 · Recipient privacy.
- [ ] T10 · Reconnect and timeout.
- [x] T11 (2026-11-20) · Ingress validation; full verify 44786479783 (2,927 cases), paired core L6 44774432743 with unchanged gates.
- [ ] T12 · Seeded fuzzing.
- [ ] T13 · Commander hardening.
- [ ] T14 · Team HUD and pings.
- [ ] T15 · Team AI.
- [ ] T16 · Channel budgets and pending state.
- [ ] T17 · S11 mock measurement.
- [ ] T18 · S11 real-client comparison and reserve decision.
- [ ] T19 · Two-client protocol.
- [ ] T20 · Multiplayer performance gate.
- [ ] T21 · Integration and compatibility.
- [ ] T22 · Civilisation assets (parallel after MVP/decisions).
- [ ] T23 · Art acceptance.
- [ ] T24 · Floating platform gate (independent; not in M7 Done).
- [ ] T25 · Milestone handoff and owner acceptance.

## 6. Acceptance checklist

- [ ] Every M7 runtime card criterion in §5.2 is demonstrated; no unexecuted contract test is called runtime evidence.
- [ ] Private state absent from every unauthorized player's full/delta/query/reconnect/deferred output.
- [ ] Commander guards pass with positive controls before multiplayer play, including `-Pcompat` movement/fog/hardening tests.
- [ ] S11 mock and two-real-client comparison complete; every client's E passes, tier-4 decision explicit.
- [ ] PERF-10 normal and stress gates valid and archived; no thresholds raised or invalid runs hidden.
- [ ] ART-03 blueprint/icon/golden checks and owner look verdict complete after MVP approval.
- [ ] Dedicated two-client acceptance covers allied victory, co-op, pings and mid-fight reconnect; no P0/P1 remains open.
- [ ] V/S and all change-type gates pass; branch/card/spec/KB/inventory records agree; handoff names residual M8 work.
- [ ] QA-14 tracked on its own timing/claim with owner decision; never used to block or falsely complete M7.

## 7. Risks and fallbacks

| Risk | Detection | Response |
|---|---|---|
| Numeric player/faction identity survives in a side path | Search numeric comparisons, multi-controller tests for every command/query | Route through one control resolver; deny unknown bindings |
| Deferred/reconnect state leaks after revocation | Per-recipient capture across epoch/grant/team changes | Invalidate/reproject pending state and force a recipient snapshot |
| M6 schemas/contracts drift | T1 reconciliation and fixture compilation | Adapt to merged authority; leave AFTER markers and block missing integration |
| Shared vision is assumed before FOW-02 | Allied hidden-unit/body tests | Keep solo fog; export diplomacy contract for M8; do not claim shared vision acceptance |
| Four-player tests consume the fast-suite budget | QA-15 per-scene elapsed report | Keep exact matrices in L1 and long deadlines in slow suites; never skip the fast smoke |
| S11 exceeds E | Separate gameplay/terrain/per-channel counters and real comparison | Optimize/defer within bounded queues, then reviewed tier-4 reserve; no threshold increase |
| New mixin conflicts with compatibility mods | Inventory and positive/negative `-Pcompat` tests | Diagnose supported injection points; no blanket cancellation that breaks unpairing |
| Art or product decision missing | T22 readiness check | Name owner asset/decision task; continue independent runtime work; keep acceptance blocked |
| Invalid performance environment | CPU/report/crash guards | Preserve INVALID evidence and cause; do not certify regression or silently rerun |

## 8. Owner inputs, windows and handoff

- D-20: accepted by the owner in the M7 session on 2026-10-30: dedicated first-class plus
  Open to LAN through one service, one active match per server. Dedicated tests are authoritative.
  The maintainer reconciles the main STATE record at review; M6 start integration remains separate.
- D-03/D-04: theme and applicable style direction; name any owner-supplied asset work and record
  the M6 fun verdict before ART-03 starts. Do not invent approval for unresolved parts.
- Co-op avatar: resolve the roadmap demo's fighting/scouting wording against D-08 and deferred
  CMB-08. This plan uses the existing observer avatar; adding combat or vision requires an
  explicit scope decision and its own card/task, not an implicit expansion of MATCH-06.
- Approval/opening: review proposed M7 scope and this plan, then explicitly open ready tasks for
  claims. This planning delivery leaves all feature statuses `proposed` and STATE unchanged.
- Client batch: future implementation collects lobby, team HUD/ping and art scenarios and runs
  them consecutively, one bounded window at a time with the commands above, announced before
  opening. Inspect every changed PNG. If display is unavailable use the Xvfb route, not blind
  retries. No window runs during another build or L6 measurement.
- Real-client batch: owner starts two clients and a dedicated server after T13 hardening; use the
  combined T18/T25 protocol for network accounting and acceptance, recording the distinct scenes.
- Handoff: provide task-level readiness/dependency map, schema migrations, protocol/corpus and
  hook inventory, S11/PERF-10 reports, inspected screenshots, owner verdicts and remaining M8
  integration contracts. Report M7 runtime completion separately from owner acceptance and the
  floating QA-14 platform decision. Public release and every merge remain the owner's actions.

### Foundation verification, 2026-10-29

T2a: [cloud verify 40788528341](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40788528341), implementation checkpoint `e8438cd`, passed in 7m 20s: 2,253 canonical XML test cases, zero failures/errors/skips; all 16 DiplomacyStateTest cases executed. Core architecture/static checks, adapter tests, GameTests, server smoke and negative harness self-tests passed through verify. Running tick/order/hash paths are unchanged, so this bounded task does not require slow/soak or JMH. T2b.2 still owns installation, journal and versioned persistence; no live co-op or privacy criterion is declared complete.

Main synchronization: merge `4e1ff97` includes `origin/main` at `dcb5013`. [Cloud verify 40789677322](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40789677322) passed in 6m 2s: 2,253 canonical XML cases, zero failures/errors/skips, all 16 diplomacy cases executed. Final fetch showed zero commits behind main. The operator requested a stop for 2026-10-29 after T2a and this merge; T2b.2 is unstarted.

T7 acceptance (2026-11-18): immutable production source `4dd51afa` passes cloud verify
[44296700394](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44296700394)
(2,731 canonical cases, zero failures/errors/skips); full-ci
[44296705408](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44296705408)
(all 23 jobs, 248 slow/core cases and complete seeds 1–20); inspected six-frame L4
[44297120970](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44297120970)
(all NMSE 0, altered-golden negatives rejected); and the unchanged 15%/allocation JMH gate
[44303346246](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44303346246)
on all 30 preserved matched measurements. Standard L6 run 44296716682 passed 21 scenes
before its outer execution clock expired; completion
[44314975700](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44314975700)
passed exactly the three missing scenes at the same source and original settings, including
6,000 battle ticks and 36,000 MVP ticks. The strict disjoint 21+3 inventory and original
report hashes are archived in artifact 14434942206. No gate, window or threshold changed.
T8 still owns co-op command/recipient integration; MATCH-06 remains in progress.

T6 engine acceptance (2026-11-18): source `f329fc55`, cloud verify [44300748747](https://github.com/o-e-lytvynskyi/strategos/actions/runs/44300748747), 2,734 canonical cases with no failures/errors/skips. All three new engine contracts passed (four players 1.099 s, atomic admission 0.770 s, bounded hostile ingress 1.319 s); six dependency-cache counterexamples and the validated module hand-off passed before Gradle. QA-15 stays in progress for T19/T25 L7.
