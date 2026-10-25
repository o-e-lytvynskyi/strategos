# Strategos — rules for every developer and every coding agent

A Minecraft 1.21.1 NeoForge mod that recreates the RTS mechanics of 0 A.D. and Age of
Empires, basics first. Professional bar: clean architecture, best practices, performance,
and automated tests of every intermediate result.

This file is the single source of the project's rules, whatever tool reads it (Claude Code,
Codex or another agent; `CLAUDE.md` imports it).

## Roles

| Role | Who | Does |
|---|---|---|
| **Owner** | the repository's owner | product decisions, roadmap approvals, opening milestones for claims, the decisions kept for the owner below, and every merge into `main` |
| **Maintainer** | the owner, or a session the owner starts for it — never the session that did the work | accepts claim and release pull requests, reviews developer branches with delegated workers, keeps `main`'s records (STATE.md, worklog.md, claims.md), merges code on the owner's word ([strategos-maintain](.claude/skills/strategos-maintain/SKILL.md)) |
| **Developer** | anyone with access: a person, or an agent session (Claude Code, Codex, other) — the owner's own agent sessions included | claims work, builds it on their own branch, hands it in for review ([strategos-dev](.claude/skills/strategos-dev/SKILL.md)) |

Every developer is external, the owner's own agents too: the same claims, the same branch rules,
the same review. That keeps the history transparent and the review objective.

## Start here (a developer session)

The procedure is [.claude/skills/strategos-dev/SKILL.md](.claude/skills/strategos-dev/SKILL.md) — a
plain Markdown document; tools without skills read it directly. In short: orient, claim or
continue your work, build it test-first, verify, record progress in your branch file, commit and
push your branch, repeat until a stop condition; then report.

1. Read `docs/STATE.md` by heading — "Open for claims", "Standing restrictions", "Blocked" — and
   your branch file `docs/branches/<developer>-<work>.md` if you have one.
2. Run `python3 tools/claims.py --fetch list` (the registry [docs/claims.md](docs/claims.md)) and
   `python3 tools/roadmap.py check`.
3. Read only the docs the task needs:

| Question | Where |
|---|---|
| Features, milestones, what the owner approved | [docs/ROADMAP.md](docs/ROADMAP.md), cards in `docs/roadmap/` |
| How the mod is built (authority, tick, units, content, pathing, network, client) | [docs/04-architecture.md](docs/04-architecture.md), decisions in [docs/adr/](docs/adr/README.md) |
| Versions, libraries, dependency policy | [docs/02-tooling.md](docs/02-tooling.md) |
| Test levels, commands, Definition of Done | [docs/05-testing.md](docs/05-testing.md) |
| Roles, feature lifecycle, statuses and evidence, autonomous sessions | [docs/03-design-process.md](docs/03-design-process.md) |
| Engine, toolchain and simulation pitfalls already paid for | [docs/kb/README.md](docs/kb/README.md) |
| Delegating to smaller models: roles, briefs, the roster | [docs/agents.md](docs/agents.md) |
| Joining the project: tools, approach, claim to merge, reports — for any AI tool or none | [docs/onboarding.md](docs/onboarding.md) |
| Setting up your tool (Claude Code, Codex) | [docs/developer-setup.md](docs/developer-setup.md) |
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
  below. Interactive `runClient` and `runServer` sessions are started by a person.
- **Never weaken a check to get green:** no raised thresholds, no retries until green, no test
  marked flaky, no skipped gate.
- **Branches.** A developer commits only to their own `dev/<developer>/<work>` branch, and only
  for work assigned to them in docs/claims.md; nobody but the maintainer writes to `main`. Never force-push, never rewrite pushed
  history, never delete someone else's branch or edits.
- **Permissions.** Unattended sessions run without prompts (Claude Code: auto mode with the
  project allowlist `.claude/settings.json`; Codex: the equivalent approval policy). Never edit
  `.claude/settings.json`; propose changes in your branch file's "For the owner".
