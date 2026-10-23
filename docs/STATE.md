# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-23 · **Owner last seen:** 2026-10-23 · **Current:** M3 — started 2026-10-23 at the owner's word (without windows); M1 and M2 Done

## For the owner

Answer in chat or here; the next session applies the answers.

1. **Nothing is waiting from M0.** You accepted it on 2026-10-22 without playing the demo. The demo
   stays ready whenever you want it: `scripts/playtest.sh`, then the steps in
   [testing/playtest.md](testing/playtest.md). Its first use confirms ADR-0022, the one thing your
   acceptance left open.
2. **Your go-ahead for M2 (2026-10-23):** «когда закончишь М1 - приступай сразу к М2». Once T27
   closes M1, the M2 spec is written and its tasks start without waiting; M1's acceptance items
   for you (the demo, consent batches) are listed here and do not hold M2 up.
3. **Your consent for M1's tests (2026-10-22):** every kind of test, client windows included. With
   the lid closed and no display, a Mac window may not render, so client scenarios run on the
   Linux route on CI first; a Mac run is used only where a measurement needs this Mac.
   **And for today (2026-10-23):** «мак до вечера в твоем распоряжении, можешь делать все, что
   посчитаешь нужным, чтобы ускорить разработку, кроме субагентов» — this Mac, client windows
   included, was mine until the evening; no subagents. **From the evening of 2026-10-23: no windows**
   («пока без окон»); work that needs one is listed in item 6 and does not hold the rest up.
4. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
5. **Your decisions of 2026-10-23 (evening):** «Давай примем все решения что ты рекомендуешь и начнем м3 пока без
   окон, если работа встанет из-за того что тебе будут нужны окна - дай мне знать. По поводу решений с байтами,
   держим в голове, что можно это оптимизировать». Applied:
   - the block writer's limits gate (1.15 ms a tick at p99, a tenth at the median), under gate `c`;
   - PERF-14's baselines are this Mac's ([perf/client/m4pro.json](../perf/client/m4pro.json)); card PERF-14 done;
   - the goldens are approved but `pose_high` (see item 6);
   - the JMH baseline is the full run of 2026-10-23 (18 benchmarks, [perf/baseline-m4pro.json](../perf/baseline-m4pro.json));
   - QA-07.4 allows a kernel's events their messages: `OrcaBench` may spend 16 B a step on the stuck ladder's;
     an allocation-free bus stays a known follow-up, as you said;
   - gates `c` and `e` join `a`, `d` and `g`;
   - S10's footprint rule joins M1 as T29.
   The five far-off decisions (D-03, D-06, D-07, D-20, D-21) stay open: none is needed before M7, and two of them
   are about the licence and going public.
