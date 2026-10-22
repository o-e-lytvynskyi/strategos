# Worklog

Append-only session log, newest first. Each entry says:
- what was done (milestone tasks and cards);
- the results, with numbers;
- the problems met;
- what comes next.

The owner reads it to see what happened since the last visit; [STATE.md](STATE.md) shows where
things stand now. Commits are named `Mx Tn: …`, so `git log --grep "M0 T3"` finds a task's work.

## 2026-10-22 — M1 start, T1, T2, T11, T3, T4, T5, T6, T7, T8, T19, T28, T9, T12, T10, T13, T14, T15.1, T15.2, T21, T17, T18, T25, T20, T23, T22

- **M1 T22 · Worker pool, ADAPTIVE delivery and spike S7** — PATH-16 done; ADR-0015 Accepted.
  - **Results:** `PooledJobExecutor` (`strategos-path-N` daemons) and `PathThreads` (the setting,
    or `clamp(cores / 3, 1, 4)` integrated and `clamp(cores / 2 - 1, 1, 4)` dedicated) in
    `mod/server/jobs`; requests wait for admission within W, `BUSY` past 2,048 outstanding with a
    retry 10 ticks later, `ADAPTIVE` delivery from a journaled `JobDelivery` (input kind 3) with a
    join 8 ticks after due; the admission queue is saved and hashed while it holds a request.
    Tests: `ThreadCountHashTest` (4), `AdmissionTest` (2), the canary on 0, 1 and 4 threads,
    `PathThreadsTest` (4), `pathtests.save_quiesces_and_continues`, `perftests.path_budget_d`;
    `head_on_gap` re-recorded (130 GameTests); `verify verifySlow verifySoak` 1 min 48 s.
  - **Measured (S7, 34 configurations):** with any worker the main thread's path work is at most
    0.56 ms at p99 (slow-host proxy) and `STRICT`'s join at most 0.03 ms; inline it is 34 ms. A
    100-unit order behind 200 requests waits 30 ticks at W = 200,000 and 4 at 1,600,000, now the
    default; `k` stays 2, `STRICT` is live everywhere. The perf test: 0.26 ms at p99, 4 ticks.
  - **Problems:** the first S7 run stepped ticks back to back, so the workers had no time between
    them and the main thread joined half the jobs unfinished; ticks are paced 50 ms now (D row
    146). `perf.fakeForeignLoad` only fakes a measured number, so the slow-host proxy loads the
    host with 24 busy threads. The perf test's first run in a cold JVM joined 17 unfinished jobs
    (11.6 ms on one tick); it warms up twice and pools ten runs. The save test first had 24 units,
    which the new W admits in one tick, so none waited at the save; it has 96.
  - **Next:** T24 (shared group path).

