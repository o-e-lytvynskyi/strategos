# M3 · First loop

Feature cards of milestone M3. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it.

## Milestone

**Goal.** Play the whole loop early, crude but complete: gather wood and food, build block by
block, train workers and spearmen, fight, and destroy the opponent's civic centre while a scripted
bot does the same. The verdict answers "is this fun?" before M4 to M6 deepen the systems
(decision gate 3).

**The owner plays.** Both sides start on the proving ground with a civic centre and five workers.
The owner chops real trees log by log, with cracks, particles and sounds, picks berries and lays
out fields; places a house and a barracks that rise block by block; sends new units straight to
work with rally points; finds idle workers with one key; spots the bot's base on the minimap;
hears its announced attack coming; and burns the bot's civic centre with torches. A headless
bot-versus-bot run of the same setup ends with a winner.

**Order: four playable slices.** Each slice ends with a build the owner can play.

| Slice | Cards | The owner can |
|---|---|---|
| 3.1 Economy | DATA-04, ECO-01, ECO-02, ECO-03, ECO-05, UNIT-04, BLD-01 (a civic centre placed instantly), AUD-04 | chop, pick berries and see the treasury grow |
| 3.2 Building and training | BLD-02, BLD-03, ECO-06, ECO-09, PROD-01, PROD-03, UI-06, UI-07, UI-10, ART-01 | build a town and train units into it |
| 3.3 Buildings fall, victory, orientation | CMB-03, MATCH-01, UI-11, UI-12, MATCH-05 | burn a town down, win, pause, use the minimap |
| 3.4 The opponent | AI-01, AI-17, QA-11, PERF-06 | play a full match against the bot |

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M3 cards passes.
2. L1: gather, carry and walk timing; builder progress (n^0.7); production, population and
   treasury conservation over 200 seeds; the bot's decisions.
3. L3 integration: a worker gathers and delivers wood and berries; builders finish a house that
   equals its blueprint; trained units spawn by the door and follow a rally point; ten spearmen
   burn down a house; a field yields and is reseeded.
4. QA-11 (L1 over ten seeds, with an L3 sample of two): each bot-versus-bot match ends with a
   winner within 30 game minutes, with no exception and no stall; no AI training queue waits for
   food longer than 60 s after minute 5; every report carries the telemetry.
5. L6 (PERF-06): 2 × 100 units, 20 buildings and three constructions within A–E, G when an
   autosave falls in the window, and per-unit costs within 15 % of M1's baseline; the 2× stress
   pass holds C p95 ≤ 50 ms.
6. L5: 600 ticks of AI versus AI on the proving ground on a dedicated server.
7. Consent batch: the M3 goldens, PERF-14 re-run and S4's armour and held-item pass (ART-01).

**Acceptance** (owner; decision gate 3): the owner plays one full match against the bot and
scores the questionnaire; "would I play another?" goes into STATE.md with its reason. A "no", or
any score of 2 or less, sends pacing and readability back for tuning before M4 adds content.
Optional owner task: owner-built blueprints replace the generated placeholders (ART-01).

**Retires.** F-R8 (a full match to play); F-R6 starts (the bot plays headless); F-R17 starts
(balance sheet, L1 matches); F-R12 for construction (the writer budget at scale).

**Size and critical path.** 27 cards (7 S, 18 M, 2 L), at most 81 card-days. Critical path: DATA-04
→ UNIT-04 → ECO-05 → ECO-09 → AI-17 → QA-11 (23 card-days).

---

## Decisions this milestone needs

Interlocked decisions are presented together: the identity bundle (D-25 with D-02, and the
garrison answer to raids, BLD-16) and the theme bundle (D-03, D-04, D-05, D-10).

### D-25 · The MVP's identity

**Question.** The catalog says territory, capture, fog and an AI make a game feel like 0 A.D.
rather than a generic RTS; the MVP has the AI, and fog only with D-24 (c). Which identity does the
MVP take?

