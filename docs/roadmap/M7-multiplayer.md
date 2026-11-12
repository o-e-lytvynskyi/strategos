# M7 · Multiplayer

Feature cards of milestone M7. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it. Cards after the
MVP are coarser by design and are refined at the review that opens their milestone.

Planning specification: [M7 implementation plan](../milestones/M7-multiplayer.md), 2026-10-28.
It defines tasks and contracts for review; card statuses remain `proposed` and implementation
is not opened by this document.

## Milestone

**Goal.** The owner's next stage: 2–4 players on a dedicated server, against each other or
together against AIs, with teams and shared control, a lobby, reconnects, bandwidth inside the
budget, and a server hardened against modified clients before anyone plays on it. Beside it, the
first civilisation art pass, after the MVP verdict.

**The owner plays.** On a dedicated server the owner and a friend, with two AIs, play 2 v 2 to an
allied victory; one client drops its connection and rejoins with full control. In a co-op game the
owner commands from the camera while the friend fights in first person in the same faction.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M7 cards passes.
2. L3 with mock players: commands validated per player; private state never reaches an opponent;
   allies receive exactly what the sharing rules allow; every hostile packet (teleport to an
   entity, attack as a spectator, a move out of bounds) is refused; every negative test has its
   positive control.
3. S11 (NET-04): ≤ 100 KB/s per client over the stated windows, with four mock players and 600
   units.
4. L6 (PERF-10): 4 × 150 population with four mock players for 20 minutes within A–E for every
   client; the 2× stress pass holds.
5. A `-Pcompat` run of the movement, fog-gate and hardening GameTests passes, and the inventory
   lists both new mixins.

**Acceptance** (owner): a session on a dedicated server with two clients, a reconnect included, by
QA-09's protocol and questionnaire; no P0 or P1 bug open; the owner approves ART-03's look.

**Retires.** A-R9 (bandwidth); A-R13 (spectator powers); F-R20/A-R18 (QA-14, whenever it runs);
F-R18 for the first art set.

**Size and critical path.** 13 cards (1 S, 10 M, 2 L), at most 51 card-days. Critical path: MATCH-06
→ NET-02 → QA-15 → NET-09 (19 card-days).

---

## Decisions this milestone needs

### D-20 · Multiplayer hosting

| Option | For | Against | Card delta |
|---|---|---|---|
| Dedicated servers only | One code path; real movement checks | Friends need a server | – |
| Dedicated servers plus Open to LAN (recommended) | Easy play with friends | The host's client shares the JVM, which hides bugs; every test still runs on a dedicated server | – |
| Several matches per server | Community servers | Lobby and resource management | lobby work in NET-02 grows |

**Recommendation.** Dedicated servers first-class; Open to LAN supported, but every test runs on a
dedicated server; one match per server in M7, several later (the match container already allows
it). **Needed by:** M7 (NET-02). **Affects:** NET-02, NET-05, QA-15, MATCH-16.

---

## Cards

#### QA-14 · 26.x platform decision gate
- **Value:** Strategos stays on the Minecraft version players actually use.
- **Timing:** runs at the end of the MVP or in 2027-Q2, whichever comes first (ADR-0001), as a
  time-boxed spike on a branch; it may run beside M5 or M6. It is listed here because it is not MVP
  scope, and it is part of no Done gate.
- **In scope:** the gate of [02-tooling.md](../02-tooling.md): a time-boxed port spike of `mod` to
  the leading 26.x line; a check of the listed triggers; a decision to stay, port or keep two
  branches.
- **Out of scope:** the port itself.
- **Acceptance:**
  1. A spike report gives the measured port effort per area: rendering, GUI, input, persistence
     (R).
  2. An ADR records the decision and the next review date, approved by the owner (D).
  3. The spike branch builds and its GameTests pass, and the spike changed nothing in `core` or the
     report lists each change (L3, on the branch).
- **Tests:** L0, L3, R, D · **Depends on:** — · **Size:** M · **Tier:** T1
- **Status:** proposed

#### NET-02 · Match lobby on a dedicated server
- **Value:** Friends join one server and set up a match together.
- **In scope:** per D-20: a lobby in the arena world with slots (human, AI, closed), civilisation,
  team and colour; a ready check; the host or an operator starts; joining a running match is
  refused (spectators come with NET-07).
- **Out of scope:** matchmaking across servers.
- **Acceptance:**
  1. With four mock players, slots, teams and ready states sync to everyone (L3).
  2. Starting puts every mock player into the commander state, each receiving `match_join` within
     10 s (L3).
  3. Players outside the lobby can't issue match commands (L3).
  4. Lobby goldens (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent), L5 · **Depends on:** MATCH-02, MATCH-06, NET-01, D-20 ·
  **Size:** L · **Tier:** T1
- **Status:** proposed

