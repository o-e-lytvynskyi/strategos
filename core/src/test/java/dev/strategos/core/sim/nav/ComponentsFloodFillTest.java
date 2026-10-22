package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PATH-04.1 (spec §5.5): on 500 random grids, sizes not always a multiple of a page, what a unit can reach by the
 * components (its own and those downstream by drops) is exactly what a flood fill along the grid's links reaches, from
 * random starts; after some pages are rebuilt and republished the updated components still agree, and equal those of
 * a grid built afresh. A wall splits a floor, a gap joins it, and a two-block ledge is a one-way drop.
 */
class ComponentsFloodFillTest {

    @Test
    void componentsEqualAFloodFillOn500RandomGrids() {
        for (int seed = 1; seed <= 500; seed++) {
            Random r = new Random(seed);
            int sizeX = 20 + r.nextInt(30);
            int sizeZ = 20 + r.nextInt(30);
            VoxelWorld world = randomWorld(r, sizeX, sizeZ);
            SurfaceGrid grid = build(world, sizeX, sizeZ);

            assertMatchesFloodFill(grid, r, "seed " + seed);

            List<SurfacePage> rebuilt = new ArrayList<>();
            PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
            for (int i = 0; i < 2; i++) {
                int px = r.nextInt(grid.pagesX());
                int pz = r.nextInt(grid.pagesZ());
                int wallX = px * 16 + r.nextInt(16);
                world.fill(wallX, 0, pz * 16, wallX, 8, pz * 16 + 15, VoxelFixtures.SOLID); // a wall across a page
                PageCoord coord = new PageCoord(px, pz);
                rebuilt.add(builder.build(world.slice(coord), NavOverlay.EMPTY, coord));
            }
            SurfaceGrid updated = grid.publishAll(rebuilt);

            assertMatchesFloodFill(updated, r, "seed " + seed + " after republishing");
            SurfaceGrid fresh = build(world, sizeX, sizeZ);
            String what = "seed " + seed + ": an update equals a fresh build";
            forEachNode(
                    updated,
                    node -> assertThat(updated.component(node)).as(what).isEqualTo(fresh.component(node)));
        }
    }

    @Test
    void aWallSplitsAndAGapJoins() {
        VoxelWorld w = VoxelFixtures.flat(48, 32, 16, 1);
        w.fill(20, 1, 0, 20, 4, 31, VoxelFixtures.SOLID);
        SurfaceGrid split = build(w, 48, 32);

        assertThat(split.component(split.node(5, 5, 0))).isNotEqualTo(split.component(split.node(40, 5, 0)));

        w.fill(20, 1, 30, 20, 4, 31, VoxelFixtures.AIR);
        PageCoord page = new PageCoord(1, 1);
        SurfaceGrid joined =
                split.publish(new PageBuilder(MobilityClass.INFANTRY).build(w.slice(page), NavOverlay.EMPTY, page));

        assertThat(joined.component(joined.node(5, 5, 0))).isEqualTo(joined.component(joined.node(40, 5, 0)));
        assertThat(split.component(split.node(5, 5, 0)))
                .as("the old grid keeps its components")
                .isNotEqualTo(split.component(split.node(40, 5, 0)));
    }

    @Test
    void aLedgeIsAOneWayDrop() {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 16, 1);
        w.fill(10, 1, 10, 20, 2, 20, VoxelFixtures.SOLID); // a plateau two blocks up, no way up
        SurfaceGrid grid = w.grid();
        int top = grid.node(15, 15, 0);
        int ground = grid.node(3, 3, 0);

