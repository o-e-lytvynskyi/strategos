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

**Implementation started 2026-10-24**, on the owner's explicit go-ahead, in the isolated
`codex/m5-combat` branch. The owner's target is a MacBook M5; this session's Windows x64
environment, reproducible commands, results and remaining macOS checks are recorded in
[M5-windows-x64.md](../testing/M5-windows-x64.md). The original planning text above is historical.

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
| PATH-11 | T2 | 1 `MobilityTest.aOneBlockGapIsOpenToInfantryAndClosedToLarge` (L1) · 2 `MobilityTest.reachabilityPerClassIsAFloodFill` (L1) · 3 `pathtests.thirty_horsemen_cross` (L3) |
| CMB-04 | T3 | 1 `ShotTest.fixedSeedsReproduceTheGoldenResults` (L1) · 2 `ShotTest.theHitRateStaysInTheBinomialBound` (L1) · 3 `ShotTest.movingTargetsAreHitLessAtLongRange` (L1) · 4 `combattests.a_volley_damages_as_l1_predicts` (L3) · 5 `perftests.battle_soak`'s budget E (L6, T14) |
| CMB-16 | T4 | 1 `HighGroundTest.rangeAndDamageFollowTheFormula` (L1) · 2 `DuelTest.archersOnAHillWin` (L1, slow) · 3 `HighGroundTest.theBonusIsInTheBreakdown` (L1) · 4 `combattests.a_hill_reaches_further` (L3) |
| CMB-05 | T5 | 1 `StanceTest.theTable` (L1) · 2 `StanceTest.targetPreferences` (L1) · 3 `StanceTest.aChaseEndsOutOfVision` (L1) · 4 `combattests.stance_*` per family (L3) |
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
| Archer | 60F 40W 20I | 50 | 6 pierce | 20 | 16 / 25 | 0.2 | 1/1/1 |
| Horseman | 80F 40W 30I | 120 | 5 hack | 1.5 | 8 / 18 | 0.34 | 3/2/2 |

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

T1 → T2 (PATH-11) → T3 (CMB-04) → T4 (CMB-16) → T5 (CMB-05) → T6 (UNIT-06) → T7 (UNIT-07) → T8 (ORD-05)
→ T9 (CMB-07) → T10 (BLD-16) → T11 (BLD-11) → T12 (CMB-17) → T13 (AI-03) → T14 (PERF-08) → T15 (the
battle scenario) → T17 (the consent batch) → T18 (close). T16 is reserved for a row a task adds.

### 7.2 Tasks

#### T1 · Health check from M4's close
- Split `RtsHud` (882 lines), `PerfTests` (856) and `MatchState` (836) along their seams; a scene's
  failed check fails its run; Linux CI's goldens re-recorded when the scenario job next runs by hand.
- **Verification:** `verify verifySlow`.

#### T2 · PATH-11
- §2.1. **Verification:** the card's checks; `PageBuilderBench` within its gate.

#### T3 · CMB-04
- §2.2; the archer template ahead of T7 for the tests. **Verification:** the card's checks 1–4.

#### T4 · CMB-16
- §2.3. **Verification:** checks 1, 3, 4 (2 with T7's duel harness).

#### T5 · CMB-05
- §2.4; `SetStance` (25). **Verification:** the card's checks.

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

- [x] T1 (2026-10-25) · [ ] T2 · [ ] T3 · [ ] T4 · [ ] T5 · [ ] T6 · [ ] T7 · [ ] T8 · [ ] T9 · [ ] T10 · [ ] T11
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
| «начинай M5» | received 2026-10-24 | Settled; implementation authorised in `codex/m5-combat` |
| The goldens and the looks | T17 | Accepted |
| The battle playtest and the questionnaire | after the demo | Accepted |

## 11. Implementation results

### T1 — health check (2026-10-25)

Split the three oversized classes along their existing seams: resource discovery and
external resource losses (`MatchResourceDiscovery`), card presentation (`HudCardPresentation`),
and long perf scenes (`PerfSoaks`). The annotated perf entry points and test names stay
unchanged. `scenarioDiff` now rejects a failed scene verdict even when its pixels agree
or its platform has no goldens, proved by its extended selftest. The roadmap reader
now uses UTF-8 explicitly and rejects empty input; three Python regression tests pass.

`verify verifySlow` passed in 18m 32s on the documented Windows x64 environment:
1298 core, 3 slow core, 200 determinism soak, 73 journal replay, 95 Minecraft unit, 12 architecture, 211 GameTest, 13 slow GameTest; no failures. No baselines, thresholds or images were changed. Linux goldens
remain to be reviewed when its scenario job next runs; this session did not re-record them.