#### NET-03 · Per-player and per-faction state through the gateway
- **Value:** Each player sees their own economy and queues, allies see what allies should, and
  nothing else leaks.
- **In scope:** player and faction audiences from `ReplicationService`; treasury, queues and
  research to their owner only; team-shared pings and markers. Strategos relies on no attachment
  sync for match state: every per-player or per-faction datum leaves through `NetGateway`.
- **Out of scope:** vision (FOW-02).
- **Acceptance:**
  1. A mock opponent never receives another player's treasury, queues or research, while their
     owner does (L3).
  2. Allies receive the shared data of the rules table and nothing more (L3).
  3. No Strategos attachment type syncs to clients (L2, inventory).
- **Tests:** L0, L2, L3 · **Depends on:** NET-08, MATCH-06 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### NET-04 · Spike S11: bandwidth and per-channel byte budgets
- **Value:** Smooth games over home internet with whole armies on the move.
- **In scope:** S11: four mock players counted by QA-19 (and two real clients with consent), 600
  units moving and fighting, a commander pan at 40 blocks per second with terrain bytes reported
  separately, a skirmish on a fog edge and a construction storm; per-channel byte budgets per tick
  that defer low-priority items; average, peak and per-tick windows stated; the army channel is
  NET-08's `unit_summary`; streamed tier-4 entity types only if S11 fails, chosen per match at its
  start.
- **Out of scope:** fog filtering itself (FOW-02).
- **Acceptance:**
  1. Each client stays ≤ 100 KB/s averaged over the scene, with peak and per-tick figures
     reported (L6).
  2. A channel over its per-tick budget defers low-priority items without losing state (L1).
  3. Two real clients confirm the mock counts (R, consent).
  4. The report decides on the tier-4 reserve (R).
- **Tests:** L1, L6, R · **Depends on:** NET-03, QA-19, PERF-01 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### NET-05 · Reconnect and rejoin
- **Value:** A dropped connection doesn't end your game.
- **In scope:** identity by UUID; a disconnected player's units finish their orders, then idle;
  rejoining restores control, with camera pose and groups restored on the client; after a
  server-set timeout the faction is defeated or handed to the AI.
- **Out of scope:** joining as a new player mid-match.
- **Acceptance:**
  1. A mock player who disconnects and returns within 5 minutes regains the same faction (L3).
  2. Units keep executing their orders while their owner is away (L3).
  3. After the timeout the faction is handled as configured (L3).
  4. In a real two-client session, a client that drops its connection rejoins mid-fight (L7,
     acceptance).
- **Tests:** L0, L3, L7 · **Depends on:** NET-02, CORE-07 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### NET-06 · Command validation hardening and fuzzing
- **Value:** A modified client cannot cheat with forged or flooded commands.
- **In scope:** fuzzing of every serverbound payload from a seeded corpus kept in the repository,
  with every crashing input kept as a regression fixture; field-level validation; rate limits; an
  audit log of rejections; a kick policy.
