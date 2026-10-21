# M11 · Adaptive AI

Feature cards of milestone M11. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** An AI that plans instead of following a script: managers that share one budget,
expansion, attack plans, defence, personalities and a five-level ladder, fair up to its labelled
top level. The structure is inspired by 0 A.D.'s Petra, as design notes only, and avoids its known
weakness of separate budgets per queue.

**The owner plays.** AI-versus-AI matches on random seeds, opened as an observer, show different
openings and expansions; an attack plan gathers, telegraphs, strikes and retreats when it is
losing; the owner plays the top level and loses.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M11 cards passes.
2. L1, 200 fixed seeds: no AI stall and no exception; each difficulty level beats the one below in
   at least 124 of 200 (QA-16).
3. L6 (PERF-11): AI time within its share of budget A with three AIs at full population.
4. Consent batch: the setup golden.

**Acceptance** (owner): the owner plays two levels and files a playtest report.

**Retires.** F-R6 in full.

**Size and critical path.** 7 cards (1 S, 3 M, 3 L), at most 40 card-days. Critical path: AI-10 →
AI-11 → AI-14 → AI-15 (26 card-days).

---

## Cards

#### AI-10 · AI architecture: managers and one budget
- **Value:** An AI that weighs economy, army and defence against each other instead of running a
  fixed script (P5).
- **In scope:** managers (headquarters, economy and bases, military, defence) implementing
  `AiModule.think(AiView, AiBudget, CommandSink)` and sharing one central budget and priority
  queue; a blackboard for perception; offsets per player; work counted in evaluation units; heavy
  searches as pure jobs.
- **Out of scope:** the individual managers' logic (AI-11 to AI-13).
- **Acceptance:**
  1. The budget never overspends or deadlocks over seeded runs (L1).
  2. Managers talk only through the blackboard and commands (L0, ArchUnit).
  3. Seeded matches run on the new architecture within the AI's evaluation cap (L1).
  4. An L3 sample reaches the hash of its L1 run (L3).
- **Tests:** L0, L1, L3 · **Depends on:** AI-09 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### AI-11 · Expansion and multi-base economy
- **Value:** The AI grows like a player: new bases, new fields, new dropsites (P5).
- **In scope:** choosing new civic-centre sites by resources and territory; a base manager per
  civic centre; field and dropsite management.
- **Out of scope:** naval expansion.
- **Acceptance:**
  1. On a 384² map the AI founds a second base by the documented time in seeded runs (L1).
  2. No base starves of workers (L1).
- **Tests:** L0, L1 · **Depends on:** AI-10, TERR-02 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### AI-12 · Attack planning
- **Value:** Attacks that adapt to what the AI has seen of you, and that it still announces (P5).
- **In scope:** plan types (rush, raid, attack, all-in); composition against the scouted enemy; a
  staging point; telegraphing through AI-04's raid bar; retreat thresholds; fog-fair knowledge only.
- **Out of scope:** naval and amphibious attacks.
- **Acceptance:**
  1. Each plan type triggers in a seeded scenario (L1).
  2. The composition counters the enemy's scouted majority class (L1).
  3. An attack retreats when its strength ratio drops below the threshold (L1).
- **Tests:** L0, L1 · **Depends on:** AI-10, AI-07 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### AI-13 · Defence manager
- **Value:** An AI base that defends itself sensibly (P5).
- **In scope:** threat assessment; defenders committed by a ratio to the threat; garrisons and the
  bell; counter-attacks.
- **Out of scope:** wall and tower placement beyond the build order.
- **Acceptance:**
  1. Defenders are committed by the configured ratio (L1).
  2. Workers garrison during raids (L1).
- **Tests:** L0, L1 · **Depends on:** AI-10, BLD-10 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### AI-14 · Personalities and openings
- **Value:** Every AI match feels different (P5).
- **In scope:** personalities (balanced, aggressive, defensive, economic) and openings in data; a
  choice per match seed.
- **Out of scope:** learning from past matches.
- **Acceptance:**
  1. Each personality produces measurably different timings across seeds (L1).
  2. Openings load from data, and invalid ones fail with clear errors (L2).
- **Tests:** L0, L1, L2 · **Depends on:** AI-11, AI-12 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### AI-15 · Difficulty ladder
- **Value:** A level for every player, from first-timer to veteran, all honest below the top (P5).
- **In scope:** five levels by reaction time, planning depth and timings; open handicaps at every
  level; bonuses only at the labelled top level, shown in setup (D-19).
- **Out of scope:** dynamic difficulty.
- **Acceptance:**
  1. Each level beats the one below in at least 124 of 200 seeded matches (L1).
  2. Every handicap and bonus is listed in setup (L1).
  3. A setup golden shows them (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** AI-14, QA-16 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### PERF-11 · AI CPU budget gate
- **Value:** A smarter AI never costs tick rate.
- **In scope:** the M11 row of the budget matrix: three AI players at full population for 15
  minutes; percentiles of AI time per tick.
- **Out of scope:** AI quality.
- **Acceptance:**
  1. Total AI time stays ≤ 1 ms p95 per tick, within budget A (L6).
  2. No single AI think exceeds 5 ms (L6).
- **Tests:** L6 · **Depends on:** AI-10, QA-07 · **Size:** S · **Tier:** T2
- **Status:** proposed
