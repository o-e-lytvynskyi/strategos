# M2 · Command

Feature cards of milestone M2. [ROADMAP.md](../ROADMAP.md) holds the overview, the conventions
and the status of every card (the source of truth); the statuses below mirror it.

## Milestone

**Goal.** Retire the camera and controls risk and give the owner the first fun: command armies
from an AoE-style RTS camera with box selection and mouse orders, in singleplayer and on a
dedicated server, drop into first person and back, and fight a first battle.

**The owner plays.** The battle sandbox: on the proving ground, on a dedicated server, the owner
box-selects 30 spearmen, sees where each will stand before releasing the right click, puts them on
control group 1, attack-moves into 30 enemy spearmen across the river and watches the health bars
decide the fight; then drops into first person, walks among the soldiers and returns.

**Order: the client track.** M2 cards start right after M0 on the owner-facing client track, beside
the assistant's headless M1 (ROADMAP.md, "Two tracks"). Each card starts when its dependencies
allow:
- right after M0: CAM-01 (S9), UI-01, UI-02, UNIT-03, then CAM-02, CAM-04, QA-18, PERF-14;
- after NET-01 (slice 1B): CAM-03, NET-08;
- after UNIT-03: UI-03, UI-04;
- after ORD-03: UI-05; after PATH-06: the battle sandbox (CMB-01, CMB-02, ORD-04, CMB-06,
  AUD-01), then CAM-05 and CAM-07.

M2 is Done only after M1 is Done, because its battles move M1's armies.

**Done gate** (automated and consent-batch items; plus the cumulative rule):
1. Every acceptance criterion of the M2 cards passes.
2. L1: rig, picking, selection, layout and slot-preview maths at every tested resolution, GUI scale
   and framebuffer scale.
3. L3: a mock commander drives `handleMovePlayer` at 8 blocks per tick for 12,000 ticks with 20
   jumps and receives no `ClientboundPlayerPositionPacket` except the jump teleports; bodies in
   newly sent chunks are paired within one tick of `ChunkWatchEvent.Sent`, and coverage is full
   within 3 s of a 300-block jump.
4. L6: the 2 × 100 melee soak holds budgets A–C for 5 minutes (CMB-02); `ChunkMap#move` stays
   ≤ 0.5 ms per panning commander per tick (CAM-03).
5. Consent batch (L4): goldens for camera poses, outlines, order markers, health bars and the HUD,
   approved by the owner, and the same scenarios once under `-Pcompat` (Sodium, Iris); PERF-14;
   M1's deferred consent items. Pending consent blocks Accepted, not Done.
6. ADR-0017 accepted with the S9 numbers, or its pre-agreed fallback adopted.

**Acceptance** (owner; decision gate 2):
1. On a dedicated server, 10 minutes of panning and 20 camera jumps show no snap-back and at most
   the known one-off echo warning per jump (L7).
2. The owner plays the battle sandbox and scores the questionnaire: "commanding a fight" at least
   3 of 5 and no score of 2 or less. A negative verdict on the controls means a UX iteration before
   M3.

**Retires.** F-R4/A-R6 (camera on a dedicated server); F-R13/A-R15 for the camera; F-R2/A-R8 in
part (PERF-14).

**Size and critical path.** 20 cards (4 S, 14 M, 2 L), at most 66 card-days. Critical path: CAM-01 →
CAM-04 → UI-05 → ORD-04 → CAM-07 (22 card-days). CAM-01 starts right after M0, so most of this path
runs beside M1.

---

## Decisions this milestone needs

D-08 and D-24 are presented together, as the avatar-and-vision bundle: in the final architecture a
first-person player is a vision source and an obstacle for units, so the avatar's rules and the
MVP's fog decide each other.

### D-08 · First-person avatar

**Question.** What the player is when dropping into first person.

| Option | For | Against | Card delta |
|---|---|---|---|
| Observer with rules (recommended) | Immersion without balance problems; no death or respawn | Cannot fight. Unrestricted, an invulnerable observer would be an unkillable scout once fog exists and a wall that can hold an enemy column at a gate, because first-person players are vision sources (architecture §10.2) and non-reactive agents units steer around (§9.5); hence the rules below | CAM-05, CAM-07 |
| Fighting avatar | Minecraft-native fun; fight beside your army | Balance, death and respawn rules; units must react to a player | + CMB-08 (M, M5) |
| Commander hero | Ties into heroes and auras | Needs heroes (CMB-14) | CMB-14 (M14) |
| Vanilla survival rules | Natural in an open world | Breaks a bounded arena's balance | MATCH-09 (M13) |

**Recommendation.** An observer in the MVP, with three rules: it adds no vision of its own (it sees
what its faction sees); units ignore it for avoidance and pass through it; it may stand only in
explored cells. A fighting avatar (CMB-08) keeps vision and collision, because it can die. The
commander hero comes with heroes; survival rules only in the open world.

**Needed by:** M2 (CAM-05). **Affects:** CAM-05, CAM-07, CMB-08, CMB-14, MATCH-09, PATH-06.

**Decided (2026-10-21):** as recommended, by the owner.

