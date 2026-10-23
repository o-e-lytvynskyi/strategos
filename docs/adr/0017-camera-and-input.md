# ADR-0017: A spectator body at the camera eye, a cursor-owning screen, perspective projection

Date: 2026-10-21

## Status

Accepted 2026-10-23, with spike S9 and two amendments

**Accepted 2026-10-23** with spike [S9](../spikes/S9-commander-camera.md) (M2 T1): on a dedicated
server with 1,000 bodies, a real client and a mock commander, the decided body met every exit
criterion, vanilla and under Sodium and Iris: no snap-back at 10–100 blocks a second, coverage
0.3 s after 300-block jumps, picks exact on Retina, `ChunkMap#move` 0.19–0.22 ms at p99 per
commander. Two amendments, both from its runs: **pans are capped at 5 blocks a tick**, not 8, since
a client that runs two ticks in one frame sends two packets in one server tick, and the second, at
8 a tick, passes the server's `10 × √k` blocks (ServerGamePacketListenerImpl.java:910–919); and
**a jump teleports at the start of the next server tick** (`ServerTickEvent.Pre`), since a teleport
after the connections tick is answered between ticks and measured against the tick's first good
position (262), which drew 2–3 "moved too quickly" a jump. With that, no echo warning occurs at
jumps. Neither fallback is needed; the camera height stays 8–64 blocks.

*Note 2026-10-21:* the owner decided D-08 (the observer avatar: no vision, units pass through it, explored cells only; first-person combat, CMB-08, deferred) and D-09 (perspective), both as recommended. ADR-0023 records them; this ADR stays Proposed until S9 reports.

## Context

Research contradiction 7 and gap G2. The owner wants an AoE-style RTS camera with box select and
mouse orders, plus the ability to drop into first person. Verified in the 1.21.1 client and server
sources:

- **Streaming follows the body.** Chunks stream and entities track around the player's body, not
  the camera. `LocalPlayer#sendPosition` sends movement only while `isControlledCamera()`
  (LocalPlayer.java:277, 700). A client camera entity therefore stops movement packets, and with
  them streaming and the movement-packet re-check (fact 18).
- **A server camera anchor** (`ServerPlayer#setCamera`) drags the player onto it every tick and
  resets when Shift is held (ServerPlayer.java:524–534). Shift is the queue key.
- **The movement check.** The dedicated server snaps back moves of more than about 10 blocks per
  packet, exempting only the singleplayer owner (ServerGamePacketListenerImpl.java:918–919).
  Spectators skip "moved wrongly" and have `noPhysics` (946–958; Player.java:258).
- **With a screen open:** `Minecraft#tick` skips `handleKeybinds` (Minecraft.java:1847–1849),
  clicks go to the screen without grabbing the mouse, and scrolling reaches the screen instead of
  spectator flight speed (MouseHandler.java:182–185). Without a screen, `setScreen(null)` re-grabs
  the mouse (Minecraft.java:1067).
- **Smoothness without a mixin.** `Camera#setup` lerps from `xo` to `x` and posts
  `ViewportEvent.ComputeCameraAngles` (Camera.java:47–62). `PlayerTickEvent.Pre` fires at the start
  of `Player#tick`, after the client has saved the old position.
- **Orthographic** projection needs a mixin on `GameRenderer#getProjectionMatrix` and a bypass of
  a frustum loop that can otherwise freeze the game (fact 20).
- **Retina.** Picking must normalise by `Window#getScreenWidth/Height`; the framebuffer is 2× on
  Retina (fact 19).
- **Spectator powers** usable by a modified client: teleport to any entity, spectate by attacking,
  spectators seeing spectators, no server-side bounds
  ([design/judgement-performance.md §6.1](../design/judgement-performance.md)).

## Decision

- **Body: the player follows the camera.**
  - In RTS mode the commander's own player stays the camera entity, in spectator mode, positioned
    at the camera eye: in `PlayerTickEvent.Pre` per tick for position, in `ComputeCameraAngles` per
    frame for angles. No camera mixin.
  - Pans are capped at 5 blocks per tick (8 before S9).
  - Jumps (minimap, control groups, alerts) always go through the server as `camera_jump`,
    answered by a server teleport, even in singleplayer, so development exercises the dedicated
    server's checks.
  - Bodies in chunks that arrive after a jump are re-paired on `ChunkWatchEvent.Sent` (ADR-0016).
