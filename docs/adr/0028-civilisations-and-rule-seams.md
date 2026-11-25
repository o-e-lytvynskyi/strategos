# ADR-0028: Civilisations are content bound per faction; gameplay rules vary through a closed catalogue of seams

Date: 2026-11-25

## Status

Proposed (M10 T2); becomes Accepted when M10's probe civilisation passes (T9b). Amends ADR-0010 (the match
configuration gains each faction's civilisation) and ADR-0021 (delivery and construction become per-faction
rules). Implements what ADR-0012 and ADR-0013 left open: the `civs` content kind and the civilisation bonus as a
faction modifier.

## Context

- **The owner's goal** (2026-11-25): one default civilisation now, "the simplest, like what we already have", and a
  foundation on which distinctive civilisations — with their own gameplay, not only other numbers — are added
  later "without rewriting the core". CIV-04 (M15, civilisation-unique mechanics) is where that lands.
- **What the core has** (the M10 survey, 2026-11-25):
  - content is component-shaped: templates with inheritance, value paths (`Housing/capacity`,
    `Dropsite/resources`, `ResourceGatherer/rates`, `Builder/buildings`), faction- and unit-scoped modifiers that
    change numbers and token lists (ADR-0013), resources as data (at most 16), phases as technologies with an
    ordinal;
  - one global content set: no civilisation concept; every faction can train, build and research everything;
  - rules are match-wide (`MatchRules`: economy and play), with no per-faction part; `MatchConfig` holds a faction
    count and the start slots;
  - a few rules are branches inside systems: population is `min(housing + BASE, max)` with `BASE = 0` "until
    civilisations"; a gatherer goes idle without a dropsite; a foundation progresses only under builders; defeat
    is the loss of every `CivicCentre`;
  - the AI and a handful of classes name templates (`strategos:building/house`, `civic_centre`, `ram`, `archer`)
    and assume houses, dropsites and builders;
  - starting entities are written per scenario slot as template ids.
- **Forces:** determinism and replay (ADR-0009: every rule is a pure function of the state; journals replay from a
  snapshot that carries `MatchConfig`); old saves and journals must load and keep their hashes; zero runtime
  dependencies (ADR-0004); hot paths stay an array index (ADR-0019).

## Decision

