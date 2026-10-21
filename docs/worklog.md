# Worklog

Append-only session log, newest first. Each entry says:
- what was done (milestone tasks and cards);
- the results, with numbers;
- the problems met;
- what comes next.

The owner reads it to see what happened since the last visit; [STATE.md](STATE.md) shows where
things stand now. Commits are named `Mx Tn: …`, so `git log --grep "M0 T3"` finds a task's work.

## 2026-10-21 — M0 T1, T2

- **M0 T2 · GameTest harness** — QA-04 in progress, QA-01.
  - **Results:**
    - `runGameTestServer` passes `buildtests.core_in_mod_module` and
      `buildtests.testfixtures_in_mod_module`: core and the test kit run inside the mod module;
    - L3 wall time 1.2 s of the 180 s budget;
    - `verifyFast` plus the GameTest run take 6 s warm;
    - all four negative checks behave as designed (STATE.md, "Evidence for the S5 report");
    - both SNBT arenas are generated and committed.
  - **Problems:**
    - Retries never ran on the 1.21.1 GameTest server: its tracker counts only first attempts,
      so it halted before a rerun and exited with the first failure. `RerunTracking` keeps the
      tracker on the newest attempt (spec §1.6 D row 41; pitfall in the knowledge base).
    - ErrorProne flagged `LocalDateTime.now()` without a zone: the report's timestamp is UTC.
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
- **Next:** M0 T3 · smoke, the first ArchUnit rules, `verify`, `verifyHarness` and the first S5 report.

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