| Option | For | Against | Card delta |
|---|---|---|---|
| (a) An AoE-leaning MVP, with 0 A.D.'s visible signatures from M9 (recommended, with garrison and the bell in M5 and solo fog per D-24) | Smallest credible MVP; garrison and fog give the feel both games share | Plays like a slim AoE II until M9 | none beyond BLD-16 (M5) and D-24 (c) |
| (b) Citizen-soldiers through data (D-02's alternative) | 0 A.D.'s sharpest tension between economy and army | A harder AI economy; harder to read and balance | data in UNIT-04; AI-02 and AI-04 grow (≈ +3 days) |
| (c) Territory-lite in M6: TERR-01 and TERR-02, borders on the minimap only | 0 A.D.'s expansion decision in the MVP | A large system pulled forward | TERR-01 (L) and TERR-02 (M) into M6 (≈ +13 days) |
| (d) Capture in M6: CMB-10 without decay or team rules | 0 A.D.'s answer to buildings | A T1 system pulled forward | CMB-10 (L) into M6 (≈ +10 days) |

**Recommendation.** (a). The design pillars (ROADMAP.md) rank later cards against this choice.

**Needed by:** M3. **Affects:** UNIT-04, BLD-16, D-24, TERR-01, TERR-02, CMB-10.

**Decided (2026-10-21):** as recommended, by the owner.

### D-02 · Worker model

**Question.** Who gathers and builds: 0 A.D.-style citizen-soldiers (soldiers gather and build at
lower rates beside civilians) or AoE-style villagers (only workers gather; soldiers fight)?

| Option | For | Against | Card delta |
|---|---|---|---|
| Villagers, AoE-style (recommended) | Clear roles; a simpler AI economy; easier balance; familiar | Less 0 A.D.; idle armies between fights; a raided player can only watch workers flee, hence garrison and the bell (BLD-16, M5) | none |
| Citizen-soldiers, 0 A.D.-style | 0 A.D.'s identity; workers can defend | The AI juggles soldiers between work and war; the population cap bounds units either way, so no performance gain | data in UNIT-04; AI-02 and AI-04 grow |
| Per civilisation, in data | Both styles as an asymmetry | Both AI behaviours eventually | CIV-01 (M10) |

**Recommendation.** Villagers for the MVP. Gathering and building are class-token abilities in data
(UNIT-04), so citizen-soldiers can arrive as a civilisation trait in M10 (CIV-01) without a
rewrite.

**Needed by:** M3 (UNIT-04). **Affects:** UNIT-04, ECO-05, AI-01, AI-02, CIV-01, BLD-16.

**Decided (2026-10-21):** as recommended, by the owner.

### Theme bundle: D-03, D-04, D-05, D-10

Decided together they stay consistent; decided apart they can give, for example, an antiquity
theme with vanilla-only armour. D-04 and D-10 are needed by M3; D-05 by M6; D-03 by M7 (ART-03) at
the latest, and the MVP's placeholders use a neutral palette until it is decided.

**D-04 · Art direction**

| Option | For | Against | Card delta |
|---|---|---|---|
| Vanilla-faithful | Vanilla humanoid and horse models, armour, held items; resource-pack friendly; Sodium's fast path; cheapest for one person | Limited silhouettes; siege and deposits have no vanilla model | – |
| Stylised custom models | Distinct look | Art capacity the project doesn't have; GeckoLib only after a benchmark | + art cards |
| Vanilla-faithful, custom models only where needed (recommended) | Readable and cheap; custom work limited to siege, deposits and a few weapons | Needs a style guide | ART-01's style guide |
| Workers in the vanilla villager look | Instantly readable; `VillagerModel` is generic over `Entity`, and `VillagerProfessionLayer` accepts any `LivingEntity` that implements `VillagerDataHolder`, so outfits can show the current task | Closer to Reign of Nether's villager look; two model styles side by side | a villager-look renderer variant (S) |

Recommendation: the third option, with ART-01's rules: 16 × 16 texel density and the vanilla
palette; faction colour through dyed leather, banners and the outline; role readable from the held
item and shield at 60 blocks; vanilla render types only; every asset original; footprint, height
and block-count ranges per building class.

**D-10 · Resource set and names**

| Option | For | Against |
|---|---|---|
| Food, wood, stone, metal (0 A.D.) | Generic | "Metal" means little in Minecraft |
| Food, wood, stone, gold (AoE) | Familiar to AoE players | Gold is a weak, decorative material in Minecraft |
| Food, wood, stone, iron (recommended) | Iron is what Minecraft players make weapons and armour from | Departs from both inspirations |

The list is data (ECO-01), so a fifth resource can come later. No card delta.

**D-05 · Phase names**

| Option | For | Against |
|---|---|---|
| Village → Town → City (recommended) | Culture-neutral; describes the settlement growing | Less dramatic |
| Dark → Feudal → Castle → Imperial | Familiar to AoE players | Medieval-specific; four ages; strong AoE association |
| Wood → Stone → Iron → Diamond ages | Instantly readable in Minecraft | Odd for a civilisation; suggests tool tiers, not societies |
| Per civilisation | Flavour | Harder to read across civilisations |

Recommendation: Village → Town → City, with optional display names per civilisation; Minecraft's
material tiers go to equipment technologies (stone → iron weapons, leather → chainmail), which
TECH-03 shows on the units. No card delta.

**D-03 · Civilisation themes and the first two civilisations**

| Option | For | Against | Art cost: MVP / by M10 |
|---|---|---|---|
| Historical antiquity (recommended if the owner prefers it): Romans and Gauls | Matches the 0 A.D. core; rare in Minecraft; public-domain history | Custom armour textures and weapon models (javelins, slings) for both peoples (risk F-R18) | one custom armour and weapon set / two sets |
| Historical medieval: Franks and Norse | Vanilla armour, shields, bows, crossbows and stone castles fit almost free | Closer to existing AoE-like mods; strong AoE associations | almost none / almost none |
| Minecraft-native factions | Vanilla models and lore | Reign of Nether owns this look; phases and civic centres fit less | none / none |
| Biome cultures | Vanilla's five village styles (plains, desert, savanna, snowy, taiga) give architecture palettes, its seven villager biome types give outfits, and biome-matched maps follow, with no custom armour | Less evocative; more writing; no history to lean on | lowest / lowest |

Recommendation: antiquity if the owner prefers its identity, knowing it carries the largest asset
bill; otherwise biome cultures as the cheapest distinctive choice. The MVP ships one civilisation
on both sides (a mirror match) in placeholder art (ART-01, ART-02); ART-03 follows the MVP verdict.

**Affects:** ART-01 to ART-05, UNIT-06, CIV-01, CIV-02, TECH-02, TECH-03, UI-13, ECO-01.

**Decided (2026-10-21):** D-04, D-10 and D-05 as recommended, by the owner; D-03 stays open until M7.

### D-11 · Pacing and scale

**Question.** How long a match lasts and how fast things happen at Minecraft's scale.

| Option | For | Against |
|---|---|---|
| 35–45 minutes | Room for a long economy game | Long for Minecraft sessions |
| Median 20–35 minutes, every match within 45 (recommended) | Fits play sessions and the playtest loop | Less room for a long boom |
| Slower | Builders' delight | Waiting kills the fun |

**Recommendation.** Median 20–35 minutes for a 1 v 1 on the MVP map and every match within 45
(M6); M3's crude loop ends within 30. Template values are Strategos's own numbers in game units
(ADR-0005): infantry walks about 4.3 blocks per second, a walking player's speed; engagement ranges
fit the camera's view at the default height; train, build and research times are set for the
target length. DATA-04 records a provenance note per value and timing targets for the Normal
opening (first house, barracks, first attack, Town); playtests tune all of it.

**Needed by:** M3 (DATA-04). **Affects:** DATA-04, every template, QA-11, QA-12.

**Decided (2026-10-21):** as recommended, by the owner.

### D-12 · How buildings fall in the MVP

**Question.** Soldiers barely scratch buildings with realistic armour values, and the MVP has no
capture. How does a player destroy a town?

| Option | For | Against | Card delta |
|---|---|---|---|
| Soldiers damage buildings at tuned rates | Smallest scope | Buildings feel flimsy; no role for siege | CMB-03 only |
| Torches at tuned rates, plus a ram in Town (recommended) | Readable, Minecraft-native visuals with no real fire; the ram makes Town a decisive unlock; wood burns faster than stone | One more unit and the arsenal | CMB-03 (M3), CMB-09 (M, M6) |
| Capture in the MVP | 0 A.D.'s own answer | A T1 system pulled forward; no destruction | CMB-10 (L) into M3 |
| A ram only | Siege matters | No answer to buildings before Town | CMB-09 into M3 |
| Field-built rams (AoE IV): infantry assemble one through an ability | No arsenal needed | Needs abilities (UNIT-10, M10) | after M10 |
| A TNT sapper: its blast resolved in the core as crush damage, explosion particles as `fx`, never a real `PrimedTnt` | Minecraft-native and dramatic | A new unit with special rules | + a unit card (M) |

**Recommendation.** Torches plus the ram at the arsenal in Town; capture follows in M9; field rams
and the sapper stay options for later. If MVP scope must shrink: torches only, with the defence
tower (BLD-17) as the Town unlock.

**Needed by:** M3 (CMB-03). **Affects:** CMB-03, CMB-09, BLD-17, TECH-02, BLD-05.

**Decided (2026-10-21):** as recommended, by the owner.

### D-16 · Minimap in the MVP

| Option | For | Against | Card delta |
|---|---|---|---|
| None | Smaller scope | Navigating 384² with only the camera is painful | – |
| Basic in M3, full in M6 (recommended) | Orientation for the M3 verdict match | Two cards | UI-11 (M, M3), UI-20 (M, M6) |
| Full in M6 only | One card | Too late for the M3 gate | UI-11 (L, M6) |

**Recommendation.** Basic in M3 (terrain, dots, camera frame, click to jump), full in M6 (orders,
pings, live construction); fog on the minimap with UI-16. **Needed by:** M3. **Affects:** UI-11,
UI-20, UI-16.

**Decided (2026-10-21):** as recommended, by the owner.

### D-18 · Pause and game speed

| Option | For | Against |
|---|---|---|
| No pause | Same as multiplayer | Minecraft Legends was criticised for exactly this |
| Pause only | Breathing room | – |
| Pause and speeds (recommended) | Learning and relaxed play | 2× halves the time each tick has |

**Recommendation.** Pause plus 0.5×, 1× and 1.5× in singleplayer; 2× only if the budgets hold at 40
ticks per second; no pause in multiplayer at first, a vote pause later (the per-match `PAUSED`
state serves it). **Needed by:** M3 (MATCH-05). **Affects:** MATCH-05, PERF-09.

**Decided (2026-10-21):** as recommended, by the owner.

---

## Cards

### Slice 3.1 · Economy

#### DATA-04 · Balance sheet: relationships, provenance and timing targets
- **Value:** Every number of the game sits in one reviewed table, authored for Minecraft's scale,
  so the owner sees what a change does before playing it (P4).
- **In scope:** template base values are Strategos's own numbers in game units (blocks, seconds,
  resource units), authored from relationships in the behavioural notes (for example "cavalry
  about 1.5× infantry speed"), never copied from or scaled off 0 A.D. or AoE values (ADR-0005);
  converting seconds to ticks is the only scale step; relationships between templates computed by
  the sheet; a template may override a derived value only with an explicit override marker and a
  reason, and the sheet lists every override; a provenance note per value; starting timing targets
  for the Normal opening (first house, barracks, first attack, Town); a resource-sink check; the
  sheet generated from data as Markdown and CSV, its diffs approved like goldens.
- **Out of scope:** tuning, which runs in every milestone.
- **Acceptance:**
  1. Changing a relationship changes every derived value consistently (L1).
  2. The sheet regenerates from data, and its diff shows every changed number (L1).
  3. An override without a marker and a reason fails the build, and every override is listed (L1).
  4. Every value has a provenance note (L1).
  5. Every resource gatherable in a milestone's content appears in the cost of at least two items
     available in that milestone (L1).
  6. The sheet carries the Normal opening's timing targets (D).
- **Tests:** L0, L1, D · **Fixtures:** the committed balance sheet · **Depends on:** DATA-02,
  D-11 · **Size:** M · **Tier:** T0
- **Status:** specified

#### ECO-01 · Resources and treasury
- **Value:** A clear economy in the AoE and 0 A.D. style: resources in a treasury, spent on units,
  buildings and research, never items in chests (P1).
- **In scope:** architecture §11.1: resource types in data (D-10 recommends food, wood, stone and
  iron); a fixed-point treasury per faction with income, spending and refund counters; costs,
  payment and refunds, and the missing amounts when something can't be paid; the invariant
  starting + gathered + refunded = stock + spent, carried loads and node remainders included,
  checked after every tick of every harness run and GameTest; `faction_state` to its own faction
  only (allies later).
- **Out of scope:** trade and tribute (ECO-13, MATCH-11); loot (CMB-13).
- **Acceptance:**
  1. The invariant holds over 200 seeded command streams (L1).
  2. A command that can't be paid is rejected with the missing amounts (L1).
  3. A mock enemy player never receives another faction's treasury, while the owning faction's
     mock player does (L3).
  4. `faction_state` round-trips within its cap and declares its audience (L2).
- **Tests:** L0, L1, L2, L3 · **Depends on:** CORE-03, NET-01, D-10 · **Size:** M · **Tier:** T0
- **Status:** specified

#### ECO-02 · Resource nodes: discovery and index
- **Value:** Workers find the nearest tree, bush or deposit instantly, and blocks from other mods
  can become resources with no code.
- **In scope:** architecture §11.3: discovery at prepare by a deterministic flood fill over a
  material channel from the `strategos:resource_yield` data map, whose defaults name
  `#minecraft:logs`, `#minecraft:leaves`, `#minecraft:base_stone_overworld` and `#c:ores` (map
  manifests may override or add nodes); the data map's registration and inventory entry; a
  per-match node index by type and page; work slots per node (8 per tree to start); queries for the
  nearest available, reachable node.
- **Out of scope:** the node types themselves (ECO-03, ECO-05, ECO-07 to ECO-09).
- **Acceptance:**
  1. Discovery on synthetic slices equals a reference implementation (L1).
  2. Nearest-node queries equal brute force on random maps (L1).
  3. Slot claims never exceed capacity with many workers competing, over seeded runs (L1).
  4. A `resource_yield` entry for a test block makes it a node with no code (L3).
  5. Indexing the proving ground takes under 2 s during prepare (L6).
- **Tests:** L0, L1, L2, L3, L6 · **Depends on:** CORE-08, PATH-04, MAP-01 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### ECO-03 · Woodcutting on real trees, with natural canopy thinning
- **Value:** Workers chop real Minecraft trees log by log, the forest visibly recedes, and leaves
  never float over a stump (P1).
- **In scope:** trees as log structures with their leaves, 2 × 2 trees included; the amount per
  tree from its logs through `resource_yield`; work slots at the trunk; logs removed top-down per
  gathered quota through the block writer; leaves that are no longer within six leaf steps of a
  remaining log removed under the writer budget, which is vanilla's own decay rule
  (`LeavesBlock.DECAY_DISTANCE` = 7), computed by the core because the writer's flags suppress
  vanilla's distance updates; leaves still in reach of a live neighbouring tree stay; the core
  updates its own surface data when it removes blocks, and `OWN_WRITE` edits are never
  reclassified; a short ADR that supersedes ADR-0021's leaf rule (leaves only at exhaustion).
- **Out of scope:** replanting and regrowth.
- **Acceptance:**
  1. One worker gathers exactly the balance-sheet amount in the balance-sheet time (L1).
  2. Logs leave top-down on tree fixtures of every shipped species, 2 × 2 trees included (L1).
  3. Leaves within reach of a live neighbouring tree stay (L1).
  4. A 2 × 2 tree accepts its full slot count (L1).
  5. At every stage of felling, with `randomTickSpeed` 0, no leaf that vanilla would decay remains
     for more than 5 s (L3).
  6. A worker fells a tree in a GameTest, and units then walk through the clearing with no page
     rebuild (L3).
- **Tests:** L0, L1, L3, L5 · **Fixtures:** tree fixtures per species, 2 × 2 trees and touching
  canopies · **Depends on:** ECO-02, BLD-15, PATH-15 · **Size:** L · **Tier:** T0
- **Status:** specified

#### ECO-05 · Gathering, dropsites and berry bushes
- **Value:** Workers gather, carry and deliver on their own, pick the next node when one runs out,
  and show what they are doing (P1, P2).
- **In scope:** architecture §11.4: GATHER states (approach, gather, return, find next); carry
  capacity as a stat; rates per class and resource subtype through the modifier pipeline, with
  fixed-point accumulators; delivery to the nearest own dropsite that accepts the resource;
  retargeting to the nearest node of the same subtype within 24 blocks, else idle with a notice;
  right-click on a node gathers; sweet berry bushes as forage nodes with an amount and a regrowth
  rule, shown by the block's age with no random ticks; working workers hold their task tool (axe,
  pickaxe, hoe, and a building tool chosen in ART-01's style guide, since vanilla 1.21.1 has no
  hammer) and show their load as a held item (log, berries, cobblestone, raw iron) from the
  carried-resource byte; external edits shrink a node and credit nothing.
