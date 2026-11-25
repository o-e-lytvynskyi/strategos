# M10 — Civilisations foundation and depth: implementation spec

Cards: [roadmap/M10-second-civilisation.md](../roadmap/M10-second-civilisation.md#cards), reshaped and approved by the
owner on 2026-11-25 («сейчас сделать 1 дефолтную нацию, начальную, самую простую и понятную по типу того, что у нас
уже есть, и так же сделать фундамент для того, чтобы правильно и бестпрактисно, без переписывания ядра мы потом могли
добавлять экстравагантные нации с отличающимся геймплеем. в остальном, следуй уже намеченым планам»). Developer:
`o-e-lytvynskyi` ([branch file](../branches/o-e-lytvynskyi-m10.md)). Base: `main` after the M10 claim, with M9's branch
merged in (M9 is handed in, [PR #58](https://github.com/o-e-lytvynskyi/strategos/pull/58)).

## 0. Read this first

### 0.1 Authority

As M9 §0.1: the architecture and the ADRs decide *what*, this spec *how M10 builds it*; the cards define scope and
acceptance; ROADMAP.md holds the statuses; a disagreement is a §1.6 row. The civilisation layer is
[ADR-0028](../adr/0028-civilisations-and-rule-seams.md) (Proposed in T2, Accepted at T9).

**M10 stacks on M9.** Until #58 merges, this branch carries M9's commits; `origin/main` is merged in when it lands.
**M7 is not merged** and is not needed: ART-04 moves to M14 with CIV-02; the per-player views of the new content
(the technology screen, ability buttons, hotkeys) go over today's single gateway, with `AFTER-M7` where NET-03's
per-player state replaces it. **M8** (`lev1pes`, foundations) is met at one point: MATCH-16's night radius and light
sources are inputs to FOW-01's `VisionSystem`; team vision (FOW-02) is not touched, and `AFTER-M8` marks where shared
vision must take the night radius. M10 never edits M7's or M8's files.

### 0.2 Working rules for every task

M9 §0.2 holds, with these changes:
- **The default civilisation is invisible.** Every task of the civilisation chain (T2–T9b) keeps every pinned
  state hash, golden and replay of M0–M9 as it is: the soak seeds' hashes, `:core:openingBalance`'s results, the
  journals under test resources. A changed state hash in that chain is a bug, never a re-pin. The content SHA holds
  too: the default civilisation is *implied* — while a set's civilisations are exactly the built-in default, its
  canonical form has no `$civs` entry, so `TemplateSetTest`'s pinned versions and every saved template-set hash stay
  as they are (§2.1).
- **Windows.** Every L4 check of M10 runs in the consent batch (T30).
- **Order.** T1 first; then the civilisation chain T2–T9b, because every later content task adds to the default
  civilisation's roster; then the depth cards in the order of §7.1. The AI (T25), the balance matrix (T26) and the
  perf gate (T27) come last.
- **Long runs** off the working copy (`tools/longrun.sh`), as in M9.

### 0.3 Code conventions

M9 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Packages | Content: `core.content.CivDef`, `RawCiv`, `StartKit`, `CivRoles`; binding: `core.sim.civ.FactionCivs` (T3); rules: `core.sim.civ` (`CivRules`, `RuleCatalogue`, `RuleRef`, the eight seam interfaces and their variants, T5); nothing outside `core.sim.civ` and `core.content` reads a civilisation id (ArchUnit, T8) |
| Ids | `CivId` (a namespaced string, interned like `TemplateId`); the default is `strategos:default` |
| Config | `MatchConfig` gains `civs` (`List<CivId>`, index = faction), `optionalFieldOf("civs", List.of())`, empty = every faction plays the default; a back-compat constructor keeps every existing call site |
| Hash | `StateHasher` mixes the civilisation ids (strings, not indices) only when some faction plays a non-default civilisation (the `PlayRules` pattern), so every M0–M9 hash holds |
| Modifier sources | Faction scope: technologies `-1 - netIndex`, `AiHandicap.SOURCE = Integer.MIN_VALUE`, `CivBonuses.SOURCE = MIN_VALUE + 1`, `NightFactor.SOURCE = MIN_VALUE + 2` (MATCH-16) — derived, never saved, re-derived at the three `AiHandicap` sites (`Snapshots` restore, `TemplateSwap`, `AiRules`). Unit personal scope: `Ranks.SOURCE = MIN_VALUE`, an ability's timed modifiers `MIN_VALUE + 1 + abilityNetIndex`; T10 lists the personal sources already in use first |
| Rule ids | A variant's behaviour never changes under its id; a changed behaviour is a new id (`direct@2`) and the old stays for old journals |
| Snapshot | `MatchSnapshot.Play` gains, each optional and saved only when non-empty: `experience` and `ranks` (CMB-11), `abilities` (cooldowns and timed modifiers, UNIT-10), `formations` (ORD-06), the standing orders' anchors (ORD-07), `loot` in each faction's treasury (CMB-13); `SchemaVersion` stays 3 |
| Commands | The next free wire id is 35: `UseAbility` 35, `Patrol` 36, `Guard` 37, `FormationMove` 38, `SelectCivilisation` none (setup is not a command: it is `MatchConfig`) |
| Payloads | `SkirmishSetup` gains per-slot civilisations (optional in its wire codec); the template set's client view carries `CivDef`'s client part (names, phase names, roles, roster); `unit_detail` gains rank, experience and ability cooldowns; every cap pinned in `WireCodecSizeTest` and `PayloadRoundTripTest` |
| Template paths | `Abilities/<id>/{target,range,cooldown,autocast}` with `Abilities/<id>/Cost/resources/<r>` and `Abilities/<id>/effects`; `Promotion/{value,thresholds}`; `Loot/resources/<r>`; `Vision/night` (a multiplier at night) and `Vision/light` (a radius that holds at night); `Fauna/{wild,flee}`; constants in `CorePaths`, provenance in `provenance.json` |
| Civilisation data | `data/<ns>/strategos/civs/<id>.json`: `name`, `phases`, `roster.templates`, `roster.techs`, `roles`, `start`, `bonuses`, `rules`, `ai` (§2.1) |
| Balance | Every new number has its provenance note and its sheet row; the sheet gains a section per civilisation |
| Relations | As M9: every friend-or-foe question through `Allies` |

### 0.4 Engine names to check (`api-lookup`)

- CAM-06 (T23): `GameRenderer#getProjectionMatrix(double)`, `Frustum#offsetToFullyIncludeCameraCube(int)`, the mixin
  config entries and the inventory format of ADR-0020; Sodium's and Iris's frustum paths (L4 answers those).
- MATCH-16 (T24): `ServerLevel#setDayTime(long)`, `GameRules.RULE_DAYLIGHT`, `MinecraftServer` time
  synchronisation to players (`ClientboundSetTimePacket`), the client's sky-darkness hook for a tint fallback.
- UI-17 (T22): `KeyMapping` registration through `RegisterKeyMappingsEvent` (already used by `RtsKeys`) for any new
  binding category.
- ORD-06 (T19): the Alt modifier in the client's mouse handler (`Screen.hasAltDown()` or the RTS input's
  `Modifiers`).

## 1. Goal, demo and scope

### 1.1 Goal

The game gets **one civilisation, the default** — today's content, the simplest and the most readable — and **the
foundation for many**: each faction plays a civilisation that is content (roster, roles, start kit, bonuses, phase
names, AI profile) and that selects its gameplay rules through seams in the core, so that later civilisations with
gameplay of their own (no houses, no drop-off, buildings that raise themselves, other victory paths) are data plus,
at most, a new variant class — never a rewrite of a system or of the AI. A probe civilisation, in the tests only,
proves it. On top of that, the depth planned for M10: the City phase, the full technology tree and its screen,
abilities, ranks, healing, loot, formations, patrol and guard, hunting, the full hotkey set, an optional orthographic
view and the first Minecraft twist, day and night.

### 1.2 Slices

1. **Civilisations as content** (CIV-01): the `civs` kind, the default civilisation, the binding per faction, the
   setup choice, start kits, bonuses, the roster rule (T2–T4).
2. **Civilisation rules** (CIV-05): the catalogue, the eight seams with today's behaviour as defaults, three
   non-default variants, roles instead of template ids, the AI's capabilities, the probe (T5–T9).
3. **Depth for every civilisation**: abilities (T10–T11), ranks (T12), healing (T13), loot (T14), City (T15), the
   technology tree (T16–T17), formations (T18–T19), patrol and guard (T20), hunting (T21), hotkeys (T22), the
   orthographic view (T23), day and night (T24).
4. **The AI and the gates**: the AI on the new content (T25), the balance matrix (T26), the perf gate (T27).

### 1.3 Demo script (L7, the owner)

1. `scripts/playtest.sh`, then a skirmish: the setup screen offers the civilisation per slot (one entry, the default,
   shown as "Settlers"); start against Normal.
2. Reach Town, then City: civic-centre territory grows, its arrows get stronger; open the technology tree screen,
   pick one of a pair (the other locks).
3. Train a healer at the temple; fight, watch veterans gain an insignia; wounded troops heal by the healer and inside
   the temple.
4. Select an army, march it in box, then line formation; give one member its own order (it leaves, the rest re-slot);
   Alt+click a member to select the whole formation.
5. Set a patrol between two points and a guard on a worker; hunt wild goats with horsemen; a kill pays loot.
6. Use the horseman's charge ability; walk the full hotkey set from the key reference.
7. A second match with day and night on: night falls, vision shrinks, torches on buildings light up the fog.
8. Optional: switch to the orthographic view in the options.

### 1.4 Cards, tasks and checks (traceability)

| Card | Tasks | Acceptance → check |
|---|---|---|
| CIV-01 | T2, T3, T4, T9b | 1 `CivDefLoadTest`, `CivLintTest`, the shipped files through the real loader (`ShippedCivsTest`, L2); 2 `CivRosterTest`; 3 `CivBonusBreakdownTest`; 4 `civtests.two_civilisations` (L3: the default against the probe, from the GameTest pack); 5 `DefaultCivCompatibilityTest` (old saves and journals load as the default, pinned hashes hold) |
| CIV-05 | T5–T9b | 1 `SeamDefaultsTest` + the pinned soak hashes; 2 `PopulationFixedTest`, `DeliveryDirectTest`, `ConstructionSelfTest`; 3 `ProbeMatchesTest` (20 seeds, replays); 4 `CivBoundaryArchTest`; 5 `ProbeFixtureTest` (the probe lives in test fixtures only) |
| UNIT-10 | T10, T11 | 1 `AbilityValidationTest`; 2 `AbilityEffectsTest`; 3 `AbilityCardTest`; 4 `abilitytests.charge` |
| CMB-11 | T12, T30 | 1 `ExperienceTest`; 2 `RankModifiersTest`; 3 insignia golden at 40 blocks (consent) |
| CMB-12 | T13 | 1 `HealRateTest`; 2 `HealTargetTest`; 3 `HealFilterTest`; 4 `healtests.healer_heals` |
| CMB-13 | T14 | 1 `LootTest`; 2 `InvariantsTest` with loot; 3 `loottests.kill_pays` |
| TECH-04 | T15 | 1 `CityPhaseTest`; 2 `CityTerritoryTest`; 3 `phasetests.city_bonuses` |
| TECH-05 | T16, T17, T30 | 1 `TechEffectsTest` (generated per technology); 2 `techtests.<family>`; 3 `TechPairsTest`, the sheet's tiers; 4 tree-screen goldens (consent) |
| ORD-06 | T18, T19 | 1 `FormationSlotTest`; 2 `FormationSpeedTest`; 3 `FormationBreakTest`; 4 `formationtests.march`; 5 `FormationLeaveTest`; 6 `FormationSelectTest` |
| ORD-07 | T20 | 1 `PatrolTest`; 2 `GuardTest`; 3 `ordertests.patrol_and_guard` |
| ECO-10 | T21, T27 | 1 `HuntTest`; 2 `FleeStanceTest`; 3 PERF-17's fauna scene |
| UI-17 | T22, T30 | 1 `HotkeyCoverageTest`; 2 `KeyReferenceTest`; 3 idle cycling on screen (consent) |
| CAM-06 | T23, T30 | 1 `OrthoPickingTest`; 2 `EngineSurfaceInventoryTest`; 3, 4 switching and Sodium/Iris (consent) |
| MATCH-16 | T24, T30 | 1 `NightVisionTest`; 2 `LightSourceTest`; 3 `NightReplayTest`; 4 night goldens (consent) |
| AI-09 | T9a, T9b, T25 | 1 `AiCityTest`; 2 `AiProbeTest`; 3 `aisoak` sample hash |
| QA-16 | T26 | 1 `:core:balanceMatrix`; 2 `BalanceMatrixReportTest`; 3 `BalanceMatrixReproTest`; 4 the L3 samples in `verifySoak` |
| PERF-17 | T27, T29 | 1 `perf_m10` A–E; 2 `perf_m10_stress` C p95 ≤ 50 ms; 3 the report's `formation`, `fauna` and `ability` costs |

### 1.5 Moved out of M10, with the reason

| Item | To | Why |
|---|---|---|
| CIV-02 Civilisation 2 | M14 (`deferred (M14)`), beside CIV-03 | The owner's word: one default civilisation now; distinctive civilisations come once the core and the engine are ready |
| ART-04 Civilisation 2 art | M14 (`deferred (M14)`) | Follows CIV-02; it also needed M7's ART-03 |
| D-03 Civilisation themes | needed by M14 (CIV-02) | Themes are chosen with the distinctive civilisations; the default civilisation keeps today's look (D-04) |

### 1.6 Differences resolved

| # | Topic | Resolution |
|---|---|---|
| 1 | M10's goal ("a second civilisation proves the data model") | Reshaped on the owner's word: the default civilisation plus a rule foundation proved by a test-only probe civilisation (CIV-05) |
| 2 | CIV-01's value ("adding one needs no code") | Kept for data differences; rule differences go through CIV-05's catalogue, where a new mechanic is one variant class and its tests, never a system edit |
| 3 | CIV-01 scope | Gains roles, start kits, the roster rule, the default civilisation and a compatibility criterion (old saves and journals); acceptance 4 plays the default against the probe |
| 4 | CIV-05 | New card (M10), size L, depends on CIV-01 |
| 5 | AI-09 acceptance 1–2 | "reaches City with both civilisations" and "trains a unique unit of each" become: reaches City with the default; plays the probe (Town with no houses, no dropsites, no builders) |
| 6 | QA-16 | The matrix keeps its civilisation dimension; with one playable civilisation it is the mirror pairing over levels and start slots, and the 80–120 of 200 band applies to every pairing — in a mirror, to each start slot. Depends on CIV-01 instead of CIV-02 |
| 7 | PERF-17 | "Second-civilisation perf gate" becomes "M10 perf gate": formations of 40, fauna, abilities and the City phase, A–E, the 2× stress pass; depends on CIV-01 instead of CIV-02 |
| 8 | The Done gate and Acceptance | Item 2 follows row 6, item 4 follows row 7; the owner approves the default civilisation's play, not ART-04's look |
| 9 | CIV-04 (M15) | Depends on CIV-05 as well: unique mechanics are its variants |
| 10 | The milestone's name | "Second civilisation and depth" → "Civilisations foundation and depth"; the card file keeps its path |
| 11 | ART-03 (M7) names D-03 | Not M10's card: put to the owner (branch file "For the owner") |
| 12 | Kit frame (T4) | Kits are written for yaw 270 (`first_skirmish`'s slot 0); a quarter turn maps `(dx, dz) → (−dz, dx)` and adds 1 to a building's rotation. Slot 1 (yaw 90) gets exactly its hand-mirrored offsets, but the kit turns its civic centre to rotation 2 where the written list has 0. Both slots play the written lists today, so nothing changes; a kit-played slot 1 shows its centre turned, which mirrors the map |
| 13 | Kit resources (T4) | A kit's starting resources replace the slot's when the kit has any |
| 15 | T5's shapes | A rule reference is `CivDef.Rule` (no separate `RuleRef`); systems reach a faction's rules by `s.civs().rules(f)`, not `MatchState.civRules()`; `PlacementRule` lives in `core.content` beside the pure `PlacementRules`, because `core.client` may not depend on `core.sim`; the load-time lint runs in the mod's `ServerTemplates` (`core.content` cannot see `core.sim.civ`); `PhaseRule` answers both `phase` and `reached(name)`, the default keeping today's exact-researched test; population, delivery and construction are placeholder ids in `RuleCatalogue.DEFAULT` until T6–T7 |
| 16 | The centre class (T5, from T8) | `Placing`, `ClientPlacement` and `ai/Base` read the civilisation's `centreClass` in T5 with `DefeatRule`, so one civilisation never has two centre classes |
| 14 | T8's boundary rule | `MatchConfig`, `StateHasher`, `ScenarioSetup` (kits), the protocol and the HUD (names, phases, the card) hold or read civilisation ids by design; the rule forbids them in the systems' packages (`econ`, `build`, `prod`, `unitai`, `ai`, `combat`, `territory`, `capture`, `garrison`, `vision`, `nav`, `move`, `order`), where behaviour must come from `CivRules` |

### 1.7 Consent plan

One batch (T30), about 25 minutes of windows: the tree screen (vanilla, Sodium), insignia at 40 blocks, idle cycling,
the orthographic switch (vanilla, Sodium, Iris), night goldens (vanilla, Sodium), PERF-14's client pass. Each static
scene two or three times, never more (D-22).

## 2. Design

### 2.1 Civilisations as content (CIV-01, ADR-0028)

- **`RawCiv` → `CivDef`.** `TemplateFiles` reads `strategos/civs/*.json`; `TemplateCompiler` compiles them after
  templates and techs, resolving every id; `TemplateLint` reports unknown keys, unknown templates or techs in the
  roster, a role pointing outside the roster, a role naming no template, an unknown rule id or bad rule parameters, a
  missing role that a selected rule needs (the `house` role is required only under the default
  `PopulationRule`). `TemplateSet` gains
  `civs()` and a `$civs` canonical entry only when its civilisations are not exactly the built-in default (the implied
  default, §0.2). When `$civs` is present, the client view (`TemplateSetView`) carries the full
  canonical civilisation JSON behind a mark like the technologies' `+t` (`+c`); without it the client takes the
  built-in default, so `toSet()` reproduces the server's
  version and a journaled `ContentSwap` keeps passing `InputCodec`'s check; `MAX_BYTES` (256 KB) is measured with
  the default civilisation and TECH-05's technologies (T16).
- **The default civilisation** (`civs/default.json`): `name` "Settlers" (a lang key); `phases` Village, Town, City;
  `roster` the wildcard `*` (every template and technology of the set, so later content tasks add nothing here);
  `centreClass` `CivicCentre`; `roles` from today's content (`centre` civic_centre, `house`, `worker`, `infantry`, `ranged` archer, `cavalry`
  horseman, `siege` ram, `barracks`, `tower`, `farmstead`, `field`, `storehouse`, `wall` palisade, `gate`
  palisade_gate; T13 adds `temple` and `healer` with their templates); `start` today's first-skirmish civilisation
  entities; no bonuses; no rules (all defaults); `ai` the `skirmish` profile. The built-in fallback set holds the same definition, so a set without data
  files still has the default; `ShippedCivsTest` pins the shipped file equal to it.
- **The roster rule.** `Production.trains`, `Placing.listed` and `ResearchRules` add one check, `roster.has(...)` of
  the faction's civilisation (a bitset over template and tech indices, built at binding); the command card hides what
  the roster lacks. The roster belongs to the faction, not the building: a captured building (M9) trains only what
  its new owner's roster has. Admin spawns (`SpawnUnits`, `SpawnBuilding`, a scenario's or a kit's entities) bypass the
  roster on purpose: a scenario may hand any faction anything; the AI acts through commands and is checked. The
  default's roster is the wildcard, so nothing changes.
- **CIV-01's other in-scope items** are data on this frame: house size is a bonus on `Housing/capacity`;
  citizen-soldiers (D-02) are the `Gatherer` class on a soldier template (the probe's infantry has it, T9b); unique
  abilities are roster templates carrying UNIT-10's abilities (T11).

### 2.2 Binding, setup and start kits (CIV-01)

- **`MatchConfig.civs`** (§0.3). `FactionCivs.bind(config, templates)` (T3) resolves each faction's `CivDef` and its
  roster once at match start and again at restore and content swap (a swap that removes a civilisation in use is
  refused, like a template); T5 extends the binding with each faction's `CivRules`.
- **Bonuses** are faction modifiers under `CivBonuses.SOURCE`, applied at bind and re-derived at the three
  `AiHandicap` sites; tooltips show them through `StatBreakdown` with the civilisation's name as the source.
- **Setup.** `SkirmishSetup` gains `civs` per slot (default: the default civilisation); `SkirmishScreen` shows a
  selector per slot (one entry today); `/strategos skirmish … civ=<id>[,<id>]`; `MatchRegistry` writes them into
  `MatchConfig`. The AI profile per slot comes from the civilisation's `ai` (`Skirmishes` today forces `skirmish`).
- **Start kits.** A scenario slot gains `civ` (the civilisation it was written for, default `strategos:default`) and
  its civilisation's own entries carry `"civ": true` (in `first_skirmish`: the civic centre, the workers, the horseman
  and the livestock; deposits such as `iron_mine_large`, which only one slot has, stay unflagged map extras). A slot
  that plays the civilisation it was written for emits its list as written — the identical commands in the identical
  order, which keeps every hash. A slot that plays another civilisation drops its flagged entries and emits that
  civilisation's kit in place of the first of them, and takes the kit's starting resources. Kit offsets are in the
  slot's frame: they turn with the slot's yaw (`first_skirmish`'s two slots differ by 180°, `(dx, dz) → (−dx, −dz)`),
  which T4 checks against today's hand-mirrored offsets. A new scenario may write `kit: true` with no flagged entries;
  the kit then comes first.

### 2.3 The rule catalogue and the seams (CIV-05)

- **`RuleCatalogue`**: an immutable map `RuleId → RuleFactory<S>` per seam kind, each factory with a parameter codec;
  built once in the core (`RuleCatalogue.DEFAULT`); tests may build a wider one. A `CivDef.rules` entry is
  `{seam: {id, params}}`; lint decodes the parameters with the factory's codec.
- **`CivRules`**: per faction, an immutable record of the eight seam instances (T5); `MatchState.civRules()` holds the
  array; systems call `s.civRules().of(f).population()` — an array index, cheap on the hot path.
- **The seams** (ADR-0028's table). Each default is today's code moved behind the interface, not rewritten:
  - `PopulationRule`: the cap and the population part of `Production.step`'s reservation, with its refusal reason
    (one owner: production keeps the cost reservation and `setWaiting`, and asks the rule whether the product fits
    and why not). Default `min(housing + base, max)`, `base` from the civilisation (0), refusal `HOUSING_NEEDED`;
    `fixed{cap}`: `min(cap, max)`, housing ignored, refusal `POPULATION_CAP`.
  - `DeliveryRule`: the seam point is `Gathering.headHome`, where a full carry turns toward a dropsite (every entry
    into GATHER_RETURNING goes through it; T6 first makes it the only entry). Default — head to the nearest accepting
    dropsite, as today; `direct{}`: the full carry is credited to the treasury as `gathered` where the gatherer
    stands, and it stays in GATHERING. Stateless: no new per-unit data (`data0` keeps the remainder, `data1` is
    `Livestock`'s); the conservation invariant holds because `gathered` is the same counter.
  - `ConstructionRule`: two points. `onPlaced(state, site)`: default nothing (a builder's arrival commits the
    foundation, `Constructing`); `self` commits at placement — the world writes and the eviction of units that
    `Construction.commit` does today. `progress(state, site, builders)`: default `r × n^0.7` from builders; `self{rate}`:
    `rate × buildTime⁻¹` with no builder, builders add nothing. The cost is reserved at placement as today.
  - `UpkeepRule.step(state, faction)`: once per economy step; default nothing; a test-only variant pays passive
    income to prove the seam (upkeep, decay and passive income are its later uses).
  - `ResourceRule.uses(faction, resource)`: default — every match resource.
  - `PhaseRule.phase(state, faction)`: default — the highest researched phase ordinal; phase display names come from
    the civilisation.
  - `PlacementRule`: a parameter of the pure `PlacementRules` (its `World`/`Params`), so `ClientPlacement`'s preview
    runs the same variant and `PlacementParityTest` covers it; default — the template's `Placement/territory` and
    today's checks.
  - `DefeatRule.holds(state, faction)`: whether the faction still has what keeps it in play; default — a building of
    its `centreClass`. `VictoryCheck` keeps the per-faction latch (`Outcome`'s `holding`) and asks the rule only under
    `Victory.CIVIC_CENTRE`; `CONQUEST` is unchanged.
  - **Not seams in M10**, named so their later arrival is planned: the production model (what trains where; spawning
    without buildings), the gathering model at a node, the territory model. Each arrives as a seam when a
    civilisation needs it (ADR-0028).
- **Variant state.** M10's variants are stateless. A later stateful variant registers a store through
  `CivRules.stores()`, saved in the snapshot and mixed into the hash by the same code as every store.

### 2.4 Roles and the engine's vocabulary (CIV-05)

- **Template ids out of the core's rules.** The AI's `BuildManager` (`HOUSE`, the civic centre), `ProductionManager`
  (the ram), `Composition` (archer, horseman) and `StrategyScript`'s defaults (worker, infantry) ask the faction's
  roles instead of naming `strategos:building/house` and friends. `VictoryCheck`, `Placing`, `ClientPlacement` and
  `ai/Base` name the class token `CivicCentre`, not a template: they read the civilisation's `centreClass`. `TemplateSet`'s `DEBUG_WALKER`,
  `INFANTRY`, `WORKER`, `BuiltinTemplates`, `PlaceholderBlueprints` and `persist.Migrations` keep their ids (the
  built-in set and old saves).
- **The vocabulary** — the class tokens the core reads (`Gatherer`, `Builder`, `Structure`, `Phase`, `Organic`,
  `Fauna`, `Village` as the implicit phase, …) — is listed in architecture §8.9 with the seam that reads each.
- **`CivBoundaryArchTest`** (core ArchUnit): no class in `core.sim..` outside `core.sim.civ` and `core.content`
  references `CivId` values or `CivDef`; no `TemplateId` string literal in `core.sim..` or `core.client..` outside the
  built-in set and migrations.

### 2.5 The AI on any civilisation (CIV-05, AI-09)

- `AiView.civ()` gives the roles and the AI hints each rule variant declares (`needsHousing` from the population
  rule, `needsDropsites` from delivery, `needsBuilders` from construction; a later variant brings its own hints), and
  every production or placement refusal carries its reason (`HOUSING_NEEDED`, `POPULATION_CAP`, …), which the AI
  reads and backs off from. The managers branch on hints, never on ids: no houses when `!needsHousing`; no dropsite placement or worker distance logic when
  `!needsDropsites`; a foundation is placed and left when `!needsBuilders`.
- Strategy scripts: the civilisation's `ai` names its profile per level; the default keeps today's scripts byte for
  byte.

### 2.6 The probe civilisation (CIV-05)

`core/src/testFixtures/resources/data/strategos/strategos/civs/probe.json`, loaded by `SimHarness` and by the
GameTest pack (`mod/src/gametest` resources) for `civtests.two_civilisations`: `population: fixed{cap: 60}`,
`delivery: direct{}`, `construction: self{rate: 1}`; a roster without the house and the storehouse; its own start
kit (a civic centre, six workers, no house); infantry with the `Gatherer` class (citizen-soldiers, D-02); one bonus
(worker gather rate ×1.1) to exercise the modifier path; its own strategy script. `ProbeMatchesTest`: 20 seeded headless matches of probe against default, each journal replays to
its live hash; the AI with the probe reaches Town. `ProbeFixtureTest` asserts the probe is absent from `src/main`.

### 2.7 Abilities (UNIT-10, architecture §7.6)

- **Data.** `Abilities/<id>` in templates: `target` (none, point, unit, area), `range`, `cooldown` (ticks), `cost`,
  `autocast`, `effects` (a list of `{kind, params}`).
- **Core.** `AbilityDef` compiled with the template; `UseAbility(UnitSet, AbilityId, Target, QueueMode)` (wire 35)
  through a `CommandRule`; `AbilityRules` validates range, cooldown and cost at the tick boundary; `AbilityEffects`, a
  closed registry like `RuleCatalogue`: `Damage` (into `CombatSystem`'s packets), `Heal` (into `HealthStore`),
  `ApplyModifiers` for N ticks (a `TimedModifiers` store over `StatService.addPersonal`/`removePersonal`, with expiry
  at the tick boundary, saved and hashed), `Spawn`, `CaptureStrength`, `Teleport`. Cooldowns in an `AbilityStore`.
- **Game.** Command-card buttons come from the template's abilities (icon, hotkey slot, cooldown sweep); the showcase
  is the horseman's `charge` (`ApplyModifiers` walk speed ×1.5 and melee damage ×1.2 for 100 ticks, cooldown 600).

### 2.8 Ranks (CMB-11)

Experience in an `ExperienceStore` (per unit, saved): damage dealt × the target's `Promotion/value` ÷ its max HP;
`Promotion/thresholds` per class (data). A rank is a modifier set applied with `addPersonal` under the source
`rank`; ranks **replace** each other (the new rank's set replaces the old — no compounding, documented in the card's
Done). The insignia is a client billboard over the unit's head (rank in `unit_detail` and the spawn data).

### 2.9 Healing (CMB-12)

The temple (`strategos:building/temple`, Town, a placeholder blueprint, `GarrisonHolder/heal`) and the healer
(`strategos:unit/healer`, trained at the temple) with a `heal` ability (`Heal` effect, autocast, range 12): the target
is the most wounded unit in range with the class filter `Organic !Siege`; buildings and siege engines are repaired,
never healed (`HealFilterTest`). Garrison healing reuses M9's `GarrisonSystem`.

### 2.10 Loot (CMB-13)

`Loot/resources/<r>` on templates; at `UnitDied`, the killer's faction is granted the loot plus the victim's carry
(`CarryStore`), counted in a new `looted` counter of `FactionTreasury`; the invariant becomes granted + gathered +
refunded + looted = stock + spent (`Treasury.broken`, `EconomyChecks`, architecture §11.1).

### 2.11 The City phase (TECH-04)

`techs/phase_city.json`: ordinal 2, requires Town and a count of Town buildings (data), effects
`TerritoryInfluence/radius` ×1.3 on `CivicCentre` and `Attack/Building/arrows` +2 on `Structure` with arrows,
unlocks the City technologies. `TerritorySystem` already re-emits on a stat change; `TownArrows` reads the path.

### 2.12 The technology tree (TECH-05)

About 30 technologies in data: resource lines to three tiers each (food, wood, stone, iron, carry), equipment to
three tiers (armour to vanilla's `IRON` and `DIAMOND` materials), building technologies (HP, arrows, build speed),
paired technologies under a new `excludes` field (`TechDef`, checked by `ResearchRules`; researching one locks its
pair). Generated L1 tests: for each technology, its effect on the paths it names. The screen: a client `TechTreeScreen`
with phases as columns, opened from the phase panel; state from the faction's research view.

### 2.13 Formations (ORD-06, architecture §9.4)

`FormationMove(UnitSet, target, shape, queue)` (wire 38) creates a `FormationController` in a `FormationStore` (a
core entity: shape, members, slots, one PATH-09 group path); slots by class (melee front, ranged behind) assigned
greedily by sorted projection onto the facing; speed is the slowest member's; members enter `FORMATION_MEMBER` and
steer to their slot; a gather or attack order dissolves the formation, a member's own order removes it, and the rest
re-slot. Client: a click selects the member; Alt+click selects the formation.

### 2.14 Patrol and guard (ORD-07)

`Patrol(UnitSet, points, queue)` (wire 36): loop between the points with attack-move behaviour (`WALKING_AND_FIGHTING`
per leg). `Guard(UnitSet, target, queue)` (wire 37): follow the charge (`Following`) and engage what attacks it or
comes into range. Both are `OrderKind`s whose anchors are saved.

### 2.15 Hunting (ECO-10)

A wild herd template (`strategos:unit/wild_goat`, the fauna archetype with `Fauna/wild` and the passive stance: it
flees `Fauna/flee` blocks from an attacker); herds placed by the map manifest's new `herds` list; horsemen gain a hunt
rate (`ResourceGatherer/rates/food.hunt`) and gather the carcass the kill leaves (M4's `Livestock` carcass path).

### 2.16 Hotkeys (UI-17)

`Action` gains queue, stop, back to work, garrison, delete, next idle soldier, select all soldiers, select all
workers, camera bookmarks 1–4 (set, jump); `DefaultBindings` binds them; the key reference (`ControlsOverlay`) is
generated from the bindings; `HotkeyCoverageTest`: every command-card action has a binding.

### 2.17 The orthographic view (CAM-06)

A client mixin on `GameRenderer#getProjectionMatrix` returning an orthographic matrix sized from the camera height; a
bypass of `Frustum#offsetToFullyIncludeCameraCube` while orthographic; `Picking` and `View` gain the orthographic
ray; an option toggle with a fallback to perspective when Sodium or Iris refuse (detected at the first frame); the
ADR-0020 inventory entry and its L2 check.

### 2.18 Day and night (MATCH-16)

A match rule `PlayRules.dayNight` (off by default, `optionalFieldOf`; mixed into the hash only when on), a day clock
derived from the match tick (cycle length in data, no state to save), a night factor that scales `Vision/range` by
`Vision/night` through a faction-scope modifier refreshed at dusk and dawn (`VisionSystem` re-stamps on the stat
version), and buildings' `Vision/light` as a radius that holds at night (torches and campfires on the default
civilisation's buildings). The arena's `setDayTime` follows the clock when the rule is on; a client tint is the
fallback with more than one match in a world (D-20). `AFTER-M8`: FOW-02's shared vision takes the night radius.

### 2.19 The AI on the new content (AI-09)

Scripts reach City; the AI researches along the tree, trains healers and keeps them behind the army, retreats units
by rank (veterans first), uses `charge` on the approach, and marches in formation between fights.

### 2.20 The balance matrix (QA-16) and the perf gate (PERF-17)

`:core:balanceMatrix` extends `OpeningBalance` (QA-12): civilisation pairings × levels, 200 seeds each, whole matches,
win rates, match length and phase times, and a diff of the balance sheet against the last run; the report flags any
pairing outside 80–120 of 200 (in a mirror, per start slot). Beyond QA-12's openings it plays whole matches, keeps the
sheet diff, and has the civilisation dimension ready for M14's second civilisation. The L3 samples (20 seeds) join
`verifySoak`.
`perf_m10`: four factions with formations of 40, two wild herds per quadrant, abilities on cooldown, City reached;
15 minutes, A–E; `perf_m10_stress` at 2×.

## 3. Build changes

`:core:balanceMatrix` (QA-16) beside `:core:openingBalance`; the GameTest pack's `civs/probe.json` resource; a client
mixin entry for CAM-06. No new dependencies.

## 4. Tests per level

- **L0:** `CivBoundaryArchTest`; the engine-surface inventory for CAM-06.
- **L1:** the seams' defaults against pinned hashes; each variant; the probe matches; abilities, ranks, healing, loot,
  the City phase, the generated technology tests, formations, patrol and guard, hunting, hotkeys, night vision; the
  AI's City and probe runs; the balance matrix.
- **L2:** the shipped civilisation files through the real loader (`ShippedCivsTest`), the payload round trips.
- **L3:** `civtests.two_civilisations`, `abilitytests.charge`, `healtests.healer_heals`, `loottests.kill_pays`,
  `phasetests.city_bonuses`, `techtests.<family>`, `formationtests.march`, `ordertests.patrol_and_guard`, the `aisoak`
  samples.
- **L4 (consent, T30):** tree screen, insignia, idle cycling, orthographic, night.
- **L6:** `perf_m10`, `perf_m10_stress`.

## 5. Measurements before building on them

- T1 measures the restore-and-compare check's cost on the seeded scenes before it joins `verify`.
- T18 measures a 40-member formation's slot error on flat ground before the client work of T19.
- T23 checks the mixin under Sodium and Iris in the consent batch before CAM-06 is `done`; until then it is off by
  default.

## 6. Risks and fallbacks

| Risk | Fallback |
|---|---|
| A seam's default drifts a hash | The default calls the moved code unchanged; T1's restore check and the pinned seeds catch it at the task |
| The probe cannot run in a GameTest (test pack loading) | `civtests.two_civilisations` loads the probe through the GameTest bootstrap's template set; else an L1 headless match plus an L3 default-only run, recorded in §1.6 |
| The AI refactor weakens the default AI | The opening balance (QA-12) and the AI soak at the close must match M9's results within their bands |
| Formation slot error ≥ 1 block | Fewer slot reassignments (re-slot only on membership change); the speed cap at the slowest member |
| The orthographic mixin breaks under Sodium or Iris | Off by default with the clean fallback; CAM-06 is optional and may stay `in_progress` for the owner |
| Night with several matches in one world | The client tint (D-20 open) |
| 32 tasks | The civilisation chain first: if time runs short, the depth cards keep their order and the close lists what is left |

## 7. Task breakdown

### 7.1 Order

T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9a → T9b → (T10 → T11 → T12 → T13 → T14) ∥ (T15 → T16 → T17) ∥
(T18 → T19 → T20) ∥ (T21) ∥ (T23) ∥ (T24) → T22 → T25 → T26 → T27 → T28 → T29 → T30 → T31. T22 (hotkeys) follows
T11, T19 and T20, which add command-card buttons. T13 needs T10 (the heal ability) and T15
(the temple is a Town building, the healer's data names City upgrades).

### 7.2 Tasks

#### T1 · Health check moves (M9 follow-ups)
Split `MatchState` (815 lines) under 800 by moving the territory, capture, gate and breach accessors into a
play-state facet, by moves only; add the restore check (M9 §10.4 item 3): an L1 test that restores mid-match on every
seeded scene and compares hashes over the next ticks. Verify: `verify verifySlow`, the soak seeds' hashes unchanged.
Size: 500.

#### T2 · CIV-01 · Civilisations as content
`RawCiv`, `CivDef`, `StartKit`, `CivRoles`; the `civs` folder, compile, lint, `$civs`, the client view; the default
civilisation's data and its built-in copy; ADR-0028 Proposed, architecture §8.9. Verify: `verify`, `ShippedCivsTest`,
the template set's pinned SHA unchanged without civilisation files. Size: 700.

#### T3 · CIV-01 · Binding, roster and bonuses
`MatchConfig.civs`, the hash guard, `FactionCivs.bind` at start, restore and swap (the `CivDef` and its roster); the roster rule in production,
placement, research and the command card; `CivBonuses`; phase display names. Verify: `verify`, `DefaultCivCompatibilityTest`.
Size: 700.

#### T4 · CIV-01 · Setup and start kits
`SkirmishSetup` per-slot civilisations, the screen selector, the command option, the scenario `civ` and `kit`;
`first_skirmish` on its kit with the identical command list; the AI profile from the civilisation. Verify: `verify`,
`ScenarioKitTest`. Size: 600.

#### T5 · CIV-05 · The catalogue and the quiet seams
`RuleCatalogue`, `RuleRef`, `CivRules` in the binding, the lint of rule ids and parameters; `UpkeepRule`,
`ResourceRule`, `PhaseRule`, `PlacementRule` (in `PlacementRules`), `DefeatRule` with today's code moved behind them; test-only variants prove each seam is honoured.
Verify: `verify verifySlow`, `SeamDefaultsTest`, the pinned hashes. Size: 700.

#### T6 · CIV-05 · Population and delivery
`PopulationRule` (default, `fixed`) with the refusal reason; `DeliveryRule` (default, `direct`) at `headHome`, made the
only entry into GATHER_RETURNING first.
Verify: `verify verifySlow verifySoak` (gathering is in the tick), `PopulationFixedTest`, `DeliveryDirectTest`.
Size: 600.

#### T7 · CIV-05 · Construction
`ConstructionRule` (default, `self`): the commit at placement for `self` (world writes, eviction) and the progress
path in `Construction` and `BuildingStore`. Verify:
`verify verifySlow`, `ConstructionSelfTest`. Size: 500.

#### T8 · CIV-05 · Roles and the vocabulary
Template ids out of `core.sim` and `core.client` into roles; `CivBoundaryArchTest`; architecture §8.9's vocabulary.
Verify: `verify verifySlow`, the opening balance unchanged. Size: 600.

#### T9a · CIV-05, AI-09 · The AI's hints
The variants' AI hints, refusal reasons, `AiView.civ()`, the managers on hints and roles. Verify: `verify verifySlow
verifySoak`, `:core:openingBalance` within M9's band. Size: 500.

#### T9b · CIV-05 · The probe civilisation
The probe (fixtures and GameTest pack), `ProbeMatchesTest`, `AiProbeTest`, `ProbeFixtureTest`,
`civtests.two_civilisations`; ADR-0028 Accepted. Verify: `verify verifySlow`. Size: 500.

#### T10 · UNIT-10 · Abilities in the core
`AbilityDef`, `UseAbility`, `AbilityRules`, `AbilityEffects`, `TimedModifiers`, `AbilityStore`, the snapshot.
Verify: `verify verifySlow`, `:core:jmhCompare`. Size: 800.

#### T11 · UNIT-10 · Abilities in the game
Command-card buttons and cooldowns from templates; the horseman's `charge`; `abilitytests.charge`. Verify: `verify`.
Size: 400.

#### T12 · CMB-11 · Ranks
`ExperienceStore`, thresholds, rank modifier sets, the insignia, rank in `unit_detail` and spawn data. Verify:
`verify`. Size: 600.

#### T13 · CMB-12 · Healing
The temple and the healer with their blueprints, the `heal` ability, the target rule and the filter; `healtests`.
Verify: `verify`, `:core:balanceSheet`. Size: 600.

#### T14 · CMB-13 · Loot
`Loot` paths, the `looted` counter, the invariant; `loottests`. Verify: `verify verifySoak`. Size: 350.

#### T15 · TECH-04 · The City phase
`phase_city.json`, its requirements and bonuses; `phasetests.city_bonuses`. Verify: `verify`. Size: 400.

#### T16 · TECH-05 · The technology set
About 30 technologies, `excludes`, generated tests, the sheet's tiers, the default roster. Verify: `verify`,
`:core:balanceSheet`. Size: 800 (mostly data).

#### T17 · TECH-05 · The tree screen
`TechTreeScreen`, the L3 family samples. Verify: `verify`. Size: 600.

#### T18 · ORD-06 · Formations in the core
`FormationMove`, `FormationController`, `FormationStore`, `FORMATION_MEMBER`, dissolving and re-slotting; the slot
error measurement. Verify: `verify verifySlow verifySoak`, `:core:jmhCompare`. Size: 900.

#### T19 · ORD-06 · Formations in the game
The card button and hotkey, click versus Alt+click selection, `formationtests.march`. Verify: `verify verifySlow`.
Size: 500.

#### T20 · ORD-07 · Patrol and guard
`Patrol`, `Guard`, their order kinds and anchors; `ordertests.patrol_and_guard`. Verify: `verify verifySlow`. Size: 600.

#### T21 · ECO-10 · Wild herds and hunting
The wild goat, herds in the manifest, flee by stance, the horseman's hunt. Verify: `verify verifySlow`. Size: 600.

#### T22 · UI-17 · Hotkeys
The full action set, bindings, the generated key reference, `HotkeyCoverageTest`. Verify: `verify`. Size: 600.

#### T23 · CAM-06 · The orthographic view
The mixin, the frustum bypass, orthographic picking, the toggle and fallback, the inventory entry. Verify: `verify`,
`:mod:archTest`. Size: 500.

#### T24 · MATCH-16 · Day and night
`PlayRules.dayNight`, the clock, the night factor, light sources, the arena time, the tint fallback, `AFTER-M8`.
Verify: `verify verifySlow`. Size: 700.

#### T25 · AI-09 · The AI on the new content
City, the tree, healers, rank-aware retreats, `charge`, formation marches; `AiCityTest`. Verify: `verify verifySlow`,
`:core:openingBalance`. Size: 700.

#### T26 · QA-16 · The balance matrix
`:core:balanceMatrix`, the report and its flags, the sheet diff, the L3 samples in `verifySoak`. Verify: the matrix
run. Size: 500.

#### T27 · PERF-17 · The perf gate
`perf_m10`, `perf_m10_stress`, the report's costs, a proposed baseline where growth is the features' own cost.
Verify: the perf batch. Size: 400.

#### T28 · The demo chapters
A chapter per new visible mechanic (`DemoCatalog.CHAPTERS`). Verify: `verify`. Size: 500.

#### T29 · The close's long runs
`verify verifySlow verifySoak`, `:core:jmhCompare`, the perf batch, the AI soak, off the working copy. Size: runs.

#### T30 · The consent batch
§1.7. Size: runs.

#### T31 · The close
Results (§10), the cards' statuses, the hand-in. Size: docs.

### 7.3 Progress

- [x] T1 (2026-11-25)
- [x] T2 (2026-11-25)
- [x] T3 (2026-11-25)
- [x] T4 (2026-11-25)
- [x] T5 (2026-11-25)
- [x] T6 (2026-11-25)
- [x] T7 (2026-11-25)
- [x] T8 (2026-11-25)
- [ ] T9a
- [ ] T9b
- [ ] T10
- [ ] T11
- [ ] T12
- [ ] T13
- [ ] T14
- [ ] T15
- [ ] T16
- [ ] T17
- [ ] T18
- [ ] T19
- [ ] T20
- [ ] T21
- [ ] T22
- [ ] T23
- [ ] T24
- [ ] T25
- [ ] T26
- [ ] T27
- [ ] T28
- [ ] T29
- [ ] T30
- [ ] T31

## 8. Acceptance checklist (M10 exit)

1. Every acceptance criterion of the M10 cards passes at its level (§1.4).
2. The default civilisation is invisible: M0–M9's pinned hashes, goldens and replays hold.
3. The probe civilisation plays, replays and needed no edit outside `core.sim.civ` and the fixtures.
4. QA-16: every pairing within 80–120 of 200 (the mirror per start slot); the L3 samples replay.
5. PERF-17 within A–E; the 2× stress pass holds.
6. The consent batch (§1.7) seen and accepted.

## 9. Owner inputs

None blocks M10. Put to the owner (branch file "For the owner"): ART-03's use of D-03 (M7), and the default
civilisation's display name ("Settlers", a lang key).

## 10. Implementation results

Written at the close (T31).
