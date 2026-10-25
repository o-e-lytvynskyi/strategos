# Developer setup

Tool-specific setup for a developer with Claude Code, Codex or another agent. The tool-neutral
part — tools, approach, claim to merge, reports — is [onboarding.md](onboarding.md); read it
first. Every developer is external, the owner's own agent sessions included: the same rules
(AGENTS.md), the same claims, the same review. Written 2026-10-25.

## 1. Everyone

1. **Access:** the owner adds you as a collaborator on `o-e-lytvynskyi/strategos` (private); clone
   over SSH.
2. **A handle:** a short lowercase name for your branches and branch files (`alex`; the owner's
   Claude sessions use `claude-owner`).
3. **Toolchain:** JDK 21 (Gradle's toolchain resolver fetches it), Python 3, `gh` logged in for
   pull requests and CI reads. `./gradlew verifyFast` must pass on a fresh clone.
4. **Read:** AGENTS.md, then the procedure `.claude/skills/strategos-dev/SKILL.md` (plain
   Markdown), docs/agents.md, docs/kb/README.md.
5. **Start:** `python3 tools/claims.py --fetch list`, then claim work (procedure §2a). Ask the
   owner to open a milestone or reserve work for you if nothing is claimable.
6. **Client windows** (L4): goldens are per platform (`mod/src/devtools/README.md`). On a non-Mac
   machine, record new goldens for your platform; the Mac ones are taken at merge on the owner's
   machine.

## 2. Claude Code

Works out of the box: the repository carries everything.

- `CLAUDE.md` imports AGENTS.md; the skills `strategos-dev`, `strategos-maintain`, `new-agent` and
  the workers in `.claude/agents/` load from the repository.
- **Model:** start the main session on Opus (`claude --model opus`, or `/model opus`); it is the
  orchestrator. The workers pick their own models (Sonnet, Haiku).
- **Permissions:** the shared allowlist `.claude/settings.json` lets the work loop run in auto mode
  and denies force pushes and discarding the working tree. Personal additions go to
  `.claude/settings.local.json` (ignored by git); never edit the shared file — propose changes in
  your branch file's "For the owner".
- **Unattended runs:** say «продолжай» / "continue" (or `/loop` for a self-paced run); the skill
  does the rest.

## 3. Codex: a specification

What a Codex developer sets up so that a Codex session follows the same rules and orchestration.
The developer builds the files; this section is the requirement.

### R1 — Orchestrator on the strongest model

The main Codex session runs on the strongest available Codex model at high reasoning effort. It
does tier A's work only (docs/agents.md "Tiers"). Set it in the project's Codex configuration,
not per command, so unattended runs get it.

### R2 — The worker roles with the same contracts

Define, in whatever form the developer's Codex version supports for sub-agents or delegated tasks
(custom agent profiles, named prompts with a model override, or a wrapper that runs `codex exec`
with a profile):

| Role | Tier | Codex model class | Reasoning | May edit | Contract source |
|---|---|---|---|---|---|
| `implementer` | B | standard coding model | medium | the files its brief names | `.claude/agents/implementer.md` |
| `reviewer` | B | standard coding model | high | nothing | `.claude/agents/reviewer.md` |
| `runner` | C | the smallest ("mini") model | low | nothing | `.claude/agents/runner.md` |
| `api-lookup` | C | the smallest model | low | nothing | `.claude/agents/api-lookup.md` |
| `recorder` | C | the smallest model | low | spec Progress, cards, the branch file | `.claude/agents/recorder.md` |

- The roster is a demo (docs/agents.md): expect roles to change; the table here follows them.
  A Codex developer may propose new roles or changes, and take a role out of demo on the
  criteria there — by pull request, like any developer.
- Each role's instructions are the **body** of its Claude file (below the frontmatter), copied or
  referenced verbatim, so both tools share one contract. The frontmatter maps to Codex settings:
  `model` → the tier's model, `effort` → reasoning effort, `disallowedTools` → the role's sandbox
  (read-only for `reviewer`, `runner`, `api-lookup`).
- **Return formats are mandatory** and unchanged: the orchestrator reads only them.
- Workers cannot commit, push or start other workers.
- Store the definitions in the repository (for example `.codex/`) only if they hold no personal
  paths or keys; otherwise keep them local and describe them in your branch file.

### R3 — Sandbox and approvals for unattended work

Equivalent to `.claude/settings.json`: the work loop's commands run without prompts (`./gradlew`,
`python3 tools/roadmap.py`, `python3 tools/claims.py`, `git` add/commit/push/fetch/merge/switch,
`gh pr create|view`, `gh run list|view`, `javap`, reads of `~/.gradle/caches`), and these are
refused: force push, push to `main`, `git reset --hard`, `git clean`, discarding the working
tree, deleting branches. Network access is needed for Gradle and `git push`.

### R4 — Branch discipline

Exactly the procedure: claims, `dev/<developer>/<work>` branches, the branch file as the only
record, `origin/main` merged in and never rebased, commits prefixed `Mx Tn:` and free of tool
traces, hand-in by pull request.

### Acceptance

The setup is done when one real task has gone through it: the orchestrator delegated at least a
runner run and an implementer brief; the workers' returns matched their formats; the task's gate
passed, the branch file was updated, the commit was pushed to the branch; the branch file's
"Decisions taken" says what the Codex setup looks like (models, where the role definitions live),
so the review can account for it.

## 4. Keeping parity

A role added or changed on the Claude side (the `new-agent` skill) is added to R2 in the same
commit; Codex developers mirror it. A role the Codex side needs and Claude's lacks is proposed in
a branch file and added to both through the same skill.
