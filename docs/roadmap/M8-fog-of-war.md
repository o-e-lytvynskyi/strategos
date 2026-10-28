# M8 · Fog of war

Feature cards of milestone M8. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** Fog that is fair in multiplayer: team vision, targeted re-tracking, mirages of last-seen
buildings, a leak audit of every outbound channel, and an AI that scouts and remembers. The solo fog of
M6 (FOW-01, FOW-07, UI-16, AI-18, MATCH-07; D-24 (c), decided 2026-10-21) is already in place.

**The owner plays.** In a 2 v 2 on a dedicated server allies share vision; enemy armies vanish into
fog and are absent from the client's entity list; last-seen enemy buildings stay as ghosts until
re-scouted; the AI scouts every start in the first minutes.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M8 cards passes.
2. L3: an enemy outside its viewer's team vision is never paired, while a visible control unit is;
   reveal within 6 ticks and hide within 15; one leak test per outbound channel, each with a
   positive control.
3. L6 (PERF-15): 4 × 150 population with fog and a fog-edge skirmish for 20 minutes within A–E;
   the 2× stress pass holds.
4. Consent batch: fog goldens under Sodium and Iris; PERF-14's fog scene.

**Acceptance** (owner): a two-client session with the questionnaire; no P0 or P1 bug open.

**Retires.** F-R11/A-R7 (fog leaks and re-tracking).

**Size and critical path.** 5 cards (1 S, 3 M, 1 L), at most 20 card-days. Critical path: FOW-02 →
FOW-03 → AI-07 (16 card-days).

---

## Decisions this milestone needs

D-07 and D-21 are needed by the first public release, which the roadmap recommends after M8.

### D-07 · Licence

**Question.** Under which terms Strategos's code and assets are published, if ever.

| Option | For | Against |
|---|---|---|
| MIT or Apache-2.0 | Maximum reuse; simplest | Anyone may ship closed forks |
| MPL-2.0 (recommended for code) | Changes to Strategos files stay open; add-ons and modpacks of any licence can use it; no GPL-with-Minecraft question | Less familiar to some players |
| LGPL-3.0 | Common in Minecraft libraries | Relinking terms fit Java mods awkwardly |
| GPL-3.0 | Strong copyleft | A GPL plug-in in a proprietary host is a known problem, and it would not make GPL code reusable anyway |
| All rights reserved | Full control | No contributions or forks; less trust from players |

**Recommendation.** Code under MPL-2.0; assets all rights reserved with explicit permission for
modpacks, or CC BY-NC-SA 4.0 if the owner wants remixing. The clean-room rule keeps every option
open, and the RVO2 notice goes into `NOTICE` either way. The repository stays private until the
owner decides. **Needed by:** the first public release. **Affects:** QA-13, `NOTICE`, README.

**Owner (2026-10-25):** all rights stay with the owner; personal and other non-commercial use is
allowed, commercial use is not, for now — the terms may change after the full release. Applied as
the PolyForm Noncommercial License 1.0.0 ([LICENSE](../../LICENSE)) for code and assets alike;
contributions are voluntary and licensed to the owner with the right to relicense
([CONTRIBUTING.md](../../CONTRIBUTING.md)). The adapted RVO2 code keeps its Apache-2.0 notice in
`NOTICE`.

### D-21 · Public release and name

| Option | For | Against |
|---|---|---|
| Public alpha after the MVP (M6) | Early feedback | Solo only; first impressions without multiplayer |
| After multiplayer (M7) | What Minecraft players expect | Multiplayer without fair fog |
| After fog of war (M8) (recommended) | Fair multiplayer | Later feedback |

**Recommendation.** Private playtests with friends from M6; a public alpha on Modrinth and
CurseForge after M8, once the licence (D-07) is chosen and the CurseForge and trademark checks for
"Strategos" are done. "Age of Empires", "AoE" and "0 A.D." stay out of the name, logo and primary
tags; the README credits them as inspiration only. **Needed by:** the first public release.
**Affects:** QA-13, D-07.

---

Specification: [M8-fog-of-war.md](../milestones/M8-fog-of-war.md). The owner's
2026-10-28 opening and merged claim #31 authorize only independent T1–T6; the
full runtime acceptance below still waits for M6/M7.

## Cards

#### FOW-02 · Team vision and targeted re-tracking
- **Value:** Hidden enemies are not just invisible: their data never reaches your client, so no
  modified client can reveal them, and allies see what allies see (P5).
