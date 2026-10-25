# M6 · MVP: First skirmish

Feature cards of milestone M6, the MVP. [ROADMAP.md](../ROADMAP.md) holds the overview, the
conventions and the status of every card (the source of truth); the statuses below mirror it.

## Milestone

**Goal.** The first complete game: a solo skirmish against a scripted AI, started from the title
screen: gather, build, train, fight, explore, advance from Village to Town and win by conquest.
Placeholder art; the civilisation art pass follows the MVP verdict (ART-03, M7).

**The owner plays.** From the title screen: Skirmish → first map → Normal AI. In a 20–35-minute
match the owner explores under fog with a horseman scout, grows the economy, chooses boom or rush,
reaches Town, researches equipment tiers that show on the units, builds a defence tower, trains
rams at the arsenal and razes the enemy civic centre after holding off waves announced by a raid
bar and a horn. The end screen shows the statistics.

**Solo fog.** FOW-01, FOW-07, UI-16, AI-18 and MATCH-07 bring solo-grade fog into the MVP: the owner took
D-24 (c) on 2026-10-21 ([M2's file](M2-command.md#d-24--exploration-in-the-mvp)); team fog follows in M8.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M6 cards passes; `verify`, `verifyClient` (owner-approved
   goldens, with consent or on CI) and `verifyPerf` are green.
2. QA-12 (L1, 200 fixed seeds per pairing; a 20-seed L3 sample in `verifySoak`): every AI-versus-AI
   match ends by conquest, with a median length of 20–35 game minutes and none over 45; no
   exception and no stall; each opening (rush, boom, balanced) wins 60–140 of 200 against each
   other opening; Hard beats Normal and Normal beats Easy in at least 124 of 200; neither start of
   MAP-02 wins 121 or more of 200.
3. L3: conquest ends the match; save and resume keep the state hash; pause stops the match while
   camera and UI keep working; an enemy outside vision is never paired while a visible control
   unit is (solo fog, D-24 (c)).
4. L6 (PERF-09): 2 × 150 population and 60 buildings with both sides active for 30 game minutes,
   within A–E and G, and C ≤ 23 ms ÷ the fastest game speed; the 2× stress pass holds.
5. MVP completeness (QA-13): every command-card entry has an icon; every MVP blueprint passes the
   construction and damage-stage GameTests; the naming and `NOTICE` lint passes; a provenance note
   exists per subsystem.
6. The production jar starts on a dedicated server (L5) and on a client (mc-runtime-test on CI,
   or L4 with consent).

**Acceptance** (owner; the MVP gate):
1. The owner plays one match per difficulty, one of them against a dedicated server, wins on Easy,
   and scores the questionnaire with no score of 2 or less; "would I play another?" goes into
   STATE.md with its reason.
2. Two private playtesters, friends of the owner, play at least one match each and fill in the
   questionnaire.
3. No P0 or P1 bug open. Then the 26.x gate (QA-14), if 2027-Q2 has not triggered it already, and
   the multiplayer stage.

**Retires.** F-R6 (the opponent); F-R7 at the MVP gate; F-R11 for solo play (solo fog, D-24 (c)).

**Size and critical path.** 23 cards (7 S, 14 M, 2 L), at most 69 card-days. Critical path: MAP-02 →
AI-04 → AI-05 → QA-12 → PERF-09 → QA-13 (19 card-days). The five solo-fog cards (D-24 (c)) add 9 of these
card-days.

---

## Decisions this milestone needs

D-05 is in the theme bundle ([M3](M3-first-loop.md#theme-bundle-d-03-d-04-d-05-d-10)), D-17 in the
world-rules bundle ([M1](M1-movement.md#d-17--arena-size)) and D-24 in the avatar-and-vision
bundle ([M2](M2-command.md#d-24--exploration-in-the-mvp)).

### D-19 · AI fairness and difficulty

**Question.** What the AI may know, and what it may receive.

| Option | For | Against | Card delta |
|---|---|---|---|
| Omniscient AI with handicaps | A strong AI cheaply | Players feel cheated; the reference AI's hard levels gather 25–56 % faster | – |
| Fog-fair AI (recommended) | Honest | Needs vision and memory | AI-18 (M6, D-24 (c)); AI-07 (M8) |
| Resource bonuses for higher levels | Easy scaling | Feels like cheating | – |

**Recommendation.** The AI only issues commands, at every level, and never writes game state.
Levels differ by timing, reaction, decision quality and open handicaps: penalties, shown in setup,
are allowed at every level; bonuses only at one labelled top level. Once fog exists, the AI sees
only its own vision at every level except that labelled top level.

**Needed by:** M6 (AI-05). **Affects:** AI-05, AI-18, AI-07, AI-15.

**Decided (2026-10-21):** as recommended, by the owner.

---

## Cards

### Phases, technologies and siege

#### TECH-01 · Research system
- **Value:** Research upgrades in your buildings, as in 0 A.D. and AoE (P4).
- **In scope:** technologies in data (cost, time, requirements combined with all and any, the
  researching building, supersede chains); research queued in buildings beside training; effects
  applied as DATA-02 modifiers on completion, `TOKENS` unlocks included; once per faction.
- **Out of scope:** the full tree (TECH-05).
- **Acceptance:**
  1. Requirement evaluation passes table-driven tests (L1).
  2. A completed technology changes the affected stats at once, and tooltips show the change (L1).
  3. A technology cannot be researched twice or in two buildings at once (L1).
  4. A research completes and applies in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** DATA-02, PROD-01 · **Size:** M · **Tier:** T0
- **Status:** in_progress

#### TECH-02 · Phases: Village to Town
- **Value:** Advancing to Town is a real decision, boom or rush, and a moment that unlocks new
  buildings, units and technologies (P4).
- **In scope:** phases as technologies with D-05's names; Town requirements (resources, stone or
  iron among them, plus a number of Village buildings, from the balance sheet); unlocks: the
  arsenal and the ram (D-12), the defence tower (BLD-17), Town technologies; phase bonuses as
  modifiers; a notice to every player; Town's cost and unlocks tuned until QA-12's opening check
  holds.
- **Out of scope:** the City phase (TECH-04).
- **Acceptance:**
  1. Phase requirements pass table-driven tests (L1).
  2. Reaching Town unlocks exactly the listed items (L1).
  3. The Town cost includes stone or iron (L1).
  4. Every player gets the phase notice (L3).
- **Tests:** L0, L1, L3 · **Depends on:** TECH-01, BLD-08, D-05 · **Size:** M · **Tier:** T0
- **Status:** specified

#### TECH-03 · Starting technologies and visible equipment tiers
- **Value:** Early research choices, a faster economy now or a stronger army later, and upgrades
  you can see on the units from the camera (P2, P4).
- **In scope:** one gathering technology per resource line (wood at the storehouse, farming at the
  farmstead, stone and iron mining), carry capacity, and two military technologies shown as
  equipment tiers with vanilla items and armour layers (stone → iron weapons, leather → chainmail;
  vanilla `ArmorMaterials` has `LEATHER`, `CHAIN`, `IRON` and `DIAMOND`); the tier as a
  faction-level value the unit renderer reads, sent to every match participant (enemies may see
  it; it shows on the units anyway), or as spawn data with a re-pair, the card recording which;
  balance-sheet entries for every tier.
- **Out of scope:** the full tree (TECH-05).
- **Acceptance:**
  1. Each technology's effect equals its modifier exactly (L1).
  2. The balance sheet lists every technology and tier (L1).
  3. Within 1 s of research, the new tier reaches every player who sees an affected unit (L3).
  4. A golden shows a unit before and after the tier change (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** TECH-01, UNIT-07 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### CMB-09 · Siege ram and the arsenal
- **Value:** The decisive answer to buildings: slow and tough, devastating to structures, helpless
  against soldiers (P4).
- **In scope:** per D-12: the arsenal (a Town building) trains rams on the `strategos:unit_large`
  type (2.0 × 2.0, tracking range 12, interval 3); rams deal crush damage, cannot attack units or
  fields, cost 3 population and use the large mobility class; soldiers counter them in melee; stone
  buildings need the ram (CMB-03's material classes).
- **Out of scope:** catapults (CMB-15); capture (CMB-10).
- **Acceptance:**
  1. Two rams raze a civic centre in exactly the balance-sheet time (L1).
  2. Rams never attack units (L1).
  3. The `unit_large` type's settings match ADR-0011 (L2).
  4. Rams pass only gaps that fit their footprint (L3).
- **Tests:** L0, L1, L2, L3, L5 · **Depends on:** CMB-03, UNIT-06, PATH-11, TECH-02, D-12 ·
  **Size:** M · **Tier:** T1 (pulled into the MVP by D-12)
- **Status:** specified

#### BLD-17 · Defence tower
- **Value:** A defensive path to Town (boom behind towers) and another way to spend stone (P4).
- **In scope:** a Town-phase tower that shoots with CMB-07's arrow system and costs stone; its
  blueprint with damage stages; garrison and upgrades stay in BLD-12 (M9).
- **Out of scope:** walls (BLD-13, BLD-14).
- **Acceptance:**
  1. Arrow count and damage per second equal the balance sheet (L1).
  2. The tower is available only in Town and costs stone (L1).
  3. Only enemies are targeted (L1).
  4. Its blueprint passes the lint and the construction and damage-stage GameTests (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-07, TECH-02 · **Size:** M · **Tier:** T0
- **Status:** specified

### The match

#### MATCH-14 · Spike: an arena world from the title screen
- **Value:** Starting a skirmish never asks the player to create a world by hand.
- **In scope:** proves, before MATCH-02 starts, that a `strategos:arena` preset world can be created
  programmatically on the client (`WorldOpenFlows#createFreshLevel`) when it is missing, with the
  arena rules applied once.
- **Out of scope:** the setup screen (MATCH-02).
- **Acceptance:**
  1. A scripted scenario creates the arena world from code, and it loads with the preset's biome
     and the arena rules applied once (L4, consent).
  2. The report states how MATCH-02 creates and reuses the world (R).
- **Tests:** L4 (consent), R · **Depends on:** MAP-07 · **Size:** S · **Tier:** T0
- **Status:** specified

#### MATCH-02 · Skirmish setup and launch
- **Value:** Start a skirmish from the main menu in a few clicks (P3).
- **In scope:** a "Skirmish" entry on the title screen (`ScreenEvent.Init.Post`); a setup screen
  with map, civilisation, AI difficulty, starting resources, population cap and visibility
  (MATCH-07); a skirmish is a new match in a fresh slot of the arena world, which is created once
  from the preset when it is missing (MATCH-14), never a new world per map; the match starts after
  prepare, with a progress bar.
- **Out of scope:** the multiplayer lobby (NET-02); the full option set (MATCH-08).
- **Acceptance:**
  1. The setup pipeline, run headless, creates a match with the map, the rules and the chosen
     settings (L3).
  2. A second skirmish takes the next free slot (L3).
  3. From the title screen to a running match takes at most 5 clicks (L4, consent).
  4. Setup-screen goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** MATCH-14, MAP-01, MATCH-01, UI-02 ·
  **Size:** L · **Tier:** T0
- **Status:** specified

#### MATCH-03 · Conquest, defeat and statistics
- **Value:** A clear ending: destroy everything the enemy has, or make them resign, then see how
  the match went.
- **In scope:** conquest: a faction with no conquest-critical entities (units and buildings,
  except fields and decorations) is defeated; the "civic centres only" option; resign; an end
  screen with resources gathered, units trained and lost, buildings and a timeline of phases;
  restart.
- **Out of scope:** other victory types (MATCH-10); allied victory (MATCH-06).
- **Acceptance:**
  1. Defeat triggers within 20 ticks of the last conquest-critical entity dying (L1).
  2. "Civic centres only" ends the match when the last one falls (L1).
  3. End-screen statistics equal the match counters (L1).
  4. A conquest ends a GameTest match (L3).
  5. End-screen golden (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** MATCH-01, BLD-08 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### MAP-02 · First skirmish map
- **Value:** A balanced 1 v 1 map whose terrain creates decisions, not only paths (P1, P4).
- **In scope:** a 384² map (D-17) from a seeded, diffable source with a terrain hash; two mirrored
  starts with equal resources within walking distance (trees, outcrops, mines, berry bushes,
  livestock, room for fields); each start's units: a civic centre, five workers and a horseman
  scout; terrain with a job, documented in the manifest and verified by the checker: a hill over
  the contested middle higher than CMB-16's threshold, a forest line between the bases with gaps,
  two fords and a contested iron deposit. Hand-finishing is a named owner task with a date.
- **Out of scope:** generated maps (MAP-03).
- **Acceptance:**
  1. The checker finds resource totals per start within 5 % and dropsite walking distances within
     10 % (L1).
  2. The checker verifies every documented terrain job (L1).
  3. Every start reaches every other for every mobility class (L1).
  4. In QA-12's L1 run, neither start wins 121 or more of 200 seeded matches (L1).
  5. The package imports with its terrain hash (L3).
- **Tests:** L0, L1, L3 · **Fixtures:** the skirmish map package with a terrain hash ·
  **Depends on:** MAP-01, ECO-02, ECO-16, D-17 · **Size:** M · **Tier:** T0
- **Status:** specified

### The opponent

#### AI-04 · AI openings, waves, raid bar and defence
- **Value:** An opponent with a plan: different openings, waves you see coming, and a base it
  defends (P5).
- **In scope:** two or three openings in data (rush, boom, balanced); timed waves sized by
  difficulty that muster at a camp marked with campfires and banners (cosmetic writes) and are
  presented as vanilla raids are: a red notched bar like `Raid`'s `ServerBossEvent`
  (`BossEvent.BossBarOverlay.NOTCHED_10`), drawn by our GUI layer from a notice payload so it stays
  inside the fog-aware sender, and `SoundEvents.RAID_HORN` as `fx`; every wave announced at least
  30 s before it leaves on Easy and Normal; defence that pulls soldiers to an attacked base, rings
  the bell (BLD-16) when workers are raided and retreats damaged units; target priorities (army,
  then economy, then civic centre); rebuilding lost buildings.
- **Out of scope:** adaptive attack planning (AI-12).
- **Acceptance:**
  1. Every wave on Easy and Normal is announced at least 30 s before it leaves (L1).
  2. When its base is attacked, the AI commits defenders within 10 s and rings the bell when
     workers are raided (L1).
  3. Different seeds pick different openings (L1).
  4. Waves reach the player's base on MAP-02 with no ladder rung above repath (L3, sample).
  5. The raid bar reaches only the players its notice addresses (L3).
  6. A golden of the raid bar (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** AI-03, MAP-02, BLD-16 · **Size:** L ·
  **Tier:** T0
- **Status:** specified

#### AI-05 · AI phases, research and difficulty levels
- **Value:** An AI that advances and researches, with three levels that feel different and never
  cheat below the top (P5).
- **In scope:** phase-up and research in the build orders; Easy, Normal and Hard through timings,
  wave sizes, reaction delays, decision quality and open handicaps, penalties shown in setup
  (D-19); no bonus below a labelled top level.
- **Out of scope:** the five-level ladder (AI-15).
- **Acceptance:**
  1. Normal reaches Town at the documented time ±2 minutes in 10 seeded L1 runs (L1).
  2. Hard beats Normal, and Normal beats Easy, in at least 124 of 200 seeded L1 matches (L1).
  3. No level below the labelled top receives a bonus, and every handicap is listed in setup (L1).
- **Tests:** L0, L1 · **Depends on:** AI-04, TECH-02, D-19 · **Size:** M · **Tier:** T0
- **Status:** specified

### Interface

#### UI-20 · Minimap orders, pings and live construction
- **Value:** Command from the minimap and see the map change as it happens (P3).
- **In scope:** a right-click on the minimap issues a move to that point; alert pings; construction
  and changed chunks redrawn within 1 s, at most once a second per changed chunk; the minimap's
  frame-cost check.
- **Out of scope:** fog on the minimap (UI-16); multiplayer pings (UI-15).
- **Acceptance:**
  1. A right-click sends exactly one move command to the mapped point (L1).
  2. A changed chunk's tile refreshes within 1 s and at most once a second (L1).
  3. The server applies the minimap order (L3).
  4. Minimap extraction stays under 0.5 ms (L6, JMH); PERF-14 confirms budget F.
  5. A golden with a ping (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent), L6 · **Depends on:** UI-11, UI-12 · **Size:** M ·
  **Tier:** T1 (pulled into the MVP by D-16)
- **Status:** specified

#### UI-21 · Notification feed and alert audio
- **Value:** Never miss what matters (an attack, a finished research, a new phase) and hear it
  without looking (P3).
- **In scope:** a prioritised feed with rate limits; research and phase completion; UI-10's economy
  notices and UI-12's alert in the same feed; alert sounds (under attack, phase complete, research
  complete, training complete, building complete) from vanilla sound events, played locally, with
  rate limits and volume categories.
- **Out of scope:** multiplayer pings (UI-15); music (AUD-03).
- **Acceptance:**
  1. Priorities and rate limits pass tests (L1).
  2. Each alert maps to one sound and respects its rate limit (L1).
  3. Feed golden (L4, consent).
  4. The owner's audio checklist passes (L7, acceptance).
- **Tests:** L0, L1, L4 (consent), L7 · **Depends on:** UI-12, TECH-01 · **Size:** M · **Tier:** T0
- **Status:** specified

#### UI-13 · Research and phase UI
- **Value:** Plan your phase-up: see what Town needs and what each technology does (P4).
- **In scope:** research buttons on building cards; progress; a phase-up panel with a checklist of
  requirements.
- **Out of scope:** the tree screen (TECH-05).
- **Acceptance:**
  1. The checklist mirrors the server's evaluation (L1).
  2. Research progress survives save and load (L3).
  3. Goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** UI-08, TECH-02 · **Size:** M · **Tier:** T0
- **Status:** specified

#### UI-14 · First-match hints and controls help
- **Value:** A new player learns the controls by playing, without a manual.
- **In scope:** contextual hints triggered by the situation (first selection, first idle worker,
  first time near the population cap), each dismissible and shown once per profile; an overlay with
  the current key bindings.
- **Out of scope:** the guided tutorial (UI-18).
- **Acceptance:**
  1. Each hint fires once per profile under its condition (L1).
  2. The overlay shows the current bindings, rebinds included (L1).
  3. Overlay golden (L4, consent).
  4. A friend playtester new to the game gets through the first 5 minutes with the hints alone
     (L7, acceptance).
- **Tests:** L0, L1, L4 (consent), L7 · **Depends on:** UI-21 · **Size:** M · **Tier:** T0
- **Status:** specified

### Solo fog (D-24 (c))

#### FOW-01 · Vision grid per faction
- **Value:** What your units and buildings see is what you know (P5).
- **In the MVP** by D-24 (c), decided 2026-10-21; team fog follows in M8.
- **In scope:** architecture §10.2–10.3: per faction, 4-block cells with a count of the vision
  sources covering each cell; sources: units and buildings (an observer avatar adds none, D-08;
  a fighting first-person avatar would count only if CMB-08 is revived); incremental updates
  every tick, unstamping and restamping discs from precomputed offset tables; `VisibilityIndex`,
  revealing a unit at once and hiding it after 10 ticks unseen; per faction only, with team
  vision in FOW-02; command validation treats a unit hidden from the issuer's faction as missing
  (CORE-03's `UNKNOWN_TARGET`).
- **Out of scope:** hiding terrain (FOW-06).
- **Acceptance:**
  1. Visibility equals brute force on random layouts (L1).
  2. A unit is revealed at once and hidden after 10 ticks unseen (L1).
  3. An observer adds no visible or explored cell (L1).
  4. Updates for 2 × 150 fit budget A (L6).
  5. A command that names an enemy unit hidden from the issuer's faction, as a unit to order or
     as an attack target, gets `UNKNOWN_TARGET` exactly as a missing handle does, while the same
     unit in vision gets `NOT_OWNER` as a unit to order and is accepted as a target (L1; the
     hidden case of CORE-03 AC4, which M0 cannot test without fog).
- **Tests:** L0, L1, L6 · **Depends on:** CORE-08 · **Size:** M · **Tier:** T1 (pulled into the MVP
  by D-24)
- **Status:** specified

#### FOW-07 · Explored state, the shroud and solo fog gating
- **Value:** A match starts in the dark: scouting matters, explored land stays dim, and hidden
  enemies are hidden, not just invisible (P5).
- **In the MVP** by D-24 (c), decided 2026-10-21; team fog follows in M8.
- **In scope:** explored state per faction, which never reverts; the client shroud: unexplored
  black, explored dimmed, visible clear, drawn as translucent quads with a vanilla render type that
  shader packs keep, with soft edges, under Sodium and Iris; `vision_delta` to the own faction at
  2 Hz; enemy bodies gated by the existing `FogGate` reading `VisibilityIndex`, refreshed every 5
  ticks through the public `ServerChunkCache#move(viewer)` for affected viewers (the
  zero-access-transformer fallback, cheap with one human viewer); units in fog left out of
  `unit_summary`; explored state saved with the match.
- **Out of scope:** team vision and targeted re-tracking (FOW-02); mirages (FOW-03); the leak
  audit (FOW-05).
- **Acceptance:**
  1. Explored cells never revert (L1).
  2. An enemy outside vision is never paired to a mock viewer, while a visible control unit is
     (L3).
  3. Entering vision pairs within 6 ticks, and leaving it unpairs within 15 (L3).
  4. Explored state survives save and load (L3).
  5. Shroud extraction stays within its L1 bench budget (L6, JMH); PERF-14 confirms budget F.
  6. Goldens for unexplored, explored and visible areas, also under Sodium and under Iris with a
     shader pack (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent), L5, L6 · **Depends on:** FOW-01, NET-08 · **Size:** M ·
  **Tier:** T1 (pulled into the MVP by D-24)
- **Status:** specified

#### UI-16 · Minimap fog and explored areas
- **Value:** The minimap shows what you know, and no more (P5).
- **In the MVP** by D-24 (c), decided 2026-10-21; team fog follows in M8.
- **In scope:** fog, explored and visible states on the minimap; enemy dots only where visible.
  Ghost icons of last-seen buildings come with FOW-03 (M8).
- **Out of scope:** the terrain image itself (UI-11).
- **Acceptance:**
  1. The minimap pixel array under fog equals its stored hash (L1).
  2. No enemy dot data reaches the client for fogged units, while visible ones arrive (L3).
  3. Golden (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** UI-11, FOW-07 · **Size:** S · **Tier:** T1
  (pulled into the MVP by D-24)
- **Status:** specified

#### AI-18 · The AI reads only its own vision
- **Value:** The AI plays with the information you have (P5).
- **In the MVP** by D-24 (c), decided 2026-10-21; team fog follows in M8.
- **In scope:** with fog on, `AiView` exposes only its faction's visibility (it already filters by
  it); the AI knows the start positions; no omniscience below the labelled top level (D-19).
- **Out of scope:** scouting and memory of last-seen buildings (AI-07).
- **Acceptance:**
  1. With fog on, every AI command target was visible to its faction when issued (L1).
  2. The AI finds the enemy base from the start positions in every seeded match (L1).
  3. Fogged AI-versus-AI matches still end within 45 game minutes (L1).
- **Tests:** L0, L1 · **Depends on:** AI-01, FOW-01 · **Size:** S · **Tier:** T1 (pulled into the
  MVP by D-24)
- **Status:** specified

#### MATCH-07 · Visibility options
- **Value:** Choose how much of the map you know at the start.
- **In the MVP** by D-24 (c), decided 2026-10-21; team fog follows in M8.
- **In scope:** setup options: fog, explored at start, revealed map (`MatchRules.revealMap`).
- **Out of scope:** the full setup screen (MATCH-08).
- **Acceptance:**
  1. Each option initialises the grids as specified (L1).
  2. A revealed map makes `FogGate` answer "visible" for every participant (L3).
- **Tests:** L0, L1, L3 · **Depends on:** FOW-07, MATCH-02 · **Size:** S · **Tier:** T1 (pulled
  into the MVP by D-24)
- **Status:** specified

### The MVP gate

#### QA-12 · AI-versus-AI soak and opening balance
- **Value:** Each release candidate has played hundreds of full matches unattended, and no opening
  dominates, so boom or rush is a real choice (P4, P5).
- **In scope:** QA-11 at MVP scale: L1 on 200 fixed seeds per pairing as the gate; a 20-seed L3
  sample in `verifySoak` as a smoke test with no win-rate threshold and a stated wall time;
  pairings of openings (rush, boom, balanced) at equal difficulty and of difficulty levels;
  pacing (median 20–35 game minutes, every match within 45); telemetry, and a summary of win rates
  per start, opening and level.
- **Out of scope:** the balance matrix across civilisations (QA-16).
- **Acceptance:**
  1. Every match ends by conquest within 45 game minutes, with a median of 20–35, no exception and
     no stall (L1).
  2. Each opening wins 60–140 of 200 matches against each other opening (L1).
  3. The summary reports win rates per start, opening and level (L1).
  4. The L3 sample completes, and its journals replay to the same hash (L3).
- **Tests:** L1, L3 · **Fixtures:** soak seed sets · **Depends on:** QA-11, AI-05, MATCH-03 ·
  **Size:** S · **Tier:** T0
- **Status:** specified

#### QA-13 · MVP release-candidate gate
- **Value:** The MVP the owner plays is complete, stable and honest about its origins.
- **In scope:** a checklist: every level green, goldens approved, perf gates; the owner's matches
  and two friends' playtests with reports; no P0 or P1 open; placeholder completeness (an icon for
  every command-card entry; every MVP blueprint through the construction and damage-stage tests);
  the production jar on a client (mc-runtime-test on CI, or L4 with consent) and on a dedicated
  server; a lint over mod id, display name, tags, README title and `NOTICE` (the RVO2 notice
  present; no "Age of Empires", "AoE" or "0 A.D." in name, logo or primary tags); a provenance note
  per subsystem; feasibility §6.3's automated criteria as the MVP regression suite.
- **Out of scope:** public release (D-21).
- **Acceptance:**
  1. The naming and `NOTICE` lint passes (L0).
  2. Every command-card entry has an icon (L2).
  3. Every MVP blueprint passes the construction and damage-stage GameTests (L3).
  4. The production jar starts on a dedicated server (L5).
  5. It starts on a client (L4, consent; or mc-runtime-test on CI).
  6. A provenance note exists for every subsystem (D).
  7. The owner signs off the checklist (L7, acceptance).
- **Tests:** L0, L2, L3, L4 (consent), L5, L7, D · **Depends on:** QA-12, QA-09, PERF-09 ·
  **Size:** S · **Tier:** T0
- **Status:** specified

#### PERF-09 · MVP perf gate
- **Value:** The MVP plays smoothly from the first minute to the last.
- **In scope:** the M6 row of the budget matrix: 2 × 150 population, 60 buildings and active AIs for
  30 game minutes; A–E and G; C ≤ 23 ms ÷ the fastest game speed; per-unit normalisation; the 2×
  stress pass; archived as the MVP baseline; PERF-14 with consent.
- **Out of scope:** multiplayer scale (PERF-10).
- **Acceptance:**
  1. Budgets A–E and G hold, autosave included (L6).
  2. C p95 ≤ 23 ms ÷ the fastest offered game speed (L6).
  3. The stress pass holds C p95 ≤ 50 ms (L6).
  4. The report is archived as the MVP baseline (R).
- **Tests:** L6, R · **Depends on:** PERF-08, QA-12 · **Size:** S · **Tier:** T0
- **Status:** specified
