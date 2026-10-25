# Strategos — rules for every coding agent

A Minecraft 1.21.1 NeoForge mod that recreates the RTS mechanics of 0 A.D. and Age of
Empires, basics first. Professional bar: clean architecture, best practices, performance,
and automated tests of every intermediate result.

This file is the single source of the project's rules, whatever tool reads it (Claude Code,
Codex or another agent; `CLAUDE.md` imports it). "The agent" below is whoever does the work;
"the owner" is the repository's owner.

## Start here

Every work session follows the procedure in
[.claude/skills/strategos-dev/SKILL.md](.claude/skills/strategos-dev/SKILL.md) — a plain
Markdown document; tools without skills read it directly. In short: orient, pick the next ready
task, build it test-first, verify, record progress, commit and push, repeat until a stop
condition; then report. The owner visits rarely and expects the repository itself to say what
happened.

1. Read [docs/STATE.md](docs/STATE.md) — the owner's queue, "Now", "Assignments", blockers — and
   the newest entry of [docs/worklog.md](docs/worklog.md). On a developer branch, read its
   `docs/branches/<branch>.md` instead (see "Parallel developers").
2. Run `python3 tools/roadmap.py check` and `summary`.
3. Read only the docs the task needs:

| Question | Where |
|---|---|
| Features, milestones, what the owner approved | [docs/ROADMAP.md](docs/ROADMAP.md), cards in `docs/roadmap/` |
| How the mod is built (authority, tick, units, content, pathing, network, client) | [docs/04-architecture.md](docs/04-architecture.md), decisions in [docs/adr/](docs/adr/README.md) |
| Versions, libraries, dependency policy | [docs/02-tooling.md](docs/02-tooling.md) |
| Test levels, commands, Definition of Done | [docs/05-testing.md](docs/05-testing.md) |
| Roles, feature lifecycle, statuses and evidence, autonomous sessions | [docs/03-design-process.md](docs/03-design-process.md) |
| Delegating to smaller models: roles, briefs, the roster | [docs/agents.md](docs/agents.md) |
| What is realistic, risks, MVP | [docs/01-feasibility.md](docs/01-feasibility.md) |
| Evidence: engine facts, prior art, mechanics | [docs/research/README.md](docs/research/README.md) |
| The current milestone's implementation spec | `docs/milestones/` |

## Hard rules

- **Clean-room.** Never open GPL or all-rights-reserved sources: Reign of Nether, MineColonies,
  Structurize, BlockUI, Ancient Warfare 2, Villager Recruits and family, 0 A.D. code or data —
  not even on the web. Use the behavioural notes in `docs/research/`. MIT/Apache-2.0 code may be
  adapted with its notice in `NOTICE` ([ADR-0005](docs/adr/0005-clean-room-and-licensing.md)).
- **Verify engine APIs before relying on them.** Grep the decompiled Minecraft 1.21.1 + NeoForge
  sources (`neoforge-<ver>-sources.jar` from `createMinecraftArtifacts`); FML, the event bus and
  ModDevGradle are not in that jar, so check them with `javap` on the cached jars (procedure
  §3.3). Never trust memory for class, method or event names.
- **Rules in `core`, Minecraft in `mod`.** `core` never imports Minecraft; game state is owned by
  the core ([ADR-0008](docs/adr/0008-authority-and-state-model.md)).
- **Runs.** Run only tasks that end on their own: builds, `:core:test`, `:mod:test`,
  `runGameTestServer`, `runServerSmoke`, perf batches, and client-window runs under the rules
  below. Interactive `runClient` and `runServer` sessions are the owner's.
- **Never weaken a check to get green:** no raised thresholds, no retries until green, no test
  marked flaky, no skipped gate.
