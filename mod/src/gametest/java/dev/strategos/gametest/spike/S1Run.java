package dev.strategos.gametest.spike;

import dev.strategos.gametest.spike.S1BodyCost.Config;
import dev.strategos.gametest.spike.S1BodyCost.Population;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * S1's run (spec 8.4): every configuration of the plan in turn, spawned until every body exists, 200 warm-up ticks,
 * 600 measured ticks, torn down, then 40 idle ticks.
 */
final class S1Run implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(S1Run.class);
    private static final int SPAWN_LIMIT = 400;
    private static final int WARM_UP = 200;
    private static final int MEASURED = 600;
    private static final int IDLE = 40;
    private static final int STORM_PER_TICK = 64;

    private enum Phase {
        SPAWNING,
        WARMING,
        MEASURING,
        IDLING
    }

    private final MinecraftServer server;
    private final SpikeArena arena;
    private final SpikeReport report;
    private final List<Config> plan = S1BodyCost.plan();
    private int index = -1;
    private Phase phase = Phase.IDLING;
    private int ticksInPhase = IDLE;
    private @Nullable Population population;
    private @Nullable TickSampler sampler;
    private long tick;
    private boolean failed;

    S1Run(MinecraftServer server) {
        this.server = server;
        this.arena = SpikeArena.s1(server.overworld());
        this.report = new SpikeReport(SpikeReport.dirFor("s1"));
        LOG.info("Spike s1: {} configurations with the warm-ups, arena floor y {}", plan.size(), arena.floorY);
    }

    private Config config() {
        return plan.get(index);
    }

    @Override
    public void preTick() {
        Population p = population;
        if (p == null || (phase != Phase.WARMING && phase != Phase.MEASURING)) {
            return;
        }
        p.preTick(tick);
        if (config().storm()) {
            storm();
        }
    }

    /** The build storm: 64 placements a tick toggling stone and air in a 16 x 16 patch. */
    private void storm() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int j = 0; j < STORM_PER_TICK; j++) {
            long n = tick * STORM_PER_TICK + j;
            int cell = (int) (n % 256);
            BlockPos pos = arena.block(SpikeArena.STORM + cell % 16, SpikeArena.STORM + cell / 16);
            arena.level.setBlock(pos, (n / 256) % 2 == 0 ? stone : air, 3);
        }
    }

    @Override
    public boolean postTick() {
        tick++;
        ticksInPhase++;
        Population p = population;
        switch (phase) {
            case IDLING -> {
                if (ticksInPhase >= IDLE) {
                    return next();
                }
            }
            case SPAWNING -> {
                if (p != null && p.ready()) {
                    enter(Phase.WARMING);
                } else if (ticksInPhase > SPAWN_LIMIT) {
                    LOG.error("Spike configuration {} never got all its bodies", config().name());
                    failed = true;
                    return true;
                }
            }
            case WARMING -> {
                if (p != null) {
                    p.postTick(tick);
                }
                if (ticksInPhase >= WARM_UP) {
                    sampler = new TickSampler(MEASURED);
                    enter(Phase.MEASURING);
                }
            }
            case MEASURING -> {
                TickSampler s = sampler;
                if (p == null || s == null) {
                    throw new IllegalStateException("measuring without a population");
                }
                s.record(server, p.stepNanos(), p.adapterNanos());
                p.postTick(tick);
                if (s.full()) {
                    if (config().recorded()) {
                        record(s);
                    }
                    p.tearDown();
                    population = null;
                    enter(Phase.IDLING);
                }
            }
        }
        return false;
    }

    /** The next configuration; true when there is none. */
    private boolean next() {
        index++;
        if (index >= plan.size()) {
            return true;
        }
        LOG.info("Spike configuration {} of {}: {}", index + 1, plan.size(), config().name());
        Population p = S1BodyCost.population(config(), arena);
        p.spawn();
        population = p;
        enter(Phase.SPAWNING);
        return false;
    }

    private void enter(Phase next) {
        phase = next;
        ticksInPhase = 0;
    }

    private void record(TickSampler s) {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        double heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
        int entities = 0;
        for (Entity ignored : arena.level.getAllEntities()) {
            entities++;
        }
        Config c = config();
        report.add(new SpikeReport.Row(
                c.name(),
                c.body().name().toLowerCase(Locale.ROOT),
                c.count(),
                c.state(),
                s.count(),
                s.tickMs(0.50),
                s.tickMs(0.95),
                s.tickMs(0.99),
                s.tickMaxMs(),
                s.stepP50Ms(),
                s.adapterP50Ms(),
                heapMb,
                entities));
        report.writeCsv(); // the partial file stays if a later configuration never ends
        LOG.info(
                "Spike {}: tick p50 {} ms, p99 {} ms",
                c.name(),
                SpikeReport.num(s.tickMs(0.50)),
                SpikeReport.num(s.tickMs(0.99)));
    }

    @Override
    public void finish(boolean complete) {
        Population p = population;
        if (p != null) {
            p.tearDown();
        }
        arena.close();
        report.writeCsv();
        report.writeMarkdown("Spike S1: server cost per body", verdicts(report));
        report.writeEnv();
        if (complete && !failed) {
            report.done();
        }
    }

    /** The exit criteria of spec 8.4, as far as the rows answer them. */
    static List<String> verdicts(SpikeReport report) {
        List<String> verdicts = new ArrayList<>();
        SpikeReport.Row k500 = row(report, "k_500_moving");
        if (k500 != null) {
            double beyond = report.beyondCoreUs(k500);
            verdicts.add(String.format(
                    Locale.ROOT,
                    "kinematic at 500 moving: %s µs per body beyond the core (exit: <= 4): %s",
                    SpikeReport.num(beyond),
                    beyond <= 4 ? "met" : "NOT met"));
        }
        double best = Double.NaN;
        String bestName = "";
        for (String name : List.of("k_500_moving", "p_500_moving", "l_500_moving")) {
            SpikeReport.Row row = row(report, name);
            if (row != null && (Double.isNaN(best) || report.perBodyUs(row) < best)) {
                best = report.perBodyUs(row);
                bestName = name;
            }
        }
        if (!Double.isNaN(best)) {
            verdicts.add(String.format(
                    Locale.ROOT,
                    "best body at 500 moving: %s, %s µs per body (exit: <= 15): D-01's default %s",
                    bestName,
                    SpikeReport.num(best),
                    best <= 15 ? "stays at 150" : "drops to 100"));
        }
        SpikeReport.Row first = row(report, "baseline_first");
        SpikeReport.Row last = row(report, "baseline_last");
        if (first != null && last != null) {
            verdicts.add(String.format(
                    Locale.ROOT,
                    "baseline drift: p50 %s ms first, %s ms last",
                    SpikeReport.num(first.p50()),
                    SpikeReport.num(last.p50())));
        }
        return verdicts;
    }

    private static SpikeReport.@Nullable Row row(SpikeReport report, String name) {
        return report.rows().stream()
                .filter(r -> r.config().equals(name))
                .findFirst()
                .orElse(null);
    }
}
