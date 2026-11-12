# Claims and developer branches

Who works on what. **A claim is its branch file**, `<developer>-<work>.md` in this folder: one file
per claim, no shared registry, so claims made side by side never conflict. It reaches `main` by a
pull request before any development starts, so two developers never hold the same work (AGENTS.md
"Developers and claims"; `python3 tools/claims.py list` shows them, `check` guards them). On the
branch the file replaces STATE.md "Now", "For the owner", "Decisions taken" and the worklog; at the
merge the maintainer folds it into them and keeps it here as the record.

A copy of this folder on a development branch is a snapshot; the current claims are [on
main](https://github.com/o-e-lytvynskyi/strategos/tree/main/docs/branches).

- **Claim:** a pull request `Claim: <work>` that adds your branch file (`Status: claimed`) — or
  turns a `reserved` file in your name to `claimed` — and nothing else. Develop only after it is
  merged.
- **Work** is written `M5` (every task of its spec), `M5 T1–T6` or `M5 T1, T3`, `M6 spec`, or a
  free name (`fix-main`, `agents-runner`).
- **Status** (the header's last field): `reserved` (the owner set it aside for the named developer;
  written by the maintainer), `claimed`, `in progress`, `ready for review` (the hand-in pull
  request is open), `merged <date>` (delivered into `main`; written by the maintainer at the
  merge), `released <date>` (given up; the work is claimable again). The first four are active.
- **`**Note:**`**, an optional line under the header: the pull requests that assigned, delivered or
  released the work, a hand-over, the owner's words.
- **Taking over** work someone else holds needs their explicit approval: they release it in their
  own pull request, or approve yours in a comment; your file's `**Note:**` says `handed over by
  <developer>, <link>`. A claim with no commit for 7 days is stale: the owner may release it.
- **Forks** claim the same way, from a fork; `Branch` names it `<login>/strategos:dev/<handle>/<work>`.
  They rank below collaborators: when a fork's claim and a collaborator's compete for the same work,
  the collaborator's is accepted first, whatever their order; the owner accepts outside claims by hand.
- **`claims.py check`** (the `records` gate on every pull request touching this folder): the header
  fields `Developer`, `Branch`, `Work`, `Status` present and spelled as here; a known Status, dated
  where it must be; no two active claims covering the same work; a takeover names its hand-over; and
  `ready for review` only on the pull request's own branch or on a branch it stacks on — a file that is
  already `ready for review` on `main` is a delivery merged without its records, and every later pull request stays red until the maintainer sets
  `merged <date>` (issue #28).

## Template

```markdown
# <work> on dev/<developer>/<work>

**Developer:** <handle> (<tool and model, e.g. Claude Code, Opus>) · **Branch:** dev/<developer>/<work> (a fork: <login>/strategos:dev/<developer>/<work>) · **Work:** <M5 | M5 T1–T6 | M6 spec> · **Base:** <main @ commit | dev/... @ commit> · **Claimed:** <date> · **Status:** claimed

**Note:** <optional: the claim's pull request, a hand-over, the owner's words>

## Now

- **Milestone:** <milestone>, <n> of <total> tasks done; spec <link>.
- **In progress:** <task> since <date> | none.
- **Last finished task:** <task> (<date>), commit <hash> | none.
- **Next task:** <task> · <title>.
- **Merged from main last:** <commit> (<date>).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current. `- None.` when empty.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|

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
