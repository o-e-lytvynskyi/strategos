# Fog contracts before live integration

The owning APIs are `TeamVision`, `BuildingMemory`, `FogAudience` and
`ScoutKnowledge`. They are cold contracts on the M4 runtime base, not installed
fog, packet guards or an AI controller. M6/M7 adapters and final acceptance are
explicit AFTER markers; isolated L1 decisions cannot establish live leak freedom.

## Handles are signed bit patterns

`Handle` packs 20 index bits and 12 generation bits. Generation 2048 sets the
sign bit; a negative int can be a valid handle. Generation zero is invalid.
Buildings use indices at/above `BuildingStore.BASE`, units below it. Validate the
domain and preserve the full handle, not `handle > 0` or only its slot index.
Memory pins a signed-handle round trip and keeps different generations distinct.
Generation wrapping still needs the live adapter's identity/lifetime policy.

## A union is not persistent exploration

TeamVision queries only the viewer and directly authorized vision sources, in the
source-to-viewer direction. It does not transitively grant an ally's allies and
does not add a second hysteresis layer over M6's VisibilityIndex. Its explored
query returns the sources' current union. Before diplomacy removes a source,
materialize already received exploration into the viewer's saved M6 explored
state. Do not erase terrain knowledge merely by removing sharing.

## Absence and old events are not observations

A hidden building disappearing from the live list must not clear a mirage or
update its health/owner. Only an authorized positive observation updates memory;
only a positively observed empty remembered footprint clears it. Store template
identity as TemplateId, never as a session-local numeric template index.

An empty-site event must also advance the saved watermark. Otherwise an old
queued observation can resurrect a cleared record after save/load. BuildingMemory
enforces monotonic event times and a bounded, canonically ordered snapshot. Its
standalone codec does not change MatchSnapshot, the gameplay hash or live saves.

## Visibility does not expose enemy intentions

UnitView contains a target position from its order. Even a currently visible unit
must not grant a fog-fair AI that private destination. ScoutKnowledge copies only
an explicit public target projection (handle, faction, template, current position),
not UnitView, MatchQueries or private orders. It separates remembered buildings
from current unit targets and expires those targets at the next think tick.
Actual commands must still go through normal authoritative target validation.

## Transport audiences do not filter fields

PayloadSpec/Audience.Kind declarations constrain the transport; they do not prove
that an fx frame, building snapshot or diagnostic contains only allowed fields.
FxSender groups world events by 16-block pages, while M6 vision uses 4-block cells.
Seeing one cell/page corner cannot authorize every event in that page: filter
events before batching. Operator command permission also does not authorize a
server particle broadcast to nearby ordinary players.

The outbound audit lists the current S2C types and vanilla routes. Its L2 inventory
test fails if the registry changes without an audit entry. T9 must still exercise
real packets for every channel, with hidden negatives and visible positives.
The public WAVE telegraph is intentional (M6 spec §1.6 row 17); it is not permission
to broadcast arbitrary hidden state. Terrain/block states and the entity-ID
counter remain the documented ADR-0016 side channels, not secrecy guarantees.
