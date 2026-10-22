package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * PATH-03.1 (spec §5.6): string pulling keeps only the waypoints a path needs, each in line of walk of the one before,
 * ends exactly at the goal, and keeps the first 64 of a longer path.
 */
class StringPullerTest {
    private final GridAStar search = new GridAStar();

    @Test
    void openGroundNeedsOnlyTheGoal() {
        SurfaceGrid grid = VoxelFixtures.flat(48, 48, 16, 1).grid();

        PathStore.Waypoints w = pull(grid, grid.node(2, 2, 0), 40.3, 30.7);

        assertThat(w.count()).isOne();
        assertThat(w.x(0)).isEqualTo(40.3);
        assertThat(w.z(0)).isEqualTo(30.7);
        assertThat(w.floor(0)).isEqualTo(16);
        assertThat(w.truncated()).isFalse();
    }

    @Test
    void aWallLeavesCornerWaypointsInLineOfWalk() {
        VoxelWorld w = VoxelFixtures.flat(48, 48, 16, 1);
        w.fill(20, 1, 0, 20, 4, 39, VoxelFixtures.SOLID); // a wall with a gap at z 40..47
        w.fill(30, 1, 8, 30, 4, 47, VoxelFixtures.SOLID); // and another with its gap at z 0..7
        SurfaceGrid grid = w.grid();
        int from = grid.node(5, 20, 0);

        PathStore.Waypoints path = pull(grid, from, 44.5, 20.5);

        assertThat(path.count()).as("round two walls").isBetween(3, 6);
        assertThat(path.x(path.count() - 1)).isEqualTo(44.5);
        assertInLineOfWalk(grid, from, path);
    }

    @Test
    void theStartColumnIsTheGoal() {
        SurfaceGrid grid = VoxelFixtures.flat(16, 16, 16, 1).grid();

        PathStore.Waypoints w = pull(grid, grid.node(3, 3, 0), 3.9, 3.1);

        assertThat(w.count()).isOne();
        assertThat(w.x(0)).isEqualTo(3.9);
        assertThat(w.z(0)).isEqualTo(3.1);
    }

    @Test
    void aLongPathKeepsItsFirst64Waypoints() {
        VoxelWorld w = VoxelFixtures.flat(160, 32, 16, 1);
        for (int x = 2; x < 158; x += 2) { // a serpentine: walls with gaps at alternate ends
            boolean gapNorth = (x / 2) % 2 == 0;
            w.fill(x, 1, gapNorth ? 2 : 0, x, 4, gapNorth ? 31 : 29, VoxelFixtures.SOLID);
        }
        SurfaceGrid grid = w.grid();
        int from = grid.node(0, 16, 0);

        GridAStar.Result found = search.search(grid, grid.node(0, 16, 0), 159, 16, 20_000);
        PathStore.Waypoints path = StringPuller.pull(grid, found.nodes(), true, 159.5, 16.5);

        assertThat(found.status()).isEqualTo(PathStatus.OK);
        assertThat(path.count()).isEqualTo(PathStore.Waypoints.MAX);
        assertThat(path.truncated()).isTrue();
        assertInLineOfWalk(grid, from, path);
    }

    private PathStore.Waypoints pull(SurfaceGrid grid, int from, double gx, double gz) {
        GridAStar.Result found = search.search(grid, from, (int) Math.floor(gx), (int) Math.floor(gz), 20_000);
        assertThat(found.status()).isEqualTo(PathStatus.OK);
        return StringPuller.pull(grid, found.nodes(), true, gx, gz);
    }

    /** Each waypoint is reached by a straight walk from the column of the one before (the start's first). */
    private static void assertInLineOfWalk(SurfaceGrid grid, int from, PathStore.Waypoints path) {
        int node = from;
        double x = grid.nodeX(from) + 0.5;
        double z = grid.nodeZ(from) + 0.5;
        for (int i = 0; i < path.count(); i++) {
            int next = LineOfWalk.walk(grid, node, x, z, path.x(i), path.z(i));
            assertThat(next).as("waypoint %d in line of walk", i).isNotNegative();
            assertThat(Surface.floor(grid.surface(grid.nodeX(next), grid.nodeZ(next), SurfaceGrid.nodeLayer(next))))
                    .as("waypoint %d's floor", i)
                    .isEqualTo(path.floor(i));
            node = next;
            x = path.x(i);
            z = path.z(i);
        }
    }
}
