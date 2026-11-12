package dev.strategos.core.sim.vision;

import dev.strategos.core.protocol.VisionDelta;
import dev.strategos.core.shared.CoreApi;

/**
 * Tracks one faction's visible and explored bitmaps and produces deltas for the {@code vision_delta} payload (FOW-07).
 * The first call is always a full frame; subsequent calls return only the cells that changed since the last send.
 */
@CoreApi
public final class VisionDeltaTracker {
    private final int cellsX;
    private final int cellsZ;
    private long[] lastVisible;
    private long[] lastExplored;
    private boolean first = true;

    public VisionDeltaTracker(int cellsX, int cellsZ) {
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        int words = (cellsX * cellsZ + 63) / 64;
        this.lastVisible = new long[words];
        this.lastExplored = new long[words];
    }

    /** Produces a delta (or a full frame the first time) from the grid's current state. */
    public VisionDelta delta(int matchId, long tick, VisionGrid grid) {
        int total = cellsX * cellsZ;
        int words = (total + 63) / 64;
        if (first) {
            first = false;
            int[] visible = collectVisible(grid, total);
            int[] explored = collectExplored(grid, words);
            snapshotVisible(grid, total);
            snapshotExplored(grid, words);
            return new VisionDelta(matchId, tick, cellsX, cellsZ, true, visible, explored);
        }
        int[] visibleFlips = collectVisibleFlips(grid, total, words);
        int[] newExplored = collectNewExplored(grid, words);
        snapshotVisible(grid, total);
        snapshotExplored(grid, words);
        return new VisionDelta(matchId, tick, cellsX, cellsZ, false, visibleFlips, newExplored);
    }

    /** Forces the next call to produce a full frame (after a restore or join). */
    public void reset() {
        first = true;
        java.util.Arrays.fill(lastVisible, 0);
        java.util.Arrays.fill(lastExplored, 0);
    }

    private int[] collectVisible(VisionGrid grid, int total) {
        int count = 0;
        for (int i = 0; i < total; i++) {
            if (grid.count(i % cellsX, i / cellsX) > 0) {
                count++;
            }
        }
        int[] out = new int[count];
        int k = 0;
        for (int i = 0; i < total; i++) {
            if (grid.count(i % cellsX, i / cellsX) > 0) {
                out[k++] = i;
            }
        }
        return out;
    }

    private int[] collectExplored(VisionGrid grid, int words) {
        int count = 0;
        for (int w = 0; w < words; w++) {
            count += Long.bitCount(grid.exploredWord(w));
        }
        int[] out = new int[count];
        int k = 0;
        for (int w = 0; w < words; w++) {
            long bits = grid.exploredWord(w);
            while (bits != 0) {
                int bit = Long.numberOfTrailingZeros(bits);
                out[k++] = w * 64 + bit;
                bits &= bits - 1;
            }
        }
        return out;
    }

    private int[] collectVisibleFlips(VisionGrid grid, int total, int words) {
        long[] current = visibleBitmap(grid, total, words);
        int count = 0;
        for (int w = 0; w < words; w++) {
            count += Long.bitCount(current[w] ^ lastVisible[w]);
        }
        int[] out = new int[count];
        int k = 0;
        for (int w = 0; w < words; w++) {
            long diff = current[w] ^ lastVisible[w];
            while (diff != 0) {
                int bit = Long.numberOfTrailingZeros(diff);
                out[k++] = w * 64 + bit;
                diff &= diff - 1;
            }
        }
        return out;
    }

    private int[] collectNewExplored(VisionGrid grid, int words) {
        int count = 0;
        for (int w = 0; w < words; w++) {
            count += Long.bitCount(grid.exploredWord(w) & ~lastExplored[w]);
        }
        int[] out = new int[count];
        int k = 0;
        for (int w = 0; w < words; w++) {
            long fresh = grid.exploredWord(w) & ~lastExplored[w];
            while (fresh != 0) {
                int bit = Long.numberOfTrailingZeros(fresh);
                out[k++] = w * 64 + bit;
                fresh &= fresh - 1;
            }
        }
        return out;
    }

    private void snapshotVisible(VisionGrid grid, int total) {
        java.util.Arrays.fill(lastVisible, 0);
        for (int i = 0; i < total; i++) {
            if (grid.count(i % cellsX, i / cellsX) > 0) {
                lastVisible[i >> 6] |= 1L << (i & 63);
            }
        }
    }

    private void snapshotExplored(VisionGrid grid, int words) {
        for (int w = 0; w < words; w++) {
            lastExplored[w] = grid.exploredWord(w);
        }
    }

    private long[] visibleBitmap(VisionGrid grid, int total, int words) {
        long[] bitmap = new long[words];
        for (int i = 0; i < total; i++) {
            if (grid.count(i % cellsX, i / cellsX) > 0) {
                bitmap[i >> 6] |= 1L << (i & 63);
            }
        }
        return bitmap;
    }
}
