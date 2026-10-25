# Strategos — Claude Code

The project's rules are tool-neutral and live in AGENTS.md, imported here:

@AGENTS.md

## Claude Code specifics

- **The procedure** is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).
- **The main session is the orchestrator** and runs on Opus (`claude --model opus`, or `/model
  opus`). Workers are in `.claude/agents/`: `implementer` and `reviewer` (Sonnet), `runner`,
  `api-lookup` and `recorder` (Haiku). The rules are in [docs/agents.md](docs/agents.md); a new
  role follows the `new-agent` skill.
- **Workflows** (multi-agent scripts) stay rare: they burn the owner's quota. A single reviewer
  agent covers most risky changes.
- **Long runs** go to the background (`run_in_background`), and a run's end is awaited with
  Monitor, never a sleep loop.
