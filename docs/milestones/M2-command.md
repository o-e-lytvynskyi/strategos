# M2 — Command: implementation spec

*Written 2026-10-23 by the assistant while M1 closed, from the 20 approved M2 cards in
[roadmap/M2-command.md](../roadmap/M2-command.md), [04-architecture.md](../04-architecture.md)
(cited as "04 §n"), the ADRs in [adr/](../adr/README.md), chiefly
[ADR-0017](../adr/0017-camera-and-input.md), and M1's spec and code
([M1-movement.md](M1-movement.md), cited as "M1 §n"). Status: **specified**. The decisions M2
needs, D-08, D-09 and D-24, were taken as recommended on 2026-10-21. Unlike M1's spec, this one
cites the engine lines that the architecture and ADR-0017 already verified; every other Minecraft
or NeoForge name is checked in `build/mc-src` by the task that first uses it, and recorded in §1.6
when it differs (§0.2). Clean-room as in M1: our documents, our code, decompiled vanilla and
NeoForge only.*

---

## 0. Read this first

### 0.1 Authority

As M1 §0.1: the architecture and the ADRs decide *what*, this spec *how M2 builds it*; the cards
define scope and acceptance; ROADMAP.md holds the statuses; a disagreement is recorded in §1.6.
M1's code is the starting point.

### 0.2 Working rules for every task

M1 §0.2 holds, with these changes:
- **Engine names** are verified by the task that first uses them, in `build/mc-src` (vanilla and
  NeoForge) or with `javap` (FML, the event bus), before code relies on them; a name that differs
  from this spec is a §1.6 row.
- **Client windows.** M2 is the client track. `runScenario` and a client joining the spike server
  open a window and take keyboard focus: they run only with the owner's consent. The owner gave it
  for 2026-10-23 («мак до вечера в твоем распоряжении»); outside such a grant, L4 items run on the
  Linux route on CI (`scenario.yml`, Xvfb, llvmpipe), and what needs this Mac (Retina picking,
  absolute frame times, the owner's goldens) waits in the consent batch (§1.7).
- **Runs** add `runSpike -Pspike=s9` and the client that joins it (§5); both end on their own.
- **Done** as M1 §0.4, with this spec's progress list (§7.3).

### 0.3 Code conventions

M1 §0.3 holds. Additions:

| Topic | Rule |
|---|---|
| Client maths | Camera, picking, box selection, HUD layout and slot preview are pure `core.client` classes, L1-tested; the mod's client package applies them and draws |
| Client never decides | The client sends intents as commands; the server checks ownership, ranges and rules for every one (04 §12.6) |
| Extract, then draw | Overlays and HUD build immutable frame data from the client model once per frame, then draw from it (04 §13.6) |
| Sounds and effects | Only as `fx` events to their audiences, played client-side; never a server-side `playSound` (AUD-01) |

### 0.4 How a task is finished

As M1 §0.4.

---

## 1. Goal, demo and scope

### 1.1 Goal

Command armies from an AoE-style RTS camera: box selection, mouse orders with a preview of where
each unit will stand, control groups, in singleplayer and on a dedicated server; drop into first
person and back; fight a first battle. M2 retires the camera risk (F-R4/A-R6) with spike S9.

### 1.2 Slices

| Slice | Content | Demo at its end |
|---|---|---|
| 2A camera | S9 (CAM-01), UI-01, CAM-02, CAM-04, CAM-03, UI-02, UNIT-03, QA-18 | Pan, rotate and zoom over the proving ground on a dedicated server; jumps refill the view within 3 s; clicks pick units and blocks |
| 2B commanding | NET-08, UI-03, UI-04, UI-05 | Box-select 150 units, see their slots under the held right button, put them on group 1, jump back to them |
| 2C battle | CMB-01, CMB-02, ORD-04, CMB-06, AUD-01 | 30 spearmen attack-move into 30 across the river; health bars, hits and deaths decide it |
| 2D first person | CAM-05, CAM-07 | Drop among the soldiers, lead them with "follow me", return |
| gates | PERF-14 (consent), the 2 × 100 melee soak, the consent batch | M2's Done gate |

### 1.3 Demo script (L7, the owner)

1. `scripts/playtest.sh` starts the dedicated test server and the owner's client (the owner runs
   it). `/strategos match create map strategos:proving_ground`, then `/strategos match join 1 0`.
2. The client enters RTS mode: the cursor is free, the camera above the west start.
3. Pan with WASD and the screen edge, rotate with comma and period or a middle drag, zoom with the wheel.
4. `/strategos spawn strategos:unit/infantry 30 0` and `... 30 1` across the river.
5. Drag a box round your 30, hold the right button on the far bank, see the slots, release.
6. Ctrl+1, pan away, double-tap 1: the camera jumps back.
7. Attack-move (Z + click) into the enemy; watch the bars, hear the hits.
8. V (first person) among the soldiers; "follow me"; V back.
9. The questionnaire (`docs/testing/playtest.md`).

### 1.4 Cards, tasks and checks (traceability)

| Card | Task | Checks (level) |
|---|---|---|
| CAM-01 | T1 | S9 report: snap-backs, coverage, picking, `ChunkMap#move`, Sodium and Iris (L4, consent; R) |
| UI-01 | T3 | 1 `ActionMapTest`, `GestureTest` (L1) · 2 `DefaultKeysTest` against `vanilla-keys-1.21.1.json` (L1) · 3 rebinding on screen (L4, consent) |
| CAM-02 | T4 | 1 `CameraRigTest.samePoseAtAnyFrameRate` (L1) · 2 `CameraRigTest.staysInsideTheMargin` (L1) · 3 pose goldens (L4) · 4 questionnaire (L7) |
| CAM-04 | T5 | 1 `PickingTest` at GUI 1–4 × framebuffer 1×, 2× (L1) · 2 `RtsKeysTest` (L1) · 3 10 scripted clicks (L4) · 4 no attack or use packet (L4) |
| UI-02 | T6 | 1 `HudLayoutTest` (L1) · 2 `HudBench` (L6) · 3 HUD goldens (L4) |
| UNIT-03 | T7 | 1 `bodytests.no_unit_on_a_team` (L3) · 2 outline (L4) · 3 192-block render (L4) |
| QA-18 | T8 | 1 three runs within threshold (L4) · 2 a changed golden fails on CI (L4, CI) · 3 a scenario drives `RtsScreen` (L4) · 4 README procedure (D) |
| CAM-03 | T9 | 1 `cameratests.jump_moves_body`, `cameratests.jump_limits` (L3) · 2 `cameratests.pan_12000_no_snapback` (L3) · 3 `cameratests.chunk_sent_repair` (L3) · 4 `cameratests.units_ignore_commander` (L3) · 5 `perftests.commander_pan_move_cost` (L6) |
| NET-08 | T10 | 1 `UnitSummaryCodecTest` (L1) · 2 `ReplicationInterestTest` (L1) · 3 `nettests.summaries_own_and_visible` (L3) · 4 `nettests.detail_own_only` (L3) · 5 markers beyond range (L4) |
| UI-03 | T11 | 1 `BoxSelectTest` (L1) · 2 `SelectionRulesTest` (L1) · 3 drag 150 and double click (L4) · 4 outline on selected only (L4) |
| UI-04 | T12 | 1–3 `ControlGroupsTest` (L1) · 4 round trip on screen (L4) |
| UI-05 | T13 | 1 `SlotPreviewTest` (L1) · 2 `ordertests.preview_matches_server` (L3) · 3 `OrderIntentTest` (L1) · 4 `ordertests.shift_queues` (L3) · 5 markers (L4) · 6 questionnaire (L7) |
| CMB-01 | T14 | 1 `DamageFormulaTest` (L1) · 2 `FocusFireTest` (L1) · 3 `combattests.no_vanilla_hurt` (L3) · 4 `combattests.vanilla_sources_routed` (L3) · 5 `combattests.death_cleanup` (L3) |
| CMB-02 | T15 | 1–4 `MeleeTest` (L1) · 5 `combattests.squad_fight_matches_l1` (L3) · 6 `perftests.melee_soak_2x100` (L6) |
| ORD-04 | T16 | 1 `ContextOrderTest` (L1) · 2 `AttackMoveTest` (L1) · 3 `combattests.attack_move_through_squad` (L3) · 4 markers (L4) |
| CMB-06 | T17 | 1 `nettests.unit_hud_audience` (L3) · 2 `HealthBarBench` (L6) · 3 bar goldens (L4) |
| AUD-01 | T18 | 1 `FxMappingTest` (L1) · 2 `combattests.no_server_sounds` (L3) · 3 `nettests.fx_audience` (L3) · 4 checklist (L7) |
| CAM-05 | T19 | 1 `cameratests.first_person_round_trip` (L3) · 2 `cameratests.drop_point_rules` (L3) · 3 `ClientModelRoundTripTest` (L1) · 4 `avoidtests.observer_in_gap` (L3) · 5 `ObserverVisionTest` (L1) · 6 the owner's round trip (L7) |
| CAM-07 | T20 | 1 `FirstPersonIntentTest` (L1) · 2 `CrosshairPickTest` (L1) · 3 `ordertests.follow_me` (L3) · 4 inspect (L4) |
| PERF-14 | T21 | 1 frame budgets on this Mac (L6 client, consent) · 2 the archive and the owner's baseline (R) |