- **Git:** commit and push after each green task; English messages with the prefixes of the
  procedure (`M5 T3: …`), no traces of the agent or its tool (no `Co-Authored-By`, no "generated
  with"). The repository is private; making anything public is the owner's decision.
- **Language:** everything in the repository is English; chat in the language of whoever runs
  the session (with the owner: Russian).

## Autonomy by default

Everything a developer session needs is allowed by default (the owner, 2026-10-25): any test
level, client windows under the rules below, subagents and smaller models, background runs, the
decisions you recommend, work without pauses. A restriction the owner gives for a day or a period
(«без окон», «без субагентов») is written into STATE.md "Standing restrictions" and overrides this
section until lifted.

- **Decide and record.** When a choice has a recommended option — a spec deviation, an
  implementation finding, a golden image you checked, a performance baseline whose growth is the
  features' own cost, an MIT/Apache-2.0-compatible library — take it, apply it, and add a line to
  your branch file's "Decisions taken" (what, why, how to undo). The review sees all of them; the
  owner vetoes there.
- **Still the owner's:** anything public (licence, release, repository visibility), money,
  roadmap scope beyond the approved cards (a proposal in "For the owner"), opening a milestone for
  claims, merging into `main`, and the open product decisions listed in STATE.md.
- **Reports: after every milestone, not after every task.** A milestone (epic) ends with a
  mandatory report — the hand-in pull request's body, also posted in chat — and work goes on
  without waiting. Tasks (T) are documented in the repository (spec, cards, branch file), never
  reported one by one. A session that stops says why. No other pauses.
- **No questions with a sensible default.** Ask only what blocks all remaining ready work.

## Client windows (a real Minecraft client)

Allowed by default (the L4 scenario harness, the demo, spikes S3/S4/S9). A window takes the
screen and may take keyboard focus, so:

1. **Batch.** Collect the window runs a task or milestone needs and run them back to back, at the
   end of the task (or in the milestone's consent batch), not one at a time between edits.
2. **One at a time.** Never two windows, never a window during an L6 measurement or another Gradle
   build on the same machine.
3. **Announce, don't ask.** One line in chat before the run: what opens and for how long.
4. **Bounded.** Run in the background with `-Pscenario.timeoutSeconds=<n>`; wait on the run's end,
   never on a sleep loop; the window closes itself when the scenario ends.
5. **Hands off.** Never drive the keyboard, mouse or other windows of the person at the machine;
   scenarios drive the client from code.
6. **No display** (lid closed, screen locked, black frames): do not retry blindly. Use the CI route
   under Xvfb (`.github/workflows/scenario.yml`), record it, and move on.
7. **Evidence.** Look at every new or changed PNG yourself before accepting it; small PNGs worth
   seeing go to `docs/media/<milestone>/`.

## Context economy (mandatory)

A rule, not advice: wasting context is a defect like a red test. Tokens are a budget; quality is
not negotiable.

- Read only the file and section a step needs: search first, then read a range. STATE.md, the
  worklog and the specs are long — read them by heading, never whole.
- Never pull a full build log, a sources-jar listing, a CI log or a wide `grep` into the main
  context. Delegate the run and take its summary ([docs/agents.md](docs/agents.md)), or narrow
  the command (`tail`, `grep -c`, `head`, a path filter) before running it.
- Do not re-read a file you just wrote; do not repeat facts already established in the session.
- Long runs (perf batches, `verifySlow`) go to the background; do other ready work meanwhile.

## Delegation

The strongest model orchestrates; routine work goes to smaller ones. The roles, the brief format,
what never to delegate and the roster per tool are in [docs/agents.md](docs/agents.md). Workers
never commit, push, or edit records; the orchestrator reads every worker's diff before it is
committed. **The current roster is a demo** (2026-10-25): expect it to be improved, reworked or
replaced as trials show what works. Any developer may add, change or retire a role, and their
agent may take a role out of demo on its own when the role meets the criteria in docs/agents.md —
always on their branch, landing by pull request like code.

## Developers and claims

- **Claimable work:** the tasks of a milestone listed in STATE.md "Open for claims" (a whole
  milestone or a run of tasks whose dependencies are met), and planning work — the next
  milestone's spec — at any time. A `reserved` row in [docs/claims.md](docs/claims.md) may be
  claimed only by the developer it names.
- **Assess before claiming:** what the work needs from milestones not yet merged into `main`; build
  independent functionality first, code against a contract where possible, and mark each spot
  that must be finished or validated later with `AFTER-M<n>: …` (listed by `tools/claims.py
  after` and in the branch file's "Depends on unfinished work"). Work that can only follow
  another milestone is not claimed yet.
- **Assignment comes first, by pull request to `main`.** The claim pull request adds your row to
  [docs/claims.md](docs/claims.md) (`assigned`) and your branch file, nothing else;
  `python3 tools/claims.py check --base origin/main` must pass on it. A maintainer merges it
  (procedure: strategos-maintain §3). **No development before it is merged**; meanwhile, work on
  another claim of yours or stop.
- **Nobody takes assigned work without its holder's explicit approval:** the holder releases it
  in their own pull request, or approves the takeover in a comment that your row's note links
  (`handed over by <developer>, <link>`). Only a stale claim (no commit for 7 days) may be
  released by the owner instead.
- **Work on it:** on your `dev/<developer>/<work>` branch; your records are the spec's "Progress",
  the cards' statuses and your branch file (its "Now", log, "For the owner", "Decisions taken");
  never STATE.md or worklog.md. Merge `origin/main` into your branch when `main` moves; never
  rebase a pushed branch.
- **Hand it in:** when the claimed work is done and its gates pass, set the branch file's `Status:
  ready for review`, open the pull request to `main` with the milestone report as its body, and
  claim the next work.
- **Release:** a pull request turning your row to `released`, if you stop.
- **Review and merge:** a maintainer session (never the one that built it) validates the branch
  with delegated workers, fixes or returns it, and merges it on the owner's word; the merge turns
  the row to `done` ([strategos-maintain](.claude/skills/strategos-maintain/SKILL.md)).

## Definition of Done

Tested at the lowest level that can express it; every level its change type requires passes
([ADR-0006](docs/adr/0006-test-levels-and-definition-of-done.md), amended by
[ADR-0022](docs/adr/0022-definition-of-done-amendments.md); summary in
[05-testing.md](docs/05-testing.md#definition-of-done)); no new ErrorProne/NullAway findings;
the branch file, spec progress, roadmap statuses and the affected docs or ADRs updated; new
pitfalls written to [docs/kb/](docs/kb/README.md) in the same session.