- **In scope:** ADR-0016: vision shared with allies (MATCH-06) through `VisibilityIndex`; forced
  re-tracking every 5 ticks through a targeted `TrackedEntity#updatePlayer` for exactly the flipped
  (unit, viewer) pairs, reached through the two access-transformer lines on `ChunkMap.entityMap` and
  `ChunkMap$TrackedEntity`; `ServerChunkCache#move(viewer)` as the fallback (FOW-07's mechanism);
  `updatePlayer` never cancelled; the gate covers unit bodies only, because buildings are blocks;
  the chunk-sent re-pair switches to targeted `updatePlayer`; inventory entries for the
  access-transformer lines; a `-Pcompat` run, Moonrise's behaviour included.
- **Out of scope:** vanilla entities we don't own; terrain secrecy (FOW-06).
- **Acceptance:**
  1. An enemy outside the team's vision is never paired to a mock viewer, while a visible control
     unit is (L3).
  2. Entering vision pairs within 6 ticks, and leaving it unpairs within 15: 10 ticks of hysteresis
     plus the 5-tick pass (L3).
  3. Allies see what allied units see (L3).
  4. The inventory contains no cancellation of `updatePlayer` (L2).
  5. Re-tracking at 4 × 150 fits budget A (L6).
- **Tests:** L0, L2, L3, L5, L6 · **Depends on:** FOW-07, NET-03, MATCH-06 · **Size:** L ·
  **Tier:** T1
- **Status:** in_progress

#### FOW-03 · Mirages and shared explored state
- **Value:** You remember what you scouted: explored terrain stays dimly visible, and enemy
  buildings stay as you last saw them (P5).
- **In scope:** explored state shared with allies that share vision; mirage records of enemy
  buildings with their last-seen state (health, owner) from the `MIRAGE` template kind; a mirage
  updates when seen again, and a building destroyed in fog stays a mirage until re-scouted; ghost
  icons on the minimap; saved with the match.
- **Out of scope:** last-seen units.
- **Acceptance:**
  1. A building destroyed in fog stays a mirage until re-scouted (L1).
  2. Mirage data contains only last-seen information (L1).
  3. Mirages and shared explored state survive save and load (L3).
  4. Ghost icons on the minimap (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** FOW-02 · **Size:** M · **Tier:** T1
- **Status:** in_progress

#### FOW-05 · Leak audit and guards
- **Value:** Fog can't be cheated through sounds, particles, health bars or pings (P5).
- **In scope:** an audit table of every outbound channel: sounds (with the
  `PlayLevelSoundEvent.AtEntity` backstop), particles, projectile `fx`, health bars and `unit_hud`,
  selection details, `unit_summary`, pings, block-break progress, scoreboard teams, enemy
  commanders and first-person bodies (NET-09), and the documented entity-id side channel; guards in
  the audiences; one mock-player test per channel; the accepted terrain leak stated (block changes
  stay visible until FOW-06).
- **Out of scope:** terrain secrecy (FOW-06).
- **Acceptance:**
  1. Each channel has a mock-player test showing that no data about fogged entities is sent, with a
     positive control (L3).
  2. A new payload type without declared audiences fails the audience test (L2).
  3. The audit table is in the docs, with the accepted leaks stated (D).
- **Tests:** L0, L2, L3, D · **Depends on:** FOW-02, NET-09 · **Size:** M · **Tier:** T1
- **Status:** in_progress

#### AI-07 · Fog-fair AI scouting and memory
- **Value:** The AI plays by the same information you have, and it scouts for it (P5).
- **In scope:** per D-19, the AI sees only its own and its allies' vision at every level except one
  labelled top level; early scouting with fast units; memory of last-seen enemy buildings
  (mirages).
- **Out of scope:** adaptive attack planning (AI-12).
- **Acceptance:**
  1. A fog-fair AI never targets an enemy it has not seen (L1).
  2. The AI scouts every start within the first 5 minutes (L1).
  3. Fog-fair Normal still beats Easy in at least 124 of 200 seeded matches (L1).
- **Tests:** L0, L1, L3 · **Depends on:** AI-18, FOW-03, D-19 · **Size:** M · **Tier:** T1
- **Status:** in_progress

#### PERF-15 · Fog perf gate
- **Value:** Fog costs no tick rate.
- **In scope:** the M8 row of the budget matrix: 4 × 150 population with fog and a fog-edge
  skirmish for 20 minutes; A–E; the 2× stress pass; PERF-14's fog scene with consent.
- **Out of scope:** terrain secrecy (FOW-06).
- **Acceptance:**
  1. Budgets A–E hold (L6).
  2. The stress pass holds C p95 ≤ 50 ms (L6).
  3. The report lists vision, re-tracking and pairing costs (R).
- **Tests:** L6, R · **Depends on:** FOW-02, PERF-10 · **Size:** S · **Tier:** T1
- **Status:** specified
