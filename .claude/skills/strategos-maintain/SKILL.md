---
name: strategos-maintain
description: Maintainer procedure for Strategos — the only path into main. Use when the owner asks to validate, review, fix or merge a developer branch («провалидируй <ветку>», «проверь работу коллеги», «влей <ветку>», «с правками»), to open a milestone for claims or reserve work for someone («открой M5», «отдай M5 <кому>»), to set or lift a standing restriction, to answer developers' questions, or to update main's records (STATE.md, worklog.md) and process documents on the owner's word.
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
   "Assignments", "Blocked"; `gh pr list --state open`.
5. **If the owner speaks in this session:** `Owner last seen: <today>` in STATE's header, and
   "Since your last visit" becomes `Nothing yet: the owner was here on <today>.`

## 2. Apply the owner's word (commits `Docs: …` on `main`)

The maintainer may commit to `main` directly only for records and process on the owner's word;
code reaches `main` only through §4.

- **Open a milestone for claims** («открой M5», «начинай M5»): a line in STATE "Open for claims"
  (milestone, date, the owner's words); the "Milestones" row `open for claims`; ROADMAP.md's
  "Owner approval" note `<milestone> opened <date>`; README.md "Status". A standing word («все
  утверждённые вехи открыты») is recorded as such.
- **Reserve work** («отдай M5 <кому>»): a row in STATE "Assignments" (work, developer, date). The
  developer claims it themselves (strategos-dev §2a).
- **Standing restrictions:** add or delete the line in STATE "Standing restrictions", with its
  date and end.
- **Answers to developers:** write the owner's answer under the item in STATE "For the owner"
  (and in the pull request, if one is open); the developer reads it there.
- **Lift developers' questions:** items from `claims.py owner` go into STATE "For the owner",
  numbered, naming the branch.
- **Decisions vetoed** by the owner: a line under the item; the developer undoes it on the branch,
  or, if merged, it becomes claimable work in "For the owner" → a `fix-…` claim.

## 3. Validate a branch («провалидируй <branch>»)

1. `git fetch`; `git log --oneline main..origin/<branch>`; `git diff --stat main...origin/<branch>`.
2. Check the branch out in its own worktree (`git worktree add ../strategos-review
   origin/<branch>`), so `main`'s working copy stays untouched.
3. In that worktree, through `runner`: `./gradlew verify verifySlow verifySoak`, and
   `:core:jmh :core:jmhCompare` when the core's step, codecs or a `@HotPath` kernel changed. The
   pull request's CI run is read too (`gh pr checks <n>`).
4. `reviewer` on `main...origin/<branch>` with the spec sections, the cards and the branch file's
   "Decisions taken" as input. For a large milestone, one reviewer per area (core rules,
   adapters, client), at most three.
5. Check the records: `tools/roadmap.py check`; spec "Progress" against the commits; every `done`
   card's evidence exists; the branch file's log matches the commits; new kb entries are general
   facts, not project decisions.
6. Check the hard rules a reviewer may miss: new runtime dependencies (`gradle/libs.versions.toml`,
   `NOTICE`), `.claude/settings.json` untouched, no tool traces in commit messages (`git log
   --format=%B main..origin/<branch> | grep -i -E 'co-authored|generated with|claude|codex'`).
7. Write the verdict into the branch file's "Review" (on the branch, commit `Mx: review <date>`)
   and as a pull request comment, and a numbered "For the owner" item on `main`: findings by
   severity, gate results with numbers, the "Decisions taken" the owner may want to veto, a
   recommendation — merge / merge after fixes / back to the developer.
8. **Fixes.** On the owner's «с правками»: fix on the branch in the review worktree, commit
   `Mx Tn: review fix — <what>`, push the branch, note it in the pull request. Without it, the
   findings go back to the developer.

## 4. Merge («влей <branch>», only on the owner's word)

1. If `main` moved since the validation, merge `origin/main` into the branch in the review
   worktree, re-run step 3.3's gate, push the branch.
2. On `main`: `git merge --no-ff origin/<branch> -m "Mx: merge <branch> after review"`.
3. In the same merge, before committing it (`git merge --no-commit` first), fold the branch file
   into `main`'s records:
   - its "Log" entries into docs/worklog.md, dated, at the top in order;
   - "Since your last visit": one line per task or milestone it delivered;
   - "Decisions taken": its lines, marked with the branch;
   - STATE "Now", "Milestones" table and the header's "Current" from the merged state;
   - the "For the owner" items it raised: still open ones stay, answered ones go;
   - the branch file's header `Status: merged <date>`; "Assignments" row deleted.
   - Cards become `accepted` only on the owner's word after the demo, here or later.
4. `./gradlew verify` on `main`, then `git push`. The pull request closes as merged.
5. `git worktree remove ../strategos-review`. The branch stays on `origin` until the owner
   deletes it.
6. Stacked claims based on the merged branch: tell their developers in STATE to merge
   `origin/main`.

## 5. Keep `main`'s records

- **STATE.md** shape: header (Last update, Owner last seen, Current), "For the owner", "Since your
  last visit", "Now" (`main`'s state: milestones, last merge, CI, minutes used, overview
  commands), "Open for claims", "Standing restrictions", "Decisions taken", "Assignments",
  "Blocked", "Milestones", "History", "Known follow-ups".
- **CI minutes** (GitHub Free, 2,000 a month for private repositories): above 80 %, move CI to
  nightly and say so in STATE.
- **Stale claims:** a claim with no commit for 7 days — ask its developer in "For the owner";
  with the owner's word, release it (`Status: released <date>` on the branch, a line in STATE).
- **Old `wip/*` branches** from before 2026-10-25: list them for the owner to delete.

## 6. Report

In the owner's language (Russian): what was validated or merged, gates with numbers, the
findings, the open "For the owner" items, links to the pull requests, the branch files and the
commits.
