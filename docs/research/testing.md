# Strategos: testing, QA and developer workflow (NeoForge 1.21.1, Apple Silicon, AI-assisted)

**Scope.** How to make every intermediate result of a large NeoForge 1.21.1 mod checkable by machine, for a solo developer working with Claude Code on an M4 Pro Mac and on GitHub Actions.

**How this was checked (2026-10-20).** I read these primary sources:
- Maven metadata.
- The ModDevGradle (MDG) source tree, at commit 2026-09-29.
- The decompiled Minecraft 1.21.1 + NeoForge 21.1.262 sources produced by the MDK build (`scratchpad/mc-src-1.21.1`).
- The source jars of FancyModLoader (FML) 4.0.45 and `testframework` 21.1.264.
- The CI and build files of AE2, GuideME, Create and Fabric API.

I ran no Gradle builds, as instructed. Anything marked *(inference)* or *(estimate)* was reasoned from source, not executed. A second, independent fact-check pass was made the same day; its corrections are inline and summarised in **Verification notes** at the end.

---

## 0. Bottom line

1. **A seven-level test pyramid.** Every level is a Gradle task. Every level ends on its own with a timeout, so the assistant can run all of them. The levels:
   - static analysis
   - unit tests for the Minecraft-free `core`
   - JUnit with FML bootstrapped
   - headless GameTests
   - a dedicated-server boot smoke test
   - scripted client screenshots
   - benchmarks

   On top of that comes manual multiplayer play at milestones.
2. **Effect on the loader choice: small, slightly in NeoForge's favour.**
   - MDG has built-in JUnit integration (`neoForge.unitTest`).
   - NeoForge publishes `testframework` for almost every build: 254 of the 259 21.1.x builds on its Maven have one; 21.1.236, .237, .240, .245 and .246 do not. 21.1.264 (2026-10-19) has one.
   - Fabric's client gametest API, the only ready-made client-test framework, first shipped in Fabric API 0.112.1+1.21.4 (2024-12-16). The 1.21.1 line, latest 0.116.17+1.21.1 (2026-09-01), never got it.
   - So **on 1.21.1 neither loader has automated client tests**. You build a harness either way.
3. **`core` is a plain-Java subproject merged into the mod.** Use MDG `mods { strategos { sourceSet project(':core').sourceSets.main } }` for dev runs, and `jar { from core output }` for the artifact. Do not use jarJar or Shadow for first-party code.
4. **GameTests are registered through one `@GameTestGenerator` that you own.** It gives you:
   - subset filtering
   - stable names
   - correct retry semantics. In 1.21.1 the vanilla `attempts` and `requiredSuccesses` values are swapped.
   - SNBT arenas kept in git
   - a JUnit-XML reporter. 1.21.1 writes no report by default.

   The headless server ticks without sleeping. Once tests start, its exit code is the number of failed required tests. Two failures happen before that point: an empty test set, and a server-side mod-loading error. Both are caught in the server's `main()` and only logged, so the process very likely exits 0 *(inference from source)*. The build must therefore also fail when the report is missing or empty.
5. **Visual checks use a dev-only client "scenario" harness.** A system property selects a scenario. The harness builds a deterministic world and options, runs scripted steps, writes framebuffer PNGs plus JSON, and exits.
   - On the Mac the window is real: macOS has no headless GL path for this stack.
   - On CI it runs under Xvfb with Mesa llvmpipe. That is the same technique AE2 uses on GitHub Actions for its 1.21.1 guide export.
   - Claude Code reads the PNGs directly. Golden images are compared per platform.
6. **Code-quality gates:**
   - Spotless with palantir-java-format.
   - ErrorProne 2.50.0 with NullAway 0.14.2 in JSpecify mode.
   - ArchUnit rules that keep client code out of common code.
   - Checkstyle and PMD are not worth adding.
