# Strategos — Claude Code

The project's rules are tool-neutral and live in AGENTS.md, imported here. They apply to every
Claude Code user equally — the owner's sessions and any other developer's:

@AGENTS.md

## Claude Code specifics

- **Procedures** are skills: `strategos-dev` (a developer session), `strategos-maintain`
  (validating and merging branches, `main`'s records), `new-agent` (a new worker role).
- **The main session is the orchestrator** and runs on Opus (`claude --model opus`, or `/model
  opus`). Workers are in `.claude/agents/` — a demo roster: `implementer` and `reviewer`
  (Sonnet), `runner`, `api-lookup` and `recorder` (Haiku). Rules: [docs/agents.md](docs/agents.md).
- **Setup** for a new developer: [docs/developer-setup.md](docs/developer-setup.md). Personal
  settings go to `.claude/settings.local.json` (ignored by git); the shared allowlist is
  `.claude/settings.json`.
- **Workflows** (multi-agent scripts) stay rare: they burn the usage quota. A single reviewer
  agent covers most risky changes.
- **Long runs** go to the background (`run_in_background`), and a run's end is awaited with
  Monitor, never a sleep loop.
