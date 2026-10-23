package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.LeaderUpdate;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * CAM-07's follow in L1: slot 0's commander walks east in first person, its position each tick an input as the server
 * sends it; six debug walkers following it keep their places behind it while it walks, each its own, and wait there
 * once it stops; a leader back in the RTS view (no input) leaves them where they are.
 */
class FollowTest {
    private static final PlayerSlot P0 = new PlayerSlot(0);

    @Test
    void followersKeepPlacesBehindAWalkingLeaderAndStopWhenItStops() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        h.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 16), 6, 2));
        int[] units = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        MatchState state = KernelAccess.state(h.core());
        double[] leader = {12, 16};
        float yaw = -90; // Minecraft's yaw for facing +X
        h.schedule(h.tick() + 1, new LeaderUpdate(0, (float) leader[0], (float) leader[1], yaw));
        h.submit(P0, Follow.replace(UnitSet.of(units)));
        double most = 0;
        for (int t = 0; t < 400; t++) {
            if (t < 300) {
                leader[0] = Math.min(80, leader[0] + 0.15); // walks east, then stands
            }
            h.schedule(h.tick() + 1, new LeaderUpdate(0, (float) leader[0], (float) leader[1], yaw));
            h.step();
            if (t > 60 && t < 300) {
                for (int u : units) {
                    double x = state.motion().x(Handle.index(u));
                    assertThat(x).as("behind a leader walking east").isLessThan(leader[0]);
                    most = Math.max(most, leader[0] - x);
                }
            }
        }
        assertThat(most).as("never left far behind").isLessThan(12);
        for (int u : units) {
            int i = Handle.index(u);
            assertThat(state.orders().kind(i)).isEqualTo(OrderKind.FOLLOW);
            assertThat(UnitAi.leaf(state.ai().code(i)))
                    .as("waiting at its place")
                    .isEqualTo(UnitAi.FOLLOW_WAITING);
            double behind = leader[0] - state.motion().x(i);
            assertThat(behind).isBetween(1.0, 8.0);
            assertThat(Math.abs(state.motion().z(i) - leader[1])).isLessThan(4);
            for (int v : units) {
                if (v != u) {
                    int j = Handle.index(v);
                    assertThat(DetMath.length(
                                    state.motion().x(i) - state.motion().x(j),
                                    state.motion().z(i) - state.motion().z(j)))
                            .as("each its own place")
                            .isGreaterThan(0.5);
                }
            }
        }
        double[] at = {state.motion().x(Handle.index(units[0])), state.motion().z(Handle.index(units[0]))};
        h.runTicks(60); // the leader steps back up into the RTS view: no input, no one led
        assertThat(state.motion().x(Handle.index(units[0]))).isEqualTo(at[0]);
        assertThat(state.motion().z(Handle.index(units[0]))).isEqualTo(at[1]);
    }

    @Test
    void onlyAPlayerLeads() {
        SimHarness h = SimHarness.builder().build();
        h.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 16), 2, 1));
        int[] units = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        h.submit(PlayerSlot.ADMIN, Follow.replace(UnitSet.of(units)));
        h.step();
        assertThat(KernelAccess.state(h.core()).orders().kind(Handle.index(units[0])))
                .isEqualTo(OrderKind.NONE);
    }
}
