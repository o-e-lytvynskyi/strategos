# Strategos — instructions for the assistant

A Minecraft 1.21.1 NeoForge mod that recreates the RTS mechanics of 0 A.D. and Age of
Empires, basics first. Professional bar: clean architecture, best practices, performance,
and automated tests of every intermediate result.

## Start here

1. Read [docs/STATE.md](docs/STATE.md): where the project stands, what is next, what waits for
   the owner.
2. Read only the docs the task needs:

| Question | Where |
|---|---|
| Features, milestones, what the owner approved | [docs/ROADMAP.md](docs/ROADMAP.md), cards in `docs/roadmap/` |
| How the mod is built (authority, tick, units, content, pathing, network, client) | [docs/04-architecture.md](docs/04-architecture.md), decisions in [docs/adr/](docs/adr/README.md) |
| Versions, libraries, dependency policy | [docs/02-tooling.md](docs/02-tooling.md) |
| Test levels, commands, Definition of Done | [docs/05-testing.md](docs/05-testing.md) |
| Roles, feature lifecycle, milestone cycle | [docs/03-design-process.md](docs/03-design-process.md) |
| What is realistic, risks, MVP | [docs/01-feasibility.md](docs/01-feasibility.md) |
| Evidence: engine facts, prior art, mechanics | [docs/research/README.md](docs/research/README.md) |
| The current milestone's implementation spec | `docs/milestones/` |

## Hard rules

- **Clean-room.** Never open GPL or all-rights-reserved sources: Reign of Nether, MineColonies,
  Structurize, BlockUI, Ancient Warfare 2, Villager Recruits and family, 0 A.D. code or data —
  not even on the web. Use the behavioural notes in `docs/research/`. MIT/Apache-2.0 code may be
  adapted with its notice in `NOTICE` ([ADR-0005](docs/adr/0005-clean-room-and-licensing.md)).
- **Verify engine APIs before relying on them.** Grep the decompiled Minecraft 1.21.1 + NeoForge
  sources (`neoforge-<ver>-sources.jar` from `createMinecraftArtifacts`; see the pet-projects
  knowledge base `minecraft-mod.md`). Never trust memory for class, method or event names.
- **Rules in `core`, Minecraft in `mod`.** `core` never imports Minecraft; game state is owned by
  the core ([ADR-0008](docs/adr/0008-authority-and-state-model.md)).
- **Runs.** Only run tasks that end on their own (builds, `:core:test`, `:mod:test`,
  `runGameTestServer`, `runServerSmoke`, perf batches). The owner starts interactive `runClient`
  and `runServer` sessions. **A client window (scenario harness, spikes S3/S4/S9) opens only
  after asking the owner**: it can take keyboard focus.
- **Owner approvals:** roadmap features, golden images, performance baselines and thresholds, new
  runtime dependencies, anything that changes the product.
- **Git:** commit and push to the private GitHub repository (`o-e-lytvynskyi/strategos`, over SSH)
  without asking, after each green task; English messages, no assistant traces. Making anything
  public stays the owner's decision.
- **Language:** everything in the repository is English; chat with the owner is Russian.

## Definition of Done

Tested at the lowest level that can express it; every level its change type requires passes
([ADR-0006](docs/adr/0006-test-levels-and-definition-of-done.md), amended by
[ADR-0022](docs/adr/0022-definition-of-done-amendments.md); summary in
[05-testing.md](docs/05-testing.md#definition-of-done)); no new ErrorProne/NullAway
findings; STATE.md, roadmap statuses and the affected docs or ADRs updated; new pitfalls written
to the pet-projects knowledge base in the same session.
