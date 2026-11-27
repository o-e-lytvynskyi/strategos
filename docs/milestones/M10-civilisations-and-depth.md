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
[ADR-0028](../adr/0028-civilisations-and-rule-seams.md) (Accepted at T9b).

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
| AI-09 | T9a, T9b, T25a, T25b | 1 `AiCityTest`; 2 `AiProbeTest`; 3 `aisoak` sample hash |
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
| 17 | The AI and refusal reasons (T9a) | The AI sends commands through a `CommandSink` with no feedback, so it does not read refusal notices: it reads the same facts from state through `AiView` (population used against the rule's cap; its hints). The notices carry their reasons for the HUD. A command-result channel to the AI belongs to AI-10's manager architecture (M11) |
| 18 | Hints (T9a) | Each seam's rule declares `hints()` (a bitmask of `AiHint`), the variants clearing their own; `CivRules` combines them at bind. Under direct delivery the farmstead stays in the build order (it reseeds fields), every other dropsite step is skipped |
| 19 | The probe's files (T9b) | `mod/src/gametest/resources/probe/` (civilisation, a citizen-soldier infantry and its barracks by template inheritance, an AI script), outside any data pack: the gametest source set is on the mod's L2 test classpath, where a pack would break the shipped-set pins. Core tests load them through `ProbeCiv`; `civtests.two_civilisations` loads them into the GameTest server and restores the pack's set after its batch. Its bonus is two entries (a wood-rate bonus for every gatherer, its barracks on the workers' list) |
| 20 | What the probe found (T9b) | The AI read a building's static `Trainer/units`; it now asks `MatchQueries.trains` (the faction's effective list and roster, as production does) — the one production edit outside `core.sim.civ` the probe needed; hash-neutral, since no shipped technology edits trainer lists (`AiTrainsEffectiveTest`) |
| 21 | Abilities' shapes (T10) | Ability ids are plain (`charge`), global to the set: templates declaring the same id must declare it identically; at most one timed effect per ability; `damage` takes hack, pierce or crush; data keys beyond §0.3: `radius` (area), `pick` (autocast picker: `self`, `nearest_enemy_in_range`), `on` (`ally` default, `enemy`, `any`) for `apply_modifiers` and `capture_strength`; `heal` mends `Organic` non-`Siege` units only. An ability's index is its place in the id-sorted list: a content swap that would move an index in use is refused, and a restore remaps saved orders by the saved id table (a missing ability refuses the restore; cooldown and timed rows are dropped). Cooldown and cost are checked at the command only for REPLACE, at firing otherwise. `RejectReason.ON_COOLDOWN` (wire 20) is appended. Personal modifier sources: none were in use; abilities take `MIN_VALUE + 1 + index`, ranks keep `MIN_VALUE` |
| 22 | Ranks (T12) | `unit_soldier` (infantry, archer, horseman) carries `Promotion`: values are cost sums, thresholds 200/500/1000, ranks ×1.05/×1.10/×1.15 on hit points and damage (each about half an equipment tier; the first try at ×1.3 kept seed 2 of `BattleAtTheFordTest` from ending by its cap). Experience is credited per packet, `min(dealt, left) × value ÷ max HP`, to enemy player-faction attackers that are not dying; an archer that dies before its arrow lands forfeits it. The earned rank is state and stays as earned after a swap or a restore; what shows and applies is `min(rank, the list's length)`. Max HP keeps the ratio on a rank change (ADR-0013's note) |
| 23 | Formations (T18) | One route per formation, searched by the path kernel (`PathJob`, gates passed like `PathService`) at command time on the most confined member's grid, re-searched from a partial route's end, instead of a PATH-09 group request; members steer to a place pushed ahead along it and plan individually only when it is out of line of walk. `FormationMove` is REPLACE only in M10 (a formation is a standing thing); one unit becomes a plain Move. The anchor waits up to 100 ticks for stragglers (assembled within 0.6 block; the place runs 0.9 block ahead). `MatchSnapshot.Play` reached DFU's 16-field limit: abilities, experience and formations nest in `Play.Depth`, still written as flat optional fields |
| 24 | Patrol and guard (T20) | Patrol rides WALKING_AND_FIGHTING, its points saved as the order's anchors (on the order, not in `MatchSnapshot.Play`); one point patrols from the unit's place; a queued order after a patrol is accepted and never runs until a REPLACE. Guard has two leaves (GUARDING, GUARD_STANDING), stands 3 blocks off its charge, engages within its stance radius of the charge, leashes back, ends when the charge dies or leaves the alliance. Hotkeys O and G (P is vanilla's); a plain click patrols to one point, Shift-click adds points, right-click or Enter sends |
| 25 | Hunting (T21) | The wild animal is the wild boar on the pig model (150 meat, 30 HP, flees 16 blocks through `Behaviour/flee` — no separate `Fauna/flee`); a new `Hunter` class (horsemen hunt at 5 food/s, carry 20; workers only gather a carcass, at 1/s) keeps horsemen out of the AI's worker logic; a food subtype `hunt`; herds through an optional `MapManifest.herds`, four point-mirrored herds on `first_skirmish` |
| 26 | Hotkeys (T22) | Back to work T, garrison ;, delete Backspace pressed twice within 3 s (a new `DeleteUnits`, wire 39: own units die, no loot), next idle soldier Shift+I, select all soldiers Ctrl+A and workers Ctrl+W, camera bookmarks F4/F5/F7/F8 (set with Ctrl, x and z only, per match); queueing stays Shift on any order. The vanilla keys left no free letters. Units holding a standing order (patrol, guard) are flagged in `unit_summary` by the server and skipped by idle cycling |
| 27 | The orthographic view (T23) | Verified: `GameRenderer#getProjectionMatrix(double)` (GameRenderer.java:980) and `Frustum#offsetToFullyIncludeCameraCube(int)` (Frustum.java:33). The client mixins sit in `mod.client.mixin` under a second, client-only config (`strategos.client.mixins.json`), as ArchUnit's B5/B10 require. The picture's half height is `height / sin(pitch) · tan(FOV/2)`, the perspective view's height at the focus; picking needed no change (it unprojects both planes). Off by default; Ctrl+O while the RTS screen is open or the config toggles it; with Sodium or Iris loaded it stays off unless `orthographicWithRenderMods`; a fault turns it off for the session |
| 28 | Day and night (T24) | A 24,000-tick day; match tick 0 is noon; night 13,000–23,000 in time-of-day (the first night at match tick 7,000), dusk and dawn 1,000 each. At night `Vision/range` ×`Vision/night` (0.6, on the unit and building bases) through `NightFactor`, refreshed only when the night begins or ends; `Vision/light` (civic centre 12, house 8, tower 24, sentry tower 20) holds at night. One match in a level drives the level's time (`setDayTime` every 20 ticks, vanilla's sync); more than one gets a client tint instead (`MatchStateMsg.tint`). `PlayRules.dayNight` is a boolean (no forced-tint mode); the cycle length is a constant (`DayClock.CYCLE`). `AFTER-M8` in NightFactor: FOW-02's shared vision reads the stamped radius |
| 29 | The AI on the new content (T25) | Split into T25a and T25b. The first runs had City out of reach and the opening balance red; a diagnosis of 100 matches found the cause in the late game, not in City: in the 35 matches unfinished at 45 minutes the stronger side sat on unspent food and stone (1,158 and 754 at the limit) with 20 population free, as the tree's and the ram's reserves held soldiers back and gatherers kept a fixed ratio; the mop-up searched only round the enemy start, re-tasked only idle soldiers and skipped foundations; and a centre that fell late left too many buildings to burn. City now costs 600 food, 700 wood, 350 stone and 250 iron (was 800/900/600/400; the map gives each start one 1,000-stone quarry, and the AI spends about 1,050 stone before City), the temple 150 wood and 100 stone (was 100/200), the small quarry 2,000 stone (was 1,000; the pre-tech reconstruction keeps 1,000). The L3 sample plays in its own slow batch (`ai_sample`): the soak's batch must hold one test (QA-04.5). The shipped script's build order gains a tower and a temple at population 24, after Town, so the AI uses the new content with or without the `depth` block. Two rules came from the matches: no node farther than 128 blocks from an own civic centre is gathered (`EconomyManager.FAR_NODE`, the economy's reach beside `BuildManager.REMOTE_REACH`; a stone worker was sent 340 blocks across the map while its near slot stayed claimed, and Town came late), and a standing formation's member that gets no nearer its place for 200 ticks leaves the formation |
| 30 | The perf gate (T27) | The combat path's M10 cost is the ranks' own: bisected to T12 (5.68 → 6.78 µs/op at that commit), `Ranks.credit` costs about 12 ns a hit spread over its checks, lookups and arithmetic, and the target's `Promotion/value` read costs nothing measurable, so the spec's "hand over the target's value" would save nothing and is not done; the attacker's max-health read gave way to a dying-this-tick bitset (set the moment a hit turns lethal, as the old check saw it) and the death pass's rows allocate nothing. `ShotBench.volley60{preferred}` stays at 6.636 µs/op (+19 % on 5.576): a proposed baseline row. `M10Scene` re-orders its formations every 10 ticks, as the Hard computer takes members within about 25 (formed 86 % of the time, reported); the fauna's cost is timed per run of consecutive Gaia units in the unit AI, not per animal |
| 31 | City as a capability (T25b) | Unopposed, the depth path first reached City in 0 of 12 seeds: the map gives each start about 1,065 iron before expansion (one small mine; the large one lies past `FAR_NODE`), and the iron went on archers, horsemen, healers, rams and a second barracks before City's reserve existed. What made it a capability, all behind `Depth.late` (the shipped AI's opening balance is identical, `-Pseeds=4`): the phase's price is the reserve as soon as it heads the plan and an own building would research it but for its requirements; above the army floor (`attack.min`) no spend takes a resource of the price below its amount, below it soldiers spare only what the reserve lacks, and below half of it the cheapest soldier passes; healers and rams are always strict and no ram price is held while a phase is saved; Town-class buildings, houses and a dropsite by the lacking resource are exempt; a build step the reserve blocks is skipped and caught up (`Slots.SKIPPED`); gatherers follow the lack. `AiCityTest`: Normal with the depth block (a test resource: `techs.from` 720 s, `phaseHold` 36,000) against a script that never attacks, no conquest victory, 45 minutes — City in 11 of 12 seeds (26–32 minutes), tier 2 in 10, tier 3 in 9, healers in 12, formations in 12, charges in 9. `techs.keep` is gone |
| 32 | The close's long runs (T29) | On T28 the perf batch gave up one order of `crowd_soak_600`'s 18,437, and a perf scene counts a given-up order as a violation. The order was a walker's a hair past a corner whose two sides link but whose diagonal does not (a one-block rise without the diagonal's clearance). The line-of-walk check crosses one column edge at a time and had passed the leg; the 0.25-block step crossed both edges in one tick, was refused as a diagonal, and slid into a pocket beside the leg. Every re-route began at its own cell centre and nearing it counted as progress, so the ladder never left its first rung, and the backstop gave the order up after 620 ticks. `MotionMoves.tryMove` now walks such a step's segment as `LineOfWalk` does, through the column it crosses first (`RampCornerTest`). A wall's corner stays refused: applied there, the rule changed every crowd's path through a gap, moved two golden journals' hashes and settled a unit 1.01 blocks off `head_on_groups_through_gap`'s 1.0. The scene's journal replayed with the fix gives up no order. On the fix: `verify verifySlow verifySoak` green (3 h 02 min; the aisoak's 20 matches replay), `jmhCompare` green against the approved baseline, the perf batch's 27 scenes pass (`crowd_soak_600` gives up none, `walk_600`'s verdict valid this time). The batch reached its 180-minute watchdog before its last scene, `battle_soak`, which passed alone on the same code: T27's two M10 scenes add 20 minutes of wall time and the watchdog was never raised for them. It is 210 minutes now, as M9 raised it; no budget changed |
| 33 | The consent batch (T30) | The scenario runner pins the client's light to noon on every client tick, so that a frame does not depend on when the server's time packet lands. The night scene's level was at night on the server (13,060) while the client stayed at 6,000, and the demo's night chapter would have shown noon too: its L1 test runs on the fake stage, which has no client. A scene about the light now releases the pin (`ScenarioContext.worldLight()`): `m10_night` waits for the client's level to reach night, and the demo releases it for its whole run (its night chapter, shot, goes dark). PERF-14's pass is `rts_frame_veterans`, `rts_frame_battle`'s scene with every unit ranked, so the bars' timer holds the insignia; by the card's method (medians of three runs) every budget holds, the client tick p50 at 0.997 ms of 1 (runs 1.004, 0.973, 0.997). The insignia are not that cost: the same scene without ranks read 1.128 ms in its one run, the bare map 0.95 ms, our drawing 0.12 ms and the HUD's build 0.09–0.12 ms of the tick (M2's baseline 0.80 ms, M5's battle 0.96) — §10.4 |
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
(T18 → T19 → T20) ∥ (T21) ∥ (T23) ∥ (T24) → T22 → T25a → T25b → T26 → T27 → T28 → T29 → T30 → T31. T22 (hotkeys) follows
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
`TechTreeScreen` with phases as columns, opened from the phase panel. First, the command card's research buttons: they
list a building's base `Researcher/techs` only, so the technologies a phase adds by tokens (Town's and City's tiers,
`iron_weapons`, `chainmail`) never show (found in T16; true since M6) — the card and the screen read the faction's
effective lists (the client's own `StatService` over its template copy and research, ADR-0013's client prediction).
Verify: `verify`. Size: 600.

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
`:core:openingBalance`. Size: 700. Split on 2026-11-26 (it passed 1,500 lines, and its runs showed the late game, not
the new content, holding City back — §1.6 row 29):

- **T25a · The machinery and what its runs found.** The script's `depth` block (the tree with its holds, healers kept
  behind the army, veterans retreating first, `charge` on the approach, formation marches, workers growing with the
  phase), each manager with its L1 test; the retuned City, temple and small quarry; the L3 sample that reaches Town and
  replays its journal (AI-09.3). Five defects the AI's matches exposed, each with a test that failed first: a storehouse
  placed on a gatherer's work slot, a formation slot left stale when the layout shrank, a capture walking on to a
  fallen building, a standing formation's member that never reaches its place, and a boxed-in unit whose stuck count
  was zeroed on every re-request. The shipped script leaves the `depth` block out, so every earlier gate holds.
  Verify: `verify verifySlow`, `:core:openingBalance`, `:core:jmh :core:jmhCompare`. Size: 1,200.
