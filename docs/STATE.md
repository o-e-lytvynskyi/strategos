# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-22 · **Owner last seen:** 2026-10-22 · **Current:** M0 Foundation —
Done on 2026-10-22 (its automated gate), Acceptance waits for the owner's session; M1 is
specified next

## For the owner

Answer in chat or here; the next session applies the answers.

1. **M0 is Done: your acceptance session (L7).** Everything automated passes; what is left is
   yours. From the repository root run `scripts/playtest.sh`: it starts the dedicated server in
   the terminal and two clients that join it. Then follow the demo in
   [testing/playtest.md](testing/playtest.md): `op Dev1` in the server console; as Dev1
   `/strategos match create 64 64`, `/strategos debug spawn 3`, `/strategos debug move ~20 ~`;
   `/strategos hash` and `/strategos debug replay`; `stop`, start the server again with
   `mod/build/moddev/runServer.sh`, rejoin, `/strategos match resume m1`; `/tick freeze`, `step 5`,
   `unfreeze`; the singleplayer world switch. Fill in the checklist, the questionnaire and the
   report there, or just tell me what you saw. «M0 принимаю» makes the cards `accepted`; any bug
   becomes the first task.
2. **JMH baseline to approve (M0 T12).** `perf/candidate-m4pro.json` is the first JMH run on this
   Mac (µs per operation): one core step with 600 walking units 14.1; the state hash of 600 units
   22.8, allocating nothing (0.16 B/op); encoding 500 units to JSON 220; 1,000 bus messages 13.7;
   the phase timers 0.113, 0.8 % of a step. Answer «одобряю бейзлайн» and the next session copies
   it to `perf/baseline-m4pro.json`: from then on `verifyPerf` fails when a benchmark gets 15 %
   slower, and CI gets its perf step. For scale, in a real server a match tick with 600 walking
   units takes about 0.05 ms (p50) and 0.3 ms (p99) against budget A's 5 and 10 ms, and saving a
   500-unit match about 1 ms against budget G's 100 ms.
