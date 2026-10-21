# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-22 · **Owner last seen:** 2026-10-22 · **Current:** M0 Foundation —
started 2026-10-21 (owner's go-ahead)

## For the owner

Answer in chat or here; the next session applies the answers.

1. **Consent batch for client windows.** Nothing is queued yet. M0's batch (spikes S3 and S4)
   comes at T16, near the end of M0.
2. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
3. **Optional reviews:**
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

## Now

- **Milestone:** M0 Foundation, started 2026-10-21 (owner's go-ahead); spec [milestones/M0-foundation.md](milestones/M0-foundation.md); 16 of 25 tasks done.
- **Tripwire (M0 only):** T1 started 2026-10-21; T9 green 2026-10-22; M0 work days 2 of 10.
- **In progress:** none.
- **Last finished task:** M0 T9b (2026-10-22); T9 was commit f4eefff.
- **Next task:** M0 T11 · Static gates complete (QA-17, CORE-05, CORE-06, QA-10).
- **WIP branch:** none.
- **CI:** green on `main` (M0 T7b review fix, run 39685642374, 6 min 28 s); minutes used this month about 83 of 2,000, as of 2026-10-22.
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
