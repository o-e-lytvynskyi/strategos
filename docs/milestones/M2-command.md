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
| CMB-02 | T15 | 1–4 `MeleeTest` (L1) · 5 `combattests.squads_fight_as_the_core_predicts` (L3) · 6 `perftests.melee_soak_200` (L6) |
| ORD-04 | T16 | 1 `OrderIntentTest` (L1) · 2 `MeleeTest.attackMovingUnitsFightWhatTheyMeetThenGoOn` (L1) · 3 `combattests.attack_move_through_a_squad_as_the_core_predicts` (L3) · 4 markers in `rts_select` (L4) |
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
| 54 | NET-08: the summary frame | `UnitSummary` keys a unit by its index, not its handle: one live unit holds an index at a time and every body carries it, so the client needs no generation to tell a body from a marker. Entries go by faction in index order, each the index's step, template and state in one byte (templates past 30 escape to a varint) and match-local X and Z in eighths of a block, two bytes each up to 2,048 blocks: 6.0 bytes a unit for 600 units, 3.6 KB a frame; a frame holds at most 2,048 |
| 55 | NET-08: who gets what | `ReplicationService.summary` (core, outside the hashed state) reads the queries after a step: a faction's own units and the enemies its `Visibility` sees, every unit for an observer; until fog (M6) every unit is visible. The mod's `Replication` sends each running match's seated factions their frame every 10 server ticks (2 Hz), after the matches stepped and before the gateway's flush; a test sets its own visibility. The client keeps the last frame of its match in `ClientMatchModel` (`core.client.model`), newer frames only |
| 56 | NET-08: far markers | `FarMarkers` draws a unit of the model whose body the client does not track as a square of its faction's colour on the ground, extracted then drawn in one batch at `AFTER_PARTICLES`; a column whose chunk the client has not loaded gets none. Bodies pair within min(160, view distance × 16) blocks, and at render distance 12 chunks arrive to 192, so from the top height the band 160–192 blocks out is markers. The client keeps a ring of chunks loaded that it does not draw, so a few markers stand past the drawn ground's edge (row 36) |
| 57 | T10 so far: tests | `UnitSummaryCodecTest` (3: 600 units at 6.0 bytes each within the cap, the order and escapes, the cap: NET-08.1), `ReplicationServiceTest` (2: own and only visible enemies, never the hidden handle, an observer all: NET-08.2), `replicationtests.summaries_at_two_hertz_own_and_visible_only` (four frames in 2 s to each of two seated players; the hidden enemy never reaches faction 0, faction 1 gets its own and all of faction 0: NET-08.3). L4 on this Mac, `far_markers`: from the top height at render distance 12, a band of 72 enemies 150–175 blocks out and five own units: of the 17 in the window on loaded ground, 5 bodies and 12 markers, none missing (NET-08.5, UNIT-03.3). The first runs in a 512² and a 384² space hung the integrated server's stop in its chunk unloading; 256², as the other scenes use, stops at once |
| 58 | NET-08: `unit_hud` | Each seated player has a `HudMemory` (core): every 4 ticks (5 Hz) it gets the units it sees whose hit points, in 255ths of their most, differ from what it was last sent, once each; a unit never listed is whole, and a unit still alive never shows 0. To that player only (`Player` audience), so each player's memory stays its own |
| 59 | NET-08: `ui_query` and `unit_detail` | A query (at most 32 handles, four a second; a fifth in a second is refused) replaces the player's subscription, an empty one ends it; every 2 ticks (10 Hz) the player gets `unit_detail` when it differs from the last sent: its own live units among the handles, each with template, state, hit points and their most, effective walk speed and its orders, at most 16. The client's HUD asks for the selection's details when the selection changes, at most every 5 ticks. The visibility rule a test sets is the match's own (`Replication#visibility(match, rule)`), since a batch's tests run together |
| 60 | T10: tests | `HudAndDetailTest` (3: the hud lists changes once and only what the viewer sees, details for own units only, the three codecs round-trip), `replicationtests.detail_answers_own_units_only` (NET-08.4), `replicationtests.hud_reaches_only_those_who_see` (the hidden enemy's hit points never reach faction 0, faction 1 gets both: CMB-06.1). With rows 54–57, every acceptance item of NET-08 |
| 61 | CMB-06: which units get a bar | `HealthBars` (`core.client.hud`) takes each body of the match the client draws, with its hit points from `ClientMatchModel` (`unit_hud`'s 255ths; a unit never listed is whole): a selected unit gets a bar, and so does an unselected one below whole. The bar sits half a block over the head, 1.5 times the body's width and at least 0.9 blocks wide, fills from the left and runs green, whole, through yellow at half to red. It is extracted once a frame from the bodies' interpolated positions; units without a body (far markers) get none |
| 62 | CMB-06: the drawing | `HealthBarOverlay` draws the frame's bars in one batch at `AFTER_PARTICLES`, each a dark back and its fill as quads facing the camera, the fill nudged 0.01 block toward the camera against the look vector (the camera's left × up points away from it; the first cut hid the fill behind its back). `debugQuads` keeps the depth test, so the ground and bodies in front hide a bar; F1 hides them with the GUI |
| 63 | T17: tests and numbers | `HealthBarsTest` (2: a bar for the selected and the hurt only, half a block over the head and at least 0.9 wide; the colour green, yellow at half, red), `HealthBarBench.bars300`: 0.92 µs for 300 units against CMB-06.2's 0.5 ms. `rts_goldens` hurts five units (20 to 90 hit points, two own and three enemies) before its shots and waits a second for `unit_hud`, so the close, default and top poses all show bars, over the selected group in the outline and tooltip frames too; re-recorded three times on this Mac (identical but for 2.9e-10 in `pose_high`) and once on CI's llvmpipe, proposed in `goldens-proposed` (CMB-06.3). PERF-14 again, three runs a profile with bars over the selected hundred: frame p50 2.70 ms and p99 6.22 ms without performance mods, 1.86 and 4.78 with Sodium; our drawing 0.82 and 0.87 ms against 3, of which the bars' pass is 0.024 and 0.011 ms (CMB-06.2's budget F; `perf/client/m4pro.json`) |
| 64 | AUD-01: `fx` | The `fx` payload holds up to 64 events of a match, each a kind (`UNIT_HIT`, `UNIT_DIED`) and where, X and Z in eighths of a block from the space's origin as two bytes each, Y from the origin's height as a zigzag varint: 6 or 7 bytes an event. `FxSender` reads a step's outputs before they apply, while the dead still have bodies: a unit hurt in the tick sounds once and a unit killed once, its death in place of the tick's hits, at its body and to its trackers only (`Audience.TrackersOf`); the payload declares trackers, a cell's watchers and a player. An order's acknowledgement needs no `fx`: the player already gets its `feedback`, and the client sounds it from there |
| 65 | AUD-01: on the client | `Sounds` (`core.client.audio`) maps each `fx` kind and each command outcome to a vanilla sound: a hit `entity.player.hurt`, a death `entity.player.death`, both in the friendly-creatures volume; accepted or partly accepted `entity.villager.yes`, refused `entity.villager.no`, as interface sounds. `FxPlayer` plays an event at its place with `ClientLevel#playLocalSound`. Vanilla fades a sound linearly over its volume (at least 1) times 16 blocks and caps its loudness at 1 (SoundEngine.java:447–450, 570), so from the RTS camera the volume is raised until the sound carries 2.5 times the distance to the ground in view: a hit there plays at 60 %. At most six sounds of a kind start in a client tick (`SoundBudget`). The bodies are silent (`UnitEntity#isSilent`): every vanilla path through `Entity#playSound` stops there. That call posts `PlayLevelSoundEvent.AtPosition`, not `AtEntity` (Entity.java:1138–1141, ServerLevel.java:989–1000), so 04 §10.5's listener on `AtEntity` alone would have heard none of them; corrected there |
| 66 | T18: tests | `FxMappingTest` (3: every kind and outcome has its sound and category; the volume vanilla's own close to the ground, the focus at 60 % from the default height, more from the top; six a tick per kind: AUD-01.1), `WireCodecSizeTest.anFxBatchRoundTripsWithinItsCap`, `combattests.no_server_sounds` (bodies hit, shot, killed and made to call `playSound` and `makeSound` never sound through either event, a pig beside them does, every cue a vanilla sound: AUD-01.2; without `isSilent` it hears the bodies twice), `nettests.fx_audience` (the hit and the death reach the player tracking the body, at the body; the one seated in the match 240 blocks off gets the match's state and no `fx`: AUD-01.3; with `SeesCell` in place of trackers it fails). L4 on this Mac: `rts_goldens`' five hurts start five hit sounds, `rts_first_person`'s order one acknowledgement; the goldens unchanged. AUD-01.4 is the owner's, in the demo's step 7 |
| 67 | CMB-02: the tree | COMBAT (3) under INDIVIDUAL holds APPROACHING (4) and CHASING (6), which report WALKING since the movement systems walk them, and ATTACKING (5), which reports the new `UnitState.COMBAT`; WALKING_AND_FIGHTING (7, WALKING) carries out attack-move. Finding a new target is a step, not a state: whichever state finds its target dead takes up the nearest enemy in sight from a new place or finishes the order, so no unit ever rests there and UNIT-02.1's table needs no row for it. An attack always enters COMBAT afresh, so a new target gets a new place. The table has 43 rows now, every declared transition with one, the one only a think makes (a target stepping out of reach is chased) through a tick |
| 68 | CMB-02: orders | `OrderKind` gains ATTACK (the player's, chased until the target dies or the order ends), ATTACK_MOVE and ENGAGE: an attack the unit takes up itself, put before its orders with `pushFront`, so when it ends the unit walks and fights on or stands idle again; it is given up when its target is farther than the unit's sight. The queue keeps the attacked unit per order, and the attacker's place round it in the slot word; a save keeps the current order's kind and target (`"kind"`, `"attacks"`, both written only when set) and the hash mixes the target only for attacks, so every older save loads and every older state hashes as before (the golden journals did). A move of the place, as the target walks, is `PlaceMoved`: a new path and the arrival latch released, the machine where it is; it moves once the place is a block off, or a quarter of the way, whichever is more |
| 69 | CMB-02: the rhythm and reach | Reach is between the two bodies' edges, with 1e-9 for rounding (1.9 blocks apart came out at 1.2000000000000002 against a reach of 1.2). A unit in reach when it takes up an attack strikes at once; the first data word holds the tick of its next hit, the wind-up from the start, then the period after each: the hits of CMB-02.1 land on exactly those ticks. A target may step half a block past the reach before the attacker chases it. Each hit is a `Damage` packet for the same tick's COMBAT phase and a `UnitStruck` output, which swings the body's arm (`LivingEntity#swing`, to its trackers); the attacker turns to face its target |
| 70 | CMB-02: places round a target | `Engagement`: up to four rings, the first 0.15 blocks out from the edges and each next 0.75 farther, places 0.8 apart, numbered ring by ring, each ring turned half a place; a spearman round a spearman has 6, then 12, 18 and 24. An attacker takes the free place of the innermost ring nearest to it; the places taken are read from every live attacker's order, near or still on its way, so ten sent at once take ten places. An arrival short of reach takes another free place |
| 71 | CMB-02: the aggressive default | An idle unit with a melee attack looks every 10 ticks for the nearest enemy within its sight (`Vision/range`, 12 blocks by default) and engages it; walking and fighting looks every 5. A unit that lives through another unit's hit hears `Attacked` from the COMBAT phase and, idle or walking and fighting, hits back; a player's move is never interrupted, and a fighting unit keeps its target. A stop ends every order; an aggressive unit with an enemy still in sight takes it up again. The grid the scan reads is the last movement's, so a unit killed since is skipped |
| 72 | CMB-02: the spearman | New core paths with their defaults and ranges: `Attack/Melee/hack`, `pierce`, `crush` (0, at least 0), `reach` (1, 0–8), `prepare` (10 ticks, 0–200), `repeat` (20, 1–200) and `Vision/range` (12, 0–64); a unit whose amounts are all 0 has no melee attack, as the debug walker. The bundled infantry becomes a spearman: 3 hack and 3 pierce a hit, reach 1.2, a 10-tick wind-up and 20-tick period, resistances hack 5, pierce 5, crush 2 (0.9^5 lets 59 % through), class `Spearman`; a spearman kills another in 29 hits, about half a minute. Its bundled JSON in the mod is the same (DATA-03's test). Class bonuses wait for the first class that needs one (cavalry, CMB-04) |
| 73 | ORD-04: commands | `Attack` (units, the target's handle, queue mode; wire id 4) and `AttackMove` (units, point, mode; 5). A target that is not a live unit is refused as unknown, as a hidden one will be; one of the unit's own faction as an invalid argument; a unit without a melee attack as unsupported. AttackMove places its units round the point as a move does (the move rule's walk, shared), and its group asks for one path (PATH-09). A journal names its catalogue; one written with fewer kinds now reads when each of its kinds has the same number here, so M1's journals replay in M2. The client's right click on an enemy's body attacks it, on the ground or a unit of one's own moves; Z arms attack-move for the next right click; H stops the selection. Markers: white a move, red an attack, orange attack-move |
| 74 | T15, T16: tests and numbers | `MeleeTest` (7: hits 10, 30, 50 and 70 ticks after the order and a swing with each, 6 a hit on no resistance: CMB-02.1; ten attackers in ten places, every one striking: .2; a chase until the target dies, a stop ending one: .3; an idle spearman engaging a walker that comes into sight and giving it up once it outruns its sight, a blind one hitting back a wind-up after the first blow: .4; an attack-move killing the two it met and walking on to its point: ORD-04.2), UNIT-02.1's 43 rows, `OrderIntentTest.aClickOnAnEnemyAttacksOnTheGroundMovesAndArmedAttackMoves` (ORD-04.1), the codec and catalogue tests. `combattests.squads_fight_as_the_core_predicts` (five spearmen a side; the journal replayed in L1 ends on the live hash, so the same units survive with the same hit points: CMB-02.5; every living body's vanilla health full: CMB-01.3) and `combattests.attack_move_through_a_squad_as_the_core_predicts` (ORD-04.3). `perftests.melee_soak_200` (CMB-02.6; `-Pperf.gates=a,b,c,g`): 10 waves, 28,082 hits and 953 deaths in 6,000 paced ticks; A p50 0.79 ms and p99 1.97 ms against 5 and 10, B p95 1.95 ms against 17, C p95 6.07 ms against 23; D p99 0.22 ms and E 2.2 KB/s a viewer reported. A first run sent the waves to a point between the fords, where the river is deep: 65 hits in 10 waves, no fight; each half now crosses at its own ford. L4 on this Mac, `rts_select` continued: a right click on an enemy sends an Attack with a red marker over its head, Z then a right click on the ground an AttackMove all 150 hold, with an orange marker (ORD-04.4; the owner's look in T22) |
| 75 | CAM-07: follow me | The core cannot follow a player it does not know, and a first-person commander is no avoidance agent (row 45), so the server sends, each tick, where each seated commander in first person stands and faces: `LeaderUpdate` (slot, match-local X and Z, yaw), journaled and held for its tick only, like the agents, so nothing is saved or hashed and a commander back in the RTS view leads no one. `Follow` (units, mode; wire id 6) gives each owned unit the order FOLLOW, the leader's slot as its target, one group for the command and the next place in handle order; the administrator leads no one. FOLLOW holds FOLLOW_WALKING (WALKING) and FOLLOW_WAITING (IDLE): every 5 ticks the follower's place is the move's layout (`SlotLayout`) across the leader's facing, its front row 2 blocks behind; walking, it moves the place as a chasing attacker does (`PlaceMoved`); arrived, it waits until the place is 1.5 blocks off. A waiting follower is idle but keeps its order, so M1's latch no longer walks an idle unit with an order back to its latch point. The transition table has 61 rows now |
| 76 | CAM-07: what the crosshair picks | `CrosshairPick` (`core.client.pick`): the ray from the eye along Minecraft's yaw and pitch, and of the match's bodies it enters the nearest before the first block it hits, within 128 blocks. G in first person attacks the enemy picked, else moves the selection to the block in view as before; R (`FOLLOW_ME`, no vanilla default on it) sends one Follow of the selection. Inspecting is looking: by the crosshair a panel shows the picked unit's name and faction, an own unit's hit points and state from `unit_detail` (its `ui_query` on the HUD's limiter), an enemy's share of its hit points from `unit_hud` |
| 77 | T20: tests | `FollowTest` (2: six walkers behind a leader walking east, never more than 12 blocks back, waiting in their own places once it stops and staying when it leaves first person; the administrator leads no one), `CrosshairPickTest` (2: the body in view, the nearest of two in line, none behind a block or looking aside; the ray's directions as Minecraft's), `OrderIntentTest.followMeSendsOneFollowOfTheSelection` (CAM-07.1, with G's attack through `OrderIntents.context`), UNIT-02.1's 18 follow rows, the codec and catalogue tests; `firstpersontests.followers_keep_places_behind_a_walking_player` (a mock commander in first person walking 15 blocks east: CAM-07.3). L4 on this Mac, `rts_first_person` continued: looking at an own walker shows "Debug walker 100 / 100 · idle" by the crosshair (CAM-07.4, `first_person_inspect` for the owner), G on an enemy sends an Attack, R a Follow, and all twelve follow as the player walks |

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
| L6 | `perftests.melee_soak_200` (A–C, 5 minutes, paced as M1's soak), `perftests.commander_pan_move_cost`, `HudBench`, `HealthBarBench`; PERF-14 on this Mac with consent |
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
  commands, template components; `combattests`; `perftests.melee_soak_200`; goldens re-recorded
  with a reason where hashes change. **Verification:** `verify verifySlow verifyPerf`.

#### T17 · Health bars — CMB-06 · T18 · Sounds — AUD-01
- T17 (§1.6 rows 61–63): `HealthBars` (`core.client.hud`), `HealthBarOverlay`, `HealthBarBench`, the
  hurt phase of `rts_goldens` and the bars' timer in `rts_frame`.
- T18 (§1.6 rows 64–66): `Fx`, `FxPayload`, `FxSender`; `Sounds`, `SoundBudget` (`core.client.audio`) and
  `FxPlayer`; silent bodies.
- **Files:** `mod.client.overlay.HealthBars`, `HealthBarBench`; `fx` payload, `FxSender`, client
  playback. **Verification:** `verify`; goldens (L4).

#### T19 · First person — CAM-05 · T20 · First-person orders — CAM-07
- T19 (§1.6 rows 44–46): `ModeSwitch`, `mode_switch`, `CommanderService#switchMode`, `ObserverRules`,
  `firstpersontests`, the scenario `rts_first_person`.
- T20 (§1.6 rows 75–77): `LeaderUpdate`, `Follow`, the FOLLOW states and `Following`; `CrosshairPick`; G, R and
  the inspect panel; `rts_first_person`'s CAM-07 phases.
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

- [x] T1 (2026-10-23) · [x] T2 (2026-10-23) · [x] T3 (2026-10-23) · [x] T4 (2026-10-23) · [x] T5 (2026-10-23) · [x] T6 (2026-10-23) · [x] T7 (2026-10-23) · [x] T8 (2026-10-23) · [x] T9 (2026-10-23) · [x] T10 (2026-10-23) · [x] T11 (2026-10-23) · [x] T12 (2026-10-23)
- [x] T13 (2026-10-23) · [x] T14 (2026-10-23) · [x] T15 (2026-10-23) · [x] T16 (2026-10-23) · [x] T17 (2026-10-23) · [x] T18 (2026-10-23) · [x] T19 (2026-10-23) · [x] T20 (2026-10-23) · [x] T21 (2026-10-23) · [ ] T22 (the owner's: all but `pose_high` and the demo) · [x] T23 (2026-10-23)

---

## 8. Acceptance checklist (M2 exit)

The roadmap's Done gate, then the owner's acceptance.

| # | Criterion | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the M2 cards passes at its level | §1.4 |
| 2 | L1: rig, picking, selection, layout and slot preview at every tested resolution, GUI scale and framebuffer scale | `CameraRigTest`, `PickingTest`, `BoxSelectTest`, `HudLayoutTest`, `SlotPreviewTest` |
| 3 | L3: a mock commander pans 12,000 ticks at 5 blocks a tick (§1.6 row 9) with 20 jumps and gets no position packet but the jumps'; bodies pair within a tick of `ChunkWatchEvent.Sent`; coverage within 3 s of a 300-block jump | `cameratests.*` |
| 4 | L6: the 2 × 100 melee soak holds A–C for 5 minutes; `ChunkMap#move` ≤ 0.5 ms per panning commander | `perftests.melee_soak_200`, `perftests.commander_pan_move_cost` |
| 5 | The consent batch: goldens approved by the owner, once under `-Pcompat`; PERF-14; M1's deferred items | T22 |
| 6 | ADR-0017 accepted with the S9 numbers, or its fallback adopted | S9 report, the ADR |
| 7 | M1's gates still pass; `verify`, `verifySlow`, `verifySoak`, `verifyPerf` green; CI green | the runs |
| 8 | L7: 10 minutes of panning and 20 jumps without a snap-back; the battle sandbox scores "commanding a fight" ≥ 3 of 5, none ≤ 2 | the owner's note in STATE.md |

### 8.1 Results (T23, 2026-10-23)

| # | Result | Evidence |
|---|---|---|
| 1 | Every acceptance criterion of the 20 cards passes at its level but the owner's: the questionnaires and checklists (CAM-02.4, UI-05.6, AUD-01.4: L7), the looks at proposed goldens and frames (CMB-06.3, ORD-04.4, CAM-07.4, the poses, outline, HUD and overlay: T22), and PERF-14's baselines; 19 cards done, PERF-14 in progress until its baselines | §1.4, §1.6 |
| 2 | Pass | `CameraRigTest`, `PickingTest`, `BoxSelectTest`, `HudLayoutTest`, `SlotPreviewParityTest` |
| 3 | Pass: the 12,000-tick pan with 20 jumps and no snap-back, the chunk-sent re-pair, units ignoring the commander | `cameratests.pan_no_snap_back`, `jump_moves_body_and_limits`, `chunk_sent_repair`, `units_ignore_commander` |
| 4 | The melee soak, twice: A p50 0.68–0.79 ms, B p95 1.95–3.35 ms, C p95 5.2–6.1 ms, 28,082 hits and 953 deaths both times; a panning commander's `ChunkMap#move` 0.08 ms at p99 (0.5) | `perftests.melee_soak_200`, `perftests.commander_pan_move_cost` |
| 5 | The owner approved the goldens, PERF-14's baselines and the rest on 2026-10-23, but `pose_high`, whose world edge needs a window to look into; done early: QA-19.4 (0.006 %), PATH-10.3's overlay goldens, the RTS scenarios once under `-Pcompat`; UNIT-09.3's optional Mac pass not taken | §1.6 rows 38–39, STATE.md |
| 6 | ADR-0017 accepted with S9's numbers; no fallback needed | §1.6 row 10, the ADR |
| 7 | Pass since the owner's word of 2026-10-23: `verify`, `verifySlow`, `verifySoak` and `verifyPerf` green on the new JMH baseline, `OrcaBench`'s events allowed their messages; CI green | M1 spec §10.1 rows 7–8, §1.6 D row 169 |
| 8 | The owner's | STATE.md |

**Deviations.** M2 ran alongside M1's close, at the owner's word: T27's runs measured M2's tree. Finding a
new target is a step, not a COMBAT state (row 67); first-person follow needs the commander's position as a
journaled input, `LeaderUpdate` (row 75). The soak's waves cross at the fords (row 74), since a point
between them sends them the long way round.

**Follow-ups.** The health check is M1's (its §10.1: the same tree): `RtsMode` grew to 780 lines with M2
and is next to split, with `MotionSystem` (798); the two test classes over 800 lines split by area.

**Status.** **M2 is Done** (2026-10-23): rows 1–4, 6 and 7 pass; row 5's `pose_high` and row 8's demo are the owner's and block Accepted, not Done (§1.7).

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
