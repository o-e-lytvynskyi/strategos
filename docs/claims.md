# Claims

**Current registry:** [claims on main](https://github.com/o-e-lytvynskyi/strategos/blob/main/docs/claims.md).
A copy on a development branch is a snapshot; it does not update when `main` changes.
**Last reconciled with merged pull requests:** 2026-10-27.

Who works on what. A row here is an assignment: it reaches `main` only by a pull request,
before any development starts, so two developers can never hold the same work (AGENTS.md
"Developers and claims"; `python3 tools/claims.py check` guards it).

- **Claim:** a pull request that adds your row (`assigned`) — or turns a `reserved` row in your
  name into `assigned` — and adds your branch file. Nothing else in it. Develop only after it is
  merged.
- **Statuses:** `reserved` (the owner set it aside for the named developer), `assigned` (theirs),
  `done` (merged into `main`), `released` (given up; claimable again).
- **Work** is written `M5` (every task of its spec), `M5 T1–T6` or `M5 T1, T3`, `M6 spec`, or a
  free name (`fix-main`, `agents-runner`).
- **Taking over** work that is `assigned` to someone else needs their explicit approval: they
  release it in their own pull request, or approve yours in a comment; your row's note says
  `handed over by <developer>, <link>`. A claim with no commit for 7 days is stale: the owner may
  release it.
- **Forks** claim the same way, from a fork; the Branch column names it
  `<login>/strategos:dev/<handle>/<work>`. They rank below collaborators: when a fork's claim and a
  collaborator's compete for the same work, the collaborator's is accepted first, whatever their
  order; the owner accepts outside claims by hand.
- Live progress is in each branch file, not here; this table changes only at a claim, a release
  and a merge. An assignment-only merge leaves the work `assigned`; merge of the delivered
  work changes it to `done`. Maintainers update the row in the delivery merge. Branch records
  may also be snapshots on `main`; use the active branch for current progress.

| Work | Developer | Branch | Status | Since | Note |
|---|---|---|---|---|---|
| M6 spec | dyadik | dev/dyadik/m6-spec | done | 2026-10-25 | specification merged 2026-10-25 in [PR #16](https://github.com/o-e-lytvynskyi/strategos/pull/16); implementation is a separate claim |
| fix-main | lev1pes | dev/lev1pes/fix-main | done | 2026-10-27 | performance repair merged 2026-10-27 in [PR #21](https://github.com/o-e-lytvynskyi/strategos/pull/21); historical run 40176248614 |
| M5 | lev1pes | dev/lev1pes/m5 | released | 2026-10-25 | released 2026-10-26 in [PR #18](https://github.com/o-e-lytvynskyi/strategos/pull/18) for owner-authorized takeover; holder approval is not claimed; preserve legacy T1-T2 and both original branches |
| M6 T2–T17 | dyadik | dev/dyadik/m6-t2-t17 | assigned | 2026-10-25 | assignment merged in [PR #17](https://github.com/o-e-lytvynskyi/strategos/pull/17); implementation not merged; opening M6 remains an owner decision; [branch progress](https://github.com/o-e-lytvynskyi/strategos/blob/dev/dyadik/m6-t2-t17/docs/branches/dyadik-m6-t2-t17.md) |
| M5 | o-e-lytvynskyi | dev/o-e-lytvynskyi/m5 | assigned | 2026-10-26 | takeover assignment merged in [PR #19](https://github.com/o-e-lytvynskyi/strategos/pull/19), not an implementation delivery; preserve legacy T1-T2; [branch progress](https://github.com/o-e-lytvynskyi/strategos/blob/dev/o-e-lytvynskyi/m5/docs/branches/o-e-lytvynskyi-m5.md) |
| main-save-reload | lev1pes | dev/lev1pes/main-save-reload | assigned | 2026-10-27 | assignment merged in [PR #26](https://github.com/o-e-lytvynskyi/strategos/pull/26), not an implementation delivery; fixes #23, #24, #25; excludes fix-main and new M5 scope; [branch progress](https://github.com/o-e-lytvynskyi/strategos/blob/dev/lev1pes/main-save-reload/docs/branches/lev1pes-main-save-reload.md) |
| M8 T1–T6 | lev1pes | dev/lev1pes/m8-foundations | assigned | 2026-10-28 | owner requested M8 independent work in the operator session; specification and contract-tested foundations only; runtime M6/M7 integration and final M8 gates excluded until prerequisites merge |
