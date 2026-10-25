# ADR-0025: Commands into unexplored cells

Date: 2026-10-25

## Status

Proposed (the M6 spec, [M6-first-skirmish.md](../milestones/M6-first-skirmish.md) §1.6 row 2). Accepted on the
owner's word; it amends ADR-0014's validation list and 04 §7.3 when M6 T6 lands.

## Context

- ADR-0014 and 04 §7.3 validate "ownership, liveness, visible targets, bounds and explored area" for every
  command: a position must lie in the match space and in explored area.
- Until M6 nothing was unexplored: the three explored checks (`Placing`, `ClientPlacement`,
  `CommanderService`) return true. Solo fog (FOW-01, FOW-07, D-24 (c)) starts a match in the dark.
- Scouting is the point of solo fog: the player sends the horseman into the black, and the AI attack-moves
  to the enemy start it cannot see (AI-18). Under the explored-area rule neither order is valid.
- What must stay closed: building where the faction has never looked, gathering from a node the faction has
  never seen, and the observer avatar standing on unseen ground (ADR-0023, D-08).

## Decision

- **Movement positions may be unexplored.** `Move`, `AttackMove` and rally points (`SetRally`) need a position
  inside the match space only.
- **Explored cells are still required** for placement (`PlaceBuilding`, checked by `Placing` on the server and
  `ClientPlacement` for the ghost), for node targets (`Gather`, refused with `NOT_EXPLORED`), and for the
  observer's footing (`CommanderService`).
- **Units as targets** are governed by visibility, not by exploration: a hidden enemy is `UNKNOWN_TARGET`
  (FOW-01), as before.

## Consequences

- 04 §7.3 and ADR-0014's list are amended by M6 T6, which wires the three checks to the explored bits.
- A move into the dark reveals nothing about what is there: the order is accepted the same whether the cell
  holds an enemy or not, so the rejection reasons stay uniform (04 §10.5).
- Placement in the dark is refused with `NOT_EXPLORED`, which the command card shows as its reason.

## Alternatives

- **Keep explored area for every command:** scouting would need moves to the edge of the explored land, one
  step at a time; the AI could not attack an enemy it has not seen. Rejected.
- **Drop the explored rule entirely:** placement and gathering in unseen land would leak the map and let a
  player build where they have never been. Rejected.
