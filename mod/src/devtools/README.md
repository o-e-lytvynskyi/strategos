# The client scenario harness (L4)

Dev-only code that drives a real client: it never ships in the mod's jar. A scenario creates a
fresh flat world (or joins a server), sets up a scene on the integrated server, ticks through its
steps, takes PNGs and writes `result.json`; a watchdog ends a stuck run. A run opens a game window,
so on the owner's Mac it runs **only with the owner's consent**; CI runs it under Xvfb and Mesa
without one (`.github/workflows/scenario.yml`).

```
./gradlew :mod:runScenario -Pscenario=<name> [-Pcompat] [-Pscenario.jfr=<settings>]
```

- Scenarios are listed in `ScenarioRunner#scenarioNamed`. Output: `mod/build/scenario/out/`
  (`result.json` with `"ok"` and `"platform"`, the PNGs).
- `-Pcompat` adds the client set most players run (Sodium, Iris and others) to the run's `mods`.
- `-Pscenario.jfr=profile` (or a `.jfc` file) records the run into `mod/build/scenario/scenario.jfr`.

## Goldens (QA-18)

Every run ends with `:mod:scenarioDiff`, which compares the run's frames with the goldens of its
platform and scenario:

```
mod/src/devtools/goldens/<platform>/<scenario>/<frame>.png     approved goldens
mod/src/devtools/goldens/<platform>/<scenario>/goldens.json    optional: thresholds and masks
mod/src/devtools/goldens-proposed/<platform>/<scenario>/       proposals waiting for the owner
```

- **Platforms:** `macos`, and `linux-llvmpipe` for CI's Xvfb and Mesa. The same frame differs on
  92 % of its pixels between the two, so each has its own goldens. A run with Sodium (`-Pcompat`)
  adds `-sodium`, since Sodium shades and loads the far terrain its own way: its frames differ from
  vanilla's by about 1.2e-5, over the threshold.
- **Size:** frames are box-filtered to at most 640 pixels across before they are compared, the size
  goldens are kept at.
- **Measure:** the normalised mean squared error over RGB, 0 for the same picture and 1 for black
  against white, over the pixels outside the masks. A frame fails above its threshold, by default
  1e-5. `goldens.json` sets `threshold` for all frames and, under `frames.<name>`, a frame's own
  `threshold` and `masks` (`[x0, y0, x1, y1]` in golden pixels) for whatever moves between runs.
- **Output:** `mod/build/scenario/diff/`: `candidates/` holds the run's shrunk frames,
  `<frame>-diff.png` shows a failing frame's difference eight times as loud, and `report.json`
  lists every frame's error. A scenario with no goldens on its platform leaves candidates and
  passes.
- **Scenes for goldens** pin everything that varies between runs: a fixed anchor and seed, noon,
  no weather, the cursor mid-window, and `/tick freeze` once the units stand, so bodies draw at a
  fixed partial tick (`rts_goldens`).

### Approving goldens

Goldens change only with the owner's approval.

1. Record a candidate set: run the scenario three times. The runs must agree within the threshold,
   or what moves gets a mask, written down in `goldens.json` and the commit.
2. Propose it: copy `mod/build/scenario/diff/candidates/*.png` into
   `goldens-proposed/<platform>/<scenario>/`, and commit with what changed and why. For CI's
   platform, take the candidates from the job's `scenario-out` artifact. A proposal can be tried on
   CI before approval: run the `scenario` workflow with `goldens` set to
   `mod/src/devtools/goldens-proposed`.
3. The owner looks at the proposed frames and says yes or no.
4. On a yes: `git mv` the proposal into `goldens/<platform>/<scenario>/` (or, for the last run's
   candidates, `./gradlew :mod:approveGoldens -Pscenario=<name>`), and commit
   `Goldens: <scenario> on <platform>, approved by the owner on <date>: <why>`.

A change that alters approved frames on purpose needs new goldens the same way, in the same
commit as the change or the next one; until then the diff fails.