6. **Needs a window, when you have one (the work does not wait for it):** `pose_high`, the camera at its top height,
   shows the world's edge. The tilt I suggested would also hide the far markers, which need the view to reach
   160 blocks; the edge is more likely the scene's own: a fresh flat world still generating chunks when the camera
   has just risen. The fix is probably for the scene to wait for the ground, then a new `pose_high` on this Mac
   (CI's Linux one can follow headless). The camera stays as it is.
7. **For your information: the long-route planner (card PATH-08, HPA*) left the reserve and is built.**
   The roadmap had it waiting for one measurement, which T8 took: one unit's path across the
   384² proving ground needed about 30,000 search steps, over the 20,000 cap, and 4.5 ms of server
   time, over the 2 ms a tick the budget allows all pathfinding. T28 added it: the same crossing
   now takes about 2,800 steps and a fraction of a millisecond, and its route is within 1 % of the
   best one. Nothing to do; it is in the roadmap's rules ([M1 spec §1.6 D rows 42 and 47–53](milestones/M1-movement.md)).
8. **The M2 demo, when you have an hour (T22's last part, L7):** [milestones/M2-command.md §1.3](milestones/M2-command.md):
   `scripts/playtest.sh`, `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0`,
   `/strategos spawn strategos:unit/infantry 30 0` and `... 30 1`, then box-select, Ctrl+1, attack-move with Z and a
   right click, V into first person, R (follow), G on an enemy, V back; the questionnaire in
   `docs/testing/playtest.md`.
9. **The M1 demo, whenever you like (L7):** [milestones/M1-movement.md §1.3](milestones/M1-movement.md): an Arena
   world, `/strategos match create map=strategos:proving_ground`, `/strategos spawn strategos:unit/infantry 200`,
   F8 for the movement overlay, `/strategos order move @units waypoint=far_side`, then the questionnaire. (The
   infantry are spearmen since M2; 200 of one faction do not fight.) That session confirms ADR-0022.
10. **Optional reviews:**
   - the card refinements made after the approval
     ([design/roadmap-changes.md §8](design/roadmap-changes.md));
   - the promo draft ([promo/roadmap-announcement.md](promo/roadmap-announcement.md)).

## Since your last visit

- 2026-10-22 · **M1 T1 Binary journal:** every match's input journal is now a compact binary file in
  one-minute segments, each replayable on its own; `/strategos journal save <name>` writes one, and
  two golden journals pin the core's behaviour (a change fails until re-recorded with a reason).
  Card CORE-11 is done.
- 2026-10-22 · **M1 T2 Terrain pages:** every block state now has a navigation class, and 16 x 16
  pages of standable surfaces are built from them: slabs, stairs, carpets, snow, water, doors,
  bridges, wall walkways and tower floors, in 27 µs a page. Card PATH-02 is done.
- 2026-10-22 · **M1 T11 Template engine:** units are now defined in JSON with parents, mixins and
  edit operations; every mistake is reported with its file and field, all at once; 500 templates
  compile in 4 ms. `/strategos spawn <template> [count] [slot]` spawns any unit type. Card DATA-01
  is done.
- 2026-10-22 · **M1 T3 Terrain census (spike S8):** 11 vanilla terrain squares of 512² were measured
  against the surface pages. Four layers per column are enough: what the cap drops is only cave
  floors deep underground, never anything within 16 blocks of the surface. Pages take about 2 KB,
  and a 512² map needs at most 3.4 MB. One rule changes for the open world (M13): the height band
  must follow the terrain page by page, because one band per map missed up to 13 % of a hilly
  area. [Report](spikes/S8-terrain-census.md). Card PATH-01 is done.
- 2026-10-22 · **M1 T4 Arena world:** a new world type, "Strategos Arena" in Create World
  (`level-type=strategos:arena` on a dedicated server): an empty world that gets the arena rules
  once on its first start: no mob spawning, fire, griefing or weather, no random ticks, fixed noon.
  Changes you make to these rules later stay. Matches in it take their own slots 2,048 blocks apart
  and keep their area loaded until they end; if a server dies, the next start cleans up what a
  finished match left loaded. The lobby pad comes with the next task. Card MAP-07 is done.
- 2026-10-22 · **M1 T5 Block writer:** every block Strategos places or removes now goes through one
  writer with a shared budget of 64 blocks a tick. Building sites take turns and write in bursts,
  so a busy match never spikes the tick. A match's blocks are protected until it ends. A new arena
  world now gets its 9 x 9 stone lobby pad, once. Servers have a config file,
  `serverconfig/strategos-server.toml`. Card BLD-15 is half done: its last check, that the
  writer's own blocks never trigger navigation rebuilds, comes with the terrain capture in T7.
- 2026-10-22 · **M1 T6 Maps and the proving ground:** maps now ship as packages of vanilla
  structure tiles plus a manifest, and import block by block without stalling the server. The
  first map is the proving ground, 384 x 384, built to test movement: hills, ramps, a staircase, a
  cliff band, a river with two fords and two bridges, a forest, a narrow pass and a walled yard. A
  test checks that every point on it can be reached from the west start. It imports in 1–7 s, and
  its terrain is verified block for block by a hash. `/strategos match create map
  strategos:proving_ground` starts a match on it; `/strategos map export|import|list` copies
  regions as maps. Card MAP-01 is done.
- 2026-10-22 · **M1 T7 Live terrain:** the navigation grid now follows the world as it changes.
  Any block placed or broken in a match area by a player, an explosion or another mod rebuilds the
  affected 16 x 16 page within 2 seconds, never more than 4 pages a tick, at 0.25 ms. Strategos's
  own building never costs a rebuild. Every match builds all its pages when it starts (512 x 512 in
  about 1 s), and replays rebuild the same pages from their recorded terrain. Card BLD-15 is done;
  PATH-15 finishes when paths arrive in T8.
- 2026-10-22 · **M1 T8 Pathfinding:** units now find their way over real terrain: they climb steps
  of up to a block, drop up to three, wade fords, use doors and avoid deep water, lava and cliffs.
  A unit moves on the tick its order arrives: a short move with a clear line is decided at once,
  a longer one starts straight while its route is computed and switches to it two ticks later.
  When a wall goes up across a route, the units on it re-plan around it. A match without terrain
  behaves exactly as in M0. `/strategos nav show|path|rebuild` shows a page, a unit's route (with
  particles) and rebuilds the terrain. A server setting `journal = disk` now also writes each
  match's journal to the world folder. One measurement fired the long-route planner's trigger (see
  item 5 above). Cards PATH-03 and PATH-15 are done.
- 2026-10-22 · **M1 T19 Reachability:** the navigation grid now knows which ground connects to which. An
  order to a spot a unit cannot reach (inside a closed yard, across deep water, on top of a wall)
  sends it to the nearest point it can reach, found in well under a millisecond, instead of
  searching the whole map. Drops are one-way: a unit may jump down a two- or three-block ledge but
  not climb it, and the reachability rules know that. Rebuilding a page updates it within the same
  tick. Card PATH-04 is done. T19 was taken before T9 because T9's crossing tests need the
  long-route planner (T28), which builds on it.
- 2026-10-22 · **M1 T28 Long routes (HPA*):** a long order is now planned in two levels: first
  from one page edge crossing to the next across the map, then step by step inside each page. A
  unit crossing the whole proving ground gets its route in about 0.3 ms instead of 4.5 ms, using a
  tenth of the search, and the route is within 1 % of the best. When a wall goes up, the long
  routes know it as soon as its pages are rebuilt. A path that went stale while it was being
  computed is now checked when it arrives. Card PATH-08 is done.
- 2026-10-22 · **M1 T9 Motion on surfaces:** units now walk on the real ground. They climb a
  one-block step over two ticks, drop down ledges of up to three blocks half a block a tick, wade
  through shallow water at half speed, never step into lava, turn no faster than their template
  allows, and stand exactly on the floor the navigation grid sees; their bodies follow that height
  within a sixteenth of a block. Fifty units sent across the proving ground one after another all
  arrive within their ideal time (the slowest in 0.96 of it), none stuck. Moving 600 units costs
  about 24 µs a tick. A unit that has arrived holds its spot and walks back if pushed more than a
  block away. A match without terrain still moves exactly as in M0. Card PATH-05 is done.
- 2026-10-22 · **M1 T12 Modifier pipeline:** every number a unit uses now comes from one place
  that technologies, ranks, civilisation bonuses and AI handicaps will feed later: the template's
  value, changed by modifiers of the unit's faction and its own, which multiply, add or replace a
  value, or add and remove entries of a list (such as what a building can train), and can target
  classes of units ("Infantry+Melee", "not Champion"). The result never depends on the order the
  modifiers were added in, a read costs about 2 ns, and `/strategos stats <unit>` shows each value
  and how it came about. Movement already reads its speed and turn rate this way. Nothing in the
  game adds modifiers yet; the first technologies will. Card DATA-02 is done.
- 2026-10-22 · **M1 T10 Spike S10, how units sit on the ground:** units walking over slabs,
  stairs, snow, carpet, dirt paths, farmland and through a shallow pool stand exactly where a
  vanilla body would, and drop off a ledge half a block a tick. On a real client (the Linux route
  on CI, twice) the steps show as smooth ramps without jumps; frames are in the report. A player's
  punch and arrow on a unit now reach the game's core and are recorded, to be applied with combat in
  M2; a unit still takes no vanilla damage. One visual point is in "For the owner", item 6. Card
  UNIT-09 finishes with T21's check that units walk round players.
- 2026-10-22 · **M1 T13 Spatial index:** the game can now ask "which units are near this point" at
  once: units sit in a grid of 8-block squares rebuilt every tick after they move (about 5 µs for a
  thousand), and a thousand units each asking for the enemies within 16 blocks of them take 0.4 ms.
  Answers always come in the same order, and they match a slow full search on a thousand random
  tests. Avoidance (T21) is its first user; buildings, resource nodes and dropsites have their own
  index, empty until the economy arrives. Card CORE-08 is done.
- 2026-10-22 · **M1 T14 Network test kit:** tests can now seat a pretend player that sees exactly
  what a real client would, record every packet it gets, and count the bytes as the game's own
  encoder writes them, ours and vanilla's apart. A first measurement: one such viewer watching 600
  walking units receives 41 KB/s, under the 100 KB/s budget per player. The check against a real
  client's network recording waits for M2's consent batch, as planned. Card QA-19 is done.
- 2026-10-22 · **M1 T15.1 Payload layer:** the mod now has its own network messages: a player's
  commands, the match a player joined, the match's state, and feedback (a command's result, or a
  notice such as "unit stuck"). One gateway sends everything, only to the players each message is
  meant for, and only to clients that have the mod. Bad or flooding clients are cut off after 100
  invalid messages a minute. A unit's body now reaches players with the very position of that
  tick, and a walking unit sends no extra data: each new body used to send one redundant packet
  after appearing, now fixed. Card UNIT-01, left open by M0, is done; T15.2 seats players in
  matches next.
