# Minecraft mods: client, screenshots and frame measurement (NeoForge 1.21.1)

How to launch the client from a session and get repeatable frames and honest measurements. The
general toolchain is in [minecraft-mod.md](minecraft-mod.md), GameTest in
[minecraft-mod-gametest.md](minecraft-mod-gametest.md). Reference implementation: this repository,
`mod/src/devtools/` (the harness), `docs/spikes/S3-*.md` and `S4-*.md` (here `../spikes/`),
`.github/workflows/scenario.yml`.

## The client window steals focus

GLFW focuses the window on show by default (`GLFW_FOCUS_ON_SHOW`), and on macOS it also activates
the application. A Minecraft client started from a session intercepts the owner's keystrokes. It is
the same trap as with demos of menu-bar apps (the debug and acceptance pitfalls of the mac-menubar
notes). S3 measurement: in 21 of 25 runs the window still held focus at the end, a run takes ~15 s.
Open the client window (screenshot harness, `runClient`) only with the owner's consent; the owner
starts interactive sessions themselves. Without a window: Linux under Xvfb (below).

## A jar-in-jar mod on the classpath loses its libraries

FML loads a mod from `localRuntime` as "passed in as source" and does not unpack its JarJar:
Sodium 0.8 on the client fails with `NoClassDefFoundError` (`VertexSerializer`). Put client mods
with JarJar into the `mods` folder of the run directory (own configuration plus a copy before the
run), not onto the classpath. The server mods Lithium, ModernFix and FerriteCore work on the
classpath.

## A repeatable screenshot: what varies between runs

- The player's spawn point is random within the spawn radius. Build the scene from a fixed anchor
  (`getHeight(MOTION_BLOCKING_NO_LEAVES, 0, 0)`), not from the player.
- Light and sky depend on the load duration: `time set 6000`, `doDaylightCycle false`,
  `doWeatherCycle false`, `weather clear`.
- Setup commands write to chat: `gamerule sendCommandFeedback false` as the first command.
- The mouse of a focused window turns the spectator camera: `mouseHandler.releaseMouse()` and
  `player.moveTo(x, y, z, yaw, pitch)` on every client tick and frame.
- The vignette fades by 1 % per client tick (`Gui#updateVignetteBrightness`), so the frame depends
  on the number of load ticks (on llvmpipe half a frame, ±1–4 levels). Pin the public
  `gui.vignetteBrightness = 0` (the limit under open sky).
- On llvmpipe, distant terrain keeps loading after `hasRenderedAllSections`: take the shot from
  tick 60.
- The idle animation of mobs runs on the frame's partial tick (~100 pixels of difference). Use a
  mask or `/tick freeze`: non-players are then drawn with partial tick 1.0 (`DeltaTracker` and
  `LevelRenderer#renderLevel`).
- But a frozen body also waves its arms by age (`tickCount`), and each has its own: how many ticks
  it lived on the client before the freeze. A body frozen in the middle of interpolation
  (`lerpSteps`) stands not where the server put it. After the freeze, pin: `tickCount = 0`,
  `walkAnimation.update(0, 1)` twice, position and rotation to `lerpTargetX/Y/Z/YRot()`,
  `yBodyRot`/`yHeadRot` and their `O` values to the same, `setOldPosAndRot()`.
- A snapshot ordered for "the next frame" on a slow renderer (llvmpipe: a few frames per second) is
  taken after several client ticks. A scenario that steps on at once will capture the next action:
  a cleared selection, the middle of a turn. Change nothing until the snapshot is taken. On a Mac
  this is not visible, frames are fast.
- Your own server code that depends on `runsNormally()` is stopped by `/tick freeze`, while
  `ServerTickEvent` and entity tracking (`ChunkMap#tick`) go on. A deterministic frame with motion
  is a freeze at a given tick of your own simulation from `ServerTickEvent.Post`
  (`tickRateManager().setFrozen(true)`): the client gets exactly this state.
  A freeze can also be set by a condition ("first crack", "load in hands"): a match listener on the
  server checks it after every step and freezes on the very first such tick. With a deterministic
  simulation this is the same tick in every run (Strategos M3, nmse 0).
- A cursor for a scene: `glfwSetCursorPos` does not change `mouseHandler.xpos()` at once, and the
  motion event arrives on window polling and overwrites what was set. To put a thing "under the
  cursor" (a building ghost, a tooltip) at the right point, do not touch GLFW; set the private
  `MouseHandler.xpos/ypos` by reflection (units: `Window#getScreenWidth`). A point behind the
  camera projects beyond the window edge, and a cursor at the edge turns on RTS camera panning.

Result of S3: on an M4 Pro two runs matched pixel by pixel; on llvmpipe, everything except unit
animation. With pinned bodies (Strategos M2 T8), llvmpipe gave two runs without a single
difference. Sodium draws differently from vanilla (~1.2e-5 NMSE per frame): references are per
renderer, not only per platform. Mac and llvmpipe frames differ in 92 % of pixels: references are
per platform.

## `renderDistance().set(n)` from code does not reach the server

