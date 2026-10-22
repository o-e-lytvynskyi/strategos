# S3: client scenario harness — plan, pending consent

*Prepared with M0 T16 on 2026-10-22; not run. Every step opens a game window, which can take the
keyboard focus, so S3 waits for the owner's consent to M0's client batch
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
- Scenarios: `smoke` (five units at the player, a fixed spectator camera, PNGs at ticks 20, 40 and
  60, and the framebuffer size, GUI scale, GL renderer and camera pose), `stall` (never finishes),
  `world_switch` (CORE-01.5) and `s4_units` (S4).
- `./gradlew :mod:runScenario -Pscenario=<name>`: game directory `mod/build/scenario`, synced
  from `mod/src/devtools/scenario-gamedir` (an `options.txt` without tutorials, sounds, clouds or
  vsync, render distance 5, GUI scale 2); the verdict is `mod/build/scenario/out/result.json` with
  `"ok": true`. `./gradlew verifyClient` runs the default scenario.

## Method (to run)

1. `./gradlew :mod:runScenario -Pscenario=smoke` ten times. Each time record: whether the window
   took the focus and captured typing, whether it rendered while unfocused, covered or on another
   Space, the framebuffer size on the Retina panel; then compare the ten runs' PNGs.
2. Whether a Linux container under Xvfb renders the same scenario (the container route).
3. `./gradlew :mod:runScenario -Pscenario=stall -Pscenario.timeoutSeconds=20` once: the watchdog
   must end it with exit 1, so the task fails, as designed (QA-06.1).
4. The production-jar check (QA-06.2, §1.6 row 31): a separate NeoForge 21.1.264 client profile
   with `mod/build/libs/strategos-0.0.1.jar` in its `mods` folder reaches the title screen with
   Strategos in the mod list and no error in its log. CI's release workflow already does the
   headless half: run 39697935330 (2026-10-22) found `strategos-0.0.1.jar` on a NeoForge 21.1.264
   client under Xvfb, entered a world and finished cleanly.

## Results

Pending the owner's consent.

## What it will decide

D-22's policy for L4 runs (QA-06.3), and whether golden-image tests (QA-18, M2) can run on the
owner's Mac or need the container.
