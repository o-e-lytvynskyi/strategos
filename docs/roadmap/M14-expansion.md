# M14 · Expansion

Feature cards of milestone M14. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

## Milestone

**Goal.** The deeper feature set (the catalog's T2), each card approved on its own: corrals, trade
and barter, heroes and auras, catapults, stone walls, territory decay, more victory types with
sacred sites, neutral diplomacy, special formations, naval play and two more civilisations. No
fixed order beyond the listed dependencies.

**The owner plays.** Traders run between markets; a hero's aura turns a fight; catapults break a
stone wall; a naval skirmish on a lake map; sacred-site beacons change colour as they are captured;
a wonder victory.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the approved M14 cards passes.
2. QA-16 with four civilisations: every pairing within 80–120 of 200 fixed seeds (L1).
3. L6 (PERF-18): catapult splash, ships and traders within A–E; the 2× stress pass holds.

**Acceptance** (owner): a playtest per approved feature.

**Retires.** No risk from the register; each card carries its own.

**Size and critical path.** 15 cards (1 S, 5 M, 9 L), at most 106 card-days. Critical path: CMB-14 →
MATCH-10 → MATCH-15 (23 card-days).

---

## Cards

#### ECO-12 · Corrals and domestic animals
- **Value:** A renewable meat economy for civilisations that herd (P1).
- **In scope:** a corral that trains domestic animals for food on ECO-16's fauna archetype;
  slaughtering by workers or horsemen.
- **Out of scope:** herding wild animals.
- **Acceptance:**
  1. Training and yields match the data (L1).
  2. Animals stay inside their match area (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-16, BLD-08 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### ECO-13 · Trade routes and barter
- **Value:** Long, risky trade routes that pay more the farther they reach, and a market that turns
  surplus into what you need, at a price (P4).
- **In scope:** markets; traders walking between own or allied markets; gain that grows faster than
  linearly with straight-line distance (re-derived at our scale); an international bonus; raids on
  traders; barter at the market with prices that move with each deal and recover over time.
- **Out of scope:** naval trade; trading between players (MATCH-11).
- **Acceptance:**
  1. Gain per trip follows the formula over test distances (L1).
  2. Prices move and recover as specified, and conservation holds with trade and barter (L1).
  3. Traders run their route unattended until stopped or killed (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ECO-05, MATCH-06, PATH-09 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### CMB-14 · Heroes and auras
- **Value:** Unique leaders who change how a fight goes (P4).
- **In scope:** heroes (one alive at a time, each once per match) with auras as range-based modifier
  sources evaluated on a slower cadence; hero abilities through UNIT-10; the commander-hero option
  of D-08.
- **Out of scope:** regicide (MATCH-10).
- **Acceptance:**
  1. Auras apply and expire with range exactly as specified (L1).
  2. A hero and its aura work in a GameTest (L3).
  3. Aura evaluation fits budget A at full population (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** DATA-02, CMB-11, UNIT-10 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### CMB-15 · Catapults and splash damage
- **Value:** Artillery that breaks walls and punishes clumped armies (P4).
- **In scope:** catapults with packing and unpacking, minimum range, splash damage with falloff and
  a friendly-fire flag.
- **Out of scope:** naval artillery.
- **Acceptance:**
  1. Splash falloff follows the formula (L1).
  2. A packed catapult can't fire, and an unpacked one can't move (L1).
  3. A catapult volley works in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** CMB-04, CMB-09 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### BLD-14 · Stone walls and gates
- **Value:** Real fortifications for the late game (P1).
- **In scope:** stone wall segments with towers and gates (Town phase); units can stand on walls,
  whose walkable tops are core overlays; resistant to everything but siege.
- **Out of scope:** walls in open-world mode.
- **Acceptance:**
  1. Only siege damages walls meaningfully (L1).
  2. Drawn walls follow the terrain as continuous segments with gates (L3).
  3. Units walk on wall tops (L3).
- **Tests:** L0, L1, L3 · **Depends on:** BLD-13, CMB-15 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### TERR-04 · Territory decay
- **Value:** Buildings cut off from your territory slowly change hands (P4).
- **In scope:** buildings in neutral, enemy or unconnected territory lose capture points to their
  neighbours; garrisons slow it.
- **Out of scope:** fields and walls, which are never captured.
- **Acceptance:**
  1. Decay rates follow the data (L1).
  2. A cut-off building changes hands in the expected time (L1).
  3. Decay runs in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** TERR-01, CMB-10 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### MATCH-10 · Wonder, regicide and relic victories
- **Value:** Other ways to win than conquest (P4).
- **In scope:** wonder (build and hold for a set time), regicide (protect your hero), relics (hold
  them all for a set time); chosen in setup.
- **Out of scope:** scenario-specific goals (MATCH-12).
- **Acceptance:**
  1. Each condition ends the match exactly as specified (L1).
  2. Countdown timers reach every player (L3).
  3. Countdown golden (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** MATCH-03, CMB-14 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### MATCH-11 · Neutral diplomacy, tribute and shared vision
- **Value:** Richer politics in team games.
- **In scope:** neutral relations; tribute of any resource; shared vision for allies through a
  technology.
- **Out of scope:** markets between players (ECO-13).
- **Acceptance:**
  1. Tribute moves resources exactly, and conservation holds (L1).
  2. Shared vision reveals allies' sight only after the technology (L3).
- **Tests:** L0, L1, L3 · **Depends on:** MATCH-06, FOW-02 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### MATCH-15 · Sacred sites as beacons
- **Value:** A fast game mode with a landmark every Minecraft player knows, visible across the
  whole arena (P1).
- **In scope:** neutral capture points on map-placed beacon bases that generate resources or count
  toward a hold-to-win condition, as AoE IV's sacred sites do; ownership shown as the beam's colour
  through faction-stained glass above the beacon (vanilla `StainedGlassBlock` implements
  `BeaconBeamBlock`, which tints the beam), placed through the block writer.
- **Out of scope:** relics (MATCH-10).
- **Acceptance:**
  1. Capture and income follow the data (L1).
  2. The hold-to-win timer ends the match as specified (L1).
  3. The glass above the beacon changes with the owner (L3).
  4. Golden of a captured site (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** CMB-10, MATCH-10 · **Size:** M ·
  **Tier:** T2
- **Status:** proposed

#### ORD-08 · Special formations
- **Value:** Formations with tactical meaning, if playtests ask for them (P3).
- **In scope:** further formations (for example staggered and flank); formations stay movement-only
  unless an ADR decides otherwise.
- **Out of scope:** formation combat bonuses.
- **Acceptance:**
  1. Each formation keeps its slot error under 1 block on flat ground (L1).
  2. Each formation marches in a GameTest (L3).
- **Tests:** L0, L1, L3 · **Depends on:** ORD-06 · **Size:** M · **Tier:** T2
- **Status:** proposed

#### PATH-14 · Water navigation
- **Value:** Ships sail, and land troops where the shore allows.
- **In scope:** a water mobility class; shore surfaces for docks and landings; transport loading and
  unloading points.
- **Out of scope:** the ships themselves (UNIT-08).
- **Acceptance:**
  1. Water reachability equals a flood fill (L1).
  2. A ship paths around islands to a landing point (L3).
- **Tests:** L0, L1, L3 · **Depends on:** PATH-11 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### UNIT-08 · Ships, docks and fishing
- **Value:** Naval skirmishes, transports and an economy of their own on water maps.
- **In scope:** a dock, which is also the dropsite for fish; fishing boats gathering fish deposits;
  a transport and a warship; loading and unloading units; ship bodies, whose body mode or entity
  type the card's ADR decides.
- **Out of scope:** naval trade.
- **Acceptance:**
  1. Warships fight with CMB-04's ranged rules (L1).
  2. Fishing yields match the data, and boats return to the nearest dock (L1).
  3. A transport loads, crosses and unloads 10 units (L3).
- **Tests:** L0, L1, L2, L3 · **Depends on:** PATH-14, CMB-04, BLD-08 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### CIV-03 · Civilisations 3 and 4
- **Value:** More peoples to play, each with its own style (P4).
- **In scope:** two civilisations chosen by the owner, with rosters, bonuses and buildings in data.
- **Out of scope:** their art (ART-05).
- **Acceptance:**
  1. Every unit and building passes the existing GameTests (L3).
  2. QA-16 pairings fall within 80–120 of 200 (L1).
- **Tests:** L0, L1, L3 · **Depends on:** CIV-02 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### ART-05 · Civilisations 3 and 4 art
- **Value:** The new civilisations are recognisable at a glance (P2).
- **In scope:** skins, banners, blueprints and icons for both, as named owner tasks where they need
  the owner's hands or outside help.
- **Out of scope:** animations beyond the shared unit set.
- **Acceptance:**
  1. Blueprints pass the lint and the construction and damage-stage tests (L3).
  2. Goldens (L4, consent).
  3. The owner approves the look (L7, acceptance).
- **Tests:** L2, L3, L4 (consent), L7 · **Depends on:** CIV-03, ART-04 · **Size:** L · **Tier:** T2
- **Status:** proposed

#### PERF-18 · Expansion perf gate
- **Value:** Siege, ships and trade never cost tick rate.
- **In scope:** the M14 row of the budget matrix: catapult splash, ships and traders for 15
  minutes; A–E; the 2× stress pass.
- **Out of scope:** open-world scale (PERF-13).
- **Acceptance:**
  1. Budgets A–E hold (L6).
  2. The stress pass holds C p95 ≤ 50 ms (L6).
  3. The report lists splash, naval and trade costs (R).
- **Tests:** L6, R · **Depends on:** CMB-15, UNIT-08, ECO-13, PERF-17 · **Size:** S · **Tier:** T2
- **Status:** proposed