### D-24 · Exploration in the MVP

**Question.** The draft MVP plays on a revealed map, so nothing is ever explored. How much fog does
the MVP get?

| Option | For | Against | Card delta |
|---|---|---|---|
| (a) Revealed map | Smallest MVP | Scouting, surprise, finding livestock and tower vision mean nothing | none; fog waits for M8 |
| (b) Shroud only: unexplored terrain black, explored visible, units never hidden | Exploration without gating | Enemy units still visible everywhere | FOW-07 without gating in M6 (≈ +4 days) |
| (c) Solo-grade fog (recommended) | Exploration and hidden enemies for the solo player; the architecture built the seam for it | Fog arrives before multiplayer, so team vision and the leak audit wait for M8 | FOW-01, FOW-07, UI-16, AI-18, MATCH-07 in M6 (+9 card-days); a horseman scout in the start |
| (d) The full M8 set | Everything at once | Pulls a whole milestone into the MVP | (c) plus FOW-02, FOW-03, FOW-05, AI-07, PERF-15 (≈ +20 days) |

Option (c) in detail: per-faction vision (FOW-01) without team rules; explored state, a client
shroud drawn with vanilla render types, and enemy bodies gated by the existing `FogGate`, refreshed
through the public `ServerChunkCache#move(viewer)` (the architecture's fallback without access
transformers, cheap with one human viewer) (FOW-07); minimap fog (UI-16); an AI that reads only its
own vision and knows the start positions (AI-18); setup options revealed, explored or fogged
(MATCH-07). M8 keeps team vision, targeted re-tracking, mirages, the leak audit and AI scouting.
Feasibility §3.5 calls client-side fog acceptable for solo play.

**Recommendation.** (c). If declined, those five cards move back to M8.

**Needed by:** M6. **Affects:** FOW-01, FOW-07, UI-16, AI-18, MATCH-07, MAP-02, D-08.

**Decided (2026-10-21):** as recommended, by the owner.

### D-09 · Camera projection

**Question.** Perspective or orthographic RTS view.

| Option | For | Against | Card delta |
|---|---|---|---|
| Perspective at 35–70° pitch (ADR-0017) | No projection mixin, no frustum bypass; safest with Sodium and Iris | Less of the flat classic look | none |
| Orthographic | Classic RTS look; even scale across the screen | A projection mixin and a bypass of a frustum loop that otherwise freezes the game; untested with Sodium and Iris | CAM-06 instead of perspective |
| Perspective, orthographic as an option | Both audiences | Two paths to test | + CAM-06 (M, M10) |

**Recommendation.** Perspective through the MVP; the orthographic option (CAM-06, M10) only if the
owner wants it after the M2 playtest and it passes the Sodium and Iris checks. S9 tests
perspective only.

**Needed by:** M2 (CAM-02). **Affects:** CAM-02, CAM-06.

**Decided (2026-10-21):** as recommended, by the owner.

---

## Cards

### Camera and controls

#### CAM-01 · Spike S9: commander camera on a dedicated server
- **Value:** The RTS camera works the same in singleplayer and on dedicated servers, with no
  rubber-banding, and the decided design is proven before anything is built on it.
