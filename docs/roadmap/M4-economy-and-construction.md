# M4 · Economy and construction

Feature cards of milestone M4. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it.

## Milestone

**Goal.** Deepen the economy and the town: stone and iron from real rock and deposits, each with
things to buy; livestock at the start; fields that reseed themselves; the full starting building
set on uneven ground; abstract HP with blocks that fall away and return; protection against
vanilla interference; batches and the complete command card; save and resume; an AI that runs the
whole economy.

**The owner plays.** A town economy: workers mine a rock outcrop and an iron mine, slaughter the
starting sheep and pigs, and farm fields that reseed for wood at the farmstead; stone and iron pay
for buildings and soldiers. A barracks rises on a slope; one builder against five shows the speed
difference. Enemy torches knock blocks off a house at each threshold, and repair puts them back;
an explosion and a piston leave the town untouched. The owner saves mid-match, quits, and resumes
exactly where it stopped. The AI lays out its own base next door.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M4 cards passes.
2. L1: gather rates, field returns and reseeding, deposit depletion, damage stages with
   hysteresis, batch timing and treasury conservation over 200 seeds; every gatherable resource
   appears in at least two costs (DATA-04).
3. L3: deposits yield exactly their amount; a foundation on a slope is prepared and completed;
   damage stages and repair round-trip through save and load; every protection source, first
   enabled and shown working on a control case, fails to change a building; a batch of 5 reaches
   its rally point; a match reloaded at minute 10 equals an uninterrupted run at minute 15; the
   same match with and without a connected viewer gives identical hashes.
4. QA-11 (L1 over ten seeds, with the L3 sample): the AI uses all four resources and livestock and
   completes its build order with no worker idle longer than 30 s.
5. L6 (PERF-07): 300 units and 60 buildings, five under construction and two under repair, within
   A–E and G; the 2× stress pass holds C p95 ≤ 50 ms.
6. L5: the restart round trip continues a match with buildings and nodes bit for bit.
7. Consent batch: the M4 goldens.

**Acceptance** (owner): a playtest with the questionnaire; no score of 2 or less; no P0 or P1 bug
open.

**Retires.** F-R12 (construction at scale); F-R1/A-R2 for idle and working units (level of
detail); F-R14/A-R14 (save, resume and the world reconciler); F-R21 (protection).

**Size and critical path.** 18 cards (5 S, 12 M, 1 L), at most 51 card-days. Critical path: BLD-04 →
BLD-08 → AI-02 (16 card-days).

---

## Decisions this milestone needs

### D-14 · Field lifecycle

**Question.** The owner decided that fields hold a remaining amount (ECO-09 in M3, with manual
reseed). What happens when one runs out?

| Option | For | Against | Card delta |
|---|---|---|---|
| Manual reseed only | Simple | Tedious micromanagement | none |
| Auto-reseed for a wood cost, toggled at the farmstead (recommended) | A steady economy with a cost and no tedium | One more rule to show in the UI | ECO-17 (S) |
| Free regrowth | No tedium | The amount loses its meaning | none |

**Recommendation.** Auto-reseed for wood, toggled at the farmstead; manual reseed always possible.

**Needed by:** M4 (ECO-17). **Affects:** ECO-09, ECO-17, BLD-08, AI-02.

**Decided (2026-10-21):** as recommended, by the owner.

### D-15 · Repair cost

| Option | For | Against | Card delta |
|---|---|---|---|
| Free, at half the build speed (recommended for the MVP) | Simple; defence is rewarded | Turtling gets cheap | none (data) |
| A share of the build cost | Repair is a decision | More bookkeeping and UI | none (data) |

**Recommendation.** Free at half the build speed; revisit after playtests. **Needed by:** M4
(BLD-06). **Affects:** BLD-06, AI-04.

**Decided (2026-10-21):** as recommended, by the owner.

---

## Cards

### Resources

#### ECO-07 · Stone from real rock
- **Value:** Workers mine real rock outcrops block by block, and hills are quarried away without
  leaving pits (P1).
