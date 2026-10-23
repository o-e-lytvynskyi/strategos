# ADR-0012: Templates load through a reload listener with core-side inheritance

Date: 2026-10-21

## Status

Accepted; targets confirmed 2026-10-23 by spike S12

**Confirmed 2026-10-23** by spike [S12](../spikes/S12-content-cycle.md) (M1 T16): `/reload` takes
0.26–0.33 s end to end with twenty test templates over two levels of inheritance and mixins, the
templates' own share about 2 ms, and each of twelve planted errors is reported at its file and
JSON pointer. Two details of the implementation, neither a change of the decision: at the server's
start the listener applies before any server exists, so the server reads the same files itself
when it starts; and the client acknowledges every set with the SHA-256 it decoded, the server
resending on a mismatch (§1.6 D row 9 of the M1 spec).

## Context

Research contradiction 3 and gap G6: should units, buildings, techs and civs be datapack
registries, a reload listener, or registries for ids plus reload data? Verified facts:

- **Datapack registries are a poor fit.** They load only at world load and ignore `/reload`.
  `RegistryDataLoader` decodes each element file on its own, so a registry cannot resolve
  0 A.D.-style parents and mixins without a mixin (fact 22). Reloadable datapack registries arrive
  only in NeoForge 26.3.
- **A reload listener re-runs on `/reload`** and owns its parsing. It needs its own sync payload.
  `OnDatapackSyncEvent` reaches a joining client before any entity pairing (PlayerList.java:208,
  before `addNewPlayer` at 234) and fires for everyone on `/reload` (920).
- **The template system needs:**
  - parent chains and mixins;
  - merge operations (add, multiply, replace, disable, token edits);
  - derived kinds (foundation, preview, mirage);
  - error reports that name the file and JSON path;
  - and a fast balance loop: edit JSON, `/reload`, run a scenario.
- **No planned feature needs a `Holder` or tag that points at a template.** Class tokens replace
  tags.
- **An `EntityType`** with its renderer and tracking settings is fixed at startup (fact 23).

## Decision

- **One server reload listener,** `TemplateReloadListener extends SimplePreparableReloadListener`,
  registered in `AddReloadListenerEvent`. It loads:
  - templates (`data/<ns>/strategos/templates/**.json`);
  - techs, civs, auras, resources, abilities and AI strategy scripts;
  - map manifests;
  - blueprint `.nbt` files, parsed by our own loader because `StructureTemplate` palettes are
    private.
- **`prepare`** reads files off-thread.
- **`apply`** (main thread) does the rest:
  - runs the core `TemplateCompiler` (inheritance, merge operations, derived kinds) under NeoForge's
    conditional ops;
  - decodes with codecs defined in core;
  - runs `TemplateLint` (unknown keys, tokens, value paths, missing blueprints, models and ids);
  - builds an immutable `TemplateSet` versioned by the SHA-256 of its canonical JSON.

  The set swaps into running matches at the next tick boundary as a journaled `ContentSwap`
  input.
- **Errors** are `TemplateProblem(file, JSON pointer, severity, message)`. All of them are
  collected. A reload with errors keeps the previous set and reports to the log, operators and
  `/strategos templates errors`. A match needing a failed template refuses to start, naming it.
- **Client sync.** On `OnDatapackSyncEvent`, the `strategos:template_set` payload carries the
  client view and the hash. Network indices come from an append-only id table that survives reloads
  within a session, and the client asks for a resend on a hash mismatch.
- **Registries hold only engine-fixed things:** three generic unit entity types, damage types,
  payload types. Templates choose a body type, a model archetype, a skin and equipment.
- **NeoForge data maps on `minecraft:block`** carry block properties: `strategos:nav_class`
  overrides and `strategos:resource_yield`. They work for other mods' blocks with no code.
- **Hot reload is supported in dev worlds and GameTest servers.** A reload that removes a template
  still used by a running match is refused.

## Consequences

- Balance work is a `/reload` away, and the content compiler is plain Java tested in L1.
- Template ids are strings: saves reference templates by id, and the network uses compact indices.
- Client sync is ours to maintain: about 200 KB for the MVP content, capped at 256 KB by an L2
  test (DATA-03).
- A datapack can add units that combine shipped model archetypes with resource-pack textures; a
  new archetype needs Java.
- At the 26.3 port, templates could move into a reloadable registry. The compiler and the
  inheritance stay ours either way.
- Spike S12 measures the `/reload` cycle (target ≤ 2 s) and checks that every error names its file
  and JSON path. It confirms targets, not the decision.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Datapack registries for all content, inheritance flattened at datagen time | No `/reload`; every balance change needs a world restart; inheritance invisible to datapack authors; errors at world load |
| Registries for stable ids plus reload-listener balance data | Two sources of truth for one template, and no feature needs the `Holder`s |
| Templates as Java classes | Contradicts "content is data" (design rule 4) |
| Vanilla `StructureTemplate` palettes for blueprints | Private; parsing ourselves gives an immutable core `BlueprintShape` |
