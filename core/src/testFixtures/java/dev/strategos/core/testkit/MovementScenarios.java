package dev.strategos.core.testkit;

import static dev.strategos.core.testkit.Scenarios.FACTION_0;
import static dev.strategos.core.testkit.Scenarios.FACTION_1;
import static dev.strategos.core.testkit.Scenarios.alive;
import static dev.strategos.core.testkit.Scenarios.check;
import static dev.strategos.core.testkit.Scenarios.trace;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import java.util.ArrayList;
import java.util.List;

/** {@link Scenarios}' movement scenarios: the shuttling patrol, the synthetic crossing (PATH-03) and S6's canary. */
final class MovementScenarios {
    private MovementScenarios() {}

    static final class Patrol implements Scenario {
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

    static final class CrossingSynthetic implements Scenario {
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

    static final class Canary implements Scenario {
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