- 2026-10-22 · **M1 T15.2 Seats in a match:** a player can now take a seat in a match with
  `/strategos match join <id> <slot> [player]`, one player to a slot. The seat is saved with the
  match and outlives a logout: a returning player is told again which match it sits in. A seated
  player's commands act for its own faction only, and each gets an answer; a player without a seat
  is ignored. Units are shown only to their own match's players and to players in no match, such
  as an operator watching: a member of another match standing next to them sees nothing of them.
  Card NET-01 is done.
- 2026-10-23 · **M1 T21 Units walk round each other:** units now step round one another, round
  players and round mobs instead of walking through them. Two groups of 50 sent head-on through a
  6-block gap in a wall both get through, and no two units ever stand inside each other; a unit
  standing still steps aside for one walking past; a unit pushed behind a wall asks for a new
  route. A group sent to one point settles around it rather than piling onto it (each unit gets a
  place of its own when group orders arrive in T20). A player of the match itself is its observer
  and is walked through, as decided in D-08. It costs under 1 µs a unit per tick (the budget is 5).
  The avoidance adapts the open-source RVO2 library (Apache-2.0), credited in the new `NOTICE`
  file. Cards PATH-06 and UNIT-09 are done, and ADR-0011, how unit bodies work, is now accepted:
  no unit type needs vanilla physics.
