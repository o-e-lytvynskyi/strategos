# claims-in-branch-files on dev/o-e-lytvynskyi/claims-in-branch-files

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/claims-in-branch-files · **Work:** claims-in-branch-files · **Base:** main @ ea87681 · **Claimed:** 2026-10-29 · **Status:** ready for review

**Note:** the owner's request in the maintainer session, 2026-10-29 («мне не нравится один общий файл … давай будем там писать клеймы»); claim and delivery in one pull request on that word.

## Now

- **Work:** the claims registry docs/claims.md is gone; a claim is its branch file. Done; hand-in pull request open.
- **In progress:** none.
- **Merged from main last:** ea87681 (2026-10-29).

## Depends on unfinished work

- None.

## Log

### 2026-10-29 — claims in branch files

- **tools/claims.py** reads the claims from the headers of docs/branches/*.md (on the working tree,
  on `--base`, on `origin/main` for `list` and `owner`): required fields, the six statuses
  (`reserved`, `claimed`, `in progress`, `ready for review`, `merged <date>`, `released <date>`),
  overlaps of active claims, hand-over on a takeover, `ready for review` only on the pull request's
  head (#28). `tools/test_claims.py`: 22 tests in tools pass; `claims.py check --base origin/main`
  11 claims / 0 problems; `roadmap.py check` 245 cards / 0 problems.
- **Migration:** each row's note moved to its branch file's new `**Note:**` line (10 of 10); the
  file's Status was already the claim's state. docs/claims.md deleted.
- **Rules:** docs/branches/README.md now holds what docs/claims.md said (statuses, work notation,
  takeover, forks); AGENTS.md, strategos-dev §1/§2a/§12, strategos-maintain §1/§2/§3/§5/§6,
  CONTRIBUTING.md, onboarding.md, 03-design-process.md, people.md, README.md, STATE.md, the PR and
  issue templates and records.yml's paths point at it.

## For the owner

1. Open pull request #34 (lev1pes, M7) adds a row to docs/claims.md: after this merges it conflicts
   (the file is deleted). Its developer resolves it by dropping the row and moving its note into the
   branch file's `**Note:**`.
2. `.claude/settings.json` needs no change: its allowlist names `python3 tools/claims.py *`, which
   keeps its name and commands.

## Decisions taken

- 2026-10-29 · The folder keeps its name docs/branches/ — renaming would rewrite dozens of links in
  the worklog, STATE and pull requests — undo: `git mv` and a link sweep.
- 2026-10-29 · Statuses are the branch file's own (`claimed` … `merged <date>`); the registry's
  `assigned`/`done` are gone — undo: revert this branch.
- 2026-10-29 · Claim and delivery in one pull request, on the owner's direct word — undo: n/a.

## Pitfalls

## Review

2026-10-29 · a delegated `reviewer` (Sonnet) before the hand-in; fixed in the second commit:
- [major] the `ready for review` rule broke stacked branches (strategos-dev §12.1): now only a file
  already `ready for review` on the base is flagged; a test pins both cases.
- the hand-over is matched as a whole name in `**Note:**` only; active statuses take no trailing
  text; backticks stripped from `Branch`; a base without docs/branches/ gives a message, not a
  traceback; `list` no longer drops a claim with an unknown status; the release pull request is cut
  from `origin/main`; CONTRIBUTING's last "claims row"; STATE's history sentence. 25 tests pass.
- Not done (no regression, recorded): `Branch` vs `Developer` and file-name consistency, two files
  naming one branch, staleness of a never-pushed branch in `list`.
