package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PATH-04.2 (spec §5.5): an unreachable goal resolves to the nearest surface the unit can reach, inspecting fewer than
 * 200 columns (1 % of the A* cap); a reachable goal stays as it is.
 */
class MakeGoalReachableTest {

    @Test
    void aGoalInsideAClosedBoxMovesToTheNearestOutsideColumn() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 16, 1);
        w.fill(30, 1, 30, 40, 5, 30, VoxelFixtures.SOLID);
        w.fill(30, 1, 40, 40, 5, 40, VoxelFixtures.SOLID);
        w.fill(30, 1, 30, 30, 5, 40, VoxelFixtures.SOLID);
        w.fill(40, 1, 30, 40, 5, 40, VoxelFixtures.SOLID);
        SurfaceGrid grid = w.grid();
        BitSet outside = grid.reachableFrom(grid.node(5, 5, 0));

        PathJob.Goal goal = PathJob.makeGoalReachable(grid, outside, 34.2, 35.5);

        assertThat(new int[] {goal.columnX(), goal.columnZ()}).containsExactly(29, 35);
        assertThat(goal.x()).isEqualTo(29.5);
        assertThat(goal.inspected()).isLessThan(200);
        assertNearest(grid, outside, 34.2, 35.5, goal);
    }

    @Test
    void aGoalAcrossDeepWaterFromAnIslandStaysOnTheIsland() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 16, 1);
        w.fill(0, 1, 0, 63, 2, 63, VoxelFixtures.WATER); // a lake two deep everywhere,
        w.fill(10, 1, 10, 17, 2, 17, VoxelFixtures.SOLID); // an island of 8 x 8 two blocks up
        SurfaceGrid grid = w.grid();
        BitSet island = grid.reachableFrom(grid.node(12, 12, 0));

        PathJob.Goal goal = PathJob.makeGoalReachable(grid, island, 50.5, 14.25);

        assertThat(new int[] {goal.columnX(), goal.columnZ()}).containsExactly(17, 14);
        assertThat(goal.inspected()).isLessThan(200);
        assertNearest(grid, island, 50.5, 14.25, goal);
    }

    @Test
    void aReachableGoalStaysPut() {
        SurfaceGrid grid = VoxelFixtures.flat(32, 32, 16, 1).grid();

        PathJob.Goal goal = PathJob.makeGoalReachable(grid, grid.reachableFrom(grid.node(1, 1, 0)), 20.3, 7.9);

        assertThat(goal.x()).isEqualTo(20.3);
        assertThat(goal.z()).isEqualTo(7.9);
        assertThat(goal.inspected()).isZero();
    }

    @Test
    void theResultIsTheNearestOnRandomTerrain() {
        for (int seed = 1; seed <= 200; seed++) {
            SurfaceGrid grid = GridAStarTest.randomTerrain(seed);
            Random r = new Random(seed * 7L);
            int start = GridAStarTest.anyNode(grid, r);
            BitSet component = grid.reachableFrom(start);
            double gx = r.nextInt(48 * 16) / 16.0;
            double gz = r.nextInt(48 * 16) / 16.0;

            PathJob.Goal goal = PathJob.makeGoalReachable(grid, component, gx, gz);

            assertNearest(grid, component, gx, gz, goal);
        }
    }

    /** The goal stays when its column holds the component; otherwise no holding column is nearer than the result. */
    private static void assertNearest(SurfaceGrid grid, BitSet component, double gx, double gz, PathJob.Goal goal) {
        int cx = (int) Math.floor(gx);
        int cz = (int) Math.floor(gz);
        if (holds(grid, cx, cz, component)) {
            assertThat(goal.x()).isEqualTo(gx);
            assertThat(goal.z()).isEqualTo(gz);
            return;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                if (holds(grid, x, z, component)) {
                    best = Math.min(best, Math.hypot(x + 0.5 - gx, z + 0.5 - gz));
                }
            }
        }
        assertThat(holds(grid, goal.columnX(), goal.columnZ(), component)).isTrue();
        assertThat(Math.hypot(goal.x() - gx, goal.z() - gz)).isCloseTo(best, within(1e-3));
    }

    private static boolean holds(SurfaceGrid grid, int x, int z, BitSet component) {
        for (int layer = 0; layer < grid.count(x, z); layer++) {
            if (component.get(grid.component(grid.node(x, z, layer)))) {
                return true;
            }
        }
        return false;
    }
}