- 2026-10-23 · **M1 T17 Unit behaviour as a state machine:** each unit's behaviour now runs as a
  small tree of states (idle and walking for now) that later units' work, combat, gathering and
  building, plugs into without rewriting what exists. A check proves every state has a way back to
  idle, and a unit's state is saved with the match. Nothing changes in play yet: the same matches
  replay to the same results. Card UNIT-02 is done.
- 2026-10-23 · **M1 T18 Order queues:** a unit now holds up to 16 orders. A new order replaces the
  old ones, a queued one (Shift, later in the interface) waits its turn, and an urgent one goes
  first and lets the interrupted one resume; Stop clears them all. An order to several units
  marks them as one group, for the group places and shared paths coming next. Server commands
  `/strategos order move @units <x> <z> [queue]`, `... waypoint <name>` and `/strategos order stop`
  give orders by hand, the way a player's client will. Card ORD-02 is done.
- 2026-10-23 · **M1 T25 Movement debug overlay:** an operator can press F8 in a dev client to see
  the walkable surfaces, the connected regions, each unit's path and a marker per unit, and a panel
  with the path queue, its latency and the server's time per phase; the server sends these only to
  operators who ask, and never to anyone else. `/strategos perf start|stop|dump` records a match's
  timings to a CSV file. The overlay itself is checked on screen in M2's consent batch. Card
  PATH-10 is done.
