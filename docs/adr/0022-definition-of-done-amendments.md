# ADR-0022: Definition-of-Done amendments for networking and tooling changes, and the inventory check in L2

Date: 2026-10-21

## Status

Accepted. The owner approved it with the roadmap on 2026-10-21. It amends
[ADR-0006](0006-test-levels-and-definition-of-done.md): two rows of its Definition of Done, the L5
column, and the level of the engine-surface inventory check.

## Context

- **Two rows of ADR-0006's Definition of Done cannot be met as written**
  ([design/roadmap-review-qa.md](../design/roadmap-review-qa.md), QAR-05):
  - **networking and payloads** require L7 before every merge. L7 is a session the owner runs on a
    dedicated server with two clients, and payloads land in most milestones from M1 on
    ([04-architecture.md §12.2](../04-architecture.md));
  - **build and tooling** require L4 for every change. L4 needs the scenario runner that QA-06
    builds, and it opens a client window that can take the owner's keyboard focus (gap G4), while
    QA-01, the first card of M0, is itself a build change.
- **The roadmap already applies two amendments** and attributes them to QA-09's ADR
  ([ROADMAP.md](../ROADMAP.md#rules-every-card-follows), "Rules every card follows"). This is that
  ADR; the owner approved the roadmap, and with it these rules, on 2026-10-21.
- **The core is deterministic** (ADR-0009). A rule asserted in L3 within a tolerance band ("±5 %",
  "at least 95 %") can only hide a bug, and a GameTest per rule would break the L3 suite's target of
  under 3 minutes (ADR-0006;
  [design/roadmap-review-techlead.md](../design/roadmap-review-techlead.md), TL-04).
- **ADR-0006 lists the engine-surface inventory in L0**, beside the ArchUnit rules, while ADR-0020
  and rule B12 ([04-architecture.md §2.4](../04-architecture.md)) make it an L2 test. The check
  compares the inventory with the registered entity types, payload types and data registrations.
  They exist only once Minecraft's registries are bootstrapped, and L0's ArchUnit run reads compiled
  classes without Minecraft on its classpath
  ([milestones/M0-foundation.md §7.1](../milestones/M0-foundation.md)).

## Decision

**ADR-0006's Definition of Done, as amended** (R = required, C = conditional; changed cells in
bold):

| Change type | L0 | L1 | L2 | L3 | L4 | L5 | L6 | L7 |
|---|---|---|---|---|---|---|---|---|
| Core simulation, pathfinding, economy | R | R, with new tests | – | C (if visible in the world) | – | **R** | C (hot path) | – |
| Server gameplay adapter (bodies, blocks, AI glue) | R | R | C | R, with a new GameTest | – | R | C | milestone |
| Data, registries, codecs, datagen | R | – | R | R | – | R | – | – |
| Networking and payloads | R | – | R (round trip and size) | R (mock players) | – | R | C | **R at the milestone exit** |
| Client UI, HUD, camera, rendering | R | R (pure maths) | – | – | R (consent) | R | C (frame time) | – |
| Build and tooling | R (full `verify`) | R | R | R | **R once QA-06 exists, with consent or on CI** | R | – | – |

- **Networking changes** merge after L3 with mock players and L5. L7 runs at the milestone exit,
  by QA-09's playtest protocol, not before every merge.
- **Build and tooling changes** need no L4 before QA-06 exists. Once it does, they run L4 in the
  owner's consent batch (D-22) or on CI under Xvfb (D-23).
- **L0 and L5 run on every change**, because both are part of `verify`, the gate before every
  commit. That makes L5 required in the core-simulation row too.
- **Rules are checked exactly in L1.** Formulas, timings and thresholds of the deterministic core
  are asserted as exact values in `SimHarness` tests, never as tolerance bands in L3. L3 keeps one
  integration test per feature, which proves that the adapters carry the rule into the world; "a
  new GameTest" in the table means that test.
- **The engine-surface inventory check runs in L2** (`:mod:test`), because it reads the registered
  types and payloads. In ADR-0006's level table, L0 keeps formatting, compilation, ErrorProne +
  NullAway (JSpecify) and the ArchUnit rules, and the inventory (B12) moves to L2, as ADR-0020
  already says.

Everything else in ADR-0006 stands: the levels and who runs them, the rules that make the levels
trustworthy, and the rest of what "done" means.

## Consequences

- A networking change is done without an owner session; the owner's two-client session gates the
  milestone exit instead.
- The M0 build cards before QA-06 close without a client window. Afterwards, a build change gets
  its L4 run in the next consent batch or on CI.
- Every change pays for L5 inside `verify`, so a client class leaking into common code fails before
  any commit, whatever the change type.
- The L3 suite grows by one test per feature, not one per rule, which keeps it inside its 3-minute
  target.
- A registration change is checked by `:mod:test`, which `verify` includes; an ArchUnit run alone
  no longer claims to cover the inventory.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Keep L7 before every networking merge | Each payload change would wait for an owner session; L3 mock players already prove fog and audiences per change |
| Keep L4 for every build change | Impossible before QA-06, and afterwards a client window per build change, against the consent policy (D-22) |
| Rule checks in L3 with tolerance bands | Bands hide bugs in deterministic code, and a GameTest per rule breaks the L3 time target |
| The inventory check as an ArchUnit rule in L0 | ArchUnit sees bytecode, not registries; registered types, payloads and data maps exist only after bootstrap |
| Rewrite ADR-0006's table in place | Accepted ADRs are not rewritten ([README](README.md)) |
