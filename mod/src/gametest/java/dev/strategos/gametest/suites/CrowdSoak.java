package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SoakOrders;
import dev.strategos.gametest.TickPacer;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * PERF-05's crowd soak (M1 spec §8.5), driven by a GameTest's every tick on the imported proving ground: two factions of
 * {@code perFaction} units at the map's start slots take {@link SoakOrders}' orders for {@code ticks} ticks. It counts
 * the orders refused or given up by the stuck ladder, the nudges, and each unit's longest run of ticks without progress
 * for the stuck histogram, and it exports the match's journal. With budgets on, the arena first runs {@value #SETTLE}
 * ticks to settle and {@value #BASELINE} ticks without units, the "without" side of budget B, and {@code viewers} mock
 * players seated in the match stand at its centre; after {@value #WARM_UP} ticks of warm-up each soak tick samples A
 * (the core's step and appliers), the server's tick, our handlers' time before it (C), the main thread's path work (D)
 * and the viewers' bytes (E). The test that runs it judges the result.
 */
final class CrowdSoak {
    static final int WARM_UP = 200;
    static final int SETTLE = 100;
    static final int BASELINE = 600;
    static final int MOST_STUCK = 400;
    private static final int PER_SPAWN = 300;
    private static final double SPREAD = 14;
    /** Bodies wait for their chunks' entities to load, on the IO worker, so their deadline is wall time. */
    private static final long SPAWN_NANOS = 60_000_000_000L;

    private static final int[] RUNG_TICKS = {0, 40, 100, 200, 400, 600};

    /** One soak: its seed set line, units per faction, soak ticks, whether it measures budgets, how many viewers. */
    record Config(String name, int seedLine, int perFaction, int ticks, boolean budgets, int viewers, int lane) {}

    /**
     * What a soak saw. Samples are per soak tick after the warm-up, in nanoseconds: {@code core} the core's step with our
     * appliers, {@code tick} the server's tick, {@code between} our handlers' time before it, {@code paths} the main
     * thread's path work; {@code baseline} the arena's ticks without units. {@code viewerBytes} each viewer's bytes over
     * the sampled window. {@code stuck} counts units by their longest run without progress, by the stuck ladder's rungs
     * (under 40 ticks, 40, 100, 200, 400 and 600 or more).
     */
    static final class Result {
        private final Config config;
        private final long seed;
        private final long[] core;
        private final long[] tick;
        private final long[] between;
        private final long[] paths;
        private final long[] baseline;
        private final long[] viewerBytes;
        private final double viewerUnits;
        private final int viewerUnitsMost;
        private final long groupOrders;
        private final long unitOrders;
        private final long refused;
        private final long[] rungs;
        private final int[] stuck;
        private final int mostStuck;
        private final String worst;
        private final long navBytes;
        private final int units;
        private final @Nullable Path journal;

        Result(
                Config config,
                long seed,
                long[] core,
                long[] tick,
                long[] between,
                long[] paths,
                long[] baseline,
                long[] viewerBytes,
                double viewerUnits,
                int viewerUnitsMost,
                long groupOrders,
                long unitOrders,
                long refused,
                long[] rungs,
                int[] stuck,
                int mostStuck,
                String worst,
                long navBytes,
                int units,
                @Nullable Path journal) {
            this.config = config;
            this.seed = seed;
            this.core = core;
            this.tick = tick;
            this.between = between;
            this.paths = paths;
            this.baseline = baseline;
            this.viewerBytes = viewerBytes;
            this.viewerUnits = viewerUnits;
            this.viewerUnitsMost = viewerUnitsMost;
            this.groupOrders = groupOrders;
            this.unitOrders = unitOrders;
            this.refused = refused;
            this.rungs = rungs;
            this.stuck = stuck;
            this.mostStuck = mostStuck;
            this.worst = worst;
            this.navBytes = navBytes;
            this.units = units;
            this.journal = journal;
        }

        Config config() {
            return config;
        }

        long seed() {
            return seed;
        }

        long[] core() {
            return core;
        }

        long[] tick() {
            return tick;
        }

        long[] between() {
            return between;
        }

        long[] paths() {
            return paths;
        }

        long[] baseline() {
            return baseline;
        }

        long[] viewerBytes() {
            return viewerBytes;
        }

        /** The bodies a viewer was paired with, on average over the window's seconds. */
        double viewerUnits() {
            return viewerUnits;
        }

        /** The most bodies a viewer was paired with at once. */
        int viewerUnitsMost() {
            return viewerUnitsMost;
        }

        long groupOrders() {
            return groupOrders;
        }

        long unitOrders() {
            return unitOrders;
        }

        long[] rungs() {
            return rungs;
        }

        int[] stuck() {
            return stuck;
        }

        int mostStuck() {
            return mostStuck;
        }

        String worst() {
            return worst;
        }

        long navBytes() {
            return navBytes;
        }

        int units() {
            return units;
        }

        @Nullable Path journal() {
            return journal;
        }

        long failedOrders() {
            return refused + rungs[Stuck.GIVE_UP];
        }

        long nudges() {
            return rungs[4];
        }

        /** The soak's own failures (PERF-05.2), budgets aside. */
        List<String> failures() {
            List<String> failures = new ArrayList<>();
            if (failedOrders() > 0) {
                failures.add(failedOrders() + " failed orders (" + refused + " refused, " + rungs[Stuck.GIVE_UP]
                        + " given up)");
            }
            if (nudges() * 1_000 > unitOrders) {
                failures.add(nudges() + " nudges for " + unitOrders + " unit orders, over 1 per 1,000");
            }
            if (mostStuck > MOST_STUCK) {
                failures.add("a unit " + mostStuck + " ticks without progress, over " + MOST_STUCK + ": " + worst);
            }
            return failures;
        }
    }

    private enum Phase {
        PREPARING,
        SETTLE,
        BASELINE,
        SPAWNING,
        SOAK,
        DONE
    }

    private final GameTestHelper h;
    private final Config config;
    private final long seed;
    private final MatchRuntime rt;
    private final MapManifest manifest;
    private final SoakOrders orders;
    private final Consumer<Result> done;
    private final Runnable windowStarts;
    private final Runnable everySecond;
    private final List<MockViewer> viewers = new ArrayList<>();
    private final int samples;
    private final long[] core;
    private final long[] tick;
    private final long[] between;
    private final long[] paths;
    private final long[] baseline = new long[BASELINE];
    private int[] longest = new int[0];
    private Phase phase = Phase.PREPARING;
    private int phaseTicks;
    private int sampled;
    private long refused;
    private int mostStuck;
    private String worst = "";
    private @Nullable String refusedSpawn;
    private long spawnedAt;
    private long seenSum;
    private int seenSamples;
    private int seenMost;

    private CrowdSoak(
            GameTestHelper h, Config config, Consumer<Result> done, Runnable windowStarts, Runnable everySecond) {
        this.h = h;
        this.config = config;
        this.seed = Seeds.set("soak").skip(config.seedLine()).findFirst().orElseThrow();
        this.rt = MapTests.provingGround(h, config.lane(), 0);
        this.manifest = MapTests.manifest(h);
        this.orders = new SoakOrders(seed, ProvingGround.generate());
        this.done = done;
        this.windowStarts = windowStarts;
        this.everySecond = everySecond;
        this.samples = config.ticks() - WARM_UP;
        this.core = new long[samples];
        this.tick = new long[samples];
        this.between = new long[samples];
        this.paths = new long[samples];
    }

    /**
     * Runs {@code config} as the GameTest {@code h}'s every tick; {@code done} gets the result, and {@code windowStarts}
     * and {@code everySecond} mark the sampled window for the caller's foreign-load samples.
     */
    static void start(
            GameTestHelper h, Config config, Consumer<Result> done, Runnable windowStarts, Runnable everySecond) {
        CrowdSoak soak = new CrowdSoak(h, config, done, windowStarts, everySecond);
        h.onEachTick(soak::tick);
    }

    private void tick() {
        if (phaseTicks % 20 == 0) {
            viewers.forEach(MockViewer::moved); // a client's position packet, at least once a second
        }
        viewers.forEach(MockViewer::poll);
        if (phase == Phase.PREPARING) {
            if (rt.lifecycle() == MatchLifecycle.PREPARING) {
                return;
            }
            check(
                    rt.lifecycle() == MatchLifecycle.RUNNING,
                    "the match is " + rt.lifecycle() + ": " + rt.faultMessage());
            if (config.budgets()) {
                seatViewers();
                TickPacer.pace(true);
                next(Phase.SETTLE);
            } else {
                spawn();
            }
            return;
        }
        phaseTicks++;
        switch (phase) {
            case SETTLE -> {
                if (phaseTicks >= SETTLE) {
                    next(Phase.BASELINE);
                }
            }
            case BASELINE -> {
                baseline[phaseTicks - 1] = lastServerTick();
                if (phaseTicks >= BASELINE) {
                    spawn();
                }
            }
            case SPAWNING -> {
                int units = 2 * config.perFaction();
                check(refusedSpawn == null, "a spawn was refused: " + refusedSpawn);
                check(
                        System.nanoTime() - spawnedAt < SPAWN_NANOS,
                        core(rt).queries().unitCount() + " units and "
                                + rt.bodies().count() + " bodies of " + units + " after a minute; without a body: "
                                + unbodied());
                if (core(rt).queries().unitCount() == units && rt.bodies().count() == units) {
                    longest = new int[maxIndex() + 1];
                    next(Phase.SOAK);
                }
            }
            case SOAK -> soak();
            case DONE -> {}
            default -> throw new IllegalStateException("phase " + phase);
        }
    }

    private void soak() {
        MatchQueries q = core(rt).queries();
        int t = phaseTicks - 1;
        if (t >= config.ticks()) {
            next(Phase.DONE);
            TickPacer.pace(false);
            finish(q);
            return;
        }
        if (t == WARM_UP) {
            viewers.forEach(v -> v.bytes().clear());
            windowStarts.run();
        }
        if (t >= WARM_UP) {
            int i = t - WARM_UP;
            core[i] = core(rt).timers().lastTotalNanos();
            tick[i] = lastServerTick();
            between[i] = StrategosServer.get().betweenTicks().last();
            paths[i] = q.pathStats().lastWork();
            sampled++;
            if (sampled % 20 == 0) {
                everySecond.run();
                for (MockViewer viewer : viewers) {
                    int seen = StrategosServer.get()
                            .interest()
                            .handles(viewer.player().getUUID(), rt.id().value())
                            .size();
                    seenSum += seen;
                    seenSamples++;
                    seenMost = Math.max(seenMost, seen);
                }
            }
        }
        for (int handle : q.unitHandles()) {
            MotionStore.Extra motion = q.motion(handle);
            int index = Handle.index(handle);
            if (motion != null && index < longest.length && motion.stuck() > longest[index]) {
                longest[index] = motion.stuck();
                if (motion.stuck() > mostStuck) {
                    mostStuck = motion.stuck();
                    UnitView unit = q.unit(handle);
                    worst = Handle.toString(handle)
                            + (unit == null
                                    ? ""
                                    : " at " + unit.x() + ", " + unit.z() + " toward " + unit.target() + " on tick "
                                            + q.tick());
                }
            }
        }
        Move move = orders.next(q.tick() + 1, q);
        if (move != null) {
            rt.submit(PlayerSlot.ADMIN, move, result -> {
                if (result.outcome() != CommandOutcome.ACCEPTED) {
                    refused++;
                }
            });
        }
    }

    private void finish(MatchQueries q) {
        int[] histogram = new int[RUNG_TICKS.length];
        for (int handle : q.unitHandles()) {
            int stuck = longest[Handle.index(handle)];
            int rung = 0;
            while (rung + 1 < RUNG_TICKS.length && stuck >= RUNG_TICKS[rung + 1]) {
                rung++;
            }
            histogram[rung]++;
        }
        long[] bytes = viewers.stream().mapToLong(v -> v.bytes().bytes()).toArray();
        viewers.forEach(MockViewer::close);
        done.accept(new Result(
                config,
                seed,
                core,
                tick,
                between,
                paths,
                config.budgets() ? baseline : new long[0],
                bytes,
                seenSamples == 0 ? 0 : (double) seenSum / seenSamples,
                seenMost,
                orders.groups(),
                orders.unitOrders(),
                refused,
                q.ladderRungs(),
                histogram,
                mostStuck,
                worst,
                q.surfaces().bytes(),
                q.unitCount(),
                exportJournal()));
    }

    /** {@code <out>/<name>.strj} for a replay, when the run names an output directory. */
    private @Nullable Path exportJournal() {
        String dir = System.getProperty("strategos.perf.out", System.getProperty("strategos.soak.out", ""));
        if (dir.isBlank()) {
            return null;
        }
        try {
            Files.createDirectories(Path.of(dir));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path file = Path.of(dir, config.name() + JournalFiles.EXTENSION);
        JournalFiles.write(
                file, new JournalExport(core(rt).journal(), core(rt).queries().tick(), core(rt).stateHash()));
        return file;
    }

    private void seatViewers() {
        double cx = manifest.sizeX() / 2.0;
        double cz = manifest.sizeZ() / 2.0;
        StartSlot west = manifest.startSlots().get(0);
        var at = rt.space().origin().getCenter().add(cx, west.y() + 2, cz);
        for (int k = 0; k < config.viewers(); k++) {
            MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-soak" + k, at.x, at.y, at.z, 12);
            viewer.capture().keep(false);
            StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(k));
            StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(k));
            viewers.add(viewer);
        }
    }

    /** Each faction's units round its start slot, in spawns of at most {@value #PER_SPAWN} a block of rows apart. */
    private void spawn() {
        for (int f = 0; f < 2; f++) {
            StartSlot slot = manifest.startSlots().get(f);
            int left = config.perFaction();
            for (int k = 0; left > 0; k++) {
                int count = Math.min(PER_SPAWN, left);
                left -= count;
                double dz = (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * 2 * SPREAD;
                rt.submit(
                        PlayerSlot.ADMIN,
                        new SpawnUnits(
                                TemplateSet.DEBUG_WALKER,
                                new FactionSlot(f),
                                new Vec2d(slot.x() + 0.5, slot.z() + 0.5 + dz),
                                count,
                                SPREAD),
                        result -> {
                            if (result.outcome() != CommandOutcome.ACCEPTED) {
                                refusedSpawn = String.valueOf(result);
                            }
                        });
            }
        }
        spawnedAt = System.nanoTime();
        next(Phase.SPAWNING);
    }

    /** The first few units without a body, where they stand and whether their chunk ticks entities. */
    private String unbodied() {
        StringBuilder out = new StringBuilder();
        int listed = 0;
        for (int handle : core(rt).queries().unitHandles()) {
            UnitView unit = core(rt).queries().unit(handle);
            if (unit == null || rt.bodies().get(handle) != null || listed++ >= 3) {
                continue;
            }
            BlockPos at = BlockPos.containing(
                    rt.space().worldX(unit.x()), unit.y(), rt.space().worldZ(unit.z()));
            out.append(Handle.toString(handle))
                    .append(" at ")
                    .append(at.toShortString())
                    .append(" entity ticking ")
                    .append(h.getLevel().isPositionEntityTicking(at))
                    .append("; ");
        }
        return out.toString();
    }

    private int maxIndex() {
        return Arrays.stream(core(rt).queries().unitHandles())
                .map(Handle::index)
                .max()
                .orElse(0);
    }

    private void next(Phase phase) {
        this.phase = phase;
        this.phaseTicks = 0;
    }

    /** The server's last finished tick, in nanoseconds, as {@code MinecraftServer#tickServer} stored it. */
    private long lastServerTick() {
        MinecraftServer server = h.getLevel().getServer();
        return server.getTickTimesNanos()[(server.getTickCount() - 1) % 100];
    }
}
