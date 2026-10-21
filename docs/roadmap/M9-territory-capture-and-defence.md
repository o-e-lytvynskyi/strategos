# M9 · Territory, capture and defence

Feature cards of milestone M9. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** 0 A.D.'s identity: territory that limits building, buildings you capture instead of raze,
the full garrison (healing, ejection, capture regeneration), towers that garrison and upgrade, and
palisades with gates.

**The owner plays.** Borders show on the ground and on the minimap, and building outside them is
refused. Twenty infantry capture an enemy barracks; a sentry tower and a palisade with a gate hold
a chokepoint; wounded soldiers heal inside a garrison.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M9 cards passes.
2. L1: territory influence and capture maths, including the rule that a building changes owner
   only at zero owner points; identical territory with 0, 1 and 4 threads.
3. L3: a garrison round trip keeps every unit's state; gates open for allies only.
4. L6 (PERF-16): 4 × 150 population with territory churn and 40 garrisoned units for 15 minutes
   within A–C and E; territory never blocks a tick; the 2× stress pass holds.
5. Consent batch: border goldens, also under Sodium; PERF-14's border scene (budget F's 200 units
   on screen, with borders on the terrain and the minimap).

**Acceptance** (owner): a playtest with the questionnaire; no P0 or P1 bug open.

**Retires.** No risk from the register; this milestone adds 0 A.D.'s visible signatures (D-25).

**Size and critical path.** 10 cards (1 S, 6 M, 3 L), at most 49 card-days. Critical path: TERR-01 →
CMB-10 → BLD-10 → AI-08 (26 card-days).

---

## Cards

#### TERR-01 · Territory influence grid
- **Value:** Your settlement claims land around it, and the map shows who owns what (P1).
- **In scope:** architecture §10.6: `sim.territory` in the `WORLD` phase on 8 × 8-block tiles:
  influence per building (weight, radius, root flag) with linear falloff; ownership by the highest
  summed weight; connectivity to roots; recomputed on change under a per-tick work cap (at most one
  recompute per 20 ticks), or as a pure job with deterministic delivery like paths; a compact sync
  payload.
- **Out of scope:** decay (TERR-04).
- **Acceptance:**
  1. Ownership and connectivity equal a reference implementation on random layouts (L1).
  2. Results are identical with 0, 1 and 4 threads (L1).
  3. A recompute after a civic centre completes stays within its per-tick cap and never blocks a
     tick on a 384² map (L6).
  4. An update payload stays under 8 KB (L2).
- **Tests:** L0, L1, L2, L6 · **Depends on:** BLD-08, CORE-02 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### TERR-02 · Territory build rules
- **Value:** Expansion is a strategic choice: you build in your territory, and new civic centres
  claim more (P4).
- **In scope:** buildings only in own territory; civic centres also in neutral territory, a second
  one from Town on; one more validator in `PlacementRules`, replacing BLD-02's distance stand-in.
- **Out of scope:** decay of unconnected buildings (TERR-04).
- **Acceptance:**
  1. A placement outside own territory is refused with the reason (L1).
  2. A second civic centre can be placed in neutral territory after Town (L1).
  3. AI placement respects the rules (L1).
  4. A refused placement in a GameTest shows the reason (L3).
- **Tests:** L0, L1, L3 · **Depends on:** TERR-01, BLD-02, AI-02 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### TERR-03 · Borders on terrain and minimap
- **Value:** Borders are visible where they matter, on the ground and on the minimap (P2).
- **In scope:** border lines baked into terrain sections through `AddSectionGeometryEvent`, with
  data copied on the main thread because its renderers run on chunk-build threads, re-meshed
  through `LevelRenderer#setSectionDirty`; minimap borders; ally and enemy colours.
- **Out of scope:** territory effects beyond building (TERR-04).
- **Acceptance:**
  1. Border geometry for a test layout equals its stored hash (L1).
  2. A border change re-meshes only the affected sections (L1).
  3. Border extraction stays within its L1 bench budget (L6, JMH); PERF-14's border scene, in
     M9's consent batch, confirms budget F.
  4. Goldens, also under Sodium (L4, consent).
- **Tests:** L0, L1, L4 (consent), L6 · **Depends on:** TERR-01, UI-20 · **Size:** M ·
  **Tier:** T1
- **Status:** proposed

