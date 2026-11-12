# Strategos

*Working title; the name may change before the first release.*

A real-time strategy layer for Minecraft: Java Edition 1.21.1 (NeoForge). Command a faction from
an RTS camera, gather from real forests and quarries, raise buildings block by block, train an
army, advance through the ages and defeat your opponent — or drop into first person and walk
among your own units.

Strategos is inspired by classic RTS games such as 0 A.D. and Age of Empires. It is not
affiliated with or endorsed by Wildfire Games, Microsoft or Mojang, and it contains no code, data
or art from those games.

## Status

Pre-alpha, in active development; not yet a playable release.

| Milestone | What it brought | Status |
|---|---|---|
| M0 Foundation | Build, test harnesses on eight levels, CI, the core/adapter split | accepted |
| M1 Movement | Crowds of hundreds with avoidance, pathfinding on real terrain, formations | done |
| M2 Command | RTS camera, selection, orders, health, first person and back | done |
| M3 First loop | Workers, wood and food, buildings, resource bar, a first victory | done |
| M4 Economy and construction | Mines, quarries, fields, builders, production, a computer opponent | done |
| M5 Combat | Archers, horsemen, counters, garrisons, a battle demo | open for contributors |
| M6 | The first playable version (MVP) | approved |

The roadmap, features and decisions: [docs/ROADMAP.md](docs/ROADMAP.md); where things stand:
[docs/STATE.md](docs/STATE.md).

## Try it

Requirements: JDK 21 (Gradle fetches it if missing), Git. Then:

```
./gradlew verifyFast                      # build and the fast tests, about a minute
./gradlew :mod:runScenario -Pscenario=demo -Pdemo=all -Pscenario.timeoutSeconds=3600
                                          # opens a game window: a narrated tour of every mechanic so far
scripts/playtest.sh                       # a dedicated server and two clients, for playing it yourself
```

Playtest steps: [docs/testing/playtest.md](docs/testing/playtest.md).

## Platforms

- Minecraft 1.21.1, NeoForge 21.1.264 or newer, Java 21; no other mods required.
- **Developed and measured on one machine so far:** an Apple M4 Pro with 24 GB on macOS 26. CI
  runs the automated tests on Linux.
- **Windows has not been tested.** Every performance baseline is that one Mac, which makes them
  subjective: results from other operating systems, CPUs and GPUs are very welcome
  ([CONTRIBUTING.md](CONTRIBUTING.md) "Platforms and measurements").

## Contributing

Volunteers are welcome, with or without AI agents. Work is claimed by pull request before it
starts, built on your own branch or fork, tested locally, and handed in with a milestone report.
Start with [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/onboarding.md](docs/onboarding.md); the
rules every developer and agent follows are in [AGENTS.md](AGENTS.md).

## Documentation

| Document | Contents |
|---|---|
| [docs/STATE.md](docs/STATE.md) | Where the project stands, milestone by milestone |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestones, features and the decisions the owner approves |
| [docs/branches/](docs/branches/README.md) | Who works on what: one branch file per claim |
| [docs/onboarding.md](docs/onboarding.md) | Joining as a developer: tools, approach, claim to merge, reports |
| [docs/developer-setup.md](docs/developer-setup.md) | Setting up Claude Code or Codex for the project |
| [docs/01-feasibility.md](docs/01-feasibility.md) | What is realistic, complexity, risks, the MVP |
| [docs/02-tooling.md](docs/02-tooling.md) | Platform, versions, libraries, dependency policy |
| [docs/03-design-process.md](docs/03-design-process.md) | How the project is designed and built |
| [docs/04-architecture.md](docs/04-architecture.md) | Architecture; decisions in [docs/adr/](docs/adr/README.md) |
| [docs/05-testing.md](docs/05-testing.md) | Test levels, commands, Definition of Done |
| [docs/kb/](docs/kb/README.md) | Engine, toolchain and simulation pitfalls already paid for |
| [docs/security.md](docs/security.md) | Agents, untrusted code, dependencies, privacy |
| [docs/research/](docs/research/README.md) | The research behind the decisions |

## Licence

[PolyForm Noncommercial License 1.0.0](LICENSE). All rights belong to the owner; personal and
other non-commercial use is allowed; commercial use is not, for now. The terms may change after
the full release. Contributions: [CONTRIBUTING.md](CONTRIBUTING.md) "Licence of contributions".
Adapted third-party code and its licences: [NOTICE](NOTICE).
