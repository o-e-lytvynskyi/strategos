# Worklog

Append-only session log, newest first. Each entry says:
- what was done (milestone tasks and cards);
- the results, with numbers;
- the problems met;
- what comes next.

The owner reads it to see what happened since the last visit; [STATE.md](STATE.md) shows where
things stand now. Commits are named `Mx Tn: …`, so `git log --grep "M0 T3"` finds a task's work.

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
    `tools/roadmap.py`.
- **Results:** `tools/roadmap.py check` passes on 245 cards, 0 problems. Open decisions: D-03,
  D-06, D-07, D-20, D-21, all needed after the MVP.
- **Problems:** usage limits cut three multi-agent runs short. Prefer inline work and small
  review workflows.
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
