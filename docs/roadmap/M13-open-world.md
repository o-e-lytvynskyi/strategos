# M13 · Open world

Feature cards of milestone M13. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** Play Strategos in an ordinary survival world: claim an area, keep armies simulating
off-screen, and coexist with survival players, through an `OpenWorldPolicy` that replaces
`ArenaPolicy` and never changes a world gamerule. The arena-first design is what keeps this
milestone small.

**The owner plays.** In a normal survival world the owner claims a valley and plays against the
AI; an army marches beyond the loaded chunks and appears when it arrives; a survival friend visits
the town.

**Done gate** (automated; plus the cumulative rule):
1. Every acceptance criterion of the M13 cards passes.
2. L3: armies crossing unloaded chunks arrive on time with no duplicates.
3. L6 (PERF-13): one match area and two survival mock players within A–C and the chunk policy.

**Acceptance** (owner): a session in a survival world.

**Retires.** A-R17 (players want their own world).

**Size and critical path.** 6 cards (1 S, 2 M, 3 L), at most 37 card-days. Critical path: PATH-13 →
CORE-09 → PERF-13 (21 card-days). PATH-08 (L, reserve since M1) comes first if no earlier trigger
has built it.

---

## Cards

#### MAP-06 · Open-world match areas
- **Value:** Turn any part of your survival world into a battlefield.
- **In scope:** claiming a match area in an existing world: a `SpaceShape` chunk set, pregeneration
  of missing chunks, and `OpenWorldPolicy` (capped follow-tickets per active army plus one ticket
  that keeps the level alive; protection through events and the block-change backstop only; no
  gamerule changes); several areas per world; releasing an area.
- **Out of scope:** generating new terrain.
- **Acceptance:**
  1. Claiming pregenerates and prewarms the area (L3).
  2. Two areas in one world run independently (L3).
  3. Releasing an area restores normal play, and no gamerule was changed (L3).
- **Tests:** L0, L1, L3, L5 · **Depends on:** CORE-01, MATCH-01, MAP-07 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### CORE-09 · Virtualised units and abstract travel
- **Value:** Armies and traders keep moving even where no chunk is loaded.
- **In scope:** units outside entity-ticking chunks continue as records (`BodyMode.NONE`) along
  coarse paths at their template speed and materialise on arrival; view-driven materialisation;
  capped follow tickets (a `TicketType` with a lifespan, re-placed while groups move).
- **Out of scope:** combat between virtual units.
- **Acceptance:**
  1. A group marching through unloaded chunks arrives within 10 % of its coarse-path time (L3).
  2. Materialising never duplicates a unit (L3).
  3. The cap on ticketed groups holds (L1).
- **Tests:** L0, L1, L3 · **Depends on:** CORE-06, PATH-13 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### PATH-13 · On-demand navigation for open worlds
- **Value:** Units path well in natural terrain without loading the world around them.
- **In scope:** pages built on chunk load under a count budget; sparse paged grids; persisted
  portal summaries for unloaded pages; HPA* (PATH-08 leaves the reserve here) with a second
  hierarchy level of 4 × 4 pages; navigation never loads a chunk.
- **Out of scope:** arena prewarm (PATH-15).
- **Acceptance:**
  1. Pathing never causes a chunk load (L3).
  2. Inside loaded areas, paths equal arena behaviour (L1).
  3. Memory stays within budget G (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** PATH-15, PATH-08 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### MATCH-09 · Coexistence with survival play
- **Value:** Commanders and survival players share one world fairly.
- **In scope:** rules for survival players in and near a match area (vanilla survival rules, D-08);
  vanilla mobs inside the area as agents; protection of match buildings; interaction rules between
  units and survival players.
- **Out of scope:** bounded arenas (their rules stay).
- **Acceptance:**
  1. Survival players can't alter match buildings (L3).
  2. Vanilla mobs inside the area follow the match rules (L3).
  3. A survival mock player and a commander mock player receive the same building states (L3).
- **Tests:** L0, L1, L3 · **Depends on:** MAP-06, BLD-07, D-08 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### AI-16 · AI on natural terrain
- **Value:** The AI builds a working town wherever it starts (P5).
- **In scope:** base-site selection and building placement on unprepared terrain; layouts that keep
  paths open.
- **Out of scope:** terraforming.
- **Acceptance:**
  1. The AI builds a working base on 10 captured vanilla terrains (L1).
  2. No AI foundation is blocked by terrain in a GameTest sample (L3).
- **Tests:** L0, L1, L3 · **Depends on:** AI-11, MAP-06 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### PERF-13 · Open-world perf gate
- **Value:** Strategos never slows down a survival server.
- **In scope:** the M13 row of the budget matrix: one match area and two survival mock players for
  15 minutes; A–C; chunk loading within the area policy.
- **Out of scope:** several match areas at once.
- **Acceptance:**
  1. Budgets A–C hold (L6).
  2. Chunk loading stays within the area policy (L6).
- **Tests:** L6 · **Depends on:** CORE-09, PATH-13 · **Size:** S · **Tier:** T2
- **Status:** proposed
