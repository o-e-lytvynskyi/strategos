# M10 · Second civilisation and depth

Feature cards of milestone M10. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** A second civilisation proves the data model; the City phase, the full technology tree
with its equipment lines, abilities, promotion, healing, loot, formations, patrols, hunting, the
full hotkey set, an optional orthographic view and the first Minecraft twist deepen the game.

**The owner plays.** The two civilisations of D-03 meet on the MVP map with different rosters,
bonuses, abilities and architecture. Veterans show their rank, healers mend troops at a temple,
armies march in box and line formations, and, with the day-and-night twist, night falls and the
torches on buildings light up the fog.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M10 cards passes.
2. QA-16 (L1, 200 fixed seeds per pairing): each civilisation wins 80–120 of 200 mirrored matches;
   the L3 samples are smoke tests with no win-rate threshold.
3. L1: a generated test per technology proves its effect, with one L3 integration sample.
4. L6 (PERF-17): two civilisations, formations of 40 and fauna within A–E; the 2× stress pass
   holds.
5. Consent batch: the M10 goldens and PERF-14.

**Acceptance** (owner): a playtest with both civilisations and the questionnaire; the owner
approves ART-04's look.

**Retires.** F-R17 for two civilisations; F-R18 for the second art set.

**Size and critical path.** 18 cards (2 S, 12 M, 4 L), at most 78 card-days. Critical path: CIV-01 →
CIV-02 → ART-04 (23 card-days).