- **In scope:** confirms ADR-0017 against its exit criteria; time-boxed; runs right after M0 on the
  client track, with the owner's clients and consent. A dedicated server with two scripted clients
  (on QA-06's runner, joining by Quick Play) and about 1,000 tracked dummy bodies; ADR-0017's three
  body strategies: the player follows the camera (decided), a client camera entity with the server
  body teleported to the focus at most 5 times a second plus the chunk-sent re-pair (fallback 1), a
  body parked at the arena centre with a view distance of about 18 (fallback 2); pans at 10–60
  blocks per second and 300-block jumps; `ChunkMap#move` cost per panning commander; fill time and
  pairing after jumps; both cursor variants (`RtsScreen` and a released mouse); Retina picking; on
  a LAN and with a dev-only latency injector; under Sodium and Iris. Not tested:
  `ServerPlayer#setCamera` (it drags the player every tick and resets on Shift, the queue key),
  chunk tickets around the camera (tracking follows the body), orthographic projection (CAM-06).
  S4 runs here if PERF-04 waited for consent. Spike code is never promoted.
- **Out of scope:** the production camera (CAM-02 to CAM-04).
- **Acceptance:**
  1. Zero snap-backs while panning, with at most the known one-off echo warning per jump (L4,
     consent).
  2. Full coverage within 3 s after a 300-block jump (L4, consent).
  3. Picks within one block at GUI scale 2 on Retina (L4, consent).
  4. `ChunkMap#move` ≤ 0.5 ms per panning commander per tick, from server timers during the
     scripted run (L4, consent).
  5. The same scenarios pass under Sodium and Iris (L4, consent).
  6. The report confirms ADR-0017 or adopts the fallback its numbers choose, and sets the camera
     height limit (R).
- **Tests:** L4 (consent), R · **Depends on:** QA-06, QA-09, UNIT-01, D-22 · **Size:** L ·
  **Tier:** T0
- **Done:** 2026-10-23 — M2 T1, on the owner's Mac with consent ([S9](../spikes/S9-commander-camera.md)): no snap-back at 10–100 blocks a second in 3,500 ticks with 20 jumps, none at jumps once they teleport at the tick's start (1; spec §1.6 row 5); coverage 0.30 s after 300-block jumps, every body in range paired (2); picks 20 of 20 exact at GUI scale 2 on a 2× framebuffer, checked against the frame's pixels (3; row 8); `ChunkMap#move` 0.19–0.22 ms p99 per commander (4); the same under Sodium and Iris, where 8 blocks a tick snapped back, so pans cap at 5 (5; row 9); ADR-0017 Accepted with two amendments, neither fallback needed, height 8–64 (6)
- **Status:** done

#### UI-01 · Input action layer and key bindings
- **Value:** Every RTS control can be rebound in the standard Controls screen, with familiar RTS
  defaults that never clash with Minecraft's keys or with macOS.
- **In scope:** architecture §13.3: pure `Action` ids, `Binding`s and `ActionMap`s per context
  (RTS, placement, targeting, first person); `InputState` turning raw events into actions, with
  gestures (a double click within 300 ms, a drag of at least 4 GUI pixels); `KeyMapping`s
  registered in `RegisterKeyMappingsEvent` under an RTS-only `IKeyConflictContext` and matched by
  `RtsScreen` with `KeyMapping#matches`; held keys polled with `InputConstants.isKeyDown`; our own
  `KeyCode`; defaults modelled on the classics in a documented table; no default uses Ctrl + click,
  which macOS turns into a right click; handlers produce intents, never game logic.
- **Out of scope:** the full hotkey set (UI-17).
- **Acceptance:**
  1. Mapping, context resolution and gestures pass unit tests (L1).
  2. Our defaults collide with no key in a committed table of vanilla 1.21.1 defaults, and none
     uses Ctrl + click (L1).
  3. Every action can be rebound in vanilla Controls and works in RTS mode, and chat still opens
     (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Fixtures:** vanilla 1.21.1 default-key table ·
  **Depends on:** QA-01 · **Size:** M · **Tier:** T0
- **Done:** 2026-10-23 — M2 T3: `ActionMapTest`, `GestureTest` (1); `DefaultKeysTest` against `client/vanilla-keys-1.21.1.json`, generated from Options.java, and no Ctrl + click (2; spec §1.6 row 11); `rts_basics` on the owner's Mac: attack-move rebound to G through its `KeyMapping` fires on G, and chat opens from the RTS screen (3; rows 12 and 16)
- **Status:** done

#### CAM-02 · RTS camera rig
- **Value:** Survey and steer the battle from above as in the classics, with smooth pan, zoom and
  rotation that never leave the map (pillar P3).
- **In scope:** ADR-0017 and architecture §13.2: `core.client.CameraRig` (pure): focus, yaw, pitch
  35–70° (default 55°), height 8–64 blocks above the focus ground (default 24; S9 sets the final
  limit), FOV 55°; pan by keys, screen edge and drag; rotation by keys and middle drag; scroll
  zoom; terrain-following height; clamped to the match space plus a margin; smoothing that is
  independent of frame rate; position applied in `PlayerTickEvent.Pre` and angles in
  `ViewportEvent.ComputeCameraAngles`, with no camera mixin; view bobbing off; hotkeys that jump to
  the selection and to the civic centre through `camera_jump`; client options for speeds and edge
  pan; the projection of D-09.
- **Out of scope:** an orthographic view (CAM-06); the server side of jumps (CAM-03).
- **Acceptance:**
  1. The same input gives the same pose after 1 s at 30, 60 and 144 fps (L1).
  2. The view never extends beyond a fixed margin around the match space (L1).
  3. Goldens at fixed poses (L4, consent).
  4. The owner scores pan, zoom and rotation on the questionnaire (L7, acceptance).
- **Tests:** L0, L1, L4 (consent), L7 · **Depends on:** CAM-01, UI-01, D-09 · **Size:** L ·
  **Tier:** T0
- **Done:** 2026-10-23 — M2 T4: `CameraRig` in `core.client.camera` with the pan cap S9 set (5 blocks a tick, by bisection), `RtsMode` applies it in `PlayerTickEvent.Pre` and `ComputeCameraAngles` with no mixin, `strategos-client.toml` holds pan and turn speeds and edge pan; `CameraRigTest` (1, 2; spec §1.6 row 16); jumps through `camera_jump` since T9; pose goldens from `rts_goldens` on both platforms, proposed for the owner (3, T8; spec §1.6 row 33); the questionnaire waits for the owner's acceptance (4)
- **Status:** done

#### CAM-04 · RtsScreen, cursor and world picking
- **Value:** Point and click any unit or block as in a classic RTS, accurately on Retina, and no
  click ever breaks a block or attacks.
- **In scope:** ADR-0017: a non-pausing full-window `RtsScreen` that owns all input, so every
  vanilla binding is inert (attack, use, hotbar, inventory, drop, spectator menu, scroll to fly
  speed); it forwards F1, F2, F3 and F11, opens chat, turns Escape into "cancel targeting or
  placement, else pause", and reopens after chat or the pause menu close (`ScreenEvent.Closing`);
  `renderBackground` is a no-op and `isPauseScreen` false; picking with the
  `RenderLevelStageEvent` matrices and the cursor normalised by `Window#getScreenWidth` and
  `getScreenHeight`; blocks by `level.clip`, units by rays against the AABBs of the client unit
  index, buildings through the client footprint index; hover highlight.
- **Out of scope:** selection rules (UI-03).
- **Acceptance:**
  1. For known matrices and cursor positions, picking returns the expected block or unit at GUI
     scales 1–4 and framebuffer scales 1× and 2× (L1).
  2. F1, F2, F3 and F11 are forwarded, and Escape cancels before it pauses (L1).
  3. A scenario clicks 10 known targets through our API and hits all 10 (L4, consent).
  4. After chat and the pause menu close, the cursor stays free and no attack or use packet is
     ever sent (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** CAM-01, UI-01 · **Size:** M · **Tier:** T0
- **Done:** 2026-10-23 — M2 T5: `PickingTest` at GUI scales 1–4 and framebuffer scales 1× and 2×, framebuffer pixels shown to miss (1); `ScreenKeysTest` (2); `rts_basics` on the owner's Mac: 10 clicks through `RtsScreen` hit 10 gold blocks exactly (3); after chat and the pause menu the screen is back with the cursor free, and no attack, use or interaction reached the server (4); the hover highlight comes with UI-03 (spec §1.6 row 14)
- **Status:** done

#### UI-02 · HUD framework
- **Value:** A clean RTS screen that stays readable at any resolution and GUI scale.
- **In scope:** GUI layers registered with `RegisterGuiLayersEvent` that draw immutable
  view-models; `HudLayout.compute` in `core.client` (anchors, scale, safe area); the vanilla
  hotbar, crosshair, health, food, armour, air, experience and spectator layers cancelled in RTS
  mode through `RenderGuiLayerEvent.Pre`; theme tokens; tooltips; extraction timers.
- **Out of scope:** individual panels (UI-06 onward).
- **Acceptance:**
  1. Layouts at 1280 × 720, 1920 × 1080, 2560 × 1440 and 3456 × 2234 with GUI scales 1–4 have no
     overlaps and stay inside the safe area (L1).
  2. Building the HUD view-models and frame data for 200 selected units takes under 0.5 ms (L6,
     JMH); PERF-14 confirms budget F on screen.
  3. HUD goldens (L4, consent).
- **Tests:** L0, L1, L4 (consent), L6 · **Depends on:** UI-01, QA-06 · **Size:** M · **Tier:** T0
- **Done:** 2026-10-23 — M2 T6: `HudLayout` with `HudLayoutTest` at every listed window and GUI scale (1); `HudBench.hudFrame200`, 1.47 µs for 200 selected units, and PERF-14 with the HUD within budget F (2); the selection summary, group bar and tooltip in `rts_goldens`, proposed as goldens for the owner (3); M2 spec §1.6 rows 40–42
- **Status:** done

#### UNIT-03 · Unit renderer for the RTS view
- **Value:** Units read well from high above: visible far away, outlined when selected, moving
  smoothly.
- **In scope:** `UnitRenderer extends LivingEntityRenderer` with `HumanoidModel`, armour and
  held-item layers and a skin per template; client-side `isCurrentlyGlowing` and `getTeamColor` for
  the selection outline in faction colour, never scoreboard teams; `shouldRenderAtSqrDistance` at
  a fixed 192 blocks; a wider culling box for large units; model level of detail only if S4 asks.
  It depends on PERF-04's status, not on its result. Entity-type settings are UNIT-01's.
- **Out of scope:** the look (ART-01); far-unit markers (NET-08).
- **Acceptance:**
  1. No scoreboard team ever contains a unit (L3).
  2. A selected unit shows its outline in its faction's colour, and no other unit does (L4,
     consent).
  3. Units render out to 192 blocks at the highest camera position (L4, consent).
- **Tests:** L0, L3, L4 (consent) · **Depends on:** UNIT-01 · **Size:** M · **Tier:** T0
- **Progress:** 2026-10-23 — M2 T7: `bodytests.no_unit_on_a_team` (1); `rts_select` on the owner's Mac: the selected 150 glow in their faction's colour and no enemy does (2; spec §1.6 rows 17–18); render distance 192 set, its L4 check waits for NET-08's markers (3)
- **Status:** in_progress

#### QA-18 · Scenario diff, goldens and the CI golden job
- **Value:** Client changes are checked against approved pictures, on the Mac with consent or on
  CI without the owner.
- **In scope:** `scenarioDiff` (normalised mean squared error, masks); goldens per platform (macOS,
  linux-llvmpipe) with the owner-approval procedure; driving `RtsScreen` through our scenario API;
  the Linux container route if D-22 picks it; the golden step on CI (QA-08) if D-23 allows.
- **Out of scope:** frame-time gates (PERF-14).
- **Acceptance:**
  1. Three consecutive runs differ by less than the threshold, or the variance is masked and
     documented (L4, consent; three, not ten, since the owner's amendment of D-22 on 2026-10-22).
  2. A changed golden fails the CI job, with no window on the owner's Mac (L4, on CI).
  3. A scenario drives `RtsScreen` through our API (L4, consent).
  4. The README states the approval procedure, and goldens change only with the owner's approval
     (D).
- **Tests:** L0, L4 (consent), D · **Fixtures:** goldens per platform · **Depends on:** QA-06,
  CAM-04, D-22 · **Size:** M · **Tier:** T0
- **Done:** 2026-10-23 — M2 T8: `scenarioDiff` with masks and its selftest in `verifyHarness`; the
  scenario `rts_goldens` driving `RtsScreen` through its keys, wheel and mouse (3); three runs on
  this Mac and two on CI's llvmpipe identical to the pixel against the threshold of 1e-5 (1); the CI
  job fails a copy of the goldens with a square inverted, 3.2e-3 to 3.7e-3 (2); the approval
  procedure in `mod/src/devtools/README.md`, and the goldens proposed for both platforms wait for
  the owner (4); M2 spec §1.6 rows 32–37
- **Status:** done

#### CAM-03 · Commander body, jumps and the chunk-sent re-pair
- **Value:** Wherever the camera looks, terrain and units are loaded and current, in singleplayer
  and on servers.
- **In scope:** ADR-0017 and architecture §10.4 and §13.2: the commander is a vanilla spectator at
  the camera eye; pans capped at 8 blocks per tick, below the dedicated server's limit; every jump
  (minimap, control groups, alerts) goes through `camera_jump` and is answered by a server
  teleport, in singleplayer too; `camera_jump` limited to 4 per second, inside the match space plus
  32 blocks, commanders only; the chunk-sent re-pair (`ChunkWatchEvent.Sent` marks the viewer, and
  `ServerChunkCache#move(viewer)` runs once on the next tick); timers around our between-tick
  handlers (budget C); view-distance guidance for servers; the player's normal state restored on
  leaving the match. Arena worlds already turn `spectatorsGenerateChunks` off (MAP-07).
- **Out of scope:** first person (CAM-05); hardening against modified clients (NET-09).
- **Acceptance:**
  1. A `camera_jump` moves the body within one tick of receipt, and jumps beyond the limits are
     refused (L3).
  2. A mock commander driving `handleMovePlayer` at 8 blocks per tick for 12,000 ticks with 20
     jumps receives no `ClientboundPlayerPositionPacket` except the jump teleports (L3).
  3. After a 300-block jump, the bodies in each newly sent chunk are paired within one tick of
     `ChunkWatchEvent.Sent`, and the view is fully covered within 3 s (L3, QA-19's acknowledging
     mock).
  4. Units never target, block or collide with the commander body (L3).
  5. `ChunkMap#move` stays ≤ 0.5 ms per panning commander per tick and is counted in budget C
     (L6).
- **Tests:** L0, L1, L3, L5, L6 · **Depends on:** CAM-01, CORE-03, NET-01, QA-19 · **Size:** M ·
  **Tier:** T0
- **Done:** 2026-10-23 — M2 T9: `cameratests.jump_moves_body_and_limits` (1); `cameratests.pan_no_snap_back`, 12,000 ticks at 5 blocks a tick with 20 jumps, 20 position packets (2); `cameratests.chunk_sent_repair`, every body within a tick of its chunk after a 470-block jump, coverage within 3 s, and failing with the re-pair off (3); `cameratests.units_ignore_commander` (4); `perftests.commander_pan_move_cost`, 0.10 ms at p99 with 600 units (5); the commander's own state back when the match ends, kept across restarts (spec §1.6 rows 25–27)
- **Status:** done

#### NET-08 · Tier-2 replication: summaries, far markers and details
- **Value:** Armies never vanish when the camera zooms out, and panels show exact orders and
  stats, while nothing hidden ever reaches a client.
- **In scope:** architecture §12.2–12.4: `ReplicationService` in core, outside the hashed state,
  with per-faction interest (own units in full, enemy units only while visible, observers
  everything); `unit_summary` at 2 Hz as full frames, about 6 B per unit; far-unit markers drawn
  for units known from summaries but beyond tracking range; `ui_query` (≤ 32 handles, 4 per
  second) and `unit_detail` (orders, carried load, cooldowns, effective stats; ≤ 10 Hz);
  `unit_hud` (HP fraction and state bits; changes only, ≤ 5 Hz); `ClientMatchModel` in
  `core.client`; each payload with an L2 round trip, a cap and an audience declaration.
- **Out of scope:** per-recipient delta frames (reserve); per-channel byte budgets (NET-04).
- **Acceptance:**
  1. A summary frame costs about 6 B per unit, and 600 units fit its cap (L1).
  2. No frame addressed to a faction contains a handle hidden from it (L1).
  3. A mock player receives summaries at 2 Hz for its own units and visible enemies only, with a
     positive control in the same setup (L3).
  4. `unit_detail` answers a `ui_query` for own units only (L3).
  5. Every unit in view is drawn as a body inside tracking range or as a marker beyond it, at the
     highest camera position (L4, consent).
- **Tests:** L0, L1, L2, L3, L4 (consent), L5 · **Depends on:** NET-01, UNIT-03 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### UI-03 · Selection
- **Value:** Grab exactly the units you want, anywhere on the map: click, drag a box, Shift to add,
  double-click for all of a type (P3).
- **In scope:** architecture §13.4: click select; box select of own units by projecting feet and
  heads (`core.client.BoxSelect`; buildings only when no unit is inside the box); Shift adds and
  removes; a double click selects every unit of that template on screen; a select-all-of-type
  hotkey; enemies can be inspected but never mixed into a selection; at most 300 units; the
  selection lives on the client, and the server checks ownership on every command.
- **Out of scope:** control groups (UI-04); the selection panel (UI-06).
- **Acceptance:**
  1. Box selection over projected positions returns the expected handles in stable order for given
     poses, partly visible units included (L1).
  2. An enemy can be inspected but never joins a selection, and a selection never exceeds 300
     (L1).
  3. A scripted drag selects 150 units, and a double click selects all of that type on screen (L4,
     consent).
  4. The outline shows on selected units only (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** CAM-04, UI-02, UNIT-03 · **Size:** M ·
  **Tier:** T0
- **Done:** 2026-10-23 — M2 T11: `BoxSelectTest`, feet or head inside, handle order, partly visible units (1); `SelectionRulesTest`: enemies inspected never selected, at most 300, Shift toggles (2); `rts_select` on the owner's Mac: a drag selects 150, a double click takes all 150 of the type (3), the outline only on the selected (4; spec §1.6 row 20)
- **Status:** done

#### UI-04 · Control groups
- **Value:** Keep armies on number keys and recall them instantly (P3).
- **In scope:** Ctrl+number assigns, the number selects, Shift+number adds, a double tap centres the
  camera through `camera_jump`; dead units pruned; groups kept on the client for the session.
- **Out of scope:** saving groups with the match (they stay client-side; MATCH-04).
- **Acceptance:**
  1. Group operations pass unit tests (L1).
  2. Dead or removed units leave their groups (L1).
  3. Groups survive the first-person round trip in the client model (L1).
  4. The same round trip on screen keeps the groups (L4, consent).
- **Tests:** L0, L1, L4 (consent) · **Depends on:** UI-03 · **Size:** S · **Tier:** T0
- **Done:** 2026-10-23 — M2 T12: `ControlGroupsTest` (1–3); `rts_select`: Ctrl+1 then 1 recalls 150, a double tap centres through `camera_jump`; `rts_first_person`: group 1 and the selection survive first person and back on screen (4; spec §1.6 rows 19, 46)
- **Status:** done

#### UI-05 · Order feedback and destination preview
- **Value:** Before releasing the click you see exactly where each unit will stand; after it,
  markers and lines show where everyone is going (P3).
- **In scope:** right-click issues the context order; holding the right button previews ORD-03's
  slots, computed by the same core slot-layout code on the client, against a terrain view built
  from loaded chunks with the same `BlockClassTable`; dragging sets facing and width; waypoint
  markers and path lines for the selection; acknowledgement feedback (AUD-01's sound, a particle).
- **Out of scope:** the context resolver itself (ORD-02); attack markers (ORD-04).
- **Acceptance:**
  1. The client and server slot functions give equal slots for equal inputs (L1).
  2. On the proving ground, the server's slots equal the client's prediction, within a documented
     tolerance where the client lacks reachability data (L3).
  3. Each click sends exactly one order command (L1).
  4. Shift applies `QUEUE` on the server (L3).
  5. Markers appear at the clicked point and at queued waypoints (L4, consent).
  6. The owner finds the preview readable at every camera height (L7, acceptance).
- **Tests:** L0, L1, L3, L4 (consent), L7 · **Depends on:** ORD-03, UI-02, CAM-04 · **Size:** M ·
  **Tier:** T0
- **Done:** 2026-10-23 — M2 T13: `SlotPreviewParityTest`, equal places without terrain (1) and within the 3-column snap on the proving ground, in L1 (2; spec §1.6 rows 21–22); `OrderIntentTest`, one Move per click (3), Shift queues (4), and on the server all 150 units of `rts_select` hold the order and the queued one; the preview's places and the markers on the owner's Mac (5; row 23); facing and width by dragging, and path lines, come later (row 21); the owner's verdict is the questionnaire's (6)
- **Status:** done

#### CAM-05 · First person and back, with observer rules
- **Value:** Step down into your town as a normal player, walk among your soldiers and return to
  the RTS view, without gaining an unfair scout or wall (P2).
- **In scope:** architecture §13.7: the toggle sends `mode_switch` (1 per second); the server checks
  the match rule, a 5 s cooldown, and that the drop point is inside the match space, explored and
  standable; adventure mode through `ServerPlayer#setGameMode` and a teleport; back to spectator
  with the rig above the anchor; `RtsScreen` closes and reopens; units keep their orders; the D-08
  observer rules: no vision of its own, units ignore it for avoidance and pass through it, it
  stands only in explored cells (moved back otherwise), it cannot be hurt, attack
  (`AttackEntityEvent` cancelled for match bodies) or edit blocks; one first-person order, "move
  the selection to where I look".
- **Out of scope:** further first-person orders (CAM-07); a fighting avatar (CMB-08).
- **Acceptance:**
  1. Toggling applies the avatar rules and restores the commander body on return (L3).
  2. A drop point outside the space, unexplored or not standable is refused, and the cooldown
     holds (L3).
  3. Selection and control groups survive the round trip in the client model (L1).
  4. An observer standing in a 1-block gap delays an enemy column by less than 1 s (L3).
  5. An observer's agent record is never a vision source (L1).
  6. Switching back takes under 0.5 s, and two minutes of walking among units show no desync (L7,
     acceptance).
- **Tests:** L0, L1, L3, L4 (consent), L7 · **Depends on:** CAM-03, D-08 · **Size:** M ·
  **Tier:** T0
- **Done:** 2026-10-23 — M2 T19: `firstpersontests.to_first_person_and_back` (1, 5: no observer is an avoidance agent, so it is no vision source by itself either); `firstpersontests.drop_points_and_cooldown` (2); the selection and groups through the round trip in the client model and on screen, `rts_first_person` (3); `firstpersontests.observer_in_a_gap_delays_little`, 113 ticks with the observer in the gap against 116 without (4); the way back 0.1 s on this Mac, the owner's two minutes among units waiting for the acceptance (6); M2 spec §1.6 rows 44–46
- **Status:** done

#### CAM-07 · First-person orders: follow me, attack what I look at, inspect
- **Value:** First person has a purpose: lead the selection, point at a target and inspect what
  you look at (P2).
- **In scope:** "follow me": the selection follows the first-person agent, keeping slots behind
  it; "attack what I look at": an Attack command on the unit or building under the crosshair, from
  a ray through the first-person view; inspecting units and buildings by looking at them
  (`ui_query`). Ringing the town bell from first person comes with the bell (BLD-16).
- **Out of scope:** fighting yourself (CMB-08).
- **Acceptance:**
  1. Each action sends exactly one command (L1).
  2. The crosshair ray picks the expected unit or building (L1).
  3. Followers keep their slots behind a walking mock player and stop when it stops (L3).
  4. Inspecting shows the looked-at unit's details (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** CAM-05, ORD-04, NET-08 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

### Battle sandbox

#### CMB-01 · Damage model in the core
- **Value:** Combat outcomes are predictable and readable: damage types and armour matter, and no
  hit is ever silently lost (P4).
- **In scope:** ADR-0011 and architecture §6.7: damage types hack, pierce and crush (fire for
  torches comes with CMB-03); resistance 0.9^level per type; bonus multipliers by class token;
  unit-against-unit damage resolved entirely in the core: `LivingEntity#hurt` is never called for
  it, and HP is never mirrored into vanilla health; trackers get feedback through
  `ServerLevel#broadcastDamageEvent` (the red flash) and `LivingEntity#swing`, and HP through
  `unit_hud`; at death the adapter sets vanilla health to 0, calls `die()` and drives
  `tickDeath()`; vanilla sources hitting units (player melee to hack, arrows to pierce, explosions
  to crush, `/kill` to lethal) routed by the `hurt` and `knockback` overrides into journaled
  `ExternalDamage` and `ExternalImpulse` inputs; vanilla armour, shields and totems never apply to
  units; no vanilla loot.
- **Out of scope:** projectiles (CMB-04); damage to players and mobs (CMB-08); capture (CMB-10).
- **Acceptance:**
  1. Formula tables for types, levels and bonuses pass (L1).
  2. Twenty attackers focusing one target deal exactly 100 % of the summed formula damage (L1).
  3. Vanilla `hurt` is never called for unit-on-unit hits, and vanilla health stays full until
     death (L3).
  4. Player melee, an arrow, an explosion and `/kill` reach the core as hack, pierce, crush and
     lethal damage (L3).
  5. A dead unit runs `die()` and `tickDeath()`, its record and body are gone, and its owner is
     notified within one tick (L3).
- **Tests:** L0, L1, L3, L5 · **Depends on:** DATA-02, UNIT-02 · **Size:** M · **Tier:** T0
- **Status:** specified

#### CMB-02 · Melee combat with a default aggressive behaviour
- **Value:** Soldiers close in, strike on a steady rhythm, chase a fleeing target and defend
  themselves without being told (P3, P4).
- **In scope:** the COMBAT states (approaching, attacking, chasing, finding a new target);
  wind-up and repeat in ticks; reach; engagement slots around a target; the default aggressive
  behaviour: units retaliate when attacked, and idle units attack enemies that come into range
  (target scan by activity tier); the 2 × 100 melee soak of the M2 gate. Other stances are
  CMB-05's.
- **Out of scope:** stances, leashes and fleeing (CMB-05).
- **Acceptance:**
  1. Hits land at the wind-up tick and repeat at the exact period (L1).
  2. Ten attackers take distinct slots around one target and all reach it (L1).
  3. A melee unit chases a fleeing target until its order ends or the target dies (L1).
  4. An idle unit attacks an enemy entering its range, and an attacked unit retaliates (L1).
  5. A GameTest fight between two squads ends with the survivors the L1 run predicts (L3).
  6. A 2 × 100 melee soak holds budgets A–C for 5 minutes (L6).
- **Tests:** L0, L1, L3, L6 · **Depends on:** CMB-01, PATH-06 · **Size:** M · **Tier:** T0
- **Status:** specified

#### ORD-04 · Attack and attack-move
- **Value:** Send armies to fight the way RTS players expect: attack a target, or attack-move
  through an area and fight whatever is met on the way (P3).
- **In scope:** the Attack order (right-click on an enemy unit) and attack-move
  (`WALKING_AND_FIGHTING`, a hotkey plus a click); context-resolver additions; markers for both.
  Attacking buildings and the torch switch are CMB-03's.
- **Out of scope:** focus fire, spread and force attack (ORD-05).
- **Acceptance:**
  1. Right-click on an enemy issues Attack; on the ground it issues Move (L1).
  2. Attack-moving units engage enemies met on the way, then continue to the point (L1).
  3. A GameTest attack-move through an enemy squad behaves as in L1 (L3).
  4. Markers for attack and attack-move (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent) · **Depends on:** ORD-02, CMB-02, UI-05 · **Size:** M ·
  **Tier:** T0
- **Status:** specified

#### CMB-06 · Health bars, hit flash and death
- **Value:** You see who is winning a fight at a glance (P2).
- **In scope:** health bars for selected and damaged units from `unit_hud`, drawn in the
  extract-then-draw overlay; the vanilla hit flash and death animation from CMB-01's feedback.
  Attack lines and the under-attack flash are CMB-17's (M5).
- **Out of scope:** alerts (UI-12).
- **Acceptance:**
  1. Health-bar data reaches only players allowed to see the unit, with a positive control (L3).
  2. Extracting bars for 300 units takes under 0.5 ms (L6, JMH); PERF-14 confirms budget F.
  3. Goldens of health bars at three camera heights (L4, consent).
- **Tests:** L0, L1, L3, L4 (consent), L6 · **Depends on:** CMB-01, UI-02, NET-08 · **Size:** S ·
  **Tier:** T0
- **Status:** specified

#### AUD-01 · Order, hit and death sounds
- **Value:** Orders and fights sound right, so you notice action off-screen (P2).
- **In scope:** vanilla sound events for order acknowledgements, hits and deaths, sent only as `fx`
  events with spatial audiences (the unit's trackers, or players whose faction sees the cell) and
  played client-side with `ClientLevel#playLocalSound`; never a server-side `playSound`;
  attenuation that suits the camera height; volume categories; the `fx` payload with its cap and
  audience declaration. Bow-shot sounds come with CMB-04.
- **Out of scope:** alert sounds (UI-12, UI-21); music (AUD-03).
- **Acceptance:**
  1. Each order and combat event maps to its sound (L1).
  2. Unit bodies never play server-side sounds: `PlayLevelSoundEvent.AtEntity` never fires for
     them (L3).
  3. A viewer outside the audience receives no `fx`, while one inside does (L3).
  4. The owner's checklist passes (L7, acceptance).
- **Tests:** L0, L1, L2, L3, L7 · **Depends on:** ORD-02, CMB-01, NET-01 · **Size:** S ·
  **Tier:** T0
- **Status:** specified

#### PERF-14 · Client frame gate (consent)
- **Value:** The client stays smooth as features grow, measured on the owner's Mac instead of
  assumed.
- **In scope:** a scripted client scene with 200 units on screen at render distance 16, Sodium on
  and off; frame p50 and p99, our drawing, unit drawing and client tick; archived per machine;
  re-run with consent at the M3, M5, M6, M8, M9, M10 and M12 exits with that milestone's scene. CI
  with llvmpipe cannot check absolute frame times.
- **Out of scope:** server budgets (the perf gates of each milestone).
- **Acceptance:**
  1. Frame p50 ≤ 16.7 ms and p99 ≤ 25 ms, our drawing ≤ 3 ms, units ≤ 6 ms, client tick ≤ 1 ms
     (L6 client, consent).
  2. Results are archived per machine, and the owner approves the baselines (R).
- **Tests:** L6 (client, consent), R · **Depends on:** UI-02, UNIT-03, QA-06 · **Size:** S ·
  **Tier:** T0
- **Progress:** 2026-10-23 — M2 T21: the scenario `rts_frame` (2 × 100 units in the RTS view at
  render distance 16, walking, the own hundred outlined) and the archive `perf/client/m4pro.json`.
  On this Mac, medians of three runs: vanilla frame 2.59 / 5.73 ms (p50 / p99), units 1.36 ms, our
  drawing 0.53 ms, client tick 0.80 ms; Sodium and Iris 1.56 / 4.40 ms, 0.32, 0.60 and 0.81 ms (1,
  M2 spec §1.6 rows 28–30). The client tick is timed at vanilla's 120 frames a second. Open: the
  run with the HUD and health bars in M2's consent batch, and the owner's approval of the baselines
  (2)
- **Status:** in_progress
