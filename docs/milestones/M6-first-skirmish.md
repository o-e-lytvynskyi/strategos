# M6 — MVP: First skirmish: implementation spec

*Written 2026-10-25 by the `dyadik` developer session (claim [M6 spec](../claims.md)) before M5 is built, from
the 23 approved M6 cards in [roadmap/M6-first-skirmish.md](../roadmap/M6-first-skirmish.md),
[04-architecture.md](../04-architecture.md) (cited as "04 §n"), the ADRs in [adr/](../adr/README.md), chiefly
[ADR-0010](../adr/0010-match-model-and-lifecycle.md) (the match), [ADR-0013](../adr/0013-modifier-pipeline.md)
(modifiers), [ADR-0016](../adr/0016-networking-and-fog-of-war.md) (fog),
[ADR-0019](../adr/0019-performance-budgets.md) (budgets) and [ADR-0023](../adr/0023-plan-alignment-after-roadmap-approval.md)
(solo fog in M6), the M5 spec ([M5-combat.md](M5-combat.md), cited as "M5 §n") as the contract for what M5
delivers, and `main`'s code at cb18b94. Status: **specified**. M6 starts when the owner opens it for claims.
Engine names cited here come from the cards, the architecture, or §0.4's check; every other Minecraft or
NeoForge name is checked in `build/mc-src` by the task that first uses it, and recorded in §1.6 when it
differs. Clean-room as before: the 0 A.D. and AoE figures are the research notes' behavioural numbers, never
their code or data.*

---

## 0. Read this first

### 0.1 Authority

As M5 §0.1: the architecture and the ADRs decide *what*, this spec *how M6 builds it*; the cards define scope
and acceptance; ROADMAP.md holds the statuses; a disagreement is a §1.6 row. M5 is not built yet: what M6
takes from it (the large mobility class, the horseman, the roster, `Attack/Building` arrows, the garrison
and the bell, battle tactics, the battle perf base) is designed against M5's spec. **AFTER-M5:** each task
that uses an M5 class checks it against M5's code and "Results" when M5 lands, and records any difference
as a §1.6 row.

### 0.2 Working rules for every task

M5 §0.2 holds, with these changes:
- **Windows.** Every L4 check of M6 but one runs in the consent batch (T27), so the screen is taken once.
  The exception is MATCH-14's `arena_world`, which runs at the end of T11 (one window, or `scenario.yml` by
  `workflow_dispatch` under Xvfb), because MATCH-02 builds on it.
- **Long L1 runs.** QA-12's 1,000 matches run in their own task (`:core:openingBalance`, §2.21), outside
  `verify`; L1 matches over 15 game minutes are tagged `slow`.
- **Order.** T2–T17 need nothing from M5 and may start before it (T12's scout and large-class reachability
  are validated when M5 lands); T1 waits for M5's close (§7.1).

### 0.3 Code conventions

