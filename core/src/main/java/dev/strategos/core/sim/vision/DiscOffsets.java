package dev.strategos.core.sim.vision;

/** Filled-disc offset tables for stamping a vision radius onto a grid, one per radius in cells. */
public final class DiscOffsets {
    /** Blocks per vision cell. */
    public static final int CELL = 4;

    /** The largest vision range, 64 blocks, in cells. */
    public static final int MAX_RADIUS = 16;

    private final int[][] tables = precompute();

    /** The disc of {@code radiusInCells} (capped at {@link #MAX_RADIUS}) as alternating dx, dz. */
    public int[] offsets(int radiusInCells) {
        return tables[Math.max(0, Math.min(radiusInCells, MAX_RADIUS))];
    }

    private static int[][] precompute() {
        int[][] tables = new int[MAX_RADIUS + 1][];
        for (int r = 0; r <= MAX_RADIUS; r++) {
            int n = 0;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dz * dz <= r * r) {
                        n++;
                    }
                }
            }
            int[] t = new int[2 * n];
            int k = 0;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dz * dz <= r * r) {
                        t[k++] = dx;
                        t[k++] = dz;
                    }
                }
            }
            tables[r] = t;
        }
        return tables;
    }
}