- **Out of scope:** stone, deposits and fields (ECO-07, ECO-08, ECO-09).
- **Acceptance:**
  1. Gather timing (rate, carry, walk) is exact for the formula over seeded runs (L1).
  2. A worker whose node runs out takes the nearest same-subtype node within 24 blocks, or goes
     idle with a notice (L1).
  3. Workers deliver to the nearest dropsite that accepts the resource (L1).
  4. 30 workers on one forest never exceed any node's slots (L1).
  5. Turning soldier gathering on in data makes a spearman gather at its own rate (L1).
  6. Bush yield and regrowth are exact (L1).
  7. With random ticks enabled in the test level, a match bush changes only through the core,
     while a control bush outside the match grows (L3).
  8. A worker gathers wood and berries and delivers them, and the bush state survives save and
     load (L3).
  9. A golden shows a worker's tool and load at 60 blocks (L4, consent).
  10. The owner confirms that tool and load read at that distance (L7, acceptance).
- **Tests:** L0, L1, L3, L4 (consent), L5, L7 · **Depends on:** ECO-01, ECO-02, UNIT-04, BLD-01 ·
  **Size:** L · **Tier:** T0
- **Status:** specified

#### UNIT-04 · Starting units and the worker model
- **Value:** Workers gather and build, spearmen fight, and who else may gather follows the worker
  model the owner picks (P4).
