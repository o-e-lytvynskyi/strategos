# S12: content cycle

*Written with M1 T16 on 2026-10-23. Spec: [M1 §8.3](../milestones/M1-movement.md#83-s12-content-cycle-t16--confirms-adr-0012);
card DATA-03.*

## Question and exit criterion

Is [ADR-0012](../adr/0012-template-and-content-pipeline.md)'s balance loop (edit JSON, `/reload`,
look) fast enough, and does every content error say where it is?

Exit (spec §8.3): **`/reload` ≤ 2 s** end to end, and **every error names its file and JSON
path**. S12 confirms targets, not the decision.

## Setup

- **Machine:** Apple M4 Pro, 12 cores; "Mac OS X 27.0.1" on aarch64 as the JVM reports it.
- **Software:** Temurin 21.0.12.1, heap 4 GB; NeoForge 21.1.264; no performance mods.
- **Tree:** 2026-10-23, M1 T24 (commit `ff70fa7`) with T16 uncommitted.
- **Command:** `./gradlew :mod:runSpike -Pspike=s12`, twice, each about 6 s of server log from
  start to halt, ending with its `DONE` marker. The data: [S12-results.json](data/S12-results.json) (both
  runs), [S12-env.json](data/S12-env.json).

## Method

`S12ContentCycle` (`mod/src/gametest/.../spike/`) runs on the spike server, a dedicated server on
a fresh flat world:

1. **The content.** The spike's game directory (`mod/src/gametest/spike-s12-gamedir`) puts two
   folder packs in the world's `datapacks`:
   - `strategos_s12`: 20 templates in namespace `s12`. Four abstract bases over two levels
     (`template/base`, then `infantry`, `ranged` and `cavalry` on it), four mixins (`fast` and
     `heavy` with `$op` multiplies and adds, `elite` with a class edit, `tall`), and twelve units,
     each on a second-level base with zero to two mixins.
   - `strategos_s12_errors`: 13 files in namespace `s12e` that plant 12 errors, one per kind the
     pipeline knows: an unknown key, a wrong type, a negative speed, an unknown mobility, an
     unknown parent, an inheritance cycle (two files), an unknown mixin, an unknown `$op`, an
     unknown entity type, an unknown skin, a file that does not parse and a file that is no
     object.

   `server.properties` disables the errors pack when the world is created
   (`initial-disabled-packs`); the good pack is picked up as a new world pack. The packs are not
   in the gametest source set's resources, as the spec first had it: the dev runs load that
   source set as part of the mod, so the planted errors would refuse the bundled set in every
   GameTest run.
2. **The start.** The server loads the bundled templates and S12's at its start: 14 templates in
   the set, 12 of them S12 units (the 8 abstract bases and mixins compile into them).
3. **Three `/reload`s**, each as the command runs it: `MinecraftServer#reloadResources` with the
   selected packs, on the server thread, where it blocks until its future is done
   (MinecraftServer.java:1547). Each is timed end to end and must load the templates exactly once,
   through `TemplateReloadListener`, and keep the set's version.
4. **The template work alone**, three times: the server reads the packs' template files again
   and compiles, lints and installs them (`StrategosServer#packTemplates`, `#loadTemplates`).
5. **The errors reload:** one more timed reload with the errors pack selected too. It must be
   refused, keep the set, report each planted error at its file, JSON pointer and message, and no
   error that was not planted.

## Results

| Run | `/reload` 1 | `/reload` 2 | `/reload` 3 | with the errors pack | template work alone |
|---|---|---|---|---|---|
| 1 | 331.7 ms | 281.2 ms | 307.5 ms | 278.9 ms | 2.3, 1.7, 1.6 ms |
| 2 | 291.7 ms | 261.4 ms | 257.0 ms | 270.9 ms | 2.0, 1.5, 1.4 ms |

- **`/reload` takes 0.26–0.33 s**, a sixth of the target. The templates' share is under 1 %:
  2 ms for 24 files. The rest is vanilla's reload: recipes, loot tables, advancements, tags and
  functions.
- **Every planted error is reported at its place, and nothing else is**, in both runs:

| File (`data/s12e/strategos/templates/unit/`) | Pointer | Message |
|---|---|---|
| `garbled.json` | `/` | does not parse: java.io.EOFException: End of input at line 4 column 1 path $.Identity.name |
| `not_an_object.json` | `/` | a template is a JSON object |
| `bad_op.json` | `/UnitMotion/walkSpeed/$op` | unknown $op 'pow'; add, mul or replace |
| `cycle_a.json` | `/parent` | inheritance cycle s12e:unit/cycle_a → s12e:unit/cycle_b → s12e:unit/cycle_a |
| `missing_mixin.json` | `/mixins/1` | unknown mixin 's12:mixin/winged' in s12e:unit/missing_mixin → s12:mixin/winged |
| `missing_parent.json` | `/parent` | unknown parent 's12:template/legion' in s12e:unit/missing_parent → s12:template/legion |
| `negative_speed.json` | `/UnitMotion/walkSpeed` | expected a positive number, got -1 |
| `typo_key.json` | `/UnitMotion/walkSped` | unknown key 'walkSped'; known here: [walkSpeed, turnRate, mobility] |
| `unknown_mobility.json` | `/UnitMotion/mobility` | unknown mobility 'flying'; M1 has infantry |
| `wrong_type.json` | `/Body/width` | expected a number, got "wide" |
| `unknown_entity.json` | `/Body/entityType` | unknown entity type 's12e:dragon' |
| `unknown_skin.json` | `/Body/skin` | unknown skin 's12e:units/gold' |

  The two whole-file errors point at `/`; the parse error carries Gson's line, column and path.
  The cycle is reported once, at the file where it closes.
- **A refused reload keeps the set**, and the server logs the refusal with every problem; the
  operators online get the one-line outcome.

## Decision

**Exit met.** ADR-0012's targets hold: `/reload` in 0.33 s at worst against 2 s, and every error
names its file and JSON path. ADR-0012 stays Accepted, confirmed by this report. Nothing in the
numbers asks for an off-thread compile or an incremental reload; the template work would have to
grow about a hundredfold before it showed in the reload's time.
