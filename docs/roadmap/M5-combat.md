# M5 · Combat

Feature cards of milestone M5. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it.

## Milestone

**Goal.** Deepen combat into readable counters (archers with analytic projectiles, horsemen,
stances, focus fire, high ground), give raids their classic answer (garrison and the town bell,
with civic-centre arrows that grow with the garrison), and an AI that fights by the same rules.

**The owner plays.** "Battle at the ford": 60 against 60 (spearmen, archers and horsemen) on the
proving ground. Archers on the hill outrange those below; attack-move, focus fire and stances
decide the fight. Horsemen raid the workers: the owner rings the town bell, the workers vanish into
the civic centre, its arrows (one more per garrisoned unit) break the raid, and "all clear" sends
everyone back to work.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M5 cards passes, except deferred CMB-08's, which join the
   gate only if CMB-08 is revived.
2. L1, 200 fixed seeds, equal cost: spearmen beat horsemen, horsemen beat archers, and archers
   beat spearmen when they start at range, each in at least 170 of 200; archers on a 6-block hill
   beat equal archers below in at least 170 of 200. The 20-seed L3 duels are smoke tests with no
   win-rate threshold.
3. L1: ranged hits reproduce golden results for fixed seeds; every stance behaves as its table
   says.
4. L3: the bell brings every worker within 64 blocks inside within 10 s, and all clear returns each
   to its previous node within 5 s.
5. L6 (PERF-08): 300 units fighting for 5 minutes within A–E; the 2× stress pass holds C p95 ≤
   50 ms.
6. Consent batch: the M5 goldens and PERF-14's battle scene.

**Acceptance** (owner): the owner wins and loses the battle at least once and scores the
questionnaire, compared with M2 and M3; no score of 2 or less.

**Retires.** F-R2/A-R8 and F-R13/A-R15 under combat load; F-R6 for tactics.

**Size and critical path.** 14 cards (4 S, 9 M, 1 L), at most 41 card-days, deferred CMB-08 (M)
included; no card depends on it. Critical path: CMB-04 → CMB-05 → UNIT-07 → AI-03 (19 card-days).

---

## Cards

### Combat

#### CMB-04 · Ranged combat and analytic projectiles
- **Value:** Arrows fly, lead their targets and can miss, without lagging the server (P4).
- **In scope:** architecture §6.7: analytic projectiles: flight time, aim at the predicted
  position, a Gaussian spread from the stateless RNG that grows with distance, the hit resolved at
  landing; no projectile entities; batched `fx` launch and impact events to the `SeesCell`
  audience (shown when either end is visible); bow-shot sounds.