D-03 (the theme bundle, [M3](M3-first-loop.md#theme-bundle-d-03-d-04-d-05-d-10)) must be decided
before CIV-02; D-09 ([M2](M2-command.md#d-09--camera-projection)) governs CAM-06 and D-13
([M1](M1-movement.md#d-13--which-minecraft-systems-run-and-which-become-mechanics)) MATCH-16.

---

## Cards

#### CIV-01 · Civilisation framework
- **Value:** Civilisations differ in data (rosters, buildings, bonuses), so adding one needs no
  code.
- **In scope:** civilisation definitions in data: roster, buildings, technologies, bonuses as
  modifiers, starting entities, names, house size, optional citizen-soldiers (D-02), unique
  abilities through UNIT-10; civilisation choice in setup; trainer-list entries that resolve per
  civilisation.
- **Out of scope:** the second civilisation's content (CIV-02).
- **Acceptance:**
  1. Civilisation data loads and validates (L2).
  2. Changing a faction's civilisation changes its roster and buildings (L1).
  3. Bonuses appear in tooltips through the modifier breakdown (L1).
  4. A match with two civilisations runs in a GameTest (L3).
- **Tests:** L0, L1, L2, L3 · **Depends on:** DATA-03, MATCH-02 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### CIV-02 · Civilisation 2: roster, bonuses and abilities
- **Value:** A second people that plays differently, not just in another colour (P4).
- **In scope:** per D-03: at least two unique units, which may use UNIT-10's abilities, two or
  three bonuses, a house variant and phase display names; balance-sheet entries.
- **Out of scope:** its art (ART-04).
- **Acceptance:**
  1. Every unit and building passes the existing GameTests (L3).
  2. The balance sheet has a complete section for it (L1).
  3. QA-16 pairings fall within 80–120 of 200 (L1).
- **Tests:** L0, L1, L3 · **Depends on:** CIV-01, UNIT-10, D-03 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### ART-04 · Civilisation 2 art set
- **Value:** The second civilisation is recognisable at a glance (P2).
- **In scope:** per D-04: skins, banners, blueprints for its starting and City buildings, icons;
  a named owner task where it needs the owner's hands or outside help.
- **Out of scope:** further civilisations (ART-05).
- **Acceptance:**
  1. Blueprints pass the lint and the construction and damage-stage tests (L3).
  2. Goldens (L4, consent).
  3. The owner approves the look (L7, acceptance).
- **Tests:** L2, L3, L4 (consent), L7 · **Depends on:** CIV-02, ART-03 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### TECH-04 · City phase
- **Value:** A late-game goal with its own power spike (P4).
- **In scope:** City requirements and unlocks; phase bonuses (a larger territory radius for civic
  centres, stronger building arrows) as modifiers.
- **Out of scope:** heroes (CMB-14).
- **Acceptance:**
  1. Requirements and unlocks pass table-driven tests (L1).
  2. The territory radius bonus applies on completion (L1).
  3. A GameTest phase-up to City applies the bonuses (L3).
- **Tests:** L0, L1, L3 · **Depends on:** TECH-02, TERR-01 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### TECH-05 · Technology tree and tree screen
- **Value:** Real strategic choices across three phases, with upgrades you can see (P2, P4).
- **In scope:** about 30 technologies in data across economy, military and buildings, carrying
  TECH-03's resource lines and equipment tiers to their full length (vanilla's `IRON` and `DIAMOND`
  armour materials included); paired technologies, choose one of two; a technology-tree screen.
- **Out of scope:** civilisation-unique mechanics (CIV-04).
- **Acceptance:**
  1. A generated test per technology proves its effect (L1).
  2. One integration sample per technology family passes in a GameTest (L3).
  3. Paired technologies exclude each other, and the balance sheet lists every tier (L1).
  4. Tree-screen goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** TECH-04, UI-13, TECH-03 · **Size:** L ·
  **Tier:** T1
- **Status:** proposed

#### UNIT-10 · Abilities
- **Value:** Units with special actions, the cheapest asymmetry between civilisations, without a
  new command type per ability (P4).
- **In scope:** architecture §7.6: `AbilityDef` in templates (targeting none, point, unit or area;
  range; cooldown; cost; autocast); the `UseAbility` command; effect primitives `Damage`, `Heal`,
  `ApplyModifiers` for N ticks, `Spawn`, `CaptureStrength` and `Teleport`, run through the existing
  damage and modifier pipelines; command-card buttons from the template; one showcase unit
  ability. CMB-12 and CMB-14 build on it.
- **Out of scope:** heroes (CMB-14).
- **Acceptance:**
  1. Range, cooldown and cost are validated at the tick boundary (L1).
  2. Each effect primitive passes table-driven tests (L1).
  3. Command-card buttons come from the template (L1).
  4. The showcase ability works in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CORE-03, DATA-02 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### CMB-11 · Promotion and ranks
- **Value:** Veterans matter: units that survive fights get stronger and show it (P2).
- **In scope:** experience from damage dealt, scaled by the target's value; thresholds per class;
  ranks as modifier sets, with compounding decided explicitly; a visible insignia; the rank in
  spawn data.
- **Out of scope:** heroes (CMB-14).
- **Acceptance:**
  1. Experience and thresholds pass table-driven tests (L1).
  2. Rank modifiers apply, compounding as documented (L1).
  3. Insignia are visible at 40 blocks (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** CMB-01, DATA-02 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### CMB-12 · Healing
- **Value:** Keep an army alive between fights (P4).
- **In scope:** a healer unit trained at a temple, with a heal ability (UNIT-10); heal rate and
  range; garrison healing in the civic centre and temple (BLD-10); siege engines and buildings are
  repaired, not healed.
- **Out of scope:** auras (CMB-14).
- **Acceptance:**
  1. Heal rates match the data (L1).
  2. Healers pick the most wounded unit nearby (L1).
  3. Siege engines and buildings can't be healed (L1).
  4. A heal works in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-05, BLD-10, UNIT-10 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### CMB-13 · Loot
- **Value:** Winning a fight pays (P4).
- **In scope:** the killer's faction receives the victim's loot from data plus what it was
  carrying; loot counted as its own source in the treasury invariant.
- **Out of scope:** treasures (ECO-11).
- **Acceptance:**
  1. Loot per kill equals the data (L1).
  2. Treasury conservation holds with loot as a source (L1).
  3. A kill pays out in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-01, ECO-01 · **Size:** S · **Tier:** T1
- **Status:** proposed

#### ORD-06 · Formations (move only)
- **Value:** Move armies in tidy box or line formations (P3).
- **In scope:** a `FormationController` as a core entity, not a Minecraft entity; box and line;
  slots by class with greedy assignment by sorted projection; the formation moves at its slowest
  member's speed; members fight individually, and the formation dissolves on gather or attack
  orders.
- **Out of scope:** special formations (ORD-08).
- **Acceptance:**
  1. A formation of 40 keeps slot error under 1 block while moving on flat ground (L1).
  2. Formation speed equals its slowest member's (L1).
  3. An attack order breaks the formation (L1).
  4. A formation marches across the proving ground in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ORD-03, PATH-09 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### ORD-07 · Patrol and guard
- **Value:** Standing orders that keep working while you look elsewhere (P3).
- **In scope:** patrol between points with attack-move behaviour; guard a unit or building, following
  and defending it.
- **Out of scope:** formations (ORD-06).
- **Acceptance:**
  1. A patrol loops and engages enemies it meets (L1).
  2. A guard follows its charge and defends it (L1).
  3. Both orders work in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ORD-04 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### ECO-10 · Wild herds and hunting
- **Value:** Hunting is a fast early food source and a reason to scout (P1).
- **In scope:** wild fauna on ECO-16's archetype that flee by their stance; horsemen hunt; kills
  gathered as meat nodes.
- **Out of scope:** corrals and herding (ECO-12).
- **Acceptance:**
  1. Hunt yield and timing match the data (L1).
  2. Animals flee as their stance says (L1).
  3. Fauna fit the unit budgets (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** ECO-16, UNIT-06 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### UI-17 · Full hotkeys and idle-unit cycling
- **Value:** Experienced players can play at speed from the keyboard (P3).
- **In scope:** the full hotkey set (queue, stop, back to work, garrison, delete, cycle idle
  soldiers, select all soldiers or all workers, camera bookmarks); the in-game key reference.
- **Out of scope:** controller support.
- **Acceptance:**
  1. Every command-card action has a hotkey (L1).
  2. The key reference matches the bindings (L1).
  3. Idle-soldier and idle-worker cycling work on screen (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** UI-08 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### CAM-06 · Orthographic view (optional)
- **Value:** The classic flat RTS look for players who prefer it.
- **In scope:** per D-09: an orthographic projection through a mixin on
  `GameRenderer#getProjectionMatrix` and a bypass of `Frustum#offsetToFullyIncludeCameraCube`,
  which otherwise loops forever; an option toggle; Sodium and Iris checks with a fallback to
  perspective; the inventory entry.
- **Out of scope:** picking changes beyond the projection.
- **Acceptance:**
  1. Picking stays exact in orthographic view (L1).
  2. The inventory lists the mixin (L2).
  3. Switching projection never freezes the game (L4, consent).
  4. Under Sodium and Iris it works or falls back cleanly (L4, consent).
- **Tests:** L0, L1, L2, L4 (consent) · **Depends on:** CAM-02, D-09 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### MATCH-16 · First Minecraft twist: day and night
- **Value:** A signature mechanic only Minecraft can give: night shrinks what you see, and the
  torches on your buildings push back the dark (P1).
- **In scope:** the first twist from D-13's menu, the owner's pick; recommended: day and night as a
  match rule, off by default: vision radius shrinks at night, and torches and campfires on
  buildings become vision sources. World time belongs to the level and is synchronised to every
  player in it (`MinecraftServer#synchronizeTime`), and daylight is a world-wide gamerule, so a
  visible night is world-wide, which is fine with one match per world (D-20), or it is drawn as a
  client-side tint.
- **Out of scope:** night raiders and weather (later twists).
- **Acceptance:**
  1. Vision radius follows the time-of-day rule exactly (L1).
  2. Torches and campfires on buildings add vision sources (L1).
  3. The rule is journaled and replays to the same hash (L1).
  4. Night goldens (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** FOW-01, MATCH-01, D-13 · **Size:** M ·
  **Tier:** T2
- **Status:** proposed

#### AI-09 · AI uses the new content
- **Value:** The AI plays both civilisations and the late game well (P5).
- **In scope:** rosters per civilisation, the City phase, abilities, healers, rank-aware retreats,
  formations for marches.
- **Out of scope:** the manager architecture (AI-10).
- **Acceptance:**
  1. The AI reaches City with both civilisations in seeded runs (L1).
  2. The AI trains at least one unique unit of each civilisation (L1).
  3. An L3 sample reaches the hash of its L1 run (L3).
- **Tests:** L0, L1, L3 · **Depends on:** AI-08, CIV-02, TECH-04 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### QA-16 · Balance matrix
- **Value:** Civilisations and difficulty levels stay fair as content grows (P4).
- **In scope:** AI-versus-AI matrices by civilisation pairing and difficulty, 200 fixed seeds per
  pairing in L1, with 20-seed L3 samples as smoke tests; win rates, match length and phase times; a
  diff of the balance sheet between runs.
- **Out of scope:** human playtests.
- **Acceptance:**
  1. The matrix runs from one command (L1).
  2. The report flags any pairing outside 80–120 of 200 (L1).
  3. Results reproduce from their seeds (L1).
  4. The L3 samples complete and replay to the same hash (L3).
- **Tests:** L1, L3 · **Fixtures:** balance seed sets · **Depends on:** QA-12, CIV-02 ·
  **Size:** M · **Tier:** T1
- **Status:** proposed

#### PERF-17 · Second-civilisation perf gate
- **Value:** More content never costs tick rate.
- **In scope:** the M10 row of the budget matrix: two civilisations, formations of 40 and fauna for
  15 minutes; A–E; the 2× stress pass; PERF-14 with consent.
- **Out of scope:** AI cost (PERF-11).
- **Acceptance:**
  1. Budgets A–E hold (L6).
  2. The stress pass holds C p95 ≤ 50 ms (L6).
  3. The report lists the cost of formations, fauna and abilities (R).
- **Tests:** L6, R · **Depends on:** CIV-02, ORD-06, ECO-10, PERF-16 · **Size:** S · **Tier:** T1
- **Status:** proposed
