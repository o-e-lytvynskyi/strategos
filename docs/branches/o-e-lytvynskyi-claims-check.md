# claims-check on dev/o-e-lytvynskyi/claims-check

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/claims-check · **Work:** claims-check · **Base:** main @ 0456984 · **Claimed:** 2026-10-29 · **Status:** merged 2026-10-29

## Now

- **Work:** issue [#28](https://github.com/o-e-lytvynskyi/strategos/issues/28), done; hand-in pull request open.
- **In progress:** none.
- **Last finished task:** the row/branch-file check, its tests and the records it found (2026-10-29).
- **Next task:** none; the review.
- **Merged from main last:** 2075428 (2026-10-29).

## Depends on unfinished work

- None.

## Log

### 2026-10-29 — the check

- **claims-check** — `tools/claims.py check` now compares every registry row with its branch file's
  Status (`assigned` ↔ `claimed`/`in progress`, `done` ↔ `merged <date>`, `released` ↔ `released
  <date>`), requires a branch file for an `assigned` row, and treats a `ready for review` file on any
  branch but the pull request's head (`--head`, default `$GITHUB_HEAD_REF` or the checked-out branch;
  a fork's `<login>/strategos:` prefix is matched) as a delivery merged without records. 10 new tests
  in `tools/test_claims.py` (22 in tools, all pass); `roadmap.py check` 245 cards / 0 problems.
- On `main` it found three: `lev1pes-m7-spec.md` `ready for review` and the `M7 spec` row `assigned`
  after delivery PR #32 (merged 2026-10-28) — now `merged 2026-10-28` / `done`; `dyadik-m6-spec.md`
  `ready for review` after #16 — now `merged 2026-10-25`; `lev1pes-m5.md` `claimed` under a
  `released` row (#18) — now `released 2026-10-26`. `claims.py check --base origin/main`: 9 claims,
  0 problems.
- Docs: docs/branches/README.md (the pairs), docs/claims.md (the rule and the gate), strategos-maintain
  §5.2 (run the check on the records commit).

### 2026-10-29 — claim

- **claims-check · claim** — assessment: tooling and records only, no milestone dependency; overlaps no active claim.

## For the owner

- PR #32 (M7 spec) was merged without its other records (STATE "Milestones", worklog), the gap #28
  describes; this branch fixes only the claim row and the branch file. A maintainer session should
  fold the rest.

## Decisions taken

- 2026-10-29 · Offline check from the files on the branch, no GitHub API — the `records` gate already
  runs on every change to `docs/branches/**`, needs no token, and a hand-in always changes its branch
  file — undo: drop `record_problems` from `check`.
- 2026-10-29 · A stray `ready for review` file fails *every* later pull request to `main`, not only
  the delivery that left it — the delivery's own pull request cannot be told from its records, so the
  next one catches it; a developer whose branch predates the fix clears it by merging `origin/main` —
  undo: report it as a warning instead of a problem.
- 2026-10-29 · Wrote three records of other developers' merged or released work on `main` (status
  lines only, dates from the GitHub merge times) — the check cannot pass without them, and they only
  state what GitHub already shows — undo: revert those three lines and the M7 spec row.

## Pitfalls

## Review
