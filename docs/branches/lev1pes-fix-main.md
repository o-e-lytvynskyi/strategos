# fix-main on dev/lev1pes/fix-main

**Developer:** lev1pes (Codex, GPT-6 family) · **Branch:** dev/lev1pes/fix-main · **Work:** fix-main · **Base:** main @ f723161 · **Claimed:** 2026-10-25 · **Status:** claimed

## Now

- **Milestone:** repair the existing main-branch perf failure; this is an assignment request, not a completed fix.
- **In progress:** none; registration and claim merges are pending.
- **Last finished task:** none under this claim.
- **Next task:** after assignment, reproduce and investigate the two failed perf checks without weakening their limits.
- **Merged from main last:** f723161 (2026-10-25).

## Depends on unfinished work

- No unfinished milestone is required: reproduce against the code already on main, independently of the legacy M5 branch.
- Developer registration [#12](https://github.com/o-e-lytvynskyi/strategos/pull/12) must be merged before this claim is accepted.

## Log

### 2026-10-25 — request the main perf investigation

- Read the updated AGENTS.md, developer procedure, onboarding, security policy and claims registry. GitHub identity is @lev1pes, with collaborator write access; the repository is public.
- Updated main at f723161 passes local `verifyFast` in 5m 26s on Windows x64, Java 21, Gradle 9.8.2, ErrorProne 2.51.0 and Gson 2.14.0. Nine Python tool tests pass; roadmap check reads 245 cards with zero problems.
- Inspected the failed step of [perf run 40176248614](https://github.com/o-e-lytvynskyi/strategos/actions/runs/40176248614), at the earlier main commit 3705118. `idle_300_costs_a_third` measured 0.3255060777 against 0.30; `path_budget_d` measured p99 50.288387 ms against the CI limit of 6 ms. These are historical CI results, not failures of the new local fast check.
- No implementation, perf rerun or threshold change has been made for this claim.
- **Next:** reproduce with recorded load and environment, distinguish implementation cost from measurement conditions, then fix and run the applicable gates. A successful fast check does not close a failed perf gate.

## For the owner

1. Merge registration #12, then review and merge this claim. Development waits for assignment; no approval or merge is performed by this session.

## Decisions taken

- 2026-10-25 · Separate the existing red perf investigation from M5 feature work, as the updated procedure prioritises red main CI. Both claims have separate branches and evidence; release this claim by pull request if reassigned.
- 2026-10-25 · Preserve all current thresholds and Mac baselines. Windows measurements describe their actual host and do not replace Mac evidence.
- 2026-10-25 · No workers launched: the operator's requirement for explicit permission before delegation remains in effect. This session is limited to review, onboarding and assignment preparation.

## Pitfalls

- The upstream Windows wrapper is stored as CRLF while its text attribute normalises it, causing a whitespace-only dirty checkout. A local, untracked Git attribute preserves its exact upstream bytes; no wrapper content or shared rule was changed. Remove that local override after the upstream blob is normalised.

## Review

Pending a maintainer's review. This session neither approves nor merges its own work.
