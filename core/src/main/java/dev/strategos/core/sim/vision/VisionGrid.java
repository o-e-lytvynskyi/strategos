package dev.strategos.core.sim.vision;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One faction's vision: per 4-block cell the number of its viewers that see it, and the cells it ever saw. A stamp sets
 * the explored bit of every cell it covers, so the explored set never needs a pass over the grid; a bit never clears.
 */
@CoreApi
public final class VisionGrid {
    private final int cellsX;
    private final int cellsZ;
    private final short[] counts;
    private final long[] explored;
    private final DiscOffsets offsets = new DiscOffsets();

    public VisionGrid(int sizeXBlocks, int sizeZBlocks) {
        this.cellsX = (sizeXBlocks + DiscOffsets.CELL - 1) / DiscOffsets.CELL;
        this.cellsZ = (sizeZBlocks + DiscOffsets.CELL - 1) / DiscOffsets.CELL;
        this.counts = new short[cellsX * cellsZ];
        this.explored = new long[(counts.length + 63) / 64];
    }

    /** The cell of a match-local coordinate. */
    public static int cellOf(double worldCoord) {
        return (int) Math.floor(worldCoord / DiscOffsets.CELL);
    }

    public int cellsX() {
        return cellsX;
    }

    public int cellsZ() {
        return cellsZ;
    }

    public void stamp(int cellX, int cellZ, int radiusCells) {
        add(cellX, cellZ, radiusCells, 1);
    }

    public void unstamp(int cellX, int cellZ, int radiusCells) {
        add(cellX, cellZ, radiusCells, -1);
    }

    private void add(int cx, int cz, int r, int delta) {
        int[] o = offsets.offsets(r);
        int rr = Math.min(r, DiscOffsets.MAX_RADIUS);
        if (cx - rr >= 0 && cx + rr < cellsX && cz - rr >= 0 && cz + rr < cellsZ) {
            for (int k = 0; k < o.length; k += 2) {
                int i = (cz + o[k + 1]) * cellsX + cx + o[k];
                counts[i] += (short) delta;
                if (delta > 0) {
                    explored[i >> 6] |= 1L << (i & 63);
                }
            }
            return;
        }
        for (int k = 0; k < o.length; k += 2) {
            int x = cx + o[k];
            int z = cz + o[k + 1];
            if (x >= 0 && x < cellsX && z >= 0 && z < cellsZ) {
                int i = z * cellsX + x;
                counts[i] += (short) delta;
                if (delta > 0) {
                    explored[i >> 6] |= 1L << (i & 63);
                }
            }
        }
    }

    public int count(int cellX, int cellZ) {
        if (cellX < 0 || cellX >= cellsX || cellZ < 0 || cellZ >= cellsZ) {
            return 0;
        }
        return counts[cellZ * cellsX + cellX];
    }

    public boolean isExplored(int cellX, int cellZ) {
        if (cellX < 0 || cellX >= cellsX || cellZ < 0 || cellZ >= cellsZ) {
            return false;
        }
        int i = cellZ * cellsX + cellX;
        return (explored[i >> 6] & (1L << (i & 63))) != 0;
    }

    /** The raw explored word at index {@code w}; for the delta tracker. */
    long exploredWord(int w) {
        return explored[w];
    }

    int exploredWords() {
        return explored.length;
    }

    /** Whether no cell was ever explored. */
    public boolean pristine() {
        for (long w : explored) {
            if (w != 0) {
                return false;
            }
        }
        return true;
    }

    public List<Long> saveExplored() {
        List<Long> out = new ArrayList<>(explored.length);
        for (long w : explored) {
            out.add(w);
        }
        return out;
    }

    public void restoreExplored(List<Long> data) {
        if (data.size() != explored.length) {
            throw new IllegalArgumentException(
                    "a saved explored set of " + data.size() + " words for a grid of " + explored.length);
        }
        for (int i = 0; i < explored.length; i++) {
            explored[i] = data.get(i);
        }
    }

    public long mixExplored(long h) {
        for (long w : explored) {
            h = Hasher.mix(h, w);
        }
        return h;
    }

    /** Every cell seen and explored, for a match that reveals its map. */
    public void revealAll() {
        Arrays.fill(counts, (short) 1);
        for (int i = 0; i < counts.length; i++) {
            explored[i >> 6] |= 1L << (i & 63);
        }
    }

    /** Every cell explored, for MATCH-07's explored-at-start option; counts stay as vision stamps them. */
    public void exploreAll() {
        Arrays.fill(explored, -1L);
    }

    public void clearCounts() {
        Arrays.fill(counts, (short) 0);
    }
}
