---
name: strategos-maintain
description: Maintainer procedure for Strategos — the only path into main. Use when the owner asks to accept claim or release pull requests («прими клеймы»), to validate, review, fix or merge a developer branch («провалидируй <ветку>», «проверь работу коллеги», «влей <ветку>», «с правками»), to open a milestone for claims or reserve work for someone («открой M5», «отдай M5 <кому>»), to set or lift a standing restriction, to answer developers' questions, or to update main's records (STATE.md, worklog.md) and process documents on the owner's word.
---

# Strategos: the maintainer

The maintainer keeps `main`: it validates what developers hand in, merges on the owner's word,
and keeps `main`'s records. Rules: AGENTS.md "Roles" and "Developers and claims"; the developer's
side is the `strategos-dev` skill (§12 hand-in).

**Objectivity.** A maintainer session never validates work it built. If this session (or this
conversation) developed the branch, say so and ask the owner to start a fresh one. The
validation reads the branch as a stranger would: from the diff, the spec and the branch file.

Commands are spelled the way `.claude/settings.json` expects.

## 1. Orient

1. The lock (`.session.lock`) as in strategos-dev §1.1.
2. `main`'s working copy clean (`git status`); `git switch main && git pull --ff-only`.
3. `python3 tools/claims.py --fetch list` and `python3 tools/claims.py owner`.
4. docs/STATE.md by heading: "For the owner", "Open for claims", "Standing restrictions",
   "Blocked"; docs/claims.md; `gh pr list --state open` (claim, release and hand-in pull requests).
5. **If the owner speaks in this session:** `Owner last seen: <today>` in STATE's header, and
   "Since your last visit" becomes `Nothing yet: the owner was here on <today>.`

## 2. Apply the owner's word (commits `Docs: …` on `main`)

The maintainer may commit to `main` directly only for records and process on the owner's word;
claims and releases go through §3, code only through §4 and §5.