#### CMB-10 · Capturing buildings
- **Value:** Take enemy buildings instead of razing them, as in 0 A.D. (P4).
- **In scope:** capture points split by player; capture strength per unit; owner regeneration plus
  garrison; ownership changes only when the owner's points reach zero, then goes to the player with
  the most points; damage speeds capture (the effect divided by 0.1 + 0.9 × the health fraction); a
  capture bar; a force-capture key; fields and walls can't be captured; queues refunded on capture;
  numbers re-derived at our scale.
- **Out of scope:** decay by territory (TERR-04).
- **Acceptance:**
  1. Capture maths reproduce worked examples at our scale (L1).
  2. A building changes owner only at zero owner points, never on a majority (L1).
  3. A captured barracks trains for its new owner (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-03, TERR-01, MATCH-06 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### BLD-10 · Garrison: healing, ejection and capture regeneration
- **Value:** Garrisons protect and restore your army, and they hold buildings against capture
  (P4).
- **In scope:** what BLD-16 left: healing inside; ejection below 10 % health; garrisoned units add
  to capture regeneration; rally points that garrison; houses and towers garrison by data.
- **Out of scope:** units on walls (BLD-14).
- **Acceptance:**
  1. Garrisoned units heal at the data rate (L1).
  2. Units are ejected when the building drops below 10 % health (L1).
  3. Garrisoned units raise capture regeneration (L1).
  4. A rally point on a building garrisons new units (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-16, CMB-10 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### BLD-12 · Sentry tower and tower upgrades
- **Value:** Hold chokepoints and protect your economy with towers (P4).
- **In scope:** a sentry tower in Village; BLD-17's defence tower gains a garrison (BLD-11's extra
  arrows) and an upgrade path; upgrades keep the garrison and the health ratio.
- **Out of scope:** walls (BLD-13, BLD-14).
- **Acceptance:**
  1. Tower damage and garrison follow the data (L1).
  2. Upgrading keeps the garrison and the health ratio (L3).
  3. Tower blueprints pass the construction and damage-stage tests (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-17, BLD-11 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### BLD-13 · Palisades and gates
- **Value:** Wall off chokepoints with wooden palisades and let your own units through the gates
  (P1, P4).
- **In scope:** palisade segments drawn by click and drag along the terrain; gates that open for the
  owner and allies; palisades can't be captured; construction per segment.
- **Out of scope:** stone walls (BLD-14).
- **Acceptance:**
  1. A drawn line becomes continuous segments that follow the terrain (L1).
  2. The built segments match the layout (L3).
  3. Gates open for allies and stay shut for enemies (L3).
  4. Enemies path around a palisade or attack it (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-04, PATH-12 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### PATH-12 · Gates and doors in navigation
- **Value:** Units pass their own gates without stopping and treat enemy gates as walls.
- **In scope:** gate cells passable for owner and allies only, as core overlays; door states;
  reachability per faction.
- **Out of scope:** the walls themselves (BLD-13).
- **Acceptance:**
  1. Reachability per faction respects gates (L1).
  2. Units pass their own gates without stopping (L3).
- **Tests:** L0, L1, L3 · **Depends on:** PATH-04, MATCH-06 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### AI-08 · AI uses territory and capture
- **Value:** The AI attacks and defends with the new systems instead of ignoring them (P5).
- **In scope:** the AI places within territory, captures weakly defended buildings and garrisons
  towers and houses under attack; it already rings the bell (AI-04).
- **Out of scope:** expansion planning (AI-11).
- **Acceptance:**
  1. The AI captures an undefended building in a seeded scenario (L1).
  2. No AI placement fails for territory reasons (L1).
  3. The AI garrisons a tower under attack (L1).
- **Tests:** L0, L1, L3 · **Depends on:** AI-05, CMB-10, BLD-10, TERR-02 · **Size:** M ·
  **Tier:** T1
- **Status:** proposed

#### PERF-16 · Territory and garrison perf gate
- **Value:** Territory and garrisons cost no tick rate.
- **In scope:** the M9 row of the budget matrix: 4 × 150 population, territory churn and 40
  garrisoned units for 15 minutes; A–C and E; the 2× stress pass.
- **Out of scope:** client cost (PERF-14's border scene).
- **Acceptance:**
  1. Budgets A–C and E hold (L6).
  2. The stress pass holds C p95 ≤ 50 ms (L6).
  3. The report lists territory and garrison costs (R).
- **Tests:** L6, R · **Depends on:** TERR-01, BLD-10, PERF-10 · **Size:** S · **Tier:** T1
- **Status:** proposed
