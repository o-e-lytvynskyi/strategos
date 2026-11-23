package dev.strategos.core.sim.territory;

/**
 * The naive territory (M9 spec §2.2): every tile against every source, owners by the highest sum, connectivity by a
 * recursive-free flood from each root over a fresh visited set. Slow and obvious; {@link TerritoryKernel} must equal
 * it.
 */
final class TerritoryReference {
    private TerritoryReference() {}

    static TerritoryGrid compute(TerritorySources s, int factions, int tilesX, int tilesZ) {
        TerritoryGrid g = new TerritoryGrid(tilesX, tilesZ);
        int[] owner = new int[tilesX * tilesZ];
        for (int z = 0; z < tilesZ; z++) {
            for (int x = 0; x < tilesX; x++) {
                long[] sum = new long[factions];
                for (int i = 0; i < s.size(); i++) {
                    int f = s.faction(i);
                    if (f < 0 || f >= factions) {
                        continue;
                    }
                    long dx = x * 16L + 8 - s.centreX2(i);
                    long dz = z * 16L + 8 - s.centreZ2(i);
                    double d = Math.floor(Math.sqrt((double) (dx * dx + dz * dz)));
                    long r = 2L * s.radius(i);
                    if (d < r) {
                        sum[f] += s.weight(i) * (r - (long) d) / r;
                    }
                }
                long best = 0;
                int o = -1;
                for (int f = 0; f < factions; f++) {
                    if (sum[f] > best) {
                        best = sum[f];
                        o = f;
                    }
                }
                for (int f = 0; f < factions; f++) {
                    if (f != o && o >= 0 && sum[f] == best) {
                        o = -1;
                        break;
                    }
                }
                owner[z * tilesX + x] = o;
            }
        }
        boolean[] connected = new boolean[owner.length];
        for (int i = 0; i < s.size(); i++) {
            if (!s.root(i)) {
                continue;
            }
            int x = Math.floorDiv(s.centreX2(i), 16);
            int z = Math.floorDiv(s.centreZ2(i), 16);
            if (x < 0 || z < 0 || x >= tilesX || z >= tilesZ || owner[z * tilesX + x] != s.faction(i)) {
                continue;
            }
            java.util.ArrayDeque<Integer> open = new java.util.ArrayDeque<>();
            open.add(z * tilesX + x);
            while (!open.isEmpty()) {
                int t = open.poll();
                if (connected[t] || owner[t] != s.faction(i)) {
                    continue;
                }
                connected[t] = true;
                int tx = t % tilesX;
                int tz = t / tilesX;
                if (tx > 0) open.add(t - 1);
                if (tx < tilesX - 1) open.add(t + 1);
                if (tz > 0) open.add(t - tilesX);
                if (tz < tilesZ - 1) open.add(t + tilesX);
            }
        }
        for (int t = 0; t < owner.length; t++) {
            g.set(
                    t,
                    owner[t] < 0
                            ? TerritoryGrid.NEUTRAL
                            : (byte) (owner[t] | (connected[t] ? TerritoryGrid.CONNECTED : 0)));
        }
        return g;
    }
}
