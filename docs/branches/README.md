# Developer branches

One file per developer branch (AGENTS.md "Developers and claims"). Named
`<developer>-<work>.md`, created by the branch's first commit: that commit is the claim, and
`python3 tools/claims.py list` shows every claim on `origin`. On the branch the file replaces
STATE.md "Now", "For the owner", "Decisions taken" and the worklog; on merge the maintainer folds
it into them and keeps it here as the record.

The header's fields are read by `tools/claims.py`; keep their spelling. `Status` is one of
`claimed`, `in progress`, `ready for review`, `merged <date>`, `released <date>`.

## Template

```markdown
# <work> on dev/<developer>/<work>

**Developer:** <name> (<tool and model, e.g. Claude Code, Opus>) · **Branch:** dev/<developer>/<work> · **Work:** <M5 | M5 T1–T6 | M6 spec> · **Base:** <main @ commit | dev/... @ commit> · **Claimed:** <date> · **Status:** claimed

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

## For the owner

Numbered; what is needed, the options with a recommendation, what is blocked. `- None.` when empty.

## Decisions taken

- <date> · <what> — <why> — <how to undo>.

## Pitfalls

New pitfalls go straight into `docs/kb/` on the branch; list here which entries were added.

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
```