M5 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Snapshot | `MatchSnapshotCodec` is at RecordCodecBuilder's 16 top-level fields, so new state goes into the nested `Play` record (`MatchSnapshot.Play`): `progress` (techs and phases per faction), `stats`, `vision` (explored bits and the hysteresis set). Each is optional, saved and hashed only when non-empty; `SchemaVersion` stays 2 |
| Commands | M5 takes 25–30. M6: `Research` 31. A research entry is cancelled by `CancelTrain` (12), whose `index` already names any queue entry |
| Randomness | `Streams` (core `sim/Streams.java`) is the table; M6's numbers start at 10, leaving 2–9 to M5: `OPENING` 10 |
| Modifiers | `StatService`'s sources are never saved: at restore and at a `ContentSwap` they are re-derived from state (researched techs, phase, the AI's handicap) |
| Payloads | New: `strategos:vision_delta` (S2C), `strategos:skirmish` (C2S). Each in `ModPayloads`, its audience test and the engine-surface inventory (04 §2.5), which also gains `unit_large`'s settings, the key mapping `key.strategos.controls`, `ScreenEvent.Init.Post` and the new GUI layers (raid bar, feed, phase panel, end screen) |
| AI memory | `AiMemory` slots 0–10 are used; M6 takes 11 (the opening), 12 (the last attack's tick), 13 (the rebuild cursor). **AFTER-M5:** AI-03 may take slots first; renumber then |
| AI script size | `SetAi.SCRIPT_BYTES` (3,000) rises to 8,000 if the resolved skirmish script needs it, checked by an L1 test (T22); a wire change, pinned in the protocol tests |
| Balance | Every new number has provenance in `provenance.json` and its sheet row; Town's cost and the openings are tuned in T23 against QA-12, never past their rows without a new note |

### 0.4 Engine names checked (2026-10-25, NeoForge 21.1.264 sources)

`WorldOpenFlows#createFreshLevel` and `#openWorld(String, Runnable)`; `LevelStorageSource#levelExists(String)`;
`ServerChunkCache#move(ServerPlayer)`, whose `ChunkMap.move` calls `updatePlayer(player)` on every tracked
entity; `TickRateManager#runsNormally()` (`!isFrozen || frozenTicksToRun > 0`, so `/tick sprint` keeps the core
stepping); `BossEvent.BossBarOverlay.NOTCHED_10`; `SoundEvents.RAID_HORN`, `BELL_BLOCK`,
`UI_TOAST_CHALLENGE_COMPLETE`, `PLAYER_LEVELUP`, `VILLAGER_CELEBRATE`, `ANVIL_USE`; `ArmorMaterials.LEATHER`,
`CHAIN`, `IRON`; NeoForge's `ScreenEvent.Init.Post`, `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`;
`KeyMapping#getTranslatedKeyMessage()`.

---

## 1. Goal, demo and scope

### 1.1 Goal

The first complete game (the roadmap's goal): a solo skirmish against a scripted AI, started from the title
screen. The player explores under fog with a horseman scout, grows the economy, chooses boom or rush, reaches
Town, researches equipment tiers that show on the units, builds a defence tower, trains rams at the arsenal
and razes the enemy after holding off waves announced by a raid bar and a horn. The end screen shows the
statistics. Placeholder art; the art pass follows the MVP verdict (ART-03, M7).

### 1.2 Slices

| Slice | Content | Demo at its end |
|---|---|---|
| 6.0 carry-over | T1 health check | M5's follow-ups closed |
| 6.1 research and phases | TECH-01, TECH-02 | A storehouse researches a gathering tech; the civic centre advances to Town and unlocks its list |
| 6.2 the end | MATCH-03 | Conquest ends the match; the end screen's statistics |
| 6.3 solo fog | FOW-01, FOW-07, UI-16, AI-18, MATCH-07 | The match starts in the dark; scouting reveals; hidden enemies are not tracked |
| 6.4 the skirmish | MATCH-14, MAP-02, MATCH-02 | Title screen → Skirmish → Start on the first map, in two clicks |
| 6.5 the interface | UI-21, UI-13, UI-20, UI-14 | The feed and its sounds; research buttons and the phase checklist; minimap orders; hints |
| 6.6 equipment and siege | TECH-03, CMB-09, BLD-17 | Iron weapons and chainmail on the units; rams at a stone centre; towers shooting |
| 6.7 the opponent | AI-04, AI-05 | Three openings, waves under a raid bar, defence, three levels |
| 6.8 the gate | QA-12, PERF-09, QA-13, the consent batch | 1,000 AI matches in balance; the MVP perf base; the release-candidate checklist |

### 1.3 Demo script (L7, the owner; the MVP gate)

1. Title screen → **Skirmish** → map *First skirmish*, AI *Normal*, the rest default → **Start**. The arena
   world is created the first time and reused after; the progress bar runs; the match starts in the dark.
2. Scout with the horseman; gather all four resources; research a gathering technology at the storehouse;
   watch the feed and hear its sounds.
3. Open the phase panel on the civic centre: the checklist turns green; advance to Town (every player gets
   the notice); research iron weapons and chainmail and see them on the units.
4. The raid bar fills while the wave musters at its camp (campfires, banners), the horn sounds; hold the
   wave with soldiers, a defence tower and the bell.
5. Train two rams at the arsenal, raze the enemy's buildings; the end screen shows the statistics.
6. Once per difficulty, one of them against a dedicated server (`scripts/playtest.sh`, then
   `/strategos skirmish strategos:first_skirmish level=<easy|normal|hard>`); win on Easy; the questionnaire
   (`docs/testing/playtest.md`, the MVP version from T25), with no score of 2 or less; "would I play
   another?" with its reason. Two friends play one match each with the hints alone (UI-14.4).

### 1.4 Cards, tasks and checks (traceability)

L0 (`verifyFast`: formatting, ErrorProne, NullAway, ArchUnit) holds for every card and is not repeated below.

| Card | Task | Checks (level) |
|---|---|---|
| TECH-01 | T2.1, T2.2 | 1 `RequirementTest.theTable` (L1) · 2 `ResearchTest.aCompletedTechChangesStatsAtOnce`, `ResearchTest.theBreakdownNamesTheTech` (L1) · 3 `ResearchTest.onceAndInOneBuildingAtATime` (L1) · 4 `techtests.a_research_completes_and_applies` (L3) |
| TECH-02 | T3 | 1 `PhaseTest.requirementsTable` (L1) · 2 `PhaseTest.townUnlocksExactlyItsList` (L1) · 3 `PhaseTest.townCostsStoneOrIron` (L1) · 4 `techtests.every_player_gets_the_phase_notice` (L3) |
| TECH-03 | T18 | 1 `TechEffectTest.eachEffectEqualsItsModifier` (L1) · 2 `BalanceSheetTest` lists every technology and tier (L1) · 3 `techtests.a_tier_reaches_every_viewer_within_a_second` (L3) · 4 `m6_goldens` a spearman before and after (L4, T27) |
| CMB-09 | T19 | 1 `SiegeTest.twoRamsRazeACentreInTheSheetTime`, `SiegeTest.torchesTakeFiveMinutesOnStone` (L1, row 14) · 2 `SiegeTest.ramsNeverAttackUnits` (L1) · 3 the engine-surface inventory lists `unit_large` with ADR-0011's settings (L2) · 4 `pathtests.rams_pass_only_gaps_that_fit` (L3) · L5: `runServerSmoke` spawns, saves and restores a ram |
| BLD-17 | T20 | 1 `TowerTest.arrowsAndDpsAreTheSheets` (L1) · 2 `TowerTest.townOnlyAndStone` (L1) · 3 `TowerTest.onlyEnemies` (L1) · 4 the blueprint lint and `constructiontests.tower_*` (construction, damage stages) (L3) |
| MATCH-14 | T11 | 1 L4 scenario `arena_world`: the world created from code loads with the preset's biome and the rules applied once (L4, at T11's end) · 2 `docs/spikes/S13-arena-world-from-code.md` (T11): how MATCH-02 creates and reuses the world (R) |
| MATCH-02 | T13 | L1 `SkirmishSetupTest` (codec, defaults, bounds) · 1 `skirmishtests.the_pipeline_creates_the_match` (L3) · 2 `skirmishtests.a_second_skirmish_takes_the_next_slot` (L3) · 3 L4 `skirmish_flow`: title screen to a running match in ≤ 5 clicks (T27) · 4 `m6_goldens` setup screen (L4, T27) |
| MATCH-03 | T4 | 1 `ConquestTest.defeatWithinTwentyTicks` (L1) · 2 `ConquestTest.civicCentresOnly` (L1) · 3 `MatchStatsTest.theEndScreenEqualsTheCounters` (L1) · 4 `matchtests.conquest_ends_a_match` (L3) · 5 `m6_goldens` end screen (L4, T27) |
| MAP-02 | T12 | 1 `FirstSkirmishTest.resourcesWithinFivePercentAndDropsitesWithinTen` (L1) · 2 `FirstSkirmishTest.everyTerrainJobHolds` (L1) · 3 `FirstSkirmishTest.everyStartReachesEveryOtherPerClass` (L1) · 4 QA-12's start split (L1, T23) · 5 `maptests.first_skirmish_imports_with_its_hash` (L3) |
| AI-04 | T21 | 1 `WaveTest.announcedThirtySecondsAhead` (L1) · 2 `DefenceTest.defendersWithinTenSecondsAndTheBell` (L1) · 3 `OpeningTest.seedsPickDifferentOpenings` (L1) · 4 `aitests.waves_reach_the_base` with no ladder rung above repath (L3, slow sample) · 5 `aitests.the_raid_bar_reaches_only_its_audience` (L3) · 6 `m6_goldens` raid bar (L4, T27) |
| AI-05 | T22 | 1 `DifficultyTest.normalReachesTownOnTime` (L1, 10 seeds) · 2 `:core:openingBalance`'s level pairings (L1, 200 seeds, T23) · 3 `DifficultyTest.noBonusAndEveryHandicapListed` (L1) |
| UI-20 | T16 | 1 `MinimapOrderTest.aRightClickSendsOneMove` (L1) · 2 `MinimapTilesTest.aChangedChunkWithinASecondAtMostOnce` (L1) · 3 `nettests.a_minimap_order_applies` (L3) · 4 `MinimapBench` < 0.5 ms (L6), PERF-14 budget F (T27) · 5 `m6_goldens` a ping (L4, T27) |
| UI-21 | T14 | 1 `NotificationFeedTest.prioritiesAndRateLimits` (L1) · 2 `AlertSoundsTest.oneSoundEachWithinItsLimit` (L1) · 3 `m6_goldens` feed (L4, T27) · 4 the owner's audio checklist (L7) |
| UI-13 | T15 | 1 `PhasePanelTest.theChecklistMirrorsTheServer` (L1) · 2 `persistencetests.research_progress_survives_save_and_load` (L3) · 3 `m6_goldens` research card and phase panel (L4, T27) |
| UI-14 | T17 | 1 `HintBookTest.eachHintOncePerProfile` (L1) · 2 `ControlsOverlayTest.showsTheCurrentBindings` (L1) · 3 `m6_goldens` overlay (L4, T27) · 4 a friend's first five minutes (L7) |
| FOW-01 | T5 | 1 `VisionGridTest.equalsBruteForce` (L1) · 2 `VisibilityIndexTest.revealAtOnceHideAfterTen` (L1) · 3 `VisionGridTest.anObserverAddsNothing` (L1) · 4 `VisionBench`, `perftests.mvp_soak` budget A (L6, T24) · 5 `FogCommandTest.hiddenIsUnknownTargetVisibleIsNotOwner` (L1) |
| FOW-07 | T6, T7 | 1 `ExploredTest.neverReverts` (L1) · 2 `fogtests.a_hidden_enemy_is_never_paired` (L3) · 3 `fogtests.pairs_within_6_unpairs_within_15` (L3) · 4 `persistencetests.explored_survives_save_and_load` (L3) · 5 `ShroudBench` (L6), PERF-14 (T27) · 6 `m6_goldens` shroud, with Sodium, with Iris and a shader pack (L4, T27) · L5: `runServerSmoke` runs with fog on |
| UI-16 | T8 | 1 `MinimapFogTest.pixelsUnderFogEqualTheHash` (L1) · 2 `fogtests.no_dot_data_for_fogged_units` (L3) · 3 `m6_goldens` minimap fog (L4, T27) |
| AI-18 | T9 | 1 `FairAiTest.everyTargetWasVisible` (L1) · 2 `FairAiTest.findsTheBaseFromTheStarts` (L1) · 3 `FairAiTest.foggedMatchesEndWithin45` (L1, slow) |
| MATCH-07 | T10 | 1 `VisibilityOptionsTest.eachOptionInitialisesTheGrids` (L1) · 2 `fogtests.a_revealed_map_shows_everything` (L3) |
| QA-12 | T23 | 1–3 `:core:openingBalance` (L1, 200 seeds per pairing; the summary) · 4 `aisoak.*` (L3, 20 seeds, `verifySoak`) |
| QA-13 | T28 | 1 `checkNaming` (L0) · 2 `CommandCardIconsTest` (L2) · 3 every MVP blueprint's construction and damage-stage GameTests (L3) · 4 `runServerSmokeJar` (L5) · 5 the jar on a client: mc-runtime-test on CI, or L4 in T27 · 6 `docs/provenance.md` (D, T28) · 7 the owner's sign-off (L7) |
| PERF-09 | T24 | 1 `perftests.mvp_soak` A–E and G (L6) · 2 C p95 ≤ 15.3 ms (L6) · 3 `perftests.mvp_stress` C p95 ≤ 50 ms (L6) · 4 `perf/mvp-m4pro.json` and the report (R) |

### 1.5 Moved out of M6, with the reason

None. The `strategos:ping` payload stays with multiplayer pings (UI-15, M7); UI-20's alert pings need none
(row 3).

### 1.6 Differences resolved

| # | Topic | Resolution |
|---|---|---|
| 1 | Phases | The architecture names no phase concept. A phase is a technology with the class `Phase` and an ordinal; a faction's phase is the highest phase researched, and `phase_village` is the phase of a faction that has researched none (row 19) |
| 2 | Moves into the dark | 04 §7.3 and ADR-0014 ask positions "in explored area" for every command; scouting needs moves into the shroud. [ADR-0025](../adr/0025-commands-into-unexplored-cells.md) (Proposed) keeps the explored check for placement (`Placing`, `ClientPlacement`), node targets (`NOT_EXPLORED`) and the observer's footing (`CommanderService`, ADR-0023), and lets move, attack-move and rally points lie anywhere in the match space. T6 wires all of them and amends 04 §7.3 |
| 3 | Pings | 04 §12.2 lists `ping` (C2S) for M7. UI-20's alert pings are drawn by the client from `match_event` alerts, so M6 adds no payload; UI-15 keeps `ping` |
| 4 | AI think offset | 04 §4.5 says `(5 × i) mod 20`; the code (M3) wakes at `20k + 7 × ((faction + seed) mod factions)`. The code stands, and the think period becomes a per-level script value (§2.20); T22 corrects 04 §4.5 |
| 5 | Equipment tiers' transport | TECH-03 leaves it open: a faction-level value, or spawn data with a re-pair. Faction tiers ride a new `looks` field of `match_state` (Match audience, on change and at join); no body is re-paired. T18 records it on the card and amends 04 §12.2 |
| 6 | Fog comments | `FogGate` and `NetGateway` say fog comes in M8, `Visibility` and `Replication` say M6. Solo fog is M6 (ADR-0023); T6 corrects the comments |
| 7 | Maps in the jar | `checkJarContents` refuses every map package, so the released jar has no playable map. It becomes an allowlist of shipped maps (`first_skirmish`); the proving ground stays in devtools (T12) |
| 8 | Statistics | The architecture is silent on their content. `MatchStats` lives in the core; `MatchStateMsg` gains the `results` 04 §12.2 already lists for it (the record has none yet), sent at ENDED; `match_event` ENDED stays the trigger. No new payload |
| 9 | Levels and bonuses | D-19 allows bonuses only at a labelled top level. M6's Easy, Normal and Hard take none: Hard is stronger by timing, reaction and decisions. If AI-05.2 cannot hold that way, Hard becomes the labelled top level with an open bonus (R6) |
| 10 | Starting a skirmish | The setup screen runs on an integrated server's client: it sends `strategos:skirmish` (C2S, the host only, 1/s, like `match_control`) rather than a command, which needs a match. A dedicated server uses `/strategos skirmish <map> [level=…] [visibility=…]`, which replaces 04 §15.3's `[ai=<profile>]`; T13 amends 04 §15.3 |
| 11 | Conquest-critical | Every unit, and every building past FOUNDATION, except the classes `Field` and `Decoration`; Gaia never counts. A foundation keeps no faction alive, so placing one cannot stall a match |
| 12 | The L3 sample's time | Twenty matches at 20 ticks a second take about ten hours. `aisoak` runs them under `/tick sprint` (§0.4), and T23 states the wall time |
| 13 | Enemy buildings under fog | As ADR-0016 and 04 §12.2: an enemy building's `building_state` is a last-seen record, sent once seen and frozen while out of vision. The minimap draws no ghost icon (UI-16; FOW-03 in M8); the AI reads only buildings in vision (AI-18; AI-07's memory in M8). Block changes stay visible until FOW-06 (04 §10.5) |
| 14 | Stone needs the ram | D-12 lets torches burn stone, slower than wood; CMB-09 says stone buildings "need the ram". Read as a balance target: a stone civic centre falls to two rams within 60 s and to the same cost in torch-bearers in no less than 5 minutes (sheet rows; `SiegeTest.torchesTakeFiveMinutesOnStone`) |
| 15 | A player in no match | `FogGate.decide` shows every body to a player in no match (M1 spec §1.6 row 8: an operator watching the arena), unlike 04 §10.4's default. M6 keeps it: solo play has no such viewer, and the rule for non-participants comes with multiplayer (M7, M8). The no-holder fallback (`BodyEvents.canSee` → `decide(match, 0)`) keeps today's answer |
| 16 | The victory cadence | `VictoryCheck` runs every tick in FINAL (M3), not every 20 ticks as 04 §4.5 has it; `CIVIC_CENTRE` keeps that. `CONQUEST`'s recount runs every 20 ticks at offset 13, clear of the AI's offsets 0 and 7 with two factions |
| 17 | The WAVE notice | Stays as M3 sends it, to every participant of the match; the raid bar draws from it, and a viewer in another match gets none (AI-04.5) |
| 18 | Tech entries in the queue (T2.2) | `BuildingStore`'s queue holds unit template net indices, and about ten readers (`Production`, `QueriesImpl`, `ReplicationService` and `BuildingState`, `ProductionManager`, the client's `SelectionPanel`, `RtsHud`, `CardController`) take an entry for a unit. A tech entry is a new entry kind in the nested `BuildingStore.Production` record (the `BUILDING` codec is at 16 fields too); every reader branches on it, and `building_state` carries the kind in one bit |
| 19 | Village without a tech (T2.2, T3) | Researching `phase_village` at tick 0 would make `Progress` non-empty in every match and change every saved hash and journal. Village is the phase of a faction with no phase researched; `phase_village` stays a data entry for its name and icon, never queued |
| 20 | Stable tech ids (T2.1) | A bitset over tech indices breaks when a reload reorders the techs. Techs get an append-only id table, as templates have (`ServerTemplates`), and `Progress` stores those ids |
| 21 | Techs in the template set (T2.1) | Techs join the set's canonical JSON under the key `$techs`, which no template id can take, only when there are any, so sets without techs keep their hash; they travel in `TemplateSetView`, or a client would never ack the set and a journal with a `ContentSwap` would stop decoding |
| 22 | Modifiers at restore (T2.2) | Nothing rebuilds `StatService` at restore today: `MatchState` starts it empty. T2.2 re-adds the researched techs' effects in `Snapshots`' restore before `Production.recount` (housing reads modified values) and in `MatchState.swapTemplates` after `stats.swap` |
| 23 | The card's token lists (T15) | `CommandCard` reads a building's `Trainer/units` as raw template text, so a `TOKENS` unlock never shows on the card; T15 reads the effective lists. `CommandCard.QUEUE_MAX` (8) disagrees with `BuildingStore.QUEUE_MAX` (5); T15 makes them one |

