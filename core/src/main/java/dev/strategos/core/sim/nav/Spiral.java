package dev.strategos.core.sim.nav;

import java.util.Arrays;

/**
 * The columns round a column in the fixed order every search of the surfaces nearby takes (slot snapping, the stuck
 * ladder): by distance, then Z, then X.
 */
public final class Spiral {
    private Spiral() {}

    /** The (dx, dz) pairs, flattened, of the columns within {@code range} of (0, 0), itself first. */
    public static int[] of(int range) {
        int side = 2 * range + 1;
        int[] cells = new int[2 * side * side];
        int n = 0;
        for (int dz = -range; dz <= range; dz++) {
            for (int dx = -range; dx <= range; dx++) {
                if (dx * dx + dz * dz <= range * range) {
                    cells[2 * n] = dx;
                    cells[2 * n + 1] = dz;
                    n++;
                }
            }
        }
        for (int a = 1; a < n; a++) { // a stable insertion sort by distance: laid out by Z, then X, already
            for (int b = a; b > 0 && farther(cells, b - 1, b); b--) {
                int x = cells[2 * b];
                int z = cells[2 * b + 1];
                cells[2 * b] = cells[2 * b - 2];
                cells[2 * b + 1] = cells[2 * b - 1];
                cells[2 * b - 2] = x;
                cells[2 * b - 1] = z;
            }
        }
        return Arrays.copyOf(cells, 2 * n);
    }

    private static boolean farther(int[] cells, int a, int b) {
        int x = cells[2 * a];
        int z = cells[2 * a + 1];
        int u = cells[2 * b];
        int w = cells[2 * b + 1];
        return x * x + z * z > u * u + w * w;
    }
}
