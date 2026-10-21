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

Nothing yet: the owner was here on 2026-10-22.

## Now

- **Milestone:** M0 Foundation, started 2026-10-21 (owner's go-ahead); spec [milestones/M0-foundation.md](milestones/M0-foundation.md); 12 of 25 tasks done.
- **Tripwire (M0 only):** T1 started 2026-10-21; T9 green not yet; M0 work days 1 of 10.
- **In progress:** M0 T7c since 2026-10-21, on the WIP branch below (the session stopped as usage ran low).
- **Last finished task:** M0 T7b (2026-10-21); T7a was commit 65dbc69.
- **Next task:** M0 T7c · Body and match GameTests, the batch-end check (UNIT-01, CORE-01, CORE-06, QA-04, QA-01).
- **WIP branch:** `wip/M0-T7c` (pushed, commit c064b5f): the ticket controller, the batch-end check, `MatchFixture` and `JUnitXmlReporter#recordFailure`, unverified; what is left is in the worklog's newest entry.
- **CI:** green on `main` (M0 T7b, run 39670656358: 825 tests, the S6 canary on Linux against the macOS golden); minutes used this month about 61 of 2,000, as of 2026-10-22.
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
