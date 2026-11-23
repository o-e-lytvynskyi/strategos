# long-runs on dev/o-e-lytvynskyi/long-runs

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/long-runs · **Work:** long-runs · **Base:** main @ d39069e · **Claimed:** 2026-11-24 · **Status:** ready for review

**Note:** the owner's word in the developer session, 2026-11-24: «я помню там были трехчасовые тесты, надо что-то возможно придумать, чтобы их или на внешней машине делать или в целом в параллель их ставить, чтобы работа не стопорилась от тестов», then «я буду готовить раннер гитлаба локальный … но только на потом. пока максимум можно сделать заготовки»; claim and delivery in one pull request on that word, as perf-validity.

## Now

- **Work:** the long runs (`verifySoak` 3 h 13 min, `runGameTestPerf` 2 h 11 min since M6) out of the working copy: the AI soak sharded by seed, a local runner in git worktrees, `full-ci`'s soak as a matrix, a draft for the owner's future self-hosted runner.
- **In progress:** none.
- **Last finished task:** long-runs (2026-11-24).
- **Next task:** none; hand-in.
- **Merged from main last:** d39069e (2026-11-24).

## Depends on unfinished work

- None.

## Log

### 2026-11-24 — long-runs

- **AI soak shards** — `strategos.aisoak.firstSeed` (Gradle `-Paisoak.firstSeed`, default 1) beside `aisoak.seeds`: a shard runs seeds first .. first + seeds − 1; the default run is unchanged (seeds 1..20).
- **`tools/longrun.sh`** — runs the committed HEAD in worktrees under `../strategos-longrun/`, blocks, prints a verdict per shard; `--shards N` splits the soak's seeds; `--clean` removes the worktrees.
- **`full-ci`** — the job `nightly` runs `verifySlow :core:soak` (25 min as before); a new matrix job `aisoak` runs ten shards of two seeds, 90 min each at most. Before it the job ran `verifySoak` whole under a 25-minute limit, which M6's 3-hour soak cannot meet: `full-ci` was never run on M6's code.
- **`ci/gitlab/long-runs.gitlab-ci.yml`** — a draft for the owner's self-hosted GitLab runner: slow, ten soak shards, perf on a `strategos-perf` runner one at a time; by hand only; inactive until a mirror points at it.
- **Docs** — 05-testing.md "Long runs".
- **Gates and trial** (M4 Pro): `verify` green, 1 min 18 s; `tools/longrun.sh --shards 2` with `AISOAK_SEEDS=2` on da21ab8: two worktrees side by side, seed 1 conquered at tick 33,393 and seed 2 at tick 36,253, both replayed to their hashes, 10 min 0 s and 10 min 52 s including a cold build; `--clean` removed both worktrees.

## For the owner

1. **The first `full-ci` with shards** measures a shard's time on a GitHub runner; the 90-minute limit is a guess from the Mac (two seeds ≈ 20 min there). If a shard runs out, the fix is more shards of one seed, not a longer limit. Nothing is blocked.
2. **The self-hosted runner:** when it exists, set the GitLab mirror's CI configuration path to `ci/gitlab/long-runs.gitlab-ci.yml`, tag the runner `strategos` (and the perf Mac `strategos-perf`), and check the file's assumptions (shell executor, JDK 21, 8 GB per concurrent soak job).

## Decisions taken

- 2026-11-24 · `full-ci`'s soak split into a seed matrix rather than a longer timeout — one 3-hour job would hold the label's verdict for hours and a single fault would hide the rest — undo: revert the `aisoak` job and run `verifySoak` in `nightly` with `timeout-minutes: 240`.
- 2026-11-24 · the GitLab draft lives in `ci/gitlab/`, not as `.gitlab-ci.yml` at the root — no mirror or tool picks it up by accident — undo: move it to the root.

## Pitfalls

- None.

## Review
