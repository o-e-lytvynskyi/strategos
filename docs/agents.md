# Delegation: the orchestrator and its workers

How a session splits its work between models so that tokens go where judgement is needed.
The rules are tool-neutral and bind every developer; each tool has its own roster files
(Claude Code: `.claude/agents/*.md`; Codex: see [developer-setup.md](developer-setup.md)).
Adopted 2026-10-25.

> **Status: demo.** The roster below is a first version, written before any trial. Roles,
> models, contracts and the split itself are expected to be improved, reworked or replaced as
> real tasks show what works. Each role's status is in "Role status" below; changes go by pull
> request ("Changing the roster").

## Tiers

| Tier | Model class | Does | Never does |
|---|---|---|---|
| **A — orchestrator** | the strongest (Claude Opus; Codex's top model at high reasoning) | orients, picks the task, reads the spec and ADRs, makes design calls and spec deviations, writes briefs, reads every worker diff, runs the final gate, records decisions, commits, pushes, talks to the owner | read whole build logs, grep sources jars, type bookkeeping by hand when a brief would do |
| **B — worker** | mid-size (Claude Sonnet; Codex's standard coding model) | implements a brief test-first, reviews a diff | commit, push, edit records (branch file, STATE, ROADMAP, worklog), change the spec, spawn agents |
| **C — runner** | small (Claude Haiku; Codex's mini model) | runs commands and summarises them, looks up engine APIs, applies bookkeeping edits whose content the orchestrator gave | judge design, edit code, decide anything |

## The roster

| Role | Tier | Use it for | Returns |
|---|---|---|---|
| `implementer` | B | one task or a slice of one (≈ 50–800 lines), described by a brief | files changed, tests added, the `verifyFast` result, open questions |
| `reviewer` | B (high reasoning) | a diff against its spec, the ADRs and the hard rules: risky changes, a milestone close, another developer's branch | findings ranked by severity with `file:line`, or "no findings" |
| `runner` | C | any Gradle task, `roadmap.py`, a CI run's log, `jmhCompare` | ≤ 30 lines: verdict, failing tests and the first error of each, timings, numbers asked for |
| `api-lookup` | C | "does `X.y()` exist in 1.21.1, what is its signature, who calls it" — sources jar grep, `javap` | signatures with `file:line`, or "not found" plus the nearest names |
| `recorder` | C | the procedure's §5 records from facts the orchestrator lists: spec checkbox, card statuses, the branch file (STATE and the worklog only in a maintainer session), `roadmap.py check` | the files edited and the check's result |

A role that keeps being improvised inline is a candidate for a new one: the `new-agent` skill
(`.claude/skills/new-agent/SKILL.md`) is the procedure.

## When to delegate

- **Delegate** anything whose output is large and whose conclusion is small (runs, logs,
  lookups), and code a brief can describe completely.
- **Keep inline:** edits under ~40 lines in one or two files the orchestrator already has open;
  design and spec decisions; anything that needs the owner's words from this session; the final
  `verify` before a commit when its result decides the commit (a runner may run it, the
  orchestrator reads the verdict).
- **Parallel** workers only on disjoint files, each in its own worktree; at most one Gradle build
  per working copy, and nothing at all next to an L6 measurement or a client window.
- **Escalate, don't loop.** A worker that fails a brief twice hands back; the orchestrator takes
  the task over or rewrites the brief. A runner never "fixes" anything it ran.

## The brief (what the orchestrator sends a worker)

```
Task: M5 T4 · <title> — slice <n> of <m>
Read: docs/milestones/M5-combat.md §4.3 (lines 120–168); card CMB-05 acceptance; ADR-0008
Touch: core/src/main/java/dev/strategos/core/combat/Shot*.java; core/src/test/.../ShotTest.java
Do not touch: anything else; no new dependencies
Behaviour: <the acceptance criteria, numbered, as tests should state them>
APIs already verified: <names and signatures, so the worker does not re-check>
Done when: ./gradlew verifyFast green; ShotTest covers criteria 1–4
Return: the format in the role file
```

A brief cites line ranges, not whole documents, and lists what the orchestrator already knows
so the worker does not rediscover it.

## Quality gates that do not move

- The orchestrator reads `git diff` of every worker change before committing it.
- The `reviewer` runs before committing: an architecture change, a `@HotPath` kernel, a network
  payload, a change over ~500 lines, a milestone close; and in every branch validation
  (`strategos-maintain` §3), from a session that did not build the branch.
- Every gate of the Definition of Done runs as before; delegation changes who reads the log, not
  what must pass.

## Role status

| Role | Status | Trials passed | Since |
|---|---|---|---|
| `implementer` | demo | 0 of 3 | 2026-10-25 |
| `reviewer` | demo | 0 of 3 | 2026-10-25 |
| `runner` | demo | 0 of 3 | 2026-10-25 |
| `api-lookup` | demo | 0 of 3 | 2026-10-25 |
| `recorder` | demo | 0 of 3 | 2026-10-25 |

A trial is one real use on claimed work, logged in the branch file's "Decisions taken" as
`trial <role>: pass | fail — <why>`. It passes when the worker stayed in its brief's scope, its
return kept its format and limit, the orchestrator could act on the return without re-reading
what the worker read, and the result was right (the orchestrator re-ran or read it).

## Changing the roster

Any developer may add a role, change one, or take one out of demo — always by pull request on
their own branch (a claim such as `dev/<you>/agents-<role>`), reviewed and merged like code. The
procedure is the `new-agent` skill.

- **A new or changed role:** the role file in `.claude/agents/`, its row in "The roster" and in
  "Role status" (a changed contract resets its trials), its row in
  [developer-setup.md](developer-setup.md) R2; the trial that justified it, in the pull request.
- **Out of demo — decided by the developer's agent itself:** when a role has passed three trials
  in a row on at least two different tasks with no fail since its last contract change, the agent
  may set it to `stable` without asking: the "Role status" row, the demo line removed from the
  role file, the trials cited, a "Decisions taken" line. It lands through the pull request; the
  review checks the cited trials.
- **Back to demo or retired:** a failed trial on a `stable` role sets it back to `demo` with the
  fix; a role unused for a whole milestone, or always overridden by its brief, is removed.
- A Codex-only role follows the same path; Claude's roster gets the matching file so both tools
  keep parity.

