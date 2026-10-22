# S3: client scenario harness

*Prepared with M0 T16 and run on 2026-10-22, in M0's client batch after the owner's consent
([D-22](../roadmap/M0-foundation.md#d-22--client-run-l4-consent-policy)). Spec:
[M0 §8.6](../milestones/M0-foundation.md#86-s3-and-s4-prepared-pending-consent-t16) and §7.7.*

## Question and exit criterion

Can scripted client scenarios run on the owner's Mac without disturbing them, and do they give
repeatable screenshots? The answer feeds D-22 (QA-06.3) and the L4 policy of ADR-0006 as amended
by ADR-0022.

Exit: no focus theft, or a working container route (a Linux container under Xvfb rendering the
same scenario).

## Setup (as built)

- `mod/src/devtools`, dev only, never in the jar: `ScenarioRunner` (a state machine on the client
  tick: title screen, a fresh flat creative peaceful world with seed 42, wait until every section
  is rendered, set the scenario up on the integrated server, tick it, write `result.json`, stop the
  client), with a watchdog that exits 1 after `strategos.scenario.timeoutSeconds` (default 180);
  screenshots after `RenderFrameEvent.Post`, synchronous.
- What makes a frame repeatable, each added after a run showed the need (Deviations):
  - everything is placed relative to a fixed anchor, (0, the heightmap's floor, 0), not to the
    player, whose spawn point moves within the spawn radius on every run;
  - the time is frozen at noon (`time set 6000`, no daylight or weather cycle, clear weather), so
    light and sky do not depend on how long the world took to load;
  - `sendCommandFeedback` is off, so the set-up commands print no chat lines into the frames;
  - the spectator camera is teleported to its pose and then held there on every client tick and
    every frame, with the mouse released: in a focused window the mouse turns a spectator's view;
  - the vignette is pinned at its limit under the open sky, 0: it fades by 1 % a client tick from
    full (Gui.java:1298–1303), so without the pin a frame depends on how many ticks the load took.
- Scenarios: `smoke` (a 16 × 16 match with five units at the anchor, the camera 6 blocks up and
  5.5 back at 45° pitch, PNGs at scenario ticks 20, 40 and 60, then the window's focus, content
  scale, window and framebuffer size, GUI scale, GL renderer and camera pose), `stall` (never
  finishes), `world_switch` (CORE-01.5) and `s4_units` ([S4](S4-client-frame-time.md)).
- `./gradlew :mod:runScenario -Pscenario=<name>`: game directory `mod/build/scenario`, synced
  from `mod/src/devtools/scenario-gamedir` (an `options.txt` without tutorials, sounds, clouds or
  vsync, render distance 5, GUI scale 2); the verdict is `mod/build/scenario/out/result.json` with
  `"ok": true`. `./gradlew verifyClient` runs the default scenario.
- The Mac: Apple M4 Pro, macOS 27.0.1, Temurin 21.0.12.1, NeoForge 21.1.264; two displays, the
  main one an external 2560 × 1440 monitor at scale 1, and the built-in Retina panel.
- The Linux route: `.github/workflows/scenario.yml`, dispatched by hand with a scenario name: an
  `ubuntu-24.04` runner (x86-64), `xvfb-run` with a 1920 × 1080 screen, Mesa's `libgl1-mesa-dri`,
  the same `runScenario` task; the PNGs and logs are uploaded as an artifact.

## Method

1. `./gradlew :mod:runScenario -Pscenario=smoke` on the Mac, in four batches while the harness
   was fixed: 10 runs, 3, 10 on the fixed anchor, and 2 with the held camera. For each run:
   whether the window had the focus, the framebuffer and content scale, and the PNGs compared
   pixel by pixel with the batch's first run (a pure-Python PNG decoder; a pixel "differs" if any
   channel does, and the pixels off by more than 8 levels in a channel are counted apart). The
   plan said ten runs; the owner then cut repeat runs of a static scene to two or three (D-22's
   amendment, 2026-10-22), which the last batch follows.
2. The same `smoke` on the Linux route: five runs, one before the repeatability fixes, two with
   the held camera, two with the vignette pinned as well.
3. `./gradlew :mod:runScenario -Pscenario=stall -Pscenario.timeoutSeconds=20` once.
4. `./gradlew :mod:runScenario -Pscenario=world_switch` with the held camera.
5. The production jar on a client: CI's release workflow (QA-08) runs mc-runtime-test on it.

## Results

**Focus.** The game window comes to the front and takes the keyboard focus when it opens: it
still had the focus at the end of 21 of 25 smoke runs (8 of 10, 2 of 3, 9 of 10 and 2 of 2 by
batch, [data/S3-smoke-runs.csv](data/S3-smoke-runs.csv)). Keystrokes typed meanwhile go to the
game; with the camera held and the mouse released they cannot move the view, but they are lost to
whatever the owner was typing in. Each run takes 11–16 s from the Gradle command to the window
closing (warm daemon).

**Rendering without the focus.** The four runs that ended without the focus wrote the same frames
as the others (run 4 of the anchor batch: 22 pixels off by more than 8 levels at tick 60, within
the batch's spread). Covered windows and other Spaces were not tried: that needs the owner at the
keyboard, and the Linux route makes it moot for unattended runs.

**Framebuffer.** Content scale 1.0 and a framebuffer equal to the window, 1280 × 720, in every
run: the window opens on the main display, an external monitor at scale 1. The Retina panel's 2×
was not exercised; `result.json` records the content scale, so a golden check can refuse a
mismatched scale.

**Repeatability on the Mac**, against each batch's first run
([data/S3-png-diff.csv](data/S3-png-diff.csv)):

| Batch | Tick | Pixels differing | Off by > 8 levels | Where |
|---|---|---|---|---|
| Fixed anchor, 9 pairs | 20 | 19 to 695,077 | 3 to 2,771 | the units; run 3's sky |
| | 40 | 15 to 670,957 | 0 to 85 | the units |
| | 60 | 10 to 637,131 | 0 to 107 | the units |
| Held camera, 1 pair | 20 | 40 | 21 | scattered |
| | 40 | 4 | 3 | the units |
| | 60 | **0** | **0** | identical |

Before the camera was held, most pairs differed in a quarter to three quarters of the frame by one
or two levels, in a radial pattern, and run 3's sky still changed at tick 20. With the camera
held, tick 60 is identical to the pixel. This pair predates the vignette pin, which on the Mac
had nothing to change: both loads took the same number of ticks.

**The Linux route** works: CI run [39742945559](https://github.com/o-e-lytvynskyi/strategos/actions/runs/39742945559)
rendered `smoke` under Xvfb on `llvmpipe (LLVM 20.1.2, 256 bits)` with `"ok": true`, the same
1280 × 720 framebuffer and GUI scale 2, and the later runs put the camera at the Mac's pose. A job
takes 2–3 minutes, and no window opens anywhere. Its frames differ from the Mac's in 92 % of the
pixels (another rasteriser), so goldens are per platform. The only log error is the narrator's
missing `flite` library, harmless. Pairs of runs:

| Harness | Runs | Tick 20 | Tick 40 | Tick 60 |
|---|---|---|---|---|
| Held camera | 39744041933, 39744431215 | 598,232 (126 over 8) | 550,829 (103) | 494,832 (90) |
| Vignette pinned too | 39745023650, 39745318713 | 50,007 (49,482) | 14,901 (11,684) | **115 (90), the units** |

- With the camera held, half the frame still differed by one to four levels in a radial pattern:
  the vignette, still fading when the frame was taken, after a load whose length in ticks varies
  on llvmpipe. Pinned, tick 60 differs only at the units.
- At ticks 20 and 40 the far terrain was still arriving in one run of the second pair (its top
  rows show sky where the other run shows ground): "every section rendered" can hold before the
  last chunks reach the client.
- The units differ by up to 123 levels in about 100 pixels: their idle arm swing follows the
  frame's partial tick, which depends on when the frame falls between ticks.

**Stall:** the watchdog ended the run with exit 1 after 20 s, and the task failed, as designed.

**world_switch** (CORE-01.5) passed with the held camera.

**The production jar:** release run [39697935330](https://github.com/o-e-lytvynskyi/strategos/actions/runs/39697935330)
found `strategos-0.0.1.jar` on a NeoForge 21.1.264 client under Xvfb, entered a world and finished
cleanly (mc-runtime-test 5.0.0).

## Analysis

- **Focus theft: yes.** Every Mac run takes the keyboard for about 15 s, so a Mac run needs the
  owner's consent and their hands off the keyboard, as D-22 (b) assumed.
- **A working route without a window: yes,** on CI under Xvfb and llvmpipe. The exit criterion
  is met by its second branch. A local container on the Mac was not needed: Docker is installed,
  but Minecraft 1.21.1's version manifest has no linux-arm64 LWJGL natives, and CI already gives
  the same result.
- **Repeatable screenshots: yes,** once the scene is pinned (anchor, time, chat, camera,
  vignette): on the Mac two runs agree to the pixel at tick 60, and on Linux everything but the
  units' animation agrees at tick 60. A golden is taken after the terrain has settled, at tick 60
  or later, with the units masked or their animation pinned: under `/tick freeze`, entities other
  than players are drawn at partial tick 1.0 (DeltaTracker.java:114–116, LevelRenderer.java:1040).

## What it decides

- **D-22 (QA-06.3):** the decided policy stands, with numbers: Mac runs in consent batches of
  about 15 s per run, a static scene repeated two or three times; goldens and every other check
  that needs no absolute frame time run on CI, which opens no window.
- **QA-18 (M2):** per-platform goldens (macOS on the M4 Pro, linux-llvmpipe on CI); a capture at
  tick 60 or later, after the chunks around the camera have all arrived; the units masked or
  frozen; a golden records its content scale and refuses another; three consecutive runs, per
  D-22's amendment.
- **QA-06.1 and 06.2** are met: the smoke scenario writes its PNGs and `result.json` in about
  15 s, the stall ends through the watchdog, and the production jar loads on a client in CI.

## Raw data

[data/S3-smoke-runs.csv](data/S3-smoke-runs.csv) (every Mac smoke run's `result.json`),
[data/S3-png-diff.csv](data/S3-png-diff.csv) (each pair, Mac and Linux, with the box of the pixels
off by more than 8 levels). The PNGs (1–2 MB each) are not committed; the CI runs keep theirs for
7 days.

## Deviations and follow-ups

- **The harness changed during the batch.** The first batch placed the scene at the player, whose
  spawn point moves within the spawn radius on every run, so its frames showed different ground;
  light shifted with the load time; chat lines from the set-up commands showed in the frames; the
  mouse of a focused window turned the spectator camera; on Linux the vignette was still fading.
  Each fix is in Setup.
- **Ten runs, then two.** Step 1 ran ten times as planned before the owner cut repeats of a
  static scene to two or three.
- **Not tried:** a covered window, another Space, the Retina panel at 2×, typing during a run, the
  Mac with the vignette pinned. None blocks the verdict: unattended runs go to CI.
- **Hidden windows** stay unproven; nothing needs them now.
