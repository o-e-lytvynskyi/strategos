# Claims refresh on dev/lev1pes/claims-refresh

**Developer:** lev1pes · **Branch:** dev/lev1pes/claims-refresh · **Work:** owner-requested records correction · **Base:** main @ bb800d0 · **Status:** ready for review

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
