# Architecture decision records

One file per lasting decision: its context, the decision, its consequences and the alternatives
that were rejected ([03-design-process.md](../03-design-process.md), design rule 1). The
architecture itself is [04-architecture.md](../04-architecture.md); each ADR names the sections it
governs.

## Index

| ADR | Decision | Status | Date | Architecture |
|---|---|---|---|---|
| [0001](0001-loader-and-version.md) | Minecraft 1.21.1 on NeoForge 21.1 (≥ 21.1.264) as the only loader | Accepted | 2026-10-21 | – |
| [0002](0002-package-naming.md) | Package and namespace naming under `dev.strategos` | Accepted | 2026-10-21 | §2 |
| [0003](0003-module-layout-and-core-merge.md) | Gradle layout with a Minecraft-free core merged into the mod | Accepted | 2026-10-21 | §2 |
| [0004](0004-zero-runtime-dependencies.md) | No runtime dependencies besides NeoForge | Accepted | 2026-10-21 | §2 |
| [0005](0005-clean-room-and-licensing.md) | Clean-room development and licensing policy | Accepted | 2026-10-21 | – |
| [0006](0006-test-levels-and-definition-of-done.md) | Test levels L0–L7 and the Definition of Done | Accepted; amended by 0022 | 2026-10-21 | §16 |
| [0007](0007-gametest-wiring.md) | GameTests inside the `strategos` mod, registered by our own generator | Accepted (S5) | 2026-10-21 | §16 |
| [0008](0008-authority-and-state-model.md) | The core owns all match state; entities are views | Accepted | 2026-10-21 | §5 |
| [0009](0009-tick-pipeline-determinism-and-fault-containment.md) | Tick pipeline, determinism boundary and fault containment | Accepted; amended by 0023 | 2026-10-21 | §4, §5 |
| [0010](0010-match-model-and-lifecycle.md) | A match is a region of a level; arenas live in an arena world | Accepted; amended by 0023 | 2026-10-21 | §3 |
| [0011](0011-unit-entity-and-body-seam.md) | Units are kinematic `LivingEntity` views behind a body seam | Proposed - S1 met; pending spike S10 | 2026-10-21 | §6 |
| [0012](0012-template-and-content-pipeline.md) | Templates load through a reload listener with core-side inheritance | Accepted | 2026-10-21 | §8 |
| [0013](0013-modifier-pipeline.md) | The core modifier pipeline is the single source of every stat | Accepted | 2026-10-21 | §8 |
| [0014](0014-orders-commands-and-unitai.md) | Typed commands, server-side validation, 0 A.D. order queues and a hierarchical UnitAI | Accepted; amended by 0023 | 2026-10-21 | §7 |
| [0015](0015-pathfinding-approach.md) | Layered 2.5D surface grid, staged search, ORCA and deterministic job delivery | Proposed - pending spikes S8 and S7 | 2026-10-21 | §9 |
| [0016](0016-networking-and-fog-of-war.md) | One fog-aware sender, vanilla tracking behind a gate, targeted re-tracking | Accepted; amended by 0023 | 2026-10-21 | §10, §12 |
| [0017](0017-camera-and-input.md) | A spectator body at the camera eye, a cursor-owning screen, perspective projection | Proposed - pending spike S9 | 2026-10-21 | §13 |
| [0018](0018-persistence-and-versioning.md) | One versioned `SavedData` blob per match, codecs in core, quarantine on failure | Accepted | 2026-10-21 | §5 |
| [0019](0019-performance-budgets.md) | Performance budgets A–G at 600 units, measured from M0 | Proposed - S1 and S2 reported; pending S4 and the owner's approval of the revision | 2026-10-21 | §17 |
| [0020](0020-engine-surface-inventory.md) | One reviewed inventory of every hook into Minecraft | Accepted | 2026-10-21 | §2 |
| [0021](0021-economy-and-construction-model.md) | Hybrid world economy: real-block nodes, finite deposits and fields, abstract building HP | Accepted | 2026-10-21 | §11 |
| [0022](0022-definition-of-done-amendments.md) | Definition-of-Done amendments for networking and tooling changes, and the inventory check in L2 | Accepted | 2026-10-21 | §16 |
| [0023](0023-plan-alignment-after-roadmap-approval.md) | Plan alignment after the roadmap's approval; the roadmap governs the schedule | Accepted | 2026-10-21 | §1.5, §3.4, §5.5, §6.7, §7.5, §9.5, §10, §13.7 |

## Pending measurements

