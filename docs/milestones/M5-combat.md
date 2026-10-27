# M5 — Combat: implementation spec

*Written 2026-10-24 by the assistant as M4 closed, from the 14 approved M5 cards in
[roadmap/M5-combat.md](../roadmap/M5-combat.md), [04-architecture.md](../04-architecture.md) (cited as
"04 §n"), the ADRs in [adr/](../adr/README.md), chiefly
[ADR-0011](../adr/0011-unit-entity-and-body-seam.md) (unit bodies) and [ADR-0019](../adr/0019-performance-budgets.md)
(budgets), the behavioural notes of [research/mechanics.md](../research/mechanics.md) §1.8–1.10 and
§1.13, and M4's spec and code ([M4-economy-and-construction.md](M4-economy-and-construction.md), cited
as "M4 §n"). Status: **specified**. M5 starts on the owner's word («начинай M5»). D-08 kept the
observer avatar, so CMB-08 stays deferred and outside the gate. Engine names cited here come from the
cards and the architecture; every other Minecraft or NeoForge name is checked in `build/mc-src` by the
task that first uses it, and recorded in §1.6 when it differs. Clean-room as before: the 0 A.D. figures
below are the research notes' behavioural numbers, never its code or data.*

---

## 0. Read this first

### 0.1 Authority

As M4 §0.1: the architecture and the ADRs decide *what*, this spec *how M5 builds it*; the cards define
scope and acceptance; ROADMAP.md holds the statuses; a disagreement is a §1.6 row. M4's code is the
starting point.

### 0.2 Working rules for every task

M4 §0.2 holds, with these changes:
- **Windows.** The owner's word of 2026-10-24 allows every kind of test. L4 checks run together in the
  consent batch (T17), so the screen is taken once.
- **A scene's failed check fails its run** from T1 on (M4 §8.1's follow-up): `scenarioDiff` reads the
  result's `ok` as well as the pixels.
- **Long L1 matches** (duel matrices over 200 seeds, battle scenarios) are tagged `slow` and run in
  `:core:slowTest` (M4 T28), every task still running them locally.

### 0.3 Code conventions

M4 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Snapshot | New stores (shots in flight, stances, garrisons) are optional fields of the snapshot, saved and hashed only when they hold something, so M4's journals and saves replay unchanged until a match uses them |
| Commands | New ids follow 24: `SetStance` 25, `Garrison` 26, `Ungarrison` 27, `RingBell` 28, `AllClear` 29, `AttackSpread` 30; each in `CommandKinds`, the protocol pins and the engine-surface inventory where it adds a payload |
| Randomness | Every draw (a shot's spread, a duel's placement) comes from the stateless `Rng(seed, stream, tick, a, b)`; a new stream number per use, listed in `Rng`'s table |
| Balance | Every new number has provenance in `provenance.json` and its sheet row; the starting values below are tuned in T7 against the duel matrix, never past their sheet rows without a new provenance note |

---

## 1. Goal, demo and scope

### 1.1 Goal