- 2026-10-23 · **M1 T20 Group places:** an order to several units now gives each its own place in a
  block of rows round the target, facing the way the group came, 1.5 blocks apart for today's units
  so latecomers can still walk between the first to arrive; the places snap onto walkable ground
  reachable from the target. Fifty units sent together settle in their places from every one of
  sixteen starting scatters tried. Units standing still now step aside for one that wants to pass,
  even if it has stopped in front of them, and a settled group no longer shuffles. Card ORD-03 is
  done.
- 2026-10-23 · **M1 T23 Stuck ladder:** a unit that makes no progress now works its way out in
  steps: after 2 seconds it asks for a new route, after 5 it walks to the nearest free spot close by,
  after 10 it walks its route for 3 seconds ignoring other units, after 20 it is nudged up to 4
  blocks along its route, and after 30 its order fails and its player gets a "stuck" notice. A
  unit walled in by other units walks out on the third step; one penned in by blocks mid-walk
  finds the opening. Nothing ever teleports or leaves the match. The overlay shows each unit's
  step. Card PATH-07 is done.
- 2026-10-23 · **M1 T22 Path threads:** paths are now searched on up to four background threads,
  and a match still plays exactly the same on any number of them: one match gives the same result
  on 0, 1 and 4 threads, and a save taken while searches run continues exactly. Spike S7 fired 300
  path requests at once on the proving ground: the server thread spends at most 0.6 ms a tick on
  paths, even on a deliberately overloaded machine, and a 100-unit order queued behind 200 others
  gets its paths within 4 ticks (0.2 s). It set how many searches start each tick (80), and the
  pathfinding decision, ADR-0015, is now accepted. Card PATH-16 is done.
- 2026-10-23 · **M1 T24 Shared group paths:** an order to a group of eight or more now plans one
  path for all of them, so 150 units make one search instead of 150; they walk it side by side in
  lanes matching their places, closing up through narrow ways, and spread to their places near the
  goal. 200 units crossed the whole proving ground as one order, the slowest in 1.21 times the
  time it would take alone, and none got stuck long enough to need more than a new path: units in
  a crowded formation now simply swap places instead of pushing toward their own. Card PATH-09 is
  done.
- 2026-10-23 · **M1 T16 Content from data packs:** unit types now load from data packs, and
  `/reload` changes them in a running world: units already walking move at a new speed from the
  next tick. A reload with a mistake in it keeps the old units and lists every mistake with its
  file and field, in the log, to operators online and in `/strategos templates errors`; one that
  would remove a unit type still on the field is refused with its name. Every player gets the unit
  types when joining, before seeing any unit, and again after each reload; a client that got a
  different set asks for it again. Spike S12 timed `/reload` with twenty test unit types at
  0.26–0.33 s against a 2 s target (the unit types themselves take 2 ms of it), and each of twelve
  planted mistakes was reported at its file and field. [Report](spikes/S12-content-cycle.md).
  ADR-0012, how content loads, is confirmed. Card DATA-03 is done.
- 2026-10-23 · **M1 T26 Crowd soak, the M1 performance gate:** 600 units on the proving ground took
  random group orders for ten real minutes (18,437 unit orders): none failed, none needed the
  ladder's nudge, and no unit stood more than 11 seconds without progress. Every budget held with
  room to spare: our core 1.6 ms a tick (limit 5), everything Strategos adds 4.7 ms (17), the whole
  server tick 7.3 ms (23), pathfinding on the main thread 0.34 ms (2), 3.3 KB/s per watching player
  (100), navigation memory 2.9 MB (32). 1,200 units kept the tick at 9.4 ms (limit 50). The soak
  runs in `verifyPerf` (about 15 minutes, paced like a real server) and its order checks in
  `verifySlow` (40 s); the nightly CI run does the first 2 minutes. Budgets B–E only report for now:
  the rule wants five steady runs first, and two of mine were lost; the rest come with T27. Card
  PERF-05 is done.
