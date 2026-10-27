# Minecraft mods: build — Gradle, MDG, Spotless, ErrorProne (NeoForge 1.21.1)

Pitfalls of the build and of the test run; versions and the general toolchain —
[minecraft-mod.md](minecraft-mod.md). The reference is this repository: `build-logic/`,
`mod/build.gradle`.


## Decompiled sources: use `-sources.jar`, not `*sources*`

`./gradlew createMinecraftArtifacts` puts four jars into `build/moddev/artifacts/`. The mask
`*sources*` catches `neoforge-<ver>-client-extra-aka-minecraft-resources.jar` first
("re**sources**") — that one holds assets and data, not code. You need exactly
`neoforge-<ver>-sources.jar`: about 6.3k `.java` files (MC + NeoForge, Mojang names +
Parchment). Check every class, method and event against it before relying on it.

## A convention plugin with Spotless in `core` and `mod` — declare it in the root

Plugins from `includeBuild 'build-logic'` are loaded by a separate class loader for each project
with a different set of plugins. `mod` has ModDevGradle and `core` does not, so Spotless is
loaded twice, and its shared build service fails already at configuration: `Cannot set the value
of task ':mod:spotlessJava' property 'taskService' … loaded with …project-mod… using a provider …
project-core`. The fix: declare the convention plugin with `apply false` in the root
`build.gradle`. Then it, Spotless and ErrorProne are loaded once, in the parent class loader.

Also: Gradle 9 deprecated the space-separated property assignment (`exceptionFormat 'full'`);
Gradle 10 will remove it. Write it with `=`; methods like `events 'failed'` stay as they are.

## A plain library is not loaded in the dev runtime

On MC <= 1.21.8 MDG puts only mods and jars with `FMLModType` into the runtime.
`implementation project(':core')` compiles, but gives `ClassNotFoundException` in
`runClient`/`runGameTestServer`. The fix for your own code: merge the source set into the mod —
`neoForge { mods { mymod { sourceSet project(':core').sourceSets.main } } }` plus
`jar { from core.sourceSets.main.output }`. No `module-info.java` in core. `jarJar` +
`additionalRuntimeClasspath` is only for third-party libraries.

## Server run directory: `Sync` with `upToDateWhen { false }`

A `Sync` task that wipes the game directory (world, logs, crash-reports) before a run stays
UP-TO-DATE after the first run. Gradle does not see that the forked server wrote into the
synchronised directory. The next run starts with the old world and the old crash reports, and the
guard fails a healthy smoke run because of someone else's report. The fix is
`outputs.upToDateWhen { false }` on every such `Sync`.

Also: touching a client class on a dedicated dev server fails not with `NoClassDefFoundError`
but in NeoForge's `RuntimeDistCleaner`: "Attempted to load class net/minecraft/client/Minecraft
for invalid dist DEDICATED_SERVER".

## A backslash in an MDG `systemProperty` arrives doubled

MDG writes run properties into a VM-arguments file (`build/moddev/<run>RunVmArgs.txt`) and
escapes the backslash, and Java does not unescape it: the value `^perftests\.` (in Groovy
`'^perftests\\.'`) reaches the program as `^perftests\\.`, and the regex matches nothing. Avoid
the backslash in property values: use `[.]` for a dot.

## A variable named like a configuration silences `dependencies { name dep }`

`def localRuntime = configurations.create('localRuntime')`, and then in the block
`dependencies { localRuntime libs.x }`: Groovy takes the script variable (a `Configuration`
object) and calls `call(...)` on it — "Could not find method call() …". A branch under
`if (providers.gradleProperty(...).isPresent())` stays quiet until the property is set, so the
profile may not work for years. Write `add('localRuntime', libs.x)`.

## `spotlessApply` and compilation in one Gradle call — a race

- With the configuration cache Gradle runs independent tasks in parallel even inside one
  project. `compileJava` does not depend on `spotlessApply`, so `./gradlew spotlessApply
  compileJava` compiles while spotless rewrites the same files.
- Symptom: ErrorProne fails with its own exception on ordinary code (it was
  `StringConcatToTextBlock`: `NoSuchElementException`); a rerun passes.
- Format in one call, compile in the next.

## `:mod:test` (MDG `unitTest`) is a dedicated server

- The FML JUnit targets (`JUnitDevLaunchTarget`, `JUnitUserDevLaunchTarget`) return
  `Dist.DEDICATED_SERVER`. Client registration events — `RegisterKeyMappingsEvent`, GUI layers,
  client commands — never fire in L2 tests.
- There is nothing to check client registrations with there: only a list (here — the engine
  inventory, reconciled with the architecture table), and live — in a client run.

## ErrorProne `-Werror` catches what the compiler lets through

- `RenderLevelStageEvent.Stage` in NeoForge 21.1 is a class, not an enum: `!=` gives
  `ReferenceEquality`; compare with `equals`.
- Integer division assigned to a `float` is `NarrowCalculation`; a `continue` as the last
  statement of an if/else chain is `RedundantControlFlow`. In main these are build errors.
- NullAway in a neighbouring Gradle project (the mod against the core jar) does not see a
  JSpecify annotation on a nested type in a record component: `record Input(Outer.@Nullable
  Inner x)` is called from the mod as `@NonNull`, although a method with the same parameter
  `Outer.@Nullable Inner` is read correctly. The workaround is to pass such a value as a separate
  method parameter, not as a record component.

## `build/` in `.gitignore` hides the Java package `build`

- The line `build/` ignores any directory with that name, including a package `.../sim/build/`
  in `src`: `git add` of the directory silently skips it, everything builds locally, and CI fails
  with "package does not exist". The cure is an exception `!**/src/**/build/` under the `build/`
  line.
- A check before committing a new package: `git check-ignore -v <file>` and `git status
  --short` — the new files must be `A`, not missing.
- ErrorProne `ArrayRecordComponent`: an array as a record component is an error under `-Werror`;
  replace a record with an array by a plain immutable class with `equals` through
  `Arrays.equals`.

## Python document checks: pin UTF-8 and reject empty discoveries

`Path.read_text()` uses the host's default encoding. On a Windows host using cp1251,
UTF-8 roadmap headings can decode without an exception yet no longer match the parser.
A validator that only loops over discovered cards then reports success for zero cards.
Read repository documents with `encoding="utf-8"` and explicitly reject an empty discovery.
`tools/test_roadmap.py` reproduces the host-default mismatch and empty-document false pass;
both tests fail against the pre-M5 reader and pass with the explicit encoding and guard.

## Python bookkeeping on Windows needs UTF-8 mode

Run the roadmap and claims tools with `PYTHONUTF8=1` on a Windows host whose default code page
is not UTF-8. Roadmap headings contain Unicode punctuation; decoding them with a legacy code
page can silently match zero headings and print `0 cards checked, 0 problems`. Check that the
reported card count is plausible, not just the exit code. In PowerShell set
`$env:PYTHONUTF8='1'` before invoking the tools; this does not change repository data.

## Groovy: a line that starts with an operator is a new statement

In a Gradle Groovy script, `def x = a()` followed by a line `+ b()` is two statements: the
second is unary plus on `b()`'s result, and `x` holds only `a()`. `jmhCompare` concatenated
`JmhGate.problems(...)` and `JmhGate.missing(...)` this way from M0 to M5 T3c.1, so a benchmark
missing from the result was never reported; once one was missing, the gate died with
`No signature of method: java.lang.String.positive()` instead of naming it. Keep the operator at
the end of the first line (`a() +` then `b()`), and give a new gate check a case that must fail.