- **In scope:** architecture §11.2: outcrops of stone-family blocks discovered through
  `resource_yield` or marked by the map; an amount per block; exposed blocks mined top-down and
  outside-in, never below the surrounding floor, so no unit falls into a pit; break feedback
  through AUD-04's `fx`; the core updates its own surface data.
- **Out of scope:** quarries and mines (ECO-08).
- **Acceptance:**
  1. A fully mined outcrop leaves the floor at its base level (L1).
  2. The gather rate is exact for the balance sheet (L1).
  3. No unit ends below the surrounding floor while an outcrop is mined (L1).
  4. A worker mines an outcrop in a GameTest with no page rebuild (L3).
- **Tests:** L0, L1, L3 · **Fixtures:** outcrop fixtures · **Depends on:** ECO-05, PATH-15 ·
  **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T5: a mined outcrop leaves its floor (1); the gather rate is the sheet's (2); no unit ends below the floor (3); a worker mines an outcrop in a GameTest with no page job (4) (spec §1.6 row 8)

#### ECO-08 · Mines and quarries
- **Value:** Large quarries and iron mines are lasting, contested deposits with a visible amount
  left (P1).
- **In scope:** `DepositNode`: an amount in two sizes (for example 1,000 and 5,000), work slots per
  size, visual stages at 75, 50, 25 and 0 % that swap the deposit's blocks through the writer, the
  remaining amount shown on selection.