- **In scope:** the worker template (gathers, builds, repairs; flees by default) and the spearman
  (melee, hack damage); gathering and building as class-token abilities in data, so D-02 is a data
  choice.
- **Out of scope:** archers, horsemen and counters (UNIT-07).
- **Acceptance:**
  1. Who can gather and build follows class tokens in data (L1).
  2. Both templates appear in the balance sheet (L1).
  3. A spawned worker and spearman carry their template's stats, read through the modifier
     pipeline (L3).
- **Tests:** L0, L1, L3 · **Depends on:** UNIT-02, DATA-04, D-02 · **Size:** S · **Tier:** T0
- **Status:** specified

#### BLD-01 · Building records and blueprints
- **Value:** Buildings are real structures with roles (dropsite, housing, training) that every
  player can see and select.
- **In scope:** architecture §11.7: `BlueprintLoader` parsing vanilla `.nbt` structures into an
  adapter palette and a core `BlueprintShape` (size, footprint mask, build order, damage ranks, and
  markers from data-mode structure blocks: spawn, door, rally, work), because `StructureTemplate`'s
  palettes are private; blueprints reloaded with the templates; the building as a core entity
  (owner, template, footprint, progress, health; dropsite, population and trainer components);
  instant placement for scenarios; selecting buildings; the footprint as an obstruction in the
  core overlay; collapse and removal at 0 HP; test blueprints generated in code (devtools), so
  construction tests never wait for art; a blueprint lint: no flammable block in any palette,
  required markers present, damage ranks respecting supports, the two lowest outer-ring layers
  never ranked; `building_state` to the owner in full.
