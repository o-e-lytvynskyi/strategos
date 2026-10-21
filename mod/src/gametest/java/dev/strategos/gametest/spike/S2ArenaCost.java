package dev.strategos.gametest.spike;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.testkit.Percentiles;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S2, the cost of a force-loaded arena (spec 8.5): for 256², 384² and 512² at (8192, floor, 8192), with
 * {@code ticking=false} tickets, the import time, idle MSPT, heap and autosave, then 600 walking production bodies of a
 * saved match; at 576 chunks also the keep-alive check and, for the record, the idle cost with {@code ticking=true}
 * tickets, under the arena's rules and under the defaults.
 */
final class S2ArenaCost implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(S2ArenaCost.class);
    private static final int ORIGIN = 8192;
    private static final int[] SIZES = {256, 384, 512};
    private static final int KEEP_ALIVE_SIZE = 384;
    private static final int SETTLE = 100;
    private static final int WARM_UP = 200;
    private static final int MEASURED = 600;
    private static final int KEEP_ALIVE_TICKS = 12_000;
    private static final int KEEP_ALIVE_MIN = 11_900;
    private static final int UNLOAD = 100;
    private static final int SPAWN_LIMIT = 400;
    private static final Vec2d WEST = new Vec2d(12.5, 12.5);
    private static final Vec2d EAST = new Vec2d(83.5, 83.5);

    /** One measured line of the report; blank fields are written as empty cells. */
    private static final class Row {
        final String phase;
        final int size;
        final int chunks;
        final boolean ticking;
        final String rules;
        final int bodies;
        double[] mspt = new double[0];
        double heapMb = Double.NaN;
        double importMs = Double.NaN;
        double[] autosaveMs = new double[0];
        long probeTicks = -1;

        Row(String phase, int size, int chunks, boolean ticking, String rules, int bodies) {
            this.phase = phase;
            this.size = size;
            this.chunks = chunks;
            this.ticking = ticking;
            this.rules = rules;
            this.bodies = bodies;
        }
    }

    /** A step of the script: begun once, then ticked until it says it is done. */
    private interface Step {
        default void begin() {}

        default void pre() {}

        boolean post();
    }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Path dir = SpikeReport.dirFor("s2");
    private final SpikeReport env;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<Row> rows = new ArrayList<>();
    private @Nullable Step current;
    private @Nullable SpikeArena arena;
    private boolean failed;

    S2ArenaCost(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.env = new SpikeReport(dir);
        for (int size : SIZES) {
            script(size);
        }
        LOG.info("Spike s2: {} steps, output {}", steps.size(), dir);
    }

    private SpikeArena arena() {
        SpikeArena a = arena;
        if (a == null) {
            throw new IllegalStateException("no arena is open");
        }
        return a;
    }

    private void script(int size) {
        int chunks = (size / 16) * (size / 16);
        Row idle = new Row("idle", size, chunks, false, "arena", 0);
        steps.add(once(() -> {
            long start = System.nanoTime();
            arena = SpikeArena.open(level, ORIGIN, size, false);
            idle.importMs = (System.nanoTime() - start) / 1e6;
            LOG.info("Spike s2: {}² forced, {} chunks in {} ms", size, arena().chunkCount(), num(idle.importMs));
        }));
        steps.add(idleTicks(SETTLE));
        steps.add(measure(idle, null));
        steps.add(once(() -> {
            idle.heapMb = heapAfterGc();
            idle.autosaveMs = autosaves();
            rows.add(idle);
        }));
        Row walking = new Row("bodies_600", size, chunks, false, "arena", 600);
        Walkers walkers = new Walkers();
        steps.add(once(() -> walkers.spawn(arena())));
        steps.add(new Step() {
            private int ticks;

            @Override
            public boolean post() {
                if (walkers.ready()) {
                    return true;
                }
                if (++ticks > SPAWN_LIMIT) {
                    LOG.error("Spike s2: the 600 bodies never all appeared at {}²", size);
                    failed = true;
                    return true;
                }
                return false;
            }
        });
        steps.add(measure(walking, walkers));
        steps.add(once(() -> {
            walking.heapMb = heapAfterGc();
            walking.autosaveMs = autosaves();
            rows.add(walking);
            walkers.dispose();
        }));
        if (size == KEEP_ALIVE_SIZE) {
            Row keepAlive = new Row("keep_alive", size, chunks, false, "arena", 1);
            steps.add(probe(keepAlive, KEEP_ALIVE_TICKS));
            Row ticking = new Row("idle_ticking", size, chunks, true, "arena", 0);
            steps.add(once(() -> arena().retick(true)));
            steps.add(idleTicks(SETTLE));
            steps.add(measure(ticking, null));
            steps.add(once(() -> rows.add(ticking)));
            Row defaults = new Row("idle_ticking_defaults", size, chunks, true, "defaults", 0);
            steps.add(once(() -> {
                GameRules rules = server.getGameRules();
                rules.getRule(GameRules.RULE_RANDOMTICKING).set(3, server);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
            }));
            steps.add(idleTicks(SETTLE));
            steps.add(measure(defaults, null));
            steps.add(once(() -> {
                rows.add(defaults);
                SpikeDriver.arenaRules(server);
            }));
        }
        steps.add(once(() -> {
            arena().close();
            arena = null;
        }));
        steps.add(idleTicks(UNLOAD));
    }

    private static Step once(Runnable action) {
        return new Step() {
            @Override
            public void begin() {
                action.run();
            }

            @Override
            public boolean post() {
                return true;
            }
        };
    }

    private static Step idleTicks(int n) {
        return new Step() {
            private int ticks;

            @Override
            public boolean post() {
                return ++ticks >= n;
            }
        };
    }

    /** 200 warm-up and 600 measured ticks of MSPT, with the walkers moving when there are any. */
    private Step measure(Row row, @Nullable Walkers walkers) {
        return new Step() {
            private int ticks;
            private final long[] mspt = new long[MEASURED];

            @Override
            public boolean post() {
                if (walkers != null) {
                    walkers.postTick();
                }
                ticks++;
                if (ticks > WARM_UP) {
                    mspt[ticks - WARM_UP - 1] = server.getTickTimesNanos()[server.getTickCount() % 100];
                }
                if (ticks < WARM_UP + MEASURED) {
                    return false;
                }
                row.mspt = new double[] {
                    Percentiles.percentile(mspt, 0.50) / 1e6,
                    Percentiles.percentile(mspt, 0.95) / 1e6,
                    Percentiles.percentile(mspt, 0.99) / 1e6,
                    Arrays.stream(mspt).max().orElse(0) / 1e6
                };
                LOG.info(
                        "Spike s2 {} at {}²: MSPT p50 {} ms, p99 {} ms",
                        row.phase,
                        row.size,
                        num(row.mspt[0]),
                        num(row.mspt[2]));
                return true;
            }
        };
    }

    /** PERF-03.2: a probe body's own ticks over {@code n} server ticks with nobody online, at least 11,900 of 12,000. */
    private Step probe(Row row, int n) {
        return new Step() {
            private @Nullable SpikePhysicsBody probe;
            private int start;
            private int ticks;

            @Override
            public void begin() {
                SpikePhysicsBody body =
                        SpikeRegistry.require(SpikeRegistry.physics).create(level);
                if (body == null) {
                    throw new IllegalStateException("cannot create the keep-alive probe");
                }
                Vec3 at = arena().world(48.5, 48.5);
                body.moveTo(at.x, at.y, at.z, 0, 0);
                level.addFreshEntity(body);
                probe = body;
                start = body.tickCount;
            }

            @Override
            public boolean post() {
                if (++ticks < n) {
                    return false;
                }
                SpikePhysicsBody body = probe;
                if (body == null) {
                    throw new IllegalStateException("no keep-alive probe");
                }
                row.probeTicks = (long) body.tickCount - start;
                if (row.probeTicks < KEEP_ALIVE_MIN) {
                    LOG.error("Spike s2: the probe ticked {} of {} times", row.probeTicks, KEEP_ALIVE_TICKS);
                    failed = true;
                }
                body.discard();
                rows.add(row);
                return true;
            }
        };
    }

    /** 600 production bodies of one saved match, 300 a faction, walking corner to corner, re-ordered every 120 ticks. */
    private final class Walkers {
        private @Nullable MatchRuntime match;
        private long orderedAt = -1;
        private boolean toEast = true;

        void spawn(SpikeArena a) {
            MatchOptions options =
                    new MatchOptions(42L, 2, true, MatchSource.GAMETEST, null, MetricsSink.none(), a.floorY);
            MatchRuntime m = StrategosServer.get().matches().create(level, a.space, options);
            m.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, 300, 6));
            m.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, 300, 6));
            match = m;
        }

        private MatchCore core() {
            MatchRuntime m = match;
            MatchCore core = m == null ? null : m.core();
            if (core == null) {
                throw new IllegalStateException("the walkers' match is gone");
            }
            return core;
        }

        boolean ready() {
            MatchRuntime m = match;
            return m != null
                    && core().queries().unitCount() == 600
                    && m.bodies().count() == 600;
        }

        void postTick() {
            MatchRuntime m = match;
            if (m == null) {
                return;
            }
            long tick = core().queries().tick();
            if (orderedAt >= 0 && tick - orderedAt < 120) {
                return;
            }
            for (int f = 0; f < 2; f++) {
                Vec2d target = (f == 0) == toEast ? EAST : WEST;
                int[] units = core().queries().unitHandlesOf(new FactionSlot(f));
                m.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), target));
            }
            toEast = !toEast;
            orderedAt = tick;
        }

        void dispose() {
            MatchRuntime m = match;
            if (m != null) {
                StrategosServer.get().matches().dispose(m.id());
                match = null;
            }
        }
    }

    private static double heapAfterGc() {
        System.gc();
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
    }

    /** Three timed {@code saveEverything(true, false, true)} calls with saving on, in ms (budget G). */
    private double[] autosaves() {
        double[] ms = new double[3];
        for (ServerLevel l : server.getAllLevels()) {
            l.noSave = false;
        }
        for (int i = 0; i < ms.length; i++) {
            long start = System.nanoTime();
            server.saveEverything(true, false, true);
            ms[i] = (System.nanoTime() - start) / 1e6;
        }
        for (ServerLevel l : server.getAllLevels()) {
            l.noSave = true;
        }
        return ms;
    }

    @Override
    public void preTick() {
        Step step = current;
        if (step != null) {
            step.pre();
        }
    }

    @Override
    public boolean postTick() {
        if (failed) {
            return true;
        }
        Step step = current;
        if (step == null) {
            step = steps.poll();
            if (step == null) {
                return true;
            }
            current = step;
            step.begin();
        }
        if (step.post()) {
            current = null;
        }
        return false;
    }

    @Override
    public void finish(boolean complete) {
        SpikeArena a = arena;
        if (a != null) {
            a.close();
        }
        StringBuilder csv =
                new StringBuilder("phase,size,chunks,ticking,rules,bodies,mspt_p50_ms,mspt_p95_ms,mspt_p99_ms,"
                        + "mspt_max_ms,heap_mb,import_ms,autosave_1_ms,autosave_2_ms,autosave_3_ms,autosave_median_ms,"
                        + "autosave_max_ms,probe_ticks\n");
        StringBuilder md =
                new StringBuilder("# Spike S2: force-loaded arena cost\n\n| Phase | Size | Chunks | Ticking |"
                        + " Rules | Bodies | MSPT p50 | p95 | p99 | max | heap MB | import ms | autosave ms (median, max) |"
                        + " probe ticks |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            double[] m = r.mspt.length == 4 ? r.mspt : new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
            double[] s = r.autosaveMs.length == 3 ? r.autosaveMs : new double[] {Double.NaN, Double.NaN, Double.NaN};
            double median = median(s);
            double max = Arrays.stream(s).max().orElse(Double.NaN);
            String probe = r.probeTicks < 0 ? "" : String.valueOf(r.probeTicks);
            csv.append(String.join(
                            ",",
                            r.phase,
                            String.valueOf(r.size),
                            String.valueOf(r.chunks),
                            String.valueOf(r.ticking),
                            r.rules,
                            String.valueOf(r.bodies),
                            num(m[0]),
                            num(m[1]),
                            num(m[2]),
                            num(m[3]),
                            num(r.heapMb),
                            num(r.importMs),
                            num(s[0]),
                            num(s[1]),
                            num(s[2]),
                            num(median),
                            num(max),
                            probe))
                    .append('\n');
            md.append(String.format(
                    Locale.ROOT,
                    "| %s | %d² | %d | %s | %s | %d | %s | %s | %s | %s | %s | %s | %s | %s |%n",
                    r.phase,
                    r.size,
                    r.chunks,
                    r.ticking,
                    r.rules,
                    r.bodies,
                    num(m[0]),
                    num(m[1]),
                    num(m[2]),
                    num(m[3]),
                    num(r.heapMb),
                    num(r.importMs),
                    Double.isNaN(median) ? "" : num(median) + ", " + num(max),
                    probe));
        }
        md.append('\n');
        for (String verdict : verdicts()) {
            md.append("- ").append(verdict).append('\n');
        }
        write("results.csv", csv.toString());
        write("results.md", md.toString());
        env.writeEnv();
        if (complete && !failed) {
            env.done();
        }
    }

    /** The exit criteria of spec 8.5: idle MSPT at 576 chunks <= 4 ms, autosave <= 100 ms. */
    private List<String> verdicts() {
        List<String> verdicts = new ArrayList<>();
        for (Row r : rows) {
            if (r.size == KEEP_ALIVE_SIZE && r.phase.equals("idle") && r.mspt.length == 4) {
                verdicts.add(String.format(
                        Locale.ROOT,
                        "idle MSPT at 576 chunks: p50 %s ms, p99 %s ms (exit: <= 4): %s",
                        num(r.mspt[0]),
                        num(r.mspt[2]),
                        r.mspt[0] <= 4 ? "met" : "NOT met"));
            }
            if (r.size == KEEP_ALIVE_SIZE && r.autosaveMs.length == 3) {
                double median = median(r.autosaveMs);
                verdicts.add(String.format(
                        Locale.ROOT,
                        "autosave at 576 chunks, %s: median %s ms (exit: <= 100): %s",
                        r.phase,
                        num(median),
                        median <= 100 ? "met" : "NOT met"));
            }
            if (r.probeTicks >= 0) {
                verdicts.add(String.format(
                        Locale.ROOT,
                        "keep-alive: the probe ticked %d of %d times with nobody online (exit: >= %d): %s",
                        r.probeTicks,
                        KEEP_ALIVE_TICKS,
                        KEEP_ALIVE_MIN,
                        r.probeTicks >= KEEP_ALIVE_MIN ? "met" : "NOT met"));
            }
        }
        return verdicts;
    }

    private static double median(double[] values) {
        if (values.length == 0 || Double.isNaN(values[0])) {
            return Double.NaN;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private void write(String name, String text) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the spike's " + name, e);
        }
    }

    private static String num(double v) {
        return SpikeReport.num(v);
    }
}
