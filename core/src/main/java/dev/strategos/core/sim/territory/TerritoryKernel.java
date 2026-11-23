package dev.strategos.core.sim.territory;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/**
 * The territory recompute (M9 spec §2.2, TERR-01): each source is stamped into its faction's accumulator over its
 * bounding box only; each tile goes to the faction with the highest summed influence above zero (a tie, or none, is
 * neutral); then the tiles 4-connected through the same owner to a tile holding one of its roots are marked connected.
 * One kernel per thread: its scratch arrays are reused, so a recompute allocates nothing once warm.
 */
@CoreApi
@HotPath
public final class TerritoryKernel {
    private static final int TILE2 = 2 * TerritoryGrid.TILE; // a tile's side in half-blocks
    private static final int HALF_TILE2 = TerritoryGrid.TILE; // a tile centre's offset in half-blocks

    private long[] sums = new long[0];
    private int[] queue = new int[0];

    /** Recomputes {@code out} from {@code sources} for {@code factions} player factions. */
    public void compute(TerritorySources sources, int factions, TerritoryGrid out) {
        if (factions <= 0 || factions > TerritoryGrid.MAX_FACTIONS) {
            throw new IllegalArgumentException("factions: " + factions);
        }
        int tx = out.tilesX();
        int tz = out.tilesZ();
        int tiles = tx * tz;
        if (sums.length < tiles * factions) {
            sums = new long[tiles * factions];
        }
        if (queue.length < tiles) {
            queue = new int[tiles];
        }
        Arrays.fill(sums, 0, tiles * factions, 0L);
        for (int i = 0; i < sources.size(); i++) {
            int f = sources.faction(i);
            if (f < 0 || f >= factions) {
                continue;
            }
            stamp(sources, i, f * tiles, tx, tz);
        }
        for (int t = 0; t < tiles; t++) {
            long best = 0;
            int owner = -1;
            for (int f = 0; f < factions; f++) {
                long v = sums[f * tiles + t];
                if (v > best) {
                    best = v;
                    owner = f;
                } else if (v == best && v > 0) {
                    owner = -1; // a tie at the top is nobody's
                }
            }
            out.set(t, owner < 0 ? TerritoryGrid.NEUTRAL : (byte) owner);
        }
        for (int i = 0; i < sources.size(); i++) {
            if (sources.root(i)) {
                flood(sources, i, out, tx, tz);
            }
        }
    }

    private void stamp(TerritorySources s, int i, int base, int tx, int tz) {
        int cx = s.centreX2(i);
        int cz = s.centreZ2(i);
        int r2 = 2 * s.radius(i);
        int weight = s.weight(i);
        int x0 = Math.max(0, ceilDiv(cx - r2 - HALF_TILE2, TILE2));
        int x1 = Math.min(tx - 1, Math.floorDiv(cx + r2 - HALF_TILE2, TILE2));
        int z0 = Math.max(0, ceilDiv(cz - r2 - HALF_TILE2, TILE2));
        int z1 = Math.min(tz - 1, Math.floorDiv(cz + r2 - HALF_TILE2, TILE2));
        for (int z = z0; z <= z1; z++) {
            long dz = (long) z * TILE2 + HALF_TILE2 - cz;
            for (int x = x0; x <= x1; x++) {
                long dx = (long) x * TILE2 + HALF_TILE2 - cx;
                long v = TerritorySources.influence(weight, s.radius(i), dx * dx + dz * dz);
                if (v > 0) {
                    sums[base + z * tx + x] += v;
                }
            }
        }
    }

    private void flood(TerritorySources s, int i, TerritoryGrid out, int tx, int tz) {
        int f = s.faction(i);
        int x = Math.floorDiv(s.centreX2(i), TILE2);
        int z = Math.floorDiv(s.centreZ2(i), TILE2);
        if (x < 0 || z < 0 || x >= tx || z >= tz) {
            return;
        }
        int start = z * tx + x;
        if (!claim(out, start, f)) {
            return;
        }
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        while (head < tail) {
            int t = queue[head++];
            int cx = t % tx;
            int cz = t / tx;
            if (cx > 0 && claim(out, t - 1, f)) {
                queue[tail++] = t - 1;
            }
            if (cx < tx - 1 && claim(out, t + 1, f)) {
                queue[tail++] = t + 1;
            }
            if (cz > 0 && claim(out, t - tx, f)) {
                queue[tail++] = t - tx;
            }
            if (cz < tz - 1 && claim(out, t + tx, f)) {
                queue[tail++] = t + tx;
            }
        }
    }

    /** Marks tile {@code t} connected if {@code f} owns it and it is not marked yet; true when it was marked now. */
    private static boolean claim(TerritoryGrid out, int t, int f) {
        int v = out.value(t);
        if ((v & 0x80) != 0 || (v & TerritoryGrid.OWNER) != f || (v & TerritoryGrid.CONNECTED) != 0) {
            return false;
        }
        out.set(t, (byte) (v | TerritoryGrid.CONNECTED));
        return true;
    }

    private static int ceilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }
}
