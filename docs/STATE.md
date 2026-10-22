# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-22 · **Owner last seen:** 2026-10-22 · **Current:** M0 Foundation —
Accepted on 2026-10-22; M1 Movement is specified next

## For the owner

Answer in chat or here; the next session applies the answers.

1. **Nothing is waiting from M0.** You accepted it on 2026-10-22 without playing the demo. The demo
   stays ready whenever you want it: `scripts/playtest.sh`, then the steps in
   [testing/playtest.md](testing/playtest.md). Its first use confirms ADR-0022, the one thing your
   acceptance left open.
2. **M1's spec is being written** (`docs/milestones/M1-movement.md`). When it is ready you will be
   asked for «начинай M1»: implementation starts only after it, so you can read the spec first.
3. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
4. **Optional reviews:**
   - the card refinements made after the approval
     ([design/roadmap-changes.md §8](design/roadmap-changes.md));
   - the promo draft ([promo/roadmap-announcement.md](promo/roadmap-announcement.md)).

## Since your last visit

- 2026-10-22 · **Your approvals applied:** the JMH baseline is `perf/baseline-m4pro.json`, so a
  benchmark 15 % slower now fails `verifyPerf`; ADR-0019 is Accepted with every budget unchanged;
  CI runs the benchmarks and the perf tests every night at three times the budgets
  (`perf.yml`, first run green).
- 2026-10-22 · **The client batch:** 25 smoke runs, 12 frame-time runs, the stall check and two
  world switches, each window open 15–30 s; the first frame-time runs with Sodium crashed until
  the client mods moved into the run's `mods` folder. [S3](spikes/S3-client-scenario-harness.md): the window takes the keyboard focus, so Mac
  runs keep needing your consent; the same scenarios render on CI under Linux with no window
  (`scenario.yml`), and once the scene is pinned (anchor, time, chat, camera, vignette) two runs
  match to the pixel. [S4](spikes/S4-client-frame-time.md): 200 bare units cost 0.69 ms of the
  frame with Sodium and the usual client mods, 4.5 ms without them, against 6 ms, so the cap stays
  150 per player. The world switch leaves no match behind (CORE-01.5).
- 2026-10-22 · **Your rule:** a static scene is repeated two or three times, never ten (D-22, the
  skill, QA-18).
- 2026-10-22 · **M0 accepted:** 25 of 26 cards are `accepted`; UNIT-01 closes with NET-01 in M1.

## Now

- **Milestone:** M0 Foundation, started 2026-10-21, Done and Accepted 2026-10-22; spec [milestones/M0-foundation.md](milestones/M0-foundation.md). Next: M1 Movement.
- **Tripwire (M0 only):** T1 started 2026-10-21; T9 green 2026-10-22; met on day 2.
- **In progress:** none.
- **Last finished task:** the M0 client batch and acceptance (2026-10-22).
- **Next task:** M1 · specify Movement (`docs/milestones/M1-movement.md`, the skill's §4).
- **WIP branch:** none.
- **CI:** green on `main`: `verify` on every push, `perf.yml` nightly (run 39740227029 green), `release.yml` and `scenario.yml` by hand; about 220 of 2,000 minutes used this month, as of 2026-10-22.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner |
| M1–M6 (MVP) | approved | Specs are written by the assistant when each milestone starts; M1's is next |
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
