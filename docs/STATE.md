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
5. **Optional reviews:**
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

## Now

- **Milestone:** M1 Movement, started 2026-10-22 (owner's go-ahead); spec [milestones/M1-movement.md](milestones/M1-movement.md); 6 of 27 tasks done.
- **In progress:** none.
- **Last finished task:** M1 T5 (2026-10-22).
- **Next task:** M1 T6 · Map packages, importer and the proving ground.
- **WIP branch:** none.
- **CI:** green; minutes used this month about 220 of 2,000, as of 2026-10-22.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner |
| M1 Movement | in progress | Started 2026-10-22 on the owner's go-ahead; 27 tasks |
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
