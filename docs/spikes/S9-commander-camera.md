# S9: the commander camera on a dedicated server

*Written with M2 T1 on 2026-10-23, on the Mac the owner lent for the day. Spec:
[M2 §5](../milestones/M2-command.md#5-spike-s9-the-commander-camera-on-a-dedicated-server-t1--confirms-adr-0017);
card CAM-01.*

## Question and exit criterion

Does [ADR-0017](../adr/0017-camera-and-input.md)'s decided body, the commander's own player in
spectator mode moved to the camera eye each tick, hold on a dedicated server, or do its
pre-agreed fallbacks take over?

Exit (CAM-01): zero snap-backs while panning, at most a one-off echo warning per jump; full
coverage within 3 s of a 300-block jump; picks within one block at GUI scale 2 on Retina;
`ChunkMap#move` ≤ 0.5 ms per panning commander per tick; the same under Sodium and Iris. The report
also sets the camera's height limit.

## Setup

- **Machine:** Apple M4 Pro, 12 cores, its built-in Retina display; "Mac OS X 27.0.1" on aarch64
  as the JVM reports it. Server and client on the same Mac over loopback (M2 §1.6 row 1).
- **Software:** Temurin 21.0.12.1; NeoForge 21.1.264; the server with heap 4 GB. The `-Pcompat`
  client set: Sodium 0.8.13, Iris 1.8.14-beta.1, Lithium, ModernFix, FerriteCore, Entity Culling,
  ImmediatelyFast.
- **Tree:** 2026-10-23, after M1 T26 (commit `a24ec45`) with M2 T1 uncommitted.
- **Commands:** `./gradlew :mod:runSpike -Pspike=s9`, then, once it waits for the client,
  `./gradlew :mod:runScenario -Pscenario=s9 -Pscenario.timeoutSeconds=900 [-Pcompat]`. A run takes
  about five minutes. Data: [S9-results.json](data/S9-results.json) (the three runs below, server
  and client), [S9-env.json](data/S9-env.json).

## Method

- **The server** (`S9Camera`, `mod/src/gametest/.../spike/`) is the self-terminating spike server
  on a fresh flat world, view distance 10, at 20 ticks a second (no sprint). It makes a 384² match
  round the world's centre with 1,000 debug walkers in four blocks, half of them walking between
  random points, places 20 gold blocks for picking, and seats nobody, so both commanders see every
  body. A mock commander (QA-19's `MockViewer`, a spectator) pans back and forth at 8 blocks a tick
  through `handleMovePlayer`, as a second client's packets would (M2 §1.6 row 3).
- **The client** (`S9CameraScenario`, devtools) joins the server through the scenario runner, which
  now connects to a server when a scenario names one. The server makes it a spectator at the
  picking pose. The client sets its render distance to 10 (the harness's is 5, §1.6 row 6), GUI
  scale 2 and hides the HUD.
- **Picking:** for each gold block, the client projects the block's top centre with the frame's own
  projection and view matrices (`RenderLevelStageEvent`), puts the OS cursor there with GLFW,
  reads it back from GLFW in window points, unprojects it into a ray and clips it against the
  level. Then it samples the frame's pixel at that point, the window point times the framebuffer
  scale, which must be gold (§1.6 row 8).
- **Panning:** the decided body: in each client tick, before the player ticks and sends its
  position, the rig moves the player itself. It pans along X at 0.5, 1.5, 3, 5 and 8 blocks a tick
  (10, 30, 60, 100 and 160 blocks a second), then keeps 3 while the server jumps it. Any gap
  between where the rig put the player and where the player is counts as a correction (a snap-back
  seen by the client).
- **Jumps:** from 70–80 s after the join, the server teleports the client 300–337 blocks across the
  map, 20 times, each once the last one is covered. Coverage is the time until every chunk of the
  player's view is sent and every body within its tracking range (its view distance × 16, at most
  the body type's 160 blocks) is paired with it. The server counts its "moved too quickly" and
  "moved wrongly" warnings.
- **`ChunkMap#move`:** the server times `ServerChunkCache#move` for each commander once a tick, the
  call each accepted movement packet makes.

## Results

| Run | Snap-backs (warnings) | Coverage after a jump | `ChunkMap#move` p50 / p99, client · mock | Picks within 1 block · gold pixels |
|---|---|---|---|---|
| Vanilla client | 0 in 3,500 ticks and 20 jumps | worst 0.30 s, median 0.30 s; every body in range paired (29–617) | 0.10 / 0.22 ms · 0.09 / 0.21 ms | 20 / 20 · 20 / 20 |
| `-Pcompat`, first | 89, all at 8 blocks a tick; none at jumps | worst 0.30 s; all paired (75–602) | 0.09 / 0.19 ms · 0.09 / 0.17 ms | 20 / 20 · 20 / 20 |
| `-Pcompat`, second | 4, all at 8 blocks a tick; none at 5 or below, none at jumps | worst 0.30 s; all paired | 0.09 / 0.21 ms · 0.09 / 0.19 ms | 20 / 20 · 20 / 20 |

- **Picking is exact.** The window is 1280 × 720 points and its framebuffer 2560 × 1440 pixels:
  every pick hit its gold block (error 0), and every projected point showed gold in the frame.
  Normalising by the window, as ADR-0017 says, is right; a projection by framebuffer size would
  miss by half the screen.
- **Two runs before these taught two things** (§1.6 rows 5 and 6):
  - A jump teleported in `ServerTickEvent.Post` drew 2–3 "moved too quickly" each, 56 in 20 jumps.
    Each tick the connection takes the player's position as its first good one
    (ServerGamePacketListenerImpl.java:262), and the client's answer, reaching the server between
    ticks, was measured against the old position. Teleported in `ServerTickEvent.Pre`, before the
    connections tick, the same jumps drew none.
  - With the harness's render distance of 5 the client tracked bodies only within 80 blocks, so
    the coverage check, written for the server's 10, waited for bodies that could never pair.
- **The pan cap.** The server allows a packet to move `10 × √k` blocks from the tick's first good
  position, where `k` is the packets it has had since its last tick
  (ServerGamePacketListenerImpl.java:910–919). A client that runs two ticks in one frame sends two
  packets in one server tick, and at 8 blocks a tick the second is 16 blocks away, past 14.1. The
  vanilla client never bunched packets here, but with Iris and Sodium the frames sometimes took longer
  than a tick and the server snapped back 89 and 4 of the 200 packets at 8 blocks a tick. At 5 a tick
  even four bunched packets stay inside the limit, and none snapped back.
- **`ChunkMap#move`** costs 0.19–0.22 ms at p99 per commander with 600–1,000 bodies loaded, under
  ADR-0017's 0.3 ms estimate and the 0.5 ms exit.
- **Bodies come and go with the commanders' chunks.** A match without a map holds no tickets
  (M1 T4), so its bodies existed only where the two commanders loaded chunks: 600 to 1,000 of the
  1,000 units at a time (§1.6 row 7). Arena and map matches hold their whole space.

## Decision

**Exit met, with two amendments to the decided body.** ADR-0017 is Accepted:
- **Pans are capped at 5 blocks a tick** (100 blocks a second), not 8. At 10 to 60 blocks a second,
  the card's range, nothing ever snapped back. 8 a tick snaps back when a slower client runs two ticks
  in a frame, which a player's machine will do.
- **A jump teleports at the start of the next server tick.** CAM-03 queues each accepted
  `camera_jump` and applies it in `ServerTickEvent.Pre`. Then no echo warning occurs at all, not
  even the one-off warning the ADR expected.
- **Neither fallback is needed** (M2 §1.6 row 2).
- **The camera height** stays 8–64 blocks above the focus ground. At the default 55° pitch the
  farthest visible ground lies about 2.6 times the height away (04 §13.2). Up to about 60 blocks
  that is inside the 160-block tracking range at view distance 10. Above it, and for players with a
  shorter view distance, NET-08's far markers draw the units beyond the range. S9 measured at 24
  blocks only; the limit is computed, not measured.