- **Out of scope:** the deposit art (ART-02).
- **Acceptance:**
  1. A deposit with N remaining yields exactly N, then is exhausted and frees its slots (L1).
  2. Slot counts follow the size and the balance sheet (L1).
  3. The look changes at 75, 50, 25 and 0 % (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-05 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T6: an iron mine of 1,000 yields 1,000, then is exhausted and frees its slots (1); 3 slots small, 6 large, as the sheet (2); in the world a quarter of its blocks turns to stone at each stage (3); the amount left reaches every player and shows on a click (spec §1.6 row 10)

#### ECO-16 · Livestock at the start
- **Value:** The classic opening food: sheep, pigs and chickens by the civic centre, the most
  Minecraft-native resource, and the first gathering that isn't "click a node and wait" (P1).
- **In scope:** a few domestic animals per start (sheep, pigs, chickens) as Gaia fauna units that
  never flee and cost no population; Gaia itself, the match's neutral faction, moved from CORE-01
  to these first neutral units; a worker's gather order kills the animal through core damage,
  then gathers the carcass in place as a meat node; yields from the balance sheet; a fauna model
  archetype: vanilla `PigModel` and `ChickenModel` are generic over `Entity` and draw our
  `LivingEntity` body as they are, while `SheepModel` is bound to `Sheep`, so its
  `createBodyLayer()` goes into our own model class, as the horse mesh does for mounts; an
  amendment to ADR-0011 and the engine-surface inventory for a `strategos:unit_fauna` entity type
  or a renderer that dispatches by archetype.
- **Out of scope:** wild herds and hunting (ECO-10); corrals (ECO-12).
- **Acceptance:**
  1. Killing and gathering yield exactly the template amount (L1).
  2. Animals never move away from attackers and cost no population (L1).
  3. A carcass node depletes, and its body is removed (L3).
  4. The inventory test lists the new type or renderer (L2).
  5. Each animal renders with its vanilla-derived model at 60 blocks (L4, consent).
- **Tests:** L0, L1, L2, L3, L4 (consent), L5 · **Depends on:** ECO-05, UNIT-03 · **Size:** M ·
  **Tier:** T1 (pulled into the MVP)
- **Status:** in_progress
- **Progress:** M4 T7 (2026-10-24): checks 1–4 pass (spec §1.6 row 12); 5, the `livestock` goldens, waits for T27's consent batch

#### ECO-17 · Field auto-reseed at the farmstead
- **Value:** Farms keep running without micromanagement, at a price (P4).
- **In scope:** per D-14: a toggle at the farmstead that reseeds an exhausted field for a wood cost
  when it can be paid, and waits otherwise; manual reseed still works; the toggle state in
  `faction_state`.
- **Out of scope:** corrals (ECO-12).
- **Acceptance:**
  1. With the toggle on, an exhausted field reseeds when the cost can be paid and waits otherwise
     (L1).
  2. Manual reseed still works with the toggle off (L1).
  3. The toggle state reaches its owner only (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-09, BLD-08, D-14 · **Size:** S · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T15: reseeds when paid, else waits (1); manual reseed with the toggle off (2); the toggle reaches its owner only (3) (spec §1.6 row 19)

### Units and the town

#### UNIT-05 · Level of detail by activity
- **Value:** Idle and working units cost little, so more of the budget goes to the fight, and a
  battle plays out the same whether or not anyone watches.
- **In scope:** architecture §6.5: tiers by activity only: ACTIVE (fighting, or ordered within the
  last 40 ticks) thinks every 2 ticks, MOVING every 4, WORKING every 10, IDLE every 20, ABSTRACT
  (no body) every 20; wake messages move a unit to ACTIVE at once (attacked, order received, target
  gone, node exhausted, a block change under it, a stat change); tier counts in the overlay. A tier
  never depends on selection or on who watches.
- **Out of scope:** units outside loaded chunks (CORE-09).
- **Acceptance:**
  1. Tier assignment and wake rules pass table-driven tests (L1).
  2. A sleeping unit wakes within one tick when hit, ordered, or when the block beneath it changes
     (L1).
  3. Damage timing is the same for sleeping and awake units (L1).
  4. The same seeded match with and without a connected viewer gives identical hashes (L3).
  5. 300 idle units cost at most 30 % of 300 moving units (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** CORE-02, PERF-02 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T4: the tier table and the order's 40 ticks (1); a long idle unit answers an order and a blow within a tick (2); hits land on the same ticks asleep and awake (3); a watched match's hash equals an unwatched one's (4); 300 standing units cost 0.19 of 300 walking (5) (spec §1.6 rows 6–7)

#### BLD-04 · Foundations on uneven ground
- **Value:** Build where the map allows, not only on perfectly flat ground (P1).
- **In scope:** `PlacementRules` against the core surface grid (a floor-height spread of at most 2
  blocks), never a vanilla heightmap; terrain preparation as budgeted writer jobs (fill columns down
  to the ground, clear above, an optional one-block apron), shown on the foundation; the core
  updates its own overlay.
- **Out of scope:** walls drawn over terrain (BLD-13).
- **Acceptance:**
  1. A site above the maximum spread is refused with the reason (L1).
  2. A barracks on a 2-block slope is prepared and completed on a flat, supported footprint (L3).
  3. Preparation stays within the writer budget (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-03 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T8: a spread above 2 refused with its reason, `NOT_FLAT` (1); a barracks on a 2-block slope prepared, gapless and equal to its blueprint (2); its preparation within a writer budget of 8 and before its first layer (3) (spec §1.6 row 13)

#### BLD-05 · Abstract HP and damage stages
- **Value:** Damaged buildings visibly crumble, with blocks falling away at damage thresholds, and
  look whole again after repair (P1, P2).
- **In scope:** architecture §11.8: abstract HP in the core; thresholds from the template (85, 65
  and 35 % by default) that remove the blocks ranked below each stage, through the writer, with
  falling-debris `fx`; damage ranks from a hash of the blueprint id and the block index, under the
  build-order support graph; the two lowest layers of the footprint's outer ring never removed, so
  no passage opens; repair 2 % above a threshold restores its stage (hysteresis); collapse at 0
  under budget, the footprint cleared by the core; the stage saved.
- **Out of scope:** repair (BLD-06).
- **Acceptance:**
  1. At each threshold exactly the ranked blocks go, in the same order every time (L1).
  2. Damage to 30 % removes the expected blocks, and repair to full restores them (L1).
  3. HP oscillating around a threshold changes no block until it is 2 % above it (L1).
  4. No block of the two lowest outer-ring layers is ever removed (L1).
  5. Damage stages and repair round-trip through save and load in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-03, CMB-03 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T9: each threshold's ranked blocks, in one order every match (1); 30 % and back (2); 2 % hysteresis (3); the bottom ring stays (4); stages and repair through save and load (5) (spec §1.6 row 14)

#### BLD-06 · Repair
- **Value:** Builders restore damaged buildings and their lost blocks (P4).
- **In scope:** a repair order on own damaged buildings (REPAIR states); a repair rate of half the
  build speed to start, with the builder rule n^0.7; the cost of D-15; stages return as HP crosses
  thresholds upward.
- **Out of scope:** healing units (CMB-12).
- **Acceptance:**
  1. Repair time with k builders is exact for the formula (L1).
  2. Costs follow D-15 (L1).
  3. Repair never exceeds maximum HP (L1).
  4. A GameTest repair restores the removed blocks (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-05, D-15 · **Size:** S · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T10: repair time exact for k builders (1); free, D-15 (2); never past the most (3); a GameTest repair restores the blocks (4) (spec §1.6 row 14)

#### BLD-07 · Building protection
- **Value:** Buildings and nodes change only through the game's rules: no explosion, piston, mob,
  crop or stray fire can wreck them (P1).
- **In scope:** architecture §11.9: player breaking (`BlockEvent.BreakEvent`: cancel, or convert to
  damage by match rule); placing in footprints (`BlockEvent.EntityPlaceEvent`); explosions
  (`ExplosionEvent.Detonate`: protected positions pruned and converted to crush damage); pistons
  (`PistonEvent.Pre`); mob griefing (`EntityMobGriefingEvent`, `LivingDestroyBlockEvent`); fluids
  (`BlockEvent.FluidPlaceBlockEvent`); crop growth inside fields (`CropGrowEvent.Pre`); natural
  spawns in match regions (`MobSpawnEvent.PositionCheck`); the `LevelChunkMixin` catch-all that
  restores, at the end of the tick, any protected block changed without the writer's flag; fire,
  which posts no event, is handled by `doFireTick` off in arena worlds and by the blueprint lint;
  `ProtectedBlockIndex` for O(1) lookups; every handler inside `FaultBarrier`.
- **Out of scope:** survival players in open-world mode (MATCH-09).
- **Acceptance:**
  1. One GameTest per source shows it cannot change a building block; each test first enables its
     mechanism and shows it working on a control case outside the match (L3).
  2. A direct `LevelChunk#setBlockState` write to a protected position is restored by the end of
     the tick (L3).
  3. Blocks placed by builders are recognised, and foreign blocks in a footprint are rejected (L3).
  4. A lookup costs O(1) per event (L1).
- **Tests:** L0, L1, L3, L5 · **Depends on:** BLD-01, BLD-15 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T11: one GameTest per source with its control (1); a direct chunk write undone by the tick's end (2); builders' blocks indexed, foreign ones refused (3); a lookup O(1) (4) (spec §1.6 row 15)

#### BLD-08 · Starting building set, with resource sinks
- **Value:** A complete village (civic centre, houses, storehouse, farmstead, fields, barracks) in
  which every resource buys something (P1, P4).
- **In scope:** templates and rules for the civic centre (dropsite for everything, +20 population,
  trains workers), house (population per civilisation data), storehouse (wood, stone, iron),
  farmstead (food; ECO-17's reseed toggle), field and barracks (trains the roster); limits (one
  civic centre until Town); costs and build times from the balance sheet, with stone paying for
  buildings (for example the civic centre and the barracks) and iron for M4 items (for example the
  spearman's weapons), so DATA-04's sink check holds.
- **Out of scope:** the arsenal (CMB-09); towers (BLD-17, BLD-12).
- **Acceptance:**
  1. Every building's roles work end to end: dropsite, population, training (L3).
  2. Building limits are enforced (L1).
  3. Every template loads and appears in the balance sheet (L2).
  4. Stone and iron each appear in the cost of at least two M4 items (L1).
- **Tests:** L0, L1, L2, L3 · **Depends on:** BLD-04, BLD-05, ECO-08, PROD-01 · **Size:** M ·
  **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T12: dropsites end to end with the storehouse and the farmstead, population and training as M3's (1); one civic centre a faction (2); every template in the sheet (3); stone in three costs, iron in two (4) (spec §1.6 row 16)

#### BLD-09 · Cancel and demolish
- **Value:** Undo a misplaced foundation, and clear space when the town needs it.
- **In scope:** cancelling a foundation with a refund scaled by progress; demolishing an own
  finished building, without refund, after confirmation; its population and dropsite effects
  removed.
- **Out of scope:** deleting units.
- **Acceptance:**
  1. Cancelling at x % progress refunds exactly the documented amount (L1).
  2. Demolishing removes every block and effect, and the population cap is recalculated (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-03 · **Size:** S · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T13: a cancel refunds the documented share at any progress (1); a demolish removes every block and effect, the cap recalculated (2) (spec §1.6 row 17)

#### PROD-02 · Batch training
- **Value:** Train five at a time for less total time (P4).
- **In scope:** batch time = unit time × n^0.8, the exponent per building in data, rounded to ticks
  once; Shift-click queues a batch of 5.
- **Out of scope:** batch technologies (TECH-05).
- **Acceptance:**
  1. A batch of 5 completes in exactly unit time × 5^0.8, in ticks (L1).
  2. All five spawn and walk to the rally point (L3).
- **Tests:** L0, L1, L3 · **Depends on:** PROD-01 · **Size:** S · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-24 — M4 T14: a batch of 5 in exactly round(unit ticks × 5^0.8) (1); all five step out together and walk to the rally point (2) (spec §1.6 row 18)

### Interface, persistence and the opponent

#### UI-08 · Command card complete
- **Value:** Every action at your fingertips, with grid hotkeys and the exact reason when something
  is unavailable (P3).
- **In scope:** grid hotkeys; costs; disabled reasons (resources, population, phase, limit);
  tooltips with DATA-02's breakdown; the production queue with cancel.
- **Out of scope:** the research panel (UI-13).
- **Acceptance:**
  1. Hotkey assignment and layout pass tests (L1).
  2. A disabled button names the missing resource or requirement exactly (L1).
  3. Tooltip numbers equal the server's breakdown (L1).
  4. Goldens for the civic centre and barracks cards (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** UI-06, DATA-02 · **Size:** M · **Tier:** T0
- **Status:** in_progress
- **Progress:** M4 T16 (2026-10-24): checks 1–3 pass (spec §1.6 row 20); 4, the card goldens, waits for T27's consent batch

#### UI-09 · Build menu and placement UX
- **Value:** Build quickly: a clear build menu, hotkeys, and Shift to place several buildings in a
  row (P3).
- **In scope:** the worker's build page with costs and requirements; Shift places several
  foundations queued for the same builders; cancelling placement; foundation progress on screen.
- **Out of scope:** walls (BLD-13).
- **Acceptance:**
  1. Layout and hotkey tests pass (L1).
  2. Shift places several foundations, and the builders take them in order (L3).
  3. Goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** UI-08, BLD-04 · **Size:** M · **Tier:** T0
- **Status:** in_progress
- **Progress:** M4 T17 (2026-10-24): checks 1 and 2 pass (spec §1.6 row 21); 3, the goldens, waits for T27's consent batch

#### MATCH-04 · Save and resume
- **Value:** Stop at any time and continue later exactly where you were, even after a crash between
  saves.
- **In scope:** architecture §5.6 and ADR-0018 for players: resume restores orders, queues, nodes,
  buildings and AI plans; singleplayer matches resume `PAUSED`; on resume the terrain is re-sliced
  and compared with the stored page hashes, and `WorldReconciler` converges every difference
  (blocks the core owns are rewritten from core state; every other difference enters the first tick
  as a `WorldChange`); the 10-minute production journal ring (config) and `/strategos journal
  save`, so a bug the owner finds becomes an L1 reproduction; camera pose and control groups stay
  client-side.
- **Out of scope:** replays for players (MATCH-13).
- **Acceptance:**
  1. After a reload at minute 10, the state hash at minute 15 equals that of an uninterrupted run
     (L3).
  2. The restart round trip continues a match with buildings and nodes bit for bit (L5).
  3. On a crash-between-saves fixture, `WorldReconciler` restores the core's blocks and journals
     the other differences as world changes (L3).
  4. A saved journal ring replays in L1 to the same hash (L1).
  5. Camera pose and control groups survive a resume in the client model (L1).
- **Tests:** L0, L1, L3, L5 · **Fixtures:** crash-between-saves fixture (append-only) ·
  **Depends on:** CORE-07, CORE-11, CORE-12, BLD-05, ECO-08 · **Size:** M · **Tier:** T0
- **Status:** specified

#### AI-02 · AI economy and base building
- **Value:** The AI builds a real economy and lays out its own base, so its armies come from its
  own work (P5).
- **In scope:** worker ratios for four resources and livestock; reassignment when nodes run out;
  houses before the cap; dropsites near resources; fields and auto-reseed; a site map computed at
  match start as a pure job; the player's placement rules; build orders in data; one central budget,
  never per-queue accounts.
- **Out of scope:** tactics (AI-03); waves and defence (AI-04).
- **Acceptance:**
  1. In 10 seeded 15-minute L1 matches the AI completes its build order without blocked or
     abandoned foundations (L1).
  2. No AI worker idles longer than 30 s, and resource shares stay within 10 % of the targets
     after minute 3 (L1).
  3. The AI never walls off its own units or nodes (L1, reachability check).
  4. Planning stays within the `AiBudget` (L1).
  5. An L3 sample of one seed reaches the hash of its L1 run (L3).
- **Tests:** L0, L1, L3 · **Depends on:** AI-17, BLD-08, ECO-08, ECO-09, ECO-16 · **Size:** L ·
  **Tier:** T0
- **Status:** specified

#### ART-02 · Resource and deposit visuals
- **Value:** Every resource reads well from the camera: what it is and how much is left (P2).
- **In scope:** per D-04: quarries and iron mines with depletion stages; field crop stages; berry
  bushes; outcrops; livestock; selection markers on nodes; vanilla blocks or our own textures only.
- **Out of scope:** civilisation art (ART-03).
- **Acceptance:**
  1. Every node type and stage has a golden at 60 blocks (L4, consent).
  2. The owner approves the look and recognises each node and stage (L7, acceptance).
- **Tests:** L4 (consent), L7 · **Depends on:** ECO-08, ECO-09, ECO-16, D-04 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### PERF-07 · Economy and construction perf gate
- **Value:** A busy town never lags.
- **In scope:** the M4 row of the budget matrix: 150 workers on trees, rock, mines, fields and
  livestock, 150 other units, and 60 buildings with five under construction and two under repair,
  for 15 game minutes; A–E and G; per-unit normalisation; a 2× stress pass; a report of node-index,
  dropsite-query, placement and writer times.
- **Out of scope:** combat load (PERF-08).
- **Acceptance:**
  1. Budgets A–E and G hold (L6).
  2. Per-unit p95 of the unit-driven buckets grows at most 15 % over M3's baseline (L6).
  3. The stress pass holds C p95 ≤ 50 ms (L6).
  4. The report lists the economy and construction times (R).
- **Tests:** L6, R · **Depends on:** BLD-08, UNIT-05, PERF-06 · **Size:** S · **Tier:** T0
- **Status:** specified
