package dev.strategos.core.testkit;

import static dev.strategos.core.testkit.Scenarios.FACTION_0;
import static dev.strategos.core.testkit.Scenarios.FACTION_1;
import static dev.strategos.core.testkit.Scenarios.alive;
import static dev.strategos.core.testkit.Scenarios.check;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;

/** {@link Scenarios}' crowd scenarios: fifty units into their slots (ORD-03) and two groups head-on in a gap (PATH-06). */
final class CrowdScenarios {
    private CrowdScenarios() {}

    static final class GroupOf50Slots implements Scenario {
        static final double NEAR = 1.5;
        static final double APART = 0.6;
        static final int SETTLE = 60;
        private static final int UNITS = 50;
        private static final Vec2d TARGET = new Vec2d(46.5, 24.5);

        private int[] units = new int[0];
        private double[] placeX = new double[0];
        private double[] placeZ = new double[0];
        private double[] settledOff = new double[0]; // how far from its place each unit came to rest, -1 before
        private long restedAt = -1; // the tick all stood idle since, -1 while one walks
        private final long seed;

        GroupOf50Slots(long seed) {
            this.seed = seed;
        }

        @Override
        public String id() {
            return "group_of_50_slots";
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
            return seed;
        }

        @Override
        public int timeoutTicks() {
            return 1_000;
        }

        @Override
        public void setup(ScenarioContext ctx) {
            VoxelWorld w = VoxelFixtures.flat(64, 48, 8, 1);
            PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
            for (int pz = 0; pz < 3; pz++) {
                for (int px = 0; px < 4; px++) {
                    PageCoord coord = new PageCoord(px, pz);
                    ctx.publish(TerrainPage.build(coord, w.slice(coord), builder));
                }
            }
            ctx.submit(
                    PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(10, 24), UNITS, 6));
        }

        @Override
        public void onTick(ScenarioContext ctx) {
            if (units.length == 0) {
                int[] all = ctx.queries().unitHandlesOf(FACTION_0);
                if (all.length < UNITS) {
                    return;
                }
                units = all;
                ctx.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(all), TARGET));
                return;
            }
            if (placeX.length == 0) {
                placeX = new double[UNITS];
                placeZ = new double[UNITS];
                settledOff = new double[UNITS];
                for (int k = 0; k < UNITS; k++) {
                    var orders = ctx.queries().orders(units[k]);
                    check(!orders.isEmpty(), "unit " + Handle.toString(units[k]) + " got no order");
                    placeX[k] = orders.get(0).x();
                    placeZ[k] = orders.get(0).z();
                    settledOff[k] = -1;
                }
                return;
            }
            for (int k = 0; k < UNITS; k++) { // members trade places in a crowd (spec §1.6 D row 151)
                var orders = ctx.queries().orders(units[k]);
                if (orders.isEmpty()
                        || (orders.get(0).x() == placeX[k] && orders.get(0).z() == placeZ[k])) {
                    continue;
                }
                for (int m = 0; m < UNITS; m++) { // the one that took this unit's place got its new one
                    if (m != k
                            && placeX[m] == orders.get(0).x()
                            && placeZ[m] == orders.get(0).z()) {
                        placeX[m] = placeX[k];
                        placeZ[m] = placeZ[k];
                    }
                }
                placeX[k] = orders.get(0).x();
                placeZ[k] = orders.get(0).z();
            }
            for (int k = 0; k < UNITS; k++) {
                UnitView u = alive(ctx, units[k]);
                if (settledOff[k] < 0 && u.state() == UnitState.IDLE) {
                    settledOff[k] = Math.hypot(u.x() - placeX[k], u.z() - placeZ[k]);
                }
            }
        }

        @Override
        public boolean isDone(ScenarioContext ctx) {
            if (placeX.length == 0) {
                return false;
            }
            for (int unit : units) {
                if (alive(ctx, unit).state() != UnitState.IDLE) {
                    restedAt = -1;
                    return false;
                }
            }
            if (restedAt < 0) {
                restedAt = ctx.queries().tick();
            }
            return ctx.queries().tick() - restedAt >= SETTLE; // standing units part slowly, to 0.6 in the limit
        }

        @Override
        public void verify(ScenarioContext ctx) {
            for (int k = 0; k < UNITS; k++) {
                UnitView u = alive(ctx, units[k]);
                check(
                        settledOff[k] >= 0 && settledOff[k] <= NEAR + 1e-9,
                        "unit " + Handle.toString(units[k]) + " came to rest " + settledOff[k] + " from its place");
                for (int j = 0; j < k; j++) {
                    UnitView v = alive(ctx, units[j]);
                    double gap = Math.hypot(u.x() - v.x(), u.z() - v.z());
                    check(gap >= APART - 1e-3, "two units stand " + gap + " apart"); // to 0.6 in the limit
                }
            }
        }
    }

    static final class HeadOnGap implements Scenario {
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
}