- **Cursor: a non-pausing full-window `RtsScreen` owns all input.** Every vanilla binding is inert
  by construction: attack, use, hotbar, inventory, drop, the spectator menu, scroll-to-fly-speed.
  - The screen forwards F1, F2, F3 and F11, opens chat, turns Escape into "cancel, else pause",
    and reopens after chat or the pause menu close.
  - Actions come from our own action layer (`core.client`, L1-tested), with `KeyMapping`s
    registered under an RTS `IKeyConflictContext` so players rebind them in vanilla Controls.
- **Projection: perspective** at 35–70° pitch (default 55°), FOV 55°, height 8–64 blocks above the
  focus ground. Orthographic stays an option for later (D-09, CAM-06).
- **The rig** (`core.client.CameraRig`) is pure maths and gives the same pose at any frame rate.
  Picking unprojects with the matrices from `RenderLevelStageEvent` and normalises the cursor by
  window size, never framebuffer size.
- **First person** is a game-mode switch.
  - Adventure mode at a validated drop point; back to spectator with the rig above the anchor.
  - `RtsScreen` closes and reopens.
  - Selection, control groups and orders survive the round trip.
  - Whether the avatar may fight is the match rule of D-08 (an observer is recommended for the
    MVP).
- **Hardening (M7)** against modified clients: a mixin drops `handleTeleportToEntityPacket` for
  match members; `ServerPlayer#attack` is a no-op for commanders; enemy commanders are hidden from
  each other; commander bodies are clamped to the match space after every accepted move.

**Spike S9 (CAM-01) confirms or replaces the body choice.** On a dedicated server with two clients
and about 1,000 tracked dummies, on a LAN and with internet-like latency, it compares the three
body strategies under pans at 10–60 blocks/s and 300-block jumps. Exit criteria:
- zero snap-backs while panning, apart from the known one-off warning per jump: the client's
  echo of a teleport can arrive in the same gap between ticks
  (ServerGamePacketListenerImpl.java:262);
- full coverage within 3 s after a jump;
- picks within one block at GUI scale 2 on Retina;
- `ChunkMap#move` ≤ 0.5 ms per panning commander per tick;
- the same scenarios pass once under Sodium and Iris.

**Fallbacks fixed in advance:**
1. A client camera entity with the server body teleported to the camera focus at most 5 times a
   second, plus the chunk-sent re-pair.
2. A body parked at the arena centre, for arenas up to 384².

## Consequences

- Streaming, tracking and vanilla's re-check follow the camera with no camera entity and no
  teleport protocol for pans.
- Each accepted movement packet costs one `ChunkMap#move` over all tracked entities, outside MSPT:
  about 0.3 ms per panning commander per tick at 1,000 entities *(estimate)*. Our handler timers
  and S9 measure it (budget C).
- With the screen open, other mods' key bindings are inactive in RTS mode, and our layer must
  forward the F keys itself.
- Terrain streams again as the camera pans, which costs bandwidth outside the entity budget (S9,
  S11).
- The commander is not in the world while commanding; first person is one key away.

## Alternatives considered

| Alternative | Why not |
|---|---|
| A client-only camera entity (Camera Lib, Freecam) with a parked body | Stops movement packets, so streaming and the re-check stay at the parked body; needs whole-arena view distance and a matching tracking range; no open world. Kept as fallback 2 |
| A camera entity with the server body teleported to the focus (C) | Fewer `ChunkMap#move` passes, but a camera entity, a teleport protocol and a pairing gap after jumps to fix. Kept as fallback 1, chosen if S9 measures the per-packet cost as too high |
| A server camera anchor via `ServerPlayer#setCamera` | Drags the player every tick and resets on Shift, the queue key |
| Release the mouse and cancel `InputEvent.MouseButton.Pre`, no screen (B) | Vanilla bindings stay live (spectator menu, scroll-to-fly, hotbar), so a list of drains to maintain, and every closed screen re-grabs the cursor; it does keep other mods' keys working |
| A non-spectator "commander" game state | The server replays movement with collisions and rubber-bands a camera gliding over terrain |
| Orthographic projection now | A projection mixin plus a frustum bypass, untested under Sodium and Iris |
