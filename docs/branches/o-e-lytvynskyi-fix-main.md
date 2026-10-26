# fix-main takeover proposal on dev/o-e-lytvynskyi/fix-main

**Developer:** o-e-lytvynskyi · **Branch:** dev/o-e-lytvynskyi/fix-main · **Work:** fix-main takeover preparation · **Base:** main @ b207e50 · **Claimed:** not assigned · **Status:** claimed

## Now

- **Milestone:** unblock autonomous M5 recovery by resolving the existing main perf failures.
- **In progress:** release proposal only; no fix-main implementation is assigned to this session.
- **Last finished task:** no fix delivered. Prior assignment #13 contained records only.
- **Next task:** the owner decides this release by merging or rejecting this PR; after a merge, obtain the successor assignment before implementing a fix. Continue independent M5 recovery meanwhile.
- **Merged from main last:** based on b207e50 (2026-10-26).

## Depends on unfinished work

- No unfinished milestone required: reproduce against current main independently of M5. Do not count M5 functional tests as proof of repairing historical main performance.

## Log

### 2026-10-26 — propose release of the blocking perf investigation

- The owner authorized autonomous M5 work and recommended decisions. Its prerequisite fix-main remains assigned to lev1pes with no delivered implementation; propose a separate owner-directed release so a successor assignment can proceed. This proposal does not claim lev1pes consent and has no effect on main before a human merge.
- Re-read historical CI run 40176248614: idle_300_costs_a_third measured 0.325506 against 0.30; path_budget_d measured p99 50.288387 ms against the CI limit 6 ms. The log supplies no explicit invalid/foreign-load verdict. It ran Ubuntu 24.04.5, Temurin 21.0.12.1, Gradle 9.2.1 and ciFactor 3. These are measured failures; host contention is not established by the log.
- M5 recovery independently exposed and is removing repeated classification and eviction scans; any overlap with the standing-cost failure is a hypothesis until measured. No fix-main code or measurement has been made in this branch.
- **Next:** human release decision, successor assignment, reproduction on current main, then a measured fix with original thresholds and baselines retained.

## For the owner

1. Review and merge this release only if transferring the investigation is desired. This is the next blocker after independent M5 recovery; the usual holder-approved release is explicitly replaced by an owner-directed decision through this PR. No holder approval or stale-claim condition is asserted.

## Decisions taken

- 2026-10-26 · Propose releasing the blocking investigation separately from M5. Keep the old branch and evidence; take no fix-main code ownership before the successor assignment is merged. Undo by rejecting this PR or restoring the assignment by PR.

## Pitfalls

- No new knowledge-base entry: historical failures remain unresolved and no implementation conclusion is established.

## Review

Pending independent human review. No product code, gates or baselines changed.
