package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/** The grid of pages: links across page edges, copy-on-write publication, node numbers. */
class SurfaceGridTest {

    @Test
    void linksCrossPageEdgesByTheSameRule() {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 16, 1);
        w.fill(16, 1, 20, 16, 4, 20, VoxelFixtures.SOLID);

        SurfaceGrid grid = w.grid();

        assertThat(grid.neighbour(15, 5, 0, Dir.E)).as("into the next page").isZero();
        assertThat(grid.neighbour(16, 5, 0, Dir.W)).isZero();
        assertThat(grid.neighbour(15, 15, 0, Dir.SE))
                .as("a diagonal into the corner page")
                .isZero();
        assertThat(grid.neighbour(15, 20, 0, Dir.E)).as("a wall on the edge").isEqualTo(-1);
        assertThat(grid.neighbour(15, 19, 0, Dir.SE))
                .as("no corner cutting across the edge")
                .isEqualTo(-1);
        assertThat(grid.neighbour(31, 5, 0, Dir.E)).as("outside the grid").isEqualTo(-1);
    }

    @Test
    void publishingLeavesTheOldGridAlone() {
        SurfaceGrid before = VoxelFixtures.flat(32, 16, 16, 1).grid();
        VoxelWorld changed = VoxelFixtures.flat(32, 16, 16, 1);
        changed.set(20, 1, 3, VoxelFixtures.SOLID);
        PageCoord page = new PageCoord(1, 0);
        SurfacePage rebuilt =
                new PageBuilder(MobilityClass.INFANTRY).build(changed.slice(page), NavOverlay.EMPTY, page);

        SurfaceGrid after = before.publish(rebuilt);

        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(Surface.floor(after.surface(20, 3, 0))).isEqualTo(32);
        assertThat(Surface.floor(before.surface(20, 3, 0)))
                .as("a job's snapshot keeps the old page")
                .isEqualTo(16);
    }

    @Test
    void nodesNumberSurfacesAcrossPages() {
        SurfaceGrid grid = VoxelFixtures.flat(48, 32, 16, 1).grid();

        int node = grid.node(37, 21, 0);

        assertThat(grid.nodeX(node)).isEqualTo(37);
        assertThat(grid.nodeZ(node)).isEqualTo(21);
        assertThat(SurfaceGrid.nodeLayer(node)).isZero();
        assertThat(grid.bytes()).as("6 flat pages").isLessThan(6 * 2_200L);
    }
}
