# Roadmap changes: from the draft to ROADMAP.md

*2026-10-21. The producer's record of how [roadmap-draft.md](roadmap-draft.md) became
[ROADMAP.md](../ROADMAP.md) and the card files in [roadmap/](../roadmap/), applying the change
requests of the three reviews: [game design](roadmap-review-designer.md) (DR-01 to DR-38),
[tech lead](roadmap-review-techlead.md) (TL-01 to TL-73) and [QA](roadmap-review-qa.md) (QAR-01 to
QAR-113). Rules: every "must" applied; "should" and "could" applied unless they conflict with the
architecture, the owner decisions or each other, in which case this file says what was decided and
why. Every Minecraft and NeoForge name added to the cards was checked in the decompiled 1.21.1 +
NeoForge 21.1.262 sources.*

## 1. Outcome in numbers

| | 224 change requests | Applied | Applied with changes or in part | Rejected |
|---|---|---|---|---|
| Designer | 38 (6 must, 21 should, 11 could) | 27 | 11 | 0 |
| Tech lead | 73 (34 must, 35 should, 4 could) | 61 | 11 | 1 (TL-38) |
| QA | 113 (50 must, 57 should, 6 could) | 97 | 16 | 0 |

"Applied with changes" means the request's intent is in the cards, but a detail moved, for one of
three reasons: two requests conflicted, a verified engine fact made the requested test impossible
(for example a frozen GameTest server stalls its own tests), or the M0 implementation spec, written
in parallel, had already placed an item in a later milestone. No "must" was rejected.

| Milestone | Draft cards / card-days | Final | Main differences |
|---|---|---|---|
| M0 | 23 / 58 | 26 / 66 | + CORE-10, CORE-12, QA-17; QA-02, QA-05, QA-08 grow; QA-06 shrinks; skeleton first |
| M1 | 20 / 88 | 26 / 83 | + UNIT-09, CORE-11, BLD-15, MAP-07, PATH-15, PATH-16, QA-19; PATH-08 to reserve; three slices |
| M2 | 11 / 38 | 20 / 66 | + NET-08, QA-18, PERF-14, CAM-07; the battle sandbox (CMB-01, CMB-02, ORD-04, CMB-06, AUD-01) |
| M3 | 23 / 89 | 27 / 81 | + AUD-04, AI-17, ECO-09, PROD-03, UI-10, UI-11, UI-12, MATCH-05; − combat to M2; ECO-04 merged |
| M4 | 18 / 49 | 18 / 51 | + ECO-16, ECO-17, MATCH-04; − ECO-09, PROD-03, UI-10 |
| M5 | 12 / 39 | 14 / 41 | + BLD-16, BLD-11, CMB-16, CMB-17; − CMB-06, AUD-01 |
| M6 | 20 / 76 | 23 / 69 | + BLD-17, MATCH-14, UI-20, UI-21, solo fog (5 cards with D-24 (c)); − ART-03, MATCH-04, MATCH-05, UI-11, UI-12; AUD-02 merged |
| **MVP** | **127 / 437** | **154 / 457** | 448 without D-24 (c) |
| M7 | 11 / 38 | 13 / 51 | + NET-09, ART-03; QA-14 floats |
| M8 | 8 / 27 | 5 / 20 | + PERF-15; − solo fog to M6; FOW-04 merged |
| M9 | 10 / 56 | 10 / 49 | + PERF-16; − BLD-11; BLD-10 slimmer (L → M) |
| M10 | 15 / 71 | 18 / 78 | + UNIT-10, MATCH-16, PERF-17 |
| M11–M13 | 20 / 115 | 20 / 115 | – |
| M14 | 16 / 105 | 15 / 106 | + MATCH-15, PERF-18; − CMB-16; ECO-14 and ECO-15 merged |
| M15 | 8 / 52 | 8 / 52 | – |
| **All** | **215 / 901** | **243 / 928** | plus 2 reserve cards (PATH-08, PATH-17; 13 card-days) |

The MVP grows by 27 cards and 20 card-days: the architecture work the draft lacked, the battle
sandbox, the first-match feedback, garrison and the bell, livestock, high ground, solo fog and the
gates for budgets E and F, offset by narrower spikes, conditional work moved to the reserve, and the
art pass moved after the MVP verdict.

## 2. New cards and identifier conflicts

35 cards are new: the 34 the reviews proposed, and CAM-07, split out of CAM-05. The reviews reused
some numbers for different content. New cards take the next free number in their area; where two requests claimed one number, the card that lands earlier
kept it, or the one the concurrently written M0 spec ([milestones/M0-foundation.md](../milestones/M0-foundation.md))
already uses.

| Card | Content | Requested as | Why this number |
|---|---|---|---|
| CORE-10 | Fault containment (M0) | CORE-10 (TL-14; QAR-27 with quarantine) | – |
| CORE-11 | Binary journal and cross-level replay (M1) | CORE-11 (TL-25) | – |
| CORE-12 | Save quarantine (M0) | CORE-12 (TL-15); part of QAR-27's CORE-10 | two cards' worth of work, as TL-15 says |
| QA-17 | Static gates, inventory and the fixture catalogue (M0) | QA-17 (TL-16); QA-17 for the catalogue (QAR-13) | one M0 card; the M0 spec uses QA-17 for the gates |
| QA-18 | Scenario diff, goldens and the CI golden job (M2) | QA-18 (TL-18) | the M0 spec already refers to QA-18 for it |
| QA-19 | Network test kit (M1) | QA-18 (QAR-09) | QA-18 taken |
| UNIT-09 | Spike S10: kinematic fidelity (M1) | UNIT-09 (DR-06, TL-24, QAR-50) | – |
| UNIT-10 | Abilities (M10) | UNIT-10 (DR-21) | – |
| BLD-15 | Block writer (M1) | BLD-15 (TL-32) | lands first |
| BLD-16 | Garrison and the town bell (M5) | BLD-15 (DR-10) | BLD-15 taken |
| BLD-17 | Defence tower (M6) | BLD-16 (DR-30) | shifted with the garrison |
| MAP-07 | Arena world (M1) | MAP-07 (TL-31) | – |
| PATH-15, PATH-16 | Live capture; worker pool and S7 (M1) | TL-27, TL-28 | – |
| PATH-17 | Flow fields, reserve (M1) | "flow fields as reserve cards" (TL-36) | new reserve card |
| NET-08 | Tier-2 replication (M2) | NET-08 (TL-44); NET-09 (QAR-70) | milestone order |
| NET-09 | Commander hardening (M7) | NET-09 (TL-63); NET-08 (QAR-97) | milestone order |
| PERF-14 to PERF-18 | Client frame gate (M2); gates for M8, M9, M10, M14 | QAR-10, QAR-106 | – |
| CAM-07 | First-person orders (M2) | inside CAM-05 (DR-35) | split out so the owner can approve it alone |
| ECO-16, ECO-17 | Livestock (M4); auto-reseed (M4) | DR-11, DR-02 | – |
| CMB-17 | Attack lines and the under-attack flash (M5) | DR-01 | – |
| AUD-04 | World-edit feedback (M3) | DR-14 | – |
| UI-20, UI-21 | Full minimap (M6); notification feed (M6) | DR-08, DR-03 | UI-21 also absorbs AUD-02 |
| FOW-07 | Explored state, shroud and solo fog gating (M6, D-24 (c)) | DR-17 | absorbs FOW-04 |
| AI-17 | Scripted bot (M3) | AI-17 (TL-53) | lands first |
| AI-18 | The AI reads only its own vision (M6, D-24 (c)) | AI-17 (DR-17) | AI-17 taken |
| MATCH-14 | Spike: an arena world from the title screen (M6) | MATCH-14 (TL-60) | lands first |
| MATCH-15 | Sacred sites as beacons (M14) | MATCH-14 (DR-33) | MATCH-14 taken |
| MATCH-16 | First Minecraft twist: day and night (M10) | "a new card in M10" (DR-29) | – |
| D-22, D-23 | L4 consent policy; CI and the private repository | D-22, D-23 (TL-72, QAR-22, QAR-23) | two reviewers agree; needed by M0 |
| D-24, D-25 | Exploration in the MVP; the MVP's identity | D-22, D-23 (DR-17, DR-18) | D-22 and D-23 taken |

