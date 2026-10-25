# Developer branches

One file per branch that another developer works on (AGENTS.md "Parallel developers",
procedure §12). Named `<developer>-<milestone>.md`. On the branch it replaces STATE.md "Now"
and the worklog; on merge it is folded into them and kept here as the record.

## Template

```markdown
# <milestone> on dev/<developer>/<milestone>

**Developer:** <name> · **Assigned:** <date> · **Base:** <commit on main> · **Status:** in progress | ready for review | merged <date>

## Now

- **Milestone:** <milestone>, <n> of <total> tasks done; spec <link>.
- **In progress:** <task> since <date> | none.
- **Last finished task:** <task> (<date>), commit <hash> | none.
- **Next task:** <task> · <title>.
- **Merged from main last:** <commit> (<date>).

## Log

Newest first; one entry per session in the worklog's shape.

### <date> — <milestone> <tasks>

- **<task> · <title>** — <cards>; <results with numbers>; <problems met>.
- **Next:** …

## Decisions taken

- <date> · <what> — <why> — <how to undo>.

## Pitfalls

General Minecraft/NeoForge pitfalls for the pet-projects knowledge base, carried over at merge.

## Review

Written by the validation (procedure §12.3): gate results, findings, recommendation.
```
