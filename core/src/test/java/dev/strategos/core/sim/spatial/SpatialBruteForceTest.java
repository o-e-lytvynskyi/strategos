package dev.strategos.core.sim.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * CORE-08.1 (spec §7.2): over 1,000 seeds of random spaces, units (some outside the space, some in reused slots, some
 * dead), filters and points, every query equals brute force: the units in a radius, their count, the nearest unit, and
 * the nearest node and dropsite of the static index after adds, moves and removals.
 */
class SpatialBruteForceTest {
    @Test
    void unitQueriesEqualBruteForce() {
        for (int seed = 1; seed <= 1_000; seed++) {
            Random r = new Random(seed);
            int sizeX = 16 + r.nextInt(240);
            int sizeZ = 16 + r.nextInt(240);
            SpatialWorld w = SpatialWorld.random(r, sizeX, sizeZ, r.nextInt(200));
            UnitGrid grid = w.grid(sizeX, sizeZ);
            for (int q = 0; q < 20; q++) {
                double x = SpatialWorld.coordinate(r, sizeX);
                double z = SpatialWorld.coordinate(r, sizeZ);
                double radius = r.nextInt(4) == 0 ? r.nextInt(3) : r.nextDouble() * 40;
                UnitFilter filter = filter(r);
                List<Integer> expected = within(w, x, z, radius, filter);
                List<Integer> found = new ArrayList<>();

                grid.forEachInRadius(x, z, radius, filter, found::add);

                assertThat(found).as("seed %d query %d", seed, q).isEqualTo(expected);
                assertThat(grid.countWithin(x, z, radius, filter)).isEqualTo(expected.size());
                assertThat(grid.nearest(x, z, filter))
                        .as("seed %d nearest %d", seed, q)
                        .isEqualTo(nearest(w, x, z, filter));
            }
        }
    }

    @Test
    void staticQueriesEqualBruteForce() {
        for (int seed = 1; seed <= 1_000; seed++) {
            Random r = new Random(seed * 31L);
            int sizeX = 16 + r.nextInt(240);
            int sizeZ = 16 + r.nextInt(240);
            StaticIndex index = new StaticIndex(sizeX, sizeZ);
            int n = r.nextInt(60);
            int[] kinds = new int[n];
            double[] xs = new double[n];
            double[] zs = new double[n];
            int[] factions = new int[n];
            long[] resources = new long[n];
            boolean[] present = new boolean[n];
            for (int step = 0; step < n * 2; step++) {
                int e = r.nextInt(Math.max(1, n));
                if (n == 0) {
                    break;
                }
                if (present[e] && r.nextInt(3) == 0) {
                    index.remove(e);
                    present[e] = false;
                    continue;
                }
                kinds[e] = 1 + r.nextInt(7);
                xs[e] = SpatialWorld.coordinate(r, sizeX);
                zs[e] = SpatialWorld.coordinate(r, sizeZ);
                factions[e] = r.nextInt(3) - 1;
                resources[e] = r.nextInt(16);
                index.add(e, kinds[e], xs[e], zs[e], factions[e], resources[e]);
                present[e] = true;
            }
            for (int q = 0; q < 20; q++) {
                double x = SpatialWorld.coordinate(r, sizeX);
                double z = SpatialWorld.coordinate(r, sizeZ);
                int resource = r.nextInt(4);
                int faction = r.nextInt(2);
                int node = -1;
                int dropsite = -1;
                double nodeD = Double.POSITIVE_INFINITY;
                double dropD = Double.POSITIVE_INFINITY;
                for (int e = 0; e < n; e++) {
                    if (!present[e] || (resources[e] & (1L << resource)) == 0) {
                        continue;
                    }
                    double d = (xs[e] - x) * (xs[e] - x) + (zs[e] - z) * (zs[e] - z);
                    if ((kinds[e] & StaticIndex.NODE) != 0 && d < nodeD) {
                        node = e;
                        nodeD = d;
                    }
                    if ((kinds[e] & StaticIndex.DROPSITE) != 0 && factions[e] == faction && d < dropD) {
                        dropsite = e;
                        dropD = d;
                    }
                }

                assertThat(index.nearestNode(resource, x, z))
                        .as("seed %d node %d", seed, q)
                        .isEqualTo(node);
                assertThat(index.nearestDropsite(faction, resource, x, z))
                        .as("seed %d dropsite %d", seed, q)
                        .isEqualTo(dropsite);
            }
        }
    }

    private static UnitFilter filter(Random r) {
        UnitFilter base = switch (r.nextInt(4)) {
            case 0 -> UnitFilter.ANY;
            case 1 -> UnitFilter.of(r.nextInt(4));
            case 2 -> UnitFilter.allBut(r.nextInt(4));
            default -> new UnitFilter(r.nextInt(16), 0);
        };
        return r.nextInt(3) == 0 ? base.withClasses(1L << r.nextInt(4)) : base;
    }

    private static List<Integer> within(SpatialWorld w, double x, double z, double radius, UnitFilter filter) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < w.entities.highWater(); i++) {
            if (passes(w, i, filter)) {
                double dx = w.motion.x(i) - x;
                double dz = w.motion.z(i) - z;
                if (dx * dx + dz * dz <= radius * radius) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    private static int nearest(SpatialWorld w, double x, double z, UnitFilter filter) {
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < w.entities.highWater(); i++) {
            if (passes(w, i, filter)) {
                double dx = w.motion.x(i) - x;
                double dz = w.motion.z(i) - z;
                double d = dx * dx + dz * dz;
                if (d < bestD) {
                    best = i;
                    bestD = d;
                }
            }
        }
        return best;
    }

    private static boolean passes(SpatialWorld w, int i, UnitFilter filter) {
        return w.entities.aliveAt(i) && filter.passes(w.identity.faction(i), w.classes(i));
    }
}
