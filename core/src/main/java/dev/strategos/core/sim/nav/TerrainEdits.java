package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The core's own changes to its terrain (M3 spec §1.6 row 5): per page, each voxel the core set itself (a log it
 * removed, a footprint it cleared) with its new class and the tick it decided it, in order, a later edit of a voxel
 * winning; a voxel is a {@link #cell}, column and band level. A page is rebuilt from its last captured slice with its edits laid over it; a capture taken after an edit's
 * write reached the block writer already holds it, so {@link #dropUpTo} forgets the edits up to the capture's sample
 * tick. Deterministic; plain arrays.
 */
@CoreApi
public final class TerrainEdits {
    private final int pages;
    private int[][] voxels;
    private short[][] classes;
    private long[][] ticks;
    private final int[] counts;

    public TerrainEdits(int pages) {
        this.pages = pages;
        this.voxels = new int[pages][];
        this.classes = new short[pages][];
        this.ticks = new long[pages][];
        this.counts = new int[pages];
    }

    /** An independent copy of the edits held now. */
    public TerrainEdits copy() {
        TerrainEdits copy = new TerrainEdits(pages);
        for (int page = 0; page < pages; page++) {
            if (voxels[page] != null) {
                copy.voxels[page] = voxels[page].clone();
                copy.classes[page] = classes[page].clone();
                copy.ticks[page] = ticks[page].clone();
                copy.counts[page] = counts[page];
            }
        }
        return copy;
    }

    /** The cell of column (x, z), 0–15 each, at band level {@code y}: what an edit names, whatever the slice's height. */
    public static int cell(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y >= VoxelSlice.MAX_HEIGHT) {
            throw new IllegalArgumentException("cell (" + x + ", " + y + ", " + z + ") outside a page");
        }
        return SurfacePage.column(x, z) * VoxelSlice.MAX_HEIGHT + y;
    }

    /** Cell {@code voxel} of page {@code page} ({@link #cell}) is {@code cls} from tick {@code tick} on. */
    public void add(int page, int voxel, short cls, long tick) {
        int n = counts[page];
        if (voxels[page] == null) {
            voxels[page] = new int[8];
            classes[page] = new short[8];
            ticks[page] = new long[8];
        } else if (n == voxels[page].length) {
            voxels[page] = Arrays.copyOf(voxels[page], n * 2);
            classes[page] = Arrays.copyOf(classes[page], n * 2);
            ticks[page] = Arrays.copyOf(ticks[page], n * 2);
        }
        voxels[page][n] = voxel;
        classes[page][n] = cls;
        ticks[page][n] = tick;
        counts[page] = n + 1;
    }

    /** Forgets page {@code page}'s edits of tick {@code tick} or before: a capture sampled then holds them. */
    public void dropUpTo(int page, long tick) {
        int n = counts[page];
        int kept = 0;
        for (int k = 0; k < n; k++) {
            if (ticks[page][k] > tick) {
                voxels[page][kept] = voxels[page][k];
                classes[page][kept] = classes[page][k];
                ticks[page][kept] = ticks[page][k];
                kept++;
            }
        }
        counts[page] = kept;
    }

    public int count(int page) {
        return counts[page];
    }

    /** The class the latest edit gave cell {@code cell} of page {@code page}, or -1 when none did. */
    public int classAt(int page, int cell) {
        for (int k = counts[page] - 1; k >= 0; k--) {
            if (voxels[page][k] == cell) {
                return classes[page][k] & 0xFFFF;
            }
        }
        return -1;
    }

    /** Whether any page holds an edit. */
    public boolean empty() {
        for (int c : counts) {
            if (c > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code slice} with page {@code page}'s edits laid over it, in order; the slice itself if there are none. A cell above
     * the slice's band is left out.
     */
    public VoxelSlice applyTo(int page, VoxelSlice slice) {
        int n = counts[page];
        if (n == 0) {
            return slice;
        }
        int[] indices = new int[n];
        short[] cls = new short[n];
        int k = 0;
        for (int e = 0; e < n; e++) {
            int column = voxels[page][e] / VoxelSlice.MAX_HEIGHT;
            int y = voxels[page][e] % VoxelSlice.MAX_HEIGHT;
            if (y < slice.height()) {
                indices[k] = column * slice.height() + y;
                cls[k] = classes[page][e];
                k++;
            }
        }
        return slice.edited(Arrays.copyOf(indices, k), Arrays.copyOf(cls, k));
    }

    /** Mixes every edit, page by page, into {@code h}. */
    public long mix(long h) {
        long x = h;
        for (int p = 0; p < pages; p++) {
            for (int k = 0; k < counts[p]; k++) {
                x = Hasher.mix(x, p);
                x = Hasher.mix(x, voxels[p][k]);
                x = Hasher.mix(x, classes[p][k]);
                x = Hasher.mix(x, ticks[p][k]);
            }
        }
        return x;
    }

    /** Every edit as four numbers, page, voxel, class and tick, pages ascending: a snapshot's form. */
    public List<Long> save() {
        List<Long> out = new ArrayList<>();
        for (int p = 0; p < pages; p++) {
            for (int k = 0; k < counts[p]; k++) {
                out.add((long) p);
                out.add((long) voxels[p][k]);
                out.add((long) classes[p][k]);
                out.add(ticks[p][k]);
            }
        }
        return out;
    }

    /** Adds the edits of {@link #save}'s form. */
    public void restore(List<Long> saved) {
        if (saved.size() % 4 != 0) {
            throw new IllegalArgumentException("saved terrain edits come in fours, not " + saved.size() + " numbers");
        }
        for (int k = 0; k < saved.size(); k += 4) {
            int page = Math.toIntExact(saved.get(k));
            if (page < 0 || page >= pages) {
                throw new IllegalArgumentException("a saved terrain edit of page " + page + " of " + pages);
            }
            add(page, Math.toIntExact(saved.get(k + 1)), (short) (long) saved.get(k + 2), saved.get(k + 3));
        }
    }
}
