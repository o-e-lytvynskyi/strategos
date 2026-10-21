# ADR-0014: Typed commands, server-side validation, 0 A.D. order queues and a hierarchical UnitAI

Date: 2026-10-21

## Status

Accepted; amended by [ADR-0023](0023-plan-alignment-after-roadmap-approval.md) (the UnitAI tree's
milestones: combat with attack and attack-move in M2, garrison in M5)

## Context

- **Networking is server-authoritative from day one** (owner decision). Clients may only send
  intents, and the server must validate them against a consistent state.
- **Prior art's failures:**
  - a 138-value action enum (RoN);
  - players identified by name;
  - broadcast-first networking;
  - group-only orders;
  - command range tied to the avatar ([research/README.md §5](../research/README.md)).
- **0 A.D.'s UnitAI design** ([research/mechanics.md §2.4](../research/mechanics.md)): a
  hierarchical state machine with an order queue and the queue operations `Replace` (default),
  `Add` (Shift), `PushFront` (interrupts) and `Finish`.
- **UnitAI is the most behaviour-heavy code.** Under ADR-0008 it lives in the core and is tested in
  L1.

## Decision

- **Commands are records** of a sealed `Command` type in `core.protocol`: `Move`, `AttackMove`,
  `Attack`, `Stop`, `Gather`, `Build`, `Repair`, `PlaceFoundation`, `Train`, `CancelTrain`,
  `Research`, `SetRally`, `SetStance`, `UseAbility`, `Delete`, with `Garrison` and `Patrol` later.
  - Each has a `CommandType` (wire id, wire codec, validator, handler) registered in a core
    `CommandRegistry` when a match core is built. There is no action enum and no central switch.
  - Abilities never add command types: `UseAbility` carries a data-defined ability id.
  - Units are named by core handles in a sorted `UnitSet` of at most 300.
- **One path for every source.** Human payloads, the AI's `CommandSink`, `/strategos order` and
  GameTests all produce the same records and pass the same validators. A command applies at the
  next tick boundary in canonical order.
- **Two validation stages:**
  1. **Gateway, between ticks:**
     - bounded decoding (≤ 32 commands and 4 KiB per batch, ≤ 300 handles per command);
     - token buckets (40 commands/s, bursts of 80);
     - the issuer is the slot bound to the sender's UUID.
  2. **Core, at the tick boundary:**
     - ownership, liveness, visible targets, bounds and explored area;
     - costs checked when spent;
     - one feedback record per command;
     - the same rejection reason for hidden and missing targets, so commands cannot probe the fog.
- **Order queues** of at most 16 orders per unit, with `REPLACE`, `QUEUE`, `PUSH_FRONT` and
  `finishOrder()`. Group commands fan out into one order per unit, with spread-slot destinations
  and a shared group id for shared path work. Rally points are queued orders for new units.
- **UnitAI** is a hierarchical state machine in `core.sim.unitai`:
  - states as a tree with `enter`, `leave`, `onTick` and typed message handlers, bubbling to
    parents;
  - deferred transitions;
  - plain-data state that saves as a state id plus a small record;
  - think periods per state.
- **The tree grows by milestone:** IDLE and WALKING in M0; combat, gather and build in M3; repair
  in M4; fleeing in M5; garrison in M9; formation member in M10.
- **Stances are data:** aggressive, defensive, stand ground, passive, violent.
- **Worker model.** Gathering and building are class-token abilities in templates, so the worker
  model (D-02) is a data choice.
- **Testing.** Every state transition has an L1 test, and a static check proves every state has an
  exit.

## Consequences

- A new command is a record plus a registration; a new ability is data.
- The AI cannot cheat through the command path: its commands meet the same checks as a human's.
- Behaviour bugs reproduce in L1 in milliseconds, from a seed or from a replayed journal.
- An order's visible response comes within one tick, through provisional steering, although path
  results arrive at `t+k` (ADR-0015).

## Alternatives considered

| Alternative | Why not |
|---|---|
| An action enum with a central switch (RoN) | The 138-value anti-pattern; every feature touches one file |
| A synced vanilla registry of command types (B) | Ties the command model to Minecraft types; the core registry is enough because commands never leave the mod |
| Commands naming units by vanilla entity id | Ids differ per session and come from a global counter, so they can be probed; core handles are stable within a match |
| Validation on arrival, between ticks | The state can change before the command applies; checking at the tick boundary is consistent |
| UnitAI states in Minecraft-typed code (B) | Testable only in GameTests; a rewrite later if behaviour moves to the core |
| Behaviour trees | No dormant-free library; the HFSM with an order queue is the proven RTS shape |