## 3. Removed identifiers (merged, never reused)

| Removed | Merged into | Why |
|---|---|---|
| ECO-04 Berry bushes (S) | ECO-05 Gathering, dropsites and berry bushes | A forage node type is a slice of gathering, too small for an owner decision of its own |
| AUD-02 Alert and feedback audio (S) | UI-21 Notification feed and alert audio (M) | One feature to the player: the alert and its sound |
| FOW-04 Fog rendering (M) | FOW-07 Explored state, the shroud and solo fog gating | The shroud is the fog rendering; with D-24 (c) it lands in M6 |
| ECO-14 Barter (S) | ECO-13 Trade routes and barter (L) | Both live in the market building |
| ECO-15 Fishing (S) | UNIT-08 Ships, docks and fishing (L) | Fishing boats and the dock are ship content |

No other card was cut. Titles were adjusted to the new scope; IDs are unchanged.

## 4. Cards that moved

| Card | From | To | Request |
|---|---|---|---|
| CMB-01, CMB-02, ORD-04 | M3 | M2 | DR-01 |
| CMB-06 (health bars, flash, death) | M5 | M2; the rest is CMB-17 in M5 | DR-01 |
| AUD-01 | M5 | M2; bow-shot sounds join CMB-04 | DR-01 |
| ECO-09 (manual reseed) | M4 | M3; auto-reseed is ECO-17 in M4 | DR-02 |
| UI-10, PROD-03 | M4 | M3 | DR-03 |
| UI-12 (under-attack alert) | M6 | M3; the feed is UI-21 in M6 | DR-03 |
| UI-11 (basic minimap) | M6 | M3; the rest is UI-20 in M6 | DR-08 (TL-58 wanted M4) |
| MATCH-05 | M6 | M3 | TL-58 |
| MATCH-04 | M6 | M4 | TL-58 |
| BLD-11 | M9 | M5 | DR-10 |
| CMB-16 | M14 | M5 | DR-13 |
| FOW-01, MATCH-07, UI-16 | M8 | M6, with D-24 (c) | DR-17 |
| ART-03 | M6 | beside M7, after the MVP verdict | DR-22 |
| QA-14 | after QA-13 in M7 | floating: 2027-Q2 or the MVP gate | TL-66, QAR-102 |
| PATH-08 | M1 | reserve | TL-36 |

## 5. Conflicts between requests, and the decisions

| Conflict | Decision | Why |
|---|---|---|
| DR-07 starts the M2 production cards right after M0; TL-39 runs only the S9 spike early and keeps the camera cards for M2 | DR-07: a client track. The cards stay M2 cards but start as soon as their dependencies allow; "one system in progress" counts per track | The client cards touch no M1 code and use the owner's otherwise idle hours (F-R8, critical impact); the assistant's M1 work needs almost none of them |
| DR-22 starts ART-03 after the MVP verdict; TL-61 starts it in M4 | DR-22, with TL-61's completeness check (icons, blueprints, damage stages) in QA-13 | M6 tuning changes footprints and silhouettes, so art locked earlier is redone; both agree ART-03 leaves M6's critical path |
| DR-08 puts the basic minimap in M3; TL-58 moves the whole minimap to M4 | DR-08 | The M3 verdict match needs orientation; DR-08 gives TL-58's M4 and M5 playtests a minimap too |
| TL-37 gates M1 at 600 units; QAR-07's matrix row says 300 | TL-37 for M1; QAR-08's per-unit normalisation from M3 on | Movement is the costliest system and M1 is headless; 600 is the design scale, so M1 can measure it at no owner cost |
| DR-09 keeps the M3 bot to one announced attack; QAR-75 wants repeated waves or a tie-break | Repeated waves, each announced at least 30 s ahead (AI-17) | The M3 exit keeps "every match ends with a winner within 30 minutes" (DR-19); AI-04 still adds openings, sized waves, defence and difficulty |
| TL-38 moves DATA-02 to M3 | Rejected: DATA-02 stays in M1's content track | Architecture §1.5 builds the pipeline in M1; it is off M1's critical path; moving it adds 3 card-days to M3, whose verdict gates M4; CMB-16, UI-08 and TECH-01 need it by M4–M6 |
| DR-05 sizes PATH-01 (S8) as S; TL-26 keeps the full S8 scope | S, at the start of slice 1A | The page-build exit number comes from PATH-02's bench and the long-crossing A* expansions from PATH-03's crossing benchmark, so the census itself is a day's work |
| DR-06 and TL-24 put S10's "units avoid players" in UNIT-09 (slice 1A); avoidance (PATH-06) arrives in slice 1B | The avoidance check is PATH-06's AC 3; UNIT-09's report waits for it | Dependency order inside M1 |
| TL-10 tests "a chunk unload and reload leaves exactly one body" in L3; QAR-33 shows GameTest chunks are force-loaded | `noSave` checked in L2 (`getEncodeId` is null), removal reasons in L3, a real unload in QA-05's L5 restart round trip | Verified constraint: the runner force-loads the structure's chunks |
| TL-12 and QAR-31 test `/tick freeze` and `/tick step` in L3 (QAR-31: a solo batch) | L2 against `TickRateManager`, plus the L7 demo | `GameTestTicker` runs only while `runsNormally()` (MinecraftServer.java:1069–1070), so a frozen GameTest server stalls its own tests |
| TL-09 and QAR-33 make `/kill` lethal core damage in M0 | In CMB-01 (M2), which brings health; in M0 `/kill` leaves units intact | M0 has no health; the M0 spec decided the same |
| TL-10 and QAR-34 test same-tick pose shipping and packet discipline in UNIT-01 (M0) | NET-01 (M1), with QA-19's packet capture | Mock-player packet capture arrives in M1; the M0 spec decided the same |
| TL-17 and QAR-40 put every fake in QA-02 | QA-02 builds the harness and the fakes for M0's ports; each later port brings its fake (FakeWorld and VoxelFixtures with PATH-02 and BLD-15, FakeNet with NET-01, the inline JobExecutor with PATH-03) | A fake for a port that does not exist yet is speculation; the M0 spec decided the same |
| TL-21 asserts A, B, C, D and G from PERF-01 in M0 | M0 asserts A and G; B's A/B runner and C's between-tick timers come with PERF-05, D with PATH-16, E with QA-19 | Each budget is asserted from the first gate that has something to measure (M0 has no payload handlers, path service or network) |
| QAR-44 adds dedicated-server joins and `RtsScreen` driving to QA-06 (M0) | The two-client join comes with CAM-01, which needs it first; `RtsScreen` driving with QA-18 (M2); QA-06 stays lean | `RtsScreen` does not exist in M0; the M0 spec keeps QA-06 lean |
| DR-13 and DR-20 gate on 16 of 20 L3 duels and 30–70 % over 20 seeds | L1 on 200 fixed seeds: counters ≥ 170, openings 60–140 | QAR-11: 20-seed gates fail by chance (26 % for a fair map, 84 % for six pairings) |
| DR-02's food-wait check in L3 | L1 over QA-11's ten seeds, with QA-11's L3 sample | TL-05 and QAR-76: ten 30-minute matches don't fit L3's time budget |
| DR-23's calendar places D-13 at M3, D-18 at M6, D-03 at M4 | D-13 by M1 (TL-71), D-18 by M3 (MATCH-05 moved, TL-58), D-03 by M7 (ART-03 after the MVP) | Each decision is needed by its first card |

