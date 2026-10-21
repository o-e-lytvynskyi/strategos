package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BodyFrame;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class MovementTest {
    private final SimHarness h = SimHarness.builder().build();

    private int unitAt(double x, double z) {
        return h.spawn(new FactionSlot(0), new Vec2d(x, z), 1, 0.0)[0];
    }

    @Test
    void anAxisAlignedTwentyBlockMoveTakesExactlyEightySteps() {
        int unit = unitAt(4.5, 8.5);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(24.5, 8.5)));
        long applied = h.tick() + 1;
        for (int step = 1; step <= 79; step++) {
            h.step();
            UnitView u = h.queries().unit(unit);
            assertThat(u.state()).as("step %d", step).isEqualTo(UnitState.WALKING);
            assertThat(u.x()).as("step %d", step).isEqualTo(4.5 + 0.25 * step);
            assertThat(u.z()).isEqualTo(8.5);
        }
        h.step();
        UnitView arrived = h.queries().unit(unit);
        assertThat(arrived.state()).isEqualTo(UnitState.IDLE);
        assertThat(arrived.x()).isEqualTo(24.5);
        assertThat(arrived.z()).isEqualTo(8.5);
        assertThat(h.tick() - applied + 1).as("steps").isEqualTo(80);
    }

    @Test
    void aDiagonalMoveSnapsExactlyOntoTheTarget() {
        int unit = unitAt(4.5, 8.5);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(7.5, 12.5))); // 3-4-5: 5 blocks
        h.runTicks(19);
        assertThat(h.queries().unit(unit).state()).as("after 19 steps").isEqualTo(UnitState.WALKING);
        h.step(); // the 20th step: ⌈5 / 0.25⌉
        UnitView u = h.queries().unit(unit);
        assertThat(u.state()).isEqualTo(UnitState.IDLE);
        assertThat(u.x()).isEqualTo(7.5);
        assertThat(u.z()).isEqualTo(12.5);
    }

    @Test
    void aZeroLengthMoveArrivesOnTheTickItApplies() {
        int unit = unitAt(4.5, 8.5);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(4.5, 8.5)));
        TickOutputs out = h.step();
        assertThat(h.queries().unit(unit).state()).isEqualTo(UnitState.IDLE);
        assertThat(h.stateChanges()).hasSize(2);
        assertThat(out.bodies().count()).isEqualTo(1);
        assertThat(h.queries().unit(unit).yaw()).as("no heading to turn to").isZero();
    }

    @Test
    void yawFollowsMinecraftsConvention() {
        int east = unitAt(8.5, 8.5);
        int south = unitAt(8.5, 20.5);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(east), new Vec2d(20.5, 8.5)));
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(south), new Vec2d(8.5, 28.5)));
        h.runTicks(3);
        assertThat(h.queries().unit(east).yaw()).as("+X").isEqualTo(-90f);
        assertThat(h.queries().unit(south).yaw()).as("+Z").isEqualTo(0f);
    }

    @Test
    void theFrameHoldsOnlyMovedUnitsInHandleOrder() {
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(16, 16), 3, 4.0);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[2], units[0]), new Vec2d(28, 28)));
        BodyFrame frame = h.step().bodies();
        assertThat(frame.count()).isEqualTo(2);
        assertThat(frame.handle(0)).isEqualTo(units[0]);
        assertThat(frame.handle(1)).isEqualTo(units[2]);
        assertThat(frame.x(0)).isEqualTo(h.queries().unit(units[0]).x());
        assertThat(frame.pose(1)).isZero();
        assertThat(h.step().bodies().count()).isEqualTo(2);
    }
}