Deepen combat into readable counters (archers with analytic projectiles, horsemen, stances, focus fire,
high ground), give raids their classic answer (garrison and the town bell, with civic-centre arrows that
grow with the garrison), and an AI that fights by the same rules (the roadmap's goal).

### 1.2 Slices

| Slice | Content | Demo at its end |
|---|---|---|
| 5.0 carry-over | T1 health check | M4's follow-ups closed |
| 5.1 shooting | PATH-11, CMB-04, CMB-16 | Archers on a hill outrange archers below; arrows lead a walking target and can miss |
| 5.2 fighting by itself | CMB-05, ORD-05 | Stances hold, leash and flee; focus and spread fire; force attack on an own building |
| 5.3 the roster | UNIT-06, UNIT-07 | Horsemen on horses; spearmen beat horsemen, horsemen archers, archers spearmen |
| 5.4 defence | CMB-07, BLD-16, BLD-11 | The bell sends the workers in; the civic centre's arrows grow with them and break a raid |
| 5.5 the opponent and the gate | CMB-17, AI-03, PERF-08, the consent batch | "Battle at the ford" against the bot |

### 1.3 Demo script (L7, the owner)

1. `scripts/playtest.sh`; `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0`,
   `/strategos match scenario strategos:battle_at_the_ford`: 60 a side (spearmen, archers, horsemen) at
   the fords, the bot in slot 1.
2. Put archers on the mesa's ramp: they outreach those below (the tooltip shows the bonus).
3. Attack-move the spearmen; Ctrl-right-click a target to focus it, Alt to spread; set the archers to
   stand ground and the spearmen to defensive.
4. The bot's horsemen raid the workers: ring the bell on the civic centre's card (or right-click its bell
   in first person); the workers vanish inside; the centre's arrows, one more per worker inside, break the
   raid; all clear sends everyone back to their nodes.
5. Win once, lose once; the questionnaire (`docs/testing/playtest.md`), compared with M2 and M3.

### 1.4 Cards, tasks and checks (traceability)

| Card | Task | Checks (level) |
|---|---|---|
| PATH-11 | T2a–T2b, T6 | 1 `MobilityTest.aOneBlockGapIsOpenToInfantryAndClosedToLarge` (L1) · 2 `MobilityTest.reachabilityPerClassIsAFloodFill` (L1) · 3 `pathtests.thirty_horsemen_cross` (L3) |
| CMB-04 | T3a–T3c, T14 | 1 `ShotTest.fixedSeedsReproduceTheGoldenResults` (L1) · 2 `ShotTest.theHitRateStaysInTheBinomialBound` (L1) · 3 `ShotTest.movingTargetsAreHitLessAtLongRange` (L1) · 4 `combattests.archer_volley_replays_as_the_core_predicts` (L3) · 5 `perftests.battle_soak`'s budget E (L6, T14) |
| CMB-16 | T4 | 1 `HighGroundTest.rangeAndDamageFollowTheFormula` (L1) · 2 `DuelTest.archersOnAHillBeatArchersBelow` (L1, slow) · 3 `HighGroundTest.theBonusIsInTheBreakdown` (L1) · 4 `combattests.a_hill_reaches_further` (L3) |
| CMB-05 | T5a, T5b | 1 `StanceTest.theTable` (L1) · 2 `StanceTest.targetPreferences` (L1) · 3 `StanceTest.aChaseEndsOutOfVision` (L1) · 4 `combattests.stance_*` per family (L3) |
| ORD-05 | T8 | 1 `FocusTest.aFocusMakesEveryArcherInRangeShootIt` (L1) · 2 `FocusTest.aSpreadIsWithinOneOfEven` (L1) · 3 `combattests.force_attack_on_an_own_building` (L3) |
| UNIT-06 | T6 | 1 `bodytests.a_horseman_is_one_entity` (L3) · 2 the engine-surface inventory lists `unit_mount` with ADR-0011's settings (L2) · 3 `perftests.mounted_cost` within 20 % of a foot unit (L6) · 4 `m5_goldens` riders at every camera height (L4, T17) |
| UNIT-07 | T7 | 1 `DuelTest.eachCounterWins` (L1, slow) · 2 `DuelTest.everyUnitWinsOneAndLosesOne` (L1, slow) · 3 `combattests.duels_*` 20 seeds each, smoke (L3, slow batch) · 4 the owner's silhouettes (L7) |
| CMB-07 | T9 | 1 `TownArrowsTest.countAndDpsAreTheSheets` (L1) · 2 `TownArrowsTest.onlyEnemies` (L1) · 3 `combattests.the_centre_breaks_a_small_raid` (L3) |
| BLD-16 | T10 | 1 `GarrisonTest.capacityAndClasses` (L1) · 2 `GarrisonTest.handleHpAndOrdersSurvive` (L1) · 3 `garrisontests.the_bell_brings_them_in` (L3) · 4 `garrisontests.all_clear_returns_them` (L3) · 5 `garrisontests.garrisoned_units_have_no_body` (L3) · 6 `garrisontests.the_bell_block_rings` (L3) |
| BLD-11 | T11 | 1 `TownArrowsTest.basePlusGarrisoned` (L1) · 2 `TownArrowsTest.dpsWithAGarrison` (L1) · 3 `garrisontests.a_garrisoned_centre_breaks_a_horse_raid` (L3) |
| CMB-17 | T12 | 1 `AttackLinesTest.onlyWhatTheViewerMayReceive` (L1) · 2 `AttackLinesBench` under 0.3 ms for 300 units (L6) and PERF-14's battle scene (T17) · 3 `m5_goldens` lines and flashes (L4, T17) |
| AI-03 | T13 | 1 `BattleBotTest.focusesAndRetreats` (L1, slow) · 2 `BattleBotTest.shiftsToSpearmenAgainstHorse` (L1, slow) · 3 `BattleBotTest.withinTheBudget` (L1) · 4 `aitests.battle_seed_replays_to_its_hash` (L3) |
| PERF-08 | T14 | 1 `perftests.battle_soak` A–E (L6) · 2 per-unit A within 15 % of M4's base (L6) · 3 `perftests.battle_stress` C p95 ≤ 50 ms (L6) · 4 the report's cost per combat system (R) |

### 1.5 Moved out of M5, with the reason

| Card | Reason |
|---|---|
| CMB-08 | Deferred: D-08 kept the observer avatar; it joins the gate only if revived |

### 1.6 Differences resolved

| # | Topic | Resolution |
|---|---|---|
| 1 | Spread and focus in one command | `Attack` stays focus (every attacker on the target, M2's meaning); `AttackSpread` (30) splits a group over the enemies within its first target's vision, the nearest first, one per target round the even split; Ctrl and Alt on the right click choose (UI rows in 04 §2.5) |
| 2 | Shots in the save | A shot in flight is core state: its store is saved and hashed, so a save mid-volley resumes with the same hits (MATCH-04's rule) |
| 3 | PATH-11 mounted crossing and task order | T2 verifies class grids, large motion, mixed groups and save/resume in L1, plus existing L3 movement gates. The thirty mounted units cross in T6, which supplies the actual body; PATH-11 stays in progress until then. Its criterion and test level are unchanged. |
| 4 | Footprints at page borders and steps | Large clearance reads all eight adjacent columns, including diagonal page neighbours. A border change rebuilds the surrounding large pages; an adjacent step may raise the body by at most one block, with raised headroom checked throughout. Infantry retains its grid and legacy journal hashes. |
| 5 | Navigation accounting and body radius | Memory reports include both derived grids. Static avoidance uses the expanded large grid without subtracting body radius twice; body-to-body avoidance still uses template radius. Large templates default to 0.7 and reject smaller radii. |
| 6 | Shot point, collision and impact ordering | Freeze the target body-center Y and predict X/Z with the current horizontal velocity; spread is horizontal Gaussian. Resolve a circular Body.radius footprint within the current body-height interval, preferring the complete live target handle, then ascending live spatial candidates of any faction, as "whatever unit" specifies. Simultaneous impacts follow stable shot IDs; a dead shooter does not cancel its frozen packet. |
| 7 | Shot persistence extension | The outer snapshot codec already has 16 fields. Add optional shot state under nested Play, preserving pristine old bytes/hashes and the allocator of used-empty stores. The pure model is implemented first; scheduler, codec and hash wiring remains part of T3a before its checkbox is complete. |
| 8 | Release phase and ranged defaults | UNIT_AI queues an internal firing intent; COMBAT constructs its frozen launch from post-MOVEMENT positions and velocity before resolving impacts. This preserves the approved lead formula and a constant-velocity control. Starting archer projectile speed is 4 blocks/tick, spread 2.25 blocks at 100 and sight 32; these project defaults receive provenance with the archer content and are tuned with the roster. |
| 9 | Flat-world velocity and numeric damage safety | Flat matches deliberately persist only poses, preserving released journal hashes. A transient firing intent captures the target position before MOVEMENT; COMBAT derives its displacement for the lead. Terrain matches use the existing motion velocity. Ranged damage is bounded to finite float-safe values before packet conversion, including after modifiers. |
| 10 | Shot feedback continuity and viewers | Freeze release coordinates and tick with every new shot so impact feedback retains both endpoints after shooter death or restore. Restore preserves future impact feedback; replaying past launch FX to reconstruct client arcs is outside the event contract. Deliver shot FX once to the union of viewers of either endpoint, batched by endpoint pages. Client arcs clear when the match changes. |
| 11 | A passive unit's flee distance | §2.4 says 12 blocks; M4 approved the worker's `Behaviour/flee` of 8 with its provenance. A passive unit flees its template's `Behaviour/flee` when that is set and 12 blocks otherwise, so the worker keeps its approved 8. |
| 12 | Stances: where the table is silent | `Stance` lives in `core.shared` (content and protocol both use it; ArchUnit forbids the cycle). Violent behaves as aggressive: with "a chase ends out of vision" for every order, "chases beyond vision" has no room, and no neutral or harmless targets exist yet to tell them apart. The out-of-vision rule ends an explicit attack only once it is chasing, so a far target ordered from afar is still walked to. A defensive unit's spot is set by `SetStance`, by going idle over 2 blocks from it, and by an engagement taken over other orders; it walks back after an engagement ends with an empty queue. Notice radii are capped at vision so the notice and the leash agree. Stand ground's reach is the melee reach or the ranged range less the 0.5 approach margin. |

### 1.7 Consent plan

The owner's word of 2026-10-24 covers every kind of test. `m5_goldens` (riders at the camera's heights,
a volley's launch and impact, attack lines and the under-attack flash, the bell and an empty town, the
battle at the ford), the art look with and without Sodium, and PERF-14's battle scene run together in
T17.

---

## 2. Design of the M5 slice

### 2.1 Mobility classes (PATH-11, 04 §9)

- `MobilityClass` gains `LARGE` beside `INFANTRY`: clearance 2.5 blocks high and a column's free
  neighbours on both sides of a step, so a 1-block gap between two blocked columns is closed to it; its
  avoidance radius 0.7 against infantry's 0.35.
- Surfaces carry the headroom already; components and the reachability index are kept per class, built
  together page by page, so a page rebuild costs about twice M4's (measured in T2, budget D).
- A unit's class comes from `UnitMotion/mobility` (data since M1); paths, slots and stuck rungs read it.

### 2.2 Ranged attacks and analytic shots (CMB-04, 04 §6.7)

- **Data.** `Attack/Ranged`: `pierce` (and other types), `range`, `prepare`, `repeat`, `speed` (blocks a
  tick), `spread` (blocks at 100 blocks, σ), `preferred` classes. Starting values for the archer below.
- **Approach.** A ranged attacker approaches to its range less a margin and stops; it keeps no melee
  ring place. Its wind-up and period are M2's timer.
- **A shot.** On release: the flight time `t = ceil(d / speed)` ticks; the aim the target's position plus
  its velocity × t; the landing point the aim plus a Gaussian offset of σ = spread × d / 100 drawn from
  `Rng(seed, SHOT, tick, shooter, n)`. The shot joins the `ShotStore` (shooter, target, land tick, landing
  point, the damage packet); at its land tick, in COMBAT, it hits whatever unit's footprint holds the
  landing point, the target first, or nothing. No projectile entity exists anywhere.
- **Feedback.** A launch and an impact `fx` event per shot, batched per tick per 16 × 16 page to the
  `SeesCell` audience, shown when either end is visible; the client draws an arrow along the arc from
  the two ends and the flight time (a vanilla arrow model, no entity) and plays the bow shot.

### 2.3 High ground (CMB-16)

- A modifier source on ranged attacks: range × (1 + `rangePerBlock` × Δh) and damage × (1 +
  `damagePerBlock` × Δh) for Δh > 0, Δh the shooter's floor over the target's, capped by `maxBlocks`;
  starting values 0.05 a block, 0.04 a block, cap 8 (0 A.D.'s reach by height, AoE II's damage from
  above). The breakdown names the source.

### 2.4 Stances, targeting, chase and flee (CMB-05, 04 §7.5)

| Stance | Engages | Chases | Returns | Flees |
|---|---|---|---|---|
| violent | anything in vision, retaliates always | beyond vision | no | no |
| aggressive (default) | visible enemies | within vision | no | no |
| defensive | enemies within attack range + ½ vision of its spot | to that radius | to its spot | no |
| stand ground | in range only | never moves | — | no |
| passive (civilians' default) | never | never | — | 12 blocks from the attacker |

- A chase always ends when the target leaves vision, even under an explicit order (0 A.D.'s rule).
- Targets by the attack's preferred classes, then the nearest, through the spatial index at the
  unit's think period; `SetStance` (25) sets it for a selection; the card shows the five.
- FLEEING is a state: away from the attacker, re-planned each think, back to the order when it is 12
  blocks off or the attacker is gone.

### 2.5 Focus, spread, force attack (ORD-05)

- `Attack` on a target: every attacker on it (focus). `AttackSpread` (30): the group's attackers over the
  enemies within the target's vision, sorted by distance to the target, one per target round the even
  split, each re-picking when its own dies. Force attack: `Attack` on an own unit or building is allowed
  with Ctrl; the rule refuses Gaia's animals, which are gathered. Target markers: a ring on the focused
  target for the selection (client).

### 2.6 Mounted units (UNIT-06, ADR-0011)

- `strategos:unit_mount`: 1.4 × 2.0, tracking range 10, interval 3; one entity per unit; the renderer
  composes a horse body from vanilla's `HorseModel` mesh in our own model class with a humanoid rider
  above (checked in `build/mc-src` in T6); gallop and attack poses from the kinematic driver's speed and
  `UnitStruck`; the large mobility class. No passengers, no vanilla horse.

### 2.7 The roster (UNIT-07)

Starting values, ticks and blocks (worker speed 0.1875 a tick), each with its provenance row:

| Unit | Cost | HP | Attack | Range | Prepare / repeat | Speed | Resistance h/p/c |
|---|---|---|---|---|---|---|---|
| Spearman (M2) | 60F 40W | 100 | 3 hack + 3 pierce, ×2.5 vs Cavalry | 1.2 | 10 / 20 | 0.1875 | 5/5/2 |
| Archer | 60F 40W 20I | 70 | 16 pierce | 20 | 16 / 20 | 0.2 | 1/1/1 |
| Horseman | 80F 40W 30I | 200 | 10 hack | 1.5 | 8 / 18 | 0.34 | 3/2/2 |

- Tuned in T7 (2026-10-27) from the starting archer 50 HP, 6 pierce, repeat 25 and horseman 120 HP,
  5 hack, which lost both counters (archers over spearmen 0 of 200); each change is a provenance and
  sheet row with its reason. The duel matrix at the tuned numbers, on a published floor as on the server: spearmen
  over horsemen 200, horsemen over archers 193, archers over spearmen 200 of 200; archers on a
  six-block plateau beat archers below 188 of 200 (flat control 90).
- The duel matrix (T7): equal cost, 200 seeds of placement and spread, each counter at least 170 wins:
  spearmen over horsemen, horsemen over archers, archers over spearmen starting at range. Tuning moves
  only the table's numbers, each change a sheet row with its reason.
- Silhouettes: the archer holds a bow, the horseman a sword and a shield, the spearman its spear.

### 2.8 Town arrows (CMB-07, BLD-11)

- `Attack/Building` on the civic centre: `arrows` 3, `perGarrisoned` 1, `interval` 80 ticks, 8 pierce,
  range 24; the arrows of a volley go round the enemies in range by preference, each a CMB-04 shot from
  the footprint's middle at its top.

### 2.9 Garrison and the bell (BLD-16)

- `GarrisonHolder` on the civic centre: capacity 20, classes Worker and Infantry (data); `Garrison` (26)
  walks units in by the door and puts them inside: GARRISONED, `BodyMode.NONE`, out of the spatial index,
  their handle, HP and order queue kept; `Ungarrison` (27) lets them out at the door.
- The bell: `RingBell` (28) from the centre's card pushes a garrison order to the front of every own
  worker's queue within 64 blocks, to the nearest building with room, and sounds the bell as `fx`;
  `AllClear` (29) finishes those orders, so each worker resumes what it was doing (04 §7.4). In first
  person a right click on the bell block of the centre's blueprint rings it
  (`PlayerInteractEvent.RightClickBlock`).

### 2.10 Attack lines and the flash (CMB-17)

- Lines from the selection's `unit_detail` targets, drawn with the extract-then-draw overlay; the flash
  from `unit_hud` state bits (hit this tick). Nothing the viewer may not receive.

### 2.11 Battle tactics (AI-03)

- A `MilitaryManager` grown with: composition from the counters (more of what beats what the enemy
  shows, re-planned each minute); focus on the wounded or high value in range; ranged units held a
  step behind the melee line; retreat to the base below a strength ratio (data, 0.6 by default);
  knobs per difficulty. Within `AiBudget`.

---

## 3. Build changes

None planned. `scenarioDiff` reads the result's `ok` (T1).

---

## 4. Tests per level

As M4 §4. New suites: `garrisontests`, the duel and battle L1 tests in `:core:slowTest`, `combattests`'
stance and duel GameTests in the slow batch (`slow_combat`, solo).

---

## 5. Measurements before building on them

| What | Where | Why |
|---|---|---|
| A page rebuild with two mobility classes | T2, `PageBuilderBench` | Budget D, §2.1's twice M4's |
| A volley of 60 shots' cost in COMBAT | T3, a new `ShotBench` | Budget C at 300 units fighting |
| Fx bytes for 300 units fighting | T3, `viewer_bytes` style | Budget E |

---

## 6. The battle soak (T14) — PERF-08

The M5 row of the budget matrix, on the proving ground v3: 300 units fighting (spearmen, archers,
horsemen) in waves across the fords, the civic centres' arrows firing and a garrison inside one, for 5
game minutes paced at 20 ticks a second; A–E; per-unit p95 of A within 15 % of M4's base
([perf/economy-m4pro.json](../../perf/economy-m4pro.json)); a 2× stress pass holding C p95 ≤ 50 ms; the
report lists targeting, shots and damage per tick. `BattleScene` (core testkit) is a `SoakScene`, as
M4's scenes are.

---

## 7. Task breakdown

### 7.1 Order

T1 → T2a → T2b (PATH-11) → T3a → T3b.1 → T3b.2 → T3c.1 → T3c.2 (CMB-04) → T4 (CMB-16) → T5a → T5b (CMB-05) → T6 (UNIT-06) → T7 (UNIT-07) → T8 (ORD-05)
→ T9 (CMB-07) → T10 (BLD-16) → T11 (BLD-11) → T12 (CMB-17) → T13 (AI-03) → T14 (PERF-08) → T15 (the
battle scenario) → T17 (the consent batch) → T18 (close). T16 is reserved for a row a task adds.

### 7.2 Tasks

#### T1 · Health check from M4's close
- Split `RtsHud` (882 lines), `PerfTests` (856) and `MatchState` (836) along their seams; a scene's
  failed check fails its run; Linux CI's goldens re-recorded when the scenario job next runs by hand.
- **Verification:** `verify verifySlow`.

#### T2 · PATH-11 (split during recovery, 2026-10-26)

- **T2a · Recover mobility and integration** (about 1,400 lines): §2.1, class grids, halos,
  mixed movement, template validation, construction eviction and same-class formation recovery.
  Checks 1–2 run in `MobilityTest`, `LargeMovementTest` and `LargeIntegrationTest`; check 3
  remains in T6 with the actual mounted body (§1.6 row 3).
- **T2b · Eliminate measured recovery overhead** (about 200–600 lines): cache template mobility
  with correct accepted/rejected reload behavior; avoid impossible eviction work; avoid unchanged
  halo rebuilding and duplicate effective-slice decoding. Differential terrain tests retain the
  complete voxel-based clearance model, including headroom, diagonals and footprint removal.
- **Verification for both steps:** `verify verifySlow verifySoak`, `:core:jmh :core:jmhCompare`
  and the path-budget check. T2a and T2b remain unchecked until the combined implementation
  passes the original gates; no intermediate red code is committed. Their shared recovery is
  delivered atomically because the measured regressions cross the original T2 source changes.

#### T3 · CMB-04 (bounded steps restored from legacy e8cf0c2)

- **T3a · Analytic shot lifecycle:** deterministic lead/spread, landing damage, stable shot ids,
  snapshot/restore and hashing. L1 fixed-seed golden results, stationary binomial model,
  impact/lifecycle checks and exact save/resume, including a quiescent previously used store.
- **T3b.1 · Ranged behavior** (estimated 900–1,300 source/test lines): typed ranged stats,
  approach/wind-up/period without melee rings, internal firing intents released after MOVEMENT,
  moving-target L1 checks with a constant-velocity positive control and direction changes.
- **T3b.2 · Archer content and engine integration** (estimated 300–600 lines plus generated
  balance evidence): the archer template ahead of T7, append-only template indices, provenance,
  balance sheet, language label and the L3 volley. Keep the existing template index prefix,
  including the previously implicit farmstead/storehouse entries, before appending the archer.
- **T3c.1 · Shot FX state and wire** (estimated 700–1,000 source/test lines): optional frozen
  release origin and tick, launch/impact outputs including misses, persistence/hash coverage,
  append-only FX codes and bounded trajectory encoding. Preserve all old FX bytes and the
  existing placement-byte cap; test new worst-case payloads separately.
- **T3c.2 · Shot FX delivery and client** (estimated 550–850 source/test lines): bounded
  batches to the union of viewers of either endpoint, client arrow arcs without entities and
  bow/impact sounds. L1 audience and L2 wire tests; images stay in T17 and the 300-unit
  network budget stays in T14.
- Same approved scope and criteria as T3. Each step runs its required full gates and JMH
  when it changes the core tick/codecs. CMB-04 remains in progress until all its criteria pass,
  including T14 budget E. Target 300–1,500 changed lines per step; split further before expansion.

#### T4 · CMB-16
- §2.3. **Verification:** checks 1, 3, 4 (2 with T7's duel harness).

#### T5 · CMB-05 (split before implementation, 2026-10-27)
- **T5a · Stances in the core** (estimated 900–1,300 source/test lines): the five stances as data
  (`Behaviour/stance`, workers passive), a per-unit stance and spot, persisted and hashed only when
  set; `SetStance` (25) with its rule; engagement, retaliation, chase, leash and return per §2.4's
  table; a chase ends when the target leaves vision under any order; targeting by the attack's
  `preferred` classes, then the nearest. L1 checks 1–3.
- **T5b · FLEEING, the card and the GameTests** (estimated 500–800 lines): FLEEING as a state
  re-planned each think until 12 blocks off or the attacker is gone; the card's five stance
  buttons; one GameTest per stance family (check 4).
- **Verification:** the card's checks, split as above; `verify verifySlow verifySoak` and JMH
  for each step (the unit AI is in the tick).

#### T6 · UNIT-06
- §2.6. **Verification:** checks 1–3; 4 in T17.

#### T7 · UNIT-07
- §2.7; the duel harness (L1, slow) and its GameTest smoke. **Verification:** checks 1–3; the sheet.

#### T8 · ORD-05
- §2.5; `AttackSpread` (30). **Verification:** the card's checks.

#### T9 · CMB-07 · T10 · BLD-16 · T11 · BLD-11
- §2.8, §2.9; `Garrison` (26), `Ungarrison` (27), `RingBell` (28), `AllClear` (29). **Verification:**
  the cards' checks.

#### T12 · CMB-17
- §2.10; `AttackLinesBench`. **Verification:** checks 1–2; 3 in T17.

#### T13 · AI-03
- §2.11; `battle.json` as the bot's script for the battle. **Verification:** the card's checks.

#### T14 · PERF-08
- §6. **Verification:** the card's checks.

#### T15 · The battle scenario
- `battle_at_the_ford.json` (§1.3), its L1 run and the demo's commands. **Verification:** an L1 run of it
  ends with a winner; the skirmish plays it.

#### T17 · The consent batch · T18 · M5 close
- §1.7; §8 walked into results, the health check, STATE with the demo.

### 7.3 Progress

- [x] T1 (2026-10-26) · [x] T2a (2026-10-26) · [x] T2b (2026-10-26) · [x] T3a (2026-10-26) · [x] T3b.1 (2026-10-27) · [x] T3b.2 (2026-10-27) · [x] T3c.1 (2026-10-27) · [x] T3c.2 (2026-10-27) · [x] T4 (2026-10-27) · [x] T5a (2026-10-27) · [x] T5b (2026-10-27) · [x] T6 (2026-10-27) · [x] T7 (2026-10-27) · [x] T8 (2026-10-27) · [x] T9 (2026-10-27) · [ ] T10 · [ ] T11
- [ ] T12 · [ ] T13 · [ ] T14 · [ ] T15 · [ ] T17 · [ ] T18

---

## 8. Acceptance checklist (M5 exit)

| # | Criterion | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the M5 cards passes at its level, CMB-08's excepted | §1.4 |
| 2 | L1, 200 seeds, equal cost: each counter wins at least 170; archers on a 6-block hill win at least 170 | `DuelTest` |
| 3 | L1: ranged hits reproduce their goldens; every stance behaves as its table says | `ShotTest`, `StanceTest` |
| 4 | L3: the bell brings every worker within 64 blocks inside within 10 s; all clear returns each within 5 s | `garrisontests` |
| 5 | L6: 300 units fighting for 5 minutes within A–E; the 2× pass holds C p95 ≤ 50 ms | `perftests.battle_soak`, `battle_stress` |
| 6 | The consent batch: the M5 goldens and PERF-14's battle scene | T17 |
| 7 | M1's to M4's gates still pass; `verify`, `verifySlow`, `verifySoak`, `verifyPerf` green; CI green | the runs |
| 8 | L7: the owner wins and loses the battle at least once and scores the questionnaire | the owner's note |

---

## 9. Risks and fallbacks

| # | Risk | Sign | Fallback |
|---|---|---|---|
| R1 | Two mobility classes double page rebuilds past budget D | T2's bench | Build the large class's components lazily, on the first large unit |
| R2 | The duel matrix cannot meet 170 of 200 with sane numbers | T7 | Widen the counters' bonuses before the stats; a §1.6 row with the owner's view at the demo |
| R3 | Shots' fx exceed budget E at 300 fighting | T3, T14 | One launch event per volley with its targets, the client deriving each arrow |
| R4 | Garrisoned units' missing bodies break replication | T10 | A body hidden, not removed, for the garrison's life |
| R5 | Horse and rider render cost over 20 % | T6 | The rider merged into the horse's mesh at a distance |

---

## 10. Owner inputs

| Input | When | Blocks |
|---|---|---|
| «начинай M5» | received; successor assignment #19 merged 2026-10-26 | Settled; recovery continues on dev/o-e-lytvynskyi/m5 |
| The goldens and the looks | T17 | Accepted |
| The battle playtest and the questionnaire | after the demo | Accepted |

## 11. Implementation results

### T1 — recovered and revalidated (2026-10-26)

Recovered the source and regression tests from legacy a13850b after independent review,
without importing its central records or historical measurement files. Resource discovery,
HUD card presentation and long perf scenes now have separate classes; MatchState, RtsHud
and PerfTests are 793, 788 and 779 lines respectively. Annotated perf entry points are unchanged.
The scene-verdict selftest first reproduced the ignored failure with matching pixels, then
passed after the fix, including platforms without goldens. The UTF-8/empty-input roadmap
regressions also failed before their fixes; all 12 Python tool tests now pass.

Current macOS validation: initial main verifyFast 1m20s; recovered T1 verifyFast 47s;
combined verify verifySlow verifySoak 4m39s, 1,908 deduplicated tests, no failures, errors or
skips. The combined run includes the scene-diff selftest. No baseline, threshold or image
was changed. Visual checks remain in T17's batch; Linux golden refresh remains outstanding
for the next scenario workflow, as T1 specifies.

### T2a–T2b — recovered mobility and measured performance (2026-10-26)

Recovered legacy a545902 selectively, retaining historical Windows evidence in its branch.
LARGE has 2.5-block headroom, eight-column clearance across page boundaries, its own grid,
reachability and paths; mixed groups share a request only within a mobility class. Memory
queries include both grids and the new border-signature storage. Exact save/resume and old
journals pass. PATH-11 remains in progress until T6's actual thirty-mounted-unit crossing.

Six integration cases reproduced and fixed adjacent/diagonal construction eviction and
mixed-class place trading. Further tests protect sparse template mobility caching, accepted
and refused reloads, footprint lifecycle and incremental/full-grid equivalence over border,
ceiling, duplicate-capture and footprint edits. Ordered changed-page reads are O(1); effective
slices are shared within a publication and unchanged neighbouring pages are not rebuilt.

Final verify verifySlow verifySoak passes in 4m24s: 1,939 tests, no failures/errors/skips;
two separately run perf assertions also pass. Populated 512x512 navigation uses 10,114,672
bytes (32 MiB cap). Final full JMH and comparison pass in 5m56s, 21 measured configurations:
MovementBench.step600 24.904 us/op; TerrainEditBench.rebuildOnePage 515.312 us/op against
the documented conservative two-grid profile 481.023; both-class PageBuilder 112.663 us/op.
The 15% limit and every other existing score/budget remain unchanged (ADR-0019).
Absolute D is 0.427499 ms p99 / 2 ms; idle ratio 0.218951 / 0.30, valid foreign load.
See testing/data/M5-T2-recovery-m4pro.json for control, initial regression and final evidence.
These local results do not declare the separate historical Linux CI investigation closed.

### T3a — model slice checkpoint (2026-10-26, task not complete)

ShotAim, ShotStore and standalone ShotSystem are implemented with deterministic lead and
Gaussian spread, complete handles, frozen damage, body-circle/height collision, ordered
single-use impacts and immutable store snapshots including allocator history. Empty and
in-flight-only due checks avoid unnecessary temporary collections. The model is not yet
registered in MatchCore or included in MatchSnapshot/codec/StateHasher; T3a remains unchecked.

The exact 500-shot binomial test uses committed canary seed 7, shooter 1048576, launch tick 2,
IDs 1–500, distance 20, spread 2.25 and radius 0.35. The preselected alpha 0.001 interval is
99–164; an independent SplitMix64/Box-Muller calculation confirms 126 hits and the first
32 outcomes MHMMMMMMMHMMMMMMHMMMMHMMMMHMMMMH. Exactly representable pierce 1/256 yields
0.4921875 health damage in the golden regression; target survival and full packet preservation
are tested. No bounds or seeds were adjusted to match observed results.

The model slice's final verify verifySlow verifySoak passes in 4m26s with 1,952 cases,
no failures/errors/skips. The session stops on the implementation worker's usage limit after
pushing this green step. Next: state/scheduler, optional nested-Play codec and non-pristine
hash integration, in-flight/used-empty continuation canaries and final JMH. T3b must build
release positions after MOVEMENT to keep constant-velocity lead aligned with COMBAT impact.

### T3a — integrated analytic lifecycle (2026-10-26)

The owned store runs in COMBAT after MOVEMENT and before damage application. Nested Play
stores optional immutable shot data and allocator history, preserving the outer 16-field codec,
schema 2, old constructors, pristine JSON and released journal hashes. Used-empty relaunch,
in-flight continuation and restored dead-shooter impacts have real pipeline tests.
Non-pristine hashing mixes every frozen field and bonus token with primitive loops.

Final verify verifySlow verifySoak passes in 4m 39s: 1,958 cases, no failures/errors/skips.
Full JMH passes in 7m 02s with all 25 configurations present. Active 300-shot hashing is 32.394 us
(0.223 B/op profiler background, within the existing allocation gate); a 60-shot impact+damage
volley among 300 fighting infantry takes 5.348 us preferred, 6.270 us collateral, 0.827 us miss.
The four new cases receive first profiles; all 20 existing metrics and thresholds are unchanged.
Evidence: testing/data/M5-T3a-analytic-shots-m4pro.json. CMB-04 remains in progress for
T3b orders/data, T3c presentation and T14's network-budget criterion.