| Spike | Confirms | Runs in (card) | Owner consent |
|---|---|---|---|
| S1 server cost per body | 0011, 0019 | M0 (PERF-02); reported [2026-10-22](../spikes/S1-server-cost-per-body.md) | No |
| S2 force-loaded arena cost | 0019 (and the sizes in 0010) | M0 (PERF-03); reported [2026-10-22](../spikes/S2-force-loaded-arena.md) | No |
| S3 client harness on macOS | 0006 (the L4 consent rule) | M0 (QA-06); [prepared](../spikes/S3-client-scenario-harness.md), pending consent | Yes |
| S4 client frame time | 0019 | M0, else with CAM-01 (PERF-04, bare humanoids); the armoured and held-item pass with ART-01 in M3; [prepared](../spikes/S4-client-frame-time.md), pending consent | Yes |
| S5 build and GameTest wiring | 0007 (and the execution of 0003) | M0, first (QA-01); [final report](../spikes/S5-build-wiring.md) 2026-10-22 | No |
| S6 determinism canary | 0009 | M0 (CORE-05); reported [2026-10-22](../spikes/S6-determinism-canary.md), Linux on CI | No |
| S7 path service under load | 0015 | M1 (PATH-16) | No |
| S8 terrain census and encoding | 0015 | M1 (PATH-01) | No |
| S9 commander camera on a dedicated server | 0017 | Right after M0, on the client track (CAM-01) | Yes (clients) |
| S10 kinematic fidelity | 0011 | M1 (UNIT-09) | Screenshots: yes |
| S11 bandwidth | 0016 (whether the tier-4 reserve is needed) | M7 (NET-04) | Real clients: yes |
| S12 content cycle | 0012 (its targets, not the decision) | M1 (DATA-03) | No |

The spikes, their questions and exit criteria are in
[04-architecture.md §19.2](../04-architecture.md); their cards and schedule follow the roadmap's
[spike table](../roadmap/M0-foundation.md#spikes).

## Planned ADRs

Future cards owe these decisions as new ADRs. Each takes the next free number when it is written
and names the part of an older ADR that it replaces or extends. QA-09's Definition-of-Done
amendment, planned here before, is written: [ADR-0022](0022-definition-of-done-amendments.md),
accepted with the roadmap on 2026-10-21.

| Card | When | Decision | Effect on existing ADRs |
|---|---|---|---|
| ECO-03 | M3 | Leaves go while a tree is felled, as vanilla would decay them: once no remaining log is within six leaf steps (`LeavesBlock.DECAY_DISTANCE` = 7), computed by the core | Supersedes ADR-0021's leaf rule (leaves only when the tree is exhausted) |
| ECO-16 | M4 | A fauna model archetype for livestock: a `strategos:unit_fauna` entity type, or a renderer that dispatches by archetype | Amends ADR-0011; the engine-surface inventory ([04-architecture.md §2.5](../04-architecture.md), ADR-0020) lists the new type or renderer |
| QA-14 | 2027-Q2 or the MVP gate, whichever comes first | The 26.x platform decision (stay, port or keep two branches) and the next review date | Records the outcome of ADR-0001's decision gate; supersedes ADR-0001 unless the decision is to stay |
| UNIT-08 | M14 | The body mode or entity type of ships | Amends ADR-0011 if ships need a mode or type it does not list |

## How ADRs are written and changed

- **File name** `NNNN-kebab-title.md`, numbered in order, never reused.
- **Sections:** Status, Context, Decision, Consequences, Alternatives considered. The date is at
  the top.
- **Status** is `Accepted`, or `Proposed - pending spike Sx` when a measurement must confirm the
  decision. When the spike reports, the ADR becomes `Accepted` with the numbers added, or it is
  superseded. Until then it may be revised, with a dated note under Status that says what changed
  and why; the status stays `Proposed`.
- **Accepted ADRs are not rewritten.** A new ADR supersedes an old one: the old one gets the status
  `Superseded by NNNN`, and the new one names what it replaces. When a new ADR replaces or extends
  only part of an old one, the old one stays `Accepted` and its status names that part, for
  example `Accepted - leaf rule superseded by NNNN` or `Accepted; amended by ADR-NNNN (topic)`.
- **The roadmap governs the schedule.** A milestone tag inside an ADR records the plan when the ADR
  was decided; [ROADMAP.md](../ROADMAP.md) and its cards govern the schedule, and the roadmap wins
  where they differ ([ADR-0023](0023-plan-alignment-after-roadmap-approval.md)). Moving a card needs
  no new ADR; changing a decision does.
- **Owner approval.** A decision that changes the product, adds a runtime dependency (ADR-0004) or
  changes a performance threshold (ADR-0019) needs the owner's approval.
- **Same-change updates.** Every ADR change updates [04-architecture.md](../04-architecture.md) and
  this index in the same change.
