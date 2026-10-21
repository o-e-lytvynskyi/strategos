# ADR-0002: Package and namespace naming under `dev.strategos`

Date: 2026-10-21

## Status

Accepted

## Context

- **Core and mod share one module.** The core's classes are merged into the mod
  (ADR-0003), so its packages must never overlap the mod's. Overlapping packages would be split
  packages and fatal if the core ever moved into a nested jar
  ([research/testing.md §3.2](../research/testing.md)).
- **ArchUnit needs stable roots.** The rules that keep the hexagon, the client isolation and the
  determinism bans (rules B1–B13 in [04-architecture.md §2.4](../04-architecture.md)) are written
  against package roots, so the roots must be stable from the first commit.
- **One identifier everywhere.** Mod id, resource namespace, payload ids, registry names, data
  paths, config files, `SavedData` names and JFR event names all need one consistent identifier.

## Decision

| Thing | Name |
|---|---|
| Java root | `dev.strategos` |
| Core (`core/src/main`) | `dev.strategos.core..` (`shared`, `content`, `protocol`, `sim..`, `client`, `port`, `diag`) |
| Core test fixtures | `dev.strategos.core.testkit..` |
| Mod (`mod/src/main`) | `dev.strategos.mod..` (`registry`, `entity`, `server..`, `client..`, `mixin`, `platform`) |
| Test source sets of `mod` | `dev.strategos.gametest`, `dev.strategos.devtools`, `dev.strategos.arch` |
| Mod id, resource and registry namespace | `strategos` |
| Payload ids | `strategos:<name>`, for example `strategos:commands` |
| Data paths | `data/strategos/strategos/templates/…`, `data/strategos/structure/strategos/…`, `data/strategos/worldgen/world_preset/arena.json` |
| Mixin config | `strategos.mixins.json`, classes in `dev.strategos.mod.mixin` |
| Config files | `strategos-server.toml`, `strategos-client.toml` |
| `SavedData` names | `strategos_registry`, `strategos_match_<id>` |
| JFR events | `dev.strategos.*` |
| Maven group | `dev.strategos` |
| Java module | None: no `module-info` in `core` or `mod` (ADR-0003) |

## Consequences

- **No split packages.** Core and mod packages can never be split, so moving `core` into a jarJar
  later stays possible without renaming.
- **Readable architecture rules.** Each layer has its own prefix.
- **The prefix is a namespace choice, not a claim.** The reverse-DNS prefix implies the domain
  `strategos.dev`, which the project does not own. Collision risk is negligible for a mod. If the
  owner wants to publish to a public Maven repository under a verified group, the rename is
  mechanical (packages, Maven group) and must happen before the first public release (D-21).
- **Player-visible names stay neutral.** "Age of Empires", "AoE" and "0 A.D." never appear in the
  mod id, namespace, package names or primary tags (ADR-0005).

## Alternatives considered

| Alternative | Why not |
|---|---|
| A GitHub-derived `io.github.<account>.strategos` | Ties every package to a personal account name; long; changes if the repository moves to an organisation |
| One root for core and mod (`dev.strategos..` everywhere) | Packages could be split between the two source trees, which breaks a later jarJar and weakens ArchUnit's layer rules |
| A flat `strategos..` package | Not reverse-DNS; collides more easily with other code on the classpath |
| `com.strategos` | Implies a commercial domain the project does not own either; no advantage over `dev` |