- 2026-10-23 · **M2 T1 Spike S9, the RTS camera on a dedicated server** (on your Mac, in its
  window): the planned camera works. A real client panned across a 384² match with 1,000 units and
  was jumped 300 blocks twenty times: no rubber-banding while panning at 10–100 blocks a second, the
  view and every unit in it were there 0.3 s after each jump, a mouse click picked exactly the
  intended block on the Retina screen every time, and the server work per commander stayed at a
  fifth of a millisecond. With Sodium and Iris the same, except that panning at 160 blocks a
  second sometimes snapped back, so the camera's top speed is 100 blocks a second. Two rules came
  out of it for the next tasks: jumps take effect at the start of the next server tick, and pans
  stop at 5 blocks a tick. ADR-0017, the camera decision, is accepted.
  [Report](spikes/S9-commander-camera.md). Card CAM-01 is done.
- 2026-10-23 · **M2 T3–T5 RTS controls, the camera rig and the RTS screen:** a spectator presses F6
  and gets the RTS view: the cursor is free, the camera pans on W A S D and at the screen's edge,
  turns on comma and period or a middle drag, and zooms on the wheel, smoothly at any frame rate.
  Clicks pick exactly the block or unit under the cursor on the Retina screen; chat (T) and the pause
  menu (Escape) open over it and it comes back when they close; no click ever breaks a block or
  hits anything. Every RTS key can be rebound in vanilla's Controls screen without a conflict mark;
  speeds and edge panning are in `strategos-client.toml`. Checked in a window on your Mac. Selecting,
  ordering and being seated as a commander come next. Cards UI-01 and CAM-04 are done; CAM-02 waits
  for its golden pictures and the jumps.
- 2026-10-23 · **M2 T7, T11, T12 Selection, control groups, outlines:** in the RTS view a click
  selects one of your units, a drag boxes many, Shift adds or removes, a double click takes every unit
  of that type on screen; an enemy can be clicked to look at but never joins; at most 300. Ctrl+1–9
  keeps a group, the digit recalls it, a double tap brings the camera there. Selected units get an
  outline in their faction's colour (blue for you; it reads rather dark, see the pictures in the
  consent batch), and the "Unit" labels over every head are gone. Checked on your Mac with 150 units
  and 20 enemies. Card UI-03 is done; UI-04 and UNIT-03 wait for first person and far markers.
- 2026-10-23 · **M2 T13 Orders from the RTS view:** hold the right button over the ground and blue
  dots show where each selected unit will stand, in rows facing the way they will walk; release and
  they go; with Shift the order waits behind the current one, and a marker stays on the spot. Checked
  on your Mac with 150 units, which all got the order. Turning the formation by dragging and path
  lines come later. Card UI-05 is done.