- **T25b · City as a capability.** Changed on the owner's word (2026-11-26, recommended option (a)): `AiCityTest` plays the skirmish script at Normal with the `depth` block (a test resource; the shipped script keeps it off until M11) against an opponent that does not attack, without a conquest victory, 45 minutes, 12 seeds: the AI reaches City, researches tier-2 and tier-3 technologies, trains a healer, marches in formation and charges. Three depth-path defects from T25a's review go with it: a research hold waiting for a build step expires with the step's reserve time, a march measures from the soldiers it orders, and a veteran walking home stays resting until it is above `resume`. The first attempt at a decisive late game (spending by need, a map-wide mop-up) regressed the opening balance and is M11's to redo. Verify: `verify verifySlow`. Size: 400.

#### T26 · QA-16 · The balance matrix
`:core:balanceMatrix`, the report and its flags, the sheet diff, the L3 samples in `verifySoak`. Verify: the matrix
run. Size: 500.

#### T27 · PERF-17 · The perf gate
`perf_m10`, `perf_m10_stress`, the report's costs, a proposed baseline where growth is the features' own cost. First, the
combat hot path's M10 cost (`ShotBench.volley60{preferred}` 5.576 → 6.828 µs/op after ranks and loot, T12/T14): CombatSystem
hands `Ranks.credit` the target's value and a dying-this-tick bitset so a hit reads no extra stat, and the death pass's
loot rows stop growing per death; then `jmhCompare`, and a proposed row only for what remains.
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
- [x] T9a (2026-11-25)
- [x] T9b (2026-11-25)
- [x] T10 (2026-11-25)
- [x] T11 (2026-11-25)
- [x] T12 (2026-11-25)
- [x] T13 (2026-11-25)
- [x] T14 (2026-11-25)
- [x] T15 (2026-11-25)
- [x] T16 (2026-11-26)
- [x] T17 (2026-11-26)
- [x] T18 (2026-11-26)
- [x] T19 (2026-11-26)
- [x] T20 (2026-11-26)
- [x] T21 (2026-11-26)
- [x] T22 (2026-11-26)
- [x] T23 (2026-11-26)
- [x] T24 (2026-11-26)
- [x] T25a (2026-11-26)
- [x] T25b (2026-11-26)
- [x] T26 (2026-11-26)
- [x] T27 (2026-11-26)
- [x] T28 (2026-11-26)
- [x] T29 (2026-11-27)
- [x] T30 (2026-11-27)
- [x] T31 (2026-11-27)

