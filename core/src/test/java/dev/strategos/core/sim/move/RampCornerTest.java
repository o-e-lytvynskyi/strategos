package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * QA-12: the one-block ramp corner of the crowd soak's loop (proving ground, (93, 185)), as a small grid. A pit (ground
 * 1) has a plateau (ground 5) to its east and a stair beside it whose steps rise a block every two columns, so from
 * the pit only the columns 2 and 3 climb it; the path turns at (3.5, 5.5) and its straight leg on, a block and a half
 * to the east of the corner, crosses a two-block cliff. A walker pushed east of the corner, as a crowd does, slid
 * along the cliff and went back to the corner, again and again. It now steers to the corner's centre while its leg is
 * not clear, and climbs.
 */
class RampCornerTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    private static VoxelWorld world() {
        StringBuilder map = new StringBuilder();
        for (int z = 0; z < 24; z++) {
            for (int x = 0; x < 24; x++) {
                int ground;
                if (z <= 5) {
                    ground = x <= 9 ? 1 : 5; // the pit, and the cliff of the plateau east of it
                } else {
                    ground = 1 + Math.min(4, x / 2); // the stair: a block every two columns
                }
                map.append(ground);
            }
            map.append('\n');
        }
        return VoxelFixtures.heightmap(8, map.toString());
    }

    @Test
    void aWalkerEastOfTheCornerWhoseLegIsNotClearGoesBackToItAndClimbs() {
        SimHarness h = SimHarness.builder().size(24, 24).build();
        h.publishAll(world());
        int unit = h.spawn(F0, new Vec2d(9.5, 5.5), 1, 0)[0];
        int i = Handle.index(unit);
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(12.5, 22.5)));
        h.step();
        // the path as it was found, the corner passed, and the walker 0.8 block east of it, in the pit
        s.paths()
                .follow(
                        i,
                        new PathStore.Waypoints(
                                new double[] {3.5, 12.5}, new double[] {5.5, 22.5}, new int[] {16, 80}, false),
                        PathStatus.OK);
        s.paths().advance(i);
        s.motion().set(i, 4.3, s.motion().y(i), 5.7, s.motion().yaw(i));
        long ticks = 0;
        for (; ticks < 30 && s.motion().z(i) < 7; ticks++) {
            h.step();
        }
        assertThat(s.motion().z(i))
                .as("up the ramp within %d ticks, at x %.2f", ticks, s.motion().x(i))
                .isGreaterThanOrEqualTo(7);
    }
}