### 1.7 Consent plan

Everything that needs a window runs back to back in T27, one window at a time (AGENTS.md "Client windows"), in
about 60 minutes:
- **`m6_goldens`**: the spearman before and after its tiers; the end screen; the setup screen; the raid
  bar; the feed; the research card and the phase panel; the controls overlay; a minimap ping; the minimap
  under fog; the shroud (unexplored, explored, visible). Each static scene twice (D-22).
- **The same shroud scene** with Sodium, and with Iris and a shader pack (`-Pcompat` adds both). The pack is named in
  T7's §1.6 row once its licence allows a test-only download; otherwise that one view is the owner's own
  check (L7).
- **The other runs**: `skirmish_flow` (≤ 5 clicks); the M6 demo chapters (T25); PERF-14 with M6's HUD and the
  shroud (budget F); the production jar on a client, unless mc-runtime-test ran on CI. `arena_world` runs
  earlier, at T11's end (§0.2).

While "Standing restrictions" forbids windows, T27 goes to the branch file's "For the owner" as a consent batch
(«запускай консент-батч», "run the consent batch"). The rest of M6 does not wait for it.

---

## 2. Design of the M6 slice

### 2.1 Technologies and research (TECH-01, 04 §8.1, §11.5)

- **Data.** `strategos/techs/<id>.json`, loaded by `TemplateReloadListener` beside the templates (04 §8.4)
  into `TechDef` (`core.content`): `name`, `icon`, `classes`, `cost` (resources), `time` (seconds, rounded
  once to ticks), `requires` (a requirement tree), `supersedes` (a tech id), `effects` (DATA-02 modifiers,
  `TOKENS` included). The `TemplateSet` hash covers the techs (row 21); their ids are stable (row 20).
