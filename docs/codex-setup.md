# Codex setup: a specification

For a developer who works on Strategos with OpenAI Codex instead of Claude Code. It says what to
set up so that a Codex session follows the same rules and the same orchestration as the Claude
sessions. The developer builds the files; this page is the requirement, not the implementation.
Written 2026-10-25.

## 1. What already works without setup

- **Rules.** Codex reads `AGENTS.md` at the repository root: the hard rules, autonomy, client
  windows, context economy, delegation, parallel developers, Definition of Done. Nothing in it
  is specific to Claude except the names of Claude's files, which say where to look.
- **Procedure.** `.claude/skills/strategos-dev/SKILL.md` is plain Markdown. The session reads it
  at the start, as AGENTS.md says. §12.2 is the part that applies to a developer branch.
- **Delegation rules.** `docs/agents.md`: tiers, roster, the brief, what never to delegate.

## 2. Requirements

### R1 — Orchestrator on the strongest model

The main Codex session runs on the strongest available Codex model at high reasoning effort.
It does tier A's work only (docs/agents.md "Tiers"). Set it in the project's Codex
configuration, not per command, so unattended runs get it.

### R2 — Five worker roles with the same contracts

Define, in whatever form the developer's Codex version supports for sub-agents or delegated
tasks (custom agent profiles, named prompts with a model override, or a wrapper that runs
`codex exec` with a profile):

| Role | Tier | Codex model class | Reasoning | May edit | Contract source |
|---|---|---|---|---|---|
| `implementer` | B | standard coding model | medium | the files its brief names | `.claude/agents/implementer.md` |
| `reviewer` | B | standard coding model | high | nothing | `.claude/agents/reviewer.md` |
| `runner` | C | the smallest ("mini") model | low | nothing | `.claude/agents/runner.md` |
| `api-lookup` | C | the smallest model | low | nothing | `.claude/agents/api-lookup.md` |
| `recorder` | C | the smallest model | low | spec Progress, cards, the branch file | `.claude/agents/recorder.md` |

- Each role's instructions are the **body** of its Claude file (below the frontmatter), copied or
  referenced verbatim, so both tools share one contract. The frontmatter maps to Codex settings:
  `model` → the tier's model, `effort` → reasoning effort, `disallowedTools` → the role's
  sandbox (read-only for `reviewer`, `runner`, `api-lookup`).
- **Return formats are mandatory** and unchanged: the orchestrator reads only them.
- Workers cannot commit, push or start other workers.
- Store the definitions in the repository (for example `.codex/`) only if they contain no
  personal paths or keys; otherwise keep them local and list them in the branch file.

### R3 — Sandbox and approvals for unattended work

Equivalent to `.claude/settings.json`: the work loop's commands run without prompts (`./gradlew`,
`python3 tools/roadmap.py`, `git` add/commit/push/fetch/merge, `gh run list|view`, `javap`, reads
of `~/.gradle/caches`), and these are refused: force push, push to `main`, `git reset --hard`,
`git clean`, discarding the working tree, deleting branches. Network access is needed for
Gradle and `git push`.

### R4 — Branch discipline

The developer's sessions work on `dev/<developer>/<milestone>` only (AGENTS.md "Parallel
developers", procedure §12.2): bookkeeping in `docs/branches/<developer>-<milestone>.md`, never in
STATE.md or worklog.md; `origin/main` merged in, never rebased; commits prefixed `Mx Tn:` and
free of tool traces.

### R5 — Client windows

Same rules as AGENTS.md "Client windows". If the developer's machine is not a Mac, the L4
goldens are platform-specific (`mod/src/devtools/README.md`): new goldens are recorded under the
developer's platform, and the Mac ones are taken at merge on the owner's machine.

### R6 — Knowledge base

The pet-projects knowledge base is not available to the developer. Pitfalls go to the branch
file's "Pitfalls"; the merge carries them over.

## 3. Acceptance

The setup is done when one real task of the assigned milestone has gone through it:
1. the orchestrator delegated at least a runner run and an implementer brief;
2. the workers' returns matched their formats;
3. the task's gate passed, the branch file was updated, the commit was pushed to the branch;
4. the developer notes in the branch file's "Decisions taken" what the Codex setup looks like
   (models used, where the role definitions live), so the owner's validation can account for it.

## 4. Keeping parity

A role added or changed on the Claude side (`new-agent` skill) is added to the table in R2 in the
same commit; the Codex developer mirrors it. A role the Codex side needs and Claude's lacks is
proposed in the branch file and added to both through the same skill.
