# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-21 · **Owner last seen:** 2026-10-21 · **Current:** M0 Foundation —
specified, not started (waiting for the owner's go-ahead)

## For the owner

Answer in chat or here; the next session applies the answers.

1. **Go-ahead for M0.**
   - **Blocks:** all implementation.
   - **What it starts:** the assistant works through
     [milestones/M0-foundation.md](milestones/M0-foundation.md) task by task. Every task is
     verified, committed and pushed, and this file and the worklog are updated after each one.
   - **How to start:** say «начинай M0».
2. **One-time setup for unattended sessions** (recommended, about five minutes):
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
3. **Consent batch for client windows.** Nothing is queued yet. M0's batch (spikes S3 and S4)
   comes at T16, near the end of M0.
4. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
5. **Optional reviews:**
   - the card refinements made after the approval
     ([design/roadmap-changes.md §8](design/roadmap-changes.md));
   - the promo draft ([promo/roadmap-announcement.md](promo/roadmap-announcement.md)).

## Since your last visit

Nothing yet: the owner was here on 2026-10-21, when design closed. Each finished task or milestone
adds a line here; the list restarts after the owner's next visit.

## Now

- **Milestone:** M0 Foundation, not started (waiting for «начинай M0»); spec [milestones/M0-foundation.md](milestones/M0-foundation.md); 0 of 25 tasks done.
- **Tripwire (M0 only):** not started; M0 work days 0 of 10.
- **In progress:** none.
- **Last finished task:** none.
- **Next task:** M0 T1 · Build skeleton (QA-01, QA-03, QA-09, CORE-06).
- **WIP branch:** none.
- **CI:** none yet (M0 T18 starts right after T3; reading runs on `main` needs item 2b); minutes used this month 0 of 2,000.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

- All implementation: waiting for item 1.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | specified | Spec written; tripwire: walking skeleton within two weeks of M0 work (10 days with M0 commits) |
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
