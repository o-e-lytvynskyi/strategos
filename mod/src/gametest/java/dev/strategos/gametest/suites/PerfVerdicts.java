package dev.strategos.gametest.suites;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.strategos.core.sim.econ.EconomyTimes;
import dev.strategos.core.testkit.BattleScene;
import dev.strategos.core.testkit.BudgetCheck;
import dev.strategos.core.testkit.EconomyScene;
import dev.strategos.core.testkit.ForeignLoad;
import dev.strategos.core.testkit.ForeignLoad.Assessment;
import dev.strategos.core.testkit.ForeignLoad.Reading;
import dev.strategos.core.testkit.Percentiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestHelper;
import org.jspecify.annotations.Nullable;

/**
 * What the perf GameTests share: their verdicts and JSON files ({@code pass}, {@code fail}, {@code report-only} or
 * {@code invalid}, see {@link PerfTests}), the soaks' judgement, the gate list, the CI factor and the foreign-load
 * window. Split out of {@link PerfTests} in M3 T1, so the suite keeps its tests and their names.
 */
final class PerfVerdicts {
    static final long MS = 1_000_000L;
    static final double INVALID_FOREIGN_LOAD = 0.25;

    /**
     * The first-loop soak per unit on the M4 Pro ({@code perf/first-loop-m4pro.json}), the base both scenes' soaks
     * compare with (PERF-06.2 and PERF-07.2): budget A's p95 and budget B's p95 a unit, re-taken in M4 T22 on map v3
     * with the workers gathering. M1's crowd soak, 600 walkers, was PERF-06's base while the first loop's workers stood
     * idle in the arena; gathering, they cost twice a walker's share, another load.
     */
    static final double M3_A_PER_UNIT = 11_648;

    static final double M3_B_PER_UNIT = 12_630;

    /**
     * The economy soak per unit on the M4 Pro ({@code perf/economy-m4pro.json}, {@code perUnitP95Ns}), the base the
     * battle soak compares with (PERF-08): budget A's p95 a unit with 316 units alive.
     */
    static final double M4_A_PER_UNIT = 6_878;

    static final double M4_B_PER_UNIT = 10_109;

    /** PERF-06.2 and PERF-07.2: a unit-driven bucket's p95 per unit grows at most 15 % over its base's. */
    static final double PER_UNIT_GROWTH = 1.15;

    private PerfVerdicts() {}

