# State

Single source of truth for where the project stands. Updated at the end of every working
session. Last update: 2026-10-21.

## Where things stand

Research, design and planning are complete, and the owner has approved the roadmap up to the
first playable version. **Implementation has not started.** The owner postponed it, and M0 waits
for the owner's go-ahead.

## Done

- **Research (2026-10-20).** Two independent, fact-checked passes on six topics. The digest is
  [research/README.md](research/README.md). The architecture resolves its gaps and
  contradictions.
- **Toolchain check.** The official NeoForge 1.21.1 MDK builds on the owner's Mac in 98 s, with
  JDK 21 provisioned by Gradle.
- **Owner decisions:**
  - **2026-10-20:**
    - Minecraft 1.21.1;
    - a bounded match arena first, open world maybe later;
    - solo against an AI first, then a 2–4 player server;
    - RTS camera plus first person;
    - a hybrid world.
  - **2026-10-21:**
    - every decision needed by M6 taken as recommended, including 150 population, AoE-style villagers, the observer avatar, solo fog of war in the MVP, perspective camera, and pause with game speeds;
    - M0–M6 approved, with CMB-08 deferred.

  Details are in [ROADMAP.md](ROADMAP.md).
- **Design.** Three competing architectures were judged on maintainability, performance and
  delivery, and synthesised into [04-architecture.md](04-architecture.md). The result is
  core-first: a deterministic, Minecraft-free core owns all match state, and units are kinematic
  `LivingEntity` views.
- **ADRs.** 23 decision records ([adr/](adr/README.md)). Five stay Proposed until their spikes
  report:
  - 0007 (S5);
  - 0011 (S1, S10);
  - 0015 (S7, S8);
  - 0017 (S9);
  - 0019 (S1, S2, S4).
- **Step documents:**
  - [01-feasibility.md](01-feasibility.md): realistic as a focused RTS; MVP in 35–49 weeks at
    12–15 h/week; parity with 0 A.D. is not a goal;
  - [02-tooling.md](02-tooling.md): platform, versions, libraries;
  - [03-design-process.md](03-design-process.md): roles, artifacts, feature lifecycle;
  - [05-testing.md](05-testing.md): test levels, commands, Definition of Done.
- **Roadmap.** 16 milestones and 243 cards, 154 of them up to the MVP. A game designer, a tech
  lead and QA reviewed it: 224 change requests, all accounted for in
  [design/roadmap-changes.md](design/roadmap-changes.md). Card statuses:
  - M0 `specified`;
  - M1–M6 `approved`;
  - M7–M15 `proposed`.
- **M0 implementation spec.** [milestones/M0-foundation.md](milestones/M0-foundation.md):
  - tasks with verification commands;
  - the two-week walking-skeleton tripwire;
  - spikes S1, S2, S5 and S6. S3 and S4 need consent, because they open a game window.
- **Promo draft.** [promo/roadmap-announcement.md](promo/roadmap-announcement.md): a simplified
  six-phase roadmap and an announcement. Review it before publishing.
- **Knowledge base.** The pet-projects file `.claude/kb/minecraft-mod.md` covers the toolchain,
  GameTest pitfalls, window focus theft, licences and three verified engine facts.

## Next (after the owner's go-ahead)

1. **M0 "Foundation"**, following [milestones/M0-foundation.md](milestones/M0-foundation.md):
   - wave A (build, harnesses, kernel, walking skeleton) within about two weeks — the tripwire;
   - then wave B (hardening; spikes S1, S2, S5, S6).
2. **D-23: the private GitHub repository and its CI job** (QA-08). The GitHub step and the first
   commit need the owner's go-ahead.
3. **The consent batch for client windows:** S3 (scenario harness) and S4 (client frame time) in
   M0; S9 (camera) right after M0.

## Waiting for the owner

- **Go-ahead:**
  - the go-ahead for M0 implementation;
  - the first commit and the private GitHub repository (D-23);
  - consent batches for client windows (D-22).
- **Decisions still open, needed later:**
  - D-03 civilisation themes (M7);
  - D-20 multiplayer hosting (M7);
  - D-07 licence and D-21 public release and name (first public release);
  - D-06 map generation (M12).
- **Reviews:**
  - approval of M7–M15 (proposed), as their milestones come closer;
  - review of the promo draft before it is published anywhere;
  - optionally, a review of the scope-neutral card refinements made after the approval
    ([design/roadmap-changes.md §8](design/roadmap-changes.md)).

## Known follow-ups (not blocking)

- [01-feasibility.md](01-feasibility.md) §3 predates the architecture in places. Its precedence
  note says the architecture and the ADRs win. Re-estimate the document after M0.
- ADRs owed by future cards:
  - ECO-03's leaf rule, which amends ADR-0021;
  - ECO-16's fauna archetype, which amends ADR-0011.
- S5 confirms or updates ADR-0007's batch list, and measures `verify`'s 5-minute budget with the
  harness runs (risk R20).