- **Permissions.** Unattended sessions run without prompts (Claude Code's auto mode with the
  project allowlist `.claude/settings.json`; Codex's equivalent approval policy). Force pushes and
  discarding the working tree are denied. Never edit `.claude/settings.json`; propose changes in
  "For the owner".
- **Git:** commit and push to the private GitHub repository (`o-e-lytvynskyi/strategos`, over SSH)
  without asking, after each green task; English messages, no traces of the agent or its tool
  (no `Co-Authored-By`, no "generated with"). Making anything public is the owner's decision.
- **Language:** everything in the repository is English; chat with the owner is Russian.

## Autonomy by default

Since 2026-10-25 the owner's standing word is: work without pauses, take the decisions you
recommend, run client windows when the work needs them. A restriction the owner gives for a day
or a period («без окон», «без субагентов») is written into STATE.md "Standing restrictions" and
overrides this section until lifted.

- **Decide and record.** When a choice has a recommended option — a spec deviation, an
  implementation finding, a golden image you checked, a new performance baseline whose growth is
  the features' own cost, a licence-compatible library — take it, apply it, and add a line to
  STATE.md "Decisions taken" (what, why, how to undo). The owner vetoes by answering there.
- **Still the owner's:** anything public (licence, release, repository visibility), money,
  roadmap scope beyond the approved cards (a proposal in "For the owner"), merging another
  developer's branch into `main`, and the open product decisions listed in STATE.md.
- **No pauses for reports.** Report only when a stop condition of the procedure holds. Progress
  lives in the repository (STATE, worklog, commits), not in chat.
- **No questions with a sensible default.** Ask only what blocks all remaining ready work.

## Client windows (a real Minecraft client)

Allowed by default (the L4 scenario harness, the demo, spikes S3/S4/S9). A window takes the
screen and may take keyboard focus, so:

1. **Batch.** Collect the window runs a task or milestone needs and run them back to back, at
   the end of the task (or in the milestone's consent batch), not one at a time between edits.
2. **One at a time.** Never two windows, never a window during an L6 measurement or another
   Gradle build in the same working copy.
3. **Announce, don't ask.** One line in chat before the run: what opens and for how long.
4. **Bounded.** Run in the background with `-Pscenario.timeoutSeconds=<n>`; wait on the run's
   end, never on a sleep loop; the window closes itself when the scenario ends.
5. **Hands off.** Never drive the owner's keyboard, mouse or other windows; scenarios drive the
   client from code.
6. **No display** (lid closed, screen locked, black frames): do not retry blindly. Use the CI
   route under Xvfb (`.github/workflows/scenario.yml`), record it, and move on.
7. **Evidence.** Look at every new or changed PNG yourself before accepting it; small PNGs the
   owner should see go to `docs/media/<milestone>/`.

## Context economy

Tokens are a budget, quality is not negotiable.

- Read only the file and section a step needs: search first, then read a range. STATE.md and
  worklog.md are long — read the sections named in "Start here", never the whole files.
- Never pull a full build log, a sources jar listing or a CI log into the main context. Delegate
  the run and take a summary ([docs/agents.md](docs/agents.md)), or tail and grep it.
- Do not re-read a file you just wrote; do not repeat facts already established in the session.
- Long runs (perf batches, `verifySlow`) go to the background; do other ready work meanwhile.

## Delegation

The strongest model orchestrates; routine work goes to smaller ones. The roles, the brief
format, what never to delegate and the roster per tool are in [docs/agents.md](docs/agents.md).
Workers never commit, push, or edit STATE, ROADMAP or the worklog; the orchestrator reads every
worker's diff before it is committed.

## Parallel developers

A milestone or a set of tasks may be assigned to another developer (a person with their own
agent) working on a branch. The procedure's §12 has the details; the rules:

- **Assignment** is a row in STATE.md "Assignments" (work, branch, developer, since), written on
  `main` by the owner or on the owner's word. Sessions on `main` never pick assigned work.
- **Branch** `dev/<developer>/<milestone>`, cut from `main`, pushed to `origin`. Commits keep the
  prefixes (`M5 T3: …`). Never push `main`; never force-push (keep up with `main` by merging
  `origin/main` into the branch).
- **Bookkeeping on the branch:** the milestone's spec "Progress", its cards in ROADMAP.md and
  `docs/roadmap/`, and `docs/branches/<developer>-<milestone>.md` — the branch's own "Now", log,
  decisions taken and pitfalls. The branch does not edit STATE.md or worklog.md; the merge folds
  its file into them.
- **Integration:** the owner validates the branch (procedure §12, usually with their agent),
  fixes or asks for fixes, and merges it into `main`.

## Definition of Done

Tested at the lowest level that can express it; every level its change type requires passes
([ADR-0006](docs/adr/0006-test-levels-and-definition-of-done.md), amended by
[ADR-0022](docs/adr/0022-definition-of-done-amendments.md); summary in
[05-testing.md](docs/05-testing.md#definition-of-done)); no new ErrorProne/NullAway findings;
STATE.md (or the branch file), roadmap statuses and the affected docs or ADRs updated; new
pitfalls recorded in the same session — in the pet-projects knowledge base when it is on disk,
otherwise in the branch file's "Pitfalls" for the merge to carry over.
