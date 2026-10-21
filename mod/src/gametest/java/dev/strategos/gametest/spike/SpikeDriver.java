package dev.strategos.gametest.spike;

import dev.strategos.gametest.spike.S1BodyCost.Config;
import dev.strategos.gametest.spike.S1BodyCost.Population;
import dev.strategos.mod.Strategos;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a spike on the self-terminating dedicated server (spec 8.3) when {@code strategos.spike} is {@code s1}: the
 * world rules, back-to-back ticks, then every configuration of the plan in turn (spawn until every body exists, 200
 * warm-up ticks, 600 measured ticks, tear-down, 40 idle ticks), the report, {@code DONE} and {@code halt}. A
 * 40-minute deadline writes what it has and halts without {@code DONE}.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SpikeDriver {
    private static final Logger LOG = LoggerFactory.getLogger(SpikeDriver.class);
    private static final int SPAWN_LIMIT = 400;
    private static final int WARM_UP = 200;
    private static final int MEASURED = 600;
    private static final int IDLE = 40;
    private static final long DEADLINE_NANOS = 40L * 60 * 1_000_000_000;
    private static final int STORM_PER_TICK = 64;

    private enum Phase {
        SPAWNING,
        WARMING,
        MEASURING,
        IDLING
    }

    /** The running spike; gametest code, so static state is fine. Server thread only. */
    private static @Nullable Run run;

    private SpikeDriver() {}

    private static final class Run {
        final MinecraftServer server;
        final SpikeArena arena;
        final SpikeReport report;
        final List<Config> plan;
        final long startNanos = System.nanoTime();
        int index = -1;
        Phase phase = Phase.IDLING;
        int ticksInPhase = IDLE;

        @Nullable Population population;

        @Nullable TickSampler sampler;

        long tick;

        Run(MinecraftServer server, SpikeArena arena, SpikeReport report, List<Config> plan) {
            this.server = server;
            this.arena = arena;
            this.report = report;
            this.plan = plan;
        }

        Config config() {
            return plan.get(index);
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        String spike = System.getProperty("strategos.spike", "");
        if (spike.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!spike.equals("s1")) {
            LOG.error("Spike {} arrives with a later M0 task", spike);
            server.halt(false);
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            level.noSave = true;
        }
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        rules.getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(0, server);
        server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        SpikeArena arena = SpikeArena.open(server.overworld());
        Path dir = SpikeReport.dirFor(spike);
        LOG.info(
                "Spike {}: {} configurations, arena floor y {}, output {}",
                spike,
                S1BodyCost.plan().size(),
                arena.floorY,
                dir);
        run = new Run(server, arena, new SpikeReport(dir), S1BodyCost.plan());
    }

    /** Measured work: inside the tick whose time the sampler reads. */
    @SubscribeEvent
    static void onServerTickPre(ServerTickEvent.Pre event) {
        Run r = run;
        if (r == null || r.population == null || (r.phase != Phase.WARMING && r.phase != Phase.MEASURING)) {
            return;
        }
        r.population.preTick(r.tick);
        if (r.config().storm()) {
            storm(r);
        }
    }

    /** The build storm: 64 placements a tick toggling stone and air in a 16 x 16 patch. */
    private static void storm(Run r) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int j = 0; j < STORM_PER_TICK; j++) {
            long n = r.tick * STORM_PER_TICK + j;
            int cell = (int) (n % 256);
            BlockPos pos = r.arena.block(SpikeArena.STORM + cell % 16, SpikeArena.STORM + cell / 16);
            r.arena.level.setBlock(pos, (n / 256) % 2 == 0 ? stone : air, 3);
        }
    }

    /** The state machine and the bookkeeping, after the tick's time is taken. */
    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        Run r = run;
        if (r == null) {
            return;
        }
        r.tick++;
        if (System.nanoTime() - r.startNanos > DEADLINE_NANOS) {
            LOG.error(
                    "Spike deadline: {} of {} configurations done",
                    r.report.rows().size(),
                    r.plan.size());
            finish(r, false);
            return;
        }
        r.ticksInPhase++;
        Population population = r.population;
        switch (r.phase) {
            case IDLING -> {
                if (r.ticksInPhase >= IDLE) {
                    next(r);
                }
            }
            case SPAWNING -> {
                if (population != null && population.ready()) {
                    enter(r, Phase.WARMING);
                } else if (r.ticksInPhase > SPAWN_LIMIT) {
                    LOG.error(
                            "Spike configuration {} never got all its bodies",
                            r.config().name());
                    finish(r, false);
                }
            }
            case WARMING -> {
                if (population != null) {
                    population.postTick(r.tick);
                }
                if (r.ticksInPhase >= WARM_UP) {
                    r.sampler = new TickSampler(MEASURED);
                    enter(r, Phase.MEASURING);
                }
            }
            case MEASURING -> {
                TickSampler sampler = r.sampler;
                if (population == null || sampler == null) {
                    throw new IllegalStateException("measuring without a population");
                }
                sampler.record(r.server, population.stepNanos(), population.adapterNanos());
                population.postTick(r.tick);
                if (sampler.full()) {
                    if (r.config().recorded()) {
                        record(r, sampler);
                    }
                    population.tearDown();
                    r.population = null;
                    enter(r, Phase.IDLING);
                }
            }
        }
    }

    private static void next(Run r) {
        r.index++;
        if (r.index >= r.plan.size()) {
            finish(r, true);
            return;
        }
        Config config = r.config();
        LOG.info("Spike configuration {} of {}: {}", r.index + 1, r.plan.size(), config.name());
        Population population = S1BodyCost.population(config, r.arena);
        population.spawn();
        r.population = population;
        enter(r, Phase.SPAWNING);
    }

    private static void enter(Run r, Phase phase) {
        r.phase = phase;
        r.ticksInPhase = 0;
    }

    private static void record(Run r, TickSampler sampler) {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        double heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
        int entities = 0;
        for (Entity ignored : r.arena.level.getAllEntities()) {
            entities++;
        }
        Config c = r.config();
        r.report.add(new SpikeReport.Row(
                c.name(),
                c.body().name().toLowerCase(Locale.ROOT),
                c.count(),
                c.state(),
                sampler.count(),
                sampler.tickMs(0.50),
                sampler.tickMs(0.95),
                sampler.tickMs(0.99),
                sampler.tickMaxMs(),
                sampler.stepP50Ms(),
                sampler.adapterP50Ms(),
                heapMb,
                entities));
        r.report.writeCsv(); // the partial file stays if a later configuration never ends
        LOG.info(
                "Spike {}: tick p50 {} ms, p99 {} ms",
                c.name(),
                SpikeReport.num(sampler.tickMs(0.50)),
                SpikeReport.num(sampler.tickMs(0.99)));
    }

    private static void finish(Run r, boolean done) {
        run = null;
        Population population = r.population;
        if (population != null) {
            population.tearDown();
        }
        r.arena.close();
        r.report.writeCsv();
        r.report.writeMarkdown("Spike S1: server cost per body", verdicts(r.report));
        r.report.writeEnv();
        if (done) {
            r.report.done();
        }
        r.server.halt(false); // never true: this handler runs on the server thread
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