## 6. Follow-ups outside this task's files

These documents disagree with the final roadmap or with the accepted requests, and were not edited
here (this task writes only the roadmap files). Their owners should correct them. Each item ends
with its status on 2026-10-21, the day the owner approved the roadmap.

1. **04-architecture.md §17.3 and the L6 row of §16.1**, and **05-testing.md** ("Performance tests
   run at twice the milestone's target scale"): the 2× stress pass asserts only C p95 ≤ 50 ms;
   budgets are asserted at the target scale (QAR-111, ADR-0019).
   **Status (2026-10-21):** done in 04-architecture.md §16.1 (the L6 row), §17.1 and §17.3, whose
   revision note of that date covers follow-ups 1–4, and in 05-testing.md's performance budgets;
   the revised ADR-0019 says the same.
2. **04-architecture.md §3.4**: map-scale GameTests place and release their own tickets, with a
   batch-end check (QAR-112).
   **Status (2026-10-21):** done in 04-architecture.md §3.4; ADR-0023 amends ADR-0010's ticket
   rule to match.
3. **04-architecture.md §12.5**: headless bandwidth is counted with QA-19's encoder count; JFR
   `PacketSent` applies only to real connections (QAR-113).
   **Status (2026-10-21):** done in 04-architecture.md §12.5 and in budget E's row of §17.2.
4. **04-architecture.md §1.5, §12.2 ("Lands in") and §17.1**: schedule changes. `fx`, `unit_hud`,
   `ui_query` and `unit_detail` land in M2; pause in M3; save and resume with the production journal
   ring in M4 (was M6); the basic minimap in M3; garrison, the first bodiless units, in M5; solo fog
   and `vision_delta` in M6 with D-24 (c); S9 right after M0; M1's target scale 600 units.
   **Status (2026-10-21):** done in 04-architecture.md §1.5, §12.2, §17.1 and §19. ADR-0023
   aligns the milestone tags of the accepted ADR-0009 (the journal ring in M4), ADR-0014 (the
   UnitAI states) and ADR-0016 (solo fog in M6), and states that the roadmap governs the schedule.
5. **ADR-0019**: budget D's wording "a 100-unit order moving within 5 ticks" becomes path-result
   latency (TL-28); the milestone targets follow this roadmap. PERF-01's one revision carries both.
   **Status (2026-10-21):** done: ADR-0019 is revised and still Proposed, with a dated note under
   its status (path-result latency for D, the budget matrix as the milestone targets, the
   target-scale and 2× rules). PERF-01 now confirms the revised table with the M0 numbers and moves
   it to Accepted.
6. **adr/README.md**: its "Pending measurements" table lacks S12 (DATA-03), which confirms
   ADR-0012.
   **Status (2026-10-21):** done: the table lists S12 (DATA-03), which confirms ADR-0012's targets.
7. **ADR-0021's leaf rule** is superseded by a short ADR written in ECO-03 (DR-15); **ADR-0006's
   Definition-of-Done rows** by QA-09's ADR (QAR-05); **ADR-0011 and the engine-surface inventory**
   gain the fauna archetype in ECO-16 (DR-11). Accepted ADRs are superseded, never rewritten.
   **Status (2026-10-21):** done for ADR-0006: ADR-0022, accepted with the roadmap, amends its two
   Definition-of-Done rows (and its L5 column and the inventory check's level), and QA-09 confirms
   it in first use. ADR-0023 also aligns the accepted ADR-0009, ADR-0010, ADR-0014 and ADR-0016
   with the approved roadmap and records where D-08 touches ADR-0011, ADR-0015 and ADR-0016. Open
   by design: ADR-0021's leaf rule and ADR-0011's fauna archetype wait for the ADRs of ECO-03 (M3)
   and ECO-16 (M4), which adr/README.md lists as planned.
8. **01-feasibility.md** §4 and §6.5 still name the pre-architecture spikes (S2 at 24², 32² and 64²
   chunks; a "nav spike" and a "camera spike") and the draft's milestone order; it is re-estimated
   after M0, as it says itself.
   **Status (2026-10-21):** done for §4 and §6.5: the spike table uses S1–S12 with their cards,
   and §6.5 follows the final milestone order and totals. Open until the re-estimate after M0:
   §3, §5 and §8 keep some pre-architecture spike names, which §4 and §5.6 map to S1–S12, and
   §8's first decision gate still has the camera spike at the start of M2 (S9 runs right after
   M0).
9. **milestones/M0-foundation.md** (written in parallel) uses the same identifiers. Against the
   final M0 cards it should check: QA-05 grows to M (headless match and restart round trip), QA-08
   is M, QA-17 also holds the fixture catalogue, QA-09 writes the Definition-of-Done ADR, and
   CORE-01's world switch is an L4 consent scenario as well as demo step 7.
   **Status (2026-10-21):** done: the spec was reconciled with the final M0 cards (its §1.6 C), and
   the cards adopted its §1.6 rows 26–28, 31, 32 and 34–37 the same day. Open: the spec cites
   CORE-01.4 for the smoke's holder checks and rule B8, which are now CORE-01 AC6 and AC7.
10. **STATE.md**: the roadmap now waits for the owner's approval and for decisions D-01, D-22 and
    D-23 before M0 closes.
    **Status (2026-10-21):** open: the owner approved every M0–M6 card and decided D-01, D-22 and
    D-23 that day, but STATE.md, written before the approval, still lists the roadmap and its
    decisions as waiting for the owner. It should record the approval, the decisions still open
    (D-03, D-06, D-07, D-20, D-21) and that M0 waits for the owner's go-ahead.

## 7. Every change request

Outcome: **applied**; **changed** (applied with a change, the reason given); **partly** (part
applied, the rest explained); **rejected**.

### 7.1 Game design (DR)

| ID | Sev | Outcome | Where, and why |
|---|---|---|---|
| DR-01 | must | changed | Battle sandbox in M2: CMB-01, CMB-02 (with the default aggressive behaviour), ORD-04 (torch criterion moved to CMB-03), CMB-06 (health bars, flash, death; S), AUD-01; CMB-17 (S) in M5; the M2 demo; the 2 × 100 melee soak (CMB-02 AC6) and "commanding a fight" ≥ 3 of 5 (M2 Acceptance). Change: the `fx` and `unit_hud` payloads now land in M2 (architecture follow-up 4) |
| DR-02 | must | changed | ECO-09 in M3 with a finite amount, 5 slots, 0.9 returns, manual reseed and the civic centre as dropsite; ECO-17 (S, M4). Change: the food-wait exit check runs in L1 over QA-11's ten seeds with the L3 sample, not in L3 alone (TL-05) |
| DR-03 | must | applied | UI-10 and PROD-03 in M3; UI-12 split into the M3 alert and UI-21 in M6 (which also absorbs AUD-02) |
| DR-04 | must | changed | D-08 reframed with the observer's three rules; CAM-05 AC4 (a column held under 1 s) and AC5. Change: "adds no visible or explored cell" is an L1 agent-record test in M2 and a vision test in FOW-01 (no vision grid exists in M2) |
| DR-05 | must | applied | UNIT-01, PERF-02, CORE-06, DATA-03 (S12), PATH-01 (S8, size S), PATH-03 with PATH-16 (S7, `ADAPTIVE`, work-counted admission), CORE-05 (S6), CAM-01 (S9), CAM-04 and UI-01 (`RtsScreen`), CAM-02 (8–64, default 24), UI-03 (no Ctrl-click), ORD-01 (L1 replay), NET-04 (S11); the engineering-decisions table in M0's baseline; D-14's stale remark gone |
| DR-06 | must | changed | UNIT-09 in M1 slice 1A. Change: "units avoid players" is PATH-06's AC3, because avoidance arrives in slice 1B; UNIT-09's report waits for it |
| DR-07 | should | applied | The two tracks (ROADMAP.md "Order and the two tracks", M2's start list); risk F-R7 counted per track; the "owner plays at the end" column, and F-R8 points at it. Supersedes TL-39's note that the camera cards wait for M2 (section 5) |
| DR-08 | should | applied | UI-11 basic (M, M3), UI-20 (M, M6); D-16 needed by M3 |
| DR-09 | should | applied | AI-17 announces every wave at least 30 s ahead with a notice, a countdown and a horn from a visible muster point (AC2 L1, AC3 L3); with QAR-75's repeated waves |
| DR-10 | should | changed | As BLD-16 (BLD-15 is the block writer) in M5, with BLD-11 moved to M5; AI-04 rings the bell; healing, ejection, capture regeneration and rally-to-garrison stay in BLD-10 (M9). Change: "no entity tick while garrisoned" is the L3 `BodyMode.NONE` check (QAR-108) instead of L6 |
| DR-11 | should | applied | ECO-16 (M4) with the fauna archetype and its ADR amendment; ECO-10 becomes "Wild herds and hunting" (M10); ECO-12 depends on ECO-16. Verified: `PigModel`, `CowModel` and `ChickenModel` are generic over `Entity`; `SheepModel` is bound to `Sheep` and has `createBodyLayer()` |
| DR-12 | should | applied | BLD-08 (stone and iron each pay for two M4 items), UNIT-07 (iron in soldier costs), TECH-02 (Town costs stone or iron), DATA-04 AC5 (the L1 sink check) |
| DR-13 | should | changed | CMB-16 in M5 (S, T2 pulled in) as a modifier source with two knobs, the bonus in the breakdown. Change: the duel gate is at least 170 of 200 seeds in L1 (QAR-11) instead of 16 of 20 in L3 |
| DR-14 | should | applied | AUD-04 (M3). Verified: `ClientLevel#destroyBlockProgress`, `ClientLevel#addDestroyBlockEffect`, `SoundType#getBreakSound` and `getPlaceSound` |
| DR-15 | should | applied | ECO-03's leaf rule and AC5; the short ADR that supersedes ADR-0021's leaf rule is part of ECO-03. Verified: `LeavesBlock.DECAY_DISTANCE` = 7 |
| DR-16 | should | applied | TECH-03 (S → M) with one gathering technology per resource line, carry capacity and two equipment tiers; TECH-05 carries the full lines; the tier's transport is chosen and recorded in TECH-03. Verified: `ArmorMaterials` `LEATHER`, `CHAIN`, `IRON`, `DIAMOND` |
| DR-17 | should | changed | As D-24 (D-22 is the consent policy) and AI-18 (AI-17 is the bot): FOW-01, FOW-07, AI-18 and MATCH-07 in M6, conditional on D-24 (c); a horseman scout in MAP-02's starts; M8 keeps team vision, re-tracking, mirages, the audit and scouting. Change: UI-16 (minimap fog) moves too, so the minimap never shows what the fog hides, and FOW-04 is merged into FOW-07 |
| DR-18 | should | applied | The five pillars in ROADMAP.md, cited on Value lines; D-25 (renumbered from D-23) with options (a)–(d) and card deltas |
| DR-19 | should | applied | M3 within 30 minutes; M6 median 20–35 and within 45 (M6 Done gate, QA-12); DATA-04's timing targets; telemetry in QA-11 and QA-12; QA-11, QA-12 and QA-16 in L1 with L3 samples |
| DR-20 | should | changed | QA-12 AC2 checks that each opening wins against each other opening; TECH-02 is tuned until it holds. Change: 60–140 of 200 seeds in L1 (QAR-11's statistics) instead of 30–70 % over 20 seeds |
| DR-21 | should | applied | UNIT-10 (M10); CMB-12, CMB-14 and CIV-02 build on it |
| DR-22 | should | applied | ART-03 after the MVP verdict, beside M7; M6's completeness check in QA-13 (with TL-61) |
| DR-23 | should | changed | Card delta per option, the four bundles and the calendar in ROADMAP.md. Change: D-13 by M1, D-18 by M3 and D-03 by M7, as their first cards need them (section 5) |
| DR-24 | should | applied | D-03's art-cost column and the restated biome option. Verified: five village styles and seven villager biome types in 1.21.1 |
| DR-25 | should | applied | QA-09's scored questionnaire; the M2, M3, M5 and M6 Acceptance compare scores and fail on any score of 2 or less; two friend playtesters at the MVP gate |
| DR-26 | should | applied | DATA-04 AC3 |
| DR-27 | should | changed | ECO-05 (tools and the carried load as held items), UI-07 (workers per resource, idle count), ART-01's style guide. Change: vanilla 1.21.1 has no hammer item (verified), so the style guide picks the building tool |
| DR-28 | could | applied | AI-04: the raid bar and horn (verified `BossEvent.BossBarOverlay.NOTCHED_10`, `SoundEvents.RAID_HORN`) and campfire camps |
| DR-29 | could | applied | D-13 reframed with costed twists and the world-time constraint; MATCH-16 (M10). Verified: `MinecraftServer#synchronizeTime` |
| DR-30 | could | applied | As BLD-17 (renumbered), a Town unlock in M6 that costs stone; garrison and upgrades in BLD-12 |
| DR-31 | could | applied | CMB-03's material classes and AC2; D-12's two new options |
| DR-32 | could | applied | D-04's villager-look option (verified `VillagerModel` generic over `Entity`; `VillagerProfessionLayer` bound to `LivingEntity & VillagerDataHolder`); ART-01's per-class ranges |
| DR-33 | could | applied | As MATCH-15 (renumbered) in M14. Verified: `StainedGlassBlock` implements `BeaconBeamBlock` |
| DR-34 | could | applied | MATCH-06: two humans in one faction |
| DR-35 | could | changed | New CAM-07 (M, M2) for "follow me", "attack what I look at" and inspection. Change: ringing the bell in first person is in BLD-16, because the bell arrives in M5 (verified `PlayerInteractEvent.RightClickBlock`) |
| DR-36 | could | applied | D-19, AI-05, AI-15 |
| DR-37 | could | applied | QA-11's observe command and AC5 |
| DR-38 | could | applied | MAP-02's terrain jobs and AC2 |

### 7.2 Tech lead (TL)

| ID | Sev | Outcome | Where, and why |
|---|---|---|---|
| TL-01 | must | applied | The engineering-decisions table in M0's baseline (every row an ADR and its confirming spike); "confirms ADR-NNNN or triggers its fallback" in QA-01, PERF-02, PERF-03, CORE-05, DATA-02, DATA-03, PATH-01 and CAM-01 |
| TL-02 | must | applied | Budget table in M0's baseline with ADR-0019's definitions of A, B, C and G |
| TL-03 | should | applied | One merged risk register in ROADMAP.md with F- and A- prefixes, including the eight architecture risks that had no card |
| TL-04 | should | applied | Tests fields follow ADR-0006 by change type; L0 and L5 run in `verify`; deterministic checks are exact L1 tests (ECO-03, ECO-05, ECO-08, BLD-03, BLD-06, PROD-02, CMB-01, CMB-04 with golden results and a binomial bound, CMB-07, TECH-03) with one L3 integration test per feature |
| TL-05 | should | applied | AI-versus-AI and per-technology checks in L1 (QA-11, QA-12, QA-16, AI-01 to AI-05, AI-07, TECH-05); small L3 samples in `verifySlow` and `verifySoak` |
| TL-06 | should | applied | Client cost criteria as L1 JMH proxies plus PERF-14 with consent (UI-02, CMB-06, UI-20, FOW-07, TERR-03) |
| TL-07 | should | applied | DATA-04 (own numbers in game units, provenance notes, overrides), D-11 reworded without "0.4 of 0 A.D." |
| TL-08 | must | applied | M0's waves A and B, the tripwire, the skeleton path of 16 card-days, "skeleton first, then measure on it" |
| TL-09 | must | changed | CORE-06 implements ADR-0008 without the PERF-02 dependency; PERF-02 runs S1 on UNIT-01's body with §19.2's scope and exit. Change: `/kill` becomes lethal core damage in CMB-01, because M0 has no health (section 5) |
| TL-10 | must | changed | UNIT-01 rewritten to ADR-0011, the `Mob` APIs and `STEP_HEIGHT` gone, steps moved to PATH-05 and UNIT-09. Change: the L3 chunk-reload test became the L2 `getEncodeId` check and QA-05's L5 restart round trip; the same-tick pose test moved to NET-01 (section 5) |
| TL-11 | must | changed | CORE-01's lifecycle, `MatchSpace`, `MatchRegionIndex`, `StrategosServer`, `strategos_registry`, `/strategos match create`. Change: the holder test is L2 (QAR-30), and the world switch an L4 consent scenario plus demo step 7 |
| TL-12 | must | changed | CORE-02's driving port, nine phases (five implemented), stepping rules, handler and applier tables. Change: freeze and step are checked in L2 and L7, because a frozen GameTest server stalls its own tests |
| TL-13 | must | applied | The in-memory journal in CORE-03; `Rng`, `DetMath`, the full B4, `ReplayRunner` and S6 in CORE-05 (Linux once D-23 allows); ORD-01 AC4 and the M0 Done gate replay the skeleton; bounded decoding and token buckets in NET-01 |
| TL-14 | must | applied | CORE-10 |
| TL-15 | must | applied | CORE-07 (one blob, codecs in core, `"v"` on `Dynamic`, no dirty tracking) and CORE-12 |
| TL-16 | must | applied | QA-01 (S5 confirms ADR-0007 or switches to its fallback) and QA-17 (B2, B3, B5–B11 with self-tests; the jar check; the inventory test, empty at first). QA-17 also takes QAR-13's catalogue |
| TL-17 | must | changed | QA-02 is the core harness, size M. Change: the fakes arrive with their ports (section 5) |
| TL-18 | should | applied | QA-06 lean (S3 and a minimal runner); QA-18 (scenario diff, goldens, the container route) at M2; QA-08's golden step in QA-18 |
| TL-19 | should | applied | QA-10 keeps the command tree and log tagging; the dev payloads and their test are PATH-10's (M1) |
| TL-20 | should | applied | PERF-03 at 256, 576 and 1,024 chunks with §19.2's exit; import timing in MAP-01 |
| TL-21 | should | changed | PERF-01 enforces and revises ADR-0019 once, with the owner's approval. Change: each budget is asserted from its first gate (section 5) |
| TL-22 | should | applied | PERF-04 depends on UNIT-01 and runs with CAM-01 if consent comes late |
| TL-23 | must | applied | M1's slices 1A, 1B and 1C and the content track, each slice with a demo and checks; QA-19 joins 1B |
| TL-24 | must | changed | UNIT-09, as DR-06; the avoidance check is PATH-06's |
| TL-25 | must | applied | CORE-11 |
| TL-26 | should | applied | PATH-01 confirms the layered grid with S8's exit; with DR-05's size S, the page-build number comes from PATH-02 and the crossings from PATH-03 |
| TL-27 | must | applied | PATH-02 (pure) and PATH-15 (live) |
| TL-28 | must | applied | PATH-03 and PATH-16, the 0/1/4-thread test; budget D as path-result latency, flagged for ADR-0019's revision (follow-up 5) |
| TL-29 | must | applied | PATH-05 |
| TL-30 | must | applied | PATH-07, the M1 Done gate and PERF-05 use the ladder; no teleport |
| TL-31 | must | applied | MAP-07 and MAP-01 (8,192 blocks per tick, lighting wait, export, ≤ 30 s, the generated proving ground) |
| TL-32 | must | applied | BLD-15 in M1; BLD-03 L → M |
| TL-33 | should | applied | NET-01 |
| TL-34 | should | applied | DATA-03 (M → L) |
| TL-35 | should | applied | DATA-02 |
| TL-36 | should | applied | PATH-08 and the new PATH-17 in reserve, outside M1's totals; PATH-09 the shared group path (L → M) |
| TL-37 | should | applied | PERF-05 at 600 with a 1,200 stress pass; M1 Done gate items 3–5 |
| TL-38 | could | rejected | DATA-02 stays in M1 (section 5) |
| TL-39 | should | applied | CAM-01 right after M0, beside slice 1A, with the owner's clients; its "production cards wait for M2" is superseded by DR-07's client track |
| TL-40 | must | applied | CAM-01's three body strategies, the dropped variants, LAN and latency, Sodium and Iris, §19.2's exit (size L per QAR-66) |
| TL-41 | must | applied | M2 Done gate 3 and Acceptance 1; CAM-01 AC1 and AC2; CAM-03 AC2 and AC3 |
| TL-42 | must | applied | CAM-04 |
| TL-43 | must | applied | UI-03 (double click only) and UI-01 AC2 |
| TL-44 | must | applied | NET-08 |
| TL-45 | should | applied | CAM-02 and CAM-03 (`spectatorsGenerateChunks` in MAP-07) |
| TL-46 | should | applied | UNIT-03; type settings in UNIT-01 |
| TL-47 | should | applied | UI-05 AC1 and AC2 |
| TL-48 | should | changed | M3's four playable slices. Change: melee and attack-move moved to M2 (DR-01), so slice 3.3 holds attacking buildings, victory, the minimap, the alert and pause, and BLD-01 joins slice 3.1 |
| TL-49 | must | applied | ECO-03, ECO-07, BLD-01, BLD-03, BLD-05 |
| TL-50 | must | applied | CMB-01; the invulnerability handling and damage types in CMB-08 |
| TL-51 | should | applied | ECO-02 |
| TL-52 | must | applied | MATCH-01 (`MatchRules`, `RuleChange`) and MAP-07 (`ArenaWorld`) |
| TL-53 | should | applied | AI-01 (M, depends only on CORE-03) and AI-17 (M) |
| TL-54 | should | applied | BLD-01 (blueprints generated in code) and ART-01 |
| TL-55 | must | applied | UNIT-05 |
| TL-56 | must | applied | BLD-04 |
| TL-57 | should | applied | BLD-05 and BLD-07 |
| TL-58 | should | partly | MATCH-04 in M4 (with `WorldReconciler`, `PAUSED` resume, the journal ring) and MATCH-05 in M3; the minimap lands in M3 through DR-08 instead of M4 |
| TL-59 | should | applied | MATCH-05 |
| TL-60 | should | applied | MATCH-02 and MATCH-14 |
| TL-61 | should | partly | ART-03 leaves M6's critical path and QA-13 checks completeness; ART-03 starts after the MVP verdict (DR-22), not in M4 (section 5) |
| TL-62 | should | applied | CMB-08 and AUD-01 |
| TL-63 | must | applied | NET-09 |
| TL-64 | must | applied | NET-03 |
| TL-65 | should | applied | NET-04 |
| TL-66 | should | applied | QA-14 floats: 2027-Q2 or the MVP gate, as a time-boxed branch spike |
| TL-67 | must | applied | FOW-01 (every tick, hysteresis), FOW-07 and FOW-02 (6 and 15 ticks, the access transformer and its fallback, bodies only); no CAM-01 dependency |
| TL-68 | could | applied | MATCH-13 |
| TL-69 | could | applied | TERR-01 |
| TL-70 | could | applied | QA-11 AC4 (and QA-05's per-milestone extensions) |
| TL-71 | should | applied | D-09, D-13 (needed by M1), D-14 |
| TL-72 | should | applied | D-22 and D-23 |
| TL-73 | should | changed | The size changes are applied and the totals recomputed. Change: the MVP comes to 154 cards and 457 card-days instead of the estimated 139 and 431, because the other two reviews added cards (section 1) |

### 7.3 QA (QAR)

| ID | Sev | Outcome | Where, and why |
|---|---|---|---|
| QAR-01 | must | applied | As TL-01 |
| QAR-02 | must | applied | One verdict level per criterion (L0–L7, R, D), consent on the criterion; the 21 level-less criteria now carry R or D, and criteria with two levels were split |
| QAR-03 | must | applied | The terms in ROADMAP.md's rules |
| QAR-04 | must | applied | Every milestone file splits its exit into a Done gate and an Acceptance |
| QAR-05 | must | applied | QA-09 writes the superseding ADR; ROADMAP.md states the Definition of Done followed meanwhile |
| QAR-06 | must | applied | M0's budget table and its gate rules |
| QAR-07 | must | changed | The budget matrix in M0's baseline, kept by PERF-01; D from M1, E once QA-19 exists, G whenever an autosave falls in the window. Change: M1's row is 600 units (TL-37) |
| QAR-08 | should | changed | Per-unit normalisation below 600 units in every gate from M3 on (M1 gates at 600, TL-37) |
| QAR-09 | must | changed | As QA-19 in M1 (QA-18 is the scenario-diff card); every E gate depends on it. Verified: `GameProtocols.CLIENTBOUND_TEMPLATE`, `RegistryFriendlyByteBuf.decorator`, `ServerboundChunkBatchReceivedPacket` |
| QAR-10 | must | applied | PERF-14; L1 JMH proxies; PERF-09 and PERF-12 assert A–E and G headless and F through PERF-14; CI cannot measure frame time |
| QAR-11 | must | applied | Win-rate and counter gates in L1 on 200 fixed seeds with the stated bounds (MAP-02, MAP-04, AI-05, AI-07, AI-15, UNIT-07, CIV-02, CIV-03, CIV-04, QA-16, the M5, M10, M11 and M14 exits); 20-seed L3 runs are smoke tests |
| QAR-12 | must | applied | The cumulative rule in ROADMAP.md and in every Done gate |
| QAR-13 | must | changed | The catalogue is part of QA-17 (one M0 card, see section 2); a Fixtures field on cards that add test data |
| QAR-14 | must | applied | QA-04's README rule and ROADMAP.md's rule; MATCH-01 AC4, CMB-03 AC4, ECO-05 AC7, ECO-09 AC3, BLD-07 AC1. Verified: `GameTestServer` turns off mob spawning, weather, random ticks and fire tick |
| QAR-15 | must | applied | QA-04 (own tickets, batch-end check); MAP-01 AC4 and PERF-03 AC2 on a self-terminating server |
| QAR-16 | must | applied | QA-04 (solo batches, the generator's rejection); DATA-03 AC3–4 solo; CORE-02's freeze test left L3 entirely (TL-12) |
| QAR-17 | must | applied | Positive controls in NET-01, CMB-06, AUD-01, AUD-04, NET-08, UI-16, FOW-07, FOW-02, FOW-05, NET-03, MATCH-06, NET-09, UI-15, NET-07, FOW-06; a rule in ROADMAP.md |
| QAR-18 | should | applied | QA-04 AC4 |
| QAR-19 | should | applied | QA-07 |
| QAR-20 | should | applied | The aggregates table with wall-time budgets in M0's baseline; QA-01, QA-04 |
| QAR-21 | should | applied | `verifyHarness` in QA-01, QA-07 and QA-17 and the M0 Done gate |
| QAR-22 | should | applied | ROADMAP.md's consent rule (batches, D-22, pending consent blocks only Accepted, a consent batch per milestone spec) |
| QAR-23 | should | applied | D-23 with what depends on it |
| QAR-24 | should | applied | The generated proving ground (MAP-01) and placeholder blueprints (BLD-01, ART-01); owner tasks named in M1, M3 and M6 and the art cards; friend playtesters in UI-14, UI-18 and the M6 and M12 Acceptance; UI-18's L3 run as the Done proxy |
| QAR-25 | could | applied | F- and A- prefixes in the risk register and the Retires lines |
| QAR-26 | must | applied | As TL-08 |
| QAR-27 | must | changed | As two cards, CORE-10 and CORE-12 (TL-14, TL-15), with QA's criteria; `FogGate` failing closed is NET-01 AC7, where `FogGate` arrives |
| QAR-28 | must | applied | QA-01 and QA-17 |
| QAR-29 | must | applied | CORE-05 (S6, B4 with self-tests, `ReplayRunner`; AC3 dropped); the journal itself in CORE-03 |
| QAR-30 | must | applied | CORE-01 (ADR-0010 lifecycle, 50 matches in one batch, the L2 holder test, the L4 world switch) |
| QAR-31 | must | changed | CORE-02's nine phases, the slow-clock test and the handler tables. Change: freeze and step in L2 and L7, not in any GameTest batch (section 5) |
| QAR-32 | must | changed | ADR-0014's caps, split by stage: ≤ 300 handles and ≤ 256 commands per tick in CORE-03; batch, rate and handle-rate caps at the gateway in NET-01 (TL-13); one feedback record and a uniform `UNKNOWN_TARGET` in CORE-03 |
| QAR-33 | must | changed | CORE-06 for `noSave` bodies; QA-05's restart round trip. Change: `/kill` is lethal from CMB-01 on, because M0 has no health |
| QAR-34 | must | changed | UNIT-01's L2 type test. Change: the up-step and floor-height tests are PATH-05's (M0 is flat ground), and packet discipline is NET-01's |
| QAR-35 | must | changed | PERF-03 as requested. Change: the 384² import time is MAP-01's, since the importer arrives in M1 (TL-20) |
| QAR-36 | should | applied | CORE-07; quiescing in PATH-16, quarantine in CORE-12 |
| QAR-37 | should | partly | ORD-01: 20 seeded instances, the L1 replay, the two-client view in Acceptance. The two-mock-player Done proxy moves to NET-01, which brings packet capture in M1 |
| QAR-38 | should | applied | PERF-02 |
| QAR-39 | should | applied | PERF-04 |
| QAR-40 | should | applied | QA-02 (with the fakes arriving with their ports, TL-17) |
| QAR-41 | could | applied | QA-03 AC3 (`:mod:test` ≤ 60 s) |
| QAR-42 | should | applied | QA-04 |
| QAR-43 | should | applied | QA-05 (S → M), extended by MAP-01 and QA-11 |
| QAR-44 | should | changed | QA-06 keeps the container verdict; the two-client join comes with CAM-01 and `RtsScreen` driving with QA-18 (section 5) |
| QAR-45 | should | changed | QA-07 has the warm-up, windows, solo perf batches, `-prof gc` and the validity rules; the A/B runner and between-tick timers are built with PERF-05 |
| QAR-46 | should | applied | QA-08 (S → M, release job, Linux S6, speed ratio); the golden step in QA-18 |
| QAR-47 | could | applied | QA-09 AC4 (D) and the desync proxy |
| QAR-48 | should | applied | M0's Done gate and Acceptance |
| QAR-49 | must | applied | PATH-16 AC1 and M1 Done gate 4 |
| QAR-50 | must | applied | PATH-01 (S8), PATH-16 (S7), DATA-03 (S12), UNIT-09 (S10) with their exits and the ADR status changes (M1 Done gate 5) |
| QAR-51 | must | applied | CORE-11 |
| QAR-52 | must | applied | PATH-07 |
| QAR-53 | must | applied | PATH-05 AC3, PATH-09 AC2, PERF-05 AC2, M1 Done gates 2 and 3 |
| QAR-54 | must | applied | ORD-03 AC2 |
| QAR-55 | should | applied | PATH-01 (reproducible census on a self-terminating server) |
| QAR-56 | should | applied | Split between PATH-02 (L2 table, L1 pages, page-build bench) and PATH-15 (capture budget, `OWN_WRITE`, prewarm in a solo batch) |
| QAR-57 | should | applied | Provisional steering and AC2 in L1 in PATH-03; `BUSY` and admission by W in PATH-16 |
| QAR-58 | should | applied | PATH-04 AC3 |
| QAR-59 | should | applied | DATA-03 |
| QAR-60 | should | applied | NET-01 AC3 and AC6; byte counts from QA-19 |
| QAR-61 | should | applied | The preset criteria in MAP-07; MAP-01 AC1 (≤ 30 s of wall time) and AC4 (L5) |
| QAR-62 | should | applied | M1 Done gate 6 |
| QAR-63 | should | applied | PATH-10 AC3 in M2's consent batch |
| QAR-64 | could | applied | DATA-02 AC2 and AC3 |
| QAR-65 | must | applied | CAM-04 and UI-01 AC2 |
| QAR-66 | must | applied | CAM-01 (§19.2's exit, scripted two-client run with a latency injector, M → L) |
| QAR-67 | must | applied | M2 Done gate 3 and Acceptance 1; CAM-03 AC2 |
| QAR-68 | must | applied | CAM-03 AC1 and AC3 |
| QAR-69 | must | applied | CAM-05 AC2 and AC3; UI-04 AC3 and AC4 |
| QAR-70 | must | changed | As NET-08 (renumbered from QA's NET-09); UNIT-03's far-unit criterion is NET-08 AC5 |
| QAR-71 | should | applied | CAM-02 (8–64; AC4 in Acceptance) |
| QAR-72 | should | applied | UI-01 AC2 and AC3 |
| QAR-73 | should | applied | UI-03 |
| QAR-74 | should | applied | UI-05 |
| QAR-75 | must | applied | AI-17's repeated, announced waves; stall defined as in QAR-03 |
| QAR-76 | must | applied | QA-11 |
| QAR-77 | must | applied | MATCH-01 (and MAP-07 for the gamerules) |
| QAR-78 | must | applied | CMB-01 AC2–AC5; invulnerability handling in CMB-08 |
| QAR-79 | should | applied | ECO-03 |
| QAR-80 | should | applied | ECO-01 and QA-02's invariants |
| QAR-81 | should | applied | BLD-03 AC5 and AC6 |
| QAR-82 | should | applied | BLD-01's blueprint lint |
| QAR-83 | should | applied | UI-06 and UI-07 |
| QAR-84 | should | applied | ROADMAP.md's payload rule and each payload's card: `unit_hud`, `ui_query`, `unit_detail` and `fx` now in M2 (NET-08, AUD-01); `faction_state`, `building_state`, `match_event` and `match_control` in M3 |
| QAR-85 | must | applied | UNIT-05 |
| QAR-86 | must | applied | BLD-07 |
| QAR-87 | should | applied | BLD-05 AC2 and AC3 |
| QAR-88 | should | applied | ECO-09 AC3 |
| QAR-89 | should | applied | AUD-01 AC2 and AC3 |
| QAR-90 | should | applied | CMB-08 AC2 and AC6 |
| QAR-91 | must | applied | UI-11 (now M3), UI-20 and UI-12 |
| QAR-92 | should | applied | MATCH-04 (now M4) |
| QAR-93 | should | applied | MATCH-05 AC4 (now M3) and PERF-09 AC2 |
| QAR-94 | should | applied | MATCH-04 (the ring, now in M4) |
| QAR-95 | should | applied | With DR-19: 30 minutes in M3, median 20–35 and 45 in M6; QA-12 in `verifySoak` with its L1 variant |
| QAR-96 | should | applied | QA-13 |
| QAR-97 | must | changed | As NET-09 (renumbered from QA's NET-08) |
| QAR-98 | must | applied | NET-04 |
| QAR-99 | should | applied | NET-03 AC3 |
| QAR-100 | should | applied | NET-02 AC2; NET-05 AC4 in Acceptance |
| QAR-101 | should | applied | NET-06 |
| QAR-102 | should | applied | QA-14 |
| QAR-103 | should | applied | QA-15 builds on QA-19 |
| QAR-104 | must | applied | FOW-01, FOW-07, FOW-02 and M8's Done gate |
| QAR-105 | should | applied | FOW-05 |
| QAR-106 | should | applied | PERF-15 to PERF-18 |
| QAR-107 | should | applied | TERR-01 AC2 |
| QAR-108 | could | changed | In BLD-16 AC5, where the garrison's body rule now lives |
| QAR-109 | could | applied | MATCH-09 AC3 |
| QAR-110 | should | applied | ORD-02 (16), PROD-01 (5), ECO-05 (24 blocks), ECO-08 (75/50/25/0 %), BLD-02 (the placement limits), CMB-02 (exact ticks) |
| QAR-111 | must | applied | In the roadmap (M0's gate rules); the architecture and 05-testing.md corrected on 2026-10-21 (follow-up 1) |
| QAR-112 | must | applied | In the roadmap (QA-04); architecture §3.4 corrected on 2026-10-21 (follow-up 2; ADR-0023 amends ADR-0010) |
| QAR-113 | should | applied | In the roadmap (QA-19); architecture §12.5 corrected on 2026-10-21 (follow-up 3) |

## 8. Refinements

Scope-neutral refinements of approved cards (test levels, measuring methods, wording, scope moved
between cards). Each dated block below lists them; anything that changes what a card delivers is a
`changed` card for the owner instead.

### 2026-10-21, after the owner's approval

After the owner approved M0–M6 and took the decisions needed by M6 as recommended, the
documents were reconciled. These card edits move existing scope between approved cards or make
a criterion testable; none adds scope, and no status changed. The owner may veto any of them.

| Card | Change | Why |
|---|---|---|
| CORE-01 | Criterion 3 in L2; the old criterion 4 split into 4 (L3), 6 (L5) and 7 (L0, rule B8) | One verdict level per criterion; `MatchSpace` is a mod type, and the lifecycle events need a running server |
| CORE-01 → NET-01, ECO-16 | The UUID-keyed player and faction roster moves to NET-01 (new AC9); Gaia moves to ECO-16, the first neutral entity | Neither has a consumer or a test in M0 |
| QA-10 → PATH-10 | The dev-overlay host moves to PATH-10 | Its first content is the movement overlay |
| CORE-03 → FOW-01 | The hidden-target case of command validation moves to FOW-01 AC5 | No fog exists in M0 |
| PERF-04 → ART-01 | S4 measures bare humanoids in M0; the armoured and held-item pass is ART-01 AC5 in M3 | Equipment layers arrive with ART-01 |
| QA-09 | Confirms ADR-0022 at its first use instead of writing it | ADR-0022 was recorded with the approval |
| PERF-01 | Confirms the revised ADR-0019 with M0's measurements and moves it to Accepted | ADR-0019 was revised on 2026-10-21 |
| TERR-03, M9 | A PERF-14 border scene joins M9's consent batch and Done gate | Criterion 3 needed a scheduled frame measurement |
| QA-02, M0 aggregates | `@Tag("slow")` arrives in M1; `verifySlow` exists from M0 for the GameTest batch `slow`; `verifyHarness` and the M0 contents of `verifySoak` added to the aggregates table | Matches the M0 spec |
| FOW-01, FOW-07, UI-16, AI-18, MATCH-07; M8 goal | "Conditional on D-24 (c)" removed | The owner took D-24 (c) |
| CMB-08 | Deferred; nothing in M5 depends on it | D-08 chose the observer avatar |

### 2026-10-25, M5 mobility implementation

| Card | Change | Why |
|---|---|---|
| PATH-11 | Its L3 thirty-horsemen crossing runs in M5 T6, after UNIT-06 supplies the actual mounted body. T2 verifies the class grids, real large-unit motion, mixed groups and exact save/resume in L1 and reruns the existing L3 movement gates. PATH-11 remains in progress until the T6 crossing passes. | T2 precedes creation of the mounted entity; a large humanoid test double would not verify the approved mounted integration. The criterion, level and milestone scope stay unchanged. |

