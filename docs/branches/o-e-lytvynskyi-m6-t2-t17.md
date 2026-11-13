# m6-t2-t17 on dev/o-e-lytvynskyi/m6-t2-t17

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m6-t2-t17 · **Work:** M6 T2–T17 · **Base:** dev/dyadik/m6-t2-t17 @ 7f08f40 · **Claimed:** 2026-11-14 · **Status:** in progress

**Note:** taken over on the owner's word, 2026-11-14 («давай перехватим задачу и добьем ее»); dyadik's claim was released by the owner in [PR #54](https://github.com/o-e-lytvynskyi/strategos/pull/54), not handed over by its holder; the work continues from dyadik's head 7f08f40 ([PR #53](https://github.com/o-e-lytvynskyi/strategos/pull/53)), whose commits and branch-file log stay in the history.

## Now

- **Milestone:** M6 MVP: First skirmish, the claim's T2.1–T12.2 done by dyadik (13 of 29 tasks with the T2 and T12 splits); spec [M6-first-skirmish.md](../milestones/M6-first-skirmish.md).
- **In progress:** M6 T13–T17's tests and gates since 2026-11-14 (T13; T14 with T17; then T15, T16).
- **Last finished task:** M6 T12.2 (2026-11-13, dyadik), commit 0c767da.
- **Next task:** M6 T13 · MATCH-02 skirmish setup and launch — its code is in (`WIP M6 T13`); write its tests and run its gates; then T14–T17 the same way.
- **Merged from main last:** 8111c0a (2026-11-13, through dyadik's branch).
- **Taken over in this state (7f08f40):** CI `verify` green; local `verifySlow` green (20 required GameTests, 3 min 19 s) and `verifySoak` green, 2026-11-14. T13–T17 are code without tests (`WIP` commits).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0); kept current.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M8 foundations (FOW-02/03/05, `lev1pes`, claimed) | Nothing; it overlaps fog: team vision, last-seen buildings, leak audit | independent: T5–T8's vision grid and client shroud are M6's solo fog; M8 extends them, the owner settles the base if both land fog | none |

## Log

Newest first; one entry per session in the worklog's shape. Before 2026-11-14: dyadik's branch file on 7f08f40.

### 2026-11-14 — claim

- **Claim** — M6 T2–T17 taken over from dyadik on the owner's word; the release is #54 (merged by the owner). Merged dyadik's head 7f08f40 and `origin/main` a24572d; the code equals 7f08f40, on which `verifySlow` (20 required GameTests, 3 min 19 s) and `verifySoak` passed locally. Draft pull request #55.

## For the owner

- None yet.

## Decisions taken

- 2026-11-14 · The claim and the delivery go in one pull request (#55, a draft until the hand-in), and development starts before a separate claim merge — the owner's word «продолжай автономную разработку, принимай все рекомендуемые решения» after merging the release #54; the precedent is #39 and #43 — undo: split out a claim-only pull request of this file.
- 2026-11-14 · The released `dyadik-m6-t2-t17.md` keeps dyadik's log from 7f08f40 with `main`'s released header and note (the merge's resolution) — undo: take `main`'s version of that file.

## Pitfalls

- None yet.

## Review

Written by the validation. The final review is done by a fresh maintainer session: this session reviewed #53 and now develops the work.
