# RTS simulation in Java: the stuck ladder, the net-progress backstop, stepping past corners

Facts from this repository: the deterministic core `core/sim`, M1 onwards. What counts as progress for the stuck
ladder and its backstop, and the step rules that let a unit get past a corner. Avoidance, crowd arrival and
formations are in [rts-sim.md](rts-sim.md).

## The stuck ladder: what counts as progress

- A "ticks without progress" counter that resets on any change of the point the unit steers to
  breaks the ladder. The first rung is a path re-request, the new path gives a new first point,
  the counter goes to zero, and the unit sits on the first rung forever.
- Progress is only getting closer to the point by a noticeable amount (here 0.05 blocks from the
  best) or passing a path point. A new route is not progress, and a nudge of the ladder is not
  either (the best distance is rewritten after a nudge). A new order and a stop reset the counter
  explicitly.
- The check: for an immobile unit the counter grows by exactly 1 every tick through a route change
  and a nudge.
- "Walled in with no exit" in a core test: walls from the terrain give "no path", and the move
  ends in place (not a failure). A ring of units is passed through on the "no avoidance" rung,
  because units are not solid to each other. Only a unit with speed 0 (a modifier) has no exit on
  any rung.

- A bot that sends the same order to a unit on every think resets its stuck count each time (a new
  order resets it), so a unit held up in a crowd (a one-block lane of burners round a centre, two
  swapped head-on) never reaches the ladder's end and the invariant "WALKING without progress for
  641 ticks" fires, though the ladder is fine. Send an order only to units not already on it
  (`MilitaryManager.pushFogMarch`, M6 T21; `FieldWaveTest`).
- "Passing a path point" is progress only if the route goes on. A boxed-in unit is given a truncated
  one-point route at its own feet on every re-request; passing that point at once zeroed the count, so
  the ladder never left its first rung and the same invariant fired (M10 T25, horsemen boxed in at
  their base). Passing the last point of a route keeps the count (`MotionMoves.passedLast`,
  `StuckLadderTest.aTruncatedRouteAtTheUnitsFeetDoesNotRestartTheCount`).
- A backstop that ends an order before the invariant fires must restart its count exactly where the
  invariant does, or it runs out later than the invariant. QA-12's net-progress count restarted only
  after 8 blocks of displacement, while the invariant restarts on any strictly nearer distance to
  the target: units creeping into a crowd at one front point were failed 276 ticks too late (M10,
  long AI matches). It now keeps the best squared distance and the target it was taken against
  (both hashed while it runs), counts on landing ticks too, and a new order to the same place keeps
  the count (an AI alternating Burn and Capture on one building restarted it every swap).
- The rule that checks a leg and the rule that steps along it must cross the same columns. The line-of-walk check
  crosses one column edge at a time, so it passes a leg that runs a hair from a corner. A 0.25-block step that crossed
  both edges in one tick was refused as a diagonal and slid on one axis into a pocket beside the leg. That corner had
  both sides linked and only the diagonal refused (a one-block rise whose diagonal lacked the clearance). The corner
  rule pulled the walker back a tick at a time, never far enough. Every re-route began at its own cell centre, and
  nearing that point counted as progress, so the ladder never left its first rung. The backstop gave the order up
  after 620 ticks (M10 T29, `crowd_soak_600`). Such a step now walks its segment as the check does (`MotionMoves.tryMove`,
  `RampCornerTest`). Applied at wall corners too, the same rule changed every crowd's path through a gap and moved the
  golden hashes, and a unit settled 1.01 blocks off a 1.0 tolerance. Keep a wall's corner refused.
- To find a given-up order in a perf run, replay the scene's `.strj` in a scratch core test (`KernelAccess.state`).
  Stop at the unit whose net count reaches `NET_FAIL`, then print its steering point, path cursor and the grid's
  links around it.
