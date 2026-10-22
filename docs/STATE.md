# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-22 · **Owner last seen:** 2026-10-22 · **Current:** M1 Movement — started 2026-10-22 (owner's go-ahead)

## For the owner

Answer in chat or here; the next session applies the answers.

1. **Nothing is waiting from M0.** You accepted it on 2026-10-22 without playing the demo. The demo
   stays ready whenever you want it: `scripts/playtest.sh`, then the steps in
   [testing/playtest.md](testing/playtest.md). Its first use confirms ADR-0022, the one thing your
   acceptance left open.
2. **Your consent for M1's tests (2026-10-22):** every kind of test, client windows included. With
   the lid closed and no display, a Mac window may not render, so client scenarios run on the
   Linux route on CI first; a Mac run is used only where a measurement needs this Mac.
3. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
4. **A threshold to approve, not urgent:** the block writer's cost is measured but not yet gated.
   64 blocks a tick take 0.02 ms (0.065 ms at p99) on this Mac. The proposed limit is 1.15 ms a
   tick at p99, 5 % of the whole-tick budget C. Say yes and it becomes a gate in `verifyPerf`
   ([M1 spec §1.6 D row 20](milestones/M1-movement.md)).
5. **For your information: the long-route planner (card PATH-08, HPA*) left the reserve and is built.**
   The roadmap had it waiting for one measurement, which T8 took: one unit's path across the
   384² proving ground needed about 30,000 search steps, over the 20,000 cap, and 4.5 ms of server
   time, over the 2 ms a tick the budget allows all pathfinding. T28 added it: the same crossing
   now takes about 2,800 steps and a fraction of a millisecond, and its route is within 1 % of the
   best one. Nothing to do; it is in the roadmap's rules ([M1 spec §1.6 D rows 42 and 47–53](milestones/M1-movement.md)).
6. **A proposal from spike S10, your call, not urgent:** units now stand exactly on slabs, stairs,
   snow, carpet, paths, farmland and in shallow water, but they take a step when its edge passes
   their centre, so for about two frames per step up a foot cuts into the step's edge, and on the
   way down it hangs over the ledge ([frames and numbers](spikes/S10-kinematic-fidelity.md)). A
   vanilla body stands on the highest block under its whole footprint instead. I propose the same
   rule for our units, a change to the motion of card PATH-05, before your Mac look at the steps in
   M2's consent batch. Say yes and it joins M1 as a small task; no, and the steps stay as they are.
7. **Optional reviews:**
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

## Now

- **Milestone:** M1 Movement, started 2026-10-22 (owner's go-ahead); spec [milestones/M1-movement.md](milestones/M1-movement.md); 17 of 29 tasks done (T28, PATH-08, joined by its trigger).
- **In progress:** none.
- **Last finished task:** M1 T15.1 (2026-10-22).
- **Next task:** M1 T15.2 · Roster and the fog-gate stub (NET-01).
- **WIP branch:** none.
- **CI:** green; minutes used this month about 220 of 2,000, as of 2026-10-22.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner |
| M1 Movement | in progress | Started 2026-10-22 on the owner's go-ahead; 28 tasks (T28, PATH-08, joined by its R3 trigger in T8) |
| M2–M6 (MVP) | approved | Specs are written by the assistant when each milestone starts |
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
