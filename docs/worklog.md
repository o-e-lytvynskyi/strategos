# Worklog

Append-only session log, newest first. Each entry says:
- what was done (milestone tasks and cards);
- the results, with numbers;
- the problems met;
- what comes next.

The owner reads it to see what happened since the last visit; [STATE.md](STATE.md) shows where
things stand now. Commits are named `Mx Tn: …`, so `git log --grep "M0 T3"` finds a task's work.

## 2026-10-21 — M0 T1, T2, T3, T18, T4a, T4b, T5a

- **M0 T5a · Kernel internals** — CORE-01 and CORE-04 in progress; CORE-02, CORE-06.
  - **Results:**
    - `MatchLifecycle`, checked against an independent table of its 15 transitions;
    - `Phase`, `Cadence`, `Stagger` and `Scheduler`;
    - `MatchBus`: FIFO after the current message, a 10,000-deep chain without stack growth;
    - `EntityStore` with `EntitySlots`, which reuses the lowest free index and bumps generations;
    - the component stores;
    - `:core:test` passes 81 tests.
  - **Problems:** the spec put `MatchState` in T5b although T5a's scheduler needs it; T5a brings it
    with only its tick, and `UnitState` with explicit codes (spec §1.6 D row 44). NullAway caught
    an unchecked `EnumMap.get`.

- **M0 T4b · Core `protocol`, `content`** — CORE-03 in progress.
  - **Results:**
    - the sealed `Command` with `Move`, `Stop` and `SpawnUnits`, each hashable;
    - `UnitSet`, capped at 300 on decode too;
    - `CommandKinds`, whose codec dispatches on `kind` and names an unknown one;
    - `QueueMode`, `RejectReason` and `CommandOutcome`;
    - `TemplateSet.builtin()` with the debug walker and a golden hash;
    - `:core:test` passes 61 tests; `verify` passes in 20 s.
  - **Problems:** ErrorProne refused hashing `QueueMode` by its ordinal, which would let a
    reordered enum change state hashes silently; it hashes the mode's id.

- **M0 T4a · Core `shared`, `port`, `diag`** — CORE-05, CORE-02 and QA-10 in progress.
  - **Results:**
    - ids with JSON codecs, `DetMath`, the counter-based `Rng`, `Hasher`, `Hex64`, `Clock`,
      `MetricsSink`, `RingHistogram`, `PhaseTimers` and `MatchLog`;
    - `:core:test` passes 46 tests;
    - the golden values of `Rng` and `Hasher` were computed by an independent Python
      implementation of the spec's formulas, and the Java code matches it bit for bit;
    - `below` passes chi-square 8.6 against the 21.67 limit.
  - **Problems:** the test kit's `ManualClock` arrives in T5b, so `PhaseTimersTest` advances a
    clock lambda by hand.

- **M0 T18 · Continuous integration** — QA-08 and CORE-05 in progress.
  - **Results:**
    - one `ubuntu-24.04` job runs `verify` on pushes to `main` and on pull requests;
    - the first run, with a cold cache, passed in 6 min 11 s (budget 20 min, QA-08.2);
    - [pull request #1](https://github.com/o-e-lytvynskyi/strategos/pull/1) shows the check
      "verify: test results" with the L1, L2, L3 and L5 cases (QA-08.1); `main` was then
      fast-forwarded to the branch;
    - the actions are pinned to the commits of their tags;
    - documentation-only pushes run nothing, and a newer push cancels an older run.
  - **Problems:**
    - The current GitHub tools read a pull request's checks but not runs on `main`, so "Now"
      shows CI as not readable for `main` until the owner's setup item 1b.
    - `jmhSpeedRatio`, the soak and the slow batch join with T12, T6b and T7c
      (spec §1.6 D row 43).

- **M0 T3 · Smoke, the first ArchUnit rules, `verify`, `verifyHarness`, the first S5 report** —
  QA-05 and QA-17 in progress; QA-01, QA-04.
  - **Results:**
    - `verify` is green in 18.9 s warm and 19.8 s after `clean` (budget 5 min);
    - the server smoke takes 6.1 s (budget 60 s), L2 with the game bootstrap 3.8 s;
    - ArchUnit B5 and B11 hold on the shipped classes and flag their fixtures;
    - every `verifyHarness` run ends with its designed verdict;
    - every negative check and probe fails as designed (S5 checks 5–12, 15, 16), and disabling
      the retry-reason rejection makes its inverted guard fail the build;
    - ADR-0007 is Accepted on the S5 evidence ([spikes/S5-build-wiring.md](spikes/S5-build-wiring.md)).
  - **Problems:**
    - The `prepare*` `Sync` tasks stayed UP-TO-DATE after a server run, so the next smoke saw the
      last run's crash report; they now run every time (spec §1.6 D row 42; pitfall in the
      knowledge base).
    - `clientRef` crashes through NeoForge's dist cleaner, not `NoClassDefFoundError`; the guard
      catches it either way.
