package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The shared scenarios. Each runs in L1 through {@link SimHarness#run} and as a GameTest (M0 spec 7.4); every call
 * returns a fresh instance, because a scenario remembers its own run. They act on the match's state, never on how often
 * {@link Scenario#onTick} is called.
 */
public final class Scenarios {
    private static final FactionSlot FACTION_0 = new FactionSlot(0);
    private static final FactionSlot FACTION_1 = new FactionSlot(1);

    private Scenarios() {}

    /** The functional scenarios, one GameTest each: walking_skeleton, out_of_bounds, stop_mid_walk. */
    public static List<Scenario> all() {
        return List.of(walkingSkeleton(Seeds.first("skeleton-20")), outOfBounds(), stopMidWalk());
    }

    /**
     * ORD-01: one unit on 32 x 32 at (4.5 + seed mod 5, 8.5 + 2 (seed mod 7)) (a floor modulus, so any seed stays
     * inside), spawned in setup; once it exists, at tick 1, an ADMIN Move 20 blocks along +X. The Move applies at tick
     * 2 and the unit walks 80 steps of 0.25: IDLE exactly on the target at tick 81, 80 ticks after the order and within
     * ORD-01.1's bound distance / speed + 1 = 81 ticks; the trace is exactly IDLE->WALKING, WALKING->IDLE.
     */
    public static Scenario walkingSkeleton(long seed) {
        return new WalkingSkeleton(seed);
    }

    /** ORD-01.2: an ADMIN Move to (40, 16) on 32 x 32 is REJECTED OUT_OF_BOUNDS, and the unit stays put for 40 ticks. */
    public static Scenario outOfBounds() {
        return new OutOfBounds();
    }

    /** A unit walking 24 blocks along +X gets a Stop that applies at tick 30: IDLE at once, then frozen for 20 ticks. */
    public static Scenario stopMidWalk() {
        return new StopMidWalk();
    }

    /**
     * Load and isolation runs: {@code units} units of faction 0 spawned in setup in the west quarter; every tick, the
     * idle units of each half get a Move to the far end, so the crowd shuttles east and west for {@code ticks} ticks.
     */
    public static Scenario patrol(long seed, int units, int sizeX, int sizeZ, int ticks) {
        return new Patrol(seed, units, sizeX, sizeZ, ticks);
    }

    /**
     * S6's determinism canary: 64 x 64, two factions of 30 units; every 10 ticks each faction's player sends what
     * {@link RandomCommands} draws; 2,000 ticks.
     */
    public static Scenario canary(long seed) {
        return new Canary(seed);
    }

    /**
     * PATH-03's golden journal (spec §5.10): 64 x 48 of terrain, a ridge of one-block steps at x 20..26, then a river at
     * x 36..41, two blocks deep but for a ford at z 30..35. Setup publishes the pages and spawns four units in the west;
     * once they exist they are ordered east, across the ridge and through the ford; done when all four stand on their
     * targets. L1 only: a GameTest takes its terrain from its world.
     */
    public static Scenario crossingSynthetic() {
        return new CrossingSynthetic();
    }

    /**
     * PATH-06's golden journal and its acceptance 1 and 2 in L1 (spec §5.10, §5.11): 64 x 32 of flat terrain with a
     * wall two blocks thick at x 31..32, three high, but for a gap six blocks wide at z 13..18. Fifty units of faction
     * 0 stand in ten columns and five rows 1.5 blocks apart from (4.5, 12.5), fifty of faction 1 mirrored east of the
     * wall; once all exist, each group is ordered {@value HeadOnGap#SHIFT} blocks across the wall, keeping its
     * formation, so the groups meet head-on in the gap, row against row on the same lines, and the first to arrive
     * take the farthest places. The spacing leaves room to walk between units that have arrived: one block, with radii
     * of 0.35, would not. Done when every unit is idle within 2.5 blocks of its target: it arrives on it, or within
     * 1.5 blocks while another stands on it, and may step aside since by less than the latch's block. No two units may stay closer
     * than {@value HeadOnGap#CLOSE} block for more than {@value HeadOnGap#CLOSE_TICKS} ticks in a row. L1 only.
     */
    public static Scenario headOnGap() {
        return new HeadOnGap();
    }

    private static void check(boolean ok, String failure) {
        if (!ok) {
            throw new ScenarioFailure(failure);
        }
    }

    private static UnitView alive(ScenarioContext ctx, int unit) {
        UnitView view = unit == Handle.NONE ? null : ctx.queries().unit(unit);
        if (view == null) {
            throw new ScenarioFailure("the unit " + Handle.toString(unit) + " does not exist");
        }
        return view;
    }

    /** The first unit of faction 0, or NONE before it exists. */
    private static int firstUnit(ScenarioContext ctx) {
        int[] units = ctx.queries().unitHandlesOf(FACTION_0);
        return units.length == 0 ? Handle.NONE : units[0];
    }

    private static List<String> trace(ScenarioContext ctx, int unit) {
        return ctx.stateChanges().stream()
                .filter(c -> c.handle() == unit)
                .map(c -> c.from() + "->" + c.to())
                .toList();
    }

    private static @Nullable CommandResult resultOf(ScenarioContext ctx, PlayerSlot issuer, int clientSeq) {
        for (CommandResult r : ctx.results()) {
            if (r.issuer().equals(issuer) && r.clientSeq() == clientSeq) {
                return r;
            }
        }
        return null;
    }

    private static final class WalkingSkeleton implements Scenario {
        private static final double DISTANCE = 20;

        private final long seed;
        private final Vec2d start;
        private final Vec2d target;
        private int unit = Handle.NONE;
        private long orderedAt = -1;
        private long walkingAt = -1;

        WalkingSkeleton(long seed) {
            this.seed = seed;
            this.start = new Vec2d(4.5 + Math.floorMod(seed, 5), 8.5 + 2 * Math.floorMod(seed, 7));
            this.target = new Vec2d(start.x() + DISTANCE, start.z());
        }

        @Override
        public String id() {
            return "walking_skeleton";
        }

        @Override
        public int sizeX() {
            return 32;
        }

        @Override
        public int sizeZ() {
            return 32;
        }

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public int timeoutTicks() {
            return 200;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            ctx.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, start, 1, 0));
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            if (unit == Handle.NONE) {
                unit = firstUnit(ctx);
            }
            UnitView view = unit == Handle.NONE ? null : ctx.queries().unit(unit);
            if (view == null) {
                return;
            }
            if (orderedAt < 0) {
                ctx.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), target));
                orderedAt = ctx.tick();
            } else if (walkingAt < 0 && view.state() == UnitState.WALKING) {
                walkingAt = ctx.tick();
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            if (walkingAt < 0) {
                return false;
            }
            UnitView view = ctx.queries().unit(unit);
            return view != null && view.state() == UnitState.IDLE;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            UnitView view = alive(ctx, unit);
            double speed =
                    ctx.queries().templates().byIndex(view.templateIndex()).walkSpeed();
            long steps = Math.round(DISTANCE / speed);
            long bound = steps + 1; // ORD-01.1: distance / speed + 1
            check(
                    view.x() == target.x() && view.z() == target.z() && view.target() == null,
                    "the unit stands at (" + view.x() + ", " + view.z() + "), not exactly on the target " + target);
            check(orderedAt == 1, "the Move went out at tick " + orderedAt + ", not at tick 1");
            check(walkingAt == orderedAt + 1, "the Move applied at tick " + walkingAt + ", not " + (orderedAt + 1));
            check(
                    ctx.tick() == orderedAt + steps,
                    "the unit arrived at tick " + ctx.tick() + ", not " + (orderedAt + steps) + " (" + steps
                            + " steps after the Move applied)");
            check(
                    ctx.tick() - orderedAt <= bound,
                    "the unit took " + (ctx.tick() - orderedAt) + " ticks, more than the bound " + bound);
            List<String> trace = trace(ctx, unit);
            check(
                    trace.equals(List.of("IDLE->WALKING", "WALKING->IDLE")),
                    "the trace is " + trace + ", not [IDLE->WALKING, WALKING->IDLE]");
        }
    }

    private static final class OutOfBounds implements Scenario {
        private static final Vec2d START = new Vec2d(16.5, 16.5);
        private static final Vec2d OUTSIDE = new Vec2d(40, 16);
        private static final int WATCH_TICKS = 40;

        private int unit = Handle.NONE;
        private int moveSeq = -1;
        private long orderedAt = -1;
        private @Nullable String moved;

        @Override
        public String id() {
            return "out_of_bounds";
        }

        @Override
        public int sizeX() {
            return 32;
        }

        @Override
        public int sizeZ() {
            return 32;
        }

        @Override
        public long seed() {
            return SimHarness.DEFAULT_SEED;
        }

        @Override
        public int timeoutTicks() {
            return 100;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            ctx.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, START, 1, 0));
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            if (unit == Handle.NONE) {
                unit = firstUnit(ctx);
            }
            UnitView view = unit == Handle.NONE ? null : ctx.queries().unit(unit);
            if (view == null) {
                return;
            }
            if (orderedAt < 0) {
                moveSeq = ctx.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), OUTSIDE));
                orderedAt = ctx.tick();
            } else if (moved == null
                    && (view.x() != START.x() || view.z() != START.z() || view.state() != UnitState.IDLE)) {
                moved = "at tick " + ctx.tick() + " the unit is " + view.state() + " at (" + view.x() + ", " + view.z()
                        + ")";
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            return orderedAt >= 0 && ctx.tick() >= orderedAt + 1 + WATCH_TICKS;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            CommandResult result = resultOf(ctx, PlayerSlot.ADMIN, moveSeq);
            check(result != null, "no result for the Move");
            check(
                    result.outcome() == CommandOutcome.REJECTED
                            && result.firstReason() == RejectReason.OUT_OF_BOUNDS
                            && result.accepted() == 0
                            && result.rejected() == 1,
                    "the Move to " + OUTSIDE + " got " + result + ", not REJECTED OUT_OF_BOUNDS for 1 unit");
            check(moved == null, String.valueOf(moved));
            UnitView view = alive(ctx, unit);
            check(
                    view.x() == START.x() && view.z() == START.z() && view.state() == UnitState.IDLE,
                    "the unit moved to (" + view.x() + ", " + view.z() + ")");
            check(trace(ctx, unit).isEmpty(), "the unit changed state: " + trace(ctx, unit));
        }
    }

    private static final class StopMidWalk implements Scenario {
        private static final Vec2d START = new Vec2d(4.5, 16.5);
        private static final Vec2d TARGET = new Vec2d(28.5, 16.5);
        private static final long STOP_AT = 30;
        private static final int FROZEN_TICKS = 20;

        private int unit = Handle.NONE;
        private long orderedAt = -1;
        private int stopSeq = -1;
        private long stoppedAt = -1;
        private double frozenX;
        private double frozenZ;
        private @Nullable String moved;

        @Override
        public String id() {
            return "stop_mid_walk";
        }

        @Override
        public int sizeX() {
            return 32;
        }

        @Override
        public int sizeZ() {
            return 32;
        }

        @Override
        public long seed() {
            return SimHarness.DEFAULT_SEED;
        }

        @Override
        public int timeoutTicks() {
            return 100;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            ctx.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, START, 1, 0));
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            if (unit == Handle.NONE) {
                unit = firstUnit(ctx);
            }
            UnitView view = unit == Handle.NONE ? null : ctx.queries().unit(unit);
            if (view == null) {
                return;
            }
            if (orderedAt < 0) {
                ctx.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), TARGET));
                orderedAt = ctx.tick();
            } else if (stopSeq < 0) {
                if (ctx.tick() >= STOP_AT - 1 && view.state() == UnitState.WALKING) {
                    stopSeq = ctx.submit(PlayerSlot.ADMIN, new Stop(UnitSet.of(unit))); // applies at STOP_AT
                }
            } else if (stoppedAt < 0) {
                if (view.state() == UnitState.IDLE) {
                    stoppedAt = ctx.tick();
                    frozenX = view.x();
                    frozenZ = view.z();
                }
            } else if (moved == null
                    && (view.x() != frozenX || view.z() != frozenZ || view.state() != UnitState.IDLE)) {
                moved = "at tick " + ctx.tick() + " the stopped unit is " + view.state() + " at (" + view.x() + ", "
                        + view.z() + ")";
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            return stoppedAt >= 0 && ctx.tick() >= stoppedAt + FROZEN_TICKS;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            CommandResult result = resultOf(ctx, PlayerSlot.ADMIN, stopSeq);
            check(result != null, "no result for the Stop");
            check(
                    result.outcome() == CommandOutcome.ACCEPTED && result.accepted() == 1,
                    "the Stop got " + result + ", not ACCEPTED for 1 unit");
            check(stoppedAt == STOP_AT, "the unit stopped at tick " + stoppedAt + ", not " + STOP_AT);
            check(moved == null, String.valueOf(moved));
            UnitView view = alive(ctx, unit);
            check(
                    view.state() == UnitState.IDLE && view.target() == null,
                    "the unit is " + view.state() + " with target " + view.target());
            check(
                    view.x() > START.x() && view.x() < TARGET.x(),
                    "the unit stopped at x " + view.x() + ", not between the start and the target");
            List<String> trace = trace(ctx, unit);
            check(
                    trace.equals(List.of("IDLE->WALKING", "WALKING->IDLE")),
                    "the trace is " + trace + ", not [IDLE->WALKING, WALKING->IDLE]");
        }
    }

    private static final class Patrol implements Scenario {
        private final long seed;
        private final int units;
        private final int sizeX;
        private final int sizeZ;
        private final int ticks;
        private long orderedAt = -1;

        Patrol(long seed, int units, int sizeX, int sizeZ, int ticks) {
            if (units < 1 || sizeX < 8 || sizeZ < 8 || ticks < 1) {
                throw new IllegalArgumentException("patrol needs units, at least 8 x 8 and ticks: " + units + ", "
                        + sizeX + " x " + sizeZ + ", " + ticks);
            }
            this.seed = seed;
            this.units = units;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.ticks = ticks;
        }

        @Override
        public String id() {
            return "patrol";
        }

        @Override
        public int sizeX() {
            return sizeX;
        }

        @Override
        public int sizeZ() {
            return sizeZ;
        }

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public int timeoutTicks() {
            return ticks + 20;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            Vec2d center = new Vec2d(sizeX / 4.0, sizeZ / 2.0);
            double spread = Math.min(64, Math.min(sizeX / 4.0, sizeZ / 2.0) - 1);
            for (int left = units; left > 0; left -= UnitSet.MAX_SIZE) {
                int count = Math.min(left, UnitSet.MAX_SIZE);
                ctx.submit(
                        PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, center, count, spread));
            }
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            if (orderedAt == ctx.tick()) {
                return; // this tick's orders are out
            }
            orderedAt = ctx.tick();
            MatchQueries q = ctx.queries();
            List<Integer> west = new ArrayList<>();
            List<Integer> east = new ArrayList<>();
            for (int handle : q.unitHandlesOf(FACTION_0)) {
                UnitView view = q.unit(handle);
                if (view != null && view.state() == UnitState.IDLE) {
                    (view.x() < sizeX / 2.0 ? west : east).add(handle);
                }
            }
            order(ctx, west, new Vec2d(sizeX - 1.5, sizeZ / 2.0));
            order(ctx, east, new Vec2d(1.5, sizeZ / 2.0));
        }

        private static void order(ScenarioContext ctx, List<Integer> handles, Vec2d target) {
            for (int from = 0; from < handles.size(); from += UnitSet.MAX_SIZE) {
                List<Integer> part = handles.subList(from, Math.min(handles.size(), from + UnitSet.MAX_SIZE));
                Command move = Move.replace(
                        UnitSet.of(part.stream().mapToInt(Integer::intValue).toArray()), target);
                ctx.submit(PlayerSlot.ADMIN, move);
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            return ctx.tick() >= ticks;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            int alive = ctx.queries().unitCount();
            check(alive == units, alive + " units alive, not " + units);
        }
    }

    private static final class CrossingSynthetic implements Scenario {
        private static final int[] ROWS = {6, 14, 22, 40};
        private static final int TICKS = 1_200;

        private boolean ordered;

        @Override
        public String id() {
            return "crossing_synthetic";
        }

        @Override
        public int sizeX() {
            return 64;
        }

        @Override
        public int sizeZ() {
            return 48;
        }

        @Override
        public long seed() {
            return 3;
        }

        @Override
        public int timeoutTicks() {
            return TICKS;
        }

        static VoxelWorld terrain() {
            VoxelWorld w = VoxelFixtures.flat(64, 48, 16, 1);
            int[] ridge = {2, 3, 4, 4, 3, 2, 1};
            for (int i = 0; i < ridge.length; i++) {
                w.fill(20 + i, 0, 0, 20 + i, ridge[i] - 1, 47, VoxelFixtures.SOLID);
            }
            for (int z = 0; z < 48; z++) {
                boolean ford = z >= 30 && z <= 35;
                w.fill(36, 1, z, 41, ford ? 1 : 2, z, VoxelFixtures.WATER);
            }
            return w;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            VoxelWorld w = terrain();
            PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
            for (int pz = 0; pz < 3; pz++) {
                for (int px = 0; px < 4; px++) {
                    PageCoord coord = new PageCoord(px, pz);
                    ctx.publish(TerrainPage.build(coord, w.slice(coord), builder));
                }
            }
            for (int row : ROWS) {
                ctx.submit(
                        PlayerSlot.ADMIN,
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(4.5, row + 0.5), 1, 0));
            }
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            int[] units = ctx.queries().unitHandlesOf(FACTION_0);
            if (ordered || units.length < ROWS.length) {
                return;
            }
            ordered = true;
            for (int i = 0; i < units.length; i++) {
                UnitView unit = alive(ctx, units[i]);
                ctx.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[i]), new Vec2d(58.5, unit.z())));
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            if (!ordered) {
                return false;
            }
            for (int unit : ctx.queries().unitHandlesOf(FACTION_0)) {
                if (alive(ctx, unit).state() != UnitState.IDLE
                        || alive(ctx, unit).x() != 58.5) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            int[] units = ctx.queries().unitHandlesOf(FACTION_0);
            check(units.length == ROWS.length, "expected " + ROWS.length + " units, found " + units.length);
            check(ctx.queries().pathStats().ok() >= ROWS.length, "every crossing needs a path to its goal");
            for (int unit : units) {
                check(
                        trace(ctx, unit).equals(List.of("IDLE->WALKING", "WALKING->IDLE")),
                        Handle.toString(unit) + " walked once: " + trace(ctx, unit));
            }
        }
    }

    private static final class HeadOnGap implements Scenario {
        static final double CLOSE = 0.4;
        static final int CLOSE_TICKS = 5;
        private static final int COLUMNS = 10;
        private static final int ROWS = 5;
        private static final int UNITS = 2 * COLUMNS * ROWS;
        private static final double SPACING = 1.5;
        private static final double SHIFT = 41.5;
        /** A unit that arrived beside a taken target, and may have stepped aside since, by less than a block. */
        private static final double ARRIVED = 2.5;

        private int[] units = new int[0];
        private double[] targetX = new double[0];
        private double[] targetZ = new double[0];
        private int[] streak = new int[0];
        private int longestClose;

        @Override
        public String id() {
            return "head_on_gap";
        }

        @Override
        public int sizeX() {
            return 64;
        }

        @Override
        public int sizeZ() {
            return 32;
        }

        @Override
        public long seed() {
            return 6;
        }

        @Override
        public int timeoutTicks() {
            return 2_000;
        }

        static VoxelWorld terrain() {
            VoxelWorld w = VoxelFixtures.flat(64, 32, 8, 1);
            w.fill(31, 1, 0, 32, 3, 12, VoxelFixtures.SOLID);
            w.fill(31, 1, 19, 32, 3, 31, VoxelFixtures.SOLID);
            return w;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            VoxelWorld w = terrain();
            PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
            for (int pz = 0; pz < 2; pz++) {
                for (int px = 0; px < 4; px++) {
                    PageCoord coord = new PageCoord(px, pz);
                    ctx.publish(TerrainPage.build(coord, w.slice(coord), builder));
                }
            }
            for (int c = 0; c < COLUMNS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    Vec2d west = new Vec2d(4.5 + SPACING * c, 12.5 + SPACING * r);
                    ctx.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, west, 1, 0));
                    Vec2d east = new Vec2d(64 - west.x(), west.z());
                    ctx.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, east, 1, 0));
                }
            }
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            if (units.length == 0) {
                int[] all = ctx.queries().unitHandles();
                if (all.length < UNITS) {
                    return;
                }
                units = all;
                targetX = new double[UNITS];
                targetZ = new double[UNITS];
                streak = new int[UNITS * UNITS];
                for (int i = 0; i < UNITS; i++) {
                    UnitView unit = alive(ctx, units[i]);
                    targetX[i] = unit.x() < 32 ? unit.x() + SHIFT : unit.x() - SHIFT;
                    targetZ[i] = unit.z();
                    ctx.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[i]), new Vec2d(targetX[i], targetZ[i])));
                }
                return;
            }
            double[] xs = new double[UNITS];
            double[] zs = new double[UNITS];
            for (int i = 0; i < UNITS; i++) {
                UnitView unit = alive(ctx, units[i]);
                xs[i] = unit.x();
                zs[i] = unit.z();
            }
            for (int i = 0; i < UNITS; i++) {
                for (int j = i + 1; j < UNITS; j++) {
                    int k = i * UNITS + j;
                    streak[k] = Math.hypot(xs[i] - xs[j], zs[i] - zs[j]) < CLOSE ? streak[k] + 1 : 0;
                    longestClose = Math.max(longestClose, streak[k]);
                }
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            if (units.length == 0) {
                return false;
            }
            for (int i = 0; i < UNITS; i++) {
                UnitView unit = alive(ctx, units[i]);
                if (unit.state() != UnitState.IDLE
                        || Math.hypot(unit.x() - targetX[i], unit.z() - targetZ[i]) > ARRIVED) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            check(units.length == UNITS, "expected " + UNITS + " units, found " + units.length);
            check(
                    longestClose <= CLOSE_TICKS,
                    "two units stayed closer than " + CLOSE + " for " + longestClose + " ticks");
        }
    }

    private static final class Canary implements Scenario {
        private static final int UNITS = 30;
        private static final int EVERY = 10;
        private static final int TICKS = 2_000;

        private final long seed;
        private long issuedAt = -1;

        Canary(long seed) {
            this.seed = seed;
        }

        @Override
        public String id() {
            return "canary";
        }

        @Override
        public int sizeX() {
            return 64;
        }

        @Override
        public int sizeZ() {
            return 64;
        }

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public int timeoutTicks() {
            return TICKS + 20;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            ctx.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(16, 32), UNITS, 12));
            ctx.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, new Vec2d(48, 32), UNITS, 12));
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            long tick = ctx.tick();
            if (tick == 0 || tick % EVERY != 0 || issuedAt == tick) {
                return;
            }
            issuedAt = tick;
            for (FactionSlot faction : List.of(FACTION_0, FACTION_1)) {
                for (Command command : RandomCommands.next(seed, tick, ctx.queries(), faction)) {
                    ctx.submit(new PlayerSlot(faction.value()), command);
                }
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            return ctx.tick() >= TICKS;
        }

        @Override
        public void verify(ScenarioContext ctx) {
            for (FactionSlot faction : List.of(FACTION_0, FACTION_1)) {
                int alive = ctx.queries().unitHandlesOf(faction).length;
                check(alive == UNITS, "faction " + faction.value() + " has " + alive + " units, not " + UNITS);
            }
        }
    }
}