- **A civilisation is content.** `data/<ns>/strategos/civs/<id>.json` compiles to `CivDef` in the `TemplateSet`,
  hashed under `$civs` only when present (the `$techs` pattern, so today's hashes hold). A `CivDef` holds:
  - `name` and the phase display names (by phase ordinal);
  - `roster`: the templates and technologies its factions may own. Production, placement and research refuse
    anything outside it; the command card hides it;
  - `roles`: a map from engine roles (`centre`, `house`, `worker`, `infantry`, `barracks`, `tower`, …) to its templates,
    and `centreClass`, the class token whose buildings count as its centres (default `CivicCentre`). Code that
    needs "a house of this faction" asks the role, never a template id;
  - `start`: the start kit — entities by role or template, relative to the slot, and the starting resources;
  - `bonuses`: faction modifiers (ADR-0013's faction scope), applied at match start under a reserved source id and
    re-derived wherever `AiHandicap` is (restore, content swap, AI change);
  - `rules`: per seam, the variant id and its parameters (below); a missing seam takes its default;
  - `ai`: the strategy profile per level.
- **One default civilisation,** `strategos:default`: today's content and today's behaviour, every seam at its
  default. An empty or absent civilisation list means every faction plays it, so old saves, scenarios and journals
  load unchanged.
- **Each faction's civilisation is part of the match configuration:** `MatchConfig.civs` (index = faction),
  `optionalFieldOf` with an empty default, fixed for the match, chosen in setup (screen, payload, command, scenario
  slot). `StateHasher` mixes the civilisation ids (strings, not indices) only when a faction plays a non-default
  civilisation (the `PlayRules` pattern).
- **Gameplay rules vary through seams, not branches.** Each rule a civilisation may change is an interface in
  `core.sim.civ` with a default that is today's code moved behind it, bit for bit:

  | Seam | Default (today) | First non-default variant (M10) |
  |---|---|---|
  | `PopulationRule` | housing of finished buildings + base, capped by the match maximum; the "housing needed" refusal | `fixed`: a cap from data, no houses |
  | `DeliveryRule` | a full carry heads to the nearest accepting dropsite | `direct`: a full carry is credited to the treasury where the gatherer stands |
  | `ConstructionRule` | a builder's arrival commits the foundation; progress `r × n^0.7` from builders | `self`: the foundation commits at placement and builds itself at a data rate |
  | `UpkeepRule` | nothing: no per-faction work each economy step | — (seam only; a test variant pays passive income) |
  | `ResourceRule` | every resource of the match | — (seam only) |
  | `PhaseRule` | the phase is the highest researched phase ordinal | — (seam only) |
  | `PlacementRule` | `Placement/territory` of the template; a parameter of the pure `PlacementRules`, shared with the client preview | — (seam only) |
  | `DefeatRule` | `holds`: the faction still has a building of its `centreClass` (read under `Victory.CIVIC_CENTRE`; the latch stays in `Outcome`) | — (seam only) |

  Variants live in a **closed, versioned `RuleCatalogue`** in the core: id → factory with a parameter codec. Data
  selects, code implements. A variant's behaviour never changes under its id: a changed behaviour is a new id
  (`direct@2`), and the old one stays for old journals. `CivRules` is resolved once per faction at match start into an immutable array; systems
  call `rules.of(faction).population()` and never test a civilisation id. A distinctive civilisation later is data
  plus, where its mechanic is new, one variant class registered in the catalogue with its tests — no system edit.
  A variant that needs state of its own declares a store that is saved and hashed like every other (the M9
  follow-up's "every store a step reads is saved" check covers it); M10's variants are stateless.
- **The engine's vocabulary is fixed and documented;** content is not. Class tokens the core reads (`Gatherer`,
  `Builder`, `Structure`, `Phase`, `Organic`, …) are the engine's words, like the value paths. Template ids appear in
  core code only in the built-in fallback set and save migrations; an ArchUnit rule keeps them out of `sim`.
- **The AI plays the rules it is given.** Each rule variant declares the AI hints it implies (needs housing, needs
  dropsites, needs builders, …), so a new variant brings its hints with it; `AiView` exposes them and the roles, and
  refusals carry a reason the AI can read and back off from. Strategy scripts are chosen per civilisation and
  level. A mechanic the AI has never met still needs AI work — but in one manager, not across the AI.
- **A probe civilisation proves the foundation.** It lives in test resources only (the core's fixtures and the
  GameTest pack), never in `src/main`, uses every non-default
  variant and its own start kit, and is played by the AI in seeded headless matches whose journals replay. If adding
  it ever needs an edit outside `core.sim.civ`, the foundation has failed its test.

## Consequences

- M10's default civilisation must be invisible in play: every pinned hash, golden and replay of M0–M9 stays as it
  is. That is the first acceptance check of the seams.
- Likely next seams, not built in M10: the production model (what trains where, spawning without buildings),
  the gathering model (what a gatherer does at a node), the territory model. Each future mechanic that is not one of
  the eight seams needs a new seam: an interface, its default moved out of
  the system, its tests. That is a deliberate, reviewed change to `core.sim.civ`, never a branch in a system.
- The AI loses its template-id shortcuts; its managers become slightly longer, and the probe keeps them honest.
- The balance sheet gains a section per civilisation; QA-16's matrix gains a civilisation dimension.
- Saves stay at schema 3: the new field is optional and the default reproduces today.

## Alternatives considered

- **Civilisations as data only** (rosters, numbers and bonuses; rules stay in the systems): enough for the default
  civilisation and for AoE-style civilisations, but every distinctive mechanic would then be a rewrite of a system
  and of the AI. Rejected: it is the rewrite the owner asked to avoid.
- **Branches on the civilisation in each system** (`if civ == …`): fast to write, scattered, untestable as a set,
  and every new civilisation touches every system. Rejected.
- **A scripting language in data** (rules written in content): maximal freedom, but a runtime dependency or an
  interpreter of our own, a determinism and performance risk on the hot path, and hard to lint. Rejected; the closed
  catalogue gives data the choice and keeps the behaviour in tested Java.
- **Plugin discovery of variants** (`ServiceLoader`, other mods registering rules): open-ended, but the variant set
  of a replay would depend on what is installed. Rejected for now; the catalogue is versioned with the core.
