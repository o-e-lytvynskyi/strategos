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
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.BudgetCheck;
import dev.strategos.core.testkit.Percentiles;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.PathLoad;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.world.BlockChangeRouter;
import dev.strategos.mod.server.world.BlockWriter;
import dev.strategos.mod.server.world.PageJobs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
    private static final int WRITER_WARM_UP = 100;
    private static final int WRITES_PER_TICK = 64;
    private static final int GROUP_RESULTS_TICKS = 5;
    private static final int PATH_WARM_UP = 2;
    private static final int PATH_RUNS = 10;

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
     * QA-19.3, budget E's first look (report-only until PERF-05 gates it): what one acknowledging mock viewer at the
     * arena's centre receives while 600 units walk as in budget A, per tick and per packet type, ours and vanilla's.
     */
    @StrategosTest(batch = "perf_viewer", arena = "arena_flat_48", timeoutTicks = 1_200)
    public static void viewerBytes600(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, 300, 6));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, 300, 6));
        Vec3 centre = h.absoluteVec(new Vec3(24, 2, 24));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-perf", centre.x, centre.y, centre.z, 4);
        viewer.capture().keep(false);
        var state = new Object() {
            long firstOrderAt = -1;
            long orderedAt = -1;
            boolean toEast = true;
            int samples;
            long last;
            final long[] perTick = new long[SAMPLES];
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    viewer.poll();
                    check(
                            core(rt).queries().unitCount() == 600 && rt.bodies().count() == 600,
                            "the 600 units and bodies are not there yet");
                })
                .thenWaitUntil(() -> {
                    viewer.poll();
                    long tick = core(rt).queries().tick();
                    if (state.orderedAt < 0 || tick - state.orderedAt >= REORDER_EVERY) {
                        reorder(rt, state.toEast);
                        state.toEast = !state.toEast;
                        state.orderedAt = tick;
                        if (state.firstOrderAt < 0) {
                            state.firstOrderAt = tick;
                        }
                    }
                    if (tick - state.firstOrderAt == WARM_UP) {
                        viewer.bytes().clear();
                        state.last = 0;
                    } else if (tick - state.firstOrderAt > WARM_UP && state.samples < SAMPLES) {
                        long now = viewer.bytes().bytes();
                        state.perTick[state.samples++] = now - state.last;
                        state.last = now;
                    }
                    check(state.samples >= SAMPLES, "sampling: " + state.samples + " of " + SAMPLES);
                })
                .thenExecute(() -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("test", "viewer_bytes_600");
                    json.addProperty("gate", "e");
                    json.addProperty("unit", "bytes per tick");
                    json.addProperty("samples", SAMPLES);
                    json.addProperty("p50", Percentiles.percentile(state.perTick, 0.50));
                    json.addProperty("p95", Percentiles.percentile(state.perTick, 0.95));
                    json.addProperty("p99", Percentiles.percentile(state.perTick, 0.99));
                    json.addProperty("max", Arrays.stream(state.perTick).max().orElse(0));
                    json.addProperty("bytesPerSecond", viewer.bytes().bytes() * 20 / SAMPLES);
                    json.addProperty("budgetBytesPerSecond", 100 * 1024);
                    json.addProperty("verdict", "report-only");
                    json.add("viewer", viewer.bytes().toJson());
                    write("viewer_bytes_600", json);
                    viewer.close();
                    check(
                            viewer.bytes().failures().isEmpty(),
                            "unencodable packets: " + viewer.bytes().failures());
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

    /**
     * BLD-15.4: the writer's cost at its full budget: 64 blocks a tick, stone and air in turn over four 16 x 16 sites
     * that take turns, 600 ticks after 100 of warm-up; the samples are the {@code setBlock} time of each tick's 64
     * writes. Report-only under gate {@code c} (PERF-01 has no per-block threshold); the reference limits are 5 % of
     * budget C for a tick's writes at p99 (1.15 ms) and a tenth of that at the median.
     */
    @StrategosTest(batch = "perf_writer", arena = "arena_flat_48", timeoutTicks = 1_200)
    public static void blockWriterCost(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        ServerLevel level = h.getLevel();
        BlockWriter writer = StrategosServer.get().writer();
        int ticks = WRITER_WARM_UP + SAMPLES + 8;
        int perSite = ticks * WRITES_PER_TICK / 4;
        BlockState[] states = {Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState()};
        for (int site = 0; site < 4; site++) {
            BlockPos corner = rt.space().origin().offset(site % 2 * 16, 2, site / 2 * 16);
            for (int i = 0; i < perSite; i++) {
                writer.enqueue(
                        level,
                        new WriteSite(rt.id().value(), site + 1),
                        WritePriority.CONSTRUCTION,
                        corner.offset(i % 16, 0, i / 16 % 16),
                        states[i / 256 % 2],
                        0);
            }
        }
        Window window = new Window();
        var state = new Object() {
            long lastNanos = writer.writeNanos();
            long lastPlacements = writer.placements();
            int seen;
            int samples;
            long[] perTick = new long[SAMPLES];
            long[] perBlock = new long[SAMPLES];
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    long nanos = writer.writeNanos() - state.lastNanos;
                    long placed = writer.placements() - state.lastPlacements;
                    state.lastNanos = writer.writeNanos();
                    state.lastPlacements = writer.placements();
                    if (placed > 0 && state.seen++ >= WRITER_WARM_UP) {
                        if (state.samples == 0) {
                            window.start();
                        }
                        state.perTick[state.samples] = nanos;
                        state.perBlock[state.samples++] = nanos / placed;
                        if (state.samples % 20 == 0) {
                            window.sampleForeignLoad();
                        }
                    }
                    check(state.samples >= SAMPLES, "sampling: " + state.samples + " of " + SAMPLES);
                })
                .thenExecute(() -> {
                    StrategosServer.get().matches().dispose(rt.id());
                    JsonObject extra = new JsonObject();
                    extra.addProperty("ns_per_block_p50", Percentiles.percentile(state.perBlock, 0.50));
                    extra.addProperty("ns_per_block_p99", Percentiles.percentile(state.perBlock, 0.99));
                    extra.addProperty("blocks_per_tick", WRITES_PER_TICK);
                    verdict(
                            h,
                            "block_writer_cost",
                            "c",
                            new BudgetCheck("the writer's 64 blocks a tick", scaled(115_000), scaled(1_150_000)),
                            state.perTick,
                            window,
                            extra);
                })
                .thenSucceed();
    }

    /**
     * PATH-15.4: page capture and publication on the main thread with every page of a 48 x 48 match changing every 8
     * ticks: at most 4 pages captured a tick, and the capture plus the join of due pages (with the inline executor,
     * their builds too) at most 0.4 ms at p95 over 600 ticks. Also PATH-15.6, for the S8 report: what the block-change
     * router costs per change outside any match.
     */
    @StrategosTest(batch = "perf_pages", arena = "arena_flat_48", timeoutTicks = 1_200)
    public static void pageCaptureAndPublication(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        Window window = new Window();
        var state = new Object() {
            int ticks;
            int samples;
            int mostCaptured;
            long[] nanos = new long[SAMPLES];
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().pageRefs().size() == 9, "prewarm"))
                .thenWaitUntil(() -> {
                    int t = state.ticks++;
                    if (t % 8 == 0) {
                        for (int page = 0; page < 9; page++) {
                            BlockPos at = new BlockPos(page % 3 * 16 + 8, 2, page / 3 * 16 + 8);
                            h.setBlock(at, (t / 8) % 2 == 0 ? Blocks.STONE : Blocks.AIR);
                        }
                    }
                    if (t >= WRITER_WARM_UP) {
                        if (state.samples == 0) {
                            window.start();
                        }
                        state.nanos[state.samples++] =
                                rt.pages().lastCaptureNanos() + rt.pages().lastJoinNanos();
                        state.mostCaptured =
                                Math.max(state.mostCaptured, rt.pages().lastCaptured());
                        if (state.samples % 20 == 0) {
                            window.sampleForeignLoad();
                        }
                    }
                    check(state.samples >= SAMPLES, "sampling: " + state.samples + " of " + SAMPLES);
                })
                .thenExecute(() -> {
                    check(state.mostCaptured <= PageJobs.PER_TICK, state.mostCaptured + " pages captured in a tick");
                    check(
                            rt.pages().captured() > 9 + 60,
                            "too few rebuilds: " + rt.pages().captured());
                    JsonObject extra = new JsonObject();
                    extra.addProperty("pages_captured", rt.pages().captured());
                    extra.addProperty("most_pages_a_tick", state.mostCaptured);
                    extra.addProperty("router_ns_outside_matches", routerNanos(h));
                    verdict(
                            h,
                            "page_capture_and_publication",
                            "pages",
                            new BudgetCheck("page capture and publication", Long.MAX_VALUE, Long.MAX_VALUE),
                            state.nanos,
                            window,
                            extra);
                    long p95 = Percentiles.percentile(state.nanos, 0.95);
                    if (window.meanForeignLoad() <= INVALID_FOREIGN_LOAD) {
                        check(p95 <= scaled(400_000), "capture and publication take " + p95 + " ns at p95");
                    }
                })
                .thenSucceed();
    }

    /**
     * PATH-15.5, budget G: a 512² region of the test world, 64 blocks high, prewarms in at most 30 s of wall time (every
     * page captured, built and published on the first tick), and its nav takes at most 32 MB.
     */
    @StrategosTest(batch = "perf_prewarm", arena = "arena_flat_48", timeoutTicks = 2_000)
    public static void prewarm512Generated(GameTestHelper h) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(corner.getX() + 8_192, corner.getY(), corner.getZ());
        MatchSpace space =
                new MatchSpace(h.getLevel().dimension(), origin, 512, 512, origin.getY(), origin.getY() + 63);
        long start = System.nanoTime();
        MatchRuntime rt = MatchFixture.create(h, space, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().pageRefs().size() == 1_024, "prewarm"))
                .thenExecute(() -> {
                    long millis = (System.nanoTime() - start) / MS;
                    long bytes = core(rt).queries().surfaces().bytes();
                    JsonObject json = new JsonObject();
                    json.addProperty("test", "prewarm_512_generated");
                    json.addProperty("prewarm_ms", millis);
                    json.addProperty("nav_bytes", bytes);
                    write("prewarm_512_generated", json);
                    check(millis <= 30_000, "the prewarm took " + millis + " ms");
                    check(bytes <= 32L << 20, "nav takes " + bytes + " bytes");
                })
                .thenSucceed();
    }

    /**
     * PATH-16.4, budget D: {@link PathLoad} with the match rules' defaults (spike S7's {@code k}, delivery and W) and
     * the server's path threads, {@value #PATH_WARM_UP} runs to warm up and {@value #PATH_RUNS} measured, their ticks and
     * latencies pooled: the main thread's path work at most 2 ms at p99, and the results of the 100-unit order behind
     * 200 requests within 5 ticks at p95.
     */
    @StrategosTest(batch = "perf_paths", arena = "arena_flat_48", timeoutTicks = 200)
    public static void pathBudgetD(GameTestHelper h) {
        Window window = new Window();
        h.startSequence()
                .thenExecute(() -> {
                    MatchRules d = MatchRules.DEFAULTS;
                    MatchRules rules = PathLoad.rules(d.delivery(), d.pathK(), d.admissionW());
                    PathLoad load = new PathLoad();
                    for (int k = 0; k < PATH_WARM_UP; k++) {
                        load.run(rules, StrategosServer.get().jobs());
                    }
                    window.start();
                    List<PathLoad.Result> runs = new ArrayList<>();
                    for (int k = 0; k < PATH_RUNS; k++) {
                        runs.add(load.run(rules, StrategosServer.get().jobs()));
                        window.sampleForeignLoad();
                    }
                    PathLoad.Result r = PathLoad.Result.pooled(runs);
                    JsonObject extra = new JsonObject();
                    extra.addProperty("threads", StrategosServer.get().jobs().threads());
                    extra.addProperty("delivery", d.delivery().id());
                    extra.addProperty("k", d.pathK());
                    extra.addProperty("w", d.admissionW());
                    extra.addProperty("join_p99_ns", r.joinP99());
                    extra.addProperty("group_p50_ticks", r.groupP50());
                    extra.addProperty("group_p95_ticks", r.groupP95());
                    extra.addProperty("group_max_ticks", r.groupMax());
                    extra.addProperty("all_p95_ticks", r.allP95());
                    extra.addProperty("ticks_measured", r.ticks());
                    extra.addProperty("unfinished_joins", r.stats().unfinished());
                    verdict(
                            h,
                            "path_budget_d",
                            "d",
                            new BudgetCheck("budget D", Long.MAX_VALUE, scaled(2 * MS)),
                            r.work(),
                            window,
                            extra);
                    if (window.meanForeignLoad() <= INVALID_FOREIGN_LOAD && gated("d")) {
                        check(
                                r.groupP95() <= GROUP_RESULTS_TICKS,
                                "the 100-unit order's results take " + r.groupP95() + " ticks at p95");
                    }
                })
                .thenSucceed();
    }

    /** Whether a gate is listed in {@code strategos.perf.gates}, so it fails the run rather than reports. */
    private static boolean gated(String gate) {
        return Arrays.asList(System.getProperty("strategos.perf.gates", "a,g").split(","))
                .contains(gate);
    }

    /** What the router costs per block change outside any match, averaged over 100,000 calls. */
    private static long routerNanos(GameTestHelper h) {
        StrategosServer s = StrategosServer.get();
        BlockPos far = h.absolutePos(BlockPos.ZERO).offset(100_000, 0, 0);
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            BlockChangeRouter.route(
                    s, h.getLevel(), far, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState());
        }
        return (System.nanoTime() - start) / 100_000;
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
