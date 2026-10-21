# ADR-0023: Plan alignment after the roadmap's approval; the roadmap governs the schedule

Date: 2026-10-21

## Status

Accepted. It amends [ADR-0009](0009-tick-pipeline-determinism-and-fault-containment.md),
[ADR-0010](0010-match-model-and-lifecycle.md), [ADR-0014](0014-orders-commands-and-unitai.md) and
[ADR-0016](0016-networking-and-fog-of-war.md), and records where D-08 touches
[ADR-0011](0011-unit-entity-and-body-seam.md), [ADR-0015](0015-pathfinding-approach.md) and
ADR-0016.

## Context

- **The owner approved the roadmap on 2026-10-21** ([ROADMAP.md](../ROADMAP.md)). Every M0–M6
  card is approved except CMB-08, which is deferred, and every decision M6 needs is taken as
  recommended. Two of those decisions change what earlier ADRs assumed:
  - **D-24 (c):** solo-grade fog of war in the MVP. FOW-01, FOW-07, UI-16, AI-18 and MATCH-07 land
    in M6; team vision, targeted re-tracking, mirages, the leak audit and AI scouting stay in M8.
  - **D-08:** the first-person avatar is an observer. It adds no vision of its own, units pass
    through it, and it stands only in explored cells. CMB-08, first-person combat, is deferred, to
    be revisited after the M2 playtest.
- **The ADRs predate the roadmap's reviews**, which moved cards between milestones
  ([design/roadmap-changes.md](../design/roadmap-changes.md) §4 and §6). Their milestone tags now
  differ from the roadmap in three ADRs:
  - ADR-0009 starts the production journal ring in M6, and save and resume (MATCH-04) moved to M4;
  - ADR-0014 grows UnitAI combat in M3 and garrison in M9, while the battle sandbox brings combat
    to M2 and BLD-16 brings garrison and the town bell to M5;
  - ADR-0016 says the MVP plays on a revealed map, with entity fog in M8.
- **ADR-0010 says that GameTest matches place no tickets.** That holds for a match inside its test
  structure, because the runner force-loads the structure's chunks (StructureUtils.java:126,
  167–168). Map-scale GameTests import maps beyond the structure
  ([04-architecture.md §16.5](../04-architecture.md)), and at batch end the runner releases only
  vanilla forced chunks: it unforces `ServerLevel#getForcedChunks()`, which never holds
  `TicketController` tickets (GameTestRunner.java:102–103; ServerLevel.java:1410–1413). A leaked
  ticket would keep its chunks loaded into later batches, perf baselines included.
- **Accepted ADRs are not rewritten** ([README](README.md)); a new ADR amends them.

## Decision

- **The roadmap governs the schedule.** A milestone tag inside an ADR records the plan when the ADR
  was decided. [ROADMAP.md](../ROADMAP.md) and its cards govern the schedule, and
  [04-architecture.md](../04-architecture.md) follows them; where an ADR's tag and the roadmap
  differ, the roadmap wins. Moving a card needs no new ADR; changing a decision does. The
  amendments below align the ADRs once, at the roadmap's approval.
- **ADR-0009:** the production journal ring (the last 10 minutes, configurable) arrives in M4 with
  MATCH-04, save and resume, instead of M6.
- **ADR-0010:** a GameTest match inside its test structure places no tickets, because the runner
  forces the structure's chunks. Map-scale GameTests place their own tickets over the imported map
  and release them at teardown, and a batch-end check fails if any Strategos ticket, match, body or
  writer job remains (QA-04; architecture §3.4).
- **ADR-0014:** the UnitAI tree grows as the roadmap schedules it: IDLE and WALKING in M0; combat,
  with attack and attack-move, in M2 (CMB-02, ORD-04); gather and build in M3; repair in M4;
  fleeing and garrison in M5 (CMB-05, BLD-16); formation member in M10. The rest of the garrison
  (healing, ejection, capture regeneration) stays in M9 (BLD-10).
- **ADR-0016: solo-grade fog lands in M6 (D-24 (c)).**
  - M6 brings the per-faction vision grid with its 10-tick hysteresis (FOW-01), explored state and
    the shroud, `vision_delta` to the own faction, and solo fog gating (FOW-07): enemy bodies pass
    the existing `FogGate`, and enemy units outside vision are left out of `unit_summary`. Minimap
    fog (UI-16), an AI that reads only its own vision (AI-18) and the visibility options (MATCH-07)
    come with it.
  - Until M8 the 5-tick refresh is the zero-access-transformer fallback that ADR-0016 already keeps:
    the public `ServerChunkCache#move(viewer)` for each affected viewer, cheap with one human
    viewer. M6 needs no mixin and no access transformer.
  - M8 keeps team vision with targeted re-tracking through the two access-transformer lines
    (FOW-02), mirages and shared explored state (FOW-03), the leak audit (FOW-05) and fog-fair AI
    scouting (AI-07).
- **D-08's observer avatar**, where it touches the ADRs:
  - **ADR-0016.** The observer is not a vision source: it sees what its faction sees (FOW-01). It is
    a member of its faction, so `FogGate` answers for it as for its faction; it is not one of the
    match observers that see everything. It stands only in explored cells, so it can never scout,
    and from M7 the `ServerPlayer#broadcastToPlayer` mixin hides it from enemies while it stands in
    a cell they don't see, like any first-person body.
  - **ADR-0011.** Units never target the observer, and it cannot attack: `AttackEntityEvent`, posted
    from `Player#attack` (Player.java:1223), is cancelled for match bodies. Units hitting players or
    mobs through the target's own `hurt`, with the Strategos damage types tagged
    `minecraft:bypasses_cooldown` and the `LivingIncomingDamageEvent` handler, belong to CMB-08 and
    are deferred with it, inventory lines included. Unit-against-unit damage and vanilla sources
    hitting units (CMB-01) are unchanged.
  - **ADR-0015.** Units pass through the observer: it is not one of the non-reactive agents of
    ORCA (PATH-06).
  - If the owner later chooses the fighting avatar, CMB-08 brings these pieces back as ADR-0011 and
    ADR-0016 describe them, and the avatar becomes a vision source, an obstacle and a target.

## Consequences

- The ADRs agree with the approved roadmap. Each amended ADR's status points here, and so do notes
  under the status of the proposed ADR-0011 and ADR-0015.
- Later schedule moves change the roadmap and the architecture's milestone tags, not the ADRs.
- The MVP's fog relies on the gate and the declared audiences, refreshed by the fallback; the leak
  audit of every channel waits for M8, as D-24 (c) accepted.
- "Observer" now names two things: D-08's observer avatar, a faction member in first person, and the
  match observers of ADR-0016, who see everything. The architecture says "observer avatar" for the
  first.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Rewrite the milestone tags in the accepted ADRs | Accepted ADRs are not rewritten |
| One new ADR per amended ADR | Four ADRs with one cause, the roadmap's approval |
| Leave the tags and rely on the roadmap alone | ADR-0016's revealed-map MVP and ADR-0010's ticket rule would stay wrong in substance, not only in timing |
| Targeted re-tracking already in M6 | Two access-transformer lines before multiplayer needs them; with one human viewer the public fallback is cheap (FOW-07) |
| D-24's and D-08's other options | The owner decided both in the roadmap; their option tables are in M2's card file ([D-08](../roadmap/M2-command.md#d-08--first-person-avatar), [D-24](../roadmap/M2-command.md#d-24--exploration-in-the-mvp)) |