        assertThat(grid.component(top)).isNotEqualTo(grid.component(ground));
        assertThat(grid.reachableFrom(top).get(grid.component(ground)))
                .as("down the ledge")
                .isTrue();
        assertThat(grid.reachableFrom(ground).get(grid.component(top)))
                .as("up the ledge")
                .isFalse();
    }

    @Test
    void aThreeHighWallWithAWalkableTopStillSplits() {
        VoxelWorld w = VoxelFixtures.flat(48, 32, 16, 1);
        w.fill(20, 1, 0, 20, 3, 31, VoxelFixtures.SOLID); // its top is a surface, three blocks over both sides
        SurfaceGrid grid = w.grid();
        int west = grid.node(5, 5, 0);
        int east = grid.node(40, 5, 0);

        assertThat(grid.reachableFrom(west).get(grid.component(east))).isFalse();
        assertThat(grid.reachableFrom(grid.node(20, 5, 0)).get(grid.component(east)))
                .as("from the wall's top both sides are a drop away")
                .isTrue();
    }

    /** Ground 1 to 3 high with deep water, walls and lava; partial last pages when the size is not a multiple of 16. */
    private static VoxelWorld randomWorld(Random r, int sizeX, int sizeZ) {
        int worldX = (sizeX + 15) / 16 * 16;
        int worldZ = (sizeZ + 15) / 16 * 16;
        VoxelWorld w = new VoxelWorld(worldX, 16, worldZ);
        for (int x = 0; x < worldX; x++) {
            for (int z = 0; z < worldZ; z++) {
                int ground = 1 + r.nextInt(3);
                w.fill(x, 0, z, x, ground - 1, z, VoxelFixtures.SOLID);
                int roll = r.nextInt(100);
                if (roll < 12) {
                    w.fill(x, ground, z, x, ground + 1, z, VoxelFixtures.WATER);
                } else if (roll < 22) {
                    w.fill(x, ground, z, x, ground + 4, z, VoxelFixtures.SOLID);
                } else if (roll < 25) {
                    w.set(x, ground, z, VoxelFixtures.LAVA);
                }
            }
        }
        return w;
    }

    private static SurfaceGrid build(VoxelWorld world, int sizeX, int sizeZ) {
        SurfaceGrid grid = SurfaceGrid.empty(sizeX, sizeZ, MobilityClass.INFANTRY);
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        List<SurfacePage> pages = new ArrayList<>();
        for (int pz = 0; pz < grid.pagesZ(); pz++) {
            for (int px = 0; px < grid.pagesX(); px++) {
                PageCoord coord = new PageCoord(px, pz);
                pages.add(builder.build(world.slice(coord), NavOverlay.EMPTY, coord));
            }
        }
        return grid.publishAll(pages);
    }

    /** From eight random starts, the nodes a flood fill along the links reaches are those the components say. */
    private static void assertMatchesFloodFill(SurfaceGrid grid, Random r, String what) {
        List<Integer> nodes = new ArrayList<>();
        forEachNode(grid, nodes::add);
        if (nodes.isEmpty()) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            int start = nodes.get(r.nextInt(nodes.size()));
            Set<Integer> filled = floodFill(grid, start);
            BitSet reachable = grid.reachableFrom(start);
            for (int node : nodes) {
                assertThat(reachable.get(grid.component(node)))
                        .as("%s: node %d from node %d", what, node, start)
                        .isEqualTo(filled.contains(node));
            }
        }
    }

    /** Every node reached from {@code start} along the grid's links, which run one way down drops. */
    static Set<Integer> floodFill(SurfaceGrid grid, int start) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> open = new ArrayDeque<>();
        seen.add(start);
        open.add(start);
        while (!open.isEmpty()) {
            int node = open.poll();
            int x = grid.nodeX(node);
            int z = grid.nodeZ(node);
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int nl = grid.neighbour(x, z, SurfaceGrid.nodeLayer(node), dir);
                if (nl >= 0) {
                    int next = grid.node(x + Dir.dx(dir), z + Dir.dz(dir), nl);
                    if (seen.add(next)) {
                        open.add(next);
                    }
                }
            }
        }
        return seen;
    }

    private static void forEachNode(SurfaceGrid grid, NodeAction action) {
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                for (int layer = 0; layer < grid.count(x, z); layer++) {
                    action.at(grid.node(x, z, layer));
                }
            }
        }
    }

    @FunctionalInterface
    private interface NodeAction {
        void at(int node);
    }
}