The server loads chunks and sends them to the player by `ServerPlayer#requestedViewDistance`
(`ChunkMap#getPlayerViewDistance` = clamp(requested, 2, server view distance)). The requested value
arrives only in the client settings packet (`ServerboundClientInformationPacket`), and
`OptionInstance#set` does not send it: the options screen does that when it closes. The integrated
server raises its own view distance after the option (`IntegratedServer#tickServer`), but still
sends the player as much as the player requested on join (in the harness, 5 from `options.txt`).
Result: the client "draws 16", but the ground ends after 5 chunks in a stepped circle, and frame
measurements run on a small world. After `set`, call `mc.options.broadcastOptions()`.

## `RenderFrameEvent` measures only the CPU part of a frame

`RenderFrameEvent.Pre/Post` wrap only `GameRenderer#render` (frame submission). The buffer swap and
GPU wait are outside. A full frame is the `Pre`→`Pre` interval, and with `framerateLimit` 260
("unlimited"), otherwise you measure the cap (the default was 60).

## A client tick without a frame limit waits for the GPU

Without a limit (`framerateLimit` 260) the GPU does not idle, and vanilla `TextureManager#tick`
in the client tick uploads animated textures (`glTexSubImage2D`) and waits until the GPU releases
the atlas. The wall time `ClientTickEvent.Pre`→`Post` grows with GPU load and is not about our
code: in an RTS view with an outline of 200 units the tick is 2.0–2.5 ms without a limit and
0.77 ms at 60 frames. Without units the tick is even longer than with them. Measure the frame cost
without a limit, and the tick in a separate window at the vanilla limit of 120. Tick timer
listeners are `HIGHEST` on `Pre` and `LOWEST` on `Post`, otherwise other people's work drops out.
Reference: this repository, scenario `rts_frame` (M2 §1.6 line 29).

## JFR on a client on a Mac

- The render thread on macOS is called `main`, not `Render thread`.
- `jdk.ExecutionSample` sees only Java code; time in GL and the driver is in
  `jdk.NativeMethodSample` (period 20 ms). Few samples in a tick with a large wall time mean the
  tick is stuck in native code.
- Frequent samples: `jfr configure --input profile --output s.jfc jdk.ExecutionSample#period=1ms`,
  then `-XX:StartFlightRecording=filename=…,settings=s.jfc`. The directory of the file must exist
  before the JVM starts, otherwise it does not start.
- `jfr print --json` on a 7 MB recording with a 1 ms period gave 4.6 GB. Read through
  `jdk.jfr.consumer` (`RecordingFile`) with a small program: `java Tool.java rec.jfr`.
- Line numbers in a stack come from bytecode and may not match the decompiled source, and after
  inlining a frame may name a neighbouring method: 127 samples "in `ServerChunkCache.getChunk`" in
  the client `animateTick` are almost certainly the client `ClientChunkCache`: not a single `join`
  in the stack, not a single such task in the server thread.

## The integrated server does not stop after a big warmed-up space

A harness scenario that created a 512² or 384² match with warm-up of all chunks of the space
(navigation pages) runs to the end, but the integrated server stop hangs in "Saving worlds": the
server thread spins in `ChunkMap#processUnloads` from `stopServer`, G1 is loaded, and the Gradle
task fails on timeout. With 256² and smaller it stops at once. The cause was not found; keep scenes
within 256².

## A client command from code: `ClientCommandHandler.runCommand`

`net.neoforged.neoforge.client.ClientCommandHandler.runCommand("<command without slash>")` runs a
command registered in `RegisterClientCommandsEvent` as if typed in chat; `false` means there is no
such client command. It is handy for a scenario when a `KeyMapping` with the `IN_GAME` context does
not fire because a screen is open.

## A client on Linux CI without a window

GitHub Actions `ubuntu-24.04` (x86-64): `apt-get install xvfb libgl1-mesa-dri`, then
`xvfb-run -a -s "-screen 0 1920x1080x24 +extension GLX +render -noreset" ./gradlew <run>`.
llvmpipe renders, the task takes 2–3 min. The narrator error "Failed to load library flite" is
harmless. A container on Apple Silicon (linux-arm64) does not work without extra effort: the 1.21.1
manifest has no LWJGL natives for linux-arm64.

## Client interpolation turns a step into a ramp

The client pulls an entity's position to each packet over 3 steps (`lerpTo`), and packets come every
`updateInterval` ticks. With an interval of 3, a server body's climb of one block in 2 ticks is
drawn by the client as a smooth ramp over about 3 ticks of travel: Y starts changing before the edge
and ends after it, X and Y move together. There are no jumps, but half a block from the edge the
feet move away from the top of the step by up to 1/6 block. Measure a body's landing by the client
Y (the scenario's `result.json`); the server one is exact. Reference: this repository, spike S10
(`docs/spikes/S10-kinematic-fidelity.md`, here `../spikes/S10-kinematic-fidelity.md`).

Drawing: HUD, particles, camera, renderer layers are in
[minecraft-mod-client-render.md](minecraft-mod-client-render.md).
