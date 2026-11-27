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
 *
 * <p>M10 T29: the crowd soak's give-up at (231, 303). A leg that passes a hair from a column corner is clear to the
 * line-of-walk check, which crosses the column it meets first; a step of the walk that crosses both column edges at
 * once, where both sides of the corner link but its diagonal does not (a rise the diagonal alone cannot take), was
 * refused and slid along one axis instead, into a pocket beside the leg, and the corner rule pulled it back a tick at a
 * time, never far enough. Such a step now walks the segment as the check does; a wall's corner stays refused.
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

    /**
     * The start column A (8, 8) at ground 1; south of it C (8, 7) at ground 2, and south-west D (7, 7) on the upper
     * ground 3: A to C and C to D climb a block each, the diagonal A to D two, so it has no link. West of A the pocket
     * B (7, 8) at ground 1, walled by ground 3 on its other sides: its only link is back to A.
     */
    private static VoxelWorld pocketWorld() {
        StringBuilder map = new StringBuilder();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int ground;
                if (x <= 7) {
                    ground = x == 7 && z == 8 ? 1 : 3; // the upper ground, and the pocket B in it
                } else {
                    ground = z <= 7 ? 2 : 1; // the column of C, and the start ground of A
                }
                map.append(ground);
            }
            map.append('\n');
        }
        return VoxelFixtures.heightmap(8, map.toString());
    }

    @Test
    void aStepAcrossACornerWithNoDiagonalLinkWalksThroughTheColumnItsLegCrossesFirst() {
        SimHarness h = SimHarness.builder().size(16, 16).build();
        h.publishAll(pocketWorld());
        int unit = h.spawn(F0, new Vec2d(10.5, 12.5), 1, 0)[0];
        int i = Handle.index(unit);
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(3.5, 2.5)));
        h.step();
        // the leg from A's centre to the target crosses C at x 8.08 and D at z 7.9; the walker stands on it, so that
        // its next step crosses both edges of A at once, z first
        s.paths()
                .follow(
                        i,
                        new PathStore.Waypoints(
                                new double[] {8.5, 3.5}, new double[] {8.5, 2.5}, new int[] {16, 48}, false),
                        PathStatus.OK);
        s.paths().advance(i);
        s.motion().set(i, 8.15, s.motion().y(i), 8.09, s.motion().yaw(i));
        h.step();
        // the case itself: one step crossed both of A's edges (a slower walker would cross one and pin nothing)
        assertThat(s.motion().x(i)).as("x after the first step").isLessThan(8);
        assertThat(s.motion().z(i)).as("z after the first step").isLessThan(8);
        long ticks = 1;
        boolean pocket = false;
        for (; ticks < 30 && s.motion().floor(i) != 48; ticks++) {
            h.step();
            pocket |= Math.floor(s.motion().x(i)) == 7 && Math.floor(s.motion().z(i)) == 8;
        }
        assertThat(pocket).as("never in the pocket").isFalse();
        assertThat(s.motion().floor(i))
                .as(
                        "on the upper ground within %d ticks, at (%.2f, %.2f)",
                        ticks, s.motion().x(i), s.motion().z(i))
                .isEqualTo(48);
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
