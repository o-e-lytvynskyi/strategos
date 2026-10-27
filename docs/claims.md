# Claims

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
  and a merge.

| Work | Developer | Branch | Status | Since | Note |
|---|---|---|---|---|---|
| M6 spec | dyadik | dev/dyadik/m6-spec | assigned | 2026-10-25 | planning |
| fix-main | lev1pes | dev/lev1pes/fix-main | done | 2026-10-27 | merged by PR #21; investigate the two perf failures in run 40176248614; registration PR #12 first |
| M5 | lev1pes | dev/lev1pes/m5 | released | 2026-10-25 | owner-authorized release on 2026-10-26 for takeover by o-e-lytvynskyi; holder approval is not claimed; preserve legacy T1-T2 and both original branches |
| M6 T2–T17 | dyadik | dev/dyadik/m6-t2-t17 | assigned | 2026-10-25 | needs nothing from M5 (spec §7.1); waits for «open M6 for claims» |
| M5 | o-e-lytvynskyi | dev/o-e-lytvynskyi/m5 | assigned | 2026-10-26 | owner-authorized takeover after release PR #18; preserve and reconcile legacy T1-T2 from codex/m5-combat; fix-main remains a separate claim |