- **Out of scope:** splash damage and minimum range (CMB-15); high ground (CMB-16).
- **Acceptance:**
  1. Fixed seeds reproduce golden hit and damage results exactly (L1).
  2. Over 500 seeded shots at a stationary target, the hit rate stays within the binomial bound of
     the model's expectation, and spread grows linearly with distance (L1).
  3. Moving targets are hit less often than stationary ones at long range (L1).
  4. A volley in a GameTest damages its target as the L1 run predicts (L3).
  5. Launch and impact `fx` with 300 units fighting fit budget E (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** CMB-01, NET-01 · **Size:** L · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-28 — M5 T3a–T3c.2: `ShotTest`/`ShotAimTest` goldens and binomial bound (1–2, L1), `MovingShotTest` (3, L1), `combattests.archer_volley_replays_as_the_core_predicts` (4, L3); M5 T14: `perftests.battle_soak` budget E 9.2 KB/s a viewer (5, L6)

#### CMB-05 · Stances, targeting, chase and flee
- **Value:** Units fight on their own the way you told them: hold the line, defend an area or
  charge, while workers run (P3, P4).
- **In scope:** architecture §7.5: stances as data: aggressive (CMB-02's default), defensive (leash
  at attack range plus half vision), stand ground, passive (civilians flee 12 blocks), violent;
  automatic targeting by class preference through the spatial index at the unit's think cadence;
  chase and leash (a chase ends when the target leaves vision, and defensive units return to their
  spot); the FLEEING state; the `SetStance` command.
- **Out of scope:** formations (ORD-06).
- **Acceptance:**
  1. A stance table drives seeded scenarios: each stance engages, holds, returns or flees as
     specified (L1).
  2. Target preferences per class are followed (L1).
  3. A chase stops when the target leaves vision, even under an explicit order (L1).
  4. One GameTest per stance family confirms its L1 behaviour (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-02, CMB-04, CORE-08 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-27 — M5 T5a–T5b: `StanceTest.theTable` (per stance), `StanceTest.targetPreferences`, `StanceTest.aChaseEndsOutOfVision` (1–3, L1); `combattests.stance_aggressive`, `stance_defensive`, `stance_stand_ground`, `stance_passive` (4, L3; violent equals aggressive, spec §1.6 row 12); `FleeingTest`, `SetStanceRuleTest`, `StancePersistenceTest`, `CommandCardTest` stance buttons; spec §1.6 rows 11–12

#### CMB-07 · Civic centre arrows
- **Value:** Your civic centre defends itself, so an early raid is not instantly fatal (P4).
- **In scope:** the civic centre shoots arrows at enemies in range, with count, interval and damage
  from data, using CMB-04's projectiles and target preferences. One arrow more per garrisoned unit
  comes with BLD-11.
- **Out of scope:** towers (BLD-17, BLD-12).
- **Acceptance:**
  1. Arrow count and damage per second equal the balance sheet exactly (L1).
  2. Only enemies are targeted (L1).
  3. The civic centre breaks a small raid in a GameTest as the L1 run predicts (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-04, BLD-08 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-27 — M5 T9: `TownArrowsTest.countAndDpsAreTheSheets` (3 arrows every 80 ticks, damage = landed arrows × the sheet's 8 pierce after resistance) (1, L1), `TownArrowsTest.onlyEnemies` (2, L1), `combattests.the_centre_breaks_a_small_raid` with its headless replay (3, L3)

#### CMB-08 · First-person combat (if D-08 chooses it)
- **Value:** In first person you can fight beside your soldiers (P2).
- **In scope:** the fighting avatar of D-08: the avatar's vanilla attacks routed into core damage
  as `ExternalDamage`; units may target the avatar, which stays a vision source and an obstacle;
  units hitting players and vanilla mobs through the target's own `hurt` with the Strategos damage
  types `unit_hack`, `unit_pierce` and `unit_crush`, tagged `minecraft:bypasses_cooldown`, and a
  `LivingIncomingDamageEvent` handler that zeroes post-attack invulnerability; death and respawn at
  the civic centre after a delay; no block editing in the match. Deferred if D-08 keeps the
  observer.
- **Out of scope:** the avatar as a hero with auras (CMB-14).
- **Acceptance:**
  1. A player's sword hit on an enemy unit applies core damage exactly once (L3).
  2. 20 units hitting one vanilla mob deal 100 % of the formula (L3).
  3. A killed avatar respawns at its civic centre after the configured delay (L3).
  4. The avatar still cannot break or place blocks in the match (L3).
  5. The damage types are registered, tagged and listed in the inventory (L2).
  6. The owner fights beside the army for 5 minutes with no desync (L7, acceptance).
- **Tests:** L0, L2, L3, L5, L7 · **Depends on:** CAM-05, CMB-01, D-08 · **Size:** M ·
  **Tier:** T0
- **Status:** deferred (D-08 chose the observer avatar; revisit after the M2 playtest)

#### CMB-16 · High ground
- **Value:** Real 3D terrain matters: archers on a hill outrange and outhit those below (P1, P4).
- **In scope:** a modifier source with two data knobs, a range bonus by height difference (as in
  0 A.D.) and a damage bonus from above (as in AoE II), with starting values from the balance
  sheet; the bonus in the tooltip breakdown. The core already knows every floor height.
- **Out of scope:** line of sight blocked by terrain.
- **Acceptance:**
  1. Range and damage by height follow the formula (L1).
  2. Archers on a 6-block hill beat equal archers below in at least 170 of 200 seeded duels (L1).
  3. The bonus appears in the tooltip breakdown (L1).
  4. A GameTest on a hill shows the longer reach (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-04, DATA-02 · **Size:** S · **Tier:** T2 (pulled
  into the MVP)
- **Status:** done
- **Done:** 2026-10-27 — M5 T4: `HighGroundTest` (1, L1), `CommandCardTest.theArcherBreakdownNamesTheHighGroundSource` (3, L1), `combattests.high_ground_reaches_farther` (4, L3); M5 T7: `DuelTest.archersOnAHillBeatArchersBelow` 188 of 200 on a six-block plateau, flat control 90 (2, L1)

#### CMB-17 · Combat readability: attack lines and the under-attack flash
- **Value:** You always see who attacks whom (P2).
- **In scope:** attack lines for the selection from `unit_detail` targets; an under-attack flash on
  own units from `unit_hud` state bits; drawing through the extract-then-draw overlay.
- **Out of scope:** alerts (UI-12, UI-21).
- **Acceptance:**
  1. Lines and flashes come only from data the viewer may receive (L1, view-model tests).
  2. Extracting lines and flashes for 300 units takes under 0.3 ms (L6, JMH); PERF-14's battle
     scene confirms budget F.
  3. Goldens (L4, consent).
- **Tests:** L0, L1, L4 (consent), L6 · **Depends on:** CMB-06, UI-02 · **Size:** S · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-28 — M5 T12: `AttackLinesTest.onlyWhatTheViewerMayReceive` (1, L1), `AttackLinesBench.extract300` 0.43 µs (2, L6); M5 T17: PERF-14's battle scene `rts_frame_battle`, 202 on screen: frame p50 5.03 / p99 10.56 ms, units 3.55, ours 0.06, client tick p50 0.96 (Sodium 4.04 / 8.90, 3.18, 0.05, 0.82) (2, L6); `m5_goldens` attack_lines (3, L4)

#### ORD-05 · Focus fire and force attack
- **Value:** Fine control of a fight: focus one target, spread fire, or attack anything on command
  (P3).
- **In scope:** for group attack orders, spreading targets against focusing them (a modifier key);
  force attack on any entity or building, own ones included; target priority markers.
- **Out of scope:** patrol and guard (ORD-07).
- **Acceptance:**
  1. A focus order makes every ranged unit in range shoot the target (L1).
  2. A spread order splits attackers within one per target of the even split (L1).
  3. Force attack works on an own building (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ORD-04, CMB-05 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-27 — M5 T8: `FocusTest.aFocusMakesEveryArcherInRangeShootIt` (1, L1), `FocusTest.aSpreadIsWithinOneOfEven` (7 n, k pairs) and the re-pick test (2, L1), `combattests.force_attack_on_an_own_building` (3, L3); `ForceAttackTest`, `FocusTargetsTest`, the focus ring; Ctrl forces, Alt spreads

### Units

#### PATH-11 · Mobility classes and footprints
- **Value:** Horsemen and later siege engines path through gaps that fit them and avoid those that
  don't.
- **In scope:** mobility classes (infantry, large) with clearance in surfaces and components per
  class; an avoidance radius per class; the large class for horsemen now and rams in M6.
- **Out of scope:** water (PATH-14).
- **Acceptance:**
  1. A 1-block gap is open to infantry and closed to large units (L1).
  2. Reachability per class equals a flood fill (L1).
  3. 30 horsemen cross the proving ground with no ladder rung above repath (L3).
- **Tests:** L0, L1, L3 · **Depends on:** PATH-06, PATH-09 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-27 — M5 T2a–T2b: `MobilityTest`, `LargeMovementTest`, `LargeIntegrationTest` (1–2, L1); M5 T6: `maptests.thirty_horsemen_cross` — 30 horsemen arrive, ladder rungs [0,16,0,0,0,0] (repath only), longest stuck 50 ticks, 1,254 ticks (3, L3); spec §1.6 row 3

#### UNIT-06 · Mounted units
- **Value:** Cavalry looks and moves like riders on horses, yet costs about as much as one foot
  soldier.
- **In scope:** the `strategos:unit_mount` type (1.4 × 2.0, tracking range 10, interval 3); one
  entity per mounted unit with a composed renderer: a horse body from the vanilla mesh
  (`HorseModel.createBodyMesh`) in our own model class plus a humanoid rider; the large mobility
  class; gallop and attack animations; no passenger entities; the inventory entry.
- **Out of scope:** the cavalry roster (UNIT-07).
- **Acceptance:**
  1. A mounted unit is one entity on the server (L3).
  2. The type's settings match ADR-0011 (L2).
  3. Server cost per mounted unit stays within 20 % of a foot unit (L6).
  4. Rider and horse render together at every camera height (L4, consent).
- **Tests:** L0, L2, L3, L4 (consent), L5, L6 · **Depends on:** UNIT-03, PATH-11 · **Size:** M ·
  **Tier:** T0
- **Status:** done
- **Done:** 2026-10-28 — M5 T6: `bodytests.a_horseman_is_one_entity` (1, L3), `UnitMountTypeTest` (2, L2), `perftests.mounted_cost` 1.162 ≤ 1.20 (3, L6); M5 T17: `m5_goldens` rider_low, rider_default, rider_high on macos and macos-sodium (4, L4)

#### UNIT-07 · Combat roster and counters
- **Value:** Three soldiers with clear roles and counters: spearmen stop horsemen, horsemen run down
  archers, archers shoot spearmen before they close in (P4).
- **In scope:** the spearman gains its bonus against cavalry (×2.5 as a starting relationship);
  new templates for the archer (ranged, the vanilla bow) and the horseman (fast melee cavalry,
  raider and scout); stats from the balance sheet, with iron in soldier costs per DATA-04's sink
  check; silhouettes readable from the camera through held items and shields.
- **Out of scope:** siege (CMB-09); civilisation-specific units (CIV-02).
- **Acceptance:**
  1. Each counter wins at equal cost in at least 170 of 200 seeded L1 duels (L1).
  2. Every unit has a matchup it wins and one it loses in the duel matrix (L1).
  3. 20 seeded L3 duels per counter run as a smoke test with no win-rate threshold (L3).
  4. The owner recognises each role from its silhouette at 60 blocks (L7, acceptance).
- **Tests:** L0, L1, L3, L7 · **Fixtures:** duel seed sets · **Depends on:** CMB-04, CMB-05,
  UNIT-06, DATA-04 · **Size:** M · **Tier:** T0
- **Status:** in_progress

### Defence

#### BLD-16 · Garrison and the town bell
- **Value:** The classic answer to a raid: ring the bell, and the workers vanish into the civic
  centre until the danger passes. Minecraft already teaches the gesture: villagers hide when a bell
  rings (P3, P4).
- **In scope:** a Garrison order; the civic centre garrisons workers and soldiers by class rules in
  data (houses and towers later, by data); garrisoned units have no body (`BodyMode.NONE`) and leave
  at the door; a "Ring the bell" button on the civic centre's card sends every own worker within 64
  blocks to the nearest building with room as a `PUSH_FRONT` order, with the bell sound
  (`SoundEvents.BELL_BLOCK`, as `fx`); "All clear" finishes those orders, so each worker resumes
  what it was doing (architecture §7.4's queue semantics); in first person, using the bell block of
  the civic centre's blueprint (`PlayerInteractEvent.RightClickBlock`) rings the bell; capacity and
  allowed classes. Healing inside, ejection below 10 %, capture regeneration and rally-to-garrison
  stay in BLD-10 (M9).
- **Out of scope:** units on walls (BLD-14).
- **Acceptance:**
  1. Capacity and class rules hold (L1).
  2. Garrisoned units keep their handle, HP and orders through garrison and release (L1).
  3. After the bell, every worker within 64 blocks is inside within 10 s (L3).
  4. After all clear, each worker is back on its previous node within 5 s (L3).
  5. Garrisoned units have no body, so they cost no entity tick (L3).
  6. A first-person mock player using the bell block rings the bell (L3).
- **Tests:** L0, L1, L3, L5 · **Depends on:** BLD-08, CORE-06, ORD-02 · **Size:** M · **Tier:** T1
  (pulled into the MVP)
- **Status:** done
- **Done:** 2026-10-27 — M5 T10a: `GarrisonTest.capacityAndClasses` (1, L1), `GarrisonTest.handleHpAndOrdersSurvive` (2, L1), `GarrisonPersistenceTest`; M5 T10b: `garrisontests.the_bell_brings_them_in` (all inside 82 ticks after the ring, limit 200) (3), `garrisontests.all_clear_returns_them` (back on the node 93 ticks after, limit 100) (4), `garrisontests.garrisoned_units_have_no_body` (5), `garrisontests.the_bell_block_rings` (6), all L3; `BellTest`

#### BLD-11 · Arrows per garrisoned unit
- **Value:** Garrisoning turns the civic centre into a real defence (P4).
- **In scope:** one extra arrow per garrisoned unit, from data, for the civic centre, and for
  towers once they garrison (BLD-12).
- **Out of scope:** towers themselves (BLD-17, BLD-12).
- **Acceptance:**
  1. The civic centre fires base plus garrisoned arrows (L1).
  2. Damage per second equals the balance sheet (L1).
  3. A garrisoned civic centre breaks a horseman raid in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-16, CMB-07 · **Size:** S · **Tier:** T1 (pulled
  into the MVP)
- **Status:** done
- **Done:** 2026-10-27 — M5 T11: `TownArrowsTest.basePlusGarrisoned` (0/1/5/20 inside → 3/4/8/23 arrows) (1, L1), `TownArrowsTest.dpsWithAGarrison` (2, L1), `garrisontests.a_garrisoned_centre_breaks_a_horse_raid` (6 horsemen; L1: the bare centre leaves 5 alive, ten inside break it in 1,483 ticks) (3, L3)

### The opponent and the gate

#### AI-03 · AI battle tactics
- **Value:** An opponent that fights sensibly: it focuses weak targets, keeps archers behind
  spearmen and retreats when outnumbered (P5).
- **In scope:** composition from the counters; focus on wounded or high-value targets; ranged units
  kept behind melee; retreat below a strength ratio; quality knobs per difficulty.
- **Out of scope:** attack planning across the map (AI-12); ringing the bell (AI-04).
- **Acceptance:**
  1. In the battle scenario the AI focuses fire and retreats below its ratio (L1).
  2. Against a horseman-heavy army the AI shifts its training toward spearmen within 2 minutes
     (L1).
  3. Tactics stay within the `AiBudget` (L1).
  4. An L3 battle sample reaches the hash of its L1 run (L3).
- **Tests:** L0, L1, L3 · **Depends on:** AI-02, UNIT-07, ORD-05 · **Size:** M · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-28 — M5 T13: `BattleBotTest.focusesAndRetreats` (10 v 6 share one most-wounded target; 6 v 14 retreat at contact and end within 12 blocks of base) (1, L1, slow; the battle scenario of T15 reuses the bot), `BattleBotTest.shiftsToSpearmenAgainstHorse` on the shipped templates (12 spearmen and no others in the two minutes after 20 horsemen appear) (2, L1, slow), `BattleBotTest.withinTheBudget` (326 of 5,000 at 80 v 80) (3, L1), `aitests.battle_seed_replays_to_its_hash` (4, L3); `battle.json`

#### PERF-08 · Battle perf gate
- **Value:** Big battles stay smooth on the server, the network and the screen.
- **In scope:** the M5 row of the budget matrix: 300 units fighting (melee, archers, horsemen) with
  the civic centre's arrows and a garrison, for 5 minutes; A–E; per-unit normalisation; a 2× stress
  pass; cost per combat system (targeting, projectiles, damage); PERF-14's battle scene with
  consent.
- **Out of scope:** multiplayer bandwidth (PERF-10).
- **Acceptance:**
  1. Budgets A–E hold for 5 minutes of fighting (L6).
  2. Per-unit p95 of the unit-driven buckets grows at most 15 % over M4's baseline (L6).
  3. The stress pass holds C p95 ≤ 50 ms (L6).
  4. The report lists the cost per combat system (R).
- **Tests:** L6, R · **Depends on:** UNIT-07, CMB-07, PERF-07 · **Size:** S · **Tier:** T0
- **Status:** done
- **Done:** 2026-10-28 — M5 T14: `perftests.battle_soak` (320 units, 5 min at 20 t/s: A p50 1.23 / p99 4.13 ms, C p95 7.85 ms, D p99 0.47 ms, E 9.2 KB/s a viewer) (1, L6); per-unit A p95 7,832 ns = 1.139 × M4's 6,878 (2, L6); `perftests.battle_stress` (640 units) C p95 9.35 ms ≤ 50 (3, L6); the report's combat block: targeting p95 90 µs, volleys 15 µs, shots 56 µs, damage 51 µs a tick (4, R); `BattleScene`, `BattleSceneTest`