- **Open a milestone for claims** («открой M5», «начинай M5»): a line in STATE "Open for claims"
  (milestone, date, the owner's words); the "Milestones" row `open for claims`; ROADMAP.md's
  "Owner approval" note `<milestone> opened <date>`; README.md "Status". A standing word («все
  утверждённые вехи открыты») is recorded as such.
- **Reserve work** («отдай M5 <кому>»): a `reserved` row in docs/claims.md (work, developer, date,
  the owner's words in the note). The developer claims it themselves (strategos-dev §2a).
- **Release a stale claim** (no commit for 7 days, holder unreachable): only on the owner's word;
  the row turns `released` with the owner's words in the note.
- **Standing restrictions:** add or delete the line in STATE "Standing restrictions", with its
  date and end.
- **Answers to developers:** write the owner's answer under the item in STATE "For the owner"
  (and in the pull request, if one is open); the developer reads it there.
- **Lift developers' questions:** items from `claims.py owner` go into STATE "For the owner",
  numbered, naming the branch.
- **Decisions vetoed** by the owner: a line under the item; the developer undoes it on the branch,
  or, if merged, it becomes claimable work in "For the owner" → a `fix-…` claim.

## 3. Claim and release pull requests («прими клеймы», or at every maintainer session)

Records only, so the maintainer merges them without waiting for the owner's word (unless "Standing
restrictions" says otherwise). Delegated whole to `runner`; the orchestrator reads its verdict and
merges.

1. `gh pr list --state open --search 'Claim: OR Release: in:title'`; oldest first — the first
   claim merged wins.
2. For each, `runner` reports: `gh pr diff <n> --name-only` (only docs/claims.md and one new
   `docs/branches/*.md` for a claim; only docs/claims.md for a release); in a worktree of the pull
   request's head, `python3 tools/claims.py check --base origin/main`.
3. Also check: the work is open (STATE "Open for claims") or planning; a `reserved` row is claimed
   only by its developer; a takeover's note links the holder's approval (their release pull
   request, or their comment on this one); a milestone claim's branch file has its "Depends on
   unfinished work" filled; a release comes from the holder, or carries the owner's
   word for a stale claim.
4. All pass: `git merge --no-ff origin/<branch> -m "Docs: accept claim <work> (#<n>)"` on `main`,
   push; the pull request closes as merged. Then tell the developer in the pull request to merge
   `origin/main` and start (`gh pr comment`).
5. Any fails: a pull request comment with the reason (`gh pr comment <n> --body …`), no merge. An
   overlap with an earlier merged claim: close it with that reason.

## 4. Review a branch («провалидируй <branch>», or a hand-in pull request)

The review is delegated so that the expensive model judges and the cheap ones read. Tiers as in
docs/agents.md:

| Step | Who | What comes back |
|---|---|---|
| Diff map | `runner` (Haiku) | commits, `--stat`, the files by area (core rules, adapters, client, data, docs), lines per area |
| Gates | `runner` (Haiku), in the background | verdicts with numbers: `verify verifySlow verifySoak`, `jmhCompare` if needed, the pull request's CI |
| Records and rules | `runner` (Haiku) | `roadmap.py check`, `claims.py check`, Progress against commits, evidence paths exist, the trace-free commit grep |
| Code review | `reviewer` (Sonnet, high), one per area with real code, at most three | ranked findings with `file:line` |
| Verdict | the orchestrator (Opus) | reads the returns only; spot-reads the diff where a finding or a deviation needs judgement; decides |

Cost rules:
- **Size the review to the diff.** Under ~300 lines of code: one reviewer over everything. Docs
  only: no reviewer; the records check is enough. Over ~3,000 lines: split by area.
- **Do not re-run what already ran on the same commit.** If the pull request's CI passed on the
  head commit, `verify` is not repeated locally; `verifySlow`, `verifySoak` and `jmhCompare` are
  (CI does not run them on pull requests).
- **Give reviewers the context, not the repository:** the diff range, the spec sections and card
  criteria by line range, the branch file's "Decisions taken"; never "read the docs".
- **One pass, then targeted re-checks:** after fixes, review only the commits since the verdict.

Steps:
1. `git fetch`; a worktree for the branch (`git worktree add ../strategos-review
   origin/<branch>`), so `main`'s working copy stays untouched.
2. Launch the gates (`runner`, background) and the diff map (`runner`) together.
3. From the diff map, brief the `reviewer`s (per area) and the records check (`runner`) together.
4. **Agent roster changes** in the branch (`.claude/agents/`, docs/agents.md): each new or changed
   role cites its trial; a role set to `stable` has the trials docs/agents.md "Role status"
   requires, in the branch files; developer-setup.md R2 matches.
5. **Unfinished dependencies:** `python3 tools/claims.py after` in the worktree; every marker is in
   the branch file's "Depends on unfinished work"; none waits for a milestone already merged; the
   marked parts are seams or fakes, not hidden behaviour. A claim pull request for a milestone
   carries the assessment in its branch file (§3 checks it is there).
6. **Hard rules a reviewer may miss:** new runtime dependencies (`gradle/libs.versions.toml`,
   `NOTICE`), `.claude/settings.json` untouched, no tool traces in commit messages.
7. **The verdict**, written by the orchestrator into the branch file's "Review" (on the branch,
   commit `Mx: review <date>`), as a pull request review (`gh pr review <n> --comment
   --body-file …`), and as a numbered "For the owner" item on `main`: findings by severity, gate
   results with numbers, the "Decisions taken" the owner may want to veto, a recommendation —
   merge / merge after fixes / back to the developer.
8. **Fixes.** On the owner's «с правками»: small fixes inline, larger ones by an `implementer`
   brief, in the review worktree; commit `Mx Tn: review fix — <what>`, push the branch, note it in
   the pull request. Without it, the findings go back to the developer.

## 5. Merge («влей <branch>», only on the owner's word)

1. If `main` moved since the validation, merge `origin/main` into the branch in the review
   worktree, re-run §4's gates (delegated), push the branch.
2. On `main`: `git merge --no-ff origin/<branch> -m "Mx: merge <branch> after review"`.
3. In the same merge, before committing it (`git merge --no-commit` first), fold the branch file
   into `main`'s records:
   - its "Log" entries into docs/worklog.md, dated, at the top in order;
   - "Since your last visit": one line per task or milestone it delivered;
   - "Decisions taken": its lines, marked with the branch;
   - STATE "Now", "Milestones" table and the header's "Current" from the merged state;
   - the "For the owner" items it raised: still open ones stay, answered ones go;
   - the branch file's header `Status: merged <date>`; the docs/claims.md row `done`, today.
   - Cards become `accepted` only on the owner's word after the demo, here or later.
4. `./gradlew verify` on `main`, then `git push`. The pull request closes as merged.
5. `git worktree remove ../strategos-review`. The branch stays on `origin` until the owner
   deletes it.
6. Stacked claims based on the merged branch: tell their developers in STATE to merge
   `origin/main`.

## 6. Keep `main`'s records

- **STATE.md** shape: header (Last update, Owner last seen, Current), "For the owner", "Since your
  last visit", "Now" (`main`'s state: milestones, last merge, CI, minutes used, overview
  commands), "Open for claims", "Standing restrictions", "Decisions taken",
  "Blocked", "Milestones", "History", "Known follow-ups".
- **CI minutes** (GitHub Free, 2,000 a month for private repositories): above 80 %, move CI to
  nightly and say so in STATE.
- **Stale claims:** a claim with no commit for 7 days — ask its developer in "For the owner";
  with the owner's word, release it (`Status: released <date>` on the branch, a line in STATE).
- **Old `wip/*` branches** from before 2026-10-25: list them for the owner to delete.

## 7. Report

In the owner's language (Russian): what was validated or merged, gates with numbers, the
findings, the open "For the owner" items, links to the pull requests, the branch files and the
commits.