## 8. Acceptance checklist (M10 exit)

1. Every acceptance criterion of the M10 cards passes at its level (§1.4).
2. The default civilisation is invisible: M0–M9's pinned hashes, goldens and replays hold.
3. The probe civilisation plays, replays and needed no edit outside `core.sim.civ` and the fixtures.
4. QA-16: every pairing within 80–120 of 200 (the mirror per start slot); the L3 samples replay. The easy mirror is left out until Easy can finish a won match (the owner, 2026-11-26; M11).
5. PERF-17 within A–E; the 2× stress pass holds.
6. The consent batch (§1.7) seen and accepted.

## 9. Owner inputs

None blocks M10. Put to the owner (branch file "For the owner"): ART-03's use of D-03 (M7), and the default
civilisation's display name ("Settlers", a lang key).

## 10. Implementation results

Written at the close (T31), 2026-11-27, on `dev/o-e-lytvynskyi/m10`.

### 10.1 What was built

The milestone as the owner reshaped it on 2026-11-25: one default civilisation (today's content, "Settlers") and a
civilisation foundation — civilisations as content (CIV-01) and the rule catalogue with its seams (CIV-05), proved by
a test-only probe civilisation that plays a Town with no houses, no dropsites and no builders and needed one edit
outside `core.sim.civ` (row 20). On it, the depth the roadmap planned: abilities, ranks, healing, loot, the City phase,
a technology set of 32 with its tree screen, formations, patrol and guard, wild herds and hunting, the full hotkey set,
the orthographic view, day and night, the AI's use of all of it, the balance matrix and the M10 perf gate.

All 17 active cards are `done`: CIV-01, CIV-05, TECH-04, TECH-05, UNIT-10, CMB-11, CMB-12, CMB-13, ORD-06, ORD-07,
ECO-10, UI-17, CAM-06, MATCH-16, AI-09, QA-16 and PERF-17. CIV-02 and ART-04 are deferred to M14 with D-03 (§1.5).

### 10.2 Numbers

M4 Pro, the head's code (25b942cb and later) unless noted.

| What | Result | Bound |
|---|---|---|
| `perftests.m_10_soak` (435 units, four Hard factions, formations of 40, herds, `charge`, City; 18,000 ticks) | A p50 1.58 ms, A p99 8.58, B p95 5.02, C p95 9.05, D p99 0.24, E 4.3 KB/s (T27: 1.92, 8.99, 5.44, 10.33, 0.32, 7.6) | 5, 10, 23 ms; 100 KB/s |
| `perftests.m_10_stress` (1,088 units) | C p95 12.09 ms (T27: 13.4) | 50 ms |
| formations / fauna / abilities a tick (T27) | p95 31 / 4.5 / 11.2 µs; formed 86 % of the time | reported (R) |
| the cumulative `runGameTestPerf`, 28 scenes | every scene passes, foreign load at most 2.6 %; 27 in the batch, which reached its 180-minute watchdog (now 210), `battle_soak` alone (A p50/p99 1.27/3.15 ms, C p95 6.0). `crowd_soak_600`: 18,437 orders, none given up (row 32). `mvp_soak` A 0.73/6.14 ms, C 6.81; `territory_soak` A 0.95/7.11, C 7.93 | every earlier budget |
| `jmhCompare` | green; `ShotBench.volley60{preferred}` 6.774 µs/op against the owner's 6.636 row (the ranks' cost, row 30) | the approved baseline |
| `AiCityTest` (Normal with the depth block, unopposed, 45 min) | City in 11 of 12 seeds (26–32 min), tier 2 in 10, tier 3 in 9, healers and formations in 12, charges in 9 | — |
| the AI soak, 20 seeds rotating the mirrors over the levels (QA-16.4) | 10 by conquest (19.6–42.5 min), 10 to the 45-minute limit, 872,830 ticks; every journal replays to its hash | every journal replays |
| `:core:balanceMatrix` (200 seeds a pairing; the Normal and Hard mirrors) | Normal 104/86 and Hard 89/103 wins by start slot, 10 and 8 unfinished, medians 36:09 and 24:44; nothing flagged; 400 matches in 3.8 min | 80–120 of 200 a start slot |
| `:core:openingBalance` (QA-12) | every criterion holds: 818 of 1000 by conquest, median 34:49; rush 75 and 75, boom 69, Hard 155, Normal 170 of 200; 8.3 min | every criterion |
| PERF-14 `rts_frame_veterans` (202 units on screen, every one ranked) | vanilla, medians of three runs: frame p50/p99 5.07/10.27 ms, units 3.61, ours 0.12, client tick 0.997 (runs 1.004, 0.973, 0.997); Sodium and Iris: 3.77/8.06, 2.96, 0.07, 0.855 | F: 16.7, 25, 6, 3, 1 ms |
| `m10_goldens`, macos and macos-sodium | twelve frames each, every one looked at; reruns at most 1.3e-6 (vanilla, the night) and 5.6e-9 | 1e-5 |
| `runClientSmokeJar`; the demo `-Pdemo=m10` | green, 13 s; nine chapters shown, none failed, 6 min 20 s | — |
| `verify verifySlow verifySoak` | green, 3 h 02 min | — |
| `verify` | 3 min 3 s after a core change, 1 min 17 s after a devtools change; `verify verifySlow` about 5 min 11 s (T27) | 5 min for `verify` |

