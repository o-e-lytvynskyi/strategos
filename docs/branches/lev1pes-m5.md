# M5 on dev/lev1pes/m5

**Developer:** lev1pes (Codex, GPT-6 family) · **Branch:** dev/lev1pes/m5 · **Work:** M5 · **Base:** main @ f723161 · **Claimed:** 2026-10-25 · **Status:** released 2026-10-26

**Note:** released 2026-10-26 in [PR #18](https://github.com/o-e-lytvynskyi/strategos/pull/18) for owner-authorized takeover; holder approval is not claimed; preserve legacy T1-T2 and both original branches

## Now

- **Milestone:** M5 Combat. Main records 0 of 17 tasks; the preserved legacy branch records T1-T2 complete and a planned T3 split (2 of 19), pending import and validation under the new process.
- **In progress:** none; registration #12 is merged, this assignment is pending, and the operator stopped feature work after T2.
- **Last finished task:** legacy T2, commit a545902 on `codex/m5-combat`; this is not a claim delivery or a merge into main.
- **Next task:** after registration and assignment merge, reconcile the legacy work with current main, migrate its progress to this branch file, validate the updated toolchain and sanitise published measurement paths. Resume T3 only on the operator's next instruction and after the red main perf investigation is resolved.
- **Merged from main last:** 2d58bdf (2026-10-25); the later changes are records and procedure updates, with no build or product-code changes.

## Depends on unfinished work

- M0-M4 are already on main; M5's implementation has no dependency on an unfinished external milestone. The M6 spec belongs to dyadik and is outside this claim.
- The preserved T1-T2 implementation is part of this same M5 work, not a merged dependency. Import it only after assignment; do not rebase or rewrite its pushed history.
- Registration [#12](https://github.com/o-e-lytvynskyi/strategos/pull/12) is merged. The separate [main perf investigation #13](https://github.com/o-e-lytvynskyi/strategos/pull/13) has priority over further feature implementation; record-only reconciliation can be prepared independently.

## Log

### 2026-10-25 — adopt the new developer process

- Read the updated AGENTS.md, developer procedure, onboarding, security and branch-record rules. Verified GitHub identity @lev1pes, collaborator write access and public repository visibility.
- Preserved `codex/m5-combat` at bde875d. Historical commits: T1 a13850b, T2 a545902, T3 planning split e8cf0c2, and the operator's stop bde875d. No T3 implementation or tests exist.
- Historical T2 evidence: `verify verifySlow verifySoak` passed in 15m 44s, with 1315 core, 4 slow core, 200 soak, 73 replay, 95 Minecraft unit, 12 architecture, 211 GameTest and 13 slow GameTest results; no failures, errors or skips. This used Gradle 9.2.1 and belongs to the legacy tree.
- Historical T2 navigation: 10,049,008 bytes on the populated 512-square bridge fixture; path D p99 0.9465 / 1.0524 ms. Same-host terrain rebuild cost grew from 739.15 to 1848.21 microseconds; the operator approved the Windows comparison base in this chat. Keep the original cost visible and preserve the owner's Mac base and all thresholds.
- Updated main f723161 passes local `verifyFast` in 5m 26s on Windows x64, Java 21, Gradle 9.8.2, ErrorProne 2.51.0 and Gson 2.14.0. Nine Python tool tests pass; roadmap check reads 245 cards with zero problems. This is a fast check of current main, not revalidation of the unmerged T2 implementation against the new toolchain.
- Main's older perf run 40176248614 has two real failed checks. They are recorded separately in claim #13; the successful local fast check does not erase them.
- A human merged registration #12 during this session. Merged subsequent main 2d58bdf into this branch without rewriting history; read its additional GitHub-login and automatic branch-deletion rules. The records CI for both claim requests passes; no heavy CI was requested.
- **Next:** wait for the human-reviewed registration and assignment. No product code or legacy history is changed by this claim.

## For the owner

1. Registration #12 is already merged. Review and merge [M5 assignment #15](https://github.com/o-e-lytvynskyi/strategos/pull/15). No new development starts before assignment is merged. This claim includes only its registry row and this branch file.
2. The old M5 artifacts contain absolute JVM paths. Sanitise them in the future import without changing measured numbers; do not rewrite older history. Main's central STATE and worklog remain the maintainer's records.

## Decisions taken

- 2026-10-25 · Use the existing GitHub handle `lev1pes` and branch `dev/lev1pes/m5`; preserve the original branch as the historical source. There is no takeover of another developer's assignment: main currently has only dyadik's M6-spec claim.
- 2026-10-25 · Separate registration, assignment, the main perf investigation and later feature delivery into their respective pull requests. Do not open an unfinished M5 hand-in to obtain heavy CI.
- 2026-10-25 · Keep the operator's pause after T2 and explicit-permission rule for subagents. No workers were launched. Codex role configuration and trials must be validated before future delegated work; no unrelated global configuration was changed here.
- 2026-10-25 · Follow the new local-record workflow: spec/card evidence and this branch file; central STATE and worklog are maintained on merge. Baseline and golden decisions retain their measuring environment and rollback information.

## Pitfalls

- Carry the Windows UTF-8, ASCII build-path and quoted Gradle-property findings from the legacy environment report into the shared knowledge base when the assigned migration is implemented. New public records use relative paths or a home placeholder.

## Review

Pending an independent maintainer review. This session neither approves nor merges its own pull requests.
