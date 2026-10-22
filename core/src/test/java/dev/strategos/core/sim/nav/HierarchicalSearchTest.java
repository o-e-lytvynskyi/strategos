package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PATH-08.1 and .2 (spec T28): a path found in two levels, for a goal at least four pages away, costs at most 5 % more
 * than full A* on the test maps and walks along links all the way; the proving ground's 384-block crossing, and its
 * 512² version, expand at most 10 % of flat A*'s nodes; past the abstract cap the path is partial toward the goal.
 */
class HierarchicalSearchTest {
    private static final double EPSILON = 1e-9;

    private final GridAStar flat = new GridAStar();
    private final HierarchicalSearch hierarchical = new HierarchicalSearch();

    @Test
    void theCrossingCostsWithin5PercentAndExpandsUnder10Percent() {
        for (int size : new int[] {384, 512}) {
            SurfaceGrid grid = ProvingGround.generate(size).voxels().grid();
            int middle = size / 2;
            int from = grid.node(24, middle, 0);

            GridAStar.Result full = flat.search(grid, from, size - 24, middle, 1_000_000);
            GridAStar.Result two = search(grid, from, size - 24, middle);

            assertThat(full.status()).isEqualTo(PathStatus.OK);
            assertThat(two.cost()).as("%d²: cost", size).isLessThanOrEqualTo(full.cost() * 1.05);
            assertThat(two.expansions()).as("%d²: expansions", size).isLessThanOrEqualTo(full.expansions() / 10);
            assertThat(GridAStarTest.walkedCost(grid, two.nodes())).isCloseTo(two.cost(), within(EPSILON));
        }
    }

    @Test
    void pathsOnTheProvingGroundCostWithin5Percent() {
        SurfaceGrid grid = ProvingGround.generate().voxels().grid();
        Random r = new Random(8);
        int checked = 0;
        while (checked < 40) {
            int from = node(grid, r);
            int gx = r.nextInt(grid.sizeX());
            int gz = r.nextInt(grid.sizeZ());
            if (pages(grid, from, gx, gz) < PathJob.HIERARCHICAL_PAGES
                    || !holds(grid, gx, gz, grid.reachableFrom(from))) {
                continue;
            }
            GridAStar.Result full = flat.search(grid, from, gx, gz, 1_000_000);
            GridAStar.Result two = search(grid, from, gx, gz);

            assertThat(two.cost()).as("pair %d", checked).isLessThanOrEqualTo(full.cost() * 1.05 + EPSILON);
            assertThat(two.cost()).as("never below the optimum").isGreaterThanOrEqualTo(full.cost() - EPSILON);
            assertThat(GridAStarTest.walkedCost(grid, two.nodes())).isCloseTo(two.cost(), within(EPSILON));
            checked++;
        }
    }

    @Test
    void pathsOnRandomTerrainWalkAlongLinksToTheGoal() {
        for (int seed = 1; seed <= 30; seed++) {
            SurfaceGrid grid = GridAStarTest.randomTerrain(seed, 96);
            Random r = new Random(seed * 3L);
            for (int pair = 0; pair < 8; pair++) {
                int from = node(grid, r);
                int gx = r.nextInt(96);
                int gz = r.nextInt(96);
                BitSet reachable = grid.reachableFrom(from);
                if (pages(grid, from, gx, gz) < PathJob.HIERARCHICAL_PAGES || !holds(grid, gx, gz, reachable)) {
                    continue;
                }
                GridAStar.Result full = flat.search(grid, from, gx, gz, 1_000_000);
                GridAStar.Result two = hierarchical.search(grid, flat, from, gx, gz, reachable, 20_000);
                if (two == null) {
                    continue; // the portals miss a way only a flat search finds; PathJob falls back to it
                }
                int[] nodes = two.nodes();
                assertThat(nodes[0]).isEqualTo(from);
                assertThat(grid.nodeX(nodes[nodes.length - 1])).isEqualTo(gx);
                assertThat(grid.nodeZ(nodes[nodes.length - 1])).isEqualTo(gz);
                assertThat(GridAStarTest.walkedCost(grid, nodes)).isCloseTo(two.cost(), within(EPSILON));
                assertThat(two.cost()).isGreaterThanOrEqualTo(full.cost() - EPSILON);
            }
        }
    }

    @Test
    void aWallIsGoneRoundOnceItsPagesArePublished() {
        VoxelWorld w = VoxelFixtures.flat(80, 48, 16, 1);
        SurfaceGrid open = w.grid();
        w.fill(40, 1, 0, 40, 4, 39, VoxelFixtures.SOLID);
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        SurfaceGrid walled = open.publishAll(java.util.List.of(
                builder.build(w.slice(new PageCoord(2, 0)), NavOverlay.EMPTY, new PageCoord(2, 0)),
                builder.build(w.slice(new PageCoord(2, 1)), NavOverlay.EMPTY, new PageCoord(2, 1)),
                builder.build(w.slice(new PageCoord(2, 2)), NavOverlay.EMPTY, new PageCoord(2, 2))));
        int from = open.node(4, 20, 0);

        GridAStar.Result before = search(open, from, 75, 20);
        GridAStar.Result after = search(walled, from, 75, 20);

        assertThat(before.cost()).as("a straight line, nearly").isBetween(71.0, 71 * 1.05);
        boolean round = false;
        for (int node : after.nodes()) {
            assertThat(walled.nodeX(node) == 40 && walled.nodeZ(node) < 40)
                    .as("through the wall")
                    .isFalse();
            round |= walled.nodeZ(node) >= 40;
        }
        assertThat(round).as("round the wall's end").isTrue();
    }

    @Test
    void beyondTheAbstractCapThePathIsPartialTowardTheGoal() {
        SurfaceGrid grid = ProvingGround.generate(512).voxels().grid();
        int from = grid.node(510, 484, 0);

        GridAStar.Result r = hierarchical.search(grid, flat, from, 35, 269, grid.reachableFrom(from), 20_000);

        assertThat(r).isNotNull();
        assertThat(r.status()).isEqualTo(PathStatus.PARTIAL);
        int end = r.nodes()[r.nodes().length - 1];
        assertThat(GridAStar.octile(grid.nodeX(end), grid.nodeZ(end), 35, 269))
                .isLessThan(GridAStar.octile(510, 484, 35, 269) / 2);
        assertThat(GridAStarTest.walkedCost(grid, r.nodes())).isCloseTo(r.cost(), within(EPSILON));
    }

    private GridAStar.Result search(SurfaceGrid grid, int from, int gx, int gz) {
        GridAStar.Result result = hierarchical.search(grid, flat, from, gx, gz, grid.reachableFrom(from), 20_000);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(PathStatus.OK);
        return result;
    }

    private static int pages(SurfaceGrid grid, int from, int gx, int gz) {
        return Math.max(Math.abs((grid.nodeX(from) >> 4) - (gx >> 4)), Math.abs((grid.nodeZ(from) >> 4) - (gz >> 4)));
    }

    private static int node(SurfaceGrid grid, Random r) {
        while (true) {
            int x = r.nextInt(grid.sizeX());
            int z = r.nextInt(grid.sizeZ());
            if (grid.count(x, z) > 0 && Surface.passable(grid.surface(x, z, 0))) {
                return grid.node(x, z, 0);
            }
        }
    }

    private static boolean holds(SurfaceGrid grid, int x, int z, BitSet reachable) {
        for (int layer = 0; layer < grid.count(x, z); layer++) {
            if (reachable.get(grid.component(grid.node(x, z, layer)))) {
                return true;
            }
        }
        return false;
    }
}
