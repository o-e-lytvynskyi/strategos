# M8 outbound-channel audit

Foundation inventory refreshed against main dcb5013 on 2026-10-29, including M5. Runtime senders are unchanged by this
claim. `FogAudience` is an independently tested decision contract, not an installed
guard. No all-channel leak-freedom claim is made before T9's mock-player tests.

## Registered server-to-client payloads

Every registered S2C type must occur exactly once in this table. The L2 inventory
test compares it to `ModPayloads.specs()`; `PayloadAudienceTest` separately checks
declared audiences and refusal of unknown types. Adding a type requires an audit.
Audiences below are declarations, not proof that every field is fog-filtered.

| Payload | Declared audience | Sender/current behavior | Remaining integration |
|---|---|---|---|
| `strategos:match_join` | PLAYER | NetGateway: connection's assigned match and slot | M7 recipient identity/role controls; no enemy instance state |
| `strategos:match_state` | MATCH | NetGateway: public match lifecycle/clock | Preserve match isolation; no hidden position fields |
| `strategos:feedback` | PLAYER, FACTION | NetGateway/OutputAppliers: command result and owner notices | Uniform hidden/missing rejection; never reveal hidden target stats |
| `strategos:template_set` | PLAYER | TemplateSync: catalogue before entity pairing, also online reload sync | Public definitions, not instance state; preserve ordering and bounded decoding |
| `strategos:unit_summary` | FACTION | Replication: Visibility query, currently ALL unless overridden | M6/M7 authoritative TeamVision; hidden-handle negative and visible control |
| `strategos:unit_hud` | PLAYER | Replication: changed HP through the same visibility seam | Revoke cached HP on hide; observer/owner controls; never use remembered sight |
| `strategos:unit_detail` | PLAYER | Replication: requested own-unit details | Preserve owner-only restriction and subscription cleanup; no hidden-handle oracle |
| `strategos:faction_state` | PLAYER | Replication: seat's own treasury/population/workers | Keep owner-scoped even when another faction's units are visible |
| `strategos:building_state` | PLAYER | Replication: all live buildings; own-only queue/rally fields | Visible live buildings versus immutable remembered records; hidden destruction/capture cannot refresh ghosts |
| `strategos:deposit_state` | PLAYER | Replication: live deposits and current amounts | Audit unseen depletion/location by cell; current all-deposit frame is not fog-safe |
| `strategos:fx` | TRACKERS_OF, SEES_CELL, SEES_EITHER, PLAYER | FxSender: unit hits/deaths to trackers, world effects grouped by 16-block pages; ShotFx groups launch/impact endpoints; SeesCell and SeesEither currently send to every member | Per-event cell/endpoints; page visibility alone cannot reveal hidden events elsewhere on the page |
| `strategos:match_event` | MATCH, FACTION | OutputAppliers: defeat/end and WAVE public, attack alerts to owner faction | Preserve public WAVE telegraph (M6 spec §1.6 row 17), owner alerts and match isolation; classify any new event fields |
| `strategos:debug_feed` | PLAYER | DebugFeed: permission-gated operator subscription; global diagnostic state | Explicit privileged channel, never ordinary gameplay visibility; sanitize subscriptions on permission/role changes |

Seven registered C2S payloads are not outbound channels: commands, ui_query,
template_ack, camera_jump, mode_switch, match_control and debug_subscribe. Their
connection identity, rate limits and target validation remain independent gates.

## Vanilla and semantic channels

| Channel | Current source/evidence | Required guard and T9 control |
|---|---|---|
| Unit body pairing | UnitEntity.broadcastToPlayer; M6/M7 runtime absent | Authoritative unit visibility; stationary viewer, chunk arrival, hide/unpair and visible control |
| Sounds | UnitEntity suppresses server positional playSound; FxSender plays unit FX on client | Tracker/cell audience, plus PlayLevelSoundEvent.AtEntity backstop when integrated; never rely on AtPosition to identify a unit |
| Particles | FxSender world/attached effects; InspectCommands sends server debug path particles | Per-event visible source; permission to invoke a debug command does not authorize broadcasting its hidden path to nearby nonoperators |
| Projectile FX | M5 ShotFx.events/group -> FxSender -> Audience.SeesEither; NetGateway currently broadcasts to match members | Either endpoint visible, no unrelated target fields; hidden/visible source and destination controls; page grouping alone is insufficient |
| Health bars | unit_hud, no HP mirrored in vanilla synced body health | Current sight, not exploration or building memory; positive/negative HP update controls |
| Selection details | unit_detail owns subscriptions | Connection ownership and current sight where enemy summaries are permitted; denied and valid request controls |
| Map summaries | unit_summary, building_state, deposit_state | Separate current view, last-seen buildings and resource knowledge; no hidden live fallback |
| Pings | No gameplay ping sender on this base; notices are not an undeclared ping implementation | Classify per recipient/source when introduced; known-start coordinates do not authorize hidden entity pings |
| Block-break progress | BlockCracked -> FxSender world effects -> client destroyBlockProgress | Guard transient progress by observed event cell; terrain block-state exception does not authorize progress leaks |
| Scoreboard teams | No unit-faction scoreboard membership sender found | Do not implement enemy unit membership with a global scoreboard; contract always denies this channel |
| Enemy commanders | NET-09 not implemented here | Hide enemy commanders even in visible cells; explicit owner/authorized ally/observer controls |
| First-person bodies | NET-09 not implemented here | Gate enemy player body by current cell sight; positive/negative packet controls |
| Mirages/minimap ghosts | New memory contract only, no sender/rendering yet | Last observed state only; no queue/stat refresh or removal from hidden live queries |
| Terrain/block states | BlockWriter and vanilla chunk/block replication | Accepted terrain secrecy gap until FOW-06; never describe this as fully secret fog |
| Entity-ID counter | Vanilla entity allocation/IDs | Documented accepted side channel under ADR-0016; no guarantee against inference |

## Contract mapping and integration boundaries

`FogAudience.Channel` names entity-information policies, not payload IDs. A known
payload may carry different event classes (notably fx and match_event), so declaring
its Audience.Kind is necessary but insufficient. Unknown semantic IDs deny, even
for an observer/owner; outside-match evidence always denies. Only explicitly
classified observers and owners bypass ordinary entity sight. Direct sharing
authorizes allied body/commander/first-person channels; it is not a blanket
permission for enemy details. The decision switch is exhaustive: a new semantic
channel must acquire an explicit policy to compile.
Projectile destination sight authorizes only PROJECTILE; remembered information
authorizes only MIRAGE. SCOREBOARD_TEAM always denies. Public catalogue/lifecycle
and the intentional WAVE telegraph are classified separately from hidden entity
channels; owner-only faction state is not made public by sight.

M5 projectile/combat sender audit completed on 2026-10-29. Runtime endpoint filtering
still belongs to T9 and requires the M6/M7 visibility and recipient adapters.
AFTER-M6: supply authoritative cell/unit views, update cache revocation, integrate
memory and compare each field against solo fog/AI-18 behavior.
AFTER-M7: classify viewer roles/sharing, protect commander/first-person bodies and
run one mock-player negative/positive test per real channel. Operator diagnostics
need an explicit privileged path; inspect every alternative/vanilla send route.

At T9, enumerate all registered payloads again and search actual sends, sounds,
particles, progress, scoreboard changes and player pairing in the merged code.
The 13-type table is a base-specific audit, not a declaration about future M5–M7.
Run L2 inventory/audience gates, L3 packet controls and L5; then T11 measures
vision, re-tracking and pairing under the unchanged A–E and stress gates.