- **M1 T23 · Stuck ladder** — PATH-07 done.
  - **Results:** `core/sim/move/StuckLadder` acts on the stuck monitor's count: at 40 ticks a new
    path, at 100 a walk of up to 40 ticks to the nearest free spot within 3 columns, at 200 a
    60-tick walk with avoidance off, at 400 a nudge of up to 4 blocks along the path to the first
    free column, at 600 a failed order and a STUCK notice. Every rung is posted as `Stuck` and
    counted per match (`MatchQueries.ladderRungs()`); each unit's rung is on the overlay's markers;
    the detour and the ghost walk are saved and hashed only while set; `core/sim/nav/Spiral` is
    shared with slot snapping. The monitor's count now goes on through a new route and restarts
    with a new order. Tests: `StuckLadderTest` (5), `SpiralTest` (2),
    `pathtests.boxed_in_frees_or_fails`; `head_on_gap` re-recorded (129 GameTests);
    `verify verifySlow verifySoak` 1 min 54 s.
  - **Measured:** in `head_on_gap` 15 units reach the first rung and one the second, none higher;
    a unit walled in by eight units held still walks out at the third rung.
  - **Problems:** the monitor restarted its count at every new aim, so the first rung's new path
    would have kept a stuck unit on that rung for ever; a new route is no progress now, passing a
    waypoint is (D row 138). A box of terrain ends a move with no path rather than failing it, and
    units are no wall to a walk with avoidance off, so the L1 test's unit "with no exit" is one
    held at walk speed 0 (D row 141). The GameTest's pen first stood two columns ahead of the unit,
    which walked into its walls before their pages were rebuilt; it stands five ahead now. The
    first full gate failed the server smoke: a group member 2.55 blocks from the target against a
    2.5 check. The smoke's match seed is random; its scenario over 400 seeds in L1 fails the same
    10 seeds at the same points before T23 as after it, so since T20 the check was tighter than the
    rules (1.5 blocks of a member's place, which can be 2.1 from the target). It now measures each
    unit against its own place, within 2.5.
  - **Next:** T22 (worker pool, ADAPTIVE delivery, spike S7).

- **M1 T20 · Group destination slots** — ORD-03 done.
  - **Results:** `core/shared/SlotLayout` (rows across the facing, 4 radii apart, centred, inside
    the match) and `core/sim/order/SlotPlanner` (snapped in a spiral to the target's component,
    assigned column by column, front places first), wired into the Move rule for several units;
    group members arrive within 1.5 blocks of their places when stuck; standing units part only to
    0.6 and avoid walkers' steps rather than velocities. Tests: `SlotLayoutTest`, `SlotSnapTest`
    (fifty from sixteen spawns), `ordertests.fifty_settle_in_slots`, the `group_of_50_slots`
    golden (128 GameTests); `verify verifySlow verifySoak` 2 min 3 s.
  - **Measured:** fifty settle in 174–236 ticks across sixteen spawns (one unit alone: about 146);
    `maptests.fifty_units_one_by_one` 1.04 times the ideal time, at most 24 ticks without progress;
    `OrcaBench.step600` 583 µs.
  - **Problems:** the spec's spacing (2 radii and 0.1) walled members out of places the first
    arrivals surrounded; 4 radii and 0.1 leave room to pass (D row 132). The spec's single sort
    crossed ways inside columns; filling by rows and swapping crossing ways each walled a member out,
    so places fill column by column, front first (D row 134). Settled members a little off their
    places pushed each other round for ever, and a standing crowd never made way for a walker that
    had stalled, because it avoided the walker's velocity, zero: standing units now part only to
    0.6 and avoid the walker's step (D row 136, pet-projects knowledge base). Places beyond the
    edge of a match without terrain sent `canary`'s units out of its space; they are drawn inside.

- **Fix after CI (T21's avoidance)** — CI's `verifySlow` failed `maptests.fifty_units_one_by_one`
  from T17's push on: a walker stuck for 100 ticks among the idle units at the proving ground's
  west start, counting on them to make way for half while they plan only every 4 ticks. A walking
  unit now takes the whole of the avoidance of a unit that is not walking (spec §1.6 D row 131);
  the slowest of the fifty takes 1.09 times its ideal time. The local gate had run `verify` only;
  the skill now runs CI's `verify verifySlow verifySoak` before pushing movement changes.

- **M1 T25 · Movement debug overlay and dev payloads** — PATH-10 done; its overlay goldens wait for
  M2's consent batch, as planned.
  - **Results:** `core/protocol/{DebugLayer, DebugSubscribe, DebugFeedMsg}` with exact sizes; the
    optional payloads `debug_subscribe` and `debug_feed`; `mod/server/debug/{DebugFeed, DebugFrames,
    PerfDump}` (operators only, per layer at 2–10 Hz); `/strategos perf start|stop|dump` and
    `/strategos debug subscribe`; `mod/client/debug/{DebugOverlayHost, MovementOverlay,
    OverlayPanel, ClientDebugState}` with the F8 key and `/strategos-client overlay`; the path
    latency p95; inventory entries. Tests: `DebugFramesTest` and the payload size, round-trip and
    audience tests, `MockViewer#operator`, `debugtests.unsubscribed_gets_no_dev_payload` and
    `debugtests.subscribed_gets_feed` (127 GameTests); `verify` 46 s, the smoke loading no client
    class.
  - **How:** the parallel agent built everything but the GameTests on a branch and committed it when
    the owner stopped the agents; the main tree took it onto T18, resolving two conflicts with
    T15.2 by keeping both sides, added the operator viewer and the two GameTests, and ran `verify`.
  - **Problems:** GameTestServer gives operators level 0, so the test operator needs an ops entry of
    its own level; `:mod:test` runs as a dedicated server, so the client's registrations are
    checked against the architecture's table only; Brigadier merges a second `/strategos` root into
    the first (pet-projects knowledge base, which now keeps the build pitfalls in a file of their
    own).

- **M1 T18 · Order queue: move and stop** — ORD-02 done.
  - **Results:** `core/sim/order/{OrderQueues, OrderKind, ContextResolver}` replace `OrderStore`:
    16 orders a unit, `REPLACE`, `QUEUE`, `PUSH_FRONT`, `finishOrder` going on to the next, Stop
    clearing; group ids for commands to several units; `QUEUE_FULL`; the queue in the snapshot as
    optional fields, no migration; `MatchQueries.orders`; `/strategos order move|stop` with
    `@units`, `@group`, a handle, a column or a map waypoint and a mode. Tests: `OrderQueueTest`,
    `QueuedWaypointsTest`, `ordertests.commands_payload_applies_next_tick`,
    `commandtests.order_commands_queue_group_and_stop` (125 GameTests); `verify` 43 s.
  - **Problems:** group ids changed `canary`'s hashes and the S6 golden, both re-recorded with the
    reason; the other goldens only reordered their snapshot keys. The server smoke caught a unit
    left walking 0.12 block from a shared target: T21's "target taken" looked only at the unit
    nearest the point, which was the unit itself; it now counts every unit covering the point (D
    row 119). A 0.5-block tolerance for stuck units was tried again and again jammed the head-on
    formation.

- **M1 T17 · UnitAI HFSM** — UNIT-02 done.
  - **Results:** `core/sim/unitai/{Hfsm, StateDef, StateId, AiMessage, UnitAi, HfsmCheck}`, with
    `UnitAiSystem` and `AiStateStore` rewritten: a tree of states with handlers that bubble,
    transitions after the handler, enter and leave hooks, thinks by period, two data words per unit
    saved and hashed only once set; messages `OrderFinished`, `PathDelivered`, `PathInvalidated`,
    `Stuck`. Tests: `HfsmTransitionsTest` (36), `HfsmPersistenceTest` (5), `HfsmExitPathTest` (7),
    an NBT round trip; every golden replays unchanged; `verify` 47 s (123 GameTests).
  - **How:** a parallel agent built the framework, the tree and `HfsmTransitionsTest` on a branch;
    when the owner stopped the agents it committed its work in progress there. The main tree took
    the commit onto T21 without conflicts, sent T21's repath on a lost line through the new
    `PathInvalidated`, wrote the persistence and exit-path tests and the NBT case, and ran `verify`.
  - **Problems:** none in the merge. The agent met one Gradle trap: `spotlessApply` and compiling in
    one invocation race under the configuration cache, which runs unrelated tasks in parallel, and
    ErrorProne crashed reading a file being rewritten (pet-projects knowledge base).

- **M1 T21 · Local avoidance (ORCA)** — PATH-06 done; UNIT-09 done with its avoidance check, and
  ADR-0011 Accepted (2026-10-23).
  - **Results:** `core/sim/move/{Orca, OrcaLine, Separation, Avoidance}`: RVO2 2.0.3's solver and
    agent lines ported (Apache-2.0; `NOTICE`, `licenses/Apache-2.0.txt`, the changes in `Orca`'s
    header), blocked surface edges as static limits, separation in crowds, idle units stepping aside
    every 4 ticks; `MotionSystem` plans every unit before any moves; arrival beside a taken target and
    on touching a crowd that arrived; a lost line of walk asks for a new path. `core/sim/{Agent,
    AgentUpdate}` (journal input 5) and `mod/server/body/AgentTracker` (players outside the match and
    mobs, not members: D-08). Tests: `OrcaTest`, `AvoidanceTest`, `AgentUpdateTest`, `NoticeFileTest`,
    the `head_on_gap` golden (crossing_synthetic re-recorded; walking_skeleton and canary
    byte-identical), `avoidtests.head_on_groups_through_gap`, `walk_around_mock_player`,
    `member_avatar_passed_through` (123 GameTests); `verify` 42 s.
  - **Measured:** `OrcaBench.step600` 577 µs, 0.96 µs a unit (budget 5); `MotionBench.step600`
    340 µs; `SpatialBench.query1000` 0.39 → 0.25 ms. Both groups of 50 arrive in about 350 ticks
    (one unit alone: 166).
  - **Problems:** plain ORCA brakes a head-on pair on one line to a stop: a bound preference is
    turned 0.001 radian and solved again (D row 93). The gate found 19.6 KB a step in
    `MotionBench`: `Arrays.sort` on a grid query's results allocates a merge buffer once it sorts 44
    or more indices in several runs; the grid now reports through a bitmask (D row 102). JMH's own
    threads add about 0.007 B per µs of an op, so the 0-allocation gate now allows that over long
    ops, its self-test extended (D row 103). With avoidance a crowd sent to one point never landed,
    which broke 52 GameTests and the smoke: arrival beside a taken target and on touching an
    arrived crowd (D row 98); a 0.5-block tolerance and stuck walkers sent elsewhere counting as
    occupants both jammed the head-on formation and were dropped. A straight path pushed past a
    gap's edge left units on the wall until they repath on a lost line of walk (D row 99).

- **Parallel work:** at the owner's suggestion T17 (UnitAI HFSM) and T25 (debug overlay) are built
  by two agents in their own worktrees while the main tree goes on; each branch is reviewed,
  verified and merged here, and its docs are written here.
- **M1 T15.2 · Roster and the fog-gate stub** — NET-01 done.
  - **Results:** `MatchRoster` (a player's UUID bound to a slot, saved in the match blob once
    someone sits in it) with the registry's seat index and `/strategos match join <id> <slot>
    [player]`; `NetGateway` resolves `Faction`, `SeesCell` and `Match` to the roster, sends
    `match_join` on joining and at login with a seat, `match_state` to a match's members on every
    lifecycle change and every 5 s, and submits a seated sender's commands as its slot, each result
    back as `feedback`; `mod/server/fog/{FogGate, InterestSets}` behind
    `UnitEntity#broadcastToPlayer`, `startSeenByPlayer` and `stopSeenByPlayer`. Tests: `FogGateTest`,
    `MatchRosterTest`, `nettests.same_match_paired_and_informed`, `nettests.other_match_gets_nothing`,
    `nettests.issuer_from_uuid_slot`, `nettests.unseated_player_commands_ignored`,
    `nettests.seated_player_rejoins_informed` (120 GameTests); `verify` 43 s.
  - **Problems:** `joined` first sent the state to the new player alone, which `match_state`'s
    audience (a match) forbids; the next flush now sends it to all the members (spec §1.6 D row
    87). A player in no match keeps seeing every match's bodies, as the M0 demo's observer needs (D
    row 89). Rejoin at login was in the architecture's table but not the task; it is one handler
    and one test.

- **M1 T15.1 · Payload layer and gateway** — NET-01 in progress (T15.2 seats players); UNIT-01
  done (its criterion 5).
  - **Results:** `core.protocol`: `CommandBatch`, `MatchJoin`, `MatchStateMsg`, `FeedbackMsg`
    (`Feedback`, `NoticeMsg`), `NoticeKind`, with exact wire sizes; `core.sim.Notice` as an output.
    `mod/server/net`: `NetGateway` (the only sender: audience checks, a queue per player flushed
    once a tick, trackers through vanilla, no payload to a client without our channel, the
    `commands` reader with its violations), `Audience`, `PayloadSpec`, `ByteBufWire`,
    `RateLimiter`, `NetCounters`, `C2sHandlers`; the four M1 payloads; `ModPayloads`;
    `ClientPayloads` through a `PayloadArrived` event (B5). `BodyRegistry` clears a body's dirty
    data before adding it. `MockViewer` is a configured NeoForge client and can send. Tests:
    `WireCodecSizeTest` (payloads), `PayloadRoundTripTest`, `PayloadAudienceTest`, the
    engine-surface inventory with payloads probed, `nettests.bad_batches_dropped_and_disconnect`,
    `nettests.pose_same_tick_no_entity_data` (115 GameTests); `verify` 46 s.
  - **Problems:** the pose test found two things. A walking body sent an entity-data packet with its
    health: the constructor's value stayed dirty after the spawn packet carried it (fixed in
    `BodyRegistry`, one packet less per body). And the test first saw each tick's pose a tick late:
    the server holds packets unflushed until it sends chunks, after the GameTest ticker, so the
    viewer now flushes before reading (spec §1.6 D row 84). NeoForge refuses our payloads on a
    connection that never negotiated channels, which `configureMockConnection` sets up (D row 85).
    A codec that throws makes NeoForge disconnect at once, so the `commands` codec reads leniently
    (D row 80).
- **M1 T14 · Network test kit** — QA-19 done; its JFR check (QA-19.4) is in M2's consent batch.
  - **Results:** `gametest/net`: `MockViewer` (a player on an `EmbeddedChannel` that keeps its
    packets, unbundles them and answers chunk batches; closed at a batch's end), `PacketCapture`
    (per type), `ByteCounter` (each packet encoded with `GameProtocols.CLIENTBOUND_TEMPLATE`, per
    type, ours apart, encode failures kept). `nettests.acknowledging_viewer_tracks_view`,
    `nettests.capture_per_player_and_type` (113 GameTests); `perftests.viewer_bytes_600` in a new
    `perf_viewer` batch; `verify` 42 s.
  - **Measured:** one viewer at the arena's centre while 600 units walk: 41 KB/s (budget E 100),
    1,920 bytes a tick at the median and 3,456 at p99, nearly all of it position updates (190 a
    tick, 10 bytes each).
  - **Problems:** the first perf run crashed the GameTest server: a viewer named with 21
    characters made `player_info_update` unencodable (a profile name has at most 16), which a mock
    connection never notices but the byte counter does. Viewers now refuse such names, and the
    counter records failures instead of throwing (pet-projects knowledge base).
- **M1 T13 · Spatial index** — CORE-08 done.
  - **Results:** `core.sim.spatial`: `UnitGrid` (`@HotPath`; 8-block cells, a counting sort of
    (cell, unit index) every tick, a faction bitmask per cell; `forEachInRadius`, `countWithin`,
    `nearest` by rings of cells), `StaticIndex` (buildings, nodes and dropsites by page, sorted
    indices, nearest node and dropsite), `UnitFilter` (a faction mask and required classes),
    `SpatialQueries` (the match's index; the system that rebuilds the grid at the end of
    `MOVEMENT`; rebuilt on restore too). `SpatialBruteForceTest` (1,000 seeds) and
    `SpatialOrderTest` (776 core tests in all); `SpatialBench`; `verify` 40 s.
  - **Measured:** `SpatialBench.rebuild1000` 4.8 µs, no allocation; `query1000` (1,000 radius-16
    enemy queries among 1,000 units in a 128² battle) 0.39 ms against the 1 ms budget.
  - **Problems:** "handle order" is taken as the slot index, the order systems iterate in, not
    the handle's numeric value, which a reused slot's generation would reorder (spec §1.6 D row
    72). `query1000` shows 2.7 B/op: JMH's background over a long operation, so like §4.2 only
    the rebuild joins the allocation gate (D row 74). The brute-force test was checked against a
    nearest search that stops a ring early, which it fails.
- **M1 T10 · Spike S10: kinematic fidelity, external damage** — UNIT-09 in progress (it closes with
  T21's avoidance check).
  - **Results:** `ExternalDamage` (hack, pierce, crush or lethal; from a player, a mob or nothing)
    is a journaled input (kind 6), recorded but not applied until CMB-01; `DamageBridge` makes it
    from `UnitEntity#hurt`, and the runtime queues it after the commands.
    `fidelitytests.pose_traces` (eight walled lanes on the new 8-high `arena_flat_48_high`) and
    `fidelitytests.player_melee_and_arrow_journaled` (a `FakePlayer`'s blow and arrow); the
    `s10_steps` client scenario, run twice on the Linux route; `tools/png_diff.py`;
    [the S10 report](spikes/S10-kinematic-fidelity.md) with five frames; a dated note on ADR-0011.
    `ExternalDamageTest` (770 core tests in all); 111 GameTests; `verify` 40 s.
  - **Measured:** the floor error against vanilla's rest height is 0 on all eight surfaces (73
    ticks each, 145 in the pool, 25 after the drop); the drop falls half a block a tick. On the
    client, 0 a block or more from a patch's edge, up to 1/6 block within half a block, mid-step at
    the edge. The two runs' frames differ only round the unit, by up to one tick's walk.
  - **Problems:** the first `verify` failed `faulttests.unit_entity_override_fault_contained`: it
    injected its fault through `DamageSource#getMsgId`, which the bridge no longer calls; it now
    throws from `getEntity` (the bridge reads it inside the fault barrier). A four-high arena could
    not hold the drop's platform or the lanes' walls, which A* needs, or it goes round a strip it
    may avoid (spec §1.6 D row 69). The frames show feet cutting into a step's edge for about two
    frames per step: a proposal for the owner (STATE item 6).
- **M1 T12 · Modifier pipeline** — DATA-02 done.
  - **Results:** `core.sim.stats`: `Modifier` (replace, multiply, add, or a token edit `+a -b`;
    a class filter, stage, priority and source id), `ClassFilter` (clauses OR-ed, `+` and `!`,
    compiled to bitsets), `StatService` (`@HotPath`): blocks per template and faction plus
    overlays for units with modifiers of their own, evaluated per stage in a canonical order and
    clamped to the path's declared range, invalidated exactly where a change reaches, posting
    `StatsChanged` on the match bus; `StatBreakdown`. `TemplateSet` gains token paths, base values
    and the core's paths with their typed defaults (`CorePaths`). `MotionSystem` reads speed and
    turn rate through the service. `/strategos stats <handle> [path]` and
    `MatchQueries.stats`. Five test classes (15 tests) and a motion test (767 core tests);
    `commandtests.stats_show_values_and_breakdowns` (109 GameTests); `verify` 40 s.
  - **Measured:** `StatReadBench.cachedRead` 2.4 ns (budget 50 ns), no allocation;
    `MotionBench.step600` 24.4 µs with the reads through the service (23.8 µs before).
  - **Problems:** `StatsChanged` cannot live in the stats package, since the sealed bus message
    type of an unnamed module permits only its own package (spec §1.6 D row 61). Turn rate and
    radius have typed defaults a template's JSON may leave out, so the core's paths are interned in
    every set at those defaults (D row 63). The order test was checked against an evaluation in
    insertion order, which it fails. Modifiers are not yet saved or hashed: nothing adds them in a
    real match before TECH-01 (D row 66).
- **M1 T9 · Motion on surfaces** — PATH-05 done.
  - **Results:** `MotionSystem` replaces `MovementSystem`. On terrain a unit stands on a floor (in
    sixteenths, which a page rebuild does not shift), climbs a step over two ticks, falls half a
    block a tick, walks at ×0.5 in shallow water and ×0.6 on slow ground, never enters a hazard,
    turns at its template's rate, slides along walls (X, then Z) and lands exactly on its
    waypoint; a stuck monitor counts the ticks without progress; the arrival latch walks a unit
    pushed more than a block back; `MotionSystem.impulse` moves a unit in short steps along
    walkable surfaces. Without terrain it is M0's movement to the bit, and the terrain fields are
    hashed and saved only once used. Requests keep the unit's exact point and paths are pulled
    from it. `MotionStore.Extra` in snapshots, `MatchQueries.motion`, `BodyFrame.Builder.reset`.
    `MotionSystemTest` (10) and a string-pulling property test (751 core tests in all);
    `maptests.unit_crosses_proving_ground`, `body_y_follows_core_floor` and
    `fifty_units_one_by_one` in the slow map batches; `MotionBench` joins the `@HotPath`
    allocation gate; `verify verifySlow` 1 min 54 s.
  - **Measured:** `MotionBench.step600` 23.8 µs a tick for 600 units (0.04 µs per unit against
    the 5 µs budget), 0.16 B/op; the slowest of the fifty units in 0.96 of its ideal time (the
    ideal follows the grid path, which string pulling shortens), at most 1 tick without progress.
  - **Problems:** slowing within a block of the target made the scenario GameTests, now on
    terrain, arrive three ticks late; units land exactly instead (spec §1.6 D row 54). The fifty
    units first failed with a unit pressed into a corner for 100 ticks: paths were pulled from the
    start column's centre while the unit stood at its edge, a line M0's straight movement ignored
    but motion on surfaces does not; requests now keep the exact point (D row 59). The canary
    golden came out byte-different with every hash unchanged: a new field of the unit codec
    reorders the JSON keys (pet-projects knowledge base).
- **M1 T28 · Hierarchical long-range planner (HPA*)** — PATH-08 done; R3 resolved.
  - **Results:** `Portals`, the abstract graph, updated with the grid on publish: entrances along
    page borders, a portal at every fifth crossing and at each end, costs between a page's portals
    by a Dijkstra over its own links (pages now store each link's target layer). `HierarchicalSearch`:
    start and goal joined by Dijkstras inside their pages, A* over the portals (at most 4,000
    expansions, `PARTIAL` beyond), each step refined inside its page. `PathJob` searches goals four
    pages away or more in two levels, nearer ones flat within 4,000 expansions and in two levels
    when that is not enough. A path delivered after a page changed in flight is walked on the
    current grid and asked again for if broken. `pathtests.hierarchical_paths_follow_a_new_wall`
    on a new 96 × 96 arena; `HierarchicalSearchTest` (5, 740 core tests in all); `CrossingBench` gains flat variants; `verify` 41 s, `verifySlow` and the perf GameTests green.
  - **Measured:** the 384² and 512² crossings within 1 % of full A*'s cost with 9.4 % and 9.7 % of
    its expansions; the worst of 300 random pairs +4.05 % and +3.65 %. `CrossingBench`: the two-level crossing 0.28 ms p50, 0.31 ms p95 (384²) and 0.31 / 0.33 ms (512²), against flat A*'s 4.3 / 4.5 ms at its cap. Components and
    portals of a 512² prewarm 216 ms once; one republished page about 0.3 ms.
  - **Problems:** A* inside the corridor (the architecture's wording) was exact but expanded a
    quarter of flat A*'s nodes; step-by-step refinement meets both of PATH-08's numbers (spec §1.6 D
    rows 47–48). The wall test found units walking into a wall whose last page was published while
    their request was in flight; results are now checked on arrival (D row 51). Its first negative
    check passed, since stale portals fall back to a flat search; the test now bounds the
    expansions (D row 52).
- **M1 T19 · Components and reachability** — PATH-04 done; taken before T9 (spec §1.6 D row 46).
  - **Results:** each page labels its local components (surfaces linked both ways) and keeps its
    one-way links between them; `Components` joins pages across their edges, recomputing only the
    changed pages and their neighbours, and keeps a column mask per local component;
    `SurfaceGrid.reachableFrom` gives a unit's component and those downstream by drops;
    `makeGoalReachable` in `PathJob` moves an unreachable goal to the nearest reachable column.
    A tick's pages are published together. 8 new core tests (735 in all) with 500 random grids
    checked against a flood fill along the links; `navtests.wall_splits_components`, with a
    negative check that fails; `verify` 42 s.
  - **Measured:** on the 512² generated terrain, a full prewarm's components take 23 ms once; one
    republished page 0.11 ms p50, 0.27 ms p95; a goal in a closed box or across a lake resolves in
    under 200 inspected columns.
  - **Problems:** links are one-way at drops of more than a step; joining them both ways let a
    three-high wall with a walkable top join the floors on its two sides. Components now join only
    two-way links, and drops are a DAG between them (D row 43). Pages are captured whole, so a page
    past the match's edge gets its labels again from the match's own columns (D row 44).
- **M1 T8 · Path service: A*, string pulling and STRICT delivery** — PATH-03 and PATH-15 done;
  PATH-08 (HPA*) leaves the reserve as T28.
  - **Results:** `LineOfWalk` (supercover, corners need both sides), `GridAStar` (8 neighbours, the
    cost rules, octile heuristic, a heap by f, h, id, stamped open-addressing scratch that follows
    the cap), `StringPuller` (doubling probes and bisection, at most 64 waypoints, the goal point
    last), `PathJob` and `PathService`: short moves decided at once, provisional steering on the
    order's tick, `STRICT` delivery at t+2 in id order, superseded results dropped, re-asks at the
    48th waypoint and at the end of a partial path, repaths when a changed page crosses a route (at
    most once in 20 ticks). `MatchRules` gain the path rules with codec defaults; `CorePorts` gain
    `jobs` and `journal` (`JournalSink`; `journal = off|memory|disk`, disk writing
    `match-<id>.strj`); snapshots carry each unit's path and the requests in flight, joined when
    taken. `/strategos nav show|path|rebuild`. The `crossing_synthetic` golden. 41 new core tests
    (727 in all), among them a replay from every segment of a journal with requests in flight;
    `pathtests.unit_crosses_steps_and_ford` and `pathtests.new_wall_blocks_path`, each with a
    negative check that fails; `verify` 41 s with 106 GameTests; `verifySlow` green.
  - **Measured:** `CrossingBench`: both crossings hit the 20,000 cap and return `PARTIAL` (p95 4.60
    ms at 384², 4.70 ms at 512²); uncapped they need 29,293 and 30,930 expansions. Budget D (2 ms a
    tick) is missed, so R3 fires: PATH-08 joins M1 as T28, after T19.
  - **Problems:**
    - A match without terrain must keep M0's hashes, so a request does nothing until a page is
      published, and the hash mixes path state only once a unit has asked: every M0 golden and
      fixture is unchanged (the two goldens were re-recorded only to journal format 2).
    - GameTest positions count from the structure block, a block under the floor; the first path
      tests built their terrain a block low. The far-walking body test now lays a causeway through
      the barrier wall GameTests build round each arena, since its unit walks a path.
    - A page is captured whole, so a match not a multiple of 16 had walkable columns beyond its
      edge; the grid now keeps the match's size (spec §1.6 D rows 31–42).
- **M1 T7 · Live capture, page rebuilds and the router** — BLD-15 done; PATH-15 in progress (its
  wall test needs T8's paths).
  - **Results:**
    - Core:
      - `TerrainPage` inputs are published in INGEST into a copy-on-write `SurfaceGrid`.
      - Snapshot schema 2 holds the page references, with migration 1 → 2.
      - The journal format, version 2, stores each segment's slices in 'L' records.
      - The job port (`PureJob`, `JobHandle`, `JobExecutor`, `JobScratch`).
      - `TerrainReplayTest`: rebuilt pages carry the published hashes, from the start and from the
        second segment alone.
    - Mod:
      - `LevelChunkMixin` feeds `BlockChangeRouter`: own writes are skipped, and only a change of
        nav class dirties a page.
      - `SliceCapture` copies sections on the main thread; `PageJob` builds the page anywhere.
      - `PageJobs` waits 4 quiet ticks (at most 40), takes 4 pages a tick and delivers 4 ticks
        later; it prewarms at prepare and at resume.
      - Rule `JOBS_NEVER_TOUCH_THE_LEVEL`.
    - Two nav GameTests; page capture at 245 µs p95; a 512² prewarm in 1.1 s at 1.07 MB; the
      router at 37 ns a change outside matches.
    - `verify verifySlow` 71 s, 104 + 4 GameTests, 698 core tests.
  - **Problems:**
    - The state hash leaves the grid out, so a resumed match keeps its hash while its pages are
      republished from fresh captures.
    - B4's ordered-collection rule shaped the journal's slice maps.
    - Spec §1.6 D rows 26–30.
- **M1 T6 · Map packages, importer and the proving ground** — MAP-01 done.
  - **Results:**
    - Core: `MapManifest`, `StartSlot`, `MapWaypoint`, `TerrainHash`.
    - `ProvingGround`, the deterministic 384² generator, is the source of truth. `ProvingGroundTest`
      checks it against the committed package and walks the surface grid: every start slot and
      waypoint is reachable from the west start; the ridge top and the deep river are not.
    - `runMapgen` writes the package: 144 tiles, 612 KB. `MapTile` is read and written in vanilla's
      structure format, both ways (`MapTileCodecTest`).
    - `MapPackageLoader` (world exports first, then data packs) and `MapImporter`: tiles through
      the block writer, base fill, Y order, fluids last, then the light wait and the hash check.
    - `MapExporter`; matches on maps (`PREPARING` until the import ends); `/strategos map` and
      `match create map`.
    - Three map tests in `runGameTestSlow`; the server smoke's map step.
    - The proving ground imports in 1.2–2.4 s on the GameTest server and in 7 s on a sprinting
      dedicated one. `verify verifySlow` 71 s, 102 + 4 GameTests, 697 core tests.
  - **Problems:**
    - The writer's shape pass made leaves at tile edges recount their distance, so imports skip it.
    - A column with air under a lintel must also store the stone below the air, or the import
      fills the opening.
    - The map tests need lanes 2,048 blocks apart.
    - Disposing a preparing match has to fault it first (spec §1.6 D rows 21–25).
    - The negative check behaves as specified.
- **CI:** the M0 chunk-ticking GameTest flaked once: 1,600 ticks ran out in 3 s while entity data
  was still loading. Its timeout is now 16,000 ticks; pitfall recorded.
- **M1 T5 · Block writer and the config file** — BLD-15 in progress (its acceptance 2 is T7's).
  - **Results:**
    - Core: `WorldEffect` (a new output), `WritePriority`, `WriteSite` and `WriteScheduler`, which
      sets the shared budget, the round-robin bursts at most every 4 ticks, and priority then
      arrival within a site.
    - `FakeWorld` in the test kit; `FakeWorldOrderTest`, 6 cases.
    - Mod: `BlockWriter`, the only block writer (rule B7), with one scheduler per level, `OwnWrite`
      around every write, `ProtectedBlockIndex` keyed by match and handle, the shape pass when a
      site empties, and the drop of a disposed match's writes and protection.
    - `StrategosConfig` (`strategos-server.toml`, every key of spec §4.4); the arena's lobby pad.
    - `writertests.budget_holds_across_two_matches` checks at most 64 a tick, bursts 4 ticks apart
      and the scheduler's exact order. `perftests.block_writer_cost` measures 0.32 µs a block and
      20 µs per tick of 64 (65 µs at p99), report-only.
    - `verify` 35 s, 102 GameTests, 691 core tests.
  - **Problems:**
    - A sealed `OutputEvent` outside a named module permits only its own package, so `WorldEffect`
      sits in `core.sim`.
    - Solo GameTests need batches of their own, `writer` and `perf_writer`.
    - The sink for `journal = disk` moves to T8, with `CorePorts.journal` (spec §1.6 D rows 17–20,
      row 2 corrected).
- **M1 T4 · Arena world, slots and tickets** — MAP-07 done.
  - **Results:**
    - The `strategos:arena` world preset (a void flat overworld, biome `strategos:arena`, the
      vanilla nether and end) is listed in `#minecraft:normal` and bakes stable.
    - `ArenaWorld` applies D-13's rules on an arena's first start and records that in the
      registry blob, so the owner's later changes stick; other worlds are never touched.
    - `SlotAllocator` places matches 2,048 blocks apart, and the registry counts the slots.
    - `ArenaTickets` force a match's space under the `strategos:match` controller and release it on
      dispose; the validation callback drops dead matches' tickets.
    - The arena smoke runs twice on a dedicated server; the inventory now probes world generation
      data and ticket controllers.
    - `verify` 33 s, 101 GameTests, 54 L2 tests.
  - **Problems:** the lobby pad needs the block writer (rule B7), so it moves to T5; the preset is
    checked in a GameTest, since world presets exist only on a server (spec §1.6 D rows 14–16).
    The negative check holds: applying the rules on every start fails run 2.
- **M1 T3 · Spike S8, terrain census** — PATH-01 done.
  - **Results:** `./gradlew :mod:runSpike -Pspike=s8` measures 11 vanilla 512² squares (6,800
    distinct pages) and the three L1 fixtures in 1 min 30 s, under three Y-band rules. The cap of 4
    never drops a surface within 16 blocks of the terrain. In a band per page 99.992 % of columns
    need at most 4 surfaces (worst square 99.977 %); pages average about 2 KB, nav at most 3.4 MB at
    512², a page builds in 42–84 µs median. The surface layout is fixed: floor 12 bits, cost code
    2. [The report](spikes/S8-terrain-census.md); ADR-0015 carries the numbers, still Proposed
    pending S7. `verify` 34 s, 685 core tests, 99 GameTests.
  - **Problems:**
    - The spec's band per square, from its lowest terrain, failed the exit (98.18 %). Pits pull
      it down, and up to 12.7 % of a square's columns end up above it. Terrain taken from existing
      worlds gets a band per page; PATH-13 (M13) carries it (spec §1.6 D rows 11–13).
    - Forcing chunks through `forceChunk` generates them synchronously and hung the spike server
      once; the census loads through its own region tickets now. Pitfall recorded in the
      knowledge base.
- **M1 T11 · Template engine** — DATA-01 done.
  - **Results:** `TemplateCompiler` (parent chains with cycle detection naming the chain, mixins,
    `$op`, `$disable`, `$append`, `$remove`, class-token edits, abstract templates), every problem
    collected with file and JSON pointer, `TemplateFilter` (foundation, preview, mirage),
    canonical JSON and SHA-256 versions, class tokens and value paths interned; the builtin set
    (debug walker, infantry) compiled from the bundled JSON; `/strategos spawn`. 26 content tests
    (16 merge rows), the golden spearman; 500 templates in 4.15 ms; `verify` 34 s, 99 GameTests.
  - **Problems:** mixins merged in flattened form turned class edits into replacements; they are
    now overlays applied as written (spec §1.6 D rows 8–10).
- **M1 T2 · Block classes and surface pages** — PATH-02 done.
  - **Results:** `BlockClass` (16 bits: collision top and flags), `VoxelSlice` with its run-length
    form, `PageBuilder` (up to 4 surfaces a column, kinds, in-page links, cost codes, footprints),
    `SurfaceGrid` (copy-on-write pages, links across page edges by the same rule), `VoxelWorld` and
    `VoxelFixtures` for L1; `BlockClassTable` over every block state with the `strategos:nav_class`
    data map, now probed by the inventory test; the `HOT_KERNELS_MARKED` rule. 22 nav tests, 3 L2
    tests; a page builds in 27 µs against 0.5 ms; `verify` 31 s.
  - **Problems:** tags are unbound in L2, so vanilla blocks are classified by class and fluid type,
    with tags as an addition and a rebuild on tag reloads (spec §1.6 D rows 4–7).
- **M1 T1 · Wire codec and the binary journal** — CORE-11 done; NET-01 in progress (its codec base).
  - **Results:** `WireWriter`/`WireReader` (bounded varints, zigzag, raw doubles, UTF-8 checked)
    and `WireCodec`s for the commands (a one-unit Move is 20 bytes, 300 consecutive handles 320) and
    the command envelope; journals in 1,200-tick segments, each with its snapshot; the binary
    `.strj` format replaces M0's JSON in GameTest exports, fault reports and the restart smoke; two
    golden journals (`walking_skeleton`, `canary` across two segments) with `rebaselineJournals`
    and its log; `/strategos journal save <name>`. `:core:test` 637 tests; `verify` 33 s; 98
    GameTests; 73 journals replay.
  - **Problems:** ErrorProne's `EnumOrdinal` and rule B8's ban on static arrays shaped the codecs;
    the spec's checkpoint period and header changed (spec §1.6 D rows 1–3).
- **M1 started** on the owner's go-ahead, with consent for every kind of test.
- **Next:** T15.2, the roster and the fog-gate stub (NET-01).

## 2026-10-22 — M0 approvals, the client batch, M0 accepted

- **The owner's approvals** («м0 принимаю и все одобряю»): the JMH baseline committed as
  `perf/baseline-m4pro.json`, `jmhSpeedRatio` (0.98 locally), ADR-0019 Accepted with every budget
  unchanged; PERF-01 and CORE-04 done. `.github/workflows/perf.yml` runs `:core:jmh
  :core:jmhSpeedRatio :mod:runGameTestPerf -Pperf.ciFactor=3` nightly and by hand; its first run,
  39740227029, was green in 3 min 42 s.
- **The client batch** (consent given with the acceptance) — QA-06, PERF-04 and CORE-01 done.
  - **S3** ([report](spikes/S3-client-scenario-harness.md)): 25 smoke runs on the Mac in four
    batches; the window had the focus at the end of 21; frames repeat to the pixel at tick 60 once
    the scene is pinned; `stall` ends through the watchdog; `.github/workflows/scenario.yml` runs
    any scenario on Linux under Xvfb and llvmpipe in 2–3 minutes, frames repeatable at tick 60
    except the units' idle animation; goldens per platform.
  - **S4** ([report](spikes/S4-client-frame-time.md)): twelve runs; 200 bare humanoids cost
    0.69 ms of the frame with the compat set and 4.47 ms without it, the client tick 0.72 ms p50;
    D-01 stays at 150; budget F measured for bare humanoids, armoured with ART-01.
  - `world_switch` passed (CORE-01.5).
  - **Problems, each fixed in the harness:** the player's spawn point moves within the spawn
    radius (a fixed anchor now); light changed with the load time (time frozen at noon); chat lines
    in the frames (`sendCommandFeedback` off); a focused window's mouse turned the spectator
    camera (held each tick and frame, the mouse released); FPS capped at 60 and only the CPU side
    timed (no cap, the frame interval timed too); Sodium crashed on the classpath, its JarJar
    libraries not unpacked (the client set now goes into the run's `mods` folder,
    `installClientCompat` for the owner's client runs); on Linux the vignette was still fading
    after a load of varying length (pinned at 0).
- **The owner's rule:** a static scene is repeated two or three times, not ten — D-22 amended,
  QA-18.1 three runs, the skill's consent batch.
- **M0 accepted** on the owner's word: 25 of 26 cards `accepted`; UNIT-01 stays `in_progress`
  until NET-01 (M1); the L7 demo waived (spec §1.6 row 64), ADR-0022's confirmation moved to the
  first L7 session. CI about 220 of 2,000 minutes this month.
- **M1 specified** ([milestones/M1-movement.md](milestones/M1-movement.md)): 27 tasks in slices 1A
  (one unit on real terrain), 1B (a group of 50), 1C (an army) and a content track; every
  acceptance criterion of the 26 cards traced to a check; the design of the terrain model,
  path service with deterministic delivery, motion, ORCA, orders and HFSM, the binary journal,
  the arena world, the block writer, map import and the proving ground, content loading and
  the payload layer; the engine APIs checked in the 21.1.264 sources (Appendix A). Deviations:
  UNIT-09.3's screenshots on the Linux route; a `template_ack` payload for DATA-03.6;
  `/strategos match join` for seating in M1 (§1.6). The 26 cards are `specified`; PATH-08 and
  PATH-17 stay in reserve.
- **Next:** «начинай M1» from the owner, then T1.

## 2026-10-22 — M0 T9, the tripwire checkpoint, T9b, T11, T10, T11b, T12, T13, T14, T16, T15, T17

- **M0 T17 · M0 close** — QA-08 done; M0 is Done, pending the owner's Acceptance.
  - **Results:**
    - T17's runs, each on its own: `verify` 30.7 s; `verifySlow verifySoak` 5.8 s; `verifyPerf`
      ran JMH (83 s) and stopped at `jmhCompare` with "No approved JMH baseline", as designed; the
      perf batch alone passed (A p50 0.054 ms, G median 0.72 ms);
    - T15's job for CI: `release.yml` (on `v*` tags and by hand), whose manual run 39697935330
      started the production jar on a headless NeoForge 21.1.264 client: the game entered a world
      and finished cleanly (QA-08.4);
    - spec §10 walked row by row (§10.1): rows 1–13 pass, 14–16 wait for the owner;
    - health check: the largest main class has 423 lines, no ArchUnit exceptions, no TODOs,
      `verify` grew from 19.8 s (T3) to 31 s; CI about 170 of 2,000 minutes this month;
    - 05-testing.md has M0's commands, times and options; 01-feasibility.md §6.5 is
      re-estimated (the MVP at about 20–30 weeks, bounded by the owner's playtests);
    - STATE.md: the acceptance session with exact commands, the two approvals and the consent
      batch for the owner.
  - **Problems:** none.

- **M0 T15 · S5 final, S6 report, ADR updates** — QA-01, QA-03 and CORE-05 done.
  - **Results:**
    - S5 final: all 17 checks hold; cold `clean verify` without the build cache 39.3 s, warm 31.2 s;
      `clean verifyFast` 8.6 s, warm 0.4 s, the configuration cache reused; `:mod:test` 4.0 s; L3
      4.2 s; smoke 11.5 s, with the restart run 19.3 s; `verifyHarness` 12.3 s; `:core:soak`
      9.1 s; `verifySlow` 6.2 s;
    - [the S6 report](spikes/S6-determinism-canary.md): the canary equals its golden at all 20
      checkpoints on macOS and Linux, 400 seeded replays equal their live runs, all 73 GameTest
      journals replay to their live hashes;
    - ADR-0007 and ADR-0009 carry the final evidence; the ADR index's pending table and 04 §1.4
      row 9 are updated.
  - **Problems:** none.

- **M0 T16 · Client harness prepared; S3 and S4 plans** — QA-06 and PERF-04 in progress (their runs
  wait for the owner's consent); CORE-01 waits for `world_switch` the same way.
  - **Results:**
    - `mod/src/devtools`: `ScenarioRunner` with its watchdog, `ScenarioContext`, `ScenarioWorld`,
      `FrameTimer`, and the scenarios `smoke`, `stall`, `world_switch` and `s4_units`;
    - the `scenario` run with `prepareScenario` and its `result.json` guard; root `verifyClient`;
      `scenario-gamedir/options.txt`;
    - every client API checked against the 21.1.264 sources; `verifyFast` compiles it;
    - [S3](spikes/S3-client-scenario-harness.md) and [S4](spikes/S4-client-frame-time.md) written as
      plans pending consent; STATE asks for the consent batch.
  - **Problems:** none; nothing of it was run.
  - Spec §1.6 D row 62.

- **M0 T14 · S2 and the budget proposal** — PERF-03 done; PERF-01 in progress (the owner's approval
  of the revision left).
  - **Results:**
    - `S2ArenaCost` on the spike server; the driver now runs any `Spike` (`S1Run`, `S2ArenaCost`);
    - S2 in 9 s: idle MSPT p50 0.065, 0.056 and 0.103 ms at 256, 576 and 1,024 chunks; with 600
      walking bodies under 0.35 ms p99 at every size; autosave medians 5–8 ms (the first save of a
      fresh 256² arena 104 ms); heap about 0.06 MB per flat chunk;
    - keep-alive: the probe ticked 11,999 of 12,000 times with nobody online on `ticking=false`
      tickets, which ADR-0010 is right about;
    - `ticking=true`, for the record: no extra cost under the arena's rules, 0.13 ms more under
      the defaults;
    - [the S2 report](spikes/S2-force-loaded-arena.md); ADR-0019's revision proposal (every budget
      unchanged, the matrix as it is), awaiting the owner; ADR-0010 and D-17 carry the numbers;
    - `verify verifySlow` green in 34 s.
  - **Problems:**
    - T13 had taken over a wrong belief that `ticking=false` tickets freeze entities without a
      player; S2 disproved it (DistanceManager.java:197–202), so S1 was rerun on the spec's
      tickets (same verdicts), and the knowledge base entry, D row 60 and a test helper's comment
      were corrected;
    - zsh passes an unquoted `$args` as one word: the first Lithium rerun got
      `spike="s1 -Pcompat.server"`.
  - Spec §1.6 D row 61.

- **M0 T13 · Spike runner and S1** — PERF-02 and QA-05 done; PERF-03 in progress (S2 is T14).
  - **Results:**
    - the spike runner: `runSpike` on a self-terminating dedicated server, `SpikeDriver`,
      `SpikeRegistry`, `SpikeArena`, `SpikeMob` (P), `SpikePhysicsBody` (L), `TickSampler`,
      `SpikeReport`, `S1BodyCost`;
    - S1, 35 configurations in 67 s: a kinematic body costs 0.12 µs at 500 moving, 0.10 beyond
      the core (exit ≤ 4), against about 5 µs for P and for L; with Lithium P 2.3 and L 1.9, K the
      same (the numbers of T14's rerun);
    - the verdict: S1's exit met, D-01's default stays at 150 per player;
    - JFR: 93 % of the server thread in the entity loop, 75 % in `LivingEntity#travel`;
      `MatchRuntime#tick` 0.3 %, `NaturalSpawner` 0.2 %;
    - [the S1 report](spikes/S1-server-cost-per-body.md) and its data; ADR-0011 and ADR-0019
      carry the numbers; D-01 records the check;
    - `verify verifySlow` green in 33 s.
  - **Problems:**
    - the first S1 run used ticking tickets, on the wrong belief that `ticking=false` freezes
      entities without a player; T14's S2 disproved it, and S1 was rerun as the spec says;
    - a 500-unit Move broke the 300-unit limit and crashed the first run; moves go out in 300s;
    - the first K rows ran on a cold JIT; an unrecorded warm-up pass now runs first;
    - the `-Pspark` and `-Pcompat` profiles had never worked (`localRuntime` in `dependencies {}`
      is the script variable); they use `add('localRuntime', …)` now.
  - Spec §1.6 D row 60.

- **M0 T12 · Performance harness** — QA-07, CORE-02 and CORE-07 done; PERF-01 (T14 left) and
  CORE-04 (the approved baseline left) in progress.
  - **Results:**
    - JMH without a plugin (`:core:jmh`, `-prof gc`, heap and GC pinned), 5 benchmarks in µs/op:
      `MovementBench.step600` 14.1, `StateHashBench.hash600` 22.8 at 0.16 B/op,
      `SnapshotCodecBench.encode500` 220, `MatchBusBench.publish1000` 13.7,
      `PhaseTimersBench.overhead` 0.113 (0.80 % of a step); 1 min 23 s;
    - `jmhCompare` (15 % gate, 0 B/op for `@HotPath` kernels) and `jmhSelftest` in `verifyHarness`;
      without an approved baseline `jmhCompare` fails with "No approved JMH baseline", by design;
      the candidate is `perf/candidate-m4pro.json`;
    - `runGameTestPerf` with JFR, gates, the foreign-load check and `PerfTests`:
      `walk_600_budget_a` p50 about 50 µs and p99 about 0.3 ms against 5 and 10 ms;
      `snapshot_500_budget_g` median 0.7–1.3 ms against 100 ms;
    - five runs of one tree: p95 CV 4.9 % (A) and 3.2 % (G), so both gates stay on; an earlier
      tree's five gave 6.4 % for A;
    - `-Pperf.ciFactor=0.001` failed naming both budgets with the measured and allowed values;
      `-Pperf.fakeForeignLoad=0.3` ended `INVALID run, not a regression`;
    - `verifyPerf` exists; `verify verifySlow` green in 35 s.
  - **Problems:**
    - the phase timers cost 1.29 % of a step, above CORE-02.8's 1 %; `PhaseTimers.nextPhase`
      reads the clock once per phase boundary, which made it 0.80 %;
    - `^perftests\.` reached the server with its backslash doubled (MDG's VM-args file) and
      matched nothing; the filter is `^perftests[.]`;
    - ten foreign-load readings within milliseconds once gave 0.28 and a false `INVALID`; the
      saves are now 20 ticks apart and the window opens with a discarded reading;
    - the server's exit value hid the budget message; the perf guard now names it.
  - Spec §1.6 D row 59; a pitfall for the knowledge base.

- **M0 T11b · Fixture catalogue and its checks** — QA-17 and CORE-12 done; CORE-05, CORE-07 still
  in progress.
  - **Results:**
    - the fixture catalogue in `docs/05-testing.md`;
    - `released-fixtures.txt`: both `match_v1` save fixtures with their expected-hash files and the
      7 corrupted-save files, append-only;
    - root `checkFixtures` (in `verifyFast`) and `checkFixturesSelftest` (in `verifyHarness`), with
      the broken inputs in `gradle/selftest/fixtures/`;
    - three tests passed literal seeds; two now take `Seeds.first("m0-200")`, and the golden spawn
      positions keep seed 7 in the new set `golden-spawn`;
    - `verify verifySlow` green in 33 s.
  - **Problems:** none.
  - Spec §1.6 D row 58.

- **M0 T10 · Fault containment complete, log tagging** — CORE-10 and QA-10 done.
  - **Results:**
    - `FaultReport`: `strategos/faults/<match>-<tick>/` with `fault.txt` (where, tick,
      lifecycle, versions, stack trace), `snapshot.json` and `journal.json`; a file that cannot be
      written becomes `<name>-error.txt`, never an exception;
    - `FaultBarrier` writes it, logs once with the report's path and tells the players standing in
      the match;
    - `faulttests.*` (4): a fault in the tick, in `UnitEntity#hurt` and in a command's reply each
      stop only their match while the other steps on; RETHROW throws after the record; every
      report's journal replays to the fault's hash;
    - the GameTest guard's log scan (QA-10.2): a planted untagged INFO line in `BodyRegistry`
      failed it, naming the lines; removed;
    - 97 GameTests, 4.6 s of L3 wall time; `verify verifySlow` green in 35 s.
  - **Problems:** the stray-body refusal in `BodyEvents` belongs to no match, so it now starts
    with `[no match] `, which the scan accepts (spec §1.6 D row 57).

- **M0 T11 · Static gates complete** — CORE-06 done; QA-17, CORE-05, QA-10 still in progress.
  - **Results:**
    - `CoreArchitectureTest`: 13 rules (B1, B3, B3+ twice, B4a–e, B8, B9, B13, QA-10) over core's
      shipped classes, each also run on its violating fixtures: one per banned API, 22 for B4;
    - `ModArchitectureTest`: 11 rules (B2, B4, B5, B5+, B6, B7, B8, B9, B10, B11, B13) with their
      fixtures and same-named Minecraft and NeoForge stubs;
    - `engine-surface.json` and `EngineSurfaceInventoryTest` (L2, 5 tests): entity types,
      saved-data names, the command root, no mixins and no access transformers equal the
      inventory, every id appears in 04 §2.5, and an unlisted entity type, a `[[mixins]]` entry
      and an access-transformer line are each reported;
    - no production class broke a rule; the five `@HotPath` classes were already annotated;
    - negative check: a temporary `HashMap` field in `MovementSystem` failed
      `noHashOrderedCollections` on the real code; removed;
    - `:core:test` 615 tests, `:mod:test` 45, `:mod:archTest` 11; `verify verifySlow` green in
      34 s.
  - **Problems:** none.
  - Spec §1.6 D row 56.

- **M0 T9b · Restart round trip and the holder's lifetime** — QA-05, CORE-01, CORE-06, all still in
  progress (QA-05 waits for T13, CORE-01 for T16, CORE-06 for T11).
  - **Results:**
    - `runServerSmokeRestart`, run 2 of the smoke in run 1's game directory, with its marker guard
      and a JUnit case; `verify` runs it;
    - run 1: a saved 16 x 16 match, the 8 units ordered back after the check, saved at tick 120
      mid-walk, the journal exported to `journal-run1.json`;
    - run 2: the match back `PAUSED` at tick 120 with run 1's hash; 8 bodies for 8 units and no
      other; 60 ticks later the live hash equals the replay of both journals (9 checkpoints, tick
      181);
    - both markers say `holderAtAboutToStart` and `holderClearedOnStop` true (CORE-01.6);
    - `ReplayRunner.chain` in the core test kit, which also checks the join between the journals;
      `ReplayRunnerTest` +3 (a saved and restored run, a journal from elsewhere, a restore from
      another state); `:core:test` 602 tests;
    - a mutant restoring no body wishes fails run 2 ("no verdict within 600 ticks");
    - both smoke runs 19.2 s; `verify verifySlow` green in 35 s (the second server boot runs after
      the first: they share port 25599).
  - **Problems:** none.
  - Spec §1.6 D row 55.

- **Tripwire checkpoint:** T1 started 2026-10-21, T9 green 2026-10-22, 2 of 10 M0 work days;
  nothing simplified.

- **M0 T9 · Persistence and quarantine** — CORE-07, CORE-12 (now in progress), CORE-01, ORD-01,
  QA-03, QA-09; all still in progress (CORE-07.6 is T12's, CORE-12's fixture check T11b's).
  - **Results:**
    - `server/persist/`: `MatchData` (one blob per match: LIVE, LOADED, QUARANTINED, DISPOSED;
      loading never throws), `RegistryData` (records, ids, save epochs; quarantined, it refuses
      new saved matches), `Quarantine` (guard 2: an unreadable file is copied to
      `<name>.dat.corrupt-<epoch>` and nothing is registered under its name), `NbtBridge`;
      `MatchRecord`;
    - the registry loads saved matches at server start (`loadSaved` per record: restored `PAUSED`,
      or `FAULTED` with the reason) and marks them for the shutdown save; `match create` makes
      saved matches; dispose deletes the record and the blob file;
    - core: a slot table that breaks its invariants is now a decode error, not an exception thrown
      inside DFU (`EntitySlots.problem`, `MatchSnapshotCodec.decode`);
    - L2: `MatchSnapshotNbtTest`, `MatchSnapshotFixtureTest` (`match_v1.nbt`, hash
      6dcbd3d871040342), `MatchDataTest`, `RegistryDataTest`, `CorruptCorpusTest` over 5 corrupted
      blobs; `:mod:test` 40 tests, `:core:test` 599;
    - L3: `persistencetests.*` (3) and `commandtests.match_create_list_dispose` with the record
      and the blob file; mutants caught: a quarantined blob allowed to get dirty
      ("was written again") and `computeIfAbsent` in guard 2 ("its blob is DISPOSED");
    - `verify verifySlow` green in 25 s; 93 GameTests, 4.5 s of L3 wall time; slow run 3.4 s.
  - **Problems:**
    - NeoForge writes saved data on an IO worker: the file tests wait with
      `IOUtilities.waitUntilIOWorkerComplete()`;
    - a quarantined blob rewritten keeps its bytes (gzip writes no time), so the test pins the
      planted file's modification time; byte comparison alone let the first mutant live;
    - `bodytests.bodies_follow_entity_ticking_chunks` (from the T7b review) failed in about half
      the runs, alone too: its far chunk, 72 blocks out, was sometimes two chunks from the
      arena's forced chunks and stayed FULL (loaded, entities tracked, not ticking), so it never
      unloaded. Now 88 blocks out, always three chunks or more: 8 of 8 runs green, and the
      mutant without the no-reason branch still fails;
    - pitfalls in the knowledge base (`minecraft-mod-data.md`, new, and `minecraft-mod.md`).
  - Spec §1.6 D row 54.

- **Next:** M1 · specify Movement (`docs/milestones/M1-movement.md`).

## 2026-10-22 — the owner's setup, M0 T7c, T8

- **M0 T7b review** (rerun after the usage limit; three lenses, each finding checked by a skeptic):
  four findings confirmed, two engine defects, both fixed; two refuted.
  - **Results:**
    - a real chunk unload reaches `BodyRegistry#onLeft` with no removal reason (the leave event
      comes before `unloadEntity`); such a body is now stale, discarded at the next materialise,
      and its unit waits quietly for a body;
    - `KinematicDriver` now takes the level and retires a body whose next pose leaves where
      entities tick, so no body drops into an unloaded chunk or freezes past the simulation distance;
    - new GameTest `bodytests.bodies_follow_entity_ticking_chunks` (batch `chunks`, solo): the old
      code fails it ("a body stands where entities do not tick"), and without the no-reason branch
      it fails with "left the level (null)";
    - `GameTestTickets.forceTicking`; spec §6.6, the engine-fact row and §1.6 D row 53.
  - **Problems:** a passing GameTest's entities are removed by vanilla (`GameTestInfo#succeed`), so
    its match logs one line per re-made body until the batch ends; noted in the GameTest README.

- **M0 T8 · Commands, the walking skeleton end to end, the playtest kit** — QA-04 and CORE-03 done;
  ORD-01, QA-05, QA-09, QA-10, CORE-01, CORE-02.
  - **Results:**
    - `StrategosCommands`: `/strategos match create|list|pause|resume|dispose`, `debug
      spawn|move|stop|replay`, `hash`, all at permission level 2; debug orders go through the
      inbox and reply after the step;
    - `RuntimeScenarioContext` and `ScenarioGameTests`: `scenarios.*` (3), the 20
      `scenarios.walking_skeleton_seed_*` in batch `skeleton` and the 50 `isolation.match_*` in
      batch `isolation`; each stops on the same tick with the same hash as its L1 twin and exports
      its journal;
    - `commandtests.*` (3): permission level 2, the command path, match create, list and dispose;
    - `:core:replayJournals` replays all 73 journals to their live hashes; it is part of `verify`;
    - the server smoke runs a 16 x 16 match at the spawn: 8 units on the target after 100 ticks,
      8 bodies, median tick 0.38 ms;
    - the functional GameTest run: 89 tests, 3.8 s of L3 wall time; `verify` green in 23 s;
    - `clientJoin` run, `scripts/playtest.sh` (checked with `sh -n` only, never run) and
      `docs/testing/playtest.md`; `createLaunchScripts` writes `runClientJoin.sh`.
  - **Problems:** none in the code; `match create`'s Y band is cut to the build height, since the
    GameTest floor lies 6 blocks above it (spec §1.6 D row 52).

- **M0 T7c · Body and match GameTests, the batch-end check** — UNIT-01, CORE-01, CORE-06, QA-04,
  QA-01, all still in progress (UNIT-01's criteria 1–4 pass; 5 is NET-01's, in M1).
  - **Results:**
    - finished from `wip/M0-T7c` after a rebase onto `main`;
    - `GameTestTickets` with its controller, `BatchEndCheck` after every batch's `@AfterBatch`
      cleanup, `MatchFixture`, `JUnitXmlReporter#recordFailure`, `LogCapture`;
    - `bodytests.*` (9, the 6,000-tick one in batch `slow`) and `matchtests.*` (3) pass;
    - the functional run: 13 GameTests in 1.9 s of L3 wall time; the slow run: 3.0 s;
    - `verify verifySlow` green in 22 s; `verifyHarness` runs `runGameTestTicketLeak`;
    - S5 check 17: `-Pstrategos.selftest=ticketLeak` failed with
      `GameTest failures in TEST-gametest.xml: [batchend.selftest]`, naming the chunk;
    - CI now runs `verify verifySlow verifySoak`.
  - **Problems:**
    - `/tp` rejected the body's coordinates: the arenas lie millions of blocks out, where
      `Double.toString` writes `1.13E7`; commands now format coordinates with `%.6f` (README);
    - `Entity#push` ignores two entities on the very same point, so the push control's pigs stand
      0.1 apart (spec §1.6 D row 51).
- **Commit prefixes:** two of the assistant's docs commits of 2026-10-21/22 (92b7757, b8fcf74) start
  with `State:` instead of `Docs:`; the skill's owner-commit search lists them, but they are not
  the owner's. The owner's commit is 021f641.

- **Owner's setup items done** (STATE "For the owner" 1a and 1b of 2026-10-21):
  - the allowlist is active: `.claude/settings.json`, identical to the proposal (commit 021f641);
  - `gh` is installed and logged in as `o-e-lytvynskyi`, so sessions read CI on `main`.
  - **Results:** CI on `main` is green; the newest run (M0 T7b, 39670656358) passed 825 tests,
    the S6 canary on Linux against the macOS golden among them (QA-08.3). The cancelled runs (T4b,
    T6b, the T5b review fixes) were cancelled by the next push, whose run passed. About 61 of the
    month's 2,000 minutes are used.
  - **Problems:** T7b's review did not run: its three reviewers stopped at the usage limit of
    2026-10-21, so the workflow returned nothing. Run it again before finishing T7c.
- **Next:** M0 T9 · persistence and quarantine.

## 2026-10-21 — M0 T1, T2, T3, T18, T4a, T4b, T5a, T5b, T6a, T6b, T7a, T7b, T7c (started)

- **M0 T7c · Body and match GameTests, the batch-end check** — started, not finished: the session
  stopped as usage ran low. The work is on `wip/M0-T7c` (commit c064b5f, unverified).
  - **Done on the branch:** `GameTestTickets` (the ticket controller and its held chunks),
    `BatchEndCheck` (leftover tickets, GameTest matches and unit bodies are recorded as
    `batchend.<batch>`, then cleared), `MatchFixture`, `JUnitXmlReporter#recordFailure`.
  - **Left:**
    - register the controller in `GameTestBootstrap` (`RegisterTicketControllersEvent`, GameTest
      server only);
    - in `StrategosGameTests`, the `@AfterBatch` hooks of every batch (`MatchFixture.disposeAll`,
      then `BatchEndCheck.verify`), and the slow-batch rule (`strategos.gametest.slow`);
    - `Suites`: `BodyTests` and `MatchTests` in the default run, `ticketLeak` →
      `TicketLeakSelfTest`; the three suites of spec §7.4;
    - `mod/build.gradle`: the runs `gameTestSlow` and `gameTestTicketLeak`, the guard's failed-case
      check, the slow run's guard, the ticket-leak run's `expectVerdicts`; root `verifySlow`,
      `verifyHarness` gains `:mod:runGameTestTicketLeak`; CI runs `verify verifySlow verifySoak`;
    - the README's notes on the batch-end check and the slow batch.
  - **Notes for the tests:** "one log line" in `discarded_body_rematerialises` needs a log capture
    (a Log4j2 appender filtered by the match prefix); in `no_vibrations_no_push` the pig controls
    must stay more than 8 blocks from the unit's sensor (the sculk sensor's range) and run first,
    with the pigs discarded before the unit part.
  - **Reviews:** T6b's review (three lenses, each finding checked by a skeptic) confirmed nothing.
    T7b's review was still running when the session stopped: if its result is not recorded here,
    run it again before finishing T7c.

- **M0 T7b · Holder, match registry, tick driver, bodies** — CORE-10 in progress; CORE-01, CORE-02,
  CORE-06, UNIT-01.
  - **Results:**
    - `StrategosServer`, the per-server holder from `ServerAboutToStartEvent` to
      `ServerStoppedEvent`, with `StrategosSettings`;
    - `server.match`: `MatchSpace` with its codec, `MatchOptions`, `MatchRegistry` (size, height,
      build-height and 2-block overlap checks; create, pause, resume, dispose, unload),
      `MatchRuntime` and the chunk-based `MatchRegionIndex`;
    - `server.tick`: `TickDriver` in `LevelTickEvent.Pre`, gated by `/tick freeze`;
      `InputCollector`, `OutputAppliers` and `FaultBarrier`;
    - `server.body`: `BodyRegistry` (bodies in handle order where entities tick, within the 64-per-tick
      budget; parked after a chunk unload, re-made after any other removal), `KinematicDriver`,
      `SpawnBudget`, `BodyEvents`; `UnitEntity#hurt` reaches the match through the fault barrier;
    - every engine name was checked in the 21.1.264 sources first;
    - `:mod:test` passes 23 tests; `verify` is green, and the smoke also checks the holder and the
      registered body type.
  - **Problems:** the spec's `MatchException` clashes with Java 21's `java.lang.MatchException`;
    it is `MatchRefusedException` now (spec §1.6 D row 50, with three small API additions).

- **M0 T7a · Unit entity, registry, renderer** — UNIT-01 in progress; QA-03.
  - **Results:**
    - `ModEntityTypes` registers `strategos:unit_humanoid` (MISC, `noSave`, `noSummon`, fire
      immune, tracking range 10, interval 3, no velocity packets) with its attributes;
    - `UnitEntity`: a kinematic view; the server tick is empty, no pushing, loot, riding, portals,
      damage or equipment; the spawn data rides the complex-spawn packet;
    - `UnitSpawnData`: four VarInts and a byte, 13 bytes at most;
    - `Ids`, the client entry `StrategosClient` and `UnitRenderer` (the player model with the
      vanilla wide Steve skin, never copied);
    - `:mod:test` passes 8 tests: the type's settings, its attributes, the spawn data round trip
      and size, and `CodecSizes`, which fails above a cap with "UnitSpawnData encoded to 8 bytes,
      cap 7";
    - every engine name was checked in the 21.1.264 sources first;
    - `verify` green in 19 s; the dedicated and the GameTest server start with the type.
  - **Problems:** none.

- **M0 T5b review** (three lenses, every finding checked by a skeptic): five findings confirmed, all
  in tests and the spec; the kernel was right.
  - **Results:**
    - `MoveRuleTest`: "a refused Move changes nothing" compares the state hash with a twin match;
      the old assertion could never fail;
    - `StopRuleTest`: ownership is checked on a walking unit, plus stale and never-issued handles;
    - `SpawnUnitsRuleTest`: the unit cap with units already alive; golden positions in the stored
      units, on a floor at y 4;
    - the empty unit set's `INVALID_ARGUMENT` for Move and Stop is now in the spec (§1.6 D row 49);
    - each strengthened test fails under the mutation the review named (Stop applied before the
      ownership check, a cap that ignores live units, poses stored at `minY` or at the centre).
  - **Problems:** none.

- **M0 T6b · Test kit, scenarios, seeded runs, S6 canary** — QA-02 done; PERF-01 in progress;
  CORE-05, CORE-07, ORD-01, QA-01.
  - **Results:**
    - the rest of the test kit:
      - `SimHarness` checks `Invariants` after every step and throws `seed=<n> tick=<t>: …`;
      - `restore`, `replay`, `runUntil`, `run` and `play`;
      - `FakeBodies`, `ThrowingMetrics`, `RandomCommands`, `ReplayRunner`, `JournalFiles`,
        `Seeds`, `Percentiles` and `BudgetCheck`;
    - the scenarios `walking_skeleton`, `out_of_bounds`, `stop_mid_walk`, `patrol` and `canary`;
    - the seed sets `m0-200`, `skeleton-20`, `isolation-50` and `canary`;
    - `:core:test` passes 593 tests in 3.8 s wall, 2.9 s of it in tests (QA-02.1: under 10 s);
    - the walking skeleton arrives exactly at tick 81 for all 20 seeds;
    - 200 seeds × 1,000 ticks of random commands break no invariant;
    - 200 seeds × 2,000 ticks replay with every checkpoint equal;
    - the soak, 200 seeds × 10,000 ticks, takes 8.5 s and is part of `verify` and `verifySoak`;
    - the S6 canary gives equal hashes twice in one JVM and matches its golden
      (`golden/s6-canary.json`);
    - the released save fixture `fixtures/match_v1.json` (the canary at tick 500) decodes to its
      recorded hash, and a test-only step migrates it to version 2;
    - with a checkpoint every tick, dropping the command at tick 137 names tick 137;
    - negative checks, both reverted:
      - a deliberately broken seed failed as `seed=1627107988357113642 tick=501: …` and reran
        alone with `-Dstrategos.seed` (QA-02.2);
      - `testFixturesImplementation libs.assertj` failed `checkTestFixturesDeps` (QA-02.4);
    - CI runs `verify verifySoak`.
  - **Problems:** none in the code. `run` is split into `forScenario` and `play`, so a test can read
    the journal of the run it played (spec §1.6 D row 48).

- **M0 T6a · Journal, snapshot, migrations, replay** — CORE-07 in progress; CORE-05, CORE-03.
  - **Results:**
    - journal recording in INGEST, checkpoints and re-basing in FINAL;
    - `MatchCore.snapshot`, `restore` and `journal`;
    - `JournalCodec`, `ReplayDriver` and `MatchSnapshotCodec` with `"v"` and `Migrations`;
    - `:core:test` passes 140 tests;
    - a 2,000-tick random run replays with all 100 checkpoints equal;
    - removing one recorded command reports the first checkpoint after it;
    - a 500-unit snapshot survives JSON with an equal hash, and the restored core matches the original
      for 200 more ticks;
    - pending commands survive a snapshot.
  - **Problems:** none; the codec's two extra fields are spec §1.6 D row 47.

- **M0 T5b · `MatchCore`, commands, UnitAI, movement** — ORD-01 and QA-02 in progress; CORE-02,
  CORE-03, CORE-05.
  - **Results:**
    - the driving port and its records;
    - `MatchCoreImpl` with the nine-phase step;
    - `StateHasher`;
    - Move, Stop and SpawnUnits rules with ownership and `UNKNOWN_TARGET` for missing and stale
      handles;
    - 256 commands per tick, the rest next tick in order;
    - bus-driven UnitAI and exact movement;
    - the first cut of the test kit (`SimHarness`, clocks, `RecordingMetrics`);
    - `:core:test` passes 124 tests;
    - a clock ten times slower leaves 1,000 ticks of state hashes identical;
    - the spawn positions match an independent implementation of the formula bit for bit.
  - **Problems:**
    - The spec's snap rule `d <= speed` made a 3-4-5 move take 21 steps instead of 20; a 1e-6
      tolerance restores ⌈d / speed⌉ (spec §1.6 D row 46).
    - `MatchCore`'s snapshot and journal belong to T6a (row 45).

- **M0 T5a · Kernel internals** — CORE-01 and CORE-04 in progress; CORE-02, CORE-06.
  - **Results:**
    - `MatchLifecycle`, checked against an independent table of its 15 transitions;
    - `Phase`, `Cadence`, `Stagger` and `Scheduler`;
    - `MatchBus`: FIFO after the current message, a 10,000-deep chain without stack growth;
    - `EntityStore` with `EntitySlots`, which reuses the lowest free index and bumps generations;
    - the component stores;
    - `:core:test` passes 81 tests.
  - **Problems:** the spec put `MatchState` in T5b although T5a's scheduler needs it; T5a brings it
    with only its tick, and `UnitState` with explicit codes (spec §1.6 D row 44). NullAway caught
    an unchecked `EnumMap.get`.

- **M0 T4b · Core `protocol`, `content`** — CORE-03 in progress.
  - **Results:**
    - the sealed `Command` with `Move`, `Stop` and `SpawnUnits`, each hashable;
    - `UnitSet`, capped at 300 on decode too;
    - `CommandKinds`, whose codec dispatches on `kind` and names an unknown one;
    - `QueueMode`, `RejectReason` and `CommandOutcome`;
    - `TemplateSet.builtin()` with the debug walker and a golden hash;
    - `:core:test` passes 61 tests; `verify` passes in 20 s.
  - **Problems:** ErrorProne refused hashing `QueueMode` by its ordinal, which would let a
    reordered enum change state hashes silently; it hashes the mode's id.

- **M0 T4a · Core `shared`, `port`, `diag`** — CORE-05, CORE-02 and QA-10 in progress.
  - **Results:**
    - ids with JSON codecs, `DetMath`, the counter-based `Rng`, `Hasher`, `Hex64`, `Clock`,
      `MetricsSink`, `RingHistogram`, `PhaseTimers` and `MatchLog`;
    - `:core:test` passes 46 tests;
    - the golden values of `Rng` and `Hasher` were computed by an independent Python
      implementation of the spec's formulas, and the Java code matches it bit for bit;
    - `below` passes chi-square 8.6 against the 21.67 limit.
  - **Problems:** the test kit's `ManualClock` arrives in T5b, so `PhaseTimersTest` advances a
    clock lambda by hand.

- **M0 T18 · Continuous integration** — QA-08 and CORE-05 in progress.
  - **Results:**
    - one `ubuntu-24.04` job runs `verify` on pushes to `main` and on pull requests;
    - the first run, with a cold cache, passed in 6 min 11 s (budget 20 min, QA-08.2);
    - [pull request #1](https://github.com/o-e-lytvynskyi/strategos/pull/1) shows the check
      "verify: test results" with the L1, L2, L3 and L5 cases (QA-08.1); `main` was then
      fast-forwarded to the branch;
    - the actions are pinned to the commits of their tags;
    - documentation-only pushes run nothing, and a newer push cancels an older run.
  - **Problems:**
    - The current GitHub tools read a pull request's checks but not runs on `main`, so "Now"
      shows CI as not readable for `main` until the owner's setup item 1b.
    - `jmhSpeedRatio`, the soak and the slow batch join with T12, T6b and T7c
      (spec §1.6 D row 43).

- **M0 T3 · Smoke, the first ArchUnit rules, `verify`, `verifyHarness`, the first S5 report** —
  QA-05 and QA-17 in progress; QA-01, QA-04.
  - **Results:**
    - `verify` is green in 18.9 s warm and 19.8 s after `clean` (budget 5 min);
    - the server smoke takes 6.1 s (budget 60 s), L2 with the game bootstrap 3.8 s;
    - ArchUnit B5 and B11 hold on the shipped classes and flag their fixtures;
    - every `verifyHarness` run ends with its designed verdict;
    - every negative check and probe fails as designed (S5 checks 5–12, 15, 16), and disabling
      the retry-reason rejection makes its inverted guard fail the build;
    - ADR-0007 is Accepted on the S5 evidence ([spikes/S5-build-wiring.md](spikes/S5-build-wiring.md)).
  - **Problems:**
    - The `prepare*` `Sync` tasks stayed UP-TO-DATE after a server run, so the next smoke saw the
      last run's crash report; they now run every time (spec §1.6 D row 42; pitfall in the
      knowledge base).
    - `clientRef` crashes through NeoForge's dist cleaner, not `NoClassDefFoundError`; the guard
      catches it either way.
- **M0 T2 · GameTest harness** — QA-04 in progress, QA-01.
  - **Results:**
    - `runGameTestServer` passes `buildtests.core_in_mod_module` and
      `buildtests.testfixtures_in_mod_module`: core and the test kit run inside the mod module;
    - L3 wall time 1.2 s of the 180 s budget;
    - `verifyFast` plus the GameTest run take 6 s warm;
    - all four negative checks behave as designed (S5 checks 5, 7, 15, 16);
    - both SNBT arenas are generated and committed.
  - **Problems:**
    - Retries never ran on the 1.21.1 GameTest server: its tracker counts only first attempts,
      so it halted before a rerun and exited with the first failure. `RerunTracking` keeps the
      tracker on the newest attempt (spec §1.6 D row 41; pitfall in the knowledge base).
    - ErrorProne flagged `LocalDateTime.now()` without a zone: the report's timestamp is UTC.
    - A review of the commit (three lenses, each finding checked by a skeptic) corrected the
      README's account of a vanilla `server.properties` error. It also hardened the report:
      seconds always in the timestamp, no characters XML 1.0 forbids. Test names now treat a
      digit run as a word (`permission_2`), as T8's names need.
- **M0 T1 · Build skeleton** — QA-01, QA-03, QA-09 and CORE-06 in progress.
  - **Results:**
    - the wrapper checksum matched the MDK's;
    - the daemon runs on Temurin 21;
    - the first build took 55 s: NeoForge 21.1.264 downloaded and decompiled, 787 MiB of assets;
    - a clean rebuild without the build cache takes 5.9 s;
    - `HandleTest` passes 8 tests, `ModLoadsTest` 2 (the mod loads, Dist `DEDICATED_SERVER`);
    - the jar holds `Handle.class`;
    - no Gradle deprecation warnings.
  - **Problems, recorded in spec §1.6 D:**
    - Spotless was loaded twice because `core` and `mod` apply different plugin sets; the root now
      declares the convention plugin with `apply false` (row 38; pitfall in the knowledge base);
    - Gradle 9's property syntax (row 39);
    - a three-lens review with adversarial checks found that `checkVersionCatalog` missed `2+` and
      versions inside `group:name:version` strings (row 40), now proven on a broken catalog; two
      comments that strayed from the spec were removed;
    - `HandleTest` pins the bit layout with literals, since handles are saved.
- **Next:** finish M0 T7c from `wip/M0-T7c` (skill §9: rebase onto `main`, finish, green gate, fast-forward, delete the branch).

## 2026-10-21 — design closed, roadmap approved, autonomy set up

- **Owner decisions.**
  - Approved M0–M6. CMB-08 is deferred, because D-08 chose the observer avatar.
  - Took every decision needed by M6 as recommended.
  - Asked that every unit can always be controlled on its own, which is now pillar P3 and
    ORD-06's criteria 5–6.
  - Allowed commits and pushes to the private repository without asking.
  - Asked for autonomous development with occasional visits.
- **Done:**
  - the design round: three architectures, judged and synthesised into 04-architecture.md;
    ADRs 0001–0023;
  - feasibility and testing documents;
  - the roadmap reviewed by a game designer, a tech lead and QA (224 change requests);
  - the M0 implementation spec (T1–T18);
  - document reconciliation after the approval;
  - the promo draft;
  - the private GitHub repository `o-e-lytvynskyi/strategos`;
  - the autonomy kit: the `strategos-dev` skill, this worklog, the STATE.md layout, and
    `tools/roadmap.py`;
  - a dry run of the kit by a fresh agent: 30 issues, all fixed;
  - a permission allowlist for unattended sessions, proposed in `tools/claude-settings.json`;
  - a way for sessions to read CI (M0 T18 "Reading results").
- **Results:** `tools/roadmap.py check` passes on 245 cards, 0 problems. Open decisions: D-03,
  D-06, D-07, D-20, D-21, all needed after the MVP.
- **Problems:**
  - Usage limits cut several multi-agent runs short. Prefer inline work and small review
    workflows.
  - Auto mode refused to let the assistant write its own permission settings, as it should, so
    the allowlist waits for the owner (STATE "For the owner" 2a).
  - The GitHub MCP server's default tools cannot read Actions runs on `main` (2b).
- **Next:** M0 T1 (build skeleton), as soon as the owner says to start M0.

## 2026-10-20 — research

- **Owner decisions:**
  - Minecraft 1.21.1;
  - a bounded match arena first;
  - solo against an AI first, then 2–4 players;
  - RTS camera plus first person;
  - a hybrid world.
- **Done:**
  - two independent, fact-checked research passes on six topics;
  - a toolchain check: the NeoForge 1.21.1 MDK builds in 98 s on the owner's Mac;
  - decompiled 1.21.1 sources prepared for API checks;
  - tooling and process documents.
- **Next:** design.