- **Out of scope:** construction (BLD-03); damage stages (BLD-05).
- **Acceptance:**
  1. A blueprint round-trips: parsed, placed and read back, it equals the source (L2).
  2. The lint rejects a flammable block, a missing marker and a ranked bottom-ring block (L2).
  3. A pre-placed civic centre accepts every resource and grants population (L3).
  4. Units path around a footprint with no page rebuild (L3).
  5. A mock enemy player receives none of `building_state`'s owner-only fields, while the owner
     does (L3).
- **Tests:** L0, L1, L2, L3, L5 · **Fixtures:** generated test blueprints · **Depends on:**
  CORE-06, DATA-03, PATH-15 · **Size:** M · **Tier:** T0
- **Status:** specified

#### AUD-04 · World-edit feedback: cracks, particles and sounds
- **Value:** Chopping, mining and the block-by-block rise of buildings, the spectacle this mod is
  built around, look and sound like Minecraft (P1).
- **In scope:** `fx` event kinds sent through the fog-aware sender to the `SeesCell` audience,
  because the block writer is silent by design (flags 2 and 16, no `destroyBlock`): the block a
  worker is working shows the vanilla crack overlay (client `ClientLevel#destroyBlockProgress`),
  updated only on stage changes; removed logs and stone play their break particles
  (`ClientLevel#addDestroyBlockEffect`) and their `SoundType` break sound; every block a building
  gains plays its place sound; volume and rate limits so a 64-placement tick stays cheap.
- **Out of scope:** music (AUD-03).
- **Acceptance:**
  1. `fx` reach only viewers whose faction sees the cell, with a positive control (L3).
  2. The `fx` batch of a 64-placement tick stays within its byte cap (L1).
  3. A golden shows a log at a crack stage (L4, consent).
  4. The owner's checklist for chopping, mining and building passes (L7, acceptance).
- **Tests:** L0, L1, L3, L4 (consent), L7 · **Depends on:** ECO-03, BLD-15, NET-01 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

### Slice 3.2 · Building and training

#### BLD-02 · Placement mode and validators
- **Value:** See exactly where a building will stand and why a spot is refused, before paying
  (P3).
- **In scope:** architecture §11.6: a ghost from the template's `PREVIEW` kind, snapped to the grid
  and rotated in 90° steps; the same `PlacementRules` code on the client (prediction against
  loaded chunks with the same `BlockClassTable`) and on the server (authoritative, against the
  surface grid and overlays): inside the match space; every footprint column on land; a
  floor-height spread of at most 2 blocks (flat sites only until BLD-04 levels them); no overlap
  with footprints, nodes or reserved cells; explored by the faction; civic centres at least 64
  blocks apart; within reach of an own civic centre, the stand-in for territory until TERR-02;
  affordable; per-cell colours with the failing reason.
