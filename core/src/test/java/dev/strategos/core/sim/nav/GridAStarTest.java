package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PATH-03.1 and .4 (spec §5.6): A* finds the cheapest path under every cost rule, equal to a plain Dijkstra over the
 * same links, and a search that reaches its cap returns {@code PARTIAL} toward the expanded node nearest the goal.
 */
class GridAStarTest {
    private static final double EPSILON = 1e-9;
    private final GridAStar search = new GridAStar();

    @Test
    void flatGroundCostsItsDistance() {
        SurfaceGrid grid = VoxelFixtures.flat(32, 32, 16, 1).grid();
        int from = grid.node(2, 2, 0);

        GridAStar.Result straight = search.search(grid, from, 12, 2, 20_000);
        GridAStar.Result diagonal = search.search(grid, from, 12, 12, 20_000);
        GridAStar.Result here = search.search(grid, from, 2, 2, 20_000);

        assertThat(straight.status()).isEqualTo(PathStatus.OK);
        assertThat(straight.cost()).isCloseTo(10, within(EPSILON));
        assertThat(straight.nodes()).hasSize(11).startsWith(from).endsWith(grid.node(12, 2, 0));
        assertThat(diagonal.cost()).isCloseTo(10 * Math.sqrt(2), within(EPSILON));
        assertThat(here.status()).isEqualTo(PathStatus.OK);
        assertThat(here.nodes()).containsExactly(from);
        assertThat(here.cost()).isZero();
    }

    @Test
    void everyRuleAddsItsCost() {
        // A strip across the whole world at x = 8: every path from x 2 to x 14 along z 8 crosses it once, so twelve
        // steps cost 12 on plain ground, and the step onto the strip carries the rule.
        assertThat(crossStrip((w, z) -> w.set(8, 1, z, VoxelFixtures.WATER)))
                .as("shallow water x4")
                .isCloseTo(15, within(EPSILON));
        assertThat(crossStrip((w, z) -> w.set(8, 1, z, VoxelFixtures.DOOR)))
                .as("a door x1.5")
                .isCloseTo(12.5, within(EPSILON));
        assertThat(crossStrip((w, z) -> w.set(8, 0, z, VoxelFixtures.SOUL_SAND)))
                .as("slow ground x2")
                .isCloseTo(13, within(EPSILON));
        assertThat(crossStrip((w, z) -> w.set(8, 1, z, VoxelFixtures.SOLID)))
                .as("a rise of a block +0.5")
                .isCloseTo(12.5, within(EPSILON));
        assertThat(crossStrip((w, z) -> w.set(8, 1, z, VoxelFixtures.CARPET)))
                .as("a carpet's rise of 1/16")
                .isCloseTo(12, within(EPSILON));
        assertThat(crossStrip((w, z) -> {
                    if (z == 7) {
                        w.fill(8, 1, 7, 8, 3, 7, VoxelFixtures.SOLID); // a pillar beside (8, 8)
                    }
                }))
                .as("a cost code of 1 +0.1")
                .isCloseTo(12.1, within(EPSILON));
    }

    @Test
    void hazardsAndDeepWaterAreGoneAround() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        for (int z = 0; z < 15; z++) {
            w.set(8, 1, z, z % 2 == 0 ? VoxelFixtures.LAVA : VoxelFixtures.WATER)
                    .set(8, 2, z, VoxelFixtures.WATER);
        }
        SurfaceGrid grid = w.grid();

        GridAStar.Result r = search.search(grid, grid.node(2, 2, 0), 14, 2, 20_000);