    /**
     * The soak's verdicts: its own failures always (the melee soak's and the first loop's only reported: fighting units
     * stand by design), each budget as its gate says; an invalid run asserts nothing. A soak paced faster than 20 ticks
     * a second gates only budget C, its p95 at most 23 ms over the speed (MATCH-05.4: 15.3 ms at 1.5).
     */
    static void judgeSoak(GameTestHelper h, SoakResult r, Window window, boolean stress) {
        boolean melee = r.config().melee();
        boolean loose = melee || r.config().firstLoop();
        boolean fast = r.config().speed() > 1;
        long without = Percentiles.percentile(r.baseline(), 0.50);
        long[] b = Arrays.stream(r.tick()).map(t -> Math.max(0, t - without)).toArray();
        long[] c = new long[r.tick().length];
        for (int i = 0; i < c.length; i++) {
            c[i] = r.tick()[i] + r.between()[i];
        }
        double seconds = r.tick().length / 20.0;
        List<String> broken = new ArrayList<>();
        JsonObject budgets = new JsonObject();
        if (fast) {
            long limit = scaled(Math.round(23 * MS / r.config().speed()));
            budgets.add(
                    "c", budget("c", "budget C at " + r.config().speed() + "x", c, 0.95, limit, broken, gated("c")));
            budgets.add("a", budget("a", "budget A", r.core(), 0.99, Long.MAX_VALUE, broken, false));
        } else if (stress) {
            budgets.add("c", budget("c", "budget C, stress", c, 0.95, scaled(50 * MS), broken, true));
            budgets.add("a", budget("a", "budget A", r.core(), 0.99, Long.MAX_VALUE, broken, false));
            budgets.add("d", budget("d", "budget D", r.paths(), 0.99, Long.MAX_VALUE, broken, false));
        } else {
            budgets.add("a", budget("a", "budget A", r.core(), 0.50, scaled(5 * MS), broken, gated("a")));
            budgets.add("a_p99", budget("a", "budget A", r.core(), 0.99, scaled(10 * MS), broken, gated("a")));
            budgets.add("b", budget("b", "budget B", b, 0.95, scaled(17 * MS), broken, gated("b")));
            budgets.add("c", budget("c", "budget C", c, 0.95, scaled(23 * MS), broken, gated("c")));
            budgets.add("d", budget("d", "budget D", r.paths(), 0.99, scaled(2 * MS), broken, gated("d") && !melee));
            JsonArray viewers = new JsonArray();
            for (long bytes : r.viewerBytes()) {
                long perSecond = Math.round(bytes / seconds);
                JsonObject viewer = new JsonObject();
                viewer.addProperty("bytesPerSecond", perSecond);
                viewer.addProperty("limit", 100 * 1024);
                if (perSecond > 100 * 1024 && gated("e") && !melee) {
                    broken.add("budget E: " + perSecond + " bytes per second for a viewer, above 102,400");
                }
                viewers.add(viewer);
            }
            budgets.add("e", viewers);
            budgets.addProperty("e_bodies_paired_mean", r.viewerUnits());
            budgets.addProperty("e_bodies_paired_most", r.viewerUnitsMost());
            if (r.navBytes() > 32L << 20 && gated("g") && !melee) {
                broken.add("budget G: nav takes " + r.navBytes() + " bytes at 384²");
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("test", r.config().name());
        json.addProperty("seed", r.seed());
        json.addProperty("units", r.units());
        json.addProperty("ticks", r.config().ticks());
        json.addProperty("samples", r.tick().length);
        json.addProperty("baselineTickP50", without);
        json.addProperty("baselineTickP95", Percentiles.percentile(r.baseline(), 0.95));
        json.addProperty("tickP50", Percentiles.percentile(r.tick(), 0.50));
        json.addProperty("tickP95", Percentiles.percentile(r.tick(), 0.95));
        json.addProperty("betweenTicksMax", Arrays.stream(r.between()).max().orElse(0));
        json.add("budgets", budgets);
        json.addProperty("navBytes", r.navBytes());
        json.addProperty("groupOrders", r.groupOrders());
        json.addProperty("unitOrders", r.unitOrders());
        json.addProperty("failedOrders", r.failedOrders());
        json.addProperty("nudges", r.nudges());
        JsonArray rungs = new JsonArray();
        Arrays.stream(r.rungs()).forEach(rungs::add);
        json.add("ladderRungs", rungs);
        JsonArray histogram = new JsonArray();
        Arrays.stream(r.stuck()).forEach(histogram::add);
        json.add("stuckHistogram", histogram);
        json.addProperty("stuckHistogramBuckets", "<40, 40, 100, 200, 400, 600+ ticks without progress");
        json.addProperty("mostStuck", r.mostStuck());
        json.addProperty("worst", r.worst());
        json.addProperty("journal", String.valueOf(r.journal()));
        window.addTo(json);
        SoakCosts costs = r.costs();
        if (costs != null) {
            json.add(
                    r.config().scene() instanceof BattleScene.Config
                            ? "battle"
                            : r.config().scene() instanceof EconomyScene.Config ? "economy" : "firstLoop",
                    firstLoop(r, costs, b, broken, !stress && !fast));
        }
        if (melee) {
            json.addProperty("waves", r.fights()[0]);
            json.addProperty("hits", r.fights()[1]);
            json.addProperty("deaths", r.fights()[2]);
            JsonArray notes = new JsonArray();
            r.failures().forEach(notes::add); // the crowd soak's own checks: fighting units stand by design
            json.add("crowdChecks", notes);
        }
        if (loose && !melee) {
            JsonArray notes = new JsonArray();
            r.failures().forEach(notes::add);
            json.add("crowdChecks", notes);
        }
        List<String> failures = loose ? List.of() : r.failures();
        JsonArray failed = new JsonArray();
        failures.forEach(failed::add);
        broken.forEach(failed::add);
        json.add("violations", failed);
        String verdict = window.gateInvalid() ? "invalid" : failed.isEmpty() ? "pass" : "fail";
        json.addProperty("verdict", verdict);
        write(r.config().name(), json);
        if (verdict.equals("fail")) {
            h.fail(String.join("; ", failures.isEmpty() ? broken : failures));
        } else {
            h.succeed();
        }
    }

    /**
     * A scene's report, the first loop's (PERF-06.2 and .4) or the economy's (PERF-07.2 and .4): the placements'
     * commands phase, the writer's and the fx's time a tick, the scene's counts, the node index's and the dropsite
     * queries' times, the blob's save in the window (budget G, at most 100 ms), A's split into the core's step, its
     * phases and the mod's part, and A's and B's p95 per unit against the first loop's base; with {@code gates}, A per
     * unit gates under {@code a} and the save under {@code g}.
     */
    private static JsonObject firstLoop(SoakResult r, SoakCosts costs, long[] b, List<String> broken, boolean gates) {
        JsonObject json = new JsonObject();
        json.add("placement", spread(costs.placement()));
        json.add("writer", spread(costs.writer()));
        json.add("fx", spread(costs.fx()));
        json.add("adapter", spread(costs.adapter())); // budget A's mod part; the rest is the core's step
        long[] step = new long[Math.min(r.core().length, costs.adapter().length)];
        for (int i = 0; i < step.length; i++) {
            step[i] = r.core()[i] - costs.adapter()[i];
        }
        json.add("step", spread(step));
        JsonObject phases = new JsonObject(); // the step's phases
        for (int p = 0; p < costs.phaseNames().size(); p++) {
            long[] v = new long[costs.phases().length];
            for (int i = 0; i < v.length; i++) {
                v[i] = costs.phases()[i] == null ? 0 : costs.phases()[i][p];
            }
            phases.add(costs.phaseNames().get(p), spread(v));
        }
        json.add("phases", phases);
        json.addProperty("placed", costs.placed());
        json.addProperty("placementsRefused", costs.refused());
        json.addProperty("waves", costs.waves());
        json.addProperty("buildingsAtEnd", costs.buildings());
        json.addProperty("saveNanos", costs.saveNanos());
        EconomyTimes.Stats e = costs.economy();
        JsonObject economy = new JsonObject(); // PERF-07.4
        economy.addProperty("nodeIndexSearches", e.searches());
        economy.addProperty("nodeIndexSearchNsMean", e.searches() == 0 ? 0 : e.searchNanos() / e.searches());
        economy.addProperty("nodeIndexBuilds", e.builds());
        economy.addProperty("nodeIndexBuildNsMean", e.builds() == 0 ? 0 : e.buildNanos() / e.builds());
        economy.addProperty("dropsiteQueries", e.dropsites());
        economy.addProperty("dropsiteQueryNsMean", e.dropsites() == 0 ? 0 : e.dropsiteNanos() / e.dropsites());
        json.add("economyTimes", economy);
        if (gates && gated("g") && costs.saveNanos() > scaled(100 * MS)) {
            broken.add("budget G: the save took " + costs.saveNanos() + " ns, above " + scaled(100 * MS));
        }
        boolean battle = r.config().scene() instanceof BattleScene.Config; // against M4's economy soak
        boolean m4 = battle || r.config().scene() instanceof EconomyScene.Config;
        double aBase = battle ? M4_A_PER_UNIT : M3_A_PER_UNIT;
        double bBase = battle ? M4_B_PER_UNIT : M3_B_PER_UNIT;
        int units = 2 * r.config().perFaction();
        double aPerUnit = (double) Percentiles.percentile(r.core(), 0.95) / units;
        double bPerUnit = (double) Percentiles.percentile(b, 0.95) / units;
        JsonObject perUnit = new JsonObject();
        perUnit.addProperty("units", units);
        perUnit.addProperty(
                "base", battle ? "the economy soak, map v3 (M4 T28)" : "the first-loop soak, map v3 (M4 T22)");
        perUnit.addProperty("aP95PerUnitNs", aPerUnit);
        perUnit.addProperty("aBasePerUnitNs", aBase);
        perUnit.addProperty("aGrowth", aPerUnit / aBase);
        perUnit.addProperty("bP95PerUnitNs", bPerUnit);
        perUnit.addProperty("bBasePerUnitNs", bBase);
        perUnit.addProperty("bGrowth", bPerUnit / bBase);
        perUnit.addProperty(
                "note",
                "A gates; B is reported: below 600 units the tick's fixed work, not the units, makes most of B's p95");
        json.add("perUnit", perUnit);
        if (gates && gated("a") && aPerUnit > aBase * PER_UNIT_GROWTH * ciFactor()) {
            broken.add(String.format(
                    "budget A per unit: p95 %.0f ns a unit, over %.0f (the %s's base %.0f + 15 %%)",
                    aPerUnit, aBase * PER_UNIT_GROWTH, battle || m4 ? "economy" : "first loop", aBase));
        }
        SoakCosts.Combat combat = costs.combat();
        if (combat != null) {
            json.add("combat", combat(combat, r.core().length));
        }
        return json;
    }

    /**
     * PERF-08: what the combat systems cost a tick (nanoseconds, over the sampled window): the units' targeting, the
     * centres' volleys, the shot system, the damage system and their sum, and the window's searches, shots fired,
     * hits and deaths with the means per tick.
     */
    private static JsonObject combat(SoakCosts.Combat c, int ticks) {
        JsonObject json = new JsonObject();
        json.add("targetingPerTick", spread(c.targeting()));
        json.add("volleysPerTick", spread(c.volleys()));
        json.add("shotsPerTick", spread(c.shots()));
        json.add("damagePerTick", spread(c.damage()));
        long[] sum = new long[c.targeting().length];
        for (int i = 0; i < sum.length; i++) {
            sum[i] = c.targeting()[i] + c.volleys()[i] + c.shots()[i] + c.damage()[i];
        }
        json.add("combatPerTick", spread(sum));
        json.addProperty("targetingSearches", c.searches());
        json.addProperty("targetingSearchesPerTick", ticks == 0 ? 0 : (double) c.searches() / ticks);
        json.addProperty("shotsFired", c.shotsFired());
        json.addProperty("shotsFiredPerTick", ticks == 0 ? 0 : (double) c.shotsFired() / ticks);
        json.addProperty("hits", c.hits());
        json.addProperty("hitsPerTick", ticks == 0 ? 0 : (double) c.hits() / ticks);
        json.addProperty("deaths", c.deaths());
        return json;
    }

    /** A sample's count, p50, p95, p99 and largest, in nanoseconds. */
    private static JsonObject spread(long[] samples) {
        JsonObject json = new JsonObject();
        json.addProperty("samples", samples.length);
        if (samples.length == 0) {
            return json;
        }
        json.addProperty("p50", Percentiles.percentile(samples, 0.50));
        json.addProperty("p95", Percentiles.percentile(samples, 0.95));
        json.addProperty("p99", Percentiles.percentile(samples, 0.99));
        json.addProperty("max", Arrays.stream(samples).max().orElse(0));
        return json;
    }

    /** One budget's figure as JSON; a breach of a gated one joins {@code broken}. */
    private static JsonObject budget(
            String gate, String name, long[] samples, double q, long limit, List<String> broken, boolean gates) {
        long value = Percentiles.percentile(samples, q);
        JsonObject json = new JsonObject();
        json.addProperty("gate", gate);
        json.addProperty("percentile", q);
        json.addProperty("ns", value);
        json.addProperty("p50", Percentiles.percentile(samples, 0.50));
        json.addProperty("max", Arrays.stream(samples).max().orElse(0));
        json.addProperty("limit", limit == Long.MAX_VALUE ? -1 : limit);
        boolean over = value > limit;
        json.addProperty("verdict", limit == Long.MAX_VALUE || !gates ? "report-only" : over ? "fail" : "pass");
        if (over && gates) {
            broken.add(name + ": p" + Math.round(q * 100) + " " + value + " ns above the limit " + limit);
        }
        return json;
    }

    /** Whether a gate is listed in {@code strategos.perf.gates}, so it fails the run rather than reports. */
    static boolean gated(String gate) {
        return Arrays.asList(
                        System.getProperty("strategos.perf.gates", "a,c,d,e,g").split(","))
                .contains(gate);
    }

    static long scaled(long limit) {
        return Math.round(limit * ciFactor());
    }

    static double ciFactor() {
        return Double.parseDouble(System.getProperty("strategos.perf.ciFactor", "1"));
    }

    /** Writes the result and fails a listed gate on a breach; an invalid run asserts nothing. */
    static void verdict(
            GameTestHelper h,
            String name,
            String gate,
            BudgetCheck budget,
            long[] samples,
            Window window,
            JsonObject extra) {
        List<String> violations = budget.violations(samples);
        boolean listed = Arrays.asList(
                        System.getProperty("strategos.perf.gates", "a,c,d,e,g").split(","))
                .contains(gate);
        String verdict;
        if (window.gateInvalid()) {
            verdict = "invalid";
        } else if (!listed) {
            verdict = "report-only";
        } else {
            verdict = violations.isEmpty() ? "pass" : "fail";
        }
        JsonObject json = new JsonObject();
        json.addProperty("test", name);
        json.addProperty("gate", gate);
        json.addProperty("unit", "ns");
        json.addProperty("samples", samples.length);
        json.addProperty("p50", Percentiles.percentile(samples, 0.50));
        json.addProperty("p95", Percentiles.percentile(samples, 0.95));
        json.addProperty("p99", Percentiles.percentile(samples, 0.99));
        json.addProperty("max", Arrays.stream(samples).max().orElse(0));
        json.addProperty("p50Limit", budget.p50Limit());
        json.addProperty("p99Limit", budget.p99Limit());
        window.addTo(json);
        json.addProperty("verdict", verdict);
        JsonArray broken = new JsonArray();
        violations.forEach(broken::add);
        json.add("violations", broken);
        extra.entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        write(name, json);
        if (verdict.equals("fail")) {
            h.fail(String.join("; ", violations) + " (ns)");
        }
    }

    static void write(String name, JsonObject json) {
        if (json.has("foreignCpu")) {
            json.addProperty("foreignCpuSource", Window.source());
        }
        Path dir = Path.of(System.getProperty("strategos.perf.out", "build/reports/perf"));
        try {
            Files.createDirectories(dir);
            Files.writeString(
                    dir.resolve(name + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The foreign CPU load over a measured window, from the one-second readings of {@link ForeignLoadSampler} (plus a
     * fresh reading at each end), all cumulative counters over the same interval. A counter that cannot be read makes
     * the window unavailable and the run {@code invalid}, never zero. The explicit
     * {@code strategos.perf.fakeForeignLoad} self-test override is identified in every report (QA-07.3). The
     * assessment is taken once, at the first question after the window ends, so every verdict of one window agrees.
     */
    static final class Window {
        /** The first window's start in the JVM: the preflight is everything the sampler read before it. */
        private static final AtomicReference<@Nullable Long> FIRST_START = new AtomicReference<>();
        /**
         * The shortest span the load is judged over. Mach ticks are sampled at 100 Hz while the process's CPU time is
         * exact, so a window of a few ticks gives nonsense (a 46 ms window read 56 % with one busy core of twelve,
         * calibration (b)); a shorter window is judged by the sampler's readings just before it.
         */
        private static final long MIN_SPAN_NANOS = 10_000_000_000L;

        private @Nullable Reading startReading;
        private @Nullable String startFailure;
        private long startNanos;
        private double windowSeconds;
        private boolean started;
        private @Nullable ForeignLoad frozen;

        /** The window opens: its first reading. */
        void start() {
            started = true;
            frozen = null;
            startReading = null;
            startFailure = null;
            startNanos = System.nanoTime();
            FIRST_START.compareAndSet(null, startNanos);
            if (fake() != null) {
                return;
            }
            try {
                startReading = ForeignLoadSampler.now();
            } catch (CpuCounters.Unavailable e) {
                startFailure = e.getMessage();
            }
        }

        /** The sampler thread already reads every second; kept so the callers' per-second hooks need no change. */
        void sampleForeignLoad() {}

        private static @Nullable Double fake() {
            String fake = System.getProperty("strategos.perf.fakeForeignLoad", "");
            return fake.isBlank() ? null : Double.parseDouble(fake);
        }

        private ForeignLoad load() {
            ForeignLoad load = frozen;
            if (load == null) {
                load = measure();
                frozen = load;
            }
            return load;
        }

        private ForeignLoad measure() {
            Double fake = fake();
            if (fake != null) {
                return ForeignLoad.fixed(fake);
            }
            if (!started) {
                return ForeignLoad.unavailable("CPU measurement window was not started");
            }
            String failure = ForeignLoadSampler.failure();
            if (failure != null) {
                return ForeignLoad.unavailable(failure);
            }
            Reading first = startReading;
            if (first == null) {
                return ForeignLoad.unavailable(startFailure == null ? "no first CPU reading" : startFailure);
            }
            Reading end;
            try {
                end = ForeignLoadSampler.now();
            } catch (CpuCounters.Unavailable e) {
                return ForeignLoad.unavailable(e.getMessage());
            }
            windowSeconds = (end.nanos() - first.nanos()) / 1e9;
            long from = Math.min(first.nanos(), end.nanos() - MIN_SPAN_NANOS);
            List<Reading> readings = new ArrayList<>(ForeignLoadSampler.between(from, first.nanos() - 1));
            readings.add(first);
            for (Reading r : ForeignLoadSampler.between(first.nanos(), end.nanos())) {
                if (r.nanos() > first.nanos() && r.nanos() < end.nanos()) {
                    readings.add(r);
                }
            }
            readings.add(end);
            return ForeignLoad.of(readings);
        }

        private ForeignLoad preflight() {
            Long first = FIRST_START.get();
            if (first == null || fake() != null) {
                return ForeignLoad.unavailable("no preflight");
            }
            return ForeignLoad.of(ForeignLoadSampler.between(Long.MIN_VALUE, first));
        }

        private Assessment assessment() {
            return load().assess(
                            Runtime.getRuntime().availableProcessors(),
                            INVALID_FOREIGN_LOAD,
                            preflight().seconds());
        }

        /** The mean foreign load over the window (1 when it could not be measured). */
        double meanForeignLoad() {
            return load().mean();
        }

        /** True when the run is invalid under the gate: the mean is over the limit, or the load is unavailable. */
        boolean gateInvalid() {
            return assessment().gateInvalid();
        }

        /** The window's load in a result file: the gate's number, and the telemetry beside it. */
        void addTo(JsonObject json) {
            json.addProperty("foreignCpu", meanForeignLoad());
            json.add("foreignLoad", telemetry());
        }

        JsonObject telemetry() {
            ForeignLoad load = load();
            Assessment a = assessment();
            int cpus = Runtime.getRuntime().availableProcessors();
            JsonObject json = new JsonObject();
            json.addProperty("source", source());
            json.addProperty("logicalCpus", cpus);
            json.addProperty("intervalSeconds", fake() != null ? 0 : 1);
            json.addProperty("intervals", load.intervals());
            json.addProperty("windowSeconds", windowSeconds);
            json.addProperty("seconds", load.seconds());
            json.addProperty("available", load.available());
            json.addProperty("mean", load.mean());
            json.addProperty("p95", load.p95());
            json.addProperty("peak", load.peak());
            json.addProperty("gateLimit", INVALID_FOREIGN_LOAD);
            json.addProperty("proposedMeanLimit", ForeignLoad.proposedMeanLimit(cpus));
            json.addProperty("proposedBurstLimit", ForeignLoad.proposedBurstLimit(cpus));
            json.addProperty("proposedValid", a.proposedValid());
            JsonArray reasons = new JsonArray();
            a.reasons().forEach(reasons::add);
            json.add("reasons", reasons);
            ForeignLoad pre = preflight();
            JsonObject preflight = new JsonObject();
            preflight.addProperty("seconds", pre.seconds());
            preflight.addProperty("mean", pre.mean());
            preflight.addProperty("p95", pre.p95());
            preflight.addProperty("peak", pre.peak());
            json.add("preflight", preflight);
            return json;
        }

        /** Names where the numbers came from, in every report. */
        static String source() {
            return fake() != null ? "self-test override" : CpuCounters.source();
        }
    }
}