- 2026-10-23 · **M2 T9 The commander on the server:** `/strategos match join <id> <slot>` now makes
  you the match's commander: a spectator above the field, straight in the RTS view, and when the
  match ends you get back your game mode and the place you came from, even after a restart. Jumping
  the camera far (a group's double tap) goes through the server, which places you there at the start
  of its next tick; units in the new view appear at once even if the camera stands still; and units
  walk through the commander as if it were not there. A 10-minute pan with 20 jumps drew not one
  rubber-band. Card CAM-03 is done.
- 2026-10-23 · **M2 T21 Client frame times in the RTS view** (on your Mac): 200 units walking under
  the RTS camera at render distance 16, a hundred of them selected and outlined. Without the
  performance mods a frame takes 2.6 ms (5.7 ms at p99), the units 1.4 ms of it, our outline and
  markers 0.5 ms, and the client tick 0.8 ms; with Sodium and Iris 1.6 ms (4.4 ms at p99), 0.3, 0.6
  and 0.8 ms. Every number is well inside the card's budgets of 16.7 ms, 25 ms, 6 ms, 3 ms and 1 ms
  ([archive](../perf/client/m4pro.json)). The HUD and health bars are not there yet, so the scene
  runs again with them in M2's consent batch. Card PERF-14 is in progress.
- 2026-10-23 · **M2 T8 Pictures that catch changes:** every client scenario's frames are now compared
  with approved pictures of their platform, this Mac's and CI's Linux renderer, and a changed
  picture fails the run; CI proves it each time by spoiling a copy. A fixed scene with the camera
  at three poses and a selected group outlined repeats to the pixel on both platforms. The
  pictures wait for your yes (below). Card QA-18 is done.
- 2026-10-23 · **M2 T6 The RTS HUD:** in the RTS view the survival bars, hotbar and crosshair are
  gone; a panel at the bottom says how many units are selected and of which types, a bar above it
  shows your control groups, and hovering a unit names it with its faction's colour. The layout
  holds every window size and GUI scale tested, and the HUD costs well under a tenth of a
  millisecond a frame on this Mac. Card UI-02 is done.
- 2026-10-23 · **M2 T10 Armies never vanish:** every player now gets, twice a second, where
  all the units it knows stand, its own and the enemies it sees (all of them until fog). Units too
  far away for the game to show their bodies are drawn as squares of their colour on the ground, so
  zooming out never hides an army. Each player also gets the hit points of the units it sees when
  they change, and the details of its selection. Cards UNIT-03 and NET-08 are done.
- 2026-10-23 · **M2 T14 Units can be hurt and die:** each unit now has hit points (100 by default)
  and resistances to hack, pierce and crush damage; a player's blow, an arrow, an explosion or
  `/kill` takes them off in the match's own rules, and the body flashes red. A unit that dies falls
  as a vanilla mob does and disappears, and its owner is told. Units hitting units come with melee,
  the next task. Card CMB-01 is in progress.
- 2026-10-23 · **M2 T19 First person:** press V in the RTS view and you stand where the camera
  looked, in adventure mode, as an observer: nothing hurts you, you cannot hit units or use blocks,
  and units walk through you, so you can never wall a gate. G sends your selection to where you
  look; F6 takes you back to the RTS view in a tenth of a second, selection and control groups as
  they were. Cards CAM-05 and UI-04 are done.
- 2026-10-23 · **Three items of M2's consent batch, early, with today's Mac:** the network byte
  counter that the headless tests use matches a real client's own measurement to 0.006 % (M1's
  QA-19.4); the movement overlay has its pictures too (M1's PATH-10.3); and the RTS scenarios pass
  with Sodium and Iris.
- 2026-10-23 · **M2 T17 Health bars:** in the RTS view every selected unit, and every hurt one, has
  a bar over its head that runs from green to red as it loses hit points; F1 hides them with the
  rest of the interface. They cost a few hundredths of a millisecond a frame. Card CMB-06 is done;
  its pictures wait for you with the others (item 8).
- 2026-10-23 · **M2 T18 Sounds:** a unit that is hit or dies now sounds, but only for players who
  can see it; from the RTS camera the sound carries to the ground in view and somewhat beyond. An
  order you give answers with a short "yes", a refused one with a "no". The server itself never
  plays a unit's sound, so nothing leaks past fog of war later. Card AUD-01 is done; whether it
  sounds right is yours to judge in the demo.
- 2026-10-23 · **M2 T15 and T16 Melee, attack and attack-move:** units fight now. The bundled
  infantry is a spearman: right-click an enemy and your selection closes in, each soldier to its own
  place round it, and strikes every second until it dies, chasing it if it runs. Press Z and then
  right-click the ground to attack-move: they walk there and fight whatever they meet on the way.
  Left alone, a soldier attacks any enemy that comes within 12 blocks and hits back when hit. H
  stops. Two hundred spearmen fighting at the river's fords for five minutes kept the server well
  inside its budgets (the core 0.8 ms a tick). Cards CMB-01, CMB-02 and ORD-04 are done.
- 2026-10-23 · **M2 T20 First-person orders:** in first person, G on an enemy now attacks it; R makes
  your selection follow you, in rows behind you, and they wait where they are when you stop or go
  back to the RTS view; looking at a unit shows its name and, for your own, its hit points and what
  it is doing. Card CAM-07 is done.
- 2026-10-23 · **Your decisions applied; M1 and M2 are Done:** the new benchmark baseline, the kernel rule
  for messages, gates C and E, the block writer's limit, PERF-14's baselines and the pictures (all but the top
  pose, item 6) are in. S10's rule is built (M1 T29): a unit now steps onto a stair or slab as soon as its
  feet reach it and leaves a ledge only when its feet have, as a vanilla mob does.
- 2026-10-23 · **M1's close (T27), all but one task:** the 600-unit crowd soak ran five times on one
  commit: every budget held, and budget D (path work) now fails a run that breaks it. Every GameTest
  passes with Lithium, ModernFix and FerriteCore on the server. Saves of format 2 are released, and two
  fixture files keep them readable from now on. One thing is left: two movement benchmarks had
  silently stopped running in M1 (item 10); with them fixed, M1 is Done.

## Now

- **Milestone:** M3 First loop, started 2026-10-23 at your word, without windows; spec:
  [milestones/M3-first-loop.md](milestones/M3-first-loop.md) (31 tasks, every card `specified`).
  M1 and M2 are Done (2026-10-23).
- **In progress:** M3 T1, the health check from M2's close.
- **Last finished task:** the M3 spec (2026-10-23).
- **Next task:** M3's tasks in the spec's order (§7.1), headless; anything needing a window goes to
  item 6.
- **WIP branch:** none.
- **CI:** green; minutes used this month about 960 of 2,000 (48 %), as of 2026-10-23.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner |
| M1 Movement | Done 2026-10-23 | Started 2026-10-22 on the owner's go-ahead; 29 tasks (T28, PATH-08, joined by its R3 trigger in T8; T29 by the owner's word); Accepted waits for the L7 demo |
| M2 Command | Done 2026-10-23 | Started 2026-10-23 at the owner's word; 23 tasks; Accepted waits for `pose_high` (a window) and the L7 demo |
| M3 First loop | In progress | Started 2026-10-23 at the owner's word, without windows; spec written the same day, 31 tasks |
| M4–M6 (MVP) | approved | Specs are written by the assistant when each milestone starts |
| M7–M15 | proposed | Approved by the owner closer to their time |

