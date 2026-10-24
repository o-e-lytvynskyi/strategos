package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.PerfVerdicts.INVALID_FOREIGN_LOAD;
import static dev.strategos.gametest.suites.PerfVerdicts.MS;
import static dev.strategos.gametest.suites.PerfVerdicts.gated;
import static dev.strategos.gametest.suites.PerfVerdicts.judgeSoak;
import static dev.strategos.gametest.suites.PerfVerdicts.scaled;
import static dev.strategos.gametest.suites.PerfVerdicts.verdict;
import static dev.strategos.gametest.suites.PerfVerdicts.write;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.MatchControl;
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
import dev.strategos.core.testkit.EconomyScene;
import dev.strategos.core.testkit.FirstLoopScene;
import dev.strategos.core.testkit.Percentiles;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.PathLoad;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.suites.PerfVerdicts.Window;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.world.BlockChangeRouter;
import dev.strategos.mod.server.world.BlockWriter;
import dev.strategos.mod.server.world.PageJobs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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
    private static final int SOAK_TICKS = Integer.getInteger("strategos.soak.ticks", 12_000);
    private static final int STRESS_TICKS = 2_400;
    private static final int SOAK_LANE = 1;
    private static final int STRESS_LANE = 2;
    private static final int MELEE_LANE = 3;
    private static final int MELEE_TICKS = 6_000;
    private static final int FIRST_LOOP_TICKS = Integer.getInteger("strategos.firstLoop.ticks", 18_000);
    private static final int FIRST_LOOP_SHORT_TICKS = 6_000;
    private static final int FIRST_LOOP_LANE = 4;
    private static final int FIRST_LOOP_STRESS_LANE = 5;
    private static final int FIRST_LOOP_FAST_LANE = 6;
    private static final int ECONOMY_TICKS = Integer.getInteger("strategos.economy.ticks", 18_000);
    private static final int ECONOMY_LANE = 7;
    private static final int ECONOMY_STRESS_LANE = 8;

    private PerfTests() {}

    /**
     * UNIT-05.5: 300 standing units cost at most 30 % of the same 300 walking, budget A's p50 each over {@value
     * #SAMPLES} ticks after {@value #WARM_UP} of warm-up: first standing, then sent across and back every {@value
     * #REORDER_EVERY} ticks. Gated under {@code a}; writes {@code idle_300.json}.
     */
    @StrategosTest(batch = "perf_idle", arena = "arena_flat_48", timeoutTicks = 3_000)
    public static void idle300CostsAThird(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, 150, 6));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, 150, 6));
        Window window = new Window();
        var state = new Object() {
            long from = -1;
            long orderedAt = -1;
            boolean toEast = true;
            int idleSamples;
            int movingSamples;
            final long[] idle = new long[SAMPLES];
            final long[] moving = new long[SAMPLES];
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().unitCount() == 300 && rt.bodies().count() == 300,
                        "the 300 units and bodies are not there yet"))
                .thenWaitUntil(() -> {
                    long tick = core(rt).queries().tick();
                    if (state.from < 0) {
                        state.from = tick;
                        window.start();
                    }
                    if (tick - state.from >= WARM_UP) {
                        state.idle[state.idleSamples++] = core(rt).timers().lastTotalNanos();
                        if (state.idleSamples % 20 == 0) {
                            window.sampleForeignLoad();
                        }
                    }
                    check(state.idleSamples >= SAMPLES, "standing: " + state.idleSamples);
                })
                .thenWaitUntil(() -> {
                    long tick = core(rt).queries().tick();
                    if (state.orderedAt < 0 || tick - state.orderedAt >= REORDER_EVERY) {
                        if (state.orderedAt < 0) {
                            state.from = tick;
                        }
                        reorder(rt, state.toEast);
                        state.toEast = !state.toEast;
                        state.orderedAt = tick;
                    }
                    if (tick - state.from >= WARM_UP) {
                        state.moving[state.movingSamples++] = core(rt).timers().lastTotalNanos();
                        if (state.movingSamples % 20 == 0) {
                            window.sampleForeignLoad();
                        }
                    }
                    check(state.movingSamples >= SAMPLES, "walking: " + state.movingSamples);
                })
                .thenExecute(() -> {
                    long idle = Percentiles.percentile(state.idle, 0.50);
                    long walking = Percentiles.percentile(state.moving, 0.50);
                    double ratio = (double) idle / walking;
                    double foreign = window.meanForeignLoad();
                    boolean over = ratio > 0.30;
                    String verdict = foreign > INVALID_FOREIGN_LOAD
                            ? "invalid"
                            : !gated("a") ? "report-only" : over ? "fail" : "pass";
                    JsonObject json = new JsonObject();
                    json.addProperty("test", "idle_300");
                    json.addProperty("gate", "a");
                    json.addProperty("idleP50", idle);
                    json.addProperty("movingP50", walking);
                    json.addProperty("ratio", ratio);
                    json.addProperty("limit", 0.30);
                    json.addProperty("foreignCpu", foreign);
                    json.addProperty("verdict", verdict);
                    write("idle_300", json);
                    check(!verdict.equals("fail"), "300 standing units cost " + ratio + " of 300 walking, over 0.30");
                })
                .thenSucceed();
    }

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
     * writes. A gate under {@code c}: the owner approved its limits on 2026-10-23, 5 % of budget C for a tick's
     * writes at p99 (1.15 ms) and a tenth of that at the median.
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

    /**
     * PATH-09.3, budget D at 600 units: {@link PathLoad#groups}, four groups of 150 sent across the proving ground at
     * once, with the rules' defaults and the server's path threads, one run to warm up and three measured, their ticks
     * pooled: the main thread's path work at most 2 ms at p99, and at most a tenth of the requests one by one would
     * make (members that trade places in the same tick may ask together again).
     */
    @StrategosTest(batch = "perf_group_paths", arena = "arena_flat_48", timeoutTicks = 200)
    public static void groupPathsBudgetD(GameTestHelper h) {
        Window window = new Window();
        h.startSequence()
                .thenExecute(() -> {
                    MatchRules d = MatchRules.DEFAULTS;
                    MatchRules rules = PathLoad.rules(d.delivery(), d.pathK(), d.admissionW());
                    PathLoad load = new PathLoad();
                    load.groups(rules, StrategosServer.get().jobs());
                    window.start();
                    List<PathLoad.Result> runs = new ArrayList<>();
                    for (int k = 0; k < 3; k++) {
                        runs.add(load.groups(rules, StrategosServer.get().jobs()));
                        window.sampleForeignLoad();
                    }
                    PathLoad.Result r = PathLoad.Result.pooled(runs);
                    JsonObject extra = new JsonObject();
                    extra.addProperty("threads", StrategosServer.get().jobs().threads());
                    extra.addProperty("units", 4 * PathLoad.GROUP_OF);
                    extra.addProperty("join_p99_ns", r.joinP99());
                    extra.addProperty("answers_p95_ticks", r.allP95());
                    extra.addProperty("requests_last_run", r.stats().requests());
                    extra.addProperty("group_requests_last_run", r.stats().groupRequests());
                    extra.addProperty("joins_last_run", r.stats().joins());
                    extra.addProperty("ticks_measured", r.ticks());
                    verdict(
                            h,
                            "group_paths_budget_d",
                            "d",
                            new BudgetCheck("budget D, group paths", Long.MAX_VALUE, scaled(2 * MS)),
                            r.work(),
                            window,
                            extra);
                    check(
                            r.stats().groupRequests() >= 4,
                            "group requests: " + r.stats().groupRequests());
                    check(
                            r.stats().requests() <= 60,
                            "600 units in groups made " + r.stats().requests() + " requests, over a tenth of 600");
                })
                .thenSucceed();
    }

    /**
     * PERF-05.1–2 (M1 spec §8.5): the crowd soak, 600 units on the proving ground under 12,000 ticks of seeded
     * orders (unless {@code strategos.soak.ticks} shortens them), two seated viewers at its centre. It fails on a failed order, more than 1 nudge per 1,000 unit
     * orders or a unit more than 400 ticks without progress, and, for each gate listed, on a broken budget: A p50 at most
     * 5 ms and p99 at most 10 ms; B p95 at most 17 ms, the tick less the same arena's median tick without units; C p95
     * at most 23 ms, the tick with our handlers' time before it; D p99 at most 2 ms; E at most 100 KB/s per viewer; G
     * nav at most 32 MB. Writes {@code crowd_soak_600.json} and the match's journal beside it.
     */
    @StrategosTest(batch = "perf_soak", arena = "arena_flat_48", timeoutTicks = 80_000)
    public static void crowdSoak600(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("crowd_soak_600", 0, 300, SOAK_TICKS, true, 2, SOAK_LANE),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * PERF-05.3: the stress pass, 1,200 units under {@value #STRESS_TICKS} ticks of seeded orders: only budget C, p95 at
     * most 50 ms, gates; the rest is reported. Writes {@code stress_1200.json}.
     */
    @StrategosTest(batch = "perf_stress", arena = "arena_flat_48", timeoutTicks = 80_000)
    public static void stress1200(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("stress_1200", 1, 600, STRESS_TICKS, true, 0, STRESS_LANE),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * CMB-02.6 (M2 spec §6): the melee soak, two factions of 100 spearmen on the proving ground, each on its bank of the
     * river with half at each ford, sent at each other with attack-move; a wave ends when a side is gone or after 600
     * ticks, and both are filled up to 100 and sent again, for 6,000 ticks paced at 20 a second; two seated viewers at
     * the centre. Budgets A, B and C gate as in the crowd soak; the rest is reported. Writes
     * {@code melee_soak_200.json}.
     */
    @StrategosTest(batch = "perf_melee", arena = "arena_flat_48", timeoutTicks = 40_000)
    public static void meleeSoak200(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("melee_soak_200", 0, 100, MELEE_TICKS, true, 2, MELEE_LANE, true),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * PERF-06.1, .2 and .4 (M3 spec §6): the first loop on the proving ground for {@code strategos.firstLoop.ticks}
     * ticks (18,000, 15 game minutes, paced at 20 a second): two factions of 60 workers gathering and 40 spearmen in
     * waves across the fords, 20 buildings and three constructions running ({@link FirstLoopScene}), two seated viewers
     * at the centre. Budgets A–E gate as in the crowd soak, G on nav and on one save of the blob in the window, and A's
     * p95 per unit at most 15 % over M1's; the report adds the placements', the writer's and the fx's times. Writes
     * {@code first_loop_soak.json} and the match's journal.
     */
    @StrategosTest(batch = "perf_first_loop", arena = "arena_flat_48", timeoutTicks = 40_000)
    public static void firstLoopSoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "first_loop_soak", FirstLoopScene.Config.SOAK, FIRST_LOOP_TICKS, FIRST_LOOP_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * PERF-06.3: the first loop at twice its scale, 400 units and six constructions, for {@value
     * #FIRST_LOOP_SHORT_TICKS} ticks: budget C's p95 at most 50 ms gates, the rest is reported. Writes {@code
     * first_loop_stress.json}.
     */
    @StrategosTest(batch = "perf_first_loop_stress", arena = "arena_flat_48", timeoutTicks = 20_000)
    public static void firstLoopStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "first_loop_stress",
                        FirstLoopScene.Config.STRESS,
                        FIRST_LOOP_SHORT_TICKS,
                        FIRST_LOOP_STRESS_LANE,
                        1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * PERF-07.1, .2 and .4 (M4 spec §6): the economy on the proving ground for {@code strategos.economy.ticks} ticks
     * (18,000, 15 game minutes, paced at 20 a second): two factions of 75 workers at trees, quarries, mines, fields and
     * sheep, 75 spearmen in waves across the fords, towns of 28 buildings with five constructions and two repairs
     * running ({@link EconomyScene}), two seated viewers at the centre. Budgets A–E gate as in the crowd soak, G on nav
     * and on one save of the blob in the window, and A's p95 per unit at most 15 % over M3's first loop; the report adds
     * the placements', the writer's, the fx's, the node index's and the dropsite queries' times. Writes
     * {@code economy_soak.json} and the match's journal.
     */
    @StrategosTest(batch = "perf_economy", arena = "arena_flat_48", timeoutTicks = 40_000)
    public static void economySoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop("economy_soak", EconomyScene.Config.SOAK, ECONOMY_TICKS, ECONOMY_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * PERF-07.3: the economy at twice its scale, 600 workers and soldiers, ten constructions and four repairs, for
     * {@value #FIRST_LOOP_SHORT_TICKS} ticks: budget C's p95 at most 50 ms gates, the rest is reported. Writes {@code
     * economy_stress.json}.
     */
    @StrategosTest(batch = "perf_economy_stress", arena = "arena_flat_48", timeoutTicks = 20_000)
    public static void economyStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "economy_stress", EconomyScene.Config.STRESS, FIRST_LOOP_SHORT_TICKS, ECONOMY_STRESS_LANE, 1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * MATCH-05.4: the first loop played at 1.5 times its speed, 30 ticks a second, for {@value #FIRST_LOOP_SHORT_TICKS}
     * ticks: budget C's p95 at most 15.3 ms, so a tick still fits a 20-a-second budget's share, gates under {@code c};
     * the rest is reported. Writes {@code first_loop_fast.json}.
     */
    @StrategosTest(batch = "perf_first_loop_fast", arena = "arena_flat_48", timeoutTicks = 20_000)
    public static void firstLoopFast(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "first_loop_fast",
                        FirstLoopScene.Config.SOAK,
                        FIRST_LOOP_SHORT_TICKS,
                        FIRST_LOOP_FAST_LANE,
                        MatchControl.FAST),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    /**
     * CAM-03.5: what a panning commander costs in the chunk map: a mock commander, a spectator, pans 5 blocks a tick
     * across the arena while 600 units walk, and each tick the call each of its movement packets makes,
     * {@code ServerChunkCache#move}, is timed; at most 0.5 ms at p99, under gate {@code c}.
     */
    @StrategosTest(batch = "perf_commander", arena = "arena_flat_48", timeoutTicks = 1_400)
    public static void commanderPanMoveCost(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, 300, 6));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, 300, 6));
        Vec3 corner = h.absoluteVec(new Vec3(0, 26, 24));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-pancst", corner.x, corner.y, corner.z, 8);
        viewer.capture().keep(true);
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(viewer.player(), rt);
        Window window = new Window();
        var state = new Object() {
            double x = corner.x;
            double direction = 5;
            boolean placed;
            int ticks;
            int samples;
            final long[] nanos = new long[SAMPLES];
        };
        h.onEachTick(() -> {
            viewer.poll();
            for (var p : viewer.capture().of(ClientboundPlayerPositionPacket.class)) {
                viewer.player()
                        .connection
                        .handleAcceptTeleportPacket(
                                new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(
                                        p.getId()));
            }
            viewer.capture().clear();
            if (!state.placed) {
                state.placed = true;
                viewer.player().connection.teleport(state.x, corner.y, corner.z, 0, 55);
                return;
            }
            if (core(rt).queries().unitCount() < 600) {
                return;
            }
            if (state.ticks % REORDER_EVERY == 0) {
                reorder(rt, state.ticks / REORDER_EVERY % 2 == 0);
            }
            state.ticks++;
            state.x += state.direction;
            if (state.x > corner.x + 48 || state.x < corner.x) {
                state.direction = -state.direction;
                state.x += 2 * state.direction;
            }
            viewer.player()
                    .connection
                    .handleMovePlayer(
                            new ServerboundMovePlayerPacket.PosRot(state.x, corner.y, corner.z, 0, 55, false));
            long start = System.nanoTime();
            h.getLevel().getChunkSource().move(viewer.player());
            long took = System.nanoTime() - start;
            if (state.ticks > WARM_UP && state.samples < SAMPLES) {
                if (state.samples == 0) {
                    window.start();
                }
                state.nanos[state.samples++] = took;
                if (state.samples % 20 == 0) {
                    window.sampleForeignLoad();
                }
            }
            if (state.samples >= SAMPLES) {
                viewer.close();
                verdict(
                        h,
                        "commander_pan_move_cost",
                        "c",
                        new BudgetCheck("ChunkMap#move per panning commander", Long.MAX_VALUE, scaled(MS / 2)),
                        state.nanos,
                        window,
                        new JsonObject());
                h.succeed();
            }
        });
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
}