- **Requirements.** `Requirement` is a sealed tree: `all[…]`, `any[…]`, `tech: <id>`, `phase: <name>`,
  `buildings: {filter: <classes>, count: n}` (own COMPLETE buildings matching a class filter). The pure
  `Requirements.evaluate(req, FactionView)` and `Requirements.checklist(req, FactionView)` (each leaf with
  met/unmet and its amounts) serve the server's rules, the phase panel (§2.13) and the AI alike.
- **Who researches.** A building template lists `Researcher/techs` (tokens). `Research(building, tech)`
  (31): an own COMPLETE building whose list holds the tech; the tech neither researched nor pending in the
  faction; requirements met; the queue below `BuildingStore.QUEUE_MAX` (5); the cost paid at queue time.
  Reasons: `UNKNOWN_TEMPLATE` (no such tech), `LIMIT` (researched or pending), `REQUIREMENTS` (new, the last
  `RejectReason`), `QUEUE_FULL`, `NOT_ENOUGH_RESOURCES`. Unit batches and techs share the queue; the entry
  kind is new (row 18).
- **Completion.** In ECONOMY, as a unit batch completes today (`Production`): the tech joins the faction's
  researched set, its effects join `StatService` with the tech as `sourceId`, a superseded tech's effects
  leave, `TechResearched(faction, tech)` is posted (the feed, the statistics). `StatsChanged` already updates
  every cached block and the breakdown names the source.
- **Store.** `Progress` in `Play`: per faction the researched techs by their stable ids (row 20). At restore,
  `StatService` is rebuilt from it (§0.3, row 22). A cancel (`CancelTrain` on a tech entry) refunds the cost and
  clears the pending mark.

### 2.2 Phases (TECH-02, D-05)

- `phase_village` (class `Phase`, ordinal 0) is the phase of a faction with no phase researched (row 19). `phase_town`
  (ordinal 1) at the civic centre: starting cost 500 food, 500 wood, 300 stone, 200 iron, 60 s; requires
  `buildings: {filter: Village, count: 5}`, with the class `Village` added to the M4 set's houses,
  storehouse, farmstead and barracks. The numbers are sheet rows; T23 tunes them against QA-12.
- **Unlocks** are the phase's `TOKENS` effects, listed in its `unlocks` field, which the L1 test compares
  with the token diff: the arsenal and the ram (§2.17), the defence tower (§2.18), the Town technologies
  (§2.16's military tiers). **Bonuses**: +10 % building HP and +2 building vision (sheet rows, modifiers).
- **The notice.** `match_event` PHASE(faction, phase) to the Match audience: every player, the feed (§2.12),
  the statistics' timeline (§2.3).

### 2.3 Conquest, defeat and statistics (MATCH-03)

- **Conquest.** `PlayRules.Victory` gains `CONQUEST`, the skirmish default; `CIVIC_CENTRE` stays as the
  "civic centres only" option and keeps its check every tick (row 16). Under `CONQUEST`, `ConquestCount`
  recounts each faction's conquest-critical entities (row 11: units, and buildings CONSTRUCTING or COMPLETE)
  over the unit and building stores every 20 ticks at offset 13, so a defeat lands within 20 ticks; a faction
  with none left is defeated with the new reason `CONQUERED`. `Resign` (15) is as M3 built it.
- **Statistics.** `MatchStats` in `Play`, per faction, the card's list: resources gathered per type (from
  `Treasury`), units trained and lost, buildings built and lost, and the tick each phase was reached. Saved
  and hashed when non-empty.
- **The end screen.** At ENDED, `MatchStateMsg` carries `MatchResults(winner, endedAt, stats)` (row 8). The
  end layer (`HudBanners.end`) becomes `EndScreen`: victory or defeat, a table per faction, the phase
  timeline, **Play again** (the same setup in the next free slot, §2.11) and **Title screen**.

### 2.4 The vision grid (FOW-01, 04 §10.2–10.3)

- **`VisionGrid`** (`sim.vision`), per faction: 4-block cells over the match space, `short[]` counts and a
  `long[]` explored bitset (9,216 cells at 384²). Sources: units by their `Vision/range` and buildings by
  their template's; an observer adds none (D-08). Each source keeps the cell and radius it stamped; when it
  crosses a cell or its vision changes, its disc is unstamped and restamped from `DiscOffsets`, one table per
  radius in cells. It runs in WORLD after MOVEMENT's `UnitGrid` rebuild, within 150,000 cell updates a tick.
- **`VisibilityIndex`**, per faction: a bitset over unit handles. A unit is visible when it is own or its
  cell's count is above 0; it is revealed at once and hidden 10 ticks after it was last seen. The view is
  immutable for the tick; `Visibility` (replication) and `FogGate` read it.
- **State.** Counts are derived and rebuilt at restore from positions. The explored bits and the hysteresis
  set (unit, last-seen tick) shape commands, so they are core state: `vision` in `Play`, saved and hashed.
- **Commands.** The handle resolver reads the issuer's faction view: an enemy unit hidden from it is
  `UNKNOWN_TARGET` exactly as a missing handle; a visible enemy is `NOT_OWNER` as a unit to order and
  accepted as a target. The admin sees everything. The explored checks, all three stubs returning true
  today, read the explored bits (row 2, ADR-0025): `Placing` and `ClientPlacement` for placement,
  `CommanderService` for the observer's footing; a gather on a node in an unexplored cell is `NOT_EXPLORED`.
- **Measured** by `VisionBench` (JMH: 300 units moving, stamping and unstamping) before T6 builds on it.

### 2.5 Explored state, gating and the shroud (FOW-07, 04 §10.4–10.5)

- **Explored** bits are set when a cell's count first rises; they never clear.
- **`vision_delta`** (S2C, own faction, 2 Hz): the visible bitmap's changed runs and the newly explored
  cells since the last send, run-length encoded; a full frame at join and resume. Its bytes are measured
  against budget E in T6.
- **Gating.** `FogGate.canSee` gains the body's faction and handle and the viewer's seat: another match
  false; a player in no match true, as today (row 15); observer or ADMIN true; own faction true; an enemy
  body only while the viewer faction's `VisibilityIndex` holds it. It still fails closed. `TrackerRefresher` (`server.fog`, every 5 ticks at offset 1): for each viewer whose
  faction's visibility flipped a unit since the last refresh, `ServerChunkCache#move(viewer)`, which
  re-evaluates every tracked body for that viewer (§0.4). No mixin, no access transformer.
- **Summaries.** `Replication.visibility` is fed from `VisibilityIndex` instead of `ALL`; `unit_summary`
  already leaves out what it hides. An enemy building's `building_state` becomes a last-seen record
  (row 13).
- **The shroud** (`mod.client.fog.ShroudRenderer`): from the client's grid (`ClientMatchModel.vision`), per
  16 × 16 page, translucent quads laid on the terrain's top surface: unexplored black (α 1), explored dimmed
  (α 0.5), visible clear, with soft edges from per-corner alpha. Drawn at
  `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS` with its vanilla translucent render type, which
  shader packs keep; a page's mesh is rebuilt only when its cells change. `ShroudBench` measures the
  extraction for 384².
- **Saved** with the match (`vision` in `Play`).

### 2.6 Minimap fog (UI-16)