- **Out of scope:** uneven ground (BLD-04); the full build menu (UI-09).
- **Acceptance:**
  1. Validators pass table-driven cases, the numeric limits included (L1).
  2. Client and server give the same verdict for equal inputs (L1).
  3. A placement the client accepts but the server rejects is refused with a reason (L3).
  4. Goldens for valid and invalid previews (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** BLD-01, CAM-04 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### BLD-03 · Progressive construction
- **Value:** Buildings rise block by block, and more builders finish faster, with diminishing
  returns (P1).
- **In scope:** architecture §11.7: the foundation paid at placement; when the first builder
  arrives, the footprint becomes an obstruction in the core overlay, units inside move to the
  nearest free surface, and the writer clears the footprint and fills columns down to the ground;
  builder slots around the footprint; total progress `rate × n^0.7` in fixed point; the core asks
  for `floor(p × blockCount)` blocks, written through BLD-15 in the blueprint's build order; HP
  rising with progress; progress saved and resumed; no reclassification, because the core updates
  its own overlay.
- **Out of scope:** uneven ground (BLD-04); repair (BLD-06).
- **Acceptance:**
  1. With 1, 2, 5 and 10 builders the build time is exactly `base ÷ n^0.7` in ticks (L1).
  2. A finished building equals its blueprint block for block (L3).
  3. No unit is left inside a committed footprint (L3).
  4. Saving mid-build and loading resumes at the same block (L3).
  5. One seeded match with writer budgets of 64 and of 8 ends with identical core hashes (L3).
  6. With three constructions at once, the writer never exceeds 64 placements per tick, and each
     site flushes at most every 4 ticks (L3).
- **Tests:** L0, L1, L3, L5 · **Depends on:** BLD-02, UNIT-04, BLD-15 · **Size:** M · **Tier:** T0
- **Status:** specified

#### ECO-06 · Population and housing
- **Value:** Houses raise the population cap, and running out of room is obvious (P4).
- **In scope:** architecture §11.5: population cost per unit (siege 3, every other unit 1);
  `cap = min(Σ housing of finished buildings + civilisation base, the match maximum of D-01)`;
  housing from the civic centre and houses; population reserved when an entry starts; aggregates
  kept as counters, never scans.
- **Out of scope:** housing technologies (TECH-05).
- **Acceptance:**
  1. Population invariants hold across seeded training and deaths (L1).
  2. Training waits at the cap and resumes when a house completes (L1).
  3. The cap never exceeds the match maximum (L1).
  4. A GameTest shows a queue waiting with "housing needed" (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-01, BLD-01, D-01 · **Size:** S · **Tier:** T0
- **Status:** specified

#### ECO-09 · Fields with manual reseed
- **Value:** Farms are the steady food source that lets a match last: up to five farmers, crops
  that visibly grow, fields to reseed when they run out (P1).
- **In scope:** architecture §11.2: the field as a building with a finite `FieldNode` (the owner's
  decision of 2026-10-20); 5 work slots; diminishing returns of 0.9 per extra farmer as the
  starting value; the crop age as the amount indicator, driven only by the core; manual reseed for
  a wood cost (auto-reseed is ECO-17, M4); the civic centre as the food dropsite until the
  farmstead (BLD-08).
- **Out of scope:** auto-reseed (ECO-17); corrals (ECO-12).
- **Acceptance:**
  1. Total yield for n farmers follows the diminishing-returns formula exactly (L1).
  2. An exhausted field stays empty until reseeded, and reseeding charges its cost (L1).
  3. With `randomTickSpeed` 3 and a first-person mock player beside the field, crop age changes
     only through the core, while a control crop outside the match grows (L3).
  4. A field yields and is reseeded in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-05, BLD-01 · **Size:** M · **Tier:** T0
- **Status:** specified

#### PROD-01 · Production queue
- **Value:** Buildings train units in a queue you can manage.
- **In scope:** architecture §11.5: one queue of at most 5 entries per building; the cost paid when
  queued and refunded on cancel; population reserved when an entry starts; progress in ticks;
  units spawned at the blueprint's spawn marker on a free surface, their bodies created by the
  materialiser.
- **Out of scope:** batches (PROD-02); rally points (PROD-03).
- **Acceptance:**
  1. No resource is lost or duplicated under seeded add, cancel and complete streams (L1).
  2. A sixth entry is refused (L1).
  3. A full population cap pauses the head of the queue without losing progress (L1).
  4. A unit spawns at the door on a standable surface, never inside a wall (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-01, ECO-06 · **Size:** M · **Tier:** T0
- **Status:** specified

#### PROD-03 · Rally points
- **Value:** New units go straight where they are needed: to a spot, to work, or to a building
  (P3).
- **In scope:** architecture §7.4: a rally point as a list of queued orders given to each new unit;
  on the ground, move; on a resource node, gather on arrival; on a building, walk to it (garrison
  later); the marker shown for the selected building; saved with the match.
- **Out of scope:** rally to garrison (BLD-10).
- **Acceptance:**
  1. Workers rallied to a tree start gathering on arrival (L1).
  2. The rally point survives save and load (L3).
  3. The marker shows for the selected building (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** PROD-01, ECO-05 · **Size:** S ·
  **Tier:** T0
- **Status:** specified

#### UI-06 · Selection panel and command card
- **Value:** See what you selected and act on it: train, build and stop buttons with costs.
- **In scope:** a selection panel with icons and counts by type, and the details of one unit or
  building (health, carried resource, queue) from `unit_detail` and `building_state`; a command card
  of context buttons built from view-models (train, build, stop) with costs; production progress;
  the build page opens placement mode.
- **Out of scope:** the hotkey grid, disabled reasons and breakdown tooltips (UI-08); the build
  menu (UI-09).
- **Acceptance:**
  1. Panel and card layouts pass at every resolution and GUI scale (L1).
  2. One click on "train" sends exactly one command, and the view-model equals the client model
     (L1).
  3. The queue a mock player receives equals the server's (L3).
  4. Goldens for a worker, a civic centre and a barracks selection (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** UI-03, PROD-01, BLD-02 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### UI-07 · Resource bar, population and workers per resource
- **Value:** Know your economy at a glance, the way modern AoE shows it (P2).
- **In scope:** a resource bar with amounts and income per minute; population used and cap, with a
  warning when the cap blocks training; workers per resource and the idle count, from counters in
  `faction_state`.
- **Out of scope:** the idle-worker key and notices (UI-10).
- **Acceptance:**
  1. Income per minute matches the gathered amounts over a seeded minute (L1).
  2. The view-model equals the client model after every `faction_state` (L1).
  3. The `faction_state` a mock player receives equals the server's state, worker counts included
     (L3).
  4. Goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** ECO-01, ECO-06, UI-02 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### UI-10 · Idle-worker key and economy notifications
- **Value:** Never lose track of an idle worker on a big map, or wonder why training stopped (P3).
- **In scope:** an idle-worker button and key that cycle through idle workers and jump the camera
  through `camera_jump`; notifications for "not enough resources", "population capped" and
  "deposit exhausted", once per cause.
- **Out of scope:** the general notification feed (UI-21).
- **Acceptance:**
  1. The key cycles through every idle worker in stable order (L1).
  2. Each notification fires once per cause, not once per tick (L1).
  3. Goldens (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** UI-07, ECO-05 · **Size:** S · **Tier:** T0
- **Status:** specified

#### ART-01 · Placeholder look and the style guide
- **Value:** Units and buildings look like Minecraft and show owner and role at a glance, so the
  first playtest judges the game, not the placeholders (P2).
- **In scope:** per D-04: the vanilla humanoid with armour and held-item layers; faction colour
  through dyed leather, banners on shields and the outline; role through tools and weapons; a
  one-page style guide (16 × 16 texel density, the vanilla palette, colour and role rules, vanilla
  render types only, every asset original) that also fixes footprint, height and block-count ranges
  per building class, so buildings read at camera heights of 24–64 blocks and rise at a steady,
  visible rate; three placeholder blueprints (civic centre, house, barracks) of non-flammable
  blocks, generated in code. Owner-built blueprints may replace them later (owner task). S4's
  armour and held-item pass, moved from PERF-04 (no equipment layers exist before this card).
- **Out of scope:** the civilisation art pass (ART-03).
- **Acceptance:**
  1. The placeholder blueprints pass the lint and the construction GameTests (L3).
  2. The style guide exists with the per-class ranges (D).
  3. Worker and spearman goldens at 60 blocks, with and without Sodium (L4, consent).
  4. The owner approves the placeholder look and confirms role and colour read at 60 blocks (L7,
     acceptance).
  5. With armour and held-item layers, 200 humanoids drawn by the shipping renderer cost ≤ 6 ms
     with Sodium and client tick stays ≤ 1 ms, or the report names the level of detail needed;
     the numbers feed D-01 and ADR-0019's budget F (L6 client, consent; M3's consent batch).
- **Tests:** L3, L4 (consent), L6 (client, consent), L7, D · **Depends on:** UNIT-03, BLD-01,
  D-04 · **Size:** M · **Tier:** T0
- **Status:** specified

### Slice 3.3 · Buildings fall, victory and orientation

#### CMB-03 · Attacking buildings with torches
- **Value:** Soldiers can burn down a town, slowly; wood burns faster than stone, and stone waits
  for the ram (P4).
- **In scope:** the structure attack of D-12: infantry switch to a torch against buildings and deal
  fire damage at tuned rates; building resistances by blueprint material class (wood burns faster
  than stone); flame effects as `fx` on hit blocks, never real fire blocks; targeting rules and
  attack slots for buildings; attack orders on buildings through ORD-04's context resolver.
- **Out of scope:** siege (CMB-09); damage stages with falling blocks (BLD-05).
- **Acceptance:**
  1. Ten spearmen burn down a house in exactly the balance-sheet time (L1).
  2. Torches destroy a wooden house at least twice as fast per HP as a stone building (L1).
  3. Units switch to torches only against buildings (L1).
  4. With fire tick enabled in the test level, no fire block appears and no fire spreads during a
     burn, while a control fire outside the match spreads (L3).
  5. A GameTest burn ends with the building removed and its footprint cleared (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-02, BLD-01, D-12 · **Size:** M · **Tier:** T0
- **Status:** specified

#### MATCH-01 · Scenarios, match rules and a first victory
- **Value:** A match starts from a ready-made setup, plays under fair rules and ends with a clear
  victory or defeat.
- **In scope:** scenario definitions in data (map, players, factions, starting entities, victory
  rule); `MatchRules` (population cap, starting resources, victory, `revealMap`, AI vision) copied
  in at creation and changed only through journaled `RuleChange` inputs (`/strategos match
  config`); world gamerules are never changed here (MAP-07 sets them once in arena worlds; other
  worlds rely on the protection events); `MobSpawnEvent.PositionCheck` denied inside match regions;
  the first victory rule: a faction whose civic centre is destroyed loses; `match_event` (alerts,
  end of match); an end screen with winner and duration; resign through `match_control`.
- **Out of scope:** conquest and statistics (MATCH-03); setup screens (MATCH-02).
- **Acceptance:**
  1. A scenario loads into a match with exactly the listed entities (L3).
  2. Destroying the last civic centre ends the match within 20 ticks (L1).
  3. A `RuleChange` is journaled and replays to the same hash (L1).
  4. With mob spawning enabled in the test level, no hostile mob spawns inside the match region,
     while a control outside it does (L3).
  5. `match_event` brings winner and duration to every participant (L3).
  6. The end screen shows them (L4, consent).
- **Tests:** L0, L1, L2, L3, L4 (consent) · **Depends on:** CORE-01, DATA-03, MAP-01 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### UI-11 · Basic minimap
- **Value:** Find the enemy base and see an attack coming on a 384-block map (P2, P3).
- **In scope:** per D-16: a terrain image from `BlockState#getMapColor` with height shading, one
  `NativeImage` tile per chunk uploaded to a `DynamicTexture` and refreshed at most once a second
  per changed chunk; unit and building dots in faction colours (own units, and enemies known from
  `unit_summary`); the camera frame; a click jumps the camera through `camera_jump`. Right-click
  orders, pings and the cost gate are UI-20's (M6).
- **Out of scope:** fog on the minimap (UI-16).
- **Acceptance:**
  1. The minimap pixel array of the proving ground equals its stored hash (L1).
  2. The minimap-to-world mapping is exact (L1).
  3. A click sends `camera_jump`, and the server teleports within one tick of receipt (L3).
  4. A golden confirms the drawing (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** UI-02, CAM-03, NET-08, D-16 · **Size:** M ·
  **Tier:** T1 (pulled into the MVP by D-16)
- **Status:** specified

#### UI-12 · Under-attack alert with jump key
- **Value:** You notice an attack in time, wherever the camera is (P3, P5).
- **In scope:** an under-attack alert from `match_event`, raised once per sustained attack with a
  sound (local, from `fx`) and a jump key that sends `camera_jump` to the latest alert. The
  prioritised feed with research and phase notices is UI-21's (M6).
- **Out of scope:** multiplayer pings (UI-15).
- **Acceptance:**
  1. A sustained attack raises one alert, not one per hit (L1).
  2. The jump key sends `camera_jump` to the alert, and the server teleports (L3).
  3. A golden of the alert (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** UI-10, MATCH-01 · **Size:** S ·
  **Tier:** T0
- **Status:** specified

#### MATCH-05 · Pause and game speed
- **Value:** Pause a solo game to think, and play slower or faster.
- **In scope:** per D-18 and architecture §5.3: pause is the match's `PAUSED` state, in which the
  core is not stepped and the inbox keeps buffering, so orders given while paused apply on resume;
  camera, selection and UI keep working; game speed through `ServerTickRateManager#setTickRate` in
  singleplayer (0.5×, 1×, 1.5×); never tick freeze, which is server-wide; pause and resume through
  `match_control`.
- **Out of scope:** pause in multiplayer (a vote pause later).
- **Acceptance:**
  1. A paused and resumed run reaches the same state hash after N match ticks as an unpaused run
     (L1).
  2. Orders given during pause apply at the first tick after resume (L1).
  3. Pausing one match leaves a second match in the same level stepping (L3).
  4. C p95 stays ≤ 23 ms ÷ the fastest offered speed at the milestone's scale (L6); PERF-09
     re-asserts it at MVP scale.
- **Tests:** L0, L1, L2, L3, L6 · **Depends on:** CORE-01, CORE-03, D-18 · **Size:** S ·
  **Tier:** T0
- **Status:** specified

### Slice 3.4 · The opponent

#### AI-01 · AI player framework
- **Value:** The opponent is a player like any other: it reads only what it may know and acts only
  through commands (P5).
- **In scope:** architecture §14: `ComputerPlayer` inside the core step (phase P2, every 20 ticks,
  offset by slot, with early thinks on events); `AiView`, a read-only facade over its own faction
  in full and everything else through its faction's visibility; `CommandSink`, issuing ordinary
  commands that pass the same validators on the next tick; `AiBudget` in evaluation units (at most
  5,000 per think); L1 decision tests on synthetic states.
- **Out of scope:** any strategy (AI-17 onward).
- **Acceptance:**
  1. AI packages never call state mutators (L0, ArchUnit).
  2. Decisions on synthetic states pass, for example "300 wood, population 9 of 10 → house" (L1).
  3. A think never exceeds its evaluation-unit cap (L1).
  4. AI commands pass the same validators as human commands (L1).
- **Tests:** L0, L1 · **Depends on:** CORE-03 · **Size:** M · **Tier:** T0
- **Status:** specified

#### AI-17 · Scripted bot with announced waves
- **Value:** A first opponent that builds, trains and attacks, and always announces its attacks, so
  the first match feels fair (P5).
- **In scope:** a `StrategyScript` in data: a build order with conditions ("population ≥ 10 →
  barracks"), worker ratios for wood and food, spearmen trained continuously, an attack schedule;
  attack waves on that schedule, repeated until there is a winner; each wave musters at a visible
  point and is announced at least 30 s before it leaves, with a notice, a countdown and a horn
  (`fx`) for the opposing players; placement templates per start position.
- **Out of scope:** openings, sized waves, defence and difficulty (AI-04, AI-05); the full economy
  (AI-02).
- **Acceptance:**
  1. In a seeded L1 match the bot completes its build order and launches its first wave at the set
     time (L1).
  2. Every wave is announced at least 30 s before it leaves (L1).
  3. The announcement reaches the opposing players (L3).
  4. Each think stays within the `AiBudget` (L1).
- **Tests:** L0, L1, L3 · **Depends on:** AI-01, ORD-04, PROD-01, ECO-03, ECO-05, ECO-09, BLD-03 ·
  **Size:** M · **Tier:** T0
- **Status:** specified

#### QA-11 · Headless match runner
- **Value:** Every build plays full matches unattended, so crashes, stalls and absurd balance are
  caught before the owner sits down to play (P4, P5).
- **In scope:** bot-versus-bot matches on `SimHarness` with fakes as the per-commit guard (L1,
  seconds per match); a two-seed L3 sample in a solo invocation inside `verifySlow`; a game-time
  limit; telemetry per match (winner, duration, first contact, army value over time, idle-worker
  seconds, banked resources, units lost, stuck counts, exceptions; time to Town from M6); budgets
  only in a separate perf variant; a failing seed's journal replays in L1; the L5 smoke's 600 ticks
  of AI versus AI on the proving ground; `/strategos skirmish <map> observe seed=<n>`, which opens
  any seed in a client for the owner to watch (`FogGate` shows observers everything).
- **Out of scope:** soaks and balance matrices (QA-12, QA-16).
- **Acceptance:**
  1. Ten seeded L1 matches run from one command and write a summary with the telemetry (L1).
  2. An exception, a stall (no AI command for 2 game minutes) or a match unfinished at the limit
     fails the run (L1).
  3. The L3 sample's journals replay in L1 to the same hash (L3).
  4. The L5 smoke runs 600 ticks of AI versus AI on a dedicated server (L5).
  5. The observe command creates the match with an observer bound to it (L3).
- **Tests:** L0, L1, L3, L5 · **Fixtures:** seed sets for headless matches · **Depends on:**
  AI-17, MATCH-01 · **Size:** M · **Tier:** T0
- **Status:** specified

#### PERF-06 · First-loop perf gate
- **Value:** The first playable build already holds its budgets, so performance never becomes a
  late surprise.
- **In scope:** the M3 row of the budget matrix: 2 × 100 units, 20 buildings and three
  constructions for 15 game minutes; budgets A–E, and G when an autosave falls in the window;
  per-unit normalisation against M1's baseline; a 2× stress pass; a report of placement, writer and
  `fx` time; the PERF-14 re-run with consent.
- **Out of scope:** larger scales (PERF-07 onward).
- **Acceptance:**
  1. Budgets A–E hold for 15 game minutes (L6).
  2. Per-unit p95 of the unit-driven buckets grows at most 15 % over M1's baseline (L6).
  3. The stress pass holds C p95 ≤ 50 ms (L6).
  4. The report lists placement, writer and `fx` times (R).
- **Tests:** L6, R · **Depends on:** BLD-03, ECO-05, PERF-05 · **Size:** S · **Tier:** T0
- **Status:** specified
