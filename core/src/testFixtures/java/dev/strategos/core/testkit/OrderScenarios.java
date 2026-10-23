package dev.strategos.core.testkit;

import static dev.strategos.core.testkit.Scenarios.FACTION_0;
import static dev.strategos.core.testkit.Scenarios.alive;
import static dev.strategos.core.testkit.Scenarios.check;
import static dev.strategos.core.testkit.Scenarios.firstUnit;
import static dev.strategos.core.testkit.Scenarios.resultOf;
import static dev.strategos.core.testkit.Scenarios.trace;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@link Scenarios}' order scenarios: the walking skeleton, a Move out of bounds and a Stop mid-walk (ORD-01). */
final class OrderScenarios {
    private OrderScenarios() {}

    static final class WalkingSkeleton implements Scenario {
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

    static final class OutOfBounds implements Scenario {
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

    static final class StopMidWalk implements Scenario {
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
}
