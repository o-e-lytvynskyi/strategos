package dev.strategos.gametest.suites;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.OperatingSystemMXBean;
import dev.strategos.core.testkit.BudgetCheck;
import dev.strategos.core.testkit.Percentiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * What the perf GameTests share: their verdicts and JSON files ({@code pass}, {@code fail}, {@code report-only} or
 * {@code invalid}, see {@link PerfTests}), the soaks' judgement, the gate list, the CI factor and the foreign-load
 * window. Split out of {@link PerfTests} in M3 T1, so the suite keeps its tests and their names.
 */
final class PerfVerdicts {
    static final long MS = 1_000_000L;
    static final double INVALID_FOREIGN_LOAD = 0.25;

    /**
     * M1's crowd soak per unit (M1 spec §8.5, its close's five runs), the base PERF-06.2 compares with: budget A's p99,
     * 3.37 ms, for 600 units, an upper bound on its p95, which M1 did not record; and budget B's p95, 4.5 ms.
     */
    static final double M1_A_PER_UNIT = 3.37e6 / 600;

    static final double M1_B_PER_UNIT = 4.5e6 / 600;

    /** PERF-06.2: a unit-driven bucket's p95 per unit grows at most 15 % over M1's. */
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
        double foreign = window.meanForeignLoad();
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
        json.addProperty("foreignCpu", foreign);
        SoakCosts costs = r.costs();
        if (costs != null) {
            json.add("firstLoop", firstLoop(r, costs, b, broken, !stress && !fast));
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
        String verdict = foreign > INVALID_FOREIGN_LOAD ? "invalid" : failed.isEmpty() ? "pass" : "fail";
        json.addProperty("verdict", verdict);
        write(r.config().name(), json);
        if (verdict.equals("fail")) {
            h.fail(String.join("; ", failures.isEmpty() ? broken : failures));
        } else {
            h.succeed();
        }
    }

    /**
     * The first loop's report (PERF-06.2 and .4): the placements' commands phase, the writer's and the fx's time a tick,
     * the scene's counts, the blob's save in the window (budget G, at most 100 ms), and A's and B's p95 per unit against
     * M1's; with {@code gates}, A per unit gates under {@code a} and the save under {@code g}.
     */
    private static JsonObject firstLoop(SoakResult r, SoakCosts costs, long[] b, List<String> broken, boolean gates) {
        JsonObject json = new JsonObject();
        json.add("placement", spread(costs.placement()));
        json.add("writer", spread(costs.writer()));
        json.add("fx", spread(costs.fx()));
        json.addProperty("placed", costs.placed());
        json.addProperty("placementsRefused", costs.refused());
        json.addProperty("waves", costs.waves());
        json.addProperty("buildingsAtEnd", costs.buildings());
        json.addProperty("saveNanos", costs.saveNanos());
        if (gates && gated("g") && costs.saveNanos() > scaled(100 * MS)) {
            broken.add("budget G: the save took " + costs.saveNanos() + " ns, above " + scaled(100 * MS));
        }
        int units = 2 * r.config().perFaction();
        double aPerUnit = (double) Percentiles.percentile(r.core(), 0.95) / units;
        double bPerUnit = (double) Percentiles.percentile(b, 0.95) / units;
        JsonObject perUnit = new JsonObject();
        perUnit.addProperty("units", units);
        perUnit.addProperty("aP95PerUnitNs", aPerUnit);
        perUnit.addProperty("aM1PerUnitNs", M1_A_PER_UNIT);
        perUnit.addProperty("aGrowth", aPerUnit / M1_A_PER_UNIT);
        perUnit.addProperty("bP95PerUnitNs", bPerUnit);
        perUnit.addProperty("bM1PerUnitNs", M1_B_PER_UNIT);
        perUnit.addProperty("bGrowth", bPerUnit / M1_B_PER_UNIT);
        perUnit.addProperty(
                "note",
                "A gates; B is reported: below 600 units the tick's fixed work, not the units, makes most of B's p95");
        json.add("perUnit", perUnit);
        if (gates && gated("a") && aPerUnit > M1_A_PER_UNIT * PER_UNIT_GROWTH * ciFactor()) {
            broken.add(String.format(
                    "budget A per unit: p95 %.0f ns a unit, over %.0f (M1's %.0f + 15 %%)",
                    aPerUnit, M1_A_PER_UNIT * PER_UNIT_GROWTH, M1_A_PER_UNIT));
        }
        return json;
    }

    /** A sample's count, p50, p99 and largest, in nanoseconds. */
    private static JsonObject spread(long[] samples) {
        JsonObject json = new JsonObject();
        json.addProperty("samples", samples.length);
        if (samples.length == 0) {
            return json;
        }
        json.addProperty("p50", Percentiles.percentile(samples, 0.50));
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
        double foreign = window.meanForeignLoad();
        List<String> violations = budget.violations(samples);
        boolean listed = Arrays.asList(
                        System.getProperty("strategos.perf.gates", "a,c,d,e,g").split(","))
                .contains(gate);
        String verdict;
        if (foreign > INVALID_FOREIGN_LOAD) {
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
        json.addProperty("foreignCpu", foreign);
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
     * The foreign CPU load over a measured window: the machine's load minus this process's, floored at 0, from
     * {@code com.sun.management.OperatingSystemMXBean}; {@code strategos.perf.fakeForeignLoad} replaces it (QA-07.3).
     */
    static final class Window {
        private final List<Double> loads = new ArrayList<>();

        /** The window opens: a first reading, discarded, so the first sample covers the window and not the time before. */
        void start() {
            OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            os.getCpuLoad();
            os.getProcessCpuLoad();
        }

        void sampleForeignLoad() {
            String fake = System.getProperty("strategos.perf.fakeForeignLoad", "");
            if (!fake.isBlank()) {
                loads.add(Double.parseDouble(fake));
                return;
            }
            OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            double machine = os.getCpuLoad();
            double process = os.getProcessCpuLoad();
            loads.add(machine < 0 || process < 0 ? 0 : Math.max(0, machine - process));
        }

        double meanForeignLoad() {
            return loads.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }
}
