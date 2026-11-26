# RTS simulation in Java: avoidance, crowd arrival, formations, the stuck ladder

Facts from this repository: the deterministic core `core/sim`, M1 T20–T23. Minecraft has nothing
to do with it here. This is about ORCA, unit crowds and formations; measuring the core (JMH,
allocations) is in [rts-sim-perf.md](rts-sim-perf.md). The mod toolchain is in
[minecraft-mod.md](minecraft-mod.md).

## ORCA: a pair exactly head-on slows down to zero

- Two agents on one line toward each other do not part under pure ORCA (RVO2). The "cutting
  circle" line is then perpendicular to their path and limits only the longitudinal speed. Both
  slow down and stop at a distance equal to the sum of the radii.
- The RVO2 examples break the symmetry with random noise in the preferred velocity. A
  deterministic simulation cannot do that.
- The fix: if some line constrained the result (it is ≠ the preferred velocity), solve again with
  the preferred velocity rotated by 0.001 rad, to the same side for everyone. Pairs part in ideal
  time, asymmetric cases do not change. A rotation of 0.05 rad or more already sends units
  sideways for no reason.
- A pure solver test catches it: two agents from (0, 0) and (10, 0) toward each other, each
  steering to its own goal point. Both must arrive.

## Avoidance and arrival at one point

- With avoidance toward a common point, one unit stops and the others push in WALKING forever. In
  Strategos this broke 52 GameTests: patrols and "five units to a point".
- What worked while there are no group slots. A unit that has not progressed for 10 ticks arrives
  where it stands, if:
  - the point is taken by a standing unit or a stuck one with the same goal, and the point is no
    more than 1.5 blocks away;
  - it touches an already arrived unit with the same goal, so the crowd settles in layers
    outward.
- What broke:
  - the tolerance "arrived within 0.5 of the goal if avoidance pushed it off" seats units beside
    the slot and in a tight formation locks in those who go deeper;
  - "a stuck walker takes the point" without the condition "same goal": in a formation a foreign
    walker "takes" a slot, and the owner sits beside it.
- A formation test: the slot step is ≥ two radii plus a passage (here 1.5 at radius 0.35; with
  step 1 the gap is 0.3 and the first arrivals wall in the rest). The goals are a shift of the
  formation, not a mirror: the first arrivals take the far places.
- A path from one point (the goal is visible in a straight line): avoidance pushes the unit out of
  the passage, the straight line hits the wall, and the unit presses into it. A path re-request is
  needed: here every 20 ticks without progress the visibility of the point the unit steers to is
  checked.

## Standing units in ORCA: who yields to whom

- Standing units plan less often than walkers (here once per 4 ticks). A walker that counts on
  their "half of the maneuver" gets stuck in a standing crowd: a walker against a standing unit
  takes the whole maneuver.
- A standing unit that sees only the walker's *velocity* does not yield to one stuck in front of
  it: the velocity is zero, a deadlock. A standing unit must avoid the walker's *intention*, its
  preferred step in this tick. For that, first compute the steps of all walkers, then plan all
  units, in the same tick, with no memory between ticks (otherwise a restore from a snapshot
  diverges from the original run).
- Two standing units that settled slightly closer than the sum of the radii push each other
  forever, and the group "trembles". Standing units get their own smaller gap among themselves
  (here 0.6 for a sum of 0.7).

## Group formation: places and fill order

- A place step of "2 radii + 0.1" leaves 0.1 between standing units: a unit whose place is
  surrounded by earlier arrivals cannot get to it. A passage is needed: 4 radii plus a gap.
- Fill front to back: the front units go to the far places, by columns across the path. Swapping
  places for non-crossing paths (as in the matching problem) and filling by rows break this order
  and lock someone in again. Check on a dozen different starting layouts.
- Places beyond the map edge (a match without terrain, a goal at the edge) are pulled inward.
- The check "everyone within N blocks of the goal" for a group with places is flaky: the far place
  of a 3×3 formation is 2.12 from the goal, a group member stops 1.5 from its place, and the latch
  holds it within 1.0. Measure the distance to its own place (≤ 2.5). A random match seed in the
  smoke test gave about 1 failure in 40.
- How to tell an old flake from a new regression: run the same scenario in the core on hundreds of
  seeds, on the current code and on HEAD (`git worktree add --detach`). The same seeds with the
  same coordinates mean the flake is old.

## A large formation (200): swapping places and passing points in a crowd

- 200 units into a 15×14 formation at once: the crowd pushes a unit past its place and it cannot
  fight back, 130+ ticks without progress. The fix: a participant stuck for ≥ 10 ticks takes the
  place of a still-walking comrade within 1.5 blocks of it and sits down. The comrade gets its
  place and a new path. The formation does not care whose place is whose. Result: 57 ticks at most.
- Pitfalls of the swap: (1) the seated unit's "goal" in the movement state is still the old place.
  A partner that touches it triggers the rule "touches an arrived unit with the same goal" and
  sits 2 blocks from its place. Rewrite the seated unit's goal. (2) With two-pass movement the
  partner's step for this tick is already planned to the given-away place, so do not step it in
  this tick. (3) Tests that compare against the original place must track swaps by the change of
  orders.
- An intermediate path point in a crowd: count it as reached within 2 blocks (not 0.5) after 10
  ticks without progress, if the next one is visible in a straight line. Repeat requests for 200
  units dropped by half.
- Lanes of a shared path collapse at walls (a shift into the wall is impossible). Try half and a
  quarter of the offset before standing on the path axis.
- Standing units approach their gap asymptotically: check the gap after ~60 ticks of rest and with
  a tolerance of 1e-3, or the test catches 0.5999999.

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

## Vision: COMMANDS phase runs before WORLD

- `VisionSystem` runs in `Phase.WORLD`; command resolution is in `Phase.COMMANDS`, which runs
  first. On any tick, commands see the **previous** tick's vision data. On the very first tick,
  no vision data exists — all enemies are invisible to every issuer.
- Tests that resolve a cross-faction command must step the match at least once before submitting
  the command, so `VisionSystem` has stamped the grids and updated visibility. An instance
  initializer `{ h.step(); }` after the spawn fields is the lightest fix. The twin match (for
  hash equality) needs the same number of ticks.
- A faction with no units has an empty grid: every enemy is invisible to it. Tests where a
  faction without units issues a command on another faction's unit will get `UNKNOWN_TARGET`, not
  `NOT_OWNER`. Spawn an observer unit for that faction to give it vision.

## A wall of trees: one node, and slots behind it

Logs that touch form one tree node, whose work slots ring the whole mass. M6 T12.2: a forest
line built as a solid wall of logs gave a worker a slot on the far face, and it walked round
through a gap, about 110 blocks; oaks spaced on both faces did the same, because the computer
picks the tree nearest in a straight line, not along a path. `SimHarness`'s invariant ("WALKING
without progress toward … for 641 ticks") caught both in a headless match. A line that units must
go round keeps its fellable trees on one side only, the side its users come from, and blocks the
rest with leaves (an obstacle, never a floor).