T2 is the health check's split of two large classes (§7.2); T22 the consent batch; T23 the close.

### 1.5 Moved out of M2, with the reason

| Item | To | Why |
|---|---|---|
| A dev-only latency injector for S9 | S11 (NET-04, M7) | S9 runs server and client on one Mac; internet latency matters for the multiplayer bandwidth spike, not for whether the body strategy works. S9 records loopback only (§1.6 row 1) |
| Measuring both S9 fallbacks in full | Only if the decided body misses an exit criterion | ADR-0017 fixed the fallbacks in advance; S9 measures the decided strategy against every criterion and the fallbacks' costs only where the decision needs them (§1.6 row 2) |

### 1.6 Differences resolved

Rows written while specifying; tasks append theirs.

| # | Item | Resolution |
|---|---|---|
| 1 | CAM-01: "on a LAN and with a dev-only latency injector" | Server and client on this Mac over loopback, which is a LAN; the injector moves to S11 (§1.5) |
| 2 | CAM-01: "ADR-0017's three body strategies" | The decided strategy is measured against all five exit criteria; fallback 1 (a camera entity, the body teleported to the focus 5 times a second) and fallback 2 (a parked body) are measured only if it misses one. The pre-agreed fallbacks need no comparison when the decision passes |
| 3 | CAM-01: "two scripted clients" | One real client (the scenario runner, joining by Quick Play) and one server-side mock commander (QA-19's `MockViewer`, a spectator driven through `handleMovePlayer` like a client), so a single Gradle build runs the spike; the second commander measures the per-commander `ChunkMap#move` cost and the coverage with two viewers. Two real clients join if the runner can start a second one without a second build |
| 4 | Every L4 item | With a consent grant on this Mac, or on the Linux route on CI; goldens are per platform (QA-18), and only the owner approves them |
| 5 | S9, CAM-03: when a jump teleports | A teleport between ticks or in `ServerTickEvent.Post` draws two or three "moved too quickly" warnings a jump: each tick `ServerGamePacketListenerImpl#tick` takes the player's position as the first good one (ServerGamePacketListenerImpl.java:262), and the client's answer, which reaches the server in the gap between ticks, is measured against the old position. S9's first run counted 56 warnings in 20 jumps. A teleport in `ServerTickEvent.Pre`, before the connections tick, draws none (the third run: 0 in 20). CAM-03 queues each accepted `camera_jump` and teleports at the start of the next tick; the ADR's "one-off echo warning per jump" does not occur |
| 6 | S9: the client's view distance | The scenario harness's `options.txt` sets a render distance of 5, and the server tracks entities within the player's own view distance (`ChunkMap#getPlayerViewDistance`), so S9's client sets 10, a player's usual value, and its coverage check uses the player's distance |
| 7 | S9: a match without a map holds no tickets | Only arena and map matches force-load their space (M1 T4), so S9's flat-world match had bodies only where the two commanders loaded chunks: 600 to 1,000 of its 1,000 units at a time. Coverage counts the bodies that exist |
| 8 | S9: picking is checked against the rendered frame | For each of 20 gold blocks the client projects the block with the frame's own matrices, sets the OS cursor there with GLFW, reads it back in window points, unprojects it and clips; and the frame's pixel at that point, the window point times the framebuffer scale (2 on this Retina display), must be gold. A projection that used framebuffer sizes would miss by half the screen |
| 9 | CAM-02, CAM-03: the pan cap | 5 blocks a tick, not 8. The server lets a packet move `10 × √k` blocks from the tick's first good position for `k` packets since its last tick (ServerGamePacketListenerImpl.java:910–919); a client running two ticks in one frame sends two packets in one server tick, and at 8 a tick the second is 16 blocks away. Under Iris and Sodium that snapped back 89 and 4 of 200 packets at 8 a tick in S9's two runs, none at 5 or below; the vanilla client never bunched here. CAM-03.2's mock pans at 5. ADR-0017 and 04 §13.2 amended |
| 10 | T1: S9's results | The decided body passes: see [the report](../spikes/S9-commander-camera.md). ADR-0017 Accepted with rows 5 and 9; neither fallback measured (row 2); the height stays 8–64 |
| 11 | UI-01.2: what "collide" means, and the layout | A collision is what the Controls screen marks: the same key in contexts that are live together (NeoForge: `UNIVERSAL` conflicts with every context, `IN_GAME` and `GUI` only with themselves; Options.java:1557–1571 sets which vanilla keys are `IN_GAME`). The RTS keys live in a context of their own, active only while `RtsScreen` is open, so they may share vanilla's in-game keys and must avoid its universal ones (E, Q, F, P, L, C, X, the digits, F2, F11, right and middle click). Defaults: pan W A S D, rotate comma and period, zoom = and - and the wheel, attack-move Z, stop H, first person V, centre on the selection Space; RTS mode F6 in game. The table `client/vanilla-keys-1.21.1.json` is generated from Options.java; `DefaultKeysTest` checks the rule and that it catches Q |
| 12 | UI-01: what is not a key mapping | The mouse gestures (left selects, a drag boxes, a double click takes the type, right orders, middle drags to rotate), Escape, and the control groups on the digits, which vanilla binds to the hotbar in every context, are fixed until UI-17's hotkey screen; UI-01.3's "every action can be rebound" covers the key mappings. The L4 check rebinds attack-move to G through its `KeyMapping`, as the Controls screen does, and G fires it |
| 13 | CAM-02, CAM-04 before CAM-03 | Until CAM-03 makes a seated commander a spectator, RTS mode starts only for a player who already is one (in the scenarios, the integrated server sets it); jumps through `camera_jump` and the rig's limits from a seat come with CAM-03. Without a seat the limits are a 4,096-block square round the player |
| 14 | CAM-04: picking units, and the hover | Units are picked by rays against the boxes of the level's unit bodies within 192 blocks of the camera, a scan per click; NET-08's client unit index replaces it. The hover highlight comes with UI-03's selection outline, which draws it |
| 15 | §1.3: the demo's keys | Rotate on comma and period, attack-move on Z, first person on V, as row 11 sets; the script is amended |
| 16 | T3–T5: tests | `ActionMapTest` (3), `GestureTest` (4), `DefaultKeysTest` (3), `ScreenKeysTest` (2), `CameraRigTest` (5: the same pose at 30, 60 and 144 fps for keys and zoom, the same turn for a drag, the margin, the eye's 5-block step found by bisection, the terrain), `PickingTest` (3, with framebuffer pixels shown to miss). L4 on this Mac: `rts_basics` (the window's 1280 × 720 points on a 2× framebuffer): 10 clicks through the screen's own handlers on 10 gold blocks, all exact; W held for 20 ticks pans more than 10 blocks; chat and the pause menu close back to the RTS screen with the cursor free; no attack, use or interaction reaches the server; attack-move rebound to G fires on G. Two screenshots of the default pose wait for QA-18's goldens |
| 17 | UNIT-03: no name tags | Vanilla draws a name tag over every living entity within 64 blocks (`LivingEntityRenderer#shouldShowName`), so a crowd seen from above was a field of "Unit" labels; `UnitRenderer` shows none. Unit names come with the HUD's tooltips |
| 18 | UNIT-03, UI-03: the outline | The body's client-side glow (`isCurrentlyGlowing`, an instance flag the client's selection sets, so common code never references the client, B5) and `getTeamColor` by faction: blue, red, green, yellow, purple, orange, cyan, then white. Vanilla's outline effect renders the blue darker, a navy line; the consent batch's goldens decide the shade |
| 19 | UI-03, UI-04: pruning and centring before NET-08 and CAM-03 | The selection and groups drop dead units through `prune` (L1), which the client calls once NET-08 tells it of deaths; a unit out of range is not dead. Centring on the selection (Space) or a group (a double tap) pans the rig to it at the 5-block cap (`CameraRig#panTo`) until CAM-03's `camera_jump` makes long jumps a server teleport |
| 20 | T7, T11, T12: tests | `BoxSelectTest` (2), `SelectionRulesTest` (3), `ControlGroupsTest` (3), `CameraRigTest.aPanToAPointGlidesAtTheCapAndAKeyStopsIt`, `bodytests.no_unit_on_a_team`. L4 on this Mac, `rts_select`: seated in slot 0 beside 150 own units and 20 enemies, a drag over the window selects the 150, which glow while no enemy does; an empty click clears; a double click takes all 150 of the type; an enemy click inspects and selects nothing; Ctrl+1, then 1, recalls the 150. Open: UNIT-03.3 (render at 192 blocks, beyond the 160-block tracking range, so it needs NET-08's markers to show anything), UI-04.4 (the first-person round trip on screen, with CAM-05) |
| 21 | UI-05: what M2's preview is | The places the server's `SlotPlanner` lays out before it snaps them (`SlotLayout`, already in `core.shared`), facing from the selection's centroid to the cursor's target, the widest body's spacing, drawn as blue dust while the right button is held; the release sends one Move (Shift: `QUEUE`) through M1's `commands` payload, and a white marker stays at the point (longer for a queued one). Not yet: dragging to set facing and width (the protocol's Move has no facing or width; it comes with the formation work of ORD-05) and path lines for the selection (with NET-08's details) |
| 22 | UI-05.2: in L1, not L3 | `SlotPreviewParityTest` compares the preview with the core's own planner on the proving ground's terrain, in the core: every server place within 3√2 blocks, the snap spiral's reach, of a preview place; without terrain they are equal. The GameTest would add only the adapter, which the L4 check covers: all 150 units got an order near the click |
| 23 | UI-05: particles from the RTS camera | A particle further than 32 blocks from the camera is never made unless forced (LevelRenderer), and the RTS camera is always that far up, so the preview and markers are forced particles; the scenario harness's options set particles to minimal, which forced ones ignore too |
| 24 | T13: tests | `OrderIntentTest`, `SlotPreviewParityTest` (2), `GestureTest`'s right button (preview on press, order on release). L4 on this Mac, `rts_select` continued: group 1 recalled, the right button held over the ground shows 150 places in rows, its release sends exactly one command, and all 150 units hold an order there on the server; a Shift order queues a second behind it for all 150 |
| 25 | CAM-03: who becomes a commander | `/strategos match join` and a login with a seat make the player its match's commander: a spectator above the space's centre, its own game mode and place kept in its persistent data (`strategos.commander`), so they come back when the match ends (`MatchRegistry#onUnseated`) or at a login after it ended, even across a restart. A seat in another match keeps the first saved state. The network tests' seat helper seats without making a commander, so M1's tests are unchanged. Servers keep a view distance of 10 or more: S9 measured there |
| 26 | CAM-03: the jump's checks | The handler needs a seat; the service a commander (the saved state and spectator mode), at most 4 jumps in 20 ticks, and an eye within the space plus 32 blocks across and from 32 below its floor to 64 above its top; the last accepted jump of a tick wins, and it teleports in `ServerTickEvent.Pre` (row 5). The client centres by panning within 48 blocks and jumps beyond; a teleport it did not ask for (more than 16 blocks from the rig's eye) rebases the rig on the new eye |
| 27 | T9: tests and numbers | `cameratests.jump_moves_body_and_limits` (a jump moves the body the next tick; beyond the reach and a fifth in a second refused, an unseated player's goes nowhere); `cameratests.pan_no_snap_back` (12,000 ticks at 5 blocks a tick with 20 jumps: 20 position packets, all the jumps'); `cameratests.chunk_sent_repair` (a still commander after a 470-block jump: every body paired within a tick of its chunk, and with the re-pair switched off the test fails, a body unpaired two ticks after its chunk); `cameratests.units_ignore_commander`, the pan and re-pair tests in spaces 8,192 and 10,240 blocks east of their structures, since batches share the structures' place and the pan's loaded chunks kept `bodytests.bodies_follow_entity_ticking_chunks`' far chunk from unloading; `perftests.commander_pan_move_cost`: `ChunkMap#move` 0.05 ms p50 and 0.10 ms p99 per panning commander with 600 units (report-only, gate `c`). L4 on this Mac, `rts_select`: centring 100 blocks away jumps through `camera_jump`, and the rig takes over at the target. `CameraRigTest.aJumpsEyeRebasesTheFocus` |
| 28 | PERF-14: the scene | The scenario `rts_frame` (devtools) on the integrated server: 2 × 100 debug walkers in a 256² match, the player seated in slot 0 and in RTS mode at the rig's default pose (24 blocks up, pitch 55°) over the armies' centre, render distance 16, particles at All, the window 1280 × 720 points. Windows of 160 ticks with the frame limit off: no units (base), the units standing (idle), both armies walking back and forth across the view, the enemy the other way (moving), and the own hundred box-selected and outlined with a queued order's marker (rts). The units' cost is the frame interval's p50 in moving less base, our drawing rts less moving; the interval runs from one frame's start to the next, so it holds the GPU's time too. Loading settles 300 ticks first. The OS cursor rests mid-window every tick and every window fails if the camera's focus moved: in one early run the real cursor's edge pan took the camera off the armies and the rts window cost less than the moving one |
| 29 | PERF-14: the client tick at vanilla's frame limit | With no frame limit the GPU never idles, and vanilla's animated-texture upload in the client tick (`TextureManager#tick`, `glTexSubImage2D`) waits for it: the tick took 1.06 ms walking and 2.0–2.5 ms once the outline pass added GPU work, 1.6 ms with no units at all (JFR: the extra time is native, in the upload). At 60 frames a second the same windows took 0.67–0.77 ms. So the tick is timed in one more window after rts at vanilla's default limit, 120 frames a second (rtsCapped), and checked as it stands: a tick with no units is no baseline, since under the compat set it ran longer than with them (1.0 against 0.83 ms). Our own tick work is negligible: one of the 55 samples JFR took in the walking window's ticks was ours (`RtsMode#edgePan`); vanilla's `animateTick` and the bodies' ticks hold the rest. `-Pscenario.jfr=<settings>` records a scenario run |
| 30 | T21: numbers | Medians of three runs each on this Mac, every run within budget ([perf/client/m4pro.json](../../perf/client/m4pro.json)). Vanilla: frame 2.59 ms p50 and 5.73 ms p99, units 1.36 ms, our drawing 0.53 ms, client tick 0.80 ms p50 and 0.92 ms p99. The compat set (Sodium, Iris): frame 1.56 and 4.40 ms, units 0.32 ms, our drawing 0.60 ms, client tick 0.81 and 1.01 ms. Under Sodium the outline costs more than the bodies it outlines: vanilla's outline pass draws the selected hundred again. The HUD (UI-02) and health bars (CMB-06) do not draw yet, so the consent batch (T22) runs the scene again with them; the owner approves the baselines (PERF-14.2) |
| 31 | The server smoke and traded places | `runServerSmoke` failed once in `verify`: a unit idle 3.1 blocks from the place it had when every unit got its order, past the 2.5-block reach; four reruns passed. The smoke read each place once, but M1's trading rule (M1 spec §1.6 row 151) hands a stuck member a walking teammate's place and changes both orders. It now reads every unit's place each tick while its order lasts and keeps the last one after arrival |
| 32 | QA-18: the diff | `scenarioDiff` (a Groovy class in `mod/build.gradle`, as `FixtureCheck` is in the root build) runs after every `runScenario`. Frames are box-filtered to at most 640 pixels across, the size goldens are kept at (a Retina frame is 4 MB, a shrunk one about 300 KB), and compared by the normalised mean squared error over RGB outside the masks. The default threshold is 1e-5: in `rts_goldens` on this Mac, three runs differed by at most 5.7e-7 before the bodies' age was pinned (row 37) and not at all after, and the own twelve's outlines are 1.3e-4 of their frame, so losing them fails by 13 times. `checkScenarioDiffSelftest` joins `verifyHarness`: the same frame, sparse noise and a change under a mask pass; a changed body and a missing frame fail. Goldens live in `mod/src/devtools/goldens/`, not under `resources` as §4 said, since nothing loads them at run time; proposals wait in `goldens-proposed/` |
| 33 | QA-18: the scene and the procedure | `rts_goldens` builds a stone pad, a wall and two posts, spawns 12 own and 8 enemy units, freezes the game once they stand (`/tick freeze`) and drives the RTS screen only through its keys, wheel and mouse (QA-18.3): the default pose, the own twelve box-selected and outlined, a quarter turn right zoomed in to 10.4 blocks, and a quarter turn back at 64 blocks. It sets render distance 12, since at the harness's 5 the top pose showed the loaded world's edge in fog. The approval procedure is in `mod/src/devtools/README.md` (QA-18.4) |
| 34 | QA-18.2 on CI | The `scenario` workflow compares the run with the linux-llvmpipe goldens (it can point at `goldens-proposed` to try proposals before approval) and, where there are goldens, inverts a 64-pixel square in a copy of each (`tools/alter_golden.py`) and requires `scenarioDiff` against the copy to fail. Locally the inverted square gave 3.2e-3 to 3.6e-3 against 1e-5 |
| 35 | The consent batch's `-Pcompat` pass, early | With today's grant, `rts_basics`, `rts_select` and `rts_goldens` ran once under the compat set (Sodium, Iris without a shader pack), all green: 150 selected and outlined, the preview's 150 places, both orders, the jump. Sodium's frames differ from vanilla's by 1.1e-5 to 1.2e-5 over the whole frame (lighting), so a Sodium run is its own platform, `macos-sodium`, with no goldens of its own for now. S9 (T1) and PERF-14 (T21) had their compat runs already |
| 36 | The top pose shows the world's edge | At 64 blocks up, the top of the window shows the edge of the loaded terrain and the sky beyond it, at render distance 12 too (at the harness's 5, a ring of fog). S9 computed the farthest visible ground at the view's centre (about 2.6 times the height); the window's top corners reach further. Whether the top height should follow the render distance, the pitch steepen with height or a fog hide the edge is CAM-02's choice with the owner, before the pose goldens are approved |
| 37 | Slow frames and the shots | On CI's Xvfb and Mesa (llvmpipe 20.1, GL 4.5) the first `rts_goldens` frames showed no outline and the turned pose mid-turn: a shot asked for with `capture` is taken at the next frame, and at llvmpipe's frame rate the scenario had already cleared the selection and started the turn by then. The scenario now does nothing while a shot is pending. The bodies' idle arm swing follows their age, which differed by the ticks each had lived on the client before the freeze (8.6e-6 between two CI runs at the close pose), so the scenario pins every body's age and walk speed after the freeze; three runs on this Mac are then identical to the pixel |
| 38 | M1's QA-19.4, early in the consent batch | The spike `qa19` (`gametest/spike/PacketSentCheck`, with the client scenario `qa19`): on the spike server, a real client over loopback and a QA-19 mock viewer stand as spectators at one spot above 300 debug walkers, half of them walking, both at view distance 10. After 400 ticks the server records `minecraft.PacketSent` with its own JFR recording for 1,200 ticks, while the mock's counter starts from zero. JFR's total for the client's address in the play protocol was 719,780 bytes, the encoder's 719,736: 0.006 % apart against the 5 % allowed. Per type they agree to the packet but for 4 keep-alives and one teleport the real client had; a custom payload is `minecraft:custom_payload` to JFR and `payload/<id>` to the counter, with the same bytes. Neither viewer is seated, so no Strategos payload flowed; ours take the same encoder path. Data: [QA19-4-packet-sent.json](../spikes/data/QA19-4-packet-sent.json). Commands: `./gradlew :mod:runSpike -Pspike=qa19`, then `./gradlew :mod:runScenario -Pscenario=qa19 -Pscenario.timeoutSeconds=400` |
| 39 | M1's PATH-10.3: the overlay's goldens | `overlay_goldens` uses `rts_goldens`' scene in the RTS view: the overlay on through its client command, the GUI hidden (the panel holds timings, the toggles talk in the chat), the own twelve ordered round the stone pad, and the server freezing the game at a fixed match tick, 40 after the order (219 in every run), after the match stepped. The feed goes on sending the frozen state, so after 40 ticks the client holds every layer of it; the bodies are pinned to their interpolation's target, with age 0, no walk and the head turned with the body. Frames: every layer, and surfaces and components alone. Three runs on this Mac: the second frame identical to the pixel, the first within 1.7e-8. The proving ground itself needs an arena world, which the harness does not make; this scene has a raised pad, posts and a wall instead |
| 40 | UI-02: what M2's HUD draws | `HudLayout.compute` (in `core.client.hud`) places every part the later cards fill: the resource bar and notices at the top, the minimap, selection panel and command card at the bottom with the control-group bar above the panel, all inside a 4-pixel safe margin; the panel is centred and at most 360 GUI pixels wide. M2 draws three of them, from view-models rebuilt once a client tick after RTS mode's tick: the selection summary (`SelectionSummary`: the count and how many of each type, the largest first; with nothing selected, the inspected unit), the group bar (`GroupBar`: groups with members, 1–9 then 0) and the hover tooltip (the type's name and a swatch of its faction's colour; unit names were left to it in row 17). The theme's tokens are `HudTheme`. In RTS mode the vanilla hotbar, crosshair, health, food, armour, air, vehicle health, jump meter, experience, item name and spectator layers are cancelled in `RenderGuiLayerEvent.Pre`; chat, the debug screen and boss bars stay |
| 41 | UI-02: names and the hover | A template's `name` is a translation key (`strategos.unit.debug_walker`), so the HUD translates it and the language file has the two names. The hover follows the RTS screen's cursor, which `mouseMoved` reports on every real move: in `rts_goldens` the cursor put on a unit with `glfwSetCursorPos` never reached a tooltip through the mouse handler, while the screen's `mouseMoved` does, for a scenario as for a player. Picking split into `find`, without the side effect, and `pick`, which remembers the last pick for scenarios |
| 42 | T6: tests and numbers | `HudLayoutTest` (5: every part inside the safe area and apart at 1280 × 720 to 3456 × 2234 and GUI scales 1–4, the GUI size rounded up as the window rounds it, the panel centred and capped, tooltips inside the safe area at the corners) and `HudViewModelTest` (3) (UI-02.1). `HudBench.hudFrame200`: the layout, the summary of 200 selected units of four types and the bar over ten groups, 1.47 µs, against 0.5 ms (UI-02.2). `rts_goldens` now shows the panel and the group bar in its outline frame and has a tooltip frame, `hud_tooltip`: three runs on this Mac identical to the pixel, proposed as goldens (UI-02.3). PERF-14 again with the HUD, three runs each: vanilla frame 2.56 / 6.11 ms, units 1.34 ms, our drawing 0.54 ms, client tick 0.85 / 1.08 ms, the HUD's layers 0.028 ms a frame and its view-models 0.042 ms a tick; Sodium and Iris 1.77 / 4.51 ms, 0.33, 0.80, 0.86 / 1.11 ms, 0.014 and 0.056 ms ([perf/client/m4pro.json](../../perf/client/m4pro.json)) |
| 43 | The overlay's frames on llvmpipe | Two CI runs of `overlay_goldens` differed by 1.9e-5 (every layer) and 9.9e-6 (surfaces alone), in the bodies by the stone pad, where this Mac's three runs differ by at most 1.7e-8. Paths are delivered `STRICT`, so the server's frozen state does not depend on thread timing; the cause is on llvmpipe's client side and not found yet. The paths and markers are 3.1e-4 of their frame there (1.9e-4 on this Mac), so the linux-llvmpipe goldens of this scene take a threshold of 6e-5 in their `goldens.json`: three times the noise seen, a fifth of the layers' signal |
| 44 | CAM-05: the switch | V in the RTS view sends `mode_switch` (core `ModeSwitch`) with the camera's focus as the drop column; F6 in first person sends the way back (F6 is the RTS toggle, which already works in game). The server takes at most one request a second and none within 5 s (100 ticks) of the last switch; to first person only a commander, at the highest place in the match space's column and height band that has a sturdy top below and two blocks without a collision box or a fluid above, explored (every cell until fog, M6); back only an observer. The switch applies at the next tick's start, as jumps do: adventure mode and a teleport to the drop point, pitch level; back, spectator mode and the eye the rig's default height and reach behind where the observer stood, so RTS mode's focus lands on it. The observer flag lives in the commander's saved state, so a login starts in the RTS view. The client watches its game mode each tick: out of spectator mode in RTS mode it leaves the RTS view, keeping the selection and the groups; spectator again after first person, it enters it. A heightmap was the first drop rule; a GameTest arena's barrier roof put every column's top above the space, so the band is scanned instead |
| 45 | CAM-05: the observer's rules | `ObserverRules` cancels `LivingIncomingDamageEvent` for an observer, `AttackEntityEvent` on a match body, and `PlayerInteractEvent.RightClickBlock`, `LeftClickBlock` and `EntityInteract`, so it neither fights nor edits; adventure mode already keeps it from breaking and placing. It is no avoidance agent: `AgentTracker` leaves a match's own players out, so units pass through it and, when fog comes, it is nobody's vision source by itself (CAM-05.5 holds by construction until then). It stays inside the match space: one that walks out goes back to where it last stood inside. One first-person order, G (`ORDER_HERE`, in game): the selection moves to the block in view, up to 128 blocks |
| 46 | T19: tests and numbers | `firstpersontests.to_first_person_and_back` (adventure at the drop point's floor, no damage, an attack on a body cancelled, no agent; back a spectator 24 blocks above, commanding: CAM-05.1, .5), `firstpersontests.drop_points_and_cooldown` (outside the space, on water, a second request in a second and a way back in the cooldown refused, counted per player since the batch's tests run together: CAM-05.2), `firstpersontests.observer_in_a_gap_delays_little` (ten enemies through a one-block gap of a wall: 116 ticks without the observer, 113 with it in the gap: CAM-05.4). `WireCodecSizeTest.aModeSwitchRoundTripsAtItsSize`. L4 on this Mac, `rts_first_person`: the own twelve in group 1, V to first person in a tick with the selection and the group kept, G from first person gave all twelve the order, F6 back in 2 ticks (0.1 s against CAM-05.6's 0.5 s) with the selection and group 1 as they were (UI-04.4) |
| 47 | The HUD and F1 | The HUD's layers drew with the GUI hidden (F1), which `overlay_goldens`' CI frames showed; they now hide with vanilla's. With the fix, CI's overlay frames came back to 2.2e-5 of the proposals, from 4.8e-4 |
| 48 | T2: the health check | `StrategosCommands` (868 lines) keeps the root, the shared helpers and `spaceAround`; its subtrees moved to `MatchCommands` (match, journal), `MapCommands`, `UnitCommands` (spawn, order, debug), `TemplateCommands` and `InspectCommands` (nav, stats), the root adding them in the same order, so the tree is unchanged. The test kit's `Scenarios` (977 lines) keeps its factories and helpers; the scenarios moved to `OrderScenarios`, `MovementScenarios` and `CrowdScenarios`. No behaviour changed: `verify` green, the golden journals unchanged |
| 49 | The smoke's place reach, again | With row 31's fix the smoke failed once more, a unit 2.53 blocks from the place its last order gave, idle; an arrived member steps aside for one still walking, past the 1.5-block arrival and the 1.0 latch. The smoke allows 3.0 now. The L1 scenarios' tighter checks (ORD-03's 1.5 at rest) are unchanged |
| 50 | CMB-01: hit points as damage taken | A unit keeps the damage it has taken (`HealthStore`), not its hit points: 0 for a unit never hurt, which the state hash mixes and a snapshot saves only when not 0, so every older state and journal keeps its hash (the golden journals did), and a change of its most keeps the damage. Its most is the value path `Health/max` (default 100, at least 1) through M1's modifiers; resistances `Resistance/hack`, `pierce`, `crush` (default 0, within ±20); the class tokens for bonuses are `Identity/classes` as modifiers leave them. 0.9 to a level is `DetMath.pow`, StrictMath's |
| 51 | CMB-01: the COMBAT phase | Packets (`Damage`: hack, pierce and crush amounts, bonuses by class, or lethal) queue during the tick, vanilla blows from INGEST first, and `CombatSystem` applies them in COMBAT in their order to targets still alive; each is a `UnitHurt` output. A unit whose hit points reach 0 takes nothing more that tick; then the dead leave in handle order, each a `UnitDied` and a notice `UNIT_LOST` to its faction, and `MatchState#removeUnit` frees its index with a new generation, so every stale handle drops (path results and waiting requests already checked liveness). It is the core's first removal of a unit: a unit spawned at the index later resets every store |
| 52 | CMB-01: the bodies | `UnitHurt` sends vanilla's damage event to the body's trackers (the red flash); `UnitDied` takes the body out of the registry, sets its vanilla health to 0 and calls `die`, which sends the death event and pose, and the registry drives `tickDeath` each tick until vanilla removes the body at its twentieth. The body's vanilla health stays full until then. A `/kill` is lethal damage in the core: M0's `bodytests.vanilla_kill_ignored` became `vanilla_kill_goes_through_the_core` (the body stays in the kill's tick, dies with the core) |
| 53 | T14: tests | `DamageFormulaTest` (4: levels 0 to 20 and negative, each type against its own resistance, bonuses for the target's classes only and multiplied, finite amounts: CMB-01.1), `CombatSystemTest` (5: twenty blows in a tick take exactly their sum, a death takes the unit out, drops a later blow, tells its faction and frees the index for a whole new unit, lethal takes what is left, the damage survives a snapshot: CMB-01.2, .5), `ExternalDamageTest.appliesAsTheCoresDamage`, `combattests.vanilla_blows_become_core_damage` (a player's blow, an arrow, an explosion and `/kill` as hack, pierce, crush and lethal, vanilla health full: CMB-01.4), `combattests.a_dead_units_body_dies_and_goes` (record gone at once, health 0 and the death ticks, the body gone after, the owner's notice within the tick: CMB-01.5). CMB-01.3's unit-on-unit half waits for CMB-02's melee (T15) |

### 1.7 Consent plan

- **Today's grant (2026-10-23):** S9 (T1) first, because it alone needs this Mac's Retina display,
  then any L4 item that is ready.
- **The consent batch (T22):** what needs the owner's eyes or approval: the goldens (pose, outline,
  markers, health bars, HUD) on macOS, the same scenarios once under `-Pcompat` (Sodium, Iris),
  PERF-14, M1's QA-19.4 (a real client's JFR `PacketSent` against QA-19's count; done early, row 38), PATH-10.3
  (overlay goldens, with QA-18; proposed early, row 39), the optional Mac pass of UNIT-09.3. Pending consent blocks
  Accepted, not Done.

---

## 2. Design of the M2 slice

### 2.1 Components

| Component | Where | Card |
|---|---|---|
| `Action`, `Binding`, `ActionMap`, `InputState`, `KeyCode` | `core.client.input` | UI-01 |
| `CameraRig`, `CameraPose`, `CameraLimits` | `core.client.camera` | CAM-02 |
| `Picking` (rays from matrices and cursor), `BoxSelect` | `core.client.pick` | CAM-04, UI-03 |
| `HudLayout`, theme tokens | `core.client.hud` | UI-02 |
| `ClientMatchModel` (summaries, details, HUD state), `Selection`, `ControlGroups` | `core.client.model` | NET-08, UI-03, UI-04 |
| `SlotPreview` (M1's slot layout on a client terrain view) | `core.client.order` | UI-05 |
| `ReplicationService` (summaries, details, HUD changes, per-faction interest) | `core.sim.replicate`, outside the hashed state | NET-08 |
| Damage model, `CombatSystem`, COMBAT states | `core.sim.combat`, `core.sim.unitai` | CMB-01, CMB-02 |
| Attack and attack-move orders | `core.sim.order` | ORD-04 |
| `RtsMode` (enter, leave, first person), `RtsScreen`, rig application, picking hooks | `mod.client.rts` | CAM-02, CAM-04, CAM-05 |
| HUD layers, health bars, order markers, far markers | `mod.client.hud`, `mod.client.overlay` | UI-02, UI-05, CMB-06, NET-08 |
| `CommanderService` (`camera_jump`, `mode_switch`, chunk-sent re-pair, restore on leave) | `mod.server.commander` | CAM-03, CAM-05 |
| `FxSender`, the `fx` payload | `mod.server.net` | AUD-01 |

### 2.2 The commander camera (ADR-0017, 04 §13.1–13.2)

- **RTS mode** begins when a seated player's client gets `match_join` and the player presses the
  RTS toggle, and on login when the seat says so. The server sets spectator mode
  (`ServerPlayer#setGameMode`), the client opens `RtsScreen`.
- **The rig** is pure: focus, yaw, pitch 35–70° (default 55°), height 8–64 above the focus ground
  (default 24; S9 sets the final upper limit), FOV 55°. Pan by keys, screen edge and drag; rotate by
  keys and middle drag; zoom by the wheel; per-tick targets smoothed so the same input gives the
  same pose at any frame rate; clamped to the match space plus a margin.
- **Applying it:** position per tick in `PlayerTickEvent.Pre` (after `setOldPosAndRot`, so
  `Camera#setup` interpolates), angles per frame in `ViewportEvent.ComputeCameraAngles`, FOV in
  `ViewportEvent.ComputeFov`; view bobbing off while in RTS mode. No camera mixin.
- **Limits:** a pan moves at most 5 blocks a tick (S9, §1.6 row 9). Every jump is a `camera_jump`
  answered by a server teleport (`ServerGamePacketListenerImpl#teleport`), in singleplayer too,
  queued and applied at the start of the next tick (§1.6 row 5).
- **After a jump** the server marks the commander on `ChunkWatchEvent.Sent` and runs
  `ServerChunkCache#move(player)` once on the next tick, so bodies in newly sent chunks pair at
  once (M1's `MockViewer#moved` showed that sending a chunk pairs nothing).
- **Leaving** the match or the server restores the player's game mode and position.

### 2.3 Input and the screen (04 §13.3–13.4)

- `RtsScreen` is a non-pausing full-window screen that owns every input: vanilla attack, use,
  hotbar, inventory, drop, the spectator menu and scroll-to-fly-speed are inert by construction.
  It forwards F1, F2, F3 and F11, opens chat, maps Escape to "cancel targeting or placement, else
  pause", and reopens after chat or the pause menu close (`ScreenEvent.Closing`).
- `KeyMapping`s are registered in `RegisterKeyMappingsEvent` under an RTS-only
  `IKeyConflictContext`; `RtsScreen` matches them with `KeyMapping#matches`, held keys polled with
  `InputConstants.isKeyDown`. Defaults follow a documented table; none uses Ctrl + click (a right
  click on macOS).
- **Picking** unprojects the cursor with the `RenderLevelStageEvent` matrices, normalising by
  `Window#getScreenWidth/Height`, never the framebuffer: blocks by `level.clip`, units by rays
  against the client unit index's boxes.
- **Selection** projects feet and heads (box select), Shift adds and removes, a double click takes
  every unit of that template on screen; enemies can be inspected, never selected; at most 300.
  The selection is client state; every command is checked on the server.

### 2.4 Orders from the client

- Right click issues the context order: Move on ground, Attack on an enemy (ORD-04); Shift queues;
  attack-move is a hotkey plus a click. Each click sends exactly one command through M1's
  `commands` payload.
- **The destination preview** runs M1's slot layout (`core.sim.order` slots) on the client against
  a terrain view built from loaded chunks with the same `BlockClassTable`; dragging sets facing and
  width. Where the client lacks reachability data the server's slots may differ within a
  documented tolerance (UI-05.2).
- Markers: the clicked point, queued waypoints, path lines for the selection, attack markers.

### 2.5 Replication tier 2 (NET-08, 04 §12.2–12.4)

| Payload | Direction | Rate and cap | Audience |
|---|---|---|---|
| `strategos:unit_summary` | S2C | 2 Hz, full frames, about 6 B a unit | own units in full, enemies while visible, observers all |
| `strategos:ui_query` | C2S | ≤ 32 handles, 4 a second | – |
| `strategos:unit_detail` | S2C | ≤ 10 Hz | the asker, own units only |
| `strategos:unit_hud` | S2C | changes only, ≤ 5 Hz | those who may see the unit |
| `strategos:camera_jump` | C2S | 4 a second, inside the space plus 32 | commanders |
| `strategos:mode_switch` | C2S | 1 a second, 5 s cooldown | commanders |
| `strategos:fx` | S2C | per event | the unit's trackers, or players whose faction sees the cell |

`ReplicationService` reads the core after each step and is not part of the hashed state. Far
units known from summaries but beyond the tracking range are drawn as markers.

### 2.6 Combat (CMB-01, CMB-02, ORD-04; 04 §6.7)

- **Damage** types hack, pierce and crush; resistance 0.9^level per type; bonus multipliers by
  class token; all unit-on-unit damage resolved in the core, never through `LivingEntity#hurt`, and
  HP never mirrored into vanilla health. Bodies show hits through
  `ServerLevel#broadcastDamageEvent` and `LivingEntity#swing`; at death the adapter sets health to
  0, calls `die()` and drives `tickDeath()`. Vanilla sources hitting units (player melee, arrows,
  explosions, `/kill`) arrive as M1's journaled `ExternalDamage` as hack, pierce, crush and lethal.
  Vanilla armour, shields, totems and loot never apply.
- **Melee:** the UnitAI's COMBAT states (approaching, attacking, chasing, finding a new target);
  wind-up and repeat in ticks; reach; engagement slots round a target; the default aggressive
  behaviour (retaliate when attacked, idle units attack enemies entering range, scanned by activity
  tier through M1's spatial index).
- **Templates** gain `Attack/Melee` (damage per type, reach, wind-up, repeat), `Resistance` and
  `Health`, read through M1's modifier pipeline; the bundled infantry becomes a spearman.

### 2.7 First person (CAM-05, CAM-07; D-08)

`mode_switch` to first person: the server checks the match rule, a 5 s cooldown and that the drop
point is inside the space and standable (explored cells come with fog in M6; until then every cell
counts as explored), sets adventure mode and teleports; back to spectator with the rig above the
anchor. The observer rules: no vision of its own, units ignore it for avoidance, it cannot be hurt,
attack (`AttackEntityEvent` cancelled for match bodies) or edit blocks. First-person orders: "move
the selection to where I look", "follow me", "attack what I look at", inspect.

---

## 3. Build changes

- `mod/build.gradle`: `runSpike` accepts `s9`; a client run that joins the spike server by Quick
  Play (`--quickPlayMultiplayer localhost:25598`), with `-Pcompat` for the Sodium and Iris pass.
- `core`: the `core.client` packages (pure, L1-tested), no Minecraft imports, as the rest of core.
- Goldens per platform under `mod/src/devtools/resources/goldens/<platform>/` (QA-18).

---

## 4. Tests per level

| Level | M2 adds |
|---|---|
| L0 | ArchUnit: `core.client` imports no Minecraft; only `mod.client` touches `Minecraft`; B6 for the new payloads |
| L1 | Input, rig, picking, box select, HUD layout, control groups, slot preview, damage, melee, attack-move, replication interest and codecs, first-person intents |
| L2 | Round trips and caps of the seven new payloads |
| L3 | `cameratests.*` with mock commanders; `combattests.*`; `ordertests.*`; `nettests` for summaries, details, HUD and `fx` audiences |
| L4 | Scenario goldens per platform (QA-18), on CI's Linux route or with consent on this Mac |
| L5 | The server smoke with a seated commander |
| L6 | `perftests.melee_soak_2x100` (A–C, 5 minutes, paced as M1's soak), `perftests.commander_pan_move_cost`, `HudBench`, `HealthBarBench`; PERF-14 on this Mac with consent |
| L7 | The owner's battle sandbox and questionnaire |

---

## 5. Spike S9: the commander camera on a dedicated server (T1) — confirms ADR-0017

- **Server:** `./gradlew :mod:runSpike -Pspike=s9`, the self-terminating spike server on a fresh flat
  world (port 25598). It creates a 384² match and spreads about 1,000 debug walkers over it, half of
  them walking between random points, so tracked bodies move; it adds one mock commander (a
  spectator `MockViewer` driven at 8 blocks a tick on a lawnmower path, §1.6 row 3) and waits for
  the real client.
- **Client:** the scenario runner joins by Quick Play and plays `s9`: RTS mode with a spike rig
  (spike code, never promoted): pans at 10, 30 and 60 blocks a second along fixed paths, twenty
  300-block jumps through a spike `camera_jump`, then picking checks.
- **Measured:**
  - snap-backs: `ClientboundPlayerPositionPacket`s the client receives outside jumps (a client
    counter), and the server's "moved too quickly" and "moved wrongly" warnings;
  - coverage: after each jump, the time until every chunk within the view radius is loaded on the
    client and every server body inside the tracking range exists on the client;
  - `ChunkMap#move` per commander per tick: the server times `ServerChunkCache#move` for each
    commander once a tick, the call each accepted movement packet makes;
  - picking at GUI scale 2 on this Mac's Retina display: for 20 known blocks and units, the cursor
    at their projected screen point picks them within one block;
  - all of it once more with `-Pcompat` (Sodium, Iris).
- **Exit:** zero snap-backs while panning, at most the known one-off echo warning per jump; full
  coverage within 3 s after a 300-block jump; picks within one block; `ChunkMap#move` ≤ 0.5 ms per
  panning commander per tick; the same under Sodium and Iris. The report confirms ADR-0017 or
  adopts the fallback its numbers choose, and sets the camera height limit.
- **Output:** `docs/spikes/S9-commander-camera.md`.

## 6. The melee soak (T15) — CMB-02.6

On the proving ground, two factions of 100 spearmen at the two fords, sent at each other with
attack-move every 600 ticks after each wave ends (respawned to 100), 6,000 ticks paced at 20 a
second (5 minutes, M1 §1.6 row 162's pacing); budgets A, B and C as M1's soak measures them.

---

## 7. Task breakdown

### 7.1 Order

- **Today, with the Mac:** T1 (S9). Its numbers decide CAM-02, CAM-03 and CAM-04.
- **Headless, in this order:** T2 (health check), T14 (CMB-01), T15 (CMB-02), T16 (ORD-04, core
  part), T10 (NET-08), T9 (CAM-03), T3 (UI-01), T4 (CAM-02), T5 (CAM-04), T6 (UI-02), T7 (UNIT-03),
  T11 (UI-03), T12 (UI-04), T13 (UI-05), T17 (CMB-06), T18 (AUD-01), T19 (CAM-05), T20 (CAM-07).
- **With windows (a grant or CI's Linux route):** T8 (QA-18) once T5 exists, then each task's L4
  items; T21 (PERF-14) and T22 (the consent batch) with consent; T23 closes.

### 7.2 Tasks

#### T1 · Spike S9, the commander camera — CAM-01 · ~1,200 lines of spike code
- **Files:** `gametest/spike/S9Camera` (server side), `devtools/S9CameraScenario` and a spike rig
  (client side), `docs/spikes/S9-commander-camera.md`.
- **Verification:** the S9 runs of §5; `verify`.

#### T2 · Health check from M1's close · ~300 lines moved
- Split `server/command/StrategosCommands` (864 lines) by subtree and the test kit's `Scenarios`
  (977) by area; no behaviour change. **Verification:** `verify`.

#### T3 · Input action layer — UI-01
- **Files:** `core.client.input`; `mod.client.rts.RtsKeys` (`KeyMapping`s, conflict context); the
  vanilla default-key table fixture. **Verification:** `verify`; L4 rebinding with consent.

#### T4 · Camera rig — CAM-02
- **Files:** `core.client.camera`; `mod.client.rts.RigApplier`; client options. **Verification:**
  `verify`; pose goldens (L4).

#### T5 · `RtsScreen`, cursor and picking — CAM-04
- **Files:** `mod.client.rts.RtsScreen`, `RtsMode`; `core.client.pick`. **Verification:** `verify`;
  10 scripted clicks (L4).

#### T6 · HUD framework — UI-02
- **Files:** `core.client.hud`; `mod.client.hud` layers; `HudBench` (§1.6 rows 40–42). **Verification:** `verify`;
  goldens (L4).

#### T7 · Unit renderer for the RTS view — UNIT-03
- **Files:** `mod.client.UnitRenderer` (outline, team colour, 192-block distance).
  **Verification:** `verify`; outline goldens (L4).

#### T8 · Scenario diff and goldens — QA-18
- **Files:** `devtools` `scenarioDiff`, goldens per platform, `scenario.yml`'s golden step, the
  README procedure. **Verification:** three runs; a changed golden fails on CI.

#### T9 · Commander body, jumps, chunk-sent re-pair — CAM-03
- **Files:** `mod.server.commander`; payload `camera_jump`; `cameratests`;
  `perftests.commander_pan_move_cost`. **Verification:** `verify verifyPerf`.

#### T10 · Tier-2 replication — NET-08
- **Files:** `core.sim.replicate`, `core.client.model.ClientMatchModel`; payloads `unit_summary`,
  `ui_query`, `unit_detail`, `unit_hud`; far markers. **Verification:** `verify`.

#### T11 · Selection — UI-03 · T12 · Control groups — UI-04 · T13 · Order feedback and preview — UI-05
- **Files:** `core.client.model.Selection`, `ControlGroups`; `core.client.order.SlotPreview`;
  `mod.client.overlay` markers. **Verification:** `verify`; L4 items.

#### T14 · Damage model — CMB-01 · T15 · Melee and the soak — CMB-02 · T16 · Attack and attack-move — ORD-04
- **Files:** `core.sim.combat`, COMBAT states in `core.sim.unitai`, `Attack` and `AttackMove`
  commands, template components; `combattests`; `perftests.melee_soak_2x100`; goldens re-recorded
  with a reason where hashes change. **Verification:** `verify verifySlow verifyPerf`.

#### T17 · Health bars — CMB-06 · T18 · Sounds — AUD-01
- **Files:** `mod.client.overlay.HealthBars`, `HealthBarBench`; `fx` payload, `FxSender`, client
  playback. **Verification:** `verify`; goldens (L4).

#### T19 · First person — CAM-05 · T20 · First-person orders — CAM-07
- T19 (§1.6 rows 44–46): `ModeSwitch`, `mode_switch`, `CommanderService#switchMode`, `ObserverRules`,
  `firstpersontests`, the scenario `rts_first_person`.
- **Files:** `mode_switch`; observer rules in `mod.server.commander` and the avoidance agents;
  first-person intents. **Verification:** `verify`.

#### T21 · Client frame gate — PERF-14 (consent)
- The S4 scene grown to the RTS view, on this Mac, Sodium on and off; archived per machine.
- **Files:** `devtools.RtsFrameScenario` (`rts_frame`), `perf/client/<machine>.json`. **Verification:**
  three runs per profile, each within the card's budgets (§1.6 rows 28–30).

#### T22 · The consent batch
- §1.7's list, run in one sitting with the owner's consent.

#### T23 · M2 close
- §8 walked into results; health check; docs; STATE with the demo and the owner's items.

### 7.3 Progress

- [x] T1 (2026-10-23) · [x] T2 (2026-10-23) · [x] T3 (2026-10-23) · [x] T4 (2026-10-23) · [x] T5 (2026-10-23) · [x] T6 (2026-10-23) · [ ] T7 (outline and tags done; the 192-block check with NET-08) · [x] T8 (2026-10-23) · [x] T9 (2026-10-23) · [ ] T10 · [x] T11 (2026-10-23) · [x] T12 (2026-10-23)
- [x] T13 (2026-10-23) · [ ] T14 (the damage model done; the unit-on-unit check with T15) · [ ] T15 · [ ] T16 · [ ] T17 · [ ] T18 · [x] T19 (2026-10-23) · [ ] T20 · [x] T21 (2026-10-23) · [ ] T22 · [ ] T23

---

## 8. Acceptance checklist (M2 exit)

The roadmap's Done gate, then the owner's acceptance.

| # | Criterion | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the M2 cards passes at its level | §1.4 |
| 2 | L1: rig, picking, selection, layout and slot preview at every tested resolution, GUI scale and framebuffer scale | `CameraRigTest`, `PickingTest`, `BoxSelectTest`, `HudLayoutTest`, `SlotPreviewTest` |
| 3 | L3: a mock commander pans 12,000 ticks at 5 blocks a tick (§1.6 row 9) with 20 jumps and gets no position packet but the jumps'; bodies pair within a tick of `ChunkWatchEvent.Sent`; coverage within 3 s of a 300-block jump | `cameratests.*` |
| 4 | L6: the 2 × 100 melee soak holds A–C for 5 minutes; `ChunkMap#move` ≤ 0.5 ms per panning commander | `perftests.melee_soak_2x100`, `perftests.commander_pan_move_cost` |
| 5 | The consent batch: goldens approved by the owner, once under `-Pcompat`; PERF-14; M1's deferred items | T22 |
| 6 | ADR-0017 accepted with the S9 numbers, or its fallback adopted | S9 report, the ADR |
| 7 | M1's gates still pass; `verify`, `verifySlow`, `verifySoak`, `verifyPerf` green; CI green | the runs |
| 8 | L7: 10 minutes of panning and 20 jumps without a snap-back; the battle sandbox scores "commanding a fight" ≥ 3 of 5, none ≤ 2 | the owner's note in STATE.md |

---

## 9. Risks and fallbacks

| # | Risk | Sign | Fallback |
|---|---|---|---|
| R1 | The spectator body snaps back or costs too much `ChunkMap#move` | S9 | ADR-0017's fallback 1, then 2 |
| R2 | A chunk-sent re-pair misses bodies after a jump | `cameratests.chunk_sent_repair` | A second `move` a few ticks later; a coverage check |
| R3 | `RtsScreen` loses the cursor after chat or the pause menu | CAM-04.4 | Reopen on `ScreenEvent.Closing` and on the next tick |
| R4 | Retina picking is off by the framebuffer scale | S9, `PickingTest` | Normalise by window size everywhere (ADR-0017) |
| R5 | The client slot preview disagrees with the server near cliffs | UI-05.2 | A documented tolerance; the server's slots win |
| R6 | Combat breaks the golden journals' hashes | `GoldenJournalTest` | Re-record with a reason (M1's procedure) |
| R7 | Goldens differ between runs | QA-18.1 | Masks for animated areas, documented |
| R8 | The window grant ends before S9 finishes | the clock | Save the state; the rest of S9 waits for the next grant, headless tasks go on |

---

## 10. Owner inputs

| Input | When | Blocks |
|---|---|---|
| Consent for client windows | per grant; today's until the evening | L4 items only |
| Goldens and the PERF-14 baseline | T22 | Accepted |
| The questionnaire | after the demo | Accepted |
