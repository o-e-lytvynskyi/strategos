# RTS simulation: economy, building, economy AI

Pitfalls of the deterministic RTS core (this repository), found by AI-against-AI matches. For
movement and measurements in general, see [rts-sim.md](rts-sim.md).

## The ground for building rules ≠ the ground to stand on

- The "building" floor of a cell (the top surface of a column, water is not ground) counts berry
  bushes, lava and magma as ground. You cannot walk on them: a `HAZARD` surface is impassable,
  `startNode` = −1.
- Check passability for everything a unit is **placed** or **sent** to: builder slots, pushing out
  of a foundation, the exit point of a trained unit, the cargo drop-off point. Otherwise the unit
  stands on a bush, no path is built, and it is WALKING forever without progress.
- Height too: the top of a tree trunk is also a "floor", but 5 blocks above the building's base.
  Compare with the building floor (±2 blocks), as gather slots are compared with the node base.

## A building's footprint reaches the navigation grid with a delay

- The foundation columns are blocked at once, but the grid page is rebuilt once per `pageK` ticks
  (and in the WORLD phase, after movement). A unit that steps onto these columns in that window
  ends up on a column without surfaces: no path is built, and movement "without a surface" goes in
  a straight line, as in a scene without terrain. This is how a unit reached a slot inside a
  building and got stuck forever.
- Pushing out at placement does not help: it happens before the movement of the same tick. The
  reliable way is to move units off blocked columns to the nearest free ground every tick in WORLD.

## A target on a blocked cell: the nearest reachable one lies beyond the radius

- The geometric point "the cell at the building edge closest to the unit" may be a trunk. Pathfinding
  leads to the nearest reachable cell. If it is diagonal, the distance to the edge is 1.58 with a
  drop-off radius of 1.5, so the unit arrives, "cannot reach", and goes to the same place again. An
  endless loop without an error.
- Pick destination points among cells where one can stand, instead of fixing the radius.

## An order notice for an already empty queue (hierarchical state machine)

- `finishOrder` removes the order, moves to the next state and publishes `OrderReceived`. The
  transition is applied after the handler: entering a state can itself finish the order (building on
  a cancelled foundation). Then `OrderReceived` arrives when the queue is empty, `kind = NONE` →
  an exception. A queue size check right after `go()` does not catch it: the transition is not
  applied yet.
- Fix it at the receiver: a notice about an order that no longer exists starts nothing.

## The AI must see what the executor checks

- The free slots of a node in the AI view were all counted, but the gatherer takes only those where
  one can stand. The AI kept sending a worker to a "free" node, and the worker at once got "no
  node" and stood idle.
- One predicate for both sides (`GatherSlots.standable`), and the queries return already filtered
  data.

## An AI economy test: what to measure

- Worker shares by resource with 15 gatherers: one worker is 7 %. An instant sample breaks the 10 %
  tolerance every time two units change jobs. Measure the average over a minute (6 samples of 10 s).
- The watchdog "2 minutes without commands = hung" suits combat matches. An economy that saves 40
  wood per soldier on a meager income is legitimately silent for longer. Worker idling is caught by
  a separate metric (the longest idle time of one worker).
- The map decides: on the testing ground each base has a grove of 1 080 trees, and the rest of the
  forest is 180 blocks away, outside the territory. By the 8th minute the economy runs out of wood
  with any logic. First count the map's stocks by area, then tune the AI.

## Expansion by storehouses: territory rules first

- A building can be placed only within the center's radius (48 blocks). The logic "a storehouse at a
  distant resource" without this check aims beyond the territory: every attempt is refused, and the
  reserve "save for a storehouse" without a timeout holds the treasury forever, with no soldiers
  and no commands. Take candidates only within the radius of places.
- Decide on a storehouse in advance (the resource at the storehouses is below a threshold), while
  the near resource still feeds, or there is no money left for the storehouse. And do not let it
  override the build order if no place was found: otherwise the fields are not built, and food
  falls to zero.

## "No block" by the sign of the height breaks in a world below zero

- The node view returns the next block as (x, y, z), where x and z are local and y is the world
  height, and "−1 if there are no blocks". In the arena (a flat world, `minY` −60) the height of
  any block is negative: the filter `topY < 0` ("the node is empty") threw out all nodes. The AI
  and the scene of the perf run sent no workers anywhere, and L1 (a strip from 0) did not see it.
  The M3 perf baseline was taken this way, with idle workers.
- The sign that "a block remains" is the counters (`gone < blocks`), not the sign of a coordinate.
  And test the AI with at least one L1 test with a strip below zero.
