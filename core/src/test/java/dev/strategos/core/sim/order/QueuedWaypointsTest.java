package dev.strategos.core.sim.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ORD-02.2 (spec §7.2): on terrain, three waypoints queued with {@code QUEUE} are visited in order, each order finishing
 * as the unit lands on it, and the unit goes idle on the last; a Stop clears the queue, so the unit stands where it was
 * and never walks on to the rest.
 */
class QueuedWaypointsTest {
    private static final Vec2d[] WAYPOINTS = {new Vec2d(20.5, 4.5), new Vec2d(20.5, 20.5), new Vec2d(4.5, 20.5)};

    @Test
    void threeQueuedWaypointsAreVisitedInOrder() {
        SimHarness h = harness();
        int unit = h.spawn(new FactionSlot(0), new Vec2d(4.5, 4.5), 1, 0)[0];
        List<Integer> finished = new ArrayList<>();
        KernelAccess.state(h.core()).bus().subscribe(OrderFinished.class, m -> finished.add(m.handle()));
        for (Vec2d w : WAYPOINTS) {
            h.submit(PlayerSlot.ADMIN, new Move(UnitSet.of(unit), w, QueueMode.QUEUE, 0));
        }
        List<Integer> visited = new ArrayList<>();
        for (int t = 0; t < 400 && !(idle(h, unit) && visited.size() == WAYPOINTS.length); t++) {
            h.step();
            UnitView u = unit(h, unit);
            for (int k = 0; k < WAYPOINTS.length; k++) {
                if (u.x() == WAYPOINTS[k].x() && u.z() == WAYPOINTS[k].z() && !visited.contains(k)) {
                    visited.add(k);
                }
            }
        }

        assertThat(visited).containsExactly(0, 1, 2);
        assertThat(finished).containsExactly(unit, unit, unit);
        assertThat(idle(h, unit)).isTrue();
        assertThat(KernelAccess.state(h.core()).orders().size(Handle.index(unit)))
                .isZero();
    }

    @Test
    void stopClearsTheQueue() {
        SimHarness h = harness();
        int unit = h.spawn(new FactionSlot(0), new Vec2d(4.5, 4.5), 1, 0)[0];
        for (Vec2d w : WAYPOINTS) {
            h.submit(PlayerSlot.ADMIN, new Move(UnitSet.of(unit), w, QueueMode.QUEUE, 0));
        }
        h.runTicks(10);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.orders().size(Handle.index(unit))).isEqualTo(3);

        h.submit(PlayerSlot.ADMIN, new Stop(UnitSet.of(unit)));
        h.step();
        UnitView stopped = unit(h, unit);
        h.runTicks(100);

        assertThat(s.orders().size(Handle.index(unit))).isZero();
        assertThat(idle(h, unit)).isTrue();
        assertThat(unit(h, unit).x()).as("it stays where it stopped").isEqualTo(stopped.x());
        assertThat(unit(h, unit).z()).isEqualTo(stopped.z());
    }

    private static SimHarness harness() {
        SimHarness h = SimHarness.builder().size(32, 32).build();
        h.publishAll(VoxelFixtures.flat(32, 32, 8, 1));
        return h;
    }

    private static boolean idle(SimHarness h, int unit) {
        return unit(h, unit).state() == UnitState.IDLE;
    }

    private static UnitView unit(SimHarness h, int unit) {
        UnitView view = h.queries().unit(unit);
        assertThat(view).isNotNull();
        return view;
    }
}
