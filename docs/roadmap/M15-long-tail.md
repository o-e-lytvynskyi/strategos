# M15 · Long tail

Feature cards of milestone M15. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** The catalog's T3 items, each chosen on its own merits once the game stands: scenarios and
campaigns, replays, terrain secrecy, civilisation-unique mechanics, a public content API,
localisation and accessibility, spectators, and original music.

**The owner plays.** A short scenario with objectives; a replay of a finished match with seeking; a
third-party data pack that adds a unit.

**Done gate** (automated; plus the cumulative rule): every acceptance criterion of the approved
M15 cards passes.

**Acceptance** (owner): a review of each delivered feature.

**Retires.** No risk from the register; each card carries its own.

**Size and critical path.** 8 cards (0 S, 4 M, 4 L), at most 52 card-days. Critical path: MATCH-12
(10 card-days).

---

## Cards

#### MATCH-12 · Scenarios and campaigns
- **Value:** Story-driven matches with goals beyond conquest.
- **In scope:** a trigger and objective system in data (events, conditions, actions); a scenario
  format; a campaign as an ordered list of scenarios.
- **Out of scope:** cutscenes.
- **Acceptance:**
  1. A sample scenario's triggers fire in order in a scripted run (L1).
  2. Scenario data validates with clear errors (L2).
  3. The sample scenario plays through in a GameTest (L3).
- **Tests:** L0, L1, L2, L3 · **Depends on:** MATCH-03, UI-18 · **Size:** L · **Tier:** T3
- **Status:** proposed

#### MATCH-13 · Replays
- **Value:** Watch a finished match again and learn from it.
- **In scope:** built on the input journal: full-match journal retention (segments and snapshots
  from CORE-11, the production ring from MATCH-04) and a playback UI with seeking from the nearest
  snapshot.
- **Out of scope:** live spectating (NET-07).
- **Acceptance:**
  1. Playing back a recorded match reaches the recorded hashes at every snapshot (L1).
  2. Seeking to any minute takes under 5 s (L6).
  3. Playback UI golden (L4, consent).
- **Tests:** L0, L1, L4 (consent), L6 · **Depends on:** CORE-11, MATCH-04 · **Size:** L ·
  **Tier:** T3
- **Status:** proposed

#### FOW-06 · Terrain secrecy
- **Value:** Competitive fog: terrain and buildings in fog are hidden from modified clients too.
- **In scope:** gating of chunk and block-change packets per player, with match-start terrain
  snapshots for fogged areas; mixins kept minimal and tested against Moonrise and Sodium; their
  inventory entries.
- **Out of scope:** open-world match areas (MAP-06).
- **Acceptance:**
  1. A mock player receives no block change from a fogged column, with a positive control (L3).
  2. A rejoining player receives the snapshot, not the live terrain, in fogged areas (L3).
  3. Gating fits budget A at 4 × 150 population (L6).
- **Tests:** L0, L2, L3, L6 · **Depends on:** FOW-05 · **Size:** L · **Tier:** T3
- **Status:** proposed

#### CIV-04 · Civilisation-unique mechanics and mercenaries
- **Value:** Civilisations with signature mechanics, in the AoE IV spirit.
- **In scope:** options such as landmarks that advance the phase and mercenaries paid in a premium
  resource; each mechanic as data plus the smallest code that enables it. Sacred sites are
  MATCH-15's.
- **Out of scope:** civilisations beyond the four of M14.
- **Acceptance:**
  1. Each mechanic's rules pass their tests (L1).
  2. Each mechanic works in a GameTest (L3).
  3. QA-16 pairings stay within 80–120 of 200 (L1).
- **Tests:** L0, L1, L3 · **Depends on:** CIV-03, TECH-05 · **Size:** L · **Tier:** T3
- **Status:** proposed

#### DATA-05 · Public content API and authoring guide
- **Value:** Others can add units, buildings, civilisations and maps with data packs.
- **In scope:** a documented, versioned template schema; validation messages for authors; an
  authoring guide with examples; schema stability rules.
- **Out of scope:** a Java API for other mods.
- **Acceptance:**
  1. A sample external data pack adds a unit that loads and plays (L3).
  2. Schema changes are versioned with migrations (L2).
  3. The guide covers every template section with an example (D).
- **Tests:** L0, L2, L3, D · **Depends on:** DATA-03, CIV-01 · **Size:** M · **Tier:** T3
- **Status:** proposed

#### UI-19 · Localisation and accessibility
- **Value:** Players in other languages, and colour-blind players, can play comfortably.
- **In scope:** every string in language files; colour-blind palettes for faction colours; UI scale
  options.
- **Out of scope:** voice.
- **Acceptance:**
  1. A completeness check finds no hard-coded string (L2).
  2. Faction colours stay distinct under the colour-blind palettes (L1).
- **Tests:** L0, L1, L2 · **Depends on:** UI-17 · **Size:** M · **Tier:** T3
- **Status:** proposed

#### NET-07 · Spectators and observers
- **Value:** Friends can watch a match live.
- **In scope:** observer slots that see everything, or one team's view, without affecting play.
- **Out of scope:** broadcasting tools.
- **Acceptance:**
  1. Observers can't issue match commands (L3).
  2. A team observer receives only that team's visible data, with a positive control (L3).
- **Tests:** L0, L3 · **Depends on:** NET-02, FOW-02 · **Size:** M · **Tier:** T3
- **Status:** proposed

#### AUD-03 · Original music and sound cues
- **Value:** A soundscape of its own.
- **In scope:** original music per phase and original cues for key events; no copied assets; a
  provenance note per asset.
- **Out of scope:** voice acting.
- **Acceptance:**
  1. Each phase has its own track and plays on cue (L7, acceptance).
  2. A provenance note exists for every asset (D).
- **Tests:** L7, D · **Depends on:** UI-21 · **Size:** M · **Tier:** T3
- **Status:** proposed
