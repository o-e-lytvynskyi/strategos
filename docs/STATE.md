# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-21 · **Owner last seen:** 2026-10-21 · **Current:** M0 Foundation —
started 2026-10-21 (owner's go-ahead)

## For the owner

Answer in chat or here; the next session applies the answers.

1. **One-time setup for unattended sessions** (recommended, about five minutes):
   - **a. Activate the permission allowlist.**
     - **What it does:** [tools/claude-settings.json](../tools/claude-settings.json) lets the
       work loop's commands through without prompts: Gradle, git, `tools/roadmap.py`, unpacking
       sources, the knowledge-base commit. It denies force pushes, `reset --hard`, `clean` and
       similar commands that would discard work.
     - **Why you:** the assistant may not grant itself permissions; auto mode refused it, rightly.
     - **How:** review the file, then run in `minecraft/strategos`:
       `git mv tools/claude-settings.json .claude/settings.json && git commit -m "Activate the Claude Code allowlist" && git push`.
       The next session sees the commit and updates the docs that mention the proposal.
     - **Without it:** auto mode still works, but its classifier may refuse a push or a clone
       in a session nobody watches.
   - **b. Let sessions read GitHub Actions.**
     - **When:** from M0 T18, right after T3.
     - **Options:** enable the `actions` toolset of the GitHub MCP server (recommended; it is off
       by default and needs no new software); or install `gh` (`brew install gh`) and run
       `gh auth login`.
     - **Until then:** T18 is verified on a pull request, whose checks the current tools can
       read, and "Now" says `CI: not readable` for `main`.
   - **Blocks:** nothing yet; after T18, without (b) only you would notice a red CI on `main`.
2. **Consent batch for client windows.** Nothing is queued yet. M0's batch (spikes S3 and S4)
   comes at T16, near the end of M0.
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

The owner was here on 2026-10-21 and started M0. Since then:

- 2026-10-21 · **M0 T1 Build skeleton:** the build runs (Gradle 9.2.1 on Java 21, NeoForge
  21.1.264); `verifyFast` and the first tests are green; three spec corrections (§1.6 D, rows
  38–40).
- 2026-10-21 · **M0 T2 GameTest harness:** the GameTest server runs our suites and writes a JUnit
  report that the build checks; declared retries work after a fix for a vanilla 1.21.1 bug
  (§1.6 D row 41).
- 2026-10-21 · **M0 T3 Smoke, ArchUnit, `verify`:** the commit gate `verify` exists and is green in
  19 s, with every guard proven to bite; ADR-0007 is Accepted on the first S5 report
  ([spikes/S5-build-wiring.md](spikes/S5-build-wiring.md)).
- 2026-10-21 · **M0 T18 CI:** GitHub Actions runs `verify` on Linux for every push to `main` and every
  pull request; the first run passed in 6 min 11 s, and
  [pull request #1](https://github.com/o-e-lytvynskyi/strategos/pull/1) shows the test results.
- 2026-10-21 · **M0 T4a Core `shared`, `port`, `diag`:** ids with codecs, deterministic maths, the
  counter-based RNG and the state hasher (pinned against an independent implementation), phase
  timers and the match log.
- 2026-10-21 · **M0 T4b Core `protocol`, `content`:** the commands `Move`, `Stop` and `SpawnUnits`
  with their JSON codec and catalogue, `UnitSet`, the reject reasons, and the built-in unit
  template.
- 2026-10-21 · **M0 T5a Kernel internals:** the match lifecycle, the nine-phase scheduler with
  cadences and staggering, the per-match message bus, and the entity store with generational
  handles and component stores.
- 2026-10-21 · **M0 T5b `MatchCore`, commands, movement:** the match kernel steps nine phases; Move,
  Stop and SpawnUnits validate and apply in canonical order with one feedback record each; units
  walk and land exactly on their targets; the state hash ignores the clock.
- 2026-10-21 · **M0 T6a Journal, snapshot, migrations, replay:** every match keeps a journal of its
  inputs with state-hash checkpoints; a 2,000-tick run replays to the same hash; a 500-unit
  snapshot survives JSON; migrations run before decoding.
- 2026-10-21 · **M0 T6b Test kit, scenarios, seeded runs, S6 canary:** the shared scenarios (the
  walking skeleton among them) run in plain JUnit; the match invariants are checked after every
  tick; 200 seeds replay to the same hash over 2,000 ticks and, in the soak, 10,000; the
  determinism canary has its golden hashes; the first released save fixture is committed. Card
  QA-02 is done; `:core:test` runs 593 tests in 3.8 s.
- 2026-10-21 · **M0 T7a Unit entity, registry, renderer:** the unit body `strategos:unit_humanoid`
  is registered (never saved, never summoned, no physics), with its 13-byte spawn data and a
  placeholder Steve renderer on the client; the dedicated server and the GameTest server start with
  it.
- 2026-10-21 · **M0 T7b Holder, match registry, tick driver, bodies:** a server now keeps its
  matches: they are created in a checked space, stepped every level tick before the tracker, and
  their units get bodies (at most 64 new ones per server tick), which are re-made when something
  removes them; a fault stops only its match. Matches exist only in memory until T9.

## Now

- **Milestone:** M0 Foundation, started 2026-10-21 (owner's go-ahead); spec [milestones/M0-foundation.md](milestones/M0-foundation.md); 12 of 25 tasks done.
- **Tripwire (M0 only):** T1 started 2026-10-21; T9 green not yet; M0 work days 1 of 10.
- **In progress:** none.
- **Last finished task:** M0 T7b (2026-10-21); T7a was commit 65dbc69.
- **Next task:** M0 T7c · Body and match GameTests, the batch-end check (UNIT-01, CORE-01, CORE-06, QA-04, QA-01).
- **WIP branch:** none.
- **CI:** green on [pull request #1](https://github.com/o-e-lytvynskyi/strategos/pull/1); runs on `main` are not readable yet (item 1b); minutes used this month about 7 of 2,000, as of 2026-10-21.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | in progress | Started 2026-10-21; tripwire: walking skeleton within two weeks of M0 work (10 days with M0 commits) |
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

Details are in [worklog.md](worklog.md).

## Known follow-ups (not blocking)

- [01-feasibility.md](01-feasibility.md) §3 predates the architecture in places; its precedence
  note says the architecture and the ADRs win. Re-estimate it after M0.
- ADRs owed by future cards:
  - ECO-03's leaf rule, which amends ADR-0021;
  - ECO-16's fauna archetype, which amends ADR-0011.
- S5 confirms or updates ADR-0007's batch list, and measures `verify`'s 5-minute budget with the
  harness runs (risk R20).
