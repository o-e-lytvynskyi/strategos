package dev.strategos.gametest;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.DeliveryMode;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.testkit.Percentiles;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.StepPacer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Function;

/**
 * Spike S7's load (spec §8.4), also budget D's perf test: on the proving ground's terrain, the very terrain its map
 * package is written from, a match stepped on the calling thread with the given job executor sends 200 units scattered
 * over the map to far targets in one tick, and the next tick 100 units at the west start as one group to the east
 * start, then steps until every unit has its answer, each tick 50 ms after the one before, as a server at 20 ticks a
 * second gives the workers their time. It measures the main thread's path work each tick (delivery, joins, requests
 * and admission) and the part of it spent joining, and each unit's latency in ticks from its order to its result.
 */
public final class PathLoad {
    public static final int SCATTERED = 200;
    public static final int GROUP = 100;
    public static final int MAX_TICKS = 600;
    public static final long TICK_NANOS = 50_000_000;
    public static final int GROUP_OF = 150;
    public static final int AFTER = 40;

    private final ProvingGround ground = ProvingGround.generate();
    private final List<TerrainPage> pages = new ArrayList<>();

    /**
     * One run: the main thread's path work on each tick from the group's order on, and the part spent joining jobs, in
     * nanoseconds; each group unit's latency and every unit's, in ticks; and the path service's counts at the end.
     */
    public static final class Result {
        private final long[] work;
        private final long[] joins;
        private final long[] groupLatency;
        private final long[] allLatency;
        private final PathService.Stats stats;

        Result(long[] work, long[] joins, long[] groupLatency, long[] allLatency, PathService.Stats stats) {
            this.work = work;
            this.joins = joins;
            this.groupLatency = groupLatency;
            this.allLatency = allLatency;
            this.stats = stats;
        }

        public PathService.Stats stats() {
            return stats;
        }

        /** The main thread's path work on each measured tick, in nanoseconds. */
        public long[] work() {
            return work.clone();
        }

        /** Several runs as one: their ticks, joins and latencies together, the last run's counts. */
        public static Result pooled(List<Result> runs) {
            return new Result(
                    concat(runs, r -> r.work),
                    concat(runs, r -> r.joins),
                    concat(runs, r -> r.groupLatency),
                    concat(runs, r -> r.allLatency),
                    runs.get(runs.size() - 1).stats);
        }

        private static long[] concat(List<Result> runs, Function<Result, long[]> part) {
            return runs.stream().map(part).flatMapToLong(Arrays::stream).toArray();
        }

        public int ticks() {
            return work.length;
        }

        public long workP50() {
            return Percentiles.percentile(work, 0.50);
        }

        public long workP99() {
            return Percentiles.percentile(work, 0.99);
        }

        public long workMax() {
            return Arrays.stream(work).max().orElse(0);
        }

        public long joinP99() {
            return Percentiles.percentile(joins, 0.99);
        }

        public long groupP50() {
            return Percentiles.percentile(groupLatency, 0.50);
        }

        public long groupP95() {
            return Percentiles.percentile(groupLatency, 0.95);
        }

        public long groupMax() {
            return Arrays.stream(groupLatency).max().orElse(0);
        }

        public long allP95() {
            return Percentiles.percentile(allLatency, 0.95);
        }

        /** Units still without their answer when the run gave up. */
        public long unanswered() {
            return Arrays.stream(allLatency).filter(t -> t == Long.MAX_VALUE).count();
        }
    }

    public PathLoad() {
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        var voxels = ground.voxels();
        int side = ProvingGround.SIZE / 16;
        for (int pz = 0; pz < side; pz++) {
            for (int px = 0; px < side; px++) {
                PageCoord coord = new PageCoord(px, pz);
                pages.add(TerrainPage.build(coord, voxels.slice(coord), builder));
            }
        }
    }

    /** The default rules with {@code mode}, {@code k} and W, and every order of a tick applied that tick. */
    public static MatchRules rules(DeliveryMode mode, int k, int w) {
        MatchRules d = MatchRules.DEFAULTS;
        return new MatchRules(
                4_096,
                4_096,
                mode,
                k,
                d.pageK(),
                w,
                d.maxOutstanding(),
                d.busyRetry(),
                d.searchCap(),
                d.joinCap(),
                d.shortMove(),
                d.groupMin());
    }