        assertThat(r.status()).isEqualTo(PathStatus.OK);
        assertThat(r.nodes()).as("through the one dry column at z 15").contains(grid.node(8, 15, 0));
    }

    @Test
    void costsEqualAReferenceDijkstraOnRandomTerrain() {
        for (int seed = 1; seed <= 12; seed++) {
            SurfaceGrid grid = randomTerrain(seed);
            Random pick = new Random(seed * 31L);
            for (int pair = 0; pair < 8; pair++) {
                int from = anyNode(grid, pick);
                int gx = pick.nextInt(48);
                int gz = pick.nextInt(48);

                GridAStar.Result found = search.search(grid, from, gx, gz, 20_000);
                double reference = dijkstra(grid, from, gx, gz);

                if (Double.isInfinite(reference)) {
                    assertThat(found.status()).as("seed %d pair %d", seed, pair).isEqualTo(PathStatus.NO_PATH);
                    continue;
                }
                assertThat(found.status()).as("seed %d pair %d", seed, pair).isEqualTo(PathStatus.OK);
                assertThat(found.cost()).as("seed %d pair %d", seed, pair).isCloseTo(reference, within(EPSILON));
                assertThat(walkedCost(grid, found.nodes()))
                        .as("the path's own steps")
                        .isCloseTo(found.cost(), within(EPSILON));
            }
        }
    }

    @Test
    void capReturnsPartialTowardTheNearestExpandedNode() {
        VoxelWorld w = VoxelFixtures.flat(256, 256, 16, 1);
        for (int x = 99; x <= 101; x++) {
            for (int z = 99; z <= 101; z++) {
                if (x != 100 || z != 100) {
                    w.fill(x, 1, z, x, 6, z, VoxelFixtures.SOLID); // a sealed cell around the goal
                }
            }
        }
        SurfaceGrid grid = w.grid();

        GridAStar.Result r = search.search(grid, grid.node(10, 100, 0), 100, 100, 20_000);

        assertThat(r.status()).isEqualTo(PathStatus.PARTIAL);
        assertThat(r.expansions()).isEqualTo(20_000);
        int end = r.nodes()[r.nodes().length - 1];
        assertThat(new int[] {grid.nodeX(end), grid.nodeZ(end)})
                .as("of the four columns two from the goal, the one nearest the start")
                .containsExactly(98, 100);
        assertThat(walkedCost(grid, r.nodes())).isCloseTo(r.cost(), within(EPSILON));
    }

    @Test
    void anExhaustedRegionGivesNoPathToItsNearestNode() {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 16, 1);
        w.fill(4, 1, 4, 12, 6, 4, VoxelFixtures.SOLID); // a closed room from (4, 4) to (12, 12)
        w.fill(4, 1, 12, 12, 6, 12, VoxelFixtures.SOLID);
        w.fill(4, 1, 4, 4, 6, 12, VoxelFixtures.SOLID);
        w.fill(12, 1, 4, 12, 6, 12, VoxelFixtures.SOLID);
        SurfaceGrid grid = w.grid();

        GridAStar.Result r = search.search(grid, grid.node(6, 8, 0), 25, 8, 20_000);
        GridAStar.Result stuck = search.search(grid, grid.node(11, 8, 0), 25, 8, 20_000);

        assertThat(r.status()).isEqualTo(PathStatus.NO_PATH);
        assertThat(r.expansions()).as("the room's 49 columns").isEqualTo(49);
        int end = r.nodes()[r.nodes().length - 1];
        assertThat(new int[] {grid.nodeX(end), grid.nodeZ(end)}).containsExactly(11, 8);
        assertThat(stuck.status()).isEqualTo(PathStatus.NO_PATH);
        assertThat(stuck.nodes()).as("already as near as the room gets").isEmpty();
    }

    @Test
    void scratchIsReusedAcrossSearches() {
        SurfaceGrid grid = randomTerrain(5);
        int from = anyNode(grid, new Random(9));

        double first = search.search(grid, from, 40, 40, 20_000).cost();
        search.search(grid, from, 3, 44, 20_000);
        GridAStar fresh = new GridAStar();

        assertThat(search.search(grid, from, 40, 40, 20_000).cost()).isEqualTo(first);
        assertThat(fresh.search(grid, from, 40, 40, 20_000).nodes())
                .containsExactly(search.search(grid, from, 40, 40, 20_000).nodes());
    }

    /** The cost from (2, 8) to (14, 8) on flat ground with {@code strip} applied to every z of x = 8. */
    private double crossStrip(Strip strip) {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        for (int z = 0; z < 16; z++) {
            strip.apply(w, z);
        }
        SurfaceGrid grid = w.grid();
        GridAStar.Result r = search.search(grid, grid.node(2, 8, 0), 14, 8, 20_000);
        assertThat(r.status()).isEqualTo(PathStatus.OK);
        assertThat(r.cost()).isCloseTo(dijkstra(grid, grid.node(2, 8, 0), 14, 8), within(EPSILON));
        return r.cost();
    }

    @FunctionalInterface
    private interface Strip {
        void apply(VoxelWorld w, int z);
    }

    /** 48 x 48 columns of ground 1 to 4 high, with shallow and deep water, doors, soul sand and walls. */
    static SurfaceGrid randomTerrain(long seed) {
        Random r = new Random(seed);
        VoxelWorld w = new VoxelWorld(48, 16, 48);
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                int ground = 1 + r.nextInt(4);
                w.fill(x, 0, z, x, ground - 1, z, VoxelFixtures.SOLID);
                int roll = r.nextInt(100);
                if (roll < 8) {
                    w.set(x, ground, z, VoxelFixtures.WATER);
                } else if (roll < 11) {
                    w.set(x, ground, z, VoxelFixtures.WATER).set(x, ground + 1, z, VoxelFixtures.WATER);
                } else if (roll < 14) {
                    w.set(x, ground, z, VoxelFixtures.DOOR);
                } else if (roll < 17) {
                    w.set(x, ground - 1, z, VoxelFixtures.SOUL_SAND);
                } else if (roll < 22) {
                    w.fill(x, ground, z, x, ground + 5, z, VoxelFixtures.SOLID);
                }
            }
        }
        return w.grid();
    }

    static int anyNode(SurfaceGrid grid, Random r) {
        while (true) {
            int x = r.nextInt(48);
            int z = r.nextInt(48);
            if (grid.count(x, z) > 0 && Surface.passable(grid.surface(x, z, 0))) {
                return grid.node(x, z, 0);
            }
        }
    }

    /** The plain reference: Dijkstra over the grid's links with the same step costs; infinity when unreachable. */
    static double dijkstra(SurfaceGrid grid, int from, int gx, int gz) {
        Map<Integer, Double> dist = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        dist.put(from, 0.0);
        open.add(new double[] {0, from});
        while (!open.isEmpty()) {
            double[] top = open.poll();
            int node = (int) top[1];
            if (top[0] > dist.get(node)) {
                continue;
            }
            int x = grid.nodeX(node);
            int z = grid.nodeZ(node);
            if (x == gx && z == gz) {
                return top[0];
            }
            int layer = SurfaceGrid.nodeLayer(node);
            int here = grid.surface(x, z, layer);
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int nl = grid.neighbour(x, z, layer, dir);
                if (nl < 0) {
                    continue;
                }
                int nx = x + Dir.dx(dir);
                int nz = z + Dir.dz(dir);
                int next = grid.node(nx, nz, nl);
                double d = top[0] + GridAStar.step(here, grid.surface(nx, nz, nl), dir);
                if (d < dist.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    dist.put(next, d);
                    open.add(new double[] {d, next});
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** The sum of the steps along {@code nodes}, each of which must follow a link. */
    static double walkedCost(SurfaceGrid grid, int[] nodes) {
        double cost = 0;
        for (int i = 1; i < nodes.length; i++) {
            int a = nodes[i - 1];
            int b = nodes[i];
            int x = grid.nodeX(a);
            int z = grid.nodeZ(a);
            int dir = direction(grid.nodeX(b) - x, grid.nodeZ(b) - z);
            assertThat(grid.node(grid.nodeX(b), grid.nodeZ(b), grid.neighbour(x, z, SurfaceGrid.nodeLayer(a), dir)))
                    .as("step %d follows a link", i)
                    .isEqualTo(b);
            cost += GridAStar.step(
                    grid.surface(x, z, SurfaceGrid.nodeLayer(a)),
                    grid.surface(grid.nodeX(b), grid.nodeZ(b), SurfaceGrid.nodeLayer(b)),
                    dir);
        }
        return cost;
    }

    private static int direction(int dx, int dz) {
        for (int dir = 0; dir < Dir.COUNT; dir++) {
            if (Dir.dx(dir) == dx && Dir.dz(dir) == dz) {
                return dir;
            }
        }
        throw new IllegalArgumentException("not a neighbour: " + dx + ", " + dz);
    }
}
