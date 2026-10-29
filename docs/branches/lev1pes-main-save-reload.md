# main-save-reload on dev/lev1pes/main-save-reload

**Developer:** lev1pes · **Branch:** dev/lev1pes/main-save-reload · **Work:** main-save-reload · **Base:** main @ b207e509a85a3303f515a716b04a87bc90ee2cb8 · **Claimed:** 2026-10-27 · **Status:** claimed

**Note:** assignment merged in [PR #26](https://github.com/o-e-lytvynskyi/strategos/pull/26), not an implementation delivery; fixes #23, #24, #25; excludes fix-main and new M5 scope

## Now

- **Milestone:** bounded follow-up fixes for three independently reproduced bugs in existing main: [#23](https://github.com/o-e-lytvynskyi/strategos/issues/23), [#24](https://github.com/o-e-lytvynskyi/strategos/issues/24), [#25](https://github.com/o-e-lytvynskyi/strategos/issues/25).
- **In progress:** none; assignment-only PR, awaiting human merge before development.
- **Last finished task:** read-only audit with temporary diagnostic probes, removed after reproduction.
- **Next task:** after assignment merge, add permanent failing regressions and repair save identity/reload validation, then terminal lifecycle restoration.
- **Merged from main last:** branch created from b207e50 (2026-10-27).

## Depends on unfinished work

- None. All three defects reproduce on main b207e50. M5 has already landed in main via PR #19; this claim covers defects in existing functionality, not new M5 tasks or the separate M6 work.
- The performance implementation in PR #21 remains a separate, unchanged fix-main claim. These bugs do not depend on that implementation; integrate main normally when it moves and check interactions before handing in.

## Log

### 2026-10-27 — audit and assignment request

- Baseline: isolated, unmodified main `verifyFast` passed in 1m 57s (34 actionable tasks).
- **#23 (P2):** one isolated L3 GameTest ended a persistent match, verified its record was ENDED, saved/unloaded/restored it, and failed with `ended match restored as PAUSED`. Source tracing confirms that resume and command submission are then allowed; VictoryCheck already has an ended outcome and emits no new end event.
- **#24 (P1):** isolated L1 ContentSwap probe spawned a finished house and supplied a valid set omitting its template. The unsafe swap was accepted; building queries then threw ArrayIndexOutOfBoundsException through StatService.block -> BuildingLife.maxHp -> QueriesImpl.buildings. Both server and core reload guards check live units only. Real data-pack /reload was traced in source, not executed with a modified pack.
- **#25 (P1):** isolated L1 probe retained built-in indices and restored a saved custom audit:z_house against a new-session table with audit:a_house inserted before it. The restored identity was audit:a_house, without rejection. This models fresh custom-ID assignment using two valid compiled sets, not two live server processes. Production queues share numeric persistence and require follow-up inspection; their corruption was not independently reproduced.
- All issues are assigned to lev1pes. Diagnostic sources and logs remain local under the original checkout's ignored build/audit-tools; temporary edits were restored byte-for-byte. This claim includes no implementation or test changes.
- **Planned scope:** #25 stable building/production template identity with explicit legacy-save handling; #24 atomic validation of all in-use template references; #23 preserve terminal lifecycle and reject resume/commands after load. Do not silently reinterpret legacy indices, weaken gates, or change performance thresholds/baselines.
- **Validation plan:** failing core and persistence/reload GameTests before fixes; verifyFast per edit batch; verify before task commits. Run the additional slow/soak/JMH gates if a fix touches their required change types. Keep malformed/missing-template saves explicit and preserve existing fixtures or migrate with a documented policy.

## For the owner

1. Merge this assignment-only PR to register the claim. Development has not started and waits for that merge under strategos-dev section 2a.

## Decisions taken

- 2026-10-27 · Keep these related save/reload repairs in a dedicated claim — avoid overlap with the completed performance work in PR #21 and active M6 work — split the claim if review finds a dependency or ownership conflict.
- 2026-10-27 · Use one agent and an isolated main checkout with temporary diagnostic probes; no client windows or production-code edits.

## Pitfalls

- No new knowledge-base entry yet: this is an assignment-only PR. Record confirmed persistence/reload pitfalls with the corresponding fixes after assignment.

## Review

Awaiting independent maintainer review and human assignment merge.