    public Result run(MatchRules rules, JobExecutor jobs) {
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .rules(rules)
                .jobs(jobs)
                .clock(System::nanoTime)
                .build();
        pages.forEach(h::publish);
        SplittableRandom random = new SplittableRandom(7);
        List<Vec2d> starts = new ArrayList<>();
        List<Vec2d> goals = new ArrayList<>();
        while (starts.size() < SCATTERED) {
            Vec2d start = land(random);
            Vec2d goal = land(random);
            if (Math.hypot(goal.x() - start.x(), goal.z() - start.z()) > 120) {
                starts.add(start);
                goals.add(goal);
            }
        }
        for (Vec2d start : starts) {
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), start, 1, 0));
        }
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24.5, 192.5), GROUP, 6));
        h.step();
        List<Integer> scattered = new ArrayList<>();
        List<Integer> group = new ArrayList<>();
        for (int handle : h.queries().unitHandles()) {
            UnitView unit = h.queries().unit(handle);
            (unit != null && unit.faction().value() == 1 ? group : scattered).add(handle);
        }
        h.runTicks(5); // onto the terrain
        for (int k = 0; k < scattered.size(); k++) {
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(scattered.get(k)), goals.get(k)));
        }
        StepPacer pacer = new StepPacer(TICK_NANOS);
        h.step();
        long scatteredAt = h.tick();
        h.submit(
                PlayerSlot.ADMIN,
                Move.replace(
                        UnitSet.of(group.stream().mapToInt(Integer::intValue).toArray()), new Vec2d(360.5, 192.5)));
        long[] work = new long[MAX_TICKS];
        long[] joins = new long[MAX_TICKS];
        long joined = h.queries().pathStats().joinNanos();
        long[] all = new long[scattered.size() + group.size()];
        Arrays.fill(all, -1);
        int ticks = 0;
        int answered = 0;
        long groupAt = -1;
        while (answered < all.length && ticks < MAX_TICKS) {
            pacer.awaitTick();
            h.step();
            if (groupAt < 0) {
                groupAt = h.tick();
            }
            PathService.Stats stats = h.queries().pathStats();
            work[ticks] = stats.lastWork();
            joins[ticks++] = stats.joinNanos() - joined;
            joined = stats.joinNanos();
            answered = 0;
            for (int k = 0; k < all.length; k++) {
                boolean inGroup = k >= scattered.size();
                int handle = inGroup ? group.get(k - scattered.size()) : scattered.get(k);
                if (all[k] < 0 && answered(h, handle)) {
                    all[k] = h.tick() - (inGroup ? groupAt : scatteredAt);
                }
                if (all[k] >= 0) {
                    answered++;
                }
            }
        }
        for (int k = 0; k < all.length; k++) {
            if (all[k] < 0) {
                all[k] = Long.MAX_VALUE; // never answered within the run
            }
        }
        return new Result(
                Arrays.copyOf(work, ticks),
                Arrays.copyOf(joins, ticks),
                Arrays.copyOfRange(all, scattered.size(), all.length),
                all,
                h.queries().pathStats());
    }

    /**
     * Budget D's group load (PATH-09.3): 600 units in four groups of 150, at the middle of each edge, each sent as one
     * group across to the opposite edge in one tick; the ticks are paced as in {@link #run} until every unit has its
     * answer, then {@value #AFTER} more, while the groups walk and the ladder repaths whoever is held up. The work is
     * measured from the orders' tick; the latencies are every unit's.
     */
    public Result groups(MatchRules rules, JobExecutor jobs) {
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .rules(rules)
                .jobs(jobs)
                .clock(System::nanoTime)
                .build();
        pages.forEach(h::publish);
        double side = ProvingGround.SIZE;
        double middle = side / 2 + 0.5;
        Vec2d[] from = {
            new Vec2d(40.5, middle),
            new Vec2d(side - 40.5, middle),
            new Vec2d(middle, 40.5),
            new Vec2d(middle, side - 40.5)
        };
        for (int g = 0; g < from.length; g++) {
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(g % 2), from[g], GROUP_OF, 12));
            h.step();
        }
        int[] all = h.queries().unitHandles();
        h.runTicks(5); // onto the terrain
        for (int g = 0; g < from.length; g++) {
            int[] members = Arrays.copyOfRange(all, g * GROUP_OF, (g + 1) * GROUP_OF);
            Vec2d to = from[g ^ 1];
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(members), to));
        }
        long[] work = new long[MAX_TICKS];
        long[] joins = new long[MAX_TICKS];
        long joined = h.queries().pathStats().joinNanos();
        long[] latency = new long[all.length];
        Arrays.fill(latency, -1);
        int ticks = 0;
        int after = -1;
        long orderedAt = h.tick() + 1;
        StepPacer pacer = new StepPacer(TICK_NANOS);
        while (after < AFTER && ticks < MAX_TICKS) {
            pacer.awaitTick();
            h.step();
            PathService.Stats stats = h.queries().pathStats();
            work[ticks] = stats.lastWork();
            joins[ticks++] = stats.joinNanos() - joined;
            joined = stats.joinNanos();
            int answered = 0;
            for (int k = 0; k < all.length; k++) {
                if (latency[k] < 0 && answered(h, all[k])) {
                    latency[k] = h.tick() - orderedAt;
                }
                answered += latency[k] >= 0 ? 1 : 0;
            }
            if (answered == all.length || after >= 0) {
                after++;
            }
        }
        for (int k = 0; k < latency.length; k++) {
            if (latency[k] < 0) {
                latency[k] = Long.MAX_VALUE;
            }
        }
        return new Result(
                Arrays.copyOf(work, ticks),
                Arrays.copyOf(joins, ticks),
                latency,
                latency,
                h.queries().pathStats());
    }

    /** Whether the unit's last request has its answer: it waits for none and was not turned away. */
    private static boolean answered(SimHarness h, int handle) {
        PathStore.UnitPath path = h.queries().path(handle);
        return path != null
                && path.request() == 0
                && path.lastRequest() != PathStore.NEVER
                && path.status() != PathStatus.BUSY;
    }

    /** A column centre on land, away from the map's edge. */
    private Vec2d land(SplittableRandom random) {
        while (true) {
            int x = 8 + random.nextInt(ProvingGround.SIZE - 16);
            int z = 8 + random.nextInt(ProvingGround.SIZE - 16);
            if (ground.block(x, ground.top(x, z), z) != ProvingGround.WATER) {
                return new Vec2d(x + 0.5, z + 0.5);
            }
        }
    }
}
