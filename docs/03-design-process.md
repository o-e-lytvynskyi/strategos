# 03 — How we design and build

This is the working agreement between the owner and the AI assistant (Claude Code) for
Strategos: who decides what, where every artifact lives, and how a feature goes from an idea
to something playable and tested.

## Roles

| | Owner | Assistant |
|---|---|---|
| Product | Decides product questions; approves, rejects or changes roadmap features; accepts milestone demos | Proposes features, options and recommendations |
| Design | Approves decisions that change the product or add runtime dependencies | Writes specs, ADRs and the architecture; runs spikes and reports numbers |
| Code | Reviews when they want to | Implements, tests and documents |
| Tests | Approves new golden images and performance baselines; plays milestone demos and multiplayer sessions | Runs every automated level that ends on its own (L0–L3, L5, L6). A client window (L4) opens only with the owner's consent, because it can take keyboard focus |
| Runs | Starts interactive `runClient` / `runServer` sessions themselves, to watch the logs | Never starts interactive or long-running sessions |
| Git | Decides on tags, releases and anything public; the repository stays private unless the owner decides otherwise | Commits and pushes to the private repository without asking (owner, 2026-10-21), in English, with no assistant traces |

## Where things live

| Artifact | Purpose | Updated |
|---|---|---|
| [STATE.md](STATE.md) | **Where things stand now**: the owner's queue ("For the owner"), the current milestone and next task ("Now"), blockers | After every task |
| [worklog.md](worklog.md) | Append-only session log, newest first: what was done, results with numbers, problems, next | After every task |
| `tools/roadmap.py` | `check`: ROADMAP.md and the card files agree and statuses are valid; `summary`: progress per milestone and open decisions | Run after every status change |
| `.claude/skills/strategos-dev/` | The assistant's work procedure for autonomous sessions | When the procedure changes |
| [ROADMAP.md](ROADMAP.md) | Milestones and feature cards with a status the owner edits | When features are proposed, approved, changed or finished |
| [01-feasibility.md](01-feasibility.md) | Complexity, realism, risks | When estimates change materially |
| [02-tooling.md](02-tooling.md) | Platform, versions, libraries, dependency policy | On every version bump or new dependency |
| 03-design-process.md | This document | When the way we work changes |
| [04-architecture.md](04-architecture.md) | The architecture of the mod | With every ADR that changes it |
| [05-testing.md](05-testing.md) | Test levels, commands, Definition of Done | When a test level or gate changes |
| [adr/](adr/) | One file per lasting decision: context, decision, consequences, alternatives. Accepted ADRs are not rewritten; a new ADR supersedes them | When a decision is made |
| `milestones/` | Implementation spec of each milestone, written before its code | Before a milestone starts; ticked off while it runs |
| `spikes/` | Experiment reports with measured numbers | After each spike |
| [research/](research/) | Evidence behind the decisions | Rarely; new research gets its own file |
| `design/` | Records of the design process: competing proposals, judgements, roadmap reviews | When a design round runs |
| `CLAUDE.md` (repo root) | Instructions for the assistant: commands, conventions, rules | When conventions change |

## Feature lifecycle

```mermaid
stateDiagram-v2
    [*] --> proposed: idea from owner or assistant
    proposed --> approved: owner
    proposed --> rejected: owner
    proposed --> deferred: owner
    proposed --> changed: owner edits scope
    changed --> approved: owner
    deferred --> proposed: revisited
    approved --> specified: milestone spec written
    specified --> in_progress
    in_progress --> done: Definition of Done met
    done --> accepted: owner plays the milestone demo
```

- Every idea, from either side, enters [ROADMAP.md](ROADMAP.md) as a `proposed` card with
  testable acceptance criteria.
- Only `approved` cards enter a milestone spec. Changing an approved card sets it to
  `changed` until the owner approves it again.
- M0 "Foundation" is infrastructure the owner asked to start, so its cards were pre-approved
  (`approved-foundation`); they are `specified` since its implementation spec was written.

## Milestone cycle

1. **Spec.** Turn the milestone's approved cards into `milestones/Mx-<name>.md`: ordered
   tasks, files, classes, tests per level, risks with fallbacks. Unknowns become spikes that
   run first.
2. **Build.** Task by task. Every task ends green on its own verification command. L0 and
   L1 run continuously; every gameplay change gets a GameTest (L3).
3. **Verify.** The full `verify` run; performance against the baseline (L6); client
   scenarios (L4) with the owner's consent.
4. **Demo.** The owner starts a dedicated server and two clients with QA-09's one launch
   command, follows the demo script from the milestone spec, and their feedback becomes roadmap
   changes.
5. **Close.** Update STATE.md, the roadmap statuses, the knowledge base (new pitfalls and
   recipes) and the docs. The assistant commits and pushes; the owner decides on tags and releases.

## Design rules

1. **Decisions with lasting consequences get an ADR**, with the alternatives that were
   rejected and why.
2. **Measure before optimising.** Performance claims come from spikes with numbers in
   `spikes/`, not from estimates.
3. **Rules in `core`, Minecraft in `mod`.** Game rules and state transitions are written in
   the Minecraft-free core and tested there first.
4. **Content is data.** Units, buildings, techs and civilisations are templates, not classes.
   A new unit should normally need no Java code.
5. **Server-authoritative.** The client sends intents; the server validates and decides.
   Hidden information never leaves the server.
