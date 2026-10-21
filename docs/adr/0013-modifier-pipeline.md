# ADR-0013: The core modifier pipeline is the single source of every stat

Date: 2026-10-21

## Status

Accepted

## Context

- **Research contradiction 2:** one report recommended unit stats as registered, syncable vanilla
  `Attributes` (RoN's design); others computed them through a Minecraft-free modifier pipeline
  (0 A.D.'s design).
- **What 0 A.D.'s pipeline does well** ([research/mechanics.md §2.3](../research/mechanics.md)):
  technologies, auras, ranks, civilisation bonuses and AI handicaps all modify stats by value path,
  filtered by class tokens, with cached results invalidated by messages.
- **Data-defined resources and abilities need stat paths that no static attribute registry can
  have.**
- **Vanilla attribute sync** would put a second copy of every stat on every body and bypass the
  fog-aware sender.
- **Determinism.** Floating-point sums and products depend on order, so a pipeline that must be
  deterministic needs a canonical order.

## Decision

- **Value paths.** Every number a unit, building or faction uses is addressed by a string path
  (`Attack/Melee/Damage/Hack`, `ResourceGatherer/Rates/wood.tree`, `Cost/Resources/food`),
  interned to an `int` id when templates compile.
- **Modifiers.** `Modifier(path, op, value, filter, stage, priority, sourceId)` with
  `op ∈ {REPLACE, MUL, ADD, TOKENS}`. `TOKENS` edits token lists (`+a -b`), so a technology can add
  a unit to a building's production list the same way it changes a number.
- **Evaluation per stage, in ascending order:**
  - `v = replace ?? v`, then `v × Π mul`, then `v + Σ add`, clamped to the path's declared range;
  - products and sums in canonical source-id order;
  - ties between replacements to the higher priority, then the lower source id.
- **Class filters** (`"Infantry+Melee !Champion"`) compile to must-have and must-not bitsets over
  interned tokens.
- **Scopes:**
  - faction: techs, civ and phase bonuses, AI handicaps, map modifiers;
  - unit: rank, status effects, ability buffs, auras, territory, garrison.
- **Caching.** A `StatBlock` per (template, faction) tagged with a modifier-set version, plus a
  per-unit overlay only for units with personal sources. Reads are an array index and a version
  compare.
- **Invalidation** posts `StatsChanged(scope, paths)` on the match bus. Max HP keeps the HP ratio,
  vision re-stamps, and attack timers rescale.
- **`StatBreakdown`** (base, each modifier with its source and stage, result) backs tooltips and
  `/strategos stats`.
- **Vanilla attributes are never a source.** If a `PHYSICS` body is ever built, `MOVEMENT_SPEED`
  and `STEP_HEIGHT` are write-only mirrors of the core value. The only vanilla number the core
  reads is a first-person player's attack damage, to size an external hit.
- **Clients.** They get effective stats through `unit_detail`. When the command card needs
  predictions, the same `StatService` runs on the client over its template copy and its own
  faction's research.

## Consequences

- One mechanism serves technologies, ranks, auras, civilisation bonuses and AI difficulty, and a
  new tech is data, not code. That avoids RoN's 52 hard-coded research classes.
- Tooltips show the server's numbers exactly, through the shared code or the detail payload.
- Potions and beacons do not affect unit stats, because units are not vanilla stat holders.
  Equivalent effects are data-defined statuses that apply modifiers.
- L1 table-driven tests fix the order of operations. A cached lookup must stay under 50 ns (JMH,
  DATA-02).

## Alternatives considered

| Alternative | Why not |
|---|---|
| Syncable vanilla attributes as the stat store (RoN) | The attribute registry is static, so data-defined resources and abilities can't be attributes; a second copy of every stat per body; sync bypasses the fog-aware sender; no class-filtered technology modifiers |
| Both: the pipeline writes attribute base values for the stats vanilla consumes (B) | Two read locations per stat and a per-path rule every change must respect; only needed with vanilla physics, which the kinematic body doesn't use |
| Hard-coded stat fields per unit class | Contradicts "content is data"; no technology system |
| Insertion-ordered evaluation | Floating-point results would depend on the order sources were added, which breaks determinism |
