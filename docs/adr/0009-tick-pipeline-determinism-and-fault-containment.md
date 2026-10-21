# ADR-0009: Tick pipeline, determinism boundary and fault containment

Date: 2026-10-21

## Status

Accepted; amended by [ADR-0023](0023-plan-alignment-after-roadmap-approval.md) (the production
journal ring arrives in M4, with MATCH-04)

## Context

- **Tick order, verified in the 1.21.1 sources.** `MinecraftServer#tickChildren` posts
  `LevelTickEvent.Pre`, runs `ServerLevel#tick` and posts `.Post` (MinecraftServer.java:1049–1058).
  Inside the level tick, the chunk source runs the entity tracker (`ChunkMap#tick`) before the
  entity loop (ServerLevel.java:382 against 403). So poses written in `Pre` ship the same tick.
- **Nothing catches our exceptions.** An exception in an event handler propagates out of
  `tickChildren`. An exception in an entity tick crashes the server, unless
  `removeErroringEntities` (default false) silently discards the entity
  (Level.java:607–619).
- **The core's determinism is a testing tool** (ADR-0006, ADR-0008). Research contradiction 4 asks
  whether async path results can keep seeded replays deterministic and where determinism stops.
- **A slow host must not change outcomes.** Budgets that change behaviour must be counts, never
  milliseconds.

## Decision

- **Placement.** Each match's core steps in `LevelTickEvent.Pre` of its level, in `MatchId` order,
  only while `tickRateManager().runsNormally()`. `/tick freeze` and `/tick step` therefore work,
  and the integrated server's pause stops the match.
- **The driving port.** `MatchCore.step(TickInputs) → TickOutputs`, with
  `TickInputs(tick, List<InputEvent>)` and `TickOutputs(BodyFrame, List<OutputEvent>)` over sealed
  event types. Each input type has exactly one handler and each output type one applier, both
  checked by tests. A feature adds a record type, never a field.
- **Nine fixed phases:** `INGEST`, `COMMANDS`, `AI`, `UNIT_AI`, `MOVEMENT`, `COMBAT`, `ECONOMY`,
  `WORLD`, `FINAL`. Cross-system messages go through a typed, synchronous per-match bus with
  deterministic dispatch and no recursion. Slow systems declare cadences and offsets
  ([04-architecture.md §4](../04-architecture.md)).
- **Every cap counts work** (thinks, nodes, cells, commands, placements). Milliseconds are measured
  and reported, never used to decide. Caps live in `MatchRules`, and changing them mid-match is a
  journaled input.
- **Determinism rules** in `core.sim`, `core.content` and `core.protocol`, enforced by ArchUnit
  (rule B4):
  - no wall clock;
  - no `java.util.Random` or `Math.random`;
  - transcendental functions only through `StrictMath`;
  - no hash-ordered iteration (`HashMap`, `HashSet`, `Set.of`, `Map.of`, the `toSet`, `toMap` and
    `groupingBy` collectors, and object-keyed fastutil hash collections);
  - no threads.

  Positions are match-local `double`s, and accumulators are fixed point. Randomness is a stateless
  counter-based function `Rng.u64(seed, stream, tick, key, salt)`. Entities iterate in handle
  order.
- **Where determinism stops: at the port.** Human commands, player and vanilla block edits, vanilla
  damage, first-person positions, `PHYSICS` poses and content swaps enter as journaled inputs.
  Vanilla entity ids and network and chunk-load timing are never read by the core.
- **Async jobs** (paths, page builds, AI searches) are pure functions of immutable snapshots,
  delivered at a fixed tick. The delivery modes are specified in ADR-0015.
- **The journal** records each tick's inputs from M0, in memory for the L1 harness and GameTests,
  and binary from M1. `ReplayRunner` replays a journal in JUnit and reports the first divergent
  tick. Every behaviour GameTest exports its journal, and an L1 test replays it to the same hash.
  Production keeps a 10-minute ring buffer from M6.
- **Fault containment.**
  - `FaultBarrier` wraps every piece of match code Minecraft calls: event handlers, output
    appliers, payload handlers, `UnitEntity` overrides and protection handlers.
  - An exception moves the match to `FAULTED`: it stops stepping, its bodies freeze, the snapshot,
    journal and a fault report are written, and the server keeps running.
  - `FogGate` fails closed.
  - Dev and GameTest runs rethrow after recording.

## Consequences

- Poses ship the same tick they are computed. A command received between ticks applies at the
  next tick boundary, which keeps order latency within RTT plus two ticks.
- Whole-match replays are bit-exact given the journal. A canary (spike S6) runs the same match
  twice, with 0, 1 and 4 threads, on macOS and Linux.
- The determinism discipline has a cost. Some convenient Java APIs are banned, and identity-hash
  keys need care in review; the canary and the cross-level check catch what the rules miss.
- One match's bug cannot take down a multiplayer server or the other matches. A GameTest injects
  a fault in M0 and proves it.
- Work caps can make a slow host lag in wall time (lower TPS), but never diverge in outcome.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Units tick in the vanilla entity loop, match systems around it (B) | The tracker then ships each tick's moves a tick late; behaviour runs in vanilla's entity order, not a deterministic one; exceptions crash the server through `guardEntityTick` |
| Fifteen phases with a timer wheel and per-phase budgets up front (C) | Scale machinery before any measurement; the timer wheel stays a reserve behind the `Scheduler` |
| Time-sliced budgets ("stop after 2 ms") | Outcomes would depend on CPU speed, so replays and GameTests would diverge between machines |
| No fault containment, relying on `removeErroringEntities` | One exception crashes a four-player session, or silently deletes a unit |
| Stateful per-stream RNGs | Adding a draw shifts later values, and the state must be saved; the counter-based function has neither problem |