6. **Keep the seams.** Rendering is "extract state, then draw"; the UI draws view-models;
   input goes through our own action layer; persistence and networking go through codecs.
   This keeps the future 26.x port and the open-world mode affordable.
7. **Clean-room.** No GPL or all-rights-reserved code or assets, ever (see
   [research/README.md §8](research/README.md)). MIT/Apache code may be adapted with its
   notice kept in `NOTICE`.
8. **Small vertical slices.** Each milestone ends with something the owner can play and the
   tests that prove it; there is no big-bang integration at the end.

## Definition of Done

A change is done when:
- its behaviour is tested at the lowest level that can express it, and every test level the
  change type requires passes (table in
  [ADR-0006](adr/0006-test-levels-and-definition-of-done.md), amended by
  [ADR-0022](adr/0022-definition-of-done-amendments.md); summary in
  [05-testing.md](05-testing.md#definition-of-done));
- the build has no new ErrorProne warnings and NullAway is clean;
- golden images and performance baselines changed only with the owner's approval;
- the docs it affects are updated (STATE.md, ROADMAP.md statuses, the architecture or a new
  ADR when a decision changed);
- any new pitfall is written to the knowledge base in the same session.

## Autonomous development

The assistant develops the project in sessions the owner starts and rarely watches. The
procedure is the `strategos-dev` skill; this section fixes the conventions it follows.

**Starting work.** The owner opens Claude Code in the repository (`minecraft/strategos`, so that
the project settings load) and says «продолжай» (or uses `/loop` for a self-paced run).
Unattended sessions run in auto mode. The project allowlist (`.claude/settings.json`, activated
by the owner on 2026-10-21) lets the work loop's commands
through without prompts and denies force pushes and discarding the working tree; the assistant
never edits it and proposes changes in "For the owner". The assistant works through ready tasks
until a stop condition: nothing is ready, a task failed three times, a budget is red, a hard
rule would be broken, or usage is running low. Sessions can end at any moment, so every finished step is committed and
pushed, and an unfinished task lives on a `wip/Mx-Tn` branch named in STATE.md.

**Gates.** Implementing a milestone starts only after the owner's go-ahead for it («начинай Mx»),
recorded in STATE.md; planning work (writing the next milestone's spec) needs none. One session
works at a time (a lock, `.session.lock` in the repository root, which git ignores).

**Statuses and evidence.** ROADMAP.md is the source of truth; card files mirror it, and
`tools/roadmap.py check` must pass after every change.

| Status | Set by | When |
|---|---|---|
| `approved` | owner | The owner approved the card |
| `specified` | assistant | Its milestone spec is written |
| `in_progress` | assistant | The first task implementing it starts |
| `done` | assistant | Every acceptance criterion passes at its level; the card gets `- **Done:** <date> — <evidence>` (test names, reports, spec task) |
| `accepted` | assistant, on the owner's word | The owner played the milestone demo, or accepted the milestone without it (M0, 2026-10-22) |
| `changed` | assistant or owner | The scope must change; it waits for the owner |

Milestone specs keep a "Progress" checklist (`[x] T3 (date)`); commits are named
`Mx Tn: <summary>`, so `git log --grep "Mx Tn"` finds every task's work.

**Refinements.** When an acceptance criterion cannot be tested as written, the assistant may make a
scope-neutral refinement — another test level, a measuring method, clearer wording — and records it
in [design/roadmap-changes.md](design/roadmap-changes.md) "Refinements". Anything that changes what
a card delivers sets it to `changed` and waits for the owner.

**Green `main`.** Nothing reaches `main` without `verify` once it exists (M0 T3); before that, the
gate is `verifyFast` plus the task's own verification command, and documentation-only commits
need `python3 tools/roadmap.py check`. Once CI exists (M0 T18), every push
is checked on GitHub Actions as well, and a red build is the first thing the next session fixes.
Sessions read those runs through the GitHub MCP server's `actions` tools or a logged-in `gh`, a
one-time owner setup; a pull request's checks are readable without either.

**The owner's queue.** Everything that needs the owner — decisions, consent batches for client
windows, demos to play, baselines and goldens to approve, proposals that add scope — is a
numbered item in STATE.md "For the owner", with options and a recommendation. Work that does
not depend on it continues; the assistant never waits idle on one item while other work is ready.

**Consent batches.** Runs that open a game window are queued, not run. When the owner says
«запускай консент-батч», the assistant runs the whole batch in one go while the owner is away from
the keyboard, and records the results.

**When the owner visits** (five minutes):
1. Read STATE.md: "For the owner", "Since your last visit" and "Now".
2. Skim worklog.md since the last visit; optionally run `python3 tools/roadmap.py summary` or
   look at the commits and CI on GitHub.
3. Answer the queue: decisions, consent, approvals; play the demo when a milestone is Done.
4. Say «продолжай».

## Versions and git

- The mod follows semantic versioning, `0.x` until the MVP. The version lives in
  `gradle.properties`.
- Trunk-based on `main`; short-lived branches only when a change needs isolation.
- Commit messages are English, imperative, with no assistant traces. The assistant commits and
  pushes to the private GitHub repository without asking (owner, 2026-10-21).

## Communication

- Chat is in Russian; everything in the repository is in English.
- Every report states what was done, the test results with numbers, and what needs the
  owner: approvals, decisions, consent for a client run.
