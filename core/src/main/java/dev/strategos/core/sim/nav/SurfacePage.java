package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.Arrays;

/**
 * The standable surfaces of one page's 256 columns, at most 4 per column, topmost first (04 §9.1); immutable, built by
 * {@link PageBuilder}. Columns are numbered {@code x * 16 + z}. Links in the packed surfaces cover neighbours inside
 * the page; {@link SurfaceGrid} decides links across page edges with the same rule. Each surface also carries its
 * local component: the surfaces joined by in-page links both ways share one, numbered from 0 in surface order, and the
 * page keeps its one-way links (drops of more than a step) between them (PATH-04); {@link Components} joins them
 * across pages.
 */
@CoreApi
public final class SurfacePage {
    public static final int COLUMNS = 256;
    public static final int MAX_LAYERS = 4;

    private final PageCoord coord;
    private final int[] start;
    private final int[] surfaces;
    private final short[] component;
    private final int components;
    private final long[] drops;
    private final short[] layers;
    private final int overflow;
    private final int overflowColumns;
    private final long hash;

    SurfacePage(
            PageCoord coord,
            int[] start,
            int[] surfaces,
            short[] component,
            int components,
            long[] drops,
            short[] layers,
            int overflow,
            int overflowColumns) {
        if (start.length != COLUMNS + 1
                || start[COLUMNS] != surfaces.length
                || component.length != surfaces.length
                || layers.length != surfaces.length) {
            throw new IllegalArgumentException("column offsets do not cover the surfaces");
        }
        this.coord = coord;
        this.start = start;
        this.surfaces = surfaces;
        this.component = component;
        this.components = components;
        this.drops = drops;
        this.layers = layers;
        this.overflow = overflow;
        this.overflowColumns = overflowColumns;
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
        return new SurfacePage(
                coord, new int[COLUMNS + 1], new int[0], new short[0], 0, new long[0], new short[0], 0, 0);
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

    /** The local component of a surface: 0 .. {@link #components()} - 1. */
    public int component(int column, int layer) {
        return component[start[column] + layer];
    }

    /** How many local components the page's surfaces form. */
    public int components() {
        return components;
    }

    /**
     * The layer of the surface in the neighbouring column {@code dir} that a surface links to inside the page, or -1:
     * the link bit and the layer the builder chose (the smallest rise), so a search need not choose again.
     */
    public int target(int column, int layer, int dir) {
        int i = start[column] + layer;
        if (!Surface.linked(surfaces[i], dir)) {
            return -1;
        }
        return (layers[i] >>> (2 * dir)) & 3;
    }

    /** The one-way links between local components, {@code from << 32 | to}, sorted; the page's own array. */
    long[] drops() {
        return drops;
    }

    /** Surfaces dropped because their column already had {@link #MAX_LAYERS}; S8 counts them. */
    public int overflow() {
        return overflow;
    }

    /** Columns that had more than {@link #MAX_LAYERS} surfaces. */
    public int overflowColumns() {
        return overflowColumns;
    }

    /**
     * The page's memory as stored, in bytes, for S8 and budget G: offsets and surfaces, and per surface its local
     * component and link layers (two {@code short}s), plus the one-way links.
     */
    public int bytes() {
        return 16 + 4 * (start.length + surfaces.length) + 4 * surfaces.length + 8 * drops.length;
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
                && other.overflow == overflow
                && other.overflowColumns == overflowColumns;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }
}
