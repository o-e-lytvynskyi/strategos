package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/** PATH-03 (spec §5.6): a straight walk follows the grid's links column by column, and fails where one is missing. */
class LineOfWalkTest {

    @Test
    void walksAcrossOpenGroundAndPageEdges() {
        SurfaceGrid grid = VoxelFixtures.flat(48, 48, 16, 1).grid();
        int from = grid.node(2, 3, 0);

        assertThat(LineOfWalk.walk(grid, from, 2.5, 3.5, 40.2, 31.7)).isEqualTo(grid.node(40, 31, 0));
        assertThat(LineOfWalk.walk(grid, from, 2.5, 3.5, 2.9, 3.1))
                .as("within the start column")
                .isEqualTo(from);
        assertThat(LineOfWalk.between(grid, from, grid.node(30, 3, 0))).isTrue();
    }

    @Test
    void aWallOrAMissingFloorStopsIt() {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 16, 1);
        w.fill(10, 1, 0, 10, 3, 31, VoxelFixtures.SOLID); // a wall 3 high along x = 10
        SurfaceGrid grid = w.grid();
        int from = grid.node(5, 5, 0);

        assertThat(LineOfWalk.walk(grid, from, 5.5, 5.5, 20.5, 5.5)).isEqualTo(-1);
        assertThat(LineOfWalk.walk(grid, from, 5.5, 5.5, 5.5, 25.5))
                .as("along the wall")
                .isNotNegative();
        assertThat(LineOfWalk.walk(grid, from, 5.5, 5.5, -3.5, 5.5))
                .as("off the grid")
                .isEqualTo(-1);
    }

    @Test
    void stepsUpOneBlockAndDropsThreeButNoMore() {
        SurfaceGrid grid = VoxelFixtures.heightmap(16, """
                1234321
                1111111
                1155111
                """).grid();

        assertThat(LineOfWalk.walk(grid, grid.node(0, 0, 0), 0.5, 0.5, 6.5, 0.5))
                .as("a staircase up and down")
                .isEqualTo(grid.node(6, 0, 0));
        assertThat(LineOfWalk.walk(grid, grid.node(1, 2, 0), 1.5, 2.5, 2.5, 2.5))
                .as("a rise of four blocks")
                .isEqualTo(-1);
        assertThat(LineOfWalk.walk(grid, grid.node(3, 2, 0), 3.5, 2.5, 4.5, 2.5))
                .as("a drop of four blocks")
                .isEqualTo(-1);
    }

    @Test
    void cornersNeedBothSides() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(5, 1, 6, 5, 2, 6, VoxelFixtures.SOLID); // a pillar at (5, 6)
        SurfaceGrid grid = w.grid();

        assertThat(LineOfWalk.walk(grid, grid.node(2, 6, 0), 2.5, 6.5, 3.5, 5.5))
                .as("a diagonal step with both sides open")
                .isEqualTo(grid.node(3, 5, 0));
        assertThat(LineOfWalk.walk(grid, grid.node(4, 6, 0), 4.5, 6.5, 5.5, 5.5))
                .as("the same step with the pillar on its east side")
                .isEqualTo(-1);
        assertThat(LineOfWalk.walk(grid, grid.node(4, 6, 0), 4.5, 6.5, 5.5, 5.2))
                .as("passing the corner on its north side instead")
                .isEqualTo(grid.node(5, 5, 0));
    }

    @Test
    void shallowWaterIsWalkedAndDeepWaterIsNot() {
        SurfaceGrid grid = VoxelFixtures.heightmap(16, """
                ..~~..
                ..==..
                """).grid();

        assertThat(LineOfWalk.walk(grid, grid.node(0, 0, 0), 0.5, 0.5, 5.5, 0.5))
                .isEqualTo(grid.node(5, 0, 0));
        assertThat(LineOfWalk.walk(grid, grid.node(0, 1, 0), 0.5, 1.5, 5.5, 1.5))
                .isEqualTo(-1);
    }

    @Test
    void endsOnTheSurfaceTheLinksReach() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(4, 4, 0, 8, 4, 15, VoxelFixtures.SOLID); // a bridge deck at y 4 over the road
        w.fill(3, 1, 0, 3, 3, 15, VoxelFixtures.SOLID); // a ramp: a solid block at x 3 up to the deck
        w.fill(2, 1, 0, 2, 2, 15, VoxelFixtures.SOLID);
        w.fill(1, 1, 0, 1, 1, 15, VoxelFixtures.SOLID);
        SurfaceGrid grid = w.grid();
        assertThat(grid.count(6, 5)).as("a deck over a road").isEqualTo(2);

        int over = LineOfWalk.walk(grid, grid.node(0, 5, 0), 0.5, 5.5, 6.5, 5.5);
        int under = LineOfWalk.walk(grid, grid.node(6, 1, 1), 6.5, 1.5, 6.5, 5.5);

        assertThat(SurfaceGrid.nodeLayer(over)).as("up the ramp onto the deck").isZero();
        assertThat(SurfaceGrid.nodeLayer(under)).as("along the road below").isEqualTo(1);
    }
}
