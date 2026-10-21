# M12 · Maps and game setup

Feature cards of milestone M12. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** Replayability: seeded random maps for bounded arenas, tools to author maps, the full
setup screen and a guided tutorial.

**The owner plays.** A four-player "River" map generated from a seed; a setup screen that offers
size, resources, population cap, victory and visibility; a friend new to the game completes the
tutorial.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M12 cards passes.
2. L1: 100 seeds per map script pass the fairness checks; on 200 fixed seeds per script, neither
   start wins 121 or more AI-versus-AI matches.
3. L3: a scripted run completes every tutorial step (the Done proxy for UI-18).
4. L6 (PERF-12): a 512² four-player generated map within A–E and G; PERF-14's large-map scene with
   consent.

**Acceptance** (owner): a named friend playtester, new to the game, finishes the tutorial in under
20 minutes.

**Retires.** No risk from the register; this milestone adds replayability.

**Size and critical path.** 7 cards (2 S, 2 M, 3 L), at most 38 card-days. Critical path: MAP-03 →
MAP-04 → PERF-12 (21 card-days).

---

## Decisions this milestone needs

### D-06 · Map generation approach

| Option | For | Against | Card delta |
|---|---|---|---|
| Hand-built map packages only | Full control over balance and pathing | Few maps; replayability depends on authoring time | MAP-05 only |
| Vanilla world generation inside bounds, then post-processing | Endless variety | Unfair starts, hard to validate; caves and cliffs break pathing | a post-processor instead of MAP-03 |
| Our own seeded generator with map scripts (recommended) | Fair, validated maps with placement patterns | A real project | MAP-03, MAP-04 |
| Prefab tiles | Fast authoring | Visible repetition | a tile set instead of MAP-04 |

**Recommendation.** Seeded, diffable map packages for the MVP and every test (MAP-01, MAP-02), then
our own seeded generator in M12, which validates fairness and reachability before a match starts.
Vanilla generation stays for a later "wild" map type and for open-world mode. **Needed by:** M12
(MAP-03). **Affects:** MAP-01 to MAP-05.

---

## Cards

#### MAP-03 · Random map generator framework
- **Value:** A new, fair map every match (P4).
- **In scope:** per D-06: our own seeded generator for bounded arenas: a height field, biome
  palettes of vanilla blocks, player placement patterns (circle, river, lines), resources balanced
  per start, validation of reachability and fairness; output as a map package.
- **Out of scope:** open-world maps (MAP-06).
- **Acceptance:**
  1. The same seed gives the same map-package hash (L1).
  2. Every generated map passes the fairness checks: resources within 5 % per start, every start
     reachable (L1).
  3. A 384² map generates in under 60 s (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** MAP-01, D-06 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### MAP-04 · Map scripts
- **Value:** Distinct map types that call for different strategies (P4).
- **In scope:** four or five scripts (for example Mainland, River, Highlands, Forest, Lakes) at
  256², 384² and 512², each documenting its terrain jobs as MAP-02 does.
- **Out of scope:** water maps that need ships (PATH-14, UNIT-08).
- **Acceptance:**
  1. 100 seeds per script pass the fairness checks (L1).
  2. On 200 fixed seeds per script, neither start wins 121 or more AI-versus-AI matches (L1).
  3. 20-seed L3 samples per script run as smoke tests (L3).
- **Tests:** L0, L1, L3 · **Fixtures:** map seed sets · **Depends on:** MAP-03 · **Size:** L ·
  **Tier:** T2
- **Status:** proposed

#### MAP-05 · Map authoring tools
- **Value:** The owner and the community can build arenas in creative mode and share them.
- **In scope:** commands to mark bounds, starts and resource nodes in a creative world and export a
  map package (`/strategos map export`); a validation report.
- **Out of scope:** an in-game map editor.
- **Acceptance:**
  1. A map built in creative exports and imports identically (L3).
  2. Validation reports unreachable starts and imbalance (L1).
- **Tests:** L0, L1, L3 · **Depends on:** MAP-01 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### ECO-11 · Treasures
- **Value:** Rewards for scouting: resource piles to grab before the enemy does (P4).
- **In scope:** collectable resource piles placed by maps, collected on contact by a worker.
- **Out of scope:** relics (MATCH-10).
- **Acceptance:**
  1. Treasure yields match the data (L1).
  2. A treasure is collected exactly once (L1).
  3. A worker picks one up in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-01, MAP-03 · **Size:** S · **Tier:** T1
- **Status:** proposed

#### MATCH-08 · Full setup screen
- **Value:** Set up exactly the match you want.
- **In scope:** map type and size, seed, starting resources, population cap, victory condition,
  teams, visibility, AI levels and personalities, civilisations; presets per profile.
- **Out of scope:** the multiplayer lobby's slot logic (NET-02).
- **Acceptance:**
  1. Every option reaches `MatchRules` (L1).
  2. Presets save and load per profile (L1).
  3. A headless setup run applies every option (L3).
  4. Goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** MATCH-02, CIV-01, MATCH-07 · **Size:** M ·
  **Tier:** T1
- **Status:** proposed

#### UI-18 · Guided tutorial
- **Value:** A new player learns the game by playing it.
- **In scope:** a scripted tutorial match covering camera, selection, economy, building, training
  and fighting; UI-14's hint system; pause-friendly; skippable at every step.
- **Out of scope:** campaigns (MATCH-12).
- **Acceptance:**
  1. A scripted headless run completes every step (L3).
  2. A named friend playtester, new to the game, finishes it in under 20 minutes (L7, acceptance).
- **Tests:** L0, L1, L3, L7 · **Depends on:** UI-14, MATCH-02, MAP-05 · **Size:** L · **Tier:** T1
- **Status:** proposed

#### PERF-12 · Large-map gate
- **Value:** Bigger maps for four players, without lag.
- **In scope:** the M12 row of the budget matrix: a 512² generated map with 4 × 150 population;
  prewarm time; memory; A–E and G headless; budget F through PERF-14.
- **Out of scope:** open-world scale (PERF-13).
- **Acceptance:**
  1. Budgets A–E and G hold, and the 2× stress pass holds C p95 ≤ 50 ms (L6).
  2. Prewarm finishes in under 60 s (L6).
  3. PERF-14's large-map scene holds budget F (L6 client, consent).
- **Tests:** L6 · **Depends on:** MAP-04, PERF-10 · **Size:** S · **Tier:** T2
- **Status:** proposed