- **Out of scope:** anti-cheat beyond our own payloads (spectator powers are NET-09's).
- **Acceptance:**
  1. 100,000 fuzzed payloads decode without an exception (L2).
  2. Applied in a match, they change no state except through accepted commands (L3).
  3. Every rejection logs a reason code, and exceeding the rate limit triggers the configured
     policy (L3).
  4. The corpus and the crash fixtures run in every build (L2).
- **Tests:** L0, L2, L3 · **Fixtures:** fuzz corpus and crash inputs (append-only) ·
  **Depends on:** CORE-03, NET-02 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### NET-09 · Commander hardening against modified clients
- **Value:** A modified client cannot use the vanilla spectator's powers to scout, teleport or leave
  the arena.
- **In scope:** architecture §12.6, before any multiplayer play: `ServerGamePacketListenerImplMixin`
  drops `handleTeleportToEntityPacket` for match members; `ServerPlayerMixin` makes `attack` a no-op
  for commanders, and in `broadcastToPlayer` hides enemy commanders always and enemy first-person
  bodies standing in cells the viewer's faction doesn't see; `CommanderGuard` clamps commander
  bodies to the match space plus a margin after every accepted move (`PlayerTickEvent.Post`); each
  hook's inventory entry; a `-Pcompat` run.
- **Out of scope:** our own payloads (NET-06).
- **Acceptance:**
  1. A teleport-to-entity packet from a match member is dropped (L3).
  2. A commander's attack on a unit never changes its camera (L3).
  3. Enemy commanders are never paired with each other, and enemy first-person bodies in fog are
     never paired, each test with a positive control (L3).
  4. A commander body outside the space plus margin is moved back within one tick (L3).
  5. The inventory lists both mixins, and the movement and fog GameTests pass under `-Pcompat`
     (L2).
- **Tests:** L0, L2, L3, L5 · **Depends on:** NET-02, QA-15 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### MATCH-06 · Teams, diplomacy and shared control
- **Value:** Play with friends against others, or two humans in one faction: one commands from the
  camera while the other fights or scouts in first person, which only Strategos can offer.
- **In scope:** our own faction and diplomacy registry (ally, enemy, locked teams); allied victory;
  no damage between allies; ally colours; two humans may control one faction, as in AoE II's co-op
  mode, through the core's "ally that granted control" validation (architecture §7.3); scoreboard
  teams only colour player names and never contain units.
- **Out of scope:** neutral relations and tribute (MATCH-11).
- **Acceptance:**
  1. Allied units never damage each other (L1).
  2. Allied victory ends the match when every enemy is defeated (L1).
  3. Two mock players controlling one faction both have their commands accepted (L3).
  4. No unit is ever added to a scoreboard team, while player names are coloured (L3).
- **Tests:** L0, L1, L3 · **Depends on:** MATCH-03, CORE-01 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### UI-15 · Multiplayer HUD and pings
- **Value:** Coordinate with allies: see who is playing and point at places on the map.
- **In scope:** a player list with colours, teams and status; pings (`strategos:ping`, 1 per 2 s)
  to allies on the minimap and in the world; diplomacy shown in the HUD.
- **Out of scope:** voice and text chat beyond vanilla.
- **Acceptance:**
  1. A ping reaches allies only, with a positive control (L3).
  2. Goldens (L4, consent).
  3. The owner's two-client session uses pings (L7, acceptance).
- **Tests:** L0, L1, L3, L4 (consent), L7 · **Depends on:** UI-20, NET-02 · **Size:** M ·
  **Tier:** T1
- **Status:** proposed

#### AI-06 · AI in team games
- **Value:** AI allies and opponents behave sensibly in team games.
- **In scope:** the AI respects alliances, helps defend an allied base when its army is idle, and
  times its waves with allied AIs.
- **Out of scope:** coordinating with human allies by pings.
- **Acceptance:**
  1. The AI never targets allies (L1).
  2. With an idle army, the AI joins the defence of an allied base within 30 s (L1).
  3. Allied AIs launch their waves within 60 s of each other (L1).
- **Tests:** L0, L1, L3 · **Depends on:** AI-05, MATCH-06 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### QA-15 · Multiplayer test kit
- **Value:** Multiplayer behaviour is tested on every change, not only in sessions with friends.
- **In scope:** built on QA-19: GameTest helpers for N acknowledging, byte-counting mock players
  with factions and commands; hostile-packet helpers for NET-09; QA-09's two-client protocol
  extended for teams and reconnects.
- **Out of scope:** load tests with real clients.
- **Acceptance:**
  1. A test with four mock players runs in under 30 s (L3).
  2. Captured payloads can be asserted per player (L3).
  3. The extended protocol is used at the M7 acceptance (L7, acceptance).
- **Tests:** L0, L3, L7 · **Depends on:** QA-19, NET-02 · **Size:** M · **Tier:** T1
- **Status:** proposed

#### PERF-10 · Multiplayer perf and bandwidth gate
- **Value:** Four-player matches stay smooth for every player.
- **In scope:** the M7 row of the budget matrix: 4 × 150 population, 120 buildings and four mock
  players for 20 minutes; A–E per client; the 2× stress pass; archived as the multiplayer
  baseline.
- **Out of scope:** fog cost (PERF-15).
- **Acceptance:**
  1. Budgets A–E hold for every client (L6).
  2. The stress pass holds C p95 ≤ 50 ms (L6).
  3. The report is archived as the multiplayer baseline (R).
- **Tests:** L6, R · **Depends on:** NET-04, QA-15, PERF-09 · **Size:** S · **Tier:** T1
- **Status:** proposed

### Beside M7

#### ART-03 · Civilisation 1 art pass
- **Value:** The first civilisation looks like a people, not a placeholder, while staying
  Minecraft (P2).
- **Timing:** starts after the M6 fun verdict, as the first card after the MVP gate, beside M7;
  tuning before that verdict would redo the art.
- **In scope:** per D-03 and D-04: unit skins and banners; blueprints for the starting set, the
  tower and the arsenal, with damage-stage block sets and a construction look, inside ART-01's
  style guide; icons for units, buildings, technologies and resources. Where the art needs the
  owner's hands or outside help, it is a named owner task.
- **Out of scope:** the second civilisation (ART-04).
- **Acceptance:**
  1. Every new blueprint passes the lint and the construction and damage-stage GameTests (L3).
  2. Every command-card entry has an icon (L2).
  3. Goldens at 60 blocks (L4, consent).
  4. The owner approves the look and finds the silhouettes distinct (L7, acceptance).
- **Tests:** L2, L3, L4 (consent), L7 · **Depends on:** QA-13, D-03, D-04 · **Size:** L ·
  **Tier:** T1
- **Status:** proposed