## History

- **2026-10-20:**
  - research: two fact-checked passes;
  - toolchain check;
  - the owner's base decisions.
- **2026-10-21:**
  - design:
    - architecture and 23 ADRs;
    - feasibility, tooling, process and testing documents;
    - a roadmap of 243 cards, reviewed;
    - the M0 spec;
    - the promo draft;
  - the owner approved M0–M6 and the decisions needed by M6;
  - the private GitHub repository;
  - the autonomy kit.
- **2026-10-21 to 2026-10-22:** M0 built, all 25 tasks: the build and its gates, every test
  harness, the deterministic kernel with persistence, quarantine and fault containment, the
  walking skeleton, the performance harness and spikes S1 to S6; accepted by the owner on
  2026-10-22.

Details are in [worklog.md](worklog.md).

## Known follow-ups (not blocking)

- [01-feasibility.md](01-feasibility.md) §3 predates the architecture in places; its precedence
  note says the architecture and the ADRs win. §6.5 was re-estimated after M0 (2026-10-22).
- ADRs owed by future cards:
  - ECO-03's leaf rule, which amends ADR-0021;
  - ECO-16's fauna archetype, which amends ADR-0011.
- ADR-0022 waits for its confirmation at the first L7 session the owner plays.
- Budget F is measured for bare humanoids only; ART-01 (M3) runs the armoured pass.
- Modifiers are not yet saved or hashed: the first real source (TECH-01's research or an AI
  handicap) brings their input, snapshot field and hashing ([M1 spec §1.6 D row 66](milestones/M1-movement.md)).
