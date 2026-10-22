package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.Arrays;

/**
 * The standable surfaces of one page's 256 columns, at most 4 per column, topmost first (04 §9.1); immutable, built by
 * {@link PageBuilder}. Columns are numbered {@code x * 16 + z}. Links in the packed surfaces cover neighbours inside
 * the page; {@link SurfaceGrid} decides links across page edges with the same rule.
 */
@CoreApi
public final class SurfacePage {
    public static final int COLUMNS = 256;
    public static final int MAX_LAYERS = 4;

    private final PageCoord coord;
    private final int[] start;
    private final int[] surfaces;
    private final int overflow;
    private final long hash;

    SurfacePage(PageCoord coord, int[] start, int[] surfaces, int overflow) {
        if (start.length != COLUMNS + 1 || start[COLUMNS] != surfaces.length) {
            throw new IllegalArgumentException("column offsets do not cover the surfaces");
        }
        this.coord = coord;
        this.start = start;
        this.surfaces = surfaces;
        this.overflow = overflow;
        long h = Hasher.start(0x9A9E);
        for (int s : start) {
            h = Hasher.mix(h, s);
        }
        for (int s : surfaces) {
            h = Hasher.mix(h, s);
        }
        this.hash = Hasher.finish(h);
    }

    /** A page with no surface at all. */
    public static SurfacePage empty(PageCoord coord) {
        return new SurfacePage(coord, new int[COLUMNS + 1], new int[0], 0);
    }

    public static int column(int x, int z) {
        return x * VoxelSlice.SIDE + z;
    }

    public PageCoord coord() {
        return coord;
    }

    public int count(int column) {
        return start[column + 1] - start[column];
    }

    /** The packed surface, see {@link Surface}. */
    public int surface(int column, int layer) {
        if (layer < 0 || layer >= count(column)) {
            throw new IndexOutOfBoundsException("layer " + layer + " of column " + column + " with " + count(column));
        }
        return surfaces[start[column] + layer];
    }

    public int surfaceCount() {
        return surfaces.length;
    }

    /** Surfaces dropped because their column already had {@link #MAX_LAYERS}; S8 counts them. */
    public int overflow() {
        return overflow;
    }

    /** The page's memory as stored: offsets and surfaces, in bytes, for S8 and budget G. */
    public int bytes() {
        return 16 + 4 * (start.length + surfaces.length);
    }

    /** Depends on the surfaces only, not on the coordinate: equal slices give equal hashes (PATH-02.3). */
    public long hash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SurfacePage other
                && other.coord.equals(coord)
                && Arrays.equals(other.start, start)
                && Arrays.equals(other.surfaces, surfaces)
                && other.overflow == overflow;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }
}