7. **Do not use jqwik ≥ 1.10 in an AI-assisted repo.** It ships an "Anti-AI Usage Clause" and output aimed at agents. Release 1.10.0 tried to get coding agents to delete tests ([LWN](https://lwn.net/Articles/1075315/)).
8. **CI is one Linux job on GitHub Actions.** Set `cache-provider: basic`. The default "enhanced" cache of `setup-gradle` v6 is proprietary and only a "Free Preview" for private repos.

---

## 1. Baseline versions (verified 2026-10-20)

| Component | Version (date) | License | Note |
|---|---|---|---|
| NeoForge 1.21.1 | 21.1.264 (2026-10-19); the MDK pins 21.1.262 (2026-10-15) | LGPL-2.1 | The 21.1 line gets builds in bursts: 10 builds between 2026-10-03 and 2026-10-19, but only 5 in August–September 2026 ([maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)) |
| FML / modlauncher / Mixin | FML 4.0.45, modlauncher 11.0.5, sponge-mixin 0.16.4+mixin.0.8.7, MixinExtras 0.5.5 | FML LGPL-2.1 / modlauncher LGPL-3.0-only / Mixin and MixinExtras MIT | From the `neoforge-21.1.262.module` metadata (the same in 21.1.264) |
| `net.neoforged:testframework` | 21.1.264 (2026-10-19) | LGPL-2.1 | A mod jar (`modId="testframework"`). Published for most 21.1 builds, not all |
| ModDevGradle | 2.0.148 (2026-09-29, latest) | LGPL-2.1 | [Plugin Portal](https://plugins.gradle.org/plugin/net.neoforged.moddev) |
| Gradle | MDK wrapper 9.2.1 (2025-11-17); current release 9.8.2 (2026-10-19) | Apache-2.0 | 9.x runs on Java 17–25; Java 25 needs ≥ 9.1.0 ([compat matrix](https://docs.gradle.org/9.2.1/userguide/compatibility.html)) |
| Minecraft 1.21.1 runtime | Java 21; LWJGL 3.3.3 including `natives-macos-arm64`; fastutil 8.5.12, Guava 32.1.2-jre, Gson 2.10.1, JOML 1.10.5 | – | [version JSON](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json). No `natives-linux-arm64` is listed, so client runs on CI need x64 Linux |
| Temurin 21 (macOS aarch64) | 21.0.12.1 (2026-08-19) | GPL-2.0+CPE | [Adoptium API](https://api.adoptium.net/v3/assets/latest/21/hotspot) |
| JUnit | 5.14.4 (2026-04-26); 6.1.3 also exists (2026-08-07) | EPL-2.0 | Recommend 5.14.x (see §4) |
| AssertJ | 3.27.7 (2026-01-24) | Apache-2.0 | |
| ArchUnit | 1.5.1 (2026-09-25) | Apache-2.0 | |
| JMH | 1.37 (2023-08-03, still latest) | GPL-2.0+CPE | |
| `me.champeau.jmh` | 0.7.3 (2025-01-30) | Apache-2.0 | The "Support Gradle 9" PR was merged 2026-07-19 but no newer release is on the Portal. That PR mostly upgrades the plugin's own build and test matrix; whether 0.7.3 actually fails on Gradle 9.2.1 is unverified |
| Spotless Gradle plugin | 8.10.4 (2026-10-08) | Apache-2.0 | Needs JRE 17+ and Gradle 8.1+ (per CHANGES.md 8.1.0; the README still says 7.3) |
| palantir-java-format / google-java-format | 2.102.0 (2026-10-05) / 1.37.0 (2026-10-01) | Apache-2.0 | |
| ErrorProne / `net.ltgt.errorprone` | 2.50.0 (2026-06-10) / 5.1.1 (2026-08-25, Gradle Plugin Portal) | Apache-2.0 | ErrorProne ≥ 2.43 needs JDK 21+ to run ([releases](https://github.com/google/error-prone/releases)) |
| NullAway / JSpecify | 0.14.2 (2026-09-25) / 1.0.1 (2026-07-29) | MIT / Apache-2.0 | |
| spark (NeoForge 1.21.1 build) | 1.10.124 (2025-02-23) | GPL-3.0-only | The jar bundles `spark-native/macos/libasyncProfiler.so` (a universal x86_64 + arm64 binary) |
| async-profiler | v4.6 (tag commit 2026-10-12; GitHub release published 2026-10-19) | Apache-2.0 | Official support for macOS x64 and arm64. On macOS only the `itimer` CPU engine works (see §9) |
| mod-publish-plugin | 2.2.1 (2026-09-21) | MIT | 2.0.0 requires Gradle ≥ 9.0 |
| mc-runtime-test | 5.0.0 (2026-10-12) | MIT | Runs a real client in CI via HeadlessMC + Xvfb; NeoForge 1.21.x is supported |
| GitHub Actions | checkout v7 (7.0.1), setup-java v6 (6.1.0, 2026-10-14), gradle/actions v6 (6.4.0), upload-artifact v7 (7.0.2) | MIT | The MDK workflow still pins v4 of checkout, setup-java and setup-gradle; it has no upload step |

---

## 2. Test levels and gates

| Level | What it proves | Tool / Gradle task | Speed on the M4 Pro *(estimate)* | Who runs it |
|---|---|---|---|---|
| L0 static | Formatting, compile, bug patterns, null-safety, architecture rules | `spotlessCheck`, `compileJava` (ErrorProne + NullAway), `:mod:archTest` | 10–60 s incremental | Assistant after every edit batch; CI |
| L1 core unit | Simulation logic, determinism, invariants | `:core:test` (JUnit 5 + AssertJ) | < 10 s | Assistant, often (TDD); CI |
| L2 MC unit | Registries, codecs, data, payload round-trips with Minecraft bootstrapped | `:mod:test` (MDG `unitTest`, FML JUnit) | 15–40 s, most of it the one-time bootstrap | Assistant on adapter or data changes; CI |
| L3 GameTest | Behaviour inside a real server world | `:mod:runGameTestServer` | 40–120 s | Assistant before every commit; CI |
| L4 client scenario | HUD, camera, rendering: screenshots plus JSON | `:mod:runScenario -Pscenario=…`, `:mod:scenarioDiff` | 45–120 s per batch | Assistant on any client change; CI (Linux goldens) |
| L5 server boot smoke | Common code does not touch client classes; mod loads on a dedicated server | `:mod:runServerSmoke` | 20–40 s | Assistant before commit; CI |
| L6 performance | No regression on hot paths; tick-time budgets | `:core:jmh`, GameTest `perf` batch | minutes | Assistant when touching hot paths; owner approves new baselines |
| L7 manual / production | Real multiplayer feel; the production jar | Dedicated server + 2 clients; mc-runtime-test on release tags | – | Owner, at milestones and releases |

Consistent with how the owner already works, the assistant only runs tasks that terminate on their own:
- the GameTest server exits by itself. Its exit code alone is not a verdict: an empty test set very likely exits 0 (§6.1), so the task also checks the report.
- the scenario harness has a watchdog;
- the server smoke run halts after it starts, and its verdict comes from a marker file, because a crashed dev server very likely exits 0 (§8);
- every such task also gets a Gradle `timeout`.

Interactive `runClient` and `runServer` sessions are started by the owner.

---

## 3. Build layout: Minecraft-free `core` plus `mod`

### 3.1 Layout

```
strategos/
  settings.gradle                     includes core, mod; foojay; includeBuild build-logic
  gradle/gradle-daemon-jvm.properties toolchainVersion=21 (see §12)
  build-logic/                        convention plugin: toolchain, Spotless, ErrorProne/NullAway
  core/                               java-library, NO Minecraft; src/main, src/test, src/jmh
  mod/                                MDG; depends on :core
    src/main        common + client code that ships
    src/test        FML-bootstrapped JUnit (L2)
    src/gametest    GameTests + helpers (dev only, never in the jar)
    src/gametest/structures/*.snbt    test arenas, diff-friendly
    src/devtools    client scenario harness (dev only)
    src/archTest    ArchUnit rules (plain JVM)
    src/scenario/golden/<platform>/<scenario>/*.png
```

### 3.2 Wiring `core` into the mod (exact MDG mechanics)

**Verified facts:**
- **Plain libraries are not loaded in runs on 1.21.1.** For Minecraft ≤ 1.21.8, MDG says "external dependencies will only be loaded in your runs if they are mods … or if they have the `FMLModType` entry". Anything else gives `ClassNotFoundException` unless it is added to `additionalRuntimeClasspath` ([MDG README](https://github.com/neoforged/ModDevGradle)).
- **What MDG does for 1.21.1.** It writes a per-run `legacyClasspath` file holding only the Minecraft libraries plus `additionalRuntimeClasspath` (`ModDevRunWorkflow`, `VersionCapabilitiesInternal`). FML's `UserdevLocator` does also scan classpath jars that carry a `MANIFEST.MF`, but it does not load a jar that has neither `neoforge.mods.toml` nor `FMLModType`. So `implementation project(':core')` **alone breaks dev runs** (consistent with the README; not executed).
- **Source sets from other projects are supported.** `mods { … sourceSet(x) }` accepts source sets from another project: `RunUtils.findSourceSetProject` walks all projects ("will break with cross-project isolation, but that's expected"). Each source set's output is passed to FML as `-Dfml.modFolders=strategos%%<dir>`. IntelliJ runs get the IDE output folders, for both delegated and native builds.

`mod/build.gradle` (core wiring only; the full file is in §6–§11):
```groovy
evaluationDependsOn(':core')
def core = project(':core')

neoForge {
    mods {
        strategos {
            sourceSet sourceSets.main
            sourceSet core.sourceSets.main     // core classes become part of the mod module in every run
            sourceSet sourceSets.gametest      // dev only
            sourceSet sourceSets.devtools      // dev only
        }
    }
}
dependencies {
    implementation core                         // compile classpath, IDE, tests
}
tasks.named('jar', Jar) {
    from core.sourceSets.main.output            // ship core classes inside the mod jar (same module as in dev)
}
```

| Approach | Dev runs | Production jar | Classloading | Verdict |
|---|---|---|---|---|
| **Merge** (`mods{}` + `jar.from(core output)`) | Core is part of the mod's folder group: one module in the GAME layer, transformable | Single jar, single module | Dev and prod identical; no split packages possible | **Recommended** |
| `jarJar project(':core')` + `additionalRuntimeClasspath project(':core')` | Core sits on the legacy classpath (not transformable, different loader) | Nested library jar, its own module (name from `Automatic-Module-Name` or prefixed filename) | Dev and prod differ; split packages are fatal; version-range rules apply | Only for third-party libraries |
| Shadow (`com.gradleup.shadow` 9.6.1) | Still needs the `mods{}` merge | Merged jar, optional relocation | Extra plugin; MDG jar/jarJar wiring has to be redone | Pointless for first-party code (no reobf on 1.21.1) |

For third-party libraries (verified syntax):
```groovy
jarJar(implementation('org.example:lib')) { version { strictly '[1.2,2.0)'; prefer '1.2.3' } }
additionalRuntimeClasspath 'org.example:lib:1.2.3'
```

**Pitfalls to encode as rules:**
- **No `module-info.java` in `core`.** Once merged, it would become the mod's module descriptor *(inference)*.
- **Give `core` its own package root** (`dev.strategos.core..`), so a later move to jarJar cannot split packages.
- **Limit `core`'s dependencies:**
  - Only the JDK, plus libraries Minecraft 1.21.1 already ships, as `compileOnly` at exactly those versions (e.g. fastutil 8.5.12).
  - Anything else must be jarJar'd *and* added to `additionalRuntimeClasspath`.
- **Prove the jar in CI.** Run mc-runtime-test with the built jar in a real NeoForge client (§11). Packaging mistakes (missing core classes) only show up in production.

### 3.3 Convention plugin (shared by `core` and `mod`)

`build-logic/build.gradle`:
```groovy
plugins { id 'groovy-gradle-plugin' }
repositories { gradlePluginPortal(); mavenCentral() }
dependencies {
    implementation 'com.diffplug.spotless:spotless-plugin-gradle:8.10.4'
    implementation 'net.ltgt.gradle:gradle-errorprone-plugin:5.1.1'
}
```
`build-logic/src/main/groovy/strategos.java-conventions.gradle`:
```groovy
import net.ltgt.gradle.errorprone.CheckSeverity

plugins { id 'java'; id 'com.diffplug.spotless'; id 'net.ltgt.errorprone' }

java.toolchain.languageVersion = JavaLanguageVersion.of(21)
repositories { mavenCentral() }
dependencies {
    errorprone 'com.google.errorprone:error_prone_core:2.50.0'
    errorprone 'com.uber.nullaway:nullaway:0.14.2'
}
tasks.withType(JavaCompile).configureEach { t ->
    t.options.encoding = 'UTF-8'
    t.options.compilerArgs += ['-XDaddTypeAnnotationsToSymbol=true'] // NullAway JSpecify mode on JDK 21.0.8+
    t.options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = '.*/(build/generated|mixin)/.*'
        check('NullAway', t.name == 'compileJava' ? CheckSeverity.ERROR : CheckSeverity.OFF)
        option('NullAway:OnlyNullMarked', 'true')
        option('NullAway:JSpecifyMode', 'true')
    }
}
spotless {
    java { target 'src/*/java/**/*.java'; palantirJavaFormat('2.102.0'); formatAnnotations() }
}
tasks.withType(Test).configureEach {
    useJUnitPlatform()
    testLogging { events 'failed'; exceptionFormat 'full' }  // compact, readable failures for the assistant
}
```

---

## 4. L1: core unit, invariant and determinism tests; JMH

**Libraries.**
- **JUnit Jupiter 5.14.4 + AssertJ 3.27.7.** Stay on 5.14.x. Everything in this ecosystem is built on Platform 1.x:
  - AE2's 1.21.1 build uses 5.10.2;
  - NeoForge's own `junit-fml` 4.0.45 imports `junit-bom` 5.10.2;
  - jqwik 1.10.x and ArchUnit 1.5.1 depend on Platform 1.14.4.

  JUnit 6 offers nothing you need.
- **Gradle 9 requirement.** Declare `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`, as the MDG README does. Gradle 9.0 removed the automatic loading of test-framework implementation dependencies.

**jqwik — decision-relevant:**
- Since 1.10, the [release notes](https://jqwik.net/release-notes.html) say: "Use of jqwik >= 1.10 with coding agents is strongly discouraged" and "This project is not meant to be used by any 'AI' coding agents at all".
- 1.10.0 (2026-05-25) printed an instruction telling agents to delete jqwik tests and code ([LWN](https://lwn.net/Articles/1075315/)).
  - The author has withdrawn it. The release notes say "You should no longer use release 1.10.0", and an LWN reader reports it is gone from the GitHub releases page.
  - It is **still downloadable from Maven Central**.
  - 1.10.1 (2026-05-29) replaced it with a modified agent-directed stdout line, documented as the "Anti-AI Usage Clause".
  - The user guide's `jqwik.hideAntiAiClause` switch only hides that line in terminal emulators. Captured stdout, which is what an agent reads, always contains it.
  - The POM licence is still EPL-2.0.
- This is a supply-chain hazard aimed squarely at this workflow. Do not depend on jqwik ≥ 1.10.
- If you want its shrinking, the last release before the clause is 1.9.3 (2025-06-07, EPL-2.0, built on Platform 1.13.1). Pin it with `strictly` and a Dependabot ignore. I recommend no PBT library at all. For an RTS simulation the valuable properties are better written as **seeded invariant runs**:

```java
@ParameterizedTest @MethodSource("seeds")
void economyConservesResources(long seed) {
    var sim = Simulation.newMatch(MatchConfig.small(), seed);
    var commands = RandomCommands.stream(new SplittableRandom(seed)).limit(5_000).toList();
    for (var c : commands) { sim.apply(c); sim.tick(); }
    assertThat(sim.totalResourcesIncludingSpent()).as("seed=%d", seed).isEqualTo(sim.initialResources());
}
static LongStream seeds() { return LongStream.range(0, 200); }

@Test void deterministicReplay() {   // same seed + same commands => identical state hash
    assertThat(run(42).stateHash()).isEqualTo(run(42).stateHash());
}
```
Print the seed in every failure message. That alone covers 90% of what shrinking would give you *(estimate)*.

**JMH, without a plugin.** `me.champeau.jmh` 0.7.3 predates the merged Gradle 9 support PR (merged 2026-07-19, unreleased). Whether 0.7.3 actually breaks on Gradle 9.2.1 is unverified; this setup avoids the question. Use a source set plus `JavaExec`:
```groovy
sourceSets { jmh }
configurations { jmhImplementation.extendsFrom implementation; jmhCompileOnly.extendsFrom compileOnly }
dependencies {
    jmhImplementation sourceSets.main.output
    jmhImplementation 'org.openjdk.jmh:jmh-core:1.37'
    jmhAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
    jmhRuntimeOnly 'it.unimi.dsi:fastutil:8.5.12'
}
tasks.register('jmh', JavaExec) {
    group = 'verification'
    classpath = sourceSets.jmh.runtimeClasspath
    mainClass = 'org.openjdk.jmh.Main'
    def include = providers.gradleProperty('jmh.include').orElse('.*')
    def result = layout.buildDirectory.file('reports/jmh/result.json')
    argumentProviders.add({ [include.get(), '-f', '2', '-wi', '3', '-i', '5', '-rf', 'json',
                             '-rff', result.get().asFile.absolutePath] } as CommandLineArgumentProvider)
    doFirst { result.get().asFile.parentFile.mkdirs() }
}
```

**Benchmarking hygiene on Apple Silicon:**
- macOS cannot pin threads, and benchmarks can land on efficiency cores, so expect ±5–10% noise *(inference)*.
- Benchmark on mains power, with nothing heavy running.
- Compare against a baseline JSON taken in the same session, or one committed per machine (`perf/baseline-m4pro.json`).
- Gate at more than 15% regression.
- `-prof gc` works on macOS. `perfasm`/`perfnorm` need Linux `perf`. async-profiler can be attached via `-prof async:libPath=…`. On macOS add `;event=itimer`, because the default `cpu` event falls back to wall-clock sampling there (§9).

---

## 5. L2: Minecraft-bootstrapped JUnit (`neoForge.unitTest`)

**How it works (verified in source):**
- `unitTest { enable(); testedMod = mods.strategos }` makes MDG configure the Gradle `test` task with NeoForge's `junit` run type: launch target `forgejunitdev`, working dir `build/minecraft-junit`, `-Dfml.junit.argsfile=…`, plus the mod folders. MDG also adds the `test` source set to the tested mod (`RunUtils.buildModFolders`).
- NeoForge's `modDevTestFixtures` variant pulls in `net.neoforged.fancymodloader:junit-fml:4.0.45`. Its `LauncherSessionListener` (`JUnitService`, via `LaunchWrapper`) calls `BootstrapLauncher.unitTestingMain(args)` once per JVM, and sets the thread context classloader to the game's transforming loader. Tests therefore run inside the game's module layer, with mixins applied.
- The launch handler runs `net.neoforged.neoforge.junit.JUnitMain`:
  - `SharedConstants.tryDetectVersion()`
  - `Bootstrap.bootStrap()`
  - `ServerModLoader.load()`, the full mod lifecycle with **Dist = DEDICATED_SERVER**
  - then it **disables dist cleaning**, so client classes can be loaded. Classes already loaded during mod construction stay dist-cleaned.
- **There is no server and no world** unless you ask for one.
- `testframework`'s `EphemeralTestServerProvider` (a JUnit `ParameterResolver`) builds a lazy, JVM-wide singleton `MinecraftServer`. Its Javadoc says not to touch the world: use GameTests for that.
  - It loads data packs (tags, recipes, data registries) but never loads a level: its `initServer()` never calls `loadLevel()`, even though the Javadoc speaks of a "void overworld".
  - It enables **every available pack and every feature flag** (`FeatureFlags.REGISTRY.allFlags()`), so experimental content is on.
  - Once started, its server thread ticks with no sleep (`waitUntilNextTick()` just runs tasks) for the rest of the test JVM.

```groovy
dependencies {
    testImplementation platform('org.junit:junit-bom:5.14.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core:3.27.7'
    testImplementation 'net.neoforged:testframework:21.1.264'   // keep in lockstep with neoForge.version (most, not all, 21.1 builds have one)
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
neoForge { unitTest { enable(); testedMod = mods.strategos } }
tasks.named('test', Test) { maxParallelForks = 1 }   // each fork would bootstrap the game again
```
```java
@ExtendWith(EphemeralTestServerProvider.class)
class PayloadCodecTest {
    @Test void unitOrderRoundTrips(MinecraftServer server) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        var in = new UnitOrderPayload(List.of(7, 9), OrderType.ATTACK_MOVE, new BlockPos(1, 64, 2));
        UnitOrderPayload.STREAM_CODEC.encode(buf, in);
        assertThat(UnitOrderPayload.STREAM_CODEC.decode(buf)).isEqualTo(in);
        assertThat(buf.readableBytes()).isZero();
    }
}
```

**Good fits:**
- Every `Codec` and `StreamCodec` (round-trip, plus a payload-size budget).
- Data-driven unit and tech definitions loading.
- Registry completeness: every block, item and entity has a lang key, a model and loot. The server loads no client resources, so model checks must read the asset files directly.
- Tag sanity.
- Pure client math that only references Minecraft types, never GL.

**Limitations:**
- One bootstrap per JVM. Global state persists across test classes; you must reset your own singletons.
- `Dist.CLIENT` subscribers and client setup events never run.
- No GL or window.
- No world: use GameTests.
- On macOS, MDG also adds `-XstartOnFirstThread` to this JVM (`PrepareTest.isClientDistribution()` returns `true`). Keep AWT/ImageIO work (image diffing) out of these tests, or set `java.awt.headless=true`.
- Speed is not measured. My *estimate*: 5–15 s bootstrap, plus 5–10 s the first time the ephemeral server is used.

---

## 6. L3: GameTests on 1.21.1

### 6.1 Verified mechanics

- **Registration** (`GameTestHooks.registerGametests`):
  - First the mod-bus `RegisterGameTestsEvent` fires (`event.register(Class|Method)`).
  - Then every class annotated `@GameTestHolder` is scanned and *all* its methods are added.
  - Each method goes to `GameTestRegistry.register(method, enabledNamespaces)`. Only `@GameTest` and `@GameTestGenerator` methods count, plus `@BeforeBatch`/`@AfterBatch`.
- **When tests are enabled.** `isGametestEnabled()` = `!production && (IS_RUNNING_IN_IDE || neoforge.gameTestServer || neoforge.enableGameTest)`. NeoForge patches `SharedConstants.IS_RUNNING_IN_IDE = !FMLLoader.isProduction()`. So GameTests are enabled in every dev run and never in production. They are actually registered, and `/test` exists, wherever a server or client starts: client, server, gameTestServer. The data run starts neither.
- **Namespace filter.** `neoforge.enabledGameTestNamespaces` (comma-separated; empty or unset means all) is compared with the *template namespace*:
  - lookup order: `@GameTest.templateNamespace`, then `@GameTestHolder.value`, then `"minecraft"`;
  - for generator-produced functions, the namespace of `structureName` is used.

  **Pitfall:** the MDK sets the property to the mod id. A test class registered through the event without a holder or namespace is silently dropped. If nothing is left, `GameTestServer.create` throws "No test functions were given!". That exception is thrown on the main thread and caught by the server `Main`'s `catch (Exception)`, which only logs "Failed to start the minecraft server". `main()` then returns normally, so the JVM very likely exits **0** and `runGameTestServer` passes green *(inference from source, not executed)*. Guard against it as described under "Exit and reports".
- **Naming.** Template = `<ns>:` + optional `<classname-lowercase>.` (`@PrefixGameTestTemplate`, default on) + `template()`, or the lowercase method name. The test name is the optional `<classname-lowercase>.` prefix plus the lowercase *method* name. It equals the template path only when `template()` is empty.
- **Templates:**
  - Shipped `.nbt` files live at `data/<ns>/structure/<path>.nbt`. 1.21 uses the singular `structure`.
  - In dev, `StructureTemplateManager` adds a source that reads **`.snbt` from `StructureUtils.testStructuresDir`**. It is a mutable `public static String`, default `"gameteststructures"` relative to the run dir, and the loader ignores the namespace.
  - `/test export <name>` converts a structure-block save into SNBT in that same folder.
  - Source order: world `generated/` first, then the SNBT dir, then resources.
- **What the runner does.** It force-loads chunks and clears a margin around the template: the box minX−2…maxX+3, minY−3…maxY+20, minZ−3…maxZ+3, with stone below `minY-1` and air from there up. It places the structure block at `minY-1`, so that layer is air apart from the structure block. It walls the area with barriers, and adds a barrier roof unless `skyAccess` is set. Put an explicit floor layer inside arena templates; testframework's `@EmptyTemplate(floor=true)` does the same.
- **Annotations.** `@GameTest` fields, defaults in brackets: `timeoutTicks` (100), `batch` ("defaultBatch"), `setupTicks` (0), `required` (true), `manualOnly`, `skyAccess`, `rotationSteps`, `templateNamespace`, `template`, `attempts`, `requiredSuccesses`.
- **Bug in 1.21.1:** `turnMethodIntoTestFunction` passes `requiredSuccesses()` into `maxAttempts` and `attempts()` into `requiredSuccesses`. Retry attributes are swapped.
- **`manualOnly`** is honoured only by in-game `/test runall`-style finders. `GameTestServer` receives `getAllTestFunctions()` unfiltered.
- **Batches.** Tests are grouped by batch name and split into chunks of 50. Batches run **one after another**; tests inside a batch run **at the same time** in one world. `@BeforeBatch`/`@AfterBatch(batch=…)` take a `ServerLevel`.
- **`GameTestServer` world.** Flat preset, seed 0, structures off, creative, difficulty NORMAL. Mob spawning, weather cycle, random ticks and fire tick are all off.
  - **All available data packs and all feature flags (including experimental ones) are enabled**, unlike a default world.
  - The test origin is random each run, within ±14,999,992 X/Z at y = -59. **A float has a one-block ULP at 15 M**, so keep simulation coordinates match-local or `double`.
  - The run uses launch target `forgeserverdev`, i.e. a server dist with client classes stripped, yet `isDedicatedServer()` returns `false`.
  - `waitUntilNextTick()` just runs tasks: **ticks run back-to-back with no 50 ms sleep**.
- **Exit and reports:**
  - `onServerExit` → `System.exit(failedRequiredCount)`. A crash inside the server's run loop → `System.exit(1)`. MDG's `RunGameTask` extends `JavaExec`, so a non-zero exit fails the Gradle task.
  - Failures before the server thread starts are caught in server `Main` and very likely exit 0 *(inference)*. That covers an empty test set and a mod-loading error. **So the Gradle task must also fail when the report file is missing or contains no `<testcase>`** (snippet in §6.2).
  - Edge case: exactly 256 failures would wrap to exit code 0. Also check the report.
  - Optional (`required=false`) failures are logged, not counted.
  - **No report file by default.** `JUnitLikeTestReporter` exists but nothing installs it. Call `GlobalTestReporter.replaceWith(...)` yourself. Its XML nests `<testsuite>` inside `<testsuite>`, so write your own standard JUnit XML instead (about 40 lines).
- **Commands** (`/test …`, all dev runs that start a server): `run`, `runall`, `runclosest`, `runthat`, `runthese`, `runfailed`, `runmultiple`, `create`, `locate`, `export*`, `import`, `pos`, `clearall`, `clearthat`, `clearthese`, `reset*`, `stop`, `verify`, `verifyclass`.
- **Helper API, verified:**
  - `spawn`, `spawnWithNoFreeWill`, `walkTo`, `setBlock`, `getBlockEntity`
  - `succeedWhen`, `succeedIf`, `succeedOnTickWhen`, `runAfterDelay`, `runAtTickTime`, `onEachTick`, `startSequence()` (with `thenWaitUntil`, `thenIdle`, `thenExecuteAfter`, `thenSucceed`)
  - `assertTrue`, `assertValueEqual`, `assertEntityPresent`, `assertEntitiesPresent`, `assertBlockState`, `assertContainerContains`, `fail`
  - `makeMockPlayer(GameType)`, and `makeMockServerPlayerInLevel()` (deprecated for removal; uses an `EmbeddedChannel` connection)
  - `absolutePos` / `relativePos`

### 6.2 Recommended harness (in `src/gametest`)

Own registration: one generator, your own annotation, a filter, SNBT from the repo, and the reporter. This mirrors Create's 1.21.1 approach (`CreateTestFunction.getTestsFrom` + `@GameTestGenerator`, structures under `data/create/structure/gametest/…`, CI runs `./gradlew runGameTestServer`; [Create mc1.21.1/dev](https://github.com/Creators-of-Create/Create/tree/mc1.21.1/dev)).

```java
@Retention(RUNTIME) @Target(METHOD)
public @interface StrategosTest {
    String arena() default "arena_16"; String batch() default "default";
    int timeoutTicks() default 200; long setupTicks() default 0; boolean required() default true;
}

@EventBusSubscriber(modid = Strategos.MOD_ID)   // FML 4.0.45: bus is inferred, `bus=` is deprecated and ignored
final class GameTestBootstrap {
    @SubscribeEvent
    static void onRegister(RegisterGameTestsEvent event) {
        String dir = System.getProperty("strategos.gametest.structures");
        if (dir != null) StructureUtils.testStructuresDir = dir;          // SNBT arenas straight from git
        String report = System.getProperty("strategos.gametest.report");
        if (report != null && GameTestHooks.isGametestServer())
            GlobalTestReporter.replaceWith(new TeeReporter(List.of(new LogTestReporter(), new JUnitXmlReporter(Path.of(report)))));
        event.register(StrategosGameTests.class);
    }
}

public final class StrategosGameTests {
    private static final List<Class<?>> SUITES = List.of(EconomyTests.class, CombatTests.class, PathingTests.class, PerfTests.class);

    @GameTestGenerator
    public static Collection<TestFunction> all() {
        var filter = Pattern.compile(System.getProperty("strategos.gametest.filter", ""));
        var out = new ArrayList<TestFunction>();
        for (Class<?> suite : SUITES) for (Method m : suite.getDeclaredMethods()) {
            StrategosTest t = m.getAnnotation(StrategosTest.class);
            if (t == null) continue;
            String name = suite.getSimpleName().toLowerCase(Locale.ROOT) + "." + m.getName().toLowerCase(Locale.ROOT);
            if (!filter.matcher(name).find()) continue;
            m.setAccessible(true);
            out.add(new TestFunction(t.batch(), name, Strategos.MOD_ID + ":" + t.arena(), Rotation.NONE,
                    t.timeoutTicks(), t.setupTicks(), t.required(), false, 1, 1, false, h -> invoke(m, h)));
        }
        out.sort(Comparator.comparing(TestFunction::testName));
        return out;
    }
}
```
Register through the event, not `@GameTestHolder`. The structure names carry the `strategos:` namespace, so they pass the namespace filter. Test names are `<suite class name lowercased>.<method lowercased>`, so a subset run is `./gradlew :mod:runGameTestServer -Pgametest.filter='^combattests\.'`. `'^combat\.'` would match nothing, and an empty test set is a silent pass unless the report check below is in place.

GameTest-related parts of `mod/build.gradle`:
```groovy
sourceSets { gametest }
neoForge {
    addModdingDependenciesTo sourceSets.gametest
    runs {
        configureEach { systemProperty 'neoforge.enabledGameTestNamespaces', 'strategos'; logLevel = org.slf4j.event.Level.INFO }
        gameTestServer {
            type = 'gameTestServer'
            gameDirectory = file('build/gametest')
            systemProperty 'strategos.gametest.structures', file('src/gametest/structures').absolutePath
            systemProperty 'strategos.gametest.report', file('build/test-results/gametest/TEST-gametest.xml').absolutePath
            systemProperty 'strategos.gametest.filter', providers.gradleProperty('gametest.filter').getOrElse('')
        }
    }
}
dependencies { gametestImplementation sourceSets.main.output; gametestImplementation core }
tasks.matching { it.name in ['runGameTestServer', 'runScenario', 'runServerSmoke'] }.configureEach {
    timeout = java.time.Duration.ofMinutes(10)
}
tasks.matching { it.name == 'runGameTestServer' }.configureEach {
    def world = layout.buildDirectory.dir('gametest/world')
    def report = file('build/test-results/gametest/TEST-gametest.xml')
    doFirst { world.get().asFile.deleteDir(); report.delete() }      // no stale generated/ structures, chunks or report
    doLast {   // added during verification (not executed): an empty test set or a mod-loading error can exit 0
        if (!report.isFile() || !report.text.contains('<testcase'))
            throw new GradleException("GameTest report missing or empty: ${report}")
    }
}
```
About the MDK's `logLevel = DEBUG`: MDG's `logLevel` sets the *root* level of the `log4j2.xml` it generates. That root level feeds both the console and `logs/debug.log`. Setting INFO, which is also MDG's default, keeps the assistant's console readable, **but `debug.log` then no longer receives DEBUG lines either**. To keep DEBUG in the file and INFO on the console, point the run's `loggingConfigFile` at a copy of MDG's generated config (`build/moddev/<run>Log4j2.xml`) with `level="info"` on the Console `AppenderRef`.

### 6.3 Mock players and networking

- For per-player server logic (ownership, diplomacy, per-player fog), use mock `ServerPlayer`s.
- `testframework`'s `ExtendedGameTestHelper.makeTickingMockServerPlayerInLevel` returns a `GameTestPlayer`:
  - It answers keep-alives.
  - It is set up through `NetworkRegistry.configureMockConnection`.
  - It records outbound packets: `getOutboundPayloads(Class)`, `requireOutboundPayload(Class)`.

  That is the cheapest way to assert that the server sends the right sync payloads.
- Using `ExtendedGameTestHelper` means running the testframework's helper factory. A simpler route is to copy the pattern into your own small helper. If you copy code, it is LGPL; writing your own is clean.
- Codec round-trips stay in L2.

### 6.4 Performance GameTests

APIs (verified):
- `MinecraftServer#getTickTimesNanos()` (a `long[100]` ring buffer)
- `getAverageTickTimeNanos()`
- `getCurrentSmoothedTickTime()` (ms, exponential moving average)
- NeoForge's `getTickTime(ResourceKey<Level>)` (nullable)
- `/tick query` (P50/P95/P99), `/tick freeze|step|sprint`
- `/neoforge tps`

Because the GameTest server never sleeps and the tests in a batch share ticks, measure **your own systems' time** and isolate perf tests:
```java
@StrategosTest(arena = "arena_48", batch = "perf", timeoutTicks = 20 * 60)
static void tick800Units(GameTestHelper h) {
    var sim = SimHooks.match(h.getLevel());
    Fixtures.spawnArmies(sim, h, 4, 200);
    var samples = new ArrayList<Long>();
    h.onEachTick(() -> samples.add(sim.lastTickNanos()));          // instrumented in your tick loop
    h.runAfterDelay(20 * 30, () -> {
        long p95 = Stats.percentile(samples.subList(200, samples.size()), 0.95);   // drop JIT warm-up
        h.assertTrue(p95 <= Budgets.serverTickNanos(), "p95=" + p95 / 1e6 + " ms");
        h.succeed();
    });
}
```
- Run perf tests in a separate invocation: `-Pgametest.filter='^perftests\.'`.
- Locally, gate at the real budget. On shared CI runners, gate at 3× the budget and upload the numbers as an artifact for trend tracking.
- Keep battle arenas at most 48 blocks per axis. Structure blocks clamp saved sizes to `MAX_SIZE_PER_AXIS = 48`. Larger templates may work in memory, but this is *unverified*. Generate bigger terrain procedurally in setup.

---

## 7. L4: client-side automated verification

### 7.1 What exists for 1.21.1

| Option | Status on 1.21.1 | Relevance |
|---|---|---|
| Fabric client gametest API (screenshots, input simulation, `MeanSquaredDifference` comparison with default 0.005) | **Not available**: first shipped in Fabric API 0.112.1+1.21.4 (2024-12-16); the 1.21.1 line has only `fabric-gametest-api-v1` ([Fabric Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-client-gametest-api-v1/maven-metadata.xml)) | Design reference: forced options (tutorial off, clouds off, onboarding off, render distance 5, music 0; newer versions also anisotropy off and chunk fade off), a virtual framebuffer decoupled from the OS window, focus/iconify events cancelled, a fixed partial tick |
| NeoForge (`net.neoforged.neoforge.gametest`) | Server-side only: `GameTestHolder`, `PrefixGameTestTemplate`, `GameTestHooks`, `BlockPosValueConverter` | No client automation |
| NeoForge `testframework` | In-game test screens and overlay, enable/disable commands, `side()`; tests where a human acts and events decide pass or fail. It also drives automated *server-side* GameTests (`ExtendedGameTestHelper`, `@EmptyTemplate`, `GameTestPlayer`) | Manual on the client side, not visual automation |
| AE2 + GuideME (1.21.1) | Client run with `-Dappeng.runGuideExportAndExit=true`; CI runs `sudo apt-get install xvfb libgl1-mesa-dri` (plus `zopfli`) then `DRI_PRIME=0 xvfb-run ./gradlew runGuideexport` ([workflow](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1.21.1/.github/workflows/export_guide.yml)); GuideME renders into a `TextureTarget` FBO, reads it back with `NativeImage.downloadTexture`, flips it and writes PNG/WebP ([OffScreenRenderer](https://github.com/AppliedEnergistics/GuideME/blob/1.21.1/src/main/java/guideme/internal/siteexport/OffScreenRenderer.java)) | **Proves the recommended pattern** on this exact stack |
| Create / Ponder | Create tests with server GameTests; Ponder is a scripted in-game tutorial-scene system | No visual verification |
| mc-runtime-test 5.0.0 (MIT) | Real client with your *production* jar on CI: joins a world, waits for chunks, quits | Release smoke only. Your GameTests are disabled in production, so its `/test runall` finds none |
| MineClient Bridge 1.1.3 (MIT, NeoForge 1.21.1, 2026-09-10, about 95 downloads, on CurseForge) | Localhost HTTP API with a bearer token: framebuffer PNG, state, input, commands, shutdown | The right idea for interactive mode, too immature to depend on |

### 7.2 Recommended: the dev-only scenario harness (`src/devtools`)

Design:
- **Trigger.** A system property; the code is inert otherwise and never ships. It is part of the mod's folder group, so it is discovered in dev only.
- **State machine on `ClientTickEvent.Post`:**
  - wait until `TitleScreen` shows and `getOverlay()==null`;
  - `createFreshLevel` with a flat, seeded world: structures off, creative, peaceful, commands allowed;
  - wait until `level`/`player` exist, `screen==null` and `levelRenderer.hasRenderedAllSections()`;
  - run setup commands on the integrated server: `tick freeze`, `time set 6000`, `weather clear`, gamerules;
  - set your RTS camera state through your own API, not synthesized mouse input;
  - step / wait N ticks / capture.
- **Capture on `RenderFrameEvent.Post`.** It fires right after `gameRenderer.render()`, before the blit to screen, so the HUD and GUI are included. Call `Screenshot.takeScreenshot(mainRenderTarget)` and `writeToFile` synchronously. `Screenshot.grab` writes asynchronously on `Util.ioPool()`, which would race with exit.
- **Output.** Each capture writes `<shot>.png` and `result.json` containing:
  - framebuffer size, GUI scale, GL renderer string;
  - camera position and rotation, FOV;
  - the HUD element rectangles your HUD code computed.

  Numbers let the assistant reason beyond pixels. Also save 1:1 crops of HUD regions, because full Retina frames get downscaled when viewed.
- **Exit.** `Minecraft.stop()` on success. On failure or watchdog, `System.exit(1)`; GuideME does the same.

```java
@EventBusSubscriber(modid = Strategos.MOD_ID, value = Dist.CLIENT)
final class ScenarioRunner {
    private static final @Nullable String ID = System.getProperty("strategos.scenario");
    private static final Path OUT = Path.of(System.getProperty("strategos.scenario.out", "scenario-out"));
    private static final long DEADLINE = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
    private static Phase phase = Phase.WAIT_TITLE;
    private static @Nullable Scenario scenario;
    private static @Nullable String pendingShot;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post e) {
        if (ID == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (System.nanoTime() > DEADLINE) fail("timeout in " + phase);
        switch (phase) {
            case WAIT_TITLE -> { if (mc.getOverlay() == null && mc.screen instanceof TitleScreen) {
                mc.createWorldOpenFlows().createFreshLevel("scenario",
                    new LevelSettings("scenario", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false),
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
                phase = Phase.WAIT_WORLD; } }
            case WAIT_WORLD -> { if (mc.level != null && mc.player != null && mc.screen == null && mc.levelRenderer.hasRenderedAllSections()) {
                scenario = Scenarios.byId(ID); scenario.setup(mc, mc.getSingleplayerServer()); phase = Phase.RUN; } }
            case RUN -> { var step = scenario.tick(mc);
                if (step instanceof Step.Capture c) pendingShot = c.name(); else if (step == Step.DONE) phase = Phase.DONE; }
            case DONE -> { Results.write(OUT, mc, scenario); mc.stop(); }
        }
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Post e) {
        if (pendingShot == null) return;
        try (NativeImage img = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            Files.createDirectories(OUT); img.writeToFile(OUT.resolve(pendingShot + ".png"));
        } catch (IOException ex) { fail(ex.toString()); }
        pendingShot = null;
    }
}
```
NeoForge registers its `mod_data` pack with `PackSelectionConfig(required=true)`, so `WorldDataConfiguration.DEFAULT` still loads mod data. This holds while the mod keeps the default `showAsDataPack=false`; with `true` its pack becomes a separate, non-required pack.

Run definition and a deterministic game directory (all option keys exist in 1.21.1 `Options`):
```groovy
sourceSets { devtools }
neoForge {
    addModdingDependenciesTo sourceSets.devtools
    runs {
        scenario {
            client(); disableIdeRun()
            gameDirectory = file('build/scenario')
            programArguments.addAll '--username', 'ScenarioBot', '--width', '1280', '--height', '720'
            systemProperty 'strategos.scenario', providers.gradleProperty('scenario').getOrElse('smoke')
            systemProperty 'strategos.scenario.out', file('build/scenario/out').absolutePath
            jvmArguments.addAll '-Xmx3G'
        }
    }
}
dependencies { devtoolsImplementation sourceSets.main.output; devtoolsImplementation core }
def prepareScenario = tasks.register('prepareScenario', Sync) {    // wipes saves/out every run
    from 'src/devtools/scenario-gamedir'                            // options.txt (+ optional config/fml.toml)
    into layout.buildDirectory.dir('scenario')
}
neoForge.runs.scenario.taskBefore prepareScenario
```

`options.txt` keys:

| Key | Value |
|---|---|
| `onboardAccessibility` | `false` (otherwise the first launch stops on the accessibility screen) |
| `tutorialStep` | `none` |
| `pauseOnLostFocus` | `false` |
| `skipMultiplayerWarning` | `true` |
| `joinedFirstServer` | `true` |
| `renderClouds` | `"false"` |
| `renderDistance` / `simulationDistance` | `5` |
| `particles` | `2` |
| `bobView` | `false` |
| `fov` | `0.0` |
| `guiScale` | `2` |
| `maxFps` | `60` |
| `enableVsync` | `false` |
| `narrator` | `0` |
| `hideSplashTexts` | `true` |
| `realmsNotifications` | `false` |
| `showAutosaveIndicator` | `false` |
| `soundCategory_master` | `0.0` |

The encodings were checked against the 1.21.1 `Options.java`:
- `fov` is stored normalised as (fov−70)/40, so `0.0` = 70°;
- `renderClouds` accepts `"false"` or `false` (both OFF);
- `particles` `2` = minimal;
- `narrator` `0` = off.

Generating the file once from a real run and editing it is still the easiest way to get a complete, consistent file.

`config/fml.toml` (optional):
- `earlyWindowWidth`, `earlyWindowHeight`. FML's early window parses `--width/--height` itself and falls back to these values (default 854×480) only when those arguments are absent. It writes the result back to `fml.toml` and hands its size to Minecraft's `Window` via `ImmediateWindowHandler.positionWindow`. **So `--width 1280 --height 720` is enough**; the toml entries are redundant.
- `versionCheck = false` disables NeoForge's update check. It does not by itself guarantee the run makes no other network calls *(unverified)*.

### 7.3 macOS realities

- **No headless GL.** GLFW on macOS is Cocoa: it needs a window and the main thread (`-XstartOnFirstThread`, which MDG adds automatically for client and data runs on macOS). Xvfb does not apply.
- **The window is real.** It appears and takes focus for about a minute *(estimate)*. With `pauseOnLostFocus:false` the owner can keep working.
- **Do not minimise it.** A zero framebuffer skips rendering *(inference)*.
- **No TCC prompt.** Pixels are read from the game's own GL framebuffer, so no Screen Recording permission is needed.
- **Retina.** Neither FML's early window nor Minecraft sets `GLFW_COCOA_RETINA_FRAMEBUFFER` (now `GLFW_SCALE_FRAMEBUFFER`). GLFW's default is true, so expect a framebuffer twice the window size on a Retina panel *(inference; confirm in the spike)*. The harness records the size, and goldens are compared only at a matching size. GuideME's fixed-size FBO is the fallback if size independence ever matters.
- **Hidden windows** (`glfwHideWindow` after start) and GLFW-null-platform/OSMesa are *unproven*. Not worth the time now.

### 7.4 CI

- Same harness under `xvfb-run -a -s "-screen 0 1920x1080x24"` with `libgl1-mesa-dri` (llvmpipe, OpenGL 4.5 conformant since Mesa 20.x, which covers the 3.2 core profile Minecraft needs).
- Keep Linux goldens separate from macOS goldens: rasterizers differ.
- Mesa updates on runner images can shift pixels. A threshold comparison absorbs that; re-baseline occasionally.

### 7.5 Comparing images and the assistant's loop

**`scenarioDiff`** is a plain-JVM task using ImageIO. For each shot it computes the normalized mean squared difference against `src/scenario/golden/<platform>/<scenario>/`, with Fabric's default threshold of 0.005 as the starting point. It supports per-scenario mask rectangles and writes `diff-<shot>.png` (amplified absolute difference) next to the output.

**Assistant loop for a HUD or camera change:**
1. Edit.
2. `./gradlew :mod:runScenario -Pscenario=hud_basic`.
3. Read `result.json` and the PNG crops (Claude Code views PNGs natively).
4. Run `scenarioDiff`.
5. If the change is intended, show the owner the before and after images. **Golden images change only with the owner's approval.**

Put layout math (anchors, scaling, overlap rules) in pure code and unit-test it at several GUI scales and resolutions. Screenshots then only confirm the rendering.

### 7.6 Phase 2 (optional): an interactive bridge

A dev-only localhost HTTP endpoint inside the client:
- Built on the JDK's `com.sun.net.httpserver`. That module is in the boot layer *(inference)*.
- A random token in a file; binds only to 127.0.0.1.
- Endpoints: `/shot`, `/camera`, `/command`, `/state`, `/quit`.

The owner starts the client with hot swap. The assistant iterates without paying 30–60 s of startup per check *(estimate)*. MineClient Bridge shows the pattern works; write your own (about 300 lines, *estimate*) rather than depend on it.

---

## 8. Multiplayer testing in dev

Verified behaviour:
- In dev the dedicated server **auto-agrees to the EULA** and **`online-mode` defaults to false**. Vanilla's `Eula` already accepts when `IS_RUNNING_IN_IDE` is true, and NeoForge makes that constant true in dev (it also auto-accepts for the GameTest server). NeoForge patches the `online-mode` default in `DedicatedServerProperties` to `!IS_RUNNING_IN_IDE`.
- `enforceSecureProfile()` also needs online mode, so offline dev accounts just work.
- The default client username is `"Player"` plus `System.currentTimeMillis() % 1000` (1–3 digits). Always pass `--username`; offline UUIDs come from the name, so player data is stable per name.
- Quick Play flags exist in 1.21.1: `--quickPlayMultiplayer`, `--quickPlaySingleplayer`.

```groovy
runs {
    client  { client(); gameDirectory = file('run/client');  programArguments.addAll '--username', 'Dev1'; jvmArguments.addAll '-Xmx4G' }
    client2 { client(); gameDirectory = file('run/client2'); programArguments.addAll '--username', 'Dev2', '--quickPlayMultiplayer', 'localhost:25565'; jvmArguments.addAll '-Xmx3G' }
    server  { server(); gameDirectory = file('run/server');  programArgument '--nogui'; jvmArguments.addAll '-Xmx3G' }
    serverSmoke { server(); disableIdeRun(); gameDirectory = file('build/server-smoke'); programArgument '--nogui'
                  systemProperty 'strategos.smoke.haltAfterStart', 'true' }   // ServerStartedEvent -> write marker, server.halt(false)
}
```

- **Separate game directories** keep `options.txt`, logs and screenshots from clobbering each other.
- **Smoke-run port.** Give `serverSmoke` a `server.properties` with `server-port=25599`, so it never collides with the owner's running dev server on 25565. `GameTestServer` opens no port.
- **Smoke-run verdict.** The task's `doLast` checks for the marker file and for no new `crash-reports/*`. The marker is required, not optional, because a crashed dedicated dev server very likely exits **0** *(inference from source, not executed)*:
  - a crash in the run loop writes `crash-reports/*.txt`, then calls `onServerCrash` (a no-op for `DedicatedServer`) and `onServerExit` (no `System.exit`); only the `ServerWatchdog` exits 1;
  - startup and mod-loading failures are caught in server `Main` and only logged.
- **Launching several instances.** `./gradlew :mod:createLaunchScripts` generates shell scripts under `mod/build/moddev/` (e.g. `runClient2.sh`) that launch a run without Gradle. That is the clean way to start server + client + client2 in parallel; run `classes` first.
- **Always test with a dedicated server, not Open-to-LAN.** An integrated server shares the JVM and statics with the host client, which hides logical-side bugs.
- **Automate the per-player server logic** with mock players (§6.3); keep human multiplayer sessions for milestones.

---

## 9. Profiling and budgets

**Profiling tools:**
- **spark 1.10.124** (dev-only: `localRuntime 'maven.modrinth:spark:1.10.124-neoforge-1.21.1'` from `https://api.modrinth.com/maven`). The async-profiler engine works on Apple Silicon (bundled universal macOS native; spark accepts macOS).
  - Use `/spark profiler start --timeout 60`, `/spark tickmonitor`, `/spark health` (command flags *unverified*).
  - Use `--save-to-file` to keep profiles local (*unverified*).
  - It suits human eyes; its viewer is not readable by the assistant.
- **JFR, for the assistant:**
  - Minecraft has built-in JFR: `--jfrProfile` on client and server, `/jfr start|stop` writes `debug/<env>-<time>.jfr`, and it adds custom events (ServerTickTime, PacketSent/Received, ChunkGeneration).
  - Or add `-XX:StartFlightRecording=filename=…,settings=profile,dumponexit=true` to a dedicated `profile*` run.
  - JDK 21's `jfr view hot-methods <file>` and `jcmd <pid> JFR.view …` ([JDK-8306703](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)) give plain-text tables the assistant can read.
- **async-profiler 4.x on macOS arm64.**
  - **Use `asprof -d 30 -e itimer -o collapsed`** for text output, or HTML flame graphs. On macOS `-e cpu` silently falls back to wall-clock sampling, because perf_events and ctimer are Linux-only; `-e cpu` is right on Linux CI.
  - For profiling runs add `-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints`.
  - JDK 21 warns on dynamic agent attach; use `-XX:+EnableDynamicAgentLoading` or start it as `-agentpath`.

**Budgets** (my recommendation; tune after the first playable build). Design scale is 4 players × 200 units plus about 100 buildings.

| Budget | Target |
|---|---|
| Server tick (vanilla budget 50 ms) | Mod's own server work ≤ 5 ms p50 and ≤ 10 ms p99 on the M4 Pro; assume hosts are about 2× slower |
| Pathfinding | Time-sliced, at most 2 ms per tick; never blocks the tick |
| Client frame | Mod rendering + HUD ≤ 3 ms per frame at 1080p; frame-time p99 ≤ 25 ms |
| Network | Per-player bytes-per-tick budget, checked in L2 (encoded payload sizes) and in perf GameTests |

---

## 10. Code quality

- **Spotless 8.10.4 + palantir-java-format 2.102.0.** 4-space indent and 120 columns, close to Mojang/NeoForge style. `formatAnnotations()` keeps JSpecify type-use annotations next to their types.
- **ErrorProne 2.50.0 + NullAway 0.14.2:**
  - Use JSpecify mode with `OnlyNullMarked`, plus `@NullMarked` in each `package-info.java`.
  - On JDK 21, JSpecify mode needs `-XDaddTypeAnnotationsToSymbol=true`, JDK ≥ 21.0.8, and an OpenJDK build: Temurin works, Oracle JDK is not supported ([NullAway wiki](https://github.com/uber/NullAway/wiki/JSpecify-Support)).
  - JSpecify mode always turns on `AcknowledgeRestrictiveAnnotations`, so Minecraft's `javax.annotation.@Nullable` (e.g. `Minecraft.player`, `Level.getBlockEntity`) is honoured. That makes it valuable for mod code.
  - Exclude `mixin/` and generated code.
  - Keep `@SubscribeEvent` handlers non-private, or `UnusedMethod` (private methods only) may fire. Its exemption list includes Guava's `@Subscribe` but not NeoForge's `@SubscribeEvent`.
  - The Gradle plugin forks javac and adds the needed `--add-exports` itself. ErrorProne's memory is therefore the compiler fork's (`options.forkOptions.memoryMaximumSize`), not the daemon's.
- **ArchUnit 1.5.1 in a plain-JVM `archTest` task, not inside FML JUnit.** ArchUnit's classpath scanning under the module layer is a risk *(inference)*. It imports class directories, so Minecraft does not need to be on the classpath:
```groovy
sourceSets { archTest }
dependencies {
    archTestImplementation platform('org.junit:junit-bom:5.14.4')
    archTestImplementation 'org.junit.jupiter:junit-jupiter'
    archTestImplementation 'com.tngtech.archunit:archunit-junit5:1.5.1'
    archTestRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
def archTest = tasks.register('archTest', Test) {
    testClassesDirs = sourceSets.archTest.output.classesDirs
    classpath = sourceSets.archTest.runtimeClasspath
    def classes = files(sourceSets.main.output.classesDirs, core.sourceSets.main.output.classesDirs)
    inputs.files(classes)
    jvmArgumentProviders.add({ ["-Dstrategos.arch.classes=${classes.asPath}"] } as CommandLineArgumentProvider)
}
tasks.named('check') { dependsOn archTest }
```
```java
@Test void commonCodeNeverTouchesClientClasses() {
    noClasses().that().resideOutsideOfPackages("dev.strategos.mod.client..")
        .should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..", "com.mojang.blaze3d..", "net.neoforged.neoforge.client..")
        .because("common code runs on dedicated servers").check(CLASSES);
}
```
  The `core` boundary is already enforced by compilation: Minecraft is not on its classpath.
- **Checkstyle 14.3.0 / PMD 7.28.0: skip both.** They overlap with the formatter and ErrorProne, and their noise costs assistant attention.

---

## 11. CI and release

```yaml
name: CI
on: { push: { branches: ['**'] }, pull_request: {} }
permissions: { contents: read, checks: write }
concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }
jobs:
  verify:
    runs-on: ubuntu-24.04
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v6
        with:
          cache-provider: basic                      # MIT; default "enhanced" is proprietary (free preview for private repos)
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}   # restates the default (writes only from the default branch)
      - run: ./gradlew --console=plain check          # L0, L1, L2, archTest
      - run: ./gradlew --console=plain :mod:runData && git diff --exit-code -- mod/src/generated ':!**/.cache/**'
      - run: ./gradlew --console=plain :mod:runGameTestServer :mod:runServerSmoke   # runGameTestServer's doLast fails on a missing/empty report (§6.2)
      - name: Client scenarios (Xvfb + llvmpipe)
        if: github.event_name == 'pull_request' || github.ref == 'refs/heads/main'
        run: |
          sudo apt-get update -q && sudo apt-get install -yq xvfb libgl1-mesa-dri
          xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew --console=plain :mod:runScenario -Pscenario=ci_all :mod:scenarioDiff -Pscenario.platform=linux-llvmpipe
      - uses: mikepenz/action-junit-report@v6
        if: always()
        with: { report_paths: '**/build/test-results/**/TEST-*.xml' }
      - uses: actions/upload-artifact@v7
        if: always()
        with:
          name: reports
          retention-days: 7
          path: |
            **/build/reports/
            **/build/test-results/
            mod/build/scenario/out/
            mod/build/gametest/logs/
            mod/build/gametest/crash-reports/
```

- **CI speed.** MDG ≥ 2.0.136 skips decompile and recompile when `CI=true`.
- **Cache behaviour.** The basic provider stores `~/.gradle/caches` and `~/.gradle/wrapper` as one entry keyed on the build files, with no restore keys. Any build-file change starts from an empty cache.
- **Budget.** A GitHub Free private repo gets 2,000 minutes a month (Linux 2-core $0.006/min, macOS $0.062/min), 500 MB of artifacts and 10 GB of cache per repo ([billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)). Stay on Linux, keep artifacts for 7 days, and run scenarios only on PRs and `main`.
- **Release tags** (`v*`) run three steps:
  1. `./gradlew verify`.
  2. mc-runtime-test on the built jar (`mc: 1.21.1`, `modloader: neoforge`, `mc-runtime-test: neoforge`, `java: 21`, `xvfb: true`; check the inputs when you adopt it). Its README lists no input for pinning a specific NeoForge build.
  3. mod-publish-plugin 2.2.1: `publishMods { file = tasks.named('jar').flatMap { it.archiveFile }; type = STABLE; modLoaders.add('neoforge'); modrinth { projectId = '…'; accessToken = providers.environmentVariable('MODRINTH_TOKEN'); minecraftVersions.add('1.21.1') }; curseforge { …; client = true; server = true }; github { … } }`. Since 2.0, CurseForge requires at least one of `client`/`server`; a dry-run mode exists.

---

## 12. macOS developer environment

- **JDK.**
  - Let foojay-resolver 1.0.0 provision Java 21; its preference order starts with Temurin.
  - Do not install `openjdk@21` from Homebrew; keg-only JDKs are easy to mis-detect.
  - Check that the JDK is arm64: `os.arch=aarch64`. An x64 JDK under Rosetta would load the x64 LWJGL natives.
- **Daemon vs toolchain.**
  - The toolchain JDK (21) compiles, tests and launches the game. MDG's run tasks call `launcherFor(languageVersion)`, which ignores the vendor.
  - Without other settings the daemon runs on whatever `JAVA_HOME` is (Homebrew 25 is supported by Gradle ≥ 9.1).
  - Commit `gradle/gradle-daemon-jvm.properties` via `./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium`. It takes precedence over `JAVA_HOME`, so one JDK serves everything, locally and on CI.
- **Memory on 24 GB** *(estimates)*:

  | Process | Memory |
  |---|---|
  | Gradle daemon | `org.gradle.jvmargs=-Xmx2g`. The MDK's 1G may be tight for MDG and IDE sync *(unverified)*. ErrorProne itself runs in the forked compiler, sized by `options.forkOptions.memoryMaximumSize`, not in the daemon |
  | IntelliJ | 3–4 GB |
  | client | `-Xmx4G` |
  | server | `-Xmx3G` |
  | client2 | `-Xmx3G` |
  | Total for a full 3-process multiplayer test | about 18–20 GB including native and GPU memory (unified memory) |

  For multiplayer sessions use render distance 6.
- **Hot swap:**
  - foojay serves JetBrains Runtime 21.0.11 for macOS aarch64 (`jbrsdk_jcef-21.0.11-osx-aarch64-b1163.116`).
  - Opt in per machine. The override relies on this `configureEach` running after MDG's own `javaLauncher` setting; check that in the spike *(unverified)*:
    ```groovy
    if (providers.gradleProperty('strategos.jbr').isPresent()) {
      tasks.matching { it.name == 'runClient' }.configureEach { JavaExec t ->
        t.javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21); vendor = JvmVendorSpec.JETBRAINS }
        t.jvmArgs '-XX:+AllowEnhancedClassRedefinition' } }
    ```
  - The flag is not recognised by non-JBR JVMs and stops them from starting, so it must stay conditional.
  - In IntelliJ, set the Project SDK to JBR 21 and "Build and run using: IntelliJ IDEA" for the fastest swaps. MDG supports both build modes.
  - Mixin targets and registries still need a restart *(unverified)*.

---

## 13. Definition of Done

Aggregate tasks in the root `build.gradle`:
- `verifyFast` = `:core:check`, `:mod:spotlessCheck`, `:mod:compileJava`, `:mod:compileGametestJava`, `:mod:compileDevtoolsJava`, `:mod:archTest`
- `verify` = `:core:check`, `:mod:check`, `:mod:runGameTestServer` (including its non-empty-report check), `:mod:runServerSmoke` (marker-file verdict)
- `verifyClient` = `:mod:runScenario` + `:mod:scenarioDiff`
- `verifyPerf` = `:core:jmh` + the perf GameTest filter

R = required, C = conditional, – = not needed.

| Change type | L0 | L1 | L2 | L3 | L4 | L5 | L6 | L7 |
|---|---|---|---|---|---|---|---|---|
| core simulation, pathfinding, economy | R | R, with new tests | – | C (if visible in-world) | – | – | C (hot path: JMH vs baseline) | – |
| server gameplay adapter (entities, blocks, AI) | R | R | C | R, with a new GameTest | – | R | C | milestone |
| data, registries, codecs, datagen | R | – | R | R | – | R | – | – |
| networking / payloads | R | – | R (round-trip + size) | R (mock players) | – | R | C | R before merge |
| client UI, HUD, camera, rendering | R | R (pure layout math) | – | – | R (assistant inspects, diff, owner approves goldens) | R | C (frame time) | – |
| build / tooling | R (full `verify`) | R | R | R | R | R | – | – |
| release | everything, plus mc-runtime-test, plus a manual multiplayer session | | | | | | | |

**Done means:**
- New behaviour is tested at the lowest level that can express it.
- The required levels pass locally (run by the assistant) and on CI.
- There are no new ErrorProne warnings and NullAway is clean.
- Goldens and perf baselines change only with the owner's approval.
- Any new pitfall is written down in the project's knowledge base in the same session.

---

## 14. Verified vs inferred, and residual risks

**Verified from source or metadata:**
- The MDG mechanics: cross-project mod source sets, legacy classpath, the unit-test wiring, `-XstartOnFirstThread`, CI recompile skipping, and that `logLevel` sets the root level for both the console and `debug.log`.
- The JUnit bootstrap chain.
- Every GameTest behaviour listed in §6.1, including the swapped retry attributes, the exit code and the absent report.
- Dev-server EULA and online-mode defaults.
- Fabric client-gametest availability.
- AE2, GuideME and Create practices.
- All versions, dates and licenses in §1, with the corrections noted there.
- The jqwik clause.
- spark's macOS native.
- The setup-gradle licensing.
- GitHub billing figures.
- `options.txt` value encodings (from `Options.java`).
- That `--width/--height` set the window size (FML early window code).

**Resolved from source but not executed (confirm in the spike):**
- A crashed dedicated dev server, a server-side mod-loading failure and an empty GameTest set all very likely exit with code 0. Hence the marker-file and non-empty-report checks.
- On macOS, async-profiler's `-e cpu` falls back to wall-clock sampling; use `itimer`.

**Inferred, needs a one-day spike:**
- Startup times for L2, L3 and L4 on the M4 Pro.
- Retina 2× framebuffer under FML's early window.
- That `implementation core` plus the `mods{}` merge causes no duplicate-class side effects in 1.21.1 runs. Static analysis of `UserdevLocator` says the core jar on the classpath is ignored as a non-mod.
- Templates larger than 48 blocks.
- Whether the JBR `javaLauncher` override wins over MDG's own setting.
- Configuration-cache compatibility of the build snippets (the MDK enables `org.gradle.configuration-cache=true`).

---

## Verification notes

**What was checked (2026-10-20, independent second pass).**
- **Maven metadata and POM `Last-Modified` headers** for:
  - NeoForge, testframework, FML, junit-fml and modlauncher (maven.neoforged.net);
  - every Maven Central artifact in §1, and the Gradle Plugin Portal plugins;
  - Fabric API POMs (maven.fabricmc.net).
- **Release and version services:** Gradle `services.gradle.org`, the Adoptium API, the foojay Disco API, the Modrinth API (spark), and GitHub tags and releases (actions, async-profiler, mc-runtime-test).
- **The MDG repository** at `e9c127db` (2026-09-29, = 2.0.148).
- **Downloaded source jars:** FML loader, earlydisplay, junit-fml 4.0.45 and testframework 21.1.264.
- **The NeoForge 21.1.262 `moddev-config.json`.**
- **The decompiled Minecraft 1.21.1 + NeoForge sources** in `scratchpad/mc-src-1.21.1`.
- **Third-party repositories:** AE2, GuideME and Create 1.21.1 build files, Fabric's client-gametest module, mod-publish-plugin docs, setup-gradle v6.4.0 docs, Spotless 8.10.4 README and CHANGES, gradle-errorprone-plugin v5.1.1 README, ErrorProne `UnusedMethod`/`WellKnownKeep`, and async-profiler v4.6 docs and source.
- **Pages and docs:** jqwik release notes and user guide, LWN, GitHub billing docs, Gradle compatibility, daemon and upgrade docs, NullAway wiki, GLFW 3.4 docs.
- **One local test:** a JBR-only VM flag on a stock JDK 23.

**What changed (errors fixed inline).**
1. **MDG `logLevel` (§6.2).** Lowering it to INFO also removes DEBUG lines from `logs/debug.log`, because it sets the root level. The original text said debug output would still go there. Use a custom `loggingConfigFile` to split the levels.
2. **Window size (§7.2).** `--width/--height` *do* stick: FML's early window parses them and writes them back to `fml.toml`. The toml entries are optional. `versionCheck=false` only disables the NeoForge update check.
3. **Silent passes (§0, §2, §6.1, §6.2, §11, §13).** An empty GameTest set or a server-side mod-loading error is caught and only logged in the server's `main()`, so `runGameTestServer` very likely exits 0. I added a non-empty-report check. A crashed dedicated dev server also very likely exits 0 (§8), so the smoke-test marker is required.
4. **Subset-filter example (§6.2).** It used `'^combat\.'`, which matches nothing with the report's own naming; corrected to `'^combattests\.'`.
5. **GameTest naming and commands.** Test names equal the template path only when `template()` is empty. The `/test` list was missing `verifyclass`, `clearthat` and `clearthese`.
6. **testframework coverage.** It is published for most 21.1 builds, not every one (none for 21.1.236/.237/.240/.245/.246).
7. **NeoForge cadence.** Builds come in bursts: 10 in Oct 3–19 2026, 5 in Aug–Sep.
8. **jqwik 1.10.0.** It was withdrawn by the author and removed from the GitHub releases page, but it is still on Maven Central. `jqwik.hideAntiAiClause` exists but only hides the line in terminals.
9. **async-profiler on macOS.** Use `-e itimer`; `-e cpu` falls back to wall-clock sampling. The v4.6 release was published 2026-10-19 (the tag commit is 2026-10-12).
10. **Licences.** modlauncher is LGPL-3.0-only, not LGPL-2.1.
11. **me.champeau.jmh.** Whether 0.7.3 actually fails on Gradle 9.2.1 is unverified; the plugin-free setup is kept as a safe choice.
12. **Small wording fixes:**
    - the EULA mechanism (vanilla already checks `IS_RUNNING_IN_IDE`);
    - the default username suffix is 1–3 digits;
    - CurseForge needs at least one of `client`/`server`;
    - the Spotless minimum Gradle comes from its CHANGES (the README is stale);
    - `cache-read-only` restates the default;
    - the daemon-heap rationale is doubtful, because ErrorProne runs in the forked javac.

**New cautions found during verification.**
- **Feature flags.** `GameTestServer` and testframework's `EphemeralTestServerProvider` enable *all* available data packs and *all* feature flags. Tests therefore see experimental content that a default world does not have.
- **Ephemeral server.** It never loads a level, and once started it ticks without sleeping for the rest of the test JVM.
- **`WorldDataConfiguration.DEFAULT`.** It loads mod data only while `showAsDataPack` stays false.
- **Configuration cache.** The MDK enables it, and the report's Groovy snippets have not been run against it.

**Status of the researcher's open questions.**
1. Wall-clock costs on the M4 Pro: still open.
2. Retina 2× framebuffer: still an inference (no hint set; GLFW default true). Window size: resolved; `--width/--height` decide it.
3. Duplicate classes from `implementation core` plus the `mods{}` merge: static analysis of `UserdevLocator` says no; still to run.
4. Templates larger than 48 blocks: open.
5. Crash exit code: resolved from source; very likely 0, so the marker file is required.
6. Hidden-window rendering on macOS: open.
7. `options.txt` encodings: resolved from source.
8. mc-runtime-test: its README exposes no input for picking a NeoForge build; asset and network flakiness is untested.
9. jqwik: the switch exists but does not affect captured stdout; the POM licence is EPL-2.0.
10. me.champeau.jmh release: still unreleased; it is now also unverified whether 0.7.3 breaks on Gradle 9.
11. IntelliJ and `gradle-daemon-jvm.properties`: open.
12. Moving the wrapper from Gradle 9.2.1 to 9.8.2: open.

## Recommendations (after verification)

1. 1. Adopt the core/mod split. ':core' is Minecraft-free plain Java, with no module-info and its own package root (dev.strategos.core). Merge it into the mod with MDG mods { strategos { sourceSet project(':core').sourceSets.main } } plus jar { from core output }. Never jarJar or shade first-party code; keep jarJar plus additionalRuntimeClasspath for third-party libraries. The merge rests on README and source analysis only, so confirm it in the spike with a dev run and with the production jar under mc-runtime-test.
2. 2. Make './gradlew verify' the commit gate for both the assistant and CI: static checks, core tests, FML unit tests, archTest, headless GameTests and the dedicated-server boot smoke. Every task the assistant runs must end on its own and have a Gradle timeout. Exit codes alone are not verdicts: an empty GameTest set, a server-side mod-loading error and a crashed dedicated dev server all very likely exit 0 (inference from source). So runGameTestServer fails on a missing or empty JUnit report, and the smoke test passes only on its marker file with no new crash report. Interactive client and server sessions stay with the owner.
3. 3. Register GameTests through a single @GameTestGenerator with your own annotation. Test names are '<suite class lowercased>.<method lowercased>', so filter with e.g. -Pgametest.filter='^combattests\.', not '^combat\.'. Pass the correct retry values yourself (vanilla 1.21.1 swaps them) and use the strategos: template namespace. Keep SNBT arenas in git via StructureUtils.testStructuresDir. Install a custom standard JUnit-XML reporter and fail the task when the report is missing or empty. Isolate perf tests in batch 'perf' with your own tick instrumentation and budgets. GameTests run with all data packs and all feature flags enabled, including experimental ones; keep that in mind when a test touches flag-gated content.
4. 4. Build the dev-only client scenario harness. A system property selects a scenario. The harness builds a deterministic world and options.txt (encodings checked against Options.java; generating the file from a real run is still simplest), runs scripted steps, captures synchronously on RenderFrameEvent.Post, writes PNGs plus result JSON and HUD crops, and exits under a watchdog. Set the window size with --width/--height; FML's early window honours them, so fml.toml size entries are optional. Compare against per-platform goldens with normalized MSE, starting at 0.005. Run it on the Mac with a visible window and on CI under xvfb-run with Mesa llvmpipe. Goldens change only with the owner's approval.
5. 5. Do not use jqwik 1.10 or later; it ships an anti-AI clause and agent-directed stdout. The withdrawn 1.10.0 is still on Maven Central, and jqwik.hideAntiAiClause only hides the line in terminals. Write seeded invariant and deterministic-replay tests with JUnit 5.14.4 @ParameterizedTest and AssertJ 3.27.7, printing the seed in every failure. Stay on JUnit 5.14.x, since NeoForge's junit-fml imports junit-bom 5.10.2 and ArchUnit 1.5.1 builds on Platform 1.14.4. If shrinking is ever needed, pin jqwik 1.9.3 strictly and add a Dependabot ignore.
6. 6. Code quality: Spotless 8.10.4 (needs Gradle 8.1+) with palantir-java-format 2.102.0 and formatAnnotations(). ErrorProne 2.50.0 with NullAway 0.14.2 on main sources only: JSpecify mode, OnlyNullMarked, -XDaddTypeAnnotationsToSymbol=true, a Temurin JDK 21.0.8 or newer, mixins and generated code excluded. Keep @SubscribeEvent handlers non-private, because UnusedMethod does not exempt NeoForge's annotation. ErrorProne runs in the forked compiler, so size it with options.forkOptions.memoryMaximumSize if needed. Run ArchUnit 1.5.1 rules forbidding client-package dependencies from common code in a plain-JVM archTest task. Skip Checkstyle and PMD.
7. 7. CI: one ubuntu-24.04 job using checkout@v7, setup-java@v6 (Temurin 21) and gradle/actions/setup-gradle@v6 with cache-provider: basic. Basic caching stores ~/.gradle/caches and ~/.gradle/wrapper as one entry with no restore keys, so build-file changes start cold; writing only from main is already the default. Run check, then a datagen diff that excludes .cache, then the GameTests (with the non-empty-report check) and the server smoke (marker verdict), then scenarios under Xvfb on PRs and main. Publish JUnit reports, keep artifacts 7 days, and update the MDK's v4 action pins.
8. 8. Release automation on v* tags: run verify, then mc-runtime-test on the built jar in a real NeoForge 1.21.1 client (it has no input for pinning a NeoForge build), then mod-publish-plugin 2.2.1 for Modrinth, CurseForge and GitHub, with a dry run first. CurseForge requires at least one of client/server; set both.
9. 9. Profiling: use the plugin-free JMH source set on core, with committed per-machine baselines and a gate at more than 15% regression. Whether me.champeau.jmh 0.7.3 works on Gradle 9.2.1 is unverified, so the plugin-free route avoids the question. For text the assistant can read, use Minecraft's built-in JFR (--jfrProfile or /jfr) analysed with 'jfr view hot-methods' or 'jcmd <pid> JFR.view', plus async-profiler 4.x collapsed output. On macOS use '-e itimer' (and event=itimer for JMH -prof async), because '-e cpu' falls back to wall-clock there. Keep spark 1.10.124 as a dev-only localRuntime for humans. Adopt the budgets in §9 and tune them after the first playable build.
10. 10. macOS environment: let foojay provision Temurin 21 and do not use Homebrew openjdk@21. Commit gradle-daemon-jvm.properties for version 21 (updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium). Set org.gradle.jvmargs=-Xmx2g, measured rather than assumed, and an explicit -Xmx per run (client 4G, server 3G, client2 3G). MDG's logLevel sets the root level for both the console and debug.log: lower it to INFO only if you accept losing DEBUG in debug.log, or supply a custom loggingConfigFile with an INFO console and a DEBUG file. Make JBR 21 hot swap opt-in behind -Pstrategos.jbr, with -XX:+AllowEnhancedClassRedefinition set only when JBR is in use, and confirm the javaLauncher override actually wins over MDG's default.
11. 11. Multiplayer: define client, client2 (--username Dev2 --quickPlayMultiplayer localhost:25565), server and serverSmoke runs, each with its own gameDirectory, and give the smoke run a different port (server-port=25599). Judge the smoke run by its marker file and the absence of new crash reports, never by exit code. Use createLaunchScripts (build/moddev/run<Name>.sh) to start several instances in parallel. Always test against a dedicated server rather than LAN, and automate per-player server logic with mock players, such as testframework's GameTestPlayer packet capture.
12. 12. Spend one day on a spike before feature work:
- measure L2, L3 and L4 cycle times on the M4 Pro;
- confirm the core merge works in both a dev run and the production jar via mc-runtime-test;
- confirm the Retina framebuffer size;
- check that an empty GameTest set and a deliberately broken mod make runGameTestServer and runServerSmoke fail through the new report and marker checks;
- check that the JBR launcher override and the build snippets work with the MDK's configuration cache enabled;
- generate a canonical options.txt for the scenario game directory.

## Open questions (from the original research)

- Actual wall-clock costs on the M4 Pro are unmeasured, since no Gradle runs were allowed: the FML JUnit bootstrap, the EphemeralTestServerProvider startup, the GameTest server cycle, the client scenario cycle and the dedicated-server boot.
- Under FML's early window on a Retina Mac, is the framebuffer really twice the window size (GLFW_COCOA_RETINA_FRAMEBUFFER defaults to true, and neither FML nor Minecraft sets it)? Does config/fml.toml earlyWindowWidth/Height, rather than --width/--height or options.txt overrideWidth, decide the final window size?
- Does 'implementation project(:core)' combined with the mods{} source-set merge cause any duplicate-class or classloader side effects in 1.21.1 dev runs? Static analysis says no; it has not been run.
- Do GameTest templates larger than 48 blocks per axis work? Structure-block NBT loading clamps to 48, but the in-memory loading path does not.
- Does a crashed dedicated server (DedicatedServer.onServerExit) exit with a non-zero code? I could not confirm it, which is why the smoke test uses a marker file.
- Can rendering continue with a hidden window on macOS (glfwHideWindow after start), so scenario runs do not take focus? Unverified.
- What are the exact value encodings of some options.txt keys in 1.21.1, such as renderClouds and fov? Generate the file from a real run to be sure.
- What are the exact mc-runtime-test 5.0.0 inputs for choosing a specific NeoForge 21.1.x build, and does it handle 1.21.1 assets without network flakiness?
- Does the jqwik anti-AI clause have licensing implications beyond the user guide (the POM license is still EPL-2.0), and does the reported 'jqwik.hideAntiAiClause' switch really exist? Moot if jqwik is avoided.
- When will me.champeau.jmh release with its merged Gradle 9 support? The plugin-free setup removes the need to wait.
- Does IntelliJ honour gradle/gradle-daemon-jvm.properties when importing and syncing, or does its Gradle JVM setting have to be aligned by hand?
- Should the wrapper move from the MDK's Gradle 9.2.1 to the current 9.8.2? MDG 2.0.148 should work, but I did not validate it.

## Fact-check verdicts

confirmed: 40, corrected: 14, refuted: 2, unverifiable: 2

| Verdict | Claim | Correction | Evidence |
|---|---|---|---|
| confirmed | NeoForge 21.1 (MC 1.21.1) is actively maintained: 21.1.264 was published 2026-10-19 (the MDK pins 21.1.262 from 2026-10-15), and net.neoforged:testframework 21.1.264 was published the same day (LGPL-2.1). | All versions and dates match. Builds come in bursts: 10 builds between 2026-10-03 and 2026-10-19, but only 5 in August–September 2026 (21.1.248 on Aug 1 to 21.1.252 on Sep 26). 'Several times a week' holds for October only. | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (lastUpdated 20261020; 21.1.264 is the newest 21.1.x). POM Last-Modified headers: 21.1.262 Thu 15 Oct 2026 01:43 GMT, 21.1.263 Sun 18 Oct 2026, 21.1.264 Mon 19 Oct 2026 03:40 GMT. testframework-21.1.264.pom Mon 19 Oct 2026 03:40 GMT. testframework jar META-INF/neoforge.mods.toml: modId="testframework", license="LGPL v2.1". neoforge-21.1.264.pom license 'LGPL 2.1'. /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/gradle.properties neo_version=21.1.262 |
| corrected | NeoForge publishes testframework for every build. | Not every build has one. 254 of the 259 21.1.x NeoForge versions have a testframework artifact; there is none for 21.1.236, 21.1.237, 21.1.240, 21.1.245 and 21.1.246. 21.1.262–264 do have one. | Version lists compared between https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml and https://maven.neoforged.net/releases/net/neoforged/testframework/maven-metadata.xml |
| confirmed | On MC <= 1.21.8, MDG runs load only mods, FMLModType jars and additionalRuntimeClasspath, so a plain-Java subproject added only via implementation project(':core') hits ClassNotFoundException. MDG's mods{} block supports source sets from other projects (RunUtils.findSourceSetProject), so merging core's source set into the mod (plus jar.from(core output)) gives an identical dev/prod layout. | Confirmed from the README and source; nothing was executed. FML's UserdevLocator also scans classpath jars that carry a MANIFEST.MF, but a jar with neither neoforge.mods.toml nor FMLModType is not loaded, which matches the README. The cross-project lookup breaks under Isolated Projects but not under the configuration cache. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-mdg/mdg/README.md:351-367; .../dl-mdg/mdg/src/main/java/net/neoforged/moddevgradle/internal/RunUtils.java:180-198 (comment 'will break with cross-project isolation, but that's expected'), :277 and :292 ('-Dfml.modFolders=' with '<mod>%%<dir>'); ModDevRunWorkflow.java:336-351 (per-run legacyClasspath = game libraries + additionalRuntimeClasspath); utils/VersionCapabilitiesInternal.java:98-100; IntelliJIntegration.java:232-244 and IntelliJOutputDirectoryValueSource.java:21-25 (delegated vs native output dirs); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-fmlloader/x-loader-sources/net/neoforged/fml/loading/moddiscovery/locators/UserdevLocator.java:38-45. The MDG clone is at HEAD e9c127db (2026-09-29), which matches the 2.0.148 publish on the same day. |
| confirmed | MDG unitTest uses NeoForge's 'junit' run type (forgejunitdev) with junit-fml 4.0.45. A LauncherSessionListener bootstraps the game once per JVM. JUnitMain runs Bootstrap and ServerModLoader as DEDICATED_SERVER with dist cleaning disabled. There is no server or level unless EphemeralTestServerProvider is used, and that loads data packs but no world. | Confirmed, with three details about EphemeralTestServerProvider. (1) It enables every available pack and every feature flag (FeatureFlags.REGISTRY.allFlags()), so experimental content is on. (2) Its initServer() never calls loadLevel(); the Javadoc's 'void overworld' wording is looser than the code. (3) Once started, its server thread ticks with no sleep (waitUntilNextTick = runAllTasks) for the rest of the test JVM. Dist cleaning is disabled only after ServerModLoader.load(), so classes loaded during mod construction are still dist-cleaned. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-nfconfig/moddev-config.json (runs.junit: --launchTarget forgejunitdev); .../dl-fmlloader/x-loader-sources/net/neoforged/fml/loading/targets/JUnitDevLaunchTarget.java:16-26; .../dl-junitfml/x-junitfml-sources/net/neoforged/fml/junit/JUnitService.java:24 and LaunchWrapper.java:25-26; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/junit/JUnitMain.java:15-24; .../dl-tf/x-tf-sources/net/neoforged/testframework/junit/EphemeralTestServerProvider.java:67,84,151,203,241-242; neoforge-21.1.262.module variant modDevTestFixtures -> junit-fml 4.0.45; MDG ModDevRunWorkflow.java:53 (build/minecraft-junit), :524 (fml.junit.argsfile); RunUtils.java:252-262 (test source set added to the tested mod) |
| confirmed | In 1.21.1 the headless GameTestServer uses a flat world (seed 0, structures off, creative) with mob spawning, weather, random ticks and fire tick disabled. It ticks without sleeping, places tests at random X/Z within ±14,999,992 at y=-59, and exits with System.exit(failed required count), or 1 on a crash. | Confirmed. In addition, difficulty is NORMAL, and every available data pack and every feature flag is enabled, so test worlds differ from a default world. The 'exit 1 on crash' path covers only crashes inside the server's run loop (see the next verdict). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/GameTestServer.java:57 (TEST_POSITION_RANGE), :65-70 (rules, WorldOptions(0L,false,false)), :82 (all packs + allFlags), :100 (WorldPresets.FLAT), :213-214 (waitUntilNextTick -> runAllTasks), :227 (System.exit(failedRequiredCount)), :237 (System.exit(1)), :242 (y -59), :280 (isDedicatedServer false); vanilla MinecraftServer.java:833-835 sleeps via managedBlock |
| corrected | An empty test set makes GameTestServer.create throw 'No test functions were given!', so the run fails. | The exception is thrown on the main thread, inside MinecraftServer.spin, before the server thread starts. The dedicated-server Main catches it in catch (Exception) and only logs 'Failed to start the minecraft server'; main() then returns normally. So the JVM very likely exits 0 and runGameTestServer passes green. A mod-loading error on the server side takes the same path, because ServerModLoader rethrows into that catch. This is inferred from source and was not executed. Guard against it: fail the task if the JUnit report is missing or has no <testcase>. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/GameTestServer.java:78; .../net/minecraft/server/MinecraftServer.java:265-276 (factory applied before thread.start); .../net/minecraft/server/Main.java:124 (ServerModLoader.load inside the try), :193-195 (gametest branch), :309-310 (catch logs only); .../net/neoforged/neoforge/server/loading/ServerModLoader.java:41-42 (dump crash report, rethrow); Util.startTimerHackThread sets setDaemon(true) (Util.java:757-770) |
| confirmed | 1.21.1 writes no GameTest report file by default; JUnitLikeTestReporter exists but nothing installs it; its XML nests testsuite inside testsuite. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/GlobalTestReporter.java:4 (DELEGATE = new LogTestReporter()); JUnitLikeTestReporter.java:27-29 (testsuite appended into a testsuite root); grep over net/ and com/ finds no other reference to JUnitLikeTestReporter |
| confirmed | neoforge.enabledGameTestNamespaces filters by template namespace (GameTest.templateNamespace, then GameTestHolder.value, then 'minecraft'). Event-registered tests without a holder or namespace are silently dropped, and the MDK sets the property to the mod id. | None. Generator-produced functions are filtered by the namespace of structureName. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/gametest/GameTestHooks.java:65-72,85-99; .../net/minecraft/gametest/framework/GameTestRegistry.java:57,66; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/build.gradle:58,64,72 |
| confirmed | In NeoForge dev runs IS_RUNNING_IN_IDE = !FMLLoader.isProduction(), so GameTests are enabled in every dev run, StructureTemplateManager loads .snbt from the mutable StructureUtils.testStructuresDir, and /test export writes SNBT there. | isGametestEnabled() is true in every dev run. Tests are registered, and /test exists, only where a server or client actually starts (client, server, gameTestServer); the data run starts neither. The SNBT loader ignores the namespace. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/SharedConstants.java:198; .../net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java:75,131-135,200-203; .../net/minecraft/gametest/framework/StructureUtils.java:38; TestCommand.java:404-445; GameTestHooks.java:32-34; Commands.java:217-218; registration call sites: server/Main.java:193, ServerLifecycleHooks.java:106, ClientHooks.java:1023 |
| confirmed | 1.21.1's GameTestRegistry.turnMethodIntoTestFunction passes requiredSuccesses into maxAttempts and attempts into requiredSuccesses. | None. Because isFlaky() is maxAttempts > 1, @GameTest(attempts=N) never retries. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/GameTestRegistry.java:156-157 vs TestFunction.java:15-16 (record order maxAttempts, requiredSuccesses); ReportGameListener.java:63-99 |
| confirmed | Fabric's client gametest API first shipped in Fabric API 0.112.1+1.21.4 (2024-12-16); the 1.21.1 line (latest 0.116.17+1.21.1, 2026-09-01) lacks it; NeoForge 21.1 has only server-side GameTest support. | None. | https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.112.0+1.21.4/fabric-api-0.112.0+1.21.4.pom (no client gametest); .../0.112.1+1.21.4/...pom lists fabric-client-gametest-api-v1 1.0.0+b47eab6b04 (Last-Modified 16 Dec 2024); .../0.116.17+1.21.1/...pom lists only fabric-gametest-api-v1 2.0.5 (Last-Modified 1 Sep 2026); package /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/gametest/ contains only BlockPosValueConverter, GameTestHolder, GameTestHooks, PrefixGameTestTemplate |
| confirmed | AE2 1.21.1 runs a client export under xvfb-run with libgl1-mesa-dri on GitHub Actions; GuideME renders into a TextureTarget, reads back via NativeImage.downloadTexture, writes PNG/WebP, then client.stop(). | The workflow also installs zopfli and does not run apt-get update. GuideME calls System.exit(1) when the export fails. | https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/1.21.1/.github/workflows/export_guide.yml ('sudo apt-get install xvfb libgl1-mesa-dri zopfli', 'DRI_PRIME=0 xvfb-run ./gradlew runGuideexport'); AE2 build.gradle guideexport run sets appeng.runGuideExportAndExit=true; https://raw.githubusercontent.com/AppliedEnergistics/GuideME/1.21.1/src/main/java/guideme/internal/siteexport/OffScreenRenderer.java:36,66,133-134; SiteExporter.java:105-119 |
| confirmed | macOS has no headless GL path; MDG adds -XstartOnFirstThread to client runs, data runs and the FML unit-test JVM; Linux CI uses Xvfb plus Mesa. | None. llvmpipe has been OpenGL 4.5 conformant since Mesa 20.x. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-mdg/mdg/src/main/java/net/neoforged/moddevgradle/internal/PrepareRunOrTest.java:154-157, PrepareRun.java:45-47, PrepareTest.java:29-31; 1.21.1 version JSON jvm rule {os: osx} -XstartOnFirstThread; https://www.khronos.org/news/permalink/mesas-llvmpipe-is-opengl-4.5-conformant |
| confirmed | Minecraft 1.21.1 requires Java 21 and ships LWJGL 3.3.3 with natives-macos-arm64; no linux-arm64 natives are listed. | None. The library versions fastutil 8.5.12, Guava 32.1.2-jre, Gson 2.10.1 and JOML 1.10.5 are also confirmed. | https://piston-meta.mojang.com/v1/packages/1757c8b1fbfc6b638504e1f6cceb05893f7389d5/1.21.1.json (javaVersion.majorVersion 21; org.lwjgl:*:3.3.3:natives-macos-arm64 present; 0 linux-arm64 entries) |
| corrected | jqwik >= 1.10 (1.10.0 on 2026-05-25 through 1.10.2 on 2026-10-10) ships an Anti-AI Usage Clause and agent-directed output; 1.10.0 told coding agents to delete tests and was pulled; the last release before the clause is 1.9.3 (2025-06-07, EPL-2.0). | Mostly right, but 1.10.0 was not pulled from distribution. It is still on Maven Central. The author's release notes say 'You should no longer use release 1.10.0', and an LWN reader reports it is gone from the GitHub releases page. 1.10.1 replaced it with a modified stdout line documented as the 'Anti-AI Usage Clause'. The user guide documents jqwik.hideAntiAiClause, but it only hides that line in terminal emulators; captured stdout, which is what an agent reads, still contains it. The POM licence is still EPL-2.0. Platforms: 1.10.x is built on JUnit Platform 1.14.4, 1.9.3 on 1.13.1. The decision (avoid jqwik >= 1.10) stands. | https://repo1.maven.org/maven2/net/jqwik/jqwik/1.10.0/jqwik-1.10.0.pom exists (Last-Modified 25 May 2026); 1.10.1 29 May 2026; 1.10.2 10 Oct 2026; 1.9.3 07 Jun 2025; https://jqwik.net/release-notes.html ('Starting with version 1.10 jqwik comes with an Anti-AI Usage Clause', 'Use of jqwik >= 1.10 with coding agents is strongly discouraged', 'You should no longer use release 1.10.0'); https://jqwik.net/docs/current/user-guide.html (jqwik.hideAntiAiClause); https://lwn.net/Articles/1075315/ (2026-05-29) |
| confirmed | ErrorProne >= 2.43 needs JDK 21+ to run (latest 2.50.0, 2026-06-10); NullAway JSpecify mode on JDK 21 needs -XDaddTypeAnnotationsToSymbol=true, JDK >= 21.0.8 and an OpenJDK build; Temurin 21.0.12.1 (2026-08-19) qualifies; JSpecify mode always honours restrictive annotations. | None. JDK 22+ javac works without the flag. | https://github.com/google/error-prone/releases/tag/v2.43.0 ('The minimum supported JDK version to run Error Prone is now JDK 21'); error_prone_core 2.50.0 POM Last-Modified 10 Jun 2026; https://github.com/uber/NullAway/wiki/JSpecify-Support (21.0.8, not Oracle JDK; use Temurin/Zulu); https://github.com/uber/NullAway/wiki/Configuration (AcknowledgeRestrictiveAnnotations 'always on in JSpecify mode'); https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=aarch64&os=mac -> 21.0.12.1+1, updated 2026-08-19 |
| confirmed | setup-gradle v6 (6.4.0) defaults to proprietary 'enhanced' caching (free for public repos, Free Preview for private); cache-provider: basic is MIT, free for all and caches all of ~/.gradle/caches. | Basic caching stores both ~/.gradle/caches and ~/.gradle/wrapper as a single entry. Its key hashes the build files and it has no restore keys, so any build-file change starts from an empty cache. Writing only from the default branch is already the default, so the cache-read-only expression is redundant. | https://raw.githubusercontent.com/gradle/actions/v6.4.0/docs/setup-gradle.md lines 7-10, 134-135, 157-160, 448-466; tag v6.4.0 published 2026-09-28 |
| corrected | The latest me.champeau.jmh is 0.7.3 (2025-01-30); Gradle 9 support was merged 2026-07-19 but is unreleased, so JMH should be wired as a plain source set plus JavaExec on Gradle 9.2.1. | The release facts are right. Whether 0.7.3 actually fails on Gradle 9.2.1 is unverified: PR #288 ('Support Gradle 9', merged 2026-07-19) mostly upgrades the plugin's own build (wrapper, Spock and the Gradle test matrix), and the old setCanBeResolved incompatibility was fixed back in 2023. The plugin-free setup is still a safe choice, but it is a choice, not a necessity. | https://plugins.gradle.org/m2/me/champeau/jmh/me.champeau.jmh.gradle.plugin/maven-metadata.xml (latest 0.7.3, lastUpdated 20250130); GitHub search API: PR #288 merged_at 2026-07-19T10:43:17Z, body 'Upgrades the build to support Gradle 9.x while maintaining compatibility with Gradle 8'; tags end at RELEASE_0_7_3 |
| confirmed | spark 1.10.124 for NeoForge 1.21.1 (2025-02-23, GPL-3.0-only) bundles spark-native/macos/libasyncProfiler.so; async-profiler 4.x officially supports macOS arm64. | The bundled macOS library is a universal x86_64+arm64 Mach-O, and spark's AsyncProfilerAccess accepts os.name macos/macosx. The Modrinth Maven coordinate maven.modrinth:spark:1.10.124-neoforge-1.21.1 resolves (HTTP 200). | https://api.modrinth.com/v2/project/spark (license GPL-3.0-only); versions?loaders=[neoforge]&game_versions=[1.21.1] -> 1.10.124-neoforge-1.21.1, 2025-02-23; jar listing and `file` on spark-native/macos/libasyncProfiler.so; https://raw.githubusercontent.com/async-profiler/async-profiler/v4.6/README.md lines 82-87 |
| corrected | async-profiler on macOS arm64: `asprof -d 30 -e cpu -o collapsed` gives CPU profiles. | On macOS, `-e cpu` silently falls back to the wall-clock engine, because perf_events and ctimer are Linux-only. For CPU profiles on the Mac use `-e itimer`. The same applies to JMH's `-prof async`: add `event=itimer`. | https://raw.githubusercontent.com/async-profiler/async-profiler/v4.6/docs/CpuSamplingEngines.md (table: macOS support ❌ cpu, ✅ itimer, ❌ ctimer); src/profiler.cpp:772-799 selectEngine (cpu -> perf_events, else ctimer, else wall_clock); src/perfEvents.h and src/ctimer.h: supported() returns false outside __linux__ |
| corrected | async-profiler v4.6 tag (2026-10-12). | The v4.6 tag commit is dated 2026-10-12; the GitHub release was published 2026-10-19. | GitHub API releases/tags/v4.6: created_at 2026-10-12T21:23:44Z, published_at 2026-10-19T23:51:47Z |
| corrected | In NeoForge dev the dedicated server auto-agrees to the EULA and online-mode defaults to false (NeoForge patched IS_RUNNING_IN_IDE into Eula and DedicatedServerProperties); the default username is 'Player' plus a 3-digit suffix. | The effect is right, but the mechanism is slightly different. Vanilla's Eula already accepts when IS_RUNNING_IN_IDE is true; NeoForge makes that constant true in dev, adds an auto-accept for the GameTest server, and patches only the online-mode default. The username suffix is System.currentTimeMillis() % 1000, so 1 to 3 digits. enforceSecureProfile() also requires online mode. | https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/patches/net/minecraft/server/Eula.java.patch and .../dedicated/DedicatedServerProperties.java.patch; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/Eula.java:20; .../dedicated/DedicatedServerProperties.java:48; .../dedicated/DedicatedServer.java:463-466; .../net/minecraft/client/main/Main.java:77 |
| confirmed | MDG 2.0.136 and later skip NeoForm decompilation/recompilation automatically when CI=true. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-mdg/mdg/README.md:111-117 |
| confirmed | GitHub Free private repos get 2,000 Actions minutes/month (Linux 2-core $0.006/min, macOS $0.062/min), 500 MB artifacts and 10 GB cache per repo. | None. Cache above 10 GB is billed only if the owner raises the limit. | https://docs.github.com/en/billing/concepts/product-billing/github-actions |
| confirmed | mc-runtime-test 5.0.0 (2026-10-12, MIT) launches a real client with the production jar via HeadlessMC and Xvfb, joins a world, quits; supports NeoForge 1.21.x; boot smoke only because dev GameTests are disabled in production. | None. The README lists no input for choosing a specific NeoForge build; inputs are mc, modloader, java, mc-runtime-test, xvfb and others. | https://raw.githubusercontent.com/headlesshq/mc-runtime-test/5.0.0/README.md (support table, inputs); release 5.0.0 published 2026-10-12T15:08:10Z; LICENSE 'MIT License' |
| confirmed | FML 4.0.45 ignores and deprecates @EventBusSubscriber.bus and allows private static handlers; RegisterGameTestsEvent is a mod-bus event. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-fmlloader/x-loader-sources/net/neoforged/fml/common/EventBusSubscriber.java:28 (private static example), :42 ('this value is ignored'), :70,80 (@Deprecated(since="1.21.1", forRemoval=true)); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/event/RegisterGameTestsEvent.java:24 (implements IModBusEvent) |
| refuted | Change the MDK's logLevel = DEBUG to INFO for console runs; debug output still goes to logs/debug.log. | MDG's logLevel sets the root level of its generated log4j2.xml, and the root level feeds both the Console and the DebugFile (logs/debug.log) appenders. At INFO, debug.log no longer gets DEBUG lines either; INFO is also MDG's default. To keep DEBUG in the file and INFO on the console, set the run's loggingConfigFile to a copy of MDG's generated config with level="info" on the Console AppenderRef. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-mdg/mdg/src/main/java/net/neoforged/moddevgradle/internal/RunUtils.java:110 (DebugFile logs/debug.log), :133-137 (<Root level="$ROOTLEVEL$"> with unfiltered Console and DebugFile refs); dsl/RunModel.java:58 (convention INFO), :252-260 (logLevel ignored if loggingConfigFile is set); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/build.gradle:96 |
| refuted | FML's early window hands its size to Minecraft's Window, so --width/--height alone may not stick; set earlyWindowWidth/Height in config/fml.toml. | FML's early window parses --width/--height itself. It falls back to the fml.toml earlyWindowWidth/Height values (854×480) only when those arguments are absent, writes the result back to fml.toml, and Minecraft's Window then takes the early window's size via positionWindow. So --width 1280 --height 720 sticks. The fml.toml entries are optional. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-earlydisplay/x-earlydisplay-sources/net/neoforged/fml/earlydisplay/DisplayWindow.java:120-132 (accepts width/height, defaultsTo fml.toml, updateConfig), :410 (glfwCreateWindow(winWidth, winHeight)); .../dl-fmlloader/x-loader-sources/net/neoforged/fml/loading/ImmediateWindowHandler.java:38,71 (initialize(arguments)); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/com/mojang/blaze3d/platform/Window.java:89-90 |
| corrected | config/fml.toml versionCheck = false means the run makes no network calls. | versionCheck only disables NeoForge's global update check. Whether the client makes other network calls (Mojang services and the like) is not ruled out (unverified). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-fmlloader/x-loader-sources/net/neoforged/fml/loading/FMLConfig.java:40 ('Enable NeoForge global version checking') |
| confirmed | All listed options.txt keys exist in 1.21.1 Options; the value encodings are unverified. | The keys exist, and the encodings can now be confirmed from source. fov is stored normalised as (fov-70)/40, so 0.0 means 70°. renderClouds accepts "false" (string) or false (bool), both meaning OFF. particles 2 = MINIMAL. narrator 0 = OFF. tutorialStep uses names, so none is valid. onboardAccessibility=false skips the accessibility onboarding screen. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/client/Options.java:153,589,701,712,1224,1238,1260; ParticleStatus.java:13; NarratorStatus.java:11; CloudStatus.java (OFF 'false'); Minecraft.java:685 |
| corrected | It is unknown whether a crashed dedicated server exits non-zero, so the smoke test checks a marker file. | The source settles the question; nothing was executed. A crash inside the run loop writes crash-reports/*.txt, then calls onServerCrash, which is a no-op for DedicatedServer, and onServerExit, which never calls System.exit. Only the ServerWatchdog exits with 1. Startup and mod-loading failures are caught in Main and only logged. A crashed dev server therefore very likely exits 0, so the marker file and crash-report checks are required, not optional. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/MinecraftServer.java:736-757, :914-915; .../dedicated/DedicatedServer.java:293-313; .../dedicated/ServerWatchdog.java:101-106; .../server/Main.java:309-310 |
| corrected | Subset run: ./gradlew :mod:runGameTestServer -Pgametest.filter='^combat\.' | With the report's own generator, test names are '<suite simple name lowercased>.<method lowercased>', for example 'combattests.flankcharge'. '^combat\.' therefore matches nothing, and the result is an empty test set that probably passes with exit 0. Use '^combattests\.'. The perf example '^perftests\.' is already correct. | The report's StrategosGameTests.all(): name = suite.getSimpleName().toLowerCase(Locale.ROOT) + "." + m.getName().toLowerCase(Locale.ROOT); SUITES = EconomyTests, CombatTests, PathingTests, PerfTests |
| corrected | Template = '<ns>:' + optional '<classname>.' + template() or the lowercase method name; the test name is the same minus the namespace. | The test name is always '<classname>.' (when prefixed) plus the lowercase method name. It equals the template path only when template() is empty. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/GameTestRegistry.java:138-145 |
| corrected | /test subcommands: run, runall, runclosest, runthat, runthese, runfailed, runmultiple, create, locate, export*, import, pos, clearall, reset*, stop, verify. | verifyclass, clearthat and clearthese also exist. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/TestCommand.java:148-251 (verifyclass :186, clearthat :222, clearthese :223) |
| corrected | FML / modlauncher / Mixin are LGPL-2.1 / MIT. | FML is LGPL-2.1-only, modlauncher 11.0.5 is LGPL-3.0-only, and sponge-mixin and MixinExtras are MIT. These are loader-provided runtime libraries, so the mod's own licence is not affected. | https://maven.neoforged.net/releases/cpw/mods/modlauncher/11.0.5/modlauncher-11.0.5-sources.jar (Launcher.java header: 'GNU Lesser General Public License ... version 3'); FML sources SPDX LGPL-2.1-only; sponge-mixin-0.16.4+mixin.0.8.7.pom 'The MIT License' |
| confirmed | Spotless Gradle plugin 8.10.4 needs JRE 17+ and Gradle 8.1+. | The changelog is the source for this: 8.1.0 (2025-11-18) raised the minimum Gradle to 8.1. The README still says 'Gradle 7.3 or newer', which is out of date. | https://raw.githubusercontent.com/diffplug/spotless/gradle/8.10.4/plugin-gradle/CHANGES.md ('Bump minimum supported Gradle version from 7.3 to 8.1'); README.md:166 |
| confirmed | Version table: Gradle 9.2.1 (2025-11-17) / 9.8.2 (2026-10-19); JUnit 5.14.4 (2026-04-26), 6.1.3 (2026-08-07); AssertJ 3.27.7 (2026-01-24); ArchUnit 1.5.1 (2026-09-25); JMH 1.37 (2023-08-03); Spotless 8.10.4 (2026-10-08); palantir 2.102.0 (2026-10-05); GJF 1.37.0 (2026-10-01); net.ltgt.errorprone 5.1.1 (2026-08-25); NullAway 0.14.2 (2026-09-25); JSpecify 1.0.1 (2026-07-29); Shadow 9.6.1; mod-publish-plugin 2.2.1 (2026-09-21); Checkstyle 14.3.0; PMD 7.28.0; checkout v7.0.1, setup-java v6.1.0 (2026-10-14), gradle/actions v6.4.0, upload-artifact v7.0.2; MDK workflow pins v4. | Two small additions. net.ltgt.gradle:gradle-errorprone-plugin is published on the Gradle Plugin Portal, not Maven Central. The MDK workflow pins checkout, setup-java and setup-gradle at v4 and has no upload-artifact step. | https://services.gradle.org/versions/all; Maven Central maven-metadata.xml + POM Last-Modified for each artifact; https://plugins.gradle.org/m2/ metadata for net.ltgt.errorprone, com.gradleup.shadow (9.6.1, 2026-07-22), me.modmuss50.mod-publish-plugin, com.diffplug.spotless; git ls-remote tags; GitHub release dates (setup-java v6.1.0 2026-10-14, checkout v7.0.1 2026-07-20, upload-artifact v7.0.2 2026-10-07, gradle/actions v6.4.0 2026-09-28); /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/.github/workflows/build.yml |
| confirmed | mod-publish-plugin 2.0.0 requires Gradle >= 9.0; CurseForge client/server flags are mandatory since 2.0; a dry-run mode exists; MIT. | Precisely, at least one of client or server must be set. | mod-publish-plugin docs/pages/index.mdx changelog 2.0.0 ('Update minimum supported Gradle version to 9.0.0', 'CurseForge client and/or server properties are now required'), docs/pages/platforms/curseforge.mdx:45, index.mdx:9 (dry run); LICENSE MIT |
| confirmed | Gradle 9 requires an explicit testRuntimeOnly 'org.junit.platform:junit-platform-launcher'; Gradle 9.x runs on Java 17–25 and Java 25 needs >= 9.1.0. | None. | https://docs.gradle.org/current/userguide/upgrading_version_8.html (automatic loading of test framework implementation dependencies 'deprecated and will be removed in Gradle 9.0.0'); https://docs.gradle.org/9.2.1/userguide/compatibility.html ('A JVM version between 17 and 25 is required'; Java 25 row 9.1.0) |
| confirmed | gradle/gradle-daemon-jvm.properties via `updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium` takes precedence over JAVA_HOME; MDG's run tasks call launcherFor(languageVersion) ignoring the vendor; foojay prefers Temurin; foojay serves JBR 21.0.11 for macOS aarch64; -XX:+AllowEnhancedClassRedefinition stops non-JBR JVMs. | Unverified part: whether the report's JBR override, a tasks.matching configureEach that sets javaLauncher, wins over MDG's own launcher setting depends on Gradle's configuration-action order. Check it in the spike. | https://docs.gradle.org/9.2.1/userguide/gradle_daemon.html; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-mdg/mdg/src/main/java/net/neoforged/moddevgradle/internal/ModDevRunWorkflow.java:418; foojay-toolchains-plugin-1.0.0 distributions.kt:36 (distributionOrderOfPreference Temurin, AOJ) and README vendor table (JETBRAINS -> JetBrains); https://api.foojay.io/disco/v3.0/packages?version=21&distribution=jetbrains&architecture=aarch64&operating_system=macos -> jbrsdk_jcef-21.0.11-osx-aarch64-b1163.116; local test: JDK 23 `java -XX:+AllowEnhancedClassRedefinition -version` -> 'Unrecognized VM option', JVM not created |
| confirmed | Minecraft has built-in JFR (--jfrProfile on client and server; /jfr start\|stop writes debug/<env>-<time>.jfr; custom events ServerTickTime, PacketSent/Received, ChunkGeneration); JDK 21 has `jfr view hot-methods` and `jcmd <pid> JFR.view`. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/util/profiling/jfr/JfrProfiler.java (CUSTOM_EVENTS list, Paths.get("debug/%s-%s.jfr")); client/main/Main.java:65 and server/Main.java:83 (jfrProfile); Commands.java:213-214 (JfrCommand when JFR available); JDK-8306703 integrated May 2023 for JDK 21 (https://mail.openjdk.org/pipermail/build-dev/2023-May/039536.html, https://www.inside.java/2023/09/26/sip082/); local `jfr --help view` lists hot-methods |
| confirmed | Tick-time APIs and commands: MinecraftServer#getTickTimesNanos (long[100]), getAverageTickTimeNanos, getCurrentSmoothedTickTime (ms EMA), NeoForge getTickTime(ResourceKey<Level>), /tick query (P50/P95/P99), /tick freeze\|step\|sprint, /neoforge tps. | getTickTime(ResourceKey) is @Nullable. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/server/MinecraftServer.java:228,955,1743-1757,1784-1786; server/commands/TickCommand.java:25-54,91-96; net/neoforged/neoforge/server/command/NeoForgeCommand.java:17-18 |
| confirmed | Client harness APIs: Screenshot.takeScreenshot(RenderTarget) is synchronous while Screenshot.grab writes on Util.ioPool(); NativeImage.writeToFile(Path); WorldOpenFlows.createFreshLevel(String, LevelSettings, WorldOptions, Function<RegistryAccess, WorldDimensions>, Screen); LevelSettings 7-arg constructor; RenderFrameEvent.Post; ClientTickEvent.Post; LevelRenderer.hasRenderedAllSections(); Minecraft.getOverlay()/stop(); WorldDataConfiguration.DEFAULT. | RenderFrameEvent.Post fires right after gameRenderer.render() and before the blit to screen, so a capture there includes the HUD and GUI. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/client/Screenshot.java:52-92; com/mojang/blaze3d/platform/NativeImage.java:546; net/minecraft/client/gui/screens/worldselection/WorldOpenFlows.java:79-81; net/minecraft/world/level/LevelSettings.java:16-18; net/minecraft/client/Minecraft.java:847,1199-1216,1608,2062,2514,2852; net/minecraft/client/renderer/LevelRenderer.java:3604; net/minecraft/world/level/WorldDataConfiguration.java:20 |
| confirmed | NeoForge registers its mod_data pack with PackSelectionConfig(required=true), so WorldDataConfiguration.DEFAULT still loads mod data. | This holds while the mod keeps the default showAsDataPack=false. With showAsDataPack=true, the mod's pack becomes a separate pack that is not required. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/neoforged/neoforge/resource/ResourcePackLoader.java:63,108-131,195-205 |
| confirmed | Neither FML's early window nor Minecraft sets GLFW_COCOA_RETINA_FRAMEBUFFER; GLFW defaults to true, so expect a 2× framebuffer on Retina. | Neither sets the hint, and GLFW's default is GLFW_TRUE (now named GLFW_SCALE_FRAMEBUFFER). That the framebuffer is actually 2× in practice is still an inference to confirm in the spike. | grep for COCOA/RETINA finds nothing in /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-earlydisplay/x-earlydisplay-sources or /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/com/mojang/blaze3d; https://www.glfw.org/docs/3.4/window_guide.html (GLFW_SCALE_FRAMEBUFFER default GLFW_TRUE; COCOA_RETINA_FRAMEBUFFER alias) |
| confirmed | Keep @SubscribeEvent handlers non-private, or ErrorProne's UnusedMethod may fire; the ErrorProne Gradle plugin forks javac and adds the needed --add-exports itself. | Because the compiler is forked, ErrorProne's heap belongs to the compiler fork (options.forkOptions.memoryMaximumSize), not to the Gradle daemon (see the §12 memory note). | https://raw.githubusercontent.com/google/error-prone/v2.50.0/core/src/main/java/com/google/errorprone/bugpatterns/WellKnownKeep.java (exempting method annotations include com.google.common.eventbus.Subscribe but not net.neoforged.bus.api.SubscribeEvent); https://raw.githubusercontent.com/tbroyer/gradle-errorprone-plugin/v5.1.1/README.md lines 160-177 (JDK 16+ fork + JVM args), 276-302 (DSL, CheckSeverity import) |
| confirmed | Minecraft's javax.annotation.@Nullable (e.g. Minecraft.player, Level.getBlockEntity) is honoured by NullAway JSpecify mode. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/client/Minecraft.java:343-344; net/minecraft/world/level/Level.java:803-805; NullAway Configuration wiki |
| confirmed | Stay on JUnit 5.14.x: the ecosystem is built on Platform 1.x (AE2 1.21.1 uses 5.10.2; jqwik 1.10.x depends on Platform 1.14.4). | Further support: NeoForge's own junit-fml 4.0.45 imports junit-bom 5.10.2, and ArchUnit 1.5.1's engine API depends on junit-platform-engine 1.14.4. | https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/1.21.1/gradle.properties (junit_version=5.10.2); https://maven.neoforged.net/releases/net/neoforged/fancymodloader/junit-fml/4.0.45/junit-fml-4.0.45.module (junit-bom 5.10.2); archunit-junit5-engine-api-1.5.1.pom; jqwik-engine-1.10.1/1.10.2 POMs |
| confirmed | Create's 1.21.1 approach: CreateTestFunction.getTestsFrom + @GameTestGenerator, structures under data/create/structure/gametest, CI runs ./gradlew runGameTestServer. | None. | Create mc1.21.1/dev (7ff2760e): src/main/java/com/simibubi/create/infrastructure/gametest/CreateGameTests.java:19-38, CreateTestFunction.java:52, src/main/resources/data/create/structure/gametest/, .github/workflows/build.yml:34 |
| confirmed | MineClient Bridge 1.1.3 (MIT, NeoForge 1.21.1, 2026-09-10, about 95 downloads) offers a localhost HTTP API with a bearer token: framebuffer PNG, state, input, commands, shutdown. | It is distributed on CurseForge; it is not on Modrinth. It binds 127.0.0.1:38121 and is enabled by default. | https://www.curseforge.com/minecraft/mc-mods/mineclient-bridge |
| confirmed | Fabric client gametest design reference: forced options (tutorial off, clouds off, onboarding off, render distance 5, music 0) and MeanSquaredDifference default 0.005. | Newer versions also turn off anisotropic filtering and chunk fade-in. | FabricMC/fabric branch 1.21.11 fabric-client-gametest-api-v1/.../impl/client/gametest/context/ClientGameTestContextImpl.java:90-108; .../api/client/gametest/v1/screenshot/TestScreenshotComparisonAlgorithm.java:35-51 |
| confirmed | createLaunchScripts generates shell scripts under mod/build/moddev/ (e.g. runClient2.sh). | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-mdg/mdg/src/main/java/net/neoforged/moddevgradle/internal/RunUtils.java getLaunchScript (nameOfRun(run, "run", ".sh")); ModDevArtifactsWorkflow.java:83 (build/moddev); ModDevRunWorkflow.java:265,410 |
| confirmed | GameTest batches: grouped by batch name, split into chunks of 50, batches run sequentially, tests within a batch concurrently; @BeforeBatch/@AfterBatch take a ServerLevel; manualOnly honoured only by /test finders. | None. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/GameTestBatchFactory.java:15-26; GameTestRunner.java:80-135 (runBatch(index + 1) when a batch is done); GameTestRegistry.java:25-26,71-86; TestFinder.java:132,140; server/Main.java:195 (getAllTestFunctions()) |
| confirmed | The runner force-loads chunks, clears space (stone below minY-1, air above, 20 up), puts the structure block at minY-1 and encases with barriers; structure blocks clamp saved sizes to MAX_SIZE_PER_AXIS = 48. | The cleared box is minX-2..maxX+3, minY-3..maxY+20, minZ-3..maxZ+3. The roof barrier is skipped when skyAccess is set. The layer at minY-1 is air apart from the structure block, so arenas need their own floor. Whether templates larger than 48 work remains unverified. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mc-src-1.21.1/net/minecraft/gametest/framework/StructureUtils.java:105-180,255-266; GameTestInfo.java:269; net/minecraft/world/level/block/entity/StructureBlockEntity.java:37,93-95 |
| confirmed | testframework's ExtendedGameTestHelper.makeTickingMockServerPlayerInLevel returns a GameTestPlayer that answers keep-alives, is set up via NetworkRegistry.configureMockConnection, and records outbound payloads (getOutboundPayloads, requireOutboundPayload); @EmptyTemplate(floor=true) exists. | None. The sources are LGPL-2.1-only (SPDX header). | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-tf/x-tf-sources/net/neoforged/testframework/gametest/ExtendedGameTestHelper.java:113-150; GameTestPlayer.java:125-141; EmptyTemplate.java:28 |
| corrected | NeoForge testframework offers in-game test screens/overlay, enable/disable commands and side(); it is for manual tests, not visual automation. | The screens, overlay, commands and side() exist. testframework also drives automated server-side GameTests (ExtendedGameTestHelper, @EmptyTemplate, GameTestPlayer), so 'manual' describes only its client part. | /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/verify-testing/dl-tf/x-tf-sources/net/neoforged/testframework/client/TestScreen.java, client/TestsOverlay.java, impl/Commands.java, annotation/TestHolder.java (side()), gametest/* |
| unverifiable | The Gradle daemon needs org.gradle.jvmargs=-Xmx2g because the MDK's 1G is tight with ErrorProne. | The reason given is doubtful. The ErrorProne plugin forks javac, so ErrorProne runs in the compiler fork, whose heap is set by options.forkOptions.memoryMaximumSize, not in the daemon. Raising the daemon heap to 2g may still help MDG and IDE sync; measure it in the spike. | https://raw.githubusercontent.com/tbroyer/gradle-errorprone-plugin/v5.1.1/README.md:160-177; /private/tmp/claude-501/-Users-oleksandrlytvynskyi-work-pet-projects-minecraft/ee58a1af-b4f8-4282-b3ae-c341fb060cea/scratchpad/mdk-1.21.1/gradle.properties (org.gradle.jvmargs=-Xmx1G) |
| unverifiable | Wall-clock costs on the M4 Pro (L2 5–15 s bootstrap, L3 40–120 s, L4 45–120 s, etc.) and memory totals. | These are estimates; no runs were allowed. Measure them in the spike. | No Gradle runs were permitted; none were executed during verification. |