- **M0 T2 · GameTest harness** — QA-04 in progress, QA-01.
  - **Results:**
    - `runGameTestServer` passes `buildtests.core_in_mod_module` and
      `buildtests.testfixtures_in_mod_module`: core and the test kit run inside the mod module;
    - L3 wall time 1.2 s of the 180 s budget;
    - `verifyFast` plus the GameTest run take 6 s warm;
    - all four negative checks behave as designed (S5 checks 5, 7, 15, 16);
    - both SNBT arenas are generated and committed.
  - **Problems:**
    - Retries never ran on the 1.21.1 GameTest server: its tracker counts only first attempts,
      so it halted before a rerun and exited with the first failure. `RerunTracking` keeps the
      tracker on the newest attempt (spec §1.6 D row 41; pitfall in the knowledge base).
    - ErrorProne flagged `LocalDateTime.now()` without a zone: the report's timestamp is UTC.
    - A review of the commit (three lenses, each finding checked by a skeptic) corrected the
      README's account of a vanilla `server.properties` error. It also hardened the report:
      seconds always in the timestamp, no characters XML 1.0 forbids. Test names now treat a
      digit run as a word (`permission_2`), as T8's names need.
- **M0 T1 · Build skeleton** — QA-01, QA-03, QA-09 and CORE-06 in progress.
  - **Results:**
    - the wrapper checksum matched the MDK's;
    - the daemon runs on Temurin 21;
    - the first build took 55 s: NeoForge 21.1.264 downloaded and decompiled, 787 MiB of assets;
    - a clean rebuild without the build cache takes 5.9 s;
    - `HandleTest` passes 8 tests, `ModLoadsTest` 2 (the mod loads, Dist `DEDICATED_SERVER`);
    - the jar holds `Handle.class`;
    - no Gradle deprecation warnings.
  - **Problems, recorded in spec §1.6 D:**
    - Spotless was loaded twice because `core` and `mod` apply different plugin sets; the root now
      declares the convention plugin with `apply false` (row 38; pitfall in the knowledge base);
    - Gradle 9's property syntax (row 39);
    - a three-lens review with adversarial checks found that `checkVersionCatalog` missed `2+` and
      versions inside `group:name:version` strings (row 40), now proven on a broken catalog; two
      comments that strayed from the spec were removed;
    - `HandleTest` pins the bit layout with literals, since handles are saved.
- **Next:** M0 T5b · `MatchCore`, commands and movement.

## 2026-10-21 — design closed, roadmap approved, autonomy set up

- **Owner decisions.**
  - Approved M0–M6. CMB-08 is deferred, because D-08 chose the observer avatar.
  - Took every decision needed by M6 as recommended.
  - Asked that every unit can always be controlled on its own, which is now pillar P3 and
    ORD-06's criteria 5–6.
  - Allowed commits and pushes to the private repository without asking.
  - Asked for autonomous development with occasional visits.
- **Done:**
  - the design round: three architectures, judged and synthesised into 04-architecture.md;
    ADRs 0001–0023;
  - feasibility and testing documents;
  - the roadmap reviewed by a game designer, a tech lead and QA (224 change requests);
  - the M0 implementation spec (T1–T18);
  - document reconciliation after the approval;
  - the promo draft;
  - the private GitHub repository `o-e-lytvynskyi/strategos`;
  - the autonomy kit: the `strategos-dev` skill, this worklog, the STATE.md layout, and
    `tools/roadmap.py`;
  - a dry run of the kit by a fresh agent: 30 issues, all fixed;
  - a permission allowlist for unattended sessions, proposed in `tools/claude-settings.json`;
  - a way for sessions to read CI (M0 T18 "Reading results").
- **Results:** `tools/roadmap.py check` passes on 245 cards, 0 problems. Open decisions: D-03,
  D-06, D-07, D-20, D-21, all needed after the MVP.
- **Problems:**
  - Usage limits cut several multi-agent runs short. Prefer inline work and small review
    workflows.
  - Auto mode refused to let the assistant write its own permission settings, as it should, so
    the allowlist waits for the owner (STATE "For the owner" 2a).
  - The GitHub MCP server's default tools cannot read Actions runs on `main` (2b).
- **Next:** M0 T1 (build skeleton), as soon as the owner says to start M0.

## 2026-10-20 — research

- **Owner decisions:**
  - Minecraft 1.21.1;
  - a bounded match arena first;
  - solo against an AI first, then 2–4 players;
  - RTS camera plus first person;
  - a hybrid world.
- **Done:**
  - two independent, fact-checked research passes on six topics;
  - a toolchain check: the NeoForge 1.21.1 MDK builds in 98 s on the owner's Mac;
  - decompiled 1.21.1 sources prepared for API checks;
  - tooling and process documents.
- **Next:** design.