3. **Performance budgets to approve (M0 T14, PERF-01.3).** ADR-0019's
   [revision proposal](adr/0019-performance-budgets.md#revision-proposal-from-m0-2026-10-22-proposed-awaiting-owner-approval)
   keeps every budget and the whole budget matrix as they are: what M0 measured is 20 to 100 times
   inside them, but the systems that will spend them (pathfinding, combat, economy, AI) come later.
   Answer «одобряю бюджеты» and the ADR becomes Accepted (budget F stays provisional until S4).
4. **Consent batch for client windows (M0 T16, D-22).** The client harness is built and has never
   been run. M0's batch opens a game window on this Mac and can take the keyboard focus, so it runs
   only when you say so, at a time that suits you:
   - S3: `./gradlew :mod:runScenario -Pscenario=smoke` ten times, `-Pscenario=stall
     -Pscenario.timeoutSeconds=20` once, and the production jar in a separate client profile
     ([plan](spikes/S3-client-scenario-harness.md));
   - S4: `-Pscenario=s4_units` at 200, 500 and 1,000 units, render distance 16 and 24, with and
     without `-Pcompat` ([plan](spikes/S4-client-frame-time.md));
   - CORE-01.5: `-Pscenario=world_switch`.

   Answer «согласен на клиентский батч» (optionally with a time), and a session runs it while you
   are away from the keyboard. None of it blocks M0's Done.
5. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
6. **Optional reviews:**
   - the card refinements made after the approval
     ([design/roadmap-changes.md §8](design/roadmap-changes.md));
   - the promo draft ([promo/roadmap-announcement.md](promo/roadmap-announcement.md)).

## Since your last visit

- 2026-10-22 · **M0 T7c Body and match GameTests, the batch-end check:** unit bodies and matches are
  now tested in a real server — made, posed, re-made, never saved, never pushed, no vibrations, and
  paused or disposed matches leave nothing behind; every GameTest batch ends with a check for leaked
  tickets, matches and bodies.
- 2026-10-22 · **M0 T8 Commands, the walking skeleton end to end, the playtest kit:** `/strategos`
  commands create matches, spawn and order units and print hashes; the walking skeleton and 50
  side-by-side matches run as GameTests with exactly the hashes of their plain-JUnit twins, and
  their journals replay to the same hashes; the server smoke now runs a match. One command,
  `scripts/playtest.sh`, starts a server and two clients for you
  ([testing/playtest.md](testing/playtest.md)). Cards QA-04 and CORE-03 are done.
- 2026-10-22 · **M0 T9 Persistence and quarantine:** matches made with `/strategos match create`
  are saved with the world and come back `PAUSED` after a restart, with the same tick and hash; a
  damaged save is never overwritten: the match comes back `FAULTED` with the reason, and a file
  the game cannot read is kept with a copy beside it. Demo step 5 (stop, restart, resume) is ready
  to try. The two-week tripwire did not fire: the walking skeleton was green on the second work
  day, and nothing was cut.
- 2026-10-22 · **M0 T9b Restart round trip and the holder's lifetime:** `verify` now stops the test
  server and starts it again: the saved match comes back with the same tick and hash, one body per
  unit, and walks on exactly as one uninterrupted run would.
- 2026-10-22 · **M0 T11 Static gates complete:** every architecture rule of the spec is now
  checked on each build (determinism, layering, no client code on servers, no shared static
  state, and more), each proved to catch its own deliberate violation; the list of the mod's
  hooks into Minecraft is checked against the code. Card CORE-06 is done.
- 2026-10-22 · **M0 T10 Fault containment complete, log tagging:** an error in one match stops
  only that match; it leaves a report in `strategos/faults/` from which the error replays, and
  players standing in the match are told. Every log line about a match names it and its tick.
  Cards CORE-10 and QA-10 are done.
- 2026-10-22 · **M0 T11b Fixture catalogue and its checks:** every test fixture is catalogued, and
  the build now fails when a fixture a test needs is missing, a released save fixture is deleted
  or a test hard-codes a seed. Cards QA-17 and CORE-12 are done.
- 2026-10-22 · **M0 T12 Performance harness:** benchmarks of the core's kernels and two
  performance tests in a real server, each gate proved to fail on a synthetic overload and to
  refuse a verdict on a busy machine. The numbers are far inside the budgets; the benchmark
  baseline waits for your approval (item 2 above). Cards QA-07, CORE-02 and CORE-07 are done.
- 2026-10-22 · **M0 T13 Spike S1, server cost per body:** a unit body costs the server about
  0.1 µs a tick, about 40 times less than a vanilla mob (4.9 µs; 2.2 with Lithium). The default
  population cap stays at 150 per player, as D-01 planned ([report](spikes/S1-server-cost-per-body.md)).
  Cards PERF-02 and QA-05 are done.
- 2026-10-22 · **M0 T14 Spike S2, force-loaded arena cost, and the budget proposal:** a 384²
  arena kept loaded with nobody online costs the server 0.06 ms a tick, and saving it about 5 ms,
  far inside the limits of 4 and 100 ms; units keep moving there for hours. All three arena sizes
  are affordable ([report](spikes/S2-force-loaded-arena.md)). The budget proposal waits for you
  (item 3 above). Card PERF-03 is done.
- 2026-10-22 · **M0 T16 Client harness prepared:** scripted client scenarios (screenshots, a
  world switch, frame times with many units) are built and compile, but have not run: they open a
  game window, so they wait for your consent (item 4 above).
- 2026-10-22 · **M0 T15 Build and determinism reports:** the build wiring and the determinism
  canary are confirmed with their final numbers (a clean full check in 39 s; the same results on
  macOS and Linux). Cards QA-01, QA-03 and CORE-05 are done.
- 2026-10-22 · **M0 Foundation is Done:** all 25 tasks, in two working days; every automated check
  of the milestone passes ([results](milestones/M0-foundation.md#101-results-m0-t17-2026-10-22)).
  Eighteen cards are done; the other eight wait only for you: the acceptance session (item 1),
  the two approvals (items 2 and 3) and the consent batch (item 4). The next sessions write M1's
  spec; when it is ready you will be asked for «начинай M1».

## Now

- **Milestone:** M0 Foundation, started 2026-10-21 (owner's go-ahead); spec [milestones/M0-foundation.md](milestones/M0-foundation.md); 25 of 25 tasks done; Done 2026-10-22, Acceptance waits for the owner.
- **Tripwire (M0 only):** T1 started 2026-10-21; T9 green 2026-10-22; M0 work days 2 of 10.
- **In progress:** none.
- **Last finished task:** M0 T17 (2026-10-22); T15 was commit 34fbe38, its release workflow 125268c.
- **Next task:** M1 · specify Movement (`docs/milestones/M1-movement.md`, the skill's §4).
- **WIP branch:** none.
- **CI:** green on `main` (M0 T15's release workflow, run 39697922471, 5 min 33 s) and the manual release run 39697935330 (the production jar on a headless client); minutes used this month about 170 of 2,000, as of 2026-10-22.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | Done 2026-10-22; Acceptance waits | Started 2026-10-21; tripwire met on day 2; 18 of 26 cards done, 8 wait for the owner's session, approvals or consent |
| M1–M6 (MVP) | approved | Specs are written by the assistant when each milestone starts |
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
  walking skeleton, the performance harness and spikes S1, S2, S5 and S6, with S3 and S4 prepared.

Details are in [worklog.md](worklog.md).

## Known follow-ups (not blocking)

- [01-feasibility.md](01-feasibility.md) §3 predates the architecture in places; its precedence
  note says the architecture and the ADRs win. §6.5 was re-estimated after M0 (2026-10-22).
- ADRs owed by future cards:
  - ECO-03's leaf rule, which amends ADR-0021;
  - ECO-16's fauna archetype, which amends ADR-0011.
- T18's CI perf step comes with the approved JMH baseline (item 2), since it needs it
  (`jmhSpeedRatio`).
