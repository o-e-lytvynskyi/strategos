# Claims refresh on dev/lev1pes/claims-refresh

**Developer:** lev1pes · **Branch:** dev/lev1pes/claims-refresh · **Work:** owner-requested records correction · **Base:** main @ bb800d0 · **Status:** merged 2026-10-29

## Now

- Owner directly requested reconciliation of docs/claims.md and a notification about stale records on 2026-10-27. This is an administrative correction, not a new implementation assignment or takeover.
- Compared the registry against GitHub PR merge metadata for #16, #17, #18, #19, #21 and #26.
- M6 spec delivery #16 is merged: mark it done. Keep M5, M6 T2–T17 and main-save-reload assigned: their merged PRs registered assignments, not delivered implementations.
- Add a canonical main link and explain branch snapshots and assignment versus delivery merges.

## Validation

- Records checks passed: 9 tools unit tests; roadmap check: 245 cards, 0 problems; claims check against origin/main: 6 claims, 0 problems; git diff --check clean. No runtime or build changes.

## For the owner

- Review and merge the correction. A separate GitHub issue reports the missed delivery-status update and asks for a merge-time consistency check; it does not propose synchronizing every historical branch.

## Decisions taken

- 2026-10-27 · Preserve assignment dates and historical released rows. Use PR links as evidence and active branch links for progress; a merged assignment is not proof of delivered work.

## Review

2026-10-29 · maintainer review, fixes on the owner's word («можем пофиксить этот МР»); the owner confirmed the request in the maintainer session.

- Facts checked against GitHub: #16 delivered the M6 spec; #17, #19, #26 and #31 are assignment-only; #18 released M5; #21 delivered fix-main.
- Fixed: merged `origin/main` (conflict in docs/claims.md: kept the M8 T1–T6 row from #31); M5 (o-e-lytvynskyi) turned `done` — delivered by #33 on 2026-10-28, after this branch was cut; dropped the "branch progress" links (GitHub deletes merged branches, the links would break) and the "Last reconciled" date (it goes stale at the next merge).
- Follow-up outside this branch: #33 was merged without the rest of its records (STATE "Milestones", header, worklog, the M5 branch file's status) — the same gap issue #28 reports.