`MinimapImage` composes the terrain tile with the fog: unexplored black, explored at half brightness, visible
clear; enemy dots come only from `unit_summary`, which fog already filters. The L1 test hashes the pixel
array of a fixed grid.

### 2.7 The AI under fog (AI-18)

`AiView.enemyUnits` and `enemyBuildings` filter by the faction's `VisibilityIndex` (and, for buildings, a
visible footprint cell) unless `revealMap` holds; `AiView.startPositions()` gives the map's start slots.
With no enemy building visible, `MilitaryManager` attack-moves to the enemy start. `aiVision` (omniscience)
stays for a labelled top level; no M6 level sets it (row 9).

### 2.8 Visibility options (MATCH-07)

`PlayRules` gains `explored` beside `revealMap`. Setup offers **Fog** (default), **Explored** (every cell
explored at tick 0, vision as usual) and **Revealed** (`revealMap`: every cell visible, `FogGate` true for
every participant). The grids are initialised at the match's start; `RuleChange` accepts both keys.

### 2.9 The arena world from code (MATCH-14)

`ArenaWorldLauncher` (`mod.client.menu`): when `LevelStorageSource#levelExists("strategos_skirmish")`,
`WorldOpenFlows#openWorld`; otherwise `createFreshLevel` with `ArenaWorld.PRESET`'s dimensions (as
`ScenarioRunner.createWorld` does with `WorldPresets.FLAT`), cheats off. `ArenaWorld.onStarted` applies the
rules once, as today. The spike (T11, `docs/spikes/S13-arena-world-from-code.md`) records the
`LevelSettings` (game type, difficulty, cheats), the reuse rule and the failure path (a world that is not
an arena: refused with a message).

### 2.10 The first skirmish map (MAP-02, D-17)

- **Source.** `FirstSkirmish` (core testFixtures, as `ProvingGround`): seeded, 384² and 64 high, version 1;
  `MapgenDriver` writes its package to main resources (`data/strategos/strategos/maps/first_skirmish.json`
  and its tiles) with its terrain hash.
