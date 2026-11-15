package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;

/**
 * Where a wave musters (AI-04): at least {@code minDistance} blocks (the script's {@code camp}, 40 by default) from the base's centre on the way to the enemy, on ground
 * the base's own units can walk to. A flood over the standing ground round the line (a step of a block at most, no
 * footprint, no water) from the civic centre's edge gives the cells the soldiers reach; the camp is the first of them
 * on the line from {@code want} blocks out, else the reached cell nearest the point at {@code want}. {@code want} is at
 * most three quarters of the way to the enemy.
 */
final class CampSite {
    /** How far beside and beyond the line the flood covers. */
    private static final int MARGIN = 28;

    private CampSite() {}

    /** The camp's column {x, z}. */
    static int[] find(AiView view, AiBudget budget, Base base, int muster, int minDistance) {
        double dx = base.enemyX() - base.x();
        double dz = base.enemyZ() - base.z();
        double d = Math.max(1, DetMath.length(dx, dz));
        if (minDistance == 0) { // the script's own muster point on the line, as before the camp
            return new int[] {(int) Math.floor(base.x() + dx / d * muster), (int) Math.floor(base.z() + dz / d * muster)
            };
        }
        double want = Math.min(Math.max(muster, minDistance), d * 0.75);
        double ux = dx / d;
        double uz = dz / d;
        int sizeX = view.bounds().sizeX();
        int sizeZ = view.bounds().sizeZ();
        int idealX = clamp((int) Math.ceil(base.x() + ux * want), 3, sizeX - 4);
        int idealZ = clamp((int) Math.ceil(base.z() + uz * want), 3, sizeZ - 4);
        int x0 = clamp((int) Math.min(base.x(), idealX + ux * 24) - MARGIN, 0, sizeX - 1);
        int x1 = clamp((int) Math.max(base.x(), idealX + ux * 24) + MARGIN, 0, sizeX - 1);
        int z0 = clamp((int) Math.min(base.z(), idealZ + uz * 24) - MARGIN, 0, sizeZ - 1);
        int z1 = clamp((int) Math.max(base.z(), idealZ + uz * 24) + MARGIN, 0, sizeZ - 1);
        int w = x1 - x0 + 1;
        int h = z1 - z0 + 1;
        budget.spend(Math.min(200, w * h / 50));
        double[] floor = new double[w * h];
        boolean[] open = new boolean[w * h];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int k = (x - x0) * h + (z - z0);
                floor[k] = view.floorAt(x, z);
                open[k] = !Double.isNaN(floor[k]) && !view.footprintAt(x, z);
            }
        }
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int head = 0;
        int tail = 0;
        Footprint f = base.centre().footprint();
        for (int x = f.anchorX() - 1; x <= f.anchorX() + f.spanX(); x++) {
            for (int z = f.anchorZ() - 1; z <= f.anchorZ() + f.spanZ(); z++) {
                if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                    int k = (x - x0) * h + (z - z0);
                    if (open[k] && !seen[k]) {
                        seen[k] = true;
                        queue[tail++] = k;
                    }
                }
            }
        }
        while (head < tail) {
            int k = queue[head++];
            int x = k / h;
            int z = k % h;
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int nz = z + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                    continue;
                }
                int n = nx * h + nz;
                if (open[n] && !seen[n] && Math.abs(floor[n] - floor[k]) <= 1.0) {
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
        }
        for (double r = want; r <= want + 24; r += 2) { // the line first
            int x = (int) Math.floor(base.x() + ux * r);
            int z = (int) Math.floor(base.z() + uz * r);
            if (x >= x0 + 3
                    && x <= x1 - 3
                    && z >= z0 + 3
                    && z <= z1 - 3
                    && seen[(x - x0) * h + (z - z0)]
                    && DetMath.length(x - base.x(), z - base.z()) >= want) {
                return new int[] {x, z};
            }
        }
        int bestX = idealX;
        int bestZ = idealZ;
        double best = Double.POSITIVE_INFINITY;
        for (int x = x0; x <= x1; x++) { // else the reached cell nearest the ideal point
            for (int z = z0; z <= z1; z++) {
                if (seen[(x - x0) * h + (z - z0)]) {
                    double e = DetMath.length(x - idealX, z - idealZ);
                    if (e < best) {
                        best = e;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }
        }
        return new int[] {bestX, bestZ};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