### 10.3 Deviations

Findings rows 1–33 (§1.6). The ones a reviewer should read first:

- **Rows 1–11, the reshape.** On the owner's word: the default civilisation and a foundation for distinctive ones
  instead of a second civilisation. CIV-05 is new; CIV-02, ART-04 and D-03 move to M14; AI-09, QA-16 and PERF-17
  follow (ADR-0028).
- **Rows 29 and 31, City in the AI's hands.** The first runs had City out of reach and the opening balance red. A
  diagnosis of 100 matches found the cause in the late game: unspent stock, soldiers held back, a mop-up that searched
  too narrowly. City's price came down, and the depth path learned to save for a big price. The shipped script keeps
  `depth` off until M11 (the owner's option (a)).
- **Row 26, stalled walkers in long matches (T26).** The 90-minute matrix found three causes the 45-minute soak never
  reached: the AI's base drifting to a captured centre, the net backstop restarting later than the invariant, and a
  re-order to the same place restarting the count.
- **Row 32, a corner the step refused and the check passed (T29).** The crowd soak gave up one order of 18,437. A step
  across a corner whose sides link but whose diagonal does not now walks its segment as the line-of-walk check does; a
  wall's corner stays refused.
- **Row 33, the harness hid the night (T30).** The scenario runner pinned the client to noon on every tick; a scene
  about the light now releases the pin.
- **Row 30, the combat path's cost.** It is the ranks' own; the spec's proposed hand-over would have saved nothing and
  was not done.
- **Row 23, formations.** One route per formation, searched at command time, instead of a PATH-09 group request.
  `MatchSnapshot.Play` reached DFU's 16-field limit and nests the M10 sections.

### 10.4 Follow-ups

1. Split the classes over 800 lines: `BuiltinTemplates` (660 → 1,298, the built-in content mirror: units,
   technologies, City), `RtsHud` (757 → 847), `QueriesImpl` (668 → 810), and `MatchState` (811; T1 took it to 761,
   and later tasks grew it back). The first tasks of M11.
2. PERF-14's client tick sits at its 1 ms edge: 0.997 ms at the median, 1.128 in one run of the battle scene
   without ranks, against M2's 0.80. The bare map alone reads about 0.95 ms of it, and our HUD's build 0.09–0.12 ms.
   Profile the client tick (`-Pscenario.jfr=profile`) before M11's scenes add to it.
3. The shipped script's `depth` block goes on with M11's AI-11 and AI-12, QA-12.1 as the gate. The easy mirror
   rejoins the matrix when Easy can finish a won match (AI-12).
4. `walk_600`'s measured window (about 0.4 s) is shorter than the foreign-load sampler's 1-s interval, so its
   validity is chance: lengthen the window.
5. The perf batch runs about 190 minutes now; the next scene should come with a split of the batch, not another
   raise of its watchdog.
6. `AFTER-M8` in `NightFactor`: FOW-02's team vision takes the stamped night radius. The `AFTER-M7` markers carried
   from M9 stay.

### 10.5 Planned against actual

33 tasks (T25 split in two) in three days, 2026-11-25 to 2026-11-27, against at most 68 card-days. T25 took the most:
T25b needed seven implementer rounds, each round's measurement naming the next cause. The long runs needed two chains
of about six hours each, because the first one found the corner.
