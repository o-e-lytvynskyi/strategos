package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.OperatingSystemMXBean;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.BudgetCheck;
import dev.strategos.core.testkit.Percentiles;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.persist.MatchData;
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
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;

/**
 * The L6 perf GameTests (spec 7.6), one per batch, in their own run ({@code runGameTestPerf}). Each samples the foreign
 * CPU load over its window and writes its JSON with the percentiles and a verdict: {@code pass} or {@code fail} for a
 * gate listed in {@code strategos.perf.gates}, {@code report-only} for one that is not, and {@code invalid} when the
 * mean foreign load is above 0.25, in which case the test asserts nothing and the Gradle guard stops the build.
 */
public final class PerfTests {
    private static final long MS = 1_000_000L;
    private static final double INVALID_FOREIGN_LOAD = 0.25;
    private static final Vec2d WEST = new Vec2d(8.5, 8.5);
    private static final Vec2d EAST = new Vec2d(39.5, 39.5);
    private static final int WARM_UP = 200;
    private static final int SAMPLES = 600;
    private static final int REORDER_EVERY = 120;

    private PerfTests() {}

    /** Budget A (PERF-01.1, QA-07.2): one match's tick with 600 walking units, p50 <= 5 ms and p99 <= 10 ms. */
    @StrategosTest(batch = "perf_walk", arena = "arena_flat_48", timeoutTicks = 1_200)
    public static void walk600BudgetA(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, 300, 6));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, 300, 6));
        Window window = new Window();
        var state = new Object() {
            long firstOrderAt = -1;
            long orderedAt = -1;
            boolean toEast = true;
            int samples;
            long[] total = new long[SAMPLES];
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().unitCount() == 600 && rt.bodies().count() == 600,
                        "the 600 units and bodies are not there yet"))
                .thenWaitUntil(() -> {
                    long tick = core(rt).queries().tick();
                    if (state.orderedAt < 0 || tick - state.orderedAt >= REORDER_EVERY) {
                        reorder(rt, state.toEast);
                        state.toEast = !state.toEast;
                        state.orderedAt = tick;
                        if (state.firstOrderAt < 0) {
                            state.firstOrderAt = tick;
                        }
                    }
                    if (tick - state.firstOrderAt >= WARM_UP) {
                        if (state.samples == 0) {
                            window.start();
                        }
                        state.total[state.samples++] = core(rt).timers().lastTotalNanos();
                        if (state.samples % 20 == 0) {
                            window.sampleForeignLoad();
                        }
                    }
                    check(state.samples >= SAMPLES, "sampling: " + state.samples + " of " + SAMPLES);
                })
                .thenExecute(() -> {
                    long[] mspt = Arrays.stream(h.getLevel().getServer().getTickTimesNanos())
                            .filter(n -> n > 0)
                            .toArray();
                    JsonObject extra = new JsonObject();
                    extra.addProperty("mspt_p50_ns", Percentiles.percentile(mspt, 0.50));
                    extra.addProperty("mspt_p99_ns", Percentiles.percentile(mspt, 0.99));
                    verdict(
                            h,
                            "walk_600",
                            "a",
                            new BudgetCheck("budget A", scaled(5 * MS), scaled(10 * MS)),
                            state.total,
                            window,
                            extra);
                })
                .thenSucceed();
    }

    /**
     * Budget G (PERF-01.1, CORE-07.6): saving a 500-unit match's blob, median <= 100 ms over ten saves, one every 20
     * ticks, so the foreign load is sampled every 20 ticks of the window like the other tests'.
     */
    @StrategosTest(batch = "perf_snapshot", arena = "arena_flat_48", timeoutTicks = 600)
    public static void snapshot500BudgetG(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, 250, 6));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, 250, 6));
        Window window = new Window();
        long[] saves = new long[10];
        GameTestSequence sequence = h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitCount() == 500, "the 500 units are not there yet"))
                .thenExecute(() -> reorder(rt, true))
                .thenExecuteAfter(20, window::start);
        for (int i = 0; i < saves.length; i++) {
            int save = i;
            sequence.thenExecuteAfter(20, () -> {
                MatchData data = rt.data();
                check(data != null, "the saved match has no blob");
                long start = System.nanoTime();
                CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
                saves[save] = System.nanoTime() - start;
                check(tag.contains("snapshot"), "the blob saved no snapshot");
                window.sampleForeignLoad();
            });
        }
        sequence.thenExecute(() -> verdict(
                        h,
                        "snapshot_500",
                        "g",
                        new BudgetCheck("budget G", scaled(100 * MS), Long.MAX_VALUE),
                        saves,
                        window,
                        new JsonObject()))
                .thenSucceed();
    }

    private static void reorder(MatchRuntime rt, boolean toEast) {
        for (int f = 0; f < 2; f++) {
            Vec2d target = (f == 0) == toEast ? EAST : WEST;
            int[] units = core(rt).queries().unitHandlesOf(new FactionSlot(f));
            rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), target));
        }
    }

    private static long scaled(long limit) {
        return Math.round(limit * Double.parseDouble(System.getProperty("strategos.perf.ciFactor", "1")));
    }

    /** Writes the result and fails a listed gate on a breach; an invalid run asserts nothing. */
    private static void verdict(
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
                        System.getProperty("strategos.perf.gates", "a,g").split(","))
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

    private static void write(String name, JsonObject json) {
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
    private static final class Window {
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