- **Starts.** Two, point-symmetric about the centre; each a civic centre, five workers and a horseman scout
  (**AFTER-M5:** `unit/horseman` is UNIT-07's); within walking distance: two forests, a stone outcrop, an iron
  deposit, berry bushes, livestock and room for six fields.
- **Terrain jobs** (the manifest's `terrainJobs`, each verified by the checker): a hill over the contested
  middle at least 6 blocks above it (CMB-16's bonus grows from Δh > 0, M5 §2.3); a forest line between the
  bases with three gaps, each at least 4 blocks wide; a river with two fords; a large iron deposit in the
  middle.
- **`MapChecker`** (`core.content`, pure): resource totals within 48 blocks of each start within 5 %;
  walking distances from each civic centre to its dropsite sites within 10 %; every start reaches every
  other for each mobility class (**AFTER-M5:** the large class, PATH-11); each terrain job.
- **Hand-finishing** is the owner's task with a date (§10); the checker re-runs on the finished package.

### 2.11 Skirmish setup and launch (MATCH-02)

- **Entry.** `ScreenEvent.Init.Post` on `TitleScreen` adds **Skirmish** under Singleplayer.
- **`SkirmishScreen`** (`mod.client.menu`): map (the shipped maps), civilisation (the one, shown), AI level
  (Easy, Normal, Hard, each with its handicaps listed, D-19), starting resources (the map's, low, high),
  population cap (50–200, default 150, D-01), visibility (§2.8), **Start**. Two clicks with the defaults.
- **`SkirmishSetup`** (core record with its codec, defaults and bounds) → `ArenaWorldLauncher` → on joining,
  the client sends `strategos:skirmish` → `Skirmishes.start(setup, player)`:
  `MatchRegistry.createInArena(mapId, opts)` in the next slot; rules (victory, population cap, resources,
  visibility); slot 0 the player, slot 1 `ai:skirmish` at the level (§2.20); prepare with its progress bar;
  start; the player joins slot 0 in RTS mode. The headless pipeline is the same call (L3).
- **Dedicated server.** `/strategos skirmish <map> [level=…] [visibility=…]` seats the caller in slot 0;
  `skirmish <map> observe` stays (QA-11).

### 2.12 The notification feed and alert audio (UI-21)

- **`NotificationFeed`** (`core.client.hud`): entries (kind, priority, text, tick, jump target). Priorities:
  alerts (under attack, a wave) over phase, then research, then training and building complete, then UI-10's
  economy notices. Rate limits per kind (under attack once per 10 s per 32-block area; each economy notice
  once per 15 s); six visible, each fading after 8 s; J jumps to the newest alert, as UI-12's does.
- **Sources.** `EconomyNotices`, UI-12's alert, `match_event` PHASE and WAVE, and own `feedback` events for
  research, training and building complete.
- **`AlertSounds`**: under attack `BELL_BLOCK` and a wave `RAID_HORN` (as today), phase `UI_TOAST_CHALLENGE_COMPLETE`,
  research `PLAYER_LEVELUP`, training `VILLAGER_CELEBRATE`, building `ANVIL_USE` (pitch 1.2, volume 0.4);
  alerts under `SoundSource.HOSTILE`, the rest under `NEUTRAL`, so vanilla's sliders set them; each sound at
  most once per 3 s by `AlertSounds`' own limiter (`SoundBudget`'s per-tick cap stays for unit sounds). The
  horn and the bell move from `RtsHud` into `AlertSounds`, played locally through `FxPlayer`. The owner's
  audio checklist (L7) judges the choice.

### 2.13 Research and phase UI (UI-13)

- `CommandCard` shows a building's `Researcher/techs` as buttons with cost, time, the effects' breakdown in
  the tooltip and the disabled reason (unmet requirements named); the queue shows research entries with
  progress.
- **The phase panel** above the card when the civic centre is selected: one row per leaf of
  `Requirements.checklist(phase_town, view)` with its amounts, the same code as the server's, fed from the
  client's faction view. `faction_state` gains the researched set, the phase and research entries in its
  queue summaries.

### 2.14 Minimap orders, pings and live construction (UI-20)

- A right click on the minimap sends one `Move` of the selection to the mapped point (Shift queues it);
  the mapping is `Minimap`'s (core, pure).
- **Pings**: rings at the positions of `match_event` alerts and wave musters, 3 s each (row 3).
- **Live construction**: a chunk is dirty on a client block update in it (the hook named in T16) or a
  `building_state` progress change; a dirty tile is re-extracted within 1 s and at most once a second.
  `MinimapBench` keeps the extraction under 0.5 ms.

### 2.15 First-match hints and the controls overlay (UI-14)

- **`HintBook`** (`core.client`): the card's three hints, with conditions over `ClientMatchModel`: the first
  selection (how to order), the first idle worker (the idle button), two below the population cap (build a
  house). Each dismissible and shown once per profile, the seen ids kept in the client config
  (`strategos-client.toml`, 04 §15.4's one home).
- **The overlay** (`K`, free in every context; rebindable as `key.strategos.controls`) lists the mod's key mappings with their
  current keys (`KeyMapping#getTranslatedKeyMessage`), rebinds included.

### 2.16 Starting technologies and equipment tiers (TECH-03)

- **Village**: wood gathering at the storehouse, farming at the farmstead, stone and iron mining at the
  storehouse (each +20 % to its rate), carry capacity (+5 for every resource) at the storehouse.
- **Town**, at the barracks: iron weapons (+1 hack and +1 pierce attack) and chainmail (+1 hack and
  +1 pierce resistance). Every number is a sheet row.
- **Tiers.** The two military techs raise the faction values `Look/weapon` and `Look/armour` (0 or 1), which
  ride `match_state`'s new `looks` field (row 5). A unit template's `Body/equipment` maps the tiers to vanilla items: weapon
  stone → iron (`stone_sword`, `iron_sword`, or the spear's own), armour `LEATHER` → `CHAIN`. The renderer
  draws the armour layer and the held item for the unit's faction tier; a change reaches every viewer with
  the next `match_state`.
- **AFTER-M5:** the archer's and horseman's tiers follow UNIT-07's roster; the spearman (M2) carries them
  from T18.

### 2.17 The ram and the arsenal (CMB-09, D-12)

- **`strategos:unit_large`** in `ModEntityTypes`: 2.0 × 2.0, tracking range 12, interval 3 (ADR-0011); a wider
  culling box; the engine-surface inventory gains its settings.
- **The `ram` archetype**: our own model class of cubes (a roofed frame on four wheels with a log head); no
  vanilla entity model.
- **`unit/ram`**: crush only, `Attack/Melee/restrictedClasses` `Unit Field` (the rules refuse such targets
  and it never picks one), 3 population, the large mobility class (**AFTER-M5:** PATH-11), low hack
  resistance so soldiers beat it in melee. Two rams raze a civic centre in the sheet's time, and stone
  needs them (row 14).
- **The arsenal**: a Town building with its blueprint and damage stages, training rams.

### 2.18 The defence tower (BLD-17)

A Town building that costs stone (and wood), with `Attack/Building` from CMB-07 (**AFTER-M5:** M5 §2.8):
1 arrow every 40 ticks, 10 pierce, range 28, enemies only; vision 24; a blueprint with damage stages; no
garrison (BLD-12, M9).

### 2.19 AI openings, waves, the raid bar and defence (AI-04)

- **Openings.** `StrategyScript` gains `openings`: rush, boom and balanced, each with a weight, its build
  order, worker target, soldier queue and attack schedule. The opening is drawn at the match's start from
  `Rng(seed, OPENING, 0, faction, 0)` by weight and kept in `AiMemory` slot 11.
- **Waves.** A wave musters at a camp on the path to the enemy, at least 40 blocks out from its own centre,
  marked with campfires and banners (cosmetic writes, removed when it leaves). `AnnounceWave` (17) at the
  muster's start names the leave tick, at least 30 s ahead at every level (`AnnounceWave.LEAST`, 600 ticks,
  holds for every AI; the card asks it of Easy and Normal); from the `match_event` WAVE (row 17) the clients
  draw **the raid bar**: our GUI
  layer, red and notched like a vanilla raid (`BossBarOverlay.NOTCHED_10`'s look; no `ServerBossEvent`),
  filling with the muster and then counting down; `RAID_HORN` as `fx`.
- **`DefenceManager`**: when its base is attacked (`AiSlots.wake`), it commits soldiers within 10 s; it
  rings the bell (`RingBell` 28, **AFTER-M5:** BLD-16) when two workers are hit within 5 s, and sends
  `AllClear` (29) after 20 s with no enemy within 32 blocks; units below 30 % HP retreat to the centre.
  Targets: the army in range, then the economy, then the civic centre.
- **`RebuildManager`**: a lost building is placed again on its site when affordable. All within `AiBudget`.

### 2.20 AI phases, research and difficulty (AI-05, D-19)

- Build orders gain the steps `{"research": <tech>}` and `{"phase": "town"}`.
- `ai/skirmish.json` holds the openings and `levels`. The setup resolves the chosen level into the script
  `SetAi` carries, so a journal replays it:

| Knob | Easy | Normal | Hard |
|---|---|---|---|
| Think period | 40 ticks | 20 | 20 |
| Reaction before a defence | 100 ticks | 60 | 20 |
| Commands per think | 8 | 16 | 32 |
| Wave size | × 0.6 | × 1 | × 1.4 |
| Counters, focus, retreat (AI-03) | off | on | on |
| Open handicap (a faction modifier source `ai_handicap`, shown in setup) | gather × 0.85, train time × 1.15 | none | none |

- No level takes a bonus (row 9). Normal's Town time is `targets.json`'s 12:00, held to ±2 minutes.
- Easy's worst case from an attack to its defenders' command is 100 + 40 + 1 = 141 ticks, within AI-04.2's
  10 s. An L1 test checks that the resolved script fits `SetAi`'s limit (§0.3).

### 2.21 The soak and opening balance (QA-12)

- **`:core:openingBalance`** (L1): rush–boom, rush–balanced and boom–balanced at Normal, each side's opening
  pinned by the resolved script's `opening` field (which skips the draw); Hard–Normal and Normal–Easy with
  the scripts' weights; 200 seeds each, sides swapped on odd seeds; on MAP-02 with fog on.
  `HeadlessMatch` gains conquest, a 45-minute limit and its 2-minute stall watchdog. Matches run in
  parallel over the machine's cores, each single-threaded. It writes a JSON and a markdown summary: win rates
  per start, opening and level, median and longest match, per-match telemetry.
- **It asserts** QA-12's criteria and AI-05.2, MAP-02.4 and AI-18.3: every match ends by conquest within 45
  minutes, median 20–35; no exception and no stall; each opening 60–140 of 200 against each other; each level
  at least 124 against the one below; neither start 121 or more in any pairing.
- **`aisoak`** (L3): 20 seeds of Normal against Normal on the arena under `/tick sprint`, in `verifySoak`
  (`:mod:runGameTestSoak`), no win-rate threshold; each journal replays to its hash; the wall time stated.

### 2.22 The MVP perf gate (PERF-09)

`perftests.mvp_soak`: MAP-02 with `MvpScene` (§6): 2 × 150 population and 60 buildings, two Hard AIs active
for 30 game minutes paced at 20 ticks a second under `Victory.NONE`, so the run cannot end early; autosaves
included; budgets A–E and G; C p95 ≤
23 ms ÷ 1.5 (D-18's fastest speed) = 15.3 ms; per-unit p95 of A within 15 % of M5's battle base (**AFTER-M5:**
`perf/battle-m4pro.json`). `perftests.mvp_stress`: twice the scale, C p95 ≤ 50 ms only. The run on the owner's
M4 Pro is archived as `perf/mvp-m4pro.json`, the MVP baseline; other machines report beside it.

### 2.23 The MVP gate (QA-13)

- **`checkNaming`** (L0, in `verifyFast`): mod id, display name, tags, README title and `NOTICE` (the RVO2
  notice present; no "Age of Empires", "AoE" or "0 A.D." in the name, logo or primary tags).
- **`CommandCardIconsTest`** (L2): every command-card entry of every template and technology has an icon.
- **Blueprints**: every MVP blueprint (M3–M6) through its construction and damage-stage GameTests.
- **`runServerSmokeJar`** (L5): the built production jar, not the dev classpath, on a dedicated server; the
  client side by mc-runtime-test on CI, or L4 in T27.
- **`docs/provenance.md`** (D): one note per subsystem: where its behaviour, numbers and art
  come from.
- **The MVP regression suite**: feasibility §6.3's automated criteria, each mapped to the test that keeps it,
  in §8.

### 2.24 The demo and the playtest

New demo chapters (group `m6`): Research, Town, Fog, Raid, Siege, Tower, Notices. `docs/testing/playtest.md`
becomes the MVP protocol: the owner's match per level, the questionnaire, the audio checklist (UI-21.4), the
friends' test (UI-14.4), the report and the severities.

---

## 3. Build changes

| Change | Task | Why |
|---|---|---|
| `checkJarContents`: an allowlist of shipped maps | T12 | Row 7 |
| `:core:openingBalance` (parallel L1 runner) | T23 | QA-12; outside `verify` |
| `:mod:runGameTestSoak` (`^aisoak[.]`) in `verifySoak` | T23 | QA-12.4 |
| `runServerSmokeJar` | T28 | QA-13.4; joins `verify` if it fits the 5-minute budget, otherwise the choice goes to the owner (procedure §3.5) |
| `checkNaming` in `verifyFast` | T28 | QA-13.1 |
| A test-only download of a shader pack for the Iris golden (its licence checked; `-Pcompat` already adds Iris and Sodium) | T7 | FOW-07.6 |

---

## 4. Tests per level

As M5 §4, with these additions:

| Level | M6 adds |
|---|---|
| L0 | `verifyFast` for every card; ArchUnit: `sim.vision` free of the mod, `mod.client.fog` and `mod.client.menu` on the client side only; B6: the two new payloads only through `NetGateway`; `checkNaming` (T28) |
| L1 | The cards' tests (§1.4); the fogged AI matches and `DifficultyTest` tagged `slow` (`:core:slowTest`); `:core:openingBalance` outside `verify` |
| L2 | Round trips, size caps and audiences of `vision_delta`, `skirmish` and the grown `faction_state`, `match_state` and `building_state` (ADR-0016, ADR-0022); the engine-surface inventory (§0.3); `CommandCardIconsTest` |
| L3 | `techtests`, `fogtests`, `skirmishtests`; grown `matchtests`, `persistencetests`, `aitests`, `maptests`, `pathtests`, `constructiontests`, `nettests`; `aisoak` in `verifySoak` |
| L4 | `arena_world` (T11); `m6_goldens`, `skirmish_flow`, the demo chapters, PERF-14 (T27) |
| L5 | `runServerSmoke` with fog on, and a ram saved and restored; `runServerSmokeJar` (T28) |
| L6 | `VisionBench`, `ShroudBench`, `MinimapBench` (JMH, `jmhCompare`); `perftests.mvp_soak`, `mvp_stress` |
| L7 | §1.3 |

---

## 5. Measurements before building on them

| What | Where | Why |
|---|---|---|
| Vision stamping, 300 units moving | T5, `VisionBench` | WORLD's 0.5 ms, budget A |
| `move(viewer)` per refresh with 300 units | T6 | Budget B; R1 |
| `vision_delta` bytes at 2 Hz | T6 | Budget E |
| The shroud's extraction and draw | T7, `ShroudBench`; PERF-14 in T27 | Budget F; R2 |
| One headless match on MAP-02 | T12, then T23 | QA-12's wall time; R5 |
| An L3 match under `/tick sprint` | T23 | `verifySoak`'s wall time |

---

## 6. The MVP soak (T24) — PERF-09

§2.22. `MvpScene` (core testkit) is a `SoakScene` built directly, as M5's `BattleScene` is: each side's 150
population (workers on their nodes, the M5 roster and rams in squads) and 30 buildings placed at the start,
since a real match may never hold 2 × 150 at one tick. The report lists vision, the AI and the trackers'
refresh per tick beside M5's systems, and compares them with `:core:openingBalance`'s telemetry at the same
population (R8).

---

## 7. Task breakdown

### 7.1 Order

T1 → T2.1, T2.2 (TECH-01) → T3 (TECH-02) → T4 (MATCH-03) → T5 (FOW-01) → T6 (FOW-07 server) → T7 (FOW-07 client) → T8
(UI-16) → T9 (AI-18) → T10 (MATCH-07) → T11 (MATCH-14) → T12 (MAP-02) → T13 (MATCH-02) → T14 (UI-21) → T15
(UI-13) → T16 (UI-20) → T17 (UI-14) → T18 (TECH-03) → T19 (CMB-09) → T20 (BLD-17) → T21 (AI-04) → T22 (AI-05)
→ T23 (QA-12) → T24 (PERF-09) → T25 (the demo and the playtest) → T27 (the consent batch) → T28 (QA-13 and the
close). T26 is reserved for a row a task adds.

T2–T17 need nothing from M5 (T12's AFTER-M5 items are validated when M5 lands), so they may go first if M6
opens before M5 closes. T1 starts as soon as M5 closes, before T18; T18–T22 use M5's classes (**AFTER-M5**).

### 7.2 Tasks

Sizes, this spec's scale within the procedure's 300–1,500 lines a task: S up to 400 lines of change, M up to
900, L up to 1,500. Every task's verification is `./gradlew verify` plus the checks named; a task touching the
tick, orders, pathing or the AI adds `verifySlow verifySoak` (procedure §3.5), and one touching a `@HotPath`
kernel adds `:core:jmh :core:jmhCompare`.

#### T1 · Health check from M5's close
- **AFTER-M5:** M5's close lists its follow-ups (procedure §7.2); they become this task's content, split if
  over L. **Depends:** M5 closed. **Size:** set from M5's close. **Verification:** `verify verifySlow`.

#### T2.1 · TECH-01, the content
- §2.1's data: `TechDef`, `Requirement`, `Requirements` (`core.content`); `strategos/techs/` loaded beside the
  templates; the techs in the set's hash and in `TemplateSetView` (row 21); the stable id table (row 20); one
  sample tech (`gather_wood_1` at the storehouse) in `BuiltinTemplates` and the data pack, with its provenance;
  the lint's icon check. Split from T2 (procedure §3.7). **Depends:** — . **Size:** M. **Verification:**
  check 1 (`RequirementTest`); `BundledTemplatesTest`, `BalanceSheetTest`, `TemplateSetPayloadSizeTest`.

#### T2.2 · TECH-01, research
- §2.1's rules: `Research` (31), `REQUIREMENTS`; the tech entry kind and its readers (row 18); completion,
  cancel and refund; `Progress` in `Play`, saved and hashed; `StatService` re-derived at restore and at a
  swap (row 22); `TechResearched` and its applier. **Depends:** T2.1. **Size:** L. **Verification:** checks
  2–4; `:core:jmh :core:jmhCompare` (`StatService`'s cached lookup under 50 ns).

#### T3 · TECH-02
- §2.2; the `Village` class on the M4 set; the PHASE event. **Depends:** T2.2. **Size:** M. **Verification:**
  the card's checks; `:core:balanceSheet`.

#### T4 · MATCH-03
- §2.3; `CONQUEST`, `ConquestCount`, `MatchStats`, `EndScreen` without its golden. **Depends:** T3 (the phase
  timeline). **Size:** M. **Verification:** checks 1–4; 5 in T27.

#### T5 · FOW-01
- §2.4; `VisionGrid`, `VisibilityIndex`, the resolver's rule, `VisionBench`. **Depends:** — . **Size:** L.
  **Verification:** checks 1–3 and 5; `:core:jmh :core:jmhCompare`; `verify verifySlow verifySoak` (the tick).

#### T6 · FOW-07, the server
- §2.5 but the shroud: explored, `vision_delta`, `FogGate`, `TrackerRefresher`, the summaries, the save; 04
  §7.3's amendment (row 2) and the comments (row 6). **Depends:** T5. **Size:** L. **Verification:** checks
  1–4; L5 with fog on; `verify verifySlow verifySoak`.

#### T7 · FOW-07, the client
- §2.5's shroud; `ShroudBench`; `-Pcompat=iris` and the shader pack's licence (a §1.6 row). **Depends:** T6.
  **Size:** M. **Verification:** check 5's bench; check 6 in T27.

#### T8 · UI-16
- §2.6. **Depends:** T6. **Size:** S. **Verification:** checks 1–2; 3 in T27.

#### T9 · AI-18
- §2.7. **Depends:** T5. **Size:** S. **Verification:** the card's checks (3 on the proving ground until T12;
  again on MAP-02 in T23).

#### T10 · MATCH-07
- §2.8 in the core and the rules; the setup's control comes with T13. **Depends:** T6. **Size:** S.
  **Verification:** the card's checks.

#### T11 · MATCH-14
- §2.9; the `arena_world` L4 scenario; the spike report S13. **Depends:** — . **Size:** S.
  **Verification:** check 2 (R); check 1 at the task's end (one window, or `scenario.yml` by
  `workflow_dispatch`).

#### T12 · MAP-02
- §2.10; `FirstSkirmish`, `MapChecker`, the package, the jar's allowlist; one headless match's time.
  **Depends:** — (AFTER-M5: the large class's reachability, the horseman). **Size:** L. **Verification:**
  checks 1–3 and 5; `:mod:checkJarContents`.

#### T13 · MATCH-02
- §2.11; `SkirmishSetup`, `SkirmishScreen`, `strategos:skirmish`, `Skirmishes.start`, the dedicated-server
  form; MATCH-07's control. **Depends:** T10, T11 (its `arena_world` run passed), T12. **Size:** L. **Verification:** L1 and checks 1–2;
  3–4 in T27.

#### T14 · UI-21
- §2.12. **Depends:** T3. **Size:** M. **Verification:** checks 1–2; 3 in T27; 4 at the demo.

#### T15 · UI-13
- §2.13; `faction_state`'s new fields. **Depends:** T3. **Size:** M. **Verification:** checks 1–2; 3 in T27.

#### T16 · UI-20
- §2.14; `MinimapBench`. **Depends:** T14 (the alert positions). **Size:** M. **Verification:** checks 1–4's
  bench; 4's PERF-14 and 5 in T27.

#### T17 · UI-14
- §2.15. **Depends:** T14, T15. **Size:** M. **Verification:** checks 1–2; 3 in T27; 4 at the playtest.

#### T18 · TECH-03
- §2.16; the five Village techs, the two Town techs, `Look/*`, the renderer's layers; row 5 on the card.
  **Depends:** T3; AFTER-M5 for the roster's tiers. **Size:** M. **Verification:** checks 1–3; 4 in T27;
  `:core:balanceSheet`.

#### T19 · CMB-09
- §2.17; `unit_large`, the ram model, the arsenal and its blueprint. **Depends:** T3; M5's PATH-11 and UNIT-06
  (AFTER-M5). **Size:** L. **Verification:** the card's checks and row 14's torch test; the blueprint files test with
  `-Dstrategos.blueprints.update=true`; `verify verifySlow verifySoak`.

#### T20 · BLD-17
- §2.18. **Depends:** T3; M5's CMB-07 (AFTER-M5). **Size:** M. **Verification:** the card's checks.

#### T21 · AI-04
- §2.19; `OPENING` (10), `DefenceManager`, `RebuildManager`, the camp, the raid bar. **Depends:** T9, T12; M5's
  AI-03 and BLD-16 (AFTER-M5). **Size:** L. **Verification:** checks 1–5 with `verifySlow verifySoak`; 6 in
  T27.

#### T22 · AI-05
- §2.20; `ai/skirmish.json`, the levels, `ai_handicap`, the script-size test; 04 §4.5's correction (row 4).
  **Depends:** T3, T13 (the setup lists the handicaps), T21. **Size:** M. **Verification:** checks 1 and 3
  with `verifySlow verifySoak`; 2 in T23.

#### T23 · QA-12
- §2.21; `:core:openingBalance`, `aisoak`, `:mod:runGameTestSoak`; Town's cost and the openings tuned, each
  change a sheet row. **Depends:** T4, T9, T12, T22. **Size:** M. **Verification:** the card's checks; AI-05.2,
  MAP-02.4, AI-18.3 on MAP-02; the wall times recorded.

#### T24 · PERF-09
- §2.22, §6; `MvpScene`. **Depends:** T6, T18, T19, T20, T23. **Size:** S. **Verification:** the card's checks; the
  report.

#### T25 · The demo and the playtest
- §2.24. **Depends:** T7, T14, T19, T20, T22. **Size:** M. **Verification:** `-Pdemo=list` lists the chapters; each chapter's L1
  or L3 setup runs (its window in T27).

#### T27 · The consent batch
- §1.7. **Depends:** every task with an L4 check (T4, T7, T8, T13–T18, T21, T25). **Size:** S (runs and
  approvals). **Verification:** the goldens approved by the owner; PERF-14's
  report.

#### T28 · QA-13 and the M6 close
- §2.23, §8; `checkNaming`, `CommandCardIconsTest`, `runServerSmokeJar`, `provenance.md`; the Results; the health
  check. **Depends:** every other task. **Size:** M. **Verification:** `verify verifySlow verifySoak verifyPerf`;
  `:core:openingBalance`.

### 7.3 Progress

- [ ] T2.1 · [ ] T2.2 · [ ] T3 · [ ] T4 · [ ] T5 · [ ] T6 · [ ] T7 · [ ] T8 · [ ] T9 · [ ] T10 · [ ] T11 · [ ] T12
- [ ] T13 · [ ] T14 · [ ] T15 · [ ] T16 · [ ] T17 · [ ] T1 · [ ] T18 · [ ] T19 · [ ] T20 · [ ] T21
- [ ] T22 · [ ] T23 · [ ] T24 · [ ] T25 · [ ] T27 · [ ] T28

---

## 8. Acceptance checklist (M6 exit)

| # | Criterion | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the 23 cards passes at its level | §1.4 |
| 2 | L1, 200 seeds per pairing: every match by conquest within 45 minutes, median 20–35, no exception or stall; each opening 60–140 against each other; each level ≥ 124 against the one below; neither start ≥ 121 | `:core:openingBalance` |
| 3 | L3: conquest ends the match; save and resume keep the state hash; pause stops the match while the camera and UI work; an enemy outside vision is never paired while a visible control unit is | `matchtests`, `persistencetests`, `fogtests` |
| 4 | L6: 2 × 150 and 60 buildings, both sides active, 30 game minutes within A–E and G; C p95 ≤ 15.3 ms; the 2× pass C p95 ≤ 50 ms | `perftests.mvp_soak`, `mvp_stress` |
| 5 | MVP completeness: every card entry has an icon; every MVP blueprint passes its GameTests; the naming and `NOTICE` lint; a provenance note per subsystem | T28 |
| 6 | The production jar on a dedicated server (L5) and on a client (CI or L4) | `runServerSmokeJar`, T27 |
| 7 | The MVP regression suite (feasibility §6.3): gather timing (`GatherTimingTest`), builders (`constructiontests`), a batch at its rally point (`BatchTest`), the counters (`DuelTest`, AFTER-M5), 200 units with zero stuck (`maptests`), damage stages and repair (`DamageStagesTest`), the bell (`garrisontests`, AFTER-M5), save mid-construction, and a match resumed at minute 10 equals an uninterrupted run at minute 15 (`persistencetests.reload_at_ten_equals_fifteen_uninterrupted`), conquest and fog pairing (`matchtests`, `fogtests`) | the runs |
| 8 | M0's to M5's gates still pass; `verify`, `verifySlow`, `verifySoak`, `verifyClient`, `verifyPerf` green; CI green | the runs |
| 9 | L7: the owner's matches per level, one against a dedicated server, a win on Easy, no score ≤ 2, the verdict in STATE.md; two friends' matches; no P0 or P1 open | the owner's note |

---

## 9. Risks and fallbacks

| # | Risk | Sign | Fallback |
|---|---|---|---|
| R1 | `move(viewer)` every 5 ticks costs too much with many bodies | T6's measurement | Refresh only viewers whose tracking range holds a flipped unit; if still over, an ADR proposing M8's targeted re-tracking early goes to the owner (ADR-0023 keeps it in M8) |
| R2 | The shroud's quads cost budget F or break under a shader pack | T7, PERF-14 | Coarser pages far from the camera; a fog colour in the sky render under Iris |
| R3 | Fog makes the AI blind: matches stall or run past 45 minutes | T9, T23 | Attack-moves to the enemy start first (§2.7); if that fails, AI-18 goes to `changed` with a proposal in "For the owner" to pull AI-07's last-seen memory into M6 (scope is the owner's) |
| R4 | No opening holds 60–140 against every other | T23 | Retune the openings' timings before the units' numbers; Town's cost as the main lever (TECH-02 allows it) |
| R5 | 1,000 headless matches take too long | T12's measured match | Parallel by default; seeds sharded across `full-ci` runners |
| R6 | Hard cannot beat Normal 124 of 200 without a bonus | T23 | Hard becomes the labelled top level with an open bonus shown in setup (D-19, row 9) |
| R7 | M5's classes differ from its spec | AFTER-M5 checks | §1.6 rows; the M5-dependent tasks re-planned in the spec before they start |
| R8 | `MvpScene`'s placed armies cost unlike a grown match | T24 | Compare per-system costs with `:core:openingBalance`'s telemetry at the same population; a §1.6 row and a scene closer to the match if they differ by more than 20 % |

---

## 10. Owner inputs

| Input | When | Blocks |
|---|---|---|
| «open M6 for claims» | now | M6's start |
| [ADR-0025](../adr/0025-commands-into-unexplored-cells.md): commands into unexplored cells | before T6 | Row 2's move rule |
| The first map's hand-finishing, with a date | after T12 | MAP-02's done |
| The goldens and the looks | T27 | Accepted |
| The MVP baseline on the M4 Pro | T24 | PERF-09.4 |
| The matches per level, the questionnaire, the audio checklist, two friends' playtests | after the demo | Accepted, the MVP gate |
