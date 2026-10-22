package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/**
 * A match's published pages (04 §9.1): {@code pagesX x pagesZ} pages over the match space, replaced copy-on-write, so
 * a path job keeps the grid it captured while the core publishes newer pages. A node is a surface, numbered {@code
 * (page * 256 + column) * 4 + layer}. Links inside a page come from its packed bits; links across a page edge follow
 * {@link PageBuilder#linkable}, with the same smallest-rise choice. Columns of the last pages beyond the match's size
 * are outside: a page is captured whole, but no path leaves the match.
 */
@CoreApi
@HotPath
public final class SurfaceGrid {
    private final int sizeX;
    private final int sizeZ;
    private final int pagesX;
    private final int pagesZ;
    private final MobilityClass mobility;
    private final SurfacePage[] pages;
    private final long version;

    private SurfaceGrid(int sizeX, int sizeZ, MobilityClass mobility, SurfacePage[] pages, long version) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.pagesX = (sizeX + 15) >> 4;
        this.pagesZ = (sizeZ + 15) >> 4;
        this.mobility = mobility;
        this.pages = pages;
        this.version = version;
    }

    /** A grid of empty pages covering {@code sizeX x sizeZ} match-local columns. */
    public static SurfaceGrid empty(int sizeX, int sizeZ, MobilityClass mobility) {
        int pagesX = (sizeX + 15) >> 4;
        int pagesZ = (sizeZ + 15) >> 4;
        SurfacePage[] pages = new SurfacePage[pagesX * pagesZ];
        for (int pz = 0; pz < pagesZ; pz++) {
            for (int px = 0; px < pagesX; px++) {
                pages[pz * pagesX + px] = SurfacePage.empty(new PageCoord(px, pz));
            }
        }
        return new SurfaceGrid(sizeX, sizeZ, mobility, pages, 0);
    }

    /** A new grid with {@code page} in its place; this grid is unchanged. */
    public SurfaceGrid publish(SurfacePage page) {
        PageCoord c = page.coord();
        if (c.x() >= pagesX || c.z() >= pagesZ) {
            throw new IllegalArgumentException("page " + c + " outside a grid of " + pagesX + " x " + pagesZ);
        }
        SurfacePage[] copy = Arrays.copyOf(pages, pages.length);
        copy[c.z() * pagesX + c.x()] = page;
        return new SurfaceGrid(sizeX, sizeZ, mobility, copy, version + 1);
    }

    /** The match's columns along X; the pages may hold up to 15 more. */
    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int pagesX() {
        return pagesX;
    }

    public int pagesZ() {
        return pagesZ;
    }

    public MobilityClass mobility() {
        return mobility;
    }

    /** Grows by one with every publication. */
    public long version() {
        return version;
    }

    public SurfacePage page(int px, int pz) {
        return pages[pz * pagesX + px];
    }

    /** Surfaces in the match-local column, 0 outside the grid. */
    public int count(int localX, int localZ) {
        if (!inside(localX, localZ)) {
            return 0;
        }
        return page(localX >> 4, localZ >> 4).count(SurfacePage.column(localX & 15, localZ & 15));
    }

    public int surface(int localX, int localZ, int layer) {
        return page(localX >> 4, localZ >> 4).surface(SurfacePage.column(localX & 15, localZ & 15), layer);
    }

    /** Whether the match-local column is in the match. */
    public boolean inside(int localX, int localZ) {
        return localX >= 0 && localZ >= 0 && localX < sizeX && localZ < sizeZ;
    }

    /**
     * The layer of the surface in the neighbouring column {@code dir} that the surface at (x, z, layer) links to, or
     * -1: the packed bit inside a page, the link rule across a page edge (a diagonal also needs both orthogonals).
     */
    public int neighbour(int localX, int localZ, int layer, int dir) {
        int nx = localX + Dir.dx(dir);
        int nz = localZ + Dir.dz(dir);
        if (!inside(nx, nz)) {
            return -1;
        }
        int from = surface(localX, localZ, layer);
        boolean samePage = (nx >> 4) == (localX >> 4) && (nz >> 4) == (localZ >> 4);
        if (samePage) {
            return Surface.linked(from, dir) ? bestLayer(nx, nz, from) : -1;
        }
        if (Dir.diagonal(dir)
                && (neighbour(localX, localZ, layer, Dir.left(dir)) < 0
                        || neighbour(localX, localZ, layer, Dir.right(dir)) < 0)) {
            return -1;
        }
        return bestLayer(nx, nz, from);
    }

    /** The layer of the column's surface that {@code from} links to by the rule, or -1. */
    private int bestLayer(int localX, int localZ, int from) {
        int count = count(localX, localZ);
        int best = -1;
        int bestRise = Integer.MAX_VALUE;
        for (int layer = 0; layer < count; layer++) {
            int to = surface(localX, localZ, layer);
            if (!PageBuilder.linkable(from, to, mobility)) {
                continue;
            }
            int rise = Math.abs(Surface.floor(to) - Surface.floor(from));
            if (rise < bestRise) {
                best = layer;
                bestRise = rise;
            }
        }
        return best;
    }

    /** The node number of a surface. */
    public int node(int localX, int localZ, int layer) {
        int page = (localZ >> 4) * pagesX + (localX >> 4);
        return (page * SurfacePage.COLUMNS + SurfacePage.column(localX & 15, localZ & 15)) * SurfacePage.MAX_LAYERS
                + layer;
    }

    public int nodeX(int node) {
        int page = node / (SurfacePage.COLUMNS * SurfacePage.MAX_LAYERS);
        int column = (node / SurfacePage.MAX_LAYERS) % SurfacePage.COLUMNS;
        return (page % pagesX) * 16 + column / 16;
    }

    public int nodeZ(int node) {
        int page = node / (SurfacePage.COLUMNS * SurfacePage.MAX_LAYERS);
        int column = (node / SurfacePage.MAX_LAYERS) % SurfacePage.COLUMNS;
        return (page / pagesX) * 16 + column % 16;
    }

    public static int nodeLayer(int node) {
        return node % SurfacePage.MAX_LAYERS;
    }

    /** The memory of the pages as stored, in bytes (budget G, S8). */
    public long bytes() {
        long total = 0;
        for (SurfacePage page : pages) {
            total += page.bytes();
        }
        return total;
    }
}
